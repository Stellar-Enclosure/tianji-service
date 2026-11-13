package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.math.BigInteger;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class CompleteEngineHumanTaskCommand {

    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String commandId;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,128}")
    private String expectedVersion;
    @NotNull @Valid @ToString.Exclude
    private Map<String, Object> result;

    @JsonIgnore
    @AssertTrue(message = "Invalid automation human task result")
    public boolean isScalarResultValid() {
        return result != null && result.values().stream().allMatch(CompleteEngineHumanTaskCommand::isScalar)
                && AutomationStartCommand.boundedData(result);
    }

    public void validateAgainst(ProcessSchemaVO resultSchema) {
        if (!EngineHumanTaskDTO.isScalarResultSchema(resultSchema)) {
            throw new IllegalArgumentException("Invalid automation human task result schema");
        }
        resultSchema.validateValue(result);
    }

    private static boolean isScalar(Object value) {
        return value instanceof String || value instanceof Boolean || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long || value instanceof BigInteger;
    }
}
