package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;

/** Durable START receipt evidence, independent of the current process lifecycle. */
public record ProcessStartReceiptDTO(String instanceId, StatusEnum status, Instant recordedAt) {
    private static final Instant FIRST = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant LAST = Instant.parse("9999-12-31T23:59:59.999999Z");

    public ProcessStartReceiptDTO {
        instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
        if (status == null || recordedAt == null || recordedAt.isBefore(FIRST) || recordedAt.isAfter(LAST)
                || recordedAt.getNano() % 1_000 != 0) throw OrchestrationChecks.invalid();
    }

    public enum StatusEnum { APPLIED }
}
