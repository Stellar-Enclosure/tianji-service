package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyAgentFlowImporterTest {
    private static ValidatorFactory validators;
    private static LegacyAgentFlowImporter importer;

    private static final String FLOW = """
            egon:
              cola:
                component:
                  agent-flow:
                    enabled: false
                    execution-timeout: 2m
                    flows:
                      research-flow:
                        chat-model-bean-name: researchModel
                        model-name: ${MODEL_NAME:fixture-model}
                        root-agent-name: sequence
                        agents:
                          - name: planner
                            instruction: plan
                            output-key: plan
                          - name: writer
                            instruction: write
                            output-key: report
                        workflows:
                          - type: SEQUENTIAL
                            name: sequence
                            sub-agent-names: [planner, writer]
            """;

    @BeforeAll
    static void setUp() {
        validators = Validation.buildDefaultValidatorFactory();
        importer = new LegacyAgentFlowImporter(new AgentFlowConfigValidator(new ValidationUtils(validators.getValidator())));
    }

    @AfterAll
    static void tearDown() {
        validators.close();
    }

    @Test
    void imports_and_validates_the_selected_legacy_flow_with_explicit_substitutions() {
        var spec = importer.importFlow(resource(FLOW), options(Map.of("MODEL_NAME", "model-v1")));
        assertEquals("research", spec.definitionKey());
        assertEquals("CONFIG", spec.executionMode());
        assertEquals("model-v1", spec.configuration().get("modelName"));
        assertEquals("sequence", spec.configuration().get("rootAgentName"));
        assertEquals(2, ((List<?>) spec.configuration().get("agents")).size());
    }

    @Test
    void rejects_unknown_fields_invalid_graphs_and_unresolved_placeholders() {
        assertThrows(RuntimeException.class, () -> importer.importFlow(
                resource(FLOW.replace("root-agent-name: sequence", "root-agent-name: missing")), options(Map.of())));
        assertThrows(RuntimeException.class, () -> importer.importFlow(
                resource(FLOW.replace("enabled: false", "enabled: false\n        unexpected-field: true")), options(Map.of())));
        assertThrows(RuntimeException.class, () -> importer.importFlow(
                resource(FLOW.replace("${MODEL_NAME:fixture-model}", "${UNRESOLVED_MODEL}")), options(Map.of())));
    }

    private static ByteArrayResource resource(String value) {
        return new ByteArrayResource(value.getBytes(StandardCharsets.UTF_8));
    }

    private static AgentFlowImportOptions options(Map<String, String> substitutions) {
        var policy = new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(20), Duration.ofSeconds(10),
                5, 0, 1000, 500, BigDecimal.ONE, "USD", 1, Set.of(), "retry-v1", "approval-v1");
        var dependencies = new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "model-route", "policy-v1");
        return new AgentFlowImportOptions("research-flow", "research", 1, "1", "1", dependencies, policy, substitutions);
    }
}
