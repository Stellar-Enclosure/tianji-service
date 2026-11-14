package top.egon.cola.platform.tianji.process.server.bridge;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Trusted ingress authenticates and authorizes the SERVICE actor before this owner-local transaction. */
public final class AgentResultInbox {
    private static final JsonMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule())
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final AgentDispatchStore dispatches;
    private final AgentResultProducerRegistry producers;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Duration pendingRetention;
    private final String table;
    private final DataSource source;
    private final DataSourceTransactionManager manager;
    private final String schema;
    private final AgentResultInboxLagObservation lag;

    public AgentResultInbox(DataSource source, DataSourceTransactionManager manager, String schema, AgentDispatchStore dispatches,
                             AgentResultProducerRegistry producers, Duration pendingRetention) {
        this(source, manager, schema, dispatches, producers, pendingRetention, AgentResultInboxLagObservation.disabled());
    }

    public AgentResultInbox(DataSource source, DataSourceTransactionManager manager, String schema, AgentDispatchStore dispatches,
                             AgentResultProducerRegistry producers, Duration pendingRetention, AgentResultInboxLagObservation lag) {
        if (source == null || manager == null || manager.getDataSource() != source || schema == null
                || !schema.matches("[a-z_][a-z0-9_]{0,62}") || pendingRetention == null
                || pendingRetention.compareTo(Duration.ofMinutes(1)) < 0 || pendingRetention.compareTo(Duration.ofDays(30)) > 0
                || pendingRetention.toNanos() % 1_000_000 != 0) throw invalid();
        this.dispatches = Objects.requireNonNull(dispatches, "dispatches");
        this.dispatches.requireStorage(source, manager, schema);
        this.producers = Objects.requireNonNull(producers, "producers");
        new ProcessApiSchemaVerifier(source, schema).verifyAgentResultInbox();
        this.jdbc = new JdbcTemplate(source);
        this.transaction = new TransactionTemplate(manager);
        this.pendingRetention = pendingRetention;
        this.table = '"' + schema + "\".tianji_process_agent_result_inbox";
        this.source = source;
        this.manager = manager;
        this.schema = schema;
        this.lag = Objects.requireNonNull(lag, "lag");
    }

    /** RECEIVED means the exact payload is durable; this method never advances Flowable or claims an unknown run ID. */
    public AgentResultReceipt receive(ProcessActor authenticatedProducer, AgentTerminalEvent event) {
        requireOutsideTransaction();
        var registration = producers.require(authenticatedProducer, event);
        return receiveExact(authenticatedProducer, registration, EnvelopeVO.of(event), encode(event), event);
    }

    public AgentResultReceipt receiveV2(ProcessActor authenticatedProducer, AgentTerminalEventV2 event) {
        requireOutsideTransaction();
        var registration = producers.requireV2(authenticatedProducer, event);
        var payload = new String(event.canonicalBytes(), StandardCharsets.UTF_8);
        return receiveExact(authenticatedProducer, registration, EnvelopeVO.of(event), payload, event);
    }

    private static void requireOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Agent result reception must own its JDBC transaction before returning a receipt");
        }
    }

    private AgentResultReceipt receiveExact(ProcessActor authenticatedProducer, AgentResultProducerRegistry.Registration registration,
            EnvelopeVO event, String payload, Object original) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var link = event.link();
            var key = new AgentDispatchStore.Key(registration.owner(), registration.sourceId(), link.scopeInstanceId(), link.activityOccurrenceId());
            // All ingress and application paths use dispatch -> Inbox ordering.
            var dispatch = dispatches.lockForResult(key);
            if (!dispatch.query().equals(new LinkedAgentLookupQuery(event.idempotencyKey(), link))
                    || !dispatch.seed().binding().definitionKey().equals(event.definitionKey())
                    || dispatch.seed().binding().definitionVersion() != event.definitionVersion()
                    || dispatch.runId() != null && !dispatch.runId().equals(event.runId())
                    || dispatch.state() == AgentDispatchStore.State.CLOSED && dispatch.runId() == null) throw conflict();
            requireDispatchProfile(dispatch, original);
            jdbc.update("WITH observed AS (SELECT clock_timestamp() AS now) INSERT INTO " + table + " ("
                            + "tenant_id,event_id,producer_id,producer_issuer,producer_subject,owner_issuer,owner_subject,source_id,"
                            + "scope_type,scope_id,occurrence_id,run_id,terminal_revision,event_sha256,event_payload,state,"
                            + "received_at,next_attempt_at,retry_not_after,attempt_count) "
                            + "SELECT ?,?,?,?,?,?,?,?,'BPMN',?,?,?,?,?,CAST(? AS jsonb),'PENDING',now,now,"
                            + "now + (? * interval '1 millisecond'),0 FROM observed ON CONFLICT DO NOTHING",
                    authenticatedProducer.tenantId(), event.eventId(), registration.producerId(), authenticatedProducer.issuerFingerprint(),
                    authenticatedProducer.subjectId(), registration.owner().issuerFingerprint(), registration.owner().subjectId(), registration.sourceId(),
                    link.scopeInstanceId(), link.activityOccurrenceId(), event.runId(), event.terminalRevision(), event.fingerprint(), payload, pendingRetention.toMillis());
            var rows = jdbc.query("SELECT * FROM " + table + " WHERE tenant_id=? AND (event_id=? OR "
                            + "(owner_issuer=? AND owner_subject=? AND run_id=?)) FOR UPDATE", (rs, row) -> read(rs),
                    authenticatedProducer.tenantId(), event.eventId(), event.ownerIssuerSha256(), event.ownerSubjectId(), event.runId());
            if (rows.size() != 1) throw conflict();
            var stored = rows.getFirst();
            if (!authenticatedProducer.equals(stored.producer()) || !original.equals(stored.original())) throw conflict();
            return stored.receipt();
        }));
    }

    private void requireDispatchProfile(AgentDispatchStore.Intent dispatch, Object original) {
        var seed = dispatch.seed();
        boolean automationCatalog = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pg_catalog.to_regclass(?) IS NOT NULL", Boolean.class, schema + ".tianji_process_automation_release"));
        boolean automation = automationCatalog && isAutomationPublication(seed);
        if (original instanceof AgentTerminalEventV2 typed) {
            if (automationCatalog && !automation || !seed.inputs().equals(Map.of(
                    "nodeExecutionRef", typed.nodeExecutionRef(), "inputSha256", typed.inputSha256(),
                    "configurationSha256", typed.configurationSha256()))) throw conflict();
        } else if (automation) {
            throw conflict();
        }
    }

    private boolean isAutomationPublication(AgentDispatchStore.Seed seed) {
        // Catalog evidence is immutable; reading it requires no new lock after the V5 dispatch row.
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM %1$s.tianji_process_automation_release automation
                    JOIN %1$s.tianji_process_release_definition definition ON definition.tenant_id=automation.tenant_id
                        AND definition.release_key=automation.release_key AND definition.definition_key=automation.root_definition_key
                    JOIN %1$s.tianji_process_release publication ON publication.tenant_id=automation.tenant_id
                        AND publication.release_key=automation.release_key
                    WHERE automation.tenant_id=? AND automation.release_key=? AND definition.model_type='BPMN'
                        AND definition.engine_definition_id=? AND definition.model_sha256=?
                        AND publication.state='ACTIVE' AND publication.manifest_sha256=automation.compiled_sha256
                )
                """.formatted('"' + schema + '"'), Boolean.class, seed.key().actor().tenantId(), seed.releaseKey(),
                seed.processDefinitionId(), seed.processModelSha256()));
    }

    void requireStorage(DataSource candidate, DataSourceTransactionManager transactionManager, String schema) {
        if (source != candidate || manager != transactionManager || !this.schema.equals(schema)) throw invalid();
    }

    void requireBindings(AgentDispatchStore dispatches, AgentResultProducerRegistry producers) {
        if (this.dispatches != dispatches || this.producers != producers) throw invalid();
    }

    AgentDispatchStore.Intent lockDispatch(AgentResultProducerRegistry.Registration registration, Entry entry) {
        requireTransaction();
        requireEntry(registration, entry);
        var link = entry.link();
        return dispatches.lockForResult(new AgentDispatchStore.Key(registration.owner(), registration.sourceId(), link.scopeInstanceId(), link.activityOccurrenceId()));
    }

    AgentDispatchStore.Intent refreshDispatch(AgentDispatchStore.Intent dispatch, boolean active) {
        requireTransaction();
        return dispatches.refresh(dispatch.seed().key(), active);
    }

    Entry find(AgentResultProducerRegistry.Registration registration, String eventId, boolean lock) {
        requireTransaction();
        producers.require(registration);
        if (eventId == null || !eventId.matches("[0-9a-f]{64}")) throw invalid();
        var actor = registration.producer();
        var rows = jdbc.query("SELECT * FROM " + table + " WHERE tenant_id=? AND event_id=? AND producer_issuer=? AND producer_subject=? "
                        + "AND producer_id=? AND source_id=? AND owner_issuer=? AND owner_subject=?" + (lock ? " FOR UPDATE" : ""), (rs, row) -> read(rs),
                actor.tenantId(), eventId, actor.issuerFingerprint(), actor.subjectId(), registration.producerId(), registration.sourceId(),
                registration.owner().issuerFingerprint(), registration.owner().subjectId());
        if (rows.size() != 1) throw conflict();
        var entry = rows.getFirst();
        if (!registration.equals(requireEntryProducer(entry))) throw conflict();
        return entry;
    }

    /** Observation claims release their row locks before native application begins. */
    List<Entry> due(AgentResultProducerRegistry.Registration registration, int limit, Duration interval) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Inbox discovery must own its short transaction");
        producers.require(registration);
        delay(interval);
        if (limit < 1 || limit > 64) throw invalid();
        var actor = registration.producer();
        return Objects.requireNonNull(transaction.execute(status -> jdbc.query("WITH candidates AS (SELECT tenant_id,event_id FROM " + table
                        + " WHERE tenant_id=? AND producer_issuer=? AND producer_subject=? AND producer_id=? AND source_id=? AND owner_issuer=? AND owner_subject=? "
                        + "AND state='PENDING' AND next_attempt_at<=clock_timestamp() ORDER BY next_attempt_at,event_id LIMIT ? FOR UPDATE SKIP LOCKED) "
                        + "UPDATE " + table + " i SET next_attempt_at=clock_timestamp() + (? * interval '1 millisecond') FROM candidates c "
                        + "WHERE i.tenant_id=c.tenant_id AND i.event_id=c.event_id RETURNING i.*", (rs, row) -> read(rs), actor.tenantId(), actor.issuerFingerprint(),
                actor.subjectId(), registration.producerId(), registration.sourceId(), registration.owner().issuerFingerprint(), registration.owner().subjectId(),
                limit, interval.toMillis())));
    }

    AgentResultReceipt finish(AgentResultProducerRegistry.Registration registration, Entry entry, AgentResultReceiptStateEnum state, String code) {
        return finish(registration, entry, state, code, entry.failures());
    }

    private AgentResultReceipt finish(AgentResultProducerRegistry.Registration registration, Entry entry, AgentResultReceiptStateEnum state,
                                      String code, int failures) {
        requireTransaction();
        requireEntry(registration, entry);
        if (state == null || state == AgentResultReceiptStateEnum.RECEIVED || code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}")
                || failures < 0 || failures > 1000) throw invalid();
        if (jdbc.update("UPDATE " + table + " SET state=?,decided_at=clock_timestamp(),decision_code=?,attempt_count=? "
                        + "WHERE tenant_id=? AND event_id=? AND event_sha256=? AND state='PENDING'", state.name(), code, failures,
                entry.tenantId(), entry.eventId(), entry.fingerprint()) != 1) throw conflict();
        var decided = find(registration, entry.eventId(), true);
        if (decided.receipt().state() == state) {
            lag.recordOnCommit(state, decided.receipt().receivedAt(), decided.decidedAt());
        }
        return decided.receipt();
    }

    /** Normal wait observations retain the failure budget and the originally committed retry deadline. */
    AgentResultReceipt reschedule(AgentResultProducerRegistry.Registration registration, Entry entry, Duration interval, int maxFailures, boolean failed) {
        requireTransaction();
        requireEntry(registration, entry);
        delay(interval);
        if (maxFailures < 1 || maxFailures > 1000) throw invalid();
        int failures = Math.min(1000, entry.failures() + (failed ? 1 : 0));
        if (!entry.retryNotAfter().isAfter(databaseNow())) return finish(registration, entry, AgentResultReceiptStateEnum.DEAD, "RETRY_DEADLINE", failures);
        if (failures >= maxFailures) return finish(registration, entry, AgentResultReceiptStateEnum.DEAD, "RETRY_EXHAUSTED", failures);
        if (jdbc.update("UPDATE " + table + " SET next_attempt_at=clock_timestamp() + (? * interval '1 millisecond'),attempt_count=? "
                        + "WHERE tenant_id=? AND event_id=? AND event_sha256=? AND state='PENDING'", interval.toMillis(), failures,
                entry.tenantId(), entry.eventId(), entry.fingerprint()) != 1) throw conflict();
        return find(registration, entry.eventId(), true).receipt();
    }

    Instant databaseNow() {
        requireTransaction();
        return jdbc.queryForObject("SELECT clock_timestamp()", (rs, row) -> rs.getTimestamp(1).toInstant());
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) {
            throw new IllegalStateException("Inbox application requires the owning Process JDBC transaction");
        }
    }

    private void requireEntry(AgentResultProducerRegistry.Registration registration, Entry entry) {
        producers.require(registration);
        if (entry == null || !registration.equals(requireEntryProducer(entry))) throw conflict();
    }

    private AgentResultProducerRegistry.Registration requireEntryProducer(Entry entry) {
        return entry.eventV2() == null ? producers.require(entry.producer(), entry.event()) : producers.requireV2(entry.producer(), entry.eventV2());
    }

    private static void delay(Duration delay) {
        if (delay == null || delay.compareTo(Duration.ofMillis(100)) < 0 || delay.compareTo(Duration.ofHours(1)) > 0
                || delay.toNanos() % 1_000_000 != 0) throw invalid();
    }

    static Entry read(ResultSet rs) throws SQLException {
        String payload = rs.getString("event_payload");
        JsonNode node = readEnvelope(payload);
        boolean v2 = node.get("schemaVersion").intValue() == 2;
        AgentTerminalEvent v1Event = v2 ? null : decode(payload);
        AgentTerminalEventV2 v2Event = v2 ? decodeV2(node) : null;
        var event = v2 ? EnvelopeVO.of(v2Event) : EnvelopeVO.of(v1Event);
        if (event.tenantId() != rs.getLong("tenant_id") || !event.eventId().equals(rs.getString("event_id"))
                || !event.producerId().equals(rs.getString("producer_id")) || !event.fingerprint().equals(rs.getString("event_sha256"))
                || !event.ownerIssuerSha256().equals(rs.getString("owner_issuer")) || !event.ownerSubjectId().equals(rs.getString("owner_subject"))
                || !event.link().sourceId().equals(rs.getString("source_id")) || !event.link().scopeType().name().equals(rs.getString("scope_type"))
                || !event.link().scopeInstanceId().equals(rs.getString("scope_id")) || !event.link().activityOccurrenceId().equals(rs.getString("occurrence_id"))
                || !event.runId().equals(rs.getString("run_id")) || event.terminalRevision() != rs.getLong("terminal_revision")) throw conflict();
        var actor = new ProcessActor(event.tenantId(), ProcessActor.SubjectType.SERVICE, rs.getString("producer_subject"), rs.getString("producer_issuer"));
        String state = rs.getString("state");
        var received = rs.getTimestamp("received_at").toInstant();
        var expires = rs.getTimestamp("retry_not_after").toInstant();
        var decided = rs.getTimestamp("decided_at");
        String code = rs.getString("decision_code");
        int attempts = rs.getInt("attempt_count");
        if (attempts < 0 || attempts > 1_000 || expires.isBefore(received.plus(Duration.ofMinutes(1)))
                || expires.isAfter(received.plus(Duration.ofDays(30))) || ("PENDING".equals(state) ? decided != null || code != null
                : decided == null || code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}"))) throw conflict();
        var receipt = new AgentResultReceipt(event.eventId(), event.fingerprint(), "PENDING".equals(state)
                ? AgentResultReceiptStateEnum.RECEIVED : AgentResultReceiptStateEnum.valueOf(state), received);
        return new Entry(actor, v1Event, v2Event, event, receipt, expires, attempts, decided == null ? null : decided.toInstant());
    }

    private static String encode(AgentTerminalEvent event) {
        try {
            String value = JSON.writeValueAsString(event);
            if (value.getBytes(StandardCharsets.UTF_8).length > 32_768) throw invalid();
            return value;
        } catch (JsonProcessingException failure) { throw invalid(); }
    }
    private static AgentTerminalEvent decode(String value) {
        try { return JSON.readValue(value, AgentTerminalEvent.class); }
        catch (JsonProcessingException failure) { throw conflict(); }
    }

    private static JsonNode readEnvelope(String value) {
        if (value == null || value.length() > 65_536) throw conflict();
        try {
            var envelope = JSON.readTree(value);
            if (envelope == null || !envelope.isObject()) throw conflict();
            var version = envelope.get("schemaVersion");
            if (version == null || !version.isIntegralNumber() || !version.canConvertToInt()
                    || version.intValue() != 1 && version.intValue() != 2) throw conflict();
            return envelope;
        } catch (JsonProcessingException failure) { throw conflict(); }
    }

    private static AgentTerminalEventV2 decodeV2(JsonNode node) {
        try {
            var event = JSON.treeToValue(node, AgentTerminalEventV2.class);
            // JSONB may reorder keys or change whitespace; the complete normalized field tree must be identical.
            if (!node.equals(JSON.readTree(event.canonicalBytes()))) throw conflict();
            return event;
        } catch (java.io.IOException | IllegalArgumentException failure) { throw conflict(); }
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Agent result Inbox configuration or payload is invalid"); }
    private static ProcessConflictException conflict() { return new ProcessConflictException(); }

    record Entry(ProcessActor producer, AgentTerminalEvent event, AgentTerminalEventV2 eventV2, EnvelopeVO envelope,
                 AgentResultReceipt receipt, Instant retryNotAfter, int failures, Instant decidedAt) {
        Entry(ProcessActor producer, AgentTerminalEvent event, AgentResultReceipt receipt, Instant retryNotAfter, int failures) {
            this(producer, event, null, EnvelopeVO.of(event), receipt, retryNotAfter, failures, null);
        }

        Entry(ProcessActor producer, AgentTerminalEventV2 event, AgentResultReceipt receipt, Instant retryNotAfter, int failures) {
            this(producer, null, event, EnvelopeVO.of(event), receipt, retryNotAfter, failures, null);
        }

        Object original() { return eventV2 == null ? event : eventV2; }
        String eventId() { return envelope.eventId(); }
        long tenantId() { return envelope.tenantId(); }
        String fingerprint() { return envelope.fingerprint(); }
        AgentProcessLink link() { return envelope.link(); }
        String definitionKey() { return envelope.definitionKey(); }
        long definitionVersion() { return envelope.definitionVersion(); }
        String runId() { return envelope.runId(); }
        String idempotencyKey() { return envelope.idempotencyKey(); }
        String producerId() { return envelope.producerId(); }
        String ownerIssuerSha256() { return envelope.ownerIssuerSha256(); }
        String ownerSubjectId() { return envelope.ownerSubjectId(); }
        AgentRunStatusEnum status() { return eventV2 == null ? event.status() : eventV2.status(); }

        @Override public String toString() { return "AgentResultInbox.Entry[state=" + receipt.state() + "]"; }
    }

    private record EnvelopeVO(long tenantId, String eventId, String producerId, String fingerprint, String ownerIssuerSha256,
            String ownerSubjectId, AgentProcessLink link, String runId, long terminalRevision, String idempotencyKey,
            String definitionKey, long definitionVersion) {
        static EnvelopeVO of(AgentTerminalEvent event) {
            return new EnvelopeVO(event.tenantId(), event.eventId(), event.producerId(), event.fingerprint(), event.ownerIssuerSha256(),
                    event.ownerSubjectId(), event.link(), event.runId(), event.terminalRevision(), event.idempotencyKey(),
                    event.definitionKey(), event.definitionVersion());
        }

        static EnvelopeVO of(AgentTerminalEventV2 event) {
            return new EnvelopeVO(event.tenantId(), event.eventId(), event.producerId(), event.fingerprint(), event.ownerIssuerSha256(),
                    event.ownerSubjectId(), event.link(), event.runId(), event.terminalRevision(), event.idempotencyKey(),
                    event.definitionKey(), event.definitionVersion());
        }
    }
}
