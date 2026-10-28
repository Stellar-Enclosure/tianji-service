package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.EvaluateDecisionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelResource;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.PublishProcessReleaseCommand;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;
import top.egon.cola.platform.tianji.process.server.decision.ProcessDecisionService;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessDecisionFailedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianji.process.server.security.VerifiedProcessIssuerFilter;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProcessControllerTest {
    private static final ProcessActor ACTOR = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker", "https://idp.example/issuer");
    private final ProcessReleaseService releases = mock(ProcessReleaseService.class);
    private final ProcessExecutionService processes = mock(ProcessExecutionService.class);
    private final ProcessDecisionService decisions = mock(ProcessDecisionService.class);
    private final PermissionExecutionGuard guard = mock(PermissionExecutionGuard.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Instant now = Instant.now();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var identity = new ServiceIdentityPrincipal("worker", "20", "worker", "token-id",
                URI.create("https://api.example/tianji"), 1, Set.of("tianji:process:read"),
                "domain", "worker", "test", "credential", now.minusSeconds(30), now.plusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(identity, List.of(),
                URI.create("https://idp.example/issuer")));
        mvc = MockMvcBuilders.standaloneSetup(new ProcessController(releases, processes, decisions,
                        new TianjiProcessAccess(guard, Clock.systemUTC())))
                .addFilters(new VerifiedProcessIssuerFilter())
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void maps_the_five_operations_to_the_verified_actor_without_engine_ids_in_responses() throws Exception {
        var published = release();
        var snapshot = new ProcessExecutionService.Snapshot("instance-1", "v1", "invoice", "order-1", "trace-1",
                ProcessExecutionService.State.ACTIVE, now, null);
        var decision = new ProcessDecisionService.Result("12345678-1234-1234-1234-123456789abc", "v1", "risk",
                "trace-1", now, List.of(Map.of("route", "review")), List.of("row:1"));
        when(releases.publish(eq(ACTOR), eq("v1"), anyList())).thenReturn(published);
        when(releases.get(ACTOR, "v1")).thenReturn(published);
        when(processes.start(eq(ACTOR), any())).thenReturn(snapshot);
        when(processes.get(ACTOR, "instance-1")).thenReturn(snapshot);
        when(decisions.evaluate(eq(ACTOR), any())).thenReturn(decision);
        var publish = new PublishProcessReleaseCommand("v1", List.of(new ProcessModelResource(
                ProcessModelTypeEnum.BPMN, "invoice", "<xml/>", Map.of("amount", ProcessValueTypeEnum.INTEGER))));
        var start = new StartProcessCommand("invoice", "v1", "order-1", "cmd-1", "trace-1", Map.of("amount", 10));
        var evaluate = new EvaluateDecisionCommand("risk", "v1", "request-1", "trace-1", Map.of("amount", 10));
        String releaseBody = mvc.perform(post("/api/tianji/v1/process-releases").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(publish))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.releaseVersion").value("v1"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(releaseBody.contains("engine-definition"));
        mvc.perform(get("/api/tianji/v1/process-releases/v1")).andExpect(status().isOk());
        mvc.perform(post("/api/tianji/v1/process-instances").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(start))).andExpect(status().isOk()).andExpect(jsonPath("$.instanceId").value("instance-1"));
        mvc.perform(get("/api/tianji/v1/process-instances/instance-1")).andExpect(status().isOk());
        mvc.perform(post("/api/tianji/v1/decisions/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(evaluate))).andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedRuleIds[0]").value("row:1"));
        verify(guard).requireExecution(TianjiProcessPermissions.RELEASE_PUBLISH_API);
        verify(guard).requireExecution(TianjiProcessPermissions.RELEASE_GET_API);
        verify(guard).requireExecution(TianjiProcessPermissions.START_API);
        verify(guard).requireExecution(TianjiProcessPermissions.GET_API);
        verify(guard).requireExecution(TianjiProcessPermissions.DECISION_API);
        verify(processes).start(eq(ACTOR), argThat(command -> command.releaseKey().equals("v1")
                && command.variables().equals(Map.of("amount", 10L))));
    }

    @Test
    void guards_access_and_returns_sanitized_distinct_errors() throws Exception {
        doThrow(new AccessDeniedException("private details")).when(guard).requireExecution(TianjiProcessPermissions.GET_API);
        String denied = mvc.perform(get("/api/tianji/v1/process-instances/instance-1"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(denied.contains("private details"));
        verifyNoInteractions(processes);
        reset(guard);
        mvc.perform(get("/api/tianji/v1/process-instances/instance-1").header("X-Egon-Tianji-Tenant", "21"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_TENANT_CONTEXT_CHANGED"));
        verifyNoInteractions(processes);
        when(processes.get(ACTOR, "missing")).thenThrow(new ProcessResourceNotFoundException());
        mvc.perform(get("/api/tianji/v1/process-instances/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        when(processes.get(ACTOR, "database-error")).thenThrow(new IllegalStateException("private database detail"));
        String unavailable = mvc.perform(get("/api/tianji/v1/process-instances/database-error"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(unavailable.contains("private database detail"));
        when(processes.start(eq(ACTOR), any())).thenThrow(new ProcessConflictException());
        var start = new StartProcessCommand("invoice", "v1", "order", "cmd", "trace", Map.of());
        mvc.perform(post("/api/tianji/v1/process-instances").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(start))).andExpect(status().isConflict());
        when(decisions.evaluate(eq(ACTOR), any())).thenThrow(new ProcessDecisionFailedException());
        var decision = new EvaluateDecisionCommand("risk", "v1", "r", "trace", Map.of());
        mvc.perform(post("/api/tianji/v1/decisions/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(decision))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TIANJI_DECISION_FAILED"));
    }

    @Test
    void rejects_invalid_and_oversized_json_before_touching_services() throws Exception {
        mvc.perform(post("/api/tianji/v1/process-instances").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/tianji/v1/process-releases").contentType(MediaType.APPLICATION_JSON)
                        .content(new byte[ProcessRequestBodyAdvice.MAX_BYTES + 1]))
                .andExpect(status().isPayloadTooLarge());
        HttpInputMessage streamed = new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(new byte[ProcessRequestBodyAdvice.MAX_BYTES + 1]); }
            @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
        };
        assertThrows(ProcessRequestBodyAdvice.TooLarge.class,
                () -> new ProcessRequestBodyAdvice().beforeBodyRead(streamed, null, PublishProcessReleaseCommand.class, null));
        verifyNoInteractions(releases, processes, decisions);
    }

    private ProcessReleaseService.Release release() {
        return new ProcessReleaseService.Release("v1", "a".repeat(64), now, List.of(
                new ProcessReleaseService.Definition(ValidatedProcessModel.Type.BPMN, "invoice", "engine-definition", "engine-deployment",
                        1, "b".repeat(64), Map.of("amount", ValidatedProcessModel.ValueType.INTEGER))));
    }
}
