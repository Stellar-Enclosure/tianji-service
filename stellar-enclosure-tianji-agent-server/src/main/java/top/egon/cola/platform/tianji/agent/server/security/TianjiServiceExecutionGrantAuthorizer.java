package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentServiceExecutionGrantStore;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ServiceExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

/** Evaluates SERVICE evidence inside the existing bounded Agent authorization executor. */
public final class TianjiServiceExecutionGrantAuthorizer {
    private final AgentLedgerService ledger;
    private final AgentServiceExecutionGrantStore store;
    private final TianjiServiceExecutionBindings bindings;
    private final URI resource;
    private final String systemCode;
    private final TrustedIssuerRegistry issuers;
    private final Map<String, TianjiExecutionGrantAuthorizer.BoundClient> clients;

    public TianjiServiceExecutionGrantAuthorizer(AgentLedgerService ledger, AgentServiceExecutionGrantStore store,
            TianjiServiceExecutionBindings bindings, IdpStarterProperties starter, IdentityAuthorityProperties authority,
            TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth, ObjectMapper mapper, Rbac3StarterProperties rbac) {
        this(ledger, store, bindings, TianjiExecutionAuthorityBindings.resource(starter.getResourceUri()),
                TianjiExecutionGrantAuthorizer.systemCode(rbac.getSystemCode()), issuers,
                TianjiExecutionGrantAuthorizer.clients(starter, authority, issuers, oauth, mapper, rbac,
                        IdentityAuthorityScopes.SERVICE_EXECUTION_GRANT_ISSUE, ServiceExecutionAuthorizationContract.SERVICE_SCOPE));
    }

    TianjiServiceExecutionGrantAuthorizer(AgentLedgerService ledger, AgentServiceExecutionGrantStore store,
            TianjiServiceExecutionBindings bindings, URI resource, String systemCode, TrustedIssuerRegistry issuers,
            Map<String, TianjiExecutionGrantAuthorizer.BoundClient> clients) {
        this.ledger = Objects.requireNonNull(ledger, "ledger"); this.store = Objects.requireNonNull(store, "store");
        this.bindings = Objects.requireNonNull(bindings, "bindings"); this.resource = TianjiExecutionAuthorityBindings.resource(resource);
        this.systemCode = TianjiExecutionGrantAuthorizer.systemCode(systemCode); this.issuers = Objects.requireNonNull(issuers, "issuers");
        this.clients = Map.copyOf(clients);
        if (!store.usesLedger(ledger) || !bindings.usesIssuerRegistry(issuers) || this.clients.isEmpty()
                || this.clients.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(TrustedIssuerRegistry.issuerHash(entry.getValue().issuer())))) {
            throw new IllegalArgumentException("TIANJI_SERVICE_EXECUTION_AUTHORIZATION_CONFIGURATION_INVALID");
        }
    }

    boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate && store.usesLedger(candidate); }
    boolean usesIssuerRegistry(TrustedIssuerRegistry candidate) { return issuers == candidate && bindings.usesIssuerRegistry(candidate); }

    AgentRunAuthorization.Decision evaluate(AgentRunAuthorization.Request request) {
        if (request == null || request.actor().subjectType() != TrustedAgentActor.SubjectType.SERVICE
                || "LEGACY".equals(request.actor().issuerFingerprint())) return AgentRunAuthorization.Decision.UNAVAILABLE;
        try {
            var material = ledger.linkedRunEvidence(request.actor(), new GetAgentRunQuery(request.runId()));
            if (!request.equals(material.authorization())) return AgentRunAuthorization.Decision.UNAVAILABLE;
            var link = material.processLink();
            var binding = bindings.resolve(request, link);
            var bound = clients.get(request.actor().issuerFingerprint());
            if (bound == null || !bound.issuer().toASCIIString().equals(binding.issuer().toASCIIString())
                    || !bound.tenantId().equals(Long.toString(request.actor().tenantId()))) return AgentRunAuthorization.Decision.UNAVAILABLE;
            var trust = issuers.findByIssuer(bound.issuer().toASCIIString()).orElse(null);
            if (trust == null || trust.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                    || !bound.tenantId().equals(trust.tenantId())) return AgentRunAuthorization.Decision.UNAVAILABLE;
            var ticket = store.current(request, resource, binding.subject()).orElse(null);
            if (ticket == null) return AgentRunAuthorization.Decision.UNAVAILABLE;
            var subject = binding.subject();
            var intent = new HttpExecutionAuthorizationClient.Expectation(bound.issuer(), bound.tenantId(), request.actor().subjectId(),
                    resource, AgentRunAuthorization.PURPOSE, request.runId(), AgentServiceExecutionGrant.fingerprint(request, resource, subject),
                    request.sessionRequired(), request.deadlineAt());
            var expected = new HttpExecutionAuthorizationClient.ServiceExpectation(intent,
                    new ServiceExecutionAuthorizationContract.Subject(subject.clientId(), subject.appId(), subject.businessCode(),
                            subject.applicationCode(), subject.environment(), subject.resourceUri().toASCIIString()));
            try {
                var result = bound.client().decideService(new ServiceExecutionAuthorizationContract.Request(systemCode,
                        ticket.grantId(), ticket.ciphertext()), expected);
                if (!unchanged(request, link, binding, bound, trust, ticket) || !result.expiresAt().isAfter(ledger.databaseNow())
                        || result.expiresAt().isAfter(ticket.expiresAt())) return AgentRunAuthorization.Decision.UNAVAILABLE;
                return AgentRunAuthorization.Decision.ALLOW;
            } catch (HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException denied) {
                return unchanged(request, link, binding, bound, trust, ticket) ? AgentRunAuthorization.Decision.DENY : AgentRunAuthorization.Decision.UNAVAILABLE;
            }
        } catch (RuntimeException failure) { return AgentRunAuthorization.Decision.UNAVAILABLE; }
    }

    private boolean unchanged(AgentRunAuthorization.Request request, AgentProcessLink link, TianjiServiceExecutionBindings.Bound binding,
            TianjiExecutionGrantAuthorizer.BoundClient bound, TrustedIssuerRegistry.TrustedIssuerVO trust,
            AgentServiceExecutionGrant.Ticket ticket) {
        if (!issuers.findByIssuer(bound.issuer().toASCIIString()).filter(trust::equals).isPresent()) return false;
        var material = ledger.linkedRunEvidence(request.actor(), new GetAgentRunQuery(request.runId()));
        if (!request.equals(material.authorization()) || !link.equals(material.processLink())) return false;
        var current = bindings.resolve(request, material.processLink());
        if (!binding.issuer().toASCIIString().equals(current.issuer().toASCIIString()) || !binding.subject().equals(current.subject())
                || !binding.subject().resourceUri().toASCIIString().equals(current.subject().resourceUri().toASCIIString())
                || store.current(request, resource, binding.subject()).filter(ticket::equals).isEmpty()) return false;
        var now = ledger.databaseNow();
        return request.deadlineAt().isAfter(now) && ticket.expiresAt().isAfter(now);
    }
}
