package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessAgentOutcomesQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO;
import top.egon.cola.platform.tianji.process.server.bridge.ProcessLinkedAgentOutcomeQuery;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProcessAgentOutcomesControllerTest {
    private static final String PATH = "/api/tianji/v1/process-instances/instance-1/agent-outcomes";
    private static final String RESOURCE = TianjiProcessPermissions.AGENT_OUTCOMES_API;
    private static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "owner",
            "https://idp.example/tenants/20");

    private final ProcessLinkedAgentOutcomeQuery query = mock(ProcessLinkedAgentOutcomeQuery.class);
    private final TianjiProcessAccess access = mock(TianjiProcessAccess.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new ProcessAgentOutcomesController(query, access)).setControllerAdvice(new ProcessApiErrors()).build();

    @Test
    void process_agent_outcomes_route_exists() {
        var flags = ProcessAgentOutcomesController.class.getAnnotation(ConditionalOnProperty.class);
        assertEquals("stellar.tianji.process", flags.prefix());
        assertEquals("true", flags.havingValue());
        assertFalse(flags.matchIfMissing());
        assertEquals(Set.of("api.enabled", "agent-results.enabled", "agent-results.owner-outcomes-enabled"), Set.of(flags.name()));
    }

    @Test
    void publishes_only_process_local_outcomes_under_the_current_actor() throws Exception {
        var recorded = Instant.parse("2026-11-05T00:00:00Z");
        var received = recorded.plusSeconds(1);
        var decided = received.plusSeconds(1);
        var item = new ProcessAgentOutcomePageDTO.ItemDTO("review",
                ProcessAgentOutcomePageDTO.DispatchState.ACKNOWLEDGED, ProcessAgentOutcomePageDTO.InboxState.APPLIED,
                AgentRunStatusEnum.COMPLETED, recorded, received, decided);
        var input = new GetProcessAgentOutcomesQuery("instance-1", null);
        when(access.require(RESOURCE)).thenReturn(OWNER);
        when(query.list(OWNER, input)).thenReturn(new ProcessAgentOutcomePageDTO("instance-1", decided, List.of(item), null));

        var body = mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.instanceId").value("instance-1"))
                .andExpect(jsonPath("$.items[0].activityId").value("review"))
                .andExpect(jsonPath("$.items[0].terminalStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].dispatchRecordedAt").value(recorded.toString()))
                .andReturn().getResponse().getContentAsString();
        var result = JsonMapper.builder().build().readTree(body);
        assertEquals(Set.of("instanceId", "observedAt", "items", "nextCursor"), Set.copyOf(result.propertyNames()));
        assertEquals(Set.of("activityId", "dispatchState", "inboxState", "terminalStatus",
                "dispatchRecordedAt", "receivedAt", "decidedAt"), Set.copyOf(result.get("items").get(0).propertyNames()));
        verify(access).require(RESOURCE);
        verify(query).list(OWNER, input);
    }

    @Test
    void rejects_unapproved_parameters_and_authority_before_querying() throws Exception {
        when(access.require(RESOURCE)).thenReturn(OWNER);
        for (var request : List.of(get(PATH).param("runId", "foreign"), get(PATH).param("cursor", ""),
                get(PATH).param("cursor", "v1." + "a".repeat(64), "v1." + "b".repeat(64)))) {
            mvc.perform(request).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        when(access.require(RESOURCE)).thenThrow(new AccessDeniedException("private details"));
        mvc.perform(get(PATH).param("sourceId", "foreign")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
        verifyNoInteractions(query);
    }

    @Test
    void missing_owner_or_unavailable_evidence_are_not_successful_empty_pages() throws Exception {
        when(access.require(RESOURCE)).thenReturn(OWNER);
        var input = new GetProcessAgentOutcomesQuery("instance-1", null);
        when(query.list(OWNER, input)).thenThrow(new ProcessResourceNotFoundException())
                .thenThrow(new IllegalStateException("private persistence details"));
        mvc.perform(get(PATH)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        var body = mvc.perform(get(PATH)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("private persistence details"));
    }
}
