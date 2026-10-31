package top.egon.cola.platform.tianji.process.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.RuntimePolicyBinding;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TianjiHumanTaskAccessTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final String ISSUER = "https://idp.example/20", GET = "tianji.api.process.task.get", COMPLETE = "tianji.api.process.task.complete";
    private static final IdentityPrincipal USER = new IdentityPrincipal("alice", "20", "token-id", Set.of("https://process.example"), NOW.minusSeconds(60), NOW.plusSeconds(60), AuthenticationContext.of("PASSWORD", NOW.minusSeconds(60)));
    private final PermissionExecutionGuard guard = mock(PermissionExecutionGuard.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final TianjiHumanTaskAccess tasks = new TianjiHumanTaskAccess(new TianjiProcessAccess(guard, clock), clock);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); TransactionSynchronizationManager.setActualTransactionActive(false); }

    @Test void membership_is_captured_after_the_actual_resource_guard_refreshes_the_context_and_is_bounded_by_snapshot_expiry() {
        request(); authenticate(List.of("50"), NOW.plusSeconds(20), "tianji");
        doAnswer(call -> { authenticate(List.of("60", "61"), NOW.plusSeconds(10), "tianji"); return null; }).when(guard).requireExecution(GET);
        var context = tasks.require(GET); assertEquals(ProcessActor.verified(20, ProcessActor.SubjectType.USER, "alice", ISSUER), context.actor());
        assertEquals(Set.of("60", "61"), context.roleIds()); assertEquals(Set.of("role:60", "role:61"), context.candidateGroups()); assertEquals(NOW.plusSeconds(10), context.deadlineAt());
        assertEquals(2, context.authVersion()); assertEquals(3, context.policyVersion()); verify(guard).requireExecution(GET);
        assertThrows(UnsupportedOperationException.class, () -> context.roleIds().clear()); assertThrows(IllegalStateException.class, () -> context.requireResource(COMPLETE));
    }

    @Test void stale_foreign_application_or_unbound_role_evidence_never_becomes_a_task_context() {
        request(); authenticate(List.of("50"), NOW.minusSeconds(1), "tianji"); assertThrows(IllegalStateException.class, () -> tasks.require(GET));
        authenticate(List.of("50"), NOW.plusSeconds(20), "other"); assertThrows(IllegalStateException.class, () -> tasks.require(GET));
        authenticate(List.of("50"), NOW.plusSeconds(20), "tianji", false); assertThrows(IllegalStateException.class, () -> tasks.require(GET));
        assertThrows(IllegalArgumentException.class, () -> authenticate(List.of("role-name"), NOW.plusSeconds(20), "tianji"));
        authenticate(List.of("50"), NOW.plusSeconds(20), "tianji"); doThrow(new AccessDeniedException("fixture denied")).when(guard).requireExecution(GET);
        assertThrows(AccessDeniedException.class, () -> tasks.require(GET));
    }

    @Test void service_identity_and_ambient_transactions_cannot_borrow_user_task_membership() {
        request(); var service = new ServiceIdentityPrincipal("worker", "20", "worker", "token", URI.create("https://process.example"), 1, Set.of("tianji:task:read"), "xingyuan", "worker", "test", "credential", NOW.minusSeconds(1), NOW.plusSeconds(60));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(service, List.of(), URI.create(ISSUER))); assertThrows(AccessDeniedException.class, () -> tasks.require(GET));
        clearInvocations(guard); TransactionSynchronizationManager.setActualTransactionActive(true); assertThrows(IllegalStateException.class, () -> tasks.require(GET)); verifyNoInteractions(guard);
    }

    @Test void native_user_keys_bind_namespace_and_ignore_role_token_or_operation_changes() {
        var first = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "alice", ISSUER); var roles = new HashSet<>(Set.of("50"));
        var context = new HumanTaskContext(first, GET, roles, NOW.plusSeconds(10), 2, 3); roles.add("60"); assertEquals(Set.of("50"), context.roleIds());
        assertEquals(context.userKey(), new HumanTaskContext(first, COMPLETE, Set.of("60"), NOW.plusSeconds(20), 4, 5).userKey()); assertEquals(69, context.userKey().length());
        assertNotEquals(context.userKey(), new HumanTaskContext(ProcessActor.verified(20, ProcessActor.SubjectType.USER, "alice", "https://other-idp.example/20"), GET, Set.of("50"), NOW, 2, 3).userKey());
        assertNotEquals(context.userKey(), new HumanTaskContext(ProcessActor.verified(21, ProcessActor.SubjectType.USER, "alice", ISSUER), GET, Set.of("50"), NOW, 2, 3).userKey());
        assertThrows(IllegalArgumentException.class, () -> new HumanTaskContext(new ProcessActor(20, ProcessActor.SubjectType.USER, "alice"), GET, Set.of("50"), NOW, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new HumanTaskContext(first, GET, Set.of("9223372036854775808"), NOW, 2, 3));
        assertFalse(context.toString().contains("alice"));
    }

    private static void request() { var request = new MockHttpServletRequest(); request.setAttribute(VerifiedProcessIssuerFilter.ATTRIBUTE, ISSUER); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request)); }
    private static void authenticate(List<String> roles, Instant expiry, String application) {
        authenticate(roles, expiry, application, true);
    }
    private static void authenticate(List<String> roles, Instant expiry, String application, boolean bound) {
        var scope = new CatalogScope("10", "20", "xingyuan", application, "default", "test");
        var view = new EffectivePermissionSnapshot(ISSUER, "alice", scope, "1", "2", "3", new EffectivePermissionSnapshot.ActiveRoleContext(application, roles, roles), List.of("TASK_PERMISSION"), expiry);
        var binding = new RuntimePolicyBinding("tianji-java", "1", "a".repeat(64), "b".repeat(64), "c".repeat(64));
        var snapshot = new SystemAuthorizationSnapshot("20", "alice", "60", application, 2, 3, roles, null, null, Set.of("TASK_PERMISSION"), Set.of(GET, COMPLETE), Map.of(), Map.of(), "checksum", NOW.minusSeconds(30), expiry, view, bound ? binding : null);
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(new Rbac3UserDetails(USER, snapshot), List.of()));
    }
}
