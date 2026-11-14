package top.egon.cola.platform.tianji.worker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Map;

import static top.egon.cola.platform.tianji.worker.AutomationWorkerHandlerDescriptor.REGISTERED_ID;

public record AutomationWorkerExecutionContextVO(@NotBlank @Pattern(regexp = REGISTERED_ID) String sourceId,
                                                 @Positive long tenantId,
                                                 @NotBlank @Pattern(regexp = REGISTERED_ID) String effectKey,
                                                 @NotNull Map<String, Object> input,
                                                 @NotNull Instant deadlineAt,
                                                 @Positive long dispatchGeneration) {
    public AutomationWorkerExecutionContextVO(String sourceId, long tenantId, String effectKey,
                                              Map<String, Object> input, Instant deadlineAt) {
        this(sourceId, tenantId, effectKey, input, deadlineAt, 1);
    }

    public AutomationWorkerExecutionContextVO {
        if (sourceId == null || !sourceId.matches(REGISTERED_ID) || tenantId <= 0 || effectKey == null
                || !effectKey.matches(REGISTERED_ID) || input == null || deadlineAt == null || dispatchGeneration < 1) {
            throw new IllegalArgumentException("Worker automation execution context is invalid");
        }
        input = WorkerDigests.immutableTypedObject(input);
    }

    AutomationWorkerExecutionContextVO forGeneration(long generation) {
        return new AutomationWorkerExecutionContextVO(sourceId, tenantId, effectKey, input, deadlineAt, generation);
    }

    @Override
    public String toString() {
        return "AutomationWorkerExecutionContextVO";
    }
}
