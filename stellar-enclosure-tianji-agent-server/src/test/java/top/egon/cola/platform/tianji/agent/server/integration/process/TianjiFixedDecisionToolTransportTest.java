package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport.FailureKind.*;

/** Actual orchestration SDK with a mock JDK HTTP client, not an HTTP listener or a cryptographic identity test. */
class TianjiFixedDecisionToolTransportTest {
    private static final Instant NOW = Instant.parse("2026-10-31T15:30:00Z");
    private static final URI ISSUER = URI.create("https://idp.example/20"), RESOURCE = URI.create("https://api.example/process/20"), BASE = URI.create("https://process.example/base");
    private static final String CLIENT = "agent-executor", APP = "tianji-agent", TOKEN = "fixture.service.token", MANIFEST = "a".repeat(64), MODEL = "b".repeat(64), DEF = "c".repeat(64);
    private static final String RUN = "10000000-0000-0000-0000-000000000001", DECISION_ID = "30000000-0000-0000-0000-000000000001";
    private static final UUID INVOCATION = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Set<String> SCOPES = Set.of(ProcessReleaseClient.READ_SCOPE, DecisionClient.EXECUTE_SCOPE);
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://owner.example/20");

    @Test void verifies_an_independent_service_and_uses_the_actual_sdk_with_only_fixed_backend_inputs() throws Exception {
        try (var fixture = new Fixture()) {
            var seen = new AtomicReference<ReadOnlyDecisionToolTransport.VerifiedExecutor>();
            var observation = fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { seen.set(executor); return operation.evaluate(); });
            assertEquals(ReadOnlyDecisionToolTransport.Status.MATCHED, observation.status());
            assertEquals(DECISION_ID, observation.decisionExecutionId()); assertEquals(Map.of("eligible", true), observation.output()); assertEquals("approved", observation.matchedRuleId());
            assertEquals(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, CLIENT, ISSUER.toASCIIString()), seen.get().actor());
            assertNotEquals(OWNER, seen.get().actor()); assertEquals(NOW.plusSeconds(5), seen.get().expiresAt());
            verify(fixture.oauth).authorizeBounded(eq(fixture.context), eq(ISSUER.toASCIIString()), argThat(duration -> !duration.isNegative() && !duration.isZero() && duration.compareTo(Duration.ofSeconds(5)) <= 0));
            verify(fixture.authentication).authenticate(TOKEN, RESOURCE); verify(fixture.converter).toServicePrincipal(fixture.proof.get().getJwt(), RESOURCE);
            assertEquals(List.of("GET", "POST"), fixture.requests.stream().map(HttpRequest::method).toList());
            assertEquals("/base/api/tianji/v1/process-releases/release-1", fixture.requests.getFirst().uri().getPath());
            assertEquals("/base/api/tianji/v1/decisions/evaluate", fixture.requests.getLast().uri().getPath());
            var body = fixture.mapper.readValue(body(fixture.requests.getLast()), Map.class);
            assertEquals("eligibility", body.get("decisionKey")); assertEquals("release-1", body.get("releaseVersion"));
            assertEquals(fixture.request().downstreamRequestId(), body.get("requestId")); assertEquals(RUN, body.get("correlationId"));
            var inputs = (Map<?, ?>) body.get("inputs"); assertEquals(Set.of("amount"), inputs.keySet()); assertEquals(20L, ((Number) inputs.get("amount")).longValue());
            assertFalse(body.containsKey("tenantId")); assertFalse(body.toString().contains(OWNER.subjectId()));
            for (var request : fixture.requests) assertEquals("Bearer " + TOKEN, request.headers().firstValue("Authorization").orElseThrow());
        }
    }

    @Test void oauth_and_signed_jwt_times_can_differ_and_the_effective_executor_expiry_uses_the_minimum() {
        try (var fixture = new Fixture()) {
            fixture.token.set(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, TOKEN, NOW.minusSeconds(20), NOW.plusSeconds(3), SCOPES));
            fixture.replacePrincipal(principal(RESOURCE, APP, "business", SCOPES, NOW.minusSeconds(40), NOW.plusSeconds(7)));
            var expiry = fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> executor.expiresAt());
            assertEquals(NOW.plusSeconds(3), expiry);
            assertEquals(1, fixture.requests.size());
        }
    }

    @Test void no_match_preserves_the_standalone_observation_without_a_business_receipt_or_cross_clock_order_assumption() {
        try (var fixture = new Fixture()) {
            fixture.decision.set(new DecisionResultDTO(DECISION_ID, "eligibility", "release-1", RUN, NOW.plusSeconds(30), List.of(), List.of()));
            var result = fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> operation.evaluate());
            assertEquals(ReadOnlyDecisionToolTransport.Status.NO_MATCH, result.status()); assertEquals(Map.of(), result.output()); assertNull(result.matchedRuleId());
            assertEquals(NOW.plusSeconds(30), result.submittedAt()); assertEquals(NOW, result.receivedAt());
        }
    }

    @Test void unverified_or_mismatched_service_material_never_reaches_release_or_decision_http() {
        for (var variant : List.of("key-client", "key-audience", "proof-token", "proof-type", "principal-app", "principal-resource", "principal-source", "principal-scopes", "inactive")) {
            try (var fixture = new Fixture()) {
                switch (variant) {
                    case "key-client" -> fixture.key.set(ServiceAuthorizationKey.from(fixture.context, ISSUER.toASCIIString(), "other-client", 1));
                    case "key-audience" -> fixture.key.set(new ServiceAuthorizationKey(ISSUER.toASCIIString(), "tools", CLIENT, 1, APP, URI.create("https://API.example/process/20"), ServiceTokenContext.TENANT, "20", SCOPES));
                    case "proof-token" -> fixture.proof.get().setJwt(jwt("different.service.token", NOW.minusSeconds(40), NOW.plusSeconds(80)));
                    case "proof-type" -> fixture.proof.get().setPrincipalType(PrincipalType.USER).setSecurityVersion(new OAuthSecurityVersionBO(1, 1, 1L));
                    case "principal-app" -> fixture.principal.set(principal(RESOURCE, "wrong-app", "business", SCOPES, NOW.minusSeconds(40), NOW.plusSeconds(80)));
                    case "principal-resource" -> fixture.principal.set(principal(URI.create("https://API.example/process/20"), APP, "business", SCOPES, NOW.minusSeconds(40), NOW.plusSeconds(80)));
                    case "principal-source" -> fixture.principal.set(principal(RESOURCE, APP, "other-business", SCOPES, NOW.minusSeconds(40), NOW.plusSeconds(80)));
                    case "principal-scopes" -> fixture.principal.set(principal(RESOURCE, APP, "business", Set.of(ProcessReleaseClient.READ_SCOPE), NOW.minusSeconds(40), NOW.plusSeconds(80)));
                    case "inactive" -> fixture.trust.set(trust(TrustedIssuerRegistry.Status.INACTIVE, 1));
                    default -> throw new AssertionError("Unexpected identity fixture");
                }
                assertFailure(CREDENTIAL_UNAVAILABLE, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> operation.evaluate()));
                assertTrue(fixture.requests.isEmpty());
            }
        }
    }

    @Test void credential_or_trust_drift_during_token_or_release_work_prevents_callback_dispatch() {
        for (var phase : List.of("token-key", "release-key", "release-trust")) {
            try (var fixture = new Fixture()) {
                var callback = new AtomicBoolean();
                Runnable change = phase.endsWith("trust") ? () -> fixture.trust.set(trust(TrustedIssuerRegistry.Status.ACTIVE, 2))
                        : () -> fixture.key.set(ServiceAuthorizationKey.from(fixture.context, ISSUER.toASCIIString(), CLIENT, 2));
                if (phase.startsWith("token")) fixture.afterToken.set(change); else fixture.afterGet.set(change);
                assertFailure(CREDENTIAL_UNAVAILABLE, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { callback.set(true); return operation.evaluate(); }));
                assertFalse(callback.get()); assertEquals(0, fixture.posts());
            }
        }
    }

    @Test void changed_release_manifest_model_or_input_schema_never_opens_a_decision_operation() {
        for (var variant : List.of("manifest", "model", "key", "inputs")) {
            try (var fixture = new Fixture()) {
                fixture.release.set(new ProcessReleaseDTO("release-1", variant.equals("manifest") ? "d".repeat(64) : MANIFEST, NOW.minusSeconds(10),
                        List.of(new ProcessModelDTO(ProcessModelTypeEnum.DMN, variant.equals("key") ? "other" : "eligibility", variant.equals("model") ? "d".repeat(64) : MODEL,
                                Map.of("amount", variant.equals("inputs") ? ProcessValueTypeEnum.BOOLEAN : ProcessValueTypeEnum.INTEGER)))));
                var callback = new AtomicBoolean();
                assertFailure(RELEASE_CHANGED, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { callback.set(true); return operation.evaluate(); }));
                assertFalse(callback.get()); assertEquals(0, fixture.posts());
            }
        }
        try (var fixture = new Fixture()) {
            fixture.getStatus.set(503);
            assertFailure(RELEASE_UNAVAILABLE, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> operation.evaluate()));
            assertEquals(0, fixture.posts());
        }
    }

    @Test void remote_denial_rejection_and_unknown_statuses_have_fixed_private_failure_categories() {
        for (int status : List.of(401, 403, 400, 409, 422, 408, 429, 500, 503)) {
            try (var fixture = new Fixture()) {
                fixture.postStatus.set(status);
                var expected = status == 401 || status == 403 ? OPERATION_DENIED : status >= 400 && status < 500 && status != 408 && status != 429 ? OPERATION_REJECTED : OPERATION_RESULT_UNKNOWN;
                var failure = assertFailure(expected, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> operation.evaluate()));
                assertEquals(expected.name(), failure.getMessage()); assertNull(failure.getCause()); assertFalse(failure.getMessage().contains(TOKEN)); assertEquals(1, fixture.posts());
            }
        }
        try (var fixture = new Fixture()) {
            fixture.postIoFailure.set(true);
            assertFailure(OPERATION_RESULT_UNKNOWN, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> operation.evaluate()));
        }
    }

    @Test void multiple_or_malformed_rows_are_not_guessed_into_a_single_match() {
        for (var variant : List.of("rows", "rules", "type", "json")) {
            try (var fixture = new Fixture()) {
                if (variant.equals("json")) fixture.badPostJson.set(true);
                else fixture.decision.set(new DecisionResultDTO(DECISION_ID, "eligibility", "release-1", RUN, NOW,
                        variant.equals("rows") ? List.of(Map.of("eligible", true), Map.of("eligible", false)) : List.of(Map.of("eligible", variant.equals("type") ? "true" : true)),
                        variant.equals("rows") || variant.equals("rules") ? List.of("approved", "other") : List.of("approved")));
                assertFailure(OPERATION_INVALID_RESPONSE, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> operation.evaluate()));
                assertEquals(1, fixture.posts());
            }
        }
    }

    @Test void the_operation_cannot_escape_its_dynamic_callback_scope_or_be_used_twice() {
        try (var fixture = new Fixture()) {
            var escaped = new AtomicReference<ReadOnlyDecisionToolTransport.OneShotDecision>();
            assertEquals("prepared", fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { escaped.set(operation); return "prepared"; }));
            assertFailure(CANCELLED, () -> escaped.get().evaluate()); assertEquals(0, fixture.posts());
        }
        try (var fixture = new Fixture()) {
            fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> {
                var result = operation.evaluate(); assertFailure(CANCELLED, operation::evaluate); return result;
            });
            assertEquals(1, fixture.posts());
        }
    }

    @Test void another_thread_or_an_ambient_transaction_cannot_consume_the_capability() {
        try (var fixture = new Fixture()) {
            fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> {
                var failure = new AtomicReference<Throwable>();
                Thread other = Thread.ofVirtual().start(() -> { try { operation.evaluate(); } catch (Throwable rejected) { failure.set(rejected); } });
                try { other.join(1000); } catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
                assertInstanceOf(ReadOnlyDecisionToolTransport.Failure.class, failure.get());
                assertEquals(CANCELLED, ((ReadOnlyDecisionToolTransport.Failure) failure.get()).kind());
                TransactionSynchronizationManager.setActualTransactionActive(true);
                try { assertFailure(CANCELLED, operation::evaluate); } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
                return "no-operation";
            });
            assertEquals(0, fixture.posts());
        }
        try (var fixture = new Fixture()) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertFailure(CREDENTIAL_UNAVAILABLE, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> operation.evaluate())); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            verifyNoInteractions(fixture.oauth, fixture.authentication, fixture.converter); assertTrue(fixture.requests.isEmpty());
        }
    }

    @Test void the_owned_worker_does_not_inherit_user_thread_material() {
        var inherited = new InheritableThreadLocal<String>(); inherited.set("private user material");
        try (var fixture = new Fixture()) {
            assertEquals("clean", fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { assertNull(inherited.get()); return "clean"; }));
        } finally { inherited.remove(); }
    }

    @Test void a_single_monotonic_window_covers_credentials_and_verification_even_if_wall_time_moves_backwards() {
        try (var fixture = new Fixture()) {
            fixture.afterToken.set(() -> { fixture.nano.set(6_000_000_001L); fixture.wall.set(NOW.minusSeconds(60)); });
            assertFailure(DEADLINE_EXCEEDED, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> operation.evaluate()));
            assertEquals(0, fixture.posts()); assertTrue(fixture.requests.isEmpty());
        }
    }

    @Test void valid_late_http_observations_reach_the_callback_before_outer_postchecks_suppress_model_values() throws Exception {
        for (var variant : List.of("deadline", "key", "trust")) {
            try (var fixture = new Fixture()) {
                var observed = new AtomicReference<ReadOnlyDecisionToolTransport.DecisionObservation>(); var recorded = new CountDownLatch(1);
                fixture.afterPost.set(() -> {
                    switch (variant) {
                        case "deadline" -> { fixture.wall.set(NOW.plusSeconds(6)); fixture.nano.set(6_000_000_000L); }
                        case "key" -> fixture.key.set(ServiceAuthorizationKey.from(fixture.context, ISSUER.toASCIIString(), CLIENT, 2));
                        case "trust" -> fixture.trust.set(trust(TrustedIssuerRegistry.Status.ACTIVE, 2));
                        default -> throw new AssertionError("Unexpected late-response fixture");
                    }
                });
                assertFailure(variant.equals("deadline") ? DEADLINE_EXCEEDED : CREDENTIAL_UNAVAILABLE,
                        () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { observed.set(operation.evaluate()); recorded.countDown(); return "model-value"; }));
                assertTrue(recorded.await(1, TimeUnit.SECONDS)); assertEquals(DECISION_ID, observed.get().decisionExecutionId()); assertEquals(1, fixture.posts());
            }
        }
    }

    @Test void callback_failures_never_retain_private_payloads_or_causes_and_known_ledger_codes_are_preserved() {
        try (var fixture = new Fixture()) {
            var failure = assertFailure(OPERATION_REJECTED, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { throw new IllegalStateException("private " + TOKEN); }));
            assertNull(failure.getCause());
        }
        try (var fixture = new Fixture()) {
            var failure = assertThrows(AgentLedgerException.class, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "private " + TOKEN);
            }));
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, failure.code()); assertNull(failure.getCause()); assertFalse(failure.getMessage().contains(TOKEN));
        }
    }

    @Test void interrupting_the_caller_rejects_late_oauth_work_without_creating_http_or_callback_activity() throws Exception {
        try (var fixture = new Fixture()) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var workerFinished = new CountDownLatch(1);
            fixture.oauthWait.set(() -> { entered.countDown(); awaitIgnoringInterrupts(release); workerFinished.countDown(); });
            var result = new AtomicReference<Throwable>(); var interrupted = new AtomicBoolean(); var callback = new AtomicBoolean();
            Thread caller = Thread.ofPlatform().start(() -> {
                try { fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { callback.set(true); return operation.evaluate(); }); }
                catch (Throwable failure) { result.set(failure); interrupted.set(Thread.currentThread().isInterrupted()); }
            });
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS)); caller.interrupt(); caller.join(1000);
                assertFalse(caller.isAlive()); assertEquals(CANCELLED, ((ReadOnlyDecisionToolTransport.Failure) result.get()).kind()); assertTrue(interrupted.get());
            } finally { release.countDown(); caller.join(1000); }
            assertTrue(workerFinished.await(1, TimeUnit.SECONDS)); assertFalse(callback.get()); assertTrue(fixture.requests.isEmpty());
        }
    }

    @Test void close_clears_active_calls_before_interrupting_workers_and_never_closes_caller_owned_clients() throws Exception {
        try (var fixture = new Fixture()) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var finished = new CountDownLatch(1);
            fixture.oauthWait.set(() -> { entered.countDown(); awaitIgnoringInterrupts(release); finished.countDown(); });
            var result = new AtomicReference<Throwable>();
            Thread caller = Thread.ofPlatform().start(() -> {
                try { fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> operation.evaluate()); }
                catch (Throwable failure) { result.set(failure); }
            });
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS)); fixture.transport.close(); caller.join(1000);
                assertFalse(caller.isAlive()); assertEquals(CANCELLED, ((ReadOnlyDecisionToolTransport.Failure) result.get()).kind());
                assertFailure(CANCELLED, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> operation.evaluate()));
            } finally { release.countDown(); caller.join(1000); }
            assertTrue(finished.await(1, TimeUnit.SECONDS)); assertTrue(fixture.requests.isEmpty());
            verify(fixture.http, never()).close(); verify(fixture.oauth, never()).close();
        }
    }

    @Test void concurrency_is_unqueued_and_rejected_work_does_not_start_another_oauth_request() throws Exception {
        try (var fixture = new Fixture()) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            fixture.oauthWait.set(() -> { entered.countDown(); awaitIgnoringInterrupts(release); });
            Thread first = Thread.ofPlatform().start(() -> { try { fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> "done"); } catch (ReadOnlyDecisionToolTransport.Failure expected) { } });
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS));
                assertFailure(CREDENTIAL_UNAVAILABLE, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> operation.evaluate()));
                verify(fixture.oauth).authorizeBounded(any(), anyString(), any());
            } finally { fixture.transport.close(); release.countDown(); first.join(1000); }
        }
    }

    @Test void the_original_timeout_bounds_oauth_without_trusting_interrupt_cooperation() {
        try (var fixture = new Fixture(Duration.ofMillis(100))) {
            var release = new CountDownLatch(1);
            fixture.oauthWait.set(() -> awaitIgnoringInterrupts(release));
            try { assertFailure(DEADLINE_EXCEEDED, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> operation.evaluate())); }
            finally { release.countDown(); }
            assertTrue(fixture.requests.isEmpty());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final IdpServiceOAuth2Client oauth = mock(IdpServiceOAuth2Client.class);
        final TenantIssuerAuthenticationManagerComponent authentication = mock(TenantIssuerAuthenticationManagerComponent.class);
        final VerifiedPrincipalConverter converter = mock(VerifiedPrincipalConverter.class);
        final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
        final HttpClient http = mock(HttpClient.class);
        final JsonMapper mapper = JsonMapper.builder().build();
        final IdpServiceTokenRequest context = new IdpServiceTokenRequest("tools", APP, RESOURCE, ServiceTokenContext.TENANT, "20", SCOPES);
        final AtomicReference<ServiceAuthorizationKey> key = new AtomicReference<>(ServiceAuthorizationKey.from(context, ISSUER.toASCIIString(), CLIENT, 1));
        final AtomicReference<TrustedIssuerRegistry.TrustedIssuerVO> trust = new AtomicReference<>(trust(TrustedIssuerRegistry.Status.ACTIVE, 1));
        final AtomicReference<OAuth2AccessToken> token = new AtomicReference<>(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, TOKEN, NOW.minusSeconds(20), NOW.plusSeconds(70), SCOPES));
        final AtomicReference<ServiceIdentityPrincipal> principal = new AtomicReference<>(principal(RESOURCE, APP, "business", SCOPES, NOW.minusSeconds(40), NOW.plusSeconds(80)));
        final AtomicReference<VerifiedJwtBO> proof = new AtomicReference<>(proof(jwt(TOKEN, NOW.minusSeconds(40), NOW.plusSeconds(80)), trust.get()));
        final AtomicReference<ProcessReleaseDTO> release = new AtomicReference<>(new ProcessReleaseDTO("release-1", MANIFEST, NOW.minusSeconds(10), List.of(new ProcessModelDTO(ProcessModelTypeEnum.DMN, "eligibility", MODEL, Map.of("amount", ProcessValueTypeEnum.INTEGER)))));
        final AtomicReference<DecisionResultDTO> decision = new AtomicReference<>(new DecisionResultDTO(DECISION_ID, "eligibility", "release-1", RUN, NOW.minusSeconds(1), List.of(Map.of("eligible", true)), List.of("approved")));
        final AtomicReference<Instant> wall = new AtomicReference<>(NOW);
        final AtomicLong nano = new AtomicLong(), getStatus = new AtomicLong(200), postStatus = new AtomicLong(200);
        final AtomicBoolean badPostJson = new AtomicBoolean(), postIoFailure = new AtomicBoolean();
        final AtomicReference<Runnable> oauthWait = new AtomicReference<>(() -> { }), afterToken = new AtomicReference<>(() -> { }), afterGet = new AtomicReference<>(() -> { }), afterPost = new AtomicReference<>(() -> { });
        final List<HttpRequest> requests = java.util.Collections.synchronizedList(new ArrayList<>());
        final ToolDescriptor descriptor;
        final TianjiFixedDecisionToolTransport transport;

        Fixture() { this(Duration.ofSeconds(5)); }
        Fixture(Duration timeout) {
            var backend = new TianjiFixedDecisionToolBindings.BackendVO(20, ISSUER, BASE, RESOURCE, "tools", CLIENT, APP, "business", "agent", "test", "release-1", MANIFEST, "eligibility", MODEL, Map.of("amount", ProcessValueTypeEnum.INTEGER), timeout);
            descriptor = new ToolDescriptor("decision.eligibility", 1, "check_eligibility", "Fixed read-only decision",
                    new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")), new ToolSchema(Map.of("eligible", ToolSchema.ValueType.BOOLEAN), Set.of("eligible")),
                    new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "decision.eligibility", DecisionClient.EXECUTE_RESOURCE_KEY, backend.bindingSha256(), null));
            var binding = new TianjiFixedDecisionToolBindings.BindingVO(20, OWNER.issuerFingerprint(), OWNER.subjectType(), "assistant", 1, DEF, descriptor.reference(), backend);
            var bindings = new TianjiFixedDecisionToolBindings(List.of(descriptor), List.of(binding));
            when(http.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
            when(oauth.authorizationKey(context)).thenAnswer(call -> key.get());
            when(oauth.authorizeBounded(eq(context), eq(ISSUER.toASCIIString()), any())).thenAnswer(call -> { oauthWait.get().run(); afterToken.get().run(); return token.get(); });
            when(issuers.findByIssuer(ISSUER.toASCIIString())).thenAnswer(call -> Optional.of(trust.get()));
            when(authentication.authenticate(TOKEN, RESOURCE)).thenAnswer(call -> proof.get());
            when(converter.toServicePrincipal(any(), eq(RESOURCE))).thenAnswer(call -> principal.get());
            when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenAnswer(call -> {
                HttpRequest request = call.getArgument(0); requests.add(request);
                if (request.method().equals("GET")) { afterGet.get().run(); return CompletableFuture.completedFuture(response((int) getStatus.get(), mapper.writeValueAsBytes(release.get()))); }
                afterPost.get().run();
                if (postIoFailure.get()) return CompletableFuture.failedFuture(new java.io.IOException("private " + TOKEN));
                byte[] bytes = badPostJson.get() ? "{private invalid JSON".getBytes(StandardCharsets.UTF_8) : postStatus.get() >= 400 ? ("private " + TOKEN).getBytes(StandardCharsets.UTF_8) : mapper.writeValueAsBytes(decision.get());
                return CompletableFuture.completedFuture(response((int) postStatus.get(), bytes));
            });
            var clock = new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() { return wall.get(); }
            };
            transport = new TianjiFixedDecisionToolTransport(bindings, oauth, authentication, converter, issuers, http, 1, clock, nano::get);
        }

        ReadOnlyDecisionToolTransport.Request request() {
            var run = new AgentRunAuthorization.Request(OWNER, RUN, "assistant", 1, DEF, "d".repeat(64), "e".repeat(64), "policy-1", false, NOW.plusSeconds(60));
            return new ReadOnlyDecisionToolTransport.Request(run, new AgentRunLease(RUN, "worker", 1, NOW.plusSeconds(30)), descriptor, Map.of("amount", 20L), INVOCATION, NOW.plusSeconds(20));
        }
        void replacePrincipal(ServiceIdentityPrincipal value) { principal.set(value); proof.set(proof(jwt(TOKEN, value.issuedAt(), value.expiresAt()), trust.get())); }
        long posts() { return requests.stream().filter(request -> request.method().equals("POST")).count(); }
        @Override public void close() { transport.close(); }
    }

    private static ServiceIdentityPrincipal principal(URI resource, String app, String business, Set<String> scopes, Instant issued, Instant expiry) {
        return new ServiceIdentityPrincipal(CLIENT, "20", CLIENT, "42", resource, 1, scopes, business, "agent", "test", "credential", issued, expiry, app, ServiceTokenContext.TENANT);
    }
    private static TrustedIssuerRegistry.TrustedIssuerVO trust(TrustedIssuerRegistry.Status status, long version) {
        return new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", status, version);
    }
    private static Jwt jwt(String token, Instant issued, Instant expiry) {
        return Jwt.withTokenValue(token).header("alg", "RS256").header("kid", "key-1").issuer(ISSUER.toASCIIString()).subject(CLIENT).issuedAt(issued).expiresAt(expiry).audience(List.of(RESOURCE.toASCIIString()))
                .claim("tenant_id", "20").claim("jti", "42").claim("client_id", CLIENT).claim("scope", String.join(" ", SCOPES)).claim("scope_context", "TENANT")
                .claim("source_biz", "business").claim("source_app", "agent").claim("source_env", "test").claim("app_id", APP).build();
    }
    private static VerifiedJwtBO proof(Jwt jwt, TrustedIssuerRegistry.TrustedIssuerVO trust) {
        return VerifiedJwtBO.builder().jwt(jwt).trustedIssuer(trust).subject(CLIENT).tenantId("20").tokenId("42").keyId("key-1").authorizationId("100").clientRegistrationId("101")
                .principalType(PrincipalType.SERVICE).expiresAt(jwt.getExpiresAt()).securityVersion(new OAuthSecurityVersionBO(1, 1, null)).build();
    }
    private static HttpResponse<byte[]> response(int status, byte[] bytes) {
        @SuppressWarnings("unchecked") var response = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(bytes); when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (key, value) -> true)); return response;
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
    private static ReadOnlyDecisionToolTransport.Failure assertFailure(ReadOnlyDecisionToolTransport.FailureKind kind, org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(ReadOnlyDecisionToolTransport.Failure.class, operation); assertEquals(kind, failure.kind()); assertEquals(kind.name(), failure.getMessage()); assertNull(failure.getCause()); return failure;
    }
    private static void awaitIgnoringInterrupts(CountDownLatch release) {
        boolean done = false;
        while (!done) { try { release.await(); done = true; } catch (InterruptedException ignored) { } }
    }
}
