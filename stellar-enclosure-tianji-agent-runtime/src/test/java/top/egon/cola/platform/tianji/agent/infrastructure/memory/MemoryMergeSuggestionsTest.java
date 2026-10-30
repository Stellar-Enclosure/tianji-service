package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryStorageVerifier;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryEntryDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMergeQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryMergeRelationEnum;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Controlled JDBC/repository markers exercise the real orchestration, not a provisioned database or model provider. */
class MemoryMergeSuggestionsTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final MemoryOwnerDTO OWNER = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
    private static final MemoryIndexPublication.Binding BINDING = new MemoryIndexPublication.Binding("memory@1#" + "a".repeat(64), 2, "agent_memory_vector_v1");

    @Test void factual_comparison_uses_current_reads_only_and_never_drops_matching_targets_at_a_page_boundary() throws Exception {
        var fixture = fixture(true); var candidate = entry("candidate", false, Map.of("language", "zh-CN"), fixture.policy());
        var rows = List.of(stored(entry("a", true, Map.of("language", "zh-CN", "format", "short"), fixture.policy())),
                stored(entry("b", true, Map.of("language", "en-US"), fixture.policy())), stored(entry("c", true, Map.of("timezone", "UTC"), fixture.policy())));
        try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, ignored) -> {
            when(repository.databaseNow()).thenReturn(NOW); when(repository.current(eq("candidate"), eq(false))).thenReturn(Optional.of(stored(candidate)));
            when(repository.list(eq(NOW), eq(MemoryKindEnum.PREFERENCE), nullable(String.class), eq(51))).thenAnswer(call -> rows.stream().filter(row -> call.getArgument(2) == null || row.entry().memoryId().compareTo(call.getArgument(2)) > 0).toList());
        })) {
            var first = fixture.memory().mergeSuggestions(context(), query(false, null, 1));
            assertEquals("a", first.items().getFirst().hit().entry().memoryId()); assertEquals(MemoryMergeRelationEnum.SHARED_VALUES, first.items().getFirst().relation()); assertEquals("a", first.nextAfterMemoryId());
            var second = fixture.memory().mergeSuggestions(context(), query(false, first.nextAfterMemoryId(), 1));
            assertEquals("b", second.items().getFirst().hit().entry().memoryId()); assertEquals(MemoryMergeRelationEnum.DIFFERING_FIELDS, second.items().getFirst().relation()); assertNull(second.nextAfterMemoryId());
            verify(fixture.index(), never()).search(anyLong(), anyString(), any(), anyString(), any(), anyString(), anyInt(), anyList(), any(), any());
            verify(fixture.authority(), times(4)).authorize(argThat(request -> request.operation() == MemoryAuthorization.Operation.GET));
            verify(fixture.authority(), times(2)).authorize(argThat(request -> request.operation() == MemoryAuthorization.Operation.RETRIEVE));
            for (var repository : repositories.constructed()) { verify(repository, never()).replace(any(), anyLong(), anyBoolean()); verify(repository, never()).insert(any()); }
            verify(fixture.publication(), never()).publish(any());
        }
    }

    @Test void a_nonmatching_scan_still_returns_a_continuation_instead_of_claiming_the_scope_was_exhausted() throws Exception {
        var fixture = fixture(true); var candidate = entry("candidate", false, Map.of("language", "zh-CN"), fixture.policy());
        var rows = new ArrayList<JdbcMemoryRepository.StoredMemory>();
        for (int index = 0; index < 51; index++) rows.add(stored(entry("item-%03d".formatted(index), true, Map.of("timezone", "UTC"), fixture.policy())));
        try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, ignored) -> {
            when(repository.databaseNow()).thenReturn(NOW); when(repository.current(eq("candidate"), eq(false))).thenReturn(Optional.of(stored(candidate)));
            when(repository.list(eq(NOW), eq(MemoryKindEnum.PREFERENCE), isNull(), eq(51))).thenReturn(rows);
        })) {
            var page = fixture.memory().mergeSuggestions(context(), query(false, null, 5)); assertTrue(page.items().isEmpty()); assertEquals("item-049", page.nextAfterMemoryId());
        }
    }

    @Test void candidate_semantic_egress_requires_its_own_policy_and_a_bounded_complete_body_before_index_access() throws Exception {
        for (boolean approved : List.of(false, true)) {
            var fixture = fixture(approved); var content = approved ? new MemoryContentDTO("x".repeat(2100), Map.of()) : new MemoryContentDTO(null, Map.of("language", "zh-CN"));
            var candidate = entry("candidate", false, content, fixture.policy());
            try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, ignored) -> {
                when(repository.databaseNow()).thenReturn(NOW); when(repository.current(eq("candidate"), eq(false))).thenReturn(Optional.of(stored(candidate)));
            })) {
                var error = assertThrows(AgentLedgerException.class, () -> fixture.memory().mergeSuggestions(context(), query(true, null, 5)));
                assertEquals(approved ? AgentErrorCodeEnum.TIANJI_REQUEST_TOO_LARGE : AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, error.code());
                verify(fixture.index(), never()).search(anyLong(), anyString(), any(), anyString(), any(), anyString(), anyInt(), anyList(), any(), any());
            }
        }
    }

    @Test void forgetting_the_candidate_while_semantic_work_runs_invalidates_the_whole_comparison() throws Exception {
        var fixture = fixture(true); var changed = new AtomicBoolean(); var candidate = entry("candidate", false, Map.of("language", "zh-CN"), fixture.policy());
        var forgotten = new MemoryEntryDTO(OWNER, "review", "candidate", MemoryKindEnum.PREFERENCE, 2, 1L, MemoryStateEnum.FORGOTTEN, null, candidate.contentSha256(), null,
                candidate.confidence(), false, candidate.sensitivity(), candidate.retentionPolicyRef(), candidate.effectiveAt(), candidate.expiresAt(), candidate.createdAt(), NOW);
        when(fixture.index().search(anyLong(), anyString(), any(), anyString(), any(), anyString(), anyInt(), anyList(), any(), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); assertEquals(new MemoryJsonCodec().content(candidate.content()), call.getArgument(5)); changed.set(true); return List.of();
        });
        try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, ignored) -> {
            when(repository.databaseNow()).thenReturn(NOW); when(repository.indexReadiness(any(), any(), any())).thenReturn(List.of());
            when(repository.current(eq("candidate"), eq(false))).thenAnswer(call -> Optional.of(stored(changed.get() ? forgotten : candidate)));
        })) {
            assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, assertThrows(AgentLedgerException.class, () -> fixture.memory().mergeSuggestions(context(), query(true, null, 5))).code());
            verify(fixture.authority(), times(2)).authorize(argThat(request -> request.operation() == MemoryAuthorization.Operation.GET));
            verify(fixture.publication(), never()).publish(any());
        }
    }

    @Test void current_source_authority_is_rechecked_after_the_target_read() throws Exception {
        var fixture = fixture(true); var getCalls = new AtomicInteger(); var candidate = entry("candidate", false, Map.of("language", "zh-CN"), fixture.policy());
        when(fixture.authority().authorize(any())).thenAnswer(call -> {
            var request = call.getArgument(0, MemoryAuthorization.Request.class);
            return request.operation() == MemoryAuthorization.Operation.GET && getCalls.incrementAndGet() > 1 ? MemoryAuthorization.Decision.DENY : MemoryAuthorization.Decision.ALLOW;
        });
        try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, ignored) -> {
            when(repository.databaseNow()).thenReturn(NOW); when(repository.current(eq("candidate"), eq(false))).thenReturn(Optional.of(stored(candidate)));
            when(repository.list(eq(NOW), eq(MemoryKindEnum.PREFERENCE), isNull(), eq(51))).thenReturn(List.of());
        })) {
            assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> fixture.memory().mergeSuggestions(context(), query(false, null, 5))).code());
        }
    }

    @Test void semantic_scores_remain_ranking_data_and_only_current_verified_active_fact_rows_are_compared() throws Exception {
        var fixture = fixture(true); var candidate = entry("candidate", false, Map.of("language", "zh-CN"), fixture.policy());
        var rows = List.of(stored(entry("a", true, Map.of("language", "zh-CN", "format", "short"), fixture.policy())), stored(entry("b", true, Map.of("timezone", "UTC"), fixture.policy())));
        var readiness = new ArrayList<JdbcMemoryRepository.IndexReadiness>(); var documents = new ArrayList<Document>();
        for (var row : rows) {
            var entry = row.entry(); var id = MemoryIndexPublication.vectorDocumentId(20, ACTOR.issuerFingerprint(), OWNER, "review", entry.memoryId(), 1, BINDING.modelRef());
            readiness.add(new JdbcMemoryRepository.IndexReadiness(entry.memoryId(), 1, entry.contentSha256(), fixture.policy().reference(), MemorySensitivityEnum.CONFIDENTIAL,
                    BINDING.modelRef(), id, "ACTIVE", entry.contentSha256(), 2, BINDING.vectorTable(), true));
            documents.add(Document.builder().id(id.toString()).text("vector text is not returned as a fact")
                    .metadata(PgVectorMemoryIndex.metadata(20, ACTOR.issuerFingerprint(), row, BINDING)).score(entry.memoryId().equals("a") ? 0.9 : 0.4).build());
        }
        when(fixture.index().search(anyLong(), anyString(), any(), anyString(), any(), anyString(), anyInt(), anyList(), any(), any())).thenReturn(documents);
        try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, ignored) -> {
            when(repository.databaseNow()).thenReturn(NOW); when(repository.indexReadiness(any(), any(), any())).thenReturn(readiness);
            when(repository.current(anyString(), eq(false))).thenAnswer(call -> call.getArgument(0).equals("candidate") ? Optional.of(stored(candidate))
                    : rows.stream().filter(row -> row.entry().memoryId().equals(call.getArgument(0))).findFirst());
        })) {
            var page = fixture.memory().mergeSuggestions(context(), query(true, null, 5)); assertEquals(1, page.items().size());
            var hit = page.items().getFirst(); assertEquals(0.9, hit.hit().score()); assertEquals(new BigDecimal("0.5"), hit.hit().entry().confidence());
            assertEquals(MemoryMergeRelationEnum.SHARED_VALUES, hit.relation()); assertNull(page.nextAfterMemoryId()); verify(fixture.publication(), never()).publish(any());
        }
    }

    @Test void background_run_authority_and_ambient_transactions_cannot_open_a_candidate_comparison() throws Exception {
        var fixture = fixture(true); var hash = "a".repeat(64);
        var run = new AgentRunAuthorization.Request(ACTOR, "run", "definition", 1, hash, hash, hash, "policy", false, NOW.plusSeconds(60));
        assertThrows(AgentLedgerException.class, () -> fixture.memory().mergeSuggestions(new MemoryOperationContext(ACTOR, NOW.plusSeconds(30), run), query(false, null, 5)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> fixture.memory().mergeSuggestions(context(), query(false, null, 5))); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verify(fixture.authority(), never()).authorize(any()); verify(fixture.publication(), never()).publish(any());
    }

    private static Fixture fixture(boolean semantic) throws Exception {
        var source = mock(DataSource.class); var connection = mock(Connection.class); when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        var ledger = mock(AgentLedgerService.class); when(ledger.usesDataSource(source)).thenReturn(true);
        var publication = mock(MemoryIndexPublication.class); when(publication.usesDataSource(source)).thenReturn(true); when(publication.binding()).thenReturn(BINDING);
        var authority = mock(MemoryAuthorization.class); when(authority.authorize(any())).thenReturn(MemoryAuthorization.Decision.ALLOW);
        var index = mock(PgVectorMemoryIndex.class); when(index.usesDataSource(source)).thenReturn(true); when(index.binding()).thenReturn(BINDING);
        var policy = new MemoryRetentionPolicy("retention", 1, Duration.ofDays(1), Duration.ofDays(30), 100, semantic ? Set.of(MemorySensitivityEnum.CONFIDENTIAL) : Set.of(), Set.of(MemorySensitivityEnum.CONFIDENTIAL), semantic);
        var memory = new PostgresMemoryService(ledger, source, authority, List.of(policy), publication, Clock.fixed(NOW, ZoneOffset.UTC), index);
        try (var verifier = mockConstruction(MemoryStorageVerifier.class)) { memory.verifyStorage(2, "0.8.0"); }
        return new Fixture(memory, authority, publication, index, policy);
    }
    private record Fixture(PostgresMemoryService memory, MemoryAuthorization authority, MemoryIndexPublication publication, PgVectorMemoryIndex index, MemoryRetentionPolicy policy) { }
    private static MemoryMergeQuery query(boolean semantic, String cursor, int limit) { return new MemoryMergeQuery(OWNER, "review", "candidate", 1, semantic, cursor, limit, semantic ? 0.5 : null); }
    private static MemoryOperationContext context() { return new MemoryOperationContext(ACTOR, NOW.plusSeconds(30), null); }
    private static MemoryEntryDTO entry(String id, boolean confirmed, Map<String, String> facts, MemoryRetentionPolicy policy) { return entry(id, confirmed, new MemoryContentDTO(null, facts), policy); }
    private static MemoryEntryDTO entry(String id, boolean confirmed, MemoryContentDTO content, MemoryRetentionPolicy policy) {
        return new MemoryEntryDTO(OWNER, "review", id, MemoryKindEnum.PREFERENCE, 1, null, confirmed ? MemoryStateEnum.ACTIVE : MemoryStateEnum.CANDIDATE, content,
                new MemoryJsonCodec().contentSha256(content), new MemorySourceDTO("declared", "b".repeat(64), null, null), new BigDecimal("0.5"), confirmed,
                MemorySensitivityEnum.CONFIDENTIAL, policy.reference(), NOW, NOW.plusSeconds(60), NOW, NOW);
    }
    private static JdbcMemoryRepository.StoredMemory stored(MemoryEntryDTO entry) { return new JdbcMemoryRepository.StoredMemory(entry, BINDING.modelRef(), "b".repeat(64), ACTOR.identityFingerprint(), "EXPLICIT_CREATE", null); }
}
