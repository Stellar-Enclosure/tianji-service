package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public record AutomationNodeBindingVO(
        @NotNull String nodeId,
        @NotNull String kind,
        @NotNull @Valid ProcessSchemaVO inputSchema,
        @NotNull @Valid ProcessSchemaVO outputSchema,
        @NotNull List<@Valid MappingVO> inputMapping,
        @NotNull List<@Valid MappingVO> outputMapping,
        @NotNull @Valid NodePolicyVO policy,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid CapabilityRefVO service,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid DecisionRefVO rule,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid AgentTaskBindingVO agent,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid HumanBindingVO human,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid ControlBindingVO control,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<@Valid MergeVO> merge) {

    private static final String FIELD = "[A-Za-z_][A-Za-z0-9_]*(?:\\[(?:0|[1-9][0-9]{0,2})\\])*";
    private static final Pattern FIELD_PATH = Pattern.compile(FIELD + "(?:\\." + FIELD + ")*");
    private static final Set<String> KINDS = Set.of("HUMAN", "SERVICE", "RULE", "AGENT", "EVENT_WAIT", "TIMER",
            "SUBPROCESS", "LOOP", "MULTI_INSTANCE", "AND_JOIN");

    public AutomationNodeBindingVO {
        require(ProcessTypedJson.isId(nodeId) && kind != null && KINDS.contains(kind));
        require(inputSchema != null && outputSchema != null && policy != null);
        inputMapping = mappings(inputMapping);
        outputMapping = mappings(outputMapping);
        require(switch (kind) {
            case "RULE" -> (rule == null) != (service == null);
            case "SERVICE" -> service != null && rule == null;
            default -> service == null && rule == null;
        });
        require("AGENT".equals(kind) == (agent != null) && "HUMAN".equals(kind) == (human != null));
        require(switch (kind) {
            case "LOOP" -> control instanceof LoopControlVO;
            case "MULTI_INSTANCE" -> control instanceof MultiInstanceControlVO;
            case "SUBPROCESS" -> control instanceof SubprocessControlVO;
            default -> control == null;
        });
        require(("AND_JOIN".equals(kind) || "MULTI_INSTANCE".equals(kind)) == (merge != null));
        if (merge != null) {
            var targets = new HashSet<String>();
            for (MergeVO item : merge) {
                require(item != null && targets.add(item.target()));
            }
            merge = List.copyOf(merge);
        }
    }

    public record MappingVO(@NotNull String target, @NotNull String source) {
        public MappingVO {
            require(isFieldPath(target) && isSourcePath(source));
        }
    }

    public record CapabilityRefVO(
            @NotNull String capabilityKey,
            @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) long version,
            @NotNull String inputSchemaSha256,
            @NotNull String outputSchemaSha256,
            @NotNull WorkerEffectSafetyEnum effectMode) {

        public CapabilityRefVO {
            require(ProcessTypedJson.isId(capabilityKey) && version >= 1 && version <= ProcessTypedJson.V2_MAX_WIRE_INTEGER);
            require(ProcessTypedJson.isSha256(inputSchemaSha256) && ProcessTypedJson.isSha256(outputSchemaSha256) && effectMode != null);
        }
    }

    public record DecisionRefVO(@NotNull String definitionKey, @NotNull String modelSha256,
                                @NotNull List<@Valid MappingVO> inputMapping, @NotNull List<@Valid MappingVO> outputMapping) {
        public DecisionRefVO {
            require(ProcessTypedJson.isId(definitionKey) && ProcessTypedJson.isSha256(modelSha256));
            inputMapping = mappings(inputMapping);
            outputMapping = mappings(outputMapping);
        }
    }

    public record HumanBindingVO(@NotNull String candidateBindingRef, @NotNull @Valid ProcessSchemaVO resultSchema,
                                 @NotNull Duration dueAfter) {
        private static final Set<String> SCALARS = Set.of("string", "integer", "boolean");

        public HumanBindingVO {
            require(ProcessTypedJson.isId(candidateBindingRef) && dueAfter != null && dueAfter.isPositive());
            require(resultSchema != null && "object".equals(resultSchema.type())
                    && resultSchema.properties().values().stream().allMatch(field -> SCALARS.contains(field.type())));
        }
    }

    public record MergeVO(@NotNull String target, @NotNull List<String> sourceNodeIds, @NotNull String reducer) {
        private static final Set<String> REDUCERS = Set.of("EXCLUSIVE", "COLLECT_INDEXED", "ALL", "ANY", "SUM");

        public MergeVO {
            require(isFieldPath(target) && reducer != null && REDUCERS.contains(reducer));
            require(sourceNodeIds != null && !sourceNodeIds.isEmpty() && sourceNodeIds.size() <= 128);
            var seen = new HashSet<String>();
            for (String sourceNodeId : sourceNodeIds) {
                require(ProcessTypedJson.isId(sourceNodeId) && seen.add(sourceNodeId));
            }
            sourceNodeIds = List.copyOf(sourceNodeIds);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes({@JsonSubTypes.Type(LoopControlVO.class), @JsonSubTypes.Type(MultiInstanceControlVO.class),
            @JsonSubTypes.Type(SubprocessControlVO.class)})
    public sealed interface ControlBindingVO permits LoopControlVO, MultiInstanceControlVO, SubprocessControlVO { }

    public record LoopControlVO(@Min(1) @Max(1000) int maxIterations, @NotNull @Valid ConditionVO condition,
                                @NotNull @Valid ProcessSchemaVO carrySchema, @NotNull Map<String, Object> initialCarry,
                                @NotNull List<@Valid MappingVO> carryMapping) implements ControlBindingVO {
        public LoopControlVO {
            require(maxIterations >= 1 && maxIterations <= 1000 && condition != null);
            require(carrySchema != null && "object".equals(carrySchema.type()));
            initialCarry = ProcessTypedJson.object(carrySchema.normalizeValue(initialCarry));
            carryMapping = mappings(carryMapping);
            requireConditionLimits(condition, 0, new int[]{0});
        }
    }

    public record MultiInstanceControlVO(@NotNull String itemsSource, @JsonProperty(required = true) boolean sequential,
                                         @Min(1) @Max(256) int maxItems,
                                         @JsonInclude(JsonInclude.Include.NON_NULL) @Valid ConditionVO completionCondition)
            implements ControlBindingVO {
        public MultiInstanceControlVO {
            require(isSourcePath(itemsSource) && maxItems >= 1 && maxItems <= 256);
            if (completionCondition != null) {
                requireConditionLimits(completionCondition, 0, new int[]{0});
            }
        }
    }

    public record SubprocessControlVO(@NotNull String definitionKey, @NotNull String modelSha256) implements ControlBindingVO {
        public SubprocessControlVO {
            require(ProcessTypedJson.isId(definitionKey) && ProcessTypedJson.isSha256(modelSha256));
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes({@JsonSubTypes.Type(ConditionOperationVO.class), @JsonSubTypes.Type(ConditionPathVO.class),
            @JsonSubTypes.Type(ConditionLiteralVO.class)})
    public sealed interface ConditionVO permits ConditionOperationVO, ConditionPathVO, ConditionLiteralVO { }

    public record ConditionOperationVO(@NotNull String op, @NotNull List<@Valid ConditionVO> args) implements ConditionVO {
        public ConditionOperationVO {
            require(op != null && args != null && args.stream().noneMatch(java.util.Objects::isNull));
            require(switch (op) {
                case "EQ", "NE", "GT", "GE", "LT", "LE" -> args.size() == 2;
                case "AND", "OR" -> args.size() >= 2 && args.size() < 64;
                case "NOT" -> args.size() == 1;
                default -> false;
            });
            args = List.copyOf(args);
            var count = new int[]{1};
            for (ConditionVO child : args) {
                requireConditionLimits(child, 1, count);
            }
        }
    }

    public record ConditionPathVO(@NotNull String path) implements ConditionVO {
        public ConditionPathVO {
            require(isFieldPath(path));
        }
    }

    public record ConditionLiteralVO(@JsonProperty(required = true) @JsonInclude(JsonInclude.Include.ALWAYS) Object literal)
            implements ConditionVO {
        public ConditionLiteralVO {
            literal = ProcessTypedJson.immutableValue(literal, 64 * 1024);
        }
    }

    static List<MappingVO> mappings(List<MappingVO> input) {
        require(input != null);
        var targets = new HashSet<String>();
        for (MappingVO mapping : input) {
            require(mapping != null && targets.add(mapping.target()));
        }
        return List.copyOf(input);
    }

    private static boolean isFieldPath(String path) {
        if (path == null || path.isEmpty() || path.length() > 256 || !FIELD_PATH.matcher(path).matches()) {
            return false;
        }
        return path.chars().filter(ch -> ch == '.' || ch == '[').count() < 8;
    }

    private static boolean isSourcePath(String path) {
        if (path == null || path.length() > 256 || path.chars().filter(ch -> ch == '.' || ch == '[').count() >= 8) {
            return false;
        }
        int firstDot = path.indexOf('.');
        if (firstDot < 0) {
            return false;
        }
        String prefix = path.substring(0, firstDot);
        if ("input".equals(prefix) || "scope".equals(prefix) || "event".equals(prefix)) {
            return isFieldPath(path.substring(firstDot + 1));
        }
        if (!"nodes".equals(prefix)) {
            return false;
        }
        String reference = path.substring(firstDot + 1);
        for (int output = reference.indexOf(".output."); output >= 0; output = reference.indexOf(".output.", output + 1)) {
            if (ProcessTypedJson.isId(reference.substring(0, output)) && isFieldPath(reference.substring(output + 8))) {
                return true;
            }
        }
        return false;
    }

    private static void requireConditionLimits(ConditionVO condition, int depth, int[] count) {
        require(depth <= 8 && ++count[0] <= 64);
        if (condition instanceof ConditionOperationVO operation) {
            for (ConditionVO child : operation.args()) {
                requireConditionLimits(child, depth + 1, count);
            }
        }
    }

    private static void require(boolean accepted) {
        if (!accepted) {
            throw new IllegalArgumentException("Invalid automation node binding");
        }
    }
}
