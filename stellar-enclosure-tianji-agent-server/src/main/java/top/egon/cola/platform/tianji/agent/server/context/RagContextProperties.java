package top.egon.cola.platform.tianji.agent.server.context;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.rag.RagContextProvider;

import java.net.URI;
import java.util.List;

/** Server-owned source selection. Current ACLs and document facts must come from the business authority. */
@ConfigurationProperties(prefix = "stellar.tianji.agent.context.rag")
public record RagContextProperties(int maxConcurrent, List<Binding> bindings) {
    public RagContextProperties {
        maxConcurrent = maxConcurrent == 0 ? 4 : maxConcurrent;
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        if (maxConcurrent < 1 || maxConcurrent > 16 || bindings.isEmpty() || bindings.size() > 1000) throw invalid();
    }
    public record Binding(String id, long tenantId, URI issuer, String definitionKey, long definitionVersion,
                          String collectionId, String logicalModelName, int topK, Double similarityThreshold) {
        public Binding {
            topK = topK == 0 ? 4 : topK; similarityThreshold = similarityThreshold == null ? 0.5 : similarityThreshold;
            if (issuer == null) throw invalid();
            String fingerprint = TrustedAgentActor.verified(tenantId, TrustedAgentActor.SubjectType.USER, "rag-context-binding", issuer.toASCIIString()).issuerFingerprint();
            new RagContextProvider.Binding(id, tenantId, fingerprint, definitionKey, definitionVersion, collectionId, logicalModelName, topK, similarityThreshold);
        }
        RagContextProvider.Binding runtime() {
            var actor = TrustedAgentActor.verified(tenantId, TrustedAgentActor.SubjectType.USER, "rag-context-binding", issuer.toASCIIString());
            return new RagContextProvider.Binding(id, tenantId, actor.issuerFingerprint(), definitionKey, definitionVersion, collectionId, logicalModelName, topK, similarityThreshold);
        }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("RAG context source configuration is invalid"); }
}
