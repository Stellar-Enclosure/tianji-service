package top.egon.cola.platform.tianji.process.server.task;

import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.TaskService;
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
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ListHumanTasksQuery;
import top.egon.cola.platform.tianji.contract.orchestration.TransferHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.UnclaimHumanTaskCommand;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Internal current-user application service. Its caller supplies the trusted current operation context, never request role claims. */
public final class FlowableHumanTaskService {
    private static final String PREFIX = "tianji.api.process.task.";
    private final TaskService tasks;
    private final ProcessModelValidator validator;
    private final ProcessReleaseService releases;
    private final VerifiedProcessModels models;
    private final ProcessCommandReceiptStore origins;
    private final HumanTaskNativeAccess nativeAccess;
    private final HumanTaskReceiptStore receipts;
    private final TransactionTemplate read;
    private final TransactionTemplate write;

    public FlowableHumanTaskService(SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn,
            ProcessModelValidator validator, HumanTaskReceiptStore receipts) {
        Objects.requireNonNull(engine, "engine"); this.validator = Objects.requireNonNull(validator, "validator"); this.receipts = Objects.requireNonNull(receipts, "receipts");
        if (!(engine.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null || !receipts.usesDataSource(manager.getDataSource())) throw new IllegalArgumentException("Human task execution requires its exact owning receipt store and JDBC manager");
        tasks = Objects.requireNonNull(engine.getTaskService(), "taskService"); releases = new ProcessReleaseService(engine, dmn, validator); models = new VerifiedProcessModels(engine, dmn, validator);
        origins = new ProcessCommandReceiptStore(new JdbcTemplate(manager.getDataSource()), engine.getDatabaseSchema()); nativeAccess = new HumanTaskNativeAccess(manager.getDataSource(), engine);
        read = new TransactionTemplate(manager); read.setReadOnly(true); read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); read.setTimeout(10);
        write = new TransactionTemplate(manager); write.setTimeout(10);
    }

    public HumanTaskDTO get(HumanTaskContext context, GetHumanTaskQuery query) {
        context.requireResource(PREFIX + "get"); Objects.requireNonNull(query, "query"); ownTransaction();
        return Objects.requireNonNull(read.execute(status -> {
            receipts.current(context); var task = nativeAccess.find(context, query.taskId(), false).orElseThrow(ProcessResourceNotFoundException::new);
            var result = load(context, task).orElseThrow(ProcessResourceNotFoundException::new).projection(context); receipts.current(context); return result;
        }));
    }

    public HumanTaskPageDTO query(HumanTaskContext context, ListHumanTasksQuery query) {
        if (!context.resourceKey().equals(PREFIX + "query") && !context.resourceKey().equals(PREFIX + "adminQuery")) throw new IllegalStateException("Task query context is required");
        Objects.requireNonNull(query, "query"); if (query.scopeType() != HumanTaskScopeTypeEnum.PROCESS) throw new IllegalArgumentException("Formal case tasks are not enabled"); ownTransaction();
        return Objects.requireNonNull(read.execute(status -> {
            receipts.current(context); var ids = nativeAccess.candidates(context, query, query.limit()); var items = new ArrayList<HumanTaskDTO>();
            for (var id : ids) {
                var task = nativeAccess.find(context, id, false); if (task.isEmpty()) continue; var snapshot = load(context, task.orElseThrow());
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
        try {
            return Objects.requireNonNull(write.execute(status -> {
                var replay = receipts.lockCommand(context, action, commandId, hash);
                if (replay.isPresent()) {
                    var result = replay.orElseThrow().receipt();
                    if (!result.taskId().equals(taskId) || !result.previousVersion().equals(expectedVersion) || action == HumanTaskActionEnum.TRANSFER && !result.lane().equals(targetLane)) throw unavailable();
                    return result;
                }
                var task = nativeAccess.find(context, taskId, true).orElseThrow(ProcessResourceNotFoundException::new); var before = load(context, task).orElseThrow(ProcessResourceNotFoundException::new);
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
                    case CLAIM -> tasks.claim(taskId, context.userKey());
                    case UNCLAIM -> tasks.unclaim(taskId);
                    case TRANSFER -> {
                        if (before.facts().assignee() != null) tasks.unclaim(taskId);
                        for (var role : before.policy().lanes().get(before.lane())) tasks.deleteCandidateGroup(taskId, "role:" + role);
                        for (var role : before.policy().lanes().get(targetLane)) tasks.addCandidateGroup(taskId, "role:" + role);
                        // Candidate-only changes must advance the native revision even when assignment is already empty.
                        var fresh = nativeAccess.find(context, taskId, true).orElseThrow(FlowableHumanTaskService::unavailable);
                        if (!(fresh instanceof TaskEntity entity)) throw unavailable(); entity.forceUpdate(); tasks.saveTask(entity);
                    }
                    case COMPLETE -> tasks.complete(taskId, context.userKey(), targets);
                }
                var remaining = nativeAccess.find(context, taskId, true); HumanTaskSnapshot after = null;
                if (action == HumanTaskActionEnum.COMPLETE) { if (remaining.isPresent()) throw unavailable(); }
                else after = load(context, remaining.orElseThrow(FlowableHumanTaskService::unavailable)).orElseThrow(FlowableHumanTaskService::unavailable);
                var result = HumanTaskActionResult.applied(context, action, commandId, hash, before, after, targetLane, form, receipts.current(context)); receipts.apply(context, result); return result.receipt();
            }));
        } catch (FlowableOptimisticLockingException | FlowableTaskAlreadyClaimedException | FlowableObjectNotFoundException | ConcurrencyFailureException race) { throw new ProcessConflictException(); }
    }

    private Optional<HumanTaskSnapshot> load(HumanTaskContext context, Task task) {
        var origin = origins.taskOrigin(context.actor(), task.getProcessInstanceId()); var release = releases.get(context.actor(), origin.releaseKey());
        var definition = release.definitions().stream().filter(value -> value.type() == Type.BPMN && value.engineDefinitionId().equals(task.getProcessDefinitionId())).findFirst().orElseThrow(FlowableHumanTaskService::unavailable);
        var model = models.runningProcess(context.actor().tenantId(), definition); if (!model.humanTasks().containsKey(task.getTaskDefinitionKey())) return Optional.empty();
        var decisions = model.decisionTasks().values().stream().distinct().toList();
        var decision = decisions.isEmpty() ? null : release.definitions().stream().filter(value -> value.type() == Type.DMN && value.definitionKey().equals(decisions.getFirst())).findFirst().orElseThrow(FlowableHumanTaskService::unavailable);
        if (decisions.size() > 1) throw unavailable(); validator.validateHumanTaskBindings(model, definition.inputs(), decision == null ? null : models.decision(context.actor().tenantId(), decision));
        var result = new HumanTaskSnapshot(task, tasks.getIdentityLinksForTask(task.getId()), origin, definition, model); receipts.current(context); return Optional.of(result);
    }
    private static void ownTransaction() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Human task service must own its short transaction"); }
    private static IllegalStateException unavailable() { return new IllegalStateException("Human task native result is inconsistent with its released source"); }
}
