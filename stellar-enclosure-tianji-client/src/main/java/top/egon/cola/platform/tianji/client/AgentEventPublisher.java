package top.egon.cola.platform.tianji.client;

import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

/** Reads persisted SSE events on demand and reconnects from the last delivered sequence. */
final class AgentEventPublisher implements Flow.Publisher<AgentEventDTO> {
    private static final int MAX_FRAME_CHARS = 1_048_576;
    private static final int MAX_ERROR_BYTES = 2_097_152;
    private static final System.Logger log = System.getLogger(AgentEventPublisher.class.getName());
    private final HttpClient http;
    private final JsonMapper mapper;
    private final AgentEventsQuery query;
    private final int maxReconnects;
    private final Duration reconnectDelay;
    private final Duration connectionTimeout;
    private final LongFunction<HttpRequest> requestFactory;
    private final BiFunction<HttpResponse<?>, byte[], AgentClientException> errorFactory;

    AgentEventPublisher(HttpClient http, JsonMapper mapper, AgentEventsQuery query, int maxReconnects,
                        Duration reconnectDelay, Duration connectionTimeout, LongFunction<HttpRequest> requestFactory,
                        BiFunction<HttpResponse<?>, byte[], AgentClientException> errorFactory) {
        this.http = http;
        this.mapper = mapper;
        this.query = query;
        this.maxReconnects = maxReconnects;
        this.reconnectDelay = reconnectDelay;
        this.connectionTimeout = connectionTimeout;
        this.requestFactory = requestFactory;
        this.errorFactory = errorFactory;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super AgentEventDTO> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        EventSubscription subscription = new EventSubscription(subscriber);
        try {
            synchronized (subscription.signals) {
                subscriber.onSubscribe(subscription);
            }
            subscription.start();
        } catch (RuntimeException subscriberFailure) {
            subscription.cancel();
        }
    }

    private final class EventSubscription implements Flow.Subscription, Runnable {
        private final Flow.Subscriber<? super AgentEventDTO> subscriber;
        private final Object monitor = new Object();
        private final Object signals = new Object();
        private long demand;
        private long cursor = query.afterSequence();
        private boolean done;
        private CompletableFuture<HttpResponse<InputStream>> pending;
        private InputStream input;
        private Thread reader;

        private EventSubscription(Flow.Subscriber<? super AgentEventDTO> subscriber) {
            this.subscriber = subscriber;
        }

        void start() {
            synchronized (monitor) {
                if (done) return;
                reader = Thread.ofVirtual().name("tianji-agent-events").start(this);
            }
        }

        @Override
        public void request(long count) {
            if (count <= 0) {
                fail(new IllegalArgumentException("event demand must be positive"));
                return;
            }
            synchronized (monitor) {
                if (done) return;
                demand = Long.MAX_VALUE - demand < count ? Long.MAX_VALUE : demand + count;
                monitor.notifyAll();
            }
        }

        @Override
        public void cancel() {
            terminate(null, false);
        }

        @Override
        public void run() {
            int reconnects = 0;
            while (!isDone()) {
                try {
                    awaitDemand();
                    if (isDone()) return;
                    HttpRequest request = requestFactory.apply(cursor);
                    CompletableFuture<HttpResponse<InputStream>> call =
                            http.sendAsync(request, info -> info.statusCode() >= 200 && info.statusCode() < 300
                                    ? HttpResponse.BodySubscribers.ofInputStream()
                                    : HttpResponse.BodySubscribers.mapping(new BoundedBodySubscriber(MAX_ERROR_BYTES),
                                            ByteArrayInputStream::new));
                    synchronized (monitor) {
                        if (done) { call.cancel(true); return; }
                        pending = call;
                    }
                    HttpResponse<InputStream> response = call.get(connectionTimeout.toNanos(), TimeUnit.NANOSECONDS);
                    synchronized (monitor) {
                        pending = null;
                        if (done) { close(response.body()); return; }
                        input = response.body();
                    }
                    try (InputStream stream = response.body()) {
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            throw errorFactory.apply(response, stream.readAllBytes());
                        }
                        String contentType = response.headers().firstValue("Content-Type").orElse("");
                        if (!contentType.split(";", 2)[0].trim().equalsIgnoreCase("text/event-stream")) {
                            throw new ProtocolFailure();
                        }
                        long previousCursor = cursor;
                        if (readStream(stream)) {
                            terminate(null, true);
                            return;
                        }
                        if (cursor > previousCursor) reconnects = 0;
                    } finally {
                        synchronized (monitor) { input = null; }
                    }
                    if (reconnects++ >= maxReconnects) {
                        fail(new AgentClientException(0, "TIANJI_EVENT_STREAM_DISCONNECTED", null, null));
                        return;
                    }
                    pauseBeforeReconnect();
                } catch (AgentClientException terminal) {
                    fail(terminal);
                    return;
                } catch (ProtocolFailure invalid) {
                    fail(new AgentClientException(0, "TIANJI_INVALID_EVENT_STREAM", null, null));
                    return;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    if (!isDone()) fail(new AgentClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null));
                    return;
                } catch (CharacterCodingException invalid) {
                    fail(new AgentClientException(0, "TIANJI_INVALID_EVENT_STREAM", null, null));
                    return;
                } catch (IOException | ExecutionException | TimeoutException failure) {
                    synchronized (monitor) {
                        if (pending != null) pending.cancel(true);
                        pending = null;
                    }
                    if (isDone()) return;
                    if (HttpAgentClient.tooLarge(failure)) {
                        fail(new AgentClientException(0, "TIANJI_RESPONSE_TOO_LARGE", null, null));
                        return;
                    }
                    if (reconnects++ >= maxReconnects) {
                        fail(new AgentClientException(0, "TIANJI_EVENT_STREAM_DISCONNECTED", null, null));
                        return;
                    }
                    try {
                        pauseBeforeReconnect();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        if (!isDone()) fail(new AgentClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null));
                        return;
                    }
                } catch (RuntimeException failure) {
                    fail(new AgentClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null));
                    return;
                }
            }
        }

        private boolean readStream(InputStream stream) throws IOException, InterruptedException {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            BufferedReader lines = new BufferedReader(new InputStreamReader(stream, decoder));
            String event = null;
            String id = null;
            StringBuilder data = new StringBuilder();
            int frameChars = 0;
            int duplicates = 0;
            while (!isDone()) {
                String line = boundedLine(lines);
                if (line == null) return false;
                frameChars += line.length() + 1;
                if (frameChars > MAX_FRAME_CHARS) throw new ProtocolFailure();
                if (line.isEmpty()) {
                    if ("stream.complete".equals(event)) return true;
                    if (!data.isEmpty()) {
                        if (event != null && !"agent.event".equals(event)) throw new ProtocolFailure();
                        if (data.toString().getBytes(StandardCharsets.UTF_8).length > MAX_FRAME_CHARS) {
                            throw new ProtocolFailure();
                        }
                        AgentEventDTO value;
                        try { value = mapper.readValue(data.toString(), AgentEventDTO.class); }
                        catch (RuntimeException invalid) { throw new ProtocolFailure(); }
                        if (!query.runId().equals(value.runId())) throw new ProtocolFailure();
                        if (id != null && !id.equals(Long.toString(value.sequence()))) throw new ProtocolFailure();
                        if (value.sequence() > cursor) {
                            if (value.sequence() != cursor + 1) throw new ProtocolFailure();
                            awaitDemand();
                            if (isDone()) return false;
                            emit(value);
                            cursor = value.sequence();
                            duplicates = 0;
                        } else if (++duplicates > 1000) {
                            throw new ProtocolFailure();
                        }
                    }
                    event = null;
                    id = null;
                    data.setLength(0);
                    frameChars = 0;
                    continue;
                }
                if (line.charAt(0) == ':') continue;
                int separator = line.indexOf(':');
                String field = separator < 0 ? line : line.substring(0, separator);
                String value = separator < 0 ? "" : line.substring(separator + 1);
                if (value.startsWith(" ")) value = value.substring(1);
                switch (field) {
                    case "event" -> event = value;
                    case "id" -> id = value;
                    case "data" -> {
                        if (!data.isEmpty()) data.append('\n');
                        data.append(value);
                    }
                    default -> { }
                }
            }
            return false;
        }

        private void emit(AgentEventDTO event) {
            synchronized (signals) {
                synchronized (monitor) {
                    if (done) return;
                    if (demand != Long.MAX_VALUE) demand--;
                }
                try {
                    subscriber.onNext(event);
                } catch (RuntimeException subscriberFailure) {
                    terminate(null, false);
                }
            }
        }

        private void awaitDemand() throws InterruptedException {
            synchronized (monitor) {
                while (!done && demand == 0) monitor.wait();
            }
        }

        private boolean isDone() {
            synchronized (monitor) { return done; }
        }

        private void pauseBeforeReconnect() throws InterruptedException {
            if (!reconnectDelay.isZero()) Thread.sleep(reconnectDelay);
        }

        private void fail(Throwable failure) {
            terminate(failure, false);
        }

        private void terminate(Throwable failure, boolean complete) {
            CompletableFuture<?> call;
            InputStream stream;
            Thread worker;
            synchronized (monitor) {
                if (done) return;
                done = true;
                call = pending;
                stream = input;
                worker = reader;
                monitor.notifyAll();
            }
            if (call != null) call.cancel(true);
            close(stream);
            if (worker != null && worker != Thread.currentThread()) worker.interrupt();
            if (failure != null) {
                Thread.ofVirtual().name("tianji-agent-event-error").start(() -> {
                    synchronized (signals) { subscriber.onError(failure); }
                });
            } else if (complete) {
                synchronized (signals) { subscriber.onComplete(); }
            }
        }
    }

    private static String boundedLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = reader.read()) != -1) {
            if (value == '\n') {
                if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') line.setLength(line.length() - 1);
                return line.toString();
            }
            line.append((char) value);
            if (line.length() > MAX_FRAME_CHARS) throw new ProtocolFailure();
        }
        return line.isEmpty() ? null : line.toString();
    }

    private static void close(InputStream stream) {
        if (stream == null) return;
        try { stream.close(); }
        catch (IOException failure) {
            log.log(System.Logger.Level.DEBUG, "Agent event stream close failed: {0}", failure.getClass().getSimpleName());
        }
    }

    private static final class ProtocolFailure extends RuntimeException { }
}
