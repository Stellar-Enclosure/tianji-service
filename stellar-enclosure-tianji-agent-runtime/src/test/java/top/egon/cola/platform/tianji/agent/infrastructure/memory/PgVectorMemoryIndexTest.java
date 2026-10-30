package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.pgvector.PgVectorFilterExpressionConverter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PgVectorMemoryIndexTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @SuppressWarnings("unchecked")
    void the_real_vectorstore_batch_contract_uses_only_a_precomputed_model_in_the_selected_transaction() throws Exception {
        var source = mock(DataSource.class); var connection = mock(java.sql.Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        var delegate = mock(EmbeddingModel.class); var model = model(delegate);
        var metadata = new EmbeddingResponseMetadata(); metadata.setModel("text-embedding-3-small");
        when(delegate.call(any(EmbeddingRequest.class))).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return new EmbeddingResponse(List.of(new Embedding(new float[]{1, 0}, 0)), metadata);
        });
        var binding = new MemoryIndexPublication.Binding(model.descriptor().modelRef(), 2, "agent_memory_vector_v1");
        try (var stores = mockConstruction(PgVectorStore.class, (store, context) -> {
            if (context.getCount() == 2) {
                var selected = ((PgVectorStore.PgVectorStoreBuilder) context.arguments().getFirst()).getEmbeddingModel();
                doAnswer(call -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    var documents = (List<Document>) call.getArgument(0);
                    var vector = selected.embed(documents, EmbeddingOptions.builder().build(), items -> List.of(items)).getFirst();
                    assertArrayEquals(new float[]{1, 0}, vector); return null;
                }).when(store).add(anyList());
            }
        }); var index = new PgVectorMemoryIndex(source, binding, model, 1, CLOCK)) {
            index.verifiedStorage(); var computed = index.compute("private fact");
            new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status -> index.addComputed(new Document("private fact", Map.of("ownerId", "alice")), computed));
            assertEquals(2, stores.constructed().size()); verify(delegate, times(1)).call(any(EmbeddingRequest.class)); verify(connection).commit();
        }
    }

    @Test
    void actual_store_construction_neither_opens_a_database_nor_infers_model_dimensions() {
        var source = mock(DataSource.class); var delegate = mock(EmbeddingModel.class); var model = model(delegate);
        var binding = new MemoryIndexPublication.Binding(model.descriptor().modelRef(), 2, "agent_memory_vector_v1");
        try (var index = new PgVectorMemoryIndex(source, binding, model, 1, CLOCK)) {
            assertTrue(index.usesDataSource(source));
            assertThrows(AgentLedgerException.class, () -> index.bounded(NOW.plusSeconds(1), () -> true));
            verifyNoInteractions(source, delegate);
        }
    }

    @Test
    void policy_alternatives_are_grouped_under_the_mandatory_owner_and_model_filters() {
        var source = mock(DataSource.class); var delegate = mock(EmbeddingModel.class); var model = model(delegate);
        var binding = new MemoryIndexPublication.Binding(model.descriptor().modelRef(), 2, "agent_memory_vector_v1");
        try (var stores = mockConstruction(PgVectorStore.class);
             var index = new PgVectorMemoryIndex(source, binding, model, 1, CLOCK)) {
            assertEquals(1, stores.constructed().size()); index.verifiedStorage();
            var reader = stores.constructed().getFirst(); when(reader.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
            var policies = List.of(new PgVectorMemoryIndex.Allowed("retention@1#" + "a".repeat(64), MemorySensitivityEnum.CONFIDENTIAL),
                    new PgVectorMemoryIndex.Allowed("retention@2#" + "b".repeat(64), MemorySensitivityEnum.PUBLIC));
            assertTrue(index.search(20, "c".repeat(64), new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice'quoted"), "research", MemoryKindEnum.PREFERENCE,
                    "private query", 5, policies, NOW, NOW.plusSeconds(1)).isEmpty());
            var request = org.mockito.ArgumentCaptor.forClass(SearchRequest.class); verify(reader).similaritySearch(request.capture());
            assertEquals(Filter.ExpressionType.AND, request.getValue().getFilterExpression().type());
            assertInstanceOf(Filter.Group.class, request.getValue().getFilterExpression().right());
            var group = (Filter.Group) request.getValue().getFilterExpression().right(); assertEquals(Filter.ExpressionType.OR, group.content().type());
            String filter = new PgVectorFilterExpressionConverter().convertExpression(request.getValue().getFilterExpression());
            assertTrue(filter.contains("alice''quoted"), filter);
            for (String required : List.of("tenantId", "issuerFingerprint", "ownerType", "ownerId", "scopeId", "modelRef", "kind", "effectiveAtMillis", "expiresAtMillis", "retentionPolicyRef", "sensitivity")) assertTrue(filter.contains(required), filter);
            assertTrue(filter.contains(" && ("), filter);
            verify(stores.constructed().getFirst(), never()).add(anyList()); verifyNoInteractions(source, delegate);
        }
    }

    @Test
    void vector_faults_never_become_an_empty_result_and_revoked_schema_readiness_stops_admission() {
        var source = mock(DataSource.class); var model = model(mock(EmbeddingModel.class));
        var binding = new MemoryIndexPublication.Binding(model.descriptor().modelRef(), 2, "agent_memory_vector_v1");
        try (var stores = mockConstruction(PgVectorStore.class); var index = new PgVectorMemoryIndex(source, binding, model, 1, CLOCK)) {
            index.verifiedStorage(); when(stores.constructed().getFirst().similaritySearch(any(SearchRequest.class))).thenThrow(new IllegalStateException("private vector detail"));
            var error = assertThrows(AgentLedgerException.class, () -> index.search(20, "c".repeat(64), new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"), "research", null,
                    "query", 5, List.of(new PgVectorMemoryIndex.Allowed("retention", MemorySensitivityEnum.CONFIDENTIAL)), NOW, NOW.plusSeconds(1)));
            assertFalse(error.toString().contains("private vector detail")); assertNull(error.getCause());
            index.unverifiedStorage(); assertThrows(AgentLedgerException.class, () -> index.bounded(NOW.plusSeconds(1), () -> true));
        }
    }

    private static VersionedMemoryEmbeddingModel model(EmbeddingModel delegate) {
        return new VersionedMemoryEmbeddingModel(new VersionedMemoryEmbeddingModel.Profile("memory-model", 1, "embeddingBean", "text-embedding-3-small", 2, 8191, 64, 16), delegate,
                () -> EmbeddingOptions.builder().model("text-embedding-3-small").dimensions(2).build());
    }
}
