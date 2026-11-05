package top.egon.cola.platform.tianji.agent.server.api;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.Acknowledgement;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeCommand;
import top.egon.cola.platform.tianji.agent.recovery.TianjiAgentCheckpointResumeConfiguration.ResumeHost;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.agent.server.security.TianjiCheckpointResumeGrantAuthorizer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiCheckpointResumeGrantHandoff;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest.PrincipalType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentCheckpointResumeControllerTest {
    private static final URI ISSUER = URI.create("https://idp.example/tenant");
    private static final TianjiAgentAccess.Authorized OWNER = new TianjiAgentAccess.Authorized(
            TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "user", ISSUER.toString()),
            Instant.parse("2026-11-07T00:00:00Z"), ISSUER);
    private static final String BODY = """
            {"runId":"run-1","commandId":"resume-1","expectedRevision":5}
            """;
    private static final ResumeCommand COMMAND = new ResumeCommand("run-1", "resume-1", 5);
    private static final Acknowledgement ACKNOWLEDGEMENT = new Acknowledgement("run-1", AgentRunStatusEnum.RUNNING, 1, 7);

    private final TianjiAgentAccess access = mock(TianjiAgentAccess.class);
    private final ResumeHost host = mock(ResumeHost.class);
    private final TianjiCheckpointResumeGrantHandoff handoff = mock(TianjiCheckpointResumeGrantHandoff.class);
    private final TianjiCheckpointResumeGrantAuthorizer authority = mock(TianjiCheckpointResumeGrantAuthorizer.class);
    private final CompletableFuture<Acknowledgement> completion = new CompletableFuture<>();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(host.usesAuthorization(authority)).thenReturn(true);
        when(access.require(TianjiAgentPermissions.CHECKPOINT_RESUME_API)).thenReturn(OWNER);
        when(handoff.submit(OWNER, COMMAND, host, authority)).thenReturn(completion.minimalCompletionStage());
        mvc = mvc(Duration.ofMillis(25));
    }

    private MockMvc mvc(Duration wait) {
        return MockMvcBuilders.standaloneSetup(new AgentCheckpointResumeController(access, host, handoff, authority, wait))
                .setControllerAdvice(new AgentApiErrors(), new AgentRequestBodyAdvice()).build();
    }

    @Test
    void admits_only_through_the_exact_guard_and_returns_the_original_acknowledgement() throws Exception {
        completion.complete(ACKNOWLEDGEMENT);
        mvc.perform(post(AgentCheckpointResumeController.PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("""
                        {"runId":"run-1","status":"RUNNING","attempt":1,"revision":7}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
        var order = inOrder(access, handoff);
        order.verify(access).require(TianjiAgentPermissions.CHECKPOINT_RESUME_API);
        order.verify(handoff).submit(OWNER, COMMAND, host, authority);
        verifyNoMoreInteractions(access, handoff);
    }

    @Test
    void rejects_services_denied_current_permission_and_query_parameters_before_handoff() throws Exception {
        var service = new TianjiAgentAccess.Authorized(
                TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "worker", ISSUER.toString()),
                OWNER.expiresAt(), ISSUER);
        when(access.require(TianjiAgentPermissions.CHECKPOINT_RESUME_API)).thenReturn(service);
        when(handoff.submit(service, COMMAND, host, authority)).thenReturn(CompletableFuture.completedStage(ACKNOWLEDGEMENT));
        mvc.perform(post(AgentCheckpointResumeController.PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store"));
        when(access.require(TianjiAgentPermissions.CHECKPOINT_RESUME_API)).thenThrow(new AccessDeniedException("denied"));
        assertInstanceOf(AccessDeniedException.class, assertThrows(ServletException.class, () ->
                mvc.perform(post(AgentCheckpointResumeController.PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))).getCause());

        reset(access);
        when(access.require(TianjiAgentPermissions.CHECKPOINT_RESUME_API)).thenReturn(OWNER);
        mvc.perform(post(AgentCheckpointResumeController.PATH).queryParam("tenantId", "20")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        verifyNoInteractions(handoff);
    }

    @Test
    void rejects_waiting_fields_wrong_shapes_and_nonintegral_or_missing_revisions() throws Exception {
        for (String invalid : List.of(
                BODY.replace("\"expectedRevision\":5", "\"expectedRevision\":5,\"waitToken\":\"external\""),
                BODY.replace("\"expectedRevision\":5", "\"expectedRevision\":5,\"approvalReference\":\"external\""),
                BODY.replace(",\"expectedRevision\":5", ""),
                BODY.replace("\"expectedRevision\":5", "\"expectedRevision\":5.0"),
                BODY.replace("\"expectedRevision\":5", "\"expectedRevision\":\"5\""),
                BODY.replace("\"expectedRevision\":5", "\"expectedRevision\":null"),
                BODY.replace("\"expectedRevision\":5", "\"expectedRevision\":5,\"expectedRevision\":6"),
                BODY.replace("\"expectedRevision\":5", "\"expectedRevision\":9223372036854775806"),
                BODY.replace("\"commandId\":\"resume-1\"", "\"commandId\":123"),
                BODY + "{}")) {
            mvc.perform(post(AgentCheckpointResumeController.PATH).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        verifyNoInteractions(access, handoff);
    }

    @Test
    void caps_fixed_length_and_streamed_commands_at_four_kibibytes() throws Exception {
        byte[] oversized = new byte[4_097];
        mvc.perform(post(AgentCheckpointResumeController.PATH).contentType(MediaType.APPLICATION_JSON).content(oversized))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("TIANJI_REQUEST_TOO_LARGE"));
        HttpInputMessage streamed = new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(oversized); }
            @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
        };
        assertThrows(AgentRequestBodyAdvice.TooLarge.class,
                () -> new AgentRequestBodyAdvice().beforeBodyRead(streamed, null, ResumeCommand.class, null));
        verifyNoInteractions(access, handoff);
    }

    @Test
    void returns_only_safe_errors_from_failed_dispatch() throws Exception {
        completion.completeExceptionally(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "private-cut-digest"));
        String response = mvc.perform(post(AgentCheckpointResumeController.PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_REVISION_CONFLICT"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("private-cut-digest"));
    }

    @Test
    void response_timeout_never_cancels_the_admitted_worker() throws Exception {
        mvc.perform(post(AgentCheckpointResumeController.PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"));
        assertFalse(completion.isDone(), "HTTP timeout must leave the worker acknowledgement stage untouched");
        assertTrue(completion.complete(ACKNOWLEDGEMENT));
    }

    @Test
    void interrupted_response_wait_preserves_interrupt_and_does_not_cancel_the_worker() {
        var controller = new AgentCheckpointResumeController(access, host, handoff, authority);
        Thread.currentThread().interrupt();
        try {
            var failure = assertThrows(AgentLedgerException.class, () -> controller.resume(COMMAND,
                    new org.springframework.util.LinkedMultiValueMap<>()));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code());
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(completion.isDone());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejects_misbound_host_on_construction() {
        var wrong = mock(ResumeHost.class);
        assertThrows(IllegalStateException.class,
                () -> new AgentCheckpointResumeController(access, wrong, handoff, authority));
        verifyNoInteractions(access, handoff);
    }

    @Test
    void declares_one_user_resource_with_exact_resume_and_read_requirements() throws Exception {
        var method = AgentCheckpointResumeController.class.getMethod("resume", ResumeCommand.class,
                org.springframework.util.MultiValueMap.class);
        var resource = method.getAnnotation(PermissionResource.class);
        assertEquals(TianjiAgentPermissions.CHECKPOINT_RESUME_API, resource.key());
        assertArrayEquals(new PrincipalType[]{PrincipalType.USER}, resource.allowedPrincipals());
        assertArrayEquals(new String[]{TianjiAgentPermissions.CHECKPOINT_RESUME_SCOPE, TianjiAgentPermissions.READ_SCOPE}, resource.oauthScopes());
        assertArrayEquals(new String[]{TianjiAgentPermissions.CHECKPOINT_RESUME, TianjiAgentPermissions.READ},
                method.getAnnotation(RequiresPermissions.class).codes());
    }
}
