package top.egon.cola.platform.tianji.agent.application.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryExtractionPolicy;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceSourceBinding;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryPreferenceAdmissionTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final MemoryOwnerDTO OWNER = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
    private static final String HASH = "a".repeat(64), RETENTION = "retention@1#" + "b".repeat(64);
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final PostgresMemoryService memory = mock(PostgresMemoryService.class);
    private final MemoryAuthorization authorization = mock(MemoryAuthorization.class);
    private final List<UpsertMemoryCommand> writes = new ArrayList<>();

    @BeforeEach void setup() {
        when(memory.policyReferences()).thenReturn(List.of(RETENTION)); when(authorization.authorize(any())).thenReturn(MemoryAuthorization.Decision.ALLOW);
        when(ledger.executionMaterial(any(), any())).thenReturn(material(AgentRunStatusEnum.COMPLETED, "memory.preference.language=zh-CN", HASH));
        when(ledger.events(any(), any())).thenReturn(List.of(event("terminal", "RUN_STATUS_CHANGED", "COMPLETED")));
        when(memory.upsert(any(), any())).thenAnswer(call -> {
            var command = call.getArgument(1, UpsertMemoryCommand.class); writes.add(command);
            return new MemoryMutationResultDTO(command.commandId(), command.owner(), command.scopeId(), command.memoryId(), 1,
                    MemoryMutationOutcomeEnum.CREATED, MemoryStateEnum.CANDIDATE, NOW);
        });
    }

    @Test void only_stored_user_input_and_exact_completed_terminal_evidence_create_unconfirmed_candidates() {
        var result = admission(policy()).extract(context(), command(policy()));
        assertEquals(MemoryPreferenceExtractionOutcomeEnum.ELIGIBLE, result.outcome()); assertEquals(1, writes.size());
        var write = writes.getFirst(); assertFalse(write.confirmed()); assertEquals(0, write.expectedRevision()); assertEquals(OWNER, write.owner());
        assertEquals(Map.of("language", "zh-CN"), write.content().facts()); assertNull(write.content().text()); assertNull(write.effectiveAt()); assertNull(write.expiresAt());
        assertEquals("run", write.source().sourceRunId()); assertEquals("terminal", write.source().sourceEventId()); assertTrue(write.source().reference().startsWith("memory-preference:source#"));
        assertEquals(command(policy()).itemCommandId("language"), write.commandId()); assertEquals(MemorySensitivityEnum.CONFIDENTIAL, write.sensitivity());
        var order = inOrder(authorization, ledger, memory);
        order.verify(authorization).authorize(argThat(request -> request.operation() == MemoryAuthorization.Operation.UPSERT && request.sourceRunId().equals("run") && request.sourceEventId().equals("terminal")));
        order.verify(ledger).executionMaterial(eq(ACTOR), eq(new GetAgentRunQuery("run")));
        order.verify(authorization).authorize(any()); order.verify(ledger).events(eq(ACTOR), eq(new AgentEventsQuery("run", 4, 1)));
        order.verify(authorization).authorize(any()); order.verify(memory).upsert(eq(context()), any());
    }

    @Test void deny_unavailable_and_current_revocation_never_become_an_empty_or_writable_source() {
        for (var decision : List.of(MemoryAuthorization.Decision.DENY, MemoryAuthorization.Decision.UNAVAILABLE)) {
            when(authorization.authorize(any())).thenReturn(decision);
            assertThrows(AgentLedgerException.class, () -> admission(policy()).extract(context(), command(policy())));
        }
        verifyNoInteractions(ledger); assertTrue(writes.isEmpty());
        when(authorization.authorize(any())).thenReturn(MemoryAuthorization.Decision.ALLOW, MemoryAuthorization.Decision.DENY);
        assertThrows(AgentLedgerException.class, () -> admission(policy()).extract(context(), command(policy())));
        verify(ledger, never()).events(any(), any()); assertTrue(writes.isEmpty());
    }

    @Test void mismatched_terminal_identity_type_state_or_source_definition_cannot_write() {
        for (var event : List.of(event("wrong", "RUN_STATUS_CHANGED", "COMPLETED"), event("terminal", "MODEL_FINAL", "COMPLETED"), event("terminal", "RUN_STATUS_CHANGED", "FAILED"))) {
            when(ledger.events(any(), any())).thenReturn(List.of(event));
            assertThrows(AgentLedgerException.class, () -> admission(policy()).extract(context(), command(policy())));
        }
        when(ledger.executionMaterial(any(), any())).thenReturn(material(AgentRunStatusEnum.COMPLETED, "memory.preference.language=zh-CN", "c".repeat(64)));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> admission(policy()).extract(context(), command(policy()))).code());
        assertTrue(writes.isEmpty());
    }

    @Test void failed_sources_unknown_declarations_and_task_data_do_not_produce_partial_writes() {
        for (var status : List.of(AgentRunStatusEnum.FAILED, AgentRunStatusEnum.CANCELLED, AgentRunStatusEnum.RUNNING)) {
            when(ledger.executionMaterial(any(), any())).thenReturn(material(status, "memory.preference.language=zh-CN", HASH));
            assertEquals(MemoryPreferenceExtractionOutcomeEnum.RUN_NOT_COMPLETED, admission(policy()).extract(context(), command(policy())).outcome());
        }
        for (var text : List.of("no explicit preference", "memory.preference.password=fixture-private", "memory.preference.language=zh-CN\nmemory.preference.language=en-US")) {
            when(ledger.executionMaterial(any(), any())).thenReturn(material(AgentRunStatusEnum.COMPLETED, text, HASH));
            assertTrue(admission(policy()).extract(context(), command(policy())).items().isEmpty());
        }
        assertTrue(writes.isEmpty());
    }

    @Test void partial_retry_and_policy_changes_keep_the_same_source_write_and_memory_identities() {
        when(ledger.executionMaterial(any(), any())).thenReturn(material(AgentRunStatusEnum.COMPLETED, "memory.preference.language=zh-CN\nmemory.preference.timezone=UTC", HASH));
        var failOnce = new AtomicBoolean(true);
        doAnswer(call -> {
            var command = call.getArgument(1, UpsertMemoryCommand.class); writes.add(command);
            if (command.content().facts().containsKey("timezone") && failOnce.getAndSet(false)) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Receipt unavailable");
            return new MemoryMutationResultDTO(command.commandId(), command.owner(), command.scopeId(), command.memoryId(), 1, MemoryMutationOutcomeEnum.CREATED, MemoryStateEnum.CANDIDATE, NOW);
        }).when(memory).upsert(any(), any());
        var service = admission(policy()); assertThrows(AgentLedgerException.class, () -> service.extract(context(), command(policy())));
        var original = List.copyOf(writes); writes.clear();
        service.extract(context(), command(policy())); assertEquals(original, writes);
        writes.clear(); service.extract(context(), command(policy()));
        assertEquals(original.stream().map(UpsertMemoryCommand::memoryId).toList(), writes.stream().map(UpsertMemoryCommand::memoryId).toList());
        assertEquals(original.getFirst().commandId(), writes.getFirst().commandId());
        var changed = new MemoryExtractionPolicy("preferences", 2, policy().preferenceValues(), new BigDecimal("0.4"), policy().sensitivity(), RETENTION, 3);
        writes.clear(); admission(changed).extract(context(), command(changed));
        assertEquals(original.stream().map(UpsertMemoryCommand::memoryId).toList(), writes.stream().map(UpsertMemoryCommand::memoryId).toList());
    }

    @Test void foreign_owner_realm_or_background_context_and_ambient_transactions_fail_before_source_access() {
        var foreign = new ExtractMemoryPreferencesCommand(new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "bob"), "research", "run", "terminal", "source", policy().reference());
        assertThrows(AgentLedgerException.class, () -> admission(policy()).extract(context(), foreign));
        var other = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/21");
        assertThrows(AgentLedgerException.class, () -> admission(policy()).extract(new MemoryOperationContext(other, NOW.plusSeconds(30), null), command(policy())));
        var background = new MemoryOperationContext(ACTOR, NOW.plusSeconds(30), material(AgentRunStatusEnum.COMPLETED, "text", HASH).authorization());
        assertThrows(AgentLedgerException.class, () -> admission(policy()).extract(background, command(policy())));
        assertThrows(AgentLedgerException.class, () -> admission(policy()).extract(new MemoryOperationContext(ACTOR, NOW, null), command(policy())));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(AgentLedgerException.class, () -> admission(policy()).extract(context(), command(policy()))); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(ledger); assertTrue(writes.isEmpty());
    }

    private MemoryPreferenceAdmission admission(MemoryExtractionPolicy policy) { return new MemoryPreferenceAdmission(ledger, memory, authorization, List.of(binding(policy)), List.of(policy), CLOCK); }
    private static MemoryPreferenceSourceBinding binding(MemoryExtractionPolicy policy) { return new MemoryPreferenceSourceBinding("source", 20, ACTOR.issuerFingerprint(), "assistant", 1, HASH, "research", policy.reference()); }
    private static MemoryExtractionPolicy policy() { return new MemoryExtractionPolicy("preferences", 1, Map.of("language", Set.of("zh-CN", "en-US"), "timezone", Set.of("UTC")), new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, RETENTION, 3); }
    private static ExtractMemoryPreferencesCommand command(MemoryExtractionPolicy policy) { return new ExtractMemoryPreferencesCommand(OWNER, "research", "run", "terminal", "source", policy.reference()); }
    private static MemoryOperationContext context() { return new MemoryOperationContext(ACTOR, NOW.plusSeconds(30), null); }
    private static AgentEventDTO event(String id, String type, String status) { return new AgentEventDTO(id, "run", 5, type, null, null, null, NOW, null, "1", Map.of("status", status)); }
    private static AgentLedgerService.ExecutionMaterial material(AgentRunStatusEnum status, String text, String hash) {
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0, 1000, 100, BigDecimal.ONE, "USD", 1, Set.of(), "retry", "approval");
        var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1", new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route", "policy"), policy, Map.of());
        var snapshot = new AgentExecutionSnapshot(definition, hash, policy, new AgentInputDTO(text, null, Map.of("preference", "memory.preference.password=fixture-private", "owner", "bob")), null);
        var run = new AgentRunDTO("run", "assistant", 1, null, status, null, false, 1, 6, 5, NOW.minusSeconds(60), NOW.minusSeconds(90), NOW.minusSeconds(70));
        var auth = new AgentRunAuthorization.Request(ACTOR, "run", "assistant", 1, hash, HASH, HASH, "policy", false, NOW.plusSeconds(30));
        return new AgentLedgerService.ExecutionMaterial(run, snapshot, auth);
    }
}
