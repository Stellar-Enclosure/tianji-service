package top.egon.cola.platform.tianji.agent.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TechnicalLimitsVO;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class RuntimeBindingBO {
    public static final String RUNTIME_V2 = "RUNTIME_V2";
    static final String SHA_256_PATTERN = "[0-9a-f]{64}";

    @Positive
    private long tenantId;
    @NotBlank @Size(max = 64)
    private String runId;
    @NotNull @Pattern(regexp = RUNTIME_V2)
    private String profile;
    @NotNull @Pattern(regexp = SHA_256_PATTERN)
    private String snapshotSha256;
    @NotNull @Pattern(regexp = SHA_256_PATTERN)
    private String configurationSha256;
    @NotNull @Pattern(regexp = SHA_256_PATTERN)
    private String templateSha256;
    @Valid @NotNull
    private TechnicalLimitsVO policy;
    @NotBlank @Size(max = 128)
    private String nodeExecutionRef;
    @NotNull
    private Instant attemptDeadlineAt;
    @PositiveOrZero
    private int modelCalls;
    @PositiveOrZero
    private int toolCalls;
    @PositiveOrZero
    private int activeCalls;
    @PositiveOrZero
    private long revision;
    private Instant createdAt;

    public void validateForCreate() {
        if (tenantId <= 0 || !identifier(runId, 64) || !RUNTIME_V2.equals(profile)
                || !hash(snapshotSha256) || !hash(configurationSha256) || !hash(templateSha256) || policy == null
                || !identifier(nodeExecutionRef, 128) || attemptDeadlineAt == null
                || !attemptDeadlineAt.equals(attemptDeadlineAt.truncatedTo(ChronoUnit.MICROS))
                || modelCalls != 0 || toolCalls != 0 || activeCalls != 0 || revision != 0 || createdAt != null) {
            throw new IllegalArgumentException("Invalid runtime binding");
        }
    }

    static boolean hash(String value) {
        return value != null && value.matches(SHA_256_PATTERN);
    }

    static boolean identifier(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength
                && value.equals(value.strip()) && value.codePoints().noneMatch(Character::isISOControl);
    }
}
