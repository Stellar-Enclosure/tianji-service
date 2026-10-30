package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.common.trace.TraceContext;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryContextBinding;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryReadGrant;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryReadGrantStore;
import top.egon.cola.platform.tianji.agent.server.integration.memory.TianjiMemoryAccessProperties;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionScopeAuthorizationContract;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Current IAM checks for independently sealed Memory-read scopes; it cannot authorize a Memory mutation. */
public final class TianjiMemoryRunAuthorization implements MemoryAuthorization, AutoCloseable {
    private final AgentLedgerService ledger;
    private final MemoryReadGrantStore store;
    private final List<MemoryContextBinding> bindings;
    private final TianjiMemoryAccessProperties teams;
    private final TianjiServiceExecutionBindings services;
    private final URI resource;
    private final String systemCode;
    private final TrustedIssuerRegistry issuers;
    private final Map<String, TianjiExecutionGrantAuthorizer.BoundClient> users;
    private final Map<String, TianjiExecutionGrantAuthorizer.BoundClient> clients;
    private final Duration timeout;
    private final Clock clock;
    private final ThreadPoolExecutor calls;

    public TianjiMemoryRunAuthorization(AgentLedgerService ledger, MemoryReadGrantStore store, List<MemoryContextBinding> bindings, TianjiMemoryAccessProperties teams,
            TianjiServiceExecutionBindings services, IdpStarterProperties starter, IdentityAuthorityProperties authority, TrustedIssuerRegistry issuers,
            IdpServiceOAuth2Client oauth, ObjectMapper mapper, Rbac3StarterProperties rbac, Duration timeout, int concurrency) {
        this(ledger, store, bindings, teams, services, TianjiExecutionAuthorityBindings.resource(starter.getResourceUri()), rbac.getSystemCode(), issuers,
                TianjiExecutionGrantAuthorizer.clients(starter, authority, issuers, oauth, mapper, rbac, IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE, ExecutionScopeAuthorizationContract.SERVICE_SCOPE),
                services == null ? Map.of() : TianjiExecutionGrantAuthorizer.clients(starter, authority, issuers, oauth, mapper, rbac,
                        IdentityAuthorityScopes.SERVICE_EXECUTION_GRANT_ISSUE, ServiceExecutionAuthorizationContract.SERVICE_SCOPE), timeout, concurrency, Clock.systemUTC());
    }

    TianjiMemoryRunAuthorization(AgentLedgerService ledger, MemoryReadGrantStore store, List<MemoryContextBinding> bindings, TianjiMemoryAccessProperties teams,
            TianjiServiceExecutionBindings services, URI resource, String systemCode, TrustedIssuerRegistry issuers,
            Map<String, TianjiExecutionGrantAuthorizer.BoundClient> users, Map<String, TianjiExecutionGrantAuthorizer.BoundClient> clients, Duration timeout, int concurrency, Clock clock) {
        this.ledger = Objects.requireNonNull(ledger, "ledger"); this.store = Objects.requireNonNull(store, "store"); this.bindings = MemoryContextBinding.validate(bindings);
        this.teams = Objects.requireNonNull(teams, "teams"); this.services = services; this.resource = TianjiExecutionAuthorityBindings.resource(resource);
        this.systemCode = TianjiExecutionGrantAuthorizer.systemCode(systemCode); this.issuers = Objects.requireNonNull(issuers, "issuers");
        this.users = Map.copyOf(users); this.clients = Map.copyOf(clients); this.clock = Objects.requireNonNull(clock, "clock"); this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (!store.usesLedger(ledger) || services != null && !services.usesIssuerRegistry(issuers) || timeout.compareTo(Duration.ofMillis(100)) < 0
                || timeout.compareTo(Duration.ofSeconds(30)) > 0 || concurrency < 1 || concurrency > 8) throw invalid();
        for (var binding : this.bindings) {
            var selected = (binding.principalType() == TrustedAgentActor.SubjectType.USER ? this.users : this.clients).get(binding.issuerFingerprint());
            if (selected == null || !selected.tenantId().equals(Long.toString(binding.tenantId())) || !TrustedIssuerRegistry.issuerHash(selected.issuer()).equals(binding.issuerFingerprint())
                    || binding.principalType() == TrustedAgentActor.SubjectType.SERVICE && services == null) throw invalid();
        }
        calls = new ThreadPoolExecutor(0, concurrency, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                Thread.ofVirtual().inheritInheritableThreadLocals(false).name("tianji-memory-authorization-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean usesBindings(List<MemoryContextBinding> configured) { return bindings.equals(configured); }
    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate && store.usesLedger(candidate); }
    boolean usesIssuerRegistry(TrustedIssuerRegistry candidate) { return issuers == candidate && (services == null || services.usesIssuerRegistry(candidate)); }

    /** Called by the actual execution authorizer before claim, so a missing Memory ticket cannot race an already issued run ticket. */
    public AgentRunAuthorization.Decision authorizeRun(AgentRunAuthorization.Request run, AgentRunAuthorization.Boundary boundary) {
        if (run == null || boundary == null || calls.isShutdown() || "LEGACY".equals(run.actor().issuerFingerprint())) return AgentRunAuthorization.Decision.UNAVAILABLE;
        try {
            for (var binding : bindings.stream().filter(value -> value.matches(run)).toList()) {
                var maximum = clock.instant().plus(timeout); var deadline = run.deadlineAt().isBefore(maximum) ? run.deadlineAt() : maximum;
                var request = new Request(new MemoryOperationContext(run.actor(), deadline, run), binding.owner(run.actor()), binding.scopeId(), Operation.RETRIEVE, null, 0, null, null);
                var result = authorize(request);
                if (result != Decision.ALLOW) return result == Decision.DENY ? AgentRunAuthorization.Decision.DENY : AgentRunAuthorization.Decision.UNAVAILABLE;
            }
            return AgentRunAuthorization.Decision.ALLOW;
        } catch (RuntimeException unavailable) { return AgentRunAuthorization.Decision.UNAVAILABLE; }
    }

    @Override public Decision authorize(Request request) {
        if (request == null || request.context().runAuthorization() == null || calls.isShutdown() || Thread.currentThread().isInterrupted()
                || TransactionSynchronizationManager.isActualTransactionActive()) return Decision.UNAVAILABLE;
        if (request.operation() != Operation.GET && request.operation() != Operation.RETRIEVE && request.operation() != Operation.SEARCH) return Decision.DENY;
        try {
            var remaining = Duration.between(clock.instant(), request.context().deadlineAt()); if (remaining.isNegative() || remaining.isZero()) return Decision.UNAVAILABLE;
            var trace = TraceContext.capture(); var future = calls.submit(() -> { try (var ignored = trace.open()) { return evaluate(request); } });
            try { return future.get((remaining.compareTo(timeout) < 0 ? remaining : timeout).toNanos(), TimeUnit.NANOSECONDS); }
            catch (InterruptedException interrupted) { future.cancel(true); Thread.currentThread().interrupt(); return Decision.UNAVAILABLE; }
            catch (TimeoutException expired) { future.cancel(true); return Decision.UNAVAILABLE; }
            catch (ExecutionException unavailable) { return Decision.UNAVAILABLE; }
        } catch (RejectedExecutionException full) { return Decision.UNAVAILABLE; }
        catch (RuntimeException unavailable) { return Decision.UNAVAILABLE; }
    }

    private Decision evaluate(Request request) {
        var run = request.context().runAuthorization();
        var selected = bindings.stream().filter(value -> value.matches(run) && value.owner(run.actor()).equals(request.owner()) && value.scopeId().equals(request.scopeId())).toList();
        if (selected.size() != 1) return Decision.DENY; var binding = selected.getFirst();
        var client = (run.actor().subjectType() == TrustedAgentActor.SubjectType.USER ? users : clients).get(run.actor().issuerFingerprint());
        if (client == null || !client.tenantId().equals(Long.toString(run.actor().tenantId()))) return Decision.UNAVAILABLE;
        var trust = issuers.findByIssuer(client.issuer().toASCIIString()).orElse(null);
        if (trust == null || trust.status() != TrustedIssuerRegistry.Status.ACTIVE || trust.realmType() != TrustedIssuerRegistry.RealmType.TENANT || !client.tenantId().equals(trust.tenantId())) return Decision.UNAVAILABLE;
        var scope = binding.scope(run, subject(run)); var ticket = store.currentEvidence(run, resource, scope).orElse(null); if (ticket == null) return Decision.UNAVAILABLE;
        var expected = new HttpExecutionAuthorizationClient.Expectation(client.issuer(), client.tenantId(), run.actor().subjectId(), resource, MemoryReadGrant.PURPOSE,
                MemoryReadGrant.intentId(run, scope).toString(), MemoryReadGrant.fingerprint(run, resource, scope), false, run.deadlineAt());
        try {
            boolean allowed = true; java.time.Instant expires;
            if (run.actor().subjectType() == TrustedAgentActor.SubjectType.USER) {
                var decision = client.client().decideScopes(new ExecutionAuthorizationContract.Request(systemCode, ticket.grantId(), ticket.ciphertext()), expected);
                if (decision.generatedAt().isAfter(clock.instant())) return Decision.UNAVAILABLE;
                expires = decision.execution().expiresAt();
                if (request.owner().type() == MemoryOwnerTypeEnum.TEAM) {
                    var dataScope = decision.dataScopes().get(TianjiAgentPermissions.MEMORY_READ);
                    if (dataScope != null && dataScope.decision() == top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision.INDETERMINATE) return Decision.UNAVAILABLE;
                    allowed = team(request, client.issuer(), decision);
                }
            } else {
                var subject = scope.serviceSubject();
                var intent = new HttpExecutionAuthorizationClient.ServiceExpectation(expected, new ServiceExecutionAuthorizationContract.Subject(subject.clientId(), subject.appId(),
                        subject.businessCode(), subject.applicationCode(), subject.environment(), subject.resourceUri().toASCIIString()));
                expires = client.client().decideService(new ServiceExecutionAuthorizationContract.Request(systemCode, ticket.grantId(), ticket.ciphertext()), intent).expiresAt();
            }
            if (!unchanged(run, binding, scope, client, trust, ticket) || !expires.isAfter(ledger.databaseNow()) || expires.isAfter(ticket.expiresAt())) return Decision.UNAVAILABLE;
            return allowed ? Decision.ALLOW : Decision.DENY;
        } catch (HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException denied) {
            return unchanged(run, binding, scope, client, trust, ticket) ? Decision.DENY : Decision.UNAVAILABLE;
        }
    }

    private AgentServiceExecutionGrant.Subject subject(AgentRunAuthorization.Request run) {
        if (run.actor().subjectType() == TrustedAgentActor.SubjectType.USER) return null;
        if (services == null) throw invalid(); var material = ledger.executionMaterial(run.actor(), new GetAgentRunQuery(run.runId()));
        if (!run.equals(material.authorization())) throw invalid(); return services.require(run, material.snapshot().processLink());
    }
    private boolean unchanged(AgentRunAuthorization.Request run, MemoryContextBinding binding, MemoryReadGrant.Scope scope, TianjiExecutionGrantAuthorizer.BoundClient client,
                              TrustedIssuerRegistry.TrustedIssuerVO trust, MemoryReadGrant.Ticket ticket) {
        return issuers.findByIssuer(client.issuer().toASCIIString()).filter(trust::equals).isPresent()
                && MemoryReadGrant.fingerprint(run, resource, binding.scope(run, subject(run))).equals(MemoryReadGrant.fingerprint(run, resource, scope))
                && store.currentEvidence(run, resource, scope).filter(ticket::equals).isPresent() && run.deadlineAt().isAfter(ledger.databaseNow()) && ticket.expiresAt().isAfter(ledger.databaseNow());
    }
    private boolean team(Request request, URI issuer, ExecutionScopeAuthorizationContract.Decision decision) {
        var mapping = teams.teams().stream().filter(value -> value.tenantId() == request.context().actor().tenantId() && value.issuer().toASCIIString().equals(issuer.toASCIIString())
                && value.teamId().equals(request.owner().id()) && value.scopeId().equals(request.scopeId())).findFirst().orElse(null);
        var scope = decision.dataScopes().get(TianjiAgentPermissions.MEMORY_READ);
        if (mapping == null || scope == null || scope.decision() != top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision.ALLOW) return false;
        return scope.allInTenant() || (mapping.directoryType() == TianjiMemoryAccessProperties.DirectoryType.ORGANIZATION
                ? scope.allowedOrgIds().contains(mapping.directoryId()) : scope.allowedDeptIds().contains(mapping.directoryId()));
    }
    @Override public void close() { calls.shutdownNow(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Deferred Memory authority binding is invalid"); }
}
