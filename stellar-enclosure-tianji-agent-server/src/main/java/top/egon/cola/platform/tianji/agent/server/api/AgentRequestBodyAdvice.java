package top.egon.cola.platform.tianji.agent.server.api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeCommand;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Applies the same byte bound to fixed-length and streamed JSON commands. */
@ControllerAdvice(name = "agentRequestBodyAdvice", basePackageClasses = AgentRunController.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.api", name = "enabled", havingValue = "true")
public final class AgentRequestBodyAdvice extends RequestBodyAdviceAdapter {
    static final int MAX_BYTES = 2_097_152;
    static final int MAX_LINK_QUERY_BYTES = 16_384;
    static final int MAX_CHECKPOINT_RESUME_BYTES = 4_096;
    private static final JsonMapper STRICT_COMMAND_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();

    private static final JsonMapper V2_COMMAND_JSON = STRICT_COMMAND_JSON.rebuild()
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private static final Set<String> RUNTIME_FIELDS = Set.of("idempotencyKey", "link", "nodeExecutionRef", "templateKey",
            "templateVersion", "templateSha256", "configurationSha256", "inputSchemaSha256", "outputSchemaSha256",
            "input", "parameters", "limits", "attemptDeadlineAt", "correlationId");
    private static final Set<String> LINK_FIELDS = Set.of("sourceId", "scopeType", "scopeInstanceId", "activityOccurrenceId",
            "definitionContentSha256", "dispatchNotAfter");
    private static final Pattern UTC_MICROS = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?Z");

    @Qualifier("tianjiAgentAccess")
    private final TianjiAgentAccess access;

    public AgentRequestBodyAdvice() {
        this(null);
    }

    @Override
    public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return type == StartAgentRunCommand.class || type == CancelAgentRunCommand.class
                || type == LinkedAgentStartCommand.class || type == LinkedAgentLookupQuery.class || type == ResumeCommand.class
                || type == AgentRuntimeTemplateCommand.class || type == LinkedAgentRuntimeCommand.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type,
                                           Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        requireRuntimeService(type);
        int limit = type == ResumeCommand.class ? MAX_CHECKPOINT_RESUME_BYTES
                : type == LinkedAgentLookupQuery.class ? MAX_LINK_QUERY_BYTES : MAX_BYTES;
        if (input.getHeaders().getContentLength() > limit) throw new TooLarge();
        byte[] body = input.getBody().readNBytes(limit + 1);
        if (body.length > limit) throw new TooLarge();
        if (type == ResumeCommand.class) {
            validateResume(body);
        } else if (type == AgentRuntimeTemplateCommand.class || type == LinkedAgentRuntimeCommand.class) {
            var mediaType = input.getHeaders().getContentType();
            if (mediaType != null && mediaType.getCharset() != null && !StandardCharsets.UTF_8.equals(mediaType.getCharset())) {
                throw new IllegalArgumentException("Agent runtime request must be UTF-8");
            }
            validateRuntime(body, type);
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

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage input, MethodParameter parameter, Type type,
            Class<? extends HttpMessageConverter<?>> converter) {
        requireRuntimeService(type);
        return body;
    }

    private void requireRuntimeService(Type type) {
        String resource;
        if (type == AgentRuntimeTemplateCommand.class) {
            resource = TianjiAgentPermissions.TEMPLATE_PUBLISH_API;
        } else if (type == LinkedAgentRuntimeCommand.class) {
            resource = TianjiAgentPermissions.LINKED_RUNTIME_SUBMIT_API;
        } else {
            return;
        }
        if (access == null) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent runtime access is unavailable");
        }
        if (access.require(resource).actor().subjectType() != TrustedAgentActor.SubjectType.SERVICE) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Verified Agent SERVICE required");
        }
    }

    private static void validateRuntime(byte[] body, Type type) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            if (type == AgentRuntimeTemplateCommand.class) {
                var template = AgentRuntimeTemplateCommand.fromExactWire(V2_COMMAND_JSON.readValue(text, OBJECT));
                if (!template.isTemplateConsistent()) throw new IllegalArgumentException();
                return;
            }
            var root = V2_COMMAND_JSON.readTree(text);
            if (!closed(root, RUNTIME_FIELDS)) throw new IllegalArgumentException();
            var link = root.get("link");
            var version = root.get("templateVersion");
            if (!closed(link, LINK_FIELDS) || !version.isIntegralNumber() || !version.canConvertToLong()
                    || !utcMicros(root.get("attemptDeadlineAt")) || !utcMicros(link.get("dispatchNotAfter"))
                    || !root.get("input").isObject() || !root.get("parameters").isObject()
                    || !V2_COMMAND_JSON.readValue(text, LinkedAgentRuntimeCommand.class).isLinkedRuntimeConsistent()) {
                throw new IllegalArgumentException();
            }
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalArgumentException("Agent runtime request is invalid");
        }
    }

    private static boolean closed(JsonNode object, Set<String> fields) {
        return object != null && object.isObject() && object.size() == fields.size()
                && fields.stream().allMatch(name -> object.hasNonNull(name));
    }

    private static boolean utcMicros(JsonNode value) {
        if (value == null || !value.isString()) return false;
        String time = value.asString();
        if (!UTC_MICROS.matcher(time).matches()) return false;
        LocalDateTime.parse(time.substring(0, time.length() - 1), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return true;
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
