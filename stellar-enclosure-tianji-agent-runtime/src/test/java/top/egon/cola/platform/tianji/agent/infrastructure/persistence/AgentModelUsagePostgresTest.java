package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compile-only in finite gates. Actual execution requires separate authorization and an empty isolated database. */
@EnabledIfSystemProperty(named = "tianji.postgres.test.url", matches = ".+")
@EnabledIfSystemProperty(named = "tianji.postgres.test.model-usage.authorized", matches = "true")
@Timeout(120)
class AgentModelUsagePostgresTest {
    private static final String HISTORY = """
            SELECT version,type,script,checksum,success FROM tianji_agent.flyway_schema_history
            WHERE version IS NOT NULL ORDER BY installed_rank
            """;
    private static final String TABLE = "tianji_agent.agent_model_usage";
    private static Database isolated;

    @Test void v15MigrationOnIsolatedPostgres() {
        var db = modelUsageDatabase();
        var enabled = new AgentStorageVerifier(db.runtime());
        assertDoesNotThrow(enabled::verifyModelUsage);
        assertDoesNotThrow(new AgentStorageVerifier(db.setup())::verify);
        assertThrows(AgentLedgerException.class, new AgentStorageVerifier(db.setup())::verifyModelUsage);

        var jdbc = db.jdbc();
        var tx = new TransactionTemplate(new DataSourceTransactionManager(db.setup()));
        tx.executeWithoutResult(status -> {
            try {
                // These are isolated constraint fixtures, not publication, authorization or settlement proof.
                jdbc.update("""
                        INSERT INTO tianji_agent.agent_definition (tenant_id,definition_key,created_at)
                        VALUES (42,'usage-fixture',clock_timestamp())
                        """);
                jdbc.update("""
                        INSERT INTO tianji_agent.agent_definition_version
                          (tenant_id,definition_key,version,content_sha256,specification,created_at)
                        VALUES (42,'usage-fixture',1,repeat('a',64),'{}'::jsonb,clock_timestamp())
                        """);
                jdbc.update("""
                        INSERT INTO tianji_agent.agent_run
                          (tenant_id,run_id,definition_key,definition_version,subject_type,subject_issuer,subject_id,
                           idempotency_key,request_hash,status,deadline_at,execution_snapshot,created_at)
                        VALUES (42,'usage-v15-fixture','usage-fixture',1,'USER',repeat('b',64),'owner',
                                'usage-fixture',repeat('c',64),'QUEUED',clock_timestamp()+interval '1 minute','{}'::jsonb,clock_timestamp())
                        """);
                for (String slot : List.of("valid", "invalid", "overflow")) {
                    jdbc.update("""
                            INSERT INTO tianji_agent.agent_budget_reservation
                              (tenant_id,run_id,reservation_id,lease_epoch,kind,input_tokens,output_tokens,state,created_at)
                            VALUES (42,'usage-v15-fixture',?,1,'MODEL',0,0,'RESERVED',clock_timestamp())
                            """, slot);
                }
                String base = "INSERT INTO " + TABLE
                        + " (tenant_id,run_id,reservation_id,lease_epoch,model_route_ref,model_name,created_at) "
                        + "VALUES (42,'usage-v15-fixture',?,1,'fixture-route','fixture-model',clock_timestamp())";
                assertEquals(1, jdbc.update(base, "valid"));
                assertEquals("PENDING", jdbc.queryForObject("SELECT state FROM " + TABLE
                        + " WHERE tenant_id=42 AND run_id='usage-v15-fixture' AND reservation_id='valid'", String.class));
                jdbc.execute("SAVEPOINT usage_invalid");
                try {
                    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(base, "unreserved"));
                } finally {
                    jdbc.execute("ROLLBACK TO SAVEPOINT usage_invalid");
                }
                jdbc.execute("SAVEPOINT usage_invalid_fields");
                try {
                    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                            INSERT INTO tianji_agent.agent_model_usage
                              (tenant_id,run_id,reservation_id,lease_epoch,model_route_ref,model_name,created_at,
                               price_version_ref,currency,input_rate_per_million,output_rate_per_million)
                            VALUES (42,'usage-v15-fixture','invalid',1,'fixture-route','fixture-model',clock_timestamp(),
                                    repeat('d',64),'USD','NaN'::numeric,0)
                            """));
                } finally {
                    jdbc.execute("ROLLBACK TO SAVEPOINT usage_invalid_fields");
                }
                jdbc.execute("SAVEPOINT usage_invalid_sum");
                try {
                    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                            INSERT INTO tianji_agent.agent_model_usage
                              (tenant_id,run_id,reservation_id,lease_epoch,model_route_ref,model_name,created_at,measured_at,
                               state,safe_reason,actual_input_tokens,actual_output_tokens,final_sha256)
                            VALUES (42,'usage-v15-fixture','overflow',1,'fixture-route','fixture-model',
                                    clock_timestamp(),clock_timestamp(),'UNPRICED','PRICE_MISSING',
                                    9223372036854775807,1,repeat('e',64))
                            """));
                } finally {
                    jdbc.execute("ROLLBACK TO SAVEPOINT usage_invalid_sum");
                }
            } finally {
                status.setRollbackOnly();
            }
        });
    }

    /** Keeps one verified disposable schema for the separately authorized I methods. */
    static synchronized Database modelUsageDatabase() {
        if (isolated != null) return isolated;
        String url = System.getProperty("tianji.postgres.test.url");
        String vectorVersion = System.getProperty("tianji.postgres.test.model-usage.vector.version");
        String runtimeRole = System.getProperty("tianji.postgres.test.model-usage.runtime-role");
        if (!"true".equals(System.getProperty("tianji.postgres.test.model-usage.authorized"))
                || url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")
                || vectorVersion == null || !vectorVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                || runtimeRole == null || !runtimeRole.matches("tianji_it_[a-z0-9_]{1,50}")) {
            throw new IllegalArgumentException("Agent model usage I requires explicit isolated PostgreSQL and a pre-provisioned limited role");
        }
        var setup = source(url, null);
        var jdbc = new JdbcTemplate(setup);
        jdbc.setQueryTimeout(5);
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT current_database() ~ '^tianji_it_[a-z0-9_]{1,40}$'
                  AND inet_server_addr()=inet '127.0.0.1' AND NOT pg_is_in_recovery()
                  AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'
                  AND NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='tianji_agent')
                """, Boolean.class)));
        assertEquals(vectorVersion, jdbc.queryForObject("SELECT extversion FROM pg_extension WHERE extname='vector'", String.class));
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT current_user<>? AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname=?
                  AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication)
                """, Boolean.class, runtimeRole, runtimeRole)));
        var old = flyway(setup, "14");
        assertEquals(14, old.migrate().migrationsExecuted);
        old.validate();
        var originalHistory = jdbc.queryForList(HISTORY);
        assertEquals(14, originalHistory.size());
        var base = new AgentStorageVerifier(setup);
        assertDoesNotThrow(base::verify);
        assertThrows(AgentLedgerException.class, base::verifyModelUsage);
        var current = flyway(setup, "15");
        assertEquals(1, current.migrate().migrationsExecuted);
        current.validate();
        var fullHistory = jdbc.queryForList(HISTORY);
        assertEquals(15, fullHistory.size());
        assertEquals(originalHistory, fullHistory.subList(0, 14));
        assertEquals("V15__add_agent_model_usage.sql", fullHistory.getLast().get("script"));
        assertEquals(1512602432, fullHistory.getLast().get("checksum"));
        String role = '"' + runtimeRole + '"';
        jdbc.execute("GRANT USAGE ON SCHEMA tianji_agent TO " + role);
        jdbc.execute("""
                GRANT SELECT ON tianji_agent.flyway_schema_history,
                  tianji_agent.agent_definition,tianji_agent.agent_definition_version,tianji_agent.agent_run,
                  tianji_agent.agent_event,tianji_agent.agent_run_command_receipt,tianji_agent.agent_budget_reservation,
                  tianji_agent.agent_session,tianji_agent.agent_session_event,tianji_agent.agent_session_scope_state,
                  tianji_agent.agent_session_lease,tianji_agent.agent_run_execution_grant TO
                """ + role);
        jdbc.execute("GRANT SELECT, INSERT ON " + TABLE + " TO " + role);
        jdbc.execute("GRANT UPDATE (actual_cost,actual_input_tokens,actual_output_tokens,final_sha256,measured_at,safe_reason,state) ON "
                + TABLE + " TO " + role);
        var runtime = source(url, runtimeRole);
        assertDoesNotThrow(new AgentStorageVerifier(runtime)::verifyModelUsage);
        isolated = new Database(setup, runtime, jdbc);
        return isolated;
    }

    private static DriverManagerDataSource source(String url, String username) {
        var source = new DriverManagerDataSource(url);
        if (username != null) source.setUsername(username);
        var props = new Properties();
        props.setProperty("connectTimeout", "5");
        props.setProperty("socketTimeout", "15");
        source.setConnectionProperties(props);
        return source;
    }

    private static Flyway flyway(DriverManagerDataSource source, String version) {
        return Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").placeholders(Map.of("tianjiMemoryDimensions", "2"))
                .target(version).cleanDisabled(true).load();
    }

    record Database(DriverManagerDataSource setup, DriverManagerDataSource runtime, JdbcTemplate jdbc) { }
}
