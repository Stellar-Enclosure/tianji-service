package top.egon.cola.platform.tianji.agent.domain.memory;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;

import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MemoryPreferenceIntentTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final String HASH = "a".repeat(64), RUN_ID = "10000000-0000-0000-0000-000000000001";
    private static final URI RESOURCE = URI.create("https://agent.example/api");

    @Test void stable_source_identity_is_distinct_from_mutable_policy_and_each_complete_write_binding() {
        var run = run(ACTOR); var first = binding("source", 1); var next = binding("new-source", 2);
        assertEquals(MemoryPreferenceIntent.intentId(run, first.scopeId()), MemoryPreferenceIntent.intentId(run, next.scopeId()));
        var original = MemoryPreferenceIntent.fingerprint(run, first, RESOURCE, NOW.plusSeconds(120));
        assertNotEquals(original, MemoryPreferenceIntent.fingerprint(run, next, RESOURCE, NOW.plusSeconds(120)));
        assertNotEquals(original, MemoryPreferenceIntent.fingerprint(run, first, RESOURCE, NOW.plusSeconds(121)));
        assertNotEquals(original, MemoryPreferenceIntent.fingerprint(run, first, URI.create("https://AGENT.example/api"), NOW.plusSeconds(120)));
        assertNotEquals(AgentRunAuthorization.PURPOSE, MemoryPreferenceIntent.PURPOSE); assertNotEquals(MemoryReadGrant.PURPOSE, MemoryPreferenceIntent.PURPOSE);
        var reservation = reservation(); assertEquals(original, reservation.bindingSha256());
        assertThrows(IllegalArgumentException.class, () -> MemoryPreferenceIntent.fingerprint(run, first, URI.create("http://agent.example/api"), NOW.plusSeconds(120)));
        assertThrows(IllegalArgumentException.class, () -> MemoryPreferenceIntent.fingerprint(run, first, RESOURCE, NOW.plusSeconds(120).plusNanos(1)));
        var machine = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "alice", "https://idp.example/20");
        assertThrows(IllegalArgumentException.class, () -> MemoryPreferenceIntent.intentId(run(machine), "research"));
    }

    @Test void ticket_deadlines_and_claim_progress_cannot_be_extended_or_made_ambiguous() {
        var ticket = new AgentExecutionGrant.Ticket("9" + "1".repeat(62), "ENC:v1:test:A:A", NOW.plusSeconds(90));
        var evidence = new MemoryPreferenceIntent.Evidence(reservation(), ticket);
        var lease = new MemoryPreferenceIntent.Lease(evidence, "worker", 1, NOW.plusSeconds(30), "event", 0, null, null, 1);
        assertEquals(0, lease.nextIndex()); assertFalse(lease.toString().contains(ticket.ciphertext())); assertFalse(evidence.toString().contains(ticket.grantId()));
        assertThrows(IllegalArgumentException.class, () -> new MemoryPreferenceIntent.Evidence(reservation(), new AgentExecutionGrant.Ticket(ticket.grantId(), ticket.ciphertext(), NOW.plusSeconds(121))));
        assertThrows(IllegalArgumentException.class, () -> new MemoryPreferenceIntent.Lease(evidence, "worker", 1, NOW.plusSeconds(91), "event", 0, null, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new MemoryPreferenceIntent.Lease(evidence, "worker", 1, NOW.plusSeconds(30), "event", 1, null, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new MemoryPreferenceIntent.Lease(evidence, "worker", 1, NOW.plusSeconds(30), "event", 3, 2, HASH, 1));
        assertDoesNotThrow(() -> new MemoryPreferenceIntent.Lease(evidence, "worker", 2, NOW.plusSeconds(30), "event", 1, 2, HASH, 2));
        assertTrue(MemoryPreferenceIntent.State.CANCELLED.terminal()); assertFalse(MemoryPreferenceIntent.State.READY.terminal()); assertTrue(MemoryPreferenceIntent.State.CLAIMED.sealed());
    }

    private static MemoryPreferenceIntent.Reservation reservation() {
        var run = run(ACTOR); var binding = binding("source", 1); var deadline = NOW.plusSeconds(120);
        return new MemoryPreferenceIntent.Reservation(run, binding, RESOURCE, deadline, 1, MemoryPreferenceIntent.intentId(run, "research"), MemoryPreferenceIntent.fingerprint(run, binding, RESOURCE, deadline));
    }
    private static AgentRunAuthorization.Request run(TrustedAgentActor actor) { return new AgentRunAuthorization.Request(actor, RUN_ID, "assistant", 1, HASH, HASH, HASH, "execution-policy", true, NOW.plusSeconds(60)); }
    private static MemoryPreferenceSourceBinding binding(String id, long version) { return new MemoryPreferenceSourceBinding(id, 20, ACTOR.issuerFingerprint(), "assistant", 1, HASH, "research", "preferences@" + version + '#' + "b".repeat(64)); }
}
