package top.egon.cola.platform.tianji.process.server.error;

/** The immutable released model does not permit process cancellation. */
public final class ProcessCancellationBlockedException extends RuntimeException {
    public ProcessCancellationBlockedException() {
        super("Process cancellation is blocked by its released model");
    }
}
