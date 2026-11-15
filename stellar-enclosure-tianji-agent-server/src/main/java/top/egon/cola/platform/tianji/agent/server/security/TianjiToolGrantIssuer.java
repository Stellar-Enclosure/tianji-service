package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolCapabilityStore;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.contract.IdpPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.IdentityPrincipal;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/** Obtains independent tool grants only while the actual submission owner and verified request carrier remain present. */
@Slf4j
public final class TianjiToolGrantIssuer {
    private final AgentLedgerService ledger;
    private final ToolCapabilityStore store;
    private final TianjiToolGrantBindings bindings;
    private final URI resource;
    private final TrustedIssuerRegistry issuers;
    private final Map<URI, HttpExecutionGrantClient> users;
    private final Map<URI, HttpExecutionGrantClient> clients;
    private final Duration issueTimeout;
    private final Clock clock;
    private final LongSupplier nanoTime;

    public TianjiToolGrantIssuer(AgentLedgerService ledger, ToolCapabilityStore store, TianjiToolGrantBindings bindings,
            IdpStarterProperties starter, IdentityAuthorityProperties authority, TrustedIssuerRegistry issuers,
            IdpServiceOAuth2Client oauth, ObjectMapper mapper) {
        this(ledger, store, bindings, TianjiExecutionAuthorityBindings.resource(starter.getResourceUri()), issuers,
                bindings.bindings().stream().anyMatch(binding -> binding.principalType() == TrustedAgentActor.SubjectType.USER)
                        ? TianjiMemoryGrantIssuer.clients(starter, authority, issuers, oauth, mapper, IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE) : Map.of(),
                bindings.hasServiceBindings() ? TianjiMemoryGrantIssuer.clients(starter, authority, issuers, oauth, mapper, IdentityAuthorityScopes.SERVICE_EXECUTION_GRANT_ISSUE) : Map.of(),
                authority.getTimeout(), Clock.systemUTC(), System::nanoTime);
    }

    TianjiToolGrantIssuer(AgentLedgerService ledger, ToolCapabilityStore store, TianjiToolGrantBindings bindings, URI resource,
            TrustedIssuerRegistry issuers, Map<URI, HttpExecutionGrantClient> users, Map<URI, HttpExecutionGrantClient> clients,
            Duration issueTimeout, Clock clock, LongSupplier nanoTime) {
        this.ledger = Objects.requireNonNull(ledger, "ledger"); this.store = Objects.requireNonNull(store, "store"); this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.resource = TianjiExecutionAuthorityBindings.resource(resource); this.issuers = Objects.requireNonNull(issuers, "issuers");
        this.users = Map.copyOf(users); this.clients = Map.copyOf(clients); this.issueTimeout = Objects.requireNonNull(issueTimeout, "issueTimeout");
        this.clock = Objects.requireNonNull(clock, "clock"); this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (!store.usesLedger(ledger) || !bindings.usesResource(resource) || !bindings.usesIssuerRegistry(issuers)
                || issueTimeout.compareTo(Duration.ofMillis(100)) < 0 || issueTimeout.compareTo(Duration.ofSeconds(5)) > 0) throw invalid();
        for (var map : List.of(this.users, this.clients)) for (var issuer : map.keySet()) TianjiExecutionAuthorityBindings.resource(issuer);
        for (var binding : bindings.bindings()) {
            var selected = binding.principalType() == TrustedAgentActor.SubjectType.USER ? this.users : this.clients;
            if (selected.keySet().stream().noneMatch(issuer -> TrustedIssuerRegistry.issuerHash(issuer).equals(binding.issuerFingerprint()))) throw invalid();
        }
    }

    public AgentRunDTO ensure(TianjiAgentAccess.Authorized authorized, AgentRunDTO submitted) {
        return ensure(authorized, submitted, AgentLedgerService.RunProfileEnum.LEGACY_V1);
    }

    public AgentRunDTO ensureRuntime(TianjiAgentAccess.Authorized authorized, AgentRunDTO submitted) {
        return ensure(authorized, submitted, AgentLedgerService.RunProfileEnum.RUNTIME_V2);
    }

    private AgentRunDTO ensure(TianjiAgentAccess.Authorized authorized, AgentRunDTO submitted,
            AgentLedgerService.RunProfileEnum profile) {
        try { return prepare(authorized, submitted, profile); }
        catch (AgentLedgerException failure) {
            throw new AgentLedgerException(failure.code(), failure.code() == AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED
                    ? "Tool grant issuance was denied" : "Tool grant preparation is unavailable or changed");
        } catch (HttpExecutionGrantClient.ExecutionGrantDeniedException denied) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Tool grant issuance was denied");
        } catch (RuntimeException failure) {
            log.debug("Agent tool grant is unavailable for execution profile {}", profile);
            throw unavailable();
        }
    }

    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate && store.usesLedger(candidate); }
    public boolean usesBindings(TianjiToolGrantBindings candidate) { return bindings == candidate; }
    public boolean usesIssuerRegistry(TrustedIssuerRegistry candidate) { return issuers == candidate && bindings.usesIssuerRegistry(candidate); }
    public boolean usesResource(URI candidate) { return candidate != null && resource.toASCIIString().equals(candidate.toASCIIString()); }
    public boolean usesServices(TianjiServiceExecutionBindings candidate) { return bindings.usesServices(candidate); }
    public boolean hasServiceBindings() { return bindings.hasServiceBindings(); }

    private AgentRunDTO prepare(TianjiAgentAccess.Authorized authorized, AgentRunDTO submitted,
            AgentLedgerService.RunProfileEnum profile) {
        outsideTransaction(); long started = nanoTime.getAsLong(); Instant wallStarted = clock.instant();
        if (authorized == null || authorized.actor() == null || authorized.expiresAt() == null || authorized.issuer() == null || submitted == null) throw unavailable();
        var actor = authorized.actor();
        if (!actor.equals(TrustedAgentActor.verified(actor.tenantId(), actor.subjectType(), actor.subjectId(), authorized.issuer().toASCIIString()))) throw unavailable();
        var material = material(actor, submitted.runId(), profile); if (!queued(material)) return material.run();
        var selected = resolve(material); if (selected.isEmpty()) return material.run();
        var run = material.authorization(); var window = window(authorized, run, wallStarted, started); check(window);
        var trust = trust(authorized); var identity = identity(authorized, selected); String bearer = bearer(actor.subjectType());
        var client = (actor.subjectType() == TrustedAgentActor.SubjectType.USER ? users : clients).get(authorized.issuer()); if (client == null) throw unavailable();
        for (var tool : selected) {
            var current = postflight(authorized, material, selected, trust, identity, bearer, window); if (!queued(current)) return current.run();
            var active = store.current(tool.binding(), tool.descriptor());
            if (active.isPresent()) {
                requireEvidence(active.orElseThrow(), tool.binding(), window);
                current = postflight(authorized, material, selected, trust, identity, bearer, window); if (!queued(current)) return current.run();
                continue;
            }
            callBudget(window); var reserved = store.reserve(tool.binding(), tool.descriptor());
            if (reserved.isEmpty()) {
                current = postflight(authorized, material, selected, trust, identity, bearer, window); if (!queued(current)) return current.run();
                var concurrent = store.current(tool.binding(), tool.descriptor()).orElseThrow(TianjiToolGrantIssuer::unavailable);
                requireEvidence(concurrent, tool.binding(), window); continue;
            }
            var reservation = reserved.orElseThrow(); if (!reservation.binding().equals(tool.binding())) throw unavailable();
            current = postflight(authorized, material, selected, trust, identity, bearer, window); if (!queued(current)) return current.run();
            callBudget(window); ToolExecutionGrant.EvidenceVO evidence;
            try {
                if (actor.subjectType() == TrustedAgentActor.SubjectType.USER) {
                    var issued = client.issue(new ExecutionGrantContract.IssueRequest(bearer, tool.binding().purpose(), tool.binding().capabilityId(),
                            tool.binding().bindingSha256(), ToolExecutionGrant.SESSION_REQUIRED, run.deadlineAt()));
                    evidence = new ToolExecutionGrant.EvidenceVO(reservation, issued.ticket().grantId(), issued.ticket().ciphertext(), issued.expiresAt());
                } else {
                    var issued = client.issueService(new ServiceExecutionGrantContract.IssueRequest(bearer, tool.binding().purpose(), tool.binding().capabilityId(),
                            tool.binding().bindingSha256(), ToolExecutionGrant.SESSION_REQUIRED, run.deadlineAt()));
                    evidence = new ToolExecutionGrant.EvidenceVO(reservation, issued.ticket().grantId(), issued.ticket().ciphertext(), issued.expiresAt());
                }
            } catch (HttpExecutionGrantClient.ExecutionGrantDeniedException denied) {
                current = postflight(authorized, material, selected, trust, identity, bearer, window); if (!queued(current)) return current.run(); throw denied;
            }
            current = postflight(authorized, material, selected, trust, identity, bearer, window); if (!queued(current)) return current.run();
            requireEvidence(evidence, tool.binding(), window);
            if (!store.attach(evidence, tool.descriptor())) {
                current = postflight(authorized, material, selected, trust, identity, bearer, window); if (!queued(current)) return current.run();
                var concurrent = store.current(tool.binding(), tool.descriptor()).orElseThrow(TianjiToolGrantIssuer::unavailable);
                requireEvidence(concurrent, tool.binding(), window);
            }
        }
        return postflight(authorized, material, selected, trust, identity, bearer, window).run();
    }

    private IssuanceMaterialVO material(TrustedAgentActor actor, String runId, AgentLedgerService.RunProfileEnum profile) {
        outsideTransaction();
        var query = new GetAgentRunQuery(runId);
        if (profile == AgentLedgerService.RunProfileEnum.RUNTIME_V2) {
            var pinned = ledger.toolRunEvidence(actor, query);
            if (pinned == null || pinned.profile() != profile || pinned.run() == null || pinned.authorization() == null
                    || actor.subjectType() != TrustedAgentActor.SubjectType.SERVICE || !actor.equals(pinned.authorization().actor())
                    || !runId.equals(pinned.run().runId()) || !runId.equals(pinned.authorization().runId())
                    || pinned.processLink() == null || !pinned.authorization().definitionSha256().equals(pinned.processLink().definitionContentSha256())
                    || pinned.authorization().sessionRequired() || pinned.run().sessionId() != null) throw unavailable();
            return new IssuanceMaterialVO(pinned.run(), pinned.authorization(), null, pinned);
        }
        var material = ledger.executionMaterial(actor, query);
        if (material == null || material.run() == null || material.authorization() == null || material.snapshot() == null) throw unavailable();
        var run = material.authorization(); var view = material.run(); var snapshot = material.snapshot(); var definition = snapshot.definition();
        if (!actor.equals(run.actor()) || !runId.equals(run.runId()) || !runId.equals(view.runId()) || !run.definitionKey().equals(view.definitionKey())
                || run.definitionVersion() != view.definitionVersion() || run.sessionRequired() != (view.sessionId() != null) || !run.deadlineAt().equals(view.deadlineAt())
                || !run.definitionKey().equals(definition.definitionKey()) || run.definitionVersion() != definition.version()
                || !run.definitionSha256().equals(snapshot.definitionContentSha256()) || !run.policyVersionRef().equals(definition.dependencies().policyVersionRef())) throw unavailable();
        return new IssuanceMaterialVO(view, run, material, null);
    }

    private List<TianjiToolGrantBindings.ResolvedToolResult> resolve(IssuanceMaterialVO material) {
        return material.runtime() == null ? bindings.resolve(material.legacy()) : bindings.resolveRuntime(material.runtime());
    }

    private IssuanceMaterialVO postflight(TianjiAgentAccess.Authorized authorized, IssuanceMaterialVO expected,
            List<TianjiToolGrantBindings.ResolvedToolResult> selected, TrustedIssuerRegistry.TrustedIssuerVO originalTrust, OwnerIdentityVO identity,
            String bearer, WindowVO window) {
        check(window);
        var profile = expected.runtime() == null ? AgentLedgerService.RunProfileEnum.LEGACY_V1 : AgentLedgerService.RunProfileEnum.RUNTIME_V2;
        var current = material(authorized.actor(), expected.authorization().runId(), profile);
        if (!current.authorization().equals(expected.authorization()) || !samePinnedScope(expected, current)) throw unavailable();
        if (!queued(current)) return current;
        var resolved = resolve(current);
        if (!selected.equals(resolved) || !sameTrust(originalTrust, trust(authorized)) || !identity.equals(identity(authorized, resolved))
                || !bearer.equals(bearer(authorized.actor().subjectType()))) throw unavailable();
        check(window); if (!current.authorization().deadlineAt().isAfter(ledger.databaseNow())) throw unavailable(); check(window); return current;
    }

    private OwnerIdentityVO identity(TianjiAgentAccess.Authorized authorized, List<TianjiToolGrantBindings.ResolvedToolResult> selected) {
        outsideTransaction(); Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) throw unavailable();
        IdpPrincipal principal;
        if (authorized.actor().subjectType() == TrustedAgentActor.SubjectType.USER) {
            if (!(authentication instanceof Rbac3AuthenticationToken user)) throw unavailable();
            var details = user.getPrincipal(); var snapshot = details.snapshot();
            IdentityPrincipal identity = details.identity();
            if (snapshot.permissionSnapshot() == null || !authorized.issuer().toASCIIString().equals(snapshot.permissionSnapshot().issuer())
                    || !identity.audience().contains(resource.toASCIIString())) throw unavailable();
            principal = identity;
        } else {
            if (!(authentication instanceof IdpAuthenticationToken token) || !(token.getPrincipal() instanceof ServiceIdentityPrincipal service)
                    || !token.verifiedIssuer().toASCIIString().equals(authorized.issuer().toASCIIString()) || service.scopeContext() != ServiceTokenContext.TENANT
                    || !service.resourceUri().toASCIIString().equals(resource.toASCIIString()) || !service.scopes().contains(TianjiAgentPermissions.LINKED_SUBMIT_SCOPE)) throw unavailable();
            for (var tool : selected) {
                var source = tool.binding().serviceSubject();
                if (source == null || !service.clientId().equals(source.clientId()) || !service.appId().equals(source.appId())
                        || !service.sourceBizCode().equals(source.businessCode()) || !service.sourceAppCode().equals(source.applicationCode())
                        || !service.sourceEnvironment().equals(source.environment())) throw unavailable();
            }
            principal = service;
        }
        if (!principal.subject().equals(authorized.actor().subjectId()) || !Long.toString(authorized.actor().tenantId()).equals(principal.tenantId())
                || !principal.expiresAt().equals(authorized.expiresAt()) || !principal.expiresAt().isAfter(clock.instant())) throw unavailable();
        return new OwnerIdentityVO(principal, authentication.getAuthorities().stream().map(authority -> authority.getAuthority()).filter(scope -> scope != null && scope.startsWith("SCOPE_")).collect(Collectors.toSet()));
    }

    private String bearer(TrustedAgentActor.SubjectType type) {
        outsideTransaction();
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet)) throw unavailable();
        String token = type == TrustedAgentActor.SubjectType.USER ? VerifiedUserTokenCarrier.current(servlet.getRequest()) : VerifiedAgentIssuerFilter.currentServiceToken(servlet.getRequest());
        if (token == null) throw unavailable(); return token;
    }
    private TrustedIssuerRegistry.TrustedIssuerVO trust(TianjiAgentAccess.Authorized authorized) {
        var current = issuers.findByIssuer(authorized.issuer().toASCIIString()).orElseThrow(TianjiToolGrantIssuer::unavailable);
        if (current.status() != TrustedIssuerRegistry.Status.ACTIVE || current.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                || !Long.toString(authorized.actor().tenantId()).equals(current.tenantId()) || !current.issuer().toASCIIString().equals(authorized.issuer().toASCIIString())) throw unavailable();
        return current;
    }
    private void requireEvidence(ToolExecutionGrant.EvidenceVO evidence, ToolExecutionGrant.BindingVO expected, WindowVO window) {
        check(window);
        if (!expected.equals(evidence.reservation().binding()) || !evidence.expiresAt().isAfter(clock.instant()) || !evidence.expiresAt().isAfter(ledger.databaseNow())) throw unavailable();
        check(window);
    }
    private WindowVO window(TianjiAgentAccess.Authorized authorized, AgentRunAuthorization.Request run, Instant startedAt, long startedNanos) {
        Instant deadline = startedAt.plusSeconds(30); if (authorized.expiresAt().isBefore(deadline)) deadline = authorized.expiresAt(); if (run.deadlineAt().isBefore(deadline)) deadline = run.deadlineAt();
        if (!deadline.isAfter(startedAt)) throw unavailable(); return new WindowVO(deadline, startedNanos + Duration.between(startedAt, deadline).toNanos());
    }
    private long check(WindowVO window) {
        outsideTransaction(); long remaining = window.nanoDeadline() - nanoTime.getAsLong();
        if (Thread.currentThread().isInterrupted() || !clock.instant().isBefore(window.deadlineAt()) || remaining <= 0) throw unavailable(); return remaining;
    }
    private void callBudget(WindowVO window) {
        if (check(window) < issueTimeout.toNanos() || Duration.between(clock.instant(), window.deadlineAt()).compareTo(issueTimeout) < 0) throw unavailable();
    }
    private static void outsideTransaction() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable(); }
    private static boolean queued(IssuanceMaterialVO material) {
        return material.run().status() == AgentRunStatusEnum.QUEUED && !material.run().cancelRequested();
    }
    private static boolean samePinnedScope(IssuanceMaterialVO expected, IssuanceMaterialVO current) {
        if (expected.runtime() == null) return sameSnapshot(expected.legacy().snapshot(), current.legacy().snapshot());
        var left = expected.runtime();
        var right = current.runtime();
        return left.profile() == right.profile() && left.processLink().equals(right.processLink())
                && left.toolRefs().equals(right.toolRefs()) && left.maxToolCalls() == right.maxToolCalls()
                && Objects.equals(left.legacyApprovalPolicyRef(), right.legacyApprovalPolicyRef());
    }
    private static boolean sameSnapshot(AgentExecutionSnapshot expected, AgentExecutionSnapshot current) {
        return expected.definition().equals(current.definition()) && expected.definitionContentSha256().equals(current.definitionContentSha256())
                && expected.effectivePolicy().equals(current.effectivePolicy()) && Objects.equals(expected.processLink(), current.processLink());
    }
    private static boolean sameTrust(TrustedIssuerRegistry.TrustedIssuerVO expected, TrustedIssuerRegistry.TrustedIssuerVO current) {
        return expected.equals(current) && expected.issuer().toASCIIString().equals(current.issuer().toASCIIString()) && expected.jwksUri().toASCIIString().equals(current.jwksUri().toASCIIString());
    }
    private record IssuanceMaterialVO(AgentRunDTO run, AgentRunAuthorization.Request authorization,
            AgentLedgerService.ExecutionMaterial legacy, AgentLedgerService.ToolRunEvidenceVO runtime) {
        @Override public String toString() { return "ToolGrantMaterial[redacted]"; }
    }
    private record WindowVO(Instant deadlineAt, long nanoDeadline) { }
    private record OwnerIdentityVO(IdpPrincipal principal, Set<String> scopes) {
        OwnerIdentityVO { scopes = Set.copyOf(scopes); }
        @Override public String toString() { return "ToolGrantOwner[redacted]"; }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool grant issuer configuration is invalid"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Tool grant issuance is unavailable or changed"); }
}
