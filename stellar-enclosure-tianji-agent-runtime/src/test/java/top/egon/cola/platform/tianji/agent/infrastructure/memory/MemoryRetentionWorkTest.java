package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryStorageVerifier;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryEntryDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryRetentionWorkTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final String ISSUER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20").issuerFingerprint();
    private static final MemoryOwnerDTO OWNER = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
    private static final MemoryRetentionPolicy POLICY = new MemoryRetentionPolicy("retention", 1, Duration.ofDays(1), Duration.ofDays(30), 100,
            Set.of(MemorySensitivityEnum.CONFIDENTIAL), Set.of(MemorySensitivityEnum.CONFIDENTIAL), true);
    private static final MemoryIndexPublication.Binding BINDING = new MemoryIndexPublication.Binding("memory@1#" + "a".repeat(64), 2, "agent_memory_vector_v1");

    @Test void expiry_scrubbing_and_reference_only_delete_intent_commit_together_without_user_authorization_or_embedding() throws Exception {
        var fixture = fixture(); var stored = stored(1);
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); assertTrue(TransactionSynchronizationManager.hasResource(fixture.source()));
            MemoryIndexPublication.Event event = call.getArgument(0); assertEquals(MemoryIndexPublication.Action.DELETE_MEMORY, event.action()); assertEquals(2, event.revision());
            assertEquals(OWNER, event.owner()); assertEquals(ISSUER, event.ownerIssuer()); return null;
        }).when(fixture.publication()).publish(any());
        try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, ignored) -> prepare(repository, stored))) {
            assertEquals(new MemoryRetentionWork.Result(1, 1, 0), fixture.work().apply(fixture.origin(), 10, NOW.plusSeconds(5)));
            var repository = repositories.constructed().getFirst();
            verify(repository).replace(argThat(row -> row.entry().state() == MemoryStateEnum.EXPIRED && row.entry().revision() == 2 && row.entry().content() == null
                    && row.entry().source() == null && !row.sourceActorSha256().equals(stored.sourceActorSha256())), eq(1L), eq(true));
            verify(repository).lockScope(); verify(fixture.connection(), times(2)).commit(); verifyNoInteractions(fixture.authorization());
        }
    }

    @Test void a_newer_revision_or_forget_after_selection_is_not_overwritten() throws Exception {
        var fixture = fixture(); var newer = stored(2);
        try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, ignored) -> prepare(repository, newer))) {
            assertEquals(new MemoryRetentionWork.Result(1, 0, 1), fixture.work().apply(fixture.origin(), 10, NOW.plusSeconds(5)));
            verify(repositories.constructed().getFirst(), never()).replace(any(), anyLong(), anyBoolean()); verify(fixture.publication(), never()).publish(any());
        }
    }

    @Test void publication_failure_rolls_back_that_fact_and_unknown_namespaces_or_outer_transactions_are_rejected() throws Exception {
        var fixture = fixture(); doThrow(new IllegalStateException("controlled outbox failure")).when(fixture.publication()).publish(any());
        try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, ignored) -> prepare(repository, stored(1)))) {
            assertThrows(IllegalStateException.class, () -> fixture.work().apply(fixture.origin(), 10, NOW.plusSeconds(5)));
            verify(fixture.connection()).rollback(); verify(fixture.connection(), times(1)).commit();
        }
        assertThrows(IllegalArgumentException.class, () -> fixture.work().apply(new MemoryRetentionWork.Origin(21, ISSUER), 10, NOW.plusSeconds(5)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> fixture.work().apply(fixture.origin(), 10, NOW.plusSeconds(5))); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verify(fixture.source(), times(2)).getConnection();
    }

    private static void prepare(JdbcMemoryRepository repository, JdbcMemoryRepository.StoredMemory row) {
        when(repository.tenantId()).thenReturn(20L); when(repository.issuer()).thenReturn(ISSUER); when(repository.databaseNow()).thenReturn(NOW);
        when(repository.current("memory", true)).thenReturn(Optional.of(row));
    }
    private static JdbcMemoryRepository.StoredMemory stored(long revision) {
        var content = new MemoryContentDTO("private expired fact", Map.of());
        var entry = new MemoryEntryDTO(OWNER, "research", "memory", MemoryKindEnum.FACT, revision, revision > 1 ? revision - 1 : null, MemoryStateEnum.ACTIVE,
                content, new MemoryJsonCodec().contentSha256(content), new MemorySourceDTO("private reference", "b".repeat(64), null, null), BigDecimal.ONE, true,
                MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), NOW.minusSeconds(60), NOW.minusSeconds(1), NOW.minusSeconds(60), NOW.minusSeconds(30));
        return new JdbcMemoryRepository.StoredMemory(entry, BINDING.modelRef(), "b".repeat(64), "c".repeat(64), "EXPLICIT_CREATE", null);
    }
    private static Fixture fixture() throws Exception {
        var source = mock(DataSource.class); var connection = mock(Connection.class); when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        var statement = mock(PreparedStatement.class); when(connection.prepareStatement(anyString())).thenReturn(statement); var result = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(result); when(result.next()).thenReturn(true, false); when(result.getString("owner_type")).thenReturn("USER"); when(result.getString("owner_id")).thenReturn("alice");
        when(result.getString("scope_id")).thenReturn("research"); when(result.getString("memory_id")).thenReturn("memory"); when(result.getLong("current_revision")).thenReturn(1L);
        when(result.getString("content_sha256")).thenReturn(stored(1).entry().contentSha256()); when(result.getString("retention_policy_ref")).thenReturn(POLICY.reference()); when(result.getTimestamp("expires_at")).thenReturn(Timestamp.from(NOW.minusSeconds(1)));
        var ledger = mock(AgentLedgerService.class); when(ledger.usesDataSource(source)).thenReturn(true); var authorization = mock(MemoryAuthorization.class);
        var publication = mock(MemoryIndexPublication.class); when(publication.usesDataSource(source)).thenReturn(true); when(publication.binding()).thenReturn(BINDING);
        var memory = new PostgresMemoryService(ledger, source, authorization, List.of(POLICY), publication, Clock.fixed(NOW, ZoneOffset.UTC));
        try (var verifier = mockConstruction(MemoryStorageVerifier.class)) { memory.verifyStorage(2, "0.8.0"); }
        var origin = new MemoryRetentionWork.Origin(20, ISSUER);
        return new Fixture(source, connection, authorization, publication, origin, new MemoryRetentionWork(source, memory, publication, List.of(origin), Set.of(POLICY.reference()), Clock.fixed(NOW, ZoneOffset.UTC)));
    }
    private record Fixture(DataSource source, Connection connection, MemoryAuthorization authorization, MemoryIndexPublication publication, MemoryRetentionWork.Origin origin, MemoryRetentionWork work) { }
}
