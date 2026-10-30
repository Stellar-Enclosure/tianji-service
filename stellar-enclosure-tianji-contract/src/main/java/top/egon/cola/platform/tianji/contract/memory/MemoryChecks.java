package top.egon.cola.platform.tianji.contract.memory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.UUID;

final class MemoryChecks {
    private MemoryChecks() { }

    static String identifier(String value, int maximum) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > maximum
                || value.chars().anyMatch(Character::isISOControl) || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw invalid();
        return value;
    }
    static String optionalIdentifier(String value, int maximum) { return value == null ? null : identifier(value, maximum); }
    static String hash(String value) { if (value == null || !value.matches("[0-9a-f]{64}")) throw invalid(); return value; }
    static String text(String value, int maximumBytes) {
        if (value == null || value.length() > maximumBytes || !StandardCharsets.UTF_8.newEncoder().canEncode(value)
                || value.chars().anyMatch(character -> Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t')) throw invalid();
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        if (bytes(normalized) > maximumBytes) throw invalid(); return normalized;
    }
    static int bytes(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
    static String sourceCommandId(MemoryOwnerDTO owner, String scopeId, String runId, String sourceEventId, String preferenceKey) {
        if (preferenceKey == null || !preferenceKey.matches("[a-z][a-z0-9_.-]{0,63}")) throw invalid();
        var data = new StringBuilder("TIANJI_MEMORY_PREFERENCE_COMMAND_V1:");
        for (var field : new String[]{owner.type().name(), owner.id(), scopeId, runId, sourceEventId, preferenceKey}) data.append(bytes(field)).append(':').append(field);
        return UUID.nameUUIDFromBytes(data.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }
    static BigDecimal confidence(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0 || value.scale() > 6 || value.scale() < -6 || value.precision() > 7) throw invalid();
        return value.stripTrailingZeros();
    }
    static String reason(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}")) throw invalid(); return value;
    }
    static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory contract is invalid"); }
}
