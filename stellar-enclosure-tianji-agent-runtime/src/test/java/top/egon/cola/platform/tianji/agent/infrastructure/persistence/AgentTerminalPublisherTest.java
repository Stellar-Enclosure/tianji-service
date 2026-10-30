package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.application.run.AgentTerminalEventPublisher;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentTerminalPublisherTest {
    @Test
    void terminal_snapshot_rejects_ambient_transactions_or_unverified_owners_before_opening_storage() {
        var source = mock(DataSource.class);
        var ledger = new AgentLedgerService(source);
        var owner = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://idp.example/20");
        assertThrows(IllegalArgumentException.class, () -> ledger.terminalReference(new TrustedAgentActor(20, TrustedAgentActor.SubjectType.SERVICE, "worker"),
                new GetAgentRunQuery("run-one"), Duration.ofSeconds(5)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, () -> ledger.terminalReference(owner, new GetAgentRunQuery("run-one"), Duration.ofSeconds(5)));
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(source);
    }
    @Test
    void publication_is_optional_for_legacy_hosts_and_requires_the_exact_ledger_storage_when_selected() {
        var source = mock(DataSource.class);
        var publisher = mock(AgentTerminalEventPublisher.class);
        assertFalse(new AgentLedgerService(source).hasTerminalPublisher(publisher));
        assertThrows(IllegalArgumentException.class, () -> AgentLedgerService.withTerminalEvents(source, publisher));
        when(publisher.usesDataSource(source)).thenReturn(true);
        when(publisher.producerId()).thenReturn("agent-east");
        var ledger = AgentLedgerService.withTerminalEvents(source, publisher);
        assertTrue(ledger.hasTerminalPublisher(publisher));
        assertFalse(ledger.hasTerminalPublisher(mock(AgentTerminalEventPublisher.class)));
        assertTrue(ledger.usesDataSource(source));
        when(publisher.producerId()).thenReturn("invalid producer");
        assertThrows(IllegalArgumentException.class, () -> AgentLedgerService.withTerminalEvents(source, publisher));
        verifyNoInteractions(source);
    }
}
