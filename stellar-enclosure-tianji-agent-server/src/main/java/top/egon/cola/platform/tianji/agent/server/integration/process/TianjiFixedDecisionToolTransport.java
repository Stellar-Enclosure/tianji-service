package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.common.trace.TraceContext;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiFixedDecisionToolBindings.BackendVO;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiProcessToolCredentialVerifier.CredentialVO;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiProcessToolCredentialVerifier.TargetVO;
import top.egon.cola.platform.tianji.client.HttpProcessClient;
import top.egon.cola.platform.tianji.client.ProcessClientException;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianji.contract.orchestration.EvaluateDecisionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessReleaseQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseDTO;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TenantIssuerAuthenticationManagerComponent;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedPrincipalConverter;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** Explicit fixed-backend transport; only a verified callback may consume its one-shot decision operation. */
public final class TianjiFixedDecisionToolTransport implements ReadOnlyDecisionToolTransport, AutoCloseable {
    private static final Set<String> SCOPES = Set.of(ProcessReleaseClient.READ_SCOPE, DecisionClient.EXECUTE_SCOPE);
    private final TianjiFixedDecisionToolBindings bindings;
    private final IdpServiceOAuth2Client oauth;
    private final TenantIssuerAuthenticationManagerComponent authentication;
    private final VerifiedPrincipalConverter converter;
    private final TrustedIssuerRegistry issuers;
    private final HttpClient http;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final TianjiProcessToolCredentialVerifier credentialVerifier;
    private final ThreadPoolExecutor calls;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Runnable> cancellations = ConcurrentHashMap.newKeySet();

    public TianjiFixedDecisionToolTransport(TianjiFixedDecisionToolBindings bindings, IdpServiceOAuth2Client oauth,
            TenantIssuerAuthenticationManagerComponent authentication, VerifiedPrincipalConverter converter, TrustedIssuerRegistry issuers,
            HttpClient http, int concurrency) {
        this(bindings, oauth, authentication, converter, issuers, http, concurrency, Clock.systemUTC(), System::nanoTime);
    }
    TianjiFixedDecisionToolTransport(TianjiFixedDecisionToolBindings bindings, IdpServiceOAuth2Client oauth,
            TenantIssuerAuthenticationManagerComponent authentication, VerifiedPrincipalConverter converter, TrustedIssuerRegistry issuers,
            HttpClient http, int concurrency, Clock clock, LongSupplier nanoTime) {
        this.bindings = Objects.requireNonNull(bindings, "bindings"); this.oauth = Objects.requireNonNull(oauth, "oauth");
        this.authentication = Objects.requireNonNull(authentication, "authentication"); this.converter = Objects.requireNonNull(converter, "converter");
        this.issuers = Objects.requireNonNull(issuers, "issuers"); this.http = Objects.requireNonNull(http, "http");
        this.clock = Objects.requireNonNull(clock, "clock"); this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        credentialVerifier = new TianjiProcessToolCredentialVerifier(this.oauth, this.authentication, this.converter, this.issuers, this.clock);
        if (concurrency < 1 || concurrency > 8 || http.followRedirects() != HttpClient.Redirect.NEVER) throw new IllegalArgumentException("Fixed decision transport configuration is invalid");
        calls = new ThreadPoolExecutor(0, concurrency, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                Thread.ofVirtual().inheritInheritableThreadLocals(false).name("tianji-fixed-decision-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean usesBindings(TianjiFixedDecisionToolBindings candidate) { return candidate == bindings; }
    public boolean usesOAuth(IdpServiceOAuth2Client candidate) { return candidate == oauth; }
    public boolean usesAuthentication(TenantIssuerAuthenticationManagerComponent candidate) { return candidate == authentication; }
    public boolean usesConverter(VerifiedPrincipalConverter candidate) { return candidate == converter; }
    public boolean usesIssuerRegistry(TrustedIssuerRegistry candidate) { return candidate == issuers; }
    public boolean usesHttpClient(HttpClient candidate) { return candidate == http; }

    @Override public <T> T withVerifiedCall(Request request, Callback<T> callback) {
        if (request == null || callback == null) throw new IllegalArgumentException("Fixed decision request and callback are required");
        if (closed.get() || Thread.currentThread().isInterrupted()) throw failure(FailureKind.CANCELLED);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
        Instant startedAt = clock.instant(); long startedNanos = nanoTime.getAsLong();
        TianjiFixedDecisionToolBindings.ResolvedBindingVO resolved;
        try { resolved = Objects.requireNonNull(bindings.resolve(request)); }
        catch (RuntimeException privateBinding) { throw failure(FailureKind.CREDENTIAL_UNAVAILABLE); }
        var backend = resolved.binding().backend();
        Instant deadline = earlier(earlier(request.deadlineAt(), request.run().deadlineAt()), startedAt.plus(backend.timeout()));
        var scope = new CallScope(new WindowVO(startedAt, startedNanos, deadline));
        Runnable cancel = scope::cancel; cancellations.add(cancel);
        try {
            scope.remaining(); TraceContext trace = TraceContext.capture();
            var task = new FutureTask<T>(() -> {
                try (var ignored = trace.open()) { scope.remaining(); return verifiedCall(request, callback, backend, scope); }
                finally { scope.clearBearer(); }
            });
            scope.future.set(task); scope.remaining(); calls.execute(task);
            T value = task.get(scope.remaining(), TimeUnit.NANOSECONDS);
            current(scope, backend); return value;
        } catch (InterruptedException interrupted) {
            scope.cancel(); Thread.currentThread().interrupt(); throw failure(FailureKind.CANCELLED);
        } catch (TimeoutException timeout) {
            scope.cancel(); throw failure(FailureKind.DEADLINE_EXCEEDED);
        } catch (CancellationException cancelled) { throw failure(FailureKind.CANCELLED); }
        catch (RejectedExecutionException busy) { throw failure(closed.get() ? FailureKind.CANCELLED : FailureKind.CREDENTIAL_UNAVAILABLE); }
        catch (ExecutionException execution) {
            if (execution.getCause() instanceof Failure rejected) throw failure(rejected.kind());
            if (execution.getCause() instanceof AgentLedgerException rejected) throw ledgerFailure(rejected);
            throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
        } catch (Failure rejected) { throw failure(rejected.kind()); }
        catch (RuntimeException privateFailure) { throw failure(FailureKind.CREDENTIAL_UNAVAILABLE); }
        finally {
            cancel.run(); cancellations.remove(cancel);
        }
    }

    private <T> T verifiedCall(Request request, Callback<T> callback, BackendVO backend, CallScope scope) {
        var credential = credentials(backend, scope); scope.credential = credential;
        current(scope, backend);
        ProcessReleaseDTO release;
        try { release = client(backend, scope).get(new GetProcessReleaseQuery(backend.releaseVersion())); }
        catch (ProcessClientException unavailable) {
            throw failure("TIANJI_INVALID_RESPONSE".equals(unavailable.code()) ? FailureKind.RELEASE_CHANGED : FailureKind.RELEASE_UNAVAILABLE);
        } catch (RuntimeException privateRelease) { throw failure(FailureKind.RELEASE_UNAVAILABLE); }
        requireRelease(release, backend); current(scope, backend);
        Thread owner = Thread.currentThread(); var callbackActive = new AtomicBoolean(true); var consumed = new AtomicBoolean();
        OneShotDecision operation = () -> {
            if (Thread.currentThread() != owner || !callbackActive.get() || TransactionSynchronizationManager.isActualTransactionActive()
                    || !consumed.compareAndSet(false, true)) throw failure(FailureKind.CANCELLED);
            current(scope, backend);
            return evaluate(request, backend, scope);
        };
        T value;
        try { value = callback.apply(new VerifiedExecutor(credential.actor(), credential.expiresAt()), operation); }
        catch (Failure rejected) { throw failure(rejected.kind()); }
        catch (AgentLedgerException rejected) { throw ledgerFailure(rejected); }
        catch (RuntimeException privateCallback) { throw failure(consumed.get() ? FailureKind.OPERATION_RESULT_UNKNOWN : FailureKind.OPERATION_REJECTED); }
        finally { callbackActive.set(false); }
        current(scope, backend); return value;
    }

    private CredentialVO credentials(BackendVO backend, CallScope scope) {
        try {
            scope.remaining();
            if (TransactionSynchronizationManager.isActualTransactionActive()) throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
            var result = credentialVerifier.credentials(target(backend), scope.window.deadlineAt(), scope::remaining, scope::publishBearer, scope.bearer::get);
            scope.credential = result; current(scope, backend); return result;
        } catch (Failure rejected) { throw failure(rejected.kind()); }
        catch (RuntimeException privateCredential) { throw failure(FailureKind.CREDENTIAL_UNAVAILABLE); }
    }

    private DecisionObservation evaluate(Request request, BackendVO backend, CallScope scope) {
        top.egon.cola.platform.tianji.contract.orchestration.DecisionResultDTO result;
        try {
            result = client(backend, scope).evaluate(new EvaluateDecisionCommand(backend.decisionKey(), backend.releaseVersion(), request.downstreamRequestId(), request.correlationId(), request.arguments()));
        } catch (Failure rejected) { throw failure(rejected.kind()); }
        catch (ProcessClientException rejected) {
            if ("TIANJI_INVALID_RESPONSE".equals(rejected.code()) || "TIANJI_RESPONSE_TOO_LARGE".equals(rejected.code())) throw failure(FailureKind.OPERATION_INVALID_RESPONSE);
            int status = rejected.httpStatus();
            if (status == 401 || status == 403) throw failure(FailureKind.OPERATION_DENIED);
            if (status >= 400 && status < 500 && status != 408 && status != 429) throw failure(FailureKind.OPERATION_REJECTED);
            throw failure(FailureKind.OPERATION_RESULT_UNKNOWN);
        } catch (RuntimeException privateOperation) { throw failure(FailureKind.OPERATION_RESULT_UNKNOWN); }
        // A valid returned response is a fact even if the caller/window/source changes now; the callback records it before outer postchecks.
        try {
            Instant receivedAt = clock.instant();
            if (result.outputs().isEmpty() && result.matchedRuleIds().isEmpty()) return new DecisionObservation(Status.NO_MATCH, result.decisionExecutionId(), result.submittedAt(), receivedAt, Map.of(), null);
            if (result.outputs().size() != 1 || result.matchedRuleIds().size() != 1) throw failure(FailureKind.OPERATION_INVALID_RESPONSE);
            var output = request.descriptor().output().values(result.outputs().getFirst());
            return new DecisionObservation(Status.MATCHED, result.decisionExecutionId(), result.submittedAt(), receivedAt, output, result.matchedRuleIds().getFirst());
        } catch (RuntimeException invalidResponse) { throw failure(FailureKind.OPERATION_INVALID_RESPONSE); }
    }

    private HttpProcessClient client(BackendVO backend, CallScope scope) {
        return new HttpProcessClient(backend.processBaseUri(), http, scope.bearer::get, Duration.ofNanos(scope.remaining()));
    }
    private void current(CallScope scope, BackendVO backend) {
        scope.remaining();
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw failure(FailureKind.CANCELLED);
        var credential = scope.credential;
        if (credential == null) throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
        try {
            credentialVerifier.requireCurrent(target(backend), credential);
        } catch (Failure rejected) { throw failure(rejected.kind()); }
        catch (RuntimeException privateSource) { throw failure(FailureKind.CREDENTIAL_UNAVAILABLE); }
        scope.remaining();
    }

    private static TargetVO target(BackendVO backend) {
        return new TargetVO(backend.tenantId(), backend.processIssuer(), backend.processResourceUri(), backend.registrationId(),
                backend.expectedClientId(), backend.expectedAppId(), backend.expectedSourceBiz(), backend.expectedSourceApp(), backend.expectedEnvironment(), SCOPES);
    }
    private static void requireRelease(ProcessReleaseDTO release, BackendVO backend) {
        var models = release.models().stream().filter(model -> model.type() == ProcessModelTypeEnum.DMN && model.definitionKey().equals(backend.decisionKey())).toList();
        if (!backend.releaseVersion().equals(release.releaseVersion()) || !backend.manifestSha256().equals(release.manifestSha256()) || models.size() != 1
                || !backend.decisionModelSha256().equals(models.getFirst().contentSha256()) || !backend.inputSchema().equals(models.getFirst().inputs())) throw failure(FailureKind.RELEASE_CHANGED);
    }

    @Override public void close() {
        closed.set(true);
        for (var cancel : cancellations) cancel.run();
        calls.shutdownNow();
    }

    /** Request-local lifecycle only; the shared set holds cleanup actions, never a token cache. */
    private final class CallScope {
        final WindowVO window;
        final AtomicBoolean active = new AtomicBoolean(true);
        final AtomicReference<String> bearer = new AtomicReference<>();
        final AtomicReference<Future<?>> future = new AtomicReference<>();
        final Object publishLock = new Object();
        volatile CredentialVO credential;

        CallScope(WindowVO window) { this.window = window; }
        long remaining() {
            if (!active.get() || closed.get() || Thread.currentThread().isInterrupted()) throw failure(FailureKind.CANCELLED);
            Instant deadline = credential == null ? window.deadlineAt() : earlier(window.deadlineAt(), credential.expiresAt());
            Instant now = clock.instant();
            if (!deadline.isAfter(now)) throw failure(FailureKind.DEADLINE_EXCEEDED);
            long remaining = window.startedNanos() + Duration.between(window.startedAt(), deadline).toNanos() - nanoTime.getAsLong();
            if (remaining <= 0) throw failure(FailureKind.DEADLINE_EXCEEDED);
            return Math.min(remaining, Duration.between(now, deadline).toNanos());
        }
        void publishBearer(String value) {
            synchronized (publishLock) {
                remaining();
                if (value == null || value.isBlank()) throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
                bearer.set(value);
            }
        }
        void clearBearer() { synchronized (publishLock) { bearer.set(null); } }
        void cancel() {
            synchronized (publishLock) { active.set(false); bearer.set(null); }
            var task = future.get(); if (task != null) task.cancel(true);
        }
    }
    private record WindowVO(Instant startedAt, long startedNanos, Instant deadlineAt) { }

    private static Instant earlier(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
    private static Failure failure(FailureKind kind) { return new Failure(kind); }
    private static AgentLedgerException ledgerFailure(AgentLedgerException failure) { return new AgentLedgerException(failure.code(), "Fixed decision callback could not complete"); }
}
