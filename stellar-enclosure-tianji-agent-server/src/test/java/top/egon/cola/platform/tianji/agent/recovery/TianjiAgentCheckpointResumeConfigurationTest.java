package top.egon.cola.platform.tianji.agent.recovery;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunReconciler;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeCommand;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;
import top.egon.cola.platform.tianji.agent.recovery.TianjiAgentCheckpointResumeConfiguration.ResumeBinding;
import top.egon.cola.platform.tianji.agent.recovery.TianjiAgentCheckpointResumeConfiguration.ResumeHost;
import top.egon.cola.platform.tianji.agent.server.TianjiAgentServerApplication;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantAuthorizer;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TianjiAgentCheckpointResumeConfigurationTest {
    private static final String ROLE = "tianji_checkpoint_resume";
    private static final String BINDING = "tianjiCheckpointResumeBinding";
    private static final String PRIMARY_INVALID = "TIANJI_CHECKPOINT_RESUME_PRIMARY_BINDING_INVALID";

    @Test
    void standalone_shell_keeps_the_resume_host_dormant_even_with_a_binding() {
        var source = mock(DataSource.class);
        new ApplicationContextRunner().withUserConfiguration(TianjiAgentServerApplication.class)
                .withPropertyValues("spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4",
                        "egon.cola.component.id.enabled=false")
                .withBean(BINDING, ResumeBinding.class, () -> new ResumeBinding(source, ROLE, mock(ResumeAuthorization.class)))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ResumeHost.class)
                        .doesNotHaveBean("webServerFactory"));
        verifyNoInteractions(source);
    }

    @Test
    void explicit_import_requires_the_exact_named_binding() throws Exception {
        try (var fixture = new Fixture(new Source("ordinary", true, true), new Source(ROLE, true, false))) {
            fixture.runner().withBean("unrelatedResumeBinding", ResumeBinding.class, fixture::binding)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasMessageContaining(BINDING);
                    });
            verifyNoInteractions(fixture.ordinary.dataSource, fixture.recovery.dataSource);
        }
    }

    @Test
    void explicit_host_pairs_primary_roles_and_keeps_resume_resources_private() throws Exception {
        try (var fixture = new Fixture(new Source("ordinary", true, true), new Source(ROLE, true, false));
             var verifiers = mockConstruction(AgentStorageVerifier.class, (mock, context) ->
                     assertThat(context.arguments()).isEqualTo(java.util.List.of(fixture.recovery.dataSource)))) {
            var host = new AtomicReference<ResumeHost>();
            var actor = TrustedAgentActor.verified(7L, TrustedAgentActor.SubjectType.USER, "owner", "https://idp.example");
            var command = new ResumeCommand("run", "command", 1);
            fixture.bound().run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(ResumeHost.class)
                        .hasSingleBean(DataSource.class).hasSingleBean(AgentLedgerService.class)
                        .doesNotHaveBean(AdkAgentRunExecutor.class).doesNotHaveBean(ResumeAuthorization.class);
                assertThat(context.getBean(DataSource.class)).isSameAs(fixture.ordinary.dataSource);
                assertThat(context.getBean(AgentLedgerService.class)).isSameAs(fixture.ledger);
                host.set(context.getBean(ResumeHost.class));
                when(fixture.reconciler.isRunning()).thenReturn(false);
                var stopped = assertThrows(AgentLedgerException.class, () -> host.get().dispatch(actor, command));
                assertThat(stopped.code()).isEqualTo(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE);
                when(fixture.reconciler.isRunning()).thenReturn(true);
            });
            assertThat(verifiers.constructed()).hasSize(1);
            verify(verifiers.constructed().getFirst()).verifyCheckpointObservation();
            var probeKey = org.mockito.ArgumentCaptor.forClass(Long.class);
            verify(fixture.ordinary.lock).setLong(eq(1), probeKey.capture());
            verify(fixture.recovery.lock).setLong(1, probeKey.getValue());
            fixture.verifyRolledBack();
            verifyNoInteractions(fixture.resumeAuthority);
            verify(fixture.modelAuthority, never()).authorize(any(), any());
            assertThat(fixture.control.isShutdown()).isFalse();
            verify(fixture.recovery.dataSource, times(1)).getConnection();

            var failure = assertThrows(AgentLedgerException.class, () -> host.get().dispatch(actor, command));
            assertThat(failure.code()).isEqualTo(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE);
        }
    }

    @Test
    void a_same_named_database_without_shared_lock_is_rejected() throws Exception {
        try (var fixture = new Fixture(new Source("ordinary", true, true), new Source(ROLE, true, true));
             var verifiers = mockConstruction(AgentStorageVerifier.class)) {
            fixture.bound().run(context -> assertThat(context.getStartupFailure()).hasRootCauseMessage(PRIMARY_INVALID));
            fixture.verifyRolledBack();
            assertThat(verifiers.constructed()).isEmpty();
        }
    }

    @Test
    void wrong_effective_role_and_non_writable_primary_fail_before_locking() throws Exception {
        for (var recovery : new Source[]{new Source("ordinary", true, false), new Source("other_role", true, false),
                new Source(ROLE, false, false)}) {
            try (var fixture = new Fixture(new Source("ordinary", true, true), recovery);
                 var verifiers = mockConstruction(AgentStorageVerifier.class)) {
                fixture.bound().run(context -> assertThat(context.getStartupFailure()).hasRootCauseMessage(PRIMARY_INVALID));
                fixture.verifyRolledBack();
                verify(fixture.ordinary.lock, never()).setLong(eq(1), anyLong());
                verify(fixture.recovery.lock, never()).setLong(eq(1), anyLong());
                assertThat(verifiers.constructed()).isEmpty();
            }
        }
    }

    @Test
    void rollback_and_connection_return_follow_a_probe_sql_failure() throws Exception {
        try (var fixture = new Fixture(new Source("ordinary", true, true), new Source(ROLE, true, false));
             var verifiers = mockConstruction(AgentStorageVerifier.class)) {
            when(fixture.recovery.lock.executeQuery()).thenThrow(new SQLException("private"));
            fixture.bound().run(context -> assertThat(context.getStartupFailure()).hasRootCauseMessage(PRIMARY_INVALID));
            fixture.verifyRolledBack();
            assertThat(verifiers.constructed()).isEmpty();
        }
        try (var fixture = new Fixture(new Source("ordinary", true, true), new Source(ROLE, true, true));
             var verifiers = mockConstruction(AgentStorageVerifier.class)) {
            doThrow(new SQLException("private rollback detail")).when(fixture.recovery.connection).rollback();
            fixture.bound().run(context -> {
                var failure = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure());
                assertThat(failure).hasMessage(PRIMARY_INVALID);
                assertThat(failure.getSuppressed()).isEmpty();
            });
            verify(fixture.recovery.connection).rollback();
            verify(fixture.recovery.connection, never()).setAutoCommit(true);
            verify(fixture.recovery.connection).close();
            verify(fixture.ordinary.connection).rollback();
            verify(fixture.ordinary.connection).setAutoCommit(true);
            verify(fixture.ordinary.connection).close();
            assertThat(verifiers.constructed()).isEmpty();
        }
    }

    @Test
    void model_authority_reconciler_and_v15_guards_run_before_opening_either_pool() throws Exception {
        try (var fixture = new Fixture(new Source("ordinary", true, true), new Source(ROLE, true, false))) {
            when(fixture.modelAuthority.usesLedger(fixture.ledger)).thenReturn(false);
            fixture.bound().run(context -> assertThat(context.getStartupFailure())
                    .hasRootCauseMessage("TIANJI_CHECKPOINT_RESUME_HOST_PREREQUISITES_INVALID"));
            when(fixture.modelAuthority.usesLedger(fixture.ledger)).thenReturn(true);
            when(fixture.reconciler.usesLedger(fixture.ledger)).thenReturn(false);
            fixture.bound().run(context -> assertThat(context.getStartupFailure())
                    .hasRootCauseMessage("TIANJI_CHECKPOINT_RESUME_HOST_PREREQUISITES_INVALID"));
            when(fixture.reconciler.usesLedger(fixture.ledger)).thenReturn(true);
            fixture.bound().withPropertyValues("stellar.tianji.agent.reconciliation.enabled=false")
                    .run(context -> assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("TIANJI_CHECKPOINT_RESUME_HOST_PREREQUISITES_INVALID"));
            fixture.bound().withPropertyValues("stellar.tianji.agent.storage.model-usage-enabled=true")
                    .run(context -> assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("TIANJI_CHECKPOINT_RESUME_MODEL_USAGE_UNAVAILABLE"));
            verifyNoInteractions(fixture.ordinary.dataSource, fixture.recovery.dataSource);
        }
    }

    @Test
    void a_paired_role_that_fails_checkpoint_verification_never_exposes_the_host() throws Exception {
        try (var fixture = new Fixture(new Source("ordinary", true, true), new Source(ROLE, true, false));
             var verifiers = mockConstruction(AgentStorageVerifier.class, (mock, context) ->
                     doThrow(new IllegalStateException("checkpoint-role-invalid")).when(mock).verifyCheckpointObservation())) {
            fixture.bound().run(context -> assertThat(context.getStartupFailure())
                    .hasRootCauseMessage("checkpoint-role-invalid"));
            assertThat(verifiers.constructed()).hasSize(1);
            fixture.verifyRolledBack();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Source ordinary;
        private final Source recovery;
        private final AgentLedgerService ledger;
        private final ResumeAuthorization resumeAuthority = mock(ResumeAuthorization.class);
        private final TianjiExecutionGrantAuthorizer modelAuthority = mock(TianjiExecutionGrantAuthorizer.class);
        private final AgentRunReconciler reconciler = mock(AgentRunReconciler.class);
        private final ScheduledThreadPoolExecutor control = new ScheduledThreadPoolExecutor(1);

        private Fixture(Source ordinary, Source recovery) {
            this.ordinary = ordinary;
            this.recovery = recovery;
            ledger = new AgentLedgerService(ordinary.dataSource);
            when(modelAuthority.usesLedger(ledger)).thenReturn(true);
            when(reconciler.usesLedger(ledger)).thenReturn(true);
            when(reconciler.isRunning()).thenReturn(true);
        }

        private ApplicationContextRunner runner() {
            return new ApplicationContextRunner()
                    .withUserConfiguration(TianjiAgentCheckpointResumeConfiguration.class)
                    .withPropertyValues("stellar.tianji.agent.storage.enabled=true",
                            "stellar.tianji.agent.runtime.enabled=true",
                            "stellar.tianji.agent.execution-authorization.enabled=true",
                            "stellar.tianji.agent.reconciliation.enabled=true")
                    .withBean(DataSource.class, () -> ordinary.dataSource)
                    .withBean(AgentLedgerService.class, () -> ledger)
                    .withBean(AgentRunReconciler.class, () -> reconciler)
                    .withBean(TianjiExecutionGrantAuthorizer.class, () -> modelAuthority, bean -> bean.setDestroyMethodName(""))
                    .withBean("tianjiAgentControlExecutor", ScheduledThreadPoolExecutor.class, () -> control,
                            bean -> bean.setDestroyMethodName(""))
                    .withBean(AgentFlowFactory.class, () -> mock(AgentFlowFactory.class))
                    .withBean(AgentFlowConfigValidator.class, () -> mock(AgentFlowConfigValidator.class))
                    .withBean(AgentModelBudgetEstimator.class, () -> mock(AgentModelBudgetEstimator.class))
                    .withBean("agentFlowChatModels", Map.class, () -> Map.of("fixture", mock(ChatModel.class)));
        }

        private ResumeBinding binding() { return new ResumeBinding(recovery.dataSource, ROLE, resumeAuthority); }

        private ApplicationContextRunner bound() { return runner().withBean(BINDING, ResumeBinding.class, this::binding); }

        private void verifyRolledBack() throws SQLException {
            for (var source : new Source[]{ordinary, recovery}) {
                var order = org.mockito.Mockito.inOrder(source.connection);
                order.verify(source.connection).setAutoCommit(false);
                order.verify(source.connection).rollback();
                order.verify(source.connection).setAutoCommit(true);
                order.verify(source.connection).close();
            }
        }

        @Override public void close() { control.shutdownNow(); }
    }

    private static final class Source {
        private final DataSource dataSource = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final PreparedStatement lock = mock(PreparedStatement.class);

        private Source(String role, boolean writable, boolean acquired) throws SQLException {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.createStatement()).thenReturn(mock(Statement.class));
            var roleQuery = mock(PreparedStatement.class);
            when(connection.prepareStatement(contains("SELECT current_database()"))).thenReturn(roleQuery);
            when(connection.prepareStatement(contains("pg_try_advisory_xact_lock"))).thenReturn(lock);
            var roleRows = mock(ResultSet.class);
            when(roleRows.next()).thenReturn(writable, false);
            when(roleRows.getString(1)).thenReturn("agent_fixture");
            when(roleRows.getString(2)).thenReturn(role);
            when(roleQuery.executeQuery()).thenReturn(roleRows);
            var lockRows = mock(ResultSet.class);
            when(lockRows.next()).thenReturn(true, false);
            when(lockRows.getBoolean(1)).thenReturn(acquired);
            when(lock.executeQuery()).thenReturn(lockRows);
        }
    }
}
