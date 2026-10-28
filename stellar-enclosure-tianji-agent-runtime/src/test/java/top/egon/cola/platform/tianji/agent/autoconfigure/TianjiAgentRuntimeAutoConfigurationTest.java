package top.egon.cola.platform.tianji.agent.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.platform.tianji.contract.agent.AgentClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TianjiAgentRuntimeAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TianjiAgentRuntimeAutoConfiguration.class));

    @Test
    void runtime_is_disabled_by_default() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AgentClient.class);
        });
    }

    @Test
    void enabling_an_unavailable_runtime_fails_closed() {
        runner.withPropertyValues("stellar.tianji.agent.runtime.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasStackTraceContaining("persistence and trusted identity integration are required");
        });
    }

    @Test
    void runtime_does_not_include_flowable() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.flowable.engine.ProcessEngine"));
    }
}
