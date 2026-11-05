package top.egon.cola.platform.tianji.agent.server.integration.process;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Optional AS-only observation. The terminal event, transport and Outbox fingerprint are independent of it. */
final class AgentResultSpanObservation {
    static final String FLAG = "stellar.tianji.agent.terminal-events.span-links-enabled";
    private static final String PREFIX = "tianji-link-v1:";
    private static final int CARRIER_LENGTH = 70;
    private static final Pattern CARRIER = Pattern.compile(PREFIX + "00-([0-9a-f]{32})-([0-9a-f]{16})-01");
    private static final String SCOPE = "top.egon.cola.tianji.agent.result";
    private static final String COUNTER = "tianji.agent.result.trace.carrier";
    private static final Delivery NO_DELIVERY = () -> { };
    private static final AgentResultSpanObservation DISABLED = new AgentResultSpanObservation(null, Map.of());

    private final Supplier<OpenTelemetry> telemetry;
    private final Map<Result, Counter> counters;

    private AgentResultSpanObservation(Supplier<OpenTelemetry> telemetry, Map<Result, Counter> counters) {
        this.telemetry = telemetry;
        this.counters = counters;
    }

    static AgentResultSpanObservation disabled() {
        return DISABLED;
    }

    static AgentResultSpanObservation enabled(Supplier<OpenTelemetry> telemetry, Supplier<MeterRegistry> registry) {
        return new AgentResultSpanObservation(Objects.requireNonNull(telemetry), counters(Objects.requireNonNull(registry)));
    }

    boolean isEnabled() {
        return telemetry != null;
    }

    String enqueueCarrier() {
        if (telemetry == null || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) return null;
        var completion = new EnqueueCompletion();
        try {
            // A span started after registration can always end outside the database write phase.
            TransactionSynchronizationManager.registerSynchronization(completion);
            completion.span = tracer().spanBuilder("tianji.agent.result.enqueue").setNoParent()
                    .setSpanKind(SpanKind.PRODUCER).startSpan();
            SpanContext context = completion.span.getSpanContext();
            if (!completion.span.isRecording() || !context.isValid() || context.isRemote() || !context.isSampled()) return null;
            String carrier = PREFIX + "00-" + context.getTraceId() + "-" + context.getSpanId() + "-01";
            return decode(carrier).isValid() ? carrier : null;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    Delivery delivery(String carrier) {
        if (telemetry == null) return NO_DELIVERY;
        if (carrier == null || !carrier.startsWith(PREFIX)) {
            count(Result.ABSENT);
            return NO_DELIVERY;
        }
        SpanContext source = decode(carrier);
        if (!source.isValid()) {
            count(Result.INVALID);
            return NO_DELIVERY;
        }
        Span created = null;
        try {
            created = tracer().spanBuilder("tianji.agent.result.deliver").setNoParent()
                    .setSpanKind(SpanKind.CONSUMER).addLink(source).startSpan();
            count(created.isRecording() ? Result.LINKED : Result.UNAVAILABLE);
            Span delivery = created;
            return () -> end(delivery);
        } catch (RuntimeException unavailable) {
            end(created);
            count(Result.UNAVAILABLE);
            return NO_DELIVERY;
        }
    }

    private Tracer tracer() {
        OpenTelemetry installed = telemetry.get();
        return (installed == null ? GlobalOpenTelemetry.getOrNoop() : installed).getTracer(SCOPE);
    }

    private static SpanContext decode(String carrier) {
        if (carrier.length() != CARRIER_LENGTH) return SpanContext.getInvalid();
        var matched = CARRIER.matcher(carrier);
        if (!matched.matches()) return SpanContext.getInvalid();
        return SpanContext.createFromRemoteParent(matched.group(1), matched.group(2),
                TraceFlags.getSampled(), TraceState.getDefault());
    }

    private static Map<Result, Counter> counters(Supplier<MeterRegistry> supplier) {
        try {
            MeterRegistry registry = supplier.get();
            if (registry == null) return Map.of();
            var counters = new EnumMap<Result, Counter>(Result.class);
            for (Result result : Result.values()) {
                counters.put(result, Counter.builder(COUNTER).tag("result", result.tag).register(registry));
            }
            return Map.copyOf(counters);
        } catch (RuntimeException unavailable) {
            return Map.of();
        }
    }

    private void count(Result result) {
        Counter counter = counters.get(result);
        if (counter == null) return;
        try {
            counter.increment();
        } catch (RuntimeException unavailable) {
            // Telemetry cannot affect the original delivery outcome.
        }
    }

    private static void end(Span span) {
        if (span == null) return;
        try {
            span.end();
        } catch (RuntimeException unavailable) {
            // Telemetry cannot affect the original transaction or delivery outcome.
        }
    }

    private static final class EnqueueCompletion implements TransactionSynchronization {
        private Span span;

        @Override public void afterCompletion(int status) {
            end(span);
        }
    }

    private enum Result {
        LINKED("linked"), ABSENT("absent"), INVALID("invalid"), UNAVAILABLE("unavailable");

        private final String tag;

        Result(String tag) { this.tag = tag; }
    }

    @FunctionalInterface
    interface Delivery extends AutoCloseable {
        @Override void close();
    }
}
