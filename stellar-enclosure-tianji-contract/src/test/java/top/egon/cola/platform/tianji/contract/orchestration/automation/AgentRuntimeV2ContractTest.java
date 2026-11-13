package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultClient;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentClient;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentCloseResult;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupResult;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartResult;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticConditionLiteralVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticTemplatePlanVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TemplateDependencyRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2.SideEffectOutcomeEnum;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeV2ContractTest {

    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);
    private static final long MAX_WIRE_INTEGER = ProcessTypedJson.V2_MAX_WIRE_INTEGER;
    private static final Instant FINISHED = Instant.parse("2026-11-11T11:00:20Z");
    private static final AgentProcessLink LINK = new AgentProcessLink("ps-main", WorkerScopeTypeEnum.BPMN,
            "pi-101", "job-101", A, FINISHED.plusSeconds(10));
    private static final Map<String, Object> REF = Map.of("key", "extract-prompt", "version", 1L, "contentSha256", A);
    private static final Map<String, Object> LEAF = Map.of("agentKey", "extract", "instructionRef", REF,
            "modelRef", "registered-model-1", "toolRefs", List.of());
    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties", Map.of(), "required", List.of(),
            "additionalProperties", false);

    @Test
    void runtime_template_owns_closed_nested_configuration_and_limit_values() {
        assertEquals(Set.of("templateKey", "templateVersion", "runtimeProfile", "executorRef", "inputSchema",
                "outputSchema", "parameterSchema", "configuration", "limits"), Arrays.stream(AgentRuntimeTemplateCommand.class.getDeclaredFields())
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .map(java.lang.reflect.Field::getName).collect(Collectors.toSet()));
        assertEquals(8, AgentRuntimeTemplateCommand.TechnicalLimitsVO.class.getRecordComponents().length);
        assertEquals(AgentRuntimeTemplateCommand.class, AgentRuntimeTemplateCommand.TemplateConfigurationVO.class.getEnclosingClass());
        assertEquals(AgentRuntimeTemplateCommand.class, AgenticTemplatePlanVO.class.getEnclosingClass());
        var template = template(Map.of());
        assertEquals(Set.of("instructionRef", "modelRef", "toolRefs"), template.getConfiguration().toWire().keySet());
        assertFalse(new String(template.canonicalBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("agenticPlan"));
    }

    @Test
    void omitted_plan_is_distinct_from_explicit_plan_but_omitted_defaults_normalize() {
        var minimal = minimalPlan();
        var explicit = new LinkedHashMap<>(minimal);
        explicit.put("strategy", "REASONING");
        explicit.put("links", List.of());
        explicit.put("maxRounds", null);
        explicit.put("maxParallelism", 1);
        explicit.put("merge", "OUTPUT_AGENT");
        explicit.put("sharedFields", List.of());
        explicit.put("termination", Map.of("mode", "ROOT_TERMINAL"));
        var first = template(Map.of("agenticPlan", minimal));
        var second = template(Map.of("agenticPlan", explicit));
        assertArrayEquals(first.canonicalBytes(), second.canonicalBytes());
        assertEquals(first.contentSha256(), second.contentSha256());
        assertNotEquals(template(Map.of()).contentSha256(), first.contentSha256());
        assertTrue(first.getConfiguration().agenticPlan().toWire().containsKey("maxRounds"));
        assertNull(first.getConfiguration().agenticPlan().maxRounds());
        var explicitNull = new LinkedHashMap<String, Object>();
        explicitNull.put("agenticPlan", null);
        assertThrows(IllegalArgumentException.class, () -> template(explicitNull));
    }

    @Test
    void rejects_unknown_null_and_non_integral_wire_values() {
        assertThrows(IllegalArgumentException.class, () -> template(Map.of("agenticPlan", with(minimalPlan(), "strategy", null))));
        assertThrows(IllegalArgumentException.class, () -> template(Map.of("agenticPlan", with(minimalPlan(), "maxParallelism", 1.0))));
        assertThrows(IllegalArgumentException.class, () -> template(Map.of("agenticPlan", with(minimalPlan(), "termination", null))));
        assertThrows(IllegalArgumentException.class, () -> template(Map.of("agenticPlan", with(minimalPlan(), "foreign", true))));
        assertThrows(IllegalArgumentException.class, () -> template(Map.of("agenticPlan",
                with(minimalPlan(), "agents", List.of(with(LEAF, "agentKey", "extract.part"))))));
        assertThrows(IllegalArgumentException.class, () -> template(Map.of("agenticPlan",
                with(minimalPlan(), "agents", List.of(with(LEAF, "credential", "rejected"))))));
        assertThrows(IllegalArgumentException.class, () -> template(Map.of("agenticPlan",
                with(minimalPlan(), "termination", Map.of("mode", "ROOT_TERMINAL", "condition", Map.of("literal", true))))));
        assertEquals(MAX_WIRE_INTEGER, AgentRuntimeTemplateCommand.fromExactWire(with(template(Map.of()).toWire(),
                "templateVersion", MAX_WIRE_INTEGER)).getTemplateVersion());
        for (long oversized : new long[]{MAX_WIRE_INTEGER + 1, 9_007_199_254_740_993L}) {
            assertThrows(IllegalArgumentException.class, () -> AgentRuntimeTemplateCommand.fromExactWire(
                    with(template(Map.of()).toWire(), "templateVersion", oversized)));
        }
        assertEquals("extract-prompt", new TemplateDependencyRefVO("extract-prompt", 1, A).key());
        assertThrows(IllegalArgumentException.class, () -> new TemplateDependencyRefVO(" extract-prompt", 1, A));
        assertThrows(IllegalArgumentException.class, () -> new TemplateDependencyRefVO("extract-prompt", MAX_WIRE_INTEGER + 1, A));
    }

    @Test
    void checks_strategy_merge_edges_and_declared_condition_paths() {
        var handoff = new LinkedHashMap<>(minimalPlan());
        handoff.put("strategy", "HANDOFF");
        handoff.put("maxRounds", 3);
        handoff.put("agents", List.of(LEAF, with(LEAF, "agentKey", "summarize")));
        handoff.put("links", List.of(Map.of("source", "extract", "target", "summarize")));
        handoff.put("sharedFields", List.of("agents.extract.output.category"));
        var nullLiteral = new LinkedHashMap<String, Object>();
        nullLiteral.put("literal", null);
        var condition = Map.of("op", "EQ", "args", List.of(Map.of("path", "agents.extract.output.category"), nullLiteral));
        handoff.put("termination", Map.of("mode", "PREDICATE", "condition", condition));
        assertDoesNotThrow(() -> AgenticTemplatePlanVO.fromExactWire(handoff));
        var literal = assertInstanceOf(AgenticConditionLiteralVO.class,
                AgentRuntimeTemplateCommand.AgenticConditionVO.fromExactWire(nullLiteral));
        assertNull(literal.literal());
        assertTrue(literal.toWire().containsKey("literal"));
        assertThrows(IllegalArgumentException.class, () -> AgentRuntimeTemplateCommand.AgenticConditionVO.fromExactWire(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> AgenticTemplatePlanVO.fromExactWire(with(handoff, "maxRounds", null)));
        assertThrows(IllegalArgumentException.class, () -> AgenticTemplatePlanVO.fromExactWire(with(handoff, "maxParallelism", 2)));
        assertThrows(IllegalArgumentException.class, () -> AgenticTemplatePlanVO.fromExactWire(with(handoff, "merge", "COLLECT_BY_AGENT")));
        assertThrows(IllegalArgumentException.class, () -> AgenticTemplatePlanVO.fromExactWire(with(handoff, "agents", List.of(LEAF, LEAF))));
        assertThrows(IllegalArgumentException.class, () -> AgenticTemplatePlanVO.fromExactWire(with(handoff, "links",
                List.of(Map.of("source", "extract", "target", "absent")))));
        assertThrows(IllegalArgumentException.class, () -> AgenticTemplatePlanVO.fromExactWire(with(handoff, "links",
                List.of(Map.of("source", "extract", "target", "summarize", "extra", true)))));
        assertThrows(IllegalArgumentException.class, () -> AgenticTemplatePlanVO.fromExactWire(with(handoff, "sharedFields",
                List.of("agents.absent.output.category"))));
        var unknownPath = Map.of("op", "EQ", "args", List.of(Map.of("path", "agents.absent.output.category"), nullLiteral));
        assertThrows(IllegalArgumentException.class, () -> AgenticTemplatePlanVO.fromExactWire(with(handoff, "termination",
                Map.of("mode", "PREDICATE", "condition", unknownPath))));
        var parallel = with(with(with(minimalPlan(), "strategy", "PARALLEL"), "merge", "COLLECT_BY_AGENT"), "outputAgentKey", null);
        assertNull(AgenticTemplatePlanVO.fromExactWire(parallel).outputAgentKey());
        assertThrows(IllegalArgumentException.class, () -> AgenticTemplatePlanVO.fromExactWire(with(parallel, "outputAgentKey", "extract")));
    }

    @Test
    void technical_limits_and_linked_commands_bind_bpmn_and_absolute_attempt_deadline() {
        var limits = template(Map.of()).getLimits();
        assertEquals(8192, limits.contextWindowTokens());
        assertThrows(IllegalArgumentException.class, () -> new AgentRuntimeTemplateCommand.TechnicalLimitsVO(Duration.ofSeconds(1),
                Duration.ofSeconds(2), Duration.ofSeconds(1), 10, 10, 1, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new AgentRuntimeTemplateCommand.TechnicalLimitsVO(Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(1), 10, 10, 1, 10, 11));
        var command = new LinkedAgentRuntimeCommand().setLink(LINK).setInput(Map.of("secret", "private"))
                .setParameters(Map.of()).setAttemptDeadlineAt(FINISHED.plusSeconds(60)).setLimits(limits);
        assertTrue(command.isLinkedRuntimeConsistent());
        assertFalse(command.toString().contains("private"));
        assertFalse(command.setAttemptDeadlineAt(FINISHED).isLinkedRuntimeConsistent());
        var cmmn = new AgentProcessLink("ps-main", WorkerScopeTypeEnum.CMMN, "pi-101", "job-101", A, FINISHED.plusSeconds(10));
        assertFalse(command.setLink(cmmn).setAttemptDeadlineAt(FINISHED.plusSeconds(60)).isLinkedRuntimeConsistent());
    }

    @Test
    void terminal_wire_retains_three_digest_golden_forms_and_explicit_nulls() throws Exception {
        var inline = event(11L, AgentRunStatusEnum.COMPLETED, Map.of("category", "A"), null, null, SideEffectOutcomeEnum.NONE);
        assertEquals("cfc6656709e75a76ac881f3882568574e0b059d3de1f2d22aa9036681788a067", inline.eventId());
        assertEquals("87fcfce7edfd5f8b5d55a9cf1c39760b8e2abcb910b9e986fdccc2277a0b9041", inline.resultSha256());
        assertEquals("7af381b5c99b97357cafdcf88237fc4f92ecb78116b29778e69ce8d1016e2f71", inline.fingerprint());
        var artifact = new AgentResultArtifactRefVO("result-101", B, "application/json", 27);
        var referenced = event(11L, AgentRunStatusEnum.COMPLETED, null, artifact, null, SideEffectOutcomeEnum.CONFIRMED);
        assertEquals("9d3684f65517f4c7d184ada544fff69e53245eff31a3b83c74b5aadd44f6e32b", referenced.resultSha256());
        assertEquals("1955c2682676904b5a981396c658f2eb44ef61b99170941d20fa1495d66e87c7", referenced.fingerprint());
        var failed = event(11L, AgentRunStatusEnum.FAILED, null, null, "INVALID_OUTPUT", SideEffectOutcomeEnum.UNKNOWN);
        assertEquals("099ea0d6232dddd88858368dd4b6d439745ec26f53ee0b338192550f2d247074", failed.resultSha256());
        assertEquals("24efc7cfa6f3c3e4becbdf0a844f6f720d5f00fccb6bb5472b21347df14817e2", failed.fingerprint());
        assertEquals(inline.eventId(), referenced.eventId());
        assertEquals(inline.eventId(), failed.eventId());
        assertEquals(23, inline.toWire().size());
        for (String member : List.of("output", "artifactRef", "errorCode")) {
            assertTrue(AgentTerminalEventV2.class.getDeclaredMethod(member).getAnnotation(JsonProperty.class).required());
            assertTrue(inline.toWire().containsKey(member));
        }
        assertNull(inline.toWire().get("artifactRef"));
        assertNull(referenced.toWire().get("output"));
        assertNull(failed.toWire().get("artifactRef"));
        assertEquals(Long.MAX_VALUE, event(Long.MAX_VALUE, AgentRunStatusEnum.COMPLETED, Map.of(), null, null,
                SideEffectOutcomeEnum.NONE).tenantId());
    }

    @Test
    void terminal_rejects_ambiguous_results_and_protects_the_original_snapshot() {
        var artifact = new AgentResultArtifactRefVO("result-101", B, "application/json", MAX_WIRE_INTEGER);
        assertThrows(IllegalArgumentException.class, () -> new AgentResultArtifactRefVO("https://example.test", B, "application/json", 1));
        assertThrows(IllegalArgumentException.class, () -> new AgentResultArtifactRefVO("result-101", B, "application/json; charset=utf-8", 1));
        assertThrows(IllegalArgumentException.class, () -> new AgentResultArtifactRefVO("result-101", B, "application/json", MAX_WIRE_INTEGER + 1));
        assertThrows(IllegalArgumentException.class, () -> event(11, AgentRunStatusEnum.COMPLETED, Map.of(), artifact, null, SideEffectOutcomeEnum.NONE));
        assertThrows(IllegalArgumentException.class, () -> event(11, AgentRunStatusEnum.FAILED, null, null, null, SideEffectOutcomeEnum.NONE));
        assertThrows(IllegalArgumentException.class, () -> event(11, AgentRunStatusEnum.RUNNING, null, null, "RUNNING", SideEffectOutcomeEnum.NONE));
        assertThrows(IllegalArgumentException.class, () -> event(11, AgentRunStatusEnum.COMPLETED, Map.of("x", "a".repeat(8192)),
                null, null, SideEffectOutcomeEnum.NONE));
        var values = new ArrayList<Object>(List.of("private-body"));
        var immutable = event(11, AgentRunStatusEnum.COMPLETED, Map.of("output", values), null, null, SideEffectOutcomeEnum.NONE);
        values.clear();
        assertEquals(List.of("private-body"), immutable.output().get("output"));
        assertFalse(immutable.toString().contains("private-body"));
        assertThrows(IllegalArgumentException.class, () -> new AgentTerminalEventV2(2, "as-main", immutable.eventId(), 11L,
                A, "ps-agent-worker", "dispatch-101", LINK, "extract", 1L, "run-101", 7L, "nx-101", A, A, A,
                AgentRunStatusEnum.COMPLETED, FINISHED, immutable.resultSha256(), Map.of("different", true), null,
                null, SideEffectOutcomeEnum.NONE));
    }

    @Test
    void legacy_clients_fail_closed_for_v2_and_error_codes_append() {
        AgentResultClient oldResult = ignored -> { throw new AssertionError("Unexpected v1 route"); };
        assertThrows(UnsupportedOperationException.class, () -> oldResult.receiveV2(null));
        LinkedAgentClient oldLinked = new LinkedAgentClient() {
            @Override public LinkedAgentStartResult submit(LinkedAgentStartCommand command) { throw new AssertionError("Unexpected v1 route"); }
            @Override public LinkedAgentLookupResult resolve(LinkedAgentLookupQuery query) { throw new AssertionError("Unexpected v1 route"); }
            @Override public LinkedAgentCloseResult close(LinkedAgentLookupQuery query) { throw new AssertionError("Unexpected v1 route"); }
        };
        assertThrows(UnsupportedOperationException.class, () -> oldLinked.submitRuntime(null));
        var oldCodes = List.of("TIANJI_INVALID_COMMAND", "TIANJI_REQUEST_TOO_LARGE", "TIANJI_UNAUTHENTICATED",
                "TIANJI_PERMISSION_DENIED", "TIANJI_RESOURCE_NOT_FOUND", "TIANJI_IDEMPOTENCY_CONFLICT",
                "TIANJI_DEFINITION_VERSION_CONFLICT", "TIANJI_REVISION_CONFLICT", "TIANJI_TENANT_CONTEXT_CHANGED",
                "TIANJI_LEASE_EXPIRED", "TIANJI_SESSION_BUSY", "TIANJI_EVENT_CURSOR_EXPIRED", "TIANJI_RESULT_NOT_READY",
                "TIANJI_CAPACITY_EXCEEDED", "TIANJI_BUDGET_EXCEEDED", "TIANJI_USAGE_UNKNOWN", "TIANJI_FEATURE_NOT_AVAILABLE",
                "TIANJI_DEPENDENCY_UNAVAILABLE", "TIANJI_RUN_TIMED_OUT", "TIANJI_MODEL_IDLE_TIMEOUT", "TIANJI_RUN_INTERRUPTED",
                "TIANJI_SIDE_EFFECT_UNKNOWN", "TIANJI_DISPATCH_EXPIRED", "TIANJI_DISPATCH_CLOSED");
        assertEquals(oldCodes, Arrays.stream(AgentErrorCodeEnum.values()).limit(oldCodes.size()).map(AgentErrorCodeEnum::wire).toList());
        assertEquals("TIANJI_INTERNAL_ERROR", AgentErrorCodeEnum.values()[oldCodes.size()].wire());
    }

    private static AgentTerminalEventV2 event(long tenant, AgentRunStatusEnum status, Map<String, Object> output,
            AgentResultArtifactRefVO artifact, String error, SideEffectOutcomeEnum sideEffect) {
        return new AgentTerminalEventV2(2, "as-main", AgentTerminalEventV2.eventIdFor("as-main", tenant, A, "ps-agent-worker",
                "run-101", 7L), tenant, A, "ps-agent-worker", "dispatch-101", LINK, "extract", 1L, "run-101", 7L,
                "nx-101", A, A, A, status, FINISHED, AgentTerminalEventV2.resultSha256(status, output, artifact, error, sideEffect),
                output, artifact, error, sideEffect);
    }

    private static AgentRuntimeTemplateCommand template(Map<String, Object> options) {
        var configuration = new LinkedHashMap<String, Object>();
        configuration.put("instructionRef", REF);
        configuration.put("modelRef", "registered-model-1");
        configuration.put("toolRefs", List.of());
        configuration.putAll(options);
        var limits = Map.<String, Object>of("totalTimeout", "PT2M", "modelIdleTimeout", "PT20S", "toolTimeout", "PT10S",
                "maxModelCalls", 10, "maxToolCalls", 10, "maxParallelism", 2, "contextWindowTokens", 8192,
                "maxOutputTokensPerCall", 1024);
        return AgentRuntimeTemplateCommand.fromExactWire(Map.of("templateKey", "extract", "templateVersion", 1L,
                "runtimeProfile", "RUNTIME_V2", "executorRef", "registered-executor", "inputSchema", SCHEMA,
                "outputSchema", SCHEMA, "parameterSchema", SCHEMA, "configuration", configuration, "limits", limits));
    }

    private static Map<String, Object> minimalPlan() {
        return Map.of("agents", List.of(LEAF), "outputAgentKey", "extract");
    }

    private static Map<String, Object> with(Map<String, Object> source, String field, Object value) {
        var copy = new LinkedHashMap<>(source);
        copy.put(field, value);
        return copy;
    }
}
