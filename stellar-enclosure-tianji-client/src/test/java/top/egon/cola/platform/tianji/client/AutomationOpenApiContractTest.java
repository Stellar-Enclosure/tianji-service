package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationOpenApiContractTest {

    private static final String PREFIX = "/api/tianji/v2";
    private static final String RESOURCE_ROOT = "META-INF/tianji/openapi/";
    private static final String SOURCE_RESOURCES = "src/main/resources/" + RESOURCE_ROOT;
    private static final String PROCESS_MODULE = "stellar-enclosure-tianji-process-server";
    private static final String CLIENT_MODULE = "stellar-enclosure-tianji-client";
    private static final String AUTOMATION = "automation-v2.json";
    private static final String RECEIPT = "process-start-receipt-v1.json";
    private static final long MAX_EXACT_INTEGER = 999_999_999_999_999L;

    private static final List<OperationVO> OPERATIONS = List.of(
            new OperationVO("post", "/process-model-validations", "validateProcessModel", 200),
            new OperationVO("post", "/process-releases", "publishAutomationRelease", 200),
            new OperationVO("get", "/process-releases/{releaseVersion}", "getAutomationRelease", 200),
            new OperationVO("post", "/process-instances", "startAutomationProcess", 200),
            new OperationVO("get", "/process-instances/{instanceId}", "getAutomationInstance", 200),
            new OperationVO("get", "/process-instances/{instanceId}/node-executions", "listProcessNodeExecutions", 200),
            new OperationVO("post", "/process-events", "receiveProcessEvent", 202),
            new OperationVO("get", "/process-event-receipts/{receiptId}", "getProcessEventReceipt", 200),
            new OperationVO("get", "/process-tasks/{taskId}", "getEngineHumanTask", 200),
            new OperationVO("post", "/process-tasks/{taskId}/completions", "completeEngineHumanTask", 200),
            new OperationVO("post", "/process-instances/{instanceId}/node-retries", "retryProcessNode", 202),
            new OperationVO("post", "/worker-tasks/acquire", "acquireAutomationWorkerTask", 200),
            new OperationVO("post", "/worker-tasks/complete", "completeAutomationWorkerTask", 200),
            new OperationVO("post", "/worker-tasks/fail", "failAutomationWorkerTask", 200),
            new OperationVO("post", "/process-definitions/{definitionKey}/drafts", "createProcessDraft", 200),
            new OperationVO("get", "/process-drafts/{draftId}", "getProcessDraft", 200),
            new OperationVO("put", "/process-drafts/{draftId}", "saveProcessDraft", 200),
            new OperationVO("get", "/process-drafts", "listProcessDrafts", 200),
            new OperationVO("post", "/process-drafts/{draftId}/discard-commands", "discardProcessDraft", 200),
            new OperationVO("post", "/process-definitions/{definitionKey}/activations", "activateAutomationRelease", 200),
            new OperationVO("get", "/process-definitions/{definitionKey}/activations", "listAutomationActivations", 200));

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void publishesExactlyThePublicOperationsWithExactSuccessStatusesAndNoStore() throws IOException {
        JsonNode document = mapper.readTree(artifact());
        assertEquals("3.1.0", document.path("openapi").asText());
        assertEquals("https://json-schema.org/draft/2020-12/schema", document.path("jsonSchemaDialect").asText());
        JsonNode paths = document.path("paths");
        assertEquals(Set.copyOf(OPERATIONS.stream().map(op -> PREFIX + op.path()).toList()), names(paths));
        for (OperationVO expected : OPERATIONS) {
            String path = PREFIX + expected.path();
            var methods = OPERATIONS.stream().filter(value -> value.path().equals(expected.path()))
                    .map(OperationVO::method).toList();
            assertEquals(Set.copyOf(methods), names(paths.path(path)), path);
            JsonNode operation = paths.path(path).path(expected.method());
            assertEquals(expected.id(), operation.path("operationId").asText());
            JsonNode responses = operation.path("responses");
            String success = Integer.toString(expected.status());
            assertEquals(Set.of(success), Set.copyOf(names(responses).stream()
                    .filter(status -> status.startsWith("2")).toList()), expected.id());
            assertTrue(responses.path(success).path("content").path("application/json").path("schema").isObject());
            assertTrue(operation.path("security").isArray() && !operation.path("security").isEmpty(), expected.id());
            for (var response : responses.properties()) {
                JsonNode resolved = resolve(document, response.getValue());
                if ("415".equals(response.getKey())) {
                    assertFalse(resolved.has("content"), "Existing MVC media-type rejection has no typed response promise");
                    continue;
                }
                assertEquals("no-store", resolved.path("headers").path("Cache-Control")
                        .path("schema").path("const").asText(), expected.id() + " " + response.getKey());
            }
        }
        verifyLocalReferences(document, document);
    }

    @Test
    void distinguishesStartObservationActivationAndServiceAuthorization() throws IOException {
        JsonNode doc = mapper.readTree(artifact());
        assertEquals(Set.of(Set.of("tianji:process:start")), oauth(operation(doc, "startAutomationProcess")));
        Set<Set<String>> observation = Set.of(Set.of("tianji:process:read"), Set.of("tianji:process:observe"));
        assertEquals(observation, oauth(operation(doc, "getAutomationInstance")));
        assertEquals(observation, oauth(operation(doc, "listProcessNodeExecutions")));
        assertEquals(Set.of(Set.of("tianji:process:release:read", "tianji:process:activate")),
                oauth(operation(doc, "activateAutomationRelease")));
        assertEquals(Set.of("USER"), values(operation(doc, "activateAutomationRelease").path("x-allowed-principals")));
        for (String id : List.of("receiveProcessEvent", "acquireAutomationWorkerTask",
                "completeAutomationWorkerTask", "failAutomationWorkerTask")) {
            assertEquals(Set.of("SERVICE"), values(operation(doc, id).path("x-allowed-principals")), id);
        }
    }

    @Test
    void freezesTypedStartClosureActivationRangesAndBindingsMerge() throws IOException {
        JsonNode doc = mapper.readTree(artifact());
        JsonNode start = schema(doc, "AutomationStartCommand");
        assertClosed(start);
        assertEquals(Set.of("definitionKey", "businessKey", "commandId", "correlationId", "variables"),
                values(start.path("required")));
        assertEquals(Set.of("definitionKey", "businessKey", "commandId", "correlationId", "variables",
                "agentTaskOverrides"), names(start.path("properties")));
        assertFalse(start.path("properties").has("releaseVersion"));
        assertFalse(start.path("properties").has("activationRevision"));
        assertEquals(MAX_EXACT_INTEGER, resolve(doc, schema(doc, "AutomationInstanceDTO").path("properties")
                .path("activationRevision")).path("maximum").asLong());
        JsonNode activation = schema(doc, "ActivationCommand").path("properties")
                .path("expectedActivationRevision");
        assertEquals(0, resolve(doc, activation).path("minimum").asLong(-1));
        assertEquals(MAX_EXACT_INTEGER, resolve(doc, activation).path("maximum").asLong());

        JsonNode binding = schema(doc, "AutomationNodeBindingVO");
        assertClosed(binding);
        assertTrue(binding.path("properties").has("merge"));
        assertFalse(schema(doc, "MultiInstanceControlVO").path("properties").has("merge"));
        JsonNode mergeRule = null;
        for (JsonNode rule : binding.path("allOf")) {
            if (values(rule.path("if").path("properties").path("kind").path("enum"))
                    .equals(Set.of("AND_JOIN", "MULTI_INSTANCE"))) {
                mergeRule = rule;
            }
        }
        assertNotNull(mergeRule, "AND_JOIN and MULTI_INSTANCE must share the top-level merge rule");
        assertTrue(values(mergeRule.path("then").path("required")).contains("merge"));
        assertTrue(values(mergeRule.path("else").path("not").path("required")).contains("merge"));
    }

    @Test
    void typedEventWaitTokensAndWorkerOutcomesPreserveNullability() throws IOException {
        JsonNode doc = mapper.readTree(artifact());
        JsonNode event = schema(doc, "ProcessEventCommand");
        assertClosed(event);
        assertTrue(values(event.path("required")).contains("waitToken"));
        JsonNode token = event.path("properties").path("waitToken").path("oneOf");
        assertEquals(2, token.size());
        JsonNode encoded = resolve(doc, token.get(0));
        assertEquals("string", encoded.path("type").asText());
        assertEquals(43, encoded.path("minLength").asInt());
        assertEquals(43, encoded.path("maxLength").asInt());
        assertEquals("null", token.get(1).path("type").asText());
        JsonNode acquire = schema(doc, "AutomationWorkerAcquireDTO");
        assertTrue(values(acquire.path("required")).contains("task"));
        assertEquals(2, acquire.path("oneOf").size());
        assertEquals("null", acquire.path("oneOf").get(1).path("properties").path("task").path("type").asText());
        JsonNode completed = responseSchema(doc, "completeAutomationWorkerTask").path("allOf").get(1);
        assertEquals("COMPLETED", completed.path("properties").path("outcome").path("const").asText());
        JsonNode failed = responseSchema(doc, "failAutomationWorkerTask").path("allOf").get(1);
        assertEquals(Set.of("UNKNOWN", "PERMANENT", "RETRY_SCHEDULED", "NEEDS_MANUAL"),
                values(failed.path("properties").path("outcome").path("enum")));
        assertEquals("#/components/schemas/DraftSummaryDTO", operation(doc, "createProcessDraft")
                .path("responses").path("200").path("content").path("application/json")
                .path("schema").path("$ref").asText());
    }

    @Test
    void processServerAndClientPublishIdenticalBytes() throws IOException {
        Path repo = repository();
        byte[] clientSource = Files.readAllBytes(repo.resolve(CLIENT_MODULE).resolve(SOURCE_RESOURCES).resolve(AUTOMATION));
        assertArrayEquals(clientSource, Files.readAllBytes(repo.resolve(PROCESS_MODULE).resolve(SOURCE_RESOURCES).resolve(AUTOMATION)));
        assertArrayEquals(clientSource, artifact());
    }

    @Test
    void retainsTheV1StartReceiptWireAndDocumentsRawV2DigestRecovery() {
        JsonNode doc = mapper.readTree(repository().resolve(PROCESS_MODULE).resolve(SOURCE_RESOURCES).resolve(RECEIPT).toFile());
        JsonNode paths = doc.path("paths");
        String route = "/api/tianji/v1/process-start-receipts";
        assertEquals(Set.of(route), names(paths));
        assertEquals(Set.of("get"), names(paths.path(route)));
        JsonNode get = paths.path(route).path("get");
        assertEquals("getOwnedProcessStartReceipt", get.path("operationId").asText());
        assertEquals(Set.of(Set.of("tianji:process:read")), oauth(get));
        var queries = new HashSet<String>();
        get.path("parameters").forEach(param -> {
            assertEquals("query", param.path("in").asText());
            assertTrue(param.path("required").asBoolean());
            queries.add(param.path("name").asText());
        });
        assertEquals(Set.of("commandId", "requestSha256"), queries);
        JsonNode receipt = schema(doc, "StartReceipt");
        assertClosed(receipt);
        assertEquals(Set.of("instanceId", "status", "recordedAt"), values(receipt.path("required")));
        assertEquals("APPLIED", receipt.path("properties").path("status").path("const").asText());
        assertEquals(Set.of("200", "400", "401", "403", "404", "409", "503"), names(get.path("responses")));
        String description = get.path("parameters").get(1).path("description").asText();
        assertTrue(description.contains("AUTOMATION_START_REQUEST_V2"), description);
        assertTrue(description.contains("before schema normalization"), description);
        assertTrue(get.path("responses").path("404").path("description").asText().contains("inconclusive"));
    }

    private byte[] artifact() throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(RESOURCE_ROOT + AUTOMATION)) {
            assertNotNull(stream, "Missing public " + AUTOMATION + " classpath artifact");
            return stream.readAllBytes();
        }
    }

    private static Path repository() {
        for (Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isDirectory(path.resolve(PROCESS_MODULE)) && Files.isDirectory(path.resolve(CLIENT_MODULE))) {
                return path;
            }
        }
        throw new AssertionError("Tianji source reactor not found from module working directory");
    }

    private static JsonNode operation(JsonNode document, String id) {
        OperationVO expected = OPERATIONS.stream().filter(op -> op.id().equals(id)).findFirst().orElseThrow();
        return document.path("paths").path(PREFIX + expected.path()).path(expected.method());
    }

    private static JsonNode schema(JsonNode document, String name) {
        JsonNode schema = document.path("components").path("schemas").path(name);
        assertTrue(schema.isObject(), name);
        return schema;
    }

    private static JsonNode responseSchema(JsonNode document, String operationId) {
        JsonNode media = operation(document, operationId).path("responses").path("200").path("content")
                .path("application/json");
        assertTrue(media.path("example").isObject(), operationId);
        return resolve(document, media.path("schema"));
    }

    private static void assertClosed(JsonNode schema) {
        assertTrue(schema.path("additionalProperties").isBoolean() && !schema.path("additionalProperties").asBoolean());
    }

    private static JsonNode resolve(JsonNode document, JsonNode object) {
        if (!object.has("$ref")) {
            return object;
        }
        String ref = object.path("$ref").asText();
        assertTrue(ref.startsWith("#/"), ref);
        JsonNode result = document.at(ref.substring(1));
        assertFalse(result.isMissingNode(), ref);
        return result;
    }

    private static void verifyLocalReferences(JsonNode document, JsonNode node) {
        resolve(document, node);
        node.forEach(child -> verifyLocalReferences(document, child));
    }

    private static Set<Set<String>> oauth(JsonNode operation) {
        Set<Set<String>> alternatives = new HashSet<>();
        operation.path("security").forEach(requirement -> alternatives.add(values(requirement.path("oauth2"))));
        return Set.copyOf(alternatives);
    }

    private static Set<String> names(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        node.propertyNames().forEach(result::add);
        return Set.copyOf(result);
    }

    private static Set<String> values(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        node.forEach(value -> result.add(value.asText()));
        return Set.copyOf(result);
    }

    private record OperationVO(String method, String path, String id, int status) {
    }
}
