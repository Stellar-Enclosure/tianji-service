package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunPageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.ListAgentRunsQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.net.URI;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentRunHttpTest {
    private static final String BASE = "/api/tianji/v1/agent-runs";
    private static final TrustedAgentActor ACTOR = new TrustedAgentActor(20, TrustedAgentActor.SubjectType.SERVICE, "worker");
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final PermissionExecutionGuard guard = mock(PermissionExecutionGuard.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Instant now = Instant.now();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var identity = new ServiceIdentityPrincipal("worker", "20", "worker", "token-id",
                URI.create("https://api.example/tianji"), 1, Set.of("tianji:agent:read", "tianji:agent:start"),
                "domain", "worker", "test", "credential", now.minusSeconds(30), now.plusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(identity, List.of(),
                URI.create("https://idp.example/issuer")));
        var access = new TianjiAgentAccess(guard, Clock.systemUTC());
        var stream = new AgentRunEventStream(ledger, access, Clock.systemUTC());
        mvc = MockMvcBuilders.standaloneSetup(new AgentRunController(ledger, access),
                new AgentRunSseController(access, stream))
                .setControllerAdvice(new AgentApiErrors(), new AgentRequestBodyAdvice()).build();
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void starts_under_the_verified_actor_and_maps_not_ready_and_not_found() throws Exception {
        var command = new StartAgentRunCommand("research", 1, null, "request-1", null, null,
                new AgentInputDTO("question", List.of(), Map.of()), null);
        when(ledger.start(eq(ACTOR), any())).thenReturn(run(AgentRunStatusEnum.QUEUED, 1));
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command)))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.runId").value("run-1"));
        verify(guard).requireExecution(TianjiAgentPermissions.START_API);
        verify(ledger).start(ACTOR, command);

        when(ledger.getResult(ACTOR, new GetAgentRunQuery("run-1"))).thenReturn(Optional.empty());
        mvc.perform(get(BASE + "/run-1/result")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIANJI_RESULT_NOT_READY"));
        when(ledger.get(ACTOR, new GetAgentRunQuery("other"))).thenThrow(
                new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "private detail"));
        String body = mvc.perform(get(BASE + "/other")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("private detail"));
    }

    @Test
    void rejects_an_oversized_command_before_it_reaches_the_ledger() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(new byte[AgentRequestBodyAdvice.MAX_BYTES + 1]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("TIANJI_REQUEST_TOO_LARGE"));
        verify(ledger, never()).start(any(), any());
        HttpInputMessage streamed = new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(new byte[AgentRequestBodyAdvice.MAX_BYTES + 1]); }
            @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
        };
        assertThrows(AgentRequestBodyAdvice.TooLarge.class,
                () -> new AgentRequestBodyAdvice().beforeBodyRead(streamed, null, StartAgentRunCommand.class, null));
    }

    @Test
    void list_uses_the_verified_actor_and_enforces_page_bounds() throws Exception {
        when(ledger.list(ACTOR, new ListAgentRunsQuery("cursor", 2)))
                .thenReturn(new AgentRunPageDTO(List.of(run(AgentRunStatusEnum.QUEUED, 1)), null));
        mvc.perform(get(BASE).param("cursor", "cursor").param("limit", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].runId").value("run-1"));
        verify(guard).requireExecution(TianjiAgentPermissions.LIST_API);
        verify(ledger).list(ACTOR, new ListAgentRunsQuery("cursor", 2));
        mvc.perform(get(BASE).param("limit", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void expected_tenant_can_only_detect_mismatch_and_never_grants_access() throws Exception {
        when(ledger.get(ACTOR, new GetAgentRunQuery("run-1"))).thenReturn(run(AgentRunStatusEnum.QUEUED, 1));
        mvc.perform(get(BASE + "/run-1").header("X-Egon-Tianji-Tenant", "21"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_TENANT_CONTEXT_CHANGED"));
        mvc.perform(get(BASE + "/run-1").header("X-Egon-Tianji-Tenant", "20", "21"))
                .andExpect(status().isConflict());
        verifyNoInteractions(ledger);
        mvc.perform(get(BASE + "/run-1").header("X-Egon-Tianji-Tenant", "20"))
                .andExpect(status().isOk());
        verify(ledger).get(ACTOR, new GetAgentRunQuery("run-1"));
    }

    @Test
    void replays_from_last_event_id_in_bounded_pages_and_completes_only_at_terminal_watermark() throws Exception {
        when(ledger.get(eq(ACTOR), any())).thenReturn(run(AgentRunStatusEnum.COMPLETED, 20));
        List<AgentEventDTO> events = events(20);
        when(ledger.events(eq(ACTOR), any())).thenAnswer(invocation -> {
            AgentEventsQuery query = invocation.getArgument(1);
            assertTrue(query.limit() <= 8);
            return events.stream().filter(event -> event.sequence() > query.afterSequence())
                    .limit(query.limit()).toList();
        });
        String body = mvc.perform(get(BASE + "/run-1/events").accept(MediaType.TEXT_EVENT_STREAM)
                        .param("afterSequence", "0").param("limit", "1000").header("Last-Event-ID", "2"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("id: 2\n"));
        assertTrue(body.contains("id: 3\nevent: agent.event\n"));
        assertTrue(body.contains("id: 20\nevent: agent.event\n"));
        assertTrue(body.endsWith("event: stream.complete\n\n"));
        assertEquals(18, body.lines().filter(line -> line.startsWith("id: ")).count());
        verify(guard, atLeast(3)).requireExecution(TianjiAgentPermissions.EVENTS_API);
    }

    @Test
    void rejects_a_missing_cursor_before_committing_the_stream() throws Exception {
        when(ledger.get(eq(ACTOR), any())).thenReturn(run(AgentRunStatusEnum.COMPLETED, 3));
        when(ledger.events(eq(ACTOR), any())).thenReturn(List.of(events(3).get(2)));
        mvc.perform(get(BASE + "/run-1/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isGone()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("TIANJI_EVENT_CURSOR_EXPIRED"));
    }

    @Test
    void authorization_revocation_stops_an_already_started_stream() throws Exception {
        when(ledger.get(eq(ACTOR), any())).thenReturn(run(AgentRunStatusEnum.COMPLETED, 9));
        List<AgentEventDTO> events = events(9);
        when(ledger.events(eq(ACTOR), any())).thenAnswer(invocation -> {
            AgentEventsQuery query = invocation.getArgument(1);
            return events.stream().filter(event -> event.sequence() > query.afterSequence())
                    .limit(query.limit()).toList();
        });
        AtomicInteger checks = new AtomicInteger();
        doAnswer(invocation -> {
            if (checks.incrementAndGet() == 3) throw new AccessDeniedException("revoked");
            return null;
        }).when(guard).requireExecution(TianjiAgentPermissions.EVENTS_API);
        String body = mvc.perform(get(BASE + "/run-1/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("id: 8\n"));
        assertFalse(body.contains("id: 9\n"));
        assertFalse(body.contains("stream.complete"));
        assertEquals(3, checks.get());
    }

    private AgentRunDTO run(AgentRunStatusEnum status, long lastSequence) {
        return new AgentRunDTO("run-1", "research", 1, null, status, null, false, 1, 2,
                lastSequence, now.plusSeconds(60), now.minusSeconds(1),
                status == AgentRunStatusEnum.COMPLETED ? now : null);
    }

    private List<AgentEventDTO> events(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(sequence -> new AgentEventDTO("event-" + sequence,
                "run-1", sequence, "TEXT_DELTA", null, null, null, now, null, "1", Map.of("text", "ok")))
                .toList();
    }
}
