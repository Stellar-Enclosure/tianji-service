package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryCommandClient.HistoricalStartReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptDTO;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentHistoricalStartReceiptWitnessTest {
    @Test
    void requires_independent_process_recorded_time_and_exact_agent_observation() {
        UUID intent = UUID.fromString("6b9a690c-c1c2-4bb0-ae68-0d7e335034d4");
        Instant recorded = Instant.parse("2026-11-06T02:00:00.001Z");
        Instant observed = Instant.parse("2026-11-06T01:00:00Z");
        var process = new ProcessStartReceiptDTO("process-instance", ProcessStartReceiptDTO.StatusEnum.APPLIED, recorded);
        var receipt = new HistoricalStartReceipt(intent, "agent-run", process.instanceId(), recorded, observed);
        assertDoesNotThrow(() -> AgentHistoricalStartReceiptHttpPostgresTest.requireExactReceipt(receipt, process, receipt));

        var wrongRecorded = new HistoricalStartReceipt(intent, "agent-run", process.instanceId(), recorded.plusNanos(1000), observed);
        assertThrows(IllegalArgumentException.class, () ->
                AgentHistoricalStartReceiptHttpPostgresTest.requireExactReceipt(wrongRecorded, process, wrongRecorded));
        var wrongObserved = new HistoricalStartReceipt(intent, "agent-run", process.instanceId(), recorded, observed.plusNanos(1000));
        assertThrows(IllegalArgumentException.class, () ->
                AgentHistoricalStartReceiptHttpPostgresTest.requireExactReceipt(receipt, process, wrongObserved));
        var wrongInstance = new HistoricalStartReceipt(intent, "agent-run", "another-instance", recorded, observed);
        assertThrows(IllegalArgumentException.class, () ->
                AgentHistoricalStartReceiptHttpPostgresTest.requireExactReceipt(wrongInstance, process, wrongInstance));
    }
}
