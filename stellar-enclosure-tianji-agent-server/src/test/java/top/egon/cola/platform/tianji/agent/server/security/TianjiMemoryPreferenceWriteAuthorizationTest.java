package top.egon.cola.platform.tianji.agent.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryExtractionPolicy;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceExtraction;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceIntent;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceSourceBinding;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceWritePermit;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryPreferenceIntentStore;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.DataScopeDecision;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionScopeAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Current-IAM/owned-source/capability boundaries with controlled clients; no IAM or PostgreSQL runtime is started. */
class TianjiMemoryPreferenceWriteAuthorizationTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final URI ISSUER = URI.create("https://idp.example/20"), RESOURCE = URI.create("https://agent.example");
    private static final String HASH = "a".repeat(64), RUN = "00000000-0000-0000-0000-000000000001", TEXT = "memory.preference.language=zh-CN";
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toASCIIString());
    private static final MemoryExtractionPolicy POLICY = new MemoryExtractionPolicy("preferences", 1, Map.of("language", Set.of("zh-CN", "en-US")),
            new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, "retention@1#" + "b".repeat(64), 2);
    private static final MemoryPreferenceSourceBinding BINDING = new MemoryPreferenceSourceBinding("source", 20, ACTOR.issuerFingerprint(), "assistant", 1, HASH, "research", POLICY.reference());
    private static final AgentRunAuthorization.Request RUN_AUTH = new AgentRunAuthorization.Request(ACTOR, RUN, "assistant", 1, HASH, HASH, HASH, "execution-policy", true, NOW.minusSeconds(30));
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final MemoryPreferenceIntentStore store = mock(MemoryPreferenceIntentStore.class);
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
    private final HttpExecutionAuthorizationClient client = mock(HttpExecutionAuthorizationClient.class);
    private final TrustedIssuerRegistry.TrustedIssuerVO trust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"),
            "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1);
    private TianjiMemoryPreferenceWriteAuthorization authority;

    @BeforeEach void setup() {
        when(store.usesLedger(ledger)).thenReturn(true); when(store.currentLease(any())).thenAnswer(call -> call.getArgument(0));
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trust)); when(ledger.databaseNow()).thenReturn(NOW);
        when(ledger.executionMaterial(eq(ACTOR), any())).thenReturn(material(TEXT, AgentRunStatusEnum.COMPLETED, false));
        when(ledger.events(eq(ACTOR), any())).thenReturn(List.of(event("terminal", "COMPLETED")));
        when(client.decideScopes(any(), any())).thenAnswer(call -> decision(call.getArgument(1), NOW.plusSeconds(20), scopes()));
        authority = create(Duration.ofSeconds(5), 2);
    }
    @AfterEach void cleanup() { authority.close(); }

    @Test void independent_write_and_source_read_precede_input_and_bind_the_original_intent_after_the_run_deadline() {
        var lease = lease(null, null, 0); var prepared = authority.prepare(lease, NOW.plusSeconds(25));
        assertEquals(MemoryPreferenceExtraction.Outcome.ELIGIBLE, prepared.outcome()); assertEquals(1, prepared.commands().size());
        var command = prepared.commands().getFirst(); assertFalse(command.confirmed()); assertEquals(0, command.expectedRevision());
        assertEquals(Map.of("language", "zh-CN"), command.content().facts()); assertEquals(RUN, command.source().sourceRunId()); assertEquals("terminal", command.source().sourceEventId());
        var expected = org.mockito.ArgumentCaptor.forClass(HttpExecutionAuthorizationClient.Expectation.class);
        var order = inOrder(store, client, ledger); order.verify(store).currentLease(lease); order.verify(client).decideScopes(any(), expected.capture());
        order.verify(store).currentLease(lease); order.verify(ledger).databaseNow(); order.verify(ledger).executionMaterial(eq(ACTOR), eq(new GetAgentRunQuery(RUN)));
        order.verify(ledger).events(eq(ACTOR), eq(new AgentEventsQuery(RUN, 4, 1)));
        assertEquals(MemoryPreferenceIntent.PURPOSE, expected.getValue().purpose()); assertFalse(expected.getValue().sessionRequired());
        assertEquals(lease.evidence().reservation().intentId().toString(), expected.getValue().intentId()); assertEquals(lease.evidence().reservation().bindingSha256(), expected.getValue().bindingSha256());
        assertEquals(NOW.plusSeconds(120), expected.getValue().deadlineAt()); assertNotEquals(RUN_AUTH.deadlineAt(), expected.getValue().deadlineAt());
        verify(client, never()).decide(any(), any()); verify(client, never()).decideService(any(), any()); verify(store, never()).verifyFence(any());
        assertFalse(prepared.toString().contains("zh-CN"));
    }

    @Test void a_permit_is_one_use_and_the_exact_next_command_crosses_a_separate_same_transaction_fence() {
        var prepared = authority.prepare(lease(null, null, 0), NOW.plusSeconds(25)); var command = prepared.commands().getFirst();
        var lease = lease(1, PostgresMemoryService.preferenceCommandListSha256(prepared.commands()), 0);
        var context = authority.permit(lease, command, NOW.plusSeconds(25)); assertNull(context.runAuthorization());
        assertEquals(ACTOR, context.actor()); assertEquals(command, context.preferenceWrite().command()); assertTrue(context.deadlineAt().isBefore(lease.expiresAt()));
        assertThrows(AgentLedgerException.class, context.preferenceWrite()::verifyTransaction); verify(store, never()).verifyFence(any());
        assertEquals(MemoryAuthorization.Decision.DENY, authority.authorize(request(context, MemoryAuthorization.Operation.GET)));
        assertEquals(MemoryAuthorization.Decision.ALLOW, authority.authorize(request(context, MemoryAuthorization.Operation.UPSERT)));
        assertEquals(MemoryAuthorization.Decision.DENY, authority.authorize(request(context, MemoryAuthorization.Operation.UPSERT)));
        context.preferenceWrite().verifyTransaction(); verify(store).verifyFence(lease);
        assertThrows(AgentLedgerException.class, context.preferenceWrite()::verifyTransaction);
        assertFalse(context.toString().contains("zh-CN")); assertFalse(context.preferenceWrite().toString().contains("sealed"));
    }

    @Test void a_substituted_body_order_progress_or_unsealed_list_cannot_mint_a_permit() {
        var prepared = authority.prepare(lease(null, null, 0), NOW.plusSeconds(25)); var command = prepared.commands().getFirst();
        assertThrows(AgentLedgerException.class, () -> authority.permit(lease(null, null, 0), command, NOW.plusSeconds(25)));
        assertThrows(AgentLedgerException.class, () -> authority.permit(lease(1, HASH, 0), command, NOW.plusSeconds(25)));
        assertThrows(AgentLedgerException.class, () -> authority.permit(lease(1, PostgresMemoryService.preferenceCommandListSha256(prepared.commands()), 1), command, NOW.plusSeconds(25)));
        var changed = new UpsertMemoryCommand(command.commandId(), command.owner(), command.scopeId(), command.memoryId(), command.kind(), 0, command.content(), command.source(),
                new BigDecimal("0.4"), false, command.sensitivity(), command.retentionPolicyRef(), null, null);
        assertThrows(AgentLedgerException.class, () -> authority.permit(lease(1, PostgresMemoryService.preferenceCommandListSha256(prepared.commands()), 0), changed, NOW.plusSeconds(25)));
    }

    @Test void missing_denied_indeterminate_or_unknown_source_authority_cannot_read_the_input() {
        for (var scopes : List.of(Map.<String, DataScopeDecision>of(), Map.of(TianjiAgentPermissions.MEMORY_WRITE, scope(TianjiAgentPermissions.MEMORY_WRITE, top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision.ALLOW)),
                Map.of(TianjiAgentPermissions.MEMORY_WRITE, scope(TianjiAgentPermissions.MEMORY_WRITE, top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision.DENY), TianjiAgentPermissions.READ, scopes().get(TianjiAgentPermissions.READ)))) {
            doAnswer(call -> decision(call.getArgument(1), NOW.plusSeconds(20), scopes)).when(client).decideScopes(any(), any());
            assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25))).code());
        }
        doAnswer(call -> decision(call.getArgument(1), NOW.plusSeconds(20), Map.of(TianjiAgentPermissions.MEMORY_WRITE,
                scope(TianjiAgentPermissions.MEMORY_WRITE, top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision.INDETERMINATE)))).when(client).decideScopes(any(), any());
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25))).code());
        doThrow(new HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException()).when(client).decideScopes(any(), any());
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25))).code());
        doThrow(new IllegalStateException("private authority detail")).when(client).decideScopes(any(), any());
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25))).code());
        verify(ledger, never()).executionMaterial(any(), any()); verify(ledger, never()).events(any(), any());
    }

    @Test void current_realm_or_lease_changes_after_the_network_or_source_read_invalidate_the_whole_result() {
        when(store.currentLease(any())).thenAnswer(call -> call.getArgument(0)).thenThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Lease changed"));
        assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25))); verify(ledger, never()).executionMaterial(any(), any());
        doAnswer(call -> call.getArgument(0)).when(store).currentLease(any());
        when(issuers.findByIssuer(anyString())).thenReturn(Optional.of(trust), Optional.empty());
        assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25))); verify(ledger, never()).executionMaterial(any(), any());
        doReturn(Optional.of(trust)).when(issuers).findByIssuer(anyString());
        when(store.currentLease(any())).thenAnswer(call -> call.getArgument(0)).thenAnswer(call -> call.getArgument(0)).thenThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Lease changed"));
        assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25))); verify(ledger).executionMaterial(any(), any());
    }

    @Test void expired_and_future_generated_decisions_are_unavailable_before_input_access() {
        for (var expiry : List.of(NOW, NOW.plusSeconds(91))) {
            doAnswer(call -> decision(call.getArgument(1), expiry, scopes())).when(client).decideScopes(any(), any());
            assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25)));
        }
        doAnswer(call -> { var value = decision(call.getArgument(1), NOW.plusSeconds(20), Map.of()); return new ExecutionScopeAuthorizationContract.Decision(value.execution(), "60", NOW.plusSeconds(1), Map.of()); }).when(client).decideScopes(any(), any());
        assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25))); verify(ledger, never()).executionMaterial(any(), any());
    }

    @Test void failed_cancelled_or_changed_terminal_sources_cannot_become_writable_and_no_declaration_is_an_explicit_outcome() {
        for (var status : List.of(AgentRunStatusEnum.RUNNING, AgentRunStatusEnum.FAILED, AgentRunStatusEnum.CANCELLED)) {
            when(ledger.executionMaterial(any(), any())).thenReturn(material(TEXT, status, false));
            assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25)));
        }
        when(ledger.executionMaterial(any(), any())).thenReturn(material(TEXT, AgentRunStatusEnum.COMPLETED, true));
        assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25)));
        when(ledger.executionMaterial(any(), any())).thenReturn(material(TEXT, AgentRunStatusEnum.COMPLETED, false)); when(ledger.events(any(), any())).thenReturn(List.of(event("other", "COMPLETED")));
        assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25)));
        when(ledger.events(any(), any())).thenReturn(List.of(event("terminal", "COMPLETED"))); when(ledger.executionMaterial(any(), any())).thenReturn(material("ordinary text", AgentRunStatusEnum.COMPLETED, false));
        var empty = authority.prepare(lease(null, null, 0), NOW.plusSeconds(25)); assertEquals(MemoryPreferenceExtraction.Outcome.NO_DECLARATION, empty.outcome()); assertTrue(empty.commands().isEmpty());
    }

    @Test void foreign_permits_and_source_ids_or_ambient_run_contexts_cannot_borrow_a_gate() {
        var prepared = authority.prepare(lease(null, null, 0), NOW.plusSeconds(25)); var command = prepared.commands().getFirst();
        var lease = lease(1, PostgresMemoryService.preferenceCommandListSha256(prepared.commands()), 0); var context = authority.permit(lease, command, NOW.plusSeconds(25));
        try (var other = create(Duration.ofSeconds(5), 1)) { assertEquals(MemoryAuthorization.Decision.DENY, other.authorize(request(context, MemoryAuthorization.Operation.UPSERT))); }
        assertEquals(MemoryAuthorization.Decision.DENY, authority.authorize(new MemoryAuthorization.Request(context, command.owner(), command.scopeId(), MemoryAuthorization.Operation.UPSERT, command.memoryId(), 0, RUN, "wrong")));
        var unknown = mock(MemoryPreferenceWritePermit.class); when(unknown.actor()).thenReturn(ACTOR); when(unknown.command()).thenReturn(command); when(unknown.expiresAt()).thenReturn(NOW.plusSeconds(20));
        assertEquals(MemoryAuthorization.Decision.DENY, authority.authorize(request(new MemoryOperationContext(ACTOR, NOW.plusSeconds(10), null, unknown), MemoryAuthorization.Operation.UPSERT)));
        assertEquals(MemoryAuthorization.Decision.ALLOW, authority.authorize(request(context, MemoryAuthorization.Operation.UPSERT)));
        authority.close(); assertThrows(AgentLedgerException.class, context.preferenceWrite()::verifyTransaction); verify(store, never()).verifyFence(any());
    }

    @Test void a_timed_out_uninterruptible_call_retains_its_only_admission_slot() throws Exception {
        authority.close(); authority = create(Duration.ofMillis(120), 1); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); while (release.getCount() > 0) { try { release.await(); } catch (InterruptedException ignored) { } } return decision(call.getArgument(1), NOW.plusSeconds(20), scopes()); }).when(client).decideScopes(any(), any());
        try {
            assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25))); assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25))); verify(client, times(1)).decideScopes(any(), any());
            verify(ledger, never()).executionMaterial(any(), any());
        } finally { release.countDown(); }
    }

    @Test void local_trust_revocation_after_minting_invalidates_admission_and_the_transaction_callback_without_network() {
        var prepared = authority.prepare(lease(null, null, 0), NOW.plusSeconds(25)); var command = prepared.commands().getFirst();
        var lease = lease(1, PostgresMemoryService.preferenceCommandListSha256(prepared.commands()), 0);
        var beforeAdmission = authority.permit(lease, command, NOW.plusSeconds(25)); var beforeTransaction = authority.permit(lease, command, NOW.plusSeconds(25));
        assertEquals(MemoryAuthorization.Decision.ALLOW, authority.authorize(request(beforeTransaction, MemoryAuthorization.Operation.UPSERT)));
        doReturn(Optional.empty()).when(issuers).findByIssuer(anyString()); clearInvocations(client);
        assertEquals(MemoryAuthorization.Decision.UNAVAILABLE, authority.authorize(request(beforeAdmission, MemoryAuthorization.Operation.UPSERT)));
        assertThrows(AgentLedgerException.class, beforeTransaction.preferenceWrite()::verifyTransaction);
        verifyNoInteractions(client); verify(store, never()).verifyFence(any());
    }

    @Test void ambient_transactions_expired_requests_and_shutdown_make_no_network_or_source_calls() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25))); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW));
        authority.close(); assertThrows(AgentLedgerException.class, () -> authority.prepare(lease(null, null, 0), NOW.plusSeconds(25)));
        verifyNoInteractions(client); verify(store, never()).currentLease(any()); verify(ledger, never()).executionMaterial(any(), any());
    }

    private TianjiMemoryPreferenceWriteAuthorization create(Duration timeout, int concurrency) {
        return new TianjiMemoryPreferenceWriteAuthorization(ledger, store, List.of(BINDING), List.of(POLICY), RESOURCE, "tianji", issuers,
                Map.of(ACTOR.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(ISSUER, "20", client)), timeout, concurrency, CLOCK);
    }
    private static MemoryPreferenceIntent.Lease lease(Integer total, String commandsHash, int index) {
        Instant deadline = NOW.plusSeconds(120); var reservation = new MemoryPreferenceIntent.Reservation(RUN_AUTH, BINDING, RESOURCE, deadline, 2,
                MemoryPreferenceIntent.intentId(RUN_AUTH, "research"), MemoryPreferenceIntent.fingerprint(RUN_AUTH, BINDING, RESOURCE, deadline));
        return new MemoryPreferenceIntent.Lease(new MemoryPreferenceIntent.Evidence(reservation, new AgentExecutionGrant.Ticket("9" + "1".repeat(62), "ENC:v1:test:nonce:sealed", NOW.plusSeconds(90))),
                "worker", 3, NOW.plusSeconds(30), "terminal", index, total, commandsHash, 4);
    }
    private static MemoryAuthorization.Request request(MemoryOperationContext context, MemoryAuthorization.Operation operation) {
        var command = context.preferenceWrite().command(); return new MemoryAuthorization.Request(context, command.owner(), command.scopeId(), operation,
                command.memoryId(), 0, command.source().sourceRunId(), command.source().sourceEventId());
    }
    private ExecutionScopeAuthorizationContract.Decision decision(HttpExecutionAuthorizationClient.Expectation expected, Instant expires, Map<String, DataScopeDecision> scopes) {
        return new ExecutionScopeAuthorizationContract.Decision(new ExecutionAuthorizationContract.Decision(lease(null, null, 0).evidence().ticket().grantId(), ISSUER.toASCIIString(), "20", "alice", RESOURCE.toASCIIString(),
                MemoryPreferenceIntent.PURPOSE, expected.intentId(), expected.bindingSha256(), false, expected.deadlineAt(), expires, 2, 3), "60", NOW, scopes);
    }
    private static Map<String, DataScopeDecision> scopes() { return Map.of(TianjiAgentPermissions.MEMORY_WRITE, scope(TianjiAgentPermissions.MEMORY_WRITE, top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision.ALLOW),
            TianjiAgentPermissions.READ, scope(TianjiAgentPermissions.READ, top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision.ALLOW)); }
    private static DataScopeDecision scope(String permission, top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision decision) {
        boolean allowed = decision == top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision.ALLOW;
        return new DataScopeDecision(decision, "CURRENT", "20", "60", permission, allowed ? "SELF" : "NONE", false, Set.of(), false, Set.of(), false, Set.of(), allowed, allowed ? "60" : null, "directory", 1, 2, 3, List.of(), NOW);
    }
    private static AgentEventDTO event(String id, String status) { return new AgentEventDTO(id, RUN, 5, "RUN_STATUS_CHANGED", null, null, null, NOW.minusSeconds(10), null, "1", Map.of("status", status)); }
    private static AgentLedgerService.ExecutionMaterial material(String text, AgentRunStatusEnum status, boolean cancelled) {
        var execution = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0, 1000, 100, BigDecimal.ONE, "USD", 1, Set.of(), "retry", "approval");
        var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1", new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route", "policy"), execution, Map.of());
        var snapshot = new AgentExecutionSnapshot(definition, HASH, execution, new AgentInputDTO(text, null, Map.of("ignored", "memory.preference.language=en-US")), null);
        var run = new AgentRunDTO(RUN, "assistant", 1, null, status, null, cancelled, 1, 6, 5, RUN_AUTH.deadlineAt(), NOW.minusSeconds(90), NOW.minusSeconds(10));
        return new AgentLedgerService.ExecutionMaterial(run, snapshot, RUN_AUTH);
    }
}
