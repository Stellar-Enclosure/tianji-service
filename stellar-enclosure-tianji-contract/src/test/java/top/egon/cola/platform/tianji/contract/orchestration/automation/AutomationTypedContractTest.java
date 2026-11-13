package top.egon.cola.platform.tianji.contract.orchestration.automation;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutomationTypedContractTest {

    private static final long MAX_WIRE_INTEGER = 999_999_999_999_999L;

    @Test
    void canonicalizes_exact_utf16_order_and_minimal_utf8_escaping() {
        var body = new LinkedHashMap<String, Object>();
        body.put("2", "二");
        body.put("\uE000", null);
        body.put("\uD800\uDC00", true);
        body.put("10", "十");
        body.put("é", "\b\f\n\r\t\u0001/\"\\\u2028\u2029");

        assertArrayEquals(utf8("{\"10\":\"十\",\"2\":\"二\",\"é\":\"\\b\\f\\n\\r\\t\\u0001/\\\"\\\\\u2028\u2029\","
                + "\"\uD800\uDC00\":true,\"\uE000\":null}"), canonical(body));
        assertArrayEquals(utf8("{\"a\":\"é\",\"b\":[1,2]}"), canonical(Map.of("b", List.of(1L, 2L), "a", "é")));
        assertArrayEquals(utf8("9223372036854775807"), canonical(Long.MAX_VALUE));
    }

    @Test
    void canonicalizer_rejects_invalid_unicode_and_non_integral_values() {
        assertThrows(IllegalArgumentException.class, () -> canonical(Map.of("bad", "\0")));
        assertThrows(IllegalArgumentException.class, () -> canonical(Map.of("\uD800", true)));
        assertThrows(IllegalArgumentException.class, () -> canonical(Map.of("bad", 1.1)));
        assertThrows(IllegalArgumentException.class, () -> canonical(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)));
        assertThrows(IllegalArgumentException.class, () -> canonical(Map.of(1, "not a string key")));
    }

    @Test
    void start_digest_freezes_literal_content_and_omits_only_the_command_key() {
        var first = originalStart("1.00", MAX_WIRE_INTEGER);
        assertEquals("7cfa4c0086eaadc9837ce74cbf54d7420a290d50fdad3d30db4ed485ff411323", startDigest(first));
        assertEquals("8758777d98df5e731e4ac50e896692e0cec31a15d4b00236c0d388399c82bac2",
                startDigest(originalStart("1.0", MAX_WIRE_INTEGER)));
        assertNotEquals(startDigest(first), startDigest(originalStart("1.0", MAX_WIRE_INTEGER)));
        var newCommand = new LinkedHashMap<>(first);
        newCommand.put("commandId", "second-attempt");
        assertEquals(startDigest(first), startDigest(newCommand));

        var omitted = new LinkedHashMap<>(first);
        omitted.remove("agentTaskOverrides");
        var empty = new LinkedHashMap<>(omitted);
        empty.put("agentTaskOverrides", Map.of());
        assertEquals(startDigest(omitted), startDigest(empty));
        var explicitNull = new LinkedHashMap<>(omitted);
        explicitNull.put("agentTaskOverrides", null);
        assertThrows(IllegalArgumentException.class, () -> startDigest(explicitNull));
    }

    @Test
    void start_digest_rejects_overflow_and_non_contract_fields() {
        assertThrows(IllegalArgumentException.class, () -> startDigest(originalStart("1.00", MAX_WIRE_INTEGER + 1)));
        assertThrows(IllegalArgumentException.class, () -> startDigest(originalStart("1.00", 9_007_199_254_740_993L)));
        var extra = new LinkedHashMap<>(originalStart("1.00", MAX_WIRE_INTEGER));
        extra.put("releaseVersion", "not-a-caller-choice");
        assertThrows(IllegalArgumentException.class, () -> startDigest(extra));
        var missing = new LinkedHashMap<>(originalStart("1.00", MAX_WIRE_INTEGER));
        missing.remove("commandId");
        assertThrows(IllegalArgumentException.class, () -> startDigest(missing));
    }

    @Test
    void schema_freezes_closed_shape_and_preserves_typed_null() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("name", Map.of("type", "null"));
        var required = new ArrayList<>(List.of("name"));
        var schema = parseSchema(Map.of("type", "object", "additionalProperties", false,
                "properties", properties, "required", required));
        properties.clear();
        required.clear();
        var value = new LinkedHashMap<String, Object>();
        value.put("name", null);
        assertEquals(value, normalize(schema, value));
        assertThrows(IllegalArgumentException.class, () -> normalize(schema, Map.of()));
        value.put("extra", true);
        assertThrows(IllegalArgumentException.class, () -> normalize(schema, value));

        assertThrows(IllegalArgumentException.class, () -> parseSchema(Map.of("type", "string", "unknown", true)));
        assertThrows(IllegalArgumentException.class, () -> parseSchema(Map.of("type", "object", "additionalProperties", true,
                "properties", Map.of(), "required", List.of())));
        var explicitNull = new LinkedHashMap<String, Object>();
        explicitNull.put("type", "string");
        explicitNull.put("maxLength", null);
        assertEquals(Map.of("type", "string"), parseSchema(Map.of("type", "string")).toWire());
        assertThrows(IllegalArgumentException.class, () -> parseSchema(explicitNull));
    }

    @Test
    void schema_enforces_depth_and_size_with_array_and_integer_boundaries() {
        Object inner = Map.of("type", "boolean");
        for (int level = 0; level < 8; level++) {
            inner = Map.of("type", "array", "items", inner, "maxItems", 256);
        }
        parseSchema(castObject(inner));
        var tooDeep = Map.of("type", "array", "items", inner, "maxItems", 256);
        assertThrows(IllegalArgumentException.class, () -> parseSchema(tooDeep));
        assertThrows(IllegalArgumentException.class, () -> parseSchema(Map.of("type", "array", "items", Map.of("type", "null"), "maxItems", 257)));
        assertThrows(IllegalArgumentException.class, () -> parseSchema(Map.of("type", "string", "enum", List.of("汉".repeat(11_000)))));

        var integer = parseSchema(Map.of("type", "integer", "minimum", -MAX_WIRE_INTEGER, "maximum", MAX_WIRE_INTEGER));
        assertEquals(MAX_WIRE_INTEGER, normalize(integer, BigInteger.valueOf(MAX_WIRE_INTEGER)));
        assertThrows(IllegalArgumentException.class, () -> normalize(integer, MAX_WIRE_INTEGER + 1));
    }

    @Test
    void schema_normalizes_decimal_and_checks_calendar_and_utc_microseconds() {
        var decimal = parseSchema(Map.of("type", "string", "format", "decimal:2"));
        assertEquals("1.20", normalize(decimal, "1.2"));
        assertEquals("0.00", normalize(decimal, "-0.0"));
        assertThrows(IllegalArgumentException.class, () -> normalize(decimal, "01.2"));
        assertThrows(IllegalArgumentException.class, () -> normalize(decimal, "1.200"));
        assertThrows(IllegalArgumentException.class, () -> normalize(decimal, "1e0"));
        assertThrows(IllegalArgumentException.class, () -> parseSchema(Map.of("type", "string", "format", "decimal:19")));
        var shortDecimal = parseSchema(Map.of("type", "string", "format", "decimal:2", "maxLength", 3));
        assertThrows(IllegalArgumentException.class, () -> normalize(shortDecimal, "1.2"));

        var date = parseSchema(Map.of("type", "string", "format", "date"));
        assertEquals("2024-02-29", normalize(date, "2024-02-29"));
        assertThrows(IllegalArgumentException.class, () -> normalize(date, "2025-02-29"));
        var instant = parseSchema(Map.of("type", "string", "format", "date-time"));
        assertEquals("2026-11-13T09:34:12.123456Z", normalize(instant, "2026-11-13T09:34:12.123456Z"));
        assertThrows(IllegalArgumentException.class, () -> normalize(instant, "2026-11-13T09:34:60Z"));
        assertThrows(IllegalArgumentException.class, () -> normalize(instant, "2026-11-13T09:34:12+00:00"));
        assertThrows(IllegalArgumentException.class, () -> normalize(instant, "2026-11-13T09:34:12.1234567Z"));
    }

    @Test
    void pattern_uses_portable_search_and_absolute_anchors() {
        var search = parseSchema(Map.of("type", "string", "pattern", "[a-z]{2}"));
        assertEquals("界az界", normalize(search, "界az界"));
        var anchored = parseSchema(Map.of("type", "string", "pattern", "^ab$"));
        assertThrows(IllegalArgumentException.class, () -> normalize(anchored, "ab\n"));
        var media = parseSchema(Map.of("type", "string", "pattern", "^[a-z0-9.+-]{1,63}/[a-z0-9.+-]{1,63}$"));
        assertEquals("application/json", normalize(media, "application/json"));
        assertThrows(IllegalArgumentException.class, () -> normalize(media, "application/json; charset=UTF-8"));
        assertEquals("b", normalize(parseSchema(Map.of("type", "string", "pattern", "^a{0}b$")), "b"));
        assertEquals("界abbbc界", normalize(parseSchema(Map.of("type", "string", "pattern", "a{1,2}b{2,3}c?")), "界abbbc界"));
        for (String unsafe : List.of("(a+)+$", "[z-a]", "[^a]", "\\w", "a{129}", "a{2,1}", "a|b")) {
            assertThrows(IllegalArgumentException.class, () -> parseSchema(Map.of("type", "string", "pattern", unsafe)), unsafe);
        }
    }

    @Test
    void and_join_and_multi_instance_use_only_binding_merge() {
        var merge = new AutomationNodeBindingVO.MergeVO("summary", List.of("left", "right"), "COLLECT_INDEXED");
        var list = new ArrayList<>(List.of(merge));
        var join = binding("AND_JOIN", null, null, list);
        list.clear();
        assertEquals(List.of(merge), join.merge());
        assertNull(join.control());
        assertThrows(IllegalArgumentException.class, () -> binding("AND_JOIN", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> binding("TIMER", null, null, List.of(merge)));
        assertThrows(IllegalArgumentException.class, () -> binding("AND_JOIN", null, null, List.of(merge, merge)));

        var condition = new AutomationNodeBindingVO.ConditionOperationVO("EQ", List.of(
                new AutomationNodeBindingVO.ConditionPathVO("scope.completed"), new AutomationNodeBindingVO.ConditionLiteralVO(null)));
        var multiInstance = new AutomationNodeBindingVO.MultiInstanceControlVO("input.items[0]", false, 256, condition);
        assertEquals(List.of(merge), binding("MULTI_INSTANCE", null, multiInstance, List.of(merge)).merge());
        assertNull(((AutomationNodeBindingVO.ConditionLiteralVO) condition.args().getLast()).literal());
        assertThrows(NoSuchMethodException.class, () -> multiInstance.getClass().getMethod("merge"));
        assertThrows(IllegalArgumentException.class, () -> binding("MULTI_INSTANCE", null, multiInstance, null));
        assertThrows(IllegalArgumentException.class, () -> binding("AND_JOIN", null, multiInstance, List.of(merge)));
        var capability = new AutomationNodeBindingVO.CapabilityRefVO("lookup", 1, "a".repeat(64), "b".repeat(64),
                WorkerEffectSafetyEnum.READ_ONLY);
        assertThrows(IllegalArgumentException.class, () -> binding("AND_JOIN", capability, null, List.of(merge)));
        assertThrows(IllegalArgumentException.class, () -> new AutomationNodeBindingVO.MultiInstanceControlVO("input.items", false, 257, null));
        assertThrows(IllegalArgumentException.class, () -> new AutomationNodeBindingVO.LoopControlVO(1001, condition,
                objectSchema(), Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AutomationNodeBindingVO.MappingVO("target", "input.items[*]"));
    }

    @Test
    void fixed_node_policy_and_event_schedule_limits_are_enforced() {
        var end = new NodePolicyVO.PolicyActionVO("END", null);
        var retry = new NodePolicyVO.RetryPolicyVO(10, Duration.ofHours(1), List.of("DEPENDENCY_UNAVAILABLE"));
        var retryAction = new NodePolicyVO.PolicyActionVO("RETRY", null);
        var maximum = Duration.ofDays(30);
        assertEquals(maximum, new NodePolicyVO(maximum, maximum, maximum, retryAction, retryAction, end, retry,
                "BEST_EFFORT_STOP", "WAIT_RECOVERY").nodeTimeout());
        assertThrows(IllegalArgumentException.class, () -> new NodePolicyVO(maximum.plusNanos(1), maximum, maximum,
                end, end, end, retry, "BEST_EFFORT_STOP", "WAIT_RECOVERY"));
        assertThrows(IllegalArgumentException.class, () -> new NodePolicyVO(maximum, maximum, maximum,
                end, end, retryAction, retry, "BEST_EFFORT_STOP", "WAIT_RECOVERY"));
        assertThrows(IllegalArgumentException.class, () -> new NodePolicyVO(maximum, maximum, maximum,
                end, end, end, retry, "BEST_EFFORT_STOP", "RETRY"));
        assertThrows(IllegalArgumentException.class, () -> new NodePolicyVO.RetryPolicyVO(11, Duration.ZERO, List.of()));

        var start = Instant.parse("2026-11-13T09:30:00.123456Z");
        var schedule = new EventRouteVO.ScheduleVO(start, Duration.ofSeconds(1), null, 10_000);
        assertEquals("START", new EventRouteVO("submitted", MAX_WIRE_INTEGER, "_OrderCreated", "START", "orderService",
                objectSchema(), List.of(), maximum, "autoFlow", null, schedule).mode());
        assertThrows(IllegalArgumentException.class, () -> new EventRouteVO.ScheduleVO(start.plusNanos(1),
                Duration.ofSeconds(1), null, 10_000));
        assertThrows(IllegalArgumentException.class, () -> new EventRouteVO.ScheduleVO(start,
                Duration.ofSeconds(1), null, 10_001));
        assertThrows(IllegalArgumentException.class, () -> new EventRouteVO.ScheduleVO(start,
                Duration.ofSeconds(1), null, null));
        assertThrows(IllegalArgumentException.class, () -> new EventRouteVO.ScheduleVO(start,
                Duration.ofSeconds(1), start.plusSeconds(10_000), null));
        assertThrows(IllegalArgumentException.class, () -> new EventRouteVO("submitted", 1, "OrderCreated", "SIGNAL",
                "orderService", objectSchema(), List.of(), maximum, null, "receive", schedule));
        assertThrows(IllegalArgumentException.class, () -> new EventRouteVO("submitted", 1, "OrderCreated", "START",
                "orderService", objectSchema(), List.of(), maximum.plusNanos(1), "autoFlow", null, schedule));
    }

    @Test
    void v1_start_digest_stays_byte_for_byte_compatible() {
        var command = new StartProcessCommand("autoFlow", "version-1", "order-101", "old-command", "corr-101",
                Map.of("amount", 5L, "enabled", true));
        assertEquals("2c4735b3905f39f182486e42b3cb981a92de7f87171c945f4978060a7a62cdd8", OrchestrationRequestDigests.start(command));
    }

    private static Map<String, Object> originalStart(String amount, long version) {
        var variables = new LinkedHashMap<String, Object>();
        variables.put("10", "十");
        variables.put("2", "二");
        variables.put("amount", amount);
        variables.put("é", "\n");
        variables.put("\uD800\uDC00", true);
        variables.put("\uE000", null);
        return Map.of("definitionKey", "autoFlow", "businessKey", "order-101", "commandId", "start-101",
                "correlationId", "corr-101", "variables", variables,
                "agentTaskOverrides", Map.of("agentNode", Map.of("templateRef",
                        Map.of("key", "template", "version", version, "contentSha256", "a".repeat(64)))));
    }

    private static byte[] canonical(Object value) {
        return ProcessTypedJson.canonicalBytes(value);
    }

    private static String startDigest(Map<String, Object> value) {
        return ProcessTypedJson.startRequestSha256(value);
    }

    private static ProcessSchemaVO parseSchema(Map<String, Object> value) {
        return ProcessSchemaVO.fromWire(value);
    }

    private static Object normalize(ProcessSchemaVO schema, Object value) {
        return schema.normalizeValue(value);
    }

    private static AutomationNodeBindingVO binding(String kind, AutomationNodeBindingVO.CapabilityRefVO service,
                                                    AutomationNodeBindingVO.ControlBindingVO control,
                                                    List<AutomationNodeBindingVO.MergeVO> merge) {
        var end = new NodePolicyVO.PolicyActionVO("END", null);
        var duration = Duration.ofMinutes(1);
        var policy = new NodePolicyVO(duration, duration, duration, end, end, end,
                new NodePolicyVO.RetryPolicyVO(1, Duration.ZERO, List.of()), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
        return new AutomationNodeBindingVO("join", kind, objectSchema(), objectSchema(), List.of(), List.of(), policy,
                service, null, null, null, control, merge);
    }

    private static ProcessSchemaVO objectSchema() {
        return parseSchema(Map.of("type", "object", "properties", Map.of(), "required", List.of(), "additionalProperties", false));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castObject(Object value) {
        return (Map<String, Object>) value;
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
