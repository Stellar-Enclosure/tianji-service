package top.egon.cola.platform.tianji.agent.application.run;

import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;

import javax.sql.DataSource;

/** Synchronous durable publication in the ledger's JDBC transaction. Implementations must not call a network. */
public interface AgentTerminalEventPublisher {
    String producerId();
    boolean usesDataSource(DataSource source);
    void publish(AgentTerminalEvent event);

    /** Only the installed publisher may address a terminal fact to a registered destination. */
    default String runtimeDestination(TrustedAgentActor owner, String sourceId) { return null; }

    /** A host lookup performed by the runtime outside the selected database transaction. */
    default AgentResultArtifactEvidenceVO verifyArtifact(AgentResultArtifactQuery query) {
        return new AgentResultArtifactEvidenceVO(AgentResultArtifactEvidenceVO.DecisionEnum.UNAVAILABLE, null, null, null, null);
    }

    default void publish(AgentTerminalEventV2 event) {
        throw new UnsupportedOperationException("Agent Runtime v2 terminal publication is unavailable");
    }
}
