package top.egon.cola.platform.tianji.process.server.execution;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.flowable.engine.impl.bpmn.parser.BpmnParse;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.parse.BpmnParseHandler;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public final class AutomationBootstrapBehavior {
    static final String ROOT_INPUTS = "tianjiAutomationRootInputs";
    static final String SCOPE = "tianjiAutomationScope";
    static final String ROOT_OUTPUT = "tianjiAutomationRootOutput";
    private static final String DELEGATE = "${tianjiAutomationBootstrap}";
    private static final String MODEL_NAMESPACE = "urn:stellar-enclosure:tianji:automation:2";
    private static final String ACTIVITY_ID = "ta_" + ProcessTypedJson.sha256("AUTOMATION_NATIVE_ID_V2",
            "first".getBytes(StandardCharsets.UTF_8), "tianji_automation_bootstrap".getBytes(StandardCharsets.UTF_8)).substring(0, 40);

    @NonNull @Qualifier("processInstanceBindingStore") private final ProcessInstanceBindingStore bindings;
    @NonNull @Qualifier("automationReleaseStore") private final AutomationReleaseStore releases;

    public static void register(SpringProcessEngineConfiguration configuration, Supplier<AutomationBootstrapBehavior> bootstrap) {
        var existing = configuration.getPostBpmnParseHandlers();
        List<BpmnParseHandler> handlers = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        handlers.add(new BootstrapParseHandler(Objects.requireNonNull(bootstrap)));
        configuration.setPostBpmnParseHandlers(List.copyOf(handlers));
    }

    public void execute(DelegateExecution execution) {
        if (!(CommandContextUtil.getProcessEngineConfiguration() instanceof SpringProcessEngineConfiguration process)
                || !(CommandContextUtil.getDmnEngineConfiguration() instanceof SpringDmnEngineConfiguration dmn)
                || execution.getTenantId() == null || !execution.getTenantId().matches("[1-9][0-9]{0,18}")
                || !ACTIVITY_ID.equals(execution.getCurrentActivityId())
                || execution.getProcessInstanceId() == null) throw unavailable();
        long tenant;
        try { tenant = Long.parseLong(execution.getTenantId()); }
        catch (NumberFormatException invalid) { throw unavailable(); }

        var instanceId = execution.getProcessInstanceId();
        AutomationInstanceGuardCommandInterceptor.requireHeld(tenant, instanceId);
        var binding = bindings.find(tenant, instanceId).orElseThrow(AutomationBootstrapBehavior::unavailable);
        if (!instanceId.equals(execution.getVariable(ProcessReleaseService.AUTOMATION_BINDING_REF))) throw unavailable();
        var definition = bindings.nativeDefinition(tenant, binding.getReleaseKey(), binding.getDefinitionKey())
                .orElseThrow(AutomationBootstrapBehavior::unavailable);
        if (!definition.definitionId().equals(execution.getProcessDefinitionId())) throw unavailable();

        var release = releases.find(tenant, binding.getReleaseKey()).orElseThrow(AutomationBootstrapBehavior::unavailable);
        new ProcessReleaseService(process, dmn, new ProcessModelValidator()).requireVerifiedAutomation(release);
        var evidence = bindings.activationEvidence(binding).orElseThrow(AutomationBootstrapBehavior::unavailable);
        if (!evidence.sourceSha256().equals(release.sourceSha256()) || !evidence.compiledSha256().equals(release.compiledSha256())
                || !evidence.dependencySha256().equals(release.dependencySha256())
                || !binding.getDefinitionKey().equals(release.rootDefinitionKey())) throw unavailable();

        Object root = process.getRuntimeService().getVariableLocal(instanceId, ROOT_INPUTS);
        Object input = ProcessExecutionService.decodeNativeJson(process, root, 64 * 1024);
        var normalized = release.source().inputSchema().normalizeValue(input);
        if (!ProcessExecutionService.rootInputSha256(input).equals(ProcessExecutionService.rootInputSha256(normalized))) throw unavailable();
        var receipt = new ProcessCommandReceiptStore(new JdbcTemplate(
                ((DataSourceTransactionManager) process.getTransactionManager()).getDataSource()),
                process.getDatabaseSchema()).findOwnedStart(binding.getOwner(), binding.getSourceCommandId()).orElseThrow(AutomationBootstrapBehavior::unavailable);
        ProcessExecutionService.requireBoundReceipt(binding, receipt);
        ProcessExecutionService.requireResolvedInput(binding, execution.getProcessInstanceBusinessKey(), receipt.correlationId(), normalized);
        process.getRuntimeService().setVariableLocal(instanceId, SCOPE,
                process.getVariableJsonMapper().readTree(ProcessTypedJson.canonicalBytes(normalized, 64 * 1024)));
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Automation bootstrap binding is unavailable or inconsistent");
    }

    private record BootstrapParseHandler(Supplier<AutomationBootstrapBehavior> bootstrap) implements BpmnParseHandler {
        @Override
        public Collection<Class<? extends BaseElement>> getHandledTypes() { return List.of(ServiceTask.class); }

        @Override
        public void parse(BpmnParse parse, BaseElement element) {
            if (element instanceof ServiceTask task && ACTIVITY_ID.equals(task.getId()) && DELEGATE.equals(task.getImplementation())
                    && task.isAsynchronous() && parse.getCurrentProcess() != null
                    && "AUTOMATION_V2".equals(parse.getCurrentProcess().getAttributeValue(MODEL_NAMESPACE, "profile"))) {
                task.setBehavior(new BootstrapActivity(bootstrap));
            }
        }
    }

    private static final class BootstrapActivity extends AbstractBpmnActivityBehavior {
        private final Supplier<AutomationBootstrapBehavior> bootstrap;

        private BootstrapActivity(Supplier<AutomationBootstrapBehavior> bootstrap) {
            this.bootstrap = bootstrap;
        }

        @Override
        public void execute(DelegateExecution execution) {
            bootstrap.get().execute(execution);
            leave(execution);
        }
    }
}
