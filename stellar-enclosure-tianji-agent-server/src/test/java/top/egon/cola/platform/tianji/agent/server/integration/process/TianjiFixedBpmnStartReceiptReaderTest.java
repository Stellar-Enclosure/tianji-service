package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartReceiptRead.Request;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartReceiptRead.TargetVO;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiProcessToolCredentialVerifier.CredentialVO;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessStartReceiptQuery;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TianjiFixedBpmnStartReceiptReaderTest {
    private static final Instant NOW = Instant.parse("2026-11-06T01:02:05Z");
    private static final Instant RECORDED = NOW.minusSeconds(30).plusNanos(1000);
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private static final URI BASE = URI.create("https://process.example/base");
    private static final URI RESOURCE = URI.create("https://api.example/process/20");
    private static final String CLIENT = "agent-executor";
    private static final String APP = "tianji-agent";
    private static final String TOKEN = "fixture.private.token";
    private static final String DEFINITION_SHA = "c".repeat(64);
    private static final String INSTANCE_ID = "instance-1";
    private static final String POSITIVE = """
            {"instanceId":"instance-1","status":"APPLIED","recordedAt":"%s"}
            """.formatted(RECORDED);
    private static final TrustedAgentActor OWNER =
            TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://owner.example/20");
    private static final TrustedAgentActor EXECUTOR =
            TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, CLIENT, ISSUER.toASCIIString());

    @Test
    void uses_the_existing_sdk_for_one_exact_get_with_a_separate_read_credential() {
        try (var fixture = new Fixture()) {
            var request = fixture.request();
            var target = fixture.reader.bind(request);
            var action = request.action();
            var command = new StartProcessCommand("case_flow", "release-1", action.businessKey(),
                    action.intentId(), action.intentId(), Map.of("amount", 20L));
            var query = new GetProcessStartReceiptQuery(action.intentId(), OrchestrationRequestDigests.start(command));
            assertEquals(query, target.query());
            assertNotEquals(action.requestSha256(), query.requestSha256());
            verifyNoInteractions(fixture.verifier);

            var observed = fixture.reader.read(request, target, NOW.plusSeconds(20)).orElseThrow();
            assertEquals(target, observed.target());
            assertEquals(EXECUTOR, observed.executor());
            assertEquals(new ProcessStartReceiptDTO(INSTANCE_ID, ProcessStartReceiptDTO.StatusEnum.APPLIED, RECORDED),
                    observed.receipt());
            assertEquals(NOW.plusSeconds(2), observed.expiresAt());
            var ack = new ProcessInstanceDTO(INSTANCE_ID, command.definitionKey(), command.releaseVersion(), command.businessKey(),
                    command.correlationId(), ProcessInstanceStatusEnum.ACTIVE, NOW.minusSeconds(25), null);
            assertEquals(TianjiFixedBpmnStartToolTransport.hostReceiptReference(fixture.backend, EXECUTOR, command, ack),
                    observed.hostReceiptReference());

            var readTarget = new TianjiProcessToolCredentialVerifier.TargetVO(20, ISSUER, RESOURCE, "tools", CLIENT, APP,
                    "business", "agent", "test", Set.of(ProcessStartReceiptClient.READ_SCOPE));
            verify(fixture.verifier).credentials(eq(readTarget), eq(NOW.plusSeconds(5)), any(), any(), any());
            verify(fixture.verifier, times(2)).requireCurrent(eq(readTarget), any());
            var http = fixture.assertSingleGet();
            assertEquals(URI.create(BASE.toASCIIString() + ProcessStartReceiptClient.START_RECEIPT_PATH
                    + "?commandId=" + action.intentId() + "&requestSha256=" + query.requestSha256()), http.uri());
            assertEquals("Bearer " + TOKEN, http.headers().firstValue("Authorization").orElseThrow());
            assertEquals("no-store", http.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(0L, http.bodyPublisher().orElseThrow().contentLength());
            assertEquals(Duration.ofSeconds(2), http.timeout().orElseThrow());
        }
    }

    @Test
    void refuses_the_wrong_target_persisted_actor_and_changed_definition_before_credentials() {
        try (var fixture = new Fixture()) {
            var request = fixture.request();
            var expected = fixture.reader.bind(request);
            var wrongDigest = new TargetVO(expected.namespaceSha256(),
                    new GetProcessStartReceiptQuery(request.action().intentId(), request.action().requestSha256()));
            var wrongNamespace = new TargetVO("f".repeat(64), expected.query());
            assertUnavailable(() -> fixture.reader.read(request, wrongDigest, NOW.plusSeconds(4)));
            assertUnavailable(() -> fixture.reader.read(request, wrongNamespace, NOW.plusSeconds(4)));
            var otherIssuer = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, CLIENT, "https://other.example/20");
            var wrongExecutor = new Request(request.run(), request.descriptor(), request.action(), otherIssuer);
            assertUnavailable(() -> fixture.reader.bind(wrongExecutor));
            var changedRun = new AgentRunAuthorization.Request(OWNER, "run-one", "assistant", 1, "f".repeat(64),
                    "d".repeat(64), "e".repeat(64), "policy-1", false, NOW.minusSeconds(1));
            assertUnavailable(() -> fixture.reader.bind(new Request(changedRun, request.descriptor(), request.action(), EXECUTOR)));
            verifyNoInteractions(fixture.verifier);
            assertEquals(0, fixture.sent.get());
        }
    }

    @Test
    void refuses_a_different_verified_service_before_get() {
        try (var fixture = new Fixture()) {
            fixture.credentialActor.set(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, CLIENT,
                    "https://other.example/20"));
            var request = fixture.request();
            var target = fixture.reader.bind(request);
            assertUnavailable(() -> fixture.reader.read(request, target, NOW.plusSeconds(4)));
            assertEquals(0, fixture.sent.get());
        }
    }

    @Test
    void returns_empty_only_for_an_exact_inconclusive_404() {
        try (var fixture = new Fixture()) {
            fixture.status = 404;
            fixture.body = "{\"code\":\"TIANJI_RESOURCE_NOT_FOUND\",\"correlationId\":null}";
            var request = fixture.request();
            assertTrue(fixture.reader.read(request, fixture.reader.bind(request), NOW.plusSeconds(4)).isEmpty());
            fixture.assertSingleGet();
        }
        for (int status : List.of(401, 403, 404, 500)) {
            try (var fixture = new Fixture()) {
                fixture.status = status;
                fixture.body = TOKEN;
                var request = fixture.request();
                assertUnavailable(() -> fixture.reader.read(request, fixture.reader.bind(request), NOW.plusSeconds(4)));
                fixture.assertSingleGet();
            }
        }
    }

    @Test
    void rejects_ambient_transaction_and_expired_allowance_before_credentials() {
        try (var fixture = new Fixture()) {
            var request = fixture.request();
            var target = fixture.reader.bind(request);
            assertUnavailable(() -> fixture.reader.read(request, target, NOW));
            TransactionSynchronizationManager.initSynchronization();
            try {
                assertUnavailable(() -> fixture.reader.read(request, target, NOW.plusSeconds(1)));
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            verifyNoInteractions(fixture.verifier);
            assertEquals(0, fixture.sent.get());
        }
    }

    @Test
    void wall_clock_rollback_does_not_extend_the_monotonic_window() {
        try (var fixture = new Fixture()) {
            fixture.beforeBearer = () -> {
                fixture.wall.set(NOW.minusSeconds(60));
                fixture.nano.set(Duration.ofSeconds(3).toNanos());
            };
            var request = fixture.request();
            assertUnavailable(() -> fixture.reader.read(request, fixture.reader.bind(request), NOW.plusSeconds(2)));
            assertEquals(0, fixture.sent.get());
        }
    }

    @Test
    void a_successful_http_reply_does_not_survive_current_credential_drift() {
        try (var fixture = new Fixture()) {
            var readTarget = new TianjiProcessToolCredentialVerifier.TargetVO(20, ISSUER, RESOURCE, "tools", CLIENT, APP,
                    "business", "agent", "test", Set.of(ProcessStartReceiptClient.READ_SCOPE));
            doNothing().doThrow(new IllegalStateException(TOKEN))
                    .when(fixture.verifier).requireCurrent(eq(readTarget), any());
            var request = fixture.request();
            assertUnavailable(() -> fixture.reader.read(request, fixture.reader.bind(request), NOW.plusSeconds(4)));
            fixture.assertSingleGet();
        }
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable call) {
        var rejected = assertThrows(IllegalStateException.class, call);
        assertEquals("Fixed BPMN START receipt is unavailable", rejected.getMessage());
        assertNull(rejected.getCause());
        assertFalse(rejected.toString().contains(TOKEN));
    }

    private static final class Fixture implements AutoCloseable {
        private final TianjiProcessToolCredentialVerifier verifier = mock(TianjiProcessToolCredentialVerifier.class);
        private final HttpClient http = mock(HttpClient.class);
        private final AtomicReference<TrustedAgentActor> credentialActor = new AtomicReference<>(EXECUTOR);
        private final AtomicReference<Instant> wall = new AtomicReference<>(NOW);
        private final AtomicLong nano = new AtomicLong();
        private final AtomicInteger sent = new AtomicInteger();
        private final AtomicReference<HttpRequest> lastHttp = new AtomicReference<>();
        private final TianjiFixedBpmnStartToolBindings.BackendVO backend;
        private final ToolDescriptor descriptor;
        private final TianjiFixedBpmnStartReceiptReader reader;
        private volatile int status = 200;
        private volatile String body = POSITIVE;
        private volatile Runnable beforeBearer = () -> { };

        private Fixture() {
            backend = new TianjiFixedBpmnStartToolBindings.BackendVO(20, ISSUER, BASE, RESOURCE, "tools", CLIENT, APP,
                    "business", "agent", "test", "release-1", "a".repeat(64), "case_flow", "b".repeat(64),
                    Map.of("amount", ProcessValueTypeEnum.INTEGER), Duration.ofSeconds(5));
            descriptor = new ToolDescriptor("process.case.start", 1, "start_case", "Fixed BPMN start",
                    new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
                    new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
                    new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start", ProcessClient.START_RESOURCE_KEY,
                            backend.bindingSha256(), null));
            var binding = new TianjiFixedBpmnStartToolBindings.BindingVO(20, OWNER.issuerFingerprint(), OWNER.subjectType(),
                    "assistant", 1, DEFINITION_SHA, descriptor.reference(), backend);
            var bindings = new TianjiFixedBpmnStartToolBindings(List.of(descriptor), List.of(binding));
            when(verifier.credentials(any(), any(), any(), any(), any())).thenAnswer(call -> {
                call.<LongSupplier>getArgument(2).getAsLong();
                beforeBearer.run();
                call.<Consumer<String>>getArgument(3).accept(TOKEN);
                assertEquals(TOKEN, call.<Supplier<String>>getArgument(4).get());
                return new CredentialVO(null, null, null, credentialActor.get(), NOW.plusSeconds(2));
            });
            when(http.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
            when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                    .thenAnswer(call -> send(call.getArgument(0), call.getArgument(1)));
            var clock = new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() { return wall.get(); }
            };
            reader = new TianjiFixedBpmnStartReceiptReader(bindings, verifier, http, 1, clock, nano::get);
        }

        private Request request() {
            var run = new AgentRunAuthorization.Request(OWNER, "run-one", "assistant", 1, DEFINITION_SHA,
                    "d".repeat(64), "e".repeat(64), "policy-1", false, NOW.minusSeconds(1));
            var action = new ToolActionRequest(OWNER, descriptor, "case-one", "object-v7", Map.of("amount", 20L));
            return new Request(run, descriptor, action, EXECUTOR);
        }

        private CompletableFuture<HttpResponse<byte[]>> send(HttpRequest request, HttpResponse.BodyHandler<byte[]> handler) {
            sent.incrementAndGet();
            lastHttp.set(request);
            var headers = HttpHeaders.of(Map.of("Content-Type", List.of("application/json"),
                    "Cache-Control", List.of("no-store")), (key, value) -> true);
            var info = mock(HttpResponse.ResponseInfo.class);
            when(info.statusCode()).thenReturn(status);
            when(info.headers()).thenReturn(headers);
            var subscriber = handler.apply(info);
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override public void request(long n) { }
                @Override public void cancel() { }
            });
            subscriber.onNext(List.of(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8))));
            subscriber.onComplete();
            return subscriber.getBody().toCompletableFuture().thenApply(bytes -> {
                @SuppressWarnings("unchecked") var response = (HttpResponse<byte[]>) mock(HttpResponse.class);
                when(response.statusCode()).thenReturn(status);
                when(response.headers()).thenReturn(headers);
                when(response.body()).thenReturn(bytes);
                return response;
            });
        }

        private HttpRequest assertSingleGet() {
            assertEquals(1, sent.get());
            var request = lastHttp.get();
            assertEquals("GET", request.method());
            return request;
        }

        @Override public void close() { reader.close(); }
    }
}
