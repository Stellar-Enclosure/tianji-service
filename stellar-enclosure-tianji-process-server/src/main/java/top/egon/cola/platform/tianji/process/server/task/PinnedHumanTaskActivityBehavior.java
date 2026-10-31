package top.egon.cola.platform.tianji.process.server.task;

import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.service.TaskService;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import java.util.List;

/** Native assignment Adapter: only the immutable release and its typed DMN output choose candidate role groups. */
public final class PinnedHumanTaskActivityBehavior extends UserTaskActivityBehavior {
    private static final long serialVersionUID = 1L;

    public PinnedHumanTaskActivityBehavior(UserTask task) { super(task); }

    @Override
    protected void handleAssignments(TaskService taskService, String assignee, String owner, List<String> candidateUsers, List<String> candidateGroups,
            TaskEntity task, ExpressionManager expressionManager, DelegateExecution execution, ProcessEngineConfigurationImpl configuration) {
        if (!(configuration instanceof SpringProcessEngineConfiguration process) || CommandContextUtil.getProcessEngineConfiguration() != process
                || !(CommandContextUtil.getDmnEngineConfiguration() instanceof SpringDmnEngineConfiguration dmn)
                || execution.getTenantId() == null || !execution.getTenantId().matches("[1-9][0-9]{0,18}")
                || !userTask.getId().equals(execution.getCurrentActivityId()) || !userTask.getId().equals(task.getTaskDefinitionKey())
                || !execution.getTenantId().equals(task.getTenantId()) || execution.getProcessInstanceId() == null || !execution.getProcessInstanceId().equals(task.getProcessInstanceId())
                || execution.getProcessDefinitionId() == null || !execution.getProcessDefinitionId().equals(task.getProcessDefinitionId())
                || execution.getId() == null || !execution.getId().equals(task.getExecutionId())
                || task.getScopeType() != null || task.getScopeId() != null || task.getParentTaskId() != null
                || assignee != null && !assignee.isBlank() || owner != null && !owner.isBlank() || candidateUsers != null && !candidateUsers.isEmpty() || candidateGroups != null && !candidateGroups.isEmpty()
                || task.getAssignee() != null || task.getOwner() != null || task.getDelegationState() != null) throw unavailable();
        long tenant;
        try { tenant = Long.parseLong(execution.getTenantId()); } catch (NumberFormatException malformed) { throw unavailable(); }
        var validator = new ProcessModelValidator(); var release = new ProcessReleaseService(process, dmn, validator).getForProcessDefinition(tenant, execution.getProcessDefinitionId());
        var definition = release.definitions().stream().filter(value -> value.type() == Type.BPMN && value.engineDefinitionId().equals(execution.getProcessDefinitionId())).findFirst().orElseThrow(PinnedHumanTaskActivityBehavior::unavailable);
        var models = new VerifiedProcessModels(process, dmn, validator); var model = models.runningProcess(tenant, definition); var policy = model.humanTasks().get(userTask.getId()); if (policy == null) throw unavailable();
        var decisions = model.decisionTasks().values().stream().distinct().toList(); if (decisions.size() > 1) throw unavailable();
        var decision = decisions.isEmpty() ? null : release.definitions().stream().filter(value -> value.type() == Type.DMN && value.definitionKey().equals(decisions.getFirst())).findFirst().orElseThrow(PinnedHumanTaskActivityBehavior::unavailable);
        var decisionModel = decision == null ? null : models.decision(tenant, decision);
        if (decisionModel != null) validator.validateDecisionBinding(model, definition.inputs(), decisionModel);
        validator.validateHumanTaskBindings(model, definition.inputs(), decisionModel);
        Object selected = policy.initialLane() == null ? execution.getVariable(policy.laneVariable()) : policy.initialLane();
        if (!(selected instanceof String lane) || !policy.lanes().containsKey(lane)) throw unavailable();
        task.addCandidateGroups(policy.lanes().get(lane).stream().sorted().map(role -> "role:" + role).toList());
    }
    private static IllegalStateException unavailable() { return new IllegalStateException("Human task assignment does not match its fixed released policy"); }
}
