package top.egon.cola.platform.tianji.process.server.acceptance;

import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

final class S12ModelUsageEvidence {
    static final String ROUTE = "expense-fixture-route";
    private static final String MODEL = "expense-fixture-model";
    private static final String CURRENCY = "USD";
    private static final BigDecimal INPUT_RATE = new BigDecimal("3000");
    private static final BigDecimal OUTPUT_RATE = new BigDecimal("4000");
    private static final BigDecimal COST = new BigDecimal("0.05");
    private static final String PRICE_VERSION = OrchestrationRequestDigests.hash(List.of("TIANJI_MODEL_USAGE_PRICE_V1",
            ROUTE, MODEL, CURRENCY, INPUT_RATE.toPlainString(), OUTPUT_RATE.toPlainString(), "PER_MILLION_CEILING_8"), Map.of());

    private S12ModelUsageEvidence() { }

    static FactDTO read(ResultSet rs, int row) throws SQLException {
        var budget = new BudgetDTO(rs.getLong("lease_epoch"), rs.getString("kind"), rs.getString("budget_state"),
                rs.getObject("budget_input", Long.class), rs.getObject("budget_output", Long.class),
                rs.getBigDecimal("budget_cost"), instant(rs, "settled_at"));
        var usage = new UsageDTO(rs.getLong("usage_epoch"), rs.getString("model_route_ref"), rs.getString("model_name"),
                rs.getString("price_version_ref"), rs.getString("currency"), rs.getBigDecimal("input_rate_per_million"),
                rs.getBigDecimal("output_rate_per_million"), rs.getString("usage_state"),
                rs.getObject("actual_input_tokens", Long.class), rs.getObject("actual_output_tokens", Long.class),
                rs.getBigDecimal("actual_cost"), rs.getString("safe_reason"), rs.getString("final_sha256"),
                instant(rs, "created_at"), instant(rs, "measured_at"));
        return new FactDTO(rs.getString("run_id"), rs.getString("reservation_id"), budget, usage);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static void verify(long tenant, String expectedRunId, List<FactDTO> facts) {
        require(facts.size() == 1, "Exactly one MODEL reservation for the owned linked Agent run");
        var fact = facts.getFirst();
        require(expectedRunId.equals(fact.runId()) && fact.reservationId() != null && !fact.reservationId().isBlank(),
                "Model settlement belongs to the exact linked Agent run");
        var budget = fact.budget();
        var usage = fact.usage();
        require(budget.epoch() > 0 && budget.epoch() == usage.epoch() && "MODEL".equals(budget.kind())
                        && "SETTLED".equals(budget.state()) && Long.valueOf(10).equals(budget.input())
                        && Long.valueOf(5).equals(budget.output()) && equalAmount(COST, budget.cost()) && budget.settledAt() != null,
                "Original MODEL budget settled exactly once for the Fake result");
        require(ROUTE.equals(usage.route()) && MODEL.equals(usage.model()) && CURRENCY.equals(usage.currency())
                        && PRICE_VERSION.equals(usage.priceVersion()) && equalAmount(INPUT_RATE, usage.inputRate())
                        && equalAmount(OUTPUT_RATE, usage.outputRate()),
                "Durable model usage holds the exact frozen Fake tariff");
        require("PRICED".equals(usage.state()) && usage.safeReason() == null && budget.input().equals(usage.input())
                        && budget.output().equals(usage.output()) && equalAmount(COST, usage.cost())
                        && budget.settledAt().equals(usage.measuredAt()) && usage.createdAt() != null
                        && !usage.measuredAt().isBefore(usage.createdAt()) && finalDigest(tenant, fact).equals(usage.finalDigest()),
                "Durable model usage matches the original budget and exact committed final");
    }

    private static String finalDigest(long tenant, FactDTO fact) {
        return OrchestrationRequestDigests.hash(List.of("TIANJI_MODEL_USAGE_FINAL_V1", Long.toString(tenant),
                fact.runId(), fact.reservationId(), Long.toString(fact.usage().epoch()), ROUTE, MODEL, "PRICED", "10", "5",
                "1", PRICE_VERSION, "1", "0.05", "0"), Map.of());
    }

    private static boolean equalAmount(BigDecimal expected, BigDecimal actual) {
        return actual != null && expected.compareTo(actual) == 0;
    }

    private static void require(boolean condition, String failure) {
        if (!condition) throw new AssertionError(failure);
    }

    record FactDTO(String runId, String reservationId, BudgetDTO budget, UsageDTO usage) { }
    record BudgetDTO(long epoch, String kind, String state, Long input, Long output, BigDecimal cost, Instant settledAt) { }
    record UsageDTO(long epoch, String route, String model, String priceVersion, String currency,
                    BigDecimal inputRate, BigDecimal outputRate, String state, Long input, Long output,
                    BigDecimal cost, String safeReason, String finalDigest, Instant createdAt, Instant measuredAt) { }
}
