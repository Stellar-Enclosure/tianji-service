package top.egon.cola.platform.tianji.process.server.error;

public final class ProcessTenantChangedException extends IllegalStateException {
    public ProcessTenantChangedException() { super("Expected tenant does not match the verified identity"); }
}
