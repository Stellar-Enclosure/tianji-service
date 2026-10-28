package top.egon.cola.platform.tianji.contract.agent;

/** Values are serialized by name, never by ordinal. */
public enum AgentRunStatusEnum {
    CREATED, QUEUED, RUNNING, WAITING, SUSPENDED, COMPLETED, FAILED, CANCELLED, TIMED_OUT
}
