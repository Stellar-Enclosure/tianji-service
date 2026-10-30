package top.egon.cola.platform.tianji.agent.domain.memory;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MemoryReadGrantTest {
    private static final URI RESOURCE = URI.create("https://agent.example");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");

    @Test
    void memory_permission_evidence_has_an_independent_purpose_and_exact_owner_scope_binding() {
        var run = run(ACTOR); var scope = scope("research", "c".repeat(64));
        assertNotEquals(AgentExecutionGrant.fingerprint(run, RESOURCE), MemoryReadGrant.fingerprint(run, RESOURCE, scope));
        assertNotEquals(MemoryReadGrant.fingerprint(run, RESOURCE, scope), MemoryReadGrant.fingerprint(run, RESOURCE, scope("other", "c".repeat(64))));
        assertNotEquals(MemoryReadGrant.intentId(run, scope), MemoryReadGrant.intentId(run, scope("research", "d".repeat(64))));
        assertNotEquals(MemoryReadGrant.fingerprint(run, RESOURCE, scope), MemoryReadGrant.fingerprint(run, URI.create("https://another-agent.example"), scope));
        assertEquals(64, MemoryReadGrant.fingerprint(run, RESOURCE, scope).length());
    }

    @Test
    void reservations_cannot_attach_a_different_intent_or_digest_and_service_sources_need_their_own_subject_binding() {
        var run = run(ACTOR); var scope = scope("research", "c".repeat(64));
        assertDoesNotThrow(() -> new MemoryReadGrant.Reservation(run, RESOURCE, scope, 1, MemoryReadGrant.intentId(run, scope), MemoryReadGrant.fingerprint(run, RESOURCE, scope)));
        assertThrows(IllegalArgumentException.class, () -> new MemoryReadGrant.Reservation(run, RESOURCE, scope, 1, UUID.randomUUID(), MemoryReadGrant.fingerprint(run, RESOURCE, scope)));
        assertThrows(IllegalArgumentException.class, () -> new MemoryReadGrant.Reservation(run, RESOURCE, scope, 1, MemoryReadGrant.intentId(run, scope), "e".repeat(64)));
        var service = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "memory-client", "https://idp.example/20");
        assertThrows(IllegalArgumentException.class, () -> MemoryReadGrant.fingerprint(run(service), RESOURCE, scope));
        assertFalse(scope.toString().contains("alice"));
    }

    private static MemoryReadGrant.Scope scope(String scope, String fingerprint) { return new MemoryReadGrant.Scope(new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"), scope, fingerprint, null); }
    private static AgentRunAuthorization.Request run(TrustedAgentActor actor) {
        return new AgentRunAuthorization.Request(actor, "00000000-0000-0000-0000-000000000001", "assistant", 2, "a".repeat(64), "b".repeat(64), "c".repeat(64),
                "policy-v1", false, Instant.parse("2026-10-30T00:01:00Z"));
    }
}
