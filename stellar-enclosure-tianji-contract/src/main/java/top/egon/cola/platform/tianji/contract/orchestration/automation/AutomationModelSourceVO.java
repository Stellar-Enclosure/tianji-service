package top.egon.cola.platform.tianji.contract.orchestration.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;

/** Parsed release source. Draft text and native XML semantics are owned by the Process host. */
public record AutomationModelSourceVO(
        @NotNull String profile,
        @NotNull @Size(min = 1, max = 64) List<@Valid ModelSourceVO> models,
        @NotNull @Valid ProcessSchemaVO inputSchema,
        @NotNull @Valid ProcessSchemaVO outputSchema,
        @NotNull @Size(max = 128) List<@Valid AutomationNodeBindingVO> nodeBindings,
        @NotNull @Size(max = 32) List<@Valid EventRouteVO> eventRoutes) {

    private static final int MAX_MODEL_BYTES = 4 * 1024 * 1024;

    public AutomationModelSourceVO {
        require("AUTOMATION_V2".equals(profile) && models != null && !models.isEmpty() && models.size() <= 64);
        require(models.getFirst() != null && "BPMN".equals(models.getFirst().type()));
        int modelBytes = 0;
        var modelKeys = new HashSet<String>();
        for (ModelSourceVO model : models) {
            require(model != null && modelKeys.add(model.definitionKey()));
            modelBytes += ProcessTypedJson.utf8Length(model.xml(), MAX_MODEL_BYTES - modelBytes);
        }
        models = List.copyOf(models);
        require(inputSchema != null && "object".equals(inputSchema.type())
                && outputSchema != null && "object".equals(outputSchema.type()));
        require(nodeBindings != null && nodeBindings.size() <= 128);
        var nodeIds = new HashSet<String>();
        for (AutomationNodeBindingVO binding : nodeBindings) {
            require(binding != null && nodeIds.add(binding.nodeId()));
        }
        nodeBindings = List.copyOf(nodeBindings);
        require(eventRoutes != null && eventRoutes.size() <= 32);
        var routeVersions = new HashSet<List<Object>>();
        for (EventRouteVO route : eventRoutes) {
            require(route != null && routeVersions.add(List.of(route.routeKey(), route.version())));
        }
        eventRoutes = List.copyOf(eventRoutes);
    }

    public record ModelSourceVO(@NotNull String type, @NotNull String definitionKey, @NotNull String xml) {
        public ModelSourceVO {
            require(("BPMN".equals(type) || "DMN".equals(type)) && ProcessTypedJson.isId(definitionKey));
            require(xml != null && !xml.isBlank());
            ProcessTypedJson.utf8Length(xml, MAX_MODEL_BYTES);
        }
    }

    private static void require(boolean accepted) {
        if (!accepted) {
            throw new IllegalArgumentException("Invalid automation model source");
        }
    }
}
