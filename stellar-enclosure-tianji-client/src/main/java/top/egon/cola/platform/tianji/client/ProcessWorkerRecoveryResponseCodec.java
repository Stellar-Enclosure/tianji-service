package top.egon.cola.platform.tianji.client;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.ReasonEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.ReportedFailureEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.StatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailuresDTO;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerResult;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

final class ProcessWorkerRecoveryResponseCodec {
    private static final int MAX_BYTES = 2 * 1_048_576;
    private static final int MAX_FAILURES = 128;
    private static final Set<String> OBSERVATION_FIELDS = Set.of("instanceId", "observedAt", "failures");
    private static final Set<String> ROW_FIELDS = Set.of("jobId", "activityId", "nativeDeadletter",
            "status", "reason", "reportedFailure", "expectedVersion");
    private static final Set<String> RESULT_FIELDS = Set.of("instanceId", "jobId", "occurrenceId", "commandId",
            "status", "retries", "decidedAt");
    private static final Set<String> ERROR_FIELDS = Set.of("code", "correlationId");
    private static final Map<Integer, Set<String>> COMMON_ERRORS = Map.of(
            400, Set.of("TIANJI_INVALID_COMMAND"),
            401, Set.of("TIANJI_UNAUTHENTICATED"),
            403, Set.of("TIANJI_PERMISSION_DENIED"),
            404, Set.of("TIANJI_RESOURCE_NOT_FOUND"),
            409, Set.of("TIANJI_TENANT_CONTEXT_CHANGED"),
            500, Set.of("TIANJI_INTERNAL_ERROR"),
            503, Set.of("TIANJI_DEPENDENCY_UNAVAILABLE"));
    private static final Map<Integer, Set<String>> POST_ONLY_ERRORS = Map.of(
            409, Set.of("TIANJI_IDEMPOTENCY_CONFLICT", "TIANJI_PROCESS_VERSION_CONFLICT", "TIANJI_PROCESS_WORKER_RECOVERY_BLOCKED"),
            413, Set.of("TIANJI_REQUEST_TOO_LARGE"));

    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(1024)
                            .maxNameLength(128).maxNumberLength(32).maxTokenCount(4096).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();

    ProcessWorkerFailuresDTO failures(byte[] body) {
        try {
            var root = exactKeys(tree(body), OBSERVATION_FIELDS);
            var failures = root.path("failures");
            if (!failures.isArray() || failures.size() > MAX_FAILURES) throw invalid();
            var rows = new ArrayList<ProcessWorkerFailureDTO>(failures.size());
            for (var row : failures) {
                exactKeys(row, ROW_FIELDS);
                var nativeDeadletter = row.path("nativeDeadletter");
                if (!nativeDeadletter.isBoolean()) throw invalid();
                rows.add(new ProcessWorkerFailureDTO(text(row, "jobId"), nullableText(row, "activityId"),
                        nativeDeadletter.asBoolean(), StatusEnum.valueOf(text(row, "status")),
                        ReasonEnum.valueOf(text(row, "reason")), ReportedFailureEnum.valueOf(text(row, "reportedFailure")),
                        nullableText(row, "expectedVersion")));
            }
            return new ProcessWorkerFailuresDTO(text(root, "instanceId"), canonicalInstant(text(root, "observedAt")), rows);
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    RecoverProcessWorkerResult recovery(byte[] body) {
        try {
            var root = exactKeys(tree(body), RESULT_FIELDS);
            var retries = root.path("retries");
            if (!retries.isIntegralNumber() || !BigInteger.ONE.equals(retries.bigIntegerValue())) throw invalid();
            return new RecoverProcessWorkerResult(text(root, "instanceId"), text(root, "jobId"), text(root, "occurrenceId"),
                    text(root, "commandId"), RecoverProcessWorkerResult.StatusEnum.valueOf(text(root, "status")),
                    1, canonicalInstant(text(root, "decidedAt")));
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    String errorCode(byte[] body, int status, boolean recoveryCommand) {
        try {
            var root = exactKeys(tree(body), ERROR_FIELDS);
            if (!root.path("correlationId").isNull()) return null;
            String code = text(root, "code");
            if (COMMON_ERRORS.getOrDefault(status, Set.of()).contains(code)) return code;
            if (recoveryCommand && POST_ONLY_ERRORS.getOrDefault(status, Set.of()).contains(code)) return code;
            return null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private JsonNode tree(byte[] body) {
        if (body == null || body.length == 0 || body.length > MAX_BYTES) throw invalid();
        try {
            String utf8 = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            return mapper.readTree(utf8);
        } catch (CharacterCodingException malformed) {
            throw invalid();
        }
    }

    private static JsonNode exactKeys(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject() || !fields.equals(Set.copyOf(node.propertyNames()))) throw invalid();
        return node;
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isString()) throw invalid();
        return value.asText();
    }

    private static String nullableText(JsonNode node, String field) {
        return node.path(field).isNull() ? null : text(node, field);
    }

    private static Instant canonicalInstant(String value) {
        Instant instant = Instant.parse(value);
        if (!instant.toString().equals(value)) throw invalid();
        return instant;
    }

    private static ProcessClientException invalid() {
        return new ProcessClientException(0, "TIANJI_INVALID_RESPONSE", null, null);
    }
}
