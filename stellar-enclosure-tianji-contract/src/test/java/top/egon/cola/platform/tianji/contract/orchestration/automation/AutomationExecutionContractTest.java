package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationExecutionContractTest {

    private static final String HASH = "a".repeat(64);
    private static final String SIGNAL_TOKEN = "A".repeat(43);
    private static final Instant TIME = Instant.parse("2026-11-12T08:36:00.123456Z");
    private static final ProcessSchemaVO EVENT_SCHEMA = objectSchema("customerId", Map.of("type", "string", "maxLength", 20));
    private static final ProcessSchemaVO HUMAN_SCHEMA = objectSchema("resultRef", Map.of("type", "string", "maxLength", 20));


    @Test
    void automation_start_has_a_separate_closed_shape_without_a_release_choice() {
        assertEquals(Set.of("definitionKey", "businessKey", "commandId", "correlationId", "variables", "agentTaskOverrides"),
                fields(AutomationStartCommand.class));
    }

    @Test
    void event_receipt_preserves_admitted_release_and_explicit_nullable_decision_fields() throws Exception {
        Class<?> type = ProcessEventReceiptDTO.class;
        assertEquals(Set.of("receiptId", "eventId", "state", "releaseVersion", "activationRevision", "admittedAt",
                "instanceId", "nodeExecutionRef", "receivedAt", "decidedAt", "decisionCode"), fields(type));
        for (String field : Set.of("instanceId", "nodeExecutionRef", "decidedAt", "decisionCode")) {
            var inclusion = type.getDeclaredField(field).getAnnotation(JsonInclude.class);
            assertNotNull(inclusion);
            assertEquals(JsonInclude.Include.ALWAYS, inclusion.value());
        }
    }

    @Test
    void technical_human_task_has_a_closed_projection() {
        assertEquals(Set.of("taskId", "instanceId", "nodeId", "state", "assignedToMe", "version", "resultSchema",
                "availableActions", "dueAt"), fields(EngineHumanTaskDTO.class));
    }

    @Test
    void start_reuses_the_original_content_digest_and_reserves_internal_command_ids() {
        var variables = new LinkedHashMap<String, Object>();
        variables.put("10", "十");
        variables.put("2", "二");
        variables.put("amount", "1.00");
        variables.put("é", "\n");
        variables.put("\uD800\uDC00", true);
        variables.put("\uE000", null);
        var template = new AgentTaskBindingVO.TemplateRefVO("template", ProcessTypedJson.V2_MAX_WIRE_INTEGER, HASH);
        var override = new AutomationStartCommand.AgentTaskOverrideVO(template, null);
        var command = AutomationStartCommand.builder().definitionKey("autoFlow").businessKey("order-101")
                .commandId("start-101").correlationId("corr-101").variables(variables)
                .agentTaskOverrides(Map.of("agentNode", override)).build();

        assertTrue(command.isPublicStartValid());
        String expected = "7cfa4c0086eaadc9837ce74cbf54d7420a290d50fdad3d30db4ed485ff411323";
        assertEquals(expected, command.requestSha256());
        assertEquals(expected, command.setCommandId("evt-v2-internal").requestSha256());
        assertFalse(command.isPublicStartValid());
        assertTrue(command.setCommandId("start-101").isPublicStartValid());
        variables.put("amount", "1.0");
        assertNotEquals(expected, command.requestSha256());
        assertFalse(command.setAgentTaskOverrides(null).isPublicStartValid());
        assertTrue(command.setAgentTaskOverrides(Map.of()).isPublicStartValid());
        assertEquals(Map.of(), new AutomationStartCommand().getAgentTaskOverrides());
        assertEquals(Map.of(), AutomationStartCommand.builder().build().getAgentTaskOverrides());
        assertFalse(command.setVariables(Map.of("overflow", ProcessTypedJson.V2_MAX_WIRE_INTEGER + 1)).isPublicStartValid());
        assertFalse(command.toString().contains("variables="));
        assertFalse(command.toString().contains("agentTaskOverrides="));
    }

    @Test
    void start_wire_constructor_preserves_defaults_until_properties_are_present() throws Exception {
        var constructor = AutomationStartCommand.class.getConstructor();
        assertNotNull(constructor.getAnnotation(JsonCreator.class));
        var allArguments = AutomationStartCommand.class.getConstructor(String.class, String.class, String.class,
                String.class, Map.class, Map.class);
        assertEquals(JsonCreator.Mode.DISABLED, allArguments.getAnnotation(JsonCreator.class).mode());
        var command = constructor.newInstance();
        assertEquals(Map.of(), command.getAgentTaskOverrides());
        assertNull(command.setAgentTaskOverrides(null).getAgentTaskOverrides());
    }

    @Test
    void freezes_override_parameters_without_rewriting_original_scalar_text() {
        var child = new ArrayList<Object>(List.of("1.00"));
        var override = new AutomationStartCommand.AgentTaskOverrideVO(null, Map.of("items", child));
        child.clear();
        assertEquals(List.of("1.00"), override.parameters().get("items"));
        assertThrows(UnsupportedOperationException.class, () -> override.parameters().clear());
        assertThrows(IllegalArgumentException.class, () -> new AutomationStartCommand.AgentTaskOverrideVO(null, null));
    }

    @Test
    void event_validation_uses_the_resolved_route_and_checks_token_canonicality() throws Exception {
        var command = new ProcessEventCommand().setEventId("evt-1").setDefinitionKey("autoFlow").setRouteKey("orders")
                .setRouteVersion(7).setType("OrderCreated").setOccurredAt(TIME).setCorrelationKey("关联".repeat(127) + "号")
                .setPayload(Map.of("customerId", "private-payload"));
        var start = new EventRouteVO("orders", 7, "OrderCreated", "START", "producer", EVENT_SCHEMA, List.of(),
                Duration.ofDays(1), "autoFlow", null, null);
        var signal = new EventRouteVO("orders", 7, "OrderCreated", "SIGNAL", "producer", EVENT_SCHEMA, List.of(),
                Duration.ofDays(1), null, "waitNode", null);

        assertEquals(Set.of("eventId", "definitionKey", "routeKey", "routeVersion", "type", "occurredAt", "correlationKey",
                "waitToken", "payload"), fields(ProcessEventCommand.class));
        var token = ProcessEventCommand.class.getDeclaredField("waitToken");
        assertTrue(token.getAnnotation(JsonProperty.class).required());
        assertEquals(JsonInclude.Include.ALWAYS, token.getAnnotation(JsonInclude.class).value());
        assertDoesNotThrow(() -> command.validateForRoute(start));
        assertThrows(IllegalArgumentException.class, () -> command.validateForRoute(signal));
        assertDoesNotThrow(() -> command.setWaitToken(SIGNAL_TOKEN).validateForRoute(signal));
        assertFalse(command.toString().contains(SIGNAL_TOKEN));
        assertFalse(command.toString().contains("private-payload"));
        assertThrows(IllegalArgumentException.class, () -> command.validateForRoute(start));
        assertThrows(IllegalArgumentException.class, () -> command.setWaitToken("A".repeat(42) + "B").validateForRoute(signal));
        assertThrows(IllegalArgumentException.class, () -> command.setWaitToken(SIGNAL_TOKEN + "=").validateForRoute(signal));
        command.setWaitToken(null);
        assertThrows(IllegalArgumentException.class, () -> command.setDefinitionKey("otherFlow").validateForRoute(start));
        command.setDefinitionKey("autoFlow");
        assertThrows(IllegalArgumentException.class, () -> command.setRouteVersion(8).validateForRoute(start));
        command.setRouteVersion(7);
        assertThrows(IllegalArgumentException.class, () -> command.setRouteKey("other").validateForRoute(start));
        command.setRouteKey("orders");
        assertThrows(IllegalArgumentException.class, () -> command.setType("orderCreated").validateForRoute(start));
        command.setType("OrderCreated");
        assertThrows(IllegalArgumentException.class, () -> command.setPayload(Map.of("customerId", 1)).validateForRoute(start));
        assertFalse(command.setPayload(Map.of("customerId", "x".repeat(64 * 1024))).isEventEnvelopeValid());
    }

    @Test
    void event_receipt_keeps_admission_separate_from_the_durable_decision() {
        var receipt = ProcessEventReceiptDTO.builder().receiptId("er-1").eventId("evt-1").releaseVersion("auto-3")
                .activationRevision(3).admittedAt(TIME).receivedAt(TIME).state("RECEIVED").build();
        assertEquals(3L, receipt.getActivationRevision());
        assertEquals("auto-3", receipt.getReleaseVersion());
        assertNull(receipt.getDecidedAt());
        assertTrue(receipt.isReceiptConsistent());
        assertFalse(receipt.setDecidedAt(TIME).isReceiptConsistent());
        assertFalse(receipt.setState("APPLIED").setDecisionCode("APPLIED").isReceiptConsistent());
        assertTrue(receipt.setInstanceId("pi-1").isReceiptConsistent());
        assertTrue(receipt.setNodeExecutionRef("nx-1").isReceiptConsistent());
        assertFalse(receipt.setReceivedAt(TIME.plusSeconds(1)).isReceiptConsistent());
        assertFalse(receipt.setReceivedAt(TIME).setDecidedAt(TIME.minusSeconds(1)).isReceiptConsistent());
        assertFalse(receipt.setDecidedAt(TIME).setState("CANCELLED").isReceiptConsistent());
    }

    @Test
    void instance_projection_requires_terminal_time_and_only_completed_output() {
        var instance = AutomationInstanceDTO.builder().instanceId("pi-1").definitionKey("autoFlow").releaseVersion("auto-3")
                .activationRevision(3).profile(AutomationInstanceDTO.ProfileEnum.AUTOMATION_V2).businessKey("order-1")
                .correlationId("corr-1").state(AutomationInstanceDTO.StateEnum.ACTIVE).version("opaque")
                .configurationSha256(HASH).submittedAt(TIME).availableActions(List.of("CANCEL"))
                .graph(new GraphVO("autoFlow", List.of(), List.of())).build();
        assertTrue(instance.isInstanceProjectionConsistent());
        assertFalse(instance.setOutput(Map.of("private", true)).isInstanceProjectionConsistent());
        assertFalse(instance.toString().contains("private"));
        instance.setState(AutomationInstanceDTO.StateEnum.COMPLETED).setAvailableActions(List.of());
        assertFalse(instance.isInstanceProjectionConsistent());
        assertTrue(instance.setFinishedAt(TIME.plusSeconds(1)).isInstanceProjectionConsistent());
        assertFalse(instance.setState(AutomationInstanceDTO.StateEnum.CANCELLED).isInstanceProjectionConsistent());
        assertTrue(instance.setOutput(null).isInstanceProjectionConsistent());
    }

    @Test
    void node_output_and_retry_are_bound_to_the_observed_attempt_state() throws Exception {
        var node = ProcessNodeExecutionDTO.builder().nodeId("sub:" + "n".repeat(251)).occurrenceId("occ-1").attempt(10)
                .nodeExecutionRef("nx-1").kind(ProcessNodeExecutionDTO.KindEnum.AND_JOIN)
                .state(ProcessNodeExecutionDTO.StateEnum.RUNNING).inputSha256(HASH).outputSchemaSha256(HASH)
                .startedAt(TIME).deadlineAt(TIME.plusSeconds(60)).sideEffectOutcome(ProcessNodeExecutionDTO.SideEffectOutcomeEnum.NONE)
                .eventReceiptIds(List.of()).version("opaque").availableActions(List.of()).build();
        assertTrue(node.isNodeProjectionConsistent());
        assertTrue(node.getNodeId().matches(ProcessNodeExecutionDTO.class.getDeclaredField("nodeId").getAnnotation(Pattern.class).regexp()));
        assertEquals(10, ProcessNodeExecutionDTO.class.getDeclaredField("attempt").getAnnotation(Max.class).value());
        assertFalse(node.setOutput(Map.of("private", true)).isNodeProjectionConsistent());
        assertTrue(node.setState(ProcessNodeExecutionDTO.StateEnum.RESULT_ACCEPTED).isNodeProjectionConsistent());
        assertTrue(node.setState(ProcessNodeExecutionDTO.StateEnum.CANCELLED).setFinishedAt(TIME).isNodeProjectionConsistent());
        assertFalse(node.toString().contains("private"));
        assertFalse(node.setAvailableActions(List.of("RETRY")).isNodeProjectionConsistent());
        assertTrue(node.setOutput(null).setState(ProcessNodeExecutionDTO.StateEnum.WAIT_RECOVERY)
                .setSideEffectOutcome(ProcessNodeExecutionDTO.SideEffectOutcomeEnum.UNKNOWN).isNodeProjectionConsistent());
        var page = new ProcessNodeExecutionPageDTO().setInstanceId("pi-1").setReleaseVersion("auto-3")
                .setObservedAt(TIME).setItems(List.of(node));
        assertTrue(page.isObservedAtValid());
        assertFalse(page.setObservedAt(TIME.plusNanos(1)).isObservedAtValid());
    }

    @Test
    void human_completion_accepts_only_frozen_scalar_result_fields() {
        var task = new EngineHumanTaskDTO().setTaskId("task-1").setInstanceId("pi-1").setNodeId("review")
                .setState(EngineHumanTaskDTO.StateEnum.READY).setVersion("opaque").setResultSchema(HUMAN_SCHEMA)
                .setAvailableActions(List.of("CLAIM"));
        assertTrue(task.isTaskProjectionConsistent());
        assertEquals(List.of("CLAIM"), task.getAvailableActions());
        assertFalse(task.setAvailableActions(List.of("APPROVE")).isTaskProjectionConsistent());
        assertFalse(task.setAvailableActions(List.of("CLAIM")).setState(EngineHumanTaskDTO.StateEnum.COMPLETED)
                .isTaskProjectionConsistent());

        var command = new CompleteEngineHumanTaskCommand().setCommandId("complete-1").setExpectedVersion("opaque")
                .setResult(Map.of("resultRef", "private-result"));
        assertTrue(command.isScalarResultValid());
        assertDoesNotThrow(() -> command.validateAgainst(HUMAN_SCHEMA));
        assertFalse(command.toString().contains("private-result"));
        assertThrows(IllegalArgumentException.class, () -> command.setResult(Map.of("approval", true)).validateAgainst(HUMAN_SCHEMA));
        assertFalse(command.setResult(Map.of("resultRef", List.of("a"))).isScalarResultValid());
        var completion = new EngineHumanTaskCompletionDTO().setTaskId("task-1").setCommandId("complete-1")
                .setState(EngineHumanTaskCompletionDTO.StateEnum.COMPLETED).setCompletedAt(TIME);
        assertTrue(completion.isCompletedAtValid());
    }

    @Test
    void retry_command_receipt_reference_remains_optional_and_control_free() throws Exception {
        var command = new NodeRetryCommand().setCommandId("retry-1").setNodeExecutionRef("nx-1")
                .setExpectedVersion("opaque").setReason("private-reason");
        assertNull(command.getBusinessReceiptRef());
        String pattern = NodeRetryCommand.class.getDeclaredField("businessReceiptRef").getAnnotation(Pattern.class).regexp();
        assertTrue("a".repeat(255).matches(pattern));
        assertFalse("a".repeat(256).matches(pattern));
        assertFalse("a\nb".matches(pattern));
        assertFalse(command.setBusinessReceiptRef("private-reference").toString().contains("private"));
    }

    @Test
    void lifecycle_event_binds_event_kind_to_minimal_references() {
        var start = lifecycle(2, "INSTANCE_STARTED", null, null, null, null);
        assertEquals(2, start.schemaVersion());
        assertDoesNotThrow(() -> lifecycle(2, "NODE_WAITING", "occ-1", 1, null, null));
        assertDoesNotThrow(() -> lifecycle(2, "TASK_CREATED", "occ-1", 1, "task-1", null));
        assertFalse(lifecycle(2, "TASK_COMPLETED", null, null, "task-1", "private-ref").toString().contains("private-ref"));
        assertThrows(IllegalArgumentException.class, () -> lifecycle(1, "INSTANCE_STARTED", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> lifecycle(2, "NODE_WAITING", "occ-1", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> lifecycle(2, "TASK_CREATED", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> lifecycle(2, "INSTANCE_CANCELLED", "occ-1", 1, null, null));
        assertThrows(IllegalArgumentException.class, () -> lifecycle(2, "APPROVAL_COMPLETED", null, null, "task-1", null));
    }

    private static ProcessLifecycleEventV2 lifecycle(int version, String type, String occurrenceId, Integer attempt,
                                                      String taskId, String resultRef) {
        return new ProcessLifecycleEventV2(version, "event-1", 11L, "source", "pi-1", "auto-3", type, TIME,
                occurrenceId, attempt, taskId, resultRef);
    }

    private static ProcessSchemaVO objectSchema(String field, Map<String, Object> schema) {
        return ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(field, schema),
                "required", List.of(field), "additionalProperties", false));
    }

    private static Set<String> fields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());
    }
}
