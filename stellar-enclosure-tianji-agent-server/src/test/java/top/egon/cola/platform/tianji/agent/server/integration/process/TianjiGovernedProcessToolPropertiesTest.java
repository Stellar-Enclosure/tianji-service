package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TianjiGovernedProcessToolPropertiesTest {
    private static final String PREFIX = "stellar.tianji.agent.tools.governed-process.";
    private static final Map<String, ProcessValueTypeEnum> INPUT = Map.of("amount", ProcessValueTypeEnum.INTEGER, "approved", ProcessValueTypeEnum.BOOLEAN, "note", ProcessValueTypeEnum.STRING);
    private static final TianjiFixedDecisionToolBindings.BackendVO DECISION = new TianjiFixedDecisionToolBindings.BackendVO(21,
            URI.create("https://idp.example/21"), URI.create("https://process.example/private-base"), URI.create("https://process.example/api/21"),
            "private-registration", "private-client", "private-app", "private-business", "private-source", "test", "release-1", "a".repeat(64),
            "eligibility", "b".repeat(64), INPUT, Duration.ofMillis(750));
    private static final TianjiFixedBpmnStartToolBindings.BackendVO START = new TianjiFixedBpmnStartToolBindings.BackendVO(21,
            URI.create("https://idp.example/21"), URI.create("https://process.example/private-base"), URI.create("https://process.example/api/21"),
            "private-registration", "private-client", "private-app", "private-business", "private-source", "test", "release-1", "a".repeat(64),
            "invoice-start", "b".repeat(64), INPUT, Duration.ofSeconds(1));
    private static final TianjiFixedDecisionToolBindings.BindingVO DECISION_BINDING = new TianjiFixedDecisionToolBindings.BindingVO(21,
            "c".repeat(64), TrustedAgentActor.SubjectType.USER, "review", 1, "d".repeat(64), new DefinitionResourceRef("decision.eligibility", 1, "e".repeat(64)), DECISION);
    private static final TianjiFixedBpmnStartToolBindings.BindingVO START_BINDING = new TianjiFixedBpmnStartToolBindings.BindingVO(21,
            "c".repeat(64), TrustedAgentActor.SubjectType.USER, "review", 1, "d".repeat(64), new DefinitionResourceRef("process.invoice", 1, "f".repeat(64)), START);

    @Test void absent_configuration_is_disabled_with_finite_defaults_and_empty_immutable_profiles() {
        var value = new TianjiGovernedProcessToolProperties(false, 0, null, null);
        assertFalse(value.enabled()); assertEquals(4, value.concurrency()); assertEquals(List.of(), value.decisionBindings()); assertEquals(List.of(), value.startBindings());
        runner().run(context -> {
            assertNull(context.getStartupFailure()); assertEquals(value, context.getBean(TianjiGovernedProcessToolProperties.class));
        });
    }

    @Test void concurrency_uses_the_same_bounds_as_both_existing_transports() {
        for (int concurrency : new int[]{-1, 9, Integer.MAX_VALUE}) assertThrows(IllegalArgumentException.class,
                () -> new TianjiGovernedProcessToolProperties(false, concurrency, null, null));
        for (int concurrency : new int[]{1, 8}) assertEquals(concurrency, new TianjiGovernedProcessToolProperties(false, concurrency, null, null).concurrency());
        assertEquals(4, new TianjiGovernedProcessToolProperties(true, 0, List.of(DECISION_BINDING), List.of(START_BINDING)).concurrency());
    }

    @Test void enabling_requires_both_profile_families_without_default_authority_or_host_context() {
        assertThrows(IllegalArgumentException.class, () -> new TianjiGovernedProcessToolProperties(true, 4, null, null));
        assertThrows(IllegalArgumentException.class, () -> new TianjiGovernedProcessToolProperties(true, 4, List.of(), List.of(START_BINDING)));
        assertThrows(IllegalArgumentException.class, () -> new TianjiGovernedProcessToolProperties(true, 4, List.of(DECISION_BINDING), List.of()));
        runner().withPropertyValues(PREFIX + "enabled=true").run(context -> assertNotNull(context.getStartupFailure()));
        for (String family : List.of("decision-bindings", "start-bindings")) {
            var only = new ArrayList<String>(List.of(PREFIX + "enabled=true")); appendBinding(only, family + "[0]", family.equals("start-bindings"));
            runner().withPropertyValues(only.toArray(String[]::new)).run(context -> assertNotNull(context.getStartupFailure(), family));
        }
    }

    @Test void each_profile_list_has_an_independent_limit_of_256() {
        assertThrows(IllegalArgumentException.class, () -> new TianjiGovernedProcessToolProperties(false, 4, Collections.nCopies(257, DECISION_BINDING), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TianjiGovernedProcessToolProperties(false, 4, List.of(), Collections.nCopies(257, START_BINDING)));
        var bounded = new TianjiGovernedProcessToolProperties(true, 4, Collections.nCopies(256, DECISION_BINDING), Collections.nCopies(256, START_BINDING));
        assertEquals(256, bounded.decisionBindings().size()); assertEquals(256, bounded.startBindings().size());
    }

    @Test void both_lists_are_defensively_copied_and_null_elements_are_rejected() {
        var decisions = new ArrayList<>(List.of(DECISION_BINDING)); var starts = new ArrayList<>(List.of(START_BINDING));
        var properties = new TianjiGovernedProcessToolProperties(true, 4, decisions, starts); decisions.clear(); starts.clear();
        assertEquals(List.of(DECISION_BINDING), properties.decisionBindings()); assertEquals(List.of(START_BINDING), properties.startBindings());
        assertThrows(UnsupportedOperationException.class, () -> properties.decisionBindings().clear()); assertThrows(UnsupportedOperationException.class, () -> properties.startBindings().clear());
        assertThrows(NullPointerException.class, () -> new TianjiGovernedProcessToolProperties(false, 4, Collections.singletonList(null), List.of()));
        assertThrows(NullPointerException.class, () -> new TianjiGovernedProcessToolProperties(false, 4, List.of(), Collections.singletonList(null)));
    }

    @Test void real_spring_binding_reuses_both_backend_models_and_valid_dynamic_input_schema_maps() {
        runner().withPropertyValues(values(false)).run(context -> {
            assertNull(context.getStartupFailure());
            var actual = context.getBean(TianjiGovernedProcessToolProperties.class);
            assertEquals(new TianjiGovernedProcessToolProperties(true, 2, List.of(DECISION_BINDING), List.of(START_BINDING)), actual);
            assertEquals(DECISION, actual.decisionBindings().getFirst().backend()); assertEquals(START, actual.startBindings().getFirst().backend());
            assertEquals(INPUT, actual.decisionBindings().getFirst().backend().inputSchema()); assertEquals(INPUT, actual.startBindings().getFirst().backend().inputSchema());
            assertEquals(DECISION.bindingSha256(), actual.decisionBindings().getFirst().backend().bindingSha256());
            assertEquals(START.bindingSha256(), actual.startBindings().getFirst().backend().bindingSha256());
        });
    }

    @Test void root_schema_rejects_parallel_descriptor_identity_timeout_and_context_models() {
        assertEquals(List.of("enabled", "concurrency", "decisionBindings", "startBindings"),
                Arrays.stream(TianjiGovernedProcessToolProperties.class.getRecordComponents()).map(component -> component.getName()).toList());
        for (String unknown : List.of("unexpected=true", "timeout=PT5S", "descriptors[0].key=other", "tenant-id=21", "intent-ttl=PT1H",
                "business-version=private-version", "resolver=private-resolver", "token=private-token")) {
            runner().withPropertyValues(values(false)).withPropertyValues(PREFIX + unknown).run(context -> assertNotNull(context.getStartupFailure(), unknown));
        }
    }

    @Test void every_backend_and_tool_root_is_closed_including_the_second_element_of_both_lists() {
        runner().withPropertyValues(values(true)).run(context -> {
            assertNull(context.getStartupFailure()); var value = context.getBean(TianjiGovernedProcessToolProperties.class);
            assertEquals(List.of(DECISION_BINDING, DECISION_BINDING), value.decisionBindings()); assertEquals(List.of(START_BINDING, START_BINDING), value.startBindings());
        });
        for (String family : List.of("decision-bindings", "start-bindings")) for (int index : new int[]{0, 1}) for (String root : List.of("backend", "tool")) {
            String unknown = family + "[" + index + "]." + root + ".unexpected=private-value";
            runner().withPropertyValues(values(true)).withPropertyValues(PREFIX + unknown).run(context -> {
                assertNotNull(context.getStartupFailure(), unknown);
                if (index == 1) {
                    var cause = context.getStartupFailure(); while (cause.getCause() != null) cause = cause.getCause();
                    assertEquals("TIANJI_GOVERNED_PROCESS_TOOL_CONFIGURATION_INVALID", cause.getMessage()); assertNull(cause.getCause()); assertFalse(cause.getMessage().contains("private-value"));
                }
            });
        }
    }

    @Test void unknown_binding_fields_and_nested_schema_leaves_cannot_expand_the_typed_profiles() {
        for (String family : List.of("decision-bindings", "start-bindings")) for (String unknown : List.of(".unexpected=true", ".backend.token=private-token", ".backend.input-schema.amount.unexpected=true")) {
            runner().withPropertyValues(values(false)).withPropertyValues(PREFIX + family + "[0]" + unknown).run(context -> assertNotNull(context.getStartupFailure(), family + unknown));
        }
    }

    @Test void both_backend_families_keep_existing_validation_and_timeout_bounds() {
        for (String family : List.of("decision-bindings", "start-bindings")) for (String malformed : List.of("backend.input-schema.amount=UNKNOWN", "backend.timeout=99ms", "backend.timeout=PT30.000000001S",
                "backend.process-base-uri=http://process.example/private-base", "backend.process-base-uri=https://process.example/私有", "backend.tenant-id=22", "tool.content-sha256=bad", "definition-version=0")) {
            runner().withPropertyValues(values(false)).withPropertyValues(PREFIX + family + "[0]." + malformed).run(context -> assertNotNull(context.getStartupFailure(), family + malformed));
        }
        for (String family : List.of("decision-bindings", "start-bindings")) for (String timeout : List.of("100ms", "PT30S")) {
            runner().withPropertyValues(values(false)).withPropertyValues(PREFIX + family + "[0].backend.timeout=" + timeout).run(context -> assertNull(context.getStartupFailure(), family + timeout));
        }
    }

    @Test void disabled_but_configured_profiles_are_still_strictly_bound() {
        runner().withPropertyValues(values(false)).withPropertyValues(PREFIX + "enabled=false").run(context -> {
            assertNull(context.getStartupFailure()); var value = context.getBean(TianjiGovernedProcessToolProperties.class);
            assertFalse(value.enabled()); assertEquals(List.of(DECISION_BINDING), value.decisionBindings()); assertEquals(List.of(START_BINDING), value.startBindings());
        });
        runner().withPropertyValues(values(false)).withPropertyValues(PREFIX + "enabled=false", PREFIX + "start-bindings[0].backend.unexpected=private-value")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test void operator_values_are_redacted_without_copying_private_profile_details() {
        var value = new TianjiGovernedProcessToolProperties(true, 2, List.of(DECISION_BINDING), List.of(START_BINDING));
        assertEquals("TianjiGovernedProcessToolProperties[redacted]", value.toString());
        for (String secret : List.of("private-registration", "private-client", "private-app", "private-base", "review", "eligibility", "invoice-start", "a".repeat(64), "c".repeat(64))) assertFalse(value.toString().contains(secret));
    }

    @Test void base_yaml_keeps_the_new_mode_disabled_with_only_its_finite_defaults() throws Exception {
        var yaml = new YamlPropertySourceLoader().load("base-defaults", new ClassPathResource("application.yml"));
        assertEquals(false, yaml.getFirst().getProperty(PREFIX + "enabled")); assertEquals(4, yaml.getFirst().getProperty(PREFIX + "concurrency"));
        runner().withInitializer(context -> yaml.forEach(source -> context.getEnvironment().getPropertySources().addLast(source))).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(new TianjiGovernedProcessToolProperties(false, 0, null, null), context.getBean(TianjiGovernedProcessToolProperties.class));
        });
    }

    private static ApplicationContextRunner runner() { return new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class); }
    @EnableConfigurationProperties(TianjiGovernedProcessToolProperties.class)
    static class PropertiesConfiguration { }
    private static String[] values(boolean secondIndex) {
        var values = new ArrayList<>(List.of(PREFIX + "enabled=true", PREFIX + "concurrency=2"));
        for (int index = 0; index < (secondIndex ? 2 : 1); index++) {
            appendBinding(values, "decision-bindings[" + index + "]", false); appendBinding(values, "start-bindings[" + index + "]", true);
        }
        return values.toArray(String[]::new);
    }
    private static void appendBinding(List<String> values, String slot, boolean start) {
        String prefix = PREFIX + slot + ".";
        values.addAll(List.of(prefix + "tenant-id=21", prefix + "owner-issuer-fingerprint=" + "c".repeat(64), prefix + "principal-type=USER",
                prefix + "definition-key=review", prefix + "definition-version=1", prefix + "definition-content-sha256=" + "d".repeat(64),
                prefix + "tool.key=" + (start ? "process.invoice" : "decision.eligibility"), prefix + "tool.version=1", prefix + "tool.content-sha256=" + (start ? "f" : "e").repeat(64),
                prefix + "backend.tenant-id=21", prefix + "backend.process-issuer=https://idp.example/21", prefix + "backend.process-base-uri=https://process.example/private-base",
                prefix + "backend.process-resource-uri=https://process.example/api/21", prefix + "backend.registration-id=private-registration", prefix + "backend.expected-client-id=private-client",
                prefix + "backend.expected-app-id=private-app", prefix + "backend.expected-source-biz=private-business", prefix + "backend.expected-source-app=private-source",
                prefix + "backend.expected-environment=test", prefix + "backend.release-version=release-1", prefix + "backend.manifest-sha256=" + "a".repeat(64),
                prefix + (start ? "backend.process-definition-key=invoice-start" : "backend.decision-key=eligibility"),
                prefix + (start ? "backend.process-model-sha256=" : "backend.decision-model-sha256=") + "b".repeat(64),
                prefix + "backend.input-schema.amount=INTEGER", prefix + "backend.input-schema.approved=BOOLEAN", prefix + "backend.input-schema.note=STRING",
                prefix + "backend.timeout=" + (start ? "PT1S" : "750ms")));
    }
}
