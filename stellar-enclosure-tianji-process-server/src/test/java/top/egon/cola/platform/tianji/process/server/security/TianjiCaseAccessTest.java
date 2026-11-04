package top.egon.cola.platform.tianji.process.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.RuntimePolicyBinding;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;
import top.egon.cola.platform.tianquan.shoubing.contract.AuthenticationContext;
import top.egon.cola.platform.tianquan.shoubing.contract.IdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TianjiCaseAccessTest {
    private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
    private static final String ISSUER = "https://idp.example/20", GET = "tianji.api.process.case.get", START = "tianji.api.process.case.start", ACT = "tianji.api.process.case.actPlanItem";
    private static final IdentityPrincipal USER = new IdentityPrincipal("alice", "20", "token-id", Set.of("https://process.example"), NOW.minusSeconds(60), NOW.plusSeconds(60), AuthenticationContext.of("PASSWORD", NOW.minusSeconds(60)));
    private final PermissionExecutionGuard guard = mock(PermissionExecutionGuard.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final TianjiCaseAccess access = new TianjiCaseAccess(new TianjiProcessAccess(guard, clock), clock);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); TransactionSynchronizationManager.setActualTransactionActive(false); }

    @Test void current_roles_are_captured_after_real_guard_refresh_and_deadline_is_bounded() {
        request(); authenticate(List.of("7"), NOW.plusSeconds(20), "tianji", true);
        doAnswer(call -> { authenticate(List.of("8", "9"), NOW.plusSeconds(10), "tianji", true); return null; }).when(guard).requireExecution(GET);
        var context = assertDoesNotThrow(() -> access.require(GET)); assertNotNull(context);
        assertEquals(ProcessActor.verified(20, ProcessActor.SubjectType.USER, "alice", ISSUER), context.actor());
        assertEquals(Set.of("8", "9"), context.roleIds()); assertEquals(NOW.plusSeconds(10), context.deadlineAt()); assertEquals(2, context.authVersion()); assertEquals(3, context.policyVersion());
        verify(guard).requireExecution(GET); assertThrows(UnsupportedOperationException.class, () -> context.roleIds().clear());
    }

    @Test void stale_foreign_or_unbound_user_role_evidence_cannot_become_case_authority() {
        request(); authenticate(List.of("7"), NOW.minusSeconds(1), "tianji", true); assertThrows(IllegalStateException.class, () -> access.require(GET));
        authenticate(List.of("7"), NOW.plusSeconds(20), "other", true); assertThrows(IllegalStateException.class, () -> access.require(GET));
        authenticate(List.of("7"), NOW.plusSeconds(20), "tianji", false); assertThrows(IllegalStateException.class, () -> access.require(GET));
        authenticate(List.of("7"), NOW.plusSeconds(20), "tianji", true); doThrow(new AccessDeniedException("private")).when(guard).requireExecution(GET);
        assertThrows(AccessDeniedException.class, () -> access.require(GET));
    }

    @Test void service_start_and_get_use_current_identity_expiry_without_user_membership() {
        request(); service(NOW.plusSeconds(5));
        for (String resource : List.of(START, GET)) {
            var context = assertDoesNotThrow(() -> access.require(resource)); assertNotNull(context);
            assertEquals(ProcessActor.SubjectType.SERVICE, context.actor().subjectType()); assertEquals("worker", context.actor().subjectId());
            assertEquals(Set.of(), context.roleIds()); assertEquals(NOW.plusSeconds(5), context.deadlineAt()); assertEquals(0, context.authVersion()); assertEquals(0, context.policyVersion());
        }
        assertThrows(AccessDeniedException.class, () -> access.require(ACT));
    }

    @Test void ambient_transactions_and_actor_swaps_never_authorize_case_work() {
        request(); authenticate(List.of("7"), NOW.plusSeconds(20), "tianji", true);
        TransactionSynchronizationManager.setActualTransactionActive(true); assertThrows(IllegalStateException.class, () -> access.require(GET)); verifyNoInteractions(guard);
        TransactionSynchronizationManager.setActualTransactionActive(false);
        doAnswer(call -> { service(NOW.plusSeconds(20)); return null; }).when(guard).requireExecution(GET);
        assertThrows(AccessDeniedException.class, () -> access.require(GET));
    }
    private static void request() { var request = new MockHttpServletRequest(); request.setAttribute(VerifiedProcessIssuerFilter.ATTRIBUTE, ISSUER); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request)); }
    private static void service(Instant expiry) {
        var service = new ServiceIdentityPrincipal("worker", "20", "worker", "token", URI.create("https://process.example"), 1, Set.of("tianji:case:start", "tianji:case:read"), "xingyuan", "worker", "test", "credential", NOW.minusSeconds(1), expiry);
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(service, List.of(), URI.create(ISSUER)));
    }
    private static void authenticate(List<String> roles, Instant expiry, String application, boolean bound) {
        var scope = new CatalogScope("10", "20", "xingyuan", application, "default", "test");
        var view = new EffectivePermissionSnapshot(ISSUER, "alice", scope, "1", "2", "3", new EffectivePermissionSnapshot.ActiveRoleContext(application, roles, roles), List.of("CASE_PERMISSION"), expiry);
        var binding = new RuntimePolicyBinding("tianji-java", "1", "a".repeat(64), "b".repeat(64), "c".repeat(64));
        var snapshot = new SystemAuthorizationSnapshot("20", "alice", "60", application, 2, 3, roles, null, null, Set.of("CASE_PERMISSION"), Set.of(GET, START, ACT), Map.of(), Map.of(), "checksum", NOW.minusSeconds(30), expiry, view, bound ? binding : null);
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(new Rbac3UserDetails(USER, snapshot), List.of()));
    }
}
