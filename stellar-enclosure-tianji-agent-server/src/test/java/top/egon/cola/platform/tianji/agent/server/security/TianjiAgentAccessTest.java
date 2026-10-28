package top.egon.cola.platform.tianji.agent.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.shoubing.contract.AuthenticationContext;
import top.egon.cola.platform.tianquan.shoubing.contract.IdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TianjiAgentAccessTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private final PermissionExecutionGuard guard = mock(PermissionExecutionGuard.class);
    private final TianjiAgentAccess access = new TianjiAgentAccess(guard, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void request() {
        var request = new MockHttpServletRequest();
        request.setAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE, "https://idp.example/issuer");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }

    @Test
    void derives_user_and_service_actor_from_verified_identity_after_permission_check() {
        var user = user("20", "alice", NOW.plusSeconds(60));
        SecurityContextHolder.getContext().setAuthentication(user);
        assertEquals(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/issuer"),
                access.require(TianjiAgentPermissions.GET_API).actor());
        verify(guard).requireExecution(TianjiAgentPermissions.GET_API);

        var service = service("21", NOW.plusSeconds(60));
        SecurityContextHolder.getContext().setAuthentication(service);
        assertEquals(TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://idp.example/issuer"),
                access.require(TianjiAgentPermissions.START_API).actor());
        verify(guard).requireExecution(TianjiAgentPermissions.START_API);
    }

    @Test
    void rejects_unverified_expired_and_unscoped_identities() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> access.require(TianjiAgentPermissions.GET_API));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", "ignored"));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> access.require(TianjiAgentPermissions.GET_API));
        SecurityContextHolder.getContext().setAuthentication(user("20", "alice", NOW));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> access.require(TianjiAgentPermissions.GET_API));
        SecurityContextHolder.getContext().setAuthentication(service(null, NOW.plusSeconds(60)));
        assertThrows(AccessDeniedException.class, () -> access.require(TianjiAgentPermissions.GET_API));
        SecurityContextHolder.getContext().setAuthentication(user("not-a-number", "alice", NOW.plusSeconds(60)));
        assertThrows(AccessDeniedException.class, () -> access.require(TianjiAgentPermissions.GET_API));
        SecurityContextHolder.getContext().setAuthentication(user("020", "alice", NOW.plusSeconds(60)));
        assertThrows(AccessDeniedException.class, () -> access.require(TianjiAgentPermissions.GET_API));
        SecurityContextHolder.getContext().setAuthentication(user("+20", "alice", NOW.plusSeconds(60)));
        assertThrows(AccessDeniedException.class, () -> access.require(TianjiAgentPermissions.GET_API));
    }

    @Test
    void denied_or_changed_identity_does_not_become_a_trusted_actor() {
        SecurityContextHolder.getContext().setAuthentication(user("20", "alice", NOW.plusSeconds(60)));
        doThrow(new AccessDeniedException("denied")).when(guard).requireExecution(TianjiAgentPermissions.GET_API);
        assertThrows(AccessDeniedException.class, () -> access.require(TianjiAgentPermissions.GET_API));
        reset(guard);
        doAnswer(invocation -> {
            SecurityContextHolder.getContext().setAuthentication(user("21", "alice", NOW.plusSeconds(60)));
            return null;
        }).when(guard).requireExecution(TianjiAgentPermissions.GET_API);
        assertThrows(AccessDeniedException.class, () -> access.require(TianjiAgentPermissions.GET_API));
        reset(guard);
        var initial = service("20", NOW.plusSeconds(60));
        SecurityContextHolder.getContext().setAuthentication(initial);
        doAnswer(invocation -> {
            SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(initial.getPrincipal(), List.of(),
                    URI.create("https://another-idp.example/issuer")));
            return null;
        }).when(guard).requireExecution(TianjiAgentPermissions.GET_API);
        assertThrows(AccessDeniedException.class, () -> access.require(TianjiAgentPermissions.GET_API));
        reset(guard);
        SecurityContextHolder.getContext().setAuthentication(user("20", "alice", NOW.plusSeconds(60)));
        doAnswer(invocation -> {
            SecurityContextHolder.getContext().setAuthentication(user("20", "alice", NOW.plusSeconds(120)));
            return null;
        }).when(guard).requireExecution(TianjiAgentPermissions.GET_API);
        assertThrows(AccessDeniedException.class, () -> access.require(TianjiAgentPermissions.GET_API));
    }

    @Test
    void issuer_filter_removes_spoofed_values_and_cleans_up_on_failure() throws Exception {
        var request = new MockHttpServletRequest();
        request.setAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE, "https://spoof.example");
        var filter = new VerifiedAgentIssuerFilter();
        filter.doFilter(request, new MockHttpServletResponse(), (inner, response) ->
                assertNull(inner.getAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE)));
        SecurityContextHolder.getContext().setAuthentication(service("20", NOW.plusSeconds(60)));
        assertThrows(jakarta.servlet.ServletException.class, () -> filter.doFilter(request, new MockHttpServletResponse(), (inner, response) -> {
            assertEquals("https://idp.example/issuer", inner.getAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE));
            throw new jakarta.servlet.ServletException("failure");
        }));
        assertNull(request.getAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE));
    }

    private static Rbac3AuthenticationToken user(String tenant, String subject, Instant expires) {
        var details = mock(Rbac3UserDetails.class);
        var token = mock(Rbac3AuthenticationToken.class);
        IdentityPrincipal identity = new IdentityPrincipal(subject, tenant, "token-id", Set.of("tianji"),
                NOW.minusSeconds(60), expires, AuthenticationContext.of("PASSWORD", NOW.minusSeconds(60)));
        when(token.isAuthenticated()).thenReturn(true);
        when(token.getPrincipal()).thenReturn(details);
        when(details.identity()).thenReturn(identity);
        var snapshot = mock(SystemAuthorizationSnapshot.class);
        var view = mock(EffectivePermissionSnapshot.class);
        when(view.issuer()).thenReturn("https://idp.example/issuer");
        when(snapshot.permissionSnapshot()).thenReturn(view);
        when(details.snapshot()).thenReturn(snapshot);
        return token;
    }

    private static IdpAuthenticationToken service(String tenant, Instant expires) {
        var identity = new ServiceIdentityPrincipal("worker", tenant, "worker", "token-id",
                URI.create("https://api.example/tianji"), 1, Set.of("tianji:agent:read"),
                "domain", "worker", "test", "credential", NOW.minusSeconds(60), expires);
        return new IdpAuthenticationToken(identity, List.of(), URI.create("https://idp.example/issuer"));
    }
}
