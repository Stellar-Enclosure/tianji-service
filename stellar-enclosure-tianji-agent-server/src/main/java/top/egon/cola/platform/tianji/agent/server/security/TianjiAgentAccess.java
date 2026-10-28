package top.egon.cola.platform.tianji.agent.server.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.contract.IdpPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Collections;

/** Obtains tenant and actor only from the authenticated identity after the resource policy allows access. */
public final class TianjiAgentAccess {
    private final PermissionExecutionGuard permissions;
    private final Clock clock;

    public TianjiAgentAccess(PermissionExecutionGuard permissions, Clock clock) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Authorized require(String resourceKey) {
        Authentication initial = SecurityContextHolder.getContext().getAuthentication();
        IdpPrincipal original = identity(initial);
        if (!original.expiresAt().isAfter(clock.instant())) {
            throw new AuthenticationCredentialsNotFoundException("identity expired");
        }
        permissions.requireExecution(resourceKey);
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        IdpPrincipal verified = identity(current);
        if (!original.subject().equals(verified.subject()) || !Objects.equals(original.tenantId(), verified.tenantId())
                || original.principalType() != verified.principalType() || !original.tokenId().equals(verified.tokenId())
                || !original.expiresAt().equals(verified.expiresAt())) {
            throw new AccessDeniedException("identity changed during authorization");
        }
        if (!verified.expiresAt().isAfter(clock.instant())) {
            throw new AuthenticationCredentialsNotFoundException("identity expired");
        }
        String tenantId = verified.tenantId();
        if (tenantId == null || !tenantId.matches("[1-9][0-9]{0,18}")) {
            throw new AccessDeniedException("tenant-scoped identity required");
        }
        long tenant;
        try { tenant = Long.parseLong(tenantId); }
        catch (NumberFormatException invalid) { throw new AccessDeniedException("tenant-scoped identity required"); }
        if (tenant <= 0) throw new AccessDeniedException("tenant-scoped identity required");
        if (verified.subject().length() > 256) throw new AccessDeniedException("identity subject is invalid");
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet) {
            var expected = Collections.list(servlet.getRequest().getHeaders("X-Egon-Tianji-Tenant"));
            if (expected.size() > 1 || (!expected.isEmpty() && !tenantId.equals(expected.getFirst()))) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_TENANT_CONTEXT_CHANGED,
                        "The expected tenant does not match the verified identity");
            }
        }
        TrustedAgentActor.SubjectType type = verified instanceof ServiceIdentityPrincipal
                ? TrustedAgentActor.SubjectType.SERVICE : TrustedAgentActor.SubjectType.USER;
        return new Authorized(new TrustedAgentActor(tenant, type, verified.subject()), verified.expiresAt());
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

    public record Authorized(TrustedAgentActor actor, Instant expiresAt) { }
}
