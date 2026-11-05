package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunPageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO.DispatchState;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO.InboxState;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceCorrelationPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceCorrelationPageDTO.ReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.TenantBindingDTO;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class S12OwnerObservationEvidenceTest {
    private static final Instant START = Instant.parse("2026-11-06T00:00:00.000001Z");
    private static final Instant CREATED = START.plusSeconds(1);
    private static final Instant OBSERVED = START.plusSeconds(4);
    private static final String ISSUER = "https://iam.example/tenant";
    private static final String PROCESS_RESOURCE = "https://process.example";
    private static final String AGENT_RESOURCE = "https://agent.example";
    private static final TenantBindingDTO TENANT = new TenantBindingDTO(13, ISSUER, "original-user",
            List.of("reviewer"), List.of("role"), "app", PROCESS_RESOURCE, AGENT_RESOURCE, "unused");
    private static final ProcessInstanceDTO INSTANCE = new ProcessInstanceDTO("own-process", "expenseReview",
            "expense-v1", "same-business", "same-correlation", ProcessInstanceStatusEnum.ACTIVE, START, null);
    private static final ProcessInstanceCorrelationPageDTO STARTS = starts(INSTANCE.correlationId(),
            new ReceiptDTO(INSTANCE.instanceId(), INSTANCE.releaseVersion(), START));
    private static final ProcessAgentOutcomePageDTO OUTCOME = outcome(INSTANCE.instanceId(), "agentStart", DispatchState.CLOSED);
    private static final AgentRunPageDTO AGENTS = agents("own-agent", CREATED, AgentRunStatusEnum.COMPLETED);

    @Test
    void accepts_single_exact_receipt_outcome_and_original_bridge_run() {
        assertDoesNotThrow(() -> verify(STARTS, OUTCOME, AGENTS));
    }

    @Test
    void rejects_foreign_owner_ids_even_when_both_tenants_share_correlation() {
        assertThrows(AssertionError.class, () -> verify(starts(INSTANCE.correlationId(),
                new ReceiptDTO("foreign-process", INSTANCE.releaseVersion(), START)), OUTCOME, AGENTS));
        assertThrows(AssertionError.class, () -> verify(STARTS, outcome("foreign-process", "agentStart", DispatchState.CLOSED), AGENTS));
        assertThrows(AssertionError.class, () -> verify(STARTS, OUTCOME, agents("foreign-agent", CREATED, AgentRunStatusEnum.COMPLETED)));
    }

    @Test
    void rejects_incomplete_duplicate_or_nonmatching_witnesses() {
        assertThrows(AssertionError.class, () -> verify(starts("other-correlation", STARTS.items().getFirst()), OUTCOME, AGENTS));
        assertThrows(AssertionError.class, () -> verify(starts(INSTANCE.correlationId(), new ReceiptDTO(INSTANCE.instanceId(),
                INSTANCE.releaseVersion(), START.plusNanos(1_000))), OUTCOME, AGENTS));
        assertThrows(AssertionError.class, () -> verify(STARTS, outcome(INSTANCE.instanceId(), "otherActivity", DispatchState.CLOSED), AGENTS));
        assertThrows(AssertionError.class, () -> verify(STARTS, outcome(INSTANCE.instanceId(), "agentStart", DispatchState.CLOSING), AGENTS));
        assertThrows(AssertionError.class, () -> verify(STARTS, OUTCOME, agents("own-agent", CREATED, AgentRunStatusEnum.RUNNING)));
        assertThrows(AssertionError.class, () -> verify(STARTS, OUTCOME, agents("own-agent", CREATED.plusSeconds(1), AgentRunStatusEnum.COMPLETED)));
        assertThrows(AssertionError.class, () -> verify(STARTS, OUTCOME, new AgentRunPageDTO(AGENTS.items(), "next")));
        assertThrows(AssertionError.class, () -> verify(STARTS, OUTCOME,
                new AgentRunPageDTO(List.of(AGENTS.items().getFirst(), AGENTS.items().getFirst()), null)));
    }

    @Test
    void preflight_requires_dedicated_read_claims_for_the_original_tenant_role_and_audience() {
        String process = claims("original-user", PROCESS_RESOURCE, "USER", "tianji:process:read", "\"13\"");
        String bridge = claims("original-bridge", AGENT_RESOURCE, "SERVICE", "tianji:agent:read", "13");
        assertDoesNotThrow(() -> processToken(process));
        assertDoesNotThrow(() -> S12OwnerObservationEvidence.requireReadToken(
                token(bridge), TENANT, "original-bridge", AGENT_RESOURCE, "SERVICE", "tianji:agent:read"));
        for (var invalid : List.of(bridge, process.replace("original-user", "different-user"),
                process.replace(PROCESS_RESOURCE, AGENT_RESOURCE), process.replace("\"13\"", "\"14\""),
                process.replace("USER", "SERVICE"), process.replace("TENANT", "PLATFORM"),
                process.replace("tianji:process:read", "tianji:process:read tianji:process:start"),
                process.replace("\"principal_type\"", "\"sub\":\"original-user\",\"principal_type\""),
                process + "{}")) {
            var error = assertThrows(AssertionError.class, () -> processToken(invalid));
            assertEquals("Dedicated S12 owner READ credential binding is invalid", error.getMessage());
            assertNull(error.getCause());
        }
    }

    private static void verify(ProcessInstanceCorrelationPageDTO starts, ProcessAgentOutcomePageDTO outcomes, AgentRunPageDTO agents) {
        S12OwnerObservationEvidence.verify(INSTANCE, "own-agent", CREATED, "expenseAnalysis", starts, outcomes, agents);
    }

    private static ProcessInstanceCorrelationPageDTO starts(String correlation, ReceiptDTO receipt) {
        return new ProcessInstanceCorrelationPageDTO(correlation, OBSERVED, List.of(receipt), null);
    }

    private static ProcessAgentOutcomePageDTO outcome(String instance, String activity, DispatchState dispatch) {
        var item = new ProcessAgentOutcomePageDTO.ItemDTO(activity, dispatch, InboxState.APPLIED, AgentRunStatusEnum.COMPLETED,
                START, CREATED, CREATED.plusSeconds(1));
        return new ProcessAgentOutcomePageDTO(instance, OBSERVED, List.of(item), null);
    }

    private static AgentRunPageDTO agents(String runId, Instant createdAt, AgentRunStatusEnum status) {
        var item = new AgentRunDTO(runId, "expenseAnalysis", 1, null, status, null, false, 1, 2, 3,
                OBSERVED, createdAt, status == AgentRunStatusEnum.COMPLETED ? OBSERVED : null);
        return new AgentRunPageDTO(List.of(item), null);
    }

    private static void processToken(String claims) {
        S12OwnerObservationEvidence.requireReadToken(token(claims), TENANT, "original-user", PROCESS_RESOURCE, "USER",
                "tianji:process:read");
    }

    private static String token(String claims) {
        return "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }

    private static String claims(String subject, String audience, String type, String scope, String tenant) {
        return """
                {"iss":"%s","sub":"%s","aud":["%s"],"principal_type":"%s","scope_context":"TENANT","scope":"%s","tenant_id":%s}
                """.formatted(ISSUER, subject, audience, type, scope, tenant);
    }
}
