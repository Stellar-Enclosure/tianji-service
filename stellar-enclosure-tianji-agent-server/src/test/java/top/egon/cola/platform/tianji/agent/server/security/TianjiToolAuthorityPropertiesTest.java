package top.egon.cola.platform.tianji.agent.server.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;

import static org.junit.jupiter.api.Assertions.*;

class TianjiToolAuthorityPropertiesTest {
    private static final String PREFIX = "stellar.tianji.agent.tools.authority.";
    private static final ToolDescriptor TOOL = descriptor();
    private static final TianjiToolGrantBindings.BindingVO BINDING = new TianjiToolGrantBindings.BindingVO(21,
            "b".repeat(64), TrustedAgentActor.SubjectType.USER, "review", 1, "c".repeat(64), TOOL.reference(), "TIANJI_TOOL_PROCESS_READ", "process.read");

    @Test void absent_values_keep_disabled_finite_defaults() {
        var properties = new TianjiToolAuthorityProperties(false, null, 0, null, null);
        assertFalse(properties.enabled()); assertEquals(Duration.ofSeconds(5), properties.timeout());
        assertEquals(4, properties.concurrency()); assertEquals(List.of(), properties.descriptors()); assertEquals(List.of(), properties.bindings());
    }

    @Test void timeout_and_concurrency_have_the_pdp_bounds() {
        for (var timeout : List.of(Duration.ofMillis(99), Duration.ofSeconds(10).plusNanos(1), Duration.ZERO, Duration.ofMillis(-1))) {
            assertThrows(IllegalArgumentException.class, () -> new TianjiToolAuthorityProperties(false, timeout, 4, List.of(), List.of()));
        }
        for (int concurrency : new int[]{-1, 9, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new TianjiToolAuthorityProperties(false, Duration.ofSeconds(5), concurrency, List.of(), List.of()));
        }
        for (var timeout : List.of(Duration.ofMillis(100), Duration.ofSeconds(10))) {
            for (int concurrency : new int[]{1, 8}) assertEquals(timeout,
                    new TianjiToolAuthorityProperties(false, timeout, concurrency, List.of(), List.of()).timeout());
        }
    }

    @Test void enabled_authority_requires_both_finite_nonempty_lists() {
        assertThrows(IllegalArgumentException.class, () -> new TianjiToolAuthorityProperties(true, null, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new TianjiToolAuthorityProperties(true, null, 0, List.of(TOOL), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TianjiToolAuthorityProperties(true, null, 0, List.of(), List.of(BINDING)));
        assertThrows(IllegalArgumentException.class, () -> new TianjiToolAuthorityProperties(false, null, 0, Collections.nCopies(257, TOOL), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TianjiToolAuthorityProperties(false, null, 0, List.of(), Collections.nCopies(257, BINDING)));
        assertEquals(256, new TianjiToolAuthorityProperties(false, null, 0, Collections.nCopies(256, TOOL), Collections.nCopies(256, BINDING)).bindings().size());
    }

    @Test void values_copy_the_input_and_refuse_null_elements() {
        var descriptors = new ArrayList<>(List.of(TOOL)); var bindings = new ArrayList<>(List.of(BINDING));
        var properties = new TianjiToolAuthorityProperties(true, Duration.ofSeconds(5), 4, descriptors, bindings);
        descriptors.clear(); bindings.clear();
        assertEquals(List.of(TOOL), properties.descriptors()); assertEquals(List.of(BINDING), properties.bindings());
        assertThrows(UnsupportedOperationException.class, () -> properties.descriptors().clear());
        assertThrows(UnsupportedOperationException.class, () -> properties.bindings().clear());
        assertThrows(RuntimeException.class, () -> new TianjiToolAuthorityProperties(false, null, 0, Collections.singletonList(null), List.of()));
        assertThrows(RuntimeException.class, () -> new TianjiToolAuthorityProperties(false, null, 0, List.of(), Collections.singletonList(null)));
    }

    @Test void spring_binds_the_actual_nested_domain_records() {
        runner().withPropertyValues(values()).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(new TianjiToolAuthorityProperties(true, Duration.ofMillis(750), 2, List.of(TOOL), List.of(BINDING)), context.getBean(TianjiToolAuthorityProperties.class));
        });
    }

    @Test void strict_binding_rejects_unknown_fields_and_invalid_nested_values() {
        runner().withPropertyValues(values()).withPropertyValues(PREFIX + "unexpected=true").run(context -> assertNotNull(context.getStartupFailure()));
        runner().withPropertyValues(values()).withPropertyValues(PREFIX + "descriptors[0].input.fields.query=UNKNOWN").run(context -> assertNotNull(context.getStartupFailure()));
        runner().withPropertyValues(values()).withPropertyValues(PREFIX + "bindings[0].purpose=TIANJI_AGENT_START").run(context -> assertNotNull(context.getStartupFailure()));
        runner().withPropertyValues(PREFIX + "enabled=true").run(context -> assertNotNull(context.getStartupFailure()));
        runner().withPropertyValues(values()).withPropertyValues(PREFIX + "timeout=PT0.099S").run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test void base_yaml_declares_the_same_disabled_defaults() throws Exception {
        var yaml = new YamlPropertySourceLoader().load("base-defaults", new ClassPathResource("application.yml"));
        assertEquals(false, yaml.getFirst().getProperty(PREFIX + "enabled"));
        assertEquals("PT5S", yaml.getFirst().getProperty(PREFIX + "timeout")); assertEquals(4, yaml.getFirst().getProperty(PREFIX + "concurrency"));
        runner().withInitializer(context -> yaml.forEach(source -> context.getEnvironment().getPropertySources().addLast(source))).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(new TianjiToolAuthorityProperties(false, null, 0, null, null), context.getBean(TianjiToolAuthorityProperties.class));
        });
    }

    private static ApplicationContextRunner runner() { return new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class); }
    @EnableConfigurationProperties(TianjiToolAuthorityProperties.class)
    static class PropertiesConfiguration { }
    private static ToolDescriptor descriptor() {
        return new ToolDescriptor("process.read", 1, "process_read", "Read process", new ToolSchema(Map.of("query", ToolSchema.ValueType.STRING), Set.of("query")),
                new ToolSchema(Map.of("found", ToolSchema.ValueType.BOOLEAN), Set.of("found")), new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "process.read", "process", "a".repeat(64), null));
    }
    private static String[] values() {
        return new String[]{PREFIX + "enabled=true", PREFIX + "timeout=750ms", PREFIX + "concurrency=2",
                PREFIX + "descriptors[0].key=process.read", PREFIX + "descriptors[0].version=1", PREFIX + "descriptors[0].name=process_read", PREFIX + "descriptors[0].description=Read process",
                PREFIX + "descriptors[0].input.fields.query=STRING", PREFIX + "descriptors[0].input.required[0]=query",
                PREFIX + "descriptors[0].output.fields.found=BOOLEAN", PREFIX + "descriptors[0].output.required[0]=found",
                PREFIX + "descriptors[0].policy.risk=READ_ONLY", PREFIX + "descriptors[0].policy.action-key=process.read", PREFIX + "descriptors[0].policy.execution-resource-key=process", PREFIX + "descriptors[0].policy.binding-sha256=" + "a".repeat(64),
                PREFIX + "bindings[0].tenant-id=21", PREFIX + "bindings[0].issuer-fingerprint=" + "b".repeat(64), PREFIX + "bindings[0].principal-type=USER",
                PREFIX + "bindings[0].definition-key=review", PREFIX + "bindings[0].definition-version=1", PREFIX + "bindings[0].definition-sha256=" + "c".repeat(64),
                PREFIX + "bindings[0].tool.key=process.read", PREFIX + "bindings[0].tool.version=1", PREFIX + "bindings[0].tool.content-sha256=" + TOOL.reference().contentSha256(),
                PREFIX + "bindings[0].purpose=TIANJI_TOOL_PROCESS_READ", PREFIX + "bindings[0].authorization-resource-key=process.read"};
    }
}
