package top.egon.cola.platform.tianji.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "stellar.tianji.worker")
public record WorkerPollingProperties(boolean enabled, long tenantId, Integer concurrency, Duration pollInterval,
                                      Duration acknowledgementReserve, Duration maxClockSkew, Duration acknowledgementRetryDelay,
                                      Integer acknowledgementAttempts, Duration shutdownTimeout) {
    public WorkerPollingProperties {
        if (concurrency == null) concurrency = 1;
        if (pollInterval == null) pollInterval = Duration.ofSeconds(2);
        if (acknowledgementReserve == null) acknowledgementReserve = Duration.ofSeconds(3);
        if (maxClockSkew == null) maxClockSkew = Duration.ofSeconds(2);
        if (acknowledgementRetryDelay == null) acknowledgementRetryDelay = Duration.ofMillis(200);
        if (acknowledgementAttempts == null) acknowledgementAttempts = 2;
        if (shutdownTimeout == null) shutdownTimeout = Duration.ofSeconds(5);
        if (tenantId <= 0 || concurrency < 1 || concurrency > 32 || pollInterval.compareTo(Duration.ofMillis(100)) < 0
                || pollInterval.compareTo(Duration.ofMinutes(1)) > 0 || acknowledgementReserve.isNegative()
                || acknowledgementReserve.compareTo(Duration.ofMinutes(5)) > 0 || maxClockSkew.isNegative()
                || maxClockSkew.compareTo(Duration.ofMinutes(5)) > 0
                || acknowledgementReserve.plus(maxClockSkew).compareTo(Duration.ofSeconds(1)) < 0
                || acknowledgementRetryDelay.isNegative() || acknowledgementRetryDelay.compareTo(Duration.ofSeconds(5)) > 0
                || acknowledgementAttempts < 1 || acknowledgementAttempts > 3 || shutdownTimeout.isNegative()
                || shutdownTimeout.compareTo(Duration.ofSeconds(30)) > 0 || !wholeMillis(pollInterval)
                || !wholeMillis(acknowledgementReserve) || !wholeMillis(maxClockSkew) || !wholeMillis(acknowledgementRetryDelay)
                || !wholeMillis(shutdownTimeout)) throw new IllegalArgumentException("Worker polling configuration is invalid");
    }

    private static boolean wholeMillis(Duration value) { return value.toMillis() * 1_000_000L == value.toNanos(); }
}
