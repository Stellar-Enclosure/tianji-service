package top.egon.cola.platform.tianji.acceptance;

import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.GenericApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class S12MetricsTelemetryTest {
    private static final String CLAIM = "tianji.agent.run.initial.claim.delay";
    private static final String LAG = "tianji.process.agent.result.inbox.decision.delay";

    @Test
    void exports_one_snapshot_with_two_actual_micrometer_samples() {
        var bodies = new ArrayList<byte[]>();
        var registry = new S12MetricsTelemetry(S12MetricsTelemetry.Role.AGENT, bodies::add);
        registry.counter("jvm.fixture.non_tianji").increment();
        registry.timer(CLAIM).record(Duration.ofMillis(125));
        registry.timer(CLAIM).record(Duration.ofMillis(375));
        registry.close();
        registry.close();
        assertEquals(1, bodies.size());
        var point = JsonMapper.builder().build().readTree(bodies.getFirst())
                .at("/resourceMetrics/0/scopeMetrics/0/metrics/0/histogram/dataPoints/0");
        assertEquals("2", point.path("count").asString());
        assertEquals(0.5, point.path("sum").asDouble(), 0.000_001);
        assertEquals("2", point.at("/bucketCounts/0").asString());
        assertTrue(point.path("attributes").isEmpty());
    }

    @Test
    void exports_only_fixed_applied_outcome() {
        var bodies = new ArrayList<byte[]>();
        var registry = new S12MetricsTelemetry(S12MetricsTelemetry.Role.PROCESS, bodies::add);
        registry.timer(LAG, "outcome", "applied").record(Duration.ZERO);
        registry.timer(LAG, "outcome", "applied").record(Duration.ofMillis(20));
        registry.close();
        var tags = JsonMapper.builder().build().readTree(bodies.getFirst())
                .at("/resourceMetrics/0/scopeMetrics/0/metrics/0/histogram/dataPoints/0/attributes");
        assertEquals(1, tags.size());
        assertEquals("outcome", tags.at("/0/key").asString());
        assertEquals("applied", tags.at("/0/value/stringValue").asString());
    }

    @Test
    void rejects_missing_wrong_or_unexpected_tianji_meters_before_export() {
        var bodies = new ArrayList<byte[]>();
        var missing = new S12MetricsTelemetry(S12MetricsTelemetry.Role.AGENT, bodies::add);
        assertThrows(IllegalStateException.class, missing::close);

        var extra = new S12MetricsTelemetry(S12MetricsTelemetry.Role.AGENT, bodies::add);
        extra.timer(CLAIM).record(Duration.ZERO);
        extra.counter("tianji.unexpected", "runId", "secret-test-value").increment();
        assertThrows(IllegalStateException.class, extra::close);

        var wrongType = new S12MetricsTelemetry(S12MetricsTelemetry.Role.AGENT, bodies::add);
        wrongType.counter(CLAIM).increment();
        assertThrows(IllegalStateException.class, wrongType::close);

        var highCardinality = new S12MetricsTelemetry(S12MetricsTelemetry.Role.PROCESS, bodies::add);
        highCardinality.timer(LAG, "outcome", "applied", "runId", "secret-test-value").record(Duration.ZERO);
        assertThrows(IllegalStateException.class, highCardinality::close);
        assertTrue(bodies.isEmpty());
    }

    @Test
    void validates_exact_loopback_receiver_without_opening_transport() {
        var accepted = URI.create("http://127.0.0.1:4318/v1/metrics");
        assertEquals(accepted, S12MetricsTelemetry.requireEndpoint(accepted));
        for (String endpoint : List.of("http://localhost:4318/v1/metrics", "http://127.0.0.1:04318/v1/metrics",
                "http://127.0.0.1:4318/v1/traces", "https://127.0.0.1:4318/v1/metrics",
                "http://user@127.0.0.1:4318/v1/metrics", "http://127.0.0.1:4318/v1/metrics?x=1")) {
            assertThrows(IllegalStateException.class, () -> S12MetricsTelemetry.requireEndpoint(URI.create(endpoint)));
        }
    }

    @Test
    void primary_registry_is_selected_and_closes_after_spring_stops_the_producer() {
        var bodies = new ArrayList<byte[]>();
        var registry = new S12MetricsTelemetry(S12MetricsTelemetry.Role.AGENT, bodies::add);
        try (var context = new GenericApplicationContext()) {
            context.registerBean("otherRegistry", SimpleMeterRegistry.class);
            var bean = BeanDefinitionBuilder.genericBeanDefinition(S12MetricsTelemetry.class, () -> registry)
                    .setDestroyMethodName("close").getBeanDefinition();
            bean.setPrimary(true);
            context.registerBeanDefinition("fixtureRegistry", bean);
            context.registerBean("producer", SmartLifecycle.class, () -> new SmartLifecycle() {
                private boolean running;
                @Override public void start() { running = true; }
                @Override public boolean isRunning() { return running; }
                @Override public void stop() {
                    registry.timer(CLAIM).record(Duration.ZERO);
                    running = false;
                }
            });
            context.refresh();
            var selected = context.getBeanProvider(MeterRegistry.class).getIfUnique();
            assertSame(registry, selected);
            selected.timer(CLAIM).record(Duration.ZERO);
        }
        assertEquals(1, bodies.size());
        var point = JsonMapper.builder().build().readTree(bodies.getFirst())
                .at("/resourceMetrics/0/scopeMetrics/0/metrics/0/histogram/dataPoints/0");
        assertEquals("2", point.path("count").asString());
    }
}
