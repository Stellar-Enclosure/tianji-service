package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentResultArtifactRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStoreTest.*;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentResultProducerRegistryTest.*;

class AgentResultCorrelationServiceTest {
    private final DataSource source = mock(DataSource.class);
    private final DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
    private final SpringProcessEngineConfiguration process = mock(SpringProcessEngineConfiguration.class);
    private final RuntimeService runtime = mock(RuntimeService.class);
    private final FlowableWorkerService workers = mock(FlowableWorkerService.class);
    private final AgentResultInbox inbox = mock(AgentResultInbox.class);
    private final AgentResultProducerRegistry.Registration registration = new AgentResultProducerRegistry.Registration(PRODUCER, "agent-east", "process-east", ACTOR);
    private final AgentDispatchStore.Intent dispatch = intent("job-one", AgentDispatchStore.State.ACKNOWLEDGED, "run-one");
    private final top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent event = event(dispatch, "run-one", 3, "b".repeat(64));
    private final AgentResultReceipt received = new AgentResultReceipt(event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, NOW);
    private final AgentResultReceipt applied = new AgentResultReceipt(event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.APPLIED, NOW);
    private final AgentResultInbox.Entry entry = new AgentResultInbox.Entry(PRODUCER, event, received, NOW.plusSeconds(86400), 0);
    private final ValidatedProcessModel.MessageWait modelWait = new ValidatedProcessModel.MessageWait("TianjiAgentFinishedV1", "worker", "waitTimeout",
            Duration.ofMinutes(5), "agentStatus");
    private Connection connection;

    @BeforeEach void setup() throws Exception {
        connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(process.getTransactionManager()).thenReturn(manager);
        when(process.getDatabaseSchema()).thenReturn("tianji_process");
        when(process.getRuntimeService()).thenReturn(runtime);
        var history = mock(HistoryService.class);
        when(process.getHistoryService()).thenReturn(history);
        when(process.getHistoryLevel()).thenReturn(HistoryLevel.ACTIVITY);
        when(process.isDbHistoryUsed()).thenReturn(true);
        when(inbox.find(registration, event.eventId(), false)).thenReturn(entry);
        when(inbox.find(registration, event.eventId(), true)).thenReturn(entry);
        when(inbox.lockDispatch(eq(registration), any())).thenReturn(dispatch);
        when(inbox.refreshDispatch(dispatch, true)).thenReturn(dispatch);
        when(inbox.databaseNow()).thenReturn(NOW.plusSeconds(30));
        when(inbox.finish(registration, entry, AgentResultReceiptStateEnum.APPLIED, "MESSAGE_APPLIED")).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.hasResource(source));
            return applied;
        });
        when(inbox.reschedule(eq(registration), eq(entry), any(), eq(3), anyBoolean())).thenReturn(received);
        when(workers.inspectAgentDispatch(dispatch)).thenReturn(observed(FlowableAgentWaitInspector.State.WAITING));
    }

    @Test
    void native_message_execution_audit_and_applied_commit_together_and_a_replay_does_not_send_again() throws Exception {
        try (var construction = audits()) {
            var service = service();
            assertEquals(applied, service.apply(registration, event.eventId()));
            var audit = construction.constructed().getFirst();
            var order = inOrder(runtime, audit, inbox);
            order.verify(audit).insert(PRODUCER, ProcessCommandReceiptStore.Kind.AGENT_RESULT, event.eventId(), event.fingerprint(), "release-one", "correlation-one");
            order.verify(audit).lock(PRODUCER, ProcessCommandReceiptStore.Kind.AGENT_RESULT, event.eventId());
            order.verify(runtime).messageEventReceived("TianjiAgentFinishedV1", "execution-one", Map.of("agentStatus", "COMPLETED"));
            order.verify(audit).apply(PRODUCER, ProcessCommandReceiptStore.Kind.AGENT_RESULT, event.eventId(), "execution-one", null);
            order.verify(inbox).finish(registration, entry, AgentResultReceiptStateEnum.APPLIED, "MESSAGE_APPLIED");
            var replay = new AgentResultInbox.Entry(PRODUCER, event, applied, entry.retryNotAfter(), 0);
            when(inbox.find(registration, event.eventId(), false)).thenReturn(replay);
            when(inbox.find(registration, event.eventId(), true)).thenReturn(replay);
            assertEquals(applied, service.apply(registration, event.eventId()));
            verify(runtime, times(1)).messageEventReceived(anyString(), anyString(), anyMap());
            verify(connection, times(2)).commit();
            verify(connection, never()).rollback();
        }
    }

    @Test
    void early_ack_and_advancing_or_suspended_waits_defer_without_spending_the_failure_budget() {
        try (var construction = audits()) {
            var service = service();
            for (var state : List.of(FlowableAgentWaitInspector.State.ADVANCING, FlowableAgentWaitInspector.State.SUSPENDED)) {
                when(workers.inspectAgentDispatch(dispatch)).thenReturn(observed(state));
                assertEquals(received, service.apply(registration, event.eventId()));
            }
            var early = intent("job-one", AgentDispatchStore.State.PENDING, null);
            when(inbox.lockDispatch(registration, entry)).thenReturn(early);
            when(inbox.refreshDispatch(early, true)).thenReturn(early);
            when(workers.inspectAgentDispatch(early)).thenReturn(observed(FlowableAgentWaitInspector.State.ADVANCING));
            assertEquals(received, service.apply(registration, event.eventId()));
            verify(inbox, times(3)).reschedule(registration, entry, Duration.ofSeconds(1), 3, false);
            verifyNoInteractions(runtime, construction.constructed().getFirst());
        }
    }

    @Test
    void a_won_timer_is_ignored_after_the_dispatch_enters_closing() {
        try (var construction = audits()) {
            when(workers.inspectAgentDispatch(dispatch)).thenReturn(observed(FlowableAgentWaitInspector.State.TIMER_WON));
            var closing = intent("job-one", AgentDispatchStore.State.CLOSING, "run-one");
            when(inbox.refreshDispatch(dispatch, false)).thenReturn(closing);
            var ignored = new AgentResultReceipt(event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.IGNORED_LATE, NOW);
            when(inbox.finish(registration, entry, AgentResultReceiptStateEnum.IGNORED_LATE, "WAIT_ENDED")).thenReturn(ignored);
            assertEquals(ignored, service().apply(registration, event.eventId()));
            verifyNoInteractions(runtime, construction.constructed().getFirst());
        }
    }

    @Test
    void native_failure_requests_rollback_and_persists_only_a_failed_retry_observation() throws Exception {
        try (var construction = audits()) {
            doThrow(new IllegalStateException("controlled native race")).when(runtime).messageEventReceived(anyString(), anyString(), anyMap());
            assertThrows(IllegalStateException.class, () -> service().apply(registration, event.eventId()));
            verify(connection).rollback();
            verify(connection).commit();
            verify(inbox).reschedule(registration, entry, Duration.ofSeconds(1), 3, true);
            verify(construction.constructed().getFirst(), never()).apply(any(), any(), anyString(), anyString(), any());
            verify(inbox, never()).finish(eq(registration), eq(entry), eq(AgentResultReceiptStateEnum.APPLIED), anyString());
        }
    }

    @Test
    void failure_after_native_delivery_and_audit_requests_rollback_before_retrying() throws Exception {
        try (var construction = audits()) {
            doThrow(new IllegalStateException("controlled Inbox write failure")).when(inbox)
                    .finish(registration, entry, AgentResultReceiptStateEnum.APPLIED, "MESSAGE_APPLIED");
            assertThrows(IllegalStateException.class, () -> service().apply(registration, event.eventId()));
            verify(runtime).messageEventReceived("TianjiAgentFinishedV1", "execution-one", Map.of("agentStatus", "COMPLETED"));
            verify(construction.constructed().getFirst()).apply(PRODUCER, ProcessCommandReceiptStore.Kind.AGENT_RESULT, event.eventId(), "execution-one", null);
            verify(connection).rollback();
            verify(inbox).reschedule(registration, entry, Duration.ofSeconds(1), 3, true);
        }
    }

    @Test
    void expired_receipts_and_ambient_transactions_cannot_apply_a_native_message() {
        try (var construction = audits()) {
            var service = service();
            when(inbox.databaseNow()).thenReturn(entry.retryNotAfter());
            var dead = new AgentResultReceipt(event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.DEAD, NOW);
            when(inbox.finish(registration, entry, AgentResultReceiptStateEnum.DEAD, "RETRY_DEADLINE")).thenReturn(dead);
            assertEquals(dead, service.apply(registration, event.eventId()));
            verifyNoInteractions(runtime, construction.constructed().getFirst());
            clearInvocations(inbox);
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                assertThrows(IllegalStateException.class, () -> service.apply(registration, event.eventId()));
                assertThrows(IllegalStateException.class, () -> service.reconcile(registration, 1));
                verifyNoInteractions(inbox);
            } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        }
    }

    @Test
    void a_failed_candidate_does_not_block_another_result_in_the_bounded_batch() throws Exception {
        var old = dispatch.seed();
        var secondSeed = new AgentDispatchStore.Seed(old.binding(), "instance-two", "job-two", old.releaseKey(), old.processDefinitionId(),
                old.processModelSha256(), old.elementId(), old.workerInputSha256(), old.businessKey(), old.correlationId(), old.inputs());
        var secondDispatch = new AgentDispatchStore.Intent(secondSeed, NOW, NOW.plusSeconds(60), AgentDispatchStore.State.ACKNOWLEDGED, "run-two", null);
        var secondEvent = event(secondDispatch, "run-two", 3, "b".repeat(64));
        var secondReceipt = new AgentResultReceipt(secondEvent.eventId(), secondEvent.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, NOW);
        var second = new AgentResultInbox.Entry(PRODUCER, secondEvent, secondReceipt, entry.retryNotAfter(), 0);
        var secondApplied = new AgentResultReceipt(secondEvent.eventId(), secondEvent.fingerprint(), AgentResultReceiptStateEnum.APPLIED, NOW);
        when(inbox.due(registration, 2, Duration.ofSeconds(1))).thenReturn(List.of(entry, second));
        when(inbox.find(registration, secondEvent.eventId(), false)).thenReturn(second);
        when(inbox.find(registration, secondEvent.eventId(), true)).thenReturn(second);
        when(inbox.lockDispatch(registration, second)).thenReturn(secondDispatch);
        when(inbox.refreshDispatch(secondDispatch, true)).thenReturn(secondDispatch);
        when(workers.inspectAgentDispatch(secondDispatch)).thenReturn(new FlowableAgentWaitInspector.Observation(
                FlowableAgentWaitInspector.State.WAITING, "waitResult", modelWait, "execution-two"));
        when(inbox.finish(registration, second, AgentResultReceiptStateEnum.APPLIED, "MESSAGE_APPLIED")).thenReturn(secondApplied);
        doThrow(new IllegalStateException("controlled first failure")).when(runtime)
                .messageEventReceived("TianjiAgentFinishedV1", "execution-one", Map.of("agentStatus", "COMPLETED"));
        try (var construction = audits()) {
            var service = service();
            var audit = construction.constructed().getFirst();
            when(audit.lock(PRODUCER, ProcessCommandReceiptStore.Kind.AGENT_RESULT, secondEvent.eventId())).thenReturn(
                    new ProcessCommandReceiptStore.Receipt(secondEvent.fingerprint(), old.releaseKey(), old.correlationId(), "PENDING", null, NOW, null));
            assertEquals(new AgentResultCorrelationService.Reconciliation(2, 1, 0, 0, 0, 1), service.reconcile(registration, 2));
            verify(runtime).messageEventReceived("TianjiAgentFinishedV1", "execution-two", Map.of("agentStatus", "COMPLETED"));
            verify(connection).rollback();
            verify(connection, times(2)).commit();
        }
    }

    @Test
    void typed_result_respects_the_frozen_schema_and_rejects_a_wire_shape_switch() {
        var inline = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of("answer", Map.of("type", "string")),
                "required", List.of("answer"), "additionalProperties", false));
        assertEquals(Map.of("answer", "ok"), AgentResultCorrelationService.normalizeResult(
                typedResult(Map.of("answer", "ok"), null), inline));
        assertThrows(IllegalArgumentException.class, () -> AgentResultCorrelationService.normalizeResult(
                typedResult(Map.of("answer", 1), null), inline));
        var artifact = new AgentResultArtifactRefVO("artifact-key", "a".repeat(64), "application/json", 30L);
        assertThrows(IllegalArgumentException.class, () -> AgentResultCorrelationService.normalizeResult(
                typedResult(null, artifact), inline));
        var schema = ProcessSchemaVO.fromWire(Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of(
                        "resourceKey", Map.of("type", "string", "maxLength", 128, "pattern", "^[A-Za-z][A-Za-z0-9_.:-]{0,127}$"),
                        "contentSha256", Map.of("type", "string", "maxLength", 64, "pattern", "^[0-9a-f]{64}$"),
                        "mediaType", Map.of("type", "string", "maxLength", 127, "pattern", "^[a-z0-9.+-]{1,63}/[a-z0-9.+-]{1,63}$"),
                        "sizeBytes", Map.of("type", "integer", "minimum", 0, "maximum", 999999999999999L)),
                "required", List.of("sizeBytes", "resourceKey", "mediaType", "contentSha256")));
        assertEquals(artifact.toWire(), AgentResultCorrelationService.normalizeResult(typedResult(null, artifact), schema));
        assertThrows(IllegalArgumentException.class, () -> AgentResultCorrelationService.normalizeResult(
                typedResult(artifact.toWire(), null), schema));
    }

    private AgentTerminalEventV2 typedResult(Map<String, Object> output, AgentResultArtifactRefVO artifact) {
        var status = AgentRunStatusEnum.COMPLETED;
        var effect = AgentTerminalEventV2.SideEffectOutcomeEnum.NONE;
        String hash = "a".repeat(64);
        String eventId = AgentTerminalEventV2.eventIdFor(event.producerId(), event.tenantId(), event.ownerIssuerSha256(),
                event.ownerSubjectId(), event.runId(), event.terminalRevision());
        return new AgentTerminalEventV2(2, event.producerId(), eventId, event.tenantId(), event.ownerIssuerSha256(),
                event.ownerSubjectId(), event.idempotencyKey(), event.link(), event.definitionKey(), event.definitionVersion(),
                event.runId(), event.terminalRevision(), "node-result", hash, hash, hash, status, event.finishedAt(),
                AgentTerminalEventV2.resultSha256(status, output, artifact, null, effect), output, artifact, null, effect);
    }

    private AgentResultCorrelationService service() { return new AgentResultCorrelationService(process, workers, inbox, Duration.ofSeconds(1), 3); }
    private FlowableAgentWaitInspector.Observation observed(FlowableAgentWaitInspector.State state) {
        return new FlowableAgentWaitInspector.Observation(state, "waitResult", modelWait, state == FlowableAgentWaitInspector.State.WAITING ? "execution-one" : null);
    }
    private MockedConstruction<ProcessCommandReceiptStore> audits() {
        return mockConstruction(ProcessCommandReceiptStore.class, (audit, context) -> {
            assertSame(source, ((JdbcTemplate) context.arguments().getFirst()).getDataSource());
            assertEquals("tianji_process", context.arguments().get(1));
            when(audit.insert(any(), any(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
            when(audit.lock(PRODUCER, ProcessCommandReceiptStore.Kind.AGENT_RESULT, event.eventId())).thenReturn(new ProcessCommandReceiptStore.Receipt(
                    event.fingerprint(), "release-one", "correlation-one", "PENDING", null, NOW, null));
        });
    }
}
