package top.egon.cola.platform.tianji.agent.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.platform.tianji.contract.agent.AgentClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TianjiAgentServerApplicationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TianjiAgentServerApplication.class);

    @Test
    void default_shell_has_health_without_exposing_an_agent_client_or_web_stack() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(HealthEndpoint.class);
            assertThat(context).doesNotHaveBean(AgentClient.class);
            assertThat(context).doesNotHaveBean("webServerFactory");
        });
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.springframework.web.servlet.DispatcherServlet"));
    }

    @Test
    void runtime_cannot_be_enabled_before_its_dependencies_exist() {
        runner.withPropertyValues("stellar.tianji.agent.runtime.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
