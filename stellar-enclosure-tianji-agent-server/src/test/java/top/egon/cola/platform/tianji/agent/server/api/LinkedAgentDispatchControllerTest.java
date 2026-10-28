package top.egon.cola.platform.tianji.agent.server.api;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLinkedSubmissionService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.agent.server.security.TianjiLinkedDispatchBindings;
import top.egon.cola.platform.tianji.agent.server.security.TianjiLinkedDispatchProperties;
import top.egon.cola.platform.tianji.agent.server.security.TianjiLinkedServiceAdmission;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentSubmissionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LinkedAgentDispatchControllerTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private static final String HASH = "a".repeat(64);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AgentLinkedSubmissionService submissions = mock(AgentLinkedSubmissionService.class);
    private final TianjiAgentAccess access = mock(TianjiAgentAccess.class);
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
    private final TrustedAgentActor actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "worker", ISSUER.toString());
    private final ServiceIdentityPrincipal principal = principal("worker", "app-id", "worker-app", NOW.plusSeconds(300));
    private final TianjiAgentAccess.Authorized authorized = new TianjiAgentAccess.Authorized(actor, NOW.plusSeconds(300), ISSUER);
    private final AgentProcessLink link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "process-1", "occurrence-1", HASH,
            NOW.plusSeconds(30));
    private final LinkedAgentStartCommand command = new LinkedAgentStartCommand(new StartAgentRunCommand("review", 1, null, "intent-1",
            "business", "trace-1", new AgentInputDTO("private-fixture", List.of(), Map.of()), null), link);
    private final LinkedAgentLookupQuery query = new LinkedAgentLookupQuery("intent-1", link);

    @BeforeEach void setup() {
        when(issuers.findByIssuer(ISSUER.toString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,
                URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20",
                TrustedIssuerRegistry.Status.ACTIVE, 1)));
        when(access.require(anyString())).thenReturn(authorized);
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(), ISSUER));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void recovery_works_while_submission_is_closed_by_default() throws Exception {
        var mvc = mvc(false, null);
        when(submissions.resolve(actor, query)).thenReturn(Optional.empty());
        when(submissions.close(actor, query)).thenReturn(new LinkedAgentSubmissionDTO(true, null));
        mvc.perform(post(path("submit")).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"));
        verify(submissions, never()).submit(any(), any());
        mvc.perform(post(path("resolve")).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(query)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.found").value(false)).andExpect(jsonPath("$.query.idempotencyKey").value("intent-1"));
        mvc.perform(post(path("close")).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(query)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.submission.closed").value(true));
    }

    @Test void submission_requires_admission_and_preserves_the_fence_on_an_uncertain_authorization() throws Exception {
        var admission = mock(TianjiLinkedServiceAdmission.class);
        var mvc = mvc(true, admission); var run = run();
        when(submissions.submit(actor, command)).thenReturn(run);
        when(submissions.resolve(actor, query)).thenReturn(Optional.of(new LinkedAgentSubmissionDTO(false, run)));
        doThrow(new IllegalStateException("private-details")).doNothing().when(admission).ensure(any(), any(), any(), any(), any());
        String body = mapper.writeValueAsString(command);
        mvc.perform(post(path("submit")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"));
        mvc.perform(post(path("submit")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.run.runId").value(run.runId()))
                .andExpect(jsonPath("$.query.link.activityOccurrenceId").value("occurrence-1"));
        verify(submissions, times(2)).submit(actor, command);
        verify(admission, times(2)).ensure(eq(authorized), same(principal), any(), eq(command), eq(run));
        when(submissions.resolve(actor, query)).thenReturn(Optional.of(new LinkedAgentSubmissionDTO(true, run)));
        mvc.perform(post(path("submit")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_DISPATCH_CLOSED"));
        assertThrows(IllegalStateException.class, () -> controller(true, null));
    }

    @Test void strict_body_validation_rejects_ambiguous_or_oversized_linked_requests() throws Exception {
        var mvc = mvc(false, null); String body = mapper.writeValueAsString(command);
        for (String bad : List.of(body.replace("\"sourceId\":\"process-east\"", "\"sourceId\":\"process-east\",\"sourceId\":\"other\""),
                body.replace("\"scopeType\":\"BPMN\"", "\"scopeType\":0"),
                body.replace("\"definitionVersion\":1", "\"definitionVersion\":\"1\""),
                body.replace("\"dispatchNotAfter\":\"2026-10-29T00:00:30Z\"", "\"dispatchNotAfter\":123"),
                body.substring(0, body.length() - 1) + ",\"unexpected\":true}", body + " {}")) {
            assertNotEquals(body, bad);
            mvc.perform(post(path("submit")).contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(path("resolve")).contentType(MediaType.APPLICATION_JSON).content(" ".repeat(16_385)))
                .andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(submissions);
    }

    @Test void bindings_reject_identity_source_scope_and_definition_drift() {
        var bindings = bindings(true);
        assertEquals("process-east", bindings.require(authorized, link, command).binding().sourceId());
        var otherSource = new AgentProcessLink("other", WorkerScopeTypeEnum.BPMN, "process-1", "occurrence-1", HASH, link.dispatchNotAfter());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> bindings.require(authorized, otherSource, null));
        var otherScope = new AgentProcessLink("process-east", WorkerScopeTypeEnum.CMMN, "process-1", "occurrence-1", HASH, link.dispatchNotAfter());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> bindings.require(authorized, otherScope, null));
        var otherHash = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "process-1", "occurrence-1", "b".repeat(64), link.dispatchNotAfter());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> bindings.require(authorized, otherHash, new LinkedAgentStartCommand(command.start(), otherHash)));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal("worker", "different", "worker-app", NOW.plusSeconds(300)), List.of(), ISSUER));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> bindings.require(authorized, link, null));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(), ISSUER));
        var forged = new TianjiAgentAccess.Authorized(new TrustedAgentActor(20, TrustedAgentActor.SubjectType.SERVICE, "worker", "b".repeat(64)),
                NOW.plusSeconds(300), ISSUER);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> bindings.require(forged, link, null));
        when(issuers.findByIssuer(ISSUER.toString())).thenReturn(Optional.empty());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> bindings.require(authorized, link, null));
        SecurityContextHolder.clearContext();
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> bindings.require(authorized, link, null));
        assertThrows(IllegalArgumentException.class, () -> new TianjiLinkedDispatchProperties(true, true, List.of(binding(), binding())));
    }

    @Test void submission_does_not_acknowledge_an_identity_that_expired_during_admission() {
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW, NOW.plusSeconds(301));
        var configured = properties(true); var admission = mock(TianjiLinkedServiceAdmission.class);
        var beans = new DefaultListableBeanFactory(); beans.registerSingleton("admission", admission);
        var controller = new LinkedAgentDispatchController(submissions, access, new TianjiLinkedDispatchBindings(configured, issuers, clock),
                configured, beans.getBeanProvider(TianjiLinkedServiceAdmission.class));
        var run = run();
        when(submissions.submit(actor, command)).thenReturn(run);
        when(submissions.resolve(actor, query)).thenReturn(Optional.of(new LinkedAgentSubmissionDTO(false, run)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> controller.submit(command));
        verify(submissions).submit(actor, command);
    }

    private MockMvc mvc(boolean enabled, TianjiLinkedServiceAdmission admission) {
        return MockMvcBuilders.standaloneSetup(controller(enabled, admission)).setControllerAdvice(new AgentApiErrors(), new AgentRequestBodyAdvice()).build();
    }
    private LinkedAgentDispatchController controller(boolean enabled, TianjiLinkedServiceAdmission admission) {
        var factory = new DefaultListableBeanFactory();
        if (admission != null) factory.registerSingleton("admission", admission);
        return new LinkedAgentDispatchController(submissions, access, bindings(enabled), properties(enabled),
                factory.getBeanProvider(TianjiLinkedServiceAdmission.class));
    }
    private TianjiLinkedDispatchBindings bindings(boolean enabled) {
        return new TianjiLinkedDispatchBindings(properties(enabled), issuers, Clock.fixed(NOW, ZoneOffset.UTC));
    }
    private TianjiLinkedDispatchProperties properties(boolean enabled) { return new TianjiLinkedDispatchProperties(true, enabled, List.of(binding())); }
    private TianjiLinkedDispatchProperties.Binding binding() {
        return new TianjiLinkedDispatchProperties.Binding(ISSUER, 20, "worker", "app-id", "xingyuan", "worker-app", "test",
                "process-east", Set.of(WorkerScopeTypeEnum.BPMN), List.of(new TianjiLinkedDispatchProperties.Definition("review", 1, HASH)));
    }
    private static ServiceIdentityPrincipal principal(String client, String appId, String app, Instant expires) {
        return new ServiceIdentityPrincipal(client, "20", client, "test-token-id", URI.create("https://api.example/tianji"), 1,
                Set.of(TianjiAgentPermissions.LINKED_SUBMIT_SCOPE, TianjiAgentPermissions.LINKED_RESOLVE_SCOPE, TianjiAgentPermissions.LINKED_CLOSE_SCOPE),
                "xingyuan", app, "test", "test-credential-id", NOW.minusSeconds(60), expires, appId, ServiceTokenContext.TENANT);
    }
    private static AgentRunDTO run() {
        return new AgentRunDTO("00000000-0000-0000-0000-000000000001", "review", 1, null, AgentRunStatusEnum.QUEUED,
                null, false, 0, 0, 1, NOW.plusSeconds(60), NOW, null);
    }
    private static String path(String operation) { return "/api/tianji/v1/agent-process-dispatches/" + operation; }
}
