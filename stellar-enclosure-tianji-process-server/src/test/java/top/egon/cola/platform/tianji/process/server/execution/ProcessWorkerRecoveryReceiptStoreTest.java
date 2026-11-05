package top.egon.cola.platform.tianji.process.server.execution;

import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerCommand;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerResult;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.Receipt;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class ProcessWorkerRecoveryReceiptStoreTest {
    private static final String V10 = "/db/tianji-process/V10__add_process_worker_recovery_receipts.sql";

    @Test
    void requiresImmutableRecoveryMigrationResource() throws Exception {
        String sql;
        try (var input = getClass().getResourceAsStream(V10)) {
            assertNotNull(input, "V10 recovery migration resource");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var columns = Pattern.compile("(?m)^    ([a-z_0-9]+) ((?:varchar|char|timestamptz)\\(\\d+\\)|bigint|integer)"
                + "( COLLATE \"C\")? NOT NULL,$").matcher(sql);
        assertEquals(List.of(
                "tenant_id bigint", "subject_type varchar(16)", "subject_issuer varchar(64)", "subject_id varchar(256)",
                "command_type varchar(32)", "command_id varchar(128)", "request_sha256 char(64)",
                "process_instance_id varchar(255)", "start_command_type varchar(32)", "start_command_id varchar(128)",
                "start_request_sha256 char(64)", "release_key varchar(128)", "model_type varchar(8)",
                "definition_key varchar(128)", "engine_definition_id varchar(255)", "definition_sha256 char(64)",
                "business_key varchar(255)", "correlation_id varchar(128)", "job_id varchar(255)",
                "occurrence_id varchar(255)", "execution_id varchar(255)", "element_id varchar(128)",
                "deadletter_revision integer", "expected_version char(64)", "evidence_sha256 char(64)",
                "admission_policy varchar(32)", "failure_code varchar(64)", "failure_decided_at timestamptz(6)",
                "reason varchar(512)", "result_status varchar(24)", "retries integer", "decided_at timestamptz(6)"),
                columns.results().map(row -> {
                    assertEquals(row.group(2).startsWith("char") || row.group(2).startsWith("varchar"),
                            row.group(3) != null, row.group(1));
                    return row.group(1) + " " + row.group(2);
                }).toList());
        String normalized = sql.replaceAll("\\s+", " ");
        assertTrue(normalized.contains("PRIMARY KEY (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id)"));
        assertEquals(3, Pattern.compile("FOREIGN KEY").matcher(sql).results().count());
        assertTrue(normalized.contains("FOREIGN KEY (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id)"
                + " REFERENCES tianji_process_command_receipt (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id)"));
        assertTrue(normalized.contains("FOREIGN KEY (tenant_id, subject_type, subject_issuer, subject_id, start_command_type, start_command_id)"
                + " REFERENCES tianji_process_command_receipt (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id)"));
        assertTrue(normalized.contains("FOREIGN KEY (tenant_id, release_key, model_type, definition_key)"
                + " REFERENCES tianji_process_release_definition (tenant_id, release_key, model_type, definition_key)"));
        assertTrue(normalized.endsWith("CREATE UNIQUE INDEX tianji_process_worker_recovery_job_uk"
                + " ON tianji_process_worker_recovery_receipt (job_id); "));
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15",
                        "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "127"),
                Pattern.compile("chr\\((\\d+)\\)").matcher(sql).results().map(row -> row.group(1)).toList());
        assertTrue(normalized.contains("reason <> '' AND reason = btrim(reason) AND octet_length(reason) <= 2048"
                + " AND translate(reason,"));
        assertTrue(normalized.contains("char_length(job_id) <= 64 AND occurrence_id = job_id"));
        assertTrue(normalized.contains("admission_policy = 'READ_ONLY_EXHAUSTED_RETRY_V1'"
                + " AND failure_code ~ '^[A-Z][A-Z0-9_]{0,63}$'"));
        assertTrue(normalized.contains("result_status = 'RETRY_SCHEDULED' AND retries = 1"));
        assertFalse(Pattern.compile("(?m)^\\s*(?:ALTER|GRANT|REVOKE|UPDATE|DELETE|TRUNCATE|DROP)\\b").matcher(sql).find());
    }


    private static final String SCHEMA = "tianji_process";
    private static final String REQUEST_SHA = "a".repeat(64);
    private static final String START_SHA = "b".repeat(64);
    private static final String DEFINITION_SHA = "c".repeat(64);
    private static final String VERSION = "d".repeat(64);
    private static final String EVIDENCE_SHA = "e".repeat(64);
    private static final ProcessActor ACTOR = new ProcessActor(42L, ProcessActor.SubjectType.SERVICE, "owner", "f".repeat(64));
    private static final Instant STARTED = Instant.parse("2026-11-05T06:00:00.000001Z");
    private static final Instant DECIDED = Instant.parse("2026-11-05T06:01:00.000002Z");
    private static final Instant FAILED = Instant.parse("2026-11-05T06:02:00.000003Z");
    private static final RecoverProcessWorkerCommand COMMAND =
            new RecoverProcessWorkerCommand("process-1", "job-1", "recover-1", VERSION, "Confirm 🧭 read-only");
    private static final RecoverProcessWorkerResult RESULT = new RecoverProcessWorkerResult(
            "process-1", "job-1", "job-1", "recover-1", RecoverProcessWorkerResult.StatusEnum.RETRY_SCHEDULED, 1, DECIDED);
    private static final TaskOrigin START = new TaskOrigin(ACTOR, "start-1", START_SHA, "release-1", "corr-1", "process-1", STARTED);
    private static final Receipt APPLIED = new Receipt(REQUEST_SHA, "release-1", "corr-1", "APPLIED", "job-1", DECIDED, null);
    private static final List<String> AUDIT_COLUMNS = List.of(
            "tenant_id", "subject_type", "subject_issuer", "subject_id", "command_type", "command_id", "request_sha256",
            "process_instance_id", "start_command_type", "start_command_id", "start_request_sha256", "release_key", "model_type",
            "definition_key", "engine_definition_id", "definition_sha256", "business_key", "correlation_id", "job_id", "occurrence_id",
            "execution_id", "element_id", "deadletter_revision", "expected_version", "evidence_sha256", "admission_policy",
            "failure_code", "failure_decided_at", "reason", "result_status", "retries", "decided_at");
    private static final List<Object> AUDIT_VALUES = List.of(
            42L, "SERVICE", ACTOR.issuerFingerprint(), "owner", "PROCESS_WORKER_RECOVER", "recover-1", REQUEST_SHA,
            "process-1", "START", "start-1", START_SHA, "release-1", "BPMN", "check", "engine-1", DEFINITION_SHA, "business-1",
            "corr-1", "job-1", "job-1", "execution-1", "worker", 7, VERSION, EVIDENCE_SHA,
            "READ_ONLY_EXHAUSTED_RETRY_V1", "WORKER_TIMEOUT", Timestamp.from(FAILED), COMMAND.reason(), "RETRY_SCHEDULED",
            1, Timestamp.from(DECIDED));

    @Test
    void insertsExactEvidenceAndDistinguishesTargetConflict() {
        var jdbc = new JdbcFixture();
        try (var templates = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(jdbc::answer));
             var verifiers = mockConstruction(ProcessApiSchemaVerifier.class)) {
            var store = new ProcessWorkerRecoveryReceiptStore(jdbc.source, SCHEMA);
            store.verifyStorage();
            verify(verifiers.constructed().getFirst()).verifyWorkerRecovery();
            jdbc.rows = List.of(authority(false));
            var entry = new ProcessWorkerRecoveryReceiptStore.Entry(ACTOR, REQUEST_SHA, COMMAND, START, "check", "engine-1",
                    DEFINITION_SHA, "business-1", "job-1", "execution-1", "worker", 7, EVIDENCE_SHA,
                    "READ_ONLY_EXHAUSTED_RETRY_V1", "WORKER_TIMEOUT", FAILED, RESULT);
            assertTrue(inTransaction(jdbc.source, () -> store.insert(entry)), "one row records the immutable recovery audit");
            assertTrue(jdbc.updateSql.endsWith(" ON CONFLICT (job_id) DO NOTHING"));
            assertTrue(jdbc.updateSql.contains("(" + String.join(", ", AUDIT_COLUMNS) + ")"));
            assertEquals(AUDIT_VALUES, jdbc.updateArgs);
            jdbc.updated = 0;
            assertFalse(inTransaction(jdbc.source, () -> store.insert(entry)));
            jdbc.updated = 2;
            assertThrows(IllegalStateException.class, () -> inTransaction(jdbc.source, () -> store.insert(entry)));
            jdbc.updateFailure = new DataIntegrityViolationException("unrelated integrity failure");
            assertSame(jdbc.updateFailure, assertThrows(DataIntegrityViolationException.class,
                    () -> inTransaction(jdbc.source, () -> store.insert(entry))));
            int updates = jdbc.updates;
            jdbc.rows = List.of(authority(true));
            assertThrows(IllegalStateException.class, () -> inTransaction(jdbc.source, () -> store.insert(entry)));
            assertEquals(updates, jdbc.updates);
            assertThrows(IllegalStateException.class, () -> inTransaction(jdbc.source, true, () -> store.insert(entry)));
            assertFalse(entry.toString().contains(COMMAND.reason()));
        }
    }

    @Test
    void readsPlainStartOnlyForExactOwner() {
        var jdbc = new JdbcFixture();
        try (var templates = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(jdbc::answer))) {
            var store = new ProcessCommandReceiptStore(new JdbcTemplate(jdbc.source), SCHEMA);
            var row = authority(true);
            row.put("subject_type", "SERVICE");
            row.put("subject_issuer", ACTOR.issuerFingerprint());
            row.put("subject_id", "owner");
            row.put("command_id", "start-1");
            row.put("request_sha256", START_SHA);
            row.put("release_key", "release-1");
            row.put("correlation_id", "corr-1");
            row.put("resource_id", "process-1");
            row.put("created_at", Timestamp.from(STARTED));
            jdbc.rows = List.of(row);
            assertEquals(START, assertDoesNotThrow(() -> store.readStartByInstance(ACTOR, "process-1")));
            assertEquals(List.of(42L, "SERVICE", ACTOR.issuerFingerprint(), "owner", "process-1"), jdbc.queryArgs);
            assertTrue(jdbc.querySql.contains("AND command_type='START' AND state='APPLIED' AND resource_id=?"));
            assertFalse(jdbc.querySql.contains("FOR UPDATE"));
            assertEquals(START, store.lockStartByInstance(ACTOR, "process-1"));
            assertTrue(jdbc.querySql.endsWith(" FOR UPDATE"));
            row.put("subject_type", "USER");
            var user = new ProcessActor(42L, ProcessActor.SubjectType.USER, "owner", ACTOR.issuerFingerprint());
            assertEquals(user, store.readStartByInstance(user, "process-1").requester());
            assertThrows(ProcessResourceNotFoundException.class, () -> store.readStartByInstance(ACTOR, "process-1"));
            jdbc.rows = List.of(row, row);
            assertThrows(ProcessResourceNotFoundException.class, () -> store.readStartByInstance(user, "process-1"));
            jdbc.rows = List.of();
            assertThrows(ProcessResourceNotFoundException.class, () -> store.readStartByInstance(user, "process-1"));
            assertTrue(store.insert(ACTOR, ProcessCommandReceiptStore.Kind.PROCESS_WORKER_RECOVER,
                    COMMAND.commandId(), REQUEST_SHA, START.releaseKey(), START.correlationId()));
            assertEquals("PROCESS_WORKER_RECOVER", jdbc.updateArgs.get(4));
            store.apply(ACTOR, ProcessCommandReceiptStore.Kind.PROCESS_WORKER_RECOVER, COMMAND.commandId(), COMMAND.jobId(), null);
            assertFalse(jdbc.updateSql.contains("decision_result"));
            assertEquals(COMMAND.jobId(), jdbc.updateArgs.getFirst());
            assertThrows(IllegalArgumentException.class, () -> store.apply(ACTOR, ProcessCommandReceiptStore.Kind.PROCESS_WORKER_RECOVER,
                    COMMAND.commandId(), COMMAND.jobId(), "{}"));
            jdbc.updated = 0;
            assertThrows(IllegalStateException.class, () -> store.apply(ACTOR, ProcessCommandReceiptStore.Kind.PROCESS_WORKER_RECOVER,
                    COMMAND.commandId(), COMMAND.jobId(), null));
        }
    }

    @Test
    void rejectsWrongOrAmbiguousAuditAndReplaysExactStoredResult() {
        var jdbc = new JdbcFixture();
        try (var templates = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(jdbc::answer));
             var verifiers = mockConstruction(ProcessApiSchemaVerifier.class)) {
            var store = new ProcessWorkerRecoveryReceiptStore(jdbc.source, SCHEMA);
            store.verifyStorage();
            var audit = replayRow();
            jdbc.rows = List.of(audit);
            assertEquals(RESULT, inTransaction(jdbc.source, () -> store.find(ACTOR, START, APPLIED, COMMAND, REQUEST_SHA)));
            assertEquals(RESULT, inTransaction(jdbc.source, () -> store.find(ACTOR, START, APPLIED, COMMAND, REQUEST_SHA)));
            assertTrue(jdbc.querySql.endsWith(" LIMIT 2"));
            assertEquals(List.of(42L, "SERVICE", ACTOR.issuerFingerprint(), "owner", "recover-1"), jdbc.queryArgs);
            jdbc.rows = List.of();
            assertThrows(IllegalStateException.class, () -> inTransaction(jdbc.source, () -> store.find(ACTOR, START, APPLIED, COMMAND, REQUEST_SHA)));
            jdbc.rows = List.of(audit, audit);
            assertThrows(IllegalStateException.class, () -> inTransaction(jdbc.source, () -> store.find(ACTOR, START, APPLIED, COMMAND, REQUEST_SHA)));
            for (var change : Map.ofEntries(
                    Map.entry("subject_id", "other-owner"), Map.entry("job_id", "other-job"),
                    Map.entry("process_instance_id", "other-instance"), Map.entry("reason", "Changed reason"),
                    Map.entry("base_state", "PENDING"), Map.entry("base_no_decision", false),
                    Map.entry("bound_definition_sha256", VERSION), Map.entry("retries", 2),
                    Map.entry("result_status", "OTHER"), Map.entry("decided_at", Timestamp.from(DECIDED.plusNanos(1)))).entrySet()) {
                var changed = new HashMap<>(audit);
                changed.put(change.getKey(), change.getValue());
                jdbc.rows = List.of(changed);
                assertThrows(IllegalStateException.class, () -> inTransaction(jdbc.source,
                        () -> store.find(ACTOR, START, APPLIED, COMMAND, REQUEST_SHA)), change.getKey());
            }
            assertEquals(0, jdbc.updates);
        }
    }

    @Test
    void probesAcceptedRestoreByExactJobAndOwner() {
        var jdbc = new JdbcFixture();
        try (var templates = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(jdbc::answer));
             var verifiers = mockConstruction(ProcessApiSchemaVerifier.class)) {
            var store = new ProcessWorkerRecoveryReceiptStore(jdbc.source, SCHEMA);
            store.verifyStorage();
            var hit = Map.<String, Object>of("job_id", "job-1");
            jdbc.rows = List.of(hit);
            assertTrue(inTransaction(jdbc.source, () -> store.hasAccepted(START, "job-1")));
            assertTrue(jdbc.querySql.startsWith("SELECT job_id FROM "));
            assertTrue(jdbc.querySql.endsWith(" LIMIT 2"));
            assertEquals(List.of("job-1", 42L, "SERVICE", ACTOR.issuerFingerprint(), "owner", "process-1"), jdbc.queryArgs);
            jdbc.rows = List.of();
            assertFalse(inTransaction(jdbc.source, () -> store.hasAccepted(START, "job-1")));
            jdbc.rows = List.of(hit, hit);
            assertThrows(IllegalStateException.class, () -> inTransaction(jdbc.source, () -> store.hasAccepted(START, "job-1")));
            assertEquals(0, jdbc.updates);
        }
    }

    @Test
    void requiresVerifiedSameSourceTransaction() {
        var jdbc = new JdbcFixture();
        try (var templates = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(jdbc::answer));
             var verifiers = mockConstruction(ProcessApiSchemaVerifier.class,
                     (verifier, context) -> assertEquals(List.of(jdbc.source, SCHEMA), context.arguments()))) {
            var store = new ProcessWorkerRecoveryReceiptStore(jdbc.source, SCHEMA);
            var foreign = mock(DataSource.class);
            assertTrue(store.usesDataSource(jdbc.source));
            assertFalse(store.usesDataSource(foreign));
            assertThrows(IllegalStateException.class, () -> inTransaction(jdbc.source, store::decisionTime));
            store.verifyStorage();
            assertThrows(IllegalStateException.class, store::decisionTime);
            assertThrows(IllegalStateException.class, () -> inTransaction(foreign, store::decisionTime));
            assertEquals(0, jdbc.sessionChecks);
            try {
                Thread.currentThread().interrupt();
                assertThrows(IllegalStateException.class, () -> inTransaction(jdbc.source, store::decisionTime));
            } finally {
                Thread.interrupted();
            }
            assertEquals(0, jdbc.sessionChecks);
            jdbc.primary = false;
            assertThrows(IllegalStateException.class, () -> inTransaction(jdbc.source, store::decisionTime));
            assertEquals(0, jdbc.clocks);
            jdbc.primary = true;
            assertEquals(DECIDED, inTransaction(jdbc.source, store::decisionTime));
            assertThrows(IllegalStateException.class, () -> inTransaction(jdbc.source, () -> {
                store.verifyStorage();
                return null;
            }));
            assertThrows(IllegalStateException.class, () -> inTransaction(jdbc.source, store::decisionTime));
        }
    }

    private static Map<String, Object> authority(boolean applied) {
        var row = new HashMap<String, Object>();
        row.put("base_state", applied ? "APPLIED" : "PENDING");
        row.put("base_resource_id", applied ? "job-1" : null);
        row.put("base_request_sha256", REQUEST_SHA);
        row.put("base_release_key", "release-1");
        row.put("base_correlation_id", "corr-1");
        row.put("base_created_at", Timestamp.from(DECIDED));
        row.put("base_no_decision", true);
        row.put("start_tenant_id", 42L);
        row.put("start_state", "APPLIED");
        row.put("start_subject_type", "SERVICE");
        row.put("start_subject_issuer", ACTOR.issuerFingerprint());
        row.put("start_subject_id", "owner");
        row.put("bound_start_command_id", "start-1");
        row.put("bound_start_request_sha256", START_SHA);
        row.put("start_resource_id", "process-1");
        row.put("start_release_key", "release-1");
        row.put("start_correlation_id", "corr-1");
        row.put("start_created_at", Timestamp.from(STARTED));
        row.put("bound_engine_definition_id", "engine-1");
        row.put("bound_definition_sha256", DEFINITION_SHA);
        return row;
    }

    private static Map<String, Object> replayRow() {
        var row = authority(true);
        for (int i = 0; i < AUDIT_COLUMNS.size(); i++) row.put(AUDIT_COLUMNS.get(i), AUDIT_VALUES.get(i));
        return row;
    }

    private static <T> T inTransaction(DataSource source, Supplier<T> work) {
        return inTransaction(source, false, work);
    }

    private static <T> T inTransaction(DataSource source, boolean readOnly, Supplier<T> work) {
        TransactionSynchronizationManager.bindResource(source, new Object());
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(readOnly);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            return work.get();
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            TransactionSynchronizationManager.unbindResource(source);
        }
    }

    private static final class JdbcFixture {
        private final DataSource source = mock(DataSource.class);
        private List<Map<String, Object>> rows = List.of();
        private String querySql;
        private List<Object> queryArgs;
        private String updateSql;
        private List<Object> updateArgs;
        private boolean primary = true;
        private int sessionChecks;
        private int clocks;
        private int updated = 1;
        private int updates;
        private DataIntegrityViolationException updateFailure;

        private Object answer(InvocationOnMock invocation) throws Throwable {
            var arguments = invocation.getArguments();
            if (arguments.length == 0 || !(arguments[0] instanceof String sql)) {
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            }
            if (sql.contains("pg_is_in_recovery()")) {
                sessionChecks++;
                return primary;
            }
            if ("SELECT clock_timestamp()".equals(sql)) {
                clocks++;
                return Timestamp.from(DECIDED);
            }
            if ("update".equals(invocation.getMethod().getName())) {
                updates++;
                updateSql = sql;
                updateArgs = Arrays.asList(arguments).subList(1, arguments.length);
                if (updateFailure != null) throw updateFailure;
                return updated;
            }
            if (arguments[1] instanceof RowMapper<?> mapper) {
                querySql = sql;
                queryArgs = Arrays.asList(arguments).subList(2, arguments.length);
                var result = new ArrayList<>();
                for (var row : rows) result.add(mapper.mapRow(resultSet(row), result.size()));
                return result;
            }
            throw new AssertionError("Unexpected fixture query " + sql);
        }
    }

    private static ResultSet resultSet(Map<String, Object> row) throws Exception {
        var rs = mock(ResultSet.class);
        when(rs.getString(anyString())).thenAnswer(call -> (String) row.get(call.getArgument(0)));
        when(rs.getBoolean(anyString())).thenAnswer(call -> Boolean.TRUE.equals(row.get(call.getArgument(0))));
        when(rs.getInt(anyString())).thenAnswer(call -> ((Number) row.getOrDefault(call.getArgument(0), 0)).intValue());
        when(rs.getLong(anyString())).thenAnswer(call -> ((Number) row.getOrDefault(call.getArgument(0), 0L)).longValue());
        when(rs.getTimestamp(anyString())).thenAnswer(call -> (Timestamp) row.get(call.getArgument(0)));
        return rs;
    }
}
