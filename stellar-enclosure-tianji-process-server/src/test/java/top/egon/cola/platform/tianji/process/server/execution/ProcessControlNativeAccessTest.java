package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.common.engine.impl.context.Context;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.eventsubscription.api.EventSubscription;
import org.flowable.eventsubscription.api.EventSubscriptionQuery;
import org.flowable.job.api.ExternalWorkerJob;
import org.flowable.job.api.ExternalWorkerJobQuery;
import org.flowable.job.api.Job;
import org.flowable.job.api.TimerJobQuery;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessControlNativeAccessTest {
    private static final long TENANT = 20;
    private static final String TENANT_TEXT = "20";
    private static final String INSTANCE = "process-1";
    private static final String DEFINITION_ID = "definition-1";
    private static final String BUSINESS_KEY = "invoice-4711";
    private static final String MODEL_HASH = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-05T03:00:00.123456Z");
    private static final Instant FINISHED = Instant.parse("2026-11-05T02:59:59.999Z");

    @Test
    void public_native_api_and_fact_shapes_are_exact() throws Exception {
        assertEquals(ProcessControlNativeAccess.class,
                ProcessControlNativeAccess.class.getConstructor(DataSource.class, SpringProcessEngineConfiguration.class)
                        .getDeclaringClass());
        assertEquals(boolean.class, ProcessControlNativeAccess.class.getMethod("usesDataSource", DataSource.class).getReturnType());
        assertEquals(boolean.class, ProcessControlNativeAccess.class.getMethod("usesEngine", SpringProcessEngineConfiguration.class).getReturnType());
        assertEquals(ProcessControlNativeAccess.ControlFacts.class,
                ProcessControlNativeAccess.class.getMethod("observe", ProcessCommandReceiptStore.TaskOrigin.class,
                        Definition.class, ValidatedProcessModel.class).getReturnType());
        assertEquals(Optional.class,
                ProcessControlNativeAccess.class.getMethod("lockRoot", ProcessCommandReceiptStore.TaskOrigin.class).getReturnType());
        assertEquals(ProcessControlNativeAccess.TerminalFacts.class,
                ProcessControlNativeAccess.class.getMethod("terminalHistory", ProcessCommandReceiptStore.TaskOrigin.class,
                        Definition.class, String.class, boolean.class).getReturnType());
        assertEquals(Instant.class, ProcessControlNativeAccess.class.getMethod("now").getReturnType());
        assertRecord(ProcessControlNativeAccess.OccurrenceKey.class,
                List.of("executionId", "activityId"), List.of(String.class, String.class));
        assertRecord(ProcessControlNativeAccess.LockedRoot.class,
                List.of("tenantId", "instanceId", "engineDefinitionId", "businessKey", "revision", "suspensionState"),
                List.of(long.class, String.class, String.class, String.class, int.class, int.class));
        assertRecord(ProcessControlNativeAccess.TerminalFacts.class,
                List.of("tenantId", "instanceId", "engineDefinitionId", "businessKey", "status", "finishedAt", "deleteCategory"),
                List.of(long.class, String.class, String.class, String.class, ProcessInstanceStatusEnum.class,
                        Instant.class, ProcessControlNativeAccess.DeleteCategory.class));
        assertRecord(ProcessControlNativeAccess.ControlFacts.class,
                List.of("liveRoot", "terminal", "activeWaits"),
                List.of(ProcessControlNativeAccess.LockedRoot.class, ProcessControlNativeAccess.TerminalFacts.class, List.class));
        assertEquals(List.of("NORMAL_COMPLETION", "TIANJI_PROCESS_CANCELLED", "PRE_EXISTING_CANCELLATION"),
                Arrays.stream(ProcessControlNativeAccess.DeleteCategory.values()).map(Enum::name).toList());
    }

    @Test
    void constructor_requires_exact_engine_source_transaction_schema_services_and_history() {
        Fixture valid = new Fixture();
        ProcessControlNativeAccess access = new ProcessControlNativeAccess(valid.source, valid.engine);
        assertTrue(access.usesDataSource(valid.source));
        assertTrue(access.usesEngine(valid.engine));
        assertFalse(access.usesDataSource(mock(DataSource.class)));
        assertFalse(access.usesEngine(mock(SpringProcessEngineConfiguration.class)));

        Fixture wrongSource = new Fixture();
        DataSource foreignSource = mock(DataSource.class);
        when(wrongSource.engine.getDataSource()).thenReturn(foreignSource);
        assertThrows(IllegalArgumentException.class,
                () -> new ProcessControlNativeAccess(wrongSource.source, wrongSource.engine));
        Fixture wrongSchema = new Fixture();
        when(wrongSchema.engine.getDatabaseSchema()).thenReturn("bad-schema");
        assertThrows(IllegalArgumentException.class,
                () -> new ProcessControlNativeAccess(wrongSchema.source, wrongSchema.engine));
        Fixture asyncHistory = new Fixture();
        when(asyncHistory.engine.isAsyncHistoryEnabled()).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> new ProcessControlNativeAccess(asyncHistory.source, asyncHistory.engine));
        Fixture noManagement = new Fixture();
        when(noManagement.engine.getManagementService()).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
                () -> new ProcessControlNativeAccess(noManagement.source, noManagement.engine));
    }

    @Test
    void immutable_facts_reject_invalid_union_and_defensively_copy_canonical_waits() {
        var root = lockedRoot();
        var terminal = terminal(ProcessControlNativeAccess.DeleteCategory.NORMAL_COMPLETION);
        var waits = new ArrayList<>(List.of(
                new ProcessActiveWaitDTO("agentWait", ProcessActiveWaitKindEnum.AGENT_RESULT_WAIT, 1),
                new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 1)));
        var facts = new ProcessControlNativeAccess.ControlFacts(root, null, waits);
        waits.clear();
        assertEquals(2, facts.activeWaits().size());
        assertThrows(UnsupportedOperationException.class, () -> facts.activeWaits().clear());
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessControlNativeAccess.ControlFacts(null, null, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessControlNativeAccess.ControlFacts(root, terminal, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessControlNativeAccess.ControlFacts(null, terminal,
                                List.of(new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 1)))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessControlNativeAccess.OccurrenceKey("", "review")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessControlNativeAccess.LockedRoot(TENANT, INSTANCE, DEFINITION_ID,
                                BUSINESS_KEY, 0, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessControlNativeAccess.LockedRoot(TENANT, INSTANCE, DEFINITION_ID,
                                BUSINESS_KEY, 1, 3)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessControlNativeAccess.TerminalFacts(TENANT, INSTANCE, DEFINITION_ID,
                                BUSINESS_KEY, ProcessInstanceStatusEnum.ACTIVE, FINISHED,
                                ProcessControlNativeAccess.DeleteCategory.NORMAL_COMPLETION))
        );
    }

    @Test
    void database_now_requires_the_owned_transaction_and_returns_the_actual_instant() {
        Fixture fixture = new Fixture();
        assertThrows(IllegalStateException.class, fixture.access::now);
        fixture.jdbcReady();
        assertEquals(NOW, transaction(fixture.source, fixture.access::now));
    }

    @Test
    void live_zero_artifact_observation_returns_empty_waits_without_an_affinity_in_clause() {
        Fixture fixture = new Fixture();
        fixture.live();
        fixture.rootRows(rootRow("root", 7, 1));
        fixture.affinityRows();
        ProcessControlNativeAccess.ControlFacts facts = transaction(fixture.source,
                () -> fixture.access.observe(start(), definition(), fixture.model));
        assertNotNull(facts);
        assertAll(
                () -> assertNotNull(facts.liveRoot()),
                () -> assertNull(facts.terminal()),
                () -> assertTrue(facts.activeWaits().isEmpty())
        );
        assertTrue(fixture.sql.stream().noneMatch(value -> value.contains(" IN (")));
        verify(fixture.runtime, never()).createExecutionQuery();
    }

    @Test
    void artifact_projection_uses_four_bounded_queries_one_affinity_batch_and_deduplicates_occurrences() {
        Fixture fixture = new Fixture();
        fixture.live();
        fixture.rootRows(rootRow("root", 7, 1));
        Task firstTask = fixture.task("task-1", "exec-human", "review");
        Task duplicateTask = fixture.task("task-2", "exec-human", "review");
        ExternalWorkerJob external = fixture.external("job-1", "exec-worker", "worker");
        Job timer = fixture.timer("timer-1", "exec-timer", "deadline");
        EventSubscription agent = fixture.message("sub-1", "exec-agent", "agentWait");
        EventSubscription linkedCase = fixture.message("sub-2", "exec-case", "caseWait");
        when(fixture.taskQuery.listPage(0, 257)).thenReturn(List.of(firstTask, duplicateTask));
        when(fixture.externalQuery.listPage(0, 257)).thenReturn(List.of(external));
        when(fixture.timerQuery.listPage(0, 257)).thenReturn(List.of(timer));
        when(fixture.messageQuery.listPage(0, 257)).thenReturn(List.of(agent, linkedCase));
        ValidatedProcessModel.HumanTask humanPolicy = mock(ValidatedProcessModel.HumanTask.class);
        ValidatedProcessModel.WorkerTask workerPolicy = mock(ValidatedProcessModel.WorkerTask.class);
        ValidatedProcessModel.MessageWait messagePolicy = mock(ValidatedProcessModel.MessageWait.class);
        when(fixture.model.humanTasks()).thenReturn(Map.of("review", humanPolicy));
        when(fixture.model.workerTasks()).thenReturn(Map.of("worker", workerPolicy));
        when(fixture.model.timerWaits()).thenReturn(Map.of("deadline", Duration.ofMinutes(1)));
        when(fixture.model.messageWaits()).thenReturn(Map.of("agentWait", messagePolicy));
        when(fixture.model.caseSources()).thenReturn(Map.of("caseSource",
                new ValidatedProcessModel.CaseSource("caseDef", "caseWait", "CaseCompleted", "caseTimer", Duration.ofMinutes(5))));
        fixture.affinityRows(
                affinity("exec-human", "review"), affinity("exec-worker", "worker"),
                affinity("exec-timer", "deadline"), affinity("exec-agent", "agentWait"),
                affinity("exec-case", "caseWait"));

        ProcessControlNativeAccess.ControlFacts facts = transaction(fixture.source,
                () -> fixture.access.observe(start(), definition(), fixture.model));
        assertNotNull(facts);
        assertAll(
                () -> assertEquals(List.of(
                        new ProcessActiveWaitDTO("agentWait", ProcessActiveWaitKindEnum.AGENT_RESULT_WAIT, 1),
                        new ProcessActiveWaitDTO("caseWait", ProcessActiveWaitKindEnum.CASE_COMPLETION_WAIT, 1),
                        new ProcessActiveWaitDTO("worker", ProcessActiveWaitKindEnum.EXTERNAL_WORKER, 1),
                        new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 1),
                        new ProcessActiveWaitDTO("deadline", ProcessActiveWaitKindEnum.TIMER, 1)), facts.activeWaits()),
                () -> assertEquals(1, fixture.sql.stream().filter(value -> value.contains(" IN (")).count())
        );
        String affinitySql = fixture.sql.stream().filter(value -> value.contains(" IN (")).findFirst().orElseThrow();
        assertFalse(affinitySql.toUpperCase(java.util.Locale.ROOT).contains("FOR UPDATE"));
        assertEquals(5, fixture.arguments.get(affinitySql).stream()
                .filter(value -> value instanceof String text && text.startsWith("exec-")).count());
        verify(fixture.taskQuery).listPage(0, 257);
        verify(fixture.externalQuery).listPage(0, 257);
        verify(fixture.timerQuery).timers();
        verify(fixture.timerQuery).listPage(0, 257);
        verify(fixture.messageQuery).eventType("message");
        verify(fixture.messageQuery).listPage(0, 257);
        verify(fixture.runtime, never()).createExecutionQuery();
    }

    @Test
    void artifact_projection_rejects_overflow_missing_wrong_unknown_and_conflicting_affinity() {
        Fixture overflow = new Fixture();
        overflow.live();
        overflow.rootRows(rootRow("root", 1, 1));
        List<Task> overflowTasks = IntStream.range(0, 257)
                .mapToObj(index -> overflow.task("task-" + index, "exec-" + index, "review")).toList();
        ValidatedProcessModel.HumanTask overflowPolicy = mock(ValidatedProcessModel.HumanTask.class);
        when(overflow.taskQuery.listPage(0, 257)).thenReturn(overflowTasks);
        when(overflow.model.humanTasks()).thenReturn(Map.of("review", overflowPolicy));
        assertThrows(IllegalStateException.class,
                () -> transaction(overflow.source, () -> overflow.access.observe(start(), definition(), overflow.model)));

        Fixture missing = oneTask("review", "review");
        missing.affinityRows();
        assertThrows(IllegalStateException.class,
                () -> transaction(missing.source, () -> missing.access.observe(start(), definition(), missing.model)));

        Fixture wrong = oneTask("review", "review");
        wrong.affinityRows(affinity("exec-one", "different"));
        assertThrows(IllegalStateException.class,
                () -> transaction(wrong.source, () -> wrong.access.observe(start(), definition(), wrong.model)));

        Fixture unknown = oneTask("unknown", "review");
        unknown.affinityRows(affinity("exec-one", "unknown"));
        assertThrows(IllegalStateException.class,
                () -> transaction(unknown.source, () -> unknown.access.observe(start(), definition(), unknown.model)));

        Fixture conflict = oneTask("review", "review");
        ExternalWorkerJob conflictingJob = conflict.external("job", "exec-one", "review");
        ValidatedProcessModel.WorkerTask conflictingPolicy = mock(ValidatedProcessModel.WorkerTask.class);
        when(conflict.externalQuery.listPage(0, 257)).thenReturn(List.of(conflictingJob));
        when(conflict.model.workerTasks()).thenReturn(Map.of("review", conflictingPolicy));
        conflict.affinityRows(affinity("exec-one", "review"));
        assertThrows(IllegalStateException.class,
                () -> transaction(conflict.source, () -> conflict.access.observe(start(), definition(), conflict.model)));
    }

    @Test
    void valid_parallel_join_context_is_ignored_and_only_the_artifact_wait_is_returned() {
        Fixture fixture = oneTask("review", "review");
        fixture.affinityRows(affinity("exec-one", "review"));
        fixture.structuralRows(affinity("inactive-join-execution", "parallelJoin"));
        ProcessControlNativeAccess.ControlFacts facts = transaction(fixture.source,
                () -> fixture.access.observe(start(), definition(), fixture.model));
        assertNotNull(facts);
        assertEquals(List.of(new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 1)),
                facts.activeWaits());
        String sql = fixture.sql.stream().filter(value -> value.contains(" IN (")).findFirst().orElseThrow();
        assertTrue(fixture.arguments.get(sql).contains("exec-one"));
        assertFalse(fixture.arguments.get(sql).contains("inactive-join-execution"));
    }

    @Test
    void root_lock_is_the_only_for_update_and_maps_zero_one_or_ambiguous_rows() {
        Fixture present = new Fixture();
        present.lockedRows(rootRow("root", 11, 2));
        Optional<ProcessControlNativeAccess.LockedRoot> root = transaction(present.source,
                () -> present.access.lockRoot(start()));
        assertTrue(root.isPresent());
        assertEquals(new ProcessControlNativeAccess.LockedRoot(TENANT, INSTANCE, DEFINITION_ID,
                BUSINESS_KEY, 11, 2), root.orElseThrow());
        assertEquals(1, present.sql.stream().filter(value -> value.toUpperCase(java.util.Locale.ROOT)
                .contains("FOR UPDATE")).count());
        assertTrue(present.sql.getFirst().contains("parent_id_ IS NULL"));

        Fixture absent = new Fixture();
        absent.lockedRows();
        assertEquals(Optional.empty(), transaction(absent.source, () -> absent.access.lockRoot(start())));
        Fixture ambiguous = new Fixture();
        ambiguous.lockedRows(rootRow("root", 1, 1), rootRow("root", 1, 1));
        assertThrows(IllegalStateException.class,
                () -> transaction(ambiguous.source, () -> ambiguous.access.lockRoot(start())));
    }

    @Test
    void terminal_history_maps_normal_own_and_preexisting_categories_without_clock_ordering() {
        Fixture fixture = new Fixture();
        fixture.terminal(null, FINISHED, BUSINESS_KEY);
        ProcessControlNativeAccess.TerminalFacts normal = transaction(fixture.source,
                () -> fixture.access.terminalHistory(start(), definition(), BUSINESS_KEY, false));
        assertNotNull(normal);
        assertEquals(ProcessInstanceStatusEnum.COMPLETED, normal.status());
        assertEquals(ProcessControlNativeAccess.DeleteCategory.NORMAL_COMPLETION, normal.deleteCategory());

        fixture.terminal("TIANJI_PROCESS_CANCELLED", FINISHED, BUSINESS_KEY);
        ProcessControlNativeAccess.TerminalFacts own = transaction(fixture.source,
                () -> fixture.access.terminalHistory(start(), definition(), BUSINESS_KEY, true));
        assertNotNull(own);
        assertEquals(ProcessControlNativeAccess.DeleteCategory.TIANJI_PROCESS_CANCELLED, own.deleteCategory());
        assertEquals(FINISHED, own.finishedAt());

        fixture.terminal("external cancellation", FINISHED, BUSINESS_KEY);
        ProcessControlNativeAccess.TerminalFacts preexisting = transaction(fixture.source,
                () -> fixture.access.terminalHistory(start(), definition(), BUSINESS_KEY, false));
        assertNotNull(preexisting);
        assertEquals(ProcessControlNativeAccess.DeleteCategory.PRE_EXISTING_CANCELLATION,
                preexisting.deleteCategory());
        assertThrows(IllegalStateException.class, () -> transaction(fixture.source,
                () -> fixture.access.terminalHistory(start(), definition(), BUSINESS_KEY, true)));
        assertThrows(IllegalStateException.class, () -> transaction(fixture.source,
                () -> fixture.access.terminalHistory(start(), definition(), "different-business", false)));
    }

    @Test
    void terminal_observation_returns_terminal_union_and_skips_all_live_artifact_queries() {
        Fixture fixture = new Fixture();
        when(fixture.instanceQuery.singleResult()).thenReturn(null);
        fixture.terminal(null, FINISHED, BUSINESS_KEY);
        ProcessControlNativeAccess.ControlFacts facts = transaction(fixture.source,
                () -> fixture.access.observe(start(), definition(), fixture.model));
        assertNotNull(facts);
        assertAll(
                () -> assertNull(facts.liveRoot()),
                () -> assertNotNull(facts.terminal()),
                () -> assertTrue(facts.activeWaits().isEmpty())
        );
        verify(fixture.tasks, never()).createTaskQuery();
        verify(fixture.management, never()).createExternalWorkerJobQuery();
        verify(fixture.management, never()).createTimerJobQuery();
        verify(fixture.runtime, never()).createEventSubscriptionQuery();
    }

    @Test
    void locksOnlyExactOrdinaryDeadletter() {
        RecoveryFixture fixture = new RecoveryFixture();
        fixture.scopeMatch = false;
        var absent = assertDoesNotThrow(() -> transaction(fixture.base.source,
                () -> fixture.base.access.lockRecoveryDeadletter(start(), definition(), "foreign-job")));
        assertTrue(absent.isEmpty());
        String lock = fixture.lastStatement();
        assertTrue(lock.contains("id_=? AND tenant_id_=? AND process_instance_id_=? AND proc_def_id_=?"));
        assertTrue(lock.endsWith("AND type_='externalWorker' FOR UPDATE"));
        assertEquals(List.of("foreign-job", TENANT_TEXT, INSTANCE, DEFINITION_ID), fixture.base.arguments.get(lock));
        assertFalse(lock.contains("exception_msg_"));
        assertFalse(lock.contains("exception_stack_id_"));
        assertFalse(lock.contains("retries_"));
        assertFalse(lock.contains("lock_owner_"));
        assertFalse(lock.contains("lock_exp_time_"));
        fixture.scopeMatch = true;
        assertEquals(fixture.job, assertDoesNotThrow(() -> transaction(fixture.base.source,
                () -> fixture.base.access.lockRecoveryDeadletter(start(), definition(), "job-1"))).orElseThrow());
        var all = assertDoesNotThrow(() -> transaction(fixture.base.source,
                () -> fixture.base.access.findRecoveryDeadletters(start())));
        assertEquals(List.of(fixture.job), all);
        assertTrue(fixture.lastStatement().contains("WHERE tenant_id_=? AND process_instance_id_=?"));
        assertTrue(fixture.lastStatement().endsWith("ORDER BY id_ COLLATE \"C\" LIMIT 129"));
        assertEquals(List.of(TENANT_TEXT, INSTANCE), fixture.base.arguments.get(fixture.lastStatement()));
    }

    @Test
    void recoveryFailureComparisonAndCollisionAreBoundedAndDoNotReturnSecrets() {
        RecoveryFixture fixture = new RecoveryFixture();
        assertTrue(assertDoesNotThrow(() -> transaction(fixture.base.source,
                () -> fixture.base.access.recoveryFailureCodeMatches(fixture.job, "TEMP_ERROR"))));
        String compare = fixture.lastStatement();
        assertTrue(compare.startsWith("SELECT COALESCE(octet_length(exception_msg_) = octet_length(CAST(? AS text))"));
        assertTrue(compare.contains("exception_msg_ COLLATE \"C\" = CAST(? AS text) COLLATE \"C\""));
        assertEquals(List.of("TEMP_ERROR", "TEMP_ERROR", "job-1", TENANT_TEXT, INSTANCE, DEFINITION_ID, 2),
                fixture.base.arguments.get(compare));
        fixture.codeEqual = false;
        assertFalse(transaction(fixture.base.source,
                () -> fixture.base.access.recoveryFailureCodeMatches(fixture.job, "TEMP_ERROR")));
        assertFalse(transaction(fixture.base.source, () -> fixture.base.access.hasRecoveryCollision("job-1")));
        for (int collision = 1; collision < 5; collision++) {
            fixture.presence[collision] = true;
            assertTrue(transaction(fixture.base.source, () -> fixture.base.access.hasRecoveryCollision("job-1")));
            fixture.presence[collision] = false;
        }
        String probes = fixture.lastStatement();
        for (String name : List.of("deadletter_job", "external_job", "job", "timer_job", "suspended_job")) {
            assertTrue(probes.contains("act_ru_" + name + " WHERE id_=?"));
        }
        assertEquals(List.of("job-1", "job-1", "job-1", "job-1", "job-1"), fixture.base.arguments.get(probes));
    }

    @Test
    void requiresExactFlushedExternalReadback() {
        RecoveryFixture fixture = new RecoveryFixture();
        assertDoesNotThrow(() -> transaction(fixture.base.source, () -> {
            fixture.base.access.moveAndVerifyRecovery(fixture.job);
            return null;
        }));
        verify(fixture.base.management).moveDeadLetterJobToExecutableJob("job-1", 1);
        String readback = fixture.lastStatement();
        assertTrue(readback.contains("(lock_owner_ IS NULL AND lock_exp_time_ IS NULL) AS recovery_unlocked"));
        assertTrue(readback.endsWith("act_ru_external_job WHERE id_=?"));
        assertEquals(List.of("job-1"), fixture.base.arguments.get(readback));

        for (String fault : List.of("old", "missing", "colliding", "locked", "wrong-retries", "wrong-id", "wrong-topic",
                "bad-revision", "wrong-correlation", "wrong-schema", "nested-context", "read-only")) {
            RecoveryFixture failed = new RecoveryFixture();
            switch (fault) {
                case "old" -> failed.keepOld = true;
                case "missing" -> failed.readbackPresent = false;
                case "colliding" -> failed.presence[2] = true;
                case "locked" -> failed.externalRow.put("recovery_unlocked", false);
                case "wrong-retries" -> failed.externalRow.put("retries_", 2);
                case "wrong-id" -> failed.externalRow.put("id_", "foreign");
                case "wrong-topic" -> failed.externalRow.put("handler_cfg_", "different");
                case "bad-revision" -> failed.externalRow.put("rev_", 0);
                case "wrong-correlation" -> failed.externalRow.put("correlation_id_", "different");
                case "wrong-schema" -> when(failed.base.jdbc.queryForObject("SELECT current_schema()", String.class))
                        .thenReturn("foreign");
                default -> { }
            }
            assertThrows(IllegalStateException.class, () -> transaction(failed.base.source, () -> {
                if (fault.equals("nested-context")) Context.setCommandContext(mock(CommandContext.class));
                if (fault.equals("read-only")) TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
                try {
                    failed.base.access.moveAndVerifyRecovery(failed.job);
                    return null;
                } finally {
                    if (fault.equals("nested-context")) Context.removeCommandContext();
                    if (fault.equals("read-only")) TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
                }
            }), fault);
            if (Set.of("wrong-schema", "nested-context", "read-only").contains(fault)) {
                verify(failed.base.management, never()).moveDeadLetterJobToExecutableJob(anyString(), eq(1));
            }
        }
    }

    private static final class RecoveryFixture {
        private final Fixture base = new Fixture();
        private final ProcessControlNativeAccess.RecoveryDeadletter job = new ProcessControlNativeAccess.RecoveryDeadletter(
                "job-1", 2, "externalWorker", TENANT_TEXT, INSTANCE, DEFINITION_ID, "execution-1", "workerTask",
                null, null, null, null, "correlation", "external-worker-complete", "invoice");
        private final Map<String, Object> externalRow = new LinkedHashMap<>();
        private final boolean[] presence = {true, false, false, false, false};
        private boolean scopeMatch = true;
        private boolean codeEqual = true;
        private boolean readbackPresent = true;
        private boolean keepOld;

        @SuppressWarnings({"rawtypes", "unchecked"})
        private RecoveryFixture() {
            externalRow.put("id_", job.jobId());
            externalRow.put("rev_", job.revision());
            externalRow.put("type_", job.type());
            externalRow.put("tenant_id_", job.tenant());
            externalRow.put("process_instance_id_", job.instanceId());
            externalRow.put("proc_def_id_", job.definitionId());
            externalRow.put("execution_id_", job.executionId());
            externalRow.put("element_id_", job.elementId());
            externalRow.put("correlation_id_", job.correlationId());
            externalRow.put("handler_type_", job.handler());
            externalRow.put("handler_cfg_", job.topic());
            externalRow.put("retries_", 1);
            externalRow.put("recovery_unlocked", true);
            when(base.jdbc.queryForObject("SELECT current_schema()", String.class)).thenReturn("process");
            Job moved = mock(Job.class);
            when(moved.getId()).thenReturn(job.jobId());
            when(moved.getJobType()).thenReturn(job.type());
            when(moved.getTenantId()).thenReturn(job.tenant());
            when(moved.getProcessInstanceId()).thenReturn(job.instanceId());
            when(moved.getProcessDefinitionId()).thenReturn(job.definitionId());
            when(moved.getExecutionId()).thenReturn(job.executionId());
            when(moved.getElementId()).thenReturn(job.elementId());
            when(moved.getCorrelationId()).thenReturn(job.correlationId());
            when(moved.getJobHandlerType()).thenReturn(job.handler());
            when(moved.getJobHandlerConfiguration()).thenReturn(job.topic());
            when(moved.getRetries()).thenReturn(1);
            when(base.management.moveDeadLetterJobToExecutableJob("job-1", 1)).thenAnswer(invocation -> {
                presence[0] = keepOld;
                presence[1] = true;
                return moved;
            });
            doAnswer(invocation -> {
                String statement = invocation.getArgument(0);
                base.sql.add(statement);
                base.arguments.put(statement, Arrays.asList(((Object[]) invocation.getRawArguments()[2]).clone()));
                RowMapper mapper = invocation.getArgument(1);
                ResultSet row = resultSet(externalRow);
                when(row.getObject("rev_", Integer.class)).thenReturn((Integer) externalRow.get("rev_"));
                when(row.getBoolean("recovery_unlocked")).thenReturn((Boolean) externalRow.get("recovery_unlocked"));
                if (statement.startsWith("SELECT EXISTS")) {
                    for (int position = 1; position <= 5; position++) {
                        when(row.getBoolean(position)).thenReturn(presence[position - 1]);
                    }
                } else if (statement.startsWith("SELECT COALESCE")) {
                    when(row.getBoolean(1)).thenReturn(codeEqual);
                } else if (statement.contains("act_ru_external_job") && !readbackPresent
                        || statement.contains("act_ru_deadletter_job") && !scopeMatch) {
                    return List.of();
                }
                return List.of(mapper.mapRow(row, 0));
            }).when(base.jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        }

        private String lastStatement() {
            return base.sql.getLast();
        }
    }

    private static Fixture oneTask(String activity, String modelActivity) {
        Fixture fixture = new Fixture();
        fixture.live();
        fixture.rootRows(rootRow("root", 1, 1));
        Task task = fixture.task("task-one", "exec-one", activity);
        ValidatedProcessModel.HumanTask policy = mock(ValidatedProcessModel.HumanTask.class);
        when(fixture.taskQuery.listPage(0, 257)).thenReturn(List.of(task));
        when(fixture.model.humanTasks()).thenReturn(Map.of(modelActivity, policy));
        return fixture;
    }

    private static ProcessControlNativeAccess.LockedRoot lockedRoot() {
        return new ProcessControlNativeAccess.LockedRoot(TENANT, INSTANCE, DEFINITION_ID, BUSINESS_KEY, 1, 1);
    }

    private static ProcessControlNativeAccess.TerminalFacts terminal(ProcessControlNativeAccess.DeleteCategory category) {
        return new ProcessControlNativeAccess.TerminalFacts(TENANT, INSTANCE, DEFINITION_ID, BUSINESS_KEY,
                category == ProcessControlNativeAccess.DeleteCategory.NORMAL_COMPLETION
                        ? ProcessInstanceStatusEnum.COMPLETED : ProcessInstanceStatusEnum.CANCELLED,
                FINISHED, category);
    }

    private static ProcessCommandReceiptStore.TaskOrigin start() {
        ProcessActor actor = new ProcessActor(TENANT, ProcessActor.SubjectType.USER, "user-1", "a".repeat(64));
        return new ProcessCommandReceiptStore.TaskOrigin(actor, "start-command", "b".repeat(64), "release-7",
                "corr-4711", INSTANCE, Instant.parse("2026-11-05T01:00:00.123456Z"));
    }

    private static Definition definition() {
        return new Definition(Type.BPMN, "invoiceReview", DEFINITION_ID, "deployment-1", 1, MODEL_HASH, Map.of());
    }

    private static Map<String, Object> rootRow(String id, int revision, int suspension) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id_", id.equals("root") ? INSTANCE : id);
        row.put("rev_", revision);
        row.put("proc_def_id_", DEFINITION_ID);
        row.put("business_key_", BUSINESS_KEY);
        row.put("suspension_state_", suspension);
        row.put("tenant_id_", TENANT_TEXT);
        row.put("proc_inst_id_", INSTANCE);
        row.put("act_id_", null);
        return row;
    }

    private static Map<String, Object> affinity(String execution, String activity) {
        Map<String, Object> row = rootRow(execution, 1, 1);
        row.put("id_", execution);
        row.put("act_id_", activity);
        return row;
    }

    private static <T> T transaction(DataSource source, Supplier<T> action) {
        TransactionSynchronizationManager.bindResource(source, new Object());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            return action.get();
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.unbindResource(source);
        }
    }

    private static void injectJdbc(ProcessControlNativeAccess access, JdbcTemplate jdbc) {
        Field field = Arrays.stream(ProcessControlNativeAccess.class.getDeclaredFields())
                .filter(candidate -> candidate.getType() == JdbcTemplate.class).findFirst().orElseThrow();
        try {
            field.setAccessible(true);
            field.set(access, jdbc);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void assertRecord(Class<?> type, List<String> names, List<Class<?>> types) {
        assertTrue(type.isRecord());
        assertEquals(names, Arrays.stream(type.getRecordComponents()).map(component -> component.getName()).toList());
        assertEquals(types, Arrays.stream(type.getRecordComponents()).map(component -> component.getType()).toList());
    }

    private static final class Fixture {
        private final DataSource source = mock(DataSource.class);
        private final SpringProcessEngineConfiguration engine = mock(SpringProcessEngineConfiguration.class);
        private final DataSourceTransactionManager transaction = mock(DataSourceTransactionManager.class);
        private final RuntimeService runtime = mock(RuntimeService.class);
        private final HistoryService history = mock(HistoryService.class);
        private final TaskService tasks = mock(TaskService.class);
        private final ManagementService management = mock(ManagementService.class);
        private final ProcessInstanceQuery instanceQuery = mock(ProcessInstanceQuery.class, RETURNS_SELF);
        private final HistoricProcessInstanceQuery historyQuery = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
        private final TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
        private final ExternalWorkerJobQuery externalQuery = mock(ExternalWorkerJobQuery.class, RETURNS_SELF);
        private final TimerJobQuery timerQuery = mock(TimerJobQuery.class, RETURNS_SELF);
        private final EventSubscriptionQuery messageQuery = mock(EventSubscriptionQuery.class, RETURNS_SELF);
        private final ValidatedProcessModel model = mock(ValidatedProcessModel.class);
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final List<String> sql = new ArrayList<>();
        private final Map<String, List<Object>> arguments = new LinkedHashMap<>();
        private List<Map<String, Object>> rootRows = List.of();
        private List<Map<String, Object>> lockedRows = List.of();
        private List<Map<String, Object>> affinityRows = List.of();
        private List<Map<String, Object>> structuralRows = List.of();
        private final ProcessControlNativeAccess access;

        private Fixture() {
            when(engine.getDataSource()).thenReturn(source);
            when(engine.getTransactionManager()).thenReturn(transaction);
            when(transaction.getDataSource()).thenReturn(source);
            when(engine.getDatabaseSchema()).thenReturn("process");
            when(engine.getDatabaseTablePrefix()).thenReturn("process.");
            when(engine.getRuntimeService()).thenReturn(runtime);
            when(engine.getHistoryService()).thenReturn(history);
            when(engine.getTaskService()).thenReturn(tasks);
            when(engine.getManagementService()).thenReturn(management);
            when(engine.isDbHistoryUsed()).thenReturn(true);
            when(engine.getHistoryLevel()).thenReturn(HistoryLevel.ACTIVITY);
            when(engine.isAsyncHistoryEnabled()).thenReturn(false);
            when(runtime.createProcessInstanceQuery()).thenReturn(instanceQuery);
            when(history.createHistoricProcessInstanceQuery()).thenReturn(historyQuery);
            when(tasks.createTaskQuery()).thenReturn(taskQuery);
            when(management.createExternalWorkerJobQuery()).thenReturn(externalQuery);
            when(management.createTimerJobQuery()).thenReturn(timerQuery);
            when(runtime.createEventSubscriptionQuery()).thenReturn(messageQuery);
            when(taskQuery.listPage(0, 257)).thenReturn(List.of());
            when(externalQuery.listPage(0, 257)).thenReturn(List.of());
            when(timerQuery.listPage(0, 257)).thenReturn(List.of());
            when(messageQuery.listPage(0, 257)).thenReturn(List.of());
            when(model.type()).thenReturn(Type.BPMN);
            when(model.definitionKey()).thenReturn("invoiceReview");
            when(model.humanTasks()).thenReturn(Map.of());
            when(model.workerTasks()).thenReturn(Map.of());
            when(model.timerWaits()).thenReturn(Map.of());
            when(model.messageWaits()).thenReturn(Map.of());
            when(model.caseSources()).thenReturn(Map.of());
            access = new ProcessControlNativeAccess(source, engine);
            injectJdbc(access, jdbc);
            jdbcReady();
            installRows();
        }

        private void jdbcReady() {
            when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
            when(jdbc.queryForObject(anyString(), eq(Timestamp.class))).thenReturn(Timestamp.from(NOW));
        }

        private void live() {
            ProcessInstance instance = mock(ProcessInstance.class);
            when(instance.getId()).thenReturn(INSTANCE);
            when(instance.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
            when(instance.getTenantId()).thenReturn(TENANT_TEXT);
            when(instance.getBusinessKey()).thenReturn(BUSINESS_KEY);
            when(instance.isSuspended()).thenReturn(false);
            when(instanceQuery.singleResult()).thenReturn(instance);
        }

        private void terminal(String reason, Instant finished, String business) {
            HistoricProcessInstance value = mock(HistoricProcessInstance.class);
            when(value.getId()).thenReturn(INSTANCE);
            when(value.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
            when(value.getTenantId()).thenReturn(TENANT_TEXT);
            when(value.getBusinessKey()).thenReturn(business);
            when(value.getEndTime()).thenReturn(java.util.Date.from(finished));
            when(value.getDeleteReason()).thenReturn(reason);
            when(historyQuery.singleResult()).thenReturn(value);
        }

        private Task task(String id, String execution, String activity) {
            Task value = mock(Task.class);
            when(value.getId()).thenReturn(id);
            when(value.getExecutionId()).thenReturn(execution);
            when(value.getTaskDefinitionKey()).thenReturn(activity);
            when(value.getTenantId()).thenReturn(TENANT_TEXT);
            when(value.getProcessInstanceId()).thenReturn(INSTANCE);
            when(value.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
            return value;
        }

        private ExternalWorkerJob external(String id, String execution, String activity) {
            ExternalWorkerJob value = mock(ExternalWorkerJob.class);
            when(value.getId()).thenReturn(id);
            when(value.getExecutionId()).thenReturn(execution);
            when(value.getElementId()).thenReturn(activity);
            when(value.getTenantId()).thenReturn(TENANT_TEXT);
            when(value.getProcessInstanceId()).thenReturn(INSTANCE);
            when(value.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
            return value;
        }

        private Job timer(String id, String execution, String activity) {
            Job value = mock(Job.class);
            when(value.getId()).thenReturn(id);
            when(value.getExecutionId()).thenReturn(execution);
            when(value.getElementId()).thenReturn(activity);
            when(value.getTenantId()).thenReturn(TENANT_TEXT);
            when(value.getProcessInstanceId()).thenReturn(INSTANCE);
            when(value.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
            return value;
        }

        private EventSubscription message(String id, String execution, String activity) {
            EventSubscription value = mock(EventSubscription.class);
            when(value.getId()).thenReturn(id);
            when(value.getExecutionId()).thenReturn(execution);
            when(value.getActivityId()).thenReturn(activity);
            when(value.getTenantId()).thenReturn(TENANT_TEXT);
            when(value.getProcessInstanceId()).thenReturn(INSTANCE);
            when(value.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
            when(value.getEventType()).thenReturn("message");
            return value;
        }

        @SafeVarargs
        private final void rootRows(Map<String, Object>... rows) {
            rootRows = List.of(rows);
        }

        @SafeVarargs
        private final void lockedRows(Map<String, Object>... rows) {
            lockedRows = List.of(rows);
        }

        @SafeVarargs
        private final void affinityRows(Map<String, Object>... rows) {
            affinityRows = List.of(rows);
        }

        @SafeVarargs
        private final void structuralRows(Map<String, Object>... rows) {
            structuralRows = List.of(rows);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void installRows() {
            doAnswer(invocation -> {
                String statement = invocation.getArgument(0);
                sql.add(statement);
                arguments.put(statement, Arrays.asList(((Object[]) invocation.getRawArguments()[2]).clone()));
                List<Map<String, Object>> selected;
                if (statement.toUpperCase(java.util.Locale.ROOT).contains("FOR UPDATE")) selected = lockedRows;
                else if (statement.contains(" IN (")) selected = affinityRows;
                else if (statement.contains("act_id_")) {
                    selected = new ArrayList<>(affinityRows);
                    selected.addAll(structuralRows);
                } else selected = rootRows;
                RowMapper mapper = invocation.getArgument(1);
                List<Object> mapped = new ArrayList<>();
                int index = 0;
                for (Map<String, Object> row : selected) mapped.add(mapper.mapRow(resultSet(row), index++));
                return mapped;
            }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        }
    }

    private static ResultSet resultSet(Map<String, Object> row) throws java.sql.SQLException {
        AtomicReference<Object> last = new AtomicReference<>();
        ResultSet result = mock(ResultSet.class);
        when(result.getString(anyString())).thenAnswer(invocation -> {
            Object value = row.get(invocation.getArgument(0)); last.set(value);
            return value == null ? null : value.toString();
        });
        when(result.getInt(anyString())).thenAnswer(invocation -> {
            Object value = row.get(invocation.getArgument(0)); last.set(value);
            return value == null ? 0 : ((Number) value).intValue();
        });
        when(result.getLong(anyString())).thenAnswer(invocation -> {
            Object value = row.get(invocation.getArgument(0)); last.set(value);
            return value == null ? 0L : ((Number) value).longValue();
        });
        when(result.wasNull()).thenAnswer(invocation -> last.get() == null);
        return result;
    }
}
