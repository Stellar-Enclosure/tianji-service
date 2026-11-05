package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryCommandClient;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryCommandClient.CheckpointResumeCommand;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryCommandClient.HistoricalStartReceiptCommand;
import top.egon.cola.platform.tianji.contract.agent.AgentRunCorrelationClient;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.ListAgentRunsByCorrelationQuery;
import top.egon.cola.platform.tianji.contract.agent.ListAgentRunsQuery;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessAgentOutcomesQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ListProcessInstancesByCorrelationQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessOwnerObservationClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerObservationHttpTest {
    private static final String TOKEN = "fixture-private-bearer";
    private static final String BASE = "https://owner.example/context/";
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final String INVALID = "TIANJI_INVALID_RESPONSE";
    private static final String FROM_TEXT = "2026-11-06T01:02:03.000001Z";
    private static final String TO_TEXT = "2026-11-06T02:03:04Z";
    private static final Instant FROM = Instant.parse(FROM_TEXT);
    private static final Instant TO = Instant.parse(TO_TEXT);
    private static final String WINDOW = "&fromInclusive=2026-11-06T01%3A02%3A03.000001Z"
            + "&toExclusive=2026-11-06T02%3A03%3A04Z";
    private static final String OUTCOME_CURSOR = "v1." + "f".repeat(64);
    private static final ListAgentRunsByCorrelationQuery AGENT = new ListAgentRunsByCorrelationQuery("trace", FROM, TO, null);
    private static final ListProcessInstancesByCorrelationQuery PROCESS =
            new ListProcessInstancesByCorrelationQuery("trace:one", FROM, TO, null);
    private static final GetProcessAgentOutcomesQuery OUTCOMES = new GetProcessAgentOutcomesQuery("instance-one", null);
    private static final String RUN = """
            {"runId":"run-one","definitionKey":"definition-1","definitionVersion":1,"sessionId":null,
             "status":"COMPLETED","waitReason":null,"cancelRequested":false,"attempt":2,"revision":4,
             "lastEventSequence":10,"deadlineAt":null,"createdAt":"%s","finishedAt":"2026-11-06T01:03:00.123456789Z"}
            """.formatted(FROM_TEXT);
    private static final String RECEIPT = """
            {"instanceId":"instance-one","releaseVersion":"release-1","submittedAt":"%s"}
            """.formatted(FROM_TEXT);
    private static final String OUTCOME = """
            {"activityId":"agentTask","dispatchState":"ACKNOWLEDGED","inboxState":"APPLIED","terminalStatus":"COMPLETED",
             "dispatchRecordedAt":"%s","receivedAt":"2026-11-06T01:03:00Z","decidedAt":"2026-11-06T01:04:00Z"}
            """.formatted(FROM_TEXT);
    private static final Map<String, List<String>> HEADERS = Map.of(
            "Content-Type", List.of("application/json; charset=UTF-8"), "Cache-Control", List.of("no-store"));

    @Test
    void uses_only_the_exact_encoded_filters_for_the_owned_agent_list() {
        var wire = reply(200, agentPage(RUN, null));
        AgentRunCorrelationClient client = agent(wire);
        var query = new ListAgentRunsByCorrelationQuery("trace /+?測", FROM, TO, "v1.incoming");
        var page = client.listByCorrelation(query);

        assertRequest(wire, "agent-runs?correlationId=trace%20%2F%2B%3F%E6%B8%AC" + WINDOW + "&cursor=v1.incoming");
        assertEquals("run-one", page.items().getFirst().runId());
        assertEquals(AgentRunStatusEnum.COMPLETED, page.items().getFirst().status());
        assertEquals(FROM, page.items().getFirst().createdAt());
        assertEquals(Instant.parse("2026-11-06T01:03:00.123456789Z"), page.items().getFirst().finishedAt());
        assertNull(page.nextCursor());
    }

    @Test
    void keeps_the_existing_unfiltered_agent_list_query_and_decoder() {
        var wire = new FakeHttpClient(200, Map.of(), "{\"items\":[],\"nextCursor\":\"old-cursor\"}".getBytes(StandardCharsets.UTF_8));
        var page = agent(wire).list(new ListAgentRunsQuery("old-cursor", 37));
        assertEquals(URI.create(BASE + "api/tianji/v1/agent-runs?limit=37&cursor=old-cursor"), wire.request.uri());
        assertTrue(wire.request.headers().firstValue("Cache-Control").isEmpty());
        assertEquals("old-cursor", page.nextCursor());
    }

    @Test
    void reads_only_owned_process_start_references_with_exact_correlation_and_cursor() {
        var wire = reply(200, receiptPage(RECEIPT, null));
        ProcessOwnerObservationClient client = process(wire);
        var query = new ListProcessInstancesByCorrelationQuery("trace:one", FROM, TO, "v1.opaque_-");
        var page = client.listByCorrelation(query);

        assertRequest(wire, "process-instance-correlations?correlationId=trace%3Aone" + WINDOW + "&cursor=v1.opaque_-");
        assertEquals(query.correlationId(), page.correlationId());
        assertEquals(TO, page.observedAt());
        assertEquals("instance-one", page.items().getFirst().instanceId());
        assertEquals("release-1", page.items().getFirst().releaseVersion());
        assertEquals(FROM, page.items().getFirst().submittedAt());
        assertNull(page.nextCursor());
    }

    @Test
    void reads_process_outcomes_with_encoded_instance_and_preserves_repeated_activity_rows() {
        String id = "instance +測";
        String pending = """
                {"activityId":"agentTask","dispatchState":"PENDING","inboxState":"RUN_NOT_CONFIRMED","terminalStatus":null,
                 "dispatchRecordedAt":"%s","receivedAt":null,"decidedAt":null}
                """.formatted(FROM_TEXT);
        var wire = reply(200, outcomePage(OUTCOME + "," + pending, null).replace(OUTCOMES.instanceId(), id));
        var page = process(wire).getAgentOutcomes(new GetProcessAgentOutcomesQuery(id, OUTCOME_CURSOR));

        assertRequest(wire, "process-instances/instance%20%2B%E6%B8%AC/agent-outcomes?cursor=" + OUTCOME_CURSOR);
        assertEquals(id, page.instanceId());
        assertEquals(2, page.items().size());
        assertEquals(page.items().getFirst().activityId(), page.items().getLast().activityId());
        assertEquals(ProcessAgentOutcomePageDTO.InboxState.APPLIED, page.items().getFirst().inboxState());
        assertEquals(AgentRunStatusEnum.COMPLETED, page.items().getFirst().terminalStatus());
        assertNull(page.items().getLast().terminalStatus());
    }

    @Test
    void validates_closed_success_shape_scalar_types_and_the_half_open_agent_window() {
        for (String body : List.of(
                agentPage(RUN.replace("\"attempt\":2", "\"attempt\":\"2\""), null),
                agentPage(RUN.replace("\"revision\":4", "\"revision\":4.0"), null),
                agentPage(RUN.replace("\"revision\":4", "\"revision\":9223372036854775808"), null),
                agentPage(RUN.replace("\"cancelRequested\":false", "\"cancelRequested\":0"), null),
                agentPage(RUN.replace("\"sessionId\":null,", ""), null),
                agentPage(RUN.replace("\"sessionId\":null", "\"sessionId\":\"\\uD800\""), null),
                agentPage(RUN.replace("\"run-one\"", "\"run-one\",\"owner\":\"foreign\""), null),
                agentPage(RUN.replace("\"run-one\"", "\"run-one\",\"runId\":\"run-one\""), null),
                agentPage(RUN.replace(FROM_TEXT, TO_TEXT), null),
                agentPage(RUN.replace(FROM_TEXT, "2026-11-06T01:02:03Z"), null),
                agentPage(RUN.replace(FROM_TEXT, "2026-11-06T01:02:03.0000010Z"), null),
                agentPage(RUN + "," + RUN, null),
                agentPage(RUN + "," + RUN.replace("run-one", "run-two"), null),
                agentPage(RUN, "v1.too-short"),
                agentPage(RUN, null) + "{}")) {
            assertAgentFailure(reply(200, body), 200, INVALID);
        }
        assertAgentFailure(new FakeHttpClient(200, HEADERS, new byte[]{(byte) 0xc0, (byte) 0xaf}), 200, INVALID);
    }

    @Test
    void rejects_process_echo_mismatch_windows_bad_receipts_and_impossible_outcomes() {
        for (String body : List.of(
                receiptPage(RECEIPT, null).replace("trace:one", "Trace:one"),
                receiptPage(RECEIPT, null).replace(TO_TEXT, FROM_TEXT),
                receiptPage(RECEIPT.replace(FROM_TEXT, TO_TEXT), null),
                receiptPage(RECEIPT.replace("\"release-1\"", "\"release/1\""), null),
                receiptPage(RECEIPT + "," + RECEIPT, null),
                receiptPage(RECEIPT + "," + RECEIPT.replace("instance-one", "instance-two"), null),
                receiptPage(RECEIPT, "v1.too-short"))) {
            assertProcessFailure(reply(200, body), false, 200, INVALID);
        }
        for (String body : List.of(
                outcomePage(OUTCOME, null).replace("instance-one", "foreign-instance"),
                outcomePage(OUTCOME, null).replace("\"APPLIED\"", "\"UNKNOWN\""),
                outcomePage(OUTCOME, null).replace("\"COMPLETED\"", "\"RUNNING\""),
                outcomePage(OUTCOME, null).replace("\"COMPLETED\"", "null"),
                outcomePage(OUTCOME, null).replace("\"2026-11-06T01:04:00Z\"", "null"),
                outcomePage(OUTCOME, null).replace("\"activityId\"", "\"rawVariables\""),
                outcomePage(OUTCOME, OUTCOME_CURSOR))) {
            assertProcessFailure(reply(200, body), true, 200, INVALID);
        }
    }

    @Test
    void supports_full_pages_only_and_rejects_a_cursor_that_cannot_advance() {
        String runs = IntStream.iterate(20, i -> i > 0, i -> i - 1)
                .mapToObj(i -> RUN.replace("run-one", "run-%02d".formatted(i))).collect(Collectors.joining(","));
        String receipts = IntStream.iterate(20, i -> i > 0, i -> i - 1)
                .mapToObj(i -> RECEIPT.replace("instance-one", "instance-%02d".formatted(i))).collect(Collectors.joining(","));
        String outcomes = IntStream.range(0, 20).mapToObj(i -> OUTCOME).collect(Collectors.joining(","));

        var agentWire = reply(200, agentPage(runs, "v1.next"));
        assertEquals("v1.next", agent(agentWire).listByCorrelation(AGENT).nextCursor());
        assertRequest(agentWire, "agent-runs?correlationId=trace" + WINDOW);
        assertAgentFailure(reply(200, agentPage(runs + "," + RUN, null)), 200, INVALID);

        var processWire = reply(200, receiptPage(receipts, "v1.next"));
        assertEquals("v1.next", process(processWire).listByCorrelation(PROCESS).nextCursor());
        assertRequest(processWire, "process-instance-correlations?correlationId=trace%3Aone" + WINDOW);
        assertProcessFailure(reply(200, receiptPage(receipts + "," + RECEIPT, null)), false, 200, INVALID);

        var outcomeWire = reply(200, outcomePage(outcomes, OUTCOME_CURSOR));
        assertEquals(OUTCOME_CURSOR, process(outcomeWire).getAgentOutcomes(OUTCOMES).nextCursor());
        assertRequest(outcomeWire, "process-instances/instance-one/agent-outcomes");
        assertProcessFailure(reply(200, outcomePage(outcomes + "," + OUTCOME, null)), true, 200, INVALID);
        var repeated = reply(200, outcomePage(outcomes, OUTCOME_CURSOR));
        var failure = assertThrows(ProcessClientException.class, () -> process(repeated)
                .getAgentOutcomes(new GetProcessAgentOutcomesQuery(OUTCOMES.instanceId(), OUTCOME_CURSOR)));
        assertEquals(INVALID, failure.code());

        String supplementary = RECEIPT.replace("instance-one", "\uD800\uDC00");
        String bmp = RECEIPT.replace("instance-one", "\uE000");
        assertEquals(2, process(reply(200, receiptPage(supplementary + "," + bmp, null))).listByCorrelation(PROCESS).items().size());
        assertProcessFailure(reply(200, receiptPage(bmp + "," + supplementary, null)), false, 200, INVALID);
    }

    @Test
    void keeps_http_status_and_only_exposes_the_closed_route_error_codes() {
        Map.of(400, "TIANJI_INVALID_COMMAND", 401, "TIANJI_UNAUTHENTICATED", 403, "TIANJI_PERMISSION_DENIED",
                409, "TIANJI_TENANT_CONTEXT_CHANGED", 503, "TIANJI_DEPENDENCY_UNAVAILABLE").forEach((status, code) -> {
            assertAgentFailure(reply(status, "{\"code\":\"" + code + "\"}"), status, code);
            assertProcessFailure(reply(status, error(code)), false, status, code);
            assertProcessFailure(reply(status, error(code)), true, status, code);
        });
        assertAgentFailure(reply(403, "{\"code\":\"TIANJI_PERMISSION_DENIED\",\"message\":\"" + TOKEN + "\","
                + "\"correlationId\":\"foreign-owner\",\"decisionId\":null,\"retryAfter\":null}"), 403, "TIANJI_PERMISSION_DENIED");
        assertAgentFailure(reply(403, "{\"code\":\"TIANJI_PERMISSION_DENIED\",\"retryAfter\":7}"), 403, INVALID);
        assertAgentFailure(reply(403, error("TIANJI_UNAUTHENTICATED")), 403, INVALID);
        assertAgentFailure(reply(403, error("TIANJI_PERMISSION_DENIED").replace("null", "null,\"owner\":true")), 403, INVALID);

        assertProcessFailure(reply(401, "{\"code\":\"TIANJI_UNAUTHENTICATED\"}"), false, 401, INVALID);
        assertProcessFailure(reply(404, error("TIANJI_RESOURCE_NOT_FOUND")), false, 404, INVALID);
        assertProcessFailure(reply(404, error("TIANJI_RESOURCE_NOT_FOUND")), true, 404, "TIANJI_RESOURCE_NOT_FOUND");
        assertProcessFailure(reply(404, error("TIANJI_RESOURCE_NOT_FOUND").replace("null", "\"foreign-owner\"")), true, 404, INVALID);
    }

    @Test
    void enforces_response_headers_size_and_the_secure_origin_before_credentials() {
        assertAgentFailure(reply(202, agentPage("", null)), 202, INVALID);
        assertProcessFailure(reply(302, receiptPage("", null)), false, 302, INVALID);
        assertProcessFailure(reply(204, ""), true, 204, INVALID);
        var noCache = new FakeHttpClient(200, Map.of("Content-Type", List.of("application/json")),
                receiptPage("", null).getBytes(StandardCharsets.UTF_8));
        assertProcessFailure(noCache, false, 200, INVALID);
        var wrongMedia = new FakeHttpClient(200, Map.of("Content-Type", List.of("application/problem+json"),
                "Cache-Control", List.of("no-store")), agentPage("", null).getBytes(StandardCharsets.UTF_8));
        assertAgentFailure(wrongMedia, 200, INVALID);
        var duplicateMedia = new FakeHttpClient(403, Map.of("Content-Type", List.of("application/json", "text/plain"),
                "Cache-Control", List.of("no-store")), error("TIANJI_PERMISSION_DENIED").getBytes(StandardCharsets.UTF_8));
        assertProcessFailure(duplicateMedia, true, 403, INVALID);

        byte[] tooLarge = new byte[262_145];
        var oversizedAgent = new FakeHttpClient(200, HEADERS, tooLarge);
        assertAgentFailure(oversizedAgent, 0, "TIANJI_RESPONSE_TOO_LARGE");
        assertTrue(oversizedAgent.subscription.cancelled);
        var oversizedProcess = new FakeHttpClient(200, HEADERS, tooLarge);
        assertProcessFailure(oversizedProcess, false, 0, "TIANJI_RESPONSE_TOO_LARGE");
        assertTrue(oversizedProcess.subscription.cancelled);

        for (String base : List.of("http://owner.example/", "http://localhost.evil/", "https://owner.example/a/../b/")) {
            var wire = reply(200, agentPage("", null));
            var reads = new AtomicInteger();
            Supplier<String> credentials = () -> { reads.incrementAndGet(); return TOKEN; };
            var agent = new HttpAgentClient(URI.create(base), wire, credentials, TIMEOUT, 0, Duration.ZERO);
            var process = new HttpProcessClient(URI.create(base), wire, credentials, TIMEOUT);
            assertThrows(IllegalArgumentException.class, () -> agent.listByCorrelation(AGENT));
            assertThrows(IllegalArgumentException.class, () -> process.listByCorrelation(PROCESS));
            assertThrows(IllegalArgumentException.class, () -> process.getAgentOutcomes(OUTCOMES));
            assertEquals(0, reads.get());
            assertEquals(0, wire.sent);
        }
    }

    @Test
    void uses_the_existing_deadline_and_disallows_redirects() {
        var stalledAgent = reply(200, agentPage("", null));
        stalledAgent.stalled = new CompletableFuture<>();
        var agent = new HttpAgentClient(URI.create("http://127.0.0.1/"), stalledAgent, () -> TOKEN,
                Duration.ofMillis(5), 0, Duration.ZERO);
        var agentFailure = assertThrows(AgentClientException.class, () -> agent.listByCorrelation(AGENT));
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", agentFailure.code());
        assertEquals(1, stalledAgent.sent);
        assertTrue(stalledAgent.stalled.isCancelled());

        var stalledProcess = reply(200, receiptPage("", null));
        stalledProcess.stalled = new CompletableFuture<>();
        var process = new HttpProcessClient(URI.create(BASE), stalledProcess, () -> TOKEN, Duration.ofMillis(5));
        var processFailure = assertThrows(ProcessClientException.class, () -> process.listByCorrelation(PROCESS));
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", processFailure.code());
        assertEquals(1, stalledProcess.sent);
        assertTrue(stalledProcess.stalled.isCancelled());

        var redirecting = reply(200, agentPage("", null));
        redirecting.redirect = HttpClient.Redirect.ALWAYS;
        assertThrows(IllegalArgumentException.class, () -> agent(redirecting));
        assertThrows(IllegalArgumentException.class, () -> process(redirecting));
        assertEquals(0, redirecting.sent);
    }

    private static final UUID INTENT = UUID.fromString("5c837d15-109c-4969-b180-fb2e1aa2cd67");
    private static final CheckpointResumeCommand RESUME = new CheckpointResumeCommand("run-one", "resume-one", 3);
    private static final HistoricalStartReceiptCommand RECONCILE = new HistoricalStartReceiptCommand("run-one", INTENT);
    private static final String RESUME_ACK = """
            {"runId":"run-one","status":"RUNNING","attempt":1,"revision":5}
            """;
    private static final String HISTORY = """
            {"intentId":"%s","dispatchRunId":"run-one","instanceId":"instance-one",
             "recordedAt":"2026-11-06T02:00:00.001Z","observedAt":"2026-11-06T01:00:00Z"}
            """.formatted(INTENT);

    @Test
    void sends_one_exact_resume_post_and_reads_the_original_durable_acknowledgement() {
        var wire = reply(200, RESUME_ACK);
        AgentRecoveryCommandClient client = agent(wire);
        var ack = client.resumeCheckpoint(RESUME);
        assertEquals("run-one", ack.runId());
        assertEquals(AgentRunStatusEnum.RUNNING, ack.status());
        assertEquals(1, ack.attempt());
        assertEquals(5, ack.revision());
        var body = assertCommand(wire, AgentRecoveryCommandClient.CHECKPOINT_RESUME_PATH);
        assertEquals(Set.of("runId", "commandId", "expectedRevision"), body.propertyNames());
        assertEquals(RESUME.runId(), body.get("runId").asString());
        assertEquals(RESUME.commandId(), body.get("commandId").asString());
        assertEquals(RESUME.expectedRevision(), body.get("expectedRevision").asLong());
    }

    @Test
    void sends_one_exact_historical_post_and_retains_both_independent_clock_values() {
        var wire = reply(200, HISTORY);
        var receipt = agent(wire).reconcileHistoricalStart(RECONCILE);
        assertEquals(INTENT, receipt.intentId());
        assertEquals("run-one", receipt.dispatchRunId());
        assertEquals("instance-one", receipt.instanceId());
        assertEquals(Instant.parse("2026-11-06T02:00:00.001Z"), receipt.recordedAt());
        assertEquals(Instant.parse("2026-11-06T01:00:00Z"), receipt.observedAt());
        var body = assertCommand(wire, AgentRecoveryCommandClient.HISTORICAL_START_RECEIPT_PATH);
        assertEquals(Set.of("runId", "intentId"), body.propertyNames());
        assertEquals(RECONCILE.runId(), body.get("runId").asString());
        assertEquals(INTENT.toString(), body.get("intentId").asString());
    }

    @Test
    void rejects_an_acknowledgement_for_another_revision() {
        var wire = reply(200, RESUME_ACK.replace("\"revision\":5", "\"revision\":6"));
        assertRecoveryFailure(wire, false, 200, INVALID);
    }

    @Test
    void rejects_mismatched_and_noncanonical_recovery_success_responses() {
        for (String bad : List.of(RESUME_ACK.replace("run-one", "other-run"), RESUME_ACK.replace("RUNNING", "COMPLETED"),
                RESUME_ACK.replace("\"attempt\":1", "\"attempt\":1.0"),
                RESUME_ACK.replace("\"revision\":5", "\"revision\":5,\"revision\":5"))) {
            assertRecoveryFailure(reply(200, bad), false, 200, INVALID);
        }
        for (String bad : List.of(HISTORY.replace("run-one", "other-run"), HISTORY.replace(INTENT.toString(), UUID.randomUUID().toString()),
                HISTORY.replace("02:00:00.001Z", "02:00:00.001+00:00"), HISTORY.replace("02:00:00.001Z", "24:00:00Z"),
                HISTORY.replace("\"instanceId\":\"instance-one\"", "\"instanceId\":\"instance-one\",\"extra\":true"))) {
            assertRecoveryFailure(reply(200, bad), true, 200, INVALID);
        }
        assertRecoveryFailure(reply(202, RESUME_ACK), false, 202, INVALID);
        assertRecoveryFailure(new FakeHttpClient(200, Map.of("Content-Type", List.of("application/json")),
                HISTORY.getBytes(StandardCharsets.UTF_8)), true, 200, INVALID);
    }

    @Test
    void retains_safe_denial_capacity_and_unknown_effect_without_replaying_a_post() {
        assertRecoveryFailure(reply(403, "{\"code\":\"TIANJI_PERMISSION_DENIED\"}"), false, 403, "TIANJI_PERMISSION_DENIED");
        assertRecoveryFailure(reply(429, error("TIANJI_CAPACITY_EXCEEDED")), false, 429, "TIANJI_CAPACITY_EXCEEDED");
        assertRecoveryFailure(reply(409, error("TIANJI_SIDE_EFFECT_UNKNOWN")), true, 409, "TIANJI_SIDE_EFFECT_UNKNOWN");
        assertRecoveryFailure(reply(409, error("TIANJI_SIDE_EFFECT_UNKNOWN")), false, 409, INVALID);
        assertRecoveryFailure(reply(409, "{\"code\":\"TIANJI_SIDE_EFFECT_UNKNOWN\",\"extra\":\"" + TOKEN + "\"}"), true, 409, INVALID);
    }

    @Test
    void caps_recovery_responses_and_refuses_plaintext_before_reading_credentials() {
        var oversized = new FakeHttpClient(200, HEADERS, new byte[16_385]);
        assertRecoveryFailure(oversized, true, 0, "TIANJI_RESPONSE_TOO_LARGE");
        assertTrue(oversized.subscription.cancelled);
        var blocked = reply(200, RESUME_ACK);
        var credentials = new AtomicInteger();
        var client = new HttpAgentClient(URI.create("http://owner.example/"), blocked,
                () -> { credentials.incrementAndGet(); return TOKEN; }, TIMEOUT, 0, Duration.ZERO);
        assertThrows(IllegalArgumentException.class, () -> client.resumeCheckpoint(RESUME));
        assertThrows(IllegalArgumentException.class, () -> client.reconcileHistoricalStart(RECONCILE));
        assertEquals(0, credentials.get());
        assertEquals(0, blocked.sent);
    }

    @Test
    void a_lost_acknowledgement_does_not_retry_the_command() {
        var wire = reply(200, RESUME_ACK);
        wire.stalled = new CompletableFuture<>();
        var client = new HttpAgentClient(URI.create(BASE), wire, () -> TOKEN, Duration.ofMillis(5), 4, Duration.ZERO);
        var failure = assertThrows(AgentClientException.class, () -> client.resumeCheckpoint(RESUME));
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", failure.code());
        assertEquals(1, wire.sent);
        assertTrue(wire.stalled.isCancelled());
    }

    private static tools.jackson.databind.JsonNode assertCommand(FakeHttpClient wire, String path) {
        assertEquals(1, wire.sent);
        assertEquals("POST", wire.request.method());
        assertEquals(URI.create(BASE).resolve(path.substring(1)), wire.request.uri());
        assertEquals("Bearer " + TOKEN, wire.request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json", wire.request.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("no-store", wire.request.headers().firstValue("Cache-Control").orElseThrow());
        var body = new CompletableFuture<byte[]>();
        wire.request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                buffer.writeBytes(bytes);
            }
            @Override public void onError(Throwable failure) { body.completeExceptionally(failure); }
            @Override public void onComplete() { body.complete(buffer.toByteArray()); }
        });
        return JsonMapper.builder().build().readTree(body.join());
    }

    private static void assertRecoveryFailure(FakeHttpClient wire, boolean historical, int status, String code) {
        var failure = assertThrows(AgentClientException.class, () -> {
            if (historical) agent(wire).reconcileHistoricalStart(RECONCILE);
            else agent(wire).resumeCheckpoint(RESUME);
        });
        assertEquals(status, failure.httpStatus());
        assertEquals(code, failure.code());
        assertNull(failure.correlationId());
        assertNull(failure.retryAfter());
        assertFalse(failure.toString().contains(TOKEN));
        assertEquals(1, wire.sent);
    }

    private static void assertRequest(FakeHttpClient wire, String suffix) {
        assertEquals(1, wire.sent);
        assertEquals("GET", wire.request.method());
        assertEquals(URI.create(BASE + "api/tianji/v1/" + suffix), wire.request.uri());
        assertEquals(TIMEOUT, wire.request.timeout().orElseThrow());
        assertEquals("Bearer " + TOKEN, wire.request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json", wire.request.headers().firstValue("Accept").orElseThrow());
        assertEquals("no-store", wire.request.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(wire.request.headers().firstValue("Content-Type").isEmpty());
        assertTrue(wire.request.headers().firstValue("X-Egon-Tianji-Tenant").isEmpty());
        assertEquals(0, wire.request.bodyPublisher().orElseThrow().contentLength());
    }

    private static void assertAgentFailure(FakeHttpClient wire, int status, String code) {
        var failure = assertThrows(AgentClientException.class, () -> agent(wire).listByCorrelation(AGENT));
        assertEquals(status, failure.httpStatus());
        assertEquals(code, failure.code());
        assertNull(failure.correlationId());
        assertNull(failure.retryAfter());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains(TOKEN));
        assertFalse(failure.toString().contains("foreign-owner"));
        assertEquals(1, wire.sent);
    }

    private static void assertProcessFailure(FakeHttpClient wire, boolean outcomes, int status, String code) {
        var client = process(wire);
        var failure = assertThrows(ProcessClientException.class, () -> {
            if (outcomes) client.getAgentOutcomes(OUTCOMES);
            else client.listByCorrelation(PROCESS);
        });
        assertEquals(status, failure.httpStatus());
        assertEquals(code, failure.code());
        assertNull(failure.correlationId());
        assertNull(failure.retryAfter());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains(TOKEN));
        assertFalse(failure.toString().contains("foreign-owner"));
        assertEquals(1, wire.sent);
    }

    private static HttpAgentClient agent(FakeHttpClient wire) {
        return new HttpAgentClient(URI.create(BASE), wire, () -> TOKEN, TIMEOUT, 0, Duration.ZERO);
    }

    private static ProcessOwnerObservationClient process(FakeHttpClient wire) {
        return new HttpProcessClient(URI.create(BASE), wire, () -> TOKEN, TIMEOUT);
    }

    private static FakeHttpClient reply(int status, String body) {
        return new FakeHttpClient(status, HEADERS, body.getBytes(StandardCharsets.UTF_8));
    }

    private static String agentPage(String items, String cursor) {
        return "{\"items\":[" + items + "],\"nextCursor\":" + jsonString(cursor) + "}";
    }

    private static String receiptPage(String items, String cursor) {
        return "{\"correlationId\":\"trace:one\",\"observedAt\":\"" + TO_TEXT + "\",\"items\":[" + items
                + "],\"nextCursor\":" + jsonString(cursor) + "}";
    }

    private static String outcomePage(String items, String cursor) {
        return "{\"instanceId\":\"instance-one\",\"observedAt\":\"" + TO_TEXT + "\",\"items\":[" + items
                + "],\"nextCursor\":" + jsonString(cursor) + "}";
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String error(String code) {
        return "{\"code\":\"" + code + "\",\"correlationId\":null}";
    }

    private static final class FakeHttpClient extends HttpClient {
        private final ReplyInfo info;
        private final byte[] body;
        private final TrackingSubscription subscription = new TrackingSubscription();
        private Redirect redirect = Redirect.NEVER;
        private CompletableFuture<?> stalled;
        private HttpRequest request;
        private int sent;

        private FakeHttpClient(int status, Map<String, List<String>> headers, byte[] body) {
            this.info = new ReplyInfo(status, HttpHeaders.of(headers, (name, value) -> true));
            this.body = body;
        }

        @Override public <T> HttpResponse<T> send(HttpRequest value, HttpResponse.BodyHandler<T> handler) {
            throw new AssertionError("Unexpected synchronous HTTP request");
        }

        @Override @SuppressWarnings("unchecked")
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest value, HttpResponse.BodyHandler<T> handler) {
            request = value;
            sent++;
            if (stalled != null) return (CompletableFuture<HttpResponse<T>>) stalled;
            var subscriber = handler.apply(info);
            subscriber.onSubscribe(subscription);
            subscriber.onNext(List.of(ByteBuffer.wrap(body)));
            if (!subscription.cancelled) subscriber.onComplete();
            return subscriber.getBody().toCompletableFuture().thenApply(reply -> new Reply<>(info, value, reply));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest value, HttpResponse.BodyHandler<T> handler,
                                                               HttpResponse.PushPromiseHandler<T> push) {
            throw new AssertionError("Unexpected HTTP push");
        }

        @Override public Redirect followRedirects() { return redirect; }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public SSLContext sslContext() { throw new AssertionError("Unexpected TLS context lookup"); }
        @Override public SSLParameters sslParameters() { throw new AssertionError("Unexpected TLS parameters lookup"); }
    }

    private record ReplyInfo(int statusCode, HttpHeaders headers) implements HttpResponse.ResponseInfo {
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private record Reply<T>(ReplyInfo info, HttpRequest request, T body) implements HttpResponse<T> {
        @Override public int statusCode() { return info.statusCode(); }
        @Override public HttpHeaders headers() { return info.headers(); }
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return info.version(); }
    }

    private static final class TrackingSubscription implements Flow.Subscription {
        private boolean cancelled;
        @Override public void request(long count) { }
        @Override public void cancel() { cancelled = true; }
    }
}
