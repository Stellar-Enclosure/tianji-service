package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public record AgentTaskBindingVO(
        @NotNull @Valid TemplateRefVO defaultTemplate,
        @NotNull @Size(min = 1, max = 16) List<@Valid TemplateRefVO> allowedTemplates,
        @NotNull @Valid ProcessSchemaVO parameterSchema,
        @NotNull Map<String, Object> parameterDefaults,
        @NotNull @Size(max = 16) List<String> overridableParameters,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid TemplateRefVO configurationRef) {

    public AgentTaskBindingVO {
        require(defaultTemplate != null && allowedTemplates != null && !allowedTemplates.isEmpty() && allowedTemplates.size() <= 16);
        var versions = new HashSet<List<Object>>();
        for (TemplateRefVO candidate : allowedTemplates) {
            require(candidate != null && versions.add(List.of(candidate.key(), candidate.version())));
        }
        require(allowedTemplates.contains(defaultTemplate));
        allowedTemplates = List.copyOf(allowedTemplates);

        require(parameterSchema != null && "object".equals(parameterSchema.type()) && parameterDefaults != null);
        require(parameterSchema.properties().keySet().containsAll(parameterDefaults.keySet()));
        var normalized = new java.util.LinkedHashMap<String, Object>();
        parameterDefaults.forEach((key, value) -> normalized.put(key, parameterSchema.properties().get(key).normalizeValue(value)));
        parameterDefaults = ProcessTypedJson.immutableObject(normalized, 64 * 1024);
        require(overridableParameters != null && overridableParameters.size() <= 16);
        var seen = new HashSet<String>();
        for (String parameter : overridableParameters) {
            require(parameter != null && parameterSchema.properties().containsKey(parameter) && seen.add(parameter));
        }
        overridableParameters = List.copyOf(overridableParameters);
    }

    public record TemplateRefVO(@NotNull String key, @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) long version,
                                @NotNull String contentSha256) {
        public TemplateRefVO {
            require(ProcessTypedJson.isId(key) && version >= 1 && version <= ProcessTypedJson.V2_MAX_WIRE_INTEGER
                    && ProcessTypedJson.isSha256(contentSha256));
        }
    }

    private static void require(boolean accepted) {
        if (!accepted) {
            throw new IllegalArgumentException("Invalid automation agent task binding");
        }
    }
}
