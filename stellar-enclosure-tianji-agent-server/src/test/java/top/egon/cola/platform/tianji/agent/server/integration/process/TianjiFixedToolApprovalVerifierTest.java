package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.MDC;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolApprovalBinding;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalClient;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalRequesterTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseClient;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;
import top.egon.cola.platform.tianji.contract.orchestration.VerifyApprovalQuery;
import top.egon.cola.platform.tianquan.shoubing.contract.OAuthSecurityVersionBO;
import top.egon.cola.platform.tianquan.shoubing.contract.PrincipalType;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.client.ServiceAuthorizationKey;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TenantIssuerAuthenticationManagerComponent;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedPrincipalConverter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.domain.VerifiedJwtBO;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual approval SDK and shared credential verifier with a controlled JDK HTTP transport. */
@Timeout(20)
class TianjiFixedToolApprovalVerifierTest {
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private static final URI RESOURCE = URI.create("https://api.example/process/20");
    private static final URI BASE = URI.create("https://process.example/base");
    private static final String CLIENT = "agent-executor", APP = "tianji-agent", TOKEN = "fixture.service.token";
    private static final String DEF = "c".repeat(64), POLICY_SHA = "a".repeat(64), BUSINESS_SHA = "b".repeat(64);
    private static final String RUN = "10000000-0000-0000-0000-000000000001", RECEIPT = "approval-reference";
    private static final Set<String> SCOPES = Set.of(ApprovalClient.VERIFY_SCOPE);
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toASCIIString());
    private static final TrustedAgentActor CONSUMER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, CLIENT, ISSUER.toASCIIString());

    @Test void affinity_is_pointer_exact_and_constructor_bounds_do_not_use_or_close_caller_dependencies() {
        try (var fixture = new Fixture()) {
            clearInvocations(fixture.oauth, fixture.authentication, fixture.converter, fixture.issuers, fixture.http);
            assertAll(
                    () -> assertTrue(fixture.verifier.usesBindings(fixture.bindings)),
                    () -> assertTrue(fixture.verifier.usesOAuth(fixture.oauth)),
                    () -> assertTrue(fixture.verifier.usesAuthentication(fixture.authentication)),
                    () -> assertTrue(fixture.verifier.usesConverter(fixture.converter)),
                    () -> assertTrue(fixture.verifier.usesIssuerRegistry(fixture.issuers)),
                    () -> assertTrue(fixture.verifier.usesHttpClient(fixture.http)),
                    () -> assertFalse(fixture.verifier.usesBindings(mock(TianjiFixedToolApprovalBindings.class))),
                    () -> assertFalse(fixture.verifier.usesOAuth(mock(IdpServiceOAuth2Client.class))),
                    () -> assertFalse(fixture.verifier.usesAuthentication(mock(TenantIssuerAuthenticationManagerComponent.class))),
                    () -> assertFalse(fixture.verifier.usesConverter(mock(VerifiedPrincipalConverter.class))),
                    () -> assertFalse(fixture.verifier.usesIssuerRegistry(mock(TrustedIssuerRegistry.class))),
                    () -> assertFalse(fixture.verifier.usesHttpClient(mock(HttpClient.class))));
            verifyNoInteractions(fixture.oauth, fixture.authentication, fixture.converter, fixture.issuers, fixture.http);
        }
        var never = mock(HttpClient.class); when(never.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        var always = mock(HttpClient.class); when(always.followRedirects()).thenReturn(HttpClient.Redirect.ALWAYS);
        var bindings = mock(TianjiFixedToolApprovalBindings.class);
        var oauth = mock(IdpServiceOAuth2Client.class); var authentication = mock(TenantIssuerAuthenticationManagerComponent.class);
        var converter = mock(VerifiedPrincipalConverter.class); var issuers = mock(TrustedIssuerRegistry.class);
        assertThrows(IllegalArgumentException.class, () -> new TianjiFixedToolApprovalVerifier(bindings, oauth, authentication, converter, issuers, never, 0));
        assertThrows(IllegalArgumentException.class, () -> new TianjiFixedToolApprovalVerifier(bindings, oauth, authentication, converter, issuers, never, 9));
        assertThrows(IllegalArgumentException.class, () -> new TianjiFixedToolApprovalVerifier(bindings, oauth, authentication, converter, issuers, always, 1));
        assertThrows(NullPointerException.class, () -> new TianjiFixedToolApprovalVerifier(null, oauth, authentication, converter, issuers, never, 1));
    }

    @Test void verify_only_service_uses_one_strict_sdk_post_and_delivers_the_flat_source_observation_once() {
        try (var fixture = new Fixture()) {
            var seen = new AtomicReference<FixedToolApprovalVerifier.ApprovedObservation>(); var callbacks = new AtomicInteger();
            assertEquals("recorded", fixture.verifier.withVerifiedApproval(fixture.request(), observation -> {
                callbacks.incrementAndGet(); seen.set(observation); return "recorded";
            }));
            assertEquals(1, callbacks.get()); var observation = seen.get();
            assertEquals("approval-primary", observation.sourceId()); assertEquals(ISSUER.toASCIIString(), observation.sourceIssuer());
            assertEquals(RESOURCE.toASCIIString(), observation.sourceResource()); assertSame(fixture.approvalBinding, observation.binding());
            assertEquals(fixture.approvalProof.get(), observation.approval()); assertEquals(CONSUMER, observation.consumer());
            assertEquals(fixture.now.plusSeconds(30), observation.consumerExpiresAt()); assertEquals(fixture.now.plusSeconds(25), observation.expiresAt());
            assertEquals(20, observation.tenantId());
            assertEquals(OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_APPROVAL_SOURCE_V1", "approval-primary", "20", ISSUER.toASCIIString(), RESOURCE.toASCIIString()), Map.of()), observation.sourceNamespaceSha256());
            verify(fixture.oauth).authorizeBounded(eq(fixture.context), eq(ISSUER.toASCIIString()), argThat(timeout -> timeout.compareTo(Duration.ZERO) > 0 && timeout.compareTo(Duration.ofSeconds(30)) <= 0));
            verify(fixture.authentication).authenticate(TOKEN, RESOURCE); verify(fixture.converter).toServicePrincipal(fixture.identityProof.get().getJwt(), RESOURCE);
            assertEquals(SCOPES, fixture.context.scopes()); assertFalse(SCOPES.contains(ProcessReleaseClient.READ_SCOPE));
            assertEquals(1, fixture.requests.size()); var request = fixture.requests.getFirst();
            assertEquals("POST", request.method()); assertEquals(BASE.getPath() + ApprovalClient.PATH, request.uri().getPath()); assertEquals("Bearer " + TOKEN, request.headers().firstValue("Authorization").orElseThrow());
            assertEquals(fixture.approvalBinding.query(fixture.action, RECEIPT), fixture.mapper.readValue(body(request), VerifyApprovalQuery.class));
        }
    }

    @Test void user_origin_is_not_obo_and_the_business_start_binding_sha_is_not_an_approval_backend_hash() {
        try (var fixture = new Fixture()) {
            assertNotEquals(fixture.descriptor.policy().bindingSha256(), fixture.approvalBinding.policyBindingSha256());
            var observed = new AtomicReference<FixedToolApprovalVerifier.ApprovedObservation>();
            fixture.verifier.withVerifiedApproval(fixture.request(), value -> { observed.set(value); return value; });
            assertEquals(TrustedAgentActor.SubjectType.USER, fixture.request().action().owner().subjectType());
            assertEquals(ApprovalRequesterTypeEnum.USER, observed.get().approval().requesterType());
            assertEquals(OWNER.subjectId(), observed.get().approval().requesterSubjectId()); assertEquals(CONSUMER, observed.get().consumer());
            assertNotEquals(OWNER, observed.get().consumer()); assertEquals(1, fixture.requests.size());
        }
    }

    @Test void malformed_current_service_credentials_or_inactive_trust_never_reach_http_or_callback() {
        for (String variant : List.of("key-client", "proof-token", "principal-scopes", "inactive")) {
            try (var fixture = new Fixture()) {
                switch (variant) {
                    case "key-client" -> fixture.key.set(ServiceAuthorizationKey.from(fixture.context, ISSUER.toASCIIString(), "other-client", 1));
                    case "proof-token" -> fixture.identityProof.set(identityProof(jwt("other.token", fixture.now.minusSeconds(1), fixture.now.plusSeconds(40), SCOPES), fixture.trust.get()));
                    case "principal-scopes" -> fixture.replacePrincipal(principal(Set.of(ProcessReleaseClient.READ_SCOPE), fixture.now));
                    case "inactive" -> fixture.trust.set(trust(TrustedIssuerRegistry.Status.INACTIVE, 1));
                    default -> throw new AssertionError("Unexpected credential variant");
                }
                var callback = new AtomicBoolean(); assertFailure(FixedToolApprovalVerifier.FailureKind.CREDENTIAL_UNAVAILABLE,
                        () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> { callback.set(true); return approval; }));
                assertFalse(callback.get(), variant); assertTrue(fixture.requests.isEmpty(), variant);
            }
        }
    }

    @Test void false_stale_or_pinned_workflow_mismatch_is_denied_after_one_fresh_projection_without_callback() {
        for (String variant : List.of("false", "stale", "workflow")) {
            try (var fixture = new Fixture()) {
                if (variant.equals("false")) fixture.approvalProof.set(fixture.approval(false, fixture.now.plusSeconds(25), fixture.approvalBinding.definitionSha256(), fixture.approvalBinding.taskDefinitionKey()));
                if (variant.equals("stale")) {
                    fixture.approvalProof.set(fixture.approval(true, fixture.now.plusSeconds(20), fixture.approvalBinding.definitionSha256(), fixture.approvalBinding.taskDefinitionKey()));
                    fixture.afterPost.set(() -> fixture.wall.set(fixture.now.plusSeconds(21)));
                }
                if (variant.equals("workflow")) fixture.approvalProof.set(fixture.approval(true, fixture.now.plusSeconds(25), "9".repeat(64), fixture.approvalBinding.taskDefinitionKey()));
                var callback = new AtomicBoolean(); assertFailure(FixedToolApprovalVerifier.FailureKind.APPROVAL_DENIED,
                        () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> { callback.set(true); return approval; }));
                assertFalse(callback.get(), variant); assertEquals(1, fixture.requests.size(), variant);
            }
        }
    }

    @Test void invalid_echo_oversize_remote_denial_and_remote_failure_use_distinct_safe_categories() {
        try (var fixture = new Fixture()) {
            var value = fixture.approvalProof.get();
            fixture.approvalProof.set(new VerifiedApprovalDTO(value.policyRef(), value.policyBindingSha256(), "9".repeat(64), value.approvalReceiptId(), value.requesterType(), value.requesterSubjectId(),
                    value.sourceStartCommandId(), value.scopeId(), value.taskId(), value.releaseVersion(), value.definitionKey(), value.definitionSha256(), value.taskDefinitionKey(), value.sourceRequestSha256(),
                    value.approved(), value.occurredAt(), value.verifiedAt(), value.expiresAt()));
            assertFailure(FixedToolApprovalVerifier.FailureKind.APPROVAL_INVALID_RESPONSE, () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> approval));
        }
        try (var fixture = new Fixture()) {
            fixture.oversized.set(true);
            assertFailure(FixedToolApprovalVerifier.FailureKind.APPROVAL_INVALID_RESPONSE, () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> approval));
        }
        try (var fixture = new Fixture()) {
            fixture.postStatus.set(403);
            assertFailure(FixedToolApprovalVerifier.FailureKind.APPROVAL_DENIED, () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> approval));
        }
        try (var fixture = new Fixture()) {
            fixture.postStatus.set(500);
            var failure = assertFailure(FixedToolApprovalVerifier.FailureKind.APPROVAL_UNAVAILABLE, () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> approval));
            assertFalse(failure.getMessage().contains(TOKEN)); assertNull(failure.getCause());
        }
    }

    @Test void one_monotonic_entry_window_bounds_oauth_sdk_and_callback_even_if_wall_time_moves_backwards() {
        try (var fixture = new Fixture(Duration.ofMillis(100))) {
            fixture.afterToken.set(() -> { fixture.nano.set(Duration.ofMillis(101).toNanos()); fixture.wall.set(fixture.now.minusSeconds(60)); });
            assertFailure(FixedToolApprovalVerifier.FailureKind.DEADLINE_EXCEEDED, () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> approval));
            assertTrue(fixture.requests.isEmpty());
        }
        try (var fixture = new Fixture()) {
            fixture.requestWindow.set(Duration.ofSeconds(2));
            fixture.verifier.withVerifiedApproval(fixture.request(), approval -> approval);
            assertTrue(fixture.oauthTimeout.get().compareTo(Duration.ZERO) > 0); assertTrue(fixture.oauthTimeout.get().compareTo(Duration.ofSeconds(2)) <= 0);
        }
    }

    @Test void credential_expiry_caps_the_callback_window_independently_of_the_longer_proof_and_entry_window() {
        try (var fixture = new Fixture()) {
            fixture.token.set(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, TOKEN,
                    fixture.now.minusSeconds(1), fixture.now.plusSeconds(4), SCOPES));
            var committed = new AtomicBoolean();
            assertFailure(FixedToolApprovalVerifier.FailureKind.DEADLINE_EXCEEDED,
                    () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> {
                        assertEquals(fixture.now.plusSeconds(4), approval.consumerExpiresAt());
                        assertEquals(fixture.now.plusSeconds(4), approval.expiresAt());
                        committed.set(true); fixture.wall.set(fixture.now.plusSeconds(5)); fixture.nano.set(Duration.ofSeconds(5).toNanos());
                        return "already-committed";
                    }));
            assertTrue(committed.get()); assertEquals(1, fixture.requests.size());
        }
    }

    @Test void a_valid_projection_reaches_the_callback_before_late_key_trust_or_deadline_checks_suppress_return() {
        for (String variant : List.of("key", "trust", "deadline", "proof-expiry")) {
            try (var fixture = new Fixture()) {
                var committed = new AtomicBoolean();
                var expected = Set.of("deadline", "proof-expiry").contains(variant)
                        ? FixedToolApprovalVerifier.FailureKind.DEADLINE_EXCEEDED : FixedToolApprovalVerifier.FailureKind.CREDENTIAL_UNAVAILABLE;
                assertFailure(expected, () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> {
                    committed.set(true);
                    if (variant.equals("key")) fixture.key.set(ServiceAuthorizationKey.from(fixture.context, ISSUER.toASCIIString(), CLIENT, 2));
                    if (variant.equals("trust")) fixture.trust.set(trust(TrustedIssuerRegistry.Status.ACTIVE, 2));
                    if (variant.equals("deadline")) fixture.nano.set(Duration.ofSeconds(31).toNanos());
                    if (variant.equals("proof-expiry")) {
                        fixture.wall.set(fixture.now.plusSeconds(26)); fixture.nano.set(Duration.ofSeconds(26).toNanos());
                    }
                    return "already-committed";
                }));
                assertTrue(committed.get(), variant); assertEquals(1, fixture.requests.size(), variant);
            }
        }
    }

    @Test void caller_handoff_rechecks_key_and_trust_after_the_worker_copies_its_last_local_current_source() {
        for (String variant : List.of("key", "trust")) {
            try (var fixture = new Fixture()) {
                fixture.mutateOnCallerAfterWorkerCurrent(variant); var committed = new AtomicBoolean();
                assertFailure(FixedToolApprovalVerifier.FailureKind.CREDENTIAL_UNAVAILABLE,
                        () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> {
                            committed.set(true); return "already-committed";
                        }));
                assertTrue(committed.get(), variant); assertEquals(1, fixture.requests.size(), variant);
            }
        }
    }

    @Test void callback_foreign_transaction_marker_blocks_postcheck_and_the_reused_worker_before_another_oauth_call() {
        try (var fixture = new Fixture()) {
            var committed = new AtomicBoolean();
            assertFailure(FixedToolApprovalVerifier.FailureKind.CANCELLED,
                    () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> {
                        committed.set(true); TransactionSynchronizationManager.setActualTransactionActive(true);
                        return "already-committed";
                    }));
            assertTrue(committed.get()); assertEquals(1, fixture.requests.size());
            assertFailure(FixedToolApprovalVerifier.FailureKind.CREDENTIAL_UNAVAILABLE,
                    () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> approval));
            verify(fixture.oauth, times(1)).authorizeBounded(any(), anyString(), any());
            assertEquals(1, fixture.requests.size());
        }
    }

    @Test void sdk_expired_projection_is_invalid_response_while_local_fake_clock_staleness_remains_denied() {
        try (var fixture = new Fixture()) {
            var query = fixture.approvalBinding.query(fixture.action, RECEIPT); var source = query.sourceStart();
            fixture.approvalProof.set(new VerifiedApprovalDTO(fixture.approvalBinding.policyRef(), fixture.approvalBinding.policyBindingSha256(),
                    query.fingerprint(), query.approvalReceiptId(), query.requesterType(), query.requesterSubjectId(), source.commandId(), "scope-1", "task-1",
                    source.releaseVersion(), source.definitionKey(), fixture.approvalBinding.definitionSha256(), fixture.approvalBinding.taskDefinitionKey(),
                    OrchestrationRequestDigests.start(source), true, fixture.now.minusSeconds(3), fixture.now.minusSeconds(2), fixture.now.minusSeconds(1)));
            var callback = new AtomicBoolean();
            assertFailure(FixedToolApprovalVerifier.FailureKind.APPROVAL_INVALID_RESPONSE,
                    () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> { callback.set(true); return approval; }));
            assertFalse(callback.get()); assertEquals(1, fixture.requests.size());
        }
    }

    @Test void callback_failures_are_safe_never_retried_and_preserve_only_known_ledger_codes() {
        try (var fixture = new Fixture()) {
            var failure = assertThrows(AgentLedgerException.class, () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "private " + TOKEN);
            }));
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, failure.code()); assertNull(failure.getCause()); assertFalse(failure.getMessage().contains(TOKEN));
            assertEquals(1, fixture.requests.size());
        }
        try (var fixture = new Fixture()) {
            var failure = assertFailure(FixedToolApprovalVerifier.FailureKind.APPROVAL_UNAVAILABLE, () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> {
                throw new IllegalStateException("private " + TOKEN);
            }));
            assertNull(failure.getCause()); assertFalse(failure.getMessage().contains(TOKEN)); assertEquals(1, fixture.requests.size());
        }
    }

    @Test void trace_is_propagated_without_inheriting_request_material_and_ambient_transactions_fail_before_work() {
        var inherited = new InheritableThreadLocal<String>(); inherited.set("private-user-material");
        MDC.put("traceId", "a".repeat(32)); MDC.put("spanId", "b".repeat(16));
        try (var fixture = new Fixture()) {
            assertEquals("ok", fixture.verifier.withVerifiedApproval(fixture.request(), approval -> {
                assertNull(inherited.get()); assertEquals("a".repeat(32), MDC.get("traceId")); return "ok";
            }));
            assertEquals("private-user-material", inherited.get()); assertEquals("a".repeat(32), MDC.get("traceId"));
            MDC.clear();
            assertEquals("clean", fixture.verifier.withVerifiedApproval(fixture.request(), approval -> {
                assertNull(inherited.get()); assertNull(MDC.get("traceId")); assertNull(MDC.get("spanId")); return "clean";
            }));
            assertNull(MDC.get("traceId")); assertNull(MDC.get("spanId"));
        } finally { inherited.remove(); MDC.clear(); }
        try (var fixture = new Fixture()) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertFailure(FixedToolApprovalVerifier.FailureKind.CREDENTIAL_UNAVAILABLE, () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> approval)); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            assertTrue(fixture.requests.isEmpty());
        }
    }

    @Test void caller_interruption_and_close_reject_late_uncooperative_credentials_without_http_or_callback() throws Exception {
        for (boolean close : List.of(false, true)) {
            try (var fixture = new Fixture()) {
                var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var workerFinished = new CountDownLatch(1);
                fixture.oauthWait.set(() -> { entered.countDown(); awaitIgnoringInterrupts(release); workerFinished.countDown(); });
                var failure = new AtomicReference<Throwable>(); var interrupted = new AtomicBoolean(); var callback = new AtomicBoolean();
                Thread caller = Thread.ofPlatform().start(() -> {
                    try { fixture.verifier.withVerifiedApproval(fixture.request(), approval -> { callback.set(true); return approval; }); }
                    catch (Throwable rejected) { failure.set(rejected); interrupted.set(Thread.currentThread().isInterrupted()); }
                });
                try {
                    assertTrue(entered.await(1, TimeUnit.SECONDS));
                    if (close) fixture.verifier.close(); else caller.interrupt();
                    caller.join(1000); assertFalse(caller.isAlive());
                    assertEquals(FixedToolApprovalVerifier.FailureKind.CANCELLED, ((FixedToolApprovalVerifier.Failure) failure.get()).kind());
                    if (!close) assertTrue(interrupted.get());
                } finally { release.countDown(); caller.join(1000); }
                assertTrue(workerFinished.await(1, TimeUnit.SECONDS)); assertFalse(callback.get()); assertTrue(fixture.requests.isEmpty());
                verify(fixture.http, never()).close(); verify(fixture.oauth, never()).close();
            }
        }
    }

    @Test void caller_interruption_and_close_reject_an_uncooperative_late_http_response_without_callback_or_caller_resource_close() throws Exception {
        for (boolean close : List.of(false, true)) {
            try (var fixture = new Fixture()) {
                var post = fixture.holdPost(); var failure = new AtomicReference<Throwable>(); var interrupted = new AtomicBoolean();
                var callback = new CountDownLatch(1);
                Thread caller = Thread.ofPlatform().start(() -> {
                    try { fixture.verifier.withVerifiedApproval(fixture.request(), approval -> { callback.countDown(); return approval; }); }
                    catch (Throwable rejected) { failure.set(rejected); interrupted.set(Thread.currentThread().isInterrupted()); }
                });
                try {
                    assertTrue(post.entered.await(1, TimeUnit.SECONDS));
                    if (close) fixture.verifier.close(); else caller.interrupt();
                    caller.join(1000); assertFalse(caller.isAlive());
                    assertEquals(FixedToolApprovalVerifier.FailureKind.CANCELLED,
                            assertInstanceOf(FixedToolApprovalVerifier.Failure.class, failure.get()).kind());
                    if (!close) assertTrue(interrupted.get());
                } finally { post.release.countDown(); caller.join(1000); }
                assertTrue(post.returned.await(1, TimeUnit.SECONDS)); assertFalse(callback.await(250, TimeUnit.MILLISECONDS));
                assertEquals(1, fixture.requests.size()); verify(fixture.http, never()).close(); verify(fixture.oauth, never()).close();
            }
        }
    }

    @Test void concurrency_is_unqueued_and_rejected_work_never_starts_a_second_oauth_request() throws Exception {
        try (var fixture = new Fixture()) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            fixture.oauthWait.set(() -> { entered.countDown(); awaitIgnoringInterrupts(release); });
            Thread first = Thread.ofPlatform().start(() -> {
                try { fixture.verifier.withVerifiedApproval(fixture.request(), approval -> approval); }
                catch (RuntimeException expected) { }
            });
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS));
                assertFailure(FixedToolApprovalVerifier.FailureKind.CREDENTIAL_UNAVAILABLE, () -> fixture.verifier.withVerifiedApproval(fixture.request(), approval -> approval));
                verify(fixture.oauth, times(1)).authorizeBounded(any(), anyString(), any());
            } finally { fixture.verifier.close(); release.countDown(); first.join(1000); }
        }
    }

    private static FixedToolApprovalVerifier.Failure assertFailure(FixedToolApprovalVerifier.FailureKind kind,
            org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(FixedToolApprovalVerifier.Failure.class, operation); assertEquals(kind, failure.kind()); assertNull(failure.getCause()); return failure;
    }

    private static final class Fixture implements AutoCloseable {
        final Instant now = Instant.now();
        final IdpServiceOAuth2Client oauth = mock(IdpServiceOAuth2Client.class);
        final TenantIssuerAuthenticationManagerComponent authentication = mock(TenantIssuerAuthenticationManagerComponent.class);
        final VerifiedPrincipalConverter converter = mock(VerifiedPrincipalConverter.class);
        final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
        final HttpClient http = mock(HttpClient.class);
        final JsonMapper mapper = JsonMapper.builder().build();
        final ToolApprovalBinding approvalBinding = new ToolApprovalBinding(CONSUMER, "approval.v1", POLICY_SHA,
                "approval-release", "approval_process", "d".repeat(64), "review");
        final ToolDescriptor descriptor = new ToolDescriptor("process.invoice.approved", 1, "approved_invoice", "Approved invoice START",
                new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
                new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
                new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.invoice.start", ProcessClient.START_RESOURCE_KEY, BUSINESS_SHA, approvalBinding.policyRef()));
        final ToolActionRequest action = new ToolActionRequest(OWNER, descriptor, "invoice-1", "version-7", Map.of("amount", 20L));
        final TianjiFixedToolApprovalBindings.BackendVO backend;
        final TianjiFixedToolApprovalBindings.BindingVO profile;
        final TianjiFixedToolApprovalBindings bindings;
        final IdpServiceTokenRequest context = new IdpServiceTokenRequest("approval-tools", APP, RESOURCE, ServiceTokenContext.TENANT, "20", SCOPES);
        final AtomicReference<ServiceAuthorizationKey> key = new AtomicReference<>(ServiceAuthorizationKey.from(context, ISSUER.toASCIIString(), CLIENT, 1));
        final AtomicReference<TrustedIssuerRegistry.TrustedIssuerVO> trust = new AtomicReference<>(trust(TrustedIssuerRegistry.Status.ACTIVE, 1));
        final AtomicReference<OAuth2AccessToken> token = new AtomicReference<>();
        final AtomicReference<ServiceIdentityPrincipal> principal = new AtomicReference<>();
        final AtomicReference<VerifiedJwtBO> identityProof = new AtomicReference<>();
        final AtomicReference<VerifiedApprovalDTO> approvalProof = new AtomicReference<>();
        final AtomicReference<Instant> wall = new AtomicReference<>(now);
        final AtomicLong nano = new AtomicLong(), postStatus = new AtomicLong(200);
        final AtomicReference<Duration> requestWindow = new AtomicReference<>(Duration.ofSeconds(30));
        final AtomicReference<Duration> oauthTimeout = new AtomicReference<>();
        final AtomicBoolean oversized = new AtomicBoolean();
        final Thread callerThread = Thread.currentThread();
        final AtomicBoolean keyHandoff = new AtomicBoolean(), trustHandoff = new AtomicBoolean(), workerCurrentCopied = new AtomicBoolean();
        final AtomicInteger workerKeyReads = new AtomicInteger(), workerTrustReads = new AtomicInteger();
        final AtomicReference<Runnable> callerHandoff = new AtomicReference<>(() -> { });
        final AtomicReference<Runnable> oauthWait = new AtomicReference<>(() -> { });
        final AtomicReference<Runnable> afterToken = new AtomicReference<>(() -> { });
        final AtomicReference<Runnable> afterPost = new AtomicReference<>(() -> { });
        final AtomicReference<ControlledPostFuture> controlledPost = new AtomicReference<>();
        final List<HttpRequest> requests = java.util.Collections.synchronizedList(new ArrayList<>());
        final TianjiFixedToolApprovalVerifier verifier;

        Fixture() { this(Duration.ofSeconds(30)); }
        Fixture(Duration timeout) {
            backend = new TianjiFixedToolApprovalBindings.BackendVO("approval-primary", 20, ISSUER, BASE, RESOURCE,
                    "approval-tools", CLIENT, APP, "business", "agent", "test", timeout);
            profile = new TianjiFixedToolApprovalBindings.BindingVO(20, OWNER.issuerFingerprint(), OWNER.subjectType(),
                    "assistant", 1, DEF, descriptor.reference(), approvalBinding, backend);
            bindings = new TianjiFixedToolApprovalBindings(List.of(descriptor), List.of(profile));
            token.set(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, TOKEN, now.minusSeconds(1), now.plusSeconds(40), SCOPES));
            replacePrincipal(principal(SCOPES, now)); approvalProof.set(approval(true, now.plusSeconds(25), approvalBinding.definitionSha256(), approvalBinding.taskDefinitionKey()));
            when(http.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
            when(oauth.authorizationKey(context)).thenAnswer(call -> {
                var value = key.get();
                if (keyHandoff.get() && Thread.currentThread() != callerThread && workerKeyReads.incrementAndGet() == 4) workerCurrentCopied.set(true);
                return value;
            });
            when(oauth.authorizeBounded(eq(context), eq(ISSUER.toASCIIString()), any())).thenAnswer(call -> {
                oauthTimeout.set(call.getArgument(2)); oauthWait.get().run(); afterToken.get().run(); return token.get();
            });
            when(issuers.findByIssuer(ISSUER.toASCIIString())).thenAnswer(call -> {
                var value = trust.get();
                if (trustHandoff.get() && Thread.currentThread() != callerThread && workerTrustReads.incrementAndGet() == 4) workerCurrentCopied.set(true);
                return Optional.of(value);
            });
            when(authentication.authenticate(TOKEN, RESOURCE)).thenAnswer(call -> identityProof.get());
            when(converter.toServicePrincipal(any(), eq(RESOURCE))).thenAnswer(call -> principal.get());
            when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenAnswer(call -> {
                HttpRequest request = call.getArgument(0); requests.add(request); afterPost.get().run();
                var controlled = controlledPost.get(); if (controlled != null) return controlled;
                if (oversized.get()) return oversized(call.getArgument(1));
                long status = postStatus.get(); byte[] bytes = status >= 400
                        ? ("{\"code\":\"" + (status == 403 ? "TIANJI_PERMISSION_DENIED" : "TIANJI_DEPENDENCY_UNAVAILABLE") + "\"}").getBytes(StandardCharsets.UTF_8)
                        : mapper.writeValueAsBytes(approvalProof.get());
                return CompletableFuture.completedFuture(response((int) status, bytes));
            });
            var clock = new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() {
                    if (Thread.currentThread() == callerThread && workerCurrentCopied.compareAndSet(true, false)) callerHandoff.get().run();
                    return wall.get();
                }
            };
            verifier = new TianjiFixedToolApprovalVerifier(bindings, oauth, authentication, converter, issuers, http, 1, clock, nano::get);
        }

        FixedToolApprovalVerifier.Request request() {
            var run = new AgentRunAuthorization.Request(OWNER, RUN, "assistant", 1, DEF, "e".repeat(64), "f".repeat(64),
                    "policy-v1", false, now.plusSeconds(60));
            var lease = new AgentRunLease(RUN, "worker", 1, now.minusSeconds(1));
            return new FixedToolApprovalVerifier.Request(run, lease, descriptor, action, RECEIPT, now.plus(requestWindow.get()));
        }

        VerifiedApprovalDTO approval(boolean approved, Instant expires, String definitionSha, String taskKey) {
            var query = approvalBinding.query(action, RECEIPT); var source = query.sourceStart();
            return new VerifiedApprovalDTO(approvalBinding.policyRef(), approvalBinding.policyBindingSha256(), query.fingerprint(), query.approvalReceiptId(),
                    query.requesterType(), query.requesterSubjectId(), source.commandId(), "scope-1", "task-1", source.releaseVersion(), source.definitionKey(),
                    definitionSha, taskKey, OrchestrationRequestDigests.start(source), approved, now.minusSeconds(1), now, expires);
        }

        void replacePrincipal(ServiceIdentityPrincipal value) {
            principal.set(value); identityProof.set(identityProof(jwt(TOKEN, value.issuedAt(), value.expiresAt(), value.scopes()), trust.get()));
        }
        void mutateOnCallerAfterWorkerCurrent(String variant) {
            if (variant.equals("key")) {
                keyHandoff.set(true); callerHandoff.set(() -> key.set(ServiceAuthorizationKey.from(context, ISSUER.toASCIIString(), CLIENT, 2)));
            } else if (variant.equals("trust")) {
                trustHandoff.set(true); callerHandoff.set(() -> trust.set(trust(TrustedIssuerRegistry.Status.ACTIVE, 2)));
            } else throw new IllegalArgumentException("Unknown handoff variant");
        }
        ControlledPostFuture holdPost() {
            var value = new ControlledPostFuture(response(200, mapper.writeValueAsBytes(approvalProof.get())));
            controlledPost.set(value); return value;
        }
        @Override public void close() { verifier.close(); }
    }

    /** Ignores interruption so the verifier, rather than the HTTP future, must reject the late projection. */
    private static final class ControlledPostFuture extends CompletableFuture<HttpResponse<byte[]>> {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), returned = new CountDownLatch(1);
        private final HttpResponse<byte[]> response;
        ControlledPostFuture(HttpResponse<byte[]> response) { this.response = response; }
        @Override public HttpResponse<byte[]> get(long timeout, TimeUnit unit) {
            entered.countDown(); boolean interrupted = false; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            try {
                while (true) {
                    long remaining = deadline - System.nanoTime(); assertTrue(remaining > 0, "controlled approval POST was never released");
                    try { assertTrue(release.await(remaining, TimeUnit.NANOSECONDS)); return response; }
                    catch (InterruptedException ignored) { interrupted = true; }
                }
            } finally { returned.countDown(); if (interrupted) Thread.currentThread().interrupt(); }
        }
    }

    private static ServiceIdentityPrincipal principal(Set<String> scopes, Instant now) {
        return new ServiceIdentityPrincipal(CLIENT, "20", CLIENT, "42", RESOURCE, 1, scopes, "business", "agent", "test", "credential",
                now.minusSeconds(1), now.plusSeconds(40), APP, ServiceTokenContext.TENANT);
    }
    private static TrustedIssuerRegistry.TrustedIssuerVO trust(TrustedIssuerRegistry.Status status, long version) {
        return new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", status, version);
    }
    private static Jwt jwt(String token, Instant issued, Instant expires, Set<String> scopes) {
        return Jwt.withTokenValue(token).header("alg", "RS256").header("kid", "key-1").issuer(ISSUER.toASCIIString()).subject(CLIENT)
                .issuedAt(issued).expiresAt(expires).audience(List.of(RESOURCE.toASCIIString()))
                .claim("tenant_id", "20").claim("jti", "42").claim("client_id", CLIENT).claim("scope", String.join(" ", scopes)).claim("scope_context", "TENANT")
                .claim("source_biz", "business").claim("source_app", "agent").claim("source_env", "test").claim("app_id", APP).build();
    }
    private static VerifiedJwtBO identityProof(Jwt jwt, TrustedIssuerRegistry.TrustedIssuerVO trust) {
        return VerifiedJwtBO.builder().jwt(jwt).trustedIssuer(trust).subject(CLIENT).tenantId("20").tokenId("42").keyId("key-1")
                .authorizationId("100").clientRegistrationId("101").principalType(PrincipalType.SERVICE).expiresAt(jwt.getExpiresAt())
                .securityVersion(new OAuthSecurityVersionBO(1, 1, null)).build();
    }
    private static HttpResponse<byte[]> response(int status, byte[] body) {
        @SuppressWarnings("unchecked") var response = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (key, value) -> true)); return response;
    }
    private static CompletableFuture<HttpResponse<byte[]>> oversized(HttpResponse.BodyHandler<byte[]> handler) {
        var info = mock(HttpResponse.ResponseInfo.class); var headers = HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (key, value) -> true);
        when(info.statusCode()).thenReturn(200); when(info.headers()).thenReturn(headers); var subscriber = handler.apply(info);
        subscriber.onSubscribe(new Flow.Subscription() { @Override public void request(long count) { } @Override public void cancel() { } });
        subscriber.onNext(List.of(ByteBuffer.allocate(2 * 1_048_576 + 1)));
        return subscriber.getBody().toCompletableFuture().thenApply(bytes -> response(200, bytes));
    }
    private static String body(HttpRequest request) {
        var bytes = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer buffer) { byte[] part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part); }
            @Override public void onError(Throwable failure) { throw new AssertionError("Controlled request body failed"); }
            @Override public void onComplete() { }
        });
        return bytes.toString(StandardCharsets.UTF_8);
    }
    private static void awaitIgnoringInterrupts(CountDownLatch release) {
        boolean done = false; while (!done) { try { release.await(); done = true; } catch (InterruptedException ignored) { } }
    }
}
