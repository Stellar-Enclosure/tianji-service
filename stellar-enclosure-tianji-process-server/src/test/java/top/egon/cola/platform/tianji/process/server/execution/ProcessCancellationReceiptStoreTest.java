package top.egon.cola.platform.tianji.process.server.execution;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessResult;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessCancellationReceiptStoreTest {
    private static final String REQUEST_HASH = "1".repeat(64);
    private static final String START_HASH = "2".repeat(64);
    private static final String DEFINITION_HASH = "3".repeat(64);
    private static final String EXPECTED_VERSION = "4".repeat(64);
    private static final String RESULT_VERSION = "5".repeat(64);
    private static final Instant STARTED_AT = Instant.parse("2026-11-05T00:00:00.123456Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-11-05T00:06:00.123456Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-11-05T00:05:59.999999Z");
    private static final List<String> AUDIT_COLUMNS = List.of(
            "tenant_id", "subject_type", "subject_issuer", "subject_id", "command_type", "command_id",
            "request_sha256", "process_instance_id", "start_command_type", "start_command_id",
            "start_request_sha256", "release_key", "model_type", "definition_key", "engine_definition_id",
            "definition_sha256", "business_key", "correlation_id", "expected_version", "reason", "accepted",
            "result_status", "result_version", "result_finished_at", "decided_at", "agent_closure_scheduled");

    @Test
    void public_store_and_nested_entry_api_are_exact() throws Exception {
        assertEquals(ProcessCancellationReceiptStore.class,
                ProcessCancellationReceiptStore.class.getConstructor(DataSource.class, String.class).getDeclaringClass());
        assertEquals(boolean.class,
                ProcessCancellationReceiptStore.class.getMethod("usesDataSource", DataSource.class).getReturnType());
        assertEquals(void.class, ProcessCancellationReceiptStore.class.getMethod("verifyStorage").getReturnType());
        assertEquals(Optional.class, ProcessCancellationReceiptStore.class
                .getMethod("find", ProcessActor.class, String.class, String.class).getReturnType());
        assertEquals(void.class, ProcessCancellationReceiptStore.class
                .getMethod("insert", ProcessCancellationReceiptStore.Entry.class).getReturnType());
        assertEquals(List.of("actor", "requestHash", "command", "start", "definitionKey", "engineDefinitionId",
                        "definitionSha256", "businessKey", "result"),
                Arrays.stream(ProcessCancellationReceiptStore.Entry.class.getRecordComponents())
                        .map(component -> component.getName()).toList());
        assertEquals(List.of(ProcessActor.class, String.class, CancelProcessCommand.class,
                        ProcessCommandReceiptStore.TaskOrigin.class, String.class, String.class, String.class,
                        String.class, CancelProcessResult.class),
                Arrays.stream(ProcessCancellationReceiptStore.Entry.class.getRecordComponents())
                        .map(component -> component.getType()).toList());
    }

    @Test
    void entry_accepts_user_service_and_both_independent_microsecond_clock_orders() {
        for (ProcessActor actor : List.of(user(), service())) {
            var nativeAfterDecision = entry(actor, FINISHED_AT, DECIDED_AT);
            var nativeBeforeDecision = entry(actor, DECIDED_AT, FINISHED_AT);
            assertEquals(actor, nativeAfterDecision.actor());
            assertEquals(FINISHED_AT, nativeAfterDecision.result().finishedAt());
            assertEquals(DECIDED_AT, nativeAfterDecision.result().decidedAt());
            assertEquals(DECIDED_AT, nativeBeforeDecision.result().finishedAt());
            assertEquals(FINISHED_AT, nativeBeforeDecision.result().decidedAt());
            assertFalse(nativeAfterDecision.toString().contains(nativeAfterDecision.command().reason()));
            assertFalse(nativeAfterDecision.toString().contains(actor.subjectId()));
            assertEquals("ProcessCancellationReceiptStore.Entry[redacted]", nativeAfterDecision.toString());
        }
    }

    @Test
    void entry_rejects_actor_command_start_source_hash_text_and_time_drift() {
        ProcessActor actor = user();
        CancelProcessCommand command = command("process-1", "cancel-command-1");
        ProcessCommandReceiptStore.TaskOrigin start = start(actor, "process-1");
        CancelProcessResult result = result("process-1", "cancel-command-1", FINISHED_AT, DECIDED_AT);
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> entry(service(), REQUEST_HASH, command, start, "invoiceReview", "engine:7",
                                DEFINITION_HASH, "invoice-4711", result)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> entry(actor, REQUEST_HASH, command, start(service(), "process-1"), "invoiceReview", "engine:7",
                                DEFINITION_HASH, "invoice-4711", result)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> entry(actor, REQUEST_HASH, command, start(actor, "different-process"), "invoiceReview", "engine:7",
                                DEFINITION_HASH, "invoice-4711", result)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> entry(actor, REQUEST_HASH, command, start, "invoiceReview", "engine:7",
                                DEFINITION_HASH, "invoice-4711", result("different-process", "cancel-command-1", FINISHED_AT, DECIDED_AT))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> entry(actor, REQUEST_HASH, command, start, "invoiceReview", "engine:7",
                                DEFINITION_HASH, "invoice-4711", result("process-1", "different-command", FINISHED_AT, DECIDED_AT))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> entry(actor, "A".repeat(64), command, start, "invoiceReview", "engine:7",
                                DEFINITION_HASH, "invoice-4711", result)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> entry(actor, REQUEST_HASH, command, start, "bad key", "engine:7",
                                DEFINITION_HASH, "invoice-4711", result)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> entry(actor, REQUEST_HASH, command, start, "invoiceReview", " engine:7",
                                DEFINITION_HASH, "invoice-4711", result)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> entry(actor, REQUEST_HASH, command, start, "invoiceReview", "engine:7",
                                "A".repeat(64), "invoice-4711", result)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> entry(actor, REQUEST_HASH, command, start, "invoiceReview", "engine:7",
                                DEFINITION_HASH, "bad" + (char) 0, result)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> entry(actor, REQUEST_HASH, command, start, "invoiceReview", "engine:7",
                                DEFINITION_HASH, "invoice-4711", result("process-1", "cancel-command-1",
                                        FINISHED_AT.plusNanos(1), DECIDED_AT))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> entry(actor, REQUEST_HASH, command, start, "invoiceReview", "engine:7",
                                DEFINITION_HASH, "invoice-4711", result("process-1", "cancel-command-1",
                                        FINISHED_AT, DECIDED_AT.plusNanos(1))))
        );
    }

    @Test
    void constructor_storage_identity_and_transaction_admission_fail_closed() {
        DataSource source = mock(DataSource.class);
        var store = new ProcessCancellationReceiptStore(source, "process");
        assertTrue(store.usesDataSource(source));
        assertFalse(store.usesDataSource(mock(DataSource.class)));
        assertThrows(NullPointerException.class, () -> new ProcessCancellationReceiptStore(null, "process"));
        assertThrows(IllegalArgumentException.class, () -> new ProcessCancellationReceiptStore(source, "bad-schema"));
        assertThrows(IllegalStateException.class, () -> store.insert(entry(user(), FINISHED_AT, DECIDED_AT)));
        assertThrows(IllegalStateException.class,
                () -> store.find(user(), "cancel-command-1", REQUEST_HASH));
        inTransaction(source, () -> {
            assertThrows(IllegalStateException.class, store::verifyStorage);
            return null;
        });
    }

    @Test
    void insert_revalidates_authority_and_binds_exactly_one_immutable_26_column_audit() {
        ProcessCancellationReceiptStore.Entry entry = entry(service(), FINISHED_AT, DECIDED_AT);
        StoreHarness harness = new StoreHarness();
        ProcessCancellationReceiptStore store = harness.store();
        harness.rows(authorityRow(entry, "PENDING", null));
        harness.updateResult = 1;

        inTransaction(harness.source, () -> {
            store.insert(entry);
            return null;
        });

        String authoritySql = harness.querySql.get();
        assertAll(
                () -> assertNotNull(authoritySql),
                () -> assertNotNull(harness.updateSql.get()),
                () -> assertEquals(1, harness.updateCalls)
        );
        assertTrue(authoritySql.contains("tianji_process_command_receipt"));
        assertTrue(authoritySql.contains("tianji_process_release_definition"));
        assertTrue(authoritySql.toUpperCase(java.util.Locale.ROOT).contains("FOR UPDATE"));
        assertEquals(List.of("PROCESS_CANCEL", "START", "BPMN"),
                harness.queryArguments.get().stream().filter(value -> value instanceof String text
                        && Set.of("PROCESS_CANCEL", "START", "BPMN").contains(text)).toList());

        String insertSql = harness.updateSql.get();
        assertEquals(AUDIT_COLUMNS, insertColumns(insertSql));
        assertFalse(insertSql.toUpperCase(java.util.Locale.ROOT).contains("ON CONFLICT"));
        assertFalse(insertSql.toUpperCase(java.util.Locale.ROOT).contains("UPDATE "));
        assertFalse(insertSql.toUpperCase(java.util.Locale.ROOT).contains("DELETE "));
        assertEquals(expectedBindings(entry), harness.updateArguments.get());
        assertEquals(1, harness.updateCalls);
    }

    @Test
    void insert_rejects_generic_start_or_release_authority_mismatch_without_writing_audit() {
        ProcessCancellationReceiptStore.Entry entry = entry(user(), FINISHED_AT, DECIDED_AT);
        for (Map<String, Object> malformed : List.of(
                changed(authorityRow(entry, "APPLIED", null), "base_state", "APPLIED"),
                changed(authorityRow(entry, "PENDING", null), "base_request_sha256", "9".repeat(64)),
                changed(authorityRow(entry, "PENDING", null), "start_state", "PENDING"),
                changed(authorityRow(entry, "PENDING", null), "start_subject_id", "borrowed-owner"),
                changed(authorityRow(entry, "PENDING", null), "bound_engine_definition_id", "wrong-engine"),
                changed(authorityRow(entry, "PENDING", null), "bound_definition_sha256", "9".repeat(64)))) {
            StoreHarness harness = new StoreHarness();
            ProcessCancellationReceiptStore store = harness.store();
            harness.rows(malformed);
            assertThrows(IllegalStateException.class,
                    () -> inTransaction(harness.source, () -> { store.insert(entry); return null; }));
            assertEquals(0, harness.updateCalls);
        }
    }

    @Test
    void find_is_plain_read_maps_all_fields_and_never_mutates_or_locks_audit() {
        ProcessCancellationReceiptStore.Entry expected = entry(service(), FINISHED_AT, DECIDED_AT);
        StoreHarness harness = new StoreHarness();
        ProcessCancellationReceiptStore store = harness.store();
        harness.rows(joinedRow(expected, "APPLIED"));

        Optional<ProcessCancellationReceiptStore.Entry> actual = inTransaction(harness.source,
                () -> store.find(expected.actor(), expected.command().commandId(), expected.requestHash()));

        assertEquals(Optional.of(expected), actual);
        String sql = harness.querySql.get();
        assertTrue(sql.contains("tianji_process_cancel_receipt"));
        assertTrue(sql.contains("tianji_process_command_receipt"));
        assertTrue(sql.contains("tianji_process_release_definition"));
        assertTrue(sql.toUpperCase(java.util.Locale.ROOT).contains("LEFT JOIN"));
        assertFalse(sql.toUpperCase(java.util.Locale.ROOT).contains("FOR UPDATE"));
        assertEquals(0, harness.updateCalls);
    }

    @Test
    void find_distinguishes_absence_conflict_incomplete_and_ambiguous_applied_state() {
        ProcessCancellationReceiptStore.Entry entry = entry(user(), FINISHED_AT, DECIDED_AT);
        StoreHarness absent = new StoreHarness();
        assertEquals(Optional.empty(), inTransaction(absent.source,
                () -> absent.store().find(entry.actor(), entry.command().commandId(), entry.requestHash())));

        StoreHarness conflict = new StoreHarness();
        conflict.rows(joinedRow(entry, "APPLIED"));
        assertThrows(ProcessConflictException.class, () -> inTransaction(conflict.source,
                () -> conflict.store().find(entry.actor(), entry.command().commandId(), "9".repeat(64))));

        StoreHarness incomplete = new StoreHarness();
        incomplete.rows(changed(joinedRow(entry, "APPLIED"), "base_resource_id", null));
        assertThrows(IllegalStateException.class, () -> inTransaction(incomplete.source,
                () -> incomplete.store().find(entry.actor(), entry.command().commandId(), entry.requestHash())));

        StoreHarness ambiguous = new StoreHarness();
        Map<String, Object> row = joinedRow(entry, "APPLIED");
        ambiguous.rows(row, new LinkedHashMap<>(row));
        assertThrows(IllegalStateException.class, () -> inTransaction(ambiguous.source,
                () -> ambiguous.store().find(entry.actor(), entry.command().commandId(), entry.requestHash())));
    }

    @Test
    void find_rejects_foreign_affinity_before_hash_but_preserves_legitimate_hash_conflict() {
        ProcessActor requestedActor = user();
        String requestedCommand = "cancel-command-1";
        String differentHash = "9".repeat(64);
        ProcessCancellationReceiptStore.Entry legitimate = entry(requestedActor, FINISHED_AT, DECIDED_AT);
        ProcessCancellationReceiptStore.Entry foreignActor = entry(service(), FINISHED_AT, DECIDED_AT);
        ProcessCancellationReceiptStore.Entry foreignCommand = entry(requestedActor, REQUEST_HASH,
                command("process-1", "foreign-command"), start(requestedActor, "process-1"),
                "invoiceReview", "engine:7", DEFINITION_HASH, "invoice-4711",
                result("process-1", "foreign-command", FINISHED_AT, DECIDED_AT));

        for (ProcessCancellationReceiptStore.Entry foreign : List.of(foreignActor, foreignCommand)) {
            StoreHarness harness = new StoreHarness();
            harness.rows(joinedRow(foreign, "APPLIED"));
            assertThrowsExactly(IllegalStateException.class, () -> inTransaction(harness.source,
                    () -> harness.store().find(requestedActor, requestedCommand, differentHash)));
        }

        StoreHarness legitimateHarness = new StoreHarness();
        legitimateHarness.rows(joinedRow(legitimate, "APPLIED"));
        assertThrows(ProcessConflictException.class, () -> inTransaction(legitimateHarness.source,
                () -> legitimateHarness.store().find(requestedActor, requestedCommand, differentHash)));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void generic_receipt_lock_start_by_instance_returns_existing_origin_and_preserves_exact_owner_fence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ProcessActor actor = service();
        ProcessCommandReceiptStore.TaskOrigin origin = start(actor, "process-1");
        AtomicReference<String> sql = new AtomicReference<>();
        AtomicReference<List<Object>> arguments = new AtomicReference<>();
        doAnswer(invocation -> {
            sql.set(invocation.getArgument(0));
            arguments.set(Arrays.asList(((Object[]) invocation.getRawArguments()[2]).clone()));
            return List.of(origin);
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        ProcessCommandReceiptStore store = new ProcessCommandReceiptStore(jdbc, "process");
        assertSame(origin, store.lockStartByInstance(actor, "process-1"));
        assertTrue(sql.get().contains("command_type='START'"));
        assertTrue(sql.get().contains("state='APPLIED'"));
        assertTrue(sql.get().contains("resource_id=?"));
        assertTrue(sql.get().toUpperCase(java.util.Locale.ROOT).contains("FOR UPDATE"));
        assertEquals(List.of(actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(),
                actor.subjectId(), "process-1"), arguments.get());
    }

    private static ProcessCancellationReceiptStore.Entry entry(ProcessActor actor, Instant finishedAt, Instant decidedAt) {
        return entry(actor, REQUEST_HASH, command("process-1", "cancel-command-1"), start(actor, "process-1"),
                "invoiceReview", "engine:7", DEFINITION_HASH, "invoice-4711",
                result("process-1", "cancel-command-1", finishedAt, decidedAt));
    }

    private static ProcessCancellationReceiptStore.Entry entry(ProcessActor actor, String requestHash,
                                                                  CancelProcessCommand command,
                                                                  ProcessCommandReceiptStore.TaskOrigin start,
                                                                  String definitionKey, String engineDefinitionId,
                                                                  String definitionSha256, String businessKey,
                                                                  CancelProcessResult result) {
        return new ProcessCancellationReceiptStore.Entry(actor, requestHash, command, start, definitionKey,
                engineDefinitionId, definitionSha256, businessKey, result);
    }

    private static CancelProcessCommand command(String instanceId, String commandId) {
        return new CancelProcessCommand(instanceId, commandId, EXPECTED_VERSION, "private cancellation reason");
    }

    private static CancelProcessResult result(String instanceId, String commandId,
                                              Instant finishedAt, Instant decidedAt) {
        return new CancelProcessResult(instanceId, commandId, true, ProcessInstanceStatusEnum.CANCELLED,
                RESULT_VERSION, finishedAt, decidedAt, true);
    }

    private static ProcessCommandReceiptStore.TaskOrigin start(ProcessActor actor, String instanceId) {
        return new ProcessCommandReceiptStore.TaskOrigin(actor, "start-command-1", START_HASH, "release-7",
                "corr-4711", instanceId, STARTED_AT);
    }

    private static ProcessActor user() {
        return new ProcessActor(20, ProcessActor.SubjectType.USER, "user-1", "a".repeat(64));
    }

    private static ProcessActor service() {
        return new ProcessActor(20, ProcessActor.SubjectType.SERVICE, "service-1", "a".repeat(64));
    }

    private static List<Object> expectedBindings(ProcessCancellationReceiptStore.Entry entry) {
        return List.of(entry.actor().tenantId(), entry.actor().subjectType().name(), entry.actor().issuerFingerprint(),
                entry.actor().subjectId(), "PROCESS_CANCEL", entry.command().commandId(), entry.requestHash(),
                entry.command().instanceId(), "START", entry.start().commandId(), entry.start().requestHash(),
                entry.start().releaseKey(), "BPMN", entry.definitionKey(), entry.engineDefinitionId(),
                entry.definitionSha256(), entry.businessKey(), entry.start().correlationId(),
                entry.command().expectedVersion(), entry.command().reason(), entry.result().accepted(),
                entry.result().status().name(), entry.result().resultingVersion(),
                Timestamp.from(entry.result().finishedAt()), Timestamp.from(entry.result().decidedAt()),
                entry.result().linkedAgentClosureScheduled());
    }

    private static List<String> insertColumns(String sql) {
        int start = sql.indexOf('(');
        int end = sql.indexOf(')', start + 1);
        assertTrue(start > 0 && end > start);
        return Arrays.stream(sql.substring(start + 1, end).split(","))
                .map(String::trim).toList();
    }

    private static Map<String, Object> authorityRow(ProcessCancellationReceiptStore.Entry entry,
                                                     String baseState, String baseResource) {
        Map<String, Object> row = joinedRow(entry, baseState);
        row.put("base_resource_id", baseResource);
        return row;
    }

    private static Map<String, Object> joinedRow(ProcessCancellationReceiptStore.Entry entry, String baseState) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenant_id", entry.actor().tenantId());
        row.put("subject_type", entry.actor().subjectType().name());
        row.put("subject_issuer", entry.actor().issuerFingerprint());
        row.put("subject_id", entry.actor().subjectId());
        row.put("command_type", "PROCESS_CANCEL");
        row.put("command_id", entry.command().commandId());
        row.put("request_sha256", entry.requestHash());
        row.put("process_instance_id", entry.command().instanceId());
        row.put("start_command_type", "START");
        row.put("start_command_id", entry.start().commandId());
        row.put("start_request_sha256", entry.start().requestHash());
        row.put("release_key", entry.start().releaseKey());
        row.put("model_type", "BPMN");
        row.put("definition_key", entry.definitionKey());
        row.put("engine_definition_id", entry.engineDefinitionId());
        row.put("definition_sha256", entry.definitionSha256());
        row.put("business_key", entry.businessKey());
        row.put("correlation_id", entry.start().correlationId());
        row.put("expected_version", entry.command().expectedVersion());
        row.put("reason", entry.command().reason());
        row.put("accepted", entry.result().accepted());
        row.put("result_status", entry.result().status().name());
        row.put("result_version", entry.result().resultingVersion());
        row.put("result_finished_at", Timestamp.from(entry.result().finishedAt()));
        row.put("decided_at", Timestamp.from(entry.result().decidedAt()));
        row.put("agent_closure_scheduled", entry.result().linkedAgentClosureScheduled());
        row.put("base_state", baseState);
        row.put("base_resource_id", entry.command().instanceId());
        row.put("base_request_sha256", entry.requestHash());
        row.put("base_release_key", entry.start().releaseKey());
        row.put("base_correlation_id", entry.start().correlationId());
        row.put("start_state", "APPLIED");
        row.put("start_subject_type", entry.start().requester().subjectType().name());
        row.put("start_subject_issuer", entry.start().requester().issuerFingerprint());
        row.put("start_subject_id", entry.start().requester().subjectId());
        row.put("start_resource_id", entry.start().instanceId());
        row.put("start_release_key", entry.start().releaseKey());
        row.put("start_correlation_id", entry.start().correlationId());
        row.put("start_created_at", Timestamp.from(entry.start().createdAt()));
        row.put("bound_engine_definition_id", entry.engineDefinitionId());
        row.put("bound_definition_sha256", entry.definitionSha256());
        return row;
    }

    private static Map<String, Object> changed(Map<String, Object> row, String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put(key, value);
        return result;
    }

    private static <T> T inTransaction(DataSource source, Supplier<T> action) {
        TransactionSynchronizationManager.bindResource(source, new Object());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            return action.get();
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.unbindResource(source);
        }
    }

    private static void inject(Object target, Class<?> fieldType, Object value) {
        Field field = Arrays.stream(target.getClass().getDeclaredFields())
                .filter(candidate -> candidate.getType() == fieldType).findFirst().orElseThrow();
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class StoreHarness {
        private final DataSource source = mock(DataSource.class);
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final AtomicReference<String> querySql = new AtomicReference<>();
        private final AtomicReference<List<Object>> queryArguments = new AtomicReference<>();
        private final AtomicReference<String> updateSql = new AtomicReference<>();
        private final AtomicReference<List<Object>> updateArguments = new AtomicReference<>();
        private List<Map<String, Object>> queryRows = List.of();
        private int updateResult;
        private int updateCalls;

        private StoreHarness() {
            when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
            when(jdbc.queryForObject(anyString(), eq(Timestamp.class))).thenReturn(Timestamp.from(DECIDED_AT));
        }

        private ProcessCancellationReceiptStore store() {
            ProcessCancellationReceiptStore store = new ProcessCancellationReceiptStore(source, "process");
            inject(store, JdbcTemplate.class, jdbc);
            AtomicBoolean verified = Arrays.stream(store.getClass().getDeclaredFields())
                    .filter(field -> field.getType() == AtomicBoolean.class).findFirst().map(field -> {
                        try { field.setAccessible(true); return (AtomicBoolean) field.get(store); }
                        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                    }).orElseThrow();
            verified.set(true);
            installQuery();
            installUpdate();
            return store;
        }

        @SafeVarargs
        private final void rows(Map<String, Object>... rows) {
            queryRows = List.of(rows);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void installQuery() {
            doAnswer(invocation -> {
                querySql.set(invocation.getArgument(0));
                queryArguments.set(Arrays.asList(((Object[]) invocation.getRawArguments()[2]).clone()));
                RowMapper mapper = invocation.getArgument(1);
                List<Object> mapped = new ArrayList<>();
                int index = 0;
                for (Map<String, Object> row : queryRows) mapped.add(mapper.mapRow(resultSet(row), index++));
                return mapped;
            }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        }

        private void installUpdate() {
            doAnswer(invocation -> {
                updateCalls++;
                updateSql.set(invocation.getArgument(0));
                updateArguments.set(Arrays.asList(((Object[]) invocation.getRawArguments()[1]).clone()));
                return updateResult;
            }).when(jdbc).update(anyString(), any(Object[].class));
        }
    }

    private static ResultSet resultSet(Map<String, Object> row) throws java.sql.SQLException {
        AtomicReference<Object> last = new AtomicReference<>();
        ResultSet result = mock(ResultSet.class);
        when(result.getString(anyString())).thenAnswer(invocation -> {
            Object value = row.get(invocation.getArgument(0)); last.set(value);
            return value == null ? null : value.toString();
        });
        when(result.getLong(anyString())).thenAnswer(invocation -> {
            Object value = row.get(invocation.getArgument(0)); last.set(value);
            return value == null ? 0L : ((Number) value).longValue();
        });
        when(result.getBoolean(anyString())).thenAnswer(invocation -> {
            Object value = row.get(invocation.getArgument(0)); last.set(value);
            return value != null && (Boolean) value;
        });
        when(result.getTimestamp(anyString())).thenAnswer(invocation -> {
            Object value = row.get(invocation.getArgument(0)); last.set(value);
            if (value instanceof Instant instant) return Timestamp.from(instant);
            return (Timestamp) value;
        });
        when(result.getObject(anyString())).thenAnswer(invocation -> {
            Object value = row.get(invocation.getArgument(0)); last.set(value); return value;
        });
        when(result.wasNull()).thenAnswer(invocation -> last.get() == null);
        return result;
    }
}
