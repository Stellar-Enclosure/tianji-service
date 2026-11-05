package top.egon.cola.platform.tianji.client;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptDTO;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class ProcessStartReceiptResponseCodec {
    static final int MAX_BYTES = 4096;
    private static final Set<String> RECEIPT_FIELDS = Set.of("instanceId", "status", "recordedAt");
    private static final Set<String> ERROR_FIELDS = Set.of("code", "correlationId");
    private static final Pattern UTC_MICROS = Pattern.compile(
            "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?Z$");
    private static final Map<Integer, String> ERROR_CODES = Map.of(
            400, "TIANJI_INVALID_COMMAND",
            401, "TIANJI_UNAUTHENTICATED",
            403, "TIANJI_PERMISSION_DENIED",
            404, "TIANJI_RESOURCE_NOT_FOUND",
            409, "TIANJI_TENANT_CONTEXT_CHANGED",
            503, "TIANJI_DEPENDENCY_UNAVAILABLE");

    private static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(255)
                            .maxNameLength(64).maxNumberLength(32).maxTokenCount(32).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private ProcessStartReceiptResponseCodec() { }

    static ProcessStartReceiptDTO receipt(byte[] body) {
        var root = exactObject(body, RECEIPT_FIELDS);
        var status = ProcessStartReceiptDTO.StatusEnum.valueOf(text(root, "status"));
        String recordedAt = text(root, "recordedAt");
        if (!UTC_MICROS.matcher(recordedAt).matches()) throw invalid();
        return new ProcessStartReceiptDTO(text(root, "instanceId"), status, OffsetDateTime.parse(recordedAt).toInstant());
    }

    static String errorCode(byte[] body, int status) {
        try {
            var root = exactObject(body, ERROR_FIELDS);
            String code = text(root, "code");
            return root.path("correlationId").isNull() && code.equals(ERROR_CODES.get(status)) ? code : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static JsonNode exactObject(byte[] body, Set<String> fields) {
        if (body == null || body.length == 0 || body.length > MAX_BYTES) throw invalid();
        try {
            var root = MAPPER.readTree(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString());
            if (root == null || !root.isObject() || !fields.equals(Set.copyOf(root.propertyNames()))) throw invalid();
            return root;
        } catch (CharacterCodingException malformed) {
            throw invalid();
        }
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isString()) throw invalid();
        return value.asString();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Process START receipt response is invalid");
    }
}
