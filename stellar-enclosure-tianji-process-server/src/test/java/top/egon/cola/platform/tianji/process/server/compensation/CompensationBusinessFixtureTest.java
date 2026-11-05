package top.egon.cola.platform.tianji.process.server.compensation;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.process.server.compensation.CompensationBusinessFixture.ExpectedOperationVO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.worker.TransactionalWorkerHandler;
import top.egon.cola.platform.tianji.worker.WorkerExecutionConflictException;
import top.egon.cola.platform.tianji.worker.WorkerExecutionContext;
import top.egon.cola.platform.tianji.worker.WorkerExecutionDeadlineException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum.READ_ONLY;
import static top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT;
import static top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum.BPMN;
import static top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum.CMMN;

class CompensationBusinessFixtureTest {
    private static final String SOURCE = "integration-process";
    private static final long TENANT = 20;
    private static final String PROCESS = "process-one";
    private static final String BUSINESS = "business-one";
    private static final String CORRELATION = "trace-one";
    private static final String OBJECT = "reservation-one";
    private static final UUID OPERATION = UUID.fromString("5b71ec2e-72a5-4c40-9bea-c35b19cb89ed");
    private static final String RESERVE = "reserveBusiness";
    private static final String UNDO = "undoReservation";
    private static final String ACTION = "UNDO_RESERVATION_V1";
    private static final String PERSISTED_ORIGINAL_ID = "f".repeat(64);
    private static final String NEW_ORIGINAL_ID = ProcessRequestDigest.hash(
            List.of("worker-compensation-original-v1", SOURCE, Long.toString(TENANT), OPERATION.toString()), Map.of());
    private static final String COMPENSATION_KEY = ProcessRequestDigest.hash(
            List.of("worker-compensation-v1", SOURCE, Long.toString(TENANT), PERSISTED_ORIGINAL_ID, ACTION), Map.of());
    private static final Instant DEADLINE = Instant.parse("2026-11-05T04:00:00.123456Z");
    private static final OffsetDateTime BEFORE_DEADLINE = DEADLINE.minusNanos(1_000).atOffset(ZoneOffset.ofHours(8));
    private static final OffsetDateTime RECORDED_AT = DEADLINE.minusSeconds(1).atOffset(ZoneOffset.ofHoursMinutes(5, 30));
    private static final ExpectedOperationVO EXPECTED = enrollment(10, 7);
    private static final Object[] OPERATION_BINDING = {SOURCE, TENANT, OPERATION};
    private static final Object[] OBJECT_BINDING = {SOURCE, TENANT, OBJECT};
    private static final Object[] COMPENSATION_BINDING = {SOURCE, TENANT, COMPENSATION_KEY};
    private static final Rows NO_ROWS = new Rows(List.of());

    private static ValidatorFactory validatorFactory;
    private static ValidationUtils validation;

    @BeforeAll
    static void open_validators() {
        validatorFactory = assertDoesNotThrow(Validation::buildDefaultValidatorFactory);
        assertNotNull(validatorFactory);
        validation = assertDoesNotThrow(() -> new ValidationUtils(validatorFactory.getValidator()));
        assertNotNull(validation);
    }

    @AfterAll
    static void close_validators() {
        if (validatorFactory != null) validatorFactory.close();
    }

    @Test
    void compensation_fixture_is_present() {
        assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.process.server.compensation.CompensationBusinessFixture"));
    }

    @Test
    void enrollment_uses_actual_jakarta_default_constraints() {
        assertSame(EXPECTED, assertDoesNotThrow(() -> validation.validate(EXPECTED, Default.class)));
        var invalid = new ExpectedOperationVO(" invalid", 0, "", "", "", null, "", -1, -1, 0);
        var failure = assertThrows(ConstraintViolationException.class,
                () -> new CompensationBusinessFixture(invalid, validation));
        var properties = failure.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
        assertEquals(Set.of("sourceId", "tenantId", "processInstanceId", "businessKey", "correlationId",
                "operationId", "objectId", "expectedVersion", "expectedReserved", "amount"), properties);
    }

    @Test
    void both_strategies_reject_each_context_mismatch_before_any_jdbc_or_connection() throws SQLException {
        var fixture = fixture(EXPECTED);
        var original = original(fixture);
        var undo = undo(fixture);
        for (var mismatch : List.of("scope", "case", "source", "tenant", "process", "business", "correlation",
                "missing-input", "wrong-input", "noncanonical-input", "extra-input", "topic", "other-strategy", "schema", "effect", "deadline")) {
            try (var rig = new Rig().bind()) {
                assertThrows(IllegalArgumentException.class, () -> original.execute(context(RESERVE, 1, mismatch), rig.jdbc),
                        "original " + mismatch);
                verifyNoInteractions(rig.jdbc, rig.dataSource, rig.connection);
            }
            try (var rig = new Rig().bind()) {
                assertThrows(IllegalArgumentException.class, () -> undo.execute(context(UNDO, 1, mismatch), rig.jdbc),
                        "undo " + mismatch);
                verifyNoInteractions(rig.jdbc, rig.dataSource, rig.connection);
            }
        }
    }

    @Test
    void only_the_jdbc_templates_current_bound_read_committed_connection_may_be_used() throws SQLException {
        var handler = original(fixture(EXPECTED));
        var context = context(RESERVE, 1);
        var operations = mock(JdbcOperations.class);
        assertThrows(IllegalStateException.class, () -> handler.execute(context, operations));
        verifyNoInteractions(operations);

        try (var rig = new Rig().bind(false, false, null)) {
            assertThrows(IllegalStateException.class, () -> handler.execute(context, rig.jdbc));
            verifyNoInteractions(rig.dataSource, rig.connection);
        }
        try (var rig = new Rig().bind(true, false, null)) {
            assertThrows(IllegalStateException.class, () -> handler.execute(context, rig.jdbc));
            verifyNoInteractions(rig.dataSource, rig.connection);
        }
        try (var rig = new Rig().bind(true, true, new Object())) {
            assertThrows(IllegalStateException.class, () -> handler.execute(context, rig.jdbc));
            verifyNoInteractions(rig.dataSource, rig.connection);
        }
        var emptyHolder = mock(ConnectionHolder.class);
        try (var rig = new Rig().bind(true, true, emptyHolder)) {
            assertThrows(IllegalStateException.class, () -> handler.execute(context, rig.jdbc));
            verify(emptyHolder).getConnection();
            verifyNoInteractions(rig.dataSource, rig.connection);
        }
        try (var rig = new Rig().bind(true, false, mock(ConnectionHolder.class))) {
            assertThrows(IllegalStateException.class, () -> handler.execute(context, rig.jdbc));
            verifyNoInteractions(rig.dataSource, rig.connection, rig.boundResource);
        }
        for (int isolation : List.of(Connection.TRANSACTION_NONE, Connection.TRANSACTION_REPEATABLE_READ,
                Connection.TRANSACTION_SERIALIZABLE)) {
            try (var rig = new Rig(isolation).bind()) {
                assertThrows(IllegalStateException.class, () -> handler.execute(context, rig.jdbc));
                rig.verifyOnlyBoundConnection();
            }
        }
        try (var rig = new Rig().bind()) {
            var unavailable = new SQLException("injected connection metadata failure");
            when(rig.connection.getTransactionIsolation()).thenThrow(unavailable);
            var failure = assertThrows(IllegalStateException.class, () -> handler.execute(context, rig.jdbc));
            assertSame(unavailable, failure.getCause());
            rig.verifyOnlyBoundConnection();
        }
    }

    @Test
    void first_original_reserves_once_and_inserts_the_exact_business_receipt() throws SQLException {
        var handler = original(fixture(EXPECTED));
        try (var rig = new Rig().bind()) {
            rig.originalWithNoReceipt(EXPECTED);
            rig.cas(10, 7, 11, 10, rows(Map.of("version", 11L)));
            rig.insertOriginal(EXPECTED, RECORDED_AT);
            assertResult(NEW_ORIGINAL_ID, () -> handler.execute(context(RESERVE, 1), rig.jdbc));
            rig.verifyComplete();
        }
    }

    @Test
    void original_replays_the_persisted_receipt_without_querying_object_state() throws SQLException {
        var handler = original(fixture(EXPECTED));
        try (var rig = new Rig().bind()) {
            rig.expect(Sql.LOCK_ORIGINAL, rows(originalRow(EXPECTED)), OPERATION_BINDING);
            rig.expect(Sql.CLOCK, BEFORE_DEADLINE);
            assertResult(PERSISTED_ORIGINAL_ID, () -> handler.execute(context(RESERVE, 2), rig.jdbc));
            rig.verifyComplete();
        }
    }

    @Test
    void original_lock_window_rereads_plain_receipt_before_comparing_the_new_object_version() throws SQLException {
        var handler = original(fixture(EXPECTED));
        try (var rig = new Rig().bind()) {
            rig.expect(Sql.LOCK_ORIGINAL, NO_ROWS, OPERATION_BINDING);
            rig.expect(Sql.LOCK_OBJECT, objectRow(99, 123), OBJECT_BINDING);
            rig.expect(Sql.CLOCK, BEFORE_DEADLINE);
            rig.expect(Sql.READ_ORIGINAL, rows(originalRow(EXPECTED)), OPERATION_BINDING);
            assertResult(PERSISTED_ORIGINAL_ID, () -> handler.execute(context(RESERVE, 2), rig.jdbc));
            rig.verifyComplete();
        }
    }

    @Test
    void original_refuses_drift_and_conflicting_receipt_data_before_writing() throws SQLException {
        var handler = original(fixture(EXPECTED));
        try (var rig = new Rig().bind()) {
            rig.expect(Sql.LOCK_ORIGINAL, NO_ROWS, OPERATION_BINDING);
            rig.expect(Sql.LOCK_OBJECT, objectRow(11, 7), OBJECT_BINDING);
            rig.expect(Sql.CLOCK, BEFORE_DEADLINE);
            rig.expect(Sql.READ_ORIGINAL, NO_ROWS, OPERATION_BINDING);
            assertThrows(WorkerExecutionConflictException.class, () -> handler.execute(context(RESERVE, 1), rig.jdbc));
            rig.verifyComplete();
        }
        try (var rig = new Rig().bind()) {
            var wrongBinding = originalRow(EXPECTED);
            wrongBinding.put("business_key", "different-business");
            rig.expect(Sql.LOCK_ORIGINAL, rows(wrongBinding), OPERATION_BINDING);
            rig.expect(Sql.CLOCK, BEFORE_DEADLINE);
            assertThrows(WorkerExecutionConflictException.class, () -> handler.execute(context(RESERVE, 2), rig.jdbc));
            rig.verifyComplete();
        }
    }

    @Test
    void original_cas_insert_and_arithmetic_failures_escape_the_handler() throws SQLException {
        var handler = original(fixture(EXPECTED));
        for (var failedCas : List.of(NO_ROWS, rows(Map.of("version", 12L)),
                new Rows(List.of(Map.of("version", 11L), Map.of("version", 11L))))) {
            try (var rig = new Rig().bind()) {
                rig.originalWithNoReceipt(EXPECTED);
                rig.cas(10, 7, 11, 10, failedCas);
                assertThrows(WorkerExecutionConflictException.class, () -> handler.execute(context(RESERVE, 1), rig.jdbc));
                rig.verifyComplete();
            }
        }
        try (var rig = new Rig().bind()) {
            var failure = new DataIntegrityViolationException("injected original insert failure");
            rig.originalWithNoReceipt(EXPECTED);
            rig.cas(10, 7, 11, 10, rows(Map.of("version", 11L)));
            rig.insertOriginal(EXPECTED, failure);
            assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                    () -> handler.execute(context(RESERVE, 1), rig.jdbc)));
            rig.verifyComplete();
        }
        for (var tooLarge : List.of(enrollment(Long.MAX_VALUE, 7), enrollment(10, Long.MAX_VALUE))) {
            var overflow = original(fixture(tooLarge));
            try (var rig = new Rig().bind()) {
                rig.originalWithNoReceipt(tooLarge);
                assertThrows(ArithmeticException.class, () -> overflow.execute(context(RESERVE, 1), rig.jdbc));
                rig.verifyComplete();
            }
        }
    }

    @Test
    void original_checks_business_clock_after_waiting_for_either_required_lock() throws SQLException {
        var handler = original(fixture(EXPECTED));
        try (var rig = new Rig().bind()) {
            rig.expect(Sql.LOCK_ORIGINAL, rows(originalRow(EXPECTED)), OPERATION_BINDING);
            rig.expect(Sql.CLOCK, DEADLINE.atOffset(ZoneOffset.ofHours(-7)));
            assertThrows(WorkerExecutionDeadlineException.class, () -> handler.execute(context(RESERVE, 1), rig.jdbc));
            rig.verifyComplete();
        }
        try (var rig = new Rig().bind()) {
            rig.expect(Sql.LOCK_ORIGINAL, NO_ROWS, OPERATION_BINDING);
            rig.expect(Sql.LOCK_OBJECT, objectRow(10, 7), OBJECT_BINDING);
            rig.expect(Sql.CLOCK, DEADLINE.atOffset(ZoneOffset.ofHours(8)));
            assertThrows(WorkerExecutionDeadlineException.class, () -> handler.execute(context(RESERVE, 1), rig.jdbc));
            rig.verifyComplete();
        }
    }

    @Test
    void first_undo_derives_its_key_from_the_persisted_original_and_restores_reserved_at_the_next_version() throws SQLException {
        var handler = undo(fixture(EXPECTED));
        try (var rig = new Rig().bind()) {
            rig.undoWithNoReceipt(EXPECTED);
            rig.cas(11, 10, 12, 7, rows(Map.of("version", 12L)));
            rig.insertCompensation(RECORDED_AT);
            assertResult(COMPENSATION_KEY, () -> handler.execute(context(UNDO, 3), rig.jdbc));
            rig.verifyComplete();
        }
    }

    @Test
    void new_technical_undo_tasks_replay_the_same_business_receipt_before_any_object_lookup() throws SQLException {
        var handler = undo(fixture(EXPECTED));
        for (int attempt : List.of(3, 4)) {
            try (var rig = new Rig().bind()) {
                rig.expect(Sql.LOCK_ORIGINAL, rows(originalRow(EXPECTED)), OPERATION_BINDING);
                rig.expect(Sql.CLOCK, BEFORE_DEADLINE.withOffsetSameInstant(ZoneOffset.ofHours(-7)));
                rig.expect(Sql.READ_COMPENSATION, rows(compensationRow()), COMPENSATION_BINDING);
                assertResult(COMPENSATION_KEY, () -> handler.execute(context(UNDO, attempt), rig.jdbc));
                rig.verifyComplete();
            }
        }
    }

    @Test
    void undo_rejects_missing_or_invalid_receipts_and_objects_without_inventing_a_business_result() throws SQLException {
        var handler = undo(fixture(EXPECTED));
        try (var rig = new Rig().bind()) {
            rig.expect(Sql.LOCK_ORIGINAL, NO_ROWS, OPERATION_BINDING);
            assertThrows(WorkerExecutionConflictException.class, () -> handler.execute(context(UNDO, 3), rig.jdbc));
            rig.verifyComplete();
        }
        try (var rig = new Rig().bind()) {
            var badOriginal = originalRow(EXPECTED);
            badOriginal.put("after_reserved", 9L);
            rig.expect(Sql.LOCK_ORIGINAL, rows(badOriginal), OPERATION_BINDING);
            rig.expect(Sql.CLOCK, BEFORE_DEADLINE);
            assertThrows(WorkerExecutionConflictException.class, () -> handler.execute(context(UNDO, 3), rig.jdbc));
            rig.verifyComplete();
        }
        for (var corrupt : Map.of("object_id", "different-object", "undo_action", "OTHER", "after_version", 11L,
                "after_reserved", 8L, "compensation_receipt_id", "a".repeat(64)).entrySet()) {
            try (var rig = new Rig().bind()) {
                var badPrior = compensationRow();
                badPrior.put(corrupt.getKey(), corrupt.getValue());
                rig.expect(Sql.LOCK_ORIGINAL, rows(originalRow(EXPECTED)), OPERATION_BINDING);
                rig.expect(Sql.CLOCK, BEFORE_DEADLINE);
                rig.expect(Sql.READ_COMPENSATION, rows(badPrior), COMPENSATION_BINDING);
                assertThrows(WorkerExecutionConflictException.class, () -> handler.execute(context(UNDO, 4), rig.jdbc),
                        corrupt.getKey());
                rig.verifyComplete();
            }
        }
        try (var rig = new Rig().bind()) {
            rig.expect(Sql.LOCK_ORIGINAL, rows(originalRow(EXPECTED)), OPERATION_BINDING);
            rig.expect(Sql.CLOCK, BEFORE_DEADLINE);
            rig.expect(Sql.READ_COMPENSATION, NO_ROWS, COMPENSATION_BINDING);
            rig.expect(Sql.LOCK_OBJECT, objectRow(12, 10), OBJECT_BINDING);
            rig.expect(Sql.CLOCK, BEFORE_DEADLINE);
            assertThrows(WorkerExecutionConflictException.class, () -> handler.execute(context(UNDO, 4), rig.jdbc));
            rig.verifyComplete();
        }
    }

    @Test
    void undo_enforces_microsecond_deadline_equality_after_the_original_or_object_lock() throws SQLException {
        var handler = undo(fixture(EXPECTED));
        try (var rig = new Rig().bind()) {
            rig.expect(Sql.LOCK_ORIGINAL, rows(originalRow(EXPECTED)), OPERATION_BINDING);
            rig.expect(Sql.CLOCK, DEADLINE.atOffset(ZoneOffset.ofHours(-7)));
            assertThrows(WorkerExecutionDeadlineException.class, () -> handler.execute(context(UNDO, 3), rig.jdbc));
            rig.verifyComplete();
        }
        try (var rig = new Rig().bind()) {
            rig.expect(Sql.LOCK_ORIGINAL, rows(originalRow(EXPECTED)), OPERATION_BINDING);
            rig.expect(Sql.CLOCK, BEFORE_DEADLINE);
            rig.expect(Sql.READ_COMPENSATION, NO_ROWS, COMPENSATION_BINDING);
            rig.expect(Sql.LOCK_OBJECT, objectRow(11, 10), OBJECT_BINDING);
            rig.expect(Sql.CLOCK, DEADLINE.atOffset(ZoneOffset.ofHours(8)));
            assertThrows(WorkerExecutionDeadlineException.class, () -> handler.execute(context(UNDO, 3), rig.jdbc));
            rig.verifyComplete();
        }
    }

    @Test
    void undo_cas_insert_and_version_overflow_failures_escape_the_handler() throws SQLException {
        var handler = undo(fixture(EXPECTED));
        try (var rig = new Rig().bind()) {
            rig.undoWithNoReceipt(EXPECTED);
            rig.cas(11, 10, 12, 7, NO_ROWS);
            assertThrows(WorkerExecutionConflictException.class, () -> handler.execute(context(UNDO, 3), rig.jdbc));
            rig.verifyComplete();
        }
        try (var rig = new Rig().bind()) {
            var failure = new DataIntegrityViolationException("injected compensation insert failure");
            rig.undoWithNoReceipt(EXPECTED);
            rig.cas(11, 10, 12, 7, rows(Map.of("version", 12L)));
            rig.insertCompensation(failure);
            assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                    () -> handler.execute(context(UNDO, 3), rig.jdbc)));
            rig.verifyComplete();
        }
        var tooLarge = enrollment(Long.MAX_VALUE - 1, 7);
        var overflow = undo(fixture(tooLarge));
        try (var rig = new Rig().bind()) {
            rig.undoWithNoReceipt(tooLarge);
            assertThrows(ArithmeticException.class, () -> overflow.execute(context(UNDO, 3), rig.jdbc));
            rig.verifyComplete();
        }
    }

    private static CompensationBusinessFixture fixture(ExpectedOperationVO expected) {
        var fixture = assertDoesNotThrow(() -> new CompensationBusinessFixture(expected, validation));
        assertNotNull(fixture);
        return fixture;
    }

    private static TransactionalWorkerHandler original(CompensationBusinessFixture fixture) {
        var handler = assertDoesNotThrow(fixture::originalHandler);
        assertNotNull(handler);
        return handler;
    }

    private static TransactionalWorkerHandler undo(CompensationBusinessFixture fixture) {
        var handler = assertDoesNotThrow(fixture::undoHandler);
        assertNotNull(handler);
        return handler;
    }

    private static void assertResult(String receiptId, ThrowingSupplier<WorkerBusinessResultDTO> call) {
        var result = assertDoesNotThrow(call);
        assertNotNull(result);
        assertEquals(new WorkerBusinessResultDTO(receiptId, Map.of()), result);
    }

    private static ExpectedOperationVO enrollment(long expectedVersion, long expectedReserved) {
        return new ExpectedOperationVO(SOURCE, TENANT, PROCESS, BUSINESS, CORRELATION, OPERATION, OBJECT,
                expectedVersion, expectedReserved, 3);
    }

    private static WorkerExecutionContext context(String topic, int attempt) {
        return context(topic, attempt, "");
    }

    private static WorkerExecutionContext context(String topic, int attempt, String mismatch) {
        var input = switch (mismatch) {
            case "missing-input" -> Map.<String, Object>of();
            case "wrong-input" -> Map.<String, Object>of("operationId", "00000000-0000-4000-8000-000000000000");
            case "noncanonical-input" -> Map.<String, Object>of("operationId", OPERATION.toString().toUpperCase(Locale.ROOT));
            case "extra-input" -> Map.<String, Object>of("operationId", OPERATION.toString(), "receipt", PERSISTED_ORIGINAL_ID);
            default -> Map.<String, Object>of("operationId", OPERATION.toString());
        };
        String contextTopic = switch (mismatch) {
            case "topic" -> "different-topic";
            case "other-strategy" -> RESERVE.equals(topic) ? UNDO : RESERVE;
            default -> topic;
        };
        return new WorkerExecutionContext(
                "source".equals(mismatch) ? "different-source" : SOURCE, "tenant".equals(mismatch) ? TENANT + 1 : TENANT,
                "task-" + attempt, "activity-" + attempt, "scope".equals(mismatch) ? CMMN : BPMN,
                "process".equals(mismatch) ? "different-process" : PROCESS, "case".equals(mismatch) ? "case-one" : null,
                contextTopic, "schema".equals(mismatch) ? "2" : "1",
                "effect".equals(mismatch) ? READ_ONLY : TRANSACTIONAL_RECEIPT,
                "business".equals(mismatch) ? "different-business" : BUSINESS,
                "correlation".equals(mismatch) ? "different-correlation" : CORRELATION,
                "technical-key-" + attempt, "deadline".equals(mismatch) ? null : DEADLINE, input);
    }

    private static Map<String, Object> originalRow(ExpectedOperationVO expected) {
        var row = bindingRow();
        row.put("original_receipt_id", PERSISTED_ORIGINAL_ID);
        row.put("before_version", expected.expectedVersion());
        row.put("after_version", Math.addExact(expected.expectedVersion(), 1));
        row.put("amount", expected.amount());
        row.put("before_reserved", expected.expectedReserved());
        row.put("after_reserved", Math.addExact(expected.expectedReserved(), expected.amount()));
        row.put("recorded_at", RECORDED_AT);
        return row;
    }

    private static Map<String, Object> compensationRow() {
        var row = bindingRow();
        row.put("compensation_key", COMPENSATION_KEY);
        row.put("compensation_receipt_id", COMPENSATION_KEY);
        row.put("original_receipt_id", PERSISTED_ORIGINAL_ID);
        row.put("undo_action", ACTION);
        row.put("before_version", 11L);
        row.put("after_version", 12L);
        row.put("before_reserved", 10L);
        row.put("after_reserved", 7L);
        row.put("recorded_at", RECORDED_AT.withOffsetSameInstant(ZoneOffset.ofHours(-7)));
        return row;
    }

    private static Map<String, Object> bindingRow() {
        return new HashMap<>(Map.of("source_id", SOURCE, "tenant_id", TENANT, "operation_id", OPERATION,
                "object_id", OBJECT, "process_instance_id", PROCESS, "business_key", BUSINESS, "correlation_id", CORRELATION));
    }

    private static Rows objectRow(long version, long reserved) {
        return rows(Map.of("version", version, "reserved_amount", reserved));
    }

    private static Rows rows(Map<String, Object> row) {
        return new Rows(List.of(row));
    }

    private record Rows(List<Map<String, Object>> values) { }

    private enum Sql {
        CLOCK("queryForObject", "select clock_timestamp()", null, false),
        LOCK_ORIGINAL("query", "select ", "original_receipt", true),
        READ_ORIGINAL("query", "select ", "original_receipt", false),
        LOCK_OBJECT("query", "select ", "business_object", true),
        READ_COMPENSATION("query", "select ", "compensation_receipt", false),
        CAS_OBJECT("query", "update compensation_fixture.", "business_object", false),
        INSERT_ORIGINAL("queryForObject", "insert into compensation_fixture.", "original_receipt", false),
        INSERT_COMPENSATION("queryForObject", "insert into compensation_fixture.", "compensation_receipt", false);

        private final String method;
        private final String prefix;
        private final String table;
        private final boolean locks;

        Sql(String method, String prefix, String table, boolean locks) {
            this.method = method;
            this.prefix = prefix;
            this.table = table;
            this.locks = locks;
        }

        void verify(String actualMethod, String actualSql) {
            String sql = actualSql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            assertEquals(method, actualMethod, name());
            assertTrue(sql.startsWith(prefix), name() + ": " + sql);
            assertEquals(locks, sql.contains("for update"), name() + ": " + sql);
            if (table == null) {
                assertEquals(prefix, sql);
            } else if (sql.startsWith("select ")) {
                assertTrue(sql.contains(" from compensation_fixture." + table + " where "), name() + ": " + sql);
                requirePredicate(sql, "source_id", "tenant_id", switch (this) {
                    case LOCK_ORIGINAL, READ_ORIGINAL -> "operation_id";
                    case LOCK_OBJECT -> "object_id";
                    case READ_COMPENSATION -> "compensation_key";
                    default -> throw new AssertionError(this);
                });
            } else {
                assertTrue(sql.startsWith(prefix + table), name() + ": " + sql);
                if (this == CAS_OBJECT) {
                    requirePredicate(sql, "source_id", "tenant_id", "object_id", "version", "reserved_amount");
                    assertTrue(sql.endsWith("returning version"), sql);
                } else {
                    assertTrue(sql.endsWith("returning recorded_at"), sql);
                }
            }
        }

        private static void requirePredicate(String sql, String... columns) {
            int whereIndex = sql.indexOf(" where ");
            assertTrue(whereIndex >= 0, sql);
            String predicate = sql.substring(whereIndex).replace(" ", "");
            for (String column : columns) assertTrue(predicate.contains(column + "=?"), sql);
        }
    }

    private record Call(Sql sql, List<Object> parameters, Object response) { }

    private static final class Rig implements AutoCloseable {
        private final ArrayDeque<Call> calls = new ArrayDeque<>();
        private final DataSource dataSource = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class, this::answerSql);
        private DataSource boundSource;
        private Object boundResource;

        Rig() throws SQLException {
            this(Connection.TRANSACTION_READ_COMMITTED);
        }

        Rig(int isolation) throws SQLException {
            when(connection.getTransactionIsolation()).thenReturn(isolation);
        }

        Rig bind() {
            return bind(true, true, new ConnectionHolder(connection));
        }

        Rig bind(boolean active, boolean sameDataSource, Object holder) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            boundResource = holder;
            if (holder != null) {
                boundSource = sameDataSource ? dataSource : mock(DataSource.class);
                TransactionSynchronizationManager.bindResource(boundSource, holder);
            }
            TransactionSynchronizationManager.setActualTransactionActive(active);
            return this;
        }

        void expect(Sql sql, Object response, Object... args) {
            calls.add(new Call(sql, List.copyOf(Arrays.asList(args)), response));
        }

        void originalWithNoReceipt(ExpectedOperationVO expected) {
            expect(Sql.LOCK_ORIGINAL, NO_ROWS, OPERATION_BINDING);
            expect(Sql.LOCK_OBJECT, objectRow(expected.expectedVersion(), expected.expectedReserved()), OBJECT_BINDING);
            expect(Sql.CLOCK, BEFORE_DEADLINE);
            expect(Sql.READ_ORIGINAL, NO_ROWS, OPERATION_BINDING);
        }

        void undoWithNoReceipt(ExpectedOperationVO expected) {
            expect(Sql.LOCK_ORIGINAL, rows(originalRow(expected)), OPERATION_BINDING);
            expect(Sql.CLOCK, BEFORE_DEADLINE);
            expect(Sql.READ_COMPENSATION, NO_ROWS, COMPENSATION_BINDING);
            expect(Sql.LOCK_OBJECT, objectRow(Math.addExact(expected.expectedVersion(), 1),
                    Math.addExact(expected.expectedReserved(), expected.amount())), OBJECT_BINDING);
            expect(Sql.CLOCK, BEFORE_DEADLINE);
        }

        void cas(long oldVersion, long oldReserved, long newVersion, long newReserved, Rows response) {
            expect(Sql.CAS_OBJECT, response, newReserved, newVersion, SOURCE, TENANT, OBJECT, oldVersion, oldReserved);
        }

        void insertOriginal(ExpectedOperationVO expected, Object response) {
            expect(Sql.INSERT_ORIGINAL, response, SOURCE, TENANT, OPERATION, NEW_ORIGINAL_ID, OBJECT, PROCESS, BUSINESS,
                    CORRELATION, expected.expectedVersion(), Math.addExact(expected.expectedVersion(), 1),
                    expected.amount(), expected.expectedReserved(), Math.addExact(expected.expectedReserved(), expected.amount()));
        }

        void insertCompensation(Object response) {
            expect(Sql.INSERT_COMPENSATION, response, SOURCE, TENANT, COMPENSATION_KEY, COMPENSATION_KEY,
                    PERSISTED_ORIGINAL_ID, OPERATION, OBJECT, PROCESS, BUSINESS, CORRELATION, ACTION, 11L, 12L, 10L, 7L);
        }

        private Object answerSql(InvocationOnMock invocation) throws SQLException {
            String method = invocation.getMethod().getName();
            if ("getDataSource".equals(method)) return dataSource;

            var call = calls.poll();
            var arguments = invocation.getRawArguments();
            assertNotNull(call, () -> "unexpected JDBC call: " + method + " " + Arrays.toString(arguments));
            call.sql().verify(method, (String) arguments[0]);
            var parameters = arguments.length == 3 ? Arrays.asList((Object[]) arguments[2]) : List.of();
            assertEquals(call.parameters(), parameters, call.sql().name());
            if (call.response() instanceof Rows rows) {
                var mapper = (RowMapper<?>) arguments[1];
                var result = new ArrayList<>();
                for (var row : rows.values()) result.add(mapper.mapRow(resultSet(row), result.size()));
                return result;
            }
            assertEquals(OffsetDateTime.class, arguments[1], call.sql().name());
            if (call.response() instanceof RuntimeException failure) throw failure;
            return call.response();
        }

        private static ResultSet resultSet(Map<String, Object> values) {
            return mock(ResultSet.class, invocation -> {
                String column = invocation.getArgument(0);
                assertTrue(values.containsKey(column), () -> "unexpected row column: " + column);
                Object value = values.get(column);
                return switch (invocation.getMethod().getName()) {
                    case "getString" -> assertInstanceOf(String.class, value);
                    case "getLong" -> assertInstanceOf(Long.class, value);
                    case "getObject" -> {
                        Class<?> type = invocation.getArgument(1);
                        assertEquals("recorded_at".equals(column) ? OffsetDateTime.class : UUID.class, type, column);
                        yield assertInstanceOf(type, value);
                    }
                    default -> throw new AssertionError("unexpected ResultSet method: " + invocation.getMethod());
                };
            });
        }

        void verifyComplete() throws SQLException {
            assertTrue(calls.isEmpty(), () -> "unused JDBC expectations: " + calls.stream().map(Call::sql).toList());
            verifyOnlyBoundConnection();
        }

        void verifyOnlyBoundConnection() throws SQLException {
            verify(connection).getTransactionIsolation();
            verifyNoMoreInteractions(connection);
            verifyNoInteractions(dataSource);
        }

        @Override
        public void close() {
            try {
                if (boundSource != null) TransactionSynchronizationManager.unbindResource(boundSource);
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        }
    }
}
