package top.egon.cola.platform.tianji.process.server.task;

import org.flowable.cmmn.engine.impl.behavior.impl.HumanTaskActivityBehavior;
import org.flowable.cmmn.engine.impl.persistence.entity.PlanItemInstanceEntity;
import org.flowable.cmmn.engine.impl.util.CommandContextUtil;
import org.flowable.cmmn.engine.interceptor.CreateHumanTaskBeforeContext;
import org.flowable.cmmn.engine.interceptor.MigrationContext;
import org.flowable.cmmn.model.HumanTask;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.common.engine.impl.context.Context;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import top.egon.cola.platform.tianji.process.server.execution.FlowableCaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;

import java.util.Objects;
import java.util.function.Supplier;

/** Rejects ungoverned native evaluation before super; the later candidate hook adds only released literal groups. */
public final class PinnedCaseHumanTaskActivityBehavior extends HumanTaskActivityBehavior {
    private final HumanTask modelTask;
    private final Supplier<FlowableCaseService> service;
    public PinnedCaseHumanTaskActivityBehavior(HumanTask task, Supplier<FlowableCaseService> service) { super(task); modelTask = Objects.requireNonNull(task, "task"); this.service = Objects.requireNonNull(service, "service"); }
    @Override public void execute(CommandContext context, PlanItemInstanceEntity item, MigrationContext migration) {
        policy(context, item, migration);
        super.execute(context, item, migration);
    }
    @Override protected void handleCandidateGroups(CommandContext context, PlanItemInstanceEntity item, ExpressionManager expressions, TaskEntity task, CreateHumanTaskBeforeContext before, MigrationContext migration) {
        var policy = policy(context, item, migration);
        if (task == null || !"cmmn".equals(task.getScopeType()) || !item.getTenantId().equals(task.getTenantId())
                || !item.getCaseInstanceId().equals(task.getScopeId()) || !item.getId().equals(task.getSubScopeId()) || !item.getCaseDefinitionId().equals(task.getScopeDefinitionId())
                || !modelTask.getId().equals(task.getTaskDefinitionKey()) || task.getProcessInstanceId() != null || task.getProcessDefinitionId() != null || task.getExecutionId() != null
                || task.getParentTaskId() != null || task.getAssignee() != null || task.getOwner() != null || task.getDelegationState() != null) throw unavailable();
        task.addCandidateGroups(policy.lanes().get(policy.initialLane()).stream().sorted().map(role -> "role:" + role).toList());
    }
    private ValidatedProcessModel.HumanTask policy(CommandContext context, PlanItemInstanceEntity item, MigrationContext migration) {
        if (migration != null || context == null || Context.getCommandContext() != context || item == null
                || !(CommandContextUtil.getCmmnEngineConfiguration(context) instanceof SpringCmmnEngineConfiguration configuration)
                || configuration.getCreateHumanTaskInterceptor() != null || !modelTask.isBlocking() || modelTask.getBlockingExpression() != null
                || modelTask.getAssignee() != null || modelTask.getOwner() != null || !modelTask.getCandidateUsers().isEmpty() || !modelTask.getCandidateGroups().isEmpty()
                || !modelTask.getTaskListeners().isEmpty() || modelTask.getTaskIdVariableName() != null || modelTask.getFormKey() != null
                || modelTask.getDueDate() != null || modelTask.getCategory() != null || modelTask.getPriority() != null
                || expression(modelTask.getName()) || expression(modelTask.getDocumentation()) || !modelTask.getId().equals(item.getPlanItemDefinitionId())) throw unavailable();
        var application = service.get(); if (application == null) throw unavailable();
        var model = application.nativeTaskPolicy(item); if (model == null) throw unavailable();
        var plan = model.planItems().get(item.getElementId()); var policy = model.humanTasks().get(modelTask.getId());
        if (plan == null || !modelTask.getId().equals(plan.definitionId()) || policy == null || policy.kind() != ValidatedProcessModel.HumanTaskKind.ACTION
                || policy.initialLane() == null || policy.lanes().size() != 1 || !policy.lanes().containsKey(policy.initialLane()) || policy.laneVariable() != null
                || policy.approvalVariable() != null || !policy.fields().isEmpty() || !policy.lanes().get(policy.initialLane()).equals(plan.roleIds())) throw unavailable();
        return policy;
    }
    private static boolean expression(String value) { return value != null && (value.contains("${") || value.contains("#{")); }
    private static IllegalStateException unavailable() { return new IllegalStateException("CMMN task does not match its fixed released policy"); }
}
