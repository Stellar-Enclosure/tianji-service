package top.egon.cola.platform.tianji.agent.application.run;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.agentflow.adapter.adk.AdkAgentExecutorAdapter;
import top.egon.cola.component.agentflow.adapter.springai.SpringAiExecutionModelAdapter.ModelRegistrationVO;
import top.egon.cola.component.agentflow.agentic.AgenticPlanVO;
import top.egon.cola.component.agentflow.api.capability.CallPermitProvider;
import top.egon.cola.component.agentflow.api.context.ContextScopeVO;
import top.egon.cola.component.agentflow.api.context.ContextSource;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionCommand;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionContextVO;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionContextVO.ModelCallLifecycle;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionHandle;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionResult;
import top.egon.cola.component.agentflow.api.execution.AgentExecutor;
import top.egon.cola.component.agentflow.api.session.SessionStore;
import top.egon.cola.platform.tianji.agent.application.context.TianjiHostContextBO;
import top.egon.cola.platform.tianji.agent.application.context.TianjiHostContextConverter;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun;
import top.egon.cola.platform.tianji.agent.domain.LeaseHeartbeat;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.RunProfileEnum;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.RuntimeExecutionMaterialVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentRuntimeCallReceiptStore;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticConditionLiteralVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticConditionOpEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticConditionOperationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticConditionPathVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticMergeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticPredicateVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticRootTerminalVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticStrategyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticTemplateLeafVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticTemplateLinkVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticTemplatePlanVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticTerminationModeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TemplateDependencyRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentRuntimeExecutionServiceTest {
    private static final TrustedAgentActor ACTOR = new TrustedAgentActor(24, TrustedAgentActor.SubjectType.SERVICE, "process");
    private static final GetAgentRunQuery QUERY = new GetAgentRunQuery("fixed-run");
    private static final Instant NOW = Instant.parse("2026-11-14T05:00:00Z");

    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final AdkAgentRunExecutor legacy = mock(AdkAgentRunExecutor.class);
    private final AgentRuntimeExecutionService runtime = mock(AgentRuntimeExecutionService.class);

    @Test
    void deniesClaimBeforeCallingTheNeutralExecutor() {
        try (var fixture = new RuntimeFixture()) {
            var suspended = fixture.run(AgentRunStatusEnum.SUSPENDED, 2);
            when(fixture.authorization.authorize(fixture.request, AgentRunAuthorization.Boundary.CLAIM))
                    .thenReturn(AgentRunAuthorization.Decision.DENY);
            when(ledger.suspendQueuedForAuthorization(fixture.actor, QUERY, 1)).thenReturn(suspended);

            assertSame(suspended, fixture.service.executeQueued(fixture.actor, QUERY));
            verify(ledger, never()).claimRuntimeV2(any(), any(), anyLong(), anyString(), any());
            verifyNoInteractions(fixture.executor);
        }
    }

    @Test
    void checksGrantAgainAfterClaimBeforeStartingTheNeutralExecutor() {
        try (var fixture = new RuntimeFixture()) {
            var suspended = fixture.run(AgentRunStatusEnum.SUSPENDED, 3);
            when(fixture.authorization.authorize(fixture.request, AgentRunAuthorization.Boundary.CLAIM))
                    .thenReturn(AgentRunAuthorization.Decision.ALLOW);
            when(fixture.authorization.authorize(fixture.request, AgentRunAuthorization.Boundary.MODEL))
                    .thenReturn(AgentRunAuthorization.Decision.DENY);
            when(ledger.suspendForAuthorization(fixture.actor, fixture.lease, AgentRunAuthorization.Decision.DENY))
                    .thenReturn(suspended);

            assertSame(suspended, fixture.service.executeQueued(fixture.actor, QUERY));
            verify(ledger).claimRuntimeV2(eq(fixture.actor), eq(QUERY), eq(1L), anyString(), any());
            verifyNoInteractions(fixture.executor);
        }
    }

    @Test
    void preservesTheSingleExecutorAndDiscardsAResultIfFinalAuthorizationWasRevoked() {
        try (var fixture = new RuntimeFixture()) {
            var suspended = fixture.run(AgentRunStatusEnum.SUSPENDED, 3);
            when(fixture.authorization.authorize(fixture.request, AgentRunAuthorization.Boundary.CLAIM))
                    .thenReturn(AgentRunAuthorization.Decision.ALLOW);
            when(fixture.authorization.authorize(fixture.request, AgentRunAuthorization.Boundary.MODEL))
                    .thenReturn(AgentRunAuthorization.Decision.ALLOW, AgentRunAuthorization.Decision.DENY);
            when(ledger.get(fixture.actor, QUERY)).thenReturn(fixture.running);
            when(ledger.suspendForAuthorization(fixture.actor, fixture.lease, AgentRunAuthorization.Decision.DENY))
                    .thenReturn(suspended);
            var handle = mock(AgentExecutionHandle.class);
            var neutralResult = completed();
            when(handle.completion()).thenReturn(CompletableFuture.completedFuture(neutralResult));
            when(fixture.executor.execute(any(), any())).thenAnswer(call -> {
                AgentExecutionCommand command = call.getArgument(0);
                assertEquals("registered-executor", command.getTemplate().executorRef());
                assertEquals(fixture.request.definitionSha256(), command.getTemplate().contentSha256());
                return handle;
            });

            assertSame(suspended, fixture.service.executeQueued(fixture.actor, QUERY));
            verify(ledger, never()).finish(eq(fixture.actor), eq(fixture.lease), eq(2L), any());
        }
    }

    @Test
    void losingTheLeaseCancelsTheHandleAndFencesAResponseFromThePriorWorker() {
        try (var fixture = new RuntimeFixture()) {
            when(fixture.authorization.authorize(fixture.request, AgentRunAuthorization.Boundary.CLAIM))
                    .thenReturn(AgentRunAuthorization.Decision.ALLOW);
            when(fixture.authorization.authorize(fixture.request, AgentRunAuthorization.Boundary.MODEL))
                    .thenReturn(AgentRunAuthorization.Decision.ALLOW);
            when(ledger.heartbeat(eq(fixture.actor), eq(fixture.lease), any())).thenReturn(LeaseHeartbeat.LEASE_LOST);
            when(ledger.get(fixture.actor, QUERY)).thenReturn(fixture.running);
            var late = new CompletableFuture<AgentExecutionResult>();
            var handle = mock(AgentExecutionHandle.class);
            when(handle.completion()).thenReturn(late);
            when(fixture.executor.execute(any(), any())).thenAnswer(call -> {
                AgentExecutionContextVO context = call.getArgument(1);
                fixture.heartbeatCheck.run();
                assertTrue(context.cancellation().toCompletableFuture().isDone());
                return handle;
            });

            assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class,
                    () -> fixture.service.executeQueued(fixture.actor, QUERY)).code());
            assertTrue(late.complete(completed()));
            verify(ledger, never()).finish(eq(fixture.actor), eq(fixture.lease), anyLong(), any());
        }
    }

    @Test
    void persistedCancellationWinsOverACompletedReplyAndUsesTheCurrentRevision() {
        try (var fixture = new RuntimeFixture()) {
            var cancelled = fixture.run(AgentRunStatusEnum.CANCELLED, 4);
            var current = new AgentRunDTO(QUERY.runId(), "review", 1, null, AgentRunStatusEnum.RUNNING, null,
                    true, 1, 3, 3, fixture.deadline, NOW, null);
            when(fixture.authorization.authorize(fixture.request, AgentRunAuthorization.Boundary.CLAIM))
                    .thenReturn(AgentRunAuthorization.Decision.ALLOW);
            when(fixture.authorization.authorize(fixture.request, AgentRunAuthorization.Boundary.MODEL))
                    .thenReturn(AgentRunAuthorization.Decision.ALLOW, AgentRunAuthorization.Decision.UNAVAILABLE);
            when(ledger.get(fixture.actor, QUERY)).thenReturn(current);
            when(ledger.finish(eq(fixture.actor), eq(fixture.lease), eq(3L), any())).thenAnswer(call -> {
                var result = call.<top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult>getArgument(3);
                assertEquals(AgentRunStatusEnum.CANCELLED, result.status());
                assertEquals(Map.of(), result.output());
                return cancelled;
            });
            var handle = mock(AgentExecutionHandle.class);
            when(handle.completion()).thenReturn(CompletableFuture.completedFuture(completed()));
            when(fixture.executor.execute(any(), any())).thenReturn(handle);

            assertSame(cancelled, fixture.service.executeQueued(fixture.actor, QUERY));
            verify(ledger).finish(eq(fixture.actor), eq(fixture.lease), eq(3L), any());
        }
    }

    @Test
    void generatedContextMapperIsNamedAndPreservesOnlyTheVerifiedScopeAndCallbacks() {
        var scope = new ContextScopeVO("tianji", "24", "process", "fixed-run", "node-attempt", "EXECUTE");
        var source = mock(ContextSource.class);
        var session = mock(SessionStore.class);
        var permit = mock(CallPermitProvider.class);
        var lifecycle = mock(ModelCallLifecycle.class);
        var cancellation = new CompletableFuture<Void>();
        var input = new TianjiHostContextBO(scope, NOW, cancellation, source, session, permit, lifecycle);

        try (var spring = mapperContext()) {
            var mapper = spring.getBean("tianjiHostContextConverter", TianjiHostContextConverter.class);
            assertEquals("tianjiHostContextConverter", mapper.getClass().getAnnotation(Component.class).value());
            var target = mapper.toTarget(input);
            assertSame(scope, target.scope());
            assertEquals(NOW, target.deadline());
            assertSame(cancellation, target.cancellation());
            assertSame(source, target.contextSource());
            assertSame(session, target.sessionStore());
            assertSame(permit, target.callPermitProvider());
            assertSame(lifecycle, target.modelCallLifecycle());
            assertThrows(NullPointerException.class, () -> mapper.toTarget(input.setVerifiedScope(null)));
        }
    }

    @Test
    void generatedAgenticMapperPreservesAllFieldsAndNullLiteral() {
        var instruction = new TemplateDependencyRefVO("instruction", 2, "a".repeat(64));
        var tool = new TemplateDependencyRefVO("lookup", 3, "b".repeat(64));
        var first = new AgenticTemplateLeafVO("first", instruction, "model-a", List.of(tool));
        var second = new AgenticTemplateLeafVO("second", instruction, "model-b", List.of());
        var input = new AgenticTemplatePlanVO(AgenticStrategyEnum.HANDOFF, List.of(first, second),
                List.of(new AgenticTemplateLinkVO("first", "second")), 4, 1, "second", AgenticMergeEnum.OUTPUT_AGENT,
                List.of("input.text", "agents.first.output.value"), new AgenticPredicateVO(AgenticTerminationModeEnum.PREDICATE,
                new AgenticConditionOperationVO(AgenticConditionOpEnum.EQ,
                        List.of(new AgenticConditionPathVO("input.text"), new AgenticConditionLiteralVO(null)))));

        try (var spring = mapperContext()) {
            var mapper = spring.getBean("agenticTemplateConverter", AgenticTemplateConverter.class);
            assertEquals("agenticTemplateConverter", mapper.getClass().getAnnotation(Component.class).value());
            var target = mapper.toTarget(input);
            assertEquals(AgenticPlanVO.StrategyEnum.HANDOFF, target.strategy());
            assertEquals(List.of("first", "second"), target.agents().stream().map(AgenticPlanVO.LeafVO::agentKey).toList());
            var mappedLeaf = target.agents().getFirst();
            assertEquals(new AgenticPlanVO.DependencyRefVO(instruction.key(), instruction.version(), instruction.contentSha256()),
                    mappedLeaf.instructionRef());
            assertEquals("model-a", mappedLeaf.modelRef());
            assertEquals(List.of(new AgenticPlanVO.DependencyRefVO(tool.key(), tool.version(), tool.contentSha256())), mappedLeaf.toolRefs());
            assertEquals(List.of(new AgenticPlanVO.LinkVO("first", "second")), target.links());
            assertEquals(4, target.maxRounds());
            assertEquals(1, target.maxParallelism());
            assertEquals("second", target.outputAgentKey());
            assertEquals(AgenticPlanVO.MergeEnum.OUTPUT_AGENT, target.merge());
            assertEquals(input.sharedFields(), target.sharedFields());
            assertNotSame(input.agents(), target.agents());
            assertThrows(UnsupportedOperationException.class, () -> target.agents().clear());
            var predicate = assertInstanceOf(AgenticPlanVO.PredicateVO.class, target.termination());
            assertEquals(AgenticPlanVO.TerminationModeEnum.PREDICATE, predicate.mode());
            var condition = assertInstanceOf(AgenticPlanVO.OperationVO.class, predicate.condition());
            assertEquals(AgenticPlanVO.ConditionOpEnum.EQ, condition.op());
            assertEquals(new AgenticPlanVO.PathVO("input.text"), condition.args().getFirst());
            assertNull(assertInstanceOf(AgenticPlanVO.LiteralVO.class, condition.args().getLast()).literal());

            for (var strategy : List.of(AgenticStrategyEnum.REASONING, AgenticStrategyEnum.SEQUENTIAL,
                    AgenticStrategyEnum.PARALLEL, AgenticStrategyEnum.BOUNDED_LOOP)) {
                var plan = new AgenticTemplatePlanVO(strategy, List.of(first), List.of(),
                        strategy == AgenticStrategyEnum.BOUNDED_LOOP ? 3 : null, 1, "first", AgenticMergeEnum.OUTPUT_AGENT,
                        List.of(), new AgenticRootTerminalVO(AgenticTerminationModeEnum.ROOT_TERMINAL));
                var mapped = mapper.toTarget(plan);
                assertEquals(strategy.name(), mapped.strategy().name());
                assertEquals(new AgenticPlanVO.RootTerminalVO(AgenticPlanVO.TerminationModeEnum.ROOT_TERMINAL), mapped.termination());
            }
        }
    }

    @Test
    void resolverOnlyReturnsTheExactRegistrationAndKeepsTheSingleExecutorPath() {
        var instruction = new TemplateDependencyRefVO("instruction", 2, "d".repeat(64));
        var sourceRoot = new AgenticTemplateLeafVO("root", instruction, "model", List.of());
        var sourcePlan = new AgenticTemplatePlanVO(AgenticStrategyEnum.REASONING, List.of(sourceRoot), List.of(), null, 1,
                "root", AgenticMergeEnum.OUTPUT_AGENT, List.of(), new AgenticRootTerminalVO(AgenticTerminationModeEnum.ROOT_TERMINAL));
        var single = new AgentRuntimeTemplateCommand.TemplateConfigurationVO(instruction, "model", List.of(), null);
        var agentic = new AgentRuntimeTemplateCommand.TemplateConfigurationVO(instruction, "model", List.of(), sourcePlan);
        var scope = new ContextScopeVO("tianji", "24", "process", "fixed-run", "node-attempt", "EXECUTE");
        var validator = mock(AgentExecutionCommand.SchemaValidator.class);
        var plainCommand = new AgentExecutionCommand.TemplateVO("review", 1, "a".repeat(64),
                AgentRuntimeExecutionService.ADK_EXECUTOR_REF, single.toWire(), validator);
        var agenticCommand = new AgentExecutionCommand.TemplateVO("review", 1, "b".repeat(64),
                AgentRuntimeExecutionService.ADK_EXECUTOR_REF, agentic.toWire(), validator);
        var unregisteredCommand = new AgentExecutionCommand.TemplateVO("review", 1, "a".repeat(64),
                "unregistered", single.toWire(), validator);
        var registry = mock(AdkAgentExecutorAdapter.TemplateResolver.class);

        try (var spring = mapperContext()) {
            var mapper = spring.getBean("agenticTemplateConverter", AgenticTemplateConverter.class);
            var plan = mapper.toTarget(sourcePlan);
            var model = mock(ChatModel.class);
            var registeredModel = new ModelRegistrationVO("model", "fixture-model", model, 8192, 1024, prompt -> 10);
            var leaf = new AdkAgentExecutorAdapter.RegisteredLeafVO(plan.agents().getFirst().instructionRef(),
                    "Fixture instruction", "model", registeredModel, List.of(),
                    (run, key, round) -> new AdkAgentExecutorAdapter.LeafDecisionVO(true, null));
            var plainRegistration = new AdkAgentExecutorAdapter.RegisteredTemplateVO("review", 1, "a".repeat(64),
                    plan, Map.of("root", leaf), Map.of());
            var agenticRegistration = new AdkAgentExecutorAdapter.RegisteredTemplateVO("review", 1, "b".repeat(64),
                    plan, Map.of("root", leaf), Map.of());
            var differentPlan = new AgenticPlanVO(AgenticPlanVO.StrategyEnum.BOUNDED_LOOP, plan.agents(), List.of(), 2, 1,
                    "root", plan.merge(), List.of(), plan.termination());
            var changed = new AdkAgentExecutorAdapter.RegisteredTemplateVO("review", 1, "b".repeat(64),
                    differentPlan, Map.of("root", leaf), Map.of());
            when(registry.resolve(plainCommand, scope)).thenReturn(plainRegistration);
            when(registry.resolve(agenticCommand, scope)).thenReturn(agenticRegistration, changed, plainRegistration);
            var singleOnly = AgentRuntimeExecutionService.verifiedTemplates(registry, mock(AgenticTemplateConverter.class));
            assertSame(plainRegistration, singleOnly.resolve(plainCommand, scope));
            var resolver = AgentRuntimeExecutionService.verifiedTemplates(registry, mapper);
            assertSame(agenticRegistration, resolver.resolve(agenticCommand, scope));
            assertNull(resolver.resolve(agenticCommand, scope));
            assertNull(resolver.resolve(agenticCommand, scope));
            assertNull(resolver.resolve(unregisteredCommand, scope));
            verify(registry, never()).resolve(eq(unregisteredCommand), any());
        }
    }

    @Test
    void dispatchesPersistedV2WithoutInvokingTheLegacyExecutor() {
        when(runtime.usesLedger(ledger)).thenReturn(true);
        when(ledger.verifyPersistedProfile(ACTOR, QUERY)).thenReturn(RunProfileEnum.RUNTIME_V2);
        var result = outcome();
        when(runtime.executeQueued(ACTOR, QUERY)).thenReturn(result);

        try (var dispatcher = new AgentRunDispatcher(Runnable::run, legacy, ledger, runtime)) {
            assertSame(result, dispatcher.dispatch(ACTOR, QUERY).toCompletableFuture().join());
        }
        verify(runtime).executeQueued(ACTOR, QUERY);
        verifyNoInteractions(legacy);
    }

    @Test
    void keepsTheLegacyExecutorForAProvenV1Snapshot() {
        when(runtime.usesLedger(ledger)).thenReturn(true);
        when(ledger.verifyPersistedProfile(ACTOR, QUERY)).thenReturn(RunProfileEnum.LEGACY_V1);
        var result = outcome();
        when(legacy.executeQueued(ACTOR, QUERY)).thenReturn(result);

        try (var dispatcher = new AgentRunDispatcher(Runnable::run, legacy, ledger, runtime)) {
            assertSame(result, dispatcher.dispatch(ACTOR, QUERY).toCompletableFuture().join());
        }
        verify(legacy).executeQueued(ACTOR, QUERY);
        verify(runtime, never()).executeQueued(any(), any());
    }

    @Test
    void inconsistentPersistedEvidenceCannotReachEitherExecutor() {
        when(runtime.usesLedger(ledger)).thenReturn(true);
        var unavailable = new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Unverifiable run");
        when(ledger.verifyPersistedProfile(ACTOR, QUERY)).thenThrow(unavailable);

        try (var dispatcher = new AgentRunDispatcher(Runnable::run, legacy, ledger, runtime)) {
            assertSame(unavailable, assertThrows(CompletionException.class,
                    () -> dispatcher.dispatch(ACTOR, QUERY).toCompletableFuture().join()).getCause());
        }
        verifyNoInteractions(legacy);
        verify(runtime, never()).executeQueued(any(), any());
    }

    @Test
    void v2OnlyDispatchIsUnavailableWhenRuntimeAssemblyIsMissing() {
        when(ledger.verifyPersistedProfile(ACTOR, QUERY)).thenReturn(RunProfileEnum.RUNTIME_V2);

        try (var dispatcher = new AgentRunDispatcher(Runnable::run, legacy, ledger)) {
            var failed = assertThrows(CompletionException.class, () -> dispatcher.dispatch(ACTOR, QUERY).toCompletableFuture().join());
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    assertInstanceOf(AgentLedgerException.class, failed.getCause()).code());
        }
        verifyNoInteractions(legacy);
    }

    private static AgentRunDTO outcome() {
        return new AgentRunDTO(QUERY.runId(), "review", 1, null, AgentRunStatusEnum.COMPLETED, null, false,
                1, 2, 3, NOW.plusSeconds(30), NOW, NOW);
    }

    private static AgentExecutionResult completed() {
        return new AgentExecutionResult(QUERY.runId(), AgentExecutionResult.StatusEnum.COMPLETED, Map.of(), null,
                AgentExecutionResult.SideEffectOutcomeEnum.NONE, NOW);
    }

    private static AnnotationConfigApplicationContext mapperContext() {
        var spring = new AnnotationConfigApplicationContext();
        spring.scan(AgenticTemplateConverter.class.getPackageName(), TianjiHostContextConverter.class.getPackageName());
        spring.refresh();
        return spring;
    }

    private final class RuntimeFixture implements AutoCloseable {
        private final TrustedAgentActor actor = new TrustedAgentActor(24, TrustedAgentActor.SubjectType.SERVICE,
                "process", "c".repeat(64));
        private final Instant deadline = NOW.plusSeconds(30);
        private final AgentRunAuthorization.Request request = new AgentRunAuthorization.Request(actor, QUERY.runId(),
                "review", 1, "a".repeat(64), "b".repeat(64), "d".repeat(64), "e".repeat(64), false, deadline);
        private final AgentRunLease lease = new AgentRunLease(QUERY.runId(), "current-worker", 1, deadline);
        private final AgentRunDTO running = run(AgentRunStatusEnum.RUNNING, 2);
        private final AgentRunAuthorization authorization = mock(AgentRunAuthorization.class);
        private final AgentExecutor executor = mock(AgentExecutor.class);
        private final ScheduledThreadPoolExecutor control = mock(ScheduledThreadPoolExecutor.class);
        private Runnable heartbeatCheck;
        private final AnnotationConfigApplicationContext mappers = mapperContext();
        private final AgentRuntimeExecutionService service = new AgentRuntimeExecutionService(ledger, authorization, executor,
                mappers.getBean("tianjiHostContextConverter", TianjiHostContextConverter.class), control,
                mock(AgentRuntimeCallReceiptStore.class), mock(TransactionTemplate.class));

        private RuntimeFixture() {
            var limits = new AgentRuntimeTemplateCommand.TechnicalLimitsVO(Duration.ofSeconds(30), Duration.ofSeconds(2),
                    Duration.ofSeconds(2), 2, 1, 1, 8192, 1024);
            var schema = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(),
                    "required", List.of(), "additionalProperties", false));
            var config = new AgentRuntimeTemplateCommand.TemplateConfigurationVO(
                    new TemplateDependencyRefVO("instruction", 1, "f".repeat(64)), "model", List.of(), null);
            var template = new AgentRuntimeTemplateCommand("review", 1, AgentRuntimeTemplateCommand.RuntimeProfileEnum.RUNTIME_V2,
                    "registered-executor", schema, schema, schema, config, limits);
            var command = new LinkedAgentRuntimeCommand().setInput(Map.of()).setParameters(Map.of()).setLimits(limits)
                    .setNodeExecutionRef("bpmn-node").setAttemptDeadlineAt(deadline).setTemplateSha256(request.definitionSha256());
            when(control.scheduleWithFixedDelay(any(Runnable.class), eq(1L), eq(1L), eq(TimeUnit.SECONDS))).thenAnswer(call -> {
                heartbeatCheck = call.getArgument(0);
                return mock(ScheduledFuture.class);
            });
            when(ledger.databaseNow()).thenReturn(NOW);
            when(ledger.runtimeExecutionMaterial(actor, QUERY))
                    .thenReturn(new RuntimeExecutionMaterialVO(run(AgentRunStatusEnum.QUEUED, 1), template, command,
                            request.snapshotSha256(), request), new RuntimeExecutionMaterialVO(running, template, command,
                            request.snapshotSha256(), request));
            when(ledger.claimRuntimeV2(eq(actor), eq(QUERY), eq(1L), anyString(), any())).thenReturn(new ClaimedAgentRun(running, lease));
        }

        private AgentRunDTO run(AgentRunStatusEnum status, long revision) {
            return new AgentRunDTO(QUERY.runId(), "review", 1, null, status, null, false,
                    status == AgentRunStatusEnum.QUEUED ? 0 : 1, revision, revision, deadline, NOW, null);
        }

        @Override
        public void close() {
            mappers.close();
        }
    }
}
