package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryPreferenceCommands;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryExtractionPolicy;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceExtraction;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceIntent;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceSourceBinding;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryPreferenceIntentStore;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryPreferenceWriteAuthorization;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Orchestration with controlled store/authority/fact boundaries; actual authority and SQL have separate opt-in coverage. */
class MemoryPreferenceWorkTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final MemoryPreferenceWork.Origin ORIGIN = new MemoryPreferenceWork.Origin(20, ACTOR.issuerFingerprint());
    private static final String HASH = "a".repeat(64), RUN = "00000000-0000-0000-0000-000000000001";
    private static final MemoryExtractionPolicy POLICY = new MemoryExtractionPolicy("preferences", 1, Map.of("language", Set.of("zh-CN"), "timezone", Set.of("UTC")), new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, "retention@1#" + HASH, 2);
    private static final MemoryPreferenceSourceBinding BINDING = new MemoryPreferenceSourceBinding("source", 20, ACTOR.issuerFingerprint(), "assistant", 1, HASH, "research", POLICY.reference());
    private final DataSource source = mock(DataSource.class);
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final MemoryPreferenceIntentStore store = mock(MemoryPreferenceIntentStore.class);
    private final PostgresMemoryService memory = mock(PostgresMemoryService.class);
    private final TianjiMemoryPreferenceWriteAuthorization authority = mock(TianjiMemoryPreferenceWriteAuthorization.class);
    private final MemoryPreferenceIntent.Lease initial = lease();
    private final List<UpsertMemoryCommand> commands = commands();
    private MemoryPreferenceWork work;

    @BeforeEach void setup() {
        when(ledger.usesDataSource(source)).thenReturn(true); when(store.usesDataSource(source)).thenReturn(true); when(store.usesLedger(ledger)).thenReturn(true);
        when(memory.usesDataSource(source)).thenReturn(true); when(authority.usesLedger(ledger)).thenReturn(true);
        when(store.candidates(anyLong(), anyString(), anyInt())).thenReturn(List.of(initial.evidence().reservation().intentId()));
        when(store.claim(anyLong(), anyString(), any(), anyString(), any())).thenReturn(Optional.of(initial));
        when(authority.prepare(any(), any())).thenReturn(new TianjiMemoryPreferenceWriteAuthorization.Prepared(MemoryPreferenceExtraction.Outcome.ELIGIBLE, commands));
        when(store.sealCommands(any(), anyInt(), anyString())).thenAnswer(call -> {
            MemoryPreferenceIntent.Lease lease = call.getArgument(0); if (lease.totalCount() != null) return lease;
            return progress(lease, lease.nextIndex(), call.getArgument(1), call.getArgument(2), lease.revision() + 1);
        });
        when(authority.permit(any(), any(), any())).thenAnswer(call -> new MemoryOperationContext(ACTOR, call.getArgument(2), null));
        when(store.acknowledge(any())).thenAnswer(call -> {
            MemoryPreferenceIntent.Lease lease = call.getArgument(0); return lease.nextIndex() + 1 == lease.totalCount() ? Optional.empty()
                    : Optional.of(progress(lease, lease.nextIndex() + 1, lease.totalCount(), lease.commandsSha256(), lease.revision() + 1));
        });
        work = new MemoryPreferenceWork(source, ledger, store, memory, authority, List.of(ORIGIN), Duration.ofSeconds(30), Duration.ofSeconds(1), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void preparation_list_sealing_each_fresh_permit_fact_commit_and_progress_acknowledgement_keep_their_order() {
        var result = apply(4, 4); assertEquals(new MemoryPreferenceWork.Result(1, 2, 2, 0), result);
        var order = inOrder(store, authority, memory); order.verify(store).reconcile(20, ACTOR.issuerFingerprint(), 4);
        order.verify(store).candidates(20, ACTOR.issuerFingerprint(), 4); order.verify(store).claim(eq(20L), eq(ACTOR.issuerFingerprint()), any(), startsWith("memory-preferences-"), eq(Duration.ofSeconds(30)));
        order.verify(authority).prepare(initial, NOW.plusSeconds(10)); order.verify(store).sealCommands(initial, 2, PostgresMemoryService.preferenceCommandListSha256(commands));
        order.verify(authority).permit(argThat(lease -> lease.nextIndex() == 0), eq(commands.getFirst()), eq(NOW.plusSeconds(10)));
        order.verify(memory).upsert(any(), eq(commands.getFirst())); order.verify(store).acknowledge(argThat(lease -> lease.nextIndex() == 0));
        order.verify(authority).permit(argThat(lease -> lease.nextIndex() == 1), eq(commands.get(1)), any()); order.verify(memory).upsert(any(), eq(commands.get(1)));
        order.verify(store).acknowledge(argThat(lease -> lease.nextIndex() == 1)); verify(store, never()).defer(any(), anyString(), any());
    }

    @Test void the_fact_budget_yields_after_an_acknowledged_index_and_the_next_tick_resumes_that_index() {
        assertEquals(new MemoryPreferenceWork.Result(1, 1, 1, 0), apply(2, 1));
        var deferred = org.mockito.ArgumentCaptor.forClass(MemoryPreferenceIntent.Lease.class); verify(store).defer(deferred.capture(), eq("BUDGET_YIELD"), eq(Duration.ofSeconds(1)));
        assertEquals(1, deferred.getValue().nextIndex()); doReturn(Optional.of(deferred.getValue())).when(store).claim(anyLong(), anyString(), any(), anyString(), any());
        assertEquals(new MemoryPreferenceWork.Result(1, 1, 1, 0), apply(2, 1)); verify(memory, times(1)).upsert(any(), eq(commands.getFirst())); verify(memory, times(1)).upsert(any(), eq(commands.get(1)));
    }

    @Test void a_lost_ack_retries_the_same_command_and_an_unknown_fact_commit_consumes_the_attempt_budget() {
        var once = new AtomicBoolean(true);
        doAnswer(call -> { if (once.getAndSet(false)) throw unavailable(); MemoryPreferenceIntent.Lease lease = call.getArgument(0);
            return Optional.of(progress(lease, 1, lease.totalCount(), lease.commandsSha256(), lease.revision() + 1)); }).when(store).acknowledge(any());
        assertEquals(new MemoryPreferenceWork.Result(1, 1, 0, 0), apply(2, 1)); verify(store).defer(argThat(lease -> lease.nextIndex() == 0), eq("WRITE_UNAVAILABLE"), any());
        assertEquals(new MemoryPreferenceWork.Result(1, 1, 1, 0), apply(2, 1)); verify(memory, times(2)).upsert(any(), eq(commands.getFirst()));
        doThrow(unavailable()).when(memory).upsert(any(), any()); clearInvocations(memory);
        var second = UUID.randomUUID(); when(store.candidates(anyLong(), anyString(), anyInt())).thenReturn(List.of(initial.evidence().reservation().intentId(), second));
        assertEquals(new MemoryPreferenceWork.Result(1, 1, 0, 0), apply(2, 1)); verify(memory, times(1)).upsert(any(), any());
    }

    @Test void current_denial_closes_the_intent_unavailability_defers_and_a_stale_lease_is_left_for_reconciliation() {
        doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Denied")).when(authority).prepare(any(), any());
        apply(2, 2); verify(store).finish(initial, MemoryPreferenceIntent.State.REJECTED, "WRITE_PERMISSION_DENIED"); verifyNoFactAdmission();
        doThrow(unavailable()).when(authority).prepare(any(), any()); apply(2, 2); verify(store).defer(initial, "WRITE_UNAVAILABLE", Duration.ofSeconds(1));
        clearInvocations(store); doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Stale")).when(authority).prepare(any(), any()); apply(2, 2);
        verify(store, never()).finish(any(), any(), anyString()); verify(store, never()).defer(any(), anyString(), any());
    }

    @Test void no_declaration_is_skipped_and_rejected_preflight_never_creates_or_confirms_a_fact() {
        for (var outcome : List.of(MemoryPreferenceExtraction.Outcome.NO_DECLARATION, MemoryPreferenceExtraction.Outcome.UNSUPPORTED_PREFERENCE)) {
            doReturn(new TianjiMemoryPreferenceWriteAuthorization.Prepared(outcome, List.of())).when(authority).prepare(any(), any()); apply(2, 2);
            verify(store).finish(any(), eq(outcome == MemoryPreferenceExtraction.Outcome.NO_DECLARATION ? MemoryPreferenceIntent.State.SKIPPED : MemoryPreferenceIntent.State.REJECTED), eq("EXTRACTION_" + outcome.name()));
        }
        verifyNoFactAdmission();
    }

    @Test void stopping_after_a_permit_was_prepared_does_not_admit_a_fact() {
        var admitted = new AtomicBoolean(true);
        doAnswer(call -> { admitted.set(false); return new MemoryOperationContext(ACTOR, NOW.plusSeconds(10), null); }).when(authority).permit(any(), any(), any());
        var result = work.apply(ORIGIN, 2, 2, NOW.plusSeconds(10), admitted::get); assertEquals(0, result.attemptedFacts()); verify(memory, never()).upsert(any(), any());
        verify(store).defer(argThat(lease -> lease.nextIndex() == 0), eq("BUDGET_YIELD"), any());
    }

    @Test void cleanup_consumes_the_record_budget_and_failed_claims_do_not_starve_later_candidates() {
        when(store.reconcile(20, ACTOR.issuerFingerprint(), 2)).thenReturn(2); assertEquals(new MemoryPreferenceWork.Result(0, 0, 0, 2), apply(2, 2)); verify(store, never()).candidates(anyLong(), anyString(), anyInt());
        doReturn(0).when(store).reconcile(anyLong(), anyString(), anyInt()); when(store.candidates(anyLong(), anyString(), anyInt())).thenReturn(List.of(UUID.randomUUID(), initial.evidence().reservation().intentId()));
        doThrow(unavailable()).doReturn(Optional.of(initial)).when(store).claim(anyLong(), anyString(), any(), anyString(), any());
        assertEquals(new MemoryPreferenceWork.Result(2, 2, 2, 0), apply(2, 2));
    }

    @Test void wrong_origins_deadlines_admission_and_ambient_transactions_fail_before_any_work() {
        assertEquals(new MemoryPreferenceWork.Result(0, 0, 0, 0), work.apply(ORIGIN, 1, 1, NOW, () -> true));
        assertEquals(new MemoryPreferenceWork.Result(0, 0, 0, 0), work.apply(ORIGIN, 1, 1, NOW.plusSeconds(10), () -> false));
        assertThrows(IllegalArgumentException.class, () -> work.apply(new MemoryPreferenceWork.Origin(21, HASH), 1, 1, NOW.plusSeconds(10), () -> true));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> apply(1, 1)); } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verify(store, never()).reconcile(anyLong(), anyString(), anyInt()); verifyNoFactAdmission();
    }

    private MemoryPreferenceWork.Result apply(int records, int facts) { return work.apply(ORIGIN, records, facts, NOW.plusSeconds(10), () -> true); }
    private void verifyNoFactAdmission() { verify(authority, never()).permit(any(), any(), any()); verify(memory, never()).upsert(any(), any()); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Fixture unavailable"); }
    private static MemoryPreferenceIntent.Lease progress(MemoryPreferenceIntent.Lease lease, int index, int total, String hash, long revision) {
        return new MemoryPreferenceIntent.Lease(lease.evidence(), lease.owner(), lease.epoch(), lease.expiresAt(), lease.sourceEventId(), index, total, hash, revision);
    }
    private static MemoryPreferenceIntent.Lease lease() {
        var run = new AgentRunAuthorization.Request(ACTOR, RUN, "assistant", 1, HASH, HASH, HASH, "policy", true, NOW.plusSeconds(60)); var resource = URI.create("https://agent.example"); var deadline = NOW.plusSeconds(120);
        var reservation = new MemoryPreferenceIntent.Reservation(run, BINDING, resource, deadline, 1, MemoryPreferenceIntent.intentId(run, "research"), MemoryPreferenceIntent.fingerprint(run, BINDING, resource, deadline));
        return new MemoryPreferenceIntent.Lease(new MemoryPreferenceIntent.Evidence(reservation, new AgentExecutionGrant.Ticket("9" + "1".repeat(62), "ENC:v1:test:A:A", NOW.plusSeconds(90))), "worker", 1, NOW.plusSeconds(30), "terminal", 0, null, null, 3);
    }
    private static List<UpsertMemoryCommand> commands() {
        var extracted = new MemoryPreferenceExtraction().evaluate(new MemoryPreferenceExtraction.Source(AgentRunStatusEnum.COMPLETED, MemoryPreferenceExtraction.SourceKind.USER_INPUT, true, "memory.preference.language=zh-CN\nmemory.preference.timezone=UTC"), POLICY);
        return MemoryPreferenceCommands.create(ACTOR, new ExtractMemoryPreferencesCommand(new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"), "research", RUN, "terminal", "source", POLICY.reference()), POLICY, extracted);
    }
}
