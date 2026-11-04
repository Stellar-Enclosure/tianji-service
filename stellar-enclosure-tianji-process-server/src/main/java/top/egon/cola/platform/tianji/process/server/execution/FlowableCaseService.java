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
    }

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
