package top.egon.cola.platform.tianji.process.server.worker;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ExternalWorkerCompletionBuilder;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.job.api.AcquiredExternalWorkerJob;
import org.flowable.job.api.ExternalWorkerJob;
import org.flowable.job.api.ExternalWorkerJobAcquireBuilder;
import org.flowable.job.api.ExternalWorkerJobFailureBuilder;
import org.flowable.job.api.ExternalWorkerJobQuery;
import org.flowable.job.api.Job;
import org.flowable.job.api.JobQuery;
import org.flowable.job.service.impl.asyncexecutor.AsyncExecutor;
import org.flowable.job.service.impl.asyncexecutor.AsyncJobExecutorConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedConstruction;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.FailWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireCommand;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchBinding;
import top.egon.cola.platform.tianji.process.server.bridge.FlowableAgentWaitInspector;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.WorkerLeaseLostException;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.*;

class FlowableWorkerServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00.123456Z");
    private static final String JOB = "worker-job";
    private static final String INSTANCE = "process-one";
    private static final String EXECUTION = "worker-execution";
    private static final String DEFINITION = "workerBusinessError:1:definition";
    private static final String ELEMENT = "worker";
    private static final String RELEASE = "business-error-release";
    private static final String MODEL_SHA = "a".repeat(64);
    private static final String MODEL_CODE = "ORDER_REJECTED";
    private static final String DIAGNOSTIC = "CUSTOMER_INELIGIBLE";
    private static final ProcessActor ACTOR = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE,
            "worker-service", "https://idp.example/20");

    @Test
    void acquisition_requires_live_native_job_processing_and_expiry_reset() {
        var engine = mock(SpringProcessEngineConfiguration.class);
        var executor = mock(AsyncExecutor.class);
        var configuration = mock(AsyncJobExecutorConfiguration.class);
        when(engine.isAsyncExecutorActivate()).thenReturn(true);
        when(engine.getAsyncExecutor()).thenReturn(executor);
        when(executor.isActive()).thenReturn(true);
        when(engine.getAsyncExecutorConfiguration()).thenReturn(configuration);
        when(configuration.isAsyncJobAcquisitionEnabled()).thenReturn(true);
        when(configuration.isResetExpiredJobEnabled()).thenReturn(true);
        assertDoesNotThrow(() -> WorkerNativeAvailability.requireConfigured(engine));
        assertDoesNotThrow(() -> WorkerNativeAvailability.require(engine));
        when(configuration.isResetExpiredJobEnabled()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> WorkerNativeAvailability.requireConfigured(engine));
        assertThrows(IllegalStateException.class, () -> WorkerNativeAvailability.require(engine));
        when(configuration.isResetExpiredJobEnabled()).thenReturn(true);
        when(configuration.isAsyncJobAcquisitionEnabled()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> WorkerNativeAvailability.require(engine));
        when(configuration.isAsyncJobAcquisitionEnabled()).thenReturn(true);
        when(executor.isActive()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> WorkerNativeAvailability.require(engine));
    }

    @Test
    void a_legacy_grant_for_the_same_capability_cannot_acquire_a_typed_job() throws Exception {
        try (var fixture = new Fixture(false)) {
            assertThrowsExactly(ProcessResourceNotFoundException.class,
                    () -> fixture.service.acquireAutomation(ACTOR, new AutomationWorkerAcquireCommand("riskCheck", 1)));
            verifyNoInteractions(fixture.management, fixture.store());
        }
    }

    @Test
    void declared_business_rejection_uses_only_model_code_and_commits_scheduling_with_receipt() throws Exception {
        try (var f = new Fixture(false)) {
            var command = f.command(WorkerFailureKindEnum.BUSINESS_REJECTED, DIAGNOSTIC);
            WorkerReceiptDTO result = assertDoesNotThrow(() -> f.service.fail(ACTOR, command));
            assertNotNull(result);
            assertEquals(WorkerReceiptOutcomeEnum.BUSINESS_REJECTED, result.outcome());
            assertEquals(NOW, result.decidedAt());
            var order = inOrder(f.store(), f.completion, f.oldQuery, f.newQuery, f.connection);
            order.verify(f.store()).find(ACTOR, JOB, f.tokenHash());
            order.verify(f.store()).lock(ACTOR, JOB, f.tokenHash());
            order.verify(f.completion).bpmnError(MODEL_CODE);
            order.verify(f.oldQuery).jobId(JOB);
            order.verify(f.oldQuery).listPage(0, 2);
            order.verify(f.newQuery).jobId(JOB);
            order.verify(f.newQuery).listPage(0, 2);
            order.verify(f.store()).finish(eq(ACTOR), eq(JOB), eq(f.tokenHash()), argThat(finish ->
                    finish.outcome() == WorkerReceiptOutcomeEnum.BUSINESS_REJECTED
                            && finish.failureKind() == WorkerFailureKindEnum.BUSINESS_REJECTED
                            && DIAGNOSTIC.equals(finish.failureCode()) && finish.businessReceiptId() == null));
            order.verify(f.connection).commit();
            verify(f.connection, never()).rollback();
            verify(f.completion, never()).variables(anyMap());
            verify(f.completion, never()).variable(anyString(), any());
            verify(f.completion, never()).complete();
            verify(f.failure, never()).fail();
            verify(f.management, never()).executeJob(anyString());
            verifyNoMoreInteractions(f.oldQuery, f.newQuery);
        }
    }

    @Test
    void native_command_failure_preserves_the_original_exception_and_rolls_back() throws Exception {
        try (var f = new Fixture(false)) {
            var nativeFailure = new DataAccessResourceFailureException("private native dependency failure");
            doThrow(nativeFailure).when(f.completion).bpmnError(MODEL_CODE);
            var thrown = assertThrows(DataAccessResourceFailureException.class,
                    () -> f.service.fail(ACTOR, f.command(WorkerFailureKindEnum.BUSINESS_REJECTED, DIAGNOSTIC)));
            assertSame(nativeFailure, thrown);
            verify(f.connection).rollback();
            verify(f.connection, never()).commit();
            verify(f.store(), never()).finish(any(), anyString(), anyString(), any());
            verifyNoInteractions(f.oldQuery, f.newQuery);
        }
    }

    @Test
    void native_void_quiet_noop_cannot_finish_a_business_receipt() throws Exception {
        try (var f = new Fixture(false)) {
            f.quietNoOp = true;
            assertThrowsExactly(IllegalStateException.class,
                    () -> f.service.fail(ACTOR, f.command(WorkerFailureKindEnum.BUSINESS_REJECTED, DIAGNOSTIC)));
            verify(f.store(), never()).finish(any(), anyString(), anyString(), any());
            verify(f.connection).rollback();
            verify(f.connection, never()).commit();
        }
    }

    @Test
    void old_same_id_external_presence_is_rejected_even_when_its_affinity_drifted() {
        List<Executable> checks = new ArrayList<>();
        for (String field : List.of("none", "tenant", "instance", "definition", "execution", "element")) {
            checks.add(() -> {
                try (var f = new Fixture(false)) {
                    if (!"none".equals(field)) f.oldFields.put(field, "foreign");
                    f.oldAfter = List.of(f.oldExternal);
                    assertThrowsExactly(IllegalStateException.class,
                            () -> f.service.fail(ACTOR, f.command(WorkerFailureKindEnum.BUSINESS_REJECTED, DIAGNOSTIC)), field);
                    verify(f.store(), never()).finish(any(), anyString(), anyString(), any());
                    verify(f.connection).rollback();
                    verify(f.connection, never()).commit();
                }
            });
        }
        assertAll(checks);
    }

    @Test
    void executable_postcondition_compares_all_fields_after_an_id_only_query() {
        List<Executable> checks = new ArrayList<>();
        for (String field : List.of("id", "tenant", "instance", "definition", "execution", "element", "handler", "config")) {
            checks.add(() -> {
                try (var f = new Fixture(false)) {
                    f.targetFields.put(field, "foreign");
                    assertThrowsExactly(IllegalStateException.class,
                            () -> f.service.fail(ACTOR, f.command(WorkerFailureKindEnum.BUSINESS_REJECTED, DIAGNOSTIC)), field);
                    verify(f.store(), never()).finish(any(), anyString(), anyString(), any());
                    verify(f.connection).rollback();
                    verify(f.connection, never()).commit();
                }
            });
        }
        assertAll(checks);
    }

    @Test
    void absent_or_duplicate_executable_postcondition_rolls_back_without_a_receipt() {
        assertAll(
                () -> {
                    try (var f = new Fixture(false)) {
                        f.newAfter = List.of();
                        f.assertPostconditionFailure();
                    }
                },
                () -> {
                    try (var f = new Fixture(false)) {
                        f.newAfter = List.of(f.executable, f.executable);
                        f.assertPostconditionFailure();
                    }
                });
    }

    @Test
    void receipt_failure_after_native_scheduling_rolls_back_the_owned_transaction() throws Exception {
        try (var f = new Fixture(false)) {
            f.finishFailure = new IllegalStateException("receipt dependency unavailable");
            assertThrowsExactly(IllegalStateException.class,
                    () -> f.service.fail(ACTOR, f.command(WorkerFailureKindEnum.BUSINESS_REJECTED, DIAGNOSTIC)));
            verify(f.completion).bpmnError(MODEL_CODE);
            verify(f.connection).rollback();
            verify(f.connection, never()).commit();
        }
    }

    @Test
    void terminal_failure_replay_returns_the_stored_receipt_before_any_native_access() throws Exception {
        try (var f = new Fixture(false)) {
            var command = f.command(WorkerFailureKindEnum.BUSINESS_REJECTED, DIAGNOSTIC);
            f.seedTerminal(command);
            f.clearCalls();
            WorkerReceiptDTO replay = assertDoesNotThrow(() -> f.service.fail(ACTOR, command));
            assertEquals(new WorkerReceiptDTO(JOB, JOB, WorkerReceiptOutcomeEnum.BUSINESS_REJECTED, NOW), replay);
            verifyNoInteractions(f.management, f.jdbc(), f.runtime);
            verify(f.store(), never()).isLive(any(), anyString(), anyString());
            verify(f.connection).commit();
        }
    }

    @Test
    void changed_diagnostic_conflicts_with_a_terminal_failure_without_native_access() throws Exception {
        try (var f = new Fixture(false)) {
            f.seedTerminal(f.command(WorkerFailureKindEnum.BUSINESS_REJECTED, DIAGNOSTIC));
            f.clearCalls();
            assertThrows(ProcessConflictException.class,
                    () -> f.service.fail(ACTOR, f.command(WorkerFailureKindEnum.BUSINESS_REJECTED, "DIFFERENT_DIAGNOSTIC")));
            verifyNoInteractions(f.management, f.jdbc(), f.runtime);
            verify(f.connection).rollback();
        }
    }

    @Test
    void stale_or_drifted_authority_never_schedules_or_finishes_a_business_error() {
        List<FenceCase> mutations = List.of(
                new FenceCase("missing native", f -> f.nativePresent = false, WorkerLeaseLostException.class),
                new FenceCase("expired native", f -> f.nativeLive = false, WorkerLeaseLostException.class),
                new FenceCase("expired lease", f -> f.leaseLive = false, WorkerLeaseLostException.class),
                new FenceCase("native owner", f -> f.nativeOwner = "foreign-owner", WorkerLeaseLostException.class),
                new FenceCase("process affinity", f -> f.nativeInstance = "foreign-instance", ProcessConflictException.class),
                new FenceCase("definition affinity", f -> f.nativeDefinition = "foreign-definition", ProcessConflictException.class),
                new FenceCase("element affinity", f -> f.nativeElement = "foreign-element", ProcessConflictException.class),
                new FenceCase("input hash", f -> f.inputAmount = 11L, ProcessConflictException.class),
                new FenceCase("model hash", f -> f.binding = new ProcessReleaseService.Definition(ValidatedProcessModel.Type.BPMN,
                        "workerBusinessError", DEFINITION, "deployment", 1, "b".repeat(64), Map.of("amount", INTEGER)), ProcessConflictException.class),
                new FenceCase("schema", f -> f.workerMetadata = new ValidatedProcessModel.WorkerTask("riskCheck", "2",
                        ValidatedProcessModel.WorkerEffect.TRANSACTIONAL_RECEIPT, f.workerMetadata.inputs(), f.workerMetadata.outputs()),
                        ProcessResourceNotFoundException.class));
        List<Executable> checks = new ArrayList<>();
        for (var mutation : mutations) checks.add(() -> {
            try (var f = new Fixture(false)) {
                mutation.mutate().accept(f);
                assertThrowsExactly(mutation.failure(),
                        () -> f.service.fail(ACTOR, f.command(WorkerFailureKindEnum.BUSINESS_REJECTED, DIAGNOSTIC)), mutation.name());
                verify(f.completion, never()).bpmnError(anyString());
                verify(f.failure, never()).fail();
                verify(f.store(), never()).finish(any(), anyString(), anyString(), any());
                verify(f.connection).rollback();
            }
        });
        assertAll(checks);
    }

    @Test
    void forged_actor_and_occurrence_are_rejected_before_native_mutation() throws Exception {
        try (var f = new Fixture(false)) {
            var foreign = ProcessActor.verified(21, ProcessActor.SubjectType.SERVICE, "worker-service", "https://idp.example/21");
            assertThrows(ProcessResourceNotFoundException.class,
                    () -> f.service.fail(foreign, f.command(WorkerFailureKindEnum.BUSINESS_REJECTED, DIAGNOSTIC)));
            verify(f.completion, never()).bpmnError(anyString());
        }
        try (var f = new Fixture(false)) {
            var command = new FailWorkerTaskCommand(JOB, "foreign-occurrence", f.task.leaseToken(),
                    WorkerFailureKindEnum.BUSINESS_REJECTED, DIAGNOSTIC);
            assertThrows(ProcessConflictException.class, () -> f.service.fail(ACTOR, command));
            verify(f.completion, never()).bpmnError(anyString());
            verifyNoInteractions(f.jdbc());
        }
    }

    @Test
    void unknown_and_undeclared_business_rejection_preserve_manual_deadletter_behavior() throws Exception {
        for (var kind : List.of(WorkerFailureKindEnum.UNKNOWN, WorkerFailureKindEnum.BUSINESS_REJECTED)) {
            try (var f = new Fixture(false)) {
                f.profile = false;
                var result = f.service.fail(ACTOR, f.command(kind, DIAGNOSTIC));
                assertEquals(WorkerReceiptOutcomeEnum.NEEDS_MANUAL, result.outcome());
                verify(f.failure).errorMessage(DIAGNOSTIC);
                verify(f.failure).retries(0);
                verify(f.failure).fail();
                verify(f.completion, never()).bpmnError(anyString());
            }
        }
    }

    @Test
    void unknown_with_a_declared_profile_still_requires_manual_recovery() throws Exception {
        try (var f = new Fixture(false)) {
            var result = f.service.fail(ACTOR, f.command(WorkerFailureKindEnum.UNKNOWN, DIAGNOSTIC));
            assertEquals(WorkerReceiptOutcomeEnum.NEEDS_MANUAL, result.outcome());
            verify(f.failure).retries(0);
            verify(f.failure).fail();
            verify(f.completion, never()).bpmnError(anyString());
        }
    }

    @Test
    void internal_agent_dispatch_never_routes_even_with_inconsistent_declared_metadata() throws Exception {
        try (var f = new Fixture(true)) {
            var result = f.service.failAgentDispatch(ACTOR, f.command(WorkerFailureKindEnum.BUSINESS_REJECTED, DIAGNOSTIC));
            assertEquals(WorkerReceiptOutcomeEnum.NEEDS_MANUAL, result.outcome());
            verify(f.failure).retries(0);
            verify(f.failure).fail();
            verify(f.completion, never()).bpmnError(anyString());
            verify(f.management, never()).createJobQuery();
        }
    }

    @Test
    void retryable_failures_keep_the_existing_retry_and_exhausted_manual_paths() throws Exception {
        try (var f = new Fixture(false)) {
            var result = f.service.fail(ACTOR, f.command(WorkerFailureKindEnum.RETRYABLE, "TECHNICAL_FAILURE"));
            assertEquals(WorkerReceiptOutcomeEnum.RETRY_SCHEDULED, result.outcome());
            verify(f.failure).retries(2);
            verify(f.failure).retryTimeout(Duration.ofSeconds(1));
            verify(f.completion, never()).bpmnError(anyString());
        }
        try (var f = new Fixture(false)) {
            f.retries = 1;
            var result = f.service.fail(ACTOR, f.command(WorkerFailureKindEnum.RETRYABLE, "TECHNICAL_FAILURE"));
            assertEquals(WorkerReceiptOutcomeEnum.NEEDS_MANUAL, result.outcome());
            verify(f.failure).retries(0);
            verify(f.failure, never()).retryTimeout(any());
            verify(f.completion, never()).bpmnError(anyString());
        }
    }

    private record FenceCase(String name, Consumer<Fixture> mutate, Class<? extends RuntimeException> failure) { }

    /** Public acquisition supplies the existing lease/input hash rather than mirroring its private algorithm. */
    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        final SpringProcessEngineConfiguration process = mock(SpringProcessEngineConfiguration.class);
        final SpringDmnEngineConfiguration dmn = mock(SpringDmnEngineConfiguration.class);
        final ManagementService management = mock(ManagementService.class);
        final RuntimeService runtime = mock(RuntimeService.class);
        final ProcessInstanceQuery instances = mock(ProcessInstanceQuery.class, RETURNS_SELF);
        final ProcessInstance instance = mock(ProcessInstance.class);
        final ProcessModelValidator validator = mock(ProcessModelValidator.class);
        final ValidatedProcessModel model = mock(ValidatedProcessModel.class);
        final AcquiredExternalWorkerJob acquired = mock(AcquiredExternalWorkerJob.class);
        final ExternalWorkerJobAcquireBuilder acquire = mock(ExternalWorkerJobAcquireBuilder.class, RETURNS_SELF);
        final ExternalWorkerCompletionBuilder completion = mock(ExternalWorkerCompletionBuilder.class, RETURNS_SELF);
        final ExternalWorkerJobFailureBuilder failure = mock(ExternalWorkerJobFailureBuilder.class, RETURNS_SELF);
        final ExternalWorkerJobQuery oldQuery = mock(ExternalWorkerJobQuery.class, RETURNS_SELF);
        final JobQuery newQuery = mock(JobQuery.class, RETURNS_SELF);
        final ExternalWorkerJob oldExternal = mock(ExternalWorkerJob.class);
        final Job executable = mock(Job.class);
        final Map<String, String> oldFields = fields();
        final Map<String, String> targetFields = fields();
        MockedConstruction<ProcessReleaseService> releaseConstruction;
        MockedConstruction<VerifiedProcessModels> modelConstruction;
        MockedConstruction<FlowableAgentWaitInspector> waitConstruction;
        MockedConstruction<ProcessApiSchemaVerifier> schemaConstruction;
        MockedConstruction<WorkerLeaseStore> storeConstruction;
        MockedConstruction<JdbcTemplate> jdbcConstruction;
        FlowableWorkerService service;
        WorkerTaskDTO task;
        WorkerLeaseStore.Lease lease;
        ProcessReleaseService.Definition binding = new ProcessReleaseService.Definition(ValidatedProcessModel.Type.BPMN,
                "workerBusinessError", DEFINITION, "deployment", 1, MODEL_SHA, Map.of("amount", INTEGER));
        ValidatedProcessModel.WorkerTask workerMetadata;
        List<ExternalWorkerJob> oldAfter = List.of();
        List<Job> newAfter;
        RuntimeException finishFailure;
        boolean nativePresent = true;
        boolean nativeLive = true;
        boolean leaseLive = true;
        boolean profile = true;
        boolean scheduled;
        boolean quietNoOp;
        String nativeOwner;
        String nativeInstance = INSTANCE;
        String nativeDefinition = DEFINITION;
        String nativeElement = ELEMENT;
        long inputAmount = 10L;
        int retries = 3;

        Fixture(boolean internal) throws Exception {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            when(process.getTransactionManager()).thenReturn(manager);
            when(process.getDataSource()).thenReturn(source);
            when(process.getDatabaseSchema()).thenReturn("tianji_process");
            when(process.getManagementService()).thenReturn(management);
            when(process.getRuntimeService()).thenReturn(runtime);
            var history = mock(HistoryService.class);
            when(process.getHistoryService()).thenReturn(history);
            when(process.isDbHistoryUsed()).thenReturn(true);
            when(process.getHistoryLevel()).thenReturn(HistoryLevel.ACTIVITY);
            var executor = mock(AsyncExecutor.class);
            var configuration = mock(AsyncJobExecutorConfiguration.class);
            when(process.isAsyncExecutorActivate()).thenReturn(true);
            when(process.getAsyncExecutor()).thenReturn(executor);
            when(executor.isActive()).thenReturn(true);
            when(process.getAsyncExecutorConfiguration()).thenReturn(configuration);
            when(configuration.isAsyncJobAcquisitionEnabled()).thenReturn(true);
            when(configuration.isResetExpiredJobEnabled()).thenReturn(true);
            when(management.createExternalWorkerJobAcquireBuilder()).thenReturn(acquire);
            when(acquire.acquireAndLock(eq(1), anyString(), eq(1))).thenAnswer(call -> {
                nativeOwner = call.getArgument(1);
                return List.of(acquired);
            });
            when(acquired.getId()).thenReturn(JOB);
            when(acquired.getVariables()).thenReturn(Map.of("requestedAmount", 10L));
            when(management.createExternalWorkerCompletionBuilder(eq(JOB), anyString())).thenReturn(completion);
            when(management.createExternalWorkerJobFailureBuilder(eq(JOB), anyString())).thenReturn(failure);
            when(management.createExternalWorkerJobQuery()).thenReturn(oldQuery);
            when(management.createJobQuery()).thenReturn(newQuery);
            when(oldQuery.listPage(0, 2)).thenAnswer(call -> scheduled ? oldAfter : List.of(oldExternal));
            when(newQuery.listPage(0, 2)).thenAnswer(call -> scheduled ? newAfter : List.of());
            doAnswer(call -> { if (!quietNoOp) scheduled = true; return null; }).when(completion).bpmnError(anyString());
            stubJob(oldExternal, oldFields); stubJob(executable, targetFields); newAfter = List.of(executable);
            when(runtime.createProcessInstanceQuery()).thenReturn(instances);
            when(instances.singleResult()).thenReturn(instance);
            when(instance.getProcessDefinitionId()).thenReturn(DEFINITION);
            when(instance.getBusinessKey()).thenReturn("order-one");
            when(runtime.getVariable(eq(EXECUTION), eq("amount"))).thenAnswer(call -> inputAmount);
            var effect = internal ? ValidatedProcessModel.WorkerEffect.EXTERNAL_IDEMPOTENT : ValidatedProcessModel.WorkerEffect.TRANSACTIONAL_RECEIPT;
            var outputTypes = internal ? Map.of("runId", STRING) : Map.of("approved", BOOLEAN);
            var outputMappings = internal ? Map.of("runId", Set.of("runId")) : Map.of("approved", Set.of("approved"));
            workerMetadata = new ValidatedProcessModel.WorkerTask("riskCheck", "1", effect,
                    List.of(new ValidatedProcessModel.WorkerParameter("amount", "requestedAmount", INTEGER)),
                    List.of(new ValidatedProcessModel.WorkerParameter(internal ? "runId" : "approved", internal ? "runId" : "approved", internal ? STRING : BOOLEAN)));
            when(model.type()).thenReturn(ValidatedProcessModel.Type.BPMN);
            when(model.definitionKey()).thenReturn("workerBusinessError");
            when(model.workerTasks()).thenAnswer(call -> Map.of(ELEMENT, workerMetadata));
            when(model.messageWaits()).thenReturn(internal ? Map.of("waitAgent", new ValidatedProcessModel.MessageWait(
                    "TianjiAgentFinishedV1", ELEMENT, "timer", Duration.ofMinutes(5))) : Map.of());
            when(model.decisionTasks()).thenReturn(Map.of());
            when(model.workerBusinessErrors()).thenAnswer(call -> profile ? Map.of(ELEMENT,
                    new ValidatedProcessModel.WorkerBusinessError("businessError", MODEL_CODE, "workerRejected", "rejectionReview")) : Map.of());
            var registration = new WorkerTopicRegistry.Registration(20, "https://idp.example/20", "worker-service", "riskCheck", "1",
                    Map.of("requestedAmount", INTEGER), outputTypes, Map.of("requestedAmount", Set.of("amount")), outputMappings,
                    1, Duration.ofMinutes(2), Duration.ofSeconds(1), effect, internal);
            var registry = new WorkerTopicRegistry(List.of(registration));
            try {
                schemaConstruction = mockConstruction(ProcessApiSchemaVerifier.class);
                releaseConstruction = mockConstruction(ProcessReleaseService.class, (value, ignored) ->
                        when(value.getForProcessDefinition(20, DEFINITION)).thenAnswer(call -> new ProcessReleaseService.Release(
                                RELEASE, "b".repeat(64), NOW, List.of(binding))));
                modelConstruction = mockConstruction(VerifiedProcessModels.class, (value, ignored) ->
                        when(value.runningProcess(eq(20L), any())).thenReturn(model));
                waitConstruction = mockConstruction(FlowableAgentWaitInspector.class);
                storeConstruction = mockConstruction(WorkerLeaseStore.class, (value, ignored) -> {
                    when(value.hasCapacity(eq(ACTOR), eq("riskCheck"), eq("1"), eq(1))).thenReturn(true);
                    when(value.claim(any())).thenAnswer(call -> {
                        WorkerLeaseStore.Claim claim = call.getArgument(0);
                        lease = new WorkerLeaseStore.Lease(claim.tokenSha256(), 20, ACTOR.issuerFingerprint(), ACTOR.subjectId(),
                                claim.topic(), claim.schemaVersion(), claim.jobId(), claim.occurrenceId(), claim.scopeType(), claim.scopeId(),
                                claim.releaseKey(), claim.definitionId(), claim.elementId(), claim.idempotencyKey(), claim.modelSha256(),
                                claim.inputSha256(), claim.nativeOwner(), NOW, NOW.plusSeconds(120), "LEASED", null, null, null, null, null);
                        return lease;
                    });
                    when(value.find(any(), anyString(), anyString())).thenAnswer(this::readLease);
                    when(value.lock(any(), anyString(), anyString())).thenAnswer(this::readLease);
                    when(value.isLive(any(), anyString(), anyString())).thenAnswer(call -> {
                        assertOwnedTransaction(); return leaseLive;
                    });
                    when(value.finish(any(), anyString(), anyString(), any())).thenAnswer(call -> {
                        assertOwnedTransaction();
                        if (finishFailure != null) throw finishFailure;
                        WorkerLeaseStore.Finish finish = call.getArgument(3);
                        if (!"LEASED".equals(lease.state())) {
                            if (!finish.resultSha256().equals(lease.resultSha256())
                                    || !finish.outcome().name().equals(lease.state())) throw new ProcessConflictException();
                            return lease;
                        }
                        lease = terminal(finish);
                        return lease;
                    });
                });
                jdbcConstruction = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(this::sql));
                service = new FlowableWorkerService(process, dmn, validator, registry);
                task = internal ? service.acquireAgentDispatch(new AgentDispatchBinding(ACTOR, "source-one", "riskCheck", "1",
                        "agent-one", 1, "c".repeat(64), "fixed instruction", Duration.ofSeconds(30))).task()
                        : service.acquire(ACTOR, new AcquireWorkerTaskCommand("riskCheck", "1")).task();
                assertNotNull(task, "controlled acquisition must produce the lease fixture");
                clearCalls();
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private Object readLease(InvocationOnMock call) {
            assertOwnedTransaction();
            if (!ACTOR.equals(call.getArgument(0)) || !JOB.equals(call.getArgument(1))
                    || !lease.tokenSha256().equals(call.getArgument(2))) throw new ProcessResourceNotFoundException();
            return lease;
        }

        private Object sql(InvocationOnMock call) throws Throwable {
            if (call.getArguments().length == 0 || !(call.getArgument(0) instanceof String statement)) return RETURNS_DEFAULTS.answer(call);
            if (!call.getMethod().getName().equals("query") && !call.getMethod().getName().equals("queryForObject")) return RETURNS_DEFAULTS.answer(call);
            assertOwnedTransaction();
            @SuppressWarnings("unchecked") RowMapper<Object> mapper = call.getArgument(1);
            if (statement.contains("SELECT id_ FROM") && statement.contains("act_ru_external_job"))
                return nativePresent ? List.of(mapper.mapRow(row(Map.of("id_", JOB)), 0)) : List.of();
            if (statement.contains("lock_owner_") && statement.contains("act_ru_external_job")) {
                Map<String, Object> values = new HashMap<>();
                values.put("id_", JOB); values.put("lock_owner_", nativeOwner); values.put("lock_exp_time_", Timestamp.from(NOW.plusSeconds(120)));
                values.put("process_instance_id_", nativeInstance); values.put("execution_id_", EXECUTION); values.put("proc_def_id_", nativeDefinition);
                values.put("element_id_", nativeElement); values.put("handler_cfg_", "riskCheck:1"); values.put("retries_", retries); values.put("live", nativeLive);
                return mapper.mapRow(row(values), 0);
            }
            if (statement.contains("correlation_id") && statement.contains("tianji_process_command_receipt"))
                return List.of(mapper.mapRow(row(Map.of("correlation_id", "correlation-one")), 0));
            throw new AssertionError("Unexpected Worker service SQL: " + statement);
        }

        void assertPostconditionFailure() throws Exception {
            assertThrowsExactly(IllegalStateException.class,
                    () -> service.fail(ACTOR, command(WorkerFailureKindEnum.BUSINESS_REJECTED, DIAGNOSTIC)));
            verify(store(), never()).finish(any(), anyString(), anyString(), any());
            verify(connection).rollback(); verify(connection, never()).commit();
        }

        FailWorkerTaskCommand command(WorkerFailureKindEnum kind, String code) {
            return new FailWorkerTaskCommand(task.taskId(), task.activityOccurrenceId(), task.leaseToken(), kind, code);
        }

        void seedTerminal(FailWorkerTaskCommand command) {
            String hash = top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest.hash(
                    List.of("worker-fail-v1", command.taskId(), command.activityOccurrenceId(), command.kind().name(), command.errorCode()), Map.of());
            lease = terminal(new WorkerLeaseStore.Finish(WorkerReceiptOutcomeEnum.BUSINESS_REJECTED, hash, null,
                    command.kind(), command.errorCode()));
        }

        private WorkerLeaseStore.Lease terminal(WorkerLeaseStore.Finish finish) {
            return new WorkerLeaseStore.Lease(lease.tokenSha256(), lease.tenantId(), lease.subjectIssuer(), lease.subjectId(), lease.topic(),
                    lease.schemaVersion(), lease.jobId(), lease.occurrenceId(), lease.scopeType(), lease.scopeId(), lease.releaseKey(), lease.definitionId(),
                    lease.elementId(), lease.idempotencyKey(), lease.modelSha256(), lease.inputSha256(), lease.nativeOwner(), lease.leasedAt(),
                    lease.leaseExpiresAt(), finish.outcome().name(), finish.resultSha256(), NOW, finish.businessReceiptId(), finish.failureKind(), finish.failureCode());
        }

        String tokenHash() { return lease.tokenSha256(); }
        WorkerLeaseStore store() { return storeConstruction.constructed().getFirst(); }
        JdbcTemplate jdbc() { return jdbcConstruction.constructed().getFirst(); }
        void clearCalls() {
            clearInvocations(connection, management, runtime, completion, failure, oldQuery, newQuery, store(), jdbc());
        }
        private void assertOwnedTransaction() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.hasResource(source));
        }
        @Override public void close() {
            if (jdbcConstruction != null) jdbcConstruction.close();
            if (storeConstruction != null) storeConstruction.close();
            if (waitConstruction != null) waitConstruction.close();
            if (modelConstruction != null) modelConstruction.close();
            if (releaseConstruction != null) releaseConstruction.close();
            if (schemaConstruction != null) schemaConstruction.close();
        }
    }

    private static Map<String, String> fields() {
        var fields = new LinkedHashMap<String, String>();
        fields.put("id", JOB); fields.put("tenant", "20"); fields.put("instance", INSTANCE); fields.put("definition", DEFINITION);
        fields.put("execution", EXECUTION); fields.put("element", ELEMENT); fields.put("handler", "external-worker-complete");
        fields.put("config", "error:" + MODEL_CODE); return fields;
    }

    private static void stubJob(Job job, Map<String, String> fields) {
        when(job.getId()).thenAnswer(call -> fields.get("id"));
        when(job.getTenantId()).thenAnswer(call -> fields.get("tenant"));
        when(job.getProcessInstanceId()).thenAnswer(call -> fields.get("instance"));
        when(job.getProcessDefinitionId()).thenAnswer(call -> fields.get("definition"));
        when(job.getExecutionId()).thenAnswer(call -> fields.get("execution"));
        when(job.getElementId()).thenAnswer(call -> fields.get("element"));
        when(job.getJobType()).thenReturn(job instanceof ExternalWorkerJob ? Job.JOB_TYPE_EXTERNAL_WORKER : Job.JOB_TYPE_MESSAGE);
        when(job.getJobHandlerType()).thenAnswer(call -> fields.get("handler"));
        when(job.getJobHandlerConfiguration()).thenAnswer(call -> fields.get("config"));
    }

    private static ResultSet row(Map<String, Object> values) throws SQLException {
        ResultSet result = mock(ResultSet.class);
        when(result.getString(anyString())).thenAnswer(call -> {
            Object value = values.get(call.getArgument(0)); return value == null ? null : value.toString();
        });
        when(result.getInt(anyString())).thenAnswer(call -> values.get(call.getArgument(0)) instanceof Number n ? n.intValue() : 0);
        when(result.getTimestamp(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        when(result.getBoolean(anyString())).thenAnswer(call -> Boolean.TRUE.equals(values.get(call.getArgument(0))));
        return result;
    }
}
