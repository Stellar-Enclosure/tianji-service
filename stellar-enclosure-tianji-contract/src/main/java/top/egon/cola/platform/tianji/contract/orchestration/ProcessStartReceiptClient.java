package top.egon.cola.platform.tianji.contract.orchestration;

/** Reads positive evidence for an exact START under the transport's current Process owner. */
public interface ProcessStartReceiptClient {
    String READ_SCOPE = ProcessControlClient.READ_SCOPE;
    String START_RECEIPT_RESOURCE_KEY = "tianji.api.process.startReceipt.get";
    String START_RECEIPT_PATH = "/api/tianji/v1/process-start-receipts";

    /** A lookup without an applied receipt is inconclusive and cannot authorize a new START. */
    ProcessStartReceiptDTO getStartReceipt(GetProcessStartReceiptQuery query);
}
