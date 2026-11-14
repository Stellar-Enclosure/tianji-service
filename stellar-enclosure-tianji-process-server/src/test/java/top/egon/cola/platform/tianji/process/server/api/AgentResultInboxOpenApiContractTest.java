package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultClient;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentResultArtifactRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2.SideEffectOutcomeEnum;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResultInboxOpenApiContractTest {

    private static final String RESOURCE = "/META-INF/tianji/openapi/agent-result-inbox.json";
    private static final String PATH = "/api/tianji/v1/agent-result-inbox";
    private static final String REQUEST = "application/json";
    private static final String SHA = "a".repeat(64);
    private static final Instant FINISHED = Instant.parse("2026-11-11T11:00:20.123456Z");
    private static final AgentProcessLink LINK = new AgentProcessLink("ps-main", WorkerScopeTypeEnum.BPMN,
            "pi-101", "job-101", SHA, FINISHED.plusSeconds(10));
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void documentsOnlyTheOriginalPrivateServiceRouteAndReceipt() throws Exception {
        JsonNode root = document();
        JsonNode operation = root.path("paths").path(PATH).path("post");
        assertEquals("3.1.0", root.path("openapi").asString());
        assertEquals(Set.of(PATH), Set.copyOf(root.path("paths").propertyNames()));
        assertEquals(Set.of("post"), Set.copyOf(root.path("paths").path(PATH).propertyNames()));
        assertEquals("receiveAgentTerminalEvent", operation.path("operationId").asString());
        assertEquals(Set.of("SERVICE"), strings(operation.path("x-allowed-principals")));
        assertEquals(Set.of("TIANJI_AGENT_RESULT_RECEIVE"), strings(operation.path("x-required-permissions")));
        assertEquals("tianji.api.process.agent.result.receive", operation.path("x-resource-key").asString());
        assertEquals(1, operation.path("security").size());
        assertEquals(Set.of(AgentResultClient.RECEIVE_SCOPE), strings(operation.path("security").path(0).path("oauth2")));
        var flows = root.path("components").path("securitySchemes").path("oauth2").path("flows");
        assertEquals(Set.of("clientCredentials"), Set.copyOf(flows.propertyNames()));
        assertEquals(Set.of(AgentResultClient.RECEIVE_SCOPE),
                Set.copyOf(flows.path("clientCredentials").path("scopes").propertyNames()));

        assertTrue(operation.path("requestBody").path("required").asBoolean());
        assertEquals("#/components/schemas/TerminalEnvelope", operation.path("requestBody").path("content")
                .path(REQUEST).path("schema").path("$ref").asString());
        var responses = operation.path("responses");
        assertEquals(Set.of("202", "400", "401", "403", "404", "409", "413", "415", "429", "500", "503"),
                Set.copyOf(responses.propertyNames()));
        assertEquals("no-store", responses.path("202").path("headers").path("Cache-Control")
                .path("schema").path("const").asString());
        assertEquals(Set.of("code"), strings(schema(root, "SecurityError").path("required")));
        for (String status : Set.of("401", "403")) {
            var alternatives = responses.path(status).path("content").path(REQUEST).path("schema").path("oneOf");
            assertEquals(Set.of("#/components/schemas/SecurityError", "#/components/schemas/Error"),
                    alternatives.valueStream().map(branch -> branch.path("$ref").asString()).collect(Collectors.toSet()));
        }
        assertTrue(responses.path("415").path("content").isMissingNode());
        assertEquals("#/components/schemas/AgentResultReceipt", responses.path("202").path("content").path(REQUEST)
                .path("schema").path("$ref").asString());
        var receipt = schema(root, "AgentResultReceipt");
        assertEquals(recordFields(AgentResultReceipt.class), strings(receipt.path("required")));
        assertEquals(recordFields(AgentResultReceipt.class), Set.copyOf(receipt.path("properties").propertyNames()));
        assertEquals(Arrays.stream(AgentResultReceiptStateEnum.values()).map(Enum::name).collect(Collectors.toSet()),
                strings(receipt.path("properties").path("state").path("enum")));
        assertEquals(Set.of("code", "correlationId"), strings(schema(root, "Error").path("required")));
    }

    @Test
    void freezesVersionDispatchMemberPresenceAndExactV2Limits() throws Exception {
        var root = document();
        var union = schema(root, "TerminalEnvelope").path("oneOf");
        assertEquals(2, union.size());
        assertEquals(Set.of("#/components/schemas/AgentTerminalEventV1", "#/components/schemas/AgentTerminalEventV2"),
                Set.of(union.path(0).path("$ref").asString(), union.path(1).path("$ref").asString()));
        var v1 = schema(root, "AgentTerminalEventV1");
        var v2 = schema(root, "AgentTerminalEventV2");
        Set<String> legacyRequired = new HashSet<>(recordFields(AgentTerminalEvent.class));
        legacyRequired.remove("errorCode");
        assertEquals(legacyRequired, strings(v1.path("required")));
        assertEquals(recordFields(AgentTerminalEvent.class), Set.copyOf(v1.path("properties").propertyNames()));
        assertEquals(recordFields(AgentTerminalEventV2.class), strings(v2.path("required")));
        assertEquals(recordFields(AgentTerminalEventV2.class), Set.copyOf(v2.path("properties").propertyNames()));
        assertEquals(1, v1.path("properties").path("schemaVersion").path("const").asInt());
        assertEquals(2, v2.path("properties").path("schemaVersion").path("const").asInt());
        for (String name : Set.of("definitionVersion", "terminalRevision")) {
            assertEquals(Long.MAX_VALUE, v1.path("properties").path(name).path("maximum").asLong());
            assertEquals(999_999_999_999_999L, v2.path("properties").path(name).path("maximum").asLong());
            assertEquals(1, v2.path("properties").path(name).path("minimum").asInt());
        }
        assertEquals(Set.of("string", "null"), strings(v1.path("properties").path("errorCode").path("type")));
        assertEquals(Set.of("object", "null"), strings(v2.path("properties").path("output").path("type")));
        assertEquals(Set.of("string", "null"), strings(v2.path("properties").path("errorCode").path("type")));
        assertEquals(2, v2.path("properties").path("artifactRef").path("oneOf").size());
        assertEquals(65_536, v1.path("x-max-request-bytes").asInt());
        assertEquals(16_384, v2.path("x-max-request-bytes").asInt());
        assertEquals(8_192, v2.path("properties").path("output").path("x-max-canonical-bytes").asInt());
    }

    @Test
    void fixturesConformToThePublishedUnionAndBindTheReceiptToTheOriginalFingerprint() throws Exception {
        JsonNode root = document();
        var operation = root.path("paths").path(PATH).path("post");
        var examples = operation.path("requestBody").path("content").path(REQUEST).path("examples");
        assertEquals(Set.of("legacy", "inline", "reference", "failure"), Set.copyOf(examples.propertyNames()));
        var union = schema(root, "TerminalEnvelope");
        for (var fixture : examples.properties()) {
            assertTrue(accepts(root, union, fixture.getValue().path("value")), fixture.getKey());
        }
        var inline = examples.path("inline").path("value");
        var old = examples.path("legacy").path("value");
        assertEquals(AgentTerminalEvent.eventIdFor("as-main", 11, SHA, "ps-agent-worker", "run-101", 7),
                old.path("eventId").asString());
        assertEquals(AgentTerminalEventV2.eventIdFor("as-main", 11, SHA, "ps-agent-worker", "run-101", 7),
                inline.path("eventId").asString());

        var receipt = operation.path("responses").path("202").path("content").path(REQUEST).path("example");
        assertTrue(accepts(root, schema(root, "AgentResultReceipt"), receipt));
        assertEquals(inline.path("eventId"), receipt.path("eventId"));
        var output = Map.<String, Object>of("category", "A");
        var effect = SideEffectOutcomeEnum.NONE;
        var finished = Instant.parse("2026-11-11T11:00:20Z");
        var link = new AgentProcessLink("ps-main", WorkerScopeTypeEnum.BPMN, "pi-101", "job-101", SHA,
                finished.plusSeconds(10));
        var terminal = new AgentTerminalEventV2(2, "as-main", inline.path("eventId").asString(),
                11, SHA, "ps-agent-worker", "dispatch-101", link, "extract", 1, "run-101", 7,
                "nx-101", SHA, SHA, SHA, AgentRunStatusEnum.COMPLETED, finished,
                inline.path("resultSha256").asString(), output, null, null, effect);
        assertEquals(terminal.fingerprint(), receipt.path("eventFingerprint").asString());
    }

    @Test
    void staticSchemaAcceptsThreeV2ResultShapesAndRejectsMixedOrUnknownEnvelopes() throws Exception {
        var root = document();
        var artifact = new AgentResultArtifactRefVO("result-1", SHA, "application/json", 42);
        ObjectNode inline = fixture(AgentRunStatusEnum.COMPLETED,
                Map.of("matched", java.util.List.of(true, 17, Map.of("type", "rule"))), null, null);
        ObjectNode reference = fixture(AgentRunStatusEnum.COMPLETED, null, artifact, null);
        ObjectNode failed = fixture(AgentRunStatusEnum.FAILED, null, null, "INVALID_OUTPUT");
        JsonNode schema = schema(root, "TerminalEnvelope");
        for (var accepted : Set.of(inline, reference, failed)) {
            assertTrue(accepts(root, schema, accepted), accepted::toString);
        }

        var both = inline.deepCopy().set("artifactRef", reference.path("artifactRef"));
        var neither = inline.deepCopy().putNull("output");
        var invalidArtifact = reference.deepCopy();
        ((ObjectNode) invalidArtifact.path("artifactRef")).put("resourceKey", "https://example.invalid/artifact");
        var invalidMedia = reference.deepCopy();
        ((ObjectNode) invalidMedia.path("artifactRef")).put("mediaType", "application/json; charset=utf-8");
        var extraArtifact = reference.deepCopy();
        ((ObjectNode) extraArtifact.path("artifactRef")).put("url", "https://example.invalid/artifact");
        var wrongScope = inline.deepCopy();
        ((ObjectNode) wrongScope.path("link")).put("scopeType", "CMMN");
        var missingNull = inline.deepCopy();
        missingNull.remove("artifactRef");
        var floatingOutput = inline.deepCopy().set("output", mapper.readTree("{\"score\":1.25}"));
        for (var rejected : Set.of(both, neither, invalidArtifact, invalidMedia, extraArtifact, wrongScope, missingNull,
                floatingOutput,
                failed.deepCopy().putNull("errorCode"), failed.deepCopy().put("errorCode", "not-stable"),
                inline.deepCopy().put("status", "RUNNING"), inline.deepCopy().put("schemaVersion", 3),
                inline.deepCopy().put("schemaVersion", 1), inline.deepCopy().put("finishedAt", "2026-11-11T11:00:20+00:00"))) {
            assertFalse(accepts(root, schema, rejected), rejected::toString);
        }
    }

    @Test
    void legacyShapeRemainsOpenToItsHistoricalErrorCodesAndLongVersions() throws Exception {
        var root = document();
        var legacy = new AgentTerminalEvent(1, "as-main",
                AgentTerminalEvent.eventIdFor("as-main", 11, SHA, "dispatch-owner", "run-101", Long.MAX_VALUE),
                11, SHA, "dispatch-owner", "dispatch-101", LINK, "extract", Long.MAX_VALUE, "run-101", Long.MAX_VALUE,
                AgentRunStatusEnum.FAILED, FINISHED, SHA, null, "HISTORICAL_TECHNICAL_CODE");
        var wire = (ObjectNode) mapper.valueToTree(legacy);
        var envelope = schema(root, "TerminalEnvelope");
        assertTrue(accepts(root, envelope, wire));
        wire.remove("errorCode");
        assertTrue(accepts(root, envelope, wire));
        wire.putNull("output");
        assertFalse(accepts(root, envelope, wire));
    }

    private ObjectNode fixture(AgentRunStatusEnum status, Map<String, Object> output,
                               AgentResultArtifactRefVO artifact, String error) {
        var effect = SideEffectOutcomeEnum.NONE;
        return mapper.valueToTree(new AgentTerminalEventV2(2, "as-main",
                AgentTerminalEventV2.eventIdFor("as-main", 11, SHA, "dispatch-owner", "run-101", 7),
                11, SHA, "dispatch-owner", "dispatch-101", LINK, "extract", 1, "run-101", 7,
                "nx-101", SHA, SHA, SHA, status, FINISHED,
                AgentTerminalEventV2.resultSha256(status, output, artifact, error, effect), output, artifact, error, effect));
    }

    private JsonNode document() throws Exception {
        try (var input = getClass().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, "The Process Server must package the private Inbox OpenAPI contract");
            return mapper.readTree(input);
        }
    }

    private static JsonNode schema(JsonNode root, String name) {
        return root.path("components").path("schemas").path(name);
    }

    private static Set<String> strings(JsonNode array) {
        var result = new HashSet<String>();
        array.forEach(value -> assertTrue(result.add(value.asString()), "Duplicate schema value"));
        return result;
    }

    private static Set<String> recordFields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(component -> component.getName()).collect(Collectors.toSet());
    }

    // Evaluates only the closed structural keywords used in the Inbox envelope fixtures.
    private static boolean accepts(JsonNode document, JsonNode shape, JsonNode value) {
        assertFalse(shape.isMissingNode(), "Unresolved Inbox schema");
        if (shape.has("$ref") && !accepts(document, document.at(shape.path("$ref").asString().substring(1)), value)) return false;
        if (shape.has("oneOf")) {
            int matching = 0;
            for (var branch : shape.path("oneOf")) {
                if (accepts(document, branch, value)) matching++;
            }
            if (matching != 1) return false;
        }
        if (shape.has("allOf")) {
            for (var branch : shape.path("allOf")) {
                if (!accepts(document, branch, value)) return false;
            }
        }
        if (shape.has("const") && !shape.path("const").equals(value)) return false;
        if (shape.has("enum") && !strings(shape.path("enum")).contains(value.asString())) return false;
        if (shape.has("type")) {
            var type = shape.path("type");
            Set<String> allowed = type.isArray() ? strings(type) : Set.of(type.asString());
            if (allowed.stream().noneMatch(candidate -> objectType(candidate, value))) return false;
        }
        if (value.isString() && ((shape.has("pattern") && !java.util.regex.Pattern
                .compile(shape.path("pattern").asString()).matcher(value.asString()).find())
                || value.asString().length() < shape.path("minLength").asInt(0)
                || value.asString().length() > shape.path("maxLength").asInt(Integer.MAX_VALUE))) return false;
        if (value.isIntegralNumber() && (value.asLong() < shape.path("minimum").asLong(Long.MIN_VALUE)
                || value.asLong() > shape.path("maximum").asLong(Long.MAX_VALUE))) return false;
        if (value.isObject()) {
            for (String member : strings(shape.path("required"))) {
                if (!value.has(member)) return false;
            }
            var members = shape.path("properties");
            var additional = shape.path("additionalProperties");
            for (String member : value.propertyNames()) {
                if (members.has(member)) {
                    if (!accepts(document, members.path(member), value.path(member))) return false;
                } else if (additional.isBoolean() && !additional.asBoolean()
                        || additional.isObject() && !accepts(document, additional, value.path(member))) {
                    return false;
                }
            }
        }
        if (value.isArray()) {
            if (value.size() > shape.path("maxItems").asInt(Integer.MAX_VALUE)) return false;
            if (shape.has("items")) {
                for (var element : value) {
                    if (!accepts(document, shape.path("items"), element)) return false;
                }
            }
        }
        return true;
    }

    private static boolean objectType(String expected, JsonNode node) {
        return switch (expected) {
            case "object" -> node.isObject();
            case "array" -> node.isArray();
            case "integer" -> node.isIntegralNumber();
            case "string" -> node.isString();
            case "boolean" -> node.isBoolean();
            case "null" -> node.isNull();
            default -> throw new AssertionError("Unexpected Inbox fixture schema type: " + expected);
        };
    }
}
