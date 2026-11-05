package top.egon.cola.platform.tianji.process.server.acceptance;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentRunPageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceCorrelationPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.TenantBindingDTO;

import java.time.Instant;
import java.util.Base64;

final class S12OwnerObservationEvidence {
    static final String PROCESS_TOKEN = "token.s12-process-owner-read";
    static final String AGENT_TOKEN = "token.s12-agent-bridge-read";
    private static final String TOKEN_MISMATCH = "Dedicated S12 owner READ credential binding is invalid";
    private static final JsonMapper JWT_PAYLOAD = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private S12OwnerObservationEvidence() { }

    static void requireReadToken(String compact, TenantBindingDTO tenant, String subject, String resource,
                                 String principalType, String scope) {
        // This is a preflight against accidental forwarding. The isolated server verifies the signed credential and IAM.
        try {
            var parts = compact.split("\\.", -1);
            if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
                throw new IllegalArgumentException(TOKEN_MISMATCH);
            }
            var claims = JWT_PAYLOAD.readTree(Base64.getUrlDecoder().decode(parts[1]));
            var audience = claims.path("aud");
            var tenantId = claims.path("tenant_id");
            boolean exactAudience = audience.isString() && resource.equals(audience.asString())
                    || audience.isArray() && audience.size() == 1 && audience.path(0).isString()
                    && resource.equals(audience.path(0).asString());
            if (!(claims.isObject() && exactAudience
                    && tenant.issuer().equals(claims.path("iss").asString())
                    && subject.equals(claims.path("sub").asString())
                    && principalType.equals(claims.path("principal_type").asString())
                    && "TENANT".equals(claims.path("scope_context").asString())
                    && (tenantId.isString() || tenantId.isIntegralNumber())
                    && Long.toString(tenant.tenantId()).equals(tenantId.asString())
                    && scope.equals(claims.path("scope").asString()))) {
                throw new IllegalArgumentException(TOKEN_MISMATCH);
            }
        } catch (tools.jackson.core.JacksonException | IllegalArgumentException invalid) {
            throw new AssertionError(TOKEN_MISMATCH);
        }
    }

    static void verify(ProcessInstanceDTO instance, String linkedRunId, Instant agentCreatedAt, String agentDefinition,
                       ProcessInstanceCorrelationPageDTO starts, ProcessAgentOutcomePageDTO outcomes, AgentRunPageDTO agents) {
        require(instance.correlationId().equals(starts.correlationId())
                        && starts.nextCursor() == null && starts.items().size() == 1,
                "Exactly one original Process owner START receipt");
        var start = starts.items().getFirst();
        require(instance.instanceId().equals(start.instanceId()) && instance.releaseVersion().equals(start.releaseVersion())
                        && instance.submittedAt().equals(start.submittedAt()),
                "Process START receipt agrees with the exact owned instance");

        require(instance.instanceId().equals(outcomes.instanceId())
                        && outcomes.nextCursor() == null && outcomes.items().size() == 1,
                "Exactly one original Process owner Agent outcome");
        var outcome = outcomes.items().getFirst();
        require("agentStart".equals(outcome.activityId()) && outcome.dispatchState() == ProcessAgentOutcomePageDTO.DispatchState.CLOSED
                        && outcome.inboxState() == ProcessAgentOutcomePageDTO.InboxState.APPLIED
                        && outcome.terminalStatus() == AgentRunStatusEnum.COMPLETED,
                "Process Agent outcome agrees with the reconciled dispatch and committed Inbox");

        require(agents.nextCursor() == null && agents.items().size() == 1,
                "Exactly one original bridge SERVICE Agent run");
        var agent = agents.items().getFirst();
        require(linkedRunId.equals(agent.runId()) && agentDefinition.equals(agent.definitionKey())
                        && agent.definitionVersion() == 1 && agentCreatedAt.equals(agent.createdAt())
                        && agent.status() == AgentRunStatusEnum.COMPLETED,
                "Agent correlation agrees with the exact linked original bridge SERVICE run");
    }

    private static void require(boolean condition, String failure) {
        if (!condition) throw new AssertionError(failure);
    }
}
