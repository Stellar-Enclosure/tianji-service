package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "stellar.tianji.agent.memory.extraction.background")
public record TianjiMemoryPreferenceBackgroundProperties(int batchSize, int maximumAttempts, Duration interval, Duration timeout,
        Duration leaseDuration, Duration retryDelay, Duration shutdownTimeout, Duration grantLifetime, Duration authorizationTimeout, int authorizationConcurrency) {
    public TianjiMemoryPreferenceBackgroundProperties {
        batchSize = batchSize == 0 ? 16 : batchSize; maximumAttempts = maximumAttempts == 0 ? 32 : maximumAttempts;
        interval = interval == null ? Duration.ofSeconds(5) : interval; timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        leaseDuration = leaseDuration == null ? Duration.ofSeconds(30) : leaseDuration; retryDelay = retryDelay == null ? Duration.ofSeconds(1) : retryDelay;
        shutdownTimeout = shutdownTimeout == null ? Duration.ofSeconds(10) : shutdownTimeout; grantLifetime = grantLifetime == null ? Duration.ofHours(1) : grantLifetime;
        authorizationTimeout = authorizationTimeout == null ? Duration.ofSeconds(2) : authorizationTimeout; authorizationConcurrency = authorizationConcurrency == 0 ? 2 : authorizationConcurrency;
        if (batchSize < 1 || batchSize > 100 || maximumAttempts < 1 || maximumAttempts > 100 || interval.compareTo(Duration.ofSeconds(1)) < 0 || interval.compareTo(Duration.ofDays(1)) > 0
                || timeout.compareTo(Duration.ofMillis(100)) < 0 || timeout.compareTo(Duration.ofSeconds(30)) > 0 || leaseDuration.compareTo(Duration.ofSeconds(1)) < 0 || leaseDuration.compareTo(Duration.ofSeconds(30)) > 0 || leaseDuration.getNano() % 1_000_000 != 0
                || retryDelay.compareTo(Duration.ofMillis(100)) < 0 || retryDelay.compareTo(Duration.ofHours(1)) > 0 || shutdownTimeout.isNegative() || shutdownTimeout.compareTo(Duration.ofSeconds(30)) > 0
                || grantLifetime.compareTo(Duration.ofSeconds(1)) < 0 || grantLifetime.compareTo(Duration.ofHours(8)) > 0 || authorizationTimeout.compareTo(Duration.ofMillis(100)) < 0
                || authorizationTimeout.compareTo(Duration.ofSeconds(30)) > 0 || authorizationTimeout.compareTo(timeout) > 0 || authorizationConcurrency < 1 || authorizationConcurrency > 8) throw new IllegalArgumentException("Memory preference background bound or timing is invalid");
    }
}
