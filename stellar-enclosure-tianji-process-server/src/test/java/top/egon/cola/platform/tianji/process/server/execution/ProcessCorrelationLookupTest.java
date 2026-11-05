package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.ListProcessInstancesByCorrelationQuery;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.CorrelationReceipt;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessCorrelationLookupTest {
    private static final ProcessActor OWNER = new ProcessActor(20, ProcessActor.SubjectType.USER, "same-owner", "a".repeat(64));
    private static final String CORRELATION = "Correlation-A";
    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant UNTIL = Instant.parse("2026-11-05T00:00:00Z");
    private static final Instant SUBMITTED = Instant.parse("2026-11-02T09:23:41.123456Z");
    private static final Instant OBSERVED = UNTIL.plusSeconds(1);

    @Test
    void returns_only_exact_owned_correlation_receipts_and_binds_the_cursor() throws Exception {
        try (var fixture = new Fixture()) {
            for (int index = 20; index >= 0; index--) {
                fixture.data.add(row("instance-%02d".formatted(index)));
            }
            var first = fixture.service.listByCorrelation(OWNER, query(null));
            assertEquals(CORRELATION, first.correlationId());
            assertEquals(20, first.items().size());
            assertEquals(OBSERVED, first.observedAt());
            assertEquals("instance-20", first.items().getFirst().instanceId());
            assertEquals("instance-01", first.items().getLast().instanceId());
            assertEquals(SUBMITTED, first.items().getLast().submittedAt());
            assertNotNull(first.nextCursor());
            assertEquals(token(OWNER, query(null), SUBMITTED, "instance-01"), first.nextCursor());
            fixture.assertReadOnlyPrefix(4);
            assertEquals(List.of(20L, "USER", OWNER.issuerFingerprint(), OWNER.subjectId(), CORRELATION, utc(FROM), utc(UNTIL)),
                    fixture.calls.getLast().parameters());
            assertTrue(fixture.calls.getLast().sql().endsWith("ORDER BY created_at DESC, resource_id COLLATE \"C\" DESC LIMIT 21"));

            fixture.calls.clear();
            fixture.data.clear();
            fixture.data.add(row("instance-00"));
            var second = fixture.service.listByCorrelation(OWNER, query(first.nextCursor()));
            assertEquals("instance-00", second.items().getFirst().instanceId());
            assertNull(second.nextCursor());
            fixture.assertReadOnlyPrefix(5);
            var anchor = fixture.calls.get(3);
            assertTrue(anchor.sql().startsWith("SELECT EXISTS (SELECT 1"));
            assertTrue(anchor.sql().endsWith("AND created_at=? AND resource_id COLLATE \"C\"=?)"));
            assertTrue(fixture.calls.getLast().sql().contains("AND (created_at,resource_id COLLATE \"C\") < (?,?)"));
            var exact = List.of(20L, "USER", OWNER.issuerFingerprint(), OWNER.subjectId(), CORRELATION, utc(FROM), utc(UNTIL),
                    utc(SUBMITTED), "instance-01");
            assertEquals(exact, anchor.parameters());
            assertEquals(exact, fixture.calls.getLast().parameters());

            fixture.calls.clear();
            fixture.data.clear();
            // UTF-16 lexical order differs here; PostgreSQL C collation orders UTF-8 bytes.
            fixture.data.add(row("instance-😀"));
            fixture.data.add(row("instance-\ue000"));
            var unicode = fixture.service.listByCorrelation(OWNER, query(null));
            assertEquals(List.of("instance-😀", "instance-\ue000"),
                    unicode.items().stream().map(item -> item.instanceId()).toList());
            fixture.assertReadOnlyPrefix(4);

            fixture.calls.clear();
            fixture.data.clear();
            String maximumUtf8Id = "\u0800".repeat(255);
            for (int index = 0; index <= 20; index++) {
                fixture.data.add(new CorrelationReceipt(index == 19 ? maximumUtf8Id : "instance-" + index,
                        "release-v1", SUBMITTED.minusSeconds(index)));
            }
            var maximum = fixture.service.listByCorrelation(OWNER, query(null));
            assertEquals(maximumUtf8Id, maximum.items().getLast().instanceId());
            assertNotNull(maximum.nextCursor());
            assertEquals(1147, maximum.nextCursor().length());
            assertEquals(token(OWNER, query(null), SUBMITTED.minusSeconds(19), maximumUtf8Id), maximum.nextCursor());
            fixture.assertReadOnlyPrefix(4);
            verify(fixture.connection, never()).setReadOnly(true);
            verifyNoInteractions(fixture.runtime, fixture.history, fixture.repository);
        }
    }

    @Test
    void rejects_foreign_or_malformed_cursors_and_requires_an_owned_anchor() throws Exception {
        String owned = token(OWNER, query(null), SUBMITTED, "owned-anchor");
        try (var fixture = new Fixture()) {
            for (var actor : List.of(new ProcessActor(21, OWNER.subjectType(), OWNER.subjectId(), OWNER.issuerFingerprint()),
                    new ProcessActor(20, ProcessActor.SubjectType.SERVICE, OWNER.subjectId(), OWNER.issuerFingerprint()),
                    new ProcessActor(20, OWNER.subjectType(), "different-owner", OWNER.issuerFingerprint()),
                    new ProcessActor(20, OWNER.subjectType(), OWNER.subjectId(), "b".repeat(64)))) {
                assertThrows(IllegalArgumentException.class, () -> fixture.service.listByCorrelation(actor, query(owned)));
            }
            assertThrows(IllegalArgumentException.class, () -> fixture.service.listByCorrelation(OWNER,
                    new ListProcessInstancesByCorrelationQuery("correlation-a", FROM, UNTIL, owned)));
            assertThrows(IllegalArgumentException.class, () -> fixture.service.listByCorrelation(OWNER,
                    new ListProcessInstancesByCorrelationQuery(CORRELATION, FROM.plusSeconds(1), UNTIL, owned)));
            assertThrows(IllegalArgumentException.class, () -> fixture.service.listByCorrelation(OWNER, query("v1.A")));
            assertThrows(IllegalArgumentException.class, () -> fixture.service.listByCorrelation(OWNER, query("v1._w")));
            assertThrows(IllegalArgumentException.class, () -> fixture.service.listByCorrelation(OWNER,
                    query(token(OWNER, query(null), SUBMITTED, ".."))));
            assertThrows(IllegalArgumentException.class, () -> fixture.service.listByCorrelation(OWNER,
                    query(token(OWNER, query(null), UNTIL, "invalid-window"))));
            assertTrue(fixture.calls.isEmpty());

            fixture.anchorPresent = false;
            assertThrows(IllegalArgumentException.class, () -> fixture.service.listByCorrelation(OWNER, query(owned)));
            fixture.assertReadOnlyPrefix(4);
            assertTrue(fixture.calls.getLast().sql().startsWith("SELECT EXISTS"));
            assertEquals("owned-anchor", fixture.calls.getLast().parameters().getLast());
        }
    }

    @Test
    void rejects_unverified_owner_future_window_ambient_or_unavailable_primary_and_corrupt_rows() throws Exception {
        try (var fixture = new Fixture()) {
            assertThrows(AccessDeniedException.class, () -> fixture.service.listByCorrelation(
                    new ProcessActor(20, OWNER.subjectType(), OWNER.subjectId()), query(null)));
            new TransactionTemplate(fixture.manager).executeWithoutResult(status -> assertThrows(IllegalStateException.class,
                    () -> fixture.service.listByCorrelation(OWNER, query(null))));
            assertTrue(fixture.calls.isEmpty());

            assertThrows(IllegalArgumentException.class, () -> fixture.service.listByCorrelation(OWNER,
                    new ListProcessInstancesByCorrelationQuery(CORRELATION, FROM, OBSERVED.plusNanos(1_000), null)));
            fixture.assertReadOnlyPrefix(3);
            fixture.calls.clear();

            fixture.available = false;
            assertThrows(IllegalStateException.class, () -> fixture.service.listByCorrelation(OWNER, query(null)));
            fixture.assertReadOnlyPrefix(3);
            fixture.calls.clear();

            fixture.available = true;
            fixture.data.add(row("instance-01"));
            fixture.data.add(row("instance-02"));
            assertThrows(IllegalStateException.class, () -> fixture.service.listByCorrelation(OWNER, query(null)));
            fixture.assertReadOnlyPrefix(4);

            fixture.calls.clear();
            fixture.data.clear();
            for (int index = 0; index <= 20; index++) {
                fixture.data.add(new CorrelationReceipt(index == 20 ? "." : "instance-" + index,
                        "release-v1", SUBMITTED.minusSeconds(index)));
            }
            assertThrows(IllegalStateException.class, () -> fixture.service.listByCorrelation(OWNER, query(null)));
            fixture.assertReadOnlyPrefix(4);
        }
    }

    private static ListProcessInstancesByCorrelationQuery query(String cursor) {
        return new ListProcessInstancesByCorrelationQuery(CORRELATION, FROM, UNTIL, cursor);
    }

    private static CorrelationReceipt row(String id) { return new CorrelationReceipt(id, "release-v1", SUBMITTED); }
    private static OffsetDateTime utc(Instant time) { return OffsetDateTime.ofInstant(time, ZoneOffset.UTC); }

    private static String token(ProcessActor actor, ListProcessInstancesByCorrelationQuery query, Instant time, String id) {
        String digest = ProcessRequestDigest.hash(List.of("TIANJI_PROCESS_CORRELATION_CURSOR_V1",
                Long.toString(actor.tenantId()), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                query.correlationId(), query.fromInclusive().toString(), query.toExclusive().toString(), time.toString(), id),
                Map.of());
        return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(
                (time + "\n" + id + "\n" + digest).getBytes(StandardCharsets.UTF_8));
    }

    private record SqlCall(String sql, List<Object> parameters) { }

    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        final RepositoryService repository = mock(RepositoryService.class);
        final RuntimeService runtime = mock(RuntimeService.class);
        final HistoryService history = mock(HistoryService.class);
        final List<SqlCall> calls = new ArrayList<>();
        final List<CorrelationReceipt> data = new ArrayList<>();
        final MockedConstruction<JdbcTemplate> templates;
        final ProcessExecutionService service;
        boolean available = true;
        boolean anchorPresent = true;

        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            var process = mock(SpringProcessEngineConfiguration.class);
            when(process.getDataSource()).thenReturn(source);
            when(process.getTransactionManager()).thenReturn(manager);
            when(process.getDatabaseSchema()).thenReturn("tianji_process");
            when(process.getRepositoryService()).thenReturn(repository);
            when(process.getRuntimeService()).thenReturn(runtime);
            when(process.getHistoryService()).thenReturn(history);
            when(process.getBeans()).thenReturn(Map.of());
            when(process.isDbHistoryUsed()).thenReturn(true);
            when(process.getHistoryLevel()).thenReturn(HistoryLevel.INSTANCE);
            var dmn = mock(SpringDmnEngineConfiguration.class);
            when(dmn.getDataSource()).thenReturn(source);
            when(dmn.getTransactionManager()).thenReturn(manager);
            when(dmn.getDatabaseSchema()).thenReturn("tianji_process");
            when(dmn.getDmnRepositoryService()).thenReturn(mock(DmnRepositoryService.class));
            when(dmn.getBeans()).thenReturn(Map.of());
            templates = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(this::sql),
                    (mock, context) -> assertSame(source, context.arguments().getFirst()));
            try {
                service = new ProcessExecutionService(process, dmn, new ProcessModelValidator());
            } catch (RuntimeException | Error failure) {
                templates.close();
                throw failure;
            }
        }

        private Object sql(InvocationOnMock invocation) throws Exception {
            if ("getDataSource".equals(invocation.getMethod().getName())) return source;
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.hasResource(source));
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(Connection.TRANSACTION_REPEATABLE_READ, TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            var raw = invocation.getRawArguments();
            String statement = (String) raw[0];
            var parameters = raw.length == 3 ? Arrays.asList((Object[]) raw[2]) : List.of();
            calls.add(new SqlCall(statement, parameters));
            return switch (invocation.getMethod().getName()) {
                case "execute" -> {
                    assertTrue(List.of("SET TRANSACTION READ ONLY", "SET LOCAL statement_timeout = '2s'").contains(statement));
                    yield null;
                }
                case "queryForObject" -> {
                    if (statement.startsWith("SELECT pg_catalog.clock_timestamp()")) {
                        assertEquals(OffsetDateTime.class, raw[1]);
                        yield available ? OffsetDateTime.ofInstant(OBSERVED, ZoneOffset.ofHours(8)) : null;
                    }
                    assertEquals(Boolean.class, raw[1]);
                    assertTrue(statement.startsWith("SELECT EXISTS (SELECT 1 FROM "));
                    yield anchorPresent;
                }
                case "query" -> {
                    assertTrue(statement.startsWith("SELECT resource_id, release_key, created_at FROM "));
                    @SuppressWarnings("unchecked")
                    var mapper = (RowMapper<CorrelationReceipt>) raw[1];
                    var results = new ArrayList<CorrelationReceipt>();
                    for (var row : data) {
                        var rs = mock(ResultSet.class);
                        when(rs.getString("resource_id")).thenReturn(row.instanceId());
                        when(rs.getString("release_key")).thenReturn(row.releaseVersion());
                        when(rs.getObject("created_at", OffsetDateTime.class))
                                .thenReturn(OffsetDateTime.ofInstant(row.submittedAt(), ZoneOffset.ofHours(-4)));
                        results.add(mapper.mapRow(rs, results.size()));
                    }
                    yield results;
                }
                default -> throw new AssertionError("Unexpected database access " + invocation.getMethod().getName());
            };
        }

        void assertReadOnlyPrefix(int expectedStatements) {
            assertEquals(expectedStatements, calls.size());
            assertEquals("SET TRANSACTION READ ONLY", calls.get(0).sql());
            assertEquals("SET LOCAL statement_timeout = '2s'", calls.get(1).sql());
            var clock = calls.get(2);
            assertEquals(List.of("tianji_process"), clock.parameters());
            for (String guard : List.of("pg_catalog.current_schema()", "pg_catalog.has_schema_privilege",
                    "pg_catalog.has_table_privilege", "pg_catalog.pg_inherits", "NOT pg_catalog.pg_is_in_recovery()", "'transaction_read_only'",
                    "'transaction_isolation'")) {
                assertTrue(clock.sql().contains(guard));
            }
            for (var call : calls.subList(3, calls.size())) {
                assertTrue(call.sql().contains("\"tianji_process\".tianji_process_command_receipt"));
                assertTrue(call.sql().contains("tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=?"));
                assertTrue(call.sql().contains("command_type='START' AND state='APPLIED'"));
                assertTrue(call.sql().contains("correlation_id COLLATE \"C\"=? AND created_at>=? AND created_at<?"));
                assertFalse(call.sql().contains("FOR UPDATE"));
            }
        }

        @Override public void close() { templates.close(); }
    }
}
