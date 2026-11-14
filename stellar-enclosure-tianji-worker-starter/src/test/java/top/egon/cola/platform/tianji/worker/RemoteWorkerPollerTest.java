package top.egon.cola.platform.tianji.worker;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import top.egon.cola.platform.tianji.client.ProcessClientException;
import top.egon.cola.platform.tianji.contract.orchestration.*;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerClient;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerCompletionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerFailureCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerReportDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO;
import top.egon.cola.platform.tianji.worker.WorkerExecutionStore.AutomationWorkerReadinessVO;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RemoteWorkerPollerTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String TRUSTED_SOURCE = "trusted-process";

    @Test
    void auto_configuration_is_disabled_by_default_and_requires_host_dependencies_when_enabled() {
        var runner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TianjiWorkerAutoConfiguration.class));
        runner.run(context -> { assertThat(context).hasNotFailed(); assertThat(context).doesNotHaveBean(RemoteWorkerPoller.class); });
        runner.withPropertyValues("stellar.tianji.worker.enabled=true", "stellar.tianji.worker.tenant-id=20")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabled_auto_configuration_uses_only_host_managed_dependencies() {
        var client = mock(WorkerClient.class);
        var store = store();
        var scheduler = mock(TaskScheduler.class);
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenReturn(mock(ScheduledFuture.class));
        Executor executor = Runnable::run;
        var runner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TianjiWorkerAutoConfiguration.class))
                .withPropertyValues("stellar.tianji.worker.enabled=true", "stellar.tianji.worker.tenant-id=20")
                .withBean(WorkerClient.class, () -> client)
                .withBean(WorkerExecutionStore.class, () -> store)
                .withBean(WorkerHandlerRegistration.class, () -> registration(contract()))
                .withBean("tianjiWorkerScheduler", TaskScheduler.class, () -> scheduler)
                .withBean("tianjiWorkerExecutor", Executor.class, () -> executor);
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(RemoteWorkerPoller.class);
            assertTrue(context.getBean(RemoteWorkerPoller.class).isRunning());
            verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(2)));
        });
        when(store.deadlineSafetyMargin()).thenReturn(Duration.ofSeconds(1));
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void single_configured_scheduler_acquires_v1_and_v2_for_the_same_logical_capability() {
        var legacy = mock(WorkerClient.class);
        var typed = mock(AutomationWorkerClient.class);
        var executionStore = store();
        when(executionStore.automationReadiness()).thenReturn(ready());
        var scheduler = mock(TaskScheduler.class);
        var callback = captureScheduler(scheduler);
        when(legacy.acquire(any())).thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.EMPTY, null));
        when(typed.acquire(any())).thenReturn(new AutomationWorkerAcquireDTO(AutomationWorkerAcquireDTO.StatusEnum.EMPTY, null));
        var runner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TianjiWorkerAutoConfiguration.class))
                .withPropertyValues("stellar.tianji.worker.enabled=true", "stellar.tianji.worker.tenant-id=20",
                        "stellar.tianji.worker.concurrency=2")
                .withBean(WorkerClient.class, () -> legacy)
                .withBean("automationWorkerClient", AutomationWorkerClient.class, () -> typed)
                .withBean(WorkerExecutionStore.class, () -> executionStore)
                .withBean(WorkerHandlerRegistration.class, () -> registration(contract()))
                .withBean(AutomationWorkerHandlerRegistration.class, RemoteWorkerPollerTest::automationRegistration)
                .withBean("tianjiWorkerScheduler", TaskScheduler.class, () -> scheduler)
                .withBean("tianjiWorkerExecutor", Executor.class, () -> Runnable::run);
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(RemoteWorkerPoller.class);
            verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(2)));
            callback.get().run();
            verify(executionStore).automationReadiness();
            verify(typed).acquire(new AutomationWorkerAcquireCommand("riskCheck", 1));
            verify(legacy).acquire(new AcquireWorkerTaskCommand("riskCheck", "1"));
        });
    }

    @Test
    void a_typed_client_with_the_wrong_bean_name_never_acquires_and_legacy_keeps_polling() {
        var legacy = mock(WorkerClient.class);
        var unqualified = mock(AutomationWorkerClient.class);
        var executionStore = store();
        when(executionStore.automationReadiness()).thenReturn(ready());
        when(legacy.acquire(any())).thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.EMPTY, null));
        var scheduler = mock(TaskScheduler.class);
        var callback = captureScheduler(scheduler);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TianjiWorkerAutoConfiguration.class))
                .withPropertyValues("stellar.tianji.worker.enabled=true", "stellar.tianji.worker.tenant-id=20",
                        "stellar.tianji.worker.concurrency=2")
                .withBean(WorkerClient.class, () -> legacy)
                .withBean("otherAutomationClient", AutomationWorkerClient.class, () -> unqualified)
                .withBean(WorkerExecutionStore.class, () -> executionStore)
                .withBean(WorkerHandlerRegistration.class, () -> registration(contract()))
                .withBean(AutomationWorkerHandlerRegistration.class, RemoteWorkerPollerTest::automationRegistration)
                .withBean("tianjiWorkerScheduler", TaskScheduler.class, () -> scheduler)
                .withBean("tianjiWorkerExecutor", Executor.class, () -> Runnable::run)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RemoteWorkerPoller.class);
                    callback.get().run();
                    verify(legacy, atLeastOnce()).acquire(new AcquireWorkerTaskCommand("riskCheck", "1"));
                    verifyNoInteractions(unqualified);
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void host_method_validation_keeps_the_final_poller_live_and_rejects_an_invalid_typed_handoff() {
        var registration = automationRegistration();
        var fixture = new AutomationPollingFixture(registration);
        var task = automationTask(registration, 1, null);
        var validator = mock(Validator.class);
        when(validator.validate(any(AutomationWorkerTaskDTO.class)))
                .thenReturn(Set.of((ConstraintViolation<AutomationWorkerTaskDTO>) mock(ConstraintViolation.class)));
        fixture.acquire(task);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TianjiWorkerAutoConfiguration.class))
                .withPropertyValues("stellar.tianji.worker.enabled=true", "stellar.tianji.worker.tenant-id=20")
                .withBean(WorkerClient.class, () -> mock(WorkerClient.class))
                .withBean("automationWorkerClient", AutomationWorkerClient.class, () -> fixture.typed)
                .withBean(WorkerExecutionStore.class, () -> fixture.store)
                .withBean(AutomationWorkerHandlerRegistration.class, () -> registration)
                .withBean("tianjiWorkerScheduler", TaskScheduler.class, () -> fixture.scheduler)
                .withBean("tianjiWorkerExecutor", Executor.class, () -> Runnable::run)
                .withBean(Validator.class, () -> validator)
                .withBean(MethodValidationPostProcessor.class, () -> {
                    var processor = new MethodValidationPostProcessor();
                    processor.setProxyTargetClass(true);
                    processor.setValidator(validator);
                    return processor;
                })
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RemoteWorkerPoller.class);
                    var poller = context.getBean(RemoteWorkerPoller.class);
                    assertFalse(AopUtils.isAopProxy(poller));
                    assertTrue(poller.isRunning());
                    fixture.tick();
                    verify(validator).validate(eq(task));
                    verify(fixture.store, never()).executeAutomation(any(), any());
                    verify(fixture.typed, never()).complete(any());
                    verify(fixture.typed, never()).fail(any());
                });
    }

    @Test
    void missing_failed_or_out_of_scope_readiness_keeps_legacy_polling_until_typed_is_ready() {
        var legacy = mock(WorkerClient.class);
        var fixture = new AutomationPollingFixture(automationRegistration());
        when(legacy.acquire(any())).thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.EMPTY, null));
        when(fixture.store.automationReadiness()).thenReturn(Optional.empty())
                .thenThrow(new IllegalStateException("V3 unavailable"))
                .thenReturn(Optional.of(new AutomationWorkerReadinessVO(21, TRUSTED_SOURCE)))
                .thenReturn(ready());
        when(fixture.typed.acquire(any())).thenReturn(new AutomationWorkerAcquireDTO(AutomationWorkerAcquireDTO.StatusEnum.EMPTY, null));

        var poller = fixture.poller(legacy, List.of(registration(contract())), 2);
        poller.start();
        try {
            for (int tick = 1; tick <= 3; tick++) {
                fixture.tick();
                verify(legacy, atLeast(tick)).acquire(new AcquireWorkerTaskCommand("riskCheck", "1"));
                verifyNoInteractions(fixture.typed);
            }
            fixture.tick();
            verify(fixture.typed).acquire(new AutomationWorkerAcquireCommand("riskCheck", 1));
            assertTrue(poller.isRunning());
        } finally {
            poller.stop();
        }
    }

    @Test
    void typed_tasks_with_wrong_scope_hash_descriptor_or_noncanonical_token_never_enter_the_store() {
        var valid = automationRegistration();
        var fixture = new AutomationPollingFixture(valid);
        var wrongSource = automationTask(valid, 1, null);
        wrongSource.setTaskContractSha256(wrongSource.contractSha256(20, "foreign-process"));
        var wrongTenant = automationTask(valid, 1, null);
        wrongTenant.setTaskContractSha256(wrongTenant.contractSha256(21, TRUSTED_SOURCE));
        var wrongInput = automationTask(valid, 1, null).setInputSha256("b".repeat(64));
        wrongInput.setTaskContractSha256(wrongInput.contractSha256(20, TRUSTED_SOURCE));
        var wrongVersion = automationTask(valid, 1, null).setHandlerVersion("unexpected");
        wrongVersion.setTaskContractSha256(wrongVersion.contractSha256(20, TRUSTED_SOURCE));
        var wrongFingerprint = automationTask(valid, 1, null).setEffectFingerprint("c".repeat(64));
        wrongFingerprint.setTaskContractSha256(wrongFingerprint.contractSha256(20, TRUSTED_SOURCE));
        var validToken = automationTask(valid, 0, null).getLeaseToken();
        var tokenAlias = automationTask(valid, 0, null).setLeaseToken(validToken.substring(0, 42) + "B");
        assertArrayEquals(Base64.getUrlDecoder().decode(validToken), Base64.getUrlDecoder().decode(tokenAlias.getLeaseToken()));
        assertThrows(IllegalArgumentException.class, tokenAlias::leaseTokenSha256);
        assertNotEquals(WorkerDigests.rawSha256(validToken.getBytes(StandardCharsets.UTF_8)),
                WorkerDigests.rawSha256(Base64.getUrlDecoder().decode(validToken)));

        var poller = fixture.poller(mock(WorkerClient.class), List.of(), 1);
        poller.start();
        try {
            for (var rejected : List.of(wrongSource, wrongTenant, wrongInput, wrongVersion, wrongFingerprint, tokenAlias)) {
                fixture.acquire(rejected);
                fixture.tick();
                if (!poller.isRunning()) poller.start();
            }
            verify(fixture.store, never()).executeAutomation(any(), any());
            verify(fixture.typed, never()).complete(any());
            verify(fixture.typed, never()).fail(any());
        } finally {
            poller.stop();
        }
    }

    @Test
    void typed_completion_replays_identical_transport_intent_and_new_lease_preserves_the_effect_generation() {
        var registration = automationRegistration();
        var fixture = new AutomationPollingFixture(registration);
        var output = Map.<String, Object>of("approved", true);
        var initial = automationTask(registration, 1, null);
        var result = new AutomationWorkerBusinessResultDTO("receipt-1", output);
        String resultHash = AutomationWorkerBusinessResultDTO.resultSha256(initial.getEffectFingerprint(), "receipt-1", output);
        var proof = new WorkerEffectRecoveryVO(WorkerEffectRecoveryVO.Mode.REPLAY_APPLIED, 4, NOW.minusSeconds(1),
                NOW.plusSeconds(20), "effect-authority", "d".repeat(64), "receipt-1", resultHash);
        var task = automationTask(registration, 1, proof);
        var reacquired = automationTask(registration, 2, proof);
        when(fixture.store.executeAutomation(any(), same(registration))).thenReturn(WorkerExecutionReceipt.fromAutomation(
                registration.descriptor().handlerVersion(), NOW, task.getEffectFingerprint(), result, resultHash, true));
        List<AutomationWorkerCompletionCommand> reports = new ArrayList<>();
        when(fixture.typed.complete(any())).thenAnswer(call -> {
            var command = call.<AutomationWorkerCompletionCommand>getArgument(0);
            reports.add(new AutomationWorkerCompletionCommand(command.getTaskId(), command.getActivityOccurrenceId(),
                    command.getNodeExecutionRef(), command.getLeaseToken(), command.getCommandId(), command.getEffectKey(),
                    command.getBusinessReceiptId(), Map.copyOf(command.getOutput())));
            if (reports.size() < 3) throw new ProcessClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null);
            return new AutomationWorkerReportDTO(command.getTaskId(), command.getActivityOccurrenceId(),
                    AutomationWorkerReportDTO.OutcomeEnum.COMPLETED, NOW);
        });

        var poller = fixture.poller(mock(WorkerClient.class), List.of(), 1);
        poller.start();
        try {
            fixture.acquire(task);
            fixture.tick();
            assertEquals(2, reports.size());
            assertEquals(reports.get(0), reports.get(1));
            assertEquals(task.getLeaseToken(), reports.getFirst().getLeaseToken());
            assertEquals(task.getEffectKey(), reports.getFirst().getEffectKey());
            verify(fixture.store, times(1)).executeAutomation(eq(task), same(registration));

            fixture.acquire(reacquired);
            fixture.tick();
            assertEquals(3, reports.size());
            var first = reports.getFirst();
            var recovery = reports.getLast();
            assertNotEquals(first.getCommandId(), recovery.getCommandId());
            assertEquals(reacquired.getLeaseToken(), recovery.getLeaseToken());
            assertEquals(first.getEffectKey(), recovery.getEffectKey());
            assertEquals(first.getBusinessReceiptId(), recovery.getBusinessReceiptId());
            assertEquals(first.getOutput(), recovery.getOutput());
            verify(fixture.store).executeAutomation(argThat(received -> received.getLeaseToken().equals(reacquired.getLeaseToken())
                    && received.getEffectRecovery().observedGeneration() == 4
                    && received.getEffectFingerprint().equals(task.getEffectFingerprint())),
                    same(registration));
            verify(fixture.typed, never()).fail(any());
        } finally {
            poller.stop();
        }
    }

    @Test
    void expired_recovery_proof_reports_unknown_without_entering_the_store() {
        var registration = automationRegistration();
        var fixture = new AutomationPollingFixture(registration);
        var poller = fixture.poller(mock(WorkerClient.class), List.of(), 1);
        var noProof = automationTask(registration, 1, null);
        String outputHash = AutomationWorkerBusinessResultDTO.resultSha256(noProof.getEffectFingerprint(), "receipt-1",
                Map.of("approved", true));
        when(fixture.typed.fail(any())).thenAnswer(call -> {
            var failure = call.<AutomationWorkerFailureCommand>getArgument(0);
            return new AutomationWorkerReportDTO(failure.getTaskId(), failure.getActivityOccurrenceId(),
                    AutomationWorkerReportDTO.OutcomeEnum.UNKNOWN, NOW);
        });
        poller.start();
        try {
            for (var expiry : List.of(NOW.minusNanos(1_000), NOW, NOW.plusSeconds(5))) {
                var proof = new WorkerEffectRecoveryVO(WorkerEffectRecoveryVO.Mode.REPLAY_APPLIED, 4, NOW.minusSeconds(1),
                        expiry, "effect-authority", "d".repeat(64), "receipt-1", outputHash);
                fixture.acquire(automationTask(registration, 1, proof));
                fixture.tick();
            }
            var futureProof = new WorkerEffectRecoveryVO(WorkerEffectRecoveryVO.Mode.REPLAY_APPLIED, 4, NOW.plusSeconds(3),
                    NOW.plusSeconds(20), "effect-authority", "d".repeat(64), "receipt-1", outputHash);
            fixture.acquire(automationTask(registration, 1, futureProof));
            fixture.tick();
            var sent = org.mockito.ArgumentCaptor.forClass(AutomationWorkerFailureCommand.class);
            verify(fixture.typed, times(4)).fail(sent.capture());
            sent.getAllValues().forEach(report -> {
                assertEquals(AutomationWorkerFailureCommand.KindEnum.UNKNOWN, report.getKind());
                assertNull(report.getBusinessReceiptId());
            });
            assertEquals(List.of("WORKER_RECOVERY_PROOF_EXPIRED", "WORKER_RECOVERY_PROOF_EXPIRED",
                            "WORKER_RECOVERY_PROOF_EXPIRED", "WORKER_RECOVERY_PROOF_MISMATCH"),
                    sent.getAllValues().stream().map(AutomationWorkerFailureCommand::getErrorCode).toList());
            verify(fixture.store, never()).executeAutomation(any(), any());
            verify(fixture.typed, never()).complete(any());
        } finally {
            poller.stop();
        }
    }

    @Test
    void ordinary_typed_acquisition_with_no_recovery_proof_executes_and_completes_once() {
        var registration = automationRegistration();
        var fixture = new AutomationPollingFixture(registration);
        var task = automationTask(registration, 1, null);
        var output = Map.<String, Object>of("approved", true);
        var result = new AutomationWorkerBusinessResultDTO("receipt-1", output);
        var receipt = WorkerExecutionReceipt.fromAutomation(registration.descriptor().handlerVersion(), NOW,
                task.getEffectFingerprint(), result,
                AutomationWorkerBusinessResultDTO.resultSha256(task.getEffectFingerprint(), "receipt-1", output), false);
        fixture.acquire(task);
        when(fixture.store.executeAutomation(eq(task), same(registration))).thenReturn(receipt);
        when(fixture.typed.complete(any())).thenReturn(new AutomationWorkerReportDTO(task.getTaskId(), task.getActivityOccurrenceId(),
                AutomationWorkerReportDTO.OutcomeEnum.COMPLETED, NOW));
        var poller = fixture.poller(mock(WorkerClient.class), List.of(), 1);
        poller.start();
        try {
            fixture.tick();
            verify(fixture.store).executeAutomation(eq(task), same(registration));
            verify(fixture.typed).complete(argThat(command -> "receipt-1".equals(command.getBusinessReceiptId())
                    && output.equals(command.getOutput()) && task.getLeaseToken().equals(command.getLeaseToken())));
            verify(fixture.typed, never()).fail(any());
        } finally {
            poller.stop();
        }
    }

    @Test
    void exact_receipt_is_retried_without_reexecuting_business_and_old_callbacks_cannot_revive_after_stop() {
        var task = task(20, contract());
        var registration = registration(contract());
        var client = mock(WorkerClient.class);
        var store = store();
        var scheduler = mock(TaskScheduler.class);
        var callbacks = new java.util.ArrayList<Runnable>();
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenAnswer(invocation -> {
            callbacks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        when(client.acquire(any())).thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.ACQUIRED, task));
        var result = new WorkerBusinessResultDTO("receipt", Map.of("approved", true));
        when(store.execute(eq(task), eq(registration.descriptor()), same(registration.handler())))
                .thenReturn(new WorkerExecutionReceipt("v1", NOW, result, false));
        var accepted = new WorkerReceiptDTO("job", "occurrence", WorkerReceiptOutcomeEnum.COMPLETED, NOW);
        when(client.complete(any())).thenThrow(new ProcessClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null)).thenReturn(accepted);
        var poller = poller(client, store, List.of(registration), scheduler, Runnable::run, properties(1, Duration.ZERO));
        poller.start();
        callbacks.getFirst().run();
        verify(store, times(1)).execute(eq(task), eq(registration.descriptor()), same(registration.handler()));
        var sent = org.mockito.ArgumentCaptor.forClass(CompleteWorkerTaskCommand.class);
        verify(client, times(2)).complete(sent.capture());
        assertEquals(sent.getAllValues().getFirst(), sent.getAllValues().getLast());
        poller.stop();
        callbacks.getFirst().run();
        verify(client, times(1)).acquire(any());
        poller.start();
        assertEquals(2, callbacks.size());
        poller.stop();
    }

    @Test
    void failures_are_classified_and_wrong_tenant_or_contract_never_reach_the_handler() {
        var client = mock(WorkerClient.class);
        var store = store();
        var scheduler = mock(TaskScheduler.class);
        var callback = captureScheduler(scheduler);
        var registration = registration(contract());
        var task = task(20, contract());
        when(client.acquire(any())).thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.ACQUIRED, task));
        when(store.execute(any(), any(), any())).thenThrow(new WorkerExecutionDeadlineException());
        when(client.fail(any())).thenReturn(new WorkerReceiptDTO("job", "occurrence", WorkerReceiptOutcomeEnum.RETRY_SCHEDULED, NOW));
        var poller = poller(client, store, List.of(registration), scheduler, Runnable::run, properties(1, Duration.ZERO));
        poller.start();
        callback.get().run();
        var failure = org.mockito.ArgumentCaptor.forClass(FailWorkerTaskCommand.class);
        verify(client).fail(failure.capture());
        assertEquals(WorkerFailureKindEnum.RETRYABLE, failure.getValue().kind());
        assertEquals("WORKER_DEADLINE_EXCEEDED", failure.getValue().errorCode());

        reset(client, store);
        configureStore(store);
        when(client.acquire(any())).thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.ACQUIRED, task(21, contract())));
        callback.get().run();
        verify(store, never()).execute(any(), any(), any());
        verify(client, never()).fail(any());
        assertFalse(poller.isRunning());

        reset(client, store);
        configureStore(store);
        var other = new WorkerContractDTO("riskCheck", "1", WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT,
                Map.of("amount", ProcessValueTypeEnum.INTEGER), Map.of("approved", ProcessValueTypeEnum.STRING));
        when(client.acquire(any())).thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.ACQUIRED, task(20, other)));
        when(client.fail(any())).thenReturn(new WorkerReceiptDTO("job", "occurrence", WorkerReceiptOutcomeEnum.NEEDS_MANUAL, NOW));
        poller.start();
        callback.get().run();
        verify(store, never()).execute(any(), any(), any());
        var mismatch = org.mockito.ArgumentCaptor.forClass(FailWorkerTaskCommand.class);
        verify(client).fail(mismatch.capture());
        assertEquals(WorkerFailureKindEnum.UNKNOWN, mismatch.getValue().kind());
        assertEquals("WORKER_CONTRACT_MISMATCH", mismatch.getValue().errorCode());
        assertFalse(poller.isRunning());
        poller.stop();
    }

    @Test
    void business_rejection_accepts_the_existing_terminal_receipt_without_reexecuting_business() {
        var client = mock(WorkerClient.class); var store = store(); var scheduler = mock(TaskScheduler.class);
        var callback = captureScheduler(scheduler); var registration = registration(contract());
        var task = task(20, contract());
        when(client.acquire(any())).thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.ACQUIRED, task));
        when(store.execute(eq(task), eq(registration.descriptor()), same(registration.handler())))
                .thenThrow(new WorkerHandlerFailureException(WorkerFailureKindEnum.BUSINESS_REJECTED, "CUSTOMER_INELIGIBLE"));
        var receipt = new WorkerReceiptDTO(task.taskId(), task.activityOccurrenceId(), WorkerReceiptOutcomeEnum.BUSINESS_REJECTED, NOW);
        when(client.fail(any())).thenReturn(receipt);
        var poller = poller(client, store, List.of(registration), scheduler, Runnable::run, properties(1, Duration.ZERO));
        poller.start();
        try {
            callback.get().run();
            var failure = org.mockito.ArgumentCaptor.forClass(FailWorkerTaskCommand.class);
            verify(client, times(1)).fail(failure.capture());
            assertEquals(WorkerFailureKindEnum.BUSINESS_REJECTED, failure.getValue().kind());
            assertEquals("CUSTOMER_INELIGIBLE", failure.getValue().errorCode());
            assertEquals(task.taskId(), failure.getValue().taskId());
            assertEquals(task.activityOccurrenceId(), failure.getValue().activityOccurrenceId());
            assertEquals(task.leaseToken(), failure.getValue().leaseToken());
            verify(store, times(1)).execute(eq(task), eq(registration.descriptor()), same(registration.handler()));
            verify(client, never()).complete(any());
        } finally { poller.stop(); }
    }

    @Test
    void bounded_slots_and_shutdown_prevent_a_new_generation_from_overlapping_old_work() throws Exception {
        var client = mock(WorkerClient.class);
        var store = store();
        var scheduler = mock(TaskScheduler.class);
        var callback = captureScheduler(scheduler);
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(client.acquire(any())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.EMPTY, null);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var poller = poller(client, store, List.of(registration(contract())), scheduler, executor, properties(2, Duration.ZERO));
            poller.start();
            callback.get().run();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            callback.get().run();
            assertEquals(2, calls.get());
            poller.stop();
            assertThrows(IllegalStateException.class, poller::start);
            release.countDown();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertDoesNotThrow(poller::start);
            });
            poller.stop();
        }
    }

    @Test
    void rejects_invalid_capacity_registration_and_permanent_acknowledgement_errors() {
        var client = mock(WorkerClient.class);
        var store = store();
        var scheduler = mock(TaskScheduler.class);
        var callback = captureScheduler(scheduler);
        var registration = registration(contract());
        assertThrows(IllegalArgumentException.class, () -> new WorkerPollingProperties(true, 20, 0, Duration.ofSeconds(1),
                Duration.ofSeconds(3), Duration.ofSeconds(2), Duration.ZERO, 2, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> poller(client, store, List.of(registration, registration), scheduler, Runnable::run, properties(1, Duration.ZERO)));
        when(store.tenantId()).thenReturn(21L);
        assertThrows(IllegalArgumentException.class, () -> poller(client, store, List.of(registration), scheduler, Runnable::run, properties(1, Duration.ZERO)));
        configureStore(store);
        var task = task(20, contract());
        when(client.acquire(any())).thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.ACQUIRED, task));
        when(store.execute(any(), any(), any())).thenReturn(new WorkerExecutionReceipt("v1", NOW,
                new WorkerBusinessResultDTO("receipt", Map.of("approved", true)), false));
        when(client.complete(any())).thenThrow(new ProcessClientException(409, "TIANJI_WORKER_LEASE_LOST", null, null));
        var poller = poller(client, store, List.of(registration), scheduler, Runnable::run, properties(1, Duration.ZERO));
        poller.start();
        callback.get().run();
        verify(client, times(1)).complete(any());
        poller.stop();
    }

    @Test
    void executor_that_runs_inline_then_throws_does_not_release_the_same_slot_twice() {
        var client = mock(WorkerClient.class);
        when(client.acquire(any())).thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.EMPTY, null));
        var store = store();
        var scheduler = mock(TaskScheduler.class);
        var callback = captureScheduler(scheduler);
        Executor inlineThenThrow = task -> { task.run(); throw new IllegalStateException("after execution"); };
        var poller = poller(client, store, List.of(registration(contract())), scheduler, inlineThenThrow, properties(1, Duration.ZERO));
        poller.start();
        callback.get().run();
        poller.stop();
        assertDoesNotThrow(poller::start);
        poller.stop();
    }

    @Test
    void callback_delivered_after_executor_rejection_cannot_acquire_or_release_a_slot() {
        var client = mock(WorkerClient.class);
        var acquisitions = new AtomicInteger();
        when(client.acquire(any())).thenAnswer(call -> {
            acquisitions.incrementAndGet();
            return new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.EMPTY, null);
        });
        var store = store();
        var scheduler = mock(TaskScheduler.class);
        var callback = captureScheduler(scheduler);
        var handedOff = new ArrayList<Runnable>();
        Executor delayedThenReject = task -> {
            handedOff.add(task);
            if (handedOff.size() == 1) throw new RejectedExecutionException("after handoff");
        };
        var poller = poller(client, store, List.of(registration(contract())), scheduler, delayedThenReject,
                properties(1, Duration.ZERO));
        try {
            poller.start();
            var firstGeneration = callback.get();
            firstGeneration.run();
            firstGeneration.run();
            assertEquals(2, handedOff.size());

            handedOff.getFirst().run();
            assertEquals(0, acquisitions.get());
            firstGeneration.run();
            assertEquals(2, handedOff.size());

            handedOff.get(1).run();
            handedOff.get(1).run();
            assertEquals(1, acquisitions.get());

            poller.stop();
            assertDoesNotThrow(poller::start);
            firstGeneration.run();
            assertEquals(2, handedOff.size());
        } finally {
            poller.stop();
        }
    }

    @Test
    void non_transactional_registration_requires_store_support_and_uses_its_durable_path() {
        var contract = new WorkerContractDTO("remote", "1", WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT,
                Map.of("amount", ProcessValueTypeEnum.INTEGER), Map.of("approved", ProcessValueTypeEnum.BOOLEAN));
        var descriptor = new WorkerHandlerDescriptor(contract, "v1");
        assertThrows(IllegalArgumentException.class, () -> new WorkerHandlerRegistration(descriptor,
                (context, jdbc) -> new WorkerBusinessResultDTO("receipt", Map.of("approved", true))));
        var registration = new NonTransactionalWorkerRegistration(descriptor, "orders-v1",
                (context, externalKey) -> new WorkerBusinessResultDTO("receipt", Map.of("approved", true)));
        var client = mock(WorkerClient.class); var store = store(); var scheduler = mock(TaskScheduler.class);
        var duplicateContract = new WorkerContractDTO("remote", "1", WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT,
                contract.inputs(), contract.outputs());
        assertThrows(IllegalArgumentException.class, () -> new RemoteWorkerPoller(client, store,
                List.of(registration(duplicateContract)), List.of(registration), scheduler, Runnable::run, properties(1, Duration.ZERO), CLOCK));
        doThrow(new IllegalStateException("missing schema")).when(store).requireNonTransactional();
        assertThrows(IllegalStateException.class,
                () -> new RemoteWorkerPoller(client, store, List.of(), List.of(registration), scheduler, Runnable::run, properties(1, Duration.ZERO), CLOCK));
        reset(store); configureStore(store);
        var callback = captureScheduler(scheduler); var task = task(20, contract);
        when(client.acquire(any())).thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.ACQUIRED, task));
        when(store.executeNonTransactional(eq(task), eq(descriptor), eq("orders-v1"), same(registration.handler())))
                .thenReturn(new WorkerExecutionReceipt("v1", NOW, new WorkerBusinessResultDTO("receipt", Map.of("approved", true)), false));
        when(client.complete(any())).thenReturn(new WorkerReceiptDTO("job", "occurrence", WorkerReceiptOutcomeEnum.COMPLETED, NOW));
        var poller = new RemoteWorkerPoller(client, store, List.of(), List.of(registration), scheduler, Runnable::run, properties(1, Duration.ZERO), CLOCK);
        verify(store).requireNonTransactional();
        poller.start(); callback.get().run(); poller.stop();
        verify(store).executeNonTransactional(eq(task), eq(descriptor), eq("orders-v1"), same(registration.handler()));
        verify(store, never()).execute(any(), any(), any());
        verify(client).complete(any());
    }

    @Test
    void auto_configuration_accepts_a_non_transactional_handler_without_a_transactional_one() {
        var contract = new WorkerContractDTO("remote", "1", WorkerEffectSafetyEnum.READ_ONLY,
                Map.of("amount", ProcessValueTypeEnum.INTEGER), Map.of("approved", ProcessValueTypeEnum.BOOLEAN));
        var registration = new NonTransactionalWorkerRegistration(new WorkerHandlerDescriptor(contract, "v1"), "catalog-v1",
                (context, externalKey) -> new WorkerBusinessResultDTO("receipt", Map.of("approved", true)));
        var store = store(); var scheduler = mock(TaskScheduler.class);
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenReturn(mock(ScheduledFuture.class));
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TianjiWorkerAutoConfiguration.class))
                .withPropertyValues("stellar.tianji.worker.enabled=true", "stellar.tianji.worker.tenant-id=20")
                .withBean(WorkerClient.class, () -> mock(WorkerClient.class))
                .withBean(WorkerExecutionStore.class, () -> store)
                .withBean(NonTransactionalWorkerRegistration.class, () -> registration)
                .withBean("tianjiWorkerScheduler", TaskScheduler.class, () -> scheduler)
                .withBean("tianjiWorkerExecutor", Executor.class, () -> Runnable::run)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(RemoteWorkerPoller.class));
        verify(store).requireNonTransactional();
    }

    @Test
    void auto_configuration_retains_a_host_supplied_transactional_registration_list() {
        var scheduler = mock(TaskScheduler.class);
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenReturn(mock(ScheduledFuture.class));
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TianjiWorkerAutoConfiguration.class))
                .withUserConfiguration(RegistrationListConfiguration.class)
                .withPropertyValues("stellar.tianji.worker.enabled=true", "stellar.tianji.worker.tenant-id=20")
                .withBean(WorkerClient.class, () -> mock(WorkerClient.class))
                .withBean(WorkerExecutionStore.class, RemoteWorkerPollerTest::store)
                .withBean("tianjiWorkerScheduler", TaskScheduler.class, () -> scheduler)
                .withBean("tianjiWorkerExecutor", Executor.class, () -> Runnable::run)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(RemoteWorkerPoller.class));
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class RegistrationListConfiguration {
        @org.springframework.context.annotation.Bean
        List<WorkerHandlerRegistration> workerHandlers() { return List.of(registration(contract())); }
    }

    private static AtomicReference<Runnable> captureScheduler(TaskScheduler scheduler) {
        var callback = new AtomicReference<Runnable>();
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        return callback;
    }
    private static WorkerExecutionStore store() { var store = mock(WorkerExecutionStore.class); configureStore(store); return store; }
    private static void configureStore(WorkerExecutionStore store) {
        when(store.tenantId()).thenReturn(20L);
        when(store.deadlineSafetyMargin()).thenReturn(Duration.ofSeconds(5));
    }
    private static WorkerHandlerRegistration registration(WorkerContractDTO contract) {
        return new WorkerHandlerRegistration(new WorkerHandlerDescriptor(contract, "v1"), (context, jdbc) -> new WorkerBusinessResultDTO("receipt", Map.of("approved", true)));
    }
    private static AutomationWorkerHandlerRegistration automationRegistration() {
        var input = ProcessSchemaVO.fromWire(Map.of("type", "object", "additionalProperties", false, "required", List.of("amount"),
                "properties", Map.of("amount", Map.of("type", "integer"))));
        var output = ProcessSchemaVO.fromWire(Map.of("type", "object", "additionalProperties", false, "required", List.of("approved"),
                "properties", Map.of("approved", Map.of("type", "boolean"))));
        var descriptor = new AutomationWorkerHandlerDescriptor("riskCheck", 1, WorkerDigests.schemaSha256(input),
                WorkerDigests.schemaSha256(output), WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, "typed-v1", null);
        return new AutomationWorkerHandlerRegistration(descriptor, input, output, (context, jdbc) -> null, null);
    }
    private static Optional<AutomationWorkerReadinessVO> ready() {
        return Optional.of(new AutomationWorkerReadinessVO(20, TRUSTED_SOURCE));
    }
    private static AutomationWorkerTaskDTO automationTask(AutomationWorkerHandlerRegistration registration, int leaseSerial,
                                                           WorkerEffectRecoveryVO proof) {
        var descriptor = registration.descriptor();
        var input = Map.<String, Object>of("amount", 10L);
        byte[] token = new byte[32];
        token[0] = (byte) leaseSerial;
        var task = AutomationWorkerTaskDTO.builder().taskId("task").activityOccurrenceId("occurrence").nodeExecutionRef("node-execution")
                .leaseToken(Base64.getUrlEncoder().withoutPadding().encodeToString(token))
                .capabilityKey(descriptor.capabilityKey()).capabilityVersion(descriptor.capabilityVersion())
                .input(input).inputSha256(WorkerDigests.taskInputSha256(input)).effectKey("effect-1")
                .attemptDeadlineAt(NOW.plusSeconds(60)).leaseExpiresAt(NOW.plusSeconds(30)).handlerVersion(descriptor.handlerVersion())
                .effectMode(descriptor.effectMode()).targetId(descriptor.targetId()).inputSchemaSha256(descriptor.inputSchemaSha256())
                .outputSchemaSha256(descriptor.outputSchemaSha256()).effectFingerprint(WorkerDigests.automationEffectFingerprint(descriptor, input))
                .effectRecovery(proof).build();
        return task.setTaskContractSha256(task.contractSha256(20, TRUSTED_SOURCE));
    }
    private static final class AutomationPollingFixture {
        private final AutomationWorkerClient typed = mock(AutomationWorkerClient.class);
        private final WorkerExecutionStore store = store();
        private final TaskScheduler scheduler = mock(TaskScheduler.class);
        private final AtomicReference<Runnable> callback = captureScheduler(scheduler);
        private final AutomationWorkerHandlerRegistration registration;

        private AutomationPollingFixture(AutomationWorkerHandlerRegistration registration) {
            this.registration = registration;
            when(store.automationReadiness()).thenReturn(ready());
        }

        private RemoteWorkerPoller poller(WorkerClient legacy, List<WorkerHandlerRegistration> handlers, int concurrency) {
            return new RemoteWorkerPoller(legacy, typed, store, handlers, List.of(), List.of(registration), scheduler, Runnable::run,
                    properties(concurrency, Duration.ZERO), CLOCK);
        }

        private void acquire(AutomationWorkerTaskDTO task) {
            when(typed.acquire(any())).thenReturn(new AutomationWorkerAcquireDTO(AutomationWorkerAcquireDTO.StatusEnum.ACQUIRED, task));
        }

        private void tick() {
            callback.get().run();
        }
    }
    private static WorkerContractDTO contract() {
        return new WorkerContractDTO("riskCheck", "1", WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT,
                Map.of("amount", ProcessValueTypeEnum.INTEGER), Map.of("approved", ProcessValueTypeEnum.BOOLEAN));
    }
    private static WorkerTaskDTO task(long tenant, WorkerContractDTO contract) {
        return new WorkerTaskDTO("job", "occurrence", WorkerScopeTypeEnum.BPMN, "process", null, tenant,
                contract.topic(), contract.schemaVersion(), contract.effectSafety(), contract.inputs(), contract.outputs(),
                "order", "trace", "intent", NOW, NOW.plusSeconds(30), Map.of("amount", 10),
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]));
    }
    private static WorkerPollingProperties properties(int concurrency, Duration shutdown) {
        return new WorkerPollingProperties(true, 20, concurrency, Duration.ofSeconds(1), Duration.ofSeconds(3),
                Duration.ofSeconds(2), Duration.ZERO, 2, shutdown);
    }
    private static RemoteWorkerPoller poller(WorkerClient client, WorkerExecutionStore store, List<WorkerHandlerRegistration> handlers,
                                              TaskScheduler scheduler, Executor executor, WorkerPollingProperties properties) {
        return new RemoteWorkerPoller(client, store, handlers, scheduler, executor, properties, CLOCK);
    }
}
