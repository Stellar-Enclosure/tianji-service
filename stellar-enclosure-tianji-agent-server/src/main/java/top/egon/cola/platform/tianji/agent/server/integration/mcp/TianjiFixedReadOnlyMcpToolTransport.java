package top.egon.cola.platform.tianji.agent.server.integration.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.common.trace.TraceContext;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService.McpToolMaterialVO;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyMcpToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.server.integration.mcp.TianjiFixedReadOnlyMcpToolBindings.BackendVO;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiProcessToolCredentialVerifier;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiProcessToolCredentialVerifier.CredentialVO;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiProcessToolCredentialVerifier.TargetVO;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TenantIssuerAuthenticationManagerComponent;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedPrincipalConverter;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** Fixed read-only MCP capability over a verified request-local SERVICE credential. */
public final class TianjiFixedReadOnlyMcpToolTransport implements ReadOnlyMcpToolTransport, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TianjiFixedReadOnlyMcpToolTransport.class);
    private static final Set<String> PREPARED_HEADERS = Set.of("content-type", "accept", "mcp-protocol-version", "mcp-session-id");
    private final TianjiFixedReadOnlyMcpToolBindings bindings;
    private final IdpServiceOAuth2Client oauth;
    private final TenantIssuerAuthenticationManagerComponent authentication;
    private final VerifiedPrincipalConverter converter;
    private final TrustedIssuerRegistry issuers;
    private final HttpClient http;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final TianjiProcessToolCredentialVerifier credentialVerifier;
    private final PublicationAuthority publicationAuthority;
    private final ThreadPoolExecutor calls;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Runnable> cancellations = ConcurrentHashMap.newKeySet();

    public TianjiFixedReadOnlyMcpToolTransport(TianjiFixedReadOnlyMcpToolBindings bindings, IdpServiceOAuth2Client oauth,
            TenantIssuerAuthenticationManagerComponent authentication, VerifiedPrincipalConverter converter, TrustedIssuerRegistry issuers,
            HttpClient http, int concurrency) {
        this(bindings, oauth, authentication, converter, issuers, http, concurrency, Clock.systemUTC(), System::nanoTime);
    }

    public TianjiFixedReadOnlyMcpToolTransport(TianjiFixedReadOnlyMcpToolBindings bindings, IdpServiceOAuth2Client oauth,
            TenantIssuerAuthenticationManagerComponent authentication, VerifiedPrincipalConverter converter, TrustedIssuerRegistry issuers,
            HttpClient http, int concurrency, PublicationAuthority publicationAuthority) {
        this(bindings, oauth, authentication, converter, issuers, http, concurrency, Clock.systemUTC(), System::nanoTime,
                Objects.requireNonNull(publicationAuthority, "publication authority"));
    }

    TianjiFixedReadOnlyMcpToolTransport(TianjiFixedReadOnlyMcpToolBindings bindings, IdpServiceOAuth2Client oauth,
            TenantIssuerAuthenticationManagerComponent authentication, VerifiedPrincipalConverter converter, TrustedIssuerRegistry issuers,
            HttpClient http, int concurrency, Clock clock, LongSupplier nanoTime) {
        this(bindings, oauth, authentication, converter, issuers, http, concurrency, clock, nanoTime, null);
    }

    TianjiFixedReadOnlyMcpToolTransport(TianjiFixedReadOnlyMcpToolBindings bindings, IdpServiceOAuth2Client oauth,
            TenantIssuerAuthenticationManagerComponent authentication, VerifiedPrincipalConverter converter, TrustedIssuerRegistry issuers,
            HttpClient http, int concurrency, Clock clock, LongSupplier nanoTime, PublicationAuthority publicationAuthority) {
        this.bindings = Objects.requireNonNull(bindings, "bindings"); this.oauth = Objects.requireNonNull(oauth, "oauth");
        this.authentication = Objects.requireNonNull(authentication, "authentication"); this.converter = Objects.requireNonNull(converter, "converter");
        this.issuers = Objects.requireNonNull(issuers, "issuers"); this.http = Objects.requireNonNull(http, "http");
        this.clock = Objects.requireNonNull(clock, "clock"); this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.publicationAuthority = publicationAuthority;
        if (concurrency < 1 || concurrency > 8 || http.followRedirects() != HttpClient.Redirect.NEVER
                || http.cookieHandler().isPresent() || http.authenticator().isPresent()) throw new IllegalArgumentException("Fixed MCP transport configuration is invalid");
        credentialVerifier = new TianjiProcessToolCredentialVerifier(this.oauth, this.authentication, this.converter, this.issuers, this.clock);
        calls = new ThreadPoolExecutor(0, concurrency, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                Thread.ofVirtual().inheritInheritableThreadLocals(false).name("tianji-fixed-mcp-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean usesBindings(TianjiFixedReadOnlyMcpToolBindings candidate) { return candidate == bindings; }
    public boolean usesOAuth(IdpServiceOAuth2Client candidate) { return candidate == oauth; }
    public boolean usesAuthentication(TenantIssuerAuthenticationManagerComponent candidate) { return candidate == authentication; }
    public boolean usesConverter(VerifiedPrincipalConverter candidate) { return candidate == converter; }
    public boolean usesIssuerRegistry(TrustedIssuerRegistry candidate) { return candidate == issuers; }
    public boolean usesHttpClient(HttpClient candidate) { return candidate == http; }

    @FunctionalInterface
    public interface PublicationAuthority {
        void requireCurrent(TrustedAgentActor actor, McpToolMaterialVO material);
    }

    @Override public <T> T withVerifiedCall(Request request, Callback<T> callback) {
        if (request == null || callback == null) throw new IllegalArgumentException("Fixed MCP request and callback are required");
        if (closed.get() || Thread.currentThread().isInterrupted()) throw failure(FailureKind.CANCELLED);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
        Instant startedAt = clock.instant(); long startedNanos = nanoTime.getAsLong();
        TianjiFixedReadOnlyMcpToolBindings.ResolvedBindingVO resolved;
        try { resolved = Objects.requireNonNull(bindings.resolve(request)); }
        catch (RuntimeException privateBinding) { throw failure(FailureKind.CREDENTIAL_UNAVAILABLE); }
        var backend = resolved.binding().backend();
        var deadline = earlier(earlier(request.deadlineAt(), request.run().deadlineAt()), startedAt.plus(backend.timeout()));
        var scope = new CallScope(new WindowVO(startedAt, startedNanos, deadline), backend, TraceContext.capture());
        Runnable cancel = scope::cancel; cancellations.add(cancel);
        try {
            scope.remaining();
            var task = new FutureTask<T>(() -> {
                try (var ignored = scope.trace.open()) { scope.remaining(); return verifiedCall(request, callback, scope); }
                finally { scope.clearBearer(); scope.localClose(); }
            });
            synchronized (scope.publishLock) { scope.remaining(); scope.worker = task; }
            calls.execute(task);
            T value = task.get(scope.remaining(), TimeUnit.NANOSECONDS);
            current(scope); return value;
        } catch (InterruptedException interrupted) {
            scope.cancel(); Thread.currentThread().interrupt(); throw failure(FailureKind.CANCELLED);
        } catch (TimeoutException timeout) { scope.cancel(); throw failure(FailureKind.DEADLINE_EXCEEDED); }
        catch (CancellationException cancelled) { throw failure(FailureKind.CANCELLED); }
        catch (RejectedExecutionException busy) { throw failure(closed.get() ? FailureKind.CANCELLED : FailureKind.CREDENTIAL_UNAVAILABLE); }
        catch (ExecutionException execution) {
            if (execution.getCause() instanceof Failure rejected) throw failure(rejected.kind());
            if (execution.getCause() instanceof AgentLedgerException rejected) throw ledgerFailure(rejected);
            throw failure(scope.toolsPossiblySent ? FailureKind.OPERATION_RESULT_UNKNOWN : FailureKind.CREDENTIAL_UNAVAILABLE);
        } catch (Failure rejected) { throw failure(rejected.kind()); }
        catch (RuntimeException privateFailure) { throw failure(scope.toolsPossiblySent ? FailureKind.OPERATION_RESULT_UNKNOWN : FailureKind.CREDENTIAL_UNAVAILABLE); }
        finally { cancel.run(); cancellations.remove(cancel); }
    }

    private <T> T verifiedCall(Request request, Callback<T> callback, CallScope scope) {
        CredentialVO credential;
        try {
            scope.remaining();
            if (TransactionSynchronizationManager.isActualTransactionActive()) throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
            if (publicationAuthority != null) {
                publicationAuthority.requireCurrent(request.run().actor(), Objects.requireNonNull(request.siMaterial()));
                scope.remaining();
            }
            credential = credentialVerifier.credentials(target(scope.backend), scope.window.deadlineAt(), scope::remaining, scope::publishBearer, scope::currentBearer);
            synchronized (scope.publishLock) { scope.remaining(); scope.credential = credential; }
            current(scope);
        } catch (Failure rejected) { throw failure(rejected.kind()); }
        catch (RuntimeException privateCredential) { throw failure(FailureKind.CREDENTIAL_UNAVAILABLE); }
        Instant ceiling = earlier(credential.expiresAt(), scope.window.deadlineAt()).truncatedTo(ChronoUnit.MICROS);
        if (!ceiling.isAfter(clock.instant())) throw failure(FailureKind.DEADLINE_EXCEEDED);
        Thread owner = Thread.currentThread(); var callbackActive = new AtomicBoolean(true); var consumed = new AtomicBoolean();
        OneShotCall operation = callDeadlineAt -> {
            if (Thread.currentThread() != owner || !callbackActive.get() || TransactionSynchronizationManager.isActualTransactionActive()
                    || !consumed.compareAndSet(false, true)) throw failure(FailureKind.CANCELLED);
            scope.narrowCallDeadline(callDeadlineAt, ceiling); current(scope);
            return call(request, scope);
        };
        T value;
        try { value = callback.apply(new ReadOnlyDecisionToolTransport.VerifiedExecutor(credential.actor(), ceiling), operation); }
        catch (Failure rejected) { throw failure(rejected.kind()); }
        catch (AgentLedgerException rejected) { throw ledgerFailure(rejected); }
        catch (RuntimeException privateCallback) { throw failure(scope.toolsPossiblySent ? FailureKind.OPERATION_RESULT_UNKNOWN : FailureKind.OPERATION_NOT_SENT); }
        finally { callbackActive.set(false); scope.closeOperation(); }
        scope.cleanup(); scope.localClose(); current(scope); return value;
    }

    private CallObservation call(Request request, CallScope scope) {
        McpSyncClient client;
        try {
            synchronized (scope.publishLock) {
                current(scope); Duration remaining = Duration.ofNanos(scope.remaining());
                scope.protocol = new TianjiMcpJsonRpcTransport(scope.backend, scope::exchange);
                client = McpClient.sync(scope.protocol).requestTimeout(remaining).initializationTimeout(remaining)
                        .capabilities(McpSchema.ClientCapabilities.builder().build())
                        .clientInfo(McpSchema.Implementation.builder("tianji-readonly-mcp", "1.0").build()).enableCallToolSchemaCaching(false).build();
                scope.client = client;
            }
            Objects.requireNonNull(client.initialize()); Objects.requireNonNull(client.listTools((String) null));
        } catch (RuntimeException privateProfile) { throw protocolFailure(privateProfile, FailureKind.PROFILE_UNAVAILABLE); }
        McpSchema.CallToolResult result;
        try { result = client.callTool(new McpSchema.CallToolRequest(scope.backend.remoteToolName(), scope.backend.inputSchema().values(request.arguments()), null)); }
        catch (McpError denied) { throw failure(FailureKind.OPERATION_DENIED); }
        catch (RuntimeException privateCall) { throw protocolFailure(privateCall, scope.toolsPossiblySent ? FailureKind.OPERATION_RESULT_UNKNOWN : FailureKind.OPERATION_NOT_SENT); }
        // A validated response is a fact; the callback receives it before cleanup or authority postchecks.
        try {
            if (result == null || result.isError() == null) throw failure(FailureKind.OPERATION_INVALID_RESPONSE);
            if (result.isError()) return new CallObservation(Status.TOOL_ERROR, Map.of());
            if (!(result.structuredContent() instanceof Map<?, ?> raw)) throw failure(FailureKind.OPERATION_INVALID_RESPONSE);
            var output = new HashMap<String, Object>();
            for (var entry : raw.entrySet()) { if (!(entry.getKey() instanceof String key)) throw failure(FailureKind.OPERATION_INVALID_RESPONSE); output.put(key, entry.getValue()); }
            return new CallObservation(Status.SUCCESS, scope.backend.outputSchema().values(output));
        } catch (RuntimeException invalid) { throw failure(FailureKind.OPERATION_INVALID_RESPONSE); }
    }

    private void current(CallScope scope) {
        synchronized (scope.publishLock) {
            scope.remaining();
            if (TransactionSynchronizationManager.isActualTransactionActive()) throw failure(FailureKind.CANCELLED);
            if (scope.credential == null) throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
            try { credentialVerifier.requireCurrent(target(scope.backend), scope.credential); }
            catch (RuntimeException privateSource) { throw failure(FailureKind.CREDENTIAL_UNAVAILABLE); }
            scope.remaining();
        }
    }
    private static TargetVO target(BackendVO backend) {
        return new TargetVO(backend.tenantId(), backend.issuer(), backend.resourceUri(), backend.registrationId(), backend.expectedClientId(), backend.expectedAppId(),
                backend.expectedSourceBiz(), backend.expectedSourceApp(), backend.expectedEnvironment(), backend.businessScopes());
    }

    @Override public void close() {
        closed.set(true); for (var cancel : cancellations) cancel.run(); calls.shutdownNow();
    }

    /** Only this original scoped window may publish a bearer or submit an HTTP request. */
    private final class CallScope {
        final WindowVO window;
        final BackendVO backend;
        final TraceContext trace;
        final Object publishLock = new Object();
        final AtomicBoolean active = new AtomicBoolean(true);
        final Set<CompletableFuture<HttpResponse<byte[]>>> httpFutures = new HashSet<>();
        String bearer;
        Future<?> worker;
        CredentialVO credential;
        Instant narrowedDeadline;
        boolean operationOpen;
        boolean cleanupAllowed;
        volatile boolean toolsPossiblySent;
        String sessionId;
        TianjiMcpJsonRpcTransport protocol;
        McpSyncClient client;
        CallScope(WindowVO window, BackendVO backend, TraceContext trace) { this.window = window; this.backend = backend; this.trace = trace; }
        long remaining() {
            synchronized (publishLock) {
                if (!active.get() || closed.get() || Thread.currentThread().isInterrupted()) throw failure(FailureKind.CANCELLED);
                Instant deadline = credential == null ? window.deadlineAt() : earlier(window.deadlineAt(), credential.expiresAt());
                if (narrowedDeadline != null) deadline = earlier(deadline, narrowedDeadline);
                Instant now = clock.instant(); if (!deadline.isAfter(now)) throw failure(FailureKind.DEADLINE_EXCEEDED);
                long budget = Duration.between(window.startedAt(), deadline).toNanos(), elapsed = nanoTime.getAsLong() - window.startedNanos();
                if (elapsed < 0 || budget <= 0 || elapsed >= budget) throw failure(FailureKind.DEADLINE_EXCEEDED);
                long wall = now.isBefore(window.startedAt()) ? budget : Duration.between(now, deadline).toNanos();
                return Math.min(budget - elapsed, wall);
            }
        }
        void narrowCallDeadline(Instant value, Instant ceiling) {
            synchronized (publishLock) {
                remaining();
                if (value == null || value.getNano() % 1_000 != 0 || !value.isAfter(clock.instant()) || value.isAfter(ceiling)) throw failure(FailureKind.OPERATION_NOT_SENT);
                narrowedDeadline = value; remaining(); operationOpen = true;
            }
        }
        void publishBearer(String value) {
            synchronized (publishLock) { remaining(); if (value == null || value.isBlank()) throw failure(FailureKind.CREDENTIAL_UNAVAILABLE); bearer = value; }
        }
        String currentBearer() { synchronized (publishLock) { remaining(); return bearer; } }
        void clearBearer() { synchronized (publishLock) { bearer = null; } }
        CompletableFuture<HttpResponse<byte[]>> exchange(HttpRequest prepared, boolean toolsCall) {
            synchronized (publishLock) {
                current(this);
                boolean deleting = prepared != null && prepared.method().equals("DELETE");
                if (prepared == null || !backend.endpoint().toASCIIString().equals(prepared.uri().toASCIIString())
                        || deleting && (!cleanupAllowed || toolsCall) || !deleting && (!operationOpen || !prepared.method().equals("POST"))
                        || bearer == null || prepared.headers().map().entrySet().stream().anyMatch(entry -> !PREPARED_HEADERS.contains(entry.getKey().toLowerCase(Locale.ROOT)) || entry.getValue().size() != 1)) throw failure(FailureKind.OPERATION_NOT_SENT);
                if (!TianjiFixedReadOnlyMcpToolBindings.PROTOCOL_VERSION.equals(prepared.headers().firstValue("Mcp-Protocol-Version").orElse(null))
                        || !"application/json,text/event-stream".equals(prepared.headers().firstValue("Accept").orElse(null))) throw failure(FailureKind.OPERATION_NOT_SENT);
                String suppliedSession = prepared.headers().firstValue("Mcp-Session-Id").orElse(null);
                if (suppliedSession != null) {
                    try { if (!UUID.fromString(suppliedSession).toString().equals(suppliedSession)) throw failure(FailureKind.OPERATION_NOT_SENT); }
                    catch (RuntimeException invalid) { throw failure(FailureKind.OPERATION_NOT_SENT); }
                    if (sessionId == null && !deleting) sessionId = suppliedSession;
                    if (!suppliedSession.equals(sessionId)) throw failure(FailureKind.OPERATION_NOT_SENT);
                } else if (sessionId != null || deleting || toolsCall) throw failure(FailureKind.OPERATION_NOT_SENT);
                var builder = HttpRequest.newBuilder(backend.endpoint()).timeout(Duration.ofNanos(remaining()));
                if (TianjiFixedReadOnlyMcpToolBindings.isInternalService(backend.endpoint())) {
                    builder.version(HttpClient.Version.HTTP_1_1);
                }
                prepared.headers().map().forEach((name, values) -> builder.header(name, values.getFirst()));
                builder.header("Authorization", "Bearer " + bearer); if (trace.hasTrace()) trace.inject(builder::header);
                if (deleting) { if (prepared.bodyPublisher().isPresent()) throw failure(FailureKind.OPERATION_NOT_SENT); builder.DELETE(); }
                else builder.POST(prepared.bodyPublisher().orElseThrow(() -> failure(FailureKind.OPERATION_NOT_SENT)));
                if (toolsCall) toolsPossiblySent = true;
                var future = Objects.requireNonNull(http.sendAsync(builder.build(), HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), TianjiFixedReadOnlyMcpToolBindings.MAX_RESPONSE_BYTES)));
                httpFutures.add(future); return future;
            }
        }
        void closeOperation() { synchronized (publishLock) { operationOpen = false; cleanupAllowed = true; } }
        void cleanup() {
            TianjiMcpJsonRpcTransport transport; synchronized (publishLock) { transport = protocol; }
            if (transport == null) return;
            try { current(this); transport.deleteOwnedSession().block(Duration.ofNanos(remaining())); }
            catch (RuntimeException unavailable) { LOG.warn("Fixed MCP session cleanup was unavailable; peer TTL applies"); }
        }
        void localClose() {
            McpSyncClient sdk; TianjiMcpJsonRpcTransport transport;
            synchronized (publishLock) { bearer = null; sdk = client; transport = protocol; client = null; protocol = null; }
            try { if (sdk != null) sdk.close(); else if (transport != null) transport.closeGracefully().block(); }
            catch (RuntimeException unavailable) { LOG.warn("Fixed MCP local client cleanup was unavailable"); }
        }
        void cancel() {
            ArrayList<CompletableFuture<HttpResponse<byte[]>>> pending; Future<?> task;
            synchronized (publishLock) {
                active.set(false); bearer = null; operationOpen = false; cleanupAllowed = false;
                pending = new ArrayList<>(httpFutures); httpFutures.clear(); task = worker; worker = null;
            }
            for (var future : pending) future.cancel(true); if (task != null) task.cancel(true);
        }
    }
    private record WindowVO(Instant startedAt, long startedNanos, Instant deadlineAt) { }
    private static Instant earlier(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
    private static Failure failure(FailureKind kind) { return new Failure(kind); }
    private static AgentLedgerException ledgerFailure(AgentLedgerException rejected) { return new AgentLedgerException(rejected.code(), "Fixed MCP callback could not complete"); }
    private static Failure protocolFailure(RuntimeException rejected, FailureKind fallback) {
        if (rejected instanceof Failure fixed) return failure(fixed.kind());
        if (rejected.getClass() == RuntimeException.class && "Client failed to initialize by explicit API call".equals(rejected.getMessage())
                && rejected.getCause() instanceof Failure fixed && fixed.getCause() == null && fixed.getSuppressed().length == 0) return failure(fixed.kind());
        return failure(fallback);
    }
}
