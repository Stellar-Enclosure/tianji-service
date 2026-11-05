package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptOutcomeEnum;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.ConnectionDropped;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.Decision;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.Direction;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.FaultWindowDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.ObservedExchangeDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.RequestDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.ResponseDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.Witnesses;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.FaultPhase.DROP_COMMITTED_WORKER_COMPLETE_200;

class ExpenseWorkerCompletionFaultTest {
    private static final String PATH = "/api/tianji/v1/worker-tasks/complete";
    private static final String LEASE = "A".repeat(43);
    private static final Instant DECIDED = Instant.parse("2026-11-06T00:00:00Z");
    private static final Duration LIMIT = Duration.ofSeconds(5);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void drops_forwarded_success_and_observes_only_the_same_command_and_committed_receipt() {
        var forwarded = new AtomicInteger();
        var witnesses = committedBusiness();
        var response = completed(DECIDED);
        try (var relay = new ExpenseAcceptanceFaultRelay(System::nanoTime, (request, timeout) -> {
            forwarded.incrementAndGet();
            return response;
        }, witnesses)) {
            relay.arm(window());
            var exact = completion(Map.of());
            var observed = new ObservedExchangeDTO(Direction.WORKER, "POST", PATH, null, null, null,
                    "occurrence", "job", null, null);
            assertEquals(Decision.DROP_RESPONSE, relay.decide(observed, System.nanoTime()));
            assertThrows(ConnectionDropped.class, () -> relay.exchange(exact));
            assertEquals(1, forwarded.get());
            assertEquals(Decision.OBSERVE_RESEND, relay.decide(observed, System.nanoTime()));
            assertThrows(IllegalStateException.class, () -> relay.exchange(completion(Map.of("different", true))));
            assertEquals(1, forwarded.get());
            assertEquals(200, relay.exchange(exact).status());
            assertEquals("occurrence", relay.awaitObserved(DROP_COMMITTED_WORKER_COMPLETE_200, LIMIT).occurrenceId());
            assertEquals(2, forwarded.get());
            verify(witnesses).businessCommitted(window(), observed, LEASE, "receipt");
        }
    }

    @Test
    void a_changed_upstream_receipt_cannot_prove_the_retry() {
        var forwarded = new AtomicInteger();
        try (var relay = new ExpenseAcceptanceFaultRelay(System::nanoTime, (request, timeout) ->
                completed(DECIDED.plusSeconds(forwarded.getAndIncrement())), committedBusiness())) {
            relay.arm(window());
            var exact = completion(Map.of());
            assertThrows(ConnectionDropped.class, () -> relay.exchange(exact));
            assertThrows(IllegalStateException.class, () -> relay.exchange(exact));
            assertThrows(IllegalStateException.class, () -> relay.awaitObserved(DROP_COMMITTED_WORKER_COMPLETE_200, LIMIT));
            assertEquals(2, forwarded.get());
        }
    }

    private static Witnesses committedBusiness() {
        var witnesses = mock(Witnesses.class);
        when(witnesses.owned(any(), any())).thenReturn(true);
        when(witnesses.businessCommitted(any(), any(), any(), any())).thenReturn(true);
        return witnesses;
    }

    private static FaultWindowDTO window() {
        return new FaultWindowDTO(DROP_COMMITTED_WORKER_COMPLETE_200, 42, "source", "instance",
                "occurrence", "job", null, null, LIMIT);
    }

    private static RequestDTO completion(Map<String, Object> output) {
        var command = new CompleteWorkerTaskCommand("job", "occurrence", LEASE, "receipt", output);
        return new RequestDTO(Direction.WORKER, "POST", PATH,
                Map.of("Authorization", List.of("Bearer fixture"), "Content-Type", List.of("application/json")),
                JSON.writeValueAsBytes(command));
    }

    private static ResponseDTO completed(Instant decidedAt) {
        var receipt = new WorkerReceiptDTO("job", "occurrence", WorkerReceiptOutcomeEnum.COMPLETED, decidedAt);
        return new ResponseDTO(200, Map.of("Content-Type", List.of("application/json")), JSON.writeValueAsBytes(receipt));
    }
}
