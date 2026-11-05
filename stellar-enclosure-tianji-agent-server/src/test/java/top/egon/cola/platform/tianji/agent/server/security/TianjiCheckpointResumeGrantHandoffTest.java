package top.egon.cola.platform.tianji.agent.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.Acknowledgement;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeCommand;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeRequest;
import top.egon.cola.platform.tianji.agent.recovery.TianjiAgentCheckpointResumeConfiguration.ResumeHost;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient.Expectation;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;
import top.egon.cola.platform.tianquan.shoubing.contract.AuthenticationContext;
import top.egon.cola.platform.tianquan.shoubing.contract.IdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TianjiCheckpointResumeGrantHandoffTest {
    private static final URI ISSUER = URI.create("https://idp.example/tenants/21");
    private static final URI RESOURCE = URI.create("https://api.example/tianji");
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private static final Instant OWNER_EXPIRY = NOW.plusSeconds(60);
    private static final Instant GRANT_EXPIRY = NOW.plusSeconds(25);
    private static final Instant DEADLINE = NOW.plusSeconds(90);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String USER_BEARER = "user-proof-only-in-request";
    private static final String CIPHERTEXT = "ENC:v1:key:nonce:sealed";
    private static final String GRANT_ID = "9" + "1".repeat(62);
    private static final String RUN = "00000000-0000-0000-0000-000000000123";
    private static final ResumeCommand COMMAND = new ResumeCommand(RUN, "resume-1", 3);
    private static final Acknowledgement ACK = new Acknowledgement(RUN, AgentRunStatusEnum.RUNNING, 1, 5);
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER,
            "alice", ISSUER.toASCIIString());
    private static final TianjiAgentAccess.Authorized AUTHORIZED = new TianjiAgentAccess.Authorized(OWNER, OWNER_EXPIRY, ISSUER);
    private static final AgentRunAuthorization.Request EXECUTION = new AgentRunAuthorization.Request(OWNER, RUN, "review", 1,
            "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE);
    private static final ResumeRequest PREVIEW = new ResumeRequest(EXECUTION, COMMAND, "d".repeat(64));
    private static final Expectation EXPECTED = TianjiCheckpointResumeGrantAuthorizer.expectation(PREVIEW, ISSUER, RESOURCE);

    private MockHttpServletRequest servlet;

    @BeforeEach
    void establishVerifiedCaller() {
        servlet = new MockHttpServletRequest();
        servlet.setAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE, ISSUER.toASCIIString());
        VerifiedUserTokenCarrier.set(servlet, USER_BEARER);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servlet));
        setVerifiedAuthentication("request-token-id");
    }

    private void setVerifiedAuthentication(String tokenId) {
        var authentication = mock(Rbac3AuthenticationToken.class);
        var principal = mock(Rbac3UserDetails.class);
        var snapshot = mock(SystemAuthorizationSnapshot.class);
        var permission = mock(EffectivePermissionSnapshot.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.identity()).thenReturn(new IdentityPrincipal(OWNER.subjectId(), "21", tokenId, Set.of("tianji"),
                NOW.minusSeconds(60), OWNER_EXPIRY, AuthenticationContext.of("PASSWORD", NOW.minusSeconds(60))));
        when(principal.snapshot()).thenReturn(snapshot);
        when(snapshot.permissionSnapshot()).thenReturn(permission);
        when(permission.issuer()).thenReturn(ISSUER.toASCIIString());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearCaller() {
        VerifiedUserTokenCarrier.clear(servlet);
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void publishes_the_exact_opaque_ticket_to_a_worker_without_servlet_or_user_bearer() throws Exception {
        try (var fixture = new Fixture()) {
            Thread requestThread = Thread.currentThread();
            when(fixture.issuer.issue(any())).thenAnswer(call -> {
                var request = call.<ExecutionGrantContract.IssueRequest>getArgument(0);
                assertSame(requestThread, Thread.currentThread());
                assertEquals(USER_BEARER, request.userToken());
                assertEquals(AgentCheckpointTakeoverService.PURPOSE, request.purpose());
                assertEquals(EXPECTED.intentId(), request.intentId());
                assertEquals(EXPECTED.bindingSha256(), request.bindingSha256());
                assertEquals(DEADLINE, request.deadlineAt());
                assertTrue(request.sessionRequired());
                assertFalse(request.toString().contains(USER_BEARER));
                assertTrue(fixture.handoff.current(EXPECTED).isEmpty());
                return issued(GRANT_EXPIRY);
            });
            when(fixture.host.dispatch(OWNER, COMMAND)).thenAnswer(call -> {
                var bound = fixture.handoff.current(EXPECTED).orElseThrow();
                assertEquals(EXPECTED, bound.binding());
                assertEquals(GRANT_ID, bound.ticket().grantId());
                assertEquals(CIPHERTEXT, bound.ticket().ciphertext());
                assertEquals(GRANT_EXPIRY, bound.ticket().expiresAt());
                assertFalse(bound.toString().contains(USER_BEARER));
                assertFalse(bound.toString().contains(CIPHERTEXT));
                return fixture.pending.minimalCompletionStage();
            });
            when(fixture.jianshen.decide(any(), eq(EXPECTED))).thenAnswer(call -> {
                var request = call.<ExecutionAuthorizationContract.Request>getArgument(0);
                assertNull(RequestContextHolder.getRequestAttributes());
                assertNull(SecurityContextHolder.getContext().getAuthentication());
                assertNull(VerifiedUserTokenCarrier.currentOrNull());
                assertEquals(new ExecutionAuthorizationContract.Request("tianji", GRANT_ID, CIPHERTEXT), request);
                return decision();
            });

            var caller = fixture.submit().toCompletableFuture();
            assertTrue(caller.cancel(true));
            assertTrue(fixture.handoff.current(EXPECTED).isPresent());
            CompletableFuture.runAsync(() -> {
                assertNull(RequestContextHolder.getRequestAttributes());
                var permit = fixture.authority.authorize(PREVIEW).orElseThrow();
                assertEquals(PREVIEW, permit.request());
                assertEquals(GRANT_EXPIRY, permit.expiresAt());
                fixture.pending.complete(ACK);
            }).get(5, TimeUnit.SECONDS);
            assertEquals(ACK, fixture.pending.join());
            verify(fixture.issuer).issue(any());
            verify(fixture.jianshen).decide(any(), eq(EXPECTED));
        }
    }

    @Test
    void clears_the_published_ticket_when_dispatch_completes() {
        try (var fixture = new Fixture()) {
            var stage = fixture.submit();
            assertTrue(fixture.handoff.current(EXPECTED).isPresent());
            fixture.pending.complete(ACK);
            assertEquals(ACK, stage.toCompletableFuture().join());
            assertTrue(fixture.handoff.current(EXPECTED).isEmpty(), "completion must release the opaque RESUME ticket");
        }
    }

    @Test
    void a_duplicate_or_full_handoff_does_not_issue_another_grant() {
        try (var fixture = new Fixture()) {
            fixture.submit();
            assertCode(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, fixture::submit);

            var another = new ResumeCommand(RUN, "resume-2", 3);
            when(fixture.host.previewAuthorization(OWNER, another)).thenReturn(
                    Optional.of(new ResumeRequest(EXECUTION, another, PREVIEW.cutSha256())));
            assertCode(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED,
                    () -> fixture.handoff.submit(AUTHORIZED, another, fixture.host, fixture.authority));
            verify(fixture.issuer).issue(any());
            verify(fixture.host).dispatch(OWNER, COMMAND);
            verify(fixture.host, never()).dispatch(OWNER, another);
            fixture.pending.complete(ACK);
        }
    }

    @Test
    void an_exact_committed_replay_skips_grant_issuance() {
        try (var fixture = new Fixture()) {
            when(fixture.host.previewAuthorization(OWNER, COMMAND)).thenReturn(Optional.empty());
            fixture.pending.complete(ACK);

            assertEquals(ACK, fixture.submit().toCompletableFuture().join());
            assertTrue(fixture.handoff.current(EXPECTED).isEmpty());
            verifyNoInteractions(fixture.issuer, fixture.jianshen);
            verify(fixture.host).dispatch(OWNER, COMMAND);
        }
    }

    @Test
    void an_inexact_request_identity_or_preview_fails_before_issuance() {
        try (var fixture = new Fixture()) {
            when(fixture.host.usesAuthorization(fixture.authority)).thenReturn(false);
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, fixture::submit);
            when(fixture.host.usesAuthorization(fixture.authority)).thenReturn(true);
            servlet.setAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE, "https://other.example/tenants/21");
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, fixture::submit);
            servlet.setAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE, ISSUER.toASCIIString());
            var other = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "someone-else", ISSUER.toASCIIString());
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, () -> fixture.handoff.submit(
                    new TianjiAgentAccess.Authorized(other, OWNER_EXPIRY, ISSUER), COMMAND, fixture.host, fixture.authority));
            verify(fixture.host, never()).previewAuthorization(any(), any());

            when(fixture.host.previewAuthorization(OWNER, COMMAND)).thenReturn(Optional.of(new ResumeRequest(EXECUTION,
                    new ResumeCommand(RUN, "different-command", 3), PREVIEW.cutSha256())));
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, fixture::submit);
            verifyNoInteractions(fixture.issuer, fixture.jianshen);
            verify(fixture.host, never()).dispatch(any(), any());
        }
    }

    @Test
    void denied_expired_or_stale_issuance_never_publishes_a_ticket() {
        try (var denied = new Fixture()) {
            when(denied.issuer.issue(any())).thenThrow(new HttpExecutionGrantClient.ExecutionGrantDeniedException());
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, denied::submit);
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, denied::submit);
            verify(denied.issuer, times(2)).issue(any());
            verify(denied.host, never()).dispatch(any(), any());
            assertTrue(denied.handoff.current(EXPECTED).isEmpty());
        }
        for (var expiry : new Instant[]{NOW, OWNER_EXPIRY.plusSeconds(1)}) {
            try (var fixture = new Fixture()) {
                when(fixture.issuer.issue(any())).thenReturn(issued(expiry));
                assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, fixture::submit);
                verify(fixture.host, never()).dispatch(any(), any());
                assertTrue(fixture.handoff.current(EXPECTED).isEmpty());
            }
        }
        try (var stale = new Fixture()) {
            when(stale.issuer.issue(any())).thenAnswer(call -> {
                when(stale.issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.empty());
                return issued(GRANT_EXPIRY);
            });
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, stale::submit);
            verify(stale.host, never()).dispatch(any(), any());
            assertTrue(stale.handoff.current(EXPECTED).isEmpty());
        }
        try (var changed = new Fixture()) {
            when(changed.issuer.issue(any())).thenAnswer(call -> {
                setVerifiedAuthentication("replaced-token-id");
                return issued(GRANT_EXPIRY);
            });
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, changed::submit);
            verify(changed.host, never()).dispatch(any(), any());
            assertTrue(changed.handoff.current(EXPECTED).isEmpty());
        }
    }

    @Test
    void failed_dispatch_synchronously_or_asynchronously_releases_the_ticket() {
        try (var fixture = new Fixture()) {
            var stage = fixture.submit();
            var failed = new IllegalStateException("fixture dispatch failed");
            fixture.pending.completeExceptionally(failed);
            var completed = assertThrows(java.util.concurrent.CompletionException.class, stage.toCompletableFuture()::join);
            assertSame(failed, completed.getCause());
            assertTrue(fixture.handoff.current(EXPECTED).isEmpty());
        }
        try (var fixture = new Fixture()) {
            doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "fixture"))
                    .when(fixture.host).dispatch(OWNER, COMMAND);
            assertCode(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, fixture::submit);
            assertTrue(fixture.handoff.current(EXPECTED).isEmpty());
        }
    }

    @Test
    void ambient_transaction_or_missing_verified_user_proof_never_mints() {
        try (var fixture = new Fixture()) {
            TransactionSynchronizationManager.initSynchronization();
            try {
                assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, fixture::submit);
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            verifyNoInteractions(fixture.host);
            VerifiedUserTokenCarrier.clear(servlet);
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, fixture::submit);
            assertTrue(fixture.handoff.current(EXPECTED).isEmpty());
            verifyNoInteractions(fixture.issuer, fixture.jianshen);
            verify(fixture.host, never()).dispatch(any(), any());
        }
    }

    private static ExecutionGrantContract.Issued issued(Instant expiresAt) {
        return new ExecutionGrantContract.Issued(new ExecutionGrantContract.Ticket(GRANT_ID, CIPHERTEXT), expiresAt);
    }

    private static ExecutionAuthorizationContract.Decision decision() {
        return new ExecutionAuthorizationContract.Decision(GRANT_ID, ISSUER.toASCIIString(), "21", OWNER.subjectId(),
                RESOURCE.toASCIIString(), EXPECTED.purpose(), EXPECTED.intentId(), EXPECTED.bindingSha256(), true,
                DEADLINE, GRANT_EXPIRY, 1, 1);
    }

    private static void assertCode(AgentErrorCodeEnum expected, org.junit.jupiter.api.function.Executable work) {
        var error = assertThrows(AgentLedgerException.class, work);
        assertEquals(expected, error.code());
        assertFalse(error.toString().contains(USER_BEARER));
        assertFalse(error.toString().contains(CIPHERTEXT));
    }

    private static final class Fixture implements AutoCloseable {
        private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
        private final HttpExecutionGrantClient issuer = mock(HttpExecutionGrantClient.class);
        private final HttpExecutionAuthorizationClient jianshen = mock(HttpExecutionAuthorizationClient.class);
        private final ResumeHost host = mock(ResumeHost.class);
        private final CompletableFuture<Acknowledgement> pending = new CompletableFuture<>();
        private final TianjiCheckpointResumeGrantHandoff handoff =
                new TianjiCheckpointResumeGrantHandoff(RESOURCE, issuers, Map.of(ISSUER, issuer), 1, CLOCK);
        private final TianjiCheckpointResumeGrantAuthorizer authority = new TianjiCheckpointResumeGrantAuthorizer(RESOURCE,
                "tianji", issuers, Map.of(ISSUER, jianshen), handoff, Duration.ofSeconds(2), 1, CLOCK);

        private Fixture() {
            when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(
                    new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"), "tenant:21",
                            TrustedIssuerRegistry.RealmType.TENANT, "21", TrustedIssuerRegistry.Status.ACTIVE, 1)));
            when(issuer.issue(any())).thenReturn(issued(GRANT_EXPIRY));
            when(host.usesAuthorization(authority)).thenReturn(true);
            when(host.previewAuthorization(OWNER, COMMAND)).thenReturn(Optional.of(PREVIEW));
            when(host.dispatch(OWNER, COMMAND)).thenReturn(pending.minimalCompletionStage());
        }

        private java.util.concurrent.CompletionStage<Acknowledgement> submit() {
            return handoff.submit(AUTHORIZED, COMMAND, host, authority);
        }

        @Override public void close() {
            pending.completeExceptionally(new IllegalStateException("fixture closed"));
            authority.close();
        }
    }
}
