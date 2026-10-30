package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryIndexWorkTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final String ISSUER = "a".repeat(64);
    private static final String MODEL = "memory-model@1#" + "b".repeat(64);
    private static final MemoryOwnerDTO OWNER = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
    private static final MemoryIndexPublication.Binding BINDING = new MemoryIndexPublication.Binding(MODEL, 2, "agent_memory_vector_v1");
    private static final MemoryRetentionPolicy POLICY = new MemoryRetentionPolicy("retention", 1, Duration.ofDays(1), Duration.ofDays(30), 100,
            Set.of(MemorySensitivityEnum.CONFIDENTIAL), Set.of(MemorySensitivityEnum.CONFIDENTIAL), true);

    @Test
    void embedding_is_outside_the_fact_transaction_and_only_precomputed_vectors_are_written_inside_it() throws Exception {
        var fixture = fixture(); var row = stored(false); var event = build(row); var manifest = manifest(row, "PENDING");
        when(fixture.index.physical(anyString(), any())).thenReturn(Optional.empty());
        when(fixture.index.compute(anyString())).thenAnswer(call -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return response(); });
        doAnswer(call -> { assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); assertTrue(TransactionSynchronizationManager.hasResource(fixture.source)); return null; })
                .when(fixture.index).addComputed(any(), any());
        try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, context) -> prepare(repository, row, event, manifest))) {
            fixture.work.apply(event, NOW.plusSeconds(10));
            verify(fixture.index).compute(anyString()); verify(fixture.index).addComputed(any(), any());
            assertEquals(2, repositories.constructed().size()); verify(repositories.constructed().getLast()).activate(manifest);
            verify(fixture.connection, times(2)).commit();
        }
    }

    @Test
    void a_forget_committed_while_embedding_was_in_flight_prevents_the_vector_write() throws Exception {
        var fixture = fixture(); var live = stored(false); var forgotten = stored(true); var event = build(live); var manifest = manifest(live, "PENDING");
        var forgottenDuringModel = new AtomicBoolean();
        when(fixture.index.physical(anyString(), any())).thenReturn(Optional.empty());
        when(fixture.index.compute(anyString())).thenAnswer(call -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); forgottenDuringModel.set(true); return response(); });
        try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, context) -> {
            prepare(repository, live, event, manifest);
            when(repository.current(anyString(), anyBoolean())).thenAnswer(call -> Optional.of(forgottenDuringModel.get() ? forgotten : live));
        })) {
            fixture.work.apply(event, NOW.plusSeconds(10));
            verify(fixture.index, never()).addComputed(any(), any());
            verify(fixture.index).deletePhysical(eq(BINDING.vectorTable()), eq(manifest.documentId()), argThat(values -> values.get("ownerId").equals("alice") && values.get("memoryId").equals("memory")));
            verify(repositories.constructed().getLast()).deleted(manifest);
        }
    }

    @Test
    void deletion_continuations_commit_short_chunks_without_embedding() throws Exception {
        var fixture = fixture(); var forgotten = stored(true, 10);
        var event = new MemoryIndexPublication.Event(MemoryIndexPublication.Action.DELETE_MEMORY, 20, ISSUER, OWNER, "research", "memory", 10,
                forgotten.entry().contentSha256(), null, null, NOW);
        var manifests = new java.util.ArrayList<JdbcMemoryRepository.Manifest>();
        for (int revision = 1; revision <= 9; revision++) manifests.add(new JdbcMemoryRepository.Manifest("memory", revision, MODEL,
                MemoryIndexPublication.vectorDocumentId(20, ISSUER, OWNER, "research", "memory", revision, MODEL), BINDING.vectorTable(), 2, forgotten.entry().contentSha256(), "PENDING"));
        var deleted = new AtomicInteger();
        try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, context) -> {
            prepare(repository, forgotten, event, manifests.getFirst());
            when(repository.deletionBatch(eq("memory"), isNull(), eq(9))).thenAnswer(call -> List.copyOf(manifests.subList(deleted.get(), manifests.size())));
            doAnswer(call -> { assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); deleted.incrementAndGet(); return null; }).when(repository).deleted(any());
        })) {
            fixture.work.apply(event, NOW.plusSeconds(10)); assertEquals(9, deleted.get());
            assertEquals(2, repositories.constructed().size()); verify(fixture.connection, times(2)).commit();
            verify(fixture.index, never()).compute(anyString()); verifyNoInteractions(fixture.authority);
        }
    }

    private static void prepare(JdbcMemoryRepository repository, JdbcMemoryRepository.StoredMemory row, MemoryIndexPublication.Event event, JdbcMemoryRepository.Manifest manifest) {
        when(repository.databaseNow()).thenReturn(NOW); when(repository.tenantId()).thenReturn(20L); when(repository.issuer()).thenReturn(ISSUER);
        when(repository.owner()).thenReturn(OWNER); when(repository.scopeId()).thenReturn("research");
        when(repository.reference(anyString(), anyLong())).thenReturn(Optional.of(new JdbcMemoryRepository.RevisionReference(event.revision(), row.entry().state(), event.contentSha256(), MODEL, NOW)));
        when(repository.current(anyString(), anyBoolean())).thenReturn(Optional.of(row));
        when(repository.manifest(anyString(), anyLong(), anyString())).thenReturn(Optional.of(manifest)); when(repository.activate(any())).thenReturn(true);
    }
    private static MemoryIndexPublication.Event build(JdbcMemoryRepository.StoredMemory row) {
        var id = MemoryIndexPublication.vectorDocumentId(20, ISSUER, OWNER, "research", "memory", 1, MODEL);
        return new MemoryIndexPublication.Event(MemoryIndexPublication.Action.BUILD, 20, ISSUER, OWNER, "research", "memory", 1, row.entry().contentSha256(), MODEL, id, NOW);
    }
    private static JdbcMemoryRepository.Manifest manifest(JdbcMemoryRepository.StoredMemory row, String state) {
        return new JdbcMemoryRepository.Manifest("memory", 1, MODEL, build(row).documentId(), BINDING.vectorTable(), 2, row.entry().contentSha256(), state);
    }
    private static JdbcMemoryRepository.StoredMemory stored(boolean forgotten) {
        return stored(forgotten, forgotten ? 2 : 1);
    }
    private static JdbcMemoryRepository.StoredMemory stored(boolean forgotten, long revision) {
        var content = new MemoryContentDTO("private preference", java.util.Map.of()); var codec = new MemoryJsonCodec();
        var entry = new MemoryEntryDTO(OWNER, "research", "memory", MemoryKindEnum.PREFERENCE, revision, forgotten ? revision - 1 : null,
                forgotten ? MemoryStateEnum.FORGOTTEN : MemoryStateEnum.ACTIVE, forgotten ? null : content, codec.contentSha256(content),
                forgotten ? null : new MemorySourceDTO("reference", "c".repeat(64), null, null), BigDecimal.ONE, true, MemorySensitivityEnum.CONFIDENTIAL,
                POLICY.reference(), NOW.minusSeconds(10), NOW.plusSeconds(60), NOW.minusSeconds(10), NOW);
        return new JdbcMemoryRepository.StoredMemory(entry, MODEL, "c".repeat(64), "d".repeat(64), forgotten ? "USER_FORGET" : "EXPLICIT_CREATE", forgotten ? NOW : null);
    }
    @SuppressWarnings("unchecked")
    private static Fixture fixture() throws Exception {
        var source = mock(DataSource.class); var connection = mock(Connection.class); when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        var memory = mock(PostgresMemoryService.class); when(memory.usesDataSource(source)).thenReturn(true); when(memory.indexBinding()).thenReturn(BINDING); when(memory.policy(anyString())).thenReturn(POLICY);
        var index = mock(PgVectorMemoryIndex.class); when(index.usesDataSource(source)).thenReturn(true); when(index.binding()).thenReturn(BINDING);
        doAnswer(call -> ((Callable<?>) call.getArgument(1)).call()).when(index).bounded(any(), any());
        Consumer<MemoryIndexPublication.Event> authority = mock(Consumer.class);
        return new Fixture(source, connection, index, authority, new MemoryIndexWork(source, memory, index, authority, Clock.fixed(NOW, ZoneOffset.UTC)));
    }
    private static EmbeddingResponse response() { var metadata = new EmbeddingResponseMetadata(); metadata.setModel("text-embedding-3-small"); return new EmbeddingResponse(List.of(new Embedding(new float[]{1, 0}, 0)), metadata); }
    private record Fixture(DataSource source, Connection connection, PgVectorMemoryIndex index, Consumer<MemoryIndexPublication.Event> authority, MemoryIndexWork work) { }
}
