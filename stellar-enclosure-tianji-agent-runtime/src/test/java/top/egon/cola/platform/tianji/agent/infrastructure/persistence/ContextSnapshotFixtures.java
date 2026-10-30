package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentContextMetadataDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextRoleEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceKindEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextWindowDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ContextSnapshotFixtures {
    static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    static final AgentRunLease LEASE = new AgentRunLease("run-one", "worker", 7, NOW.plusSeconds(20));
    static final ExecutionPolicyDTO POLICY = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0, 1000, 100,
            BigDecimal.ONE, "USD", 2, Set.of(), "retry-v1", "approval-v1");
    static final AgentDefinitionSpecDTO DEFINITION = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1",
            new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route", "policy"), POLICY, Map.of());
    static final AgentExecutionSnapshot SNAPSHOT = new AgentExecutionSnapshot(DEFINITION, "a".repeat(64), POLICY, new AgentInputDTO("private-input", null, null), null);
    static final AgentRunDTO RUN = new AgentRunDTO("run-one", "assistant", 1, null, AgentRunStatusEnum.RUNNING, null, false, 1, 2, 0, NOW.plusSeconds(30), NOW, null);
    static final AgentRunAuthorization.Request AUTHORIZATION = new AgentRunAuthorization.Request(ACTOR, RUN.runId(), "assistant", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy", false, RUN.deadlineAt());
    static final AgentBudgetRequest REQUEST = new AgentBudgetRequest("call-one", AgentBudgetRequest.Kind.MODEL, null, 20, 10, new BigDecimal("0.1"));

    static AgentContextMetadataDTO metadata() { return metadata("call-one"); }
    static AgentContextMetadataDTO metadata(String call) {
        return new AgentContextMetadataDTO(1, AgentContextMetadataDTO.PROJECTION_V1, ACTOR.identityFingerprint(), RUN.runId(), call, LEASE.epoch(), "writer",
                "invocation", "session", 0, "d".repeat(64), null, "a".repeat(64), "b".repeat(64), "c".repeat(64), "e".repeat(64), "f".repeat(64), "0".repeat(64), "1".repeat(64),
                new AgentContextWindowDTO("model", "route", "policy", 100, 5), 20, 10,
                List.of(new AgentContextSourceDTO(AgentContextSourceKindEnum.DEFINITION, AgentContextRoleEnum.SYSTEM, "assistant", "1", "definition:assistant:1",
                        "a".repeat(64), "e".repeat(64), 10, null)), List.of());
    }
    static AgentBudgetReservation reserved() {
        return new AgentBudgetReservation(REQUEST.reservationId(), LEASE.epoch(), AgentBudgetRequest.Kind.MODEL, null, REQUEST.inputTokens(), REQUEST.outputTokens(),
                REQUEST.estimatedCost(), AgentBudgetReservation.State.RESERVED, null, null, null, false);
    }
}
