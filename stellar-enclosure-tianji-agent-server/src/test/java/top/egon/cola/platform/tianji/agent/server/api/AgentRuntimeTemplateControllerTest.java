package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactVerifier;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateDTO;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

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
import static top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.RuntimeProfileEnum.RUNTIME_V2;

class AgentRuntimeTemplateControllerTest {
    private static final String PATH = "/api/tianji/v2/agent-definitions";
    private static final String RESOURCE = "tianji.api.agent.template.publish.v2";
    private static final Instant NOW = Instant.parse("2026-11-11T11:00:00Z");
    private static final URI ISSUER = URI.create("https://idp.example/20");
    static final String LIMITS = """
            {"totalTimeout":"PT2M","modelIdleTimeout":"PT20S","toolTimeout":"PT10S","maxModelCalls":10,
             "maxToolCalls":10,"maxParallelism":2,"contextWindowTokens":8192,"maxOutputTokensPerCall":1024}
            """;
    private static final String SCHEMA = """
            {"type":"object","properties":{},"required":[],"additionalProperties":false}
            """;
    private static final String BODY = """
            {"templateKey":"extract","templateVersion":1,"runtimeProfile":"RUNTIME_V2","executorRef":"adk-default",
             "inputSchema":%s,"outputSchema":%s,"parameterSchema":%s,
             "configuration":{"instructionRef":{"key":"extract-prompt","version":1,"contentSha256":"%s"},
             "modelRef":"registered-model","toolRefs":[]},"limits":%s}
            """.formatted(SCHEMA, SCHEMA, SCHEMA, "a".repeat(64), LIMITS);

    private static final String ARTIFACT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["resourceKey","contentSha256","mediaType","sizeBytes"],
             "properties":{"resourceKey":{"type":"string","maxLength":128,"pattern":"^[A-Za-z][A-Za-z0-9_.:-]{0,127}$"},
                "contentSha256":{"type":"string","maxLength":64,"pattern":"^[0-9a-f]{64}$"},
                "mediaType":{"type":"string","maxLength":127,"pattern":"^[a-z0-9.+-]{1,63}/[a-z0-9.+-]{1,63}$"},
                "sizeBytes":{"type":"integer","minimum":0,"maximum":999999999999999}}}
            """;

    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final TianjiAgentAccess access = mock(TianjiAgentAccess.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final TrustedAgentActor actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE,
            "release-publisher", ISSUER.toASCIIString());

    @Test void publishes_and_replays_the_pinned_receipt_even_when_new_admission_is_off() throws Exception {
        environment.withProperty(AgentRuntimeTemplateController.NEW_ADMISSION, "true");
        authorizeService();
        var authority = mock(AgentLedgerService.RuntimeTemplatePublicationAuthority.class);
        when(authority.verify(eq(actor), any())).thenReturn(now -> true);
        var mvc = mvc(Map.of("authority", authority));
        var original = new AtomicReference<AgentRuntimeTemplateDTO>();
        when(ledger.publishRuntimeTemplate(eq(actor), any(AgentRuntimeTemplateCommand.class),
                any(BooleanSupplier.class), any(AgentLedgerService.RuntimeTemplatePublicationAuthority.class))).thenAnswer(call -> {
            AgentRuntimeTemplateCommand request = call.getArgument(1);
            if (original.get() != null) {
                if (!original.get().getContentSha256().equals(request.contentSha256())) {
                    throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEFINITION_VERSION_CONFLICT, "occupied");
                }
                return original.get();
            }
            assertTrue(((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            AgentLedgerService.RuntimeTemplatePublicationAuthority adapter = call.getArgument(3);
            var proof = adapter.verify(actor, request);
            assertTrue(proof.test(NOW));
            assertFalse(proof.test(NOW.plusSeconds(60)));
            var accepted = new AgentRuntimeTemplateDTO("extract", 1, request.contentSha256(), RUNTIME_V2, NOW);
            original.set(accepted);
            return accepted;
        });
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.templateKey").value("extract")).andExpect(jsonPath("$.templateVersion").value(1))
                .andExpect(jsonPath("$.runtimeProfile").value("RUNTIME_V2")).andExpect(jsonPath("$.publishedAt").value(NOW.toString()));
        environment.withProperty(AgentRuntimeTemplateController.NEW_ADMISSION, "false");
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publishedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.contentSha256").value(original.get().getContentSha256()));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY.replace("adk-default", "adk-other")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_DEFINITION_VERSION_CONFLICT"));
        verify(authority).verify(eq(actor), any());
        verify(access, times(6)).require(RESOURCE);
    }

    @Test void dedicated_resource_denial_uses_the_same_stable_error_before_reading_the_body() throws Exception {
        when(access.require(RESOURCE)).thenThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "denied"));
        var mvc = mvc(Map.of());
        for (String body : List.of(BODY, "{malformed", "")) {
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
        }
        var user = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "release-publisher", ISSUER.toASCIIString());
        org.mockito.Mockito.doReturn(new TianjiAgentAccess.Authorized(user, NOW.plusSeconds(60), ISSUER))
                .when(access).require(RESOURCE);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ledger);
    }

    @Test void newly_published_template_requires_dependency_permission_and_a_reference_verifier_when_selected() throws Exception {
        authorizeService();
        environment.withProperty(AgentRuntimeTemplateController.NEW_ADMISSION, "true");
        var authority = mock(AgentLedgerService.RuntimeTemplatePublicationAuthority.class);
        when(authority.verify(eq(actor), any())).thenThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "hidden"));
        when(ledger.publishRuntimeTemplate(eq(actor), any(), any(), any())).thenAnswer(call -> {
            AgentLedgerService.RuntimeTemplatePublicationAuthority adapter = call.getArgument(3);
            adapter.verify(actor, call.getArgument(1));
            throw new AssertionError("Missing permission reached publication");
        });
        var mvc = mvc(Map.of("authority", authority));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        String reference = BODY.replace("\"outputSchema\":" + SCHEMA, "\"outputSchema\":" + ARTIFACT_SCHEMA);
        assertFalse(BODY.equals(reference));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(reference))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"));
        verify(authority).verify(eq(actor), any());
    }

    @Test void disabled_admission_is_a_503_and_closed_wire_is_validated_without_publication() throws Exception {
        authorizeService();
        var mvc = mvc(Map.of());
        for (String malformed : List.of(BODY.replace("\"templateVersion\":1", "\"templateVersion\":1.0"),
                BODY.replace("\"templateVersion\":1", "\"templateVersion\":\"1\""),
                BODY.replace("\"templateVersion\":1", "\"templateVersion\":1000000000000000"),
                BODY.replace("\"templateKey\":\"extract\"", "\"templateKey\":\"extract\",\"templateKey\":\"changed\""),
                BODY.replace("\"toolRefs\":[]", "\"toolRefs\":[],\"agenticPlan\":null"),
                BODY.replace("\"inputSchema\":", "\"unexpectedSchema\":"), BODY + " {}")) {
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(malformed))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        verifyNoInteractions(ledger);
        when(ledger.publishRuntimeTemplate(eq(actor), any(), any(), any())).thenAnswer(call -> {
            assertFalse(((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "disabled");
        });
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"));
    }

    private void authorizeService() {
        when(access.require(RESOURCE)).thenReturn(new TianjiAgentAccess.Authorized(actor, NOW.plusSeconds(60), ISSUER));
    }

    private MockMvc mvc(Map<String, Object> hostBeans) {
        var beans = new StaticListableBeanFactory(hostBeans);
        var controller = new AgentRuntimeTemplateController(ledger, access, environment,
                beans.getBeanProvider(AgentLedgerService.RuntimeTemplatePublicationAuthority.class),
                beans.getBeanProvider(AgentResultArtifactVerifier.class));
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AgentApiErrors(), new AgentRequestBodyAdvice(access)).build();
    }
}
