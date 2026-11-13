package top.egon.cola.platform.tianji.contract.orchestration.automation.draft;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class ProcessDraftPageDTO {

    @NotNull
    private Instant observedAt;
    @NotNull @Size(max = 100)
    private List<@NotNull @Valid DraftSummaryDTO> items;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Size(min = 1, max = 2048)
    private String nextCursor;

    @JsonIgnore
    @AssertTrue(message = "Draft observation timestamp must have microsecond precision")
    public boolean isObservationTimeValid() {
        return DraftSummaryDTO.utcMicros(observedAt);
    }
}
