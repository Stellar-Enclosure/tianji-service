package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.CompleteEngineHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodeRetryCommand;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static top.egon.cola.platform.tianji.client.HttpProcessDraftClientTest.JSON;
import static top.egon.cola.platform.tianji.client.HttpProcessDraftClientTest.TOKEN;
import static top.egon.cola.platform.tianji.client.HttpProcessDraftClientTest.assertRequest;

class HttpProcessAutomationClientTest {
    static final String V2 = "/api/tianji/v2/";
    private static final String RELEASE = V2 + "process-releases/{releaseVersion}";
    private static final String START = V2 + "process-instances";
    private static final String INSTANCE = START + "/{instanceId}";
    private static final String NODES = INSTANCE + "/node-executions";
    private static final String RETRY = INSTANCE + "/node-retries";
    private static final String TASK = V2 + "process-tasks/{taskId}";
    private static final String COMPLETE = TASK + "/completions";
    private static final JsonNode OAS = loadOas();
    private HttpProcessDraftClientTest.Fixture fixture;
    private HttpProcessAutomationClient client;
    private HttpClient http;

    @BeforeEach void start() throws IOException {
        fixture = new HttpProcessDraftClientTest.Fixture();
        http = HttpClient.newHttpClient();
        client = new HttpProcessAutomationClient(fixture.base, http, () -> TOKEN, Duration.ofSeconds(5), 11L);
    }

    @AfterEach void stop() {
        client.close();
        http.close();
        fixture.close();
    }

    @Test void fixed_routes_use_the_public_automation_shapes_and_statuses() {
        var start = startCommand();
        var complete = request(COMPLETE, "post", CompleteEngineHumanTaskCommand.class);
        var retry = request(RETRY, "post", NodeRetryCommand.class);
        fixture.handler = request -> switch (request.method() + " " + request.path()) {
            case "GET /api/tianji/v2/process-releases/auto-1" -> response(RELEASE, "get", 200);
            case "POST /api/tianji/v2/process-instances" -> response(START, "post", 200);
            case "GET /api/tianji/v2/process-instances/pi-101" -> response(INSTANCE, "get", 200);
            case "GET /api/tianji/v2/process-instances/pi-101/node-executions" -> response(NODES, "get", 200);
            case "GET /api/tianji/v2/process-tasks/task-101" -> response(TASK, "get", 200);
            case "POST /api/tianji/v2/process-tasks/task-101/completions" -> response(COMPLETE, "post", 200);
            case "POST /api/tianji/v2/process-instances/pi-101/node-retries" -> response(RETRY, "post", 202);
            default -> new HttpProcessDraftClientTest.Reply(404, "application/json", "{}");
        };
        assertEquals("auto-1", client.getRelease("auto-1").getReleaseVersion());
        assertEquals(2, client.start(start).getActivationRevision());
        assertEquals("auto-2", client.getInstance("pi-101").getReleaseVersion());
        assertEquals("extract", client.listNodes("pi-101", "extract", "cursor_1", 10).getItems().getFirst().getNodeId());
        assertEquals("task-101", client.getTask("task-101").getTaskId());
        assertEquals("COMPLETED", client.completeTask("task-101", complete).getState().name());
        assertEquals("SCHEDULED", client.retryNode("pi-101", retry).getState());

        assertRequest(fixture.take(), "GET", V2 + "process-releases/auto-1", null, null);
        var sentStart = fixture.take();
        assertRequest(sentStart, "POST", START, AutomationStartCommand.class, start);
        assertFalse(JSON.readTree(sentStart.body()).has("releaseVersion"));
        assertRequest(fixture.take(), "GET", START + "/pi-101", null, null);
        var nodes = fixture.take();
        assertRequest(nodes, "GET", START + "/pi-101/node-executions", null, null);
        assertTrue(List.of(nodes.query().split("&")).containsAll(List.of("nodeId=extract", "limit=10", "cursor=cursor_1")));
        assertRequest(fixture.take(), "GET", V2 + "process-tasks/task-101", null, null);
        assertRequest(fixture.take(), "POST", V2 + "process-tasks/task-101/completions", CompleteEngineHumanTaskCommand.class, complete);
        assertRequest(fixture.take(), "POST", START + "/pi-101/node-retries", NodeRetryCommand.class, retry);
        assertNull(fixture.poll());
    }

    @Test void rejects_foreign_resources_non_integral_attempt_and_invalid_nullable_projection() {
        String nodes = response(NODES, "get", 200).body();
        for (String invalid : List.of(nodes.replace("pi-101", "pi-102"), nodes.replace("extract", "different"),
                nodes.replace("\"attempt\":1", "\"attempt\":1.0"), nodes.replace(",\"nextCursor\":null", ""),
                nodes.replace("\"output\":null", "\"output\":{\"amount\":1.5}"))) {
            fixture.handler = request -> HttpProcessDraftClientTest.Reply.json(invalid);
            assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class,
                    () -> client.listNodes("pi-101", "extract", null, null)).code());
            fixture.take();
        }
        fixture.handler = request -> response(INSTANCE, "get", 200);
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class,
                () -> client.getInstance("foreign")).code());
        fixture.take();
        fixture.handler = request -> response(RETRY, "post", 202);
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class,
                () -> client.retryNode("pi-other", request(RETRY, "post", NodeRetryCommand.class))).code());
        fixture.take();
        assertNull(fixture.poll());
    }

    @Test void invalid_start_input_is_rejected_before_wire_and_response_loss_never_reissues_start() {
        var invalid = startCommand().setVariables(Map.of("amount", 9_007_199_254_740_993L));
        assertThrows(IllegalArgumentException.class, () -> client.start(invalid));
        assertThrows(IllegalArgumentException.class, () -> client.start(startCommand().setCommandId("evt-v2-forbidden")));
        assertThrows(IllegalArgumentException.class, () -> client.start(startCommand().setAgentTaskOverrides(null)));
        assertNull(fixture.poll());
        fixture.handler = request -> null;
        var original = startCommand();
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", assertThrows(ProcessClientException.class, () -> client.start(original)).code());
        assertRequest(fixture.take(), "POST", START, AutomationStartCommand.class, original);
        assertNull(fixture.poll());
    }

    @Test void reads_large_release_source_and_enforces_the_success_status_and_resource_echo() {
        var example = response(RELEASE, "get", 200);
        var json = JSON.readTree(example.body());
        var root = (tools.jackson.databind.node.ObjectNode) json.path("artifact").path("models").get(0);
        String largeXml = "<!--" + "界".repeat(1_050_000) + "-->";
        root.put("xml", largeXml);
        fixture.handler = request -> HttpProcessDraftClientTest.Reply.json(JSON.writeValueAsString(json));
        assertEquals(largeXml, client.getRelease("auto-1").getArtifact().models().getFirst().xml());
        fixture.take();
        fixture.handler = request -> example;
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.getRelease("auto-other")).code());
        fixture.take();
        fixture.handler = request -> new HttpProcessDraftClientTest.Reply(202, "application/json", response(START, "post", 200).body());
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.start(startCommand())).code());
        fixture.take();
        assertNull(fixture.poll());
    }

    static AutomationStartCommand startCommand() {
        return request(START, "post", AutomationStartCommand.class);
    }

    static AutomationStartCommand startGolden(String amount, long version) {
        var variables = new LinkedHashMap<String, Object>();
        variables.put("10", "十");
        variables.put("2", "二");
        variables.put("amount", amount);
        variables.put("é", "\n");
        variables.put("\uD800\uDC00", true);
        variables.put("\uE000", null);
        var template = new top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO.TemplateRefVO(
                "template", version, "a".repeat(64));
        return startCommand().setVariables(variables).setAgentTaskOverrides(Map.of("agentNode", new AutomationStartCommand.AgentTaskOverrideVO(template, null)));
    }

    static <T> T request(String path, String method, Class<T> type) {
        return JSON.treeToValue(OAS.path("paths").path(path).path(method).path("requestBody").path("content").path("application/json").path("example"), type);
    }

    static HttpProcessDraftClientTest.Reply response(String path, String method, int status) {
        JsonNode example = OAS.path("paths").path(path).path(method).path("responses").path(Integer.toString(status))
                .path("content").path("application/json").path("example");
        assertTrue(example.isObject());
        return new HttpProcessDraftClientTest.Reply(status, "application/json", JSON.writeValueAsString(example));
    }

    private static JsonNode loadOas() {
        try (var resource = HttpProcessAutomationClientTest.class.getResourceAsStream("/META-INF/tianji/openapi/automation-v2.json")) {
            return JSON.readTree(resource);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
