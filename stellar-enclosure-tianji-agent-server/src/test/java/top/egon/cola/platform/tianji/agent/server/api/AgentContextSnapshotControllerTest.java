package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.agent.server.security.VerifiedAgentIssuerFilter;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotPageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotsQuery;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentContextSnapshotControllerTest {
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final PermissionExecutionGuard permissions = mock(PermissionExecutionGuard.class);
    private final TrustedAgentActor actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://idp.example/20");
    private MockMvc mvc;
    private AgentContextSnapshotController controller;

    @BeforeEach void setup() {
        var now = Instant.now();
        var principal = new ServiceIdentityPrincipal("worker", "20", "worker", "token", URI.create("https://api.example/tianji"), 1,
                Set.of(TianjiAgentPermissions.READ_SCOPE), "xingyuan", "worker", "test", "credential", now.minusSeconds(30), now.plusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(), URI.create("https://idp.example/20")));
        controller = new AgentContextSnapshotController(ledger, new TianjiAgentAccess(permissions, Clock.systemUTC()));
        mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(new VerifiedAgentIssuerFilter()).setControllerAdvice(new AgentApiErrors()).build();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }

    @Test
    void metadata_is_disabled_until_both_api_and_capture_are_enabled() {
        new ApplicationContextRunner().withUserConfiguration(AgentContextSnapshotController.class).run(context -> assertTrue(context.getBeansOfType(AgentContextSnapshotController.class).isEmpty()));
        new ApplicationContextRunner().withUserConfiguration(AgentContextSnapshotController.class).withPropertyValues("stellar.tianji.agent.api.enabled=true")
                .run(context -> assertTrue(context.getBeansOfType(AgentContextSnapshotController.class).isEmpty()));
    }

    @Test
    void current_actor_and_read_resource_guard_precede_the_scoped_bounded_ledger_query() throws Exception {
        when(ledger.contextSnapshots(actor, new AgentContextSnapshotsQuery("run-one", 5, 1))).thenReturn(new AgentContextSnapshotPageDTO(List.of(), null));
        mvc.perform(get("/api/tianji/v1/agent-runs/run-one/context-snapshots").param("afterModelCall", "5").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.items").isArray());
        var order = inOrder(permissions, ledger);
        order.verify(permissions).requireExecution(TianjiAgentPermissions.CONTEXT_SNAPSHOTS_API);
        order.verify(ledger).contextSnapshots(actor, new AgentContextSnapshotsQuery("run-one", 5, 1));
    }

    @Test
    void invalid_page_bounds_do_not_reach_storage_and_dependency_errors_are_sanitized() throws Exception {
        mvc.perform(get("/api/tianji/v1/agent-runs/run-one/context-snapshots").param("limit", "6"))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(ledger);
        when(ledger.contextSnapshots(eq(actor), any())).thenThrow(new DataAccessResourceFailureException("private-storage-detail"));
        var result = mvc.perform(get("/api/tianji/v1/agent-runs/run-one/context-snapshots"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE")).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("private-storage-detail"));
    }

    @Test
    void permission_denial_stops_the_query_before_storage() {
        doThrow(new AccessDeniedException("controlled denial")).when(permissions).requireExecution(TianjiAgentPermissions.CONTEXT_SNAPSHOTS_API);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(VerifiedAgentIssuerFilter.class.getName() + ".issuer", "https://idp.example/20");
        RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        assertThrows(AccessDeniedException.class, () -> controller.list("run-one", 0, 5));
        verifyNoInteractions(ledger);
    }
}
