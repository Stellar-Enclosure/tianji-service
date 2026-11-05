package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativeTaskPlanVOTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String RESEARCHER = "bounded_researcher";
    private static final String CRITIC = "bounded_critic";
    private static final String PRIVATE_VALUE = "private task question sentinel";

    @Test
    void bounded_plan_type_is_present() {
        assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.agent.infrastructure.adk.NativeTaskPlanVO"));
    }

    @Test
    void accepts_one_and_eight_ordered_immutable_tasks() {
        for (int count : List.of(1, 8)) {
            var values = new ArrayList<Map<String, Object>>();
            for (int index = 0; index < count; index++) {
                values.add(task("task-" + index, index % 2 == 0 ? RESEARCHER : CRITIC, "Question " + index));
            }
            var plan = parse(planJson(values));
            assertEquals("1", plan.schemaVersion());
            assertNotNull(plan.tasks());
            assertEquals(count, plan.tasks().size());
            for (int index = 0; index < count; index++) {
                assertEquals("task-" + index, plan.tasks().get(index).taskId());
                assertEquals("Question " + index, plan.tasks().get(index).question());
            }
            assertThrows(UnsupportedOperationException.class, () -> plan.tasks().clear());
        }
    }

    @Test
    void rejects_non_object_and_non_closed_root_documents() {
        String valid = one("task-1", RESEARCHER, "Question");
        String[] invalid = {null, "", " ", "null", "[]", "true", "\"text\"", "{}",
                "{\"schemaVersion\":\"1\"}", "{\"tasks\":[]}",
                "{\"schemaVersion\":1,\"tasks\":[]}", "{\"schemaVersion\":null,\"tasks\":[]}",
                "{\"schemaVersion\":\"2\",\"tasks\":[]}", "{\"schemaVersion\":\"1\",\"tasks\":null}",
                "{\"schemaVersion\":\"1\",\"tasks\":{}}",
                valid.replace("\"schemaVersion\":\"1\"", "\"schemaVersion\":\"1\",\"schemaVersion\":\"1\""),
                valid.substring(0, valid.length() - 1) + ",\"unknown\":true}",
                valid + " {}", "```json\n" + valid + "\n```"};
        for (String json : invalid) assertInvalid(json);
        assertInvalid("{\"schemaVersion\":\"1\",\"tasks\":[],\"tasks\":[]}");
    }

    @Test
    void rejects_non_closed_task_shapes_types_and_nested_payloads() {
        String[] invalidTasks = {"null", "[]", "\"task\"", "{}",
                "{\"taskId\":\"a\",\"agent\":\"bounded_researcher\"}",
                "{\"taskId\":null,\"agent\":\"bounded_researcher\",\"question\":\"q\"}",
                "{\"taskId\":1,\"agent\":\"bounded_researcher\",\"question\":\"q\"}",
                "{\"taskId\":\"a\",\"agent\":null,\"question\":\"q\"}",
                "{\"taskId\":\"a\",\"agent\":true,\"question\":\"q\"}",
                "{\"taskId\":\"a\",\"agent\":\"bounded_researcher\",\"question\":null}",
                "{\"taskId\":\"a\",\"agent\":\"bounded_researcher\",\"question\":1}",
                "{\"taskId\":\"a\",\"agent\":\"bounded_researcher\",\"question\":\"q\",\"extra\":0}",
                "{\"taskId\":\"a\",\"taskId\":\"a\",\"agent\":\"bounded_researcher\",\"question\":\"q\"}",
                "{\"taskId\":\"a\",\"agent\":\"bounded_researcher\",\"question\":{\"nested\":[[{\"x\":1}]]}}"};
        for (String task : invalidTasks) assertInvalid("{\"schemaVersion\":\"1\",\"tasks\":[" + task + "]}");
    }

    @Test
    void enforces_task_count_ascii_identity_uniqueness_and_fixed_pool() {
        assertInvalid(planJson(List.of()));
        var nine = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < 9; index++) nine.add(task("task-" + index, RESEARCHER, "q"));
        assertInvalid(planJson(nine));
        assertInvalid(planJson(List.of(task("same", RESEARCHER, "q"), task("same", CRITIC, "different"))));
        for (String id : List.of("", " ", "a b", "_a", "-a", "a/", "é", "a\n", "a".repeat(49))) {
            assertInvalid(one(id, RESEARCHER, "q"));
        }
        for (String agent : List.of("", "bounded_supervisor", "unknown", "Bounded_researcher", "bounded_researcher ")) {
            assertInvalid(one("a", agent, "q"));
        }
        assertEquals("a".repeat(48), parse(one("a".repeat(48), RESEARCHER, "q")).tasks().getFirst().taskId());
        assertEquals(2, parse(planJson(List.of(task("A", RESEARCHER, "q1"), task("a", RESEARCHER, "q2")))).tasks().size());
        assertEquals("a:._-0", parse(one("a:._-0", CRITIC, "q")).tasks().getFirst().taskId());
    }

    @Test
    void preserves_question_whitespace_controls_and_valid_surrogate_pairs() {
        String question = "  exact e\u0301\tline\nnext\r尾巴 \uD83D\uDE00  ";
        var plan = parse(one("task", RESEARCHER, question));
        assertEquals(question, plan.tasks().getFirst().question());
        String compact = one("task", RESEARCHER, question);
        var spaced = parse(" \n\t" + compact + "\r\n ");
        assertEquals(question, spaced.tasks().getFirst().question());
        assertEquals(hash(plan), hash(spaced));
    }

    @Test
    void rejects_blank_unsafe_controls_and_raw_or_decoded_unpaired_surrogates() {
        for (String question : List.of("", " \t\r\n", "a\u0000b", "a\u0001b", "a\u000bb", "a\u007fb", "a\u0085b")) {
            assertInvalid(one("a", RESEARCHER, question));
        }
        for (String token : List.of("\"\\uD800\"", "\"\\uDC00\"", "\"\\uD800x\"", "\"x\\uDC00\"")) {
            assertInvalid(questionToken(token));
        }
        assertInvalid(questionToken("\"" + Character.toString((char) 0xD800) + "\""));
        assertInvalid(questionToken("\"" + Character.toString((char) 0xDC00) + "\""));
    }

    @Test
    void enforces_raw_and_decoded_size_caps_without_truncating() {
        String cjk = "界".repeat(1024);
        assertEquals(1024, cjk.length());
        assertEquals(3072, cjk.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(cjk, parse(one("a", RESEARCHER, cjk)).tasks().getFirst().question());
        String paired = "\uD83D\uDE00".repeat(512);
        assertEquals(1024, paired.length());
        assertEquals(paired, parse(one("a", RESEARCHER, paired)).tasks().getFirst().question());
        assertTooLarge(one("a", RESEARCHER, "x".repeat(1025)));
        assertTooLarge(one("a", RESEARCHER, "界".repeat(1025)));
        assertEquals("x".repeat(1024), parse(questionToken("\"" + "\\u0078".repeat(1024) + "\"")).tasks().getFirst().question());
        assertTooLarge(questionToken("\"" + "\\u0078".repeat(1025) + "\""));
        String compact = one("a", RESEARCHER, "q");
        String maximumRaw = compact + " ".repeat(16384 - compact.length());
        assertEquals(16384, maximumRaw.length());
        assertEquals("q", parse(maximumRaw).tasks().getFirst().question());
        assertTooLarge(maximumRaw + " ");
        String overBothRawCaps = questionToken("\"" + "界".repeat(22000) + "\"");
        assertTrue(overBothRawCaps.getBytes(StandardCharsets.UTF_8).length > 65536);
        assertTooLarge(overBothRawCaps);
    }

    @Test
    void hashes_semantic_framed_fields_in_task_order_without_string_normalization() {
        var original = parse(planJson(List.of(task("first", RESEARCHER, "first question"), task("second", CRITIC, "second question"))));
        String originalHash = hash(original);
        String expected = assertDoesNotThrow(() -> AdkSemanticJsonDigest.fingerprint("TIANJI_NATIVE_TASK_PLAN_V1",
                List.of("1", "2", "first", RESEARCHER, "first question", "second", CRITIC, "second question")));
        assertEquals(expected, originalHash);
        String reorderedKeys = "{ \"tasks\" : [{\"question\":\"first question\",\"agent\":\"bounded_researcher\",\"taskId\":\"first\"},"
                + "{\"question\":\"second question\",\"taskId\":\"second\",\"agent\":\"bounded_critic\"}],\"schemaVersion\":\"1\"}";
        assertEquals(originalHash, hash(parse(reorderedKeys)));
        assertNotEquals(originalHash, hash(parse(planJson(List.of(task("second", CRITIC, "second question"), task("first", RESEARCHER, "first question"))))));
        assertNotEquals(originalHash, hash(parse(planJson(List.of(task("first", RESEARCHER, "first question "), task("second", CRITIC, "second question"))))));
        assertNotEquals(hash(parse(one("a", RESEARCHER, "é"))), hash(parse(one("a", RESEARCHER, "e\u0301"))));
        assertNotEquals(hash(parse(one("a", RESEARCHER, "q"))), hash(parse(one("A", RESEARCHER, "q"))));
        assertNotEquals(hash(parse(one("a", RESEARCHER, "q"))), hash(parse(one("a", CRITIC, "q"))));
    }

    @Test
    void constructors_cannot_bypass_plan_or_task_validation() {
        var valid = assertDoesNotThrow(() -> new NativeTaskPlanVO.TaskVO("a", RESEARCHER, "q"));
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO(null, List.of(valid))),
                () -> assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO("2", List.of(valid))),
                () -> assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO("1", null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO("1", List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO("1", java.util.Arrays.asList(valid, null))),
                () -> assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO("1", List.of(valid, valid))),
                () -> assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO.TaskVO(null, RESEARCHER, "q")),
                () -> assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO.TaskVO("a/", RESEARCHER, "q")),
                () -> assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO.TaskVO("a", "unknown", "q")),
                () -> assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO.TaskVO("a", RESEARCHER, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO.TaskVO("a", RESEARCHER, " \n")),
                () -> assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO.TaskVO("a", RESEARCHER, "q\u0001")),
                () -> assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO.TaskVO("a", RESEARCHER, Character.toString((char) 0xD800))),
                () -> assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO.TaskVO("a", RESEARCHER, Character.toString((char) 0xDC00))));
        var nine = new ArrayList<NativeTaskPlanVO.TaskVO>();
        var escaped = new ArrayList<NativeTaskPlanVO.TaskVO>();
        for (int index = 0; index < 9; index++) nine.add(assertDoesNotThrow(() -> new NativeTaskPlanVO.TaskVO("t" + nine.size(), RESEARCHER, "q")));
        for (int index = 0; index < 8; index++) escaped.add(assertDoesNotThrow(() -> new NativeTaskPlanVO.TaskVO("t" + escaped.size(), RESEARCHER, "\n".repeat(1023) + "x")));
        assertThrows(IllegalArgumentException.class, () -> new NativeTaskPlanVO("1", nine));
        assertSizeFailure(assertThrows(AgentLedgerException.class, () -> new NativeTaskPlanVO.TaskVO("a", RESEARCHER, "q".repeat(1025))));
        assertSizeFailure(assertThrows(AgentLedgerException.class, () -> new NativeTaskPlanVO("1", escaped)));
    }

    @Test
    void constructors_defensively_copy_and_redact_question_values() {
        var task = assertDoesNotThrow(() -> new NativeTaskPlanVO.TaskVO("a", RESEARCHER, PRIVATE_VALUE));
        var mutable = new ArrayList<>(List.of(task));
        var plan = assertDoesNotThrow(() -> new NativeTaskPlanVO("1", mutable));
        assertNotNull(plan.tasks());
        mutable.clear();
        assertAll(
                () -> assertEquals(1, plan.tasks().size()),
                () -> assertThrows(UnsupportedOperationException.class, () -> plan.tasks().clear()),
                () -> assertFalse(plan.toString().contains(PRIVATE_VALUE)),
                () -> assertFalse(task.toString().contains(PRIVATE_VALUE)));
    }

    @Test
    void parse_errors_and_size_errors_have_safe_messages_without_private_causes() {
        String malformed = one("a", RESEARCHER, PRIVATE_VALUE) + " private trailing token";
        var invalid = assertThrows(IllegalArgumentException.class, () -> NativeTaskPlanVO.parse(malformed));
        assertNull(invalid.getCause());
        assertNotNull(invalid.getMessage());
        assertFalse(invalid.getMessage().contains(PRIVATE_VALUE));
        var oversized = assertThrows(AgentLedgerException.class,
                () -> NativeTaskPlanVO.parse(one("a", RESEARCHER, PRIVATE_VALUE + "x".repeat(1025))));
        assertSizeFailure(oversized);
        assertNotNull(oversized.getMessage());
        assertFalse(oversized.getMessage().contains(PRIVATE_VALUE));
    }

    private static NativeTaskPlanVO parse(String json) {
        var plan = assertDoesNotThrow(() -> NativeTaskPlanVO.parse(json));
        assertNotNull(plan);
        return plan;
    }

    private static String hash(NativeTaskPlanVO plan) {
        String hash = assertDoesNotThrow(plan::planHash);
        assertNotNull(hash);
        assertTrue(hash.matches("[0-9a-f]{64}"));
        return hash;
    }

    private static Map<String, Object> task(String id, String agent, String question) {
        return Map.of("taskId", id, "agent", agent, "question", question);
    }

    private static String one(String id, String agent, String question) {
        return planJson(List.of(task(id, agent, question)));
    }

    private static String planJson(List<Map<String, Object>> tasks) {
        return assertDoesNotThrow(() -> JSON.writeValueAsString(Map.of("schemaVersion", "1", "tasks", tasks)));
    }

    private static String questionToken(String token) {
        return "{\"schemaVersion\":\"1\",\"tasks\":[{\"taskId\":\"a\",\"agent\":\"bounded_researcher\",\"question\":" + token + "}]}";
    }

    private static void assertInvalid(String json) {
        var failure = assertThrows(IllegalArgumentException.class, () -> NativeTaskPlanVO.parse(json));
        assertNull(failure.getCause());
    }

    private static void assertTooLarge(String json) {
        assertSizeFailure(assertThrows(AgentLedgerException.class, () -> NativeTaskPlanVO.parse(json)));
    }

    private static void assertSizeFailure(AgentLedgerException failure) {
        assertEquals(AgentErrorCodeEnum.TIANJI_REQUEST_TOO_LARGE, failure.code());
        assertNull(failure.getCause());
    }
}
