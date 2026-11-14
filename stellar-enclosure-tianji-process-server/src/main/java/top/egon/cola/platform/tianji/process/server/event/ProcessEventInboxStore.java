package top.egon.cola.platform.tianji.process.server.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.event.EventInboxBO.EventAdmissionVO;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseTenantFence;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Owns the event inbox's durable producer identity, admission and terminal result. */
@Slf4j
@Validated
public class ProcessEventInboxStore {
    public static final int MAX_RETRIES = 20;
    static final int MAX_BATCH = 100;
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private static final Set<String> ADMISSION_FIELDS = Set.of("receiptId", "definitionKey", "activationRevision", "releaseVersion",
            "routeSha256", "originalRequestSha256", "mappedStartInput", "effectiveConfiguration", "configurationSha256",
            "authorizationSha256", "admittedAt", "admissionNotAfter");
    private static final String COLUMNS = """
            tenant_id,producer_issuer,producer_subject,event_id,receipt_id,fingerprint,route_key,route_version,
            route_sha256,release_key,mode,command_id,wait_token_sha256,payload,correlation_key,state,instance_id,
            node_execution_ref,occurred_at,received_at,expires_at,decided_at,next_retry_at,attempts,decision_code,
            definition_key,activation_revision,admitted_at,admission_not_after,admission_json
            """;
    private static final String PRODUCER_KEY = " WHERE tenant_id=? AND producer_issuer=? AND producer_subject=? AND event_id=?";
    private static final String RECEIPT_KEY = " WHERE tenant_id=? AND receipt_id=?";
    private final JdbcTemplate jdbc;
    private final String table;
    private final String subscriptions;

    public ProcessEventInboxStore(JdbcTemplate jdbc, String schema) {
        this.jdbc = Objects.requireNonNull(jdbc);
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw new IllegalArgumentException("Invalid Process schema");
        table = '"' + schema + "\".tianji_process_event_inbox";
        subscriptions = '"' + schema + "\".tianji_process_event_subscription";
    }

    public Optional<EventInboxBO> findOriginal(@NotNull ProcessActor producer, @NotNull String eventId) {
        if (producer.subjectType() != ProcessActor.SubjectType.SERVICE || !EventInboxBO.sha256(producer.issuerFingerprint())
                || !EventInboxBO.eventId(eventId)) throw unavailable();
        return single(jdbc.query("SELECT " + COLUMNS + " FROM " + table + PRODUCER_KEY,
                this::read, producer.tenantId(), producer.issuerFingerprint(), producer.subjectId(), eventId));
    }

    public Optional<EventInboxBO> find(@Min(1) long tenantId, @NotNull String receiptId) {
        if (tenantId <= 0 || !EventInboxBO.eventId(receiptId)) throw new ProcessResourceNotFoundException();
        return single(jdbc.query("SELECT " + COLUMNS + " FROM " + table + RECEIPT_KEY,
                this::read, tenantId, receiptId));
    }

    public EventInboxBO lock(@Min(1) long tenantId, @NotNull String receiptId) {
        requireWrite();
        return single(jdbc.query("SELECT " + COLUMNS + " FROM " + table + RECEIPT_KEY + " FOR UPDATE",
                this::read, tenantId, receiptId)).orElseThrow(ProcessResourceNotFoundException::new);
    }

    public void acquireTenantShared(@Min(1) long tenantId) {
        requireWrite();
        PlatformReleaseTenantFence.acquireShared(jdbc, tenantId);
    }

    public Instant now() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT pg_catalog.clock_timestamp()::timestamptz(6)", OffsetDateTime.class))
                .toInstant();
    }

    boolean insert(@NotNull @Valid EventInboxBO row) {
        requireWrite();
        requireEvidence(row);
        if (!"START".equals(row.getMode())) throw unavailable();
        requireInitial(row);
        var payload = text(row.getPayload(), EventInboxBO.MAX_PAYLOAD_BYTES);
        var admission = text(admissionWire(row.getAdmission()), EventInboxBO.MAX_ADMISSION_BYTES);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT octet_length((?::jsonb)::text)<=? AND octet_length((?::jsonb)::text)<=?",
                Boolean.class, payload, EventInboxBO.MAX_PAYLOAD_BYTES, admission, EventInboxBO.MAX_ADMISSION_BYTES))) {
            throw new IllegalArgumentException("Process event exceeds its storage limit");
        }
        return jdbc.update("INSERT INTO " + table + " (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,"
                        + "'START',?,NULL,?::jsonb,?,'RECEIVED',NULL,NULL,?,?,?,NULL,?,0,NULL,?,?,?,?,?::jsonb)"
                        + " ON CONFLICT (tenant_id,producer_issuer,producer_subject,event_id) DO NOTHING",
                row.getTenantId(), row.getProducerIssuer(), row.getProducerSubject(), row.getEventId(), row.getReceiptId(),
                row.getFingerprint(), row.getRouteKey(), row.getRouteVersion(), row.getRouteSha256(), row.getReleaseKey(),
                row.getCommandId(), payload, row.getCorrelationKey(), utc(row.getOccurredAt()), utc(row.getReceivedAt()),
                utc(row.getExpiresAt()), utc(row.getNextRetryAt()), row.getDefinitionKey(), row.getActivationRevision(),
                utc(row.getAdmittedAt()), utc(row.getAdmissionNotAfter()), admission) == 1;
    }

    /** Records exact reservation affinity even before native binding. V15 keeps result references empty until application. */
    boolean insertSignal(@NotNull @Valid EventInboxBO row, @NotNull @Valid EventSubscriptionBO reservation) {
        requireSignalWrite(row, reservation);
        requireInitial(row);
        requireStoredReservation(reservation);
        String payload = text(row.getPayload(), EventInboxBO.MAX_PAYLOAD_BYTES);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT octet_length((?::jsonb)::text)<=?",
                Boolean.class, payload, EventInboxBO.MAX_PAYLOAD_BYTES))) {
            throw new IllegalArgumentException("Process event exceeds its storage limit");
        }
        return jdbc.update("INSERT INTO " + table + " (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,"
                        + "'SIGNAL',NULL,?,?::jsonb,?,'RECEIVED',NULL,NULL,?,?,?,NULL,?,0,NULL,?,?,?,NULL,NULL)"
                        + " ON CONFLICT (tenant_id,producer_issuer,producer_subject,event_id) DO NOTHING",
                row.getTenantId(), row.getProducerIssuer(), row.getProducerSubject(), row.getEventId(), row.getReceiptId(),
                row.getFingerprint(), row.getRouteKey(), row.getRouteVersion(), row.getRouteSha256(), row.getReleaseKey(),
                row.getWaitTokenSha256(), payload, row.getCorrelationKey(), utc(row.getOccurredAt()), utc(row.getReceivedAt()),
                utc(row.getExpiresAt()), utc(row.getNextRetryAt()), row.getDefinitionKey(), row.getActivationRevision(),
                utc(row.getAdmittedAt())) == 1;
    }

    /** Callers hold the tenant fence and exact inbox row before deciding an original START result. */
    public EventInboxBO decide(@NotNull @Valid EventInboxBO locked, @NotNull String state, @NotNull String decisionCode, String instanceId) {
        requireWrite();
        boolean applied = "APPLIED".equals(state);
        boolean rejected = "REJECTED".equals(state) && Set.of("EXPIRED", "ROUTE_REVOKED").contains(decisionCode);
        boolean exhausted = "DEAD".equals(state) && "RETRY_EXHAUSTED".equals(decisionCode);
        if (!"START".equals(locked.getMode()) || !"RECEIVED".equals(locked.getState())
                || !applied && !rejected && !exhausted
                || applied != "APPLIED".equals(decisionCode) || applied != (instanceId != null)) throw unavailable();
        Instant decidedAt = now();
        if (jdbc.update("UPDATE " + table + " SET state=?,decided_at=?,decision_code=?,instance_id=?,next_retry_at=NULL"
                        + RECEIPT_KEY + " AND mode='START' AND state='RECEIVED' AND fingerprint=? AND command_id=?",
                state, utc(decidedAt), decisionCode, instanceId, locked.getTenantId(), locked.getReceiptId(),
                locked.getFingerprint(), locked.getCommandId()) != 1) throw unavailable();
        return locked.setState(state).setDecisionCode(decisionCode).setInstanceId(instanceId)
                .setDecidedAt(decidedAt).setNextRetryAt(null);
    }

    /** Applies one accepted result, or closes one pending receipt without assigning native result references. */
    EventInboxBO decideSignal(@NotNull @Valid EventInboxBO locked, @NotNull @Valid EventSubscriptionBO reservation,
                              @NotNull String state, @NotNull String decisionCode) {
        requireSignalWrite(locked, reservation);
        boolean allowed = switch (state) {
            case "APPLIED" -> "APPLIED".equals(decisionCode) && reservation.getState() == EventSubscriptionBO.State.CONSUMED
                    && EventInboxBO.eventId(reservation.getNodeExecutionRef());
            case "IGNORED_LATE" -> "LATE".equals(decisionCode);
            case "REJECTED" -> Set.of("EXPIRED", "ROUTE_REVOKED", "INVALID_PAYLOAD").contains(decisionCode);
            case "DEAD" -> "RETRY_EXHAUSTED".equals(decisionCode) && locked.getAttempts() >= MAX_RETRIES;
            default -> false;
        };
        if (!allowed || !"RECEIVED".equals(locked.getState())) throw unavailable();
        requireStoredReservation(reservation);
        boolean applied = "APPLIED".equals(state);
        String instanceId = applied ? reservation.getInstanceId() : null;
        String nodeExecutionRef = applied ? reservation.getNodeExecutionRef() : null;
        Instant decidedAt = now();
        if (jdbc.update("UPDATE " + table
                        + " SET state=?,decided_at=?,decision_code=?,instance_id=?,node_execution_ref=?,next_retry_at=NULL"
                        + RECEIPT_KEY + " AND mode='SIGNAL' AND state='RECEIVED' AND producer_issuer=? AND producer_subject=?"
                        + " AND event_id=? AND fingerprint=? AND wait_token_sha256=? AND definition_key=? AND release_key=?"
                        + " AND activation_revision=? AND route_key=? AND route_version=? AND route_sha256=?",
                state, utc(decidedAt), decisionCode, instanceId, nodeExecutionRef, locked.getTenantId(), locked.getReceiptId(),
                locked.getProducerIssuer(), locked.getProducerSubject(), locked.getEventId(), locked.getFingerprint(),
                locked.getWaitTokenSha256(), locked.getDefinitionKey(), locked.getReleaseKey(), locked.getActivationRevision(),
                locked.getRouteKey(), locked.getRouteVersion(), locked.getRouteSha256()) != 1) throw unavailable();
        return locked.setState(state).setDecisionCode(decisionCode).setInstanceId(instanceId).setNodeExecutionRef(nodeExecutionRef)
                .setDecidedAt(decidedAt).setNextRetryAt(null);
    }

    /** The same root guard serializes arrivals and ensures a later receipt cannot overtake an earlier pending receipt. */
    boolean hasEarlierPendingSignal(@NotNull @Valid EventInboxBO locked, @NotNull @Valid EventSubscriptionBO reservation) {
        requireSignalWrite(locked, reservation);
        if (!"RECEIVED".equals(locked.getState())) throw unavailable();
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM %s WHERE tenant_id=? AND wait_token_sha256=? AND mode='SIGNAL' AND state='RECEIVED'
                        AND (received_at,receipt_id)<(?,?)
                )
                """.formatted(table), Boolean.class, locked.getTenantId(), locked.getWaitTokenSha256(),
                utc(locked.getReceivedAt()), locked.getReceiptId()));
    }

    /** Releases short retry claims before any root guard or native execution is acquired. */
    List<ClaimedEventVO> claimPending(int limit) {
        requireWrite();
        if (limit < 1 || limit > MAX_BATCH) throw new IllegalArgumentException("Invalid event scan limit");
        return jdbc.query("""
                WITH selected AS (
                    SELECT tenant_id,receipt_id,next_retry_at FROM %1$s
                    WHERE state='RECEIVED' AND next_retry_at<=statement_timestamp()
                    ORDER BY next_retry_at,tenant_id,receipt_id LIMIT ? FOR UPDATE SKIP LOCKED
                ), claimed AS (
                    UPDATE %1$s inbox SET next_retry_at=LEAST(
                        CASE WHEN inbox.mode='START' THEN inbox.admission_not_after
                             WHEN inbox.expires_at>statement_timestamp() THEN inbox.expires_at END,
                        statement_timestamp() + LEAST(60, (1 << LEAST(inbox.attempts,6))) * interval '1 second')
                    FROM selected WHERE inbox.tenant_id=selected.tenant_id AND inbox.receipt_id=selected.receipt_id
                    RETURNING inbox.tenant_id,inbox.receipt_id,inbox.next_retry_at,inbox.attempts,inbox.mode
                ) SELECT claimed.* FROM claimed ORDER BY next_retry_at,tenant_id,receipt_id
                """.formatted(table), (row, index) -> new ClaimedEventVO(row.getLong("tenant_id"),
                row.getString("receipt_id"), instant(row, "next_retry_at"), row.getInt("attempts"), row.getString("mode")), limit);
    }

    void recordRetry(ClaimedEventVO claim) {
        requireWrite();
        jdbc.update("UPDATE " + table + " SET attempts=attempts+1" + RECEIPT_KEY
                        + " AND mode=? AND state='RECEIVED' AND next_retry_at=? AND attempts=? AND attempts<?",
                claim.tenantId(), claim.receiptId(), claim.mode(), utc(claim.nextRetryAt()), claim.attempts(), MAX_RETRIES);
    }

    private static void requireInitial(EventInboxBO row) {
        if (!"RECEIVED".equals(row.getState()) || row.getAttempts() != 0 || !row.getReceivedAt().equals(row.getNextRetryAt())
                || row.getInstanceId() != null || row.getNodeExecutionRef() != null || row.getDecidedAt() != null
                || row.getDecisionCode() != null) throw unavailable();
    }

    private void requireSignalWrite(EventInboxBO row, EventSubscriptionBO reservation) {
        requireWrite();
        requireEvidence(row);
        if (!"SIGNAL".equals(row.getMode()) || row.getTenantId() != reservation.getTenantId()
                || !row.getWaitTokenSha256().equals(reservation.getTokenSha256()) || !row.getRouteKey().equals(reservation.getRouteKey())
                || row.getRouteVersion() != reservation.getRouteVersion()) throw unavailable();
        AutomationInstanceGuardCommandInterceptor.requireHeld(row.getTenantId(), reservation.getInstanceId());
    }

    private void requireStoredReservation(EventSubscriptionBO reservation) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM %s WHERE tenant_id=? AND token_sha256=? AND instance_id=? AND parent_scope_id=?
                        AND target_node_id=? AND reservation_id=? AND route_key=? AND route_version=? AND source_ref=?
                        AND state=? AND revision=? AND node_execution_ref IS NOT DISTINCT FROM ?
                )
                """.formatted(subscriptions), Boolean.class, reservation.getTenantId(), reservation.getTokenSha256(),
                reservation.getInstanceId(), reservation.getParentScopeId(), reservation.getTargetNodeId(),
                reservation.getReservationId(), reservation.getRouteKey(), reservation.getRouteVersion(), reservation.getSourceRef(),
                reservation.getState().name(), reservation.getRevision(), reservation.getNodeExecutionRef()))) throw unavailable();
    }

    private EventInboxBO read(ResultSet row, int index) throws SQLException {
        try {
            String encodedAdmission = row.getString("admission_json");
            var result = new EventInboxBO()
                    .setTenantId(row.getLong("tenant_id")).setProducerIssuer(row.getString("producer_issuer"))
                    .setProducerSubject(row.getString("producer_subject")).setEventId(row.getString("event_id"))
                    .setReceiptId(row.getString("receipt_id")).setFingerprint(row.getString("fingerprint"))
                    .setRouteKey(row.getString("route_key")).setRouteVersion(row.getLong("route_version"))
                    .setRouteSha256(row.getString("route_sha256")).setReleaseKey(row.getString("release_key"))
                    .setMode(row.getString("mode")).setCommandId(row.getString("command_id"))
                    .setWaitTokenSha256(row.getString("wait_token_sha256"))
                    .setPayload(EventInboxBO.immutableObject(object(row.getString("payload"), EventInboxBO.MAX_PAYLOAD_BYTES),
                            EventInboxBO.MAX_PAYLOAD_BYTES))
                    .setCorrelationKey(row.getString("correlation_key")).setState(row.getString("state"))
                    .setInstanceId(row.getString("instance_id")).setNodeExecutionRef(row.getString("node_execution_ref"))
                    .setOccurredAt(instant(row, "occurred_at")).setReceivedAt(instant(row, "received_at"))
                    .setExpiresAt(instant(row, "expires_at")).setDecidedAt(instant(row, "decided_at"))
                    .setNextRetryAt(instant(row, "next_retry_at")).setAttempts(row.getInt("attempts"))
                    .setDecisionCode(row.getString("decision_code")).setDefinitionKey(row.getString("definition_key"))
                    .setActivationRevision(row.getLong("activation_revision")).setAdmittedAt(instant(row, "admitted_at"))
                    .setAdmissionNotAfter(instant(row, "admission_not_after"))
                    .setAdmission(encodedAdmission == null ? null : admission(object(encodedAdmission, EventInboxBO.MAX_ADMISSION_BYTES)));
            requireEvidence(result);
            return result;
        } catch (RuntimeException corrupt) {
            throw unavailable();
        }
    }

    private static void requireEvidence(EventInboxBO row) {
        if (!EventInboxBO.sha256(row.getProducerIssuer()) || !EventInboxBO.sha256(row.getFingerprint())
                || !EventInboxBO.sha256(row.getRouteSha256()) || !EventInboxBO.eventId(row.getEventId())
                || !EventInboxBO.eventId(row.getReceiptId()) || !EventInboxBO.eventId(row.getDefinitionKey())
                || !EventInboxBO.eventId(row.getRouteKey()) || !row.getReceivedAt().equals(row.getAdmittedAt())) throw unavailable();
        var admission = row.getAdmission();
        if ("START".equals(row.getMode())) {
            if (admission == null || !row.getReceiptId().equals(admission.receiptId())
                    || !row.getDefinitionKey().equals(admission.definitionKey()) || row.getActivationRevision() != admission.activationRevision()
                    || !row.getReleaseKey().equals(admission.releaseVersion()) || !row.getRouteSha256().equals(admission.routeSha256())
                    || !row.getFingerprint().equals(admission.originalRequestSha256()) || !row.getAdmittedAt().equals(admission.admittedAt())
                    || !admission.admissionNotAfter().equals(row.getAdmissionNotAfter())
                    || !ProcessExecutionService.eventStartCommandId(row.producer(), row.getEventId()).equals(row.getCommandId())) throw unavailable();
        } else if (!"SIGNAL".equals(row.getMode()) || admission != null || row.getCommandId() != null
                || row.getAdmissionNotAfter() != null || !EventInboxBO.sha256(row.getWaitTokenSha256())) {
            throw unavailable();
        }
    }

    private static Map<String, Object> admissionWire(EventAdmissionVO admission) {
        return Map.ofEntries(Map.entry("receiptId", admission.receiptId()), Map.entry("definitionKey", admission.definitionKey()),
                Map.entry("activationRevision", admission.activationRevision()), Map.entry("releaseVersion", admission.releaseVersion()),
                Map.entry("routeSha256", admission.routeSha256()), Map.entry("originalRequestSha256", admission.originalRequestSha256()),
                Map.entry("mappedStartInput", admission.mappedStartInput()), Map.entry("effectiveConfiguration", admission.effectiveConfiguration()),
                Map.entry("configurationSha256", admission.configurationSha256()), Map.entry("authorizationSha256", admission.authorizationSha256()),
                Map.entry("admittedAt", admission.admittedAt().toString()), Map.entry("admissionNotAfter", admission.admissionNotAfter().toString()));
    }

    private static EventAdmissionVO admission(Map<String, Object> wire) {
        if (!wire.keySet().equals(ADMISSION_FIELDS) || !(wire.get("activationRevision") instanceof Integer
                || wire.get("activationRevision") instanceof Long)
                || !(wire.get("mappedStartInput") instanceof Map<?, ?> input)
                || !(wire.get("effectiveConfiguration") instanceof Map<?, ?> configuration)) throw unavailable();
        return new EventAdmissionVO((String) wire.get("receiptId"), (String) wire.get("definitionKey"),
                ((Number) wire.get("activationRevision")).longValue(), (String) wire.get("releaseVersion"),
                (String) wire.get("routeSha256"), (String) wire.get("originalRequestSha256"),
                EventInboxBO.immutableObject(input, EventInboxBO.MAX_CONFIGURATION_BYTES),
                EventInboxBO.immutableObject(configuration, EventInboxBO.MAX_CONFIGURATION_BYTES),
                (String) wire.get("configurationSha256"), (String) wire.get("authorizationSha256"),
                Instant.parse((String) wire.get("admittedAt")), Instant.parse((String) wire.get("admissionNotAfter")));
    }

    private static String text(Map<String, Object> value, int maxBytes) {
        return new String(ProcessTypedJson.canonicalBytes(value, maxBytes), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> object(String raw, int maxBytes) {
        if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > maxBytes) throw unavailable();
        return JSON.readValue(raw, OBJECT);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime time = row.getObject(column, OffsetDateTime.class);
        return time == null ? null : time.toInstant();
    }

    private static OffsetDateTime utc(Instant time) {
        return time.atOffset(ZoneOffset.UTC);
    }

    private static Optional<EventInboxBO> single(List<EventInboxBO> rows) {
        if (rows.size() > 1) throw unavailable();
        return rows.stream().findFirst();
    }

    private void requireWrite() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.hasResource(jdbc.getDataSource())) throw unavailable();
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Process event storage evidence is unavailable");
    }

    record ClaimedEventVO(long tenantId, String receiptId, Instant nextRetryAt, int attempts, String mode) { }
}
