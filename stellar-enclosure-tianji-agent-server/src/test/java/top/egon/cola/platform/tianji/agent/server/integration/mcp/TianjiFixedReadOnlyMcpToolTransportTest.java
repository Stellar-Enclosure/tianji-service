package top.egon.cola.platform.tianji.agent.server.integration.mcp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.common.trace.TraceContext;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyMcpToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
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
import java.net.Authenticator;
import java.net.CookieManager;
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
import java.util.Arrays;
import java.util.Collections;
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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyMcpToolTransport.FailureKind.*;

/** Actual credential verifier and native MCP SDK over a driven JDK subscriber, without a listener. */
class TianjiFixedReadOnlyMcpToolTransportTest {
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private static final URI RESOURCE = URI.create("https://mcp.example/tenant/20");
    private static final URI ENDPOINT = URI.create("http://catalog-mcp-v3.apps.svc.cluster.local:8080/mcp");
    private static final String CLIENT = "agent-executor", APP = "tianji-agent", TOKEN = "private.service.token", DEF = "c".repeat(64);
    private static final String RUN = "10000000-0000-0000-0000-000000000001", SESSION = "50000000-0000-0000-0000-000000000001";
    private static final UUID INVOCATION = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Set<String> SCOPES = Set.of("catalog:read", "price:read");
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://owner.example/20");
    private static final ToolSchema INPUT = new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount"));
    private static final ToolSchema OUTPUT = new ToolSchema(Map.of("eligible", ToolSchema.ValueType.BOOLEAN), Set.of("eligible"));
    private static final Map<String, Object> TOOL = Map.of("name", "catalog.read", "description", "private tool description", "inputSchema",
            Map.of("type", "object", "properties", Map.of("amount", Map.of("type", "integer")), "required", List.of("amount")), "annotations", Map.of("readOnlyHint", "true"));

    @Test void verifies_actual_service_target_and_defers_every_native_protocol_exchange_until_consumed() throws Exception {
        try (var fixture = new Fixture()) {
            var observation = fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> {
                assertTrue(fixture.requests.isEmpty());
                assertEquals(TrustedAgentActor.SubjectType.SERVICE, executor.actor().subjectType());
                assertEquals(CLIENT, executor.actor().subjectId()); assertNotEquals(OWNER, executor.actor());
                assertEquals(NOW.plusSeconds(5), executor.expiresAt());
                return operation.call(executor.expiresAt());
            });
            assertEquals(ReadOnlyMcpToolTransport.Status.SUCCESS, observation.status()); assertEquals(Map.of("eligible", true), observation.output());
            assertEquals(List.of("initialize", "notifications/initialized", "tools/list", "tools/call", "DELETE"), fixture.methods());
            verify(fixture.oauth).authorizeBounded(eq(fixture.context), eq(ISSUER.toASCIIString()), argThat(value -> !value.isNegative() && value.compareTo(Duration.ofSeconds(5)) <= 0));
            verify(fixture.authentication).authenticate(TOKEN, RESOURCE); verify(fixture.converter).toServicePrincipal(any(), eq(RESOURCE));
            for (var request : fixture.requests) {
                assertEquals(ENDPOINT.toASCIIString(), request.uri().toASCIIString()); assertEquals("Bearer " + TOKEN, request.headers().firstValue("Authorization").orElseThrow());
                assertEquals(HttpClient.Version.HTTP_1_1, request.version().orElseThrow());
                assertTrue(request.headers().firstValue("Cookie").isEmpty()); assertTrue(request.headers().firstValue("Proxy-Authorization").isEmpty());
                assertTrue(request.timeout().orElseThrow().compareTo(Duration.ofSeconds(5)) <= 0);
                assertEquals("2025-11-25", request.headers().firstValue("Mcp-Protocol-Version").orElseThrow());
            }
            var call = fixture.mapper.readTree(body(fixture.requests.get(3))); assertEquals("catalog.read", call.path("params").path("name").asText());
            assertEquals(20L, call.path("params").path("arguments").path("amount").asLong()); assertFalse(call.path("params").has("_meta"));
            assertFalse(body(fixture.requests.getFirst()).contains(TOKEN)); assertFalse(body(fixture.requests.get(3)).contains(TOKEN));
        }
    }

    @Test void callback_can_decline_call_and_cannot_escape_or_reconsume_capability() {
        try (var fixture = new Fixture()) {
            var escaped = new AtomicReference<ReadOnlyMcpToolTransport.OneShotCall>();
            assertEquals("declined", fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { escaped.set(operation); return "declined"; }));
            assertTrue(fixture.requests.isEmpty()); assertFailure(CANCELLED, () -> escaped.get().call(NOW.plusSeconds(1)));
            fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> {
                operation.call(executor.expiresAt()); assertFailure(CANCELLED, () -> operation.call(executor.expiresAt())); return "once";
            });
            assertEquals(1, fixture.methods().stream().filter("tools/call"::equals).count());
        }
    }

    @Test void a_new_catalogue_with_identical_tool_document_cannot_replace_the_pinned_catalogue() {
        String v3 = "3".repeat(64);
        var publication = new TianjiFixedReadOnlyMcpToolBindings.McpPublicationPinVO(
                new VersionIdentityVO("101", "12", "33", "3", DdcSiKindEnum.MCP, "test", "c".repeat(64)),
                "business", "tools", "agent", "catalog-owner", "catalog", "mcp.catalog",
                URI.create("https://mcp.example/mcp"), "catalog-v3-post", "catalog-v3-delete", 3, v3);
        for (String wrong : Arrays.asList(null, "4".repeat(64))) {
            try (var fixture = new Fixture(Duration.ofSeconds(5), publication)) {
                fixture.actualCatalogue.set(wrong);
                assertFailure(PROFILE_CHANGED, fixture::call);
                assertEquals(List.of("initialize"), fixture.methods());
            }
        }
        try (var fixture = new Fixture(Duration.ofSeconds(5), publication)) {
            fixture.actualCatalogue.set(v3);
            assertEquals(ReadOnlyMcpToolTransport.Status.SUCCESS, fixture.call().status());
            assertEquals(List.of("initialize", "notifications/initialized", "tools/list", "tools/call", "DELETE"),
                    fixture.methods());
            assertTrue(fixture.requests.stream().allMatch(request -> ENDPOINT.equals(request.uri())));
        }
    }

    @Test void current_si_denial_stops_the_production_binding_before_token_or_http() {
        var selected = TianjiFixedReadOnlyMcpToolBindingsTest.approvedMcpFixture(3, "catalog-mcp");
        var request = selected.request();
        try (var fixture = new Fixture()) {
            var authority = mock(TianjiFixedReadOnlyMcpToolTransport.PublicationAuthority.class);
            doThrow(new IllegalStateException("revoked publication")).when(authority).requireCurrent(
                    request.run().actor(), request.siMaterial());
            var clock = Clock.fixed(request.deadlineAt().minusSeconds(10), ZoneOffset.UTC);
            try (var transport = new TianjiFixedReadOnlyMcpToolTransport(selected.fixedBindings(), fixture.oauth,
                    fixture.authentication, fixture.converter, fixture.issuers, fixture.http, 1, clock, System::nanoTime, authority)) {
                assertFailure(CREDENTIAL_UNAVAILABLE, () -> transport.withVerifiedCall(request, (executor, operation) -> {
                    throw new AssertionError("Revoked SI publication reached the execution callback");
                }));
            }
            verify(authority).requireCurrent(request.run().actor(), request.siMaterial());
            verifyNoInteractions(fixture.oauth, fixture.authentication, fixture.converter, fixture.issuers);
            assertTrue(fixture.requests.isEmpty());
        }
    }

    @Test void null_nonmicro_past_and_extended_deadlines_consume_once_before_validation_without_protocol_io() {
        for (Instant deadline : Arrays.asList(null, NOW.plusSeconds(1).plusNanos(1), NOW, NOW.minusSeconds(1), NOW.plusSeconds(6))) {
            try (var fixture = new Fixture()) {
                fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> {
                    assertFailure(OPERATION_NOT_SENT, () -> operation.call(deadline));
                    assertFailure(CANCELLED, () -> operation.call(NOW.plusSeconds(1))); return "spent";
                });
                assertTrue(fixture.requests.isEmpty());
            }
        }
    }

    @Test void a_foreign_thread_cannot_consume_the_callback_owners_capability() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> {
                var future = CompletableFuture.runAsync(() -> assertFailure(CANCELLED, () -> operation.call(executor.expiresAt())));
                future.join(); assertTrue(fixture.requests.isEmpty()); return operation.call(executor.expiresAt());
            });
            assertEquals(1, fixture.methods().stream().filter("tools/call"::equals).count());
        }
    }

    @Test void entry_and_callback_foreign_transactions_are_rejected_without_clearing_them() {
        try (var fixture = new Fixture()) {
            var callbackReached = new AtomicBoolean(); TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertFailure(CREDENTIAL_UNAVAILABLE, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { callbackReached.set(true); return "invalid"; })); assertFalse(callbackReached.get()); assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> {
                TransactionSynchronizationManager.setActualTransactionActive(true);
                try { assertFailure(CANCELLED, () -> operation.call(executor.expiresAt())); assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); }
                finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
                return "rejected";
            });
            assertTrue(fixture.requests.isEmpty());
        }
    }

    @Test void callback_expiry_floors_the_actual_credential_and_original_backend_ceiling_to_microseconds() {
        try (var fixture = new Fixture()) {
            Instant expiry = NOW.plusSeconds(2).plusNanos(1_999);
            fixture.token.set(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, TOKEN, NOW.minusSeconds(20), expiry, SCOPES));
            fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> {
                assertEquals(NOW.plusSeconds(2).plusNanos(1_000), executor.expiresAt());
                assertFailure(OPERATION_NOT_SENT, () -> operation.call(expiry)); return "floor";
            });
            assertTrue(fixture.requests.isEmpty());
        }
        try (var fixture = new Fixture(Duration.ofMillis(150))) {
            fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { assertEquals(NOW.plusMillis(150), executor.expiresAt()); return "backend ceiling"; });
        }
    }

    @Test void waiting_before_one_shot_and_wall_rollback_do_not_reset_the_original_monotonic_budget() {
        try (var fixture = new Fixture()) {
            fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> {
                fixture.nano.set(Duration.ofSeconds(4).toNanos()); fixture.wall.set(NOW.minusSeconds(30));
                return operation.call(NOW.plusSeconds(5));
            });
            assertTrue(fixture.requests.stream().allMatch(request -> request.timeout().orElseThrow().compareTo(Duration.ofSeconds(1)) <= 0));
        }
        try (var fixture = new Fixture()) {
            assertFailure(DEADLINE_EXCEEDED, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> {
                fixture.nano.set(Duration.ofSeconds(5).toNanos()); fixture.wall.set(NOW.minusSeconds(30)); return operation.call(executor.expiresAt());
            })); assertTrue(fixture.requests.isEmpty());
        }
    }

    @Test void time_spent_resolving_bindings_is_charged_from_the_original_entry_origin() {
        try (var fixture = new Fixture()) {
            var slowBindings = spy(fixture.bindings);
            doAnswer(call -> { fixture.wall.set(NOW.plusSeconds(4)); fixture.nano.set(Duration.ofSeconds(4).toNanos()); return call.callRealMethod(); }).when(slowBindings).resolve(any());
            try (var transport = new TianjiFixedReadOnlyMcpToolTransport(slowBindings, fixture.oauth, fixture.authentication, fixture.converter, fixture.issuers, fixture.http, 1, fixture.clock, fixture.nano::get)) {
                transport.withVerifiedCall(fixture.request(), (executor, operation) -> { assertEquals(NOW.plusSeconds(5), executor.expiresAt()); return operation.call(executor.expiresAt()); });
            }
            assertTrue(fixture.requests.stream().allMatch(request -> request.timeout().orElseThrow().compareTo(Duration.ofSeconds(1)) <= 0));
        }
    }

    @Test void rotated_authorization_key_and_inactive_trust_are_rechecked_at_protocol_submission() {
        for (boolean key : List.of(true, false)) try (var fixture = new Fixture()) {
            assertFailure(CREDENTIAL_UNAVAILABLE, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> {
                if (key) fixture.key.set(ServiceAuthorizationKey.from(fixture.context, ISSUER.toASCIIString(), CLIENT, 2));
                else fixture.trust.set(trust(TrustedIssuerRegistry.Status.INACTIVE, 2));
                return operation.call(executor.expiresAt());
            })); assertTrue(fixture.requests.isEmpty());
        }
        try (var fixture = new Fixture()) {
            fixture.afterHttp.set(method -> { if (method.equals("notifications/initialized")) fixture.key.set(ServiceAuthorizationKey.from(fixture.context, ISSUER.toASCIIString(), CLIENT, 2)); });
            assertFailure(CREDENTIAL_UNAVAILABLE, () -> fixture.call()); assertFalse(fixture.methods().contains("tools/call"));
        }
        try (var fixture = new Fixture()) {
            fixture.afterHttp.set(method -> { if (method.equals("tools/list")) fixture.trust.set(trust(TrustedIssuerRegistry.Status.INACTIVE, 2)); });
            assertFailure(CREDENTIAL_UNAVAILABLE, fixture::call); assertFalse(fixture.methods().contains("tools/call"));
        }
    }

    @Test void known_observation_reaches_callback_before_expiry_and_cleanup_cannot_erase_it() {
        try (var fixture = new Fixture()) {
            var recorded = new AtomicBoolean();
            fixture.afterHttp.set(method -> { if (method.equals("tools/call")) fixture.wall.set(NOW.plusSeconds(8)); });
            assertFailure(DEADLINE_EXCEEDED, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> {
                assertEquals(ReadOnlyMcpToolTransport.Status.SUCCESS, operation.call(executor.expiresAt()).status()); recorded.set(true); return "fact";
            })); assertTrue(recorded.get()); assertFalse(fixture.methods().contains("DELETE"));
        }
        try (var fixture = new Fixture()) {
            fixture.deleteFailure.set(true); var recorded = new AtomicBoolean();
            assertEquals("fact", fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { operation.call(executor.expiresAt()); recorded.set(true); return "fact"; }));
            assertTrue(recorded.get()); assertEquals(1, fixture.methods().stream().filter("tools/call"::equals).count());
        }
    }

    @Test void actual_http_status_and_body_failures_have_fixed_causeless_classification() {
        for (int status : List.of(401, 403, 500)) try (var fixture = new Fixture()) {
            fixture.callStatus.set(status); assertFailure(status == 500 ? OPERATION_RESULT_UNKNOWN : OPERATION_DENIED, fixture::call);
        }
        try (var fixture = new Fixture()) { fixture.badCall.set(true); assertFailure(OPERATION_INVALID_RESPONSE, fixture::call); }
        try (var fixture = new Fixture()) { fixture.ioMethod.set("initialize"); assertFailure(OPERATION_NOT_SENT, fixture::call); }
        try (var fixture = new Fixture()) { fixture.ioMethod.set("tools/call"); assertFailure(OPERATION_RESULT_UNKNOWN, fixture::call); }
        try (var fixture = new Fixture()) { fixture.toolError.set(true); assertEquals(ReadOnlyMcpToolTransport.Status.TOOL_ERROR, fixture.call().status()); }
    }

    @Test void response_limiter_cancels_upstream_before_aggregation_exceeds_one_megabyte() {
        try (var fixture = new Fixture()) {
            fixture.oversize.set(true); assertFailure(OPERATION_RESULT_UNKNOWN, fixture::call); assertTrue(fixture.upstreamCancelled.get()); assertFalse(fixture.callBodyProduced.get());
            assertTrue(fixture.deliveredBytes.get() <= TianjiFixedReadOnlyMcpToolBindings.MAX_RESPONSE_BYTES + 8192L);
        }
    }

    @Test void cancellation_clears_bearer_before_cancelling_http_and_late_oauth_cannot_republish() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.pendingMethod.set("initialize");
            fixture.beforeFutureCancel.set(() -> assertPrivateScopeCancelled(fixture.transport));
            var result = CompletableFuture.runAsync(() -> assertFailure(CANCELLED, fixture::call));
            assertTrue(fixture.pendingStarted.await(5, TimeUnit.SECONDS)); fixture.transport.close(); result.get(5, TimeUnit.SECONDS);
            assertTrue(fixture.pendingFuture.isCancelled()); assertEquals(List.of("initialize"), fixture.methods());
        }
        try (var fixture = new Fixture()) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var exited = new CountDownLatch(1); var worker = new AtomicReference<Thread>();
            fixture.oauthWait.set(() -> { worker.set(Thread.currentThread()); entered.countDown(); awaitIgnoringInterrupts(release); exited.countDown(); });
            var result = CompletableFuture.runAsync(() -> assertFailure(CANCELLED, fixture::call));
            assertTrue(entered.await(5, TimeUnit.SECONDS)); fixture.transport.close(); result.get(5, TimeUnit.SECONDS); release.countDown();
            assertTrue(exited.await(5, TimeUnit.SECONDS)); worker.get().join(5000); assertFalse(worker.get().isAlive()); assertTrue(fixture.requests.isEmpty());
        }
    }

    @Test void actual_helper_rejects_wrong_service_scope_resource_and_user_proof_before_callback() {
        for (int mismatch = 0; mismatch < 3; mismatch++) try (var fixture = new Fixture()) {
            if (mismatch == 0) fixture.principal.set(new ServiceIdentityPrincipal(CLIENT, "20", CLIENT, "42", RESOURCE, 1, Set.of("catalog:read"), "business", "agent", "test", "credential", NOW.minusSeconds(40), NOW.plusSeconds(80), APP, ServiceTokenContext.TENANT));
            else if (mismatch == 1) fixture.principal.set(new ServiceIdentityPrincipal(CLIENT, "20", CLIENT, "42", URI.create("https://other.example/"), 1, SCOPES, "business", "agent", "test", "credential", NOW.minusSeconds(40), NOW.plusSeconds(80), APP, ServiceTokenContext.TENANT));
            else fixture.proof.set(VerifiedJwtBO.builder().jwt(jwt(NOW.plusSeconds(80))).trustedIssuer(fixture.trust.get()).subject(CLIENT).tenantId("20").tokenId("42").keyId("key-1").authorizationId("100").clientRegistrationId("101").principalType(PrincipalType.USER).expiresAt(NOW.plusSeconds(80)).securityVersion(new OAuthSecurityVersionBO(1, 1, 1L)).build());
            var callbackReached = new AtomicBoolean();
            assertFailure(CREDENTIAL_UNAVAILABLE, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { callbackReached.set(true); return "invalid"; }));
            assertFalse(callbackReached.get()); assertTrue(fixture.requests.isEmpty());
        }
    }

    @Test void borrowed_http_and_identity_clients_are_never_closed_and_affinity_has_no_interactions() {
        var fixture = new Fixture();
        clearInvocations(fixture.oauth, fixture.authentication, fixture.converter, fixture.issuers, fixture.http);
        assertTrue(fixture.transport.usesBindings(fixture.bindings)); assertTrue(fixture.transport.usesOAuth(fixture.oauth)); assertTrue(fixture.transport.usesAuthentication(fixture.authentication));
        assertTrue(fixture.transport.usesConverter(fixture.converter)); assertTrue(fixture.transport.usesIssuerRegistry(fixture.issuers)); assertTrue(fixture.transport.usesHttpClient(fixture.http));
        assertFalse(fixture.transport.usesBindings(null)); assertFalse(fixture.transport.usesOAuth(null)); assertFalse(fixture.transport.usesAuthentication(null));
        assertFalse(fixture.transport.usesConverter(null)); assertFalse(fixture.transport.usesIssuerRegistry(null)); assertFalse(fixture.transport.usesHttpClient(null));
        verifyNoInteractions(fixture.oauth, fixture.authentication, fixture.converter, fixture.issuers, fixture.http);
        fixture.close(); verify(fixture.http, never()).close(); verify(fixture.http, never()).shutdown(); verify(fixture.http, never()).shutdownNow();
        assertFailure(CANCELLED, fixture::call);
    }

    @Test void constructor_rejects_redirect_cookie_authenticator_and_unbounded_concurrency() {
        try (var fixture = new Fixture()) {
            for (int concurrency : List.of(0, 9)) assertThrows(IllegalArgumentException.class, () -> fixture.newTransport(concurrency));
            when(fixture.http.followRedirects()).thenReturn(HttpClient.Redirect.ALWAYS); assertThrows(IllegalArgumentException.class, () -> fixture.newTransport(1));
            when(fixture.http.followRedirects()).thenReturn(HttpClient.Redirect.NEVER); when(fixture.http.cookieHandler()).thenReturn(Optional.of(new CookieManager())); assertThrows(IllegalArgumentException.class, () -> fixture.newTransport(1));
            when(fixture.http.cookieHandler()).thenReturn(Optional.empty()); when(fixture.http.authenticator()).thenReturn(Optional.of(new Authenticator() {})); assertThrows(IllegalArgumentException.class, () -> fixture.newTransport(1));
        }
    }

    @Test void worker_does_not_inherit_user_material_and_only_captured_trace_headers_are_injected() {
        var inherited = new InheritableThreadLocal<String>(); inherited.set("private user material");
        var trace = TraceContext.fromHeaders(name -> switch (name) { case "traceparent" -> "00-11111111111111111111111111111111-2222222222222222-01"; case "tracestate" -> "vendor=value"; case "x-egon-request-id" -> "request-1"; default -> null; }, false);
        try (var fixture = new Fixture(); var ignored = trace.open()) {
            fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { assertNull(inherited.get()); return operation.call(executor.expiresAt()); });
            for (var request : fixture.requests) { assertEquals(trace.traceparent(), request.headers().firstValue("traceparent").orElseThrow()); assertEquals("vendor=value", request.headers().firstValue("tracestate").orElseThrow()); assertEquals("request-1", request.headers().firstValue(TraceContext.REQUEST_ID_HEADER).orElseThrow()); }
            assertEquals(trace.traceparent(), TraceContext.capture().traceparent());
        } finally { inherited.remove(); }
    }

    @Test void private_authorization_header_and_raw_transport_causes_never_enter_native_sdk_logs() {
        Logger logger = (Logger) LoggerFactory.getLogger("io.modelcontextprotocol"); Level previous = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender); logger.setLevel(Level.DEBUG);
        try {
            try (var fixture = new Fixture()) { fixture.call(); }
            try (var fixture = new Fixture()) { fixture.toolError.set(true); assertEquals(ReadOnlyMcpToolTransport.Status.TOOL_ERROR, fixture.call().status()); }
            try (var fixture = new Fixture()) { fixture.ioMethod.set("initialize"); assertFailure(OPERATION_NOT_SENT, fixture::call); }
            try (var fixture = new Fixture()) { fixture.ioMethod.set("tools/call"); assertFailure(OPERATION_RESULT_UNKNOWN, fixture::call); }
            try (var fixture = new Fixture()) { fixture.deleteFailure.set(true); fixture.call(); }
            for (var event : appender.list) { assertFalse(event.getFormattedMessage().contains(TOKEN)); assertFalse(event.getFormattedMessage().contains("private tool description")); assertSafeThrowable(event.getThrowableProxy()); }
        } finally { logger.detachAppender(appender); logger.setLevel(previous); appender.stop(); }
    }

    @Test void callback_ledger_failure_keeps_code_but_discards_private_message_and_cause() {
        try (var fixture = new Fixture()) {
            var rejected = assertThrows(AgentLedgerException.class, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> { throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, TOKEN); }));
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, rejected.code()); assertFalse(rejected.getMessage().contains(TOKEN)); assertNull(rejected.getCause()); assertTrue(fixture.requests.isEmpty());
        }
    }

    @Test void callback_ledger_failure_after_known_response_preserves_code_and_skips_cleanup() {
        try (var fixture = new Fixture()) {
            var rejected = assertThrows(AgentLedgerException.class, () -> fixture.transport.withVerifiedCall(fixture.request(), (executor, operation) -> {
                operation.call(executor.expiresAt()); throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, TOKEN);
            }));
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, rejected.code()); assertNull(rejected.getCause()); assertFalse(rejected.getMessage().contains(TOKEN)); assertFalse(fixture.methods().contains("DELETE"));
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
        final AtomicReference<ServiceIdentityPrincipal> principal = new AtomicReference<>(principal(NOW.plusSeconds(80)));
        final AtomicReference<VerifiedJwtBO> proof = new AtomicReference<>(proof(jwt(NOW.plusSeconds(80)), trust.get()));
        final AtomicReference<Instant> wall = new AtomicReference<>(NOW);
        final AtomicLong nano = new AtomicLong(), callStatus = new AtomicLong(200), deliveredBytes = new AtomicLong();
        final AtomicBoolean badCall = new AtomicBoolean(), toolError = new AtomicBoolean(), deleteFailure = new AtomicBoolean(), oversize = new AtomicBoolean(), upstreamCancelled = new AtomicBoolean(), callBodyProduced = new AtomicBoolean();
        final AtomicReference<String> ioMethod = new AtomicReference<>(""), pendingMethod = new AtomicReference<>("");
        final AtomicReference<String> actualCatalogue = new AtomicReference<>();
        final CountDownLatch pendingStarted = new CountDownLatch(1);
        final AtomicReference<Runnable> beforeFutureCancel = new AtomicReference<>(() -> {});
        final CompletableFuture<HttpResponse<byte[]>> pendingFuture = new CompletableFuture<>() { @Override public boolean cancel(boolean interrupt) { beforeFutureCancel.get().run(); return super.cancel(interrupt); } };
        final AtomicReference<Consumer<String>> afterHttp = new AtomicReference<>(method -> {});
        final AtomicReference<Runnable> oauthWait = new AtomicReference<>(() -> {});
        final List<HttpRequest> requests = Collections.synchronizedList(new ArrayList<>());
        final ToolDescriptor descriptor;
        final TianjiFixedReadOnlyMcpToolBindings bindings;
        final TianjiFixedReadOnlyMcpToolTransport transport;
        final Clock clock = new Clock() { @Override public ZoneId getZone() { return ZoneOffset.UTC; } @Override public Clock withZone(ZoneId zone) { return this; } @Override public Instant instant() { return wall.get(); } };
        Fixture() { this(Duration.ofSeconds(5)); }
        Fixture(Duration timeout) { this(timeout, null); }
        Fixture(Duration timeout, TianjiFixedReadOnlyMcpToolBindings.McpPublicationPinVO publication) {
            var backend = new TianjiFixedReadOnlyMcpToolBindings.BackendVO(20, ISSUER, ENDPOINT, RESOURCE, "tools", CLIENT, APP, "business", "agent", "test", SCOPES, "yuheng", "1.0", "catalog.read", TianjiFixedReadOnlyMcpToolBindings.remoteToolDocumentSha256(TOOL), INPUT, OUTPUT, true, timeout, publication);
            descriptor = new ToolDescriptor("mcp.catalog", 1, "read_catalog", "Fixed read-only MCP", INPUT, OUTPUT, new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "mcp.catalog", ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY, backend.bindingSha256(), null));
            bindings = new TianjiFixedReadOnlyMcpToolBindings(List.of(descriptor), List.of(new TianjiFixedReadOnlyMcpToolBindings.BindingVO(20, OWNER.issuerFingerprint(), OWNER.subjectType(), "assistant", 1, DEF, descriptor.reference(), backend)));
            when(http.followRedirects()).thenReturn(HttpClient.Redirect.NEVER); when(http.cookieHandler()).thenReturn(Optional.empty()); when(http.authenticator()).thenReturn(Optional.empty());
            when(oauth.authorizationKey(context)).thenAnswer(call -> key.get()); when(oauth.authorizeBounded(eq(context), eq(ISSUER.toASCIIString()), any())).thenAnswer(call -> { oauthWait.get().run(); return token.get(); });
            when(issuers.findByIssuer(ISSUER.toASCIIString())).thenAnswer(call -> Optional.of(trust.get())); when(authentication.authenticate(TOKEN, RESOURCE)).thenAnswer(call -> proof.get()); when(converter.toServicePrincipal(any(), eq(RESOURCE))).thenAnswer(call -> principal.get());
            when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenAnswer(call -> exchange(call.getArgument(0), call.getArgument(1)));
            transport = newTransport(1);
        }
        TianjiFixedReadOnlyMcpToolTransport newTransport(int concurrency) { return new TianjiFixedReadOnlyMcpToolTransport(bindings, oauth, authentication, converter, issuers, http, concurrency, clock, nano::get); }
        ReadOnlyMcpToolTransport.Request request() {
            var run = new AgentRunAuthorization.Request(OWNER, RUN, "assistant", 1, DEF, "d".repeat(64), "e".repeat(64), "policy-1", false, NOW.plusSeconds(60));
            return new ReadOnlyMcpToolTransport.Request(run, new AgentRunLease(RUN, "worker", 1, NOW.plusSeconds(30)), descriptor, Map.of("amount", 20L), INVOCATION, NOW.plusSeconds(20));
        }
        ReadOnlyMcpToolTransport.CallObservation call() { return transport.withVerifiedCall(request(), (executor, operation) -> operation.call(executor.expiresAt())); }
        List<String> methods() { return requests.stream().map(request -> { try { return request.method().equals("DELETE") ? "DELETE" : mapper.readTree(body(request)).path("method").asText(); } catch (Exception failure) { throw new AssertionError(failure); } }).toList(); }
        CompletableFuture<HttpResponse<byte[]>> exchange(HttpRequest request, HttpResponse.BodyHandler<byte[]> handler) throws Exception {
            requests.add(request); JsonNode json = request.method().equals("DELETE") ? null : mapper.readTree(body(request)); String method = json == null ? "DELETE" : json.path("method").asText();
            if (pendingMethod.get().equals(method)) { pendingStarted.countDown(); return pendingFuture; }
            if (ioMethod.get().equals(method) || method.equals("DELETE") && deleteFailure.get()) return CompletableFuture.failedFuture(new java.io.IOException(TOKEN));
            int status = 200; byte[] bytes;
            if (method.equals("DELETE") || method.equals("notifications/initialized")) { status = method.equals("DELETE") ? 204 : 202; bytes = new byte[0]; }
            else {
                Object result = switch (method) {
                    case "initialize" -> initializeResult();
                    case "tools/list" -> Map.of("tools", List.of(TOOL));
                    case "tools/call" -> toolError.get() ? Map.of("isError", true, "content", List.of(Map.of("type", "text", "text", TOKEN)), "structuredContent", Map.of("code", 400, "message", TOKEN, "data", TOKEN)) : Map.of("isError", false, "structuredContent", Map.of("eligible", true));
                    default -> throw new AssertionError("Unexpected native MCP method");
                };
                bytes = mapper.writeValueAsBytes(Map.of("jsonrpc", "2.0", "id", mapper.treeToValue(json.get("id"), Object.class), "result", result));
                if (method.equals("tools/call")) { status = (int) callStatus.get(); if (badCall.get()) bytes = ("{private invalid " + TOKEN).getBytes(StandardCharsets.UTF_8); if (oversize.get()) bytes = new byte[TianjiFixedReadOnlyMcpToolBindings.MAX_RESPONSE_BYTES + 16384]; }
            }
            var headers = HttpHeaders.of(Map.of("Content-Type", List.of("application/json"), "Mcp-Session-Id", List.of(SESSION)), (name, value) -> true);
            var info = mock(HttpResponse.ResponseInfo.class); when(info.statusCode()).thenReturn(status); when(info.headers()).thenReturn(headers); when(info.version()).thenReturn(HttpClient.Version.HTTP_1_1);
            var subscriber = handler.apply(info); var cancelled = new AtomicBoolean();
            subscriber.onSubscribe(new Flow.Subscription() { @Override public void request(long count) {} @Override public void cancel() { cancelled.set(true); upstreamCancelled.set(true); } });
            for (int offset = 0; offset < bytes.length && !cancelled.get(); offset += 8192) { int count = Math.min(8192, bytes.length - offset); if (method.equals("tools/call")) deliveredBytes.addAndGet(count); subscriber.onNext(List.of(ByteBuffer.wrap(bytes, offset, count))); }
            if (!cancelled.get()) subscriber.onComplete(); final int code = status;
            return subscriber.getBody().toCompletableFuture().thenApply(received -> { if (method.equals("tools/call")) callBodyProduced.set(true); afterHttp.get().accept(method); return response(code, received, headers); });
        }
        private Map<String, Object> initializeResult() {
            var result = new java.util.LinkedHashMap<String, Object>();
            result.put("protocolVersion", "2025-11-25");
            result.put("serverInfo", Map.of("name", "yuheng", "version", "1.0"));
            result.put("capabilities", Map.of("tools", Map.of()));
            String selected = actualCatalogue.get();
            if (selected != null) result.put("_meta", Map.of("top.egon/selectedMcpCatalogueSha256", selected));
            return result;
        }
        @Override public void close() { transport.close(); }
    }
    private static ServiceIdentityPrincipal principal(Instant expiry) { return new ServiceIdentityPrincipal(CLIENT, "20", CLIENT, "42", RESOURCE, 1, SCOPES, "business", "agent", "test", "credential", NOW.minusSeconds(40), expiry, APP, ServiceTokenContext.TENANT); }
    private static TrustedIssuerRegistry.TrustedIssuerVO trust(TrustedIssuerRegistry.Status status, long version) { return new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", status, version); }
    private static Jwt jwt(Instant expiry) { return Jwt.withTokenValue(TOKEN).header("alg", "RS256").header("kid", "key-1").issuer(ISSUER.toASCIIString()).subject(CLIENT).issuedAt(NOW.minusSeconds(40)).expiresAt(expiry).audience(List.of(RESOURCE.toASCIIString())).claim("tenant_id", "20").claim("jti", "42").claim("client_id", CLIENT).claim("scope", String.join(" ", SCOPES)).claim("scope_context", "TENANT").claim("source_biz", "business").claim("source_app", "agent").claim("source_env", "test").claim("app_id", APP).build(); }
    private static VerifiedJwtBO proof(Jwt jwt, TrustedIssuerRegistry.TrustedIssuerVO trust) { return VerifiedJwtBO.builder().jwt(jwt).trustedIssuer(trust).subject(CLIENT).tenantId("20").tokenId("42").keyId("key-1").authorizationId("100").clientRegistrationId("101").principalType(PrincipalType.SERVICE).expiresAt(jwt.getExpiresAt()).securityVersion(new OAuthSecurityVersionBO(1, 1, null)).build(); }
    private static HttpResponse<byte[]> response(int status, byte[] bytes, HttpHeaders headers) { @SuppressWarnings("unchecked") var response = (HttpResponse<byte[]>) mock(HttpResponse.class); when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(bytes); when(response.headers()).thenReturn(headers); return response; }
    private static String body(HttpRequest request) {
        var bytes = new ByteArrayOutputStream(); request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() { @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); } @Override public void onNext(ByteBuffer buffer) { byte[] part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part); } @Override public void onError(Throwable failure) { throw new AssertionError("Controlled request body failed"); } @Override public void onComplete() {} }); return bytes.toString(StandardCharsets.UTF_8);
    }
    private static void assertSafeThrowable(IThrowableProxy throwable) {
        if (throwable == null) return; assertFalse(String.valueOf(throwable.getMessage()).contains(TOKEN)); assertSafeThrowable(throwable.getCause());
        if (throwable.getSuppressed() != null) for (var suppressed : throwable.getSuppressed()) assertSafeThrowable(suppressed);
    }
    private static void assertPrivateScopeCancelled(TianjiFixedReadOnlyMcpToolTransport transport) {
        try {
            var cancellations = transport.getClass().getDeclaredField("cancellations"); cancellations.setAccessible(true);
            for (Object cancellation : (Set<?>) cancellations.get(transport)) for (var captured : cancellation.getClass().getDeclaredFields()) {
                captured.setAccessible(true); Object scope = captured.get(cancellation);
                if (scope == null || !scope.getClass().getSimpleName().equals("CallScope")) continue;
                var bearer = scope.getClass().getDeclaredField("bearer"); bearer.setAccessible(true); Object value = bearer.get(scope);
                assertNull(value instanceof AtomicReference<?> reference ? reference.get() : value);
                var active = scope.getClass().getDeclaredField("active"); active.setAccessible(true); Object state = active.get(scope);
                assertFalse(state instanceof AtomicBoolean flag ? flag.get() : (boolean) state); return;
            }
            fail("active private scope must be cancelled before HTTP future cancellation");
        } catch (ReflectiveOperationException failure) { throw new AssertionError("private scope cancellation proof failed", failure); }
    }
    private static void awaitIgnoringInterrupts(CountDownLatch release) { boolean done = false; while (!done) { try { release.await(); done = true; } catch (InterruptedException ignored) {} } }
    private static ReadOnlyMcpToolTransport.Failure assertFailure(ReadOnlyMcpToolTransport.FailureKind kind, org.junit.jupiter.api.function.Executable operation) { var failure = assertThrows(ReadOnlyMcpToolTransport.Failure.class, operation); assertEquals(kind, failure.kind()); assertEquals(kind.name(), failure.getMessage()); assertNull(failure.getCause()); assertEquals(0, failure.getSuppressed().length); return failure; }
}
