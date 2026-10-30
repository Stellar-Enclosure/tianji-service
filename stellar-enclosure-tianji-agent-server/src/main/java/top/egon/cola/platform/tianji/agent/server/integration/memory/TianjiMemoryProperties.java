package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.VersionedMemoryEmbeddingModel;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** Explicit deployment-owned Memory settings; no endpoint, credential, tenant or permission is inferred. */
@ConfigurationProperties(prefix = "stellar.tianji.agent.memory")
public record TianjiMemoryProperties(int baseVectorDimensions, String expectedVectorVersion, String vectorTable, int maxConcurrent,
                                     VersionedMemoryEmbeddingModel.Profile model, List<MemoryRetentionPolicy> policies, List<Origin> origins) {
    public TianjiMemoryProperties {
        vectorTable = vectorTable == null ? "agent_memory_vector_v1" : vectorTable; maxConcurrent = maxConcurrent == 0 ? 4 : maxConcurrent;
        model = Objects.requireNonNull(model, "model"); policies = policies == null ? List.of() : List.copyOf(policies); origins = origins == null ? List.of() : List.copyOf(origins);
        if (baseVectorDimensions < 1 || baseVectorDimensions > 8192 || expectedVectorVersion == null || !expectedVectorVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                || vectorTable.length() > 56 || !vectorTable.matches("agent_memory_vector(?:_[a-z0-9]+)?") || maxConcurrent < 1 || maxConcurrent > 16
                || policies.isEmpty() || policies.size() > 32 || origins.isEmpty() || origins.size() > 1000
                || origins.stream().map(value -> value.tenantId() + ":" + value.issuerFingerprint()).distinct().count() != origins.size()) throw invalid();
    }
    public record Origin(long tenantId, URI issuer) {
        public Origin {
            if (issuer == null) throw invalid(); TrustedAgentActor.verified(tenantId, TrustedAgentActor.SubjectType.USER, "memory-origin", issuer.toASCIIString());
        }
        String issuerFingerprint() { return TrustedAgentActor.verified(tenantId, TrustedAgentActor.SubjectType.USER, "memory-origin", issuer.toASCIIString()).issuerFingerprint(); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory deployment settings are invalid"); }
}
