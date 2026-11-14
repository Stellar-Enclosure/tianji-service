package top.egon.cola.platform.tianji.process.server.bridge;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessAgentOutcomesQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO.DispatchState;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO.InboxState;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO.ItemDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reads only Process-owned dispatch and Inbox evidence. It never addresses the Agent API. */
public final class ProcessLinkedAgentOutcomeQuery {
    private static final int PAGE_SIZE = ProcessAgentOutcomePageDTO.PAGE_SIZE;
    private static final int SCOPE_LIMIT = 256;
    private static final String ANCHOR_DOMAIN = "TIANJI_PROCESS_AGENT_OUTCOME_CURSOR_V1";
    private static final Set<String> LATE = Set.of("DISPATCH_CLOSED", "WAIT_ENDED", "MESSAGE_CONSUMED");
    private static final Set<String> DEAD = Set.of("DISPATCH_MISMATCH", "RETRY_DEADLINE", "RETRY_EXHAUSTED");
    private static final String TYPED_RESULT_SCHEMA_MISMATCH = "RESULT_SCHEMA_MISMATCH";

    private final DataSource source;
    private final String schema;
    private final String dispatchTable;
    private final String inboxTable;
    private final String receiptTable;
    private final String definitionTable;
    private final AgentResultProducerRegistry producers;
    private final Map<Long, SourceScopeVO> scopes;
    private final JdbcTemplate jdbc;
    private final ProcessCommandReceiptStore receipts;
    private final TransactionTemplate read;

    public ProcessLinkedAgentOutcomeQuery(DataSource source, DataSourceTransactionManager manager, String schema,
            AgentDispatchStore dispatches, AgentResultInbox inbox, AgentResultProducerRegistry producers,
            List<AgentResultProducerRegistry.Registration> registrations) {
        if (source == null || manager == null || manager.getDataSource() != source
                || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw unavailable();
        dispatches.requireStorage(source, manager, schema);
        inbox.requireStorage(source, manager, schema);
        inbox.requireBindings(dispatches, producers);
        this.source = source;
        this.schema = schema;
        this.dispatchTable = table(schema, "tianji_process_agent_dispatch");
        this.inboxTable = table(schema, "tianji_process_agent_result_inbox");
        this.receiptTable = table(schema, "tianji_process_command_receipt");
        this.definitionTable = table(schema, "tianji_process_release_definition");
        this.producers = Objects.requireNonNull(producers, "producers");
        this.scopes = scopes(registrations);
        this.jdbc = new JdbcTemplate(source);
        this.receipts = new ProcessCommandReceiptStore(jdbc, schema);
        this.read = new TransactionTemplate(manager);
        // Leave routing on the owning primary; make the physical transaction read-only before any read.
        this.read.setReadOnly(false);
        this.read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.read.setTimeout(2);
    }

    public ProcessAgentOutcomePageDTO list(ProcessActor actor, GetProcessAgentOutcomesQuery query) {
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new AccessDeniedException("Verified Process owner is required");
        }
        Objects.requireNonNull(query, "query");
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive() || Thread.currentThread().isInterrupted()) {
            throw unavailable();
        }
        var scope = scopes.get(actor.tenantId());
        if (scope == null) throw new ProcessResourceNotFoundException();
        try {
            return Objects.requireNonNull(read.execute(status -> list(actor, query, scope)));
        } catch (DataAccessException | TransactionException failure) {
            throw unavailable();
        }
    }

    private ProcessAgentOutcomePageDTO list(ProcessActor actor, GetProcessAgentOutcomesQuery query, SourceScopeVO scope) {
        Instant observedAt = beginObservation();
        TaskOrigin start = receipts.readStartByInstance(actor, query.instanceId());
        List<String> ids = jdbc.query("SELECT occurrence_id FROM " + dispatchTable
                        + " WHERE tenant_id=? AND source_id=? AND scope_type='BPMN' AND scope_id=?"
                        + " ORDER BY occurrence_id COLLATE \"C\" LIMIT " + (SCOPE_LIMIT + 1),
                (rs, row) -> rs.getString(1), actor.tenantId(), scope.sourceId(), start.instanceId());
        if (ids.size() > SCOPE_LIMIT) throw unavailable();
        validateIds(ids, start, scope);
        int offset = 0;
        if (query.cursor() != null) {
            int matched = -1;
            for (int i = 0; i < ids.size(); i++) {
                if (query.cursor().equals(cursor(actor, scope.sourceId(), start.instanceId(), ids.get(i)))) {
                    if (matched >= 0) throw unavailable();
                    matched = i;
                }
            }
            if (matched < 0) throw invalidCursor();
            offset = matched + 1;
        }

        int end = Math.min(offset + PAGE_SIZE, ids.size());
        List<String> pageIds = ids.subList(offset, end);
        List<ItemDTO> items = pageIds.isEmpty() ? List.of() : readPage(start, scope, pageIds, observedAt);
        String next = end == ids.size() ? null : cursor(actor, scope.sourceId(), start.instanceId(), ids.get(end - 1));
        return new ProcessAgentOutcomePageDTO(start.instanceId(), observedAt, items, next);
    }

    private List<ItemDTO> readPage(TaskOrigin start, SourceScopeVO scope, List<String> ids, Instant observedAt) {
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        var arguments = new ArrayList<Object>();
        arguments.add(start.requester().tenantId());
        arguments.add(scope.sourceId());
        arguments.add(start.instanceId());
        arguments.addAll(ids);
        List<DispatchOutcomeVO> rows = jdbc.query("""
                SELECT d.occurrence_id AS dispatch_occurrence, d.subject_issuer AS dispatch_issuer,
                       d.subject_id AS dispatch_subject, d.idempotency_key AS dispatch_idempotency,
                       d.release_key AS dispatch_release, d.correlation_id AS dispatch_correlation,
                       d.element_id AS dispatch_element, d.agent_definition_key AS agent_definition_key,
                       d.agent_definition_version AS agent_definition_version,
                       d.agent_definition_sha256 AS agent_definition_sha256,
                       d.state AS dispatch_state, d.run_id AS dispatch_run, d.created_at AS dispatched_at,
                       d.closed_at AS dispatch_closed_at, d.dispatch_not_after AS dispatch_not_after,
                       d.submission_timeout_ms AS dispatch_timeout_ms,
                       r.engine_definition_id IS NOT NULL AS released_model,
                       i.tenant_id, i.event_id, i.producer_id, i.producer_issuer, i.producer_subject,
                       i.owner_issuer, i.owner_subject, i.source_id, i.scope_type, i.scope_id, i.occurrence_id,
                       i.run_id, i.terminal_revision, i.event_sha256, i.event_payload, i.state,
                       i.received_at, i.retry_not_after, i.attempt_count, i.decided_at, i.decision_code,
                       a.state AS audit_state, a.request_sha256 AS audit_hash,
                       a.release_key AS audit_release, a.correlation_id AS audit_correlation,
                       a.resource_id AS audit_resource
                FROM %s d
                LEFT JOIN %s r ON r.tenant_id=d.tenant_id AND r.release_key=d.release_key AND r.model_type='BPMN'
                    AND r.engine_definition_id=d.process_definition_id AND r.model_sha256=d.process_model_sha256
                LEFT JOIN %s i ON i.tenant_id=d.tenant_id AND i.source_id=d.source_id AND i.scope_type=d.scope_type
                    AND i.scope_id=d.scope_id AND i.occurrence_id=d.occurrence_id AND i.owner_issuer=d.subject_issuer
                    AND i.owner_subject=d.subject_id AND i.run_id=d.run_id
                LEFT JOIN %s a ON i.state='APPLIED' AND a.tenant_id=i.tenant_id AND a.subject_type='SERVICE'
                    AND a.subject_issuer=i.producer_issuer AND a.subject_id=i.producer_subject
                    AND a.command_type='AGENT_RESULT' AND a.command_id=i.event_id
                WHERE d.tenant_id=? AND d.source_id=? AND d.scope_type='BPMN' AND d.scope_id=?
                    AND d.occurrence_id IN (%s)
                ORDER BY d.occurrence_id COLLATE "C"
                LIMIT %d
                """.formatted(dispatchTable, definitionTable, inboxTable, receiptTable, placeholders, PAGE_SIZE + 1),
                (rs, row) -> readOutcome(rs, start, scope, observedAt), arguments.toArray());
        if (!rows.stream().map(DispatchOutcomeVO::occurrence).toList().equals(ids) || Thread.currentThread().isInterrupted()) {
            throw unavailable();
        }
        return rows.stream().map(DispatchOutcomeVO::item).toList();
    }

    private DispatchOutcomeVO readOutcome(ResultSet rs, TaskOrigin start, SourceScopeVO scope, Instant observedAt)
            throws SQLException {
        try {
            String occurrence = rs.getString("dispatch_occurrence");
            var owner = new ProcessActor(start.requester().tenantId(), ProcessActor.SubjectType.SERVICE,
                    rs.getString("dispatch_subject"), rs.getString("dispatch_issuer"));
            var key = new AgentDispatchStore.Key(owner, scope.sourceId(), start.instanceId(), occurrence);
            var state = DispatchState.valueOf(rs.getString("dispatch_state"));
            String run = rs.getString("dispatch_run");
            Instant created = instant(rs, "dispatched_at");
            Instant closed = instant(rs, "dispatch_closed_at");
            Instant deadline = instant(rs, "dispatch_not_after");
            long timeout = rs.getLong("dispatch_timeout_ms");
            if (!scope.owners().contains(owner) || !rs.getBoolean("released_model")
                    || !start.releaseKey().equals(rs.getString("dispatch_release"))
                    || !start.correlationId().equals(rs.getString("dispatch_correlation"))
                    || !AgentDispatchStore.idempotencyKey(key).equals(rs.getString("dispatch_idempotency"))
                    || created == null || created.isBefore(start.createdAt()) || created.isAfter(observedAt)
                    || deadline == null || timeout < 1_000 || timeout > 900_000 || !deadline.equals(created.plusMillis(timeout))
                    || (state == DispatchState.CLOSED) != (closed != null)
                    || closed != null && (closed.isBefore(created) || closed.isAfter(observedAt))
                    || state == DispatchState.PENDING && run != null
                    || (state == DispatchState.SUBMITTED || state == DispatchState.ACKNOWLEDGED) && run == null) throw unavailable();

            String activity = rs.getString("dispatch_element");
            var item = rs.getString("event_id") == null
                    ? new ItemDTO(activity, state, run == null ? InboxState.RUN_NOT_CONFIRMED : InboxState.NOT_OBSERVED,
                            null, created, null, null)
                    : received(rs, start, key, state, run, created, deadline, observedAt, activity);
            return new DispatchOutcomeVO(occurrence, item);
        } catch (IllegalArgumentException | ProcessConflictException failure) {
            throw unavailable();
        }
    }

    private ItemDTO received(ResultSet rs, TaskOrigin start, AgentDispatchStore.Key key, DispatchState dispatch,
            String run, Instant created, Instant deadline, Instant observedAt, String activity) throws SQLException {
        var entry = AgentResultInbox.read(rs);
        var registration = entry.eventV2() == null
                ? producers.require(entry.producer(), entry.event())
                : producers.requireV2(entry.producer(), entry.eventV2());
        var receipt = entry.receipt();
        var received = receipt.receivedAt();
        var decided = entry.decidedAt();
        String reason = rs.getString("decision_code");
        if (!registration.owner().equals(key.actor()) || !registration.sourceId().equals(key.sourceId())
                || !key.instanceId().equals(entry.link().scopeInstanceId())
                || !key.occurrenceId().equals(entry.link().activityOccurrenceId())
                || !entry.runId().equals(run) || received.isBefore(created) || received.isAfter(observedAt)
                || decided != null && (decided.isBefore(received) || decided.isAfter(observedAt))) throw unavailable();
        switch (receipt.state()) {
            case RECEIVED -> {
                if (reason != null || decided != null) throw unavailable();
            }
            case APPLIED -> {
                if (dispatch == DispatchState.SUBMITTED || !"MESSAGE_APPLIED".equals(reason) || decided == null
                        || !"APPLIED".equals(rs.getString("audit_state"))
                        || !entry.fingerprint().equals(rs.getString("audit_hash"))
                        || !start.releaseKey().equals(rs.getString("audit_release"))
                        || !start.correlationId().equals(rs.getString("audit_correlation"))
                        || rs.getString("audit_resource") == null || rs.getString("audit_resource").isBlank()) throw unavailable();
            }
            case IGNORED_LATE -> {
                if (reason == null || !LATE.contains(reason) || decided == null) throw unavailable();
            }
            case DEAD -> {
                if (reason == null || decided == null || !DEAD.contains(reason)
                        && !(entry.eventV2() != null && TYPED_RESULT_SCHEMA_MISMATCH.equals(reason))) throw unavailable();
            }
        }
        AgentRunStatusEnum terminal = null;
        if (!"DISPATCH_MISMATCH".equals(reason)) {
            var link = new AgentProcessLink(key.sourceId(), WorkerScopeTypeEnum.BPMN, key.instanceId(), key.occurrenceId(),
                    rs.getString("agent_definition_sha256"), deadline);
            if (!new LinkedAgentLookupQuery(AgentDispatchStore.idempotencyKey(key), link)
                    .equals(new LinkedAgentLookupQuery(entry.idempotencyKey(), entry.link()))
                    || !entry.definitionKey().equals(rs.getString("agent_definition_key"))
                    || entry.definitionVersion() != rs.getLong("agent_definition_version")) throw unavailable();
            terminal = entry.status();
        }
        return new ItemDTO(activity, dispatch, InboxState.valueOf(receipt.state().name()), terminal, created, received, decided);
    }

    private Instant beginObservation() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source)) throw unavailable();
        jdbc.execute("SET TRANSACTION READ ONLY");
        jdbc.execute("SET LOCAL statement_timeout = '2s'");
        var observed = jdbc.queryForObject("""
                SELECT pg_catalog.clock_timestamp()
                WHERE pg_catalog.current_schema()=?
                    AND NOT pg_catalog.pg_is_in_recovery()
                    AND pg_catalog.current_setting('server_encoding')='UTF8'
                    AND pg_catalog.current_setting('session_replication_role')='origin'
                    AND pg_catalog.current_setting('transaction_read_only')='on'
                    AND pg_catalog.current_setting('transaction_isolation')='repeatable read'
                    AND (SELECT count(*)
                         FROM pg_catalog.pg_class r JOIN pg_catalog.pg_namespace ns ON ns.oid=r.relnamespace
                         WHERE ns.nspname=? AND r.relname IN ('tianji_process_command_receipt',
                              'tianji_process_release_definition','tianji_process_agent_dispatch','tianji_process_agent_result_inbox')
                           AND r.relkind='r' AND r.relpersistence='p' AND NOT r.relispartition
                           AND NOT r.relrowsecurity AND NOT r.relforcerowsecurity
                           AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_inherits WHERE inhrelid=r.oid OR inhparent=r.oid)
                           AND pg_catalog.has_schema_privilege(ns.oid,'USAGE')
                           AND pg_catalog.has_table_privilege(r.oid,'SELECT'))=4
                """, OffsetDateTime.class, schema, schema);
        if (observed == null) throw unavailable();
        return observed.toInstant();
    }

    private Map<Long, SourceScopeVO> scopes(List<AgentResultProducerRegistry.Registration> registrations) {
        if (registrations == null || registrations.isEmpty()) throw unavailable();
        var sources = new HashMap<Long, String>();
        var owners = new HashMap<Long, Set<ProcessActor>>();
        for (var registration : registrations) {
            producers.require(registration);
            long tenant = registration.owner().tenantId();
            String previous = sources.putIfAbsent(tenant, registration.sourceId());
            if (previous != null && !previous.equals(registration.sourceId())) throw unavailable();
            owners.computeIfAbsent(tenant, ignored -> new HashSet<>()).add(registration.owner());
        }
        var result = new HashMap<Long, SourceScopeVO>();
        sources.forEach((tenant, sourceId) -> result.put(tenant, new SourceScopeVO(sourceId, Set.copyOf(owners.get(tenant)))));
        return Map.copyOf(result);
    }

    private static void validateIds(List<String> ids, TaskOrigin start, SourceScopeVO scope) {
        byte[] previous = null;
        for (String id : ids) {
            try {
                new AgentProcessLink(scope.sourceId(), WorkerScopeTypeEnum.BPMN, start.instanceId(), id, "0".repeat(64), Instant.EPOCH);
            } catch (IllegalArgumentException malformed) {
                throw unavailable();
            }
            byte[] current = id.getBytes(StandardCharsets.UTF_8);
            if (previous != null && Arrays.compareUnsigned(previous, current) >= 0) throw unavailable();
            previous = current;
        }
    }

    private static String cursor(ProcessActor actor, String source, String instance, String occurrence) {
        return "v1." + ProcessRequestDigest.hash(List.of(ANCHOR_DOMAIN, Long.toString(actor.tenantId()),
                actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), source, instance, occurrence), Map.of());
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        var value = result.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static String table(String schema, String name) {
        return '"' + schema + "\"." + name;
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Process Agent outcome evidence is unavailable");
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("Process Agent outcome cursor is invalid");
    }

    private record SourceScopeVO(String sourceId, Set<ProcessActor> owners) { }
    private record DispatchOutcomeVO(String occurrence, ItemDTO item) { }
}
