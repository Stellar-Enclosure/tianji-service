package top.egon.cola.platform.tianji.process.server.worker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static top.egon.cola.platform.tianji.process.server.worker.WorkerEffectRecoveryQuery.NODE_REF;
import static top.egon.cola.platform.tianji.process.server.worker.WorkerEffectRecoveryQuery.REGISTERED_ID;
import static top.egon.cola.platform.tianji.process.server.worker.WorkerEffectRecoveryQuery.SHA256;
import static top.egon.cola.platform.tianji.process.server.worker.WorkerEffectRecoveryQuery.matches;
import static top.egon.cola.platform.tianji.process.server.worker.WorkerEffectRecoveryQuery.micros;

/** A closed observation returned by the trusted source. Only a matching, live proof can be frozen. */
public record WorkerEffectRecoveryProofVO(
        @NotNull Status status,
        @NotNull @Pattern(regexp = SHA256) String querySha256,
        @Pattern(regexp = NODE_REF) String authorityRef,
        @NotNull @Pattern(regexp = REGISTERED_ID) String effectKey,
        @NotNull @Pattern(regexp = SHA256) String effectFingerprint,
        @Pattern(regexp = REGISTERED_ID) String targetId,
        Long observedGeneration,
        Instant observedAt,
        Instant validUntil,
        @Pattern(regexp = SHA256) String evidenceSha256,
        String businessReceiptId,
        Map<String, Object> output,
        @Pattern(regexp = SHA256) String resultSha256) {

    private static final int OUTPUT_LIMIT = 64 * 1024;
    private static final Duration MAX_WINDOW = Duration.ofSeconds(60);

    public WorkerEffectRecoveryProofVO {
        if (status == null || !matches(querySha256, SHA256) || !matches(effectKey, REGISTERED_ID)
                || !matches(effectFingerprint, SHA256) || targetId != null && !matches(targetId, REGISTERED_ID)) throw invalid();
        boolean actionable = status == Status.APPLIED || status == Status.NOT_APPLIED_FINAL;
        if (actionable) {
            if (!matches(authorityRef, NODE_REF) || observedGeneration == null || observedGeneration < 1
                    || observedGeneration > ProcessTypedJson.V2_MAX_WIRE_INTEGER || !micros(observedAt)
                    || !micros(validUntil) || !validUntil.isAfter(observedAt)
                    || Duration.between(observedAt, validUntil).compareTo(MAX_WINDOW) > 0
                    || !matches(evidenceSha256, SHA256)) throw invalid();
        } else if (authorityRef != null || observedGeneration != null || observedAt != null || validUntil != null
                || evidenceSha256 != null) {
            throw invalid();
        }
        if (status == Status.APPLIED) {
            if (output == null) throw invalid();
            output = freezeOutput(output);
            if (!AutomationWorkerBusinessResultDTO.resultSha256(effectFingerprint, businessReceiptId, output).equals(resultSha256)) {
                throw invalid();
            }
        } else if (businessReceiptId != null || output != null || resultSha256 != null) {
            throw invalid();
        }
    }

    public WorkerEffectRecoveryVO freeze(@NotNull @Valid WorkerEffectRecoveryQuery query, @NotNull ProcessSchemaVO outputSchema,
                                         @NotNull WorkerEffectSafetyEnum effectMode, @NotNull Instant now) {
        boolean external = effectMode == WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT;
        boolean transactional = effectMode == WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT;
        if (!querySha256.equals(query.sha256()) || !effectKey.equals(query.getEffectKey()) || !effectFingerprint.equals(query.getEffectFingerprint())
                || !Objects.equals(targetId, query.getTargetId()) || !(external || transactional)
                || status != Status.APPLIED && (status != Status.NOT_APPLIED_FINAL || !external)
                || now.isBefore(observedAt) || !now.isBefore(validUntil) || validUntil.isAfter(query.getNodeDeadlineAt())
                || !ProcessNodeLifecycleListener.schemaSha256(outputSchema).equals(query.getOutputSchemaSha256())) {
            throw blocked();
        }
        if (status == Status.APPLIED) {
            try {
                Object normalized = outputSchema.normalizeValue(output);
                if (!Arrays.equals(ProcessTypedJson.canonicalBytes(output, OUTPUT_LIMIT),
                        ProcessTypedJson.canonicalBytes(normalized, OUTPUT_LIMIT))) throw blocked();
            } catch (IllegalArgumentException invalidOutput) {
                throw blocked();
            }
        }
        var mode = status == Status.APPLIED ? WorkerEffectRecoveryVO.Mode.REPLAY_APPLIED : WorkerEffectRecoveryVO.Mode.RETRY_NOT_APPLIED;
        return new WorkerEffectRecoveryVO(mode, observedGeneration, observedAt, validUntil, authorityRef, evidenceSha256,
                businessReceiptId, resultSha256);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> freezeOutput(Map<String, Object> source) {
        ProcessTypedJson.canonicalBytes(source, OUTPUT_LIMIT);
        ProcessTypedJson.validateTypedIntegerLeaves(source);
        return (Map<String, Object>) freezeValue(source);
    }

    private static Object freezeValue(Object value) {
        if (value instanceof Map<?, ?> object) {
            var frozen = new LinkedHashMap<>();
            object.forEach((key, child) -> frozen.put(key, freezeValue(child)));
            return Collections.unmodifiableMap(frozen);
        }
        if (value instanceof List<?> array) {
            var frozen = new ArrayList<>(array.size());
            array.forEach(child -> frozen.add(freezeValue(child)));
            return Collections.unmodifiableList(frozen);
        }
        return value;
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid Worker recovery proof"); }

    private static AutomationOperationException blocked() {
        return new AutomationOperationException(AutomationOperationException.Code.TIANJI_NODE_RETRY_BLOCKED);
    }

    @Override
    public String toString() { return "WorkerEffectRecoveryProofVO[status=" + status + "]"; }

    public enum Status { APPLIED, NOT_APPLIED_FINAL, PENDING, UNVERIFIABLE }

}
