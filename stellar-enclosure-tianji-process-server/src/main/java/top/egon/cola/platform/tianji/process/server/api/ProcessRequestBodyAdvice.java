package top.egon.cola.platform.tianji.process.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;
import top.egon.cola.platform.tianji.contract.orchestration.EvaluateDecisionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.PublishProcessReleaseCommand;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.FailWorkerTaskCommand;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Set;

/** Applies the same bound to fixed-length and streamed JSON command bodies. */
@ControllerAdvice(basePackageClasses = ProcessController.class)
@ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
public final class ProcessRequestBodyAdvice extends RequestBodyAdviceAdapter {
    static final int MAX_BYTES = 9 * 1_048_576;
    static final int MAX_WORKER_BYTES = 256 * 1024;
    private static final JsonMapper STRICT_WORKER_JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    @Override
    public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return type == PublishProcessReleaseCommand.class || type == StartProcessCommand.class || type == EvaluateDecisionCommand.class
                || workerCommand(type);
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type,
                                           Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        int maxBytes = workerCommand(type) ? MAX_WORKER_BYTES : MAX_BYTES;
        if (input.getHeaders().getContentLength() > maxBytes) throw new TooLarge();
        byte[] body = input.getBody().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) throw new TooLarge();
        if (workerCommand(type)) validateWorkerBody(body, type);
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }

    private static boolean workerCommand(Type type) {
        return type == AcquireWorkerTaskCommand.class || type == CompleteWorkerTaskCommand.class || type == FailWorkerTaskCommand.class;
    }

    private static void validateWorkerBody(byte[] body, Type type) {
        Set<String> expected = type == AcquireWorkerTaskCommand.class ? Set.of("topic", "schemaVersion")
                : type == CompleteWorkerTaskCommand.class ? Set.of("taskId", "activityOccurrenceId", "leaseToken", "businessReceiptId", "output")
                : Set.of("taskId", "activityOccurrenceId", "leaseToken", "kind", "errorCode");
        try {
            var root = STRICT_WORKER_JSON.readTree(body);
            if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(expected)) {
                throw new IllegalArgumentException("Worker request is invalid");
            }
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Worker request is invalid");
        }
    }

    static final class TooLarge extends RuntimeException { }
}
