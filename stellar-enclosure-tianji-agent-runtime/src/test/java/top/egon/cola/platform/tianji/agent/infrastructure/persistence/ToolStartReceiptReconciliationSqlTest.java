package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolActionIntentSql.DispatchVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolActionIntentSql.HistoricalStartReceiptVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolActionIntentSql.Row;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies the actual statement and the participant's transaction contract without a database. */
class ToolStartReceiptReconciliationSqlTest {
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant DEADLINE = NOW.plusSeconds(10);
    private static final String HASH = "a".repeat(64);
    private static final String RUN = "dispatch-1";
    private static final String EVIDENCE = "{\"status\":\"APPLIED_OBSERVED\"}";
    private static final TrustedAgentActor OWNER =
            TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final TrustedAgentActor EXECUTOR =
            TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "process-executor", "https://service.example/20");

    @Test void checks_all_four_stamps_in_the_conditional_append() throws Exception {
        try (var fixture = new Fixture()) {
            var source = candidate();
            var stored = fixture.inTransaction(false, () -> fixture.sql.append(source, HASH, EVIDENCE, DEADLINE)).orElseThrow();
            assertEquals(NOW, stored.observedAt());
            assertEquals(HASH, stored.requestSha256());
            assertEquals(EVIDENCE, stored.encodedEvidence());
            assertTrue(fixture.query.startsWith("WITH fence_clock AS MATERIALIZED"));
            assertTrue(fixture.query.contains("INSERT INTO tianji_agent.agent_run_command_receipt"));
            assertTrue(fixture.query.contains("a.intent_id::text||':'||a.dispatch_epoch::text"));
            assertTrue(fixture.query.contains("d.lease_owner IS NULL AND d.lease_expires_at IS NULL"));
            assertTrue(fixture.query.contains("i.dispatch_not_after<=f.observed_at AND f.observed_at<CAST(? AS timestamptz)"));
            for (String relation : List.of("a", "i", "o", "d")) {
                assertTrue(fixture.query.contains("pg_catalog.to_jsonb(" + relation + ")=CAST(? AS jsonb)"),
                        "Missing exact CAS stamp for " + relation);
            }
            assertTrue(fixture.query.contains("ON CONFLICT (tenant_id,subject_type,subject_issuer,subject_id,operation,command_id) DO NOTHING"));
            assertEquals(List.of(ToolStartReceiptReconciliationSql.OPERATION, HASH, EVIDENCE, 20L, "USER", OWNER.issuerFingerprint(),
                    "alice", UUID.fromString(source.action().action().intentId()), RUN, Timestamp.from(DEADLINE),
                    source.actionStamp(), source.invocationStamp(), source.originRunStamp(), source.dispatchRunStamp()),
                    fixture.arguments);
            assertEquals(List.of("statement", "clock"), fixture.order);
            verify(fixture.connection).commit();
        }
    }

    @Test void zero_row_append_returns_empty_without_a_hidden_retry_or_replay_read() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.zeroRows = true;
            var result = fixture.inTransaction(false, () -> fixture.sql.append(candidate(), HASH, EVIDENCE, DEADLINE));
            assertTrue(result.isEmpty());
            assertEquals(List.of("statement", "clock"), fixture.order);
            verify(fixture.connection).commit();
        }
    }

    @Test void rolls_back_if_the_permit_expires_while_insert_is_waiting() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.current = false;
            var failure = assertThrows(AgentLedgerException.class,
                    () -> fixture.inTransaction(false, () -> fixture.sql.append(candidate(), HASH, EVIDENCE, DEADLINE)));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code());
            assertEquals(List.of("statement", "clock"), fixture.order);
            verify(fixture.connection).rollback();
            verify(fixture.connection, never()).commit();
        }
    }

    @Test void replay_reads_exact_owner_and_operation_and_rejects_a_conflicting_run() throws Exception {
        try (var fixture = new Fixture()) {
            var id = UUID.fromString(candidate().action().action().intentId());
            var stored = fixture.inTransaction(true, () -> fixture.sql.read(OWNER, RUN, id, 1, DEADLINE)).orElseThrow();
            assertEquals(EVIDENCE, stored.encodedEvidence());
            assertTrue(fixture.query.contains("FROM ONLY tianji_agent.agent_run_command_receipt"));
            assertTrue(fixture.query.contains("subject_issuer=? AND subject_id=? AND operation=? AND command_id=?"));
            assertFalse(fixture.query.substring(fixture.query.indexOf("WHERE")).contains("run_id"));
            assertEquals(List.of(4096, 20L, "USER", OWNER.issuerFingerprint(), "alice",
                    ToolStartReceiptReconciliationSql.OPERATION, id + ":1"), fixture.arguments);

            fixture.receiptRun = "other-run";
            var failure = assertThrows(AgentLedgerException.class,
                    () -> fixture.inTransaction(true, () -> fixture.sql.read(OWNER, RUN, id, 1, DEADLINE)));
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, failure.code());
        }
    }

    @Test void historical_probe_requires_repeatable_read_and_seals_the_exact_stopped_dispatch() throws Exception {
        try (var fixture = new Fixture()) {
            var ledger = mock(AgentLedgerService.class);
            when(ledger.usesDataSource(fixture.dataSource)).thenReturn(true);
            var intents = new ToolActionIntentSql(fixture.dataSource, ledger);
            fixture.zeroRows = true;
            var id = UUID.fromString(candidate().action().action().intentId());
            assertThrows(AgentLedgerException.class, () -> fixture.inTransaction(true,
                    () -> intents.historicalStartReceipt(OWNER, RUN, id, DEADLINE)));
            assertTrue(fixture.order.isEmpty());

            var snapshot = new TransactionTemplate(new DataSourceTransactionManager(fixture.dataSource));
            snapshot.setReadOnly(true);
            snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            assertTrue(snapshot.execute(status -> intents.historicalStartReceipt(OWNER, RUN, id, DEADLINE)).isEmpty());
            assertTrue(fixture.query.contains("FROM ONLY tianji_agent.agent_tool_action_intent a"));
            assertTrue(fixture.query.contains("a.owner_type=? AND a.owner_issuer=? AND a.owner_id=? AND a.intent_id=?"));
            assertTrue(fixture.query.contains("a.dispatch_run_id=? AND a.dispatch_epoch>0"));
            assertTrue(fixture.query.contains("d.lease_owner IS NULL AND d.lease_expires_at IS NULL"));
            assertTrue(fixture.query.contains("d.status='SUSPENDED' AND d.wait_reason='LEASE_EXPIRED'"));
            assertTrue(fixture.query.contains("i.dispatch_not_after<=pg_catalog.clock_timestamp()"));
            for (String stamp : List.of("action_stamp", "invocation_stamp", "origin_stamp", "dispatch_stamp")) {
                assertTrue(fixture.query.contains("pg_catalog.octet_length(sealed." + stamp + ")<=?"));
            }
            assertEquals(List.of(20L, "USER", OWNER.issuerFingerprint(), "alice", id, RUN,
                    524_288, 262_144, 8_388_608, 8_388_608), fixture.arguments);
            assertEquals(List.of("statement"), fixture.order);
        }
    }

    private static HistoricalStartReceiptVO candidate() {
        var tool = new ToolDescriptor("process.start", 1, "start_process", "Pinned write",
                new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
                new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
                new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start.invoice",
                        "tianji.api.process.instance.start", HASH, null));
        var action = new ToolActionRequest(OWNER, tool, "invoice-1", "business-v1", Map.of("amount", 20L));
        var dispatch = new DispatchVO(RUN, UUID.fromString("20000000-0000-0000-0000-000000000001"), 1, HASH, EXECUTOR);
        var row = new Row(action, tool, "origin-1", HASH, "DISPATCHING", 2,
                NOW.minusSeconds(20), NOW.minusSeconds(12), NOW.minusSeconds(1), null, dispatch, "UNKNOWN", null, null, null);
        var run = new AgentRunAuthorization.Request(OWNER, RUN, "assistant", 1, HASH, HASH, HASH, "policy-v1",
                false, NOW.minusSeconds(1));
        return new HistoricalStartReceiptVO(row, run, 2, NOW.minusSeconds(1), tool.parametersSha256(action.arguments()),
                "{\"stamp\":1}", "{\"stamp\":2}", "{\"stamp\":3}", "{\"stamp\":4}");
    }

    private static final class Fixture implements AutoCloseable {
        final DataSource dataSource = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final MockedConstruction<JdbcTemplate> templates;
        final ToolStartReceiptReconciliationSql sql;
        final List<String> order = new ArrayList<>();
        String query;
        List<Object> arguments;
        String receiptRun = RUN;
        boolean current = true;
        boolean zeroRows;

        Fixture() throws Exception {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            templates = mockConstruction(JdbcTemplate.class, (template, context) -> {
                when(template.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
                when(template.queryForObject(eq("SELECT clock_timestamp()"), eq(Timestamp.class))).thenReturn(Timestamp.from(NOW));
                when(template.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenAnswer(call -> {
                    order.add("clock");
                    assertTrue(((String) call.getArgument(0)).contains("pg_catalog.clock_timestamp()"));
                    return current;
                });
                doAnswer(call -> {
                    order.add("statement");
                    query = call.getArgument(0);
                    arguments = Arrays.asList(((Object[]) call.getRawArguments()[2]).clone());
                    if (zeroRows) return List.of();
                    RowMapper<?> mapper = call.getArgument(1);
                    ResultSet result = mock(ResultSet.class);
                    when(result.getString("run_id")).thenReturn(receiptRun);
                    when(result.getString("request_hash")).thenReturn(HASH);
                    when(result.getString("encoded_evidence")).thenReturn(EVIDENCE);
                    when(result.getTimestamp("created_at")).thenReturn(Timestamp.from(NOW));
                    return List.of(mapper.mapRow(result, 0));
                }).when(template).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
            });
            sql = new ToolStartReceiptReconciliationSql(dataSource);
        }

        <T> T inTransaction(boolean readOnly, Supplier<T> action) {
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            transaction.setReadOnly(readOnly);
            return transaction.execute(status -> action.get());
        }

        @Override public void close() { templates.close(); }
    }
}
