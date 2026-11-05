package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.Plugin;
import com.google.adk.sessions.InMemorySessionService;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.component.agentflow.runtime.AgentFlowSessionRunner;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.AgentRunTransitions;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.Execution;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.TakeoverResult;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentBudgetService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(15)
class AdkCheckpointResumeExecutorTest {
    private static final String APP = "tianji";
    private static final String USER = "20:USER:alice";
    private static final String SESSION = "fixture-session";
    private static final String ROOT = "root";
    private static final String ORIGINAL = "fixture first input";
    private static final String ANSWER = "fixture resumed answer";
    private static final String MODEL = "fixture-model";
    private static final String ROUTE = "route@1#" + "a".repeat(64);
    private static final String POLICY = "policy@1#" + "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private static final Instant CAP = NOW.plusSeconds(15);
    private static final Instant DEADLINE = NOW.plusSeconds(60);
    private static final GetAgentRunQuery QUERY = new GetAgentRunQuery("00000000-0000-0000-0000-000000000020");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER,
            "alice", "https://idp.example/20");
    private static final AgentRunLease LEASE = new AgentRunLease(QUERY.runId(), "resume-worker", 3, CAP);
    private static final RunConfig CONFIG = RunConfig.builder().setStreamingMode(RunConfig.StreamingMode.SSE)
            .setAutoCreateSession(false).build();

    @Test
    void resumes_the_exact_checkpoint_with_current_resume_and_model_and_no_renewal() throws Exception {
        try (var fixture = new Fixture()) {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            MDC.put("tenantId", "outer-tenant");
            MDC.put("userId", "outer-user");
            try {
                assertEquals(AgentRunStatusEnum.COMPLETED, fixture.execute().orElseThrow().status());
                assertEquals("outer-tenant", MDC.get("tenantId"));
                assertEquals("outer-user", MDC.get("userId"));
            } finally {
                MDC.clear();
                if (previous != null) MDC.setContextMap(previous);
            }
            var history = fixture.history();
            assertTrue(history.size() > fixture.prefix.size());
            assertEquals(fixture.prefix, history.subList(0, fixture.prefix.size()));
            assertEquals(1, history.stream().filter(event -> "user".equals(event.author())).count());
            assertTrue(history.stream().allMatch(event -> fixture.invocation.equals(event.invocationId())));
            assertEquals(1, fixture.model.calls.get());
            assertEquals(ANSWER, fixture.finished.get().output().get("text"));
            int reserved = fixture.actions.indexOf("reserve");
            assertTrue(reserved > 0);
            assertTrue(fixture.actions.subList(0, reserved).containsAll(List.of("RESUME", "MODEL")));
            assertTrue(fixture.actions.indexOf("model") > reserved);
            verify(fixture.budgets).reserveBudget(eq(ACTOR), eq(LEASE), any(AgentBudgetRequest.class));
            verify(fixture.budgets).settleBudget(eq(ACTOR), eq(LEASE), anyString(), any(AgentBudgetSettlement.class));
            assertTrue(fixture.execute().isEmpty(), "consumed receipt cannot execute again");
            assertEquals(history, fixture.history());
            assertEquals(1, fixture.model.calls.get());
            fixture.assertNoFreshExecutionOrRenewal();
            verify(fixture.bound).releaseSessionLease(fixture.sessionLease);
        }
    }

    @Test
    void denied_resume_never_reserves_budget_or_starts_the_model() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.resumeDecision.set(AgentRunAuthorization.Decision.DENY);
            assertEquals(AgentRunStatusEnum.SUSPENDED, fixture.execute().orElseThrow().status());
            assertTrue(fixture.actions.contains("RESUME"));
            fixture.assertNoModelOrBudget();
            verify(fixture.ledger).suspendForAuthorization(ACTOR, LEASE, AgentRunAuthorization.Decision.DENY);
        }
    }

    @Test
    void denied_model_is_not_replaced_by_an_allowed_resume_permit() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.modelDecision.set(AgentRunAuthorization.Decision.DENY);
            assertEquals(AgentRunStatusEnum.SUSPENDED, fixture.execute().orElseThrow().status());
            assertTrue(fixture.actions.contains("MODEL"));
            fixture.assertNoModelOrBudget();
            verify(fixture.ledger).suspendForAuthorization(ACTOR, LEASE, AgentRunAuthorization.Decision.DENY);
        }
    }

    @Test
    void acknowledgement_only_replay_does_not_open_the_session_or_consult_authority() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.launch.set(null);
            assertTrue(fixture.execute().isEmpty());
            assertTrue(fixture.actions.isEmpty());
            assertEquals(fixture.prefix, fixture.history());
            fixture.assertNoModelOrBudget();
            verifyNoInteractions(fixture.bound);
            verify(fixture.ledger, never()).executionMaterial(any(), any());
        }
    }

    @Test
    void an_expired_fixed_lease_is_not_a_run_deadline_or_a_model_call() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.clock.set(CAP);
            var failure = assertThrows(AgentLedgerException.class, fixture::execute);
            assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, failure.code());
            assertEquals(AgentRunStatusEnum.RUNNING, fixture.current.get().status());
            fixture.assertNoModelOrBudget();
            verify(fixture.ledger, never()).expireDeadline(any(), any(), anyLong());
            verify(fixture.ledger, never()).expireDeadline(any(), any(), any(), anyLong(), anyString());
            verify(fixture.ledger, never()).finish(any(), any(), anyLong(), any());
        }
    }

    @Test
    void periodic_resume_control_cancels_the_native_model_without_renewing_either_lease() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.model.block = true;
            var caller = Executors.newSingleThreadExecutor();
            try {
                var execution = caller.submit(fixture::execute);
                assertTrue(fixture.model.started.await(3, TimeUnit.SECONDS));
                var before = fixture.current.get();
                fixture.current.set(new AgentRunDTO(before.runId(), before.definitionKey(), before.definitionVersion(),
                        before.sessionId(), AgentRunStatusEnum.RUNNING, null, true, before.attempt(),
                        before.revision() + 1, before.lastEventSequence(), before.deadlineAt(), before.createdAt(), null));
                assertEquals(AgentRunStatusEnum.CANCELLED, execution.get(5, TimeUnit.SECONDS).orElseThrow().status());
                assertTrue(fixture.model.cancelled.await(3, TimeUnit.SECONDS));
                assertEquals(1, fixture.model.calls.get());
                assertEquals(AgentRunStatusEnum.CANCELLED, fixture.finished.get().status());
                fixture.assertNoFreshExecutionOrRenewal();
                verify(fixture.bound).releaseSessionLease(fixture.sessionLease);
            } finally {
                caller.shutdownNow();
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class);
        final ResumeAuthorization resumeAuthority = mock(ResumeAuthorization.class);
        final TakeoverResult result = mock(TakeoverResult.class);
        final Execution execution = mock(Execution.class);
        final PostgresAdkSessionService bound = mock(PostgresAdkSessionService.class);
        final PostgresAdkSessionService.SessionLease sessionLease = mock(PostgresAdkSessionService.SessionLease.class);
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final AgentBudgetService budgets = mock(AgentBudgetService.class);
        final AgentModelBudgetEstimator estimator = mock(AgentModelBudgetEstimator.class);
        final AgentFlowFactory flowFactory = mock(AgentFlowFactory.class);
        final AgentFlowConfigValidator validator = mock(AgentFlowConfigValidator.class);
        final AtomicReference<Instant> clock = new AtomicReference<>(NOW);
        final AtomicReference<AgentRunDTO> current = new AtomicReference<>(run(AgentRunStatusEnum.RUNNING, 5, 5));
        final AtomicReference<AgentExecutionResult> finished = new AtomicReference<>();
        final AtomicReference<AgentRunAuthorization.Decision> resumeDecision = new AtomicReference<>(AgentRunAuthorization.Decision.ALLOW);
        final AtomicReference<AgentRunAuthorization.Decision> modelDecision = new AtomicReference<>(AgentRunAuthorization.Decision.ALLOW);
        final AtomicReference<Execution> launch = new AtomicReference<>(execution);
        final List<String> actions = new CopyOnWriteArrayList<>();
        final InMemorySessionService storage = new InMemorySessionService(InMemorySessionService.DuplicateSessionIdBehavior.REJECT);
        final ScheduledExecutorService control = Executors.newScheduledThreadPool(2, task -> Thread.ofPlatform().daemon().unstarted(task));
        final AgentRunAuthorization.Request request;
        final AgentLedgerService.ExecutionMaterial material;
        final List<Event> prefix;
        final String invocation;
        final FakeModel model;

        Fixture() throws Exception {
            var policy = new ExecutionPolicyDTO(Duration.ofSeconds(60), Duration.ofSeconds(5), Duration.ofSeconds(2),
                    2, 0, 1000, 100, BigDecimal.ONE, "USD", 1, Set.of(), "retry", null);
            var definition = new AgentDefinitionSpecDTO("fixture", 1, "CONFIG", null, "1", "1",
                    new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), ROUTE, POLICY), policy,
                    Map.of("chatModelBeanName", "fixtureModel", "modelName", MODEL, "rootAgentName", ROOT));
            var snapshot = new AgentExecutionSnapshot(definition, "c".repeat(64), policy,
                    new AgentInputDTO(ORIGINAL, null, null), null);
            request = new AgentRunAuthorization.Request(ACTOR, QUERY.runId(), definition.definitionKey(), definition.version(),
                    "c".repeat(64), "d".repeat(64), "e".repeat(64), POLICY, true, DEADLINE);
            material = new AgentLedgerService.ExecutionMaterial(current.get(), snapshot, request);

            storage.createSession(APP, USER, Map.of(), SESSION).timeout(3, TimeUnit.SECONDS).blockingGet();
            cutOrdinaryProducer();
            prefix = history();
            assertEquals(1, prefix.size());
            assertEquals("user", prefix.getFirst().author());
            invocation = prefix.getFirst().invocationId();
            assertFalse(invocation.isBlank());
            model = new FakeModel(actions);

            when(result.consumeForExecution(source, resumeAuthority, false))
                    .thenAnswer(call -> Optional.ofNullable(launch.getAndSet(null)));
            when(execution.actor()).thenReturn(ACTOR);
            when(execution.runLease()).thenReturn(LEASE);
            when(execution.sessionLease()).thenReturn(sessionLease);
            when(execution.boundSession()).thenReturn(bound);
            when(execution.invocationId()).thenReturn(invocation);
            when(execution.request()).thenReturn(request);
            when(execution.matchesClaimed(material)).thenReturn(true);
            when(execution.authorizeResume(any(Instant.class))).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                actions.add("RESUME");
                return resumeDecision.get();
            });
            when(sessionLease.sessionId()).thenReturn(SESSION);
            when(sessionLease.expiresAt()).thenReturn(CAP);
            when(bound.adkUserId()).thenReturn(USER);
            when(bound.getSession(eq(APP), eq(USER), eq(SESSION), any()))
                    .thenAnswer(call -> storage.getSession(APP, USER, SESSION, call.getArgument(3)));
            when(bound.appendEvent(any(), any()))
                    .thenAnswer(call -> storage.appendEvent(call.getArgument(0), call.getArgument(1)));
            when(bound.releaseSessionLease(sessionLease)).thenReturn(true);
            when(ledger.usesDataSource(source)).thenReturn(true);
            when(ledger.get(ACTOR, QUERY)).thenAnswer(call -> current.get());
            when(ledger.executionMaterial(ACTOR, QUERY)).thenReturn(material);
            when(ledger.databaseNow()).thenAnswer(call -> clock.get());
            when(ledger.sessionContextHistory(eq(ACTOR), eq(LEASE), eq(request), eq(SESSION), any(Duration.class)))
                    .thenReturn(new AgentLedgerService.ContextHistory(List.of(), Set.of(), true, false, true));
            when(ledger.budgets()).thenReturn(budgets);
            when(budgets.reserveBudget(eq(ACTOR), eq(LEASE), any(AgentBudgetRequest.class))).thenAnswer(call -> {
                actions.add("reserve");
                AgentBudgetRequest budget = call.getArgument(2);
                return new AgentBudgetReservation(budget.reservationId(), LEASE.epoch(), budget.kind(), null,
                        budget.inputTokens(), budget.outputTokens(), budget.estimatedCost(),
                        AgentBudgetReservation.State.RESERVED, null, null, null, true);
            });
            when(budgets.settleBudget(eq(ACTOR), eq(LEASE), anyString(), any(AgentBudgetSettlement.class)))
                    .thenReturn(new AgentBudgetReservation("settled", LEASE.epoch(), AgentBudgetRequest.Kind.MODEL, null,
                            10, 5, BigDecimal.ZERO, AgentBudgetReservation.State.SETTLED, 10L, 3L, BigDecimal.ZERO, false));
            when(estimator.estimate(eq(snapshot), any())).thenReturn(new AgentModelBudgetEstimator.Estimate(10, 5, BigDecimal.ZERO));
            when(estimator.contextWindow(eq(snapshot), any())).thenReturn(new AgentModelBudgetEstimator.ContextWindow(MODEL, ROUTE, POLICY, 1000, 5));
            when(estimator.actualUsage(eq(snapshot), any())).thenReturn(new AgentBudgetSettlement(10L, 3L, BigDecimal.ZERO));
            when(ledger.appendEvent(eq(ACTOR), eq(LEASE), anyLong(), anyString(), anyString(), anyMap())).thenAnswer(call -> {
                var before = current.get();
                assertEquals(before.revision(), call.<Long>getArgument(2));
                var next = run(AgentRunStatusEnum.RUNNING, before.revision() + 1, before.lastEventSequence() + 1);
                current.set(next);
                return next;
            });
            when(ledger.finish(eq(ACTOR), eq(LEASE), anyLong(), any())).thenAnswer(call -> {
                AgentExecutionResult completed = call.getArgument(3);
                finished.set(completed);
                var before = current.get();
                var next = run(completed.status(), before.revision() + 1, before.lastEventSequence() + 1);
                current.set(next);
                return next;
            });
            when(ledger.suspendForAuthorization(eq(ACTOR), eq(LEASE), any())).thenAnswer(call -> {
                var before = current.get();
                var next = run(AgentRunStatusEnum.SUSPENDED, before.revision() + 1, before.lastEventSequence() + 1);
                current.set(next);
                return next;
            });
            when(flowFactory.compileRoot(eq("tianji"), any(), anyMap(), eq(List.of())))
                    .thenReturn(agent(model));
        }

        Optional<AgentRunDTO> execute() {
            AgentRunAuthorization authorizer = (actual, boundary) -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals(request, actual);
                assertEquals(AgentRunAuthorization.Boundary.MODEL, boundary);
                actions.add("MODEL");
                return modelDecision.get();
            };
            return new AdkAgentRunExecutor(ledger, flowFactory, validator, Map.of("fixtureModel", model),
                    Map.of(), control, estimator, source, false, authorizer).executeResume(result, resumeAuthority);
        }

        void assertNoFreshExecutionOrRenewal() {
            verify(ledger, never()).claim(any(), any(), anyLong(), anyString(), any(Duration.class));
            verify(ledger, never()).heartbeat(any(), any(), any(Duration.class));
            verify(bound, never()).acquireSessionLease(anyString(), any(), any());
            verify(bound, never()).renewSessionLease(any(), any());
        }

        void assertNoModelOrBudget() {
            assertEquals(0, model.calls.get());
            assertFalse(actions.contains("reserve"));
            verifyNoInteractions(budgets);
            assertNoFreshExecutionOrRenewal();
        }

        List<Event> history() {
            return List.copyOf(storage.getSession(APP, USER, SESSION, Optional.empty())
                    .timeout(3, TimeUnit.SECONDS).blockingGet().immutableEvents());
        }

        private void cutOrdinaryProducer() throws InterruptedException {
            var cut = new CountDownLatch(1);
            var originalModel = new FakeModel(new CopyOnWriteArrayList<>());
            Plugin pause = new Plugin() {
                @Override public String getName() { return "fixture-checkpoint-cut"; }
                @Override public Maybe<LlmResponse> beforeModelCallback(CallbackContext context, LlmRequest.Builder request) {
                    return Maybe.defer(() -> {
                        cut.countDown();
                        return Maybe.never();
                    });
                }
            };
            var runner = new AgentFlowSessionRunner(agent(originalModel),
                    APP, storage, List.of(pause));
            var active = runner.runAsync(USER, SESSION, Content.fromParts(Part.fromText(ORIGINAL)), CONFIG).test();
            try {
                assertTrue(cut.await(3, TimeUnit.SECONDS));
                assertEquals(0, originalModel.calls.get());
            } finally {
                active.cancel();
                assertTrue(runner.close().blockingAwait(3, TimeUnit.SECONDS));
            }
        }

        @Override public void close() { control.shutdownNow(); }
    }

    private static AgentRunDTO run(AgentRunStatusEnum status, long revision, long sequence) {
        return new AgentRunDTO(QUERY.runId(), "fixture", 1, SESSION, status, null, false, 1, revision,
                sequence, DEADLINE, NOW, AgentRunTransitions.terminal(status) ? NOW : null);
    }

    private static LlmAgent agent(ChatModel model) {
        return LlmAgent.builder().name(ROOT).model(new SpringAiModelAdapterFactory().create(model, MODEL)).build();
    }

    private static final class FakeModel implements ChatModel {
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch cancelled = new CountDownLatch(1);
        private final List<String> actions;
        volatile boolean block;

        FakeModel(List<String> actions) { this.actions = actions; }

        @Override public ChatResponse call(Prompt prompt) { throw new AssertionError("Expected the SSE model path"); }

        @Override public ChatOptions getOptions() { return ChatOptions.builder().model(MODEL).build(); }

        @Override public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.defer(() -> {
                calls.incrementAndGet();
                actions.add("model");
                assertTrue(prompt.getInstructions().stream().anyMatch(message -> ORIGINAL.equals(message.getText())));
                started.countDown();
                if (block) return Flux.<ChatResponse>never().doOnCancel(cancelled::countDown);
                return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage(ANSWER),
                        ChatGenerationMetadata.builder().finishReason("stop").build()))));
            });
        }
    }
}
