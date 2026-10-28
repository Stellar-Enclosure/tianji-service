package top.egon.cola.platform.tianji.agent.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/** Applies the same byte bound to fixed-length and streamed JSON commands. */
@ControllerAdvice(basePackageClasses = AgentRunController.class)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.api", name = "enabled", havingValue = "true")
public final class AgentRequestBodyAdvice extends RequestBodyAdviceAdapter {
    static final int MAX_BYTES = 2_097_152;

    @Override
    public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return type == StartAgentRunCommand.class || type == CancelAgentRunCommand.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type,
                                           Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        if (input.getHeaders().getContentLength() > MAX_BYTES) throw new TooLarge();
        byte[] body = input.getBody().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) throw new TooLarge();
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }

    static final class TooLarge extends RuntimeException { }
}
