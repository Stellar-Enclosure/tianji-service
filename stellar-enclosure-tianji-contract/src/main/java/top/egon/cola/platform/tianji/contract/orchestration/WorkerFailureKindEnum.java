package top.egon.cola.platform.tianji.contract.orchestration;

/** UNKNOWN requires manual reconciliation instead of automatically repeating a side effect. */
public enum WorkerFailureKindEnum { RETRYABLE, BUSINESS_REJECTED, UNKNOWN }
