package top.egon.cola.platform.tianji.process.server.compensation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.worker.TransactionalWorkerHandler;
import top.egon.cola.platform.tianji.worker.WorkerExecutionConflictException;
import top.egon.cola.platform.tianji.worker.WorkerExecutionContext;
import top.egon.cola.platform.tianji.worker.WorkerExecutionDeadlineException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Isolated business handlers for the Worker compensation fixture. */
final class CompensationBusinessFixture {
    static final String SCHEMA = "compensation_fixture";
    static final String RESERVE_TOPIC = "reserveBusiness";
    static final String UNDO_TOPIC = "undoReservation";
    static final String SCHEMA_VERSION = "1";

    private static final String UNDO_ACTION = "UNDO_RESERVATION_V1";
    private static final String BUSINESS_OBJECT = SCHEMA + ".business_object";
    private static final String ORIGINAL_RECEIPT = SCHEMA + ".original_receipt";
    private static final String COMPENSATION_RECEIPT = SCHEMA + ".compensation_receipt";
    private static final String REJECT_ORIGINAL_UPDATE = SCHEMA + ".reject_original_receipt_update";

    private static final String SELECT_ORIGINAL = """
            SELECT source_id, tenant_id, operation_id, original_receipt_id, object_id, process_instance_id,
                   business_key, correlation_id, before_version, after_version, amount, before_reserved,
                   after_reserved, recorded_at
            FROM %s
            WHERE source_id = ? AND tenant_id = ? AND operation_id = ?
            """.formatted(ORIGINAL_RECEIPT);
    private static final String LOCK_OBJECT = """
            SELECT version, reserved_amount
            FROM %s
            WHERE source_id = ? AND tenant_id = ? AND object_id = ?
            FOR UPDATE
            """.formatted(BUSINESS_OBJECT);
    private static final String SELECT_COMPENSATION = """
            SELECT source_id, tenant_id, compensation_key, compensation_receipt_id, original_receipt_id,
                   operation_id, object_id, process_instance_id, business_key, correlation_id, undo_action,
                   before_version, after_version, before_reserved, after_reserved, recorded_at
            FROM %s
            WHERE source_id = ? AND tenant_id = ? AND compensation_key = ?
            """.formatted(COMPENSATION_RECEIPT);
    private static final String UPDATE_OBJECT = """
            UPDATE %s SET reserved_amount = ?, version = ?
            WHERE source_id = ? AND tenant_id = ? AND object_id = ? AND version = ? AND reserved_amount = ?
            RETURNING version
            """.formatted(BUSINESS_OBJECT);
    private static final String INSERT_ORIGINAL = """
            INSERT INTO %s (source_id, tenant_id, operation_id, original_receipt_id, object_id, process_instance_id,
                            business_key, correlation_id, before_version, after_version, amount, before_reserved,
                            after_reserved)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING recorded_at
            """.formatted(ORIGINAL_RECEIPT);
    private static final String INSERT_COMPENSATION = """
            INSERT INTO %s (source_id, tenant_id, compensation_key, compensation_receipt_id, original_receipt_id,
                            operation_id, object_id, process_instance_id, business_key, correlation_id, undo_action,
                            before_version, after_version, before_reserved, after_reserved)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING recorded_at
            """.formatted(COMPENSATION_RECEIPT);

    private final ExpectedOperationVO expected;

    CompensationBusinessFixture(ExpectedOperationVO expected, ValidationUtils validationUtils) {
        this.expected = Objects.requireNonNull(validationUtils, "validationUtils").validate(expected);
    }

    TransactionalWorkerHandler originalHandler() { return this::reserve; }

    TransactionalWorkerHandler undoHandler() { return this::undo; }

    private WorkerBusinessResultDTO reserve(WorkerExecutionContext context, JdbcOperations operations) {
        requireContext(context, RESERVE_TOPIC);
        JdbcTemplate jdbc = requireBoundReadCommitted(operations);
        OriginalReceiptVO original = findOriginal(jdbc, SELECT_ORIGINAL + "FOR UPDATE");
        if (original != null) {
            requireTime(jdbc, context.deadline());
            requireOriginalBinding(original);
            return new WorkerBusinessResultDTO(original.originalReceiptId(), Map.of());
        }

        BusinessObjectVO object = lockObject(jdbc);
        requireTime(jdbc, context.deadline());
        // READ_COMMITTED exposes a receipt committed while the object lock was pending.
        // The reread must stay unlocked because undo locks the receipt before the object.
        original = findOriginal(jdbc, SELECT_ORIGINAL);
        if (original != null) {
            requireOriginalBinding(original);
            return new WorkerBusinessResultDTO(original.originalReceiptId(), Map.of());
        }
        if (object.version() != expected.expectedVersion() || object.reserved() != expected.expectedReserved()) {
            throw new WorkerExecutionConflictException();
        }

        long nextVersion = Math.addExact(object.version(), 1);
        long nextReserved = Math.addExact(object.reserved(), expected.amount());
        String receiptId = ProcessRequestDigest.hash(List.of("worker-compensation-original-v1",
                expected.sourceId(), Long.toString(expected.tenantId()), expected.operationId().toString()), Map.of());
        updateObject(jdbc, object, nextVersion, nextReserved);
        requireRecordedAt(jdbc.queryForObject(INSERT_ORIGINAL, OffsetDateTime.class,
                expected.sourceId(), expected.tenantId(), expected.operationId(), receiptId, expected.objectId(),
                expected.processInstanceId(), expected.businessKey(), expected.correlationId(), object.version(),
                nextVersion, expected.amount(), object.reserved(), nextReserved));
        return new WorkerBusinessResultDTO(receiptId, Map.of());
    }

    private WorkerBusinessResultDTO undo(WorkerExecutionContext context, JdbcOperations operations) {
        requireContext(context, UNDO_TOPIC);
        JdbcTemplate jdbc = requireBoundReadCommitted(operations);
        OriginalReceiptVO original = findOriginal(jdbc, SELECT_ORIGINAL + "FOR UPDATE");
        if (original == null) throw new WorkerExecutionConflictException();
        requireTime(jdbc, context.deadline());
        requireOriginalBinding(original);

        String key = ProcessRequestDigest.hash(List.of("worker-compensation-v1", original.sourceId(),
                Long.toString(original.tenantId()), original.originalReceiptId(), UNDO_ACTION), Map.of());
        CompensationReceiptVO prior = atMostOne(jdbc.query(SELECT_COMPENSATION,
                (row, index) -> readCompensation(row), original.sourceId(), original.tenantId(), key));
        if (prior != null) {
            requireCompensationBinding(prior, original, key);
            return new WorkerBusinessResultDTO(prior.compensationReceiptId(), Map.of());
        }

        BusinessObjectVO object = lockObject(jdbc);
        requireTime(jdbc, context.deadline());
        if (object.version() != original.afterVersion() || object.reserved() != original.afterReserved()) {
            throw new WorkerExecutionConflictException();
        }

        long nextVersion = Math.addExact(object.version(), 1);
        updateObject(jdbc, object, nextVersion, original.beforeReserved());
        requireRecordedAt(jdbc.queryForObject(INSERT_COMPENSATION, OffsetDateTime.class,
                original.sourceId(), original.tenantId(), key, key, original.originalReceiptId(),
                original.operationId(), original.objectId(), original.processInstanceId(), original.businessKey(),
                original.correlationId(), UNDO_ACTION, object.version(), nextVersion, object.reserved(),
                original.beforeReserved()));
        return new WorkerBusinessResultDTO(key, Map.of());
    }

    private void requireContext(WorkerExecutionContext context, String topic) {
        if (context == null || context.scopeType() != WorkerScopeTypeEnum.BPMN || context.caseInstanceId() != null
                || !expected.sourceId().equals(context.sourceId()) || expected.tenantId() != context.tenantId()
                || !expected.processInstanceId().equals(context.processInstanceId())
                || !expected.businessKey().equals(context.businessKey())
                || !expected.correlationId().equals(context.correlationId())
                || !Map.of("operationId", expected.operationId().toString()).equals(context.input())
                || !topic.equals(context.topic()) || !SCHEMA_VERSION.equals(context.schemaVersion())
                || context.effectSafety() != WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT || context.deadline() == null) {
            throw new IllegalArgumentException("Worker compensation fixture context is invalid");
        }
    }

    private static JdbcTemplate requireBoundReadCommitted(JdbcOperations operations) {
        if (!(operations instanceof JdbcTemplate jdbc) || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Worker compensation fixture requires an active JDBC transaction");
        }
        var dataSource = jdbc.getDataSource();
        if (dataSource == null || !(TransactionSynchronizationManager.getResource(dataSource) instanceof ConnectionHolder holder)) {
            throw new IllegalStateException("Worker compensation fixture requires a bound JDBC connection");
        }
        try {
            Connection connection = holder.getConnection();
            if (connection == null || connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                throw new IllegalStateException("Worker compensation fixture requires READ_COMMITTED isolation");
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Worker compensation fixture could not verify JDBC isolation", failure);
        }
        return jdbc;
    }

    private static void requireTime(JdbcTemplate jdbc, Instant deadline) {
        OffsetDateTime now = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
        if (now == null) throw new IllegalStateException("Worker compensation fixture database clock is unavailable");
        if (!now.toInstant().isBefore(deadline)) throw new WorkerExecutionDeadlineException();
    }

    private OriginalReceiptVO findOriginal(JdbcTemplate jdbc, String query) {
        return atMostOne(jdbc.query(query, (row, index) -> readOriginal(row),
                expected.sourceId(), expected.tenantId(), expected.operationId()));
    }

    private BusinessObjectVO lockObject(JdbcTemplate jdbc) {
        BusinessObjectVO object = atMostOne(jdbc.query(LOCK_OBJECT,
                (row, index) -> new BusinessObjectVO(row.getLong("version"), row.getLong("reserved_amount")),
                expected.sourceId(), expected.tenantId(), expected.objectId()));
        if (object == null) throw new WorkerExecutionConflictException();
        return object;
    }

    private void updateObject(JdbcTemplate jdbc, BusinessObjectVO object, long nextVersion, long nextReserved) {
        Long version = atMostOne(jdbc.query(UPDATE_OBJECT, (row, index) -> row.getLong("version"),
                nextReserved, nextVersion, expected.sourceId(), expected.tenantId(), expected.objectId(),
                object.version(), object.reserved()));
        if (version == null || version != nextVersion) throw new WorkerExecutionConflictException();
    }

    private void requireOriginalBinding(OriginalReceiptVO receipt) {
        if (!expected.sourceId().equals(receipt.sourceId()) || expected.tenantId() != receipt.tenantId()
                || !expected.operationId().equals(receipt.operationId()) || !expected.objectId().equals(receipt.objectId())
                || !expected.processInstanceId().equals(receipt.processInstanceId())
                || !expected.businessKey().equals(receipt.businessKey())
                || !expected.correlationId().equals(receipt.correlationId())
                || receipt.originalReceiptId() == null || !receipt.originalReceiptId().matches("[0-9a-f]{64}")
                || receipt.beforeVersion() != expected.expectedVersion() || receipt.beforeReserved() != expected.expectedReserved()
                || receipt.amount() != expected.amount()
                || receipt.afterVersion() <= receipt.beforeVersion() || receipt.afterVersion() - receipt.beforeVersion() != 1
                || receipt.afterReserved() <= receipt.beforeReserved()
                || receipt.afterReserved() - receipt.beforeReserved() != receipt.amount()) {
            throw new WorkerExecutionConflictException();
        }
    }

    private static void requireCompensationBinding(CompensationReceiptVO receipt, OriginalReceiptVO original, String key) {
        if (!original.sourceId().equals(receipt.sourceId()) || original.tenantId() != receipt.tenantId()
                || !key.equals(receipt.compensationKey()) || !key.equals(receipt.compensationReceiptId())
                || !original.originalReceiptId().equals(receipt.originalReceiptId())
                || !original.operationId().equals(receipt.operationId()) || !original.objectId().equals(receipt.objectId())
                || !original.processInstanceId().equals(receipt.processInstanceId())
                || !original.businessKey().equals(receipt.businessKey())
                || !original.correlationId().equals(receipt.correlationId()) || !UNDO_ACTION.equals(receipt.undoAction())
                || receipt.beforeVersion() != original.afterVersion()
                || receipt.afterVersion() <= receipt.beforeVersion() || receipt.afterVersion() - receipt.beforeVersion() != 1
                || receipt.beforeReserved() != original.afterReserved() || receipt.afterReserved() != original.beforeReserved()) {
            throw new WorkerExecutionConflictException();
        }
    }

    private static <T> T atMostOne(List<T> rows) {
        if (rows.size() > 1) throw new WorkerExecutionConflictException();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static OriginalReceiptVO readOriginal(ResultSet row) throws SQLException {
        return new OriginalReceiptVO(row.getString("source_id"), row.getLong("tenant_id"),
                row.getObject("operation_id", UUID.class), row.getString("original_receipt_id"), row.getString("object_id"),
                row.getString("process_instance_id"), row.getString("business_key"), row.getString("correlation_id"),
                row.getLong("before_version"), row.getLong("after_version"), row.getLong("amount"),
                row.getLong("before_reserved"), row.getLong("after_reserved"),
                requireRecordedAt(row.getObject("recorded_at", OffsetDateTime.class)));
    }

    private static CompensationReceiptVO readCompensation(ResultSet row) throws SQLException {
        return new CompensationReceiptVO(row.getString("source_id"), row.getLong("tenant_id"),
                row.getString("compensation_key"), row.getString("compensation_receipt_id"),
                row.getString("original_receipt_id"), row.getObject("operation_id", UUID.class), row.getString("object_id"),
                row.getString("process_instance_id"), row.getString("business_key"), row.getString("correlation_id"),
                row.getString("undo_action"), row.getLong("before_version"), row.getLong("after_version"),
                row.getLong("before_reserved"), row.getLong("after_reserved"),
                requireRecordedAt(row.getObject("recorded_at", OffsetDateTime.class)));
    }

    private static Instant requireRecordedAt(OffsetDateTime value) {
        if (value == null) throw new WorkerExecutionConflictException();
        return value.toInstant();
    }

    static void createBusinessTables(JdbcTemplate bootstrap) {
        bootstrap.execute("""
                CREATE TABLE %s (
                    source_id varchar(128) COLLATE "C" NOT NULL,
                    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
                    object_id varchar(128) COLLATE "C" NOT NULL,
                    version bigint NOT NULL CHECK (version >= 0),
                    reserved_amount bigint NOT NULL CHECK (reserved_amount >= 0),
                    CONSTRAINT pk_comp_business_object PRIMARY KEY (source_id, tenant_id, object_id)
                )
                """.formatted(BUSINESS_OBJECT));
        bootstrap.execute("""
                CREATE TABLE %s (
                    source_id varchar(128) COLLATE "C" NOT NULL,
                    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
                    operation_id uuid NOT NULL,
                    original_receipt_id char(64) COLLATE "C" NOT NULL CHECK (original_receipt_id ~ '^[0-9a-f]{64}$'),
                    object_id varchar(128) COLLATE "C" NOT NULL,
                    process_instance_id varchar(255) COLLATE "C" NOT NULL,
                    business_key varchar(255) COLLATE "C" NOT NULL,
                    correlation_id varchar(128) COLLATE "C" NOT NULL,
                    before_version bigint NOT NULL CHECK (before_version >= 0),
                    after_version bigint NOT NULL CHECK (after_version = before_version + 1),
                    amount bigint NOT NULL CHECK (amount > 0),
                    before_reserved bigint NOT NULL CHECK (before_reserved >= 0),
                    after_reserved bigint NOT NULL CHECK (after_reserved >= 0 AND after_reserved = before_reserved + amount),
                    recorded_at timestamptz(6) NOT NULL DEFAULT clock_timestamp(),
                    CONSTRAINT pk_comp_original_receipt PRIMARY KEY (source_id, tenant_id, operation_id),
                    CONSTRAINT uk_comp_original_receipt_id UNIQUE (source_id, tenant_id, original_receipt_id),
                    FOREIGN KEY (source_id, tenant_id, object_id)
                        REFERENCES %s (source_id, tenant_id, object_id) ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.formatted(ORIGINAL_RECEIPT, BUSINESS_OBJECT));
        bootstrap.execute("""
                CREATE TABLE %s (
                    source_id varchar(128) COLLATE "C" NOT NULL,
                    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
                    compensation_key char(64) COLLATE "C" NOT NULL CHECK (compensation_key ~ '^[0-9a-f]{64}$'),
                    compensation_receipt_id char(64) COLLATE "C" NOT NULL
                        CHECK (compensation_receipt_id = compensation_key AND compensation_receipt_id ~ '^[0-9a-f]{64}$'),
                    original_receipt_id char(64) COLLATE "C" NOT NULL CHECK (original_receipt_id ~ '^[0-9a-f]{64}$'),
                    operation_id uuid NOT NULL,
                    object_id varchar(128) COLLATE "C" NOT NULL,
                    process_instance_id varchar(255) COLLATE "C" NOT NULL,
                    business_key varchar(255) COLLATE "C" NOT NULL,
                    correlation_id varchar(128) COLLATE "C" NOT NULL,
                    undo_action varchar(64) COLLATE "C" NOT NULL CHECK (undo_action = 'UNDO_RESERVATION_V1'),
                    before_version bigint NOT NULL CHECK (before_version >= 0),
                    after_version bigint NOT NULL CHECK (after_version = before_version + 1),
                    before_reserved bigint NOT NULL CHECK (before_reserved >= 0),
                    after_reserved bigint NOT NULL CHECK (after_reserved >= 0),
                    recorded_at timestamptz(6) NOT NULL DEFAULT clock_timestamp(),
                    CONSTRAINT pk_comp_compensation_receipt PRIMARY KEY (source_id, tenant_id, compensation_key),
                    CONSTRAINT uk_comp_original_action UNIQUE (source_id, tenant_id, original_receipt_id, undo_action),
                    FOREIGN KEY (source_id, tenant_id, original_receipt_id)
                        REFERENCES %s (source_id, tenant_id, original_receipt_id) ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.formatted(COMPENSATION_RECEIPT, ORIGINAL_RECEIPT));
        // The role's column UPDATE privilege permits row locks. Actual UPDATE statements must fail.
        bootstrap.execute("""
                CREATE FUNCTION %s() RETURNS trigger LANGUAGE plpgsql SET search_path TO pg_catalog AS $fixture$
                BEGIN
                    RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'Original compensation receipt is immutable';
                END;
                $fixture$
                """.formatted(REJECT_ORIGINAL_UPDATE));
        bootstrap.execute("""
                CREATE TRIGGER original_receipt_immutable_update BEFORE UPDATE ON %s
                FOR EACH STATEMENT EXECUTE FUNCTION %s()
                """.formatted(ORIGINAL_RECEIPT, REJECT_ORIGINAL_UPDATE));
        bootstrap.execute("ALTER TABLE " + ORIGINAL_RECEIPT + " ENABLE ALWAYS TRIGGER original_receipt_immutable_update");
    }

    record ExpectedOperationVO(
            @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") String sourceId,
            @Positive long tenantId,
            @NotBlank @Size(max = 255) String processInstanceId,
            @NotBlank @Size(max = 255) String businessKey,
            @NotBlank @Size(max = 128) String correlationId,
            @NotNull UUID operationId,
            @NotBlank @Size(max = 128) String objectId,
            @PositiveOrZero long expectedVersion,
            @PositiveOrZero long expectedReserved,
            @Positive long amount) {
        @Override
        public String toString() { return "ExpectedOperationVO[redacted]"; }
    }

    private record BusinessObjectVO(long version, long reserved) { }

    private record OriginalReceiptVO(String sourceId, long tenantId, UUID operationId, String originalReceiptId,
                                     String objectId, String processInstanceId, String businessKey, String correlationId,
                                     long beforeVersion, long afterVersion, long amount, long beforeReserved,
                                     long afterReserved, Instant recordedAt) {
        @Override
        public String toString() { return "OriginalReceiptVO[redacted]"; }
    }

    private record CompensationReceiptVO(String sourceId, long tenantId, String compensationKey,
                                         String compensationReceiptId, String originalReceiptId, UUID operationId,
                                         String objectId, String processInstanceId, String businessKey,
                                         String correlationId, String undoAction, long beforeVersion, long afterVersion,
                                         long beforeReserved, long afterReserved, Instant recordedAt) {
        @Override
        public String toString() { return "CompensationReceiptVO[redacted]"; }
    }
}
