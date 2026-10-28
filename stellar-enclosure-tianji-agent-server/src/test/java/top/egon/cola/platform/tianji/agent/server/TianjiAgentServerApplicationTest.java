package top.egon.cola.platform.tianji.agent.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.platform.tianji.contract.agent.AgentClient;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class TianjiAgentServerApplicationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TianjiAgentServerApplication.class)
            .withPropertyValues("spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4",
                    "egon.cola.component.id.enabled=false");

    @Test
    void default_shell_has_health_without_exposing_an_agent_client_or_web_server() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(HealthEndpoint.class);
            assertThat(context).doesNotHaveBean(AgentClient.class);
            assertThat(context).doesNotHaveBean(DataSource.class);
            assertThat(context).doesNotHaveBean("webServerFactory");
        });
    }

    @Test
    void runtime_cannot_be_enabled_before_its_dependencies_exist() {
        runner.withPropertyValues("stellar.tianji.agent.runtime.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void server_classpath_contains_the_agent_flow_runtime_used_by_the_executor() throws Exception {
        assertThat(Class.forName("top.egon.cola.component.agentflow.runtime.AgentFlowInMemoryRunner")).isNotNull();
        assertThat(Class.forName("top.egon.cola.component.agentflow.runtime.AgentFlowSessionRunner")).isNotNull();
    }
}
