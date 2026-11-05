package top.egon.cola.platform.tianji.contract.orchestration;

/** Locates one owned START receipt only when it matches the independently expected request digest. */
public record GetProcessStartReceiptQuery(String commandId, String requestSha256) {
    public GetProcessStartReceiptQuery {
        commandId = OrchestrationChecks.command(commandId);
        if (requestSha256 == null || !requestSha256.matches("[0-9a-f]{64}")) throw OrchestrationChecks.invalid();
    }
}
