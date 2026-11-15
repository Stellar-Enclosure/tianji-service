package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import java.net.URI;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
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
@Slf4j
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

    public void ensureRuntime(TianjiAgentAccess.Authorized authorized, ServiceIdentityPrincipal principal,
            TianjiLinkedDispatchProperties.Binding binding, LinkedAgentRuntimeCommand command, AgentRunDTO run) {
        try {
            if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(run, "run");
            requireIdentity(authorized, principal);
            var authority = requireIssuer(authorized);
            var actor = authorized.actor();
            var material = ledger.runtimeExecutionMaterial(actor, new GetAgentRunQuery(run.runId()));
            if (material == null || !sameRuntimeCommand(command, material.command())) throw unavailable();
            var authorization = material.authorization();
            var subject = requireSubject(authorized, principal, binding, command.getLink(), run, material.run(), authorization);
            if (material.run().status() != AgentRunStatusEnum.QUEUED || material.run().cancelRequested()) return;
            if (tools != null) {
                var toolReady = tools.ensureRuntime(authorized, material.run());
                if (toolReady.status() != AgentRunStatusEnum.QUEUED || toolReady.cancelRequested()) return;
            } else if (hasRuntimeTools(material)) {
                throw unavailable();
            }
            requireIdentity(authorized, principal);
            issue(authorized, principal, command.getLink(), authorization, subject, authority);
        } catch (AgentLedgerException known) {
            throw known;
        } catch (HttpExecutionGrantClient.ExecutionGrantDeniedException denied) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Service execution authorization was denied");
        } catch (RuntimeException failure) {
            log.debug("Linked Agent runtime SERVICE grant is unavailable");
            throw unavailable();
        }
    }

    private static boolean sameRuntimeCommand(LinkedAgentRuntimeCommand requested, LinkedAgentRuntimeCommand stored) {
        return stored != null && runtimeCommandPins(requested).equals(runtimeCommandPins(stored))
                && Arrays.equals(ProcessTypedJson.canonicalBytes(requested.getInput()), ProcessTypedJson.canonicalBytes(stored.getInput()))
                && Arrays.equals(ProcessTypedJson.canonicalBytes(requested.getParameters()), ProcessTypedJson.canonicalBytes(stored.getParameters()));
    }

    private static List<?> runtimeCommandPins(LinkedAgentRuntimeCommand command) {
        return List.of(command.getIdempotencyKey(), command.getLink(), command.getNodeExecutionRef(),
                command.getTemplateKey(), command.getTemplateVersion(), command.getTemplateSha256(), command.getConfigurationSha256(),
                command.getInputSchemaSha256(), command.getOutputSchemaSha256(), command.getLimits(), command.getAttemptDeadlineAt(),
                command.getCorrelationId());
    }

    private static boolean hasRuntimeTools(AgentLedgerService.RuntimeExecutionMaterialVO material) {
        var configuration = material.template().getConfiguration();
        var plan = configuration.agenticPlan();
        return plan == null ? !configuration.toolRefs().isEmpty()
                : plan.agents().stream().anyMatch(agent -> !agent.toolRefs().isEmpty());
    }

    private void ensureInternal(TianjiAgentAccess.Authorized authorized, ServiceIdentityPrincipal principal,
            TianjiLinkedDispatchProperties.Binding binding, LinkedAgentStartCommand command, AgentRunDTO run) {
        Objects.requireNonNull(binding, "binding"); Objects.requireNonNull(command, "command"); Objects.requireNonNull(run, "run");
        requireIdentity(authorized, principal);
        var authority = requireIssuer(authorized);
        var material = ledger.executionMaterial(authorized.actor(), new GetAgentRunQuery(run.runId()));
        if (!command.link().equals(material.snapshot().processLink())) throw unavailable();
        var authorization = material.authorization();
        var subject = requireSubject(authorized, principal, binding, command.link(), run, material.run(), authorization);
        if (material.run().status() != AgentRunStatusEnum.QUEUED) return;
        issue(authorized, principal, command.link(), authorization, subject, authority);
    }

    private AgentServiceExecutionGrant.Subject requireSubject(TianjiAgentAccess.Authorized authorized,
            ServiceIdentityPrincipal principal, TianjiLinkedDispatchProperties.Binding binding, AgentProcessLink link,
            AgentRunDTO submitted, AgentRunDTO stored, AgentRunAuthorization.Request authorization) {
        if (binding == null || link == null || authorization == null || stored == null || !submitted.runId().equals(stored.runId())
                || !authorization.actor().equals(authorized.actor())) throw unavailable();
        var subject = bindings.require(authorization, link);
        if (!subject.clientId().equals(principal.clientId()) || !subject.appId().equals(principal.appId())
                || !subject.businessCode().equals(principal.sourceBizCode()) || !subject.applicationCode().equals(principal.sourceAppCode())
                || !subject.environment().equals(principal.sourceEnvironment()) || !binding.clientId().equals(subject.clientId())
                || !binding.issuer().toASCIIString().equals(authorized.issuer().toASCIIString())
                || binding.tenantId() != authorized.actor().tenantId() || !binding.sourceId().equals(link.sourceId())) throw unavailable();
        return subject;
    }

    private IssuerVO requireIssuer(TianjiAgentAccess.Authorized authorized) {
        URI issuer = authorized.issuer();
        var trust = issuers.findByIssuer(issuer.toASCIIString()).orElseThrow(TianjiServiceExecutionGrantIssuer::unavailable);
        var client = clients.get(issuer);
        if (client == null || trust.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                || !Long.toString(authorized.actor().tenantId()).equals(trust.tenantId())) throw unavailable();
        return new IssuerVO(trust, client);
    }

    private void issue(TianjiAgentAccess.Authorized authorized, ServiceIdentityPrincipal principal, AgentProcessLink link,
            AgentRunAuthorization.Request authorization, AgentServiceExecutionGrant.Subject subject, IssuerVO authority) {
        var actor = authorized.actor();
        if (store.current(authorization, resource, subject).isPresent()) return;
        var reserved = store.reserve(actor, authorization.runId(), resource, subject);
        if (reserved.isEmpty()) {
            if (store.current(authorization, resource, subject).isPresent()) return;
            throw unavailable();
        }
        var reservation = reserved.get();
        if (!reservation.authorization().equals(authorization)) throw unavailable();
        String token = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet
                ? VerifiedAgentIssuerFilter.currentServiceToken(servlet.getRequest()) : null;
        if (token == null) throw unavailable();
        var issued = authority.client().issueService(new ServiceExecutionGrantContract.IssueRequest(token, AgentRunAuthorization.PURPOSE,
                authorization.runId(), reservation.bindingSha256(), authorization.sessionRequired(), authorization.deadlineAt()));
        requireIdentity(authorized, principal);
        var currentSubject = bindings.require(authorization, link);
        if (issuers.findByIssuer(authorized.issuer().toASCIIString()).filter(authority.trust()::equals).isEmpty() || !subject.equals(currentSubject)
                || !subject.resourceUri().toASCIIString().equals(currentSubject.resourceUri().toASCIIString())) throw unavailable();
        var ticket = new AgentServiceExecutionGrant.Ticket(issued.ticket().grantId(), issued.ticket().ciphertext(), issued.expiresAt());
        if (store.complete(reservation, ticket) || store.current(authorization, resource, subject).isPresent()) return;
        throw unavailable();
    }

    private record IssuerVO(TrustedIssuerRegistry.TrustedIssuerVO trust, HttpExecutionGrantClient client) { }

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
