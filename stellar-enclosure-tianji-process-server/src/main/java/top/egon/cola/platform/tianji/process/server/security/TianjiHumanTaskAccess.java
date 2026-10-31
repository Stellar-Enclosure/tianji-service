package top.egon.cola.platform.tianji.process.server.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;

/** Adapts the refreshed current-user catalog view after the real Process resource/identity guard. No USER/SERVICE substitution is supported. */
public final class TianjiHumanTaskAccess {
    private final TianjiProcessAccess access;
    private final Clock clock;

    public TianjiHumanTaskAccess(TianjiProcessAccess access, Clock clock) { this.access = Objects.requireNonNull(access, "access"); this.clock = Objects.requireNonNull(clock, "clock"); }

    public HumanTaskContext require(String resource) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Current task authorization requires no ambient transaction");
        var actor = access.require(resource);
        if (actor.subjectType() != ProcessActor.SubjectType.USER || !(SecurityContextHolder.getContext().getAuthentication() instanceof Rbac3AuthenticationToken authenticated)
                || !authenticated.isAuthenticated()) throw denied();
        var details = authenticated.getPrincipal(); var snapshot = details.snapshot(); var view = snapshot.permissionSnapshot(); Instant now = clock.instant();
        if (view == null || snapshot.policyBinding() == null || !"tianji".equals(snapshot.systemCode()) || !"tianji".equals(view.activeRoleContext().applicationCode())
                || !"tianji".equals(view.catalogScope().applicationCode()) || !"xingyuan".equals(view.catalogScope().businessCode())
                || snapshot.generatedAt().isAfter(now) || !snapshot.expiresAt().isAfter(now) || !details.identity().expiresAt().isAfter(now)) throw unavailable();
        if (!actor.subjectId().equals(snapshot.identitySub()) || !Long.toString(actor.tenantId()).equals(snapshot.tenantId())
                || !actor.equals(ProcessActor.verified(actor.tenantId(), ProcessActor.SubjectType.USER, snapshot.identitySub(), view.issuer()))) throw denied();
        Instant deadline = now.plusSeconds(30); if (snapshot.expiresAt().isBefore(deadline)) deadline = snapshot.expiresAt(); if (details.identity().expiresAt().isBefore(deadline)) deadline = details.identity().expiresAt();
        var roles = new HashSet<>(view.activeRoleContext().effectiveRoleIds());
        if (roles.size() != view.activeRoleContext().effectiveRoleIds().size()) throw unavailable();
        try { return new HumanTaskContext(actor, resource, roles, deadline, snapshot.authVersion(), snapshot.policyVersion()); }
        catch (IllegalArgumentException invalid) { throw unavailable(); }
    }
    private static AccessDeniedException denied() { return new AccessDeniedException("Current USER task identity is required"); }
    private static IllegalStateException unavailable() { return new IllegalStateException("Current task membership evidence is unavailable"); }
}
