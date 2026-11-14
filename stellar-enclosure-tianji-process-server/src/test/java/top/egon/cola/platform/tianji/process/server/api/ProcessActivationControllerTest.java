package top.egon.cola.platform.tianji.process.server.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.AutomationActivationDTO;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.ProcessSecurityConfiguration;
import top.egon.cola.platform.tianji.process.server.decision.ProcessDecisionService;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.RuntimePolicyBinding;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;
import top.egon.cola.platform.tianquan.shoubing.contract.AuthenticationContext;
import top.egon.cola.platform.tianquan.shoubing.contract.IdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProcessActivationControllerTest {
    private static final String PATH = "/api/tianji/v2/process-definitions/review/activations";
    private static final String ACTIVATE = "tianji.api.process.release.activate.v2";
    private static final String READ = "tianji.api.process.activation.list.v2";
    private static final String COMMAND = """
            {"releaseVersion":"review-v1","expectedActivationRevision":0,"commandId":"activate-1"}
            """.strip();
    private static final String HASH = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-14T08:35:00.123456Z");
    private static final ProcessActor USER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "operator", "https://idp.test/main");
    private static final ProcessActor SERVICE = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "publisher", "https://idp.test/main");
    private static final ProcessActor FOREIGN = ProcessActor.verified(21, ProcessActor.SubjectType.USER, "operator", "https://idp.test/main");
    private final TianjiProcessAccess access = mock(TianjiProcessAccess.class);
    private final AutomationActivationService service = mock(AutomationActivationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ProcessActivationController(access, service))
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
    }

    @Test
    void userNeedsBothIndependentPermissionsAndSameVerifiedIdentity() throws Exception {
        when(access.require(ACTIVATE)).thenThrow(new AccessDeniedException("no ACTIVATE"));
        activate().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
        doReturn(USER).when(access).require(ACTIVATE);
        when(access.require(READ)).thenThrow(new AccessDeniedException("no READ"));
        activate().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
        doReturn(FOREIGN).when(access).require(READ);
        activate().andExpect(status().isForbidden());
        doReturn(SERVICE).when(access).require(ACTIVATE);
        doReturn(SERVICE).when(access).require(READ);
        activate().andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void originalPostReceiptRemainsDistinctFromLatestPointerAndOwnLookup() throws Exception {
        authorizeUser();
        var command = new ActivationCommand("review-v1", 0L, "activate-1");
        var original = new AutomationActivationDTO("review", 1, "review-v1", null, "activate-1",
                0L, HASH, HASH, HASH, NOW);
        when(service.activate(USER, "review", command)).thenReturn(original);
        var latest = new ActivationPageDTO.CurrentActivationVO(2, "review-v2", NOW.plusSeconds(1));
        when(service.list(USER, "review", "activate-1", null, null))
                .thenReturn(new ActivationPageDTO("review", NOW.plusSeconds(2), latest, List.of(original), null));
        for (int index = 0; index < 2; index++) {
            activate().andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.revision").value(1)).andExpect(jsonPath("$.releaseVersion").value("review-v1"))
                    .andExpect(jsonPath("$.previousRevision").value(nullValue())).andExpect(jsonPath("$.proof").doesNotExist())
                    .andExpect(jsonPath("$.actor").doesNotExist());
        }
        mvc.perform(get(PATH).param("commandId", "activate-1"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.current.revision").value(2)).andExpect(jsonPath("$.current.releaseVersion").value("review-v2"))
                .andExpect(jsonPath("$.items[0].revision").value(1)).andExpect(jsonPath("$.nextCursor").value(nullValue()));
        verify(service, times(2)).activate(USER, "review", command);
        verify(service).list(USER, "review", "activate-1", null, null);
    }

    @Test
    void serviceMayReadButMissingOrHiddenDefinitionDoesNotDisclose() throws Exception {
        doThrow(new AccessDeniedException("private")).when(access).require(READ);
        mvc.perform(get(PATH)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
        doReturn(SERVICE).when(access).require(READ);
        when(service.list(SERVICE, "review", null, 20, null))
                .thenReturn(new ActivationPageDTO("review", NOW, null, List.of(), null))
                .thenThrow(new ProcessResourceNotFoundException());
        mvc.perform(get(PATH).param("limit", "20")).andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(nullValue())).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get(PATH).param("limit", "20")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
    }

    @Test
    void strictBodyAndQueryRejectPublicProofIdentityOrCoercion() throws Exception {
        authorizeUser();
        for (String invalid : List.of(COMMAND.replace(":0,", ":0.0,"), COMMAND.replace(":0,", ":\"0\","),
                COMMAND.replace(":0,", ":-1,"), COMMAND.replace(":0,", ":1000000000000000,"),
                COMMAND.replace(":0,", ":0,\"expectedActivationRevision\":0,"),
                COMMAND.replace("\"review-v1\"", "null"), COMMAND + "{}", "[]",
                COMMAND.replace("\"review-v1\"", "\"invalid:version\""), COMMAND.replace("\"activate-1\"", "\"\""),
                COMMAND.replace("}", ",\"proof\":{},\"actor\":\"foreign\"}"))) {
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(COMMAND + " ".repeat(4096)))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("TIANJI_REQUEST_TOO_LARGE"));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(COMMAND).param("actor", "foreign"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        for (var invalid : List.of(get(PATH).param("limit", "01"), get(PATH).param("limit", "101"),
                get(PATH).param("limit", "5", "5"), get(PATH).param("tenantId", "21"),
                get(PATH).param("cursor", "x".repeat(2049)), get(PATH).param("commandId", "activate-1").param("cursor", "a"),
                get(PATH.replace("/review/", "/9-invalid/")),
                get(PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))) {
            mvc.perform(invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void stableErrorsHideProofAndPersistenceDetails() throws Exception {
        authorizeUser();
        when(service.activate(USER, "review", new ActivationCommand("review-v1", 0L, "activate-1")))
                .thenThrow(new ProcessVersionConflictException())
                .thenThrow(new ProcessConflictException())
                .thenThrow(new ProcessResourceNotFoundException())
                .thenThrow(new IllegalArgumentException("private definition mismatch"))
                .thenThrow(new IllegalStateException("private proof"));
        activate().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_PROCESS_VERSION_CONFLICT"));
        activate().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_IDEMPOTENCY_CONFLICT"));
        activate().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        activate().andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        activate().andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE")).andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void actualSecurityAllowsOnlyGetAndPostEvenWithNewAdmissionDisabled() throws Exception {
        var identity = mock(IdpBearerAuthenticationFilter.class);
        var authorization = mock(Rbac3BearerAuthenticationFilter.class);
        var permissions = mock(PermissionExecutionGuard.class);
        var issuer = URI.create("https://idp.test/main");
        var resource = URI.create("https://api.test/process");
        var issuedAt = Instant.now().minusSeconds(60);
        var expiresAt = issuedAt.plusSeconds(600);
        var human = new IdentityPrincipal("operator", "20", "user-token", Set.of(resource.toString()), issuedAt, expiresAt,
                AuthenticationContext.of("PASSWORD", issuedAt));
        var client = new ServiceIdentityPrincipal("publisher", "20", "publisher", "service-token", resource, 1,
                Set.of("tianji:process:release:read"), "xingyuan", "tianji", "test", "credential", issuedAt, expiresAt);
        var userAuthorization = userAuthorization(human, issuer);
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            switch (request.getHeader("Authorization")) {
                case "Bearer verified-user" -> SecurityContextHolder.getContext()
                        .setAuthentication(new IdpAuthenticationToken(human, List.of(), issuer));
                case "Bearer verified-service" -> SecurityContextHolder.getContext()
                        .setAuthentication(new IdpAuthenticationToken(client, List.of(), issuer));
                case null, default -> { }
            }
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, invocation.getArgument(1));
            return null;
        }).when(identity).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
        doAnswer(invocation -> {
            if (SecurityContextHolder.getContext().getAuthentication() instanceof IdpAuthenticationToken verified
                    && verified.getPrincipal() == human) {
                SecurityContextHolder.getContext().setAuthentication(userAuthorization);
            }
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(authorization).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
        new WebApplicationContextRunner().withUserConfiguration(ActivationSecurityWeb.class)
                .withPropertyValues("stellar.tianji.process.api.enabled=true", "stellar.tianji.process.automation.enabled=false",
                        "egon.cola.platform.tianquan.shoubing.register-filter=false",
                        "egon.cola.platform.tianquan.jianshen.register-filter=false")
                .withBean(IdpBearerAuthenticationFilter.class, () -> identity)
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> authorization)
                .withBean(PermissionExecutionGuard.class, () -> permissions)
                .withBean("automationActivationService", AutomationActivationService.class, () -> service)
                .withBean("processApiSchemaVerifier", ProcessApiSchemaVerifier.class, () -> mock(ProcessApiSchemaVerifier.class))
                .withBean("processReleaseService", ProcessReleaseService.class, () -> mock(ProcessReleaseService.class))
                .withBean(ProcessExecutionService.class, () -> mock(ProcessExecutionService.class))
                .withBean(ProcessDecisionService.class, () -> mock(ProcessDecisionService.class))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var secured = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                            .apply(springSecurity()).build();
                    secured.perform(get(PATH)).andExpect(status().isUnauthorized())
                            .andExpect(header().string("Cache-Control", "no-store"))
                            .andExpect(jsonPath("$.code").value("TIANJI_UNAUTHENTICATED"))
                            .andExpect(jsonPath("$.correlationId").value(nullValue()));
                    secured.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(COMMAND))
                            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TIANJI_UNAUTHENTICATED"));
                    secured.perform(head(PATH).with(user("operator"))).andExpect(status().isForbidden());
                    secured.perform(put(PATH).with(user("operator"))).andExpect(status().isForbidden());
                    secured.perform(get(PATH + "/extra").with(user("operator"))).andExpect(status().isForbidden());
                    secured.perform(post(PATH).header("Authorization", "Bearer verified-service")
                                    .contentType(MediaType.APPLICATION_JSON).content(COMMAND))
                            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
                    secured.perform(get(PATH).header("Authorization", "Bearer verified-service")
                                    .header("X-Egon-Tianji-Tenant", "21"))
                            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_TENANT_CONTEXT_CHANGED"));
                    secured.perform(post(PATH).header("Authorization", "Bearer verified-user")
                                    .contentType(MediaType.TEXT_PLAIN).content(COMMAND))
                            .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                            .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
                    secured.perform(get(PATH).header("Authorization", "Bearer verified-service").accept(MediaType.TEXT_PLAIN))
                            .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                            .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
                    verifyNoInteractions(service);
                    when(service.list(SERVICE, "review", null, null, null))
                            .thenReturn(new ActivationPageDTO("review", NOW, null, List.of(), null));
                    var prior = new ActivationCommand("review-v1", 0L, "activate-1");
                    when(service.activate(USER, "review", prior)).thenReturn(new AutomationActivationDTO(
                            "review", 1, "review-v1", null, "activate-1", 0L, HASH, HASH, HASH, NOW));
                    secured.perform(get(PATH).header("Authorization", "Bearer verified-service")
                                    .header("X-Egon-Tianji-Tenant", "20"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.current").value(nullValue()));
                    secured.perform(post(PATH).header("Authorization", "Bearer verified-user")
                                    .contentType(MediaType.APPLICATION_JSON).content(COMMAND))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.commandId").value("activate-1"));
                    verify(service).list(SERVICE, "review", null, null, null);
                    verify(service).activate(USER, "review", prior);
                });
    }

    private static Rbac3AuthenticationToken userAuthorization(IdentityPrincipal identity, URI issuer) {
        var scopes = new CatalogScope("10", "20", "xingyuan", "tianji", "default", "test");
        var grants = List.of("TIANJI_PROCESS_RELEASE_ACTIVATE", "TIANJI_PROCESS_RELEASE_READ");
        var view = new EffectivePermissionSnapshot(issuer.toString(), identity.subject(), scopes, "1", "2", "3",
                new EffectivePermissionSnapshot.ActiveRoleContext("tianji", List.of(), List.of()), grants, identity.expiresAt());
        var binding = new RuntimePolicyBinding("tianji-process-java", "1", HASH, "b".repeat(64), "c".repeat(64));
        var snapshot = new SystemAuthorizationSnapshot(identity.tenantId(), identity.subject(), "60", "tianji", 2, 3, List.of(),
                null, null, Set.copyOf(grants), Set.of(ACTIVATE, READ), Map.of(), Map.of(), "checksum",
                identity.issuedAt(), identity.expiresAt(), view, binding);
        return new Rbac3AuthenticationToken(new Rbac3UserDetails(identity, snapshot));
    }

    private org.springframework.test.web.servlet.ResultActions activate() throws Exception {
        return mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(COMMAND));
    }

    private void authorizeUser() {
        when(access.require(ACTIVATE)).thenReturn(USER);
        when(access.require(READ)).thenReturn(USER);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ProcessSecurityConfiguration.class, ProcessActivationController.class, ProcessApiErrors.class, ProcessRequestBodyAdvice.class})
    static class ActivationSecurityWeb { }
}
