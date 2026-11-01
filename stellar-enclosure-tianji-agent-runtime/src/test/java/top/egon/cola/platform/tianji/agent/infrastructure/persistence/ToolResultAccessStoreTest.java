package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore.DisclosureWindowVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore.StartResultAccessRequestVO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real access Store/SQL/Spring/BudgetService over controlled JDBC.
 * Historical sources are prepared by the existing write Rig; its Action participant is mocked.
 * This does not prove physical PostgreSQL acceptance, remote permissions, or transport execution. */
class ToolResultAccessStoreTest {
    private static final Instant NOW = ToolInvocationStoreTest.NOW;
    private static final String RUN = ToolInvocationStoreTest.RUN, OTHER_RUN = "10000000-0000-0000-0000-000000000002";
    private static final TrustedAgentActor ACTOR = ToolInvocationStoreTest.ACTOR;
    private static final String HASH = ToolInvocationStoreTest.HASH, RECEIPT = "tianji:ps-start:v1:" + HASH, INSTANCE = "private-instance-id";
    private static final ToolDescriptor TOOL = tool(ToolPolicy.Risk.IDEMPOTENT_WRITE, ProcessClient.START_RESOURCE_KEY, true);

    @Test void first_access_commits_original_result_and_its_own_zero_budget_in_lock_order() throws Exception {
        try (var scene = new Scene()) {
            var source = scene.rig.base.intentRow.get(); var sourceBudget = scene.rig.base.budgetRows.get(scene.source.reservationId());
            var sourceRow = new HashMap<>(scene.rig.row(scene.source)); scene.rig.base.trace.clear();
            scene.requireInsertOnlyAccess.set(true);
            var result = scene.read(scene.request);
            assertEquals(RUN, result.runId()); assertEquals(scene.request.accessId(), result.accessId());
            assertEquals(source.action().intentId(), result.actionIntentId()); assertEquals(source.revision(), result.sourceActionRevision());
            assertEquals(source.outcome(), result.sourceOutcome()); assertEquals(new BigDecimal("0.0300"), result.sourceOutcome().actualCost());
            assertEquals(sourceSha(source), result.sourceResultSha256()); assertEquals(RECEIPT, result.sourceOutcome().businessReceiptId());
            assertEquals(scene.request.reservationId(), result.accessBudget().reservationId()); assertEquals(BigDecimal.ZERO, result.accessBudget().estimatedCost());
            assertEquals(AgentBudgetReservation.State.RESERVED, result.accessBudget().state()); assertNull(result.accessBudget().actualCost());
            assertSame(source, scene.rig.base.intentRow.get()); assertEquals(sourceBudget, scene.rig.base.budgetRows.get(scene.source.reservationId()));
            assertEquals(sourceRow, scene.rig.row(scene.source)); assertEquals(1, scene.accesses.size()); assertEquals(2, scene.rig.base.budgetInserts.get());
            var trace = scene.rig.base.trace;
            assertTrue(trace.indexOf("access-run-lock") < trace.indexOf("cap-in")); assertTrue(trace.indexOf("cap-in") < trace.indexOf("access-action-lock"));
            assertTrue(trace.indexOf("access-action-lock") < trace.indexOf("budget-insert")); assertTrue(trace.indexOf("budget-insert") < trace.indexOf("access-insert"));
            assertTrue(trace.indexOf("access-insert") < trace.lastIndexOf("commit"));
            assertFalse(result.toString().contains(INSTANCE)); assertFalse(result.toString().contains(RECEIPT));
            assertEquals(25, scene.accesses.get(scene.request.accessId()).size()); assertFalse(scene.accesses.get(scene.request.accessId()).containsKey("outcome"));
            verify(scene.rig.base.budgets, never()).settleBudget(any(), any(), anyString(), any());
        }
    }

    @Test void known_success_and_typed_negative_sources_preserve_original_nullable_and_nonzero_fees() throws Exception {
        for (var status : List.of(ToolExecutionOutcome.Status.SUCCEEDED, ToolExecutionOutcome.Status.REJECTED, ToolExecutionOutcome.Status.FAILED_KNOWN)) {
            for (BigDecimal fee : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("0.0310")}) try (var scene = new Scene(status, fee)) {
                var result = scene.read(scene.request); assertEquals(status, result.sourceOutcome().status()); assertEquals(fee, result.sourceOutcome().actualCost());
                assertEquals(BigDecimal.ZERO, result.accessBudget().estimatedCost());
                if (status != ToolExecutionOutcome.Status.SUCCEEDED) { assertEquals(Map.of(), result.sourceOutcome().output()); assertNull(result.sourceOutcome().businessReceiptId()); }
                verify(scene.rig.base.budgets, never()).settleBudget(any(), any(), anyString(), any());
            }
        }
    }

    @Test void a_different_current_run_can_access_the_same_owned_source_without_rebinding_its_history() throws Exception {
        try (var scene = new Scene()) {
            var original = scene.rig.base.intentRow.get(); var originalBudget = scene.rig.base.budgetRows.get(scene.source.reservationId());
            var request = new StartResultAccessRequestVO(scene.rig.binding(OTHER_RUN), new AgentRunLease(OTHER_RUN, "worker", 1, NOW.minusSeconds(1)), TOOL, scene.source.action(), UUID.randomUUID());
            var result = scene.read(request); assertEquals(OTHER_RUN, result.runId()); assertEquals(original.action().intentId(), result.actionIntentId());
            assertEquals(original.outcome(), result.sourceOutcome()); assertSame(original, scene.rig.base.intentRow.get());
            assertEquals(originalBudget, scene.rig.base.budgetRows.get(scene.source.reservationId())); assertEquals(OTHER_RUN, scene.accesses.get(request.accessId()).get("run_id"));
            assertNotEquals(scene.source.reservationId(), result.accessBudget().reservationId());
        }
    }

    @Test void request_profile_identity_and_value_guards_are_closed_without_new_caller_cost_or_expiry() throws Exception {
        try (var scene = new Scene()) {
            assertEquals(5, StartResultAccessRequestVO.class.getRecordComponents().length);
            assertEquals("tool:" + scene.request.accessId(), scene.request.reservationId());
            assertEquals(TOOL.parametersSha256(scene.request.action().arguments()), scene.request.parametersSha256());
            assertNotEquals(scene.request.action().requestSha256(), scene.request.parametersSha256());
            for (var descriptor : List.of(tool(ToolPolicy.Risk.APPROVAL_REQUIRED, ProcessClient.START_RESOURCE_KEY, true),
                    tool(ToolPolicy.Risk.IDEMPOTENT_WRITE, "other.write", true), tool(ToolPolicy.Risk.IDEMPOTENT_WRITE, ProcessClient.START_RESOURCE_KEY, false))) {
                var action = new ToolActionRequest(ACTOR, descriptor, "invoice-1", "v1", Map.of("amount", 20L));
                assertThrows(IllegalArgumentException.class, () -> new StartResultAccessRequestVO(scene.request.binding(), scene.request.lease(), descriptor, action, UUID.randomUUID()));
            }
            assertThrows(IllegalArgumentException.class, () -> new StartResultAccessRequestVO(scene.request.binding(), scene.request.lease(), TOOL, null, UUID.randomUUID()));
            assertThrows(IllegalArgumentException.class, () -> new StartResultAccessRequestVO(scene.request.binding(), new AgentRunLease(OTHER_RUN, "worker", 1, NOW), TOOL, scene.request.action(), UUID.randomUUID()));
            assertThrows(IllegalArgumentException.class, () -> new StartResultAccessRequestVO(scene.request.binding(), scene.request.lease(), TOOL,
                    new ToolActionRequest(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "other", "https://idp.example/20"), TOOL, "invoice-1", "v1", Map.of("amount", 20L)), UUID.randomUUID()));
            assertThrows(IllegalArgumentException.class, () -> new StartResultAccessRequestVO(scene.request.binding(), scene.request.lease(), TOOL, scene.request.action(), null));
            assertEquals(8, ToolResultAccessStore.RecordedAccessVO.class.getRecordComponents().length);
            assertThrows(IllegalArgumentException.class, () -> new ToolResultAccessStore.RecordedAccessVO(RUN, scene.request.accessId(), scene.request.action().intentId(), 0, HASH, success(null),
                    scene.zeroBudget(scene.request), new DisclosureWindowVO(NOW.plusSeconds(10), 10_000_000_000L)));
        }
    }

    @Test void readiness_exact_affinities_and_ambient_transactions_are_checked_before_disclosure() throws Exception {
        try (var scene = new Scene()) {
            assertTrue(scene.store.usesDataSource(scene.rig.base.source)); assertFalse(scene.store.usesDataSource(mock(DataSource.class)));
            assertTrue(scene.store.usesLedger(scene.rig.base.ledger)); assertTrue(scene.store.usesCapabilities(scene.rig.base.capabilities)); assertTrue(scene.store.usesAuthorization(scene.rig.base.authority));
            assertFalse(scene.store.usesLedger(mock(AgentLedgerService.class))); assertFalse(scene.store.usesCapabilities(mock(ToolCapabilityStore.class)));
            assertFalse(scene.store.usesAuthorization(mock(ToolExecutionAuthorization.class)));
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.store.recordKnownStartResultAccess(scene.request));
            assertTrue(scene.accesses.isEmpty()); scene.store.verifyStorage();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertThrows(IllegalStateException.class, () -> scene.store.recordKnownStartResultAccess(scene.request)); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            var verifier = scene.rig.base.verifiers.constructed().getLast(); verify(verifier).verifyResultAccess(); verify(verifier, never()).verify();
        }
        try (var scene = new Scene()) {
            when(scene.rig.base.ledger.usesDataSource(scene.rig.base.source)).thenReturn(false);
            assertThrows(IllegalArgumentException.class, () -> new ToolResultAccessStore(scene.rig.base.source, scene.rig.base.ledger, scene.rig.base.capabilities, scene.rig.base.authority));
        }
    }

    @Test void every_duplicate_uses_fresh_reconcile_but_keeps_the_first_capability_audit_and_fact() throws Exception {
        try (var scene = new Scene()) {
            var first = scene.read(scene.request); var original = new HashMap<>(scene.accesses.get(scene.request.accessId()));
            var old = scene.rig.evidence(RUN); scene.rig.evidences.put(RUN, new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(old.reservation().binding(), 4),
                    "9" + "2".repeat(62), "ENC:v1:key:B:sealed", old.expiresAt()));
            scene.rig.base.wall.set(NOW.plusSeconds(1)); scene.rig.base.databaseNow.set(NOW.plusSeconds(1)); scene.rig.base.nano.set(1_000_000_000L);
            clearInvocations(scene.rig.base.authority, scene.rig.base.capabilities, scene.rig.base.budgets);
            var replay = scene.read(scene.request); assertEquals(first.sourceOutcome(), replay.sourceOutcome()); assertEquals(original, scene.accesses.get(scene.request.accessId()));
            assertEquals(NOW.plusSeconds(11), replay.window().deadlineAt()); assertEquals(1, scene.accesses.size()); assertEquals(2, scene.rig.base.budgetInserts.get());
            verify(scene.rig.base.authority).check(any(), eq(ToolExecutionAuthorization.Boundary.RECONCILE));
            verify(scene.rig.base.capabilities).current(scene.request.binding(), TOOL); verify(scene.rig.base.capabilities).currentInTransaction(scene.request.binding(), TOOL);
            verify(scene.rig.base.budgets, never()).reserveBudget(any(), any(), any()); verify(scene.rig.base.budgets, never()).settleBudget(any(), any(), anyString(), any());
            scene.check.set(value -> new ToolExecutionAuthorization.CheckResult(value, ToolExecutionAuthorization.Boundary.RECONCILE, ToolExecutionAuthorization.Decision.DENY, null));
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, () -> scene.read(scene.request)); assertEquals(original, scene.accesses.get(scene.request.accessId()));
        }
    }

    @Test void full_quota_allows_exact_replay_without_reserve_and_rejects_a_new_access() throws Exception {
        try (var scene = new Scene()) {
            scene.read(scene.request); clearInvocations(scene.rig.base.budgets);
            doReturn(0).when(scene.rig.base.budgetRepository).reserveBudget(eq(ACTOR), any(), any(), any());
            doReturn(new JdbcAgentLedgerRepository.BudgetCounters(0, 5, 0, 0, 0, 0, BigDecimal.ZERO)).when(scene.rig.base.budgetRepository).budgetCounters(eq(ACTOR), anyString());
            assertNotNull(scene.read(scene.request)); verify(scene.rig.base.budgets, never()).reserveBudget(any(), any(), any());
            var other = scene.request(UUID.randomUUID()); assertCode(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED, () -> scene.read(other));
            assertEquals(1, scene.accesses.size()); assertFalse(scene.rig.base.budgetRows.containsKey(other.reservationId())); assertEquals(2, scene.rig.base.budgetInserts.get());
        }
    }

    @Test void host_ids_cannot_adopt_regular_invocations_or_orphan_or_cross_type_budgets() throws Exception {
        try (var scene = new Scene()) {
            var colliding = scene.request(scene.source.invocationId()); var original = scene.rig.base.budgetRows.get(scene.source.reservationId());
            assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, () -> scene.read(colliding));
            assertEquals(original, scene.rig.base.budgetRows.get(scene.source.reservationId())); assertTrue(scene.accesses.isEmpty());
        }
        for (boolean model : List.of(false, true)) try (var scene = new Scene()) {
            var request = scene.request; var orphan = new AgentBudgetReservation(request.reservationId(), request.lease().epoch(), model ? AgentBudgetRequest.Kind.MODEL : AgentBudgetRequest.Kind.TOOL,
                    model ? null : TOOL.versionKey(), 0, 0, BigDecimal.ZERO, AgentBudgetReservation.State.RESERVED, null, null, null, false);
            scene.rig.base.budgetRows.put(request.reservationId(), orphan);
            assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, () -> scene.read(request)); assertSame(orphan, scene.rig.base.budgetRows.get(request.reservationId())); assertTrue(scene.accesses.isEmpty());
        }
    }

    @Test void insert_failure_and_post_insert_timeout_roll_back_the_access_budget_and_counters() throws Exception {
        for (boolean timeout : List.of(false, true)) try (var scene = new Scene()) {
            if (timeout) scene.afterInsert.set(() -> { scene.rig.base.nano.set(10_000_000_001L); scene.rig.base.wall.set(NOW.minusSeconds(30)); });
            else scene.failInsert.set(true);
            var source = scene.rig.base.intentRow.get(); var budget = scene.rig.base.budgetRows.get(scene.source.reservationId());
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.read(scene.request));
            assertTrue(scene.accesses.isEmpty()); assertFalse(scene.rig.base.budgetRows.containsKey(scene.request.reservationId())); assertEquals(1, scene.rig.base.budgetInserts.get());
            assertSame(source, scene.rig.base.intentRow.get()); assertEquals(budget, scene.rig.base.budgetRows.get(scene.source.reservationId())); assertTrue(scene.rig.base.trace.contains("rollback"));
        }
    }

    @Test void caller_target_drift_conflicts_but_unexpected_source_revision_or_digest_drift_is_unavailable() throws Exception {
        try (var scene = new Scene()) {
            scene.read(scene.request);
            var changedAction = new ToolActionRequest(ACTOR, TOOL, "another-invoice", "v1", Map.of("amount", 20L));
            var changed = new StartResultAccessRequestVO(scene.request.binding(), scene.request.lease(), TOOL, changedAction, scene.request.accessId());
            scene.missingAction.set(true);
            assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, () -> scene.read(changed)); assertEquals(1, scene.accesses.size());
        }
        for (boolean revision : List.of(false, true)) try (var scene = new Scene()) {
            scene.read(scene.request); var old = scene.rig.base.intentRow.get();
            scene.replaceSource(revision ? old.outcome() : success(new BigDecimal("0.0400")), old.revision() + (revision ? 1 : 0));
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.read(scene.request)); assertEquals(1, scene.accesses.size());
        }
    }

    @Test void unknown_nonterminal_missing_and_malformed_start_sources_never_disclose_or_consume_access_quota() throws Exception {
        for (String state : List.of("PREPARED", "DISPATCHING", "UNKNOWN", "EXPIRED", "missing", "bad-receipt", "bad-instance")) try (var scene = new Scene()) {
            if (state.equals("missing")) scene.missingAction.set(true);
            else if (state.equals("bad-receipt")) scene.replaceSource(new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", INSTANCE), "unbound-receipt", null, null), 3);
            else if (state.equals("bad-instance")) scene.replaceSource(new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", " " + INSTANCE + " "), RECEIPT, null, null), 3);
            else scene.seedState(state);
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.read(scene.request)); assertTrue(scene.accesses.isEmpty()); assertEquals(1, scene.rig.base.budgetInserts.get());
        }
    }

    @Test void reconcile_uses_independent_parameter_hash_and_rejects_bad_echo_decision_audit_or_grant() throws Exception {
        for (String change : List.of("deny", "unavailable", "legacy", "echo", "boundary", "grant", "stale", "future", "expiry")) try (var scene = new Scene()) {
            scene.check.set(request -> {
                var allowed = scene.allowed(request); var audit = allowed.audit();
                if (change.equals("deny") || change.equals("unavailable")) return new ToolExecutionAuthorization.CheckResult(request, ToolExecutionAuthorization.Boundary.RECONCILE,
                        change.equals("deny") ? ToolExecutionAuthorization.Decision.DENY : ToolExecutionAuthorization.Decision.UNAVAILABLE, null);
                if (change.equals("legacy")) return new ToolExecutionAuthorization.CheckResult(request, ToolExecutionAuthorization.Boundary.RECONCILE, ToolExecutionAuthorization.Decision.ALLOW, null);
                if (change.equals("echo")) return scene.allowed(new ToolExecutionAuthorization.Request(request.run(), request.lease(), request.tool(), request.bindingSha256(), request.parametersSha256(), null, request.deadlineAt()));
                if (change.equals("boundary")) return scene.rig.allowed(request, ToolExecutionAuthorization.Boundary.DISPATCH);
                String grant = change.equals("grant") ? "9" + "3".repeat(62) : audit.grantId();
                Instant checked = change.equals("stale") ? NOW.minusNanos(1) : change.equals("future") ? NOW.plusNanos(1) : audit.checkedAt();
                Instant expires = change.equals("expiry") ? NOW.plusSeconds(26) : audit.expiresAt();
                return new ToolExecutionAuthorization.CheckResult(request, ToolExecutionAuthorization.Boundary.RECONCILE, ToolExecutionAuthorization.Decision.ALLOW,
                        new ToolExecutionAuthorization.AuditVO(1, audit.localCheckId(), audit.boundary(), audit.decision(), audit.subjectType(), grant, audit.authVersion(), audit.catalogVersion(), audit.policyVersion(), checked, expires));
            });
            assertCode(change.equals("deny") ? AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED : AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.read(scene.request));
            assertTrue(scene.accesses.isEmpty()); assertEquals(1, scene.rig.base.budgetInserts.get());
        }
    }

    @Test void full_capability_equality_and_current_lease_material_are_rechecked_under_the_run_lock() throws Exception {
        for (String change : List.of("epoch", "cipher", "lease", "cancel", "material", "policy")) try (var scene = new Scene()) {
            var old = scene.rig.evidence(RUN);
            if (change.equals("epoch") || change.equals("cipher")) scene.rig.base.insideEvidence.set(new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(old.reservation().binding(), change.equals("epoch") ? 4 : 3),
                    old.grantId(), change.equals("cipher") ? "ENC:v1:key:B:changed" : old.ciphertext(), old.expiresAt()));
            if (change.equals("lease")) { scene.rig.base.databaseLease.set(NOW.plusSeconds(1)); scene.beforeRunLock.set(() -> scene.rig.base.databaseNow.set(NOW.plusSeconds(2))); }
            if (change.equals("cancel")) scene.rig.base.sourceActive.set(false);
            if (change.equals("material")) when(scene.rig.base.ledger.executionMaterial(eq(ACTOR), any())).thenReturn(null);
            if (change.equals("policy")) scene.beforeRunLock.set(() -> scene.setTimeout(Duration.ofSeconds(1)));
            var failure = assertThrows(AgentLedgerException.class, () -> scene.read(scene.request));
            assertTrue(Set.of(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED).contains(failure.code()));
            assertTrue(scene.accesses.isEmpty()); assertEquals(1, scene.rig.base.budgetInserts.get());
        }
    }

    @Test void stored_identity_audit_source_and_zero_access_budget_are_all_validated_on_exact_replay() throws Exception {
        for (String drift : List.of("owner", "params", "request", "source", "epoch", "audit", "time", "budget-kind", "budget-estimate", "budget-actual")) try (var scene = new Scene()) {
            scene.read(scene.request); var row = scene.accesses.get(scene.request.accessId());
            switch (drift) {
                case "owner" -> row.put("owner_id", "another-owner");
                case "params" -> row.put("parameters_sha256", "b".repeat(64));
                case "request" -> row.put("request_sha256", "b".repeat(64));
                case "source" -> row.put("source_result_sha256", "b".repeat(64));
                case "epoch" -> row.put("capability_issue_epoch", 0L);
                case "audit" -> row.put("authorization_audit", "{}");
                case "time" -> row.put("created_at", row.get("authorization_expires_at"));
                default -> {
                    var old = scene.rig.base.budgetRows.get(scene.request.reservationId());
                    scene.rig.base.budgetRows.put(old.reservationId(), new AgentBudgetReservation(old.reservationId(), old.leaseEpoch(), drift.equals("budget-kind") ? AgentBudgetRequest.Kind.MODEL : old.kind(),
                            drift.equals("budget-kind") ? null : old.toolVersion(), 0, 0, drift.equals("budget-estimate") ? BigDecimal.ONE : BigDecimal.ZERO,
                            drift.equals("budget-actual") ? AgentBudgetReservation.State.SETTLED : old.state(), drift.equals("budget-actual") ? 0L : null, drift.equals("budget-actual") ? 0L : null,
                            drift.equals("budget-actual") ? BigDecimal.ONE : null, false));
                }
            }
            assertThrows(AgentLedgerException.class, () -> scene.read(scene.request)); assertEquals(1, scene.accesses.size()); assertEquals(2, scene.rig.base.budgetInserts.get());
        }
    }

    @Test void original_nanosecond_proof_is_validated_before_distinct_micros_history_is_written() throws Exception {
        try (var scene = new Scene()) {
            var checked = NOW.plusNanos(123_456_789); var expiry = NOW.plusSeconds(3).plusNanos(987_654_321); scene.rig.base.wall.set(checked); scene.rig.base.databaseNow.set(checked.truncatedTo(ChronoUnit.MICROS).plusNanos(1_000));
            scene.setEvidenceExpiry(expiry); var result = scene.read(scene.request); var row = scene.accesses.get(scene.request.accessId()); var audit = new ToolExecutionJsonCodec().audit((String) row.get("authorization_audit"));
            assertEquals(checked.truncatedTo(ChronoUnit.MICROS), audit.checkedAt()); assertEquals(expiry.truncatedTo(ChronoUnit.MICROS), audit.expiresAt());
            assertEquals(Timestamp.from(audit.checkedAt()), row.get("authorization_checked_at")); assertEquals(Timestamp.from(audit.expiresAt()), row.get("authorization_expires_at"));
            assertEquals(expiry, result.window().deadlineAt()); assertTrue(audit.expiresAt().isBefore(scene.checked.get().request().deadlineAt()));
            assertEquals(scene.checked.get().audit().localCheckId(), audit.localCheckId()); assertEquals(checked, scene.checked.get().audit().checkedAt());
            assertEquals(expiry, scene.checked.get().audit().expiresAt()); assertEquals(expiry, scene.checked.get().request().deadlineAt());
        }
        for (boolean collapsed : List.of(false, true)) try (var scene = new Scene()) {
            scene.rig.base.wall.set(NOW.plusNanos(100)); scene.setEvidenceExpiry(collapsed ? NOW.plusNanos(900) : NOW.plusSeconds(1).plusNanos(900));
            scene.rig.base.databaseNow.set(collapsed ? NOW : NOW.plusSeconds(1));
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.read(scene.request)); assertTrue(scene.accesses.isEmpty()); assertEquals(1, scene.rig.base.budgetInserts.get());
        }
    }

    @Test void short_policy_timeout_and_original_wall_monotonic_limits_are_never_reset() throws Exception {
        try (var scene = new Scene()) {
            scene.setTimeout(Duration.ofMillis(100)); var result = scene.read(scene.request);
            assertEquals(NOW.plusMillis(100), result.window().deadlineAt()); assertEquals(100_000_000L, result.window().nanoDeadline());
        }
        for (String change : List.of("wall", "mono", "interrupt")) try (var scene = new Scene()) {
            scene.check.set(request -> { var result = scene.allowed(request); if (change.equals("wall")) scene.rig.base.wall.set(NOW.plusSeconds(11));
                else if (change.equals("mono")) { scene.rig.base.wall.set(NOW.minusSeconds(30)); scene.rig.base.nano.set(10_000_000_001L); }
                else Thread.currentThread().interrupt(); return result; });
            try { assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.read(scene.request)); }
            finally { Thread.interrupted(); }
            assertTrue(scene.accesses.isEmpty()); assertEquals(1, scene.rig.base.budgetInserts.get());
        }
    }

    @Test void post_commit_and_same_window_egress_failures_keep_facts_but_never_extend_disclosure() throws Exception {
        try (var scene = new Scene()) {
            scene.rig.base.afterCommit.set(() -> { scene.rig.base.wall.set(NOW.plusSeconds(11)); scene.rig.base.nano.set(11_000_000_000L); });
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.read(scene.request));
            assertEquals(1, scene.accesses.size()); assertTrue(scene.rig.base.budgetRows.containsKey(scene.request.reservationId()));
        }
        for (String change : List.of("wall", "mono", "interrupt")) try (var scene = new Scene()) {
            var result = scene.read(scene.request);
            if (change.equals("wall")) scene.rig.base.wall.set(NOW.plusSeconds(11));
            else if (change.equals("mono")) { scene.rig.base.wall.set(NOW.minusSeconds(30)); scene.rig.base.nano.set(10_000_000_001L); }
            else Thread.currentThread().interrupt();
            try { assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.store.checkDisclosureWindow(result.window())); }
            finally { Thread.interrupted(); }
            assertEquals(1, scene.accesses.size()); verify(scene.rig.base.budgets, never()).settleBudget(any(), any(), anyString(), any());
        }
    }

    @Test void primary_storage_failures_are_sanitized_without_mutating_the_original_source() throws Exception {
        for (boolean primary : List.of(false, true)) try (var scene = new Scene()) {
            if (primary) when(scene.jdbc.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class))).thenReturn(false);
            else when(scene.jdbc.queryForObject(eq("SELECT clock_timestamp()"), eq(Timestamp.class))).thenThrow(new DataIntegrityViolationException("private source details"));
            var original = scene.rig.base.intentRow.get(); var failure = assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.read(scene.request));
            assertFalse(failure.getMessage().contains("private")); assertSame(original, scene.rig.base.intentRow.get()); assertTrue(scene.accesses.isEmpty());
        }
    }

    @Test void approved_fixed_start_requests_keep_the_full_profile_owner_and_binding_contract() throws Exception {
        try (var scene = approvedScene(ToolExecutionOutcome.Status.SUCCEEDED, null)) {
            var request = assertDoesNotThrow(() -> scene.request(UUID.randomUUID()));
            assertEquals(ToolPolicy.Risk.APPROVAL_REQUIRED, request.descriptor().policy().risk());
            assertEquals(5, StartResultAccessRequestVO.class.getRecordComponents().length);
            assertEquals(scene.descriptor.parametersSha256(request.action().arguments()), request.parametersSha256());
            assertNotEquals(request.action().requestSha256(), request.parametersSha256());
            for (var descriptor : List.of(tool(ToolPolicy.Risk.APPROVAL_REQUIRED, "other.write", true),
                    tool(ToolPolicy.Risk.APPROVAL_REQUIRED, ProcessClient.START_RESOURCE_KEY, false))) {
                var binding = new ToolExecutionGrant.BindingVO(request.binding().run(), descriptor.reference(), descriptor.policy().bindingSha256(),
                        "TIANJI_TOOL_PROCESS", URI.create("https://agent.example/20"), "tool.process.execute", null);
                var action = new ToolActionRequest(ACTOR, descriptor, "invoice-1", "v1", Map.of("amount", 20L));
                assertThrows(IllegalArgumentException.class, () -> new StartResultAccessRequestVO(binding, request.lease(), descriptor, action, UUID.randomUUID()));
            }
            var foreign = new ToolActionRequest(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "other-owner", "https://idp.example/20"),
                    scene.descriptor, "invoice-1", "v1", Map.of("amount", 20L));
            assertThrows(IllegalArgumentException.class, () -> new StartResultAccessRequestVO(request.binding(), request.lease(), scene.descriptor, foreign, UUID.randomUUID()));
        }
    }

    @Test void expired_consumed_approval_keeps_known_three_proof_and_fees_while_only_the_access_budget_settles_zero() throws Exception {
        for (var status : List.of(ToolExecutionOutcome.Status.SUCCEEDED, ToolExecutionOutcome.Status.REJECTED, ToolExecutionOutcome.Status.FAILED_KNOWN)) {
            for (BigDecimal fee : new BigDecimal[]{null, new BigDecimal("0.0310"), new BigDecimal("2.5000")}) try (var scene = approvedScene(status, fee)) {
                var request = scene.request(UUID.randomUUID()); var original = scene.rig.base.intentRow.get();
                var sourceRow = new HashMap<>(scene.rig.row(scene.source)); var sourceBudget = scene.rig.base.budgetRows.get(scene.source.reservationId());
                var codec = new ToolExecutionJsonCodec(); String proof = codec.approval(original.approval().proof(), original.action());
                scene.advancePastApproval(); assertTrue(original.approval().proof().approval().expiresAt().isBefore(scene.rig.base.wall.get()));
                scene.store.verifyStorage(); clearInvocations(scene.rig.base.budgets, scene.actionSql);
                var observed = new ArrayList<ToolExecutionOutcome.Status>();
                var recorder = new ToolResultAccessRecorder(scene.store, scene.rig.base.ledger);
                var result = recorder.recordKnownStartResultAccess(request, observed::add);
                assertEquals(status, result.sourceOutcome().status()); assertEquals(fee, result.sourceOutcome().actualCost());
                assertEquals(ToolInvocationRecorder.AccountingState.SETTLED, result.accessAccountingState()); assertEquals(List.of(status), observed);
                assertSame(original, scene.rig.base.intentRow.get()); assertEquals(sourceRow, scene.rig.row(scene.source));
                assertEquals(sourceBudget, scene.rig.base.budgetRows.get(scene.source.reservationId()));
                assertEquals(proof, codec.approval(scene.rig.base.intentRow.get().approval().proof(), original.action()));
                assertEquals(original.approval().proofSha256(), scene.rig.base.intentRow.get().approval().proofSha256());
                var accessBudget = scene.rig.base.budgetRows.get(request.reservationId());
                assertEquals(BigDecimal.ZERO, accessBudget.actualCost()); assertEquals(AgentBudgetReservation.State.SETTLED, accessBudget.state());
                assertEquals(1, scene.accesses.size()); assertEquals(3, scene.rig.base.budgetInserts.get());
                verify(scene.rig.base.budgets).settleBudget(eq(ACTOR), eq(request.lease()), eq(request.reservationId()), any());
                verify(scene.rig.base.budgets, never()).settleBudget(eq(ACTOR), any(), eq(scene.source.reservationId()), any());
                verify(scene.actionSql).lockForApproval(eq(request.action()), eq(scene.descriptor), any());
                verify(scene.actionSql, never()).lockForWrite(any(), any(), any());
            }
        }
    }

    @Test void approved_exact_access_replay_keeps_one_fact_and_budget_but_rechecks_current_reconcile() throws Exception {
        try (var scene = approvedScene(ToolExecutionOutcome.Status.SUCCEEDED, new BigDecimal("0.0300"))) {
            var request = scene.request(UUID.randomUUID()); var first = scene.read(request); var stored = new HashMap<>(scene.accesses.get(request.accessId()));
            var source = scene.rig.base.intentRow.get(); var sourceBudget = scene.rig.base.budgetRows.get(scene.source.reservationId());
            scene.advancePastApproval(); clearInvocations(scene.rig.base.authority, scene.rig.base.capabilities, scene.rig.base.budgets);
            var replay = scene.read(request);
            assertEquals(first.sourceResultSha256(), replay.sourceResultSha256()); assertEquals(first.sourceActionRevision(), replay.sourceActionRevision());
            assertEquals(first.sourceOutcome(), replay.sourceOutcome()); assertEquals(stored, scene.accesses.get(request.accessId()));
            assertEquals(1, scene.accesses.size()); assertEquals(3, scene.rig.base.budgetInserts.get()); assertSame(source, scene.rig.base.intentRow.get());
            assertEquals(sourceBudget, scene.rig.base.budgetRows.get(scene.source.reservationId()));
            verify(scene.rig.base.authority).check(any(), eq(ToolExecutionAuthorization.Boundary.RECONCILE));
            verify(scene.rig.base.capabilities).current(request.binding(), scene.descriptor);
            verify(scene.rig.base.capabilities).currentInTransaction(request.binding(), scene.descriptor);
            verify(scene.rig.base.budgets, never()).reserveBudget(any(), any(), any());
            scene.check.set(value -> new ToolExecutionAuthorization.CheckResult(value, ToolExecutionAuthorization.Boundary.RECONCILE, ToolExecutionAuthorization.Decision.DENY, null));
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, () -> scene.read(request));
            assertEquals(stored, scene.accesses.get(request.accessId())); assertEquals(3, scene.rig.base.budgetInserts.get());
        }
    }

    @Test void approved_disclosure_rejects_current_approval_policy_drift_outside_and_inside_the_transaction() throws Exception {
        for (boolean inTransaction : List.of(false, true)) for (String policy : java.util.Arrays.asList(null, "another-approval-policy")) {
            try (var scene = approvedScene(ToolExecutionOutcome.Status.SUCCEEDED, null)) {
                var request = scene.request(UUID.randomUUID()); var original = scene.rig.base.intentRow.get();
                if (inTransaction) scene.beforeRunLock.set(() -> scene.setApprovalPolicy(policy)); else scene.setApprovalPolicy(policy);
                clearInvocations(scene.rig.base.authority);
                assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, () -> scene.read(request));
                assertTrue(scene.accesses.isEmpty()); assertEquals(2, scene.rig.base.budgetInserts.get()); assertSame(original, scene.rig.base.intentRow.get());
                if (inTransaction) verify(scene.rig.base.authority).check(any(), eq(ToolExecutionAuthorization.Boundary.RECONCILE));
                else verify(scene.rig.base.authority, never()).check(any(), eq(ToolExecutionAuthorization.Boundary.RECONCILE));
            }
        }
    }

    @Test void historical_approval_never_substitutes_for_current_reconcile_capability_or_live_lease() throws Exception {
        for (String change : List.of("deny", "prepare", "epoch", "lease", "cancel")) try (var scene = approvedScene(ToolExecutionOutcome.Status.SUCCEEDED, null)) {
            var request = scene.request(UUID.randomUUID()); var original = scene.rig.base.intentRow.get();
            if (change.equals("deny")) scene.check.set(value -> new ToolExecutionAuthorization.CheckResult(value, ToolExecutionAuthorization.Boundary.RECONCILE, ToolExecutionAuthorization.Decision.DENY, null));
            if (change.equals("prepare")) scene.check.set(value -> scene.rig.allowed(value, ToolExecutionAuthorization.Boundary.PREPARE));
            if (change.equals("epoch")) { var old = scene.rig.evidence(RUN); scene.rig.base.insideEvidence.set(new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(old.reservation().binding(), 4), old.grantId(), old.ciphertext(), old.expiresAt())); }
            if (change.equals("lease")) scene.rig.base.databaseLease.set(NOW.minusSeconds(1));
            if (change.equals("cancel")) scene.rig.base.sourceActive.set(false);
            var rejected = assertThrows(AgentLedgerException.class, () -> scene.read(request));
            assertTrue(Set.of(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED).contains(rejected.code()));
            assertTrue(scene.accesses.isEmpty()); assertEquals(2, scene.rig.base.budgetInserts.get()); assertSame(original, scene.rig.base.intentRow.get());
        }
    }

    @Test void nonterminal_approved_sources_and_strict_action_history_failures_do_not_consume_access_quota() throws Exception {
        for (String state : List.of("AWAITING_APPROVAL", "DISPATCHING", "UNKNOWN")) {
            try (var scene = state.equals("DISPATCHING") ? new Scene(ToolPolicy.Risk.APPROVAL_REQUIRED, ToolExecutionOutcome.Status.SUCCEEDED, null, false)
                    : approvedScene(ToolExecutionOutcome.Status.valueOf(state), null)) {
                var request = scene.request(UUID.randomUUID()); var original = scene.rig.base.intentRow.get(); long budgets = scene.rig.base.budgetInserts.get();
                assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.read(request));
                assertTrue(scene.accesses.isEmpty()); assertEquals(budgets, scene.rig.base.budgetInserts.get()); assertSame(original, scene.rig.base.intentRow.get());
            }
        }
        // Strict Action parsing is covered by ActionApprovalSqlTest; access must propagate its missing/corrupt/cross-action history refusal.
        for (String failure : List.of("missing", "corrupt-proof", "cross-action-proof")) try (var scene = approvedScene(ToolExecutionOutcome.Status.SUCCEEDED, null)) {
            var request = scene.request(UUID.randomUUID()); scene.sourceFailure.set(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "private " + failure));
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.read(request)); assertTrue(scene.accesses.isEmpty()); assertEquals(2, scene.rig.base.budgetInserts.get());
        }
    }

    @Test void approved_access_keeps_target_identity_and_source_digest_guards_on_replay() throws Exception {
        try (var scene = approvedScene(ToolExecutionOutcome.Status.SUCCEEDED, null)) {
            var request = scene.request(UUID.randomUUID()); scene.read(request);
            var changedAction = new ToolActionRequest(ACTOR, scene.descriptor, "different-invoice", "v1", Map.of("amount", 20L));
            var changed = new StartResultAccessRequestVO(request.binding(), request.lease(), scene.descriptor, changedAction, request.accessId());
            scene.missingAction.set(true); assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, () -> scene.read(changed));
            assertEquals(1, scene.accesses.size()); assertEquals(3, scene.rig.base.budgetInserts.get());
        }
        for (boolean revision : List.of(false, true)) try (var scene = approvedScene(ToolExecutionOutcome.Status.SUCCEEDED, new BigDecimal("0.0300"))) {
            var request = scene.request(UUID.randomUUID()); scene.read(request); var old = scene.rig.base.intentRow.get();
            scene.replaceSource(revision ? old.outcome() : success(new BigDecimal("0.0400")), old.revision() + (revision ? 1 : 0));
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.read(request)); assertEquals(1, scene.accesses.size());
            assertEquals(3, scene.rig.base.budgetInserts.get());
        }
    }

    @Test void approved_access_insert_or_timeout_failures_roll_back_only_the_new_access_fact_and_budget() throws Exception {
        for (boolean timeout : List.of(false, true)) try (var scene = approvedScene(ToolExecutionOutcome.Status.SUCCEEDED, new BigDecimal("0.0310"))) {
            var request = scene.request(UUID.randomUUID()); var original = scene.rig.base.intentRow.get(); var budget = scene.rig.base.budgetRows.get(scene.source.reservationId());
            if (timeout) scene.afterInsert.set(() -> { scene.rig.base.wall.set(NOW.minusSeconds(30)); scene.rig.base.nano.set(10_000_000_001L); }); else scene.failInsert.set(true);
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.read(request));
            assertTrue(scene.accesses.isEmpty()); assertFalse(scene.rig.base.budgetRows.containsKey(request.reservationId())); assertEquals(2, scene.rig.base.budgetInserts.get());
            assertSame(original, scene.rig.base.intentRow.get()); assertEquals(budget, scene.rig.base.budgetRows.get(scene.source.reservationId()));
        }
    }

    private static Scene approvedScene(ToolExecutionOutcome.Status status, BigDecimal fee) throws Exception {
        return new Scene(ToolPolicy.Risk.APPROVAL_REQUIRED, status, fee, true);
    }

    private static ToolDescriptor tool(ToolPolicy.Risk risk, String resource, boolean output) {
        return new ToolDescriptor("process.start", 1, "process_start", "Fixed START", new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
                new ToolSchema(output ? Map.of("instanceId", ToolSchema.ValueType.STRING) : Map.of(), output ? Set.of("instanceId") : Set.of()),
                new ToolPolicy(risk, "process.invoice", resource, HASH, risk == ToolPolicy.Risk.APPROVAL_REQUIRED ? "approval-1" : null));
    }
    private static ToolExecutionOutcome success(BigDecimal cost) { return new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", INSTANCE), RECEIPT, null, cost); }
    private static String sourceSha(ToolActionIntentSql.Row row) { return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_RESULT_ACCESS_SOURCE_V1", row.action().intentId(), Long.toString(row.revision()),
            row.action().requestSha256(), new ToolExecutionJsonCodec().outcome(row.descriptor(), row.outcome())), Map.of()); }
    private static AgentLedgerException assertCode(AgentErrorCodeEnum code, org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(AgentLedgerException.class, operation); assertEquals(code, failure.code()); assertNull(failure.getCause()); assertFalse(failure.getMessage().contains(INSTANCE)); return failure;
    }

    private static final class Scene implements AutoCloseable {
        final ToolDescriptor descriptor;
        final ToolWriteInvocationStoreTest.Rig rig;
        final ToolInvocationStore.PreparationRequestVO source;
        final ToolResultAccessStore store;
        final JdbcTemplate jdbc;
        final ToolActionIntentSql actionSql;
        final StartResultAccessRequestVO request;
        final Map<UUID, Map<String, Object>> accesses = new HashMap<>();
        final AtomicBoolean failInsert = new AtomicBoolean(), missingAction = new AtomicBoolean(), requireInsertOnlyAccess = new AtomicBoolean();
        final AtomicReference<Runnable> beforeRunLock = new AtomicReference<>(() -> { }), afterInsert = new AtomicReference<>(() -> { });
        final AtomicReference<Function<ToolExecutionAuthorization.Request, ToolExecutionAuthorization.CheckResult>> check = new AtomicReference<>();
        final AtomicReference<ToolExecutionAuthorization.CheckResult> checked = new AtomicReference<>();
        final AtomicReference<AgentLedgerException> sourceFailure = new AtomicReference<>();
        Map<UUID, Map<String, Object>> beforeAccesses;
        boolean ready;

        Scene() throws Exception { this(ToolExecutionOutcome.Status.SUCCEEDED, new BigDecimal("0.0300")); }
        Scene(ToolExecutionOutcome.Status status, BigDecimal fee) throws Exception { this(ToolPolicy.Risk.IDEMPOTENT_WRITE, status, fee, true); }
        Scene(ToolPolicy.Risk risk, ToolExecutionOutcome.Status status, BigDecimal fee, boolean complete) throws Exception {
            descriptor = risk == ToolPolicy.Risk.IDEMPOTENT_WRITE ? TOOL : tool(risk, ProcessClient.START_RESOURCE_KEY, true);
            ToolApprovedInvocationStoreTest.Fixture approved = risk == ToolPolicy.Risk.APPROVAL_REQUIRED
                    ? new ToolApprovedInvocationStoreTest.Fixture(descriptor, true, new BigDecimal("0.20")) : null;
            rig = approved == null ? new ToolWriteInvocationStoreTest.Rig(descriptor) : approved.rig;
            source = approved != null && status == ToolExecutionOutcome.Status.AWAITING_APPROVAL ? approved.waiting
                    : approved == null ? rig.request(RUN, UUID.randomUUID()) : approved.request(RUN);
            try {
                if (approved == null) {
                    var prepared = rig.prepare(source); var started = rig.begin(source, prepared.invocation(), "1".repeat(64), new ToolExecutionOutcome(ToolExecutionOutcome.Status.FAILED_KNOWN, Map.of(), null, "ACTION_NOT_DISPATCHABLE", null));
                    rig.complete(source, started.attempt(), "1".repeat(64), status == ToolExecutionOutcome.Status.SUCCEEDED ? success(fee) : new ToolExecutionOutcome(status, Map.of(), null, "SOURCE_REJECTED", fee));
                } else if (status != ToolExecutionOutcome.Status.AWAITING_APPROVAL) {
                    var started = approved.begin(source);
                    if (complete) {
                        if (fee != null && fee.compareTo(source.estimatedCost()) > 0) rig.budgetUnknown(source, fee);
                        approved.complete(source, started.attempt(), "1".repeat(64), status == ToolExecutionOutcome.Status.SUCCEEDED ? success(fee)
                                : new ToolExecutionOutcome(status, Map.of(), null, "SOURCE_REJECTED", fee));
                    }
                }
                store = new ToolResultAccessStore(rig.base.source, rig.base.ledger, rig.base.capabilities, rig.base.authority, rig.base.clock(), rig.base.nano::get);
                jdbc = rig.base.jdbc.get(); actionSql = rig.base.intents.constructed().getLast();
                // AA request construction stays in test bodies so the old production risk guard is an observed behavior failure, not a fixture leak.
                request = approved == null ? request(UUID.randomUUID()) : null;
                check.set(this::allowed);
                doAnswer(call -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); rig.base.trace.add("reconcile-authorize");
                    ToolExecutionAuthorization.Request expected = call.getArgument(0); assertEquals(descriptor.parametersSha256(source.action().arguments()), expected.parametersSha256());
                    assertNotEquals(source.action().requestSha256(), expected.parametersSha256());
                    var result = check.get().apply(expected); checked.set(result); return result;
                }).when(rig.base.authority).check(any(), eq(ToolExecutionAuthorization.Boundary.RECONCILE));
                if (approved == null) {
                    doAnswer(call -> sourceRow(call.getArgument(0))).when(actionSql).lockForWrite(any(), any(), any());
                } else {
                    doAnswer(call -> sourceRow(call.getArgument(0))).when(actionSql).lockForApproval(any(), any(), any());
                    doAnswer(call -> { throw new IllegalArgumentException("An approved action cannot use the idempotent-only lock"); })
                            .when(actionSql).lockForWrite(any(), any(), any());
                }
                installJdbc();
                doAnswer(call -> { if (!call.getArgument(0, Boolean.class)) { rig.capture(); beforeAccesses = copy(accesses); } return null; }).when(rig.base.connection).setAutoCommit(anyBoolean());
                doAnswer(call -> { rig.base.trace.add("rollback"); rig.restore(); if (beforeAccesses != null) { accesses.clear(); accesses.putAll(copy(beforeAccesses)); beforeAccesses = null; } return null; }).when(rig.base.connection).rollback();
                doAnswer(call -> { rig.base.trace.add("commit"); rig.beforeRows = null; beforeAccesses = null; rig.base.afterCommit.get().run(); return null; }).when(rig.base.connection).commit();
            } catch (RuntimeException | Error initializationFailure) { rig.close(); throw initializationFailure; }
        }

        private ToolActionIntentSql.Row sourceRow(ToolActionRequest requested) {
            assertTrue(TransactionSynchronizationManager.hasResource(rig.base.source)); rig.base.trace.add("access-action-lock");
            if (missingAction.get()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Source unavailable");
            if (sourceFailure.get() != null) throw sourceFailure.get();
            var current = rig.base.intentRow.get();
            if (!current.action().requestSha256().equals(requested.requestSha256())) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Source changed");
            rig.assertProjection(current); return current;
        }

        StartResultAccessRequestVO request(UUID id) { return new StartResultAccessRequestVO(rig.binding(RUN), source.lease(), descriptor, source.action(), id); }
        ToolResultAccessStore.RecordedAccessVO read(StartResultAccessRequestVO value) { if (!ready) { store.verifyStorage(); ready = true; } return store.recordKnownStartResultAccess(value); }
        ToolExecutionAuthorization.CheckResult allowed(ToolExecutionAuthorization.Request value) { return rig.allowed(value, ToolExecutionAuthorization.Boundary.RECONCILE); }
        AgentBudgetReservation zeroBudget(StartResultAccessRequestVO value) { return new AgentBudgetReservation(value.reservationId(), value.lease().epoch(), AgentBudgetRequest.Kind.TOOL, descriptor.versionKey(), 0, 0,
                BigDecimal.ZERO, AgentBudgetReservation.State.RESERVED, null, null, null, false); }
        void setEvidenceExpiry(Instant expires) { var old = rig.evidence(RUN); rig.evidences.put(RUN, new ToolExecutionGrant.EvidenceVO(old.reservation(), old.grantId(), old.ciphertext(), expires)); }
        void setTimeout(Duration timeout) {
            var old = rig.snapshot.effectivePolicy(); var policy = new ExecutionPolicyDTO(old.totalTimeout(), old.modelIdleTimeout(), timeout, old.maxModelCalls(), old.maxToolCalls(), old.maxInputTokens(), old.maxOutputTokens(),
                    old.maxEstimatedCost(), old.currency(), old.maxParallelism(), old.allowedToolVersions(), old.retryPolicyRef(), old.approvalPolicyRef());
            var snapshot = new AgentExecutionSnapshot(rig.snapshot.definition(), rig.snapshot.definitionContentSha256(), policy, rig.snapshot.input(), rig.snapshot.businessKey(), rig.snapshot.processLink());
            var material = rig.base.ledger.executionMaterial(ACTOR, new GetAgentRunQuery(RUN));
            doReturn(new AgentLedgerService.ExecutionMaterial(material.run(), snapshot, material.authorization())).when(rig.base.ledger).executionMaterial(eq(ACTOR), any());
        }
        void advancePastApproval() {
            rig.base.wall.set(NOW.plusSeconds(40)); rig.base.databaseNow.set(NOW.plusSeconds(40)); rig.base.nano.set(40_000_000_000L);
            rig.base.databaseLease.set(NOW.plusSeconds(55)); var old = rig.evidence(RUN);
            rig.evidences.put(RUN, new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(old.reservation().binding(), 4),
                    "9" + "4".repeat(62), "ENC:v1:key:new:ticket", NOW.plusSeconds(60)));
        }
        void setApprovalPolicy(String reference) {
            var material = rig.base.ledger.executionMaterial(ACTOR, new GetAgentRunQuery(RUN)); var snapshot = material.snapshot(); var old = snapshot.effectivePolicy();
            var policy = new ExecutionPolicyDTO(old.totalTimeout(), old.modelIdleTimeout(), old.toolTimeout(), old.maxModelCalls(), old.maxToolCalls(), old.maxInputTokens(), old.maxOutputTokens(),
                    old.maxEstimatedCost(), old.currency(), old.maxParallelism(), old.allowedToolVersions(), old.retryPolicyRef(), reference);
            var changed = new AgentExecutionSnapshot(snapshot.definition(), snapshot.definitionContentSha256(), policy, snapshot.input(), snapshot.businessKey(), snapshot.processLink());
            doReturn(new AgentLedgerService.ExecutionMaterial(material.run(), changed, material.authorization())).when(rig.base.ledger).executionMaterial(eq(ACTOR), any());
        }
        void replaceSource(ToolExecutionOutcome outcome, long revision) {
            var old = rig.base.intentRow.get(); var row = new ToolActionIntentSql.Row(old.action(), old.descriptor(), old.originRunId(), old.originSnapshotSha256(), old.state(), revision,
                    old.createdAt(), old.updatedAt(), old.expiresAt(), old.approval(), old.dispatch(), old.sideEffectOutcome(), outcome, outcome.errorCode(), old.closedAt());
            rig.row(source).put("outcome", new ToolExecutionJsonCodec().outcome(descriptor, outcome)); rig.row(source).put("business_receipt_id", outcome.businessReceiptId()); rig.row(source).put("error_code", outcome.errorCode()); rig.base.intentRow.set(row);
        }
        void seedState(String state) {
            var old = rig.base.intentRow.get(); boolean dispatched = Set.of("DISPATCHING", "UNKNOWN").contains(state);
            var outcome = state.equals("UNKNOWN") ? new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "SOURCE_UNKNOWN", null) : null;
            var row = new ToolActionIntentSql.Row(old.action(), old.descriptor(), old.originRunId(), old.originSnapshotSha256(), state, 1,
                    state.equals("EXPIRED") ? NOW.minusSeconds(60) : NOW, NOW, state.equals("EXPIRED") ? NOW.minusSeconds(1) : NOW.plusSeconds(300), null, dispatched ? old.dispatch() : null,
                    dispatched ? "UNKNOWN" : "NONE", outcome, state.equals("EXPIRED") ? "INTENT_EXPIRED" : outcome == null ? null : outcome.errorCode(), Set.of("EXPIRED", "UNKNOWN").contains(state) ? NOW : null);
            if (dispatched) { var invocation = rig.row(source); invocation.put("state", state); invocation.put("outcome", outcome == null ? null : new ToolExecutionJsonCodec().outcome(TOOL, outcome)); }
            rig.base.intentRow.set(row);
        }

        private void installJdbc() {
            doAnswer(call -> {
                String sql = call.getArgument(0); RowMapper<?> mapper = call.getArgument(1); Object[] values = (Object[]) call.getRawArguments()[2];
                if (sql.startsWith("SELECT lease_expires_at")) {
                    assertTrue(sql.contains("subject_type=?") && sql.contains("subject_issuer=?") && sql.contains("subject_id=?") && sql.contains("status='RUNNING'") && sql.contains("cancel_requested_at IS NULL") && sql.contains("FOR UPDATE"));
                    assertEquals(7, values.length); assertEquals(List.of(ACTOR.tenantId(), ACTOR.subjectType().name(), ACTOR.issuerFingerprint(), ACTOR.subjectId()), List.of(values).subList(0, 4));
                    assertTrue(Set.of(RUN, OTHER_RUN).contains(values[4])); assertEquals("worker", values[5]); assertEquals(1L, values[6]);
                    rig.base.trace.add("access-run-lock"); beforeRunLock.get().run();
                    return rig.base.sourceActive.get() ? List.of(mapper.mapRow(result(Map.of("lease_expires_at", rig.base.databaseLease.get())), 0)) : List.of();
                }
                if (sql.contains("FROM tianji_agent.agent_tool_result_access")) {
                    if (requireInsertOnlyAccess.get() && sql.contains("JOIN tianji_agent.agent_budget_reservation")) {
                        assertFalse(sql.contains("FOR UPDATE OF a"), "The insert-only access table must not need UPDATE privilege for a row lock");
                        assertTrue(sql.contains("FOR UPDATE OF b"), "The access budget must still be locked");
                    }
                    rig.base.trace.add(sql.contains("JOIN tianji_agent.agent_budget_reservation") ? "access-budget-read" : "access-probe");
                    var row = accesses.get((UUID) values[values.length - 1]);
                    if (row == null || !Objects.equals(row.get("run_id"), values[values.length - 2])) return List.of();
                    var view = new HashMap<>(row); if (sql.contains("JOIN tianji_agent.agent_budget_reservation")) addBudget(view, rig.base.budgetRows.get(row.get("reservation_id")));
                    return List.of(mapper.mapRow(result(view), 0));
                }
                if (sql.contains("FROM tianji_agent.agent_tool_invocation")) {
                    rig.base.trace.add("access-invocation-collision"); var id = (UUID) values[values.length - 1]; var run = (String) values[values.length - 2];
                    var row = rig.rows.get(run + ':' + id); return row == null ? List.of() : List.of(mapper.mapRow(result(row), 0));
                }
                throw new AssertionError("Unexpected controlled result-access query");
            }).when(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
            doAnswer(call -> {
                String sql = call.getArgument(0); Object[] values = (Object[]) call.getRawArguments()[1]; if (failInsert.get()) throw new DataIntegrityViolationException("private result-access detail");
                assertTrue(sql.startsWith("INSERT INTO tianji_agent.agent_tool_result_access")); var names = sql.substring(sql.indexOf('(') + 1, sql.indexOf(')')).split(",");
                assertEquals(25, names.length); assertEquals(25, values.length); var row = new HashMap<String, Object>();
                for (int index = 0; index < names.length; index++) row.put(names[index].trim(), values[index]);
                var id = (UUID) row.get("access_id"); if (accesses.containsKey(id)) return 0; accesses.put(id, row); rig.base.trace.add("access-insert"); afterInsert.get().run(); return 1;
            }).when(jdbc).update(startsWith("INSERT INTO tianji_agent.agent_tool_result_access"), any(Object[].class));
        }
        private static void addBudget(Map<String, Object> view, AgentBudgetReservation budget) {
            if (budget == null) return;
            view.put("budget_lease_epoch", budget.leaseEpoch()); view.put("budget_kind", budget.kind().name()); view.put("budget_tool_version", budget.toolVersion());
            view.put("budget_input_tokens", budget.inputTokens()); view.put("budget_output_tokens", budget.outputTokens()); view.put("budget_estimated_cost", budget.estimatedCost());
            view.put("budget_state", budget.state().name()); view.put("budget_actual_input_tokens", budget.actualInputTokens()); view.put("budget_actual_output_tokens", budget.actualOutputTokens()); view.put("budget_actual_cost", budget.actualCost());
        }
        private static Map<UUID, Map<String, Object>> copy(Map<UUID, Map<String, Object>> source) { var result = new HashMap<UUID, Map<String, Object>>(); source.forEach((id, row) -> result.put(id, new HashMap<>(row))); return result; }
        @Override public void close() { rig.close(); }
    }

    private static ResultSet result(Map<String, Object> values) throws Exception {
        var result = mock(ResultSet.class);
        when(result.getString(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value == null ? null : value.toString(); });
        when(result.getLong(anyString())).thenAnswer(call -> { var value = (Number) values.get(call.getArgument(0)); return value == null ? 0L : value.longValue(); });
        when(result.getBigDecimal(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        when(result.getTimestamp(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value instanceof Timestamp timestamp ? timestamp : value == null ? null : Timestamp.from((Instant) value); });
        when(result.getObject(anyString(), any(Class.class))).thenAnswer(call -> values.get(call.getArgument(0))); return result;
    }
}
