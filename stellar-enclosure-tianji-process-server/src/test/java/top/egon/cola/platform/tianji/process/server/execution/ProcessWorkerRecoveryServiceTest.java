package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.common.engine.impl.context.Context;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailuresDTO;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerCommand;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerResult;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.bridge.FlowableAgentWaitInspector;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessWorkerRecoveryBlockedException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.Receipt;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.LockedRoot;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.RecoveryDeadletter;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.RecoveryExecution;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Release;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerTask;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService.RecoveryBinding;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService.RecoveryProvenance;
import top.egon.cola.platform.tianji.process.server.worker.WorkerLeaseStore;
import top.egon.cola.platform.tianji.process.server.worker.WorkerLeaseStore.RecoveryLeaseEvidence;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.ReasonEnum.HISTORY_INCOMPLETE;
import static top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.StatusEnum.HISTORICAL_FAILURE;
import static top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.StatusEnum.RETRY_AVAILABLE;
import static top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerResult.StatusEnum.RETRY_SCHEDULED;
import static top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.Kind.PROCESS_WORKER_RECOVER;

class ProcessWorkerRecoveryServiceTest {
    private static final long TENANT = 20;
    private static final String INSTANCE = "proc-1";
    private static final String JOB = "job-1";
    private static final String RELEASE = "release-1";
    private static final String ENGINE_DEFINITION = "process:1:123";
    private static final String ELEMENT = "risk";
    private static final String EXECUTION = "execution-1";
    private static final String BUSINESS_KEY = "order-1";
    private static final String CORRELATION = "trace-1";
    private static final String COMMAND_ID = "recovery-1";
    private static final String CODE = "WORKER_RETRY_REQUIRED";
    private static final String SHA = "b".repeat(64);
    private static final Instant BASE = Instant.parse("2026-11-05T05:00:00Z");
    private static final Instant OBSERVED = BASE.plusSeconds(20);
    private static final Instant DECIDED = BASE.plusSeconds(21);
    private static final ProcessActor OWNER = ProcessActor.verified(TENANT, ProcessActor.SubjectType.USER,
            "process-owner", "https://owner.example");
    private static final ProcessActor SERVICE = ProcessActor.verified(TENANT, ProcessActor.SubjectType.SERVICE,
            "process-service", "https://owner.example");
    private static final ProcessActor REPORTER = ProcessActor.verified(TENANT, ProcessActor.SubjectType.SERVICE,
            "worker-service", "https://reporter.example");
    private static final Definition DEFINITION = new Definition(Type.BPMN, "riskProcess", ENGINE_DEFINITION, "deployment-1",
            1, SHA, Map.of());
    private static final Release RELEASED = new Release(RELEASE, SHA, BASE, List.of(DEFINITION));
    private static final LockedRoot ROOT = new LockedRoot(TENANT, INSTANCE, ENGINE_DEFINITION, BUSINESS_KEY, 8, 1);
    private static final RecoveryExecution NATIVE_EXECUTION = new RecoveryExecution(EXECUTION, ELEMENT, INSTANCE,
            ENGINE_DEFINITION, Long.toString(TENANT));
    private static final RecoveryDeadletter DEADLETTER = deadletter(JOB);

    @Test
    void readsOneBoundedRepeatableSnapshot() throws Exception {
        try (var f = new Fixture()) {
            String bmp = "job-\ue000";
            String supplementary = "job-\ud800\udc00";
            when(f.workers.findFailureJobIds(TENANT, INSTANCE)).thenReturn(List.of(supplementary, JOB, bmp));
            when(f.workers.readRecoveryHistory(TENANT, bmp)).thenReturn(List.of(lease(bmp, REPORTER, SHA)));
            when(f.workers.readRecoveryHistory(TENANT, supplementary)).thenReturn(List.of(lease(supplementary, REPORTER, SHA)));
            doAnswer(call -> {
                f.assertTransaction(true, Connection.TRANSACTION_REPEATABLE_READ);
                return null;
            }).when(f.nativeAccess).verifyRecoveryTransaction();

            var observation = f.observe();
            assertEquals(OBSERVED, observation.observedAt());
            assertEquals(List.of(JOB, bmp, supplementary), observation.failures().stream().map(ProcessWorkerFailureDTO::jobId).toList());
            var eligible = observation.failures().getFirst();
            assertEquals(RETRY_AVAILABLE, eligible.status());
            assertTrue(eligible.nativeDeadletter());
            assertNotNull(eligible.expectedVersion());
            assertEquals(HISTORICAL_FAILURE, observation.failures().get(1).status());
            assertFalse(observation.failures().get(1).nativeDeadletter());
            assertNull(observation.failures().get(1).expectedVersion());
            verify(f.jdbc()).execute("SET LOCAL statement_timeout='5s'");
            verify(f.connection).commit();
            verify(f.nativeAccess, never()).lockRoot(any());
            verify(f.nativeAccess, never()).moveAndVerifyRecovery(any());
        }
        try (var f = new Fixture(SERVICE)) {
            when(f.nativeAccess.readRecoveryRoot(f.start)).thenReturn(Optional.empty());
            when(f.nativeAccess.recoveryHistoryDefinitionId(f.start)).thenReturn(ENGINE_DEFINITION);
            when(f.nativeAccess.findRecoveryDeadletters(f.start)).thenReturn(List.of());
            assertEquals(HISTORICAL_FAILURE, f.observe().failures().getFirst().status());
            verify(f.receipts).readStartByInstance(SERVICE, INSTANCE);
        }
    }

    @Test
    void rejectsEachCandidateOverflowButKeepsPerJobHistoryOverflowManual() throws Exception {
        try (var f = new Fixture()) {
            var nativeIds = IntStream.range(0, 129).mapToObj(i -> deadletter("native-" + i)).toList();
            when(f.nativeAccess.findRecoveryDeadletters(f.start)).thenReturn(nativeIds);
            assertThrows(IllegalStateException.class, () -> f.service.workerFailures(OWNER, query()));
            verify(f.workers, never()).readRecoveryHistory(anyLong(), anyString());

            when(f.nativeAccess.findRecoveryDeadletters(f.start)).thenReturn(List.of(DEADLETTER));
            var historical = IntStream.range(0, 129).mapToObj(i -> "history-" + i).toList();
            when(f.workers.findFailureJobIds(TENANT, INSTANCE)).thenReturn(historical);
            assertThrows(IllegalStateException.class, () -> f.service.workerFailures(OWNER, query()));
            when(f.workers.findFailureJobIds(TENANT, INSTANCE)).thenReturn(historical.subList(0, 128));
            assertThrows(IllegalStateException.class, () -> f.service.workerFailures(OWNER, query()));
            verify(f.workers, never()).readRecoveryHistory(anyLong(), anyString());

            when(f.workers.findFailureJobIds(TENANT, INSTANCE)).thenReturn(List.of(JOB));
            when(f.workers.readRecoveryHistory(TENANT, JOB)).thenReturn(Collections.nCopies(33, lease(JOB, REPORTER, SHA)));
            var incomplete = f.observe().failures().getFirst();
            assertEquals(HISTORY_INCOMPLETE, incomplete.reason());
            assertNull(incomplete.expectedVersion());
        }
    }

    @Test
    void rejectsAmbientTransactionsAndConcealsMissingExactStart() throws Exception {
        try (var f = new Fixture()) {
            TransactionSynchronizationManager.bindResource(f.source, new Object());
            try {
                assertThrows(IllegalStateException.class, () -> f.service.workerFailures(OWNER, query()));
                assertThrows(IllegalStateException.class, () -> f.service.recoverWorker(OWNER, command(SHA)));
            } finally {
                TransactionSynchronizationManager.unbindResource(f.source);
            }
            Context.setCommandContext(mock(CommandContext.class));
            try {
                assertThrows(IllegalStateException.class, () -> f.service.workerFailures(OWNER, query()));
            } finally {
                Context.removeCommandContext();
            }
            verifyNoInteractions(f.receipts);
            when(f.receipts.readStartByInstance(OWNER, INSTANCE)).thenThrow(new ProcessResourceNotFoundException());
            assertThrows(ProcessResourceNotFoundException.class, () -> f.service.workerFailures(OWNER, query()));
            when(f.receipts.lockStartByInstance(OWNER, INSTANCE)).thenThrow(new ProcessResourceNotFoundException());
            assertThrows(ProcessResourceNotFoundException.class, () -> f.service.recoverWorker(OWNER, command(SHA)));
            verify(f.nativeAccess, never()).readRecoveryRoot(any());
            verify(f.nativeAccess, never()).lockRoot(any());
        }
    }

    @Test
    void restoresWithExactLocksReadbackAndAudit() throws Exception {
        try (var f = new Fixture()) {
            var command = f.commandFromObservation();
            clearInvocations(f.receipts, f.nativeAccess, f.workers, f.audit, f.connection, f.jdbc());
            doAnswer(call -> {
                f.assertTransaction(false, Connection.TRANSACTION_READ_COMMITTED);
                return null;
            }).when(f.nativeAccess).verifyRecoveryTransaction();
            var result = assertDoesNotThrow(() -> f.service.recoverWorker(OWNER, command));
            assertEquals(result(), result);
            var auditEntry = ArgumentCaptor.forClass(ProcessWorkerRecoveryReceiptStore.Entry.class);
            var order = inOrder(f.jdbc(), f.receipts, f.nativeAccess, f.workers, f.audit, f.connection);
            order.verify(f.nativeAccess).verifyRecoveryTransaction();
            order.verify(f.jdbc()).execute("SET LOCAL statement_timeout='5s'");
            order.verify(f.jdbc()).execute("SET LOCAL lock_timeout='5s'");
            order.verify(f.receipts).lockStartByInstance(OWNER, INSTANCE);
            order.verify(f.receipts).lockIfPresent(OWNER, PROCESS_WORKER_RECOVER, COMMAND_ID);
            order.verify(f.receipts).insert(OWNER, PROCESS_WORKER_RECOVER, COMMAND_ID, hash(command), RELEASE, CORRELATION);
            order.verify(f.nativeAccess).lockRoot(f.start);
            order.verify(f.nativeAccess).lockRecoveryDeadletter(f.start, DEFINITION, JOB);
            order.verify(f.workers).readRecoveryHistory(TENANT, JOB);
            order.verify(f.nativeAccess).moveAndVerifyRecovery(DEADLETTER);
            order.verify(f.audit).decisionTime();
            order.verify(f.audit).insert(auditEntry.capture());
            order.verify(f.receipts).apply(OWNER, PROCESS_WORKER_RECOVER, COMMAND_ID, JOB, null);
            order.verify(f.connection).commit();
            var accepted = auditEntry.getValue();
            assertEquals("READ_ONLY_EXHAUSTED_RETRY_V1", accepted.admissionPolicy());
            assertEquals(CODE, accepted.failureCode());
            assertEquals(BASE.plusSeconds(2), accepted.failureDecidedAt());
            assertEquals(result, accepted.result());
            verify(f.connection, never()).rollback();
        }
    }

    @Test
    void replaysExactCommandBeforeAnyNativeRead() throws Exception {
        try (var f = new Fixture()) {
            var command = command(SHA);
            var prior = applied(command);
            when(f.receipts.lockIfPresent(OWNER, PROCESS_WORKER_RECOVER, COMMAND_ID)).thenReturn(Optional.of(prior));
            when(f.audit.find(OWNER, f.start, prior, command, hash(command))).thenReturn(result());
            assertEquals(result(), assertDoesNotThrow(() -> f.service.recoverWorker(OWNER, command)));
            var order = inOrder(f.receipts, f.audit, f.connection);
            order.verify(f.receipts).lockStartByInstance(OWNER, INSTANCE);
            order.verify(f.receipts).lockIfPresent(OWNER, PROCESS_WORKER_RECOVER, COMMAND_ID);
            order.verify(f.audit).find(OWNER, f.start, prior, command, hash(command));
            order.verify(f.connection).commit();
            verify(f.nativeAccess).verifyRecoveryTransaction();
            verifyNoMoreInteractions(f.nativeAccess);
            verifyNoInteractions(f.releases, f.workers);
        }
    }

    @Test
    void rechecksWinnerAndRejectsConflictingOrPendingReplay() throws Exception {
        try (var f = new Fixture()) {
            var original = command(SHA);
            var winner = applied(original);
            when(f.receipts.lockIfPresent(OWNER, PROCESS_WORKER_RECOVER, COMMAND_ID)).thenReturn(Optional.empty()).thenReturn(Optional.of(winner));
            when(f.receipts.insert(OWNER, PROCESS_WORKER_RECOVER, COMMAND_ID, hash(original), RELEASE, CORRELATION)).thenReturn(false);
            when(f.audit.find(OWNER, f.start, winner, original, hash(original))).thenReturn(result());
            assertEquals(result(), assertDoesNotThrow(() -> f.service.recoverWorker(OWNER, original)));
            verify(f.receipts, times(2)).lockIfPresent(OWNER, PROCESS_WORKER_RECOVER, COMMAND_ID);

            for (var changed : List.of(command("c".repeat(64)),
                    new RecoverProcessWorkerCommand(INSTANCE, JOB, COMMAND_ID, SHA, "Changed reason"),
                    new RecoverProcessWorkerCommand(INSTANCE, "different-job", COMMAND_ID, SHA, original.reason()))) {
                assertThrows(ProcessConflictException.class, () -> f.service.recoverWorker(OWNER, changed));
            }
            var pending = new Receipt(hash(original), RELEASE, CORRELATION, "PENDING", null, BASE, null);
            when(f.receipts.lockIfPresent(OWNER, PROCESS_WORKER_RECOVER, COMMAND_ID)).thenReturn(Optional.of(pending));
            assertThrows(IllegalStateException.class, () -> f.service.recoverWorker(OWNER, original));
            verify(f.nativeAccess, never()).lockRoot(any());
            verify(f.nativeAccess, never()).moveAndVerifyRecovery(any());
        }
    }

    @Test
    void rollsBackBlockedStaleAndEveryPostMoveFailure() throws Exception {
        try (var f = new Fixture()) {
            when(f.nativeAccess.lockRecoveryDeadletter(f.start, DEFINITION, "foreign-job")).thenReturn(Optional.empty());
            var foreign = new RecoverProcessWorkerCommand(INSTANCE, "foreign-job", COMMAND_ID, SHA, "Read-only check");
            assertThrows(ProcessWorkerRecoveryBlockedException.class, () -> f.service.recoverWorker(OWNER, foreign));
            verify(f.nativeAccess).lockRecoveryDeadletter(f.start, DEFINITION, "foreign-job");
            verify(f.workers, never()).readRecoveryHistory(anyLong(), anyString());
            assertThrows(ProcessVersionConflictException.class, () -> f.service.recoverWorker(OWNER, command("0".repeat(64))));
            verify(f.connection, times(2)).rollback();
            verify(f.nativeAccess, never()).moveAndVerifyRecovery(any());
        }
        for (String failureStage : List.of("readback", "decision-time", "audit-unique", "audit-integrity", "apply", "commit")) {
            try (var f = new Fixture()) {
                var command = f.commandFromObservation();
                clearInvocations(f.connection);
                Class<? extends RuntimeException> expected = IllegalStateException.class;
                switch (failureStage) {
                    case "readback" -> doThrow(new IllegalStateException("test readback failure")).when(f.nativeAccess).moveAndVerifyRecovery(DEADLETTER);
                    case "decision-time" -> when(f.audit.decisionTime()).thenThrow(new IllegalStateException("test clock failure"));
                    case "audit-unique" -> {
                        when(f.audit.insert(any())).thenReturn(false);
                        expected = ProcessWorkerRecoveryBlockedException.class;
                    }
                    case "audit-integrity" -> when(f.audit.insert(any())).thenThrow(sql("23505"));
                    case "apply" -> doThrow(new IllegalStateException("test apply count")).when(f.receipts)
                            .apply(OWNER, PROCESS_WORKER_RECOVER, COMMAND_ID, JOB, null);
                    case "commit" -> doThrow(new SQLException("test connection failure", "08006")).when(f.connection).commit();
                }
                assertThrows(expected, () -> f.service.recoverWorker(OWNER, command), failureStage);
                verify(f.nativeAccess).moveAndVerifyRecovery(DEADLETTER);
                if ("commit".equals(failureStage)) {
                    verify(f.connection).commit();
                } else {
                    verify(f.connection).rollback();
                    verify(f.connection, never()).commit();
                }
                if (!List.of("apply", "commit").contains(failureStage)) {
                    verify(f.receipts, never()).apply(any(), any(), any(), any(), any());
                }
            }
        }
    }

    @Test
    void classifiesOnlyRecognizedRacesAfterRollback() throws Exception {
        for (String state : List.of("40001", "40P01", "55P03", "57014", "08006", "23505")) {
            try (var f = new Fixture()) {
                var command = f.commandFromObservation();
                clearInvocations(f.connection);
                doThrow(sql(state)).when(f.nativeAccess).moveAndVerifyRecovery(DEADLETTER);
                Class<? extends RuntimeException> type = List.of("40001", "40P01", "55P03").contains(state)
                        ? ProcessVersionConflictException.class : IllegalStateException.class;
                assertThrows(type, () -> f.service.recoverWorker(OWNER, command));
                verify(f.connection).rollback();
                verify(f.connection, never()).commit();
                verify(f.nativeAccess).moveAndVerifyRecovery(DEADLETTER);
                verify(f.audit, never()).insert(any());
            }
        }
    }

    @Test
    void readsV4SqlWithoutSecretsOrPrematureStateFilters() {
        var source = mock(DataSource.class);
        try (var statements = mockConstruction(JdbcTemplate.class)) {
            var leases = new WorkerLeaseStore(source, new DataSourceTransactionManager(source), "tianji_process");
            var jdbc = statements.constructed().getFirst();
            when(jdbc.queryForList(anyString(), eq(String.class), eq(TENANT), eq(INSTANCE))).thenReturn(List.of(JOB));
            when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<RecoveryLeaseEvidence>>any(), eq(TENANT), eq(JOB)))
                    .thenReturn(List.of(lease(JOB, REPORTER, SHA)));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.bindResource(source, new Object());
            try {
                assertEquals(List.of(JOB), assertDoesNotThrow(() -> leases.findFailureJobIds(TENANT, INSTANCE)));
                assertEquals(List.of(lease(JOB, REPORTER, SHA)), assertDoesNotThrow(() -> leases.readRecoveryHistory(TENANT, JOB)));
            } finally {
                TransactionSynchronizationManager.unbindResource(source);
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
            var sql = ArgumentCaptor.forClass(String.class);
            verify(jdbc).queryForList(sql.capture(), eq(String.class), eq(TENANT), eq(INSTANCE));
            assertTrue(sql.getValue().contains("scope_type='BPMN' AND scope_id=? AND failure_kind IS NOT NULL"));
            assertTrue(sql.getValue().contains("COLLATE \"C\" LIMIT 129"));
            verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<RecoveryLeaseEvidence>>any(), eq(TENANT), eq(JOB));
            String history = sql.getValue();
            assertTrue(history.endsWith("WHERE tenant_id=? AND job_id=? LIMIT 33"));
            assertTrue(history.contains("business_receipt_id IS NOT NULL AS has_business_receipt"));
            assertFalse(history.contains("token_sha256"));
            assertFalse(history.contains("native_owner"));
        }
    }

    @Test
    void provesBindingUsingCurrentExactReporterAndWorkerInputDigest() {
        var source = mock(DataSource.class);
        var engine = mock(SpringProcessEngineConfiguration.class);
        var management = mock(ManagementService.class);
        var runtime = mock(RuntimeService.class);
        var query = mock(ProcessInstanceQuery.class, RETURNS_SELF);
        var process = mock(ProcessInstance.class);
        var model = mock(ValidatedProcessModel.class);
        when(engine.getTransactionManager()).thenReturn(new DataSourceTransactionManager(source));
        when(engine.getDatabaseSchema()).thenReturn("tianji_process");
        when(engine.getManagementService()).thenReturn(management);
        when(engine.getRuntimeService()).thenReturn(runtime);
        when(runtime.createProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(process);
        when(process.getProcessDefinitionId()).thenReturn(ENGINE_DEFINITION);
        when(process.getBusinessKey()).thenReturn(BUSINESS_KEY);
        when(model.workerTasks()).thenReturn(Map.of(ELEMENT,
                new WorkerTask("riskCheck", "1", WorkerEffect.READ_ONLY, List.of(), List.of())));
        var registration = new WorkerTopicRegistry.Registration(TENANT, "https://reporter.example", REPORTER.subjectId(),
                "riskCheck", "1", Map.of(), Map.of(), Map.of(), Map.of(), 1, Duration.ofSeconds(5), Duration.ZERO, WorkerEffect.READ_ONLY);
        var registry = new WorkerTopicRegistry(List.of(registration));
        var inputDigest = ProcessRequestDigest.hash(List.of("worker-input-v2", JOB, INSTANCE, ENGINE_DEFINITION, ELEMENT,
                "riskCheck", "1", "READ_ONLY", "1", "5000", "0", "0", "0"), Map.of());
        try (var schemas = mockConstruction(ProcessApiSchemaVerifier.class);
             var releases = mockConstruction(ProcessReleaseService.class, (mock, context) ->
                     when(mock.getForProcessDefinition(TENANT, ENGINE_DEFINITION)).thenReturn(RELEASED));
             var models = mockConstruction(VerifiedProcessModels.class, (mock, context) ->
                     when(mock.runningProcess(TENANT, DEFINITION)).thenReturn(model));
             var waits = mockConstruction(FlowableAgentWaitInspector.class);
             var jdbc = mockConstruction(JdbcTemplate.class, (mock, context) ->
                     when(mock.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(),
                             eq(TENANT), eq(INSTANCE), eq(RELEASE))).thenReturn(List.of(CORRELATION)))) {
            var workers = new FlowableWorkerService(engine, mock(SpringDmnEngineConfiguration.class), mock(ProcessModelValidator.class), registry);
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.bindResource(source, new Object());
            try {
                var start = origin(OWNER);
                var provenance = assertDoesNotThrow(() -> workers.readRecoveryProvenance(start, DEFINITION, DEADLETTER));
                assertTrue(provenance.ordinary());
                var proof = workers.verifyRecoveryBinding(start, DEFINITION, DEADLETTER, provenance, lease(JOB, REPORTER, inputDigest));
                assertEquals(RecoveryBinding.verified(WorkerEffect.READ_ONLY, BUSINESS_KEY), proof);
                assertTrue(workers.verifyRecoveryBinding(start, DEFINITION, DEADLETTER, provenance, lease(JOB, REPORTER, SHA)).provesUnverified());
                assertTrue(workers.verifyRecoveryBinding(start, DEFINITION, DEADLETTER, provenance, lease(JOB, SERVICE, inputDigest)).provesUnverified());
                when(query.singleResult()).thenReturn(null);
                assertTrue(workers.verifyRecoveryBinding(start, DEFINITION, DEADLETTER, provenance,
                        lease(JOB, REPORTER, inputDigest)).provesUnverified());
                verifyNoInteractions(management);
            } finally {
                TransactionSynchronizationManager.unbindResource(source);
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        }
    }

    private static GetProcessInstanceQuery query() {
        return new GetProcessInstanceQuery(INSTANCE);
    }

    private static TaskOrigin origin(ProcessActor actor) {
        return new TaskOrigin(actor, "start-1", SHA, RELEASE, CORRELATION, INSTANCE, BASE);
    }

    private static RecoveryDeadletter deadletter(String jobId) {
        return new RecoveryDeadletter(jobId, 7, "externalWorker", Long.toString(TENANT), INSTANCE, ENGINE_DEFINITION,
                EXECUTION, ELEMENT, null, null, null, null, null, "external-worker-complete", "riskCheck:1");
    }

    private static RecoveryLeaseEvidence lease(String jobId, ProcessActor reporter, String inputSha) {
        String resultSha = ProcessRequestDigest.hash(List.of("worker-fail-v1", jobId, jobId, "RETRYABLE", CODE), Map.of());
        return new RecoveryLeaseEvidence(TENANT, reporter.issuerFingerprint(), reporter.subjectId(), "riskCheck", "1",
                jobId, jobId, "BPMN", INSTANCE, RELEASE, ENGINE_DEFINITION, ELEMENT, FlowableWorkerService.workerOccurrenceKey(TENANT, jobId),
                SHA, inputSha, resultSha, "NEEDS_MANUAL", BASE.plusSeconds(2), "RETRYABLE", CODE,
                BASE.plusSeconds(1), BASE.plusSeconds(3), false);
    }

    private static RecoverProcessWorkerCommand command(String version) {
        return new RecoverProcessWorkerCommand(INSTANCE, JOB, COMMAND_ID, version, "Read-only check");
    }

    private static RecoverProcessWorkerResult result() {
        return new RecoverProcessWorkerResult(INSTANCE, JOB, JOB, COMMAND_ID, RETRY_SCHEDULED, 1, DECIDED);
    }

    private static Receipt applied(RecoverProcessWorkerCommand command) {
        return new Receipt(hash(command), RELEASE, CORRELATION, "APPLIED", JOB, BASE, null);
    }

    private static String hash(RecoverProcessWorkerCommand command) {
        return ProcessRequestDigest.hash(List.of("TIANJI_WORKER_RECOVERY_COMMAND_V1", command.instanceId(),
                command.jobId(), command.commandId(), command.expectedVersion(), command.reason()), Map.of());
    }

    private static RuntimeException sql(String state) {
        return new DataAccessResourceFailureException("test dependency failure", new SQLException("test SQL failure", state));
    }

    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        final SpringProcessEngineConfiguration engine = mock(SpringProcessEngineConfiguration.class);
        final ProcessReleaseService releases = mock(ProcessReleaseService.class);
        final ProcessCommandReceiptStore receipts = mock(ProcessCommandReceiptStore.class);
        final ProcessControlNativeAccess nativeAccess = mock(ProcessControlNativeAccess.class);
        final ProcessWorkerRecoveryReceiptStore audit = mock(ProcessWorkerRecoveryReceiptStore.class);
        final FlowableWorkerService workers = mock(FlowableWorkerService.class);
        final RecoveryProvenance provenance = mock(RecoveryProvenance.class);
        final ProcessActor actor;
        final TaskOrigin start;
        final MockedConstruction<JdbcTemplate> statements;
        final ProcessWorkerRecoveryService service;

        Fixture() throws Exception {
            this(OWNER);
        }

        Fixture(ProcessActor actor) throws Exception {
            this.actor = actor;
            start = origin(actor);
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            when(engine.getDataSource()).thenReturn(source);
            when(engine.getTransactionManager()).thenReturn(manager);
            when(nativeAccess.usesEngine(engine)).thenReturn(true);
            when(nativeAccess.usesDataSource(source)).thenReturn(true);
            when(audit.usesDataSource(source)).thenReturn(true);
            when(workers.usesRecoveryStorage(engine, source)).thenReturn(true);
            when(receipts.readStartByInstance(actor, INSTANCE)).thenReturn(start);
            when(receipts.lockStartByInstance(actor, INSTANCE)).thenReturn(start);
            when(receipts.lockIfPresent(actor, PROCESS_WORKER_RECOVER, COMMAND_ID)).thenReturn(Optional.empty());
            when(receipts.insert(eq(actor), eq(PROCESS_WORKER_RECOVER), eq(COMMAND_ID), anyString(), eq(RELEASE), eq(CORRELATION))).thenReturn(true);
            when(releases.get(actor, RELEASE)).thenReturn(RELEASED);
            when(releases.getForProcessDefinition(TENANT, ENGINE_DEFINITION)).thenReturn(RELEASED);
            when(nativeAccess.readRecoveryRoot(start)).thenReturn(Optional.of(ROOT));
            when(nativeAccess.lockRoot(start)).thenReturn(Optional.of(ROOT));
            when(nativeAccess.findRecoveryDeadletters(start)).thenReturn(List.of(DEADLETTER));
            when(nativeAccess.lockRecoveryDeadletter(start, DEFINITION, JOB)).thenReturn(Optional.of(DEADLETTER));
            when(nativeAccess.readRecoveryExecution(start, DEFINITION, DEADLETTER)).thenReturn(Optional.of(NATIVE_EXECUTION));
            when(nativeAccess.recoveryFailureCodeMatches(DEADLETTER, CODE)).thenReturn(true);
            when(nativeAccess.now()).thenReturn(OBSERVED);
            when(workers.findFailureJobIds(TENANT, INSTANCE)).thenReturn(List.of(JOB));
            var selected = lease(JOB, REPORTER, SHA);
            when(workers.readRecoveryHistory(TENANT, JOB)).thenReturn(List.of(selected));
            when(provenance.ordinary()).thenReturn(true);
            when(workers.readRecoveryProvenance(start, DEFINITION, DEADLETTER)).thenReturn(provenance);
            when(workers.verifyRecoveryBinding(start, DEFINITION, DEADLETTER, provenance, selected))
                    .thenReturn(RecoveryBinding.verified(WorkerEffect.READ_ONLY, BUSINESS_KEY));
            when(audit.decisionTime()).thenReturn(DECIDED);
            when(audit.insert(any())).thenReturn(true);
            statements = mockConstruction(JdbcTemplate.class);
            service = new ProcessWorkerRecoveryService(engine, releases, receipts, nativeAccess, audit, workers);
            clearInvocations(nativeAccess, audit, workers);
        }

        JdbcTemplate jdbc() {
            return statements.constructed().getFirst();
        }

        ProcessWorkerFailuresDTO observe() {
            return assertDoesNotThrow(() -> service.workerFailures(actor, query()));
        }

        RecoverProcessWorkerCommand commandFromObservation() {
            var version = observe().failures().getFirst().expectedVersion();
            assertNotNull(version, "eligible observation must have a version");
            return command(version);
        }

        void assertTransaction(boolean readOnly, int isolation) {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(readOnly, TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(isolation, TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            var holder = assertInstanceOf(ConnectionHolder.class, TransactionSynchronizationManager.getResource(source));
            assertTrue(holder.getTimeToLiveInSeconds() > 0 && holder.getTimeToLiveInSeconds() <= 10);
        }

        @Override
        public void close() {
            statements.close();
        }
    }
}
