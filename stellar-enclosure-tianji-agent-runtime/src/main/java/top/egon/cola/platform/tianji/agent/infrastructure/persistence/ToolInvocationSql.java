package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.ApprovedWriteDispatchBeginResultVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.DispatchAttemptVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.DispatchBeginResultVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.PreparationRequestVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.RecordedResultVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.RecorderPreparationVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.WriteDispatchBeginResultVO;
import top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier.ApprovedObservation;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport.VerifiedExecutor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

/** Internal same-source transaction participant. The owner verifies schema and current authority before entering. */
final class ToolInvocationSql {
    private final DataSource source;
    private final AgentLedgerService ledger;
    private final ToolCapabilityStore capabilities;
    private final JdbcTemplate jdbc;
    private final ToolActionIntentSql intents;
    private final ToolExecutionJsonCodec codec = new ToolExecutionJsonCodec();

    ToolInvocationSql(DataSource source, AgentLedgerService ledger, ToolCapabilityStore capabilities) {
        this.source = source; this.ledger = ledger; this.capabilities = capabilities;
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
        intents = new ToolActionIntentSql(source, ledger);
    }

    enum Projection { LEGACY_PREPARATION, RECORDER_METADATA, IDEMPOTENT_WRITE_METADATA, APPROVED_RECORDER_METADATA }

    WriteDispatchBeginResultVO beginIdempotentWriteDispatch(PreparationRequestVO request, RecorderPreparationVO expected, ToolExecutionGrant.EvidenceVO evidence,
            ToolExecutionAuthorization.AuditVO audit, VerifiedExecutor executor, String nonceSha256, ToolExecutionOutcome notDispatchedOutcome,
            Instant callDeadline, Instant authorityDeadline) {
        var live = requireRun(request, authorityDeadline); var current = currentEvidence(request, evidence, authorityDeadline);
        var action = intents.lockForWrite(request.action(), request.descriptor(), authorityDeadline);
        var stored = read(request, authorityDeadline, false).orElseThrow(ToolInvocationSql::unavailable); sameMetadata(expected, stored.invocation());
        callBounds(request, stored, current, audit, executor, callDeadline, live, authorityDeadline);
        if (callDeadline.isAfter(action.expiresAt())) throw unavailable();
        if (!"PREPARED".equals(stored.invocation().state())) {
            if (stored.invocation().dispatchEpoch() > 0 && action.dispatch() == null) throw conflict();
            return new WriteDispatchBeginResultVO(stored.invocation(), projection(action), null);
        }
        if (!"PREPARED".equals(action.state()) || action.dispatch() != null || !action.expiresAt().isAfter(now(authorityDeadline))) {
            var closed = closeWriteInvocation(request, stored, notDispatchedOutcome, authorityDeadline);
            var currentAction = intents.lockForWrite(request.action(), request.descriptor(), authorityDeadline);
            return new WriteDispatchBeginResultVO(closed.invocation(), projection(currentAction), null);
        }
        if (expected.revision() != stored.invocation().revision() || stored.invocation().revision() == Long.MAX_VALUE) throw conflict();
        requirePreparationState(request, current, live, action, stored);
        Instant dispatched = now(authorityDeadline).truncatedTo(ChronoUnit.MICROS); var owner = request.binding().run().actor();
        if (!callDeadline.isAfter(dispatched) || !action.expiresAt().isAfter(dispatched)) throw unavailable();
        if (jdbc.update("""
                UPDATE tianji_agent.agent_tool_invocation SET state='DISPATCHING',revision=revision+1,dispatch_epoch=1,
                  dispatch_nonce_sha256=?,executor_issuer=?,executor_id=?,authorization_audit=CAST(? AS jsonb),updated_at=?,dispatched_at=?,side_effect_outcome='UNKNOWN'
                WHERE tenant_id=? AND owner_type=? AND owner_issuer=? AND owner_id=? AND run_id=? AND invocation_id=?
                  AND state='PREPARED' AND revision=? AND dispatch_epoch=0 AND dispatch_nonce_sha256 IS NULL AND executor_issuer IS NULL AND executor_id IS NULL AND dispatched_at IS NULL
                  AND risk='IDEMPOTENT_WRITE' AND intent_id=?
                """, nonceSha256, executor.actor().issuerFingerprint(), executor.actor().subjectId(), codec.audit(audit), Timestamp.from(dispatched), Timestamp.from(dispatched),
                owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), request.binding().run().runId(), request.invocationId(), stored.invocation().revision(), UUID.fromString(request.action().intentId())) != 1) throw conflict();
        var dispatch = new ToolActionIntentSql.DispatchVO(request.binding().run().runId(), request.invocationId(), 1, nonceSha256, executor.actor());
        // The immediate backlink FK requires the invocation key before the action points at it.
        intents.beginWrite(action, dispatch, dispatched, authorityDeadline);
        var currentAction = intents.lockForWrite(request.action(), request.descriptor(), authorityDeadline);
        var changed = read(request, authorityDeadline, false).orElseThrow(ToolInvocationSql::unavailable);
        requireRun(request, authorityDeadline); now(authorityDeadline);
        var metadata = changed.invocation();
        return new WriteDispatchBeginResultVO(metadata, projection(currentAction), new DispatchAttemptVO(metadata.runId(), metadata.invocationId(), metadata.dispatchEpoch(),
                metadata.capabilityIssueEpoch(), changed.dispatchNonceSha256(), changed.executor(), changed.authorizationAudit(), metadata.dispatchedAt(), callDeadline));
    }
    RecordedResultVO completeIdempotentWrite(PreparationRequestVO request, DispatchAttemptVO attempt, ToolExecutionOutcome outcome, Instant authorityDeadline) {
        if (!identityRun(request, authorityDeadline)) throw unavailable();
        var action = intents.lockForWrite(request.action(), request.descriptor(), authorityDeadline);
        var stored = read(request, authorityDeadline, true).orElseThrow(ToolInvocationSql::unavailable); var metadata = stored.invocation();
        requireAttempt(stored, attempt);
        if (action.dispatch() == null) throw conflict();
        boolean linked = sameDispatch(action.dispatch(), stored);
        outcome = outcome.validated(request.descriptor());
        if (outcome.status() == ToolExecutionOutcome.Status.AWAITING_APPROVAL) throw unavailable();
        if (Set.of("SUCCEEDED", "REJECTED", "FAILED_KNOWN").contains(metadata.state()) || "UNKNOWN".equals(metadata.state()) && outcome.status() == ToolExecutionOutcome.Status.UNKNOWN) {
            if (!sameOutcome(stored.outcome(), outcome)) throw conflict(); return result(stored);
        }
        if (!Set.of("DISPATCHING", "UNKNOWN").contains(metadata.state()) || metadata.revision() == Long.MAX_VALUE) throw conflict();
        Instant finished = now(authorityDeadline, true).truncatedTo(ChronoUnit.MICROS);
        if (finished.isBefore(stored.updatedAt()) || linked && finished.isBefore(action.updatedAt())) throw unavailable();
        var owner = request.binding().run().actor(); String effect = outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED ? "CONFIRMED" : outcome.status() == ToolExecutionOutcome.Status.UNKNOWN ? "UNKNOWN" : "NONE";
        if (jdbc.update("""
                UPDATE tianji_agent.agent_tool_invocation SET state=?,revision=revision+1,outcome=CAST(? AS jsonb),business_receipt_id=?,error_code=?,side_effect_outcome=?,updated_at=?,finished_at=?
                WHERE tenant_id=? AND owner_type=? AND owner_issuer=? AND owner_id=? AND run_id=? AND invocation_id=?
                  AND state IN ('DISPATCHING','UNKNOWN') AND revision=? AND dispatch_epoch=? AND dispatch_nonce_sha256=? AND executor_issuer=? AND executor_id=?
                  AND risk='IDEMPOTENT_WRITE' AND intent_id=?
                """, outcome.status().name(), codec.outcome(request.descriptor(), outcome), outcome.businessReceiptId(), outcome.errorCode(), effect, Timestamp.from(finished), Timestamp.from(finished),
                owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), request.binding().run().runId(), request.invocationId(), metadata.revision(), metadata.dispatchEpoch(),
                attempt.dispatchNonceSha256(), attempt.executor().issuerFingerprint(), attempt.executor().subjectId(), UUID.fromString(request.action().intentId())) != 1) throw conflict();
        if (linked) intents.completeWrite(action, outcome, finished, authorityDeadline);
        intents.lockForWrite(request.action(), request.descriptor(), authorityDeadline);
        return result(read(request, authorityDeadline, true).orElseThrow(ToolInvocationSql::unavailable));
    }
    ApprovedWriteDispatchBeginResultVO consumeApprovalAndBeginDispatch(PreparationRequestVO request, ApprovedObservation approval,
            ToolExecutionGrant.EvidenceVO evidence, ToolExecutionAuthorization.AuditVO audit, VerifiedExecutor executor,
            String nonceSha256, Instant callDeadline, Instant storageDeadline) {
        var live = requireRun(request, storageDeadline); var current = currentEvidence(request, evidence, storageDeadline);
        var action = intents.lockForApproval(request.action(), request.descriptor(), storageDeadline);
        var own = read(request, storageDeadline, false);
        if (own.isPresent()) {
            var stored = own.orElseThrow();
            if (stored.invocation().dispatchEpoch() > 0 && action.dispatch() == null) throw conflict();
            return new ApprovedWriteDispatchBeginResultVO(projection(action), stored.invocation(), stored.budget(), null);
        }
        Instant checked = now(storageDeadline);
        if (!"AWAITING_APPROVAL".equals(action.state()) || action.approval() != null || action.dispatch() != null
                || !action.expiresAt().isAfter(checked)) {
            return new ApprovedWriteDispatchBeginResultVO(projection(action), null, null, null);
        }
        if (callDeadline == null || callDeadline.getNano() % 1_000 != 0 || !callDeadline.isAfter(checked)
                || nonceSha256 == null || !nonceSha256.matches("[0-9a-f]{64}")
                || executor == null || executor.actor().subjectType() != TrustedAgentActor.SubjectType.SERVICE
                || "LEGACY".equals(executor.actor().issuerFingerprint()) || executor.actor().tenantId() != request.binding().run().actor().tenantId()
                || approval == null || approval.tenantId() != request.binding().run().actor().tenantId()) throw unavailable();
        Instant dispatched = checked.truncatedTo(ChronoUnit.MICROS);
        approvedCallBounds(request, action, current, audit, executor, approval, live, dispatched, callDeadline, storageDeadline);
        var budget = ledger.budgets().reserveBudget(request.binding().run().actor(), request.lease(),
                new AgentBudgetRequest(request.reservationId(), AgentBudgetRequest.Kind.TOOL, request.descriptor().versionKey(), 0, 0, request.estimatedCost()));
        if (!budget.newlyCreated() || budget.state() != AgentBudgetReservation.State.RESERVED || !request.reservationId().equals(budget.reservationId())
                || budget.leaseEpoch() != request.lease().epoch() || budget.kind() != AgentBudgetRequest.Kind.TOOL
                || !request.descriptor().versionKey().equals(budget.toolVersion()) || budget.inputTokens() != 0 || budget.outputTokens() != 0
                || !sameCost(request.estimatedCost(), budget.estimatedCost()) || budget.actualInputTokens() != null
                || budget.actualOutputTokens() != null || budget.actualCost() != null) throw conflict();
        var owner = request.binding().run().actor();
        if (jdbc.update("""
                INSERT INTO tianji_agent.agent_tool_invocation
                  (tenant_id,run_id,invocation_id,owner_type,owner_issuer,owner_id,capability_id,capability_binding_sha256,capability_issue_epoch,lease_epoch,reservation_id,
                   tool_key,tool_version,tool_sha256,binding_sha256,parameters_sha256,risk,intent_id,state,revision,dispatch_epoch,dispatch_nonce_sha256,
                   executor_issuer,executor_id,authorization_audit,side_effect_outcome,outcome,created_at,updated_at,dispatch_not_after,dispatched_at,finished_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'APPROVAL_REQUIRED',?,'DISPATCHING',1,1,?,?,?,CAST(? AS jsonb),'UNKNOWN',NULL,?,?,?,?,NULL)
                """, owner.tenantId(), request.binding().run().runId(), request.invocationId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(),
                UUID.fromString(request.binding().capabilityId()), request.binding().bindingSha256(), current.reservation().issueEpoch(), request.lease().epoch(), request.reservationId(),
                request.descriptor().key(), request.descriptor().version(), request.descriptor().reference().contentSha256(), request.descriptor().policy().bindingSha256(), request.parametersSha256(),
                UUID.fromString(request.action().intentId()), nonceSha256, executor.actor().issuerFingerprint(), executor.actor().subjectId(), codec.audit(audit),
                Timestamp.from(dispatched), Timestamp.from(dispatched), Timestamp.from(callDeadline), Timestamp.from(dispatched)) != 1) throw conflict();
        var dispatch = new ToolActionIntentSql.DispatchVO(request.binding().run().runId(), request.invocationId(), 1, nonceSha256, executor.actor());
        intents.beginApprovedWrite(action, approval, dispatch, dispatched, storageDeadline);
        var currentAction = intents.lockForApproval(request.action(), request.descriptor(), storageDeadline);
        var changed = read(request, storageDeadline, false).orElseThrow(ToolInvocationSql::unavailable);
        requireRun(request, storageDeadline); now(storageDeadline);
        if (currentAction.approval() == null || currentAction.dispatch() == null || !sameDispatch(currentAction.dispatch(), changed)) throw unavailable();
        var metadata = changed.invocation();
        var attempt = new DispatchAttemptVO(metadata.runId(), metadata.invocationId(), metadata.dispatchEpoch(), metadata.capabilityIssueEpoch(),
                changed.dispatchNonceSha256(), changed.executor(), changed.authorizationAudit(), metadata.dispatchedAt(), callDeadline);
        return new ApprovedWriteDispatchBeginResultVO(projection(currentAction), metadata, changed.budget(), attempt);
    }
    RecordedResultVO completeApprovedWrite(PreparationRequestVO request, DispatchAttemptVO attempt,
            ToolExecutionOutcome outcome, Instant storageDeadline) {
        if (!identityRun(request, storageDeadline)) throw unavailable();
        var action = intents.lockForApproval(request.action(), request.descriptor(), storageDeadline);
        var stored = read(request, storageDeadline, true).orElseThrow(ToolInvocationSql::unavailable); var metadata = stored.invocation();
        requireAttempt(stored, attempt);
        if (action.approval() == null || action.dispatch() == null) throw conflict();
        boolean linked = sameDispatch(action.dispatch(), stored);
        outcome = outcome.validated(request.descriptor());
        if (outcome.status() == ToolExecutionOutcome.Status.AWAITING_APPROVAL) throw unavailable();
        if (Set.of("SUCCEEDED", "REJECTED", "FAILED_KNOWN").contains(metadata.state())
                || "UNKNOWN".equals(metadata.state()) && outcome.status() == ToolExecutionOutcome.Status.UNKNOWN) {
            if (!sameOutcome(stored.outcome(), outcome)) throw conflict(); return result(stored);
        }
        if (!Set.of("DISPATCHING", "UNKNOWN").contains(metadata.state()) || metadata.revision() == Long.MAX_VALUE) throw conflict();
        Instant finished = now(storageDeadline, true).truncatedTo(ChronoUnit.MICROS);
        if (finished.isBefore(stored.updatedAt()) || linked && finished.isBefore(action.updatedAt())) throw unavailable();
        var owner = request.binding().run().actor(); String effect = outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED ? "CONFIRMED"
                : outcome.status() == ToolExecutionOutcome.Status.UNKNOWN ? "UNKNOWN" : "NONE";
        if (jdbc.update("""
                UPDATE tianji_agent.agent_tool_invocation SET state=?,revision=revision+1,outcome=CAST(? AS jsonb),business_receipt_id=?,error_code=?,side_effect_outcome=?,updated_at=?,finished_at=?
                WHERE tenant_id=? AND owner_type=? AND owner_issuer=? AND owner_id=? AND run_id=? AND invocation_id=?
                  AND state IN ('DISPATCHING','UNKNOWN') AND revision=? AND dispatch_epoch=? AND dispatch_nonce_sha256=? AND executor_issuer=? AND executor_id=?
                  AND risk='APPROVAL_REQUIRED' AND intent_id=?
                """, outcome.status().name(), codec.outcome(request.descriptor(), outcome), outcome.businessReceiptId(), outcome.errorCode(), effect,
                Timestamp.from(finished), Timestamp.from(finished), owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(),
                request.binding().run().runId(), request.invocationId(), metadata.revision(), metadata.dispatchEpoch(), attempt.dispatchNonceSha256(),
                attempt.executor().issuerFingerprint(), attempt.executor().subjectId(), UUID.fromString(request.action().intentId())) != 1) throw conflict();
        if (linked) intents.completeApprovedWrite(action, outcome, finished, storageDeadline);
        intents.lockForApproval(request.action(), request.descriptor(), storageDeadline);
        return result(read(request, storageDeadline, true).orElseThrow(ToolInvocationSql::unavailable));
    }
    Optional<RecorderPreparationVO> closePreparedWriteFailure(PreparationRequestVO request, RecorderPreparationVO expected, ToolExecutionOutcome notDispatchedOutcome,
            Instant authorityDeadline) {
        if (!identityRun(request, authorityDeadline) || !invocationExists(request, authorityDeadline)) return Optional.empty();
        intents.lockForWrite(request.action(), request.descriptor(), authorityDeadline);
        var found = read(request, authorityDeadline, true); if (found.isEmpty()) return Optional.empty();
        var stored = found.orElseThrow(); sameMetadata(expected, stored.invocation());
        if (!"PREPARED".equals(stored.invocation().state())) return Optional.of(stored.invocation());
        var closed = closeWriteInvocation(request, stored, notDispatchedOutcome, authorityDeadline);
        intents.lockForWrite(request.action(), request.descriptor(), authorityDeadline); return Optional.of(closed.invocation());
    }

    PreparedRowsVO prepare(PreparationRequestVO request, ToolExecutionGrant.EvidenceVO expected, ToolExecutionAuthorization.AuditVO audit, Instant authorityDeadline, Projection projection) {
        var live = requireRun(request, authorityDeadline);
        var current = capabilities.currentInTransaction(request.binding(), request.descriptor()).orElseThrow(ToolInvocationSql::unavailable);
        if (!expected.equals(current) || !current.expiresAt().isAfter(now(authorityDeadline))) throw unavailable();
        ToolActionIntentSql.PreparationResultVO action = request.action() == null ? null
                : intents.prepareWithStatus(request.binding().run(), request.lease(), request.action(), request.descriptor(), request.intentExpiry(), authorityDeadline);
        var actionRow = action == null ? null : action.row();
        var existing = read(request, authorityDeadline, false);
        if (existing.isPresent()) {
            var stored = existing.orElseThrow();
            if (projection == Projection.APPROVED_RECORDER_METADATA) {
                if (stored.invocation().dispatchEpoch() > 0 && actionRow.dispatch() == null) throw conflict();
                requireRun(request, authorityDeadline);
                return new PreparedRowsVO(stored, projection(actionRow), false);
            }
            if (Set.of("PREPARED", "AWAITING_APPROVAL").contains(stored.invocation().state())) {
                if (projection != Projection.IDEMPOTENT_WRITE_METADATA || "PREPARED".equals(actionRow.state())) requirePreparationState(request, current, live, actionRow, stored);
            }
            else if (projection == Projection.LEGACY_PREPARATION) throw unavailable();
            if (projection == Projection.IDEMPOTENT_WRITE_METADATA && stored.invocation().dispatchEpoch() > 0 && actionRow.dispatch() == null) throw conflict();
            requireRun(request, authorityDeadline);
            return new PreparedRowsVO(stored, projection(actionRow), false);
        }
        if (projection == Projection.IDEMPOTENT_WRITE_METADATA && (!"PREPARED".equals(actionRow.state()) || !actionRow.expiresAt().isAfter(now(authorityDeadline)))
                || action != null && !action.newlyCreated() && projection != Projection.IDEMPOTENT_WRITE_METADATA) {
            requireRun(request, authorityDeadline);
            return new PreparedRowsVO(null, projection(actionRow), false);
        }
        Instant created = now(authorityDeadline).truncatedTo(ChronoUnit.MICROS);
        Instant notAfter = dispatchDeadline(created, live.toolTimeout(), request, current, actionRow);
        if (!notAfter.isAfter(now(authorityDeadline))) throw unavailable();
        var budget = ledger.budgets().reserveBudget(request.binding().run().actor(), request.lease(),
                new AgentBudgetRequest(request.reservationId(), AgentBudgetRequest.Kind.TOOL, request.descriptor().versionKey(), 0, 0, request.estimatedCost()));
        if (!budget.newlyCreated()) throw conflict();
        boolean waiting = request.descriptor().policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED;
        String state = waiting ? "AWAITING_APPROVAL" : "PREPARED";
        String outcome = waiting ? codec.outcome(request.descriptor(), new ToolExecutionOutcome(ToolExecutionOutcome.Status.AWAITING_APPROVAL, Map.of(), null, null, BigDecimal.ZERO)) : null;
        var owner = request.binding().run().actor();
        if (jdbc.update("""
                INSERT INTO tianji_agent.agent_tool_invocation
                  (tenant_id,run_id,invocation_id,owner_type,owner_issuer,owner_id,capability_id,capability_binding_sha256,capability_issue_epoch,lease_epoch,reservation_id,
                   tool_key,tool_version,tool_sha256,binding_sha256,parameters_sha256,risk,intent_id,state,revision,dispatch_epoch,authorization_audit,side_effect_outcome,outcome,
                   created_at,updated_at,dispatch_not_after,finished_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,0,CAST(? AS jsonb),'NONE',CAST(? AS jsonb),?,?,?,?)
                """, owner.tenantId(), request.binding().run().runId(), request.invocationId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(),
                UUID.fromString(request.binding().capabilityId()), request.binding().bindingSha256(), current.reservation().issueEpoch(), request.lease().epoch(), request.reservationId(),
                request.descriptor().key(), request.descriptor().version(), request.descriptor().reference().contentSha256(), request.descriptor().policy().bindingSha256(), request.parametersSha256(),
                request.descriptor().policy().risk().name(), request.action() == null ? null : UUID.fromString(request.action().intentId()), state, codec.audit(audit), outcome,
                Timestamp.from(created), Timestamp.from(created), Timestamp.from(notAfter), waiting ? Timestamp.from(created) : null) != 1) throw unavailable();
        if (waiting) {
            var settled = ledger.budgets().settleBudget(owner, request.lease(), request.reservationId(), new AgentBudgetSettlement(0L, 0L, BigDecimal.ZERO));
            if (settled.state() != AgentBudgetReservation.State.SETTLED) throw unavailable();
        }
        var inserted = read(request, authorityDeadline, false).orElseThrow(ToolInvocationSql::unavailable);
        requirePreparationState(request, current, live, actionRow, inserted);
        requireRun(request, authorityDeadline);
        if (!notAfter.isAfter(now(authorityDeadline))) throw unavailable();
        return new PreparedRowsVO(inserted, projection(actionRow), true);
    }

    DispatchBeginResultVO beginReadOnlyDispatch(PreparationRequestVO request, RecorderPreparationVO expected, ToolExecutionGrant.EvidenceVO evidence,
                                               ToolExecutionAuthorization.AuditVO audit, VerifiedExecutor executor, String nonceSha256, Instant callDeadline, Instant authorityDeadline) {
        var live = requireRun(request, authorityDeadline); var current = currentEvidence(request, evidence, authorityDeadline);
        var stored = read(request, authorityDeadline, false).orElseThrow(ToolInvocationSql::unavailable); sameMetadata(expected, stored.invocation());
        callBounds(request, stored, current, audit, executor, callDeadline, live, authorityDeadline);
        if (!"PREPARED".equals(stored.invocation().state())) return new DispatchBeginResultVO(stored.invocation(), null);
        if (expected.revision() != stored.invocation().revision()) throw conflict();
        requirePreparationState(request, current, live, null, stored);
        if (stored.invocation().revision() == Long.MAX_VALUE) throw unavailable();
        Instant dispatched = now(authorityDeadline).truncatedTo(ChronoUnit.MICROS); var owner = request.binding().run().actor();
        if (!callDeadline.isAfter(dispatched)) throw unavailable();
        if (jdbc.update("""
                UPDATE tianji_agent.agent_tool_invocation SET state='DISPATCHING',revision=revision+1,dispatch_epoch=1,
                  dispatch_nonce_sha256=?,executor_issuer=?,executor_id=?,authorization_audit=CAST(? AS jsonb),updated_at=?,dispatched_at=?
                WHERE tenant_id=? AND owner_type=? AND owner_issuer=? AND owner_id=? AND run_id=? AND invocation_id=?
                  AND state='PREPARED' AND revision=? AND dispatch_epoch=0 AND dispatch_nonce_sha256 IS NULL AND executor_issuer IS NULL AND executor_id IS NULL AND dispatched_at IS NULL
                """, nonceSha256, executor.actor().issuerFingerprint(), executor.actor().subjectId(), codec.audit(audit), Timestamp.from(dispatched), Timestamp.from(dispatched),
                owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), request.binding().run().runId(), request.invocationId(), stored.invocation().revision()) != 1) throw conflict();
        var changed = read(request, authorityDeadline, false).orElseThrow(ToolInvocationSql::unavailable);
        requireRun(request, authorityDeadline); now(authorityDeadline);
        return new DispatchBeginResultVO(changed.invocation(), new DispatchAttemptVO(changed.invocation().runId(), changed.invocation().invocationId(), changed.invocation().dispatchEpoch(),
                changed.invocation().capabilityIssueEpoch(), changed.dispatchNonceSha256(), changed.executor(), changed.authorizationAudit(), changed.invocation().dispatchedAt(), callDeadline));
    }

    Optional<RecordedResultVO> readRecorded(PreparationRequestVO request, RecorderPreparationVO expected, ToolExecutionGrant.EvidenceVO evidence,
                                           ToolExecutionAuthorization.AuditVO audit, VerifiedExecutor executor, Instant callDeadline, Instant authorityDeadline) {
        var live = requireRun(request, authorityDeadline); var current = currentEvidence(request, evidence, authorityDeadline);
        var stored = read(request, authorityDeadline, false).orElseThrow(ToolInvocationSql::unavailable); sameMetadata(expected, stored.invocation());
        callBounds(request, stored, current, audit, executor, callDeadline, live, authorityDeadline);
        requireRun(request, authorityDeadline); now(authorityDeadline);
        return Set.of("SUCCEEDED", "REJECTED", "FAILED_KNOWN").contains(stored.invocation().state()) ? Optional.of(result(stored)) : Optional.empty();
    }

    RecordedResultVO completeReadOnly(PreparationRequestVO request, DispatchAttemptVO attempt, ToolExecutionOutcome supplied, Instant authorityDeadline) {
        if (!identityRun(request, authorityDeadline)) throw unavailable();
        var stored = read(request, authorityDeadline, true).orElseThrow(ToolInvocationSql::unavailable); var metadata = stored.invocation();
        requireAttempt(stored, attempt);
        var outcome = supplied.validated(request.descriptor());
        if (outcome.status() == ToolExecutionOutcome.Status.AWAITING_APPROVAL || outcome.actualCost() != null && outcome.actualCost().signum() != 0
                || outcome.status() != ToolExecutionOutcome.Status.UNKNOWN && outcome.actualCost() == null) throw unavailable();
        if (Set.of("SUCCEEDED", "REJECTED", "FAILED_KNOWN").contains(metadata.state()) || "UNKNOWN".equals(metadata.state()) && outcome.status() == ToolExecutionOutcome.Status.UNKNOWN) {
            if (!sameOutcome(stored.outcome(), outcome)) throw conflict(); return result(stored);
        }
        if (!Set.of("DISPATCHING", "UNKNOWN").contains(metadata.state()) || metadata.revision() == Long.MAX_VALUE) throw conflict();
        Instant finished = now(authorityDeadline, true).truncatedTo(ChronoUnit.MICROS);
        if (finished.isBefore(stored.updatedAt())) throw unavailable(); var owner = request.binding().run().actor();
        if (jdbc.update("""
                UPDATE tianji_agent.agent_tool_invocation SET state=?,revision=revision+1,outcome=CAST(? AS jsonb),error_code=?,updated_at=?,finished_at=?,side_effect_outcome='NONE'
                WHERE tenant_id=? AND owner_type=? AND owner_issuer=? AND owner_id=? AND run_id=? AND invocation_id=?
                  AND state IN ('DISPATCHING','UNKNOWN') AND revision=? AND dispatch_epoch=? AND dispatch_nonce_sha256=? AND executor_issuer=? AND executor_id=?
                """, outcome.status().name(), codec.outcome(request.descriptor(), outcome), outcome.errorCode(), Timestamp.from(finished), Timestamp.from(finished),
                owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), request.binding().run().runId(), request.invocationId(), metadata.revision(), metadata.dispatchEpoch(),
                attempt.dispatchNonceSha256(), attempt.executor().issuerFingerprint(), attempt.executor().subjectId()) != 1) throw conflict();
        return result(read(request, authorityDeadline, true).orElseThrow(ToolInvocationSql::unavailable));
    }

    Optional<RecorderPreparationVO> closePreparedFailure(PreparationRequestVO request, RecorderPreparationVO expected, String safeCode, Instant authorityDeadline) {
        if (!identityRun(request, authorityDeadline)) return Optional.empty();
        var found = read(request, authorityDeadline, true); if (found.isEmpty()) return Optional.empty(); var stored = found.orElseThrow();
        sameMetadata(expected, stored.invocation());
        if (!"PREPARED".equals(stored.invocation().state())) return Optional.of(stored.invocation());
        if (stored.invocation().dispatchEpoch() != 0 || stored.invocation().revision() == Long.MAX_VALUE) throw unavailable();
        var outcome = new ToolExecutionOutcome(ToolExecutionOutcome.Status.FAILED_KNOWN, Map.of(), null, safeCode, BigDecimal.ZERO);
        Instant finished = now(authorityDeadline, true).truncatedTo(ChronoUnit.MICROS);
        if (finished.isBefore(stored.updatedAt())) throw unavailable(); var owner = request.binding().run().actor();
        if (jdbc.update("""
                UPDATE tianji_agent.agent_tool_invocation SET state='FAILED_KNOWN',revision=revision+1,outcome=CAST(? AS jsonb),error_code=?,updated_at=?,finished_at=?
                WHERE tenant_id=? AND owner_type=? AND owner_issuer=? AND owner_id=? AND run_id=? AND invocation_id=?
                  AND state='PREPARED' AND revision=? AND dispatch_epoch=0 AND dispatch_nonce_sha256 IS NULL AND executor_issuer IS NULL AND executor_id IS NULL AND dispatched_at IS NULL
                """, codec.outcome(request.descriptor(), outcome), safeCode, Timestamp.from(finished), Timestamp.from(finished), owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(),
                owner.subjectId(), request.binding().run().runId(), request.invocationId(), stored.invocation().revision()) != 1) throw conflict();
        return Optional.of(read(request, authorityDeadline, true).orElseThrow(ToolInvocationSql::unavailable).invocation());
    }

    private boolean invocationExists(PreparationRequestVO request, Instant deadline) {
        now(deadline, true); var owner = request.binding().run().actor();
        var found = jdbc.query("""
                SELECT i.invocation_id FROM tianji_agent.agent_tool_invocation i
                WHERE i.tenant_id=? AND i.owner_type=? AND i.owner_issuer=? AND i.owner_id=? AND i.run_id=? AND i.invocation_id=?
                """, (rs, number) -> rs.getObject("invocation_id", UUID.class), owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), request.binding().run().runId(), request.invocationId());
        now(deadline, true); if (found.size() > 1 || !found.isEmpty() && !request.invocationId().equals(found.getFirst())) throw unavailable(); return !found.isEmpty();
    }

    private StoredInvocationRow closeWriteInvocation(PreparationRequestVO request, StoredInvocationRow stored, ToolExecutionOutcome outcome, Instant deadline) {
        var metadata = stored.invocation();
        if (!"PREPARED".equals(metadata.state()) || metadata.dispatchEpoch() != 0 || metadata.revision() == Long.MAX_VALUE) throw conflict();
        outcome = outcome.validated(request.descriptor());
        if (outcome.status() != ToolExecutionOutcome.Status.FAILED_KNOWN || !outcome.output().isEmpty() || outcome.businessReceiptId() != null) throw unavailable();
        Instant finished = now(deadline, true).truncatedTo(ChronoUnit.MICROS); if (finished.isBefore(stored.updatedAt())) throw unavailable(); var owner = request.binding().run().actor();
        if (jdbc.update("""
                UPDATE tianji_agent.agent_tool_invocation SET state='FAILED_KNOWN',revision=revision+1,outcome=CAST(? AS jsonb),error_code=?,updated_at=?,finished_at=?,side_effect_outcome='NONE'
                WHERE tenant_id=? AND owner_type=? AND owner_issuer=? AND owner_id=? AND run_id=? AND invocation_id=?
                  AND state='PREPARED' AND revision=? AND dispatch_epoch=0 AND dispatch_nonce_sha256 IS NULL AND executor_issuer IS NULL AND executor_id IS NULL AND dispatched_at IS NULL
                  AND risk='IDEMPOTENT_WRITE' AND intent_id=?
                """, codec.outcome(request.descriptor(), outcome), outcome.errorCode(), Timestamp.from(finished), Timestamp.from(finished),
                owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), request.binding().run().runId(), request.invocationId(), metadata.revision(), UUID.fromString(request.action().intentId())) != 1) throw conflict();
        return read(request, deadline, true).orElseThrow(ToolInvocationSql::unavailable);
    }

    private static boolean sameDispatch(ToolActionIntentSql.DispatchVO dispatch, StoredInvocationRow stored) {
        var metadata = stored.invocation();
        return dispatch.runId().equals(metadata.runId()) && dispatch.invocationId().equals(metadata.invocationId()) && dispatch.epoch() == metadata.dispatchEpoch()
                && dispatch.nonceSha256().equals(stored.dispatchNonceSha256()) && dispatch.executor().equals(stored.executor());
    }

    private static void requireAttempt(StoredInvocationRow stored, DispatchAttemptVO attempt) {
        var metadata = stored.invocation();
        if (!metadata.runId().equals(attempt.runId()) || !metadata.invocationId().equals(attempt.invocationId()) || metadata.dispatchEpoch() != attempt.dispatchEpoch()
                || metadata.capabilityIssueEpoch() != attempt.capabilityIssueEpoch() || !Objects.equals(stored.dispatchNonceSha256(), attempt.dispatchNonceSha256())
                || !Objects.equals(stored.executor(), attempt.executor()) || !stored.authorizationAudit().equals(attempt.authorizationAudit())
                || !Objects.equals(metadata.dispatchedAt(), attempt.dispatchedAt()) || attempt.callDeadlineAt().isAfter(metadata.dispatchNotAfter())
                || attempt.authorizationAudit().expiresAt().isBefore(attempt.callDeadlineAt())) throw conflict();
    }

    private ToolExecutionGrant.EvidenceVO currentEvidence(PreparationRequestVO request, ToolExecutionGrant.EvidenceVO expected, Instant deadline) {
        var current = capabilities.currentInTransaction(request.binding(), request.descriptor()).orElseThrow(ToolInvocationSql::unavailable);
        if (!current.equals(expected) || !current.expiresAt().isAfter(now(deadline))) throw unavailable(); return current;
    }
    private void approvedCallBounds(PreparationRequestVO request, ToolActionIntentSql.Row action,
            ToolExecutionGrant.EvidenceVO evidence, ToolExecutionAuthorization.AuditVO audit, VerifiedExecutor executor,
            ApprovedObservation approval, RunSourceVO live, Instant dispatched, Instant callDeadline, Instant storageDeadline) {
        Instant databaseNow = now(storageDeadline);
        if (!callDeadline.isAfter(dispatched) || request.binding().run().deadlineAt().isBefore(callDeadline)
                || evidence.expiresAt().isBefore(callDeadline) || executor.expiresAt().isBefore(callDeadline)
                || approval.expiresAt().isBefore(callDeadline) || action.expiresAt().isBefore(callDeadline)
                || dispatched.plus(live.toolTimeout()).isBefore(callDeadline)
                || audit == null || audit.boundary() != ToolExecutionAuthorization.Boundary.DISPATCH
                || audit.decision() != ToolExecutionAuthorization.Decision.ALLOW
                || audit.subjectType() != request.binding().run().actor().subjectType()
                || !evidence.grantId().equals(audit.grantId()) || audit.checkedAt().isAfter(databaseNow)
                || audit.expiresAt().isBefore(callDeadline) || audit.expiresAt().isAfter(evidence.expiresAt())) throw unavailable();
    }
    private void callBounds(PreparationRequestVO request, StoredInvocationRow stored, ToolExecutionGrant.EvidenceVO evidence, ToolExecutionAuthorization.AuditVO audit,
                            VerifiedExecutor executor, Instant callDeadline, RunSourceVO live, Instant authorityDeadline) {
        Instant bound = ToolInvocationStore.dispatchCallDeadline(request, stored.invocation().dispatchNotAfter(), evidence.expiresAt(), executor.expiresAt());
        if (!bound.equals(callDeadline) || !callDeadline.isAfter(now(authorityDeadline)) || audit.expiresAt().isBefore(callDeadline)
                || !evidence.grantId().equals(audit.grantId()) || audit.expiresAt().isAfter(evidence.expiresAt())
                || stored.invocation().dispatchNotAfter().isAfter(stored.invocation().createdAt().plus(live.toolTimeout()))) throw unavailable();
    }
    private boolean identityRun(PreparationRequestVO request, Instant deadline) {
        now(deadline, true); var owner = request.binding().run().actor();
        var matches = jdbc.query("SELECT run_id FROM tianji_agent.agent_run WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND run_id=? FOR UPDATE",
                (rs, row) -> rs.getString("run_id"), owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), request.binding().run().runId());
        now(deadline, true); if (matches.size() > 1) throw unavailable(); return !matches.isEmpty();
    }
    private static void sameMetadata(RecorderPreparationVO expected, RecorderPreparationVO actual) {
        if (expected != null && (!expected.runId().equals(actual.runId()) || !expected.invocationId().equals(actual.invocationId()) || !expected.reservationId().equals(actual.reservationId())
                || expected.capabilityIssueEpoch() != actual.capabilityIssueEpoch() || !expected.createdAt().equals(actual.createdAt()) || !expected.dispatchNotAfter().equals(actual.dispatchNotAfter()))) throw conflict();
    }
    private static RecordedResultVO result(StoredInvocationRow row) { return new RecordedResultVO(row.invocation(), row.outcome(), row.budget()); }

    private RunSourceVO requireRun(PreparationRequestVO request, Instant deadline) {
        now(deadline); var run = request.binding().run(); var owner = run.actor(); var lease = request.lease();
        var expiries = jdbc.query("""
                SELECT lease_expires_at FROM tianji_agent.agent_run
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND run_id=?
                  AND status='RUNNING' AND lease_owner=? AND lease_epoch=? AND lease_expires_at>clock_timestamp() AND deadline_at>clock_timestamp()
                  AND cancel_requested_at IS NULL FOR UPDATE
                """, (rs, row) -> rs.getTimestamp("lease_expires_at").toInstant(), owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), run.runId(), lease.owner(), lease.epoch());
        Instant checked = now(deadline);
        if (expiries.size() != 1 || !expiries.getFirst().isAfter(checked) || !run.deadlineAt().isAfter(checked)) throw leaseLost();
        AgentLedgerService.ExecutionMaterial material;
        try { material = ledger.executionMaterial(owner, new GetAgentRunQuery(run.runId())); }
        catch (RuntimeException privateSource) { throw unavailable(); }
        if (material == null || !run.equals(material.authorization()) || material.snapshot() == null) throw unavailable();
        var policy = material.snapshot().effectivePolicy();
        if (!material.snapshot().definition().dependencies().tools().contains(request.descriptor().reference())
                || !policy.allowedToolVersions().contains(request.descriptor().versionKey()) || policy.maxToolCalls() < 1
                || request.descriptor().policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED && !Objects.equals(policy.approvalPolicyRef(), request.descriptor().policy().approvalPolicyRef())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Tool is not allowed by the fixed run policy");
        }
        if (!expiries.getFirst().isAfter(now(deadline))) throw leaseLost();
        return new RunSourceVO(policy.toolTimeout());
    }

    private Optional<StoredInvocationRow> read(PreparationRequestVO request, Instant deadline, boolean historical) {
        now(deadline, historical); var owner = request.binding().run().actor();
        var rows = jdbc.query("""
                SELECT i.*,b.lease_epoch AS budget_lease_epoch,b.kind AS budget_kind,b.tool_version AS budget_tool_version,
                       b.input_tokens AS budget_input_tokens,b.output_tokens AS budget_output_tokens,b.estimated_cost AS budget_estimated_cost,
                       b.state AS budget_state,b.actual_input_tokens AS budget_actual_input_tokens,b.actual_output_tokens AS budget_actual_output_tokens,b.actual_cost AS budget_actual_cost
                FROM tianji_agent.agent_tool_invocation i
                JOIN tianji_agent.agent_budget_reservation b ON b.tenant_id=i.tenant_id AND b.run_id=i.run_id AND b.reservation_id=i.reservation_id
                WHERE i.tenant_id=? AND i.owner_type=? AND i.owner_issuer=? AND i.owner_id=? AND i.run_id=? AND i.invocation_id=? FOR UPDATE OF i,b
                """, (rs, number) -> row(rs, request), owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), request.binding().run().runId(), request.invocationId());
        now(deadline, historical); if (rows.size() > 1) throw unavailable(); return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private StoredInvocationRow row(ResultSet rs, PreparationRequestVO request) throws SQLException {
        try {
            var owner = new TrustedAgentActor(rs.getLong("tenant_id"), TrustedAgentActor.SubjectType.valueOf(rs.getString("owner_type")), rs.getString("owner_id"), rs.getString("owner_issuer"));
            if (!owner.equals(request.binding().run().actor()) || !request.binding().run().runId().equals(rs.getString("run_id"))) throw unavailable();
            if (!request.invocationId().equals(rs.getObject("invocation_id", UUID.class)) || !request.reservationId().equals(rs.getString("reservation_id"))
                    || request.lease().epoch() != rs.getLong("lease_epoch") || !request.descriptor().key().equals(rs.getString("tool_key"))
                    || request.descriptor().version() != rs.getLong("tool_version") || !request.descriptor().reference().contentSha256().equals(rs.getString("tool_sha256"))
                    || !request.descriptor().policy().bindingSha256().equals(rs.getString("binding_sha256")) || !request.parametersSha256().equals(rs.getString("parameters_sha256"))
                    || !request.descriptor().policy().risk().name().equals(rs.getString("risk"))
                    || !Objects.equals(request.action() == null ? null : UUID.fromString(request.action().intentId()), rs.getObject("intent_id", UUID.class))
                    || !UUID.fromString(request.binding().capabilityId()).equals(rs.getObject("capability_id", UUID.class))
                    || !request.binding().bindingSha256().equals(rs.getString("capability_binding_sha256"))
                    || !sameCost(request.estimatedCost(), rs.getBigDecimal("budget_estimated_cost"))) throw conflict();
            var metadata = new RecorderPreparationVO(request.binding().run().runId(), request.invocationId(), request.reservationId(), rs.getString("state"), rs.getLong("revision"),
                    rs.getLong("dispatch_epoch"), rs.getLong("capability_issue_epoch"), time(rs, "created_at"), time(rs, "dispatch_not_after"), time(rs, "dispatched_at"), time(rs, "finished_at"));
            Instant updated = time(rs, "updated_at");
            if (updated == null || updated.isBefore(metadata.createdAt()) || metadata.dispatchNotAfter().isAfter(request.binding().run().deadlineAt())
                    || metadata.dispatchedAt() != null && updated.isBefore(metadata.dispatchedAt()) || metadata.finishedAt() != null && updated.isBefore(metadata.finishedAt())) throw unavailable();
            var audit = codec.audit(rs.getString("authorization_audit"));
            if (audit.decision() != ToolExecutionAuthorization.Decision.ALLOW || audit.subjectType() != owner.subjectType()
                    || !audit.expiresAt().isAfter(metadata.dispatchEpoch() == 0 ? metadata.createdAt() : metadata.dispatchedAt())
                    || audit.boundary() != (metadata.dispatchEpoch() == 0 ? ToolExecutionAuthorization.Boundary.PREPARE : ToolExecutionAuthorization.Boundary.DISPATCH)) throw unavailable();
            String nonce = rs.getString("dispatch_nonce_sha256"), executorIssuer = rs.getString("executor_issuer"), executorId = rs.getString("executor_id");
            TrustedAgentActor executor = null;
            if (metadata.dispatchEpoch() == 0) {
                if (nonce != null || executorIssuer != null || executorId != null) throw unavailable();
            } else {
                if (nonce == null || !nonce.matches("[0-9a-f]{64}") || executorIssuer == null || !executorIssuer.matches("[0-9a-f]{64}") || executorId == null) throw unavailable();
                executor = new TrustedAgentActor(owner.tenantId(), TrustedAgentActor.SubjectType.SERVICE, executorId, executorIssuer);
            }
            var budget = budget(rs, request);
            String encoded = rs.getString("outcome"); var outcome = encoded == null ? null : codec.outcome(encoded, request.descriptor());
            if (!Objects.equals(rs.getString("business_receipt_id"), outcome == null ? null : outcome.businessReceiptId())
                    || !Objects.equals(rs.getString("error_code"), outcome == null ? null : outcome.errorCode())) throw unavailable();
            var stored = new StoredInvocationRow(request, metadata, budget, audit, nonce, executor, rs.getString("side_effect_outcome"), outcome, updated);
            requireLifecycle(stored); return stored;
        } catch (AgentLedgerException scopedFailure) { throw scopedFailure; }
        catch (RuntimeException privateContent) { throw unavailable(); }
    }

    private AgentBudgetReservation budget(ResultSet rs, PreparationRequestVO request) throws SQLException {
        if (rs.getLong("budget_lease_epoch") != request.lease().epoch() || !"TOOL".equals(rs.getString("budget_kind"))
                || !request.descriptor().versionKey().equals(rs.getString("budget_tool_version")) || rs.getLong("budget_input_tokens") != 0 || rs.getLong("budget_output_tokens") != 0) throw unavailable();
        var state = AgentBudgetReservation.State.valueOf(rs.getString("budget_state"));
        Long input = rs.getObject("budget_actual_input_tokens", Long.class), output = rs.getObject("budget_actual_output_tokens", Long.class); var cost = rs.getBigDecimal("budget_actual_cost");
        new AgentBudgetSettlement(input, output, cost);
        if (state == AgentBudgetReservation.State.RESERVED && (input != null || output != null || cost != null)
                || state == AgentBudgetReservation.State.SETTLED && (!Long.valueOf(0).equals(input) || !Long.valueOf(0).equals(output) || cost == null
                || request.estimatedCost() != null && cost.compareTo(request.estimatedCost()) > 0)) throw unavailable();
        return new AgentBudgetReservation(request.reservationId(), request.lease().epoch(), AgentBudgetRequest.Kind.TOOL, request.descriptor().versionKey(), 0, 0,
                request.estimatedCost(), state, input, output, cost, false);
    }

    private void requireLifecycle(StoredInvocationRow row) {
        var metadata = row.invocation(); var risk = row.identity().descriptor().policy().risk(); var outcome = row.outcome();
        if (outcome != null && !metadata.state().equals(outcome.status().name())) throw unavailable();
        String effect = risk == ToolPolicy.Risk.READ_ONLY ? "NONE" : "UNKNOWN";
        switch (metadata.state()) {
            case "PREPARED" -> {
                if (outcome != null || !"NONE".equals(row.sideEffectOutcome()) || risk == ToolPolicy.Risk.APPROVAL_REQUIRED
                        || risk != ToolPolicy.Risk.READ_ONLY && row.budget().state() != AgentBudgetReservation.State.RESERVED) throw unavailable();
            }
            case "AWAITING_APPROVAL" -> {
                var waiting = new ToolExecutionOutcome(ToolExecutionOutcome.Status.AWAITING_APPROVAL, Map.of(), null, null, BigDecimal.ZERO);
                if (risk != ToolPolicy.Risk.APPROVAL_REQUIRED || !sameOutcome(waiting, outcome) || !"NONE".equals(row.sideEffectOutcome())
                        || row.budget().state() != AgentBudgetReservation.State.SETTLED || !Long.valueOf(0).equals(row.budget().actualInputTokens())
                        || !Long.valueOf(0).equals(row.budget().actualOutputTokens()) || !sameCost(BigDecimal.ZERO, row.budget().actualCost())) throw unavailable();
            }
            case "DISPATCHING" -> { if (outcome != null || !effect.equals(row.sideEffectOutcome())) throw unavailable(); }
            case "SUCCEEDED" -> { if (outcome == null || !(risk == ToolPolicy.Risk.READ_ONLY ? "NONE" : "CONFIRMED").equals(row.sideEffectOutcome())) throw unavailable(); }
            case "REJECTED", "FAILED_KNOWN" -> { if (outcome == null || !"NONE".equals(row.sideEffectOutcome())) throw unavailable(); }
            case "UNKNOWN" -> { if (outcome == null || !effect.equals(row.sideEffectOutcome())) throw unavailable(); }
            default -> throw unavailable();
        }
    }

    private void requirePreparationState(PreparationRequestVO request, ToolExecutionGrant.EvidenceVO evidence, RunSourceVO live, ToolActionIntentSql.Row action, StoredInvocationRow row) {
        var metadata = row.invocation();
        if (!Set.of("PREPARED", "AWAITING_APPROVAL").contains(metadata.state())) throw unavailable();
        if (evidence.reservation().issueEpoch() != metadata.capabilityIssueEpoch()) throw conflict();
        if (!metadata.dispatchNotAfter().equals(dispatchDeadline(metadata.createdAt(), live.toolTimeout(), request, evidence, action))
                || !evidence.grantId().equals(row.authorizationAudit().grantId()) || row.authorizationAudit().expiresAt().isAfter(evidence.expiresAt())
                || "PREPARED".equals(metadata.state()) && row.budget().state() != AgentBudgetReservation.State.RESERVED) throw unavailable();
    }

    private Instant now(Instant deadline) { return now(deadline, false); }
    private Instant now(Instant deadline, boolean historical) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) throw unavailable();
        if ((!historical && Thread.currentThread().isInterrupted()) || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
        Timestamp value = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class);
        if (value == null || deadline == null || !deadline.isAfter(value.toInstant())) throw unavailable(); return value.toInstant();
    }

    record StoredInvocationRow(PreparationRequestVO identity, RecorderPreparationVO invocation, AgentBudgetReservation budget, ToolExecutionAuthorization.AuditVO authorizationAudit,
                               String dispatchNonceSha256, TrustedAgentActor executor, String sideEffectOutcome, ToolExecutionOutcome outcome, Instant updatedAt) {
        @Override public String toString() { return "ToolInvocationSql.StoredInvocationRow[redacted]"; }
    }
    record PreparedRowsVO(StoredInvocationRow invocation, ToolActionIntentStore.PreparedActionResult action, boolean newlyCreated) { }

    private static Instant dispatchDeadline(Instant created, Duration timeout, PreparationRequestVO request, ToolExecutionGrant.EvidenceVO evidence, ToolActionIntentSql.Row action) {
        Instant deadline = created.plus(timeout);
        if (request.binding().run().deadlineAt().isBefore(deadline)) deadline = request.binding().run().deadlineAt();
        if (evidence.expiresAt().isBefore(deadline)) deadline = evidence.expiresAt();
        if (action != null && action.expiresAt().isBefore(deadline)) deadline = action.expiresAt();
        return deadline.truncatedTo(ChronoUnit.MICROS);
    }
    private static ToolActionIntentStore.PreparedActionResult projection(ToolActionIntentSql.Row row) {
        return row == null ? null : new ToolActionIntentStore.PreparedActionResult(row.action().intentId(), row.action().requestSha256(), row.action().tool(), row.descriptor().policy().risk(),
                row.state(), row.revision(), row.originRunId(), row.createdAt(), row.expiresAt());
    }
    private static Instant time(ResultSet rs, String name) throws SQLException { var value = rs.getTimestamp(name); return value == null ? null : value.toInstant(); }
    private static boolean sameOutcome(ToolExecutionOutcome left, ToolExecutionOutcome right) {
        return left != null && right != null && left.status() == right.status() && left.output().equals(right.output()) && Objects.equals(left.businessReceiptId(), right.businessReceiptId())
                && Objects.equals(left.errorCode(), right.errorCode()) && sameCost(left.actualCost(), right.actualCost());
    }
    private static boolean sameCost(BigDecimal left, BigDecimal right) { return left == null ? right == null : right != null && left.compareTo(right) == 0; }
    private record RunSourceVO(Duration toolTimeout) { }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Tool invocation source or storage is unavailable"); }
    private static AgentLedgerException conflict() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Tool invocation identity was already used for different values"); }
    private static AgentLedgerException leaseLost() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Tool invocation run lease is no longer valid"); }
}
