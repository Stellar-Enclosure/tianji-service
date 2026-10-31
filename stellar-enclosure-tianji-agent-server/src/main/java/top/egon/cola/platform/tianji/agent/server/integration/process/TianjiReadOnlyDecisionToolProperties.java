package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;

import java.util.List;

/** Finite operator-owned fixed decision settings; disabled by default. */
@ConfigurationProperties(prefix = "stellar.tianji.agent.tools.read-only-decision", ignoreUnknownFields = false)
public record TianjiReadOnlyDecisionToolProperties(boolean enabled, int concurrency,
        List<TianjiFixedDecisionToolBindings.BindingVO> bindings) implements EnvironmentAware {
    public TianjiReadOnlyDecisionToolProperties {
        concurrency = concurrency == 0 ? 4 : concurrency;
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        if (concurrency < 1 || concurrency > 8 || bindings.size() > 256 || enabled && bindings.isEmpty()) {
            throw invalid();
        }
    }
    @Override public void setEnvironment(Environment environment) {
        try {
            var binder = Binder.get(environment);
            // Boot 4.1.2 masks deep unknown leaves below list elements; independent roots keep both records closed.
            for (int index = 0; index < bindings.size(); index++) {
                String prefix = "stellar.tianji.agent.tools.read-only-decision.bindings[" + index + ']';
                var backend = binder.bind(prefix + ".backend", Bindable.of(TianjiFixedDecisionToolBindings.BackendVO.class),
                        new NoUnboundElementsBindHandler(BindHandler.DEFAULT)).orElseThrow(TianjiReadOnlyDecisionToolProperties::invalid);
                var tool = binder.bind(prefix + ".tool", Bindable.of(DefinitionResourceRef.class),
                        new NoUnboundElementsBindHandler(BindHandler.DEFAULT)).orElseThrow(TianjiReadOnlyDecisionToolProperties::invalid);
                if (!bindings.get(index).backend().equals(backend) || !bindings.get(index).tool().equals(tool)) throw invalid();
            }
        } catch (RuntimeException privateConfiguration) { throw invalid(); }
    }
    @Override public String toString() { return "TianjiReadOnlyDecisionToolProperties[redacted]"; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TIANJI_READ_ONLY_DECISION_TOOL_CONFIGURATION_INVALID"); }
}
