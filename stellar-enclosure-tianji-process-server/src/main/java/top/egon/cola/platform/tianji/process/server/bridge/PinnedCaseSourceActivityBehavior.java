package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.bpmn.model.CaseServiceTask;
import org.flowable.common.engine.impl.context.Context;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.behavior.CaseTaskActivityBehavior;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.spring.SpringProcessEngineConfiguration;
import top.egon.cola.platform.tianji.process.server.execution.FlowableCaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import java.util.Objects;
import java.util.function.Supplier;

/** Captures the actual native invocation; only the application starts the pinned Case and owns the completion protocol. */
public final class PinnedCaseSourceActivityBehavior extends CaseTaskActivityBehavior {
    private static final long serialVersionUID = 1L;
    private final CaseServiceTask sourceTask;
    private final Supplier<FlowableCaseService> services;
    private final Supplier<CaseSourceRegistry> registries;

    public PinnedCaseSourceActivityBehavior(CaseServiceTask task, Supplier<FlowableCaseService> service, Supplier<CaseSourceRegistry> registry) {
        super(); sourceTask = Objects.requireNonNull(task, "task"); services = Objects.requireNonNull(service, "service"); registries = Objects.requireNonNull(registry, "registry");
    }
    @Override public void execute(DelegateExecution execution) {
        var command = Context.getCommandContext();
        if (command == null || !(execution instanceof ExecutionEntity nativeExecution)
                || !(CommandContextUtil.getProcessEngineConfiguration() instanceof SpringProcessEngineConfiguration process)
                || !(CommandContextUtil.getDmnEngineConfiguration() instanceof SpringDmnEngineConfiguration dmn)
                || execution.getTenantId() == null || !execution.getTenantId().matches("[1-9][0-9]{0,18}")
                || !sourceTask.getId().equals(execution.getCurrentActivityId()) || !(execution.getCurrentFlowElement() instanceof CaseServiceTask)) throw unavailable();
        var activity = CommandContextUtil.getActivityInstanceEntityManager(command).findUnfinishedActivityInstance(nativeExecution);
        if (activity == null || activity.getId() == null || activity.getEndTime() != null || !execution.getId().equals(activity.getExecutionId())
                || !execution.getProcessInstanceId().equals(activity.getProcessInstanceId()) || !execution.getProcessDefinitionId().equals(activity.getProcessDefinitionId())
                || !sourceTask.getId().equals(activity.getActivityId()) || !execution.getTenantId().equals(activity.getTenantId())) throw unavailable();
        long tenant; try { tenant = Long.parseLong(execution.getTenantId()); } catch (NumberFormatException malformed) { throw unavailable(); }
        var validator = new ProcessModelValidator(); var release = new ProcessReleaseService(process, dmn, validator).getForProcessDefinition(tenant, execution.getProcessDefinitionId());
        var parent = release.definitions().stream().filter(value -> value.type() == Type.BPMN && value.engineDefinitionId().equals(execution.getProcessDefinitionId())).findFirst().orElseThrow(PinnedCaseSourceActivityBehavior::unavailable);
        var model = new VerifiedProcessModels(process, dmn, validator).runningProcess(tenant, parent); var source = model.caseSources().get(sourceTask.getId()); if (source == null) throw unavailable();
        var child = release.definitions().stream().filter(value -> value.type() == Type.CMMN && value.definitionKey().equals(source.caseDefinitionKey())).findFirst().orElseThrow(PinnedCaseSourceActivityBehavior::unavailable);
        var registry = registries.get(); var application = services.get(); if (registry == null || application == null) throw unavailable();
        var registration = registry.require(new CaseSourceRegistry.BindingKey(tenant, release.releaseKey(), parent.definitionKey(), parent.modelSha256(), sourceTask.getId(), child.definitionKey(), child.modelSha256()));
        application.startLinked(registration, new FlowableCaseService.NativeSourceFacts(execution.getProcessInstanceId(), execution.getProcessDefinitionId(), sourceTask.getId(), activity.getId(), execution.getId()));
        // Never invoke the native key/latest Case start or its completion/termination callback.
        leave(execution);
    }
    private static IllegalStateException unavailable() { return new IllegalStateException("Native Case source does not match its immutable released binding"); }
}
