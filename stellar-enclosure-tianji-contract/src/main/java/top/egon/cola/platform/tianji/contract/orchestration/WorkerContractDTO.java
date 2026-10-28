package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Map;
import java.util.Objects;

/** Exact versioned value contract a Worker must match before executing business work. */
public record WorkerContractDTO(String topic, String schemaVersion, WorkerEffectSafetyEnum effectSafety,
                                Map<String, ProcessValueTypeEnum> inputs, Map<String, ProcessValueTypeEnum> outputs) {
    public WorkerContractDTO {
        topic = OrchestrationChecks.definition(topic);
        schemaVersion = OrchestrationChecks.release(schemaVersion);
        if (topic.length() + schemaVersion.length() + 1 > 255) throw OrchestrationChecks.invalid();
        effectSafety = Objects.requireNonNull(effectSafety, "effectSafety");
        inputs = OrchestrationChecks.schema(inputs);
        outputs = OrchestrationChecks.schema(outputs);
    }

    public Map<String, Object> validateInput(Map<String, Object> values) { return validate(inputs, values); }
    public Map<String, Object> validateOutput(Map<String, Object> values) { return validate(outputs, values); }

    private static Map<String, Object> validate(Map<String, ProcessValueTypeEnum> schema, Map<String, Object> values) {
        Map<String, Object> checked = OrchestrationChecks.values(values);
        if (!schema.keySet().equals(checked.keySet())) throw OrchestrationChecks.invalid();
        schema.forEach((key, type) -> {
            Object value = checked.get(key);
            if (!(type == ProcessValueTypeEnum.STRING && value instanceof String)
                    && !(type == ProcessValueTypeEnum.INTEGER && value instanceof Long)
                    && !(type == ProcessValueTypeEnum.BOOLEAN && value instanceof Boolean)) throw OrchestrationChecks.invalid();
        });
        return checked;
    }
}
