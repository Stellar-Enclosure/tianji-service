package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Objects;
import java.util.Set;

public record ProcessWorkerFailureDTO(String jobId, String activityId, boolean nativeDeadletter,
                                      StatusEnum status, ReasonEnum reason, ReportedFailureEnum reportedFailure,
                                      String expectedVersion) {
    private static final Set<ReasonEnum> HISTORICAL_REASONS = Set.of(ReasonEnum.NO_NATIVE_DEADLETTER,
            ReasonEnum.UNKNOWN_OUTCOME, ReasonEnum.BUSINESS_REJECTED, ReasonEnum.HISTORY_INCOMPLETE);

    public ProcessWorkerFailureDTO {
        jobId = OrchestrationChecks.id(jobId, 255);
        if (activityId != null) activityId = OrchestrationChecks.id(activityId, 128);
        status = Objects.requireNonNull(status, "status");
        reason = Objects.requireNonNull(reason, "reason");
        reportedFailure = Objects.requireNonNull(reportedFailure, "reportedFailure");
        if (reason == ReasonEnum.UNKNOWN_OUTCOME && reportedFailure != ReportedFailureEnum.UNKNOWN
                || reason == ReasonEnum.BUSINESS_REJECTED && reportedFailure != ReportedFailureEnum.BUSINESS_REJECTED) {
            throw OrchestrationChecks.invalid();
        }
        switch (status) {
            case RETRY_AVAILABLE -> {
                if (!nativeDeadletter || activityId == null || reason != ReasonEnum.NONE
                        || reportedFailure != ReportedFailureEnum.RETRYABLE) throw OrchestrationChecks.invalid();
                expectedVersion = OrchestrationChecks.hash(expectedVersion);
            }
            case MANUAL_REVIEW -> {
                if (!nativeDeadletter || expectedVersion != null || reason == ReasonEnum.NONE
                        || reason == ReasonEnum.NO_NATIVE_DEADLETTER) throw OrchestrationChecks.invalid();
            }
            case HISTORICAL_FAILURE -> {
                if (nativeDeadletter || expectedVersion != null || !HISTORICAL_REASONS.contains(reason)) {
                    throw OrchestrationChecks.invalid();
                }
            }
        }
    }

    public enum StatusEnum {
        RETRY_AVAILABLE,
        MANUAL_REVIEW,
        HISTORICAL_FAILURE
    }

    public enum ReasonEnum {
        NONE,
        NO_NATIVE_DEADLETTER,
        UNKNOWN_OUTCOME,
        BUSINESS_REJECTED,
        RESTORE_LIMIT_REACHED,
        UNSUPPORTED_NATIVE_JOB,
        PROCESS_INACTIVE,
        EVIDENCE_CONFLICT,
        ACTIVE_LEASE,
        BINDING_UNVERIFIED,
        HISTORY_INCOMPLETE,
        UNSUPPORTED_EFFECT
    }

    public enum ReportedFailureEnum {
        NONE,
        RETRYABLE,
        BUSINESS_REJECTED,
        UNKNOWN,
        UNVERIFIED
    }
}
