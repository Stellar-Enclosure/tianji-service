package top.egon.cola.platform.tianji.process.server.task;

import jakarta.validation.Valid;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.TaskService;
import org.flowable.cmmn.api.CmmnTaskService;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.ClaimHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetHumanTaskQuery;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ListHumanTasksQuery;
import top.egon.cola.platform.tianji.contract.orchestration.TransferHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.UnclaimHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.CompleteEngineHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EngineHumanTaskCompletionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EngineHumanTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceBindingBO;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess;
import top.egon.cola.platform.tianji.process.server.execution.ProcessInstanceBindingStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionBO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskActionResult.Entry;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTask;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTaskKind;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Internal current-user application service. Its caller supplies the trusted current operation context, never request role claims. */
public final class FlowableHumanTaskService {
    private static final String PREFIX = "tianji.api.process.task.";
    private static final String OBSERVE_ACTION = PREFIX + "get.v2";
    private static final String COMPLETE_ACTION = PREFIX + "complete.v2";
    private final TaskService tasks;
    private final CmmnTaskService caseTasks;
    private final CaseStartProvenanceStore caseProvenance;
    private final ProcessModelValidator validator;
    private final ProcessReleaseService releases;
    private final VerifiedProcessModels models;
    private final ProcessCommandReceiptStore origins;
    private final HumanTaskNativeAccess nativeAccess;
    private final HumanTaskReceiptStore receipts;
    private final HumanTaskCompletionObservation completionObservation;
    private final TransactionTemplate read;
    private final TransactionTemplate write;
    private final AutomationTaskParticipants automation;
    private final ProcessControlNativeAccess controlNative;
    private final ProcessApiSchemaVerifier automationVerifier;
    private final JdbcTemplate jdbc;
    private final String humanReceipts;

    public FlowableHumanTaskService(SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn,
            ProcessModelValidator validator, HumanTaskReceiptStore receipts) { this(engine, null, dmn, validator, receipts, null); }

    public FlowableHumanTaskService(SpringProcessEngineConfiguration engine, SpringCmmnEngineConfiguration cmmn,
            SpringDmnEngineConfiguration dmn, ProcessModelValidator validator, HumanTaskReceiptStore receipts, CaseStartProvenanceStore provenance) {
        this(engine, cmmn, dmn, validator, receipts, provenance, HumanTaskCompletionObservation.disabled());
    }

    public FlowableHumanTaskService(SpringProcessEngineConfiguration engine, SpringCmmnEngineConfiguration cmmn,
            SpringDmnEngineConfiguration dmn, ProcessModelValidator validator, HumanTaskReceiptStore receipts,
            CaseStartProvenanceStore provenance, HumanTaskCompletionObservation completionObservation) {
        this(engine, cmmn, dmn, validator, receipts, provenance, completionObservation, null);
    }

    public FlowableHumanTaskService(SpringProcessEngineConfiguration engine, SpringCmmnEngineConfiguration cmmn,
            SpringDmnEngineConfiguration dmn, ProcessModelValidator validator, HumanTaskReceiptStore receipts,
            CaseStartProvenanceStore provenance, HumanTaskCompletionObservation completionObservation,
            AutomationTaskParticipants automation) {
        Objects.requireNonNull(engine, "engine"); this.validator = Objects.requireNonNull(validator, "validator"); this.receipts = Objects.requireNonNull(receipts, "receipts");
        this.completionObservation = Objects.requireNonNull(completionObservation, "completionObservation");
        if (!(engine.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null || !receipts.usesDataSource(manager.getDataSource())) throw new IllegalArgumentException("Human task execution requires its exact owning receipt store and JDBC manager");
        if ((cmmn == null) != (provenance == null) || provenance != null && (!provenance.usesDataSource(manager.getDataSource()) || !receipts.usesCaseProvenance(provenance))) throw new IllegalArgumentException("Case task execution requires its exact verified provenance participant");
        tasks = Objects.requireNonNull(engine.getTaskService(), "taskService"); caseProvenance = provenance;
        caseTasks = cmmn == null ? null : Objects.requireNonNull(cmmn.getCmmnTaskService(), "cmmnTaskService");
        if (cmmn != null && (cmmn.getTransactionManager() != manager || !Objects.equals(engine.getDatabaseSchema(), cmmn.getDatabaseSchema()))) throw new IllegalArgumentException("Case task execution requires the owning native transaction");
        releases = cmmn == null ? new ProcessReleaseService(engine, dmn, validator) : new ProcessReleaseService(engine, cmmn, dmn, validator);
        models = cmmn == null ? new VerifiedProcessModels(engine, dmn, validator) : new VerifiedProcessModels(engine, cmmn, dmn, validator);
        this.automation = automation;
        jdbc = new JdbcTemplate(manager.getDataSource());
        jdbc.setQueryTimeout(5);
        origins = new ProcessCommandReceiptStore(jdbc, engine.getDatabaseSchema());
        nativeAccess = cmmn == null ? new HumanTaskNativeAccess(manager.getDataSource(), engine) : new HumanTaskNativeAccess(manager.getDataSource(), engine, cmmn);
        controlNative = automation == null ? null : new ProcessControlNativeAccess(manager.getDataSource(), engine);
        automationVerifier = automation == null ? null : new ProcessApiSchemaVerifier(manager.getDataSource(), engine.getDatabaseSchema());
        humanReceipts = '"' + engine.getDatabaseSchema() + "\".tianji_human_task_receipt";
        read = new TransactionTemplate(manager); read.setReadOnly(true); read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); read.setTimeout(10);
        write = new TransactionTemplate(manager); write.setTimeout(10);
    }

    public EngineHumanTaskDTO observeAction(HumanTaskContext context, String taskId) {
        var current = actionContext(context, OBSERVE_ACTION, PREFIX + "get");
        requireAutomationStorage();
        return Objects.requireNonNull(read.execute(status -> {
            receipts.current(current);
            var task = nativeAccess.find(current, taskId, false).orElseThrow(ProcessResourceNotFoundException::new);
            var binding = taskBinding(current, task, true).orElseThrow(ProcessResourceNotFoundException::new);
            var action = actionSnapshot(current, task, binding);
            if (!HumanTaskAccessRules.readable(current, action.facts(), action.policy())) throw new ProcessResourceNotFoundException();
            Instant now = receipts.current(current);
            boolean mine = current.userKey().equals(action.facts().assignee());
            boolean assignableRole = action.policy().lanes().get(action.facts().lane()).stream().anyMatch(current.roleIds()::contains);
            List<String> available = action.facts().suspended() || action.facts().delegated() || !now.isBefore(action.dueAt()) ? List.of()
                    : mine ? (assignableRole ? List.of("UNCLAIM", "COMPLETE") : List.of("UNCLAIM"))
                    : List.of("CLAIM");
            var result = new EngineHumanTaskDTO().setTaskId(taskId).setInstanceId(binding.getInstanceId())
                    .setNodeId(action.node().getNodeId()).setState(mine ? EngineHumanTaskDTO.StateEnum.CLAIMED : EngineHumanTaskDTO.StateEnum.READY)
                    .setAssignedToMe(mine).setVersion(action.version()).setResultSchema(action.schema())
                    .setAvailableActions(available).setDueAt(action.dueAt());
            if (!result.isTaskProjectionConsistent()) throw unavailable();
            return result;
        }));
    }

    public EngineHumanTaskCompletionDTO completeAction(HumanTaskContext context, String taskId,
                                                        @Valid CompleteEngineHumanTaskCommand command) {
        var current = actionContext(context, COMPLETE_ACTION, PREFIX + "complete");
        if (taskId == null || taskId.isBlank() || !taskId.equals(taskId.trim()) || taskId.length() > 255
                || command == null || command.getCommandId() == null || !command.getCommandId().matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}")
                || command.getExpectedVersion() == null || command.getExpectedVersion().isBlank()
                || command.getExpectedVersion().length() > 128 || !command.isScalarResultValid()) {
            throw new IllegalArgumentException("Invalid automation task completion");
        }
        requireAutomationStorage();
        String hash = ProcessTypedJson.sha256("AUTOMATION_HUMAN_TASK_COMPLETE_V2",
                ProcessTypedJson.canonicalBytes(List.of(taskId, command.getExpectedVersion(), command.getResult())));
        try {
            var receipt = Objects.requireNonNull(write.execute(status -> {
                var binding = discoverAction(current, taskId, HumanTaskActionEnum.COMPLETE, command.getCommandId(), true)
                        .orElseThrow(ProcessResourceNotFoundException::new);
                return mutateAction(current, HumanTaskActionEnum.COMPLETE, command.getCommandId(), taskId,
                        command.getExpectedVersion(), null, command.getResult(), hash, binding);
            }));
            return new EngineHumanTaskCompletionDTO().setTaskId(taskId).setCommandId(receipt.commandId())
                    .setState(EngineHumanTaskCompletionDTO.StateEnum.COMPLETED).setCompletedAt(receipt.occurredAt());
        } catch (FlowableOptimisticLockingException | FlowableTaskAlreadyClaimedException | FlowableObjectNotFoundException
                 | ConcurrencyFailureException race) {
            throw new ProcessVersionConflictException();
        }
    }

    // The exact v2 resource has already been checked by the calling boundary. Preserve the same identity and role proof
    // in the existing native and receipt participants, whose operation names describe the internal ACTION lifecycle.
    private static HumanTaskContext actionContext(HumanTaskContext context, String resource, String nativeOperation) {
        context.requireResource(resource);
        return new HumanTaskContext(context.actor(), nativeOperation, context.roleIds(), context.deadlineAt(),
                context.authVersion(), context.policyVersion());
    }

    private void requireAutomationStorage() {
        ownTransaction();
        if (automation == null) throw unavailable();
        automationVerifier.verifyAutomationV14();
    }

    public HumanTaskDTO get(HumanTaskContext context, GetHumanTaskQuery query) {
        context.requireResource(PREFIX + "get"); Objects.requireNonNull(query, "query"); ownTransaction();
        boolean automationPresent = hasAutomationStorage();
        return Objects.requireNonNull(read.execute(status -> {
            receipts.current(context); var task = readTask(context, query.taskId()).orElseThrow(ProcessResourceNotFoundException::new);
            var binding = taskBinding(context, task, automationPresent);
            if (binding.isPresent()) return actionProjection(context, actionSnapshot(context, task, binding.orElseThrow()));
            var result = load(context, task).orElseThrow(ProcessResourceNotFoundException::new).projection(context); receipts.current(context); return result;
        }));
    }

    public HumanTaskPageDTO query(HumanTaskContext context, ListHumanTasksQuery query) {
        if (!context.resourceKey().equals(PREFIX + "query") && !context.resourceKey().equals(PREFIX + "adminQuery")) throw new IllegalStateException("Task query context is required");
        Objects.requireNonNull(query, "query"); if (query.scopeType() == HumanTaskScopeTypeEnum.CASE && caseTasks == null) throw new IllegalArgumentException("Formal case tasks are not enabled"); ownTransaction();
        boolean automationPresent = hasAutomationStorage();
        return Objects.requireNonNull(read.execute(status -> {
            receipts.current(context); var ids = nativeAccess.candidates(context, query, query.limit()); var items = new ArrayList<HumanTaskDTO>();
            for (var id : ids) {
                var task = query.scopeType() == HumanTaskScopeTypeEnum.CASE ? nativeAccess.findCase(context, id, false) : nativeAccess.find(context, id, false); if (task.isEmpty()) continue;
                var binding = taskBinding(context, task.orElseThrow(), automationPresent);
                if (binding.isPresent()) {
                    var action = actionSnapshot(context, task.orElseThrow(), binding.orElseThrow());
                    if (HumanTaskAccessRules.readable(context, action.facts(), action.policy())) items.add(actionProjection(context, action));
                    continue;
                }
                var snapshot = load(context, task.orElseThrow());
                if (snapshot.isPresent() && HumanTaskAccessRules.readable(context, snapshot.orElseThrow().facts(), snapshot.orElseThrow().policy())) items.add(snapshot.orElseThrow().projection(context));
            }
            receipts.current(context); return new HumanTaskPageDTO(query.scopeType(), query.selection(), items, ids.size() == query.limit() ? ids.getLast() : null);
        }));
    }

    public HumanTaskActionReceiptDTO claim(HumanTaskContext context, ClaimHumanTaskCommand command) { return mutate(context, HumanTaskActionEnum.CLAIM, command.commandId(), command.taskId(), command.expectedVersion(), null, Map.of()); }
    public HumanTaskActionReceiptDTO unclaim(HumanTaskContext context, UnclaimHumanTaskCommand command) { return mutate(context, HumanTaskActionEnum.UNCLAIM, command.commandId(), command.taskId(), command.expectedVersion(), null, Map.of()); }
    public HumanTaskActionReceiptDTO transfer(HumanTaskContext context, TransferHumanTaskCommand command) { return mutate(context, HumanTaskActionEnum.TRANSFER, command.commandId(), command.taskId(), command.expectedVersion(), command.targetLane(), Map.of()); }
    public HumanTaskActionReceiptDTO complete(HumanTaskContext context, CompleteHumanTaskCommand command) { return mutate(context, HumanTaskActionEnum.COMPLETE, command.commandId(), command.taskId(), command.expectedVersion(), null, command.form()); }

    private HumanTaskActionReceiptDTO mutate(HumanTaskContext context, HumanTaskActionEnum action, String commandId, String taskId, String expectedVersion, String targetLane, Map<String, Object> form) {
        context.requireResource(PREFIX + action.name().toLowerCase(Locale.ROOT)); ownTransaction();
        String hash = ProcessRequestDigest.hash(List.of("TIANJI_HUMAN_TASK_COMMAND_V1", action.name(), taskId, expectedVersion, targetLane == null ? "" : targetLane), form);
        boolean automationPresent = hasAutomationStorage();
        try {
            return Objects.requireNonNull(write.execute(status -> {
                var automationTask = discoverAction(context, taskId, action, commandId, automationPresent);
                if (automationTask.isPresent()) {
                    // Typed ACTION results are accepted only by the v2 completion command.
                    if (action == HumanTaskActionEnum.COMPLETE) throw new ProcessResourceNotFoundException();
                    return mutateAction(context, action, commandId, taskId, expectedVersion, targetLane, form, hash, automationTask.orElseThrow());
                }
                var replay = receipts.lockCommand(context, action, commandId, hash);
                if (replay.isPresent()) {
                    var result = replay.orElseThrow().receipt();
                    if (!result.taskId().equals(taskId) || !result.previousVersion().equals(expectedVersion) || action == HumanTaskActionEnum.TRANSFER && !result.lane().equals(targetLane)) throw unavailable();
                    if (result.scopeType() == HumanTaskScopeTypeEnum.CASE) validateCaseReplay(context, replay.orElseThrow(), targetLane);
                    return result;
                }
                var discovered = caseTasks == null ? Optional.<Task>empty() : nativeAccess.findCase(context, taskId, false);
                Task task;
                if (discovered.isPresent()) {
                    String scopeId = discovered.orElseThrow().getScopeId(); caseProvenance.lockScope(context.actor().tenantId(), scopeId);
                    task = nativeAccess.findCase(context, taskId, true).orElseThrow(ProcessResourceNotFoundException::new);
                    if (!scopeId.equals(task.getScopeId())) throw unavailable();
                } else task = nativeAccess.find(context, taskId, true).orElseThrow(ProcessResourceNotFoundException::new);
                var before = load(context, task).orElseThrow(ProcessResourceNotFoundException::new); boolean caseTask = before.caseProvenance() != null;
                switch (action) {
                    case CLAIM -> HumanTaskAccessRules.claim(context, before.facts(), before.policy());
                    case UNCLAIM -> HumanTaskAccessRules.unclaim(context, before.facts());
                    case TRANSFER -> HumanTaskAccessRules.transfer(context, before.facts(), before.policy(), targetLane);
                    case COMPLETE -> HumanTaskAccessRules.complete(context, before.facts(), before.policy());
                }
                if (!before.version().equals(expectedVersion)) throw new ProcessConflictException();
                var targets = action == HumanTaskActionEnum.COMPLETE ? HumanTaskActionResult.formTargets(before.policy(), form) : Map.<String, Object>of();
                receipts.current(context); receipts.reserve(context, action, commandId, hash, before);
                switch (action) {
                    case CLAIM -> { if (caseTask) caseTasks.claim(taskId, context.userKey()); else tasks.claim(taskId, context.userKey()); }
                    case UNCLAIM -> { if (caseTask) caseTasks.unclaim(taskId); else tasks.unclaim(taskId); }
                    case TRANSFER -> {
                        if (before.facts().assignee() != null) { if (caseTask) caseTasks.unclaim(taskId); else tasks.unclaim(taskId); }
                        for (var role : before.policy().lanes().get(before.lane())) { if (caseTask) caseTasks.deleteGroupIdentityLink(taskId, "role:" + role, "candidate"); else tasks.deleteCandidateGroup(taskId, "role:" + role); }
                        for (var role : before.policy().lanes().get(targetLane)) { if (caseTask) caseTasks.addGroupIdentityLink(taskId, "role:" + role, "candidate"); else tasks.addCandidateGroup(taskId, "role:" + role); }
                        // Candidate-only changes must advance the native revision even when assignment is already empty.
                        var fresh = (caseTask ? nativeAccess.findCase(context, taskId, true) : nativeAccess.find(context, taskId, true)).orElseThrow(FlowableHumanTaskService::unavailable);
                        if (!(fresh instanceof TaskEntity entity)) throw unavailable(); entity.forceUpdate(); if (caseTask) caseTasks.saveTask(entity); else tasks.saveTask(entity);
                    }
                    case COMPLETE -> { if (caseTask) caseTasks.complete(taskId, context.userKey(), targets); else tasks.complete(taskId, context.userKey(), targets); }
                }
                Optional<Task> remaining;
                if (caseTask && action == HumanTaskActionEnum.COMPLETE) {
                    // The active-affinity join may hide an ended PI; completion must also remove the actual Task.
                    receipts.current(context);
                    var residual = caseTasks.createTaskQuery().taskId(taskId).taskTenantId(Long.toString(context.actor().tenantId())).singleResult();
                    receipts.current(context); if (residual != null) throw unavailable(); remaining = Optional.empty();
                } else remaining = caseTask ? nativeAccess.findCase(context, taskId, true) : nativeAccess.find(context, taskId, true);
                HumanTaskSnapshot after = null;
                if (action == HumanTaskActionEnum.COMPLETE) { if (remaining.isPresent()) throw unavailable(); }
                else after = load(context, remaining.orElseThrow(FlowableHumanTaskService::unavailable)).orElseThrow(FlowableHumanTaskService::unavailable);
                var result = HumanTaskActionResult.applied(context, action, commandId, hash, before, after, targetLane, form, receipts.current(context));
                receipts.apply(context, result);
                var receipt = result.receipt();
                if (action == HumanTaskActionEnum.COMPLETE) {
                    completionObservation.recordOnCommit(receipt.scopeType(), before.createdAt(), before.dueAt(), receipt.occurredAt());
                }
                return receipt;
            }));
        } catch (FlowableOptimisticLockingException | FlowableTaskAlreadyClaimedException | FlowableObjectNotFoundException | ConcurrencyFailureException race) { throw new ProcessConflictException(); }
    }

    private boolean hasAutomationStorage() {
        return automation != null && controlNative.automationStoragePresent();
    }

    private Optional<AutomationInstanceBindingBO> discoverAction(HumanTaskContext context, String taskId,
            HumanTaskActionEnum action, String commandId, boolean automationPresent) {
        if (!automationPresent) return Optional.empty();
        receipts.current(context);
        var actor = context.actor();
        var task = tasks.createTaskQuery().taskId(taskId).taskTenantId(Long.toString(actor.tenantId())).singleResult();
        if (task != null) return taskBinding(context, task, true);
        // Read the exact command's source before any row locks, including when native completion already removed the task.
        var sources = jdbc.query("SELECT process_instance_id,release_key FROM " + humanReceipts + " WHERE tenant_id=? AND subject_type='USER'"
                        + " AND subject_issuer=? AND subject_id=? AND command_type=? AND command_id=? AND task_id=?",
                (rs, row) -> new ActionReceiptSource(rs.getString(1), rs.getString(2)), actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(),
                "TASK_" + action.name(), commandId, taskId);
        if (sources.size() > 1) throw unavailable();
        if (sources.isEmpty()) return Optional.empty();
        var original = sources.getFirst();
        if (!isAutomationRelease(context, original.releaseKey())) return Optional.empty();
        return Optional.of(automation.bindings().find(actor.tenantId(), original.instanceId())
                .orElseThrow(FlowableHumanTaskService::unavailable));
    }

    private Optional<AutomationInstanceBindingBO> taskBinding(HumanTaskContext context, Task task, boolean automationPresent) {
        if (!automationPresent || task.getProcessInstanceId() == null || task.getScopeType() != null) return Optional.empty();
        var origin = origins.taskOrigin(context.actor(), task.getProcessInstanceId());
        if (!isAutomationRelease(context, origin.releaseKey())) return Optional.empty();
        var binding = automation.bindings().find(context.actor().tenantId(), origin.instanceId()).orElseThrow(FlowableHumanTaskService::unavailable);
        requireActionOrigin(binding, origin);
        return Optional.of(binding);
    }

    private boolean isAutomationRelease(HumanTaskContext context, String releaseKey) {
        long tenantId = context.actor().tenantId();
        if (controlNative.hasAutomationRelease(tenantId, releaseKey)) return true;
        if (controlNative.hasAutomationRootInput(tenantId, releaseKey)
                && releases.readMigrationRelease(context.actor(), releaseKey).isEmpty()) throw unavailable();
        return false;
    }

    private HumanTaskActionReceiptDTO mutateAction(HumanTaskContext context, HumanTaskActionEnum action, String commandId,
            String taskId, String expectedVersion, String targetLane, Map<String, Object> form, String hash,
            AutomationInstanceBindingBO discovered) {
        var actor = context.actor();
        var binding = automation.guard().lockKnownRoot(actor.tenantId(), discovered.getInstanceId());
        if (!binding.equals(discovered)) throw unavailable();
        var scopedTask = tasks.createTaskQuery().taskId(taskId).taskTenantId(Long.toString(actor.tenantId()))
                .processInstanceId(binding.getInstanceId()).singleResult();
        var task = scopedTask == null ? Optional.<Task>empty() : nativeAccess.find(context, taskId, true);
        var old = receipts.lockCommand(context, action, commandId, hash);
        if (old.isPresent()) return replayAction(context, action, expectedVersion, targetLane, taskId, binding, old.orElseThrow());
        var before = actionSnapshot(context, task.orElseThrow(ProcessResourceNotFoundException::new), binding);
        switch (action) {
            case CLAIM -> HumanTaskAccessRules.claim(context, before.facts(), before.policy());
            case UNCLAIM -> HumanTaskAccessRules.unclaim(context, before.facts());
            case TRANSFER -> HumanTaskAccessRules.transfer(context, before.facts(), before.policy(), targetLane);
            case COMPLETE -> HumanTaskAccessRules.complete(context, before.facts(), before.policy());
        }
        if (!expectedVersion.equals(before.version())) throw new ProcessVersionConflictException();
        if (!receipts.current(context).isBefore(before.dueAt())) throw new ProcessVersionConflictException();
        var normalized = action == HumanTaskActionEnum.COMPLETE ? normalizeActionOutput(before.schema(), form) : Map.<String, Object>of();
        var origin = before.origin();
        var kind = ProcessCommandReceiptStore.Kind.valueOf("TASK_" + action.name());
        if (!origins.insert(actor, kind, commandId, hash, origin.releaseKey(), origin.correlationId())) throw new ProcessConflictException();
        switch (action) {
            case CLAIM -> tasks.claim(taskId, context.userKey());
            case UNCLAIM -> tasks.unclaim(taskId);
            case TRANSFER -> transferAction(taskId, before, targetLane);
            case COMPLETE -> {
                var node = before.node();
                if (!automation.nodes().completeHuman(actor.tenantId(), binding.getInstanceId(),
                        node.getNodeExecutionRef(), node.getRevision(), normalized)) throw new ProcessVersionConflictException();
                tasks.complete(taskId, context.userKey(), Map.of());
            }
        }
        var residual = nativeAccess.find(context, taskId, true);
        ActionSnapshot after = null;
        if (action == HumanTaskActionEnum.COMPLETE) {
            if (residual.isPresent()) throw unavailable();
            var node = automation.nodes().find(actor.tenantId(), binding.getInstanceId(), before.node().getNodeExecutionRef())
                    .orElseThrow(FlowableHumanTaskService::unavailable);
            if (node.getState() != top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum.COMPLETED) {
                throw unavailable();
            }
        } else {
            after = actionSnapshot(context, residual.orElseThrow(FlowableHumanTaskService::unavailable), binding);
            String assignee = action == HumanTaskActionEnum.CLAIM ? context.userKey() : null;
            if (!before.node().getNodeExecutionRef().equals(after.node().getNodeExecutionRef()) || !Objects.equals(after.facts().assignee(), assignee)
                    || !after.facts().lane().equals(action == HumanTaskActionEnum.TRANSFER ? targetLane : before.facts().lane())
                    || before.version().equals(after.version())) throw unavailable();
        }
        Instant time = receipts.current(context).truncatedTo(ChronoUnit.MICROS);
        var result = actionReceipt(context, action, commandId, hash, before, after, time);
        receipts.apply(context, result);
        if (action == HumanTaskActionEnum.COMPLETE) {
            completionObservation.recordOnCommit(HumanTaskScopeTypeEnum.PROCESS, before.createdAt(), before.dueAt(), time);
        }
        return result.receipt();
    }

    private void transferAction(String taskId, ActionSnapshot before, String targetLane) {
        if (before.facts().assignee() != null) tasks.unclaim(taskId);
        for (var role : before.policy().lanes().get(before.facts().lane())) tasks.deleteCandidateGroup(taskId, "role:" + role);
        for (var role : before.policy().lanes().get(targetLane)) tasks.addCandidateGroup(taskId, "role:" + role);
        var current = tasks.createTaskQuery().taskId(taskId).processInstanceId(before.origin().instanceId()).singleResult();
        if (!(current instanceof TaskEntity task) || !current.getTenantId().equals(before.task().getTenantId())) throw unavailable();
        task.forceUpdate();
        tasks.saveTask(task);
    }

    private HumanTaskActionReceiptDTO replayAction(HumanTaskContext context, HumanTaskActionEnum action, String expectedVersion,
            String targetLane, String taskId, AutomationInstanceBindingBO binding, Entry entry) {
        var origin = origins.taskOrigin(context.actor(), binding.getInstanceId());
        requireActionOrigin(binding, origin);
        var result = entry.receipt();
        var definition = controlNative.automationDefinition(binding);
        if (!entry.origin().equals(origin) || !result.taskId().equals(taskId) || !result.previousVersion().equals(expectedVersion)
                || result.kind() != HumanTaskKindEnum.ACTION || result.scopeType() != HumanTaskScopeTypeEnum.PROCESS
                || !definition.engineDefinitionId().equals(entry.engineDefinitionId()) || !definition.modelSha256().equals(entry.modelSha256())
                || action == HumanTaskActionEnum.TRANSFER && !result.lane().equals(targetLane)) throw unavailable();
        if (action == HumanTaskActionEnum.COMPLETE || action == HumanTaskActionEnum.CLAIM) {
            var policy = actionPolicy(binding, result.taskDefinitionKey());
            var roles = policy.policy().lanes().get(result.lane());
            if (roles == null || entry.authorizedRoleId() == null || !roles.contains(entry.authorizedRoleId())) throw unavailable();
            if (roles.stream().noneMatch(context.roleIds()::contains)) {
                throw new ProcessResourceNotFoundException();
            }
            String assignee = action == HumanTaskActionEnum.COMPLETE ? entry.previousAssignee() : entry.resultingAssignee();
            if (!context.userKey().equals(assignee)) throw unavailable();
        }
        receipts.current(context);
        return result;
    }

    private static Entry actionReceipt(HumanTaskContext context, HumanTaskActionEnum action, String commandId, String hash,
            ActionSnapshot before, ActionSnapshot after, Instant occurredAt) {
        var definition = before.definition();
        var origin = before.origin();
        String lane = after == null ? before.facts().lane() : after.facts().lane();
        var receipt = new HumanTaskActionReceiptDTO(commandId, before.task().getId(), action, HumanTaskScopeTypeEnum.PROCESS,
                origin.instanceId(), origin.releaseKey(), definition.definitionKey(), before.task().getTaskDefinitionKey(), HumanTaskKindEnum.ACTION,
                lane, before.version(), after == null ? null : after.version(), null, null, occurredAt);
        String role = action == HumanTaskActionEnum.CLAIM || action == HumanTaskActionEnum.COMPLETE
                ? before.policy().lanes().get(before.facts().lane()).stream().filter(context.roleIds()::contains)
                        .sorted().findFirst().orElseThrow(FlowableHumanTaskService::unavailable) : null;
        return new Entry(context.actor(), hash, receipt, origin, definition.modelSha256(), definition.engineDefinitionId(),
                role, context.authVersion(), context.policyVersion(), before.facts().assignee(),
                after == null ? null : after.facts().assignee());
    }

    private ActionSnapshot actionSnapshot(HumanTaskContext context, Task task, AutomationInstanceBindingBO binding) {
        var actor = context.actor();
        var origin = origins.taskOrigin(actor, binding.getInstanceId());
        requireActionOrigin(binding, origin);
        var definition = controlNative.automationDefinition(binding);
        if (!(task instanceof TaskEntity nativeTask) || nativeTask.getRevision() < 1 || task.getCreateTime() == null
                || task.getDueDate() == null || task.getExecutionId() == null || task.getFormKey() != null
                || task.getScopeType() != null || task.getScopeId() != null || task.getParentTaskId() != null
                || !binding.getInstanceId().equals(task.getProcessInstanceId()) || !definition.engineDefinitionId().equals(task.getProcessDefinitionId())
                || !Long.toString(binding.getTenantId()).equals(task.getTenantId())) throw unavailable();
        var policy = actionPolicy(binding, task.getTaskDefinitionKey());
        var node = automation.nodes().findCurrentHuman(binding.getTenantId(), binding.getInstanceId(), task.getExecutionId(), policy.logicalNodeId())
                .orElseThrow(ProcessResourceNotFoundException::new);
        if (!node.getOutputSchemaSha256().equals(ProcessNodeLifecycleListener.schemaSha256(policy.schema())) || !binding.getConfigurationSha256().equals(node.getConfigurationSha256())) {
            throw unavailable();
        }
        var links = tasks.getIdentityLinksForTask(task.getId());
        if (links == null || links.size() > 64) throw unavailable();
        var groups = new HashSet<String>();
        var identities = new ArrayList<String>();
        for (var link : links) {
            if (link == null || !task.getId().equals(link.getTaskId()) || link.getType() == null || link.getType().isBlank()) throw unavailable();
            if ("candidate".equals(link.getType())) {
                String group = link.getGroupId();
                if (link.getUserId() != null || group == null || !group.matches("role:[1-9][0-9]{0,18}") || !groups.add(group.substring(5))) throw unavailable();
            }
            identities.add(ProcessRequestDigest.hash(List.of(link.getType(), value(link.getUserId()), value(link.getGroupId())), Map.of()));
        }
        var lanes = policy.policy().lanes().entrySet().stream().filter(entry -> entry.getValue().equals(groups)).map(Map.Entry::getKey).toList();
        if (lanes.size() != 1) throw unavailable();
        Instant created = task.getCreateTime().toInstant();
        Instant due = task.getDueDate().toInstant();
        if (due.isAfter(node.getAttemptDeadlineAt())) throw unavailable();
        boolean delegated = task.getOwner() != null || task.getDelegationState() != null;
        var facts = new HumanTaskAccessRules.Facts(origin.requester(), lanes.getFirst(), task.getAssignee(), task.isSuspended(), delegated);
        var fields = new ArrayList<>(List.of("TIANJI_HUMAN_TASK_SNAPSHOT_V2", task.getId(), task.getTaskDefinitionKey(),
                Long.toString(binding.getTenantId()), binding.getOwner().issuerFingerprint(), origin.requestHash(), binding.getInstanceId(),
                definition.engineDefinitionId(), definition.modelSha256(), node.getNodeExecutionRef(), Long.toString(node.getRevision()),
                node.getOutputSchemaSha256(), Integer.toString(nativeTask.getRevision()), task.getExecutionId(), lanes.getFirst(),
                value(task.getAssignee()), value(task.getOwner()), value(task.getState()), Boolean.toString(task.isSuspended()), Boolean.toString(delegated),
                created.toString(), due.toString()));
        fields.addAll(identities.stream().sorted().toList());
        return new ActionSnapshot(nativeTask, origin, definition, node, policy.schema(), policy.policy(), facts,
                ProcessRequestDigest.hash(fields, Map.of()), created, due);
    }

    private ActionPolicy actionPolicy(AutomationInstanceBindingBO binding, String nativeKey) {
        var release = automation.releases().find(binding.getTenantId(), binding.getReleaseKey()).orElseThrow(FlowableHumanTaskService::unavailable);
        if (!binding.getDefinitionKey().equals(release.rootDefinitionKey())) throw unavailable();
        releases.requireVerifiedAutomation(release);
        var source = release.source().nodeBindings().stream().filter(node -> node.human() != null
                && nativeKey.equals("ta_" + ProcessTypedJson.sha256("AUTOMATION_NATIVE_ID_V2",
                        "activity".getBytes(StandardCharsets.UTF_8), node.nodeId().getBytes(StandardCharsets.UTF_8)).substring(0, 40)))
                .findFirst().orElseThrow(ProcessResourceNotFoundException::new);
        String reference = source.human().candidateBindingRef();
        String digest = ProcessNodeLifecycleListener.expectedHumanPolicySha256(release, source.nodeId());
        var candidate = automation.lifecycle().requireCandidatePolicy(binding.getTenantId(), reference, digest);
        return new ActionPolicy(source.nodeId(), source.human().resultSchema(),
                new HumanTask(HumanTaskKind.ACTION, candidate.lanes(), candidate.initialLane(), null, null, List.of()));
    }

    private static HumanTaskDTO actionProjection(HumanTaskContext context, ActionSnapshot action) {
        if (!HumanTaskAccessRules.readable(context, action.facts(), action.policy())) throw new ProcessResourceNotFoundException();
        var task = action.task();
        String name = task.getName() == null || task.getName().isBlank() ? task.getTaskDefinitionKey() : task.getName().strip();
        return new HumanTaskDTO(task.getId(), HumanTaskScopeTypeEnum.PROCESS, action.origin().instanceId(), action.origin().releaseKey(),
                action.definition().definitionKey(), task.getTaskDefinitionKey(), name, HumanTaskKindEnum.ACTION, null, action.version(),
                action.facts().lane(), action.policy().lanes().keySet().stream().sorted().toList(), action.facts().assignee() != null,
                context.userKey().equals(action.facts().assignee()), action.facts().suspended(), Map.of(), action.createdAt(), action.dueAt());
    }

    private static void requireActionOrigin(AutomationInstanceBindingBO binding, ProcessCommandReceiptStore.TaskOrigin origin) {
        if (!binding.getOwner().equals(origin.requester()) || !binding.getInstanceId().equals(origin.instanceId())
                || !binding.getReleaseKey().equals(origin.releaseKey()) || !binding.getSourceCommandId().equals(origin.commandId())
                || !binding.getSubmittedAt().equals(origin.createdAt()) || !binding.getResolvedStartSha256().equals(origin.requestHash())) throw unavailable();
    }

    private static String value(String value) { return value == null ? "" : value; }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeActionOutput(ProcessSchemaVO schema, Map<String, Object> output) {
        return (Map<String, Object>) schema.normalizeValue(output);
    }

    public record AutomationTaskParticipants(ProcessInstanceBindingStore bindings, AutomationReleaseStore releases,
            AutomationInstanceGuardCommandInterceptor guard, ProcessNodeExecutionStore nodes, ProcessNodeLifecycleListener lifecycle) {
        public AutomationTaskParticipants {
            Objects.requireNonNull(bindings, "bindings");
            Objects.requireNonNull(releases, "releases");
            Objects.requireNonNull(guard, "guard");
            Objects.requireNonNull(nodes, "nodes");
            Objects.requireNonNull(lifecycle, "lifecycle");
        }
    }

    private record ActionPolicy(String logicalNodeId, ProcessSchemaVO schema, HumanTask policy) { }

    private record ActionReceiptSource(String instanceId, String releaseKey) { }

    private record ActionSnapshot(TaskEntity task, ProcessCommandReceiptStore.TaskOrigin origin, Definition definition,
            ProcessNodeExecutionBO node, ProcessSchemaVO schema, HumanTask policy, HumanTaskAccessRules.Facts facts,
            String version, Instant createdAt, Instant dueAt) { }

    private Optional<HumanTaskSnapshot> load(HumanTaskContext context, Task task) {
        if ("cmmn".equals(task.getScopeType())) return loadCase(context, task);
        var origin = origins.taskOrigin(context.actor(), task.getProcessInstanceId()); var release = releases.get(context.actor(), origin.releaseKey());
        var definition = release.definitions().stream().filter(value -> value.type() == Type.BPMN && value.engineDefinitionId().equals(task.getProcessDefinitionId())).findFirst().orElseThrow(FlowableHumanTaskService::unavailable);
        var model = models.runningProcess(context.actor().tenantId(), definition); if (!model.humanTasks().containsKey(task.getTaskDefinitionKey())) return Optional.empty();
        var decisions = model.decisionTasks().values().stream().distinct().toList();
        var decision = decisions.isEmpty() ? null : release.definitions().stream().filter(value -> value.type() == Type.DMN && value.definitionKey().equals(decisions.getFirst())).findFirst().orElseThrow(FlowableHumanTaskService::unavailable);
        if (decisions.size() > 1) throw unavailable(); validator.validateHumanTaskBindings(model, definition.inputs(), decision == null ? null : models.decision(context.actor().tenantId(), decision));
        var result = new HumanTaskSnapshot(task, tasks.getIdentityLinksForTask(task.getId()), origin, definition, model); receipts.current(context); return Optional.of(result);
    }
    private Optional<Task> readTask(HumanTaskContext context, String taskId) {
        if (caseTasks != null) { var task = nativeAccess.findCase(context, taskId, false); if (task.isPresent()) return task; }
        return nativeAccess.find(context, taskId, false);
    }
    private Optional<HumanTaskSnapshot> loadCase(HumanTaskContext context, Task task) {
        if (caseTasks == null || caseProvenance == null) throw new ProcessResourceNotFoundException();
        var origin = caseProvenance.find(context.actor().tenantId(), task.getScopeId()).orElseThrow(ProcessResourceNotFoundException::new);
        var definition = caseDefinition(context, origin); var model = models.caseModel(context.actor().tenantId(), definition);
        if (!model.humanTasks().containsKey(task.getTaskDefinitionKey())) return Optional.empty();
        var snapshot = new HumanTaskSnapshot(task, caseTasks.getIdentityLinksForTask(task.getId()), origin, definition, model); receipts.current(context); return Optional.of(snapshot);
    }
    private Definition caseDefinition(HumanTaskContext context, CaseStartProvenanceStore.Entry provenance) {
        receipts.current(context); var seed = provenance.seed();
        if (context.actor().tenantId() != seed.owner().tenantId() || !context.actor().issuerFingerprint().equals(seed.owner().issuerFingerprint())) throw new ProcessResourceNotFoundException();
        var release = releases.get(context.actor(), seed.releaseKey());
        var definitions = release.definitions().stream().filter(value -> value.type() == Type.CMMN && value.engineDefinitionId().equals(seed.caseEngineDefinitionId())).toList();
        if (definitions.size() != 1) throw unavailable(); var definition = definitions.getFirst();
        if (!definition.definitionKey().equals(seed.caseDefinitionKey()) || !definition.modelSha256().equals(seed.caseModelSha256())) throw unavailable();
        return definition;
    }

    /** Stored assignee/role facts are audit only; CASE replay is authorized again under the same scope as live mutations. */
    private void validateCaseReplay(HumanTaskContext context, Entry entry, String targetLane) {
        if (caseTasks == null || caseProvenance == null || entry.caseProvenance() == null || !entry.actor().equals(context.actor())) throw unavailable();
        var receipt = entry.receipt(); caseProvenance.lockScope(context.actor().tenantId(), receipt.scopeId()); receipts.current(context);
        var fresh = caseProvenance.find(context.actor().tenantId(), receipt.scopeId()).orElseThrow(ProcessResourceNotFoundException::new);
        if (!fresh.equals(entry.caseProvenance())) throw unavailable();
        var definition = caseDefinition(context, fresh); var model = models.caseModel(context.actor().tenantId(), definition); var policy = model.humanTasks().get(receipt.taskDefinitionKey());
        if (policy == null || policy.kind() != top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTaskKind.ACTION || !policy.fields().isEmpty()
                || !policy.lanes().containsKey(receipt.lane()) || !entry.modelSha256().equals(definition.modelSha256()) || !entry.engineDefinitionId().equals(definition.engineDefinitionId())) throw unavailable();
        var roles = policy.lanes().get(receipt.lane());
        switch (receipt.action()) {
            case CLAIM, COMPLETE -> {
                if (entry.authorizedRoleId() == null || !roles.contains(entry.authorizedRoleId())) throw unavailable();
                if (roles.stream().noneMatch(context.roleIds()::contains)) throw new ProcessResourceNotFoundException();
                String assignee = receipt.action() == HumanTaskActionEnum.CLAIM ? entry.resultingAssignee() : entry.previousAssignee();
                if (!context.userKey().equals(assignee)) throw unavailable();
            }
            case UNCLAIM -> { if (!context.userKey().equals(entry.previousAssignee())) throw new ProcessResourceNotFoundException(); }
            case TRANSFER -> { if (targetLane == null || !policy.lanes().containsKey(targetLane) || !targetLane.equals(receipt.lane())) throw unavailable(); }
        }
        receipts.current(context);
    }
    private static void ownTransaction() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Human task service must own its short transaction"); }
    private static IllegalStateException unavailable() { return new IllegalStateException("Human task native result is inconsistent with its released source"); }
}
