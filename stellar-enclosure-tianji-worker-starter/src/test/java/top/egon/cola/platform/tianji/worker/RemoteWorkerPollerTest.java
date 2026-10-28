package top.egon.cola.platform.tianji.worker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.client.ProcessClientException;
import top.egon.cola.platform.tianji.contract.orchestration.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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
