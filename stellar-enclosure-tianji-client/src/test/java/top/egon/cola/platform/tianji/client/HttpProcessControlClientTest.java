package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessResult;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlBlockReasonEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlSnapshotDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpProcessControlClientTest {
    private static final String TOKEN = "process-control-token";
    private static final String VERSION_A = "a".repeat(64);
    private static final String VERSION_B = "b".repeat(64);
    private static final Instant SUBMITTED = Instant.parse("2026-11-05T00:00:00.123456789Z");
    private static final Instant FINISHED = Instant.parse("2026-11-05T00:06:00.123456789Z");
    private static final Instant DECIDED = Instant.parse("2026-11-05T00:05:59.999999Z");
    private static final Map<Integer, Set<String>> ERROR_CODES = Map.of(
            400, Set.of("TIANJI_INVALID_COMMAND"),
            401, Set.of("TIANJI_UNAUTHENTICATED"),
            403, Set.of("TIANJI_PERMISSION_DENIED"),
            404, Set.of("TIANJI_RESOURCE_NOT_FOUND"),
            409, Set.of("TIANJI_IDEMPOTENCY_CONFLICT", "TIANJI_PROCESS_VERSION_CONFLICT",
                    "TIANJI_PROCESS_CANCEL_BLOCKED", "TIANJI_TENANT_CONTEXT_CHANGED"),
            413, Set.of("TIANJI_REQUEST_TOO_LARGE"),
            500, Set.of("TIANJI_INTERNAL_ERROR"),
            503, Set.of("TIANJI_DEPENDENCY_UNAVAILABLE"));
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ProcessControlResponseCodec codec = new ProcessControlResponseCodec();

    @Test
    void control_uses_exact_encoded_get_prefix_headers_and_one_send() {
        String instanceId = "process /东";
        var expected = activeSnapshot(instanceId);
        var transport = new FakeHttpClient();
        transport.reply(200, "application/json; charset=utf-8", json(expected));
        ProcessControlClient api = client(URI.create("http://process.example/prefix/"), transport,
                () -> TOKEN, Duration.ofSeconds(1));

        ProcessControlSnapshotDTO actual = api.control(new GetProcessInstanceQuery(instanceId));
        assertAll(
                () -> assertNotNull(actual),
                () -> assertEquals(expected, actual),
                () -> assertEquals(1, transport.requests.size())
        );
        HttpRequest request = transport.requests.getFirst();
        String encoded = URLEncoder.encode(instanceId, StandardCharsets.UTF_8).replace("+", "%20");
        assertEquals("/prefix" + ProcessControlClient.CONTROL_PATH.replace("{instanceId}", encoded),
                request.uri().getRawPath());
        assertEquals("GET", request.method());
        assertEquals("Bearer " + TOKEN, request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Accept").orElseThrow());
        assertEquals("no-store", request.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(request.headers().firstValue("Content-Type").isEmpty());
        assertArrayEquals(new byte[0], body(request));
        assertNull(request.uri().getRawQuery());
    }

    @Test
    void cancel_uses_exact_post_json_affinity_and_one_send() {
        var command = command("process-1", "cancel-command-1", "撤销 private audit 😀");
        var expected = cancelled("process-1", "cancel-command-1", FINISHED, DECIDED);
        var transport = new FakeHttpClient();
        transport.reply(200, "application/vnd.tianji+json", json(expected));
        ProcessControlClient api = client(URI.create("https://process.example/prefix/"), transport,
                () -> TOKEN, Duration.ofSeconds(1));

        CancelProcessResult actual = api.cancel(command);
        assertAll(
                () -> assertNotNull(actual),
                () -> assertEquals(expected, actual),
                () -> assertEquals(1, transport.requests.size())
        );
        HttpRequest request = transport.requests.getFirst();
        assertEquals("/prefix" + ProcessControlClient.CANCEL_PATH.replace("{instanceId}", "process-1"),
                request.uri().getRawPath());
        assertEquals("POST", request.method());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Accept").orElseThrow());
        assertEquals("no-store", request.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(mapper.valueToTree(command), mapper.readTree(body(request)));
    }

    @Test
    void process_control_preserves_the_general_constructor_http_and_redirect_policy() {
        var transport = new FakeHttpClient();
        transport.reply(200, "application/json", json(activeSnapshot("process-1")));
        assertDoesNotThrow(() -> client(URI.create("http://process.example/prefix/"), transport,
                () -> TOKEN, Duration.ofSeconds(1)).control(new GetProcessInstanceQuery("process-1")));
        assertEquals(1, transport.requests.size());

        var redirecting = new FakeHttpClient(HttpClient.Redirect.ALWAYS);
        assertThrows(IllegalArgumentException.class, () -> client(URI.create("https://process.example/"), redirecting,
                () -> TOKEN, Duration.ofSeconds(1)));
        assertTrue(redirecting.requests.isEmpty());
    }

    @Test
    void invalid_local_inputs_fail_before_credentials_or_transport() {
        var transport = new FakeHttpClient();
        var tokenCalls = new AtomicInteger();
        ProcessControlClient api = client(URI.create("https://process.example/"), transport,
                () -> { tokenCalls.incrementAndGet(); return TOKEN; }, Duration.ofSeconds(1));

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> api.control(null)),
                () -> assertThrows(NullPointerException.class, () -> api.cancel(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> api.control(new GetProcessInstanceQuery("."))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> api.control(new GetProcessInstanceQuery("..")))
        );
        assertEquals(0, tokenCalls.get());
        assertTrue(transport.requests.isEmpty());
    }

    @Test
    void response_identity_mismatch_is_rejected_after_exactly_one_send() {
        var controlTransport = new FakeHttpClient();
        controlTransport.reply(200, "application/json", json(activeSnapshot("different-process")));
        ProcessClientException controlFailure = assertThrows(ProcessClientException.class,
                () -> client(URI.create("https://process.example/"), controlTransport, () -> TOKEN, Duration.ofSeconds(1))
                        .control(new GetProcessInstanceQuery("process-1")));
        assertEquals("TIANJI_INVALID_RESPONSE", controlFailure.code());
        assertEquals(1, controlTransport.requests.size());

        for (CancelProcessResult mismatch : List.of(
                cancelled("different-process", "cancel-command-1", FINISHED, DECIDED),
                cancelled("process-1", "different-command", FINISHED, DECIDED))) {
            var cancelTransport = new FakeHttpClient();
            cancelTransport.reply(200, "application/json", json(mismatch));
            ProcessClientException cancelFailure = assertThrows(ProcessClientException.class,
                    () -> client(URI.create("https://process.example/"), cancelTransport, () -> TOKEN, Duration.ofSeconds(1))
                            .cancel(command("process-1", "cancel-command-1", "reason")));
            assertEquals("TIANJI_INVALID_RESPONSE", cancelFailure.code());
            assertEquals(1, cancelTransport.requests.size());
        }
    }

    @Test
    void success_requires_exact_200_and_json_or_structured_json_media() {
        var notExact = new FakeHttpClient();
        notExact.reply(201, "application/json", json(activeSnapshot("process-1")));
        assertInvalidResponse(() -> client(URI.create("https://process.example/"), notExact,
                () -> TOKEN, Duration.ofSeconds(1)).control(new GetProcessInstanceQuery("process-1")));
        assertEquals(1, notExact.requests.size());

        var wrongMedia = new FakeHttpClient();
        wrongMedia.reply(200, "text/plain", json(activeSnapshot("process-1")));
        assertInvalidResponse(() -> client(URI.create("https://process.example/"), wrongMedia,
                () -> TOKEN, Duration.ofSeconds(1)).control(new GetProcessInstanceQuery("process-1")));
        assertEquals(1, wrongMedia.requests.size());

        var structured = new FakeHttpClient();
        structured.reply(200, "application/vnd.tianji+json", json(activeSnapshot("process-1")));
        assertEquals(activeSnapshot("process-1"), client(URI.create("https://process.example/"), structured,
                () -> TOKEN, Duration.ofSeconds(1)).control(new GetProcessInstanceQuery("process-1")));
        assertEquals(1, structured.requests.size());
    }

    @Test
    void process_control_errors_are_allowlisted_redacted_and_never_retried() {
        for (String code : List.of("TIANJI_PROCESS_VERSION_CONFLICT", "TIANJI_PROCESS_CANCEL_BLOCKED")) {
            var transport = new FakeHttpClient();
            transport.reply(409, "application/json",
                    bytes("{\"code\":\"" + code + "\",\"correlationId\":\"private-" + TOKEN + "\"}"));
            String reason = "private audit reason";
            ProcessClientException failure = assertThrows(ProcessClientException.class,
                    () -> client(URI.create("https://process.example/"), transport, () -> TOKEN, Duration.ofSeconds(1))
                            .cancel(command("process-1", "cancel-command-1", reason)));
            assertEquals(409, failure.httpStatus());
            assertEquals(code, failure.code());
            assertNull(failure.correlationId());
            assertNull(failure.getCause());
            assertFalse(failure.toString().contains(TOKEN));
            assertFalse(failure.toString().contains(reason));
            assertFalse(failure.toString().contains("private"));
            assertEquals(1, transport.requests.size());
        }

        var unknown = new FakeHttpClient();
        unknown.reply(409, "application/json", bytes("{\"code\":\"PRIVATE_UNKNOWN\",\"correlationId\":\"private\"}"));
        ProcessClientException fallback = assertThrows(ProcessClientException.class,
                () -> client(URI.create("https://process.example/"), unknown, () -> TOKEN, Duration.ofSeconds(1))
                        .cancel(command("process-1", "cancel-command-1", "reason")));
        assertEquals("TIANJI_IDEMPOTENCY_CONFLICT", fallback.code());
        assertNull(fallback.correlationId());
        assertEquals(1, unknown.requests.size());
    }

    @Test
    void control_does_not_surface_cancellation_only_or_undocumented_errors() {
        for (String code : List.of("TIANJI_IDEMPOTENCY_CONFLICT", "TIANJI_PROCESS_VERSION_CONFLICT", "TIANJI_PROCESS_CANCEL_BLOCKED")) {
            var transport = new FakeHttpClient();
            transport.reply(409, "application/json", bytes("{\"code\":\"" + code + "\"}"));
            var failure = assertThrows(ProcessClientException.class,
                    () -> client(URI.create("https://process.example/"), transport, () -> TOKEN, Duration.ofSeconds(1))
                            .control(new GetProcessInstanceQuery("process-1")));
            assertEquals("TIANJI_TENANT_CONTEXT_CHANGED", failure.code());
            assertEquals(1, transport.requests.size());
        }
        for (int status : List.of(413, 429)) {
            var transport = new FakeHttpClient();
            transport.reply(status, "application/json", bytes("{\"code\":\""
                    + (status == 413 ? "TIANJI_REQUEST_TOO_LARGE" : "TIANJI_CAPACITY_EXCEEDED") + "\"}"));
            var failure = assertThrows(ProcessClientException.class,
                    () -> client(URI.create("https://process.example/"), transport, () -> TOKEN, Duration.ofSeconds(1))
                            .control(new GetProcessInstanceQuery("process-1")));
            assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", failure.code());
            assertEquals(1, transport.requests.size());
        }
    }

    @Test
    void cancellation_does_not_surface_undocumented_capacity_errors() {
        var transport = new FakeHttpClient();
        transport.reply(429, "application/json", bytes("{\"code\":\"TIANJI_CAPACITY_EXCEEDED\"}"));
        var failure = assertThrows(ProcessClientException.class,
                () -> client(URI.create("https://process.example/"), transport, () -> TOKEN, Duration.ofSeconds(1))
                        .cancel(command("process-1", "command-1", "reason")));
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", failure.code());
        assertEquals(1, transport.requests.size());
    }

    @Test
    void timeout_retry_after_and_dispatch_failures_remain_bounded_and_masked() {
        var timeoutTransport = new FakeHttpClient();
        timeoutTransport.incomplete = true;
        ProcessClientException timeout;
        try {
            timeout = assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> assertThrows(ProcessClientException.class,
                            () -> client(URI.create("https://process.example/"), timeoutTransport, () -> TOKEN, Duration.ofNanos(1))
                                    .control(new GetProcessInstanceQuery("process-1"))));
        } finally {
            if (timeoutTransport.incompleteFuture != null && !timeoutTransport.incompleteFuture.isDone()) {
                timeoutTransport.incompleteFuture.cancel(true);
            }
        }
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", timeout.code());
        assertEquals(1, timeoutTransport.requests.size());
        assertTrue(timeoutTransport.incompleteFuture.isCancelled());

        var retryTransport = new FakeHttpClient();
        retryTransport.reply(503, Map.of(
                        "Content-Type", List.of("application/json"),
                        "Retry-After", List.of("172800")),
                bytes("{\"code\":\"TIANJI_DEPENDENCY_UNAVAILABLE\"}"));
        ProcessClientException retry = assertThrows(ProcessClientException.class,
                () -> client(URI.create("https://process.example/"), retryTransport, () -> TOKEN, Duration.ofSeconds(1))
                        .control(new GetProcessInstanceQuery("process-1")));
        assertEquals(Duration.ofDays(1), retry.retryAfter());
        assertEquals(1, retryTransport.requests.size());

        var dispatchTransport = new FakeHttpClient();
        dispatchTransport.syncFailure = new IllegalStateException("private dispatch detail");
        ProcessClientException masked = assertThrows(ProcessClientException.class,
                () -> client(URI.create("https://process.example/"), dispatchTransport, () -> TOKEN, Duration.ofSeconds(1))
                        .cancel(command("process-1", "cancel-command-1", "private reason")));
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", masked.code());
        assertNull(masked.getCause());
        assertFalse(masked.toString().contains("private"));
        assertEquals(1, dispatchTransport.requests.size());
    }

    @Test
    void process_control_response_body_is_capped_at_two_mebibytes_and_cancels_subscription() {
        assertEquals(2 * 1_048_576, ProcessControlResponseCodec.MAX_BYTES);
        var transport = new FakeHttpClient();
        transport.reply(200, "application/json", new byte[ProcessControlResponseCodec.MAX_BYTES + 1]);
        ProcessClientException failure = assertThrows(ProcessClientException.class,
                () -> client(URI.create("https://process.example/"), transport, () -> TOKEN, Duration.ofSeconds(1))
                        .control(new GetProcessInstanceQuery("process-1")));
        assertEquals("TIANJI_RESPONSE_TOO_LARGE", failure.code());
        assertEquals(1, transport.requests.size());
        assertNotNull(transport.lastSubscription);
        assertTrue(transport.lastSubscription.cancelled);
    }

    @Test
    void strict_codec_decodes_snapshots_results_and_both_independent_clock_orders() {
        var snapshot = activeSnapshot("process-1");
        assertEquals(snapshot, codec.snapshot(json(snapshot)));

        var nativeAfterDecision = cancelled("process-1", "command-1", FINISHED, DECIDED);
        var nativeBeforeDecision = cancelled("process-1", "command-2", DECIDED, FINISHED);
        assertEquals(nativeAfterDecision, codec.cancellation(json(nativeAfterDecision)));
        assertEquals(nativeBeforeDecision, codec.cancellation(json(nativeBeforeDecision)));
        assertEquals(123_456_789, codec.cancellation(json(nativeAfterDecision)).finishedAt().getNano());
    }

    @Test
    void strict_snapshot_codec_rejects_shape_coercion_limits_utf8_and_state_drift() {
        ObjectNode valid = mapper.valueToTree(activeSnapshot("process-1"));
        List<byte[]> invalid = new ArrayList<>();
        invalid.add(bytes("[]"));
        invalid.add(bytes(mapper.writeValueAsString(valid) + "{}"));
        invalid.add(new byte[] {(byte) 0xc3, (byte) 0x28});
        invalid.add(bytes("[".repeat(17) + "0" + "]".repeat(17)));
        invalid.add(bytes("{\"" + "n".repeat(129) + "\":0}"));
        invalid.add(bytes("[" + "0,".repeat(4096) + "0]"));
        invalid.add(" ".repeat(ProcessControlResponseCodec.MAX_BYTES + 1).getBytes(StandardCharsets.UTF_8));

        ObjectNode missing = valid.deepCopy();
        missing.remove("version");
        invalid.add(mapper.writeValueAsBytes(missing));
        ObjectNode extra = valid.deepCopy();
        extra.put("extra", true);
        invalid.add(mapper.writeValueAsBytes(extra));
        ObjectNode coercion = valid.deepCopy();
        ((ObjectNode) coercion.path("activeWaits").get(0)).put("occurrences", 1.0);
        invalid.add(mapper.writeValueAsBytes(coercion));
        ObjectNode negative = valid.deepCopy();
        ((ObjectNode) negative.path("activeWaits").get(0)).put("occurrences", -1);
        invalid.add(mapper.writeValueAsBytes(negative));
        ObjectNode unknownEnum = valid.deepCopy();
        ((ObjectNode) unknownEnum.path("activeWaits").get(0)).put("kind", "UNKNOWN");
        invalid.add(mapper.writeValueAsBytes(unknownEnum));
        ObjectNode numericEnum = valid.deepCopy();
        ((ObjectNode) numericEnum.path("activeWaits").get(0)).put("kind", 1);
        invalid.add(mapper.writeValueAsBytes(numericEnum));
        ObjectNode nullOccurrences = valid.deepCopy();
        ((ObjectNode) nullOccurrences.path("activeWaits").get(0)).putNull("occurrences");
        invalid.add(mapper.writeValueAsBytes(nullOccurrences));
        ObjectNode duplicateActions = valid.deepCopy();
        duplicateActions.set("allowedActions", mapper.createArrayNode().add("CANCEL").add("CANCEL"));
        invalid.add(mapper.writeValueAsBytes(duplicateActions));
        ObjectNode unknownAction = valid.deepCopy();
        unknownAction.set("allowedActions", mapper.createArrayNode().add("UNKNOWN"));
        invalid.add(mapper.writeValueAsBytes(unknownAction));
        ObjectNode unknownBlock = valid.deepCopy();
        unknownBlock.put("cancellationBlockReason", "UNKNOWN");
        invalid.add(mapper.writeValueAsBytes(unknownBlock));
        ObjectNode invalidTime = valid.deepCopy();
        invalidTime.put("observedAt", "not-an-instant");
        invalid.add(mapper.writeValueAsBytes(invalidTime));
        ObjectNode numericTime = valid.deepCopy();
        numericTime.put("observedAt", 1);
        invalid.add(mapper.writeValueAsBytes(numericTime));
        ObjectNode localTime = valid.deepCopy();
        localTime.put("observedAt", "2026-11-05T00:05:59.999999");
        invalid.add(mapper.writeValueAsBytes(localTime));
        ObjectNode nullVersion = valid.deepCopy();
        nullVersion.putNull("version");
        invalid.add(mapper.writeValueAsBytes(nullVersion));
        ObjectNode missingNullableFinishedAt = valid.deepCopy();
        ((ObjectNode) missingNullableFinishedAt.path("process")).remove("finishedAt");
        invalid.add(mapper.writeValueAsBytes(missingNullableFinishedAt));
        ObjectNode longString = valid.deepCopy();
        ((ObjectNode) longString.path("process")).put("definitionKey", "x".repeat(1025));
        invalid.add(mapper.writeValueAsBytes(longString));
        ObjectNode terminalDrift = valid.deepCopy();
        ((ObjectNode) terminalDrift.path("process")).put("status", "COMPLETED");
        invalid.add(mapper.writeValueAsBytes(terminalDrift));

        String duplicate = mapper.writeValueAsString(valid);
        invalid.add(bytes(duplicate.substring(0, duplicate.length() - 1)
                + ",\"version\":\"" + VERSION_B + "\"}"));
        invalid.add(bytes(duplicate.replace("\"occurrences\":1", "\"occurrences\":123456789012345678901234567890123")));

        invalid.forEach(this::assertInvalidSnapshot);
    }

    @Test
    void strict_result_codec_rejects_missing_extra_duplicate_coercion_enum_time_and_invariants() {
        ObjectNode valid = mapper.valueToTree(cancelled("process-1", "command-1", FINISHED, DECIDED));
        List<byte[]> invalid = new ArrayList<>();
        invalid.add(bytes("true"));
        invalid.add(bytes(mapper.writeValueAsString(valid) + "[]"));
        ObjectNode missing = valid.deepCopy();
        missing.remove("commandId");
        invalid.add(mapper.writeValueAsBytes(missing));
        ObjectNode extra = valid.deepCopy();
        extra.put("extra", true);
        invalid.add(mapper.writeValueAsBytes(extra));
        ObjectNode coercion = valid.deepCopy();
        coercion.put("accepted", "true");
        invalid.add(mapper.writeValueAsBytes(coercion));
        ObjectNode unknownStatus = valid.deepCopy();
        unknownStatus.put("status", "UNKNOWN");
        invalid.add(mapper.writeValueAsBytes(unknownStatus));
        ObjectNode invalidTime = valid.deepCopy();
        invalidTime.put("finishedAt", "not-an-instant");
        invalid.add(mapper.writeValueAsBytes(invalidTime));
        ObjectNode numericTime = valid.deepCopy();
        numericTime.put("decidedAt", 1);
        invalid.add(mapper.writeValueAsBytes(numericTime));
        ObjectNode localTime = valid.deepCopy();
        localTime.put("decidedAt", "2026-11-05T00:05:59.999999");
        invalid.add(mapper.writeValueAsBytes(localTime));
        ObjectNode nullTime = valid.deepCopy();
        nullTime.putNull("decidedAt");
        invalid.add(mapper.writeValueAsBytes(nullTime));
        ObjectNode invalidVersion = valid.deepCopy();
        invalidVersion.put("resultingVersion", "A".repeat(64));
        invalid.add(mapper.writeValueAsBytes(invalidVersion));
        ObjectNode invalidInvariant = valid.deepCopy();
        invalidInvariant.put("accepted", false);
        invalid.add(mapper.writeValueAsBytes(invalidInvariant));
        ObjectNode missingClosure = valid.deepCopy();
        missingClosure.remove("linkedAgentClosureScheduled");
        invalid.add(mapper.writeValueAsBytes(missingClosure));
        ObjectNode validNoop = mapper.valueToTree(new CancelProcessResult("process-1", "command-1", false,
                ProcessInstanceStatusEnum.COMPLETED, VERSION_B, FINISHED, DECIDED, false));
        ObjectNode missingAccepted = validNoop.deepCopy();
        missingAccepted.remove("accepted");
        invalid.add(mapper.writeValueAsBytes(missingAccepted));
        ObjectNode nullAccepted = validNoop.deepCopy();
        nullAccepted.putNull("accepted");
        invalid.add(mapper.writeValueAsBytes(nullAccepted));
        ObjectNode nullClosure = valid.deepCopy();
        nullClosure.putNull("linkedAgentClosureScheduled");
        invalid.add(mapper.writeValueAsBytes(nullClosure));
        String duplicate = mapper.writeValueAsString(valid);
        invalid.add(bytes(duplicate.substring(0, duplicate.length() - 1)
                + ",\"commandId\":\"duplicate\"}"));

        invalid.forEach(this::assertInvalidCancellation);
    }

    @Test
    void strict_error_codec_accepts_only_exact_status_code_pairs_and_shape() {
        assertEquals("TIANJI_TENANT_CONTEXT_CHANGED", codec.controlErrorCode(bytes("{\"code\":\"TIANJI_TENANT_CONTEXT_CHANGED\"}"), 409));
        for (String code : List.of("TIANJI_IDEMPOTENCY_CONFLICT", "TIANJI_PROCESS_VERSION_CONFLICT", "TIANJI_PROCESS_CANCEL_BLOCKED")) {
            assertNull(codec.controlErrorCode(bytes("{\"code\":\"" + code + "\"}"), 409));
        }
        assertNull(codec.controlErrorCode(bytes("{\"code\":\"TIANJI_REQUEST_TOO_LARGE\"}"), 413));
        assertNull(codec.controlErrorCode(bytes("{\"code\":\"TIANJI_CAPACITY_EXCEEDED\"}"), 429));
        assertNull(codec.cancellationErrorCode(bytes("{\"code\":\"TIANJI_CAPACITY_EXCEEDED\"}"), 429));

        ERROR_CODES.forEach((status, codes) -> codes.forEach(code -> assertEquals(code,
                codec.cancellationErrorCode(bytes("{\"code\":\"" + code + "\",\"correlationId\":null}"), status))));
        assertNull(codec.cancellationErrorCode(bytes("{\"code\":\"TIANJI_PROCESS_VERSION_CONFLICT\"}"), 400));
        assertNull(codec.cancellationErrorCode(bytes("{\"code\":\"PRIVATE_UNKNOWN\"}"), 409));
        assertNull(codec.cancellationErrorCode(bytes("{\"code\":1}"), 409));
        assertNull(codec.cancellationErrorCode(bytes("{\"code\":\"TIANJI_INVALID_COMMAND\",\"extra\":true}"), 400));
        assertNull(codec.cancellationErrorCode(bytes("{\"code\":\"TIANJI_INVALID_COMMAND\",\"code\":\"TIANJI_INVALID_COMMAND\"}"), 400));
        assertNull(codec.cancellationErrorCode(bytes("not-json"), 400));
    }

    private void assertInvalidSnapshot(byte[] body) {
        ProcessClientException failure = assertThrows(ProcessClientException.class, () -> codec.snapshot(body));
        assertEquals("TIANJI_INVALID_RESPONSE", failure.code());
        assertNull(failure.getCause());
    }

    private void assertInvalidCancellation(byte[] body) {
        ProcessClientException failure = assertThrows(ProcessClientException.class, () -> codec.cancellation(body));
        assertEquals("TIANJI_INVALID_RESPONSE", failure.code());
        assertNull(failure.getCause());
    }

    private static void assertInvalidResponse(ThrowingAction action) {
        ProcessClientException failure = assertThrows(ProcessClientException.class, action::run);
        assertEquals("TIANJI_INVALID_RESPONSE", failure.code());
    }

    private byte[] json(Object value) {
        return mapper.writeValueAsBytes(value);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static ProcessControlSnapshotDTO activeSnapshot(String instanceId) {
        var process = new ProcessInstanceDTO(instanceId, "invoiceReview", "release-7", "invoice-4711", "corr-4711",
                ProcessInstanceStatusEnum.ACTIVE, SUBMITTED, null);
        return new ProcessControlSnapshotDTO(process,
                List.of(new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 1)),
                Set.of(ProcessControlActionEnum.CANCEL), ProcessControlBlockReasonEnum.NONE, VERSION_A, DECIDED);
    }

    private static CancelProcessCommand command(String instanceId, String commandId, String reason) {
        return new CancelProcessCommand(instanceId, commandId, VERSION_A, reason);
    }

    private static CancelProcessResult cancelled(String instanceId, String commandId, Instant finishedAt, Instant decidedAt) {
        return new CancelProcessResult(instanceId, commandId, true, ProcessInstanceStatusEnum.CANCELLED,
                VERSION_B, finishedAt, decidedAt, true);
    }

    private static HttpProcessClient client(URI base, HttpClient transport,
                                            java.util.function.Supplier<String> token, Duration timeout) {
        return new HttpProcessClient(base, transport, token, timeout);
    }

    private static byte[] body(HttpRequest request) {
        BodyCapture capture = new BodyCapture();
        request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()).subscribe(capture);
        return capture.body.join();
    }

    private static SSLContext defaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("default TLS context is unavailable", failure);
        }
    }

    private record Reply(int status, HttpHeaders headers, List<byte[]> chunks) {
        static Reply of(int status, String contentType, byte[] body) {
            return of(status, Map.of("Content-Type", List.of(contentType)), body);
        }

        static Reply of(int status, Map<String, List<String>> headers, byte[] body) {
            return new Reply(status, HttpHeaders.of(headers, (name, value) -> true), List.of(body));
        }
    }

    private static final class FakeHttpClient extends HttpClient {
        private final ArrayDeque<Reply> replies = new ArrayDeque<>();
        private final List<HttpRequest> requests = new ArrayList<>();
        private final Redirect redirects;
        private RuntimeException syncFailure;
        private boolean incomplete;
        private CompletableFuture<?> incompleteFuture;
        private TrackingSubscription lastSubscription;

        private FakeHttpClient() {
            this(Redirect.NEVER);
        }

        private FakeHttpClient(Redirect redirects) {
            this.redirects = redirects;
        }

        void reply(int status, String contentType, byte[] body) {
            replies.add(Reply.of(status, contentType, body));
        }

        void reply(int status, Map<String, List<String>> headers, byte[] body) {
            replies.add(Reply.of(status, headers, body));
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return redirects; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return defaultSslContext(); }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            throw new UnsupportedOperationException("synchronous fake send is unsupported");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                 HttpResponse.BodyHandler<T> responseBodyHandler) {
            requests.add(request);
            if (syncFailure != null) throw syncFailure;
            if (incomplete) {
                CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
                incompleteFuture = future;
                return future;
            }
            Reply reply = replies.pollFirst();
            if (reply == null) {
                reply = Reply.of(500, "application/json", bytes("{\"code\":\"TIANJI_DEPENDENCY_UNAVAILABLE\"}"));
            }
            HttpResponse.BodySubscriber<T> subscriber = responseBodyHandler.apply(new ResponseInfo(reply));
            TrackingSubscription subscription = new TrackingSubscription();
            lastSubscription = subscription;
            subscriber.onSubscribe(subscription);
            for (byte[] chunk : reply.chunks()) subscriber.onNext(List.of(ByteBuffer.wrap(chunk)));
            subscriber.onComplete();
            Reply completed = reply;
            return subscriber.getBody().toCompletableFuture().handle((value, failure) -> {
                if (failure != null) throw new CompletionException(failure);
                return new FakeResponse<>(completed.status(), request, completed.headers(), value);
            });
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                 HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                 HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }
    }

    private record ResponseInfo(Reply reply) implements HttpResponse.ResponseInfo {
        @Override public int statusCode() { return reply.status(); }
        @Override public HttpHeaders headers() { return reply.headers(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private record FakeResponse<T>(int statusCode, HttpRequest request, HttpHeaders headers, T body)
            implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private static final class TrackingSubscription implements Flow.Subscription {
        private boolean cancelled;
        @Override public void request(long count) { }
        @Override public void cancel() { cancelled = true; }
    }

    private static final class BodyCapture implements Flow.Subscriber<ByteBuffer> {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
        @Override public void onNext(ByteBuffer buffer) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            output.writeBytes(bytes);
        }
        @Override public void onError(Throwable failure) { body.completeExceptionally(failure); }
        @Override public void onComplete() { body.complete(output.toByteArray()); }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }
}
