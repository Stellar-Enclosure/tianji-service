package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Set;

@ConfigurationProperties(prefix = "stellar.tianji.agent.memory.retention")
public record TianjiMemoryRetentionProperties(Set<String> policyRefs, int batchSize, Duration interval, Duration timeout, Duration shutdownTimeout) {
    public TianjiMemoryRetentionProperties {
        policyRefs = policyRefs == null ? Set.of() : Set.copyOf(policyRefs); batchSize = batchSize == 0 ? 100 : batchSize;
        interval = interval == null ? Duration.ofMinutes(1) : interval; timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        shutdownTimeout = shutdownTimeout == null ? Duration.ofSeconds(10) : shutdownTimeout;
        if (policyRefs.isEmpty() || policyRefs.size() > 32 || batchSize < 1 || batchSize > 1000 || interval.compareTo(Duration.ofSeconds(1)) < 0 || interval.compareTo(Duration.ofDays(1)) > 0
                || timeout.compareTo(Duration.ofMillis(100)) < 0 || timeout.compareTo(Duration.ofSeconds(30)) > 0 || shutdownTimeout.isNegative() || shutdownTimeout.compareTo(Duration.ofSeconds(30)) > 0) throw new IllegalArgumentException("Memory retention policy or timing is invalid");
    }
}
