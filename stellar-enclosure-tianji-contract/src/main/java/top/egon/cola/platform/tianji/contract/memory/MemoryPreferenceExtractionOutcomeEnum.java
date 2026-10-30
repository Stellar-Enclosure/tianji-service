package top.egon.cola.platform.tianji.contract.memory;

/** ELIGIBLE means candidate receipts were obtained; it never denotes fact confirmation or a batch transaction. */
public enum MemoryPreferenceExtractionOutcomeEnum {
    ELIGIBLE, RUN_NOT_COMPLETED, SOURCE_NOT_APPROVED, SOURCE_NOT_USER_INPUT, SOURCE_LIMIT,
    QUOTED_SOURCE, UNSUPPORTED_PREFERENCE, CONFLICTING_PREFERENCE, BATCH_LIMIT, NO_DECLARATION
}
