package top.egon.cola.platform.tianji.agent.domain.memory;

import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** An immutable allowlist for explicit preference declarations; it grants no source access or Memory write authority. */
public record MemoryExtractionPolicy(String key, long version, Map<String, Set<String>> preferenceValues,
                                     BigDecimal candidateConfidence, MemorySensitivityEnum sensitivity,
                                     String retentionPolicyRef, int maximumCandidates) {
    public MemoryExtractionPolicy {
        if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}") || version < 1
                || preferenceValues == null || preferenceValues.isEmpty() || preferenceValues.size() > 32
                || candidateConfidence == null || candidateConfidence.signum() < 0 || candidateConfidence.compareTo(BigDecimal.ONE) >= 0
                || candidateConfidence.scale() > 6 || candidateConfidence.scale() < -6 || candidateConfidence.precision() > 7
                || sensitivity != MemorySensitivityEnum.CONFIDENTIAL && sensitivity != MemorySensitivityEnum.RESTRICTED
                || retentionPolicyRef == null || !retentionPolicyRef.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}@[1-9][0-9]{0,18}#[0-9a-f]{64}")
                || maximumCandidates < 1 || maximumCandidates > 32) throw invalid();
        var copy = new TreeMap<String, Set<String>>();
        for (var entry : preferenceValues.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[a-z][a-z0-9_.-]{0,63}")
                    || entry.getValue() == null || entry.getValue().isEmpty() || entry.getValue().size() > 64) throw invalid();
            var values = new TreeSet<String>();
            for (var value : entry.getValue()) {
                if (value == null || value.isBlank() || value.length() > 128 || !value.equals(value.strip())
                        || value.chars().anyMatch(Character::isISOControl) || !StandardCharsets.UTF_8.newEncoder().canEncode(value)
                        || !Normalizer.isNormalized(value, Normalizer.Form.NFC)) throw invalid();
                values.add(value);
            }
            copy.put(entry.getKey(), Set.copyOf(values));
        }
        preferenceValues = Map.copyOf(copy); candidateConfidence = candidateConfidence.stripTrailingZeros();
    }

    public String reference() {
        var data = new StringBuilder("TIANJI_MEMORY_EXTRACTION_POLICY_V1:");
        for (var field : new String[]{key, Long.toString(version), candidateConfidence.toPlainString(), sensitivity.name(), retentionPolicyRef, Integer.toString(maximumCandidates)}) append(data, field);
        new TreeMap<>(preferenceValues).forEach((name, values) -> {
            append(data, name); append(data, Integer.toString(values.size()));
            new TreeSet<>(values).forEach(value -> append(data, value));
        });
        return key + '@' + version + '#' + sha256(data.toString());
    }

    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("SHA-256 is unavailable", missing); }
    }
    private static void append(StringBuilder data, String value) { data.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory extraction policy is invalid"); }
    @Override public String toString() { return "MemoryExtractionPolicy[redacted]"; }
}
