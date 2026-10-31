package top.egon.cola.platform.tianji.agent.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.ai.chat.model.ChatModel;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkReadOnlyDecisionToolBridge;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentClient;
import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

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
    void enabling_runtime_without_host_dependencies_fails_closed() {
        runner.withPropertyValues("stellar.tianji.agent.runtime.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("DataSource");
        });
    }

    @Test
    void runtime_does_not_include_flowable() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.flowable.engine.ProcessEngine"));
    }

    @Test
    void disabled_tools_never_resolve_the_provider_including_a_null_legacy_fixture() {
        var provider = toolProvider();
        var control = control();
        try {
            var worker = worker(new MockEnvironment(), provider, control);
            assertThat(worker.usesReadOnlyDecisionTools(mock(AdkReadOnlyDecisionToolBridge.class))).isFalse();
            verifyNoInteractions(provider);
            var legacy = worker(new MockEnvironment(), null, control);
            assertThat(legacy.usesReadOnlyDecisionTools(mock(AdkReadOnlyDecisionToolBridge.class))).isFalse();
        } finally {
            control.shutdownNow();
        }
    }

    @Test
    void enabled_tools_resolve_lazily_and_cache_the_exact_qualified_bridge() {
        var provider = toolProvider();
        var bridge = mock(AdkReadOnlyDecisionToolBridge.class);
        when(provider.getObject()).thenReturn(bridge);
        var control = control();
        try {
            var environment = new MockEnvironment().withProperty("stellar.tianji.agent.tools.read-only-decision.enabled", "true");
            var worker = worker(environment, provider, control);
            verifyNoInteractions(provider);
            assertThat(worker.usesReadOnlyDecisionTools(bridge)).isTrue();
            assertThat(worker.usesReadOnlyDecisionTools(bridge)).isTrue();
            assertThat(worker.usesReadOnlyDecisionTools(mock(AdkReadOnlyDecisionToolBridge.class))).isFalse();
            verify(provider, times(1)).getObject();
        } finally {
            control.shutdownNow();
        }
    }

    @Test
    void tool_provider_uses_the_exact_host_bean_qualifier() {
        var factory = Arrays.stream(TianjiAgentRuntimeAutoConfiguration.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("tianjiAdkAgentRunExecutor")).findFirst().orElseThrow();
        var provider = factory.getParameters()[factory.getParameterCount() - 1];
        assertThat(provider.getType()).isEqualTo(ObjectProvider.class);
        assertThat(provider.getAnnotation(Qualifier.class).value()).isEqualTo("tianjiAdkReadOnlyDecisionTools");
    }

    private static AdkAgentRunExecutor worker(MockEnvironment environment,
            ObjectProvider<AdkReadOnlyDecisionToolBridge> provider, ScheduledThreadPoolExecutor control) {
        var dataSource = mock(DataSource.class);
        var ledger = mock(AgentLedgerService.class);
        when(ledger.usesDataSource(dataSource)).thenReturn(true);
        environment.withProperty("stellar.tianji.agent.context.snapshots.enabled", "true");
        return new TianjiAgentRuntimeAutoConfiguration().tianjiAdkAgentRunExecutor(ledger, dataSource,
                mock(AgentFlowFactory.class), mock(AgentFlowConfigValidator.class), Map.of("model", mock(ChatModel.class)),
                Map.of(), control, mock(AgentModelBudgetEstimator.class),
                (request, boundary) -> AgentRunAuthorization.Decision.ALLOW, environment,
                new DefaultListableBeanFactory().getBeanProvider(DefaultContextAssembler.class), provider);
    }

    private static ScheduledThreadPoolExecutor control() {
        var control = new ScheduledThreadPoolExecutor(1);
        control.setRemoveOnCancelPolicy(true);
        return control;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AdkReadOnlyDecisionToolBridge> toolProvider() {
        return mock(ObjectProvider.class);
    }
}
