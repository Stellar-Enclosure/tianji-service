package top.egon.cola.platform.tianji.agent.server;

import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentModelUsageCorrelationQuery;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentModelUsagePageDTO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentModelUsageRunDTO;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import java.util.Objects;

final class AgentModelUsageDiagnostics {
    private final AgentLedgerService ledger;
    private final TianjiAgentAccess access;

    AgentModelUsageDiagnostics(AgentLedgerService ledger, TianjiAgentAccess access) {
        this.ledger = Objects.requireNonNull(ledger);
        this.access = Objects.requireNonNull(access);
    }

    AgentModelUsageRunDTO byRun(GetAgentRunQuery query, String afterReservationId) {
        return ledger.getModelUsage(access.require(TianjiAgentPermissions.GET_API).actor(), query, afterReservationId);
    }

    AgentModelUsagePageDTO byCorrelation(AgentModelUsageCorrelationQuery query) {
        return ledger.listModelUsageByCorrelation(access.require(TianjiAgentPermissions.LIST_API).actor(), query);
    }
}
