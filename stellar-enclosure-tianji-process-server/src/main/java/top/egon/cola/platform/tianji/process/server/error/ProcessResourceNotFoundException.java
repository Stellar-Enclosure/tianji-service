package top.egon.cola.platform.tianji.process.server.error;

/** Missing and out-of-scope resources have the same externally observable result. */
public final class ProcessResourceNotFoundException extends IllegalArgumentException {
    public ProcessResourceNotFoundException() { super("Process resource is unavailable"); }
}
