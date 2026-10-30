package top.egon.cola.platform.tianji.contract.memory;

/** Caller-declared provenance. A run/event claim requires server validation and does not imply verified evidence. */
public record MemorySourceDTO(String reference, String evidenceSha256, String sourceRunId, String sourceEventId) {
    public MemorySourceDTO {
        reference = MemoryChecks.identifier(reference, 512); evidenceSha256 = MemoryChecks.hash(evidenceSha256);
        sourceRunId = MemoryChecks.optionalIdentifier(sourceRunId, 64); sourceEventId = MemoryChecks.optionalIdentifier(sourceEventId, 128);
        if ((sourceRunId == null) != (sourceEventId == null)) throw MemoryChecks.invalid();
    }
    @Override public String toString() { return "MemorySource[redacted]"; }
}
