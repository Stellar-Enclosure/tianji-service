package top.egon.cola.platform.tianji.process.server.acceptance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.client.AgentResultTraceLinkCarrier;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartResult;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.ExpenseAcceptanceManifestDTO;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

final class ExpenseAcceptanceFaultRelay implements AutoCloseable {
    private static final String WORKER = "/api/tianji/v1/worker-tasks/";
    private static final String LINKED = "/api/tianji/v1/agent-process-dispatches/";
    private static final String RESULT = "/api/tianji/v1/agent-result-inbox";
    private static final String TENANT = "X-Egon-Tianji-Tenant";
    private static final Duration EXCHANGE_LIMIT = Duration.ofSeconds(15);
    private static final Duration MAX_WINDOW = Duration.ofMinutes(4);
    private static final Duration MAX_HOLD = Duration.ofSeconds(10);
    private static final Set<String> REQUEST_HEADERS = Set.of("Authorization", "Accept", "Content-Type", TENANT);
    private static final Set<String> RESPONSE_HEADERS = Set.of("Content-Type", "Cache-Control", "Retry-After", "WWW-Authenticate");
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(24).maxStringLength(1_048_576)
                            .maxNameLength(128).maxNumberLength(24).maxTokenCount(32_768).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();

    private final LongSupplier nanoTime;
    private final Transport transport;
    private final Witnesses witnesses;
    private final boolean resultLinkReplay;
    private final EnumMap<FaultPhase, Window> windows = new EnumMap<>(FaultPhase.class);
    private final List<HttpsServer> listeners = new ArrayList<>();
    private ThreadPoolExecutor listenerExecutor;
    private ScheduledThreadPoolExecutor exchangeDeadlines;
    private HttpClient operatorHttpClient;
    private Duration maximumOperatorWindow = MAX_WINDOW;
    private Duration maximumOperatorHold = MAX_HOLD;
    private boolean closed;

    ExpenseAcceptanceFaultRelay(LongSupplier nanoTime, Transport transport, Witnesses witnesses) {
        this(nanoTime, transport, witnesses, false);
    }

    ExpenseAcceptanceFaultRelay(LongSupplier nanoTime, Transport transport, Witnesses witnesses,
                                boolean resultLinkReplay) {
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.transport = Objects.requireNonNull(transport);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.resultLinkReplay = resultLinkReplay;
    }

    static ExpenseAcceptanceFaultRelay open(ExpenseAcceptanceManifestDTO manifest, SSLContext listenerTls,
                                             SSLContext upstreamTls, Witnesses witnesses) {
        if (manifest == null || !manifest.runAuthorized() || manifest.schemaVersion() != 1
                || listenerTls == null || upstreamTls == null
                || Boolean.getBoolean("jdk.httpclient.enableAllMethodRetry")
                || Boolean.getBoolean("jdk.internal.httpclient.disableHostnameVerification")) throw invalid();
        var endpoints = manifest.endpoints();
        URI agent = localOrigin(endpoints.agent());
        URI process = localOrigin(endpoints.process());
        URI workerRelay = localOrigin(endpoints.workerRelay());
        URI linkedRelay = localOrigin(endpoints.linkedRelay());
        URI resultRelay = localOrigin(endpoints.resultRelay());
        if (Set.of(agent.getPort(), process.getPort(), workerRelay.getPort(), linkedRelay.getPort(), resultRelay.getPort()).size() != 5) {
            throw invalid();
        }
        var operatorWindow = bounded(Duration.ofSeconds(manifest.limits().recoverySeconds()), MAX_WINDOW);
        var operatorHold = bounded(Duration.ofSeconds(manifest.limits().linkedHoldSeconds()), MAX_HOLD);
        var upstream = HttpClient.newBuilder().sslContext(upstreamTls).proxy(noProxy())
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(EXCHANGE_LIMIT)
                .version(HttpClient.Version.HTTP_1_1).build();
        boolean resultLinkReplay = manifest.suite() == ExpenseAcceptanceHarness.Suite.RECEIVE_LINK_REPLAY;
        var relay = new ExpenseAcceptanceFaultRelay(System::nanoTime, (request, timeout) ->
                forward(upstream, request.direction() == Direction.LINKED ? agent : process, request, timeout,
                        resultLinkReplay), witnesses, resultLinkReplay);
        relay.operatorHttpClient = upstream;
        relay.maximumOperatorWindow = operatorWindow;
        relay.maximumOperatorHold = operatorHold;
        try {
            relay.listenerExecutor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16),
                    Thread.ofPlatform().daemon().name("expense-relay-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
            relay.exchangeDeadlines = new ScheduledThreadPoolExecutor(1,
                    Thread.ofPlatform().daemon().name("expense-relay-deadline-", 0).factory());
            relay.exchangeDeadlines.setRemoveOnCancelPolicy(true);
            relay.bind(workerRelay, Direction.WORKER, listenerTls);
            relay.bind(linkedRelay, Direction.LINKED, listenerTls);
            relay.bind(resultRelay, Direction.RESULT, listenerTls);
            relay.listeners.forEach(HttpsServer::start);
            return relay;
        } catch (RuntimeException | IOException failure) {
            relay.close();
            throw unavailable();
        }
    }

    synchronized void arm(FaultWindowDTO definition) {
        requireOpen();
        validateWindow(definition);
        bounded(definition.budget(), definition.phase() == FaultPhase.HOLD_LINKED_SUCCESS
                ? maximumOperatorHold : maximumOperatorWindow);
        Direction direction = direction(definition.phase());
        if (resultLinkReplay && direction == Direction.RESULT
                && (definition.phase() != FaultPhase.DROP_COMMITTED_RESULT_202
                    && definition.phase() != FaultPhase.OBSERVE_REPLACEMENT_RESEND
                    || windows.containsKey(definition.phase()))) throw invalid();
        if (windows.values().stream().anyMatch(window -> direction(window.definition.phase()) == direction && !window.finished)) {
            throw unavailable();
        }
        if (definition.phase() == FaultPhase.OBSERVE_REPLACEMENT_RESEND) {
            Window drop = windows.get(FaultPhase.DROP_COMMITTED_RESULT_202);
            if (drop == null || !drop.finished || drop.observation == null
                    || !sameEvent(definition, drop.observation)) throw unavailable();
        }
        windows.put(definition.phase(), new Window(definition, nanoTime.getAsLong()));
    }

    synchronized Decision decide(ObservedExchangeDTO exchange, long now) {
        requireOpen();
        Route route = route(exchange.direction(), exchange.method(), exchange.rawPath());
        Window window = matchingWindow(route, exchange);
        if (window == null) return Decision.PASS_THROUGH;
        requireRemaining(window, now);
        return decision(window.definition.phase());
    }

    synchronized ObservationDTO awaitObserved(FaultPhase phase, Duration timeout) {
        long start = nanoTime.getAsLong();
        long realStart = System.nanoTime();
        long budget = bounded(timeout, MAX_WINDOW).toNanos();
        Window window = windows.get(phase);
        if (window == null) throw unavailable();
        while (window.observation == null && !window.finished && !closed) {
            long wait = Math.min(budget - elapsed(start, nanoTime.getAsLong()), budget - elapsed(realStart, System.nanoTime()));
            waitFor(Math.min(wait, remaining(window, nanoTime.getAsLong())));
        }
        if (window.observation == null || closed) throw unavailable();
        return window.observation;
    }

    synchronized void release(FaultPhase phase) {
        requireOpen();
        Window window = windows.get(phase);
        if (window == null || window.finished || window.observation == null
                || phase != FaultPhase.HOLD_LINKED_SUCCESS && phase != FaultPhase.DEFER_RESULT_UNTIL_TIMER_OR_RESTART) {
            throw unavailable();
        }
        requireRemaining(window, nanoTime.getAsLong());
        window.finished = true;
        notifyAll();
    }

    ResponseDTO exchange(RequestDTO request) {
        Route route = route(request.direction(), request.method(), request.rawPath());
        validateRequest(request, route);
        String traceLink = resultTraceLink(request, route, resultLinkReplay);
        var decoded = decode(route, request);
        var observed = decoded.observed();
        Window window;
        synchronized (this) {
            requireOpen();
            window = matchingWindow(route, observed);
            if (window != null) requireRemaining(window, nanoTime.getAsLong());
        }
        if (window == null) {
            if (resultLinkReplay && route == Route.RESULT) throw unavailable();
            return forwardChecked(request, EXCHANGE_LIMIT, route);
        }
        var candidate = withOccurrence(window.definition, observed.occurrenceId());
        if (!witnesses.owned(candidate, observed)) throw unavailable();
        bindVerifiedOccurrence(window, observed.occurrenceId());
        return switch (candidate.phase()) {
            case DROP_WORKER_COMPLETE_AFTER_BUSINESS_COMMIT -> {
                if (!witnesses.businessCommitted(candidate, observed, decoded.leaseToken(), decoded.receiptId())) {
                    throw unavailable();
                }
                finish(window, observed);
                throw new ConnectionDropped();
            }
            case HOLD_LINKED_SUCCESS -> holdLinked(window, candidate, request, route, observed);
            case DEFER_RESULT_UNTIL_TIMER_OR_RESTART -> deferResult(window, request, route, observed);
            case DROP_COMMITTED_RESULT_202 -> dropCommittedResult(window, candidate, request, route, observed, traceLink);
            case OBSERVE_REPLACEMENT_RESEND -> observeResend(window, candidate, request, route, observed, traceLink);
        };
    }

    private ResponseDTO holdLinked(Window window, FaultWindowDTO candidate, RequestDTO request, Route route,
                                      ObservedExchangeDTO observed) {
        claim(window);
        try {
            var reply = forwardChecked(request, timeFor(window), route);
            if (reply.status() != 202) throw unavailable();
            var accepted = read(reply.body(), LinkedAgentStartResult.class);
            var link = accepted.query().link();
            if (!observed.sourceId().equals(link.sourceId()) || !observed.instanceId().equals(link.scopeInstanceId())
                    || !observed.occurrenceId().equals(link.activityOccurrenceId())
                    || !witnesses.agentCommitted(candidate, observed, accepted.run().runId())) throw unavailable();
            synchronized (this) {
                observe(window, observed);
                long realStart = System.nanoTime();
                long budget = remaining(window, nanoTime.getAsLong());
                while (!window.finished && !closed) {
                    waitFor(Math.min(remaining(window, nanoTime.getAsLong()), budget - elapsed(realStart, System.nanoTime())));
                }
                if (closed) throw unavailable();
            }
            return reply;
        } catch (RuntimeException failure) {
            fail(window);
            throw failure;
        }
    }

    private ResponseDTO deferResult(Window window, RequestDTO request, Route route, ObservedExchangeDTO observed) {
        synchronized (this) {
            if (!window.finished) {
                observe(window, observed);
                return new ResponseDTO(503, Map.of("Content-Type", List.of("application/json"),
                        "Cache-Control", List.of("no-store")),
                        "{\"code\":\"TIANJI_DEPENDENCY_UNAVAILABLE\",\"correlationId\":null}".getBytes(StandardCharsets.UTF_8));
            }
        }
        return forwardChecked(request, EXCHANGE_LIMIT, route);
    }

    private ResponseDTO dropCommittedResult(Window window, FaultWindowDTO candidate, RequestDTO request, Route route,
                                      ObservedExchangeDTO observed, String traceLink) {
        claim(window);
        try {
            var reply = forwardChecked(request, timeFor(window), route);
            requireActualReceipt(reply, observed);
            if (!witnesses.inboxCommitted(candidate, observed)) throw unavailable();
            finishResult(window, observed, traceLink);
        } catch (RuntimeException failure) {
            fail(window);
            throw failure;
        }
        throw new ConnectionDropped();
    }

    private ResponseDTO observeResend(Window window, FaultWindowDTO candidate, RequestDTO request, Route route,
                                      ObservedExchangeDTO observed, String traceLink) {
        if (!witnesses.replacementReady(candidate, observed)) throw unavailable();
        claim(window);
        try {
            var reply = forwardChecked(request, timeFor(window), route);
            requireActualReceipt(reply, observed);
            if (!witnesses.inboxCommitted(candidate, observed)) throw unavailable();
            finishResult(window, observed, traceLink);
            return reply;
        } catch (RuntimeException failure) {
            fail(window);
            throw failure;
        }
    }

    private static void requireActualReceipt(ResponseDTO reply, ObservedExchangeDTO observed) {
        if (reply.status() != 202) throw unavailable();
        var accepted = read(reply.body(), AgentResultReceipt.class);
        if (!observed.eventId().equals(accepted.eventId()) || !observed.fingerprint().equals(accepted.eventFingerprint())) {
            throw unavailable();
        }
    }

    private synchronized void bindVerifiedOccurrence(Window window, String occurrenceId) {
        requireOpen();
        requireRemaining(window, nanoTime.getAsLong());
        if (windows.get(window.definition.phase()) != window || window.finished
                || window.boundOccurrenceId != null && !window.boundOccurrenceId.equals(occurrenceId)) throw unavailable();
        window.boundOccurrenceId = occurrenceId;
    }

    private static FaultWindowDTO withOccurrence(FaultWindowDTO definition, String occurrenceId) {
        if (definition.occurrenceId() != null) return definition;
        return new FaultWindowDTO(definition.phase(), definition.tenantId(), definition.sourceId(), definition.instanceId(),
                occurrenceId, definition.jobId(), definition.eventId(), definition.fingerprint(), definition.budget());
    }

    private synchronized void claim(Window window) {
        requireOpen();
        requireRemaining(window, nanoTime.getAsLong());
        if (window.claimed || window.finished) throw unavailable();
        window.claimed = true;
    }

    synchronized String committedResultLink(FaultPhase phase) {
        requireOpen();
        if (!resultLinkReplay || phase != FaultPhase.DROP_COMMITTED_RESULT_202
                && phase != FaultPhase.OBSERVE_REPLACEMENT_RESEND) throw invalid();
        var window = windows.get(phase);
        if (window == null || !window.finished || window.observation == null
                || window.committedResultLink == null) throw unavailable();
        return window.committedResultLink;
    }

    private synchronized void finishResult(Window window, ObservedExchangeDTO exchange, String traceLink) {
        finish(window, exchange);
        window.committedResultLink = traceLink;
    }

    private synchronized void finish(Window window, ObservedExchangeDTO exchange) {
        observe(window, exchange);
        window.finished = true;
        notifyAll();
    }

    private synchronized void fail(Window window) {
        window.observation = null;
        window.finished = true;
        notifyAll();
    }

    private void observe(Window window, ObservedExchangeDTO exchange) {
        requireOpen();
        requireRemaining(window, nanoTime.getAsLong());
        if (window.finished) throw unavailable();
        var found = new ObservationDTO(window.definition.phase(), window.definition.tenantId(),
                window.definition.sourceId(), window.definition.instanceId(), window.boundOccurrenceId,
                exchange.eventId(), exchange.fingerprint());
        if (window.observation != null && !window.observation.equals(found)) throw unavailable();
        window.observation = found;
        notifyAll();
    }

    private Window matchingWindow(Route route, ObservedExchangeDTO exchange) {
        for (Window window : windows.values()) {
            var key = window.definition;
            if (window.finished || route != route(key.phase())
                    || exchange.tenantId() != null && exchange.tenantId() != key.tenantId()) continue;
            if (route == Route.WORKER_COMPLETE && !key.jobId().equals(exchange.jobId())) continue;
            if (route != Route.WORKER_COMPLETE && (!key.sourceId().equals(exchange.sourceId())
                    || !key.instanceId().equals(exchange.instanceId()))) continue;
            if (!safeId(exchange.occurrenceId(), 255)) throw invalid();
            if (window.boundOccurrenceId != null && !window.boundOccurrenceId.equals(exchange.occurrenceId())) {
                if (key.occurrenceId() == null) throw unavailable();
                continue;
            }
            if (key.eventId() != null && (!key.eventId().equals(exchange.eventId())
                    || !key.fingerprint().equals(exchange.fingerprint()))) throw unavailable();
            return window;
        }
        return null;
    }

    private Duration timeFor(Window window) {
        return Duration.ofNanos(Math.min(EXCHANGE_LIMIT.toNanos(), requireRemaining(window, nanoTime.getAsLong())));
    }

    private static long remaining(Window window, long now) {
        return window.definition.budget().toNanos() - elapsed(window.started, now);
    }

    private static long requireRemaining(Window window, long now) {
        long remaining = remaining(window, now);
        if (remaining <= 0) throw unavailable();
        return remaining;
    }

    private static long elapsed(long start, long now) {
        long delta = now - start;
        if (delta < 0) throw unavailable();
        return delta;
    }

    private void waitFor(long nanos) {
        if (nanos <= 0) throw unavailable();
        try {
            TimeUnit.NANOSECONDS.timedWait(this, nanos);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private static Route route(Direction direction, String method, String path) {
        if (direction == null || !"POST".equals(method) || path == null) throw invalid();
        return switch (direction) {
            case WORKER -> switch (path) {
                case WORKER + "acquire" -> Route.WORKER_ACQUIRE;
                case WORKER + "complete" -> Route.WORKER_COMPLETE;
                case WORKER + "fail" -> Route.WORKER_FAIL;
                default -> throw invalid();
            };
            case LINKED -> switch (path) {
                case LINKED + "submit" -> Route.LINKED_SUBMIT;
                case LINKED + "resolve", LINKED + "close" -> Route.LINKED_LOOKUP;
                default -> throw invalid();
            };
            case RESULT -> {
                if (!RESULT.equals(path)) throw invalid();
                yield Route.RESULT;
            }
        };
    }

    private static Route route(FaultPhase phase) {
        return switch (phase) {
            case DROP_WORKER_COMPLETE_AFTER_BUSINESS_COMMIT -> Route.WORKER_COMPLETE;
            case HOLD_LINKED_SUCCESS -> Route.LINKED_SUBMIT;
            case DEFER_RESULT_UNTIL_TIMER_OR_RESTART, DROP_COMMITTED_RESULT_202, OBSERVE_REPLACEMENT_RESEND -> Route.RESULT;
        };
    }

    private static Direction direction(FaultPhase phase) {
        return switch (route(phase)) {
            case WORKER_COMPLETE -> Direction.WORKER;
            case LINKED_SUBMIT -> Direction.LINKED;
            case RESULT -> Direction.RESULT;
            default -> throw invalid();
        };
    }

    private static Decision decision(FaultPhase phase) {
        return switch (phase) {
            case DROP_WORKER_COMPLETE_AFTER_BUSINESS_COMMIT -> Decision.DROP_BEFORE_UPSTREAM;
            case HOLD_LINKED_SUCCESS -> Decision.HOLD_RESPONSE;
            case DEFER_RESULT_UNTIL_TIMER_OR_RESTART -> Decision.DEFER_RETRYABLY;
            case DROP_COMMITTED_RESULT_202 -> Decision.DROP_RESPONSE;
            case OBSERVE_REPLACEMENT_RESEND -> Decision.OBSERVE_RESEND;
        };
    }

    private static void validateWindow(FaultWindowDTO window) {
        if (window == null || window.phase() == null || window.tenantId() <= 0
                || !safeId(window.sourceId(), 128) || !safeId(window.instanceId(), 255)) throw invalid();
        boolean mayResolveOccurrence = switch (window.phase()) {
            case HOLD_LINKED_SUCCESS, DEFER_RESULT_UNTIL_TIMER_OR_RESTART, DROP_COMMITTED_RESULT_202 -> true;
            case DROP_WORKER_COMPLETE_AFTER_BUSINESS_COMMIT, OBSERVE_REPLACEMENT_RESEND -> false;
        };
        if (window.occurrenceId() == null ? !mayResolveOccurrence : !safeId(window.occurrenceId(), 255)) throw invalid();
        bounded(window.budget(), window.phase() == FaultPhase.HOLD_LINKED_SUCCESS ? MAX_HOLD : MAX_WINDOW);
        boolean worker = window.phase() == FaultPhase.DROP_WORKER_COMPLETE_AFTER_BUSINESS_COMMIT;
        if (worker ? !safeId(window.jobId(), 255) : window.jobId() != null) throw invalid();
        if (window.phase() == FaultPhase.OBSERVE_REPLACEMENT_RESEND && window.eventId() == null
                || (window.eventId() == null) != (window.fingerprint() == null)
                || window.eventId() != null && (route(window.phase()) != Route.RESULT
                || !hash(window.eventId()) || !hash(window.fingerprint()))) throw invalid();
    }

    private static boolean sameEvent(FaultWindowDTO next, ObservationDTO prior) {
        return next.tenantId() == prior.tenantId() && next.sourceId().equals(prior.sourceId())
                && next.instanceId().equals(prior.instanceId()) && next.occurrenceId().equals(prior.occurrenceId())
                && next.eventId().equals(prior.eventId()) && next.fingerprint().equals(prior.fingerprint());
    }

    private static boolean safeId(String value, int limit) {
        return value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= limit
                && value.codePoints().noneMatch(c -> c < 32 || c == 127 || c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE);
    }

    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static Duration bounded(Duration duration, Duration maximum) {
        if (duration == null || duration.isZero() || duration.isNegative() || duration.compareTo(maximum) > 0) throw invalid();
        return duration;
    }

    private static void validateRequest(RequestDTO request, Route route) {
        if (request.body.length == 0 || request.body.length > route.requestLimit) throw invalid();
        String type = header(request.headers(), "Content-Type");
        String bearer = header(request.headers(), "Authorization");
        if (!"application/json".equalsIgnoreCase(type)
                || bearer == null || bearer.length() > 8_192 || !bearer.matches("Bearer [A-Za-z0-9._~+/-]+=*")
                || header(request.headers(), "Content-Encoding") != null) throw invalid();
        for (String allowed : REQUEST_HEADERS) header(request.headers(), allowed);
    }

    private static String resultTraceLink(RequestDTO request, Route route, boolean resultLinkReplay) {
        if (!resultLinkReplay || route != Route.RESULT) return null;
        String traceLink = header(request.headers(), AgentResultTraceLinkCarrier.HEADER);
        if (!AgentResultTraceLinkCarrier.isValid(traceLink)) throw invalid();
        return traceLink;
    }

    private static String header(Map<String, List<String>> headers, String key) {
        String selected = null;
        for (var entry : headers.entrySet()) {
            if (!key.equalsIgnoreCase(entry.getKey())) continue;
            if (selected != null || entry.getValue().size() != 1
                    || !safeId(entry.getValue().getFirst(), 8_192)) throw invalid();
            selected = entry.getValue().getFirst();
        }
        return selected;
    }

    private static Decoded decode(Route route, RequestDTO request) {
        var rawTenant = header(request.headers(), TENANT);
        Long tenant = null;
        if (rawTenant != null) {
            if (!rawTenant.matches("[1-9][0-9]{0,18}")) throw invalid();
            try {
                tenant = Long.parseLong(rawTenant);
            } catch (NumberFormatException malformed) {
                throw invalid();
            }
        }
        JsonNode json = read(request.body, JsonNode.class);
        if (json == null || !json.isObject()) throw invalid();
        String source = null;
        String instance = null;
        String occurrence = null;
        String job = null;
        String event = null;
        String fingerprint = null;
        String token = null;
        String receipt = null;
        if (route == Route.WORKER_COMPLETE) {
            var complete = fromTree(json, CompleteWorkerTaskCommand.class);
            job = complete.taskId();
            occurrence = complete.activityOccurrenceId();
            token = complete.leaseToken();
            receipt = complete.businessReceiptId();
        } else if (route == Route.LINKED_SUBMIT || route == Route.LINKED_LOOKUP) {
            var link = json.get("link");
            source = field(link, "sourceId");
            instance = field(link, "scopeInstanceId");
            occurrence = field(link, "activityOccurrenceId");
        } else if (route == Route.RESULT) {
            var terminal = fromTree(json, AgentTerminalEvent.class);
            if (tenant == null || tenant != terminal.tenantId()) throw invalid();
            source = terminal.link().sourceId();
            instance = terminal.link().scopeInstanceId();
            occurrence = terminal.link().activityOccurrenceId();
            event = terminal.eventId();
            fingerprint = terminal.fingerprint();
        }
        return new Decoded(new ObservedExchangeDTO(request.direction(), request.method(), request.rawPath(), tenant,
                source, instance, occurrence, job, event, fingerprint), token, receipt);
    }

    private static <T> T fromTree(JsonNode json, Class<T> type) {
        try {
            return JSON.treeToValue(json, type);
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    private static String field(JsonNode object, String key) {
        if (object == null || !object.isObject()) throw invalid();
        var value = object.get(key);
        if (value == null || !value.isString() || !safeId(value.asString(), 255)) throw invalid();
        return value.asString();
    }

    private static <T> T read(byte[] bytes, Class<T> type) {
        try {
            return JSON.readValue(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(), type);
        } catch (RuntimeException | CharacterCodingException malformed) {
            throw invalid();
        }
    }

    private ResponseDTO forwardChecked(RequestDTO request, Duration timeout, Route route) {
        var response = transport.forward(request, timeout);
        if (response == null || response.status() < 200 || response.status() > 599
                || response.status() >= 300 && response.status() < 400 || response.body.length > route.responseLimit) {
            throw unavailable();
        }
        return response;
    }

    private static URI localOrigin(String raw) {
        try {
            URI uri = URI.create(raw);
            if (!"https".equals(uri.getScheme()) || !Set.of("127.0.0.1", "[::1]").contains(uri.getHost())
                    || !Set.of("", "/").contains(uri.getRawPath()) || uri.getPort() < 1024 || uri.getPort() > 65_535
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !uri.normalize().equals(uri)) throw invalid();
            return uri;
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    private static ProxySelector noProxy() {
        return new ProxySelector() {
            @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
            @Override public void connectFailed(URI uri, SocketAddress address, IOException failure) { }
        };
    }

    private void bind(URI origin, Direction direction, SSLContext tls) throws IOException {
        var address = InetAddress.getByName(origin.getHost());
        if (!address.isLoopbackAddress()) throw invalid();
        var server = HttpsServer.create(new InetSocketAddress(address, origin.getPort()), 8);
        listeners.add(server);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        server.setExecutor(listenerExecutor);
        server.createContext("/", exchange -> accept(direction, origin, exchange));
    }

    private void accept(Direction direction, URI origin, HttpExchange exchange) throws IOException {
        var deadline = exchangeDeadlines.schedule(exchange::close, EXCHANGE_LIMIT.toNanos(), TimeUnit.NANOSECONDS);
        try (exchange) {
            try {
                var uri = exchange.getRequestURI();
                var method = exchange.getRequestMethod();
                String path = uri.getRawPath();
                var route = route(direction, method, path);
                if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || !origin.getRawAuthority().equalsIgnoreCase(header(exchange.getRequestHeaders(), "Host"))) throw invalid();
                var body = exchange.getRequestBody().readNBytes(route.requestLimit + 1);
                var reply = exchange(new RequestDTO(direction, method, path, exchange.getRequestHeaders(), body));
                reply.headers().forEach((name, values) -> {
                    if (RESPONSE_HEADERS.stream().anyMatch(name::equalsIgnoreCase)) exchange.getResponseHeaders().put(name, values);
                });
                exchange.sendResponseHeaders(reply.status(), reply.body.length == 0 ? -1 : reply.body.length);
                if (reply.body.length != 0) exchange.getResponseBody().write(reply.body);
            } catch (ConnectionDropped discarded) {
                // End this exchange without publishing any application response.
            } catch (IllegalArgumentException invalid) {
                exchange.sendResponseHeaders(400, -1);
            } catch (IllegalStateException unavailable) {
                exchange.sendResponseHeaders(503, -1);
            }
        } finally {
            deadline.cancel(false);
        }
    }

    static HttpRequest upstreamRequest(URI origin, RequestDTO request, Duration timeout, boolean resultLinkReplay) {
        var route = route(request.direction(), request.method(), request.rawPath());
        String traceLink = resultTraceLink(request, route, resultLinkReplay);
        URI exactOrigin = traceLink == null ? origin : localOrigin(origin.toASCIIString());
        var upstream = HttpRequest.newBuilder(exactOrigin.resolve(request.rawPath())).timeout(timeout);
        for (String name : REQUEST_HEADERS) {
            String value = header(request.headers(), name);
            if (value != null) upstream.header(name, value);
        }
        if (traceLink != null) upstream.header(AgentResultTraceLinkCarrier.HEADER, traceLink);
        return upstream.POST(HttpRequest.BodyPublishers.ofByteArray(request.body)).build();
    }

    private static ResponseDTO forward(HttpClient client, URI origin, RequestDTO request, Duration timeout,
                                       boolean resultLinkReplay) {
        var route = route(request.direction(), request.method(), request.rawPath());
        var pending = client.sendAsync(upstreamRequest(origin, request, timeout, resultLinkReplay),
                ignored -> new BoundedResponse(route.responseLimit));
        try {
            var response = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return new ResponseDTO(response.statusCode(), response.headers().map(), response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            pending.cancel(true);
            throw unavailable();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            pending.cancel(true);
            throw unavailable();
        }
    }

    @Override public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            windows.clear();
            notifyAll();
        }
        listeners.forEach(listener -> listener.stop(0));
        if (listenerExecutor != null) listenerExecutor.shutdownNow();
        if (exchangeDeadlines != null) exchangeDeadlines.shutdownNow();
        if (operatorHttpClient != null) operatorHttpClient.shutdownNow();
    }

    private void requireOpen() {
        if (closed) throw unavailable();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Expense acceptance fault relay input is invalid");
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Expense acceptance fault relay is unavailable");
    }

    enum Direction { WORKER, LINKED, RESULT }

    enum FaultPhase {
        DROP_WORKER_COMPLETE_AFTER_BUSINESS_COMMIT, HOLD_LINKED_SUCCESS, DEFER_RESULT_UNTIL_TIMER_OR_RESTART,
        DROP_COMMITTED_RESULT_202, OBSERVE_REPLACEMENT_RESEND
    }

    enum Decision { PASS_THROUGH, DROP_BEFORE_UPSTREAM, HOLD_RESPONSE, DEFER_RETRYABLY, DROP_RESPONSE, OBSERVE_RESEND }

    record FaultWindowDTO(FaultPhase phase, long tenantId, String sourceId, String instanceId, String occurrenceId,
                          String jobId, String eventId, String fingerprint, Duration budget) { }

    record ObservedExchangeDTO(Direction direction, String method, String rawPath, Long tenantId, String sourceId,
                               String instanceId, String occurrenceId, String jobId, String eventId,
                               String fingerprint) { }

    record ObservationDTO(FaultPhase phase, long tenantId, String sourceId, String instanceId, String occurrenceId,
                          String eventId, String fingerprint) { }

    record RequestDTO(Direction direction, String method, String rawPath, Map<String, List<String>> headers, byte[] body) {
        RequestDTO {
            headers = copyHeaders(headers);
            body = body.clone();
        }
        @Override public byte[] body() { return body.clone(); }
        @Override public String toString() { return "Expense relay request"; }
    }

    record ResponseDTO(int status, Map<String, List<String>> headers, byte[] body) {
        ResponseDTO {
            headers = copyHeaders(headers);
            body = body.clone();
        }
        @Override public byte[] body() { return body.clone(); }
        @Override public String toString() { return "Expense relay response"; }
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> headers) {
        var copy = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((key, values) -> {
            if (copy.putIfAbsent(key, List.copyOf(values)) != null) throw invalid();
        });
        return Map.copyOf(copy);
    }

    @FunctionalInterface interface Transport { ResponseDTO forward(RequestDTO request, Duration timeout); }

    interface Witnesses {
        boolean owned(FaultWindowDTO window, ObservedExchangeDTO request);
        boolean businessCommitted(FaultWindowDTO window, ObservedExchangeDTO request, String leaseToken, String receiptId);
        boolean agentCommitted(FaultWindowDTO window, ObservedExchangeDTO request, String runId);
        boolean inboxCommitted(FaultWindowDTO window, ObservedExchangeDTO request);
        boolean replacementReady(FaultWindowDTO window, ObservedExchangeDTO request);
    }

    static final class ConnectionDropped extends RuntimeException {
        ConnectionDropped() { super("Expense fixture discarded a transport connection"); }
    }

    private record Decoded(ObservedExchangeDTO observed, String leaseToken, String receiptId) {
        @Override public String toString() { return "Expense relay scope"; }
    }

    private enum Route {
        WORKER_ACQUIRE(262_144, 2_097_152), WORKER_COMPLETE(262_144, 2_097_152), WORKER_FAIL(262_144, 2_097_152),
        LINKED_SUBMIT(2_097_152, 65_536), LINKED_LOOKUP(16_384, 65_536), RESULT(16_384, 16_384);

        private final int requestLimit;
        private final int responseLimit;

        Route(int requestLimit, int responseLimit) {
            this.requestLimit = requestLimit;
            this.responseLimit = responseLimit;
        }
    }

    private static final class Window {
        private final FaultWindowDTO definition;
        private final long started;
        private ObservationDTO observation;
        private String committedResultLink;
        private String boundOccurrenceId;
        private boolean claimed;
        private boolean finished;

        Window(FaultWindowDTO definition, long started) {
            this.definition = definition;
            this.started = started;
            this.boundOccurrenceId = definition.occurrenceId();
        }
    }

    private static final class BoundedResponse implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit;
        private Flow.Subscription subscription;

        BoundedResponse(int limit) { this.limit = limit; }

        @Override public java.util.concurrent.CompletionStage<byte[]> getBody() { return result; }

        @Override public void onSubscribe(Flow.Subscription subscriber) {
            subscription = subscriber;
            subscriber.request(1);
        }

        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(unavailable());
                    return;
                }
                var chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override public void onError(Throwable failure) { result.completeExceptionally(unavailable()); }

        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
