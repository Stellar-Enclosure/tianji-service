package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceIntent;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.VersionedMemoryEmbeddingModel;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryPreferenceIntentStore;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.memory.CancelMemoryPreferenceIntentCommand;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryPreferenceIntentQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceIntentStateEnum;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import javax.sql.DataSource;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryPreferenceIntentAccessTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toASCIIString());
    private static final MemoryOwnerDTO OWNER = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final DataSource source = mock(DataSource.class);
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final MemoryPreferenceIntentStore store = mock(MemoryPreferenceIntentStore.class);
    private final TianjiAgentAccess access = mock(TianjiAgentAccess.class);
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
    private final MemoryOperationContext context = new MemoryOperationContext(ACTOR, NOW.plusSeconds(30), null);
    private final GetMemoryPreferenceIntentQuery query = new GetMemoryPreferenceIntentQuery(OWNER, "research", "run");
    private MemoryPreferenceIntentAccess service;

    @BeforeEach void setup() {
        when(ledger.usesDataSource(source)).thenReturn(true); when(store.usesDataSource(source)).thenReturn(true); when(store.usesLedger(ledger)).thenReturn(true);
        when(access.require(anyString())).thenReturn(new TianjiAgentAccess.Authorized(ACTOR, NOW.plusSeconds(60), ISSUER));
        when(store.view(ACTOR, "run", "research")).thenReturn(Optional.of(view(MemoryPreferenceIntent.State.READY, 3)));
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,
                URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1)));
        var retention = new MemoryRetentionPolicy("retention", 1, Duration.ofDays(1), Duration.ofDays(30), 100, Set.of(), Set.of(), false);
        var storage = new TianjiMemoryProperties(2, "0.8.0", null, 1, new VersionedMemoryEmbeddingModel.Profile("memory", 1, "embedding", "text-embedding-3-small", 2, 8191, 64, 16), List.of(retention), List.of(new TianjiMemoryProperties.Origin(20, ISSUER)));
        service = new MemoryPreferenceIntentAccess(source, ledger, store, access, storage, issuers, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void current_status_and_source_permissions_precede_owned_metadata_and_are_rechecked_before_returning_old_binding_progress() {
        var status = service.status(context, query); assertEquals(OWNER, status.owner()); assertEquals("run", status.runId()); assertEquals("research", status.scopeId());
        assertEquals(MemoryPreferenceIntentStateEnum.READY, status.intent().state()); assertEquals("withdrawn", status.intent().bindingId());
        var order = inOrder(access, ledger, store); order.verify(access).require(TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_STATUS_API); order.verify(access).require(TianjiAgentPermissions.GET_API);
        order.verify(ledger).get(ACTOR, new GetAgentRunQuery("run")); order.verify(store).view(ACTOR, "run", "research");
        order.verify(access).require(TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_STATUS_API); order.verify(access).require(TianjiAgentPermissions.GET_API);
        verify(ledger, never()).executionMaterial(any(), any());
    }

    @Test void missing_intent_is_not_requested_but_cannot_be_a_successful_cancellation() {
        when(store.view(ACTOR, "run", "research")).thenReturn(Optional.empty()); assertNull(service.status(context, query).intent());
        assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, assertThrows(AgentLedgerException.class, () -> service.cancel(context, command())).code());
        verify(store, never()).cancel(any(), any(), anyLong(), anyString(), anyString(), any());
    }

    @Test void cancellation_uses_the_exact_owned_row_original_body_hash_revision_and_current_identity_deadline() {
        var command = command(); when(store.cancel(ACTOR, ID, 3, command.commandId(), command.requestSha256(), context.deadlineAt())).thenReturn(view(MemoryPreferenceIntent.State.CANCELLED, 4));
        var result = service.cancel(context, command); assertEquals(command.commandId(), result.commandId()); assertEquals(MemoryPreferenceIntentStateEnum.CANCELLED, result.status().intent().state());
        var order = inOrder(access, ledger, store); order.verify(access).require(TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_CANCEL_API); order.verify(access).require(TianjiAgentPermissions.GET_API);
        order.verify(ledger).get(ACTOR, new GetAgentRunQuery("run")); order.verify(store).view(ACTOR, "run", "research");
        order.verify(access).require(TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_CANCEL_API); order.verify(access).require(TianjiAgentPermissions.GET_API);
        order.verify(store).cancel(ACTOR, ID, 3, command.commandId(), command.requestSha256(), context.deadlineAt());
        verify(ledger, never()).executionMaterial(any(), any());
    }

    @Test void denied_unavailable_or_foreign_current_identity_never_reads_the_intent_and_revocation_after_read_returns_no_status() {
        doThrow(new AccessDeniedException("fixture denied")).when(access).require(anyString());
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> service.status(context, query)).code());
        doThrow(new IllegalStateException("fixture private detail")).when(access).require(anyString());
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> service.cancel(context, command())).code());
        var other = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "other", ISSUER.toASCIIString()); doReturn(new TianjiAgentAccess.Authorized(other, NOW.plusSeconds(60), ISSUER)).when(access).require(anyString());
        assertThrows(AgentLedgerException.class, () -> service.status(context, query)); verify(ledger, never()).get(any(), any()); verify(store, never()).view(any(), anyString(), anyString());
        doReturn(new TianjiAgentAccess.Authorized(ACTOR, NOW.plusSeconds(60), ISSUER)).when(access).require(anyString());
        when(access.require(TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_STATUS_API)).thenReturn(new TianjiAgentAccess.Authorized(ACTOR, NOW.plusSeconds(60), ISSUER)).thenThrow(new AccessDeniedException("fixture revoked"));
        assertThrows(AgentLedgerException.class, () -> service.status(context, query)); verify(store).view(ACTOR, "run", "research");
    }

    @Test void foreign_owner_expired_context_inactive_origin_and_ambient_transactions_cannot_reach_source_or_intent_data() {
        assertThrows(AgentLedgerException.class, () -> service.status(context, new GetMemoryPreferenceIntentQuery(new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "other"), "research", "run")));
        assertThrows(AgentLedgerException.class, () -> service.status(new MemoryOperationContext(ACTOR, NOW, null), query));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(AgentLedgerException.class, () -> service.status(context, query)); } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        when(issuers.findByIssuer(anyString())).thenReturn(Optional.empty()); assertThrows(AgentLedgerException.class, () -> service.status(context, query));
        verifyNoInteractions(access); verify(ledger, never()).get(any(), any()); verify(store, never()).view(any(), anyString(), anyString());
    }

    private static CancelMemoryPreferenceIntentCommand command() { return new CancelMemoryPreferenceIntentCommand("cancel-1", OWNER, "research", "run", 3); }
    private static MemoryPreferenceIntent.View view(MemoryPreferenceIntent.State state, long revision) { return new MemoryPreferenceIntent.View(ID, "run", "research", "withdrawn", "withdrawn@1#" + "a".repeat(64), state, revision, 0, null, 1,
            state == MemoryPreferenceIntent.State.CANCELLED ? "USER_CANCELLED" : null, NOW.minusSeconds(1), NOW, state.terminal() ? NOW : null); }
}
