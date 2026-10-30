package top.egon.cola.platform.tianji.agent.infrastructure.rag;

import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;

import java.util.List;

/** Current business authority, separate from the vector index. No source content or credentials cross this port. */
public interface RagContextAuthority {
    /** Checks current collection/model access and returns the current owner-partition ACL revision before search. */
    Scope authorize(ContextProvider.Request request, RagContextProvider.Binding binding);

    /**
     * Rechecks current search access and every exact document revision, chunk identity and content digest against
     * authoritative facts. Vector metadata and previously issued scopes are expectations, never permission evidence.
     * Must throw on denial, deletion, drift or unavailable authority, including when sources is empty.
     */
    void revalidate(ContextProvider.Request request, RagContextProvider.Binding binding, Scope scope, List<Source> sources);

    /** Local immutable configuration digest, excluding credentials and current ACL decisions. Never performs I/O. */
    String policyFingerprint();

    record Scope(String ownerSha256, String collectionId, String logicalModelName, String aclRevision) {
        public Scope {
            if (!hash(ownerSha256)) throw invalid();
            identifier(collectionId); identifier(logicalModelName); identifier(aclRevision);
        }
        @Override public String toString() { return "RagScope[redacted]"; }
    }

    record Source(String ownerSha256, String bindingId, String collectionId, String logicalModelName, String documentId, String chunkId,
                  int chunkIndex, String documentRevision, String contentSha256, String aclRevision) {
        public Source {
            identifier(bindingId); identifier(collectionId); identifier(logicalModelName); identifier(documentId);
            identifier(chunkId); identifier(documentRevision); identifier(aclRevision);
            if (chunkIndex < 0 || !hash(ownerSha256) || !hash(contentSha256)) throw invalid();
        }
        @Override public String toString() { return "RagSource[redacted]"; }
    }

    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static void identifier(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 128
                || value.chars().anyMatch(Character::isISOControl) || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("RAG authority reference is invalid"); }
}
