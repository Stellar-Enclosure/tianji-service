package top.egon.cola.platform.tianji.process.server.bridge;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
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
import static top.egon.cola.platform.tianji.process.server.bridge.AgentResultProducerRegistryTest.*;

/** Uses a user-provisioned empty database; no database or application is started by this test. */
@EnabledIfSystemProperty(named = "tianji.process.inbox.postgres.test.url", matches = ".+")
@Timeout(90)
class AgentResultInboxPostgresTest {
    @Test
    void migration_early_results_concurrent_duplicates_and_payload_conflicts_are_durable_and_scoped() throws Exception {
        String url = System.getProperty("tianji.process.inbox.postgres.test.url");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("Inbox test requires an explicit empty loopback tianji_it_ database");
        }
        var source = new DriverManagerDataSource(url);
        var jdbc = new JdbcTemplate(source);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_process'", Integer.class) != 0) {
            throw new IllegalStateException("Inbox test refuses an existing Process schema");
        }
        assertEquals(5, flyway(source, "5").migrate().migrationsExecuted);
        var checksums = legacyMigrationChecksums(jdbc);
        assertEquals(List.of("1", "2", "3", "4", "5"), checksums.stream().map(MigrationChecksum::version).toList());
        assertTrue(checksums.stream().allMatch(migration -> migration.checksum() != null));
        var verifier = new ProcessApiSchemaVerifier(source, "tianji_process");
        assertThrows(IllegalStateException.class, verifier::verifyAgentResultInbox);
        var next = flyway(source, "6");
        assertEquals(1, next.migrate().migrationsExecuted);
        next.validate();
        verifier.verifyAgentResultInbox();
        assertEquals(checksums, legacyMigrationChecksums(jdbc));
        jdbc.update("INSERT INTO tianji_process.tianji_process_release (tenant_id,release_key,state,manifest_sha256,publisher_type,publisher_id,created_at) "
                + "VALUES (20,'release-one','ACTIVE',?,'SERVICE','legacy',clock_timestamp())", "a".repeat(64));
        var runtimeSource = runtimeSource(source, jdbc);
        var manager = new DataSourceTransactionManager(runtimeSource);
        var transaction = new TransactionTemplate(manager);
        var dispatches = new AgentDispatchStore(runtimeSource, manager, "tianji_process");
        var registration = new AgentResultProducerRegistry.Registration(PRODUCER, "agent-east", "process-east", ACTOR);
        var registry = new AgentResultProducerRegistry(List.of(registration));
        var inbox = new AgentResultInbox(runtimeSource, manager, "tianji_process", dispatches, registry, Duration.ofDays(1));
        assertThrows(IllegalArgumentException.class, () -> new AgentResultInbox(runtimeSource, new DataSourceTransactionManager(runtimeSource),
                "tianji_process", dispatches, registry, Duration.ofDays(1)));
        var intent = transaction.execute(status -> dispatches.prepare(seed("job-one")));
        assertNotNull(intent);
        var event = event(intent, "run-one", 3, "b".repeat(64));
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> inbox.receive(PRODUCER, event)));
        var first = inbox.receive(PRODUCER, event);
        assertEquals(AgentResultReceiptStateEnum.RECEIVED, first.state());
        assertEquals(first, inbox.receive(PRODUCER, event));
        assertEquals(event.fingerprint(), first.eventFingerprint());
        // The authenticated early event is stored, but cannot establish the run ID or native acknowledgement.
        assertNull(jdbc.queryForObject("SELECT run_id FROM tianji_process.tianji_process_agent_dispatch WHERE occurrence_id='job-one'", String.class));
        assertEquals("PENDING", jdbc.queryForObject("SELECT state FROM tianji_process.tianji_process_agent_result_inbox WHERE event_id=?", String.class, event.eventId()));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = java.util.stream.IntStream.range(0, 20).mapToObj(index -> executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return inbox.receive(PRODUCER, event);
            })).toList();
            start.countDown();
            for (var future : futures) assertEquals(first, future.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_agent_result_inbox", Integer.class));
        assertEquals(1, inbox.due(registration, 1, Duration.ofSeconds(30)).size());
        assertTrue(inbox.due(registration, 1, Duration.ofSeconds(30)).isEmpty());
        assertThrows(ProcessConflictException.class, () -> inbox.receive(PRODUCER, event(intent, "run-one", 3, "c".repeat(64))));
        assertThrows(ProcessConflictException.class, () -> inbox.receive(PRODUCER, event(intent, "run-one", 4, "b".repeat(64))));
        assertThrows(ProcessResourceNotFoundException.class, () -> inbox.receive(
                ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "agent-producer", "https://foreign.example/20"), event));
        assertEquals(first, new AgentResultInbox(runtimeSource, manager, "tianji_process", dispatches, registry, Duration.ofDays(1)).receive(PRODUCER, event));
        var retryIntent = transaction.execute(status -> dispatches.prepare(seed("job-retry")));
        var retryEvent = event(retryIntent, "retry-run", 3, "b".repeat(64));
        var retryReceipt = inbox.receive(PRODUCER, retryEvent);
        transaction.executeWithoutResult(status -> {
            var current = inbox.find(registration, retryEvent.eventId(), false);
            inbox.lockDispatch(registration, current);
            current = inbox.find(registration, retryEvent.eventId(), true);
            assertEquals(AgentResultReceiptStateEnum.RECEIVED, inbox.reschedule(registration, current, Duration.ofSeconds(1), 3, false).state());
            assertEquals(0, inbox.find(registration, retryEvent.eventId(), true).failures());
        });
        for (int attempt = 1; attempt <= 3; attempt++) {
            int expected = attempt;
            transaction.executeWithoutResult(status -> {
                var current = inbox.find(registration, retryEvent.eventId(), false);
                inbox.lockDispatch(registration, current);
                current = inbox.find(registration, retryEvent.eventId(), true);
                var result = inbox.reschedule(registration, current, Duration.ofSeconds(1), 3, true);
                assertEquals(expected == 3 ? AgentResultReceiptStateEnum.DEAD : AgentResultReceiptStateEnum.RECEIVED, result.state());
                assertEquals(retryReceipt.receivedAt(), result.receivedAt());
                assertEquals(expected, inbox.find(registration, retryEvent.eventId(), true).failures());
            });
        }
        var expiredIntent = transaction.execute(status -> dispatches.prepare(seed("job-expired-result")));
        var expiredEvent = event(expiredIntent, "expired-run", 3, "b".repeat(64));
        inbox.receive(PRODUCER, expiredEvent);
        jdbc.update("WITH observed AS (SELECT clock_timestamp() AS now) UPDATE tianji_process.tianji_process_agent_result_inbox "
                + "SET received_at=observed.now-interval '2 minutes',retry_not_after=observed.now-interval '1 minute' FROM observed WHERE event_id=?", expiredEvent.eventId());
        transaction.executeWithoutResult(status -> {
            var current = inbox.find(registration, expiredEvent.eventId(), false);
            inbox.lockDispatch(registration, current);
            current = inbox.find(registration, expiredEvent.eventId(), true);
            assertEquals(AgentResultReceiptStateEnum.DEAD, inbox.reschedule(registration, current, Duration.ofSeconds(1), 3, false).state());
        });
        assertThrows(InjectedFailure.class, () -> new TransactionTemplate(new DataSourceTransactionManager(source)).execute(status -> {
            jdbc.execute("ALTER TABLE tianji_process.tianji_process_agent_result_inbox DROP CONSTRAINT tianji_agent_inbox_state_fields_check");
            assertThrows(IllegalStateException.class, verifier::verifyAgentResultInbox);
            throw new InjectedFailure();
        }));
        verifier.verifyAgentResultInbox();
        assertEquals(first, inbox.receive(PRODUCER, event));
    }

    static DriverManagerDataSource runtimeSource(DriverManagerDataSource owner, JdbcTemplate ownerJdbc) {
        String role = System.getProperty("tianji.process.postgres.test.runtime-user", "");
        if (role.isEmpty()) return owner;
        if (!role.matches("tianji_it_runtime_[0-9a-f]{12}")) throw new IllegalArgumentException("Invalid test runtime role");
        assertTrue(Boolean.TRUE.equals(ownerJdbc.queryForObject("""
                SELECT r.rolcanlogin AND NOT r.rolsuper AND NOT r.rolcreaterole AND NOT r.rolcreatedb
                    AND NOT r.rolreplication AND NOT r.rolbypassrls AND NOT has_database_privilege(r.rolname,current_database(),'CREATE')
                    AND NOT has_schema_privilege(r.rolname,'tianji_process','CREATE')
                    AND NOT EXISTS (SELECT 1 FROM pg_auth_members WHERE member=r.oid)
                FROM pg_roles r WHERE r.rolname=?
                """, Boolean.class, role)));
        var quotedRole = '"' + role + '"';
        ownerJdbc.execute("GRANT USAGE ON SCHEMA tianji_process TO " + quotedRole);
        ownerJdbc.execute("GRANT SELECT ON tianji_process.tianji_process_schema_history TO " + quotedRole);
        ownerJdbc.execute("GRANT SELECT,INSERT,UPDATE ON tianji_process.tianji_process_agent_dispatch, "
                + "tianji_process.tianji_process_agent_result_inbox TO " + quotedRole);
        return new DriverManagerDataSource(owner.getUrl(), role, "");
    }

    private static List<MigrationChecksum> legacyMigrationChecksums(JdbcTemplate jdbc) {
        return jdbc.query("""
                SELECT version,script,checksum FROM tianji_process.tianji_process_schema_history
                WHERE version IN ('1','2','3','4','5') AND type='SQL' AND success ORDER BY installed_rank
                """, (row, index) -> new MigrationChecksum(row.getString("version"), row.getString("script"),
                row.getObject("checksum", Integer.class)));
    }

    private record MigrationChecksum(String version, String script, Integer checksum) { }

    private static Flyway flyway(DriverManagerDataSource source, String target) {
        return Flyway.configure().dataSource(source).schemas("tianji_process").defaultSchema("tianji_process")
                .table("tianji_process_schema_history").locations("classpath:db/tianji-process").target(target).cleanDisabled(true).load();
    }
    private static final class InjectedFailure extends RuntimeException { }
}
