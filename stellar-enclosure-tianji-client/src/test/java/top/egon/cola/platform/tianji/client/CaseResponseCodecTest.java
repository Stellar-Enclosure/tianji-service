package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.ActOnPlanItemCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CaseOperationResult;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationErrorDTO;
import top.egon.cola.platform.tianji.contract.orchestration.StartCaseCommand;

import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseResponseCodecTest {
    private static final Instant SUBMITTED = Instant.parse("2026-11-01T10:00:00.123456789Z");
    private static final Instant FINISHED = Instant.parse("2026-11-01T11:00:00.987654321Z");
    private static final String VERSION_A = "a".repeat(64);
    private static final String VERSION_B = "b".repeat(64);
    private static final Set<String> CASE_FIELDS = Set.of("caseInstanceId", "definitionKey", "releaseVersion",
            "businessKey", "correlationId", "status", "submittedAt", "finishedAt", "planItems");
    private static final Set<String> ITEM_FIELDS = Set.of("planItemInstanceId", "planItemDefinitionKey",
            "kind", "state", "version", "availableActions");
    private static final Set<String> OPERATION_FIELDS = Set.of("commandId", "caseInstanceId",
            "planItemInstanceId", "planItemDefinitionKey", "action", "releaseVersion", "definitionKey",
            "previousState", "previousVersion", "resultingState", "resultingVersion", "occurredAt");
    private static final Map<String, Set<String>> ERROR_CODES_BY_STATUS = Map.of(
            "400", Set.of("TIANJI_INVALID_COMMAND"),
            "401", Set.of("TIANJI_UNAUTHENTICATED"),
            "403", Set.of("TIANJI_PERMISSION_DENIED"),
            "404", Set.of("TIANJI_RESOURCE_NOT_FOUND"),
            "409", Set.of("TIANJI_IDEMPOTENCY_CONFLICT", "TIANJI_TENANT_CONTEXT_CHANGED"),
            "413", Set.of("TIANJI_REQUEST_TOO_LARGE"),
            "429", Set.of("TIANJI_CAPACITY_EXCEEDED"),
            "503", Set.of("TIANJI_DEPENDENCY_UNAVAILABLE"));
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CaseResponseCodec codec = new CaseResponseCodec();

    @Test
    void decodes_active_terminal_sparse_and_nanosecond_case_snapshots_with_explicit_finishedAt() {
        CaseInstanceDTO active = activeCase();
        String activeJson = mapper.writeValueAsString(active)
                .replace(SUBMITTED.toString(), "2026-11-01T18:00:00.123456789+08:00");
        CaseInstanceDTO decoded = assertDoesNotThrow(() -> codec.instance(bytes(activeJson)));
        assertEquals(active, decoded);
        assertEquals(SUBMITTED, decoded.submittedAt());
        assertNull(decoded.finishedAt());

        CaseInstanceDTO completed = new CaseInstanceDTO("case-1", "formalCase", "release-1", "business-1",
                "correlation-1", CaseInstanceStatusEnum.COMPLETED, SUBMITTED, FINISHED, List.of());
        assertEquals(completed, codec.instance(mapper.writeValueAsBytes(completed)));
        CaseInstanceDTO nonChronological = new CaseInstanceDTO("case-old", "formalCase", "release-1", "business-old",
                "correlation-old", CaseInstanceStatusEnum.COMPLETED, SUBMITTED, SUBMITTED.minusSeconds(1), List.of());
        assertEquals(nonChronological, codec.instance(mapper.writeValueAsBytes(nonChronological)));

        CasePlanItemDTO sparse = item("available", "supplementalTask", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.AVAILABLE, List.of());
        CaseInstanceDTO terminated = new CaseInstanceDTO("case-2", "formalCase", "release-1", "business-2",
                "correlation-2", CaseInstanceStatusEnum.TERMINATED, SUBMITTED, FINISHED, List.of(sparse));
        assertEquals(terminated, codec.instance(mapper.writeValueAsBytes(terminated)));
    }

    @Test
    void decodes_all_four_immutable_operation_transitions_and_preserves_nanoseconds() {
        for (CaseOperationResult result : List.of(
                operation(CasePlanItemActionEnum.DISABLE, CasePlanItemStateEnum.ENABLED, CasePlanItemStateEnum.DISABLED),
                operation(CasePlanItemActionEnum.ENABLE, CasePlanItemStateEnum.DISABLED, CasePlanItemStateEnum.ENABLED),
                operation(CasePlanItemActionEnum.START, CasePlanItemStateEnum.ENABLED, CasePlanItemStateEnum.ACTIVE),
                operation(CasePlanItemActionEnum.TERMINATE, CasePlanItemStateEnum.ACTIVE, CasePlanItemStateEnum.TERMINATED))) {
            CaseOperationResult decoded = assertDoesNotThrow(() -> codec.operation(mapper.writeValueAsBytes(result)));
            assertEquals(result, decoded);
            assertEquals(FINISHED, decoded.occurredAt());
        }
    }

    @Test
    void requires_every_case_item_and_operation_property_including_explicit_nullable_finishedAt() {
        assertMissingFieldsRejected(mapper.valueToTree(activeCase()), CASE_FIELDS, codec::instance);
        assertMissingFieldsRejected(mapper.valueToTree(activeCase().planItems().getFirst()), ITEM_FIELDS,
                bytes -> codec.instance(caseWithItem(bytes)));
        assertMissingFieldsRejected(mapper.valueToTree(operation(CasePlanItemActionEnum.START,
                CasePlanItemStateEnum.ENABLED, CasePlanItemStateEnum.ACTIVE)), OPERATION_FIELDS, codec::operation);

        ObjectNode nullCase = mapper.valueToTree(activeCase());
        nullCase.putNull("caseInstanceId");
        assertThrows(RuntimeException.class, () -> codec.instance(mapper.writeValueAsBytes(nullCase)));
        ObjectNode explicitFinishedAt = mapper.valueToTree(activeCase());
        explicitFinishedAt.putNull("finishedAt");
        assertDoesNotThrow(() -> codec.instance(mapper.writeValueAsBytes(explicitFinishedAt)));
    }

    @Test
    void rejects_duplicate_unknown_trailing_coerced_numeric_enum_and_numeric_instant_values() {
        assertEquals(128 * 1024, CaseResponseCodec.MAX_BYTES);
        assertThrows(RuntimeException.class, () -> codec.instance(null));
        assertThrows(RuntimeException.class, () -> codec.instance(new byte[0]));
        assertThrows(RuntimeException.class, () -> codec.instance(new byte[CaseResponseCodec.MAX_BYTES + 1]));
        String json = mapper.writeValueAsString(activeCase());
        assertInvalidInstance(json.replaceFirst("\\{", "{\"caseInstanceId\":\"duplicate\","));
        assertInvalidInstance(json.substring(0, json.length() - 1) + ",\"nativeRevision\":7}");
        assertInvalidInstance(json + " {}");
        assertInvalidInstance(json.replace("\"status\":\"ACTIVE\"", "\"status\":1"));
        assertInvalidInstance(json.replace("\"submittedAt\":\"" + SUBMITTED + "\"", "\"submittedAt\":1"));
        assertInvalidInstance(json.replace("\"submittedAt\":\"" + SUBMITTED + "\"",
                "\"submittedAt\":\"2026-11-01T10:00:00.123456789\""));
        assertInvalidInstance(json.replace("\"submittedAt\":\"" + SUBMITTED + "\"",
                "\"submittedAt\":\"123\""));
        ObjectNode terminalEpoch = mapper.valueToTree(activeCase());
        terminalEpoch.put("status", "COMPLETED");
        terminalEpoch.put("finishedAt", "123.1");
        terminalEpoch.set("planItems", mapper.createArrayNode());
        assertThrows(RuntimeException.class, () -> codec.instance(mapper.writeValueAsBytes(terminalEpoch)));
        ObjectNode operationEpoch = mapper.valueToTree(operation(CasePlanItemActionEnum.START,
                CasePlanItemStateEnum.ENABLED, CasePlanItemStateEnum.ACTIVE));
        operationEpoch.put("occurredAt", "123.1");
        assertThrows(RuntimeException.class, () -> codec.operation(mapper.writeValueAsBytes(operationEpoch)));
        assertInvalidInstance(json.replace("\"state\":\"ENABLED\"", "\"state\":1"));
        assertInvalidInstance(json.replace("\"caseInstanceId\":\"case-1\"", "\"caseInstanceId\":1"));
        assertInvalidInstance(json.replace("\"availableActions\":[\"DISABLE\",\"START\"]",
                "\"availableActions\":[1]"));
    }

    @Test
    void enforces_sixteen_items_four_actions_and_closed_nested_shapes_before_conversion() {
        String item = mapper.writeValueAsString(item("item-1", "item1", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.AVAILABLE, List.of()));
        ObjectNode base = mapper.valueToTree(activeCase());
        base.set("planItems", mapper.readTree("[" + java.util.stream.IntStream.range(0, 17)
                .mapToObj(index -> item.replace("item-1", "item-" + index).replace("item1", "item" + index))
                .collect(Collectors.joining(",")) + "]"));
        assertThrows(RuntimeException.class, () -> codec.instance(mapper.writeValueAsBytes(base)));

        ObjectNode tooManyActions = mapper.valueToTree(activeCase());
        ((ObjectNode) tooManyActions.path("planItems").get(0)).set("availableActions",
                mapper.readTree("[\"DISABLE\",\"START\",\"DISABLE\",\"START\",\"DISABLE\"]"));
        assertThrows(RuntimeException.class,
                () -> codec.instance(mapper.writeValueAsBytes(tooManyActions)));

        ObjectNode unknownNested = mapper.valueToTree(activeCase());
        ((ObjectNode) unknownNested.path("planItems").get(0)).put("nativeState", "ENABLED");
        assertThrows(RuntimeException.class,
                () -> codec.instance(mapper.writeValueAsBytes(unknownNested)));
    }

    @Test
    void error_decoder_accepts_only_the_nine_existing_code_status_pairs_and_never_returns_unknown_data() {
        int pairs = 0;
        for (var entry : ERROR_CODES_BY_STATUS.entrySet()) {
            for (String code : entry.getValue()) {
                byte[] body = error(code, "private-correlation");
                assertEquals(code, codec.errorCode(body, Integer.parseInt(entry.getKey())));
                pairs++;
            }
        }
        assertEquals(9, pairs);

        assertNull(codec.errorCode(error("TIANJI_INTERNAL_ERROR", null), 500));
        assertNull(codec.errorCode(error("TIANJI_IDEMPOTENCY_CONFLICT", null), 400));
        assertNull(codec.errorCode(bytes("{\"code\":\"TIANJI_INVALID_COMMAND\",\"correlationId\":null,\"message\":\"private\"}"), 400));
        assertNull(codec.errorCode(bytes("{\"code\":1,\"correlationId\":null}"), 400));
        assertEquals("TIANJI_INVALID_COMMAND",
                codec.errorCode(bytes("{\"code\":\"TIANJI_INVALID_COMMAND\"}"), 400));
        assertNull(codec.errorCode(bytes("not-json"), 400));
    }

    @Test
    void contract_first_openapi_artifact_matches_the_case_contract_and_resolves_every_local_schema_reference() throws Exception {
        JsonNode root = mapper.readTree(resource("/META-INF/tianji/openapi/case-v1.json"));
        assertEquals("3.1.0", root.path("openapi").asText());
        Set<String> expectedPaths = Set.of("/api/tianji/v1/cases/start", "/api/tianji/v1/cases/get",
                "/api/tianji/v1/cases/act-plan-item");
        assertEquals(expectedPaths, names(root.path("paths")));
        for (String path : expectedPaths) {
            JsonNode item = root.path("paths").path(path);
            assertTrue(item.path("post").isObject(), path);
            assertEquals(Set.of("post"), names(item), path);
            JsonNode post = item.path("post");
            assertTrue(post.path("requestBody").path("required").asBoolean(), path);
            assertTrue(post.path("operationId").isString(), path);
            assertEquals(Set.of("200", "400", "401", "403", "404", "409", "413", "429", "503"),
                    names(post.path("responses")), path);
            assertSecurity(post, root.path("components").path("securitySchemes"));
        }
        assertEquals(3, expectedPaths.stream().map(path -> root.path("paths").path(path).path("post")
                .path("operationId").asText()).collect(Collectors.toUnmodifiableSet()).size());

        assertOperationSchemas(root, "/api/tianji/v1/cases/start", "StartCaseCommand", "CaseInstanceDTO");
        assertOperationSchemas(root, "/api/tianji/v1/cases/get", "CaseInstanceQuery", "CaseInstanceDTO");
        assertOperationSchemas(root, "/api/tianji/v1/cases/act-plan-item", "ActOnPlanItemCommand", "CaseOperationResult");

        Map<String, Class<?>> recordSchemas = Map.of(
                "StartCaseCommand", StartCaseCommand.class,
                "CaseInstanceQuery", CaseInstanceQuery.class,
                "ActOnPlanItemCommand", ActOnPlanItemCommand.class,
                "CasePlanItemDTO", CasePlanItemDTO.class,
                "CaseInstanceDTO", CaseInstanceDTO.class,
                "CaseOperationResult", CaseOperationResult.class,
                "OrchestrationErrorDTO", OrchestrationErrorDTO.class);
        JsonNode schemas = root.path("components").path("schemas");
        for (var entry : recordSchemas.entrySet()) {
            JsonNode schema = schemas.path(entry.getKey());
            assertTrue(schema.isObject(), entry.getKey());
            Set<String> fields = Arrays.stream(entry.getValue().getRecordComponents())
                    .map(RecordComponent::getName).collect(Collectors.toUnmodifiableSet());
            assertEquals(fields, names(schema.path("properties")), entry.getKey());
            Set<String> required = entry.getValue() == OrchestrationErrorDTO.class ? Set.of("code") : fields;
            assertEquals(required, values(schema.path("required")), entry.getKey());
        }

        assertEnum(schemas, "CasePlanItemActionEnum", "DISABLE", "ENABLE", "START", "TERMINATE");
        assertEnum(schemas, "CaseInstanceStatusEnum", "ACTIVE", "COMPLETED", "TERMINATED");
        assertEnum(schemas, "CasePlanItemStateEnum", "AVAILABLE", "ENABLED", "DISABLED", "ACTIVE", "COMPLETED", "TERMINATED");
        assertEnum(schemas, "CasePlanItemKindEnum", "HUMAN_TASK", "MILESTONE");

        assertEquals("string", schemas.path("CaseInstanceDTO").path("properties").path("submittedAt").path("type").asText());
        assertEquals("date-time", schemas.path("CaseInstanceDTO").path("properties").path("submittedAt").path("format").asText());
        assertTrue(schemas.path("CaseInstanceDTO").path("properties").path("finishedAt").toString().contains("null"));
        assertEquals("array", schemas.path("CaseInstanceDTO").path("properties").path("planItems").path("type").asText());

        List<String> refs = new ArrayList<>();
        collectRefs(root, refs);
        assertFalse(refs.isEmpty());
        for (String ref : refs) {
            assertTrue(ref.startsWith("#/components/schemas/"), ref);
            assertTrue(schemas.path(ref.substring("#/components/schemas/".length())).isObject(), ref);
        }
    }

    private void assertMissingFieldsRejected(ObjectNode original, Set<String> fields, Decoder decoder) {
        for (String field : fields) {
            ObjectNode copy = original.deepCopy();
            copy.remove(field);
            assertThrows(RuntimeException.class,
                    () -> decoder.decode(mapper.writeValueAsBytes(copy)), field);
        }
    }

    private byte[] caseWithItem(byte[] itemBytes) {
        ObjectNode node = mapper.valueToTree(activeCase());
        node.set("planItems", mapper.createArrayNode().add(mapper.readTree(itemBytes)));
        return mapper.writeValueAsBytes(node);
    }

    private void assertInvalidInstance(String json) {
        assertThrows(RuntimeException.class, () -> codec.instance(bytes(json)));
    }

    private byte[] error(String code, String correlationId) {
        return mapper.writeValueAsBytes(new OrchestrationErrorDTO(code, correlationId));
    }

    private static CaseInstanceDTO activeCase() {
        return new CaseInstanceDTO("case-1", "formalCase", "release-1", "business-1", "correlation-1",
                CaseInstanceStatusEnum.ACTIVE, SUBMITTED, null, List.of(
                item("primary-1", "primaryTask", CasePlanItemKindEnum.HUMAN_TASK, CasePlanItemStateEnum.ENABLED,
                        List.of(CasePlanItemActionEnum.DISABLE, CasePlanItemActionEnum.START)),
                item("supplemental-1", "supplementalTask", CasePlanItemKindEnum.HUMAN_TASK, CasePlanItemStateEnum.AVAILABLE, List.of()),
                item("milestone-1", "completionMilestone", CasePlanItemKindEnum.MILESTONE, CasePlanItemStateEnum.AVAILABLE, List.of())));
    }

    private static CasePlanItemDTO item(String id, String key, CasePlanItemKindEnum kind,
                                        CasePlanItemStateEnum state, List<CasePlanItemActionEnum> actions) {
        return new CasePlanItemDTO(id, key, kind, state, VERSION_A, actions);
    }

    private static CaseOperationResult operation(CasePlanItemActionEnum action, CasePlanItemStateEnum previous,
                                                 CasePlanItemStateEnum resulting) {
        return new CaseOperationResult("command-1", "case-1", "primary-1", "primaryTask", action,
                "release-1", "formalCase", previous, VERSION_A, resulting, VERSION_B, FINISHED);
    }

    private static byte[] resource(String path) throws Exception {
        try (InputStream input = CaseResponseCodecTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return input.readAllBytes();
        }
    }

    private static Set<String> names(JsonNode object) {
        Set<String> names = new java.util.LinkedHashSet<>();
        object.propertyNames().forEach(names::add);
        return Set.copyOf(names);
    }

    private static Set<String> values(JsonNode array) {
        Set<String> values = new java.util.LinkedHashSet<>();
        array.forEach(value -> values.add(value.asText()));
        return Set.copyOf(values);
    }

    private static void assertEnum(JsonNode schemas, String name, String... values) {
        JsonNode schema = schemas.path(name);
        assertEquals("string", schema.path("type").asText(), name);
        assertArrayEquals(values, java.util.stream.StreamSupport.stream(schema.path("enum").spliterator(), false)
                .map(JsonNode::asText).toArray(String[]::new), name);
    }

    private static void assertOperationSchemas(JsonNode root, String path, String requestSchema, String successSchema) {
        JsonNode post = root.path("paths").path(path).path("post");
        assertEquals("#/components/schemas/" + requestSchema, post.path("requestBody").path("content")
                .path("application/json").path("schema").path("$ref").asText(), path);
        assertEquals("#/components/schemas/" + successSchema, post.path("responses").path("200").path("content")
                .path("application/json").path("schema").path("$ref").asText(), path);
        for (var entry : ERROR_CODES_BY_STATUS.entrySet()) {
            String status = entry.getKey();
            JsonNode schema = post.path("responses").path(status).path("content").path("application/json").path("schema");
            assertEquals("#/components/schemas/OrchestrationErrorDTO", schema.path("$ref").asText(), path + ':' + status);
            assertEquals(entry.getValue(), values(schema.path("properties").path("code").path("enum")),
                    path + ':' + status);
        }
    }

    private static void assertSecurity(JsonNode operation, JsonNode schemes) {
        JsonNode security = operation.path("security");
        assertTrue(security.isArray() && security.size() == 1);
        Set<String> names = names(security.get(0));
        assertEquals(1, names.size());
        String name = names.iterator().next();
        JsonNode scheme = schemes.path(name);
        assertTrue(scheme.isObject(), name);
        assertEquals("http", scheme.path("type").asText(), name);
        assertEquals("bearer", scheme.path("scheme").asText(), name);
    }

    private static void collectRefs(JsonNode node, List<String> refs) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if ("$ref".equals(entry.getKey()) && entry.getValue().isString()) refs.add(entry.getValue().asText());
                else collectRefs(entry.getValue(), refs);
            });
        } else if (node.isArray()) {
            node.forEach(value -> collectRefs(value, refs));
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface Decoder {
        Object decode(byte[] bytes);
    }
}
