package top.egon.cola.platform.tianji.agent.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryContextBinding;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryReadGrant;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryReadGrantStore;
import top.egon.cola.platform.tianji.agent.server.integration.memory.TianjiMemoryAccessProperties;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.DataScopeDecision;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionScopeAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ServiceExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiMemoryRunAuthorizationTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private static final URI RESOURCE = URI.create("https://agent.example");
    private static final String HASH = "a".repeat(64);
    private static final String POLICY = "retention@1#" + "b".repeat(64);
    private final TrustedAgentActor actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toASCIIString());
    private final AgentRunAuthorization.Request run = run(actor);
    private final MemoryReadGrant.Ticket ticket = new MemoryReadGrant.Ticket("9" + "1".repeat(62), "ENC:v1:key:nonce:sealed", NOW.plusSeconds(60));
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final MemoryReadGrantStore store = mock(MemoryReadGrantStore.class);
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
    private final HttpExecutionAuthorizationClient client = mock(HttpExecutionAuthorizationClient.class);
    private final TrustedIssuerRegistry.TrustedIssuerVO trust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"),
            "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1);
    private TianjiMemoryRunAuthorization authority;

    @BeforeEach void setup() {
        when(store.usesLedger(ledger)).thenReturn(true); when(store.currentEvidence(any(), any(), any())).thenReturn(Optional.of(ticket));
        when(ledger.databaseNow()).thenReturn(NOW); when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trust));
        when(client.decideScopes(any(), any())).thenAnswer(call -> decision(call.getArgument(1), NOW.plusSeconds(30), Map.of()));
        authority = create(binding(MemoryOwnerTypeEnum.USER), Duration.ofSeconds(1), 2, null, Map.of());
    }
    @AfterEach void cleanup() { authority.close(); }

    @Test void each_boundary_uses_the_separate_memory_intent_and_never_requests_session_or_mutation_authority() {
        assertEquals(AgentRunAuthorization.Decision.ALLOW, authority.authorizeRun(run, AgentRunAuthorization.Boundary.CLAIM));
        assertEquals(MemoryAuthorization.Decision.ALLOW, authority.authorize(request(binding(MemoryOwnerTypeEnum.USER), MemoryAuthorization.Operation.SEARCH)));
        var expected = org.mockito.ArgumentCaptor.forClass(HttpExecutionAuthorizationClient.Expectation.class); verify(client, times(2)).decideScopes(any(), expected.capture());
        assertEquals(MemoryReadGrant.PURPOSE, expected.getValue().purpose()); assertFalse(expected.getValue().sessionRequired());
        var scope = binding(MemoryOwnerTypeEnum.USER).scope(run, null);
        assertEquals(MemoryReadGrant.intentId(run, scope).toString(), expected.getValue().intentId());
        assertEquals(MemoryReadGrant.fingerprint(run, RESOURCE, scope), expected.getValue().bindingSha256());
        assertNotEquals(run.runId(), expected.getValue().intentId()); assertNotEquals(AgentExecutionGrant.fingerprint(run, RESOURCE), expected.getValue().bindingSha256());
        assertEquals(MemoryAuthorization.Decision.DENY, authority.authorize(request(binding(MemoryOwnerTypeEnum.USER), MemoryAuthorization.Operation.FORGET)));
        verify(client, never()).decide(any(), any()); verify(client, never()).decideService(any(), any());
    }

    @Test void ordinary_run_ticket_does_not_bypass_missing_memory_evidence_in_the_actual_claim_authorizer() {
        var ordinaryStore = mock(AgentExecutionGrantStore.class); var ordinaryClient = mock(HttpExecutionAuthorizationClient.class);
        when(ordinaryStore.usesLedger(ledger)).thenReturn(true); when(ordinaryStore.current(run, RESOURCE)).thenReturn(Optional.of(new AgentExecutionGrant.Ticket(ticket.grantId(), ticket.ciphertext(), ticket.expiresAt())));
        when(ordinaryClient.decide(any(), any())).thenReturn(new ExecutionAuthorizationContract.Decision(ticket.grantId(), ISSUER.toASCIIString(), "20", "alice", RESOURCE.toASCIIString(),
                AgentRunAuthorization.PURPOSE, run.runId(), AgentExecutionGrant.fingerprint(run, RESOURCE), true, run.deadlineAt(), NOW.plusSeconds(30), 2, 3));
        try (var combined = new TianjiExecutionGrantAuthorizer(ledger, ordinaryStore, RESOURCE, "tianji", issuers,
                Map.of(actor.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(ISSUER, "20", ordinaryClient)), Duration.ofSeconds(2), 2, CLOCK, null, authority)) {
            doReturn(Optional.empty()).when(store).currentEvidence(any(), any(), any());
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, combined.authorize(run, AgentRunAuthorization.Boundary.CLAIM)); verifyNoInteractions(client);
            doReturn(Optional.of(ticket)).when(store).currentEvidence(any(), any(), any());
            assertEquals(AgentRunAuthorization.Decision.ALLOW, combined.authorize(run, AgentRunAuthorization.Boundary.CLAIM));
            doThrow(new HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException()).when(client).decideScopes(any(), any());
            assertEquals(AgentRunAuthorization.Decision.DENY, combined.authorize(run, AgentRunAuthorization.Boundary.MODEL));
        }
    }

    @Test void team_scope_is_current_direct_directory_evidence_and_uses_internal_rbac_user_ids() {
        authority.close(); var binding = binding(MemoryOwnerTypeEnum.TEAM); authority = create(binding, Duration.ofSeconds(1), 2, null, Map.of());
        doAnswer(call -> decision(call.getArgument(1), NOW.plusSeconds(30), Map.of(TianjiAgentPermissions.MEMORY_READ, dataScope("10")))).when(client).decideScopes(any(), any());
        assertEquals(MemoryAuthorization.Decision.ALLOW, authority.authorize(request(binding, MemoryAuthorization.Operation.GET)));
        var indeterminate = new DataScopeDecision(top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision.INDETERMINATE, "DIRECTORY_UNAVAILABLE", "20", "60",
                TianjiAgentPermissions.MEMORY_READ, "NONE", false, Set.of(), false, Set.of(), false, Set.of(), false, null, "directory", 1, 2, 3, List.of(), NOW);
        doAnswer(call -> decision(call.getArgument(1), NOW.plusSeconds(30), Map.of(TianjiAgentPermissions.MEMORY_READ, indeterminate))).when(client).decideScopes(any(), any());
        assertEquals(MemoryAuthorization.Decision.UNAVAILABLE, authority.authorize(request(binding, MemoryAuthorization.Operation.GET)));
        doAnswer(call -> decision(call.getArgument(1), NOW.plusSeconds(30), Map.of(TianjiAgentPermissions.MEMORY_READ, dataScope("11")))).when(client).decideScopes(any(), any());
        assertEquals(MemoryAuthorization.Decision.DENY, authority.authorize(request(binding, MemoryAuthorization.Operation.GET)));
    }

    @Test void changed_or_expired_evidence_and_unknown_authority_cannot_become_allow_or_a_confirmed_denial() {
        var query = request(binding(MemoryOwnerTypeEnum.USER), MemoryAuthorization.Operation.GET);
        doReturn(Optional.of(ticket), Optional.empty()).when(store).currentEvidence(any(), any(), any());
        assertEquals(MemoryAuthorization.Decision.UNAVAILABLE, authority.authorize(query));
        doReturn(Optional.of(ticket)).when(store).currentEvidence(any(), any(), any());
        doAnswer(call -> decision(call.getArgument(1), NOW.plusSeconds(61), Map.of())).when(client).decideScopes(any(), any());
        assertEquals(MemoryAuthorization.Decision.UNAVAILABLE, authority.authorize(query));
        doThrow(new HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException()).when(client).decideScopes(any(), any());
        doReturn(Optional.of(trust), Optional.empty()).when(issuers).findByIssuer(anyString());
        assertEquals(MemoryAuthorization.Decision.UNAVAILABLE, authority.authorize(query));
        doReturn(Optional.of(trust)).when(issuers).findByIssuer(anyString());
        assertEquals(MemoryAuthorization.Decision.DENY, authority.authorize(query));
        doThrow(new IllegalStateException("private authority detail")).when(client).decideScopes(any(), any());
        assertEquals(MemoryAuthorization.Decision.UNAVAILABLE, authority.authorize(query));
    }

    @Test void an_uninterruptible_call_retains_the_only_admission_slot_until_it_really_returns() throws Exception {
        authority.close(); authority = create(binding(MemoryOwnerTypeEnum.USER), Duration.ofMillis(120), 1, null, Map.of());
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown(); while (release.getCount() > 0) { try { release.await(); } catch (InterruptedException ignored) { } }
            return decision(call.getArgument(1), NOW.plusSeconds(30), Map.of());
        }).when(client).decideScopes(any(), any());
        try {
            assertEquals(MemoryAuthorization.Decision.UNAVAILABLE, authority.authorize(request(binding(MemoryOwnerTypeEnum.USER), MemoryAuthorization.Operation.GET)));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertEquals(MemoryAuthorization.Decision.UNAVAILABLE, authority.authorize(request(binding(MemoryOwnerTypeEnum.USER), MemoryAuthorization.Operation.GET)));
            verify(client, times(1)).decideScopes(any(), any());
        } finally { release.countDown(); }
    }

    @Test void service_reads_bind_the_complete_current_source_and_use_the_service_decision_protocol() {
        authority.close(); var serviceActor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "client", ISSUER.toASCIIString()); var serviceRun = run(serviceActor);
        var binding = new MemoryContextBinding("service-memory", 20, serviceActor.issuerFingerprint(), serviceActor.subjectType(), "definition", 1, HASH,
                MemoryOwnerTypeEnum.APPLICATION, null, "research", null, false, 5, Set.of(POLICY));
        var serviceTicket = new MemoryReadGrant.Ticket("8" + "1".repeat(62), ticket.ciphertext(), ticket.expiresAt()); doReturn(Optional.of(serviceTicket)).when(store).currentEvidence(any(), any(), any());
        var services = mock(TianjiServiceExecutionBindings.class); when(services.usesIssuerRegistry(issuers)).thenReturn(true);
        var subject = new AgentServiceExecutionGrant.Subject("client", "application", "business", "source", "test", RESOURCE); when(services.require(eq(serviceRun), any())).thenReturn(subject);
        when(ledger.executionMaterial(eq(serviceActor), any())).thenReturn(new AgentLedgerService.ExecutionMaterial(null, mock(AgentExecutionSnapshot.class), serviceRun));
        when(client.decideService(any(), any())).thenAnswer(call -> {
            HttpExecutionAuthorizationClient.ServiceExpectation value = call.getArgument(1); var intent = value.intent();
            return new ServiceExecutionAuthorizationContract.Decision(serviceTicket.grantId(), ISSUER.toASCIIString(), "20", "client", value.subjectSource(), RESOURCE.toASCIIString(),
                    MemoryReadGrant.PURPOSE, intent.intentId(), intent.bindingSha256(), false, serviceRun.deadlineAt(), NOW.plusSeconds(30), 1, 3);
        });
        authority = create(binding, Duration.ofSeconds(1), 2, services, Map.of(serviceActor.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(ISSUER, "20", client)));
        var query = new MemoryAuthorization.Request(new MemoryOperationContext(serviceActor, NOW.plusSeconds(5), serviceRun), binding.owner(serviceActor), "research", MemoryAuthorization.Operation.GET, "memory", 0, null, null);
        assertEquals(MemoryAuthorization.Decision.ALLOW, authority.authorize(query)); verify(client, never()).decideScopes(any(), any());
        doReturn(subject, new AgentServiceExecutionGrant.Subject("client", "other-app", "business", "source", "test", RESOURCE)).when(services).require(eq(serviceRun), any());
        assertEquals(MemoryAuthorization.Decision.UNAVAILABLE, authority.authorize(query));
        var changedResource = new AgentServiceExecutionGrant.Subject("client", "application", "business", "source", "test", URI.create("https://AGENT.example"));
        assertEquals(subject, changedResource);
        doReturn(subject, changedResource).when(services).require(eq(serviceRun), any());
        assertEquals(MemoryAuthorization.Decision.UNAVAILABLE, authority.authorize(query));
    }

    @Test void ambient_transactions_and_closed_authorities_do_not_issue_network_or_storage_calls() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertEquals(MemoryAuthorization.Decision.UNAVAILABLE, authority.authorize(request(binding(MemoryOwnerTypeEnum.USER), MemoryAuthorization.Operation.GET))); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        authority.close(); assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authority.authorizeRun(run, AgentRunAuthorization.Boundary.CLAIM));
        verify(store, never()).currentEvidence(any(), any(), any()); verifyNoInteractions(client);
    }

    @Test void the_enabled_execution_factory_cannot_silently_omit_its_memory_gate() {
        @SuppressWarnings("unchecked") org.springframework.beans.factory.ObjectProvider<TianjiMemoryRunAuthorization> memory = mock(org.springframework.beans.factory.ObjectProvider.class);
        var environment = new org.springframework.mock.env.MockEnvironment().withProperty("stellar.tianji.agent.execution-grants.enabled", "true")
                .withProperty("stellar.tianji.agent.context.memory.enabled", "true");
        var failure = assertThrows(IllegalStateException.class, () -> new TianjiExecutionAuthorizationConfiguration()
                .tianjiExecutionGrantAuthorizer(null, null, null, null, null, null, null, null, environment, null, memory));
        assertEquals("TIANJI_MEMORY_EXECUTION_AUTHORIZER_REQUIRED", failure.getMessage());
    }

    private TianjiMemoryRunAuthorization create(MemoryContextBinding binding, Duration timeout, int concurrency, TianjiServiceExecutionBindings services,
                                                Map<String, TianjiExecutionGrantAuthorizer.BoundClient> clients) {
        var teams = new TianjiMemoryAccessProperties(List.of(new TianjiMemoryAccessProperties.Team(20, ISSUER, "team", "research", TianjiMemoryAccessProperties.DirectoryType.ORGANIZATION, "10")));
        return new TianjiMemoryRunAuthorization(ledger, store, List.of(binding), teams, services, RESOURCE, "tianji", issuers,
                Map.of(actor.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(ISSUER, "20", client)), clients, timeout, concurrency, CLOCK);
    }
    private MemoryContextBinding binding(MemoryOwnerTypeEnum owner) { return new MemoryContextBinding("memory", 20, actor.issuerFingerprint(), actor.subjectType(), "definition", 1, HASH, owner,
            owner == MemoryOwnerTypeEnum.TEAM ? "team" : null, "research", null, true, 5, Set.of(POLICY)); }
    private MemoryAuthorization.Request request(MemoryContextBinding binding, MemoryAuthorization.Operation operation) {
        return new MemoryAuthorization.Request(new MemoryOperationContext(actor, NOW.plusSeconds(5), run), binding.owner(actor), binding.scopeId(), operation,
                operation == MemoryAuthorization.Operation.RETRIEVE || operation == MemoryAuthorization.Operation.SEARCH ? null : "memory", operation == MemoryAuthorization.Operation.FORGET ? 1 : 0, null, null);
    }
    private static AgentRunAuthorization.Request run(TrustedAgentActor actor) { return new AgentRunAuthorization.Request(actor, "00000000-0000-0000-0000-000000000001", "definition", 1, HASH, HASH, HASH, "policy", true, NOW.plusSeconds(600)); }
    private ExecutionScopeAuthorizationContract.Decision decision(HttpExecutionAuthorizationClient.Expectation expected, Instant expires, Map<String, DataScopeDecision> scopes) {
        var base = new ExecutionAuthorizationContract.Decision(ticket.grantId(), ISSUER.toASCIIString(), "20", "alice", RESOURCE.toASCIIString(), MemoryReadGrant.PURPOSE,
                expected.intentId(), expected.bindingSha256(), false, run.deadlineAt(), expires, 2, 3);
        return new ExecutionScopeAuthorizationContract.Decision(base, "60", NOW, scopes);
    }
    private static DataScopeDecision dataScope(String org) { return new DataScopeDecision(top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision.ALLOW, "CURRENT", "20", "60", TianjiAgentPermissions.MEMORY_READ,
            "ORG", false, Set.of(org), true, Set.of(), false, Set.of(), false, null, "directory", 1, 2, 3, List.of("assignment"), NOW); }
}
