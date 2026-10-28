package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import org.springframework.jdbc.core.JdbcTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Database operations are always scoped by the verified actor and application. */
final class JdbcAdkSessionRepository {
    private final JdbcTemplate jdbc;
    private final TrustedAgentActor actor;
    private final String app;

    JdbcAdkSessionRepository(JdbcTemplate jdbc, TrustedAgentActor actor, String app) {
        this.jdbc = jdbc;
        this.actor = actor;
        this.app = app;
    }

    Instant databaseNow() {
        return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
    }

    Optional<RunFence> lockRun(String runId) {
        return jdbc.query("""
                SELECT session_id, status, cancel_requested_at, lease_owner, lease_epoch, lease_expires_at, deadline_at
                FROM tianji_agent.agent_run
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id COLLATE "C"=? AND run_id COLLATE "C"=? FOR UPDATE
                """, (rs, row) -> {
                    Timestamp expiry = rs.getTimestamp("lease_expires_at");
                    return new RunFence(rs.getString("session_id"), rs.getString("status"),
                            rs.getTimestamp("cancel_requested_at") != null, rs.getString("lease_owner"),
                            rs.getLong("lease_epoch"), expiry == null ? null : expiry.toInstant(),
                            rs.getTimestamp("deadline_at").toInstant());
                }, actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), runId).stream().findFirst();
    }

    boolean liveFences(String id, AgentRunLease run, String sessionOwner, long sessionEpoch) {
        Boolean live = jdbc.queryForObject("""
                WITH fence_clock AS (SELECT clock_timestamp() AS now)
                SELECT EXISTS (
                    SELECT 1 FROM tianji_agent.agent_run, fence_clock
                    WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id COLLATE "C"=? AND run_id COLLATE "C"=?
                      AND session_id COLLATE "C"=? AND status='RUNNING' AND cancel_requested_at IS NULL
                      AND lease_owner COLLATE "C"=? AND lease_epoch=? AND lease_expires_at > fence_clock.now
                      AND deadline_at > fence_clock.now
                ) AND EXISTS (
                    SELECT 1 FROM tianji_agent.agent_session_lease, fence_clock
                    WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=?
                      AND lease_owner COLLATE "C"=? AND lease_epoch=? AND lease_expires_at > fence_clock.now
                )
                """, Boolean.class, actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), run.runId(), id, run.owner(), run.epoch(),
                actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), app, id, sessionOwner, sessionEpoch);
        return Boolean.TRUE.equals(live);
    }

    int insertSession(String id, String state, int codecVersion) {
        return jdbc.update("""
                INSERT INTO tianji_agent.agent_session
                    (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, session_state, state_codec_version,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, clock_timestamp(), clock_timestamp())
                ON CONFLICT DO NOTHING
                """, actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), app, id, state, codecVersion);
    }

    void insertLease(String id) {
        jdbc.update("""
                INSERT INTO tianji_agent.agent_session_lease
                    (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, key(id));
    }

    Optional<SessionRow> session(String id, boolean lock) {
        String sql = "SELECT session_id, session_state::text AS session_state, state_codec_version, revision, last_event_sequence, updated_at "
                + "FROM tianji_agent.agent_session WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? "
                + "AND app_name=? AND session_id=?" + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, row) -> sessionRow(rs), key(id)).stream().findFirst();
    }

    List<SessionRow> sessions(int limit) {
        return jdbc.query("""
                SELECT session_id, '{}' AS session_state, 1 AS state_codec_version, revision, last_event_sequence, updated_at
                FROM tianji_agent.agent_session
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=?
                ORDER BY updated_at DESC, session_id DESC LIMIT ?
                """, (rs, row) -> sessionRow(rs), actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), app, limit);
    }

    int delete(String id) {
        return jdbc.update("DELETE FROM tianji_agent.agent_session WHERE tenant_id=? AND subject_type=? "
                + "AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=?", key(id));
    }

    boolean hasActiveRun(String id) {
        Boolean present = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM tianji_agent.agent_run
                    WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id COLLATE "C"=? AND session_id COLLATE "C"=?
                      AND status IN ('CREATED', 'QUEUED', 'RUNNING', 'WAITING', 'SUSPENDED'))
                """, Boolean.class, actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), id);
        return Boolean.TRUE.equals(present);
    }

    Optional<LeaseRow> acquire(String id, String owner, long millis) {
        return jdbc.query("""
                UPDATE tianji_agent.agent_session_lease
                SET lease_owner=?, lease_epoch=lease_epoch+1,
                    lease_expires_at=clock_timestamp()+(? * interval '1 millisecond')
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=?
                  AND (lease_owner IS NULL OR lease_expires_at <= clock_timestamp())
                RETURNING lease_epoch, lease_owner, lease_expires_at, true AS live
                """, (rs, row) -> leaseRow(rs), owner, millis, actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), app, id)
                .stream().findFirst();
    }

    Optional<LeaseRow> renew(String id, String owner, long epoch, long millis) {
        return jdbc.query("""
                UPDATE tianji_agent.agent_session_lease
                SET lease_expires_at=clock_timestamp()+(? * interval '1 millisecond')
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=?
                  AND lease_owner=? AND lease_epoch=? AND lease_expires_at > clock_timestamp()
                RETURNING lease_epoch, lease_owner, lease_expires_at, true AS live
                """, (rs, row) -> leaseRow(rs), millis, actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), app, id, owner, epoch)
                .stream().findFirst();
    }

    boolean release(String id, String owner, long epoch) {
        return jdbc.update("""
                UPDATE tianji_agent.agent_session_lease SET lease_owner=NULL, lease_expires_at=NULL
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=?
                  AND lease_owner=? AND lease_epoch=?
                """, actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), app, id, owner, epoch) == 1;
    }

    LeaseRow lockLease(String id) {
        return jdbc.query("""
                SELECT lease_epoch, lease_owner, lease_expires_at,
                       lease_owner IS NOT NULL AND lease_expires_at > clock_timestamp() AS live
                FROM tianji_agent.agent_session_lease
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=? FOR UPDATE
                """, (rs, row) -> leaseRow(rs), key(id)).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("ADK session lease is missing"));
    }

    Optional<EventRow> event(String id, String eventId, String payload) {
        return jdbc.query("""
                SELECT sequence, native_event_id, event_timestamp_ms, payload,
                       payload COLLATE "C" = CAST(? AS text) COLLATE "C" AS matches
                FROM tianji_agent.agent_session_event
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=? AND native_event_id=?
                """, (rs, row) -> eventRow(rs), payload, actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), app, id, eventId)
                .stream().findFirst();
    }

    EventBounds eventBounds(String id, int limit, Long afterMillis) {
        return jdbc.queryForObject("""
                WITH recent AS (
                    SELECT sequence, event_timestamp_ms, payload FROM tianji_agent.agent_session_event
                    WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=?
                    ORDER BY sequence DESC LIMIT ?
                )
                SELECT count(*) AS event_count, COALESCE(sum(octet_length(payload)), 0) AS total_bytes,
                       COALESCE(min(sequence), 0) AS first_sequence, COALESCE(max(sequence), 0) AS last_sequence
                FROM recent WHERE CAST(? AS bigint) IS NULL OR event_timestamp_ms >= ?
                """, (rs, row) -> new EventBounds(rs.getLong("event_count"), rs.getLong("total_bytes"),
                rs.getLong("first_sequence"), rs.getLong("last_sequence")),
                actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), app, id, limit, afterMillis, afterMillis);
    }

    List<EventRow> events(String id, int limit, Long afterMillis) {
        return jdbc.query("""
                WITH recent AS (
                    SELECT sequence, native_event_id, event_timestamp_ms, payload FROM tianji_agent.agent_session_event
                    WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=?
                    ORDER BY sequence DESC LIMIT ?
                )
                SELECT sequence, native_event_id, event_timestamp_ms, payload, true AS matches FROM recent
                WHERE CAST(? AS bigint) IS NULL OR event_timestamp_ms >= ? ORDER BY sequence
                """, (rs, row) -> eventRow(rs), actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), app, id,
                limit, afterMillis, afterMillis);
    }

    void insertEvent(String id, long sequence, String nativeId, long timestamp, String payload) {
        jdbc.update("""
                INSERT INTO tianji_agent.agent_session_event
                    (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, sequence,
                     native_event_id, event_timestamp_ms, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), app, id, sequence, nativeId, timestamp, payload);
    }

    SessionRow updateSession(String id, long expectedRevision, long sequence, String state, int codecVersion) {
        return jdbc.query("""
                UPDATE tianji_agent.agent_session
                SET session_state=CAST(? AS jsonb), state_codec_version=?, revision=revision+1,
                    last_event_sequence=?, updated_at=clock_timestamp()
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=? AND revision=?
                RETURNING session_id, session_state::text AS session_state, state_codec_version, revision, last_event_sequence, updated_at
                """, (rs, row) -> sessionRow(rs), state, codecVersion, sequence, actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), app,
                id, expectedRevision).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("ADK session revision changed while locked"));
    }

    Map<String, ScopeValue> scopeState() {
        Map<String, ScopeValue> values = new HashMap<>();
        jdbc.query("""
                SELECT scope_type, state_key, state_value::text AS state_value, value_codec_version
                FROM tianji_agent.agent_session_scope_state
                WHERE tenant_id=? AND app_name=? AND
                    ((scope_type='APP' AND subject_type='' AND subject_issuer='LEGACY' AND subject_id='') OR
                     (scope_type='USER' AND subject_type=? AND subject_issuer=? AND subject_id=?))
                """, rs -> {
                    values.put(("APP".equals(rs.getString("scope_type")) ? "app:" : "user:")
                            + rs.getString("state_key"), new ScopeValue(rs.getString("state_value"), rs.getInt("value_codec_version")));
                },
                actor.tenantId(), app, type(), actor.issuerFingerprint(), actor.subjectId());
        return values;
    }

    void lockScope() {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { },
                "tianji:adk-session-scope:" + actor.tenantId() + ":" + app);
    }

    ScopeBounds scopeBounds() {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE scope_type='APP') AS app_count,
                       COALESCE(sum(octet_length(state_key) + octet_length(state_value::text))
                           FILTER (WHERE scope_type='APP'), 0) AS app_bytes,
                       count(*) FILTER (WHERE scope_type='USER') AS user_count,
                       COALESCE(sum(octet_length(state_key) + octet_length(state_value::text))
                           FILTER (WHERE scope_type='USER'), 0) AS user_bytes
                FROM tianji_agent.agent_session_scope_state
                WHERE tenant_id=? AND app_name=? AND
                    ((scope_type='APP' AND subject_type='' AND subject_issuer='LEGACY' AND subject_id='') OR
                     (scope_type='USER' AND subject_type=? AND subject_issuer=? AND subject_id=?))
                """, (rs, row) -> new ScopeBounds(rs.getLong("app_count"), rs.getLong("app_bytes"),
                rs.getLong("user_count"), rs.getLong("user_bytes")),
                actor.tenantId(), app, type(), actor.issuerFingerprint(), actor.subjectId());
    }

    void putScope(String scope, String key, String value, int codecVersion) {
        String subjectType = "APP".equals(scope) ? "" : type();
        String issuer = "APP".equals(scope) ? "LEGACY" : actor.issuerFingerprint();
        String subjectId = "APP".equals(scope) ? "" : actor.subjectId();
        jdbc.update("""
                INSERT INTO tianji_agent.agent_session_scope_state
                    (tenant_id, app_name, scope_type, subject_type, subject_issuer, subject_id, state_key, state_value, value_codec_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                ON CONFLICT (tenant_id, app_name, scope_type, subject_type, subject_issuer, subject_id, state_key)
                DO UPDATE SET state_value=EXCLUDED.state_value, value_codec_version=EXCLUDED.value_codec_version
                """, actor.tenantId(), app, scope, subjectType, issuer, subjectId, key, value, codecVersion);
    }

    void deleteScope(String scope, String key) {
        jdbc.update("""
                DELETE FROM tianji_agent.agent_session_scope_state
                WHERE tenant_id=? AND app_name=? AND scope_type=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND state_key=?
                """, actor.tenantId(), app, scope, "APP".equals(scope) ? "" : type(),
                "APP".equals(scope) ? "LEGACY" : actor.issuerFingerprint(), "APP".equals(scope) ? "" : actor.subjectId(), key);
    }

    private Object[] key(String id) {
        return new Object[] {actor.tenantId(), type(), actor.issuerFingerprint(), actor.subjectId(), app, id};
    }

    private String type() { return actor.subjectType().name(); }

    private static SessionRow sessionRow(ResultSet rs) throws SQLException {
        return new SessionRow(rs.getString("session_id"), rs.getString("session_state"), rs.getInt("state_codec_version"), rs.getLong("revision"),
                rs.getLong("last_event_sequence"), rs.getTimestamp("updated_at").toInstant());
    }

    private static LeaseRow leaseRow(ResultSet rs) throws SQLException {
        Timestamp expiry = rs.getTimestamp("lease_expires_at");
        return new LeaseRow(rs.getLong("lease_epoch"), rs.getString("lease_owner"),
                expiry == null ? null : expiry.toInstant(), rs.getBoolean("live"));
    }

    private static EventRow eventRow(ResultSet rs) throws SQLException {
        return new EventRow(rs.getLong("sequence"), rs.getString("native_event_id"), rs.getLong("event_timestamp_ms"),
                rs.getString("payload"), rs.getBoolean("matches"));
    }

    record SessionRow(String id, String state, int codecVersion, long revision, long lastEventSequence, Instant updatedAt) { }
    record ScopeValue(String value, int codecVersion) { }
    record LeaseRow(long epoch, String owner, Instant expiresAt, boolean live) { }
    record EventRow(long sequence, String nativeId, long timestampMillis, String payload, boolean matches) { }
    record EventBounds(long count, long bytes, long firstSequence, long lastSequence) { }
    record ScopeBounds(long appCount, long appBytes, long userCount, long userBytes) { }
    record RunFence(String sessionId, String status, boolean cancelled, String owner, long epoch,
                    Instant expiresAt, Instant deadlineAt) { }
}
