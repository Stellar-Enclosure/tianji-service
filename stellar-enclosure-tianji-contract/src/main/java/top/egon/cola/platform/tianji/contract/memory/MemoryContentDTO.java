package top.egon.cola.platform.tianji.contract.memory;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Explicit text and flat structured facts. NFC normalizes Unicode only; case and meaningful whitespace remain intact. */
public record MemoryContentDTO(String text, Map<String, String> facts) {
    public MemoryContentDTO {
        if (text != null) { text = MemoryChecks.text(text, 16_384); if (text.isBlank()) text = null; }
        if (facts != null && facts.size() > 64) throw MemoryChecks.invalid();
        var normalized = new TreeMap<String, String>();
        int bytes = text == null ? 0 : MemoryChecks.bytes(text);
        if (facts != null) for (var fact : facts.entrySet()) {
            String key = MemoryChecks.text(MemoryChecks.identifier(fact.getKey(), 128), 512);
            String value = MemoryChecks.text(fact.getValue(), 4096);
            if (normalized.putIfAbsent(key, value) != null) throw MemoryChecks.invalid();
            bytes += MemoryChecks.bytes(key) + MemoryChecks.bytes(value);
        }
        if (text == null && normalized.isEmpty() || bytes > 16_384) throw MemoryChecks.invalid();
        facts = Collections.unmodifiableMap(normalized);
    }
    @Override public String toString() { return "MemoryContent[redacted]"; }
}
