package top.egon.cola.platform.tianji.worker;

import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.client.ProcessClientException;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskResult;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.FailWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerClient;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerClient;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerCompletionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerFailureCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerReportDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Bounded polling over host-managed executors; the store owns intent and receipt handling. */
@Slf4j
public final class RemoteWorkerPoller implements SmartLifecycle {
    private final WorkerClient client;
    private final @Nullable AutomationWorkerClient automationClient;
    private final @Nullable Validator validator;
    private final WorkerExecutionStore store;
    private final List<PollingHandler> handlers;
    private final TaskScheduler scheduler;
    private final Executor executor;
    private final WorkerPollingProperties properties;
    private final Clock clock;
    private final Semaphore slots;
    private final AtomicInteger nextHandler = new AtomicInteger();
    private final AtomicLong lastPollingWarning = new AtomicLong(Long.MIN_VALUE);
    private final Object monitor = new Object();
    private volatile boolean running;
    private long generation;
    private int inFlight;
    private ScheduledFuture<?> scheduled;

    public RemoteWorkerPoller(WorkerClient client, WorkerExecutionStore store, List<WorkerHandlerRegistration> handlers,
                              TaskScheduler scheduler, Executor executor, WorkerPollingProperties properties, Clock clock) {
        this(client, store, handlers, List.of(), scheduler, executor, properties, clock);
    }

    public RemoteWorkerPoller(WorkerClient client, WorkerExecutionStore store, List<WorkerHandlerRegistration> handlers,
                              List<NonTransactionalWorkerRegistration> nonTransactionalHandlers,
                              TaskScheduler scheduler, Executor executor, WorkerPollingProperties properties, Clock clock) {
        this(client, null, store, handlers, nonTransactionalHandlers, List.of(), scheduler, executor, properties, clock);
    }

    public RemoteWorkerPoller(WorkerClient client, @Nullable AutomationWorkerClient automationClient, WorkerExecutionStore store,
                              List<WorkerHandlerRegistration> handlers, List<NonTransactionalWorkerRegistration> nonTransactionalHandlers,
                              List<AutomationWorkerHandlerRegistration> automationHandlers,
                              TaskScheduler scheduler, Executor executor, WorkerPollingProperties properties, Clock clock) {
        this(client, automationClient, store, handlers, nonTransactionalHandlers, automationHandlers, scheduler, executor,
                properties, clock, null);
    }

    RemoteWorkerPoller(WorkerClient client, @Nullable AutomationWorkerClient automationClient, WorkerExecutionStore store,
                       List<WorkerHandlerRegistration> handlers, List<NonTransactionalWorkerRegistration> nonTransactionalHandlers,
                       List<AutomationWorkerHandlerRegistration> automationHandlers, TaskScheduler scheduler, Executor executor,
                       WorkerPollingProperties properties, Clock clock, @Nullable Validator validator) {
        this.client = Objects.requireNonNull(client, "client");
        this.automationClient = automationClient;
        this.validator = validator;
        this.store = Objects.requireNonNull(store, "store");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (!properties.enabled() || handlers == null || nonTransactionalHandlers == null || automationHandlers == null
                || (long) handlers.size() + nonTransactionalHandlers.size() + automationHandlers.size() < 1
                || (long) handlers.size() + nonTransactionalHandlers.size() + automationHandlers.size() > 1_000
                || store.tenantId() != properties.tenantId() || store.deadlineSafetyMargin() == null
                || store.deadlineSafetyMargin().compareTo(properties.acknowledgementReserve().plus(properties.maxClockSkew())) < 0) throw invalid();
        Set<String> keys = new HashSet<>();
        List<PollingHandler> registered = new ArrayList<>();
        for (WorkerHandlerRegistration registration : handlers) {
            if (registration == null || !keys.add(registration.descriptor().contract().topic() + ":"
                    + registration.descriptor().contract().schemaVersion())) throw invalid();
            var handler = new LegacyHandlerVO(registration.descriptor(), registration.handler(), null, null);
            registered.add(current -> processLegacy(handler, current));
        }
        for (NonTransactionalWorkerRegistration registration : nonTransactionalHandlers) {
            if (registration == null || !keys.add(registration.descriptor().contract().topic() + ":"
                    + registration.descriptor().contract().schemaVersion())) throw invalid();
            var handler = new LegacyHandlerVO(registration.descriptor(), null, registration.targetId(), registration.handler());
            registered.add(current -> processLegacy(handler, current));
        }
        if (!nonTransactionalHandlers.isEmpty()) {
            store.requireNonTransactional();
        }
        Set<String> automationKeys = new HashSet<>();
        for (var registration : automationHandlers) {
            if (registration == null || !validBean(registration) || !automationKeys.add(registration.descriptor().capabilityKey() + ":"
                    + registration.descriptor().capabilityVersion())) throw invalid();
            if (automationClient != null) registered.add(current -> processAutomation(registration, current));
        }
        this.handlers = List.copyOf(registered);
        this.slots = new Semaphore(properties.concurrency());
    }

    @Override
    public void start() {
        synchronized (monitor) {
            if (running) return;
            if (inFlight != 0) throw new IllegalStateException("Worker cannot restart while previous tasks are still running");
            long current = ++generation;
            running = true;
            try {
                scheduled = scheduler.scheduleWithFixedDelay(() -> tick(current), properties.pollInterval());
                if (scheduled == null) throw new IllegalStateException("Worker scheduler did not accept polling");
            } catch (RuntimeException failure) {
                running = false;
                generation++;
                throw failure;
            }
        }
    }

    @Override
    public void stop() {
        boolean interrupted = false;
        synchronized (monitor) {
            if (!running && inFlight == 0) return;
            running = false;
            generation++;
            if (scheduled != null) scheduled.cancel(false);
            scheduled = null;
            long deadline = System.nanoTime() + properties.shutdownTimeout().toNanos();
            while (inFlight > 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                try { TimeUnit.NANOSECONDS.timedWait(monitor, remaining); }
                catch (InterruptedException stopInterrupted) { interrupted = true; break; }
            }
            if (inFlight > 0) log.warn("Worker stopped waiting with {} task(s) still running", inFlight);
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    @Override public boolean isRunning() { return running; }

    private void tick(long current) {
        if (handlers.isEmpty()) return;
        for (int attempt = 0; attempt < properties.concurrency(); attempt++) {
            synchronized (monitor) {
                if (!running || current != generation || !slots.tryAcquire()) return;
                inFlight++;
            }
            int index = Math.floorMod(nextHandler.getAndIncrement(), handlers.size());
            var handler = handlers.get(index);
            var completionClaimed = new AtomicBoolean();
            try {
                executor.execute(() -> {
                    if (!completionClaimed.compareAndSet(false, true)) return;
                    try { if (active(current)) handler.process(current); }
                    finally { finished(); }
                });
            } catch (RuntimeException rejected) {
                if (completionClaimed.compareAndSet(false, true)) finished();
                warnPolling("executor", rejected);
            }
        }
    }

    private boolean active(long current) {
        synchronized (monitor) { return running && current == generation; }
    }

    private void finished() {
        synchronized (monitor) {
            inFlight--;
            slots.release();
            monitor.notifyAll();
        }
    }

    private void processLegacy(LegacyHandlerVO registration, long current) {
        var contract = registration.descriptor().contract();
        AcquireWorkerTaskResult acquired;
        try { acquired = client.acquire(new AcquireWorkerTaskCommand(contract.topic(), contract.schemaVersion())); }
        catch (RuntimeException failure) {
            warnPolling("acquire", failure);
            return;
        }
        if (acquired == null) {
            log.warn("Worker acquisition returned no result");
            return;
        }
        if (acquired.task() == null || !active(current)) return;
        WorkerTaskDTO task = acquired.task();
        if (task.tenantId() != properties.tenantId()) {
            log.error("Worker received a task for an unexpected tenant");
            halt();
            return;
        }
        if (!task.contract().equals(contract)) {
            log.error("Worker polling stopped because the task contract does not match its handler");
            reportFailure(task, WorkerFailureKindEnum.UNKNOWN, "WORKER_CONTRACT_MISMATCH");
            halt();
            return;
        }
        WorkerExecutionReceipt receipt;
        try {
            receipt = registration.nonTransactionalHandler() == null
                    ? store.execute(task, registration.descriptor(), registration.handler())
                    : store.executeNonTransactional(task, registration.descriptor(), registration.targetId(), registration.nonTransactionalHandler());
        }
        catch (WorkerHandlerFailureException reported) {
            reportFailure(task, reported.kind(), reported.code());
            return;
        } catch (WorkerExecutionDeadlineException deadline) {
            reportFailure(task, WorkerFailureKindEnum.RETRYABLE, "WORKER_DEADLINE_EXCEEDED");
            return;
        } catch (WorkerExecutionConflictException conflict) {
            reportFailure(task, WorkerFailureKindEnum.UNKNOWN, "WORKER_RECEIPT_CONFLICT");
            return;
        } catch (RuntimeException failure) {
            log.warn("Worker business execution failed: type={}", failure.getClass().getSimpleName());
            reportFailure(task, WorkerFailureKindEnum.UNKNOWN, "WORKER_EXECUTION_FAILED");
            return;
        }
        if (receipt == null || receipt.result() == null) {
            reportFailure(task, WorkerFailureKindEnum.UNKNOWN, "WORKER_RECEIPT_INVALID");
            return;
        }
        try { contract.validateOutput(receipt.result().output()); }
        catch (RuntimeException invalidResult) {
            reportFailure(task, WorkerFailureKindEnum.UNKNOWN, "WORKER_RECEIPT_INVALID");
            return;
        }
        var command = new CompleteWorkerTaskCommand(task.taskId(), task.activityOccurrenceId(), task.leaseToken(),
                receipt.result().businessReceiptId(), receipt.result().output());
        acknowledge(task.leaseExpiresAt(), () -> validReceipt(task, client.complete(command), WorkerFailureKindEnum.RETRYABLE, true));
    }

    private void reportFailure(WorkerTaskDTO task, WorkerFailureKindEnum kind, String code) {
        var command = new FailWorkerTaskCommand(task.taskId(), task.activityOccurrenceId(), task.leaseToken(), kind, code);
        acknowledge(task.leaseExpiresAt(), () -> validReceipt(task, client.fail(command), kind, false));
    }

    private void acknowledge(Instant leaseExpiresAt, BooleanSupplier send) {
        Instant deadline = leaseExpiresAt.minus(properties.maxClockSkew());
        for (int attempt = 0; attempt < properties.acknowledgementAttempts(); attempt++) {
            if (!clock.instant().isBefore(deadline)) break;
            try {
                if (!send.getAsBoolean()) throw new IllegalStateException("Worker receipt does not match the task");
                return;
            } catch (RuntimeException failure) {
                if (permanent(failure) || attempt + 1 == properties.acknowledgementAttempts()) break;
                Duration delay = properties.acknowledgementRetryDelay();
                if (!clock.instant().plus(delay).isBefore(deadline)) break;
                try { Thread.sleep(delay.toMillis()); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
        log.warn("Worker acknowledgement was not confirmed before its retry or lease limit");
    }

    private void processAutomation(AutomationWorkerHandlerRegistration registration, long current) {
        AutomationWorkerTaskDTO task;
        try {
            var scope = store.automationReadiness().orElse(null);
            if (scope == null || scope.tenantId() != properties.tenantId() || !active(current)) return;
            var descriptor = registration.descriptor();
            var command = new AutomationWorkerAcquireCommand(descriptor.capabilityKey(), descriptor.capabilityVersion());
            if (!validBean(command)) throw invalid();
            AutomationWorkerAcquireDTO acquired = automationClient.acquire(command);
            if (acquired == null || !acquired.isAcquisitionConsistent() || !validBean(acquired)) throw invalid();
            if (acquired.getTask() == null || !active(current)) return;
            task = WorkerExecutionStore.verifiedAutomationTask(acquired.getTask(), registration, scope);
            if (!validBean(task)) throw invalid();
        } catch (RuntimeException failure) {
            warnPolling("automation acquisition", failure);
            return;
        }

        var report = new AutomationReportIdentityVO(task.getTaskId(), task.getActivityOccurrenceId(), task.getNodeExecutionRef(),
                task.getLeaseToken(), task.leaseTokenSha256(), task.getLeaseExpiresAt(), task.getEffectKey());
        Instant now = clock.instant();
        var proof = task.getEffectRecovery();
        Instant deadline = task.getAttemptDeadlineAt().isBefore(task.getLeaseExpiresAt()) ? task.getAttemptDeadlineAt() : task.getLeaseExpiresAt();
        Duration safetyMargin = store.deadlineSafetyMargin();
        if (proof != null) {
            Instant latestLocalNow = now.plus(properties.maxClockSkew());
            if (proof.observedAt().isAfter(latestLocalNow)) {
                reportAutomationFailure(report, WorkerFailureKindEnum.UNKNOWN, "WORKER_RECOVERY_PROOF_MISMATCH");
                return;
            }
            if (!latestLocalNow.isBefore(proof.validUntil()) || proof.validUntil().isBefore(deadline)
                    && !now.isBefore(proof.validUntil().minus(safetyMargin))) {
                reportAutomationFailure(report, WorkerFailureKindEnum.UNKNOWN, "WORKER_RECOVERY_PROOF_EXPIRED");
                return;
            }
        }
        if (!now.isBefore(deadline.minus(safetyMargin))) {
            reportAutomationFailure(report, WorkerFailureKindEnum.RETRYABLE, "WORKER_DEADLINE_EXCEEDED");
            return;
        }
        if (!active(current)) return;
        String fingerprint = task.getEffectFingerprint();
        WorkerExecutionReceipt receipt;
        try {
            receipt = store.executeAutomation(task, registration);
        } catch (WorkerHandlerFailureException reported) {
            reportAutomationFailure(report, reported.kind(), reported.code());
            return;
        } catch (WorkerExecutionDeadlineException deadlineExceeded) {
            reportAutomationFailure(report, WorkerFailureKindEnum.RETRYABLE, "WORKER_DEADLINE_EXCEEDED");
            return;
        } catch (WorkerExecutionConflictException conflict) {
            reportAutomationFailure(report, WorkerFailureKindEnum.UNKNOWN, "WORKER_RECEIPT_CONFLICT");
            return;
        } catch (RuntimeException failure) {
            warnPolling("automation execution", failure);
            reportAutomationFailure(report, WorkerFailureKindEnum.UNKNOWN, "WORKER_EXECUTION_FAILED");
            return;
        }

        Map<String, Object> output;
        try {
            if (receipt == null || receipt.result() != null
                    || !registration.descriptor().handlerVersion().equals(receipt.handlerVersion())
                    || !fingerprint.equals(receipt.effectFingerprint())) throw invalid();
            output = registration.normalizeOutput(receipt.typedOutput());
            if (!Arrays.equals(ProcessTypedJson.canonicalBytes(output), ProcessTypedJson.canonicalBytes(receipt.typedOutput()))
                    || proof != null && proof.mode() == WorkerEffectRecoveryVO.Mode.REPLAY_APPLIED
                    && (!proof.businessReceiptId().equals(receipt.businessReceiptId()) || !proof.resultSha256().equals(receipt.resultSha256()))) {
                throw invalid();
            }
        } catch (RuntimeException invalidResult) {
            reportAutomationFailure(report, WorkerFailureKindEnum.UNKNOWN, "WORKER_RECEIPT_INVALID");
            return;
        }
        String commandId = report.commandId("COMPLETE");
        String businessReceiptId = receipt.businessReceiptId();
        acknowledge(report.leaseExpiresAt(), () -> {
            var command = new AutomationWorkerCompletionCommand(report.taskId(), report.occurrenceId(),
                    report.nodeExecutionRef(), report.leaseToken(), commandId, report.effectKey(), businessReceiptId, output);
            if (!validBean(command)) return false;
            var received = automationClient.complete(command);
            return report.matches(received, Set.of(AutomationWorkerReportDTO.OutcomeEnum.COMPLETED)) && validBean(received);
        });
    }

    private void reportAutomationFailure(AutomationReportIdentityVO report, WorkerFailureKindEnum failure, String code) {
        var kind = switch (failure) {
            case RETRYABLE -> AutomationWorkerFailureCommand.KindEnum.RETRYABLE;
            case BUSINESS_REJECTED -> AutomationWorkerFailureCommand.KindEnum.PERMANENT;
            case UNKNOWN -> AutomationWorkerFailureCommand.KindEnum.UNKNOWN;
        };
        var outcomes = switch (kind) {
            case RETRYABLE -> Set.of(AutomationWorkerReportDTO.OutcomeEnum.RETRY_SCHEDULED, AutomationWorkerReportDTO.OutcomeEnum.NEEDS_MANUAL);
            case PERMANENT -> Set.of(AutomationWorkerReportDTO.OutcomeEnum.PERMANENT);
            case UNKNOWN -> Set.of(AutomationWorkerReportDTO.OutcomeEnum.UNKNOWN);
        };
        String commandId = report.commandId("FAIL");
        acknowledge(report.leaseExpiresAt(), () -> {
            var command = new AutomationWorkerFailureCommand(report.taskId(), report.occurrenceId(),
                    report.nodeExecutionRef(), report.leaseToken(), commandId, kind, code, null);
            if (!validBean(command)) return false;
            var received = automationClient.fail(command);
            return report.matches(received, outcomes) && validBean(received);
        });
    }

    private boolean validBean(Object value) {
        return validator == null || validator.validate(value).isEmpty();
    }

    private static boolean validReceipt(WorkerTaskDTO task, WorkerReceiptDTO receipt, WorkerFailureKindEnum kind, boolean completion) {
        if (receipt == null || !task.taskId().equals(receipt.taskId()) || !task.activityOccurrenceId().equals(receipt.activityOccurrenceId())) return false;
        if (completion) return receipt.outcome() == WorkerReceiptOutcomeEnum.COMPLETED;
        if (kind == WorkerFailureKindEnum.UNKNOWN) return receipt.outcome() == WorkerReceiptOutcomeEnum.NEEDS_MANUAL;
        if (kind == WorkerFailureKindEnum.BUSINESS_REJECTED) return receipt.outcome() == WorkerReceiptOutcomeEnum.BUSINESS_REJECTED
                || receipt.outcome() == WorkerReceiptOutcomeEnum.NEEDS_MANUAL;
        return receipt.outcome() == WorkerReceiptOutcomeEnum.RETRY_SCHEDULED || receipt.outcome() == WorkerReceiptOutcomeEnum.NEEDS_MANUAL;
    }

    private static boolean permanent(RuntimeException failure) {
        if (!(failure instanceof ProcessClientException clientFailure)) return false;
        return clientFailure.httpStatus() == 400 || clientFailure.httpStatus() == 401 || clientFailure.httpStatus() == 403
                || clientFailure.httpStatus() == 404 || clientFailure.httpStatus() == 409;
    }

    private void halt() {
        synchronized (monitor) {
            running = false;
            generation++;
            if (scheduled != null) scheduled.cancel(false);
            scheduled = null;
        }
    }

    private void warnPolling(String operation, RuntimeException failure) {
        long now = System.nanoTime();
        long previous = lastPollingWarning.get();
        if ((previous == Long.MIN_VALUE || now - previous >= TimeUnit.SECONDS.toNanos(30))
                && lastPollingWarning.compareAndSet(previous, now)) {
            log.warn("Worker polling failed at {}: type={}", operation, failure.getClass().getSimpleName());
        }
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Worker poller configuration is invalid"); }

    @FunctionalInterface
    private interface PollingHandler {
        void process(long current);
    }

    private record LegacyHandlerVO(WorkerHandlerDescriptor descriptor, TransactionalWorkerHandler handler, String targetId,
                                   NonTransactionalWorkerHandler nonTransactionalHandler) { }

    private record AutomationReportIdentityVO(String taskId, String occurrenceId, String nodeExecutionRef, String leaseToken,
                                               String leaseTokenSha256, Instant leaseExpiresAt, String effectKey) {
        String commandId(String operation) {
            return "wr-" + ProcessTypedJson.sha256("AUTOMATION_WORKER_TRANSPORT_REPORT_V2",
                    ProcessTypedJson.canonicalBytes(List.of(operation, taskId, occurrenceId, nodeExecutionRef, leaseTokenSha256)));
        }

        boolean matches(AutomationWorkerReportDTO receipt, Set<AutomationWorkerReportDTO.OutcomeEnum> outcomes) {
            return receipt != null && taskId.equals(receipt.getTaskId()) && occurrenceId.equals(receipt.getActivityOccurrenceId())
                    && receipt.isDecidedAtValid() && receipt.getOutcome() != null && outcomes.contains(receipt.getOutcome());
        }

        @Override
        public String toString() { return "AutomationReportIdentityVO[redacted]"; }
    }
}
