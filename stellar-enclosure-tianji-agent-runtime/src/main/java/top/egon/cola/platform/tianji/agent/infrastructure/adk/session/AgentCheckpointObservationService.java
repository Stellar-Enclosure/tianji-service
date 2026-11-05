package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/** Observes an exact owner's stored pre-model checkpoint using an independently provisioned primary reader. */
public final class AgentCheckpointObservationService {
    private static final String BOUNDARY = "FIRST_DURABLE_INPUT_V1";
    private static final String ENGINE = "1.13.0";

    private final DataSource readSource;
    private final AgentStorageVerifier verifier;
    private final AgentLedgerService ledger;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate observation;
    private final AdkSessionEventCodec events = new AdkSessionEventCodec(false);
    private final AdkSessionStateCodec state = new AdkSessionStateCodec(false);
    private final DefaultContextAssembler contextAssembler = new DefaultContextAssembler();

    public AgentCheckpointObservationService(DataSource checkpointReadSource) {
        readSource = Objects.requireNonNull(checkpointReadSource, "checkpointReadSource");
        verifier = new AgentStorageVerifier(checkpointReadSource);
        verifier.verifyCheckpointObservation();
        ledger = new AgentLedgerService(checkpointReadSource);
        jdbc = new JdbcTemplate(checkpointReadSource);
        var manager = new DataSourceTransactionManager(checkpointReadSource);
        manager.setEnforceReadOnly(true);
        observation = new TransactionTemplate(manager);
        observation.setReadOnly(true);
        observation.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        observation.setTimeout(2);
    }

    public enum Observation {
        BOUNDARY_OBSERVED, NO_MATCHING_CHECKPOINT, NOT_AT_CAPTURE_FENCE,
        PROGRESS_OBSERVED, TOOL_EVIDENCE_UNAVAILABLE, SOURCE_UNAVAILABLE
    }

    /** Returns evidence from one bounded snapshot. The caller supplies the verified owner. */
    public Observation observe(TrustedAgentActor actor, GetAgentRunQuery query) {
        if (actor == null || actor.subjectType() != TrustedAgentActor.SubjectType.USER
                || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Verified Agent owner is required");
        }
        if (query == null || query.runId().length() > 64 || query.runId().chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Agent checkpoint observation query is invalid");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(readSource)) {
            throw new IllegalStateException("Agent checkpoint observation must own its snapshot transaction");
        }
        try {
            return Objects.requireNonNull(observation.execute(status -> observeInSnapshot(actor, query)));
        } catch (AgentLedgerException failure) {
            if (failure.code() == AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND) throw failure;
            return Observation.SOURCE_UNAVAILABLE;
        } catch (DataAccessException | TransactionException | IllegalArgumentException failure) {
            return Observation.SOURCE_UNAVAILABLE;
        }
    }

    private Observation observeInSnapshot(TrustedAgentActor actor, GetAgentRunQuery query) {
        jdbc.execute("SET LOCAL statement_timeout = '2s'");
        jdbc.execute("SET LOCAL search_path = pg_catalog, tianji_agent");
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT NOT pg_catalog.pg_is_in_recovery()
                  AND pg_catalog.current_setting('transaction_read_only')='on'
                  AND pg_catalog.current_setting('transaction_isolation')='repeatable read'
                """, Boolean.class))) return Observation.SOURCE_UNAVAILABLE;

        if (!verifier.checkpointObservationRoleReadable()) return Observation.SOURCE_UNAVAILABLE;
        var material = ledger.executionMaterial(actor, query);
        if (!actor.equals(material.authorization().actor()) || !query.runId().equals(material.run().runId())
                || !query.runId().equals(material.authorization().runId())) {
            return Observation.SOURCE_UNAVAILABLE;
        }
        var rows = jdbc.query(OBSERVATION_SQL, (rs, index) -> checkpointRow(rs),
                actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), query.runId());
        if (rows.size() != 1) return Observation.SOURCE_UNAVAILABLE;
        var row = rows.getFirst();
        if (!row.present()) return Observation.NO_MATCHING_CHECKPOINT;
        if (!row.runFence()) return Observation.NOT_AT_CAPTURE_FENCE;
        if (!row.matchesBinding(material.authorization()) || !eligibleSnapshot(material.snapshot())) {
            return Observation.SOURCE_UNAVAILABLE;
        }
        if (!row.sessionBoundary()) return Observation.NOT_AT_CAPTURE_FENCE;
        if (!row.noBudget()) return Observation.PROGRESS_OBSERVED;
        if (row.stateVersion() == null || row.eventTimestamp() == null || row.sessionState() == null
                || row.eventPayload() == null) return Observation.SOURCE_UNAVAILABLE;
        if (!state.decodeMap(row.sessionState(), row.stateVersion()).isEmpty()) return Observation.PROGRESS_OBSERVED;
        var event = events.decode(row.eventPayload());
        Content input = Content.fromParts(contextAssembler.assemble(material.snapshot().input()).parts().stream()
                .map(Part::fromText).toArray(Part[]::new));
        if (!PostgresAdkSessionService.matchesFirstInput(event, input)
                || !event.id().equals(row.eventId()) || !event.invocationId().equals(row.invocationId())
                || event.timestamp() != row.eventTimestamp()) return Observation.SOURCE_UNAVAILABLE;
        if (!verifier.checkpointToolEvidenceReadable()) return Observation.TOOL_EVIDENCE_UNAVAILABLE;

        Boolean noTool = jdbc.queryForObject("""
                SELECT NOT EXISTS (
                    SELECT 1 FROM ONLY tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=?
                ) AND NOT EXISTS (
                    SELECT 1 FROM ONLY tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND origin_run_id=?
                )
                """, Boolean.class, actor.tenantId(), query.runId(), actor.tenantId(), query.runId());
        if (noTool == null) return Observation.TOOL_EVIDENCE_UNAVAILABLE;
        return noTool ? Observation.BOUNDARY_OBSERVED : Observation.PROGRESS_OBSERVED;
    }

    private static boolean eligibleSnapshot(AgentExecutionSnapshot snapshot) {
        var definition = snapshot.definition();
        var dependencies = definition.dependencies();
        var policy = snapshot.effectivePolicy();
        var input = snapshot.input();
        return "CONFIG".equals(definition.executionMode()) && snapshot.processLink() == null
                && dependencies.prompts().isEmpty() && dependencies.skills().isEmpty() && dependencies.tools().isEmpty()
                && policy.maxToolCalls() == 0 && policy.allowedToolVersions().isEmpty()
                && input.artifactRefs().isEmpty() && input.taskData().isEmpty()
                && input.text() != null && !input.text().isBlank();
    }

    private static CheckpointRow checkpointRow(ResultSet rs) throws SQLException {
        return new CheckpointRow(rs.getBoolean("checkpoint_present"), rs.getBoolean("run_fence"),
                rs.getBoolean("session_boundary"), rs.getBoolean("no_budget"),
                rs.getString("safe_boundary"), rs.getString("engine_version"), rs.getBoolean("no_receipts"),
                rs.getString("definition_key"), rs.getObject("definition_version", Long.class),
                rs.getString("definition_sha256"), rs.getString("request_sha256"), rs.getString("snapshot_sha256"),
                rs.getString("policy_version_ref"), rs.getString("checkpoint_event_id"), rs.getString("native_invocation_id"),
                rs.getObject("state_codec_version", Integer.class), rs.getString("session_state"),
                rs.getObject("event_timestamp_ms", Long.class), rs.getString("event_payload"));
    }

    private record CheckpointRow(boolean present, boolean runFence, boolean sessionBoundary, boolean noBudget,
                                 String safeBoundary, String engine, boolean noReceipts, String definitionKey,
                                 Long definitionVersion, String definitionSha, String requestSha, String snapshotSha,
                                 String policyRef, String eventId, String invocationId, Integer stateVersion,
                                 String sessionState, Long eventTimestamp, String eventPayload) {
        boolean matchesBinding(AgentRunAuthorization.Request request) {
            return BOUNDARY.equals(safeBoundary) && ENGINE.equals(engine) && noReceipts && request.sessionRequired()
                    && request.definitionKey().equals(definitionKey)
                    && definitionVersion != null && request.definitionVersion() == definitionVersion
                    && request.definitionSha256().equals(definitionSha) && request.requestSha256().equals(requestSha)
                    && request.snapshotSha256().equals(snapshotSha) && request.policyVersionRef().equals(policyRef);
        }

        @Override public String toString() { return "CheckpointRow[redacted]"; }
    }

    private static final String OBSERVATION_SQL = """
            SELECT c.checkpoint_no IS NOT NULL AS checkpoint_present,
                   COALESCE(r.status='SUSPENDED' AND r.wait_reason='LEASE_EXPIRED' AND r.attempt=1
                     AND r.cancel_requested_at IS NULL AND r.result IS NULL AND r.finished_at IS NULL
                     AND r.error_code IS NULL AND r.lease_owner IS NULL AND r.lease_expires_at IS NULL
                     AND pg_catalog.isfinite(r.deadline_at) AND r.deadline_at>pg_catalog.transaction_timestamp()
                     AND pg_catalog.isfinite(c.captured_at) AND c.captured_at<=pg_catalog.transaction_timestamp()
                     AND r.lease_epoch::numeric=c.run_lease_epoch::numeric+1
                     AND r.revision::numeric=c.run_revision::numeric+1
                     AND r.last_event_sequence::numeric=c.run_event_sequence::numeric+1
                     AND ledger_event.event_type='RUN_STATUS_CHANGED' AND ledger_event.schema_version='1'
                     AND ledger_event.payload='{"from":"RUNNING","status":"SUSPENDED","reason":"LEASE_EXPIRED"}'::jsonb
                     AND NOT EXISTS (
                       SELECT 1 FROM ONLY tianji_agent.agent_event later
                       WHERE later.tenant_id=r.tenant_id AND later.run_id=r.run_id AND later.sequence>r.last_event_sequence
                     )
                     AND NOT EXISTS (
                       SELECT 1 FROM ONLY tianji_agent.agent_checkpoint other
                       WHERE other.tenant_id=r.tenant_id AND other.run_id=r.run_id
                         AND (other.attempt<>1 OR other.checkpoint_no<>1)
                     ), false) AS run_fence,
                   COALESCE(c.prior_session_revision=0 AND c.prior_session_event_sequence=0
                     AND c.session_revision=1 AND c.session_event_sequence=1
                     AND s.revision=c.session_revision AND s.last_event_sequence=c.session_event_sequence
                     AND sl.lease_epoch=c.session_lease_epoch
                     AND ((sl.lease_owner IS NULL AND sl.lease_expires_at IS NULL)
                       OR (sl.lease_owner IS NOT NULL AND pg_catalog.isfinite(sl.lease_expires_at)
                           AND sl.lease_expires_at<=pg_catalog.transaction_timestamp()))
                     AND NOT EXISTS (
                       SELECT 1 FROM ONLY tianji_agent.agent_session_event extra
                       WHERE extra.tenant_id=c.tenant_id AND extra.subject_type=c.subject_type
                         AND extra.subject_issuer=c.subject_issuer AND extra.subject_id=c.subject_id
                         AND extra.app_name=c.app_name AND extra.session_id=c.session_id AND extra.sequence<>c.session_event_sequence
                     )
                     AND NOT EXISTS (
                       SELECT 1 FROM ONLY tianji_agent.agent_session_scope_state scope_state
                       WHERE scope_state.tenant_id=c.tenant_id AND scope_state.app_name=c.app_name
                         AND ((scope_state.scope_type='APP' AND scope_state.subject_type=''
                               AND scope_state.subject_issuer='LEGACY' AND scope_state.subject_id='')
                           OR (scope_state.scope_type='USER' AND scope_state.subject_type=c.subject_type
                               AND scope_state.subject_issuer=c.subject_issuer AND scope_state.subject_id=c.subject_id))
                     ), false) AS session_boundary,
                   COALESCE(r.model_calls_reserved=0 AND r.tool_calls_reserved=0
                     AND r.active_model_calls=0 AND r.active_tool_calls=0 AND r.input_tokens_reserved=0
                     AND r.output_tokens_reserved=0 AND r.estimated_cost_reserved=0
                     AND NOT EXISTS (
                       SELECT 1 FROM ONLY tianji_agent.agent_budget_reservation b
                       WHERE b.tenant_id=r.tenant_id AND b.run_id=r.run_id
                     ), false) AS no_budget,
                   c.safe_boundary,c.engine_version,c.tool_receipts='[]'::jsonb AS no_receipts,
                   c.definition_key,c.definition_version,c.definition_sha256,c.request_sha256,c.snapshot_sha256,
                   c.policy_version_ref,c.native_event_id AS checkpoint_event_id,c.native_invocation_id,
                   s.state_codec_version,
                   CASE WHEN pg_catalog.octet_length(s.session_state::text)<=1048576 THEN s.session_state::text END AS session_state,
                   native_event.event_timestamp_ms,
                   CASE WHEN pg_catalog.octet_length(native_event.payload)<=1048576 THEN native_event.payload END AS event_payload
            FROM ONLY tianji_agent.agent_run r
            LEFT JOIN ONLY tianji_agent.agent_checkpoint c
              ON c.tenant_id=r.tenant_id AND c.run_id=r.run_id AND c.attempt=1 AND c.checkpoint_no=1
              AND c.subject_type=r.subject_type AND c.subject_issuer=r.subject_issuer AND c.subject_id=r.subject_id
              AND c.app_name='tianji' AND c.session_id=r.session_id
            LEFT JOIN ONLY tianji_agent.agent_event ledger_event
              ON ledger_event.tenant_id=r.tenant_id AND ledger_event.run_id=r.run_id AND ledger_event.sequence=r.last_event_sequence
            LEFT JOIN ONLY tianji_agent.agent_session s
              ON s.tenant_id=c.tenant_id AND s.subject_type=c.subject_type AND s.subject_issuer=c.subject_issuer
              AND s.subject_id=c.subject_id AND s.app_name=c.app_name AND s.session_id=c.session_id
            LEFT JOIN ONLY tianji_agent.agent_session_lease sl
              ON sl.tenant_id=c.tenant_id AND sl.subject_type=c.subject_type AND sl.subject_issuer=c.subject_issuer
              AND sl.subject_id=c.subject_id AND sl.app_name=c.app_name AND sl.session_id=c.session_id
            LEFT JOIN ONLY tianji_agent.agent_session_event native_event
              ON native_event.tenant_id=c.tenant_id AND native_event.subject_type=c.subject_type
              AND native_event.subject_issuer=c.subject_issuer AND native_event.subject_id=c.subject_id
              AND native_event.app_name=c.app_name AND native_event.session_id=c.session_id
              AND native_event.sequence=c.session_event_sequence AND native_event.native_event_id=c.native_event_id
            WHERE r.tenant_id=? AND r.subject_type=? AND r.subject_issuer=? AND r.subject_id=? AND r.run_id=?
            LIMIT 2
            """;
}
