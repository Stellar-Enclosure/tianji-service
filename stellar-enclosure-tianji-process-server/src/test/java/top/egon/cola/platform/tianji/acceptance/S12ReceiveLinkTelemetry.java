package top.egon.cola.platform.tianji.acceptance;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import tools.jackson.databind.json.JsonMapper;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** Test-only fixed-span bridge for an operator's isolated OTLP HTTP receiver. */
public final class S12ReceiveLinkTelemetry implements OpenTelemetry, AutoCloseable {
    static final String ENQUEUE = "tianji.agent.result.enqueue";
    static final String DELIVER = "tianji.agent.result.deliver";
    static final String RECEIVE = "tianji.process.agent.result.receive";
    private static final String AGENT_SCOPE = "top.egon.cola.tianji.agent.result";
    private static final String PROCESS_SCOPE = "top.egon.cola.platform.tianji.process.agent.result";
    private static final int SAMPLED = 1;
    private static final int REMOTE_KNOWN = 1 << 8;
    private static final int REMOTE = 1 << 9;
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final SdkTracerProvider provider;
    private final HttpClient http;

    public static S12ReceiveLinkTelemetry open(Role role, URI receiver) {
        if (role == null || receiver == null || !"http".equals(receiver.getScheme()) || !"127.0.0.1".equals(receiver.getHost())
                || receiver.getPort() < 1 || receiver.getPort() > 65_535
                || !"/v1/traces".equals(receiver.getRawPath()) || receiver.getRawUserInfo() != null
                || receiver.getRawQuery() != null || receiver.getRawFragment() != null
                || !receiver.toASCIIString().equals("http://127.0.0.1:" + receiver.getPort() + "/v1/traces")) {
            throw invalid();
        }
        var http = HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).proxy(ProxySelector.of(null)).build();
        try {
            return new S12ReceiveLinkTelemetry(http, new Export(role, body -> send(http, receiver, body)));
        } catch (RuntimeException failure) {
            http.shutdownNow();
            throw failure;
        }
    }

    private S12ReceiveLinkTelemetry(HttpClient http, SpanExporter exporter) {
        this.http = http;
        var processor = BatchSpanProcessor.builder(exporter).setMaxQueueSize(64).setMaxExportBatchSize(16)
                .setScheduleDelay(Duration.ofMillis(100)).setExporterTimeout(TIMEOUT).build();
        provider = SdkTracerProvider.builder().setSampler(Sampler.alwaysOn()).addSpanProcessor(processor).build();
    }

    @Override public TracerProvider getTracerProvider() { return provider; }
    @Override public ContextPropagators getPropagators() { return ContextPropagators.noop(); }

    @Override
    public void close() {
        try {
            provider.shutdown().join(3, TimeUnit.SECONDS);
        } finally {
            http.shutdownNow();
        }
    }

    private static boolean send(HttpClient http, URI receiver, byte[] body) {
        var request = HttpRequest.newBuilder(receiver).timeout(TIMEOUT).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var response = http.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        try {
            return response.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).statusCode() == 200;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            return false;
        } finally {
            if (!response.isDone()) response.cancel(true);
        }
    }

    public enum Role {
        AGENT("tianji-s12-agent", AGENT_SCOPE), PROCESS("tianji-s12-process", PROCESS_SCOPE);

        private final String service;
        private final String scope;
        Role(String service, String scope) {
            this.service = service;
            this.scope = scope;
        }
    }

    static final class Export implements SpanExporter {
        private final Role role;
        private final Predicate<byte[]> output;

        Export(Role role, Predicate<byte[]> output) {
            this.role = Objects.requireNonNull(role, "role");
            this.output = Objects.requireNonNull(output, "output");
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            if (spans == null || spans.isEmpty() || spans.size() > 16) return CompletableResultCode.ofFailure();
            try {
                var encoded = new ArrayList<Map<String, Object>>();
                for (SpanData span : spans) encoded.add(encode(span));
                byte[] body = JSON.writeValueAsBytes(Map.of("resourceSpans", List.of(Map.of(
                        "resource", Map.of("attributes", List.of(Map.of("key", "service.name",
                                "value", Map.of("stringValue", role.service)))),
                        "scopeSpans", List.of(Map.of("scope", Map.of("name", role.scope), "spans", encoded))))));
                return body.length <= 65_536 && output.test(body) ? CompletableResultCode.ofSuccess() : CompletableResultCode.ofFailure();
            } catch (RuntimeException invalid) {
                // Reject the entire diagnostic batch rather than omitting sensitive or unexpected attributes.
                return CompletableResultCode.ofFailure();
            }
        }

        private Map<String, Object> encode(SpanData span) {
            boolean enqueue = role == Role.AGENT && ENQUEUE.equals(span.getName());
            boolean deliver = role == Role.AGENT && DELIVER.equals(span.getName());
            boolean receive = role == Role.PROCESS && RECEIVE.equals(span.getName());
            var expected = enqueue ? io.opentelemetry.api.trace.SpanKind.PRODUCER : io.opentelemetry.api.trace.SpanKind.CONSUMER;
            if (!(enqueue || deliver || receive) || span.getKind() != expected
                    || !role.scope.equals(span.getInstrumentationScopeInfo().getName()) || !span.hasEnded()
                    || span.getStartEpochNanos() <= 0 || span.getEndEpochNanos() < span.getStartEpochNanos()
                    || span.getTotalAttributeCount() != 0 || span.getTotalRecordedEvents() != 0
                    || span.getTotalRecordedLinks() != (enqueue ? 0 : 1) || span.getLinks().size() != span.getTotalRecordedLinks()
                    || !span.getStatus().equals(io.opentelemetry.sdk.trace.data.StatusData.unset())) throw invalid();
            var parent = span.getParentSpanContext();
            if (parent.isValid() && !receive) throw invalid();
            var data = new LinkedHashMap<>(identifiers(span.getSpanContext(), SAMPLED));
            data.put("name", span.getName());
            data.put("kind", enqueue ? 4 : 5);
            data.put("startTimeUnixNano", Long.toString(span.getStartEpochNanos()));
            data.put("endTimeUnixNano", Long.toString(span.getEndEpochNanos()));
            if (parent.isValid()) {
                data.put("parentSpanId", identifiers(parent, SAMPLED).get("spanId"));
                data.put("flags", SAMPLED | REMOTE_KNOWN | (parent.isRemote() ? REMOTE : 0));
            }
            if (!enqueue) {
                var link = span.getLinks().getFirst();
                if (link.getTotalAttributeCount() != 0 || !link.getSpanContext().isRemote()) throw invalid();
                data.put("links", List.of(identifiers(link.getSpanContext(), SAMPLED | REMOTE_KNOWN | REMOTE)));
            }
            return data;
        }

        private static Map<String, Object> identifiers(SpanContext context, int flags) {
            if (!context.isValid() || !context.isSampled() || !context.getTraceState().isEmpty()) throw invalid();
            return Map.of("traceId", context.getTraceId(), "spanId", context.getSpanId(), "flags", flags);
        }

        @Override public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
        @Override public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("S12 receive-link fixture transport or span is invalid");
    }
}
