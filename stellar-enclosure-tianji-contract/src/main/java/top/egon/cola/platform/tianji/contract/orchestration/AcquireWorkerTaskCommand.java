package top.egon.cola.platform.tianji.contract.orchestration;

/** Requests at most one task. A lost response may leave a lease until it expires. */
public record AcquireWorkerTaskCommand(String topic, String schemaVersion) {
    public AcquireWorkerTaskCommand {
        topic = OrchestrationChecks.definition(topic);
        schemaVersion = OrchestrationChecks.release(schemaVersion);
        if (topic.length() + schemaVersion.length() + 1 > 255) throw OrchestrationChecks.invalid();
    }
}
