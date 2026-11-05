package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.mockito.ArgumentMatchers;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AgentModelUsageStoreTest {
    private static final TrustedAgentActor ACTOR = new TrustedAgentActor(
            42, TrustedAgentActor.SubjectType.SERVICE, "owner", "a".repeat(64));
    private static final Instant NOW = Instant.parse("2026-11-05T05:50:00.000001Z");
    private static final Instant CREATED = NOW.minusSeconds(2);

    @Test
    void finiteOwnerReadScopeAndDisabledNoV15() {
        var source = mock(DataSource.class);
        var disabled = new AgentLedgerService(source);
        assertFalse(disabled.modelUsageEnabled());
        assertFalse(disabled.acceptsModelUsage("CONFIG", "model-route"));
        assertEquals(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                assertThrows(AgentLedgerException.class,
                        () -> disabled.getModelUsage(ACTOR, new GetAgentRunQuery("run"), null)).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                assertThrows(AgentLedgerException.class, () -> disabled.listModelUsageByCorrelation(ACTOR,
                        new AgentLedgerService.AgentModelUsageCorrelationQuery("corr", CREATED, NOW, null))).code());
        verifyNoInteractions(source);

        var jdbc = mock(JdbcTemplate.class);
        var repository = new JdbcAgentLedgerRepository(jdbc, new AgentJsonCodec());
        var owned = run("RUNNING", 1, NOW.plusSeconds(1));
        doAnswer(invocation -> {
            var args = invocation.getRawArguments();
            String sql = (String) args[0];
            assertTrue(sql.contains("tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=?"));
            assertTrue(sql.contains("correlation_id COLLATE \"C\"=? AND created_at>=? AND created_at<?"));
            assertTrue(sql.contains("AND (created_at,run_id)<(?,?) ORDER BY created_at DESC, run_id DESC LIMIT ?"));
            assertArrayEquals(new Object[]{42L, "SERVICE", ACTOR.issuerFingerprint(), "owner", "corr",
                    Timestamp.from(CREATED), Timestamp.from(NOW), Timestamp.from(CREATED.plusSeconds(1)), "run-after", 21},
                    (Object[]) args[2]);
            return List.of(owned);
        }).when(jdbc).query(anyString(), ArgumentMatchers.<RowMapper<JdbcAgentLedgerRepository.ModelUsageRunRow>>any(),
                any(Object[].class));
        assertEquals(List.of(owned), repository.modelUsageRuns(ACTOR, "corr", CREATED, NOW, CREATED.plusSeconds(1), "run-after", 21));

        var slot = slot("PENDING", null, null, null, null, null, null);
        doAnswer(invocation -> {
            var args = invocation.getRawArguments();
            String sql = (String) args[0];
            assertTrue(sql.contains("u.tenant_id=b.tenant_id AND u.run_id=b.run_id"));
            assertTrue(sql.contains("u.reservation_id=b.reservation_id"));
            assertTrue(sql.contains("r.tenant_id=? AND r.subject_type=? AND r.subject_issuer=? AND r.subject_id=? AND r.run_id=?"));
            assertTrue(sql.contains("b.kind='MODEL'"));
            assertTrue(sql.contains("b.reservation_id COLLATE \"C\">? ORDER BY b.reservation_id COLLATE \"C\" LIMIT ?"));
            assertArrayEquals(new Object[]{42L, "SERVICE", ACTOR.issuerFingerprint(), "owner", "run", "slot-0", 101},
                    (Object[]) args[2]);
            return List.of(slot);
        }).when(jdbc).query(anyString(), ArgumentMatchers.<RowMapper<JdbcAgentLedgerRepository.ModelUsageSlotRow>>any(),
                any(Object[].class));
        assertEquals(List.of(slot), repository.modelUsageSlots(ACTOR, "run", "slot-0", 101));

        assertEquals(AgentLedgerService.AgentModelUsageStateEnum.PENDING,
                AgentLedgerService.projectModelUsageSlot(owned, slot, NOW).state());
        var expired = AgentLedgerService.projectModelUsageSlot(run("RUNNING", 1, NOW), slot, NOW);
        assertEquals(AgentLedgerService.AgentModelUsageStateEnum.UNKNOWN, expired.state());
        assertEquals("RESERVATION_ENDED", expired.safeReason());
        assertNull(expired.usage());
        var advanced = AgentLedgerService.projectModelUsageSlot(run("RUNNING", 2, NOW.plusSeconds(1)), slot, NOW);
        assertEquals(AgentLedgerService.AgentModelUsageStateEnum.UNKNOWN, advanced.state());

        var historic = new JdbcAgentLedgerRepository.ModelUsageSlotRow("legacy", 1, "SETTLED", CREATED,
                false, null, null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(AgentLedgerService.AgentModelUsageStateEnum.NOT_INSTRUMENTED,
                AgentLedgerService.projectModelUsageSlot(owned, historic, NOW).state());
        var unpriced = AgentLedgerService.projectModelUsageSlot(owned,
                slot("UNPRICED", 0L, 0L, null, "PRICE_MISSING", null, null), NOW);
        assertEquals(0L, unpriced.usage().totalTokens());
        assertNull(unpriced.usage().cost());
        var priced = AgentLedgerService.projectModelUsageSlot(owned,
                slot("PRICED", 2L, 3L, new BigDecimal("0.00000250"), null, "b".repeat(64), "USD"), NOW);
        assertEquals(5L, priced.usage().totalTokens());
        assertEquals(new BigDecimal("0.00000250"), priced.usage().cost());
        assertThrows(AgentLedgerException.class, () -> AgentLedgerService.projectModelUsageSlot(owned,
                slot("UNPRICED", Long.MAX_VALUE, 1L, null, "PRICE_MISSING", null, null), NOW));

        assertThrows(AgentLedgerException.class, () -> new AgentLedgerService.AgentModelUsageCorrelationQuery(
                "corr", NOW.minus(Duration.ofDays(31)).minusNanos(1_000), NOW, null));
        assertThrows(AgentLedgerException.class, () -> new AgentLedgerService.AgentModelUsageCorrelationQuery(
                " corr", CREATED, NOW, null));
        assertThrows(AgentLedgerException.class, () -> new AgentLedgerService.AgentModelUsageCorrelationQuery(
                "corr", CREATED, NOW.plusNanos(1), null));
    }

    private static JdbcAgentLedgerRepository.ModelUsageRunRow run(String state, long epoch, Instant expires) {
        return new JdbcAgentLedgerRepository.ModelUsageRunRow("run", "corr", CREATED, state, epoch, true, expires);
    }

    private static JdbcAgentLedgerRepository.ModelUsageSlotRow slot(String state, Long input, Long output, BigDecimal cost,
                                                                  String reason, String priceVersion, String currency) {
        return new JdbcAgentLedgerRepository.ModelUsageSlotRow("slot-1", 1, "RESERVED", CREATED, true, 1L, state,
                "model-route", "fixture-model", priceVersion, currency, input, output, cost, reason, CREATED,
                input == null ? null : NOW);
    }
}
