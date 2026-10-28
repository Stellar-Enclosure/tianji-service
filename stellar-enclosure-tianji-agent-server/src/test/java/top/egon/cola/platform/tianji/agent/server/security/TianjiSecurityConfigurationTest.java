package top.egon.cola.platform.tianji.agent.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.SecurityFilterChain;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.TianjiAgentServerApplication;
import top.egon.cola.platform.tianji.agent.server.api.AgentRunController;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.CurrentEffectivePermissionView;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TianjiSecurityConfigurationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(TianjiAgentServerApplication.class)
            .withPropertyValues("spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4",
                    "egon.cola.component.id.enabled=false",
                    "egon.cola.platform.tianquan.shoubing.register-filter=false",
                    "egon.cola.platform.tianquan.jianshen.register-filter=false");

    @Test
    void default_web_context_does_not_install_agent_endpoints() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
            assertThat(context).doesNotHaveBean(AgentRunController.class);
        });
    }

    @Test
    void enabling_api_without_both_identity_filters_fails_closed() {
        runner.withPropertyValues("stellar.tianji.agent.api.enabled=true")
                .withBean(AgentLedgerService.class, () -> mock(AgentLedgerService.class))
                .withBean(PermissionExecutionGuard.class, () -> mock(PermissionExecutionGuard.class))
                .withBean(CurrentEffectivePermissionView.class, () -> mock(CurrentEffectivePermissionView.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "Tianji Agent API requires both identity and permission filters in its security chain");
                });
    }

    @Test
    void identity_policy_distinguishes_shared_agent_paths_from_user_only_paths() {
        var policy = new TianjiAgentSecurityConfiguration().tianjiEndpointAuthenticationPolicy();
        assertThat(policy.requirement(new MockHttpServletRequest("GET", "/api/tianji/v1/agent-runs/run-1")))
                .isEqualTo(IdpEndpointAuthenticationPolicy.Requirement.USER_OR_SERVICE);
        assertThat(policy.requirement(new MockHttpServletRequest("GET", "/actuator/health")))
                .isEqualTo(IdpEndpointAuthenticationPolicy.Requirement.PUBLIC);
        assertThat(policy.requirement(new MockHttpServletRequest("GET", "/unclassified")))
                .isEqualTo(IdpEndpointAuthenticationPolicy.Requirement.USER);
    }
}
