package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessResult;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationErrorDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlBlockReasonEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlSnapshotDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;

import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessControlOpenApiContractTest {
    private static final Set<String> PERMISSIONS = Set.of("TIANJI_PROCESS_READ", "TIANJI_PROCESS_CANCEL");
    private static final Set<String> PRINCIPALS = Set.of("USER", "SERVICE");
    private static final Set<String> SCOPES = Set.of(ProcessControlClient.READ_SCOPE, ProcessControlClient.CANCEL_SCOPE);
    private static final Map<String, Set<String>> CONTROL_ERRORS = Map.of(
            "400", Set.of("TIANJI_INVALID_COMMAND"),
            "401", Set.of("TIANJI_UNAUTHENTICATED"),
            "403", Set.of("TIANJI_PERMISSION_DENIED"),
            "404", Set.of("TIANJI_RESOURCE_NOT_FOUND"),
            "409", Set.of("TIANJI_TENANT_CONTEXT_CHANGED"),
            "500", Set.of("TIANJI_INTERNAL_ERROR"),
            "503", Set.of("TIANJI_DEPENDENCY_UNAVAILABLE"));
    private static final Map<String, Set<String>> CANCEL_ERRORS = Map.of(
            "400", Set.of("TIANJI_INVALID_COMMAND"),
            "401", Set.of("TIANJI_UNAUTHENTICATED"),
            "403", Set.of("TIANJI_PERMISSION_DENIED"),
            "404", Set.of("TIANJI_RESOURCE_NOT_FOUND"),
            "409", Set.of("TIANJI_IDEMPOTENCY_CONFLICT", "TIANJI_PROCESS_VERSION_CONFLICT",
                    "TIANJI_PROCESS_CANCEL_BLOCKED", "TIANJI_TENANT_CONTEXT_CHANGED"),
            "413", Set.of("TIANJI_REQUEST_TOO_LARGE"),
            "500", Set.of("TIANJI_INTERNAL_ERROR"),
            "503", Set.of("TIANJI_DEPENDENCY_UNAVAILABLE"));
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void openapi_publishes_exact_operations_errors_security_and_default_off_provider_boundary() throws Exception {
        JsonNode root = root();
        assertEquals("3.1.0", root.path("openapi").asText());
        assertEquals("https://json-schema.org/draft/2020-12/schema", root.path("jsonSchemaDialect").asText());
        assertEquals(Set.of(ProcessControlClient.CONTROL_PATH, ProcessControlClient.CANCEL_PATH), names(root.path("paths")));

        JsonNode controlItem = root.path("paths").path(ProcessControlClient.CONTROL_PATH);
        JsonNode cancelItem = root.path("paths").path(ProcessControlClient.CANCEL_PATH);
        assertEquals(Set.of("get"), names(controlItem));
        assertEquals(Set.of("post"), names(cancelItem));
        JsonNode control = controlItem.path("get");
        JsonNode cancel = cancelItem.path("post");
        assertEquals("getProcessControl", control.path("operationId").asText());
        assertEquals("cancelProcessInstance", cancel.path("operationId").asText());
        assertPathParameter(control);
        assertPathParameter(cancel);
        assertTrue(control.path("requestBody").isMissingNode());
        assertTrue(cancel.path("requestBody").path("required").asBoolean());
        assertEquals("#/components/schemas/CancelProcessCommand", cancel.path("requestBody").path("content")
                .path("application/json").path("schema").path("$ref").asText());

        assertResponses(control, "ProcessControlSnapshotDTO", CONTROL_ERRORS);
        assertResponses(cancel, "CancelProcessResult", CANCEL_ERRORS);
        assertOperationMetadata(control, ProcessControlClient.CONTROL_RESOURCE_KEY);
        assertOperationMetadata(cancel, ProcessControlClient.CANCEL_RESOURCE_KEY);
        String controlScheme = assertOAuthSecurity(control, root.path("components").path("securitySchemes"));
        assertEquals(controlScheme, assertOAuthSecurity(cancel, root.path("components").path("securitySchemes")));

        String description = root.path("info").path("description").asText().toLowerCase(java.util.Locale.ROOT);
        assertTrue(description.contains("stellar.tianji.process.cancellation.enabled=false"));
        assertTrue(description.contains("step 4"));
        assertTrue(description.contains("provider source is implemented"));
        assertTrue(description.contains("runtime") && description.contains("unverified"));
        assertFalse(description.contains("pending step 4"));
    }

    @Test
    void openapi_schemas_match_records_enums_closed_shapes_bounds_and_independent_times() throws Exception {
        JsonNode schemas = root().path("components").path("schemas");
        Map<String, Class<?>> records = Map.of(
                "ProcessActiveWaitDTO", ProcessActiveWaitDTO.class,
                "ProcessControlSnapshotDTO", ProcessControlSnapshotDTO.class,
                "CancelProcessCommand", CancelProcessCommand.class,
                "CancelProcessResult", CancelProcessResult.class,
                "ProcessInstanceDTO", ProcessInstanceDTO.class,
                "OrchestrationErrorDTO", OrchestrationErrorDTO.class);
        Set<String> expectedSchemas = new LinkedHashSet<>(records.keySet());
        expectedSchemas.addAll(Set.of("ProcessActiveWaitKindEnum", "ProcessControlActionEnum",
                "ProcessControlBlockReasonEnum", "ProcessInstanceStatusEnum"));
        assertTrue(names(schemas).containsAll(expectedSchemas));

        records.forEach((name, type) -> {
            JsonNode schema = schemas.path(name);
            assertTrue(schema.isObject(), name);
            assertEquals("object", schema.path("type").asText(), name);
            assertFalse(schema.path("additionalProperties").asBoolean(true), name);
            Set<String> fields = Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getName).collect(Collectors.toUnmodifiableSet());
            assertEquals(fields, names(schema.path("properties")), name);
            assertEquals(type == OrchestrationErrorDTO.class ? Set.of("code") : fields,
                    values(schema.path("required")), name);
        });

        assertEnum(schemas, "ProcessActiveWaitKindEnum",
                Arrays.stream(ProcessActiveWaitKindEnum.values()).map(Enum::name).toArray(String[]::new));
        assertEnum(schemas, "ProcessControlActionEnum",
                Arrays.stream(ProcessControlActionEnum.values()).map(Enum::name).toArray(String[]::new));
        assertEnum(schemas, "ProcessControlBlockReasonEnum",
                Arrays.stream(ProcessControlBlockReasonEnum.values()).map(Enum::name).toArray(String[]::new));
        assertEnum(schemas, "ProcessInstanceStatusEnum",
                Arrays.stream(ProcessInstanceStatusEnum.values()).map(Enum::name).toArray(String[]::new));

        JsonNode wait = schemas.path("ProcessActiveWaitDTO").path("properties");
        assertEquals(1, wait.path("activityKey").path("minLength").asInt());
        assertEquals(128, wait.path("activityKey").path("maxLength").asInt());
        assertEquals(1, wait.path("occurrences").path("minimum").asInt());
        assertEquals(256, wait.path("occurrences").path("maximum").asInt());
        assertEquals("#/components/schemas/ProcessActiveWaitKindEnum", wait.path("kind").path("$ref").asText());

        JsonNode snapshot = schemas.path("ProcessControlSnapshotDTO").path("properties");
        assertEquals("#/components/schemas/ProcessInstanceDTO", snapshot.path("process").path("$ref").asText());
        assertEquals("array", snapshot.path("activeWaits").path("type").asText());
        assertEquals(256, snapshot.path("activeWaits").path("maxItems").asInt());
        assertTrue(snapshot.path("activeWaits").path("uniqueItems").asBoolean());
        assertEquals("#/components/schemas/ProcessActiveWaitDTO",
                snapshot.path("activeWaits").path("items").path("$ref").asText());
        assertEquals(1, snapshot.path("allowedActions").path("maxItems").asInt());
        assertTrue(snapshot.path("allowedActions").path("uniqueItems").asBoolean());
        assertEquals("#/components/schemas/ProcessControlActionEnum",
                snapshot.path("allowedActions").path("items").path("$ref").asText());
        assertHash(snapshot.path("version"));
        assertDateTime(snapshot.path("observedAt"));

        JsonNode command = schemas.path("CancelProcessCommand").path("properties");
        assertEquals(255, command.path("instanceId").path("maxLength").asInt());
        assertEquals(128, command.path("commandId").path("maxLength").asInt());
        assertHash(command.path("expectedVersion"));
        assertEquals(1, command.path("reason").path("minLength").asInt());
        assertEquals(512, command.path("reason").path("maxLength").asInt());

        JsonNode resultSchema = schemas.path("CancelProcessResult");
        JsonNode result = resultSchema.path("properties");
        assertHash(result.path("resultingVersion"));
        assertDateTime(result.path("finishedAt"));
        assertDateTime(result.path("decidedAt"));
        assertFalse(allowsNull(result.path("finishedAt")));
        assertFalse(allowsNull(result.path("decidedAt")));
        assertFalse(resultSchema.has("if"));
        assertFalse(resultSchema.has("then"));
        assertFalse(resultSchema.has("dependentSchemas"));
        assertFalse(resultSchema.toString().toLowerCase(java.util.Locale.ROOT).contains("chronolog"));

        JsonNode process = schemas.path("ProcessInstanceDTO").path("properties");
        assertDateTime(process.path("submittedAt"));
        assertDateTime(process.path("finishedAt"));
        assertTrue(allowsNull(process.path("finishedAt")));
        assertTrue(allowsNull(schemas.path("OrchestrationErrorDTO").path("properties").path("correlationId")));
    }

    @Test
    void every_local_reference_resolves_without_an_alternative_unscoped_security_requirement() throws Exception {
        JsonNode root = root();
        JsonNode schemas = root.path("components").path("schemas");
        List<String> refs = new ArrayList<>();
        collectRefs(root, refs);
        assertFalse(refs.isEmpty());
        refs.forEach(ref -> {
            assertTrue(ref.startsWith("#/components/schemas/"), ref);
            assertTrue(schemas.path(ref.substring("#/components/schemas/".length())).isObject(), ref);
        });

        for (String path : List.of(ProcessControlClient.CONTROL_PATH, ProcessControlClient.CANCEL_PATH)) {
            JsonNode item = root.path("paths").path(path);
            JsonNode operation = item.path(item.has("get") ? "get" : "post");
            JsonNode security = operation.path("security");
            assertTrue(security.isArray() && security.size() == 1, path);
            JsonNode requirement = security.get(0);
            assertEquals(1, names(requirement).size(), path);
            String scheme = names(requirement).iterator().next();
            assertEquals(SCOPES, values(requirement.path(scheme)), path);
        }
    }

    private JsonNode root() throws Exception {
        URL resource = ProcessControlOpenApiContractTest.class.getResource(
                "/META-INF/tianji/openapi/process-control-v1.json");
        assertNotNull(resource, "process-control-v1.json must be packaged");
        try (InputStream input = resource.openStream()) {
            return mapper.readTree(input.readAllBytes());
        }
    }

    private static void assertPathParameter(JsonNode operation) {
        JsonNode parameter = null;
        for (JsonNode candidate : operation.path("parameters")) {
            if ("instanceId".equals(candidate.path("name").asText()) && "path".equals(candidate.path("in").asText())) {
                parameter = candidate;
            }
        }
        assertNotNull(parameter, "required instanceId path parameter");
        assertTrue(parameter.path("required").asBoolean());
        assertEquals("string", parameter.path("schema").path("type").asText());
        assertEquals(1, parameter.path("schema").path("minLength").asInt());
        assertEquals(255, parameter.path("schema").path("maxLength").asInt());
    }

    private static void assertResponses(JsonNode operation, String successSchema,
                                        Map<String, Set<String>> errors) {
        Set<String> statuses = new LinkedHashSet<>(errors.keySet());
        statuses.add("200");
        assertEquals(statuses, names(operation.path("responses")));
        JsonNode success = operation.path("responses").path("200");
        assertEquals("#/components/schemas/" + successSchema,
                success.path("content").path("application/json").path("schema").path("$ref").asText());
        assertEquals("no-store", success.path("headers").path("Cache-Control").path("schema").path("const").asText());
        errors.forEach((status, codes) -> {
            JsonNode schema = operation.path("responses").path(status).path("content")
                    .path("application/json").path("schema");
            assertEquals("#/components/schemas/OrchestrationErrorDTO", schema.path("$ref").asText(), status);
            assertEquals(codes, values(schema.path("properties").path("code").path("enum")), status);
        });
    }

    private static void assertOperationMetadata(JsonNode operation, String resourceKey) {
        assertEquals(PERMISSIONS, values(operation.path("x-required-permissions")));
        assertEquals(resourceKey, operation.path("x-resource-key").asText());
        List<Set<String>> principalMetadata = new ArrayList<>();
        operation.properties().forEach(entry -> {
            if (entry.getKey().startsWith("x-") && entry.getKey().toLowerCase(java.util.Locale.ROOT).contains("principal")
                    && entry.getValue().isArray()) {
                principalMetadata.add(values(entry.getValue()));
            }
        });
        assertTrue(principalMetadata.contains(PRINCIPALS));
    }

    private static String assertOAuthSecurity(JsonNode operation, JsonNode schemes) {
        JsonNode security = operation.path("security");
        assertTrue(security.isArray() && security.size() == 1);
        JsonNode requirement = security.get(0);
        assertEquals(1, names(requirement).size());
        String name = names(requirement).iterator().next();
        assertEquals(SCOPES, values(requirement.path(name)));
        JsonNode scheme = schemes.path(name);
        assertEquals("oauth2", scheme.path("type").asText());
        JsonNode authorizationCode = scheme.path("flows").path("authorizationCode");
        JsonNode clientCredentials = scheme.path("flows").path("clientCredentials");
        assertTrue(authorizationCode.path("authorizationUrl").asText().contains("identity.example"));
        assertTrue(authorizationCode.path("tokenUrl").asText().contains("identity.example"));
        assertTrue(clientCredentials.path("tokenUrl").asText().contains("identity.example"));
        assertEquals(SCOPES, names(authorizationCode.path("scopes")));
        assertEquals(SCOPES, names(clientCredentials.path("scopes")));
        return name;
    }

    private static void assertHash(JsonNode schema) {
        assertEquals("string", schema.path("type").asText());
        assertEquals(64, schema.path("minLength").asInt());
        assertEquals(64, schema.path("maxLength").asInt());
        assertEquals("^[0-9a-f]{64}$", schema.path("pattern").asText());
    }

    private static void assertDateTime(JsonNode schema) {
        if (schema.path("type").isArray()) {
            assertTrue(values(schema.path("type")).contains("string"));
        } else {
            assertEquals("string", schema.path("type").asText());
        }
        assertEquals("date-time", schema.path("format").asText());
    }

    private static boolean allowsNull(JsonNode schema) {
        if (schema.path("type").isArray() && values(schema.path("type")).contains("null")) return true;
        for (JsonNode candidate : schema.path("anyOf")) {
            if ("null".equals(candidate.path("type").asText())) return true;
        }
        return false;
    }

    private static void assertEnum(JsonNode schemas, String name, String... expected) {
        JsonNode schema = schemas.path(name);
        assertEquals("string", schema.path("type").asText(), name);
        assertArrayEquals(expected, StreamSupport.stream(schema.path("enum").spliterator(), false)
                .map(JsonNode::asText).toArray(String[]::new), name);
    }

    private static Set<String> names(JsonNode object) {
        Set<String> result = new LinkedHashSet<>();
        object.propertyNames().forEach(result::add);
        return Set.copyOf(result);
    }

    private static Set<String> values(JsonNode array) {
        Set<String> result = new LinkedHashSet<>();
        array.forEach(value -> result.add(value.asText()));
        return Set.copyOf(result);
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
}
