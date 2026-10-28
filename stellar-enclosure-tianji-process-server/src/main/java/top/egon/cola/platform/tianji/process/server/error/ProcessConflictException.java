package top.egon.cola.platform.tianji.process.server.error;

public final class ProcessConflictException extends IllegalStateException {
    public ProcessConflictException() { super("Process command conflicts with an existing request"); }
}
