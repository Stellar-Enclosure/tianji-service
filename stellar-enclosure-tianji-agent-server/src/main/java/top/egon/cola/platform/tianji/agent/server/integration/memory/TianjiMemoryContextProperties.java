package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryContextBinding;

import java.util.List;

@ConfigurationProperties(prefix = "stellar.tianji.agent.context.memory")
public record TianjiMemoryContextProperties(List<MemoryContextBinding> bindings, int maxConcurrent) {
    public TianjiMemoryContextProperties {
        bindings = MemoryContextBinding.validate(bindings); maxConcurrent = maxConcurrent == 0 ? 4 : maxConcurrent;
        if (maxConcurrent < 1 || maxConcurrent > 16) throw new IllegalArgumentException("Memory context capacity is invalid");
    }
}
