package top.egon.cola.platform.tianji.contract.orchestration.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Receives producer events and reads their independent durable decisions. */
public interface ProcessEventClient {
    ProcessEventReceiptDTO receive(@NotNull @Valid ProcessEventCommand command);

    ProcessEventReceiptDTO getReceipt(@NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}") String receiptId);
}
