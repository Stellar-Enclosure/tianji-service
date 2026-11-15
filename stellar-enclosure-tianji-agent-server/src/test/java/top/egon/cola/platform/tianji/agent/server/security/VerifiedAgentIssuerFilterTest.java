package top.egon.cola.platform.tianji.agent.server.security;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import top.egon.cola.platform.tianquan.shoubing.contract.AuthenticationContext;
import top.egon.cola.platform.tianquan.shoubing.contract.IdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import static org.junit.jupiter.api.Assertions.*;

class VerifiedAgentIssuerFilterTest {
    private static final String PATH = "/api/tianji/v1/agent-process-dispatches/submit";
    private static final URI ISSUER = URI.create("https://idp.example/tenants/21");
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void only_the_original_verified_service_bearer_can_be_read_during_the_request() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(service(), List.of(), ISSUER));
        var request = new MockHttpServletRequest("POST", PATH);
        request.addHeader("Authorization", "bEaReR service-proof-sentinel");
        new VerifiedAgentIssuerFilter().doFilter(request, new MockHttpServletResponse(), (incoming, response) -> {
            assertEquals(ISSUER.toASCIIString(), request.getAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE));
            Object digest = request.getAttribute(VerifiedAgentIssuerFilter.SERVICE_TOKEN_DIGEST);
            assertInstanceOf(String.class, digest); assertTrue(((String) digest).matches("[0-9a-f]{64}"));
            assertFalse(digest.toString().contains("service-proof-sentinel"));
            assertEquals("service-proof-sentinel", VerifiedAgentIssuerFilter.currentServiceToken(request));
            request.removeHeader("Authorization"); request.addHeader("Authorization", "Bearer different-proof");
            assertNull(VerifiedAgentIssuerFilter.currentServiceToken(request));
        });
        assertNull(request.getAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE));
        assertNull(request.getAttribute(VerifiedAgentIssuerFilter.SERVICE_TOKEN_DIGEST));
        assertNull(VerifiedAgentIssuerFilter.currentServiceToken(request));
    }

    @Test void missing_issuer_user_identity_wrong_path_or_duplicate_headers_cannot_create_service_evidence() throws Exception {
        var filter = new VerifiedAgentIssuerFilter();
        var noIssuer = new MockHttpServletRequest("POST", PATH); noIssuer.addHeader("Authorization", "Bearer service-proof");
        noIssuer.setAttribute(VerifiedAgentIssuerFilter.SERVICE_TOKEN_DIGEST, "f".repeat(64));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(service()));
        filter.doFilter(noIssuer, new MockHttpServletResponse(), (incoming, response) -> {
            assertNull(noIssuer.getAttribute(VerifiedAgentIssuerFilter.SERVICE_TOKEN_DIGEST));
            assertNull(VerifiedAgentIssuerFilter.currentServiceToken(noIssuer));
        });
        var user = new IdentityPrincipal("alice", "21", "42", Set.of("https://api.example/tianji"),
                NOW.minusSeconds(10), NOW.plusSeconds(300), AuthenticationContext.of("PASSWORD", NOW.minusSeconds(20)));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(user, List.of(), ISSUER));
        var userRequest = new MockHttpServletRequest("POST", PATH); userRequest.addHeader("Authorization", "Bearer user-proof");
        filter.doFilter(userRequest, new MockHttpServletResponse(), (incoming, response) ->
                assertNull(VerifiedAgentIssuerFilter.currentServiceToken(userRequest)));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(service(), List.of(), ISSUER));
        var wrongPath = new MockHttpServletRequest("POST", "/api/tianji/v1/agent-process-dispatches/close");
        wrongPath.addHeader("Authorization", "Bearer service-proof");
        filter.doFilter(wrongPath, new MockHttpServletResponse(), (incoming, response) ->
                assertNull(VerifiedAgentIssuerFilter.currentServiceToken(wrongPath)));
        var duplicate = new MockHttpServletRequest("POST", PATH);
        duplicate.addHeader("Authorization", "Bearer service-proof"); duplicate.addHeader("Authorization", "Bearer other-proof");
        filter.doFilter(duplicate, new MockHttpServletResponse(), (incoming, response) ->
                assertNull(VerifiedAgentIssuerFilter.currentServiceToken(duplicate)));
    }

    @Test void a_failed_request_clears_the_verification_attributes() {
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(service(), List.of(), ISSUER));
        var request = new MockHttpServletRequest("POST", PATH); request.addHeader("Authorization", "Bearer service-proof");
        assertThrows(jakarta.servlet.ServletException.class, () -> new VerifiedAgentIssuerFilter().doFilter(request,
                new MockHttpServletResponse(), (incoming, response) -> { throw new jakarta.servlet.ServletException("failure"); }));
        assertNull(request.getAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE));
        assertNull(request.getAttribute(VerifiedAgentIssuerFilter.SERVICE_TOKEN_DIGEST));
    }

    @Test void only_the_exact_v2_linked_submit_path_carries_verified_service_bearer_evidence() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(service(), List.of(), ISSUER));
        var filter = new VerifiedAgentIssuerFilter();
        String linked = "/api/tianji/v2/agent-process-dispatches/submit";
        var accepted = new MockHttpServletRequest("POST", "/agent" + linked);
        accepted.setContextPath("/agent");
        accepted.addHeader("Authorization", "Bearer service-proof");
        filter.doFilter(accepted, new MockHttpServletResponse(), (incoming, response) -> {
            assertEquals(ISSUER.toASCIIString(), accepted.getAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE));
            assertEquals("service-proof", VerifiedAgentIssuerFilter.currentServiceToken(accepted));
        });
        assertNull(VerifiedAgentIssuerFilter.currentServiceToken(accepted));

        for (String path : List.of(linked + "/", linked + "/nested", linked + ";candidate=1", "/api/tianji/v2/agent-definitions")) {
            var rejected = new MockHttpServletRequest("POST", path);
            rejected.addHeader("Authorization", "Bearer service-proof");
            filter.doFilter(rejected, new MockHttpServletResponse(), (incoming, response) ->
                    assertNull(VerifiedAgentIssuerFilter.currentServiceToken(rejected)));
        }
    }

    @Test void v2_paths_and_exact_service_scopes_agree_with_the_identity_policy() {
        var configuration = new TianjiAgentSecurityConfiguration();
        var policy = configuration.tianjiEndpointAuthenticationPolicy();
        for (String path : List.of("/api/tianji/v2/agent-definitions", "/api/tianji/v2/agent-process-dispatches/submit", PATH)) {
            assertEquals(top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy.Requirement.SERVICE,
                    policy.requirement(new MockHttpServletRequest("POST", path)));
        }
        var linked = new IdpAuthenticationToken(service(), List.of(), ISSUER);
        assertTrue(TianjiAgentSecurityConfiguration.serviceScope(linked, TianjiAgentPermissions.LINKED_SUBMIT_SCOPE));
        assertFalse(TianjiAgentSecurityConfiguration.serviceScope(linked, TianjiAgentPermissions.TEMPLATE_PUBLISH_SCOPE));
        assertFalse(TianjiAgentSecurityConfiguration.serviceScope(new IdpAuthenticationToken(service()), TianjiAgentPermissions.LINKED_SUBMIT_SCOPE));
        var user = new IdentityPrincipal("alice", "21", "42", Set.of("https://api.example/tianji"),
                NOW.minusSeconds(10), NOW.plusSeconds(300), AuthenticationContext.of("PASSWORD", NOW.minusSeconds(20)));
        assertFalse(TianjiAgentSecurityConfiguration.serviceScope(new IdpAuthenticationToken(user, List.of(), ISSUER),
                TianjiAgentPermissions.TEMPLATE_PUBLISH_SCOPE));
    }

    private static ServiceIdentityPrincipal service() {
        return new ServiceIdentityPrincipal("worker", "21", "worker", "42", URI.create("https://api.example/tianji"), 1,
                Set.of("tianji:agent:linked:submit"), "platform", "process", "test", "credential",
                NOW.minusSeconds(10), NOW.plusSeconds(300), "process-app", ServiceTokenContext.TENANT);
    }
}
