package top.egon.cola.platform.tianji.process.server.error;

public final class ProcessDecisionFailedException extends IllegalStateException {
    public ProcessDecisionFailedException() { super("Decision evaluation failed"); }
}
