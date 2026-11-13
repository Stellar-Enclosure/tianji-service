package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AgentRuntimeTemplateCommand {

    private static final Set<String> TEMPLATE_FIELDS = Set.of("templateKey", "templateVersion", "runtimeProfile",
            "executorRef", "inputSchema", "outputSchema", "parameterSchema", "configuration", "limits");
    private static final Set<String> REFERENCE_FIELDS = Set.of("key", "version", "contentSha256");
    private static final String FIELD = "[A-Za-z_][A-Za-z0-9_]*(?:\\[(?:0|[1-9][0-9]{0,2})\\])*";
    private static final java.util.regex.Pattern VALUE_PATH = java.util.regex.Pattern.compile(FIELD + "(?:\\." + FIELD + ")*");
    private static final int MAX_CONDITION_NODES = 64;
    private static final int MAX_CONDITION_DEPTH = 8;

    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String templateKey;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long templateVersion;
    @NotNull
    private RuntimeProfileEnum runtimeProfile;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String executorRef;
    @NotNull @Valid
    private ProcessSchemaVO inputSchema;
    @NotNull @Valid
    private ProcessSchemaVO outputSchema;
    @NotNull @Valid
    private ProcessSchemaVO parameterSchema;
    @NotNull @Valid
    private TemplateConfigurationVO configuration;
    @NotNull @Valid
    private TechnicalLimitsVO limits;

    @JsonIgnore
    @AssertTrue(message = "Invalid runtime template")
    public boolean isTemplateConsistent() {
        return ProcessTypedJson.isId(templateKey) && boundedVersion(templateVersion) && runtimeProfile != null
                && ProcessTypedJson.isId(executorRef) && objectSchema(inputSchema) && objectSchema(outputSchema)
                && objectSchema(parameterSchema) && configuration != null && limits != null;
    }

    /** The host's strict JSON parser supplies the decoded object, before any field coercion. */
    public static AgentRuntimeTemplateCommand fromExactWire(Map<String, Object> source) {
        exactShape(source, TEMPLATE_FIELDS, Set.of(), Set.of());
        var template = new AgentRuntimeTemplateCommand(text(source, "templateKey"), integer(source, "templateVersion"),
                enumValue(source, "runtimeProfile", RuntimeProfileEnum.class), text(source, "executorRef"),
                schema(source, "inputSchema"), schema(source, "outputSchema"), schema(source, "parameterSchema"),
                TemplateConfigurationVO.fromExactWire(object(source, "configuration")),
                TechnicalLimitsVO.fromExactWire(object(source, "limits")));
        require(template.isTemplateConsistent());
        return template;
    }

    public Map<String, Object> toWire() {
        require(isTemplateConsistent());
        return Map.of("templateKey", templateKey, "templateVersion", templateVersion, "runtimeProfile", runtimeProfile.wire(),
                "executorRef", executorRef, "inputSchema", inputSchema.toWire(), "outputSchema", outputSchema.toWire(),
                "parameterSchema", parameterSchema.toWire(), "configuration", configuration.toWire(), "limits", limits.toWire());
    }

    public byte[] canonicalBytes() {
        return ProcessTypedJson.canonicalBytes(toWire());
    }

    public String contentSha256() {
        return ProcessTypedJson.sha256("tianji-agent-template-v2", canonicalBytes());
    }

    public record TemplateConfigurationVO(@NotNull @Valid TemplateDependencyRefVO instructionRef,
            @NotNull String modelRef, @NotNull List<@NotNull @Valid TemplateDependencyRefVO> toolRefs,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Valid AgenticTemplatePlanVO agenticPlan) {

        public TemplateConfigurationVO {
            require(instructionRef != null && ProcessTypedJson.isId(modelRef));
            toolRefs = copy(toolRefs, 0, 32);
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static TemplateConfigurationVO fromExactWire(Map<String, Object> source) {
            exactShape(source, Set.of("instructionRef", "modelRef", "toolRefs"), Set.of("agenticPlan"), Set.of());
            return new TemplateConfigurationVO(TemplateDependencyRefVO.fromExactWire(object(source, "instructionRef")),
                    text(source, "modelRef"), array(source, "toolRefs", 32, item -> TemplateDependencyRefVO.fromExactWire(ProcessTypedJson.object(item))),
                    source.containsKey("agenticPlan") ? AgenticTemplatePlanVO.fromExactWire(object(source, "agenticPlan")) : null);
        }

        public Map<String, Object> toWire() {
            var wire = new LinkedHashMap<String, Object>();
            wire.put("instructionRef", instructionRef.toWire());
            wire.put("modelRef", modelRef);
            wire.put("toolRefs", toolRefs.stream().map(TemplateDependencyRefVO::toWire).toList());
            if (agenticPlan != null) {
                wire.put("agenticPlan", agenticPlan.toWire());
            }
            return wire;
        }
    }

    public record TemplateDependencyRefVO(@NotNull String key, @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) long version,
                                          @NotNull String contentSha256) {
        public TemplateDependencyRefVO {
            require(ProcessTypedJson.isId(key) && boundedVersion(version) && ProcessTypedJson.isSha256(contentSha256));
        }

        public static TemplateDependencyRefVO fromExactWire(Map<String, Object> source) {
            exactShape(source, REFERENCE_FIELDS, Set.of(), Set.of());
            return new TemplateDependencyRefVO(text(source, "key"), integer(source, "version"), text(source, "contentSha256"));
        }

        Map<String, Object> toWire() {
            return Map.of("key", key, "version", version, "contentSha256", contentSha256);
        }
    }

    public record AgenticTemplateLeafVO(@NotNull String agentKey, @NotNull @Valid TemplateDependencyRefVO instructionRef,
            @NotNull String modelRef, @NotNull List<@NotNull @Valid TemplateDependencyRefVO> toolRefs) {
        public AgenticTemplateLeafVO {
            require(isAgentKey(agentKey) && instructionRef != null && ProcessTypedJson.isId(modelRef));
            toolRefs = copy(toolRefs, 0, 32);
        }

        private static AgenticTemplateLeafVO fromExactWire(Object value) {
            var source = ProcessTypedJson.object(value);
            exactShape(source, Set.of("agentKey", "instructionRef", "modelRef", "toolRefs"), Set.of(), Set.of());
            return new AgenticTemplateLeafVO(text(source, "agentKey"),
                    TemplateDependencyRefVO.fromExactWire(object(source, "instructionRef")), text(source, "modelRef"),
                    array(source, "toolRefs", 32, item -> TemplateDependencyRefVO.fromExactWire(ProcessTypedJson.object(item))));
        }

        Map<String, Object> toWire() {
            return Map.of("agentKey", agentKey, "instructionRef", instructionRef.toWire(), "modelRef", modelRef,
                    "toolRefs", toolRefs.stream().map(TemplateDependencyRefVO::toWire).toList());
        }
    }

    public record AgenticTemplatePlanVO(@NotNull AgenticStrategyEnum strategy, @NotNull List<@Valid AgenticTemplateLeafVO> agents,
            @NotNull List<@Valid AgenticTemplateLinkVO> links,
            @JsonInclude(JsonInclude.Include.ALWAYS) Integer maxRounds, @Min(1) @Max(32) int maxParallelism,
            @JsonInclude(JsonInclude.Include.ALWAYS) String outputAgentKey, @NotNull AgenticMergeEnum merge,
            @NotNull List<String> sharedFields, @NotNull @Valid AgenticTemplateTerminationVO termination) {

        private static final Set<String> OPTIONS = Set.of("strategy", "links", "maxRounds", "maxParallelism", "outputAgentKey",
                "merge", "sharedFields", "termination");

        public AgenticTemplatePlanVO {
            agents = copy(agents, 1, 16);
            links = copy(links, 0, 64);
            sharedFields = copy(sharedFields, 0, 32);
            require(strategy != null && merge != null && termination != null);
            var keys = new HashSet<String>();
            require(agents.stream().allMatch(leaf -> keys.add(leaf.agentKey())));
            require(strategy != AgenticStrategyEnum.REASONING || agents.size() == 1);

            boolean boundedRounds = strategy == AgenticStrategyEnum.BOUNDED_LOOP || strategy == AgenticStrategyEnum.HANDOFF;
            require(boundedRounds ? maxRounds != null && maxRounds >= 1 && maxRounds <= 32 : maxRounds == null);
            require(maxParallelism >= 1 && maxParallelism <= 32 && (strategy == AgenticStrategyEnum.PARALLEL || maxParallelism == 1));
            require(strategy == AgenticStrategyEnum.HANDOFF || links.isEmpty());
            var edges = new HashSet<AgenticTemplateLinkVO>();
            require(links.stream().allMatch(edge -> keys.contains(edge.source()) && keys.contains(edge.target()) && edges.add(edge)));
            require(merge == AgenticMergeEnum.OUTPUT_AGENT
                    ? outputAgentKey != null && keys.contains(outputAgentKey)
                    : outputAgentKey == null && (strategy == AgenticStrategyEnum.SEQUENTIAL || strategy == AgenticStrategyEnum.PARALLEL));
            require(new HashSet<>(sharedFields).size() == sharedFields.size());
            require(sharedFields.stream().allMatch(path -> allowedPath(path, keys, false)));
            if (termination instanceof AgenticPredicateVO predicate) {
                require(boundedRounds);
                validateCondition(predicate.condition(), keys, 1, new int[]{0});
            }
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static AgenticTemplatePlanVO fromExactWire(Map<String, Object> source) {
            exactShape(source, Set.of("agents"), OPTIONS, Set.of("maxRounds", "outputAgentKey"));
            return new AgenticTemplatePlanVO(
                    enumOr(source, "strategy", AgenticStrategyEnum.class, AgenticStrategyEnum.REASONING),
                    array(source, "agents", 16, AgenticTemplateLeafVO::fromExactWire),
                    source.containsKey("links") ? array(source, "links", 64, AgenticTemplateLinkVO::fromExactWire) : List.of(),
                    source.get("maxRounds") == null ? null : intValue(source, "maxRounds"),
                    source.containsKey("maxParallelism") ? intValue(source, "maxParallelism") : 1,
                    source.get("outputAgentKey") == null ? null : text(source, "outputAgentKey"),
                    enumOr(source, "merge", AgenticMergeEnum.class, AgenticMergeEnum.OUTPUT_AGENT),
                    source.containsKey("sharedFields") ? array(source, "sharedFields", 32, AgentRuntimeTemplateCommand::text) : List.of(),
                    source.containsKey("termination") ? AgenticTemplateTerminationVO.fromExactWire(object(source, "termination"))
                            : new AgenticRootTerminalVO(AgenticTerminationModeEnum.ROOT_TERMINAL));
        }

        public Map<String, Object> toWire() {
            var wire = new LinkedHashMap<String, Object>();
            wire.put("strategy", strategy.wire());
            wire.put("agents", agents.stream().map(AgenticTemplateLeafVO::toWire).toList());
            wire.put("links", links.stream().map(AgenticTemplateLinkVO::toWire).toList());
            wire.put("maxRounds", maxRounds);
            wire.put("maxParallelism", maxParallelism);
            wire.put("outputAgentKey", outputAgentKey);
            wire.put("merge", merge.wire());
            wire.put("sharedFields", sharedFields);
            wire.put("termination", termination.toWire());
            return wire;
        }
    }

    public record AgenticTemplateLinkVO(@NotNull String source, @NotNull String target) {
        public AgenticTemplateLinkVO {
            require(isAgentKey(source) && isAgentKey(target) && !source.equals(target));
        }

        private static AgenticTemplateLinkVO fromExactWire(Object value) {
            var source = ProcessTypedJson.object(value);
            exactShape(source, Set.of("source", "target"), Set.of(), Set.of());
            return new AgenticTemplateLinkVO(text(source, "source"), text(source, "target"));
        }

        Map<String, Object> toWire() {
            return Map.of("source", source, "target", target);
        }
    }

    public sealed interface AgenticTemplateTerminationVO permits AgenticRootTerminalVO, AgenticPredicateVO {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static AgenticTemplateTerminationVO fromExactWire(Map<String, Object> source) {
            var mode = enumValue(source, "mode", AgenticTerminationModeEnum.class);
            if (mode == AgenticTerminationModeEnum.ROOT_TERMINAL) {
                exactShape(source, Set.of("mode"), Set.of(), Set.of());
                return new AgenticRootTerminalVO(mode);
            }
            exactShape(source, Set.of("mode", "condition"), Set.of(), Set.of());
            return new AgenticPredicateVO(mode, AgenticConditionVO.fromExactWire(object(source, "condition")));
        }

        Map<String, Object> toWire();
    }

    public record AgenticRootTerminalVO(@NotNull AgenticTerminationModeEnum mode) implements AgenticTemplateTerminationVO {
        public AgenticRootTerminalVO {
            require(mode == AgenticTerminationModeEnum.ROOT_TERMINAL);
        }

        @Override
        public Map<String, Object> toWire() {
            return Map.of("mode", mode.wire());
        }
    }

    public record AgenticPredicateVO(@NotNull AgenticTerminationModeEnum mode, @NotNull @Valid AgenticConditionVO condition)
            implements AgenticTemplateTerminationVO {
        public AgenticPredicateVO {
            require(mode == AgenticTerminationModeEnum.PREDICATE && condition != null);
            validateCondition(condition, null, 1, new int[]{0});
        }

        @Override
        public Map<String, Object> toWire() {
            return Map.of("mode", mode.wire(), "condition", condition.toWire());
        }
    }

    public sealed interface AgenticConditionVO permits AgenticConditionOperationVO, AgenticConditionPathVO, AgenticConditionLiteralVO {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static AgenticConditionVO fromExactWire(Map<String, Object> source) {
            return readCondition(source, 1, new int[]{0});
        }

        Map<String, Object> toWire();
    }

    public record AgenticConditionOperationVO(@NotNull AgenticConditionOpEnum op, @NotNull List<@Valid AgenticConditionVO> args)
            implements AgenticConditionVO {
        public AgenticConditionOperationVO {
            args = copy(args, 1, MAX_CONDITION_NODES - 1);
            require(op != null && switch (op) {
                case NOT -> args.size() == 1;
                case AND, OR -> args.size() >= 2;
                default -> args.size() == 2;
            });
        }

        @Override
        public Map<String, Object> toWire() {
            return Map.of("op", op.wire(), "args", args.stream().map(AgenticConditionVO::toWire).toList());
        }
    }

    public record AgenticConditionPathVO(@NotNull String path) implements AgenticConditionVO {
        public AgenticConditionPathVO {
            require(allowedPath(path, null, true));
        }

        @Override
        public Map<String, Object> toWire() {
            return Map.of("path", path);
        }
    }

    public record AgenticConditionLiteralVO(@JsonInclude(JsonInclude.Include.ALWAYS) Object literal) implements AgenticConditionVO {
        public AgenticConditionLiteralVO {
            literal = ProcessTypedJson.immutableValue(literal, 64 * 1024);
        }

        @Override
        public Map<String, Object> toWire() {
            var wire = new LinkedHashMap<String, Object>();
            wire.put("literal", literal);
            return wire;
        }
    }

    public enum AgenticStrategyEnum {
        REASONING, SEQUENTIAL, PARALLEL, BOUNDED_LOOP, HANDOFF;
        @JsonValue public String wire() { return name(); }
    }

    public enum AgenticMergeEnum {
        OUTPUT_AGENT, COLLECT_BY_AGENT;
        @JsonValue public String wire() { return name(); }
    }

    public enum AgenticTerminationModeEnum {
        ROOT_TERMINAL, PREDICATE;
        @JsonValue public String wire() { return name(); }
    }

    public enum AgenticConditionOpEnum {
        EQ, NE, GT, GE, LT, LE, AND, OR, NOT;
        @JsonValue public String wire() { return name(); }
    }

    public enum RuntimeProfileEnum {
        RUNTIME_V2;
        @JsonValue public String wire() { return name(); }
    }

    public record TechnicalLimitsVO(@NotNull Duration totalTimeout, @NotNull Duration modelIdleTimeout, @NotNull Duration toolTimeout,
            @Min(1) @Max(1000) int maxModelCalls, @Min(1) @Max(1000) int maxToolCalls,
            @Min(1) @Max(32) int maxParallelism, @Min(1) @Max(1048576) int contextWindowTokens,
            @Min(1) @Max(65536) int maxOutputTokensPerCall) {

        public TechnicalLimitsVO {
            require(totalTimeout != null && totalTimeout.isPositive()
                    && modelIdleTimeout != null && modelIdleTimeout.isPositive() && modelIdleTimeout.compareTo(totalTimeout) <= 0
                    && toolTimeout != null && toolTimeout.isPositive() && toolTimeout.compareTo(totalTimeout) <= 0);
            require(between(maxModelCalls, 1000) && between(maxToolCalls, 1000) && between(maxParallelism, 32)
                    && between(contextWindowTokens, 1048576) && between(maxOutputTokensPerCall, 65536)
                    && maxOutputTokensPerCall <= contextWindowTokens);
        }

        public static TechnicalLimitsVO fromExactWire(Map<String, Object> source) {
            exactShape(source, Set.of("totalTimeout", "modelIdleTimeout", "toolTimeout", "maxModelCalls", "maxToolCalls",
                    "maxParallelism", "contextWindowTokens", "maxOutputTokensPerCall"), Set.of(), Set.of());
            return new TechnicalLimitsVO(duration(source, "totalTimeout"), duration(source, "modelIdleTimeout"),
                    duration(source, "toolTimeout"), intValue(source, "maxModelCalls"), intValue(source, "maxToolCalls"),
                    intValue(source, "maxParallelism"), intValue(source, "contextWindowTokens"),
                    intValue(source, "maxOutputTokensPerCall"));
        }

        public Map<String, Object> toWire() {
            return Map.of("totalTimeout", totalTimeout.toString(), "modelIdleTimeout", modelIdleTimeout.toString(),
                    "toolTimeout", toolTimeout.toString(), "maxModelCalls", maxModelCalls, "maxToolCalls", maxToolCalls,
                    "maxParallelism", maxParallelism, "contextWindowTokens", contextWindowTokens,
                    "maxOutputTokensPerCall", maxOutputTokensPerCall);
        }
    }

    private static AgenticConditionVO readCondition(Map<String, Object> source, int depth, int[] count) {
        require(source != null && depth <= MAX_CONDITION_DEPTH && ++count[0] <= MAX_CONDITION_NODES);
        var keys = source.keySet();
        if (keys.equals(Set.of("op", "args"))) {
            return new AgenticConditionOperationVO(enumValue(source, "op", AgenticConditionOpEnum.class),
                    array(source, "args", MAX_CONDITION_NODES - 1,
                            item -> readCondition(ProcessTypedJson.object(item), depth + 1, count)));
        }
        if (keys.equals(Set.of("path"))) {
            return new AgenticConditionPathVO(text(source, "path"));
        }
        if (keys.equals(Set.of("literal"))) {
            return new AgenticConditionLiteralVO(source.get("literal"));
        }
        throw invalid();
    }

    private static void validateCondition(AgenticConditionVO condition, Set<String> keys, int depth, int[] count) {
        require(depth <= MAX_CONDITION_DEPTH && ++count[0] <= MAX_CONDITION_NODES);
        switch (condition) {
            case AgenticConditionPathVO path -> require(allowedPath(path.path(), keys, true));
            case AgenticConditionOperationVO operation ->
                    operation.args().forEach(argument -> validateCondition(argument, keys, depth + 1, count));
            case AgenticConditionLiteralVO ignored -> { }
        }
    }

    private static boolean allowedPath(String path, Set<String> keys, boolean allowRound) {
        if (allowRound && "round".equals(path)) {
            return true;
        }
        if (path == null || path.length() > 256 || path.chars().filter(ch -> ch == '.' || ch == '[').count() >= 8) {
            return false;
        }
        if (path.startsWith("input.")) {
            return VALUE_PATH.matcher(path.substring(6)).matches();
        }
        if (!path.startsWith("agents.")) {
            return false;
        }
        int separator = path.indexOf('.', 7);
        if (separator < 0 || !path.startsWith(".output.", separator)) {
            return false;
        }
        String key = path.substring(7, separator);
        return isAgentKey(key) && (keys == null || keys.contains(key)) && VALUE_PATH.matcher(path.substring(separator + 8)).matches();
    }

    private static void exactShape(Map<String, Object> source, Set<String> required, Set<String> optional, Set<String> nullable) {
        require(source != null && source.keySet().containsAll(required));
        for (var field : source.entrySet()) {
            require(field.getKey() != null && (required.contains(field.getKey()) || optional.contains(field.getKey()))
                    && (field.getValue() != null || nullable.contains(field.getKey())));
        }
    }

    private static <T> List<T> copy(List<T> source, int min, int max) {
        require(source != null && source.size() >= min && source.size() <= max && source.stream().noneMatch(java.util.Objects::isNull));
        return List.copyOf(source);
    }

    private static <T> List<T> array(Map<String, Object> source, String key, int maximum, Function<Object, T> readItem) {
        if (!(source.get(key) instanceof List<?> values) || values.size() > maximum) {
            throw invalid();
        }
        var result = new ArrayList<T>(values.size());
        for (Object item : values) {
            require(item != null);
            result.add(readItem.apply(item));
        }
        return result;
    }

    private static <E extends Enum<E>> E enumValue(Map<String, Object> source, String key, Class<E> type) {
        if (source == null) {
            throw invalid();
        }
        return Enum.valueOf(type, text(source, key));
    }

    private static <E extends Enum<E>> E enumOr(Map<String, Object> source, String key, Class<E> type, E defaultValue) {
        return source.containsKey(key) ? enumValue(source, key, type) : defaultValue;
    }

    private static String text(Map<String, Object> source, String key) {
        return text(source.get(key));
    }

    private static String text(Object value) {
        if (!(value instanceof String text)) {
            throw invalid();
        }
        return text;
    }

    private static long integer(Map<String, Object> source, String key) {
        return ProcessTypedJson.exactInteger(source.get(key));
    }

    private static int intValue(Map<String, Object> source, String key) {
        long value = integer(source, key);
        require(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE);
        return (int) value;
    }

    private static Map<String, Object> object(Map<String, Object> source, String key) {
        return ProcessTypedJson.object(source.get(key));
    }

    private static ProcessSchemaVO schema(Map<String, Object> source, String key) {
        return ProcessSchemaVO.fromWire(object(source, key));
    }

    private static Duration duration(Map<String, Object> source, String key) {
        try {
            return Duration.parse(text(source, key));
        } catch (DateTimeParseException failure) {
            throw invalid();
        }
    }

    private static boolean boundedVersion(long version) {
        return version >= 1 && version <= ProcessTypedJson.V2_MAX_WIRE_INTEGER;
    }

    private static boolean objectSchema(ProcessSchemaVO schema) {
        return schema != null && "object".equals(schema.type());
    }

    private static boolean isAgentKey(String key) {
        return key != null && key.matches("[A-Za-z][A-Za-z0-9_-]{0,63}");
    }

    private static boolean between(int value, int max) {
        return value >= 1 && value <= max;
    }

    private static void require(boolean accepted) {
        if (!accepted) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid Agent Runtime template contract");
    }
}
