package top.egon.cola.platform.tianji.process.server.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.process.server.error.ProcessTenantChangedException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.contract.IdpPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.time.Clock;
import java.util.Collections;
import java.util.Objects;

/** Tenant and subject come exclusively from the verified identity after the resource policy allows access. */
public final class TianjiProcessAccess {
    private final PermissionExecutionGuard permissions;
    private final Clock clock;

    public TianjiProcessAccess(PermissionExecutionGuard permissions, Clock clock) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ProcessActor require(String resourceKey) {
        Authentication initial = SecurityContextHolder.getContext().getAuthentication();
        IdpPrincipal original = identity(initial);
        String originalIssuer = issuer(initial);
        String verifiedRequestIssuer = requestIssuer();
        if (!verifiedRequestIssuer.equals(originalIssuer)) throw new AccessDeniedException("identity issuer mismatch");
        if (!original.expiresAt().isAfter(clock.instant())) throw new AuthenticationCredentialsNotFoundException("identity expired");
        permissions.requireExecution(resourceKey);
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        IdpPrincipal verified = identity(current);
        String verifiedIssuer = issuer(current);
        if (!original.subject().equals(verified.subject()) || !Objects.equals(original.tenantId(), verified.tenantId())
                || original.principalType() != verified.principalType() || !original.tokenId().equals(verified.tokenId())
                || !original.expiresAt().equals(verified.expiresAt()) || !verifiedRequestIssuer.equals(verifiedIssuer)) {
            throw new AccessDeniedException("identity changed during authorization");
        }
        if (!verified.expiresAt().isAfter(clock.instant())) throw new AuthenticationCredentialsNotFoundException("identity expired");
        String tenantId = verified.tenantId();
        if (tenantId == null || !tenantId.matches("[1-9][0-9]{0,18}")) throw new AccessDeniedException("tenant-scoped identity required");
        long tenant;
        try { tenant = Long.parseLong(tenantId); }
        catch (NumberFormatException invalid) { throw new AccessDeniedException("tenant-scoped identity required"); }
        if (tenant <= 0) throw new AccessDeniedException("tenant-scoped identity required");
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet) {
            var expected = Collections.list(servlet.getRequest().getHeaders("X-Egon-Tianji-Tenant"));
            if (expected.size() > 1 || (!expected.isEmpty() && !tenantId.equals(expected.getFirst()))) {
                throw new ProcessTenantChangedException();
            }
        }
        ProcessActor.SubjectType type = verified instanceof ServiceIdentityPrincipal
                ? ProcessActor.SubjectType.SERVICE : ProcessActor.SubjectType.USER;
        try { return ProcessActor.verified(tenant, type, verified.subject(), verifiedIssuer); }
        catch (IllegalArgumentException invalid) { throw new AccessDeniedException("identity subject is invalid"); }
    }

    private static IdpPrincipal identity(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("authenticated identity required");
        }
        if (authentication instanceof Rbac3AuthenticationToken user) return user.getPrincipal().identity();
        if (authentication instanceof IdpAuthenticationToken service
                && service.getPrincipal() instanceof ServiceIdentityPrincipal principal) return principal;
        throw new AuthenticationCredentialsNotFoundException("verified identity required");
    }

    private static String issuer(Authentication authentication) {
        if (authentication instanceof Rbac3AuthenticationToken user) {
            var snapshot = user.getPrincipal().snapshot().permissionSnapshot();
            if (snapshot != null) return snapshot.issuer();
        } else if (authentication instanceof IdpAuthenticationToken service) {
            try { return service.verifiedIssuer().toString(); }
            catch (IllegalStateException missing) { throw new AuthenticationCredentialsNotFoundException("verified issuer required"); }
        }
        throw new AuthenticationCredentialsNotFoundException("verified issuer required");
    }

    private static String requestIssuer() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet
                && servlet.getRequest().getAttribute(VerifiedProcessIssuerFilter.ATTRIBUTE) instanceof String issuer
                && !issuer.isBlank()) return issuer;
        throw new AuthenticationCredentialsNotFoundException("verified request issuer required");
    }
}
