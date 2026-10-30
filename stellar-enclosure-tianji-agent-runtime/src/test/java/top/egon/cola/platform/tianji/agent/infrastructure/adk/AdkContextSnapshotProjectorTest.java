package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.adk.agents.CallbackContext;
import com.google.adk.events.Event;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentContextMetadataDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdkContextSnapshotProjectorTest {
    @Test
    void prepared_metadata_is_owner_call_and_epoch_bound_without_bodies_or_transport_credentials() throws Exception {
        var now = Instant.parse("2026-10-30T00:00:00Z");
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0, 1000, 100,
                BigDecimal.ONE, "USD", 1, Set.of(), "retry-v1", "approval-v1");
        var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route", "policy"), policy, Map.of());
        var snapshot = new AgentExecutionSnapshot(definition, "a".repeat(64), policy, new AgentInputDTO("private-user-input", null, null), null);
        var authorization = new AgentRunAuthorization.Request(actor, "run-one", "assistant", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy", false, now.plusSeconds(30));
        var projector = new AdkContextSnapshotProjector(snapshot, authorization, new AgentRunLease("run-one", "worker", 7, now.plusSeconds(20)), new DefaultContextAssembler());
        var context = mock(CallbackContext.class); var event = mock(Event.class);
        when(event.id()).thenReturn("old-event"); when(context.events()).thenReturn(List.of(event));
        when(context.eventId()).thenReturn("call-one"); when(context.agentName()).thenReturn("writer");
        when(context.invocationId()).thenReturn("invocation"); when(context.sessionId()).thenReturn("session");
        var original = request("first-secret");
        var prepared = projector.prepare(context, original);
        var window = new AgentModelBudgetEstimator.ContextWindow("model", "route", "policy", 100, 5);
        var estimate = new AgentModelBudgetEstimator.Estimate(20, 10, BigDecimal.ONE);
        var metadata = projector.capture(prepared, original, window, estimate, 10, List.of(), "d".repeat(64));
        var otherHeaders = projector.capture(prepared, request("second-secret"), window, estimate, 10, List.of(), "d".repeat(64));
        assertEquals(metadata.nativeRequestSha256(), otherHeaders.nativeRequestSha256());
        assertEquals(actor.identityFingerprint(), metadata.ownerSha256());
        assertEquals("run-one", metadata.runId()); assertEquals("call-one", metadata.reservationId()); assertEquals(7, metadata.leaseEpoch());
        assertEquals(1, metadata.availableHistoryEventCount()); assertEquals("old-event", metadata.lastHistoryEventId());
        assertEquals(AgentContextMetadataDTO.PROJECTION_V1, metadata.projectionVersion());
        String stored = JsonMapper.builder().addModule(new JavaTimeModule()).build().writeValueAsString(metadata);
        assertFalse(stored.contains("private-user-input")); assertFalse(stored.contains("private-system"));
        assertFalse(stored.contains("first-secret")); assertFalse(stored.contains("Authorization"));
        assertFalse(prepared.toString().contains("private-user-input"));
    }

    private static LlmRequest request(String secret) {
        return LlmRequest.builder().model("model").contents(List.of(Content.builder().role("user").parts(List.of(Part.fromText("private-user-input"))).build()))
                .config(GenerateContentConfig.builder().systemInstruction(Content.fromParts(Part.fromText("private-system")))
                        .maxOutputTokens(10).httpOptions(HttpOptions.builder().headers(Map.of("Authorization", secret)).build()).build()).build();
    }
}
