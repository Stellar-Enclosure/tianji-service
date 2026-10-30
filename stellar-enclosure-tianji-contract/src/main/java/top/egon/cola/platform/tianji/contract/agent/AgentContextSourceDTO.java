package top.egon.cola.platform.tianji.contract.agent;

import java.time.Instant;
import java.util.Objects;

/** Provenance and model-text digest only; this object never contains source or prompt bodies. */
public record AgentContextSourceDTO(AgentContextSourceKindEnum kind, AgentContextRoleEnum role, String sourceId,
                                     String sourceVersion, String provenanceId, String sourceSha256,
                                     String modelTextSha256, long utf8Bytes, Instant observedAt) {
    public AgentContextSourceDTO {
        kind = Objects.requireNonNull(kind, "kind"); role = Objects.requireNonNull(role, "role");
        sourceId = ContractChecks.contextIdentifier(sourceId, "sourceId", 128);
        sourceVersion = ContractChecks.contextIdentifier(sourceVersion, "sourceVersion", 128);
        provenanceId = ContractChecks.contextIdentifier(provenanceId, "provenanceId", 1024);
        sourceSha256 = ContractChecks.contextHash(sourceSha256); modelTextSha256 = ContractChecks.contextHash(modelTextSha256);
        AgentContextRoleEnum expected = switch (kind) {
            case DEFINITION, PROMPT, SKILL, ADK_SYSTEM -> AgentContextRoleEnum.SYSTEM;
            case ADK_CONTENTS -> AgentContextRoleEnum.MIXED;
            default -> AgentContextRoleEnum.USER;
        };
        if (utf8Bytes < 0 || utf8Bytes > 16_777_216 || role != expected
                || (kind == AgentContextSourceKindEnum.PROMPT || kind == AgentContextSourceKindEnum.SKILL
                || kind == AgentContextSourceKindEnum.RAG || kind == AgentContextSourceKindEnum.MEMORY) && observedAt == null) {
            throw new IllegalArgumentException("context source projection is invalid");
        }
    }
}
