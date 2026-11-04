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
import top.egon.cola.platform.tianji.process.server.identity.ApprovalVerificationContext;
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

    @Test void approval_lookup_uses_only_the_authenticated_service_namespace_and_full_applied_source_joins() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store(true); var result = result(context("complete")); var consumer = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "verifier", "https://idp.example/20");
            var context = new ApprovalVerificationContext(consumer, NOW.plusSeconds(30));
            when(fixture.jdbc.get().query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Entry>>any(), any(Object[].class))).thenReturn(List.of(result));
            assertEquals(result, fixture.transaction.execute(status -> store.approval(context, result.receipt().approvalReceiptId()).orElseThrow()));
            verify(fixture.jdbc.get()).query(argThat(sql -> sql.contains("a.state='APPLIED'") && sql.contains("s.state='APPLIED'") && sql.contains("s.request_sha256=h.start_request_sha256")
                            && sql.contains("d.model_sha256=h.definition_sha256") && sql.contains("h.tenant_id=? AND h.subject_issuer=? AND h.approval_receipt_id=?")
                            && sql.contains("h.command_type='TASK_COMPLETE' AND h.task_kind='APPROVAL'")),
                    org.mockito.ArgumentMatchers.<RowMapper<Entry>>any(), eq(20L), eq(consumer.issuerFingerprint()), eq(result.receipt().approvalReceiptId()));
            assertThrows(IllegalStateException.class, () -> store.approval(context, result.receipt().approvalReceiptId()));
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

    @Test void case_receipt_writer_preserves_owner_start_kind_and_scope_instead_of_service_executor() throws Exception {
        try (var f = new Fixture()) {
            var task = new HumanTaskSnapshotTest.CaseFixture(); var provenance = mock(top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore.class);
            when(provenance.usesDataSource(f.source)).thenReturn(true); when(provenance.find(20,"case-1")).thenReturn(Optional.of(task.provenance));
            var store = new HumanTaskReceiptStore(f.source,"tianji_process",provenance); store.verifyStorage();
            var context = HumanTaskSnapshotTest.caseContext("complete",Set.of("7")); var result = caseEntry(task,context);
            when(f.commands.get().lock(ACTOR,Kind.TASK_COMPLETE,"case-complete")).thenReturn(new Receipt(HASH,"release","correlation","PENDING",null,NOW,null));
            when(f.jdbc.get().update(anyString(),any(Object[].class))).thenReturn(1);
            assertDoesNotThrow(()->f.transaction.executeWithoutResult(status->store.apply(context,result)));
            var values = org.mockito.ArgumentCaptor.forClass(Object[].class); verify(f.jdbc.get()).update(startsWith("INSERT INTO"),values.capture());
            assertEquals("CMMN",values.getValue()[10]); assertEquals("case-1",values.getValue()[8]);
            assertEquals(task.provenance.seed().owner().subjectId(),values.getValue()[23]); assertEquals("START",values.getValue()[24]); assertEquals("parent-start",values.getValue()[25]);
            assertNotEquals(task.provenance.seed().executor().subjectId(),values.getValue()[23]);
        }
    }

    @Test void case_replay_revalidates_persisted_owner_executor_provenance_after_base_receipt_discovery() throws Exception {
        try (var f = new Fixture()) {
            var task = new HumanTaskSnapshotTest.CaseFixture(); var provenance = mock(top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore.class);
            when(provenance.usesDataSource(f.source)).thenReturn(true); when(provenance.find(20,"case-1")).thenReturn(Optional.of(task.provenance));
            var store = new HumanTaskReceiptStore(f.source,"tianji_process",provenance); store.verifyStorage();
            var context = HumanTaskSnapshotTest.caseContext("complete",Set.of("8")); var result = caseEntry(task,HumanTaskSnapshotTest.caseContext("complete",Set.of("7")));
            when(f.commands.get().lockIfPresent(ACTOR,Kind.TASK_COMPLETE,"case-complete")).thenReturn(Optional.of(new Receipt(HASH,"release","correlation","APPLIED","case-task",NOW,null)));
            when(f.jdbc.get().query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<Entry>>any(),any(Object[].class))).thenReturn(List.of(result));
            assertEquals(result,f.transaction.execute(s->store.lockCommand(context,HumanTaskActionEnum.COMPLETE,"case-complete",HASH).orElseThrow()));
            verify(provenance,atLeastOnce()).find(20,"case-1");
            when(provenance.find(20,"case-1")).thenReturn(Optional.empty());
            assertThrows(IllegalStateException.class,()->f.transaction.execute(s->store.lockCommand(context,HumanTaskActionEnum.COMPLETE,"case-complete",HASH)));
        }
    }

    @Test void actual_case_receipt_mapper_uses_cmmn_scope_and_the_independent_parent_start() throws Exception {
        try (var f = new Fixture()) {
            var task = new HumanTaskSnapshotTest.CaseFixture(); var provenance = mock(top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore.class);
            when(provenance.usesDataSource(f.source)).thenReturn(true); when(provenance.find(20,"case-1")).thenReturn(Optional.of(task.provenance));
            var store = new HumanTaskReceiptStore(f.source,"tianji_process",provenance); store.verifyStorage(); var context=HumanTaskSnapshotTest.caseContext("complete",Set.of("7"));
            var expected=caseEntry(task,context); var row = new java.util.HashMap<String,Object>();
            row.put("tenant_id",20L);row.put("subject_id",ACTOR.subjectId());row.put("subject_issuer",ACTOR.issuerFingerprint());row.put("requester_type","USER");row.put("requester_id",task.provenance.seed().owner().subjectId());row.put("requester_issuer",task.provenance.seed().owner().issuerFingerprint());
            row.put("command_type","TASK_COMPLETE");row.put("command_id","case-complete");row.put("task_id","case-task");row.put("process_instance_id","case-1");row.put("release_key","release");row.put("definition_key","governedCase");row.put("task_definition_key","primaryTask");row.put("task_kind","ACTION");row.put("lane_key","operator");
            row.put("previous_version",HASH);row.put("resulting_version",null);row.put("approval_receipt_id",null);row.put("approved",null);row.put("occurred_at",Timestamp.from(NOW));row.put("start_command_id","parent-start");row.put("start_command_type","START");row.put("start_request_sha256",task.provenance.seed().ownerStartRequestSha256());row.put("correlation_id","correlation");row.put("source_created_at",Timestamp.from(NOW));
            row.put("model_type","CMMN");row.put("definition_sha256",task.definition.modelSha256());row.put("engine_definition_id","case-definition");row.put("authorized_role_id",7L);row.put("auth_version",2L);row.put("policy_version",3L);row.put("previous_assignee",context.userKey());row.put("resulting_assignee",null);row.put("request_sha256",HASH);
            when(f.commands.get().lockIfPresent(ACTOR,Kind.TASK_COMPLETE,"case-complete")).thenReturn(Optional.of(new Receipt(HASH,"release","correlation","APPLIED","case-task",NOW,null)));
            when(f.jdbc.get().query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<Entry>>any(),any(Object[].class))).thenAnswer(i->{
                var rs=mock(java.sql.ResultSet.class);when(rs.getString(anyString())).thenAnswer(c->{var value=row.get(c.getArgument(0));return value==null?null:value.toString();});when(rs.getLong(anyString())).thenAnswer(c->((Number)row.get(c.getArgument(0))).longValue());when(rs.getObject(anyString())).thenAnswer(c->row.get(c.getArgument(0)));when(rs.getTimestamp(anyString())).thenAnswer(c->row.get(c.getArgument(0)));RowMapper<Entry> mapper=i.getArgument(1);return List.of(mapper.mapRow(rs,0));
            });
            assertEquals(expected,assertDoesNotThrow(()->f.transaction.execute(s->store.lockCommand(context,HumanTaskActionEnum.COMPLETE,"case-complete",HASH).orElseThrow())));
            row.put("start_command_type","CASE_START");assertThrows(IllegalStateException.class,()->f.transaction.execute(s->store.lockCommand(context,HumanTaskActionEnum.COMPLETE,"case-complete",HASH)));
        }
    }

    private static Entry caseEntry(HumanTaskSnapshotTest.CaseFixture f,HumanTaskContext context) {
        var receipt=new HumanTaskActionReceiptDTO("case-complete","case-task",HumanTaskActionEnum.COMPLETE,HumanTaskScopeTypeEnum.CASE,"case-1","release","governedCase","primaryTask",HumanTaskKindEnum.ACTION,"operator",HASH,null,null,null,NOW);
        var seed=f.provenance.seed(); var source=new TaskOrigin(seed.owner(),seed.ownerStartCommandId(),seed.ownerStartRequestSha256(),seed.releaseKey(),seed.correlationId(),seed.caseInstanceId(),f.provenance.createdAt());
        return new Entry(context.actor(),HASH,receipt,source,f.definition.modelSha256(),"case-definition","7",2,3,context.userKey(),null,f.provenance);
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
