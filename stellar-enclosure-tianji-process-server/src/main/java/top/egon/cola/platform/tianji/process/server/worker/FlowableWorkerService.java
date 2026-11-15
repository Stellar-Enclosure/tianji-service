package top.egon.cola.platform.tianji.process.server.worker;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.impl.jobexecutor.ExternalWorkerTaskCompleteJobHandler;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TechnicalLimitsVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO.TemplateRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.KindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodePolicyVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerCompletionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerFailureCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerReportDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchBinding;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStore;
import top.egon.cola.platform.tianji.process.server.bridge.AgentStartWorker;
import top.egon.cola.platform.tianji.process.server.bridge.FlowableAgentWaitInspector;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.WorkerLeaseLostException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceBindingBO;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.ProcessInstanceBindingStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionBO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.RecoveryDeadletter;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.execution.ProcessValues;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerBusinessError;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;
import top.egon.cola.platform.tianji.process.server.worker.WorkerLeaseStore.RecoveryLeaseEvidence;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry.FrozenWorkerContractVO;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Secured callers provide a verified SERVICE actor; all native job changes share the lease transaction. */
@Slf4j
public final class FlowableWorkerService {
    private static final Map<String, ProcessValueTypeEnum> AGENT_LEASE_INPUT = Map.of(
            "nodeExecutionRef", ProcessValueTypeEnum.STRING, "inputSha256", ProcessValueTypeEnum.STRING,
            "configurationSha256", ProcessValueTypeEnum.STRING);
    private static final String AGENT_DISPATCH_LEASE_STATES = "('PENDING','SUBMITTED')";
    private static final JsonMapper REPORT_JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> REPORT_OBJECT = new TypeReference<>() { };
    private final SpringProcessEngineConfiguration process;
    private final DataSource source;
    private final ProcessModelValidator validator;
    private final WorkerTopicRegistry registry;
    private final ProcessReleaseService releases;
    private final VerifiedProcessModels models;
    private final FlowableAgentWaitInspector agentWaits;
    private final WorkerLeaseStore leases;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final String schema;
    private final AutomationWorkerParticipants automation;
    private final ProcessCommandReceiptStore commands;
    private final ProcessApiSchemaVerifier schemaVerifier;
    private final TransactionTemplate automationRead;
    private final SecureRandom random = new SecureRandom();

    public FlowableWorkerService(SpringProcessEngineConfiguration process, SpringDmnEngineConfiguration dmn,
                                 ProcessModelValidator validator, WorkerTopicRegistry registry) {
        this(process, dmn, validator, registry, null);
    }

    public FlowableWorkerService(SpringProcessEngineConfiguration process, SpringDmnEngineConfiguration dmn,
                                 ProcessModelValidator validator, WorkerTopicRegistry registry, AutomationWorkerParticipants automation) {
        this.process = Objects.requireNonNull(process, "process");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.releases = new ProcessReleaseService(process, dmn, validator);
        this.models = new VerifiedProcessModels(process, dmn, validator);
        this.agentWaits = new FlowableAgentWaitInspector(process, releases, models, validator);
        if (!(process.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null
                || process.getManagementService() == null || process.getRuntimeService() == null) throw invalidConfiguration();
        this.source = manager.getDataSource();
        this.schema = process.getDatabaseSchema();
        this.schemaVerifier = new ProcessApiSchemaVerifier(source, schema);
        schemaVerifier.verifyWorker();
        this.automation = automation;
        this.leases = new WorkerLeaseStore(manager.getDataSource(), manager, schema);
        this.jdbc = new JdbcTemplate(manager.getDataSource());
        this.commands = new ProcessCommandReceiptStore(jdbc, schema);
        this.transaction = new TransactionTemplate(manager);
        automationRead = new TransactionTemplate(manager);
        automationRead.setReadOnly(true);
        automationRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        automationRead.setTimeout(10);
    }

    public boolean usesRecoveryStorage(SpringProcessEngineConfiguration engine, DataSource candidate) {
        return process == engine && source == candidate && transaction.getTransactionManager() == engine.getTransactionManager()
                && schema.equals(engine.getDatabaseSchema());
    }

    public List<String> findFailureJobIds(long tenantId, String instanceId) {
        requireRecoveryTransaction();
        return leases.findFailureJobIds(tenantId, instanceId);
    }

    public List<RecoveryLeaseEvidence> readRecoveryHistory(long tenantId, String jobId) {
        requireRecoveryTransaction();
        return leases.readRecoveryHistory(tenantId, jobId);
    }

    public RecoveryProvenance readRecoveryProvenance(TaskOrigin start, Definition definition, RecoveryDeadletter job) {
        requireRecoveryTransaction();
        if (start == null || start.requester() == null || "LEGACY".equals(start.requester().issuerFingerprint())
                || definition == null || definition.type() != Type.BPMN || job == null
                || !Long.toString(start.requester().tenantId()).equals(job.tenant())
                || !start.instanceId().equals(job.instanceId()) || !definition.engineDefinitionId().equals(job.definitionId())
                || job.elementId() == null || job.executionId() == null) {
            return RecoveryProvenance.unverified();
        }
        long tenantId = start.requester().tenantId();
        VerifiedJobModel released;
        try {
            released = verifiedJobModel(tenantId, recoveryIdentity(job));
        } catch (ProcessResourceNotFoundException missing) {
            return RecoveryProvenance.unverified();
        }
        long matching = released.release().definitions().stream().filter(item -> item.type() == Type.BPMN
                && definition.engineDefinitionId().equals(item.engineDefinitionId())).count();
        if (matching != 1 || !start.releaseKey().equals(released.release().releaseKey())
                || !definition.equals(released.definition())) return RecoveryProvenance.unverified();

        var model = released.model();
        var worker = model.workerTasks().get(job.elementId());
        if (model.caseSources().containsKey(job.elementId())
                || model.caseSources().values().stream().anyMatch(caseSource -> job.elementId().equals(caseSource.messageElementId())
                        || job.elementId().equals(caseSource.timerElementId()))
                || model.messageWaits().values().stream().anyMatch(wait -> job.elementId().equals(wait.workerElementId()))
                || worker != null && registry.isInternalAgentDispatch(tenantId, worker)) {
            return RecoveryProvenance.unsupported();
        }
        if (worker == null || !ProcessModelValidator.nativeWorkerTopic(worker.topic(), worker.schemaVersion()).equals(job.topic())) {
            return RecoveryProvenance.unverified();
        }
        return new RecoveryProvenance(false, released, job);
    }

    public RecoveryBinding verifyRecoveryBinding(TaskOrigin start, Definition definition, RecoveryDeadletter job,
                                                  RecoveryProvenance provenance, RecoveryLeaseEvidence selected) {
        requireRecoveryTransaction();
        if (provenance == null || !provenance.ordinary() || !provenance.job.equals(job) || selected == null
                || start == null || start.requester() == null || definition == null
                || !definition.equals(provenance.released.definition())
                || !start.releaseKey().equals(provenance.released.release().releaseKey())
                || !start.instanceId().equals(job.instanceId())) return RecoveryBinding.unverified();
        long tenantId = start.requester().tenantId();
        if (selected.tenantId() != tenantId || !Long.toString(tenantId).equals(job.tenant())
                || !"BPMN".equals(selected.scopeType()) || !job.jobId().equals(selected.jobId())
                || !job.jobId().equals(selected.occurrenceId()) || !job.instanceId().equals(selected.scopeId())
                || !job.definitionId().equals(selected.definitionId()) || !job.elementId().equals(selected.elementId())
                || !start.releaseKey().equals(selected.releaseKey()) || !definition.modelSha256().equals(selected.modelSha256())
                || !workerOccurrenceKey(tenantId, job.jobId()).equals(selected.idempotencyKey())) return RecoveryBinding.unverified();

        var reporter = registry.findRecoveryRegistration(tenantId, selected.subjectIssuer(), selected.subjectId(),
                selected.topic(), selected.schemaVersion());
        if (reporter.isEmpty() || reporter.get().internalAgentDispatch()) return RecoveryBinding.unverified();
        var registration = reporter.get();
        var identity = recoveryIdentity(job);
        VerifiedJob verified;
        try {
            requireCompatibleJob(identity, registration);
            verified = verifyJob(tenantId, identity, registration, provenance.released);
        } catch (IllegalArgumentException mismatch) {
            return RecoveryBinding.unverified();
        }
        if (!start.releaseKey().equals(verified.releaseKey()) || !definition.modelSha256().equals(verified.modelSha256())
                || !start.correlationId().equals(verified.correlationId())
                || !inputHash(identity, registration, verified.inputs()).equals(selected.inputSha256())) {
            return RecoveryBinding.unverified();
        }
        return RecoveryBinding.verified(registration.effectSafety(), verified.businessKey());
    }

    private void requireRecoveryTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source) || Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Worker recovery evidence requires the owning JDBC transaction");
        }
    }

    private static JobIdentity recoveryIdentity(RecoveryDeadletter job) {
        return new JobIdentity(job.jobId(), job.instanceId(), job.executionId(), job.definitionId(), job.elementId(), job.topic());
    }

    public static String workerOccurrenceKey(long tenantId, String jobId) {
        return "tw-" + ProcessRequestDigest.hash(List.of("worker-occurrence-v1", Long.toString(tenantId), jobId), Map.of());
    }

    public static final class RecoveryProvenance {
        private final boolean unsupported;
        private final VerifiedJobModel released;
        private final RecoveryDeadletter job;

        private RecoveryProvenance(boolean unsupported, VerifiedJobModel released, RecoveryDeadletter job) {
            this.unsupported = unsupported;
            this.released = released;
            this.job = job;
        }

        public static RecoveryProvenance unsupported() {
            return new RecoveryProvenance(true, null, null);
        }

        public static RecoveryProvenance unverified() {
            return new RecoveryProvenance(false, null, null);
        }

        public boolean ordinary() {
            return released != null;
        }

        public boolean provesUnsupported() {
            return unsupported;
        }

        public boolean provesUnverified() {
            return !unsupported && !ordinary();
        }
    }

    public record RecoveryBinding(boolean verified, WorkerEffect effect, String businessKey) {
        public static RecoveryBinding unverified() {
            return new RecoveryBinding(false, null, null);
        }

        public static RecoveryBinding verified(WorkerEffect effect, String businessKey) {
            return new RecoveryBinding(true, effect, businessKey);
        }

        public boolean provesUnverified() {
            return !verified;
        }
    }

    public AcquireWorkerTaskResult acquire(ProcessActor actor, AcquireWorkerTaskCommand command) {
        return acquire(actor, command, false);
    }

    public RecoveryScopeVO recoveryScope(ProcessActor actor, String instanceId, ProcessNodeExecutionBO prior,
                                         @Nullable String businessReceiptRef) {
        if (automation == null || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source)) throw automationUnavailable();
        var root = automation.bindings().find(actor.tenantId(), instanceId).orElseThrow(FlowableWorkerService::unavailable);
        if (!root.getOwner().equals(actor)) throw unavailable();
        if (prior.getTenantId() != root.getTenantId() || !instanceId.equals(prior.getInstanceId())
                || prior.getState() != StateEnum.WAIT_RECOVERY || prior.isExcluded()
                || prior.getCapabilityKey() == null || prior.getCapabilityVersion() == null) throw recoveryBlocked();
        automationStart(root);
        var release = automation.releases().find(root.getTenantId(), root.getReleaseKey()).orElseThrow(FlowableWorkerService::automationUnavailable);
        if (!root.getDefinitionKey().equals(release.rootDefinitionKey())) throw automationUnavailable();
        var verified = releases.verifiedAutomation(release);
        var slot = verified.controlBindings().occurrenceSlots().get(prior.getNodeId());
        if (slot == null) throw recoveryBlocked();
        var nodeBinding = release.source().nodeBindings().stream().filter(item -> item.nodeId().equals(prior.getNodeId())).findFirst()
                .orElseThrow(FlowableWorkerService::automationUnavailable);
        var frozen = registry.findFrozenContract(root.getTenantId(), prior.getCapabilityKey(), prior.getCapabilityVersion(),
                frozenWorkerSha256(release, prior)).orElseThrow(FlowableWorkerService::recoveryBlocked);
        var canonical = workerInput(root, prior, nodeBinding, frozen.capability());
        var wait = process.getManagementService().executeCommand(context -> automation.lifecycle().observeWorkerRecoveryWait(prior));
        if (!verified.root().engineDefinitionId().equals(wait.definitionId()) || !slot.activityId().equals(wait.activityId())) {
            throw recoveryBlocked();
        }
        var query = WorkerEffectRecoveryQuery.builder().actor(actor).sourceId(frozen.sourceId()).instanceId(instanceId)
                .nodeExecutionRef(prior.getNodeExecutionRef()).effectKey(prior.getEffectKey()).capabilityKey(prior.getCapabilityKey())
                .handlerVersion(frozen.handlerVersion()).nodeRevision(prior.getRevision()).capabilityVersion(prior.getCapabilityVersion())
                .attempt(prior.getAttempt()).effectFingerprint(workerFingerprint(frozen.capability(), frozen.handlerVersion(), frozen.targetId(),
                        canonical)).outputSchemaSha256(prior.getOutputSchemaSha256()).nodeDeadlineAt(prior.getNodeDeadlineAt())
                .targetId(frozen.targetId()).businessReceiptRef(businessReceiptRef).build();
        query.sha256();
        return new RecoveryScopeVO(query, nodeBinding.outputSchema(), frozen.capability().effectMode(), nodeBinding.policy(), root.getReleaseKey());
    }

    public String scheduleRecovery(ProcessNodeExecutionBO prior, @Nullable WorkerEffectRecoveryVO proof, String commandId, String reqSha) {
        if (automation == null || !TransactionSynchronizationManager.hasResource(source)) throw automationUnavailable();
        AutomationInstanceGuardCommandInterceptor.requireHeld(prior.getTenantId(), prior.getInstanceId());
        return Objects.requireNonNull(process.getManagementService().executeCommand(
                context -> automation.lifecycle().reenterWorkerRecovery(prior, proof, commandId, reqSha)));
    }

    public record RecoveryScopeVO(WorkerEffectRecoveryQuery query, ProcessSchemaVO outputSchema, WorkerEffectSafetyEnum effectMode,
                                  NodePolicyVO policy, String releaseKey) { }

    public AutomationWorkerAcquireDTO acquireAutomation(ProcessActor actor, @Valid AutomationWorkerAcquireCommand command) {
        if (command == null || !AutomationNodeBindingVO.isCapabilityKey(command.getCapabilityKey())
                || command.getCapabilityVersion() < 1 || command.getCapabilityVersion() > ProcessTypedJson.V2_MAX_WIRE_INTEGER) {
            throw invalidAutomation();
        }
        var registration = registry.requireAutomation(actor, command.getCapabilityKey(), command.getCapabilityVersion());
        requireAutomationStorage();
        WorkerNativeAvailability.require(process);
        return Objects.requireNonNull(transaction.execute(status -> {
            var candidate = automationCandidate(registration);
            if (candidate.isEmpty()) return emptyAutomation();
            var selected = candidate.orElseThrow();
            var root = automation.guard().lockKnownRoot(actor.tenantId(), selected.instanceId());
            var nativeJob = lockNative(actor.tenantId(), selected.jobId());
            if (nativeJob == null || nativeJob.live() || !root.getInstanceId().equals(nativeJob.processInstanceId())
                    || !registration.physicalTopic().equals(nativeJob.topic())) return emptyAutomation();
            var node = automation.nodes().requireWorkerAttempt(actor.tenantId(), root.getInstanceId(), nativeJob.id());
            var scope = automationScope(root, node, registration, nativeJob.definitionId(), nativeJob.elementId());
            if (node.getState() != StateEnum.WAITING || node.isExcluded()) return emptyAutomation();
            Instant now = automation.nodes().now();
            if (!now.isBefore(node.getAttemptDeadlineAt()) || !now.isBefore(node.getNodeDeadlineAt())) return emptyAutomation();
            Instant expiresAt = earlier(now.plus(registration.leaseDuration()), node.getAttemptDeadlineAt());
            expiresAt = earlier(expiresAt, node.getNodeDeadlineAt());
            var proof = node.getWorkerEffectRecovery();
            if (proof != null) {
                String reason = invalidRecoveryProof(node, registration.effectMode(), now);
                if (reason != null) {
                    process.getManagementService().executeCommand(context ->
                            automation.lifecycle().parkInvalidWorkerRecovery(node, nativeJob.id(), reason));
                    return emptyAutomation();
                }
                expiresAt = earlier(expiresAt, proof.validUntil());
            }
            expiresAt = expiresAt.truncatedTo(ChronoUnit.MILLIS);
            if (!now.isBefore(expiresAt) || !leases.hasAutomationCapacity(actor, registration.capabilityKey(),
                    registration.capabilityVersion(), registration.maxConcurrent())) return emptyAutomation();
            process.getManagementService().executeCommand(context -> {
                automation.lifecycle().verifyWorkerJob(node, nativeJob.id());
                return null;
            });
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            Arrays.fill(bytes, (byte) 0);
            String tokenHash = WorkerLeaseStore.tokenHash(token);
            String owner = "tianji-" + tokenHash;
            lockAutomationJob(root, nativeJob, owner, expiresAt);
            var claimed = new WorkerLeaseStore.Claim(actor, tokenHash, registration.capabilityKey(),
                    Long.toString(registration.capabilityVersion()), nativeJob.id(), node.getOccurrenceId(), WorkerScopeTypeEnum.BPMN,
                    root.getInstanceId(), root.getReleaseKey(), scope.definition().engineDefinitionId(), scope.nativeElementId(),
                    node.getEffectKey(), scope.definition().modelSha256(), physicalInputSha256(scope), owner, expiresAt, registration.leaseDuration());
            var lease = leases.claim(claimed);
            return new AutomationWorkerAcquireDTO(AutomationWorkerAcquireDTO.StatusEnum.ACQUIRED, automationTask(scope, lease, token));
        }));
    }

    public AutomationWorkerReportDTO completeAutomation(ProcessActor actor, @Valid AutomationWorkerCompletionCommand command) {
        if (command == null || !command.isCompletionValid() || !validOpaqueId(command.getBusinessReceiptId(), 255)
                || command.getEffectKey() == null || !command.getEffectKey().matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) {
            throw invalidAutomation();
        }
        Map<String, Object> output = REPORT_JSON.readValue(ProcessTypedJson.canonicalBytes(command.getOutput(), 64 * 1024), REPORT_OBJECT);
        return reportAutomation(actor, new WorkerReportCommandVO(command.getCommandId(), command.getTaskId(),
                command.getActivityOccurrenceId(), command.getNodeExecutionRef(), command.getLeaseToken(), command.getEffectKey(),
                command.getBusinessReceiptId(), output, null, null));
    }

    public AutomationWorkerReportDTO failAutomation(ProcessActor actor, @Valid AutomationWorkerFailureCommand command) {
        if (command == null || command.getKind() == null || !command.isFailureValid() || command.getErrorCode() == null
                || !command.getErrorCode().matches("[A-Z][A-Z0-9_]{0,127}")) throw invalidAutomation();
        return reportAutomation(actor, new WorkerReportCommandVO(command.getCommandId(), command.getTaskId(),
                command.getActivityOccurrenceId(), command.getNodeExecutionRef(), command.getLeaseToken(), null,
                command.getBusinessReceiptId(), null, command.getKind(), command.getErrorCode()));
    }

    private AutomationWorkerReportDTO reportAutomation(ProcessActor actor, WorkerReportCommandVO command) {
        if (actor == null || actor.subjectType() != ProcessActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())) {
            throw unavailable();
        }
        requireAutomationStorage();
        String tokenHash = WorkerLeaseStore.tokenHash(command.leaseToken());
        var discovered = Objects.requireNonNull(automationRead.execute(status -> {
            var original = commands.findWorkerReport(actor, command.commandId());
            var lease = findReportedLease(actor, command, tokenHash, original.isPresent());
            var registration = automationGrant(actor, lease);
            if (original.isPresent() && !command.nodeRef().equals(original.orElseThrow().resourceId())) throw conflict();
            var root = automation.bindings().find(actor.tenantId(), lease.scopeId()).orElseThrow(FlowableWorkerService::unavailable);
            var scope = reportScope(root, lease, registration, command.nodeRef(), false);
            var task = automationTask(scope, lease, command.leaseToken());
            var digest = reportDigest(actor, command, task, tokenHash);
            var replay = original.map(receipt -> replayAutomation(receipt, command, scope, lease, digest)).orElse(null);
            return new WorkerReportDiscoveryVO(root, lease, registration, task.getTaskContractSha256(), digest, replay);
        }));
        if (discovered.replay() != null) return discovered.replay();
        return Objects.requireNonNull(transaction.execute(status -> {
            var root = automation.guard().lockKnownRoot(actor.tenantId(), discovered.root().getInstanceId());
            if (!root.equals(discovered.root())) throw conflict();
            var nativeJob = lockNative(actor.tenantId(), command.taskId());
            var lease = leases.lock(actor, command.taskId(), tokenHash);
            var scope = reportScope(root, lease, discovered.registration(), command.nodeRef(), true);
            var task = automationTask(scope, lease, command.leaseToken());
            if (!discovered.taskContractSha256().equals(task.getTaskContractSha256())) throw conflict();
            var original = commands.lockIfPresent(actor, ProcessCommandReceiptStore.Kind.WORKER_REPORT_V2, command.commandId());
            if (original.isPresent()) return replayAutomation(original.orElseThrow(), command, scope, lease, discovered.digest());
            if ("EXPIRED".equals(lease.state())) throw lost();
            if (!"LEASED".equals(lease.state())) throw conflict();
            verifyAutomationNative(nativeJob, lease, scope);
            process.getManagementService().executeCommand(context -> {
                automation.lifecycle().verifyWorkerJob(scope.node(), lease.jobId());
                return null;
            });
            Instant now = automation.nodes().now();
            if (!now.isBefore(scope.node().getAttemptDeadlineAt()) || !now.isBefore(scope.node().getNodeDeadlineAt())
                    || !leases.isLive(actor, lease.jobId(), tokenHash)) throw lost();
            if (command.completion() && scope.node().getWorkerEffectRecovery() != null
                    && invalidRecoveryProof(scope.node(), scope.registration().effectMode(), now) != null) throw lost();
            if (!commands.insert(actor, ProcessCommandReceiptStore.Kind.WORKER_REPORT_V2, command.commandId(), discovered.digest(),
                    root.getReleaseKey(), scope.correlationId())) {
                var concurrent = commands.lock(actor, ProcessCommandReceiptStore.Kind.WORKER_REPORT_V2, command.commandId());
                return replayAutomation(concurrent, command, scope, lease, discovered.digest());
            }
            var finish = command.completion() ? completeAutomationNative(command, scope, nativeJob)
                    : failAutomationNative(command, scope, nativeJob, now);
            var decided = leases.finish(actor, command.taskId(), tokenHash, finish);
            commands.apply(actor, ProcessCommandReceiptStore.Kind.WORKER_REPORT_V2, command.commandId(), command.nodeRef(), null);
            return typedReceipt(decided);
        }));
    }

    private WorkerLeaseStore.Lease findReportedLease(ProcessActor actor, WorkerReportCommandVO command, String tokenHash,
                                                     boolean knownCommand) {
        try {
            return leases.find(actor, command.taskId(), tokenHash);
        } catch (ProcessResourceNotFoundException absent) {
            if (knownCommand) throw conflict();
            throw absent;
        }
    }

    private WorkerTopicRegistry.AutomationRegistrationVO automationGrant(ProcessActor actor, WorkerLeaseStore.Lease lease) {
        if (!automationLease(lease)) throw unavailable();
        try {
            long version = Long.parseLong(lease.schemaVersion());
            if (!Long.toString(version).equals(lease.schemaVersion())) throw unavailable();
            return registry.requireAutomation(actor, lease.topic(), version);
        } catch (NumberFormatException malformed) {
            throw unavailable();
        }
    }

    private WorkerAttemptScopeVO reportScope(AutomationInstanceBindingBO root, WorkerLeaseStore.Lease lease,
            WorkerTopicRegistry.AutomationRegistrationVO registration, String nodeRef, boolean guarded) {
        var node = guarded ? automation.nodes().requireWorkerAttempt(root.getTenantId(), root.getInstanceId(), nodeRef, lease.jobId())
                : automation.nodes().findWorkerAttempt(root.getTenantId(), root.getInstanceId(), nodeRef, lease.jobId())
                        .orElseThrow(FlowableWorkerService::unavailable);
        var scope = automationScope(root, node, registration, lease.definitionId(), lease.elementId());
        if (!root.getReleaseKey().equals(lease.releaseKey()) || !node.getOccurrenceId().equals(lease.occurrenceId())
                || !node.getEffectKey().equals(lease.idempotencyKey()) || !scope.definition().modelSha256().equals(lease.modelSha256())
                || !physicalInputSha256(scope).equals(lease.inputSha256()) || lease.scopeType() != WorkerScopeTypeEnum.BPMN
                || !root.getInstanceId().equals(lease.scopeId()) || lease.tenantId() != root.getTenantId()
                || !registration.capabilityKey().equals(lease.topic())
                || !Long.toString(registration.capabilityVersion()).equals(lease.schemaVersion())) throw conflict();
        return scope;
    }

    private WorkerAttemptScopeVO automationScope(AutomationInstanceBindingBO root, ProcessNodeExecutionBO node,
            WorkerTopicRegistry.AutomationRegistrationVO registration, String engineDefinitionId, String nativeElementId) {
        if (node.getTenantId() != registration.tenantId()) throw conflict();
        var release = automation.releases().find(root.getTenantId(), root.getReleaseKey()).orElseThrow(FlowableWorkerService::automationUnavailable);
        if (!root.getDefinitionKey().equals(release.rootDefinitionKey())) throw automationUnavailable();
        var verified = releases.verifiedAutomation(release);
        var slot = verified.controlBindings().occurrenceSlots().get(node.getNodeId());
        if (slot == null || !nativeElementId.equals(slot.activityId()) || !engineDefinitionId.equals(verified.root().engineDefinitionId())) {
            throw automationUnavailable();
        }
        var binding = release.source().nodeBindings().stream().filter(item -> item.nodeId().equals(node.getNodeId())).findFirst()
                .orElseThrow(FlowableWorkerService::automationUnavailable);
        if (!registration.contractSha256().equals(frozenWorkerSha256(release, node))) throw unavailable();
        Map<String, Object> input = typedObject(binding.inputSchema().normalizeValue(node.getInput()));
        byte[] canonical = workerInput(root, node, binding, registration.capability());
        String fingerprint = workerFingerprint(registration.capability(), registration.handlerVersion(), registration.targetId(), canonical);
        var start = automationStart(root);
        return new WorkerAttemptScopeVO(node, registration, binding, verified.root(), nativeElementId,
                start.correlationId(), input, rawSha256(canonical), fingerprint);
    }

    private ProcessCommandReceiptStore.Receipt automationStart(AutomationInstanceBindingBO root) {
        var start = commands.startByInstance(root.getOwner(), root.getInstanceId());
        if (!root.getReleaseKey().equals(start.releaseKey()) || !root.getResolvedStartSha256().equals(start.requestHash())
                || !root.getSubmittedAt().equals(start.createdAt()) || !root.getInstanceId().equals(start.resourceId())) throw automationUnavailable();
        return start;
    }

    private static String frozenWorkerSha256(AutomationReleaseStore.ImmutableReleaseVO release, ProcessNodeExecutionBO node) {
        if (!(release.dependencies().get("workerCapabilities") instanceof Map<?, ?> workers)
                || !(workers.get(node.getNodeId()) instanceof Map<?, ?> worker)
                || !(worker.get("contractSha256") instanceof String sha) || !sha.matches("[0-9a-f]{64}")) throw automationUnavailable();
        return sha;
    }

    private static byte[] workerInput(AutomationInstanceBindingBO root, ProcessNodeExecutionBO node, AutomationNodeBindingVO binding,
                                      AutomationNodeBindingVO.CapabilityRefVO capability) {
        if (node.getTenantId() != root.getTenantId() || !root.getInstanceId().equals(node.getInstanceId())
                || !root.getConfigurationSha256().equals(node.getConfigurationSha256())
                || !capability.capabilityKey().equals(node.getCapabilityKey()) || !Objects.equals(capability.version(), node.getCapabilityVersion())
                || !capability.inputSchemaSha256().equals(node.getInputSchemaSha256())
                || !capability.outputSchemaSha256().equals(node.getOutputSchemaSha256())
                || !capability.equals(binding.service())) throw conflict();
        var canonical = ProcessTypedJson.canonicalBytes(binding.inputSchema().normalizeValue(node.getInput()), 64 * 1024);
        if (!Arrays.equals(canonical, ProcessTypedJson.canonicalBytes(node.getInput(), 64 * 1024))
                || !ProcessTypedJson.sha256("AUTOMATION_NODE_INPUT_V2", utf8(node.getInputSchemaSha256()), canonical)
                    .equals(node.getInputSha256())) throw automationUnavailable();
        return canonical;
    }

    private static String workerFingerprint(AutomationNodeBindingVO.CapabilityRefVO capability, String handlerVersion,
                                            String targetId, byte[] canonical) {
        return ProcessTypedJson.sha256("AUTOMATION_WORKER_EFFECT_V2", utf8(capability.capabilityKey()),
                utf8(Long.toString(capability.version())), utf8(capability.inputSchemaSha256()), utf8(capability.outputSchemaSha256()),
                utf8(capability.effectMode().name()), utf8(handlerVersion), ProcessTypedJson.canonicalBytes(targetId), canonical);
    }

    @Nullable
    private static String invalidRecoveryProof(ProcessNodeExecutionBO node, WorkerEffectSafetyEnum effectMode, Instant now) {
        var proof = node.getWorkerEffectRecovery();
        if (proof == null) return null;
        if (proof.observedAt().isAfter(now) || proof.validUntil().isAfter(node.getNodeDeadlineAt()) || node.getAttempt() < 2
                || node.getRetryCommandId() == null || node.getRetryRequestSha256() == null
                || effectMode == WorkerEffectSafetyEnum.READ_ONLY
                || proof.mode() == WorkerEffectRecoveryVO.Mode.RETRY_NOT_APPLIED && effectMode != WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT) {
            return ProcessNodeLifecycleListener.WORKER_RECOVERY_PROOF_MISMATCH;
        }
        return now.isBefore(proof.validUntil()) ? null : ProcessNodeLifecycleListener.WORKER_RECOVERY_PROOF_EXPIRED;
    }

    private static AutomationOperationException recoveryBlocked() {
        return new AutomationOperationException(AutomationOperationException.Code.TIANJI_NODE_RETRY_BLOCKED);
    }

    private AutomationWorkerTaskDTO automationTask(WorkerAttemptScopeVO scope, WorkerLeaseStore.Lease lease, String token) {
        var node = scope.node();
        var registration = scope.registration();
        var task = new AutomationWorkerTaskDTO(lease.jobId(), lease.occurrenceId(), node.getNodeExecutionRef(), token,
                registration.capabilityKey(), registration.capabilityVersion(), scope.input(), scope.inputSha256(),
                node.getEffectKey(), node.getAttemptDeadlineAt(), lease.leaseExpiresAt(), registration.handlerVersion(),
                registration.effectMode(), registration.targetId(), registration.inputSchemaSha256(), registration.outputSchemaSha256(),
                scope.effectFingerprint(), node.getWorkerEffectRecovery(), null);
        if (!lease.tokenSha256().equals(task.leaseTokenSha256())) throw conflict();
        return task.setTaskContractSha256(task.contractSha256(registration.tenantId(), registration.sourceId()));
    }

    private Optional<WorkerCandidateVO> automationCandidate(WorkerTopicRegistry.AutomationRegistrationVO registration) {
        var rows = jdbc.query("""
                SELECT j.id_,j.process_instance_id_ FROM %1$s.act_ru_external_job j
                JOIN %1$s.act_ru_execution root ON root.id_=j.process_instance_id_ AND root.parent_id_ IS NULL
                    AND root.tenant_id_=j.tenant_id_ AND root.proc_def_id_=j.proc_def_id_ AND root.suspension_state_=1
                JOIN %1$s.tianji_process_node_execution n ON n.native_job_id=j.id_ AND n.instance_id=j.process_instance_id_
                    AND n.tenant_id=? AND n.state='WAITING' AND NOT n.excluded AND n.attempt_deadline_at>clock_timestamp()
                    AND n.node_deadline_at>clock_timestamp()
                WHERE j.tenant_id_=? AND j.handler_cfg_=? AND j.process_instance_id_ IS NOT NULL
                    AND (j.lock_exp_time_ IS NULL OR j.lock_exp_time_<=clock_timestamp()) AND j.retries_>0
                    AND (j.duedate_ IS NULL OR j.duedate_<=clock_timestamp())
                ORDER BY j.create_time_, j.id_ COLLATE "C" LIMIT 1
                """.formatted(schema),
                (rs, index) -> new WorkerCandidateVO(rs.getString(1), rs.getString(2)),
                registration.tenantId(), Long.toString(registration.tenantId()), registration.physicalTopic());
        return rows.stream().findFirst();
    }

    private void lockAutomationJob(AutomationInstanceBindingBO root, NativeJob nativeJob, String owner, Instant expiresAt) {
        AutomationInstanceGuardCommandInterceptor.requireHeld(root.getTenantId(), root.getInstanceId());
        process.getManagementService().executeCommand(context -> {
            var configuration = process.getJobServiceConfiguration();
            var entity = configuration.getExternalWorkerJobEntityManager().findById(nativeJob.id());
            if (entity == null || !Long.toString(root.getTenantId()).equals(entity.getTenantId())
                    || !root.getInstanceId().equals(entity.getProcessInstanceId())
                    || !nativeJob.definitionId().equals(entity.getProcessDefinitionId())
                    || !nativeJob.executionId().equals(entity.getExecutionId())
                    || !nativeJob.elementId().equals(entity.getElementId())
                    || !nativeJob.topic().equals(entity.getJobHandlerConfiguration())
                    || entity.getRetries() < 1 || !Objects.equals(nativeJob.owner(), entity.getLockOwner())) throw conflict();
            entity.setLockOwner(owner);
            entity.setLockExpirationTime(Date.from(expiresAt));
            if (entity.isExclusive()) configuration.getInternalJobManager().lockJobScope(entity);
            return null;
        });
    }

    private static void verifyAutomationNative(NativeJob job, WorkerLeaseStore.Lease lease, WorkerAttemptScopeVO scope) {
        if (job == null || !job.live() || !lease.nativeOwner().equals(job.owner())
                || !lease.jobId().equals(job.id()) || !lease.definitionId().equals(job.definitionId())
                || !lease.scopeId().equals(job.processInstanceId()) || !lease.elementId().equals(job.elementId())
                || !scope.registration().physicalTopic().equals(job.topic())
                || scope.node().getState() != StateEnum.WAITING || scope.node().isExcluded()) throw lost();
    }

    private WorkerLeaseStore.Finish completeAutomationNative(WorkerReportCommandVO command, WorkerAttemptScopeVO scope, NativeJob job) {
        var output = normalizeWorkerOutput(command, scope);
        var node = scope.node();
        var resultRef = automation.nodes().acceptWorkerResult(node, job.id(), output,
                scope.registration().effectMode() != WorkerEffectSafetyEnum.READ_ONLY);
        process.getManagementService().createExternalWorkerCompletionBuilder(job.id(), job.owner())
                .variables(Map.of("resultRef", resultRef)).complete();
        return completePhysicalReceipt(command, resultRef);
    }

    private WorkerLeaseStore.Finish failAutomationNative(WorkerReportCommandVO command, WorkerAttemptScopeVO scope, NativeJob job, Instant now) {
        var policy = scope.binding().policy();
        var node = scope.node();
        boolean reportedRetry = command.kind() == AutomationWorkerFailureCommand.KindEnum.RETRYABLE;
        boolean uncertainEffect = command.kind() == AutomationWorkerFailureCommand.KindEnum.UNKNOWN
                || reportedRetry && scope.registration().effectMode() != WorkerEffectSafetyEnum.READ_ONLY;
        boolean retry = reportedRetry && !uncertainEffect
                && "RETRY".equals(policy.onFailure().action())
                && policy.retry().retryableCodes().contains(command.errorCode())
                && node.getAttempt() < policy.retry().maxAttempts()
                && now.plus(policy.retry().backoff()).isBefore(node.getNodeDeadlineAt());
        if (uncertainEffect) {
            process.getManagementService().executeCommand(context -> {
                automation.lifecycle().parkWorkerFailure(node, job.id(), command.errorCode());
                return null;
            });
        } else if (retry) {
            process.getManagementService().executeCommand(context -> {
                automation.lifecycle().scheduleWorkerRetry(node, job.id(), command.errorCode());
                return null;
            });
        } else if ("WAIT_RECOVERY".equals(policy.onFailure().action()) || "RETRY".equals(policy.onFailure().action())) {
            process.getManagementService().executeCommand(context -> {
                automation.lifecycle().parkKnownWorkerFailure(node, job.id(), command.errorCode());
                return null;
            });
        } else {
            automation.nodes().acceptWorkerFailure(node, job.id(), command.kind(), command.errorCode(), false);
            process.getManagementService().createExternalWorkerCompletionBuilder(job.id(), job.owner()).bpmnError("TIANJI_AUTOMATION_NODE_FAILURE");
        }
        return failPhysicalReceipt(command, retry ? WorkerReceiptOutcomeEnum.RETRY_SCHEDULED : WorkerReceiptOutcomeEnum.NEEDS_MANUAL);
    }

    private static WorkerLeaseStore.Finish completePhysicalReceipt(WorkerReportCommandVO command, String resultRef) {
        String hash = ProcessRequestDigest.hash(List.of("worker-complete-v1", command.taskId(), command.occurrenceId(),
                command.businessReceiptId()), Map.of("resultRef", resultRef));
        return new WorkerLeaseStore.Finish(WorkerReceiptOutcomeEnum.COMPLETED, hash, command.businessReceiptId(), null, null);
    }

    private static WorkerLeaseStore.Finish failPhysicalReceipt(WorkerReportCommandVO command, WorkerReceiptOutcomeEnum outcome) {
        var kind = switch (command.kind()) {
            case RETRYABLE -> WorkerFailureKindEnum.RETRYABLE;
            case PERMANENT -> WorkerFailureKindEnum.BUSINESS_REJECTED;
            case UNKNOWN -> WorkerFailureKindEnum.UNKNOWN;
        };
        String error = switch (command.kind()) {
            case RETRYABLE -> "V2_RETRYABLE";
            case PERMANENT -> "V2_PERMANENT";
            case UNKNOWN -> "V2_EFFECT_UNKNOWN";
        };
        String hash = ProcessRequestDigest.hash(List.of("worker-fail-v1", command.taskId(), command.occurrenceId(), kind.name(), error), Map.of());
        return new WorkerLeaseStore.Finish(outcome, hash, null, kind, error);
    }

    private static AutomationWorkerReportDTO replayAutomation(ProcessCommandReceiptStore.Receipt original, WorkerReportCommandVO command,
            WorkerAttemptScopeVO scope, WorkerLeaseStore.Lease lease, String digest) {
        if (!digest.equals(original.requestHash()) || !command.nodeRef().equals(original.resourceId())) throw conflict();
        if (!"APPLIED".equals(original.state()) || !lease.releaseKey().equals(original.releaseKey())
                || !scope.correlationId().equals(original.correlationId()) || lease.decidedAt() == null) throw automationUnavailable();
        WorkerLeaseStore.Finish expected;
        if (command.completion()) {
            if (!"COMPLETED".equals(lease.state())) throw automationUnavailable();
            var normalized = normalizeWorkerOutput(command, scope);
            var row = scope.node();
            var outputSha = AutomationWorkerBusinessResultDTO.nodeOutputSha256(row.getOutputSchemaSha256(), normalized);
            if (!outputSha.equals(row.getOutputSha256())) throw automationUnavailable();
            expected = completePhysicalReceipt(command, ProcessNodeExecutionStore.resultRef(row, outputSha));
        } else {
            if (!Set.of("RETRY_SCHEDULED", "NEEDS_MANUAL").contains(lease.state())) throw automationUnavailable();
            expected = failPhysicalReceipt(command, WorkerReceiptOutcomeEnum.valueOf(lease.state()));
        }
        if (!expected.outcome().name().equals(lease.state()) || !expected.resultSha256().equals(lease.resultSha256())
                || !Objects.equals(expected.businessReceiptId(), lease.businessReceiptId()) || expected.failureKind() != lease.failureKind()
                || !Objects.equals(expected.failureCode(), lease.failureCode())) throw automationUnavailable();
        return typedReceipt(lease);
    }

    private static Map<String, Object> normalizeWorkerOutput(WorkerReportCommandVO command, WorkerAttemptScopeVO scope) {
        var output = typedObject(scope.binding().outputSchema().normalizeValue(command.output()));
        var effectHash = AutomationWorkerBusinessResultDTO.resultSha256(scope.effectFingerprint(), command.businessReceiptId(), output);
        var proof = scope.node().getWorkerEffectRecovery();
        if (proof != null && proof.mode() == top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO.Mode.REPLAY_APPLIED
                && (!command.businessReceiptId().equals(proof.businessReceiptId()) || !effectHash.equals(proof.resultSha256()))) throw conflict();
        return output;
    }

    private static String reportDigest(ProcessActor actor, WorkerReportCommandVO report, AutomationWorkerTaskDTO task, String tokenHash) {
        if (!task.getActivityOccurrenceId().equals(report.occurrenceId())
                || report.completion() && !task.getEffectKey().equals(report.effectKey())) throw conflict();
        var fields = new LinkedHashMap<String, Object>();
        fields.put("operation", report.completion() ? "COMPLETE" : "FAIL");
        fields.put("actor", Map.of("tenantId", actor.tenantId(), "subjectType", actor.subjectType().name(),
                "issuerFingerprint", actor.issuerFingerprint(), "subjectId", actor.subjectId()));
        fields.put("commandId", report.commandId());
        fields.put("taskId", report.taskId());
        fields.put("activityOccurrenceId", report.occurrenceId());
        fields.put("nodeExecutionRef", report.nodeRef());
        fields.put("leaseTokenSha256", tokenHash);
        fields.put("taskContractSha256", task.getTaskContractSha256());
        fields.put("effectKey", task.getEffectKey());
        fields.put("businessReceiptId", report.businessReceiptId());
        if (report.completion()) {
            fields.put("output", report.output());
        } else {
            fields.put("kind", report.kind().name());
            fields.put("errorCode", report.errorCode());
        }
        return ProcessTypedJson.sha256("AUTOMATION_WORKER_REPORT_V2", ProcessTypedJson.canonicalBytes(fields, 72 * 1024));
    }

    private void requireAutomationStorage() {
        if (automation == null || TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) throw automationUnavailable();
    }

    private static AutomationWorkerAcquireDTO emptyAutomation() {
        return new AutomationWorkerAcquireDTO(AutomationWorkerAcquireDTO.StatusEnum.EMPTY, null);
    }

    private static AutomationWorkerReportDTO typedReceipt(WorkerLeaseStore.Lease lease) {
        var outcome = switch (lease.state()) {
            case "COMPLETED" -> AutomationWorkerReportDTO.OutcomeEnum.COMPLETED;
            case "RETRY_SCHEDULED" -> AutomationWorkerReportDTO.OutcomeEnum.RETRY_SCHEDULED;
            case "NEEDS_MANUAL" -> switch (lease.failureCode()) {
                case "V2_PERMANENT" -> AutomationWorkerReportDTO.OutcomeEnum.PERMANENT;
                case "V2_EFFECT_UNKNOWN" -> AutomationWorkerReportDTO.OutcomeEnum.UNKNOWN;
                case "V2_RETRYABLE" -> AutomationWorkerReportDTO.OutcomeEnum.NEEDS_MANUAL;
                default -> throw automationUnavailable();
            };
            default -> throw automationUnavailable();
        };
        return new AutomationWorkerReportDTO(lease.jobId(), lease.occurrenceId(), outcome, lease.decidedAt());
    }

    private static Instant earlier(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String rawSha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException unavailable) {
            throw automationUnavailable();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> typedObject(Object value) {
        if (!(value instanceof Map<?, ?> object)) throw invalidAutomation();
        return (Map<String, Object>) object;
    }

    private static boolean validOpaqueId(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max && value.equals(value.trim())
                && value.chars().noneMatch(Character::isISOControl);
    }

    private boolean typedAgentLease(WorkerLeaseStore.Lease lease) {
        return automation != null && lease.scopeType() == WorkerScopeTypeEnum.BPMN
                && AgentDispatchStore.TYPED_TOPIC.equals(lease.topic()) && AgentDispatchStore.TYPED_VERSION.equals(lease.schemaVersion())
                && automation.bindings().find(lease.tenantId(), lease.scopeId()).isPresent();
    }

    private static boolean automationLease(WorkerLeaseStore.Lease lease) {
        return !lease.jobId().equals(lease.occurrenceId());
    }

    private static String physicalInputSha256(WorkerAttemptScopeVO scope) {
        var node = scope.node();
        var registration = scope.registration();
        return WorkerLeaseStore.automationPhysicalInputSha256(node.getNativeJobId(), node.getInstanceId(),
                scope.definition().engineDefinitionId(), scope.nativeElementId(), registration.capabilityKey(),
                registration.capabilityVersion(), node.getNodeExecutionRef(), node.getInputSha256(), node.getConfigurationSha256());
    }

    private static IllegalArgumentException invalidAutomation() { return new IllegalArgumentException("Invalid typed Worker command"); }
    private static IllegalStateException automationUnavailable() { return new IllegalStateException("Typed Worker evidence is unavailable"); }

    public record AutomationWorkerParticipants(ProcessInstanceBindingStore bindings, AutomationReleaseStore releases,
            AutomationInstanceGuardCommandInterceptor guard, ProcessNodeExecutionStore nodes, ProcessNodeLifecycleListener lifecycle) {
        public AutomationWorkerParticipants {
            Objects.requireNonNull(bindings);
            Objects.requireNonNull(releases);
            Objects.requireNonNull(guard);
            Objects.requireNonNull(nodes);
            Objects.requireNonNull(lifecycle);
        }
    }

    private record WorkerCandidateVO(String jobId, String instanceId) { }
    private record WorkerAttemptScopeVO(ProcessNodeExecutionBO node, WorkerTopicRegistry.AutomationRegistrationVO registration,
            AutomationNodeBindingVO binding, Definition definition, String nativeElementId, String correlationId,
            Map<String, Object> input, String inputSha256, String effectFingerprint) { }
    private record WorkerReportDiscoveryVO(AutomationInstanceBindingBO root, WorkerLeaseStore.Lease lease,
            WorkerTopicRegistry.AutomationRegistrationVO registration, String taskContractSha256, String digest,
            AutomationWorkerReportDTO replay) { }
    private record WorkerReportCommandVO(String commandId, String taskId, String occurrenceId, String nodeRef, String leaseToken,
            String effectKey, String businessReceiptId, Map<String, Object> output, AutomationWorkerFailureCommand.KindEnum kind, String errorCode) {
        private WorkerReportCommandVO {
            if (commandId == null || !commandId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                    || !validOpaqueId(taskId, 255) || !validOpaqueId(occurrenceId, 255)
                    || nodeRef == null || !nodeRef.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}")) throw invalidAutomation();
        }
        private boolean completion() { return kind == null; }
        @Override public String toString() { return "WorkerReportCommandVO[redacted]"; }
    }

    public static String typedAgentTopic() {
        return AgentDispatchStore.TYPED_TOPIC;
    }

    public static String nativeTypedAgentTopic() {
        return "tianji-v2:agent-dispatch:" + AgentDispatchStore.TYPED_VERSION;
    }

    public AcquireWorkerTaskResult acquireTypedAgentDispatch(AgentDispatchStore.TypedRoute route) {
        requireAutomationStorage();
        Objects.requireNonNull(route, "route");
        WorkerNativeAvailability.require(process);
        FlowableAgentWaitInspector.requireHistory(process);
        var actor = route.actor();
        return Objects.requireNonNull(transaction.execute(status -> {
            var selected = agentCandidate(route).orElse(null);
            if (selected == null) return empty();
            var root = automation.guard().lockKnownRoot(actor.tenantId(), selected.instanceId());
            var job = lockNative(actor.tenantId(), selected.jobId());
            if (job == null || job.live() || !nativeTypedAgentTopic().equals(job.topic())
                    || !root.getInstanceId().equals(job.processInstanceId())) return empty();
            var node = automation.nodes().findAgentAttempt(actor.tenantId(), root.getInstanceId(), job.id()).orElse(null);
            if (node == null || !currentAgentRoute(root, node, route)
                    || !agentDispatchAllowsLease(route, root.getInstanceId(), job.id())) return empty();
            Instant now = automation.nodes().now();
            if (!isAgentDispatchWaiting(node, now)) return empty();
            var scope = typedAgentScope(root, node, job);
            var expiresAt = earlier(now.plus(route.leaseDuration()), earlier(node.getAttemptDeadlineAt(), node.getNodeDeadlineAt()))
                    .truncatedTo(ChronoUnit.MILLIS);
            if (!now.isBefore(expiresAt) || !leases.hasCapacity(actor, AgentDispatchStore.TYPED_TOPIC, AgentDispatchStore.TYPED_VERSION, 1)) return empty();

            byte[] secret = new byte[32];
            random.nextBytes(secret);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
            Arrays.fill(secret, (byte) 0);
            String tokenHash = WorkerLeaseStore.tokenHash(token);
            String owner = "tianji-" + tokenHash;
            lockAutomationJob(root, job, owner, expiresAt);
            var claim = new WorkerLeaseStore.Claim(actor, tokenHash, AgentDispatchStore.TYPED_TOPIC, AgentDispatchStore.TYPED_VERSION, job.id(), job.id(),
                    WorkerScopeTypeEnum.BPMN, root.getInstanceId(), root.getReleaseKey(), job.definitionId(), job.elementId(),
                    workerOccurrenceKey(actor.tenantId(), job.id()), scope.definition().modelSha256(), node.getInputSha256(),
                    owner, expiresAt, route.leaseDuration());
            var lease = leases.claim(claim);
            return new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.ACQUIRED, new WorkerTaskDTO(job.id(), job.id(),
                    WorkerScopeTypeEnum.BPMN, root.getInstanceId(), null, root.getTenantId(), AgentDispatchStore.TYPED_TOPIC, AgentDispatchStore.TYPED_VERSION,
                    WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, AGENT_LEASE_INPUT, Map.of("runId", ProcessValueTypeEnum.STRING),
                    scope.businessKey(), scope.correlationId(), lease.idempotencyKey(), lease.leasedAt(), lease.leaseExpiresAt(),
                    typedAgentSeedInputs(node), token));
        }));
    }

    private Optional<WorkerCandidateVO> agentCandidate(AgentDispatchStore.TypedRoute route) {
        var actor = route.actor();
        var rows = jdbc.query("""
                SELECT j.id_,j.process_instance_id_ FROM %1$s.act_ru_external_job j
                JOIN %1$s.act_ru_execution root ON root.id_=j.process_instance_id_ AND root.parent_id_ IS NULL
                    AND root.tenant_id_=j.tenant_id_ AND root.proc_def_id_=j.proc_def_id_ AND root.suspension_state_=1
                JOIN %1$s.tianji_process_node_execution n ON n.native_job_id=j.id_ AND n.instance_id=j.process_instance_id_
                    AND n.tenant_id=? AND n.kind='AGENT' AND n.state='WAITING' AND NOT n.excluded
                    AND n.agent_run_id IS NULL AND n.native_subscription_id IS NULL
                    AND n.attempt_deadline_at>clock_timestamp() AND n.node_deadline_at>clock_timestamp()
                JOIN %1$s.tianji_process_instance_binding b ON b.tenant_id=n.tenant_id AND b.instance_id=n.instance_id
                LEFT JOIN %1$s.tianji_process_agent_dispatch d ON d.tenant_id=n.tenant_id AND d.occurrence_id=j.id_
                WHERE j.tenant_id_=? AND j.handler_cfg_=? AND j.retries_>0
                    AND (j.lock_exp_time_ IS NULL OR j.lock_exp_time_<=clock_timestamp())
                    AND (j.duedate_ IS NULL OR j.duedate_<=clock_timestamp())
                    AND b.configuration -> n.node_id ->> 'sourceId' = ?
                    AND b.configuration -> n.node_id ->> 'issuer' = ?
                    AND b.configuration -> n.node_id ->> 'subject' = ?
                    AND (d.occurrence_id IS NULL OR (d.state IN %2$s AND d.scope_type='BPMN' AND d.scope_id=n.instance_id
                        AND d.source_id=b.configuration -> n.node_id ->> 'sourceId'
                        AND d.subject_issuer=b.configuration -> n.node_id ->> 'issuer'
                        AND d.subject_id=b.configuration -> n.node_id ->> 'subject'
                        AND d.topic=? AND d.schema_version=?))
                ORDER BY j.create_time_, j.id_ COLLATE "C" LIMIT 1
                """.formatted(schema, AGENT_DISPATCH_LEASE_STATES), (rs, index) -> new WorkerCandidateVO(rs.getString(1), rs.getString(2)),
                actor.tenantId(), Long.toString(actor.tenantId()), nativeTypedAgentTopic(), route.sourceId(), actor.issuerFingerprint(),
                actor.subjectId(), AgentDispatchStore.TYPED_TOPIC, AgentDispatchStore.TYPED_VERSION);
        return rows.stream().findFirst();
    }

    private boolean agentDispatchAllowsLease(AgentDispatchStore.TypedRoute route, String instanceId, String jobId) {
        var actor = route.actor();
        AutomationInstanceGuardCommandInterceptor.requireHeld(actor.tenantId(), instanceId);
        // Close reconciliation can update the dispatch independently of the root and native job locks.
        var allowed = jdbc.query("""
                SELECT state IN %2$s AND scope_type='BPMN' AND scope_id=? AND source_id=?
                    AND subject_issuer=? AND subject_id=? AND topic=? AND schema_version=?
                FROM %1$s.tianji_process_agent_dispatch WHERE tenant_id=? AND occurrence_id=? FOR UPDATE
                """.formatted(schema, AGENT_DISPATCH_LEASE_STATES), (rs, row) -> rs.getBoolean(1), instanceId, route.sourceId(),
                actor.issuerFingerprint(), actor.subjectId(), AgentDispatchStore.TYPED_TOPIC, AgentDispatchStore.TYPED_VERSION,
                actor.tenantId(), jobId);
        return allowed.isEmpty() || allowed.size() == 1 && allowed.getFirst();
    }

    public AgentStartWorker.TypedDispatch prepareTypedAgentDispatch(AgentDispatchStore.TypedRoute route,
            WorkerTaskDTO task, AgentDispatchStore dispatch) {
        requireAutomationStorage();
        requireDispatchStorage(dispatch);
        return Objects.requireNonNull(transaction.execute(status -> prepareTypedAgent(route, task, dispatch).prepared()));
    }

    private LockedTypedAgentVO prepareTypedAgent(AgentDispatchStore.TypedRoute route, WorkerTaskDTO task, AgentDispatchStore dispatch) {
        var actor = route.actor();
        if (task == null || task.tenantId() != actor.tenantId() || task.scopeType() != WorkerScopeTypeEnum.BPMN
                || !AgentDispatchStore.TYPED_TOPIC.equals(task.topic()) || !AgentDispatchStore.TYPED_VERSION.equals(task.schemaVersion())
                || !task.taskId().equals(task.activityOccurrenceId())) throw unavailable();

        var root = automation.guard().lockKnownRoot(actor.tenantId(), task.processInstanceId());
        var job = lockNative(actor.tenantId(), task.taskId());
        String tokenHash = WorkerLeaseStore.tokenHash(task.leaseToken());
        var lease = leases.lock(actor, task.taskId(), tokenHash);
        var node = automation.nodes().findAgentAttempt(actor.tenantId(), root.getInstanceId(), task.taskId())
                .orElseThrow(FlowableWorkerService::lost);
        if (!isAgentDispatchWaiting(node, automation.nodes().now()) || !currentAgentRoute(root, node, route)
                || job == null || !job.live() || !nativeTypedAgentTopic().equals(job.topic()) || !lease.nativeOwner().equals(job.owner())
                || !"LEASED".equals(lease.state()) || !leases.isLive(actor, task.taskId(), tokenHash)) throw lost();
        var scope = typedAgentScope(root, node, job);
        var seedInputs = typedAgentSeedInputs(node);
        if (lease.scopeType() != WorkerScopeTypeEnum.BPMN || !task.topic().equals(lease.topic())
                || !task.schemaVersion().equals(lease.schemaVersion()) || !task.taskId().equals(lease.occurrenceId())
                || !root.getInstanceId().equals(lease.scopeId()) || !root.getReleaseKey().equals(lease.releaseKey())
                || !job.definitionId().equals(lease.definitionId()) || !job.elementId().equals(lease.elementId())
                || !scope.definition().modelSha256().equals(lease.modelSha256()) || !node.getInputSha256().equals(lease.inputSha256())
                || !scope.businessKey().equals(task.businessKey()) || !scope.correlationId().equals(task.correlationId())
                || !seedInputs.equals(task.input()) || !lease.idempotencyKey().equals(task.idempotencyKey())) throw conflict();
        var selected = scope.template();
        var binding = route.binding(selected.key(), selected.version(), selected.contentSha256(), scope.dispatchTimeout());
        var intent = dispatch.prepareTyped(new AgentDispatchStore.Seed(binding, root.getInstanceId(), job.id(), root.getReleaseKey(),
                job.definitionId(), lease.modelSha256(), job.elementId(), lease.inputSha256(), scope.businessKey(),
                scope.correlationId(), seedInputs), node);
        var command = new LinkedAgentRuntimeCommand(intent.seed().idempotencyKey(), intent.query().link(), node.getNodeExecutionRef(),
                selected.key(), selected.version(), selected.contentSha256(), node.getConfigurationSha256(), node.getInputSchemaSha256(),
                node.getOutputSchemaSha256(), scope.input(), scope.parameters(), scope.limits(), node.getAttemptDeadlineAt(),
                scope.correlationId());
        return new LockedTypedAgentVO(new AgentStartWorker.TypedDispatch(intent, command), node, job, tokenHash);
    }

    private TypedAgentScopeVO typedAgentScope(AutomationInstanceBindingBO root, ProcessNodeExecutionBO node, NativeJob job) {
        var release = automation.releases().find(root.getTenantId(), root.getReleaseKey()).orElseThrow(FlowableWorkerService::automationUnavailable);
        if (!root.getDefinitionKey().equals(release.rootDefinitionKey())) throw automationUnavailable();
        var verified = releases.verifiedAutomation(release);
        var slot = verified.controlBindings().occurrenceSlots().get(node.getNodeId());
        var definition = verified.root();
        if (slot == null || !job.elementId().equals(slot.activityId()) || !job.definitionId().equals(definition.engineDefinitionId())
                || !root.getInstanceId().equals(job.processInstanceId()) || !root.getConfigurationSha256().equals(node.getConfigurationSha256())) {
            throw automationUnavailable();
        }
        var binding = release.source().nodeBindings().stream().filter(item -> item.nodeId().equals(node.getNodeId())).findFirst()
                .orElseThrow(FlowableWorkerService::automationUnavailable);
        if (binding.agent() == null || node.getKind() != KindEnum.AGENT) throw automationUnavailable();
        var activity = process.getRuntimeService().createExecutionQuery().executionId(job.executionId())
                .processInstanceId(root.getInstanceId()).executionTenantId(Long.toString(root.getTenantId())).singleResult();
        if (activity == null || activity.isEnded() || !node.getAttemptScopeId().equals(activity.getParentId())
                || !slot.activityId().equals(activity.getActivityId())) throw unavailable();

        var frozen = typedObject(root.getEffectiveManifest().get(node.getNodeId()));
        var ref = typedObject(frozen.get("templateRef"));
        var selected = binding.agent().allowedTemplates().stream().filter(candidate -> ref.get("version") instanceof Number version
                && candidate.version() == version.longValue() && candidate.key().equals(ref.get("key"))
                && candidate.contentSha256().equals(ref.get("contentSha256"))).findFirst()
                .orElseThrow(FlowableWorkerService::automationUnavailable);
        var input = typedObject(binding.inputSchema().normalizeValue(node.getInput()));
        if (!ProcessNodeLifecycleListener.schemaSha256(binding.inputSchema()).equals(node.getInputSchemaSha256())
                || !ProcessNodeLifecycleListener.schemaSha256(binding.outputSchema()).equals(node.getOutputSchemaSha256())
                || !Arrays.equals(ProcessTypedJson.canonicalBytes(input), ProcessTypedJson.canonicalBytes(node.getInput()))) {
            throw automationUnavailable();
        }
        var nativeInstance = process.getRuntimeService().createProcessInstanceQuery().processInstanceId(root.getInstanceId())
                .processInstanceTenantId(Long.toString(root.getTenantId())).singleResult();
        if (nativeInstance == null || nativeInstance.isSuspended() || !definition.engineDefinitionId().equals(nativeInstance.getProcessDefinitionId())
                || nativeInstance.getBusinessKey() == null) throw unavailable();
        return new TypedAgentScopeVO(selected, definition, nativeInstance.getBusinessKey(), automationStart(root).correlationId(), input,
                typedObject(binding.agent().parameterSchema().normalizeValue(frozen.get("parameters"))),
                TechnicalLimitsVO.fromExactWire(typedObject(frozen.get("limits"))), binding.policy().dispatchTimeout());
    }

    private static boolean currentAgentRoute(AutomationInstanceBindingBO root, ProcessNodeExecutionBO node,
            AgentDispatchStore.TypedRoute route) {
        var owner = route.actor();
        var item = root.getEffectiveManifest().get(node.getNodeId());
        return owner.tenantId() == root.getTenantId() && item instanceof Map<?, ?> frozen
                && route.sourceId().equals(frozen.get("sourceId")) && owner.issuerFingerprint().equals(frozen.get("issuer"))
                && owner.subjectId().equals(frozen.get("subject"));
    }

    private static boolean isAgentDispatchWaiting(ProcessNodeExecutionBO node, Instant now) {
        return node.getKind() == KindEnum.AGENT && node.getState() == StateEnum.WAITING && !node.isExcluded()
                && node.getAgentRunId() == null && node.getNativeSubscriptionId() == null
                && now.isBefore(node.getAttemptDeadlineAt()) && now.isBefore(node.getNodeDeadlineAt());
    }

    private static Map<String, Object> typedAgentSeedInputs(ProcessNodeExecutionBO node) {
        return Map.of("nodeExecutionRef", node.getNodeExecutionRef(), "inputSha256", node.getInputSha256(),
                "configurationSha256", node.getConfigurationSha256());
    }

    public boolean isTypedAgentDispatchActive(AgentDispatchStore.Intent intent) {
        requireAutomationStorage();
        var seed = intent.seed();
        var actor = seed.binding().actor();
        return Boolean.TRUE.equals(automationRead.execute(status -> {
            var root = automation.bindings().find(actor.tenantId(), seed.instanceId()).orElseThrow(FlowableWorkerService::automationUnavailable);
            var row = automation.nodes().findAgentAttempt(actor.tenantId(), root.getInstanceId(), seed.occurrenceId())
                    .orElseThrow(FlowableWorkerService::automationUnavailable);
            if (!seed.releaseKey().equals(root.getReleaseKey()) || !typedAgentSeedInputs(row).equals(seed.inputs())
                    || row.getAgentRunId() != null && !row.getAgentRunId().equals(intent.runId())) throw conflict();
            var now = automation.nodes().now();
            if (row.getState() != StateEnum.WAITING || row.isExcluded() || !now.isBefore(row.getAttemptDeadlineAt())
                    || !now.isBefore(row.getNodeDeadlineAt())) return false;
            String tenant = Long.toString(root.getTenantId());
            var live = process.getRuntimeService().createProcessInstanceQuery().processInstanceId(root.getInstanceId())
                    .processInstanceTenantId(tenant).singleResult();
            if (live != null) {
                if (!seed.processDefinitionId().equals(live.getProcessDefinitionId())
                        || !seed.businessKey().equals(live.getBusinessKey())) throw conflict();
                return true;
            }
            var ended = process.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(root.getInstanceId())
                    .processInstanceTenantId(tenant).singleResult();
            if (ended == null || ended.getEndTime() == null || !seed.processDefinitionId().equals(ended.getProcessDefinitionId())
                    || !seed.businessKey().equals(ended.getBusinessKey())) throw automationUnavailable();
            return false;
        }));
    }

    public void completeTypedAgentDispatch(AgentDispatchStore.TypedRoute route, WorkerTaskDTO task,
            AgentDispatchStore dispatch, String runId) {
        requireAutomationStorage();
        requireDispatchStorage(dispatch);
        transaction.executeWithoutResult(status -> {
            var locked = prepareTypedAgent(route, task, dispatch);
            dispatch.markAcknowledged(locked.prepared().intent().seed().key(), runId);
            var job = locked.job();
            automation.nodes().bindAgentDispatch(locked.node(), job.id(), runId);
            String receiptId = "agent-" + runId;
            String hash = ProcessRequestDigest.hash(List.of("worker-complete-v1", job.id(), job.id(), receiptId), Map.of("runId", runId));
            var finish = new WorkerLeaseStore.Finish(WorkerReceiptOutcomeEnum.COMPLETED, hash, receiptId, null, null);
            scheduleNativeAgentAck(locked.node(), job);
            leases.finish(route.actor(), job.id(), locked.tokenHash(), finish);
        });
    }

    private void scheduleNativeAgentAck(ProcessNodeExecutionBO node, NativeJob job) {
        AutomationInstanceGuardCommandInterceptor.requireHeld(node.getTenantId(), node.getInstanceId());
        process.getManagementService().executeCommand(context -> {
            var jobs = process.getJobServiceConfiguration();
            var entity = jobs.getExternalWorkerJobEntityManager().findById(job.id());
            if (entity == null || entity.isExclusive() || !node.getInstanceId().equals(entity.getProcessInstanceId())
                    || !Long.toString(node.getTenantId()).equals(entity.getTenantId())
                    || !job.definitionId().equals(entity.getProcessDefinitionId()) || !job.executionId().equals(entity.getExecutionId())
                    || !job.elementId().equals(entity.getElementId()) || !job.owner().equals(entity.getLockOwner())
                    || !nativeTypedAgentTopic().equals(entity.getJobHandlerConfiguration())
                    || !ExternalWorkerTaskCompleteJobHandler.TYPE.equals(entity.getJobHandlerType())) throw lost();
            // Only the sidecar carries application data. The native command's out-parameter path
            // casts to ExternalWorkerServiceTask, while the signed AGENT activity is a ServiceTask.
            entity.setJobHandlerConfiguration(null);
            var continuation = jobs.getJobManager().moveExternalWorkerJobToExecutableJob(entity);
            if (continuation == null || !job.id().equals(continuation.getId())
                    || !job.executionId().equals(continuation.getExecutionId())
                    || continuation.getJobHandlerConfiguration() != null) throw conflict();
            return null;
        });
    }

    private record TypedAgentScopeVO(TemplateRefVO template, Definition definition, String businessKey, String correlationId,
            Map<String, Object> input, Map<String, Object> parameters, TechnicalLimitsVO limits, Duration dispatchTimeout) { }

    private record LockedTypedAgentVO(AgentStartWorker.TypedDispatch prepared, ProcessNodeExecutionBO node, NativeJob job,
            String tokenHash) { }

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
            String inputHash = inputHash(nativeJob.identity(), registration, inputs);
            String intent = workerOccurrenceKey(actor.tenantId(), nativeJob.id());
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
            if (automationLease(current) || typedAgentLease(current)) throw unavailable();
            var registration = registry.require(actor, current.topic(), current.schemaVersion());
            requireMode(registration, agentDispatch);
            if (!command.activityOccurrenceId().equals(current.occurrenceId())) throw conflict();
            if (!"LEASED".equals(current.state())) return receipt(leases.finish(actor, command.taskId(), hash, finish));
            NativeJob nativeJob = lockNative(actor.tenantId(), command.taskId());
            current = leases.lock(actor, command.taskId(), hash);
            if (!"LEASED".equals(current.state())) return receipt(leases.finish(actor, command.taskId(), hash, finish));
            VerifiedJob verified = verifyLease(actor, current, nativeJob, registration);
            Map<String, Object> output = ProcessValues.inputs(registration.outputs(), command.output());
            if (!current.inputSha256().equals(inputHash(nativeJob.identity(), registration, verified.inputs()))) throw conflict();
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
            if (!lease.inputSha256().equals(inputHash(job.identity(), registration, verified.inputs()))) throw conflict();
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
            if (automationLease(current) || typedAgentLease(current)) throw unavailable();
            var registration = registry.require(actor, current.topic(), current.schemaVersion());
            requireMode(registration, agentDispatch);
            if (!command.activityOccurrenceId().equals(current.occurrenceId())) throw conflict();
            if (!"LEASED".equals(current.state())) return replayFailure(actor, command, hash, resultHash, current);
            NativeJob nativeJob = lockNative(actor.tenantId(), command.taskId());
            current = leases.lock(actor, command.taskId(), hash);
            if (!"LEASED".equals(current.state())) return replayFailure(actor, command, hash, resultHash, current);
            VerifiedJob verified = verifyLease(actor, current, nativeJob, registration);
            if (!current.inputSha256().equals(inputHash(nativeJob.identity(), registration, verified.inputs()))) throw conflict();
            WorkerReceiptOutcomeEnum outcome;
            if (command.kind() == WorkerFailureKindEnum.BUSINESS_REJECTED && !agentDispatch
                    && !registration.internalAgentDispatch() && verified.businessError() != null) {
                scheduleBusinessError(actor.tenantId(), nativeJob, verified.businessError());
                outcome = WorkerReceiptOutcomeEnum.BUSINESS_REJECTED;
            } else {
                boolean retry = command.kind() == WorkerFailureKindEnum.RETRYABLE && nativeJob.retries() > 1;
                outcome = retry ? WorkerReceiptOutcomeEnum.RETRY_SCHEDULED : WorkerReceiptOutcomeEnum.NEEDS_MANUAL;
                var failure = process.getManagementService().createExternalWorkerJobFailureBuilder(nativeJob.id(), nativeJob.owner())
                        .errorMessage(command.errorCode()).retries(retry ? nativeJob.retries() - 1 : 0);
                if (retry) failure.retryTimeout(registration.retryDelay());
                failure.fail();
            }
            var finish = new WorkerLeaseStore.Finish(outcome, resultHash, null, command.kind(), command.errorCode());
            return receipt(leases.finish(actor, command.taskId(), hash, finish));
        }));
    }

    /** The receipt accepts asynchronous native error work; it does not assert that the boundary has advanced. */
    private void scheduleBusinessError(long tenantId, NativeJob original, WorkerBusinessError businessError) {
        String code = businessError.errorCode();
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}")) throw businessErrorUnavailable();
        var management = process.getManagementService();
        management.createExternalWorkerCompletionBuilder(original.id(), original.owner()).bpmnError(code);
        var external = management.createExternalWorkerJobQuery().jobId(original.id()).listPage(0, 2);
        if (external == null || !external.isEmpty()) throw businessErrorUnavailable();
        var executable = management.createJobQuery().jobId(original.id()).listPage(0, 2);
        if (executable == null || executable.size() != 1) throw businessErrorUnavailable();
        var scheduled = executable.getFirst();
        if (scheduled == null || !original.id().equals(scheduled.getId())
                || !Long.toString(tenantId).equals(scheduled.getTenantId())
                || !original.processInstanceId().equals(scheduled.getProcessInstanceId())
                || !original.definitionId().equals(scheduled.getProcessDefinitionId())
                || !original.executionId().equals(scheduled.getExecutionId())
                || !original.elementId().equals(scheduled.getElementId())
                || !"external-worker-complete".equals(scheduled.getJobHandlerType())
                || !("error:" + code).equals(scheduled.getJobHandlerConfiguration())) throw businessErrorUnavailable();
    }

    private static IllegalStateException businessErrorUnavailable() {
        return new IllegalStateException("Worker business-error scheduling could not be verified");
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
        JobIdentity identity = job == null ? null : job.identity();
        requireCompatibleJob(identity, registration);
        return verifyJob(tenantId, identity, registration, verifiedJobModel(tenantId, identity));
    }

    private static void requireCompatibleJob(JobIdentity job, WorkerTopicRegistry.Registration registration) {
        if (job == null || job.processInstanceId() == null || job.executionId() == null || job.definitionId() == null
                || job.elementId() == null || !ProcessModelValidator.nativeWorkerTopic(registration.topic(),
                registration.schemaVersion()).equals(job.topic())) throw unavailable();
    }

    private VerifiedJobModel verifiedJobModel(long tenantId, JobIdentity job) {
        var release = releases.getForProcessDefinition(tenantId, job.definitionId());
        Definition definition = release.definitions().stream().filter(item -> item.type() == Type.BPMN
                && item.engineDefinitionId().equals(job.definitionId())).findFirst().orElseThrow(FlowableWorkerService::unavailable);
        return new VerifiedJobModel(release, definition, models.runningProcess(tenantId, definition));
    }

    private VerifiedJob verifyJob(long tenantId, JobIdentity job, WorkerTopicRegistry.Registration registration,
                                  VerifiedJobModel released) {
        var release = released.release();
        var processBinding = released.definition();
        var model = released.model();
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
                ProcessValues.inputs(registration.inputs(), inputs), model.workerBusinessErrors().get(job.elementId()));
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

    private static String inputHash(JobIdentity job, WorkerTopicRegistry.Registration registration, Map<String, Object> inputs) {
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
                             String definitionId, String elementId, String topic, int retries, boolean live) {
        private JobIdentity identity() {
            return new JobIdentity(id, processInstanceId, executionId, definitionId, elementId, topic);
        }
    }

    private record JobIdentity(String id, String processInstanceId, String executionId,
                               String definitionId, String elementId, String topic) { }
    private record VerifiedJobModel(ProcessReleaseService.Release release, Definition definition,
                                    ValidatedProcessModel model) { }
    private record VerifiedJob(String releaseKey, String modelSha256, String businessKey, String correlationId,
                               Map<String, Object> inputs, WorkerBusinessError businessError) { }
}
