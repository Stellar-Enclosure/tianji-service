package top.egon.cola.platform.tianji.process.server.api;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.client.AgentResultTraceLinkCarrier;

import java.util.Objects;
import java.util.function.Supplier;

/** Optional untrusted transport link, read only after the existing receive path succeeds. */
final class AgentResultInboxReceiveObservation {
    static final String FLAG = "stellar.tianji.process.agent-results.receive-span-links-enabled";
    static final String SPAN = "tianji.process.agent.result.receive";
    private static final String SCOPE = "top.egon.cola.platform.tianji.process.agent.result";
    private static final AgentResultInboxReceiveObservation DISABLED = new AgentResultInboxReceiveObservation(null);
    private final Supplier<OpenTelemetry> telemetry;

    private AgentResultInboxReceiveObservation(Supplier<OpenTelemetry> telemetry) {
        this.telemetry = telemetry;
    }

    static AgentResultInboxReceiveObservation disabled() {
        return DISABLED;
    }

    static AgentResultInboxReceiveObservation enabled(Supplier<OpenTelemetry> telemetry) {
        return new AgentResultInboxReceiveObservation(Objects.requireNonNull(telemetry, "telemetry"));
    }

    void afterReceive() {
        if (telemetry == null) return;
        try {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return;
            var request = attributes.getRequest();
            if (!"POST".equals(request.getMethod()) || !AgentResultTraceLinkCarrier.PATH.equals(request.getRequestURI())) return;
            var values = request.getHeaders(AgentResultTraceLinkCarrier.HEADER);
            if (values == null || !values.hasMoreElements()) return;
            String value = values.nextElement();
            if (values.hasMoreElements() || !AgentResultTraceLinkCarrier.isValid(value)) return;

            var source = SpanContext.createFromRemoteParent(value.substring(3, 35), value.substring(36, 52),
                    TraceFlags.getSampled(), TraceState.getDefault());
            var installed = telemetry.get();
            if (!source.isValid() || installed == null) return;
            installed.getTracer(SCOPE).spanBuilder(SPAN).setSpanKind(SpanKind.CONSUMER)
                    .addLink(source).startSpan().end();
        } catch (RuntimeException unavailable) {
            // Observation cannot change the existing durable receive result.
        }
    }
}
