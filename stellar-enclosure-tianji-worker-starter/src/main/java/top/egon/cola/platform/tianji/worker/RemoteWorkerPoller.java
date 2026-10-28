package top.egon.cola.platform.tianji.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Bounded polling over host-managed executors; the store owns intent and receipt handling. */
public final class RemoteWorkerPoller implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(RemoteWorkerPoller.class);
    private final WorkerClient client;
    private final WorkerExecutionStore store;
    private final List<Handler> handlers;
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
        this.client = Objects.requireNonNull(client, "client");
        this.store = Objects.requireNonNull(store, "store");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (!properties.enabled() || handlers == null || nonTransactionalHandlers == null
                || (long) handlers.size() + nonTransactionalHandlers.size() < 1
                || (long) handlers.size() + nonTransactionalHandlers.size() > 1_000
                || store.tenantId() != properties.tenantId() || store.deadlineSafetyMargin() == null
                || store.deadlineSafetyMargin().compareTo(properties.acknowledgementReserve().plus(properties.maxClockSkew())) < 0) throw invalid();
        Set<String> keys = new HashSet<>();
        List<Handler> registered = new ArrayList<>();
        for (WorkerHandlerRegistration registration : handlers) {
            if (registration == null || !keys.add(registration.descriptor().contract().topic() + ":"
                    + registration.descriptor().contract().schemaVersion())) throw invalid();
            registered.add(new Handler(registration.descriptor(), registration.handler(), null, null));
        }
        for (NonTransactionalWorkerRegistration registration : nonTransactionalHandlers) {
            if (registration == null || !keys.add(registration.descriptor().contract().topic() + ":"
                    + registration.descriptor().contract().schemaVersion())) throw invalid();
            registered.add(new Handler(registration.descriptor(), null, registration.targetId(), registration.handler()));
        }
        if (!nonTransactionalHandlers.isEmpty()) {
            store.requireNonTransactional();
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
        for (int attempt = 0; attempt < properties.concurrency(); attempt++) {
            synchronized (monitor) {
                if (!running || current != generation || !slots.tryAcquire()) return;
                inFlight++;
            }
            int index = Math.floorMod(nextHandler.getAndIncrement(), handlers.size());
            Handler handler = handlers.get(index);
            var started = new AtomicBoolean();
            try {
                executor.execute(() -> {
                    started.set(true);
                    try { if (active(current)) process(handler, current); }
                    finally { finished(); }
                });
            } catch (RuntimeException rejected) {
                if (!started.get()) finished();
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

    private void process(Handler registration, long current) {
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
        acknowledge(task, () -> client.complete(command), WorkerFailureKindEnum.RETRYABLE, true);
    }

    private void reportFailure(WorkerTaskDTO task, WorkerFailureKindEnum kind, String code) {
        var command = new FailWorkerTaskCommand(task.taskId(), task.activityOccurrenceId(), task.leaseToken(), kind, code);
        acknowledge(task, () -> client.fail(command), kind, false);
    }

    private void acknowledge(WorkerTaskDTO task, Supplier<WorkerReceiptDTO> send, WorkerFailureKindEnum kind, boolean completion) {
        Instant deadline = task.leaseExpiresAt().minus(properties.maxClockSkew());
        for (int attempt = 0; attempt < properties.acknowledgementAttempts(); attempt++) {
            if (!clock.instant().isBefore(deadline)) break;
            try {
                WorkerReceiptDTO receipt = send.get();
                if (!validReceipt(task, receipt, kind, completion)) throw new IllegalStateException("Worker receipt does not match the task");
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

    private record Handler(WorkerHandlerDescriptor descriptor, TransactionalWorkerHandler handler, String targetId,
                           NonTransactionalWorkerHandler nonTransactionalHandler) { }
}
