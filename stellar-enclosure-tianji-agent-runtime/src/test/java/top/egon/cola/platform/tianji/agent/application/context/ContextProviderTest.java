package top.egon.cola.platform.tianji.agent.application.context;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContextProviderTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final String HASH = "d".repeat(64);

    @Test
    void fixed_dependencies_resolve_in_host_order_and_current_access_is_rechecked_without_refetching() {
        var provider = mock(ContextProvider.class);
        var request = request(List.of(new DefinitionResourceRef("fixed", 2, HASH)));
        var fragment = fragment(ContextProvider.Kind.PROMPT, "fixed", "2", HASH);
        when(provider.provide(any())).thenReturn(List.of(fragment));
        var sources = new DefaultContextAssembler(List.of(provider)).resolve(request);
        assertEquals(List.of(fragment), sources.fragments());
        verify(provider).revalidate(argThat(value -> value.authorization().equals(request.authorization())
                && value.leaseEpoch() == request.leaseEpoch() && value.timeout().compareTo(request.timeout()) <= 0), eq(List.of(fragment)));
        doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "revoked")).when(provider).revalidate(any(), anyList());
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, sources::revalidate).code());
        verify(provider, times(1)).provide(any());
        assertFalse(sources.toString().contains("private-source-text"));
        assertFalse(fragment.toString().contains("private-source-text"));
        assertFalse(request.toString().contains("private-input"));
    }

    @Test
    void missing_extra_duplicate_or_changed_published_dependencies_fail_closed() {
        var provider = mock(ContextProvider.class);
        var request = request(List.of(new DefinitionResourceRef("fixed", 2, HASH)));
        for (var supplied : List.of(List.<ContextProvider.Fragment>of(),
                List.of(fragment(ContextProvider.Kind.PROMPT, "fixed", "3", HASH)),
                List.of(fragment(ContextProvider.Kind.SKILL, "fixed", "2", HASH)),
                List.of(fragment(ContextProvider.Kind.PROMPT, "fixed", "2", "e".repeat(64))),
                List.of(fragment(ContextProvider.Kind.PROMPT, "fixed", "2", HASH), fragment(ContextProvider.Kind.PROMPT, "extra", "2", HASH)),
                List.of(fragment(ContextProvider.Kind.PROMPT, "fixed", "2", HASH), fragment(ContextProvider.Kind.PROMPT, "fixed", "2", HASH)))) {
            when(provider.provide(any())).thenReturn(supplied);
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    assertThrows(AgentLedgerException.class, () -> new DefaultContextAssembler(List.of(provider)).resolve(request)).code());
        }
        verify(provider, never()).revalidate(any(), anyList());
    }

    @Test
    void source_identity_fields_cannot_alias_through_delimiters_and_evidence_remains_a_separate_kind() {
        var provider = mock(ContextProvider.class);
        when(provider.provide(any())).thenReturn(List.of(fragment(ContextProvider.Kind.PROMPT, "fixed", "1:2", HASH)));
        assertThrows(AgentLedgerException.class, () -> new DefaultContextAssembler(List.of(provider))
                .resolve(request(List.of(new DefinitionResourceRef("fixed:1", 2, HASH)))));
        var first = fragment(ContextProvider.Kind.RAG, "document:1", "2", HASH);
        var second = fragment(ContextProvider.Kind.RAG, "document", "1:2", HASH);
        when(provider.provide(any())).thenReturn(List.of(first, second));
        assertEquals(List.of(first, second), new DefaultContextAssembler(List.of(provider)).resolve(request(List.of())).fragments());
    }

    @Test
    void network_source_access_rejects_ambient_transactions_and_sanitizes_unknown_provider_errors() {
        var provider = mock(ContextProvider.class);
        var assembler = new DefaultContextAssembler(List.of(provider));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> assembler.resolve(request(List.of()))); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(provider);
        when(provider.provide(any())).thenThrow(new IllegalArgumentException("private-upstream-message"));
        var failure = assertThrows(AgentLedgerException.class, () -> assembler.resolve(request(List.of())));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code());
        assertFalse(failure.toString().contains("private-upstream-message"));
        assertNull(failure.getCause());
        assertThrows(AgentLedgerException.class, () -> new DefaultContextAssembler().resolve(request(List.of(new DefinitionResourceRef("fixed", 2, HASH)))));
    }

    private static ContextProvider.Fragment fragment(ContextProvider.Kind kind, String id, String version, String hash) {
        return new ContextProvider.Fragment(kind, id, version, hash, "authorized-source-reference", "private-source-text", NOW);
    }
    private static ContextProvider.Request request(List<DefinitionResourceRef> prompts) {
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0, 1000, 100,
                BigDecimal.ONE, "USD", 1, Set.of(), "retry-v1", "approval-v1");
        var definition = new AgentDefinitionSpecDTO("assistant", 2, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(prompts, List.of(), List.of(), "route-v1", "policy-v1"), policy, Map.of());
        var snapshot = new AgentExecutionSnapshot(definition, "a".repeat(64), policy, new AgentInputDTO("private-input", null, null), null);
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
        var authorization = new AgentRunAuthorization.Request(actor, "run-one", "assistant", 2, "a".repeat(64), "b".repeat(64), "c".repeat(64),
                "policy-v1", false, NOW.plusSeconds(60));
        return new ContextProvider.Request(authorization, snapshot, 1, Duration.ofSeconds(3));
    }
}
