package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor.SubjectType.USER;

@EnabledIfSystemProperty(named = "tianji.session.migration.test.url", matches = ".+")
@Timeout(60)
class AdkSessionMigrationPostgresTest {
    @Test
    void upgrades_v2_without_rewriting_existing_runs_or_migration_checksums() {
        String url = System.getProperty("tianji.session.migration.test.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("migration test requires an explicit loopback tianji_it_ database");
        }
        DriverManagerDataSource source = new DriverManagerDataSource(url);
        JdbcTemplate jdbc = new JdbcTemplate(source);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class) != 0) {
            throw new IllegalStateException("migration test requires a fresh database without the Agent schema");
        }
        Flyway old = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").target("2").cleanDisabled(true).load();
        old.migrate();
        old.validate();
        Map<String, Integer> before = jdbc.query("SELECT version, checksum FROM tianji_agent.flyway_schema_history "
                        + "WHERE version IN ('1', '2')", rs -> {
                    Map<String, Integer> result = new java.util.HashMap<>();
                    while (rs.next()) result.put(rs.getString("version"), rs.getInt("checksum"));
                    return result;
                });
        assertEquals(2, before.size());
        jdbc.update("INSERT INTO tianji_agent.agent_definition (tenant_id, definition_key, created_at) "
                + "VALUES (91, 'legacy', clock_timestamp())");
        jdbc.update("""
                INSERT INTO tianji_agent.agent_definition_version
                    (tenant_id, definition_key, version, content_sha256, specification, created_at)
                VALUES (91, 'legacy', 1, ?, '{}'::jsonb, clock_timestamp())
                """, "a".repeat(64));
        jdbc.update("""
                INSERT INTO tianji_agent.agent_run
                    (tenant_id, run_id, definition_key, definition_version, subject_type, subject_id, idempotency_key,
                     request_hash, session_id, status, deadline_at, execution_snapshot, created_at)
                VALUES (91, 'legacy-run', 'legacy', 1, 'USER', 'alice', 'legacy-request', ?,
                        'legacy-session', 'QUEUED', clock_timestamp()+interval '1 hour', '{}'::jsonb, clock_timestamp())
                """, "b".repeat(64));

        Flyway current = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").cleanDisabled(true).load();
        assertEquals(1, current.migrate().migrationsExecuted);
        current.validate();
        Map<String, Integer> after = jdbc.query("SELECT version, checksum FROM tianji_agent.flyway_schema_history "
                        + "WHERE version IN ('1', '2')", rs -> {
                    Map<String, Integer> result = new java.util.HashMap<>();
                    while (rs.next()) result.put(rs.getString("version"), rs.getInt("checksum"));
                    return result;
                });
        assertEquals(before, after);
        assertEquals("legacy-session", jdbc.queryForObject("SELECT session_id FROM tianji_agent.agent_run "
                + "WHERE tenant_id=91 AND run_id='legacy-run'", String.class));
        assertEquals("QUEUED", jdbc.queryForObject("SELECT status FROM tianji_agent.agent_run "
                + "WHERE tenant_id=91 AND run_id='legacy-run'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_session", Integer.class));

        TrustedAgentActor owner = new TrustedAgentActor(91, USER, "alice");
        PostgresAdkSessionService sessions = new PostgresAdkSessionService(source, owner, "tianji");
        sessions.createSession("tianji", sessions.adkUserId(), Map.of(), "legacy-session").blockingGet();
        assertEquals(AgentErrorCodeEnum.TIANJI_SESSION_BUSY, assertThrows(AgentLedgerException.class,
                () -> sessions.deleteSession("tianji", sessions.adkUserId(), "legacy-session").blockingAwait()).code());
    }
}
