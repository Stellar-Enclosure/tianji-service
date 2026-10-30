package top.egon.cola.platform.tianji.process.server.bridge;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStoreTest.*;

class AgentResultProducerRegistryTest {
    static final ProcessActor PRODUCER = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "agent-producer", "https://idp.example/20");

    @Test
    void authenticated_producer_tenant_source_and_original_owner_must_all_match_an_operator_grant() {
        var registration = new AgentResultProducerRegistry.Registration(PRODUCER, "agent-east", "process-east", ACTOR);
        var registry = new AgentResultProducerRegistry(List.of(registration));
        var event = event(intent("job-one", AgentDispatchStore.State.PENDING, null), "run-one", 3, "b".repeat(64));
        assertEquals(registration, registry.require(PRODUCER, event));
        assertThrows(ProcessResourceNotFoundException.class, () -> registry.require(
                ProcessActor.verified(21, ProcessActor.SubjectType.SERVICE, "agent-producer", "https://idp.example/21"), event));
        assertThrows(ProcessResourceNotFoundException.class, () -> registry.require(
                ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "agent-producer", "https://foreign.example/20"), event));
        assertThrows(ProcessResourceNotFoundException.class, () -> registry.require(
                ProcessActor.verified(20, ProcessActor.SubjectType.USER, "agent-producer", "https://idp.example/20"), event));
        assertThrows(ProcessResourceNotFoundException.class, () -> registry.require(new ProcessActor(20, ProcessActor.SubjectType.SERVICE, "agent-producer"), event));
        var otherOwner = new AgentResultProducerRegistry.Registration(PRODUCER, "agent-east", "process-east",
                ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "other-worker", "https://idp.example/20"));
        assertThrows(ProcessResourceNotFoundException.class, () -> new AgentResultProducerRegistry(List.of(otherOwner)).require(PRODUCER, event));
        var otherSource = new AgentResultProducerRegistry.Registration(PRODUCER, "agent-east", "another-source", ACTOR);
        assertThrows(ProcessResourceNotFoundException.class, () -> new AgentResultProducerRegistry(List.of(otherSource)).require(PRODUCER, event));
        assertThrows(IllegalArgumentException.class, () -> new AgentResultProducerRegistry(List.of(registration, registration)));
    }

    @Test
    void receipts_bind_the_accepted_event_and_preserve_the_received_vs_applied_distinction() {
        var event = event(intent("job-one", AgentDispatchStore.State.PENDING, null), "run-one", 3, "b".repeat(64));
        var receipt = new AgentResultReceipt(event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, NOW);
        assertNotEquals(AgentResultReceiptStateEnum.APPLIED, receipt.state());
        assertThrows(IllegalArgumentException.class, () -> new AgentResultReceipt("wrong", event.fingerprint(), receipt.state(), NOW));
        assertThrows(IllegalArgumentException.class, () -> new AgentResultReceipt(event.eventId(), event.fingerprint(), receipt.state(), NOW.plusNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> new AgentResultProducerRegistry.Registration(PRODUCER, "agent-east", "process-east",
                new ProcessActor(20, ProcessActor.SubjectType.SERVICE, "legacy")));
    }

    static AgentTerminalEvent event(AgentDispatchStore.Intent intent, String runId, long revision, String resultHash) {
        var actor = intent.seed().binding().actor();
        return new AgentTerminalEvent(1, "agent-east", AgentTerminalEvent.eventIdFor("agent-east", actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), runId, revision),
                actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), intent.query().idempotencyKey(), intent.query().link(),
                intent.seed().binding().definitionKey(), intent.seed().binding().definitionVersion(), runId, revision, AgentRunStatusEnum.COMPLETED,
                NOW.plusSeconds(120), resultHash, null, "UNKNOWN");
    }
}
