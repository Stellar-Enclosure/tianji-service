package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import top.egon.cola.component.agentflow.autoconfigure.AgentFlowProperties;
import top.egon.cola.component.agentflow.config.AgentFlowConfigDTO;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Imports one legacy YAML flow using the existing Agent Flow binder and graph validator. */
public final class LegacyAgentFlowImporter {
    private static final int MAX_YAML_BYTES = 1_048_576;
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };
    private final AgentFlowConfigValidator validator;
    private final ObjectMapper mapper = new ObjectMapper();

    public LegacyAgentFlowImporter(AgentFlowConfigValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public AgentDefinitionSpecDTO importFlow(Resource source, AgentFlowImportOptions options) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(options, "options");
        try (InputStream input = source.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_YAML_BYTES + 1);
            if (bytes.length > MAX_YAML_BYTES) {
                throw new IllegalArgumentException("Agent Flow YAML exceeds its size limit");
            }
            Resource bounded = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return "legacy-agent-flow.yml";
                }
            };
            List<PropertySource<?>> yamlSources = new YamlPropertySourceLoader().load("legacy-agent-flow", bounded);
            MutablePropertySources placeholderSources = new MutablePropertySources();
            placeholderSources.addFirst(new MapPropertySource("explicit-substitutions",
                    new LinkedHashMap<>(options.substitutions())));
            yamlSources.forEach(placeholderSources::addLast);
            Binder binder = new Binder(ConfigurationPropertySources.from(yamlSources),
                    new PropertySourcesPlaceholdersResolver(placeholderSources));
            AgentFlowProperties properties = binder.bind("egon.cola.component.agent-flow",
                    Bindable.of(AgentFlowProperties.class), new NoUnboundElementsBindHandler(BindHandler.DEFAULT))
                    .orElseThrow(() -> new IllegalArgumentException("Agent Flow YAML section is missing"));
            AgentFlowConfigDTO flow = properties.flows().get(options.sourceFlowId());
            if (flow == null) {
                throw new IllegalArgumentException("selected Agent Flow is missing");
            }
            validator.validate(new AgentFlowProperties(true, properties.executionTimeout(), properties.shutdownTimeout(),
                    Map.of(options.sourceFlowId(), flow)));
            Map<String, Object> configuration = mapper.convertValue(flow, JSON_OBJECT);
            rejectUnresolved(configuration);
            return new AgentDefinitionSpecDTO(options.definitionKey(), options.version(), "CONFIG", null,
                    options.inputSchemaVersion(), options.outputSchemaVersion(), options.dependencies(),
                    options.policy(), configuration);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Agent Flow YAML cannot be read", failure);
        }
    }

    private static void rejectUnresolved(Object value) {
        if (value instanceof String text && text.contains("${")) {
            throw new IllegalArgumentException("Agent Flow YAML contains an unresolved placeholder");
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(LegacyAgentFlowImporter::rejectUnresolved);
        } else if (value instanceof Iterable<?> values) {
            values.forEach(LegacyAgentFlowImporter::rejectUnresolved);
        }
    }
}
