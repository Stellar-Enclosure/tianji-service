package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.cmmn.api.CmmnRuntimeService;
import org.flowable.cmmn.engine.impl.persistence.entity.PlanItemInstanceEntity;
import org.flowable.cmmn.engine.impl.util.CommandContextUtil;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.impl.AbstractEngineConfiguration;
import org.flowable.common.engine.impl.context.Context;
import org.flowable.common.spring.SpringEngineConfiguration;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.ActOnPlanItemCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.CaseOperationResult;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.StartCaseCommand;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.CaseContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedCaseModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.task.PinnedCaseActivityBehaviorFactory;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Direct Case commands own a bounded transaction; native callbacks only use the explicitly participating policy reader. */
public final class FlowableCaseService {
    private static final String PREFIX = "tianji.api.process.case.";
    private final SpringCmmnEngineConfiguration engine;
    private final SpringProcessEngineConfiguration process;
    private final top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry sourceRegistry;
    private final top.egon.cola.platform.tianji.process.server.bridge.CaseCompletionInbox caseInbox;
    private final java.util.function.BooleanSupplier completionReady;
    private final DataSource source;
    private final CmmnRuntimeService runtime;
    private final ProcessReleaseService releases;
    private final VerifiedProcessModels models;
    private final CaseStartProvenanceStore provenance;
    private final CasePlanItemReceiptStore planReceipts;
    private final ProcessCommandReceiptStore commands;
    private final CaseNativeAccess nativeAccess;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate read;
    private final TransactionTemplate write;
    private final boolean newStartsEnabled;

    public FlowableCaseService(SpringProcessEngineConfiguration process, SpringCmmnEngineConfiguration cmmn, SpringDmnEngineConfiguration dmn,
            ProcessModelValidator validator, CaseStartProvenanceStore provenance, CasePlanItemReceiptStore planReceipts) {
        this(process, cmmn, dmn, validator, provenance, planReceipts, false);
    }
    public FlowableCaseService(SpringProcessEngineConfiguration process, SpringCmmnEngineConfiguration cmmn, SpringDmnEngineConfiguration dmn,
            ProcessModelValidator validator, CaseStartProvenanceStore provenance, CasePlanItemReceiptStore planReceipts, boolean newStartsEnabled) {
        this(process,cmmn,dmn,validator,provenance,planReceipts,newStartsEnabled,null,null,()->false);
    }

    public FlowableCaseService(SpringProcessEngineConfiguration process, SpringCmmnEngineConfiguration cmmn, SpringDmnEngineConfiguration dmn,
            ProcessModelValidator validator, CaseStartProvenanceStore provenance, CasePlanItemReceiptStore planReceipts, boolean newStartsEnabled,
            top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry registry,
            top.egon.cola.platform.tianji.process.server.bridge.CaseCompletionInbox inbox, java.util.function.BooleanSupplier completionReady) {
        this.engine = Objects.requireNonNull(cmmn, "cmmn"); this.provenance = Objects.requireNonNull(provenance, "provenance"); this.planReceipts = Objects.requireNonNull(planReceipts, "planReceipts");
        Objects.requireNonNull(process, "process"); Objects.requireNonNull(dmn, "dmn"); Objects.requireNonNull(validator, "validator");
        if (!(cmmn.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null) throw invalidConfiguration();
        source = manager.getDataSource(); String schema = cmmn.getDatabaseSchema();
        storage(process, process, source, manager, schema); storage(cmmn, cmmn, source, manager, schema); storage(dmn, dmn, source, manager, schema);
        if (!provenance.usesDataSource(source) || !planReceipts.usesDataSource(source)) throw invalidConfiguration();
        PinnedCaseActivityBehaviorFactory.requireConfigured(cmmn);
        runtime = Objects.requireNonNull(cmmn.getCmmnRuntimeService(), "runtime"); releases = new ProcessReleaseService(process, cmmn, dmn, validator); models = new VerifiedProcessModels(process, cmmn, dmn, validator);
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5); commands = new ProcessCommandReceiptStore(jdbc, schema); nativeAccess = new CaseNativeAccess(source, cmmn);
        read = new TransactionTemplate(manager); read.setReadOnly(true); read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); read.setTimeout(10);
        write = new TransactionTemplate(manager); write.setTimeout(10); this.newStartsEnabled = newStartsEnabled;
        this.process = process; sourceRegistry = registry; caseInbox = inbox; this.completionReady = Objects.requireNonNull(completionReady,"completionReady");
        if ((registry == null) != (inbox == null)) throw invalidConfiguration();
        if (inbox != null) inbox.requireStorage(source,manager,schema);
    }

    /** Joins the actual current BPMN transaction; owner comes only from its durable APPLIED START. */
    public CaseStartProvenanceStore.Entry startLinked(top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.Registration registration, NativeSourceFacts facts) {
        if (sourceRegistry == null || caseInbox == null || registration == null || facts == null || !sourceRegistry.current(registration)) throw unavailable();
        var execution = nativeSource(registration,facts); Instant deadline = linkedNow().plusSeconds(10);
        String occurrence = ProcessRequestDigest.hash(List.of("TIANJI_CASE_SOURCE_OCCURRENCE_LOCK_V1",Long.toString(registration.actor().tenantId()),facts.parentProcessInstanceId(),facts.sourceOccurrenceId()),Map.of());
        jdbc.query("SELECT pg_advisory_xact_lock(?)",rs->{},ByteBuffer.wrap(HexFormat.of().parseHex(occurrence)).getLong()); linkedCurrent(deadline);
        var parent = parentStart(registration.actor().tenantId(),facts.parentProcessInstanceId()); var binding = registration.binding();
        if (!binding.releaseKey().equals(parent.releaseKey()) || !binding.sourceElementId().equals(facts.sourceElementId())) throw unavailable();
        var release = releases.get(parent.actor(),parent.releaseKey());
        var parentDefinition = release.definitions().stream().filter(value->value.type()==Type.BPMN && value.definitionKey().equals(binding.parentDefinitionKey())).findFirst().orElseThrow(FlowableCaseService::unavailable);
        var caseDefinition = release.definitions().stream().filter(value->value.type()==Type.CMMN && value.definitionKey().equals(binding.caseDefinitionKey())).findFirst().orElseThrow(FlowableCaseService::unavailable);
        if (!facts.parentDefinitionId().equals(parentDefinition.engineDefinitionId()) || !binding.parentModelSha256().equals(parentDefinition.modelSha256()) || !binding.caseModelSha256().equals(caseDefinition.modelSha256())) throw unavailable();
        var model = models.runningProcess(binding.tenantId(),parentDefinition); var policy = model.caseSources().get(facts.sourceElementId());
        if (policy == null || !binding.caseDefinitionKey().equals(policy.caseDefinitionKey()) || models.caseModel(binding.tenantId(),caseDefinition) == null) throw unavailable();
        var root = execution.getProcessInstance();
        if (root == null || !facts.parentProcessInstanceId().equals(root.getId()) || !facts.parentDefinitionId().equals(root.getProcessDefinitionId()) || !Long.toString(binding.tenantId()).equals(root.getTenantId()) || !safeReference(root.getBusinessKey(),255)) throw unavailable();
        String business = root.getBusinessKey();
        String commandId = ProcessRequestDigest.hash(List.of("TIANJI_LINKED_CASE_START_ID_V1",Long.toString(binding.tenantId()),facts.parentProcessInstanceId(),facts.sourceOccurrenceId()),Map.of());
        String requestHash = ProcessRequestDigest.hash(List.of("TIANJI_LINKED_CASE_START_V1",Long.toString(binding.tenantId()),facts.parentProcessInstanceId(),facts.parentDefinitionId(),facts.sourceElementId(),facts.sourceOccurrenceId(),facts.sourceExecutionId(),
                binding.releaseKey(),binding.parentDefinitionKey(),binding.parentModelSha256(),binding.caseDefinitionKey(),binding.caseModelSha256(),caseDefinition.engineDefinitionId(),
                parent.actor().subjectType().name(),parent.actor().issuerFingerprint(),parent.actor().subjectId(),parent.commandId(),parent.requestHash(),registration.actor().issuerFingerprint(),registration.actor().subjectId(),business,parent.correlationId()),Map.of());
        var accepted = provenance.byLinkedOccurrence(binding.tenantId(),facts.parentProcessInstanceId(),facts.sourceOccurrenceId());
        if (accepted.isPresent()) {
            var existing = accepted.orElseThrow(); provenance.lockScope(binding.tenantId(),existing.seed().caseInstanceId());
            existing = provenance.byLinkedOccurrence(binding.tenantId(),facts.parentProcessInstanceId(),facts.sourceOccurrenceId()).orElseThrow(FlowableCaseService::unavailable);
            validateLinked(existing,registration,facts,parent,parentDefinition,caseDefinition,business,commandId,requestHash);
            var base = commands.lockIfPresent(registration.actor(),ProcessCommandReceiptStore.Kind.CASE_START,commandId).orElseThrow(FlowableCaseService::unavailable);
            if (!"APPLIED".equals(base.state()) || !requestHash.equals(base.requestHash()) || !existing.seed().caseInstanceId().equals(base.resourceId()) || !parent.releaseKey().equals(base.releaseKey()) || !parent.correlationId().equals(base.correlationId())) throw unavailable();
            if (!sourceRegistry.current(registration)) throw unavailable(); linkedCurrent(deadline); return existing;
        }
        if (!newStartsEnabled || !completionReady.getAsBoolean() || !sourceRegistry.current(registration)) throw unavailable();
        if (commands.lockIfPresent(registration.actor(),ProcessCommandReceiptStore.Kind.CASE_START,commandId).isPresent()
                || !commands.insert(registration.actor(),ProcessCommandReceiptStore.Kind.CASE_START,commandId,requestHash,parent.releaseKey(),parent.correlationId())) throw unavailable();
        linkedCurrent(deadline);
        var created = runtime.createCaseInstanceBuilder().caseDefinitionId(caseDefinition.engineDefinitionId()).tenantId(Long.toString(binding.tenantId())).businessKey(business).start();
        if (created == null || !safeReference(created.getId(),255) || !caseDefinition.engineDefinitionId().equals(created.getCaseDefinitionId()) || !Long.toString(binding.tenantId()).equals(created.getTenantId()) || !business.equals(created.getBusinessKey())) throw unavailable();
        linkedCurrent(deadline); commands.apply(registration.actor(),ProcessCommandReceiptStore.Kind.CASE_START,commandId,created.getId(),null);
        var seed = new CaseStartProvenanceStore.Seed(created.getId(),"LINKED_BPMN",business,parent.correlationId(),parent.releaseKey(),caseDefinition.definitionKey(),caseDefinition.engineDefinitionId(),caseDefinition.modelSha256(),parent.actor(),"START",parent.commandId(),parent.requestHash(),registration.actor(),commandId,requestHash,registration.sourceId(),facts.parentProcessInstanceId(),facts.sourceOccurrenceId(),facts.sourceElementId(),parentDefinition.definitionKey(),parentDefinition.engineDefinitionId(),parentDefinition.modelSha256());
        var result = provenance.insert(seed); if (!seed.equals(result.seed())) throw unavailable(); caseInbox.prepare(result);
        if (!sourceRegistry.current(registration) || !completionReady.getAsBoolean()) throw unavailable(); linkedCurrent(deadline); return result;
    }
    private org.flowable.engine.impl.persistence.entity.ExecutionEntity nativeSource(top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.Registration registration, NativeSourceFacts facts) {
        var command = Context.getCommandContext();
        if (command == null || org.flowable.engine.impl.util.CommandContextUtil.getProcessEngineConfiguration()!=process || !TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) throw unavailable();
        top.egon.cola.platform.tianji.process.server.decision.PinnedDecisionActivityBehaviorFactory.requireCaseSourcesConfigured(process);
        var execution = org.flowable.engine.impl.util.CommandContextUtil.getExecutionEntityManager(command).findById(facts.sourceExecutionId());
        if (execution == null || !facts.sourceExecutionId().equals(execution.getId()) || !facts.parentProcessInstanceId().equals(execution.getProcessInstanceId()) || !facts.parentDefinitionId().equals(execution.getProcessDefinitionId())
                || !facts.sourceElementId().equals(execution.getCurrentActivityId()) || !(execution.getCurrentFlowElement() instanceof org.flowable.bpmn.model.CaseServiceTask) || !Long.toString(registration.actor().tenantId()).equals(execution.getTenantId())) throw unavailable();
        var activity = org.flowable.engine.impl.util.CommandContextUtil.getActivityInstanceEntityManager(command).findUnfinishedActivityInstance(execution);
        if (activity == null || !facts.sourceOccurrenceId().equals(activity.getId()) || activity.getEndTime()!=null || !facts.sourceExecutionId().equals(activity.getExecutionId()) || !facts.parentProcessInstanceId().equals(activity.getProcessInstanceId())
                || !facts.parentDefinitionId().equals(activity.getProcessDefinitionId()) || !facts.sourceElementId().equals(activity.getActivityId()) || !Long.toString(registration.actor().tenantId()).equals(activity.getTenantId())) throw unavailable();
        return execution;
    }
    private ParentStart parentStart(long tenant,String instanceId) {
        var rows = jdbc.query("SELECT tenant_id,subject_type,subject_issuer,subject_id,command_id,request_sha256,release_key,correlation_id,state,resource_id,created_at FROM "+'"'+engine.getDatabaseSchema()+"\".tianji_process_command_receipt WHERE tenant_id=? AND command_type='START' AND state='APPLIED' AND resource_id=?",(rs,row)->{
            try {
                var actor = new ProcessActor(rs.getLong("tenant_id"),ProcessActor.SubjectType.valueOf(rs.getString("subject_type")),rs.getString("subject_id"),rs.getString("subject_issuer"));
                if (actor.tenantId()!=tenant || "LEGACY".equals(actor.issuerFingerprint()) || !"APPLIED".equals(rs.getString("state")) || !instanceId.equals(rs.getString("resource_id"))
                        || !commandReference(rs.getString("command_id")) || !commandReference(rs.getString("correlation_id")) || rs.getString("request_sha256")==null || !rs.getString("request_sha256").matches("[0-9a-f]{64}") || rs.getString("release_key")==null || !rs.getString("release_key").matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) throw unavailable();
                return new ParentStart(actor,rs.getString("command_id"),rs.getString("request_sha256"),rs.getString("release_key"),rs.getString("correlation_id"),rs.getTimestamp("created_at").toInstant());
            } catch (RuntimeException malformed) { throw unavailable(); }
        },tenant,instanceId);
        if (rows.size()!=1) throw unavailable(); return rows.getFirst();
    }
    private static void validateLinked(CaseStartProvenanceStore.Entry existing,top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.Registration registration,NativeSourceFacts facts,ParentStart parent,Definition parentDefinition,Definition caseDefinition,String business,String commandId,String hash) {
        var seed=existing.seed();
        if (!"LINKED_BPMN".equals(seed.startKind()) || !parent.actor().equals(seed.owner()) || !registration.actor().equals(seed.executor()) || !"START".equals(seed.ownerStartCommandType()) || !parent.commandId().equals(seed.ownerStartCommandId()) || !parent.requestHash().equals(seed.ownerStartRequestSha256())
                || !parent.releaseKey().equals(seed.releaseKey()) || !parent.correlationId().equals(seed.correlationId()) || !business.equals(seed.businessKey()) || !facts.parentProcessInstanceId().equals(seed.parentProcessInstanceId()) || !facts.sourceOccurrenceId().equals(seed.sourceOccurrenceId()) || !facts.sourceElementId().equals(seed.sourceElementId())
                || !parentDefinition.definitionKey().equals(seed.parentDefinitionKey()) || !parentDefinition.engineDefinitionId().equals(seed.parentEngineDefinitionId()) || !parentDefinition.modelSha256().equals(seed.parentModelSha256()) || !caseDefinition.definitionKey().equals(seed.caseDefinitionKey()) || !caseDefinition.engineDefinitionId().equals(seed.caseEngineDefinitionId()) || !caseDefinition.modelSha256().equals(seed.caseModelSha256())
                || !commandId.equals(seed.caseStartCommandId()) || !hash.equals(seed.caseStartRequestSha256())) throw unavailable();
    }
    private Instant linkedNow() {
        if (Thread.currentThread().isInterrupted() || !TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)
                || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'",Boolean.class))) throw unavailable();
        return Objects.requireNonNull(jdbc.queryForObject("SELECT clock_timestamp()",java.sql.Timestamp.class)).toInstant();
    }
    private void linkedCurrent(Instant deadline) { if (!linkedNow().isBefore(deadline)) throw unavailable(); }
    private record ParentStart(ProcessActor actor,String commandId,String requestHash,String releaseKey,String correlationId,Instant createdAt) {}
    public record NativeSourceFacts(String parentProcessInstanceId,String parentDefinitionId,String sourceElementId,String sourceOccurrenceId,String sourceExecutionId) {
        public NativeSourceFacts { if (!safeReference(parentProcessInstanceId,255) || !safeReference(parentDefinitionId,255) || !safeReference(sourceOccurrenceId,255) || !safeReference(sourceExecutionId,255) || sourceElementId==null || !sourceElementId.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Native Case source reference is invalid"); }
        @Override public String toString() { return "NativeSourceFacts[redacted]"; }
    }
    private static boolean safeReference(String value,int maximum) { return value!=null && !value.isBlank() && value.equals(value.trim()) && value.length()<=maximum && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.chars().noneMatch(Character::isISOControl); }
    private static boolean commandReference(String value) { return value!=null && value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"); }

    public CaseInstanceDTO start(CaseContext context, StartCaseCommand command) {
        context.requireResource(PREFIX + "start"); Objects.requireNonNull(command, "command"); ownTransaction();
        String hash = ProcessRequestDigest.hash(List.of("TIANJI_CASE_START_V1", command.releaseVersion(), command.definitionKey(), command.businessKey(), command.correlationId()), Map.of());
        try {
            return Objects.requireNonNull(write.execute(status -> {
                commandLock(context, ProcessCommandReceiptStore.Kind.CASE_START, command.commandId());
                var prior = commands.lockIfPresent(context.actor(), ProcessCommandReceiptStore.Kind.CASE_START, command.commandId());
                if (prior.isPresent()) {
                    var base = prior.orElseThrow(); if (!hash.equals(base.requestHash())) throw new ProcessConflictException();
                    var origin = provenance.byExecutor(context.actor(), command.commandId()).orElseThrow(FlowableCaseService::unavailable); var seed = origin.seed();
                    if (!"APPLIED".equals(base.state()) || !seed.caseInstanceId().equals(base.resourceId()) || !seed.caseStartRequestSha256().equals(hash)
                            || !command.releaseVersion().equals(base.releaseKey()) || !command.correlationId().equals(base.correlationId())
                            || !command.definitionKey().equals(seed.caseDefinitionKey()) || !command.businessKey().equals(seed.businessKey()) || !command.correlationId().equals(seed.correlationId())) throw unavailable();
                    provenance.lockScope(context.actor().tenantId(), seed.caseInstanceId()); nativeAccess.current(context);
                    var model = model(origin); CaseSnapshot.requireRead(context, origin, model);
                    var result = new CaseSnapshot(origin, model, nativeAccess.observe(origin)).projection(context); nativeAccess.current(context); return result;
                }
                if (!newStartsEnabled) throw unavailable();
                var binding = releases.get(context.actor(), command.releaseVersion()).definitions().stream().filter(value -> value.type() == Type.CMMN && value.definitionKey().equals(command.definitionKey())).findFirst().orElseThrow(ProcessResourceNotFoundException::new);
                var model = models.caseModel(context.actor().tenantId(), binding); if (model == null) throw unavailable();
                if (!commands.insert(context.actor(), ProcessCommandReceiptStore.Kind.CASE_START, command.commandId(), hash, command.releaseVersion(), command.correlationId())) throw new ProcessConflictException();
                nativeAccess.current(context);
                var created = runtime.createCaseInstanceBuilder().caseDefinitionId(binding.engineDefinitionId()).tenantId(Long.toString(context.actor().tenantId())).businessKey(command.businessKey()).start();
                if (created == null || created.getId() == null || !binding.engineDefinitionId().equals(created.getCaseDefinitionId()) || !Long.toString(context.actor().tenantId()).equals(created.getTenantId()) || !command.businessKey().equals(created.getBusinessKey())) throw unavailable();
                nativeAccess.current(context); commands.apply(context.actor(), ProcessCommandReceiptStore.Kind.CASE_START, command.commandId(), created.getId(), null);
                var seed = new CaseStartProvenanceStore.Seed(created.getId(), "DIRECT", command.businessKey(), command.correlationId(), command.releaseVersion(), binding.definitionKey(), binding.engineDefinitionId(), binding.modelSha256(),
                        context.actor(), "CASE_START", command.commandId(), hash, context.actor(), command.commandId(), hash, null, null, null, null, null, null, null);
                var origin = provenance.insert(seed); if (!seed.equals(origin.seed())) throw unavailable();
                var result = new CaseSnapshot(origin, model, nativeAccess.observe(origin)).projection(context); nativeAccess.current(context); return result;
            }));
        } catch (FlowableOptimisticLockingException | FlowableObjectNotFoundException | ConcurrencyFailureException race) { throw new ProcessConflictException(); }
    }

    public CaseInstanceDTO get(CaseContext context, CaseInstanceQuery query) {
        context.requireResource(PREFIX + "get"); Objects.requireNonNull(query, "query"); ownTransaction();
        return Objects.requireNonNull(read.execute(status -> {
            nativeAccess.current(context); var origin = provenance.find(context.actor().tenantId(), query.caseInstanceId()).orElseThrow(ProcessResourceNotFoundException::new);
            var model = model(origin); CaseSnapshot.requireRead(context, origin, model);
            var result = new CaseSnapshot(origin, model, nativeAccess.observe(origin)).projection(context); nativeAccess.current(context); return result;
        }));
    }

    public CaseOperationResult actOnPlanItem(CaseContext context, ActOnPlanItemCommand command) {
        context.requireResource(PREFIX + "actPlanItem"); Objects.requireNonNull(command, "command"); ownTransaction();
        if (context.actor().subjectType() != ProcessActor.SubjectType.USER) throw new ProcessResourceNotFoundException();
        String hash = ProcessRequestDigest.hash(List.of("TIANJI_CASE_PLAN_ITEM_COMMAND_V1", command.action().name(), command.caseInstanceId(), command.planItemInstanceId(), command.expectedVersion()), Map.of());
        try {
            return Objects.requireNonNull(write.execute(status -> {
                commandLock(context, ProcessCommandReceiptStore.Kind.CASE_PLAN_ITEM, command.commandId());
                var prior = commands.lockIfPresent(context.actor(), ProcessCommandReceiptStore.Kind.CASE_PLAN_ITEM, command.commandId());
                if (prior.isPresent() && !hash.equals(prior.orElseThrow().requestHash())) throw new ProcessConflictException();
                provenance.lockScope(context.actor().tenantId(), command.caseInstanceId()); nativeAccess.current(context);
                var origin = provenance.find(context.actor().tenantId(), command.caseInstanceId()).orElseThrow(ProcessResourceNotFoundException::new); var model = model(origin);
                requireOperator(context, origin, model);
                if (prior.isPresent()) {
                    var base = prior.orElseThrow();
                    if (!"APPLIED".equals(base.state()) || !command.planItemInstanceId().equals(base.resourceId()) || !origin.seed().releaseKey().equals(base.releaseKey()) || !origin.seed().correlationId().equals(base.correlationId())) throw unavailable();
                    var stored = planReceipts.find(context, command.commandId(), hash).orElseThrow(FlowableCaseService::unavailable); var result = stored.result();
                    var policies = model.planItems().values().stream().filter(value -> value.definitionId().equals(result.planItemDefinitionKey())).toList();
                    if (policies.size() != 1 || !policies.getFirst().roleIds().contains(stored.authorizedRoleId()) || !stored.actor().equals(context.actor())
                            || !command.caseInstanceId().equals(result.caseInstanceId()) || !command.planItemInstanceId().equals(result.planItemInstanceId()) || command.action() != result.action()
                            || !command.expectedVersion().equals(result.previousVersion()) || !origin.seed().releaseKey().equals(result.releaseVersion()) || !origin.seed().caseDefinitionKey().equals(result.definitionKey())
                            || !origin.seed().caseModelSha256().equals(stored.modelSha256()) || !origin.seed().caseEngineDefinitionId().equals(stored.engineDefinitionId())) throw unavailable();
                    if (policies.getFirst().roleIds().stream().noneMatch(context.roleIds()::contains)) throw new ProcessResourceNotFoundException();
                    nativeAccess.current(context); return result;
                }
                var before = new CaseSnapshot(origin, model, nativeAccess.observe(origin)); var item = before.item(command.planItemInstanceId());
                if (!before.availableActions(context, item).contains(command.action())) throw new ProcessResourceNotFoundException();
                if (item.historical() || !command.expectedVersion().equals(before.version(item))) throw new ProcessConflictException();
                nativeAccess.lockPlanItem(origin, command.planItemInstanceId());
                before = new CaseSnapshot(origin, model, nativeAccess.observe(origin)); item = before.item(command.planItemInstanceId());
                if (!before.availableActions(context, item).contains(command.action())) throw new ProcessResourceNotFoundException();
                if (item.historical() || !command.expectedVersion().equals(before.version(item))) throw new ProcessConflictException();
                String role = before.authorizedRole(context, item);
                if (!commands.insert(context.actor(), ProcessCommandReceiptStore.Kind.CASE_PLAN_ITEM, command.commandId(), hash, origin.seed().releaseKey(), origin.seed().correlationId())) throw new ProcessConflictException();
                nativeAccess.current(context);
                switch (command.action()) { case DISABLE -> runtime.disablePlanItemInstance(item.id()); case ENABLE -> runtime.enablePlanItemInstance(item.id()); case START -> runtime.startPlanItemInstance(item.id()); case TERMINATE -> runtime.terminatePlanItemInstance(item.id()); }
                Instant occurred = nativeAccess.current(context); var after = new CaseSnapshot(origin, model, nativeAccess.observe(origin)); var resulting = after.item(item.id());
                CaseOperationResult result;
                try { result = new CaseOperationResult(command.commandId(), origin.seed().caseInstanceId(), item.id(), item.definitionId(), command.action(), origin.seed().releaseKey(), origin.seed().caseDefinitionKey(),
                        CasePlanItemStateEnum.valueOf(item.state().toUpperCase(java.util.Locale.ROOT)), before.version(item), CasePlanItemStateEnum.valueOf(resulting.state().toUpperCase(java.util.Locale.ROOT)), after.version(resulting), occurred); }
                catch (IllegalArgumentException inconsistent) { throw unavailable(); }
                planReceipts.insert(context, new CasePlanItemReceiptStore.Entry(context.actor(), hash, result, origin.seed().caseModelSha256(), origin.seed().caseEngineDefinitionId(), role, context.authVersion(), context.policyVersion()));
                commands.apply(context.actor(), ProcessCommandReceiptStore.Kind.CASE_PLAN_ITEM, command.commandId(), item.id(), null); nativeAccess.current(context); return result;
            }));
        } catch (FlowableOptimisticLockingException | FlowableObjectNotFoundException | ConcurrencyFailureException race) { throw new ProcessConflictException(); }
    }

    /** Participates only in the current owning native command. No USER security context or public get is restored here. */
    public ValidatedCaseModel nativeTaskPolicy(PlanItemInstanceEntity item) {
        var command = Context.getCommandContext();
        if (command == null || CommandContextUtil.getCmmnEngineConfiguration(command) != engine || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source) || Thread.currentThread().isInterrupted() || item == null || item.getTenantId() == null || !item.getTenantId().matches("[1-9][0-9]{0,18}")) throw unavailable();
        PinnedCaseActivityBehaviorFactory.requireConfigured(engine);
        long tenant; try { tenant = Long.parseLong(item.getTenantId()); } catch (NumberFormatException invalid) { throw unavailable(); }
        var origin = provenance.find(tenant, item.getCaseInstanceId()).orElseThrow(FlowableCaseService::unavailable);
        if (!origin.seed().caseEngineDefinitionId().equals(item.getCaseDefinitionId())) throw unavailable(); var model = model(origin); var policy = model.planItems().get(item.getElementId());
        if (policy == null || !policy.definitionId().equals(item.getPlanItemDefinitionId()) || !model.humanTasks().containsKey(item.getPlanItemDefinitionId())) throw unavailable();
        return model;
    }
    private ValidatedCaseModel model(CaseStartProvenanceStore.Entry origin) {
        var seed = origin.seed(); Definition binding = releases.get(seed.owner(), seed.releaseKey()).definitions().stream().filter(value -> value.type() == Type.CMMN && value.definitionKey().equals(seed.caseDefinitionKey())).findFirst().orElseThrow(FlowableCaseService::unavailable);
        if (!binding.engineDefinitionId().equals(seed.caseEngineDefinitionId()) || !binding.modelSha256().equals(seed.caseModelSha256())) throw unavailable();
        var model = models.caseModel(seed.owner().tenantId(), binding); if (model == null) throw unavailable(); return model;
    }
    private void commandLock(CaseContext context, ProcessCommandReceiptStore.Kind kind, String commandId) {
        nativeAccess.current(context);
        String hash = ProcessRequestDigest.hash(List.of("TIANJI_CASE_COMMAND_LOCK_V1", Long.toString(context.actor().tenantId()), context.actor().subjectType().name(), context.actor().issuerFingerprint(), context.actor().subjectId(), kind.name(), commandId), Map.of());
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> { }, ByteBuffer.wrap(HexFormat.of().parseHex(hash)).getLong()); nativeAccess.current(context);
    }
    private static void requireOperator(CaseContext context, CaseStartProvenanceStore.Entry origin, ValidatedCaseModel model) {
        if (context.actor().subjectType() != ProcessActor.SubjectType.USER || context.actor().tenantId() != origin.seed().owner().tenantId()
                || !context.actor().issuerFingerprint().equals(origin.seed().owner().issuerFingerprint()) || model.operatorRoleIds().stream().noneMatch(context.roleIds()::contains)) throw new ProcessResourceNotFoundException();
    }
    private static void storage(AbstractEngineConfiguration engine, SpringEngineConfiguration spring, DataSource source, DataSourceTransactionManager manager, String schema) {
        var configured = engine.getDataSource(); if (configured instanceof TransactionAwareDataSourceProxy proxy) configured = proxy.getTargetDataSource();
        if (configured != source || spring.getTransactionManager() != manager || schema == null || !schema.equals(engine.getDatabaseSchema())) throw invalidConfiguration();
    }
    private static void ownTransaction() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Case service must own its short transaction"); }
    private static IllegalArgumentException invalidConfiguration() { return new IllegalArgumentException("Case execution requires its exact shared engine storage"); }
    private static IllegalStateException unavailable() { return new IllegalStateException("Case state or released source is unavailable"); }
}
