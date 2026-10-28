package top.egon.cola.platform.tianji.process.server.decision;

import org.flowable.bpmn.model.SendTask;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.Task;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.behavior.DmnActivityBehavior;
import org.flowable.engine.impl.bpmn.parser.factory.DefaultActivityBehaviorFactory;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.spring.SpringProcessEngineConfiguration;
import top.egon.cola.platform.tianji.process.server.error.ProcessDecisionFailedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import java.util.HashMap;
import java.util.Map;

/** Replaces Flowable's latest-version DMN fallback with the process release binding. */
public final class PinnedDecisionActivityBehaviorFactory extends DefaultActivityBehaviorFactory {
    @Override
    public DmnActivityBehavior createDmnActivityBehavior(ServiceTask task) {
        return new PinnedDecisionActivityBehavior(task);
    }

    @Override
    public DmnActivityBehavior createDmnActivityBehavior(SendTask task) {
        return new PinnedDecisionActivityBehavior(task);
    }

    private static final class PinnedDecisionActivityBehavior extends DmnActivityBehavior {
        private static final long serialVersionUID = 1L;

        private PinnedDecisionActivityBehavior(Task task) { super(task); }

        @Override
        public void execute(DelegateExecution execution) {
            if (!(CommandContextUtil.getProcessEngineConfiguration() instanceof SpringProcessEngineConfiguration process)
                    || !(CommandContextUtil.getDmnEngineConfiguration() instanceof SpringDmnEngineConfiguration dmn)
                    || !dmn.isHistoryEnabled() || dmn.getDmnHistoryService() == null
                    || execution.getTenantId() == null || !execution.getTenantId().matches("[1-9][0-9]{0,18}")) throw unavailable();
            long tenant;
            try { tenant = Long.parseLong(execution.getTenantId()); }
            catch (NumberFormatException invalid) { throw unavailable(); }
            if (tenant <= 0 || !task.getId().equals(execution.getCurrentActivityId())) throw unavailable();

            var validator = new ProcessModelValidator();
            var release = new ProcessReleaseService(process, dmn, validator)
                    .getForProcessDefinition(tenant, execution.getProcessDefinitionId());
            Definition processBinding = release.definitions().stream().filter(item -> item.type() == Type.BPMN
                    && item.engineDefinitionId().equals(execution.getProcessDefinitionId())).findFirst().orElseThrow(PinnedDecisionActivityBehavior::unavailable);
            var models = new VerifiedProcessModels(process, dmn, validator);
            var processModel = models.runningProcess(tenant, processBinding);
            String decisionKey = processModel.decisionTasks().get(task.getId());
            if (decisionKey == null) throw unavailable();
            Definition decisionBinding = release.definitions().stream().filter(item -> item.type() == Type.DMN
                    && item.definitionKey().equals(decisionKey)).findFirst().orElseThrow(PinnedDecisionActivityBehavior::unavailable);
            var decisionModel = models.decision(tenant, decisionBinding);
            validator.validateDecisionBinding(processModel, processBinding.inputs(), decisionModel);

            Map<String, Object> inputs = new HashMap<>();
            for (String name : decisionModel.inputs().keySet()) inputs.put(name, execution.getVariable(name));
            var binding = new FlowablePinnedDecisionExecutor.Binding(tenant, decisionBinding.engineDefinitionId(),
                    decisionBinding.engineDeploymentId(), decisionBinding.engineVersion(), decisionModel);
            var reference = new FlowablePinnedDecisionExecutor.ProcessReference(execution.getProcessInstanceId(),
                    execution.getId(), task.getId());
            // Flowable flushes the decision history when the enclosing BPMN command completes.
            var result = new FlowablePinnedDecisionExecutor(dmn).evaluateForProcess(binding, inputs, reference);
            if (result.outputs().size() > 1 && result.outputs().stream().anyMatch(row -> !row.equals(result.outputs().getFirst()))) {
                throw new ProcessDecisionFailedException();
            }
            for (String output : decisionModel.outputs().keySet()) {
                execution.setVariable(output, result.outputs().isEmpty() ? null : result.outputs().getFirst().get(output));
            }
            leave(execution);
        }

        private static ProcessResourceNotFoundException unavailable() { return new ProcessResourceNotFoundException(); }
    }
}
