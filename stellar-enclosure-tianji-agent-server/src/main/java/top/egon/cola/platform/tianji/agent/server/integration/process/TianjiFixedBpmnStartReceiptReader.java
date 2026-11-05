package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartReceiptRead;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiFixedBpmnStartToolBindings.BackendVO;
import top.egon.cola.platform.tianji.client.HttpProcessClient;
import top.egon.cola.platform.tianji.client.ProcessClientException;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessStartReceiptQuery;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptClient;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

/** A fixed historical read under the original SERVICE using an explicitly supplied READ verifier. */
public final class TianjiFixedBpmnStartReceiptReader implements FixedBpmnStartReceiptRead, AutoCloseable {
    private static final Duration MAXIMUM_WINDOW = Duration.ofSeconds(10);
    private final TianjiFixedBpmnStartToolBindings bindings;
    private final TianjiProcessToolCredentialVerifier readVerifier;
    private final HttpClient http;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final ThreadPoolExecutor calls;

    public TianjiFixedBpmnStartReceiptReader(TianjiFixedBpmnStartToolBindings bindings,
            TianjiProcessToolCredentialVerifier readVerifier, HttpClient http, int concurrency) {
        this(bindings, readVerifier, http, concurrency, Clock.systemUTC(), System::nanoTime);
    }

    TianjiFixedBpmnStartReceiptReader(TianjiFixedBpmnStartToolBindings bindings,
            TianjiProcessToolCredentialVerifier readVerifier, HttpClient http, int concurrency, Clock clock, LongSupplier nanoTime) {
        this.bindings = Objects.requireNonNull(bindings);
        this.readVerifier = Objects.requireNonNull(readVerifier);
        this.http = Objects.requireNonNull(http);
        this.clock = Objects.requireNonNull(clock);
        this.nanoTime = Objects.requireNonNull(nanoTime);
        if (concurrency < 1 || concurrency > 8 || http.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("Fixed BPMN START receipt reader configuration is invalid");
        }
        calls = new ThreadPoolExecutor(0, concurrency, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                Thread.ofVirtual().inheritInheritableThreadLocals(false).name("tianji-start-receipt-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override public FixedBpmnStartReceiptRead.TargetVO bind(Request request) {
        return material(request).target();
    }

    @Override public Optional<ObservationVO> read(Request request, FixedBpmnStartReceiptRead.TargetVO expected, Instant deadlineAt) {
        requireAvailable();
        try {
            var material = material(request);
            if (!material.target().equals(expected) || deadlineAt == null) throw unavailable();
            Instant started = clock.instant();
            Instant deadline = earlier(deadlineAt, started.plus(earlier(MAXIMUM_WINDOW, material.backend().timeout())));
            var window = new ReadWindow(started, nanoTime.getAsLong(), deadline);
            window.remaining(deadline);
            var task = calls.submit(() -> getReceipt(material, request.executor(), window));
            try {
                var result = task.get(window.remaining(deadline), TimeUnit.NANOSECONDS);
                window.remaining(result.isPresent() ? result.orElseThrow().expiresAt() : deadline);
                return result;
            } finally {
                window.cancel();
                task.cancel(true);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (ExecutionException | TimeoutException | RuntimeException failed) {
            throw unavailable();
        }
    }

    private MaterialVO material(Request request) {
        try {
            var backend = bindings.resolve(request.run(), request.descriptor()).binding().backend();
            var executor = TrustedAgentActor.verified(backend.tenantId(), TrustedAgentActor.SubjectType.SERVICE,
                    backend.expectedClientId(), backend.processIssuer().toASCIIString());
            if (!executor.equals(request.executor())) throw unavailable();
            var command = TianjiFixedBpmnStartToolTransport.startCommand(backend, request.action());
            var query = new GetProcessStartReceiptQuery(command.commandId(), OrchestrationRequestDigests.start(command));
            String namespace = OrchestrationRequestDigests.hash(List.of("TIANJI_PS_START_RECEIPT_NAMESPACE_V1",
                    backend.processIssuer().toASCIIString(), backend.processBaseUri().toASCIIString(),
                    backend.processResourceUri().toASCIIString(), backend.bindingSha256(),
                    ProcessStartReceiptClient.START_RECEIPT_RESOURCE_KEY), Map.of());
            return new MaterialVO(backend, command, new FixedBpmnStartReceiptRead.TargetVO(namespace, query));
        } catch (RuntimeException failed) {
            throw unavailable();
        }
    }

    private Optional<ObservationVO> getReceipt(MaterialVO material, TrustedAgentActor executor, ReadWindow window) {
        var backend = material.backend();
        var target = new TianjiProcessToolCredentialVerifier.TargetVO(backend.tenantId(), backend.processIssuer(), backend.processResourceUri(),
                backend.registrationId(), backend.expectedClientId(), backend.expectedAppId(), backend.expectedSourceBiz(),
                backend.expectedSourceApp(), backend.expectedEnvironment(), Set.of(ProcessStartReceiptClient.READ_SCOPE));
        var credential = readVerifier.credentials(target, window.deadline, () -> window.remaining(window.deadline),
                window::publishBearer, () -> window.bearer(window.deadline));
        if (credential == null || !executor.equals(credential.actor())) throw unavailable();
        Instant expiry = earlier(window.deadline, credential.expiresAt());
        window.remaining(expiry);
        readVerifier.requireCurrent(target, credential);
        ProcessStartReceiptClient client = new HttpProcessClient(backend.processBaseUri(), http, () -> window.bearer(expiry),
                Duration.ofNanos(window.remaining(expiry)));
        try {
            var receipt = client.getStartReceipt(material.target().query());
            window.remaining(expiry);
            readVerifier.requireCurrent(target, credential);
            window.remaining(expiry);
            String reference = TianjiFixedBpmnStartToolTransport.hostReceiptReferenceForInstance(backend, executor, material.command(), receipt.instanceId());
            return Optional.of(new ObservationVO(material.target(), executor, receipt, reference, expiry));
        } catch (ProcessClientException failure) {
            if (failure.httpStatus() == 404 && "TIANJI_RESOURCE_NOT_FOUND".equals(failure.code())) return Optional.empty();
            throw unavailable();
        }
    }

    private void requireAvailable() {
        if (calls.isShutdown() || Thread.currentThread().isInterrupted() || TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) throw unavailable();
    }

    @Override public void close() {
        calls.shutdownNow();
    }

    private final class ReadWindow {
        private final Instant startedAt;
        private final long startedNanos;
        private final Instant deadline;
        private boolean cancelled;
        private String bearer;

        private ReadWindow(Instant startedAt, long startedNanos, Instant deadline) {
            this.startedAt = startedAt;
            this.startedNanos = startedNanos;
            this.deadline = deadline;
        }

        synchronized long remaining(Instant until) {
            requireAvailable();
            Instant now = clock.instant();
            if (cancelled || !until.isAfter(startedAt) || !until.isAfter(now)) throw unavailable();
            long monotonic = Duration.between(startedAt, until).toNanos() - (nanoTime.getAsLong() - startedNanos);
            if (monotonic <= 0) throw unavailable();
            return earlier(Duration.ofNanos(monotonic), Duration.between(now, until)).toNanos();
        }

        synchronized void publishBearer(String value) {
            remaining(deadline);
            if (value == null || value.isBlank()) throw unavailable();
            bearer = value;
        }

        synchronized String bearer(Instant until) {
            remaining(until);
            if (bearer == null) throw unavailable();
            return bearer;
        }

        synchronized void cancel() {
            cancelled = true;
            bearer = null;
        }
    }

    private record MaterialVO(BackendVO backend, StartProcessCommand command, FixedBpmnStartReceiptRead.TargetVO target) { }

    private static Instant earlier(Instant first, Instant second) { return first.isBefore(second) ? first : second; }
    private static Duration earlier(Duration first, Duration second) { return first.compareTo(second) < 0 ? first : second; }
    private static IllegalStateException unavailable() { return new IllegalStateException("Fixed BPMN START receipt is unavailable"); }
}
