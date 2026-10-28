package top.egon.cola.platform.tianji.client;

import java.time.Duration;

/** Safe transport error; raw response bodies and access tokens are not retained. */
public final class ProcessClientException extends RuntimeException {
    private final int httpStatus;
    private final String code;
    private final String correlationId;
    private final Duration retryAfter;

    public ProcessClientException(int httpStatus, String code, String correlationId, Duration retryAfter) {
        super("Process service request failed (status=" + httpStatus + ", code=" + safeCode(code) + ")");
        this.httpStatus = httpStatus;
        this.code = safeCode(code);
        this.correlationId = correlationId != null && correlationId.matches("[A-Za-z0-9._:-]{1,128}") ? correlationId : null;
        this.retryAfter = retryAfter;
    }

    public int httpStatus() { return httpStatus; }
    public String code() { return code; }
    public String correlationId() { return correlationId; }
    public Duration retryAfter() { return retryAfter; }

    private static String safeCode(String value) {
        return value != null && value.matches("[A-Z0-9_]{1,80}") ? value : "TIANJI_DEPENDENCY_UNAVAILABLE";
    }
}
