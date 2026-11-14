package top.egon.cola.platform.tianji.process.server.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.CreateProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftPublicationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftValidationResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftValidationResultDTO.ValidationIssueVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.PublishProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ValidateProcessDraftQuery;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.ProcessSecurityConfiguration;
import top.egon.cola.platform.tianji.process.server.decision.ProcessDecisionService;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessTenantChangedException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.RuntimePolicyBinding;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ServicePolicySnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient.AuthorizationUnavailableException;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionServicePolicyGuard;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProcessAutomationControllerTest {
    private static final String VALIDATE = "/api/tianji/v2/process-model-validations";
    private static final String RELEASES = "/api/tianji/v2/process-releases";
    private static final String COPY = "/api/tianji/v2/process-definitions/review/drafts";
    private static final String VALIDATE_ACCESS = "tianji.api.process.model.validate.v2";
    private static final String PUBLISH_ACCESS = "tianji.api.process.release.publish.v2";
    private static final String GET_ACCESS = "tianji.api.process.release.get.v2";
    private static final String COPY_ACCESS = "tianji.api.process.draft.create.v2";
    private static final String HASH = "a".repeat(64);
    private static final String CHECK = """
            {"draftId":"pd-one","expectedRevision":2,"expectedContentSha256":"%s"}
            """.formatted(HASH).strip();
    private static final String PUBLISH = CHECK.substring(0, CHECK.length() - 1)
            + ",\"releaseVersion\":\"review-v2\",\"commandId\":\"publish-1\"}";
    private static final String COPY_BODY = """
            {"title":"Copy","baseReleaseVersion":"review-v2","commandId":"copy-1"}
            """;
    private static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER,
            "editor", "https://idp.test/main");
    private static final ProcessActor OTHER_TENANT = ProcessActor.verified(21, ProcessActor.SubjectType.USER,
            "editor", "https://idp.test/main");
    private static final ProcessActor SERVICE = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE,
            "publisher", "https://idp.test/main");
    private static final Instant POLICY_DEADLINE = Instant.parse("2026-11-14T15:00:00Z");
    private final PermissionServicePolicyGuard servicePolicies = mock(PermissionServicePolicyGuard.class);
    private final TianjiProcessAccess access = mock(TianjiProcessAccess.class);
    private final ProcessDraftService drafts = mock(ProcessDraftService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProcessAutomationController(access, drafts, servicePolicies), new ProcessDraftController(access, drafts))
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void establishPublisher() {
        var publisher = new ServiceIdentityPrincipal("publisher", "20", "publisher", "service-token",
                URI.create("https://api.test/process"), 1, Set.of("tianji:process:publish"),
                "xingyuan", "tianji", "test", "credential", POLICY_DEADLINE.minusSeconds(60), POLICY_DEADLINE.plusSeconds(60));
        var binding = new RuntimePolicyBinding("tianji-process-java", "1", HASH, "b".repeat(64), "c".repeat(64));
        var policy = new ServicePolicySnapshot(new CatalogScope("10", "20", "xingyuan", "tianji", "default", "test"),
                "1", "3", binding, Set.of(PUBLISH_ACCESS), POLICY_DEADLINE);
        when(servicePolicies.require(publisher, PUBLISH_ACCESS)).thenReturn(policy);
        SecurityContextHolder.getContext().setAuthentication(
                new IdpAuthenticationToken(publisher, List.of(), URI.create("https://idp.test/main")));
        when(access.require(PUBLISH_ACCESS)).thenReturn(SERVICE);
    }

    @Test
    void invalidPreviewAndInvalidPublicationHaveDifferentOutcomes() throws Exception {
        when(access.require(VALIDATE_ACCESS)).thenReturn(OWNER);
        establishPublisher();
        when(drafts.validate(eq(OWNER), any(ValidateProcessDraftQuery.class))).thenReturn(
                DraftValidationResultDTO.builder().draftId("pd-one").revision(2).contentSha256(HASH)
                        .valid(false).compilerVersion("automation-bpmn-2")
                        .issues(List.of(new ValidationIssueVO("$.models", "INVALID_MODEL", "Model is incomplete", null))).build());
        when(drafts.publish(eq(SERVICE), any(PublishProcessDraftCommand.class), eq(POLICY_DEADLINE)))
                .thenThrow(new IllegalArgumentException("private parser exception"));
        mvc.perform(post(VALIDATE).contentType(MediaType.APPLICATION_JSON).content(CHECK))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.draftId").value("pd-one")).andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("INVALID_MODEL"));
        mvc.perform(post(RELEASES).contentType(MediaType.APPLICATION_JSON).content(PUBLISH))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
    }

    @Test
    void publicationReturnsTheOriginalSealEvidence() throws Exception {
        establishPublisher();
        when(drafts.publish(eq(SERVICE), any(PublishProcessDraftCommand.class), eq(POLICY_DEADLINE))).thenReturn(new DraftPublicationVO(
                "pd-one", 2, 3, "review-v2", HASH, "b".repeat(64), "c".repeat(64),
                "automation-bpmn-2", Instant.parse("2026-11-14T02:00:00.123456Z"), "publish-1"));
        mvc.perform(post(RELEASES).contentType(MediaType.APPLICATION_JSON).content(PUBLISH))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.publishedRevision").value(2)).andExpect(jsonPath("$.draftRevision").value(3))
                .andExpect(jsonPath("$.commandId").value("publish-1"))
                .andExpect(jsonPath("$.deploymentId").doesNotExist()).andExpect(jsonPath("$.activated").doesNotExist());
    }

    @Test
    void userPublicationCarriesTheCurrentPermissionViewCutoff() throws Exception {
        var identity = new IdentityPrincipal("editor", "20", "user-token", Set.of("https://api.test/process"),
                POLICY_DEADLINE.minusSeconds(60), POLICY_DEADLINE.plusSeconds(60),
                AuthenticationContext.of("PASSWORD", POLICY_DEADLINE.minusSeconds(60)));
        var scope = new CatalogScope("10", "20", "xingyuan", "tianji", "default", "test");
        var permission = "TIANJI_PROCESS_RELEASE_PUBLISH";
        var view = new EffectivePermissionSnapshot("https://idp.test/main", "editor", scope, "1", "2", "3",
                new EffectivePermissionSnapshot.ActiveRoleContext("tianji", List.of(), List.of()), List.of(permission), POLICY_DEADLINE);
        var binding = new RuntimePolicyBinding("tianji-process-java", "1", HASH, "b".repeat(64), "c".repeat(64));
        var snapshot = new SystemAuthorizationSnapshot("20", "editor", "60", "tianji", 2, 3, List.of(), null, null,
                Set.of(permission), Set.of(PUBLISH_ACCESS), Map.of(), Map.of(), "checksum",
                POLICY_DEADLINE.minusSeconds(30), POLICY_DEADLINE, view, binding);
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(new Rbac3UserDetails(identity, snapshot)));
        when(access.require(PUBLISH_ACCESS)).thenReturn(OWNER);
        when(drafts.publish(eq(OWNER), any(PublishProcessDraftCommand.class), eq(POLICY_DEADLINE)))
                .thenThrow(new AuthenticationCredentialsNotFoundException("Publication permission expired"));
        mvc.perform(post(RELEASES).contentType(MediaType.APPLICATION_JSON).content(PUBLISH))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TIANJI_UNAUTHENTICATED"));
        verify(drafts).publish(eq(OWNER), any(PublishProcessDraftCommand.class), eq(POLICY_DEADLINE));
        verifyNoInteractions(servicePolicies);
    }

    @Test
    void rejectsNonExactBodiesBeforeCallingDrafts() throws Exception {
        for (String invalid : List.of(CHECK.replace("2,", "\"2\","), CHECK.replace("2,", "2.0,"),
                CHECK.replace("2,", "2,\"expectedRevision\":2,"), CHECK + "{}", PUBLISH)) {
            mvc.perform(post(VALIDATE).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        mvc.perform(post(RELEASES).contentType(MediaType.APPLICATION_JSON).content(CHECK))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        mvc.perform(post(RELEASES).contentType(MediaType.APPLICATION_JSON).content(PUBLISH + " ".repeat(4096)))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("TIANJI_REQUEST_TOO_LARGE"));
        verifyNoInteractions(drafts);
    }

    @Test
    void reportsStaleDraftAndUnavailableDependenciesInTheSameClosedEnvelope() throws Exception {
        when(access.require(VALIDATE_ACCESS)).thenReturn(OWNER);
        when(drafts.validate(eq(OWNER), any(ValidateProcessDraftQuery.class)))
                .thenThrow(new AutomationOperationException(AutomationOperationException.Code.TIANJI_DRAFT_VERSION_CONFLICT))
                .thenThrow(new IllegalStateException("private checked authority details"));
        mvc.perform(post(VALIDATE).contentType(MediaType.APPLICATION_JSON).content(CHECK))
                .andExpect(status().isConflict()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_DRAFT_VERSION_CONFLICT"));
        mvc.perform(post(VALIDATE).contentType(MediaType.APPLICATION_JSON).content(CHECK))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void requiresReleaseReadAndPassesOnlyTheVerifiedActor() throws Exception {
        when(access.require(GET_ACCESS)).thenThrow(new AccessDeniedException("private reason"));
        mvc.perform(get(RELEASES + "/review-v2"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
        verifyNoInteractions(drafts);
        doReturn(OTHER_TENANT).when(access).require(GET_ACCESS);
        when(drafts.getAutomationRelease(OTHER_TENANT, "review-v2")).thenThrow(new ProcessResourceNotFoundException());
        mvc.perform(get(RELEASES + "/review-v2"))
                .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        mvc.perform(get(RELEASES + "/review-v2").param("tenantId", "20"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        mvc.perform(get(RELEASES + "/review-v2").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        verify(drafts, times(1)).getAutomationRelease(OTHER_TENANT, "review-v2");
    }

    @Test
    void copyMasksUnreadableSourceAndKeepsPrimaryPermissionDenial() throws Exception {
        when(access.require(COPY_ACCESS)).thenThrow(new AccessDeniedException("draft access"));
        mvc.perform(post(COPY).contentType(MediaType.APPLICATION_JSON).content(COPY_BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));

        doReturn(OWNER).when(access).require(COPY_ACCESS);
        when(access.require(GET_ACCESS)).thenThrow(new AccessDeniedException("source access"));
        mvc.perform(post(COPY).contentType(MediaType.APPLICATION_JSON).content(COPY_BODY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        doReturn(OTHER_TENANT).when(access).require(GET_ACCESS);
        mvc.perform(post(COPY).contentType(MediaType.APPLICATION_JSON).content(COPY_BODY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));

        doThrow(new AuthenticationCredentialsNotFoundException("identity expired")).when(access).require(GET_ACCESS);
        mvc.perform(post(COPY).contentType(MediaType.APPLICATION_JSON).content(COPY_BODY))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TIANJI_UNAUTHENTICATED"));
        doThrow(new ProcessTenantChangedException()).when(access).require(GET_ACCESS);
        mvc.perform(post(COPY).contentType(MediaType.APPLICATION_JSON).content(COPY_BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_TENANT_CONTEXT_CHANGED"));

        doThrow(new AuthorizationUnavailableException("source check unavailable")).when(access).require(GET_ACCESS);
        mvc.perform(post(COPY).contentType(MediaType.APPLICATION_JSON).content(COPY_BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"));
        verifyNoInteractions(drafts);
    }

    @Test
    void copyPassesTheExactAuthorizedSourceAndEmptyDraftSkipsReadAuthorization() throws Exception {
        when(access.require(COPY_ACCESS)).thenReturn(OWNER);
        when(access.require(GET_ACCESS)).thenReturn(OWNER);
        var copyCommand = new CreateProcessDraftCommand("Copy", "review-v2", "copy-1");
        var emptyCommand = new CreateProcessDraftCommand("Copy", null, "copy-1");
        var draft = DraftSummaryDTO.builder().draftId("pd-one").build();
        when(drafts.create(OWNER, "review", copyCommand)).thenReturn(draft);
        when(drafts.create(OWNER, "review", emptyCommand)).thenReturn(draft);

        mvc.perform(post(COPY).contentType(MediaType.APPLICATION_JSON).content(COPY_BODY))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.draftId").value("pd-one"));
        doThrow(new AccessDeniedException("source read denied")).when(access).require(GET_ACCESS);
        mvc.perform(post(COPY).contentType(MediaType.APPLICATION_JSON)
                        .content(COPY_BODY.replace("\"review-v2\"", "null")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draftId").value("pd-one"));
        verify(access, times(1)).require(GET_ACCESS);
        verify(drafts).create(OWNER, "review", copyCommand);
        verify(drafts).create(OWNER, "review", emptyCommand);
    }

    @Test
    void actualSecurityChainRequiresIdentityAndRejectsUnlistedMethods() throws Exception {
        var authentication = mock(IdpBearerAuthenticationFilter.class);
        var authorization = mock(Rbac3BearerAuthenticationFilter.class);
        for (var filter : List.of(authentication, authorization)) {
            doAnswer(invocation -> {
                FilterChain chain = invocation.getArgument(2);
                chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(filter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
        }
        new WebApplicationContextRunner().withUserConfiguration(AutomationSecurityWeb.class)
                .withPropertyValues("stellar.tianji.process.api.enabled=true",
                        "egon.cola.platform.tianquan.shoubing.register-filter=false",
                        "egon.cola.platform.tianquan.jianshen.register-filter=false")
                .withBean(IdpBearerAuthenticationFilter.class, () -> authentication)
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> authorization)
                .withBean(PermissionExecutionGuard.class, () -> mock(PermissionExecutionGuard.class))
                .withBean("permissionServicePolicyGuard", PermissionServicePolicyGuard.class, () -> servicePolicies)
                .withBean("processDraftService", ProcessDraftService.class, () -> drafts)
                .withBean("processApiSchemaVerifier", ProcessApiSchemaVerifier.class, () -> mock(ProcessApiSchemaVerifier.class))
                .withBean("processReleaseService", ProcessReleaseService.class, () -> mock(ProcessReleaseService.class))
                .withBean(ProcessExecutionService.class, () -> mock(ProcessExecutionService.class))
                .withBean(ProcessDecisionService.class, () -> mock(ProcessDecisionService.class))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean("processAutomationController"));
                    var secured = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                            .apply(springSecurity()).build();
                    secured.perform(get(RELEASES + "/review-v2"))
                            .andExpect(status().isUnauthorized())
                            .andExpect(header().string("Cache-Control", "no-store"))
                            .andExpect(jsonPath("$.code").value("TIANJI_UNAUTHENTICATED"));
                    secured.perform(post(VALIDATE).contentType(MediaType.APPLICATION_JSON).content(CHECK))
                            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TIANJI_UNAUTHENTICATED"));
                    secured.perform(post(RELEASES).contentType(MediaType.APPLICATION_JSON).content(PUBLISH))
                            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TIANJI_UNAUTHENTICATED"));
                    secured.perform(head(RELEASES + "/review-v2").with(user("editor")))
                            .andExpect(status().isForbidden());
                    secured.perform(get(VALIDATE).with(user("editor"))).andExpect(status().isForbidden());
                    verifyNoInteractions(drafts);
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ProcessSecurityConfiguration.class, ProcessAutomationController.class, ProcessApiErrors.class, ProcessRequestBodyAdvice.class})
    static class AutomationSecurityWeb { }

}
