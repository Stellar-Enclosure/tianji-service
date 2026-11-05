package top.egon.cola.platform.tianji.acceptance;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.json.JsonMapper;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Test-only registry exporting the two real Tianji Timer series to an isolated OTLP HTTP Collector. */
public final class S12MetricsTelemetry extends SimpleMeterRegistry {
    private static final String SCOPE = "top.egon.cola.tianji.s12.metrics";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final Role role;
    private final Consumer<byte[]> output;
    private final Instant opened = Instant.now();
    private final AtomicBoolean closed = new AtomicBoolean();

    public S12MetricsTelemetry(Role role, URI receiver) {
        this(role, output(requireEndpoint(receiver)));
    }

    S12MetricsTelemetry(Role role, Consumer<byte[]> output) {
        this.role = Objects.requireNonNull(role);
        this.output = Objects.requireNonNull(output);
    }

    public static URI requireEndpoint(URI receiver) {
        if (receiver == null || receiver.getPort() < 1 || receiver.getPort() > 65_535
                || !receiver.toASCIIString().equals("http://127.0.0.1:" + receiver.getPort() + "/v1/metrics")) {
            throw invalid();
        }
        return receiver;
    }

    public enum Role {
        AGENT("tianji-s12-agent", "tianji.agent.run.initial.claim.delay", Map.of()),
        PROCESS("tianji-s12-process", "tianji.process.agent.result.inbox.decision.delay", Map.of("outcome", "applied"));

        private final String service;
        private final String meter;
        private final Map<String, String> tags;

        Role(String service, String meter, Map<String, String> tags) {
            this.service = service;
            this.meter = meter;
            this.tags = tags;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            output.accept(snapshot(role, getMeters(), opened, Instant.now()));
        } finally {
            super.close();
        }
    }

    static byte[] snapshot(Role role, List<Meter> meters, Instant opened, Instant sampled) {
        var tianji = meters.stream().filter(meter -> meter.getId().getName().startsWith("tianji.")).limit(2).toList();
        if (tianji.size() != 1 || !(tianji.getFirst() instanceof Timer timer)
                || !role.meter.equals(timer.getId().getName()) || sampled.isBefore(opened)
                || timer.getId().getTags().size() != role.tags.size()
                || timer.getId().getTags().stream().anyMatch(tag -> !tag.getValue().equals(role.tags.get(tag.getKey())))) {
            throw invalid();
        }
        long count = timer.count();
        double totalSeconds = timer.totalTime(TimeUnit.SECONDS);
        if (count <= 0 || count != timer.count() || !Double.isFinite(totalSeconds) || totalSeconds < 0) throw invalid();

        var attributes = role.tags.entrySet().stream().map(entry -> attribute(entry.getKey(), entry.getValue())).toList();
        var point = Map.of("attributes", attributes, "startTimeUnixNano", epochNanos(opened), "timeUnixNano", epochNanos(sampled),
                "count", Long.toString(count), "sum", totalSeconds, "bucketCounts", List.of(Long.toString(count)));
        var metric = Map.of("name", role.meter, "unit", "s", "histogram",
                Map.of("aggregationTemporality", 2, "dataPoints", List.of(point)));
        byte[] body = JSON.writeValueAsBytes(Map.of("resourceMetrics", List.of(Map.of(
                "resource", Map.of("attributes", List.of(attribute("service.name", role.service))),
                "scopeMetrics", List.of(Map.of("scope", Map.of("name", SCOPE), "metrics", List.of(metric)))))));
        if (body.length > 4_096) throw invalid();
        return body;
    }

    private static String epochNanos(Instant value) {
        return Long.toString(Math.addExact(Math.multiplyExact(value.getEpochSecond(), 1_000_000_000L), value.getNano()));
    }

    private static Map<String, Object> attribute(String key, String value) {
        return Map.of("key", key, "value", Map.of("stringValue", value));
    }

    private static Consumer<byte[]> output(URI receiver) {
        return body -> publish(receiver, body);
    }

    private static void publish(URI receiver, byte[] body) {
        var http = HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).proxy(ProxySelector.of(null)).build();
        try {
            var request = HttpRequest.newBuilder(receiver).timeout(TIMEOUT).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response = http.sendAsync(request, HttpResponse.BodyHandlers.discarding());
            try {
                if (response.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).statusCode() != 200) throw invalid();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw invalid();
            } catch (ExecutionException | TimeoutException failure) {
                throw invalid();
            } finally {
                if (!response.isDone()) response.cancel(true);
            }
        } finally {
            http.shutdownNow();
        }
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("S12 metrics fixture registry or transport is invalid");
    }
}
