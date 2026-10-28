package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** The execution ID identifies this standalone decision, not a BPMN process instance. */
public record DecisionResultDTO(String decisionExecutionId, String decisionKey, String releaseVersion, String correlationId,
                                Instant submittedAt, List<Map<String, Object>> outputs, List<String> matchedRuleIds) {
    public DecisionResultDTO {
        if (decisionExecutionId == null || !decisionExecutionId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw OrchestrationChecks.invalid();
        }
        decisionKey = OrchestrationChecks.definition(decisionKey);
        releaseVersion = OrchestrationChecks.release(releaseVersion);
        correlationId = OrchestrationChecks.command(correlationId);
        submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        if (outputs == null || outputs.size() > 256 || matchedRuleIds == null || matchedRuleIds.size() > 256
                || outputs.isEmpty() != matchedRuleIds.isEmpty()) throw OrchestrationChecks.invalid();
        outputs = outputs.stream().map(OrchestrationChecks::values).toList();
        matchedRuleIds = matchedRuleIds.stream().map(id -> {
            if (id == null || !(id.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}") || id.matches("row:[1-9][0-9]{0,2}"))) {
                throw OrchestrationChecks.invalid();
            }
            return id;
        }).toList();
        if (new HashSet<>(matchedRuleIds).size() != matchedRuleIds.size()) throw OrchestrationChecks.invalid();
    }
}
