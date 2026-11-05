package top.egon.cola.platform.tianji.acceptance;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class S12ReceiveLinkTelemetryTest {
    private static final String AS_SCOPE = "top.egon.cola.tianji.agent.result";
    private static final String PS_SCOPE = "top.egon.cola.platform.tianji.process.agent.result";

    @Test
    void encodes_exact_remote_links_and_distinct_local_parent_without_a_listener() {
        var spans = new ArrayList<SpanData>();
        try (var sdk = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(capture(spans))).build()) {
            var agent = sdk.get(AS_SCOPE);
            var process = sdk.get(PS_SCOPE);
            var enqueue = agent.spanBuilder(S12ReceiveLinkTelemetry.ENQUEUE).setNoParent().setSpanKind(SpanKind.PRODUCER).startSpan();
            enqueue.end();
            var deliver = agent.spanBuilder(S12ReceiveLinkTelemetry.DELIVER).setNoParent().setSpanKind(SpanKind.CONSUMER)
                    .addLink(remote(enqueue.getSpanContext())).startSpan();
            deliver.end();
            var ambient = process.spanBuilder("fixture-ambient").setNoParent().startSpan();
            try (var ignored = ambient.makeCurrent()) {
                process.spanBuilder(S12ReceiveLinkTelemetry.RECEIVE).setSpanKind(SpanKind.CONSUMER)
                        .addLink(remote(deliver.getSpanContext())).startSpan().end();
            } finally {
                ambient.end();
            }
            var bodies = new ArrayList<byte[]>();
            assertTrue(new S12ReceiveLinkTelemetry.Export(S12ReceiveLinkTelemetry.Role.AGENT, bodies::add)
                    .export(spans.subList(0, 2)).isSuccess());
            assertTrue(new S12ReceiveLinkTelemetry.Export(S12ReceiveLinkTelemetry.Role.PROCESS, bodies::add)
                    .export(List.of(spans.get(2))).isSuccess());
            var mapper = JsonMapper.builder().build();
            var delivery = mapper.readTree(bodies.get(0)).at("/resourceSpans/0/scopeSpans/0/spans/1");
            var receive = mapper.readTree(bodies.get(1)).at("/resourceSpans/0/scopeSpans/0/spans/0");
            assertEquals(enqueue.getSpanContext().getSpanId(), delivery.at("/links/0/spanId").asString());
            assertEquals(deliver.getSpanContext().getTraceId(), receive.at("/links/0/traceId").asString());
            assertEquals(deliver.getSpanContext().getSpanId(), receive.at("/links/0/spanId").asString());
            assertEquals(769, receive.at("/links/0/flags").asInt());
            assertEquals(ambient.getSpanContext().getSpanId(), receive.path("parentSpanId").asString());
            assertNotEquals(receive.path("parentSpanId").asString(), receive.at("/links/0/spanId").asString());
            assertEquals(5, receive.path("kind").asInt());
        }
    }

    @Test
    void rejects_sensitive_payloads_as_an_entire_batch() {
        var spans = new ArrayList<SpanData>();
        try (var sdk = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(capture(spans))).build()) {
            var tracer = sdk.get(AS_SCOPE);
            tracer.spanBuilder(S12ReceiveLinkTelemetry.ENQUEUE).setSpanKind(SpanKind.PRODUCER).startSpan().end();
            tracer.spanBuilder(S12ReceiveLinkTelemetry.ENQUEUE).setSpanKind(SpanKind.PRODUCER)
                    .setAttribute("runId", "secret-test-value").startSpan().end();
            var captured = new ArrayList<byte[]>();
            var export = new S12ReceiveLinkTelemetry.Export(S12ReceiveLinkTelemetry.Role.AGENT, captured::add);
            assertFalse(export.export(spans).isSuccess());
            assertTrue(captured.isEmpty());
        }
    }

    @Test
    void rejects_nonlocal_ambiguous_and_wrong_signal_endpoints_before_transport() {
        for (String value : List.of("http://localhost:4318/v1/traces", "http://127.0.0.1:4318/v1/traces?secret=x",
                "http://127.0.0.1:4318/v1/metrics", "https://127.0.0.1:4318/v1/traces",
                "http://127.0.0.1:04318/v1/traces", "http://user@127.0.0.1:4318/v1/traces")) {
            assertThrows(IllegalArgumentException.class,
                    () -> S12ReceiveLinkTelemetry.open(S12ReceiveLinkTelemetry.Role.AGENT, URI.create(value)));
        }
    }

    private static SpanContext remote(SpanContext source) {
        return SpanContext.createFromRemoteParent(source.getTraceId(), source.getSpanId(),
                TraceFlags.getSampled(), TraceState.getDefault());
    }

    private static SpanExporter capture(List<SpanData> spans) {
        return new SpanExporter() {
            @Override public CompletableResultCode export(Collection<SpanData> batch) {
                spans.addAll(batch);
                return CompletableResultCode.ofSuccess();
            }
            @Override public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
            @Override public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
        };
    }
}
