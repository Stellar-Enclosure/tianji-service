package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.tools.BaseTool;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.ToolCommittedFactObserver;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessRecorder;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Programmatic union boundary only; native Runner integration is covered by its own focused test. */
class AdkGovernedProcessToolBridgeTest {
    private static final Instant NOW = Instant.parse("2026-11-01T01:00:00Z"), RUN_DEADLINE = NOW.plusSeconds(60);
    private static final String RUN = "10000000-0000-0000-0000-000000000001", HASH = "a".repeat(64);
    private static final UUID HOST = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/21");
    private static final String RECEIPT = "tianji:ps-start:v1:" + "b".repeat(64), INSTANCE = "private-process-instance";
    private static final ToolDescriptor READ = descriptor("decision.read", "read_decision", ToolPolicy.Risk.READ_ONLY,
            DecisionClient.EXECUTE_RESOURCE_KEY, Map.of("value", ToolSchema.ValueType.STRING));
    private static final ToolDescriptor START = descriptor("process.start", "start_process", ToolPolicy.Risk.IDEMPOTENT_WRITE,
            ProcessClient.START_RESOURCE_KEY, Map.of("instanceId", ToolSchema.ValueType.STRING));
    private static final Map<String, Object> ARGUMENTS = Map.of("amount", 20L);

    @Test void constructor_requires_exact_real_dependencies_and_exposes_only_identity_affinities() {
        try (var fixture = new Fixture()) {
            assertTrue(fixture.bridge.usesLedger(fixture.ledger)); assertTrue(fixture.bridge.usesRecorder(fixture.recorder));
            assertTrue(fixture.bridge.usesDecisionTransport(fixture.decision)); assertTrue(fixture.bridge.usesStartTransport(fixture.start));
            assertTrue(fixture.bridge.usesResultAccessRecorder(fixture.access));
            assertFalse(fixture.bridge.usesLedger(mock(AgentLedgerService.class))); assertFalse(fixture.bridge.usesRecorder(mock(ToolInvocationRecorder.class)));
            var values = new Object[]{fixture.ledger, fixture.recorder, fixture.decision, fixture.start, fixture.access,
                    List.of(READ, START), fixture.hostBindings, fixture.resolver};
            for (int index = 0; index < values.length; index++) {
                var changed = values.clone(); changed[index] = null;
                assertThrows(IllegalArgumentException.class, () -> construct(changed));
            }
        }
    }

    @Test void one_full_visibility_binding_freezes_one_host_context_and_one_database_time_for_mixed_tools() {
        try (var fixture = new Fixture()) {
            var first = fixture.bind(); var second = fixture.bind();
            assertEquals(2, first.declaredTools().size()); assertEquals(2, second.declaredTools().size());
            assertEquals(Set.of(READ.name(), START.name()), Set.of(first.declaredTools().get(0).name(), first.declaredTools().get(1).name()));
            assertNotSame(tool(first, READ.name()), tool(second, READ.name())); assertNotSame(tool(first, START.name()), tool(second, START.name()));
            assertEquals(2, fixture.bindingCalls.get()); assertEquals(2, fixture.contextRequests.size());
            assertEquals(2, fixture.databaseTimes.get());
            for (var request : fixture.contextRequests) {
                assertEquals(fixture.material.authorization(), request.run()); assertSame(fixture.material.snapshot(), request.snapshot()); assertEquals(START, request.descriptor());
                assertFalse(request.toString().contains("invoice-1"));
            }
            assertEquals("AdkGovernedProcessToolBridge.StartActionContextVO[redacted]", fixture.context.toString());
            assertThrows(UnsupportedOperationException.class, () -> first.declaredTools().clear());
            first.close(); second.close();
        }
    }

    @Test void declarations_preserve_the_single_full_registry_name_order_across_read_and_start_groups() {
        var read = descriptor("decision.z", "z_read", ToolPolicy.Risk.READ_ONLY, DecisionClient.EXECUTE_RESOURCE_KEY, Map.of("value", ToolSchema.ValueType.STRING));
        var startDescriptor = descriptor("process.a", "a_start", ToolPolicy.Risk.IDEMPOTENT_WRITE, ProcessClient.START_RESOURCE_KEY, Map.of("instanceId", ToolSchema.ValueType.STRING));
        var tools = List.of(read, startDescriptor); var material = material("invoice-1", tools, tools.stream().map(ToolDescriptor::versionKey).collect(java.util.stream.Collectors.toSet()), 4);
        var ledger = mock(AgentLedgerService.class); var recorder = mock(ToolInvocationRecorder.class); var decision = mock(ReadOnlyDecisionToolTransport.class);
        var start = mock(FixedBpmnStartToolTransport.class); var access = mock(ToolResultAccessRecorder.class);
        when(recorder.usesLedger(ledger)).thenReturn(true); when(access.usesLedger(ledger)).thenReturn(true); when(ledger.databaseNow()).thenReturn(NOW);
        var bridge = new AdkGovernedProcessToolBridge(ledger, recorder, decision, start, access, tools,
                value -> tools.stream().map(tool -> binding(value.authorization(), tool)).toList(),
                request -> new AdkGovernedProcessToolBridge.StartActionContextVO("business-v1", Duration.ofDays(1)));
        var scope = bridge.bindScope(material, lease(), event -> { });
        assertEquals(List.of("a_start", "z_read"), scope.declaredTools().stream().map(BaseTool::name).toList()); scope.close();
    }

    @Test void host_business_context_is_strict_frozen_and_never_derived_from_run_or_model_values() {
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedProcessToolBridge.StartActionContextVO(" ", Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedProcessToolBridge.StartActionContextVO("v".repeat(129), Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedProcessToolBridge.StartActionContextVO("v1", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedProcessToolBridge.StartActionContextVO("v1", Duration.ofDays(7).plusNanos(1)));
        for (String variant : List.of("missing-key", "long-key", "null-context", "resolver-throw")) {
            try (var fixture = new Fixture(variant.equals("missing-key") ? null : variant.equals("long-key") ? "x".repeat(256) : "invoice-1")) {
                fixture.resolverFailure = variant.equals("resolver-throw"); fixture.nullContext = variant.equals("null-context");
                assertThrows(AgentLedgerException.class, fixture::bind, variant);
                verifyNoInteractions(fixture.recorder, fixture.decision, fixture.start, fixture.access);
            }
        }
    }

    @Test void constructor_affinity_and_full_binding_sets_cannot_be_widened_or_rebound() {
        try (var fixture = new Fixture()) {
            var wrongRecorder = mock(ToolInvocationRecorder.class); var wrongAccess = mock(ToolResultAccessRecorder.class);
            when(wrongRecorder.usesLedger(fixture.ledger)).thenReturn(false); when(wrongAccess.usesLedger(fixture.ledger)).thenReturn(true);
            assertThrows(IllegalArgumentException.class, () -> new AdkGovernedProcessToolBridge(fixture.ledger, wrongRecorder, fixture.decision, fixture.start,
                    wrongAccess, List.of(READ, START), fixture.hostBindings, fixture.resolver));
            var correct = List.of(binding(fixture.material.authorization(), READ), binding(fixture.material.authorization(), START));
            var start = correct.get(1);
            var changedBackend = new ToolExecutionGrant.BindingVO(start.run(), start.tool(), "c".repeat(64), start.purpose(), start.resource(), start.authorizationResourceKey(), null);
            var foreignRun = new AgentRunAuthorization.Request(ACTOR, "10000000-0000-0000-0000-000000000009", "assistant", 1, HASH,
                    "b".repeat(64), "c".repeat(64), "policy-v1", false, RUN_DEADLINE);
            var other = descriptor("other.read", "other_read", ToolPolicy.Risk.READ_ONLY, DecisionClient.EXECUTE_RESOURCE_KEY, Map.of("value", ToolSchema.ValueType.STRING));
            for (var supplied : List.of(List.of(correct.getFirst()), List.of(correct.getFirst(), start, start), List.of(correct.getFirst(), changedBackend),
                    List.of(correct.getFirst(), binding(foreignRun, START)), List.of(correct.getFirst(), start, binding(fixture.material.authorization(), other)))) {
                var bridge = new AdkGovernedProcessToolBridge(fixture.ledger, fixture.recorder, fixture.decision, fixture.start, fixture.access,
                        List.of(READ, START, other), ignored -> supplied, fixture.resolver);
                assertThrows(AgentLedgerException.class, () -> bridge.bindScope(fixture.material, lease(), event -> { }));
            }
        }
    }

    @Test void unsupported_write_risks_resources_schemas_and_duplicate_native_names_fail_the_full_union() {
        var approval = new ToolDescriptor("process.approved", 1, "approved_process", "Approval write", START.input(), START.output(),
                new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.approved.action", ProcessClient.START_RESOURCE_KEY, HASH, "approval-v1"));
        var otherWrite = descriptor("process.other", "other_process", ToolPolicy.Risk.IDEMPOTENT_WRITE, "other.write", Map.of("instanceId", ToolSchema.ValueType.STRING));
        var badOutput = descriptor("process.badOutput", "bad_output", ToolPolicy.Risk.IDEMPOTENT_WRITE, ProcessClient.START_RESOURCE_KEY, Map.of("other", ToolSchema.ValueType.STRING));
        var duplicateName = descriptor("process.duplicate", START.name(), ToolPolicy.Risk.IDEMPOTENT_WRITE, ProcessClient.START_RESOURCE_KEY, Map.of("instanceId", ToolSchema.ValueType.STRING));
        for (var candidate : List.of(approval, otherWrite, badOutput, duplicateName)) {
            var tools = List.of(READ, START, candidate); var material = material("invoice-1", tools, tools.stream().map(ToolDescriptor::versionKey).collect(java.util.stream.Collectors.toSet()), 8);
            var ledger = mock(AgentLedgerService.class); var recorder = mock(ToolInvocationRecorder.class); var decision = mock(ReadOnlyDecisionToolTransport.class);
            var start = mock(FixedBpmnStartToolTransport.class); var access = mock(ToolResultAccessRecorder.class);
            when(recorder.usesLedger(ledger)).thenReturn(true); when(access.usesLedger(ledger)).thenReturn(true); when(ledger.databaseNow()).thenReturn(NOW);
            var bridge = new AdkGovernedProcessToolBridge(ledger, recorder, decision, start, access, tools,
                    value -> tools.stream().map(tool -> binding(value.authorization(), tool)).toList(),
                    request -> new AdkGovernedProcessToolBridge.StartActionContextVO("business-v1", Duration.ofDays(1)));
            assertThrows(AgentLedgerException.class, () -> bridge.bindScope(material, lease(), event -> { }), candidate.key());
        }
    }

    @Test void fresh_success_uses_frozen_action_expiry_and_observer_before_returning_only_instance_id() {
        try (var fixture = new Fixture()) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name()); var scopeRef = new AtomicReference<AdkGovernedToolBridge.Scope>(scope);
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenAnswer(call -> {
                assertEquals("UNKNOWN", scopeRef.get().sideEffectOutcome(), "START must be uncertain before Recorder I/O");
                var request = call.getArgument(1, ToolInvocationStore.PreparationRequestVO.class);
                assertEquals("invoice-1", request.action().businessKey()); assertEquals("business-v1", request.action().businessVersion());
                assertEquals(NOW.plus(Duration.ofDays(1)).truncatedTo(java.time.temporal.ChronoUnit.MICROS), request.intentExpiry());
                assertTrue(request.intentExpiry().isAfter(request.binding().run().deadlineAt()), "intent lifetime must not be clamped to this run");
                assertEquals(HOST, request.invocationId()); assertEquals(BigDecimal.ZERO, request.estimatedCost());
                var wrapped = call.getArgument(0, FixedBpmnStartToolTransport.class);
                assertEquals(INSTANCE, wrapped.withVerifiedCall(fixedRequest(request), (executor, operation) -> operation.start()).instanceId());
                call.getArgument(2, ToolCommittedFactObserver.class).onCommitted(ToolExecutionOutcome.Status.SUCCEEDED);
                return fresh(request.action(), successAttempt(ToolInvocationRecorder.AccountingState.SETTLED));
            });
            var value = scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> fixture.fences.incrementAndGet());
            assertEquals(Map.of("status", "SUCCEEDED", "output", Map.of("instanceId", INSTANCE)), value);
            assertEquals("CONFIRMED", scope.sideEffectOutcome()); assertEquals(1, fixture.fences.get()); verifyNoInteractions(fixture.access);
            assertEquals(2, fixture.events.size()); assertEquals(AdkGovernedToolBridge.Phase.STARTED, fixture.events.getFirst().phase());
            assertEquals(AdkGovernedToolBridge.Phase.FINISHED, fixture.events.getLast().phase()); assertEquals(Long.valueOf(1), fixture.events.getLast().dispatchEpoch());
            assertFalse(fixture.events.toString().contains(INSTANCE)); assertFalse(fixture.events.toString().contains(RECEIPT));
            scope.close();
        }
    }

    @Test void known_business_effect_is_independent_of_pending_accounting_while_unknown_dominates() {
        for (var status : List.of(ToolExecutionOutcome.Status.SUCCEEDED, ToolExecutionOutcome.Status.REJECTED,
                ToolExecutionOutcome.Status.FAILED_KNOWN, ToolExecutionOutcome.Status.UNKNOWN)) try (var fixture = new Fixture()) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name());
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenAnswer(call -> {
                var request = call.getArgument(1, ToolInvocationStore.PreparationRequestVO.class); call.getArgument(2, ToolCommittedFactObserver.class).onCommitted(status);
                return fresh(request.action(), attempt(status, ToolInvocationRecorder.AccountingState.PENDING));
            });
            var failure = assertThrows(AgentLedgerException.class, () -> scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> { }));
            assertTrue(Set.of(AgentErrorCodeEnum.TIANJI_USAGE_UNKNOWN, AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE).contains(failure.code()));
            assertEquals(status == ToolExecutionOutcome.Status.SUCCEEDED ? "CONFIRMED"
                    : Set.of(ToolExecutionOutcome.Status.REJECTED, ToolExecutionOutcome.Status.FAILED_KNOWN).contains(status) ? "NONE" : "UNKNOWN", scope.sideEffectOutcome());
            scope.close();
        }
    }

    @Test void settled_known_negatives_return_closed_errors_and_access_pending_keeps_confirmed_truth_without_output() {
        for (var status : List.of(ToolExecutionOutcome.Status.REJECTED, ToolExecutionOutcome.Status.FAILED_KNOWN)) try (var fixture = new Fixture()) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name());
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenAnswer(call -> {
                var request = call.getArgument(1, ToolInvocationStore.PreparationRequestVO.class);
                call.getArgument(2, ToolCommittedFactObserver.class).onCommitted(status);
                return fresh(request.action(), attempt(status, ToolInvocationRecorder.AccountingState.SETTLED));
            });
            var value = scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> { });
            assertEquals(Set.of("status", "errorCode"), value.keySet()); assertFalse(value.containsKey("output")); assertEquals("NONE", scope.sideEffectOutcome()); scope.close();
        }
        try (var fixture = new Fixture()) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name()); var action = action("business-v1");
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenReturn(new ToolInvocationRecorder.IdempotentStartResultVO(action.intentId(), "APPLIED", 3, null));
            when(fixture.access.recordKnownStartResultAccess(any(), any())).thenAnswer(call -> {
                var request = call.getArgument(0, ToolResultAccessStore.StartResultAccessRequestVO.class);
                call.getArgument(1, ToolCommittedFactObserver.class).onCommitted(ToolExecutionOutcome.Status.SUCCEEDED);
                return accessed(request, source(ToolExecutionOutcome.Status.SUCCEEDED, null), ToolInvocationRecorder.AccountingState.PENDING);
            });
            var failure = assertThrows(AgentLedgerException.class, () -> scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> { }));
            assertEquals(AgentErrorCodeEnum.TIANJI_USAGE_UNKNOWN, failure.code()); assertEquals("CONFIRMED", scope.sideEffectOutcome());
            assertFalse(failure.getMessage().contains(INSTANCE)); verify(fixture.access).recordKnownStartResultAccess(any(), any()); scope.close();
        }
    }

    @Test void metadata_without_an_own_invocation_uses_current_authorized_access_with_the_same_host_id() {
        for (var status : List.of(ToolExecutionOutcome.Status.SUCCEEDED, ToolExecutionOutcome.Status.REJECTED, ToolExecutionOutcome.Status.FAILED_KNOWN)) try (var fixture = new Fixture()) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name()); var action = action("business-v1");
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenReturn(new ToolInvocationRecorder.IdempotentStartResultVO(action.intentId(),
                    status == ToolExecutionOutcome.Status.SUCCEEDED ? "APPLIED" : status.name(), 3, null));
            when(fixture.access.recordKnownStartResultAccess(any(), any())).thenAnswer(call -> {
                assertEquals("UNKNOWN", scope.sideEffectOutcome());
                var request = call.getArgument(0, ToolResultAccessStore.StartResultAccessRequestVO.class);
                assertEquals(HOST, request.accessId()); assertEquals(action.intentId(), request.action().intentId());
                call.getArgument(1, ToolCommittedFactObserver.class).onCommitted(status);
                return accessed(request, source(status, new BigDecimal("2.5000")), ToolInvocationRecorder.AccountingState.SETTLED);
            });
            var value = scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> { });
            if (status == ToolExecutionOutcome.Status.SUCCEEDED) assertEquals(Map.of("status", "SUCCEEDED", "output", Map.of("instanceId", INSTANCE)), value);
            else assertEquals(Set.of("status", "errorCode"), value.keySet());
            assertEquals(status == ToolExecutionOutcome.Status.SUCCEEDED ? "CONFIRMED" : "NONE", scope.sideEffectOutcome());
            verify(fixture.access).recordKnownStartResultAccess(any(), any()); scope.close();
        }
    }

    @Test void own_metadata_without_body_never_uses_result_access_or_retries_transport() {
        try (var fixture = new Fixture()) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name()); var action = action("business-v1");
            var own = new ToolInvocationRecorder.WriteAttemptResultVO(RUN, HOST, "DISPATCHING", 2, 1, NOW, null, null, false, ToolInvocationRecorder.AccountingState.PENDING);
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenReturn(new ToolInvocationRecorder.IdempotentStartResultVO(action.intentId(), "DISPATCHING", 2, own));
            assertThrows(AgentLedgerException.class, () -> scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> { }));
            assertEquals("UNKNOWN", scope.sideEffectOutcome()); verifyNoInteractions(fixture.access, fixture.start); scope.close();
        }
    }

    @Test void a_scope_closed_by_the_recorder_metadata_return_never_starts_new_result_access_io() {
        try (var fixture = new Fixture()) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name()); var action = action("business-v1");
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenAnswer(call -> {
                scope.close(); return new ToolInvocationRecorder.IdempotentStartResultVO(action.intentId(), "APPLIED", 3, null);
            });
            assertThrows(AgentLedgerException.class, () -> scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> { }));
            verifyNoInteractions(fixture.access); assertEquals("UNKNOWN", scope.sideEffectOutcome()); assertEquals(1, fixture.events.size());
        }
    }

    @Test void fresh_and_access_results_must_match_exact_run_host_action_schema_and_fresh_zero_cost() {
        for (String drift : List.of("run", "host", "action", "cost")) try (var fixture = new Fixture()) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name());
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenAnswer(call -> {
                var request = call.getArgument(1, ToolInvocationStore.PreparationRequestVO.class);
                call.getArgument(2, ToolCommittedFactObserver.class).onCommitted(ToolExecutionOutcome.Status.SUCCEEDED);
                var own = successAttempt(ToolInvocationRecorder.AccountingState.SETTLED);
                if (drift.equals("run")) own = new ToolInvocationRecorder.WriteAttemptResultVO("other-run", HOST, own.state(), own.revision(), own.dispatchEpoch(),
                        own.dispatchedAt(), own.finishedAt(), own.outcome(), own.operationInvokedThisCall(), own.accountingState());
                if (drift.equals("host")) own = new ToolInvocationRecorder.WriteAttemptResultVO(RUN, UUID.randomUUID(), own.state(), own.revision(), own.dispatchEpoch(),
                        own.dispatchedAt(), own.finishedAt(), own.outcome(), own.operationInvokedThisCall(), own.accountingState());
                if (drift.equals("cost")) { own = spy(own); doReturn(source(ToolExecutionOutcome.Status.SUCCEEDED, BigDecimal.ONE)).when(own).outcome(); }
                return new ToolInvocationRecorder.IdempotentStartResultVO(drift.equals("action") ? UUID.randomUUID().toString() : request.action().intentId(), "PREPARED", 1, own);
            });
            assertThrows(AgentLedgerException.class, () -> scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> { }));
            assertEquals("CONFIRMED", scope.sideEffectOutcome()); verifyNoInteractions(fixture.access); scope.close();
        }
        for (String drift : List.of("run", "host", "action")) try (var fixture = new Fixture()) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name()); var action = action("business-v1");
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenReturn(new ToolInvocationRecorder.IdempotentStartResultVO(action.intentId(), "APPLIED", 3, null));
            when(fixture.access.recordKnownStartResultAccess(any(), any())).thenAnswer(call -> {
                var request = call.getArgument(0, ToolResultAccessStore.StartResultAccessRequestVO.class);
                call.getArgument(1, ToolCommittedFactObserver.class).onCommitted(ToolExecutionOutcome.Status.SUCCEEDED);
                return new ToolResultAccessRecorder.StartResultAccessVO(drift.equals("run") ? "other-run" : RUN,
                        drift.equals("host") ? UUID.randomUUID() : request.accessId(), drift.equals("action") ? UUID.randomUUID().toString() : action.intentId(),
                        3, HASH, source(ToolExecutionOutcome.Status.SUCCEEDED, new BigDecimal("9.5000")), ToolInvocationRecorder.AccountingState.SETTLED);
            });
            assertThrows(AgentLedgerException.class, () -> scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> { }));
            assertEquals("CONFIRMED", scope.sideEffectOutcome()); scope.close();
        }
    }

    @Test void an_unknown_sibling_dominates_a_confirmed_sibling_in_both_completion_orders() {
        UUID other = UUID.fromString("20000000-0000-0000-0000-000000000002");
        for (boolean confirmedFirst : List.of(false, true)) try (var fixture = new Fixture()) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name());
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenAnswer(call -> {
                var request = call.getArgument(1, ToolInvocationStore.PreparationRequestVO.class);
                var status = request.invocationId().equals(HOST) ? ToolExecutionOutcome.Status.SUCCEEDED : ToolExecutionOutcome.Status.UNKNOWN;
                call.getArgument(2, ToolCommittedFactObserver.class).onCommitted(status);
                return fresh(request.action(), attemptFor(request.invocationId(), status, ToolInvocationRecorder.AccountingState.SETTLED));
            });
            var first = confirmedFirst ? HOST : other; var second = confirmedFirst ? other : HOST;
            if (confirmedFirst) assertEquals("SUCCEEDED", scope.execute(nativeStart, ARGUMENTS, first, "review_agent", () -> { }).get("status"));
            else assertThrows(AgentLedgerException.class, () -> scope.execute(nativeStart, ARGUMENTS, first, "review_agent", () -> { }));
            if (confirmedFirst) assertThrows(AgentLedgerException.class, () -> scope.execute(nativeStart, ARGUMENTS, second, "review_agent", () -> { }));
            else assertEquals("SUCCEEDED", scope.execute(nativeStart, ARGUMENTS, second, "review_agent", () -> { }).get("status"));
            assertEquals("UNKNOWN", scope.sideEffectOutcome()); scope.close(); assertEquals("UNKNOWN", scope.sideEffectOutcome());
        }
    }

    @Test void pre_delegate_fence_failure_never_calls_start_and_remains_a_run_stopping_error() {
        try (var fixture = new Fixture()) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name());
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenAnswer(call -> {
                var wrapped = call.getArgument(0, FixedBpmnStartToolTransport.class); var request = call.getArgument(1, ToolInvocationStore.PreparationRequestVO.class);
                var observer = call.getArgument(2, ToolCommittedFactObserver.class);
                try {
                    wrapped.withVerifiedCall(new FixedBpmnStartToolTransport.Request(request.binding().run(), request.lease(), request.descriptor(), request.action(), request.invocationId(), RUN_DEADLINE),
                            (executor, operation) -> { operation.start(); return null; });
                    return fail("Fence failure must not return");
                } catch (FixedBpmnStartToolTransport.Failure expected) {
                    assertEquals(FixedBpmnStartToolTransport.FailureKind.OPERATION_NOT_SENT, expected.kind()); observer.onCommitted(ToolExecutionOutcome.Status.FAILED_KNOWN);
                    return fresh(request.action(), attempt(ToolExecutionOutcome.Status.FAILED_KNOWN, ToolInvocationRecorder.AccountingState.SETTLED));
                }
            });
            var privateFence = new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "private Session fence");
            var failure = assertThrows(AgentLedgerException.class, () -> scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> { throw privateFence; }));
            assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, failure.code()); assertNull(failure.getCause()); assertFalse(failure.getMessage().contains("private"));
            assertEquals(0, fixture.startDelegates.get()); assertEquals("NONE", scope.sideEffectOutcome());
            verify(fixture.recorder).executeIdempotentStart(any(), any(), any()); assertEquals("UNAVAILABLE", fixture.events.getLast().state()); scope.close();
        }
    }

    @Test void delegate_failure_is_never_reclassified_as_a_pre_admission_not_sent_failure() {
        try (var fixture = new Fixture()) {
            fixture.delegateFailure = FixedBpmnStartToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN;
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name());
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenAnswer(call -> {
                var request = call.getArgument(1, ToolInvocationStore.PreparationRequestVO.class);
                var wrapped = call.getArgument(0, FixedBpmnStartToolTransport.class);
                var failure = assertThrows(FixedBpmnStartToolTransport.Failure.class,
                        () -> wrapped.withVerifiedCall(fixedRequest(request), (executor, operation) -> operation.start()));
                assertEquals(FixedBpmnStartToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN, failure.kind());
                call.getArgument(2, ToolCommittedFactObserver.class).onCommitted(ToolExecutionOutcome.Status.UNKNOWN);
                return fresh(request.action(), attempt(ToolExecutionOutcome.Status.UNKNOWN, ToolInvocationRecorder.AccountingState.SETTLED));
            });
            var failure = assertThrows(AgentLedgerException.class,
                    () -> scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> fixture.fences.incrementAndGet()));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code());
            assertEquals(1, fixture.fences.get()); assertEquals(1, fixture.startDelegates.get()); assertEquals("UNKNOWN", scope.sideEffectOutcome()); scope.close();
        }
    }

    @Test void held_pre_admission_failure_wins_over_an_outer_transport_postcheck_failure_after_known_negative_commit() {
        try (var fixture = new Fixture()) {
            fixture.afterTransportFailure = FixedBpmnStartToolTransport.FailureKind.RELEASE_CHANGED;
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name());
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenAnswer(call -> {
                var request = call.getArgument(1, ToolInvocationStore.PreparationRequestVO.class); var observer = call.getArgument(2, ToolCommittedFactObserver.class);
                return call.getArgument(0, FixedBpmnStartToolTransport.class).withVerifiedCall(fixedRequest(request), (executor, operation) -> {
                    try { operation.start(); return fail("Fence failure must prevent the delegate"); }
                    catch (FixedBpmnStartToolTransport.Failure knownNoSend) {
                        assertEquals(FixedBpmnStartToolTransport.FailureKind.OPERATION_NOT_SENT, knownNoSend.kind());
                        observer.onCommitted(ToolExecutionOutcome.Status.FAILED_KNOWN);
                        return fresh(request.action(), attempt(ToolExecutionOutcome.Status.FAILED_KNOWN, ToolInvocationRecorder.AccountingState.SETTLED));
                    }
                });
            });
            var privateFence = new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "private current fence");
            var failure = assertThrows(AgentLedgerException.class,
                    () -> scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> { throw privateFence; }));
            assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, failure.code()); assertFalse(failure.getMessage().contains("private"));
            assertEquals(0, fixture.startDelegates.get()); assertEquals("NONE", scope.sideEffectOutcome());
            verify(fixture.recorder).executeIdempotentStart(any(), any(), any()); assertEquals("UNAVAILABLE", fixture.events.getLast().state()); scope.close();
        }
    }

    @Test void close_freezes_unknown_across_recorder_and_metadata_access_races_and_late_observers() {
        for (boolean metadata : List.of(false, true)) try (var fixture = new Fixture()) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name()); var action = action("business-v1");
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenAnswer(call -> {
                assertEquals("UNKNOWN", scope.sideEffectOutcome());
                if (!metadata) { scope.close(); call.getArgument(2, ToolCommittedFactObserver.class).onCommitted(ToolExecutionOutcome.Status.SUCCEEDED); return fresh(action, successAttempt(ToolInvocationRecorder.AccountingState.SETTLED)); }
                return new ToolInvocationRecorder.IdempotentStartResultVO(action.intentId(), "APPLIED", 3, null);
            });
            if (metadata) when(fixture.access.recordKnownStartResultAccess(any(), any())).thenAnswer(call -> {
                assertEquals("UNKNOWN", scope.sideEffectOutcome()); scope.close(); call.getArgument(1, ToolCommittedFactObserver.class).onCommitted(ToolExecutionOutcome.Status.SUCCEEDED);
                return accessed(call.getArgument(0), source(ToolExecutionOutcome.Status.SUCCEEDED, null), ToolInvocationRecorder.AccountingState.SETTLED);
            });
            assertThrows(AgentLedgerException.class, () -> scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> { }));
            assertEquals("UNKNOWN", scope.sideEffectOutcome(), "Late known facts cannot rewrite the frozen terminal aggregate");
        }
    }

    @Test void another_thread_can_close_and_freeze_while_recorder_or_result_access_io_is_blocked() throws Exception {
        for (boolean accessPath : List.of(false, true)) try (var fixture = new Fixture(); var pool = Executors.newFixedThreadPool(2)) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name()); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var action = action("business-v1");
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenAnswer(call -> {
                if (accessPath) return new ToolInvocationRecorder.IdempotentStartResultVO(action.intentId(), "APPLIED", 3, null);
                entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS));
                call.getArgument(2, ToolCommittedFactObserver.class).onCommitted(ToolExecutionOutcome.Status.SUCCEEDED);
                return fresh(action, successAttempt(ToolInvocationRecorder.AccountingState.SETTLED));
            });
            if (accessPath) when(fixture.access.recordKnownStartResultAccess(any(), any())).thenAnswer(call -> {
                entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS));
                call.getArgument(1, ToolCommittedFactObserver.class).onCommitted(ToolExecutionOutcome.Status.SUCCEEDED);
                return accessed(call.getArgument(0), source(ToolExecutionOutcome.Status.SUCCEEDED, null), ToolInvocationRecorder.AccountingState.SETTLED);
            });
            var execution = pool.submit(() -> scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> { }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var closing = pool.submit(scope::close);
            try { closing.get(1, TimeUnit.SECONDS); }
            finally { release.countDown(); }
            assertEquals("UNKNOWN", scope.sideEffectOutcome());
            assertThrows(ExecutionException.class, () -> execution.get(5, TimeUnit.SECONDS));
            assertEquals("UNKNOWN", scope.sideEffectOutcome()); assertEquals(1, fixture.events.size());
            assertEquals(AdkGovernedToolBridge.Phase.STARTED, fixture.events.getFirst().phase());
        }
    }

    @Test void close_while_the_pre_http_session_fence_is_blocked_freezes_unknown_and_prevents_delegate() throws Exception {
        try (var fixture = new Fixture(); var pool = Executors.newFixedThreadPool(2)) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name()); var fenceEntered = new CountDownLatch(1); var releaseFence = new CountDownLatch(1);
            when(fixture.recorder.executeIdempotentStart(any(), any(), any())).thenAnswer(call -> {
                var request = call.getArgument(1, ToolInvocationStore.PreparationRequestVO.class); var observer = call.getArgument(2, ToolCommittedFactObserver.class);
                try {
                    call.getArgument(0, FixedBpmnStartToolTransport.class).withVerifiedCall(fixedRequest(request),
                            (executor, operation) -> { operation.start(); return null; });
                    return fail("Closed admission must not return");
                } catch (FixedBpmnStartToolTransport.Failure expected) {
                    assertEquals(FixedBpmnStartToolTransport.FailureKind.OPERATION_NOT_SENT, expected.kind());
                    observer.onCommitted(ToolExecutionOutcome.Status.FAILED_KNOWN);
                    return fresh(request.action(), attempt(ToolExecutionOutcome.Status.FAILED_KNOWN, ToolInvocationRecorder.AccountingState.SETTLED));
                }
            });
            var execution = pool.submit(() -> scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", () -> {
                fenceEntered.countDown();
                try { assertTrue(releaseFence.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); fail("Fence wait was interrupted"); }
            }));
            assertTrue(fenceEntered.await(5, TimeUnit.SECONDS));
            var closing = pool.submit(scope::close);
            try { closing.get(1, TimeUnit.SECONDS); }
            finally { releaseFence.countDown(); }
            assertThrows(ExecutionException.class, () -> execution.get(5, TimeUnit.SECONDS));
            assertEquals(0, fixture.startDelegates.get()); assertEquals("UNKNOWN", scope.sideEffectOutcome()); assertEquals(1, fixture.events.size());
        }
    }

    @Test void cloned_tools_null_fences_malformed_arguments_and_started_event_failure_stop_before_recorder() {
        try (var fixture = new Fixture()) {
            var scope = fixture.bind(); var nativeStart = tool(scope, START.name());
            var alias = new BaseTool(nativeStart.name(), nativeStart.description()) { @Override public boolean equals(Object value) { return true; } };
            assertThrows(AgentLedgerException.class, () -> scope.execute(alias, ARGUMENTS, HOST, "review_agent", () -> { }));
            assertThrows(AgentLedgerException.class, () -> scope.execute(nativeStart, ARGUMENTS, HOST, "review_agent", null));
            assertThrows(AgentLedgerException.class, () -> scope.execute(nativeStart, Map.of("amount", "twenty"), HOST, "review_agent", () -> { }));
            verifyNoInteractions(fixture.recorder, fixture.start, fixture.access); assertEquals("NONE", scope.sideEffectOutcome()); scope.close();
        }
        try (var fixture = new Fixture()) {
            fixture.failStarted = true; var scope = fixture.bind();
            assertThrows(AgentLedgerException.class, () -> scope.execute(tool(scope, START.name()), ARGUMENTS, HOST, "review_agent", () -> { }));
            verifyNoInteractions(fixture.recorder, fixture.start, fixture.access); assertEquals("NONE", scope.sideEffectOutcome()); scope.close();
        }
    }

    @Test void empty_scope_is_legacy_unknown_while_unused_or_known_start_entries_aggregate_safely() {
        try (var fixture = new Fixture()) {
            var unused = fixture.bind(); assertEquals("NONE", unused.sideEffectOutcome()); unused.close(); assertEquals("NONE", unused.sideEffectOutcome());
        }
        try (var fixture = new Fixture("invoice-1", Set.of(), 0)) {
            var empty = fixture.bind(); assertTrue(empty.declaredTools().isEmpty()); assertEquals("UNKNOWN", empty.sideEffectOutcome()); empty.close();
        }
    }

    private static AdkGovernedProcessToolBridge construct(Object[] values) {
        return new AdkGovernedProcessToolBridge((AgentLedgerService) values[0], (ToolInvocationRecorder) values[1],
                (ReadOnlyDecisionToolTransport) values[2], (FixedBpmnStartToolTransport) values[3], (ToolResultAccessRecorder) values[4],
                (Collection<ToolDescriptor>) values[5], (Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>>) values[6],
                (AdkGovernedProcessToolBridge.StartActionContextResolver) values[7]);
    }
    private static BaseTool tool(AdkGovernedToolBridge.Scope scope, String name) { return scope.declaredTools().stream().filter(value -> value.name().equals(name)).findFirst().orElseThrow(); }
    private static FixedBpmnStartToolTransport.Request fixedRequest(ToolInvocationStore.PreparationRequestVO request) {
        return new FixedBpmnStartToolTransport.Request(request.binding().run(), request.lease(), request.descriptor(), request.action(), request.invocationId(), RUN_DEADLINE);
    }
    private static ToolActionRequest action(String version) { return new ToolActionRequest(ACTOR, START, "invoice-1", version, ARGUMENTS); }
    private static ToolExecutionOutcome source(ToolExecutionOutcome.Status status, BigDecimal cost) {
        return status == ToolExecutionOutcome.Status.SUCCEEDED ? new ToolExecutionOutcome(status, Map.of("instanceId", INSTANCE), RECEIPT, null, cost)
                : new ToolExecutionOutcome(status, Map.of(), null, "SOURCE_" + status.name(), cost);
    }
    private static ToolInvocationRecorder.WriteAttemptResultVO successAttempt(ToolInvocationRecorder.AccountingState accounting) {
        return attempt(ToolExecutionOutcome.Status.SUCCEEDED, accounting);
    }
    private static ToolInvocationRecorder.WriteAttemptResultVO attempt(ToolExecutionOutcome.Status status, ToolInvocationRecorder.AccountingState accounting) {
        return attemptFor(HOST, status, accounting);
    }
    private static ToolInvocationRecorder.WriteAttemptResultVO attemptFor(UUID invocationId, ToolExecutionOutcome.Status status, ToolInvocationRecorder.AccountingState accounting) {
        var outcome = status == ToolExecutionOutcome.Status.SUCCEEDED ? source(status, BigDecimal.ZERO)
                : status == ToolExecutionOutcome.Status.UNKNOWN ? new ToolExecutionOutcome(status, Map.of(), null, "OUTCOME_UNKNOWN", BigDecimal.ZERO)
                : new ToolExecutionOutcome(status, Map.of(), null, "DOWNSTREAM_" + status.name(), BigDecimal.ZERO);
        return new ToolInvocationRecorder.WriteAttemptResultVO(RUN, invocationId, status.name(), 3, 1, NOW, NOW.plusSeconds(1), outcome, true, accounting);
    }
    private static ToolInvocationRecorder.IdempotentStartResultVO fresh(ToolActionRequest action, ToolInvocationRecorder.WriteAttemptResultVO own) {
        return new ToolInvocationRecorder.IdempotentStartResultVO(action.intentId(), "PREPARED", 1, own);
    }
    private static ToolResultAccessRecorder.StartResultAccessVO accessed(ToolResultAccessStore.StartResultAccessRequestVO request,
                                                                         ToolExecutionOutcome outcome, ToolInvocationRecorder.AccountingState accounting) {
        return new ToolResultAccessRecorder.StartResultAccessVO(RUN, request.accessId(), request.action().intentId(), 3, HASH, outcome, accounting);
    }
    private static ToolDescriptor descriptor(String key, String name, ToolPolicy.Risk risk, String resource, Map<String, ToolSchema.ValueType> output) {
        return new ToolDescriptor(key, 1, name, "Governed " + name, new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
                new ToolSchema(output, output.keySet()), new ToolPolicy(risk, key + ".action", resource, HASH, null));
    }
    private static ToolExecutionGrant.BindingVO binding(AgentRunAuthorization.Request run, ToolDescriptor descriptor) {
        return new ToolExecutionGrant.BindingVO(run, descriptor.reference(), descriptor.policy().bindingSha256(), "TIANJI_TOOL_NATIVE_PROCESS",
                URI.create("https://agent.example/api"), descriptor.policy().executionResourceKey(), null);
    }
    private static AgentRunLease lease() { return new AgentRunLease(RUN, "worker", 1, NOW.plusSeconds(30)); }

    private static final class Fixture implements AutoCloseable {
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final ToolInvocationRecorder recorder = mock(ToolInvocationRecorder.class);
        final ReadOnlyDecisionToolTransport decision = mock(ReadOnlyDecisionToolTransport.class);
        final FixedBpmnStartToolTransport start = mock(FixedBpmnStartToolTransport.class);
        final ToolResultAccessRecorder access = mock(ToolResultAccessRecorder.class);
        final AtomicInteger bindingCalls = new AtomicInteger(), databaseTimes = new AtomicInteger(), fences = new AtomicInteger(), startDelegates = new AtomicInteger();
        final List<AdkGovernedProcessToolBridge.StartActionContextRequestVO> contextRequests = new ArrayList<>();
        final List<AdkGovernedToolBridge.ToolEventVO> events = new ArrayList<>();
        final Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>> hostBindings;
        final AdkGovernedProcessToolBridge.StartActionContextResolver resolver;
        final AgentLedgerService.ExecutionMaterial material;
        final AdkGovernedProcessToolBridge bridge;
        AdkGovernedProcessToolBridge.StartActionContextVO context = new AdkGovernedProcessToolBridge.StartActionContextVO("business-v1", Duration.ofDays(1));
        FixedBpmnStartToolTransport.FailureKind delegateFailure, afterTransportFailure;
        boolean resolverFailure, nullContext, failStarted;

        Fixture() { this("invoice-1", Set.of(READ.versionKey(), START.versionKey()), 8); }
        Fixture(String businessKey) { this(businessKey, Set.of(READ.versionKey(), START.versionKey()), 8); }
        Fixture(String businessKey, Set<String> allowed, int maxTools) {
            material = material(businessKey, allowed, maxTools);
            hostBindings = value -> { bindingCalls.incrementAndGet(); return allowed.stream().map(version -> version.equals(READ.versionKey()) ? binding(value.authorization(), READ) : binding(value.authorization(), START)).toList(); };
            resolver = value -> { contextRequests.add(value); if (resolverFailure) throw new IllegalStateException("private resolver"); return nullContext ? null : context; };
            when(ledger.databaseNow()).thenAnswer(call -> { databaseTimes.incrementAndGet(); return NOW; });
            when(recorder.usesLedger(ledger)).thenReturn(true); when(access.usesLedger(ledger)).thenReturn(true);
            when(start.withVerifiedCall(any(), any())).thenAnswer(call -> {
                var result = call.getArgument(1, FixedBpmnStartToolTransport.Callback.class).apply(
                        new ReadOnlyDecisionToolTransport.VerifiedExecutor(TrustedAgentActor.verified(ACTOR.tenantId(), TrustedAgentActor.SubjectType.SERVICE, "process-service", "https://process-idp.example/21"), RUN_DEADLINE),
                        () -> { startDelegates.incrementAndGet(); if (delegateFailure != null) throw new FixedBpmnStartToolTransport.Failure(delegateFailure);
                            return new FixedBpmnStartToolTransport.StartObservation(RECEIPT, INSTANCE); });
                if (afterTransportFailure != null) throw new FixedBpmnStartToolTransport.Failure(afterTransportFailure);
                return result;
            });
            bridge = new AdkGovernedProcessToolBridge(ledger, recorder, decision, start, access, List.of(READ, START), hostBindings, resolver);
            clearInvocations(recorder, decision, start, access);
        }
        AdkGovernedToolBridge.Scope bind() {
            return bridge.bindScope(material, lease(), event -> { if (failStarted && event.phase() == AdkGovernedToolBridge.Phase.STARTED) throw new IllegalStateException("private event"); events.add(event); });
        }
        @Override public void close() { }
    }

    private static AgentLedgerService.ExecutionMaterial material(String businessKey, Set<String> allowed, int maxTools) {
        return material(businessKey, List.of(READ, START), allowed, maxTools);
    }
    private static AgentLedgerService.ExecutionMaterial material(String businessKey, List<ToolDescriptor> tools, Set<String> allowed, int maxTools) {
        String approval = tools.stream().filter(tool -> tool.policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED)
                .map(tool -> tool.policy().approvalPolicyRef()).findFirst().orElse(null);
        var declared = policy(tools.stream().map(ToolDescriptor::versionKey).collect(java.util.stream.Collectors.toSet()), Math.max(maxTools, tools.size()), approval);
        var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), tools.stream().map(ToolDescriptor::reference).toList(), "route", "policy-v1"), declared, Map.of());
        var snapshot = new AgentExecutionSnapshot(definition, HASH, policy(allowed, maxTools, approval), new AgentInputDTO("private task", List.of(), Map.of()), businessKey);
        var run = new AgentRunDTO(RUN, "assistant", 1, null, AgentRunStatusEnum.QUEUED, null, false, 0, 0, 0, RUN_DEADLINE, NOW, null);
        var authorization = new AgentRunAuthorization.Request(ACTOR, RUN, "assistant", 1, HASH, "b".repeat(64), "c".repeat(64), "policy-v1", false, RUN_DEADLINE);
        return new AgentLedgerService.ExecutionMaterial(run, snapshot, authorization);
    }
    private static ExecutionPolicyDTO policy(Set<String> allowed, int maxTools) {
        return policy(allowed, maxTools, null);
    }
    private static ExecutionPolicyDTO policy(Set<String> allowed, int maxTools, String approval) {
        return new ExecutionPolicyDTO(Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofSeconds(5), 4, maxTools,
                1000, 1000, null, null, 2, allowed, "retry-v1", approval);
    }
}
