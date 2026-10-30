package top.egon.cola.platform.tianji.process.server.worker;

import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskResult;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.FailWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerAcquireStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchBinding;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStore;
import top.egon.cola.platform.tianji.process.server.bridge.FlowableAgentWaitInspector;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.WorkerLeaseLostException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.execution.ProcessValues;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Secured callers provide a verified SERVICE actor; all native job changes share the lease transaction. */
public final class FlowableWorkerService {
    private final SpringProcessEngineConfiguration process;
    private final ProcessModelValidator validator;
    private final WorkerTopicRegistry registry;
    private final ProcessReleaseService releases;
    private final VerifiedProcessModels models;
    private final FlowableAgentWaitInspector agentWaits;
    private final WorkerLeaseStore leases;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final String schema;
    private final SecureRandom random = new SecureRandom();

    public FlowableWorkerService(SpringProcessEngineConfiguration process, SpringDmnEngineConfiguration dmn,
                                 ProcessModelValidator validator, WorkerTopicRegistry registry) {
        this.process = Objects.requireNonNull(process, "process");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.releases = new ProcessReleaseService(process, dmn, validator);
        this.models = new VerifiedProcessModels(process, dmn, validator);
        this.agentWaits = new FlowableAgentWaitInspector(process, releases, models, validator);
        if (!(process.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null
                || process.getManagementService() == null || process.getRuntimeService() == null) throw invalidConfiguration();
        this.schema = process.getDatabaseSchema();
        new ProcessApiSchemaVerifier(manager.getDataSource(), schema).verifyWorker();
        this.leases = new WorkerLeaseStore(manager.getDataSource(), manager, schema);
        this.jdbc = new JdbcTemplate(manager.getDataSource());
        this.transaction = new TransactionTemplate(manager);
    }

    public AcquireWorkerTaskResult acquire(ProcessActor actor, AcquireWorkerTaskCommand command) {
        return acquire(actor, command, false);
    }

    public AcquireWorkerTaskResult acquireAgentDispatch(AgentDispatchBinding binding) {
        Objects.requireNonNull(binding, "binding");
        FlowableAgentWaitInspector.requireHistory(process);
        return acquire(binding.actor(), new AcquireWorkerTaskCommand(binding.topic(), binding.schemaVersion()), true);
    }

    private AcquireWorkerTaskResult acquire(ProcessActor actor, AcquireWorkerTaskCommand command, boolean agentDispatch) {
        Objects.requireNonNull(command, "command");
        var registration = registry.require(actor, command.topic(), command.schemaVersion());
        requireMode(registration, agentDispatch);
        WorkerNativeAvailability.require(process);
        return Objects.requireNonNull(transaction.execute(status -> {
            if (!leases.hasCapacity(actor, command.topic(), command.schemaVersion(), registration.maxConcurrent())) return empty();
            byte[] secret = new byte[32];
            random.nextBytes(secret);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
            Arrays.fill(secret, (byte) 0);
            String tokenHash = WorkerLeaseStore.tokenHash(token);
            String nativeOwner = "tianji-" + tokenHash;
            var acquired = process.getManagementService().createExternalWorkerJobAcquireBuilder()
                    .topic(ProcessModelValidator.nativeWorkerTopic(command.topic(), command.schemaVersion()), registration.leaseDuration())
                    .onlyBpmn().tenantId(Long.toString(actor.tenantId())).acquireAndLock(1, nativeOwner, 1);
            if (acquired.isEmpty()) return empty();
            if (acquired.size() != 1) throw conflict();
            var job = acquired.getFirst();
            NativeJob nativeJob = lockNative(actor.tenantId(), job.getId());
            if (nativeJob == null || !nativeOwner.equals(nativeJob.owner()) || !nativeJob.live()) throw conflict();
            VerifiedJob verified = verifyJob(actor.tenantId(), nativeJob, registration);
            Map<String, Object> inputs = ProcessValues.inputs(registration.inputs(), job.getVariables());
            if (!inputs.equals(verified.inputs())) throw conflict();
            String inputHash = inputHash(nativeJob, registration, inputs);
            String intent = "tw-" + ProcessRequestDigest.hash(List.of("worker-occurrence-v1", Long.toString(actor.tenantId()), nativeJob.id()), Map.of());
            var claim = new WorkerLeaseStore.Claim(actor, tokenHash, command.topic(), command.schemaVersion(), nativeJob.id(), nativeJob.id(),
                    WorkerScopeTypeEnum.BPMN, nativeJob.processInstanceId(), verified.releaseKey(), nativeJob.definitionId(), nativeJob.elementId(),
                    intent, verified.modelSha256(), inputHash, nativeOwner, nativeJob.expiresAt(), registration.leaseDuration());
            var lease = leases.claim(claim);
            NativeJob rechecked = lockNative(actor.tenantId(), nativeJob.id());
            if (rechecked == null || !nativeOwner.equals(rechecked.owner()) || !rechecked.live()
                    || !leases.isLive(actor, nativeJob.id(), tokenHash)) throw conflict();
            var task = new WorkerTaskDTO(lease.jobId(), lease.occurrenceId(), WorkerScopeTypeEnum.BPMN,
                    lease.scopeId(), null, actor.tenantId(), lease.topic(), lease.schemaVersion(),
                    WorkerEffectSafetyEnum.valueOf(registration.effectSafety().name()), contractSchema(registration.inputs()),
                    contractSchema(registration.outputs()), verified.businessKey(),
                    verified.correlationId(), lease.idempotencyKey(), lease.leasedAt(), lease.leaseExpiresAt(), inputs, token);
            return new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.ACQUIRED, task);
        }));
    }

    public WorkerReceiptDTO complete(ProcessActor actor, CompleteWorkerTaskCommand command) {
        return complete(actor, command, false);
    }

    private WorkerReceiptDTO complete(ProcessActor actor, CompleteWorkerTaskCommand command, boolean agentDispatch) {
        Objects.requireNonNull(command, "command");
        String hash = WorkerLeaseStore.tokenHash(command.leaseToken());
        String resultHash = ProcessRequestDigest.hash(List.of("worker-complete-v1", command.taskId(), command.activityOccurrenceId(),
                command.businessReceiptId()), command.output());
        var finish = new WorkerLeaseStore.Finish(WorkerReceiptOutcomeEnum.COMPLETED, resultHash, command.businessReceiptId(), null, null);
        return Objects.requireNonNull(transaction.execute(status -> {
            var current = leases.find(actor, command.taskId(), hash);
            var registration = registry.require(actor, current.topic(), current.schemaVersion());
            requireMode(registration, agentDispatch);
            if (!command.activityOccurrenceId().equals(current.occurrenceId())) throw conflict();
            if (!"LEASED".equals(current.state())) return receipt(leases.finish(actor, command.taskId(), hash, finish));
            NativeJob nativeJob = lockNative(actor.tenantId(), command.taskId());
            current = leases.lock(actor, command.taskId(), hash);
            if (!"LEASED".equals(current.state())) return receipt(leases.finish(actor, command.taskId(), hash, finish));
            VerifiedJob verified = verifyLease(actor, current, nativeJob, registration);
            Map<String, Object> output = ProcessValues.inputs(registration.outputs(), command.output());
            if (!current.inputSha256().equals(inputHash(nativeJob, registration, verified.inputs()))) throw conflict();
            process.getManagementService().createExternalWorkerCompletionBuilder(nativeJob.id(), nativeJob.owner()).variables(output).complete();
            return receipt(leases.finish(actor, command.taskId(), hash, finish));
        }));
    }

    /** Internal AgentStartWorker seam; it derives the occurrence and input from the verified native lease. */
    public AgentDispatchStore.Intent prepareAgentDispatch(ProcessActor actor, WorkerTaskDTO task,
                                                         AgentDispatchBinding binding, AgentDispatchStore dispatch) {
        requireDispatchStorage(dispatch);
        FlowableAgentWaitInspector.requireHistory(process);
        if (task == null || binding == null || !binding.actor().equals(actor) || task.tenantId() != actor.tenantId()) throw unavailable();
        String hash = WorkerLeaseStore.tokenHash(task.leaseToken());
        return Objects.requireNonNull(transaction.execute(status -> {
            NativeJob job = lockNative(actor.tenantId(), task.taskId());
            var lease = leases.lock(actor, task.taskId(), hash);
            var registration = registry.require(actor, lease.topic(), lease.schemaVersion());
            requireMode(registration, true);
            if (!"LEASED".equals(lease.state()) || !lease.occurrenceId().equals(task.activityOccurrenceId())) throw lost();
            if (!binding.topic().equals(lease.topic()) || !binding.schemaVersion().equals(lease.schemaVersion())
                    || registration.effectSafety() != ValidatedProcessModel.WorkerEffect.EXTERNAL_IDEMPOTENT
                    || !registration.outputs().equals(Map.of("runId", ValidatedProcessModel.ValueType.STRING))) throw unavailable();
            VerifiedJob verified = verifyLease(actor, lease, job, registration);
            if (!lease.inputSha256().equals(inputHash(job, registration, verified.inputs()))) throw conflict();
            var intent = dispatch.prepare(new AgentDispatchStore.Seed(binding, lease.scopeId(), lease.occurrenceId(), lease.releaseKey(),
                    lease.definitionId(), lease.modelSha256(), lease.elementId(), lease.inputSha256(),
                    verified.businessKey(), verified.correlationId(), verified.inputs()));
            NativeJob rechecked = lockNative(actor.tenantId(), task.taskId());
            if (rechecked == null || !rechecked.live() || !lease.nativeOwner().equals(rechecked.owner())
                    || !leases.isLive(actor, lease.jobId(), hash)) throw lost();
            return intent;
        }));
    }

    /** Correlation, Worker receipt and native completion either all commit or all roll back. */
    public WorkerReceiptDTO completeAgentDispatch(ProcessActor actor, WorkerTaskDTO task, AgentDispatchBinding binding,
                                                  AgentDispatchStore dispatch, String runId) {
        requireDispatchStorage(dispatch);
        return Objects.requireNonNull(transaction.execute(status -> {
            // Preserve the native-job -> lease -> dispatch lock order used by prepare.
            var intent = prepareAgentDispatch(actor, task, binding, dispatch);
            dispatch.markAcknowledged(intent.seed().key(), runId);
            return complete(actor, new CompleteWorkerTaskCommand(task.taskId(), task.activityOccurrenceId(), task.leaseToken(),
                    "agent-" + runId, Map.of("runId", runId)), true);
        }));
    }

    /** Read-only liveness observation; reconciliation repeats it and never locks Flowable around a network call. */
    public boolean isAgentDispatchActive(AgentDispatchStore.Intent intent) {
        var observation = inspectAgentDispatch(intent);
        return observation.state() != FlowableAgentWaitInspector.State.PROCESS_ENDED
                && observation.state() != FlowableAgentWaitInspector.State.TIMER_WON;
    }

    public FlowableAgentWaitInspector.Observation inspectAgentDispatch(AgentDispatchStore.Intent intent) { return agentWaits.inspect(intent); }

    public void requireStorage(SpringProcessEngineConfiguration candidate, DataSourceTransactionManager manager) {
        if (process != candidate || process.getTransactionManager() != manager) throw invalidConfiguration();
    }

    public void requireStorage(SpringProcessEngineConfiguration candidate, DataSourceTransactionManager manager, WorkerTopicRegistry registry) {
        requireStorage(candidate, manager);
        if (this.registry != registry) throw invalidConfiguration();
    }

    private void requireDispatchStorage(AgentDispatchStore dispatch) {
        if (dispatch == null || !(process.getTransactionManager() instanceof DataSourceTransactionManager manager)) throw invalidConfiguration();
        dispatch.requireStorage(manager.getDataSource(), manager, process.getDatabaseSchema());
    }

    public WorkerReceiptDTO fail(ProcessActor actor, FailWorkerTaskCommand command) {
        return fail(actor, command, false);
    }

    public WorkerReceiptDTO failAgentDispatch(ProcessActor actor, FailWorkerTaskCommand command) {
        return fail(actor, command, true);
    }

    private WorkerReceiptDTO fail(ProcessActor actor, FailWorkerTaskCommand command, boolean agentDispatch) {
        Objects.requireNonNull(command, "command");
        String hash = WorkerLeaseStore.tokenHash(command.leaseToken());
        String resultHash = ProcessRequestDigest.hash(List.of("worker-fail-v1", command.taskId(), command.activityOccurrenceId(),
                command.kind().name(), command.errorCode()), Map.of());
        return Objects.requireNonNull(transaction.execute(status -> {
            var current = leases.find(actor, command.taskId(), hash);
            var registration = registry.require(actor, current.topic(), current.schemaVersion());
            requireMode(registration, agentDispatch);
            if (!command.activityOccurrenceId().equals(current.occurrenceId())) throw conflict();
            if (!"LEASED".equals(current.state())) return replayFailure(actor, command, hash, resultHash, current);
            NativeJob nativeJob = lockNative(actor.tenantId(), command.taskId());
            current = leases.lock(actor, command.taskId(), hash);
            if (!"LEASED".equals(current.state())) return replayFailure(actor, command, hash, resultHash, current);
            VerifiedJob verified = verifyLease(actor, current, nativeJob, registration);
            if (!current.inputSha256().equals(inputHash(nativeJob, registration, verified.inputs()))) throw conflict();
            boolean retry = command.kind() == WorkerFailureKindEnum.RETRYABLE && nativeJob.retries() > 1;
            WorkerReceiptOutcomeEnum outcome = retry ? WorkerReceiptOutcomeEnum.RETRY_SCHEDULED : WorkerReceiptOutcomeEnum.NEEDS_MANUAL;
            var failure = process.getManagementService().createExternalWorkerJobFailureBuilder(nativeJob.id(), nativeJob.owner())
                    .errorMessage(command.errorCode()).retries(retry ? nativeJob.retries() - 1 : 0);
            if (retry) failure.retryTimeout(registration.retryDelay());
            failure.fail();
            var finish = new WorkerLeaseStore.Finish(outcome, resultHash, null, command.kind(), command.errorCode());
            return receipt(leases.finish(actor, command.taskId(), hash, finish));
        }));
    }

    private WorkerReceiptDTO replayFailure(ProcessActor actor, FailWorkerTaskCommand command, String hash, String resultHash,
                                            WorkerLeaseStore.Lease lease) {
        WorkerReceiptOutcomeEnum outcome;
        if ("EXPIRED".equals(lease.state())) throw lost();
        try { outcome = WorkerReceiptOutcomeEnum.valueOf(lease.state()); }
        catch (IllegalArgumentException invalid) { throw conflict(); }
        if (outcome == WorkerReceiptOutcomeEnum.COMPLETED) throw conflict();
        WorkerLeaseStore.Finish finish;
        try { finish = new WorkerLeaseStore.Finish(outcome, resultHash, null, command.kind(), command.errorCode()); }
        catch (IllegalArgumentException invalid) { throw conflict(); }
        return receipt(leases.finish(actor, command.taskId(), hash, finish));
    }

    private VerifiedJob verifyLease(ProcessActor actor, WorkerLeaseStore.Lease lease, NativeJob job,
                                    WorkerTopicRegistry.Registration registration) {
        if (job == null || !job.live() || !lease.nativeOwner().equals(job.owner())
                || !leases.isLive(actor, lease.jobId(), lease.tokenSha256())) throw lost();
        if (!lease.scopeId().equals(job.processInstanceId()) || !lease.definitionId().equals(job.definitionId())
                || !lease.elementId().equals(job.elementId()) || lease.scopeType() != WorkerScopeTypeEnum.BPMN) throw conflict();
        VerifiedJob verified = verifyJob(actor.tenantId(), job, registration);
        if (!lease.releaseKey().equals(verified.releaseKey()) || !lease.modelSha256().equals(verified.modelSha256())) throw conflict();
        return verified;
    }

    private VerifiedJob verifyJob(long tenantId, NativeJob job, WorkerTopicRegistry.Registration registration) {
        if (job == null || job.processInstanceId() == null || job.executionId() == null || job.definitionId() == null || job.elementId() == null
                || !ProcessModelValidator.nativeWorkerTopic(registration.topic(), registration.schemaVersion()).equals(job.topic())) throw unavailable();
        var release = releases.getForProcessDefinition(tenantId, job.definitionId());
        Definition processBinding = release.definitions().stream().filter(item -> item.type() == Type.BPMN
                && item.engineDefinitionId().equals(job.definitionId())).findFirst().orElseThrow(FlowableWorkerService::unavailable);
        var model = models.runningProcess(tenantId, processBinding);
        var worker = model.workerTasks().get(job.elementId());
        if (worker == null || !registration.topic().equals(worker.topic()) || !registration.schemaVersion().equals(worker.schemaVersion())
                || !registry.acceptsModel(tenantId, worker)) throw unavailable();
        boolean linkedWait = model.messageWaits().values().stream().anyMatch(wait -> wait.workerElementId().equals(job.elementId()));
        if (linkedWait != registration.internalAgentDispatch()) throw unavailable();
        ValidatedProcessModel decision = null;
        if (!model.decisionTasks().isEmpty()) {
            String key = model.decisionTasks().values().iterator().next();
            Definition binding = release.definitions().stream().filter(item -> item.type() == Type.DMN && item.definitionKey().equals(key))
                    .findFirst().orElseThrow(FlowableWorkerService::unavailable);
            decision = models.decision(tenantId, binding);
            validator.validateDecisionBinding(model, processBinding.inputs(), decision);
        }
        validator.validateWorkerBindings(model, processBinding.inputs(), decision);
        ProcessInstance instance = process.getRuntimeService().createProcessInstanceQuery().processInstanceId(job.processInstanceId())
                .processInstanceTenantId(Long.toString(tenantId)).singleResult();
        if (instance == null || !job.definitionId().equals(instance.getProcessDefinitionId()) || instance.getBusinessKey() == null) throw unavailable();
        List<String> correlations = jdbc.query("SELECT correlation_id FROM \"" + schema + "\".tianji_process_command_receipt "
                        + "WHERE tenant_id=? AND command_type='START' AND state='APPLIED' AND resource_id=? AND release_key=?",
                (rs, row) -> rs.getString("correlation_id"), tenantId, job.processInstanceId(), release.releaseKey());
        if (correlations.size() != 1) throw unavailable();
        Map<String, Object> inputs = new HashMap<>();
        for (var mapping : worker.inputs()) inputs.put(mapping.target(), process.getRuntimeService().getVariable(job.executionId(), mapping.source()));
        return new VerifiedJob(release.releaseKey(), processBinding.modelSha256(), instance.getBusinessKey(), correlations.getFirst(),
                ProcessValues.inputs(registration.inputs(), inputs));
    }

    private NativeJob lockNative(long tenantId, String jobId) {
        String table = '"' + schema + "\".act_ru_external_job";
        List<String> rows = jdbc.query("SELECT id_ FROM " + table + " WHERE id_=? AND tenant_id_=? FOR UPDATE",
                (rs, row) -> rs.getString("id_"), jobId, Long.toString(tenantId));
        if (rows.isEmpty()) return null;
        if (rows.size() != 1) throw unavailable();
        return jdbc.queryForObject("SELECT id_,lock_owner_,lock_exp_time_,process_instance_id_,execution_id_,proc_def_id_,element_id_, "
                        + "handler_cfg_,retries_,COALESCE(lock_exp_time_ > clock_timestamp(), false) AS live FROM " + table + " WHERE id_=? AND tenant_id_=?",
                (rs, row) -> {
                    Timestamp expires = rs.getTimestamp("lock_exp_time_");
                    return new NativeJob(rs.getString("id_"), rs.getString("lock_owner_"), expires == null ? null : expires.toInstant(),
                            rs.getString("process_instance_id_"), rs.getString("execution_id_"), rs.getString("proc_def_id_"),
                            rs.getString("element_id_"), rs.getString("handler_cfg_"), rs.getInt("retries_"), rs.getBoolean("live"));
                }, jobId, Long.toString(tenantId));
    }

    private static String inputHash(NativeJob job, WorkerTopicRegistry.Registration registration, Map<String, Object> inputs) {
        List<String> fields = new ArrayList<>(List.of("worker-input-v2", job.id(), job.processInstanceId(), job.definitionId(), job.elementId(),
                registration.topic(), registration.schemaVersion(), registration.effectSafety().name(), Integer.toString(registration.maxConcurrent()),
                Long.toString(registration.leaseDuration().toMillis()), Long.toString(registration.retryDelay().toMillis())));
        addPolicy(fields, registration.inputs(), registration.inputSources());
        addPolicy(fields, registration.outputs(), registration.outputTargets());
        if (registration.internalAgentDispatch()) fields.add("internal-agent-dispatch-v1");
        return ProcessRequestDigest.hash(fields, inputs);
    }

    private static Map<String, ProcessValueTypeEnum> contractSchema(Map<String, ValidatedProcessModel.ValueType> schema) {
        return schema.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                entry -> ProcessValueTypeEnum.valueOf(entry.getValue().name())));
    }

    private static void addPolicy(List<String> fields, Map<String, ValidatedProcessModel.ValueType> types, Map<String, Set<String>> mappings) {
        fields.add(Integer.toString(types.size()));
        for (var entry : new TreeMap<>(types).entrySet()) {
            fields.add(entry.getKey());
            fields.add(entry.getValue().name());
            var allowed = new TreeSet<>(mappings.get(entry.getKey()));
            fields.add(Integer.toString(allowed.size()));
            fields.addAll(allowed);
        }
    }
    private static AcquireWorkerTaskResult empty() { return new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.EMPTY, null); }
    private static void requireMode(WorkerTopicRegistry.Registration registration, boolean agentDispatch) {
        if (registration.internalAgentDispatch() != agentDispatch) throw unavailable();
    }
    private static WorkerReceiptDTO receipt(WorkerLeaseStore.Lease lease) {
        return new WorkerReceiptDTO(lease.jobId(), lease.occurrenceId(), WorkerReceiptOutcomeEnum.valueOf(lease.state()), lease.decidedAt());
    }
    private static IllegalArgumentException invalidConfiguration() { return new IllegalArgumentException("Worker execution requires shared Flowable JDBC storage"); }
    private static ProcessResourceNotFoundException unavailable() { return new ProcessResourceNotFoundException(); }
    private static ProcessConflictException conflict() { return new ProcessConflictException(); }
    private static WorkerLeaseLostException lost() { return new WorkerLeaseLostException(); }

    private record NativeJob(String id, String owner, Instant expiresAt, String processInstanceId, String executionId,
                             String definitionId, String elementId, String topic, int retries, boolean live) { }
    private record VerifiedJob(String releaseKey, String modelSha256, String businessKey, String correlationId,
                               Map<String, Object> inputs) { }
}
