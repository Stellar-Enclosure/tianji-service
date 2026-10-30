package top.egon.cola.platform.tianji.agent.application.run;

import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;

import javax.sql.DataSource;

/** Synchronous durable publication in the ledger's JDBC transaction. Implementations must not call a network. */
public interface AgentTerminalEventPublisher {
    String producerId();
    boolean usesDataSource(DataSource source);
    void publish(AgentTerminalEvent event);
}
