package top.egon.cola.platform.tianji.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpProcessClientTest {
    private static final String TOKEN = "test-process-token";
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AtomicReference<Handler> handler = new AtomicReference<>();
    private HttpServer server;
    private ExecutorService executor;
    private URI base;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try (exchange) { handler.get().handle(exchange); }
            catch (IOException ignored) { }
        });
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void release_process_and_decision_calls_use_neutral_contracts_and_verified_responses() throws Exception {
        String xml = "<definitions/>";
        var model = new ProcessModelResource(ProcessModelTypeEnum.BPMN, "invoice", xml, Map.of("amount", ProcessValueTypeEnum.INTEGER));
        var release = new ProcessReleaseDTO("v1", "a".repeat(64), NOW, List.of(new ProcessModelDTO(
                ProcessModelTypeEnum.BPMN, "invoice", sha(xml), Map.of("amount", ProcessValueTypeEnum.INTEGER))));
        var process = new ProcessInstanceDTO("instance-1", "invoice", "v1", "order-1", "trace-1",
                ProcessInstanceStatusEnum.ACTIVE, NOW, null);
        var decision = new DecisionResultDTO("12345678-1234-1234-1234-123456789abc", "risk", "v1", "trace-1", NOW,
                List.of(Map.of("route", "review")), List.of("row:1"));
        BlockingQueue<Seen> seen = new LinkedBlockingQueue<>();
        handler.set(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            seen.add(new Seen(exchange.getRequestMethod(), path, exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestBody().readAllBytes()));
            Object body = path.contains("process-releases") ? release : path.contains("process-instances") ? process : decision;
            respond(exchange, 200, "application/json", mapper.writeValueAsBytes(body));
        });
        HttpProcessClient client = client(Duration.ofSeconds(3));
        var publish = new PublishProcessReleaseCommand("v1", List.of(model));
        var start = new StartProcessCommand("invoice", "v1", "order-1", "command-1", "trace-1", Map.of("amount", 10));
        var evaluate = new EvaluateDecisionCommand("risk", "v1", "request-1", "trace-1", Map.of("amount", 10));
        assertEquals(release, client.publish(publish));
        assertEquals(release, client.get(new GetProcessReleaseQuery("v1")));
        assertEquals(process, client.start(start));
        assertEquals(process, client.get(new GetProcessInstanceQuery("instance-1")));
        assertEquals(decision, client.evaluate(evaluate));
        List<Seen> requests = new ArrayList<>();
        seen.drainTo(requests);
        assertEquals(List.of("POST", "GET", "POST", "GET", "POST"), requests.stream().map(Seen::method).toList());
        assertEquals(List.of("/api/tianji/v1/process-releases", "/api/tianji/v1/process-releases/v1",
                "/api/tianji/v1/process-instances", "/api/tianji/v1/process-instances/instance-1",
                "/api/tianji/v1/decisions/evaluate"), requests.stream().map(Seen::path).toList());
        assertTrue(requests.stream().allMatch(item -> ("Bearer " + TOKEN).equals(item.authorization())));
        assertEquals(publish, mapper.readValue(requests.get(0).body(), PublishProcessReleaseCommand.class));
        assertEquals(start, mapper.readValue(requests.get(2).body(), StartProcessCommand.class));
        assertEquals(evaluate, mapper.readValue(requests.get(4).body(), EvaluateDecisionCommand.class));
    }

    @Test
    void rejects_mismatched_release_content_and_mismatched_instance_identity() {
        var model = new ProcessModelResource(ProcessModelTypeEnum.BPMN, "invoice", "<xml/>", Map.of());
        var wrong = new ProcessReleaseDTO("v1", "a".repeat(64), NOW,
                List.of(new ProcessModelDTO(ProcessModelTypeEnum.BPMN, "invoice", "b".repeat(64), Map.of())));
        handler.set(exchange -> respond(exchange, 200, "application/json", mapper.writeValueAsBytes(wrong)));
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class,
                () -> client(Duration.ofSeconds(3)).publish(new PublishProcessReleaseCommand("v1", List.of(model)))).code());
        var other = new ProcessInstanceDTO("other", "invoice", "v1", "order", "trace", ProcessInstanceStatusEnum.ACTIVE, NOW, null);
        handler.set(exchange -> respond(exchange, 200, "application/json", mapper.writeValueAsBytes(other)));
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class,
                () -> client(Duration.ofSeconds(3)).get(new GetProcessInstanceQuery("expected"))).code());
        handler.set(exchange -> respond(exchange, 200, "text/html", mapper.writeValueAsBytes(other)));
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class,
                () -> client(Duration.ofSeconds(3)).get(new GetProcessInstanceQuery("other"))).code());
    }

    @Test
    void sends_writes_once_and_keeps_credentials_and_error_bodies_out_of_exceptions() {
        AtomicInteger calls = new AtomicInteger();
        handler.set(exchange -> {
            calls.incrementAndGet();
            exchange.getResponseHeaders().set("Retry-After", "9");
            respond(exchange, 409, "application/json", ("{\"code\":\"TIANJI_IDEMPOTENCY_CONFLICT\","
                    + "\"correlationId\":\"trace-123\",\"message\":\"private " + TOKEN + "\"}").getBytes(StandardCharsets.UTF_8));
        });
        var command = new EvaluateDecisionCommand("risk", "v1", "request", "trace", Map.of("amount", 10));
        ProcessClientException failure = assertThrows(ProcessClientException.class, () -> client(Duration.ofSeconds(3)).evaluate(command));
        assertEquals(1, calls.get());
        assertEquals(409, failure.httpStatus());
        assertEquals("TIANJI_IDEMPOTENCY_CONFLICT", failure.code());
        assertEquals("trace-123", failure.correlationId());
        assertEquals(Duration.ofSeconds(9), failure.retryAfter());
        assertFalse(failure.toString().contains(TOKEN));
        assertNull(failure.getCause());
        handler.set(exchange -> respond(exchange, 409, "application/json",
                ("{\"code\":\"" + TOKEN + "\",\"correlationId\":\"prefix-" + TOKEN + "\"}").getBytes(StandardCharsets.UTF_8)));
        ProcessClientException echoed = assertThrows(ProcessClientException.class, () -> client(Duration.ofSeconds(3)).evaluate(command));
        assertEquals("TIANJI_IDEMPOTENCY_CONFLICT", echoed.code());
        assertNull(echoed.correlationId());
        assertFalse(echoed.toString().contains(TOKEN));
    }

    @Test
    void bounds_responses_and_rejects_redirects_or_invalid_credentials() {
        handler.set(exchange -> respond(exchange, 200, "application/json", new byte[2 * 1_048_576 + 1]));
        ProcessClientException large = assertThrows(ProcessClientException.class,
                () -> client(Duration.ofSeconds(3)).get(new GetProcessReleaseQuery("v1")));
        assertEquals("TIANJI_RESPONSE_TOO_LARGE", large.code());
        assertThrows(IllegalArgumentException.class, () -> new HttpProcessClient(base,
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(), () -> TOKEN, Duration.ofSeconds(3)));
        var badToken = new HttpProcessClient(base, HttpClient.newHttpClient(), () -> "bad\nsecret", Duration.ofSeconds(3));
        assertThrows(IllegalArgumentException.class, () -> badToken.get(new GetProcessReleaseQuery("v1")));
        var throwing = new HttpProcessClient(base, HttpClient.newHttpClient(), () -> { throw new IllegalStateException(TOKEN); }, Duration.ofSeconds(3));
        ProcessClientException failure = assertThrows(ProcessClientException.class, () -> throwing.get(new GetProcessReleaseQuery("v1")));
        assertFalse(failure.toString().contains(TOKEN));
        assertNull(failure.getCause());
    }

    @Test
    void bounds_the_entire_response_time() {
        handler.set(exchange -> {
            try { Thread.sleep(500); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            respond(exchange, 200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        });
        long started = System.nanoTime();
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", assertThrows(ProcessClientException.class,
                () -> client(Duration.ofMillis(100)).get(new GetProcessReleaseQuery("v1"))).code());
        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(2)) < 0);
    }

    @Test
    void worker_calls_send_once_and_validate_topic_occurrence_and_outcome() throws Exception {
        String lease = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        var task = new WorkerTaskDTO("task-1", "occurrence-1", WorkerScopeTypeEnum.BPMN, "process-1", null, 20,
                "riskCheck", "1", WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, Map.of("amount", ProcessValueTypeEnum.INTEGER),
                Map.of("approved", ProcessValueTypeEnum.BOOLEAN), "order-1", "trace-1", "intent-1", NOW, NOW.plusSeconds(30), Map.of("amount", 10), lease);
        var success = new WorkerReceiptDTO("task-1", "occurrence-1", WorkerReceiptOutcomeEnum.COMPLETED, NOW);
        var manual = new WorkerReceiptDTO("task-1", "occurrence-1", WorkerReceiptOutcomeEnum.NEEDS_MANUAL, NOW);
        BlockingQueue<Seen> seen = new LinkedBlockingQueue<>();
        handler.set(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            seen.add(new Seen(exchange.getRequestMethod(), path, exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestBody().readAllBytes()));
            Object result = path.endsWith("acquire") ? new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.ACQUIRED, task)
                    : path.endsWith("complete") ? success : manual;
            respond(exchange, 200, "application/json", mapper.writeValueAsBytes(result));
        });
        var client = client(Duration.ofSeconds(3));
        var acquire = new AcquireWorkerTaskCommand("riskCheck", "1");
        var complete = new CompleteWorkerTaskCommand("task-1", "occurrence-1", lease, "receipt-1", Map.of("route", "review"));
        var fail = new FailWorkerTaskCommand("task-1", "occurrence-1", lease, WorkerFailureKindEnum.UNKNOWN, "OUTCOME_UNKNOWN");
        assertEquals(task, client.acquire(acquire).task());
        assertEquals(success, client.complete(complete));
        assertEquals(manual, client.fail(fail));
        List<Seen> requests = new ArrayList<>();
        seen.drainTo(requests);
        assertEquals(List.of("/api/tianji/v1/worker-tasks/acquire", "/api/tianji/v1/worker-tasks/complete",
                "/api/tianji/v1/worker-tasks/fail"), requests.stream().map(Seen::path).toList());
        assertEquals(complete, mapper.readValue(requests.get(1).body(), CompleteWorkerTaskCommand.class));
        assertEquals(fail, mapper.readValue(requests.get(2).body(), FailWorkerTaskCommand.class));

        handler.set(exchange -> respond(exchange, 200, "application/json", mapper.writeValueAsBytes(
                new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.ACQUIRED, task))));
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class,
                () -> client.acquire(new AcquireWorkerTaskCommand("otherTopic", "1"))).code());
        handler.set(exchange -> respond(exchange, 200, "application/json", "{}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.acquire(acquire)).code());
        handler.set(exchange -> respond(exchange, 200, "application/json", mapper.writeValueAsBytes(
                new WorkerReceiptDTO("task-1", "wrong-occurrence", WorkerReceiptOutcomeEnum.COMPLETED, NOW))));
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.complete(complete)).code());
        handler.set(exchange -> respond(exchange, 200, "application/json", mapper.writeValueAsBytes(
                new WorkerReceiptDTO("task-1", "occurrence-1", WorkerReceiptOutcomeEnum.RETRY_SCHEDULED, NOW))));
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.fail(fail)).code());
    }

    @Test
    void worker_errors_do_not_retry_or_expose_the_lease_capability() {
        String lease = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        AtomicInteger calls = new AtomicInteger();
        handler.set(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 409, "application/json", ("{\"code\":\"" + lease + "\",\"correlationId\":\"" + lease + "\"}")
                    .getBytes(StandardCharsets.UTF_8));
        });
        var command = new CompleteWorkerTaskCommand("task-1", "occurrence-1", lease, "receipt-1", Map.of());
        var failure = assertThrows(ProcessClientException.class, () -> client(Duration.ofSeconds(3)).complete(command));
        assertEquals(1, calls.get());
        assertEquals("TIANJI_IDEMPOTENCY_CONFLICT", failure.code());
        assertNull(failure.correlationId());
        assertFalse(failure.toString().contains(lease));
        assertNull(failure.getCause());
        handler.set(exchange -> respond(exchange, 409, "application/json",
                "{\"code\":\"TIANJI_WORKER_LEASE_LOST\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("TIANJI_WORKER_LEASE_LOST", assertThrows(ProcessClientException.class,
                () -> client(Duration.ofSeconds(3)).complete(command)).code());
    }

    @Test
    void human_task_queries_use_private_post_and_closed_bound_snapshot_pages() {
        var task = humanTask(); var encoded = mapper.writeValueAsString(task); var response = new AtomicReference<>(encoded); var calls = new AtomicInteger();
        handler.set(exchange -> {
            calls.incrementAndGet(); assertEquals("POST", exchange.getRequestMethod()); assertEquals("/api/tianji/v1/human-tasks/get", exchange.getRequestURI().getPath()); assertNull(exchange.getRequestURI().getRawQuery());
            assertEquals(new GetHumanTaskQuery("task-1"), mapper.readValue(exchange.getRequestBody().readAllBytes(), GetHumanTaskQuery.class)); respond(exchange, 200, "application/json", response.get().getBytes(StandardCharsets.UTF_8));
        });
        assertEquals(task, client(Duration.ofSeconds(3)).getHumanTask(new GetHumanTaskQuery("task-1")));
        for (var invalid : List.of(encoded.replace("task-1", "other"), encoded.replace("\"assigned\":false", "\"assigned\":\"false\""), encoded.replace("\"assignedToMe\":false", "\"assignedToMe\":true"),
                encoded.replace("\"version\":\"" + "a".repeat(64) + "\"", "\"version\":123"), encoded.replace("\"approvalField\":\"approved\"", "\"approvalField\":\"comment\""),
                encoded.substring(0, encoded.length() - 1) + ",\"candidateRoles\":[\"untrusted\"]}")) {
            response.set(invalid); int before = calls.get(); assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client(Duration.ofSeconds(3)).getHumanTask(new GetHumanTaskQuery("task-1"))).code()); assertEquals(before + 1, calls.get());
        }
        var query = new ListHumanTasksQuery(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.ALL, "release", "instance", "review", "000", 1);
        var page = new HumanTaskPageDTO(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.ALL, List.of(task), "z"); String pageJson = mapper.writeValueAsString(page); response.set(pageJson);
        handler.set(exchange -> {
            calls.incrementAndGet(); assertEquals("POST", exchange.getRequestMethod()); assertEquals("/api/tianji/v1/human-tasks/query", exchange.getRequestURI().getPath()); assertNull(exchange.getRequestURI().getRawQuery());
            assertEquals(query, mapper.readValue(exchange.getRequestBody().readAllBytes(), ListHumanTasksQuery.class)); respond(exchange, 200, "application/json", response.get().getBytes(StandardCharsets.UTF_8));
        });
        assertEquals(page, client(Duration.ofSeconds(3)).humanTasks(query));
        for (var invalid : List.of(pageJson.replace("\"releaseVersion\":\"release\"", "\"releaseVersion\":\"other\""), pageJson.replace("\"scopeId\":\"instance\"", "\"scopeId\":\"other\""),
                pageJson.replace("\"taskDefinitionKey\":\"review\"", "\"taskDefinitionKey\":\"other\""), pageJson.replace("\"nextAfterTaskId\":\"z\"", "\"nextAfterTaskId\":\"000\""), pageJson.replace(",\"nextAfterTaskId\":\"z\"", ""))) {
            response.set(invalid); assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client(Duration.ofSeconds(3)).humanTasks(query)).code());
        }
        var empty = new HumanTaskPageDTO(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.ALL, List.of(), "scanned"); response.set(mapper.writeValueAsString(empty));
        assertEquals(empty, client(Duration.ofSeconds(3)).humanTasks(query));
    }

    @Test
    void human_task_actions_match_original_command_version_action_and_transfer_lane_without_post_retry() {
        String version = "a".repeat(64); var claim = new ClaimHumanTaskCommand("claim-1", "task-1", version); var unclaim = new UnclaimHumanTaskCommand("unclaim-1", "task-1", version);
        var transfer = new TransferHumanTaskCommand("transfer-1", "task-1", version, "manager"); var complete = new CompleteHumanTaskCommand("complete-1", "task-1", version, Map.of("approved", false, "comment", "private form"));
        var response = new AtomicReference<String>(); var calls = new AtomicInteger(); var status = new AtomicInteger(200);
        handler.set(exchange -> {
            calls.incrementAndGet(); assertEquals("POST", exchange.getRequestMethod()); assertNull(exchange.getRequestURI().getRawQuery()); byte[] body = exchange.getRequestBody().readAllBytes();
            switch (exchange.getRequestURI().getPath()) {
                case "/api/tianji/v1/human-tasks/claim" -> assertEquals(claim, mapper.readValue(body, ClaimHumanTaskCommand.class));
                case "/api/tianji/v1/human-tasks/unclaim" -> assertEquals(unclaim, mapper.readValue(body, UnclaimHumanTaskCommand.class));
                case "/api/tianji/v1/human-tasks/transfer" -> assertEquals(transfer, mapper.readValue(body, TransferHumanTaskCommand.class));
                case "/api/tianji/v1/human-tasks/complete" -> assertEquals(complete, mapper.readValue(body, CompleteHumanTaskCommand.class));
                default -> fail("Unknown human task route");
            }
            respond(exchange, status.get(), "application/json", response.get().getBytes(StandardCharsets.UTF_8));
        });
        var client = client(Duration.ofSeconds(3));
        var claimed = humanReceipt(claim.commandId(), HumanTaskActionEnum.CLAIM, "finance"); response.set(mapper.writeValueAsString(claimed)); assertEquals(claimed, client.claimHumanTask(claim));
        var released = humanReceipt(unclaim.commandId(), HumanTaskActionEnum.UNCLAIM, "finance"); response.set(mapper.writeValueAsString(released)); assertEquals(released, client.unclaimHumanTask(unclaim));
        var transferred = humanReceipt(transfer.commandId(), HumanTaskActionEnum.TRANSFER, "manager"); response.set(mapper.writeValueAsString(transferred)); assertEquals(transferred, client.transferHumanTask(transfer));
        response.set(mapper.writeValueAsString(humanReceipt(transfer.commandId(), HumanTaskActionEnum.TRANSFER, "finance"))); assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.transferHumanTask(transfer)).code());
        var completed = humanReceipt(complete.commandId(), HumanTaskActionEnum.COMPLETE, "finance"); String encoded = mapper.writeValueAsString(completed); response.set(encoded); assertEquals(completed, client.completeHumanTask(complete)); assertEquals(Boolean.FALSE, completed.approved());
        for (var invalid : List.of(encoded.replace("complete-1", "other"), encoded.replace("task-1", "other"), encoded.replace(version, "c".repeat(64)),
                encoded.replace("\"approved\":false", "\"approved\":\"false\""), encoded.replace("\"kind\":\"APPROVAL\"", "\"kind\":\"ACTION\""), encoded.substring(0, encoded.length() - 1) + ",\"form\":{}}")) {
            response.set(invalid); int before = calls.get(); var failure = assertThrows(ProcessClientException.class, () -> client.completeHumanTask(complete)); assertEquals("TIANJI_INVALID_RESPONSE", failure.code()); assertFalse(failure.toString().contains("private form")); assertEquals(before + 1, calls.get());
        }
        response.set(encoded); status.set(202); assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.completeHumanTask(complete)).code());
        response.set("{}"); status.set(503); int before = calls.get(); assertThrows(ProcessClientException.class, () -> client.completeHumanTask(complete)); assertEquals(before + 1, calls.get());
        response.set("{\"code\":\"private form\",\"correlationId\":\"private form\"}"); status.set(409);
        var error = assertThrows(ProcessClientException.class, () -> client.completeHumanTask(complete)); assertEquals("TIANJI_IDEMPOTENCY_CONFLICT", error.code()); assertNull(error.correlationId()); assertFalse(error.toString().contains("private form"));
    }

    private static HumanTaskDTO humanTask() { return new HumanTaskDTO("task-1", HumanTaskScopeTypeEnum.PROCESS, "instance", "release", "invoice", "review", "Review", HumanTaskKindEnum.APPROVAL, "approved", "a".repeat(64), "finance", List.of("finance", "manager"), false, false, false, Map.of("approved", ProcessValueTypeEnum.BOOLEAN, "comment", ProcessValueTypeEnum.STRING), NOW, null); }
    private static HumanTaskActionReceiptDTO humanReceipt(String command, HumanTaskActionEnum action, String lane) { return new HumanTaskActionReceiptDTO(command, "task-1", action, HumanTaskScopeTypeEnum.PROCESS, "instance", "release", "invoice", "review", HumanTaskKindEnum.APPROVAL, lane, "a".repeat(64), action == HumanTaskActionEnum.COMPLETE ? null : "b".repeat(64), action == HumanTaskActionEnum.COMPLETE ? "approval-ref" : null, action == HumanTaskActionEnum.COMPLETE ? Boolean.FALSE : null, NOW); }

    @Test
    void human_task_transport_refuses_plain_remote_http_before_requesting_a_credential() {
        var credentials = new AtomicInteger();
        var unsafe = new HttpProcessClient(URI.create("http://process.example/"), HttpClient.newHttpClient(), () -> { credentials.incrementAndGet(); return TOKEN; }, Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> unsafe.getHumanTask(new GetHumanTaskQuery("task-1"))); assertEquals(0, credentials.get());
    }

    private HttpProcessClient client(Duration timeout) { return new HttpProcessClient(base, HttpClient.newHttpClient(), () -> TOKEN, timeout); }
    private static String sha(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    private static void respond(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
    private interface Handler { void handle(HttpExchange exchange) throws IOException; }
    private record Seen(String method, String path, String authorization, byte[] body) { }
}
