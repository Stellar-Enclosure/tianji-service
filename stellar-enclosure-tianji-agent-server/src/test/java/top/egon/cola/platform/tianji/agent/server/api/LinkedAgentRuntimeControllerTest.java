package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService.FrozenSiBundleVO;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLinkedSubmissionService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.agent.server.security.TianjiLinkedDispatchBindings;
import top.egon.cola.platform.tianji.agent.server.security.TianjiLinkedDispatchProperties;
import top.egon.cola.platform.tianji.agent.server.security.TianjiServiceExecutionGrantIssuer;
import top.egon.cola.platform.tianji.agent.server.security.VerifiedAgentIssuerFilter;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentSubmissionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LinkedAgentRuntimeControllerTest {
    private static final String PATH = "/api/tianji/v2/agent-process-dispatches/submit";
    private static final String HASH = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-11T11:00:00Z");
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private static final String RUN_ID = "00000000-0000-0000-0000-000000000001";
    private static final String BODY = """
            {"idempotencyKey":"dispatch-101","link":{"sourceId":"ps-main","scopeType":"BPMN",
             "scopeInstanceId":"pi-101","activityOccurrenceId":"job-101","definitionContentSha256":"%s",
             "dispatchNotAfter":"2026-11-11T11:00:30Z"},"nodeExecutionRef":"nx-101","templateKey":"extract",
             "templateVersion":1,"templateSha256":"%s","configurationSha256":"%s","inputSchemaSha256":"%s",
             "outputSchemaSha256":"%s","input":{"text":"example"},"parameters":{},"limits":%s,
             "attemptDeadlineAt":"2026-11-11T11:02:00Z","correlationId":"corr-101"}
            """.formatted(HASH, HASH, HASH, HASH, HASH, AgentRuntimeTemplateControllerTest.LIMITS);

    private final TianjiSiExecutionBundleService bundleService = mock(TianjiSiExecutionBundleService.class);
    private final AgentRuntimeTemplateCommand selectedTemplate = new AgentRuntimeTemplateCommand();
    private final FrozenSiBundleVO preparedBundle = mock(FrozenSiBundleVO.class);
    private final AgentLinkedSubmissionService submissions = mock(AgentLinkedSubmissionService.class);
    private final TianjiServiceExecutionGrantIssuer issuer = mock(TianjiServiceExecutionGrantIssuer.class);
    private final PermissionExecutionGuard permissions = mock(PermissionExecutionGuard.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final ServiceIdentityPrincipal principal = new ServiceIdentityPrincipal("worker", "20", "worker", "token",
            URI.create("https://as.example/api"), 1, Set.of(TianjiAgentPermissions.LINKED_SUBMIT_SCOPE),
            "business", "app", "test", "credential", NOW.minusSeconds(60), NOW.plusSeconds(300), "app-id", ServiceTokenContext.TENANT);
    private final TrustedAgentActor actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE,
            principal.subject(), ISSUER.toASCIIString());
    private final TianjiLinkedDispatchProperties.Binding binding = new TianjiLinkedDispatchProperties.Binding(ISSUER, 20,
            "worker", "app-id", "business", "app", "test", "ps-main", Set.of(WorkerScopeTypeEnum.BPMN),
            List.of(new TianjiLinkedDispatchProperties.Definition("extract", 1, HASH)));
    private final AgentProcessLink link = new AgentProcessLink("ps-main", WorkerScopeTypeEnum.BPMN, "pi-101", "job-101", HASH,
            NOW.plusSeconds(30));
    private final AgentRunDTO queued = new AgentRunDTO(RUN_ID, "extract", 1, null, AgentRunStatusEnum.QUEUED, null, false, 0, 1, 1,
            NOW.plusSeconds(120), NOW, null);
    private final LinkedAgentLookupQuery query = new LinkedAgentLookupQuery("dispatch-101", link);
    private MockMvc mvc;

    @BeforeEach void setUp() {
        var issuers = mock(TrustedIssuerRegistry.class);
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(
                ISSUER, URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20",
                TrustedIssuerRegistry.Status.ACTIVE, 1)));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(), ISSUER));
        var beans = new StaticListableBeanFactory(Map.of("tianjiServiceExecutionGrantIssuer", issuer,
                "tianjiSiExecutionBundleService", bundleService));
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var access = new TianjiAgentAccess(permissions, clock);
        var bindings = new TianjiLinkedDispatchBindings(new TianjiLinkedDispatchProperties(true, false, List.of(binding)), issuers, clock);
        var controller = new LinkedAgentRuntimeController(submissions, access, bindings, environment,
                beans.getBeanProvider(TianjiServiceExecutionGrantIssuer.class),
                beans.getBeanProvider(TianjiSiExecutionBundleService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(new VerifiedAgentIssuerFilter())
                .setControllerAdvice(new AgentApiErrors(), new AgentRequestBodyAdvice(access)).build();
    }

    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test void accepts_an_exact_linked_runtime_command_on_the_private_v2_route() throws Exception {
        environment.withProperty(AgentRuntimeTemplateController.NEW_ADMISSION, "true");
        when(bundleService.prepare(actor, selectedTemplate)).thenReturn(preparedBundle);
        when(submissions.submitRuntimeV2(eq(actor), any(), any(), any())).thenAnswer(call -> {
            LinkedAgentRuntimeCommand command = call.getArgument(1);
            assertTrue(((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            assertTrue(link.equals(command.getLink()));
            assertTrue(command.getInput().equals(Map.of("text", "example")));
            Function<AgentRuntimeTemplateCommand, FrozenSiBundleVO> preparation = call.getArgument(3);
            assertThat(preparation.apply(selectedTemplate)).isSameAs(preparedBundle);
            return queued;
        });
        when(submissions.resolveRuntimeV2(actor, query)).thenReturn(Optional.of(new LinkedAgentSubmissionDTO(false, queued)));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.runId").value(RUN_ID)).andExpect(jsonPath("$.nodeExecutionRef").value("nx-101"))
                .andExpect(jsonPath("$.idempotencyKey").value("dispatch-101")).andExpect(jsonPath("$.state").value("QUEUED"))
                .andExpect(jsonPath("$.revision").value(1)).andExpect(jsonPath("$.input").doesNotExist());
        verify(issuer).ensureRuntime(any(), eq(principal), eq(binding), any(), eq(queued));
        verify(permissions, times(3)).requireExecution(TianjiAgentPermissions.LINKED_RUNTIME_SUBMIT_API);
        verify(bundleService).prepare(actor, selectedTemplate);
    }

    @Test void keeps_exact_replay_and_returns_existing_conflict_when_new_admission_is_off() throws Exception {
        var existing = new AtomicBoolean();
        when(submissions.submitRuntimeV2(eq(actor), any(), any(), any())).thenAnswer(call -> {
            assertFalse(((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            if (!existing.get()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "disabled");
            LinkedAgentRuntimeCommand command = call.getArgument(1);
            if (!"nx-101".equals(command.getNodeExecutionRef())) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "changed");
            }
            return queued;
        });
        when(submissions.resolveRuntimeV2(actor, query)).thenReturn(Optional.of(new LinkedAgentSubmissionDTO(false, queued)));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"));
        verifyNoInteractions(issuer);
        existing.set(true);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.runId").value(RUN_ID));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY.replace("nx-101", "nx-changed")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_IDEMPOTENCY_CONFLICT"));
        verify(issuer).ensureRuntime(any(), eq(principal), eq(binding), any(), eq(queued));
        verifyNoInteractions(bundleService);
    }

    @Test void unavailable_frozen_material_prevents_linked_start_and_grant_issuance() throws Exception {
        environment.withProperty(AgentRuntimeTemplateController.NEW_ADMISSION, "true");
        when(bundleService.prepare(actor, selectedTemplate)).thenThrow(TianjiSiExecutionBundleService.unavailable());
        when(submissions.submitRuntimeV2(eq(actor), any(), any(), any())).thenAnswer(call -> {
            Function<AgentRuntimeTemplateCommand, FrozenSiBundleVO> preparation = call.getArgument(3);
            preparation.apply(selectedTemplate);
            throw new AssertionError("Missing SI material must stop admission");
        });
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"));
        verifyNoInteractions(issuer);
    }

    @Test void refuses_to_acknowledge_an_old_stored_revision_that_cannot_be_represented() throws Exception {
        var old = new AgentRunDTO(RUN_ID, "extract", 1, null, AgentRunStatusEnum.QUEUED, null, false, 0, 0, 1,
                NOW.plusSeconds(120), NOW, null);
        when(submissions.submitRuntimeV2(eq(actor), any(), any(), any())).thenReturn(old);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.revision").doesNotExist());
        verifyNoInteractions(issuer);
    }

    @Test void refuses_identity_source_template_and_deadline_failures_without_a_grant() throws Exception {
        for (String body : List.of(BODY, "{malformed", "")) {
            mvc.perform(post(PATH).header("X-Egon-Tianji-Tenant", "21").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_TENANT_CONTEXT_CHANGED"));
        }
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY.replace("extract", "other-template")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
        assertThatThrownBy(() -> mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("ps-main", "other-source")))).hasCauseInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(submissions, issuer);
        when(submissions.submitRuntimeV2(eq(actor), any(), any(), any())).thenThrow(
                new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DISPATCH_EXPIRED, "expired"));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY.replace("11:00:30Z", "10:59:59Z")
                        .replace("11:02:00Z", "11:00:00Z")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_DISPATCH_EXPIRED"));
        verifyNoInteractions(issuer);
    }

    @Test void rejects_nonclosed_nonutf8_and_ambiguous_runtime_commands() throws Exception {
        String trimmed = BODY.strip();
        for (String malformed : List.of(BODY.replace("\"templateVersion\":1", "\"templateVersion\":1.0"),
                BODY.replace("\"templateVersion\":1", "\"templateVersion\":\"1\""),
                BODY.replace("\"sourceId\":\"ps-main\"", "\"sourceId\":\"ps-main\",\"sourceId\":\"other\""),
                BODY.replace("\"attemptDeadlineAt\"", "\"undocumented\""), BODY.replace("11:02:00Z", "11:02:00+00:00"),
                BODY.replace("nx-101", "!invalid"), trimmed.substring(0, trimmed.length() - 1) + ",\"tenantId\":20}", BODY + " {}")) {
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(malformed))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY.getBytes(StandardCharsets.UTF_16)))
                .andExpect(status().isBadRequest());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(" ".repeat(2_097_153)))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("TIANJI_REQUEST_TOO_LARGE"));
        verifyNoInteractions(submissions, issuer);
    }
}
