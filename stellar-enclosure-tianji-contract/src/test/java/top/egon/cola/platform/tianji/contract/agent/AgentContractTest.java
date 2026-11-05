package top.egon.cola.platform.tianji.contract.agent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgentContractTest {

    @Test
    void correlation_query_contract_exists() {
        var from = ListAgentRunsByCorrelationQuery.parseUtc("2026-10-01T00:00:00.123456Z");
        var to = ListAgentRunsByCorrelationQuery.parseUtc("2026-11-01T00:00:00.123456Z");
        var query = new ListAgentRunsByCorrelationQuery("审批 / 42", from, to, "v1.A_bc-D");
        assertEquals("审批 / 42", query.correlationId());
        assertEquals(Instant.parse("2026-10-01T00:00:00.123456Z"), query.fromInclusive());
        assertEquals(Duration.ofDays(31), Duration.between(query.fromInclusive(), query.toExclusive()));
        assertEquals("v1.A_bc-D", query.cursor());
        assertNull(new ListAgentRunsByCorrelationQuery("A", from, to, null).cursor());
    }

    @Test
    void correlation_query_rejects_nonexact_text_bad_utc_and_unbounded_navigation() {
        var from = Instant.parse("2026-10-01T00:00:00Z");
        var to = from.plus(Duration.ofDays(31));
        for (String key : List.of("", " a", "a ", "a\tb", "a\u0085b", "a" + (char) 0xD800, "a".repeat(129))) {
            assertThrows(IllegalArgumentException.class, () -> new ListAgentRunsByCorrelationQuery(key, from, to, null));
        }
        for (String time : List.of("2026-10-01T00:00:00+00:00", "2026-10-01t00:00:00Z", "2026-10-01T00:00:00z",
                "2026-10-01T00:00:00.1234567Z", "2026-10-01T24:00:00Z", "2026-10-01T00:00:60Z",
                "2026-02-29T00:00:00Z", "0000-01-01T00:00:00Z", " 2026-10-01T00:00:00Z")) {
            assertThrows(IllegalArgumentException.class, () -> ListAgentRunsByCorrelationQuery.parseUtc(time));
        }
        for (String cursor : List.of("", " v1.X", "v1.X=", "v2.X", "v1." + "a".repeat(254))) {
            assertThrows(IllegalArgumentException.class, () -> new ListAgentRunsByCorrelationQuery("A", from, to, cursor));
        }
        assertThrows(IllegalArgumentException.class, () -> new ListAgentRunsByCorrelationQuery("A", from, from, null));
        assertThrows(IllegalArgumentException.class, () -> new ListAgentRunsByCorrelationQuery("A", to, from, null));
        assertThrows(IllegalArgumentException.class, () -> new ListAgentRunsByCorrelationQuery("A", from, to.plusNanos(1_000), null));
        assertThrows(IllegalArgumentException.class, () -> new ListAgentRunsByCorrelationQuery("A", from.plusNanos(1), to, null));
    }

    @Test
    void input_copies_nested_json_without_losing_null_values() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("nullable", null);
        List<Object> list = new ArrayList<>(List.of("before"));
        nested.put("list", list);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nested", nested);
        List<String> refs = new ArrayList<>(List.of("artifact:1"));

        AgentInputDTO input = new AgentInputDTO("question", refs, data);
        list.add("after");
        nested.put("new", true);
        refs.add("artifact:2");

        Map<?, ?> stored = (Map<?, ?>) input.taskData().get("nested");
        assertEquals(List.of("before"), stored.get("list"));
        assertTrue(stored.containsKey("nullable"));
        assertNull(stored.get("nullable"));
        assertFalse(stored.containsKey("new"));
        assertEquals(List.of("artifact:1"), input.artifactRefs());
        assertThrows(UnsupportedOperationException.class, () -> input.taskData().put("new", 1));
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) stored.get("list")).clear());
    }

    @Test
    void rejects_cycles_arbitrary_objects_and_non_finite_numbers() {
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("self", cyclic);
        assertThrows(IllegalArgumentException.class, () -> new AgentInputDTO(null, null, cyclic));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentInputDTO(null, null, Map.of("object", new Object())));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentInputDTO(null, null, Map.of("number", Double.NaN)));
    }

    @Test
    void validates_start_and_cursor_without_accepting_tenant_in_the_command() {
        AgentInputDTO input = new AgentInputDTO("hello", null, null);
        StartAgentRunCommand command = new StartAgentRunCommand("review", 1, null, "key-1", null, null, input, null);
        assertEquals("key-1", command.idempotencyKey());
        assertThrows(IllegalArgumentException.class,
                () -> new StartAgentRunCommand("review", 0, null, "key-1", null, null, input, null));
        assertThrows(IllegalArgumentException.class,
                () -> new StartAgentRunCommand("review", 1, null, " ", null, null, input, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentEventsQuery("run", -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new AgentEventsQuery("run", 0, 1001));
        assertThrows(IllegalArgumentException.class, () -> new ListAgentRunsQuery(null, 0));
        assertThrows(IllegalArgumentException.class, () -> new ListAgentRunsQuery(null, 101));
        assertThrows(IllegalArgumentException.class, () -> new ListAgentRunsQuery("x".repeat(257), 1));
        assertFalse(List.of(StartAgentRunCommand.class.getRecordComponents()).stream()
                .anyMatch(field -> field.getName().equalsIgnoreCase("tenantId")));
    }

    @Test
    void policy_override_distinguishes_absent_tools_from_explicitly_empty_allowlist() {
        ExecutionPolicyOverrideDTO unchanged = new ExecutionPolicyOverrideDTO(null, null, null,
                null, null, null, null, null, null, null);
        ExecutionPolicyOverrideDTO denyTools = new ExecutionPolicyOverrideDTO(Duration.ofSeconds(5), null, null,
                1, 0, 10L, 5L, BigDecimal.ZERO, 1, Set.of());
        assertNull(unchanged.allowedToolVersions());
        assertEquals(Set.of(), denyTools.allowedToolVersions());
        assertThrows(IllegalArgumentException.class, () -> new ExecutionPolicyOverrideDTO(Duration.ZERO, null, null,
                null, null, null, null, null, null, null));
    }

    @Test
    void usage_preserves_unknown_values() {
        AgentUsageDTO usage = new AgentUsageDTO(AgentUsageKindEnum.UNKNOWN, null, null, null, null, null, null);
        assertNull(usage.inputTokens());
        assertNull(usage.totalTokens());
        assertThrows(IllegalArgumentException.class,
                () -> new AgentUsageDTO(AgentUsageKindEnum.ACTUAL, -1L, null, null, null, null, null));
    }

    @Test
    void session_commands_accept_unicode_but_reject_control_and_invalid_surrogate_characters() {
        assertEquals("会话/😀 +", new CreateAgentSessionCommand("会话/😀 +").sessionId());
        assertThrows(IllegalArgumentException.class, () -> new CreateAgentSessionCommand("bad\nvalue"));
        assertThrows(IllegalArgumentException.class, () -> new GetAgentSessionQuery("bad\ud800"));
        assertThrows(IllegalArgumentException.class, () -> new DeleteAgentSessionCommand("bad\udc00"));
        assertThrows(IllegalArgumentException.class,
                () -> new StartAgentRunCommand("review", 1, "bad\ud800", "key", null, null,
                        new AgentInputDTO("text", null, null), null));
    }
}
