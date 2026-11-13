package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionEnum;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class EngineHumanTaskDTO {

    private static final List<String> ACTIONS = Arrays.stream(HumanTaskActionEnum.values()).map(Enum::name).toList();

    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}")
    private String taskId;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}")
    private String instanceId;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,254}")
    private String nodeId;
    @NotNull
    private StateEnum state;
    @JsonProperty(required = true)
    private boolean assignedToMe;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,128}")
    private String version;
    @NotNull @Valid
    private ProcessSchemaVO resultSchema;
    @NotNull @Size(max = 4)
    private List<@NotNull String> availableActions;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Instant dueAt;

    @JsonIgnore
    @AssertTrue(message = "Invalid automation human task observation")
    public boolean isTaskProjectionConsistent() {
        if (state == null || availableActions == null || !isScalarResultSchema(resultSchema)
                || dueAt != null && !ProcessLifecycleEventV2.utcMicros(dueAt)) {
            return false;
        }
        return availableActions.stream().allMatch(action -> action != null && ACTIONS.contains(action))
                && new HashSet<>(availableActions).size() == availableActions.size()
                && (state == StateEnum.READY || state == StateEnum.CLAIMED || availableActions.isEmpty());
    }

    static boolean isScalarResultSchema(ProcessSchemaVO schema) {
        return schema != null && "object".equals(schema.type()) && schema.properties().values().stream()
                .allMatch(property -> switch (property.type()) {
                    case "string", "integer", "boolean" -> true;
                    default -> false;
                });
    }

    public enum StateEnum {
        READY, CLAIMED, COMPLETED, CANCELLED;

        @JsonValue
        public String wire() { return name(); }
    }
}
