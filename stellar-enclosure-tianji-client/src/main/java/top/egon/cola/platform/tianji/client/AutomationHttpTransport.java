package top.egon.cola.platform.tianji.client;

import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** A bounded bearer-authenticated transport for fixed automation v2 operations. */
final class AutomationHttpTransport implements AutoCloseable {
    static final String V2 = "/api/tianji/v2/";
    static final int MAX_DRAFT_REQUEST_BYTES = 5 * 1024 * 1024;
    static final int MAX_DRAFT_RESPONSE_BYTES = 5 * 1024 * 1024;
    static final int MAX_STANDARD_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ERROR_BYTES = 4096;
    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]", "::1");
    private static final String ENCODED_SEGMENT = "(?:[A-Za-z0-9._*-]++|%[0-9A-F]{2})++";
    private static final Pattern PATH = Pattern.compile(V2 + ENCODED_SEGMENT + "(?:/" + ENCODED_SEGMENT + ")*");
    private static final Pattern QUERY_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,63}");
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Pattern DEFINITION_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,127}");
    private static final Pattern RELEASE_VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    private static final Pattern COMMAND_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");
    private final URI baseUri;
    private final HttpClient http;
    private final Supplier<String> tokens;
    private final Long tenantId;
    private final BoundedServiceCalls calls;

    AutomationHttpTransport(URI baseUri, HttpClient http, Supplier<String> tokens, Duration timeout, Long tenantId) {
        String host = baseUri == null ? null : baseUri.getHost();
        if (host == null || baseUri.getRawUserInfo() != null || baseUri.getRawQuery() != null || baseUri.getRawFragment() != null
                || !baseUri.normalize().equals(baseUri) || !baseUri.getRawPath().equals(baseUri.getPath())
                || !("https".equalsIgnoreCase(baseUri.getScheme())
                || "http".equalsIgnoreCase(baseUri.getScheme()) && LOOPBACK.contains(host.toLowerCase(Locale.ROOT)))) {
            throw new IllegalArgumentException("Automation service URI is invalid");
        }
        String base = baseUri.toASCIIString();
        this.baseUri = URI.create(base.endsWith("/") ? base : base + "/");
        this.http = Objects.requireNonNull(http, "http");
        if (http.followRedirects() != HttpClient.Redirect.NEVER || http.authenticator().isPresent()) {
            throw new IllegalArgumentException("Automation HTTP client requires explicit bearer authentication without redirects");
        }
        if (tenantId != null && tenantId <= 0) throw new IllegalArgumentException("Automation tenant assertion is invalid");
        this.tenantId = tenantId;
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.calls = new BoundedServiceCalls(timeout, 4, "automation-token-", AutomationHttpTransport::unavailable);
    }

    ResponseVO sendJson(String method, String path, Map<String, String> query, Object body,
                         int requestLimit, int expectedStatus, int responseLimit, Set<String> extraConflicts) {
        byte[] bytes;
        try { bytes = body == null ? null : AutomationResponseCodec.encode(body); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Automation request cannot be encoded"); }
        return send(method, path, query, bytes, requestLimit, expectedStatus, responseLimit, extraConflicts);
    }

    ResponseVO send(String method, String path, Map<String, String> query, byte[] body,
                    int requestLimit, int expectedStatus, int responseLimit, Set<String> extraConflicts) {
        return send(method, path, query, body, requestLimit, expectedStatus, responseLimit, extraConflicts, Map.of());
    }

    /** Sends an exact v2 wire snapshot. Extra error statuses must belong to the operation's published contract. */
    ResponseVO send(String method, String path, Map<String, String> query, byte[] body,
                    int requestLimit, int expectedStatus, int responseLimit, Set<String> extraConflicts,
                    Map<Integer, String> extraErrors) {
        if (!Set.of("GET", "POST", "PUT").contains(method) || "GET".equals(method) != (body == null)
                || requestLimit < 0 || responseLimit <= 0 || expectedStatus < 200 || expectedStatus >= 300) {
            throw new IllegalArgumentException("Automation request operation is invalid");
        }
        if (body != null && (body.length == 0 || body.length > requestLimit)) {
            throw new IllegalArgumentException("Automation request exceeds its size bounds");
        }
        URI uri = endpoint(path, query);
        long started = calls.begin();
        String token = calls.token(tokens, started);
        var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofNanos(calls.remaining(started)))
                .header("Authorization", "Bearer " + token).header("Accept", "application/json").header("Cache-Control", "no-store");
        if (tenantId != null) builder.header("X-Egon-Tianji-Tenant", tenantId.toString());
        HttpRequest request = body == null ? builder.GET().build() : builder.header("Content-Type", "application/json")
                .method(method, new SingleSendBody(body)).build();
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending;
        try {
            pending = calls.track(http.sendAsync(request, info -> new BoundedBodySubscriber(
                    info.statusCode() == expectedStatus ? responseLimit : MAX_ERROR_BYTES)));
        } catch (RuntimeException failure) {
            throw unavailable();
        }
        HttpResponse<byte[]> response;
        try {
            response = pending.get(calls.remaining(started), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (TimeoutException | CancellationException failure) {
            pending.cancel(true);
            throw unavailable();
        } catch (ExecutionException failure) {
            if (BoundedBodySubscriber.tooLarge(failure)) {
                throw new ProcessClientException(0, "TIANJI_RESPONSE_TOO_LARGE", null, null);
            }
            throw unavailable();
        } catch (RuntimeException expired) {
            pending.cancel(true);
            throw expired;
        } finally {
            calls.untrack(pending);
        }
        int status = response.statusCode();
        if (!uri.equals(response.uri())) throw AutomationResponseCodec.invalidResponse(status);
        if (status == 415 && response.body().length == 0 && extraErrors.containsKey(status)) {
            throw new ProcessClientException(status, extraErrors.get(status), null, null);
        }
        var contentTypes = response.headers().allValues("Content-Type");
        if (contentTypes.size() != 1
                || !"application/json".equalsIgnoreCase(contentTypes.getFirst().split(";", 2)[0].trim())
                || !List.of("no-store").equals(response.headers().allValues("Cache-Control"))) {
            throw AutomationResponseCodec.invalidResponse(status);
        }
        if (status != expectedStatus) throw AutomationResponseCodec.error(response.body(), status, token, extraConflicts, extraErrors);
        return new ResponseVO(status, response.body());
    }

    private URI endpoint(String path, Map<String, String> query) {
        if (path == null || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Automation path is invalid");
        }
        for (String segment : path.substring(V2.length()).split("/")) {
            String decoded = java.net.URLDecoder.decode(segment, StandardCharsets.UTF_8);
            if (isDotSegment(decoded) || !encodePart(decoded).equals(segment)) {
                throw new IllegalArgumentException("Automation path segment is invalid");
            }
        }
        var suffix = new StringJoiner("&", "?", "").setEmptyValue("");
        query.forEach((name, value) -> {
            if (!QUERY_NAME.matcher(name).matches() || value == null || value.isEmpty() || value.length() > 2048
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(value)
                    || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Automation query is invalid");
            }
            suffix.add(name + "=" + encodePart(value));
        });
        return baseUri.resolve(path.substring(1) + suffix);
    }

    static String segment(String value) {
        if (value == null || !SEGMENT.matcher(value).matches() || isDotSegment(value)) {
            throw new IllegalArgumentException("Automation resource ID is invalid");
        }
        return encodePart(value);
    }

    /** Encodes a native identity as one path segment, without interpreting its punctuation. */
    static String opaqueSegment(String value) {
        require(value != null && !value.isBlank() && value.codePointCount(0, value.length()) <= 255
                && value.chars().noneMatch(Character::isISOControl) && !isDotSegment(value)
                && StandardCharsets.UTF_8.newEncoder().canEncode(value));
        return encodePart(value);
    }

    private static boolean isDotSegment(String value) { return ".".equals(value) || "..".equals(value); }

    static void definitionKey(String input) { require(input != null && DEFINITION_KEY.matcher(input).matches()); }
    static void releaseVersion(String input) { require(input != null && RELEASE_VERSION.matcher(input).matches()); }
    static void commandId(String input) { require(input != null && COMMAND_ID.matcher(input).matches()); }
    static void revision(long value, int minimum) { require(value >= minimum && value <= ProcessTypedJson.V2_MAX_WIRE_INTEGER); }
    static void require(boolean accepted) { if (!accepted) throw new IllegalArgumentException("Automation request is invalid"); }

    private static String encodePart(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static ProcessClientException unavailable() {
        return new ProcessClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null);
    }

    @Override public void close() { calls.close(); }

    record ResponseVO(int status, byte[] body) { }

    /** HttpClient may resubscribe a publisher even when the SDK invokes sendAsync only once. */
    static final class SingleSendBody implements HttpRequest.BodyPublisher {
        private static final Flow.Subscription NO_BODY = new Flow.Subscription() {
            @Override public void request(long count) { }
            @Override public void cancel() { }
        };
        private final HttpRequest.BodyPublisher body;
        private final AtomicBoolean subscribed = new AtomicBoolean();

        SingleSendBody(byte[] bytes) {
            body = HttpRequest.BodyPublishers.ofByteArray(bytes.clone());
        }

        @Override public long contentLength() { return body.contentLength(); }

        @Override public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            Objects.requireNonNull(subscriber, "subscriber");
            if (subscribed.compareAndSet(false, true)) {
                body.subscribe(subscriber);
            } else {
                subscriber.onSubscribe(NO_BODY);
                subscriber.onError(new IllegalStateException("Automation request body has already been sent"));
            }
        }
    }
}
