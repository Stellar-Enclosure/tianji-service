package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationErrorDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailuresDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerRecoveryClient;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerCommand;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerResult;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessWorkerRecoveryOpenApiContractTest {
    private static final String RESOURCE = "/META-INF/tianji/openapi/process-worker-recovery-v1.json";
    private static final String SCHEMA_PREFIX = "#/components/schemas/";
    private static final Set<String> SCOPES = Set.of(ProcessControlClient.READ_SCOPE,
            ProcessWorkerRecoveryClient.WORKER_RECOVER_SCOPE);
    private static final Set<String> PERMISSIONS = Set.of("TIANJI_PROCESS_READ", "TIANJI_PROCESS_WORKER_RECOVER");
    private static final Map<String, Set<String>> GET_ERRORS = Map.of(
            "400", Set.of("TIANJI_INVALID_COMMAND"),
            "401", Set.of("TIANJI_UNAUTHENTICATED"),
            "403", Set.of("TIANJI_PERMISSION_DENIED"),
            "404", Set.of("TIANJI_RESOURCE_NOT_FOUND"),
            "409", Set.of("TIANJI_TENANT_CONTEXT_CHANGED"),
            "500", Set.of("TIANJI_INTERNAL_ERROR"),
            "503", Set.of("TIANJI_DEPENDENCY_UNAVAILABLE"));
    private static final Map<String, Set<String>> POST_ERRORS = Map.of(
            "400", GET_ERRORS.get("400"), "401", GET_ERRORS.get("401"), "403", GET_ERRORS.get("403"),
            "404", GET_ERRORS.get("404"), "500", GET_ERRORS.get("500"), "503", GET_ERRORS.get("503"),
            "409", Set.of("TIANJI_TENANT_CONTEXT_CHANGED", "TIANJI_IDEMPOTENCY_CONFLICT",
                    "TIANJI_PROCESS_VERSION_CONFLICT", "TIANJI_PROCESS_WORKER_RECOVERY_BLOCKED"),
            "413", Set.of("TIANJI_REQUEST_TOO_LARGE"));
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void openapi_publishes_exact_paths_security_and_status_pairs() throws IOException {
        var root = root();
        assertEquals("3.1.0", root.path("openapi").asText());
        assertEquals("https://json-schema.org/draft/2020-12/schema", root.path("jsonSchemaDialect").asText());
        assertEquals(Set.of(ProcessWorkerRecoveryClient.WORKER_FAILURES_PATH,
                ProcessWorkerRecoveryClient.WORKER_RECOVERIES_PATH), names(root.path("paths")));
        var description = root.path("info").path("description").asText();
        assertTrue(description.contains("stellar.tianji.process.worker-recovery.enabled=false"));
        assertTrue(description.contains("Runtime PostgreSQL, Flowable and IAM acceptance is separate"));
        var get = assertOperation(root, ProcessWorkerRecoveryClient.WORKER_FAILURES_PATH, "get",
                "getProcessWorkerFailures", ProcessWorkerRecoveryClient.WORKER_FAILURES_RESOURCE_KEY,
                "ProcessWorkerFailuresDTO", GET_ERRORS);
        assertTrue(get.path("requestBody").isMissingNode());
        var post = assertOperation(root, ProcessWorkerRecoveryClient.WORKER_RECOVERIES_PATH, "post",
                "recoverProcessWorker", ProcessWorkerRecoveryClient.WORKER_RECOVERIES_RESOURCE_KEY,
                "RecoverProcessWorkerResult", POST_ERRORS);
        var body = post.path("requestBody");
        assertTrue(body.path("required").asBoolean());
        assertEquals(8192, body.path("x-max-request-bytes").asInt());
        assertEquals(SCHEMA_PREFIX + "RecoverProcessWorkerCommand", jsonSchema(body).path("$ref").asText());
        assertTrue(body.path("description").asText().contains("equal the path instanceId exactly"));
        assertTrue(post.path("description").asText().contains("415"));
        assertTrue(post.path("responses").path("415").isMissingNode());

        var schemes = root.path("components").path("securitySchemes");
        assertEquals(Set.of("oauth2"), names(schemes));
        var scheme = schemes.path("oauth2");
        assertEquals("oauth2", scheme.path("type").asText());
        var flows = scheme.path("flows");
        assertEquals(Set.of("authorizationCode", "clientCredentials"), names(flows));
        assertTrue(flows.path("authorizationCode").path("authorizationUrl").asText().contains("identity.example"));
        for (var flow : List.of("authorizationCode", "clientCredentials")) {
            assertEquals(SCOPES, names(flows.path(flow).path("scopes")));
            assertTrue(flows.path(flow).path("tokenUrl").asText().contains("identity.example"));
        }
    }

    @Test
    void openapi_schemas_match_closed_records_enums_and_bounds() throws IOException {
        var schemas = root().path("components").path("schemas");
        assertEquals(Set.of("ProcessWorkerFailureDTO", "ProcessWorkerFailuresDTO", "RecoverProcessWorkerCommand",
                "RecoverProcessWorkerResult", "OrchestrationErrorDTO", "WorkerRecoveryFailureStatusEnum",
                "WorkerRecoveryReasonEnum", "WorkerRecoveryReportedFailureEnum", "WorkerRecoveryResultStatusEnum"), names(schemas));
        var row = assertRecordSchema(schemas, ProcessWorkerFailureDTO.class, Set.of("activityId", "expectedVersion"));
        var observation = assertRecordSchema(schemas, ProcessWorkerFailuresDTO.class, Set.of());
        var command = assertRecordSchema(schemas, RecoverProcessWorkerCommand.class, Set.of());
        var result = assertRecordSchema(schemas, RecoverProcessWorkerResult.class, Set.of());
        var error = assertRecordSchema(schemas, OrchestrationErrorDTO.class, Set.of("correlationId"));

        assertEnum(schemas, "WorkerRecoveryFailureStatusEnum", ProcessWorkerFailureDTO.StatusEnum.class);
        assertEnum(schemas, "WorkerRecoveryReasonEnum", ProcessWorkerFailureDTO.ReasonEnum.class);
        assertEnum(schemas, "WorkerRecoveryReportedFailureEnum", ProcessWorkerFailureDTO.ReportedFailureEnum.class);
        assertEnum(schemas, "WorkerRecoveryResultStatusEnum", RecoverProcessWorkerResult.StatusEnum.class);
        assertEquals("RETRY_SCHEDULED", schemas.path("WorkerRecoveryResultStatusEnum").path("const").asText());
        assertInstance(observation.path("instanceId"));
        assertInstance(command.path("instanceId"));
        assertInstance(result.path("instanceId"));
        for (var id : List.of(row.path("jobId"), command.path("jobId"), result.path("jobId"), result.path("occurrenceId"))) {
            assertSafeText(id, 255);
        }
        assertSafeText(nonnullBranch(row.path("activityId")), 128);
        assertHash(nonnullBranch(row.path("expectedVersion")));
        assertHash(command.path("expectedVersion"));
        assertSafeText(command.path("reason"), 512);
        for (var key : List.of(command.path("commandId"), result.path("commandId"))) {
            assertEquals("string", key.path("type").asText());
            assertEquals(1, key.path("minLength").asInt());
            assertEquals(128, key.path("maxLength").asInt());
            assertEquals("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$", key.path("pattern").asText());
        }
        assertEquals("boolean", row.path("nativeDeadletter").path("type").asText());
        assertEquals(SCHEMA_PREFIX + "WorkerRecoveryFailureStatusEnum", row.path("status").path("$ref").asText());
        assertEquals(SCHEMA_PREFIX + "WorkerRecoveryReasonEnum", row.path("reason").path("$ref").asText());
        assertEquals(SCHEMA_PREFIX + "WorkerRecoveryReportedFailureEnum", row.path("reportedFailure").path("$ref").asText());
        assertEquals(SCHEMA_PREFIX + "WorkerRecoveryResultStatusEnum", result.path("status").path("$ref").asText());
        assertEquals("jobId", result.path("occurrenceId").path("x-equals-field").asText());
        assertEquals("integer", result.path("retries").path("type").asText());
        assertTrue(result.path("retries").path("const").isIntegralNumber());
        assertEquals(1, result.path("retries").path("const").asInt());
        assertEquals("string", error.path("code").path("type").asText());
        assertEquals("null", error.path("correlationId").path("type").asText());
        assertUtc(observation.path("observedAt"));
        assertUtc(result.path("decidedAt"));

        var failures = observation.path("failures");
        assertEquals("array", failures.path("type").asText());
        assertEquals(128, failures.path("maxItems").asInt());
        assertTrue(failures.path("uniqueItems").asBoolean());
        assertEquals("jobId", failures.path("x-unique-by").asText());
        assertEquals("unsigned-utf8-jobId", failures.path("x-sort-order").asText());
        assertEquals(SCHEMA_PREFIX + "ProcessWorkerFailureDTO", failures.path("items").path("$ref").asText());

        var all = schemas.path("ProcessWorkerFailureDTO").path("allOf");
        assertEquals(5, all.size());
        var eligible = consequence(all, "status", "RETRY_AVAILABLE");
        assertEquals(Set.of("nativeDeadletter", "activityId", "expectedVersion", "reason", "reportedFailure"), names(eligible));
        assertBooleanConst(eligible.path("nativeDeadletter"), true);
        assertEquals("string", eligible.path("activityId").path("type").asText());
        assertEquals("string", eligible.path("expectedVersion").path("type").asText());
        assertEquals("NONE", eligible.path("reason").path("const").asText());
        assertEquals("RETRYABLE", eligible.path("reportedFailure").path("const").asText());

        var manual = consequence(all, "status", "MANUAL_REVIEW");
        var historical = consequence(all, "status", "HISTORICAL_FAILURE");
        assertEquals(Set.of("nativeDeadletter", "expectedVersion", "reason"), names(manual));
        assertEquals(names(manual), names(historical));
        assertBooleanConst(manual.path("nativeDeadletter"), true);
        assertBooleanConst(historical.path("nativeDeadletter"), false);
        assertEquals("null", manual.path("expectedVersion").path("type").asText());
        assertEquals("null", historical.path("expectedVersion").path("type").asText());
        var manualReasons = new HashSet<>(values(schemas.path("WorkerRecoveryReasonEnum").path("enum")));
        manualReasons.removeAll(Set.of("NONE", "NO_NATIVE_DEADLETTER"));
        assertEquals(manualReasons, values(manual.path("reason").path("enum")));
        assertEquals(Set.of("NO_NATIVE_DEADLETTER", "UNKNOWN_OUTCOME", "BUSINESS_REJECTED", "HISTORY_INCOMPLETE"),
                values(historical.path("reason").path("enum")));
        assertEquals("UNKNOWN", consequence(all, "reason", "UNKNOWN_OUTCOME").path("reportedFailure").path("const").asText());
        assertEquals("BUSINESS_REJECTED",
                consequence(all, "reason", "BUSINESS_REJECTED").path("reportedFailure").path("const").asText());
    }

    @Test
    void every_local_reference_resolves() throws IOException {
        var root = root();
        var schemas = root.path("components").path("schemas");
        var refs = new ArrayList<String>();
        collectRefs(root, refs);
        assertFalse(refs.isEmpty());
        for (String ref : refs) {
            assertTrue(ref.startsWith(SCHEMA_PREFIX), ref);
            assertTrue(schemas.path(ref.substring(SCHEMA_PREFIX.length())).isObject(), ref);
        }
    }

    private static JsonNode root() throws IOException {
        var url = ProcessWorkerRecoveryOpenApiContractTest.class.getResource(RESOURCE);
        assertNotNull(url, "packaged Worker recovery contract");
        try (var input = url.openStream()) {
            return JSON.readTree(input.readAllBytes());
        }
    }

    private static JsonNode assertOperation(JsonNode root, String path, String verb, String operationId,
                                            String resource, String success, Map<String, Set<String>> errors) {
        var item = root.path("paths").path(path);
        assertEquals(Set.of(verb), names(item));
        var operation = item.path(verb);
        assertEquals(operationId, operation.path("operationId").asText());
        assertEquals(resource, operation.path("x-resource-key").asText());
        assertEquals(PERMISSIONS, values(operation.path("x-required-permissions")));
        assertEquals(Set.of("USER", "SERVICE"), values(operation.path("x-allowed-principals")));
        var security = operation.path("security");
        assertTrue(security.isArray());
        assertEquals(1, security.size());
        assertEquals(Set.of("oauth2"), names(security.get(0)));
        assertEquals(SCOPES, values(security.get(0).path("oauth2")));

        var parameters = new HashMap<String, JsonNode>();
        assertTrue(operation.path("parameters").isArray());
        assertEquals(2, operation.path("parameters").size());
        operation.path("parameters").forEach(node -> parameters.put(node.path("name").asText(), node));
        assertEquals(Set.of("instanceId", "X-Egon-Tianji-Tenant"), parameters.keySet());
        var id = parameters.get("instanceId");
        assertEquals("path", id.path("in").asText());
        assertTrue(id.path("required").asBoolean());
        assertInstance(id.path("schema"));
        var tenant = parameters.get("X-Egon-Tianji-Tenant");
        assertEquals("header", tenant.path("in").asText());
        assertTrue(tenant.path("required").isBoolean());
        assertFalse(tenant.path("required").asBoolean());
        assertEquals("string", tenant.path("schema").path("type").asText());
        assertEquals("^[1-9][0-9]{0,18}$", tenant.path("schema").path("pattern").asText());
        assertEquals("9223372036854775807", tenant.path("schema").path("x-java-long-max").asText());

        var responses = operation.path("responses");
        var statuses = new HashSet<>(errors.keySet());
        statuses.add("200");
        assertEquals(statuses, names(responses));
        for (String status : statuses) {
            var response = responses.path(status);
            var cache = response.path("headers").path("Cache-Control");
            assertTrue(cache.path("required").asBoolean(), status);
            assertEquals("no-store", cache.path("schema").path("const").asText(), status);
            var schema = jsonSchema(response);
            if ("200".equals(status)) {
                assertEquals(SCHEMA_PREFIX + success, schema.path("$ref").asText(), status);
            } else {
                assertEquals(SCHEMA_PREFIX + "OrchestrationErrorDTO", schema.path("$ref").asText(), status);
                assertEquals(errors.get(status), values(schema.path("properties").path("code").path("enum")), status);
            }
        }
        return operation;
    }

    private static JsonNode jsonSchema(JsonNode message) {
        assertEquals(Set.of("application/json"), names(message.path("content")));
        return message.path("content").path("application/json").path("schema");
    }

    private static JsonNode assertRecordSchema(JsonNode schemas, Class<?> record, Set<String> nullableFields) {
        var schema = schemas.path(record.getSimpleName());
        assertEquals("object", schema.path("type").asText());
        assertTrue(schema.path("additionalProperties").isBoolean());
        assertFalse(schema.path("additionalProperties").asBoolean());
        var components = Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
        var properties = schema.path("properties");
        assertEquals(components, names(properties));
        assertEquals(components, values(schema.path("required")));
        var actualNullable = components.stream().filter(name -> allowsNull(properties.path(name))).collect(Collectors.toSet());
        assertEquals(nullableFields, actualNullable);
        return properties;
    }

    private static boolean allowsNull(JsonNode schema) {
        if ("null".equals(schema.path("type").asText())) return true;
        for (var branch : schema.path("anyOf")) {
            if ("null".equals(branch.path("type").asText())) return true;
        }
        return false;
    }

    private static JsonNode nonnullBranch(JsonNode schema) {
        assertEquals(2, schema.path("anyOf").size());
        return schema.path("anyOf").get(0);
    }

    private static void assertInstance(JsonNode schema) {
        assertSafeText(schema, 255);
        assertEquals(Set.of(".", ".."), values(schema.path("not").path("enum")));
    }

    private static void assertSafeText(JsonNode schema, int max) {
        assertEquals("string", schema.path("type").asText());
        assertEquals(1, schema.path("minLength").asInt());
        assertEquals(max, schema.path("maxLength").asInt());
        assertEquals(max, schema.path("x-java-utf16-max-length").asInt());
        assertTrue(schema.path("pattern").asText().contains("\\u007F"));
        assertTrue(schema.path("pattern").asText().contains("\\uD800"));
    }

    private static void assertHash(JsonNode schema) {
        assertEquals("string", schema.path("type").asText());
        assertEquals(64, schema.path("minLength").asInt());
        assertEquals(64, schema.path("maxLength").asInt());
        assertEquals("^[0-9a-f]{64}$", schema.path("pattern").asText());
    }

    private static void assertUtc(JsonNode schema) {
        assertEquals("string", schema.path("type").asText());
        assertEquals("date-time", schema.path("format").asText());
        assertEquals("Z$", schema.path("pattern").asText());
        assertEquals("java.time.Instant.toString", schema.path("x-canonical-format").asText());
    }

    private static void assertEnum(JsonNode schemas, String name, Class<? extends Enum<?>> values) {
        var schema = schemas.path(name);
        assertEquals("string", schema.path("type").asText());
        var expected = Arrays.stream(values.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
        assertEquals(expected, values(schema.path("enum")));
    }

    private static JsonNode consequence(JsonNode all, String field, String value) {
        JsonNode consequence = null;
        for (var branch : all) {
            if (value.equals(branch.path("if").path("properties").path(field).path("const").asText())) {
                assertTrue(consequence == null, "duplicate condition " + value);
                consequence = branch.path("then").path("properties");
            }
        }
        assertNotNull(consequence, "missing condition " + value);
        return consequence;
    }

    private static void assertBooleanConst(JsonNode schema, boolean expected) {
        assertTrue(schema.path("const").isBoolean());
        assertEquals(expected, schema.path("const").asBoolean());
    }

    private static Set<String> names(JsonNode object) {
        assertTrue(object.isObject());
        return Set.copyOf(object.propertyNames());
    }

    private static Set<String> values(JsonNode array) {
        assertTrue(array.isArray());
        var values = new LinkedHashSet<String>();
        array.forEach(node -> {
            assertTrue(node.isString());
            assertTrue(values.add(node.asText()), "duplicate value");
        });
        return values;
    }

    private static void collectRefs(JsonNode node, List<String> refs) {
        if (node.isObject()) {
            node.properties().forEach(property -> {
                if ("$ref".equals(property.getKey())) {
                    assertTrue(property.getValue().isString());
                    refs.add(property.getValue().asText());
                } else {
                    collectRefs(property.getValue(), refs);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectRefs(child, refs));
        }
    }
}
