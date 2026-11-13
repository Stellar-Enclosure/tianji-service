package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
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
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO.TemplateRefVO;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor(onConstructor_ = @JsonCreator)
// Lombok adds @ConstructorProperties. Keep the all-args constructor out of Jackson creator inference.
@AllArgsConstructor(onConstructor_ = @JsonCreator(mode = JsonCreator.Mode.DISABLED))
@Accessors(chain = true)
@Builder
public class AutomationStartCommand {

    private static final String EVENT_COMMAND_PREFIX = "evt-v2-";

    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String definitionKey;
    @NotBlank @Size(max = 255)
    private String businessKey;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String commandId;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String correlationId;
    @NotNull @Valid @ToString.Exclude
    private Map<String, Object> variables;
    @NotNull @Valid @Builder.Default @ToString.Exclude
    private Map<String, @NotNull @Valid AgentTaskOverrideVO> agentTaskOverrides = Map.of();

    @JsonIgnore
    @AssertTrue(message = "Invalid public automation start")
    public boolean isPublicStartValid() {
        if (commandId == null || commandId.startsWith(EVENT_COMMAND_PREFIX)) {
            return false;
        }
        try {
            requestSha256();
            return true;
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    /** Computes the original intent before the host selects a release and normalizes typed values. */
    public String requestSha256() {
        if (agentTaskOverrides == null) {
            throw new IllegalArgumentException("Invalid automation start overrides");
        }
        var overrides = new LinkedHashMap<String, Object>();
        agentTaskOverrides.forEach((node, override) -> {
            if (override == null) {
                throw new IllegalArgumentException("Invalid automation start override");
            }
            overrides.put(node, override.toWire());
        });
        var wire = new LinkedHashMap<String, Object>();
        wire.put("definitionKey", definitionKey);
        wire.put("businessKey", businessKey);
        wire.put("commandId", commandId);
        wire.put("correlationId", correlationId);
        wire.put("variables", variables);
        wire.put("agentTaskOverrides", overrides);
        return ProcessTypedJson.startRequestSha256(wire);
    }

    static boolean boundedData(Map<String, Object> value) {
        try {
            ProcessTypedJson.immutableObject(value, 64 * 1024);
            return true;
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AgentTaskOverrideVO(@Valid TemplateRefVO templateRef, Map<String, Object> parameters) {

        public AgentTaskOverrideVO {
            if (templateRef == null && parameters == null) {
                throw new IllegalArgumentException("Empty automation start override");
            }
            if (parameters != null) {
                parameters = ProcessTypedJson.immutableObject(parameters, 256 * 1024);
            }
        }

        private Map<String, Object> toWire() {
            var wire = new LinkedHashMap<String, Object>();
            if (templateRef != null) {
                wire.put("templateRef", Map.of("key", templateRef.key(), "version", templateRef.version(),
                        "contentSha256", templateRef.contentSha256()));
            }
            if (parameters != null) {
                wire.put("parameters", parameters);
            }
            return wire;
        }

        @Override
        public String toString() {
            return "AgentTaskOverrideVO";
        }
    }
}
