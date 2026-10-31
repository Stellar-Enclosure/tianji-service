package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentServiceExecutionGrantStore;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ServiceExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

/** Obtains one owner-sealed SERVICE intent outside the ledger transaction and publishes it behind the process fence. */
public final class TianjiServiceExecutionGrantIssuer implements TianjiLinkedServiceAdmission {
    private final AgentLedgerService ledger;
    private final AgentServiceExecutionGrantStore store;
    private final TianjiServiceExecutionBindings bindings;
    private final URI resource;
    private final TrustedIssuerRegistry issuers;
    private final Map<URI, HttpExecutionGrantClient> clients;
    private final Clock clock;
    private final TianjiMemoryGrantIssuer memory;
    private final TianjiToolGrantIssuer tools;

    public boolean usesMemoryIssuer(TianjiMemoryGrantIssuer candidate) { return memory == candidate; }

    public TianjiServiceExecutionGrantIssuer(AgentLedgerService ledger, AgentServiceExecutionGrantStore store,
            TianjiServiceExecutionBindings bindings, IdpStarterProperties starter, IdentityAuthorityProperties authority,
            TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth, ObjectMapper mapper) {
        this(ledger, store, bindings, starter, authority, issuers, oauth, mapper, null);
    }

    public TianjiServiceExecutionGrantIssuer(AgentLedgerService ledger, AgentServiceExecutionGrantStore store,
            TianjiServiceExecutionBindings bindings, IdpStarterProperties starter, IdentityAuthorityProperties authority,
            TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth, ObjectMapper mapper, TianjiMemoryGrantIssuer memory) {
        this(ledger, store, bindings, TianjiExecutionAuthorityBindings.resource(starter.getResourceUri()), issuers,
                clients(starter, authority, issuers, oauth, mapper), Clock.systemUTC(), memory);
    }

    TianjiServiceExecutionGrantIssuer(AgentLedgerService ledger, AgentServiceExecutionGrantStore store,
            TianjiServiceExecutionBindings bindings, URI resource, TrustedIssuerRegistry issuers,
            Map<URI, HttpExecutionGrantClient> clients, Clock clock) {
        this(ledger, store, bindings, resource, issuers, clients, clock, null);
    }

    TianjiServiceExecutionGrantIssuer(AgentLedgerService ledger, AgentServiceExecutionGrantStore store,
            TianjiServiceExecutionBindings bindings, URI resource, TrustedIssuerRegistry issuers,
            Map<URI, HttpExecutionGrantClient> clients, Clock clock, TianjiMemoryGrantIssuer memory) {
        this(ledger, store, bindings, resource, issuers, clients, clock, memory, null);
    }

    public boolean usesToolIssuer(TianjiToolGrantIssuer candidate) { return tools == candidate; }

    public TianjiServiceExecutionGrantIssuer(AgentLedgerService ledger, AgentServiceExecutionGrantStore store,
            TianjiServiceExecutionBindings bindings, IdpStarterProperties starter, IdentityAuthorityProperties authority,
            TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth, ObjectMapper mapper, TianjiMemoryGrantIssuer memory,
            TianjiToolGrantIssuer tools) {
        this(ledger, store, bindings, TianjiExecutionAuthorityBindings.resource(starter.getResourceUri()), issuers,
                clients(starter, authority, issuers, oauth, mapper), Clock.systemUTC(), memory, tools);
    }

    TianjiServiceExecutionGrantIssuer(AgentLedgerService ledger, AgentServiceExecutionGrantStore store,
            TianjiServiceExecutionBindings bindings, URI resource, TrustedIssuerRegistry issuers,
            Map<URI, HttpExecutionGrantClient> clients, Clock clock, TianjiMemoryGrantIssuer memory, TianjiToolGrantIssuer tools) {
        this.ledger = Objects.requireNonNull(ledger, "ledger"); this.store = Objects.requireNonNull(store, "store");
        this.bindings = Objects.requireNonNull(bindings, "bindings"); this.resource = TianjiExecutionAuthorityBindings.resource(resource);
        this.issuers = Objects.requireNonNull(issuers, "issuers"); this.clients = Map.copyOf(clients);
        this.clock = Objects.requireNonNull(clock, "clock");
        if (memory != null && (!memory.usesLedger(ledger) || !memory.usesIssuerRegistry(issuers) || !memory.usesResource(resource))) throw new IllegalArgumentException("Memory issue authority does not match the service issuer");
        this.memory = memory;
        if (!store.usesLedger(ledger) || !bindings.usesIssuerRegistry(issuers) || this.clients.isEmpty()) {
            throw new IllegalArgumentException("TIANJI_SERVICE_EXECUTION_ISSUER_CONFIGURATION_INVALID");
        }
        if (tools != null && (!tools.usesLedger(ledger) || !tools.usesIssuerRegistry(issuers) || !tools.usesResource(resource) || tools.hasServiceBindings() && !tools.usesServices(bindings))) throw new IllegalArgumentException("Tool issue authority does not match the service issuer");
        this.tools = tools;
    }

    @Override
    public void ensure(TianjiAgentAccess.Authorized authorized, ServiceIdentityPrincipal principal,
            TianjiLinkedDispatchProperties.Binding binding, LinkedAgentStartCommand command, AgentRunDTO run) {
        try {
            ensureInternal(authorized, principal, binding, command, run);
            AgentRunDTO current = memory == null ? run : memory.ensure(authorized, run);
            if (tools != null) tools.ensure(authorized, current);
        }
        catch (AgentLedgerException known) { throw known; }
        catch (HttpExecutionGrantClient.ExecutionGrantDeniedException denied) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Service execution authorization was denied");
        } catch (RuntimeException failure) { throw unavailable(); }
    }

    private void ensureInternal(TianjiAgentAccess.Authorized authorized, ServiceIdentityPrincipal principal,
            TianjiLinkedDispatchProperties.Binding binding, LinkedAgentStartCommand command, AgentRunDTO run) {
        Objects.requireNonNull(binding, "binding"); Objects.requireNonNull(command, "command"); Objects.requireNonNull(run, "run");
        requireIdentity(authorized, principal);
        var actor = authorized.actor(); URI issuer = authorized.issuer();
        var trusted = issuers.findByIssuer(issuer.toASCIIString()).orElseThrow(TianjiServiceExecutionGrantIssuer::unavailable);
        if (trusted.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                || !Long.toString(actor.tenantId()).equals(trusted.tenantId())) throw unavailable();
        var client = clients.get(issuer);
        if (client == null) throw unavailable();
        var material = ledger.executionMaterial(actor, new GetAgentRunQuery(run.runId()));
        if (!command.link().equals(material.snapshot().processLink()) || !run.runId().equals(material.run().runId())
                || !material.authorization().actor().equals(actor)) throw unavailable();
        var subject = bindings.require(material.authorization(), material.snapshot().processLink());
        if (!subject.clientId().equals(principal.clientId()) || !subject.appId().equals(principal.appId())
                || !subject.businessCode().equals(principal.sourceBizCode()) || !subject.applicationCode().equals(principal.sourceAppCode())
                || !subject.environment().equals(principal.sourceEnvironment()) || !binding.clientId().equals(subject.clientId())
                || !binding.issuer().toASCIIString().equals(issuer.toASCIIString()) || binding.tenantId() != actor.tenantId()
                || !binding.sourceId().equals(command.link().sourceId())) throw unavailable();
        if (material.run().status() != AgentRunStatusEnum.QUEUED) return;
        if (store.current(material.authorization(), resource, subject).isPresent()) return;
        var reserved = store.reserve(actor, run.runId(), resource, subject);
        if (reserved.isEmpty()) {
            if (store.current(material.authorization(), resource, subject).isPresent()) return;
            throw unavailable();
        }
        var reservation = reserved.get();
        if (!reservation.authorization().equals(material.authorization())) throw unavailable();
        String token = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet
                ? VerifiedAgentIssuerFilter.currentServiceToken(servlet.getRequest()) : null;
        if (token == null) throw unavailable();
        var issued = client.issueService(new ServiceExecutionGrantContract.IssueRequest(token, AgentRunAuthorization.PURPOSE,
                run.runId(), reservation.bindingSha256(), reservation.authorization().sessionRequired(), reservation.authorization().deadlineAt()));
        requireIdentity(authorized, principal);
        var currentSubject = bindings.require(reservation.authorization(), command.link());
        if (!issuers.findByIssuer(issuer.toASCIIString()).filter(trusted::equals).isPresent() || !subject.equals(currentSubject)
                || !subject.resourceUri().toASCIIString().equals(currentSubject.resourceUri().toASCIIString())) throw unavailable();
        var ticket = new AgentServiceExecutionGrant.Ticket(issued.ticket().grantId(), issued.ticket().ciphertext(), issued.expiresAt());
        if (store.complete(reservation, ticket) || store.current(reservation.authorization(), resource, subject).isPresent()) return;
        throw unavailable();
    }

    private void requireIdentity(TianjiAgentAccess.Authorized authorized, ServiceIdentityPrincipal principal) {
        if (authorized == null || authorized.actor() == null || authorized.issuer() == null || principal == null
                || authorized.actor().subjectType() != TrustedAgentActor.SubjectType.SERVICE
                || !(SecurityContextHolder.getContext().getAuthentication() instanceof IdpAuthenticationToken token)
                || !token.isAuthenticated() || token.getPrincipal() != principal || !principal.expiresAt().equals(authorized.expiresAt())
                || principal.scopeContext() != ServiceTokenContext.TENANT
                || !principal.resourceUri().toASCIIString().equals(resource.toASCIIString())
                || !principal.scopes().contains(TianjiAgentPermissions.LINKED_SUBMIT_SCOPE)
                || !principal.expiresAt().isAfter(clock.instant()) || !Long.toString(authorized.actor().tenantId()).equals(principal.tenantId())
                || !authorized.actor().subjectId().equals(principal.subject())) throw unavailable();
        try {
            if (!authorized.issuer().toASCIIString().equals(token.verifiedIssuer().toASCIIString())
                    || !authorized.actor().equals(TrustedAgentActor.verified(authorized.actor().tenantId(),
                    TrustedAgentActor.SubjectType.SERVICE, principal.subject(), authorized.issuer().toASCIIString()))) throw unavailable();
        } catch (IllegalArgumentException | IllegalStateException invalid) { throw unavailable(); }
    }

    private static Map<URI, HttpExecutionGrantClient> clients(IdpStarterProperties starter, IdentityAuthorityProperties authority,
            TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth, ObjectMapper mapper) {
        Map<URI, HttpExecutionGrantClient> result = new LinkedHashMap<>();
        TianjiExecutionAuthorityBindings.source(starter, authority, issuers, IdentityAuthorityScopes.SERVICE_EXECUTION_GRANT_ISSUE)
                .forEach((issuer, context) -> result.put(issuer,
                        new HttpExecutionGrantClient(authority.getBaseUri(), issuer, context, oauth, mapper, authority.getTimeout())));
        return Map.copyOf(result);
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Service execution authorization is unavailable");
    }
}
