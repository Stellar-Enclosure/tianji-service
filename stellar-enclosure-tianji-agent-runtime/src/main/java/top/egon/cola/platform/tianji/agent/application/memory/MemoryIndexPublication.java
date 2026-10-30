package top.egon.cola.platform.tianji.agent.application.memory;

import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Synchronous publication inside the selected Memory transaction; the host adapts it to the existing durable Outbox. */
public interface MemoryIndexPublication {
    boolean usesDataSource(DataSource source);
    Binding binding();
    void publish(Event event);

    record Binding(String modelRef, int dimensions, String vectorTable) {
        public Binding {
            if (!modelReference(modelRef) || dimensions < 1 || dimensions > 8192 || vectorTable == null || vectorTable.length() > 56
                    || !vectorTable.matches("agent_memory_vector(?:_[a-z0-9]+)?")) throw invalid();
        }
        public String activeView() { return vectorTable + "_active"; }
    }

    enum Action { BUILD, DELETE_REVISION, DELETE_MEMORY }

    /** Contains scoped references only, never a content body, bearer token or caller-declared source reference. */
    record Event(Action action, long tenantId, String ownerIssuer, MemoryOwnerDTO owner, String scopeId, String memoryId,
                 long revision, String contentSha256, String modelRef, UUID documentId, Instant occurredAt) {
        public Event {
            action = Objects.requireNonNull(action, "action"); owner = Objects.requireNonNull(owner, "owner"); occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
            if (tenantId < 1 || ownerIssuer == null || !ownerIssuer.matches("[0-9a-f]{64}") || revision < 1
                    || contentSha256 == null || !contentSha256.matches("[0-9a-f]{64}")) throw invalid();
            identifier(scopeId); identifier(memoryId);
            if (action == Action.BUILD) {
                if (!modelReference(modelRef) || documentId == null || !documentId.equals(vectorDocumentId(tenantId, ownerIssuer, owner, scopeId, memoryId, revision, modelRef))) throw invalid();
            } else if (modelRef != null || documentId != null) throw invalid();
        }
        public String eventId() {
            return "memory-index:" + digest("TIANJI_MEMORY_INDEX_EVENT_ID_V1", List.of(action.name(), Long.toString(tenantId), ownerIssuer,
                    owner.type().name(), owner.id(), scopeId, memoryId, Long.toString(revision), modelRef == null ? "" : modelRef));
        }
        public String fingerprint() {
            return digest("TIANJI_MEMORY_INDEX_EVENT_V1", List.of(eventId(), contentSha256, modelRef == null ? "" : modelRef,
                    documentId == null ? "" : documentId.toString(), occurredAt.toString()));
        }
        @Override public String toString() { return "MemoryIndexEvent[redacted]"; }
    }

    static UUID vectorDocumentId(long tenantId, String issuer, MemoryOwnerDTO owner, String scopeId, String memoryId, long revision, String modelRef) {
        if (tenantId < 1 || issuer == null || !issuer.matches("[0-9a-f]{64}") || owner == null || revision < 1 || !modelReference(modelRef)) throw invalid();
        identifier(scopeId); identifier(memoryId);
        byte[] hash = HexFormat.of().parseHex(digest("TIANJI_MEMORY_VECTOR_ID_V1", List.of(Long.toString(tenantId), issuer,
                owner.type().name(), owner.id(), scopeId, memoryId, Long.toString(revision), modelRef)));
        var bytes = ByteBuffer.wrap(hash); return new UUID(bytes.getLong(), bytes.getLong());
    }
    private static boolean modelReference(String value) { return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}@[1-9][0-9]{0,18}#[0-9a-f]{64}"); }
    private static void identifier(String value) {
        if (value == null || value.isBlank() || value.length() > 128 || !value.equals(value.trim()) || value.chars().anyMatch(Character::isISOControl)
                || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw invalid();
    }
    private static String digest(String domain, List<String> fields) {
        StringBuilder framed = new StringBuilder(domain).append(':');
        for (var field : fields) framed.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(framed.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("SHA-256 is unavailable", missing); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory index publication binding is invalid"); }
}
