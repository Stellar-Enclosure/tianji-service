package top.egon.cola.platform.tianji.process.server.task;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.ClaimHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetHumanTaskQuery;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskSelectionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ListHumanTasksQuery;
import top.egon.cola.platform.tianji.contract.orchestration.TransferHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.UnclaimHumanTaskCommand;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Release;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskActionResult.Entry;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native markers and real domain/result construction, without starting an engine or a database. */
class FlowableHumanTaskServiceTest {
    private static final String COMPLETION_TIMER = "tianji.process.human.task.completion.duration";
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final String HASH = "a".repeat(64);
    private static final ProcessActor ACTOR = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "reviewer", "https://idp.example/20");
    private static final ProcessActor STARTER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "applicant", "https://idp.example/20");
    private static final TaskOrigin ORIGIN = new TaskOrigin(STARTER, "start", HASH, "release", "correlation", "instance", NOW);

    @Test
    void observes_only_fresh_committed_task_completion() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (var fixture = new Fixture(HumanTaskCompletionObservation.enabled(() -> registry))) {
            when(fixture.task.getCreateTime()).thenReturn(Date.from(NOW.minusSeconds(15)));
            when(fixture.task.getDueDate()).thenReturn(Date.from(NOW));
            var claim = context("claim", Set.of("50"));
            fixture.service.claim(claim, new ClaimHumanTaskCommand("claim", "task", fixture.version()));
            assertTrue(registry.getMeters().isEmpty());
            var complete = context("complete", Set.of("50"));
            var command = new CompleteHumanTaskCommand("complete", "task", fixture.version(), Map.of("approved", false, "comment", "example"));
            doAnswer(call -> {
                assertTrue(registry.getMeters().isEmpty());
                fixture.last.set(call.getArgument(1));
                return null;
            }).when(fixture.receipts).apply(eq(complete), any());
            doAnswer(call -> { assertTrue(registry.getMeters().isEmpty()); return null; }).when(fixture.connection).commit();
            var receipt = fixture.service.complete(complete, command);
            assertEquals(NOW, receipt.occurredAt());
            assertEquals(1, registry.getMeters().size());
            var timer = registry.get(COMPLETION_TIMER).tags("scope", "process", "due", "on_time").timer();
            assertEquals(1, timer.count());
            assertEquals(15, timer.totalTime(TimeUnit.SECONDS));
            assertEquals(Set.of("scope", "due"), timer.getId().getTags().stream()
                    .map(io.micrometer.core.instrument.Tag::getKey).collect(java.util.stream.Collectors.toSet()));

            clearInvocations(fixture.connection);
            doNothing().when(fixture.connection).commit();
            when(fixture.receipts.lockCommand(any(), eq(HumanTaskActionEnum.COMPLETE), eq("complete"), anyString()))
                    .thenReturn(Optional.of(fixture.last.get()));
            assertEquals(receipt, fixture.service.complete(complete, command));
            verify(fixture.tasks, times(1)).complete(eq("task"), anyString(), anyMap());
            assertEquals(1, timer.count());
        }
    }

    @Test
    void classifies_only_fixed_due_postures_and_never_emits_invalid_or_rolled_back_times() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (var fixture = new Fixture()) {
            var observation = HumanTaskCompletionObservation.enabled(() -> registry);
            var transaction = new TransactionTemplate(fixture.manager);
            observation.recordOnCommit(HumanTaskScopeTypeEnum.PROCESS, NOW, NOW, NOW);
            assertTrue(registry.getMeters().isEmpty());
            transaction.executeWithoutResult(status -> {
                observation.recordOnCommit(HumanTaskScopeTypeEnum.PROCESS, NOW.minusSeconds(3), NOW.minusNanos(1), NOW);
                observation.recordOnCommit(HumanTaskScopeTypeEnum.CASE, NOW, null, NOW);
                observation.recordOnCommit(HumanTaskScopeTypeEnum.PROCESS, NOW.plusNanos(1), NOW, NOW);
                observation.recordOnCommit(HumanTaskScopeTypeEnum.PROCESS, Instant.EPOCH,
                        null, Instant.parse("9999-01-01T00:00:00Z"));
                assertTrue(registry.getMeters().isEmpty());
            });
            assertEquals(2, registry.getMeters().size());
            var late = registry.get(COMPLETION_TIMER).tags("scope", "process", "due", "late").timer();
            var unscheduled = registry.get(COMPLETION_TIMER).tags("scope", "case", "due", "unscheduled").timer();
            assertEquals(1, late.count());
            assertEquals(3, late.totalTime(TimeUnit.SECONDS));
            assertEquals(1, unscheduled.count());
            assertEquals(0, unscheduled.totalTime(TimeUnit.NANOSECONDS));
            transaction.executeWithoutResult(status -> {
                observation.recordOnCommit(HumanTaskScopeTypeEnum.PROCESS, NOW, NOW, NOW);
                status.setRollbackOnly();
            });
            verify(fixture.connection).rollback();
            assertEquals(2, registry.getMeters().size());
        }
    }

    @Test
    void failed_commit_and_unavailable_registry_do_not_claim_a_sample() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (var fixture = new Fixture(HumanTaskCompletionObservation.enabled(() -> registry))) {
            var complete = context("complete", Set.of("50"));
            fixture.assignee.set(complete.userKey());
            var command = new CompleteHumanTaskCommand("complete", "task", fixture.version(), Map.of("approved", true, "comment", "example"));
            doThrow(new java.sql.SQLException("private commit failure")).when(fixture.connection).commit();
            assertThrows(org.springframework.transaction.TransactionException.class, () -> fixture.service.complete(complete, command));
            verify(fixture.receipts).apply(eq(complete), any());
            assertTrue(registry.getMeters().isEmpty());
        }
        var registryLookups = new AtomicInteger();
        var failing = HumanTaskCompletionObservation.enabled(() -> {
            registryLookups.incrementAndGet();
            throw new IllegalStateException("private registry failure");
        });
        try (var fixture = new Fixture(failing)) {
            var complete = context("complete", Set.of("50"));
            fixture.assignee.set(complete.userKey());
            var command = new CompleteHumanTaskCommand("complete", "task", fixture.version(), Map.of("approved", true, "comment", "example"));
            assertEquals(HumanTaskActionEnum.COMPLETE, fixture.service.complete(complete, command).action());
            verify(fixture.connection).commit();
            assertEquals(1, registryLookups.get());
        }
    }

    @Test void ambient_transactions_and_a_different_store_source_are_rejected_before_native_work() throws Exception {
        try (var fixture = new Fixture()) {
            var transaction = new TransactionTemplate(fixture.manager);
            assertThrows(IllegalStateException.class, () -> transaction.execute(status -> fixture.service.get(context("get", Set.of("50")), new GetHumanTaskQuery("task"))));
            assertThrows(IllegalStateException.class, () -> transaction.execute(status -> fixture.service.claim(context("claim", Set.of("50")), new ClaimHumanTaskCommand("cmd", "task", HASH))));
            verifyNoInteractions(fixture.tasks); verify(fixture.nativeAccess.get(), never()).find(any(), anyString(), anyBoolean());
            when(fixture.receipts.usesDataSource(fixture.source)).thenReturn(false);
            assertThrows(IllegalArgumentException.class, () -> new FlowableHumanTaskService(fixture.engine, fixture.dmn, new ProcessModelValidator(), fixture.receipts));
        }
    }

    @Test void ordinary_reads_apply_real_policy_and_an_empty_filtered_scan_keeps_its_cursor() throws Exception {
        try (var fixture = new Fixture()) {
            var value = fixture.service.get(context("get", Set.of("50")), new GetHumanTaskQuery("task"));
            assertEquals("approved", value.approvalField()); assertEquals(Set.of("approved", "comment"), value.formSchema().keySet());
            var query = new ListHumanTasksQuery(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.CANDIDATE, null, null, null, null, 1);
            when(fixture.nativeAccess.get().candidates(any(), eq(query), eq(1))).thenReturn(List.of("task"));
            var applicant = new HumanTaskContext(STARTER, "tianji.api.process.task.query", Set.of("50"), NOW.plusSeconds(30), 2, 3);
            var empty = fixture.service.query(applicant, query); assertEquals(List.of(), empty.items()); assertEquals("task", empty.nextAfterTaskId());
            var admin = new HumanTaskContext(ACTOR, "tianji.api.process.task.adminQuery", Set.of(), NOW.plusSeconds(30), 2, 3);
            assertEquals(1, fixture.service.query(admin, query).items().size());
            verify(fixture.task, never()).getProcessVariables(); verify(fixture.task, never()).getTaskLocalVariables();
            var caseQuery = new ListHumanTasksQuery(HumanTaskScopeTypeEnum.CASE, HumanTaskSelectionEnum.ALL, null, null, null, null, 1);
            assertThrows(IllegalArgumentException.class, () -> fixture.service.query(context("query", Set.of("50")), caseQuery));
        }
    }

    @Test void claim_then_false_approval_map_only_released_targets_and_replay_never_reexecutes_a_deleted_task() throws Exception {
        try (var fixture = new Fixture()) {
            var claim = context("claim", Set.of("50")); var before = fixture.version();
            var claimed = fixture.service.claim(claim, new ClaimHumanTaskCommand("claim", "task", before));
            assertEquals(before, claimed.previousVersion()); assertEquals(fixture.version(), claimed.resultingVersion()); assertEquals(claim.userKey(), fixture.assignee.get());
            var complete = context("complete", Set.of("50")); var command = new CompleteHumanTaskCommand("complete", "task", fixture.version(), Map.of("approved", false, "comment", "private form"));
            var result = fixture.service.complete(complete, command); assertEquals(Boolean.FALSE, result.approved()); assertNotNull(result.approvalReceiptId());
            verify(fixture.tasks).complete("task", complete.userKey(), Map.of("reviewApproved", false, "reviewComment", "private form"));
            var order = inOrder(fixture.receipts, fixture.nativeAccess.get(), fixture.tasks);
            order.verify(fixture.nativeAccess.get()).find(claim, "task", true); order.verify(fixture.receipts).reserve(eq(claim), eq(HumanTaskActionEnum.CLAIM), eq("claim"), anyString(), any());
            order.verify(fixture.tasks).claim("task", claim.userKey()); order.verify(fixture.receipts).apply(eq(claim), any());
            Entry applied = fixture.last.get(); clearInvocations(fixture.nativeAccess.get(), fixture.tasks);
            when(fixture.receipts.lockCommand(any(), eq(HumanTaskActionEnum.COMPLETE), eq("complete"), anyString())).thenAnswer(call -> {
                if (!applied.requestHash().equals(call.getArgument(3))) throw new ProcessConflictException(); return Optional.of(applied);
            });
            assertEquals(result, fixture.service.complete(context("complete", Set.of()), command));
            assertThrows(ProcessConflictException.class, () -> fixture.service.complete(complete, new CompleteHumanTaskCommand("complete", "task", command.expectedVersion(), Map.of("approved", true, "comment", "private form"))));
            verifyNoInteractions(fixture.nativeAccess.get(), fixture.tasks);
        }
    }

    @Test void a_legacy_unmanaged_task_is_skipped_without_becoming_governed_work() throws Exception {
        try (var fixture = new Fixture()) {
            when(fixture.task.getTaskDefinitionKey()).thenReturn("legacyReview");
            var query = new ListHumanTasksQuery(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.ALL, null, null, null, null, 1);
            when(fixture.nativeAccess.get().candidates(any(), eq(query), eq(1))).thenReturn(List.of("task"));
            var page = fixture.service.query(context("query", Set.of("50")), query); assertTrue(page.items().isEmpty()); assertEquals("task", page.nextAfterTaskId());
            assertThrows(ProcessResourceNotFoundException.class, () -> fixture.service.get(context("get", Set.of("50")), new GetHumanTaskQuery("task")));
            verify(fixture.tasks, never()).getIdentityLinksForTask(anyString()); verify(fixture.receipts, never()).reserve(any(), any(), anyString(), anyString(), any());
        }
    }

    @Test void access_version_and_form_failures_cannot_reserve_a_command_or_reach_native_mutation() throws Exception {
        try (var fixture = new Fixture()) {
            var valid = fixture.version();
            assertThrows(ProcessResourceNotFoundException.class, () -> fixture.service.claim(context("claim", Set.of("99")), new ClaimHumanTaskCommand("other", "task", HASH)));
            assertThrows(ProcessConflictException.class, () -> fixture.service.claim(context("claim", Set.of("50")), new ClaimHumanTaskCommand("stale", "task", HASH)));
            fixture.assignee.set(context("complete", Set.of("50")).userKey());
            assertThrows(IllegalArgumentException.class, () -> fixture.service.complete(context("complete", Set.of("50")), new CompleteHumanTaskCommand("form", "task", fixture.version(), Map.of("approved", "true", "comment", "private"))));
            assertNotNull(valid); verify(fixture.receipts, never()).reserve(any(), any(), anyString(), anyString(), any());
            verify(fixture.tasks, never()).claim(anyString(), anyString()); verify(fixture.tasks, never()).complete(anyString(), anyString(), anyMap());
        }
    }

    @Test void administrator_transfer_reloads_before_forced_revision_and_an_orphan_can_unclaim_without_its_old_role() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.assignee.set(context("unclaim", Set.of()).userKey());
            fixture.service.unclaim(context("unclaim", Set.of()), new UnclaimHumanTaskCommand("release", "task", fixture.version()));
            assertNull(fixture.assignee.get()); assertNull(fixture.last.get().authorizedRoleId());
            var result = fixture.service.transfer(context("transfer", Set.of()), new TransferHumanTaskCommand("transfer", "task", fixture.version(), "manager"));
            assertEquals("manager", result.lane()); assertEquals(Set.of("60"), fixture.roles); assertEquals(fixture.version(), result.resultingVersion());
            var order = inOrder(fixture.tasks, fixture.nativeAccess.get(), fixture.task);
            order.verify(fixture.tasks).addCandidateGroup("task", "role:60"); order.verify(fixture.nativeAccess.get()).find(argThat(ctx -> ctx.resourceKey().endsWith("transfer")), eq("task"), eq(true));
            order.verify(fixture.task).forceUpdate(); order.verify(fixture.tasks).saveTask(fixture.task);
        }
    }

    @Test void receipt_failure_and_native_race_roll_back_the_owned_transaction_without_a_success_result() throws Exception {
        try (var fixture = new Fixture()) {
            doThrow(new InjectedFailure()).when(fixture.receipts).apply(any(), any());
            assertThrows(InjectedFailure.class, () -> fixture.service.claim(context("claim", Set.of("50")), new ClaimHumanTaskCommand("cmd", "task", fixture.version())));
            verify(fixture.connection).rollback(); reset(fixture.receipts); when(fixture.receipts.current(any())).thenReturn(NOW); fixture.assignee.set(null);
            doThrow(new FlowableOptimisticLockingException("native race")).when(fixture.tasks).claim(anyString(), anyString());
            assertThrows(ProcessConflictException.class, () -> fixture.service.claim(context("claim", Set.of("50")), new ClaimHumanTaskCommand("race", "task", fixture.version())));
            verify(fixture.receipts, never()).apply(any(), any()); verify(fixture.connection, times(2)).rollback();
        }
    }

    @Test void complete_does_not_mint_a_receipt_if_the_native_task_still_exists() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.assignee.set(context("complete", Set.of("50")).userKey()); doNothing().when(fixture.tasks).complete(anyString(), anyString(), anyMap());
            assertThrows(IllegalStateException.class, () -> fixture.service.complete(context("complete", Set.of("50")), new CompleteHumanTaskCommand("cmd", "task", fixture.version(), Map.of("approved", true, "comment", "text"))));
            verify(fixture.receipts, never()).apply(any(), any()); verify(fixture.connection).rollback();
        }
    }

    @Test void case_queries_and_claim_completion_use_the_case_facade_after_shared_scope_lock() throws Exception {
        try(var f=new CaseFixture()) {
            var query=new ListHumanTasksQuery(HumanTaskScopeTypeEnum.CASE,HumanTaskSelectionEnum.CANDIDATE,null,null,null,null,5);
            var page=assertDoesNotThrow(()->f.service.query(HumanTaskSnapshotTest.caseContext("query",Set.of("7")),query));
            assertEquals(1,page.items().size()); assertEquals(HumanTaskScopeTypeEnum.CASE,page.items().getFirst().scopeType());
            var claim=HumanTaskSnapshotTest.caseContext("claim",Set.of("7"));
            var result=assertDoesNotThrow(()->f.service.claim(claim,new ClaimHumanTaskCommand("claim","case-task",f.facts.snapshot().version())));
            assertEquals(HumanTaskScopeTypeEnum.CASE,result.scopeType()); assertEquals(claim.userKey(),f.facts.assignee.get());
            var order=inOrder(f.provenance,f.access.get(),f.tasks);
            order.verify(f.provenance).lockScope(20,"case-1"); order.verify(f.access.get()).findCase(claim,"case-task",true); order.verify(f.tasks).claim("case-task",claim.userKey());
            var complete=HumanTaskSnapshotTest.caseContext("complete",Set.of("8"));
            var completed=f.service.complete(complete,new CompleteHumanTaskCommand("complete","case-task",f.facts.snapshot().version(),Map.of()));
            assertEquals(HumanTaskScopeTypeEnum.CASE,completed.scopeType()); assertNull(completed.approvalReceiptId());
            verify(f.tasks).complete("case-task",complete.userKey(),Map.of()); verifyNoInteractions(f.processTasks);
            assertEquals(f.facts.provenance,f.last.get().caseProvenance());
        }
    }

    @Test void case_replay_requires_fresh_any_lane_role_and_never_uses_deleted_native_task() throws Exception {
        try(var f=new CaseFixture()) {
            var context=HumanTaskSnapshotTest.caseContext("complete",Set.of("7")); f.facts.assignee.set(context.userKey());
            var command=new CompleteHumanTaskCommand("complete","case-task",assertDoesNotThrow(f.facts::snapshot).version(),Map.of());
            var result=assertDoesNotThrow(()->f.service.complete(context,command)); var stored=f.last.get();
            when(f.receipts.lockCommand(any(),eq(HumanTaskActionEnum.COMPLETE),eq("complete"),anyString())).thenReturn(Optional.of(stored));
            clearInvocations(f.access.get(),f.tasks,f.provenance);
            assertEquals(result,f.service.complete(HumanTaskSnapshotTest.caseContext("complete",Set.of("8")),command));
            verify(f.provenance).lockScope(20,"case-1"); verify(f.provenance,atLeastOnce()).find(20,"case-1"); verifyNoInteractions(f.access.get(),f.tasks);
            assertThrows(ProcessResourceNotFoundException.class,()->f.service.complete(HumanTaskSnapshotTest.caseContext("complete",Set.of()),command));
            var invalidHistoricalRole=new Entry(stored.actor(),stored.requestHash(),stored.receipt(),stored.origin(),stored.modelSha256(),stored.engineDefinitionId(),"99",stored.authVersion(),stored.policyVersion(),stored.previousAssignee(),stored.resultingAssignee(),stored.caseProvenance());
            when(f.receipts.lockCommand(any(),eq(HumanTaskActionEnum.COMPLETE),eq("complete"),anyString())).thenReturn(Optional.of(invalidHistoricalRole));
            assertThrows(IllegalStateException.class,()->f.service.complete(HumanTaskSnapshotTest.caseContext("complete",Set.of("8")),command));
        }
    }

    @Test void case_transfer_and_unclaim_keep_existing_self_and_dedicated_resource_rules() throws Exception {
        try(var f=new CaseFixture()) {
            var self=HumanTaskSnapshotTest.caseContext("unclaim",Set.of()); f.facts.assignee.set(self.userKey());
            assertDoesNotThrow(()->f.service.unclaim(self,new UnclaimHumanTaskCommand("unclaim","case-task",f.facts.snapshot().version())));
            assertNull(f.facts.assignee.get());
            var transfer=HumanTaskSnapshotTest.caseContext("transfer",Set.of());
            var result=f.service.transfer(transfer,new TransferHumanTaskCommand("transfer","case-task",f.facts.snapshot().version(),"operator"));
            assertEquals("operator",result.lane()); verify(f.tasks).deleteGroupIdentityLink("case-task","role:7","candidate"); verify(f.tasks).addGroupIdentityLink("case-task","role:8","candidate");
            verify(f.facts.task).forceUpdate(); verify(f.tasks).saveTask(f.facts.task);
            assertThrows(IllegalArgumentException.class,()->f.service.transfer(transfer,new TransferHumanTaskCommand("other","case-task",f.facts.snapshot().version(),"unreleased")));
        }
    }

    @Test void case_form_or_other_assignee_and_source_realm_fail_without_native_effects() throws Exception {
        try(var f=new CaseFixture()) {
            var context=HumanTaskSnapshotTest.caseContext("complete",Set.of("7")); f.facts.assignee.set(context.userKey());
            assertThrows(IllegalArgumentException.class,()->f.service.complete(context,new CompleteHumanTaskCommand("form","case-task",f.facts.snapshot().version(),Map.of("approved",true))));
            f.facts.assignee.set("user-"+"f".repeat(64));
            assertThrows(ProcessResourceNotFoundException.class,()->f.service.complete(context,new CompleteHumanTaskCommand("other","case-task",f.facts.snapshot().version(),Map.of())));
            verify(f.receipts,never()).reserve(any(),any(),anyString(),anyString(),any());verify(f.tasks,never()).complete(anyString(),anyString(),anyMap());
        }
    }

    @Test void case_completion_rejects_a_physical_task_hidden_by_the_active_affinity_filter() throws Exception {
        try (var f = new CaseFixture()) {
            var context = HumanTaskSnapshotTest.caseContext("complete", Set.of("7")); f.facts.assignee.set(context.userKey());
            var command = new CompleteHumanTaskCommand("complete", "case-task", f.facts.snapshot().version(), Map.of());
            // The native operation clears the active-affinity view but leaves its actual Task row behind.
            when(f.taskQuery.singleResult()).thenReturn(f.facts.task);
            assertThrows(IllegalStateException.class, () -> f.service.complete(context, command));
            assertFalse(f.alive.get()); verify(f.tasks).complete("case-task", context.userKey(), Map.of());
            verify(f.connection).rollback(); verify(f.receipts, never()).apply(any(), any());
        }
    }

    private static final class CaseFixture implements AutoCloseable {
        final HumanTaskSnapshotTest.CaseFixture facts=new HumanTaskSnapshotTest.CaseFixture();
        final DataSource source=mock(DataSource.class); final Connection connection=mock(Connection.class);
        final SpringProcessEngineConfiguration engine=mock(SpringProcessEngineConfiguration.class);final SpringDmnEngineConfiguration dmn=mock(SpringDmnEngineConfiguration.class);
        final org.flowable.cmmn.spring.SpringCmmnEngineConfiguration cmmn=mock(org.flowable.cmmn.spring.SpringCmmnEngineConfiguration.class);
        final org.flowable.cmmn.api.CmmnTaskService tasks=mock(org.flowable.cmmn.api.CmmnTaskService.class);final TaskService processTasks=mock(TaskService.class);
        final TaskQuery taskQuery=mock(TaskQuery.class,RETURNS_SELF);
        final top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore provenance=mock(top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore.class);
        final HumanTaskReceiptStore receipts=mock(HumanTaskReceiptStore.class); final AtomicReference<Entry> last=new AtomicReference<>();final AtomicBoolean alive=new AtomicBoolean(true);
        final AtomicReference<HumanTaskNativeAccess> access=new AtomicReference<>();final FlowableHumanTaskService service;
        final org.mockito.MockedConstruction<ProcessReleaseService> releases;final org.mockito.MockedConstruction<VerifiedProcessModels> models;final org.mockito.MockedConstruction<ProcessCommandReceiptStore> commands;final org.mockito.MockedConstruction<HumanTaskNativeAccess> natives;
        CaseFixture() throws Exception {
            when(source.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);var manager=new DataSourceTransactionManager(source);
            when(engine.getDataSource()).thenReturn(source);when(engine.getTransactionManager()).thenReturn(manager);when(engine.getDatabaseSchema()).thenReturn("tianji_process");when(engine.getTaskService()).thenReturn(processTasks);
            when(cmmn.getDataSource()).thenReturn(source);when(cmmn.getTransactionManager()).thenReturn(manager);when(cmmn.getDatabaseSchema()).thenReturn("tianji_process");when(cmmn.getCmmnTaskService()).thenReturn(tasks);
            when(provenance.usesDataSource(source)).thenReturn(true);when(provenance.find(20,"case-1")).thenReturn(Optional.of(facts.provenance));
            when(receipts.usesDataSource(source)).thenReturn(true);when(receipts.usesCaseProvenance(provenance)).thenReturn(true);when(receipts.current(any())).thenReturn(NOW);
            doAnswer(i->{last.set(i.getArgument(1));return null;}).when(receipts).apply(any(),any());
            when(tasks.createTaskQuery()).thenReturn(taskQuery); when(taskQuery.singleResult()).thenReturn(null);
            when(tasks.getIdentityLinksForTask("case-task")).thenAnswer(i->facts.links());
            doAnswer(i->{facts.assignee.set(i.getArgument(1));facts.revision.incrementAndGet();return null;}).when(tasks).claim(eq("case-task"),anyString());
            doAnswer(i->{facts.assignee.set(null);facts.revision.incrementAndGet();return null;}).when(tasks).unclaim("case-task");
            doAnswer(i->{facts.revision.incrementAndGet();return null;}).when(tasks).saveTask(facts.task);
            doAnswer(i->{alive.set(false);return null;}).when(tasks).complete(eq("case-task"),anyString(),anyMap());
            releases=mockConstruction(ProcessReleaseService.class,(value,ignored)->when(value.get(any(),eq("release"))).thenReturn(new Release("release",HASH,NOW,List.of(facts.definition))));
            models=mockConstruction(VerifiedProcessModels.class,(value,ignored)->when(value.caseModel(20,facts.definition)).thenReturn(facts.model));
            commands=mockConstruction(ProcessCommandReceiptStore.class);
            natives=mockConstruction(HumanTaskNativeAccess.class,(value,ignored)->{access.set(value);when(value.candidates(any(),any(),anyInt())).thenReturn(List.of("case-task"));when(value.findCase(any(),eq("case-task"),anyBoolean())).thenAnswer(i->alive.get()?Optional.of(facts.task):Optional.empty());});
            service=new FlowableHumanTaskService(engine,cmmn,dmn,new ProcessModelValidator(),receipts,provenance);
        }
        @Override public void close(){natives.close();commands.close();models.close();releases.close();}
    }

    private static HumanTaskContext context(String operation, Set<String> roles) { return new HumanTaskContext(ACTOR, "tianji.api.process.task." + operation, roles, NOW.plusSeconds(30), 2, 3); }
    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class); final Connection connection = mock(Connection.class); final TaskService tasks = mock(TaskService.class); final TaskEntity task = mock(TaskEntity.class);
        final SpringProcessEngineConfiguration engine = mock(SpringProcessEngineConfiguration.class); final SpringDmnEngineConfiguration dmn = mock(SpringDmnEngineConfiguration.class);
        final HumanTaskReceiptStore receipts = mock(HumanTaskReceiptStore.class); final AtomicReference<Entry> last = new AtomicReference<>();
        final AtomicReference<String> assignee = new AtomicReference<>(); final AtomicInteger revision = new AtomicInteger(1); final AtomicBoolean alive = new AtomicBoolean(true); final Set<String> roles = new HashSet<>(Set.of("50", "51"));
        final AtomicReference<HumanTaskNativeAccess> nativeAccess = new AtomicReference<>(); final DataSourceTransactionManager manager; final FlowableHumanTaskService service;
        final org.mockito.MockedConstruction<ProcessReleaseService> releases; final org.mockito.MockedConstruction<VerifiedProcessModels> models;
        final org.mockito.MockedConstruction<ProcessCommandReceiptStore> origins; final org.mockito.MockedConstruction<HumanTaskNativeAccess> nativeConstruction;
        final ValidatedProcessModel model; final Definition definition;
        Fixture() throws Exception {
            this(HumanTaskCompletionObservation.disabled());
        }

        Fixture(HumanTaskCompletionObservation completion) throws Exception {
            byte[] xml = """
                    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:task="urn:stellar-enclosure:tianji:task:1" targetNamespace="urn:test">
                      <process id="humanReview" isExecutable="true"><startEvent id="start"/><sequenceFlow id="toReview" sourceRef="start" targetRef="review"/>
                        <userTask id="review" name="Review" task:kind="APPROVAL" task:initialLane="finance" task:approvalVariable="reviewApproved"><extensionElements>
                          <task:taskLane key="finance" roleIds="50,51"/><task:taskLane key="manager" roleIds="60"/>
                          <task:taskField name="approved" target="reviewApproved" type="BOOLEAN"/><task:taskField name="comment" target="reviewComment" type="STRING"/>
                        </extensionElements></userTask><sequenceFlow id="toEnd" sourceRef="review" targetRef="end"/><endEvent id="end"/>
                      </process>
                    </definitions>
                    """.getBytes(StandardCharsets.UTF_8);
            model = new ProcessModelValidator().validateBpmn(xml, "humanReview"); definition = new Definition(Type.BPMN, "humanReview", "definition", "deployment", 1, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xml)), Map.of());
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true); manager = new DataSourceTransactionManager(source);
            when(engine.getTransactionManager()).thenReturn(manager); when(engine.getDatabaseSchema()).thenReturn("tianji_process"); when(engine.getTaskService()).thenReturn(tasks); when(receipts.usesDataSource(source)).thenReturn(true); when(receipts.current(any())).thenReturn(NOW);
            doAnswer(call -> { last.set(call.getArgument(1)); return null; }).when(receipts).apply(any(), any());
            when(task.getId()).thenReturn("task"); when(task.getTaskDefinitionKey()).thenReturn("review"); when(task.getProcessInstanceId()).thenReturn("instance"); when(task.getProcessDefinitionId()).thenReturn("definition");
            when(task.getTenantId()).thenReturn("20"); when(task.getCreateTime()).thenReturn(Date.from(NOW)); when(task.getRevision()).thenAnswer(call -> revision.get()); when(task.getAssignee()).thenAnswer(call -> assignee.get());
            when(tasks.getIdentityLinksForTask("task")).thenAnswer(call -> links());
            doAnswer(call -> { assignee.set(call.getArgument(1)); revision.incrementAndGet(); return null; }).when(tasks).claim(eq("task"), anyString());
            doAnswer(call -> { assignee.set(null); revision.incrementAndGet(); return null; }).when(tasks).unclaim("task");
            doAnswer(call -> { roles.remove(call.<String>getArgument(1).substring(5)); return null; }).when(tasks).deleteCandidateGroup(eq("task"), anyString());
            doAnswer(call -> { roles.add(call.<String>getArgument(1).substring(5)); return null; }).when(tasks).addCandidateGroup(eq("task"), anyString());
            doAnswer(call -> { revision.incrementAndGet(); return null; }).when(tasks).saveTask(task);
            doAnswer(call -> { alive.set(false); return null; }).when(tasks).complete(eq("task"), anyString(), anyMap());
            releases = mockConstruction(ProcessReleaseService.class, (value, ignored) -> when(value.get(any(), eq("release"))).thenReturn(new Release("release", HASH, NOW, List.of(definition))));
            models = mockConstruction(VerifiedProcessModels.class, (value, ignored) -> when(value.runningProcess(20, definition)).thenReturn(model));
            origins = mockConstruction(ProcessCommandReceiptStore.class, (value, ignored) -> when(value.taskOrigin(any(), eq("instance"))).thenReturn(ORIGIN));
            nativeConstruction = mockConstruction(HumanTaskNativeAccess.class, (value, ignored) -> { nativeAccess.set(value); when(value.find(any(), eq("task"), anyBoolean())).thenAnswer(call -> alive.get() ? Optional.of(task) : Optional.empty()); });
            service = new FlowableHumanTaskService(engine, null, dmn, new ProcessModelValidator(), receipts, null, completion);
        }
        String version() { return new HumanTaskSnapshot(task, links(), ORIGIN, definition, model).version(); }
        List<IdentityLink> links() { return roles.stream().sorted().map(role -> { var link = mock(IdentityLink.class); when(link.getTaskId()).thenReturn("task"); when(link.getType()).thenReturn("candidate"); when(link.getGroupId()).thenReturn("role:" + role); return link; }).toList(); }
        @Override public void close() { nativeConstruction.close(); origins.close(); models.close(); releases.close(); }
    }
    private static final class InjectedFailure extends RuntimeException { }
}
