package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.acceptance.S12ReceiveLinkInitializer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class S12ReceiveLinkCollectorEvidenceTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant SINCE = Instant.now().minusSeconds(30);
    private static final String ENQUEUE = "tianji.agent.result.enqueue";
    private static final String DELIVER = "tianji.agent.result.deliver";
    private static final String RECEIVE = "tianji.process.agent.result.receive";
    private static final List<String> CARRIERS = List.of(carrier(1), carrier(4));

    @Test
    void checks_both_persisted_sources_without_matching_by_timing_or_count_alone() {
        var fixture = traces();
        assertTrue(S12ReceiveLinkCollectorEvidence.linked(lines(fixture), CARRIERS, SINCE));
        assertFalse(S12ReceiveLinkCollectorEvidence.linked(lines(fixture.subList(0, 5)), CARRIERS, SINCE));
        assertFalse(S12ReceiveLinkCollectorEvidence.linked(new byte[0], CARRIERS, SINCE));
        assertThrows(IllegalStateException.class, () -> S12ReceiveLinkCollectorEvidence.linked(
                lines(fixture), List.of(carrier(1), carrier(1)), SINCE));
        assertThrows(IllegalStateException.class, () -> S12ReceiveLinkCollectorEvidence.linked(
                lines(fixture), List.of(carrier(1), carrier(6)), SINCE));
        assertThrows(IllegalStateException.class, () -> S12ReceiveLinkCollectorEvidence.linked(
                lines(fixture), CARRIERS, Instant.now()));
    }

    @Test
    void rejects_duplicate_parent_substitution_and_leaked_attributes() {
        var fixture = traces();
        fixture.set(5, fixture.get(2));
        assertThrows(IllegalStateException.class, () -> S12ReceiveLinkCollectorEvidence.linked(lines(fixture), CARRIERS, SINCE));

        fixture.set(5, span(RECEIVE, 6, 5, Map.of("parentSpanId", id(5, 16))));
        assertThrows(IllegalStateException.class, () -> S12ReceiveLinkCollectorEvidence.linked(lines(fixture), CARRIERS, SINCE));

        fixture.set(5, span(RECEIVE, 6, 5, Map.of("attributes", List.of(Map.of("key", "runId", "value",
                Map.of("stringValue", "private-canary"))))));
        assertThrows(IllegalStateException.class, () -> S12ReceiveLinkCollectorEvidence.linked(lines(fixture), CARRIERS, SINCE));

        fixture.set(5, span(RECEIVE, 6, 2, Map.of()));
        assertThrows(IllegalStateException.class, () -> S12ReceiveLinkCollectorEvidence.linked(lines(fixture), CARRIERS, SINCE));
    }

    @Test
    void rejects_malformed_fields_and_trace_reuse_across_six_distinct_span_ids() {
        var fixture = traces();
        fixture.set(5, span(RECEIVE, 6, 5, Map.of("traceId", id(1, 32))));
        assertThrows(IllegalStateException.class, () -> S12ReceiveLinkCollectorEvidence.linked(lines(fixture), CARRIERS, SINCE));

        fixture.set(5, span(RECEIVE, 6, 5, Map.of("kind", "5")));
        assertThrows(IllegalStateException.class, () -> S12ReceiveLinkCollectorEvidence.linked(lines(fixture), CARRIERS, SINCE));

        fixture.set(5, span(RECEIVE, 6, 5, Map.of("flags", 4_294_967_297L)));
        assertThrows(IllegalStateException.class, () -> S12ReceiveLinkCollectorEvidence.linked(lines(fixture), CARRIERS, SINCE));
    }

    @Test
    void initializer_requires_the_private_profile_before_registering_a_provider() {
        try (var context = new GenericApplicationContext()) {
            var initializer = new S12ReceiveLinkInitializer();
            assertThrows(IllegalStateException.class, () -> initializer.initialize(context));
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture", Map.of(
                    "tianji.expense.fixture.run-authorized", "true",
                    "tianji.expense.fixture.s12-receive-link", "true",
                    "tianji.expense.fixture.role", "PROCESS",
                    "management.otlp.tracing.endpoint", "http://127.0.0.1:4318/v1/traces")));
            initializer.initialize(context);
            assertTrue(context.containsBeanDefinition("tianjiS12ReceiveLinkTelemetry"));
            // Do not refresh the context or open a transport.
        }
    }

    private static List<String> traces() {
        return new ArrayList<>(List.of(span(ENQUEUE, 1, 0, Map.of()), span(DELIVER, 2, 1, Map.of()),
                span(RECEIVE, 3, 2, Map.of()), span(ENQUEUE, 4, 0, Map.of()),
                span(DELIVER, 5, 4, Map.of()), span(RECEIVE, 6, 5, Map.of())));
    }

    private static String span(String name, int value, int linked, Map<String, Object> additions) {
        var span = new LinkedHashMap<String, Object>();
        span.put("name", name);
        span.put("traceId", id(value, 32));
        span.put("spanId", id(value, 16));
        span.put("kind", ENQUEUE.equals(name) ? 4 : 5);
        span.put("flags", 1);
        long nanos = Instant.now().minusSeconds(10).getEpochSecond() * 1_000_000_000L;
        span.put("startTimeUnixNano", Long.toString(nanos));
        span.put("endTimeUnixNano", Long.toString(nanos + 1));
        if (linked > 0) span.put("links", List.of(Map.of("traceId", id(linked, 32), "spanId", id(linked, 16), "flags", 769)));
        span.putAll(additions);
        boolean process = RECEIVE.equals(name);
        return JSON.writeValueAsString(Map.of("resourceSpans", List.of(Map.of(
                "resource", Map.of("attributes", List.of(Map.of("key", "service.name",
                        "value", Map.of("stringValue", process ? "tianji-s12-process" : "tianji-s12-agent")))),
                "scopeSpans", List.of(Map.of("scope", Map.of("name", process ? "top.egon.cola.platform.tianji.process.agent.result"
                        : "top.egon.cola.tianji.agent.result"), "spans", List.of(span)))))));
    }

    private static byte[] lines(List<String> items) { return (String.join("\n", items) + "\n").getBytes(StandardCharsets.UTF_8); }

    private static String carrier(int value) { return "tianji-link-v1:00-" + id(value, 32) + "-" + id(value, 16) + "-01"; }

    private static String id(int value, int length) { return ("%0" + length + "x").formatted(value); }
}
