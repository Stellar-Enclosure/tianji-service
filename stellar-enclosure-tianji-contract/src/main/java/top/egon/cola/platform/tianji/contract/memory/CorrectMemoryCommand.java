package top.egon.cola.platform.tianji.contract.memory;

import java.math.BigDecimal;
import java.util.Objects;

/** Explicit confirmation/correction preserves owner, kind, sensitivity and retention; it never revives a tombstone. */
public record CorrectMemoryCommand(String commandId, MemoryOwnerDTO owner, String scopeId, String memoryId, long expectedRevision,
                                   MemoryContentDTO content, MemorySourceDTO source, BigDecimal confidence, String reasonCode) {
    public CorrectMemoryCommand {
        commandId = MemoryChecks.identifier(commandId, 128); owner = Objects.requireNonNull(owner, "owner");
        scopeId = MemoryChecks.identifier(scopeId, 128); memoryId = MemoryChecks.identifier(memoryId, 128);
        if (expectedRevision < 1) throw MemoryChecks.invalid();
        content = Objects.requireNonNull(content, "content"); source = Objects.requireNonNull(source, "source");
        confidence = MemoryChecks.confidence(confidence); reasonCode = MemoryChecks.reason(reasonCode);
    }
    @Override public String toString() { return "CorrectMemoryCommand[redacted]"; }
}
