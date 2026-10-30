package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryStorageVerifier;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalQuery;
import java.sql.Connection;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostgresMemoryAuthorizationTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");

    @Test
    void unverified_storage_and_cross_user_or_principal_type_intentions_never_access_fact_storage() {
        var source = mock(DataSource.class); var authorization = mock(MemoryAuthorization.class); var service = service(source, authorization);
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> service.get(context(ACTOR), query(MemoryOwnerTypeEnum.USER, "alice"))).code());
        initialize(service);
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> service.get(context(ACTOR), query(MemoryOwnerTypeEnum.USER, "bob"))).code());
        var machine = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "alice", "https://idp.example/20");
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> service.get(context(machine), query(MemoryOwnerTypeEnum.USER, "alice"))).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> service.get(context(ACTOR), query(MemoryOwnerTypeEnum.APPLICATION, "alice"))).code());
        verifyNoInteractions(source, authorization);
    }

    @Test
    void a_team_owner_id_never_grants_membership_and_denial_precedes_jdbc() {
        var source = mock(DataSource.class); var authorization = mock(MemoryAuthorization.class); when(authorization.authorize(any())).thenReturn(MemoryAuthorization.Decision.DENY);
        var service = service(source, authorization); initialize(service);
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> service.get(context(ACTOR), query(MemoryOwnerTypeEnum.TEAM, "team-one"))).code());
        verify(authorization).authorize(argThat(value -> value.context().actor().equals(ACTOR) && value.owner().type() == MemoryOwnerTypeEnum.TEAM
                && value.owner().id().equals("team-one") && value.scopeId().equals("research") && value.operation() == MemoryAuthorization.Operation.GET));
        verifyNoInteractions(source);
    }

    @Test
    void null_unavailable_and_unexpected_authority_failures_are_sanitized_without_ledger_reads() {
        var source = mock(DataSource.class); var authorization = mock(MemoryAuthorization.class); var service = service(source, authorization); initialize(service);
        when(authorization.authorize(any())).thenReturn(null, MemoryAuthorization.Decision.UNAVAILABLE).thenThrow(new IllegalStateException("private authority detail"));
        for (int i = 0; i < 3; i++) {
            var error = assertThrows(AgentLedgerException.class, () -> service.get(context(ACTOR), query(MemoryOwnerTypeEnum.USER, "alice")));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, error.code()); assertNull(error.getCause()); assertFalse(error.toString().contains("private authority detail"));
        }
        verifyNoInteractions(source);
    }

    @Test
    void ambient_transactions_are_rejected_before_current_authority_or_storage() {
        var source = mock(DataSource.class); var authorization = mock(MemoryAuthorization.class); var service = service(source, authorization); initialize(service);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> service.get(context(ACTOR), query(MemoryOwnerTypeEnum.USER, "alice"))); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(source, authorization);
    }

    @Test
    void semantic_results_require_a_second_current_authorization_after_the_model_call() throws Exception {
        for (var result : MemoryAuthorization.Decision.values()) {
            var source = mock(DataSource.class); var connection = mock(Connection.class); when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
            var authorization = mock(MemoryAuthorization.class); when(authorization.authorize(any())).thenReturn(MemoryAuthorization.Decision.ALLOW, result);
            var index = mock(PgVectorMemoryIndex.class); when(index.usesDataSource(source)).thenReturn(true);
            when(index.binding()).thenReturn(new MemoryIndexPublication.Binding("memory-model@1#" + "a".repeat(64), 2, "agent_memory_vector_v1"));
            when(index.search(anyLong(), anyString(), any(), anyString(), isNull(), anyString(), anyInt(), anyList(), any(), any())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return List.of();
            });
            var service = service(source, authorization, index); initialize(service);
            try (var repositories = mockConstruction(JdbcMemoryRepository.class, (repository, ignored) -> {
                when(repository.databaseNow()).thenReturn(NOW); when(repository.indexReadiness(any(), isNull(), any())).thenReturn(List.of());
            })) {
                var query = new MemoryRetrievalQuery(new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"), "research", null, "current preference", null, 5);
                if (result == MemoryAuthorization.Decision.ALLOW) { assertTrue(service.retrieve(context(ACTOR), query).items().isEmpty()); assertEquals(2, repositories.constructed().size()); }
                else {
                    var failure = assertThrows(AgentLedgerException.class, () -> service.retrieve(context(ACTOR), query));
                    assertEquals(result == MemoryAuthorization.Decision.DENY ? AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED : AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code());
                    assertEquals(1, repositories.constructed().size());
                }
                verify(authorization, times(2)).authorize(argThat(value -> value.operation() == MemoryAuthorization.Operation.SEARCH));
            }
        }
    }

    private static void initialize(PostgresMemoryService service) {
        // This controlled verifier proves only the service boundary; it is not schema or database acceptance.
        try (var verifiers = mockConstruction(MemoryStorageVerifier.class)) { service.verifyStorage(2, "0.8.0"); assertEquals(1, verifiers.constructed().size()); }
    }
    private static GetMemoryQuery query(MemoryOwnerTypeEnum type, String owner) { return new GetMemoryQuery(new MemoryOwnerDTO(type, owner), "research", "memory"); }
    private static MemoryOperationContext context(TrustedAgentActor actor) { return new MemoryOperationContext(actor, NOW.plusSeconds(5), null); }
    private static PostgresMemoryService service(DataSource source, MemoryAuthorization authorization) { return service(source, authorization, null); }
    private static PostgresMemoryService service(DataSource source, MemoryAuthorization authorization, PgVectorMemoryIndex index) {
        var ledger = mock(AgentLedgerService.class); when(ledger.usesDataSource(source)).thenReturn(true);
        var publication = mock(MemoryIndexPublication.class); when(publication.usesDataSource(source)).thenReturn(true);
        when(publication.binding()).thenReturn(new MemoryIndexPublication.Binding("memory-model@1#" + "a".repeat(64), 2, "agent_memory_vector_v1"));
        var policy = new MemoryRetentionPolicy("retention", 1, Duration.ofDays(1), Duration.ofDays(30), 100,
                Set.of(MemorySensitivityEnum.CONFIDENTIAL), Set.of(MemorySensitivityEnum.CONFIDENTIAL), true);
        return new PostgresMemoryService(ledger, source, authorization, List.of(policy), publication, Clock.fixed(NOW, ZoneOffset.UTC), index);
    }
}
