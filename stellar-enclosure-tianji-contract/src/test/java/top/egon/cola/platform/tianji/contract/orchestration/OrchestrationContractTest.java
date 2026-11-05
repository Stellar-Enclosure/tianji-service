package top.egon.cola.platform.tianji.contract.orchestration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrchestrationContractTest {
    @Test
    void exposes_only_bounded_process_local_agent_outcome_evidence() {
        var recorded = Instant.parse("2026-11-05T12:00:00Z");
        var received = recorded.plusSeconds(1);
        var decided = received.plusSeconds(1);
        var dispatch = ProcessAgentOutcomePageDTO.DispatchState.ACKNOWLEDGED;
        var inbox = ProcessAgentOutcomePageDTO.InboxState.APPLIED;
        var terminal = top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.COMPLETED;
        var item = new ProcessAgentOutcomePageDTO.ItemDTO("review", dispatch, inbox, terminal, recorded, received, decided);
        var items = new ArrayList<>(List.of(item));
        var page = new ProcessAgentOutcomePageDTO("instance", decided, items, null);
        items.clear();
        assertEquals(List.of(item), page.items());
        assertThrows(IllegalArgumentException.class, () -> new GetProcessAgentOutcomesQuery("..", null));
        assertThrows(IllegalArgumentException.class, () -> new GetProcessAgentOutcomesQuery("instance", "occurrence-id"));
        assertThrows(IllegalArgumentException.class, () -> new ProcessAgentOutcomePageDTO("instance", recorded, List.of(item), null));
        assertThrows(IllegalArgumentException.class,
                () -> new ProcessAgentOutcomePageDTO("instance", decided, List.of(item), "v1." + "a".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new ProcessAgentOutcomePageDTO.ItemDTO(
                "review", dispatch, inbox, null, recorded, received, decided));
        assertThrows(IllegalArgumentException.class, () -> new ProcessAgentOutcomePageDTO.ItemDTO(
                "review", dispatch, ProcessAgentOutcomePageDTO.InboxState.NOT_OBSERVED, terminal, recorded, null, null));
    }

    @Test
    void rejects_invalid_process_correlation_queries() {
        var start = Instant.parse("2026-11-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> new ListProcessInstancesByCorrelationQuery("trace-1", start, start.plus(Duration.ofDays(31)).plusNanos(1_000), null));
        assertThrows(IllegalArgumentException.class,
                () -> new ListProcessInstancesByCorrelationQuery("trace-1", start, start, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ListProcessInstancesByCorrelationQuery("trace-1", start.plusNanos(1), start.plusSeconds(1), null));
        assertThrows(IllegalArgumentException.class,
                () -> new ListProcessInstancesByCorrelationQuery("trace-1", start, start.plusSeconds(1), "v1.YQ="));
        assertThrows(IllegalArgumentException.class,
                () -> new ListProcessInstancesByCorrelationQuery(" trace-1", start, start.plusSeconds(1), null));
        assertEquals(start, ListProcessInstancesByCorrelationQuery.parseUtc("2026-11-01T00:00:00.000Z"));
        for (var malformed : List.of("2026-11-01T00:00:00+00:00", "2026-11-01T00:00:60Z",
                "2026-11-01T24:00:00Z", "2026-02-29T00:00:00Z", "2026-11-01T00:00:00.0000001Z")) {
            assertThrows(IllegalArgumentException.class, () -> ListProcessInstancesByCorrelationQuery.parseUtc(malformed));
        }
        assertEquals(start.plus(Duration.ofDays(31)),
                new ListProcessInstancesByCorrelationQuery("trace-1", start, start.plus(Duration.ofDays(31)), null).toExclusive());
    }


    @Test
    void correlation_page_is_bounded_immutable_and_has_only_owned_receipt_fields() {
        var now = Instant.parse("2026-11-01T00:00:00Z");
        var item = new ProcessInstanceCorrelationPageDTO.ReceiptDTO("instance", "v1", now);
        var source = new ArrayList<>(List.of(item));
        var page = new ProcessInstanceCorrelationPageDTO("trace-1", now, source, null);
        source.clear();
        assertEquals(List.of(item), page.items());
        assertThrows(UnsupportedOperationException.class, () -> page.items().clear());
        assertThrows(IllegalArgumentException.class, () -> new ProcessInstanceCorrelationPageDTO("trace-1", now, List.of(item), "v1.YQ"));
        assertThrows(IllegalArgumentException.class, () -> new ProcessInstanceCorrelationPageDTO("trace-1", now, List.of(item, item), null));
        assertThrows(IllegalArgumentException.class, () -> new ProcessInstanceCorrelationPageDTO("trace-1", now.minusSeconds(1), List.of(item), null));
        assertThrows(IllegalArgumentException.class, () -> new ProcessInstanceCorrelationPageDTO.ReceiptDTO("..", "v1", now));
        assertEquals(List.of("instanceId", "releaseVersion", "submittedAt"),
                java.util.Arrays.stream(item.getClass().getRecordComponents()).map(component -> component.getName()).toList());
    }

    @Test
    void formal_case_models_have_their_own_named_release_type() {
        var cmmn = assertDoesNotThrow(() -> Enum.valueOf(ProcessModelTypeEnum.class, "CMMN"));
        assertEquals("CMMN", cmmn.name());
        assertEquals(ProcessModelTypeEnum.BPMN, Enum.valueOf(ProcessModelTypeEnum.class, "BPMN"));
        assertEquals(ProcessModelTypeEnum.DMN, Enum.valueOf(ProcessModelTypeEnum.class, "DMN"));
    }

    @Test
    void canonical_start_values_preserve_the_existing_wire_fields() {
        assertEquals("tianji:process:start", ProcessClient.START_SCOPE);
        assertEquals("tianji.api.process.instance.start", ProcessClient.START_RESOURCE_KEY);
        assertEquals(List.of("definitionKey", "releaseVersion", "businessKey", "commandId", "correlationId", "variables"),
                java.util.Arrays.stream(StartProcessCommand.class.getRecordComponents()).map(component -> component.getName()).toList());
    }

    @Test
    void copies_values_and_normalizes_only_supported_numeric_inputs() {
        Map<String, Object> values = new HashMap<>(Map.of("amount", 10, "name", "中文😀"));
        var command = new StartProcessCommand("invoice", "v1", "订单-1", "command-1", "trace-1", values);
        values.put("amount", 20);
        assertEquals(10L, command.variables().get("amount"));
        assertThrows(UnsupportedOperationException.class, () -> command.variables().put("amount", 30));
        assertThrows(IllegalArgumentException.class, () -> new EvaluateDecisionCommand("risk", "v1", "r", "c", Map.of("amount", 10.0)));
        assertThrows(IllegalArgumentException.class, () -> new EvaluateDecisionCommand("risk", "v1", "r", "c", Map.of("amount", new BigDecimal("10"))));
        assertThrows(IllegalArgumentException.class, () -> new EvaluateDecisionCommand("risk", "v1", "r", "c", Map.of("amount", 1_000_000_000_000_000L)));
        assertThrows(IllegalArgumentException.class, () -> new StartProcessCommand("invoice", "v1", "bad" + (char) 0, "c", "t", Map.of()));
        assertFalse(List.of(StartProcessCommand.class.getRecordComponents()).stream()
                .anyMatch(component -> component.getName().equalsIgnoreCase("tenantId")));
    }

    @Test
    void release_models_are_bounded_immutable_and_unique() {
        var model = new ProcessModelResource(ProcessModelTypeEnum.BPMN, "invoice", "<xml/>", Map.of("amount", ProcessValueTypeEnum.INTEGER));
        List<ProcessModelResource> source = new ArrayList<>(List.of(model));
        var release = new PublishProcessReleaseCommand("v1", source);
        source.clear();
        assertEquals(1, release.models().size());
        assertThrows(UnsupportedOperationException.class, () -> release.models().clear());
        assertThrows(IllegalArgumentException.class, () -> new PublishProcessReleaseCommand("v1", List.of(model, model)));
        assertThrows(IllegalArgumentException.class, () -> new ProcessModelResource(ProcessModelTypeEnum.BPMN, "invoice", "x".repeat(524_289), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProcessModelResource(ProcessModelTypeEnum.BPMN, "invoice", "bad" + (char) 0xd800, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProcessModelResource(ProcessModelTypeEnum.BPMN, "invoice", "<xml/>", Map.of("execution", ProcessValueTypeEnum.STRING)));
    }

    @Test
    void validates_terminal_times_and_standalone_decision_results() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> new ProcessInstanceDTO("instance", "invoice", "v1", "order",
                "trace", ProcessInstanceStatusEnum.COMPLETED, now, null));
        var result = new DecisionResultDTO("12345678-1234-1234-1234-123456789abc", "risk", "v1", "trace", now,
                List.of(Map.of("route", "review", "score", 1)), List.of("row:1"));
        assertEquals(1L, result.outputs().getFirst().get("score"));
        assertThrows(UnsupportedOperationException.class, () -> result.outputs().getFirst().put("route", "changed"));
        assertThrows(IllegalArgumentException.class, () -> new DecisionResultDTO("not-an-id", "risk", "v1", "trace", now, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DecisionResultDTO("12345678-1234-1234-1234-123456789abc", "risk", "v1", "trace", now,
                List.of(), List.of("row:1")));
        assertThrows(IllegalArgumentException.class, () -> new DecisionResultDTO("12345678-1234-1234-1234-123456789abc", "risk", "v1", "trace", now,
                List.of(Map.of("route", "review")), List.of("row:1", "row:1")));
    }

    @Test
    void worker_contract_keeps_lease_data_bounded_immutable_and_out_of_string_representations() {
        String lease = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Map<String, Object> input = new HashMap<>(Map.of("amount", 10, "description", "private-fixture"));
        var task = new WorkerTaskDTO("task-1", "occurrence-1", WorkerScopeTypeEnum.BPMN, "process-1", null, 20,
                "riskCheck", "1", WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT,
                Map.of("amount", ProcessValueTypeEnum.INTEGER, "description", ProcessValueTypeEnum.STRING), Map.of("route", ProcessValueTypeEnum.STRING),
                "order-1", "trace-1", "intent-1", start, start.plusSeconds(30), input, lease);
        input.put("amount", 99);
        assertEquals(10L, task.input().get("amount"));
        assertThrows(UnsupportedOperationException.class, () -> task.input().clear());
        assertEquals(Map.of("route", ProcessValueTypeEnum.STRING), task.contract().outputs());
        assertThrows(IllegalArgumentException.class, () -> task.contract().validateInput(Map.of("amount", 10)));
        assertThrows(IllegalArgumentException.class, () -> task.contract().validateOutput(Map.of("route", true)));
        assertThrows(IllegalArgumentException.class, () -> new AcquireWorkerTaskCommand("A".repeat(128), "1".repeat(128)));
        var businessResult = new WorkerBusinessResultDTO("receipt", Map.of("route", "private-result"));
        assertFalse(businessResult.toString().contains("private-result"));
        assertFalse(task.toString().contains(lease));
        assertFalse(task.toString().contains("private-fixture"));
        var complete = new CompleteWorkerTaskCommand("task-1", "occurrence-1", lease, "receipt-1", Map.of("route", "private-result"));
        assertFalse(complete.toString().contains(lease));
        assertFalse(complete.toString().contains("private-result"));
        assertFalse(new FailWorkerTaskCommand("task-1", "occurrence-1", lease, WorkerFailureKindEnum.UNKNOWN, "OUTCOME_UNKNOWN").toString().contains(lease));
        assertNull(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.EMPTY, null).task());
        assertThrows(IllegalArgumentException.class, () -> new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.ACQUIRED, null));
        assertThrows(IllegalArgumentException.class, () -> new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.EMPTY, task));
        var caseTask = new WorkerTaskDTO("task", "occurrence", WorkerScopeTypeEnum.CMMN, null, "case", 20,
                "riskCheck", "1", WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, Map.of(), Map.of(), "order", "trace", "intent", start, start.plusSeconds(30), Map.of(), lease);
        assertEquals("case", caseTask.caseInstanceId());
        assertThrows(IllegalArgumentException.class, () -> new CompleteWorkerTaskCommand("task-1", "occurrence-1", lease + "=", "receipt-1", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new FailWorkerTaskCommand("task-1", "occurrence-1", lease, WorkerFailureKindEnum.RETRYABLE, "stack trace"));
        assertThrows(IllegalArgumentException.class, () -> new WorkerTaskDTO("task", "occurrence", WorkerScopeTypeEnum.BPMN,
                "process", "case", 20, "riskCheck", "1", WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, Map.of(), Map.of(), "order", "trace", "intent", start, start.plusSeconds(30), Map.of(), lease));
        assertThrows(IllegalArgumentException.class, () -> new WorkerTaskDTO("task", "occurrence", WorkerScopeTypeEnum.CMMN,
                null, "case", 20, "riskCheck", "1", WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, Map.of(), Map.of(), "order", "trace", "intent", start, start.plus(Duration.ofMinutes(16)), Map.of(), lease));
        assertFalse(List.of(AcquireWorkerTaskCommand.class.getRecordComponents()).stream()
                .anyMatch(component -> component.getName().equalsIgnoreCase("tenantId")));
    }

    @Test
    void linked_agent_contract_bounds_occurrence_and_deadline_without_accepting_tenant_identity() {
        Instant deadline = Instant.parse("2026-01-01T00:00:30.123456Z");
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "process-1", "occurrence-1", "a".repeat(64), deadline);
        var start = new top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand("review", 1, null, "intent-1", null, "trace-1",
                new top.egon.cola.platform.tianji.contract.agent.AgentInputDTO("private-fixture", List.of(), Map.of()), null);
        var command = new LinkedAgentStartCommand(start, link);
        var query = new LinkedAgentLookupQuery("intent-1", link);
        assertEquals(link, query.link());
        assertFalse(new LinkedAgentLookupResult(query, false, null).found());
        assertThrows(IllegalArgumentException.class, () -> new LinkedAgentLookupResult(query, true, null));
        assertTrue(new LinkedAgentCloseResult(query, new LinkedAgentSubmissionDTO(true, null)).submission().closed());
        assertFalse(command.toString().contains("private-fixture"));
        assertFalse(List.of(AgentProcessLink.class.getRecordComponents()).stream()
                .anyMatch(component -> component.getName().equalsIgnoreCase("tenantId")));
        assertThrows(IllegalArgumentException.class, () -> new AgentProcessLink("source", WorkerScopeTypeEnum.BPMN,
                "process", "occurrence", "bad", deadline));
        assertThrows(IllegalArgumentException.class, () -> new AgentProcessLink("source", WorkerScopeTypeEnum.BPMN,
                "process", "occurrence", "a".repeat(64), deadline.plusNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> new AgentProcessLink("source", WorkerScopeTypeEnum.BPMN,
                "汉".repeat(200), "occurrence", "a".repeat(64), deadline));
        var noTrace = new top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand("review", 1, null, "intent-1", null, null, start.input(), null);
        assertThrows(IllegalArgumentException.class, () -> new LinkedAgentStartCommand(noTrace, link));
    }
}
