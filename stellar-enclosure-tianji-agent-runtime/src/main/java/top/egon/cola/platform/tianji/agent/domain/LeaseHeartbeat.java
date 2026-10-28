package top.egon.cola.platform.tianji.agent.domain;

/** Result of checking a claimed run against its persisted state. */
public enum LeaseHeartbeat {
    ACTIVE, CANCEL_REQUESTED, DEADLINE_EXPIRED, LEASE_LOST
}
