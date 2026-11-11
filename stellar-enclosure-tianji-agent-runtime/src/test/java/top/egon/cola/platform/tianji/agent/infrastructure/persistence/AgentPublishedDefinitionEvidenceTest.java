package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedConstruction;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class AgentPublishedDefinitionEvidenceTest {
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(42,
            TrustedAgentActor.SubjectType.SERVICE, "release-reader", "https://identity.example.test/tenant");
    private static final String KEY = "review";
    private static final long VERSION = 7;
    private static final Instant OBSERVED = Instant.parse("2026-11-11T02:04:06Z");
    private static final String BODY = "private publication content";
    private static final String RESOURCE_KEY = "private dependency key";

    @Test
    void projects_only_exact_published_metadata_from_the_owning_primary() throws SQLException {
        try (var fixture = new Fixture()) {
            var result = fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION);
            assertEquals(new AgentLedgerService.PublishedDefinitionEvidenceDTO(42, KEY, VERSION,
                    fixture.hash, "CONFIG", "in-v2", "out-v4", "model-route@2", "budget-policy@3", false, OBSERVED), result);
            assertEquals(List.of("read-only", "timeout", "primary-clock", "definition"), fixture.calls);
            assertFalse(result.toString().contains(BODY));
            verify(fixture.connection).commit();

            fixture.store(definition(KEY, VERSION, true));
            var withDependency = fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION);
            assertTrue(withDependency.optionalDependenciesPresent());
            assertFalse(withDependency.toString().contains(RESOURCE_KEY));
        }
    }

    @Test
    void does_not_fall_back_to_other_tenants_keys_or_versions() throws SQLException {
        try (var fixture = new Fixture()) {
            assertCode(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION + 1));
            assertCode(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, "other-key", VERSION));
            var foreign = new TrustedAgentActor(43, OWNER.subjectType(), OWNER.subjectId(), OWNER.issuerFingerprint());
            assertCode(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND,
                    () -> fixture.ledger.publishedDefinitionEvidence(foreign, KEY, VERSION));
        }
    }

    @Test
    void refuses_mismatched_digest_and_embedded_identity_independently() throws SQLException {
        try (var fixture = new Fixture()) {
            fixture.hash = "0".repeat(64);
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION));
            fixture.store(definition("different-key", VERSION, false));
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION));
            fixture.store(definition(KEY, VERSION + 1, false));
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION));
        }
    }

    @Test
    void refuses_unreadable_content_and_unavailable_primary_without_exposing_source_data() throws SQLException {
        try (var fixture = new Fixture()) {
            fixture.specification = BODY;
            var failure = assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION));
            assertFalse(failure.getMessage().contains(BODY));
            assertNull(failure.getCause());

            fixture.calls.clear();
            fixture.observed = null;
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION));
            assertEquals(List.of("read-only", "timeout", "primary-clock"), fixture.calls);

            fixture.observed = OBSERVED;
            fixture.sourceFailed = true;
            var unavailable = assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION));
            assertFalse(unavailable.getMessage().contains(BODY));
            assertNull(unavailable.getCause());
        }
    }

    @Test
    void rejects_unverified_identity_inexact_key_and_nonpositive_version_before_io() throws SQLException {
        try (var fixture = new Fixture()) {
            var legacy = new TrustedAgentActor(42, OWNER.subjectType(), OWNER.subjectId());
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED,
                    () -> fixture.ledger.publishedDefinitionEvidence(legacy, KEY, VERSION));
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED,
                    () -> fixture.ledger.publishedDefinitionEvidence(null, KEY, VERSION));
            for (var invalidKey : List.of(" " + KEY, KEY + " ", KEY + "\nline", "\uD800", "x".repeat(129))) {
                assertCode(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND,
                        () -> fixture.ledger.publishedDefinitionEvidence(OWNER, invalidKey, VERSION));
            }
            assertCode(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, 0));
            verifyNoInteractions(fixture.source);
        }
    }

    @Test
    void refuses_to_observe_an_existing_transaction() throws SQLException {
        try (var fixture = new Fixture()) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                        () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION));
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
            verifyNoInteractions(fixture.source);
        }
    }

    private static AgentLedgerException assertCode(AgentErrorCodeEnum expected, Executable operation) {
        var failure = assertThrows(AgentLedgerException.class, operation);
        assertEquals(expected, failure.code());
        return failure;
    }

    private static AgentDefinitionSpecDTO definition(String key, long version, boolean withOptionalDependency) {
        var prompts = withOptionalDependency ? List.of(new DefinitionResourceRef(RESOURCE_KEY, 1, "a".repeat(64)))
                : List.<DefinitionResourceRef>of();
        var dependencies = new DefinitionDependenciesDTO(prompts, List.of(), List.of(), "model-route@2", "budget-policy@3");
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(5),
                1, 0, 128, 64, null, null, 1, Set.of(), null, null);
        return new AgentDefinitionSpecDTO(key, version, "CONFIG", null, "in-v2", "out-v4", dependencies, policy,
                Map.of("instruction", BODY));
    }

    private static final class Fixture implements AutoCloseable {
        private final DataSource source = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final AgentJsonCodec json = new AgentJsonCodec();
        private final List<String> calls = new ArrayList<>();
        private final MockedConstruction<JdbcTemplate> jdbc;
        private final AgentLedgerService ledger;
        private String hash;
        private String specification;
        private Instant observed = OBSERVED;
        private boolean sourceFailed;

        private Fixture() throws SQLException {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            jdbc = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(this::jdbcCall),
                    (mock, context) -> assertSame(source, context.arguments().getFirst()));
            ledger = new AgentLedgerService(source);
            store(definition(KEY, VERSION, false));
        }

        private void store(AgentDefinitionSpecDTO spec) {
            hash = json.definitionHash(spec);
            specification = json.write(spec, AgentJsonCodec.MAX_SNAPSHOT_BYTES);
        }

        private Object jdbcCall(InvocationOnMock call) throws SQLException {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.hasResource(source));
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(Connection.TRANSACTION_REPEATABLE_READ,
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            var args = call.getRawArguments();
            String sql = (String) args[0];
            return switch (call.getMethod().getName()) {
                case "execute" -> {
                    if ("SET TRANSACTION READ ONLY".equals(sql)) calls.add("read-only");
                    else if ("SET LOCAL statement_timeout = '2s'".equals(sql)) calls.add("timeout");
                    else throw new AssertionError("Unexpected statement");
                    yield null;
                }
                case "queryForObject" -> {
                    calls.add("primary-clock");
                    assertTrue(sql.contains("NOT pg_catalog.pg_is_in_recovery()"));
                    assertTrue(sql.contains("pg_catalog.current_setting('transaction_read_only') = 'on'"));
                    yield observed == null ? null : Timestamp.from(observed);
                }
                case "query" -> {
                    calls.add("definition");
                    assertTrue(sql.contains("SELECT content_sha256, specification::text AS specification"));
                    assertTrue(sql.contains("FROM tianji_agent.agent_definition_version"));
                    assertTrue(sql.contains("WHERE tenant_id = ? AND definition_key = ? AND version = ?"));
                    assertFalse(sql.contains("FOR UPDATE"));
                    if (sourceFailed) throw new DataAccessResourceFailureException(BODY);
                    var bindings = (Object[]) args[2];
                    if (!List.of(42L, KEY, VERSION).equals(List.of(bindings))) yield List.of();
                    var row = mock(ResultSet.class);
                    when(row.getString("content_sha256")).thenReturn(hash);
                    when(row.getString("specification")).thenReturn(specification);
                    yield List.of(((RowMapper<?>) args[1]).mapRow(row, 0));
                }
                default -> throw new AssertionError("Unexpected JDBC method");
            };
        }

        @Override public void close() { jdbc.close(); }
    }
}
