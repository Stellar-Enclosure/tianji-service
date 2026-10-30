package top.egon.cola.platform.tianji.agent.domain.memory;

import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure candidate preflight for reviewed user-input declarations. No model, persistence, activation or authorization occurs here. */
public final class MemoryPreferenceExtraction {
    private static final String PREFIX = "memory.preference.";
    private static final int SOURCE_BYTES = 65_536;

    public Result evaluate(Source source, MemoryExtractionPolicy policy) {
        Objects.requireNonNull(source, "source"); Objects.requireNonNull(policy, "policy");
        if (source.status() != AgentRunStatusEnum.COMPLETED) return rejected(policy, Outcome.RUN_NOT_COMPLETED);
        if (!source.approved()) return rejected(policy, Outcome.SOURCE_NOT_APPROVED);
        if (source.kind() != SourceKind.USER_INPUT) return rejected(policy, Outcome.SOURCE_NOT_USER_INPUT);
        if (source.text().length() > SOURCE_BYTES || source.text().getBytes(StandardCharsets.UTF_8).length > SOURCE_BYTES) return rejected(policy, Outcome.SOURCE_LIMIT);
        if (source.text().contains("```") || source.text().contains("~~~")) return rejected(policy, Outcome.QUOTED_SOURCE);
        var found = new LinkedHashMap<String, Proposal>();
        int start = 0;
        while (start < source.text().length()) {
            int newline = source.text().indexOf('\n', start); int end = newline < 0 ? source.text().length() : newline;
            String line = source.text().substring(start, end).strip();
            if (line.startsWith(PREFIX)) {
                int separator = line.indexOf('=', PREFIX.length());
                if (separator < 0) return rejected(policy, Outcome.UNSUPPORTED_PREFERENCE);
                String name = line.substring(PREFIX.length(), separator).strip(); String value = line.substring(separator + 1).strip();
                if (!policy.preferenceValues().getOrDefault(name, java.util.Set.of()).contains(value)) return rejected(policy, Outcome.UNSUPPORTED_PREFERENCE);
                var previous = found.get(name);
                if (previous != null && !previous.content().facts().get(name).equals(value)) return rejected(policy, Outcome.CONFLICTING_PREFERENCE);
                if (previous == null) {
                    if (found.size() >= policy.maximumCandidates()) return rejected(policy, Outcome.BATCH_LIMIT);
                    found.put(name, new Proposal(MemoryKindEnum.PREFERENCE, new MemoryContentDTO(null, Map.of(name, value)),
                            policy.candidateConfidence(), policy.sensitivity(), policy.retentionPolicyRef(), start, end));
                }
            }
            if (newline < 0) break;
            start = newline + 1;
        }
        if (found.isEmpty()) return rejected(policy, Outcome.NO_DECLARATION);
        String sourceHash = MemoryExtractionPolicy.sha256("TIANJI_MEMORY_PREFERENCE_SOURCE_V1:" + source.text());
        return new Result(policy.reference(), sourceHash, Outcome.ELIGIBLE, new ArrayList<>(found.values()));
    }

    private static Result rejected(MemoryExtractionPolicy policy, Outcome outcome) { return new Result(policy.reference(), null, outcome, List.of()); }

    public enum SourceKind { USER_INPUT, MODEL_OUTPUT, TOOL_OUTPUT, WEB_CONTENT }
    public enum Outcome { ELIGIBLE, RUN_NOT_COMPLETED, SOURCE_NOT_APPROVED, SOURCE_NOT_USER_INPUT, SOURCE_LIMIT,
        QUOTED_SOURCE, UNSUPPORTED_PREFERENCE, CONFLICTING_PREFERENCE, BATCH_LIMIT, NO_DECLARATION }

    /** The caller must derive approval, status and origin from trusted storage/configuration; these values are not identity evidence. */
    public record Source(AgentRunStatusEnum status, SourceKind kind, boolean approved, String text) {
        public Source {
            Objects.requireNonNull(status, "status"); Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(text, "text");
            if (text.length() <= SOURCE_BYTES && !StandardCharsets.UTF_8.newEncoder().canEncode(text)) throw new IllegalArgumentException("Memory extraction source is invalid");
        }
        @Override public String toString() { return "MemoryPreferenceExtraction.Source[redacted]"; }
    }

    /** Offsets refer to the exact UTF-16 source line, excluding LF. This proposal cannot be used as a Memory mutation command. */
    public record Proposal(MemoryKindEnum kind, MemoryContentDTO content, BigDecimal confidence, MemorySensitivityEnum sensitivity,
                           String retentionPolicyRef, int sourceStart, int sourceEnd) {
        public Proposal {
            if (kind != MemoryKindEnum.PREFERENCE || content == null || content.text() != null || content.facts().size() != 1
                    || confidence == null || confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) >= 0
                    || sensitivity != MemorySensitivityEnum.CONFIDENTIAL && sensitivity != MemorySensitivityEnum.RESTRICTED
                    || retentionPolicyRef == null || sourceStart < 0 || sourceEnd <= sourceStart) throw new IllegalArgumentException("Memory preference proposal is invalid");
        }
        public boolean confirmed() { return false; }
        @Override public String toString() { return "MemoryPreferenceExtraction.Proposal[redacted]"; }
    }

    public record Result(String policyRef, String sourceSha256, Outcome outcome, List<Proposal> proposals) {
        public Result {
            Objects.requireNonNull(policyRef, "policyRef"); Objects.requireNonNull(outcome, "outcome"); proposals = List.copyOf(proposals);
            if (outcome == Outcome.ELIGIBLE ? proposals.isEmpty() || proposals.size() > 32 || sourceSha256 == null || !sourceSha256.matches("[0-9a-f]{64}")
                    : !proposals.isEmpty() || sourceSha256 != null) throw new IllegalArgumentException("Memory extraction result is invalid");
        }
        @Override public String toString() { return "MemoryPreferenceExtraction.Result[redacted]"; }
    }
}
