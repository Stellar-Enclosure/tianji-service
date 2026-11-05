package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record NativeTaskPlanVO(String schemaVersion, List<TaskVO> tasks) {
    private static final int MAX_PLAN_CHARS = 16_384;
    private static final int MAX_PLAN_BYTES = 65_536;
    private static final int MAX_QUESTION_CHARS = 1_024;
    private static final int MAX_QUESTION_BYTES = 4_096;
    private static final Set<String> PLAN_FIELDS = Set.of("schemaVersion", "tasks");
    private static final Set<String> TASK_FIELDS = Set.of("taskId", "agent", "question");
    private static final Set<String> AGENTS = Set.of("bounded_researcher", "bounded_critic");
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4)
                            .maxNameLength(32).maxStringLength(MAX_PLAN_CHARS).maxNumberLength(32).build())
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    NativeTaskPlanVO {
        if (!"1".equals(schemaVersion) || tasks == null || tasks.isEmpty() || tasks.size() > 8) throw invalid();
        try {
            tasks = List.copyOf(tasks);
        } catch (NullPointerException malformed) {
            throw invalid();
        }
        Set<String> identities = new HashSet<>();
        for (TaskVO task : tasks) if (!identities.add(task.taskId())) throw invalid();
        var values = tasks.stream().map(task -> Map.<String, Object>of(
                "taskId", task.taskId(), "agent", task.agent(), "question", task.question())).toList();
        try {
            String canonical = JSON.writeValueAsString(Map.<String, Object>of("schemaVersion", schemaVersion, "tasks", values));
            checkText(canonical, MAX_PLAN_CHARS, MAX_PLAN_BYTES, false);
        } catch (JsonProcessingException malformed) {
            throw invalid();
        }
    }

    static NativeTaskPlanVO parse(String json) {
        checkText(json, MAX_PLAN_CHARS, MAX_PLAN_BYTES, false);
        try {
            JsonNode plan = JSON.readTree(json);
            requireFields(plan, PLAN_FIELDS);
            String version = text(plan.get("schemaVersion"));
            JsonNode taskNodes = plan.get("tasks");
            if (taskNodes == null || !taskNodes.isArray() || taskNodes.isEmpty() || taskNodes.size() > 8) throw invalid();
            List<TaskVO> tasks = new ArrayList<>(taskNodes.size());
            for (JsonNode task : taskNodes) {
                requireFields(task, TASK_FIELDS);
                tasks.add(new TaskVO(text(task.get("taskId")), text(task.get("agent")), text(task.get("question"))));
            }
            return new NativeTaskPlanVO(version, tasks);
        } catch (JsonProcessingException malformed) {
            throw invalid();
        }
    }

    String planHash() {
        List<String> fields = new ArrayList<>(2 + tasks.size() * 3);
        fields.add(schemaVersion);
        fields.add(Integer.toString(tasks.size()));
        for (TaskVO task : tasks) {
            fields.add(task.taskId());
            fields.add(task.agent());
            fields.add(task.question());
        }
        return AdkSemanticJsonDigest.fingerprint("TIANJI_NATIVE_TASK_PLAN_V1", fields);
    }

    @Override
    public String toString() {
        return "NativeTaskPlanVO[redacted]";
    }

    record TaskVO(String taskId, String agent, String question) {
        TaskVO {
            if (taskId == null || !taskId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,47}")
                    || agent == null || !AGENTS.contains(agent)) throw invalid();
            checkText(question, MAX_QUESTION_CHARS, MAX_QUESTION_BYTES, true);
        }

        @Override
        public String toString() {
            return "TaskVO[redacted]";
        }
    }

    private static void requireFields(JsonNode value, Set<String> fields) {
        if (value == null || !value.isObject() || value.size() != fields.size()) throw invalid();
        for (String field : fields) if (!value.has(field)) throw invalid();
    }

    private static String text(JsonNode value) {
        if (value == null || !value.isTextual()) throw invalid();
        return value.textValue();
    }

    private static void checkText(String value, int maximumChars, int maximumBytes, boolean question) {
        if (value == null) throw invalid();
        if (value.length() > maximumChars) throw tooLarge();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (question && Character.isISOControl(current) && current != '\t' && current != '\n' && current != '\r') throw invalid();
            if (Character.isLowSurrogate(current)) throw invalid();
            if (Character.isHighSurrogate(current)
                    && (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++index)))) throw invalid();
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > maximumBytes) throw tooLarge();
        if (question && value.isBlank()) throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Native task plan is invalid");
    }

    private static AgentLedgerException tooLarge() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REQUEST_TOO_LARGE, "Native task plan exceeds its limit");
    }
}
