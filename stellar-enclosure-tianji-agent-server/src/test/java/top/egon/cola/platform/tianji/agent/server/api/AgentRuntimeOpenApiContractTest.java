package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.Test;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentClient;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeOpenApiContractTest {

    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final String PREFIX = "/api/tianji/v2/";
    private static final String PUBLISH_SCOPE = "tianji:agent:template:publish";
    private static final Set<String> ERROR_STATUSES = Set.of("400", "401", "403", "404", "409", "413", "415", "429", "500", "503");
    private static final String A = "a".repeat(64);
    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties", Map.of(), "required", List.of(),
            "additionalProperties", false);
    private static final Map<String, Object> REFERENCE = Map.of("key", "extract-prompt", "version", 1L, "contentSha256", A);
    private static final Map<String, Object> LEAF = Map.of("agentKey", "extract", "instructionRef", REFERENCE,
            "modelRef", "registered-model-1", "toolRefs", List.of());
    private static final Map<String, Object> LIMITS = Map.of("totalTimeout", "PT2M", "modelIdleTimeout", "PT20S", "toolTimeout", "PT10S",
            "maxModelCalls", 10, "maxToolCalls", 10, "maxParallelism", 2, "contextWindowTokens", 8192, "maxOutputTokensPerCall", 1024);

    @Test
    void packages_only_the_two_service_operations_with_their_durable_statuses_and_scopes() throws Exception {
        var document = document();
        assertEquals("3.1.0", document.path("openapi").asText());
        assertEquals("https://json-schema.org/draft/2020-12/schema", document.path("jsonSchemaDialect").asText());
        var paths = document.path("paths");
        assertEquals(Set.of(PREFIX + "agent-definitions", PREFIX + "agent-process-dispatches/submit"), names(paths));
        assertOperation(paths.path(PREFIX + "agent-definitions"), "publishAgentRuntimeTemplate", "200", PUBLISH_SCOPE,
                "tianji.api.agent.template.publish.v2", "TIANJI_AGENT_TEMPLATE_PUBLISH", "AgentRuntimeTemplateCommand", "AgentRuntimeTemplateDTO");
        assertOperation(paths.path(PREFIX + "agent-process-dispatches/submit"), "submitAgentRuntimeNode", "202", LinkedAgentClient.SUBMIT_SCOPE,
                "tianji.api.agent.linkedSubmit.v2", "TIANJI_LINKED_AGENT_SUBMIT", "LinkedAgentRuntimeCommand", "LinkedAgentRuntimeDTO");
        var oauth = document.at("/components/securitySchemes/oauth2");
        assertEquals("oauth2", oauth.path("type").asText());
        assertEquals(Set.of("clientCredentials"), names(oauth.path("flows")));
        assertEquals(Set.of(PUBLISH_SCOPE, LinkedAgentClient.SUBMIT_SCOPE), names(oauth.at("/flows/clientCredentials/scopes")));
        assertLocalReferencesResolve(document, document);
        String artifact = document.toString();
        for (String componentType : List.of("AgentExecutor", "AgenticPlanVO", "AgenticLeafVO", "AgentExecutionContextVO",
                "AgentExecutionHandle", "CallPermitProvider", "SessionStore")) {
            assertFalse(artifact.contains(componentType), componentType);
        }
    }

    @Test
    void reflects_java_wire_fields_and_exact_numeric_bounds() throws Exception {
        var document = document();
        assertClosedClass(document, AgentRuntimeTemplateCommand.class);
        assertClosedClass(document, AgentRuntimeTemplateDTO.class);
        assertClosedClass(document, LinkedAgentRuntimeCommand.class);
        assertClosedClass(document, LinkedAgentRuntimeDTO.class);
        assertClosedClass(document, AgentRuntimeTemplateCommand.TechnicalLimitsVO.class);
        for (String member : List.of("templateVersion", "revision")) {
            Class<?> type = member.equals("revision") ? LinkedAgentRuntimeDTO.class : AgentRuntimeTemplateCommand.class;
            var property = schema(document, type.getSimpleName()).path("properties").path(member);
            assertTrue(accepts(document, property, JSON.valueToTree(ProcessTypedJson.V2_MAX_WIRE_INTEGER)));
            assertFalse(accepts(document, property, JSON.valueToTree(ProcessTypedJson.V2_MAX_WIRE_INTEGER + 1)));
            assertFalse(accepts(document, property, JSON.valueToTree(1.5)));
        }
        assertAccepted(document, "TechnicalLimitsVO", LIMITS);
        assertRejected(document, "TechnicalLimitsVO", with(LIMITS, "maxOutputTokensPerCall", 65_537));
        assertRejected(document, "TechnicalLimitsVO", with(LIMITS, "budgetReservation", "unknown"));
        assertAccepted(document, "LinkedAgentRuntimeCommand", submission());
        assertRejected(document, "LinkedAgentRuntimeCommand",
                with(submission(), "link", with(typedObject(submission().get("link")), "scopeType", "CMMN")));
        assertRejected(document, "LinkedAgentRuntimeCommand", with(submission(), "attemptDeadlineAt", "2026-11-11T12:02:00+01:00"));
    }

    @Test
    void validates_configuration_omission_defaults_and_exactly_two_nullable_plan_members() throws Exception {
        var document = document();
        var standalone = template(configuration());
        var minimal = template(with(configuration(), "agenticPlan", minimalPlan()));
        var normalizedPlan = new LinkedHashMap<>(minimalPlan());
        normalizedPlan.put("strategy", "REASONING");
        normalizedPlan.put("links", List.of());
        normalizedPlan.put("maxRounds", null);
        normalizedPlan.put("maxParallelism", 1);
        normalizedPlan.put("merge", "OUTPUT_AGENT");
        normalizedPlan.put("sharedFields", List.of());
        normalizedPlan.put("termination", Map.of("mode", "ROOT_TERMINAL"));
        var normalized = template(with(configuration(), "agenticPlan", normalizedPlan));

        assertAccepted(document, "AgentRuntimeTemplateCommand", standalone);
        assertAccepted(document, "AgentRuntimeTemplateCommand", minimal);
        assertAccepted(document, "AgentRuntimeTemplateCommand", normalized);
        assertRejected(document, "AgentRuntimeTemplateCommand", template(with(configuration(), "agenticPlan", null)));
        assertRejected(document, "AgentRuntimeTemplateCommand", template(with(configuration(), "toolRefs", null)));
        assertArrayEquals(AgentRuntimeTemplateCommand.fromExactWire(minimal).canonicalBytes(),
                AgentRuntimeTemplateCommand.fromExactWire(normalized).canonicalBytes());

        var properties = schema(document, "AgenticTemplatePlanVO").path("properties");
        assertEquals(Set.of("strategy", "agents", "links", "maxRounds", "maxParallelism", "outputAgentKey", "merge", "sharedFields", "termination"),
                names(properties));
        assertEquals("REASONING", properties.at("/strategy/default").asText());
        assertEquals("OUTPUT_AGENT", properties.at("/merge/default").asText());
        assertEquals(1, properties.at("/maxParallelism/default").asInt());
        assertEquals("ROOT_TERMINAL", properties.at("/termination/default/mode").asText());
        for (String arrayDefault : List.of("links", "sharedFields")) {
            assertTrue(properties.path(arrayDefault).path("default").isArray());
            assertEquals(0, properties.path(arrayDefault).path("default").size());
        }
        for (var field : properties.properties()) {
            assertEquals(Set.of("maxRounds", "outputAgentKey").contains(field.getKey()),
                    accepts(document, field.getValue(), JSON.readTree("null")), field.getKey());
        }
    }

    @Test
    void rejects_invalid_agentic_branches_and_unknown_fields_with_real_fixtures() throws Exception {
        var document = document();
        var plan = minimalPlan();
        var leaf2 = with(LEAF, "agentKey", "second-agent");
        var edge = Map.<String, Object>of("source", "extract", "target", "second-agent");
        var twoLeaves = with(plan, "agents", List.of(LEAF, leaf2));
        var handoff = with(with(with(twoLeaves, "strategy", "HANDOFF"), "maxRounds", 2), "links", List.of(edge));
        var predicate = Map.of("mode", "PREDICATE", "condition",
                Map.of("op", "EQ", "args", List.of(Map.of("path", "round"), with(Map.of(), "literal", null))));

        assertAccepted(document, "AgenticTemplatePlanVO", with(handoff, "termination", predicate));
        assertAccepted(document, "AgenticTemplatePlanVO", with(with(without(twoLeaves, "outputAgentKey"), "strategy", "PARALLEL"),
                "merge", "COLLECT_BY_AGENT"));
        assertRejected(document, "AgenticTemplatePlanVO", twoLeaves);
        assertRejected(document, "AgenticTemplatePlanVO", without(plan, "outputAgentKey"));
        assertRejected(document, "AgenticTemplatePlanVO", with(plan, "maxParallelism", 2));
        assertRejected(document, "AgenticTemplatePlanVO", with(plan, "links", List.of(edge)));
        assertRejected(document, "AgenticTemplatePlanVO", with(plan, "termination", predicate));
        assertRejected(document, "AgenticTemplatePlanVO", with(plan, "strategy", null));
        assertRejected(document, "AgenticTemplatePlanVO", with(plan, "reserved", true));
        assertRejected(document, "AgenticTemplatePlanVO", without(handoff, "maxRounds"));
        assertRejected(document, "AgenticTemplatePlanVO", with(handoff, "merge", "COLLECT_BY_AGENT"));
        assertRejected(document, "AgenticTemplateLeafVO", with(LEAF, "agentKey", "extract.part"));
        assertRejected(document, "AgenticTemplateLeafVO", with(LEAF, "extra", true));
        assertRejected(document, "AgenticTemplateLinkVO", with(edge, "extra", true));
        assertRejected(document, "AgenticTemplateTerminationVO", with(predicate, "mode", "ROOT_TERMINAL"));
        assertRejected(document, "AgenticTemplateTerminationVO", without(predicate, "condition"));
        assertAccepted(document, "AgenticConditionVO", with(Map.of(), "literal", null));
        assertRejected(document, "AgenticConditionVO", Map.of());
        assertRejected(document, "AgenticConditionVO", Map.of("literal", 1.5));
        assertRejected(document, "AgenticConditionVO", Map.of("op", "NOT", "args", List.of(Map.of("literal", true), Map.of("literal", false))));
    }

    @Test
    void validates_the_closed_process_schema_subset_and_frozen_format_names() throws Exception {
        var document = document();
        assertAccepted(document, "ProcessSchemaVO", SCHEMA);
        assertAccepted(document, "ProcessSchemaVO", Map.of("type", "string", "format", "date-time"));
        assertAccepted(document, "ProcessSchemaVO", Map.of("type", "string", "format", "decimal:18"));
        assertRejected(document, "ProcessSchemaVO", Map.of("type", "string", "format", "decimal:19"));
        assertRejected(document, "ProcessSchemaVO", Map.of("type", "number"));
        assertRejected(document, "ProcessSchemaVO", Map.of("type", "array", "items", SCHEMA));
        assertRejected(document, "ProcessSchemaVO", with(SCHEMA, "additionalProperties", true));
        assertRejected(document, "ProcessSchemaVO", with(SCHEMA, "$ref", "remote"));
        assertRejected(document, "ProcessSchemaVO", with(SCHEMA, "maxLength", 4));
        assertRejected(document, "AgentRuntimeTemplateCommand", with(template(configuration()), "parameterSchema", Map.of("type", "string")));
    }

    private static void assertOperation(JsonNode path, String operationId, String status, String scope, String resource,
                                        String permission, String request, String response) {
        assertEquals(Set.of("post"), names(path));
        var operation = path.path("post");
        assertEquals(operationId, operation.path("operationId").asText());
        assertEquals(Set.of("SERVICE"), strings(operation.path("x-allowed-principals")));
        assertEquals(Set.of(permission), strings(operation.path("x-required-permissions")));
        assertEquals(resource, operation.path("x-resource-key").asText());
        assertEquals(JSON.<JsonNode>valueToTree(List.of(Map.of("oauth2", List.of(scope)))), operation.path("security"));
        assertTrue(operation.at("/requestBody/required").asBoolean());
        assertEquals("#/components/schemas/" + request, operation.at("/requestBody/content/application~1json/schema/$ref").asText());
        var responses = operation.path("responses");
        assertEquals(ERROR_STATUSES, names(responses).stream().filter(code -> !code.equals(status)).collect(Collectors.toSet()));
        var success = responses.path(status);
        assertEquals("#/components/schemas/" + response, success.at("/content/application~1json/schema/$ref").asText());
        assertEquals("no-store", success.at("/headers/Cache-Control/schema/const").asText());
        assertTrue(operation.path("parameters").isArray());
        assertTrue(operation.path("parameters").valueStream().anyMatch(parameter ->
                "header".equals(parameter.path("in").asText()) && "X-Egon-Tianji-Tenant".equals(parameter.path("name").asText())
                        && !parameter.path("required").asBoolean(true)));
    }

    private static void assertClosedClass(JsonNode document, Class<?> type) {
        var schema = schema(document, type.getSimpleName());
        assertEquals("object", schema.path("type").asText(), type.getSimpleName());
        assertFalse(schema.path("additionalProperties").asBoolean(true), type.getSimpleName());
        var fields = Arrays.stream(type.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName).collect(Collectors.toSet());
        assertEquals(fields, names(schema.path("properties")), type.getSimpleName());
        assertEquals(fields, strings(schema.path("required")), type.getSimpleName());
    }

    private static JsonNode document() throws Exception {
        try (var resource = AgentRuntimeOpenApiContractTest.class.getResourceAsStream("/META-INF/tianji/openapi/agent-runtime-v2.json")) {
            assertNotNull(resource, "agent-runtime-v2.json must be packaged");
            return JSON.readTree(resource.readAllBytes());
        }
    }

    private static JsonNode schema(JsonNode document, String name) {
        var schema = document.path("components").path("schemas").path(name);
        assertTrue(schema.isObject(), name);
        return schema;
    }

    private static void assertLocalReferencesResolve(JsonNode root, JsonNode node) {
        if (node.isObject()) {
            for (var entry : node.properties()) {
                if ("$ref".equals(entry.getKey())) {
                    String ref = entry.getValue().asText();
                    assertTrue(ref.startsWith("#/components/"), ref);
                    assertFalse(root.at(ref.substring(1)).isMissingNode(), ref);
                } else {
                    assertLocalReferencesResolve(root, entry.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(member -> assertLocalReferencesResolve(root, member));
        }
    }

    private static void assertAccepted(JsonNode document, String schema, Object fixture) {
        assertTrue(accepts(document, schema(document, schema), JSON.valueToTree(fixture)), schema + " rejected the fixture");
    }

    private static void assertRejected(JsonNode document, String schema, Object fixture) {
        assertFalse(accepts(document, schema(document, schema), JSON.valueToTree(fixture)), schema + " accepted an invalid fixture");
    }

    // Evaluates only the JSON Schema assertions exercised by these static fixtures.
    private static boolean accepts(JsonNode document, JsonNode schema, JsonNode value) {
        if (schema.isBoolean()) {
            return schema.asBoolean();
        }
        if (schema.has("$ref") && !accepts(document, document.at(schema.path("$ref").asText().substring(1)), value)) {
            return false;
        }
        if (schema.has("type") && !matchesType(schema.path("type"), value) || schema.has("const") && !schema.path("const").equals(value)
                || schema.has("enum") && schema.path("enum").valueStream().noneMatch(value::equals)) {
            return false;
        }
        if (!schema.path("allOf").valueStream().allMatch(child -> accepts(document, child, value))
                || schema.has("oneOf") && schema.path("oneOf").valueStream().filter(child -> accepts(document, child, value)).count() != 1
                || schema.has("anyOf") && schema.path("anyOf").valueStream().noneMatch(child -> accepts(document, child, value))
                || schema.has("not") && accepts(document, schema.path("not"), value)) {
            return false;
        }
        if (schema.has("if")) {
            String branch = accepts(document, schema.path("if"), value) ? "then" : "else";
            if (schema.has(branch) && !accepts(document, schema.path(branch), value)) {
                return false;
            }
        }
        if (value.isObject()) {
            return matchesObject(document, schema, value);
        }
        if (value.isArray()) {
            return value.size() >= schema.path("minItems").asInt(0) && value.size() <= schema.path("maxItems").asInt(Integer.MAX_VALUE)
                    && (!schema.path("uniqueItems").asBoolean() || value.valueStream().distinct().count() == value.size())
                    && (!schema.has("items") || value.valueStream().allMatch(item -> accepts(document, schema.path("items"), item)));
        }
        if (value.isString()) {
            String text = value.asText();
            int length = text.codePointCount(0, text.length());
            return length >= schema.path("minLength").asInt(0) && length <= schema.path("maxLength").asInt(Integer.MAX_VALUE)
                    && (!schema.has("pattern") || java.util.regex.Pattern.compile(schema.path("pattern").asText()).matcher(text).find());
        }
        return !value.isNumber() || (!schema.has("minimum") || value.decimalValue().compareTo(schema.path("minimum").decimalValue()) >= 0)
                && (!schema.has("maximum") || value.decimalValue().compareTo(schema.path("maximum").decimalValue()) <= 0);
    }

    private static boolean matchesType(JsonNode types, JsonNode value) {
        if (types.isArray()) {
            return types.valueStream().anyMatch(type -> matchesType(type, value));
        }
        return switch (types.asText()) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isString();
            case "boolean" -> value.isBoolean();
            case "integer" -> value.isNumber() && value.decimalValue().stripTrailingZeros().scale() <= 0;
            case "null" -> value.isNull();
            default -> false;
        };
    }

    private static boolean matchesObject(JsonNode document, JsonNode schema, JsonNode value) {
        if (!names(value).containsAll(strings(schema.path("required")))) {
            return false;
        }
        var properties = schema.path("properties");
        for (var field : value.properties()) {
            var member = properties.path(field.getKey());
            if (member.isMissingNode()) {
                member = schema.path("additionalProperties");
            }
            if (!member.isMissingNode() && !accepts(document, member, field.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> names(JsonNode value) {
        return Set.copyOf(value.propertyNames());
    }

    private static Set<String> strings(JsonNode node) {
        return node.valueStream().map(JsonNode::asText).collect(Collectors.toSet());
    }

    private static Map<String, Object> template(Map<String, Object> configuration) {
        return Map.of("templateKey", "extract", "templateVersion", 1L, "runtimeProfile", "RUNTIME_V2", "executorRef", "executor",
                "inputSchema", SCHEMA, "outputSchema", SCHEMA, "parameterSchema", SCHEMA, "configuration", configuration, "limits", LIMITS);
    }

    private static Map<String, Object> configuration() {
        return Map.of("instructionRef", REFERENCE, "modelRef", "registered-model-1", "toolRefs", List.of());
    }

    private static Map<String, Object> minimalPlan() {
        return Map.of("agents", List.of(LEAF), "outputAgentKey", "extract");
    }

    private static Map<String, Object> submission() {
        var body = new LinkedHashMap<String, Object>();
        body.put("idempotencyKey", "dispatch-101");
        body.put("link", Map.of("sourceId", "ps-main", "scopeType", "BPMN", "scopeInstanceId", "pi-101", "activityOccurrenceId", "job-101",
                "definitionContentSha256", A, "dispatchNotAfter", "2026-11-11T11:00:30Z"));
        body.put("nodeExecutionRef", "nx-101");
        body.put("templateKey", "extract");
        body.put("templateVersion", 1L);
        body.put("templateSha256", A);
        body.put("configurationSha256", A);
        body.put("inputSchemaSha256", A);
        body.put("outputSchemaSha256", A);
        body.put("input", Map.of("text", "sample"));
        body.put("parameters", Map.of());
        body.put("limits", LIMITS);
        body.put("attemptDeadlineAt", "2026-11-11T11:02:00Z");
        body.put("correlationId", "corr-101");
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> typedObject(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> with(Map<String, Object> source, String field, Object value) {
        var result = new LinkedHashMap<>(source);
        result.put(field, value);
        return result;
    }

    private static Map<String, Object> without(Map<String, Object> source, String field) {
        var result = new LinkedHashMap<>(source);
        result.remove(field);
        return result;
    }
}
