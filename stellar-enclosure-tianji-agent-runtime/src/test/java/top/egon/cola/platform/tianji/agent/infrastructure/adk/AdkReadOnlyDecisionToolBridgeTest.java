package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.models.springai.ToolConverter;
import com.google.adk.tools.BaseTool;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdkReadOnlyDecisionToolBridgeTest {
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z"), DEADLINE = NOW.plusSeconds(60);
    private static final String RUN = "10000000-0000-0000-0000-000000000001", HASH = "a".repeat(64);
    private static final UUID HOST = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/21");
    private static final ToolDescriptor TOOL = descriptor(ToolPolicy.Risk.READ_ONLY);
    private static final Map<String, Object> ARGUMENTS = Map.of("amount", 20, "approved", true, "note", "private arguments");

    @Test void each_binding_owns_fresh_exact_native_declarations_with_closed_primitive_schema_and_no_fallback() throws Exception {
        var recorder = mock(ToolInvocationRecorder.class); var transport = mock(ReadOnlyDecisionToolTransport.class); var bridge = bridge(recorder, transport);
        var material = material(TOOL, "CONFIG", policy(Set.of(TOOL.versionKey()), 4)); var events = new ArrayList<AdkReadOnlyDecisionToolBridge.ToolEventVO>();
        var first = bridge.bind(material, lease(), events::add); var second = bridge.bind(material, lease(), events::add); assertNotNull(first); assertNotNull(second);
        assertEquals(1, first.declaredTools().size()); var tool = first.declaredTools().getFirst(); assertNotSame(tool, second.declaredTools().getFirst());
        assertEquals(TOOL.name(), tool.name()); var declaration = tool.declaration().orElseThrow();
        assertTrue(declaration.parameters().isEmpty()); var schema = (Map<?, ?>) declaration.parametersJsonSchema().orElseThrow();
        assertEquals("object", schema.get("type")); assertEquals(false, schema.get("additionalProperties")); assertEquals(List.of("amount"), schema.get("required"));
        var properties = (Map<?, ?>) schema.get("properties"); assertEquals(Map.of("type", "integer"), properties.get("amount"));
        assertEquals(Map.of("type", "boolean"), properties.get("approved")); assertEquals(Map.of("type", "string"), properties.get("note"));
        var converted = new ToolConverter().convertToSpringAiTools(Map.of(tool.name(), tool));
        assertFalse(new ObjectMapper().readTree(converted.getFirst().getToolDefinition().inputSchema()).get("additionalProperties").booleanValue());
        assertEquals(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, assertThrows(AgentLedgerException.class, () -> tool.runAsync(ARGUMENTS, null).blockingGet()).code());
        assertThrows(UnsupportedOperationException.class, () -> first.declaredTools().clear()); assertEquals("NONE", first.sideEffectOutcome());
        verifyNoInteractions(recorder, transport); first.close(); second.close();
    }

    @Test void exact_pointer_and_normalized_arguments_precede_started_then_recorder_then_finished_and_settled_envelope() {
        var recorder = mock(ToolInvocationRecorder.class); var transport = mock(ReadOnlyDecisionToolTransport.class); var events = new ArrayList<AdkReadOnlyDecisionToolBridge.ToolEventVO>();
        var scope = bridge(recorder, transport).bind(material(TOOL, "CONFIG", policy(Set.of(TOOL.versionKey()), 4)), lease(), events::add); assertNotNull(scope);
        when(recorder.executeReadOnly(eq(transport), any())).thenAnswer(call -> { assertEquals(1, events.size()); assertEquals(AdkReadOnlyDecisionToolBridge.Phase.STARTED, events.getFirst().phase()); return result("SUCCEEDED", ToolInvocationRecorder.AccountingState.SETTLED); });
        var response = scope.execute(scope.declaredTools().getFirst(), ARGUMENTS, HOST, "review_agent"); assertNotNull(response);
        assertEquals(Map.of("status", "SUCCEEDED", "output", Map.of("value", "private decision output")), response);
        var request = ArgumentCaptor.forClass(ToolInvocationStore.PreparationRequestVO.class); verify(recorder).executeReadOnly(eq(transport), request.capture());
        assertEquals(20L, request.getValue().arguments().get("amount")); assertEquals(HOST, request.getValue().invocationId()); assertEquals(BigDecimal.ZERO, request.getValue().estimatedCost());
        assertNull(request.getValue().action()); assertNull(request.getValue().intentExpiry());
        assertEquals(2, events.size()); assertEquals("REQUESTED", events.getFirst().state()); assertEquals("SUCCEEDED", events.getLast().state());
        assertEquals(TOOL.reference(), events.getLast().toolRef()); assertEquals(HOST, events.getLast().hostInvocationId()); assertEquals(1, events.getLast().dispatchEpoch());
        assertFalse(events.toString().contains("private")); assertThrows(UnsupportedOperationException.class, () -> response.clear()); scope.close();
    }

    @Test void cloned_or_equal_named_tools_unknown_arguments_and_closed_scopes_never_reach_recorder() {
        var recorder = mock(ToolInvocationRecorder.class); var transport = mock(ReadOnlyDecisionToolTransport.class); var events = new ArrayList<AdkReadOnlyDecisionToolBridge.ToolEventVO>();
        var scope = bridge(recorder, transport).bind(material(TOOL, "CONFIG", policy(Set.of(TOOL.versionKey()), 4)), lease(), events::add); assertNotNull(scope);
        var nativeTool = scope.declaredTools().getFirst();
        var alias = new BaseTool(nativeTool.name(), nativeTool.description()) { @Override public boolean equals(Object other) { return true; } @Override public int hashCode() { return nativeTool.hashCode(); } };
        assertThrows(AgentLedgerException.class, () -> scope.execute(alias, ARGUMENTS, HOST, "review_agent"));
        assertThrows(AgentLedgerException.class, () -> scope.execute(nativeTool, Map.of("amount", 20L, "tenantId", 22L), HOST, "review_agent"));
        assertThrows(AgentLedgerException.class, () -> scope.execute(nativeTool, Map.of("amount", "20"), HOST, "review_agent"));
        assertTrue(events.isEmpty()); scope.close();
        assertThrows(AgentLedgerException.class, () -> scope.execute(nativeTool, ARGUMENTS, HOST, "review_agent")); verifyNoInteractions(recorder, transport);
    }

    @Test void only_settled_known_results_cross_the_model_boundary_and_failures_never_return_private_output() {
        for (var variant : List.of("REJECTED", "FAILED_KNOWN", "UNKNOWN", "DISPATCHING", "SUCCEEDED", "SUCCEEDED_NULL", "SUCCEEDED_MISMATCH", "SUCCEEDED_SCHEMA", "SUCCEEDED_COST")) {
            String state = variant.startsWith("SUCCEEDED_") ? "SUCCEEDED" : variant;
            var recorder = mock(ToolInvocationRecorder.class); var transport = mock(ReadOnlyDecisionToolTransport.class); var events = new ArrayList<AdkReadOnlyDecisionToolBridge.ToolEventVO>();
            var scope = bridge(recorder, transport).bind(material(TOOL, "CONFIG", policy(Set.of(TOOL.versionKey()), 4)), lease(), events::add); assertNotNull(scope);
            var accounting = variant.equals("SUCCEEDED") || state.equals("DISPATCHING") ? ToolInvocationRecorder.AccountingState.PENDING : ToolInvocationRecorder.AccountingState.SETTLED;
            var supplied = result(state, accounting);
            if (variant.equals("SUCCEEDED_NULL")) supplied = new ToolInvocationRecorder.RecordResult(RUN, HOST, state, 3, 1, NOW, NOW.plusSeconds(1), null, true, accounting);
            if (variant.equals("SUCCEEDED_SCHEMA")) supplied = new ToolInvocationRecorder.RecordResult(RUN, HOST, state, 3, 1, NOW, NOW.plusSeconds(1),
                    new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("value", 20L), null, null, BigDecimal.ZERO), true, accounting);
            if (variant.equals("SUCCEEDED_MISMATCH") || variant.equals("SUCCEEDED_COST")) {
                supplied = spy(supplied);
                doReturn(variant.equals("SUCCEEDED_MISMATCH") ? new ToolExecutionOutcome(ToolExecutionOutcome.Status.REJECTED, Map.of(), null, "DECISION_NO_MATCH", BigDecimal.ZERO)
                        : new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("value", "private decision output"), null, null, new BigDecimal("0.10"))).when(supplied).outcome();
            }
            when(recorder.executeReadOnly(eq(transport), any())).thenReturn(supplied);
            if (state.equals("REJECTED") || state.equals("FAILED_KNOWN")) {
                var value = scope.execute(scope.declaredTools().getFirst(), ARGUMENTS, HOST, "review_agent");
                assertEquals(Set.of("status", "errorCode"), value.keySet()); assertFalse(value.containsKey("output"));
            } else {
                var failure = assertThrows(AgentLedgerException.class, () -> scope.execute(scope.declaredTools().getFirst(), ARGUMENTS, HOST, "review_agent"));
                assertEquals(accounting == ToolInvocationRecorder.AccountingState.PENDING ? AgentErrorCodeEnum.TIANJI_USAGE_UNKNOWN : AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code()); assertNull(failure.getCause());
            }
            assertEquals(2, events.size()); assertEquals(AdkReadOnlyDecisionToolBridge.Phase.FINISHED, events.getLast().phase());
            verify(recorder).executeReadOnly(eq(transport), any()); scope.close();
        }
    }

    @Test void missing_or_wrong_identity_results_and_recorder_exceptions_publish_only_safe_unavailable_metadata() {
        for (var variant : List.of("null", "throw", "run", "invocation")) {
            var recorder = mock(ToolInvocationRecorder.class); var transport = mock(ReadOnlyDecisionToolTransport.class); var events = new ArrayList<AdkReadOnlyDecisionToolBridge.ToolEventVO>();
            var scope = bridge(recorder, transport).bind(material(TOOL, "CONFIG", policy(Set.of(TOOL.versionKey()), 4)), lease(), events::add); assertNotNull(scope);
            if (variant.equals("throw")) when(recorder.executeReadOnly(eq(transport), any())).thenThrow(new IllegalStateException("private arguments"));
            else if (variant.equals("null")) when(recorder.executeReadOnly(eq(transport), any())).thenReturn(null);
            else {
                var original = result("SUCCEEDED", ToolInvocationRecorder.AccountingState.SETTLED);
                when(recorder.executeReadOnly(eq(transport), any())).thenReturn(new ToolInvocationRecorder.RecordResult(variant.equals("run") ? "other-run" : RUN,
                        variant.equals("invocation") ? UUID.randomUUID() : HOST, original.state(), original.revision(), original.dispatchEpoch(), original.dispatchedAt(), original.finishedAt(), original.outcome(), original.operationInvoked(), original.accountingState()));
            }
            var failure = assertThrows(AgentLedgerException.class, () -> scope.execute(scope.declaredTools().getFirst(), ARGUMENTS, HOST, "review_agent"));
            assertNull(failure.getCause()); assertFalse(failure.getMessage().contains("private"));
            assertEquals("UNAVAILABLE", events.getLast().state()); assertNull(events.getLast().accountingState()); assertFalse(events.toString().contains("private"));
            verify(recorder).executeReadOnly(eq(transport), any()); scope.close();
        }
    }

    @Test void event_write_failures_stop_admission_or_suppress_the_model_result_without_repeating_the_receipt() {
        for (int failAt : List.of(1, 2)) {
            var recorder = mock(ToolInvocationRecorder.class); var transport = mock(ReadOnlyDecisionToolTransport.class); var count = new AtomicInteger();
            when(recorder.executeReadOnly(eq(transport), any())).thenReturn(result("SUCCEEDED", ToolInvocationRecorder.AccountingState.SETTLED));
            var scope = bridge(recorder, transport).bind(material(TOOL, "CONFIG", policy(Set.of(TOOL.versionKey()), 4)), lease(), event -> { if (count.incrementAndGet() == failAt) throw new IllegalStateException("private event failure"); }); assertNotNull(scope);
            var failure = assertThrows(AgentLedgerException.class, () -> scope.execute(scope.declaredTools().getFirst(), ARGUMENTS, HOST, "review_agent")); assertNull(failure.getCause());
            verify(recorder, times(failAt == 1 ? 0 : 1)).executeReadOnly(eq(transport), any()); assertEquals(failAt, count.get()); scope.close();
        }
    }

    @Test void host_bindings_are_exact_visible_read_only_refs_and_cannot_widen_the_fixed_material() {
        var recorder = mock(ToolInvocationRecorder.class); var transport = mock(ReadOnlyDecisionToolTransport.class); var material = material(TOOL, "CONFIG", policy(Set.of(TOOL.versionKey()), 4));
        var correct = binding(material.authorization(), TOOL);
        var changed = new ToolExecutionGrant.BindingVO(correct.run(), correct.tool(), "b".repeat(64), correct.purpose(), correct.resource(), correct.authorizationResourceKey(), null);
        var other = new ToolDescriptor("other.read", 1, "other_read", "Other", TOOL.input(), TOOL.output(), TOOL.policy());
        for (var supplied : List.of(List.<ToolExecutionGrant.BindingVO>of(), List.of(correct, correct), List.of(changed), List.of(binding(material.authorization(), other)))) {
            var bridge = new AdkReadOnlyDecisionToolBridge(recorder, transport, List.of(TOOL), value -> supplied);
            assertThrows(AgentLedgerException.class, () -> bridge.bind(material, lease(), event -> { }));
        }
        var write = descriptor(ToolPolicy.Risk.IDEMPOTENT_WRITE); var writeMaterial = material(write, "CONFIG", policy(Set.of(write.versionKey()), 4));
        assertThrows(AgentLedgerException.class, () -> new AdkReadOnlyDecisionToolBridge(recorder, transport, List.of(write), value -> List.of(binding(value.authorization(), write))).bind(writeMaterial, lease(), event -> { }));
        assertThrows(AgentLedgerException.class, () -> bridge(recorder, transport).bind(material(TOOL, "CODE_FIRST", policy(Set.of(TOOL.versionKey()), 4)), lease(), event -> { }));
        var auth = material.authorization(); var drift = new AgentRunAuthorization.Request(auth.actor(), auth.runId(), auth.definitionKey(), auth.definitionVersion(), "b".repeat(64), auth.requestSha256(), auth.snapshotSha256(), auth.policyVersionRef(), false, auth.deadlineAt());
        assertThrows(AgentLedgerException.class, () -> bridge(recorder, transport).bind(new AgentLedgerService.ExecutionMaterial(material.run(), material.snapshot(), drift), lease(), event -> { }));
        verifyNoInteractions(recorder, transport);
    }

    @Test void zero_visible_tools_keep_legacy_effect_semantics_and_configuration_is_an_immutable_bounded_registry() {
        var recorder = mock(ToolInvocationRecorder.class); var transport = mock(ReadOnlyDecisionToolTransport.class); var registrations = new ArrayList<>(List.of(TOOL));
        var bridge = new AdkReadOnlyDecisionToolBridge(recorder, transport, registrations, value -> List.of()); registrations.clear();
        var scope = bridge.bind(material(TOOL, "CONFIG", policy(Set.of(), 0)), lease(), event -> { }); assertNotNull(scope); assertTrue(scope.declaredTools().isEmpty()); assertEquals("UNKNOWN", scope.sideEffectOutcome()); scope.close();
        assertTrue(bridge.usesRecorder(recorder)); assertTrue(bridge.usesTransport(transport)); assertFalse(bridge.usesRecorder(mock(ToolInvocationRecorder.class)));
        assertThrows(IllegalArgumentException.class, () -> new AdkReadOnlyDecisionToolBridge(null, transport, List.of(TOOL), value -> List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AdkReadOnlyDecisionToolBridge(recorder, transport, List.of(TOOL, TOOL), value -> List.of()));
    }

    @Test void typed_events_are_closed_validated_and_redacted_metadata() {
        assertEquals(Set.of("phase", "author", "toolRef", "hostInvocationId", "state", "dispatchEpoch", "accountingState", "errorCode"),
                Arrays.stream(AdkReadOnlyDecisionToolBridge.ToolEventVO.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet()));
        assertThrows(IllegalArgumentException.class, () -> new AdkReadOnlyDecisionToolBridge.ToolEventVO(null, "review_agent", TOOL.reference(), HOST, "REQUESTED", 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AdkReadOnlyDecisionToolBridge.ToolEventVO(AdkReadOnlyDecisionToolBridge.Phase.STARTED, " ", TOOL.reference(), HOST, "REQUESTED", 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AdkReadOnlyDecisionToolBridge.ToolEventVO(AdkReadOnlyDecisionToolBridge.Phase.STARTED, "review_agent", TOOL.reference(), HOST, "private arguments", 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AdkReadOnlyDecisionToolBridge.ToolEventVO(AdkReadOnlyDecisionToolBridge.Phase.STARTED, "review_agent", TOOL.reference(), HOST, "REQUESTED", 1, ToolInvocationRecorder.AccountingState.SETTLED, null));
    }

    @Test void shared_declarations_preserve_the_closed_schema_and_never_execute_a_native_fallback() throws Exception {
        var first = new AdkGovernedToolDeclaration(TOOL); var second = new AdkGovernedToolDeclaration(TOOL);
        assertNotSame(first, second); assertEquals(TOOL.name(), first.name()); assertEquals(TOOL.description(), first.description());
        var declaration = first.declaration().orElseThrow(); assertTrue(declaration.parameters().isEmpty());
        var schema = (Map<?, ?>) declaration.parametersJsonSchema().orElseThrow();
        assertEquals(Set.of("type", "properties", "required", "additionalProperties"), schema.keySet());
        assertEquals("object", schema.get("type")); assertEquals(false, schema.get("additionalProperties")); assertEquals(List.of("amount"), schema.get("required"));
        var properties = (Map<?, ?>) schema.get("properties"); assertEquals(List.of("amount", "approved", "note"), new ArrayList<>(properties.keySet()));
        assertEquals(Map.of("type", "integer"), properties.get("amount")); assertEquals(Map.of("type", "boolean"), properties.get("approved")); assertEquals(Map.of("type", "string"), properties.get("note"));
        assertThrows(UnsupportedOperationException.class, schema::clear); assertThrows(UnsupportedOperationException.class, properties::clear);
        assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>) properties.get("amount")).clear());
        var converted = new ToolConverter().convertToSpringAiTools(Map.of(first.name(), first));
        assertFalse(new ObjectMapper().readTree(converted.getFirst().getToolDefinition().inputSchema()).get("additionalProperties").booleanValue());
        var denied = assertThrows(AgentLedgerException.class, () -> first.runAsync(ARGUMENTS, null).blockingGet());
        assertEquals(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, denied.code()); assertNull(denied.getCause()); assertFalse(denied.getMessage().contains("private"));
        var recorder = mock(ToolInvocationRecorder.class); var transport = mock(ReadOnlyDecisionToolTransport.class);
        try (var scope = bridge(recorder, transport).bind(material(TOOL, "CONFIG", policy(Set.of(TOOL.versionKey()), 4)), lease(), event -> { })) {
            assertInstanceOf(AdkGovernedToolDeclaration.class, scope.declaredTools().getFirst()); verifyNoInteractions(recorder, transport);
        }
    }

    @Test void common_binding_calls_the_existing_bind_once_and_returns_its_identical_scope_and_boxed_events() {
        var recorder = mock(ToolInvocationRecorder.class); var transport = mock(ReadOnlyDecisionToolTransport.class); var resolves = new AtomicInteger();
        var bridge = spy(new AdkReadOnlyDecisionToolBridge(recorder, transport, List.of(TOOL), value -> { resolves.incrementAndGet(); return List.of(binding(value.authorization(), TOOL)); }));
        var oldScope = new AtomicReference<AdkReadOnlyDecisionToolBridge.RunScope>();
        doAnswer(call -> { var value = (AdkReadOnlyDecisionToolBridge.RunScope) call.callRealMethod(); oldScope.set(value); return value; }).when(bridge).bind(any(), any(), any());
        var events = new ArrayList<AdkGovernedToolBridge.ToolEventVO>(); var material = material(TOOL, "CONFIG", policy(Set.of(TOOL.versionKey()), 4));
        try (var scope = bridge.bindScope(material, lease(), events::add)) {
            assertSame(oldScope.get(), scope); assertInstanceOf(AdkReadOnlyDecisionToolBridge.RunScope.class, scope); assertEquals(1, resolves.get());
            when(recorder.executeReadOnly(eq(transport), any())).thenReturn(result("SUCCEEDED", ToolInvocationRecorder.AccountingState.SETTLED));
            var response = scope.execute(scope.declaredTools().getFirst(), ARGUMENTS, HOST, "review_agent", () -> { });
            assertEquals("SUCCEEDED", response.get("status")); assertEquals(2, events.size());
            assertEquals(AdkGovernedToolBridge.Phase.STARTED, events.getFirst().phase()); assertEquals(Long.valueOf(0), events.getFirst().dispatchEpoch());
            assertEquals(AdkGovernedToolBridge.Phase.FINISHED, events.getLast().phase()); assertEquals(Long.valueOf(1), events.getLast().dispatchEpoch());
            assertEquals(TOOL.reference(), events.getLast().toolRef()); assertEquals(HOST, events.getLast().hostInvocationId()); assertFalse(events.toString().contains("private"));
            verify(bridge, times(1)).bind(eq(material), eq(lease()), any());
        }
        assertThrows(AgentLedgerException.class, () -> oldScope.get().execute(oldScope.get().declaredTools().getFirst(), ARGUMENTS, HOST, "review_agent"));
    }

    @Test void common_execution_runs_the_fence_before_events_and_sanitizes_denials_without_recorder_io() {
        var recorder = mock(ToolInvocationRecorder.class); var transport = mock(ReadOnlyDecisionToolTransport.class); var events = new ArrayList<AdkReadOnlyDecisionToolBridge.ToolEventVO>();
        try (var scope = bridge(recorder, transport).bind(material(TOOL, "CONFIG", policy(Set.of(TOOL.versionKey()), 4)), lease(), events::add)) {
            var nativeTool = scope.declaredTools().getFirst(); var calls = new AtomicInteger();
            when(recorder.executeReadOnly(eq(transport), any())).thenAnswer(call -> { assertEquals(1, calls.get()); assertEquals(1, events.size()); return result("SUCCEEDED", ToolInvocationRecorder.AccountingState.SETTLED); });
            var response = scope.execute(nativeTool, ARGUMENTS, HOST, "review_agent", () -> { assertTrue(events.isEmpty()); calls.incrementAndGet(); });
            assertEquals("SUCCEEDED", response.get("status")); assertEquals(1, calls.get());
            events.clear(); clearInvocations(recorder, transport);
            for (String variant : List.of("null", "private", "ledger")) {
                Runnable fence = variant.equals("null") ? null : () -> {
                    if (variant.equals("ledger")) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "private fence input");
                    throw new IllegalStateException("private fence input");
                };
                var failure = assertThrows(AgentLedgerException.class, () -> scope.execute(nativeTool, ARGUMENTS, HOST, "review_agent", fence));
                assertEquals(variant.equals("ledger") ? AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED : AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code());
                assertNull(failure.getCause()); assertFalse(failure.getMessage().contains("private")); assertTrue(events.isEmpty());
            }
            verifyNoInteractions(recorder, transport);
        }
    }

    @Test void already_resolved_readonly_subset_accepts_a_mixed_snapshot_without_resolving_the_full_registry_again() {
        var recorder = mock(ToolInvocationRecorder.class); var transport = mock(ReadOnlyDecisionToolTransport.class); var material = mixedMaterial();
        try (var first = AdkReadOnlyDecisionToolBridge.bindResolved(recorder, transport, material, lease(), event -> { }, List.of(TOOL), List.of(binding(material.authorization(), TOOL)));
             var second = AdkReadOnlyDecisionToolBridge.bindResolved(recorder, transport, material, lease(), event -> { }, List.of(TOOL), List.of(binding(material.authorization(), TOOL)))) {
            assertEquals(1, first.declaredTools().size()); assertEquals(TOOL.name(), first.declaredTools().getFirst().name()); assertNotSame(first.declaredTools().getFirst(), second.declaredTools().getFirst());
            assertEquals("NONE", first.sideEffectOutcome()); assertInstanceOf(AdkGovernedToolBridge.Scope.class, first); verifyNoInteractions(recorder, transport);
        }
        try (var empty = AdkReadOnlyDecisionToolBridge.bindResolved(recorder, transport, material, lease(), event -> { }, List.of(), List.of())) {
            assertTrue(empty.declaredTools().isEmpty()); assertEquals("UNKNOWN", empty.sideEffectOutcome());
        }
    }

    @Test void resolved_subset_still_rejects_missing_extra_duplicate_wrong_material_and_backend_bindings() {
        var recorder = mock(ToolInvocationRecorder.class); var transport = mock(ReadOnlyDecisionToolTransport.class); var material = mixedMaterial(); var correct = binding(material.authorization(), TOOL);
        var changed = new ToolExecutionGrant.BindingVO(correct.run(), correct.tool(), "b".repeat(64), correct.purpose(), correct.resource(), correct.authorizationResourceKey(), null);
        var other = new ToolDescriptor("other.read", 1, "other_read", "Other", TOOL.input(), TOOL.output(), TOOL.policy());
        var auth = material.authorization(); var anotherRun = new AgentRunAuthorization.Request(auth.actor(), "10000000-0000-0000-0000-000000000002", auth.definitionKey(), auth.definitionVersion(), auth.definitionSha256(), auth.requestSha256(), auth.snapshotSha256(), auth.policyVersionRef(), false, auth.deadlineAt());
        for (var supplied : List.of(List.<ToolExecutionGrant.BindingVO>of(), List.of(correct, correct), List.of(changed), List.of(binding(material.authorization(), other)), List.of(binding(anotherRun, TOOL)))) {
            assertThrows(AgentLedgerException.class, () -> AdkReadOnlyDecisionToolBridge.bindResolved(recorder, transport, material, lease(), event -> { }, List.of(TOOL), supplied));
        }
        for (var subset : List.of(List.of(TOOL, TOOL), List.of(other), List.of(descriptor(ToolPolicy.Risk.IDEMPOTENT_WRITE)))) {
            var supplied = subset.stream().map(tool -> binding(material.authorization(), tool)).toList();
            assertThrows(AgentLedgerException.class, () -> AdkReadOnlyDecisionToolBridge.bindResolved(recorder, transport, material, lease(), event -> { }, subset, supplied));
        }
        assertThrows(AgentLedgerException.class, () -> AdkReadOnlyDecisionToolBridge.bindResolved(recorder, transport, material(TOOL, "CODE_FIRST", policy(Set.of(TOOL.versionKey()), 4)), lease(), event -> { }, List.of(TOOL), List.of(correct)));
        assertThrows(AgentLedgerException.class, () -> AdkReadOnlyDecisionToolBridge.bindResolved(recorder, transport, material, lease(), null, List.of(TOOL), List.of(correct)));
        verifyNoInteractions(recorder, transport);
    }

    @Test void common_events_allow_only_closed_metadata_and_distinguish_access_from_dispatch_epochs() {
        var settled = ToolInvocationRecorder.AccountingState.SETTLED;
        for (String state : List.of("SUCCEEDED", "REJECTED", "FAILED_KNOWN")) {
            var event = new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "review_agent", TOOL.reference(), HOST, state, null, settled, state.equals("SUCCEEDED") ? null : "DECISION_NO_MATCH");
            assertNull(event.dispatchEpoch()); assertEquals("AdkGovernedToolBridge.ToolEventVO[redacted]", event.toString());
        }
        for (String state : List.of("PREPARED", "DISPATCHING", "SUCCEEDED", "REJECTED", "FAILED_KNOWN", "UNKNOWN")) assertNotNull(new AdkGovernedToolBridge.ToolEventVO(
                AdkGovernedToolBridge.Phase.FINISHED, "review_agent", TOOL.reference(), HOST, state, 0L, settled, null));
        for (Long epoch : new Long[]{null, 0L}) assertNotNull(new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "review_agent", TOOL.reference(), HOST, "UNAVAILABLE", epoch, null, "TIANJI_DEPENDENCY_UNAVAILABLE"));
        assertNotNull(new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.STARTED, "review_agent", TOOL.reference(), HOST, "REQUESTED", 0L, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(null, "review_agent", TOOL.reference(), HOST, "REQUESTED", 0L, null, null));
        for (String author : List.of(" ", " review_agent", "x".repeat(129), "line\nbreak", "\ud800")) assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.STARTED, author, TOOL.reference(), HOST, "REQUESTED", 0L, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.STARTED, "review_agent", null, HOST, "REQUESTED", 0L, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.STARTED, "review_agent", TOOL.reference(), null, "REQUESTED", 0L, null, null));
        for (Long epoch : new Long[]{null, -1L, 1L}) assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.STARTED, "review_agent", TOOL.reference(), HOST, "REQUESTED", epoch, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.STARTED, "review_agent", TOOL.reference(), HOST, "SUCCEEDED", 0L, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.STARTED, "review_agent", TOOL.reference(), HOST, "REQUESTED", 0L, settled, null));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.STARTED, "review_agent", TOOL.reference(), HOST, "REQUESTED", 0L, null, "UNAVAILABLE"));
        for (String state : List.of("PREPARED", "DISPATCHING", "UNKNOWN", "private input")) assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "review_agent", TOOL.reference(), HOST, state, null, settled, null));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "review_agent", TOOL.reference(), HOST, "SUCCEEDED", -1L, settled, null));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "review_agent", TOOL.reference(), HOST, "SUCCEEDED", 1L, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "review_agent", TOOL.reference(), HOST, "SUCCEEDED", 1L, settled, "private-error"));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "review_agent", TOOL.reference(), HOST, "UNAVAILABLE", 1L, null, "UNAVAILABLE"));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "review_agent", TOOL.reference(), HOST, "UNAVAILABLE", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "review_agent", TOOL.reference(), HOST, "UNAVAILABLE", null, settled, "UNAVAILABLE"));
    }

    private static AgentLedgerService.ExecutionMaterial mixedMaterial() {
        var read = material(TOOL, "CONFIG", policy(Set.of(TOOL.versionKey()), 4));
        var write = new ToolDescriptor("process.start", 1, "start_process", "Fixed process", TOOL.input(), new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
                new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.invoice", "tianji.api.process.instance.start", HASH, null));
        var policy = policy(Set.of(TOOL.versionKey(), write.versionKey()), 4);
        var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1", new DefinitionDependenciesDTO(List.of(), List.of(), List.of(TOOL.reference(), write.reference()), "route", "policy-v1"), policy, Map.of());
        var snapshot = new AgentExecutionSnapshot(definition, HASH, policy, read.snapshot().input(), "invoice-1");
        return new AgentLedgerService.ExecutionMaterial(read.run(), snapshot, read.authorization());
    }

    private static AdkReadOnlyDecisionToolBridge bridge(ToolInvocationRecorder recorder, ReadOnlyDecisionToolTransport transport) {
        return new AdkReadOnlyDecisionToolBridge(recorder, transport, List.of(TOOL), value -> List.of(binding(value.authorization(), TOOL)));
    }
    private static ToolInvocationRecorder.RecordResult result(String state, ToolInvocationRecorder.AccountingState accounting) {
        ToolExecutionOutcome outcome = switch (state) {
            case "SUCCEEDED" -> new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("value", "private decision output"), null, null, BigDecimal.ZERO);
            case "REJECTED", "FAILED_KNOWN" -> new ToolExecutionOutcome(ToolExecutionOutcome.Status.valueOf(state), Map.of(), null, "DECISION_NO_MATCH", BigDecimal.ZERO);
            default -> null;
        };
        return new ToolInvocationRecorder.RecordResult(RUN, HOST, state, 3, 1, NOW, state.equals("DISPATCHING") ? null : NOW.plusSeconds(1), outcome, !state.equals("DISPATCHING") && !state.equals("UNKNOWN"), accounting);
    }
    private static ToolDescriptor descriptor(ToolPolicy.Risk risk) {
        return new ToolDescriptor("decision.read", 1, "read_decision", "Read a fixed decision", new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER, "approved", ToolSchema.ValueType.BOOLEAN, "note", ToolSchema.ValueType.STRING), Set.of("amount")),
                new ToolSchema(Map.of("value", ToolSchema.ValueType.STRING), Set.of("value")), new ToolPolicy(risk, "decision.read", "tianji.api.process.decision.evaluate", HASH, null));
    }
    private static ToolExecutionGrant.BindingVO binding(AgentRunAuthorization.Request run, ToolDescriptor tool) { return new ToolExecutionGrant.BindingVO(run, tool.reference(), tool.policy().bindingSha256(), "TIANJI_TOOL_DECISION", URI.create("https://agent.example/api"), "tianji.api.process.decision.evaluate", null); }
    private static AgentRunLease lease() { return new AgentRunLease(RUN, "worker", 1, NOW.plusSeconds(30)); }
    private static ExecutionPolicyDTO policy(Set<String> allowed, int tools) { return new ExecutionPolicyDTO(Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofSeconds(5), 4, tools, 1000, 1000, null, null, 2, allowed, "retry-v1", null); }
    private static AgentLedgerService.ExecutionMaterial material(ToolDescriptor tool, String mode, ExecutionPolicyDTO effective) {
        var declared = policy(Set.of(tool.versionKey()), 4);
        var definition = new AgentDefinitionSpecDTO("assistant", 1, mode, mode.equals("CODE_FIRST") ? "trusted-code" : null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(tool.reference()), "route", "policy-v1"), declared, Map.of());
        var snapshot = new AgentExecutionSnapshot(definition, HASH, effective, new AgentInputDTO("private task", List.of(), Map.of()), null);
        var run = new AgentRunDTO(RUN, "assistant", 1, null, AgentRunStatusEnum.QUEUED, null, false, 0, 0, 0, DEADLINE, NOW, null);
        var authorization = new AgentRunAuthorization.Request(ACTOR, RUN, "assistant", 1, HASH, "b".repeat(64), "c".repeat(64), "policy-v1", false, DEADLINE);
        return new AgentLedgerService.ExecutionMaterial(run, snapshot, authorization);
    }
}
