package top.egon.cola.platform.tianji.contract.orchestration;

/** RECEIVED confirms durable storage; only APPLIED confirms native process advancement. */
public enum AgentResultReceiptStateEnum { RECEIVED, APPLIED, IGNORED_LATE, DEAD }
