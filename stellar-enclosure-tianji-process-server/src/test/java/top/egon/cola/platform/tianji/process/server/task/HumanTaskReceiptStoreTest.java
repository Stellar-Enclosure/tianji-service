package top.egon.cola.platform.tianji.process.server.task;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.Kind;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.Receipt;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskActionResult.Entry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HumanTaskReceiptStoreTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final String HASH = "a".repeat(64);
    private static final ProcessActor ACTOR = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "reviewer", "https://idp.example/20");
    private static final ProcessActor STARTER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "applicant", "https://idp.example/20");
    private static final TaskOrigin ORIGIN = new TaskOrigin(STARTER, "start", HASH, "release", "correlation", "instance", NOW.minusSeconds(10));

    @Test void unverified_storage_and_missing_owned_source_transaction_fail_before_command_io() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store(false); var context = context("claim");
            assertThrows(IllegalStateException.class, () -> fixture.transaction.execute(status -> store.lockCommand(context, HumanTaskActionEnum.CLAIM, "cmd", HASH)));
            store.verifyStorage(); assertThrows(IllegalStateException.class, () -> store.lockCommand(context, HumanTaskActionEnum.CLAIM, "cmd", HASH));
            verifyNoInteractions(fixture.commands.get()); verify(fixture.jdbc.get(), never()).queryForObject(anyString(), eq(Boolean.class));
        }
    }

    @Test void the_first_command_lock_is_stable_across_body_changes_and_deadlines_are_rechecked_after_waiting() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store(true); var context = context("claim"); var keys = new ArrayList<Long>();
            doAnswer(invocation -> { keys.add(invocation.getArgument(2)); return null; }).when(fixture.jdbc.get()).query(eq("SELECT pg_advisory_xact_lock(?)"), any(RowCallbackHandler.class), any(Long.class));
            when(fixture.commands.get().lockIfPresent(any(), any(), anyString())).thenReturn(Optional.empty());
            assertTrue(fixture.transaction.execute(status -> store.lockCommand(context, HumanTaskActionEnum.CLAIM, "cmd", HASH)).isEmpty());
            assertTrue(fixture.transaction.execute(status -> store.lockCommand(context, HumanTaskActionEnum.CLAIM, "cmd", "b".repeat(64))).isEmpty());
            assertEquals(2, keys.size()); assertEquals(keys.get(0), keys.get(1));
            clearInvocations(fixture.commands.get());
            when(fixture.jdbc.get().queryForObject(eq("SELECT clock_timestamp()"), eq(Timestamp.class))).thenReturn(Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(31)));
            assertThrows(IllegalStateException.class, () -> fixture.transaction.execute(status -> store.lockCommand(context, HumanTaskActionEnum.CLAIM, "cmd", HASH)));
            verifyNoInteractions(fixture.commands.get()); verify(fixture.connection).rollback();
        }
    }

    @Test void replay_requires_the_own_applied_command_and_exact_durable_start_and_definition_joins() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store(true); var context = context("complete"); var result = result(context);
            when(fixture.commands.get().lockIfPresent(ACTOR, Kind.TASK_COMPLETE, "cmd")).thenReturn(Optional.of(base("APPLIED", HASH)));
            when(fixture.jdbc.get().query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Entry>>any(), any(Object[].class))).thenReturn(List.of(result));
            assertEquals(result, fixture.transaction.execute(status -> store.lockCommand(context, HumanTaskActionEnum.COMPLETE, "cmd", HASH).orElseThrow()));
            verify(fixture.jdbc.get()).query(argThat(sql -> sql.contains("a.state='APPLIED'") && sql.contains("a.request_sha256=h.request_sha256")
                            && sql.contains("s.state='APPLIED'") && sql.contains("s.request_sha256=h.start_request_sha256") && sql.contains("s.resource_id=h.process_instance_id")
                            && sql.contains("d.model_sha256=h.definition_sha256") && sql.contains("d.engine_definition_id=h.engine_definition_id") && sql.contains("h.subject_issuer=?")),
                    org.mockito.ArgumentMatchers.<RowMapper<Entry>>any(), eq(20L), eq(ACTOR.issuerFingerprint()), eq(ACTOR.subjectId()), eq("TASK_COMPLETE"), eq("cmd"));
            clearInvocations(fixture.jdbc.get());
            assertThrows(ProcessConflictException.class, () -> fixture.transaction.execute(status -> store.lockCommand(context, HumanTaskActionEnum.COMPLETE, "cmd", "b".repeat(64))));
            verify(fixture.jdbc.get(), never()).query(contains("SELECT h.*"), org.mockito.ArgumentMatchers.<RowMapper<Entry>>any(), any(Object[].class));
            when(fixture.commands.get().lockIfPresent(ACTOR, Kind.TASK_COMPLETE, "cmd")).thenReturn(Optional.of(base("PENDING", HASH)));
            assertThrows(IllegalStateException.class, () -> fixture.transaction.execute(status -> store.lockCommand(context, HumanTaskActionEnum.COMPLETE, "cmd", HASH)));
        }
    }

    @Test void reference_insert_precedes_base_applied_and_any_insert_failure_rolls_back_the_owner_transaction() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store(true); var context = context("complete"); var result = result(context);
            when(fixture.commands.get().lock(ACTOR, Kind.TASK_COMPLETE, "cmd")).thenReturn(base("PENDING", HASH));
            when(fixture.jdbc.get().update(anyString(), any(Object[].class))).thenReturn(1);
            fixture.transaction.executeWithoutResult(status -> store.apply(context, result));
            var order = inOrder(fixture.jdbc.get(), fixture.commands.get());
            order.verify(fixture.jdbc.get()).update(startsWith("INSERT INTO \"tianji_process\".tianji_human_task_receipt"), any(Object[].class));
            order.verify(fixture.commands.get()).apply(ACTOR, Kind.TASK_COMPLETE, "cmd", "task", null);
            clearInvocations(fixture.commands.get());
            when(fixture.jdbc.get().update(anyString(), any(Object[].class))).thenReturn(0);
            assertThrows(IllegalStateException.class, () -> fixture.transaction.executeWithoutResult(status -> store.apply(context, result)));
            verify(fixture.commands.get(), never()).apply(any(), any(), anyString(), anyString(), any()); verify(fixture.connection).rollback();
        }
    }

    @Test void mismatched_operation_or_current_role_cannot_apply_an_old_authorization_context() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store(true); var context = context("complete"); var result = result(context);
            assertThrows(IllegalStateException.class, () -> fixture.transaction.executeWithoutResult(status -> store.apply(context("claim"), result)));
            var lostRole = new HumanTaskContext(ACTOR, context.resourceKey(), Set.of(), context.deadlineAt(), context.authVersion(), context.policyVersion());
            assertThrows(IllegalStateException.class, () -> fixture.transaction.executeWithoutResult(status -> store.apply(lostRole, result)));
            verify(fixture.commands.get(), never()).lock(any(), any(), anyString());
        }
    }

    private static HumanTaskContext context(String operation) { return new HumanTaskContext(ACTOR, "tianji.api.process.task." + operation, Set.of("50"), NOW.plusSeconds(30), 2, 3); }
    private static Receipt base(String state, String hash) { return new Receipt(hash, "release", "correlation", state, "APPLIED".equals(state) ? "task" : null, NOW, null); }
    private static Entry result(HumanTaskContext context) {
        var receipt = new HumanTaskActionReceiptDTO("cmd", "task", HumanTaskActionEnum.COMPLETE, HumanTaskScopeTypeEnum.PROCESS, "instance", "release", "workflow", "review", HumanTaskKindEnum.APPROVAL,
                "finance", HASH, null, "approval-" + HASH, false, NOW);
        return new Entry(ACTOR, HASH, receipt, ORIGIN, HASH, "definition", "50", 2, 3, context.userKey(), null);
    }
    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class); final Connection connection = mock(Connection.class);
        final AtomicReference<JdbcTemplate> jdbc = new AtomicReference<>(); final AtomicReference<ProcessCommandReceiptStore> commands = new AtomicReference<>();
        final org.mockito.MockedConstruction<JdbcTemplate> statements; final org.mockito.MockedConstruction<ProcessCommandReceiptStore> receipts;
        final org.mockito.MockedConstruction<HumanTaskStorageVerifier> verifier; final TransactionTemplate transaction;
        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
            statements = mockConstruction(JdbcTemplate.class, (value, ignored) -> { jdbc.set(value);
                when(value.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class))).thenReturn(true);
                when(value.queryForObject(eq("SELECT clock_timestamp()"), eq(Timestamp.class))).thenReturn(Timestamp.from(NOW)); });
            receipts = mockConstruction(ProcessCommandReceiptStore.class, (value, ignored) -> commands.set(value)); verifier = mockConstruction(HumanTaskStorageVerifier.class);
            transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        }
        HumanTaskReceiptStore store(boolean verified) { var store = new HumanTaskReceiptStore(source, "tianji_process"); if (verified) store.verifyStorage(); return store; }
        @Override public void close() { verifier.close(); receipts.close(); statements.close(); }
    }
}
