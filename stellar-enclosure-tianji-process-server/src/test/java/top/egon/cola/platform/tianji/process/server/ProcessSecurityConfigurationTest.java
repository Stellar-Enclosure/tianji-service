package top.egon.cola.platform.tianji.process.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ProcessDraftPageDTO;
import top.egon.cola.platform.tianji.process.server.api.ProcessApiErrors;
import top.egon.cola.platform.tianji.process.server.api.ProcessDraftController;
import top.egon.cola.platform.tianji.process.server.api.ProcessRequestBodyAdvice;
import top.egon.cola.platform.tianji.process.server.decision.ProcessDecisionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftConverter;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.jianshen.starter.cache.AuthorizationSnapshotLoader;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient.AuthorizationUnavailableException;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.contract.AuthenticationContext;
import top.egon.cola.platform.tianquan.shoubing.contract.IdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpJwtVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.UserAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedTenantContextFilter;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ProcessSecurityConfigurationTest.Probe.class, properties = "spring.main.web-application-type=servlet",
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({ProcessSecurityConfiguration.class, ProcessSecurityConfigurationTest.Probe.class})
class ProcessSecurityConfigurationTest {
    @Test
    void classifies_exact_start_receipt_get_as_mixed_principal() {
        var endpoint = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/tianji/v1/process-start-receipts");
        assertEquals(top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy.Requirement.USER_OR_SERVICE,
                new ProcessSecurityConfiguration().processEndpointAuthenticationPolicy().requirement(endpoint));
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void allows_health_but_denies_other_paths() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/process-api")).andExpect(status().isForbidden());
    }

    @Test
    void default_off_security_denies_all_three_case_posts() throws Exception {
        for (String operation : java.util.List.of("start", "get", "act-plan-item"))
            mvc.perform(post("/api/tianji/v1/cases/" + operation).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
    }

    @Test
    void draft_paths_explicitly_allow_verified_user_and_service_tokens() {
        var policy = new ProcessSecurityConfiguration().processEndpointAuthenticationPolicy();
        for (DraftRouteVO route : draftRoutes()) {
            assertEquals(IdpEndpointAuthenticationPolicy.Requirement.USER_OR_SERVICE,
                    policy.requirement(new MockHttpServletRequest(route.method(), route.path())), route.path());
        }
        assertEquals(IdpEndpointAuthenticationPolicy.Requirement.USER,
                policy.requirement(new MockHttpServletRequest("POST", "/api/tianji/v2/process-drafts/d1/missing")));
    }

    @Test
    void draft_routes_normalize_iam_short_circuits_to_the_closed_process_errors() throws Exception {
        var filter = new ProcessSecurityConfiguration.ClosedProcessSecurityErrors(false, false, false);
        for (DraftRouteVO route : draftRoutes()) {
            var request = new MockHttpServletRequest(route.method(), route.path());
            for (int status : new int[]{401, 403, 503}) {
                var response = new MockHttpServletResponse();
                filter.doFilter(request, response, (incoming, outgoing) -> {
                    ((HttpServletResponse) outgoing).setStatus(status);
                    outgoing.getWriter().write("{\"code\":\"UPSTREAM\",\"message\":\"private\"}");
                });
                String code = switch (status) {
                    case 401 -> "TIANJI_UNAUTHENTICATED";
                    case 403 -> "TIANJI_PERMISSION_DENIED";
                    default -> "TIANJI_DEPENDENCY_UNAVAILABLE";
                };
                assertEquals(status, response.getStatus(), route.path());
                assertEquals("no-store", response.getHeader("Cache-Control"), route.path());
                assertEquals("{\"code\":\"" + code + "\",\"correlationId\":null}",
                        response.getContentAsString(), route.path());
            }
        }
    }

    @Test
    void draft_converter_is_discovered_under_the_explicit_generated_bean_name() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var scanner = new ClassPathBeanDefinitionScanner(context, false);
            scanner.addIncludeFilter((reader, factory) -> reader.getAnnotationMetadata().getAnnotationTypes()
                    .contains(Component.class.getName())
                    && reader.getClassMetadata().getInterfaceNames().length == 1
                    && ProcessDraftConverter.class.getName().equals(reader.getClassMetadata().getInterfaceNames()[0]));
            scanner.scan(ProcessDraftConverter.class.getPackageName());
            context.refresh();
            Object bean = context.getBean("processDraftConverter");
            assertInstanceOf(ProcessDraftConverter.class, bean);
            assertEquals("processDraftConverter", bean.getClass().getAnnotation(Component.class).value());
        }
    }

    @Test
    void draft_filter_chain_accepts_both_verified_principals_and_rejects_unapproved_methods() {
        var issuer = URI.create("https://idp.example/tenants/20");
        var audience = URI.create("https://api.example/tianji");
        Instant now = Instant.now();
        var scopes = Set.of(TianjiProcessPermissions.RELEASE_PUBLISH_SCOPE);
        var user = new IdentityPrincipal("editor", "20", "user-at", Set.of(audience.toString()), now.minusSeconds(60),
                now.plusSeconds(300), AuthenticationContext.of("PASSWORD", now.minusSeconds(60)));
        var service = new ServiceIdentityPrincipal("editor", "20", "editor", "service-at", audience, 1, scopes,
                "xingyuan", "tianji", "test", "credential", now.minusSeconds(60), now.plusSeconds(300));
        var verifier = mock(IdpJwtVerifier.class);
        when(verifier.verifyAny("draft-user")).thenReturn(new AccessTokenVerification.Valid<>(user, scopes, null, issuer));
        when(verifier.verifyAny("draft-service")).thenReturn(new AccessTokenVerification.Valid<>(service, scopes, null, issuer));
        when(verifier.verifyAny("draft-invalid")).thenReturn(new AccessTokenVerification.Invalid<>("untrusted details"));

        var snapshotLoader = mock(AuthorizationSnapshotLoader.class);
        var permissions = List.of(TianjiProcessPermissions.RELEASE_PUBLISH);
        var view = new EffectivePermissionSnapshot(issuer.toString(), user.subject(),
                new CatalogScope("10", "20", "xingyuan", "tianji", "default", "test"), "1", "2", "3",
                new EffectivePermissionSnapshot.ActiveRoleContext("tianji", List.of(), List.of()), permissions, now.plusSeconds(30));
        var snapshot = new SystemAuthorizationSnapshot("20", user.subject(), "60", "tianji", 2, 3, List.of(), null, null,
                Set.copyOf(permissions), Set.of(), Map.of(), Map.of(), "checksum", now.minusSeconds(10),
                view.snapshotExpiresAt(), view, null);
        when(snapshotLoader.load(user)).thenReturn(snapshot);
        var mapper = new ObjectMapper();
        var identity = IdpBearerAuthenticationFilter.create(new UserAccessTokenVerifier(verifier), new ServiceAccessTokenVerifier(verifier),
                new ProcessSecurityConfiguration().processEndpointAuthenticationPolicy(), mapper, new VerifiedTenantContextFilter(), verifier);
        var authorization = new Rbac3BearerAuthenticationFilter(snapshotLoader, mapper);
        var draftService = mock(ProcessDraftService.class);
        var userOwner = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "editor", issuer.toString());
        var serviceOwner = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "editor", issuer.toString());
        var empty = new ProcessDraftPageDTO(Instant.parse("2026-11-14T08:00:00Z"), List.of(), null);
        when(draftService.list(userOwner, null, null, null, null, null)).thenReturn(empty);
        when(draftService.list(serviceOwner, null, null, null, null, null)).thenReturn(empty);

        new WebApplicationContextRunner().withUserConfiguration(DraftSecurityWeb.class)
                .withPropertyValues("stellar.tianji.process.api.enabled=true",
                        "egon.cola.platform.tianquan.shoubing.register-filter=false",
                        "egon.cola.platform.tianquan.jianshen.register-filter=false")
                .withBean(IdpBearerAuthenticationFilter.class, () -> identity)
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> authorization)
                .withBean(PermissionExecutionGuard.class, () -> mock(PermissionExecutionGuard.class))
                .withBean(ProcessDraftService.class, () -> draftService)
                .withBean(ProcessApiSchemaVerifier.class, () -> mock(ProcessApiSchemaVerifier.class))
                .withBean(ProcessReleaseService.class, () -> mock(ProcessReleaseService.class))
                .withBean(ProcessExecutionService.class, () -> mock(ProcessExecutionService.class))
                .withBean(ProcessDecisionService.class, () -> mock(ProcessDecisionService.class))
                .run(context -> assertDoesNotThrow(() -> {
                    assertNull(context.getStartupFailure());
                    var protectedMvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context.getSourceApplicationContext())
                            .apply(springSecurity()).build();
                    for (String token : List.of("draft-user", "draft-service")) {
                        protectedMvc.perform(get(ProcessDraftController.LIST_PATH).header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk());
                        protectedMvc.perform(head(ProcessDraftController.LIST_PATH).header("Authorization", "Bearer " + token))
                                .andExpect(status().isForbidden());
                        protectedMvc.perform(post(ProcessDraftController.LIST_PATH).header("Authorization", "Bearer " + token))
                                .andExpect(status().isForbidden());
                    }
                    protectedMvc.perform(get(ProcessDraftController.LIST_PATH).header("Authorization", "Bearer draft-invalid"))
                            .andExpect(status().isUnauthorized())
                            .andExpect(content().json("{\"code\":\"TIANJI_UNAUTHENTICATED\",\"correlationId\":null}"));
                    protectedMvc.perform(get(ProcessDraftController.LIST_PATH).header("Authorization", "Bearer draft-service")
                                    .header("X-Egon-Tianji-Tenant", "21"))
                            .andExpect(status().isConflict())
                            .andExpect(content().json("{\"code\":\"TIANJI_TENANT_CONTEXT_CHANGED\",\"correlationId\":null}"));
                    when(snapshotLoader.load(user)).thenThrow(new AuthorizationUnavailableException("unavailable details"));
                    protectedMvc.perform(get(ProcessDraftController.LIST_PATH).header("Authorization", "Bearer draft-user"))
                            .andExpect(status().isServiceUnavailable())
                            .andExpect(content().json("{\"code\":\"TIANJI_DEPENDENCY_UNAVAILABLE\",\"correlationId\":null}"));
                    verify(draftService).list(userOwner, null, null, null, null, null);
                    verify(draftService).list(serviceOwner, null, null, null, null, null);
                }));
    }

    private static List<DraftRouteVO> draftRoutes() {
        return List.of(
                new DraftRouteVO("POST", "/api/tianji/v2/process-definitions/order/drafts"),
                new DraftRouteVO("GET", "/api/tianji/v2/process-drafts/d1"),
                new DraftRouteVO("PUT", "/api/tianji/v2/process-drafts/d1"),
                new DraftRouteVO("GET", "/api/tianji/v2/process-drafts"),
                new DraftRouteVO("POST", "/api/tianji/v2/process-drafts/d1/discard-commands"));
    }

    private record DraftRouteVO(String method, String path) { }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ProcessSecurityConfiguration.class, ProcessDraftController.class, ProcessRequestBodyAdvice.class, ProcessApiErrors.class})
    static class DraftSecurityWeb { }

    @Test
    void case_start_and_get_are_explicit_mixed_principal_endpoints() {
        var policy = new ProcessSecurityConfiguration().processEndpointAuthenticationPolicy();
        for (String operation : java.util.List.of("start", "get")) {
            var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/tianji/v1/cases/" + operation);
            assertEquals(top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy.Requirement.USER_OR_SERVICE, policy.requirement(request));
        }
    }

    @Test
    void control_and_cancel_use_the_existing_mixed_principal_wildcard_policy() {
        var policy = new ProcessSecurityConfiguration().processEndpointAuthenticationPolicy();
        for (var operation : java.util.List.of("control", "cancellations")) {
            var request = new org.springframework.mock.web.MockHttpServletRequest(
                    operation.equals("control") ? "GET" : "POST", "/api/tianji/v1/process-instances/one/" + operation);
            assertEquals(top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy.Requirement.USER_OR_SERVICE,
                    policy.requirement(request));
        }
    }

    @Test
    void cancellation_has_an_explicit_default_off_configuration_boundary() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> java.util.Arrays.stream(ProcessSecurityConfiguration.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("CancellationConfiguration")).findFirst().orElseThrow());
    }

    @RestController
    static class Probe {
        @GetMapping("/actuator/health")
        String health() { return "ok"; }

        @GetMapping("/process-api")
        String process() { return "not exposed"; }
    }
}
