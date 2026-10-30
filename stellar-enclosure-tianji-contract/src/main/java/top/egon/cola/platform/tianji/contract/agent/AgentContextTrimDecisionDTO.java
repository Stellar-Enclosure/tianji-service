package top.egon.cola.platform.tianji.contract.agent;

import java.util.Objects;

public record AgentContextTrimDecisionDTO(AgentContextSourceKindEnum kind, String sourceId, String sourceVersion,
                                           String reason, String beforeSha256, String afterSha256, int droppedItems) {
    public AgentContextTrimDecisionDTO {
        kind = Objects.requireNonNull(kind, "kind");
        sourceId = ContractChecks.contextIdentifier(sourceId, "sourceId", 128);
        sourceVersion = ContractChecks.contextIdentifier(sourceVersion, "sourceVersion", 128);
        reason = ContractChecks.contextIdentifier(reason, "reason", 64);
        beforeSha256 = ContractChecks.contextHash(beforeSha256); afterSha256 = ContractChecks.contextHash(afterSha256);
        if (!reason.matches("[A-Z][A-Z0-9_]{0,63}") || droppedItems < 1 || droppedItems > 1000
                || kind != AgentContextSourceKindEnum.RAG && kind != AgentContextSourceKindEnum.MEMORY
                && kind != AgentContextSourceKindEnum.ADK_CONTENTS) throw new IllegalArgumentException("context trim decision is invalid");
    }
}
