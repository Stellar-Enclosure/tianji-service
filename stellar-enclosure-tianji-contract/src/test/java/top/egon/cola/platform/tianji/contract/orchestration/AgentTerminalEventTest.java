package top.egon.cola.platform.tianji.contract.orchestration;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AgentTerminalEventTest {
    private static final Instant FINISHED = Instant.parse("2026-10-30T00:01:00.123456Z");
    private static final AgentProcessLink LINK = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN,
            "instance-one", "occurrence-one", "a".repeat(64), FINISHED.minusSeconds(30));

    @Test
    void event_identity_is_stable_and_domain_separated_from_its_payload_fingerprint() {
        var first = event(AgentRunStatusEnum.COMPLETED, "b".repeat(64));
        assertEquals("84014ceaf0e858171ff847c5ad938b5ee63355d89007fe1c7388f6997cc1d81f", first.eventId());
        assertEquals("8643147cb3c996c9e40a13ce3823559c373af83ceb91bf97972fb0d0d94ab446", first.fingerprint());
        assertEquals(first, event(AgentRunStatusEnum.COMPLETED, "b".repeat(64)));
        assertEquals(first.fingerprint(), event(AgentRunStatusEnum.COMPLETED, "b".repeat(64)).fingerprint());
        var altered = event(AgentRunStatusEnum.FAILED, "c".repeat(64));
        assertEquals(first.eventId(), altered.eventId());
        assertNotEquals(first.fingerprint(), altered.fingerprint());
        assertNotEquals(first.eventId(), AgentTerminalEvent.eventIdFor("agent-east", 21, "d".repeat(64), "process-worker", "run-one", 3));
        assertNotEquals(first.eventId(), AgentTerminalEvent.eventIdFor("agent-east", 20, "e".repeat(64), "process-worker", "run-one", 3));
        assertNotEquals(first.eventId(), AgentTerminalEvent.eventIdFor("agent-east", 20, "d".repeat(64), "process-worker", "run-one", 4));
        assertFalse(first.toString().contains("process-worker"));
        assertFalse(first.toString().contains("instance-one"));
    }

    @Test
    void intermediate_states_forged_ids_and_unbounded_or_ambiguous_fields_are_rejected() {
        assertThrows(IllegalArgumentException.class, () -> event(AgentRunStatusEnum.RUNNING, "b".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> event(AgentRunStatusEnum.SUSPENDED, "b".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> event(AgentRunStatusEnum.COMPLETED, "not-a-hash"));
        var valid = event(AgentRunStatusEnum.CANCELLED, "b".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> new AgentTerminalEvent(1, "agent-east", "forged", 20, "d".repeat(64),
                "process-worker", "intent-one", LINK, "research", 2, "run-one", 3, valid.status(), FINISHED, valid.resultSha256(), null, "UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> new AgentTerminalEvent(2, "agent-east", valid.eventId(), 20, "d".repeat(64),
                "process-worker", "intent-one", LINK, "research", 2, "run-one", 3, valid.status(), FINISHED, valid.resultSha256(), null, "UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> new AgentTerminalEvent(1, "agent-east", valid.eventId(), 20, "d".repeat(64),
                "process-worker", "intent-one", LINK, "research", 2, "run-one", 3, valid.status(), FINISHED.plusNanos(1), valid.resultSha256(), null, "UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> AgentTerminalEvent.eventIdFor("agent-east", 20, "d".repeat(64), "worker\u0000", "run-one", 3));
    }

    private static AgentTerminalEvent event(AgentRunStatusEnum status, String resultHash) {
        return new AgentTerminalEvent(1, "agent-east", AgentTerminalEvent.eventIdFor("agent-east", 20, "d".repeat(64), "process-worker", "run-one", 3),
                20, "d".repeat(64), "process-worker", "intent-one", LINK, "research", 2, "run-one", 3, status, FINISHED, resultHash, null, "UNKNOWN");
    }
}
