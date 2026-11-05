package top.egon.cola.platform.tianji.agent.server.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.application.tool.HistoricalToolReceiptAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolStartReceiptReconciliationService.ReceiptVO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionContext;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.UserPermissionScopePolicy;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static top.egon.cola.platform.tianji.agent.server.security.TianjiHistoricalStartReceiptPermissions.PATH;
import static top.egon.cola.platform.tianji.agent.server.security.TianjiHistoricalStartReceiptPermissions.RESOURCE;
import static top.egon.cola.platform.tianji.agent.server.security.TianjiHistoricalStartReceiptPermissions.SCOPE;

/** Binds current Jianshen decisions to one verified servlet USER, Run, intent and execution thread. */
public final class TianjiServletHistoricalStartReceiptAuthority implements HistoricalToolReceiptAuthorization {
    private final TianjiAgentAccess access;
    private final Clock clock;
    private final ThreadLocal<InvocationVO> current = new ThreadLocal<>();

    public TianjiServletHistoricalStartReceiptAuthority(TianjiAgentAccess access, Clock clock) {
        this.access = Objects.requireNonNull(access, "access");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The only caller is the protected servlet command. The scope is removed for every outcome. */
    public ReceiptVO reconcile(String runId, UUID intentId, Function<TrustedAgentActor, ReceiptVO> command) {
        requireOutsideTransaction();
        HttpServletRequest servlet = request();
        if (servlet == null || current.get() != null || !UserPermissionScopePolicy.currentScopes().contains(SCOPE)) throw denied();
        var owner = access.require(RESOURCE);
        var token = currentUser();
        if (token == null || owner.actor().subjectType() != TrustedAgentActor.SubjectType.USER) throw denied();

        current.set(new InvocationVO(servlet, owner.actor(), token.getPrincipal().identity().tokenId(), runId, intentId));
        try {
            return command.apply(owner.actor());
        } finally {
            current.remove();
        }
    }

    @Override
    public Optional<Permit> authorize(Request request) {
        requireOutsideTransaction();
        var invocation = current.get();
        if (request == null || invocation == null || !invocation.accepts(request)
                || invocation.servlet() != request() || !sameUser(invocation)) return Optional.empty();
        if (!request.deadlineAt().isAfter(clock.instant())) throw unavailable();

        TianjiAgentAccess.Authorized fresh;
        try {
            fresh = access.require(RESOURCE);
        } catch (AccessDeniedException | AuthenticationException refused) {
            return Optional.empty();
        }
        if (!invocation.owner().equals(fresh.actor()) || !sameUser(invocation) || invocation.servlet() != request()) {
            return Optional.empty();
        }
        var snapshot = currentUser().getPrincipal().snapshot();
        Instant now = clock.instant();
        Instant expires = earliest(request.deadlineAt(), earliest(fresh.expiresAt(), snapshot.expiresAt()));
        if (!expires.isAfter(now) || snapshot.policyVersion() < 1) throw unavailable();
        return Optional.of(new Permit(UUID.randomUUID(), request.bindingSha256(), snapshot.policyVersion(), now, expires));
    }

    private static boolean sameUser(InvocationVO invocation) {
        var token = currentUser();
        return token != null && invocation.tokenId().equals(token.getPrincipal().identity().tokenId())
                && UserPermissionScopePolicy.currentScopes().contains(SCOPE);
    }

    private static Rbac3AuthenticationToken currentUser() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof Rbac3AuthenticationToken token
                && token.isAuthenticated() ? token : null;
    }

    private static HttpServletRequest request() {
        if (RESOURCE.equals(PermissionExecutionContext.currentApiKeyOrNull())
                && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet) {
            var request = servlet.getRequest();
            if ("POST".equals(request.getMethod()) && request.getQueryString() == null
                    && (request.getContextPath() + PATH).equals(request.getRequestURI())) return request;
        }
        return null;
    }

    private static void requireOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) throw unavailable();
    }

    private static Instant earliest(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static AgentLedgerException denied() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Historical START receipt command was denied");
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Historical START receipt authority is unavailable");
    }

    private record InvocationVO(HttpServletRequest servlet, TrustedAgentActor owner, String tokenId, String runId, UUID intentId) {
        boolean accepts(Request request) {
            return owner.equals(request.owner()) && runId.equals(request.dispatchRunId()) && intentId.equals(request.intentId());
        }
        @Override public String toString() { return "HistoricalStartReceiptInvocation[redacted]"; }
    }
}
