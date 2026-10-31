package top.egon.cola.platform.tianji.agent.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceIntent;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceSourceBinding;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryPreferenceIntentStore;
import top.egon.cola.platform.tianji.agent.server.integration.memory.TianjiMemoryHttpAuthorization;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiMemoryPreferenceGrantIssuerTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final URI ISSUER = URI.create("https://idp.example/20"), RESOURCE = URI.create("https://agent.example");
    private static final String RUN = "00000000-0000-0000-0000-000000000001", HASH = "a".repeat(64), TOKEN = "verified-fixture-request-token";
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toASCIIString());
    private static final AgentRunAuthorization.Request RUN_AUTH = new AgentRunAuthorization.Request(ACTOR, RUN, "assistant", 1, HASH, HASH, HASH, "policy", true, NOW.plusSeconds(60));
    private static final MemoryPreferenceSourceBinding BINDING = new MemoryPreferenceSourceBinding("source", 20, ACTOR.issuerFingerprint(), "assistant", 1, HASH, "research", "preferences@1#" + "b".repeat(64));
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final MemoryPreferenceIntentStore store = mock(MemoryPreferenceIntentStore.class);
    private final TianjiAgentAccess access = mock(TianjiAgentAccess.class);
    private final TianjiMemoryHttpAuthorization permissions = mock(TianjiMemoryHttpAuthorization.class);
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
    private final HttpExecutionGrantClient client = mock(HttpExecutionGrantClient.class);
    private final AgentExecutionSnapshot snapshot = mock(AgentExecutionSnapshot.class);
    private final TianjiAgentAccess.Authorized authorized = new TianjiAgentAccess.Authorized(ACTOR, NOW.plusSeconds(300), ISSUER);
    private final TrustedIssuerRegistry.TrustedIssuerVO trust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"), "tenant:20",
            TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1);
    private TianjiMemoryPreferenceGrantIssuer issuer;

    @BeforeEach void setup() {
        var request = new MockHttpServletRequest(); VerifiedUserTokenCarrier.set(request, TOKEN); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(store.usesLedger(ledger)).thenReturn(true); when(access.require(TianjiAgentPermissions.GET_API)).thenReturn(authorized);
        when(permissions.authorize(any())).thenReturn(MemoryAuthorization.Decision.ALLOW); when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trust));
        when(ledger.executionMaterial(eq(ACTOR), any())).thenReturn(new AgentLedgerService.ExecutionMaterial(run(AgentRunStatusEnum.QUEUED), snapshot, RUN_AUTH));
        when(ledger.get(eq(ACTOR), any())).thenReturn(run(AgentRunStatusEnum.QUEUED)); when(store.currentEvidence(any(), any(), any())).thenReturn(Optional.empty());
        when(store.reserve(any(), any(), any(), any(), anyInt())).thenAnswer(call -> {
            AgentRunAuthorization.Request run = call.getArgument(0); MemoryPreferenceSourceBinding binding = call.getArgument(1); URI resource = call.getArgument(2);
            Instant deadline = call.getArgument(3); return Optional.of(reservation(run, binding, resource, deadline.truncatedTo(ChronoUnit.MICROS), 2));
        });
        when(store.complete(any(), any())).thenReturn(true); when(client.issue(any())).thenReturn(issued(NOW.plusSeconds(90)));
        issuer = create();
    }
    @AfterEach void cleanup() { RequestContextHolder.resetRequestAttributes(); }

    @Test void independent_write_issuance_uses_only_current_user_proof_after_current_write_and_source_read_checks() {
        assertEquals(run(AgentRunStatusEnum.QUEUED), issuer.ensure(authorized, run(AgentRunStatusEnum.QUEUED)));
        var sent = org.mockito.ArgumentCaptor.forClass(ExecutionGrantContract.IssueRequest.class); var reserved = org.mockito.ArgumentCaptor.forClass(MemoryPreferenceIntent.Reservation.class);
        var order = inOrder(access, permissions, ledger, store, client);
        order.verify(access).require(TianjiAgentPermissions.GET_API); order.verify(permissions).authorize(argThat(value -> value.operation() == MemoryAuthorization.Operation.UPSERT && value.sourceRunId() == null && value.sourceEventId() == null));
        order.verify(ledger).executionMaterial(eq(ACTOR), eq(new GetAgentRunQuery(RUN))); order.verify(store).currentEvidence(RUN_AUTH, BINDING, RESOURCE);
        order.verify(store).reserve(eq(RUN_AUTH), eq(BINDING), eq(RESOURCE), eq(NOW.plusSeconds(120)), eq(3)); order.verify(client).issue(sent.capture());
        order.verify(access).require(TianjiAgentPermissions.GET_API); order.verify(permissions).authorize(any()); order.verify(ledger).executionMaterial(eq(ACTOR), any()); order.verify(store).complete(reserved.capture(), any());
        assertEquals(MemoryPreferenceIntent.PURPOSE, sent.getValue().purpose()); assertFalse(sent.getValue().sessionRequired());
        assertEquals(TOKEN, sent.getValue().userToken()); assertEquals(reserved.getValue().intentId().toString(), sent.getValue().intentId()); assertEquals(reserved.getValue().bindingSha256(), sent.getValue().bindingSha256());
        assertEquals(NOW.plusSeconds(120), sent.getValue().deadlineAt()); assertNotEquals(RUN, sent.getValue().intentId()); assertNotEquals(RUN_AUTH.deadlineAt(), sent.getValue().deadlineAt());
        assertFalse(sent.getValue().toString().contains(TOKEN)); verify(snapshot, never()).input(); verify(client, never()).issueService(any());
    }

    @Test void absent_current_source_write_or_request_bearer_skips_the_optional_enhancement_without_reading_input() {
        doThrow(new AccessDeniedException("fixture denied")).when(access).require(anyString());
        assertEquals(run(AgentRunStatusEnum.QUEUED), issuer.ensure(authorized, run(AgentRunStatusEnum.QUEUED)));
        doReturn(authorized).when(access).require(anyString());
        for (var decision : List.of(MemoryAuthorization.Decision.DENY, MemoryAuthorization.Decision.UNAVAILABLE)) {
            when(permissions.authorize(any())).thenReturn(decision); issuer.ensure(authorized, run(AgentRunStatusEnum.QUEUED));
        }
        when(permissions.authorize(any())).thenReturn(MemoryAuthorization.Decision.ALLOW); RequestContextHolder.resetRequestAttributes(); issuer.ensure(authorized, run(AgentRunStatusEnum.QUEUED));
        verify(ledger, never()).executionMaterial(any(), any()); verify(store, never()).reserve(any(), any(), any(), any(), anyInt()); verifyNoInteractions(client);
    }

    @Test void denied_unknown_or_lost_epoch_issue_records_reference_only_reason_and_keeps_the_run_result() {
        doThrow(new HttpExecutionGrantClient.ExecutionGrantDeniedException()).when(client).issue(any());
        assertEquals(run(AgentRunStatusEnum.QUEUED), issuer.ensure(authorized, run(AgentRunStatusEnum.QUEUED))); verify(store).recordIssueFailure(any(), eq("OWNER_DENIED"), eq(true));
        doThrow(new IllegalStateException(TOKEN)).when(client).issue(any());
        assertEquals(run(AgentRunStatusEnum.QUEUED), issuer.ensure(authorized, run(AgentRunStatusEnum.QUEUED))); verify(store).recordIssueFailure(any(), eq("ISSUE_UNAVAILABLE"), eq(false));
        doReturn(issued(NOW.plusSeconds(90))).when(client).issue(any()); when(store.complete(any(), any())).thenReturn(false);
        assertEquals(run(AgentRunStatusEnum.QUEUED), issuer.ensure(authorized, run(AgentRunStatusEnum.QUEUED))); verify(store).recordIssueFailure(any(), eq("ISSUE_EPOCH_CHANGED"), eq(false));
    }

    @Test void current_permission_or_trust_change_after_issuance_never_attaches_the_reply() {
        when(permissions.authorize(any())).thenReturn(MemoryAuthorization.Decision.ALLOW, MemoryAuthorization.Decision.DENY);
        issuer.ensure(authorized, run(AgentRunStatusEnum.QUEUED)); verify(store).recordIssueFailure(any(), eq("CURRENT_PERMISSION_DENIED"), eq(true)); verify(store, never()).complete(any(), any());
        doReturn(MemoryAuthorization.Decision.ALLOW).when(permissions).authorize(any()); when(issuers.findByIssuer(anyString())).thenReturn(Optional.of(trust), Optional.empty());
        issuer.ensure(authorized, run(AgentRunStatusEnum.QUEUED)); verify(store).recordIssueFailure(any(), eq("ISSUE_AUTHORITY_CHANGED"), eq(false)); verify(store, never()).complete(any(), any());
    }

    @Test void the_fixed_original_write_deadline_survives_a_later_current_request() {
        var fixed = reservation(RUN_AUTH, BINDING, RESOURCE, NOW.plusSeconds(100), 9); doReturn(Optional.of(fixed)).when(store).reserve(any(), any(), any(), any(), anyInt());
        issuer.ensure(authorized, run(AgentRunStatusEnum.QUEUED)); var sent = org.mockito.ArgumentCaptor.forClass(ExecutionGrantContract.IssueRequest.class); verify(client).issue(sent.capture());
        assertEquals(fixed.deadlineAt(), sent.getValue().deadlineAt()); assertEquals(fixed.bindingSha256(), sent.getValue().bindingSha256());
        assertEquals(fixed.intentId().toString(), sent.getValue().intentId());
    }

    @Test void fast_running_or_completed_sources_use_the_optional_hook_without_gating_ordinary_claim_or_requiring_a_live_run_deadline() {
        var currentRun = run(AgentRunStatusEnum.RUNNING); when(ledger.get(eq(ACTOR), any())).thenReturn(currentRun);
        when(ledger.executionMaterial(eq(ACTOR), any())).thenReturn(new AgentLedgerService.ExecutionMaterial(currentRun, snapshot, RUN_AUTH));
        var ordinaryStore = mock(AgentExecutionGrantStore.class); var ordinaryClient = mock(HttpExecutionGrantClient.class);
        when(ordinaryStore.current(RUN_AUTH, RESOURCE)).thenReturn(Optional.of(new AgentExecutionGrant.Ticket("9" + "1".repeat(62), "ENC:v1:test:A:A", NOW.plusSeconds(50))));
        var combined = new TianjiExecutionGrantIssuer(ledger, ordinaryStore, RESOURCE, issuers, Map.of(ISSUER, ordinaryClient), null, issuer);
        assertTrue(combined.usesPreferenceIssuer(issuer)); assertEquals(currentRun, combined.ensure(authorized, run(AgentRunStatusEnum.QUEUED))); verifyNoInteractions(ordinaryClient);
        verify(client).issue(any()); clearInvocations(client);
        var expiredRunAuthorization = new AgentRunAuthorization.Request(ACTOR, RUN, "assistant", 1, HASH, HASH, HASH, "policy", true, NOW.minusSeconds(30));
        when(ledger.executionMaterial(eq(ACTOR), any())).thenReturn(new AgentLedgerService.ExecutionMaterial(run(AgentRunStatusEnum.COMPLETED), snapshot, expiredRunAuthorization));
        when(ledger.get(eq(ACTOR), any())).thenReturn(run(AgentRunStatusEnum.COMPLETED));
        assertEquals(run(AgentRunStatusEnum.COMPLETED), combined.ensure(authorized, run(AgentRunStatusEnum.COMPLETED))); verify(client).issue(any());
    }

    @Test void existing_evidence_is_reused_only_after_current_permissions_and_never_refreshed_in_the_background() {
        var evidence = new MemoryPreferenceIntent.Evidence(reservation(RUN_AUTH, BINDING, RESOURCE, NOW.plusSeconds(120), 1), new AgentExecutionGrant.Ticket("9" + "1".repeat(62), "ENC:v1:test:A:A", NOW.plusSeconds(90)));
        when(store.currentEvidence(any(), any(), any())).thenReturn(Optional.of(evidence)); issuer.ensure(authorized, run(AgentRunStatusEnum.QUEUED));
        verify(access).require(TianjiAgentPermissions.GET_API); verify(permissions).authorize(any()); verify(store, never()).reserve(any(), any(), any(), any(), anyInt()); verifyNoInteractions(client);
    }

    @Test void source_cancellation_or_changes_after_the_exchange_are_rejected_and_database_failures_remain_explicit() {
        var failed = new AgentLedgerService.ExecutionMaterial(run(AgentRunStatusEnum.FAILED), snapshot, RUN_AUTH);
        when(ledger.executionMaterial(any(), any())).thenReturn(new AgentLedgerService.ExecutionMaterial(run(AgentRunStatusEnum.QUEUED), snapshot, RUN_AUTH), failed);
        issuer.ensure(authorized, run(AgentRunStatusEnum.QUEUED)); verify(store).recordIssueFailure(any(), eq("SOURCE_CHANGED"), eq(true)); verify(store, never()).complete(any(), any());
        doReturn(new AgentLedgerService.ExecutionMaterial(run(AgentRunStatusEnum.QUEUED), snapshot, RUN_AUTH)).when(ledger).executionMaterial(any(), any());
        var failure = new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Fixture storage unavailable");
        doThrow(failure).when(store).reserve(any(), any(), any(), any(), anyInt()); assertSame(failure, assertThrows(AgentLedgerException.class, () -> issuer.ensure(authorized, run(AgentRunStatusEnum.QUEUED))));
    }

    @Test void expired_identity_insufficient_budget_foreign_source_or_ambient_transaction_cannot_issue() {
        issuer.ensure(new TianjiAgentAccess.Authorized(ACTOR, NOW.plusMillis(100), ISSUER), run(AgentRunStatusEnum.QUEUED)); verifyNoInteractions(client);
        issuer.ensure(authorized, run(AgentRunStatusEnum.FAILED));
        var service = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "client", ISSUER.toASCIIString()); issuer.ensure(new TianjiAgentAccess.Authorized(service, NOW.plusSeconds(300), ISSUER), run(AgentRunStatusEnum.QUEUED));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(AgentLedgerException.class, () -> issuer.ensure(authorized, run(AgentRunStatusEnum.QUEUED))); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verify(ledger, never()).executionMaterial(any(), any()); verify(store, never()).reserve(any(), any(), any(), any(), anyInt()); verifyNoInteractions(client);
    }

    private TianjiMemoryPreferenceGrantIssuer create() { return new TianjiMemoryPreferenceGrantIssuer(ledger, store, List.of(BINDING), access, permissions, RESOURCE, issuers, Map.of(ISSUER, client),
            Duration.ofSeconds(1), Duration.ofSeconds(120), 3, Clock.fixed(NOW, ZoneOffset.UTC)); }
    private static AgentRunDTO run(AgentRunStatusEnum status) { return new AgentRunDTO(RUN, "assistant", 1, "session", status, null, false, 1, 2, 1, NOW.plusSeconds(60), NOW.minusSeconds(30), status == AgentRunStatusEnum.COMPLETED ? NOW.minusSeconds(1) : null); }
    private static MemoryPreferenceIntent.Reservation reservation(AgentRunAuthorization.Request run, MemoryPreferenceSourceBinding binding, URI resource, Instant deadline, long epoch) {
        return new MemoryPreferenceIntent.Reservation(run, binding, resource, deadline, epoch, MemoryPreferenceIntent.intentId(run, binding.scopeId()), MemoryPreferenceIntent.fingerprint(run, binding, resource, deadline));
    }
    private static ExecutionGrantContract.Issued issued(Instant expires) { return new ExecutionGrantContract.Issued(new ExecutionGrantContract.Ticket("9" + "2".repeat(62), "ENC:v1:test:nonce:sealed"), expires); }
}
