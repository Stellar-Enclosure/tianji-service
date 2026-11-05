package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentRecoveryEffectEnum;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentRecoveryLifecycleEnum;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentRecoveryReasonEnum;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentRecoveryToolCoverageEnum;

class AgentRecoveryDispositionEvidenceTest {
    private static final TrustedAgentActor OWNER = new TrustedAgentActor(
            42L, TrustedAgentActor.SubjectType.USER, "owner", "a".repeat(64));
    private static final GetAgentRunQuery QUERY = new GetAgentRunQuery("run");
    private static final Instant NOW = Instant.parse("2026-11-05T09:45:00.123456Z");

    @Test
    void inspectRecoveryHasInternalSignature() {
        assertDoesNotThrow(() -> AgentLedgerService.class.getMethod("inspectRecovery",
                TrustedAgentActor.class, GetAgentRunQuery.class));
    }

    @Test
    void readsExactOwnerOnBoundPrimaryWithoutWrites() throws SQLException {
        try (var fixture = new Fixture()) {
            var actual = assertDoesNotThrow(() -> fixture.ledger.inspectRecovery(OWNER, QUERY));
            assertEquals(QUERY.runId(), actual.runId());
            assertEquals(4L, actual.revision());
            assertEquals(1, actual.attempt());
            assertEquals(NOW, actual.observedAt());
            assertEquals(AgentRecoveryLifecycleEnum.QUEUED, actual.lifecycle());
            assertEquals(AgentRecoveryReasonEnum.QUEUE_DISCOVERY, actual.reason());
            assertEquals(AgentRecoveryEffectEnum.UNVERIFIED, actual.agentEffect());
            assertEquals(AgentRecoveryToolCoverageEnum.COMPLETE, actual.toolEvidence().coverage());
            assertEquals(Boolean.FALSE, actual.toolEvidence().hasUnresolvedWrite());
            assertFalse(actual.evidenceConflict());
            assertEquals(List.of("read-only", "timeout", "clock", "run", "tool-catalog", "tool-history", "tool-rows"), fixture.calls);
            verify(fixture.source).getConnection();
            verify(fixture.connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            verify(fixture.connection).commit();
            verify(fixture.connection).close();
            assertFalse(TransactionSynchronizationManager.hasResource(fixture.source));
        }
    }

    @Test
    void classifiesPersistedLifecycleAndEffectWithoutGuessing() throws SQLException {
        try (var fixture = new Fixture()) {
            fixture.run.put("status", "RUNNING");
            fixture.run.put("has_lease_owner", true);
            fixture.run.put("has_lease_epoch", true);
            fixture.run.put("lease_expires_at", Timestamp.from(NOW.plusNanos(1_000)));
            assertEquals(AgentRecoveryReasonEnum.CURRENT_LEASE, fixture.inspect().reason());
            fixture.run.put("cancel_requested", true);
            assertEquals(AgentRecoveryReasonEnum.CANCEL_REQUESTED, fixture.inspect().reason());
            fixture.run.put("lease_expires_at", Timestamp.from(NOW));
            assertEquals(AgentRecoveryReasonEnum.LEASE_EXPIRED, fixture.inspect().reason());
            fixture.run.put("lease_expires_at", Timestamp.from(NOW.minusNanos(1_000)));
            assertEquals(AgentRecoveryLifecycleEnum.STALLED, fixture.inspect().lifecycle());
            fixture.run.put("has_lease_epoch", false);
            assertEquals(AgentRecoveryReasonEnum.FENCE_UNVERIFIED, fixture.inspect().reason());
            fixture.run.put("lease_expires_at", null);
            assertEquals(AgentRecoveryReasonEnum.LEASE_MISSING, fixture.inspect().reason());
            fixture.run.put("invalid_lease_time", true);
            assertEquals(AgentRecoveryReasonEnum.FENCE_UNVERIFIED, fixture.inspect().reason());
            fixture.run.put("deadline_at", Timestamp.from(NOW));
            assertEquals(AgentRecoveryReasonEnum.DEADLINE_DUE, fixture.inspect().reason());

            fixture.run.put("status", "SUSPENDED");
            fixture.run.put("deadline_at", Timestamp.from(NOW.plusSeconds(1)));
            fixture.run.put("safe_wait_reason", "AUTHORIZATION_DENIED");
            assertEquals(AgentRecoveryReasonEnum.AUTHORIZATION_DENIED, fixture.inspect().reason());
            fixture.run.put("safe_wait_reason", "AUTHORIZATION_UNAVAILABLE");
            assertEquals(AgentRecoveryReasonEnum.AUTHORIZATION_UNAVAILABLE, fixture.inspect().reason());
            fixture.run.put("safe_wait_reason", "unrecognized");
            assertEquals(AgentRecoveryReasonEnum.SUSPENSION_UNVERIFIED, fixture.inspect().reason());
            fixture.run.put("status", "WAITING");
            assertEquals(AgentRecoveryReasonEnum.LEGACY_WAIT_UNVERIFIED, fixture.inspect().reason());
            fixture.run.put("deadline_at", Timestamp.from(NOW.minusNanos(1_000)));
            assertEquals(AgentRecoveryReasonEnum.DEADLINE_DUE, fixture.inspect().reason());

            fixture.run.put("status", "FAILED");
            fixture.run.put("terminal_effect", "UNKNOWN");
            var terminal = fixture.inspect();
            assertEquals(AgentRecoveryLifecycleEnum.TERMINAL, terminal.lifecycle());
            assertEquals(AgentRecoveryEffectEnum.UNKNOWN, terminal.agentEffect());
            fixture.run.put("terminal_effect", "CONFIRMED");
            assertEquals(AgentRecoveryEffectEnum.RECORDED_CONFIRMED, fixture.inspect().agentEffect());
            fixture.run.put("terminal_effect", null);
            assertEquals(AgentRecoveryEffectEnum.UNVERIFIED, fixture.inspect().agentEffect());
            fixture.run.put("status", "invalid");
            assertEquals(AgentRecoveryReasonEnum.SOURCE_UNAVAILABLE, fixture.inspect().reason());
        }
    }

    @Test
    void keepsBoundedToolCoverageAndUnknownIndependent() throws SQLException {
        try (var fixture = new Fixture()) {
            var prepared = tool("PREPARED", "READ_ONLY", "NONE", false);
            var dispatching = tool("DISPATCHING", "IDEMPOTENT_WRITE", "UNKNOWN", false);
            var unknownRead = tool("UNKNOWN", "READ_ONLY", "NONE", false);
            var receipt = tool("SUCCEEDED", "APPROVAL_REQUIRED", "CONFIRMED", true);
            fixture.tools = List.of(unknownRead);
            var readOnly = fixture.inspect().toolEvidence();
            assertEquals(Boolean.TRUE, readOnly.hasUnknownResult());
            assertEquals(Boolean.FALSE, readOnly.hasUnresolvedWrite());
            fixture.tools = List.of(dispatching, receipt);
            fixture.run.put("status", "COMPLETED");
            fixture.run.put("terminal_effect", "NONE");
            var conflict = fixture.inspect();
            assertEquals(AgentRecoveryEffectEnum.RECORDED_NONE, conflict.agentEffect());
            assertEquals(Boolean.TRUE, conflict.toolEvidence().hasDispatching());
            assertEquals(Boolean.FALSE, conflict.toolEvidence().hasUnknownResult());
            assertEquals(Boolean.TRUE, conflict.toolEvidence().hasUnresolvedWrite());
            assertEquals(Boolean.TRUE, conflict.toolEvidence().hasLocalReceipt());
            assertTrue(conflict.evidenceConflict());

            fixture.tools = Collections.nCopies(64, prepared);
            var complete = fixture.inspect().toolEvidence();
            assertEquals(AgentRecoveryToolCoverageEnum.COMPLETE, complete.coverage());
            assertEquals(Boolean.FALSE, complete.hasDispatching());
            var truncatedRows = new ArrayList<>(fixture.tools);
            truncatedRows.set(0, dispatching);
            truncatedRows.add(receipt);
            fixture.tools = truncatedRows;
            var truncated = fixture.inspect().toolEvidence();
            assertEquals(AgentRecoveryToolCoverageEnum.TRUNCATED, truncated.coverage());
            assertEquals(Boolean.TRUE, truncated.hasDispatching());
            assertEquals(Boolean.TRUE, truncated.hasUnresolvedWrite());
            assertNull(truncated.hasUnknownResult());
            assertNull(truncated.hasLocalReceipt());
            var malformed = tool("UNKNOWN", "READ_ONLY", "UNKNOWN", false);
            fixture.tools = List.of(dispatching, malformed);
            assertUnavailable(fixture.inspect().toolEvidence());
            var mismatch = tool(null, null, null, null);
            mismatch.put("owner_matches", false);
            fixture.tools = List.of(dispatching, mismatch);
            assertUnavailable(fixture.inspect().toolEvidence());
        }
    }

    @Test
    void rejectsForeignLegacyAndUnavailableEvidence() throws SQLException {
        try (var fixture = new Fixture()) {
            assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED,
                    assertThrows(AgentLedgerException.class, () -> fixture.ledger.inspectRecovery(null, QUERY)).code());
            var legacy = new TrustedAgentActor(OWNER.tenantId(), OWNER.subjectType(), OWNER.subjectId());
            assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED,
                    assertThrows(AgentLedgerException.class, () -> fixture.ledger.inspectRecovery(legacy, QUERY)).code());
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                assertThrows(IllegalStateException.class, () -> fixture.inspect());
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
            verifyNoInteractions(fixture.source);
            var foreign = new TrustedAgentActor(OWNER.tenantId(), OWNER.subjectType(),
                    OWNER.subjectId(), "b".repeat(64));
            var foreignFailure = assertThrows(AgentLedgerException.class, () -> fixture.ledger.inspectRecovery(foreign, QUERY));
            fixture.runPresent = false;
            var missingFailure = assertThrows(AgentLedgerException.class, () -> fixture.inspect());
            assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, foreignFailure.code());
            assertEquals(foreignFailure.code(), missingFailure.code());
            assertEquals(foreignFailure.getMessage(), missingFailure.getMessage());
            assertFalse(fixture.calls.contains("tool-catalog"));
            fixture.runPresent = true;
            fixture.toolReadable = false;
            assertUnavailable(fixture.inspect().toolEvidence());
            assertFalse(fixture.calls.contains("tool-rows"));

            fixture.toolReadable = true;
            fixture.historyPresent = false;
            assertUnavailable(fixture.inspect().toolEvidence());
            fixture.historyPresent = true;
            fixture.historyValid = false;
            assertUnavailable(fixture.inspect().toolEvidence());
            assertFalse(fixture.calls.contains("tool-rows"));
            fixture.historyValid = true;
            fixture.primary = false;
            var nonPrimary = assertThrows(AgentLedgerException.class, () -> fixture.inspect());
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, nonPrimary.code());
            fixture.primary = true;
            fixture.clockNull = true;
            var missingClock = assertThrows(AgentLedgerException.class, () -> fixture.inspect());
            assertEquals(nonPrimary.code(), missingClock.code());
            assertEquals(nonPrimary.getMessage(), missingClock.getMessage());
            fixture.clockNull = false;
            fixture.toolFailure = true;
            var toolFailure = assertThrows(AgentLedgerException.class, () -> fixture.inspect());
            assertEquals(nonPrimary.code(), toolFailure.code());
            assertEquals(nonPrimary.getMessage(), toolFailure.getMessage());
        }
    }

    private static void assertUnavailable(AgentLedgerService.AgentRecoveryToolEvidenceDTO tool) {
        assertEquals(AgentRecoveryToolCoverageEnum.UNAVAILABLE, tool.coverage());
        assertNull(tool.hasDispatching());
        assertNull(tool.hasUnknownResult());
        assertNull(tool.hasUnresolvedWrite());
        assertNull(tool.hasLocalReceipt());
    }

    private static Map<String, Object> tool(String state, String risk, String effect, Boolean receipt) {
        var row = new HashMap<String, Object>();
        row.put("owner_matches", true);
        row.put("state", state);
        row.put("risk", risk);
        row.put("side_effect_outcome", effect);
        row.put("has_local_receipt", receipt);
        return row;
    }

    private static final class Fixture implements AutoCloseable {
        private final DataSource source = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final MockedConstruction<JdbcTemplate> jdbc;
        private final AgentLedgerService ledger;
        private final Map<String, Object> run = new HashMap<>();
        private final List<String> calls = new ArrayList<>();
        private List<Map<String, Object>> tools = List.of();
        private boolean runPresent = true;
        private boolean primary = true;
        private boolean toolReadable = true;
        private boolean historyPresent = true;
        private boolean historyValid = true;
        private boolean clockNull;
        private boolean toolFailure;

        private Fixture() throws SQLException {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            run.put("run_id", QUERY.runId());
            run.put("status", "QUEUED");
            run.put("revision", 4L);
            run.put("attempt", 1);
            run.put("deadline_at", Timestamp.from(NOW.plusSeconds(1)));
            run.put("lease_expires_at", null);
            run.put("invalid_lease_time", false);
            run.put("has_lease_owner", false);
            run.put("has_lease_epoch", false);
            run.put("cancel_requested", false);
            run.put("safe_wait_reason", null);
            run.put("terminal_effect", null);
            jdbc = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(this::jdbcCall),
                    (mock, context) -> assertSame(source, context.arguments().getFirst()));
            ledger = new AgentLedgerService(source);
        }

        private AgentLedgerService.AgentRecoveryDispositionDTO inspect() {
            return ledger.inspectRecovery(OWNER, QUERY);
        }

        private Object jdbcCall(InvocationOnMock invocation) throws SQLException {
            assertTrue(TransactionSynchronizationManager.hasResource(source));
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(Connection.TRANSACTION_REPEATABLE_READ,
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            var args = invocation.getRawArguments();
            String sql = (String) args[0];
            assertFalse(sql.contains("FOR UPDATE"));
            return switch (invocation.getMethod().getName()) {
                case "execute" -> {
                    if ("SET TRANSACTION READ ONLY".equals(sql)) calls.add("read-only");
                    else if ("SET LOCAL statement_timeout = '2s'".equals(sql)) calls.add("timeout");
                    else throw new AssertionError("Unexpected JDBC command");
                    yield null;
                }
                case "queryForObject" -> {
                    if (sql.contains("clock_timestamp()")) {
                        calls.add("clock");
                        assertTrue(sql.contains("NOT pg_catalog.pg_is_in_recovery()"));
                        assertTrue(sql.contains("current_setting('transaction_read_only') = 'on'"));
                        if (!primary) throw new EmptyResultDataAccessException(1);
                        yield clockNull ? null : Timestamp.from(NOW);
                    }
                    calls.add("tool-catalog");
                    assertTrue(sql.contains("n.nspname='tianji_agent' AND c.relname IN ('agent_tool_invocation','flyway_schema_history')"));
                    assertTrue(sql.contains("has_table_privilege(c.oid,'SELECT')"));
                    assertTrue(sql.contains("NOT c.relrowsecurity"));
                    yield toolReadable;
                }
                case "query" -> {
                    var arguments = (Object[]) args[2];
                    if (sql.contains("FROM tianji_agent.agent_run")) {
                        calls.add("run");
                        assertTrue(sql.contains("tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND run_id=?"));
                        assertTrue(sql.contains("LIMIT 2"));
                        assertTrue(sql.contains("result->>'runId'=run_id"));
                        assertTrue(sql.contains("result->>'status'=status"));
                        assertTrue(sql.contains("result->>'sideEffectOutcome' IN ('NONE','CONFIRMED','UNKNOWN')"));
                        assertFalse(sql.contains("SELECT *"));
                        assertArrayEquals(new Object[]{OWNER.tenantId(), OWNER.subjectType().name(), arguments[2],
                                OWNER.subjectId(), QUERY.runId()}, arguments);
                        if (!runPresent || !OWNER.issuerFingerprint().equals(arguments[2])) yield List.of();
                        yield mappedRows((RowMapper<?>) args[1], List.of(run));
                    }
                    if (sql.contains("FROM tianji_agent.flyway_schema_history")) {
                        calls.add("tool-history");
                        assertTrue(sql.contains("WHERE version=? LIMIT 2"));
                        assertArrayEquals(new Object[]{"13"}, arguments);
                        if (!historyPresent) yield List.of();
                        yield mappedRows((RowMapper<?>) args[1], List.of(Map.of(
                                "type", "SQL", "script", ToolExecutionStorageVerifier.FILE, "success", historyValid,
                                "checksum", AgentStorageVerifier.checksum(ToolExecutionStorageVerifier.FILE))));
                    }
                    calls.add("tool-rows");
                    assertTrue(sql.contains("FROM ONLY tianji_agent.agent_tool_invocation"));
                    assertTrue(sql.contains("owner_type=? AND owner_issuer=? AND owner_id=?"));
                    assertTrue(sql.contains("WHERE tenant_id=? AND run_id=?"));
                    assertTrue(sql.contains("ORDER BY invocation_id"));
                    assertTrue(sql.contains("LIMIT 65"));
                    assertTrue(sql.contains("CASE WHEN owner_matches THEN state END"));
                    assertFalse(sql.contains("dispatch_nonce_sha256"));
                    assertFalse(sql.contains("capability_id"));
                    assertArrayEquals(new Object[]{OWNER.subjectType().name(), OWNER.issuerFingerprint(),
                            OWNER.subjectId(), OWNER.tenantId(), QUERY.runId()}, arguments);
                    if (toolFailure) throw new DataAccessResourceFailureException("unsafe diagnostic");
                    yield mappedRows((RowMapper<?>) args[1], tools);
                }
                default -> throw new AssertionError("Unexpected JDBC operation " + invocation.getMethod().getName());
            };
        }

        private List<?> mappedRows(RowMapper<?> mapper, List<Map<String, Object>> rows) throws SQLException {
            var mapped = new ArrayList<>();
            for (var row : rows) {
                var result = mock(ResultSet.class, invocation -> {
                    String column = invocation.getArgument(0);
                    if (!row.containsKey(column)) throw new AssertionError("Unexpected projection " + column);
                    return row.get(column);
                });
                mapped.add(mapper.mapRow(result, mapped.size()));
            }
            return mapped;
        }

        @Override
        public void close() {
            jdbc.close();
        }
    }
}
