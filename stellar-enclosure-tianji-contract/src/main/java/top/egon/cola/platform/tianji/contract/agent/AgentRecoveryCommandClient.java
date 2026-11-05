package top.egon.cola.platform.tianji.contract.agent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Protected USER commands for expired-checkpoint recovery and positive historical START evidence. */
public interface AgentRecoveryCommandClient {
    String CHECKPOINT_RESUME_PATH = "/api/tianji/v1/agent-checkpoints/resume";
    String HISTORICAL_START_RECEIPT_PATH = "/api/tianji/v1/agent-checkpoints/reconcile-start-receipt";

    /** Returns the original durable takeover acknowledgement. An uncertain response may be queried with the same command. */
    CheckpointResumeAcknowledgement resumeCheckpoint(CheckpointResumeCommand command);

    /** Requests a positive historical receipt. A missing result does not establish the absence of an effect. */
    HistoricalStartReceipt reconcileHistoricalStart(HistoricalStartReceiptCommand command);

    record CheckpointResumeCommand(String runId, String commandId, long expectedRevision) {
        public CheckpointResumeCommand {
            runId = ContractChecks.contextIdentifier(runId, "runId", 64);
            commandId = ContractChecks.contextIdentifier(commandId, "commandId", 128);
            if (expectedRevision < 0 || expectedRevision > Long.MAX_VALUE - 2) {
                throw new IllegalArgumentException("Checkpoint resume revision is invalid");
            }
        }

        @Override public String toString() { return "CheckpointResumeCommand[redacted]"; }
    }

    record CheckpointResumeAcknowledgement(String runId, AgentRunStatusEnum status, int attempt, long revision) {
        public CheckpointResumeAcknowledgement {
            runId = ContractChecks.contextIdentifier(runId, "runId", 64);
            if (status != AgentRunStatusEnum.RUNNING || attempt != 1 || revision < 2) {
                throw new IllegalArgumentException("Checkpoint resume acknowledgement is invalid");
            }
        }

        @Override public String toString() { return "CheckpointResumeAcknowledgement[redacted]"; }
    }

    record HistoricalStartReceiptCommand(String runId, UUID intentId) {
        public HistoricalStartReceiptCommand {
            runId = ContractChecks.contextIdentifier(runId, "runId", 64);
            Objects.requireNonNull(intentId, "intentId");
        }

        @Override public String toString() { return "HistoricalStartReceiptCommand[redacted]"; }
    }

    record HistoricalStartReceipt(UUID intentId, String dispatchRunId, String instanceId,
                                  Instant recordedAt, Instant observedAt) {
        public HistoricalStartReceipt {
            Objects.requireNonNull(intentId, "intentId");
            dispatchRunId = ContractChecks.contextIdentifier(dispatchRunId, "dispatchRunId", 64);
            instanceId = ContractChecks.contextIdentifier(instanceId, "instanceId", 255);
            Objects.requireNonNull(recordedAt, "recordedAt");
            Objects.requireNonNull(observedAt, "observedAt");
        }

        @Override public String toString() { return "HistoricalStartReceipt[redacted]"; }
    }
}
