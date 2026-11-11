package top.egon.cola.platform.tianji.process.server.acceptance;

import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Checks persisted observations from the separately owned Worker and Process stores. */
final class S12WorkerReceiptCorrelationEvidence {
    private S12WorkerReceiptCorrelationEvidence() { }

    static void verify(Scope scope, CompleteWorkerTaskCommand command, String effectReceiptId,
                       WorkerRow before, WorkerRow after, ProcessRow completed, long nativeCompletions) {
        require(scope != null && command != null && effectReceiptId != null && before != null
                && after != null && completed != null, "Both owned Worker receipt observations are required");
        require(command.output().isEmpty() && effectReceiptId.equals(command.businessReceiptId()),
                "Worker acknowledgement must match the original fixture effect");

        String idempotencyKey = FlowableWorkerService.workerOccurrenceKey(scope.tenant(), command.taskId());
        require(scope.source().equals(before.source()) && scope.tenant() == before.tenant()
                        && idempotencyKey.equals(before.idempotencyKey())
                        && command.taskId().equals(before.task()) && command.activityOccurrenceId().equals(before.occurrence())
                        && "reserveBusiness".equals(before.topic()) && "1".equals(before.schema())
                        && "TRANSACTIONAL_RECEIPT".equals(before.effect()) && before.handlerVersion() != null
                        && before.committedAt() != null && hash(before.inputSha256()),
                "Worker business receipt must match the original owned occurrence");
        require(before.equals(after), "Worker business receipt must remain unchanged across Process acknowledgement replay");

        require(scope.tenant() == completed.tenant() && scope.instance().equals(completed.instance())
                        && scope.workerIssuer().equals(completed.workerIssuer()) && scope.workerSubject().equals(completed.workerSubject())
                        && idempotencyKey.equals(completed.idempotencyKey()) && command.taskId().equals(completed.task())
                        && command.activityOccurrenceId().equals(completed.occurrence()) && "reserveBusiness".equals(completed.topic())
                        && "1".equals(completed.schema()) && "COMPLETED".equals(completed.state()) && completed.decidedAt() != null,
                "Process receipt must match the original owned Worker occurrence");

        // The two stores use the same length-delimited encoding with different domain and fields.
        String workerDigest = ProcessRequestDigest.hash(List.of("worker-business-result-v1", effectReceiptId), Map.of());
        require(effectReceiptId.equals(before.businessReceiptId()) && before.emptyOutput()
                        && workerDigest.equals(before.resultSha256()),
                "Worker business receipt digest must match its original domain");
        String processDigest = ProcessRequestDigest.hash(List.of("worker-complete-v1", command.taskId(),
                command.activityOccurrenceId(), effectReceiptId), command.output());
        require(effectReceiptId.equals(completed.businessReceiptId()) && processDigest.equals(completed.resultSha256()),
                "Process completion digest must match its original domain");
        require(nativeCompletions == 1, "Exactly one native Worker activity must have completed");
    }

    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }

    record Scope(long tenant, String source, String instance, String workerIssuer, String workerSubject) {
        Scope {
            if (tenant <= 0) throw new IllegalArgumentException("Invalid Worker receipt evidence scope");
            Objects.requireNonNull(source);
            Objects.requireNonNull(instance);
            Objects.requireNonNull(workerIssuer);
            Objects.requireNonNull(workerSubject);
        }

        @Override public String toString() { return "S12 Worker receipt scope"; }
    }

    record WorkerRow(String source, long tenant, String idempotencyKey, String task, String occurrence, String topic,
                     String schema, String effect, String handlerVersion, String inputSha256, String resultSha256,
                     String businessReceiptId, boolean emptyOutput, Instant committedAt) {
        @Override public String toString() { return "S12 Worker business receipt"; }
    }

    record ProcessRow(long tenant, String workerIssuer, String workerSubject, String instance, String task,
                      String occurrence, String idempotencyKey, String topic, String schema, String state,
                      String businessReceiptId, String resultSha256, Instant decidedAt) {
        @Override public String toString() { return "S12 Process Worker completion receipt"; }
    }
}
