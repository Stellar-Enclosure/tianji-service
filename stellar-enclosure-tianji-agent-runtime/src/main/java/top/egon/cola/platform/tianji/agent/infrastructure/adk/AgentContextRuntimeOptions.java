package top.egon.cola.platform.tianji.agent.infrastructure.adk;

/** Request selection must leave durable preparation evidence; legacy execution keeps both capabilities disabled. */
public record AgentContextRuntimeOptions(boolean snapshots, boolean trimming) {
    public AgentContextRuntimeOptions {
        if (trimming && !snapshots) throw new IllegalArgumentException("Context trimming requires preparation snapshots");
    }
}
