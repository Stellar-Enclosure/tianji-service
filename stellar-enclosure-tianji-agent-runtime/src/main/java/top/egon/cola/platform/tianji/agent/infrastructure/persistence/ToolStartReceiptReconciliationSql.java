package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolActionIntentSql.HistoricalStartReceiptVO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Inserts only a positive observation against the exact previously verified Agent snapshot. */
final class ToolStartReceiptReconciliationSql {
    static final String OPERATION = "TOOL_START_RECEIPT_RECONCILE";
    private static final int MAX_RECEIPT_BYTES = 4096;
    private static final String APPEND = """
            WITH fence_clock AS MATERIALIZED (SELECT pg_catalog.clock_timestamp() AS observed_at)
            INSERT INTO tianji_agent.agent_run_command_receipt
                (tenant_id,subject_type,subject_issuer,subject_id,command_id,operation,request_hash,run_id,result,created_at)
            SELECT a.tenant_id,a.owner_type,a.owner_issuer,a.owner_id,a.intent_id::text||':'||a.dispatch_epoch::text,
                   ?,?,d.run_id,CAST(? AS jsonb),f.observed_at
            FROM ONLY tianji_agent.agent_tool_action_intent a
            JOIN ONLY tianji_agent.agent_tool_invocation i
              ON i.tenant_id=a.tenant_id AND i.run_id=a.dispatch_run_id AND i.invocation_id=a.dispatch_invocation_id
            JOIN ONLY tianji_agent.agent_run o ON o.tenant_id=a.tenant_id AND o.run_id=a.origin_run_id
            JOIN ONLY tianji_agent.agent_run d ON d.tenant_id=a.tenant_id AND d.run_id=a.dispatch_run_id
            CROSS JOIN fence_clock f
            WHERE a.tenant_id=? AND a.owner_type=? AND a.owner_issuer=? AND a.owner_id=? AND a.intent_id=? AND d.run_id=?
              AND o.subject_type=a.owner_type AND o.subject_issuer=a.owner_issuer AND o.subject_id=a.owner_id
              AND d.subject_type=a.owner_type AND d.subject_issuer=a.owner_issuer AND d.subject_id=a.owner_id
              AND a.dispatch_epoch>0 AND a.state IN ('DISPATCHING','UNKNOWN') AND a.side_effect_outcome='UNKNOWN'
              AND i.state=a.state AND i.side_effect_outcome='UNKNOWN'
              AND d.lease_owner IS NULL AND d.lease_expires_at IS NULL
              AND (d.status IN ('COMPLETED','FAILED','CANCELLED','TIMED_OUT')
                  OR (d.status='SUSPENDED' AND d.wait_reason='LEASE_EXPIRED'))
              AND i.dispatch_not_after<=f.observed_at AND f.observed_at<CAST(? AS timestamptz)
              AND pg_catalog.to_jsonb(a)=CAST(? AS jsonb)
              AND pg_catalog.to_jsonb(i)=CAST(? AS jsonb)
              AND pg_catalog.to_jsonb(o)=CAST(? AS jsonb)
              AND pg_catalog.to_jsonb(d)=CAST(? AS jsonb)
            ON CONFLICT (tenant_id,subject_type,subject_issuer,subject_id,operation,command_id) DO NOTHING
            RETURNING run_id,request_hash,result::text AS encoded_evidence,created_at
            """;
    private static final String READ = """
            SELECT run_id,request_hash,
                   CASE WHEN pg_catalog.octet_length(result::text)<=? THEN result::text END AS encoded_evidence,
                   created_at
            FROM ONLY tianji_agent.agent_run_command_receipt
            WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND operation=? AND command_id=?
            LIMIT 2
            """;

    private final DataSource source;
    private final JdbcTemplate jdbc;

    ToolStartReceiptReconciliationSql(DataSource source) {
        this.source = Objects.requireNonNull(source, "source");
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(2);
    }

    Optional<StoredReceiptVO> append(HistoricalStartReceiptVO source, String requestSha256, String encodedEvidence, Instant notAfter) {
        requireTransaction(false);
        if (source == null || !hash(requestSha256) || !validEvidence(encodedEvidence) || notAfter == null
                || !("DISPATCHING".equals(source.action().state()) || "UNKNOWN".equals(source.action().state()))) {
            throw unavailable();
        }
        Instant dbDeadline = notAfter.truncatedTo(ChronoUnit.MICROS);
        var owner = source.action().action().owner();
        try {
            var rows = jdbc.query(APPEND, (rs, index) -> row(rs), OPERATION, requestSha256, encodedEvidence,
                    owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(),
                    UUID.fromString(source.action().action().intentId()), source.dispatchRun().runId(), Timestamp.from(dbDeadline),
                    source.actionStamp(), source.invocationStamp(), source.originRunStamp(), source.dispatchRunStamp());
            // ON CONFLICT may wait for a competing transaction. Never commit an observation after its permit window.
            requireBefore(dbDeadline);
            if (rows.isEmpty()) return Optional.empty();
            if (rows.size() != 1) throw unavailable();
            var row = rows.getFirst();
            if (!requestSha256.equals(row.requestSha256()) || row.observedAt() == null || !row.observedAt().isBefore(dbDeadline)) {
                throw unavailable();
            }
            return Optional.of(row.forRun(source.dispatchRun().runId()));
        } catch (DataAccessException privateStorage) {
            throw unavailable();
        }
    }

    Optional<StoredReceiptVO> read(TrustedAgentActor owner, String dispatchRunId, UUID intentId, long dispatchEpoch, Instant deadline) {
        requireTransaction(true);
        if (owner == null || !hash(owner.issuerFingerprint()) || dispatchRunId == null || intentId == null || dispatchEpoch < 1
                || deadline == null) throw unavailable();
        try {
            var rows = jdbc.query(READ, (rs, index) -> row(rs), MAX_RECEIPT_BYTES,
                    owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(),
                    OPERATION, intentId + ":" + dispatchEpoch);
            requireBefore(deadline.truncatedTo(ChronoUnit.MICROS));
            if (rows.isEmpty()) return Optional.empty();
            if (rows.size() != 1) throw unavailable();
            return Optional.of(rows.getFirst().forRun(dispatchRunId));
        } catch (DataAccessException privateStorage) {
            throw unavailable();
        }
    }

    private void requireTransaction(boolean readOnly) {
        if (Thread.currentThread().isInterrupted() || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source)
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly() != readOnly) throw unavailable();
    }

    private void requireBefore(Instant deadline) {
        if (Thread.currentThread().isInterrupted() || !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pg_catalog.clock_timestamp()<CAST(? AS timestamptz)", Boolean.class, Timestamp.from(deadline)))) {
            throw unavailable();
        }
    }

    private static StoredRow row(ResultSet rs) throws SQLException {
        var observedAt = rs.getTimestamp("created_at");
        return new StoredRow(rs.getString("run_id"), rs.getString("request_hash"), rs.getString("encoded_evidence"),
                observedAt == null ? null : observedAt.toInstant());
    }

    private record StoredRow(String runId, String requestSha256, String encodedEvidence, Instant observedAt) {
        StoredReceiptVO forRun(String expectedRunId) {
            if (!expectedRunId.equals(runId)) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                        "Historical Tool receipt identity already belongs to another request");
            }
            return new StoredReceiptVO(requestSha256, encodedEvidence, observedAt);
        }

        @Override public String toString() { return "StoredToolStartReceiptRow[redacted]"; }
    }

    record StoredReceiptVO(String requestSha256, String encodedEvidence, Instant observedAt) {
        StoredReceiptVO {
            if (!hash(requestSha256) || !validEvidence(encodedEvidence) || observedAt == null) throw unavailable();
        }

        @Override public String toString() { return "StoredToolStartReceipt[redacted]"; }
    }

    private static boolean validEvidence(String encoded) {
        return encoded != null && encoded.length() <= MAX_RECEIPT_BYTES && encoded.startsWith("{") && encoded.endsWith("}")
                && encoded.getBytes(StandardCharsets.UTF_8).length <= MAX_RECEIPT_BYTES;
    }

    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                "Historical Tool receipt storage is unavailable");
    }
}
