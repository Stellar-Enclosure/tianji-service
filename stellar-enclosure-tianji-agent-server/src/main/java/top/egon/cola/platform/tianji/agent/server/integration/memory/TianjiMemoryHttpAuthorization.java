package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;

import java.time.Clock;
import java.util.Objects;

/** Adapts the current HTTP permission guard and IAM data scope; deferred runs require their own sealed Memory authorization. */
public final class TianjiMemoryHttpAuthorization implements MemoryAuthorization {
    private final TianjiAgentAccess access;
    private final TianjiMemoryAccessProperties properties;
    private final Clock clock;

    public TianjiMemoryHttpAuthorization(TianjiAgentAccess access, TianjiMemoryAccessProperties properties, Clock clock) {
        this.access = Objects.requireNonNull(access, "access"); this.properties = Objects.requireNonNull(properties, "properties"); this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override public Decision authorize(Request request) {
        if (request == null || request.context().runAuthorization() != null || request.context().preferenceWrite() != null || !clock.instant().isBefore(request.context().deadlineAt())) return Decision.UNAVAILABLE;
        try {
            if (request.sourceRunId() != null && !request.context().actor().equals(access.require(TianjiAgentPermissions.GET_API).actor())) return Decision.DENY;
            var current = access.require(resource(request.operation())); var actor = request.context().actor();
            if (!actor.equals(current.actor()) || request.context().deadlineAt().isAfter(current.expiresAt())) return Decision.DENY;
            if (request.owner().type() == MemoryOwnerTypeEnum.USER) return actor.subjectType() == TrustedAgentActor.SubjectType.USER && request.owner().id().equals(actor.subjectId()) ? Decision.ALLOW : Decision.DENY;
            if (request.owner().type() == MemoryOwnerTypeEnum.APPLICATION) return actor.subjectType() == TrustedAgentActor.SubjectType.SERVICE && request.owner().id().equals(actor.subjectId()) ? Decision.ALLOW : Decision.DENY;
            if (actor.subjectType() != TrustedAgentActor.SubjectType.USER || !(SecurityContextHolder.getContext().getAuthentication() instanceof Rbac3AuthenticationToken authenticated)) return Decision.DENY;
            var team = properties.teams().stream().filter(value -> value.tenantId() == actor.tenantId() && value.issuer().toASCIIString().equals(current.issuer().toASCIIString())
                    && value.teamId().equals(request.owner().id()) && value.scopeId().equals(request.scopeId())).findFirst().orElse(null);
            if (team == null) return Decision.DENY;
            var snapshot = authenticated.getPrincipal().snapshot(); String permission = permission(request.operation()); var scope = snapshot.dataScopes().get(permission);
            if (scope == null) return Decision.DENY;
            if (!snapshot.tenantId().equals(scope.tenantId()) || !snapshot.rbac3UserId().equals(scope.subjectId()) || !permission.equals(scope.permissionCode())
                    || snapshot.authVersion() != scope.authVersion() || snapshot.policyVersion() != scope.policyVersion() || !snapshot.generatedAt().equals(scope.decidedAt())
                    || !snapshot.expiresAt().isAfter(clock.instant()) || snapshot.generatedAt().isAfter(clock.instant())) return Decision.UNAVAILABLE;
            if (scope.decision() == top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision.INDETERMINATE) return Decision.UNAVAILABLE;
            if (scope.decision() != top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision.ALLOW) return Decision.DENY;
            boolean allowed = scope.allInTenant() || (team.directoryType() == TianjiMemoryAccessProperties.DirectoryType.ORGANIZATION
                    ? scope.allowedOrgIds().contains(team.directoryId()) : scope.allowedDeptIds().contains(team.directoryId()));
            return allowed ? Decision.ALLOW : Decision.DENY;
        } catch (AccessDeniedException | AuthenticationException denied) { return Decision.DENY; }
        catch (RuntimeException unavailable) { return Decision.UNAVAILABLE; }
    }

    static String resource(Operation operation) {
        return switch (operation) {
            case GET -> TianjiAgentPermissions.MEMORY_GET_API;
            case RETRIEVE, SEARCH -> TianjiAgentPermissions.MEMORY_RETRIEVE_API;
            case CANDIDATES -> TianjiAgentPermissions.MEMORY_CANDIDATES_API;
            case UPSERT -> TianjiAgentPermissions.MEMORY_UPSERT_API;
            case CORRECT -> TianjiAgentPermissions.MEMORY_CORRECT_API;
            case FORGET -> TianjiAgentPermissions.MEMORY_FORGET_API;
        };
    }
    static String permission(Operation operation) {
        return switch (operation) {
            case GET, RETRIEVE, SEARCH, CANDIDATES -> TianjiAgentPermissions.MEMORY_READ;
            case UPSERT, CORRECT -> TianjiAgentPermissions.MEMORY_WRITE;
            case FORGET -> TianjiAgentPermissions.MEMORY_FORGET;
        };
    }
}
