package top.egon.cola.platform.tianji.process.server.bridge;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.*;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStoreTest.*;

class AgentStartWorkerTest {
    private final FlowableWorkerService workers = mock(FlowableWorkerService.class);
    private final AgentDispatchStore store = mock(AgentDispatchStore.class);
    private final LinkedAgentClient client = mock(LinkedAgentClient.class);
    private final AgentStartWorker worker = new AgentStartWorker(binding(), workers, store, client);
    private final AgentDispatchStore.Intent pending = intent("job-one", AgentDispatchStore.State.PENDING, null);
    private final AgentDispatchStore.Intent submitted = intent("job-one", AgentDispatchStore.State.SUBMITTED, "run-one");

    @Test
    void intent_commits_before_resolve_submit_and_native_acknowledgement() {
        var task = acquired();
        when(store.refresh(eq(pending.seed().key()), anyBoolean())).thenReturn(pending, pending, submitted);
        when(client.resolve(pending.query())).thenReturn(new LinkedAgentLookupResult(pending.query(), false, null));
        when(client.submit(pending.command())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new LinkedAgentStartResult(pending.query(), run());
        });
        when(store.record(pending.seed().key(), pending.query(), new LinkedAgentSubmissionDTO(false, run()))).thenReturn(submitted);
        assertEquals(AgentStartWorker.Outcome.ACKNOWLEDGED, worker.runOnce());
        var order = inOrder(workers, store, client);
        order.verify(workers).acquireAgentDispatch(binding());
        order.verify(workers).prepareAgentDispatch(ACTOR, task, binding(), store);
        order.verify(workers).isAgentDispatchActive(pending);
        order.verify(store).refresh(pending.seed().key(), false);
        order.verify(client).resolve(pending.query());
        order.verify(workers).isAgentDispatchActive(pending);
        order.verify(store).refresh(pending.seed().key(), false);
        order.verify(client).submit(pending.command());
        order.verify(store).record(pending.seed().key(), pending.query(), new LinkedAgentSubmissionDTO(false, run()));
        order.verify(workers).isAgentDispatchActive(submitted);
        order.verify(store).refresh(pending.seed().key(), false);
        order.verify(workers).completeAgentDispatch(ACTOR, task, binding(), store, "run-one");
    }

    @Test
    void unknown_submit_outcome_is_resolved_on_a_later_lease_without_a_new_deadline() {
        var task = acquired();
        when(store.refresh(eq(pending.seed().key()), anyBoolean())).thenReturn(pending);
        when(client.resolve(pending.query())).thenReturn(new LinkedAgentLookupResult(pending.query(), false, null));
        when(client.submit(pending.command())).thenThrow(new IllegalStateException("controlled lost response"));
        assertThrows(IllegalStateException.class, worker::runOnce);
        verify(workers, never()).completeAgentDispatch(any(), any(), any(), any(), any());
        when(client.resolve(pending.query())).thenReturn(new LinkedAgentLookupResult(pending.query(), true,
                new LinkedAgentSubmissionDTO(false, run())));
        when(store.record(pending.seed().key(), pending.query(), new LinkedAgentSubmissionDTO(false, run()))).thenReturn(submitted);
        when(store.refresh(eq(pending.seed().key()), anyBoolean())).thenReturn(pending, submitted);
        assertEquals(AgentStartWorker.Outcome.ACKNOWLEDGED, worker.runOnce());
        verify(client, times(1)).submit(pending.command());
        verify(workers).completeAgentDispatch(ACTOR, task, binding(), store, "run-one");
    }

    @Test
    void expiry_during_resolve_closes_the_unknown_submission_instead_of_submitting_again() {
        acquired();
        var closing = intent("job-one", AgentDispatchStore.State.CLOSING, null);
        when(store.refresh(eq(pending.seed().key()), anyBoolean())).thenReturn(pending, closing, closing);
        when(client.resolve(pending.query())).thenReturn(new LinkedAgentLookupResult(pending.query(), false, null));
        when(client.close(pending.query())).thenReturn(new LinkedAgentCloseResult(pending.query(), new LinkedAgentSubmissionDTO(true, null)));
        assertEquals(AgentStartWorker.Outcome.CLOSED, worker.runOnce());
        verify(client, never()).submit(any());
        verify(store).record(pending.seed().key(), pending.query(), new LinkedAgentSubmissionDTO(true, null));
        verify(workers).failAgentDispatch(eq(ACTOR), argThat(command -> command.kind() == WorkerFailureKindEnum.UNKNOWN));
    }

    @Test
    void a_failed_close_remains_retryable_and_does_not_block_other_occurrences() {
        var first = intent("job-one", AgentDispatchStore.State.CLOSING, null);
        var second = intent("job-two", AgentDispatchStore.State.CLOSING, null);
        when(store.due(binding(), 2, Duration.ofSeconds(10))).thenReturn(List.of(first, second));
        when(store.refresh(first.seed().key(), false)).thenReturn(first);
        when(store.refresh(second.seed().key(), false)).thenReturn(second);
        when(client.close(first.query())).thenThrow(new IllegalStateException("controlled dependency unavailable"));
        when(client.close(second.query())).thenReturn(new LinkedAgentCloseResult(second.query(), new LinkedAgentSubmissionDTO(true, null)));
        assertEquals(new AgentStartWorker.Reconciliation(2, 1, 1), worker.reconcileOnce(2, Duration.ofSeconds(10)));
        verify(store, never()).record(eq(first.seed().key()), any(), any());
        verify(store).record(second.seed().key(), second.query(), new LinkedAgentSubmissionDTO(true, null));
        verify(client, never()).submit(any());
    }

    @Test
    void acknowledgement_failure_leaves_submission_evidence_for_replay() {
        var task = acquired();
        when(store.refresh(eq(pending.seed().key()), anyBoolean())).thenReturn(pending, submitted);
        when(client.resolve(pending.query())).thenReturn(new LinkedAgentLookupResult(pending.query(), true, new LinkedAgentSubmissionDTO(false, run())));
        when(store.record(pending.seed().key(), pending.query(), new LinkedAgentSubmissionDTO(false, run()))).thenReturn(submitted);
        when(workers.completeAgentDispatch(ACTOR, task, binding(), store, "run-one")).thenThrow(new ProcessConflictException());
        assertThrows(ProcessConflictException.class, worker::runOnce);
        verify(store).record(pending.seed().key(), pending.query(), new LinkedAgentSubmissionDTO(false, run()));
        verify(client, never()).submit(any());
        verify(client, never()).close(any());
    }

    @Test
    void caller_transaction_and_mismatched_query_are_rejected_before_side_effects() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, worker::runOnce);
            assertThrows(IllegalStateException.class, () -> worker.reconcileOnce(1, Duration.ofSeconds(1)));
            verifyNoInteractions(workers, store, client);
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        acquired();
        when(store.refresh(eq(pending.seed().key()), anyBoolean())).thenReturn(pending);
        when(client.resolve(pending.query())).thenReturn(new LinkedAgentLookupResult(intent("different", AgentDispatchStore.State.PENDING, null).query(), false, null));
        assertThrows(ProcessConflictException.class, worker::runOnce);
        verify(client, never()).submit(any());
    }

    @Test
    void empty_acquisition_does_not_contact_agent() {
        when(workers.acquireAgentDispatch(any())).thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.EMPTY, null));
        assertEquals(AgentStartWorker.Outcome.EMPTY, worker.runOnce());
        verifyNoInteractions(store, client);
    }

    private WorkerTaskDTO acquired() {
        var task = new WorkerTaskDTO("job-one", "job-one", WorkerScopeTypeEnum.BPMN, "instance-one", null, 20,
                "agent-start", "1", WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT,
                Map.of("question", ProcessValueTypeEnum.STRING, "amount", ProcessValueTypeEnum.INTEGER),
                Map.of("runId", ProcessValueTypeEnum.STRING), "order-123", "correlation-one", "worker-key", NOW, NOW.plusSeconds(30),
                pending.seed().inputs(), "A".repeat(43));
        when(workers.acquireAgentDispatch(binding()))
                .thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.ACQUIRED, task));
        when(workers.prepareAgentDispatch(ACTOR, task, binding(), store)).thenReturn(pending);
        return task;
    }
    static AgentRunDTO run() {
        return new AgentRunDTO("run-one", "research", 2, null, AgentRunStatusEnum.QUEUED, null, false,
                0, 1, 0, NOW.plusSeconds(600), NOW, null);
    }
}
