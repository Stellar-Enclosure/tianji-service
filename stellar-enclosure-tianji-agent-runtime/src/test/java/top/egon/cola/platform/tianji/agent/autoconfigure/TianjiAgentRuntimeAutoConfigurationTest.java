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
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkGovernedProcessToolBridge;
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
    void disabled_tools_never_resolve_either_provider_including_a_null_legacy_fixture() {
        var governedProvider = governedProcessToolProvider();
        var readOnlyProvider = readOnlyDecisionToolProvider();
        var control = control();
        try {
            var worker = worker(new MockEnvironment(), governedProvider, readOnlyProvider, control);
            assertThat(worker.usesReadOnlyDecisionTools(mock(AdkReadOnlyDecisionToolBridge.class))).isFalse();
            assertThat(worker.usesGovernedProcessTools(mock(AdkGovernedProcessToolBridge.class))).isFalse();
            verifyNoInteractions(governedProvider, readOnlyProvider);
            var legacy = worker(new MockEnvironment(), governedProvider, null, control);
            assertThat(legacy.usesReadOnlyDecisionTools(mock(AdkReadOnlyDecisionToolBridge.class))).isFalse();
            assertThat(legacy.usesGovernedProcessTools(mock(AdkGovernedProcessToolBridge.class))).isFalse();
            verifyNoInteractions(governedProvider);
        } finally {
            control.shutdownNow();
        }
    }

    @Test
    void read_only_mode_resolves_only_its_provider_lazily_and_caches_the_exact_bridge() {
        var governedProvider = governedProcessToolProvider();
        var readOnlyProvider = readOnlyDecisionToolProvider();
        var bridge = mock(AdkReadOnlyDecisionToolBridge.class);
        when(readOnlyProvider.getObject()).thenReturn(bridge);
        var control = control();
        try {
            var environment = new MockEnvironment().withProperty("stellar.tianji.agent.tools.read-only-decision.enabled", "true");
            var worker = worker(environment, governedProvider, readOnlyProvider, control);
            verifyNoInteractions(governedProvider, readOnlyProvider);
            assertThat(worker.usesReadOnlyDecisionTools(bridge)).isTrue();
            assertThat(worker.usesReadOnlyDecisionTools(bridge)).isTrue();
            assertThat(worker.usesReadOnlyDecisionTools(mock(AdkReadOnlyDecisionToolBridge.class))).isFalse();
            assertThat(worker.usesGovernedProcessTools(mock(AdkGovernedProcessToolBridge.class))).isFalse();
            verify(readOnlyProvider, times(1)).getObject();
            verifyNoInteractions(governedProvider);
        } finally {
            control.shutdownNow();
        }
    }

    @Test
    void governed_process_mode_resolves_only_its_provider_lazily_and_caches_the_exact_bridge() {
        var governedProvider = governedProcessToolProvider();
        var readOnlyProvider = readOnlyDecisionToolProvider();
        var bridge = mock(AdkGovernedProcessToolBridge.class);
        when(governedProvider.getObject()).thenReturn(bridge);
        var control = control();
        try {
            var environment = new MockEnvironment().withProperty("stellar.tianji.agent.tools.governed-process.enabled", "true");
            var worker = worker(environment, governedProvider, readOnlyProvider, control);
            verifyNoInteractions(governedProvider, readOnlyProvider);
            assertThat(worker.usesGovernedProcessTools(bridge)).isTrue();
            assertThat(worker.usesGovernedProcessTools(bridge)).isTrue();
            assertThat(worker.usesGovernedProcessTools(mock(AdkGovernedProcessToolBridge.class))).isFalse();
            assertThat(worker.usesReadOnlyDecisionTools(mock(AdkReadOnlyDecisionToolBridge.class))).isFalse();
            verify(governedProvider, times(1)).getObject();
            verifyNoInteractions(readOnlyProvider);
        } finally {
            control.shutdownNow();
        }
    }

    @Test
    void mutually_enabled_modes_fail_before_owned_storage_context_or_provider_resolution() {
        var governedProvider = governedProcessToolProvider();
        var readOnlyProvider = readOnlyDecisionToolProvider();
        var contexts = contextProvider();
        var dataSource = mock(DataSource.class);
        var ledger = mock(AgentLedgerService.class);
        when(ledger.usesDataSource(dataSource)).thenReturn(true);
        var control = control();
        try {
            var environment = new MockEnvironment()
                    .withProperty("stellar.tianji.agent.tools.governed-process.enabled", "true")
                    .withProperty("stellar.tianji.agent.tools.read-only-decision.enabled", "true");
            var failure = assertThrows(IllegalStateException.class,
                    () -> new TianjiAgentRuntimeAutoConfiguration().tianjiAdkAgentRunExecutor(ledger, dataSource,
                            mock(AgentFlowFactory.class), mock(AgentFlowConfigValidator.class),
                            Map.of("model", mock(ChatModel.class)), Map.of(), control,
                            mock(AgentModelBudgetEstimator.class),
                            (request, boundary) -> AgentRunAuthorization.Decision.ALLOW, environment, contexts,
                            governedProvider, readOnlyProvider));
            org.junit.jupiter.api.Assertions.assertAll(
                    () -> org.junit.jupiter.api.Assertions.assertEquals("TIANJI_TOOL_RUNTIME_MODE_CONFLICT", failure.getMessage()),
                    () -> verifyNoInteractions(dataSource, contexts, governedProvider, readOnlyProvider));
        } finally {
            control.shutdownNow();
        }
    }

    @Test
    void tool_providers_use_the_exact_host_bean_qualifiers_and_keep_read_only_last() {
        var factory = Arrays.stream(TianjiAgentRuntimeAutoConfiguration.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("tianjiAdkAgentRunExecutor")).findFirst().orElseThrow();
        var governedProvider = factory.getParameters()[factory.getParameterCount() - 2];
        var readOnlyProvider = factory.getParameters()[factory.getParameterCount() - 1];
        assertThat(governedProvider.getType()).isEqualTo(ObjectProvider.class);
        assertThat(governedProvider.getAnnotation(Qualifier.class).value()).isEqualTo("tianjiAdkGovernedProcessTools");
        assertThat(readOnlyProvider.getType()).isEqualTo(ObjectProvider.class);
        assertThat(readOnlyProvider.getAnnotation(Qualifier.class).value()).isEqualTo("tianjiAdkReadOnlyDecisionTools");
        assertThat(AdkAgentRunExecutor.class.getConstructors()).hasSize(8);
    }

    private static AdkAgentRunExecutor worker(MockEnvironment environment,
            ObjectProvider<AdkGovernedProcessToolBridge> governedProvider,
            ObjectProvider<AdkReadOnlyDecisionToolBridge> readOnlyProvider, ScheduledThreadPoolExecutor control) {
        var dataSource = mock(DataSource.class);
        var ledger = mock(AgentLedgerService.class);
        when(ledger.usesDataSource(dataSource)).thenReturn(true);
        environment.withProperty("stellar.tianji.agent.context.snapshots.enabled", "true");
        return new TianjiAgentRuntimeAutoConfiguration().tianjiAdkAgentRunExecutor(ledger, dataSource,
                mock(AgentFlowFactory.class), mock(AgentFlowConfigValidator.class), Map.of("model", mock(ChatModel.class)),
                Map.of(), control, mock(AgentModelBudgetEstimator.class),
                (request, boundary) -> AgentRunAuthorization.Decision.ALLOW, environment,
                new DefaultListableBeanFactory().getBeanProvider(DefaultContextAssembler.class),
                governedProvider, readOnlyProvider);
    }

    private static ScheduledThreadPoolExecutor control() {
        var control = new ScheduledThreadPoolExecutor(1);
        control.setRemoveOnCancelPolicy(true);
        return control;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AdkGovernedProcessToolBridge> governedProcessToolProvider() {
        return mock(ObjectProvider.class);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AdkReadOnlyDecisionToolBridge> readOnlyDecisionToolProvider() {
        return mock(ObjectProvider.class);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<DefaultContextAssembler> contextProvider() {
        return mock(ObjectProvider.class);
    }
}
