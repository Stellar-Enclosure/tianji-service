package top.egon.cola.platform.tianji.client;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;

/** Closed task-only decoder; existing Process/Decision/Worker response compatibility is preserved. */
final class HumanTaskResponseCodec {
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).build();

    HumanTaskDTO task(byte[] body) { var node = mapper.readTree(body); task(node); return mapper.treeToValue(node, HumanTaskDTO.class); }
    HumanTaskPageDTO page(byte[] body) {
        var node = mapper.readTree(body); strings(node, "scopeType", "selection");
        if (!node.path("items").isArray() || node.path("items").size() > 50 || node.path("nextAfterTaskId").isMissingNode()) throw invalid();
        optionalString(node.path("nextAfterTaskId")); node.path("items").forEach(HumanTaskResponseCodec::task); return mapper.treeToValue(node, HumanTaskPageDTO.class);
    }
    HumanTaskActionReceiptDTO receipt(byte[] body) {
        var node = mapper.readTree(body); strings(node, "commandId", "taskId", "action", "scopeType", "scopeId", "releaseVersion", "definitionKey", "taskDefinitionKey", "kind", "lane", "previousVersion", "occurredAt");
        optionalString(node.path("resultingVersion")); optionalString(node.path("approvalReceiptId")); var approved = node.path("approved");
        if (!approved.isMissingNode() && !approved.isNull() && !approved.isBoolean()) throw invalid(); return mapper.treeToValue(node, HumanTaskActionReceiptDTO.class);
    }
    VerifiedApprovalDTO approval(byte[] body) {
        var node = mapper.readTree(body);
        strings(node, "policyRef", "policyBindingSha256", "requestSha256", "approvalReceiptId", "requesterType", "requesterSubjectId", "sourceStartCommandId",
                "scopeId", "taskId", "releaseVersion", "definitionKey", "definitionSha256", "taskDefinitionKey", "sourceRequestSha256", "occurredAt", "verifiedAt", "expiresAt");
        if (!node.path("approved").isBoolean()) throw invalid(); return mapper.treeToValue(node, VerifiedApprovalDTO.class);
    }
    private static void task(JsonNode node) {
        strings(node, "taskId", "scopeType", "scopeId", "releaseVersion", "definitionKey", "taskDefinitionKey", "name", "kind", "version", "lane", "createdAt"); optionalString(node.path("dueAt"));
        optionalString(node.path("approvalField"));
        for (var key : new String[]{"assigned", "assignedToMe", "suspended"}) if (!node.path(key).isBoolean()) throw invalid();
        if (!node.path("availableLanes").isArray() || node.path("availableLanes").size() > 8 || !node.path("formSchema").isObject() || node.path("formSchema").size() > 16) throw invalid();
        for (var lane : node.path("availableLanes")) if (!lane.isString()) throw invalid(); for (var value : node.path("formSchema")) if (!value.isString()) throw invalid();
    }
    private static void strings(JsonNode node, String... names) { if (!node.isObject()) throw invalid(); for (var name : names) if (!node.path(name).isString()) throw invalid(); }
    private static void optionalString(JsonNode node) { if (!node.isMissingNode() && !node.isNull() && !node.isString()) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Human task response is invalid"); }
}
