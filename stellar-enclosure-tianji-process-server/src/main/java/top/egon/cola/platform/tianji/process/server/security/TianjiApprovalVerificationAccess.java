package top.egon.cola.platform.tianji.process.server.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.process.server.identity.ApprovalVerificationContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.time.Clock;
import java.util.Objects;

/** Binds current service identity and token expiry after the real exact-resource execution guard. */
public final class TianjiApprovalVerificationAccess {
    private final TianjiProcessAccess access;
    private final Clock clock;
    public TianjiApprovalVerificationAccess(TianjiProcessAccess access, Clock clock) { this.access = Objects.requireNonNull(access, "access"); this.clock = Objects.requireNonNull(clock, "clock"); }
    public ApprovalVerificationContext require() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Approval verification authorization requires no ambient transaction");
        var actor = access.require(TianjiProcessPermissions.APPROVAL_VERIFY_API); var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (actor.subjectType() != ProcessActor.SubjectType.SERVICE || !(authentication instanceof IdpAuthenticationToken token) || !token.isAuthenticated()
                || !(token.getPrincipal() instanceof ServiceIdentityPrincipal principal) || !Long.toString(actor.tenantId()).equals(principal.tenantId())
                || !actor.equals(ProcessActor.verified(actor.tenantId(), ProcessActor.SubjectType.SERVICE, principal.subject(), token.verifiedIssuer().toASCIIString()))) throw denied();
        var now = clock.instant(); if (!principal.expiresAt().isAfter(now)) throw denied(); var deadline = now.plusSeconds(30);
        if (principal.expiresAt().isBefore(deadline)) deadline = principal.expiresAt(); return new ApprovalVerificationContext(actor, deadline);
    }
    private static AccessDeniedException denied() { return new AccessDeniedException("Current approval-verification SERVICE identity is required"); }
}
