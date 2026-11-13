package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolExecutionJsonCodec.ActionProfile;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolExecutionStorageVerifier.SchemaProfile;
import top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier.ApprovedObservation;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Internal transaction participant for governed preparation and bounded historical facts. */
final class ToolActionIntentSql {
    static final Set<String> STATES = Set.of("PREPARED", "AWAITING_APPROVAL", "DISPATCHING", "APPLIED", "REJECTED", "FAILED_KNOWN", "UNKNOWN", "EXPIRED");
    private static final String OWNER_ROW_PROJECTION = """
                SELECT a.*,
                  (o.run_id IS NOT NULL AND o.subject_type=a.owner_type AND o.subject_issuer=a.owner_issuer AND o.subject_id=a.owner_id) AS origin_owner_matches,
                  (a.dispatch_epoch=0 OR (d.run_id IS NOT NULL AND d.subject_type=a.owner_type AND d.subject_issuer=a.owner_issuer AND d.subject_id=a.owner_id)) AS dispatch_owner_matches,
                  (a.dispatch_epoch=0 OR (i.invocation_id IS NOT NULL AND i.owner_type=a.owner_type AND i.owner_issuer=a.owner_issuer AND i.owner_id=a.owner_id
                    AND i.intent_id=a.intent_id AND i.tool_key=a.tool_key AND i.tool_version=a.tool_version AND i.tool_sha256=a.tool_sha256
                    AND i.binding_sha256=a.binding_sha256 AND i.risk=a.risk AND i.run_id=a.dispatch_run_id AND i.invocation_id=a.dispatch_invocation_id
                    AND i.dispatch_epoch=a.dispatch_epoch AND i.dispatch_nonce_sha256=a.dispatch_nonce_sha256 AND i.executor_issuer=a.executor_issuer AND i.executor_id=a.executor_id)) AS dispatch_identity_matches,
                  (a.dispatch_epoch=0 OR (i.state=CASE a.state WHEN 'APPLIED' THEN 'SUCCEEDED' ELSE a.state END
                    AND a.result IS NOT DISTINCT FROM i.outcome AND a.business_receipt_id IS NOT DISTINCT FROM i.business_receipt_id
                    AND a.last_error_code IS NOT DISTINCT FROM i.error_code AND a.side_effect_outcome=i.side_effect_outcome)) AS dispatch_fact_matches,
                  i.parameters_sha256 AS dispatch_parameters_sha256,
                  (pg_catalog.to_jsonb(a)->'action_profile')::text AS stored_action_profile,
                  (pg_catalog.to_jsonb(a)->'dispatch_call_id')::text AS stored_dispatch_call_id
                """;
    private static final String OWNER_ROW_JOINS = """
                FROM %1$stianji_agent.agent_tool_action_intent a
                LEFT JOIN %1$stianji_agent.agent_run o ON o.tenant_id=a.tenant_id AND o.run_id=a.origin_run_id
                LEFT JOIN %1$stianji_agent.agent_run d ON d.tenant_id=a.tenant_id AND d.run_id=a.dispatch_run_id
                LEFT JOIN %1$stianji_agent.agent_tool_invocation i ON i.tenant_id=a.tenant_id AND i.run_id=a.dispatch_run_id AND i.invocation_id=a.dispatch_invocation_id
                """;
    private static final String OWNER_ROW_KEY = """
                WHERE a.tenant_id=? AND a.owner_type=? AND a.owner_issuer=? AND a.owner_id=? AND a.intent_id=?
                """;
    private static final String OWNER_ROW_READ = OWNER_ROW_PROJECTION + OWNER_ROW_JOINS.formatted("") + OWNER_ROW_KEY;
    private static final String RUNTIME_OWNER_ROW_READ = OWNER_ROW_PROJECTION + """
                , rb.snapshot_sha256 AS origin_runtime_snapshot_sha256,
                  db.snapshot_sha256 AS dispatch_runtime_snapshot_sha256,
                  (r.call_id IS NOT NULL AND r.kind='TOOL' AND r.intent_id=a.intent_id
                    AND r.owner_type=a.owner_type AND r.owner_issuer=a.owner_issuer AND r.owner_id=a.owner_id
                    AND r.tool_key=a.tool_key AND r.tool_version=a.tool_version AND r.tool_sha256=a.tool_sha256
                    AND r.backend_binding_sha256=a.binding_sha256 AND r.risk=a.risk
                    AND r.dispatch_epoch=a.dispatch_epoch AND r.dispatch_nonce_sha256=a.dispatch_nonce_sha256
                    AND r.executor_issuer=a.executor_issuer AND r.executor_id=a.executor_id
                    AND r.dispatched_at IS NOT NULL AND r.source_action_revision IS NULL AND r.source_result_sha256 IS NULL) AS runtime_dispatch_matches,
                  r.input_sha256 AS runtime_input_sha256, r.state AS runtime_call_state,
                  r.result_sha256 AS runtime_result_sha256, r.error_code AS runtime_error_code
                """ + OWNER_ROW_JOINS.formatted("") + """
                LEFT JOIN tianji_agent.agent_runtime_binding rb ON rb.tenant_id=a.tenant_id AND rb.run_id=a.origin_run_id AND rb.runtime_profile='RUNTIME_V2'
                LEFT JOIN tianji_agent.agent_runtime_binding db ON db.tenant_id=a.tenant_id AND db.run_id=a.dispatch_run_id AND db.runtime_profile='RUNTIME_V2'
                LEFT JOIN tianji_agent.agent_runtime_call_receipt r ON r.tenant_id=a.tenant_id AND r.run_id=a.dispatch_run_id AND r.call_id=a.dispatch_call_id
                """ + OWNER_ROW_KEY;
    private static final int ACTION_STAMP_BYTES = 524_288;
    private static final int INVOCATION_STAMP_BYTES = 262_144;
    private static final int RUN_STAMP_BYTES = 8_388_608;
    private static final String HISTORICAL_START = OWNER_ROW_PROJECTION + """
                , i.revision AS invocation_revision, i.dispatch_not_after,
                  sealed.action_stamp, sealed.invocation_stamp, sealed.origin_stamp, sealed.dispatch_stamp
                """ + OWNER_ROW_JOINS.formatted("ONLY ") + """
                CROSS JOIN LATERAL (
                    SELECT pg_catalog.to_jsonb(a)::text AS action_stamp,
                           pg_catalog.to_jsonb(i)::text AS invocation_stamp,
                           pg_catalog.to_jsonb(o)::text AS origin_stamp,
                           pg_catalog.to_jsonb(d)::text AS dispatch_stamp
                ) sealed
                """ + OWNER_ROW_KEY + """
                  AND a.dispatch_run_id=? AND a.dispatch_epoch>0
                  AND a.state IN ('DISPATCHING','UNKNOWN','APPLIED')
                  AND d.lease_owner IS NULL AND d.lease_expires_at IS NULL
                  AND (d.status IN ('COMPLETED','FAILED','CANCELLED','TIMED_OUT')
                      OR (d.status='SUSPENDED' AND d.wait_reason='LEASE_EXPIRED'))
                  AND i.dispatch_not_after<=pg_catalog.clock_timestamp()
                  AND pg_catalog.octet_length(sealed.action_stamp)<=?
                  AND pg_catalog.octet_length(sealed.invocation_stamp)<=?
                  AND pg_catalog.octet_length(sealed.origin_stamp)<=?
                  AND pg_catalog.octet_length(sealed.dispatch_stamp)<=?
                LIMIT 2
                """;
    private final DataSource source;
    private final AgentLedgerService ledger;
    private final JdbcTemplate jdbc;
    private final ToolExecutionJsonCodec codec = new ToolExecutionJsonCodec();

    ToolActionIntentSql(DataSource source, AgentLedgerService ledger) {
        this.source = Objects.requireNonNull(source, "source"); this.ledger = Objects.requireNonNull(ledger, "ledger");
        if (!ledger.usesDataSource(source)) throw invalid();
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
    }

    Row prepare(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action, ToolDescriptor descriptor,
                Instant requestedExpiry, Instant authorityDeadline) {
        return prepareWithStatus(run, lease, action, descriptor, requestedExpiry, authorityDeadline).row();
    }

    Row prepare(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action, ToolDescriptor descriptor,
                Instant requestedExpiry, Instant authorityDeadline, SchemaProfile schema) {
        return prepareWithStatus(run, lease, action, descriptor, requestedExpiry, authorityDeadline, schema).row();
    }

    PreparationResultVO prepareWithStatus(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action, ToolDescriptor descriptor,
                                          Instant requestedExpiry, Instant authorityDeadline) {
        return prepareWithStatus(run, lease, action, descriptor, requestedExpiry, authorityDeadline, null);
    }

    private PreparationResultVO prepareWithStatus(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action, ToolDescriptor descriptor,
                                                  Instant requestedExpiry, Instant authorityDeadline, SchemaProfile schema) {
        binding(run, lease, action, descriptor); requireRun(run, lease, descriptor, authorityDeadline);
        UUID id = UUID.fromString(action.intentId());
        // Serialize first insertion even when distinct request bodies race the same stable business identity.
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> { }, id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        requireRun(run, lease, descriptor, authorityDeadline);
        var existing = read(run.actor(), id, true, authorityDeadline, schema, false);
        if (existing.isPresent()) {
            same(existing.orElseThrow(), action); requireRun(run, lease, descriptor, authorityDeadline);
            return new PreparationResultVO(existing.orElseThrow(), false);
        }
        Instant now = now(authorityDeadline);
        Instant expiry = Objects.requireNonNull(requestedExpiry, "requestedExpiry").truncatedTo(ChronoUnit.MICROS);
        if (!expiry.isAfter(now) || expiry.isAfter(now.plus(Duration.ofDays(7)))) throw invalid();
        String state = descriptor.policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED ? "AWAITING_APPROVAL" : "PREPARED";
        int inserted = jdbc.update("""
                INSERT INTO tianji_agent.agent_tool_action_intent
                  (tenant_id,intent_id,owner_type,owner_issuer,owner_id,action_key,business_key,business_version,tool_key,tool_version,tool_sha256,binding_sha256,request_sha256,logical_identity_sha256,
                   descriptor,arguments,origin_run_id,origin_snapshot_sha256,risk,approval_policy_ref,state,revision,side_effect_outcome,created_at,updated_at,expires_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?,?,1,'NONE',?,?,?) ON CONFLICT DO NOTHING
                """, run.actor().tenantId(), id, run.actor().subjectType().name(), run.actor().issuerFingerprint(), run.actor().subjectId(), action.actionKey(), action.businessKey(), action.businessVersion(),
                descriptor.key(), descriptor.version(), descriptor.reference().contentSha256(), descriptor.policy().bindingSha256(), action.requestSha256(), logicalIdentity(action), codec.descriptor(descriptor), codec.arguments(descriptor, action.arguments()),
                run.runId(), run.snapshotSha256(), descriptor.policy().risk().name(), descriptor.policy().approvalPolicyRef(), state, Timestamp.from(now), Timestamp.from(now), Timestamp.from(expiry));
        if (inserted != 1) throw unavailable();
        var row = read(run.actor(), id, true, authorityDeadline, schema, false).orElseThrow(ToolActionIntentSql::unavailable);
        same(row, action); requireRun(run, lease, descriptor, authorityDeadline);
        return new PreparationResultVO(row, true);
    }

    record PreparationResultVO(Row row, boolean newlyCreated) {
        PreparationResultVO { Objects.requireNonNull(row, "row"); }
        @Override public String toString() { return "ToolActionIntentPreparation[redacted]"; }
    }

    Optional<Row> current(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action,
                          ToolDescriptor descriptor, Instant authorityDeadline) {
        return current(run, lease, action, descriptor, authorityDeadline, null);
    }

    Optional<Row> current(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action,
                          ToolDescriptor descriptor, Instant authorityDeadline, SchemaProfile schema) {
        binding(run, lease, action, descriptor); requireRun(run, lease, descriptor, authorityDeadline);
        var found = read(run.actor(), UUID.fromString(action.intentId()), false, authorityDeadline, schema, false);
        found.ifPresent(row -> same(row, action)); requireRun(run, lease, descriptor, authorityDeadline); return found;
    }

    Optional<Row> historical(ToolActionRequest action, ToolDescriptor descriptor, Instant storageDeadline) {
        return historical(action, descriptor, storageDeadline, null);
    }

    Optional<Row> historical(ToolActionRequest action, ToolDescriptor descriptor, Instant storageDeadline, SchemaProfile schema) {
        if (action == null || descriptor == null || !descriptor.reference().equals(action.tool())) throw invalid();
        sameAction(new ToolActionRequest(action.owner(), descriptor, action.businessKey(), action.businessVersion(), action.arguments()), action);
        var found = read(action.owner(), UUID.fromString(action.intentId()), false, storageDeadline, schema, schema == SchemaProfile.V17);
        found.ifPresent(row -> same(row, action)); return found;
    }

    /** Reads the exact historical source and seals the same snapshot for the later positive observation. */
    Optional<HistoricalStartReceiptVO> historicalStartReceipt(TrustedAgentActor owner, String dispatchRunId, UUID intentId, Instant deadline) {
        if (owner == null || !hash(owner.issuerFingerprint()) || !runId(dispatchRunId) || intentId == null) throw invalid();
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                        .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) throw unavailable();
        now(deadline);
        try {
            var rows = jdbc.query(HISTORICAL_START, (rs, number) -> {
                var verified = verifiedRow(rs, owner, intentId, deadline, null, false);
                return new HistoricalStartReceiptVO(verified.action(), verified.dispatchRun(), rs.getLong("invocation_revision"),
                        time(rs, "dispatch_not_after"), rs.getString("dispatch_parameters_sha256"),
                        rs.getString("action_stamp"), rs.getString("invocation_stamp"), rs.getString("origin_stamp"), rs.getString("dispatch_stamp"));
            }, owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), intentId,
                    dispatchRunId, ACTION_STAMP_BYTES, INVOCATION_STAMP_BYTES, RUN_STAMP_BYTES, RUN_STAMP_BYTES);
            Instant checked = now(deadline);
            if (rows.isEmpty()) return Optional.empty();
            if (rows.size() != 1 || !dispatchRunId.equals(rows.getFirst().dispatchRun().runId())
                    || rows.getFirst().dispatchNotAfter().isAfter(checked)) throw unavailable();
            return Optional.of(rows.getFirst());
        } catch (DataAccessException privateStorage) {
            throw unavailable();
        }
    }

    record HistoricalStartReceiptVO(Row action, AgentRunAuthorization.Request dispatchRun, long invocationRevision,
                                    Instant dispatchNotAfter, String parametersSha256, String actionStamp,
                                    String invocationStamp, String originRunStamp, String dispatchRunStamp) {
        HistoricalStartReceiptVO {
            if (action == null || action.profile() != ActionProfile.LEGACY_V1 || action.dispatch() == null || dispatchRun == null
                    || !Set.of("DISPATCHING", "UNKNOWN", "APPLIED").contains(action.state())
                    || !action.action().owner().equals(dispatchRun.actor()) || !action.dispatch().runId().equals(dispatchRun.runId())
                    || invocationRevision < 1 || dispatchNotAfter == null || !hash(parametersSha256)
                    || !validStamp(actionStamp, ACTION_STAMP_BYTES) || !validStamp(invocationStamp, INVOCATION_STAMP_BYTES)
                    || !validStamp(originRunStamp, RUN_STAMP_BYTES) || !validStamp(dispatchRunStamp, RUN_STAMP_BYTES)) throw unavailable();
        }

        private static boolean validStamp(String stamp, int maximumBytes) {
            return stamp != null && stamp.length() <= maximumBytes && stamp.startsWith("{") && stamp.endsWith("}")
                    && stamp.getBytes(StandardCharsets.UTF_8).length <= maximumBytes;
        }

        @Override public String toString() { return "ToolActionIntentHistoricalStartReceipt[redacted]"; }
    }

    Row lockForApproval(ToolActionRequest action, ToolDescriptor descriptor, Instant deadline) {
        now(deadline); requireApproval(action, descriptor);
        try {
            var locked = read(action.owner(), UUID.fromString(action.intentId()), true, deadline).orElseThrow(ToolActionIntentSql::unavailable);
            same(locked, action); return locked;
        } catch (DataAccessException privateStorage) { throw unavailable(); }
    }

    void beginApprovedWrite(Row expected, ApprovedObservation approval, DispatchVO dispatch, Instant consumedAndDispatchedAt, Instant deadline) {
        Instant checked = now(deadline); requireApproval(expected);
        if (!"AWAITING_APPROVAL".equals(expected.state()) || expected.approval() != null || expected.dispatch() != null || approval == null
                || dispatch == null || dispatch.invocationId() == null || dispatch.epoch() != 1 || dispatch.executor().tenantId() != expected.action().owner().tenantId()) throw invalid();
        if (expected.revision() == Long.MAX_VALUE) throw unavailable();
        writeTime(expected, consumedAndDispatchedAt, checked);
        if (!expected.expiresAt().isAfter(consumedAndDispatchedAt) || !expected.expiresAt().isAfter(checked)) throw invalid();
        var action = expected.action(); var owner = action.owner(); var tool = expected.descriptor();
        approval.binding().requireApproved(action, approval.approval(), consumedAndDispatchedAt);
        approval.binding().requireApproved(action, approval.approval(), checked);
        if (!approval.consumerExpiresAt().isAfter(checked)) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Tool approval consumer is no longer current");
        Instant indexedExpiry = approval.approval().expiresAt().truncatedTo(ChronoUnit.MICROS);
        if (!indexedExpiry.isAfter(consumedAndDispatchedAt) || !indexedExpiry.isAfter(checked)) throw invalid();
        var proof = new ToolExecutionJsonCodec.ApprovalProofVO(1,
                new ToolExecutionJsonCodec.ApprovalSourceVO(approval.sourceId(), approval.tenantId(), approval.sourceIssuer(), approval.sourceResource()), approval.binding(), approval.approval());
        String encoded = codec.approval(proof, action), sha = codec.approvalSha256(proof, action);
        try {
            if (jdbc.update("""
                    UPDATE tianji_agent.agent_tool_action_intent
                    SET state='DISPATCHING',revision=revision+1,approval_source_namespace_sha256=?,approval_receipt_id=?,approval_proof_sha256=?,approval_proof=CAST(? AS jsonb),
                        approval_approved=TRUE,approval_consumed_at=?,approval_expires_at=?,dispatch_epoch=1,dispatch_nonce_sha256=?,dispatch_run_id=?,dispatch_invocation_id=?,
                        executor_issuer=?,executor_id=?,updated_at=?,side_effect_outcome='UNKNOWN'
                    WHERE tenant_id=? AND owner_type=? AND owner_issuer=? AND owner_id=? AND intent_id=?
                      AND ((pg_catalog.to_jsonb(agent_tool_action_intent)->'action_profile' IS NULL
                            AND pg_catalog.to_jsonb(agent_tool_action_intent)->'dispatch_call_id' IS NULL)
                           OR (pg_catalog.to_jsonb(agent_tool_action_intent)->>'action_profile'='LEGACY_V1'
                            AND pg_catalog.to_jsonb(agent_tool_action_intent)->'dispatch_call_id'='null'::jsonb))
                      AND action_key=? AND business_key=? AND business_version=? AND request_sha256=? AND logical_identity_sha256=?
                      AND tool_key=? AND tool_version=? AND tool_sha256=? AND binding_sha256=? AND risk='APPROVAL_REQUIRED' AND approval_policy_ref=?
                      AND state='AWAITING_APPROVAL' AND revision=? AND dispatch_epoch=0
                      AND dispatch_nonce_sha256 IS NULL AND dispatch_run_id IS NULL AND dispatch_invocation_id IS NULL AND executor_issuer IS NULL AND executor_id IS NULL
                      AND approval_source_namespace_sha256 IS NULL AND approval_receipt_id IS NULL AND approval_proof_sha256 IS NULL AND approval_proof IS NULL
                      AND approval_approved IS NULL AND approval_consumed_at IS NULL AND approval_expires_at IS NULL
                      AND side_effect_outcome='NONE' AND result IS NULL AND business_receipt_id IS NULL AND last_error_code IS NULL AND closed_at IS NULL
                      AND origin_run_id=? AND origin_snapshot_sha256=? AND created_at=? AND updated_at=? AND expires_at=? AND expires_at>clock_timestamp()
                      AND ?>clock_timestamp() AND ?>clock_timestamp()
                    """, approval.sourceNamespaceSha256(), approval.approval().approvalReceiptId(), sha, encoded, Timestamp.from(consumedAndDispatchedAt), Timestamp.from(indexedExpiry),
                    dispatch.nonceSha256(), dispatch.runId(), dispatch.invocationId(), dispatch.executor().issuerFingerprint(), dispatch.executor().subjectId(), Timestamp.from(consumedAndDispatchedAt),
                    owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), UUID.fromString(action.intentId()),
                    action.actionKey(), action.businessKey(), action.businessVersion(), action.requestSha256(), logicalIdentity(action), tool.key(), tool.version(), tool.reference().contentSha256(), tool.policy().bindingSha256(), tool.policy().approvalPolicyRef(),
                    expected.revision(), expected.originRunId(), expected.originSnapshotSha256(), Timestamp.from(expected.createdAt()), Timestamp.from(expected.updatedAt()), Timestamp.from(expected.expiresAt()),
                    Timestamp.from(indexedExpiry), Timestamp.from(approval.consumerExpiresAt().truncatedTo(ChronoUnit.MICROS))) != 1) throw conflict();
            Instant completed = now(deadline);
            if (!indexedExpiry.isAfter(completed) || !approval.consumerExpiresAt().isAfter(completed)) throw unavailable();
        } catch (DuplicateKeyException privateDuplicate) { throw conflict(); }
        catch (DataAccessException privateStorage) { throw unavailable(); }
    }

    void completeApprovedWrite(Row expected, ToolExecutionOutcome outcome, Instant finishedAt, Instant deadline) {
        Instant checked = now(deadline); requireApproval(expected);
        if (!Set.of("DISPATCHING", "UNKNOWN").contains(expected.state()) || expected.dispatch() == null || expected.approval() == null || outcome == null) throw invalid();
        if (expected.revision() == Long.MAX_VALUE) throw unavailable();
        writeTime(expected, finishedAt, checked); outcome = outcome.validated(expected.descriptor());
        String effect = switch (outcome.status()) {
            case SUCCEEDED -> "CONFIRMED";
            case REJECTED, FAILED_KNOWN -> "NONE";
            case UNKNOWN -> "UNKNOWN";
            default -> throw invalid();
        };
        String state = outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED ? "APPLIED" : outcome.status().name();
        var action = expected.action(); var owner = action.owner(); var tool = expected.descriptor(); var dispatch = expected.dispatch(); var approval = expected.approval(); var proof = approval.proof();
        if (!codec.approvalSha256(proof, action).equals(approval.proofSha256())) throw invalid();
        try {
            if (jdbc.update("""
                    UPDATE tianji_agent.agent_tool_action_intent
                    SET state=?,side_effect_outcome=?,result=CAST(? AS jsonb),business_receipt_id=?,last_error_code=?,closed_at=?,updated_at=?,revision=revision+1
                    WHERE tenant_id=? AND owner_type=? AND owner_issuer=? AND owner_id=? AND intent_id=?
                      AND ((pg_catalog.to_jsonb(agent_tool_action_intent)->'action_profile' IS NULL
                            AND pg_catalog.to_jsonb(agent_tool_action_intent)->'dispatch_call_id' IS NULL)
                           OR (pg_catalog.to_jsonb(agent_tool_action_intent)->>'action_profile'='LEGACY_V1'
                            AND pg_catalog.to_jsonb(agent_tool_action_intent)->'dispatch_call_id'='null'::jsonb))
                      AND action_key=? AND business_key=? AND business_version=? AND request_sha256=? AND logical_identity_sha256=?
                      AND tool_key=? AND tool_version=? AND tool_sha256=? AND binding_sha256=? AND risk='APPROVAL_REQUIRED' AND approval_policy_ref=?
                      AND state=? AND revision=? AND dispatch_epoch=? AND dispatch_nonce_sha256=? AND dispatch_run_id=? AND dispatch_invocation_id=? AND executor_issuer=? AND executor_id=?
                      AND approval_source_namespace_sha256=? AND approval_receipt_id=? AND approval_proof_sha256=? AND approval_proof=CAST(? AS jsonb)
                      AND approval_approved IS TRUE AND approval_consumed_at=? AND approval_expires_at=?
                      AND origin_run_id=? AND origin_snapshot_sha256=? AND created_at=? AND updated_at=? AND expires_at=?
                    """, state, effect, codec.outcome(tool, outcome), outcome.businessReceiptId(), outcome.errorCode(), Timestamp.from(finishedAt), Timestamp.from(finishedAt),
                    owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), UUID.fromString(action.intentId()),
                    action.actionKey(), action.businessKey(), action.businessVersion(), action.requestSha256(), logicalIdentity(action), tool.key(), tool.version(), tool.reference().contentSha256(), tool.policy().bindingSha256(), tool.policy().approvalPolicyRef(),
                    expected.state(), expected.revision(), dispatch.epoch(), dispatch.nonceSha256(), dispatch.runId(), dispatch.invocationId(), dispatch.executor().issuerFingerprint(), dispatch.executor().subjectId(),
                    proof.source().namespaceSha256(), proof.approval().approvalReceiptId(), approval.proofSha256(), codec.approval(proof, action), Timestamp.from(approval.consumedAt()), Timestamp.from(proof.approval().expiresAt().truncatedTo(ChronoUnit.MICROS)),
                    expected.originRunId(), expected.originSnapshotSha256(), Timestamp.from(expected.createdAt()), Timestamp.from(expected.updatedAt()), Timestamp.from(expected.expiresAt())) != 1) throw conflict();
            now(deadline);
        } catch (DataAccessException privateStorage) { throw unavailable(); }
    }

    private static void requireApproval(Row expected) {
        if (expected == null || expected.profile() != ActionProfile.LEGACY_V1) throw invalid(); requireApproval(expected.action(), expected.descriptor());
    }
    private static void requireApproval(ToolActionRequest action, ToolDescriptor descriptor) {
        if (action == null || descriptor == null || descriptor.policy().risk() != ToolPolicy.Risk.APPROVAL_REQUIRED
                || action.policy().risk() != ToolPolicy.Risk.APPROVAL_REQUIRED || !descriptor.reference().equals(action.tool())) throw invalid();
        sameAction(new ToolActionRequest(action.owner(), descriptor, action.businessKey(), action.businessVersion(), action.arguments()), action);
    }

    Row lockForWrite(ToolActionRequest action, ToolDescriptor descriptor, Instant deadline) {
        now(deadline); requireIdempotentWrite(action, descriptor);
        try {
            var locked = read(action.owner(), UUID.fromString(action.intentId()), true, deadline).orElseThrow(ToolActionIntentSql::unavailable);
            same(locked, action); return locked;
        } catch (DataAccessException privateStorage) { throw unavailable(); }
    }

    void beginWrite(Row expected, DispatchVO dispatch, Instant dispatchedAt, Instant deadline) {
        Instant checked = now(deadline); requireIdempotentWrite(expected);
        if (!"PREPARED".equals(expected.state()) || expected.dispatch() != null || expected.approval() != null
                || dispatch == null || dispatch.invocationId() == null || dispatch.epoch() != 1 || dispatch.executor().tenantId() != expected.action().owner().tenantId()) throw invalid();
        if (expected.revision() == Long.MAX_VALUE) throw unavailable();
        writeTime(expected, dispatchedAt, checked);
        if (!expected.expiresAt().isAfter(dispatchedAt) || !expected.expiresAt().isAfter(checked)) throw invalid();
        var action = expected.action(); var owner = action.owner(); var tool = expected.descriptor();
        try {
            if (jdbc.update("""
                    UPDATE tianji_agent.agent_tool_action_intent
                    SET state='DISPATCHING',revision=revision+1,dispatch_epoch=1,dispatch_nonce_sha256=?,dispatch_run_id=?,dispatch_invocation_id=?,
                        executor_issuer=?,executor_id=?,updated_at=?,side_effect_outcome='UNKNOWN'
                    WHERE tenant_id=? AND owner_type=? AND owner_issuer=? AND owner_id=? AND intent_id=?
                      AND ((pg_catalog.to_jsonb(agent_tool_action_intent)->'action_profile' IS NULL
                            AND pg_catalog.to_jsonb(agent_tool_action_intent)->'dispatch_call_id' IS NULL)
                           OR (pg_catalog.to_jsonb(agent_tool_action_intent)->>'action_profile'='LEGACY_V1'
                            AND pg_catalog.to_jsonb(agent_tool_action_intent)->'dispatch_call_id'='null'::jsonb))
                      AND action_key=? AND business_key=? AND business_version=? AND request_sha256=? AND logical_identity_sha256=?
                      AND tool_key=? AND tool_version=? AND tool_sha256=? AND binding_sha256=? AND risk='IDEMPOTENT_WRITE'
                      AND revision=? AND state='PREPARED' AND dispatch_epoch=0
                      AND dispatch_nonce_sha256 IS NULL AND dispatch_run_id IS NULL AND dispatch_invocation_id IS NULL AND executor_issuer IS NULL AND executor_id IS NULL
                      AND approval_policy_ref IS NULL AND approval_source_namespace_sha256 IS NULL AND approval_receipt_id IS NULL AND approval_proof_sha256 IS NULL
                      AND approval_proof IS NULL AND approval_approved IS NULL AND approval_consumed_at IS NULL AND approval_expires_at IS NULL
                      AND side_effect_outcome='NONE' AND result IS NULL AND business_receipt_id IS NULL AND last_error_code IS NULL AND closed_at IS NULL
                      AND origin_run_id=? AND origin_snapshot_sha256=? AND created_at=? AND updated_at=? AND expires_at=? AND expires_at>clock_timestamp()
                    """, dispatch.nonceSha256(), dispatch.runId(), dispatch.invocationId(), dispatch.executor().issuerFingerprint(), dispatch.executor().subjectId(), Timestamp.from(dispatchedAt),
                    owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), UUID.fromString(action.intentId()),
                    action.actionKey(), action.businessKey(), action.businessVersion(), action.requestSha256(), logicalIdentity(action), tool.key(), tool.version(), tool.reference().contentSha256(), tool.policy().bindingSha256(),
                    expected.revision(), expected.originRunId(), expected.originSnapshotSha256(), Timestamp.from(expected.createdAt()), Timestamp.from(expected.updatedAt()), Timestamp.from(expected.expiresAt())) != 1) throw conflict();
            // The invocation is updated first by the owner; its matching action JOIN is valid only after both writes.
            now(deadline);
        } catch (DataAccessException privateStorage) { throw unavailable(); }
    }

    void completeWrite(Row expected, ToolExecutionOutcome outcome, Instant finishedAt, Instant deadline) {
        Instant checked = now(deadline); requireIdempotentWrite(expected);
        if (!Set.of("DISPATCHING", "UNKNOWN").contains(expected.state()) || expected.dispatch() == null || expected.approval() != null || outcome == null) throw invalid();
        if (expected.revision() == Long.MAX_VALUE) throw unavailable();
        writeTime(expected, finishedAt, checked); outcome = outcome.validated(expected.descriptor());
        String effect = switch (outcome.status()) {
            case SUCCEEDED -> "CONFIRMED";
            case REJECTED, FAILED_KNOWN -> "NONE";
            case UNKNOWN -> "UNKNOWN";
            default -> throw invalid();
        };
        String state = outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED ? "APPLIED" : outcome.status().name();
        var action = expected.action(); var owner = action.owner(); var tool = expected.descriptor(); var dispatch = expected.dispatch();
        try {
            if (jdbc.update("""
                    UPDATE tianji_agent.agent_tool_action_intent
                    SET state=?,side_effect_outcome=?,result=CAST(? AS jsonb),business_receipt_id=?,last_error_code=?,closed_at=?,updated_at=?,revision=revision+1
                    WHERE tenant_id=? AND owner_type=? AND owner_issuer=? AND owner_id=? AND intent_id=?
                      AND ((pg_catalog.to_jsonb(agent_tool_action_intent)->'action_profile' IS NULL
                            AND pg_catalog.to_jsonb(agent_tool_action_intent)->'dispatch_call_id' IS NULL)
                           OR (pg_catalog.to_jsonb(agent_tool_action_intent)->>'action_profile'='LEGACY_V1'
                            AND pg_catalog.to_jsonb(agent_tool_action_intent)->'dispatch_call_id'='null'::jsonb))
                      AND action_key=? AND business_key=? AND business_version=? AND request_sha256=? AND logical_identity_sha256=?
                      AND tool_key=? AND tool_version=? AND tool_sha256=? AND binding_sha256=? AND risk='IDEMPOTENT_WRITE'
                      AND state=? AND revision=? AND dispatch_epoch=? AND dispatch_nonce_sha256=? AND dispatch_run_id=? AND dispatch_invocation_id=? AND executor_issuer=? AND executor_id=?
                      AND approval_policy_ref IS NULL AND approval_source_namespace_sha256 IS NULL AND approval_receipt_id IS NULL AND approval_proof_sha256 IS NULL
                      AND approval_proof IS NULL AND approval_approved IS NULL AND approval_consumed_at IS NULL AND approval_expires_at IS NULL
                      AND origin_run_id=? AND origin_snapshot_sha256=? AND created_at=? AND updated_at=? AND expires_at=?
                    """, state, effect, codec.outcome(tool, outcome), outcome.businessReceiptId(), outcome.errorCode(), Timestamp.from(finishedAt), Timestamp.from(finishedAt),
                    owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), UUID.fromString(action.intentId()),
                    action.actionKey(), action.businessKey(), action.businessVersion(), action.requestSha256(), logicalIdentity(action), tool.key(), tool.version(), tool.reference().contentSha256(), tool.policy().bindingSha256(),
                    expected.state(), expected.revision(), dispatch.epoch(), dispatch.nonceSha256(), dispatch.runId(), dispatch.invocationId(), dispatch.executor().issuerFingerprint(), dispatch.executor().subjectId(),
                    expected.originRunId(), expected.originSnapshotSha256(), Timestamp.from(expected.createdAt()), Timestamp.from(expected.updatedAt()), Timestamp.from(expected.expiresAt())) != 1) throw conflict();
            now(deadline);
        } catch (DataAccessException privateStorage) { throw unavailable(); }
    }

    private static void requireIdempotentWrite(Row expected) {
        if (expected == null || expected.profile() != ActionProfile.LEGACY_V1) throw invalid(); requireIdempotentWrite(expected.action(), expected.descriptor());
    }
    private static void requireIdempotentWrite(ToolActionRequest action, ToolDescriptor descriptor) {
        if (action == null || descriptor == null || descriptor.policy().risk() != ToolPolicy.Risk.IDEMPOTENT_WRITE
                || action.policy().risk() != ToolPolicy.Risk.IDEMPOTENT_WRITE || !descriptor.reference().equals(action.tool())) throw invalid();
        sameAction(new ToolActionRequest(action.owner(), descriptor, action.businessKey(), action.businessVersion(), action.arguments()), action);
    }
    private static void writeTime(Row expected, Instant writtenAt, Instant databaseNow) {
        if (writtenAt == null || writtenAt.getNano() % 1_000 != 0 || writtenAt.isAfter(databaseNow)
                || writtenAt.isBefore(expected.createdAt()) || writtenAt.isBefore(expected.updatedAt())) throw invalid();
    }

    private Optional<Row> read(TrustedAgentActor owner, UUID id, boolean lock, Instant deadline) {
        return read(owner, id, lock, deadline, null, false);
    }

    private Optional<Row> read(TrustedAgentActor owner, UUID id, boolean lock, Instant deadline, SchemaProfile schema, boolean allowRuntime) {
        now(deadline);
        if (lock) {
            var locked = jdbc.query("SELECT intent_id FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND owner_type=? AND owner_issuer=? AND owner_id=? AND intent_id=? FOR UPDATE",
                    (rs, number) -> rs.getObject("intent_id", UUID.class), owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), id);
            now(deadline); if (locked.size() > 1) throw unavailable(); if (locked.isEmpty()) return Optional.empty();
            if (!id.equals(locked.getFirst())) throw unavailable();
        }
        var rows = jdbc.query(allowRuntime ? RUNTIME_OWNER_ROW_READ : OWNER_ROW_READ,
                (rs, number) -> verifiedRow(rs, owner, id, deadline, schema, allowRuntime).action(),
                owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), id);
        now(deadline); if (rows.size() > 1) throw unavailable();
        if (lock && rows.isEmpty()) throw unavailable();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private void requireRun(AgentRunAuthorization.Request run, AgentRunLease lease, ToolDescriptor descriptor, Instant deadline) {
        now(deadline);
        var expiries = jdbc.query("""
                SELECT lease_expires_at FROM tianji_agent.agent_run WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND run_id=?
                  AND status='RUNNING' AND lease_owner=? AND lease_epoch=? AND lease_expires_at>clock_timestamp() AND deadline_at>clock_timestamp()
                  AND cancel_requested_at IS NULL FOR SHARE
                """, (rs, number) -> rs.getTimestamp("lease_expires_at").toInstant(), run.actor().tenantId(), run.actor().subjectType().name(), run.actor().issuerFingerprint(), run.actor().subjectId(), run.runId(), lease.owner(), lease.epoch());
        Instant checked = now(deadline);
        if (expiries.size() != 1 || !expiries.getFirst().isAfter(checked) || !run.deadlineAt().isAfter(checked)) throw leaseLost();
        AgentLedgerService.ExecutionMaterial material;
        try { material = ledger.executionMaterial(run.actor(), new GetAgentRunQuery(run.runId())); }
        catch (RuntimeException malformedSource) { throw unavailable(); }
        if (!run.equals(material.authorization())) throw unavailable();
        if (!material.snapshot().definition().dependencies().tools().contains(descriptor.reference())
                || !material.snapshot().effectivePolicy().allowedToolVersions().contains(descriptor.versionKey()) || material.snapshot().effectivePolicy().maxToolCalls() < 1
                || descriptor.policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED && !Objects.equals(descriptor.policy().approvalPolicyRef(), material.snapshot().effectivePolicy().approvalPolicyRef())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Tool is not allowed by the fixed run policy");
        }
        // The WHERE predicate may have been evaluated before a lock wait. The captured DB value, not the claim hint, is authoritative.
        if (!expiries.getFirst().isAfter(now(deadline))) throw leaseLost();
    }

    private Instant now(Instant deadline) {
        requireTransaction();
        if (Thread.currentThread().isInterrupted() || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
        Timestamp time = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class);
        if (time == null || deadline == null || !deadline.isAfter(time.toInstant())) throw unavailable();
        return time.toInstant();
    }
    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) throw unavailable();
    }

    private record VerifiedRow(Row action, AgentRunAuthorization.Request dispatchRun) {
        @Override public String toString() { return "ToolActionIntentVerifiedRow[redacted]"; }
    }

    private VerifiedRow verifiedRow(ResultSet rs, TrustedAgentActor expectedOwner, UUID expectedId, Instant deadline,
                                    SchemaProfile schema, boolean allowRuntime) throws SQLException {
        try {
            var owner = new TrustedAgentActor(rs.getLong("tenant_id"), TrustedAgentActor.SubjectType.valueOf(rs.getString("owner_type")), rs.getString("owner_id"), rs.getString("owner_issuer"));
            if (!owner.equals(expectedOwner) || !expectedId.equals(rs.getObject("intent_id", UUID.class))) throw unavailable();
            var reference = new DefinitionResourceRef(rs.getString("tool_key"), rs.getLong("tool_version"), rs.getString("tool_sha256"));
            var descriptor = codec.descriptor(rs.getString("descriptor"), reference);
            var action = new ToolActionRequest(owner, descriptor, rs.getString("business_key"), rs.getString("business_version"), codec.arguments(rs.getString("arguments"), descriptor));
            if (!action.intentId().equals(rs.getObject("intent_id", UUID.class).toString()) || !logicalIdentity(action).equals(rs.getString("logical_identity_sha256"))
                    || !action.requestSha256().equals(rs.getString("request_sha256")) || !action.actionKey().equals(rs.getString("action_key"))
                    || !descriptor.policy().bindingSha256().equals(rs.getString("binding_sha256")) || !descriptor.policy().risk().name().equals(rs.getString("risk"))
                    || !Objects.equals(descriptor.policy().approvalPolicyRef(), rs.getString("approval_policy_ref"))) throw unavailable();
            String storedProfile = rs.getString("stored_action_profile"), storedCallId = rs.getString("stored_dispatch_call_id");
            var profile = actionProfile(storedProfile, storedCallId, schema);
            if (profile == ActionProfile.RUNTIME_V2 && !allowRuntime) throw unavailable();
            Instant created = time(rs, "created_at");
            var approval = approval(rs, action, created);
            var dispatch = dispatch(rs, owner, dispatchCallId(storedCallId));
            String encoded = rs.getString("result"); var outcome = encoded == null ? null : codec.outcome(profile, encoded, descriptor);
            if (!Objects.equals(rs.getString("business_receipt_id"), outcome == null ? null : outcome.businessReceiptId())
                    || outcome != null && !Objects.equals(rs.getString("last_error_code"), outcome.errorCode())) throw unavailable();
            var origin = new OriginVO(rs.getString("origin_run_id"), rs.getString("origin_snapshot_sha256"));
            var stored = new Row(action, descriptor, origin, profile, rs.getString("state"), rs.getLong("revision"),
                    created, time(rs, "updated_at"), time(rs, "expires_at"), approval, dispatch, rs.getString("side_effect_outcome"), outcome, rs.getString("last_error_code"), time(rs, "closed_at"));
            if (!Boolean.TRUE.equals(rs.getObject("origin_owner_matches", Boolean.class))
                    || !Boolean.TRUE.equals(rs.getObject("dispatch_owner_matches", Boolean.class))) throw unavailable();
            if (profile == ActionProfile.LEGACY_V1) {
                if (allowRuntime && (rs.getString("origin_runtime_snapshot_sha256") != null
                        || rs.getString("dispatch_runtime_snapshot_sha256") != null)) throw unavailable();
                verifyLegacyDispatch(rs, stored);
            } else verifyRuntimeDispatch(rs, stored);
            requireHistoricalSource(owner, stored.originRunId(), descriptor, stored.originSnapshotSha256(), deadline);
            String dispatchSnapshot = profile == ActionProfile.RUNTIME_V2 ? rs.getString("dispatch_runtime_snapshot_sha256") : null;
            var dispatchRun = dispatch == null ? null : requireHistoricalSource(owner, dispatch.runId(), descriptor, dispatchSnapshot, deadline);
            return new VerifiedRow(stored, dispatchRun);
        } catch (RuntimeException malformed) { throw unavailable(); }
    }

    private static ActionProfile actionProfile(String profile, String callId, SchemaProfile schema) {
        if (profile == null && callId == null && schema != SchemaProfile.V17) return ActionProfile.LEGACY_V1;
        if (profile == null || callId == null || schema == SchemaProfile.LEGACY) throw unavailable();
        return switch (profile) {
            case "\"LEGACY_V1\"" -> ActionProfile.LEGACY_V1;
            case "\"RUNTIME_V2\"" -> ActionProfile.RUNTIME_V2;
            default -> throw unavailable();
        };
    }

    private static UUID dispatchCallId(String encoded) {
        if (encoded == null || "null".equals(encoded)) return null;
        if (encoded.length() != 38 || encoded.charAt(0) != '"' || encoded.charAt(37) != '"') throw unavailable();
        String value = encoded.substring(1, 37);
        var id = UUID.fromString(value);
        if (!value.equals(id.toString())) throw unavailable();
        return id;
    }

    private static void verifyLegacyDispatch(ResultSet rs, Row row) throws SQLException {
        if (!Boolean.TRUE.equals(rs.getObject("dispatch_identity_matches", Boolean.class))
                || !Boolean.TRUE.equals(rs.getObject("dispatch_fact_matches", Boolean.class))
                || row.dispatch() != null && !row.descriptor().parametersSha256(row.action().arguments()).equals(rs.getString("dispatch_parameters_sha256"))) {
            throw unavailable();
        }
    }

    private static void verifyRuntimeDispatch(ResultSet rs, Row row) throws SQLException {
        if (!row.originSnapshotSha256().equals(rs.getString("origin_runtime_snapshot_sha256"))) throw unavailable();
        if (row.dispatch() == null) return;
        if (!hash(rs.getString("dispatch_runtime_snapshot_sha256"))
                || !Boolean.TRUE.equals(rs.getObject("runtime_dispatch_matches", Boolean.class))
                || !row.descriptor().parametersSha256(row.action().arguments()).equals(rs.getString("runtime_input_sha256"))) throw unavailable();
        String state = switch (row.state()) {
            case "DISPATCHING" -> "DISPATCHED";
            case "APPLIED" -> "SUCCEEDED";
            case "REJECTED", "FAILED_KNOWN" -> "FAILED";
            case "UNKNOWN" -> "UNKNOWN";
            default -> throw unavailable();
        };
        String result = rs.getString("runtime_result_sha256");
        if (!state.equals(rs.getString("runtime_call_state")) || !Objects.equals(row.lastErrorCode(), rs.getString("runtime_error_code"))
                || "SUCCEEDED".equals(state) && !hash(result) || "DISPATCHED".equals(state) && result != null
                || result != null && !hash(result)) throw unavailable();
    }

    private ApprovalConsumptionVO approval(ResultSet rs, ToolActionRequest action, Instant created) throws SQLException {
        String namespace = rs.getString("approval_source_namespace_sha256"), receipt = rs.getString("approval_receipt_id"), sha = rs.getString("approval_proof_sha256"), encoded = rs.getString("approval_proof");
        Boolean approved = rs.getObject("approval_approved", Boolean.class); Instant consumed = time(rs, "approval_consumed_at"), expires = time(rs, "approval_expires_at");
        if (namespace == null && receipt == null && sha == null && encoded == null && approved == null && consumed == null && expires == null) return null;
        if (action.policy().risk() != ToolPolicy.Risk.APPROVAL_REQUIRED || namespace == null || receipt == null || sha == null || encoded == null
                || !Boolean.TRUE.equals(approved) || consumed == null || expires == null || created == null || consumed.isBefore(created)) throw unavailable();
        var proof = codec.approval(encoded, sha, action);
        if (!namespace.equals(proof.source().namespaceSha256()) || !receipt.equals(proof.approval().approvalReceiptId())
                || !expires.equals(proof.approval().expiresAt().truncatedTo(ChronoUnit.MICROS)) || !expires.isAfter(consumed)) throw unavailable();
        return new ApprovalConsumptionVO(proof, sha, consumed);
    }

    private static DispatchVO dispatch(ResultSet rs, TrustedAgentActor owner, UUID callId) throws SQLException {
        Long epoch = rs.getObject("dispatch_epoch", Long.class); String run = rs.getString("dispatch_run_id"), nonce = rs.getString("dispatch_nonce_sha256");
        UUID invocation = rs.getObject("dispatch_invocation_id", UUID.class); String issuer = rs.getString("executor_issuer"), id = rs.getString("executor_id");
        if (epoch == null || epoch < 0) throw unavailable();
        if (epoch == 0) {
            if (run != null || nonce != null || invocation != null || callId != null || issuer != null || id != null) throw unavailable(); return null;
        }
        if (run == null || nonce == null || (invocation == null) == (callId == null) || !hash(issuer) || id == null) throw unavailable();
        return new DispatchVO(run, invocation, callId, epoch, nonce, new TrustedAgentActor(owner.tenantId(), TrustedAgentActor.SubjectType.SERVICE, id, issuer));
    }

    private AgentRunAuthorization.Request requireHistoricalSource(TrustedAgentActor owner, String id, ToolDescriptor descriptor,
                                                                   String snapshotSha256, Instant deadline) {
        now(deadline);
        AgentLedgerService.ExecutionMaterial material;
        try { material = ledger.executionMaterial(owner, new GetAgentRunQuery(id)); }
        catch (RuntimeException malformedSource) { throw unavailable(); }
        if (material == null || material.authorization() == null || material.run() == null || material.snapshot() == null) throw unavailable();
        var run = material.run(); var authorization = material.authorization(); var snapshot = material.snapshot();
        if (!owner.equals(authorization.actor()) || !id.equals(run.runId()) || !id.equals(authorization.runId())
                || !run.definitionKey().equals(authorization.definitionKey()) || run.definitionVersion() != authorization.definitionVersion()
                || !run.deadlineAt().equals(authorization.deadlineAt()) || authorization.sessionRequired() != (run.sessionId() != null)
                || !run.definitionKey().equals(snapshot.definition().definitionKey()) || run.definitionVersion() != snapshot.definition().version()
                || !authorization.definitionSha256().equals(snapshot.definitionContentSha256())
                || snapshotSha256 != null && !snapshotSha256.equals(authorization.snapshotSha256())
                || !authorization.policyVersionRef().equals(snapshot.definition().dependencies().policyVersionRef())
                || !snapshot.definition().dependencies().tools().contains(descriptor.reference()) || !snapshot.effectivePolicy().allowedToolVersions().contains(descriptor.versionKey())
                || snapshot.effectivePolicy().maxToolCalls() < 1 || descriptor.policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED
                && !Objects.equals(descriptor.policy().approvalPolicyRef(), snapshot.effectivePolicy().approvalPolicyRef())) throw unavailable();
        now(deadline);
        return authorization;
    }

    private static void binding(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action, ToolDescriptor descriptor) {
        if (run == null || lease == null || action == null || descriptor == null || !run.actor().equals(action.owner())
                || !run.runId().equals(lease.runId()) || !descriptor.reference().equals(action.tool())) throw invalid();
        sameAction(new ToolActionRequest(run.actor(), descriptor, action.businessKey(), action.businessVersion(), action.arguments()), action);
    }
    private static void same(Row row, ToolActionRequest action) { sameAction(row.action(), action); }
    private static void sameAction(ToolActionRequest stored, ToolActionRequest action) {
        if (!stored.owner().equals(action.owner()) || !stored.intentId().equals(action.intentId()) || !stored.requestSha256().equals(action.requestSha256())
                || !stored.actionKey().equals(action.actionKey()) || !stored.businessKey().equals(action.businessKey()) || !stored.businessVersion().equals(action.businessVersion())
                || !stored.tool().equals(action.tool()) || !stored.policy().equals(action.policy()) || !stored.arguments().equals(action.arguments())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Tool action intent was already prepared with different inputs");
        }
    }
    private static String logicalIdentity(ToolActionRequest action) {
        return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_ACTION_LOGICAL_IDENTITY_V1", action.owner().identityFingerprint(), action.actionKey(), action.businessKey(), action.businessVersion()), Map.of());
    }

    record OriginVO(String runId, String snapshotSha256) {
        OriginVO { if (!ToolActionIntentSql.runId(runId) || !hash(snapshotSha256)) throw invalid(); }
        @Override public String toString() { return "ToolActionIntentOrigin[redacted]"; }
    }

    record Row(ToolActionRequest action, ToolDescriptor descriptor, OriginVO origin, ActionProfile profile, String state, long revision,
               Instant createdAt, Instant updatedAt, Instant expiresAt, ApprovalConsumptionVO approval, DispatchVO dispatch,
               String sideEffectOutcome, ToolExecutionOutcome outcome, String lastErrorCode, Instant closedAt) {
        Row(ToolActionRequest action, ToolDescriptor descriptor, String originRunId, String originSnapshotSha256, String state, long revision,
            Instant createdAt, Instant updatedAt, Instant expiresAt, ApprovalConsumptionVO approval, DispatchVO dispatch,
            String sideEffectOutcome, ToolExecutionOutcome outcome, String lastErrorCode, Instant closedAt) {
            this(action, descriptor, new OriginVO(originRunId, originSnapshotSha256), ActionProfile.LEGACY_V1, state, revision,
                    createdAt, updatedAt, expiresAt, approval, dispatch, sideEffectOutcome, outcome, lastErrorCode, closedAt);
        }
        String originRunId() { return origin.runId(); }
        String originSnapshotSha256() { return origin.snapshotSha256(); }
        Row {
            if (action == null || descriptor == null || !descriptor.reference().equals(action.tool()) || !descriptor.policy().equals(action.policy())
                    || origin == null || profile == null || state == null || !STATES.contains(state) || revision < 1
                    || createdAt == null || updatedAt == null || expiresAt == null || updatedAt.isBefore(createdAt)
                    || !expiresAt.isAfter(createdAt) || expiresAt.isAfter(createdAt.plus(Duration.ofDays(7)))
                    || closedAt != null && (closedAt.isBefore(createdAt) || updatedAt.isBefore(closedAt))
                    || lastErrorCode != null && !lastErrorCode.matches("[A-Z][A-Z0-9_]{0,63}")) throw invalid();
            var risk = descriptor.policy().risk();
            if (approval != null && (risk != ToolPolicy.Risk.APPROVAL_REQUIRED || approval.consumedAt().isBefore(createdAt))
                    || dispatch != null && (dispatch.executor().tenantId() != action.owner().tenantId() || risk == ToolPolicy.Risk.APPROVAL_REQUIRED && approval == null
                    || (profile == ActionProfile.LEGACY_V1) != (dispatch.invocationId() != null))) throw invalid();
            if (approval != null) approval.proof().binding().requireApproved(action, approval.proof().approval(), approval.proof().approval().verifiedAt());
            if (outcome != null) {
                outcome = outcome.validated(descriptor);
                if (profile == ActionProfile.RUNTIME_V2 && outcome.actualCost() != null) throw invalid();
                if (!(state.equals("APPLIED") ? "SUCCEEDED" : state).equals(outcome.status().name()) || !Objects.equals(lastErrorCode, outcome.errorCode())) throw invalid();
            }
            switch (state) {
                case "PREPARED" -> {
                    if (dispatch != null || outcome != null || lastErrorCode != null || closedAt != null || !"NONE".equals(sideEffectOutcome)
                            || risk == ToolPolicy.Risk.APPROVAL_REQUIRED && approval == null) throw invalid();
                }
                case "AWAITING_APPROVAL" -> {
                    if (risk != ToolPolicy.Risk.APPROVAL_REQUIRED || approval != null || dispatch != null || outcome != null || lastErrorCode != null
                            || closedAt != null || !"NONE".equals(sideEffectOutcome)) throw invalid();
                }
                case "DISPATCHING" -> {
                    if (dispatch == null || outcome != null || lastErrorCode != null || closedAt != null || !"UNKNOWN".equals(sideEffectOutcome)) throw invalid();
                }
                case "APPLIED" -> {
                    if (dispatch == null || outcome == null || outcome.businessReceiptId() == null || lastErrorCode != null || closedAt == null || !"CONFIRMED".equals(sideEffectOutcome)) throw invalid();
                }
                case "REJECTED", "FAILED_KNOWN" -> {
                    if (outcome == null || outcome.businessReceiptId() != null || lastErrorCode == null || closedAt == null || !"NONE".equals(sideEffectOutcome)) throw invalid();
                }
                case "UNKNOWN" -> {
                    if (dispatch == null || outcome == null || outcome.businessReceiptId() != null || lastErrorCode == null || closedAt == null || !"UNKNOWN".equals(sideEffectOutcome)) throw invalid();
                }
                case "EXPIRED" -> {
                    if (dispatch != null || outcome != null || !"INTENT_EXPIRED".equals(lastErrorCode) || closedAt == null || !"NONE".equals(sideEffectOutcome)) throw invalid();
                }
                default -> throw invalid();
            }
        }
        @Override public String toString() { return "ToolActionIntentRow[redacted]"; }
    }
    record ApprovalConsumptionVO(ToolExecutionJsonCodec.ApprovalProofVO proof, String proofSha256, Instant consumedAt) {
        ApprovalConsumptionVO {
            if (proof == null || !hash(proofSha256) || consumedAt == null || !proof.approval().approved()
                    || proof.approval().verifiedAt().isAfter(consumedAt) || !proof.approval().expiresAt().isAfter(consumedAt)) throw invalid();
        }
        @Override public String toString() { return "ToolActionIntentApprovalConsumption[redacted]"; }
    }
    record DispatchVO(String runId, UUID invocationId, UUID callId, long epoch, String nonceSha256, TrustedAgentActor executor) {
        DispatchVO(String runId, UUID invocationId, long epoch, String nonceSha256, TrustedAgentActor executor) {
            this(runId, invocationId, null, epoch, nonceSha256, executor);
        }
        DispatchVO {
            if (!ToolActionIntentSql.runId(runId) || (invocationId == null) == (callId == null) || epoch < 1 || !hash(nonceSha256) || executor == null
                    || executor.subjectType() != TrustedAgentActor.SubjectType.SERVICE || !hash(executor.issuerFingerprint())) throw invalid();
        }
        @Override public String toString() { return "ToolActionIntentDispatch[redacted]"; }
    }
    private static Instant time(ResultSet rs, String name) throws SQLException { var value = rs.getTimestamp(name); return value == null ? null : value.toInstant(); }
    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean runId(String value) { return value != null && value.length() <= 64 && !value.isBlank() && value.equals(value.trim()) && value.chars().noneMatch(Character::isISOControl); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool action source or binding is invalid"); }
    private static AgentLedgerException conflict() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Tool action write identity, revision or dispatch changed"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Tool action source or storage is unavailable"); }
    private static AgentLedgerException leaseLost() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Tool action run lease is no longer valid"); }
}
