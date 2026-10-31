package top.egon.cola.platform.tianji.agent.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryContextBinding;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryReadGrant;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryReadGrantStore;
import top.egon.cola.platform.tianji.agent.server.integration.memory.TianjiMemoryHttpAuthorization;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ServiceExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiMemoryGrantIssuerTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private static final URI RESOURCE = URI.create("https://agent.example");
    private static final String RUN = "00000000-0000-0000-0000-000000000001";
    private static final String HASH = "a".repeat(64);
    private static final String TOKEN = "verified-request-token";
    private final TrustedAgentActor actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toASCIIString());
    private final AgentRunAuthorization.Request request = run(actor);
    private final TianjiAgentAccess.Authorized authorized = new TianjiAgentAccess.Authorized(actor, NOW.plusSeconds(300), ISSUER);
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final MemoryReadGrantStore store = mock(MemoryReadGrantStore.class);
    private final TianjiMemoryHttpAuthorization permissions = mock(TianjiMemoryHttpAuthorization.class);
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
    private final HttpExecutionGrantClient client = mock(HttpExecutionGrantClient.class);
    private final AgentRunDTO queued = mock(AgentRunDTO.class);
    private final TrustedIssuerRegistry.TrustedIssuerVO trust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"), "tenant:20",
            TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1);
    private final MemoryContextBinding binding = binding(actor);
    private final MemoryReadGrant.Reservation reservation = reservation(request, binding, null);
    private TianjiMemoryGrantIssuer issuer;

    @BeforeEach void setup() {
        var servlet = new MockHttpServletRequest(); VerifiedUserTokenCarrier.set(servlet, TOKEN); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servlet));
        when(store.usesLedger(ledger)).thenReturn(true); when(queued.runId()).thenReturn(RUN); when(queued.status()).thenReturn(AgentRunStatusEnum.QUEUED);
        when(ledger.executionMaterial(eq(actor), any())).thenReturn(new AgentLedgerService.ExecutionMaterial(queued, null, request)); when(ledger.get(eq(actor), any())).thenReturn(queued);
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trust)); when(permissions.authorize(any())).thenReturn(MemoryAuthorization.Decision.ALLOW);
        when(store.currentEvidence(any(), any(), any())).thenReturn(Optional.empty()); when(store.reserve(eq(request), eq(RESOURCE), any())).thenReturn(Optional.of(reservation));
        when(store.completeEvidence(any(), any())).thenReturn(true);
        when(client.issue(any())).thenReturn(new ExecutionGrantContract.Issued(new ExecutionGrantContract.Ticket("9" + "1".repeat(62), "ENC:v1:key:nonce:sealed"), NOW.plusSeconds(60)));
        issuer = create(binding, null, Map.of());
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }

    @Test void a_user_memory_intent_is_separate_from_the_run_and_contains_no_session_or_write_permission() {
        assertSame(queued, issuer.ensure(authorized, queued));
        var sent = org.mockito.ArgumentCaptor.forClass(ExecutionGrantContract.IssueRequest.class); verify(client).issue(sent.capture());
        assertEquals(TOKEN, sent.getValue().userToken()); assertEquals(MemoryReadGrant.PURPOSE, sent.getValue().purpose()); assertFalse(sent.getValue().sessionRequired());
        assertEquals(reservation.intentId().toString(), sent.getValue().intentId()); assertEquals(reservation.bindingSha256(), sent.getValue().bindingSha256());
        assertEquals(request.deadlineAt(), sent.getValue().deadlineAt()); assertFalse(sent.getValue().toString().contains(TOKEN));
        var order = inOrder(permissions, store, client); order.verify(permissions).authorize(argThat(value -> value.context().runAuthorization() == null
                && value.operation() == MemoryAuthorization.Operation.RETRIEVE && value.owner().equals(binding.owner(actor))));
        order.verify(store).currentEvidence(eq(request), eq(RESOURCE), any()); order.verify(store).reserve(eq(request), eq(RESOURCE), any()); order.verify(client).issue(any());
        order.verify(store).completeEvidence(eq(reservation), argThat(value -> value.subjectType() == TrustedAgentActor.SubjectType.USER));
    }

    @Test void active_evidence_is_not_reissued_but_current_read_permission_is_still_required() {
        doReturn(Optional.of(new MemoryReadGrant.Ticket("9" + "1".repeat(62), "ENC:v1:key:nonce:existing", NOW.plusSeconds(60)))).when(store).currentEvidence(any(), any(), any());
        assertSame(queued, issuer.ensure(authorized, queued)); verify(permissions).authorize(any()); verifyNoInteractions(client); verify(store, never()).reserve(any(), any(), any());
        doReturn(MemoryAuthorization.Decision.DENY).when(permissions).authorize(any());
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> issuer.ensure(authorized, queued)).code());
    }

    @Test void missing_credentials_unknown_exchange_and_late_closed_completion_do_not_claim_or_attach() {
        RequestContextHolder.resetRequestAttributes();
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> issuer.ensure(authorized, queued)).code()); verifyNoInteractions(client);
        var servlet = new MockHttpServletRequest(); VerifiedUserTokenCarrier.set(servlet, TOKEN); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servlet));
        doThrow(new HttpExecutionGrantClient.ExecutionGrantUnavailableException()).when(client).issue(any());
        var unknown = assertThrows(AgentLedgerException.class, () -> issuer.ensure(authorized, queued)); assertNull(unknown.getCause()); assertFalse(unknown.toString().contains(TOKEN));
        doReturn(new ExecutionGrantContract.Issued(new ExecutionGrantContract.Ticket("9" + "1".repeat(62), "ENC:v1:key:nonce:late"), NOW.plusSeconds(60))).when(client).issue(any());
        doReturn(false).when(store).completeEvidence(any(), any()); var cancelled = mock(AgentRunDTO.class); when(cancelled.status()).thenReturn(AgentRunStatusEnum.CANCELLED);
        doReturn(cancelled).when(ledger).get(eq(actor), any()); assertSame(cancelled, issuer.ensure(authorized, queued));
    }

    @Test void changed_realm_or_expired_request_budget_blocks_completion_after_the_exchange() {
        doReturn(Optional.of(trust), Optional.empty()).when(issuers).findByIssuer(anyString());
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> issuer.ensure(authorized, queued)).code()); verify(store, never()).completeEvidence(any(), any());
        doReturn(Optional.of(trust)).when(issuers).findByIssuer(anyString()); var clock = mock(Clock.class); when(clock.instant()).thenReturn(NOW);
        issuer = new TianjiMemoryGrantIssuer(ledger, store, List.of(binding), permissions, null, RESOURCE, issuers, Map.of(ISSUER, client), Map.of(), Duration.ofSeconds(1), clock);
        doAnswer(call -> { doReturn(NOW.plusSeconds(31)).when(clock).instant(); return new ExecutionGrantContract.Issued(new ExecutionGrantContract.Ticket("9" + "1".repeat(62), "ENC:v1:key:nonce:late"), NOW.plusSeconds(60)); }).when(client).issue(any());
        assertThrows(AgentLedgerException.class, () -> issuer.ensure(authorized, queued)); verify(store, never()).completeEvidence(any(), any());
    }

    @Test void service_issuance_uses_the_verified_linked_request_and_owner_registered_source_resource() throws Exception {
        var serviceActor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "client", ISSUER.toASCIIString()); var serviceRun = run(serviceActor); var sourceBinding = binding(serviceActor);
        var principal = new ServiceIdentityPrincipal("client", "20", "client", "token", RESOURCE, 1, Set.of(TianjiAgentPermissions.MEMORY_READ_SCOPE), "business", "source", "test", "credential",
                NOW.minusSeconds(10), NOW.plusSeconds(300), "application", ServiceTokenContext.TENANT);
        var serviceAuthorized = new TianjiAgentAccess.Authorized(serviceActor, principal.expiresAt(), ISSUER);
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(), ISSUER));
        var services = mock(TianjiServiceExecutionBindings.class); when(services.usesIssuerRegistry(issuers)).thenReturn(true);
        var subject = new AgentServiceExecutionGrant.Subject("client", "application", "business", "source", "test", URI.create("https://process.example"));
        when(services.require(eq(serviceRun), any())).thenReturn(subject); when(ledger.executionMaterial(eq(serviceActor), any())).thenReturn(new AgentLedgerService.ExecutionMaterial(queued, mock(AgentExecutionSnapshot.class), serviceRun));
        when(ledger.get(eq(serviceActor), any())).thenReturn(queued); var reserved = reservation(serviceRun, sourceBinding, subject);
        when(store.reserve(eq(serviceRun), eq(RESOURCE), any())).thenReturn(Optional.of(reserved));
        when(client.issueService(any())).thenReturn(new ServiceExecutionGrantContract.Issued(new ServiceExecutionGrantContract.Ticket("8" + "1".repeat(62), "ENC:v1:key:nonce:service"), NOW.plusSeconds(60)));
        issuer = create(sourceBinding, services, Map.of(ISSUER, client));
        var servlet = new MockHttpServletRequest("POST", "/api/tianji/v1/agent-process-dispatches/submit"); servlet.addHeader("Authorization", "Bearer " + TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servlet));
        new VerifiedAgentIssuerFilter().doFilter(servlet, new MockHttpServletResponse(), (request, response) -> assertSame(queued, issuer.ensure(serviceAuthorized, queued)));
        var sent = org.mockito.ArgumentCaptor.forClass(ServiceExecutionGrantContract.IssueRequest.class); verify(client).issueService(sent.capture());
        assertEquals(TOKEN, sent.getValue().serviceToken()); assertEquals(reserved.intentId().toString(), sent.getValue().intentId()); assertEquals(MemoryReadGrant.PURPOSE, sent.getValue().purpose());
        verify(store).completeEvidence(eq(reserved), argThat(value -> value.subjectType() == TrustedAgentActor.SubjectType.SERVICE)); verify(client, never()).issue(any());
    }

    @Test void ordinary_user_issue_completion_precedes_the_memory_hook_and_original_constructors_remain_available() {
        var ordinaryStore = mock(AgentExecutionGrantStore.class); var ordinaryClient = mock(HttpExecutionGrantClient.class); var memory = mock(TianjiMemoryGrantIssuer.class);
        when(memory.usesLedger(ledger)).thenReturn(true); when(memory.usesIssuerRegistry(issuers)).thenReturn(true); when(memory.usesResource(RESOURCE)).thenReturn(true); when(memory.ensure(authorized, queued)).thenReturn(queued);
        when(ordinaryStore.current(request, RESOURCE)).thenReturn(Optional.empty()); var reserved = new AgentExecutionGrant.Reservation(request, RESOURCE, 1, AgentExecutionGrant.fingerprint(request, RESOURCE));
        when(ordinaryStore.reserve(actor, RUN, RESOURCE)).thenReturn(Optional.of(reserved)); when(ordinaryStore.complete(any(), any())).thenReturn(true);
        when(ordinaryClient.issue(any())).thenReturn(new ExecutionGrantContract.Issued(new ExecutionGrantContract.Ticket("9" + "2".repeat(62), "ENC:v1:key:nonce:ordinary"), NOW.plusSeconds(60)));
        var ordinary = new TianjiExecutionGrantIssuer(ledger, ordinaryStore, RESOURCE, issuers, Map.of(ISSUER, ordinaryClient), memory);
        assertSame(queued, ordinary.ensure(authorized, queued)); var order = inOrder(ordinaryStore, memory); order.verify(ordinaryStore).current(request, RESOURCE);
        order.verify(ordinaryStore).reserve(actor, RUN, RESOURCE); order.verify(ordinaryStore).complete(eq(reserved), any()); order.verify(memory).ensure(authorized, queued);
    }

    @Test void an_outer_transaction_or_missing_required_factory_issuer_fails_closed() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(AgentLedgerException.class, () -> issuer.ensure(authorized, queued)); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(client, permissions);
        @SuppressWarnings("unchecked") org.springframework.beans.factory.ObjectProvider<TianjiMemoryGrantIssuer> memory = mock(org.springframework.beans.factory.ObjectProvider.class);
        var environment = new org.springframework.mock.env.MockEnvironment().withProperty("stellar.tianji.agent.context.memory.enabled", "true");
        assertThrows(IllegalStateException.class, () -> new TianjiExecutionGrantConfiguration().tianjiExecutionGrantIssuer(null, null, null, null, null, null, null, environment, memory, null, null));
        assertThrows(IllegalStateException.class, () -> new TianjiServiceExecutionConfiguration().tianjiServiceExecutionGrantIssuer(null, null, null, null, null, null, null, null, environment, memory, null));
    }

    @Test void the_actual_user_factory_requires_and_forwards_the_optional_write_issuer_when_enabled() {
        @SuppressWarnings("unchecked") org.springframework.beans.factory.ObjectProvider<TianjiMemoryGrantIssuer> reads = mock(org.springframework.beans.factory.ObjectProvider.class);
        @SuppressWarnings("unchecked") org.springframework.beans.factory.ObjectProvider<TianjiMemoryPreferenceGrantIssuer> writes = mock(org.springframework.beans.factory.ObjectProvider.class);
        var environment = new org.springframework.mock.env.MockEnvironment().withProperty("stellar.tianji.agent.memory.extraction.background.enabled", "true");
        var failure = assertThrows(IllegalStateException.class, () -> new TianjiExecutionGrantConfiguration().tianjiExecutionGrantIssuer(null, null, null, null, null, null, null, environment, reads, writes, null));
        assertEquals("TIANJI_MEMORY_PREFERENCE_GRANT_ISSUER_REQUIRED", failure.getMessage());
        var readIssuer = mock(TianjiMemoryGrantIssuer.class); var writeIssuer = mock(TianjiMemoryPreferenceGrantIssuer.class); when(reads.getIfAvailable()).thenReturn(readIssuer); when(writes.getIfAvailable()).thenReturn(writeIssuer);
        environment.withProperty("stellar.tianji.agent.context.memory.enabled", "true"); var arguments = new java.util.concurrent.atomic.AtomicReference<List<?>>();
        try (var constructed = mockConstruction(TianjiExecutionGrantIssuer.class, (value, context) -> arguments.set(context.arguments()))) {
            var result = new TianjiExecutionGrantConfiguration().tianjiExecutionGrantIssuer(null, null, null, null, null, null, null, environment, reads, writes, null);
            assertSame(constructed.constructed().getFirst(), result); assertSame(readIssuer, arguments.get().get(7)); assertSame(writeIssuer, arguments.get().get(8));
        }
    }

    private TianjiMemoryGrantIssuer create(MemoryContextBinding binding, TianjiServiceExecutionBindings services, Map<URI, HttpExecutionGrantClient> clients) {
        return new TianjiMemoryGrantIssuer(ledger, store, List.of(binding), permissions, services, RESOURCE, issuers, Map.of(ISSUER, client), clients, Duration.ofSeconds(1), Clock.fixed(NOW, ZoneOffset.UTC));
    }
    private static MemoryContextBinding binding(TrustedAgentActor actor) { return new MemoryContextBinding("memory", 20, actor.issuerFingerprint(), actor.subjectType(), "definition", 1, HASH,
            actor.subjectType() == TrustedAgentActor.SubjectType.USER ? MemoryOwnerTypeEnum.USER : MemoryOwnerTypeEnum.APPLICATION, null, "research", null, true, 5, Set.of("retention@1#" + "b".repeat(64))); }
    private static AgentRunAuthorization.Request run(TrustedAgentActor actor) { return new AgentRunAuthorization.Request(actor, RUN, "definition", 1, HASH, HASH, HASH, "policy", true, NOW.plusSeconds(600)); }
    private static MemoryReadGrant.Reservation reservation(AgentRunAuthorization.Request run, MemoryContextBinding binding, AgentServiceExecutionGrant.Subject subject) {
        var scope = binding.scope(run, subject); return new MemoryReadGrant.Reservation(run, RESOURCE, scope, 1, MemoryReadGrant.intentId(run, scope), MemoryReadGrant.fingerprint(run, RESOURCE, scope));
    }
}
