package top.egon.cola.platform.tianji.process.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import top.egon.cola.platform.tianji.process.server.api.ProcessController;
import top.egon.cola.platform.tianji.process.server.api.WorkerController;
import top.egon.cola.platform.tianji.process.server.api.AgentResultInboxController;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultInbox;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStore;
import top.egon.cola.platform.tianji.process.server.decision.ProcessDecisionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import org.flowable.engine.ProcessEngine;
import org.flowable.dmn.engine.DmnEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessApiConfigurationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner().withUserConfiguration(TestWeb.class)
            .withPropertyValues("egon.cola.platform.tianquan.shoubing.register-filter=false",
                    "egon.cola.platform.tianquan.jianshen.register-filter=false");

    @Test
    void api_is_absent_by_default_and_fails_closed_when_filters_are_missing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ProcessController.class);
            assertThat(context).doesNotHaveBean(WorkerController.class);
            assertThat(context).doesNotHaveBean(AgentResultInboxController.class);
        });
        runner.withPropertyValues("stellar.tianji.process.worker.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("Worker API requires the Process API security boundary");
        });
        runner.withPropertyValues("stellar.tianji.process.agent-results.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("Agent result Inbox requires the Process API security boundary");
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
    void inbox_enablement_requires_an_operator_owned_producer_registry() {
        runner.withPropertyValues("stellar.tianji.process.api.enabled=true", "stellar.tianji.process.agent-results.enabled=true")
                .withBean(PermissionExecutionGuard.class, () -> mock(PermissionExecutionGuard.class))
                .withBean(IdpBearerAuthenticationFilter.class, () -> mock(IdpBearerAuthenticationFilter.class))
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> mock(Rbac3BearerAuthenticationFilter.class))
                .withBean(ProcessReleaseService.class, () -> mock(ProcessReleaseService.class))
                .withBean(ProcessExecutionService.class, () -> mock(ProcessExecutionService.class))
                .withBean(ProcessDecisionService.class, () -> mock(ProcessDecisionService.class))
                .withBean(ProcessApiSchemaVerifier.class, () -> mock(ProcessApiSchemaVerifier.class))
                .withBean(AgentDispatchStore.class, () -> mock(AgentDispatchStore.class))
                .withBean(AgentResultInbox.class, () -> mock(AgentResultInbox.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage("Agent result producer binding is invalid");
                });
    }

    @Test
    void enabled_worker_api_requires_an_operator_owned_registration() {
        runner.withPropertyValues("stellar.tianji.process.api.enabled=true", "stellar.tianji.process.worker.enabled=true")
                .withBean(PermissionExecutionGuard.class, () -> mock(PermissionExecutionGuard.class))
                .withBean(IdpBearerAuthenticationFilter.class, () -> mock(IdpBearerAuthenticationFilter.class))
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> mock(Rbac3BearerAuthenticationFilter.class))
                .withBean(ProcessReleaseService.class, () -> mock(ProcessReleaseService.class))
                .withBean(ProcessExecutionService.class, () -> mock(ProcessExecutionService.class))
                .withBean(ProcessDecisionService.class, () -> mock(ProcessDecisionService.class))
                .withBean(ProcessApiSchemaVerifier.class, () -> mock(ProcessApiSchemaVerifier.class))
                .withBean(FlowableWorkerService.class, () -> mock(FlowableWorkerService.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage("Worker topic registration is invalid");
                });
    }

    @Test
    void enabled_worker_api_rejects_a_disabled_native_executor() {
        var engine = mock(ProcessEngine.class);
        var configuration = mock(SpringProcessEngineConfiguration.class);
        when(engine.getProcessEngineConfiguration()).thenReturn(configuration);
        runner.withPropertyValues("stellar.tianji.process.api.enabled=true", "stellar.tianji.process.worker.enabled=true")
                .withBean(PermissionExecutionGuard.class, () -> mock(PermissionExecutionGuard.class))
                .withBean(IdpBearerAuthenticationFilter.class, () -> mock(IdpBearerAuthenticationFilter.class))
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> mock(Rbac3BearerAuthenticationFilter.class))
                .withBean(ProcessReleaseService.class, () -> mock(ProcessReleaseService.class))
                .withBean(ProcessExecutionService.class, () -> mock(ProcessExecutionService.class))
                .withBean(ProcessDecisionService.class, () -> mock(ProcessDecisionService.class))
                .withBean(ProcessApiSchemaVerifier.class, () -> mock(ProcessApiSchemaVerifier.class))
                .withBean(ProcessEngine.class, () -> engine)
                .withBean(DmnEngine.class, () -> mock(DmnEngine.class))
                .withBean(ProcessModelValidator.class, ProcessModelValidator::new)
                .withBean(WorkerTopicRegistry.class, () -> mock(WorkerTopicRegistry.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "Worker acquisition requires the native job executor and expired-lock reset");
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
        assertThat(policy.requirement(new MockHttpServletRequest("POST", "/api/tianji/v1/worker-tasks/acquire")))
                .isEqualTo(IdpEndpointAuthenticationPolicy.Requirement.SERVICE);
        assertThat(policy.requirement(new MockHttpServletRequest("POST", "/api/tianji/v1/worker-tasks/complete")))
                .isEqualTo(IdpEndpointAuthenticationPolicy.Requirement.SERVICE);
        assertThat(policy.requirement(new MockHttpServletRequest("POST", "/api/tianji/v1/agent-result-inbox")))
                .isEqualTo(IdpEndpointAuthenticationPolicy.Requirement.SERVICE);
        assertThat(policy.requirement(new MockHttpServletRequest("GET", "/actuator/health")))
                .isEqualTo(IdpEndpointAuthenticationPolicy.Requirement.PUBLIC);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ProcessSecurityConfiguration.class, ProcessController.class, WorkerController.class, AgentResultInboxController.class})
    static class TestWeb { }
}
