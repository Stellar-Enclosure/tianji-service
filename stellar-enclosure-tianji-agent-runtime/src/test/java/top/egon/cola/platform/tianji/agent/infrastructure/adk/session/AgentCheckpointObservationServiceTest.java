package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointObservationService.Observation.*;

class AgentCheckpointObservationServiceTest {
    private static final TrustedAgentActor OWNER =
            new TrustedAgentActor(40001, TrustedAgentActor.SubjectType.USER, "fixture", "a".repeat(64));
    private static final GetAgentRunQuery QUERY = new GetAgentRunQuery("fixture-run");
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private static final Instant DEADLINE = NOW.plusSeconds(90);
    private static final String INPUT = "fixture input";

    @Test
    void observes_only_a_bounded_exact_owner_snapshot_in_both_codecs() throws Exception {
        for (boolean typed : List.of(false, true)) {
            try (var fixture = new Fixture(typed)) {
                assertEquals(BOUNDARY_OBSERVED, fixture.service.observe(OWNER, QUERY));
                verify(fixture.verifier).verifyCheckpointObservation();
                verify(fixture.verifier).checkpointObservationRoleReadable();
                verify(fixture.verifier).checkpointToolEvidenceReadable();
                verify(fixture.ledger).executionMaterial(OWNER, QUERY);
                verify(fixture.connection).setReadOnly(true);
                verify(fixture.connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                verify(fixture.statement).executeUpdate("SET TRANSACTION READ ONLY");
                assertTrue(fixture.observationSql.contains("r.lease_epoch::numeric=c.run_lease_epoch::numeric+1"));
                assertTrue(fixture.observationSql.contains("r.revision::numeric=c.run_revision::numeric+1"));
                assertTrue(fixture.observationSql.contains("r.last_event_sequence::numeric=c.run_event_sequence::numeric+1"));
                assertTrue(fixture.observationSql.contains("ledger_event.event_type='RUN_STATUS_CHANGED'"));
                assertTrue(fixture.observationSql.contains("later.sequence>r.last_event_sequence"));
                assertTrue(fixture.observationSql.contains("(other.attempt<>1 OR other.checkpoint_no<>1)"));
                assertTrue(fixture.observationSql.contains("r.cancel_requested_at IS NULL"));
                assertTrue(fixture.observationSql.contains("r.model_calls_reserved=0 AND r.tool_calls_reserved=0"));
                assertTrue(fixture.observationSql.contains("FROM ONLY tianji_agent.agent_budget_reservation"));
                assertTrue(fixture.observationSql.contains("sl.lease_epoch=c.session_lease_epoch"));
                assertTrue(fixture.observationSql.contains("extra.sequence<>c.session_event_sequence"));
                assertTrue(fixture.observationSql.contains("native_event.native_event_id=c.native_event_id"));
                assertTrue(fixture.observationSql.contains("scope_state.subject_issuer=c.subject_issuer"));
                assertTrue(fixture.observationSql.contains("LIMIT 2"));
                assertTrue(fixture.toolSql.contains("FROM ONLY tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=?"));
                assertTrue(fixture.toolSql.contains("FROM ONLY tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND origin_run_id=?"));
                assertTrue(fixture.toolSql.contains("NOT EXISTS"));
                assertFalse(fixture.observationSql.contains("FOR UPDATE"));
                assertFalse(fixture.observationSql.contains("FOR SHARE"));
            }
        }
    }

    @Test
    void unreadable_v13_never_observes_boundary() throws Exception {
        try (var fixture = new Fixture(false)) {
            when(fixture.verifier.checkpointToolEvidenceReadable()).thenReturn(false);
            assertEquals(TOOL_EVIDENCE_UNAVAILABLE, fixture.service.observe(OWNER, QUERY));
            assertNull(fixture.toolSql);
        }
    }

    @Test
    void refuses_missing_checkpoint_and_fence_or_budget_progress() throws Exception {
        for (var condition : Map.of("checkpoint_present", NO_MATCHING_CHECKPOINT, "run_fence", NOT_AT_CAPTURE_FENCE,
                "session_boundary", NOT_AT_CAPTURE_FENCE, "no_budget", PROGRESS_OBSERVED).entrySet()) {
            try (var fixture = new Fixture(false)) {
                fixture.row.put(condition.getKey(), false);
                assertEquals(condition.getValue(), fixture.service.observe(OWNER, QUERY));
                verify(fixture.verifier, never()).checkpointToolEvidenceReadable();
            }
        }
        try (var fixture = new Fixture(false)) {
            fixture.noTool = false;
            assertEquals(PROGRESS_OBSERVED, fixture.service.observe(OWNER, QUERY));
        }
    }

    @Test
    void rejects_stale_binding_and_malformed_or_unexpected_native_event() throws Exception {
        for (var condition : Map.of("engine_version", "1.12.0", "snapshot_sha256", "f".repeat(64),
                "native_invocation_id", "different", "event_payload", "{}").entrySet()) {
            try (var fixture = new Fixture(true)) {
                fixture.row.put(condition.getKey(), condition.getValue());
                assertEquals(SOURCE_UNAVAILABLE, fixture.service.observe(OWNER, QUERY));
                verify(fixture.verifier, never()).checkpointToolEvidenceReadable();
            }
        }
        try (var fixture = new Fixture(false)) {
            fixture.row.put("session_state", "{\"outside\":true}");
            assertEquals(PROGRESS_OBSERVED, fixture.service.observe(OWNER, QUERY));
        }
    }

    @Test
    void preserves_owner_denial_and_rejects_non_primary_or_reader_drift() throws Exception {
        try (var fixture = new Fixture(false)) {
            var legacy = new TrustedAgentActor(40001, TrustedAgentActor.SubjectType.USER, "fixture", "LEGACY");
            assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class,
                    () -> fixture.service.observe(legacy, QUERY)).code());
            verifyNoInteractions(fixture.ledger);
            fixture.primary = false;
            assertEquals(SOURCE_UNAVAILABLE, fixture.service.observe(OWNER, QUERY));
            verifyNoInteractions(fixture.ledger);
            fixture.primary = true;
            doReturn(false).when(fixture.verifier).checkpointObservationRoleReadable();
            assertEquals(SOURCE_UNAVAILABLE, fixture.service.observe(OWNER, QUERY));
            verifyNoInteractions(fixture.ledger);
        }
        try (var fixture = new Fixture(false)) {
            var absent = new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "outside fixture scope");
            when(fixture.ledger.executionMaterial(eq(OWNER), eq(QUERY))).thenThrow(absent);
            assertSame(absent, assertThrows(AgentLedgerException.class, () -> fixture.service.observe(OWNER, QUERY)));
            assertNull(fixture.observationSql);
        }
        try (var fixture = new Fixture(false)) {
            fixture.observationFailure = new DataAccessResourceFailureException("fixture role lost SELECT");
            assertEquals(SOURCE_UNAVAILABLE, fixture.service.observe(OWNER, QUERY));
            verify(fixture.verifier, never()).checkpointToolEvidenceReadable();
        }
    }

    @Test
    void verifies_reader_role_before_preparing_ledger_or_jdbc() {
        var source = mock(DataSource.class);
        var invalid = new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "reader role not verified");
        try (var verifiers = mockConstruction(AgentStorageVerifier.class,
                (mock, context) -> doThrow(invalid).when(mock).verifyCheckpointObservation());
             var ledgers = mockConstruction(AgentLedgerService.class);
             var templates = mockConstruction(JdbcTemplate.class)) {
            assertSame(invalid, assertThrows(AgentLedgerException.class, () -> new AgentCheckpointObservationService(source)));
            assertEquals(1, verifiers.constructed().size());
            assertTrue(ledgers.constructed().isEmpty());
            assertTrue(templates.constructed().isEmpty());
        }
    }

    private static AgentLedgerService.ExecutionMaterial material() {
        Duration timeout = Duration.ofSeconds(30);
        var policy = new ExecutionPolicyDTO(timeout, timeout, timeout, 1, 0, 100, 100,
                null, null, 1, Set.of(), null, null);
        var dependencies = new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "model:v1", "policy:v1");
        var definition = new AgentDefinitionSpecDTO("fixture-def", 1, "CONFIG", null, "v1", "v1",
                dependencies, policy, Map.of());
        var snapshot = new AgentExecutionSnapshot(definition, "b".repeat(64), policy,
                new AgentInputDTO(INPUT, List.of(), Map.of()), null);
        var run = new AgentRunDTO(QUERY.runId(), definition.definitionKey(), definition.version(), "fixture-session",
                AgentRunStatusEnum.SUSPENDED, "LEASE_EXPIRED", false, 1, 3, 3, DEADLINE, NOW, null);
        var authorization = new AgentRunAuthorization.Request(OWNER, run.runId(), definition.definitionKey(), 1,
                snapshot.definitionContentSha256(), "c".repeat(64), "d".repeat(64), "policy:v1", true, DEADLINE);
        return new AgentLedgerService.ExecutionMaterial(run, snapshot, authorization);
    }

    private static final class Fixture implements AutoCloseable {
        private final DataSource source = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final Statement statement = mock(Statement.class);
        private final MockedConstruction<AgentStorageVerifier> verifierMocks;
        private final MockedConstruction<AgentLedgerService> ledgerMocks;
        private final MockedConstruction<JdbcTemplate> jdbcMocks;
        private final AgentStorageVerifier verifier;
        private final AgentLedgerService ledger;
        private final AgentCheckpointObservationService service;
        private final Map<String, Object> row = new HashMap<>();
        private String observationSql;
        private String toolSql;
        private boolean primary = true;
        private boolean noTool = true;
        private RuntimeException observationFailure;

        Fixture(boolean typed) throws Exception {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            when(connection.createStatement()).thenReturn(statement);
            var event = Event.builder().id("fixture-event").invocationId("fixture-invocation").author("user")
                    .timestamp(NOW.toEpochMilli()).content(Content.fromParts(Part.fromText(INPUT))).build();
            var material = material();
            var request = material.authorization();
            row.putAll(Map.ofEntries(
                    Map.entry("checkpoint_present", true), Map.entry("run_fence", true), Map.entry("session_boundary", true),
                    Map.entry("no_budget", true), Map.entry("safe_boundary", "FIRST_DURABLE_INPUT_V1"),
                    Map.entry("engine_version", "1.13.0"), Map.entry("no_receipts", true),
                    Map.entry("definition_key", request.definitionKey()), Map.entry("definition_version", request.definitionVersion()),
                    Map.entry("definition_sha256", request.definitionSha256()), Map.entry("request_sha256", request.requestSha256()),
                    Map.entry("snapshot_sha256", request.snapshotSha256()), Map.entry("policy_version_ref", request.policyVersionRef()),
                    Map.entry("checkpoint_event_id", event.id()), Map.entry("native_invocation_id", event.invocationId()),
                    Map.entry("state_codec_version", typed ? 2 : 1),
                    Map.entry("session_state", new AdkSessionStateCodec(typed).encode(Map.of())),
                    Map.entry("event_timestamp_ms", event.timestamp()), Map.entry("event_payload", new AdkSessionEventCodec(typed).encode(event))));
            verifierMocks = mockConstruction(AgentStorageVerifier.class, (mock, context) -> {
                assertEquals(List.of(source), context.arguments());
                when(mock.checkpointObservationRoleReadable()).thenAnswer(ignored -> {
                    assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                    assertTrue(TransactionSynchronizationManager.hasResource(source));
                    return true;
                });
                when(mock.checkpointToolEvidenceReadable()).thenReturn(true);
            });
            ledgerMocks = mockConstruction(AgentLedgerService.class, (mock, context) -> {
                assertEquals(List.of(source), context.arguments());
                when(mock.executionMaterial(OWNER, QUERY)).thenReturn(material);
            });
            jdbcMocks = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(this::jdbc));
            try {
                service = new AgentCheckpointObservationService(source);
                verifier = verifierMocks.constructed().getFirst();
                ledger = ledgerMocks.constructed().getFirst();
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private Object jdbc(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            if (args.length == 0 || !(args[0] instanceof String sql)) return RETURNS_DEFAULTS.answer(invocation);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertTrue(TransactionSynchronizationManager.hasResource(source));
            if ("execute".equals(invocation.getMethod().getName())) {
                assertTrue(sql.equals("SET LOCAL statement_timeout = '2s'")
                        || sql.equals("SET LOCAL search_path = pg_catalog, tianji_agent"));
                return null;
            }
            if (sql.contains("pg_catalog.pg_is_in_recovery()")) return primary;
            if (sql.contains("AS checkpoint_present")) {
                if (observationFailure != null) throw observationFailure;
                observationSql = sql;
                assertEquals(List.of(OWNER.tenantId(), "USER", OWNER.issuerFingerprint(), OWNER.subjectId(), QUERY.runId()),
                        Arrays.asList(args).subList(2, args.length));
                ResultSet rs = mock(ResultSet.class);
                when(rs.getBoolean(anyString())).thenAnswer(call -> Boolean.TRUE.equals(row.get(call.getArgument(0))));
                when(rs.getString(anyString())).thenAnswer(call -> row.get(call.getArgument(0)));
                when(rs.getObject(anyString(), eq(Long.class))).thenAnswer(call -> row.get(call.getArgument(0)));
                when(rs.getObject(anyString(), eq(Integer.class))).thenAnswer(call -> row.get(call.getArgument(0)));
                return List.of(((RowMapper<?>) args[1]).mapRow(rs, 0));
            }
            if (sql.contains("agent_tool_invocation")) {
                toolSql = sql;
                assertEquals(List.of(OWNER.tenantId(), QUERY.runId(), OWNER.tenantId(), QUERY.runId()),
                        Arrays.asList(args).subList(2, args.length));
                return noTool;
            }
            throw new AssertionError("Unexpected checkpoint observation SQL");
        }

        @Override
        public void close() {
            jdbcMocks.close();
            ledgerMocks.close();
            verifierMocks.close();
        }
    }
}
