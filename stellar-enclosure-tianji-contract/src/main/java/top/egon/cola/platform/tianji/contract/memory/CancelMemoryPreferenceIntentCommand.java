package top.egon.cola.platform.tianji.contract.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Cancels pending source extraction, retaining already committed facts and the original cancellation receipt. */
public record CancelMemoryPreferenceIntentCommand(String commandId, MemoryOwnerDTO owner, String scopeId, String runId, long expectedRevision) {
    public CancelMemoryPreferenceIntentCommand {
        commandId = MemoryChecks.identifier(commandId, 128); owner = Objects.requireNonNull(owner, "owner");
        scopeId = MemoryChecks.identifier(scopeId, 128); runId = MemoryChecks.identifier(runId, 64);
        if (owner.type() != MemoryOwnerTypeEnum.USER || expectedRevision < 1) throw MemoryChecks.invalid();
    }
    public GetMemoryPreferenceIntentQuery sourceQuery() { return new GetMemoryPreferenceIntentQuery(owner, scopeId, runId); }
    /** Framed full request identity is computed internally; a caller cannot supply another body's receipt hash. */
    public String requestSha256() {
        var data = new StringBuilder("TIANJI_MEMORY_PREFERENCE_CANCEL_V1:");
        for (var field : new String[]{commandId, owner.type().name(), owner.id(), scopeId, runId, Long.toString(expectedRevision)}) data.append(MemoryChecks.bytes(field)).append(':').append(field);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("SHA-256 is unavailable", missing); }
    }
    @Override public String toString() { return "CancelMemoryPreferenceIntentCommand[redacted]"; }
}
