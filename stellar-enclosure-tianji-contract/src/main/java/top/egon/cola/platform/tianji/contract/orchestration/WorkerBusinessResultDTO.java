package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Map;

/** A committed business receipt and its bounded output for later acknowledgement. */
public record WorkerBusinessResultDTO(String businessReceiptId, Map<String, Object> output) {
    public WorkerBusinessResultDTO {
        businessReceiptId = OrchestrationChecks.id(businessReceiptId, 255);
        output = OrchestrationChecks.values(output);
    }

    @Override public String toString() {
        return "WorkerBusinessResultDTO[businessReceiptId=" + businessReceiptId + ", output=<redacted>]";
    }
}
