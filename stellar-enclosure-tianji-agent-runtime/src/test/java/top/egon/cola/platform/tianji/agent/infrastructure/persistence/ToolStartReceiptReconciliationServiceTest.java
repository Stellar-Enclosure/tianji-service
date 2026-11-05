package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartReceiptRead;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartReceiptRead.ObservationVO;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartReceiptRead.TargetVO;
import top.egon.cola.platform.tianji.agent.application.tool.HistoricalToolReceiptAuthorization;
import top.egon.cola.platform.tianji.agent.application.tool.HistoricalToolReceiptAuthorization.Permit;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolActionIntentSql.DispatchVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolActionIntentSql.HistoricalStartReceiptVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolActionIntentSql.Row;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolStartReceiptReconciliationSql.StoredReceiptVO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessStartReceiptQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptDTO;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ToolStartReceiptReconciliationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private static final String HASH = "a".repeat(64);
    private static final String RUN = "dispatch-run";
    private static final String ORIGIN = "origin-run";
    private static final String REF = "tianji:ps-start:v1:" + "b".repeat(64);
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER,
            "alice", "https://idp.example/20");
    private static final TrustedAgentActor EXECUTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE,
            "process-executor", "https://service.example/20");
    private static final ToolDescriptor TOOL = new ToolDescriptor("process.start", 1, "start_process", "Pinned write",
            new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
            new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
            new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start.invoice", "tianji.api.process.instance.start", HASH, null));
    private static final ToolActionRequest ACTION = new ToolActionRequest(OWNER, TOOL, "invoice", "v1", Map.of("amount", 20L));
    private static final UUID INTENT = UUID.fromString(ACTION.intentId());
    private static final TargetVO TARGET = new TargetVO("c".repeat(64), new GetProcessStartReceiptQuery(ACTION.intentId(), "d".repeat(64)));
    private static final ProcessStartReceiptDTO APPLIED = new ProcessStartReceiptDTO("process-1",
            ProcessStartReceiptDTO.StatusEnum.APPLIED, NOW.minusSeconds(60));
    private static final ObservationVO OBSERVATION = new ObservationVO(TARGET, EXECUTOR, APPLIED, REF, NOW.plusSeconds(3));

    @Test
    void appends_only_the_exact_positive_observation_after_two_current_decisions_and_one_get() throws Exception {
        var f = new Fixture();
        var ack = f.observe();
        assertEquals("process-1", ack.instanceId());
        assertEquals(APPLIED.recordedAt(), ack.recordedAt());
        assertEquals(NOW, ack.observedAt());
        var order = inOrder(f.intents, f.process, f.authority, f.receipts);
        order.verify(f.intents).historicalStartReceipt(eq(OWNER), eq(RUN), eq(INTENT), any());
        order.verify(f.process).bind(any());
        order.verify(f.authority).authorize(any());
        order.verify(f.process).read(any(), eq(TARGET), eq(NOW.plusSeconds(8)));
        order.verify(f.authority).authorize(any());
        order.verify(f.receipts).read(eq(OWNER), eq(RUN), eq(INTENT), eq(1L), eq(NOW.plusSeconds(3)));
        order.verify(f.receipts).append(eq(f.historical.get()), anyString(), anyString(), eq(NOW.plusSeconds(3)));
        verifyNoMoreInteractions(f.intents, f.process, f.authority, f.receipts);
        assertTrue(f.written.get().encodedEvidence().contains("APPLIED_OBSERVED"));
        assertFalse(f.written.get().encodedEvidence().contains(ACTION.businessKey()));
        verify(f.connection, times(3)).commit();
    }

    @Test
    void a_revoked_historical_decision_after_get_cannot_append() throws Exception {
        var f = new Fixture();
        doAnswer(call -> allowed(call.getArgument(0))).doReturn(Optional.empty()).when(f.authority).authorize(any());
        assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, f);
        verify(f.process).read(any(), eq(TARGET), any());
        verifyNoInteractions(f.receipts);
    }

    @Test
    void rejects_unproved_or_foreign_remote_effects_before_writing() throws Exception {
        var missing = new Fixture();
        when(missing.process.read(any(), eq(TARGET), any())).thenReturn(Optional.empty());
        assertCode(AgentErrorCodeEnum.TIANJI_SIDE_EFFECT_UNKNOWN, missing);
        verifyNoInteractions(missing.receipts);
        var foreign = new Fixture();
        var sameName = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, EXECUTOR.subjectId(), "https://other.example/20");
        when(foreign.process.read(any(), eq(TARGET), any())).thenReturn(
                Optional.of(new ObservationVO(TARGET, sameName, APPLIED, REF, NOW.plusSeconds(3))));
        assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, foreign);
        verifyNoInteractions(foreign.receipts);
        var refused = new Fixture();
        doReturn(Optional.empty()).when(refused.authority).authorize(any());
        assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, refused);
        verify(refused.process, never()).read(any(), any(), any());
        verifyNoInteractions(refused.receipts);
    }

    @Test
    void exact_replay_returns_original_ack_and_changed_positive_evidence_conflicts() throws Exception {
        var f = new Fixture();
        var original = f.observe();
        when(f.receipts.read(eq(OWNER), eq(RUN), eq(INTENT), eq(1L), any())).thenReturn(Optional.of(f.written.get()));
        clearInvocations(f.receipts, f.authority, f.process);
        assertEquals(original, f.observe());
        verify(f.authority, times(2)).authorize(any());
        verify(f.process).read(any(), eq(TARGET), any());
        var other = new ProcessStartReceiptDTO("process-2", ProcessStartReceiptDTO.StatusEnum.APPLIED, APPLIED.recordedAt());
        when(f.process.read(any(), eq(TARGET), any())).thenReturn(Optional.of(new ObservationVO(TARGET, EXECUTOR, other, REF, NOW.plusSeconds(3))));
        assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, f);
        verify(f.receipts, never()).append(any(), anyString(), anyString(), any());
    }

    @Test
    void a_zero_insert_reads_a_fresh_winner_and_never_overwrites_source_drift() throws Exception {
        var producer = new Fixture();
        var expected = producer.observe();
        var race = new Fixture();
        doReturn(Optional.empty()).when(race.receipts).append(any(), anyString(), anyString(), any());
        when(race.receipts.read(eq(OWNER), eq(RUN), eq(INTENT), eq(1L), any()))
                .thenReturn(Optional.empty()).thenReturn(Optional.of(producer.written.get()));
        assertEquals(expected, race.observe());
        verify(race.connection, times(4)).commit();
        verify(race.receipts).append(any(), anyString(), anyString(), any());

        var drift = new Fixture();
        doAnswer(call -> {
            drift.historical.set(historical("DISPATCHING", 3, REF));
            return Optional.empty();
        }).when(drift.receipts).append(any(), anyString(), anyString(), any());
        when(drift.receipts.read(eq(OWNER), eq(RUN), eq(INTENT), eq(1L), any()))
                .thenReturn(Optional.empty()).thenReturn(Optional.of(producer.written.get()));
        assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, drift);
        verify(drift.connection).rollback();
    }

    @Test
    void a_late_local_applied_result_only_replays_an_existing_matching_native_reference() throws Exception {
        var f = new Fixture();
        var original = f.observe();
        var durable = f.written.get();
        var late = new Fixture();
        late.historical.set(historical("APPLIED", 3, REF));
        assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, late);
        when(late.receipts.read(eq(OWNER), eq(RUN), eq(INTENT), eq(1L), any())).thenReturn(Optional.of(durable));
        assertEquals(original, late.observe());
        late.historical.set(historical("APPLIED", 3, "tianji:ps-start:v1:" + "e".repeat(64)));
        assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, late);
        verify(late.receipts, never()).append(any(), anyString(), anyString(), any());
    }

    @Test
    void rejects_invalid_returning_evidence_inside_the_write_transaction_and_ambient_calls() throws Exception {
        var f = new Fixture();
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            String evidence = call.<String>getArgument(2).replace("\"policyVersion\":1", "\"policyVersion\":0");
            return Optional.of(new StoredReceiptVO(call.getArgument(1), evidence, NOW));
        }).when(f.receipts).append(any(), anyString(), anyString(), any());
        assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, f);
        verify(f.connection, times(2)).commit();
        verify(f.connection).rollback();
        var ambient = new Fixture();
        TransactionSynchronizationManager.initSynchronization();
        try { assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, ambient); }
        finally { TransactionSynchronizationManager.clearSynchronization(); }
        verifyNoInteractions(ambient.source, ambient.intents, ambient.process, ambient.receipts);
    }

    private static void assertCode(AgentErrorCodeEnum expected, Fixture f) {
        assertEquals(expected, assertThrows(AgentLedgerException.class, f::observe).code());
    }

    private static Optional<Permit> allowed(HistoricalToolReceiptAuthorization.Request request) {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        return Optional.of(new Permit(UUID.randomUUID(), request.bindingSha256(), 1, NOW, NOW.plusSeconds(8)));
    }

    private static HistoricalStartReceiptVO historical(String state, long revision, String receiptReference) {
        var outcome = "APPLIED".equals(state) ? new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED,
                Map.of("instanceId", APPLIED.instanceId()), receiptReference, null, null) : null;
        var dispatch = new DispatchVO(RUN, UUID.fromString("20000000-0000-0000-0000-000000000001"), 1, HASH, EXECUTOR);
        var row = new Row(ACTION, TOOL, ORIGIN, HASH, state, revision, NOW.minusSeconds(120), NOW.minusSeconds(40),
                NOW.plusSeconds(30), null, dispatch, outcome == null ? "UNKNOWN" : "CONFIRMED",
                outcome, null, outcome == null ? null : NOW.minusSeconds(40));
        var run = new AgentRunAuthorization.Request(OWNER, RUN, "workflow", 1, HASH, HASH, HASH, "v1", false, NOW.minusSeconds(20));
        return new HistoricalStartReceiptVO(row, run, revision, NOW.minusSeconds(30), TOOL.parametersSha256(ACTION.arguments()),
                "{\"revision\":" + revision + "}", "{}", "{}", "{}");
    }

    private static final class Fixture {
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        final ToolActionIntentSql intents = mock(ToolActionIntentSql.class);
        final ToolStartReceiptReconciliationSql receipts = mock(ToolStartReceiptReconciliationSql.class);
        final HistoricalToolReceiptAuthorization authority = mock(HistoricalToolReceiptAuthorization.class);
        final FixedBpmnStartReceiptRead process = mock(FixedBpmnStartReceiptRead.class);
        final AtomicReference<HistoricalStartReceiptVO> historical = new AtomicReference<>(historical("DISPATCHING", 2, REF));
        final AtomicReference<StoredReceiptVO> written = new AtomicReference<>();
        final ToolStartReceiptReconciliationService service;

        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.createStatement()).thenReturn(mock(Statement.class));
            when(jdbc.queryForObject(anyString(), eq(Boolean.class), anyString())).thenReturn(true);
            when(intents.historicalStartReceipt(eq(OWNER), eq(RUN), eq(INTENT), any())).thenAnswer(call -> {
                assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                return Optional.of(historical.get());
            });
            when(authority.authorize(any())).thenAnswer(call -> allowed(call.getArgument(0)));
            when(process.bind(any())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                return TARGET;
            });
            when(process.read(any(), eq(TARGET), any())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                return Optional.of(OBSERVATION);
            });
            when(receipts.read(eq(OWNER), eq(RUN), eq(INTENT), eq(1L), any())).thenAnswer(call -> {
                assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                return Optional.empty();
            });
            when(receipts.append(any(), anyString(), anyString(), any())).thenAnswer(call -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                var record = new StoredReceiptVO(call.getArgument(1), call.getArgument(2), NOW);
                written.set(record);
                return Optional.of(record);
            });
            service = new ToolStartReceiptReconciliationService(source, jdbc, intents, receipts, authority, process,
                    Clock.fixed(NOW, ZoneOffset.UTC), () -> 0);
        }

        ToolStartReceiptReconciliationService.ReceiptVO observe() { return service.observe(OWNER, RUN, INTENT); }
    }
}
