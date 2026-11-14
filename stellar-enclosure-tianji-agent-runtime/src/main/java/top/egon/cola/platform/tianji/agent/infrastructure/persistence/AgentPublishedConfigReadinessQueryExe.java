package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;

import java.util.Objects;
import java.util.function.Consumer;

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

    /** Current dependency verification runs outside the ledger transaction. */
    public RuntimeTemplateReadinessVO requireRuntimeReady(TrustedAgentActor actor, String templateKey, long version,
                                                          String expectedContentSha256,
                                                          Consumer<AgentRuntimeTemplateCommand> verifyCurrentDependencies) {
        var template = ledger.readVerifiedRuntimeTemplate(actor, templateKey, version, expectedContentSha256);
        Objects.requireNonNull(verifyCurrentDependencies).accept(template);
        if (!expectedContentSha256.equals(template.contentSha256())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    "Published Agent Runtime template readiness is unavailable");
        }
        return new RuntimeTemplateReadinessVO(actor.tenantId(), templateKey, version, expectedContentSha256,
                template.getExecutorRef(), template.getConfiguration().modelRef());
    }

    public record RuntimeTemplateReadinessVO(long tenantId, String templateKey, long templateVersion, String contentSha256,
                                             String executorRef, String modelRef) { }
}
