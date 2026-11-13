package top.egon.cola.platform.tianji.contract.orchestration.automation.draft;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DraftSummaryDTO {

    private static final Set<String> ACTIONS = Set.of("SAVE", "VALIDATE", "PUBLISH", "DISCARD");

    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String draftId;
    @NotNull @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.-]{0,127}")
    private String definitionKey;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,128}")
    private String title;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
    private String baseReleaseVersion;
    @NotNull
    private StateEnum state;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long revision;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String contentSha256;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
    private String publishedReleaseVersion;
    @NotNull
    private Instant createdAt;
    @NotNull
    private Instant updatedAt;
    @NotNull @Size(max = 4)
    private List<@NotNull String> availableActions;

    @JsonIgnore
    @AssertTrue(message = "Invalid automation draft summary")
    public boolean isSummaryConsistent() {
        return validSummary(state, publishedReleaseVersion, createdAt, updatedAt, availableActions);
    }

    static boolean validSummary(StateEnum state, String publishedReleaseVersion, Instant createdAt,
                                Instant updatedAt, List<String> actions) {
        return state != null && (state == StateEnum.PUBLISHED) == (publishedReleaseVersion != null)
                && utcMicros(createdAt) && utcMicros(updatedAt) && !updatedAt.isBefore(createdAt)
                && actions != null && actions.stream().allMatch(action -> action != null && ACTIONS.contains(action)
                        && (state == StateEnum.EDITING || "VALIDATE".equals(action)))
                && new HashSet<>(actions).size() == actions.size();
    }

    static boolean utcMicros(Instant time) {
        return time != null && time.getNano() % 1_000 == 0;
    }

    public enum StateEnum {
        EDITING, PUBLISHED, DISCARDED;

        @JsonValue
        public String wire() {
            return name();
        }
    }
}
