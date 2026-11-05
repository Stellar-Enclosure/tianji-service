package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.Acknowledgement;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeCommand;
import top.egon.cola.platform.tianji.agent.recovery.TianjiAgentCheckpointResumeConfiguration.ResumeHost;
import top.egon.cola.platform.tianji.agent.server.security.TianjiCheckpointResumeGrantAuthorizer.BoundResumeTicket;
import top.egon.cola.platform.tianji.agent.server.security.TianjiCheckpointResumeGrantAuthorizer.ResumeTicketSource;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient.Expectation;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Explicit host bridge from a currently authorized USER request to an opaque RESUME ticket on the worker. */
public final class TianjiCheckpointResumeGrantHandoff implements ResumeTicketSource {
    private final URI resource;
    private final TrustedIssuerRegistry issuers;
    private final Map<String, HttpExecutionGrantClient> clients;
    private final Clock clock;
    private final int capacity;
    private final Map<Expectation, Slot> active = new HashMap<>();

    public TianjiCheckpointResumeGrantHandoff(IdpStarterProperties starter, IdentityAuthorityProperties issueAuthority,
                                              TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth,
                                              ObjectMapper mapper, int capacity) {
        this(TianjiExecutionAuthorityBindings.resource(starter.getResourceUri()), issuers,
                configuredClients(starter, issueAuthority, issuers, oauth, mapper), capacity, Clock.systemUTC());
    }

    private static Map<URI, HttpExecutionGrantClient> configuredClients(IdpStarterProperties starter,
            IdentityAuthorityProperties authority, TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth, ObjectMapper mapper) {
        var clients = new HashMap<URI, HttpExecutionGrantClient>();
        TianjiExecutionAuthorityBindings.source(starter, authority, issuers).forEach((issuer, context) ->
                clients.put(issuer, new HttpExecutionGrantClient(authority.getBaseUri(), issuer, context, oauth, mapper, authority.getTimeout())));
        return Map.copyOf(clients);
    }

    TianjiCheckpointResumeGrantHandoff(URI resource, TrustedIssuerRegistry issuers,
                                      Map<URI, HttpExecutionGrantClient> clients, int capacity, Clock clock) {
        this.resource = TianjiExecutionAuthorityBindings.resource(resource);
        this.issuers = Objects.requireNonNull(issuers, "issuers");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (clients == null || clients.isEmpty() || capacity < 1 || capacity > 128) {
            throw new IllegalArgumentException("Checkpoint RESUME handoff configuration is invalid");
        }
        var exact = new HashMap<String, HttpExecutionGrantClient>();
        clients.forEach((issuer, client) -> {
            var verified = TianjiExecutionAuthorityBindings.resource(issuer);
            if (exact.putIfAbsent(verified.toASCIIString(), Objects.requireNonNull(client, "client")) != null) {
                throw new IllegalArgumentException("Duplicate checkpoint RESUME issuer");
            }
        });
        this.clients = Map.copyOf(exact);
        this.capacity = capacity;
    }

    /**
     * The trusted caller supplies the current dedicated RESUME authorization from the same request.
     * The raw USER credential is used synchronously only for issuing the independent opaque ticket.
     */
    public CompletionStage<Acknowledgement> submit(TianjiAgentAccess.Authorized owner, ResumeCommand command,
                                                   ResumeHost host, TianjiCheckpointResumeGrantAuthorizer authority) {
        requireOutsideTransaction();
        Objects.requireNonNull(command, "command");
        if (host == null || authority == null || !host.usesAuthorization(authority)
                || !authority.usesResource(resource) || !authority.usesTicketSource(this)) throw unavailable();

        var caller = requireCaller(owner);
        var actor = owner.actor();
        String issuer = owner.issuer().toASCIIString();
        var trusted = trust(actor, issuer);
        var preview = host.previewAuthorization(actor, command);
        if (preview.isEmpty()) {
            if (!caller.equals(requireCaller(owner)) || !trusted.equals(trust(actor, issuer))) throw unavailable();
            return host.dispatch(actor, command);
        }

        var request = preview.orElseThrow();
        if (!actor.equals(request.execution().actor()) || !command.equals(request.command())) throw unavailable();
        var expected = TianjiCheckpointResumeGrantAuthorizer.expectation(request, owner.issuer(), resource);
        var client = clients.get(issuer);
        if (client == null) throw unavailable();
        var slot = reserve(expected, earlier(owner.expiresAt(), expected.deadlineAt()));
        try {
            requireOutsideTransaction();
            if (!caller.equals(requireCaller(owner)) || !trusted.equals(trust(actor, issuer))) throw unavailable();
            var issued = issue(client, caller.servlet(), expected);
            if (!caller.equals(requireCaller(owner)) || !trusted.equals(trust(actor, issuer))
                    || !issued.expiresAt().isAfter(clock.instant()) || issued.expiresAt().isAfter(slot.expiresAt)) throw unavailable();
            var ticket = new AgentExecutionGrant.Ticket(issued.ticket().grantId(), issued.ticket().ciphertext(), issued.expiresAt());
            publish(expected, slot, new BoundResumeTicket(expected, ticket));
            var stage = Objects.requireNonNull(host.dispatch(actor, command));
            stage.whenComplete((acknowledgement, failure) -> release(expected, slot));
            return stage;
        } catch (RuntimeException | Error failure) {
            release(expected, slot);
            throw failure;
        }
    }

    @Override
    public Optional<BoundResumeTicket> current(Expectation binding) {
        synchronized (active) {
            prune();
            var slot = active.get(binding);
            return slot == null ? Optional.empty() : Optional.ofNullable(slot.ticket);
        }
    }

    private ExecutionGrantContract.Issued issue(HttpExecutionGrantClient client, ServletRequestAttributes servlet, Expectation binding) {
        try {
            return client.issue(new ExecutionGrantContract.IssueRequest(VerifiedUserTokenCarrier.current(servlet.getRequest()),
                    binding.purpose(), binding.intentId(), binding.bindingSha256(), binding.sessionRequired(), binding.deadlineAt()));
        } catch (HttpExecutionGrantClient.ExecutionGrantDeniedException denied) {
            throw denied();
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    private CallerVO requireCaller(TianjiAgentAccess.Authorized owner) {
        if (owner == null || owner.actor() == null || owner.actor().subjectType() != TrustedAgentActor.SubjectType.USER
                || owner.issuer() == null || owner.expiresAt() == null || !owner.expiresAt().isAfter(clock.instant())
                || !(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet)
                || !(SecurityContextHolder.getContext().getAuthentication() instanceof Rbac3AuthenticationToken token)
                || !token.isAuthenticated()) throw denied();
        var principal = token.getPrincipal();
        if (principal == null || principal.snapshot() == null) throw denied();
        var identity = principal.identity();
        var permission = principal.snapshot().permissionSnapshot();
        String issuer = owner.issuer().toASCIIString();
        if (identity == null || permission == null || !issuer.equals(permission.issuer())
                || !issuer.equals(servlet.getRequest().getAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE))
                || !owner.actor().subjectId().equals(identity.subject())
                || !Long.toString(owner.actor().tenantId()).equals(identity.tenantId())
                || !owner.expiresAt().equals(identity.expiresAt())) throw denied();
        try {
            if (!TrustedAgentActor.verified(owner.actor().tenantId(), TrustedAgentActor.SubjectType.USER,
                    identity.subject(), issuer).equals(owner.actor())) throw denied();
        } catch (IllegalArgumentException invalid) {
            throw denied();
        }
        return new CallerVO(servlet, token, identity.tokenId());
    }

    private TrustedIssuerRegistry.TrustedIssuerVO trust(TrustedAgentActor actor, String issuer) {
        var current = issuers.findByIssuer(issuer).orElseThrow(TianjiCheckpointResumeGrantHandoff::unavailable);
        if (current.status() != TrustedIssuerRegistry.Status.ACTIVE || current.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                || !issuer.equals(current.issuer().toASCIIString()) || !Long.toString(actor.tenantId()).equals(current.tenantId())) throw unavailable();
        return current;
    }

    private Slot reserve(Expectation binding, Instant expiresAt) {
        synchronized (active) {
            prune();
            if (!expiresAt.isAfter(clock.instant())) throw unavailable();
            if (active.size() >= capacity || active.containsKey(binding)) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "Checkpoint RESUME handoff is busy");
            }
            var slot = new Slot(expiresAt);
            active.put(binding, slot);
            return slot;
        }
    }

    private void publish(Expectation binding, Slot slot, BoundResumeTicket ticket) {
        synchronized (active) {
            if (active.get(binding) != slot || !slot.expiresAt.isAfter(clock.instant())
                    || !ticket.ticket().expiresAt().isAfter(clock.instant())) throw unavailable();
            slot.expiresAt = ticket.ticket().expiresAt();
            slot.ticket = ticket;
        }
    }

    private void release(Expectation binding, Slot slot) {
        synchronized (active) {
            active.remove(binding, slot);
        }
    }

    private void prune() {
        Instant now = clock.instant();
        active.values().removeIf(slot -> !slot.expiresAt.isAfter(now));
    }

    private static void requireOutsideTransaction() {
        if (Thread.currentThread().isInterrupted() || TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) throw unavailable();
    }

    private static Instant earlier(Instant a, Instant b) { return a.isBefore(b) ? a : b; }

    private static AgentLedgerException denied() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current checkpoint RESUME authorization was denied");
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Checkpoint RESUME grant handoff is unavailable");
    }

    private record CallerVO(ServletRequestAttributes servlet, Rbac3AuthenticationToken authentication, String tokenId) {
        @Override public String toString() { return "CheckpointResumeCaller[redacted]"; }
    }

    private static final class Slot {
        private Instant expiresAt;
        private BoundResumeTicket ticket;

        private Slot(Instant expiresAt) { this.expiresAt = expiresAt; }
    }
}
