package top.egon.cola.platform.tianji.process.server.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.process.server.identity.CaseContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Projects current verified Case-operation facts before the service's local transaction. */
public final class TianjiCaseAccess {
    private final TianjiProcessAccess access;
    private final Clock clock;
    public TianjiCaseAccess(TianjiProcessAccess access, Clock clock) { this.access = Objects.requireNonNull(access, "access"); this.clock = Objects.requireNonNull(clock, "clock"); }

    public CaseContext require(String resource) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Current Case authorization requires no ambient transaction");
        var actor = access.require(resource); var authentication = SecurityContextHolder.getContext().getAuthentication(); Instant now = clock.instant();
        if (actor.subjectType() == ProcessActor.SubjectType.SERVICE) {
            if ("tianji.api.process.case.actPlanItem".equals(resource) || !(authentication instanceof IdpAuthenticationToken token)
                    || !token.isAuthenticated() || !(token.getPrincipal() instanceof ServiceIdentityPrincipal identity)
                    || !actor.subjectId().equals(identity.subject()) || !Long.toString(actor.tenantId()).equals(identity.tenantId())
                    || !actor.equals(ProcessActor.verified(actor.tenantId(), ProcessActor.SubjectType.SERVICE, identity.subject(), token.verifiedIssuer().toString()))) throw denied();
            if (!identity.expiresAt().isAfter(now)) throw unavailable();
            Instant deadline = identity.expiresAt().isBefore(now.plusSeconds(30)) ? identity.expiresAt() : now.plusSeconds(30);
            return new CaseContext(actor, resource, Set.of(), deadline, 0, 0);
        }
        if (!(authentication instanceof Rbac3AuthenticationToken authenticated) || !authenticated.isAuthenticated()) throw denied();
        var details = authenticated.getPrincipal(); var snapshot = details.snapshot(); var view = snapshot.permissionSnapshot();
        if (view == null || snapshot.policyBinding() == null || !"tianji".equals(snapshot.systemCode()) || !"tianji".equals(view.activeRoleContext().applicationCode())
                || !"tianji".equals(view.catalogScope().applicationCode()) || !"xingyuan".equals(view.catalogScope().businessCode())
                || snapshot.generatedAt().isAfter(now) || !snapshot.expiresAt().isAfter(now) || !details.identity().expiresAt().isAfter(now)) throw unavailable();
        if (!actor.subjectId().equals(snapshot.identitySub()) || !Long.toString(actor.tenantId()).equals(snapshot.tenantId())
                || !actor.equals(ProcessActor.verified(actor.tenantId(), ProcessActor.SubjectType.USER, snapshot.identitySub(), view.issuer()))) throw denied();
        Instant deadline = now.plusSeconds(30); if (snapshot.expiresAt().isBefore(deadline)) deadline = snapshot.expiresAt(); if (details.identity().expiresAt().isBefore(deadline)) deadline = details.identity().expiresAt();
        var roles = new HashSet<>(view.activeRoleContext().effectiveRoleIds());
        if (roles.size() != view.activeRoleContext().effectiveRoleIds().size()) throw unavailable();
        try { return new CaseContext(actor, resource, roles, deadline, snapshot.authVersion(), snapshot.policyVersion()); }
        catch (IllegalArgumentException invalid) { throw unavailable(); }
    }
    private static AccessDeniedException denied() { return new AccessDeniedException("Current Case identity is required"); }
    private static IllegalStateException unavailable() { return new IllegalStateException("Current Case identity or membership evidence is unavailable"); }
}
