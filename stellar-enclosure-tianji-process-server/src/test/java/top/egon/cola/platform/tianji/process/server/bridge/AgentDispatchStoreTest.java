package top.egon.cola.platform.tianji.process.server.bridge;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Duration;
import java.time.Instant;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentDispatchStoreTest {
    static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    static final ProcessActor ACTOR = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "process-worker", "https://idp.example/20");
    static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "requester", "https://idp.example/20");

    @Test
    void process_cancellation_requires_the_bounded_closure_scope_and_storage_primitive() {
        Class<?> scope = assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStore$ProcessClosureScope"));
        assertDoesNotThrow(() -> AgentDispatchStore.class.getMethod("scheduleProcessClosure", scope));
    }

    @Test
    void process_closure_scope_accepts_exact_user_or_service_owners_and_rejects_untrusted_facts() {
        assertDoesNotThrow(() -> scope(OWNER));
        assertDoesNotThrow(() -> scope(ACTOR));
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> scope(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.ProcessClosureScope(
                        new ProcessActor(20, ProcessActor.SubjectType.SERVICE, "legacy"), "instance-one", "release-one",
                        "process:1:id", "b".repeat(64), "order-123", "correlation-one")),
                () -> assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.ProcessClosureScope(
                        OWNER, " instance", "release-one", "process:1:id", "b".repeat(64), "order-123", "correlation-one")),
                () -> assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.ProcessClosureScope(
                        OWNER, "instance-one", "-release", "process:1:id", "b".repeat(64), "order-123", "correlation-one")),
                () -> assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.ProcessClosureScope(
                        OWNER, "instance-one", "release-one", " ", "b".repeat(64), "order-123", "correlation-one")),
                () -> assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.ProcessClosureScope(
                        OWNER, "instance-one", "release-one", "process:1:id", "invalid", "order-123", "correlation-one")),
                () -> assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.ProcessClosureScope(
                        OWNER, "instance-one", "release-one", "process:1:id", "b".repeat(64), " order", "correlation-one")),
                () -> assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.ProcessClosureScope(
                        OWNER, "instance-one", "release-one", "process:1:id", "b".repeat(64), "order-123", "-correlation"))
        );
    }

    @Test
    void process_closure_requires_the_owning_transaction_and_zero_candidates_issue_no_update() {
        try (var fixture = fixture()) {
            assertThrows(IllegalStateException.class, () -> fixture.store().scheduleProcessClosure(scope(OWNER)));
            var foreignSource = mock(DataSource.class);
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.bindResource(foreignSource, new ConnectionHolder(mock(Connection.class)));
            try { assertThrows(IllegalStateException.class, () -> fixture.store().scheduleProcessClosure(scope(OWNER))); }
            finally {
                TransactionSynchronizationManager.unbindResource(foreignSource);
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
            fixture.begin();
            rows(fixture, List.of());
            assertEquals(0, fixture.store().scheduleProcessClosure(scope(OWNER)));
            verify(fixture.jdbc()).query(argThat(sql -> sql.contains("state<>'CLOSED'") && sql.contains("scope_type='BPMN'")
                            && sql.contains("scope_id=?") && sql.contains("ORDER BY") && sql.contains("LIMIT 257") && sql.contains("FOR UPDATE")),
                    org.mockito.ArgumentMatchers.<RowMapper<AgentDispatchStore.Intent>>any(), eq(20L), eq("instance-one"));
            verify(fixture.jdbc(), never()).update(anyString(), any(Object[].class));
        }
    }

    @Test
    void process_closure_validates_all_mixed_open_rows_then_updates_only_their_stored_service_keys() {
        try (var fixture = fixture()) {
            fixture.begin();
            var candidates = List.of(intent("job-pending", AgentDispatchStore.State.PENDING, null),
                    intent("job-submitted", AgentDispatchStore.State.SUBMITTED, "run-one"),
                    intent("job-acknowledged", AgentDispatchStore.State.ACKNOWLEDGED, "run-two"),
                    intent("job-closing", AgentDispatchStore.State.CLOSING, "run-three"));
            rows(fixture, candidates);
            when(fixture.jdbc().update(anyString(), any(Object[].class))).thenReturn(candidates.size());
            assertEquals(4, fixture.store().scheduleProcessClosure(scope(OWNER)));
            var sql = org.mockito.ArgumentCaptor.forClass(String.class);
            var arguments = org.mockito.ArgumentCaptor.forClass(Object[].class);
            verify(fixture.jdbc()).update(sql.capture(), arguments.capture());
            assertTrue(sql.getValue().contains("VALUES"));
            assertTrue(sql.getValue().contains("state='CLOSING'"));
            assertTrue(sql.getValue().contains("subject_issuer") && sql.getValue().contains("subject_id")
                    && sql.getValue().contains("source_id") && sql.getValue().contains("occurrence_id"));
            assertFalse(sql.getValue().matches("(?s).*UPDATE .* WHERE tenant_id=\\? AND scope_type='BPMN' AND scope_id=\\?.*"));
            var values = List.of(arguments.getValue());
            assertTrue(values.contains(ACTOR.issuerFingerprint()));
            assertTrue(values.contains(ACTOR.subjectId()));
            assertTrue(values.contains("process-east"));
            assertTrue(values.contains("job-closing"));
            assertFalse(values.contains(OWNER.subjectId()));
            assertFalse(values.contains("phantom-after-select"));
        }
    }

    @Test
    void process_closure_fails_before_any_write_for_foreign_or_inconsistent_candidates() {
        var original = seed("job-one");
        var foreignActor = ProcessActor.verified(21, ProcessActor.SubjectType.SERVICE, "process-worker", "https://idp.example/21");
        var foreignBinding = new AgentDispatchBinding(foreignActor, original.binding().sourceId(), original.binding().topic(),
                original.binding().schemaVersion(), original.binding().definitionKey(), original.binding().definitionVersion(),
                original.binding().definitionContentSha256(), original.binding().instruction(), original.binding().submissionTimeout());
        var mismatches = List.of(
                copiedIntent(original, original.binding(), "other-instance", original.releaseKey(), original.processDefinitionId(),
                        original.processModelSha256(), original.businessKey(), original.correlationId()),
                copiedIntent(original, original.binding(), original.instanceId(), "other-release", original.processDefinitionId(),
                        original.processModelSha256(), original.businessKey(), original.correlationId()),
                copiedIntent(original, original.binding(), original.instanceId(), original.releaseKey(), "other-definition",
                        original.processModelSha256(), original.businessKey(), original.correlationId()),
                copiedIntent(original, original.binding(), original.instanceId(), original.releaseKey(), original.processDefinitionId(),
                        "d".repeat(64), original.businessKey(), original.correlationId()),
                copiedIntent(original, original.binding(), original.instanceId(), original.releaseKey(), original.processDefinitionId(),
                        original.processModelSha256(), "other-business", original.correlationId()),
                copiedIntent(original, original.binding(), original.instanceId(), original.releaseKey(), original.processDefinitionId(),
                        original.processModelSha256(), original.businessKey(), "other-correlation"),
                copiedIntent(original, foreignBinding, original.instanceId(), original.releaseKey(), original.processDefinitionId(),
                        original.processModelSha256(), original.businessKey(), original.correlationId()),
                intent("job-closed", AgentDispatchStore.State.CLOSED, "run-closed")
        );
        for (var candidate : mismatches) {
            try (var fixture = fixture()) {
                fixture.begin(); rows(fixture, List.of(candidate));
                assertThrows(RuntimeException.class, () -> fixture.store().scheduleProcessClosure(scope(OWNER)));
                verify(fixture.jdbc(), never()).update(anyString(), any(Object[].class));
            }
        }
    }

    @Test
    void process_closure_reuses_existing_seed_fingerprint_and_idempotency_validation_before_writing() {
        for (String column : List.of("seed_sha256", "idempotency_key")) {
            try (var fixture = fixture()) {
                fixture.begin(); corruptRow(fixture, intent("job-one", AgentDispatchStore.State.PENDING, null), column);
                assertThrows(RuntimeException.class, () -> fixture.store().scheduleProcessClosure(scope(OWNER)));
                verify(fixture.jdbc(), never()).update(anyString(), any(Object[].class));
            }
        }
    }

    @Test
    void process_closure_rejects_the_257th_row_and_affected_count_mismatch_without_fallback_update() {
        try (var overflow = fixture()) {
            overflow.begin();
            var candidates = new ArrayList<AgentDispatchStore.Intent>();
            for (int index = 0; index < 257; index++) candidates.add(intent("job-" + index, AgentDispatchStore.State.PENDING, null));
            rows(overflow, candidates);
            assertThrows(RuntimeException.class, () -> overflow.store().scheduleProcessClosure(scope(OWNER)));
            verify(overflow.jdbc(), never()).update(anyString(), any(Object[].class));
        }
        try (var mismatch = fixture()) {
            mismatch.begin(); rows(mismatch, List.of(intent("job-one", AgentDispatchStore.State.PENDING, null),
                    intent("job-two", AgentDispatchStore.State.CLOSING, "run")));
            when(mismatch.jdbc().update(anyString(), any(Object[].class))).thenReturn(1);
            assertThrows(RuntimeException.class, () -> mismatch.store().scheduleProcessClosure(scope(OWNER)));
            verify(mismatch.jdbc(), times(1)).update(anyString(), any(Object[].class));
        }
    }

    @Test
    void process_closure_propagates_storage_failure_for_the_caller_transaction_to_roll_back() {
        try (var fixture = fixture()) {
            fixture.begin();
            when(fixture.jdbc().query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AgentDispatchStore.Intent>>any(), any(Object[].class)))
                    .thenThrow(new DataAccessResourceFailureException("storage unavailable"));
            assertThrows(DataAccessResourceFailureException.class, () -> fixture.store().scheduleProcessClosure(scope(OWNER)));
            verify(fixture.jdbc(), never()).update(anyString(), any(Object[].class));
        }
    }

    @Test
    void startup_guards_reject_schema_and_registry_or_dispatch_instance_drift_without_ddl() {
        var source = mock(DataSource.class);
        var manager = new DataSourceTransactionManager(source);
        try (var verification = mockConstruction(ProcessApiSchemaVerifier.class)) {
            var dispatches = new AgentDispatchStore(source, manager, "tianji_process");
            assertDoesNotThrow(() -> dispatches.requireStorage(source, manager, "tianji_process"));
            assertThrows(IllegalArgumentException.class, () -> dispatches.requireStorage(source, manager, "other_schema"));
            var registry = new AgentResultProducerRegistry(List.of(new AgentResultProducerRegistry.Registration(ACTOR, "agent", "process-east", ACTOR)));
            var inbox = new AgentResultInbox(source, manager, "tianji_process", dispatches, registry, Duration.ofDays(1));
            assertDoesNotThrow(() -> inbox.requireBindings(dispatches, registry));
            assertThrows(IllegalArgumentException.class, () -> inbox.requireBindings(mock(AgentDispatchStore.class), registry));
            assertThrows(IllegalArgumentException.class, () -> inbox.requireBindings(dispatches, mock(AgentResultProducerRegistry.class)));
            assertThrows(IllegalArgumentException.class, () -> new AgentResultInbox(source, manager, "other_schema", dispatches, registry, Duration.ofDays(1)));
            verifyNoInteractions(source);
        }
    }

    static AgentDispatchBinding binding() {
        return new AgentDispatchBinding(ACTOR, "process-east", "agent-start", "1", "research", 2,
                "a".repeat(64), "Summarize the supplied task data", Duration.ofSeconds(60));
    }
    static AgentDispatchStore.Seed seed(String occurrence) {
        return new AgentDispatchStore.Seed(binding(), "instance-one", occurrence, "release-one", "process:1:id", "b".repeat(64),
                "agentTask", "c".repeat(64), "order-123", "correlation-one", Map.of("question", "private task", "amount", 7L));
    }
    static AgentDispatchStore.Intent intent(String occurrence, AgentDispatchStore.State state, String runId) {
        return new AgentDispatchStore.Intent(seed(occurrence), NOW, NOW.plusSeconds(60), state, runId,
                state == AgentDispatchStore.State.CLOSED ? NOW.plusSeconds(90) : null);
    }

    @Test
    void occurrence_identity_is_stable_while_the_full_payload_and_routing_are_bound() {
        var original = seed("job-one");
        var reordered = new HashMap<String, Object>();
        reordered.put("amount", 7L);
        reordered.put("question", "private task");
        var copy = copy(original, original.binding(), reordered);
        assertEquals(original.fingerprint(), copy.fingerprint());
        assertEquals(original.idempotencyKey(), copy.idempotencyKey());
        assertNotEquals(original.idempotencyKey(), seed("job-two").idempotencyKey());
        var changedInput = copy(original, original.binding(), Map.of("question", "different", "amount", 7L));
        assertEquals(original.idempotencyKey(), changedInput.idempotencyKey());
        assertNotEquals(original.fingerprint(), changedInput.fingerprint());
        var binding = original.binding();
        var changedDefinition = new AgentDispatchBinding(ACTOR, binding.sourceId(), binding.topic(), binding.schemaVersion(),
                binding.definitionKey(), 3, "d".repeat(64), binding.instruction(), binding.submissionTimeout());
        assertNotEquals(original.fingerprint(), copy(original, changedDefinition, original.inputs()).fingerprint());
        assertThrows(UnsupportedOperationException.class, () -> original.inputs().put("new", "value"));
        assertFalse(original.toString().contains("private task"));
    }

    @Test
    void reconstructed_command_keeps_the_same_deadline_definition_input_and_occurrence() {
        var intent = intent("job-one", AgentDispatchStore.State.PENDING, null);
        assertEquals(intent.query().link(), intent.command().link());
        assertEquals(NOW.plusSeconds(60), intent.command().link().dispatchNotAfter());
        assertEquals("job-one", intent.command().link().activityOccurrenceId());
        assertEquals("a".repeat(64), intent.command().link().definitionContentSha256());
        assertEquals(intent.seed().inputs(), intent.command().start().input().taskData());
        assertNull(intent.command().start().sessionId());
        assertNull(intent.command().start().policyOverride());
        assertFalse(intent.toString().contains("private task"));
    }

    @Test
    void unverified_identity_invalid_deadline_and_nonprimitive_values_are_rejected() {
        var original = seed("job-one");
        assertThrows(IllegalArgumentException.class, () -> copy(original, binding(), Map.of("amount", 7)));
        assertThrows(IllegalArgumentException.class, () -> copy(original, binding(), Map.of("amount", Map.of("nested", true))));
        assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.Key(
                new ProcessActor(20, ProcessActor.SubjectType.SERVICE, "worker"), "source", "instance", "occurrence"));
        assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.Key(
                ProcessActor.verified(20, ProcessActor.SubjectType.USER, "worker", "https://idp.example/20"), "source", "instance", "occurrence"));
        assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.Intent(original, NOW, NOW.plusSeconds(61),
                AgentDispatchStore.State.PENDING, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.Intent(original, NOW, NOW.plusSeconds(60),
                AgentDispatchStore.State.SUBMITTED, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.Intent(original, NOW, NOW.plusSeconds(60),
                AgentDispatchStore.State.PENDING, "run", null));
    }

    private static AgentDispatchStore.Seed copy(AgentDispatchStore.Seed seed, AgentDispatchBinding binding, Map<String, Object> input) {
        return new AgentDispatchStore.Seed(binding, seed.instanceId(), seed.occurrenceId(), seed.releaseKey(), seed.processDefinitionId(),
                seed.processModelSha256(), seed.elementId(), seed.workerInputSha256(), seed.businessKey(), seed.correlationId(), input);
    }

    private static AgentDispatchStore.ProcessClosureScope scope(ProcessActor owner) {
        return new AgentDispatchStore.ProcessClosureScope(owner, "instance-one", "release-one", "process:1:id",
                "b".repeat(64), "order-123", "correlation-one");
    }

    private static AgentDispatchStore.Intent copiedIntent(AgentDispatchStore.Seed seed, AgentDispatchBinding binding,
                                                           String instanceId, String releaseKey, String processDefinitionId,
                                                           String modelSha, String businessKey, String correlationId) {
        var changed = new AgentDispatchStore.Seed(binding, instanceId, seed.occurrenceId(), releaseKey, processDefinitionId,
                modelSha, seed.elementId(), seed.workerInputSha256(), businessKey, correlationId, seed.inputs());
        return new AgentDispatchStore.Intent(changed, NOW, NOW.plusSeconds(60), AgentDispatchStore.State.PENDING, null, null);
    }

    private static Fixture fixture() {
        var source = mock(DataSource.class);
        var manager = new DataSourceTransactionManager(source);
        var verifier = mockConstruction(ProcessApiSchemaVerifier.class);
        var templates = mockConstruction(JdbcTemplate.class);
        var store = new AgentDispatchStore(source, manager, "tianji_process");
        return new Fixture(source, templates.constructed().getFirst(), store, verifier, templates);
    }

    private static void rows(Fixture fixture, List<AgentDispatchStore.Intent> intents) {
        doAnswer(invocation -> {
            RowMapper<AgentDispatchStore.Intent> mapper = invocation.getArgument(1);
            var mapped = new ArrayList<AgentDispatchStore.Intent>();
            for (int index = 0; index < intents.size(); index++) mapped.add(mapper.mapRow(row(intents.get(index)), index));
            return mapped;
        }).when(fixture.jdbc()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AgentDispatchStore.Intent>>any(), any(Object[].class));
    }

    private static void corruptRow(Fixture fixture, AgentDispatchStore.Intent intent, String column) {
        doAnswer(invocation -> {
            RowMapper<AgentDispatchStore.Intent> mapper = invocation.getArgument(1);
            var result = row(intent);
            when(result.getString(column)).thenReturn("0".repeat(64));
            return List.of(mapper.mapRow(result, 0));
        }).when(fixture.jdbc()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AgentDispatchStore.Intent>>any(), any(Object[].class));
    }

    private static ResultSet row(AgentDispatchStore.Intent intent) throws Exception {
        var seed = intent.seed();
        var binding = seed.binding();
        var actor = binding.actor();
        var rs = mock(ResultSet.class);
        when(rs.getLong("tenant_id")).thenReturn(actor.tenantId());
        when(rs.getString("subject_id")).thenReturn(actor.subjectId());
        when(rs.getString("subject_issuer")).thenReturn(actor.issuerFingerprint());
        when(rs.getString("source_id")).thenReturn(binding.sourceId());
        when(rs.getString("topic")).thenReturn(binding.topic());
        when(rs.getString("schema_version")).thenReturn(binding.schemaVersion());
        when(rs.getString("agent_definition_key")).thenReturn(binding.definitionKey());
        when(rs.getLong("agent_definition_version")).thenReturn(binding.definitionVersion());
        when(rs.getString("agent_definition_sha256")).thenReturn(binding.definitionContentSha256());
        when(rs.getString("instruction")).thenReturn(binding.instruction());
        when(rs.getLong("submission_timeout_ms")).thenReturn(binding.submissionTimeout().toMillis());
        when(rs.getString("scope_id")).thenReturn(seed.instanceId());
        when(rs.getString("occurrence_id")).thenReturn(seed.occurrenceId());
        when(rs.getString("release_key")).thenReturn(seed.releaseKey());
        when(rs.getString("process_definition_id")).thenReturn(seed.processDefinitionId());
        when(rs.getString("process_model_sha256")).thenReturn(seed.processModelSha256());
        when(rs.getString("element_id")).thenReturn(seed.elementId());
        when(rs.getString("worker_input_sha256")).thenReturn(seed.workerInputSha256());
        when(rs.getString("business_key")).thenReturn(seed.businessKey());
        when(rs.getString("correlation_id")).thenReturn(seed.correlationId());
        when(rs.getString("input_data")).thenReturn("{\"amount\":7,\"question\":\"private task\"}");
        when(rs.getString("scope_type")).thenReturn("BPMN");
        when(rs.getString("seed_sha256")).thenReturn(seed.fingerprint());
        when(rs.getString("idempotency_key")).thenReturn(seed.idempotencyKey());
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(intent.createdAt()));
        when(rs.getTimestamp("dispatch_not_after")).thenReturn(Timestamp.from(intent.dispatchNotAfter()));
        when(rs.getString("state")).thenReturn(intent.state().name());
        when(rs.getString("run_id")).thenReturn(intent.runId());
        when(rs.getTimestamp("closed_at")).thenReturn(intent.closedAt() == null ? null : Timestamp.from(intent.closedAt()));
        return rs;
    }

    private record Fixture(DataSource source, JdbcTemplate jdbc, AgentDispatchStore store,
                           MockedConstruction<ProcessApiSchemaVerifier> verifier,
                           MockedConstruction<JdbcTemplate> templates) implements AutoCloseable {
        void begin() {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.bindResource(source, new ConnectionHolder(mock(Connection.class)));
        }
        @Override public void close() {
            if (TransactionSynchronizationManager.hasResource(source)) TransactionSynchronizationManager.unbindResource(source);
            TransactionSynchronizationManager.setActualTransactionActive(false);
            templates.close();
            verifier.close();
        }
    }
}
