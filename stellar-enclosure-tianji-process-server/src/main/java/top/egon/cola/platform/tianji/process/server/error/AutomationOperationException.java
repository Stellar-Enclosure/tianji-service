package top.egon.cola.platform.tianji.process.server.error;

import java.util.Objects;

/** Carries a stable Automation v2 error without request or dependency data. */
public final class AutomationOperationException extends RuntimeException {
    private final Code code;

    public AutomationOperationException(Code code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public String stableCode() {
        return code.name();
    }

    public enum Code {
        TIANJI_DRAFT_VERSION_CONFLICT,
        TIANJI_DRAFT_STATE_CONFLICT,
        TIANJI_DRAFT_SOURCE_UNAVAILABLE,
        TIANJI_PROCESS_NOT_ACTIVATED,
        TIANJI_PROCESS_ACTIVATION_CONFLICT,
        TIANJI_EVENT_ROUTE_VERSION_CONFLICT,
        TIANJI_NODE_RETRY_BLOCKED,
        TIANJI_EVENT_CONFLICT,
        TIANJI_EVENT_ROUTE_CHANGED,
        TIANJI_REQUEST_TOO_LARGE
    }
}
