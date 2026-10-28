package top.egon.cola.platform.tianji.process.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.process.server.error.ProcessTenantChangedException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
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

class TianjiProcessAccessTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private final PermissionExecutionGuard guard = mock(PermissionExecutionGuard.class);
    private final TianjiProcessAccess access = new TianjiProcessAccess(guard, Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void derives_the_actor_from_the_verified_identity_and_treats_tenant_headers_only_as_expectations() {
        authenticate(principal("worker", "20", "token-1", NOW.plusSeconds(60)));
        var request = new MockHttpServletRequest();
        request.setAttribute(VerifiedProcessIssuerFilter.ATTRIBUTE, "https://idp.example/tenants/20");
        request.addHeader("X-Egon-Tianji-Tenant", "20");
        request.addHeader("X-Tenant-ID", "21");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertEquals(ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker", "https://idp.example/tenants/20"),
                access.require(TianjiProcessPermissions.GET_API));
        verify(guard).requireExecution(TianjiProcessPermissions.GET_API);
        request.addHeader("X-Egon-Tianji-Tenant", "21");
        assertThrows(ProcessTenantChangedException.class, () -> access.require(TianjiProcessPermissions.GET_API));
    }

    @Test
    void rejects_expiry_or_identity_changes_during_permission_evaluation() {
        verifiedRequest();
        authenticate(principal("worker", "20", "token-1", NOW.minusSeconds(1)));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> access.require(TianjiProcessPermissions.GET_API));
        verifyNoInteractions(guard);
        authenticate(principal("worker", "20", "token-1", NOW.plusSeconds(60)));
        doAnswer(invocation -> { authenticate(principal("worker", "20", "token-2", NOW.plusSeconds(60))); return null; })
                .when(guard).requireExecution(TianjiProcessPermissions.GET_API);
        assertThrows(AccessDeniedException.class, () -> access.require(TianjiProcessPermissions.GET_API));
    }

    @Test
    void rejects_a_changed_or_missing_verified_issuer() {
        verifiedRequest();
        var principal = principal("worker", "20", "token-1", NOW.plusSeconds(60));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> access.require(TianjiProcessPermissions.GET_API));
        authenticate(principal);
        doAnswer(invocation -> {
            SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(),
                    URI.create("https://another-idp.example/tenants/20")));
            return null;
        }).when(guard).requireExecution(TianjiProcessPermissions.GET_API);
        assertThrows(AccessDeniedException.class, () -> access.require(TianjiProcessPermissions.GET_API));
    }

    @Test
    void issuer_filter_discards_prior_attributes_and_clears_verified_issuer_after_the_request() throws Exception {
        var request = new MockHttpServletRequest();
        request.setAttribute(VerifiedProcessIssuerFilter.ATTRIBUTE, "https://spoof.example");
        var filter = new VerifiedProcessIssuerFilter();
        filter.doFilter(request, new MockHttpServletResponse(), (inner, response) ->
                assertNull(inner.getAttribute(VerifiedProcessIssuerFilter.ATTRIBUTE)));
        assertNull(request.getAttribute(VerifiedProcessIssuerFilter.ATTRIBUTE));
        authenticate(principal("worker", "20", "token-1", NOW.plusSeconds(60)));
        filter.doFilter(request, new MockHttpServletResponse(), (inner, response) ->
                assertEquals("https://idp.example/tenants/20", inner.getAttribute(VerifiedProcessIssuerFilter.ATTRIBUTE)));
        assertNull(request.getAttribute(VerifiedProcessIssuerFilter.ATTRIBUTE));
        assertThrows(jakarta.servlet.ServletException.class, () -> filter.doFilter(request, new MockHttpServletResponse(),
                (inner, response) -> { throw new jakarta.servlet.ServletException("failure"); }));
        assertNull(request.getAttribute(VerifiedProcessIssuerFilter.ATTRIBUTE));
    }

    private static ServiceIdentityPrincipal principal(String subject, String tenant, String token, Instant expiry) {
        return new ServiceIdentityPrincipal(subject, tenant, subject, token, URI.create("https://api.example/tianji"), 1,
                Set.of(TianjiProcessPermissions.READ_SCOPE), "xingyuan", subject, "test", "credential", NOW.minusSeconds(60), expiry);
    }

    private static void authenticate(ServiceIdentityPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(),
                URI.create("https://idp.example/tenants/20")));
    }

    private static void verifiedRequest() {
        var request = new MockHttpServletRequest();
        request.setAttribute(VerifiedProcessIssuerFilter.ATTRIBUTE, "https://idp.example/tenants/20");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
