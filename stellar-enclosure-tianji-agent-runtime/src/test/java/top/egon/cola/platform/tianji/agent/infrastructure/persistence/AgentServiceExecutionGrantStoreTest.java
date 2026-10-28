package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import java.net.URI;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentServiceExecutionGrantStoreTest {
    @Test void schema_check_normalization_preserves_case_sensitive_evidence_literals() {
        String original = "CHECK (((ciphertext IS NULL) OR (ciphertext ~~ 'ENC:v1:%'::text)))";
        assertEquals("CHECKciphertextISNULLORciphertext~~'ENC:v1:%'", AgentStorageVerifier.normalizedCheck(original));
        assertNotEquals(AgentStorageVerifier.normalizedCheck(original),
                AgentStorageVerifier.normalizedCheck(original.replace("ENC", "enc")));
        assertNotEquals(AgentStorageVerifier.normalizedCheck(original),
                AgentStorageVerifier.normalizedCheck(original.replace("v1", "v 1")));
        assertNotEquals(AgentStorageVerifier.normalizedCheck(original),
                AgentStorageVerifier.normalizedCheck(original.replace("v1", "v(1)")));
    }

    @Test void the_store_requires_the_same_data_source_and_its_own_transaction_before_taking_fence_locks() {
        var source = mock(DataSource.class); var other = mock(DataSource.class);
        var ledger = mock(AgentLedgerService.class);
        when(ledger.usesDataSource(source)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> new AgentServiceExecutionGrantStore(other, ledger));
        var store = new AgentServiceExecutionGrantStore(source, ledger);
        var actor = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://idp.example/21");
        var request = new AgentRunAuthorization.Request(actor, "00000000-0000-0000-0000-000000000123", "review", 1,
                "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", false, Instant.parse("2026-10-30T00:00:00Z"));
        var subject = new AgentServiceExecutionGrant.Subject("worker", "process", "platform", "process", "test",
                URI.create("https://api.example/process"));
        var resource = URI.create("https://api.example/tianji");
        var reservation = new AgentServiceExecutionGrant.Reservation(request, resource, subject, 1,
                AgentServiceExecutionGrant.fingerprint(request, resource, subject));
        var ticket = new AgentServiceExecutionGrant.Ticket("8" + "1".repeat(62), "ENC:v1:key:nonce:sealed", request.deadlineAt());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, () -> store.reserve(actor, request.runId(), resource, subject));
            assertThrows(IllegalStateException.class, () -> store.complete(reservation, ticket));
            assertThrows(IllegalStateException.class, () -> store.current(request, resource, subject));
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(source);
    }
}
