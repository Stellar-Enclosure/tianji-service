package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.application.run.AgentTerminalEventPublisher;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentTerminalPublisherTest {
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
