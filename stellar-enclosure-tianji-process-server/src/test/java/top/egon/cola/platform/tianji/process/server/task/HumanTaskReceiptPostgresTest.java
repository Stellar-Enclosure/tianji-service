package top.egon.cola.platform.tianji.process.server.task;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.Kind;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskActionResult.Entry;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reference storage only; no native engine or application is started. Requires a separate explicitly supplied empty database. */
@EnabledIfSystemProperty(named = "tianji.task.receipt.postgres.test.url", matches = ".+")
@Timeout(90)
class HumanTaskReceiptPostgresTest {
    private static final String SCHEMA = "tianji_process", HASH = "a".repeat(64);
    private static final ProcessActor ACTOR = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "reviewer", "https://idp.example/20");
    private static final ProcessActor STARTER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "applicant", "https://idp.example/20");

    @Test void v7_preserves_old_checksums_and_reference_actions_are_atomic_scoped_and_replayable_under_contention() throws Exception {
        String url = System.getProperty("tianji.task.receipt.postgres.test.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) throw new IllegalArgumentException("integration tests require an explicit loopback tianji_it_ database");
        var source = new DriverManagerDataSource(url); var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, SCHEMA) != 0) throw new IllegalStateException("integration tests require a fresh database without the process schema");
        var previous = flyway(source, "6"); assertEquals(6, previous.migrate().migrationsExecuted); previous.validate();
        List<Integer> checksums = jdbc.query("SELECT checksum FROM tianji_process.tianji_process_schema_history ORDER BY installed_rank", (rs, number) -> rs.getInt(1));
        var verifier = new HumanTaskStorageVerifier(source, SCHEMA); assertThrows(IllegalStateException.class, verifier::verify);
        var next = flyway(source, "7"); assertEquals(1, next.migrate().migrationsExecuted); next.validate(); verifier.verify();
        assertEquals(checksums, jdbc.query("SELECT checksum FROM tianji_process.tianji_process_schema_history WHERE version<>'7' ORDER BY installed_rank", (rs, number) -> rs.getInt(1)));
        verifyDrift(jdbc, verifier);

        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(10);
        jdbc.update("INSERT INTO tianji_process.tianji_process_release (tenant_id,release_key,state,manifest_sha256,publisher_type,publisher_id,publisher_issuer,created_at) VALUES (20,'release','ACTIVE',?,'USER',?,?,clock_timestamp())", HASH, STARTER.subjectId(), STARTER.issuerFingerprint());
        jdbc.update("INSERT INTO tianji_process.tianji_process_release_definition (tenant_id,release_key,model_type,definition_key,engine_definition_id,engine_deployment_id,engine_version,model_sha256,input_schema) VALUES (20,'release','BPMN','workflow','definition','deployment',1,?,'{}'::jsonb)", HASH);
        var base = new ProcessCommandReceiptStore(jdbc, SCHEMA);
        transaction.executeWithoutResult(status -> { assertTrue(base.insert(STARTER, Kind.START, "start", HASH, "release", "correlation")); base.apply(STARTER, Kind.START, "start", "instance", null); });
        var origin = base.taskOrigin(ACTOR, "instance"); var before = mock(HumanTaskSnapshot.class); when(before.origin()).thenReturn(origin);
        var store = new HumanTaskReceiptStore(source, SCHEMA); assertThrows(IllegalStateException.class, () -> transaction.execute(status -> store.lockCommand(context(ACTOR, now(jdbc)), HumanTaskActionEnum.COMPLETE, "cmd", HASH))); store.verifyStorage();
        var context = context(ACTOR, now(jdbc));
        assertThrows(InjectedFailure.class, () -> transaction.executeWithoutResult(status -> {
            assertTrue(store.lockCommand(context, HumanTaskActionEnum.COMPLETE, "rollback", HASH).isEmpty()); store.reserve(context, HumanTaskActionEnum.COMPLETE, "rollback", HASH, before);
            store.apply(context, result(context, origin, "rollback", now(jdbc))); throw new InjectedFailure();
        }));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_command_receipt WHERE command_id='rollback'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_process.tianji_human_task_receipt", Integer.class));

        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(10)) {
            List<Future<Entry>> attempts = IntStream.range(0, 20).mapToObj(number -> pool.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS)); var current = context(ACTOR, now(jdbc));
                return transaction.execute(status -> {
                    var replay = store.lockCommand(current, HumanTaskActionEnum.COMPLETE, "cmd", HASH); if (replay.isPresent()) return replay.orElseThrow();
                    store.reserve(current, HumanTaskActionEnum.COMPLETE, "cmd", HASH, before); var result = result(current, origin, "cmd", now(jdbc)); store.apply(current, result); return result;
                });
            })).toList(); start.countDown();
            Entry first = attempts.getFirst().get(20, TimeUnit.SECONDS);
            for (var attempt : attempts) assertEquals(first, attempt.get(20, TimeUnit.SECONDS));
            assertEquals(Boolean.FALSE, first.receipt().approved());
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_process.tianji_human_task_receipt", Integer.class));
        var current = context(ACTOR, now(jdbc));
        assertThrows(ProcessConflictException.class, () -> transaction.execute(status -> store.lockCommand(current, HumanTaskActionEnum.COMPLETE, "cmd", "b".repeat(64))));
        var foreign = context(ProcessActor.verified(20, ProcessActor.SubjectType.USER, ACTOR.subjectId(), "https://other.example/20"), now(jdbc));
        assertTrue(transaction.execute(status -> store.lockCommand(foreign, HumanTaskActionEnum.COMPLETE, "cmd", HASH)).isEmpty());
        jdbc.update("UPDATE tianji_process.tianji_process_command_receipt SET request_sha256=? WHERE command_type='START' AND command_id='start'", "b".repeat(64));
        try { assertThrows(IllegalStateException.class, () -> transaction.execute(status -> store.lockCommand(current, HumanTaskActionEnum.COMPLETE, "cmd", HASH))); }
        finally { jdbc.update("UPDATE tianji_process.tianji_process_command_receipt SET request_sha256=? WHERE command_type='START' AND command_id='start'", HASH); }
    }

    private static void verifyDrift(JdbcTemplate jdbc, HumanTaskStorageVerifier verifier) {
        Integer checksum = jdbc.queryForObject("SELECT checksum FROM tianji_process.tianji_process_schema_history WHERE version='7'", Integer.class);
        jdbc.update("UPDATE tianji_process.tianji_process_schema_history SET checksum=0 WHERE version='7'");
        try { assertThrows(IllegalStateException.class, verifier::verify); }
        finally { jdbc.update("UPDATE tianji_process.tianji_process_schema_history SET checksum=? WHERE version='7'", checksum); }
        String definition = jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='human_task_decision_kind' AND conrelid='tianji_process.tianji_human_task_receipt'::regclass", String.class);
        jdbc.execute("ALTER TABLE tianji_process.tianji_human_task_receipt DROP CONSTRAINT human_task_decision_kind");
        jdbc.execute("ALTER TABLE tianji_process.tianji_human_task_receipt ADD CONSTRAINT human_task_decision_kind CHECK (true)");
        try { assertThrows(IllegalStateException.class, verifier::verify); }
        finally { jdbc.execute("ALTER TABLE tianji_process.tianji_human_task_receipt DROP CONSTRAINT human_task_decision_kind"); jdbc.execute("ALTER TABLE tianji_process.tianji_human_task_receipt ADD CONSTRAINT human_task_decision_kind " + definition); }
        jdbc.execute("ALTER TABLE tianji_process.tianji_human_task_receipt ENABLE ROW LEVEL SECURITY");
        try { assertThrows(IllegalStateException.class, verifier::verify); }
        finally { jdbc.execute("ALTER TABLE tianji_process.tianji_human_task_receipt DISABLE ROW LEVEL SECURITY"); }
        verifier.verify();
    }
    private static HumanTaskContext context(ProcessActor actor, Instant now) { return new HumanTaskContext(actor, "tianji.api.process.task.complete", Set.of("50"), now.plusSeconds(30), 2, 3); }
    private static Entry result(HumanTaskContext context, TaskOrigin origin, String command, Instant now) {
        var receipt = new HumanTaskActionReceiptDTO(command, "task", HumanTaskActionEnum.COMPLETE, HumanTaskScopeTypeEnum.PROCESS, "instance", "release", "workflow", "review", HumanTaskKindEnum.APPROVAL,
                "finance", HASH, null, "approval-" + HASH, false, now);
        return new Entry(context.actor(), HASH, receipt, origin, HASH, "definition", "50", 2, 3, context.userKey(), null);
    }
    private static Flyway flyway(DataSource source, String target) { return Flyway.configure().dataSource(source).schemas(SCHEMA).defaultSchema(SCHEMA).table("tianji_process_schema_history").baselineVersion("0").locations("classpath:db/tianji-process").target(target).cleanDisabled(true).load(); }
    private static Instant now(JdbcTemplate jdbc) { return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant(); }
    private static final class InjectedFailure extends RuntimeException { }
}
