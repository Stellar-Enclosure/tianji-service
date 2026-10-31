package top.egon.cola.platform.tianji.process.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.ClaimHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetHumanTaskQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ListHumanTasksQuery;
import top.egon.cola.platform.tianji.contract.orchestration.TransferHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.UnclaimHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.VerifyApprovalQuery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Set;

/** Closed task request shapes without changing existing Process/Worker JSON behavior. */
@ControllerAdvice(assignableTypes = {HumanTaskController.class, ApprovalVerificationController.class})
@ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
public final class HumanTaskRequestBodyAdvice extends RequestBodyAdviceAdapter {
    static final int MAX_BYTES = 128 * 1024;
    private static final Set<Type> TYPES = Set.of(GetHumanTaskQuery.class, ListHumanTasksQuery.class, ClaimHumanTaskCommand.class, UnclaimHumanTaskCommand.class, TransferHumanTaskCommand.class, CompleteHumanTaskCommand.class, VerifyApprovalQuery.class);
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(4).maxStringLength(4096).maxNameLength(64).maxNumberLength(20).maxTokenCount(256).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) { return TYPES.contains(type); }
    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        if (input.getHeaders().getContentLength() > MAX_BYTES) throw new ProcessRequestBodyAdvice.TooLarge();
        byte[] body = input.getBody().readNBytes(MAX_BYTES + 1); if (body.length > MAX_BYTES) throw new ProcessRequestBodyAdvice.TooLarge();
        try {
            var root = JSON.readTree(body); if (root == null || !root.isObject()) throw invalid();
            if (type == VerifyApprovalQuery.class) {
                strings(root, Set.of("policyRef", "policyBindingSha256", "approvalReceiptId", "requesterType", "requesterSubjectId"));
                var source = root.get("sourceStart"); if (source == null || !source.isObject()) throw invalid();
                strings(source, Set.of("definitionKey", "releaseVersion", "businessKey", "commandId", "correlationId"));
                if (!source.path("variables").isObject()) throw invalid();
            } else if (type == ListHumanTasksQuery.class) {
                strings(root, Set.of("scopeType", "selection")); var limit = root.get("limit"); if (limit == null || !limit.isIntegralNumber() || !limit.canConvertToInt()) throw invalid();
                for (var name : Set.of("releaseVersion", "scopeId", "taskDefinitionKey", "afterTaskId")) { var value = root.get(name); if (value != null && !value.isNull() && !value.isString()) throw invalid(); }
            } else {
                strings(root, type == GetHumanTaskQuery.class ? Set.of("taskId") : type == TransferHumanTaskCommand.class ? Set.of("commandId", "taskId", "expectedVersion", "targetLane") : Set.of("commandId", "taskId", "expectedVersion"));
                if (type == CompleteHumanTaskCommand.class) { var form = root.get("form"); if (form == null || !form.isObject()) throw invalid(); }
            }
            JSON.readValue(body, (Class<?>) type);
        }
        catch (RuntimeException malformed) { throw new IllegalArgumentException("Human task request is invalid"); }
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }
    private static void strings(JsonNode node, Set<String> fields) { for (var field : fields) { var value = node.get(field); if (value == null || !value.isString()) throw invalid(); } }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Human task request is invalid"); }
}
