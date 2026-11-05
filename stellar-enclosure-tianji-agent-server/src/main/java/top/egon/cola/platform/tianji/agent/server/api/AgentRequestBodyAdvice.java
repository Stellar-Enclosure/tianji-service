package top.egon.cola.platform.tianji.agent.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeCommand;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/** Applies the same byte bound to fixed-length and streamed JSON commands. */
@ControllerAdvice(basePackageClasses = AgentRunController.class)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.api", name = "enabled", havingValue = "true")
public final class AgentRequestBodyAdvice extends RequestBodyAdviceAdapter {
    static final int MAX_BYTES = 2_097_152;
    static final int MAX_LINK_QUERY_BYTES = 16_384;
    static final int MAX_CHECKPOINT_RESUME_BYTES = 4_096;
    private static final JsonMapper STRICT_COMMAND_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();

    @Override
    public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return type == StartAgentRunCommand.class || type == CancelAgentRunCommand.class
                || type == LinkedAgentStartCommand.class || type == LinkedAgentLookupQuery.class || type == ResumeCommand.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type,
                                           Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        int limit = type == ResumeCommand.class ? MAX_CHECKPOINT_RESUME_BYTES
                : type == LinkedAgentLookupQuery.class ? MAX_LINK_QUERY_BYTES : MAX_BYTES;
        if (input.getHeaders().getContentLength() > limit) throw new TooLarge();
        byte[] body = input.getBody().readNBytes(limit + 1);
        if (body.length > limit) throw new TooLarge();
        if (type == ResumeCommand.class) {
            validateResume(body);
        } else if (type == LinkedAgentStartCommand.class || type == LinkedAgentLookupQuery.class) {
            try {
                var root = STRICT_COMMAND_JSON.readTree(body);
                var link = root == null ? null : root.get("link");
                if (root == null || !root.isObject() || link == null || !link.isObject()
                        || !text(link, "sourceId") || !text(link, "scopeType") || !text(link, "scopeInstanceId")
                        || !text(link, "activityOccurrenceId") || !text(link, "definitionContentSha256")
                        || !text(link, "dispatchNotAfter")) throw new IllegalArgumentException();
                if (type == LinkedAgentLookupQuery.class && !text(root, "idempotencyKey")) throw new IllegalArgumentException();
                if (type == LinkedAgentStartCommand.class) {
                    var start = root.get("start");
                    if (start == null || !start.isObject() || !text(start, "definitionKey") || !text(start, "idempotencyKey")
                            || !text(start, "correlationId") || start.get("definitionVersion") == null
                            || !start.get("definitionVersion").isIntegralNumber()) throw new IllegalArgumentException();
                }
                if (type == LinkedAgentStartCommand.class) STRICT_COMMAND_JSON.readValue(body, LinkedAgentStartCommand.class);
                else STRICT_COMMAND_JSON.readValue(body, LinkedAgentLookupQuery.class);
            }
            catch (RuntimeException invalid) { throw new IllegalArgumentException("Linked Agent request is invalid"); }
        }
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }

    private static void validateResume(byte[] body) {
        try {
            var root = STRICT_COMMAND_JSON.readTree(body);
            var revision = root == null ? null : root.get("expectedRevision");
            if (root == null || !root.isObject() || root.size() != 3 || !text(root, "runId")
                    || !text(root, "commandId") || revision == null || !revision.isIntegralNumber()
                    || !revision.canConvertToLong()) throw new IllegalArgumentException();
            STRICT_COMMAND_JSON.readValue(body, ResumeCommand.class);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Agent checkpoint resume request is invalid");
        }
    }

    private static boolean text(tools.jackson.databind.JsonNode object, String field) {
        var value = object.get(field);
        return value != null && value.isString();
    }

    static final class TooLarge extends RuntimeException { }
}
