package top.egon.cola.platform.tianji.worker;

import top.egon.cola.platform.tianji.contract.orchestration.WorkerBusinessResultDTO;

/**
 * Runs without a local transaction. READ_ONLY handlers must not write; EXTERNAL_IDEMPOTENT handlers
 * must pass the supplied external idempotency key to an operation that atomically deduplicates matching
 * requests and returns the same receipt and result on every retry.
 */
@FunctionalInterface
public interface NonTransactionalWorkerHandler {
    WorkerBusinessResultDTO execute(WorkerExecutionContext context, String externalIdempotencyKey);
}
