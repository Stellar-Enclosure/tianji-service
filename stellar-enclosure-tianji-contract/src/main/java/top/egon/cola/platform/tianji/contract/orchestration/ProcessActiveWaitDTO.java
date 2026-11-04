package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Objects;

public record ProcessActiveWaitDTO(String activityKey, ProcessActiveWaitKindEnum kind, int occurrences) {
    public ProcessActiveWaitDTO {
        activityKey = OrchestrationChecks.definition(activityKey);
        kind = Objects.requireNonNull(kind, "kind");
        if (occurrences < 1 || occurrences > 256) throw OrchestrationChecks.invalid();
    }
}
