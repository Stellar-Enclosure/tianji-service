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
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static top.egon.cola.platform.tianji.agent.domain.RuntimeBindingBO.SHA_256_PATTERN;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class RuntimeCallReceiptBO {
    public enum Kind { MODEL, TOOL, TOOL_RESULT_READ }
    public enum State { PREPARED, DISPATCHED, SUCCEEDED, FAILED, UNKNOWN }

    public record CapabilityVO(@NotNull UUID id, @NotNull @Pattern(regexp = SHA_256_PATTERN) String bindingSha256,
                               @Positive long issueEpoch, @Pattern(regexp = "[89][0-9]{62}") String grantId,
                               @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}") String toolKey,
                               @Positive long toolVersion, @NotNull @Pattern(regexp = SHA_256_PATTERN) String toolSha256,
                               @NotNull @Pattern(regexp = SHA_256_PATTERN) String backendBindingSha256, @NotNull ToolPolicy.Risk risk) {
        public CapabilityVO {
            if (id == null || !RuntimeBindingBO.hash(bindingSha256) || issueEpoch <= 0
                    || grantId != null && !grantId.matches("[89][0-9]{62}")
                    || toolKey == null || !toolKey.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}") || toolVersion <= 0
                    || !RuntimeBindingBO.hash(toolSha256) || !RuntimeBindingBO.hash(backendBindingSha256) || risk == null) {
                throw invalid();
            }
        }
    }

    public record DispatchVO(@Positive long epoch, @NotNull @Pattern(regexp = SHA_256_PATTERN) String nonceSha256,
                             @NotNull @Pattern(regexp = SHA_256_PATTERN) String executorIssuer,
                             @NotBlank @Size(max = 256) String executorId) {
        public DispatchVO {
            if (epoch <= 0 || !RuntimeBindingBO.hash(nonceSha256) || !RuntimeBindingBO.hash(executorIssuer)
                    || !RuntimeBindingBO.identifier(executorId, 256)) throw invalid();
        }
    }

    public record SourceResultVO(@Positive long revision, @NotNull @Pattern(regexp = SHA_256_PATTERN) String resultSha256) {
        public SourceResultVO {
            if (revision <= 0 || !RuntimeBindingBO.hash(resultSha256)) throw invalid();
        }
    }

    public record OutcomeVO(@NotNull State state, @Pattern(regexp = SHA_256_PATTERN) String resultSha256,
                            @PositiveOrZero Long inputTokens, @PositiveOrZero Long outputTokens,
                            @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}") String errorCode) {
        public OutcomeVO {
            if (state == null || !Set.of(State.SUCCEEDED, State.FAILED, State.UNKNOWN).contains(state)
                    || (state == State.SUCCEEDED && (!RuntimeBindingBO.hash(resultSha256) || errorCode != null))
                    || (state != State.SUCCEEDED && (resultSha256 != null || errorCode == null
                        || !errorCode.matches("[A-Z][A-Z0-9_]{0,127}")))
                    || (inputTokens != null && inputTokens < 0) || (outputTokens != null && outputTokens < 0)) throw invalid();
        }
    }

    @Positive
    private long tenantId;
    @NotBlank @Size(max = 64)
    private String runId;
    @NotNull
    private UUID callId;
    @NotNull
    private Kind kind;
    @Valid @NotNull
    private TrustedAgentActor owner;
    @Positive
    private long leaseEpoch;
    @Valid
    private CapabilityVO capability;
    @NotNull @Pattern(regexp = SHA_256_PATTERN)
    private String inputSha256;
    @NotNull
    private Map<String, Object> contextMetadata;
    private UUID intentId;
    @Valid
    private DispatchVO dispatch;
    @Valid
    private SourceResultVO sourceResult;
    private State state;
    @PositiveOrZero
    private long revision;
    @NotNull
    private Instant deadlineAt;
    @Pattern(regexp = SHA_256_PATTERN)
    private String resultSha256;
    @PositiveOrZero
    private Long inputTokens;
    @PositiveOrZero
    private Long outputTokens;
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}")
    private String errorCode;
    private Instant createdAt;
    private Instant dispatchedAt;
    private Instant finishedAt;

    public void validateForPrepare() {
        if (tenantId <= 0 || !RuntimeBindingBO.identifier(runId, 64) || callId == null || kind == null
                || owner == null || owner.tenantId() != tenantId || !RuntimeBindingBO.hash(owner.issuerFingerprint())
                || !RuntimeBindingBO.hash(inputSha256) || leaseEpoch <= 0 || contextMetadata == null
                || deadlineAt == null || !deadlineAt.equals(deadlineAt.truncatedTo(ChronoUnit.MICROS))
                || state != null || revision != 0 || resultSha256 != null || inputTokens != null || outputTokens != null
                || errorCode != null || createdAt != null || dispatchedAt != null || finishedAt != null || dispatch != null) {
            throw invalid();
        }
        validateContextMetadata();
        if ((kind == Kind.MODEL) != (capability == null)
                || (kind == Kind.TOOL_RESULT_READ) != (sourceResult != null)
                || (kind == Kind.TOOL_RESULT_READ || isWrite()) != (intentId != null)
                || kind == Kind.TOOL_RESULT_READ && capability.risk() != ToolPolicy.Risk.IDEMPOTENT_WRITE
                || capability != null && capability.grantId() != null
                    && (owner.subjectType() == TrustedAgentActor.SubjectType.USER) != capability.grantId().startsWith("9")) {
            throw invalid();
        }
    }

    public void validateContextMetadata() {
        var allowed = switch (kind) {
            case MODEL -> Set.of("modelRef", "sources");
            case TOOL -> Set.of("sources");
            case TOOL_RESULT_READ -> Set.of("authorization", "sources");
        };
        if (!allowed.containsAll(contextMetadata.keySet())
                || kind == Kind.MODEL && !reference(contextMetadata.get("modelRef"))
                || kind == Kind.TOOL_RESULT_READ && !(contextMetadata.get("authorization") instanceof Map<?, ?>)) throw invalid();
        if (!contextMetadata.containsKey("sources")) return;
        if (!(contextMetadata.get("sources") instanceof List<?> sources) || sources.size() > 128) throw invalid();
        for (Object entry : sources) {
            if (!(entry instanceof Map<?, ?> source) || !source.keySet().equals(Set.of("sourceRef", "sourceRevision", "contentSha256"))
                    || !reference(source.get("sourceRef")) || !(source.get("sourceRevision") instanceof Integer || source.get("sourceRevision") instanceof Long)
                    || ((Number) source.get("sourceRevision")).longValue() <= 0
                    || !(source.get("contentSha256") instanceof String hash) || !RuntimeBindingBO.hash(hash)) throw invalid();
        }
    }

    private static boolean reference(Object value) {
        return value instanceof String ref && ref.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}");
    }

    public boolean isWrite() {
        return kind == Kind.TOOL && capability != null && capability.risk() != ToolPolicy.Risk.READ_ONLY;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid runtime call receipt");
    }
}
