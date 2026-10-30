package top.egon.cola.platform.tianji.agent.domain.memory;

import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Immutable lifetime, quota and explicit model-egress decisions. They do not replace current owner authorization. */
public record MemoryRetentionPolicy(String key, long version, Duration defaultLifetime, Duration maximumLifetime,
                                    int maximumActiveEntries, Set<MemorySensitivityEnum> embeddingAllowed,
                                    Set<MemorySensitivityEnum> modelContextAllowed, boolean semanticQueryAllowed) {
    public MemoryRetentionPolicy {
        if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}") || version < 1 || defaultLifetime == null || maximumLifetime == null
                || defaultLifetime.compareTo(Duration.ofMillis(1)) < 0 || defaultLifetime.getNano() % 1_000_000 != 0 || maximumLifetime.getNano() % 1_000_000 != 0
                || defaultLifetime.compareTo(Duration.ofDays(3650)) > 0
                || maximumLifetime.compareTo(defaultLifetime) < 0 || maximumLifetime.compareTo(Duration.ofDays(3650)) > 0
                || maximumActiveEntries < 1 || maximumActiveEntries > 10_000) throw new IllegalArgumentException("Memory retention policy is invalid");
        embeddingAllowed = Set.copyOf(embeddingAllowed); modelContextAllowed = Set.copyOf(modelContextAllowed);
    }

    public String reference() {
        StringBuilder data = new StringBuilder("TIANJI_MEMORY_RETENTION_V1:");
        for (var field : List.of(defaultLifetime.toString(), maximumLifetime.toString(), Integer.toString(maximumActiveEntries),
                embeddingAllowed.stream().map(Enum::name).sorted().toList().toString(), modelContextAllowed.stream().map(Enum::name).sorted().toList().toString(),
                Boolean.toString(semanticQueryAllowed))) data.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.toString().getBytes(StandardCharsets.UTF_8)));
            return key + '@' + version + '#' + hash;
        } catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("SHA-256 is unavailable", missing); }
    }
}
