package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.process.server.acceptance.S12ModelUsageEvidence.BudgetDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.S12ModelUsageEvidence.FactDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.S12ModelUsageEvidence.UsageDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class S12ModelUsageEvidenceTest {
    private static final long TENANT = 71;
    private static final String RUN = "linked-run";
    private static final String RESERVATION = "model-call";
    private static final Instant CREATED = Instant.parse("2026-11-06T00:00:00Z");
    private static final Instant MEASURED = CREATED.plusSeconds(1);
    private static final BigDecimal COST = new BigDecimal("0.05000000");
    private static final BudgetDTO BUDGET = new BudgetDTO(1, "MODEL", "SETTLED", 10L, 5L, COST, MEASURED);
    private static final String PRICE = OrchestrationRequestDigests.hash(List.of("TIANJI_MODEL_USAGE_PRICE_V1",
            "expense-fixture-route", "expense-fixture-model", "USD", "3000", "4000", "PER_MILLION_CEILING_8"), Map.of());

    @Test
    void accepts_the_single_exact_priced_result_and_original_settlement() {
        assertDoesNotThrow(() -> verify(fact(RUN, TENANT, BUDGET, COST, "PRICED", PRICE, MEASURED)));
    }

    @Test
    void rejects_a_priced_result_from_another_bridge_run() {
        var foreign = fact("foreign-run", TENANT, BUDGET, COST, "PRICED", PRICE, MEASURED);
        assertThrows(AssertionError.class, () -> verify(foreign));
    }

    @Test
    void rejects_missing_duplicate_unpriced_or_modified_model_facts() {
        var valid = fact(RUN, TENANT, BUDGET, COST, "PRICED", PRICE, MEASURED);
        assertThrows(AssertionError.class, () -> S12ModelUsageEvidence.verify(TENANT, RUN, List.of()));
        assertThrows(AssertionError.class, () -> S12ModelUsageEvidence.verify(TENANT, RUN, List.of(valid, valid)));
        assertThrows(AssertionError.class, () -> verify(fact(RUN, TENANT, BUDGET, null, "UNPRICED", PRICE, MEASURED)));
        assertThrows(AssertionError.class, () -> verify(fact(RUN, TENANT, BUDGET, COST, "PRICED", "0".repeat(64), MEASURED)));
        assertThrows(AssertionError.class, () -> verify(fact(RUN, TENANT + 1, BUDGET, COST, "PRICED", PRICE, MEASURED)));
    }

    @Test
    void rejects_budget_or_measurement_mismatch() {
        var unsettled = new BudgetDTO(1, "MODEL", "RESERVED", 10L, 5L, COST, MEASURED);
        assertThrows(AssertionError.class, () -> verify(fact(RUN, TENANT, unsettled, COST, "PRICED", PRICE, MEASURED)));
        assertThrows(AssertionError.class, () -> verify(fact(RUN, TENANT, BUDGET, BigDecimal.ZERO, "PRICED", PRICE, MEASURED)));
        assertThrows(AssertionError.class, () -> verify(fact(RUN, TENANT, BUDGET, COST, "PRICED", PRICE, MEASURED.plusSeconds(1))));
    }

    private static void verify(FactDTO fact) {
        S12ModelUsageEvidence.verify(TENANT, RUN, List.of(fact));
    }

    private static FactDTO fact(String run, long tenant, BudgetDTO budget, BigDecimal cost, String state, String price,
                                Instant measuredAt) {
        String digest = OrchestrationRequestDigests.hash(List.of("TIANJI_MODEL_USAGE_FINAL_V1", Long.toString(tenant), run,
                RESERVATION, "1", "expense-fixture-route", "expense-fixture-model", "PRICED", "10", "5",
                "1", PRICE, "1", "0.05", "0"), Map.of());
        var usage = new UsageDTO(1, "expense-fixture-route", "expense-fixture-model", price, "USD", new BigDecimal("3000.00000000"),
                new BigDecimal("4000.00000000"), state, 10L, 5L, cost, null, digest, CREATED, measuredAt);
        return new FactDTO(run, RESERVATION, budget, usage);
    }
}
