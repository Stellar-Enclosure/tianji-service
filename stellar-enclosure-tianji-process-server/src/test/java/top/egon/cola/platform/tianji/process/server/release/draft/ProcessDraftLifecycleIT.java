package top.egon.cola.platform.tianji.process.server.release.draft;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.mapstruct.factory.Mappers;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.CreateProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DiscardProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftContentVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.SaveProcessDraftCommand;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(120)
@Isolated("Requires one explicitly provisioned and empty loopback PostgreSQL database")
class ProcessDraftLifecycleIT {
    private static final String SCHEMA = "tianji_process";
    private static final String ENABLED = "stellar.tianji.process.automation.enabled";
    private static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "editor", "https://idp.test/main");
    private static final ProcessActor OTHER_ISSUER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "editor", "https://idp.test/other");
    private static final ProcessActor OTHER_TENANT = ProcessActor.verified(21, ProcessActor.SubjectType.USER, "editor", "https://idp.test/main");
    private static final ProcessActor OTHER_TYPE = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "editor", "https://idp.test/main");

    @Test
    void owner_keeps_incomplete_content_cas_and_original_commands_across_reopening() throws Exception {
        var db = provision();
        var environment = new MockEnvironment().withProperty(ENABLED, "true");
        var first = service(db, environment);
        var create = CreateProcessDraftCommand.builder().title("Unfinished").commandId("create-1").build();
        var created = first.create(OWNER, "review", create);
        String draft = created.getDraftId();
        assertTrue(draft.startsWith("pd"));
        assertEquals(1, created.getRevision());
        assertNull(created.getBaseReleaseVersion());

        var fragment = new DraftContentVO(List.of(new DraftModelSourceVO("BPMN", "🚩".repeat(65), "  <process\n")), " { \"type\":\n",
                null, "[", " ");
        var save = new SaveProcessDraftCommand("save-1", 1, "Draft changed", fragment);
        var saved = first.save(OWNER, draft, save);
        assertEquals(2, saved.getRevision());
        assertNotEquals(created.getContentSha256(), saved.getContentSha256());

        var reopened = service(db, environment);
        var fetched = reopened.get(OWNER, draft, "save-1");
        assertEquals("Draft changed", fetched.getTitle());
        assertEquals(fragment, fetched.getContent());
        assertEquals(saved.getContentSha256(), fetched.getContentSha256());
        assertEquals(2, fetched.getRevision());
        assertEquals(2, fetched.getCommandReceipt().getResultRevision());
        assertEquals("save-1", fetched.getLastCommandId());
        assertEquals(saved, reopened.save(OWNER, draft, save));
        assertEquals(created, reopened.create(OWNER, "review", create));
        assertEquals(2, reopened.list(OWNER, null, null, null, null, "create-1").getItems().getFirst().getRevision());
        assertEquals(1, db.runtime().queryForObject("SELECT count(*) FROM tianji_process.tianji_process_draft", Integer.class));
        for (ProcessActor rejected : List.of(OTHER_ISSUER, OTHER_TENANT, OTHER_TYPE)) {
            assertThrows(ProcessResourceNotFoundException.class, () -> reopened.get(rejected, draft, null));
            assertTrue(reopened.list(rejected, null, null, null, null, "create-1").getItems().isEmpty());
        }
        assertThrows(ProcessResourceNotFoundException.class, () -> reopened.get(OTHER_ISSUER, draft, "save-1"));
        assertThrows(ProcessConflictException.class, () -> reopened.create(OWNER, "review",
                CreateProcessDraftCommand.builder().title("Changed request").commandId("create-1").build()));
        assertThrows(ProcessConflictException.class, () -> reopened.discard(OWNER, draft, new DiscardProcessDraftCommand("save-1", 2)));
        assertThrows(ProcessResourceNotFoundException.class, () -> reopened.create(OWNER, "review",
                CreateProcessDraftCommand.builder().title("Copy").baseReleaseVersion("pending").commandId("future-copy").build()));
        assertEquals(1, db.runtime().queryForObject("SELECT count(*) FROM tianji_process.tianji_process_draft", Integer.class));

        var start = new CountDownLatch(1);
        var entered = new CountDownLatch(2);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var left = pool.submit(() -> compete(first, draft, "racer-1", fragment, entered, start));
            var right = pool.submit(() -> compete(reopened, draft, "racer-2", fragment, entered, start));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (left.get(25, TimeUnit.SECONDS) ? 1 : 0) + (right.get(25, TimeUnit.SECONDS) ? 1 : 0));
        }
        assertEquals(3, reopened.get(OWNER, draft, null).getRevision());
        assertEquals(2, reopened.get(OWNER, draft, "save-1").getCommandReceipt().getResultRevision());
        assertEquals(0, db.runtime().queryForObject("SELECT count(*) FROM tianji_process.tianji_process_automation_release", Integer.class));
        assertEquals(0, db.runtime().queryForObject("SELECT count(*) FROM tianji_process.tianji_process_automation_activation", Integer.class));
        assertEquals(0, db.owner().queryForObject("SELECT count(*) FROM tianji_process.act_re_deployment", Integer.class));
        assertThrows(DataAccessException.class, () -> db.runtime().update("DELETE FROM tianji_process.tianji_process_draft_command"));

        var discarded = reopened.discard(OWNER, draft, new DiscardProcessDraftCommand("discard-1", 3));
        assertEquals(4, discarded.getRevision());
        assertEquals("DISCARDED", discarded.getState().name());
        var historical = reopened.create(OWNER, "review", create);
        assertEquals(1, historical.getRevision());
        assertEquals("Unfinished", historical.getTitle());
        assertTrue(historical.getAvailableActions().isEmpty());
        assertEquals(4, reopened.list(OWNER, null, null, null, null, "create-1").getItems().getFirst().getRevision());
        assertTrue(reopened.list(OWNER, null, null, 100, null, "save-1").getItems().isEmpty());

        var later1 = reopened.create(OWNER, "review", new CreateProcessDraftCommand("Page one", null, "page-one"));
        var later2 = reopened.create(OWNER, "review", new CreateProcessDraftCommand("Page two", null, "page-two"));
        var page1 = reopened.list(OWNER, "review", null, 1, null, null);
        assertEquals(List.of(later2.getDraftId()), page1.getItems().stream().map(row -> row.getDraftId()).toList());
        String next = page1.getNextCursor();
        assertNotNull(next);
        for (ProcessActor rejected : List.of(OTHER_ISSUER, OTHER_TENANT, OTHER_TYPE)) {
            assertThrows(IllegalArgumentException.class, () -> reopened.list(rejected, "review", "EDITING", 100, next, null));
        }
        assertThrows(IllegalArgumentException.class, () -> reopened.list(OWNER, "review", "DISCARDED", 100, next, null));
        assertThrows(IllegalArgumentException.class, () -> reopened.list(OWNER, "other", "EDITING", 100, next, null));
        assertThrows(IllegalArgumentException.class, () -> reopened.list(OWNER, "review", "EDITING", 100, next + "=x", null));
        reopened.create(OWNER, "review", new CreateProcessDraftCommand("After ceiling", null, "page-new"));
        var page2 = first.list(OWNER, "review", "EDITING", 100, next, null);
        assertEquals(List.of(later1.getDraftId()), page2.getItems().stream().map(row -> row.getDraftId()).toList());
        assertNull(page2.getNextCursor());

        // Command receipt uniqueness applies across draft IDs, including simultaneous first CREATE with identical intent.
        var concurrentCreate = new CreateProcessDraftCommand("One creation", null, "concurrent-create");
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var go = new CountDownLatch(1);
            var left = pool.submit(() -> { assertTrue(go.await(5, TimeUnit.SECONDS)); return first.create(OWNER, "concurrent", concurrentCreate); });
            var right = pool.submit(() -> { assertTrue(go.await(5, TimeUnit.SECONDS)); return reopened.create(OWNER, "concurrent", concurrentCreate); });
            go.countDown();
            assertEquals(left.get(25, TimeUnit.SECONDS), right.get(25, TimeUnit.SECONDS));
        }
        assertEquals(1, db.runtime().queryForObject("SELECT count(*) FROM tianji_process.tianji_process_draft WHERE definition_key='concurrent'", Integer.class));
        assertEquals(1, db.runtime().queryForObject("SELECT count(*) FROM tianji_process.tianji_process_draft_command WHERE command_id='concurrent-create'", Integer.class));
        assertEquals(Boolean.TRUE, db.runtime().queryForObject("""
                SELECT result_json->>'draftId'=draft_id AND NOT result_json?'content' AND result_json?'releaseVersion'
                FROM tianji_process.tianji_process_draft_command WHERE command_id='concurrent-create'
                """, Boolean.class));
        db.owner().update("UPDATE tianji_process.tianji_process_draft SET content_sha256=? WHERE draft_id=?", "f".repeat(64), later1.getDraftId());
        assertThrows(IllegalStateException.class, () -> reopened.get(OWNER, later1.getDraftId(), null));
        assertThrows(IllegalStateException.class, () -> first.discard(OWNER, later1.getDraftId(), new DiscardProcessDraftCommand("bad-stored", 1)));

        environment.setProperty(ENABLED, "false");
        assertEquals(discarded, reopened.discard(OWNER, draft, new DiscardProcessDraftCommand("discard-1", 3)));
        assertTrue(reopened.get(OWNER, draft, "create-1").getAvailableActions().isEmpty());
        assertEquals(List.of("VALIDATE"), reopened.get(OWNER, later2.getDraftId(), null).getAvailableActions());
        assertThrows(AccessDeniedException.class, () -> reopened.create(OWNER, "review",
                CreateProcessDraftCommand.builder().title("Closed").commandId("new-command").build()));
    }

    private static boolean compete(ProcessDraftService service, String id, String commandId, DraftContentVO fragment,
                                   CountDownLatch entered, CountDownLatch start) throws InterruptedException {
        entered.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            service.save(OWNER, id, new SaveProcessDraftCommand(commandId, 2, "Concurrent edit", fragment));
            return true;
        } catch (AutomationOperationException conflict) {
            assertEquals(AutomationOperationException.Code.TIANJI_DRAFT_VERSION_CONFLICT, conflict.code());
            return false;
        }
    }

    private static ProcessDraftService service(TestDatabaseVO db, MockEnvironment environment) {
        var originalSource = db.runtimeSource();
        var reopenedSource = source(originalSource.getUrl(), originalSource.getUsername());
        var reopenedJdbc = new JdbcTemplate(reopenedSource);
        reopenedJdbc.setQueryTimeout(10);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(reopenedSource));
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var verifier = new ProcessApiSchemaVerifier(reopenedSource, SCHEMA);
        return new ProcessDraftService(new ProcessDraftStore(reopenedJdbc, SCHEMA),
                new ProcessDraftCommandReceiptStore(reopenedJdbc, SCHEMA), new ProcessDraftContentCodec(),
                Mappers.getMapper(ProcessDraftConverter.class), verifier, tx, environment);
    }

    private static TestDatabaseVO provision() {
        String url = System.getProperty("tianji.process.postgres.test.url", "");
        String role = System.getProperty("tianji.process.postgres.test.runtime-user", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")
                || !role.matches("tianji_it_runtime_[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Draft lifecycle requires a dedicated loopback test database and runtime role");
        }
        var ownerSource = source(url, null);
        var owner = new JdbcTemplate(ownerSource);
        owner.setQueryTimeout(10);
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT current_database() LIKE 'tianji_it_%' AND NOT pg_is_in_recovery()
                   AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'
                """, Boolean.class)));
        assertEquals(0, owner.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, SCHEMA));
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT rolcanlogin AND NOT rolsuper AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication
                   AND NOT rolbypassrls AND NOT has_database_privilege(rolname,current_database(),'CREATE')
                   AND NOT has_schema_privilege(rolname,'public','CREATE')
                FROM pg_roles WHERE rolname=?
                """, Boolean.class, role)));
        assertEquals(0, owner.queryForObject("""
                SELECT count(*) FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname=?)
                """, Integer.class, role));
        owner.execute("CREATE SCHEMA " + SCHEMA);
        var flyway = Flyway.configure().dataSource(ownerSource).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_process_schema_history").createSchemas(false).baselineOnMigrate(false)
                .locations("classpath:db/tianji-process").target("14").cleanDisabled(true).load();
        assertEquals(14, flyway.migrate().migrationsExecuted);
        flyway.validate();

        String scopedUrl = url + "?currentSchema=" + SCHEMA;
        try (var ignored = new SpringApplicationBuilder(NativeTestProvisioner.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + scopedUrl, "--spring.datasource.username=" + System.getProperty("user.name"),
                "--spring.main.banner-mode=off", "--flowable.database-schema-update=true",
                "--egon.cola.component.transactional-outbox.enabled=false")) {
            assertNotNull(ignored.getBean(org.flowable.engine.RepositoryService.class));
        }
        String quoted = "\"" + role + "\"";
        owner.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + quoted);
        grant(owner, quoted, "SELECT", "tianji_process_schema_history", "tianji_platform_release_activation");
        grant(owner, quoted, "SELECT,INSERT,UPDATE", "tianji_process_release", "tianji_process_release_definition",
                "tianji_process_command_receipt", "tianji_process_draft", "tianji_process_node_execution");
        grant(owner, quoted, "SELECT,INSERT", "tianji_governed_process_start", "tianji_process_automation_release",
                "tianji_process_draft_command", "tianji_process_automation_activation", "tianji_process_instance_binding");
        owner.execute("GRANT UPDATE (created_at) ON " + SCHEMA + ".tianji_process_instance_binding TO " + quoted);
        var runtimeSource = source(scopedUrl, role);
        var runtime = new JdbcTemplate(runtimeSource);
        runtime.setQueryTimeout(10);
        new ProcessApiSchemaVerifier(runtimeSource, SCHEMA).verifyAutomationV14();
        return new TestDatabaseVO(owner, runtime, runtimeSource);
    }

    private static void grant(JdbcTemplate jdbc, String role, String privilege, String... tables) {
        for (String table : tables) jdbc.execute("GRANT " + privilege + " ON " + SCHEMA + "." + table + " TO " + role);
    }

    private static DriverManagerDataSource source(String url, String role) {
        var source = new DriverManagerDataSource(url);
        if (role != null) source.setUsername(role);
        var settings = new Properties();
        settings.setProperty("connectTimeout", "5");
        settings.setProperty("socketTimeout", "15");
        source.setConnectionProperties(settings);
        return source;
    }

    private record TestDatabaseVO(JdbcTemplate owner, JdbcTemplate runtime, DriverManagerDataSource runtimeSource) { }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class NativeTestProvisioner { }
}
