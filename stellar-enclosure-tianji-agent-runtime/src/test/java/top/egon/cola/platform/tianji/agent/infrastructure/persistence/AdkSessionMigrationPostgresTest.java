package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.google.adk.JsonBaseModel;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor.SubjectType.USER;

@EnabledIfSystemProperty(named = "tianji.session.migration.test.url", matches = ".+")
@Timeout(60)
class AdkSessionMigrationPostgresTest {
    @Test
    void upgrades_v2_and_v3_without_rewriting_runs_sessions_or_migration_checksums() throws Exception {
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

        Flyway version3 = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").target("3").cleanDisabled(true).load();
        assertEquals(1, version3.migrate().migrationsExecuted);
        Integer v3Checksum = jdbc.queryForObject("SELECT checksum FROM tianji_agent.flyway_schema_history WHERE version='3'", Integer.class);
        jdbc.update("""
                INSERT INTO tianji_agent.agent_session (tenant_id, subject_type, subject_id, app_name, session_id,
                    session_state, revision, last_event_sequence, created_at, updated_at)
                VALUES (91, 'USER', 'alice', 'tianji', 'legacy-session', '{"count":1,"shape":["long","1"]}'::jsonb, 1, 1,
                    clock_timestamp(), clock_timestamp())
                """);
        jdbc.update("""
                INSERT INTO tianji_agent.agent_session_lease (tenant_id, subject_type, subject_id, app_name, session_id)
                VALUES (91, 'USER', 'alice', 'tianji', 'legacy-session')
                """);
        jdbc.update("""
                INSERT INTO tianji_agent.agent_session_scope_state
                    (tenant_id, app_name, scope_type, subject_type, subject_id, state_key, state_value)
                VALUES (91, 'tianji', 'USER', 'USER', 'alice', 'color', '"blue"'::jsonb)
                """);
        Event legacyEvent = Event.builder().id("legacy-event").invocationId("legacy-invocation").author("agent")
                .timestamp(1_000).content(Content.fromParts(Part.fromText("legacy")))
                .actions(EventActions.builder().stateDelta(Map.of("count", 1)).build()).build();
        var envelope = JsonBaseModel.getMapper().createObjectNode();
        envelope.put("codecVersion", 1);
        envelope.put("adkVersion", "1.13.0");
        envelope.set("event", JsonBaseModel.getMapper().readTree(legacyEvent.toJson()));
        envelope.putArray("removedStateKeys");
        jdbc.update("""
                INSERT INTO tianji_agent.agent_session_event
                    (tenant_id, subject_type, subject_id, app_name, session_id, sequence, native_event_id, event_timestamp_ms, payload)
                VALUES (91, 'USER', 'alice', 'tianji', 'legacy-session', 1, 'legacy-event', 1000, ?)
                """, envelope.toString());

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
        assertEquals(v3Checksum, jdbc.queryForObject("SELECT checksum FROM tianji_agent.flyway_schema_history WHERE version='3'", Integer.class));
        assertEquals("legacy-session", jdbc.queryForObject("SELECT session_id FROM tianji_agent.agent_run "
                + "WHERE tenant_id=91 AND run_id='legacy-run'", String.class));
        assertEquals("QUEUED", jdbc.queryForObject("SELECT status FROM tianji_agent.agent_run "
                + "WHERE tenant_id=91 AND run_id='legacy-run'", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT state_codec_version FROM tianji_agent.agent_session WHERE session_id='legacy-session'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT value_codec_version FROM tianji_agent.agent_session_scope_state WHERE state_key='color'", Integer.class));

        TrustedAgentActor owner = new TrustedAgentActor(91, USER, "alice");
        PostgresAdkSessionService sessions = new PostgresAdkSessionService(source, owner, "tianji");
        var restored = sessions.getSession("tianji", sessions.adkUserId(), "legacy-session", Optional.empty()).blockingGet();
        assertEquals(1, restored.state().get("count"));
        assertEquals(List.of("long", "1"), restored.state().get("shape"));
        assertEquals("blue", restored.state().get("user:color"));
        assertEquals("legacy-event", restored.immutableEvents().getFirst().id());
        assertEquals(AgentErrorCodeEnum.TIANJI_SESSION_BUSY, assertThrows(AgentLedgerException.class,
                () -> sessions.deleteSession("tianji", sessions.adkUserId(), "legacy-session").blockingAwait()).code());
    }
}
