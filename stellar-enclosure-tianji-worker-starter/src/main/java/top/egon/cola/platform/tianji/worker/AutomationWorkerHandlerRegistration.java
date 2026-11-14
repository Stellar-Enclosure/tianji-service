package top.egon.cola.platform.tianji.worker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;

import java.util.Map;

/** Frozen schema and effect adapter registered by the Worker host. */
public record AutomationWorkerHandlerRegistration(@NotNull @Valid AutomationWorkerHandlerDescriptor descriptor,
                                                  @NotNull @Valid ProcessSchemaVO inputSchema,
                                                  @NotNull @Valid ProcessSchemaVO outputSchema,
                                                  AutomationTransactionalWorkerHandler transactional,
                                                  AutomationNonTransactionalWorkerHandler nonTransactional) {
    public AutomationWorkerHandlerRegistration {
        if (descriptor == null || inputSchema == null || outputSchema == null
                || !"object".equals(inputSchema.type()) || !"object".equals(outputSchema.type())
                || !WorkerDigests.schemaSha256(inputSchema).equals(descriptor.inputSchemaSha256())
                || !WorkerDigests.schemaSha256(outputSchema).equals(descriptor.outputSchemaSha256())
                || (transactional == null) == (nonTransactional == null)
                || (transactional != null) != (descriptor.effectMode() == WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT)) {
            throw new IllegalArgumentException("Worker automation handler registration is invalid");
        }
    }

    Map<String, Object> normalizeInput(Map<String, Object> input) {
        return normalize(inputSchema, input);
    }

    Map<String, Object> normalizeOutput(Map<String, Object> output) {
        return normalize(outputSchema, output);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalize(ProcessSchemaVO schema, Map<String, Object> value) {
        return (Map<String, Object>) schema.normalizeValue(value);
    }
}
