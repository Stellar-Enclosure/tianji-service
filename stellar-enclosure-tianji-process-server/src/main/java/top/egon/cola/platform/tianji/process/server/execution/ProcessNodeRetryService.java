package top.egon.cola.platform.tianji.process.server.execution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodeRetryCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodeRetryResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.SideEffectOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService.RecoveryScopeVO;
import top.egon.cola.platform.tianji.process.server.worker.WorkerEffectRecoveryAuthority;
import top.egon.cola.platform.tianji.process.server.worker.WorkerEffectRecoveryProofVO;
import top.egon.cola.platform.tianji.process.server.worker.WorkerEffectRecoveryQuery;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static top.egon.cola.platform.tianji.process.server.error.AutomationOperationException.Code.TIANJI_NODE_RETRY_BLOCKED;

@Slf4j
@Validated
@Service("processNodeRetryService")
@ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessNodeRetryService {
    private static final Duration MAX_OBSERVATION_TIME = Duration.ofSeconds(10);
    private static final String TRUSTED_AUTHORITIES = "stellar.tianji.process.worker-recovery.trusted-authorities";
    private final Semaphore observationSlots = new Semaphore(16);

    @Qualifier("processInstanceBindingStore") private final ProcessInstanceBindingStore bindings;
    @Qualifier("processNodeExecutionStore") private final ProcessNodeExecutionStore nodes;
    @Qualifier("automationInstanceGuardCommandInterceptor") private final AutomationInstanceGuardCommandInterceptor guard;
    @Qualifier("flowableWorkerService") private final ObjectProvider<FlowableWorkerService> workers;
    @Qualifier("workerEffectRecoveryAuthority") private final ObjectProvider<WorkerEffectRecoveryAuthority> authorities;
    @Qualifier("processDraftTransactionTemplate") private final TransactionTemplate transaction;
    @Qualifier("environment") private final Environment environment;
    @Qualifier("processNodeExecutionConverter") private final ProcessNodeExecutionConverter converter;

    public NodeRetryResultDTO retry(@NotNull ProcessActor actor, String instanceId, @NotNull @Valid NodeRetryCommand command,
                                   @NotNull Supplier<Instant> refreshAuthorization) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Worker recovery must own its transaction");
        }
        var intent = RetryIntentVO.from(instanceId, command);
        String requestSha256 = intent.digest(actor, instanceId);
        Instant initialGrant = authorization(refreshAuthorization);
        var read = new TransactionTemplate(transaction.getTransactionManager());
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(transaction.getTimeout());
        var observed = Objects.requireNonNull(read.execute(status -> {
            requireOwner(actor, instanceId);
            Instant now = nodes.now();
            requireGrant(initialGrant, now);
            var priorReceipt = nodes.findRetry(actor.tenantId(), instanceId, intent.commandId());
            if (priorReceipt.isPresent()) return new RetryObservationVO(replay(priorReceipt.get(), requestSha256), null, null, now);
            var prior = eligible(actor, instanceId, intent);
            var scope = worker().recoveryScope(actor, instanceId, prior, intent.businessReceiptRef());
            requireWindow(prior, scope, now);
            return new RetryObservationVO(null, prior, scope, now);
        }));
        if (observed.receipt() != null) return observed.receipt();
        WorkerEffectRecoveryVO proof;
        try {
            proof = observeProof(observed);
        } catch (AutomationOperationException failure) {
            if (failure.code() != TIANJI_NODE_RETRY_BLOCKED || Thread.currentThread().isInterrupted()) throw failure;
            Instant currentGrant = authorization(refreshAuthorization);
            var committed = read.execute(status -> {
                requireOwner(actor, instanceId);
                requireGrant(currentGrant, nodes.now());
                return nodes.findRetry(actor.tenantId(), instanceId, intent.commandId());
            });
            if (committed == null || committed.isEmpty()) throw failure;
            return replay(committed.orElseThrow(), requestSha256);
        }
        Instant finalGrant = authorization(refreshAuthorization);
        return Objects.requireNonNull(transaction.execute(status -> {
            var root = guard.lockKnownRoot(actor.tenantId(), instanceId);
            if (!actor.equals(root.getOwner())) throw new ProcessResourceNotFoundException();
            Instant now = nodes.now();
            requireGrant(finalGrant, now);
            var concurrent = nodes.findRetry(actor.tenantId(), instanceId, intent.commandId());
            if (concurrent.isPresent()) return replay(concurrent.get(), requestSha256);
            var prior = eligible(actor, instanceId, intent);
            var worker = worker();
            var current = worker.recoveryScope(actor, instanceId, prior, intent.businessReceiptRef());
            if (!observed.prior().equals(prior) || !observed.scope().releaseKey().equals(current.releaseKey())
                    || !observed.scope().query().sha256().equals(current.query().sha256())) throw blocked();
            requireWindow(prior, current, now);
            if (proof != null && (proof.observedAt().isAfter(now) || !now.isBefore(proof.validUntil()))) throw blocked();
            String nextRef = worker.scheduleRecovery(prior, proof, intent.commandId(), requestSha256);
            var next = nodes.findRetry(actor.tenantId(), instanceId, intent.commandId()).orElseThrow(ProcessNodeRetryService::blocked);
            if (!nextRef.equals(next.getNodeExecutionRef()) || !prior.getOccurrenceId().equals(next.getOccurrenceId())
                    || prior.getAttempt() + 1 != next.getAttempt()) throw blocked();
            return replay(next, requestSha256);
        }));
    }

    public boolean canOfferRetry(ProcessActor actor, AutomationInstanceBindingBO binding, ProcessNodeExecutionBO row, Instant now) {
        if (!actor.equals(binding.getOwner()) || !plausibleWait(row) || !now.isBefore(row.getNodeDeadlineAt())
                || row.getSideEffectOutcome() == SideEffectOutcomeEnum.UNKNOWN
                   && (authorities.getIfAvailable() == null || trustedAuthorities().isEmpty())) return false;
        try {
            var scope = worker().recoveryScope(actor, binding.getInstanceId(), row, null);
            if (!binding.getReleaseKey().equals(scope.releaseKey())) throw blocked();
            requireWindow(row, scope, now);
            return true;
        } catch (AutomationOperationException unavailable) {
            if (unavailable.code() != TIANJI_NODE_RETRY_BLOCKED) throw unavailable;
            return false;
        }
    }

    private WorkerEffectRecoveryVO observeProof(RetryObservationVO observed) {
        if (observed.prior().getSideEffectOutcome() == SideEffectOutcomeEnum.NONE) return null;
        var authority = authorities.getIfAvailable();
        var allowed = trustedAuthorities();
        if (authority == null || allowed.isEmpty()) throw blocked();
        var original = observed.scope().query();
        Duration remaining = Duration.between(observed.observedAt(), original.getNodeDeadlineAt());
        Duration timeout = remaining.compareTo(MAX_OBSERVATION_TIME) < 0 ? remaining : MAX_OBSERVATION_TIME;
        var proof = observeBounded(authority, original.toBuilder().build(), timeout);
        if (proof == null || proof.authorityRef() == null || !allowed.contains(proof.authorityRef())) throw blocked();
        Instant now = Objects.requireNonNull(transaction.execute(status -> nodes.now()));
        return proof.freeze(original, observed.scope().outputSchema(), observed.scope().effectMode(), now);
    }

    private WorkerEffectRecoveryProofVO observeBounded(WorkerEffectRecoveryAuthority authority,
            WorkerEffectRecoveryQuery query, Duration timeout) {
        if (!observationSlots.tryAcquire()) throw new RejectedExecutionException("Worker recovery observer is at capacity");
        var observation = new FutureTask<>(() -> authority.observe(query));
        try {
            Thread.startVirtualThread(() -> {
                try { observation.run(); }
                finally { observationSlots.release(); }
            });
        } catch (RuntimeException | Error cannotStart) {
            observationSlots.release();
            throw cannotStart;
        }
        try {
            return observation.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw blocked();
        } catch (TimeoutException expired) {
            throw blocked();
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof Error fatal) throw fatal;
            log.warn("Worker recovery observer failed: type={}", failure.getCause().getClass().getSimpleName());
            throw blocked();
        } finally {
            observation.cancel(true);
        }
    }

    private List<String> trustedAuthorities() {
        var configured = Binder.get(environment).bind(TRUSTED_AUTHORITIES, Bindable.listOf(String.class)).orElse(List.of());
        return configured.stream().filter(ref -> ref.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}")).toList();
    }

    private ProcessNodeExecutionBO eligible(ProcessActor actor, String instanceId, RetryIntentVO intent) {
        var row = nodes.find(actor.tenantId(), instanceId, intent.nodeRef()).orElseThrow(ProcessResourceNotFoundException::new);
        if (!intent.expectedVersion().equals(converter.versionOf(row))) throw new ProcessVersionConflictException();
        if (!plausibleWait(row) || nodes.findByOccurrence(actor.tenantId(), instanceId, row.getOccurrenceId())
                .filter(row::equals).isEmpty()) throw blocked();
        return row;
    }

    private static boolean plausibleWait(ProcessNodeExecutionBO row) {
        return row.getState() == StateEnum.WAIT_RECOVERY && !row.isExcluded()
                && row.getCapabilityKey() != null && row.getNativeJobId() != null && row.getNativeSubscriptionId() != null
                && row.getAttempt() < 10 && row.getSideEffectOutcome() != SideEffectOutcomeEnum.CONFIRMED;
    }

    private static void requireWindow(ProcessNodeExecutionBO row, RecoveryScopeVO scope, Instant now) {
        var policy = scope.policy().retry();
        if (!now.isBefore(row.getNodeDeadlineAt()) || row.getAttempt() >= policy.maxAttempts()
                || row.getSideEffectOutcome() == SideEffectOutcomeEnum.NONE && !policy.retryableCodes().contains(row.getErrorCode())) {
            throw blocked();
        }
    }

    private void requireOwner(ProcessActor actor, String instanceId) {
        var root = bindings.find(actor.tenantId(), instanceId).orElseThrow(ProcessResourceNotFoundException::new);
        if (!actor.equals(root.getOwner())) throw new ProcessResourceNotFoundException();
    }

    private FlowableWorkerService worker() {
        var service = workers.getIfAvailable();
        if (service == null) throw blocked();
        return service;
    }

    private static Instant authorization(Supplier<Instant> refresh) {
        var validUntil = refresh.get();
        if (validUntil == null) throw new AccessDeniedException("Current node retry permission is required");
        return validUntil;
    }

    private static void requireGrant(Instant validUntil, Instant now) {
        if (!now.isBefore(validUntil)) throw new AccessDeniedException("Node retry permission expired");
    }

    private static NodeRetryResultDTO replay(ProcessNodeExecutionBO stored, String requestSha256) {
        if (!requestSha256.equals(stored.getRetryRequestSha256())) throw new ProcessConflictException();
        return new NodeRetryResultDTO(stored.getRetryCommandId(), stored.getInstanceId(), stored.getOccurrenceId(),
                stored.getAttempt(), stored.getNodeExecutionRef(), "SCHEDULED");
    }

    private static AutomationOperationException blocked() {
        return new AutomationOperationException(TIANJI_NODE_RETRY_BLOCKED);
    }

    private record RetryObservationVO(NodeRetryResultDTO receipt, ProcessNodeExecutionBO prior, RecoveryScopeVO scope, Instant observedAt) { }

    private record RetryIntentVO(String commandId, String nodeRef, String expectedVersion, String reason, String businessReceiptRef) {
        static RetryIntentVO from(String instanceId, NodeRetryCommand command) {
            if (!opaque(instanceId, 255) || command == null || !id(command.getCommandId()) || !id(command.getNodeExecutionRef())
                    || !opaque(command.getExpectedVersion(), 128) || !opaque(command.getReason(), 512)
                    || command.getBusinessReceiptRef() != null && !opaque(command.getBusinessReceiptRef(), 255)) {
                throw new IllegalArgumentException("Invalid node retry command");
            }
            return new RetryIntentVO(command.getCommandId(), command.getNodeExecutionRef(), command.getExpectedVersion(),
                    command.getReason(), command.getBusinessReceiptRef());
        }

        String digest(ProcessActor actor, String instanceId) {
            var data = new LinkedHashMap<String, Object>();
            data.put("tenantId", actor.tenantId());
            data.put("subjectType", actor.subjectType().name());
            data.put("issuerFingerprint", actor.issuerFingerprint());
            data.put("subjectId", actor.subjectId());
            data.put("instanceId", instanceId);
            data.put("commandId", commandId);
            data.put("nodeExecutionRef", nodeRef);
            data.put("expectedVersion", expectedVersion);
            data.put("reason", reason);
            data.put("businessReceiptRef", businessReceiptRef);
            return ProcessTypedJson.sha256("AUTOMATION_NODE_RETRY_V2", ProcessTypedJson.canonicalBytes(data, 8192));
        }

        private static boolean id(String value) {
            return value != null && value.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}");
        }

        private static boolean opaque(String value, int max) {
            return value != null && !value.isBlank() && value.length() <= max && value.chars().noneMatch(Character::isISOControl);
        }
    }
}
