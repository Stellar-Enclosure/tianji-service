package top.egon.cola.platform.tianji.agent.domain.memory;

import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Independent USER delegation for one approved input source; it contains sealed evidence and never a USER bearer. */
public final class MemoryPreferenceIntent {
    public static final String PURPOSE = "TIANJI_MEMORY_WRITE";
    private MemoryPreferenceIntent() { }

    public enum State {
        RESERVED, READY, CLAIMED, APPLIED, SKIPPED, REJECTED, EXPIRED, CANCELLED, DEAD;
        public boolean terminal() { return this != RESERVED && this != READY && this != CLAIMED; }
        public boolean sealed() { return this == READY || this == CLAIMED; }
    }

    public record Reservation(AgentRunAuthorization.Request run, MemoryPreferenceSourceBinding binding, URI resource,
                               Instant deadlineAt, long issueEpoch, UUID intentId, String bindingSha256) {
        public Reservation {
            Objects.requireNonNull(run, "run"); Objects.requireNonNull(binding, "binding"); Objects.requireNonNull(resource, "resource");
            if (issueEpoch < 1 || !MemoryPreferenceIntent.intentId(run, binding.scopeId()).equals(intentId)
                    || !fingerprint(run, binding, resource, deadlineAt).equals(bindingSha256)) throw invalid();
        }
        @Override public String toString() { return "MemoryPreferenceReservation[redacted]"; }
    }

    public record Evidence(Reservation reservation, AgentExecutionGrant.Ticket ticket) {
        public Evidence {
            reservation = Objects.requireNonNull(reservation, "reservation"); ticket = Objects.requireNonNull(ticket, "ticket");
            if (ticket.expiresAt().isAfter(reservation.deadlineAt())) throw invalid();
        }
        @Override public String toString() { return "MemoryPreferenceEvidence[redacted]"; }
    }

    public record Lease(Evidence evidence, String owner, long epoch, Instant expiresAt, String sourceEventId,
                         int nextIndex, Integer totalCount, String commandsSha256, long revision) {
        public Lease {
            evidence = Objects.requireNonNull(evidence, "evidence");
            if (!identifier(owner, 128) || epoch < 1 || revision < 1 || expiresAt == null || !microseconds(expiresAt)
                    || expiresAt.isAfter(evidence.ticket().expiresAt()) || !identifier(sourceEventId, 64) || nextIndex < 0 || nextIndex > 32
                    || totalCount == null && (nextIndex != 0 || commandsSha256 != null)
                    || totalCount != null && (totalCount < 0 || totalCount > 32 || nextIndex > totalCount || !hash(commandsSha256))) throw invalid();
        }
        @Override public String toString() { return "MemoryPreferenceLease[redacted]"; }
    }

    /** Technical progress only. It never projects a sealed grant, USER proof or candidate body. */
    public record View(UUID intentId, String runId, String scopeId, String bindingId, String policyRef, State state, long revision,
                        int nextIndex, Integer totalCount, int attemptCount, String reasonCode, Instant createdAt, Instant updatedAt, Instant closedAt) {
        public View {
            Objects.requireNonNull(intentId, "intentId"); Objects.requireNonNull(state, "state"); Objects.requireNonNull(createdAt, "createdAt"); Objects.requireNonNull(updatedAt, "updatedAt");
            if (!identifier(runId, 64) || !identifier(scopeId, 128) || !identifier(bindingId, 128) || !identifier(policyRef, 256) || revision < 1
                    || nextIndex < 0 || nextIndex > 32 || totalCount != null && (totalCount < nextIndex || totalCount > 32)
                    || totalCount == null && nextIndex != 0 || attemptCount < 0 || updatedAt.isBefore(createdAt) || state.terminal() != (closedAt != null)) throw invalid();
        }
        @Override public String toString() { return "MemoryPreferenceIntentView[redacted]"; }
    }

    /** Mutable configuration cannot choose another intent for the same original actor/run/scope. */
    public static UUID intentId(AgentRunAuthorization.Request run, String scopeId) {
        Objects.requireNonNull(run, "run");
        if (run.actor().subjectType() != TrustedAgentActor.SubjectType.USER || "LEGACY".equals(run.actor().issuerFingerprint()) || !identifier(scopeId, 128)) throw invalid();
        var data = ByteBuffer.wrap(HexFormat.of().parseHex(digest(List.of("TIANJI_MEMORY_PREFERENCE_INTENT_V1", run.actor().identityFingerprint(), run.runId(), scopeId))));
        return new UUID(data.getLong(), data.getLong());
    }

    public static String fingerprint(AgentRunAuthorization.Request run, MemoryPreferenceSourceBinding binding, URI resource, Instant deadlineAt) {
        Objects.requireNonNull(run, "run"); Objects.requireNonNull(binding, "binding");
        if (run.actor().subjectType() != TrustedAgentActor.SubjectType.USER || !binding.matches(run) || deadlineAt == null || !microseconds(deadlineAt)) throw invalid();
        String source = AgentExecutionGrant.fingerprint(run, resource);
        return digest(List.of("TIANJI_MEMORY_PREFERENCE_WRITE_BINDING_V1", PURPOSE, resource.toASCIIString(), source, binding.id(), Long.toString(binding.tenantId()),
                binding.issuerFingerprint(), binding.definitionKey(), Long.toString(binding.definitionVersion()), binding.definitionSha256(), binding.scopeId(), binding.policyRef(), deadlineAt.toString()));
    }

    private static String digest(List<String> fields) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var stream = new DataOutputStream(bytes)) { for (var field : fields) { byte[] value = field.getBytes(StandardCharsets.UTF_8); stream.writeInt(value.length); stream.write(value); } }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException missing) { throw new IllegalStateException("Memory preference intent digest is unavailable"); }
    }
    private static boolean identifier(String value, int maximum) { return value != null && !value.isBlank() && value.length() <= maximum && value.equals(value.trim()) && value.chars().noneMatch(Character::isISOControl) && StandardCharsets.UTF_8.newEncoder().canEncode(value); }
    private static boolean microseconds(Instant value) { return value.getNano() % 1000 == 0; }
    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory preference intent binding is invalid"); }
}
