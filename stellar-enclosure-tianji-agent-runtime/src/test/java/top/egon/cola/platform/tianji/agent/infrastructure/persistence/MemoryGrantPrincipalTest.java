package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryReadGrant;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;

import javax.sql.DataSource;
import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryGrantPrincipalTest {
    @Test void service_evidence_requires_the_new_schema_and_cannot_be_retyped_as_a_user_ticket() {
        var source = mock(DataSource.class); var ledger = mock(AgentLedgerService.class); when(ledger.usesDataSource(source)).thenReturn(true);
        var store = new MemoryReadGrantStore(source, ledger); var resource = URI.create("https://agent.example");
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "client", "https://idp.example/20");
        var run = new AgentRunAuthorization.Request(actor, "00000000-0000-0000-0000-000000000001", "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy", false, Instant.now().plusSeconds(60));
        var scope = new MemoryReadGrant.Scope(new MemoryOwnerDTO(MemoryOwnerTypeEnum.APPLICATION, "client"), "research", "d".repeat(64),
                new AgentServiceExecutionGrant.Subject("client", "application", "business", "source", "test", resource));
        var reservation = new MemoryReadGrant.Reservation(run, resource, scope, 1, MemoryReadGrant.intentId(run, scope), MemoryReadGrant.fingerprint(run, resource, scope));
        assertThrows(AgentLedgerException.class, () -> store.reserve(run, resource, scope));
        assertThrows(AgentLedgerException.class, () -> store.currentEvidence(run, resource, scope));
        assertThrows(IllegalArgumentException.class, () -> store.current(run, resource, scope));
        var userTicket = new AgentExecutionGrant.Ticket("9" + "1".repeat(62), "ENC:v1:key:nonce:sealed", Instant.now().plusSeconds(30));
        assertThrows(IllegalArgumentException.class, () -> store.complete(reservation, userTicket));
        assertThrows(IllegalArgumentException.class, () -> store.completeEvidence(reservation, new MemoryReadGrant.Ticket(userTicket.grantId(), userTicket.ciphertext(), userTicket.expiresAt())));
        verifyNoInteractions(source);
    }
}
