package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessResult;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlSnapshotDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlBlockReasonEnum;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStore;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessCancellationBlockedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Release;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.ControlFacts;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.LockedRoot;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.TerminalFacts;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.event.ProcessEventSubscriptionStore;
import top.egon.cola.platform.tianji.process.server.event.ProcessLifecycleEventPublisher;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Owner-only query and cancellation command. Remote Agent closure is never performed in this transaction. */
public final class ProcessControlService {
    private static final Logger log = LoggerFactory.getLogger(ProcessControlService.class);
    private static final String DELETE_REASON = "TIANJI_PROCESS_CANCELLED";
    private final SpringProcessEngineConfiguration engine;
    private final DataSource source;
    private final String schema;
    private final JdbcTemplate jdbc;
    private final VerifiedProcessModels models;
    private final ProcessReleaseService releases;
    private final ProcessCommandReceiptStore receipts;
    private final ProcessControlNativeAccess nativeAccess;
    private final ProcessCancellationReceiptStore cancellations;
    private final AgentDispatchStore dispatch;
    private final TransactionTemplate read;
    private final TransactionTemplate write;
    private final AutomationControlParticipants automation;
    private final ProcessApiSchemaVerifier automationVerifier;

    public ProcessControlService(SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn,
                                 ProcessModelValidator validator, ProcessReleaseService releases,
                                 ProcessControlNativeAccess nativeAccess, ProcessCancellationReceiptStore cancellations,
                                 AgentDispatchStore dispatch) {
        this(engine, dmn, validator, releases, nativeAccess, cancellations, dispatch, null);
    }

    public ProcessControlService(SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn,
                                 ProcessModelValidator validator, ProcessReleaseService releases,
                                 ProcessControlNativeAccess nativeAccess, ProcessCancellationReceiptStore cancellations,
                                 AgentDispatchStore dispatch, AutomationControlParticipants automation) {
        this.engine = Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(dmn, "dmn"); Objects.requireNonNull(validator, "validator");
        this.releases = Objects.requireNonNull(releases, "releases");
        this.nativeAccess = Objects.requireNonNull(nativeAccess, "nativeAccess");
        this.cancellations = Objects.requireNonNull(cancellations, "cancellations");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.automation = automation;
        if (!(engine.getTransactionManager() instanceof DataSourceTransactionManager manager)
                || manager.getDataSource() == null || configured(engine.getDataSource()) != manager.getDataSource()
                || dmn.getTransactionManager() != manager || configured(dmn.getDataSource()) != manager.getDataSource()
                || engine.getDatabaseSchema() == null || !engine.getDatabaseSchema().matches("[a-z_][a-z0-9_]{0,62}")
                || !engine.getDatabaseSchema().equals(dmn.getDatabaseSchema())
                || engine.getRuntimeService() == null || engine.getHistoryService() == null || !engine.isDbHistoryUsed()
                || engine.getHistoryLevel() == null || !engine.getHistoryLevel().isAtLeast(HistoryLevel.INSTANCE)
                || engine.isAsyncHistoryEnabled()) throw invalidStorage();
        source = manager.getDataSource(); schema = engine.getDatabaseSchema();
        automationVerifier = automation == null ? null : new ProcessApiSchemaVerifier(source, schema);
        if (!nativeAccess.usesDataSource(source) || !nativeAccess.usesEngine(engine) || !cancellations.usesDataSource(source)) throw invalidStorage();
        if (automation != null && automation.subscriptions() != null) automation.subscriptions().requireDataSource(source);
        dispatch.requireStorage(source, manager, schema);
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(10);
        models = new VerifiedProcessModels(engine, dmn, validator);
        receipts = new ProcessCommandReceiptStore(jdbc, schema);
        read = new TransactionTemplate(manager); read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); read.setTimeout(10);
        write = new TransactionTemplate(manager); write.setTimeout(10);
    }

    public ProcessControlSnapshotDTO control(ProcessActor actor, GetProcessInstanceQuery query) {
        actor(actor); Objects.requireNonNull(query, "query"); noAmbientTransaction();
        try {
            boolean automationReady = verifyAutomationStorage();
            return Objects.requireNonNull(read.execute(status -> {
                var binding = automationBinding(actor, query.instanceId(), automationReady);
                if (binding.isPresent()) return controlAutomation(actor, binding.orElseThrow());
                TaskOrigin start = readStart(actor, query.instanceId());
                Release release = release(start);
                Definition definition = discoverDefinition(start, release);
                ValidatedProcessModel model = runningModel(actor.tenantId(), definition);
                return snapshot(start, definition, model, nativeAccess.observe(start, definition, model), nativeAccess.now());
            }));
        } catch (ProcessResourceNotFoundException missing) { throw missing; }
        catch (RuntimeException failure) { throw unavailable(); }
    }

    public CancelProcessResult cancel(ProcessActor actor, CancelProcessCommand command) {
        actor(actor); Objects.requireNonNull(command, "command"); noAmbientTransaction();
        try {
            boolean automationReady = verifyAutomationStorage();
            CancelProcessResult result = Objects.requireNonNull(write.execute(status -> cancelInTransaction(actor, command, automationReady)));
            log.info("Process cancellation: tenant={} instanceHash={} commandHash={} accepted={} status={}", actor.tenantId(),
                    safeHash(command.instanceId()), safeHash(command.commandId()), result.accepted(), result.status());
            return result;
        } catch (RuntimeException failure) {
            RuntimeException outcome = classify(failure);
            log.warn("Process cancellation failed: tenant={} instanceHash={} commandHash={} type={}", actor.tenantId(),
                    safeHash(command.instanceId()), safeHash(command.commandId()), outcome.getClass().getSimpleName());
            throw outcome;
        }
    }

    private CancelProcessResult cancelInTransaction(ProcessActor actor, CancelProcessCommand command, boolean automationReady) {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        var binding = automationBinding(actor, command.instanceId(), automationReady);
        if (binding.isPresent()) return cancelAutomation(actor, command, binding.orElseThrow());
        TaskOrigin start = receipts.lockStartByInstance(actor, command.instanceId());
        validateStart(start, actor, command.instanceId());
        String hash = ProcessRequestDigest.hash(List.of("TIANJI_PROCESS_CANCEL_V1", command.instanceId(), command.commandId(), command.expectedVersion(), command.reason()), Map.of());
        var previous = receipts.lockIfPresent(actor, ProcessCommandReceiptStore.Kind.PROCESS_CANCEL, command.commandId());
        if (previous.isPresent()) {
            var generic = previous.orElseThrow();
            if (!hash.equals(generic.requestHash())) throw new ProcessConflictException();
            if (!"APPLIED".equals(generic.state()) || !command.instanceId().equals(generic.resourceId())
                    || !start.releaseKey().equals(generic.releaseKey()) || !start.correlationId().equals(generic.correlationId())) throw unavailable();
            var detail = cancellations.find(actor, command.commandId(), hash).orElseThrow(ProcessControlService::unavailable);
            if (!detail.actor().equals(actor) || !detail.command().equals(command) || !detail.start().equals(start)) throw unavailable();
            return detail.result();
        }
        var root = nativeAccess.lockRoot(start);
        Release release = release(start);
        Definition definition;
        TerminalFacts terminal;
        boolean accepted = false, scheduled = false;
        if (root.isEmpty()) {
            var history = historic(start);
            definition = definition(release, history.getProcessDefinitionId());
            terminal = nativeAccess.terminalHistory(start, definition, history.getBusinessKey(), false);
        } else {
            var locked = root.orElseThrow();
            definition = definition(release, locked.engineDefinitionId());
            ValidatedProcessModel model = runningModel(actor.tenantId(), definition);
            ControlFacts facts = nativeAccess.observe(start, definition, model);
            if (!locked.equals(facts.liveRoot())) throw unavailable();
            var current = snapshot(start, definition, model, facts, nativeAccess.now());
            if (!command.expectedVersion().equals(current.version())) throw new ProcessVersionConflictException();
            verifyCaseProvenance(start, definition, model, release, locked.businessKey());
            if (!model.caseSources().isEmpty()) throw new ProcessCancellationBlockedException();
            reserve(actor, start, command, hash);
            try { engine.getRuntimeService().deleteProcessInstance(command.instanceId(), DELETE_REASON); }
            catch (FlowableObjectNotFoundException missing) { throw new DeleteStageMissing(missing); }
            terminal = nativeAccess.terminalHistory(start, definition, locked.businessKey(), true);
            int count = dispatch.scheduleProcessClosure(new AgentDispatchStore.ProcessClosureScope(actor, start.instanceId(),
                    start.releaseKey(), definition.engineDefinitionId(), definition.modelSha256(), locked.businessKey(), start.correlationId()));
            scheduled = count > 0; accepted = true;
        }
        if (!accepted) reserve(actor, start, command, hash);
        validateTerminal(start, definition, terminal);
        var result = new CancelProcessResult(start.instanceId(), command.commandId(), accepted, terminal.status(),
                terminalVersion(start, definition, terminal), terminal.finishedAt(), nativeAccess.now(), scheduled);
        cancellations.insert(new ProcessCancellationReceiptStore.Entry(actor, hash, command, start, definition.definitionKey(),
                definition.engineDefinitionId(), definition.modelSha256(), terminal.businessKey(), result));
        receipts.apply(actor, ProcessCommandReceiptStore.Kind.PROCESS_CANCEL, command.commandId(), start.instanceId(), null);
        return result;
    }

    private boolean verifyAutomationStorage() {
        boolean present = automation != null && nativeAccess.automationStoragePresent();
        if (present) automationVerifier.verifyAutomationV14();
        return present;
    }

    private Optional<AutomationInstanceBindingBO> automationBinding(ProcessActor actor, String instanceId, boolean automationReady) {
        if (!automationReady) return Optional.empty();
        var owner = automation.bindings().findOwner(actor.tenantId(), instanceId);
        if (owner.isEmpty()) return Optional.empty();
        if (!actor.equals(owner.orElseThrow())) throw new ProcessResourceNotFoundException();
        var binding = automation.bindings().find(actor.tenantId(), instanceId).orElseThrow(ProcessControlService::unavailable);
        if (!actor.equals(binding.getOwner())) throw unavailable();
        return Optional.of(binding);
    }

    private ProcessControlSnapshotDTO controlAutomation(ProcessActor actor, AutomationInstanceBindingBO binding) {
        var start = automationStart(actor, binding, false);
        var definition = automationDefinition(binding);
        var root = nativeAccess.readAutomationRoot(binding, definition);
        if (root.isPresent()) return automationSnapshot(start, definition, root.orElseThrow(), binding);
        var terminal = nativeAccess.automationTerminalHistory(binding, definition, null, false);
        validateTerminal(start, definition, terminal);
        return new ProcessControlSnapshotDTO(process(start, definition, terminal.businessKey(), terminal.status(), terminal.finishedAt()),
                List.of(), Set.of(), ProcessControlBlockReasonEnum.PROCESS_TERMINAL,
                terminalVersion(start, definition, terminal), nativeAccess.now());
    }

    private CancelProcessResult cancelAutomation(ProcessActor actor, CancelProcessCommand command, AutomationInstanceBindingBO binding) {
        var lockedBinding = automation.guard().lockKnownRoot(binding.getTenantId(), binding.getInstanceId());
        if (!binding.equals(lockedBinding)) throw unavailable();
        var definition = automationDefinition(lockedBinding);
        var root = nativeAccess.lockAutomationRoot(lockedBinding, definition);
        var start = automationStart(actor, lockedBinding, true);
        String hash = ProcessRequestDigest.hash(List.of("TIANJI_PROCESS_CANCEL_V1", command.instanceId(),
                command.commandId(), command.expectedVersion(), command.reason()), Map.of());
        var previous = receipts.lockIfPresent(actor, ProcessCommandReceiptStore.Kind.PROCESS_CANCEL, command.commandId());
        if (previous.isPresent()) {
            var generic = previous.orElseThrow();
            if (!hash.equals(generic.requestHash())) throw new ProcessConflictException();
            if (!"APPLIED".equals(generic.state()) || !command.instanceId().equals(generic.resourceId())
                    || !start.releaseKey().equals(generic.releaseKey()) || !start.correlationId().equals(generic.correlationId())) throw unavailable();
            var detail = cancellations.find(actor, command.commandId(), hash).orElseThrow(ProcessControlService::unavailable);
            if (!detail.actor().equals(actor) || !detail.command().equals(command) || !detail.start().equals(start)
                    || !detail.engineDefinitionId().equals(definition.engineDefinitionId())
                    || !detail.definitionSha256().equals(definition.modelSha256())) throw unavailable();
            return detail.result();
        }
        TerminalFacts terminal;
        boolean accepted = root.isPresent();
        boolean scheduled = false;
        if (accepted) {
            var current = root.orElseThrow();
            var snapshot = automationSnapshot(start, definition, current, lockedBinding);
            if (!command.expectedVersion().equals(snapshot.version())) throw new ProcessVersionConflictException();
            reserve(actor, start, command, hash);
            try { engine.getRuntimeService().deleteProcessInstance(command.instanceId(), DELETE_REASON); }
            catch (FlowableObjectNotFoundException missing) { throw new DeleteStageMissing(missing); }
            terminal = nativeAccess.automationTerminalHistory(lockedBinding, definition, current.businessKey(), true);
            int count = dispatch.scheduleProcessClosure(new AgentDispatchStore.ProcessClosureScope(actor, start.instanceId(),
                    start.releaseKey(), definition.engineDefinitionId(), definition.modelSha256(), current.businessKey(), start.correlationId()));
            scheduled = count > 0;
            automation.nodes().cancelOpen(actor.tenantId(), command.instanceId());
            if (automation.subscriptions() != null) {
                automation.subscriptions().closeOpen(actor.tenantId(), command.instanceId());
            }
            if (automation.lifecycle() != null) {
                automation.lifecycle().publish(lockedBinding, "INSTANCE_CANCELLED", terminal.finishedAt(), null, null, null, null);
            }
        } else {
            terminal = nativeAccess.automationTerminalHistory(lockedBinding, definition, null, false);
            reserve(actor, start, command, hash);
        }
        validateTerminal(start, definition, terminal);
        var result = new CancelProcessResult(start.instanceId(), command.commandId(), accepted, terminal.status(),
                terminalVersion(start, definition, terminal), terminal.finishedAt(), nativeAccess.now(), scheduled);
        cancellations.insert(new ProcessCancellationReceiptStore.Entry(actor, hash, command, start, definition.definitionKey(),
                definition.engineDefinitionId(), definition.modelSha256(), terminal.businessKey(), result));
        receipts.apply(actor, ProcessCommandReceiptStore.Kind.PROCESS_CANCEL, command.commandId(), start.instanceId(), null);
        return result;
    }

    private TaskOrigin automationStart(ProcessActor actor, AutomationInstanceBindingBO binding, boolean lock) {
        var start = lock ? receipts.lockStartByInstance(actor, binding.getInstanceId()) : readStart(actor, binding.getInstanceId());
        validateStart(start, actor, binding.getInstanceId());
        if (!binding.getSourceCommandId().equals(start.commandId()) || !binding.getReleaseKey().equals(start.releaseKey())
                || !binding.getResolvedStartSha256().equals(start.requestHash()) || !binding.getSubmittedAt().equals(start.createdAt())) {
            throw unavailable();
        }
        return start;
    }

    private Definition automationDefinition(AutomationInstanceBindingBO binding) {
        var descriptor = automation.releases().find(binding.getTenantId(), binding.getReleaseKey()).orElseThrow(ProcessControlService::unavailable);
        if (!binding.getDefinitionKey().equals(descriptor.rootDefinitionKey())) throw unavailable();
        releases.requireVerifiedAutomation(descriptor);
        return nativeAccess.automationDefinition(binding);
    }

    private ProcessControlSnapshotDTO automationSnapshot(TaskOrigin start, Definition definition, LockedRoot root,
                                                        AutomationInstanceBindingBO binding) {
        var open = automation.nodes().open(binding.getTenantId(), binding.getInstanceId(), 257);
        if (open.size() > 256) throw unavailable();
        var groups = new TreeMap<AutomationWait, Integer>(Comparator.comparing((AutomationWait wait) -> wait.kind().name())
                .thenComparing(AutomationWait::nodeId));
        for (var row : open) {
            var kind = switch (row.getKind()) {
                case HUMAN -> ProcessActiveWaitKindEnum.HUMAN_TASK;
                case SERVICE -> ProcessActiveWaitKindEnum.EXTERNAL_WORKER;
                case AGENT -> ProcessActiveWaitKindEnum.AGENT_RESULT_WAIT;
                case TIMER -> ProcessActiveWaitKindEnum.TIMER;
                default -> null;
            };
            if (kind != null) {
                String activityKey = row.getNodeId();
                if (!activityKey.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) {
                    activityKey = "node-" + ProcessRequestDigest.hash(List.of(activityKey), Map.of());
                }
                groups.merge(new AutomationWait(kind, activityKey), 1, Integer::sum);
            }
        }
        var waits = groups.entrySet().stream()
                .map(group -> new ProcessActiveWaitDTO(group.getKey().nodeId(), group.getKey().kind(), group.getValue()))
                .toList();
        return activeSnapshot(start, definition, new ControlFacts(root, null, waits), ProcessControlBlockReasonEnum.NONE,
                nativeAccess.now());
    }

    private void reserve(ProcessActor actor, TaskOrigin start, CancelProcessCommand command, String hash) {
        if (receipts.insert(actor, ProcessCommandReceiptStore.Kind.PROCESS_CANCEL, command.commandId(), hash, start.releaseKey(), start.correlationId())) return;
        var current = receipts.lock(actor, ProcessCommandReceiptStore.Kind.PROCESS_CANCEL, command.commandId());
        if (!hash.equals(current.requestHash())) throw new ProcessConflictException();
        throw unavailable();
    }

    private TaskOrigin readStart(ProcessActor actor, String instanceId) {
        var rows = jdbc.query("SELECT subject_type,subject_issuer,subject_id,command_id,request_sha256,release_key,correlation_id,resource_id,created_at FROM \""
                        + schema + "\".tianji_process_command_receipt WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? "
                        + "AND command_type='START' AND state='APPLIED' AND resource_id=?",
                (rs, number) -> new TaskOrigin(new ProcessActor(actor.tenantId(), ProcessActor.SubjectType.valueOf(rs.getString("subject_type")),
                        rs.getString("subject_id"), rs.getString("subject_issuer")), rs.getString("command_id"), rs.getString("request_sha256"),
                        rs.getString("release_key"), rs.getString("correlation_id"), rs.getString("resource_id"), rs.getTimestamp("created_at").toInstant()),
                actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), instanceId);
        if (rows.isEmpty()) throw new ProcessResourceNotFoundException();
        if (rows.size() != 1) throw unavailable();
        var result = rows.getFirst(); validateStart(result, actor, instanceId); return result;
    }

    private Definition discoverDefinition(TaskOrigin start, Release release) {
        String tenant = Long.toString(start.requester().tenantId());
        var current = engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(start.instanceId()).processInstanceTenantId(tenant).singleResult();
        if (current != null) {
            if (!start.instanceId().equals(current.getId()) || !tenant.equals(current.getTenantId())) throw unavailable();
            return definition(release, current.getProcessDefinitionId());
        }
        return definition(release, historic(start).getProcessDefinitionId());
    }

    private org.flowable.engine.history.HistoricProcessInstance historic(TaskOrigin start) {
        String tenant = Long.toString(start.requester().tenantId());
        var history = engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(start.instanceId()).processInstanceTenantId(tenant).singleResult();
        if (history == null || !start.instanceId().equals(history.getId()) || !tenant.equals(history.getTenantId()) || history.getEndTime() == null) throw unavailable();
        return history;
    }

    private Release release(TaskOrigin start) {
        try {
            if (automation != null && (receipts.hasAutomationRelease(start.requester().tenantId(), start.releaseKey())
                    || receipts.hasAutomationRootInput(start.requester().tenantId(), start.releaseKey())
                        && releases.readMigrationRelease(start.requester(), start.releaseKey()).isEmpty())) throw unavailable();
            var result = releases.get(start.requester(), start.releaseKey());
            if (result == null || !start.releaseKey().equals(result.releaseKey())) throw unavailable();
            return result;
        } catch (ProcessResourceNotFoundException missing) { throw unavailable(); }
    }

    private ValidatedProcessModel runningModel(long tenantId, Definition definition) {
        try { return models.runningProcess(tenantId, definition); }
        catch (ProcessResourceNotFoundException missing) { throw unavailable(); }
    }

    private ProcessControlSnapshotDTO snapshot(TaskOrigin start, Definition definition, ValidatedProcessModel model,
                                               ControlFacts facts, Instant observedAt) {
        if (facts == null || model.type() != Type.BPMN || !definition.definitionKey().equals(model.definitionKey())
                || (facts.liveRoot() == null) == (facts.terminal() == null)
                || facts.terminal() != null && !facts.activeWaits().isEmpty()) throw unavailable();
        if (facts.terminal() != null) {
            var terminal = facts.terminal(); validateTerminal(start, definition, terminal);
            return new ProcessControlSnapshotDTO(process(start, definition, terminal.businessKey(), terminal.status(), terminal.finishedAt()),
                    List.of(), Set.of(), ProcessControlBlockReasonEnum.PROCESS_TERMINAL, terminalVersion(start, definition, terminal), observedAt);
        }
        var block = model.caseSources().isEmpty() ? ProcessControlBlockReasonEnum.NONE : ProcessControlBlockReasonEnum.MODEL_CONTAINS_CASE_SOURCE;
        return activeSnapshot(start, definition, facts, block, observedAt);
    }

    private ProcessControlSnapshotDTO activeSnapshot(TaskOrigin start, Definition definition, ControlFacts facts,
                                                     ProcessControlBlockReasonEnum block, Instant observedAt) {
        var root = facts.liveRoot();
        if (root == null || root.tenantId() != start.requester().tenantId() || !start.instanceId().equals(root.instanceId())
                || !definition.engineDefinitionId().equals(root.engineDefinitionId()) || root.revision() < 0
                || root.suspensionState() != 1 && root.suspensionState() != 2) throw unavailable();
        var status = root.suspensionState() == 1 ? ProcessInstanceStatusEnum.ACTIVE : ProcessInstanceStatusEnum.SUSPENDED;
        var fields = identity("TIANJI_PROCESS_CONTROL_V1", start, definition, root.businessKey());
        fields.add(Integer.toString(root.revision())); fields.add(Integer.toString(root.suspensionState())); fields.add(status.name()); fields.add(block.name());
        for (var wait : facts.activeWaits()) { fields.add(wait.kind().name()); fields.add(wait.activityKey()); fields.add(Integer.toString(wait.occurrences())); }
        return new ProcessControlSnapshotDTO(process(start, definition, root.businessKey(), status, null), facts.activeWaits(),
                block == ProcessControlBlockReasonEnum.NONE ? Set.of(ProcessControlActionEnum.CANCEL) : Set.of(), block,
                ProcessRequestDigest.hash(fields, Map.of()), observedAt);
    }

    private static ProcessInstanceDTO process(TaskOrigin start, Definition definition, String businessKey, ProcessInstanceStatusEnum status, Instant finishedAt) {
        return new ProcessInstanceDTO(start.instanceId(), definition.definitionKey(), start.releaseKey(), businessKey, start.correlationId(), status, start.createdAt(), finishedAt);
    }

    private static String terminalVersion(TaskOrigin start, Definition definition, TerminalFacts terminal) {
        var fields = identity("TIANJI_PROCESS_TERMINAL_V1", start, definition, terminal.businessKey());
        fields.add(terminal.status().name()); fields.add(terminal.finishedAt().toString()); fields.add(terminal.deleteCategory().name());
        return ProcessRequestDigest.hash(fields, Map.of());
    }

    private static ArrayList<String> identity(String domain, TaskOrigin start, Definition definition, String businessKey) {
        var actor = start.requester();
        return new ArrayList<>(List.of(domain, Long.toString(actor.tenantId()), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                start.commandId(), start.requestHash(), start.instanceId(), start.releaseKey(), definition.definitionKey(), definition.engineDefinitionId(),
                definition.modelSha256(), businessKey, start.correlationId()));
    }

    private void verifyCaseProvenance(TaskOrigin start, Definition definition, ValidatedProcessModel model, Release release, String businessKey) {
        var rows = jdbc.query("SELECT p.*,c.state AS executor_state,c.request_sha256 AS executor_request_sha256,"
                        + "c.resource_id AS executor_resource_id,c.release_key AS executor_release_key,c.correlation_id AS executor_correlation_id "
                        + "FROM \"" + schema + "\".tianji_case_start_provenance p LEFT JOIN \"" + schema + "\".tianji_process_command_receipt c "
                        + "ON c.tenant_id=p.tenant_id AND c.subject_type=p.executor_type AND c.subject_issuer=p.executor_issuer "
                        + "AND c.subject_id=p.executor_subject AND c.command_type=p.case_start_command_type AND c.command_id=p.case_start_command_id "
                        + "WHERE p.tenant_id=? AND p.parent_process_instance_id=? ORDER BY p.case_instance_id LIMIT 257",
                (rs, number) -> caseSource(rs), start.requester().tenantId(), start.instanceId());
        if (rows.size() > 256) throw unavailable();
        for (var row : rows) {
            var sourceModel = model.caseSources().get(row.sourceElementId());
            if (!"LINKED_BPMN".equals(row.startKind()) || !start.requester().equals(row.owner()) || !start.instanceId().equals(row.parentProcessInstanceId())
                    || !"START".equals(row.ownerStartCommandType()) || !start.commandId().equals(row.ownerStartCommandId())
                    || !start.requestHash().equals(row.ownerStartRequestSha256()) || !start.releaseKey().equals(row.releaseKey())
                    || !start.correlationId().equals(row.correlationId()) || !businessKey.equals(row.businessKey())
                    || !definition.definitionKey().equals(row.parentDefinitionKey()) || !definition.engineDefinitionId().equals(row.parentEngineDefinitionId())
                    || !definition.modelSha256().equals(row.parentModelSha256()) || sourceModel == null
                    || !sourceModel.caseDefinitionKey().equals(row.caseDefinitionKey())) throw unavailable();
            long matches = release.definitions().stream().filter(item -> item.type() == Type.CMMN && item.definitionKey().equals(row.caseDefinitionKey())
                    && item.engineDefinitionId().equals(row.caseEngineDefinitionId()) && item.modelSha256().equals(row.caseModelSha256())).count();
            if (matches != 1) throw unavailable();
        }
    }

    /** Reuses the immutable provenance value only; cancellation never activates the Case storage capability. */
    private static CaseStartProvenanceStore.Seed caseSource(java.sql.ResultSet rs) throws SQLException {
        try {
            var value = new CaseStartProvenanceStore.Seed(rs.getString("case_instance_id"), rs.getString("start_kind"), rs.getString("business_key"),
                    rs.getString("correlation_id"), rs.getString("release_key"), rs.getString("case_definition_key"), rs.getString("case_engine_definition_id"),
                    rs.getString("case_model_sha256"), new ProcessActor(rs.getLong("tenant_id"), ProcessActor.SubjectType.valueOf(rs.getString("owner_type")),
                    rs.getString("owner_subject"), rs.getString("owner_issuer")), rs.getString("owner_start_command_type"), rs.getString("owner_start_command_id"),
                    rs.getString("owner_start_request_sha256"), new ProcessActor(rs.getLong("tenant_id"), ProcessActor.SubjectType.valueOf(rs.getString("executor_type")),
                    rs.getString("executor_subject"), rs.getString("executor_issuer")), rs.getString("case_start_command_id"), rs.getString("case_start_request_sha256"),
                    rs.getString("source_id"), rs.getString("parent_process_instance_id"), rs.getString("source_occurrence_id"), rs.getString("source_element_id"),
                    rs.getString("parent_definition_key"), rs.getString("parent_engine_definition_id"), rs.getString("parent_model_sha256"));
            if (!"LINKED_BPMN".equals(value.startKind()) || !"BPMN".equals(rs.getString("parent_model_type"))
                    || !"CMMN".equals(rs.getString("case_model_type")) || !"CASE_START".equals(rs.getString("case_start_command_type"))
                    || !"APPLIED".equals(rs.getString("executor_state"))
                    || !value.caseStartRequestSha256().equals(rs.getString("executor_request_sha256"))
                    || !value.caseInstanceId().equals(rs.getString("executor_resource_id"))
                    || !value.releaseKey().equals(rs.getString("executor_release_key"))
                    || !value.correlationId().equals(rs.getString("executor_correlation_id"))) throw unavailable();
            return value;
        } catch (RuntimeException malformed) { throw unavailable(); }
    }

    private static Definition definition(Release release, String engineId) {
        var matches = release.definitions().stream().filter(item -> item.type() == Type.BPMN && item.engineDefinitionId().equals(engineId)).toList();
        if (matches.size() != 1) throw unavailable(); return matches.getFirst();
    }
    private static void validateStart(TaskOrigin start, ProcessActor actor, String instanceId) {
        if (start == null || !actor.equals(start.requester()) || !instanceId.equals(start.instanceId()) || start.createdAt() == null
                || start.commandId() == null || !start.commandId().matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                || start.requestHash() == null || !start.requestHash().matches("[0-9a-f]{64}")
                || start.releaseKey() == null || !start.releaseKey().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                || start.correlationId() == null || !start.correlationId().matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw unavailable();
    }
    private static void validateTerminal(TaskOrigin start, Definition definition, TerminalFacts terminal) {
        if (terminal == null || terminal.tenantId() != start.requester().tenantId() || !start.instanceId().equals(terminal.instanceId())
                || !definition.engineDefinitionId().equals(terminal.engineDefinitionId()) || terminal.finishedAt() == null
                || terminal.status() != ProcessInstanceStatusEnum.COMPLETED && terminal.status() != ProcessInstanceStatusEnum.CANCELLED) throw unavailable();
    }
    private static RuntimeException classify(RuntimeException failure) {
        if (failure instanceof ProcessConflictException || failure instanceof ProcessVersionConflictException
                || failure instanceof ProcessCancellationBlockedException || failure instanceof ProcessResourceNotFoundException) return failure;
        Throwable cursor = failure;
        for (int index = 0; cursor != null && index < 16; index++, cursor = cursor.getCause()) {
            if (cursor instanceof DeleteStageMissing || cursor instanceof FlowableOptimisticLockingException) return new ProcessVersionConflictException();
            if (cursor instanceof SQLException sql) {
                return sql.getSQLState() != null && Set.of("40001", "40P01", "55P03").contains(sql.getSQLState()) ? new ProcessVersionConflictException() : unavailable();
            }
        }
        return unavailable();
    }
    private static DataSource configured(DataSource value) { return value instanceof TransactionAwareDataSourceProxy proxy ? proxy.getTargetDataSource() : value; }
    private static void actor(ProcessActor actor) { if (actor == null || "LEGACY".equals(actor.issuerFingerprint())) throw new IllegalArgumentException("Process owner identity is invalid"); }
    private static void noAmbientTransaction() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable(); }
    private static String safeHash(String value) { return ProcessRequestDigest.hash(List.of(value), Map.of()); }
    private static IllegalArgumentException invalidStorage() { return new IllegalArgumentException("Process control requires its exact owning synchronous JDBC engine"); }
    private static IllegalStateException unavailable() { return new IllegalStateException("Process control source or dependency is unavailable"); }

    public record AutomationControlParticipants(ProcessInstanceBindingStore bindings, AutomationReleaseStore releases,
            AutomationInstanceGuardCommandInterceptor guard, ProcessNodeExecutionStore nodes,
            ProcessEventSubscriptionStore subscriptions, ProcessLifecycleEventPublisher lifecycle) {
        public AutomationControlParticipants(ProcessInstanceBindingStore bindings, AutomationReleaseStore releases,
                AutomationInstanceGuardCommandInterceptor guard, ProcessNodeExecutionStore nodes) {
            this(bindings, releases, guard, nodes, null, null);
        }

        public AutomationControlParticipants {
            Objects.requireNonNull(bindings, "bindings");
            Objects.requireNonNull(releases, "releases");
            Objects.requireNonNull(guard, "guard");
            Objects.requireNonNull(nodes, "nodes");
        }
    }
    private record AutomationWait(ProcessActiveWaitKindEnum kind, String nodeId) { }
    private static final class DeleteStageMissing extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private DeleteStageMissing(FlowableObjectNotFoundException cause) { super("Process changed during native cancellation", cause); }
    }
}
