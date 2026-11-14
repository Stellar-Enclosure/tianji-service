package top.egon.cola.platform.tianji.worker;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static top.egon.cola.platform.tianji.worker.AutomationWorkerHandlerDescriptor.CAPABILITY_ID;
import static top.egon.cola.platform.tianji.worker.AutomationWorkerHandlerDescriptor.REGISTERED_ID;
import static top.egon.cola.platform.tianji.worker.AutomationWorkerHandlerDescriptor.SHA256;

public record WorkerEffectEvidenceVO(@NotNull Status status, @NotNull @Pattern(regexp = REGISTERED_ID) String effectKey,
                                     @NotNull @Pattern(regexp = SHA256) String effectFingerprint,
                                     @NotNull @Pattern(regexp = REGISTERED_ID) String targetId,
                                     @Positive @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) long dispatchGeneration,
                                     @NotNull @Pattern(regexp = CAPABILITY_ID) String authorityRef,
                                     @NotNull Instant observedAt, @NotNull Instant validUntil,
                                     @NotNull @Pattern(regexp = SHA256) String evidenceSha256,
                                     String businessReceiptId, Map<String, Object> output, String resultSha256) {
    public WorkerEffectEvidenceVO {
        if (status == null || !matches(effectKey, REGISTERED_ID) || !matches(effectFingerprint, SHA256)
                || !matches(targetId, REGISTERED_ID) || dispatchGeneration < 1
                || dispatchGeneration > ProcessTypedJson.V2_MAX_WIRE_INTEGER || !matches(authorityRef, CAPABILITY_ID)
                || !micros(observedAt) || !micros(validUntil) || !observedAt.isBefore(validUntil)
                || Duration.between(observedAt, validUntil).compareTo(Duration.ofSeconds(60)) > 0
                || !matches(evidenceSha256, SHA256)) throw invalid();
        if (status == Status.APPLIED) {
            output = WorkerDigests.immutableTypedObject(output);
            if (!AutomationWorkerBusinessResultDTO.resultSha256(effectFingerprint, businessReceiptId, output)
                    .equals(resultSha256)) throw invalid();
        } else if (businessReceiptId != null || output != null || resultSha256 != null) {
            throw invalid();
        }
    }

    private static boolean matches(String value, String regex) {
        return value != null && value.matches(regex);
    }

    private static boolean micros(Instant instant) {
        return instant != null && instant.getNano() % 1_000 == 0;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Worker effect evidence is invalid");
    }

    @Override
    public String toString() {
        return "WorkerEffectEvidenceVO[status=" + status + ", dispatchGeneration=" + dispatchGeneration + "]";
    }

    public enum Status {
        APPLIED, NOT_APPLIED_FINAL, PENDING, UNVERIFIABLE;

        @JsonValue
        public String code() {
            return name();
        }
    }
}
