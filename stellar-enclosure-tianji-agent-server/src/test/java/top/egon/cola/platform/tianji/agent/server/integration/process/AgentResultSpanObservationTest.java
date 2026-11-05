package top.egon.cola.platform.tianji.agent.server.integration.process;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResultSpanObservationTest {
    private static final String COUNTER = "tianji.agent.result.trace.carrier";
    private static final String VALID = "tianji-link-v1:00-" + "a".repeat(32) + "-" + "b".repeat(16) + "-01";

    @Test
    void closedCarrierAndRealSdkLinks() {
        var spans = new LocalSpanProcessor();
        var registry = new SimpleMeterRegistry();
        try (var provider = SdkTracerProvider.builder().setSampler(Sampler.alwaysOn()).addSpanProcessor(spans).build()) {
            OpenTelemetry sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build();
            var observation = AgentResultSpanObservation.enabled(() -> sdk, () -> registry);
            String carrier = committedCarrier(observation, spans);
            assertNotNull(carrier);
            var origin = spans.completed.getFirst();
            assertEquals("tianji.agent.result.enqueue", origin.getName());
            assertEquals(SpanKind.PRODUCER, origin.getKind());
            assertFalse(origin.getParentSpanContext().isValid());
            assertEquals("tianji-link-v1:00-" + origin.getTraceId() + "-" + origin.getSpanId() + "-01", carrier);
            assertEquals(70, carrier.length());

            var current = Context.current();
            deliver(observation, carrier);
            deliver(observation, carrier);
            assertSame(current, Context.current());
            var first = spans.completed.get(1);
            var second = spans.completed.get(2);
            assertNotEquals(first.getTraceId(), second.getTraceId());
            assertNotEquals(first.getSpanId(), second.getSpanId());
            for (var linked : List.of(first, second)) {
                assertEquals("tianji.agent.result.deliver", linked.getName());
                assertEquals(SpanKind.CONSUMER, linked.getKind());
                assertFalse(linked.getParentSpanContext().isValid());
                assertNotEquals(origin.getTraceId(), linked.getTraceId());
                assertEquals(1, linked.getLinks().size());
                var linkedSource = linked.getLinks().getFirst().getSpanContext();
                assertEquals(origin.getTraceId(), linkedSource.getTraceId());
                assertEquals(origin.getSpanId(), linkedSource.getSpanId());
                assertTrue(linkedSource.isSampled());
                assertTrue(linkedSource.getTraceState().isEmpty());
                assertTrue(linked.getAttributes().isEmpty());
            }

            deliver(observation, null);
            deliver(observation, "a".repeat(32));
            deliver(observation, "tianji-link-v2:" + VALID.substring(15));
            deliver(observation, VALID.substring(0, 69));
            deliver(observation, VALID.replace("a".repeat(32), "0".repeat(32)));
            deliver(observation, VALID.replace("b".repeat(16), "0".repeat(16)));
            deliver(observation, VALID.replace("a".repeat(32), "A".repeat(32)));
            deliver(observation, VALID.substring(0, 68) + "00");
            assertEquals(3, spans.completed.size());
            assertEquals(Set.of("linked", "absent", "invalid", "unavailable"),
                    registry.getMeters().stream().map(meter -> meter.getId().getTag("result")).collect(Collectors.toSet()));
            assertTrue(registry.getMeters().stream().allMatch(meter -> meter.getId().getName().equals(COUNTER)
                    && meter.getId().getTags().size() == 1));
            assertEquals(2.0, registry.get(COUNTER).tag("result", "linked").counter().count());
            assertEquals(3.0, registry.get(COUNTER).tag("result", "absent").counter().count());
            assertEquals(5.0, registry.get(COUNTER).tag("result", "invalid").counter().count());
        } finally {
            registry.close();
        }
    }

    @Test
    void disabledAndUnavailableDoNotMutateContext() {
        var current = Context.current();
        var disabled = AgentResultSpanObservation.disabled();
        assertNull(disabled.enqueueCarrier());
        deliver(disabled, VALID);
        var registry = new SimpleMeterRegistry();
        try {
            var noSdk = AgentResultSpanObservation.enabled(OpenTelemetry::noop, () -> registry);
            assertNull(noSdk.enqueueCarrier());
            deliver(noSdk, VALID);

            var broken = AgentResultSpanObservation.enabled(() -> { throw new IllegalStateException("private fixture"); },
                    () -> registry);
            assertNull(committedCarrier(broken, new LocalSpanProcessor()));
            deliver(broken, VALID);
            assertEquals(2.0, registry.get(COUNTER).tag("result", "unavailable").counter().count());
        } finally {
            registry.close();
        }
        assertSame(current, Context.current());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
    }

    private static void deliver(AgentResultSpanObservation observation, String carrier) {
        try (var ignored = observation.delivery(carrier)) {
            // The actual caller continues its existing transport here, without changing the current context.
        }
    }

    private static String committedCarrier(AgentResultSpanObservation observation, LocalSpanProcessor spans) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            String carrier = observation.enqueueCarrier();
            assertTrue(spans.completed.isEmpty());
            var callbacks = TransactionSynchronizationManager.getSynchronizations();
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
            callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            return carrier;
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private static final class LocalSpanProcessor implements SpanProcessor {
        private final List<SpanData> completed = new ArrayList<>();

        @Override public void onStart(Context context, ReadWriteSpan span) { }

        @Override public boolean isStartRequired() { return false; }

        @Override public void onEnd(ReadableSpan span) { completed.add(span.toSpanData()); }

        @Override public boolean isEndRequired() { return true; }
    }
}
