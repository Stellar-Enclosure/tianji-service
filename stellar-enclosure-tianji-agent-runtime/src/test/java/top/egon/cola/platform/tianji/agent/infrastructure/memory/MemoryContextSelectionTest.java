package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryStorageVerifier;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalQuery;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryContextSelectionTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final MemoryOwnerDTO OWNER = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
    private static final MemoryIndexPublication.Binding BINDING = new MemoryIndexPublication.Binding("memory@1#" + "a".repeat(64), 2, "agent_memory_vector_v1");

    @Test void context_semantic_topk_excludes_disallowed_classifications_and_policies_before_index_readiness_and_search() throws Exception {
        var source = mock(DataSource.class); var connection = mock(Connection.class); when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        var ledger = mock(AgentLedgerService.class); when(ledger.usesDataSource(source)).thenReturn(true);
        var authority = mock(MemoryAuthorization.class); when(authority.authorize(any())).thenReturn(MemoryAuthorization.Decision.ALLOW);
        var publication = mock(MemoryIndexPublication.class); when(publication.usesDataSource(source)).thenReturn(true); when(publication.binding()).thenReturn(BINDING);
        var index = mock(PgVectorMemoryIndex.class); when(index.usesDataSource(source)).thenReturn(true); when(index.binding()).thenReturn(BINDING);
        var selected = policy("selected"); var other = policy("other");
        var memory = new PostgresMemoryService(ledger, source, authority, List.of(selected, other), publication, Clock.fixed(NOW, ZoneOffset.UTC), index);
        try (var verifier = mockConstruction(MemoryStorageVerifier.class)) { memory.verifyStorage(2, "0.8.0"); }
        var context = new MemoryOperationContext(ACTOR, NOW.plusSeconds(5), null); var query = new MemoryRetrievalQuery(OWNER, "research", null, "question", null, 1);
        var id = MemoryIndexPublication.vectorDocumentId(20, ACTOR.issuerFingerprint(), OWNER, "research", "eligible", 1, BINDING.modelRef());
        var rows = List.of(new JdbcMemoryRepository.IndexReadiness("eligible", 1, "b".repeat(64), selected.reference(), MemorySensitivityEnum.CONFIDENTIAL,
                        BINDING.modelRef(), id, "ACTIVE", "b".repeat(64), 2, BINDING.vectorTable(), true),
                new JdbcMemoryRepository.IndexReadiness("excluded-classification", 1, "b".repeat(64), selected.reference(), MemorySensitivityEnum.INTERNAL, BINDING.modelRef(), null, "PENDING", null, null, null, false),
                new JdbcMemoryRepository.IndexReadiness("excluded-policy", 1, "b".repeat(64), other.reference(), MemorySensitivityEnum.CONFIDENTIAL, BINDING.modelRef(), null, "PENDING", null, null, null, false));
        when(index.search(anyLong(), anyString(), any(), anyString(), isNull(), anyString(), anyInt(), anyList(), any(), any())).thenReturn(List.of());
        try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, ignored) -> {
            when(repository.databaseNow()).thenReturn(NOW); when(repository.indexReadiness(any(), isNull(), eq(BINDING))).thenReturn(rows);
        })) {
            assertTrue(memory.retrieveContext(context, query, Set.of(selected.reference())).items().isEmpty());
            verify(index).search(eq(20L), eq(ACTOR.issuerFingerprint()), eq(OWNER), eq("research"), isNull(), eq("question"), eq(1),
                    eq(List.of(new PgVectorMemoryIndex.Allowed(selected.reference(), MemorySensitivityEnum.CONFIDENTIAL))), eq(NOW), any());
            // Ordinary authorized fact retrieval still considers every eligible indexed classification.
            assertThrows(AgentLedgerException.class, () -> memory.retrieve(context, query)); verify(index, times(1)).search(anyLong(), anyString(), any(), anyString(), any(), anyString(), anyInt(), anyList(), any(), any());
        }
    }

    @Test void sql_context_filters_stay_grouped_under_the_owner_and_precede_limit_with_all_values_parameterized() {
        var jdbc = mock(JdbcTemplate.class); var sql = new AtomicReference<String>(); var parameters = new AtomicReference<Object[]>();
        doAnswer(call -> { sql.set(call.getArgument(0)); parameters.set((Object[]) call.getRawArguments()[2]); return List.of(); })
                .when(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<JdbcMemoryRepository.StoredMemory>>any(), any(Object[].class));
        var repository = new JdbcMemoryRepository(jdbc, 20, ACTOR.issuerFingerprint(), OWNER, "research", new MemoryJsonCodec());
        repository.list(NOW, null, null, 2, List.of(new PgVectorMemoryIndex.Allowed("first", MemorySensitivityEnum.CONFIDENTIAL), new PgVectorMemoryIndex.Allowed("second", MemorySensitivityEnum.PUBLIC)));
        String predicate = "AND ((e.retention_policy_ref=? AND e.sensitivity=?) OR (e.retention_policy_ref=? AND e.sensitivity=?))";
        assertTrue(sql.get().contains("WHERE e.tenant_id=? AND e.owner_issuer=? AND e.owner_type=? AND e.owner_id=? AND e.scope_id=?"));
        assertTrue(sql.get().contains(predicate)); assertTrue(sql.get().indexOf(predicate) < sql.get().indexOf("ORDER BY e.memory_id LIMIT ?"));
        assertEquals(List.of(20L, ACTOR.issuerFingerprint(), "USER", "alice", "research"), java.util.Arrays.asList(parameters.get()).subList(0, 5));
        assertEquals(List.of("first", "CONFIDENTIAL", "second", "PUBLIC", 2), java.util.Arrays.asList(parameters.get()).subList(parameters.get().length - 5, parameters.get().length));
        assertFalse(sql.get().contains("'first'"));
    }

    private static MemoryRetentionPolicy policy(String key) { return new MemoryRetentionPolicy(key, 1, Duration.ofDays(1), Duration.ofDays(30), 100,
            Set.of(MemorySensitivityEnum.CONFIDENTIAL, MemorySensitivityEnum.INTERNAL), Set.of(MemorySensitivityEnum.CONFIDENTIAL), true); }
}
