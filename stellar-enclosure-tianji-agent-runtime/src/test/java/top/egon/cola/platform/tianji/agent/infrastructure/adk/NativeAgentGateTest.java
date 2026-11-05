package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.flows.llmflows.AgentTransfer;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.ExecutableCode;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.google.genai.types.PartialArg;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class NativeAgentGateTest {
    private static final String ROUTE = "research-route@1#" + "a".repeat(64);
    private static final String POLICY = "research-policy@1#" + "b".repeat(64);

    @Test
    void native_agent_gate_is_present() {
        assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.agent.infrastructure.adk.NativeAgentGate"));
    }

    @Test
    void binds_distinct_model_wrappers_only_when_they_share_one_explicit_base_llm() {
        var shared = new ScriptedLlm("gpt-5");
        var accepted = tree(shared, shared, shared);
        assertNotSame(accepted.root().model().orElseThrow(), accepted.quick().model().orElseThrow());
        assertDoesNotThrow(() -> bind(accepted, (author, payload) -> { }));

        var different = tree(shared, new ScriptedLlm("gpt-5"), shared);
        assertThrows(AgentLedgerException.class, () -> bind(different, (author, payload) -> { }));

        var nameOnly = tree(shared, null, shared);
        assertTrue(nameOnly.quick().model().orElseThrow().model().isEmpty());
        assertThrows(AgentLedgerException.class, () -> bind(nameOnly, (author, payload) -> { }));
    }

    @Test
    void recognizes_only_the_exact_native_method_and_real_agent_name_declaration() throws Exception {
        var fixture = fixture((author, payload) -> { });
        Method nativeMethod = AgentTransfer.class.getMethod("transferToAgent", String.class, ToolContext.class);
        var exact = FunctionTool.create(nativeMethod);
        var wrong = FunctionTool.create(Math.class.getMethod("abs", int.class));
        var declaration = exact.declaration().orElseThrow();
        var parameters = declaration.parameters().orElseThrow();

        assertAll(
                () -> assertEquals(nativeMethod, exact.func()),
                () -> assertEquals("transfer_to_agent", exact.name()),
                () -> assertEquals("transfer_to_agent", declaration.name().orElseThrow()),
                () -> assertEquals("OBJECT", parameters.type().orElseThrow().toString()),
                () -> assertEquals(List.of("agent_name"), parameters.required().orElseThrow()),
                () -> assertEquals(List.of("agent_name"),
                        parameters.properties().orElseThrow().keySet().stream().toList()),
                () -> assertEquals("STRING",
                        parameters.properties().orElseThrow().get("agent_name").type().orElseThrow().toString()),
                () -> assertTrue(fixture.gate().isNativeTransfer(exact)),
                () -> assertFalse(fixture.gate().isNativeTransfer(wrong)),
                () -> assertFalse(fixture.gate().isNativeTransfer(mock(BaseTool.class))));
    }

    @Test
    void rejects_unknown_self_and_peer_targets_as_sticky_policy_failures() throws Exception {
        for (String target : List.of("unknown_agent", "conditional_root")) {
            var fixture = fixture((author, payload) -> { });
            assertThrows(AgentLedgerException.class, () -> fixture.gate().observeResponse(
                    callback(fixture.rootContext(), "root-model"),
                    transferResponse(target, "call-" + target, null)));
            fixture.gate().failureSignal().test().assertError(AgentLedgerException.class);
        }

        var peer = fixture((author, payload) -> { });
        var quickContext = peer.rootContext().toBuilder().agent(peer.tree().quick()).build();
        transfer(peer.gate(), peer.rootContext(), "quick_research", "root-call", null);
        assertThrows(AgentLedgerException.class, () -> peer.gate().observeResponse(
                callback(quickContext, "quick-model"),
                transferResponse("deep_research", "peer-call", "peer output")));
        peer.gate().failureSignal().test().assertError(AgentLedgerException.class);
    }

    @Test
    void rejects_extra_arguments_multiple_function_calls_and_unsafe_present_provider_id() {
        var extraArguments = FunctionCall.builder().id("extra-args").name("transfer_to_agent")
                .args(Map.of("agent_name", "quick_research", "extra", true)).build();
        assertRejectedRootResponse(response(List.of(extraArguments)));

        var first = FunctionCall.builder().id("first").name("transfer_to_agent")
                .args(Map.of("agent_name", "quick_research")).build();
        var second = FunctionCall.builder().id("second").name("transfer_to_agent")
                .args(Map.of("agent_name", "deep_research")).build();
        assertRejectedRootResponse(response(List.of(first, second)));

        assertRejectedRootResponse(transferResponse("quick_research", " unsafe-id", null));
    }

    @Test
    void accepts_a_missing_provider_id_but_rejects_a_safe_present_id_mismatch() throws Exception {
        var missing = fixture((author, payload) -> { });
        missing.gate().observeResponse(callback(missing.rootContext(), "root-model"),
                transferResponse("quick_research", null, null));
        var generated = tool(missing.rootContext(), "adk-generated");
        missing.gate().beforeTool(transferTool(), Map.of("agent_name", "quick_research"), generated);
        AgentTransfer.transferToAgent("quick_research", generated);
        missing.gate().afterTool(transferTool(), Map.of("agent_name", "quick_research"), generated, Map.of());
        var quickContext = missing.rootContext().toBuilder().agent(missing.tree().quick()).build();
        assertDoesNotThrow(() -> missing.gate().requireExpectedAgent(callback(quickContext, "quick-model")));

        var mismatch = fixture((author, payload) -> { });
        mismatch.gate().observeResponse(callback(mismatch.rootContext(), "root-model"),
                transferResponse("quick_research", "provider-call", null));
        assertThrows(AgentLedgerException.class, () -> mismatch.gate().beforeTool(transferTool(),
                Map.of("agent_name", "quick_research"), tool(mismatch.rootContext(), "actual-call")));
        mismatch.gate().failureSignal().test().assertError(AgentLedgerException.class);
    }

    @Test
    void supervisor_rejects_critic_first_duplicate_child_and_a_fifth_transfer_after_the_cap() throws Exception {
        var criticFirst = supervisorFixture((author, payload) -> { });
        assertThrows(AgentLedgerException.class, () -> criticFirst.gate().observeResponse(
                callback(criticFirst.rootContext(), "root-model"),
                transferResponse("critic", "critic-first", null)));

        var duplicate = supervisorFixture((author, payload) -> { });
        var researcherContext = duplicate.rootContext().toBuilder().agent(duplicate.researcher()).build();
        var returnedRoot = researcherContext.toBuilder().agent(duplicate.root()).build();
        transfer(duplicate.gate(), duplicate.rootContext(), "researcher", "to-researcher", null);
        transfer(duplicate.gate(), researcherContext, "supervisor", "from-researcher", "research output");
        assertThrows(AgentLedgerException.class, () -> duplicate.gate().observeResponse(
                callback(returnedRoot, "root-after-research"),
                transferResponse("researcher", "duplicate-researcher", null)));

        var audits = new AtomicInteger();
        var capped = supervisorFixture((author, payload) -> audits.incrementAndGet());
        var firstResearcher = capped.rootContext().toBuilder().agent(capped.researcher()).build();
        var rootAfterResearcher = firstResearcher.toBuilder().agent(capped.root()).build();
        var criticContext = rootAfterResearcher.toBuilder().agent(capped.critic()).build();
        var rootAfterCritic = criticContext.toBuilder().agent(capped.root()).build();
        transfer(capped.gate(), capped.rootContext(), "researcher", "one", null);
        transfer(capped.gate(), firstResearcher, "supervisor", "two", "research output");
        transfer(capped.gate(), rootAfterResearcher, "critic", "three", null);
        transfer(capped.gate(), criticContext, "supervisor", "four", "critique output");
        assertEquals(4, audits.get());
        assertThrows(AgentLedgerException.class, () -> capped.gate().observeResponse(
                callback(rootAfterCritic, "root-after-critic"),
                transferResponse("researcher", "five", null)));
        assertEquals(4, audits.get());
    }

    @Test
    void before_tool_rejects_a_confirmed_context_and_call_id_before_a_second_audit() throws Exception {
        var audits = new AtomicInteger();
        var fixture = supervisorFixture((author, payload) -> audits.incrementAndGet());
        var originalRootContext = fixture.rootContext();
        var researcherContext = originalRootContext.toBuilder().agent(fixture.researcher()).build();
        transfer(fixture.gate(), originalRootContext, "researcher", "reused-root-call", null);
        transfer(fixture.gate(), researcherContext, "supervisor", "researcher-return", "research output");
        assertEquals(2, audits.get());

        fixture.gate().observeResponse(callback(originalRootContext, "root-before-critic"),
                transferResponse("critic", "reused-root-call", null));
        assertThrows(AgentLedgerException.class, () -> fixture.gate().beforeTool(transferTool(),
                Map.of("agent_name", "critic"), tool(originalRootContext, "reused-root-call")));
        assertEquals(2, audits.get());
        fixture.gate().failureSignal().test().assertError(AgentLedgerException.class);
    }

    @Test
    void follows_expected_roles_across_distinct_per_agent_invocation_contexts() throws Exception {
        var early = fixture((author, payload) -> { });
        var earlyRootContext = early.rootContext();
        var earlyQuickContext = earlyRootContext.toBuilder().agent(early.tree().quick()).build();

        assertNotSame(earlyRootContext, earlyQuickContext);
        assertSame(earlyRootContext.session(), earlyQuickContext.session());
        assertEquals(earlyRootContext.invocationId(), earlyQuickContext.invocationId());
        assertDoesNotThrow(() -> early.gate().requireExpectedAgent(callback(earlyRootContext, "root-model")));
        assertThrows(AgentLedgerException.class,
                () -> early.gate().requireExpectedAgent(callback(earlyQuickContext, "early-child")));

        var flow = fixture((author, payload) -> { });
        var rootContext = flow.rootContext();
        var quickContext = rootContext.toBuilder().agent(flow.tree().quick()).build();
        var returnedRootContext = quickContext.toBuilder().agent(flow.tree().root()).build();
        transfer(flow.gate(), rootContext, "quick_research", "root-call", null);
        assertDoesNotThrow(() -> flow.gate().requireExpectedAgent(callback(quickContext, "quick-model")));
        transfer(flow.gate(), quickContext, "conditional_root", "quick-call", "current summary");
        assertDoesNotThrow(() -> flow.gate().requireExpectedAgent(callback(returnedRootContext, "root-return")));
    }

    @Test
    void pairs_model_and_tool_callbacks_by_exact_context_identity_not_equal_looking_wrappers() throws Exception {
        var fixture = fixture((author, payload) -> { });
        var response = transferResponse("quick_research", "call-1", null);
        fixture.gate().observeResponse(callback(fixture.rootContext(), "model-event"), response);
        var copiedContext = fixture.rootContext().toBuilder().build();

        assertNotSame(fixture.rootContext(), copiedContext);
        assertThrows(AgentLedgerException.class, () -> fixture.gate().beforeTool(transferTool(),
                Map.of("agent_name", "quick_research"), tool(copiedContext, "call-1")));
    }

    @Test
    void accepts_complete_nonpartial_transfer_frames_before_turn_completion_and_commits_text_only_after_action()
            throws Exception {
        var audits = new ArrayList<Audit>();
        var fixture = fixture((author, payload) -> audits.add(new Audit(author, Map.copyOf(payload))));
        var rootContext = fixture.rootContext();
        var quickContext = rootContext.toBuilder().agent(fixture.tree().quick()).build();
        var returnedRootContext = quickContext.toBuilder().agent(fixture.tree().root()).build();
        transfer(fixture.gate(), rootContext, "quick_research", "", null);

        var childResponse = transferResponse("conditional_root", "child-call", "current summary\n");
        assertFalse(childResponse.partial().orElseThrow());
        assertFalse(childResponse.turnComplete().orElseThrow());
        fixture.gate().observeResponse(callback(quickContext, "child-model"), childResponse);
        var childTool = tool(quickContext, "child-call");
        fixture.gate().beforeTool(transferTool(), Map.of("agent_name", "conditional_root"), childTool);
        AgentTransfer.transferToAgent("conditional_root", childTool);
        fixture.gate().afterTool(transferTool(), Map.of("agent_name", "conditional_root"), childTool, Map.of());

        completeRoot(fixture.gate(), returnedRootContext, "genuine root final");
        assertEquals(Map.of("conditional_root", "genuine root final", "quick_research", "current summary\n"),
                fixture.gate().requireCompletedOutputs());
        assertEquals(2, audits.size());
        assertEquals(Map.of("to", "quick_research", "ordinal", 1,
                "topologyHash", conditionalSpec().topologyHash(POLICY), "state", "AUTHORIZED"),
                audits.getFirst().payload());
    }

    @Test
    void terminal_guard_rejects_missing_outputs_and_action_mismatch_without_continuing_the_fixture()
            throws Exception {
        var missingRoot = fixture((author, payload) -> { });
        var quickContext = missingRoot.rootContext().toBuilder().agent(missingRoot.tree().quick()).build();
        transfer(missingRoot.gate(), missingRoot.rootContext(), "quick_research", "root-call", null);
        transfer(missingRoot.gate(), quickContext, "conditional_root", "quick-call", "current summary");
        assertThrows(AgentLedgerException.class, missingRoot.gate()::requireCompletedOutputs);

        var missingChild = fixture((author, payload) -> { });
        assertThrows(AgentLedgerException.class,
                () -> completeRoot(missingChild.gate(), missingChild.rootContext(), "premature root"));

        var mismatch = fixture((author, payload) -> { });
        mismatch.gate().observeResponse(callback(mismatch.rootContext(), "root-model"),
                transferResponse("quick_research", "mismatch-call", null));
        var mismatchedTool = tool(mismatch.rootContext(), "mismatch-call");
        mismatch.gate().beforeTool(transferTool(), Map.of("agent_name", "quick_research"), mismatchedTool);
        AgentTransfer.transferToAgent("deep_research", mismatchedTool);
        assertThrows(AgentLedgerException.class, () -> mismatch.gate().afterTool(transferTool(),
                Map.of("agent_name", "quick_research"), mismatchedTool, Map.of()));
    }

    @Test
    void rejects_oversize_blank_control_and_unpaired_surrogate_child_text() throws Exception {
        assertInvalidChildText(" ");
        assertInvalidChildText("x".repeat(8_193));
        assertInvalidChildText("safe" + (char) 0x0085 + "unsafe");
        assertInvalidChildText("safe" + (char) 0xD800 + "unsafe");

        var fixture = fixture((author, payload) -> { });
        var quickContext = fixture.rootContext().toBuilder().agent(fixture.tree().quick()).build();
        transfer(fixture.gate(), fixture.rootContext(), "quick_research", "root-call", null);
        assertDoesNotThrow(() -> fixture.gate().observeResponse(callback(quickContext, "child-model"),
                transferResponse("conditional_root", "child-call", "x".repeat(8_192))));
    }

    @Test
    void unsafe_call_id_and_child_text_each_set_a_sticky_failure() throws Exception {
        var unsafeId = fixture((author, payload) -> { });
        unsafeId.gate().observeResponse(callback(unsafeId.rootContext(), "root-model"),
                transferResponse("quick_research", "call-1", null));
        assertThrows(AgentLedgerException.class, () -> unsafeId.gate().beforeTool(transferTool(),
                Map.of("agent_name", "quick_research"), tool(unsafeId.rootContext(), " call-1")));
        unsafeId.gate().failureSignal().test().assertError(AgentLedgerException.class);

        var unsafeText = fixture((author, payload) -> { });
        var quickContext = unsafeText.rootContext().toBuilder().agent(unsafeText.tree().quick()).build();
        transfer(unsafeText.gate(), unsafeText.rootContext(), "quick_research", "root-call", null);
        assertThrows(AgentLedgerException.class, () -> unsafeText.gate().observeResponse(
                callback(quickContext, "child-model"),
                transferResponse("conditional_root", "child-call", "safe" + (char) 0x0085 + "unsafe")));
        unsafeText.gate().failureSignal().test().assertError(AgentLedgerException.class);
    }

    @Test
    void pins_session_identity_and_invocation_id_and_rejects_a_foreign_root_final() throws Exception {
        var clonedSession = fixture((author, payload) -> { });
        clonedSession.gate().requireExpectedAgent(callback(clonedSession.rootContext(), "root-model"));
        Session sameValues = Session.builder("session-1").appName("app").userId("user").state(Map.of()).build();
        InvocationContext clonedContext = clonedSession.rootContext().toBuilder().session(sameValues).build();
        assertThrows(AgentLedgerException.class,
                () -> clonedSession.gate().requireExpectedAgent(callback(clonedContext, "cloned-session")));
        clonedSession.gate().failureSignal().test().assertError(AgentLedgerException.class);

        var wrongInvocation = fixture((author, payload) -> { });
        var quickContext = wrongInvocation.rootContext().toBuilder().agent(wrongInvocation.tree().quick()).build();
        transfer(wrongInvocation.gate(), wrongInvocation.rootContext(), "quick_research", "root-call", null);
        transfer(wrongInvocation.gate(), quickContext, "conditional_root", "quick-call", "current summary");
        Event event = finalEvent(wrongInvocation.tree().root(), "foreign-invocation", "stale root final");
        assertThrows(AgentLedgerException.class, () -> wrongInvocation.gate().observeEvent(event,
                Optional.of(outputProjection(wrongInvocation.tree().root(), "stale root final"))));
        wrongInvocation.gate().failureSignal().test().assertError(AgentLedgerException.class);
    }

    @Test
    void append_failure_does_not_advance_and_before_after_replay_is_bounded() throws Exception {
        var attempts = new AtomicInteger();
        var failed = fixture((author, payload) -> {
            attempts.incrementAndGet();
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "controlled conflict");
        });
        failed.gate().observeResponse(callback(failed.rootContext(), "root-model"),
                transferResponse("quick_research", "call-1", null));
        assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                assertThrows(AgentLedgerException.class, () -> failed.gate().beforeTool(transferTool(),
                        Map.of("agent_name", "quick_research"), tool(failed.rootContext(), "call-1"))).code());
        assertEquals(1, attempts.get());

        var repeatAttempts = new AtomicInteger();
        var repeatedBefore = fixture((author, payload) -> repeatAttempts.incrementAndGet());
        repeatedBefore.gate().observeResponse(callback(repeatedBefore.rootContext(), "root-model"),
                transferResponse("quick_research", "call-2", null));
        var repeatedContext = tool(repeatedBefore.rootContext(), "call-2");
        repeatedBefore.gate().beforeTool(transferTool(), Map.of("agent_name", "quick_research"), repeatedContext);
        assertThrows(AgentLedgerException.class, () -> repeatedBefore.gate().beforeTool(transferTool(),
                Map.of("agent_name", "quick_research"), repeatedContext));
        assertEquals(1, repeatAttempts.get());

        var repeatedAfter = fixture((author, payload) -> { });
        repeatedAfter.gate().observeResponse(callback(repeatedAfter.rootContext(), "root-model"),
                transferResponse("quick_research", "call-3", null));
        var confirmedContext = tool(repeatedAfter.rootContext(), "call-3");
        repeatedAfter.gate().beforeTool(transferTool(), Map.of("agent_name", "quick_research"), confirmedContext);
        AgentTransfer.transferToAgent("quick_research", confirmedContext);
        repeatedAfter.gate().afterTool(transferTool(), Map.of("agent_name", "quick_research"), confirmedContext, Map.of());
        assertDoesNotThrow(() -> repeatedAfter.gate().afterTool(transferTool(),
                Map.of("agent_name", "quick_research"), confirmedContext, Map.of()));
    }

    @Test
    void replays_sanitized_authorization_ledger_timeout_and_unknown_failure_classifications() {
        var deniedGate = fixture((author, payload) -> { }).gate();
        var denied = deniedGate.markFatal(new AgentExecutionAuthorization.Failure(
                AgentRunAuthorization.Decision.DENY));
        assertInstanceOf(AgentExecutionAuthorization.Failure.class, denied);
        deniedGate.failureSignal().test().assertError(error -> error instanceof AgentExecutionAuthorization.Failure value
                && value.decision() == AgentRunAuthorization.Decision.DENY);

        var ledgerGate = fixture((author, payload) -> { }).gate();
        var ledger = assertInstanceOf(AgentLedgerException.class, ledgerGate.markFatal(
                new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "private-ledger")));
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, ledger.code());
        assertFalse(ledger.getMessage().contains("private-ledger"));
        ledgerGate.failureSignal().test().assertError(error -> error instanceof AgentLedgerException value
                && value.code() == AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED);

        var timeoutGate = fixture((author, payload) -> { }).gate();
        var timeout = timeoutGate.markFatal(new RuntimeException("private-timeout", new TimeoutException("private")));
        assertTrue(hasCause(timeout, TimeoutException.class));
        assertFalse(timeout.getMessage().contains("private-timeout"));
        timeoutGate.failureSignal().test().assertError(error -> hasCause(error, TimeoutException.class));

        var unknownGate = fixture((author, payload) -> { }).gate();
        var unknown = assertInstanceOf(AgentLedgerException.class,
                unknownGate.markFatal(new IllegalStateException("private-unknown")));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, unknown.code());
        assertFalse(unknown.getMessage().contains("private-unknown"));
    }

    @Test
    void bounded_plan_route_and_repeated_role() throws Exception {
        var audits = new ArrayList<Audit>();
        var fixture = boundedFixture((author, payload) -> audits.add(new Audit(author, Map.copyOf(payload))));
        String plan = boundedPlan("""
                {"taskId":"task-1","agent":"bounded_researcher","question":"first question"},
                {"taskId":"task-2","agent":"bounded_researcher","question":"second question"}
                """);
        ToolContext firstOutbound = captureBoundedTransfer(fixture.gate(), fixture.rootContext(),
                boundedPlanResponse(plan, "bounded_researcher", "out-1"), "bounded_researcher", "out-1");
        confirmBoundedTransfer(fixture.gate(), fixture.rootContext(), firstOutbound, "bounded_researcher", Map.of());
        InvocationContext firstChild = fixture.rootContext().toBuilder().agent(fixture.researcher()).build();
        LlmRequest.Builder firstRequest = requestBuilder();
        assertDoesNotThrow(() -> fixture.gate().prepareModelRequest(callback(firstChild, "before-child-1"), firstRequest));
        assertEquals(1, countQuestionMarkers(firstRequest));
        assertTrue(requestText(firstRequest).contains("first question"));
        assertDoesNotThrow(() -> fixture.gate().prepareModelRequest(callback(firstChild, "before-child-1-retry"), firstRequest));
        assertEquals(1, countQuestionMarkers(firstRequest));
        ToolContext firstReturn = captureBoundedTransfer(fixture.gate(), firstChild,
                transferResponse("bounded_supervisor", "back-1", "first summary"), "bounded_supervisor", "back-1");
        confirmBoundedTransfer(fixture.gate(), firstChild, firstReturn, "bounded_supervisor", Map.of());
        InvocationContext routeRoot = firstChild.toBuilder().agent(fixture.root()).build();
        ToolContext secondOutbound = captureBoundedTransfer(fixture.gate(), routeRoot,
                transferResponse("bounded_researcher", "out-2", null), "bounded_researcher", "out-2");
        confirmBoundedTransfer(fixture.gate(), routeRoot, secondOutbound, "bounded_researcher", Map.of());
        InvocationContext secondChild = routeRoot.toBuilder().agent(fixture.researcher()).build();
        assertNotSame(firstChild, secondChild);
        LlmRequest.Builder secondRequest = requestBuilder();
        assertDoesNotThrow(() -> fixture.gate().prepareModelRequest(callback(secondChild, "before-child-2"), secondRequest));
        assertTrue(requestText(secondRequest).contains("second question"));
        assertFalse(requestText(secondRequest).contains("first question"));
        ToolContext secondReturn = captureBoundedTransfer(fixture.gate(), secondChild,
                transferResponse("bounded_supervisor", "back-2", "second summary"), "bounded_supervisor", "back-2");
        confirmBoundedTransfer(fixture.gate(), secondChild, secondReturn, "bounded_supervisor", Map.of());
        InvocationContext finalRoot = secondChild.toBuilder().agent(fixture.root()).build();
        completeBoundedRoot(fixture.gate(), finalRoot, "final answer");
        assertEquals(List.of(
                new NativeAgentGate.TaskResultVO("task-1", "bounded_researcher", "first summary"),
                new NativeAgentGate.TaskResultVO("task-2", "bounded_researcher", "second summary")),
                fixture.gate().requireCompletedTasks());
        assertEquals(Map.of("bounded_supervisor", "final answer", "bounded_researcher", "second summary"),
                fixture.gate().requireCompletedOutputs());
        assertEquals(4, audits.size());
        for (int index = 0; index < audits.size(); index++) {
            assertEquals(Set.of("to", "ordinal", "topologyHash", "planHash", "taskOrdinal", "state"),
                    audits.get(index).payload().keySet());
            assertEquals(index + 1, ((Number) audits.get(index).payload().get("ordinal")).intValue());
            assertEquals(index / 2 + 1, ((Number) audits.get(index).payload().get("taskOrdinal")).intValue());
            assertFalse(audits.get(index).payload().containsValue("task-1"));
            assertFalse(audits.get(index).payload().containsValue("first question"));
        }
    }

    @Test
    void bounded_context_window_and_stored_confirmation() throws Exception {
        var fixture = boundedFixture((author, payload) -> { });
        String plan = boundedPlan("""
                {"taskId":"task-1","agent":"bounded_researcher","question":"first"},
                {"taskId":"task-2","agent":"bounded_researcher","question":"second"}
                """);
        ToolContext firstOutbound = captureBoundedTransfer(fixture.gate(), fixture.rootContext(),
                boundedPlanResponse(plan, "bounded_researcher", "out-1"), "bounded_researcher", "out-1");
        confirmBoundedTransfer(fixture.gate(), fixture.rootContext(), firstOutbound,
                "bounded_researcher", Map.of("receipt", "one"));
        assertDoesNotThrow(() -> fixture.gate().afterTool(transferTool(),
                Map.of("agent_name", "bounded_researcher"), firstOutbound, Map.of("receipt", "one")));
        InvocationContext firstChild = fixture.rootContext().toBuilder().agent(fixture.researcher()).build();
        LlmRequest.Builder firstBuilder = requestBuilder();
        assertDoesNotThrow(() -> fixture.gate().prepareModelRequest(callback(firstChild, "child-1"), firstBuilder));
        ToolContext firstReturn = captureBoundedTransfer(fixture.gate(), firstChild,
                transferResponse("bounded_supervisor", "back-1", "first summary"), "bounded_supervisor", "back-1");
        confirmBoundedTransfer(fixture.gate(), firstChild, firstReturn, "bounded_supervisor", Map.of());
        InvocationContext routeRoot = firstChild.toBuilder().agent(fixture.root()).build();
        ToolContext secondOutbound = captureBoundedTransfer(fixture.gate(), routeRoot,
                transferResponse("bounded_researcher", "out-2", null), "bounded_researcher", "out-2");
        confirmBoundedTransfer(fixture.gate(), routeRoot, secondOutbound, "bounded_researcher", Map.of());
        assertDoesNotThrow(() -> fixture.gate().afterTool(transferTool(),
                Map.of("agent_name", "bounded_researcher"), firstOutbound, Map.of("receipt", "one")));
        InvocationContext secondChild = routeRoot.toBuilder().agent(fixture.researcher()).build();
        assertThrows(AgentLedgerException.class, () -> fixture.gate().prepareModelRequest(callback(secondChild, "child-2"), firstBuilder));

        var staleContext = boundedFixture((author, payload) -> { });
        ToolContext staleFirstOutbound = captureBoundedTransfer(staleContext.gate(), staleContext.rootContext(),
                boundedPlanResponse(plan, "bounded_researcher", "stale-out-1"), "bounded_researcher", "stale-out-1");
        confirmBoundedTransfer(staleContext.gate(), staleContext.rootContext(), staleFirstOutbound,
                "bounded_researcher", Map.of());
        InvocationContext staleFirstChild = staleContext.rootContext().toBuilder().agent(staleContext.researcher()).build();
        assertDoesNotThrow(() -> staleContext.gate().prepareModelRequest(
                callback(staleFirstChild, "stale-child-1"), requestBuilder()));
        ToolContext staleFirstReturn = captureBoundedTransfer(staleContext.gate(), staleFirstChild,
                transferResponse("bounded_supervisor", "stale-back-1", "first summary"), "bounded_supervisor", "stale-back-1");
        confirmBoundedTransfer(staleContext.gate(), staleFirstChild, staleFirstReturn, "bounded_supervisor", Map.of());
        InvocationContext staleRouteRoot = staleFirstChild.toBuilder().agent(staleContext.root()).build();
        ToolContext staleSecondOutbound = captureBoundedTransfer(staleContext.gate(), staleRouteRoot,
                transferResponse("bounded_researcher", "stale-out-2", null), "bounded_researcher", "stale-out-2");
        confirmBoundedTransfer(staleContext.gate(), staleRouteRoot, staleSecondOutbound, "bounded_researcher", Map.of());
        assertThrows(AgentLedgerException.class, () -> staleContext.gate().prepareModelRequest(
                callback(staleFirstChild, "stale-child-reused"), requestBuilder()));

        var changedReplay = boundedFixture((author, payload) -> { });
        ToolContext changed = captureBoundedTransfer(changedReplay.gate(), changedReplay.rootContext(),
                boundedPlanResponse(boundedPlan("""
                        {"taskId":"only","agent":"bounded_researcher","question":"question"}
                        """), "bounded_researcher", "out"), "bounded_researcher", "out");
        confirmBoundedTransfer(changedReplay.gate(), changedReplay.rootContext(), changed,
                "bounded_researcher", Map.of("receipt", "original"));
        assertThrows(AgentLedgerException.class, () -> changedReplay.gate().afterTool(transferTool(),
                Map.of("agent_name", "bounded_researcher"), changed, Map.of("receipt", "changed")));
    }

    @Test
    void bounded_partial_semantic_allowlist() {
        List<Part> forbidden = List.of(
                Part.builder().functionResponse(FunctionResponse.builder().name("other").response(Map.of()).build()).build(),
                Part.builder().executableCode(ExecutableCode.builder().language("PYTHON").code("print('x')").build()).build(),
                Part.builder().inlineData(com.google.genai.types.Blob.builder()
                        .mimeType("text/plain").data(new byte[]{1}).build()).build());
        for (Part part : forbidden) {
            var fixture = boundedFixture((author, payload) -> { });
            LlmResponse partial = LlmResponse.builder().content(Content.fromParts(part))
                    .partial(true).turnComplete(false).build();
            assertThrows(AgentLedgerException.class, () -> fixture.gate().validateResponseEnvelope(
                    callback(fixture.rootContext(), "partial"), partial));
        }
        var allowed = boundedFixture((author, payload) -> { });
        String plan = boundedPlan("""
                {"taskId":"one","agent":"bounded_researcher","question":"question"}
                """);
        FunctionCall normal = FunctionCall.builder().id("call").name("transfer_to_agent")
                .args(Map.of("agent_name", "bounded_researcher")).build();
        LlmResponse withMetadata = LlmResponse.builder().content(Content.builder().role("model").parts(
                        Part.builder().text(plan).partMetadata(Map.of("provider", "normal")).build(),
                        Part.builder().functionCall(normal).thoughtSignature(new byte[]{1, 2}).build()).build())
                .partial(false).turnComplete(false).build();
        assertDoesNotThrow(() -> allowed.gate().observeResponse(callback(allowed.rootContext(), "complete"), withMetadata));
        var partialArgs = boundedFixture((author, payload) -> { });
        FunctionCall incomplete = FunctionCall.builder().id("call").name("transfer_to_agent")
                .args(Map.of("agent_name", "bounded_researcher"))
                .partialArgs(PartialArg.builder().jsonPath("$.agent_name").stringValue("bounded_researcher").build()).build();
        assertThrows(AgentLedgerException.class, () -> partialArgs.gate().validateResponseEnvelope(
                callback(partialArgs.rootContext(), "complete"), response(List.of(incomplete))));
        var continuing = boundedFixture((author, payload) -> { });
        FunctionCall unfinished = FunctionCall.builder().id("call").name("transfer_to_agent")
                .args(Map.of("agent_name", "bounded_researcher")).willContinue(true).build();
        assertThrows(AgentLedgerException.class, () -> continuing.gate().validateResponseEnvelope(
                callback(continuing.rootContext(), "complete"), response(List.of(unfinished))));
    }

    @Test
    void bounded_final_candidate_is_one_shot() throws Exception {
        var wrongText = readyForBoundedFinal();
        assertDoesNotThrow(() -> wrongText.fixture().gate().observeResponse(
                callback(wrongText.finalRoot(), "candidate"), finalResponse("expected")));
        Event wrong = finalEvent(wrongText.fixture().root(), wrongText.finalRoot().invocationId(), "wrong");
        assertThrows(AgentLedgerException.class, () -> wrongText.fixture().gate().observeEvent(wrong,
                Optional.of(outputProjection(wrongText.fixture().root(), "wrong"))));
        var secondCandidate = readyForBoundedFinal();
        assertDoesNotThrow(() -> secondCandidate.fixture().gate().observeResponse(
                callback(secondCandidate.finalRoot(), "candidate-1"), finalResponse("expected")));
        assertThrows(AgentLedgerException.class, () -> secondCandidate.fixture().gate().observeResponse(
                callback(secondCandidate.finalRoot(), "candidate-2"), finalResponse("expected")));
        var oldRoot = readyForBoundedFinal();
        assertThrows(AgentLedgerException.class, () -> oldRoot.fixture().gate().observeResponse(
                callback(oldRoot.fixture().rootContext(), "old-root"), finalResponse("expected")));
        var consumed = readyForBoundedFinal();
        assertDoesNotThrow(() -> consumed.fixture().gate().observeResponse(
                callback(consumed.finalRoot(), "candidate"), finalResponse("expected")));
        Event exact = finalEvent(consumed.fixture().root(), consumed.finalRoot().invocationId(), "expected");
        assertDoesNotThrow(() -> consumed.fixture().gate().observeEvent(
                exact, Optional.of(outputProjection(consumed.fixture().root(), "expected"))));
        assertEquals(1, consumed.fixture().gate().requireCompletedTasks().size());
        assertThrows(AgentLedgerException.class, () -> consumed.fixture().gate().observeEvent(
                exact, Optional.of(outputProjection(consumed.fixture().root(), "expected"))));
    }

    @Test
    void bounded_audit_and_failed_cas() throws Exception {
        var audits = new ArrayList<Audit>();
        var successful = boundedFixture((author, payload) -> audits.add(new Audit(author, Map.copyOf(payload))));
        String plan = boundedPlan("""
                {"taskId":"private-task-id","agent":"bounded_researcher","question":"private question"}
                """);
        ToolContext outbound = captureBoundedTransfer(successful.gate(), successful.rootContext(),
                boundedPlanResponse(plan, "bounded_researcher", "out"), "bounded_researcher", "out");
        assertEquals(1, audits.size());
        assertEquals(Set.of("to", "ordinal", "topologyHash", "planHash", "taskOrdinal", "state"),
                audits.getFirst().payload().keySet());
        assertFalse(audits.getFirst().payload().containsValue("private-task-id"));
        assertFalse(audits.getFirst().payload().containsValue("private question"));
        confirmBoundedTransfer(successful.gate(), successful.rootContext(), outbound, "bounded_researcher", Map.of());
        var attempts = new AtomicInteger();
        var failed = boundedFixture((author, payload) -> {
            attempts.incrementAndGet();
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "private conflict");
        });
        assertDoesNotThrow(() -> failed.gate().observeResponse(callback(failed.rootContext(), "plan"),
                boundedPlanResponse(plan, "bounded_researcher", "out")));
        AgentLedgerException conflict = assertThrows(AgentLedgerException.class, () -> failed.gate().beforeTool(
                transferTool(), Map.of("agent_name", "bounded_researcher"), tool(failed.rootContext(), "out")));
        assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, conflict.code());
        assertFalse(conflict.getMessage().contains("private conflict"));
        assertEquals(1, attempts.get());
        assertThrows(AgentLedgerException.class, failed.gate()::requireCompletedTasks);
        var unknown = boundedFixture((author, payload) -> { throw new IllegalStateException("private sentinel"); });
        assertDoesNotThrow(() -> unknown.gate().observeResponse(callback(unknown.rootContext(), "plan"),
                boundedPlanResponse(plan, "bounded_researcher", "out")));
        AgentLedgerException safe = assertInstanceOf(AgentLedgerException.class,
                assertThrows(RuntimeException.class, () -> unknown.gate().beforeTool(transferTool(),
                        Map.of("agent_name", "bounded_researcher"), tool(unknown.rootContext(), "out"))));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, safe.code());
        assertNull(safe.getCause());
        assertFalse(safe.getMessage().contains("private sentinel"));
    }

    private static Fixture fixture(NativeAgentGate.AuthorizationAppender appender) {
        var model = new ScriptedLlm("gpt-5");
        var tree = tree(model, model, model);
        var gate = bind(tree, appender);
        BaseSessionService sessions = mock(BaseSessionService.class);
        Session session = Session.builder("session-1").appName("app").userId("user").state(Map.of()).build();
        InvocationContext context = InvocationContext.builder().sessionService(sessions).agent(tree.root())
                .session(session).invocationId("invocation-1").build();
        return new Fixture(gate, tree, context);
    }

    private static SupervisorFixture supervisorFixture(NativeAgentGate.AuthorizationAppender appender) {
        var model = new ScriptedLlm("gpt-5");
        var researcher = LlmAgent.builder().name("researcher").model(model).outputKey("researcher_output")
                .disallowTransferToParent(false).disallowTransferToPeers(true).build();
        var critic = LlmAgent.builder().name("critic").model(model).outputKey("critic_output")
                .disallowTransferToParent(false).disallowTransferToPeers(true).build();
        var root = LlmAgent.builder().name("supervisor").model(model).outputKey("supervisor_root_output")
                .subAgents(researcher, critic).disallowTransferToParent(true).disallowTransferToPeers(true).build();
        var spec = supervisorSpec();
        var gate = NativeAgentGate.bind(spec, root, spec.topologyHash(POLICY), appender);
        BaseSessionService sessions = mock(BaseSessionService.class);
        Session session = Session.builder("session-1").appName("app").userId("user").state(Map.of()).build();
        InvocationContext context = InvocationContext.builder().sessionService(sessions).agent(root)
                .session(session).invocationId("invocation-1").build();
        return new SupervisorFixture(gate, root, researcher, critic, context);
    }

    private static BoundedFixture boundedFixture(NativeAgentGate.AuthorizationAppender appender) {
        var model = new ScriptedLlm("gpt-5");
        var researcher = LlmAgent.builder().name("bounded_researcher").model(model)
                .outputKey("bounded_researcher_output")
                .disallowTransferToParent(false).disallowTransferToPeers(true).build();
        var critic = LlmAgent.builder().name("bounded_critic").model(model)
                .outputKey("bounded_critic_output")
                .disallowTransferToParent(false).disallowTransferToPeers(true).build();
        var root = LlmAgent.builder().name("bounded_supervisor").model(model)
                .outputKey("bounded_supervisor_output").subAgents(researcher, critic)
                .disallowTransferToParent(true).disallowTransferToPeers(true).build();
        NativeAgentCompilerStrategy.TemplateSpec spec = boundedSpec();
        NativeAgentGate gate = NativeAgentGate.bind(spec, root, spec.topologyHash(POLICY), appender);
        BaseSessionService sessions = mock(BaseSessionService.class);
        Session session = Session.builder("session-1").appName("app").userId("user").state(Map.of()).build();
        InvocationContext context = InvocationContext.builder().sessionService(sessions).agent(root)
                .session(session).invocationId("invocation-1").build();
        return new BoundedFixture(gate, root, researcher, critic, context);
    }

    private static NativeAgentGate bind(Tree tree, NativeAgentGate.AuthorizationAppender appender) {
        return NativeAgentGate.bind(conditionalSpec(), tree.root(), conditionalSpec().topologyHash(POLICY), appender);
    }

    private static Tree tree(BaseLlm rootModel, BaseLlm quickModel, BaseLlm deepModel) {
        var quick = quickModel == null
                ? LlmAgent.builder().name("quick_research").model("gpt-5").outputKey("quick_research_output")
                .disallowTransferToParent(false).disallowTransferToPeers(true).build()
                : LlmAgent.builder().name("quick_research").model(quickModel).outputKey("quick_research_output")
                .disallowTransferToParent(false).disallowTransferToPeers(true).build();
        var deep = LlmAgent.builder().name("deep_research").model(deepModel).outputKey("deep_research_output")
                .disallowTransferToParent(false).disallowTransferToPeers(true).build();
        var root = LlmAgent.builder().name("conditional_root").model(rootModel).outputKey("conditional_root_output")
                .subAgents(quick, deep).disallowTransferToParent(true).disallowTransferToPeers(true).build();
        return new Tree(root, quick, deep);
    }

    private static NativeAgentCompilerStrategy.TemplateSpec conditionalSpec() {
        return new NativeAgentCompilerStrategy.TemplateSpec("conditional-research-v1",
                NativeAgentCompilerStrategy.TransferTopologyEnum.CONDITIONAL_RESEARCH_V1,
                "researchModel", "gpt-5", ROUTE, "1", "1", "conditional_root",
                List.of(
                        new NativeAgentCompilerStrategy.AgentSpec(
                                "conditional_root", null, "conditional_root_output", true, true),
                        new NativeAgentCompilerStrategy.AgentSpec(
                                "quick_research", "conditional_root", "quick_research_output", false, true),
                        new NativeAgentCompilerStrategy.AgentSpec(
                                "deep_research", "conditional_root", "deep_research_output", false, true)),
                2, 2, List.of("quick_research", "deep_research"));
    }

    private static NativeAgentCompilerStrategy.TemplateSpec supervisorSpec() {
        return new NativeAgentCompilerStrategy.TemplateSpec("supervisor-research-v1",
                NativeAgentCompilerStrategy.TransferTopologyEnum.SUPERVISOR_RESEARCH_V1,
                "researchModel", "gpt-5", ROUTE, "1", "1", "supervisor",
                List.of(
                        new NativeAgentCompilerStrategy.AgentSpec(
                                "supervisor", null, "supervisor_root_output", true, true),
                        new NativeAgentCompilerStrategy.AgentSpec(
                                "researcher", "supervisor", "researcher_output", false, true),
                        new NativeAgentCompilerStrategy.AgentSpec(
                                "critic", "supervisor", "critic_output", false, true)),
                2, 4, List.of("researcher", "critic"));
    }

    private static NativeAgentCompilerStrategy.TemplateSpec boundedSpec() {
        return new NativeAgentCompilerStrategy.TemplateSpec("bounded-research-v1",
                NativeAgentCompilerStrategy.TransferTopologyEnum.BOUNDED_RESEARCH_V1,
                "researchModel", "gpt-5", ROUTE, "1", "2", "bounded_supervisor",
                List.of(
                        new NativeAgentCompilerStrategy.AgentSpec(
                                "bounded_supervisor", null, "bounded_supervisor_output", true, true),
                        new NativeAgentCompilerStrategy.AgentSpec(
                                "bounded_researcher", "bounded_supervisor", "bounded_researcher_output", false, true),
                        new NativeAgentCompilerStrategy.AgentSpec(
                                "bounded_critic", "bounded_supervisor", "bounded_critic_output", false, true)),
                2, 16, List.of("bounded_researcher", "bounded_critic"));
    }

    private static CallbackContext callback(InvocationContext context, String eventId) {
        return new CallbackContext(context, EventActions.builder().build(), eventId);
    }

    private static ToolContext tool(InvocationContext context, String callId) {
        return ToolContext.builder(context).actions(EventActions.builder().build())
                .functionCallId(callId).eventId("tool-" + callId).build();
    }

    private static FunctionTool transferTool() throws Exception {
        return FunctionTool.create(AgentTransfer.class.getMethod(
                "transferToAgent", String.class, ToolContext.class));
    }

    private static LlmResponse transferResponse(String target, String callId, String text) {
        var call = FunctionCall.builder().name("transfer_to_agent").args(Map.of("agent_name", target));
        if (callId != null) call.id(callId);
        List<Part> parts = new ArrayList<>();
        if (text != null) {
            parts.add(Part.builder().text("private thought").thought(true).build());
            parts.add(Part.fromText(text));
        }
        parts.add(Part.builder().functionCall(call.build()).build());
        return LlmResponse.builder().content(Content.builder().role("model").parts(parts).build())
                .partial(false).turnComplete(false).build();
    }

    private static String boundedPlan(String taskRows) {
        return "{\"schemaVersion\":\"1\",\"tasks\":[" + taskRows.strip() + "]}";
    }

    private static LlmResponse boundedPlanResponse(String plan, String target, String callId) {
        FunctionCall call = FunctionCall.builder().id(callId).name("transfer_to_agent")
                .args(Map.of("agent_name", target)).build();
        return LlmResponse.builder().content(Content.builder().role("model")
                        .parts(Part.fromText(plan), Part.builder().functionCall(call).build()).build())
                .partial(false).turnComplete(false).build();
    }

    private static LlmResponse finalResponse(String text) {
        return LlmResponse.builder().content(Content.builder().role("model")
                        .parts(Part.fromText(text)).build())
                .partial(false).turnComplete(true).build();
    }

    private static LlmRequest.Builder requestBuilder() {
        return LlmRequest.builder().model("gpt-5").contents(List.of(
                Content.builder().role("user").parts(Part.fromText("existing input")).build()));
    }

    private static String requestText(LlmRequest.Builder request) {
        return request.build().contents().stream()
                .flatMap(content -> content.parts().orElse(List.of()).stream())
                .map(part -> part.text().orElse(""))
                .reduce("", String::concat);
    }

    private static long countQuestionMarkers(LlmRequest.Builder request) {
        return request.build().contents().stream()
                .flatMap(content -> content.parts().orElse(List.of()).stream())
                .map(part -> part.text().orElse(""))
                .filter(text -> text.contains("TIANJI_UNTRUSTED_TASK_QUESTION_V1"))
                .count();
    }

    private static LlmResponse response(List<FunctionCall> calls) {
        return LlmResponse.builder().content(Content.builder().role("model")
                        .parts(calls.stream().map(call -> Part.builder().functionCall(call).build()).toList()).build())
                .partial(false).turnComplete(false).build();
    }

    private static ToolContext captureBoundedTransfer(NativeAgentGate gate, InvocationContext source,
                                                      LlmResponse response, String target,
                                                      String callId) throws Exception {
        assertDoesNotThrow(() -> gate.observeResponse(callback(source, "model-" + callId), response));
        ToolContext tool = tool(source, callId);
        assertDoesNotThrow(() -> gate.beforeTool(transferTool(), Map.of("agent_name", target), tool));
        return tool;
    }

    private static void confirmBoundedTransfer(NativeAgentGate gate, InvocationContext source,
                                               ToolContext context, String target,
                                               Map<String, Object> result) throws Exception {
        assertSame(source, context.invocationContext());
        AgentTransfer.transferToAgent(target, context);
        assertDoesNotThrow(() -> gate.afterTool(
                transferTool(), Map.of("agent_name", target), context, result));
    }

    private static void completeBoundedRoot(NativeAgentGate gate, InvocationContext context, String text) {
        assertDoesNotThrow(() -> gate.observeResponse(callback(context, "final-model"), finalResponse(text)));
        Event event = finalEvent(context.agent(), context.invocationId(), text);
        assertDoesNotThrow(() -> gate.observeEvent(event, Optional.of(outputProjection(context.agent(), text))));
    }

    private static FinalReady readyForBoundedFinal() throws Exception {
        BoundedFixture fixture = boundedFixture((author, payload) -> { });
        String plan = boundedPlan("""
                {"taskId":"only","agent":"bounded_researcher","question":"question"}
                """);
        ToolContext outbound = captureBoundedTransfer(fixture.gate(), fixture.rootContext(),
                boundedPlanResponse(plan, "bounded_researcher", "out"), "bounded_researcher", "out");
        confirmBoundedTransfer(fixture.gate(), fixture.rootContext(), outbound, "bounded_researcher", Map.of());
        InvocationContext child = fixture.rootContext().toBuilder().agent(fixture.researcher()).build();
        ToolContext returned = captureBoundedTransfer(fixture.gate(), child,
                transferResponse("bounded_supervisor", "back", "summary"), "bounded_supervisor", "back");
        confirmBoundedTransfer(fixture.gate(), child, returned, "bounded_supervisor", Map.of());
        return new FinalReady(fixture, child.toBuilder().agent(fixture.root()).build());
    }

    private static void assertRejectedRootResponse(LlmResponse response) {
        var fixture = fixture((author, payload) -> { });
        assertThrows(AgentLedgerException.class,
                () -> fixture.gate().observeResponse(callback(fixture.rootContext(), "root-model"), response));
        fixture.gate().failureSignal().test().assertError(AgentLedgerException.class);
    }

    private static void transfer(NativeAgentGate gate, InvocationContext source,
                                 String target, String callId, String text) throws Exception {
        gate.observeResponse(callback(source, "model-" + callId), transferResponse(target, callId, text));
        var toolContext = tool(source, callId.isEmpty() ? "adk-generated" : callId);
        gate.beforeTool(transferTool(), Map.of("agent_name", target), toolContext);
        AgentTransfer.transferToAgent(target, toolContext);
        gate.afterTool(transferTool(), Map.of("agent_name", target), toolContext, Map.of());
    }

    private static void completeRoot(NativeAgentGate gate, InvocationContext context, String text) {
        Event event = finalEvent(context.agent(), context.invocationId(), text);
        assertTrue(event.finalResponse());
        gate.observeEvent(event, Optional.of(outputProjection(context.agent(), text)));
    }

    private static Event finalEvent(BaseAgent agent, String invocationId, String text) {
        return Event.builder().invocationId(invocationId).author(agent.name())
                .content(Content.fromParts(Part.fromText(text))).partial(false).turnComplete(true).build();
    }

    private static AdkEventProjector.Projection outputProjection(BaseAgent agent, String text) {
        return new AdkEventProjector.Projection(
                "AGENT_OUTPUT", agent.name(), Map.of("text", text), true);
    }

    private static void assertInvalidChildText(String text) throws Exception {
        var fixture = fixture((author, payload) -> { });
        var quickContext = fixture.rootContext().toBuilder().agent(fixture.tree().quick()).build();
        transfer(fixture.gate(), fixture.rootContext(), "quick_research", "root-call", null);
        assertThrows(AgentLedgerException.class, () -> fixture.gate().observeResponse(
                callback(quickContext, "child-model"),
                transferResponse("conditional_root", "child-call", text)));
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 64; depth++, current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    private record Tree(LlmAgent root, LlmAgent quick, LlmAgent deep) {
    }

    private record Fixture(NativeAgentGate gate, Tree tree, InvocationContext rootContext) {
    }

    private record SupervisorFixture(NativeAgentGate gate, LlmAgent root, LlmAgent researcher,
                                     LlmAgent critic, InvocationContext rootContext) {
    }

    private record BoundedFixture(NativeAgentGate gate, LlmAgent root, LlmAgent researcher,
                                  LlmAgent critic, InvocationContext rootContext) {
    }

    private record FinalReady(BoundedFixture fixture, InvocationContext finalRoot) {
    }

    private record Audit(String author, Map<String, Object> payload) {
    }

    private static final class ScriptedLlm extends BaseLlm {
        private ScriptedLlm(String model) {
            super(model);
        }

        @Override
        public Flowable<LlmResponse> generateContent(LlmRequest request, boolean stream) {
            return Flowable.never();
        }

        @Override
        public BaseLlmConnection connect(LlmRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
