package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Direct participant markers prove guarded CAS and transaction ordering, not real PostgreSQL execution or lock races. */
class ToolActionWriteSqlTest {
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z"), CREATED = NOW.minusSeconds(60), UPDATED = NOW.minusSeconds(5);
    private static final String RUN = "10000000-0000-0000-0000-000000000001", DISPATCH_RUN = "10000000-0000-0000-0000-000000000002", HASH = "a".repeat(64);
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final TrustedAgentActor EXECUTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "process-executor", "https://service.example/20");
    private static final ToolDescriptor TOOL = descriptor(false);
    private static final ToolActionRequest ACTION = action(TOOL);
    private static final UUID INVOCATION = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final ToolActionIntentSql.DispatchVO DISPATCH = new ToolActionIntentSql.DispatchVO(DISPATCH_RUN, INVOCATION, 1, "d".repeat(64), EXECUTOR);

    @Test void write_participants_require_the_exact_source_transaction_before_reading_or_mutating() throws Exception {
        try (var fixture = new Fixture()) {
            assertUnavailable(() -> fixture.sql.lockForWrite(ACTION, TOOL, NOW.plusSeconds(10)));
            assertUnavailable(() -> fixture.sql.beginWrite(row("PREPARED", 7, NOW.plusSeconds(30)), DISPATCH, NOW, NOW.plusSeconds(10)));
            assertUnavailable(() -> fixture.sql.completeWrite(row("DISPATCHING", 7, NOW.plusSeconds(30)), unknown(null), NOW, NOW.plusSeconds(10)));
            var foreign = mock(DataSource.class);
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.bindResource(foreign, fixture.connection);
            try { assertUnavailable(() -> fixture.sql.lockForWrite(ACTION, TOOL, NOW.plusSeconds(10))); }
            finally { TransactionSynchronizationManager.unbindResource(foreign); TransactionSynchronizationManager.setActualTransactionActive(false); }
            assertTrue(fixture.queries.isEmpty()); assertTrue(fixture.updates.isEmpty());
        }
    }

    @Test void write_lock_precedes_the_fresh_join_and_preserves_expired_historical_facts() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.stored.put("expires_at", NOW.minusSeconds(1));
            var result = fixture.inTransaction(() -> fixture.sql.lockForWrite(ACTION, TOOL, NOW.plusSeconds(10)));
            assertNotNull(result); assertEquals(ACTION.requestSha256(), result.action().requestSha256());
            assertEquals(NOW.minusSeconds(1), result.expiresAt()); assertEquals("PREPARED", result.state());
            assertEquals(2, fixture.queries.size());
            assertTrue(fixture.queries.get(0).sql().startsWith("SELECT intent_id FROM tianji_agent.agent_tool_action_intent"));
            assertTrue(fixture.queries.get(0).sql().contains("FOR UPDATE"));
            assertTrue(fixture.queries.get(1).sql().contains("LEFT JOIN tianji_agent.agent_tool_invocation i"));
            for (var query : fixture.queries) assertEquals(List.of(20L, "USER", OWNER.issuerFingerprint(), "alice", UUID.fromString(ACTION.intentId())), Arrays.asList(query.arguments()));
            assertTrue(fixture.updates.isEmpty()); verify(fixture.jdbc.get()).setQueryTimeout(5);
            verify(fixture.connection).commit(); verify(fixture.connection, never()).rollback();
        }
    }

    @Test void locking_rechecks_identity_and_the_technical_deadline_after_waiting() throws Exception {
        try (var fixture = new Fixture()) {
            var changed = new ToolActionRequest(OWNER, TOOL, ACTION.businessKey(), ACTION.businessVersion(), Map.of("amount", 21L));
            var conflict = assertThrows(AgentLedgerException.class, () -> fixture.inTransaction(() -> fixture.sql.lockForWrite(changed, TOOL, NOW.plusSeconds(10))));
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, conflict.code()); assertNull(conflict.getCause());
            fixture.queries.clear(); fixture.afterLock = () -> fixture.databaseNow.set(NOW.plusSeconds(11));
            assertUnavailable(() -> fixture.inTransaction(() -> fixture.sql.lockForWrite(ACTION, TOOL, NOW.plusSeconds(10))));
            assertEquals(1, fixture.queries.size()); assertFalse(fixture.queries.getFirst().sql().contains("LEFT JOIN"));
            assertTrue(fixture.updates.isEmpty());
        }
    }

    @Test void begin_claims_the_action_with_exact_identity_and_null_dispatch_predicates_without_a_transient_join() throws Exception {
        try (var fixture = new Fixture()) {
            var prepared = row("PREPARED", 7, NOW.plusSeconds(30));
            fixture.inTransaction(() -> { fixture.sql.beginWrite(prepared, DISPATCH, NOW, NOW.plusSeconds(10)); return null; });
            assertEquals(1, fixture.updates.size()); var write = fixture.updates.getFirst();
            assertTrue(write.sql().contains("state='DISPATCHING'")); assertTrue(write.sql().contains("side_effect_outcome='UNKNOWN'"));
            assertTrue(write.sql().contains("revision=revision+1"));
            assertPredicates(write.sql(), List.of("tenant_id=?", "owner_type=?", "owner_issuer=?", "owner_id=?", "intent_id=?", "request_sha256=?",
                    "tool_key=?", "tool_version=?", "tool_sha256=?", "binding_sha256=?", "risk='IDEMPOTENT_WRITE'", "revision=?", "state='PREPARED'", "dispatch_epoch=0",
                    "dispatch_nonce_sha256 IS NULL", "dispatch_run_id IS NULL", "dispatch_invocation_id IS NULL", "executor_issuer IS NULL", "executor_id IS NULL", "approval_proof IS NULL"));
            assertArguments(write, OWNER.tenantId(), OWNER.subjectType().name(), OWNER.issuerFingerprint(), OWNER.subjectId(), UUID.fromString(ACTION.intentId()), ACTION.requestSha256(),
                    TOOL.key(), TOOL.version(), TOOL.reference().contentSha256(), TOOL.policy().bindingSha256(), 7L, DISPATCH.runId(), DISPATCH.invocationId(), DISPATCH.nonceSha256(),
                    EXECUTOR.issuerFingerprint(), EXECUTOR.subjectId(), Timestamp.from(NOW));
            assertTrue(fixture.queries.isEmpty(), "A mutator must not inspect the intermediate invocation/action join");
            verifyNoInteractions(fixture.ledger.budgets());
        }
    }

    @Test void begin_rejects_non_idempotent_states_foreign_executors_and_invalid_generations_before_writing() throws Exception {
        try (var fixture = new Fixture()) {
            var approved = descriptor(true); var waiting = new ToolActionIntentSql.Row(action(approved), approved, RUN, HASH, "AWAITING_APPROVAL", 7,
                    CREATED, UPDATED, NOW.plusSeconds(30), null, null, "NONE", null, null, null);
            var foreign = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, EXECUTOR.subjectId(), "https://service.example/21");
            for (var dispatch : List.of(new ToolActionIntentSql.DispatchVO(DISPATCH_RUN, INVOCATION, 2, DISPATCH.nonceSha256(), EXECUTOR),
                    new ToolActionIntentSql.DispatchVO(DISPATCH_RUN, INVOCATION, 1, DISPATCH.nonceSha256(), foreign))) {
                assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> { fixture.sql.beginWrite(row("PREPARED", 7, NOW.plusSeconds(30)), dispatch, NOW, NOW.plusSeconds(10)); return null; }));
            }
            assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> { fixture.sql.beginWrite(waiting, DISPATCH, NOW, NOW.plusSeconds(10)); return null; }));
            assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> { fixture.sql.beginWrite(row("DISPATCHING", 7, NOW.plusSeconds(30)), DISPATCH, NOW, NOW.plusSeconds(10)); return null; }));
            assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> fixture.sql.lockForWrite(action(approved), approved, NOW.plusSeconds(10))));
            assertTrue(fixture.updates.isEmpty());
        }
    }

    @Test void begin_and_completion_require_exact_microseconds_not_future_or_backwards_times_and_no_revision_overflow() throws Exception {
        try (var fixture = new Fixture()) {
            for (var timestamp : List.of(NOW.plusNanos(1_000), NOW.minusNanos(1), UPDATED.minusSeconds(1))) {
                assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> { fixture.sql.beginWrite(row("PREPARED", 7, NOW.plusSeconds(30)), DISPATCH, timestamp, NOW.plusSeconds(10)); return null; }));
                assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> { fixture.sql.completeWrite(row("DISPATCHING", 7, NOW.plusSeconds(30)), unknown(null), timestamp, NOW.plusSeconds(10)); return null; }));
            }
            assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> { fixture.sql.beginWrite(row("PREPARED", 7, NOW), DISPATCH, NOW, NOW.plusSeconds(10)); return null; }));
            assertUnavailable(() -> fixture.inTransaction(() -> { fixture.sql.beginWrite(row("PREPARED", Long.MAX_VALUE, NOW.plusSeconds(30)), DISPATCH, NOW, NOW.plusSeconds(10)); return null; }));
            assertUnavailable(() -> fixture.inTransaction(() -> { fixture.sql.completeWrite(row("DISPATCHING", Long.MAX_VALUE, NOW.plusSeconds(30)), unknown(null), NOW, NOW.plusSeconds(10)); return null; }));
            assertTrue(fixture.updates.isEmpty());
        }
    }

    @Test void completion_preserves_business_receipts_effect_semantics_and_generic_cost_without_reading_or_settling() throws Exception {
        var outcomes = List.of(
                new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", "process-1"), "receipt-1", null, new BigDecimal("12.12500000")),
                new ToolExecutionOutcome(ToolExecutionOutcome.Status.REJECTED, Map.of(), null, "BUSINESS_REJECTED", null),
                new ToolExecutionOutcome(ToolExecutionOutcome.Status.FAILED_KNOWN, Map.of(), null, "KNOWN_NO_EFFECT", new BigDecimal("0.5000")),
                unknown(null));
        for (var outcome : outcomes) {
            try (var fixture = new Fixture()) {
                fixture.inTransaction(() -> { fixture.sql.completeWrite(row("DISPATCHING", 8, NOW.plusSeconds(30)), outcome, NOW, NOW.plusSeconds(10)); return null; });
                assertEquals(1, fixture.updates.size()); var write = fixture.updates.getFirst();
                assertPredicates(write.sql(), List.of("tenant_id=?", "owner_type=?", "owner_issuer=?", "owner_id=?", "intent_id=?", "request_sha256=?",
                        "tool_key=?", "tool_version=?", "tool_sha256=?", "binding_sha256=?", "risk='IDEMPOTENT_WRITE'", "revision=?", "state=?",
                        "dispatch_epoch=?", "dispatch_nonce_sha256=?", "dispatch_run_id=?", "dispatch_invocation_id=?", "executor_issuer=?", "executor_id=?"));
                String state = outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED ? "APPLIED" : outcome.status().name();
                String effect = outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED ? "CONFIRMED" : outcome.status() == ToolExecutionOutcome.Status.UNKNOWN ? "UNKNOWN" : "NONE";
                assertArguments(write, state, effect, OWNER.tenantId(), OWNER.issuerFingerprint(), OWNER.subjectId(), UUID.fromString(ACTION.intentId()), ACTION.requestSha256(),
                        8L, "DISPATCHING", DISPATCH.epoch(), DISPATCH.nonceSha256(), DISPATCH.runId(), DISPATCH.invocationId(), EXECUTOR.issuerFingerprint(), EXECUTOR.subjectId(), Timestamp.from(NOW));
                String json = Arrays.stream(write.arguments()).filter(value -> value instanceof String text && text.startsWith("{")).map(Object::toString).findFirst().orElseThrow();
                assertEquals(outcome, new ToolExecutionJsonCodec().outcome(json, TOOL));
                assertArguments(write, outcome.businessReceiptId(), outcome.errorCode());
                assertTrue(fixture.queries.isEmpty()); verify(fixture.ledger, never()).executionMaterial(any(), any());
                verifyNoInteractions(fixture.ledger.budgets());
            }
        }
    }

    @Test void authentic_late_completion_ignores_old_expiry_and_can_refine_unknown_but_not_rewrite_known_or_waiting_facts() throws Exception {
        try (var fixture = new Fixture()) {
            var success = new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", "process-1"), "receipt-1", null, null);
            fixture.inTransaction(() -> { fixture.sql.completeWrite(row("UNKNOWN", 9, NOW.minusSeconds(1)), success, NOW, NOW.plusSeconds(10)); return null; });
            assertEquals(1, fixture.updates.size());
            assertFalse(fixture.updates.getFirst().sql().contains("expires_at>"));
            assertFalse(fixture.updates.getFirst().sql().contains("lease_expires_at"));
            fixture.updates.clear();
            for (var state : List.of("PREPARED", "APPLIED")) {
                assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> { fixture.sql.completeWrite(row(state, 7, NOW.plusSeconds(30)), success, NOW, NOW.plusSeconds(10)); return null; }));
            }
            assertTrue(fixture.updates.isEmpty());
        }
    }

    @Test void zero_row_compare_and_set_requests_rollback_with_a_scoped_sanitized_conflict() throws Exception {
        for (boolean complete : List.of(false, true)) {
            try (var fixture = new Fixture()) {
                fixture.updateCount = 0;
                var failure = assertThrows(AgentLedgerException.class, () -> fixture.inTransaction(() -> {
                    if (complete) fixture.sql.completeWrite(row("DISPATCHING", 7, NOW.plusSeconds(30)), unknown(null), NOW, NOW.plusSeconds(10));
                    else fixture.sql.beginWrite(row("PREPARED", 7, NOW.plusSeconds(30)), DISPATCH, NOW, NOW.plusSeconds(10));
                    return null;
                }));
                assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, failure.code()); assertNull(failure.getCause());
                verify(fixture.connection).rollback(); verify(fixture.connection, never()).commit();
                assertTrue(fixture.queries.isEmpty());
            }
        }
    }

    @Test void primary_and_technical_expiry_fail_before_any_action_update() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.primary = false;
            assertUnavailable(() -> fixture.inTransaction(() -> { fixture.sql.beginWrite(row("PREPARED", 7, NOW.plusSeconds(30)), DISPATCH, NOW, NOW.plusSeconds(10)); return null; }));
            fixture.primary = true;
            assertUnavailable(() -> fixture.inTransaction(() -> { fixture.sql.completeWrite(row("DISPATCHING", 7, NOW.plusSeconds(30)), unknown(null), NOW, NOW); return null; }));
            assertTrue(fixture.updates.isEmpty());
        }
    }

    private static void assertPredicates(String sql, List<String> predicates) {
        String condition = sql.substring(sql.indexOf("WHERE"));
        for (var predicate : predicates) assertTrue(condition.contains(predicate), "Missing guarded predicate: " + predicate);
    }
    private static void assertArguments(StatementVO statement, Object... expected) {
        var actual = Arrays.asList(statement.arguments()); for (var value : expected) assertTrue(actual.contains(value), "Missing bounded action argument");
    }
    private static void assertUnavailable(org.junit.jupiter.api.function.Executable work) {
        var failure = assertThrows(AgentLedgerException.class, work); assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code()); assertNull(failure.getCause());
    }
    private static ToolExecutionOutcome unknown(BigDecimal cost) { return new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "OUTCOME_UNKNOWN", cost); }
    private static ToolDescriptor descriptor(boolean approval) {
        return new ToolDescriptor(approval ? "process.approvedStart" : "process.start", 1, approval ? "approved_start" : "start_process", "Pinned write",
                new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")), new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
                new ToolPolicy(approval ? ToolPolicy.Risk.APPROVAL_REQUIRED : ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start.invoice", "tianji.api.process.instance.start", HASH, approval ? "approval-v1" : null));
    }
    private static ToolActionRequest action(ToolDescriptor tool) { return new ToolActionRequest(OWNER, tool, "private-invoice-1", "business-v1", Map.of("amount", 20L)); }
    private static ToolActionIntentSql.Row row(String state, long revision, Instant expiry) {
        boolean dispatched = !"PREPARED".equals(state);
        var outcome = state.equals("APPLIED") ? new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", "process-1"), "receipt-1", null, BigDecimal.ZERO)
                : state.equals("UNKNOWN") ? unknown(null) : null;
        return new ToolActionIntentSql.Row(ACTION, TOOL, RUN, HASH, state, revision, CREATED, UPDATED, expiry, null, dispatched ? DISPATCH : null,
                state.equals("APPLIED") ? "CONFIRMED" : dispatched ? "UNKNOWN" : "NONE", outcome, outcome == null ? null : outcome.errorCode(), outcome == null ? null : UPDATED);
    }

    private record StatementVO(String sql, Object[] arguments) { }
    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final AtomicReference<Instant> databaseNow = new AtomicReference<>(NOW);
        final AtomicReference<JdbcTemplate> jdbc = new AtomicReference<>();
        final List<StatementVO> queries = new ArrayList<>(), updates = new ArrayList<>();
        final Map<String, Object> stored = new HashMap<>();
        final MockedConstruction<JdbcTemplate> templates;
        final ToolActionIntentSql sql;
        Runnable afterLock = () -> { };
        boolean primary = true;
        int updateCount = 1;

        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
            when(ledger.usesDataSource(source)).thenReturn(true); when(ledger.budgets()).thenReturn(mock(AgentBudgetService.class));
            when(ledger.executionMaterial(eq(OWNER), any())).thenAnswer(call -> material(((GetAgentRunQuery) call.getArgument(1)).runId()));
            seed();
            templates = mockConstruction(JdbcTemplate.class, (template, context) -> {
                jdbc.set(template);
                when(template.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class))).thenAnswer(call -> primary);
                when(template.queryForObject(eq("SELECT clock_timestamp()"), eq(Timestamp.class))).thenAnswer(call -> Timestamp.from(databaseNow.get()));
                doAnswer(call -> {
                    String query = call.getArgument(0); Object[] arguments = (Object[]) call.getRawArguments()[2];
                    queries.add(new StatementVO(query, arguments.clone())); var mapper = (RowMapper<?>) call.getArgument(1);
                    if (!Arrays.asList(arguments).equals(List.of(20L, "USER", OWNER.issuerFingerprint(), OWNER.subjectId(), UUID.fromString(ACTION.intentId())))) return List.of();
                    if (query.startsWith("SELECT intent_id FROM tianji_agent.agent_tool_action_intent")) {
                        afterLock.run(); return List.of(mapper.mapRow(result(Map.of("intent_id", UUID.fromString(ACTION.intentId()))), 0));
                    }
                    if (query.contains("FROM tianji_agent.agent_tool_action_intent a")) return List.of(mapper.mapRow(result(stored), 0));
                    throw new AssertionError("Unexpected write participant query");
                }).when(template).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
                doAnswer(call -> { updates.add(new StatementVO(call.getArgument(0), ((Object[]) call.getRawArguments()[1]).clone())); return updateCount; })
                        .when(template).update(anyString(), any(Object[].class));
            });
            sql = new ToolActionIntentSql(source, ledger);
        }
        <T> T inTransaction(Supplier<T> work) { return new TransactionTemplate(new DataSourceTransactionManager(source)).execute(status -> work.get()); }
        private void seed() {
            var codec = new ToolExecutionJsonCodec();
            stored.put("tenant_id", OWNER.tenantId()); stored.put("intent_id", UUID.fromString(ACTION.intentId())); stored.put("owner_type", "USER");
            stored.put("owner_issuer", OWNER.issuerFingerprint()); stored.put("owner_id", OWNER.subjectId()); stored.put("action_key", ACTION.actionKey());
            stored.put("business_key", ACTION.businessKey()); stored.put("business_version", ACTION.businessVersion()); stored.put("tool_key", TOOL.key());
            stored.put("tool_version", TOOL.version()); stored.put("tool_sha256", TOOL.reference().contentSha256()); stored.put("binding_sha256", TOOL.policy().bindingSha256());
            stored.put("request_sha256", ACTION.requestSha256()); stored.put("logical_identity_sha256", OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_ACTION_LOGICAL_IDENTITY_V1", OWNER.identityFingerprint(), ACTION.actionKey(), ACTION.businessKey(), ACTION.businessVersion()), Map.of()));
            stored.put("descriptor", codec.descriptor(TOOL)); stored.put("arguments", codec.arguments(TOOL, ACTION.arguments())); stored.put("origin_run_id", RUN);
            stored.put("origin_snapshot_sha256", HASH); stored.put("risk", "IDEMPOTENT_WRITE"); stored.put("state", "PREPARED"); stored.put("revision", 7L);
            stored.put("created_at", CREATED); stored.put("updated_at", UPDATED); stored.put("expires_at", NOW.plusSeconds(30)); stored.put("dispatch_epoch", 0L); stored.put("side_effect_outcome", "NONE");
            for (var alias : List.of("origin_owner_matches", "dispatch_owner_matches", "dispatch_identity_matches", "dispatch_fact_matches")) stored.put(alias, true);
        }
        private AgentLedgerService.ExecutionMaterial material(String id) {
            var policy = new ExecutionPolicyDTO(Duration.ofMinutes(10), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 2, 1000, 100,
                    BigDecimal.ONE, "USD", 1, Set.of(TOOL.versionKey()), "retry-1", null);
            var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1",
                    new DefinitionDependenciesDTO(List.of(), List.of(), List.of(TOOL.reference()), "route-1", "policy-1"), policy, Map.of());
            var snapshot = new AgentExecutionSnapshot(definition, HASH, policy, new AgentInputDTO("fixed task", null, null), null);
            Instant oldDeadline = NOW.minusSeconds(1);
            var authorization = new AgentRunAuthorization.Request(OWNER, id, "assistant", 1, HASH, HASH, HASH, "policy-1", false, oldDeadline);
            var run = new AgentRunDTO(id, "assistant", 1, null, AgentRunStatusEnum.COMPLETED, null, false, 1, 2, 0, oldDeadline, CREATED.minusSeconds(600), NOW.minusSeconds(2));
            return new AgentLedgerService.ExecutionMaterial(run, snapshot, authorization);
        }
        @Override public void close() { templates.close(); }
    }
    private static ResultSet result(Map<String, Object> values) throws Exception {
        var result = mock(ResultSet.class);
        when(result.getString(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value == null ? null : value.toString(); });
        when(result.getLong(anyString())).thenAnswer(call -> { var value = (Number) values.get(call.getArgument(0)); return value == null ? 0L : value.longValue(); });
        when(result.getTimestamp(anyString())).thenAnswer(call -> { var value = (Instant) values.get(call.getArgument(0)); return value == null ? null : Timestamp.from(value); });
        when(result.getObject(anyString(), any(Class.class))).thenAnswer(call -> values.get(call.getArgument(0)));
        return result;
    }
}
