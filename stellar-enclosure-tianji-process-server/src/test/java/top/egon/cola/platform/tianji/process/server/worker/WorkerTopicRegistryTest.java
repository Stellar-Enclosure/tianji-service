package top.egon.cola.platform.tianji.process.server.worker;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkerTopicRegistryTest {
    @Test
    void internal_agent_topics_cannot_share_a_public_registration_or_an_unrelated_result_contract() {
        var internal = new WorkerTopicRegistry.Registration(20, "https://idp.example/20", "worker-a", "agentStart", "1",
                Map.of("amount", ValueType.INTEGER), Map.of("runId", ValueType.STRING),
                Map.of("amount", Set.of("amount")), Map.of("runId", Set.of("agentRunId")), 2,
                Duration.ofSeconds(30), Duration.ofSeconds(5), WorkerEffect.EXTERNAL_IDEMPOTENT, true);
        assertTrue(internal.internalAgentDispatch());
        var external = new WorkerTopicRegistry.Registration(20, "https://idp.example/20", "worker-b", "agentStart", "1",
                internal.inputs(), internal.outputs(), internal.inputSources(), internal.outputTargets(), 2,
                internal.leaseDuration(), internal.retryDelay(), internal.effectSafety());
        assertFalse(external.internalAgentDispatch());
        assertThrows(IllegalArgumentException.class, () -> new WorkerTopicRegistry(List.of(internal, external)));
        assertThrows(IllegalArgumentException.class, () -> new WorkerTopicRegistry.Registration(20, "https://idp.example/20", "worker-a", "agentStart", "1",
                internal.inputs(), Map.of("approved", ValueType.BOOLEAN), internal.inputSources(), Map.of("approved", Set.of("approved")),
                2, internal.leaseDuration(), internal.retryDelay(), WorkerEffect.EXTERNAL_IDEMPOTENT, true));
        assertThrows(IllegalArgumentException.class, () -> new WorkerTopicRegistry.Registration(20, "https://idp.example/20", "worker-a", "agentStart", "1",
                internal.inputs(), internal.outputs(), internal.inputSources(), internal.outputTargets(), 2,
                internal.leaseDuration(), internal.retryDelay(), WorkerEffect.READ_ONLY, true));
    }

    @Test
    void operator_grants_are_exactly_scoped_and_models_must_match_the_registered_schema() throws Exception {
        var first = registration("https://idp.example/20", "worker-a", "1", Map.of("requestedAmount", ValueType.INTEGER));
        var second = registration("https://idp.example/20", "worker-b", "1", Map.of("requestedAmount", ValueType.INTEGER));
        var next = registration("https://idp.example/20", "worker-a", "2", Map.of("requestedAmount", ValueType.STRING));
        var registry = new WorkerTopicRegistry(List.of(first, second, next));
        var actor = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker-a", "https://idp.example/20");
        assertEquals(first, registry.require(actor, "riskCheck", "1"));
        assertEquals(next, registry.require(actor, "riskCheck", "2"));
        assertNotEquals(ProcessModelValidator.nativeWorkerTopic("riskCheck", "1"), ProcessModelValidator.nativeWorkerTopic("riskCheck", "2"));
        assertThrows(ProcessResourceNotFoundException.class, () -> registry.require(
                ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker-a", "https://other.example/20"), "riskCheck", "1"));
        assertThrows(ProcessResourceNotFoundException.class, () -> registry.require(
                ProcessActor.verified(21, ProcessActor.SubjectType.SERVICE, "worker-a", "https://idp.example/20"), "riskCheck", "1"));
        assertThrows(ProcessResourceNotFoundException.class, () -> registry.require(
                ProcessActor.verified(20, ProcessActor.SubjectType.USER, "worker-a", "https://idp.example/20"), "riskCheck", "1"));
        assertThrows(ProcessResourceNotFoundException.class, () -> registry.require(new ProcessActor(20, ProcessActor.SubjectType.SERVICE, "worker-a"), "riskCheck", "1"));
        assertThrows(ProcessResourceNotFoundException.class, () -> registry.require(actor, "otherTopic", "1"));
        try (var input = getClass().getResourceAsStream("/fixtures/worker-mapping.bpmn20.xml")) {
            assertNotNull(input);
            byte[] xml = input.readAllBytes();
            var model = new ProcessModelValidator().validateBpmn(xml, "workerMapping");
            assertTrue(registry.acceptsModel(20, model.workerTasks().get("worker")));
            assertFalse(registry.acceptsModel(21, model.workerTasks().get("worker")));
            String altered = new String(xml, java.nio.charset.StandardCharsets.UTF_8).replace("source=\"amount\"", "source=\"privateAmount\"");
            var unauthorized = new ProcessModelValidator().validateBpmn(altered.getBytes(java.nio.charset.StandardCharsets.UTF_8), "workerMapping");
            assertFalse(registry.acceptsModel(20, unauthorized.workerTasks().get("worker")));
            String unsafeEffect = new String(xml, java.nio.charset.StandardCharsets.UTF_8).replace("TRANSACTIONAL_RECEIPT", "READ_ONLY");
            var mismatched = new ProcessModelValidator().validateBpmn(unsafeEffect.getBytes(java.nio.charset.StandardCharsets.UTF_8), "workerMapping");
            assertFalse(registry.acceptsModel(20, mismatched.workerTasks().get("worker")));
        }
        assertThrows(IllegalArgumentException.class, () -> new WorkerTopicRegistry(List.of(first, first)));
        assertThrows(IllegalArgumentException.class, () -> new WorkerTopicRegistry(List.of(first,
                registration("https://idp.example/20", "worker-b", "1", Map.of("requestedAmount", ValueType.STRING)))));
        var differentSafety = new WorkerTopicRegistry.Registration(20, "https://idp.example/20", "worker-c", "riskCheck", "1",
                Map.of("requestedAmount", ValueType.INTEGER), Map.of("approved", ValueType.BOOLEAN),
                Map.of("requestedAmount", Set.of("amount")), Map.of("approved", Set.of("approved")), 2,
                Duration.ofSeconds(30), Duration.ofSeconds(5), WorkerEffect.READ_ONLY);
        assertThrows(IllegalArgumentException.class, () -> new WorkerTopicRegistry(List.of(first, differentSafety)));
        var differentSource = new WorkerTopicRegistry.Registration(20, "https://idp.example/20", "worker-c", "riskCheck", "1",
                Map.of("requestedAmount", ValueType.INTEGER), Map.of("approved", ValueType.BOOLEAN),
                Map.of("requestedAmount", Set.of("privateAmount")), Map.of("approved", Set.of("approved")), 2,
                Duration.ofSeconds(30), Duration.ofSeconds(5), WorkerEffect.TRANSACTIONAL_RECEIPT);
        assertThrows(IllegalArgumentException.class, () -> new WorkerTopicRegistry(List.of(first, differentSource)));
        assertThrows(IllegalArgumentException.class, () -> ProcessModelValidator.nativeWorkerTopic("risk:other", "1"));
    }

    private static WorkerTopicRegistry.Registration registration(String issuer, String subject, String version, Map<String, ValueType> inputs) {
        return new WorkerTopicRegistry.Registration(20, issuer, subject, "riskCheck", version, inputs,
                Map.of("approved", ValueType.BOOLEAN), Map.of("requestedAmount", Set.of("amount")),
                Map.of("approved", Set.of("approved")), 2, Duration.ofSeconds(30), Duration.ofSeconds(5),
                WorkerEffect.TRANSACTIONAL_RECEIPT);
    }
}
