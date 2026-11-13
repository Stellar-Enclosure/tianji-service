package top.egon.cola.platform.tianji.contract.orchestration.automation.activation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class ActivationPageDTO {

    @NotNull @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.-]{0,127}")
    private String definitionKey;
    @NotNull
    private Instant observedAt;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Valid
    private CurrentActivationVO current;
    @NotNull @Size(max = 100)
    private List<@NotNull @Valid AutomationActivationDTO> items;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Size(min = 1, max = 2048)
    private String nextCursor;

    @JsonIgnore
    @AssertTrue(message = "Invalid automation activation observation")
    public boolean isActivationObservationConsistent() {
        if (definitionKey == null || !AutomationActivationDTO.utcMicros(observedAt) || items == null) {
            return false;
        }
        if (current == null) {
            return items.isEmpty() && nextCursor == null;
        }
        long previous = current.revision() + 1;
        for (AutomationActivationDTO item : items) {
            if (item == null || !definitionKey.equals(item.getDefinitionKey()) || item.getRevision() < 1
                    || item.getRevision() >= previous) {
                return false;
            }
            previous = item.getRevision();
        }
        return true;
    }

    public record CurrentActivationVO(
            @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) long revision,
            @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}") String releaseVersion,
            @NotNull Instant activatedAt) {
        public CurrentActivationVO {
            if (revision < 1 || revision > ProcessTypedJson.V2_MAX_WIRE_INTEGER || releaseVersion == null
                    || !releaseVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}") || !AutomationActivationDTO.utcMicros(activatedAt)) {
                throw new IllegalArgumentException("Invalid current automation activation");
            }
        }
    }
}
