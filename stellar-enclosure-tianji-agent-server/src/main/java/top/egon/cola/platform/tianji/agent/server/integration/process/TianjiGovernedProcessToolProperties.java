package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolApprovalBinding;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;

import java.util.List;

/** Finite operator-owned decision and START profiles; disabled by default. */
@ConfigurationProperties(prefix = "stellar.tianji.agent.tools.governed-process", ignoreUnknownFields = false)
public record TianjiGovernedProcessToolProperties(boolean enabled, int concurrency,
        List<TianjiFixedDecisionToolBindings.BindingVO> decisionBindings,
        List<TianjiFixedBpmnStartToolBindings.BindingVO> startBindings,
        List<TianjiFixedToolApprovalBindings.BindingVO> approvalBindings) implements EnvironmentAware {
    public TianjiGovernedProcessToolProperties(boolean enabled, int concurrency,
            List<TianjiFixedDecisionToolBindings.BindingVO> decisionBindings,
            List<TianjiFixedBpmnStartToolBindings.BindingVO> startBindings) {
        this(enabled, concurrency, decisionBindings, startBindings, null);
    }
    @ConstructorBinding
    public TianjiGovernedProcessToolProperties {
        concurrency = concurrency == 0 ? 4 : concurrency;
        decisionBindings = decisionBindings == null ? List.of() : List.copyOf(decisionBindings);
        startBindings = startBindings == null ? List.of() : List.copyOf(startBindings);
        approvalBindings = approvalBindings == null ? List.of() : List.copyOf(approvalBindings);
        if (concurrency < 1 || concurrency > 8 || decisionBindings.size() > 256 || startBindings.size() > 256 || approvalBindings.size() > 256
                || enabled && (decisionBindings.isEmpty() || startBindings.isEmpty())) throw invalid();
    }
    @Override public void setEnvironment(Environment environment) {
        try {
            var binder = Binder.get(environment);
            // Independent indexed roots keep both backend models and resource references closed in Boot 4.1.2.
            for (int index = 0; index < decisionBindings.size(); index++) {
                String prefix = "stellar.tianji.agent.tools.governed-process.decision-bindings[" + index + ']';
                var backend = binder.bind(prefix + ".backend", Bindable.of(TianjiFixedDecisionToolBindings.BackendVO.class),
                        new NoUnboundElementsBindHandler(BindHandler.DEFAULT)).orElseThrow(TianjiGovernedProcessToolProperties::invalid);
                var tool = binder.bind(prefix + ".tool", Bindable.of(DefinitionResourceRef.class),
                        new NoUnboundElementsBindHandler(BindHandler.DEFAULT)).orElseThrow(TianjiGovernedProcessToolProperties::invalid);
                if (!decisionBindings.get(index).backend().equals(backend) || !decisionBindings.get(index).tool().equals(tool)) throw invalid();
            }
            for (int index = 0; index < startBindings.size(); index++) {
                String prefix = "stellar.tianji.agent.tools.governed-process.start-bindings[" + index + ']';
                var backend = binder.bind(prefix + ".backend", Bindable.of(TianjiFixedBpmnStartToolBindings.BackendVO.class),
                        new NoUnboundElementsBindHandler(BindHandler.DEFAULT)).orElseThrow(TianjiGovernedProcessToolProperties::invalid);
                var tool = binder.bind(prefix + ".tool", Bindable.of(DefinitionResourceRef.class),
                        new NoUnboundElementsBindHandler(BindHandler.DEFAULT)).orElseThrow(TianjiGovernedProcessToolProperties::invalid);
                if (!startBindings.get(index).backend().equals(backend) || !startBindings.get(index).tool().equals(tool)) throw invalid();
            }
            for (int index = 0; index < approvalBindings.size(); index++) {
                String prefix = "stellar.tianji.agent.tools.governed-process.approval-bindings[" + index + ']';
                var backend = binder.bind(prefix + ".backend", Bindable.of(TianjiFixedToolApprovalBindings.BackendVO.class),
                        new NoUnboundElementsBindHandler(BindHandler.DEFAULT)).orElseThrow(TianjiGovernedProcessToolProperties::invalid);
                var tool = binder.bind(prefix + ".tool", Bindable.of(DefinitionResourceRef.class),
                        new NoUnboundElementsBindHandler(BindHandler.DEFAULT)).orElseThrow(TianjiGovernedProcessToolProperties::invalid);
                var approval = binder.bind(prefix + ".approval", Bindable.of(ToolApprovalBinding.class),
                        new NoUnboundElementsBindHandler(BindHandler.DEFAULT)).orElseThrow(TianjiGovernedProcessToolProperties::invalid);
                var consumer = binder.bind(prefix + ".approval.consumer", Bindable.of(TrustedAgentActor.class),
                        new NoUnboundElementsBindHandler(BindHandler.DEFAULT)).orElseThrow(TianjiGovernedProcessToolProperties::invalid);
                var expected = approvalBindings.get(index);
                if (!expected.backend().equals(backend) || !expected.tool().equals(tool) || !expected.approval().equals(approval)
                        || !expected.approval().consumer().equals(consumer)) throw invalid();
            }
        } catch (RuntimeException privateConfiguration) { throw invalid(); }
    }
    @Override public String toString() { return "TianjiGovernedProcessToolProperties[redacted]"; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TIANJI_GOVERNED_PROCESS_TOOL_CONFIGURATION_INVALID"); }
}
