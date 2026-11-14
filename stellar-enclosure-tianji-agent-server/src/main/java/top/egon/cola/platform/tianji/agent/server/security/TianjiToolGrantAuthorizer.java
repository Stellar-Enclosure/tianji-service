package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.common.trace.TraceContext;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.RunProfileEnum;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.ToolRunEvidenceVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolCapabilityStore;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ServiceExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** Rechecks independently sealed tool capabilities through the current USER/SERVICE PDP protocols. */
public final class TianjiToolGrantAuthorizer implements ToolExecutionAuthorization, AutoCloseable {
    private final AgentLedgerService ledger;
    private final ToolCapabilityStore store;
    private final TianjiToolGrantBindings bindings;
    private final URI resource;
    private final String systemCode;
    private final TrustedIssuerRegistry issuers;
    private final Map<String, TianjiExecutionGrantAuthorizer.BoundClient> users;
    private final Map<String, TianjiExecutionGrantAuthorizer.BoundClient> clients;
    private final Duration timeout;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final ThreadPoolExecutor calls;
    private final AtomicBoolean closed = new AtomicBoolean();

    public TianjiToolGrantAuthorizer(AgentLedgerService ledger, ToolCapabilityStore store, TianjiToolGrantBindings bindings,
            IdpStarterProperties starter, IdentityAuthorityProperties authority, TrustedIssuerRegistry issuers,
            IdpServiceOAuth2Client oauth, ObjectMapper mapper, Rbac3StarterProperties rbac, Duration timeout, int concurrency) {
        this(ledger, store, bindings, TianjiExecutionAuthorityBindings.resource(starter.getResourceUri()), rbac.getSystemCode(), issuers,
                bindings.bindings().stream().anyMatch(binding -> binding.principalType() == TrustedAgentActor.SubjectType.USER)
                        ? TianjiExecutionGrantAuthorizer.clients(starter, authority, issuers, oauth, mapper, rbac,
                        IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE, ExecutionAuthorizationContract.SERVICE_SCOPE) : Map.of(),
                bindings.hasServiceBindings() ? TianjiExecutionGrantAuthorizer.clients(starter, authority, issuers, oauth, mapper, rbac,
                        IdentityAuthorityScopes.SERVICE_EXECUTION_GRANT_ISSUE, ServiceExecutionAuthorizationContract.SERVICE_SCOPE) : Map.of(),
                timeout, concurrency, Clock.systemUTC(), System::nanoTime);
    }

    TianjiToolGrantAuthorizer(AgentLedgerService ledger, ToolCapabilityStore store, TianjiToolGrantBindings bindings, URI resource,
            String systemCode, TrustedIssuerRegistry issuers, Map<String, TianjiExecutionGrantAuthorizer.BoundClient> users,
            Map<String, TianjiExecutionGrantAuthorizer.BoundClient> clients, Duration timeout, int concurrency, Clock clock, LongSupplier nanoTime) {
        this.ledger = Objects.requireNonNull(ledger, "ledger"); this.store = Objects.requireNonNull(store, "store"); this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.resource = TianjiExecutionAuthorityBindings.resource(resource); this.systemCode = TianjiExecutionGrantAuthorizer.systemCode(systemCode);
        this.issuers = Objects.requireNonNull(issuers, "issuers"); this.users = Map.copyOf(users); this.clients = Map.copyOf(clients);
        this.timeout = Objects.requireNonNull(timeout, "timeout"); this.clock = Objects.requireNonNull(clock, "clock"); this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (!store.usesLedger(ledger) || !bindings.usesResource(resource) || !bindings.usesIssuerRegistry(issuers)
                || timeout.compareTo(Duration.ofMillis(100)) < 0 || timeout.compareTo(Duration.ofSeconds(10)) > 0 || concurrency < 1 || concurrency > 8) throw invalid();
        for (var map : List.of(this.users, this.clients)) {
            if (map.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(TrustedIssuerRegistry.issuerHash(entry.getValue().issuer())))) throw invalid();
        }
        for (var binding : bindings.bindings()) {
            var client = (binding.principalType() == TrustedAgentActor.SubjectType.USER ? this.users : this.clients).get(binding.issuerFingerprint());
            if (client == null || !client.tenantId().equals(Long.toString(binding.tenantId()))) throw invalid();
        }
        calls = new ThreadPoolExecutor(0, concurrency, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                Thread.ofVirtual().inheritInheritableThreadLocals(false).name("tianji-tool-authorization-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate && store.usesLedger(candidate); }
    public boolean usesBindings(TianjiToolGrantBindings candidate) { return bindings == candidate; }
    public boolean usesIssuerRegistry(TrustedIssuerRegistry candidate) { return issuers == candidate && bindings.usesIssuerRegistry(candidate); }
    public boolean usesResource(URI candidate) { return candidate != null && resource.toASCIIString().equals(candidate.toASCIIString()); }
    @Override public Decision authorize(Request request, Boundary boundary) {
        if (request == null || boundary == null) return Decision.UNAVAILABLE;
        return check(request, boundary).decision();
    }
    @Override public CheckResult check(Request request, Boundary boundary) {
        if (request == null || boundary == null) throw new IllegalArgumentException("Tool authorization request and boundary are required");
        var unavailable = new CheckResult(request, boundary, Decision.UNAVAILABLE, null);
        if (!admitted() || "LEGACY".equals(request.run().actor().issuerFingerprint())) return unavailable;
        try {
            var window = window(request.run(), request.deadlineAt());
            return execute(window, () -> evaluate(request, boundary, window), unavailable);
        } catch (RuntimeException failure) { return unavailable; }
    }

    /** Extra gate only, not ordinary run permission or a lease claim. It is unconnected until the host installs it. */
    public AgentRunAuthorization.Decision authorizeRun(AgentRunAuthorization.Request run, AgentRunAuthorization.Boundary boundary) {
        if (run == null || boundary == null || !admitted() || "LEGACY".equals(run.actor().issuerFingerprint())) return AgentRunAuthorization.Decision.UNAVAILABLE;
        try {
            var window = window(run, run.deadlineAt());
            return execute(window, () -> evaluateRun(run, boundary, window), AgentRunAuthorization.Decision.UNAVAILABLE);
        } catch (RuntimeException failure) { return AgentRunAuthorization.Decision.UNAVAILABLE; }
    }

    private CheckResult evaluate(Request request, Boundary boundary, WindowVO window) {
        try {
            var source = source(request.run(), AgentRunStatusEnum.RUNNING, request.lease(), request.deadlineAt(), window);
            if (source == null) return negative(request, boundary, Decision.UNAVAILABLE);
            var matches = source.tools().stream().filter(selected -> selected.descriptor().reference().equals(request.tool())).toList();
            if (matches.size() != 1) return negative(request, boundary, Decision.DENY);
            var selected = matches.getFirst();
            if (!selected.binding().backendBindingSha256().equals(request.bindingSha256())
                    || (selected.descriptor().policy().risk() == ToolPolicy.Risk.READ_ONLY) != (request.actionIntentId() == null)) return negative(request, boundary, Decision.DENY);
            var checked = verify(request.run(), AgentRunStatusEnum.RUNNING, source, selected, request.deadlineAt(), window);
            if (checked == null) return negative(request, boundary, Decision.UNAVAILABLE);
            if (checked.decision() != Decision.ALLOW) return negative(request, boundary, checked.decision());
            remaining(window);
            var audit = new AuditVO(1, UUID.randomUUID(), boundary, Decision.ALLOW, request.run().actor().subjectType(), checked.evidence().grantId(),
                    checked.authVersion(), checked.catalogVersion(), checked.policyVersion(), clock.instant(), checked.expiresAt());
            return new CheckResult(request, boundary, Decision.ALLOW, audit);
        } catch (RuntimeException failure) { return negative(request, boundary, Decision.UNAVAILABLE); }
    }

    private AgentRunAuthorization.Decision evaluateRun(AgentRunAuthorization.Request run, AgentRunAuthorization.Boundary boundary, WindowVO window) {
        try {
            var status = boundary == AgentRunAuthorization.Boundary.CLAIM ? AgentRunStatusEnum.QUEUED : AgentRunStatusEnum.RUNNING;
            var source = source(run, status, null, window.deadlineAt(), window);
            if (source == null) return AgentRunAuthorization.Decision.UNAVAILABLE;
            var checked = new ArrayList<VerifiedToolResult>();
            for (var selected : source.tools()) {
                remaining(window); var current = verify(run, status, source, selected, window.deadlineAt(), window);
                if (current == null) return AgentRunAuthorization.Decision.UNAVAILABLE;
                if (current.decision() == Decision.DENY) return AgentRunAuthorization.Decision.DENY;
                checked.add(current);
            }
            // An earlier tool may rotate while the last PDP call runs; the batch is not ALLOW until all stored evidence is still current.
            for (var result : checked) if (!unchanged(run, status, source, result, window.deadlineAt(), window)) return AgentRunAuthorization.Decision.UNAVAILABLE;
            if (checked.isEmpty()) {
                var current = source(run, status, source.activeLease(), window.deadlineAt(), window);
                if (current == null || !sameSource(source, current)) return AgentRunAuthorization.Decision.UNAVAILABLE;
            }
            remaining(window); return AgentRunAuthorization.Decision.ALLOW;
        } catch (RuntimeException failure) { return AgentRunAuthorization.Decision.UNAVAILABLE; }
    }

    private VerifiedToolResult verify(AgentRunAuthorization.Request run, AgentRunStatusEnum status, SourceResult source,
                                     TianjiToolGrantBindings.ResolvedToolResult selected, Instant requiredThrough, WindowVO window) {
        try {
            remaining(window); var bound = client(run); var trust = trust(run, bound); if (trust == null) return null;
            var binding = selected.binding(); var evidence = store.current(binding, selected.descriptor()).orElse(null);
            if (!usable(evidence, binding, requiredThrough, run, window)) return null;
            var expected = new HttpExecutionAuthorizationClient.Expectation(bound.issuer(), bound.tenantId(), run.actor().subjectId(), resource,
                    binding.purpose(), binding.capabilityId(), binding.bindingSha256(), ToolExecutionGrant.SESSION_REQUIRED, run.deadlineAt());
            var snapshot = new VerifiedToolResult(selected, evidence, trust, Decision.DENY, null, null, null, null);
            try {
                remaining(window); String grantId; Long authVersion = null, catalogVersion = null; long policyVersion; Instant expires;
                if (run.actor().subjectType() == TrustedAgentActor.SubjectType.USER) {
                    var result = bound.client().decide(new ExecutionAuthorizationContract.Request(systemCode, evidence.grantId(), evidence.ciphertext()), expected);
                    grantId = result.grantId(); authVersion = result.authVersion(); policyVersion = result.policyVersion(); expires = result.expiresAt();
                } else {
                    var subject = binding.serviceSubject();
                    var serviceExpected = new HttpExecutionAuthorizationClient.ServiceExpectation(expected, new ServiceExecutionAuthorizationContract.Subject(subject.clientId(), subject.appId(), subject.businessCode(),
                            subject.applicationCode(), subject.environment(), subject.resourceUri().toASCIIString()));
                    var result = bound.client().decideService(new ServiceExecutionAuthorizationContract.Request(systemCode, evidence.grantId(), evidence.ciphertext()), serviceExpected);
                    grantId = result.grantId(); catalogVersion = result.catalogVersion(); policyVersion = result.policyVersion(); expires = result.expiresAt();
                }
                if (!evidence.grantId().equals(grantId) || expires == null || expires.isBefore(requiredThrough) || expires.isAfter(evidence.expiresAt())) return null;
                var result = new VerifiedToolResult(selected, evidence, trust, Decision.ALLOW, authVersion, catalogVersion, policyVersion, expires);
                return unchanged(run, status, source, result, requiredThrough, window) ? result : null;
            } catch (HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException denied) {
                return unchanged(run, status, source, snapshot, requiredThrough, window) ? snapshot : null;
            }
        } catch (RuntimeException failure) { return null; }
    }

    private boolean unchanged(AgentRunAuthorization.Request run, AgentRunStatusEnum status, SourceResult expectedSource,
                              VerifiedToolResult expected, Instant requiredThrough, WindowVO window) {
        remaining(window); var currentTrust = trust(run, client(run));
        if (currentTrust == null || !sameTrust(expected.trust(), currentTrust)) return false;
        var current = source(run, status, expectedSource.activeLease(), requiredThrough, window);
        if (current == null || !sameSource(expectedSource, current)) return false;
        var selected = expected.selected();
        var evidence = store.current(selected.binding(), selected.descriptor()).orElse(null);
        if (!expected.evidence().equals(evidence) || !usable(evidence, selected.binding(), requiredThrough, run, window)) return false;
        var now = ledger.databaseNow(); remaining(window);
        return requiredThrough.isAfter(now) && run.deadlineAt().isAfter(now) && evidence.expiresAt().isAfter(now)
                && (expected.expiresAt() == null || expected.expiresAt().isAfter(now));
    }

    private SourceResult source(AgentRunAuthorization.Request run, AgentRunStatusEnum status,
                                AgentRunLease expectedLease, Instant requiredThrough, WindowVO window) {
        remaining(window);
        var query = new GetAgentRunQuery(run.runId());
        var runtime = run.actor().subjectType() == TrustedAgentActor.SubjectType.SERVICE
                ? ledger.toolRunEvidence(run.actor(), query) : null;
        SourceResult source;
        if (runtime != null && runtime.profile() == RunProfileEnum.RUNTIME_V2) {
            if (!run.equals(runtime.authorization()) || !currentRun(runtime.run(), status)) return null;
            var activeLease = runtime.activeLease();
            if (status == AgentRunStatusEnum.RUNNING && (activeLease == null || !run.runId().equals(activeLease.runId())
                    || activeLease.expiresAt().isBefore(requiredThrough) || expectedLease != null
                    && (!activeLease.owner().equals(expectedLease.owner()) || activeLease.epoch() != expectedLease.epoch()))) {
                return null;
            }
            source = new SourceResult(null, runtime, bindings.resolveRuntime(runtime));
        } else {
            if (run.actor().subjectType() == TrustedAgentActor.SubjectType.SERVICE
                    && (runtime == null || runtime.profile() != RunProfileEnum.LEGACY_V1)) return null;
            var legacy = ledger.executionMaterial(run.actor(), query);
            if (legacy == null || !run.equals(legacy.authorization()) || !currentRun(legacy.run(), status)) return null;
            source = new SourceResult(legacy.snapshot(), null, bindings.resolve(legacy));
        }
        remaining(window);
        Instant now = ledger.databaseNow();
        remaining(window);
        return window.deadlineAt().isAfter(now) && requiredThrough.isAfter(now) && run.deadlineAt().isAfter(now) ? source : null;
    }

    private static boolean currentRun(AgentRunDTO run, AgentRunStatusEnum status) {
        return run != null && run.status() == status && !run.cancelRequested();
    }

    private boolean usable(ToolExecutionGrant.EvidenceVO evidence, ToolExecutionGrant.BindingVO binding, Instant requiredThrough,
                           AgentRunAuthorization.Request run, WindowVO window) {
        remaining(window);
        if (evidence == null || !binding.equals(evidence.reservation().binding()) || evidence.expiresAt().isBefore(requiredThrough)) return false;
        Instant now = ledger.databaseNow(); remaining(window);
        return requiredThrough.isAfter(now) && evidence.expiresAt().isAfter(now) && run.deadlineAt().isAfter(now);
    }
    private TianjiExecutionGrantAuthorizer.BoundClient client(AgentRunAuthorization.Request run) {
        var result = (run.actor().subjectType() == TrustedAgentActor.SubjectType.USER ? users : clients).get(run.actor().issuerFingerprint());
        if (result == null || !result.tenantId().equals(Long.toString(run.actor().tenantId())) || !TrustedIssuerRegistry.issuerHash(result.issuer()).equals(run.actor().issuerFingerprint())) throw invalid();
        return result;
    }
    private TrustedIssuerRegistry.TrustedIssuerVO trust(AgentRunAuthorization.Request run, TianjiExecutionGrantAuthorizer.BoundClient client) {
        var trust = issuers.findByIssuer(client.issuer().toASCIIString()).orElse(null);
        return trust != null && trust.status() == TrustedIssuerRegistry.Status.ACTIVE && trust.realmType() == TrustedIssuerRegistry.RealmType.TENANT
                && client.tenantId().equals(trust.tenantId()) && trust.issuer().toASCIIString().equals(client.issuer().toASCIIString())
                && client.tenantId().equals(Long.toString(run.actor().tenantId())) ? trust : null;
    }
    private static boolean sameTrust(TrustedIssuerRegistry.TrustedIssuerVO expected, TrustedIssuerRegistry.TrustedIssuerVO actual) {
        return expected.equals(actual) && expected.issuer().toASCIIString().equals(actual.issuer().toASCIIString()) && expected.jwksUri().toASCIIString().equals(actual.jwksUri().toASCIIString());
    }
    private static boolean sameSource(SourceResult expected, SourceResult actual) {
        if (!expected.tools().equals(actual.tools())) return false;
        if (expected.runtime() != null) {
            var pinned = expected.runtime();
            var current = actual.runtime();
            return current != null && pinned.authorization().equals(current.authorization())
                    && pinned.processLink().equals(current.processLink()) && pinned.toolRefs().equals(current.toolRefs())
                    && pinned.maxToolCalls() == current.maxToolCalls();
        }
        return actual.snapshot() != null && expected.snapshot().definition().equals(actual.snapshot().definition())
                && expected.snapshot().definitionContentSha256().equals(actual.snapshot().definitionContentSha256())
                && expected.snapshot().effectivePolicy().equals(actual.snapshot().effectivePolicy()) && Objects.equals(expected.snapshot().processLink(), actual.snapshot().processLink());
    }
    private CheckResult negative(Request request, Boundary boundary, Decision decision) {
        return new CheckResult(request, boundary, decision, new AuditVO(1, UUID.randomUUID(), boundary, decision, request.run().actor().subjectType(), null, null, null, null, clock.instant(), null));
    }

    private WindowVO window(AgentRunAuthorization.Request run, Instant deadline) {
        long started = nanoTime.getAsLong(); Instant now = clock.instant(); Instant maximum = now.plus(timeout);
        if (deadline.isAfter(run.deadlineAt())) deadline = run.deadlineAt(); if (deadline.isAfter(maximum)) deadline = maximum;
        if (!deadline.isAfter(now)) throw invalid();
        return new WindowVO(deadline, started + Duration.between(now, deadline).toNanos());
    }
    private boolean admitted() { return !closed.get() && !calls.isShutdown() && !Thread.currentThread().isInterrupted() && !TransactionSynchronizationManager.isActualTransactionActive(); }
    private long remaining(WindowVO window) {
        if (!admitted() || !clock.instant().isBefore(window.deadlineAt())) throw invalid();
        long remaining = window.nanoDeadline() - nanoTime.getAsLong(); if (remaining <= 0) throw invalid(); return remaining;
    }
    private <T> T execute(WindowVO window, Callable<T> operation, T unavailable) {
        Future<T> future = null;
        try {
            remaining(window); TraceContext trace = TraceContext.capture();
            future = calls.submit(() -> { try (var ignored = trace.open()) { remaining(window); T result = operation.call(); remaining(window); return result; } });
            T result = future.get(remaining(window), TimeUnit.NANOSECONDS); remaining(window); return result;
        } catch (InterruptedException interrupted) {
            if (future != null) future.cancel(true); Thread.currentThread().interrupt(); return unavailable;
        } catch (TimeoutException | ExecutionException | RejectedExecutionException failure) {
            if (future != null) future.cancel(true); return unavailable;
        } catch (RuntimeException failure) {
            if (future != null) future.cancel(true); return unavailable;
        }
    }
    @Override public void close() { closed.set(true); calls.shutdownNow(); }
    private record WindowVO(Instant deadlineAt, long nanoDeadline) { }
    private record SourceResult(AgentExecutionSnapshot snapshot, ToolRunEvidenceVO runtime,
                                List<TianjiToolGrantBindings.ResolvedToolResult> tools) {
        SourceResult {
            if ((snapshot == null) == (runtime == null)) throw invalid();
            tools = List.copyOf(tools);
        }
        AgentRunLease activeLease() { return runtime == null ? null : runtime.activeLease(); }
        @Override public String toString() { return "ToolGrantSource[redacted]"; }
    }
    private record VerifiedToolResult(TianjiToolGrantBindings.ResolvedToolResult selected, ToolExecutionGrant.EvidenceVO evidence,
                                      TrustedIssuerRegistry.TrustedIssuerVO trust, Decision decision, Long authVersion, Long catalogVersion, Long policyVersion, Instant expiresAt) {
        @Override public String toString() { return "VerifiedToolGrant[redacted]"; }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool grant authority configuration, source or window is invalid"); }
}
