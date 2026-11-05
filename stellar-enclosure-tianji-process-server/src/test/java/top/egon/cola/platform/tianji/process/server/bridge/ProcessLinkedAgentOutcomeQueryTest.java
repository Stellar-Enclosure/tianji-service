package top.egon.cola.platform.tianji.process.server.bridge;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessAgentOutcomesQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO.DispatchState;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO.InboxState;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessLinkedAgentOutcomeQueryTest {
    private static final ProcessActor OWNER = actor(20, ProcessActor.SubjectType.USER, "starter", "a");
    private static final ProcessActor BRIDGE = actor(20, ProcessActor.SubjectType.SERVICE, "bridge", "b");
    private static final ProcessActor PRODUCER = actor(20, ProcessActor.SubjectType.SERVICE, "producer", "c");
    private static final String INSTANCE = "instance-one";
    private static final String OTHER_INSTANCE = "instance-two";
    private static final String SOURCE = "process-source";
    private static final String RELEASE = "expense-v1";
    private static final String CORRELATION = "expense-link";
    private static final String AGENT_SHA = "d".repeat(64);
    private static final Instant STARTED = Instant.parse("2026-11-05T00:00:00Z");
    private static final Instant DISPATCHED = STARTED.plusSeconds(1);
    private static final Instant RECEIVED = STARTED.plusSeconds(2);
    private static final Instant DECIDED = STARTED.plusSeconds(3);
    private static final Instant OBSERVED = STARTED.plusSeconds(4);
    private static final Instant DEADLINE = DISPATCHED.plusSeconds(120);
    private static final JsonMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Test
    void returns_only_proven_local_outcomes_and_opaque_owner_pages() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.append(21);
            fixture.rows.getFirst().confirm("private-run");
            fixture.rows.getFirst().receive("private-run", "APPLIED", "MESSAGE_APPLIED", "expenseAgent");
            var first = fixture.query.list(OWNER, query(INSTANCE, null));
            assertEquals(20, first.items().size());
            assertEquals(INSTANCE, first.instanceId());
            assertEquals(OBSERVED, first.observedAt());
            var item = first.items().getFirst();
            assertEquals("activity-000", item.activityId());
            assertEquals(DispatchState.ACKNOWLEDGED, item.dispatchState());
            assertEquals(InboxState.APPLIED, item.inboxState());
            assertEquals(AgentRunStatusEnum.FAILED, item.terminalStatus());
            assertEquals(DISPATCHED, item.dispatchRecordedAt());
            assertEquals(RECEIVED, item.receivedAt());
            assertEquals(DECIDED, item.decidedAt());
            assertEquals(InboxState.RUN_NOT_CONFIRMED, first.items().get(1).inboxState());
            assertNull(first.items().get(1).terminalStatus());
            assertTrue(first.nextCursor().matches("v1\\.[0-9a-f]{64}"));
            assertFalse(first.toString().contains("private-run"));
            assertFalse(first.toString().contains("job-000"));
            assertFalse(first.toString().contains(SOURCE));
            fixture.assertFirstPageSql(20);

            fixture.calls.clear();
            fixture.rows.addFirst(new Row("aaa-before-anchor", "activity-earlier"));
            var next = fixture.query.list(OWNER, query(INSTANCE, first.nextCursor()));
            assertEquals(List.of("activity-020"), next.items().stream().map(row -> row.activityId()).toList());
            assertNull(next.nextCursor());
        }
    }

    @Test
    void does_not_promote_early_or_mismatched_agent_events() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.append(5);
            var early = fixture.rows.get(0);
            early.receive("before-run-binding", "PENDING", null, "expenseAgent");
            var wrongRun = fixture.rows.get(1);
            wrongRun.confirm("different-confirmed-run");
            wrongRun.receive("other-unconfirmed-event", "PENDING", null, "expenseAgent");
            var confirmed = fixture.rows.get(2);
            confirmed.confirm("confirmed-run");
            confirmed.receive("foreign-first-event", "PENDING", null, "expenseAgent");
            confirmed.receive("confirmed-run", "PENDING", null, "expenseAgent");
            var mismatch = fixture.rows.get(3);
            mismatch.confirm("mismatched-definition");
            mismatch.receive("mismatched-definition", "DEAD", "DISPATCH_MISMATCH", "otherDefinition");
            var late = fixture.rows.get(4);
            late.confirm("late-run");
            late.receive("late-run", "IGNORED_LATE", "WAIT_ENDED", "expenseAgent");
            var items = fixture.query.list(OWNER, query(INSTANCE, null)).items();
            assertEquals(List.of(InboxState.RUN_NOT_CONFIRMED, InboxState.NOT_OBSERVED, InboxState.RECEIVED,
                    InboxState.DEAD, InboxState.IGNORED_LATE), items.stream().map(item -> item.inboxState()).toList());
            assertNull(items.get(0).terminalStatus());
            assertNull(items.get(1).terminalStatus());
            assertEquals(AgentRunStatusEnum.FAILED, items.get(2).terminalStatus());
            assertNull(items.get(2).decidedAt());
            assertNull(items.get(3).terminalStatus());
            assertEquals(AgentRunStatusEnum.FAILED, items.get(4).terminalStatus());
            assertTrue(fixture.calls.getLast().statement().contains("i.run_id=d.run_id"));
            assertTrue(fixture.calls.getLast().statement().contains("i.owner_issuer=d.subject_issuer"));
            assertTrue(fixture.calls.getLast().statement().contains("i.occurrence_id=d.occurrence_id"));
        }
    }

    @Test
    void rejects_foreign_or_changed_anchors_and_above_bound_scope() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.append(21);
            String cursor = fixture.query.list(OWNER, query(INSTANCE, null)).nextCursor();
            assertThrows(IllegalArgumentException.class, () -> fixture.query.list(OWNER, query(OTHER_INSTANCE, cursor)));
            assertThrows(IllegalArgumentException.class, () -> fixture.query.list(OWNER, query(INSTANCE, "v1." + "0".repeat(64))));
            fixture.rows.remove(19);
            assertThrows(IllegalArgumentException.class, () -> fixture.query.list(OWNER, query(INSTANCE, cursor)));
            fixture.rows.clear();
            fixture.append(257);
            fixture.calls.clear();
            assertThrows(IllegalStateException.class, () -> fixture.query.list(OWNER, query(INSTANCE, null)));
            assertEquals(5, fixture.calls.size());
            assertTrue(fixture.calls.getLast().statement().endsWith("LIMIT 257"));
        }
    }

    @Test
    void requires_exact_owner_before_dispatch_and_fails_closed_on_corrupt_evidence() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.append(1);
            var row = fixture.rows.getFirst();
            row.confirm("confirmed-run");
            row.receive("confirmed-run", "APPLIED", "MESSAGE_APPLIED", "expenseAgent");
            var query = query(INSTANCE, null);
            assertThrows(AccessDeniedException.class, () ->
                    fixture.query.list(new ProcessActor(20, ProcessActor.SubjectType.USER, OWNER.subjectId()), query));
            assertEquals(0, fixture.calls.size());
            for (var foreign : List.of(actor(20, ProcessActor.SubjectType.USER, OWNER.subjectId(), "f"),
                    actor(20, ProcessActor.SubjectType.SERVICE, OWNER.subjectId(), "a"),
                    actor(20, ProcessActor.SubjectType.USER, "other-subject", "a"))) {
                fixture.calls.clear();
                assertThrows(ProcessResourceNotFoundException.class, () -> fixture.query.list(foreign, query));
                assertEquals(4, fixture.calls.size());
                assertEquals(List.of(foreign.tenantId(), foreign.subjectType().name(), foreign.issuerFingerprint(),
                        foreign.subjectId(), INSTANCE), fixture.calls.getLast().parameters());
            }
            fixture.calls.clear();
            assertThrows(ProcessResourceNotFoundException.class, () ->
                    fixture.query.list(actor(21, ProcessActor.SubjectType.USER, OWNER.subjectId(), "a"), query));
            assertEquals(0, fixture.calls.size());
            new TransactionTemplate(fixture.manager).executeWithoutResult(status ->
                    assertThrows(IllegalStateException.class, () -> fixture.query.list(OWNER, query)));
            fixture.primary = false;
            assertThrows(IllegalStateException.class, () -> fixture.query.list(OWNER, query));
            fixture.primary = true;
            row.correlation = "unrelated";
            assertThrows(IllegalStateException.class, () -> fixture.query.list(OWNER, query));
            row.correlation = CORRELATION;
            row.auditAvailable = false;
            assertThrows(IllegalStateException.class, () -> fixture.query.list(OWNER, query));
            row.auditAvailable = true;
            row.fingerprintValid = false;
            assertThrows(IllegalStateException.class, () -> fixture.query.list(OWNER, query));
            row.fingerprintValid = true;
            fixture.duplicateJoin = true;
            assertThrows(IllegalStateException.class, () -> fixture.query.list(OWNER, query));
        }
    }

    private static GetProcessAgentOutcomesQuery query(String instanceId, String cursor) {
        return new GetProcessAgentOutcomesQuery(instanceId, cursor);
    }

    private static ProcessActor actor(long tenant, ProcessActor.SubjectType type, String subject, String issuer) {
        return new ProcessActor(tenant, type, subject, issuer.repeat(64));
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.ofHours(8));
    }

    private static String idempotency(String instanceId, String occurrence) {
        return "ad-" + ProcessRequestDigest.hash(List.of("process-agent-occurrence-v1", "20",
                BRIDGE.issuerFingerprint(), BRIDGE.subjectId(), SOURCE, instanceId, occurrence), Map.of());
    }

    private record EventSpec(String run, String state, String reason, String definition) { }

    private static final class Row {
        private final String occurrence;
        private final String activity;
        private String run;
        private String correlation = CORRELATION;
        private boolean auditAvailable = true;
        private boolean fingerprintValid = true;
        private final List<EventSpec> events = new ArrayList<>();

        Row(String occurrence, String activity) {
            this.occurrence = occurrence;
            this.activity = activity;
        }

        void confirm(String runId) { run = runId; }

        void receive(String runId, String state, String reason, String definition) {
            events.add(new EventSpec(runId, state, reason, definition));
        }

        ResultSet result(String instanceId) throws Exception {
            var strings = new HashMap<String, String>();
            strings.put("dispatch_occurrence", occurrence);
            strings.put("dispatch_subject", BRIDGE.subjectId());
            strings.put("dispatch_issuer", BRIDGE.issuerFingerprint());
            strings.put("dispatch_state", run == null ? "PENDING" : "ACKNOWLEDGED");
            strings.put("dispatch_run", run);
            strings.put("dispatch_release", RELEASE);
            strings.put("dispatch_correlation", correlation);
            strings.put("dispatch_idempotency", idempotency(instanceId, occurrence));
            strings.put("dispatch_element", activity);
            strings.put("agent_definition_key", "expenseAgent");
            strings.put("agent_definition_sha256", AGENT_SHA);

            var rs = mock(ResultSet.class);
            when(rs.getString(anyString())).thenAnswer(call -> strings.get(call.getArgument(0)));
            when(rs.getBoolean("released_model")).thenReturn(true);
            when(rs.getObject("dispatched_at", OffsetDateTime.class)).thenReturn(offset(DISPATCHED));
            when(rs.getObject("dispatch_not_after", OffsetDateTime.class)).thenReturn(offset(DEADLINE));
            when(rs.getLong("dispatch_timeout_ms")).thenReturn(120_000L);
            when(rs.getLong("agent_definition_version")).thenReturn(1L);
            var selected = events.stream().filter(event -> event.run().equals(run)).findFirst();
            if (selected.isEmpty()) return rs;
            var spec = selected.orElseThrow();
            var event = new AgentTerminalEvent(1, "agent-producer",
                    AgentTerminalEvent.eventIdFor("agent-producer", 20, BRIDGE.issuerFingerprint(), BRIDGE.subjectId(), spec.run(), 1),
                    20, BRIDGE.issuerFingerprint(), BRIDGE.subjectId(), idempotency(instanceId, occurrence),
                    new AgentProcessLink(SOURCE, WorkerScopeTypeEnum.BPMN, instanceId, occurrence, AGENT_SHA, DEADLINE),
                    spec.definition(), 1, spec.run(), 1, AgentRunStatusEnum.FAILED, RECEIVED, "e".repeat(64),
                    "PRIVATE_MODEL_FAILURE", "NONE");
            strings.put("event_id", event.eventId());
            strings.put("producer_id", event.producerId());
            strings.put("producer_issuer", PRODUCER.issuerFingerprint());
            strings.put("producer_subject", PRODUCER.subjectId());
            strings.put("owner_issuer", BRIDGE.issuerFingerprint());
            strings.put("owner_subject", BRIDGE.subjectId());
            strings.put("source_id", SOURCE);
            strings.put("scope_type", "BPMN");
            strings.put("scope_id", instanceId);
            strings.put("occurrence_id", occurrence);
            strings.put("run_id", spec.run());
            strings.put("event_payload", JSON.writeValueAsString(event));
            strings.put("event_sha256", fingerprintValid ? event.fingerprint() : "0".repeat(64));
            strings.put("state", spec.state());
            strings.put("decision_code", spec.reason());
            when(rs.getLong("tenant_id")).thenReturn(20L);
            when(rs.getLong("terminal_revision")).thenReturn(1L);
            when(rs.getTimestamp("received_at")).thenReturn(Timestamp.from(RECEIVED));
            when(rs.getTimestamp("retry_not_after")).thenReturn(Timestamp.from(RECEIVED.plusSeconds(60)));
            if (!"PENDING".equals(spec.state())) when(rs.getTimestamp("decided_at")).thenReturn(Timestamp.from(DECIDED));
            if ("APPLIED".equals(spec.state()) && auditAvailable) {
                strings.put("audit_state", "APPLIED");
                strings.put("audit_hash", event.fingerprint());
                strings.put("audit_release", RELEASE);
                strings.put("audit_correlation", CORRELATION);
                strings.put("audit_resource", "internal-execution");
            }
            return rs;
        }
    }

    private record SqlCall(String statement, List<Object> parameters) { }

    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        final List<SqlCall> calls = new ArrayList<>();
        final List<Row> rows = new ArrayList<>();
        final MockedConstruction<JdbcTemplate> templates;
        final ProcessLinkedAgentOutcomeQuery query;
        boolean primary = true;
        boolean duplicateJoin;

        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            var registration = new AgentResultProducerRegistry.Registration(PRODUCER, "agent-producer", SOURCE, BRIDGE);
            var registry = new AgentResultProducerRegistry(List.of(registration));
            templates = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(this::sql),
                    (mock, context) -> assertSame(source, context.arguments().getFirst()));
            try {
                query = new ProcessLinkedAgentOutcomeQuery(source, manager, "tianji_process", mock(AgentDispatchStore.class),
                        mock(AgentResultInbox.class), registry, List.of(registration));
            } catch (RuntimeException | Error failure) {
                templates.close();
                throw failure;
            }
        }

        void append(int count) {
            for (int index = 0; index < count; index++) {
                rows.add(new Row("job-%03d".formatted(index), "activity-%03d".formatted(index)));
            }
        }

        private Object sql(InvocationOnMock invocation) throws Exception {
            String method = invocation.getMethod().getName();
            if ("getDataSource".equals(method)) return source;
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.hasResource(source));
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(Connection.TRANSACTION_REPEATABLE_READ, TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            var raw = invocation.getRawArguments();
            String statement = (String) raw[0];
            var parameters = raw.length == 3 ? Arrays.asList((Object[]) raw[2]) : List.<Object>of();
            calls.add(new SqlCall(statement, parameters));
            return switch (method) {
                case "execute" -> {
                    assertTrue(List.of("SET TRANSACTION READ ONLY", "SET LOCAL statement_timeout = '2s'").contains(statement));
                    yield null;
                }
                case "queryForObject" -> {
                    assertTrue(statement.startsWith("SELECT pg_catalog.clock_timestamp()"));
                    assertEquals(OffsetDateTime.class, raw[1]);
                    yield primary ? offset(OBSERVED) : null;
                }
                case "query" -> select(statement, parameters, (RowMapper<?>) raw[1]);
                default -> throw new AssertionError("Unexpected database access " + method);
            };
        }

        private List<?> select(String statement, List<Object> parameters, RowMapper<?> mapper) throws Exception {
            var result = new ArrayList<>();
            if (statement.startsWith("SELECT subject_type,subject_issuer,")) {
                if (!parameters.subList(0, 4).equals(List.of(20L, "USER", OWNER.issuerFingerprint(), OWNER.subjectId()))) {
                    return result;
                }
                var start = mock(ResultSet.class);
                when(start.getString("subject_type")).thenReturn("USER");
                when(start.getString("subject_issuer")).thenReturn(OWNER.issuerFingerprint());
                when(start.getString("subject_id")).thenReturn(OWNER.subjectId());
                when(start.getString("command_id")).thenReturn("start");
                when(start.getString("request_sha256")).thenReturn("f".repeat(64));
                when(start.getString("release_key")).thenReturn(RELEASE);
                when(start.getString("correlation_id")).thenReturn(CORRELATION);
                when(start.getString("resource_id")).thenReturn((String) parameters.getLast());
                when(start.getTimestamp("created_at")).thenReturn(Timestamp.from(STARTED));
                result.add(mapper.mapRow(start, 0));
                return result;
            }
            assertEquals(20L, parameters.get(0));
            assertEquals(SOURCE, parameters.get(1));
            String instance = (String) parameters.get(2);
            if (statement.startsWith("SELECT occurrence_id FROM ")) {
                assertEquals(3, parameters.size());
                for (var row : rows) {
                    var sourceRow = mock(ResultSet.class);
                    when(sourceRow.getString(1)).thenReturn(row.occurrence);
                    result.add(mapper.mapRow(sourceRow, result.size()));
                }
                return result;
            }
            assertTrue(statement.startsWith("SELECT d.occurrence_id AS dispatch_occurrence"));
            for (var row : rows) {
                if (parameters.subList(3, parameters.size()).contains(row.occurrence)) {
                    result.add(mapper.mapRow(row.result(instance), result.size()));
                }
            }
            if (duplicateJoin && !result.isEmpty()) result.add(result.getFirst());
            return result;
        }

        void assertFirstPageSql(int items) throws Exception {
            assertEquals(6, calls.size());
            assertEquals("SET TRANSACTION READ ONLY", calls.get(0).statement());
            assertEquals("SET LOCAL statement_timeout = '2s'", calls.get(1).statement());
            assertTrue(calls.get(2).statement().contains("'transaction_read_only')='on'"));
            assertTrue(calls.get(2).statement().contains("NOT pg_catalog.pg_is_in_recovery()"));
            assertEquals(List.of(20L, "USER", OWNER.issuerFingerprint(), OWNER.subjectId(), INSTANCE), calls.get(3).parameters());
            assertEquals(List.of(20L, SOURCE, INSTANCE), calls.get(4).parameters());
            assertEquals(items + 3, calls.get(5).parameters().size());
            assertFalse(calls.get(5).statement().contains("input_data"));
            assertFalse(calls.get(5).statement().contains("instruction"));
            assertFalse(calls.get(5).statement().contains("FOR UPDATE"));
            assertTrue(calls.get(5).statement().contains("a.command_type='AGENT_RESULT'"));
            verify(connection, never()).setReadOnly(true);
        }

        @Override public void close() { templates.close(); }
    }
}
