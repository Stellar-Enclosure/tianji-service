package top.egon.cola.platform.tianji.process.server.error;

/** The requested process version no longer matches the fenced native state. */
public final class ProcessVersionConflictException extends RuntimeException {
    public ProcessVersionConflictException() {
        super("Process version or native state changed");
    }
}
