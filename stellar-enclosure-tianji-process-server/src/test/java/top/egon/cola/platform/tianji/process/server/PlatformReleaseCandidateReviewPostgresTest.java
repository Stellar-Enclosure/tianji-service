package top.egon.cola.platform.tianji.process.server;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewPostgresScenarios;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in storage acceptance against a fresh loopback database and a pre-provisioned temporary runtime role. */
@EnabledIfSystemProperty(named = "tianji.process.postgres.test.url", matches = ".+")
@Isolated("Owns a fresh Process schema and its disposable runtime grants")
@Timeout(60)
class PlatformReleaseCandidateReviewPostgresTest {
    private static final String SCHEMA = "tianji_process";
    private static final String HISTORY = SCHEMA + ".tianji_process_schema_history";
    private static final String CANDIDATE = SCHEMA + ".tianji_platform_release_candidate";
    private static final String REVIEW = SCHEMA + ".tianji_platform_release_review";

    @Test
    @EnabledIfSystemProperty(named = "tianji.process.postgres.candidate-review", matches = "true")
    void upgrades_v10_and_serializes_real_independent_review_writes() throws Exception {
        String url = ProcessServerPostgresTest.explicitTestUrl();
        var ownerSource = new DriverManagerDataSource(url);
        var owner = new JdbcTemplate(ownerSource);
        owner.setQueryTimeout(3);
        assertEquals(Boolean.TRUE, owner.queryForObject("""
                SELECT NOT pg_catalog.pg_is_in_recovery()
                  AND pg_catalog.current_setting('server_encoding')='UTF8'
                  AND pg_catalog.current_setting('session_replication_role')='origin'
                """, Boolean.class));
        if (owner.queryForObject("SELECT count(*) FROM pg_catalog.pg_namespace WHERE nspname=?", Integer.class, SCHEMA) != 0) {
            throw new IllegalStateException("candidate review acceptance requires a fresh database without the Process schema");
        }

        var v10 = flyway(ownerSource, "10");
        v10.baseline();
        assertEquals(10, v10.migrate().migrationsExecuted);
        v10.validate();
        assertEquals("10", v10.info().current().getVersion().getVersion());
        var baseline = owner.queryForList("SELECT * FROM " + HISTORY + " ORDER BY installed_rank");

        String role = ProcessServerPostgresTest.explicitRuntimeRole(owner);
        assertEquals(Boolean.TRUE, owner.queryForObject("""
                SELECT NOT rolreplication AND NOT rolbypassrls
                FROM pg_catalog.pg_roles WHERE rolname=?
                """, Boolean.class, role));
        String quotedRole = "\"" + role + "\"";
        owner.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + quotedRole);
        owner.execute("GRANT SELECT ON " + HISTORY + ", " + SCHEMA + ".tianji_process_release, "
                + SCHEMA + ".tianji_process_release_definition TO " + quotedRole);

        var runtime = new DriverManagerDataSource(url + "?currentSchema=" + SCHEMA);
        runtime.setUsername(role);
        var runtimeIdentity = new JdbcTemplate(runtime);
        runtimeIdentity.setQueryTimeout(3);
        assertEquals(role, runtimeIdentity.queryForObject("SELECT current_user", String.class));
        var verifier = new ProcessApiSchemaVerifier(runtime, SCHEMA);
        assertTrue(verifier.hasCaseMigration(), "the existing exact-history reader recognizes V10");
        assertThrows(IllegalStateException.class, verifier::verifyPlatformCandidateReviews);

        var v11 = flyway(ownerSource, "11");
        assertEquals(1, v11.migrate().migrationsExecuted);
        v11.validate();
        assertEquals("11", v11.info().current().getVersion().getVersion());
        assertEquals(baseline, owner.queryForList("SELECT * FROM " + HISTORY + " WHERE version IS DISTINCT FROM '11' ORDER BY installed_rank"));
        assertTrue(verifier.hasCaseMigration(), "recognizing V11 must not require candidate grants");
        assertThrows(IllegalStateException.class, verifier::verifyPlatformCandidateReviews, "candidate tables are still inaccessible");

        owner.execute("GRANT SELECT, INSERT ON " + CANDIDATE + ", " + REVIEW + " TO " + quotedRole);
        verifier.verifyPlatformCandidateReviews();
        assertThrows(IllegalStateException.class,
                () -> new ProcessApiSchemaVerifier(ownerSource, SCHEMA).verifyPlatformCandidateReviews(),
                "schema owner cannot be the append-only runtime");
        rejectColumnGrant(owner, verifier, CANDIDATE, "UPDATE", "submitter_subject", quotedRole);
        rejectColumnGrant(owner, verifier, REVIEW, "REFERENCES", "candidate_sha256", quotedRole);
        verifier.verifyPlatformCandidateReviews();

        for (String table : new String[] {CANDIDATE, REVIEW}) {
            assertEquals(Boolean.TRUE, owner.queryForObject("SELECT has_table_privilege(?, ?, 'SELECT')"
                    + " AND has_table_privilege(?, ?, 'INSERT')", Boolean.class, role, table, role, table));
            assertFalse(owner.queryForObject("SELECT has_table_privilege(?, ?, 'UPDATE, DELETE, TRUNCATE, TRIGGER, REFERENCES')",
                    Boolean.class, role, table));
            assertFalse(owner.queryForObject("SELECT has_any_column_privilege(?, ?, 'UPDATE, REFERENCES')",
                    Boolean.class, role, table));
        }
        PlatformReleaseCandidateReviewPostgresScenarios.verify(owner, runtime);
    }

    private static Flyway flyway(DataSource source, String target) {
        return Flyway.configure().dataSource(source).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_process_schema_history").baselineVersion("0").locations("classpath:db/tianji-process")
                .target(target).cleanDisabled(true).load();
    }

    private static void rejectColumnGrant(JdbcTemplate owner, ProcessApiSchemaVerifier verifier,
                                          String table, String permission, String column, String role) {
        String columnGrant = permission + " (" + column + ") ON " + table;
        owner.execute("GRANT " + columnGrant + " TO " + role);
        try {
            assertThrows(IllegalStateException.class, verifier::verifyPlatformCandidateReviews);
        } finally {
            owner.execute("REVOKE " + columnGrant + " FROM " + role);
        }
    }
}
