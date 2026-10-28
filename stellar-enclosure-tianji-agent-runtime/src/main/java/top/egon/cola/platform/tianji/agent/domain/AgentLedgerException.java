package top.egon.cola.platform.tianji.agent.domain;

import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.util.Objects;

/** Safe error code for ledger operations; transport mapping is the caller's responsibility. */
public class AgentLedgerException extends RuntimeException {
    private final AgentErrorCodeEnum code;

    public AgentLedgerException(AgentErrorCodeEnum code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public AgentErrorCodeEnum code() {
        return code;
    }
}
