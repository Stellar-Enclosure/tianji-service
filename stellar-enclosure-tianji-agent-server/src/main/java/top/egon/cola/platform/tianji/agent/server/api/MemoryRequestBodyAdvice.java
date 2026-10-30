package top.egon.cola.platform.tianji.agent.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.memory.CorrectMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.ForgetMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalQuery;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Set;

/** Closed Memory commands are checked before MVC conversion so missing revision/confirmation flags cannot become defaults. */
@ControllerAdvice(assignableTypes = MemoryController.class)
@ConditionalOnProperty(prefix = "stellar.tianji.agent", name = {"api.enabled", "memory.enabled", "memory.http.enabled"}, havingValue = "true")
public final class MemoryRequestBodyAdvice extends RequestBodyAdviceAdapter {
    static final int MAX_BYTES = 131_072;
    private static final Set<Type> TYPES = Set.of(GetMemoryQuery.class, MemoryRetrievalQuery.class, UpsertMemoryCommand.class, CorrectMemoryCommand.class, ForgetMemoryCommand.class);
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).build();

    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) { return TYPES.contains(type); }

    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        if (input.getHeaders().getContentLength() > MAX_BYTES) throw new AgentRequestBodyAdvice.TooLarge();
        byte[] body = input.getBody().readNBytes(MAX_BYTES + 1); if (body.length > MAX_BYTES) throw new AgentRequestBodyAdvice.TooLarge();
        try {
            var root = JSON.readTree(body); if (root == null || !root.isObject()) throw new IllegalArgumentException();
            strings(root.path("owner"), "type", "id"); strings(root, "scopeId");
            if (type != MemoryRetrievalQuery.class) strings(root, "memoryId");
            if (type == UpsertMemoryCommand.class || type == CorrectMemoryCommand.class || type == ForgetMemoryCommand.class) {
                strings(root, "commandId");
                if (!root.path("expectedRevision").isIntegralNumber() || !root.path("expectedRevision").canConvertToLong()) throw new IllegalArgumentException();
            }
            if (type == UpsertMemoryCommand.class) { strings(root, "kind", "sensitivity", "retentionPolicyRef"); if (!root.path("confirmed").isBoolean()) throw new IllegalArgumentException(); }
            if (type == CorrectMemoryCommand.class || type == ForgetMemoryCommand.class) strings(root, "reasonCode");
            if (type == MemoryRetrievalQuery.class) {
                if (!root.path("limit").isIntegralNumber()) throw new IllegalArgumentException();
                optionalStrings(root, "kind", "text", "afterMemoryId");
            }
            optionalStrings(root, "effectiveAt", "expiresAt");
            if (type == UpsertMemoryCommand.class || type == CorrectMemoryCommand.class) {
                if (!root.path("confidence").isNumber() || !root.path("content").isObject()) throw new IllegalArgumentException();
                var content = root.path("content"); optionalStrings(content, "text"); var facts = content.path("facts");
                if (!facts.isMissingNode() && !facts.isNull()) { if (!facts.isObject()) throw new IllegalArgumentException(); for (var value : facts) if (!value.isString()) throw new IllegalArgumentException(); }
                strings(root.path("source"), "reference", "evidenceSha256"); optionalStrings(root.path("source"), "sourceRunId", "sourceEventId");
            }
            JSON.readValue(body, (Class<?>) type);
        } catch (RuntimeException malformed) { throw new IllegalArgumentException("Memory request is invalid"); }
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }
    private static void strings(JsonNode object, String... names) {
        if (!object.isObject()) throw new IllegalArgumentException(); for (var name : names) if (!object.path(name).isString()) throw new IllegalArgumentException();
    }
    private static void optionalStrings(JsonNode object, String... names) {
        for (var name : names) { var value = object.path(name); if (!value.isMissingNode() && !value.isNull() && !value.isString()) throw new IllegalArgumentException(); }
    }
}
