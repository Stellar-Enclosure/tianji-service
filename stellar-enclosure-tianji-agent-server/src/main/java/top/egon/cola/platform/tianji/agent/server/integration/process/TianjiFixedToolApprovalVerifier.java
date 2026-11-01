package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.common.trace.TraceContext;
import top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiProcessToolCredentialVerifier.CredentialVO;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiProcessToolCredentialVerifier.TargetVO;
import top.egon.cola.platform.tianji.client.HttpProcessClient;
import top.egon.cola.platform.tianji.client.ProcessClientException;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalClient;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TenantIssuerAuthenticationManagerComponent;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedPrincipalConverter;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

/** Explicit bounded verifier for one host-fixed approval source; it neither consumes approval nor starts a process. */
public final class TianjiFixedToolApprovalVerifier implements FixedToolApprovalVerifier, AutoCloseable {
    private final TianjiFixedToolApprovalBindings bindings;
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

    public TianjiFixedToolApprovalVerifier(TianjiFixedToolApprovalBindings bindings, IdpServiceOAuth2Client oauth,
            TenantIssuerAuthenticationManagerComponent authentication, VerifiedPrincipalConverter converter,
            TrustedIssuerRegistry issuers, HttpClient http, int concurrency) {
        this(bindings, oauth, authentication, converter, issuers, http, concurrency, Clock.systemUTC(), System::nanoTime);
    }

    TianjiFixedToolApprovalVerifier(TianjiFixedToolApprovalBindings bindings, IdpServiceOAuth2Client oauth,
            TenantIssuerAuthenticationManagerComponent authentication, VerifiedPrincipalConverter converter,
            TrustedIssuerRegistry issuers, HttpClient http, int concurrency, Clock clock, LongSupplier nanoTime) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.oauth = Objects.requireNonNull(oauth, "oauth");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.converter = Objects.requireNonNull(converter, "converter");
        this.issuers = Objects.requireNonNull(issuers, "issuers");
        this.http = Objects.requireNonNull(http, "http");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (concurrency < 1 || concurrency > 8 || http.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("Fixed tool approval verifier configuration is invalid");
        }
        credentialVerifier = new TianjiProcessToolCredentialVerifier(oauth, authentication, converter, issuers, clock);
        calls = new ThreadPoolExecutor(0, concurrency, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                Thread.ofVirtual().inheritInheritableThreadLocals(false).name("tianji-fixed-tool-approval-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean usesBindings(TianjiFixedToolApprovalBindings candidate) { return bindings == candidate; }
    public boolean usesOAuth(IdpServiceOAuth2Client candidate) { return oauth == candidate; }
    public boolean usesAuthentication(TenantIssuerAuthenticationManagerComponent candidate) { return authentication == candidate; }
    public boolean usesConverter(VerifiedPrincipalConverter candidate) { return converter == candidate; }
    public boolean usesIssuerRegistry(TrustedIssuerRegistry candidate) { return issuers == candidate; }
    public boolean usesHttpClient(HttpClient candidate) { return http == candidate; }

    @Override public <T> T withVerifiedApproval(Request request, Callback<T> callback) {
        if (request == null || callback == null) throw new IllegalArgumentException("Fixed tool approval request and callback are required");
        if (closed.get() || Thread.currentThread().isInterrupted()) throw failure(FailureKind.CANCELLED);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
        Instant startedAt = clock.instant(); long startedNanos = nanoTime.getAsLong();
        TianjiFixedToolApprovalBindings.ResolvedBindingVO resolved;
        try { resolved = Objects.requireNonNull(bindings.resolve(request)); }
        catch (RuntimeException privateBinding) { throw failure(FailureKind.APPROVAL_DENIED); }
        var backend = resolved.binding().backend();
        Instant deadline = earlier(earlier(request.deadlineAt(), request.run().deadlineAt()), startedAt.plus(backend.timeout()));
        var scope = new CallScope(new WindowVO(startedAt, startedNanos, deadline));
        Runnable cancel = scope::cancel; cancellations.add(cancel);
        try {
            scope.remaining(); TraceContext trace = TraceContext.capture();
            var task = new FutureTask<T>(() -> {
                try (var ignored = trace.open()) { scope.remaining(); return verifiedApproval(request, callback, resolved, scope); }
                finally { scope.clearBearer(); }
            });
            scope.future.set(task); scope.remaining(); calls.execute(task);
            T value = task.get(scope.remaining(), TimeUnit.NANOSECONDS);
            current(backend, scope); return value;
        } catch (InterruptedException interrupted) {
            scope.cancel(); Thread.currentThread().interrupt(); throw failure(FailureKind.CANCELLED);
        } catch (TimeoutException timeout) {
            scope.cancel(); throw failure(FailureKind.DEADLINE_EXCEEDED);
        } catch (CancellationException cancelled) { throw failure(FailureKind.CANCELLED); }
        catch (RejectedExecutionException busy) {
            scope.cancel(); throw failure(closed.get() ? FailureKind.CANCELLED : FailureKind.CREDENTIAL_UNAVAILABLE);
        } catch (ExecutionException execution) {
            scope.cancel();
            if (execution.getCause() instanceof Failure rejected) throw failure(rejected.kind());
            if (execution.getCause() instanceof AgentLedgerException rejected) throw ledgerFailure(rejected);
            throw failure(FailureKind.APPROVAL_UNAVAILABLE);
        } catch (Failure rejected) { throw failure(rejected.kind()); }
        catch (RuntimeException privateFailure) { throw failure(FailureKind.APPROVAL_UNAVAILABLE); }
        finally {
            cancel.run(); cancellations.remove(cancel);
        }
    }

    private <T> T verifiedApproval(Request request, Callback<T> callback,
            TianjiFixedToolApprovalBindings.ResolvedBindingVO resolved, CallScope scope) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
        var backend = resolved.binding().backend(); var approval = resolved.binding().approval();
        CredentialVO credential = credentials(backend, scope); scope.credential = credential;
        if (!credential.actor().equals(approval.consumer())) throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
        current(backend, scope);
        top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO proof;
        try {
            var client = new HttpProcessClient(backend.processBaseUri(), http, scope::currentBearer,
                    Duration.ofNanos(scope.remaining()));
            proof = client.verifyApproval(approval.query(request.action(), request.approvalReceiptId()));
        } catch (Failure rejected) { throw failure(rejected.kind()); }
        catch (ProcessClientException rejected) {
            scope.remaining(); throw failure(remoteFailure(rejected));
        } catch (RuntimeException privateRemote) {
            scope.remaining(); throw failure(FailureKind.APPROVAL_UNAVAILABLE);
        }
        current(backend, scope);
        try { approval.requireApproved(request.action(), proof, clock.instant()); }
        catch (AgentLedgerException denied) { throw failure(FailureKind.APPROVAL_DENIED); }
        catch (RuntimeException privateProof) { throw failure(FailureKind.APPROVAL_DENIED); }
        scope.proofExpiresAt = proof.expiresAt(); scope.remaining();
        var observation = new ApprovedObservation(backend.sourceId(), backend.processIssuer().toASCIIString(),
                backend.processResourceUri().toASCIIString(), approval, proof, credential.actor(), credential.expiresAt());
        T value;
        try { value = callback.apply(observation); }
        catch (AgentLedgerException rejected) { throw ledgerFailure(rejected); }
        catch (RuntimeException privateCallback) { throw failure(FailureKind.APPROVAL_UNAVAILABLE); }
        current(backend, scope); return value;
    }

    private CredentialVO credentials(TianjiFixedToolApprovalBindings.BackendVO backend, CallScope scope) {
        try {
            scope.remaining();
            var result = credentialVerifier.credentials(target(backend), scope.window.deadlineAt(), scope::remaining,
                    scope::publishBearer, scope::currentBearer);
            scope.credential = result; return result;
        } catch (Failure rejected) { throw failure(rejected.kind()); }
        catch (RuntimeException privateCredential) {
            scope.remaining(); throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
        }
    }

    private void current(TianjiFixedToolApprovalBindings.BackendVO backend, CallScope scope) {
        scope.remaining();
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw failure(FailureKind.CANCELLED);
        var credential = scope.credential;
        if (credential == null) throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
        try { credentialVerifier.requireCurrent(target(backend), credential); }
        catch (RuntimeException privateCurrent) {
            scope.remaining(); throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
        }
        scope.remaining();
    }

    private static TargetVO target(TianjiFixedToolApprovalBindings.BackendVO backend) {
        return new TargetVO(backend.tenantId(), backend.processIssuer(), backend.processResourceUri(), backend.registrationId(),
                backend.expectedClientId(), backend.expectedAppId(), backend.expectedSourceBiz(), backend.expectedSourceApp(),
                backend.expectedEnvironment(), Set.of(ApprovalClient.VERIFY_SCOPE));
    }

    private static FailureKind remoteFailure(ProcessClientException failure) {
        if ("TIANJI_INVALID_RESPONSE".equals(failure.code()) || "TIANJI_RESPONSE_TOO_LARGE".equals(failure.code())) {
            return FailureKind.APPROVAL_INVALID_RESPONSE;
        }
        if (failure.httpStatus() == 401 || failure.httpStatus() == 403 || failure.httpStatus() == 404) {
            return FailureKind.APPROVAL_DENIED;
        }
        return FailureKind.APPROVAL_UNAVAILABLE;
    }

    @Override public void close() {
        closed.set(true);
        for (var cancel : cancellations) cancel.run();
        calls.shutdownNow();
    }

    /** Request-local state only; bearer publication and revocation share the same short critical section. */
    private final class CallScope {
        final WindowVO window;
        final AtomicBoolean active = new AtomicBoolean(true);
        final AtomicReference<String> bearer = new AtomicReference<>();
        final AtomicReference<Future<?>> future = new AtomicReference<>();
        final Object publishLock = new Object();
        volatile CredentialVO credential;
        volatile Instant proofExpiresAt;

        CallScope(WindowVO window) { this.window = window; }
        long remaining() {
            requireActive();
            Instant deadline = window.deadlineAt();
            if (credential != null) deadline = earlier(deadline, credential.expiresAt());
            if (proofExpiresAt != null) deadline = earlier(deadline, proofExpiresAt);
            Instant now = clock.instant();
            if (!deadline.isAfter(now)) throw failure(FailureKind.DEADLINE_EXCEEDED);
            long remaining = window.startedNanos() + Duration.between(window.startedAt(), deadline).toNanos() - nanoTime.getAsLong();
            if (remaining <= 0) throw failure(FailureKind.DEADLINE_EXCEEDED);
            return Math.min(remaining, Duration.between(now, deadline).toNanos());
        }
        void requireActive() {
            if (!active.get() || closed.get() || Thread.currentThread().isInterrupted()) throw failure(FailureKind.CANCELLED);
        }
        void publishBearer(String value) {
            synchronized (publishLock) {
                remaining();
                if (value == null || value.isBlank()) throw failure(FailureKind.CREDENTIAL_UNAVAILABLE);
                bearer.set(value);
            }
        }
        String currentBearer() { synchronized (publishLock) { return bearer.get(); } }
        void clearBearer() { synchronized (publishLock) { bearer.set(null); } }
        void cancel() {
            synchronized (publishLock) { active.set(false); bearer.set(null); }
            var task = future.get(); if (task != null) task.cancel(true);
        }
    }

    private record WindowVO(Instant startedAt, long startedNanos, Instant deadlineAt) { }
    private static Instant earlier(Instant first, Instant second) { return first.isBefore(second) ? first : second; }
    private static Failure failure(FailureKind kind) { return new Failure(kind); }
    private static AgentLedgerException ledgerFailure(AgentLedgerException failure) {
        return new AgentLedgerException(failure.code(), "Fixed tool approval callback could not complete");
    }
}
