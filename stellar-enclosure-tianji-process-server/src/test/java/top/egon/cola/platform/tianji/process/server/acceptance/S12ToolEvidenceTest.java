package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.client.AgentClientException;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.TenantBindingDTO;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class S12ToolEvidenceTest {
    private static final String RUN = "own-run";
    private static final String CORRELATION = "s07-first";
    private static final UUID INVOCATION = UUID.fromString("d390dd48-c4f4-46fb-ad7f-7aab46233086");
    private static final UUID FOREIGN_INVOCATION = UUID.fromString("c19f1649-9ba0-4e6a-8199-427d14e67ff7");
    private static final DefinitionResourceRef TOOL = new DefinitionResourceRef("s07.start.13", 1, "a".repeat(64));
    private static final Instant AT = Instant.parse("2026-11-06T00:00:00Z");
    private static final TenantBindingDTO TENANT = new TenantBindingDTO(13, "https://iam.example/tenant", "owner",
            List.of("reviewer"), List.of("role"), "app", "https://process.example", "https://agent.example", "unused");
    private static final StartProcessCommand COMMAND = new StartProcessCommand("s07GovernedStart", "s07-governed-release",
            "owned-business", "owned-command", "owned-command", Map.of("amount", 10));

    @Test
    void accepts_exact_tool_pair_at_the_owned_terminal_watermark() {
        assertDoesNotThrow(() -> verify(pair(INVOCATION, TOOL)));
    }

    @Test
    void rejects_other_invocation_under_the_same_run_and_tool_version() {
        assertThrows(AssertionError.class, () -> verify(pair(FOREIGN_INVOCATION, TOOL)));
    }

    @Test
    void rejects_wrong_version_finish_order_watermark_and_sensitive_event_fields() {
        var valid = pair(INVOCATION, TOOL);
        var foreignVersion = new DefinitionResourceRef(TOOL.key(), 2, TOOL.contentSha256());
        assertThrows(AssertionError.class, () -> verify(pair(INVOCATION, foreignVersion)));
        assertThrows(AssertionError.class, () -> verify(List.of(valid.getFirst(), valid.getLast(), valid.get(1))));
        assertThrows(AssertionError.class, () -> verify(valid.subList(0, 2)));
        assertThrows(AssertionError.class, () -> verify(List.of(valid.getFirst(), valid.get(1),
                event(3, "TOOL_FINISHED", payload(INVOCATION, TOOL, true, Map.of("prompt", "fixture"))))));
        assertThrows(AssertionError.class, () -> verify(List.of(valid.getFirst(), valid.get(1),
                event(3, "TOOL_FINISHED", payload(INVOCATION, TOOL, true, Map.of("dispatchEpoch", 4_294_967_297L))))));
    }

    @Test
    void verifies_the_receipt_digest_of_the_exact_native_start() {
        String issuerHash = "b".repeat(64);
        String receipt = "tianji:ps-start:v1:" + OrchestrationRequestDigests.hash(List.of("TIANJI_PS_START_RECEIPT_REFERENCE_V1",
                TENANT.issuer(), TENANT.processResource(), "13", "SERVICE", issuerHash, "executor", "START", COMMAND.commandId(),
                OrchestrationRequestDigests.start(COMMAND), "own-instance", COMMAND.definitionKey(), COMMAND.releaseVersion(),
                COMMAND.businessKey(), COMMAND.correlationId()), Map.of());
        assertDoesNotThrow(() -> S12ToolEvidence.verifyReceipt(TENANT, issuerHash, "executor", COMMAND, "own-instance", TOOL, receipt));
        assertThrows(AssertionError.class,
                () -> S12ToolEvidence.verifyReceipt(TENANT, issuerHash, "executor", COMMAND, "foreign-instance", TOOL, receipt));
    }

    @Test
    void consumes_only_a_bounded_completed_stream_and_preserves_safe_denial() throws Exception {
        var cancelled = new AtomicBoolean();
        var valid = pair(INVOCATION, TOOL);
        assertEquals(valid, S12ToolEvidence.read(publisher(valid, cancelled, null)));
        assertTrue(cancelled.get());
        var excessive = new ArrayList<AgentEventDTO>();
        for (int index = 0; index <= S12ToolEvidence.MAX_EVENTS; index++) excessive.add(valid.getFirst());
        cancelled.set(false);
        assertThrows(AssertionError.class, () -> S12ToolEvidence.read(publisher(excessive, cancelled, null)));
        assertTrue(cancelled.get());
        var denied = new AgentClientException(404, "TIANJI_RESOURCE_NOT_FOUND", null, null);
        assertSame(denied, assertThrows(AgentClientException.class,
                () -> S12ToolEvidence.read(publisher(List.of(), new AtomicBoolean(), denied))));
    }

    private static Flow.Publisher<AgentEventDTO> publisher(List<AgentEventDTO> events, AtomicBoolean cancelled, Throwable failure) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            @Override public void request(long demand) {
                assertEquals(S12ToolEvidence.MAX_EVENTS + 1L, demand);
                if (failure != null) subscriber.onError(failure);
                else {
                    events.forEach(event -> { if (!cancelled.get()) subscriber.onNext(event); });
                    if (!cancelled.get()) subscriber.onComplete();
                }
            }
            @Override public void cancel() { cancelled.set(true); }
        });
    }

    private static void verify(List<AgentEventDTO> events) {
        S12ToolEvidence.verify(RUN, 3, CORRELATION, INVOCATION, TOOL, events);
    }

    private static List<AgentEventDTO> pair(UUID invocation, DefinitionResourceRef tool) {
        return List.of(event(1, "RUN_QUEUED", Map.of()),
                event(2, "TOOL_STARTED", payload(invocation, tool, false, Map.of())),
                event(3, "TOOL_FINISHED", payload(invocation, tool, true, Map.of())));
    }

    private static AgentEventDTO event(long sequence, String kind, Map<String, Object> payload) {
        return new AgentEventDTO("event-" + sequence, RUN, sequence, kind, "fake", null, null,
                AT.plusSeconds(sequence), CORRELATION, "1", payload);
    }

    private static Map<String, Object> payload(UUID invocation, DefinitionResourceRef tool, boolean finished,
                                               Map<String, Object> overrides) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("toolRef", Map.of("key", tool.key(), "version", tool.version(), "contentSha256", tool.contentSha256()));
        fields.put("hostInvocationId", invocation.toString());
        fields.put("state", finished ? "SUCCEEDED" : "REQUESTED");
        fields.put("dispatchEpoch", finished ? 1 : 0);
        if (finished) fields.put("accountingState", "SETTLED");
        fields.putAll(overrides);
        return fields;
    }
}
