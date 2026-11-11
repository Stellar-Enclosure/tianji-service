package top.egon.cola.platform.tianji.process.server;

import org.flywaydb.core.Flyway;
import org.flowable.cmmn.api.CmmnRepositoryService;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.egon.cola.platform.tianji.process.server.release.PlatformActivationGovernedStartPostgresScenarios;

import javax.sql.DataSource;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Opt-in native acceptance that owns the schema of one disposable loopback database. */
@EnabledIfSystemProperty(named = "tianji.process.postgres.test.url", matches = ".+")
@Isolated("Owns a fresh Process schema and disposable runtime grants")
@Timeout(120)
class PlatformActivationGovernedStartPostgresTest {
    private static final String SCHEMA = "tianji_process";
    private static final String HISTORY = SCHEMA + ".tianji_process_schema_history";
    private static final String ACTIVATION = SCHEMA + ".tianji_platform_release_activation";
    private static final String START = SCHEMA + ".tianji_governed_process_start";

    @Test
    @EnabledIfSystemProperty(named = "tianji.process.postgres.activation-start", matches = "true")
    void rolls_back_the_pointer_and_keeps_each_native_start_pinned_to_its_revision() throws Exception {
        String url = ProcessServerPostgresTest.explicitTestUrl();
        var ownerSource = new DriverManagerDataSource(url);
        var owner = new JdbcTemplate(ownerSource);
        owner.setQueryTimeout(4);
        assertEquals(Boolean.TRUE, owner.queryForObject("""
                SELECT NOT pg_catalog.pg_is_in_recovery()
                  AND pg_catalog.current_setting('server_encoding')='UTF8'
                  AND pg_catalog.current_setting('session_replication_role')='origin'
                """, Boolean.class));
        if (owner.queryForObject("SELECT count(*) FROM pg_catalog.pg_namespace WHERE nspname=?",
                Integer.class, SCHEMA) != 0) {
            throw new IllegalStateException("activation and START acceptance requires a fresh database without the Process schema");
        }

        owner.execute("CREATE SCHEMA " + SCHEMA);
        var v11 = platform(ownerSource, "11");
        assertEquals(11, v11.migrate().migrationsExecuted);
        v11.validate();
        assertHistory(owner, 11);
        var oldHistory = owner.queryForList("SELECT * FROM " + HISTORY + " ORDER BY installed_rank");

        // Platform already owns its marker-free history. Outbox has a distinct history in the same schema.
        var outbox = Flyway.configure().dataSource(ownerSource).schemas(SCHEMA).defaultSchema(SCHEMA)
                .locations("classpath:db/transactional-outbox/postgresql").createSchemas(false)
                .baselineVersion("0").cleanDisabled(true).load();
        outbox.baseline();
        outbox.migrate();
        outbox.validate();
        String scopedUrl = url + "?currentSchema=" + SCHEMA;
        try (var provisioner = ProcessServerPostgresTest.context(ProcessServerPostgresTest.Provisioner.class,
                scopedUrl, System.getProperty("user.name"), "--flowable.database-schema-update=true",
                "--egon.cola.component.transactional-outbox.enabled=false")) {
            assertNotNull(provisioner.getBean(RepositoryService.class));
            assertNotNull(provisioner.getBean(CmmnRepositoryService.class));
            assertNotNull(provisioner.getBean(DmnRepositoryService.class));
        }

        String role = ProcessServerPostgresTest.explicitRuntimeRole(owner);
        assertEquals(Boolean.TRUE, owner.queryForObject("""
                SELECT NOT rolreplication AND NOT rolbypassrls FROM pg_catalog.pg_roles WHERE rolname=?
                """, Boolean.class, role));
        String quotedRole = "\"" + role + "\"";
        grantBaseline(owner, quotedRole);
        var runtime = new DriverManagerDataSource(scopedUrl);
        runtime.setUsername(role);
        assertEquals(role, new JdbcTemplate(runtime).queryForObject("SELECT current_user", String.class));
        var verifier = new ProcessApiSchemaVerifier(runtime, SCHEMA);
        verifier.verifyPlatformCandidateReviews();
        assertThrows(IllegalStateException.class, verifier::verifyPlatformActivation);
        assertThrows(IllegalStateException.class, verifier::verifyGovernedProcessStart);

        var v12 = platform(ownerSource, "12");
        assertEquals(1, v12.migrate().migrationsExecuted);
        v12.validate();
        assertHistory(owner, 12);
        assertThrows(IllegalStateException.class, verifier::verifyPlatformActivation);
        owner.execute("GRANT SELECT, INSERT ON " + ACTIVATION + " TO " + quotedRole);
        verifier.verifyPlatformActivation();
        assertThrows(IllegalStateException.class, verifier::verifyGovernedProcessStart);

        var v13 = platform(ownerSource, "13");
        assertEquals(1, v13.migrate().migrationsExecuted);
        v13.validate();
        assertHistory(owner, 13);
        assertEquals(oldHistory, owner.queryForList("SELECT * FROM " + HISTORY
                + " WHERE version NOT IN ('12','13') ORDER BY installed_rank"));
        assertThrows(IllegalStateException.class, verifier::verifyGovernedProcessStart);
        owner.execute("GRANT SELECT, INSERT ON " + START + " TO " + quotedRole);
        verifier.verifyGovernedProcessStart();
        for (String table : new String[]{ACTIVATION, START}) {
            assertEquals(Boolean.FALSE, owner.queryForObject("""
                    SELECT has_table_privilege(?, ?, 'UPDATE, DELETE, TRUNCATE, TRIGGER, REFERENCES')
                        OR has_any_column_privilege(?, ?, 'UPDATE, REFERENCES')
                    """, Boolean.class, role, table, role, table));
        }

        try (var app = ProcessServerPostgresTest.context(TianjiProcessServerApplication.class, scopedUrl, role,
                "--flowable.history-level=full", "--flowable.async-executor-activate=true")) {
            PlatformActivationGovernedStartPostgresScenarios.verify(owner,
                    app.getBean(SpringProcessEngineConfiguration.class),
                    app.getBean(SpringDmnEngineConfiguration.class));
        }
    }

    private static void grantBaseline(JdbcTemplate owner, String role) {
        owner.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + role);
        owner.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA " + SCHEMA + " TO " + role);
        owner.execute("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA " + SCHEMA + " TO " + role);
        owner.execute("REVOKE INSERT, UPDATE, DELETE ON " + HISTORY + ", " + SCHEMA + ".flyway_schema_history FROM " + role);
        owner.execute("REVOKE UPDATE, DELETE ON " + SCHEMA + ".tianji_platform_release_candidate, "
                + SCHEMA + ".tianji_platform_release_review FROM " + role);
    }

    private static Flyway platform(DataSource source, String target) {
        return Flyway.configure().dataSource(source).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_process_schema_history").createSchemas(false).baselineOnMigrate(false)
                .locations("classpath:db/tianji-process").target(target).cleanDisabled(true).load();
    }

    private static void assertHistory(JdbcTemplate owner, int target) {
        var expected = IntStream.rangeClosed(1, target).mapToObj(version -> version + ":SQL").toList();
        assertEquals(expected, owner.queryForList("SELECT concat(version,':',type) FROM " + HISTORY
                + " ORDER BY installed_rank", String.class));
    }
}
