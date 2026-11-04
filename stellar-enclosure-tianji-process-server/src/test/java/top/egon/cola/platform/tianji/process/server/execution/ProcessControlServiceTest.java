package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessResult;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlBlockReasonEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlSnapshotDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStore;
import top.egon.cola.platform.tianji.process.server.error.ProcessCancellationBlockedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** Draft only. Promote after timer metadata B and align only concrete public/collaborator signatures. */
class ProcessControlServiceTest {
    private static final Instant STARTED = Instant.parse("2026-11-05T00:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-11-05T00:10:00Z");
    private static final Instant DECIDED = Instant.parse("2026-11-05T00:10:01Z");
    private static final String INSTANCE = "process-one";
    private static final String RELEASE = "release-one";
    private static final String DEFINITION_KEY = "governedProcess";
    private static final String ENGINE_DEFINITION = "governedProcess:1:native";
    private static final String DEPLOYMENT = "deployment-one";
    private static final String BUSINESS = "order-123";
    private static final String CORRELATION = "correlation-one";
    private static final String MODEL_SHA = "b".repeat(64);
    private static final String START_SHA = "a".repeat(64);
    private static final String VERSION = "c".repeat(64);
    private static final ProcessActor OWNER = ProcessActor.verified(
            20, ProcessActor.SubjectType.USER, "requester", "https://idp.example/20");
    private static final ProcessActor OTHER_OWNER = ProcessActor.verified(
            20, ProcessActor.SubjectType.USER, "other", "https://idp.example/20");
    private static final ProcessActor SERVICE_OWNER = ProcessActor.verified(
            20, ProcessActor.SubjectType.SERVICE, "process-consumer", "https://service.example/20");
    private static final ProcessActor CASE_EXECUTOR = ProcessActor.verified(
            20, ProcessActor.SubjectType.SERVICE, "case-executor", "https://case.example/20");
    private static final ProcessCommandReceiptStore.TaskOrigin START = new ProcessCommandReceiptStore.TaskOrigin(
            OWNER, "start-one", START_SHA, RELEASE, CORRELATION, INSTANCE, STARTED);
    private static final ProcessReleaseService.Definition DEFINITION = new ProcessReleaseService.Definition(
            ValidatedProcessModel.Type.BPMN, DEFINITION_KEY, ENGINE_DEFINITION, DEPLOYMENT, 1, MODEL_SHA, Map.of());
    private static final ProcessReleaseService.Definition CASE_DEFINITION = new ProcessReleaseService.Definition(
            ValidatedProcessModel.Type.CMMN, "governedCase", "governedCase:1:native", "case-deployment", 1,
            "7".repeat(64), Map.of());
    private static final List<ProcessActiveWaitDTO> WAITS = List.of(
            new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 1),
            new ProcessActiveWaitDTO("waitTimeout", ProcessActiveWaitKindEnum.TIMER, 1));

    @Test
    void constructor_requires_one_process_storage_engine_and_every_storage_affinity() throws Exception {
        var source = source();
        var manager = new DataSourceTransactionManager(source);
        var process = process(source, manager);
        var dmn = dmn(source, manager);
        var validator = mock(ProcessModelValidator.class);
        var releases = mock(ProcessReleaseService.class);
        var nativeAccess = mock(ProcessControlNativeAccess.class);
        var cancellations = mock(ProcessCancellationReceiptStore.class);
        var dispatch = mock(AgentDispatchStore.class);
        when(nativeAccess.usesDataSource(source)).thenReturn(true);
        when(nativeAccess.usesEngine(process)).thenReturn(true);
        when(cancellations.usesDataSource(source)).thenReturn(true);
        doNothing().when(dispatch).requireStorage(source, manager, "tianji_process");

        assertDoesNotThrow(() -> new ProcessControlService(
                process, dmn, validator, releases, nativeAccess, cancellations, dispatch));

        when(nativeAccess.usesDataSource(source)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> new ProcessControlService(
                process, dmn, validator, releases, nativeAccess, cancellations, dispatch));
        when(nativeAccess.usesDataSource(source)).thenReturn(true);
        when(cancellations.usesDataSource(source)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> new ProcessControlService(
                process, dmn, validator, releases, nativeAccess, cancellations, dispatch));
        when(cancellations.usesDataSource(source)).thenReturn(true);
        doThrow(new IllegalArgumentException("foreign dispatch storage")).when(dispatch)
                .requireStorage(source, manager, "tianji_process");
        assertThrows(IllegalArgumentException.class, () -> new ProcessControlService(
                process, dmn, validator, releases, nativeAccess, cancellations, dispatch));
    }

    @Test
    void ambient_transactions_are_rejected_before_owner_or_native_lookup() throws Exception {
        try (var fixture = new Fixture()) {
            var ambient = new TransactionTemplate(fixture.manager);
            assertAll(
                    () -> assertThrows(IllegalStateException.class, () -> ambient.execute(status ->
                            fixture.service.control(OWNER, new GetProcessInstanceQuery(INSTANCE)))),
                    () -> assertThrows(IllegalStateException.class, () -> ambient.execute(status ->
                            fixture.service.cancel(OWNER, fixture.command(VERSION))))
            );
            verify(fixture.nativeAccess, never()).observe(any(), any(), any());
            verify(fixture.nativeAccess, never()).lockRoot(any());
            verify(fixture.cancellations, never()).find(any(), anyString(), anyString());
        }
    }

    @Test
    void active_control_is_owner_scoped_useful_clock_free_and_repeatable_read() throws Exception {
        try (var fixture = new Fixture()) {
            ProcessControlSnapshotDTO result = fixture.control();
            assertAll(
                    () -> assertEquals(INSTANCE, result.process().instanceId()),
                    () -> assertEquals(ProcessInstanceStatusEnum.ACTIVE, result.process().status()),
                    () -> assertEquals(WAITS, result.activeWaits()),
                    () -> assertEquals(Set.of(ProcessControlActionEnum.CANCEL), result.allowedActions()),
                    () -> assertEquals(ProcessControlBlockReasonEnum.NONE, result.cancellationBlockReason()),
                    () -> assertEquals(DECIDED, result.observedAt()),
                    () -> assertEquals(64, result.version().length())
            );
            verify(fixture.connection).commit();
            verify(fixture.connection, never()).rollback();
            verify(fixture.connection).setReadOnly(true);
            verify(fixture.connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            verify(fixture.nativeAccess).observe(START, DEFINITION, fixture.model);
        }
    }

    @Test
    void verified_service_owner_controls_and_cancels_with_an_actor_bound_version() throws Exception {
        String userVersion;
        try (var user = new Fixture()) {
            userVersion = user.control().version();
        }
        try (var service = new Fixture(SERVICE_OWNER)) {
            ProcessControlSnapshotDTO control = service.control();
            assertNotEquals(userVersion, control.version());
            CancelProcessResult result = assertDoesNotThrow(() -> service.service.cancel(
                    SERVICE_OWNER, service.command(control.version())));
            assertNotNull(result, "service A must not return null");
            assertTrue(result.accepted());
            verify(service.commandStore()).lockStartByInstance(SERVICE_OWNER, INSTANCE);
            verify(service.cancellations).insert(argThat(entry -> entry.actor().equals(SERVICE_OWNER)
                    && entry.start().requester().equals(SERVICE_OWNER)));
            verify(service.runtime).deleteProcessInstance(INSTANCE, "TIANJI_PROCESS_CANCELLED");
        }
    }

    @Test
    void suspended_control_preserves_waits_but_changes_the_clock_free_version() throws Exception {
        try (var fixture = new Fixture()) {
            ProcessControlSnapshotDTO active = fixture.control();
            fixture.liveRoot = fixture.root(9, 2);
            fixture.controlFacts();
            ProcessControlSnapshotDTO suspended = fixture.control();
            assertAll(
                    () -> assertEquals(ProcessInstanceStatusEnum.SUSPENDED, suspended.process().status()),
                    () -> assertEquals(WAITS, suspended.activeWaits()),
                    () -> assertNotEquals(active.version(), suspended.version())
            );
        }
    }

    @Test
    void completed_and_cancelled_history_are_terminal_without_actions_or_waits() throws Exception {
        for (var terminal : List.of(
                fixtureTerminal(ProcessInstanceStatusEnum.COMPLETED, ProcessControlNativeAccess.DeleteCategory.NORMAL_COMPLETION),
                fixtureTerminal(ProcessInstanceStatusEnum.CANCELLED, ProcessControlNativeAccess.DeleteCategory.PRE_EXISTING_CANCELLATION))) {
            try (var fixture = new Fixture()) {
                fixture.terminal(terminal);
                ProcessControlSnapshotDTO result = fixture.control();
                assertAll(
                        () -> assertEquals(terminal.status(), result.process().status()),
                        () -> assertEquals(FINISHED, result.process().finishedAt()),
                        () -> assertTrue(result.activeWaits().isEmpty()),
                        () -> assertTrue(result.allowedActions().isEmpty()),
                        () -> assertEquals(ProcessControlBlockReasonEnum.PROCESS_TERMINAL,
                                result.cancellationBlockReason())
                );
            }
        }
    }

    @Test
    void observation_clock_command_and_reason_never_change_a_control_or_terminal_version() throws Exception {
        try (var fixture = new Fixture()) {
            when(fixture.nativeAccess.now()).thenReturn(DECIDED, DECIDED.plusSeconds(30));
            ProcessControlSnapshotDTO first = fixture.control();
            ProcessControlSnapshotDTO second = fixture.control();
            assertEquals(first.version(), second.version());

            fixture.terminal(fixtureTerminal(ProcessInstanceStatusEnum.CANCELLED,
                    ProcessControlNativeAccess.DeleteCategory.PRE_EXISTING_CANCELLATION));
            ProcessControlSnapshotDTO terminal = fixture.control();
            CancelProcessResult replay = fixture.replay(new CancelProcessCommand(
                    INSTANCE, "another-command", first.version(), "another exact reason"), terminal.version());
            assertEquals(terminal.version(), replay.resultingVersion());
        }
    }

    @Test
    void root_revision_waits_and_case_block_each_change_the_control_version() throws Exception {
        try (var fixture = new Fixture()) {
            ProcessControlSnapshotDTO original = fixture.control();
            fixture.liveRoot = fixture.root(8, 1);
            fixture.controlFacts();
            ProcessControlSnapshotDTO revision = fixture.control();
            fixture.waits = List.of(new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 2));
            fixture.controlFacts();
            ProcessControlSnapshotDTO waits = fixture.control();
            when(fixture.model.caseSources()).thenReturn(Map.of("caseSource",
                    new ValidatedProcessModel.CaseSource("case", "waitCase", "CaseDone", "caseTimer", Duration.ofMinutes(5))));
            ProcessControlSnapshotDTO blocked = fixture.control();
            assertAll(
                    () -> assertNotEquals(original.version(), revision.version()),
                    () -> assertNotEquals(revision.version(), waits.version()),
                    () -> assertNotEquals(waits.version(), blocked.version()),
                    () -> assertTrue(blocked.allowedActions().isEmpty()),
                    () -> assertEquals(ProcessControlBlockReasonEnum.MODEL_CONTAINS_CASE_SOURCE,
                            blocked.cancellationBlockReason())
            );
        }
    }

    @Test
    void contradictory_native_control_facts_fail_closed_before_native_deletion() throws Exception {
        for (int scenario = 0; scenario < 3; scenario++) {
            try (var fixture = new Fixture()) {
                var corrupted = mock(ProcessControlNativeAccess.ControlFacts.class);
                var terminal = fixtureTerminal(ProcessInstanceStatusEnum.CANCELLED,
                        ProcessControlNativeAccess.DeleteCategory.PRE_EXISTING_CANCELLATION);
                if (scenario == 0) {
                    when(corrupted.liveRoot()).thenReturn(fixture.liveRoot);
                    when(corrupted.terminal()).thenReturn(terminal);
                    when(corrupted.activeWaits()).thenReturn(List.of());
                } else if (scenario == 1) {
                    when(corrupted.liveRoot()).thenReturn(null);
                    when(corrupted.terminal()).thenReturn(terminal);
                    when(corrupted.activeWaits()).thenReturn(WAITS);
                } else {
                    when(corrupted.liveRoot()).thenReturn(null);
                    when(corrupted.terminal()).thenReturn(null);
                    when(corrupted.activeWaits()).thenReturn(List.of());
                }
                when(fixture.nativeAccess.observe(fixture.sourceStart, DEFINITION, fixture.model))
                        .thenReturn(corrupted);
                RuntimeException failure = assertThrows(RuntimeException.class,
                        () -> fixture.service.cancel(fixture.sourceActor, fixture.command(VERSION)));
                assertInstanceOf(IllegalStateException.class, failure);
                assertFalse(failure instanceof ProcessVersionConflictException);
                verify(fixture.runtime, never()).deleteProcessInstance(anyString(), anyString());
                verify(fixture.cancellations, never()).insert(any());
            }
        }
    }

    @Test
    void missing_or_cross_owner_start_is_hidden_before_release_or_native_access() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.startVisible = false;
            assertThrows(ProcessResourceNotFoundException.class,
                    () -> fixture.service.control(OWNER, new GetProcessInstanceQuery(INSTANCE)));
            assertThrows(ProcessResourceNotFoundException.class,
                    () -> fixture.service.control(OTHER_OWNER, new GetProcessInstanceQuery(INSTANCE)));
            verify(fixture.nativeAccess, never()).observe(any(), any(), any());
            verify(fixture.nativeAccess, never()).lockRoot(any());
        }
    }

    @Test
    void exact_replay_uses_current_owner_and_stored_detail_without_native_mutation() throws Exception {
        try (var fixture = new Fixture()) {
            CancelProcessCommand command = fixture.command(VERSION);
            CancelProcessResult stored = fixture.result(command, false, ProcessInstanceStatusEnum.COMPLETED,
                    VERSION, FINISHED, DECIDED, false);
            fixture.appliedReplay(command, stored);
            CancelProcessResult result = assertDoesNotThrow(() -> fixture.service.cancel(OWNER, command));
            assertNotNull(result, "service A must not return null");
            assertEquals(stored, result);
            InOrder order = inOrder(fixture.commandStore(), fixture.cancellations);
            order.verify(fixture.commandStore()).lockStartByInstance(OWNER, INSTANCE);
            order.verify(fixture.cancellations).find(eq(OWNER), eq(command.commandId()), anyString());
            verify(fixture.nativeAccess, never()).lockRoot(any());
            verify(fixture.runtime, never()).deleteProcessInstance(anyString(), anyString());
        }
    }

    @Test
    void same_command_with_a_different_request_hash_conflicts_before_native_access() throws Exception {
        try (var fixture = new Fixture()) {
            CancelProcessCommand command = fixture.command(VERSION);
            String actual = cancelRequestHash(command);
            String different = (actual.startsWith("0") ? "1" : "0") + actual.substring(1);
            when(fixture.commandStore().lockIfPresent(OWNER, ProcessCommandReceiptStore.Kind.PROCESS_CANCEL,
                    "cancel-one")).thenReturn(Optional.of(new ProcessCommandReceiptStore.Receipt(
                    different, RELEASE, CORRELATION, "APPLIED", INSTANCE, DECIDED, null)));
            assertThrows(ProcessConflictException.class,
                    () -> fixture.service.cancel(OWNER, command));
            verify(fixture.nativeAccess, never()).lockRoot(any());
            verify(fixture.runtime, never()).deleteProcessInstance(anyString(), anyString());
        }
    }

    @Test
    void terminal_before_cancel_is_an_audited_no_op_even_when_expected_version_is_stale() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.terminal(fixtureTerminal(ProcessInstanceStatusEnum.COMPLETED,
                    ProcessControlNativeAccess.DeleteCategory.NORMAL_COMPLETION));
            CancelProcessResult result = assertDoesNotThrow(() -> fixture.service.cancel(
                    OWNER, fixture.command("f".repeat(64))));
            assertNotNull(result, "service A must not return null");
            assertAll(
                    () -> assertFalse(result.accepted()),
                    () -> assertEquals(ProcessInstanceStatusEnum.COMPLETED, result.status()),
                    () -> assertFalse(result.linkedAgentClosureScheduled())
            );
            verify(fixture.cancellations).insert(argThat(entry -> !entry.result().accepted()
                    && entry.result().status() == ProcessInstanceStatusEnum.COMPLETED));
            verify(fixture.commandStore()).apply(OWNER, ProcessCommandReceiptStore.Kind.PROCESS_CANCEL,
                    "cancel-one", INSTANCE, null);
            verify(fixture.runtime, never()).deleteProcessInstance(anyString(), anyString());
            verify(fixture.connection).commit();
        }
    }

    @Test
    void terminal_no_op_commands_share_the_terminal_version_but_keep_distinct_request_audits() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.terminal(fixtureTerminal(ProcessInstanceStatusEnum.COMPLETED,
                    ProcessControlNativeAccess.DeleteCategory.NORMAL_COMPLETION));
            var first = new CancelProcessCommand(INSTANCE, "cancel-one", "e".repeat(64), "first exact reason");
            var second = new CancelProcessCommand(INSTANCE, "cancel-two", "f".repeat(64), "second exact reason");
            CancelProcessResult firstResult = assertDoesNotThrow(() -> fixture.service.cancel(OWNER, first));
            CancelProcessResult secondResult = assertDoesNotThrow(() -> fixture.service.cancel(OWNER, second));
            assertNotNull(firstResult, "service A must not return null");
            assertNotNull(secondResult, "service A must not return null");
            assertEquals(firstResult.resultingVersion(), secondResult.resultingVersion());
            assertFalse(firstResult.accepted());
            assertFalse(secondResult.accepted());

            ArgumentCaptor<String> genericHashes = ArgumentCaptor.forClass(String.class);
            verify(fixture.commandStore(), times(2)).insert(eq(OWNER),
                    eq(ProcessCommandReceiptStore.Kind.PROCESS_CANCEL), anyString(), genericHashes.capture(),
                    eq(RELEASE), eq(CORRELATION));
            assertEquals(2, Set.copyOf(genericHashes.getAllValues()).size());
            ArgumentCaptor<ProcessCancellationReceiptStore.Entry> audits =
                    ArgumentCaptor.forClass(ProcessCancellationReceiptStore.Entry.class);
            verify(fixture.cancellations, times(2)).insert(audits.capture());
            assertEquals(genericHashes.getAllValues(), audits.getAllValues().stream()
                    .map(ProcessCancellationReceiptStore.Entry::requestHash).toList());
            assertEquals(List.of("cancel-one", "cancel-two"), audits.getAllValues().stream()
                    .map(entry -> entry.command().commandId()).toList());
            assertEquals(List.of("first exact reason", "second exact reason"), audits.getAllValues().stream()
                    .map(entry -> entry.command().reason()).toList());
            verify(fixture.runtime, never()).deleteProcessInstance(anyString(), anyString());
            verify(fixture.dispatch, never()).scheduleProcessClosure(any());
        }
    }

    @Test
    void stale_live_version_rolls_back_before_case_provenance_delete_or_receipts() throws Exception {
        try (var fixture = new Fixture()) {
            assertThrows(ProcessVersionConflictException.class,
                    () -> fixture.service.cancel(OWNER, fixture.command("f".repeat(64))));
            verify(fixture.runtime, never()).deleteProcessInstance(anyString(), anyString());
            verify(fixture.cancellations, never()).insert(any());
            verify(fixture.dispatch, never()).scheduleProcessClosure(any());
            verify(fixture.connection).rollback();
        }
    }

    @Test
    void live_case_model_is_blocked_only_after_the_current_version_is_rechecked() throws Exception {
        try (var fixture = new Fixture()) {
            when(fixture.model.caseSources()).thenReturn(Map.of("caseSource",
                    new ValidatedProcessModel.CaseSource("case", "waitCase", "CaseDone", "caseTimer", Duration.ofMinutes(5))));
            ProcessControlSnapshotDTO control = fixture.control();
            clearInvocations(fixture.connection, fixture.nativeAccess, fixture.runtime, fixture.cancellations);
            assertThrows(ProcessCancellationBlockedException.class,
                    () -> fixture.service.cancel(OWNER, fixture.command(control.version())));
            verify(fixture.nativeAccess).lockRoot(START);
            verify(fixture.runtime, never()).deleteProcessInstance(anyString(), anyString());
            verify(fixture.cancellations, never()).insert(any());
            verify(fixture.connection).rollback();
        }
    }

    @Test
    void linked_case_provenance_requires_the_exact_service_executor_command_and_source_proof() throws Exception {
        try (var valid = new Fixture()) {
            valid.linkedCase(linkedProvenanceRow());
            ProcessControlSnapshotDTO control = valid.control();
            clearInvocations(valid.connection);
            assertThrows(ProcessCancellationBlockedException.class,
                    () -> valid.service.cancel(OWNER, valid.command(control.version())));
            verify(valid.runtime, never()).deleteProcessInstance(anyString(), anyString());
            verify(valid.cancellations, never()).insert(any());
            verify(valid.connection).rollback();
            verify(valid.connection, never()).commit();
        }

        for (var corruption : List.of(
                Map.entry("executor_type", "USER"),
                Map.entry("executor_issuer", "malformed"),
                Map.entry("source_id", ""),
                Map.entry("source_occurrence_id", ""),
                Map.entry("case_start_command_id", "-invalid"),
                Map.entry("case_start_request_sha256", "invalid"),
                Map.entry("case_start_command_type", "START"),
                Map.entry("executor_state", "PENDING"),
                Map.entry("executor_resource_id", "foreign-case"),
                Map.entry("executor_request_sha256", "8".repeat(64)),
                Map.entry("executor_release_key", "foreign-release"),
                Map.entry("executor_correlation_id", "foreign-correlation"))) {
            try (var fixture = new Fixture()) {
                Map<String, Object> row = linkedProvenanceRow();
                row.put(corruption.getKey(), corruption.getValue());
                fixture.linkedCase(row);
                ProcessControlSnapshotDTO control = fixture.control();
                clearInvocations(fixture.connection);
                assertThrows(IllegalStateException.class,
                        () -> fixture.service.cancel(OWNER, fixture.command(control.version())), corruption.getKey());
                verify(fixture.runtime, never()).deleteProcessInstance(anyString(), anyString());
                verify(fixture.cancellations, never()).insert(any());
                verify(fixture.connection).rollback();
                verify(fixture.connection, never()).commit();
            }
        }
    }

    @Test
    void accepted_cancel_commits_delete_history_agent_schedule_and_both_receipts_in_order() throws Exception {
        try (var fixture = new Fixture()) {
            ProcessControlSnapshotDTO control = fixture.control();
            clearInvocations(fixture.connection, fixture.nativeAccess, fixture.runtime, fixture.cancellations,
                    fixture.dispatch);
            when(fixture.dispatch.scheduleProcessClosure(any())).thenReturn(2);
            CancelProcessCommand command = fixture.command(control.version());
            CancelProcessResult result = assertDoesNotThrow(() -> fixture.service.cancel(OWNER, command));
            assertNotNull(result, "service A must not return null");
            assertAll(
                    () -> assertTrue(result.accepted()),
                    () -> assertEquals(ProcessInstanceStatusEnum.CANCELLED, result.status()),
                    () -> assertTrue(result.linkedAgentClosureScheduled()),
                    () -> assertEquals(FINISHED, result.finishedAt()),
                    () -> assertEquals(DECIDED, result.decidedAt())
            );
            verify(fixture.runtime).deleteProcessInstance(INSTANCE, "TIANJI_PROCESS_CANCELLED");
            verify(fixture.runtime, never()).deleteProcessInstance(INSTANCE, command.reason());
            InOrder order = inOrder(fixture.commandStore(), fixture.nativeAccess, fixture.runtime,
                    fixture.dispatch, fixture.cancellations);
            order.verify(fixture.commandStore()).lockStartByInstance(OWNER, INSTANCE);
            order.verify(fixture.nativeAccess).lockRoot(START);
            order.verify(fixture.runtime).deleteProcessInstance(INSTANCE, "TIANJI_PROCESS_CANCELLED");
            order.verify(fixture.nativeAccess).terminalHistory(START, DEFINITION, BUSINESS, true);
            order.verify(fixture.dispatch).scheduleProcessClosure(any());
            order.verify(fixture.cancellations).insert(argThat(entry -> entry.command().equals(command)
                    && entry.result().linkedAgentClosureScheduled()));
            order.verify(fixture.commandStore()).apply(OWNER, ProcessCommandReceiptStore.Kind.PROCESS_CANCEL,
                    command.commandId(), INSTANCE, null);
            verify(fixture.connection).commit();
            verify(fixture.connection, never()).rollback();
        }
    }

    @Test
    void provenance_inconsistency_rolls_back_before_reserving_or_deleting() throws Exception {
        try (var fixture = new Fixture()) {
            ProcessControlSnapshotDTO control = fixture.control();
            clearInvocations(fixture.connection, fixture.runtime, fixture.cancellations);
            fixture.provenanceFailure.set(new IllegalStateException("foreign provenance"));
            assertThrows(IllegalStateException.class,
                    () -> fixture.service.cancel(OWNER, fixture.command(control.version())));
            verify(fixture.runtime, never()).deleteProcessInstance(anyString(), anyString());
            verify(fixture.cancellations, never()).insert(any());
            verify(fixture.connection).rollback();
        }
    }

    @Test
    void native_delete_failure_rolls_back_without_history_dispatch_or_audit() throws Exception {
        try (var fixture = new Fixture()) {
            ProcessControlSnapshotDTO control = fixture.control();
            clearInvocations(fixture.connection);
            doThrow(new IllegalStateException("native delete failed")).when(fixture.runtime)
                    .deleteProcessInstance(INSTANCE, "TIANJI_PROCESS_CANCELLED");
            assertThrows(IllegalStateException.class,
                    () -> fixture.service.cancel(OWNER, fixture.command(control.version())));
            verify(fixture.nativeAccess, never()).terminalHistory(START, DEFINITION, BUSINESS, true);
            verify(fixture.dispatch, never()).scheduleProcessClosure(any());
            verify(fixture.cancellations, never()).insert(any());
            verify(fixture.connection).rollback();
            verify(fixture.connection, never()).commit();
        }
    }

    @Test
    void missing_or_inconsistent_post_delete_history_rolls_back_native_delete() throws Exception {
        try (var fixture = new Fixture()) {
            ProcessControlSnapshotDTO control = fixture.control();
            clearInvocations(fixture.connection);
            doThrow(new IllegalStateException("history not synchronous")).when(fixture.nativeAccess)
                    .terminalHistory(START, DEFINITION, BUSINESS, true);
            assertThrows(IllegalStateException.class,
                    () -> fixture.service.cancel(OWNER, fixture.command(control.version())));
            verify(fixture.runtime).deleteProcessInstance(INSTANCE, "TIANJI_PROCESS_CANCELLED");
            verify(fixture.dispatch, never()).scheduleProcessClosure(any());
            verify(fixture.cancellations, never()).insert(any());
            verify(fixture.connection).rollback();
        }
    }

    @Test
    void dispatch_failure_after_native_delete_rolls_back_without_audit_or_generic_apply() throws Exception {
        try (var fixture = new Fixture()) {
            ProcessControlSnapshotDTO control = fixture.control();
            clearInvocations(fixture.connection);
            doThrow(new IllegalStateException("dispatch affinity drift")).when(fixture.dispatch)
                    .scheduleProcessClosure(any());
            assertThrows(IllegalStateException.class,
                    () -> fixture.service.cancel(OWNER, fixture.command(control.version())));
            verify(fixture.runtime).deleteProcessInstance(INSTANCE, "TIANJI_PROCESS_CANCELLED");
            verify(fixture.cancellations, never()).insert(any());
            verify(fixture.commandStore(), never()).apply(any(), any(), anyString(), anyString(), any());
            verify(fixture.connection).rollback();
        }
    }

    @Test
    void audit_or_generic_apply_failure_rolls_back_the_whole_native_transaction() throws Exception {
        try (var audit = new Fixture()) {
            ProcessControlSnapshotDTO auditControl = audit.control();
            clearInvocations(audit.connection);
            doThrow(new IllegalStateException("audit insert failed")).when(audit.cancellations).insert(any());
            assertThrows(IllegalStateException.class,
                    () -> audit.service.cancel(OWNER, audit.command(auditControl.version())));
            verify(audit.runtime).deleteProcessInstance(INSTANCE, "TIANJI_PROCESS_CANCELLED");
            verify(audit.connection).rollback();
        }
        try (var generic = new Fixture()) {
            ProcessControlSnapshotDTO genericControl = generic.control();
            clearInvocations(generic.connection);
            doThrow(new IllegalStateException("generic apply failed")).when(generic.commandStore())
                    .apply(any(), eq(ProcessCommandReceiptStore.Kind.PROCESS_CANCEL), anyString(), anyString(), isNull());
            assertThrows(IllegalStateException.class,
                    () -> generic.service.cancel(OWNER, generic.command(genericControl.version())));
            verify(generic.cancellations).insert(any());
            verify(generic.connection).rollback();
        }
    }

    @Test
    void exact_retryable_sqlstates_are_classified_only_after_write_rollback() throws Exception {
        for (String state : List.of("40001", "40P01", "55P03")) {
            try (var fixture = new Fixture()) {
                ProcessControlSnapshotDTO control = fixture.control();
                clearInvocations(fixture.connection);
                doThrow(sql(state)).when(fixture.dispatch).scheduleProcessClosure(any());
                assertThrows(ProcessVersionConflictException.class,
                        () -> fixture.service.cancel(OWNER, fixture.command(control.version())));
                verify(fixture.connection).rollback();
                verify(fixture.connection, never()).commit();
            }
        }
        for (String state : List.of("57014", "08006", "99999")) {
            try (var fixture = new Fixture()) {
                ProcessControlSnapshotDTO control = fixture.control();
                clearInvocations(fixture.connection);
                doThrow(sql(state)).when(fixture.dispatch).scheduleProcessClosure(any());
                RuntimeException failure = assertThrows(RuntimeException.class,
                        () -> fixture.service.cancel(OWNER, fixture.command(control.version())));
                assertFalse(failure instanceof ProcessVersionConflictException);
                verify(fixture.connection).rollback();
                verify(fixture.connection, never()).commit();
            }
        }
    }

    @Test
    void not_found_is_a_conflict_only_at_the_native_delete_stage() throws Exception {
        var missingAtDelete = mock(FlowableObjectNotFoundException.class);
        try (var delete = new Fixture()) {
            ProcessControlSnapshotDTO deleteControl = delete.control();
            clearInvocations(delete.connection);
            doThrow(missingAtDelete).when(delete.runtime)
                    .deleteProcessInstance(INSTANCE, "TIANJI_PROCESS_CANCELLED");
            assertThrows(ProcessVersionConflictException.class,
                    () -> delete.service.cancel(OWNER, delete.command(deleteControl.version())));
            verify(delete.connection).rollback();
        }
        try (var beforeDelete = new Fixture()) {
            ProcessControlSnapshotDTO beforeControl = beforeDelete.control();
            clearInvocations(beforeDelete.connection);
            doThrow(missingAtDelete).when(beforeDelete.nativeAccess).lockRoot(START);
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> beforeDelete.service.cancel(OWNER, beforeDelete.command(beforeControl.version())));
            assertFalse(failure instanceof ProcessVersionConflictException);
            verify(beforeDelete.connection).rollback();
        }
    }

    @Test
    void write_transaction_has_ten_second_deadline_and_sets_five_second_local_lock_timeout() throws Exception {
        try (var fixture = new Fixture()) {
            ProcessControlSnapshotDTO control = fixture.control();
            fixture.assertWriteTransaction.set(true);
            CancelProcessResult result = assertDoesNotThrow(() -> fixture.service.cancel(
                    OWNER, fixture.command(control.version())));
            assertNotNull(result, "service A must not return null");
            assertTrue(fixture.writeTransactionObserved.get(), "write collaborator must observe the owned transaction");
            verify(fixture.ownJdbc()).execute(argThat((String sql) -> sql.toLowerCase().contains("lock_timeout")
                    && sql.contains("5")));
        }
    }

    private static ProcessControlNativeAccess.TerminalFacts fixtureTerminal(
            ProcessInstanceStatusEnum status, ProcessControlNativeAccess.DeleteCategory category) {
        return new ProcessControlNativeAccess.TerminalFacts(
                20, INSTANCE, ENGINE_DEFINITION, BUSINESS, status, FINISHED, category);
    }

    private static DataAccessResourceFailureException sql(String state) {
        return new DataAccessResourceFailureException("classified database failure",
                new SQLException("masked", state));
    }

    private static String cancelRequestHash(CancelProcessCommand command) {
        return ProcessRequestDigest.hash(List.of("TIANJI_PROCESS_CANCEL_V1", command.instanceId(),
                command.commandId(), command.expectedVersion(), command.reason()), Map.of());
    }

    private static Map<String, Object> linkedProvenanceRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenant_id", 20L);
        row.put("case_instance_id", "case-one");
        row.put("start_kind", "LINKED_BPMN");
        row.put("business_key", BUSINESS);
        row.put("correlation_id", CORRELATION);
        row.put("release_key", RELEASE);
        row.put("case_model_type", "CMMN");
        row.put("case_definition_key", CASE_DEFINITION.definitionKey());
        row.put("case_engine_definition_id", CASE_DEFINITION.engineDefinitionId());
        row.put("case_model_sha256", CASE_DEFINITION.modelSha256());
        row.put("owner_type", OWNER.subjectType().name());
        row.put("owner_issuer", OWNER.issuerFingerprint());
        row.put("owner_subject", OWNER.subjectId());
        row.put("owner_start_command_type", "START");
        row.put("owner_start_command_id", START.commandId());
        row.put("owner_start_request_sha256", START.requestHash());
        row.put("executor_type", CASE_EXECUTOR.subjectType().name());
        row.put("executor_issuer", CASE_EXECUTOR.issuerFingerprint());
        row.put("executor_subject", CASE_EXECUTOR.subjectId());
        row.put("case_start_command_type", "CASE_START");
        row.put("case_start_command_id", "case-start-one");
        row.put("case_start_request_sha256", "9".repeat(64));
        row.put("source_id", "renamed-source");
        row.put("parent_process_instance_id", INSTANCE);
        row.put("source_occurrence_id", "occurrence-one");
        row.put("source_element_id", "caseSource");
        row.put("parent_model_type", "BPMN");
        row.put("parent_definition_key", DEFINITION_KEY);
        row.put("parent_engine_definition_id", ENGINE_DEFINITION);
        row.put("parent_model_sha256", MODEL_SHA);
        row.put("created_at", Timestamp.from(STARTED.plusSeconds(1)));
        row.put("executor_state", "APPLIED");
        row.put("executor_request_sha256", row.get("case_start_request_sha256"));
        row.put("executor_resource_id", row.get("case_instance_id"));
        row.put("executor_release_key", RELEASE);
        row.put("executor_correlation_id", CORRELATION);
        return row;
    }

    private static DataSource source() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        when(connection.isReadOnly()).thenReturn(false);
        return source;
    }

    private static SpringProcessEngineConfiguration process(
            DataSource source, DataSourceTransactionManager manager) {
        var process = mock(SpringProcessEngineConfiguration.class);
        when(process.getDataSource()).thenReturn(source);
        when(process.getTransactionManager()).thenReturn(manager);
        when(process.getDatabaseSchema()).thenReturn("tianji_process");
        when(process.getRuntimeService()).thenReturn(mock(RuntimeService.class));
        when(process.getHistoryService()).thenReturn(mock(HistoryService.class));
        when(process.isDbHistoryUsed()).thenReturn(true);
        when(process.getHistoryLevel()).thenReturn(HistoryLevel.INSTANCE);
        when(process.isAsyncHistoryEnabled()).thenReturn(false);
        return process;
    }

    private static SpringDmnEngineConfiguration dmn(
            DataSource source, DataSourceTransactionManager manager) {
        var dmn = mock(SpringDmnEngineConfiguration.class);
        when(dmn.getDataSource()).thenReturn(source);
        when(dmn.getTransactionManager()).thenReturn(manager);
        when(dmn.getDatabaseSchema()).thenReturn("tianji_process");
        return dmn;
    }

    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        final SpringProcessEngineConfiguration process = mock(SpringProcessEngineConfiguration.class);
        final SpringDmnEngineConfiguration dmn = mock(SpringDmnEngineConfiguration.class);
        final RuntimeService runtime = mock(RuntimeService.class);
        final HistoryService history = mock(HistoryService.class);
        final ProcessInstanceQuery instances = mock(ProcessInstanceQuery.class);
        final ProcessInstance instance = mock(ProcessInstance.class);
        final HistoricProcessInstanceQuery historicInstances = mock(HistoricProcessInstanceQuery.class);
        final HistoricProcessInstance historicInstance = mock(HistoricProcessInstance.class);
        final ProcessModelValidator validator = mock(ProcessModelValidator.class);
        final ProcessReleaseService releases = mock(ProcessReleaseService.class);
        final ProcessControlNativeAccess nativeAccess = mock(ProcessControlNativeAccess.class);
        final ProcessCancellationReceiptStore cancellations = mock(ProcessCancellationReceiptStore.class);
        final AgentDispatchStore dispatch = mock(AgentDispatchStore.class);
        final ValidatedProcessModel model = mock(ValidatedProcessModel.class);
        final AtomicReference<RuntimeException> provenanceFailure = new AtomicReference<>();
        final AtomicReference<String> insertedRequestHash = new AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean assertWriteTransaction = new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicBoolean writeTransactionObserved = new java.util.concurrent.atomic.AtomicBoolean();
        final MockedConstruction<JdbcTemplate> statements;
        final MockedConstruction<ProcessCommandReceiptStore> commands;
        final MockedConstruction<VerifiedProcessModels> models;
        final ProcessControlService service;
        final ProcessActor sourceActor;
        final ProcessCommandReceiptStore.TaskOrigin sourceStart;
        boolean startVisible = true;
        ProcessControlNativeAccess.LockedRoot liveRoot = root(7, 1);
        ProcessControlNativeAccess.TerminalFacts terminal;
        List<ProcessActiveWaitDTO> waits = WAITS;
        List<Map<String, Object>> provenanceRows = List.of();

        Fixture() throws Exception {
            this(OWNER);
        }

        Fixture(ProcessActor sourceActor) throws Exception {
            this.sourceActor = sourceActor;
            this.sourceStart = sourceActor.equals(OWNER) ? START : new ProcessCommandReceiptStore.TaskOrigin(
                    sourceActor, START.commandId(), START.requestHash(), START.releaseKey(), START.correlationId(),
                    START.instanceId(), START.createdAt());
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            when(connection.isReadOnly()).thenReturn(false);
            when(process.getDataSource()).thenReturn(source);
            when(process.getTransactionManager()).thenReturn(manager);
            when(process.getDatabaseSchema()).thenReturn("tianji_process");
            when(process.getRuntimeService()).thenReturn(runtime);
            when(process.getHistoryService()).thenReturn(history);
            when(process.isDbHistoryUsed()).thenReturn(true);
            when(process.getHistoryLevel()).thenReturn(HistoryLevel.INSTANCE);
            when(process.isAsyncHistoryEnabled()).thenReturn(false);
            when(dmn.getDataSource()).thenReturn(source);
            when(dmn.getTransactionManager()).thenReturn(manager);
            when(dmn.getDatabaseSchema()).thenReturn("tianji_process");
            when(runtime.createProcessInstanceQuery()).thenReturn(instances);
            when(instances.processInstanceId(anyString())).thenReturn(instances);
            when(instances.processInstanceTenantId(anyString())).thenReturn(instances);
            when(instances.singleResult()).thenReturn(instance);
            when(instance.getId()).thenReturn(INSTANCE);
            when(instance.getTenantId()).thenReturn("20");
            when(instance.getProcessDefinitionId()).thenReturn(ENGINE_DEFINITION);
            when(instance.getBusinessKey()).thenReturn(BUSINESS);
            when(history.createHistoricProcessInstanceQuery()).thenReturn(historicInstances);
            when(historicInstances.processInstanceId(anyString())).thenReturn(historicInstances);
            when(historicInstances.processInstanceTenantId(anyString())).thenReturn(historicInstances);
            when(historicInstances.singleResult()).thenReturn(historicInstance);
            when(historicInstance.getId()).thenReturn(INSTANCE);
            when(historicInstance.getTenantId()).thenReturn("20");
            when(historicInstance.getProcessDefinitionId()).thenReturn(ENGINE_DEFINITION);
            when(historicInstance.getBusinessKey()).thenReturn(BUSINESS);
            when(historicInstance.getEndTime()).thenReturn(java.util.Date.from(FINISHED));
            when(nativeAccess.usesDataSource(source)).thenReturn(true);
            when(nativeAccess.usesEngine(process)).thenReturn(true);
            when(cancellations.usesDataSource(source)).thenReturn(true);
            doNothing().when(dispatch).requireStorage(source, manager, "tianji_process");
            when(nativeAccess.now()).thenReturn(DECIDED);
            when(nativeAccess.lockRoot(sourceStart)).thenAnswer(call -> {
                observeWriteTransaction();
                return Optional.ofNullable(liveRoot);
            });
            when(nativeAccess.terminalHistory(eq(sourceStart), eq(DEFINITION), eq(BUSINESS), anyBoolean()))
                    .thenAnswer(call -> terminal == null
                            ? fixtureTerminal(ProcessInstanceStatusEnum.CANCELLED,
                            ProcessControlNativeAccess.DeleteCategory.TIANJI_PROCESS_CANCELLED) : terminal);
            when(nativeAccess.observe(eq(sourceStart), eq(DEFINITION), eq(model)))
                    .thenAnswer(call -> new ProcessControlNativeAccess.ControlFacts(liveRoot, terminal, waits));
            when(releases.get(sourceActor, RELEASE)).thenReturn(new ProcessReleaseService.Release(
                    RELEASE, "d".repeat(64), STARTED, List.of(DEFINITION)));
            when(model.type()).thenReturn(ValidatedProcessModel.Type.BPMN);
            when(model.definitionKey()).thenReturn(DEFINITION_KEY);
            when(model.caseSources()).thenReturn(Map.of());
            when(cancellations.find(eq(sourceActor), anyString(), anyString())).thenReturn(Optional.empty());
            when(dispatch.scheduleProcessClosure(any())).thenAnswer(call -> {
                observeWriteTransaction();
                return 0;
            });

            statements = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(invocation -> {
                String method = invocation.getMethod().getName();
                if ("query".equals(method)) return jdbcQuery(invocation);
                if ("execute".equals(method)) return null;
                if ("update".equals(method)) return 1;
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            }));
            commands = mockConstruction(ProcessCommandReceiptStore.class, (store, context) -> {
                when(store.lockStartByInstance(sourceActor, INSTANCE)).thenAnswer(call -> {
                    observeWriteTransaction();
                    return sourceStart;
                });
                when(store.lockIfPresent(eq(sourceActor), eq(ProcessCommandReceiptStore.Kind.PROCESS_CANCEL), anyString()))
                        .thenReturn(Optional.empty());
                when(store.insert(eq(sourceActor), eq(ProcessCommandReceiptStore.Kind.PROCESS_CANCEL), anyString(),
                        anyString(), eq(RELEASE), eq(CORRELATION))).thenAnswer(call -> {
                    insertedRequestHash.set(call.getArgument(3));
                    return true;
                });
                when(store.lock(eq(sourceActor), eq(ProcessCommandReceiptStore.Kind.PROCESS_CANCEL), anyString()))
                        .thenAnswer(call -> new ProcessCommandReceiptStore.Receipt(
                                insertedRequestHash.get(), RELEASE, CORRELATION, "PENDING", null, DECIDED, null));
            });
            models = mockConstruction(VerifiedProcessModels.class, (verified, context) -> {
                when(verified.runningProcess(20, DEFINITION)).thenReturn(model);
                when(verified.process(20, DEFINITION)).thenReturn(model);
            });
            service = new ProcessControlService(
                    process, dmn, validator, releases, nativeAccess, cancellations, dispatch);
            controlFacts();
        }

        void controlFacts() {
            when(nativeAccess.observe(eq(sourceStart), eq(DEFINITION), eq(model)))
                    .thenAnswer(call -> new ProcessControlNativeAccess.ControlFacts(liveRoot, terminal, waits));
        }

        void linkedCase(Map<String, Object> row) {
            provenanceRows = List.of(Map.copyOf(row));
            when(model.caseSources()).thenReturn(Map.of("caseSource",
                    new ValidatedProcessModel.CaseSource(CASE_DEFINITION.definitionKey(), "waitCase",
                            "TianjiCaseCompletedV1", "waitCaseTimeout", Duration.ofMinutes(5))));
            when(releases.get(sourceActor, RELEASE)).thenReturn(new ProcessReleaseService.Release(
                    RELEASE, "d".repeat(64), STARTED, List.of(DEFINITION, CASE_DEFINITION)));
        }

        void terminal(ProcessControlNativeAccess.TerminalFacts value) {
            liveRoot = null;
            terminal = value;
            waits = List.of();
            when(instances.singleResult()).thenReturn(null);
            controlFacts();
            when(nativeAccess.lockRoot(sourceStart)).thenReturn(Optional.empty());
            when(nativeAccess.terminalHistory(eq(sourceStart), eq(DEFINITION), eq(BUSINESS), anyBoolean()))
                    .thenReturn(value);
        }

        ProcessControlSnapshotDTO control() {
            ProcessControlSnapshotDTO result = assertDoesNotThrow(() ->
                    service.control(sourceActor, new GetProcessInstanceQuery(INSTANCE)));
            assertNotNull(result, "service A must not return null");
            return result;
        }

        CancelProcessCommand command(String expectedVersion) {
            return new CancelProcessCommand(INSTANCE, "cancel-one", expectedVersion, "owner requested cancellation");
        }

        CancelProcessResult replay(CancelProcessCommand command, String resultingVersion) {
            CancelProcessResult stored = result(command, false, ProcessInstanceStatusEnum.CANCELLED,
                    resultingVersion, FINISHED, DECIDED, false);
            appliedReplay(command, stored);
            CancelProcessResult replayed = assertDoesNotThrow(() -> service.cancel(sourceActor, command));
            assertNotNull(replayed, "service A must not return null");
            return replayed;
        }

        void appliedReplay(CancelProcessCommand command, CancelProcessResult stored) {
            String requestHash = cancelRequestHash(command);
            when(commandStore().lockIfPresent(sourceActor, ProcessCommandReceiptStore.Kind.PROCESS_CANCEL,
                    command.commandId())).thenReturn(Optional.of(new ProcessCommandReceiptStore.Receipt(
                    requestHash, RELEASE, CORRELATION, "APPLIED", INSTANCE, DECIDED, null)));
            when(cancellations.find(sourceActor, command.commandId(), requestHash))
                    .thenReturn(Optional.of(entry(command, requestHash, stored)));
        }

        CancelProcessResult result(CancelProcessCommand command, boolean accepted, ProcessInstanceStatusEnum status,
                                   String resultingVersion, Instant finishedAt, Instant decidedAt, boolean closure) {
            return new CancelProcessResult(INSTANCE, command.commandId(), accepted, status,
                    resultingVersion, finishedAt, decidedAt, closure);
        }

        ProcessCancellationReceiptStore.Entry entry(CancelProcessCommand command, String requestHash,
                                                     CancelProcessResult result) {
            return new ProcessCancellationReceiptStore.Entry(sourceActor, requestHash, command, sourceStart, DEFINITION_KEY,
                    ENGINE_DEFINITION, MODEL_SHA, BUSINESS, result);
        }

        ProcessControlNativeAccess.LockedRoot root(int revision, int suspensionState) {
            return new ProcessControlNativeAccess.LockedRoot(
                    20, INSTANCE, ENGINE_DEFINITION, BUSINESS, revision, suspensionState);
        }

        ProcessCommandReceiptStore commandStore() {
            assertFalse(commands.constructed().isEmpty(), "service must create its generic receipt store");
            return commands.constructed().getFirst();
        }

        JdbcTemplate ownJdbc() {
            assertFalse(statements.constructed().isEmpty(), "service must create its own JdbcTemplate");
            return statements.constructed().getFirst();
        }

        private Object jdbcQuery(InvocationOnMock invocation) throws Throwable {
            String sql = invocation.getArgument(0);
            @SuppressWarnings("unchecked") RowMapper<Object> mapper = invocation.getArgument(1);
            if (sql.contains("tianji_case_start_provenance")) {
                RuntimeException failure = provenanceFailure.get();
                if (failure != null) throw failure;
                List<Object> mapped = new ArrayList<>();
                for (int index = 0; index < provenanceRows.size(); index++) {
                    mapped.add(mapper.mapRow(row(provenanceRows.get(index)), index));
                }
                return List.copyOf(mapped);
            }
            if (sql.contains("tianji_process_command_receipt")) {
                if (!startVisible) return List.of();
                return List.of(mapper.mapRow(row(startRow()), 0));
            }
            if (sql.contains("tianji_process_release_definition")) {
                return List.of(mapper.mapRow(row(definitionRow()), 0));
            }
            throw new AssertionError("Unexpected service SQL: " + sql);
        }

        private void observeWriteTransaction() {
            if (!assertWriteTransaction.get()) return;
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            var holder = assertInstanceOf(ConnectionHolder.class,
                    TransactionSynchronizationManager.getResource(source));
            int ttl = holder.getTimeToLiveInSeconds();
            assertTrue(ttl > 0 && ttl <= 10, "write transaction deadline must be ten seconds");
            writeTransactionObserved.set(true);
        }

        private Map<String, Object> startRow() {
            return Map.ofEntries(
                    Map.entry("tenant_id", sourceActor.tenantId()), Map.entry("subject_type", sourceActor.subjectType().name()),
                    Map.entry("subject_issuer", sourceActor.issuerFingerprint()),
                    Map.entry("subject_id", sourceActor.subjectId()), Map.entry("command_id", sourceStart.commandId()),
                    Map.entry("request_sha256", sourceStart.requestHash()), Map.entry("release_key", RELEASE),
                    Map.entry("correlation_id", CORRELATION), Map.entry("resource_id", INSTANCE),
                    Map.entry("created_at", Timestamp.from(STARTED)));
        }

        private Map<String, Object> definitionRow() {
            return Map.ofEntries(
                    Map.entry("model_type", "BPMN"), Map.entry("definition_key", DEFINITION_KEY),
                    Map.entry("engine_definition_id", ENGINE_DEFINITION),
                    Map.entry("engine_deployment_id", DEPLOYMENT), Map.entry("engine_version", 1),
                    Map.entry("model_sha256", MODEL_SHA), Map.entry("input_schema", "{}"));
        }

        @Override
        public void close() {
            models.close();
            commands.close();
            statements.close();
            if (TransactionSynchronizationManager.hasResource(source)) {
                TransactionSynchronizationManager.unbindResource(source);
            }
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private static ResultSet row(Map<String, Object> values) throws SQLException {
        ResultSet result = mock(ResultSet.class);
        when(result.getString(anyString())).thenAnswer(call -> {
            Object value = values.get(call.getArgument(0));
            return value == null ? null : value.toString();
        });
        when(result.getLong(anyString())).thenAnswer(call -> ((Number) values.get(call.getArgument(0))).longValue());
        when(result.getInt(anyString())).thenAnswer(call -> ((Number) values.get(call.getArgument(0))).intValue());
        when(result.getTimestamp(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        when(result.getObject(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        return result;
    }
}
