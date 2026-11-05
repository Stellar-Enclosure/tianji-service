package top.egon.cola.platform.tianji.client;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryCommandClient.CheckpointResumeAcknowledgement;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryCommandClient.CheckpointResumeCommand;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryCommandClient.HistoricalStartReceipt;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryCommandClient.HistoricalStartReceiptCommand;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class AgentRecoveryCommandResponseCodec {
    static final int MAX_BYTES = 16_384;
    private static final String INVALID_RESPONSE = "TIANJI_INVALID_RESPONSE";
    private static final Set<String> ACK_FIELDS = Set.of("runId", "status", "attempt", "revision");
    private static final Set<String> RECEIPT_FIELDS = Set.of("intentId", "dispatchRunId", "instanceId", "recordedAt", "observedAt");
    private static final Set<String> ERROR_FIELDS = Set.of("code", "correlationId", "message", "decisionId", "retryAfter");
    private static final Set<String> CONFLICT_CODES = Set.of("TIANJI_IDEMPOTENCY_CONFLICT", "TIANJI_REVISION_CONFLICT", "TIANJI_TENANT_CONTEXT_CHANGED");
    private static final Map<Integer, String> ERROR_CODES = Map.of(
            400, "TIANJI_INVALID_COMMAND", 401, "TIANJI_UNAUTHENTICATED", 403, "TIANJI_PERMISSION_DENIED",
            404, "TIANJI_RESOURCE_NOT_FOUND", 413, "TIANJI_REQUEST_TOO_LARGE", 503, "TIANJI_DEPENDENCY_UNAVAILABLE");
    private static final Pattern UTC_INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(3).maxStringLength(4096)
                            .maxNameLength(64).maxNumberLength(24).maxTokenCount(32).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    enum Route { RESUME, HISTORICAL }

    private AgentRecoveryCommandResponseCodec() { }

    static CheckpointResumeAcknowledgement acknowledgement(byte[] body, CheckpointResumeCommand command) {
        var root = exact(body, ACK_FIELDS);
        String runId = text(root, "runId");
        long revision = integer(root, "revision");
        if (!runId.equals(command.runId()) || revision != command.expectedRevision() + 2) throw invalid();
        return new CheckpointResumeAcknowledgement(runId, AgentRunStatusEnum.valueOf(text(root, "status")),
                Math.toIntExact(integer(root, "attempt")), revision);
    }

    static HistoricalStartReceipt receipt(byte[] body, HistoricalStartReceiptCommand command) {
        var root = exact(body, RECEIPT_FIELDS);
        String runId = text(root, "dispatchRunId");
        String intent = text(root, "intentId");
        if (!runId.equals(command.runId()) || !intent.equals(command.intentId().toString())) throw invalid();
        return new HistoricalStartReceipt(UUID.fromString(intent), runId, text(root, "instanceId"),
                instant(root, "recordedAt"), instant(root, "observedAt"));
    }

    static String failureCode(HttpResponse<byte[]> response, Route route) {
        var types = response.headers().allValues("Content-Type");
        if (types.size() != 1 || !"application/json".equalsIgnoreCase(types.getFirst().split(";", 2)[0].trim())
                || !List.of("no-store").equals(response.headers().allValues("Cache-Control"))) return INVALID_RESPONSE;
        int status = response.statusCode();
        if (status == 200) return null;
        try {
            var root = read(response.body());
            if (!root.isObject() || !ERROR_FIELDS.containsAll(root.propertyNames())) return INVALID_RESPONSE;
            String code = text(root, "code");
            for (String field : List.of("correlationId", "decisionId", "retryAfter")) {
                if (root.has(field) && !root.get(field).isNull()) text(root, field);
            }
            if (root.has("message")) text(root, "message");
            boolean allowed = switch (status) {
                case 409 -> CONFLICT_CODES.contains(code) || route == Route.HISTORICAL && "TIANJI_SIDE_EFFECT_UNKNOWN".equals(code);
                case 429 -> route == Route.RESUME && "TIANJI_CAPACITY_EXCEEDED".equals(code);
                default -> code.equals(ERROR_CODES.get(status));
            };
            return allowed ? code : INVALID_RESPONSE;
        } catch (RuntimeException malformed) {
            return INVALID_RESPONSE;
        }
    }

    private static JsonNode exact(byte[] body, Set<String> fields) {
        var root = read(body);
        if (!root.isObject() || !fields.equals(Set.copyOf(root.propertyNames()))) throw invalid();
        return root;
    }

    private static JsonNode read(byte[] body) {
        if (body == null || body.length == 0 || body.length > MAX_BYTES) throw invalid();
        try {
            var value = JSON.readTree(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString());
            if (value == null) throw invalid();
            return value;
        } catch (CharacterCodingException malformed) {
            throw invalid();
        }
    }

    private static String text(JsonNode node, String key) {
        var value = node.path(key);
        if (!value.isString()) throw invalid();
        return value.asString();
    }

    private static long integer(JsonNode node, String key) {
        var value = node.path(key);
        if (!value.isIntegralNumber() || !value.canConvertToLong()) throw invalid();
        return value.asLong();
    }

    private static Instant instant(JsonNode node, String key) {
        String value = text(node, key);
        if (!UTC_INSTANT.matcher(value).matches()) throw invalid();
        return OffsetDateTime.parse(value).toInstant();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Agent recovery command response is invalid");
    }
}
