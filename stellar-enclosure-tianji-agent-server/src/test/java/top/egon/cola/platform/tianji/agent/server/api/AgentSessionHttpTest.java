package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.egon.cola.platform.tianji.agent.application.session.AgentSessionManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentSessionDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentSessionListDTO;
import top.egon.cola.platform.tianji.contract.agent.CreateAgentSessionCommand;
import top.egon.cola.platform.tianji.contract.agent.DeleteAgentSessionCommand;
import top.egon.cola.platform.tianji.contract.agent.GetAgentSessionQuery;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentSessionHttpTest {
    private static final String BASE = "/api/tianji/v1/agent-sessions";
    private static final TrustedAgentActor ACTOR = new TrustedAgentActor(20, TrustedAgentActor.SubjectType.SERVICE, "worker");
    private final AgentSessionManager sessions = mock(AgentSessionManager.class);
    private final PermissionExecutionGuard guard = mock(PermissionExecutionGuard.class);
    private final Instant now = Instant.now();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var identity = new ServiceIdentityPrincipal("worker", "20", "worker", "token-id",
                URI.create("https://api.example/tianji"), 1,
                Set.of(TianjiAgentPermissions.READ_SCOPE, TianjiAgentPermissions.START_SCOPE,
                        TianjiAgentPermissions.SESSION_DELETE_SCOPE),
                "domain", "worker", "test", "credential", now.minusSeconds(30), now.plusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(identity, List.of(),
                URI.create("https://idp.example/issuer")));
        mvc = MockMvcBuilders.standaloneSetup(new AgentSessionController(sessions, new TianjiAgentAccess(guard, Clock.systemUTC())))
                .setControllerAdvice(new AgentApiErrors()).build();
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void operations_use_the_verified_actor_and_expose_only_session_metadata() throws Exception {
        String id = "report/会话 +😀";
        var metadata = new AgentSessionDTO(id, now);
        when(sessions.create(ACTOR, new CreateAgentSessionCommand(id))).thenReturn(metadata);
        when(sessions.get(ACTOR, new GetAgentSessionQuery(id))).thenReturn(metadata);
        when(sessions.list(ACTOR)).thenReturn(new AgentSessionListDTO(List.of(metadata)));
        mvc.perform(put(BASE).param("sessionId", id)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.sessionId").value(id)).andExpect(jsonPath("$.state").doesNotExist())
                .andExpect(jsonPath("$.events").doesNotExist());
        mvc.perform(get(BASE + "/detail").param("sessionId", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(id));
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].sessionId").value(id));
        mvc.perform(delete(BASE).param("sessionId", id)).andExpect(status().isNoContent());
        verify(guard).requireExecution(TianjiAgentPermissions.SESSION_CREATE_API);
        verify(guard).requireExecution(TianjiAgentPermissions.SESSION_GET_API);
        verify(guard).requireExecution(TianjiAgentPermissions.SESSION_LIST_API);
        verify(guard).requireExecution(TianjiAgentPermissions.SESSION_DELETE_API);
        verify(sessions).delete(ACTOR, new DeleteAgentSessionCommand(id));
    }

    @Test
    void active_session_and_tenant_mismatch_are_rejected() throws Exception {
        doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_SESSION_BUSY, "private detail"))
                .when(sessions).delete(ACTOR, new DeleteAgentSessionCommand("busy"));
        mvc.perform(delete(BASE).param("sessionId", "busy")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIANJI_SESSION_BUSY"));
        clearInvocations(sessions);
        mvc.perform(get(BASE).header("X-Egon-Tianji-Tenant", "21")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIANJI_TENANT_CONTEXT_CHANGED"));
        verifyNoInteractions(sessions);
    }
}
