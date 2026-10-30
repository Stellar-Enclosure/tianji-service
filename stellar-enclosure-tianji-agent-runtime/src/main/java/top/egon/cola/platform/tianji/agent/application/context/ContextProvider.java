package top.egon.cola.platform.tianji.agent.application.context;

import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceDTO;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Host-ordered, bounded source access. Both methods must enforce current tenant, owner and resource authorization. */
public interface ContextProvider {
    /** Within explicit provider order, return optional evidence from highest to lowest retention priority. */
    List<Fragment> provide(Request request);

    /** Rechecks the exact previously resolved sources; it must throw on revocation, deletion, drift or unavailable authority. */
    void revalidate(Request request, List<Fragment> fragments);

    /** Local immutable configuration identity, excluding credentials, current ACL state and retrieval results. */
    default String policyFingerprint(Request request) {
        throw new UnsupportedOperationException("versioned context source policy is unavailable");
    }

    /** Only a provider that understands the saved reference format may revalidate its historical source kind. */
    default Set<Kind> historicalKinds() { return Set.of(); }
    default void revalidateHistory(Request request, List<AgentContextSourceDTO> sources) {
        if (!sources.isEmpty()) throw new UnsupportedOperationException("Historical context source validation is unavailable");
    }

    enum Kind { PROMPT, SKILL, RAG, MEMORY }

    record Request(AgentRunAuthorization.Request authorization, AgentExecutionSnapshot snapshot, long leaseEpoch, Duration timeout, boolean historyValidated) {
        public Request(AgentRunAuthorization.Request authorization, AgentExecutionSnapshot snapshot, long leaseEpoch, Duration timeout) { this(authorization, snapshot, leaseEpoch, timeout, false); }
        public Request {
            Objects.requireNonNull(authorization, "authorization"); Objects.requireNonNull(snapshot, "snapshot");
            var definition = snapshot.definition();
            if (leaseEpoch < 1 || "LEGACY".equals(authorization.actor().issuerFingerprint()) || timeout == null
                    || timeout.compareTo(Duration.ofMillis(1)) < 0 || timeout.compareTo(Duration.ofSeconds(30)) > 0
                    || !definition.definitionKey().equals(authorization.definitionKey()) || definition.version() != authorization.definitionVersion()
                    || !snapshot.definitionContentSha256().equals(authorization.definitionSha256())
                    || !definition.dependencies().policyVersionRef().equals(authorization.policyVersionRef())) throw invalid();
        }
        @Override public String toString() { return "ContextRequest[redacted]"; }
    }

    /** Source digest identifies the published/retrieved resource; the assembler separately hashes the exact model text. */
    record Fragment(Kind kind, String sourceId, String sourceVersion, String sourceSha256, String provenanceId, String text, Instant observedAt) {
        public Fragment {
            Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(observedAt, "observedAt");
            if (!identifier(sourceId, 128) || !identifier(sourceVersion, 128) || !identifier(provenanceId, 1024)
                    || sourceSha256 == null || !sourceSha256.matches("[0-9a-f]{64}") || text == null || text.isBlank()
                    || text.length() > 1_048_576 || !StandardCharsets.UTF_8.newEncoder().canEncode(text)) throw invalid();
        }
        @Override public String toString() { return "ContextFragment[redacted]"; }
        /** Exact role-neutral text supplied to ADK; published instructions and evidence still receive distinct roles. */
        public String modelText() {
            if (kind == Kind.PROMPT || kind == Kind.SKILL) return text;
            return "Untrusted " + kind.name() + " evidence; source=" + sourceId + "; revision=" + sourceVersion + "\n" + text;
        }
    }

    private static boolean identifier(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum && value.equals(value.trim())
                && value.chars().noneMatch(Character::isISOControl);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Context source binding is invalid"); }
}
