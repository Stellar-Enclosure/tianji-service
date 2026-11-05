package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.ListAgentRunsByCorrelationQuery;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.stream.IntStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

class AgentRunCorrelationLookupTest {
    private static final TrustedAgentActor OWNER = new TrustedAgentActor(42, TrustedAgentActor.SubjectType.USER,
            "owner", "a".repeat(64));
    private static final String CORRELATION = "审批 / 42";
    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant TO = FROM.plusSeconds(60);
    private static final Instant CREATED = FROM.plusNanos(1_000);
    private static final Instant NOW = TO.plusSeconds(1);
    private static final String RUN_ID = "00000000-0000-4000-8000-000000000021";
    private static final String SCOPE = "tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=?"
            + " AND correlation_id COLLATE \"C\"=? AND created_at>=? AND created_at<?";

    @Test
    void reads_only_the_exact_owner_on_the_bound_primary() throws SQLException {
        try (var fixture = new Fixture()) {
            fixture.rows = List.of(
                    new Row(OWNER, CORRELATION, RUN_ID, CREATED),
                    new Row(new TrustedAgentActor(43, OWNER.subjectType(), OWNER.subjectId(), OWNER.issuerFingerprint()),
                            CORRELATION, "foreign-tenant", CREATED),
                    new Row(new TrustedAgentActor(42, TrustedAgentActor.SubjectType.SERVICE, OWNER.subjectId(),
                            OWNER.issuerFingerprint()), CORRELATION, "foreign-type", CREATED),
                    new Row(new TrustedAgentActor(42, OWNER.subjectType(), OWNER.subjectId(), "b".repeat(64)),
                            CORRELATION, "foreign-issuer", CREATED),
                    new Row(new TrustedAgentActor(42, OWNER.subjectType(), "another", OWNER.issuerFingerprint()),
                            CORRELATION, "foreign-subject", CREATED),
                    new Row(OWNER, "审批 / 43", "other-key", CREATED),
                    new Row(OWNER, CORRELATION, "window-end", TO),
                    new Row(OWNER, CORRELATION, "too-early", FROM.minusNanos(1_000)));
            var page = fixture.ledger.listByCorrelation(OWNER, query(null));
            assertEquals(List.of(new AgentRunDTO(RUN_ID, "expense", 1, null, AgentRunStatusEnum.QUEUED,
                    null, false, 0, 0, 0, NOW, CREATED, null)), page.items());
            assertNull(page.nextCursor());
            assertEquals(List.of("read-only", "timeout", "clock", "page"), fixture.calls);
            assertArrayEquals(new Object[]{42L, "USER", OWNER.issuerFingerprint(), "owner", CORRELATION,
                    utcOffset(FROM), utcOffset(TO), 21}, fixture.lastPageArgs);
            verify(fixture.connection).commit();
        }
    }

    @Test
    void pages_twenty_plus_one_with_exact_tie_order_and_revalidates_the_anchor() throws SQLException {
        try (var fixture = new Fixture()) {
            fixture.rows = IntStream.rangeClosed(1, 21)
                    .mapToObj(number -> new Row(OWNER, CORRELATION, "r%02d".formatted(number), FROM)).toList();
            var first = fixture.ledger.listByCorrelation(OWNER, query(null));
            assertEquals(IntStream.iterate(21, number -> number - 1).limit(20)
                    .mapToObj(number -> "r%02d".formatted(number)).toList(),
                    first.items().stream().map(AgentRunDTO::runId).toList());
            String cursor = first.nextCursor();
            assertNotNull(cursor);
            assertTrue(cursor.matches("v1\\.[A-Za-z0-9_-]+"));
            assertTrue(cursor.length() <= 256);

            fixture.calls.clear();
            var last = fixture.ledger.listByCorrelation(OWNER, query(cursor));
            assertEquals(List.of("r01"), last.items().stream().map(AgentRunDTO::runId).toList());
            assertNull(last.nextCursor());
            assertEquals(List.of("read-only", "timeout", "clock", "anchor", "page"), fixture.calls);
            assertArrayEquals(new Object[]{42L, "USER", OWNER.issuerFingerprint(), "owner", CORRELATION,
                    utcOffset(FROM), utcOffset(TO), utcOffset(FROM), "r02", 21}, fixture.lastPageArgs);
        }
    }

    @Test
    void rejects_forged_missing_cross_owner_cross_filter_and_noncanonical_cursors() throws SQLException {
        try (var fixture = new Fixture()) {
            fixture.rows = IntStream.rangeClosed(1, 21)
                    .mapToObj(number -> new Row(OWNER, CORRELATION, "r%02d".formatted(number), CREATED)).toList();
            String valid = fixture.ledger.listByCorrelation(OWNER, query(null)).nextCursor();
            assertNotNull(valid);
            String[] fields = new String(Base64.getUrlDecoder().decode(valid.substring(3)), StandardCharsets.US_ASCII)
                    .split("\\|", -1);

            fixture.calls.clear();
            assertCode(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND,
                    () -> fixture.ledger.listByCorrelation(new TrustedAgentActor(42, OWNER.subjectType(), OWNER.subjectId(),
                            "b".repeat(64)), query(valid)));
            assertCode(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND,
                    () -> fixture.ledger.listByCorrelation(OWNER, new ListAgentRunsByCorrelationQuery(
                            "审批 / 43", FROM, TO, valid)));
            assertCode(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND,
                    () -> fixture.ledger.listByCorrelation(OWNER, new ListAgentRunsByCorrelationQuery(
                            CORRELATION, FROM, TO.plusNanos(1_000), valid)));
            for (String malformed : List.of("v1.A", cursor(fields[0], "2026-11-01T00:00:00.0000010Z", fields[2]),
                    cursor(fields[0], TO.toString(), fields[2]), cursor(fields[0], fields[1], "r.02"))) {
                assertCode(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND,
                        () -> fixture.ledger.listByCorrelation(OWNER, query(malformed)));
            }
            assertTrue(fixture.calls.isEmpty());

            fixture.rows = fixture.rows.stream().filter(row -> !row.id().equals(fields[2])).toList();
            assertCode(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND,
                    () -> fixture.ledger.listByCorrelation(OWNER, query(valid)));
            assertEquals(List.of("read-only", "timeout", "clock", "anchor"), fixture.calls);
            fixture.calls.clear();
            assertCode(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND,
                    () -> fixture.ledger.listByCorrelation(OWNER, query(cursor(fields[0], fields[1], "forged"))));
            assertEquals(List.of("read-only", "timeout", "clock", "anchor"), fixture.calls);
        }
    }

    @Test
    void rejects_future_and_unavailable_primary_or_storage_with_fixed_errors() throws SQLException {
        try (var fixture = new Fixture()) {
            assertCode(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND,
                    () -> fixture.ledger.listByCorrelation(OWNER, new ListAgentRunsByCorrelationQuery(
                            CORRELATION, FROM, NOW.plusNanos(1_000), null)));
            assertEquals(List.of("read-only", "timeout", "clock"), fixture.calls);

            fixture.calls.clear();
            fixture.observed = null;
            assertUnavailable(() -> fixture.ledger.listByCorrelation(OWNER, query(null)));
            assertEquals(List.of("read-only", "timeout", "clock"), fixture.calls);
            fixture.observed = NOW;
            fixture.pageFailure = true;
            assertUnavailable(() -> fixture.ledger.listByCorrelation(OWNER, query(null)));
            verify(fixture.connection, org.mockito.Mockito.times(3)).rollback();
        }
    }

    @Test
    void rejects_ambience_and_unverified_actor_before_jdbc_and_returns_an_owned_empty_page() throws SQLException {
        try (var fixture = new Fixture()) {
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED,
                    () -> fixture.ledger.listByCorrelation(null, query(null)));
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED,
                    () -> fixture.ledger.listByCorrelation(new TrustedAgentActor(42, OWNER.subjectType(), OWNER.subjectId()),
                            query(null)));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                assertUnavailable(() -> fixture.ledger.listByCorrelation(OWNER, query(null)));
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
            TransactionSynchronizationManager.initSynchronization();
            try {
                assertUnavailable(() -> fixture.ledger.listByCorrelation(OWNER, query(null)));
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            Thread.currentThread().interrupt();
            try {
                assertUnavailable(() -> fixture.ledger.listByCorrelation(OWNER, query(null)));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            verifyNoInteractions(fixture.source, fixture.connection);

            fixture.rows = List.of(new Row(OWNER, "another-correlation", "r01", FROM));
            var page = fixture.ledger.listByCorrelation(OWNER, query(null));
            assertTrue(page.items().isEmpty());
            assertNull(page.nextCursor());
        }
    }

    @Test
    void validates_sentinel_scope_and_order_before_returning_any_page() throws SQLException {
        try (var fixture = new Fixture()) {
            List<Row> good = IntStream.iterate(21, number -> number - 1).limit(21)
                    .mapToObj(number -> new Row(OWNER, CORRELATION, "r%02d".formatted(number), FROM)).toList();
            for (Row bad : List.of(new Row(OWNER, "another-correlation", "r01", FROM),
                    new Row(OWNER, CORRELATION, "r02", FROM), new Row(OWNER, CORRELATION, "r00", TO),
                    new Row(OWNER, CORRELATION, " r01 ", FROM),
                    new Row(OWNER, CORRELATION, "r99", FROM), new Row(OWNER, CORRELATION, "r01", FROM.plusNanos(1)))) {
                fixture.forcedPage = new ArrayList<>(good.subList(0, 20));
                fixture.forcedPage.add(bad);
                assertUnavailable(() -> fixture.ledger.listByCorrelation(OWNER, query(null)));
            }
            fixture.forcedPage = new ArrayList<>(good);
            fixture.forcedPage.add(new Row(OWNER, CORRELATION, "r00", FROM));
            assertUnavailable(() -> fixture.ledger.listByCorrelation(OWNER, query(null)));
        }
    }

    @Test
    void binds_and_reads_proleptic_utc_without_legacy_timestamp_conversion() throws SQLException {
        try (var fixture = new Fixture()) {
            Instant ancient = Instant.parse("0001-01-01T00:00:00.000001Z");
            fixture.rows = List.of(new Row(OWNER, CORRELATION, "ancient", ancient));
            var page = fixture.ledger.listByCorrelation(OWNER, new ListAgentRunsByCorrelationQuery(
                    CORRELATION, ancient, ancient.plusSeconds(1), null));
            assertEquals(ancient, page.items().getFirst().createdAt());
            assertEquals(utcOffset(ancient), fixture.lastPageArgs[5]);
            assertEquals(utcOffset(ancient.plusSeconds(1)), fixture.lastPageArgs[6]);
        }
    }

    private static OffsetDateTime utcOffset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String cursor(String binding, String createdAt, String id) {
        String body = binding + "|" + createdAt + "|" + id;
        return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(body.getBytes(StandardCharsets.US_ASCII));
    }

    private static AgentLedgerException assertCode(AgentErrorCodeEnum expected, Runnable operation) {
        var failure = assertThrows(AgentLedgerException.class, operation::run);
        assertEquals(expected, failure.code());
        return failure;
    }

    private static void assertUnavailable(Runnable operation) {
        assertEquals("Agent run correlation is unavailable",
                assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, operation).getMessage());
    }

    private static ListAgentRunsByCorrelationQuery query(String cursor) {
        return new ListAgentRunsByCorrelationQuery(CORRELATION, FROM, TO, cursor);
    }

    private record Row(TrustedAgentActor actor, String correlation, String id, Instant created) { }

    private static final class Fixture implements AutoCloseable {
        private final DataSource source = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final List<String> calls = new ArrayList<>();
        private final MockedConstruction<JdbcTemplate> jdbc;
        private final AgentLedgerService ledger;
        private List<Row> rows = List.of(new Row(OWNER, CORRELATION, RUN_ID, CREATED));
        private List<Row> forcedPage;
        private Instant observed = NOW;
        private boolean pageFailure;
        private Object[] lastPageArgs;

        private Fixture() throws SQLException {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            jdbc = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(this::jdbcCall),
                    (mock, context) -> assertSame(source, context.arguments().getFirst()));
            ledger = new AgentLedgerService(source);
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
                    if (sql.contains("pg_catalog.clock_timestamp()")) {
                        calls.add("clock");
                        assertTrue(sql.contains("NOT pg_catalog.pg_is_in_recovery()"));
                        assertTrue(sql.contains("pg_catalog.current_setting('transaction_read_only') = 'on'"));
                        yield observed == null ? null : Timestamp.from(observed);
                    }
                    calls.add("anchor");
                    assertTrue(sql.startsWith("SELECT EXISTS (SELECT 1 FROM tianji_agent.agent_run WHERE " + SCOPE));
                    assertTrue(sql.endsWith("AND created_at=? AND run_id COLLATE \"C\"=?)"));
                    assertSame(Boolean.class, args[1]);
                    Object[] bound = (Object[]) args[2];
                    assertEquals(9, bound.length);
                    yield scopedRows(bound).stream().anyMatch(row -> row.created().equals(((OffsetDateTime) bound[7]).toInstant())
                            && row.id().equals(bound[8]));
                }
                case "query" -> {
                    calls.add("page");
                    assertTrue(sql.contains("FROM tianji_agent.agent_run WHERE " + SCOPE));
                    assertTrue(sql.contains("ORDER BY created_at DESC, run_id COLLATE \"C\" DESC LIMIT ?"));
                    for (String forbidden : List.of("SELECT *", "execution_snapshot", "agent_model_usage", "flyway_schema_history",
                            "agent_run_command_receipt", "FOR UPDATE", "FOR SHARE", "JOIN")) {
                        assertFalse(sql.contains(forbidden));
                    }
                    Object[] bound = (Object[]) args[2];
                    lastPageArgs = bound.clone();
                    assertTrue(bound.length == 8 || bound.length == 10);
                    assertEquals(21, bound[bound.length - 1]);
                    assertEquals(bound.length == 10, sql.contains("AND (created_at,run_id COLLATE \"C\")<(?,?)"));
                    if (pageFailure) throw new DataAccessResourceFailureException("private fixture path");
                    List<Row> returned = forcedPage;
                    if (returned == null) {
                        returned = scopedRows(bound).stream().filter(row -> bound.length == 8 || before(row, bound))
                                .sorted(Comparator.comparing(Row::created).thenComparing(Row::id).reversed())
                                .limit(21).toList();
                    }
                    var mapped = new ArrayList<>();
                    for (Row record : returned) mapped.add(((RowMapper<?>) args[1]).mapRow(resultRow(record), mapped.size()));
                    yield mapped;
                }
                default -> throw new AssertionError("Unexpected JDBC method");
            };
        }

        private List<Row> scopedRows(Object[] bound) {
            var from = ((OffsetDateTime) bound[5]).toInstant();
            var to = ((OffsetDateTime) bound[6]).toInstant();
            return rows.stream().filter(row -> Long.valueOf(row.actor().tenantId()).equals(bound[0])
                    && row.actor().subjectType().name().equals(bound[1])
                    && row.actor().issuerFingerprint().equals(bound[2]) && row.actor().subjectId().equals(bound[3])
                    && row.correlation().equals(bound[4]) && !row.created().isBefore(from) && row.created().isBefore(to)).toList();
        }

        private static boolean before(Row row, Object[] bound) {
            Instant anchor = ((OffsetDateTime) bound[7]).toInstant();
            return row.created().isBefore(anchor) || row.created().equals(anchor) && row.id().compareTo((String) bound[8]) < 0;
        }

        private static ResultSet resultRow(Row record) throws SQLException {
            ResultSet row = mock(ResultSet.class);
            when(row.getString("run_id")).thenReturn(record.id());
            when(row.getString("definition_key")).thenReturn("expense");
            when(row.getLong("definition_version")).thenReturn(1L);
            when(row.getString("status")).thenReturn("QUEUED");
            when(row.getString("correlation_id")).thenReturn(record.correlation());
            when(row.getObject("created_at", OffsetDateTime.class)).thenReturn(utcOffset(record.created()));
            when(row.getObject("deadline_at", OffsetDateTime.class)).thenReturn(utcOffset(NOW));
            return row;
        }

        @Override
        public void close() {
            jdbc.close();
        }
    }
}
