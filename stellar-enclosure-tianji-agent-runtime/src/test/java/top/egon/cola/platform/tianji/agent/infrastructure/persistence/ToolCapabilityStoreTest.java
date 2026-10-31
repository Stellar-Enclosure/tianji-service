package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Controlled SQL markers prove ownership/epoch/time gates; actual SQL and lock behavior remain in the opt-in PostgreSQL suite. */
class ToolCapabilityStoreTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final String RUN = "10000000-0000-0000-0000-000000000001", HASH = "a".repeat(64);
    private static final TrustedAgentActor USER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final TrustedAgentActor SERVICE = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://idp.example/20");
    private static final URI RESOURCE = URI.create("https://agent.example/api");
    private static final ToolDescriptor DESCRIPTOR = descriptor(ToolPolicy.Risk.READ_ONLY, null);

    @Test void source_identity_readiness_and_ambient_transaction_are_preconditions() throws Exception {
        var source = mock(DataSource.class); var ledger = mock(AgentLedgerService.class);
        assertThrows(IllegalArgumentException.class, () -> new ToolCapabilityStore(source, ledger));
        when(ledger.usesDataSource(source)).thenReturn(true);
        var store = new ToolCapabilityStore(source, ledger);
        assertTrue(store.usesDataSource(source)); assertTrue(store.usesLedger(ledger));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> store.current(user(), DESCRIPTOR)).code());
        verifyNoInteractions(source);
        try (var verifiers = mockConstruction(ToolExecutionStorageVerifier.class)) {
            store.verifyStorage(); TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertThrows(IllegalStateException.class, () -> store.reserve(user(), DESCRIPTOR)); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        }
        verifyNoInteractions(source);
    }

    @Test void first_reservation_and_expired_reissue_use_one_immutable_slot_and_positive_epochs() throws Exception {
        var fixture = new Fixture(user(), DESCRIPTOR, false, 1);
        try (var templates = fixture.templates(); var verifiers = mockConstruction(ToolExecutionStorageVerifier.class)) {
            var store = fixture.store(); store.verifyStorage();
            var first = store.reserve(fixture.binding, DESCRIPTOR);
            assertTrue(first.isPresent()); assertEquals(new ToolExecutionGrant.ReservationVO(fixture.binding, 1), first.orElseThrow());
            fixture.values.put("issue_epoch", 4L); fixture.values.put("grant_id", ticketId(USER)); fixture.values.put("ciphertext", "ENC:v1:key:A:sealed"); fixture.values.put("expires_at", NOW.minusNanos(1));
            assertEquals(Optional.of(new ToolExecutionGrant.ReservationVO(fixture.binding, 5)), store.reserve(fixture.binding, DESCRIPTOR));
            assertEquals(fixture.binding.capabilityId(), fixture.values.get("capability_id").toString());
            verify(fixture.jdbc.get()).setQueryTimeout(5); verify(fixture.connection, atLeastOnce()).commit();
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        }
    }

    @Test void active_evidence_prevents_reissue_and_current_accepts_queued_or_running_source_only() throws Exception {
        var fixture = new Fixture(user(), DESCRIPTOR, true, 3); fixture.ticket(NOW.plusSeconds(20));
        try (var templates = fixture.templates(); var verifiers = mockConstruction(ToolExecutionStorageVerifier.class)) {
            var store = fixture.store(); store.verifyStorage();
            assertEquals(Optional.of(fixture.evidence()), store.current(fixture.binding, DESCRIPTOR));
            assertEquals(Optional.empty(), store.reserve(fixture.binding, DESCRIPTOR));
            verify(fixture.jdbc.get(), never()).update(startsWith("UPDATE"), any(Object[].class));
            verify(fixture.jdbc.get()).query(contains("status IN ('QUEUED','RUNNING')"), org.mockito.ArgumentMatchers.<RowMapper<Instant>>any(), any(Object[].class));
            fixture.now.set(NOW.plusSeconds(21));
            assertEquals(Optional.empty(), store.current(fixture.binding, DESCRIPTOR));
            fixture.sourceActive = false;
            assertEquals(Optional.empty(), store.current(fixture.binding, DESCRIPTOR));
        }
    }

    @Test void changed_binding_and_counter_overflow_do_not_overwrite_existing_evidence() throws Exception {
        var fixture = new Fixture(user(), DESCRIPTOR, true, 3);
        try (var templates = fixture.templates(); var verifiers = mockConstruction(ToolExecutionStorageVerifier.class)) {
            var store = fixture.store(); store.verifyStorage();
            var changed = new ToolExecutionGrant.BindingVO(fixture.binding.run(), DESCRIPTOR.reference(), HASH, fixture.binding.purpose(), URI.create("https://AGENT.example/api"), fixture.binding.authorizationResourceKey(), null);
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class, () -> store.reserve(changed, DESCRIPTOR)).code());
            fixture.values.put("issue_epoch", Long.MAX_VALUE);
            assertEquals(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, assertThrows(AgentLedgerException.class, () -> store.reserve(fixture.binding, DESCRIPTOR)).code());
            verify(fixture.jdbc.get(), never()).update(startsWith("UPDATE"), any(Object[].class));
        }
    }

    @Test void delayed_issue_reply_cannot_attach_after_a_new_epoch_and_exact_ticket_is_microsecond_bounded() throws Exception {
        var fixture = new Fixture(user(), DESCRIPTOR, true, 3);
        try (var templates = fixture.templates(); var verifiers = mockConstruction(ToolExecutionStorageVerifier.class)) {
            var store = fixture.store(); store.verifyStorage();
            var stale = new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(fixture.binding, 2), ticketId(USER), "ENC:v1:key:A:sealed", NOW.plusSeconds(20));
            assertFalse(store.attach(stale, DESCRIPTOR));
            verify(fixture.jdbc.get(), never()).update(startsWith("UPDATE"), any(Object[].class));
            var precise = NOW.plusSeconds(20).plusNanos(123_999);
            var current = new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(fixture.binding, 3), ticketId(USER), "ENC:v1:key:A:sealed", precise);
            fixture.sourceActive = false; assertFalse(store.attach(current, DESCRIPTOR)); fixture.sourceActive = true;
            assertTrue(store.attach(current, DESCRIPTOR));
            assertEquals(NOW.plusSeconds(20).plusNanos(123_000), store.current(fixture.binding, DESCRIPTOR).orElseThrow().expiresAt());
        }
    }

    @Test void service_source_and_sealed_ticket_are_reconstructed_without_becoming_a_user_grant() throws Exception {
        var fixture = new Fixture(service(), DESCRIPTOR, true, 2); fixture.ticket(NOW.plusSeconds(20));
        try (var templates = fixture.templates(); var verifiers = mockConstruction(ToolExecutionStorageVerifier.class)) {
            var store = fixture.store(); store.verifyStorage();
            assertEquals(Optional.of(fixture.evidence()), store.current(fixture.binding, DESCRIPTOR));
            fixture.values.put("source_environment", "other");
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> store.current(fixture.binding, DESCRIPTOR)).code());
        }
    }

    @Test void source_drift_or_hidden_policy_cannot_read_capability_rows() throws Exception {
        var fixture = new Fixture(user(), DESCRIPTOR, true, 1);
        try (var templates = fixture.templates(); var verifiers = mockConstruction(ToolExecutionStorageVerifier.class)) {
            var store = fixture.store(); store.verifyStorage(); fixture.sourceActive = false;
            assertEquals(Optional.empty(), store.reserve(fixture.binding, DESCRIPTOR));
            fixture.sourceActive = true;
            var original = fixture.binding.run();
            fixture.material = new AgentLedgerService.ExecutionMaterial(null, fixture.material.snapshot(), new AgentRunAuthorization.Request(original.actor(), original.runId(), original.definitionKey(), original.definitionVersion(),
                    original.definitionSha256(), "b".repeat(64), original.snapshotSha256(), original.policyVersionRef(), original.sessionRequired(), original.deadlineAt()));
            assertEquals(Optional.empty(), store.current(fixture.binding, DESCRIPTOR));
            fixture.material = material(fixture.binding, DESCRIPTOR, policy(DESCRIPTOR, 3, Set.of(), null));
            assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> store.current(fixture.binding, DESCRIPTOR)).code());
            fixture.material = material(fixture.binding, DESCRIPTOR, policy(DESCRIPTOR, 0, Set.of(DESCRIPTOR.versionKey()), null));
            assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> store.reserve(fixture.binding, DESCRIPTOR)).code());
            assertThrows(IllegalArgumentException.class, () -> store.current(fixture.binding, descriptor(ToolPolicy.Risk.APPROVAL_REQUIRED, "approval")));
            verify(fixture.jdbc.get(), never()).query(startsWith("SELECT * FROM tianji_agent.agent_tool_capability"), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
        }
    }

    @Test void approval_required_tool_must_keep_the_effective_approval_policy() throws Exception {
        var descriptor = descriptor(ToolPolicy.Risk.APPROVAL_REQUIRED, "approval-v1"); var binding = binding(USER, descriptor, null);
        var fixture = new Fixture(binding, descriptor, true, 1);
        fixture.material = material(binding, descriptor, policy(descriptor, 2, Set.of(descriptor.versionKey()), "another-policy"));
        try (var templates = fixture.templates(); var verifiers = mockConstruction(ToolExecutionStorageVerifier.class)) {
            var store = fixture.store(); store.verifyStorage();
            assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> store.reserve(binding, descriptor)).code());
            verify(fixture.jdbc.get(), never()).query(startsWith("SELECT * FROM tianji_agent.agent_tool_capability"), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
        }
    }

    @Test void expiry_after_a_capability_lock_rolls_back_instead_of_returning_evidence() throws Exception {
        var fixture = new Fixture(user(), DESCRIPTOR, true, 1); fixture.ticket(NOW.plusSeconds(20));
        fixture.afterRead = () -> fixture.now.set(NOW.plusSeconds(61));
        try (var templates = fixture.templates(); var verifiers = mockConstruction(ToolExecutionStorageVerifier.class)) {
            var store = fixture.store(); store.verifyStorage();
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> store.current(fixture.binding, DESCRIPTOR)).code());
            verify(fixture.connection).rollback();
        }
    }

    @Test void persistence_failures_and_invalid_stored_tickets_are_sanitized_without_private_causes() throws Exception {
        var fixture = new Fixture(user(), DESCRIPTOR, false, 1); fixture.failWrite = true;
        try (var templates = fixture.templates(); var verifiers = mockConstruction(ToolExecutionStorageVerifier.class)) {
            var store = fixture.store(); store.verifyStorage();
            var failure = assertThrows(AgentLedgerException.class, () -> store.reserve(fixture.binding, DESCRIPTOR));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code()); assertNull(failure.getCause()); assertFalse(failure.getMessage().contains("private"));
            verify(fixture.connection).rollback();
            fixture.failWrite = false; fixture.present = true; fixture.ticket(NOW.plusSeconds(20)); fixture.values.put("ciphertext", "private corrupt ciphertext");
            var malformed = assertThrows(AgentLedgerException.class, () -> store.current(fixture.binding, DESCRIPTOR));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, malformed.code()); assertNull(malformed.getCause()); assertFalse(malformed.getMessage().contains("private"));
        }
    }

    @Test void malformed_source_snapshot_cause_is_not_exposed_through_tool_evidence_storage() throws Exception {
        var fixture = new Fixture(user(), DESCRIPTOR, true, 1); fixture.ticket(NOW.plusSeconds(20));
        when(fixture.ledger.executionMaterial(eq(USER), any())).thenThrow(new IllegalStateException("stored source invalid", new RuntimeException("private original snapshot")));
        try (var templates = fixture.templates(); var verifiers = mockConstruction(ToolExecutionStorageVerifier.class)) {
            var store = fixture.store(); store.verifyStorage();
            var failure = assertThrows(AgentLedgerException.class, () -> store.current(fixture.binding, DESCRIPTOR));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code()); assertNull(failure.getCause()); assertFalse(failure.getMessage().contains("private"));
            verify(fixture.jdbc.get(), never()).query(startsWith("SELECT * FROM tianji_agent.agent_tool_capability"), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
        }
    }

    @Test void failed_reverification_clears_readiness_and_replica_sessions_are_never_used() throws Exception {
        var fixture = new Fixture(user(), DESCRIPTOR, true, 1);
        try (var templates = fixture.templates(); var verifiers = mockConstruction(ToolExecutionStorageVerifier.class, (verifier, context) -> {
            if (context.getCount() > 1) doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Schema unavailable")).when(verifier).verify();
        })) {
            var store = fixture.store(); store.verifyStorage();
            assertEquals(1, verifiers.constructed().size());
            fixture.primary = false;
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> store.current(fixture.binding, DESCRIPTOR)).code());
            assertThrows(AgentLedgerException.class, store::verifyStorage);
            clearInvocations(fixture.jdbc.get());
            assertThrows(AgentLedgerException.class, () -> store.current(fixture.binding, DESCRIPTOR));
            verifyNoInteractions(fixture.jdbc.get());
        }
    }

    @Test void internal_current_joins_only_the_verified_exact_source_transaction_without_an_early_commit() throws Exception {
        var fixture = new Fixture(user(), DESCRIPTOR, true, 3); fixture.ticket(NOW.plusSeconds(20));
        try (var templates = fixture.templates(); var verifiers = mockConstruction(ToolExecutionStorageVerifier.class)) {
            var store = fixture.store(); store.verifyStorage();
            assertThrows(AgentLedgerException.class, () -> store.currentInTransaction(fixture.binding, DESCRIPTOR));
            verifyNoInteractions(fixture.source);
            var outer = new TransactionTemplate(new DataSourceTransactionManager(fixture.source));
            var result = outer.execute(status -> {
                assertThrows(IllegalStateException.class, () -> store.current(fixture.binding, DESCRIPTOR));
                var found = store.currentInTransaction(fixture.binding, DESCRIPTOR);
                assertEquals(Optional.of(fixture.evidence()), found);
                try { verify(fixture.connection, never()).commit(); }
                catch (java.sql.SQLException failure) { throw new AssertionError(failure); }
                return found;
            });
            assertEquals(Optional.of(fixture.evidence()), result);
            verify(fixture.source).getConnection(); verify(fixture.connection).commit();
            verify(fixture.connection, never()).rollback();
            verify(fixture.jdbc.get()).query(contains("status IN ('QUEUED','RUNNING')"), org.mockito.ArgumentMatchers.<RowMapper<Instant>>any(), any(Object[].class));
            verify(fixture.jdbc.get()).query(contains("owner_issuer=? AND owner_id=? FOR UPDATE"), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
        }
    }

    @Test void internal_current_requires_readiness_and_its_own_data_source_binding_even_with_an_active_transaction() throws Exception {
        var fixture = new Fixture(user(), DESCRIPTOR, true, 1); fixture.ticket(NOW.plusSeconds(20));
        try (var templates = fixture.templates(); var verifiers = mockConstruction(ToolExecutionStorageVerifier.class)) {
            var store = fixture.store();
            clearInvocations(fixture.jdbc.get());
            var outer = new TransactionTemplate(new DataSourceTransactionManager(fixture.source));
            outer.execute(status -> {
                assertThrows(AgentLedgerException.class, () -> store.currentInTransaction(fixture.binding, DESCRIPTOR));
                return true;
            });
            verifyNoInteractions(fixture.jdbc.get());
            store.verifyStorage();
            var otherSource = mock(DataSource.class); var otherConnection = mock(Connection.class);
            when(otherSource.getConnection()).thenReturn(otherConnection); when(otherConnection.getAutoCommit()).thenReturn(true);
            new TransactionTemplate(new DataSourceTransactionManager(otherSource)).execute(status -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertFalse(TransactionSynchronizationManager.hasResource(fixture.source));
                assertThrows(AgentLedgerException.class, () -> store.currentInTransaction(fixture.binding, DESCRIPTOR));
                return true;
            });
            verifyNoInteractions(fixture.jdbc.get());
        }
    }

    private static ToolDescriptor descriptor(ToolPolicy.Risk risk, String approval) {
        return new ToolDescriptor("process.start", 1, "start_process", "Fixed tool", new ToolSchema(Map.of(), Set.of()), new ToolSchema(Map.of(), Set.of()),
                new ToolPolicy(risk, "process.start", "tianji.api.process.instance.start", HASH, approval));
    }
    private static ToolExecutionGrant.BindingVO user() { return binding(USER, DESCRIPTOR, null); }
    private static ToolExecutionGrant.BindingVO service() { return binding(SERVICE, DESCRIPTOR, new AgentServiceExecutionGrant.Subject("worker", "app", "business", "application", "test", URI.create("https://worker.example/source"))); }
    private static ToolExecutionGrant.BindingVO binding(TrustedAgentActor actor, ToolDescriptor descriptor, AgentServiceExecutionGrant.Subject subject) {
        var run = new AgentRunAuthorization.Request(actor, RUN, "assistant", 1, HASH, HASH, HASH, "execution-policy", true, NOW.plusSeconds(60));
        return new ToolExecutionGrant.BindingVO(run, descriptor.reference(), descriptor.policy().bindingSha256(), "TIANJI_TOOL_PROCESS_START_V1", RESOURCE, "tianji.api.agent.start", subject);
    }
    private static String ticketId(TrustedAgentActor actor) { return (actor.subjectType() == TrustedAgentActor.SubjectType.USER ? "9" : "8") + "1".repeat(62); }
    private static ExecutionPolicyDTO policy(ToolDescriptor descriptor, int calls, Set<String> allowed, String approval) {
        return new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(10), Duration.ofSeconds(10), 5, calls, 1000, 1000, null, null, 1, allowed, null, approval);
    }
    private static AgentLedgerService.ExecutionMaterial material(ToolExecutionGrant.BindingVO binding, ToolDescriptor descriptor, ExecutionPolicyDTO policy) {
        var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "input-v1", "output-v1", new DefinitionDependenciesDTO(List.of(), List.of(), List.of(descriptor.reference()), "model", "execution-policy"), policy, Map.of());
        return new AgentLedgerService.ExecutionMaterial(null, new AgentExecutionSnapshot(definition, HASH, policy, new AgentInputDTO("task", List.of(), Map.of()), null), binding.run());
    }

    private static final class Fixture {
        final DataSource source = mock(DataSource.class); final Connection connection = mock(Connection.class); final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final AtomicReference<JdbcTemplate> jdbc = new AtomicReference<>(); final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        final ToolExecutionGrant.BindingVO binding; final ToolDescriptor descriptor; final Map<String, Object> values = new HashMap<>();
        AgentLedgerService.ExecutionMaterial material; boolean present, sourceActive = true, primary = true, failWrite; Runnable afterRead = () -> { };

        Fixture(ToolExecutionGrant.BindingVO binding, ToolDescriptor descriptor, boolean present, long epoch) throws Exception {
            this.binding = binding; this.descriptor = descriptor; this.present = present;
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
            when(ledger.usesDataSource(source)).thenReturn(true);
            material = material(binding, descriptor, policy(descriptor, 3, Set.of(descriptor.versionKey()), descriptor.policy().approvalPolicyRef()));
            when(ledger.executionMaterial(eq(binding.run().actor()), any())).thenAnswer(call -> material);
            var actor = binding.run().actor();
            values.putAll(Map.of("tenant_id", actor.tenantId(), "run_id", binding.run().runId(), "owner_type", actor.subjectType().name(), "owner_issuer", actor.issuerFingerprint(), "owner_id", actor.subjectId(),
                    "tool_key", descriptor.key(), "tool_version", descriptor.version(), "tool_sha256", descriptor.reference().contentSha256(), "backend_binding_sha256", binding.backendBindingSha256(), "purpose", binding.purpose()));
            values.putAll(Map.of("resource_uri", binding.resource().toASCIIString(), "authorization_resource_key", binding.authorizationResourceKey(), "capability_id", UUID.fromString(binding.capabilityId()), "binding_sha256", binding.bindingSha256(), "issue_epoch", epoch,
                    "created_at", NOW.minusSeconds(1), "updated_at", NOW, "deadline_at", binding.run().deadlineAt()));
            var subject = binding.serviceSubject();
            if (subject != null) values.putAll(Map.of("source_client_id", subject.clientId(), "source_app_id", subject.appId(), "source_business_code", subject.businessCode(), "source_application_code", subject.applicationCode(), "source_environment", subject.environment(), "source_resource_uri", subject.resourceUri().toASCIIString()));
        }
        ToolCapabilityStore store() { return new ToolCapabilityStore(source, ledger); }
        void ticket(Instant expires) { values.put("grant_id", ticketId(binding.run().actor())); values.put("ciphertext", "ENC:v1:key:A:sealed"); values.put("expires_at", expires); }
        ToolExecutionGrant.EvidenceVO evidence() { return new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(binding, ((Number) values.get("issue_epoch")).longValue()), (String) values.get("grant_id"), (String) values.get("ciphertext"), (Instant) values.get("expires_at")); }

        org.mockito.MockedConstruction<JdbcTemplate> templates() {
            return mockConstruction(JdbcTemplate.class, (template, context) -> {
                jdbc.set(template);
                when(template.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class))).thenAnswer(call -> primary);
                when(template.queryForObject(eq("SELECT clock_timestamp()"), eq(Timestamp.class))).thenAnswer(call -> Timestamp.from(now.get()));
                when(template.queryForObject(startsWith("SELECT set_config"), eq(String.class))).thenReturn("pg_catalog,tianji_agent,public");
                doAnswer(call -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); assertTrue(TransactionSynchronizationManager.hasResource(source));
                    String sql = call.getArgument(0); RowMapper<?> mapper = call.getArgument(1);
                    if (sql.startsWith("SELECT deadline_at FROM tianji_agent.agent_run")) return sourceActive ? List.of(binding.run().deadlineAt()) : List.of();
                    if (sql.startsWith("SELECT * FROM tianji_agent.agent_tool_capability")) {
                        if (!present) return List.of(); var result = mapper.mapRow(result(), 0); afterRead.run(); return List.of(result);
                    }
                    throw new AssertionError("Unexpected controlled capability query");
                }).when(template).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
                doAnswer(call -> {
                    String sql = call.getArgument(0); Object[] args = (Object[]) call.getRawArguments()[1];
                    if (failWrite) throw new DataIntegrityViolationException("private ciphertext or arguments");
                    if (sql.startsWith("INSERT INTO")) { if (present) return 0; present = true; values.put("issue_epoch", 1L); return 1; }
                    if (sql.startsWith("UPDATE") && sql.contains("SET grant_id=")) { values.put("grant_id", args[0]); values.put("ciphertext", args[1]); values.put("expires_at", ((Timestamp) args[2]).toInstant()); return 1; }
                    if (sql.startsWith("UPDATE") && sql.contains("SET issue_epoch=")) { values.put("issue_epoch", args[0]); values.remove("grant_id"); values.remove("ciphertext"); values.remove("expires_at"); return 1; }
                    throw new AssertionError("Unexpected controlled capability update");
                }).when(template).update(anyString(), any(Object[].class));
            });
        }
        ResultSet result() throws Exception {
            var result = mock(ResultSet.class);
            when(result.getString(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value == null ? null : value.toString(); });
            when(result.getLong(anyString())).thenAnswer(call -> { var value = (Number) values.get(call.getArgument(0)); return value == null ? 0L : value.longValue(); });
            when(result.getTimestamp(anyString())).thenAnswer(call -> { var value = (Instant) values.get(call.getArgument(0)); return value == null ? null : Timestamp.from(value); });
            when(result.getObject(eq("capability_id"), eq(UUID.class))).thenReturn((UUID) values.get("capability_id"));
            return result;
        }
    }
}
