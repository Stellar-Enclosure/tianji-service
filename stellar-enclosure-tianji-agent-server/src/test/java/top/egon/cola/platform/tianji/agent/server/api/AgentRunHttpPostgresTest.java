package top.egon.cola.platform.tianji.agent.server.api;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import jakarta.validation.Validation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.component.agentflow.workflow.AgentWorkflowStrategyFactory;
import top.egon.cola.component.agentflow.workflow.SequentialAgentWorkflowBuilderStrategy;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunDispatcher;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.application.session.AgentSessionManager;
import top.egon.cola.platform.tianji.agent.server.TianjiAgentServerApplication;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantIssuer;
import top.egon.cola.platform.tianji.agent.server.security.VerifiedAgentIssuerFilter;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.CurrentEffectivePermissionView;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;

import java.math.BigDecimal;
import javax.sql.DataSource;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses only a separately created, empty loopback database with the explicit test-name prefix. */
@EnabledIfSystemProperty(named = "tianji.http.postgres.test.url", matches = ".+")
@Timeout(60)
class AgentRunHttpPostgresTest {
    private static final String BASE = "/api/tianji/v1/agent-runs";
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(40001, TrustedAgentActor.SubjectType.SERVICE,
            "worker", "https://idp.example/tenants/40001");
    private static AgentLedgerService ledger;
    private static AgentSessionManager sessions;
    private static DriverManagerDataSource source;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private MockMvc mvc;

    @BeforeAll
    static void migrateFreshDatabase() {
        String url = System.getProperty("tianji.http.postgres.test.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("HTTP integration tests require a loopback tianji_it_ database");
        }
        var dataSource = new DriverManagerDataSource();
        dataSource.setUrl(url);
        source = dataSource;
        if (new JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM pg_namespace WHERE nspname = 'tianji_agent'", Integer.class) != 0) {
            throw new IllegalStateException("HTTP integration tests require an empty database");
        }
        var flyway = Flyway.configure().dataSource(dataSource).schemas("tianji_agent")
                .defaultSchema("tianji_agent").locations("classpath:db/tianji-agent").target("9").cleanDisabled(true).load();
        flyway.migrate();
        flyway.validate();
        ledger = new AgentLedgerService(dataSource);
        sessions = new AgentSessionManager(ledger, dataSource);
        var policy = new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(20), Duration.ofSeconds(10),
                5, 0, 1000, 500, new BigDecimal("2.00"), "USD", 2, Set.of(), null, null);
        ledger.publish(OWNER, new AgentDefinitionSpecDTO("review", 1, "SEQUENTIAL", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "model-route", "policy-v1"),
                policy, Map.of()));
    }

    @BeforeEach
    void setup() {
        authenticate("40001", "worker");
        var access = new TianjiAgentAccess(mock(PermissionExecutionGuard.class), Clock.systemUTC());
        mvc = MockMvcBuilders.standaloneSetup(new AgentRunController(ledger, access),
                new AgentRunSseController(access, new AgentRunEventStream(ledger, access, Clock.systemUTC())),
                new AgentSessionController(sessions, access))
                .addFilters(new VerifiedAgentIssuerFilter())
                .setControllerAdvice(new AgentApiErrors(), new AgentRequestBodyAdvice()).build();
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void http_commands_replay_and_cross_tenant_isolation_use_the_real_transactional_ledger() throws Exception {
        var start = new StartAgentRunCommand("review", 1, null, "request-1", null, "trace-1",
                new AgentInputDTO("question", List.of(), Map.of()), null);
        byte[] payload = mapper.writeValueAsBytes(start);
        String body = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        AgentRunDTO run = mapper.readValue(body, AgentRunDTO.class);
        String repeated = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals(run.runId(), mapper.readValue(repeated, AgentRunDTO.class).runId());
        mvc.perform(get(BASE).param("limit", "10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].runId").value(run.runId()));

        authenticate("40002", "worker");
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get(BASE + "/" + run.runId())).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/" + run.runId() + "/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());
        authenticate("40001", "other");
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get(BASE + "/" + run.runId())).andExpect(status().isNotFound());
        authenticate("40001", "worker", "https://another-idp.example/tenants/40001");
        mvc.perform(get(BASE + "/" + run.runId())).andExpect(status().isNotFound());
        authenticate("40001", "worker");

        var cancel = new CancelAgentRunCommand(run.runId(), "cancel-1", run.revision(), "user request");
        byte[] cancellation = mapper.writeValueAsBytes(cancel);
        String cancelled = mvc.perform(post(BASE + "/" + run.runId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content(cancellation))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andReturn().getResponse().getContentAsString();
        String replayed = mvc.perform(post(BASE + "/" + run.runId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content(cancellation))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(cancelled), mapper.readTree(replayed));
        mvc.perform(get(BASE + "/" + run.runId() + "/result"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        String events = mvc.perform(get(BASE + "/" + run.runId() + "/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(events.contains("id: 1\nevent: agent.event"));
        assertTrue(events.contains("id: 2\nevent: agent.event"));
        assertTrue(events.endsWith("event: stream.complete\n\n"));
        String resumed = mvc.perform(get(BASE + "/" + run.runId() + "/events")
                        .header("Last-Event-ID", "1").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(resumed.contains("id: 1\n"));
        assertTrue(resumed.contains("id: 2\n"));
        assertTrue(resumed.endsWith("event: stream.complete\n\n"));
    }

    @Test
    void one_http_start_projects_and_replays_two_durable_deltas_with_atomic_completion() throws Exception {
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(12), Duration.ofSeconds(10),
                2, 0, 100, 40, BigDecimal.ONE, "USD", 2, Set.of(), null, null);
        var configuration = Map.<String, Object>of(
                "chatModelBeanName", "s01Model", "modelName", "s01-model", "rootAgentName", "writer",
                "agents", List.of(Map.of("name", "writer", "instruction", "Write a two-part fixture response.")));
        ledger.publish(OWNER, new AgentDefinitionSpecDTO("s01-stream", 1, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "s01-route", "s01-policy"),
                policy, configuration));
        var command = new StartAgentRunCommand("s01-stream", 1, null, "s01-once", null, "s01-stream",
                new AgentInputDTO("fixture question", List.of(), Map.of()), null);
        String accepted = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(command))).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        var queued = mapper.readValue(accepted, AgentRunDTO.class);
        assertEquals(AgentRunStatusEnum.QUEUED, queued.status());
        assertEquals(0, queued.attempt());
        var query = new GetAgentRunQuery(queued.runId());
        var model = new TwoChunkModel();

        try (var validators = Validation.buildDefaultValidatorFactory();
             var control = Executors.newScheduledThreadPool(2);
             var worker = Executors.newSingleThreadExecutor()) {
            var validator = new AgentFlowConfigValidator(new ValidationUtils(validators.getValidator()));
            var factory = new AgentFlowFactory(new SpringAiModelAdapterFactory(),
                    new AgentWorkflowStrategyFactory(List.of(new SequentialAgentWorkflowBuilderStrategy())));
            var executor = new AdkAgentRunExecutor(ledger, factory, validator, Map.of("s01Model", model), Map.of(),
                    control, s01Budget(), null, false, (request, boundary) -> AgentRunAuthorization.Decision.ALLOW);
            try (var dispatcher = new AgentRunDispatcher(worker, executor, ledger)) {
                var completion = dispatcher.dispatch(OWNER, query).toCompletableFuture();
                try {
                    assertTrue(model.entered.await(8, TimeUnit.SECONDS), "ADK must enter the Fake stream");
                    assertEquals(AgentRunStatusEnum.RUNNING, ledger.get(OWNER, query).status());
                    assertTrue(ledger.getResult(OWNER, query).isEmpty());
                } finally {
                    model.release.countDown();
                }
                var finished = completion.get(15, TimeUnit.SECONDS);
                assertEquals(AgentRunStatusEnum.COMPLETED, finished.status());
                assertEquals(1, finished.attempt());
                assertAtomicCompletion(finished);
                assertReplayAndResult(finished);
                assertEquals(1, model.subscriptions.get(), "reopening SSE cannot invoke the model");
            }
        }
    }

    private void assertReplayAndResult(AgentRunDTO finished) throws Exception {
        String runPath = BASE + "/" + finished.runId();
        String full = mvc.perform(get(runPath + "/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var events = readEvents(full, finished.runId(), 0);
        assertEquals(finished.lastEventSequence(), events.getLast().sequence());
        var deltas = events.stream().filter(event -> "TEXT_DELTA".equals(event.type())).toList();
        assertEquals(List.of("First ", "second."), deltas.stream().map(event -> event.payload().get("text")).toList());
        var terminal = events.getLast();
        assertEquals("RUN_STATUS_CHANGED", terminal.type());
        assertEquals("COMPLETED", terminal.payload().get("status"));
        long cursor = deltas.getFirst().sequence();
        String replay = mvc.perform(get(runPath + "/events").accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Last-Event-ID", Long.toString(cursor)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(events.stream().filter(event -> event.sequence() > cursor).toList(),
                readEvents(replay, finished.runId(), cursor));
        String body = mvc.perform(get(runPath + "/result")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var result = mapper.readValue(body, AgentExecutionResult.class);
        assertEquals(finished.runId(), result.runId());
        assertEquals(AgentRunStatusEnum.COMPLETED, result.status());
        assertEquals("First second.", result.output().get("text"));
    }

    private List<AgentEventDTO> readEvents(String body, String runId, long after) {
        assertTrue(body.endsWith("event: stream.complete\n\n"), "stream must close at the terminal watermark");
        String[] frames = body.split("\n\n");
        assertEquals("event: stream.complete", frames[frames.length - 1]);
        var events = new ArrayList<AgentEventDTO>();
        long expected = after;
        for (int index = 0; index < frames.length - 1; index++) {
            String[] fields = frames[index].split("\n", -1);
            assertEquals(3, fields.length);
            assertEquals("id: " + ++expected, fields[0]);
            assertEquals("event: agent.event", fields[1]);
            assertTrue(fields[2].startsWith("data: "));
            var event = mapper.readValue(fields[2].substring("data: ".length()), AgentEventDTO.class);
            assertEquals(expected, event.sequence());
            assertEquals(runId, event.runId());
            events.add(event);
        }
        assertFalse(events.isEmpty());
        return List.copyOf(events);
    }

    private static void assertAtomicCompletion(AgentRunDTO finished) {
        assertEquals(Boolean.TRUE, new JdbcTemplate(source).queryForObject("""
                SELECT r.status = 'COMPLETED' AND r.result->>'status' = 'COMPLETED'
                   AND r.result->>'runId' = r.run_id AND r.attempt = 1
                   AND r.result#>>'{output,text}' = 'First second.'
                   AND e.event_type = 'RUN_STATUS_CHANGED' AND e.payload->>'status' = 'COMPLETED'
                   AND r.xmin = e.xmin
                FROM tianji_agent.agent_run r
                JOIN tianji_agent.agent_event e ON e.tenant_id = r.tenant_id AND e.run_id = r.run_id
                   AND e.sequence = r.last_event_sequence
                WHERE r.tenant_id = ? AND r.subject_type = ? AND r.subject_issuer = ? AND r.subject_id = ?
                   AND r.idempotency_key = 's01-once' AND r.run_id = ? AND r.last_event_sequence = ?
                """, Boolean.class, OWNER.tenantId(), OWNER.subjectType().name(), OWNER.issuerFingerprint(),
                OWNER.subjectId(), finished.runId(), finished.lastEventSequence()));
    }

    private static AgentModelBudgetEstimator s01Budget() {
        return new AgentModelBudgetEstimator() {
            @Override
            public Estimate estimate(AgentExecutionSnapshot snapshot, LlmRequest request) {
                return new Estimate(20, 30, new BigDecimal("0.10"));
            }

            @Override
            public ContextWindow contextWindow(AgentExecutionSnapshot snapshot, LlmRequest request) {
                return new ContextWindow(request.model().orElseThrow(), snapshot.definition().dependencies().modelRouteRef(),
                        snapshot.definition().dependencies().policyVersionRef(), 1_000, 10);
            }

            @Override
            public AgentBudgetSettlement actualUsage(AgentExecutionSnapshot snapshot, LlmResponse response) {
                return response.usageMetadata().map(usage -> new AgentBudgetSettlement(
                        usage.promptTokenCount().map(Integer::longValue).orElse(null),
                        usage.candidatesTokenCount().map(Integer::longValue).orElse(null), new BigDecimal("0.05")))
                        .orElseGet(() -> new AgentBudgetSettlement(null, null, null));
            }
        };
    }

    private static final class TwoChunkModel implements ChatModel {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger subscriptions = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new AssertionError("The S01 fixture must use the streaming model");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Mono.fromCallable(() -> {
                subscriptions.incrementAndGet();
                entered.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS), "The fixture must release the Fake stream");
                return Boolean.TRUE;
            }).thenMany(Flux.just(response("First ", ""), response("second.", "stop")));
        }

        @Override
        public ChatOptions getOptions() {
            return ToolCallingChatOptions.builder().model("s01-model").build();
        }

        private static ChatResponse response(String text, String finishReason) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text),
                    ChatGenerationMetadata.builder().finishReason(finishReason).build())),
                    ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5, 15)).build());
        }
    }

    @Test
    void session_http_create_replays_and_active_runs_block_owner_deletion() throws Exception {
        String sessionsBase = "/api/tianji/v1/agent-sessions";
        String id = "会话/with +😀";
        String created = mvc.perform(put(sessionsBase).param("sessionId", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(id)).andReturn().getResponse().getContentAsString();
        String repeated = mvc.perform(put(sessionsBase).param("sessionId", id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(created), mapper.readTree(repeated));
        authenticate("40002", "worker");
        mvc.perform(get(sessionsBase + "/detail").param("sessionId", id)).andExpect(status().isNotFound());
        mvc.perform(delete(sessionsBase).param("sessionId", id)).andExpect(status().isNotFound());
        authenticate("40001", "worker", "https://another-idp.example/tenants/40001");
        mvc.perform(get(sessionsBase + "/detail").param("sessionId", id)).andExpect(status().isNotFound());
        mvc.perform(delete(sessionsBase).param("sessionId", id)).andExpect(status().isNotFound());
        authenticate("40001", "worker");
        var start = new StartAgentRunCommand("review", 1, id, "session-run", null, null,
                new AgentInputDTO("question", List.of(), Map.of()), null);
        String body = mvc.perform(post(BASE + "/with-session").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(start)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        AgentRunDTO run = mapper.readValue(body, AgentRunDTO.class);
        mvc.perform(delete(sessionsBase).param("sessionId", id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIANJI_SESSION_BUSY"));
        var cancel = new CancelAgentRunCommand(run.runId(), "session-cancel", run.revision(), "test");
        mvc.perform(post(BASE + "/" + run.runId() + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(cancel))).andExpect(status().isOk());
        mvc.perform(delete(sessionsBase).param("sessionId", id)).andExpect(status().isNoContent());
        mvc.perform(get(sessionsBase + "/detail").param("sessionId", id)).andExpect(status().isNotFound());
    }

    @Test
    void retired_direct_start_replays_only_original_http_facts_without_writing() throws Exception {
        String sessionsBase = "/api/tianji/v1/agent-sessions";
        String suffix = UUID.randomUUID().toString();
        String sessionId = "retired-session-" + suffix;
        String unknownSessionId = "retired-unknown-session-" + suffix;
        String rootKey = "retired-root-" + suffix;
        String sessionKey = "retired-session-run-" + suffix;
        String unknownRootKey = "retired-missing-root-" + suffix;
        String unknownSessionKey = "retired-missing-session-" + suffix;

        mvc.perform(put(sessionsBase).param("sessionId", sessionId)).andExpect(status().isOk());
        mvc.perform(put(sessionsBase).param("sessionId", unknownSessionId)).andExpect(status().isOk());
        var rootCommand = new StartAgentRunCommand("review", 1, null, rootKey, null, "trace-retired-root",
                new AgentInputDTO("original root", List.of(), Map.of()), null);
        var sessionCommand = new StartAgentRunCommand("review", 1, sessionId, sessionKey, null, "trace-retired-session",
                new AgentInputDTO("original session", List.of(), Map.of()), null);
        String rootBody = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(rootCommand)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String sessionBody = mvc.perform(post(BASE + "/with-session").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(sessionCommand)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        AgentRunDTO rootRun = mapper.readValue(rootBody, AgentRunDTO.class);
        AgentRunDTO sessionRun = mapper.readValue(sessionBody, AgentRunDTO.class);

        mvc = retiredMvc();
        String rootReplay = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(rootCommand)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String sessionReplay = mvc.perform(post(BASE + "/with-session").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(sessionCommand)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(rootBody), mapper.readTree(rootReplay));
        assertEquals(mapper.readTree(sessionBody), mapper.readTree(sessionReplay));
        assertEquals(rootRun.runId(), mapper.readValue(rootReplay, AgentRunDTO.class).runId());
        assertEquals(sessionRun.runId(), mapper.readValue(sessionReplay, AgentRunDTO.class).runId());

        var changedRoot = new StartAgentRunCommand("review", 1, null, rootKey, null, "trace-retired-root",
                new AgentInputDTO("changed root", List.of(), Map.of()), null);
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(changedRoot)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIANJI_IDEMPOTENCY_CONFLICT"));
        var missingRoot = new StartAgentRunCommand("review", 1, null, unknownRootKey, null, null,
                new AgentInputDTO("unseen root", List.of(), Map.of()), null);
        var missingSession = new StartAgentRunCommand("review", 1, unknownSessionId, unknownSessionKey, null, null,
                new AgentInputDTO("unseen session", List.of(), Map.of()), null);
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(missingRoot)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        mvc.perform(post(BASE + "/with-session").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(missingSession)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));

        var jdbc = new JdbcTemplate(source);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_run WHERE run_id = ?", Integer.class,
                rootRun.runId()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_run WHERE run_id = ?", Integer.class,
                sessionRun.runId()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_event WHERE run_id = ?", Integer.class,
                rootRun.runId()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_event WHERE run_id = ?", Integer.class,
                sessionRun.runId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_run WHERE idempotency_key IN (?, ?, ?)",
                Integer.class, unknownRootKey, unknownSessionKey, "unused-" + suffix));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_run_execution_grant WHERE run_id IN (?, ?)",
                Integer.class, rootRun.runId(), sessionRun.runId()));
    }

    @SuppressWarnings("unchecked")
    private MockMvc retiredMvc() {
        var access = new TianjiAgentAccess(mock(PermissionExecutionGuard.class), Clock.systemUTC());
        ObjectProvider<TianjiExecutionGrantIssuer> issuer = mock(ObjectProvider.class);
        var environment = new MockEnvironment()
                .withProperty("stellar.tianji.agent.legacy.direct-start.enabled", "false");
        return MockMvcBuilders.standaloneSetup(new AgentRunController(ledger, access, issuer, environment),
                        new AgentRunSseController(access, new AgentRunEventStream(ledger, access, Clock.systemUTC())),
                        new AgentSessionController(sessions, access))
                .addFilters(new VerifiedAgentIssuerFilter())
                .setControllerAdvice(new AgentApiErrors(), new AgentRequestBodyAdvice()).build();
    }

    @Test
    void enabled_web_configuration_binds_session_manager_to_the_ledger_datasource() {
        WebApplicationContextRunner runner = new WebApplicationContextRunner().withUserConfiguration(TianjiAgentServerApplication.class)
                .withPropertyValues("stellar.tianji.agent.api.enabled=true",
                        "stellar.tianji.agent.legacy.direct-start.enabled=false",
                        "egon.cola.platform.tianquan.shoubing.register-filter=false",
                        "egon.cola.platform.tianquan.jianshen.register-filter=false",
                        "egon.cola.component.id.enabled=false",
                        "egon.cola.component.transactional-outbox.enabled=false",
                        "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4")
                .withBean(AgentLedgerService.class, () -> ledger)
                .withBean(PermissionExecutionGuard.class, () -> mock(PermissionExecutionGuard.class))
                .withBean(CurrentEffectivePermissionView.class, () -> mock(CurrentEffectivePermissionView.class))
                .withBean(IdpBearerAuthenticationFilter.class, () -> mock(IdpBearerAuthenticationFilter.class))
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> mock(Rbac3BearerAuthenticationFilter.class));
        runner.withBean(DataSource.class, () -> source).run(context -> {
            if (context.getStartupFailure() != null) {
                throw new AssertionError("Agent HTTP application did not start", context.getStartupFailure());
            }
            assertNotNull(context.getBean(AgentSessionManager.class));
            assertNotNull(context.getBean(AgentRunController.class));
        });
        runner.withBean(DataSource.class, () -> new DriverManagerDataSource(source.getUrl())).run(context -> {
            assertNotNull(context.getStartupFailure());
            Throwable cause = context.getStartupFailure();
            while (cause.getCause() != null) cause = cause.getCause();
            assertEquals("Agent sessions and runs require the same DataSource", cause.getMessage());
        });
    }

    private static void authenticate(String tenant, String subject) {
        authenticate(tenant, subject, "https://idp.example/tenants/" + tenant);
    }

    private static void authenticate(String tenant, String subject, String issuer) {
        Instant now = Instant.now();
        var identity = new ServiceIdentityPrincipal(subject, tenant, subject, "token-id",
                URI.create("https://api.example/tianji"), 1,
                Set.of("tianji:agent:read", "tianji:agent:start", "tianji:agent:cancel", "tianji:session:delete"),
                "xingyuan", subject, "test", "credential", now.minusSeconds(60), now.plusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(identity, List.of(),
                URI.create(issuer)));
    }
}
