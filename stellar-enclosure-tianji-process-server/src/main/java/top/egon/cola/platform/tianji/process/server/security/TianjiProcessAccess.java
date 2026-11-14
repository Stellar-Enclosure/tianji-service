package top.egon.cola.platform.tianji.process.server.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.process.server.error.ProcessTenantChangedException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.AutomationObservationPermitVO;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.UserPermissionScopePolicy;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.contract.IdpPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.time.Clock;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.AUTOMATION_INSTANCE_GET_API;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.AUTOMATION_NODE_EXECUTION_LIST_API;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.DATA_READ;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.DATA_READ_SCOPE;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.DIAGNOSTIC_READ;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.DIAGNOSTIC_READ_SCOPE;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.OBSERVE;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.OBSERVE_SCOPE;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.READ;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.READ_SCOPE;

/** Tenant and subject come exclusively from the verified identity after the resource policy allows access. */
public final class TianjiProcessAccess {
    private final PermissionExecutionGuard permissions;
    private final Clock clock;

    public TianjiProcessAccess(PermissionExecutionGuard permissions, Clock clock) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AutomationInstanceAccessVO observeAutomationInstance() {
        return observeAutomation(AUTOMATION_INSTANCE_GET_API);
    }

    public AutomationInstanceAccessVO observeAutomationNodes() {
        return observeAutomation(AUTOMATION_NODE_EXECUTION_LIST_API);
    }

    private AutomationInstanceAccessVO observeAutomation(String resourceKey) {
        var actor = require(resourceKey);
        var current = SecurityContextHolder.getContext().getAuthentication();
        if (current instanceof Rbac3AuthenticationToken user) {
            var snapshot = user.getPrincipal().snapshot();
            if (!snapshot.expiresAt().isAfter(clock.instant())
                    || !snapshot.resourceCodes().contains(resourceKey)) {
                throw new IllegalStateException("Current instance authorization is unavailable");
            }
            Set<String> scopes = UserPermissionScopePolicy.currentScopes();
            Set<String> granted = snapshot.permissions();
            boolean ownerRead = grants(granted, scopes, READ, READ_SCOPE);
            boolean observe = grants(granted, scopes, OBSERVE, OBSERVE_SCOPE);
            requireReadOrObserve(ownerRead, observe);

            var data = snapshot.dataScopes().get(DATA_READ);
            boolean currentData = grants(granted, scopes, DATA_READ, DATA_READ_SCOPE) && data != null
                    && data.decision() == Decision.ALLOW && DATA_READ.equals(data.permissionCode())
                    && snapshot.tenantId().equals(data.tenantId()) && snapshot.rbac3UserId().equals(data.subjectId())
                    && snapshot.authVersion() == data.authVersion() && snapshot.policyVersion() == data.policyVersion()
                    && !data.decidedAt().isAfter(clock.instant());
            boolean allInTenant = currentData && data.allInTenant();
            boolean ownerData = currentData && data.includeSelf() && snapshot.rbac3UserId().equals(data.selfUserId());
            boolean diagnostic = grants(granted, scopes, DIAGNOSTIC_READ, DIAGNOSTIC_READ_SCOPE);
            return new AutomationInstanceAccessVO(actor, new AutomationObservationPermitVO(
                    ownerRead, observe, allInTenant, diagnostic, false, ownerData));
        }
        if (current instanceof IdpAuthenticationToken token && token.getPrincipal() instanceof ServiceIdentityPrincipal service) {
            boolean ownerRead = service.scopes().contains(READ_SCOPE);
            boolean observe = service.scopes().contains(OBSERVE_SCOPE);
            requireReadOrObserve(ownerRead, observe);
            return new AutomationInstanceAccessVO(actor, new AutomationObservationPermitVO(ownerRead, observe, false, false, false));
        }
        throw new AuthenticationCredentialsNotFoundException("Current instance identity is required");
    }

    public record AutomationInstanceAccessVO(ProcessActor actor, AutomationObservationPermitVO permit) { }

    private static boolean grants(Set<String> permissions, Set<String> scopes, String permission, String scope) {
        return permissions.contains(permission) && scopes.contains(scope);
    }

    private static void requireReadOrObserve(boolean ownerRead, boolean observe) {
        if (!ownerRead && !observe) throw new AccessDeniedException("Current instance read or observe permission is required");
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
