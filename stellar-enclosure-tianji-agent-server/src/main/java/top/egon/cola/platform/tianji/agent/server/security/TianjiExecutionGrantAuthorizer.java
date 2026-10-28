package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import top.egon.cola.component.common.trace.TraceContext;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ServiceExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

/** Rechecks owner-sealed intents and current Jianshen policy at every execution boundary. */
public final class TianjiExecutionGrantAuthorizer implements AgentRunAuthorization, AutoCloseable {
    private final AgentLedgerService ledger;
    private final AgentExecutionGrantStore store;
    private final URI resource;
    private final String systemCode;
    private final TrustedIssuerRegistry issuers;
    private final Map<String, BoundClient> clients;
    private final ThreadPoolExecutor executor;
    private final Duration timeout;
    private final Clock clock;
    private final TianjiServiceExecutionGrantAuthorizer services;

    public TianjiExecutionGrantAuthorizer(AgentLedgerService ledger, AgentExecutionGrantStore store,
            IdpStarterProperties starter, IdentityAuthorityProperties authority, TrustedIssuerRegistry issuers,
            IdpServiceOAuth2Client oauth, ObjectMapper mapper, Rbac3StarterProperties rbac,
            Duration timeout, int concurrency) {
        this(ledger, store, starter, authority, issuers, oauth, mapper, rbac, timeout, concurrency, null);
    }

    public TianjiExecutionGrantAuthorizer(AgentLedgerService ledger, AgentExecutionGrantStore store,
            IdpStarterProperties starter, IdentityAuthorityProperties authority, TrustedIssuerRegistry issuers,
            IdpServiceOAuth2Client oauth, ObjectMapper mapper, Rbac3StarterProperties rbac,
            Duration timeout, int concurrency, TianjiServiceExecutionGrantAuthorizer services) {
        this(ledger, store, TianjiExecutionAuthorityBindings.resource(starter.getResourceUri()), systemCode(rbac), issuers,
                clients(starter, authority, issuers, oauth, mapper, rbac), timeout, concurrency, Clock.systemUTC(), services);
    }

    TianjiExecutionGrantAuthorizer(AgentLedgerService ledger, AgentExecutionGrantStore store, URI resource, String systemCode,
            TrustedIssuerRegistry issuers, Map<String, BoundClient> clients, Duration timeout, int concurrency, Clock clock) {
        this(ledger, store, resource, systemCode, issuers, clients, timeout, concurrency, clock, null);
    }

    TianjiExecutionGrantAuthorizer(AgentLedgerService ledger, AgentExecutionGrantStore store, URI resource, String systemCode,
            TrustedIssuerRegistry issuers, Map<String, BoundClient> clients, Duration timeout, int concurrency, Clock clock,
            TianjiServiceExecutionGrantAuthorizer services) {
        this.ledger = Objects.requireNonNull(ledger, "ledger"); this.store = Objects.requireNonNull(store, "store");
        this.resource = TianjiExecutionAuthorityBindings.resource(resource); this.systemCode = systemCode(systemCode);
        this.issuers = Objects.requireNonNull(issuers, "issuers"); this.clients = Map.copyOf(clients);
        if (clients.isEmpty() || timeout == null || timeout.compareTo(Duration.ofMillis(100)) < 0
                || timeout.compareTo(Duration.ofSeconds(30)) > 0 || concurrency < 1 || concurrency > 8) {
            throw new IllegalArgumentException("TIANJI_EXECUTION_AUTHORIZATION_CONFIGURATION_INVALID");
        }
        if (this.clients.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(TrustedIssuerRegistry.issuerHash(entry.getValue().issuer())))) {
            throw new IllegalArgumentException("TIANJI_EXECUTION_AUTHORIZATION_ISSUER_INVALID");
        }
        if (services != null && (!services.usesLedger(ledger) || !services.usesIssuerRegistry(issuers))) {
            throw new IllegalArgumentException("TIANJI_SERVICE_AUTHORIZATION_MISMATCH");
        }
        this.services = services;
        this.timeout = timeout; this.clock = Objects.requireNonNull(clock, "clock");
        this.executor = new ThreadPoolExecutor(0, concurrency, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                Thread.ofVirtual().inheritInheritableThreadLocals(false).name("tianji-authorization-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean usesLedger(AgentLedgerService candidate) {
        return ledger == candidate && store.usesLedger(candidate) && (services == null || services.usesLedger(candidate));
    }
    public boolean supportsService() { return services != null; }

    @Override
    public Decision authorize(Request request, Boundary boundary) {
        if (request == null || boundary == null || (request.actor().subjectType() != TrustedAgentActor.SubjectType.USER
                && (request.actor().subjectType() != TrustedAgentActor.SubjectType.SERVICE || services == null))
                || "LEGACY".equals(request.actor().issuerFingerprint()) || executor.isShutdown()) return Decision.UNAVAILABLE;
        try {
            Duration remaining = Duration.between(clock.instant(), request.deadlineAt());
            if (remaining.isZero() || remaining.isNegative()) return Decision.UNAVAILABLE;
            long waitNanos = (remaining.compareTo(timeout) < 0 ? remaining : timeout).toNanos();
            TraceContext trace = TraceContext.capture();
            var future = executor.submit(() -> {
                try (var ignored = trace.open()) { return evaluate(request); }
            });
            try { return future.get(waitNanos, TimeUnit.NANOSECONDS); }
            catch (InterruptedException interrupted) {
                future.cancel(true); Thread.currentThread().interrupt(); return Decision.UNAVAILABLE;
            } catch (TimeoutException expired) {
                future.cancel(true); return Decision.UNAVAILABLE;
            } catch (ExecutionException failed) { return Decision.UNAVAILABLE; }
        } catch (RejectedExecutionException full) { return Decision.UNAVAILABLE; }
        catch (RuntimeException failed) { return Decision.UNAVAILABLE; }
    }

    private Decision evaluate(Request request) {
        if (request.actor().subjectType() == TrustedAgentActor.SubjectType.SERVICE) return services.evaluate(request);
        try {
            var bound = clients.get(request.actor().issuerFingerprint());
            if (bound == null || !bound.tenantId().equals(Long.toString(request.actor().tenantId()))) return Decision.UNAVAILABLE;
            var trust = issuers.findByIssuer(bound.issuer().toASCIIString()).orElse(null);
            if (trust == null || trust.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                    || !bound.tenantId().equals(trust.tenantId())) return Decision.UNAVAILABLE;
            var ticket = store.current(request, resource).orElse(null);
            if (ticket == null) return Decision.UNAVAILABLE;
            var expected = new HttpExecutionAuthorizationClient.Expectation(bound.issuer(), bound.tenantId(), request.actor().subjectId(),
                    resource, AgentRunAuthorization.PURPOSE, request.runId(), AgentExecutionGrant.fingerprint(request, resource),
                    request.sessionRequired(), request.deadlineAt());
            try {
                var result = bound.client().decide(new ExecutionAuthorizationContract.Request(systemCode, ticket.grantId(), ticket.ciphertext()), expected);
                if (!unchanged(request, bound, trust, ticket) || !result.expiresAt().isAfter(ledger.databaseNow())) return Decision.UNAVAILABLE;
                return Decision.ALLOW;
            } catch (HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException denied) {
                return unchanged(request, bound, trust, ticket) ? Decision.DENY : Decision.UNAVAILABLE;
            }
        } catch (RuntimeException failed) { return Decision.UNAVAILABLE; }
    }

    private boolean unchanged(Request request, BoundClient bound, TrustedIssuerRegistry.TrustedIssuerVO trust,
                              AgentExecutionGrant.Ticket ticket) {
        return issuers.findByIssuer(bound.issuer().toASCIIString()).filter(trust::equals).isPresent()
                && store.current(request, resource).filter(ticket::equals).isPresent()
                && request.deadlineAt().isAfter(ledger.databaseNow()) && ticket.expiresAt().isAfter(ledger.databaseNow());
    }

    private static Map<String, BoundClient> clients(IdpStarterProperties starter, IdentityAuthorityProperties authority,
            TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth, ObjectMapper mapper, Rbac3StarterProperties rbac) {
        return clients(starter, authority, issuers, oauth, mapper, rbac, IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE,
                ExecutionAuthorizationContract.SERVICE_SCOPE);
    }

    static Map<String, BoundClient> clients(IdpStarterProperties starter, IdentityAuthorityProperties authority,
            TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth, ObjectMapper mapper, Rbac3StarterProperties rbac,
            String issueScope, String decisionScope) {
        if (!(IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE.equals(issueScope)
                && ExecutionAuthorizationContract.SERVICE_SCOPE.equals(decisionScope))
                && !(IdentityAuthorityScopes.SERVICE_EXECUTION_GRANT_ISSUE.equals(issueScope)
                && ServiceExecutionAuthorizationContract.SERVICE_SCOPE.equals(decisionScope))) {
            throw new IllegalArgumentException("TIANJI_EXECUTION_AUTHORIZATION_SCOPE_INVALID");
        }
        Objects.requireNonNull(rbac, "rbac");
        var authorization = rbac.getAuthorization();
        var target = authorization.getServiceToken().getResourceUri();
        var endpoint = authorization.getEndpoint();
        if (target == null || endpoint == null) throw new IllegalStateException("TIANJI_EXECUTION_AUTHORIZATION_TARGET_REQUIRED");
        URI audience = TianjiExecutionAuthorityBindings.resource(URI.create(target)); URI base = URI.create(endpoint);
        Map<String, BoundClient> result = new LinkedHashMap<>();
        TianjiExecutionAuthorityBindings.source(starter, authority, issuers, issueScope).forEach((issuer, source) -> {
            var context = new IdpServiceTokenRequest(source.registrationId(), source.appId(), audience, source.context(), source.tenantId(),
                    Set.of(decisionScope));
            var client = new HttpExecutionAuthorizationClient(base, issuer, context, oauth, mapper, authorization.getFetchTimeout());
            String key = TrustedIssuerRegistry.issuerHash(issuer);
            if (result.putIfAbsent(key, new BoundClient(issuer, source.tenantId(), client)) != null) {
                throw new IllegalStateException("TIANJI_EXECUTION_AUTHORIZATION_ISSUER_DUPLICATE");
            }
        });
        return Map.copyOf(result);
    }

    private static String systemCode(Rbac3StarterProperties value) { return systemCode(Objects.requireNonNull(value, "rbac").getSystemCode()); }
    static String systemCode(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")) {
            throw new IllegalArgumentException("TIANJI_EXECUTION_AUTHORIZATION_SYSTEM_INVALID");
        }
        return value;
    }

    @Override public void close() { executor.shutdownNow(); }
    record BoundClient(URI issuer, String tenantId, HttpExecutionAuthorizationClient client) {
        BoundClient {
            issuer = TianjiExecutionAuthorityBindings.resource(issuer);
            if (tenantId == null || !tenantId.matches("[1-9][0-9]{0,18}") || client == null) {
                throw new IllegalArgumentException("TIANJI_EXECUTION_AUTHORIZATION_CLIENT_INVALID");
            }
            try { Long.parseLong(tenantId); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("TIANJI_EXECUTION_AUTHORIZATION_CLIENT_INVALID"); }
        }
    }
}
