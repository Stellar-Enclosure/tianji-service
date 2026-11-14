package top.egon.cola.platform.tianji.process.server.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.eventsubscription.api.EventSubscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EventRouteVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.KindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceBindingBO;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessInstanceBindingStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionBO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore.ImmutableReleaseVO;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static top.egon.cola.platform.tianji.process.server.error.AutomationOperationException.Code.TIANJI_EVENT_CONFLICT;
import static top.egon.cola.platform.tianji.process.server.error.AutomationOperationException.Code.TIANJI_EVENT_ROUTE_VERSION_CONFLICT;
import static top.egon.cola.platform.tianji.process.server.event.EventSubscriptionBO.State.BOUND;
import static top.egon.cola.platform.tianji.process.server.event.EventSubscriptionBO.State.CLOSED;
import static top.egon.cola.platform.tianji.process.server.event.EventSubscriptionBO.State.CONSUMED;
import static top.egon.cola.platform.tianji.process.server.event.EventSubscriptionBO.State.RESERVED;

/** Accepts durable event admissions and advances them in separate Process transactions. */
@Slf4j
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessEventApplicationService {
    private static final String EVENTS_ENABLED = "stellar.tianji.process.events.enabled";
    private static final String EVENT_ADMISSION_ENABLED = "stellar.tianji.process.events.admission-enabled";
    private static final int MAX_EVENT_BYTES = 68 * 1024;
    private static final Set<StateEnum> TERMINAL_NODES = Set.of(StateEnum.COMPLETED, StateEnum.CANCELLED,
            StateEnum.TIMED_OUT, StateEnum.FAILED);

    @Qualifier("processEventInboxStore") private final ProcessEventInboxStore inbox;
    @Qualifier("processExecutionService") private final ProcessExecutionService execution;
    @Qualifier("processEventTransactionTemplate") private final TransactionTemplate transaction;
    private final Environment environment;
    @Qualifier("processEventSourceAuthorizer") private final EventSourceAuthorizer sourceAuthorizer;
    @Qualifier("processSignalParticipants") private final SignalParticipantsVO signal;

    public ProcessEventApplicationService(ProcessEventInboxStore inbox, ProcessExecutionService execution,
            TransactionTemplate transaction, Environment environment, EventSourceAuthorizer sourceAuthorizer) {
        this(inbox, execution, transaction, environment, sourceAuthorizer, null);
    }

    public ProcessEventReceiptDTO receive(@NotNull ProcessActor producer, @NotNull @Valid ProcessEventCommand submitted) {
        return receive(producer, submitted, null);
    }

    ProcessEventReceiptDTO receiveScheduled(ProcessActor producer, ProcessEventCommand command,
            String expectedReleaseVersion, long expectedActivationRevision, String expectedRouteSha256) {
        return receive(producer, command, new ScheduledAdmissionVO(expectedReleaseVersion,
                expectedActivationRevision, expectedRouteSha256));
    }

    private ProcessEventReceiptDTO receive(ProcessActor producer, ProcessEventCommand submitted, ScheduledAdmissionVO schedule) {
        requireOutsideTransaction();
        if (producer == null || producer.subjectType() != ProcessActor.SubjectType.SERVICE
                || !EventInboxBO.sha256(producer.issuerFingerprint())) throw denied();
        if (submitted == null || !submitted.isEventEnvelopeValid()) throw invalid();
        var command = new ProcessEventCommand(submitted.getEventId(), submitted.getDefinitionKey(), submitted.getRouteKey(),
                submitted.getRouteVersion(), submitted.getType(), submitted.getOccurredAt(), submitted.getCorrelationKey(),
                submitted.getWaitToken(), EventInboxBO.immutableObject(submitted.getPayload(), EventInboxBO.MAX_PAYLOAD_BYTES));
        String fingerprint = fingerprint(command);
        var original = inbox.findOriginal(producer, command.getEventId()).orElse(null);
        if (original != null) return replay(producer, fingerprint, original);
        if (command.getWaitToken() != null && schedule == null && signal != null) {
            return receiveSignal(producer, command, fingerprint);
        }

        final ProcessExecutionService.PreparedEventAdmissionVO prepared;
        try {
            requireNewAdmission();
            if (command.getWaitToken() != null) throw new AutomationOperationException(TIANJI_EVENT_ROUTE_VERSION_CONFLICT);
            String commandId = ProcessExecutionService.eventStartCommandId(producer, command.getEventId());
            prepared = execution.prepareEventAdmission(producer, command, commandId, fingerprint);
            if (schedule != null && (!schedule.releaseVersion().equals(prepared.release().releaseVersion())
                    || schedule.activationRevision() != prepared.activation().getRevision()
                    || !schedule.routeSha256().equals(prepared.routeSha()))) {
                throw new AutomationOperationException(AutomationOperationException.Code.TIANJI_EVENT_ROUTE_CHANGED);
            }
            if (!sourceAuthorizer.permits(producer, command.getDefinitionKey(), prepared.release().releaseVersion(),
                    command.getRouteKey(), command.getRouteVersion(), prepared.routeSha())) throw denied();
        } catch (RuntimeException failure) {
            var committed = inbox.findOriginal(producer, command.getEventId()).orElse(null);
            if (committed != null) return replay(producer, fingerprint, committed);
            throw failure;
        }

        try {
            var admitted = transaction.execute(status -> {
                inbox.acquireTenantShared(producer.tenantId());
                if (inbox.findOriginal(producer, command.getEventId()).isPresent()) return null;
                requireNewAdmission();
                var row = execution.validateEventAdmission(prepared, "er-" + UUID.randomUUID(), inbox.now());
                return inbox.insert(row) ? row : null;
            });
            if (admitted != null) return receipt(admitted);
        } catch (RuntimeException failure) {
            var committed = inbox.findOriginal(producer, command.getEventId()).orElse(null);
            if (committed != null) return replay(producer, fingerprint, committed);
            throw failure;
        }
        var committed = inbox.findOriginal(producer, command.getEventId()).orElseThrow(ProcessEventApplicationService::unavailable);
        return replay(producer, fingerprint, committed);
    }

    public ProcessEventReceiptDTO observe(@NotNull ProcessActor actor, @NotNull String receiptId,
                                          @NotNull Predicate<String> instanceObserver) {
        requireOutsideTransaction();
        var row = inbox.find(actor.tenantId(), receiptId).orElseThrow(ProcessResourceNotFoundException::new);
        if (actor.equals(row.producer()) && canUseSource(actor, row)
                || row.getInstanceId() != null && instanceObserver.test(row.getInstanceId())) {
            return receipt(row);
        }
        throw new ProcessResourceNotFoundException();
    }

    public ProcessEventReceiptDTO advance(@Min(1) long tenantId, @NotNull String receiptId) {
        requireOutsideTransaction();
        var snapshot = inbox.find(tenantId, receiptId).orElseThrow(ProcessResourceNotFoundException::new);
        if ("SIGNAL".equals(snapshot.getMode())) return receipt(advanceSignal(snapshot));
        var outcome = execution.startAdmittedEvent(inbox, tenantId, receiptId);
        if (!"APPLIED".equals(outcome.state())) {
            var decided = inbox.find(tenantId, receiptId).orElseThrow(ProcessResourceNotFoundException::new);
            requireOutcome(decided, outcome);
            return receipt(decided);
        }
        return Objects.requireNonNull(transaction.execute(status -> {
            inbox.acquireTenantShared(tenantId);
            var row = inbox.lock(tenantId, receiptId);
            if ("RECEIVED".equals(row.getState())) {
                row = inbox.decide(row, outcome.state(), outcome.decisionCode(), outcome.instanceId());
            }
            requireOutcome(row, outcome);
            return receipt(row);
        }));
    }

    private ProcessEventReceiptDTO receiveSignal(ProcessActor producer, ProcessEventCommand command, String fingerprint) {
        try {
            requireNewAdmission();
            String tokenSha = ProcessEventSubscriptionStore.tokenSha256(command.getWaitToken());
            var reservation = signal.subscriptions().find(producer.tenantId(), tokenSha)
                    .orElseThrow(ProcessResourceNotFoundException::new);
            var observed = Objects.requireNonNull(transaction.execute(status -> {
                var binding = signal.bindings().find(producer.tenantId(), reservation.getInstanceId())
                        .orElseThrow(ProcessEventApplicationService::unavailable);
                return signalScope(binding, reservation);
            }));
            if (!sourceAuthorizer.permits(producer, observed.binding().getDefinitionKey(), observed.release().releaseVersion(),
                    observed.route().routeKey(), observed.route().version(), observed.routeSha())) throw denied();
            requireSignalCommand(command, observed);
            var admitted = transaction.execute(status -> {
                var current = signal.guard().lockKnownRoot(producer.tenantId(), reservation.getInstanceId());
                if (inbox.findOriginal(producer, command.getEventId()).isPresent()) return null;
                requireNewAdmission();
                var scope = signalScope(current, reservation);
                if (!scope.equals(observed)) throw unavailable();
                var locked = signal.subscriptions().lockExact(reservation);
                var now = inbox.now();
                var row = signalRow(producer, command, fingerprint, scope, locked.getTokenSha256(), now);
                if (!inbox.insertSignal(row, locked)) return null;
                if (isClosed(locked)) return inbox.decideSignal(row, locked, "IGNORED_LATE", "LATE");
                return now.isBefore(locked.getDeadlineAt()) ? row : closeLateSignal(row, locked);
            });
            if (admitted != null) return receipt(admitted);
        } catch (RuntimeException failure) {
            var committed = inbox.findOriginal(producer, command.getEventId()).orElse(null);
            if (committed != null) return replay(producer, fingerprint, committed);
            throw failure;
        }
        return replay(producer, fingerprint,
                inbox.findOriginal(producer, command.getEventId()).orElseThrow(ProcessEventApplicationService::unavailable));
    }

    private static EventInboxBO signalRow(ProcessActor producer, ProcessEventCommand command, String fingerprint,
            SignalScopeVO scope, String tokenSha, Instant now) {
        var expires = now.plus(scope.route().ttl()).truncatedTo(ChronoUnit.MICROS);
        if (!expires.isAfter(now)) throw invalid();
        return new EventInboxBO().setTenantId(producer.tenantId()).setProducerIssuer(producer.issuerFingerprint())
                .setProducerSubject(producer.subjectId()).setEventId(command.getEventId()).setReceiptId("er-" + UUID.randomUUID())
                .setFingerprint(fingerprint).setRouteKey(scope.route().routeKey()).setRouteVersion(scope.route().version())
                .setRouteSha256(scope.routeSha()).setReleaseKey(scope.release().releaseVersion()).setMode("SIGNAL")
                .setWaitTokenSha256(tokenSha).setPayload(command.getPayload()).setCorrelationKey(command.getCorrelationKey())
                .setState("RECEIVED").setOccurredAt(command.getOccurredAt()).setReceivedAt(now).setAdmittedAt(now)
                .setExpiresAt(expires).setNextRetryAt(now).setDefinitionKey(scope.binding().getDefinitionKey())
                .setActivationRevision(scope.binding().getActivationRevision());
    }

    private EventInboxBO advanceSignal(EventInboxBO snapshot) {
        if (!"RECEIVED".equals(snapshot.getState())) return snapshot;
        if (signal == null) throw unavailable();
        var reservation = signal.subscriptions().find(snapshot.getTenantId(), snapshot.getWaitTokenSha256())
                .orElseThrow(ProcessEventApplicationService::unavailable);
        var observedAt = inbox.now();
        boolean needsAuthorization = !isClosed(reservation) && observedAt.isBefore(reservation.getDeadlineAt())
                && observedAt.isBefore(snapshot.getExpiresAt()) && snapshot.getAttempts() < ProcessEventInboxStore.MAX_RETRIES;
        var authorization = !needsAuthorization ? SignalAuthorization.NOT_OBSERVED
                : canUseSource(snapshot.producer(), snapshot) ? SignalAuthorization.PERMITTED : SignalAuthorization.REVOKED;
        return Objects.requireNonNull(transaction.execute(status -> {
            var binding = signal.guard().lockKnownRoot(reservation.getTenantId(), reservation.getInstanceId());
            var scope = signalScope(binding, reservation);
            if (!sameSignalScope(snapshot, scope, reservation)) throw unavailable();
            // The guarded snapshot may have changed while authorization was observed.
            var current = signal.subscriptions().find(reservation.getTenantId(), reservation.getTokenSha256())
                    .orElseThrow(ProcessEventApplicationService::unavailable);
            var now = inbox.now();
            var wait = authorization == SignalAuthorization.PERMITTED && current.getState() == BOUND
                    && now.isBefore(current.getDeadlineAt()) && now.isBefore(snapshot.getExpiresAt())
                    ? readSignalWait(scope, current) : null;
            var locked = signal.subscriptions().lockExact(reservation);
            if (!current.equals(locked)) throw unavailable();
            var row = inbox.lock(snapshot.getTenantId(), snapshot.getReceiptId());
            if (!sameSignalScope(row, scope, locked) || !row.getFingerprint().equals(snapshot.getFingerprint())
                    || !row.producer().equals(snapshot.producer()) || !row.getEventId().equals(snapshot.getEventId())) throw unavailable();
            if (!"RECEIVED".equals(row.getState())) return row;
            return applySignal(row, scope, locked, wait, authorization);
        }));
    }

    private EventInboxBO applySignal(EventInboxBO row, SignalScopeVO scope, EventSubscriptionBO reservation,
            SignalWaitVO wait, SignalAuthorization authorization) {
        if (isClosed(reservation)) {
            return inbox.decideSignal(row, reservation, "IGNORED_LATE", "LATE");
        }
        var now = inbox.now();
        if (!now.isBefore(reservation.getDeadlineAt())) return closeLateSignal(row, reservation);
        if (!now.isBefore(row.getExpiresAt())) return inbox.decideSignal(row, reservation, "REJECTED", "EXPIRED");
        if (row.getAttempts() >= ProcessEventInboxStore.MAX_RETRIES) {
            return inbox.decideSignal(row, reservation, "DEAD", "RETRY_EXHAUSTED");
        }
        if (authorization == SignalAuthorization.NOT_OBSERVED) throw unavailable();
        if (authorization == SignalAuthorization.REVOKED) return inbox.decideSignal(row, reservation, "REJECTED", "ROUTE_REVOKED");
        if (reservation.getState() == RESERVED || inbox.hasEarlierPendingSignal(row, reservation)) return row;
        if (wait == null) throw unavailable();
        if (wait.nativeWait() == null || !now.isBefore(wait.row().getNodeDeadlineAt())
                || !now.isBefore(wait.row().getAttemptDeadlineAt())) return closeLateSignal(row, reservation);
        try {
            if (!signal.lifecycle().acceptEventResult(scope.binding(), scope.release(), scope.route(), reservation, wait.row(), row.getPayload())) {
                return closeLateSignal(row, reservation);
            }
        } catch (IllegalArgumentException invalidPayload) {
            return inbox.decideSignal(row, reservation, "REJECTED", "INVALID_PAYLOAD");
        }
        var consumed = signal.subscriptions().consume(reservation);
        signal.runtime().messageEventReceived(wait.nativeWait().getEventName(), wait.nativeWait().getExecutionId());
        return inbox.decideSignal(row, consumed, "APPLIED", "APPLIED");
    }

    private SignalWaitVO readSignalWait(SignalScopeVO scope, EventSubscriptionBO reservation) {
        var binding = scope.binding();
        var verified = signal.releaseService().verifiedAutomation(scope.release());
        var slot = verified.controlBindings().occurrenceSlots().get(reservation.getTargetNodeId());
        if (slot == null || slot.activityId() == null || slot.attemptOrBodyId() == null || !slot.activityId().equals(slot.waitId())) {
            throw unavailable();
        }
        var node = signal.nodes().find(binding.getTenantId(), binding.getInstanceId(), reservation.getNodeExecutionRef())
                .orElseThrow(ProcessEventApplicationService::unavailable);
        if (node.getKind() != KindEnum.EVENT_WAIT || !reservation.getParentScopeId().equals(node.getParentScopeId())
                || !reservation.getTargetNodeId().equals(node.getNodeId())
                || !reservation.getNativeSubscriptionId().equals(node.getNativeSubscriptionId())
                || !binding.getConfigurationSha256().equals(node.getConfigurationSha256())) throw unavailable();
        if (node.isExcluded() || TERMINAL_NODES.contains(node.getState())) return new SignalWaitVO(node, null);
        if (node.getState() != StateEnum.WAITING) throw unavailable();
        var nativeWait = signal.nativeAccess().findEventWait(binding, verified.root().engineDefinitionId(), slot.activityId(),
                reservation.getNativeExecutionId(), reservation.getNativeSubscriptionId(), node.getAttemptScopeId()).orElse(null);
        return new SignalWaitVO(node, nativeWait);
    }

    private EventInboxBO closeLateSignal(EventInboxBO row, EventSubscriptionBO reservation) {
        var closed = signal.subscriptions().close(reservation);
        return inbox.decideSignal(row, closed, "IGNORED_LATE", "LATE");
    }

    private static boolean isClosed(EventSubscriptionBO reservation) {
        return reservation.getState() == CLOSED || reservation.getState() == CONSUMED;
    }

    private SignalScopeVO signalScope(AutomationInstanceBindingBO binding, EventSubscriptionBO reservation) {
        var release = signal.releases().find(binding.getTenantId(), binding.getReleaseKey())
                .orElseThrow(ProcessEventApplicationService::unavailable);
        if (binding.getTenantId() != reservation.getTenantId() || !binding.getInstanceId().equals(reservation.getInstanceId())
                || !binding.getDefinitionKey().equals(release.rootDefinitionKey())) throw unavailable();
        var matches = release.source().eventRoutes().stream().filter(route -> "SIGNAL".equals(route.mode())
                && reservation.getRouteKey().equals(route.routeKey()) && reservation.getRouteVersion() == route.version()
                && reservation.getTargetNodeId().equals(route.targetNodeId()) && reservation.getSourceRef().equals(route.sourceRef()))
                .limit(2).toList();
        if (matches.size() != 1 || !(release.dependencies().get("eventRoutes") instanceof Map<?, ?> dependencies)
                || !(dependencies.get(reservation.getRouteKey() + "/" + reservation.getRouteVersion()) instanceof String routeSha)
                || !EventInboxBO.sha256(routeSha)) throw unavailable();
        return new SignalScopeVO(binding, release, matches.getFirst(), routeSha);
    }

    private static void requireSignalCommand(ProcessEventCommand command, SignalScopeVO scope) {
        if (!scope.binding().getDefinitionKey().equals(command.getDefinitionKey())
                || !scope.route().routeKey().equals(command.getRouteKey()) || scope.route().version() != command.getRouteVersion()) {
            throw new AutomationOperationException(TIANJI_EVENT_ROUTE_VERSION_CONFLICT);
        }
        command.validateForRoute(scope.route());
    }

    private static boolean sameSignalScope(EventInboxBO row, SignalScopeVO scope, EventSubscriptionBO reservation) {
        return "SIGNAL".equals(row.getMode()) && row.getTenantId() == reservation.getTenantId()
                && reservation.getTokenSha256().equals(row.getWaitTokenSha256()) && scope.routeSha().equals(row.getRouteSha256())
                && scope.route().routeKey().equals(row.getRouteKey()) && scope.route().version() == row.getRouteVersion()
                && scope.release().releaseVersion().equals(row.getReleaseKey())
                && scope.binding().getDefinitionKey().equals(row.getDefinitionKey())
                && scope.binding().getActivationRevision() == row.getActivationRevision();
    }

    /** The short scan transaction releases claim locks before taking the owner locks for event application. */
    public void advancePending(int limit) {
        requireOutsideTransaction();
        var claims = Objects.requireNonNull(transaction.execute(status -> inbox.claimPending(limit)));
        for (var claim : claims) {
            try {
                advance(claim.tenantId(), claim.receiptId());
            } catch (RuntimeException failure) {
                transaction.executeWithoutResult(status -> inbox.recordRetry(claim));
                log.warn("Process event attempt failed: type={}", failure.getClass().getName());
            }
        }
    }

    private ProcessEventReceiptDTO replay(ProcessActor producer, String fingerprint, EventInboxBO row) {
        if (!producer.equals(row.producer()) || !canUseSource(producer, row)) throw denied();
        if (!fingerprint.equals(row.getFingerprint())) throw new AutomationOperationException(TIANJI_EVENT_CONFLICT);
        return receipt(row);
    }

    private boolean canUseSource(ProcessActor producer, EventInboxBO row) {
        try {
            return sourceAuthorizer.permits(producer, row.getDefinitionKey(), row.getReleaseKey(), row.getRouteKey(),
                    row.getRouteVersion(), row.getRouteSha256());
        } catch (AccessDeniedException denied) {
            return false;
        }
    }

    private void requireNewAdmission() {
        if (!environment.getProperty(EVENTS_ENABLED, Boolean.class, false)
                || !environment.getProperty(EVENT_ADMISSION_ENABLED, Boolean.class, true)) throw unavailable();
    }

    private static void requireOutcome(EventInboxBO row, ProcessExecutionService.EventStartOutcomeVO outcome) {
        if (!outcome.state().equals(row.getState()) || !Objects.equals(outcome.decisionCode(), row.getDecisionCode())
                || !Objects.equals(outcome.instanceId(), row.getInstanceId())) throw unavailable();
    }

    private static ProcessEventReceiptDTO receipt(EventInboxBO row) {
        var receipt = new ProcessEventReceiptDTO().setReceiptId(row.getReceiptId()).setEventId(row.getEventId())
                .setState(row.getState()).setReleaseVersion(row.getReleaseKey()).setActivationRevision(row.getActivationRevision())
                .setAdmittedAt(row.getAdmittedAt()).setReceivedAt(row.getReceivedAt()).setDecidedAt(row.getDecidedAt())
                .setInstanceId(row.getInstanceId()).setNodeExecutionRef(row.getNodeExecutionRef()).setDecisionCode(row.getDecisionCode());
        if (!receipt.isReceiptConsistent()) throw unavailable();
        return receipt;
    }

    private static String fingerprint(ProcessEventCommand command) {
        var wire = new LinkedHashMap<String, Object>();
        wire.put("eventId", command.getEventId());
        wire.put("definitionKey", command.getDefinitionKey());
        wire.put("routeKey", command.getRouteKey());
        wire.put("routeVersion", command.getRouteVersion());
        wire.put("type", command.getType());
        wire.put("occurredAt", command.getOccurredAt().toString());
        wire.put("correlationKey", command.getCorrelationKey());
        wire.put("waitToken", command.getWaitToken());
        wire.put("payload", command.getPayload());
        return ProcessTypedJson.sha256("AUTOMATION_EVENT_REQUEST_V2", ProcessTypedJson.canonicalBytes(wire, MAX_EVENT_BYTES));
    }

    private static void requireOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) throw unavailable();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Current process event source authorization is required");
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid process event request");
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Process event evidence is unavailable");
    }

    private record ScheduledAdmissionVO(String releaseVersion, long activationRevision, String routeSha256) {
        private ScheduledAdmissionVO {
            if (releaseVersion == null || activationRevision <= 0 || !EventInboxBO.sha256(routeSha256)) throw invalid();
        }
    }

    private record SignalScopeVO(AutomationInstanceBindingBO binding, ImmutableReleaseVO release, EventRouteVO route, String routeSha) { }

    private record SignalWaitVO(ProcessNodeExecutionBO row, EventSubscription nativeWait) { }

    private enum SignalAuthorization { NOT_OBSERVED, PERMITTED, REVOKED }

    public record SignalParticipantsVO(ProcessEventSubscriptionStore subscriptions, ProcessInstanceBindingStore bindings,
            AutomationReleaseStore releases, ProcessReleaseService releaseService, AutomationInstanceGuardCommandInterceptor guard,
            ProcessNodeExecutionStore nodes, ProcessControlNativeAccess nativeAccess, ProcessNodeLifecycleListener lifecycle,
            RuntimeService runtime) {
        public SignalParticipantsVO {
            Objects.requireNonNull(subscriptions);
            Objects.requireNonNull(bindings);
            Objects.requireNonNull(releases);
            Objects.requireNonNull(releaseService);
            Objects.requireNonNull(guard);
            Objects.requireNonNull(nodes);
            Objects.requireNonNull(nativeAccess);
            Objects.requireNonNull(lifecycle);
            Objects.requireNonNull(runtime);
        }
    }

    @FunctionalInterface
    public interface EventSourceAuthorizer {
        /** Verifies the producer against the trusted source bound by this exact published route contract digest. */
        boolean permits(ProcessActor producer, String definitionKey, String releaseVersion, String routeKey,
                        long routeVersion, String routeSha256);
    }
}
