package top.egon.cola.platform.tianji.process.server.bridge;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentDispatchStoreTest {
    static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    static final ProcessActor ACTOR = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "process-worker", "https://idp.example/20");

    static AgentDispatchBinding binding() {
        return new AgentDispatchBinding(ACTOR, "process-east", "agent-start", "1", "research", 2,
                "a".repeat(64), "Summarize the supplied task data", Duration.ofSeconds(60));
    }
    static AgentDispatchStore.Seed seed(String occurrence) {
        return new AgentDispatchStore.Seed(binding(), "instance-one", occurrence, "release-one", "process:1:id", "b".repeat(64),
                "agentTask", "c".repeat(64), "order-123", "correlation-one", Map.of("question", "private task", "amount", 7L));
    }
    static AgentDispatchStore.Intent intent(String occurrence, AgentDispatchStore.State state, String runId) {
        return new AgentDispatchStore.Intent(seed(occurrence), NOW, NOW.plusSeconds(60), state, runId,
                state == AgentDispatchStore.State.CLOSED ? NOW.plusSeconds(90) : null);
    }

    @Test
    void occurrence_identity_is_stable_while_the_full_payload_and_routing_are_bound() {
        var original = seed("job-one");
        var reordered = new HashMap<String, Object>();
        reordered.put("amount", 7L);
        reordered.put("question", "private task");
        var copy = copy(original, original.binding(), reordered);
        assertEquals(original.fingerprint(), copy.fingerprint());
        assertEquals(original.idempotencyKey(), copy.idempotencyKey());
        assertNotEquals(original.idempotencyKey(), seed("job-two").idempotencyKey());
        var changedInput = copy(original, original.binding(), Map.of("question", "different", "amount", 7L));
        assertEquals(original.idempotencyKey(), changedInput.idempotencyKey());
        assertNotEquals(original.fingerprint(), changedInput.fingerprint());
        var binding = original.binding();
        var changedDefinition = new AgentDispatchBinding(ACTOR, binding.sourceId(), binding.topic(), binding.schemaVersion(),
                binding.definitionKey(), 3, "d".repeat(64), binding.instruction(), binding.submissionTimeout());
        assertNotEquals(original.fingerprint(), copy(original, changedDefinition, original.inputs()).fingerprint());
        assertThrows(UnsupportedOperationException.class, () -> original.inputs().put("new", "value"));
        assertFalse(original.toString().contains("private task"));
    }

    @Test
    void reconstructed_command_keeps_the_same_deadline_definition_input_and_occurrence() {
        var intent = intent("job-one", AgentDispatchStore.State.PENDING, null);
        assertEquals(intent.query().link(), intent.command().link());
        assertEquals(NOW.plusSeconds(60), intent.command().link().dispatchNotAfter());
        assertEquals("job-one", intent.command().link().activityOccurrenceId());
        assertEquals("a".repeat(64), intent.command().link().definitionContentSha256());
        assertEquals(intent.seed().inputs(), intent.command().start().input().taskData());
        assertNull(intent.command().start().sessionId());
        assertNull(intent.command().start().policyOverride());
        assertFalse(intent.toString().contains("private task"));
    }

    @Test
    void unverified_identity_invalid_deadline_and_nonprimitive_values_are_rejected() {
        var original = seed("job-one");
        assertThrows(IllegalArgumentException.class, () -> copy(original, binding(), Map.of("amount", 7)));
        assertThrows(IllegalArgumentException.class, () -> copy(original, binding(), Map.of("amount", Map.of("nested", true))));
        assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.Key(
                new ProcessActor(20, ProcessActor.SubjectType.SERVICE, "worker"), "source", "instance", "occurrence"));
        assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.Key(
                ProcessActor.verified(20, ProcessActor.SubjectType.USER, "worker", "https://idp.example/20"), "source", "instance", "occurrence"));
        assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.Intent(original, NOW, NOW.plusSeconds(61),
                AgentDispatchStore.State.PENDING, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.Intent(original, NOW, NOW.plusSeconds(60),
                AgentDispatchStore.State.SUBMITTED, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentDispatchStore.Intent(original, NOW, NOW.plusSeconds(60),
                AgentDispatchStore.State.PENDING, "run", null));
    }

    private static AgentDispatchStore.Seed copy(AgentDispatchStore.Seed seed, AgentDispatchBinding binding, Map<String, Object> input) {
        return new AgentDispatchStore.Seed(binding, seed.instanceId(), seed.occurrenceId(), seed.releaseKey(), seed.processDefinitionId(),
                seed.processModelSha256(), seed.elementId(), seed.workerInputSha256(), seed.businessKey(), seed.correlationId(), input);
    }
}
