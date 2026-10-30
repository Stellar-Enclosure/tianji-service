package top.egon.cola.platform.tianji.process.server.bridge;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentSubmissionDTO;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStoreTest.*;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentStartWorkerTest.run;

/** Does not start a database; the operator supplies a separate, empty loopback database. */
@EnabledIfSystemProperty(named = "tianji.process.dispatch.postgres.test.url", matches = ".+")
@Timeout(90)
class AgentDispatchStorePostgresTest {
    @Test
    void migration_immutable_intents_unknown_outcomes_and_close_fences_survive_retries() throws Exception {
        String url = System.getProperty("tianji.process.dispatch.postgres.test.url");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("integration test requires an explicit empty loopback tianji_it_ database");
        }
        var source = new DriverManagerDataSource(url);
        var jdbc = new JdbcTemplate(source);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_process'", Integer.class) != 0) {
            throw new IllegalStateException("integration test refuses an existing Process schema");
        }
        Flyway old = flyway(source, "4");
        assertEquals(4, old.migrate().migrationsExecuted);
        List<Integer> checksums = jdbc.query("SELECT checksum FROM tianji_process.tianji_process_schema_history ORDER BY installed_rank",
                (rs, row) -> rs.getInt(1));
        var verifier = new ProcessApiSchemaVerifier(source, "tianji_process");
        verifier.verifyWorker();
        assertThrows(IllegalStateException.class, verifier::verifyAgentDispatch);
        var latest = flyway(source, "5");
        assertEquals(1, latest.migrate().migrationsExecuted);
        latest.validate();
        assertEquals(checksums, jdbc.query("SELECT checksum FROM tianji_process.tianji_process_schema_history WHERE version IN ('1','2','3','4') "
                + "ORDER BY installed_rank", (rs, row) -> rs.getInt(1)));
        verifier.verifyAgentDispatch();
        jdbc.update("INSERT INTO tianji_process.tianji_process_release (tenant_id,release_key,state,manifest_sha256,publisher_type,publisher_id,created_at) "
                + "VALUES (20,'release-one','ACTIVE',?,'SERVICE','legacy',clock_timestamp())", "a".repeat(64));
        var manager = new DataSourceTransactionManager(source);
        var transaction = new TransactionTemplate(manager);
        var store = new AgentDispatchStore(source, manager, "tianji_process");
        assertThrows(IllegalArgumentException.class, () -> store.requireStorage(source, new DataSourceTransactionManager(source)));
        assertThrows(IllegalStateException.class, () -> store.prepare(seed("outside-transaction")));

        var first = transaction.execute(status -> store.prepare(seed("job-one")));
        assertNotNull(first);
        assertEquals(first, transaction.execute(status -> store.prepare(seed("job-one"))));
        var changed = seed("job-one");
        var changedBinding = new AgentDispatchBinding(ACTOR, binding().sourceId(), binding().topic(), binding().schemaVersion(),
                "other-definition", 3, "d".repeat(64), binding().instruction(), binding().submissionTimeout());
        var changedSeed = new AgentDispatchStore.Seed(changedBinding, changed.instanceId(), changed.occurrenceId(), changed.releaseKey(),
                changed.processDefinitionId(), changed.processModelSha256(), changed.elementId(), changed.workerInputSha256(),
                changed.businessKey(), changed.correlationId(), changed.inputs());
        assertThrows(ProcessConflictException.class, () -> transaction.execute(status -> store.prepare(changedSeed)));
        var foreignActor = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "process-worker", "https://foreign.example/20");
        var foreignKey = new AgentDispatchStore.Key(foreignActor, binding().sourceId(), first.seed().instanceId(), first.seed().occurrenceId());
        assertThrows(ProcessResourceNotFoundException.class, () -> store.refresh(foreignKey, true));
        assertThrows(ProcessConflictException.class, () -> store.record(first.seed().key(),
                intent("foreign-job", AgentDispatchStore.State.PENDING, null).query(), new LinkedAgentSubmissionDTO(false, run())));

        var submitted = store.record(first.seed().key(), first.query(), new LinkedAgentSubmissionDTO(false, run()));
        assertEquals(AgentDispatchStore.State.SUBMITTED, submitted.state());
        assertThrows(InjectedFailure.class, () -> transaction.execute(status -> {
            store.markAcknowledged(first.seed().key(), "run-one");
            throw new InjectedFailure();
        }));
        assertEquals(AgentDispatchStore.State.SUBMITTED, store.refresh(first.seed().key(), true).state());
        assertEquals(AgentDispatchStore.State.ACKNOWLEDGED, transaction.execute(status -> store.markAcknowledged(first.seed().key(), "run-one")).state());
        assertEquals(AgentDispatchStore.State.CLOSING, store.refresh(first.seed().key(), false).state());
        // No remote acknowledgement means no CLOSED evidence, including after a new store instance.
        var restarted = new AgentDispatchStore(source, manager, "tianji_process");
        assertEquals(AgentDispatchStore.State.CLOSING, restarted.refresh(first.seed().key(), true).state());
        assertEquals(AgentDispatchStore.State.CLOSED, restarted.record(first.seed().key(), first.query(), new LinkedAgentSubmissionDTO(true, run())).state());
        assertThrows(ProcessConflictException.class, () -> store.record(first.seed().key(), first.query(), new LinkedAgentSubmissionDTO(false, run())));

        var concurrent = seed("concurrent-job");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = java.util.stream.IntStream.range(0, 16).mapToObj(index -> executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return transaction.execute(status -> store.prepare(concurrent));
            })).toList();
            start.countDown();
            var winner = futures.getFirst().get(20, TimeUnit.SECONDS);
            for (var future : futures) assertEquals(winner, future.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_agent_dispatch WHERE occurrence_id='concurrent-job'", Integer.class));
        assertEquals(1, store.due(binding(), 1, Duration.ofSeconds(30)).size());
        assertTrue(store.due(binding(), 1, Duration.ofSeconds(30)).isEmpty());

        var shortBinding = new AgentDispatchBinding(ACTOR, binding().sourceId(), binding().topic(), binding().schemaVersion(),
                binding().definitionKey(), binding().definitionVersion(), binding().definitionContentSha256(), binding().instruction(), Duration.ofSeconds(1));
        var expiring = new AgentDispatchStore.Seed(shortBinding, changed.instanceId(), "unknown-job", changed.releaseKey(),
                changed.processDefinitionId(), changed.processModelSha256(), changed.elementId(), changed.workerInputSha256(),
                changed.businessKey(), changed.correlationId(), changed.inputs());
        var unknown = transaction.execute(status -> store.prepare(expiring));
        TimeUnit.MILLISECONDS.sleep(1_100);
        assertEquals(AgentDispatchStore.State.CLOSING, store.refresh(unknown.seed().key(), true).state());
        // Late submission evidence is retained and cannot reopen admission or reset the original deadline.
        var lateRun = new top.egon.cola.platform.tianji.contract.agent.AgentRunDTO("late-run", "research", 2, null,
                run().status(), null, false, 0, 1, 0, run().deadlineAt(), run().createdAt(), null);
        var late = store.record(unknown.seed().key(), unknown.query(), new LinkedAgentSubmissionDTO(false, lateRun));
        assertEquals(AgentDispatchStore.State.CLOSING, late.state());
        assertEquals(unknown.dispatchNotAfter(), late.dispatchNotAfter());
        assertThrows(ProcessConflictException.class, () -> transaction.execute(status -> store.markAcknowledged(late.seed().key(), "late-run")));
        assertEquals(AgentDispatchStore.State.CLOSED, store.record(late.seed().key(), late.query(), new LinkedAgentSubmissionDTO(true, lateRun)).state());

        assertThrows(InjectedFailure.class, () -> transaction.execute(status -> {
            jdbc.execute("ALTER TABLE tianji_process.tianji_process_agent_dispatch DROP CONSTRAINT tianji_agent_dispatch_state_check");
            assertThrows(IllegalStateException.class, verifier::verifyAgentDispatch);
            throw new InjectedFailure();
        }));
        verifier.verifyAgentDispatch();
    }

    private static Flyway flyway(DriverManagerDataSource source, String target) {
        return Flyway.configure().dataSource(source).schemas("tianji_process").defaultSchema("tianji_process")
                .table("tianji_process_schema_history").locations("classpath:db/tianji-process").target(target).cleanDisabled(true).load();
    }
    private static final class InjectedFailure extends RuntimeException { }
}
