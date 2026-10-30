package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryContextBinding;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryReadGrant;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryReadGrantStore;
import top.egon.cola.platform.tianji.agent.server.integration.memory.TianjiMemoryHttpAuthorization;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ServiceExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Issues only reviewed per-source Memory-read intents while the submission identity is still present. */
public final class TianjiMemoryGrantIssuer {
    private final AgentLedgerService ledger;
    private final MemoryReadGrantStore store;
    private final List<MemoryContextBinding> bindings;
    private final TianjiMemoryHttpAuthorization permissions;
    private final TianjiServiceExecutionBindings services;
    private final URI resource;
    private final TrustedIssuerRegistry issuers;
    private final Map<URI, HttpExecutionGrantClient> users;
    private final Map<URI, HttpExecutionGrantClient> clients;
    private final Duration issueTimeout;
    private final Clock clock;

    public TianjiMemoryGrantIssuer(AgentLedgerService ledger, MemoryReadGrantStore store, List<MemoryContextBinding> bindings,
            TianjiMemoryHttpAuthorization permissions, TianjiServiceExecutionBindings services, IdpStarterProperties starter,
            IdentityAuthorityProperties authority, TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth, ObjectMapper mapper) {
        this(ledger, store, bindings, permissions, services, TianjiExecutionAuthorityBindings.resource(starter.getResourceUri()), issuers,
                clients(starter, authority, issuers, oauth, mapper, IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE),
                services == null ? Map.of() : clients(starter, authority, issuers, oauth, mapper, IdentityAuthorityScopes.SERVICE_EXECUTION_GRANT_ISSUE), authority.getTimeout(), Clock.systemUTC());
    }

    TianjiMemoryGrantIssuer(AgentLedgerService ledger, MemoryReadGrantStore store, List<MemoryContextBinding> bindings,
            TianjiMemoryHttpAuthorization permissions, TianjiServiceExecutionBindings services, URI resource, TrustedIssuerRegistry issuers,
            Map<URI, HttpExecutionGrantClient> users, Map<URI, HttpExecutionGrantClient> clients, Duration issueTimeout, Clock clock) {
        this.ledger = Objects.requireNonNull(ledger, "ledger"); this.store = Objects.requireNonNull(store, "store"); this.bindings = MemoryContextBinding.validate(bindings);
        this.permissions = Objects.requireNonNull(permissions, "permissions"); this.services = services; this.resource = TianjiExecutionAuthorityBindings.resource(resource);
        this.issuers = Objects.requireNonNull(issuers, "issuers"); this.users = Map.copyOf(users); this.clients = Map.copyOf(clients); this.clock = Objects.requireNonNull(clock, "clock");
        this.issueTimeout = Objects.requireNonNull(issueTimeout, "issueTimeout");
        if (!store.usesLedger(ledger) || services != null && !services.usesIssuerRegistry(issuers) || issueTimeout.compareTo(Duration.ofMillis(100)) < 0
                || issueTimeout.compareTo(Duration.ofSeconds(30)) > 0) throw new IllegalArgumentException("Memory issue authority configuration is invalid");
        for (var binding : this.bindings) {
            var selected = binding.principalType() == TrustedAgentActor.SubjectType.USER ? this.users : this.clients;
            if (selected.keySet().stream().noneMatch(issuer -> TrustedIssuerRegistry.issuerHash(issuer).equals(binding.issuerFingerprint()))
                    || binding.principalType() == TrustedAgentActor.SubjectType.SERVICE && services == null) throw new IllegalArgumentException("Memory issue origin is unavailable");
        }
    }

    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate && store.usesLedger(candidate); }
    boolean usesIssuerRegistry(TrustedIssuerRegistry candidate) { return issuers == candidate && (services == null || services.usesIssuerRegistry(candidate)); }
    boolean usesResource(URI candidate) { return resource.toASCIIString().equals(candidate.toASCIIString()); }

    /** Unknown or partial issuance leaves the run queued behind the required-scope execution gate. */
    public AgentRunDTO ensure(TianjiAgentAccess.Authorized authorized, AgentRunDTO submitted) {
        try { return ensureInternal(authorized, submitted); }
        catch (AgentLedgerException known) { throw known; }
        catch (HttpExecutionGrantClient.ExecutionGrantDeniedException denied) { throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Memory read authorization was denied"); }
        catch (RuntimeException failed) { throw unavailable(); }
    }

    private AgentRunDTO ensureInternal(TianjiAgentAccess.Authorized authorized, AgentRunDTO submitted) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Memory grant issuance requires no ambient transaction");
        Objects.requireNonNull(authorized, "authorized"); Objects.requireNonNull(submitted, "submitted");
        var actor = authorized.actor(); var query = new GetAgentRunQuery(submitted.runId());
        var material = ledger.executionMaterial(actor, query); var run = material.authorization();
        if (material.run().status() != AgentRunStatusEnum.QUEUED) return material.run();
        var selected = bindings.stream().filter(binding -> binding.matches(run)).toList(); if (selected.isEmpty()) return material.run();
        URI issuer = authorized.issuer();
        if (issuer == null || !actor.equals(TrustedAgentActor.verified(actor.tenantId(), actor.subjectType(), actor.subjectId(), issuer.toASCIIString()))) throw unavailable();
        var trusted = issuers.findByIssuer(issuer.toASCIIString()).orElseThrow(TianjiMemoryGrantIssuer::unavailable);
        if (trusted.status() != TrustedIssuerRegistry.Status.ACTIVE || trusted.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                || !Long.toString(actor.tenantId()).equals(trusted.tenantId())) throw unavailable();
        var client = (actor.subjectType() == TrustedAgentActor.SubjectType.USER ? users : clients).get(issuer); if (client == null) throw unavailable();
        Instant deadline = clock.instant().plusSeconds(30); if (authorized.expiresAt().isBefore(deadline)) deadline = authorized.expiresAt(); if (run.deadlineAt().isBefore(deadline)) deadline = run.deadlineAt();
        long remaining = Duration.between(clock.instant(), deadline).toNanos(); if (remaining <= 0) throw unavailable(); long monotonicDeadline = System.nanoTime() + remaining;
        for (var binding : selected) {
            check(deadline, monotonicDeadline);
            var subject = subject(authorized, run); var scope = binding.scope(run, subject);
            var decision = permissions.authorize(new MemoryAuthorization.Request(new MemoryOperationContext(actor, deadline, null), scope.owner(), scope.scopeId(), MemoryAuthorization.Operation.RETRIEVE, null, 0, null, null));
            if (decision == MemoryAuthorization.Decision.DENY) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current Memory read access was denied");
            if (decision != MemoryAuthorization.Decision.ALLOW) throw unavailable(); check(deadline, monotonicDeadline);
            if (store.currentEvidence(run, resource, scope).isPresent()) continue;
            var reserved = store.reserve(run, resource, scope);
            if (reserved.isEmpty()) {
                if (store.currentEvidence(run, resource, scope).isPresent()) continue;
                var latest = ledger.get(actor, query); if (latest.status() != AgentRunStatusEnum.QUEUED) return latest; throw unavailable();
            }
            if (monotonicDeadline - System.nanoTime() < issueTimeout.toNanos()) throw unavailable();
            var reservation = reserved.orElseThrow(); MemoryReadGrant.Ticket ticket;
            if (actor.subjectType() == TrustedAgentActor.SubjectType.USER) {
                String token = VerifiedUserTokenCarrier.currentOrNull(); if (token == null) throw unavailable();
                var issued = client.issue(new ExecutionGrantContract.IssueRequest(token, MemoryReadGrant.PURPOSE, reservation.intentId().toString(), reservation.bindingSha256(), false, run.deadlineAt()));
                ticket = new MemoryReadGrant.Ticket(issued.ticket().grantId(), issued.ticket().ciphertext(), issued.expiresAt());
            } else {
                String token = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet ? VerifiedAgentIssuerFilter.currentServiceToken(servlet.getRequest()) : null;
                if (token == null) throw unavailable();
                var issued = client.issueService(new ServiceExecutionGrantContract.IssueRequest(token, MemoryReadGrant.PURPOSE, reservation.intentId().toString(), reservation.bindingSha256(), false, run.deadlineAt()));
                ticket = new MemoryReadGrant.Ticket(issued.ticket().grantId(), issued.ticket().ciphertext(), issued.expiresAt());
            }
            check(deadline, monotonicDeadline);
            if (!issuers.findByIssuer(issuer.toASCIIString()).filter(trusted::equals).isPresent()
                    || !reservation.bindingSha256().equals(MemoryReadGrant.fingerprint(run, resource, binding.scope(run, subject(authorized, run))))) throw unavailable();
            if (!store.completeEvidence(reservation, ticket) && store.currentEvidence(run, resource, scope).isEmpty()) {
                var latest = ledger.get(actor, query); if (latest.status() != AgentRunStatusEnum.QUEUED) return latest; throw unavailable();
            }
        }
        return ledger.get(actor, query);
    }

    private AgentServiceExecutionGrant.Subject subject(TianjiAgentAccess.Authorized authorized, AgentRunAuthorization.Request run) {
        if (run.actor().subjectType() == TrustedAgentActor.SubjectType.USER) return null;
        if (services == null || !(SecurityContextHolder.getContext().getAuthentication() instanceof IdpAuthenticationToken token) || !token.isAuthenticated()
                || !(token.getPrincipal() instanceof ServiceIdentityPrincipal principal) || principal.scopeContext() != ServiceTokenContext.TENANT
                || !token.verifiedIssuer().toASCIIString().equals(authorized.issuer().toASCIIString()) || !principal.expiresAt().equals(authorized.expiresAt())
                || !principal.expiresAt().isAfter(clock.instant()) || !principal.resourceUri().toASCIIString().equals(resource.toASCIIString())
                || !principal.subject().equals(run.actor().subjectId()) || !principal.tenantId().equals(Long.toString(run.actor().tenantId()))) throw unavailable();
        var material = ledger.executionMaterial(run.actor(), new GetAgentRunQuery(run.runId())); if (!run.equals(material.authorization())) throw unavailable();
        var subject = services.require(run, material.snapshot().processLink());
        if (!principal.clientId().equals(subject.clientId()) || !principal.appId().equals(subject.appId()) || !principal.sourceBizCode().equals(subject.businessCode())
                || !principal.sourceAppCode().equals(subject.applicationCode()) || !principal.sourceEnvironment().equals(subject.environment())) throw unavailable();
        return subject;
    }
    private void check(Instant deadline, long monotonicDeadline) { if (Thread.currentThread().isInterrupted() || !clock.instant().isBefore(deadline) || monotonicDeadline - System.nanoTime() <= 0) throw unavailable(); }
    private static Map<URI, HttpExecutionGrantClient> clients(IdpStarterProperties starter, IdentityAuthorityProperties authority, TrustedIssuerRegistry issuers,
            IdpServiceOAuth2Client oauth, ObjectMapper mapper, String scope) {
        var result = new LinkedHashMap<URI, HttpExecutionGrantClient>();
        TianjiExecutionAuthorityBindings.source(starter, authority, issuers, scope).forEach((issuer, context) -> result.put(issuer,
                new HttpExecutionGrantClient(authority.getBaseUri(), issuer, context, oauth, mapper, authority.getTimeout()))); return Map.copyOf(result);
    }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Memory read grant issuance is unavailable"); }
}
