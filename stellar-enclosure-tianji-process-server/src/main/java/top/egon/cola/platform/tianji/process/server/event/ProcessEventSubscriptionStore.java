package top.egon.cola.platform.tianji.process.server.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.event.EventSubscriptionBO.State;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persists exact wait reservations inside the owning Process JDBC transaction. */
@Slf4j
@Validated
public class ProcessEventSubscriptionStore {
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder TOKEN_DECODER = Base64.getUrlDecoder();
    private static final String COLUMNS = """
            tenant_id,token_sha256,instance_id,parent_scope_id,target_node_id,reservation_id,route_key,route_version,source_ref,
            native_execution_id,native_subscription_id,node_execution_ref,state,revision,deadline_at,created_at,closed_at
            """;
    private static final String TOKEN_KEY = " WHERE tenant_id=? AND token_sha256=?";
    private static final String IDENTITY = " AND instance_id=? AND parent_scope_id=? AND target_node_id=? AND reservation_id=?"
            + " AND route_key=? AND route_version=? AND source_ref=? AND deadline_at=? AND created_at=?";
    private static final String OPEN = " AND state IN ('RESERVED','BOUND')";
    private final JdbcTemplate jdbc;
    private final String table;
    private final SecureRandom random = new SecureRandom();

    public ProcessEventSubscriptionStore(JdbcTemplate jdbc, String schema) {
        this.jdbc = Objects.requireNonNull(jdbc);
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw new IllegalArgumentException("Invalid Process schema");
        this.table = '"' + schema + "\".tianji_process_event_subscription";
    }

    public void requireDataSource(@NotNull DataSource dataSource) {
        if (dataSource != jdbc.getDataSource()) throw unavailable();
    }

    /** The plaintext belongs only to the authorized outgoing request. */
    public ReservedEventWaitVO reserve(@Min(1) long tenantId, @NotNull String instanceId, @NotNull String parentScopeId,
            @NotNull String targetNodeId, @NotNull UUID reservationId, @NotNull String routeKey, @Min(1) long routeVersion,
            @NotNull String sourceRef, @NotNull Instant deadlineAt) {
        requireWrite(tenantId, instanceId);
        if (findOpen(tenantId, instanceId, parentScopeId, targetNodeId).isPresent()) throw unavailable();
        var bytes = new byte[32];
        random.nextBytes(bytes);
        try {
            var row = new EventSubscriptionBO().setTenantId(tenantId).setTokenSha256(digest(bytes)).setInstanceId(instanceId)
                    .setParentScopeId(parentScopeId).setTargetNodeId(targetNodeId).setReservationId(reservationId)
                    .setRouteKey(routeKey).setRouteVersion(routeVersion).setSourceRef(sourceRef).setState(State.RESERVED)
                    .setRevision(1).setDeadlineAt(deadlineAt).setCreatedAt(now());
            requireEvidence(row);
            int inserted = jdbc.update("INSERT INTO " + table + " (" + COLUMNS + ") VALUES "
                            + "(?,?,?,?,?,?,?,?,?,NULL,NULL,NULL,'RESERVED',1,?,?,NULL)",
                    tenantId, row.getTokenSha256(), instanceId, parentScopeId, targetNodeId, reservationId, routeKey, routeVersion,
                    sourceRef, utc(deadlineAt), utc(row.getCreatedAt()));
            if (inserted != 1) throw unavailable();
            return new ReservedEventWaitVO(TOKEN_ENCODER.encodeToString(bytes), row);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    /** Locates a guarded root or terminal reservation without granting access to it. */
    public Optional<EventSubscriptionBO> find(@Min(1) long tenantId, @NotNull String tokenSha256) {
        if (tenantId <= 0 || !EventInboxBO.sha256(tokenSha256)) throw invalid();
        return single(jdbc.query(select() + TOKEN_KEY, this::row, tenantId, tokenSha256));
    }

    /** Guards against attaching a new Flowable wait to an unrelated occurrence or stale reservation. */
    public Optional<EventSubscriptionBO> findOpen(@Min(1) long tenantId, @NotNull String instanceId,
            @NotNull String parentScopeId, @NotNull String targetNodeId) {
        requireWrite(tenantId, instanceId);
        return single(jdbc.query(select() + " WHERE tenant_id=? AND instance_id=? AND parent_scope_id=? AND target_node_id=?"
                + OPEN + " LIMIT 2", this::row, tenantId, instanceId, parentScopeId, targetNodeId));
    }

    public EventSubscriptionBO lockExact(@NotNull @Valid EventSubscriptionBO expected) {
        requireWrite(expected.getTenantId(), expected.getInstanceId());
        requireEvidence(expected);
        return single(jdbc.query(select() + TOKEN_KEY + IDENTITY + " FOR UPDATE", this::row, identity(expected)))
                .orElseThrow(ProcessEventSubscriptionStore::unavailable);
    }

    public EventSubscriptionBO bindExact(@NotNull @Valid EventSubscriptionBO reserved, @NotNull @Valid NativeWaitVO wait) {
        requireWrite(reserved.getTenantId(), reserved.getInstanceId());
        requireEvidence(reserved);
        Instant now = now();
        if (reserved.getState() != State.RESERVED || !now.isBefore(reserved.getDeadlineAt())) throw unavailable();
        var sql = "UPDATE " + table + " SET native_execution_id=?,native_subscription_id=?,node_execution_ref=?,"
                + "state='BOUND',revision=revision+1" + TOKEN_KEY + IDENTITY
                + " AND revision=? AND state='RESERVED' AND native_execution_id IS NULL AND native_subscription_id IS NULL"
                + " AND node_execution_ref IS NULL AND closed_at IS NULL AND deadline_at>?";
        return writing(sql, reserved, withIdentity(reserved,
                List.of(wait.executionId(), wait.subscriptionId(), wait.nodeExecutionRef()), reserved.getRevision(), utc(now)));
    }

    public EventSubscriptionBO consume(@NotNull @Valid EventSubscriptionBO bound) {
        requireWrite(bound.getTenantId(), bound.getInstanceId());
        requireEvidence(bound);
        Instant now = now();
        if (bound.getState() != State.BOUND || !now.isBefore(bound.getDeadlineAt())) throw unavailable();
        var sql = "UPDATE " + table + " SET state='CONSUMED',closed_at=?,revision=revision+1" + TOKEN_KEY + IDENTITY
                + " AND revision=? AND state='BOUND' AND closed_at IS NULL AND deadline_at>?"
                + " AND native_execution_id=? AND native_subscription_id=? AND node_execution_ref=?";
        return writing(sql, bound, withIdentity(bound, List.of(utc(now)), bound.getRevision(), utc(now), bound.getNativeExecutionId(),
                bound.getNativeSubscriptionId(), bound.getNodeExecutionRef()));
    }

    public EventSubscriptionBO close(@NotNull @Valid EventSubscriptionBO open) {
        requireWrite(open.getTenantId(), open.getInstanceId());
        requireEvidence(open);
        if (open.getState() != State.RESERVED && open.getState() != State.BOUND) throw unavailable();
        var sql = "UPDATE " + table + " SET state='CLOSED',closed_at=?,revision=revision+1" + TOKEN_KEY + IDENTITY
                + " AND revision=? AND state=? AND closed_at IS NULL AND native_execution_id IS NOT DISTINCT FROM ?"
                + " AND native_subscription_id IS NOT DISTINCT FROM ? AND node_execution_ref IS NOT DISTINCT FROM ?";
        return writing(sql, open, withIdentity(open, List.of(utc(now())), open.getRevision(), open.getState().name(),
                open.getNativeExecutionId(), open.getNativeSubscriptionId(), open.getNodeExecutionRef()));
    }

    /** Root cancellation closes only this tenant/root's open subscriptions. */
    public int closeOpen(@Min(1) long tenantId, @NotNull String instanceId) {
        return closeOpen(tenantId, instanceId, Set.of());
    }

    /** Native branch exclusion closes reservations owned by the discarded executions. */
    public int closeScopes(@Min(1) long tenantId, @NotNull String instanceId, @NotNull Set<String> parentScopeIds) {
        requireWrite(tenantId, instanceId);
        if (parentScopeIds.isEmpty()) return 0;
        if (parentScopeIds.stream().anyMatch(scope -> !reference(scope))) throw invalid();
        return closeOpen(tenantId, instanceId, parentScopeIds);
    }

    private int closeOpen(long tenantId, String instanceId, Set<String> parentScopeIds) {
        requireWrite(tenantId, instanceId);
        boolean scoped = !parentScopeIds.isEmpty();
        var arguments = new ArrayList<>();
        arguments.add(tenantId);
        arguments.add(instanceId);
        if (scoped) arguments.add(new SqlArrayValue("varchar", parentScopeIds.toArray()));
        arguments.add(utc(now()));
        arguments.add(tenantId);
        arguments.add(instanceId);
        return jdbc.update("""
                WITH open AS (
                    SELECT token_sha256,revision FROM %1$s
                    WHERE tenant_id=? AND instance_id=? AND state IN ('RESERVED','BOUND')%2$s
                )
                UPDATE %1$s target SET state='CLOSED',closed_at=?,revision=target.revision+1
                FROM open
                WHERE target.tenant_id=? AND target.instance_id=? AND target.token_sha256=open.token_sha256
                    AND target.revision=open.revision AND target.state IN ('RESERVED','BOUND')
                """.formatted(table, scoped ? " AND parent_scope_id=ANY(?)" : ""), arguments.toArray());
    }

    public Instant now() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT pg_catalog.clock_timestamp()::timestamptz(6)", OffsetDateTime.class))
                .toInstant();
    }

    public static String tokenSha256(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        byte[] bytes = TOKEN_DECODER.decode(token);
        try {
            if (bytes.length != 32 || !token.equals(TOKEN_ENCODER.encodeToString(bytes))) throw invalid();
            return digest(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private String select() { return "SELECT " + COLUMNS + " FROM " + table; }

    private void requireWrite(long tenantId, String instanceId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.hasResource(jdbc.getDataSource())) throw unavailable();
        AutomationInstanceGuardCommandInterceptor.requireHeld(tenantId, instanceId);
    }

    private EventSubscriptionBO writing(String sql, EventSubscriptionBO expected, Object[] parameters) {
        if (jdbc.update(sql, parameters) != 1) throw unavailable();
        return lockExact(expected);
    }

    private static Object[] identity(EventSubscriptionBO row) {
        return new Object[]{row.getTenantId(), row.getTokenSha256(), row.getInstanceId(), row.getParentScopeId(), row.getTargetNodeId(),
                row.getReservationId(), row.getRouteKey(), row.getRouteVersion(), row.getSourceRef(), utc(row.getDeadlineAt()), utc(row.getCreatedAt())};
    }

    private static Object[] withIdentity(EventSubscriptionBO row, List<?> prefix, Object... suffix) {
        var arguments = new ArrayList<Object>(prefix);
        arguments.addAll(Arrays.asList(identity(row)));
        arguments.addAll(Arrays.asList(suffix));
        return arguments.toArray();
    }

    private EventSubscriptionBO row(ResultSet rs, int index) throws SQLException {
        try {
            var row = new EventSubscriptionBO().setTenantId(rs.getLong("tenant_id")).setTokenSha256(rs.getString("token_sha256"))
                    .setInstanceId(rs.getString("instance_id")).setParentScopeId(rs.getString("parent_scope_id"))
                    .setTargetNodeId(rs.getString("target_node_id")).setReservationId(rs.getObject("reservation_id", UUID.class))
                    .setRouteKey(rs.getString("route_key")).setRouteVersion(rs.getLong("route_version")).setSourceRef(rs.getString("source_ref"))
                    .setNativeExecutionId(rs.getString("native_execution_id")).setNativeSubscriptionId(rs.getString("native_subscription_id"))
                    .setNodeExecutionRef(rs.getString("node_execution_ref")).setState(State.valueOf(rs.getString("state")))
                    .setRevision(rs.getLong("revision")).setDeadlineAt(instant(rs, "deadline_at")).setCreatedAt(instant(rs, "created_at"))
                    .setClosedAt(instant(rs, "closed_at"));
            requireEvidence(row);
            return row;
        } catch (RuntimeException malformed) {
            throw unavailable();
        }
    }

    private static void requireEvidence(EventSubscriptionBO row) {
        if (row.getTenantId() < 1 || !EventInboxBO.sha256(row.getTokenSha256()) || !reference(row.getInstanceId())
                || !reference(row.getParentScopeId()) || !reference(row.getTargetNodeId()) || row.getReservationId() == null
                || !EventInboxBO.eventId(row.getRouteKey()) || row.getRouteVersion() < 1
                || row.getRouteVersion() > ProcessTypedJson.V2_MAX_WIRE_INTEGER || !EventInboxBO.eventId(row.getSourceRef())
                || row.getState() == null || row.getRevision() < 1 || !micros(row.getCreatedAt()) || !micros(row.getDeadlineAt())
                || !row.getDeadlineAt().isAfter(row.getCreatedAt())) throw unavailable();
        boolean unbound = row.getNativeExecutionId() == null && row.getNativeSubscriptionId() == null && row.getNodeExecutionRef() == null;
        boolean bound = reference(row.getNativeExecutionId()) && reference(row.getNativeSubscriptionId())
                && EventInboxBO.eventId(row.getNodeExecutionRef());
        boolean terminal = micros(row.getClosedAt()) && !row.getClosedAt().isBefore(row.getCreatedAt());
        boolean groupsMatch = switch (row.getState()) {
            case RESERVED -> unbound && row.getClosedAt() == null;
            case BOUND -> bound && row.getClosedAt() == null;
            case CONSUMED -> bound && terminal;
            case CLOSED -> (bound || unbound) && terminal;
        };
        if (!groupsMatch) throw unavailable();
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant time) { return time.atOffset(ZoneOffset.UTC); }

    private static boolean micros(Instant instant) { return instant != null && instant.getNano() % 1_000 == 0; }

    private static boolean reference(String value) {
        return value != null && !value.isBlank() && value.length() <= 255 && value.chars().noneMatch(Character::isISOControl);
    }

    private static Optional<EventSubscriptionBO> single(List<EventSubscriptionBO> rows) {
        if (rows.size() > 1) throw unavailable();
        return rows.stream().findFirst();
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid process event reservation"); }

    private static IllegalStateException unavailable() { return new IllegalStateException("Process event reservation evidence is unavailable"); }

    public record NativeWaitVO(String executionId, String subscriptionId, String nodeExecutionRef) {
        public NativeWaitVO {
            if (!reference(executionId) || !reference(subscriptionId) || !EventInboxBO.eventId(nodeExecutionRef)) throw invalid();
        }
    }

    public record ReservedEventWaitVO(String token, EventSubscriptionBO subscription) {
        @Override
        public String toString() { return "ReservedEventWaitVO[subscription=" + subscription + "]"; }
    }
}
