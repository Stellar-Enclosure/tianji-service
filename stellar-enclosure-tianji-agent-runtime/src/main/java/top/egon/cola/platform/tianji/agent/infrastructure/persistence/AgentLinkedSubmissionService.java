package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentSubmissionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;

/** Atomically fences one service-owned process occurrence against late submission and cancellation. */
public final class AgentLinkedSubmissionService {
    private final AgentLedgerService ledger;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final AgentJsonCodec json = new AgentJsonCodec();

    public AgentLinkedSubmissionService(DataSource source, AgentLedgerService ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        if (source == null || !ledger.usesDataSource(source)) throw new IllegalArgumentException("LINKED_AGENT_DATA_SOURCE_MISMATCH");
        new AgentStorageVerifier(source).verifyLinked();
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** Creates or replays a run only while its immutable dispatch deadline and fence remain open. */
    public AgentRunDTO submit(TrustedAgentActor actor, LinkedAgentStartCommand command) {
        requireService(actor); Objects.requireNonNull(command, "command"); requireOwnedTransaction();
        var query = new LinkedAgentLookupQuery(command.start().idempotencyKey(), command.link());
        String requestHash = json.linkedRequestHash(command.start(), command.link());
        return submit(actor, query, requestHash, false, () -> true, () -> ledger.startLinked(actor, command));
    }

    /** Invoked by the host after authorizing the exact current V2 source and its selected template. */
    public AgentRunDTO submitRuntimeV2(TrustedAgentActor actor, LinkedAgentRuntimeCommand command) {
        return submitRuntimeV2(actor, command, () -> false);
    }

    public AgentRunDTO submitRuntimeV2(TrustedAgentActor actor, LinkedAgentRuntimeCommand command, BooleanSupplier newAdmissionEnabled) {
        requireService(actor); requireOwnedTransaction();
        var input = json.immutableRuntimeCommand(command);
        var query = new LinkedAgentLookupQuery(input.getIdempotencyKey(), input.getLink());
        return submit(actor, query, json.runtimeRequestHash(input), true, Objects.requireNonNull(newAdmissionEnabled),
                () -> ledger.startLinkedRuntimeV2(actor, input));
    }

    private AgentRunDTO submit(TrustedAgentActor actor, LinkedAgentLookupQuery query, String requestHash,
                               boolean runtimeV2, BooleanSupplier newAdmissionEnabled, Supplier<AgentRunDTO> start) {
        return transact(() -> {
            lockTimeout();
            if (!runtimeV2) ledger.requireDispatchTime(query.link());
            insertFence(actor, query, false);
            Fence fence = lockedFence(actor, query);
            if (fence.closed()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DISPATCH_CLOSED, "Linked Agent dispatch is closed");
            if (fence.runId() != null && !requestHash.equals(fence.requestSha256())) throw conflict();
            if (runtimeV2 && fence.runId() != null) return verifyRun(actor, query.link(), fence.runId(), requestHash, true);
            if (runtimeV2 && !newAdmissionEnabled.getAsBoolean()) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "New Agent runtime admission is disabled");
            }
            AgentRunDTO run = start.get();
            if (fence.runId() != null && !fence.runId().equals(run.runId())) throw conflict();
            if (fence.runId() == null && jdbc.update("""
                    UPDATE tianji_agent.agent_process_dispatch SET run_id=?, request_sha256=?
                    WHERE tenant_id=? AND subject_issuer=? AND subject_id=? AND idempotency_key=?
                      AND state='OPEN' AND run_id IS NULL
                    """, run.runId(), requestHash, actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), query.idempotencyKey()) != 1) {
                throw conflict();
            }
            verifyRun(actor, query.link(), run.runId(), requestHash, runtimeV2);
            ledger.requireDispatchTime(query.link());
            return run;
        });
    }

    /** Returns empty only when the exact service-owned link has never been submitted or closed. */
    public Optional<LinkedAgentSubmissionDTO> resolve(TrustedAgentActor actor, LinkedAgentLookupQuery query) {
        return resolve(actor, query, false);
    }

    public Optional<LinkedAgentSubmissionDTO> resolveRuntimeV2(TrustedAgentActor actor, LinkedAgentLookupQuery query) {
        return resolve(actor, query, true);
    }

    private Optional<LinkedAgentSubmissionDTO> resolve(TrustedAgentActor actor, LinkedAgentLookupQuery query, boolean runtimeV2) {
        requireService(actor); Objects.requireNonNull(query, "query"); requireOwnedTransaction();
        return transact(() -> {
            List<Fence> rows = find(actor, query.idempotencyKey(), false);
            if (rows.isEmpty()) return Optional.empty();
            if (rows.size() != 1 || !matches(rows.getFirst(), query.link())) return Optional.empty();
            Fence fence = rows.getFirst();
            AgentRunDTO run = fence.runId() == null ? null : verifyRun(actor, query.link(), fence.runId(), fence.requestSha256(), runtimeV2);
            if (!fence.closed() && run == null) throw unavailable();
            return Optional.of(new LinkedAgentSubmissionDTO(fence.closed(), run));
        });
    }

    /** Closes even an unknown submission; a concurrent start must finish before its run is cancelled. */
    public LinkedAgentSubmissionDTO close(TrustedAgentActor actor, LinkedAgentLookupQuery query) {
        return close(actor, query, false);
    }

    public LinkedAgentSubmissionDTO closeRuntimeV2(TrustedAgentActor actor, LinkedAgentLookupQuery query) {
        return close(actor, query, true);
    }

    private LinkedAgentSubmissionDTO close(TrustedAgentActor actor, LinkedAgentLookupQuery query, boolean runtimeV2) {
        requireService(actor); Objects.requireNonNull(query, "query"); requireOwnedTransaction();
        return transact(() -> {
            lockTimeout();
            insertFence(actor, query, true);
            Fence fence = lockedFence(actor, query);
            AgentRunDTO run = null;
            if (fence.runId() != null) {
                run = ledger.lockForLinkedClose(actor, fence.runId());
                verifyRun(actor, query.link(), fence.runId(), fence.requestSha256(), runtimeV2);
            }
            if (!fence.closed()) {
                if (jdbc.update("""
                        UPDATE tianji_agent.agent_process_dispatch SET state='CLOSED', closed_at=clock_timestamp()
                        WHERE tenant_id=? AND subject_issuer=? AND subject_id=? AND idempotency_key=? AND state='OPEN'
                        """, actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), query.idempotencyKey()) != 1) throw conflict();
                if (run != null) {
                    String cancelId = "linked-close-" + json.processLinkHash(query.link());
                    ledger.cancel(actor, new CancelAgentRunCommand(run.runId(), cancelId, run.revision(), "process dispatch closed"));
                    run = ledger.get(actor, new GetAgentRunQuery(run.runId()));
                }
            }
            return new LinkedAgentSubmissionDTO(true, run);
        });
    }

    private void insertFence(TrustedAgentActor actor, LinkedAgentLookupQuery query, boolean closed) {
        AgentProcessLink link = query.link();
        jdbc.update("""
                INSERT INTO tianji_agent.agent_process_dispatch
                    (tenant_id,subject_issuer,subject_id,idempotency_key,source_id,scope_type,scope_id,occurrence_id,
                     definition_sha256,dispatch_not_after,link_sha256,state,created_at,closed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,clock_timestamp(),CASE WHEN ? THEN clock_timestamp() ELSE NULL END)
                ON CONFLICT DO NOTHING
                """, actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), query.idempotencyKey(), link.sourceId(),
                link.scopeType().name(), link.scopeInstanceId(), link.activityOccurrenceId(), link.definitionContentSha256(),
                Timestamp.from(link.dispatchNotAfter()), json.processLinkHash(link), closed ? "CLOSED" : "OPEN", closed);
    }

    private Fence lockedFence(TrustedAgentActor actor, LinkedAgentLookupQuery query) {
        List<Fence> rows = find(actor, query.idempotencyKey(), true);
        if (rows.size() != 1 || !matches(rows.getFirst(), query.link())) throw conflict();
        return rows.getFirst();
    }

    private List<Fence> find(TrustedAgentActor actor, String key, boolean lock) {
        return jdbc.query("""
                SELECT source_id,scope_type,scope_id,occurrence_id,definition_sha256,dispatch_not_after,
                       link_sha256,request_sha256,state,run_id FROM tianji_agent.agent_process_dispatch
                WHERE tenant_id=? AND subject_issuer=? AND subject_id=? AND idempotency_key=?
                """ + (lock ? " FOR UPDATE" : ""), (rs, row) -> new Fence(rs.getString("source_id"), rs.getString("scope_type"),
                rs.getString("scope_id"), rs.getString("occurrence_id"), rs.getString("definition_sha256"),
                rs.getTimestamp("dispatch_not_after").toInstant(), rs.getString("link_sha256"), rs.getString("request_sha256"),
                "CLOSED".equals(rs.getString("state")), rs.getString("run_id")),
                actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), key);
    }

    private boolean matches(Fence fence, AgentProcessLink link) {
        return link.sourceId().equals(fence.sourceId()) && link.scopeType().name().equals(fence.scopeType())
                && link.scopeInstanceId().equals(fence.scopeId()) && link.activityOccurrenceId().equals(fence.occurrenceId())
                && link.definitionContentSha256().equals(fence.definitionSha256()) && link.dispatchNotAfter().equals(fence.notAfter())
                && json.processLinkHash(link).equals(fence.linkSha256());
    }

    private AgentRunDTO verifyRun(TrustedAgentActor actor, AgentProcessLink link, String runId, String requestHash, boolean runtimeV2) {
        if (runtimeV2) {
            var material = ledger.runtimeExecutionMaterial(actor, new GetAgentRunQuery(runId));
            if (!link.equals(material.command().getLink()) || requestHash == null
                    || !requestHash.equals(material.authorization().requestSha256())) throw unavailable();
            return material.run();
        }
        var material = ledger.executionMaterial(actor, new GetAgentRunQuery(runId));
        if (!link.equals(material.snapshot().processLink()) || requestHash == null
                || !requestHash.equals(material.authorization().requestSha256())) throw unavailable();
        return material.run();
    }

    private void lockTimeout() { jdbc.queryForObject("SELECT set_config('lock_timeout', '5000ms', true)", String.class); }

    private static void requireService(TrustedAgentActor actor) {
        if (actor == null || actor.subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new IllegalArgumentException("Linked Agent submission requires a verified service actor");
        }
    }

    private static void requireOwnedTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Linked Agent submission must own its transaction");
        }
    }

    private <T> T transact(java.util.function.Supplier<T> work) {
        try { return Objects.requireNonNull(transaction.execute(status -> work.get())); }
        catch (DataAccessException failure) { throw unavailable(); }
    }
    private static AgentLedgerException conflict() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Linked Agent submission conflicts with an existing intent");
    }
    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Linked Agent submission is unavailable");
    }
    private record Fence(String sourceId, String scopeType, String scopeId, String occurrenceId, String definitionSha256,
                         Instant notAfter, String linkSha256, String requestSha256, boolean closed, String runId) { }
}
