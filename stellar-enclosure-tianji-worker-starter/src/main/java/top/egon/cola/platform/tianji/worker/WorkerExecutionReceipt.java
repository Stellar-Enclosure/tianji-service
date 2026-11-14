package top.egon.cola.platform.tianji.worker;

import top.egon.cola.platform.tianji.contract.orchestration.WorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Carries either the existing v1 result or the v2 result pinned to a durable effect digest. */
public record WorkerExecutionReceipt(String handlerVersion, Instant committedAt, WorkerBusinessResultDTO result, boolean replayed,
                                     String effectFingerprint, String typedBusinessReceiptId, Map<String, Object> typedOutput,
                                     String resultSha256) {
    public WorkerExecutionReceipt(String handlerVersion, Instant committedAt, WorkerBusinessResultDTO result, boolean replayed) {
        this(handlerVersion, committedAt, Objects.requireNonNull(result, "result"), replayed, null, null, null, null);
    }

    public WorkerExecutionReceipt {
        committedAt = Objects.requireNonNull(committedAt, "committedAt");
        if (result != null) {
            if (handlerVersion == null || !handlerVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                    || effectFingerprint != null || typedBusinessReceiptId != null || typedOutput != null || resultSha256 != null) {
                throw invalid();
            }
        } else {
            if (handlerVersion == null || handlerVersion.isBlank() || handlerVersion.length() > 128
                    || handlerVersion.chars().anyMatch(Character::isISOControl)) throw invalid();
            typedOutput = WorkerDigests.immutableTypedObject(typedOutput);
            if (!AutomationWorkerBusinessResultDTO.resultSha256(effectFingerprint, typedBusinessReceiptId, typedOutput)
                    .equals(resultSha256)) throw invalid();
        }
    }

    public static WorkerExecutionReceipt fromAutomation(String handlerVersion, Instant committedAt, String effectFingerprint,
                                                         AutomationWorkerBusinessResultDTO result, String resultSha256, boolean replayed) {
        Objects.requireNonNull(result, "result");
        return new WorkerExecutionReceipt(handlerVersion, committedAt, null, replayed, effectFingerprint,
                result.getBusinessReceiptId(), result.getOutput(), resultSha256);
    }

    public String businessReceiptId() {
        return result == null ? typedBusinessReceiptId : result.businessReceiptId();
    }

    public AutomationWorkerBusinessResultDTO typedResult() {
        if (result != null) throw new IllegalStateException("Worker receipt has no typed result");
        return new AutomationWorkerBusinessResultDTO(typedBusinessReceiptId, typedOutput);
    }

    @Override
    public String toString() {
        return "WorkerExecutionReceipt[committedAt=" + committedAt + ", replayed=" + replayed + "]";
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Worker receipt is invalid");
    }
}
