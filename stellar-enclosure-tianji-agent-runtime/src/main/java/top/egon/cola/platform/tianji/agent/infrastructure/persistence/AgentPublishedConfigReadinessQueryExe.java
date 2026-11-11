package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.util.Objects;

/** Owner-side local query, assembled with the installed executor and its exact ledger. */
public final class AgentPublishedConfigReadinessQueryExe {
    private final AgentLedgerService ledger;
    private final AdkAgentRunExecutor executor;

    public AgentPublishedConfigReadinessQueryExe(AgentLedgerService ledger, AdkAgentRunExecutor executor) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.executor = Objects.requireNonNull(executor, "executor");
        if (!executor.usesLedger(ledger)) {
            throw new IllegalArgumentException("Published Agent CONFIG readiness requires the executor ledger");
        }
    }

    /** Requires prior authorization to read the exact publication within the actor's tenant. */
    public PublishedConfigReadinessEvidenceDTO requireReady(TrustedAgentActor actor, String definitionKey,
                                                             long version, String expectedContentSha256) {
        if (expectedContentSha256 == null || !expectedContentSha256.matches("[0-9a-f]{64}")) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND, "Agent publication query is invalid");
        }
        var publication = ledger.readVerifiedPublication(actor, definitionKey, version);
        if (!expectedContentSha256.equals(publication.evidence().definitionContentSha256())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    "Published Agent CONFIG readiness is unavailable");
        }
        executor.requirePublishedConfigReady(publication.definition());
        return new PublishedConfigReadinessEvidenceDTO(publication.evidence());
    }

    public record PublishedConfigReadinessEvidenceDTO(AgentLedgerService.PublishedDefinitionEvidenceDTO publication) {
        @Override public String toString() { return "PublishedConfigReadinessEvidenceDTO[redacted]"; }
    }
}
