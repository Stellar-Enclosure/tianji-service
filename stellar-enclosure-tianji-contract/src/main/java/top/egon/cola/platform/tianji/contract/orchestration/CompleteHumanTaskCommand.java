package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Map;

/** Only declared form keys are submitted; the service owns their released process-variable mapping. */
public record CompleteHumanTaskCommand(String commandId, String taskId, String expectedVersion, Map<String, Object> form) {
    public CompleteHumanTaskCommand {
        commandId = OrchestrationChecks.command(commandId); taskId = OrchestrationChecks.id(taskId, 255); expectedVersion = OrchestrationChecks.hash(expectedVersion);
        form = OrchestrationChecks.values(form);
    }
    @Override public String toString() { return "CompleteHumanTaskCommand[taskId=" + taskId + ", form=<redacted>]"; }
}
