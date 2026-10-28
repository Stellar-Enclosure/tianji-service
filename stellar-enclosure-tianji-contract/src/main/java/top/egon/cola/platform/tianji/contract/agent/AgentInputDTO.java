package top.egon.cola.platform.tianji.contract.agent;

import java.util.List;
import java.util.Map;

/** User input and references are data; they do not confer access to referenced artifacts. */
public record AgentInputDTO(String text, List<String> artifactRefs, Map<String, Object> taskData) {
    public AgentInputDTO {
        artifactRefs = artifactRefs == null ? List.of() : artifactRefs.stream()
                .map(ref -> ContractChecks.required(ref, "artifactRef", 1024)).toList();
        taskData = ContractChecks.jsonObject(taskData);
        if ((text == null || text.isBlank()) && artifactRefs.isEmpty() && taskData.isEmpty()) {
            throw new IllegalArgumentException("agent input must not be empty");
        }
    }
}
