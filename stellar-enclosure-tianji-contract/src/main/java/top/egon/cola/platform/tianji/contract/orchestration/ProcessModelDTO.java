package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Map;
import java.util.Objects;

public record ProcessModelDTO(ProcessModelTypeEnum type, String definitionKey, String contentSha256,
                              Map<String, ProcessValueTypeEnum> inputs) {
    public ProcessModelDTO {
        type = Objects.requireNonNull(type, "type");
        definitionKey = OrchestrationChecks.definition(definitionKey);
        contentSha256 = OrchestrationChecks.hash(contentSha256);
        inputs = OrchestrationChecks.schema(inputs);
    }
}
