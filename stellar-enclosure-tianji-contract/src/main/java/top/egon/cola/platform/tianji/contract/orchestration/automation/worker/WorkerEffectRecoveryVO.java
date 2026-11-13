package top.egon.cola.platform.tianji.contract.orchestration.automation.worker;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record WorkerEffectRecoveryVO(
        @NotNull Mode mode,
        @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) long observedGeneration,
        @NotNull Instant observedAt,
        @NotNull Instant validUntil,
        @NotNull String authorityRef,
        @NotNull String evidenceSha256,
        @JsonProperty(required = true) String businessReceiptId,
        @JsonProperty(required = true) String resultSha256) {

    public WorkerEffectRecoveryVO {
        if (mode == null || observedGeneration < 1 || observedGeneration > ProcessTypedJson.V2_MAX_WIRE_INTEGER
                || !utcMicros(observedAt) || !utcMicros(validUntil) || !validUntil.isAfter(observedAt)
                || Duration.between(observedAt, validUntil).compareTo(Duration.ofSeconds(60)) > 0
                || authorityRef == null || !authorityRef.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}")
                || !AutomationWorkerBusinessResultDTO.isHash(evidenceSha256)) {
            throw new IllegalArgumentException("Invalid worker recovery evidence");
        }
        if (mode == Mode.REPLAY_APPLIED) {
            AutomationWorkerBusinessResultDTO.requireBusinessReceiptId(businessReceiptId);
            if (!AutomationWorkerBusinessResultDTO.isHash(resultSha256)) {
                throw new IllegalArgumentException("Missing applied worker recovery result");
            }
        } else if (businessReceiptId != null || resultSha256 != null) {
            throw new IllegalArgumentException("Unexpected worker recovery result");
        }
    }

    Map<String, Object> toWire() {
        var wire = new LinkedHashMap<String, Object>();
        wire.put("mode", mode.wire());
        wire.put("observedGeneration", observedGeneration);
        wire.put("observedAt", observedAt.toString());
        wire.put("validUntil", validUntil.toString());
        wire.put("authorityRef", authorityRef);
        wire.put("evidenceSha256", evidenceSha256);
        wire.put("businessReceiptId", businessReceiptId);
        wire.put("resultSha256", resultSha256);
        return wire;
    }

    static boolean utcMicros(Instant value) {
        return value != null && value.getNano() % 1_000 == 0;
    }

    @Override
    public String toString() {
        return "WorkerEffectRecoveryVO[mode=" + mode + ", observedGeneration=" + observedGeneration + "]";
    }

    public enum Mode {
        REPLAY_APPLIED, RETRY_NOT_APPLIED;

        @JsonValue
        public String wire() { return name(); }
    }
}
