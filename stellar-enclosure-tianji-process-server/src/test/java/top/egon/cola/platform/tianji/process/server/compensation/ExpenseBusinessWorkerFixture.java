package top.egon.cola.platform.tianji.process.server.compensation;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerClient;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerContractDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.worker.JdbcWorkerExecutionStore;
import top.egon.cola.platform.tianji.worker.RemoteWorkerPoller;
import top.egon.cola.platform.tianji.worker.TransactionalWorkerHandler;
import top.egon.cola.platform.tianji.worker.WorkerExecutionConflictException;
import top.egon.cola.platform.tianji.worker.WorkerExecutionContext;
import top.egon.cola.platform.tianji.worker.WorkerExecutionDeadlineException;
import top.egon.cola.platform.tianji.worker.WorkerHandlerDescriptor;
import top.egon.cola.platform.tianji.worker.WorkerHandlerRegistration;
import top.egon.cola.platform.tianji.worker.WorkerPollingProperties;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static top.egon.cola.platform.tianji.process.server.compensation.CompensationBusinessFixture.RESERVE_TOPIC;
import static top.egon.cola.platform.tianji.process.server.compensation.CompensationBusinessFixture.SCHEMA;
import static top.egon.cola.platform.tianji.process.server.compensation.CompensationBusinessFixture.SCHEMA_VERSION;
import static top.egon.cola.platform.tianji.process.server.compensation.CompensationBusinessFixture.UNDO_TOPIC;

/** Owns only one authorized fixture Worker and scoped BusinessDB test evidence. */
public final class ExpenseBusinessWorkerFixture implements AutoCloseable {
    private static final String NOTICE_TOPIC = "notifyExpense";
    private static final String OBJECT = SCHEMA + ".business_object";
    private static final String ORIGINAL = SCHEMA + ".original_receipt";
    private static final String UNDO = SCHEMA + ".compensation_receipt";
    private static final String NOTICE = SCHEMA + ".expense_notice";
    private static final String WORKER_SCHEMA = "tianji_worker";
    private static final String WORKER_RECEIPT = WORKER_SCHEMA + ".tianji_worker_execution_receipt";

    private final DataSource runtime;
    private final DataSource observer;
    private final String sourceId;
    private final long tenantId;
    private final ValidatorFactory validators;
    private final ValidationUtils validation;
    private final Map<UUID, CompensationBusinessFixture.ExpectedOperationVO> operations = new ConcurrentHashMap<>();
    private ThreadPoolTaskScheduler scheduler;
    private ThreadPoolExecutor executor;
    private RemoteWorkerPoller poller;
    private boolean closed;

    public ExpenseBusinessWorkerFixture(DataSource runtime, DataSource observer, String sourceId, long tenantId) {
        if (runtime == null || observer == null || runtime == observer || !safeSource(sourceId) || tenantId <= 0) throw invalid();
        this.runtime = runtime;
        this.observer = observer;
        this.sourceId = sourceId;
        this.tenantId = tenantId;
        validators = Validation.buildDefaultValidatorFactory();
        validation = new ValidationUtils(validators.getValidator());
    }

    public static void prepareDisposableFixtureTables(DataSource setup, String runtimeRole, String observerRole) {
        if (!role(runtimeRole) || !role(observerRole) || runtimeRole.equals(observerRole)) throw invalid();
        var jdbc = jdbc(setup);
        write(setup).executeWithoutResult(status -> {
            requireDisposableSource(jdbc);
            requireLimitedRole(jdbc, runtimeRole);
            requireLimitedRole(jdbc, observerRole);
            if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_namespace n JOIN pg_roles r ON r.oid=n.nspowner
                                   WHERE n.nspname='compensation_fixture' AND r.rolname=current_user)
                      AND to_regclass(?) IS NOT NULL
                      AND to_regclass(?) IS NULL AND to_regclass(?) IS NULL AND to_regclass(?) IS NULL AND to_regclass(?) IS NULL
                    """, Boolean.class, WORKER_RECEIPT, OBJECT, ORIGINAL, UNDO, NOTICE))) throw invalid();
            CompensationBusinessFixture.createBusinessTables(jdbc);
            jdbc.execute("""
                    CREATE TABLE compensation_fixture.expense_notice (
                        source_id varchar(128) COLLATE "C" NOT NULL,
                        tenant_id bigint NOT NULL CHECK (tenant_id > 0),
                        operation_id uuid NOT NULL,
                        original_receipt_id char(64) COLLATE "C" NOT NULL CHECK (original_receipt_id ~ '^[0-9a-f]{64}$'),
                        notice_receipt_id char(64) COLLATE "C" NOT NULL CHECK (notice_receipt_id ~ '^[0-9a-f]{64}$'),
                        recorded_at timestamptz(6) NOT NULL,
                        CONSTRAINT pk_expense_notice PRIMARY KEY (source_id, tenant_id, operation_id),
                        FOREIGN KEY (source_id, tenant_id, original_receipt_id)
                            REFERENCES compensation_fixture.original_receipt (source_id, tenant_id, original_receipt_id)
                            ON UPDATE RESTRICT ON DELETE RESTRICT
                    )
                    """);
            String runtimeName = '"' + runtimeRole + '"', observerName = '"' + observerRole + '"';
            jdbc.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + runtimeName + ", " + observerName);
            jdbc.execute("GRANT SELECT, UPDATE (version, reserved_amount) ON " + OBJECT + " TO " + runtimeName);
            jdbc.execute("GRANT SELECT, INSERT ON " + ORIGINAL + ", " + UNDO + ", " + NOTICE + " TO " + runtimeName);
            // Row locks need one UPDATE column privilege; the existing original-receipt trigger rejects updates.
            jdbc.execute("GRANT UPDATE (recorded_at) ON " + ORIGINAL + " TO " + runtimeName);
            jdbc.execute("GRANT SELECT ON " + OBJECT + ", " + ORIGINAL + ", " + UNDO + ", " + NOTICE + " TO " + observerName);
            for (String table : List.of(OBJECT, ORIGINAL, UNDO, NOTICE)) {
                if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT has_table_privilege(?, ?, 'SELECT')
                          AND NOT has_table_privilege(?, ?, 'INSERT,UPDATE,DELETE,TRUNCATE,TRIGGER,REFERENCES')
                          AND NOT has_any_column_privilege(?, ?, 'INSERT,UPDATE,REFERENCES')
                        """, Boolean.class, observerRole, table, observerRole, table, observerRole, table))) throw invalid();
            }
            if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT has_table_privilege(?, ?, 'SELECT') AND has_table_privilege(?, ?, 'INSERT')
                      AND NOT has_table_privilege(?, ?, 'UPDATE,DELETE,TRUNCATE,TRIGGER,REFERENCES')
                      AND NOT has_any_column_privilege(?, ?, 'UPDATE,REFERENCES')
                    """, Boolean.class, runtimeRole, NOTICE, runtimeRole, NOTICE, runtimeRole, NOTICE,
                    runtimeRole, NOTICE))) throw invalid();
        });
    }

    public static void seedDisposableObject(DataSource setup, String sourceId, long tenantId, String objectId,
                                            long expectedVersion, long expectedReserved) {
        if (!safeSource(sourceId) || tenantId <= 0 || objectId == null || !objectId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                || expectedVersion < 0 || expectedReserved < 0) throw invalid();
        var jdbc = jdbc(setup);
        write(setup).executeWithoutResult(status -> {
            requireDisposableSource(jdbc);
            if (jdbc.update("INSERT INTO " + OBJECT + " (source_id,tenant_id,object_id,version,reserved_amount) VALUES (?,?,?,?,?)",
                    sourceId, tenantId, objectId, expectedVersion, expectedReserved) != 1) throw invalid();
        });
    }

    public synchronized void registerExpectedOperation(ProcessInstanceDTO start, UUID operationId, String objectId,
                                                        long expectedVersion, long expectedReserved, long amount) {
        requireOpen();
        if (start == null || !"expenseReview".equals(start.definitionKey()) || start.status() != ProcessInstanceStatusEnum.ACTIVE
                || start.finishedAt() != null || operationId == null || poller != null && poller.isRunning()) throw invalid();
        var expected = validation.validate(new CompensationBusinessFixture.ExpectedOperationVO(sourceId, tenantId, start.instanceId(),
                start.businessKey(), start.correlationId(), operationId, objectId, expectedVersion, expectedReserved, amount));
        var stored = object(jdbc(observer), expected);
        if (stored.version() != expectedVersion || stored.reserved() != expectedReserved
                || operations.putIfAbsent(operationId, expected) != null) throw invalid();
    }

    public synchronized RemoteWorkerPoller openPoller(WorkerClient client, boolean recovery) {
        requireOpen();
        if (client == null || operations.isEmpty() || poller != null) throw invalid();
        var runtimeJdbc = jdbc(runtime);
        var observerJdbc = jdbc(observer);
        requireDisposableSource(runtimeJdbc);
        requireDisposableSource(observerJdbc);
        String identitySql = "SELECT inet_server_addr()::text || ':' || inet_server_port() || '/' || current_database()";
        String roleSql = "SELECT current_user::text";
        if (!Objects.equals(runtimeJdbc.queryForObject(identitySql, String.class), observerJdbc.queryForObject(identitySql, String.class))
                || Objects.equals(runtimeJdbc.queryForObject(roleSql, String.class), observerJdbc.queryForObject(roleSql, String.class))) throw invalid();
        var store = new JdbcWorkerExecutionStore(runtime, new DataSourceTransactionManager(runtime), WORKER_SCHEMA, sourceId, tenantId,
                Duration.ofSeconds(12), Duration.ofSeconds(4));
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("expense-worker-poll-");
        scheduler.setDaemon(true);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().name("expense-worker-handler-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
        var registrations = List.of(
                registration(RESERVE_TOPIC, (context, operations) -> originalHandler(context).execute(context, operations)),
                registration(UNDO_TOPIC, (context, operations) -> undoHandler(context).execute(context, operations)),
                registration(NOTICE_TOPIC, noticeHandler()));
        var settings = new WorkerPollingProperties(true, tenantId, 1, Duration.ofMillis(100), Duration.ofSeconds(2),
                Duration.ofSeconds(1), Duration.ofMillis(200), recovery ? 1 : 2, Duration.ofSeconds(5));
        poller = new RemoteWorkerPoller(client, store, registrations, scheduler, executor, settings, Clock.systemUTC());
        return poller;
    }

    private TransactionalWorkerHandler originalHandler(WorkerExecutionContext context) {
        return new CompensationBusinessFixture(expected(context, RESERVE_TOPIC), validation).originalHandler();
    }

    private TransactionalWorkerHandler undoHandler(WorkerExecutionContext context) {
        return new CompensationBusinessFixture(expected(context, UNDO_TOPIC), validation).undoHandler();
    }

    private TransactionalWorkerHandler noticeHandler() {
        return (context, operations) -> {
            var expected = expected(context, NOTICE_TOPIC);
            var jdbc = boundReadCommitted(operations);
            var original = original(jdbc, expected);
            if (original == null) throw conflict();
            requireBeforeDeadline(jdbc, context.deadline());
            String receiptId = noticeId(expected, original);
            int changed = jdbc.update("INSERT INTO " + NOTICE
                            + " (source_id,tenant_id,operation_id,original_receipt_id,notice_receipt_id,recorded_at) VALUES (?,?,?,?,?,clock_timestamp())"
                            + " ON CONFLICT (source_id,tenant_id,operation_id) DO NOTHING",
                    sourceId, tenantId, expected.operationId(), original, receiptId);
            if (changed < 0 || changed > 1 || !receiptId.equals(notice(jdbc, expected, original))) throw conflict();
            requireBeforeDeadline(jdbc, context.deadline());
            return new WorkerBusinessResultDTO(receiptId, Map.of());
        };
    }

    public EffectsVO readEffects(UUID operationId) {
        requireOpen();
        var expected = operations.get(Objects.requireNonNull(operationId));
        if (expected == null) throw invalid();
        var read = new TransactionTemplate(new DataSourceTransactionManager(observer));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(5);
        var jdbc = jdbc(observer);
        return Objects.requireNonNull(read.execute(status -> {
            requireDisposableSource(jdbc);
            var original = original(jdbc, expected);
            var notice = notice(jdbc, expected, original);
            var undo = undo(jdbc, expected, original);
            if (notice != null && undo != null) throw conflict();
            var current = object(jdbc, expected);
            return new EffectsVO(original, notice, undo, count(original), count(notice), count(undo),
                    current.version(), current.reserved());
        }));
    }

    private static int count(String id) { return id == null ? 0 : 1; }

    private String original(JdbcTemplate jdbc, CompensationBusinessFixture.ExpectedOperationVO expected) {
        return oneOrNone(jdbc.query("SELECT original_receipt_id,object_id,process_instance_id,business_key,correlation_id,"
                        + " before_version,after_version,amount,before_reserved,after_reserved,recorded_at FROM " + ORIGINAL
                        + " WHERE source_id=? AND tenant_id=? AND operation_id=? LIMIT 2", (row, ignored) -> {
                    String id = row.getString("original_receipt_id");
                    String digest = ProcessRequestDigest.hash(List.of("worker-compensation-original-v1", sourceId,
                            Long.toString(tenantId), expected.operationId().toString()), Map.of());
                    if (!digest.equals(id) || !expected.objectId().equals(row.getString("object_id"))
                            || !expected.processInstanceId().equals(row.getString("process_instance_id"))
                            || !expected.businessKey().equals(row.getString("business_key"))
                            || !expected.correlationId().equals(row.getString("correlation_id"))
                            || row.getLong("before_version") != expected.expectedVersion()
                            || row.getLong("after_version") != Math.addExact(expected.expectedVersion(), 1)
                            || row.getLong("amount") != expected.amount()
                            || row.getLong("before_reserved") != expected.expectedReserved()
                            || row.getLong("after_reserved") != Math.addExact(expected.expectedReserved(), expected.amount())
                            || row.getObject("recorded_at", OffsetDateTime.class) == null) throw conflict();
                    return id;
                }, sourceId, tenantId, expected.operationId()));
    }

    private String notice(JdbcTemplate jdbc, CompensationBusinessFixture.ExpectedOperationVO expected, String original) {
        return oneOrNone(jdbc.query("SELECT original_receipt_id,notice_receipt_id,recorded_at FROM " + NOTICE
                        + " WHERE source_id=? AND tenant_id=? AND operation_id=? LIMIT 2", (row, ignored) -> {
                    String id = row.getString("notice_receipt_id");
                    if (original == null || !original.equals(row.getString("original_receipt_id"))
                            || !noticeId(expected, original).equals(id)
                            || row.getObject("recorded_at", OffsetDateTime.class) == null) throw conflict();
                    return id;
                }, sourceId, tenantId, expected.operationId()));
    }

    private String undo(JdbcTemplate jdbc, CompensationBusinessFixture.ExpectedOperationVO expected, String original) {
        return oneOrNone(jdbc.query("SELECT original_receipt_id,compensation_receipt_id,object_id,process_instance_id,"
                        + " business_key,correlation_id,undo_action,before_version,after_version,before_reserved,after_reserved,recorded_at FROM " + UNDO
                        + " WHERE source_id=? AND tenant_id=? AND operation_id=? LIMIT 2", (row, ignored) -> {
                    String id = row.getString("compensation_receipt_id");
                    if (original == null || !original.equals(row.getString("original_receipt_id"))
                            || !ProcessRequestDigest.hash(List.of("worker-compensation-v1", sourceId, Long.toString(tenantId),
                            original, "UNDO_RESERVATION_V1"), Map.of()).equals(id)
                            || row.getLong("before_version") != Math.addExact(expected.expectedVersion(), 1)
                            || row.getLong("after_version") != Math.addExact(expected.expectedVersion(), 2)
                            || row.getLong("before_reserved") != Math.addExact(expected.expectedReserved(), expected.amount())
                            || row.getLong("after_reserved") != expected.expectedReserved()
                            || !expected.objectId().equals(row.getString("object_id"))
                            || !expected.processInstanceId().equals(row.getString("process_instance_id"))
                            || !expected.businessKey().equals(row.getString("business_key"))
                            || !expected.correlationId().equals(row.getString("correlation_id"))
                            || !"UNDO_RESERVATION_V1".equals(row.getString("undo_action"))
                            || row.getObject("recorded_at", OffsetDateTime.class) == null) throw conflict();
                    return id;
                }, sourceId, tenantId, expected.operationId()));
    }

    private String noticeId(CompensationBusinessFixture.ExpectedOperationVO expected, String original) {
        return ProcessRequestDigest.hash(List.of("expense-notice-v1", sourceId, Long.toString(tenantId),
                expected.operationId().toString(), original), Map.of());
    }

    private ObjectStateVO object(JdbcTemplate jdbc, CompensationBusinessFixture.ExpectedOperationVO expected) {
        var row = oneOrNone(jdbc.query("SELECT version,reserved_amount FROM " + OBJECT
                        + " WHERE source_id=? AND tenant_id=? AND object_id=? LIMIT 2",
                (rs, ignored) -> new ObjectStateVO(rs.getLong(1), rs.getLong(2)), sourceId, tenantId, expected.objectId()));
        if (row == null) throw conflict();
        return row;
    }

    private CompensationBusinessFixture.ExpectedOperationVO expected(WorkerExecutionContext context, String topic) {
        if (context == null || context.scopeType() != WorkerScopeTypeEnum.BPMN || context.caseInstanceId() != null
                || !sourceId.equals(context.sourceId()) || tenantId != context.tenantId() || !topic.equals(context.topic())
                || !SCHEMA_VERSION.equals(context.schemaVersion()) || context.effectSafety() != WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT
                || context.deadline() == null || !(context.input().get("operationId") instanceof String operation)) throw invalid();
        UUID id;
        try { id = UUID.fromString(operation); }
        catch (IllegalArgumentException malformed) { throw invalid(); }
        var expected = operations.get(id);
        if (expected == null || !id.toString().equals(operation) || !Map.of("operationId", operation).equals(context.input())
                || !expected.processInstanceId().equals(context.processInstanceId())
                || !expected.businessKey().equals(context.businessKey()) || !expected.correlationId().equals(context.correlationId())) throw invalid();
        return expected;
    }

    private JdbcTemplate boundReadCommitted(JdbcOperations operations) {
        if (!(operations instanceof JdbcTemplate jdbc) || jdbc.getDataSource() != runtime
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || !(TransactionSynchronizationManager.getResource(runtime) instanceof ConnectionHolder holder)) throw invalid();
        try {
            Connection connection = holder.getConnection();
            if (connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED || connection.isReadOnly()) throw invalid();
            return jdbc;
        } catch (SQLException failure) {
            throw invalid();
        }
    }

    private static void requireBeforeDeadline(JdbcTemplate jdbc, Instant deadline) {
        OffsetDateTime now = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
        if (now == null) throw invalid();
        if (!now.toInstant().isBefore(deadline)) throw new WorkerExecutionDeadlineException();
    }

    private static void requireDisposableSource(JdbcTemplate jdbc) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT current_database() ~ '^tianji_it_[a-z0-9_]{1,51}$'
                  AND inet_server_addr() IN (inet '127.0.0.1', inet '::1')
                  AND NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8'
                  AND current_setting('session_replication_role')='origin'
                """, Boolean.class))) throw invalid();
    }

    private static void requireLimitedRole(JdbcTemplate jdbc, String role) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname=? AND rolcanlogin AND NOT rolsuper
                     AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls)
                  AND NOT EXISTS (SELECT 1 FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.member WHERE r.rolname=?)
                  AND current_user<>?
                  AND NOT EXISTS (SELECT 1 FROM pg_database d JOIN pg_roles r ON r.oid=d.datdba
                                  WHERE d.datname=current_database() AND r.rolname=?)
                  AND NOT EXISTS (SELECT 1 FROM pg_namespace n JOIN pg_roles r ON r.oid=n.nspowner
                                  WHERE n.nspname=? AND r.rolname=?)
                  AND NOT has_database_privilege(?, current_database(), 'CREATE')
                  AND NOT has_schema_privilege(?, ?, 'CREATE')
                """, Boolean.class, role, role, role, role, SCHEMA, role, role, role, SCHEMA))) throw invalid();
    }

    private static WorkerHandlerRegistration registration(String topic, TransactionalWorkerHandler handler) {
        return new WorkerHandlerRegistration(new WorkerHandlerDescriptor(new WorkerContractDTO(topic, SCHEMA_VERSION,
                WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, Map.of("operationId", ProcessValueTypeEnum.STRING), Map.of()),
                "expense-fixture-v1"), handler);
    }

    private static JdbcTemplate jdbc(DataSource source) {
        var jdbc = new JdbcTemplate(Objects.requireNonNull(source));
        jdbc.setQueryTimeout(5);
        return jdbc;
    }

    private static TransactionTemplate write(DataSource source) {
        var tx = new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(source)));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.setTimeout(10);
        return tx;
    }

    private static <T> T oneOrNone(List<T> rows) {
        if (rows.size() > 1) throw conflict();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static boolean safeSource(String source) { return source != null && source.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"); }
    private static boolean role(String role) { return role != null && role.matches("[a-z_][a-z0-9_]{0,62}"); }

    private static IllegalStateException invalid() { return new IllegalStateException("Expense Business Worker fixture boundary is invalid"); }
    private static WorkerExecutionConflictException conflict() { return new WorkerExecutionConflictException(); }

    private void requireOpen() { if (closed) throw invalid(); }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            if (poller != null) poller.stop();
        } finally {
            if (executor != null) executor.shutdownNow();
            if (scheduler != null) scheduler.shutdown();
            validators.close();
            operations.clear();
        }
    }

    private record ObjectStateVO(long version, long reserved) { }

    public record EffectsVO(String originalReceiptId, String noticeReceiptId, String undoReceiptId,
                            int originalCount, int noticeCount, int undoCount, long objectVersion, long reservedAmount) { }
}
