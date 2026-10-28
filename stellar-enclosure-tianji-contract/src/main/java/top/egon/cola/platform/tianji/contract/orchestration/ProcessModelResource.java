package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Map;
import java.util.Objects;

/** XML is a reviewed model resource; it does not confer permission to publish or execute. */
public record ProcessModelResource(ProcessModelTypeEnum type, String definitionKey, String xml,
                                   Map<String, ProcessValueTypeEnum> inputs) {
    public ProcessModelResource {
        type = Objects.requireNonNull(type, "type");
        definitionKey = OrchestrationChecks.definition(definitionKey);
        xml = OrchestrationChecks.xml(xml);
        inputs = OrchestrationChecks.schema(inputs);
    }
}
