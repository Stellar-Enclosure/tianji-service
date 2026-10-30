package top.egon.cola.platform.tianji.client;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Shared admission and deadline mechanics for service-authenticated HTTP clients. */
final class BoundedServiceCalls implements AutoCloseable {
    private final Duration timeout;
    private final Supplier<? extends RuntimeException> unavailable;
    private final ThreadPoolExecutor executor;
    private final Object lifecycle = new Object();
    private final Set<Future<?>> active = new HashSet<>();
    private boolean closed;

    BoundedServiceCalls(Duration timeout, int maxTokenCalls, String threadName, Supplier<? extends RuntimeException> unavailable) {
        if (timeout == null || timeout.compareTo(Duration.ofMillis(100)) < 0 || timeout.compareTo(Duration.ofSeconds(60)) > 0
                || maxTokenCalls < 1 || maxTokenCalls > 8) throw new IllegalArgumentException("Bounded service client settings are invalid");
        this.timeout = timeout;
        this.unavailable = Objects.requireNonNull(unavailable, "unavailable");
        this.executor = new ThreadPoolExecutor(0, maxTokenCalls, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                Thread.ofVirtual().inheritInheritableThreadLocals(false).name(threadName, 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    long begin() {
        synchronized (lifecycle) { if (closed) throw unavailable.get(); }
        return System.nanoTime();
    }

    long remaining(long started) {
        long remaining = timeout.toNanos() - (System.nanoTime() - started);
        if (remaining <= 0) throw unavailable.get();
        return remaining;
    }

    String token(Supplier<String> supplier, long started) {
        String value = invoke(supplier, started);
        if (value == null || value.length() > 16_384 || !value.matches("[A-Za-z0-9._~+/-]+=*")) throw unavailable.get();
        return value;
    }

    <T> T invoke(Supplier<T> supplier, long started) {
        Future<T> pending;
        try { pending = track(executor.submit(supplier::get)); }
        catch (RejectedExecutionException capacity) { throw unavailable.get(); }
        try { return pending.get(remaining(started), TimeUnit.NANOSECONDS); }
        catch (InterruptedException interrupted) {
            pending.cancel(true); Thread.currentThread().interrupt(); throw unavailable.get();
        } catch (TimeoutException timedOut) {
            pending.cancel(true); throw unavailable.get();
        } catch (ExecutionException | CancellationException failure) { throw unavailable.get(); }
        catch (RuntimeException failedOrExpired) { pending.cancel(true); throw failedOrExpired; }
        finally { untrack(pending); }
    }

    <T extends Future<?>> T track(T pending) {
        synchronized (lifecycle) {
            if (closed) { pending.cancel(true); throw unavailable.get(); }
            active.add(pending);
            return pending;
        }
    }

    void untrack(Future<?> pending) { synchronized (lifecycle) { active.remove(pending); } }

    @Override public void close() {
        List<Future<?>> pending;
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            pending = List.copyOf(active);
        }
        executor.shutdownNow();
        pending.forEach(call -> call.cancel(true));
    }
}
