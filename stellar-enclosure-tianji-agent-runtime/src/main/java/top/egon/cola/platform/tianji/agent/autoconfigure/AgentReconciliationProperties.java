package top.egon.cola.platform.tianji.agent.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "stellar.tianji.agent.reconciliation")
public class AgentReconciliationProperties {
    private int batchSize = 100;
    private Duration interval = Duration.ofSeconds(5);
    private Duration shutdownTimeout = Duration.ofSeconds(5);

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public Duration getInterval() { return interval; }
    public void setInterval(Duration interval) { this.interval = interval; }
    public Duration getShutdownTimeout() { return shutdownTimeout; }
    public void setShutdownTimeout(Duration shutdownTimeout) { this.shutdownTimeout = shutdownTimeout; }
}
