package top.egon.cola.platform.tianji.process.server.bridge;

import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Operator-owned routing. Neither a model nor a Worker payload chooses an Agent definition or source. */
public record AgentDispatchBinding(ProcessActor actor, String sourceId, String topic, String schemaVersion,
                                   String definitionKey, long definitionVersion, String definitionContentSha256,
                                   String instruction, Duration submissionTimeout) {
    public AgentDispatchBinding {
        if (actor == null || actor.subjectType() != ProcessActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())
                || submissionTimeout == null || submissionTimeout.compareTo(Duration.ofSeconds(1)) < 0
                || submissionTimeout.compareTo(Duration.ofMinutes(15)) > 0
                || submissionTimeout.toNanos() % 1_000_000 != 0 || instruction == null || instruction.isBlank()
                || instruction.length() > 16_384) throw invalid();
        ProcessModelValidator.nativeWorkerTopic(topic, schemaVersion);
        // Reuse the wire contract's identifier, hash and text validation.
        new AgentProcessLink(sourceId, WorkerScopeTypeEnum.BPMN, "instance", "occurrence", definitionContentSha256, Instant.EPOCH);
        new StartAgentRunCommand(definitionKey, definitionVersion, null, "intent", null, "correlation",
                new AgentInputDTO(instruction, List.of(), Map.of()), null);
        if (!definitionKey.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw invalid();
        for (int i = 0; i < instruction.length(); i++) {
            char c = instruction.charAt(i);
            if (c == 0 || Character.isLowSurrogate(c) || Character.isHighSurrogate(c)
                    && (i + 1 >= instruction.length() || !Character.isLowSurrogate(instruction.charAt(++i)))) throw invalid();
        }
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Agent dispatch binding is invalid"); }
    @Override public String toString() { return "AgentDispatchBinding[redacted]"; }
}
