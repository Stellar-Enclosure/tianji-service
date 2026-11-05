package top.egon.cola.platform.tianji.agent.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeCommand;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeRequest;
import top.egon.cola.platform.tianji.agent.server.security.TianjiCheckpointResumeGrantAuthorizer.BoundResumeTicket;
import top.egon.cola.platform.tianji.agent.server.security.TianjiCheckpointResumeGrantAuthorizer.ResumeTicketSource;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient.Expectation;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiCheckpointResumeGrantAuthorizerTest {
    private static final URI ISSUER = URI.create("https://idp.example/tenants/21");
    private static final URI RESOURCE = URI.create("https://api.example/tianji");
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private static final String RUN = "00000000-0000-0000-0000-000000000123";
    private static final String CUT = "d".repeat(64);
    private static final String GRANT_ID = "9" + "1".repeat(62);

    private final TrustedAgentActor actor = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toString());
    private final ResumeRequest request = request(actor, "resume-1", 4, CUT, NOW.plusSeconds(600));
    private final Expectation expected = TianjiCheckpointResumeGrantAuthorizer.expectation(request, ISSUER, RESOURCE);
    private final AgentExecutionGrant.Ticket ticket = new AgentExecutionGrant.Ticket(GRANT_ID, "ENC:v1:key:nonce:sealed", NOW.plusSeconds(60));
    private final BoundResumeTicket boundTicket = new BoundResumeTicket(expected, ticket);
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
    private final ResumeTicketSource source = mock(ResumeTicketSource.class);
    private final HttpExecutionAuthorizationClient client = mock(HttpExecutionAuthorizationClient.class);
    private final TrustedIssuerRegistry.TrustedIssuerVO trust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,
            URI.create("https://idp.example/jwks"), "tenant:21", TrustedIssuerRegistry.RealmType.TENANT, "21",
            TrustedIssuerRegistry.Status.ACTIVE, 1);

    private TianjiCheckpointResumeGrantAuthorizer authority;

    @BeforeEach
    void setup() {
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trust));
        when(source.current(expected)).thenReturn(Optional.of(boundTicket));
        when(client.decide(any(), any())).thenReturn(decision(NOW.plusSeconds(30)));
        authority = create(Duration.ofSeconds(1));
    }

    @AfterEach
    void close() {
        authority.close();
        MDC.clear();
    }

    @Test
    void uses_exact_independent_resume_binding_on_every_current_decision() {
        MDC.put("traceId", "a".repeat(32));
        MDC.put("spanId", "b".repeat(16));
        doAnswer(invocation -> {
            assertEquals("a".repeat(32), MDC.get("traceId"));
            return decision(NOW.plusSeconds(30));
        }).when(client).decide(any(), any());
        for (int i = 0; i < 2; i++) {
            var permit = authority.authorize(request).orElseThrow();
            assertEquals(request, permit.request());
            assertEquals(NOW.plusSeconds(30), permit.expiresAt());
        }
        var wire = ArgumentCaptor.forClass(ExecutionAuthorizationContract.Request.class);
        var binding = ArgumentCaptor.forClass(Expectation.class);
        verify(client, times(2)).decide(wire.capture(), binding.capture());
        assertTrue(wire.getAllValues().stream().allMatch(value -> value.equals(
                new ExecutionAuthorizationContract.Request("tianji", GRANT_ID, ticket.ciphertext()))));
        assertTrue(binding.getAllValues().stream().allMatch(expected::equals));
        assertEquals(AgentCheckpointTakeoverService.PURPOSE, expected.purpose());
        assertNotEquals(AgentRunAuthorization.PURPOSE, expected.purpose());
        assertEquals(request.bindingSha256(RESOURCE), expected.bindingSha256());
        assertEquals(actor.subjectId(), expected.subject());
        assertEquals("21", expected.tenantId());
        assertTrue(expected.sessionRequired());
        verify(source, times(4)).current(expected);
        assertTrue(authority.usesTicketSource(source));
        assertFalse(authority.usesTicketSource(ignored -> Optional.empty()));
        assertTrue(authority.usesResource(RESOURCE));
        assertFalse(authority.usesResource(URI.create("https://API.example/tianji")));
    }

    @Test
    void deterministic_uuid_separates_exact_owner_command_revision_cut_resource_and_deadline() {
        assertEquals(expected, TianjiCheckpointResumeGrantAuthorizer.expectation(request, ISSUER, RESOURCE));
        assertEquals("0ef39a57-92da-8574-a07b-266bdd2260de", expected.intentId());
        assertEquals(8, UUID.fromString(expected.intentId()).version());
        assertEquals(2, UUID.fromString(expected.intentId()).variant());
        var otherUser = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "bob", ISSUER.toString());
        for (var changed : java.util.List.of(
                request(otherUser, "resume-1", 4, CUT, NOW.plusSeconds(600)),
                request(actor, "resume-2", 4, CUT, NOW.plusSeconds(600)),
                request(actor, "resume-1", 5, CUT, NOW.plusSeconds(600)),
                request(actor, "resume-1", 4, "e".repeat(64), NOW.plusSeconds(600)),
                request(actor, "resume-1", 4, CUT, NOW.plusSeconds(601)))) {
            assertNotEquals(expected.intentId(),
                    TianjiCheckpointResumeGrantAuthorizer.expectation(changed, ISSUER, RESOURCE).intentId());
        }
        assertNotEquals(expected.intentId(), TianjiCheckpointResumeGrantAuthorizer.expectation(request, ISSUER,
                URI.create("https://api.example/other")).intentId());
        assertNotEquals(RUN, expected.intentId());
    }

    @Test
    void refuses_ticket_for_another_cut_before_calling_jianshen() {
        var changed = request(actor, "resume-1", 4, "e".repeat(64), NOW.plusSeconds(600));
        var other = new BoundResumeTicket(TianjiCheckpointResumeGrantAuthorizer.expectation(changed, ISSUER, RESOURCE), ticket);
        when(source.current(expected)).thenReturn(Optional.of(other));
        assertUnavailable(request);
        verifyNoInteractions(client);
    }

    @Test
    void refuses_untrusted_owner_or_missing_expired_or_ordinary_ticket_without_call() {
        var foreign = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://other.example/21");
        var service = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "alice", ISSUER.toString());
        var otherTenant = TrustedAgentActor.verified(22, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toString());
        for (var altered : java.util.List.of(foreign, service, otherTenant, new TrustedAgentActor(21, TrustedAgentActor.SubjectType.USER, "alice"))) {
            assertUnavailable(request(altered, "resume-1", 4, CUT, NOW.plusSeconds(600)));
        }
        var ordinary = new Expectation(ISSUER, "21", "alice", RESOURCE, AgentRunAuthorization.PURPOSE, RUN,
                "a".repeat(64), true, request.execution().deadlineAt());
        assertThrows(IllegalArgumentException.class, () -> new BoundResumeTicket(ordinary, ticket));
        when(source.current(expected)).thenReturn(Optional.empty());
        assertUnavailable(request);
        var expired = new AgentExecutionGrant.Ticket(GRANT_ID, ticket.ciphertext(), NOW);
        when(source.current(expected)).thenReturn(Optional.of(new BoundResumeTicket(expected, expired)));
        assertUnavailable(request);
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.empty());
        assertUnavailable(request);
        verifyNoInteractions(client);
    }

    @Test
    void denial_requires_the_exact_ticket_and_issuer_to_remain_current() {
        doThrow(new HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException()).when(client).decide(any(), any());
        assertEquals(Optional.empty(), authority.authorize(request));
        when(source.current(expected)).thenReturn(Optional.of(boundTicket), Optional.empty());
        assertUnavailable(request);
        when(source.current(expected)).thenReturn(Optional.of(boundTicket));
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trust), Optional.empty());
        assertUnavailable(request);
    }

    @Test
    void a_decision_must_remain_current_and_fit_inside_its_opaque_ticket() {
        when(client.decide(any(), any())).thenReturn(decision(ticket.expiresAt().plusNanos(1)));
        assertUnavailable(request);
        when(client.decide(any(), any())).thenReturn(decision(NOW));
        assertUnavailable(request);
        when(client.decide(any(), any())).thenReturn(decision(NOW.plusSeconds(30)));
        when(source.current(expected)).thenReturn(Optional.of(boundTicket), Optional.empty());
        assertUnavailable(request);
    }

    @Test
    void transaction_shutdown_and_deadline_fail_before_external_calls() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertUnavailable(request);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertUnavailable(request(actor, "resume-1", 4, CUT, NOW));
        authority.close();
        assertUnavailable(request);
        verifyNoInteractions(source, client);
    }

    @Test
    void timeout_and_saturation_are_bounded_even_when_provider_ignores_interrupt() throws Exception {
        authority.close();
        authority = create(Duration.ofMillis(150));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    // An uncooperative provider cannot grow the executor or queue.
                }
            }
            return decision(NOW.plusSeconds(30));
        }).when(client).decide(any(), any());
        try {
            assertUnavailable(request);
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertUnavailable(request);
            verify(client, times(1)).decide(any(), any());
        } finally {
            release.countDown();
        }
    }

    private TianjiCheckpointResumeGrantAuthorizer create(Duration timeout) {
        return new TianjiCheckpointResumeGrantAuthorizer(RESOURCE, "tianji", issuers, Map.of(ISSUER, client),
                source, timeout, 1, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void assertUnavailable(ResumeRequest candidate) {
        var error = assertThrows(AgentLedgerException.class, () -> authority.authorize(candidate));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, error.code());
        assertNull(error.getCause());
    }

    private ExecutionAuthorizationContract.Decision decision(Instant expiry) {
        return new ExecutionAuthorizationContract.Decision(GRANT_ID, ISSUER.toASCIIString(), expected.tenantId(),
                expected.subject(), RESOURCE.toASCIIString(), expected.purpose(), expected.intentId(), expected.bindingSha256(),
                true, expected.deadlineAt(), expiry, 2, 3);
    }

    private static ResumeRequest request(TrustedAgentActor actor, String command, long revision, String cut, Instant deadline) {
        var run = new AgentRunAuthorization.Request(actor, RUN, "review", 1, "a".repeat(64), "b".repeat(64),
                "c".repeat(64), "policy-v1", true, deadline);
        return new ResumeRequest(run, new ResumeCommand(RUN, command, revision), cut);
    }
}
