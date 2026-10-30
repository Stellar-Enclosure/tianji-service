package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceWritePermit;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryStorageVerifier;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
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

/** A controlled authority and JDBC marker validate the seam; no owner grant is issued by this fixture. */
class MemoryPreferenceWritePermitTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final MemoryOwnerDTO OWNER = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
    private static final MemoryRetentionPolicy POLICY = new MemoryRetentionPolicy("retention", 1, Duration.ofDays(1), Duration.ofDays(30), 100, Set.of(), Set.of(MemorySensitivityEnum.CONFIDENTIAL), false);

    @Test void a_candidate_write_context_cannot_be_extended_retyped_confirmed_or_combined_with_run_authority() {
        var permit = permit(command(false));
        assertDoesNotThrow(() -> context(permit));
        assertThrows(IllegalArgumentException.class, () -> new MemoryOperationContext(ACTOR, NOW.plusSeconds(11), null, permit));
        assertThrows(IllegalArgumentException.class, () -> context(permit(command(true))));
        var other = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "other", "https://idp.example/20");
        assertThrows(IllegalArgumentException.class, () -> new MemoryOperationContext(other, NOW.plusSeconds(5), null, permit));
        var hash = "a".repeat(64); var run = new AgentRunAuthorization.Request(ACTOR, "run", "definition", 1, hash, hash, hash, "policy", false, NOW.plusSeconds(30));
        assertThrows(IllegalArgumentException.class, () -> new MemoryOperationContext(ACTOR, NOW.plusSeconds(5), run, permit));
    }

    @Test void the_exact_command_and_source_database_are_checked_before_authority_or_fact_access() throws Exception {
        var fixture = fixture(); var command = command(false); var permit = permit(command); var context = context(permit);
        when(permit.usesDataSource(fixture.source())).thenReturn(true);
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> fixture.memory().upsert(context, command(true))).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> fixture.memory().get(context, new GetMemoryQuery(OWNER, "research", "candidate"))).code());
        when(permit.usesDataSource(fixture.source())).thenReturn(false);
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> fixture.memory().upsert(context, command)).code());
        verify(fixture.authority(), never()).authorize(any()); verify(fixture.source(), never()).getConnection(); verify(permit, never()).verifyTransaction();
    }

    @Test void the_same_permit_survives_deadline_bounding_and_is_fenced_inside_the_receipt_fact_transaction() throws Exception {
        var fixture = fixture(); var command = command(false); var permit = permit(command); var context = context(permit);
        when(permit.usesDataSource(fixture.source())).thenReturn(true);
        when(fixture.authority().authorize(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertSame(permit, call.getArgument(0, MemoryAuthorization.Request.class).context().preferenceWrite()); return MemoryAuthorization.Decision.ALLOW;
        });
        doAnswer(call -> { assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); assertTrue(TransactionSynchronizationManager.hasResource(fixture.source())); return null; }).when(permit).verifyTransaction();
        var receipt = new MemoryMutationResultDTO(command.commandId(), OWNER, "research", command.memoryId(), 1, MemoryMutationOutcomeEnum.CREATED, MemoryStateEnum.CANDIDATE, NOW);
        try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, ignored) -> {
            when(repository.databaseNow()).thenReturn(NOW); when(repository.receipt(eq(ACTOR), eq("UPSERT"), eq(command.commandId()), anyString())).thenReturn(Optional.of(receipt));
        })) {
            assertEquals(receipt, fixture.memory().upsert(context, command));
            var order = inOrder(permit, repositories.constructed().getFirst()); order.verify(permit).verifyTransaction();
            order.verify(repositories.constructed().getFirst()).receipt(eq(ACTOR), eq("UPSERT"), eq(command.commandId()), anyString());
            verify(fixture.connection()).commit(); verify(fixture.publication(), never()).publish(any());
        }
    }

    @Test void a_stale_or_cancelled_permit_rolls_back_before_receipt_replay_or_any_fact_change() throws Exception {
        var fixture = fixture(); var command = command(false); var permit = permit(command); when(permit.usesDataSource(fixture.source())).thenReturn(true);
        when(fixture.authority().authorize(any())).thenReturn(MemoryAuthorization.Decision.ALLOW);
        doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Preference lease is no longer current")).when(permit).verifyTransaction();
        try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, ignored) -> when(repository.databaseNow()).thenReturn(NOW))) {
            assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class, () -> fixture.memory().upsert(context(permit), command)).code());
            var repository = repositories.constructed().getFirst(); verify(repository, never()).receipt(any(), anyString(), anyString(), anyString());
            verify(repository, never()).insert(any()); verify(repository, never()).replace(any(), anyLong(), anyBoolean()); verify(fixture.connection()).rollback(); verify(fixture.publication(), never()).publish(any());
        }
    }

    private static MemoryPreferenceWritePermit permit(UpsertMemoryCommand command) {
        var permit = mock(MemoryPreferenceWritePermit.class); when(permit.actor()).thenReturn(ACTOR); when(permit.command()).thenReturn(command); when(permit.expiresAt()).thenReturn(NOW.plusSeconds(10)); return permit;
    }
    private static MemoryOperationContext context(MemoryPreferenceWritePermit permit) { return new MemoryOperationContext(ACTOR, NOW.plusSeconds(5), null, permit); }
    private static UpsertMemoryCommand command(boolean confirmed) { return new UpsertMemoryCommand("source-write", OWNER, "research", "candidate", MemoryKindEnum.PREFERENCE, 0, new MemoryContentDTO(null, Map.of("language", "zh-CN")),
            new MemorySourceDTO("source", "a".repeat(64), "run", "event"), new BigDecimal("0.5"), confirmed, MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), null, null); }
    private static Fixture fixture() throws Exception {
        var source = mock(DataSource.class); var connection = mock(Connection.class); when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        var ledger = mock(AgentLedgerService.class); when(ledger.usesDataSource(source)).thenReturn(true);
        var authority = mock(MemoryAuthorization.class); var publication = mock(MemoryIndexPublication.class); when(publication.usesDataSource(source)).thenReturn(true);
        when(publication.binding()).thenReturn(new MemoryIndexPublication.Binding("memory@1#" + "a".repeat(64), 2, "agent_memory_vector_v1"));
        var memory = new PostgresMemoryService(ledger, source, authority, List.of(POLICY), publication, Clock.fixed(NOW, ZoneOffset.UTC));
        try (var verifier = mockConstruction(MemoryStorageVerifier.class)) { memory.verifyStorage(2, "0.8.0"); }
        return new Fixture(source, connection, memory, authority, publication);
    }
    private record Fixture(DataSource source, Connection connection, PostgresMemoryService memory, MemoryAuthorization authority, MemoryIndexPublication publication) { }
}
