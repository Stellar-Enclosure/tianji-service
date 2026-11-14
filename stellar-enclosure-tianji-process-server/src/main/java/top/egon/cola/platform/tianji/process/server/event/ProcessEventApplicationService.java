package top.egon.cola.platform.tianji.process.server.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import static top.egon.cola.platform.tianji.process.server.error.AutomationOperationException.Code.TIANJI_EVENT_CONFLICT;
import static top.egon.cola.platform.tianji.process.server.error.AutomationOperationException.Code.TIANJI_EVENT_ROUTE_VERSION_CONFLICT;

/** Accepts durable event admissions and advances them in separate Process transactions. */
@Slf4j
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessEventApplicationService {
    private static final String EVENTS_ENABLED = "stellar.tianji.process.events.enabled";
    private static final String EVENT_ADMISSION_ENABLED = "stellar.tianji.process.events.admission-enabled";
    private static final int MAX_EVENT_BYTES = 68 * 1024;

    @Qualifier("processEventInboxStore") private final ProcessEventInboxStore inbox;
    @Qualifier("processExecutionService") private final ProcessExecutionService execution;
    @Qualifier("processEventTransactionTemplate") private final TransactionTemplate transaction;
    private final Environment environment;
    @Qualifier("processEventSourceAuthorizer") private final EventSourceAuthorizer sourceAuthorizer;

    public ProcessEventReceiptDTO receive(@NotNull ProcessActor producer, @NotNull @Valid ProcessEventCommand submitted) {
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

        final ProcessExecutionService.PreparedEventAdmissionVO prepared;
        try {
            requireNewAdmission();
            if (command.getWaitToken() != null) throw new AutomationOperationException(TIANJI_EVENT_ROUTE_VERSION_CONFLICT);
            String commandId = ProcessExecutionService.eventStartCommandId(producer, command.getEventId());
            prepared = execution.prepareEventAdmission(producer, command, commandId, fingerprint);
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

    /** The short scan transaction releases all candidate locks before the fixed START lock sequence. */
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

    @FunctionalInterface
    public interface EventSourceAuthorizer {
        boolean permits(ProcessActor producer, String definitionKey, String releaseVersion, String routeKey,
                        long routeVersion, String routeSha256);
    }
}
