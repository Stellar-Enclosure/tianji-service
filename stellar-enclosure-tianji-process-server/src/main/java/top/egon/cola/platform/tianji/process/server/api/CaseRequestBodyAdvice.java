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
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.ActOnPlanItemCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.StartCaseCommand;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

/** Strict Case input policy is isolated from existing Process and HumanTask request behavior. */
@ControllerAdvice(assignableTypes = CaseController.class)
@ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "cases.enabled"}, havingValue = "true")
public final class CaseRequestBodyAdvice extends RequestBodyAdviceAdapter {
    static final int MAX_BYTES = 64 * 1024;
    static final class TooLarge extends RuntimeException { }
    private static final Map<Type, Set<String>> FIELDS = Map.of(
            StartCaseCommand.class, Set.of("definitionKey", "releaseVersion", "businessKey", "commandId", "correlationId"),
            CaseInstanceQuery.class, Set.of("caseInstanceId"),
            ActOnPlanItemCommand.class, Set.of("commandId", "caseInstanceId", "planItemInstanceId", "expectedVersion", "action"));
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(8).maxStringLength(4096).maxNameLength(64).maxNumberLength(20).maxTokenCount(256).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) { return FIELDS.containsKey(type); }
    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        if (input.getHeaders().getContentLength() > MAX_BYTES) throw new TooLarge();
        byte[] body = input.getBody().readNBytes(MAX_BYTES + 1); if (body.length > MAX_BYTES) throw new TooLarge();
        try {
            var root = JSON.readTree(body); var fields = FIELDS.get(type);
            if (root == null || !root.isObject() || fields == null || root.size() != fields.size()) throw invalid();
            for (String field : fields) { var value = root.get(field); if (value == null || !value.isString()) throw invalid(); }
            JSON.readValue(body, (Class<?>) type);
        } catch (RuntimeException malformed) { throw invalid(); }
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Case request is invalid"); }
}
