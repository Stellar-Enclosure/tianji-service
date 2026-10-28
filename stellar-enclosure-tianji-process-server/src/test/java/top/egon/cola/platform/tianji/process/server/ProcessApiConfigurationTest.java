package top.egon.cola.platform.tianji.process.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import top.egon.cola.platform.tianji.process.server.api.ProcessController;
import top.egon.cola.platform.tianji.process.server.decision.ProcessDecisionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProcessApiConfigurationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner().withUserConfiguration(TestWeb.class)
            .withPropertyValues("egon.cola.platform.tianquan.shoubing.register-filter=false",
                    "egon.cola.platform.tianquan.jianshen.register-filter=false");

    @Test
    void api_is_absent_by_default_and_fails_closed_when_filters_are_missing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ProcessController.class);
        });
        runner.withPropertyValues("stellar.tianji.process.api.enabled=true")
                .withBean(PermissionExecutionGuard.class, () -> mock(PermissionExecutionGuard.class))
                .withBean(ProcessReleaseService.class, () -> mock(ProcessReleaseService.class))
                .withBean(ProcessExecutionService.class, () -> mock(ProcessExecutionService.class))
                .withBean(ProcessDecisionService.class, () -> mock(ProcessDecisionService.class))
                .withBean(ProcessApiSchemaVerifier.class, () -> mock(ProcessApiSchemaVerifier.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "Tianji Process API requires both identity and permission filters in its security chain");
                });
    }

    @Test
    void identity_policy_requires_user_or_service_for_process_routes() {
        var policy = new ProcessSecurityConfiguration().processEndpointAuthenticationPolicy();
        assertThat(policy.requirement(new MockHttpServletRequest("POST", "/api/tianji/v1/process-releases")))
                .isEqualTo(IdpEndpointAuthenticationPolicy.Requirement.USER_OR_SERVICE);
        assertThat(policy.requirement(new MockHttpServletRequest("GET", "/api/tianji/v1/process-instances/one")))
                .isEqualTo(IdpEndpointAuthenticationPolicy.Requirement.USER_OR_SERVICE);
        assertThat(policy.requirement(new MockHttpServletRequest("POST", "/api/tianji/v1/decisions/evaluate")))
                .isEqualTo(IdpEndpointAuthenticationPolicy.Requirement.USER_OR_SERVICE);
        assertThat(policy.requirement(new MockHttpServletRequest("GET", "/actuator/health")))
                .isEqualTo(IdpEndpointAuthenticationPolicy.Requirement.PUBLIC);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ProcessSecurityConfiguration.class, ProcessController.class})
    static class TestWeb { }
}
