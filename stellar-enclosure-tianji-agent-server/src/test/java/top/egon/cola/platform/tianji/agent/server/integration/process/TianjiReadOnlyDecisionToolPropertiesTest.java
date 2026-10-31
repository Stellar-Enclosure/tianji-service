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

class TianjiReadOnlyDecisionToolPropertiesTest {
    private static final String PREFIX = "stellar.tianji.agent.tools.read-only-decision.";
    private static final TianjiFixedDecisionToolBindings.BackendVO BACKEND = new TianjiFixedDecisionToolBindings.BackendVO(21,
            URI.create("https://idp.example/21"), URI.create("https://process.example/private-base"), URI.create("https://process.example/api/21"),
            "private-registration", "private-client", "private-app", "private-business", "private-source", "test", "release-1", "a".repeat(64),
            "eligibility", "b".repeat(64), Map.of("amount", ProcessValueTypeEnum.INTEGER), Duration.ofMillis(750));
    private static final TianjiFixedDecisionToolBindings.BindingVO BINDING = new TianjiFixedDecisionToolBindings.BindingVO(21,
            "c".repeat(64), TrustedAgentActor.SubjectType.USER, "review", 1, "d".repeat(64),
            new DefinitionResourceRef("decision.eligibility", 1, "e".repeat(64)), BACKEND);

    @Test void absent_values_keep_disabled_finite_defaults_without_requiring_a_binding() {
        var properties = new TianjiReadOnlyDecisionToolProperties(false, 0, null);
        assertFalse(properties.enabled()); assertEquals(4, properties.concurrency()); assertEquals(List.of(), properties.bindings());
        runner().run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(properties, context.getBean(TianjiReadOnlyDecisionToolProperties.class));
        });
    }

    @Test void concurrency_matches_the_existing_transport_bounds() {
        for (int concurrency : new int[]{-1, 9, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new TianjiReadOnlyDecisionToolProperties(false, concurrency, List.of()));
        }
        for (int concurrency : new int[]{1, 8}) assertEquals(concurrency,
                new TianjiReadOnlyDecisionToolProperties(false, concurrency, List.of()).concurrency());
        assertEquals(4, new TianjiReadOnlyDecisionToolProperties(true, 0, List.of(BINDING)).concurrency());
    }

    @Test void enabled_configuration_requires_a_nonempty_finite_binding_list() {
        assertThrows(IllegalArgumentException.class, () -> new TianjiReadOnlyDecisionToolProperties(true, 4, null));
        assertThrows(IllegalArgumentException.class, () -> new TianjiReadOnlyDecisionToolProperties(true, 4, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TianjiReadOnlyDecisionToolProperties(false, 4, Collections.nCopies(257, BINDING)));
        assertEquals(256, new TianjiReadOnlyDecisionToolProperties(false, 4, Collections.nCopies(256, BINDING)).bindings().size());
        assertEquals(List.of(BINDING), new TianjiReadOnlyDecisionToolProperties(true, 4, List.of(BINDING)).bindings());
    }

    @Test void values_copy_the_input_and_refuse_null_elements() {
        var bindings = new ArrayList<>(List.of(BINDING));
        var properties = new TianjiReadOnlyDecisionToolProperties(true, 4, bindings); bindings.clear();
        assertEquals(List.of(BINDING), properties.bindings());
        assertThrows(UnsupportedOperationException.class, () -> properties.bindings().clear());
        assertThrows(RuntimeException.class, () -> new TianjiReadOnlyDecisionToolProperties(false, 4, Collections.singletonList(null)));
    }

    @Test void spring_binds_the_existing_full_backend_and_binding_records() {
        runner().withPropertyValues(values()).run(context -> {
            assertNull(context.getStartupFailure());
            var properties = context.getBean(TianjiReadOnlyDecisionToolProperties.class);
            assertEquals(new TianjiReadOnlyDecisionToolProperties(true, 2, List.of(BINDING)), properties);
            assertEquals(BACKEND, properties.bindings().getFirst().backend());
            assertEquals(BACKEND.bindingSha256(), properties.bindings().getFirst().backend().bindingSha256());
        });
    }

    @Test void strict_binding_rejects_unknown_fields_without_accepting_duplicate_authority_or_timeout_models() {
        for (var unknown : List.of("unexpected=true", "timeout=PT5S", "descriptors[0].key=decision.eligibility",
                "bindings[0].unexpected=true", "bindings[0].tool.unexpected=true", "bindings[0].backend.unexpected=true",
                "bindings[0].backend.token=private-token", "bindings[0].backend.input-schema.amount.unexpected=true")) {
            runner().withPropertyValues(values()).withPropertyValues(PREFIX + unknown).run(context -> assertNotNull(context.getStartupFailure(), unknown));
        }
        assertEquals(List.of("enabled", "concurrency", "bindings"), Arrays.stream(TianjiReadOnlyDecisionToolProperties.class.getRecordComponents()).map(component -> component.getName()).toList());
    }

    @Test void each_binding_has_independently_closed_nested_roots() {
        var configured = new ArrayList<String>(List.of(values()));
        for (var value : values()) if (value.contains(".bindings[0].")) configured.add(value.replace(".bindings[0].", ".bindings[1]."));
        String[] values = configured.toArray(String[]::new);
        runner().withPropertyValues(values).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(List.of(BINDING, BINDING), context.getBean(TianjiReadOnlyDecisionToolProperties.class).bindings());
        });
        for (var unknown : List.of("bindings[1].backend.unexpected=private-value", "bindings[1].tool.unexpected=private-value")) {
            runner().withPropertyValues(values).withPropertyValues(PREFIX + unknown).run(context -> {
                assertNotNull(context.getStartupFailure(), unknown);
                var root = context.getStartupFailure();
                while (root.getCause() != null) root = root.getCause();
                assertEquals("TIANJI_READ_ONLY_DECISION_TOOL_CONFIGURATION_INVALID", root.getMessage());
                assertNull(root.getCause()); assertFalse(root.getMessage().contains("private-value"));
            });
        }
    }

    @Test void spring_reuses_nested_validation_and_rejects_incomplete_or_invalid_configuration() {
        runner().withPropertyValues(PREFIX + "enabled=true").run(context -> assertNotNull(context.getStartupFailure()));
        for (var malformed : List.of("concurrency=9", "bindings[0].backend.input-schema.amount=UNKNOWN", "bindings[0].backend.timeout=99ms",
                "bindings[0].backend.timeout=PT30.000000001S", "bindings[0].backend.process-base-uri=http://process.example/private-base",
                "bindings[0].backend.process-base-uri=https://process.example/私有", "bindings[0].backend.tenant-id=22")) {
            runner().withPropertyValues(values()).withPropertyValues(PREFIX + malformed).run(context -> assertNotNull(context.getStartupFailure(), malformed));
        }
    }

    @Test void property_string_is_redacted_even_when_enabled() {
        var properties = new TianjiReadOnlyDecisionToolProperties(true, 2, List.of(BINDING));
        assertEquals("TianjiReadOnlyDecisionToolProperties[redacted]", properties.toString());
        for (var secret : List.of("private-registration", "private-client", "private-app", "private-base", "review", "eligibility", "a".repeat(64), "c".repeat(64))) {
            assertFalse(properties.toString().contains(secret));
        }
    }

    @Test void base_yaml_declares_the_same_disabled_defaults() throws Exception {
        var yaml = new YamlPropertySourceLoader().load("base-defaults", new ClassPathResource("application.yml"));
        assertEquals(false, yaml.getFirst().getProperty(PREFIX + "enabled")); assertEquals(4, yaml.getFirst().getProperty(PREFIX + "concurrency"));
        runner().withInitializer(context -> yaml.forEach(source -> context.getEnvironment().getPropertySources().addLast(source))).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(new TianjiReadOnlyDecisionToolProperties(false, 0, null), context.getBean(TianjiReadOnlyDecisionToolProperties.class));
        });
    }

    private static ApplicationContextRunner runner() { return new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class); }
    @EnableConfigurationProperties(TianjiReadOnlyDecisionToolProperties.class)
    static class PropertiesConfiguration { }

    private static String[] values() {
        return new String[]{PREFIX + "enabled=true", PREFIX + "concurrency=2",
                PREFIX + "bindings[0].tenant-id=21", PREFIX + "bindings[0].owner-issuer-fingerprint=" + "c".repeat(64), PREFIX + "bindings[0].principal-type=USER",
                PREFIX + "bindings[0].definition-key=review", PREFIX + "bindings[0].definition-version=1", PREFIX + "bindings[0].definition-content-sha256=" + "d".repeat(64),
                PREFIX + "bindings[0].tool.key=decision.eligibility", PREFIX + "bindings[0].tool.version=1", PREFIX + "bindings[0].tool.content-sha256=" + "e".repeat(64),
                PREFIX + "bindings[0].backend.tenant-id=21", PREFIX + "bindings[0].backend.process-issuer=https://idp.example/21",
                PREFIX + "bindings[0].backend.process-base-uri=https://process.example/private-base", PREFIX + "bindings[0].backend.process-resource-uri=https://process.example/api/21",
                PREFIX + "bindings[0].backend.registration-id=private-registration", PREFIX + "bindings[0].backend.expected-client-id=private-client",
                PREFIX + "bindings[0].backend.expected-app-id=private-app", PREFIX + "bindings[0].backend.expected-source-biz=private-business",
                PREFIX + "bindings[0].backend.expected-source-app=private-source", PREFIX + "bindings[0].backend.expected-environment=test",
                PREFIX + "bindings[0].backend.release-version=release-1", PREFIX + "bindings[0].backend.manifest-sha256=" + "a".repeat(64),
                PREFIX + "bindings[0].backend.decision-key=eligibility", PREFIX + "bindings[0].backend.decision-model-sha256=" + "b".repeat(64),
                PREFIX + "bindings[0].backend.input-schema.amount=INTEGER", PREFIX + "bindings[0].backend.timeout=750ms"};
    }
}
