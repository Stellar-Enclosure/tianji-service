package top.egon.cola.platform.tianji.agent.server.security;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiExecutionGrantAuthorizerTest {
    private static final URI ISSUER = URI.create("https://idp.example/tenants/21");
    private static final URI RESOURCE = URI.create("https://api.example/tianji");
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    private static final String RUN = "00000000-0000-0000-0000-000000000123";
    private static final String ID = "9" + "1".repeat(62);
    private final TrustedAgentActor actor = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toString());
    private final AgentRunAuthorization.Request request = new AgentRunAuthorization.Request(actor, RUN, "review", 1,
            "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, NOW.plusSeconds(600));
    private final AgentExecutionGrant.Ticket ticket = new AgentExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:sealed", NOW.plusSeconds(60));
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final AgentExecutionGrantStore store = mock(AgentExecutionGrantStore.class);
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
    private final HttpExecutionAuthorizationClient client = mock(HttpExecutionAuthorizationClient.class);
    private final TrustedIssuerRegistry.TrustedIssuerVO trust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,
            URI.create("https://idp.example/jwks"), "tenant:21", TrustedIssuerRegistry.RealmType.TENANT, "21",
            TrustedIssuerRegistry.Status.ACTIVE, 1);
    private TianjiExecutionGrantAuthorizer authorizer;

    @BeforeEach void setup() {
        when(issuers.findByIssuer(ISSUER.toString())).thenReturn(Optional.of(trust));
        when(store.current(request, RESOURCE)).thenReturn(Optional.of(ticket));
        when(ledger.databaseNow()).thenReturn(NOW);
        when(client.decide(any(), any())).thenReturn(decision(NOW.plusSeconds(30)));
        authorizer = create(Duration.ofSeconds(1), 2);
    }
    @AfterEach void cleanup() { authorizer.close(); MDC.clear(); }

    @Test void claim_and_each_model_call_check_fresh_identity_intent_and_ticket() {
        assertEquals(AgentRunAuthorization.Decision.ALLOW, authorizer.authorize(request, AgentRunAuthorization.Boundary.CLAIM));
        assertEquals(AgentRunAuthorization.Decision.ALLOW, authorizer.authorize(request, AgentRunAuthorization.Boundary.MODEL));
        var sent = org.mockito.ArgumentCaptor.forClass(ExecutionAuthorizationContract.Request.class);
        var expected = org.mockito.ArgumentCaptor.forClass(HttpExecutionAuthorizationClient.Expectation.class);
        verify(client, times(2)).decide(sent.capture(), expected.capture());
        assertEquals("tianji", sent.getValue().systemCode()); assertEquals(ID, sent.getValue().grantId());
        assertEquals(ticket.ciphertext(), sent.getValue().ciphertext()); assertEquals("alice", expected.getValue().subject());
        assertEquals(AgentExecutionGrant.fingerprint(request, RESOURCE), expected.getValue().bindingSha256());
        assertEquals(request.deadlineAt(), expected.getValue().deadlineAt()); assertTrue(expected.getValue().sessionRequired());
        verify(store, times(4)).current(request, RESOURCE);
    }

    @Test void denial_is_accepted_only_if_the_same_ticket_and_issuer_remain_current() {
        doThrow(new HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException()).when(client).decide(any(), any());
        assertEquals(AgentRunAuthorization.Decision.DENY, authorizer.authorize(request, AgentRunAuthorization.Boundary.CLAIM));
        var changed = new AgentExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:other", NOW.plusSeconds(60));
        when(store.current(request, RESOURCE)).thenReturn(Optional.of(ticket), Optional.of(changed));
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.authorize(request, AgentRunAuthorization.Boundary.CLAIM));
        when(store.current(request, RESOURCE)).thenReturn(Optional.of(ticket));
        when(issuers.findByIssuer(ISSUER.toString())).thenReturn(Optional.of(trust), Optional.empty());
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.authorize(request, AgentRunAuthorization.Boundary.CLAIM));
    }

    @Test void missing_expired_or_changed_evidence_never_allows_execution() {
        when(store.current(request, RESOURCE)).thenReturn(Optional.empty());
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.authorize(request, AgentRunAuthorization.Boundary.CLAIM));
        verifyNoInteractions(client);
        when(store.current(request, RESOURCE)).thenReturn(Optional.of(ticket), Optional.empty());
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.authorize(request, AgentRunAuthorization.Boundary.MODEL));
        when(store.current(request, RESOURCE)).thenReturn(Optional.of(ticket));
        when(client.decide(any(), any())).thenReturn(decision(NOW));
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.authorize(request, AgentRunAuthorization.Boundary.MODEL));
        doThrow(new IllegalStateException("private detail")).when(store).current(request, RESOURCE);
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.authorize(request, AgentRunAuthorization.Boundary.MODEL));
    }

    @Test void service_legacy_or_unbound_issuers_do_not_inherit_user_authority() {
        var legacy = new TrustedAgentActor(21, TrustedAgentActor.SubjectType.USER, "alice");
        var service = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "agent", ISSUER.toString());
        var foreign = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://other.example/21");
        for (var value : java.util.List.of(legacy, service, foreign)) {
            var altered = new AgentRunAuthorization.Request(value, RUN, "review", 1, "a".repeat(64), "b".repeat(64),
                    "c".repeat(64), "policy-v1", true, NOW.plusSeconds(600));
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.authorize(altered, AgentRunAuthorization.Boundary.CLAIM));
        }
        verifyNoInteractions(client);
    }

    @Test void bounded_executor_fails_closed_even_if_a_dependency_ignores_interrupts() throws Exception {
        authorizer.close(); authorizer = create(Duration.ofMillis(120), 1);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(inv -> {
            entered.countDown();
            while (release.getCount() != 0) {
                try { release.await(); } catch (InterruptedException ignored) { }
            }
            return decision(NOW.plusSeconds(30));
        }).when(client).decide(any(), any());
        try {
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.authorize(request, AgentRunAuthorization.Boundary.CLAIM));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.authorize(request, AgentRunAuthorization.Boundary.CLAIM));
            verify(client, times(1)).decide(any(), any());
        } finally { release.countDown(); }
    }

    @Test void trace_context_is_propagated_and_closed_authorizer_fails_closed() {
        MDC.put("traceId", "a".repeat(32)); MDC.put("spanId", "b".repeat(16));
        doAnswer(inv -> { assertEquals("a".repeat(32), MDC.get("traceId")); return decision(NOW.plusSeconds(30)); })
                .when(client).decide(any(), any());
        assertEquals(AgentRunAuthorization.Decision.ALLOW, authorizer.authorize(request, AgentRunAuthorization.Boundary.CLAIM));
        assertEquals("a".repeat(32), MDC.get("traceId"));
        authorizer.close();
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.authorize(request, AgentRunAuthorization.Boundary.CLAIM));
    }

    @Test void interrupted_caller_remains_interrupted_and_the_check_fails_closed() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var result = new java.util.concurrent.atomic.AtomicReference<AgentRunAuthorization.Decision>();
        var interrupted = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(inv -> { entered.countDown(); release.await(5, TimeUnit.SECONDS); return decision(NOW.plusSeconds(30)); })
                .when(client).decide(any(), any());
        Thread caller = Thread.ofPlatform().daemon().start(() -> {
            result.set(authorizer.authorize(request, AgentRunAuthorization.Boundary.CLAIM));
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); caller.interrupt(); caller.join(5000);
            assertFalse(caller.isAlive()); assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, result.get()); assertTrue(interrupted.get());
        } finally { release.countDown(); }
    }

    @Test void invalid_limits_or_disabled_grants_never_open_a_runtime() {
        assertThrows(IllegalArgumentException.class, () -> create(Duration.ofMillis(99), 1));
        assertThrows(IllegalArgumentException.class, () -> create(Duration.ofSeconds(31), 1));
        assertThrows(IllegalArgumentException.class, () -> create(Duration.ofSeconds(1), 9));
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(TianjiExecutionAuthorizationConfiguration.class)
                .run(context -> assertTrue(context.getBeansOfType(TianjiExecutionGrantAuthorizer.class).isEmpty()));
        assertThrows(IllegalStateException.class, () -> new TianjiExecutionAuthorizationConfiguration()
                .tianjiExecutionGrantAuthorizer(null, null, null, null, null, null, null, null,
                        new org.springframework.mock.env.MockEnvironment()));
    }

    private TianjiExecutionGrantAuthorizer create(Duration timeout, int concurrency) {
        return new TianjiExecutionGrantAuthorizer(ledger, store, RESOURCE, "tianji", issuers,
                Map.of(actor.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(ISSUER, "21", client)),
                timeout, concurrency, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ExecutionAuthorizationContract.Decision decision(Instant expiresAt) {
        return new ExecutionAuthorizationContract.Decision(ID, ISSUER.toString(), "21", "alice", RESOURCE.toString(),
                AgentRunAuthorization.PURPOSE, RUN, AgentExecutionGrant.fingerprint(request, RESOURCE), true,
                request.deadlineAt(), expiresAt, 2, 3);
    }
}
