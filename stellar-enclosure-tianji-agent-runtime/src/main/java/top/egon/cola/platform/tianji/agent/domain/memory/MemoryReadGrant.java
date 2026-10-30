package top.egon.cola.platform.tianji.agent.domain.memory;

import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Independently sealed Memory-read evidence for one immutable run and one exact owner/scope. It carries no USER bearer. */
public final class MemoryReadGrant {
    public static final String PURPOSE = "TIANJI_MEMORY_READ";
    private MemoryReadGrant() { }

    /** Preserves the IdP's disjoint USER/SERVICE ticket namespaces instead of relabeling a SERVICE ticket as a USER ticket. */
    public record Ticket(String grantId, String ciphertext, Instant expiresAt) {
        public Ticket {
            if (grantId != null && grantId.startsWith("8")) new AgentServiceExecutionGrant.Ticket(grantId, ciphertext, expiresAt);
            else new AgentExecutionGrant.Ticket(grantId, ciphertext, expiresAt);
        }
        public TrustedAgentActor.SubjectType subjectType() { return grantId.startsWith("8") ? TrustedAgentActor.SubjectType.SERVICE : TrustedAgentActor.SubjectType.USER; }
        @Override public String toString() { return "MemoryReadTicket[redacted]"; }
    }

    public record Scope(MemoryOwnerDTO owner, String scopeId, String sourceBindingSha256, AgentServiceExecutionGrant.Subject serviceSubject) {
        public Scope {
            owner = Objects.requireNonNull(owner, "owner");
            if (scopeId == null || scopeId.isBlank() || scopeId.length() > 128 || !scopeId.equals(scopeId.trim())
                    || scopeId.chars().anyMatch(Character::isISOControl) || !StandardCharsets.UTF_8.newEncoder().canEncode(scopeId)
                    || sourceBindingSha256 == null || !sourceBindingSha256.matches("[0-9a-f]{64}")) throw invalid();
        }
        @Override public String toString() { return "MemoryReadScope[redacted]"; }
    }
    public record Reservation(AgentRunAuthorization.Request run, URI resource, Scope scope, long epoch, UUID intentId, String bindingSha256) {
        public Reservation {
            if (epoch < 1 || !MemoryReadGrant.intentId(run, scope).equals(intentId) || !fingerprint(run, resource, scope).equals(bindingSha256)) throw invalid();
        }
        @Override public String toString() { return "MemoryReadReservation[redacted]"; }
    }

    public static String fingerprint(AgentRunAuthorization.Request run, URI resource, Scope scope) {
        Objects.requireNonNull(run, "run"); Objects.requireNonNull(scope, "scope");
        String execution;
        if (run.actor().subjectType() == TrustedAgentActor.SubjectType.USER) {
            if (scope.serviceSubject() != null) throw invalid(); execution = AgentExecutionGrant.fingerprint(run, resource);
        } else {
            if (scope.serviceSubject() == null) throw invalid(); execution = AgentServiceExecutionGrant.fingerprint(run, resource, scope.serviceSubject());
        }
        return hash(List.of("TIANJI_MEMORY_READ_BINDING_V1", PURPOSE, resource.toASCIIString(), execution, scope.owner().type().name(), scope.owner().id(), scope.scopeId(), scope.sourceBindingSha256()));
    }
    public static UUID intentId(AgentRunAuthorization.Request run, Scope scope) {
        Objects.requireNonNull(run, "run"); Objects.requireNonNull(scope, "scope");
        byte[] value = HexFormat.of().parseHex(hash(List.of("TIANJI_MEMORY_READ_INTENT_V1", run.actor().identityFingerprint(), run.runId(), scope.owner().type().name(),
                scope.owner().id(), scope.scopeId(), scope.sourceBindingSha256())));
        var data = ByteBuffer.wrap(value); return new UUID(data.getLong(), data.getLong());
    }
    private static String hash(List<String> fields) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var stream = new DataOutputStream(bytes)) { for (var field : fields) { byte[] value = field.getBytes(StandardCharsets.UTF_8); stream.writeInt(value.length); stream.write(value); } }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException unavailable) { throw new IllegalStateException("Memory grant digest is unavailable"); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory read grant binding is invalid"); }
}
