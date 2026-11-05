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
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
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

    private static LlmResponse response(List<FunctionCall> calls) {
        return LlmResponse.builder().content(Content.builder().role("model")
                        .parts(calls.stream().map(call -> Part.builder().functionCall(call).build()).toList()).build())
                .partial(false).turnComplete(false).build();
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
