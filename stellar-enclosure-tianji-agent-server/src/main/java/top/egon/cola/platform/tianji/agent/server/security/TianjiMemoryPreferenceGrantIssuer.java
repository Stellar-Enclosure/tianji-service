package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceIntent;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceSourceBinding;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryPreferenceIntentStore;
import top.egon.cola.platform.tianji.agent.server.integration.memory.TianjiMemoryHttpAuthorization;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Optional USER-request enhancement. Its independent write grant never gates the ordinary Agent run claim. */
public final class TianjiMemoryPreferenceGrantIssuer {
    private final AgentLedgerService ledger;
    private final MemoryPreferenceIntentStore store;
    private final List<MemoryPreferenceSourceBinding> bindings;
    private final TianjiAgentAccess access;
    private final TianjiMemoryHttpAuthorization permissions;
    private final URI resource;
    private final TrustedIssuerRegistry issuers;
    private final Map<URI, HttpExecutionGrantClient> clients;
    private final Duration issueTimeout;
    private final Duration lifetime;
    private final int maximumAttempts;
    private final Clock clock;

    public TianjiMemoryPreferenceGrantIssuer(AgentLedgerService ledger, MemoryPreferenceIntentStore store, List<MemoryPreferenceSourceBinding> bindings,
            TianjiAgentAccess access, TianjiMemoryHttpAuthorization permissions, IdpStarterProperties starter, IdentityAuthorityProperties authority,
            TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth, ObjectMapper mapper, Duration lifetime, int maximumAttempts) {
        this(ledger, store, bindings, access, permissions, TianjiExecutionAuthorityBindings.resource(starter.getResourceUri()), issuers,
                TianjiMemoryGrantIssuer.clients(starter, authority, issuers, oauth, mapper, IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE), authority.getTimeout(), lifetime, maximumAttempts, Clock.systemUTC());
    }

    TianjiMemoryPreferenceGrantIssuer(AgentLedgerService ledger, MemoryPreferenceIntentStore store, List<MemoryPreferenceSourceBinding> bindings,
            TianjiAgentAccess access, TianjiMemoryHttpAuthorization permissions, URI resource, TrustedIssuerRegistry issuers,
            Map<URI, HttpExecutionGrantClient> clients, Duration issueTimeout, Duration lifetime, int maximumAttempts, Clock clock) {
        this.ledger = Objects.requireNonNull(ledger, "ledger"); this.store = Objects.requireNonNull(store, "store"); this.access = Objects.requireNonNull(access, "access");
        this.permissions = Objects.requireNonNull(permissions, "permissions"); this.resource = TianjiExecutionAuthorityBindings.resource(resource);
        this.issuers = Objects.requireNonNull(issuers, "issuers"); this.clients = Map.copyOf(clients); this.clock = Objects.requireNonNull(clock, "clock");
        this.issueTimeout = Objects.requireNonNull(issueTimeout, "issueTimeout"); this.lifetime = Objects.requireNonNull(lifetime, "lifetime"); this.maximumAttempts = maximumAttempts;
        if (!store.usesLedger(ledger) || bindings == null || bindings.isEmpty() || bindings.size() > 1000 || issueTimeout.compareTo(Duration.ofMillis(100)) < 0
                || issueTimeout.compareTo(Duration.ofSeconds(30)) > 0 || lifetime.compareTo(Duration.ofSeconds(1)) < 0 || lifetime.compareTo(Duration.ofHours(8)) > 0
                || lifetime.compareTo(issueTimeout) <= 0 || maximumAttempts < 1 || maximumAttempts > 100) throw invalid();
        this.bindings = List.copyOf(bindings); var ids = new HashSet<String>(); var sources = new HashSet<List<String>>(); var definitions = new HashMap<List<String>, String>();
        for (var binding : this.bindings) {
            var definition = List.of(Long.toString(binding.tenantId()), binding.issuerFingerprint(), binding.definitionKey(), Long.toString(binding.definitionVersion()));
            var source = new java.util.ArrayList<>(definition); source.add(binding.scopeId()); String previous = definitions.putIfAbsent(definition, binding.definitionSha256());
            if (!ids.add(binding.id()) || !sources.add(List.copyOf(source)) || previous != null && !previous.equals(binding.definitionSha256())
                    || this.clients.keySet().stream().noneMatch(issuer -> TrustedIssuerRegistry.issuerHash(issuer).equals(binding.issuerFingerprint()))) throw invalid();
        }
    }
    public boolean usesBindings(List<MemoryPreferenceSourceBinding> configured) { return bindings.equals(configured); }
    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate && store.usesLedger(candidate); }
    boolean usesIssuerRegistry(TrustedIssuerRegistry candidate) { return issuers == candidate; }
    boolean usesResource(URI candidate) { return resource.toASCIIString().equals(candidate.toASCIIString()); }

    public AgentRunDTO ensure(TianjiAgentAccess.Authorized authorized, AgentRunDTO submitted) {
        try { return ensureInternal(authorized, submitted); }
        catch (AgentLedgerException known) { throw known; }
        catch (RuntimeException unavailable) { throw unavailable(); }
    }

    private AgentRunDTO ensureInternal(TianjiAgentAccess.Authorized authorized, AgentRunDTO submitted) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Preference issuance requires no ambient transaction");
        Objects.requireNonNull(authorized, "authorized"); Objects.requireNonNull(submitted, "submitted"); var actor = authorized.actor();
        if (actor.subjectType() != TrustedAgentActor.SubjectType.USER || !eligible(submitted)) return submitted;
        var selected = bindings.stream().filter(binding -> binding.tenantId() == actor.tenantId() && binding.issuerFingerprint().equals(actor.issuerFingerprint())
                && binding.definitionKey().equals(submitted.definitionKey()) && binding.definitionVersion() == submitted.definitionVersion()).toList();
        if (selected.isEmpty()) return submitted;
        URI issuer = authorized.issuer();
        if (issuer == null || authorized.expiresAt() == null || !actor.equals(TrustedAgentActor.verified(actor.tenantId(), actor.subjectType(), actor.subjectId(), issuer.toASCIIString()))) throw unavailable();
        var trusted = issuers.findByIssuer(issuer.toASCIIString()).orElseThrow(TianjiMemoryPreferenceGrantIssuer::unavailable);
        if (trusted.status() != TrustedIssuerRegistry.Status.ACTIVE || trusted.realmType() != TrustedIssuerRegistry.RealmType.TENANT || !Long.toString(actor.tenantId()).equals(trusted.tenantId())) throw unavailable();
        var client = clients.get(issuer); if (client == null) throw unavailable();
        Instant deadline = earlier(clock.instant().plusSeconds(30), authorized.expiresAt());
        long remaining = Duration.between(clock.instant(), deadline).toNanos(); if (remaining < issueTimeout.toNanos()) return submitted; long monotonic = System.nanoTime() + remaining;
        var query = new GetAgentRunQuery(submitted.runId());
        for (var binding : selected) {
            if (!live(deadline, monotonic)) break;
            var permission = permission(authorized, binding, deadline); if (permission != MemoryAuthorization.Decision.ALLOW) continue;
            String token = VerifiedUserTokenCarrier.currentOrNull(); if (token == null) continue;
            var material = ledger.executionMaterial(actor, query); var run = material.authorization();
            if (!eligible(material.run()) || material.snapshot().processLink() != null) return material.run();
            if (!binding.matches(run) || !actor.equals(run.actor()) || !submitted.runId().equals(run.runId())) throw unavailable();
            if (!live(deadline, monotonic)) break;
            if (store.currentEvidence(run, binding, resource).isPresent()) continue;
            if (monotonic - System.nanoTime() < issueTimeout.toNanos()) break;
            Instant requestedDeadline = earlier(clock.instant().plus(lifetime), authorized.expiresAt());
            var reserved = store.reserve(run, binding, resource, requestedDeadline, maximumAttempts); if (reserved.isEmpty()) continue;
            var reservation = reserved.orElseThrow();
            if (!live(deadline, monotonic) || monotonic - System.nanoTime() < issueTimeout.toNanos() || Duration.between(clock.instant(), reservation.deadlineAt()).compareTo(issueTimeout) < 0) {
                store.recordIssueFailure(reservation, "ISSUE_BUDGET_EXHAUSTED", false); continue;
            }
            ExecutionGrantContract.Issued issued;
            try { issued = client.issue(new ExecutionGrantContract.IssueRequest(token, MemoryPreferenceIntent.PURPOSE, reservation.intentId().toString(), reservation.bindingSha256(), false, reservation.deadlineAt())); }
            catch (HttpExecutionGrantClient.ExecutionGrantDeniedException denied) { store.recordIssueFailure(reservation, "OWNER_DENIED", true); continue; }
            catch (RuntimeException unknown) { store.recordIssueFailure(reservation, "ISSUE_UNAVAILABLE", false); continue; }
            if (!live(deadline, monotonic) || !issuers.findByIssuer(issuer.toASCIIString()).filter(trusted::equals).isPresent()) {
                store.recordIssueFailure(reservation, "ISSUE_AUTHORITY_CHANGED", false); continue;
            }
            permission = permission(authorized, binding, deadline);
            if (permission != MemoryAuthorization.Decision.ALLOW) {
                store.recordIssueFailure(reservation, permission == MemoryAuthorization.Decision.DENY ? "CURRENT_PERMISSION_DENIED" : "CURRENT_AUTHORITY_UNAVAILABLE", permission == MemoryAuthorization.Decision.DENY); continue;
            }
            var current = ledger.executionMaterial(actor, query);
            if (!live(deadline, monotonic) || !run.equals(current.authorization()) || !eligible(current.run()) || current.snapshot().processLink() != null) {
                store.recordIssueFailure(reservation, "SOURCE_CHANGED", true); continue;
            }
            AgentExecutionGrant.Ticket ticket;
            try { ticket = new AgentExecutionGrant.Ticket(issued.ticket().grantId(), issued.ticket().ciphertext(), issued.expiresAt()); new MemoryPreferenceIntent.Evidence(reservation, ticket); }
            catch (RuntimeException malformed) { store.recordIssueFailure(reservation, "ISSUE_RESPONSE_INVALID", false); continue; }
            if (!store.complete(reservation, ticket)) store.recordIssueFailure(reservation, "ISSUE_EPOCH_CHANGED", false);
        }
        return ledger.get(actor, query);
    }

    private MemoryAuthorization.Decision permission(TianjiAgentAccess.Authorized authorized, MemoryPreferenceSourceBinding binding, Instant deadline) {
        try {
            var source = access.require(TianjiAgentPermissions.GET_API);
            if (!authorized.actor().equals(source.actor()) || !authorized.expiresAt().equals(source.expiresAt()) || source.issuer() == null || !authorized.issuer().toASCIIString().equals(source.issuer().toASCIIString())) return MemoryAuthorization.Decision.DENY;
            return permissions.authorize(new MemoryAuthorization.Request(new MemoryOperationContext(authorized.actor(), deadline, null), new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, authorized.actor().subjectId()),
                    binding.scopeId(), MemoryAuthorization.Operation.UPSERT, "preference-admission", 0, null, null));
        } catch (AccessDeniedException | AuthenticationException denied) { return MemoryAuthorization.Decision.DENY; }
        catch (RuntimeException unavailable) { return MemoryAuthorization.Decision.UNAVAILABLE; }
    }
    private boolean live(Instant deadline, long monotonic) { return !Thread.currentThread().isInterrupted() && clock.instant().isBefore(deadline) && monotonic - System.nanoTime() > 0; }
    private static Instant earlier(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
    private static boolean eligible(AgentRunDTO run) { return !run.cancelRequested() && (run.status() == AgentRunStatusEnum.QUEUED || run.status() == AgentRunStatusEnum.RUNNING || run.status() == AgentRunStatusEnum.COMPLETED); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory preference issue binding is invalid"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Memory preference source or issue authority is unavailable"); }
}
