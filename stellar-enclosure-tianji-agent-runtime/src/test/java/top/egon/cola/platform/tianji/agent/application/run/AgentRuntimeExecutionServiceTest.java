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
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.EdgeVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService.FrozenSiBundleVO;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService.SiResourceVO;
import top.egon.cola.platform.tianji.agent.application.context.TianjiHostContextBO;
import top.egon.cola.platform.tianji.agent.application.context.TianjiHostContextConverter;
import top.egon.cola.platform.tianji.agent.application.tool.AgentRuntimeToolExecutionService;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class AgentRuntimeExecutionServiceTest {
    private static final TrustedAgentActor ACTOR = new TrustedAgentActor(24, TrustedAgentActor.SubjectType.SERVICE, "process");
    private static final GetAgentRunQuery QUERY = new GetAgentRunQuery("fixed-run");
    private static final Instant NOW = Instant.parse("2026-11-14T05:00:00Z");
    private static final String FIXED_INSTRUCTION = "Instruction from the committed SI prompt version";
    private static final String PROMPT_SPEC = "{\"promptText\":\"" + FIXED_INSTRUCTION + "\"}";

    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final AdkAgentRunExecutor legacy = mock(AdkAgentRunExecutor.class);
    private final AgentRuntimeExecutionService runtime = mock(AgentRuntimeExecutionService.class);

    @Test
    void missingPersistedSiProvenanceStopsBeforeAuthorityClaimAndExecutor() {
        try (var fixture = new RuntimeFixture(List.of(), null, false, false)) {
            // The historical five-field material has no committed SI closure.  A worker must
            // reject it before it can consume a fresh grant or claim a lease.
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    assertThrows(AgentLedgerException.class,
                            () -> fixture.service.executeQueued(fixture.actor, QUERY)).code());
            verifyNoInteractions(fixture.authorization, fixture.executor);
            verify(ledger, never()).claimRuntimeV2(any(), any(), anyLong(), anyString(), any());
        }
    }

    @Test
    void claimedMaterialCannotReplaceTheCommittedSiClosureUnderTheSameAuthorization() {
        try (var fixture = new RuntimeFixture()) {
            when(fixture.authorization.authorize(fixture.request, AgentRunAuthorization.Boundary.CLAIM))
                    .thenReturn(AgentRunAuthorization.Decision.ALLOW);
            when(ledger.runtimeExecutionMaterial(fixture.actor, QUERY)).thenReturn(
                    new RuntimeExecutionMaterialVO(fixture.run(AgentRunStatusEnum.QUEUED, 1), fixture.template,
                            fixture.command, fixture.request.snapshotSha256(), fixture.request, fixture.bundle),
                    new RuntimeExecutionMaterialVO(fixture.running, fixture.template, fixture.command,
                            fixture.request.snapshotSha256(), fixture.request, frozenBundle(List.of(), "Replaced root body")));

            assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, assertThrows(AgentLedgerException.class,
                    () -> fixture.service.executeQueued(fixture.actor, QUERY)).code());
            verify(fixture.authorization, never()).authorize(any(), eq(AgentRunAuthorization.Boundary.MODEL));
            verifyNoInteractions(fixture.executor);
        }
    }

    @Test
    void fixedInstructionRegistrationCrossesTheAsyncBoundaryWithoutConsultingAnActiveReplacement() {
        try (var fixture = new RuntimeFixture(List.of(), null, true, true)) {
            when(fixture.authorization.authorize(any(), any())).thenReturn(AgentRunAuthorization.Decision.ALLOW);
            when(ledger.get(fixture.actor, QUERY)).thenReturn(fixture.running);
            var done = fixture.run(AgentRunStatusEnum.COMPLETED, 3);
            when(ledger.finish(eq(fixture.actor), eq(fixture.lease), eq(2L), any())).thenReturn(done);
            var observedCommand = new java.util.concurrent.atomic.AtomicReference<AgentExecutionCommand.TemplateVO>();
            var observedScope = new java.util.concurrent.atomic.AtomicReference<ContextScopeVO>();
            var handle = mock(AgentExecutionHandle.class);
            when(handle.completion()).thenReturn(CompletableFuture.completedFuture(completed()));
            when(fixture.executor.execute(any(), any())).thenAnswer(call -> {
                AgentExecutionCommand command = call.getArgument(0);
                AgentExecutionContextVO context = call.getArgument(1);
                observedCommand.set(command.getTemplate());
                observedScope.set(context.scope());
                // A host registry rotation after capture cannot change this persisted run's instruction.
                doAnswer(next -> registered(next.getArgument(0), "new active text"))
                        .when(fixture.hostRegistry).resolve(any(), any());
                var registration = CompletableFuture.supplyAsync(() -> fixture.bindings.resolve(command.getTemplate(), context.scope()))
                        .get(2, TimeUnit.SECONDS);
                assertEquals(FIXED_INSTRUCTION, registration.leaves().get("root").instruction());
                var foreignScope = new ContextScopeVO(context.scope().namespace(), context.scope().tenantKey(),
                        "different-owner", context.scope().instanceKey(), context.scope().sessionKey(), context.scope().purpose());
                assertNull(fixture.bindings.resolve(command.getTemplate(), foreignScope));
                return handle;
            });

            assertSame(done, fixture.service.executeQueued(fixture.actor, QUERY));
            assertNull(fixture.bindings.resolve(observedCommand.get(), observedScope.get()));
            verify(fixture.hostRegistry, times(1)).resolve(any(), any());
        }
    }

    @Test
    void identicalInstructionReferenceWithDifferentBodyNeverReachesTheExecutor() {
        try (var fixture = new RuntimeFixture(List.of(), null, true, true)) {
            when(fixture.authorization.authorize(any(), any())).thenReturn(AgentRunAuthorization.Decision.ALLOW);
            doAnswer(call -> registered(call.getArgument(0), "unpublished body"))
                    .when(fixture.hostRegistry).resolve(any(), any());
            when(ledger.get(fixture.actor, QUERY)).thenReturn(fixture.running);
            var failed = fixture.run(AgentRunStatusEnum.FAILED, 3);
            when(ledger.finish(eq(fixture.actor), eq(fixture.lease), eq(2L), any())).thenAnswer(call -> {
                var result = call.<top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult>getArgument(3);
                assertEquals(AgentRunStatusEnum.FAILED, result.status());
                assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name(), result.errorCode());
                return failed;
            });

            assertSame(failed, fixture.service.executeQueued(fixture.actor, QUERY));
            verifyNoInteractions(fixture.executor);
        }
    }

    @Test
    void closedBindingCannotResolveAgainOrRemoveALaterBinding() {
        try (var fixture = new RuntimeFixture(List.of(), null, true, true)) {
            var expected = new AgentExecutionCommand.TemplateVO("review", 1, fixture.request.definitionSha256(),
                    AgentRuntimeExecutionService.ADK_EXECUTOR_REF, fixture.template.getConfiguration().toWire(),
                    mock(AgentExecutionCommand.SchemaValidator.class));
            var scope = new ContextScopeVO("tianji", "24", fixture.actor.identityFingerprint(), QUERY.runId(), QUERY.runId(), "EXECUTE");
            assertNull(fixture.bindings.resolve(expected, scope));
            verifyNoInteractions(fixture.hostRegistry);
            var first = fixture.bindings.prepare(expected, scope, fixture.bundle);
            assertNotNull(fixture.bindings.resolve(expected, scope));
            assertThrows(AgentLedgerException.class, () -> fixture.bindings.prepare(expected, scope, fixture.bundle));
            first.close();
            assertNull(fixture.bindings.resolve(expected, scope));
            var second = fixture.bindings.prepare(expected, scope, fixture.bundle);
            first.close();
            assertNotNull(fixture.bindings.resolve(expected, scope));
            second.close();
            assertNull(fixture.bindings.resolve(expected, scope));
            verify(fixture.hostRegistry, times(2)).resolve(any(), any());
        }
    }

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
        var tools = mock(AgentExecutionContextVO.ToolCallHandler.class);
        var cancellation = new CompletableFuture<Void>();
        var input = new TianjiHostContextBO(scope, NOW, cancellation, source, session, permit, lifecycle).setToolCallHandler(tools);

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
            assertSame(tools, target.toolCallHandler());
            assertThrows(NullPointerException.class, () -> mapper.toTarget(input.setVerifiedScope(null)));
        }
    }

    @Test
    void fixedToolRequestUsesTheClaimedRuntimeScopeAndRejectsAChangedReferenceBeforeDispatch() {
        var pinned = new TemplateDependencyRefVO("invoice-start", 3, sha256(toolSpec("invoice-start")));
        var host = mock(AgentRuntimeToolExecutionService.class);
        try (var fixture = new RuntimeFixture(List.of(pinned), host)) {
            when(fixture.authorization.authorize(eq(fixture.request), any())).thenReturn(AgentRunAuthorization.Decision.ALLOW);
            when(ledger.get(fixture.actor, QUERY)).thenReturn(fixture.running);
            when(ledger.renewRuntimeLease(eq(fixture.actor), eq(fixture.lease), any())).thenReturn(fixture.lease);
            var done = fixture.run(AgentRunStatusEnum.COMPLETED, 3);
            when(ledger.finish(eq(fixture.actor), eq(fixture.lease), eq(2L), any())).thenReturn(done);
            when(host.execute(any(), eq(fixture.lease), any(), any(), any())).thenReturn(Map.of("instanceId", "started"));
            var handle = mock(AgentExecutionHandle.class);
            when(handle.completion()).thenReturn(CompletableFuture.completedFuture(completed()));
            when(fixture.executor.execute(any(), any())).thenAnswer(call -> {
                AgentExecutionContextVO context = call.getArgument(1);
                assertNotNull(context.callPermitProvider().requirePermit(context.scope(), pinned.key(), "TOOL_CALL"));
                var exact = new AgentExecutionContextVO.ToolReferenceVO(pinned.key(), pinned.version(), pinned.contentSha256());
                var changed = new AgentExecutionContextVO.ToolReferenceVO(pinned.key(), pinned.version(), "e".repeat(64));
                var boundary = mock(AgentExecutionContextVO.ToolCallBoundary.class);
                assertThrows(AgentLedgerException.class, () -> context.toolCallHandler().call(
                        new AgentExecutionContextVO.ToolCallRequestVO(QUERY.runId(), context.scope(), changed, Map.of(), true, fixture.deadline), boundary));
                verifyNoInteractions(host);
                assertEquals(Map.of("instanceId", "started"), context.toolCallHandler().call(
                        new AgentExecutionContextVO.ToolCallRequestVO(QUERY.runId(), context.scope(), exact, Map.of(), true, fixture.deadline), boundary));
                return handle;
            });
            assertSame(done, fixture.service.executeQueued(fixture.actor, QUERY));
            verify(host).execute(any(), eq(fixture.lease), any(), any(), any());
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

    private static String toolSpec(String key) {
        return "{\"tools\":[{\"toolId\":\"" + key + "\",\"name\":\"" + key + "\",\"enabled\":true}]}";
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    private static FrozenSiBundleVO frozenBundle(List<TemplateDependencyRefVO> tools, String rootInstruction) {
        String rootSpec = "{\"modelKey\":\"model\",\"systemInstruction\":\"" + rootInstruction + "\"}";
        var root = new VersionIdentityVO("24", "101", "201", "1", DdcSiKindEnum.AGENT, "prod", sha256(rootSpec));
        var prompt = new VersionIdentityVO("24", "102", "202", "1", DdcSiKindEnum.PROMPT, "prod", sha256(PROMPT_SPEC));
        var nodes = new ArrayList<VersionIdentityVO>();
        nodes.add(prompt);
        var edges = new ArrayList<EdgeVO>();
        edges.add(new EdgeVO(root.versionId(), prompt.versionId()));
        var resources = new ArrayList<SiResourceVO>();
        resources.add(new SiResourceVO(root, "review", null, rootSpec));
        resources.add(new SiResourceVO(prompt, "instruction", null, PROMPT_SPEC));
        for (int index = 0; index < tools.size(); index++) {
            var ref = tools.get(index);
            var identity = new VersionIdentityVO("24", Integer.toString(103 + index), Integer.toString(203 + index),
                    Long.toString(ref.version()), DdcSiKindEnum.MCP, "prod", ref.contentSha256());
            nodes.add(identity);
            edges.add(new EdgeVO(root.versionId(), identity.versionId()));
            resources.add(new SiResourceVO(identity, ref.key(), null, toolSpec(ref.key())));
        }
        var manifest = DdcSiDependencyManifestVO.create(root, null, nodes, edges);
        return new FrozenSiBundleVO("platform", "main", "prod", null, "tianji", manifest, resources);
    }

    private static AdkAgentExecutorAdapter.RegisteredTemplateVO registered(AgentExecutionCommand.TemplateVO expected,
                                                                          String instruction) {
        var configuration = AgentRuntimeTemplateCommand.TemplateConfigurationVO.fromExactWire(expected.configuration());
        var ref = configuration.instructionRef();
        var dependency = new AgenticPlanVO.DependencyRefVO(ref.key(), ref.version(), ref.contentSha256());
        var leaf = new AgenticPlanVO.LeafVO("root", dependency, configuration.modelRef(), List.of());
        var plan = new AgenticPlanVO(AgenticPlanVO.StrategyEnum.REASONING, List.of(leaf), List.of(), null, 1,
                "root", AgenticPlanVO.MergeEnum.OUTPUT_AGENT, List.of(),
                new AgenticPlanVO.RootTerminalVO(AgenticPlanVO.TerminationModeEnum.ROOT_TERMINAL));
        var model = new ModelRegistrationVO("model", "fixture-model", mock(ChatModel.class), 8192, 1024, prompt -> 10);
        var binding = new AdkAgentExecutorAdapter.RegisteredLeafVO(dependency, instruction, "model", model, List.of(),
                (run, key, round) -> new AdkAgentExecutorAdapter.LeafDecisionVO(true, null));
        return new AdkAgentExecutorAdapter.RegisteredTemplateVO(expected.key(), expected.version(), expected.contentSha256(),
                plan, Map.of("root", binding), Map.of());
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
        private final AgentRuntimeExecutionService service;
        private final AgentRuntimeTemplateCommand template;
        private final LinkedAgentRuntimeCommand command;
        private final FrozenSiBundleVO bundle;
        private final AdkAgentExecutorAdapter.TemplateResolver hostRegistry = mock(AdkAgentExecutorAdapter.TemplateResolver.class);
        private final AgentRuntimeExecutionService.FrozenTemplateBindings bindings;

        private RuntimeFixture() { this(List.of(), null); }

        private RuntimeFixture(List<TemplateDependencyRefVO> tools, AgentRuntimeToolExecutionService host) {
            this(tools, host, true, false);
        }

        private RuntimeFixture(List<TemplateDependencyRefVO> tools, AgentRuntimeToolExecutionService host,
                               boolean fixedSi, boolean adk) {
            var limits = new AgentRuntimeTemplateCommand.TechnicalLimitsVO(Duration.ofSeconds(30), Duration.ofSeconds(2),
                    Duration.ofSeconds(2), 2, 1, 1, 8192, 1024);
            var schema = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(),
                    "required", List.of(), "additionalProperties", false));
            var config = new AgentRuntimeTemplateCommand.TemplateConfigurationVO(
                    new TemplateDependencyRefVO("instruction", 1, sha256(PROMPT_SPEC)), "model", tools, null);
            template = new AgentRuntimeTemplateCommand("review", 1, AgentRuntimeTemplateCommand.RuntimeProfileEnum.RUNTIME_V2,
                    adk ? AgentRuntimeExecutionService.ADK_EXECUTOR_REF : "registered-executor", schema, schema, schema, config, limits);
            command = new LinkedAgentRuntimeCommand().setInput(Map.of()).setParameters(Map.of()).setLimits(limits)
                    .setNodeExecutionRef("bpmn-node").setAttemptDeadlineAt(deadline).setTemplateSha256(request.definitionSha256());
            bundle = fixedSi ? frozenBundle(tools, "Frozen root instruction") : null;
            bindings = adk ? new AgentRuntimeExecutionService.FrozenTemplateBindings(hostRegistry,
                    mappers.getBean("agenticTemplateConverter", AgenticTemplateConverter.class)) : null;
            when(hostRegistry.resolve(any(), any())).thenAnswer(call -> registered(call.getArgument(0), FIXED_INSTRUCTION));
            service = new AgentRuntimeExecutionService(ledger, authorization, executor,
                    mappers.getBean("tianjiHostContextConverter", TianjiHostContextConverter.class), control,
                    mock(AgentRuntimeCallReceiptStore.class), mock(TransactionTemplate.class), host, bindings);
            when(control.scheduleWithFixedDelay(any(Runnable.class), eq(1L), eq(1L), eq(TimeUnit.SECONDS))).thenAnswer(call -> {
                heartbeatCheck = call.getArgument(0);
                return mock(ScheduledFuture.class);
            });
            when(ledger.databaseNow()).thenReturn(NOW);
            when(ledger.runtimeExecutionMaterial(actor, QUERY))
                    .thenReturn(new RuntimeExecutionMaterialVO(run(AgentRunStatusEnum.QUEUED, 1), template, command,
                            request.snapshotSha256(), request, bundle), new RuntimeExecutionMaterialVO(running, template, command,
                            request.snapshotSha256(), request, bundle));
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
