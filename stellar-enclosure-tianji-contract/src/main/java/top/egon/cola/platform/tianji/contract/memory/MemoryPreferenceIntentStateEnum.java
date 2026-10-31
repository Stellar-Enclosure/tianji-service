package top.egon.cola.platform.tianji.contract.memory;

/** Processing status is reference metadata and grants no current authority to read input or mutate a fact. */
public enum MemoryPreferenceIntentStateEnum {
    RESERVED, READY, CLAIMED, APPLIED, SKIPPED, REJECTED, EXPIRED, CANCELLED, DEAD;

    public boolean terminal() { return this != RESERVED && this != READY && this != CLAIMED; }
}
