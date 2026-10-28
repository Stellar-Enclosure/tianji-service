package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

/** Issues one owner-sealed intent outside the ledger transaction; a later attempt fences an older reply. */
public final class TianjiExecutionGrantIssuer {
    private final AgentLedgerService ledger;
    private final AgentExecutionGrantStore store;
    private final URI resource;
    private final TrustedIssuerRegistry issuers;
    private final Map<URI, HttpExecutionGrantClient> clients;

    public TianjiExecutionGrantIssuer(AgentLedgerService ledger, AgentExecutionGrantStore store,
            IdpStarterProperties starter, IdentityAuthorityProperties authority, TrustedIssuerRegistry issuers,
            IdpServiceOAuth2Client oauth, ObjectMapper mapper) {
        this(ledger, store, requireResource(starter.getResourceUri()), issuers,
                clients(starter, authority, issuers, oauth, mapper));
    }

    TianjiExecutionGrantIssuer(AgentLedgerService ledger, AgentExecutionGrantStore store, URI resource,
            TrustedIssuerRegistry issuers, Map<URI, HttpExecutionGrantClient> clients) {
        this.ledger = Objects.requireNonNull(ledger, "ledger"); this.store = Objects.requireNonNull(store, "store");
        this.resource = requireResource(resource); this.issuers = Objects.requireNonNull(issuers, "issuers");
        this.clients = Map.copyOf(clients);
        if (this.clients.isEmpty()) throw new IllegalStateException("TIANJI_EXECUTION_GRANT_BINDINGS_REQUIRED");
    }

    /** A failed or uncertain issue leaves the run queued; a repeated start with the same key can retry. */
    public AgentRunDTO ensure(TianjiAgentAccess.Authorized authorized, AgentRunDTO run) {
        try { return ensureInternal(authorized, run); }
        catch (AgentLedgerException failure) { throw failure; }
        catch (RuntimeException failure) { throw unavailable(); }
    }

    private AgentRunDTO ensureInternal(TianjiAgentAccess.Authorized authorized, AgentRunDTO run) {
        Objects.requireNonNull(authorized, "authorized"); Objects.requireNonNull(run, "run");
        var actor = authorized.actor();
        if (actor.subjectType() != TrustedAgentActor.SubjectType.USER || run.status() != AgentRunStatusEnum.QUEUED) return run;
        URI issuer = authorized.issuer();
        if (issuer == null || !actor.equals(TrustedAgentActor.verified(actor.tenantId(), actor.subjectType(), actor.subjectId(), issuer.toASCIIString()))) {
            throw unavailable();
        }
        var trusted = issuers.findByIssuer(issuer.toASCIIString()).orElseThrow(TianjiExecutionGrantIssuer::unavailable);
        if (trusted.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                || !Long.toString(actor.tenantId()).equals(trusted.tenantId())) throw unavailable();
        var client = clients.get(issuer);
        if (client == null) throw unavailable();
        var query = new GetAgentRunQuery(run.runId());
        var authorization = ledger.executionMaterial(actor, query).authorization();
        if (store.current(authorization, resource).isPresent()) return ledger.get(actor, query);
        var reserved = store.reserve(actor, run.runId(), resource);
        if (reserved.isEmpty()) return ledger.get(actor, query);
        var reservation = reserved.get();
        String userToken = VerifiedUserTokenCarrier.currentOrNull();
        if (userToken == null) throw unavailable();
        ExecutionGrantContract.Issued issued;
        try {
            issued = client.issue(new ExecutionGrantContract.IssueRequest(userToken, AgentRunAuthorization.PURPOSE,
                    run.runId(), reservation.bindingSha256(), reservation.authorization().sessionRequired(),
                    reservation.authorization().deadlineAt()));
        } catch (HttpExecutionGrantClient.ExecutionGrantDeniedException denied) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Execution authorization was denied");
        } catch (RuntimeException failed) { throw unavailable(); }
        if (!issuers.findByIssuer(issuer.toASCIIString()).filter(trusted::equals).isPresent()) throw unavailable();
        var ticket = new AgentExecutionGrant.Ticket(issued.ticket().grantId(), issued.ticket().ciphertext(), issued.expiresAt());
        if (store.complete(reservation, ticket) || store.current(reservation.authorization(), resource).isPresent()) {
            return ledger.get(actor, query);
        }
        var latest = ledger.get(actor, query);
        if (latest.status() != AgentRunStatusEnum.QUEUED) return latest;
        throw unavailable();
    }

    private static Map<URI, HttpExecutionGrantClient> clients(IdpStarterProperties starter, IdentityAuthorityProperties authority,
            TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth, ObjectMapper mapper) {
        if (starter == null || authority == null || authority.getBindings() == null || authority.getBindings().size() > 4096) {
            throw new IllegalStateException("TIANJI_EXECUTION_GRANT_BINDINGS_INVALID");
        }
        URI source = requireResource(starter.getResourceUri());
        Map<URI, HttpExecutionGrantClient> result = new LinkedHashMap<>();
        authority.getBindings().forEach((name, value) -> {
            if (value == null || value.getScopes() == null || !value.getScopes().contains(IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE)) return;
            if (name == null || !name.matches("[A-Za-z0-9._-]{1,128}") || value.getIssuer() == null) {
                throw new IllegalStateException("TIANJI_EXECUTION_GRANT_BINDING_INVALID");
            }
            URI boundResource = value.getSourceResource() == null ? source : requireResource(value.getSourceResource());
            if (!source.toASCIIString().equals(boundResource.toASCIIString())) return;
            var trusted = issuers.findByIssuer(value.getIssuer().toASCIIString())
                    .orElseThrow(() -> new IllegalStateException("TIANJI_EXECUTION_GRANT_ISSUER_UNTRUSTED"));
            if (trusted.realmType() != TrustedIssuerRegistry.RealmType.TENANT || value.getContext() != ServiceTokenContext.TENANT
                    || !Objects.equals(value.getTenantId(), trusted.tenantId())) {
                throw new IllegalStateException("TIANJI_EXECUTION_GRANT_TENANT_INVALID");
            }
            var context = new IdpServiceTokenRequest(value.getRegistrationId(), starter.getServiceClient().getAppId(), value.getAudience(),
                    value.getContext(), value.getTenantId(), java.util.Set.copyOf(value.getScopes()));
            var client = new HttpExecutionGrantClient(authority.getBaseUri(), value.getIssuer(), context, oauth, mapper, authority.getTimeout());
            if (result.putIfAbsent(value.getIssuer(), client) != null) throw new IllegalStateException("TIANJI_EXECUTION_GRANT_BINDING_DUPLICATE");
        });
        return Map.copyOf(result);
    }

    private static URI requireResource(URI value) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null
                || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null
                || !value.normalize().equals(value) || value.toASCIIString().length() > 2048) {
            throw new IllegalArgumentException("TIANJI_EXECUTION_GRANT_RESOURCE_INVALID");
        }
        return value;
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Execution authorization is unavailable");
    }
}
