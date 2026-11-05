package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.common.engine.impl.context.Context;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailuresDTO;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerCommand;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerResult;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessWorkerRecoveryBlockedException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.Receipt;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.LockedRoot;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.RecoveryDeadletter;
import top.egon.cola.platform.tianji.process.server.execution.WorkerRecoverySpecification.Facts;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Release;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.StatusEnum.RETRY_AVAILABLE;
import static top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerResult.StatusEnum.RETRY_SCHEDULED;
import static top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.Kind.PROCESS_WORKER_RECOVER;

/** Scoped Worker failure observation and one fenced native recovery on the owning ProcessDB. */
public final class ProcessWorkerRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(ProcessWorkerRecoveryService.class);
    private static final int MAX_JOBS = 128;
    private static final String ADMISSION_POLICY = "READ_ONLY_EXHAUSTED_RETRY_V1";
    private static final Set<String> VERSION_CONFLICT_STATES = Set.of("40001", "40P01", "55P03");

    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final ProcessReleaseService releases;
    private final ProcessCommandReceiptStore receipts;
    private final ProcessControlNativeAccess nativeAccess;
    private final ProcessWorkerRecoveryReceiptStore recoveryReceipts;
    private final FlowableWorkerService workers;
    private final TransactionTemplate read;
    private final TransactionTemplate write;
    private final WorkerRecoverySpecification specification = new WorkerRecoverySpecification();

    public ProcessWorkerRecoveryService(SpringProcessEngineConfiguration engine, ProcessReleaseService releases,
            ProcessCommandReceiptStore receipts, ProcessControlNativeAccess nativeAccess,
            ProcessWorkerRecoveryReceiptStore recoveryReceipts, FlowableWorkerService workers) {
        Objects.requireNonNull(engine, "engine");
        this.releases = Objects.requireNonNull(releases, "releases");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
        this.nativeAccess = Objects.requireNonNull(nativeAccess, "nativeAccess");
        this.recoveryReceipts = Objects.requireNonNull(recoveryReceipts, "recoveryReceipts");
        this.workers = Objects.requireNonNull(workers, "workers");
        DataSource configured = engine.getDataSource();
        if (configured instanceof TransactionAwareDataSourceProxy proxy) configured = proxy.getTargetDataSource();
        if (!(engine.getTransactionManager() instanceof DataSourceTransactionManager manager)
                || manager.getDataSource() == null || manager.getDataSource() != configured
                || manager.isValidateExistingTransaction()
                || !nativeAccess.usesEngine(engine) || !nativeAccess.usesDataSource(configured)
                || !recoveryReceipts.usesDataSource(configured) || !workers.usesRecoveryStorage(engine, configured)) {
            throw new IllegalArgumentException("Process Worker recovery requires its exact owning JDBC engine");
        }
        source = configured;
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(5);
        read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(10);
        write = new TransactionTemplate(manager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        write.setTimeout(10);
    }

    public ProcessWorkerFailuresDTO workerFailures(ProcessActor actor, GetProcessInstanceQuery query) {
        requireEntry(actor, query);
        try {
            return Objects.requireNonNull(read.execute(status -> {
                nativeAccess.verifyRecoveryTransaction();
                jdbc.execute("SET LOCAL statement_timeout='5s'");
                TaskOrigin start = ownedStart(actor, query.instanceId(), false);
                LockedRoot root = nativeAccess.readRecoveryRoot(start).orElse(null);
                if (root != null) requireOwnedRoot(start, root);
                String definitionId = root == null ? nativeAccess.recoveryHistoryDefinitionId(start) : root.engineDefinitionId();
                Definition definition = exactReleasedBpmn(start, definitionId);
                var nativeRows = nativeAccess.findRecoveryDeadletters(start);
                var historicalIds = workers.findFailureJobIds(actor.tenantId(), start.instanceId());
                var byJob = nativeByJob(start, nativeRows);
                var jobIds = candidateIds(byJob.keySet(), historicalIds);
                var result = new ArrayList<ProcessWorkerFailureDTO>(jobIds.size());
                for (String jobId : jobIds) {
                    RecoveryDeadletter deadletter = byJob.get(jobId);
                    var decision = specification.evaluate(recoveryFacts(start, definition, root, deadletter, jobId));
                    result.add(new ProcessWorkerFailureDTO(jobId, decision.activityId(), deadletter != null,
                            decision.status(), decision.reason(), decision.reportedFailure(), decision.version()));
                }
                return new ProcessWorkerFailuresDTO(start.instanceId(), nativeAccess.now(), result);
            }));
        } catch (StartMissing missing) {
            throw new ProcessResourceNotFoundException();
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    public RecoverProcessWorkerResult recoverWorker(ProcessActor actor, RecoverProcessWorkerCommand command) {
        requireEntry(actor, command);
        String requestHash = ProcessRequestDigest.hash(List.of("TIANJI_WORKER_RECOVERY_COMMAND_V1", command.instanceId(),
                command.jobId(), command.commandId(), command.expectedVersion(), command.reason()), Map.of());
        try {
            RecoverProcessWorkerResult result = Objects.requireNonNull(write.execute(status -> recover(actor, command, requestHash)));
            log.info("Process Worker recovery decision returned: tenant={} status={}", actor.tenantId(), result.status());
            return result;
        } catch (RuntimeException failure) {
            RuntimeException outcome = classifyAfterRollback(failure);
            log.warn("Process Worker recovery failed: tenant={} type={}", actor.tenantId(), outcome.getClass().getSimpleName());
            throw outcome;
        }
    }

    private RecoverProcessWorkerResult recover(ProcessActor actor, RecoverProcessWorkerCommand command, String requestHash) {
        nativeAccess.verifyRecoveryTransaction();
        jdbc.execute("SET LOCAL statement_timeout='5s'");
        jdbc.execute("SET LOCAL lock_timeout='5s'");
        TaskOrigin start = ownedStart(actor, command.instanceId(), true);
        var prior = receipts.lockIfPresent(actor, PROCESS_WORKER_RECOVER, command.commandId());
        if (prior.isPresent()) return exactReplay(prior.orElseThrow(), actor, start, command, requestHash);
        if (!receipts.insert(actor, PROCESS_WORKER_RECOVER, command.commandId(), requestHash, start.releaseKey(), start.correlationId())) {
            Receipt winner = receipts.lockIfPresent(actor, PROCESS_WORKER_RECOVER, command.commandId())
                    .orElseThrow(ProcessWorkerRecoveryService::unavailable);
            return exactReplay(winner, actor, start, command, requestHash);
        }

        LockedRoot root = nativeAccess.lockRoot(start).orElseThrow(ProcessWorkerRecoveryBlockedException::new);
        requireOwnedRoot(start, root);
        if (root.suspensionState() != 1) throw new ProcessWorkerRecoveryBlockedException();
        Definition definition = exactReleasedBpmn(start, root.engineDefinitionId());
        RecoveryDeadletter deadletter = nativeAccess.lockRecoveryDeadletter(start, definition, command.jobId())
                .orElseThrow(ProcessWorkerRecoveryBlockedException::new);
        if (!command.jobId().equals(deadletter.jobId()) || !definition.engineDefinitionId().equals(deadletter.definitionId())) {
            throw unavailable();
        }
        var decision = specification.evaluate(recoveryFacts(start, definition, root, deadletter, command.jobId()));
        if (decision.status() != RETRY_AVAILABLE) throw new ProcessWorkerRecoveryBlockedException();
        if (!command.expectedVersion().equals(decision.version())) throw new ProcessVersionConflictException();

        nativeAccess.moveAndVerifyRecovery(deadletter);
        var decidedAt = recoveryReceipts.decisionTime();
        var selected = decision.qualifying();
        var result = new RecoverProcessWorkerResult(start.instanceId(), command.jobId(), selected.occurrenceId(),
                command.commandId(), RETRY_SCHEDULED, 1, decidedAt);
        var entry = new ProcessWorkerRecoveryReceiptStore.Entry(actor, requestHash, command, start,
                definition.definitionKey(), definition.engineDefinitionId(), definition.modelSha256(), root.businessKey(),
                selected.occurrenceId(), deadletter.executionId(), deadletter.elementId(), deadletter.revision(),
                decision.evidenceSha256(), ADMISSION_POLICY, selected.failureCode(), selected.decidedAt(), result);
        if (!recoveryReceipts.insert(entry)) throw new ProcessWorkerRecoveryBlockedException();
        receipts.apply(actor, PROCESS_WORKER_RECOVER, command.commandId(), command.jobId(), null);
        return result;
    }

    private Facts recoveryFacts(TaskOrigin start, Definition definition, LockedRoot root,
                                RecoveryDeadletter deadletter, String jobId) {
        var history = workers.readRecoveryHistory(start.requester().tenantId(), jobId);
        if (history == null || history.size() > 33) throw unavailable();
        var execution = deadletter == null ? null : nativeAccess.readRecoveryExecution(start, definition, deadletter).orElse(null);
        boolean collision = deadletter != null && nativeAccess.hasRecoveryCollision(jobId);
        boolean alreadyAccepted = deadletter != null && recoveryReceipts.hasAccepted(start, jobId);
        return new Facts(start, definition, jobId, root, deadletter, execution, collision, alreadyAccepted, history,
                () -> workers.readRecoveryProvenance(start, definition, deadletter),
                selected -> nativeAccess.recoveryFailureCodeMatches(deadletter, selected.failureCode()),
                (provenance, selected) -> workers.verifyRecoveryBinding(start, definition, deadletter, provenance, selected));
    }

    private RecoverProcessWorkerResult exactReplay(Receipt generic, ProcessActor actor, TaskOrigin start,
                                                    RecoverProcessWorkerCommand command, String requestHash) {
        if (!"APPLIED".equals(generic.state()) || generic.requestHash() == null
                || !generic.requestHash().matches("[0-9a-f]{64}")) throw unavailable();
        if (!requestHash.equals(generic.requestHash())) throw new ProcessConflictException();
        if (!command.jobId().equals(generic.resourceId()) || !start.releaseKey().equals(generic.releaseKey())
                || !start.correlationId().equals(generic.correlationId()) || generic.decisionResult() != null) {
            throw unavailable();
        }
        RecoverProcessWorkerResult result = recoveryReceipts.find(actor, start, generic, command, requestHash);
        if (result == null || !start.instanceId().equals(result.instanceId()) || !command.jobId().equals(result.jobId())
                || !command.commandId().equals(result.commandId())) throw unavailable();
        return result;
    }

    private TaskOrigin ownedStart(ProcessActor actor, String instanceId, boolean lock) {
        TaskOrigin start;
        try {
            start = lock ? receipts.lockStartByInstance(actor, instanceId) : receipts.readStartByInstance(actor, instanceId);
        } catch (ProcessResourceNotFoundException missing) {
            throw new StartMissing();
        }
        if (start == null || !actor.equals(start.requester()) || !instanceId.equals(start.instanceId())
                || start.createdAt() == null || start.commandId() == null || !start.commandId().matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                || start.requestHash() == null || !start.requestHash().matches("[0-9a-f]{64}")
                || start.releaseKey() == null || !start.releaseKey().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                || start.correlationId() == null || !start.correlationId().matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) {
            throw unavailable();
        }
        return start;
    }

    private Definition exactReleasedBpmn(TaskOrigin start, String definitionId) {
        Release owned = releases.get(start.requester(), start.releaseKey());
        Release current = releases.getForProcessDefinition(start.requester().tenantId(), definitionId);
        if (owned == null || current == null || !start.releaseKey().equals(owned.releaseKey())
                || !start.releaseKey().equals(current.releaseKey())) throw unavailable();
        var ownedDefinition = exactDefinition(owned, definitionId);
        if (!ownedDefinition.equals(exactDefinition(current, definitionId))) throw unavailable();
        return ownedDefinition;
    }

    private static Definition exactDefinition(Release release, String definitionId) {
        var rows = release.definitions().stream().filter(item -> item.type() == Type.BPMN
                && definitionId.equals(item.engineDefinitionId())).toList();
        if (rows.size() != 1 || rows.getFirst().modelSha256() == null
                || !rows.getFirst().modelSha256().matches("[0-9a-f]{64}")) throw unavailable();
        return rows.getFirst();
    }

    private static void requireOwnedRoot(TaskOrigin start, LockedRoot root) {
        if (start.requester().tenantId() != root.tenantId() || !start.instanceId().equals(root.instanceId())) {
            throw unavailable();
        }
    }

    private static Map<String, RecoveryDeadletter> nativeByJob(TaskOrigin start, List<RecoveryDeadletter> nativeRows) {
        if (nativeRows == null || nativeRows.size() > MAX_JOBS) throw unavailable();
        String tenant = Long.toString(start.requester().tenantId());
        var byJob = new HashMap<String, RecoveryDeadletter>();
        for (RecoveryDeadletter row : nativeRows) {
            if (row == null || !safeJobId(row.jobId()) || !tenant.equals(row.tenant())
                    || !start.instanceId().equals(row.instanceId()) || byJob.putIfAbsent(row.jobId(), row) != null) {
                throw unavailable();
            }
        }
        return byJob;
    }

    private static List<String> candidateIds(Set<String> nativeIds, List<String> historicalIds) {
        if (historicalIds == null || historicalIds.size() > MAX_JOBS) throw unavailable();
        var historical = new HashSet<String>();
        for (String jobId : historicalIds) {
            if (!safeJobId(jobId) || !historical.add(jobId)) throw unavailable();
        }
        var union = new HashSet<>(nativeIds);
        union.addAll(historical);
        if (union.size() > MAX_JOBS) throw unavailable();
        return union.stream().sorted((left, right) -> Arrays.compareUnsigned(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8))).toList();
    }

    private static boolean safeJobId(String jobId) {
        return jobId != null && !jobId.isBlank() && jobId.length() <= 255 && jobId.equals(jobId.trim())
                && jobId.chars().noneMatch(c -> c < 32 || c == 127) && StandardCharsets.UTF_8.newEncoder().canEncode(jobId);
    }

    private void requireEntry(ProcessActor actor, Object input) {
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint()) || input == null) {
            throw new IllegalArgumentException("Process Worker recovery input is invalid");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(source) || Context.getCommandContext() != null) throw unavailable();
    }

    private static RuntimeException classifyAfterRollback(RuntimeException failure) {
        if (failure instanceof StartMissing) return new ProcessResourceNotFoundException();
        if (failure instanceof ProcessConflictException || failure instanceof ProcessVersionConflictException
                || failure instanceof ProcessWorkerRecoveryBlockedException) return failure;
        Throwable cursor = failure;
        for (int depth = 0; cursor != null && depth < 16; depth++, cursor = cursor.getCause()) {
            if (cursor instanceof SQLException sql && sql.getSQLState() != null) {
                return VERSION_CONFLICT_STATES.contains(sql.getSQLState()) ? new ProcessVersionConflictException() : unavailable();
            }
        }
        return unavailable();
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Process Worker recovery is unavailable");
    }

    private static final class StartMissing extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
