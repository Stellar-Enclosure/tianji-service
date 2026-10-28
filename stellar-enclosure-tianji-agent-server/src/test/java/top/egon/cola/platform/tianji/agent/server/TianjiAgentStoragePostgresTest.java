package top.egon.cola.platform.tianji.agent.server;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Uses separate disposable loopback databases and never starts an application server. */
@EnabledIfSystemProperty(named = "tianji.agent.storage.postgres.test.url", matches = ".+")
@Timeout(60)
class TianjiAgentStoragePostgresTest {
    @Test void explicit_storage_uses_boot_datasource_and_a_preprovisioned_schema() {
        String url = testUrl("tianji.agent.storage.postgres.test.url");
        var migrationSource = new DriverManagerDataSource(url);
        var jdbc = new JdbcTemplate(migrationSource);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class));
        var flyway = Flyway.configure().dataSource(migrationSource).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").cleanDisabled(true).load();
        flyway.migrate(); flyway.validate();

        runner(url).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DataSource.class).hasSingleBean(AgentLedgerService.class);
            var source = context.getBean(DataSource.class);
            assertThat(source).isInstanceOf(com.zaxxer.hikari.HikariDataSource.class);
            assertThat(context.getBean(AgentLedgerService.class).usesDataSource(source)).isTrue();
            assertThat(context.getBean(AgentLedgerService.class).databaseNow()).isNotNull();
        });
        runner(url).withBean(AgentLedgerService.class, () -> new AgentLedgerService(migrationSource)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("TIANJI_AGENT_STORAGE_DATA_SOURCE_MISMATCH");
        });
        runtimeRunner(url).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AgentLedgerService.class)
                    .hasSingleBean(top.egon.cola.platform.tianji.agent.application.run.AgentRunDispatcher.class);
            var ledger = context.getBean(AgentLedgerService.class);
            assertThat(ledger.usesDataSource(context.getBean(DataSource.class))).isTrue();
            assertThat(context.getBean(top.egon.cola.platform.tianji.agent.application.run.AgentRunDispatcher.class)
                    .usesLedger(ledger)).isTrue();
            assertThat(context.getBean("tianjiAgentDispatchExecutor", java.util.concurrent.ThreadPoolExecutor.class)
                    .getTaskCount()).isZero();
        });
        var dispatch = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0, java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(2));
        var control = new java.util.concurrent.ScheduledThreadPoolExecutor(1);
        control.setRemoveOnCancelPolicy(true);
        try {
            runtimeRunner(url)
                    .withBean("tianjiAgentDispatchExecutor", java.util.concurrent.ThreadPoolExecutor.class, () -> dispatch)
                    .withBean("tianjiAgentControlExecutor", java.util.concurrent.ScheduledThreadPoolExecutor.class, () -> control)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(AgentLedgerService.class)
                                .hasSingleBean(top.egon.cola.platform.tianji.agent.application.run.AgentRunDispatcher.class);
                        var ledger = context.getBean(AgentLedgerService.class);
                        assertThat(ledger.usesDataSource(context.getBean(DataSource.class))).isTrue();
                        assertThat(context.getBean(top.egon.cola.platform.tianji.agent.application.run.AgentRunDispatcher.class)
                                .usesLedger(ledger)).isTrue();
                    });
        } finally { dispatch.shutdownNow(); control.shutdownNow(); }
    }

    @Test void refuses_an_unprovisioned_database_without_creating_the_schema() {
        String url = testUrl("tianji.agent.storage.empty.postgres.test.url");
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class));
        runner(url).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Agent storage verification failed");
        });
        runner(url).withInitializer(context -> context.addBeanFactoryPostProcessor(
                new org.springframework.boot.LazyInitializationBeanFactoryPostProcessor())).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Agent storage verification failed");
        });
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class));
    }

    private static ApplicationContextRunner runner(String url) {
        return new ApplicationContextRunner().withUserConfiguration(TianjiAgentServerApplication.class)
                .withPropertyValues("stellar.tianji.agent.storage.enabled=true", "spring.datasource.url=" + url,
                        "spring.flyway.enabled=false", "spring.sql.init.mode=never",
                        "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4",
                        "egon.cola.component.id.enabled=false");
    }

    private static ApplicationContextRunner runtimeRunner(String url) {
        return runner(url).withPropertyValues("stellar.tianji.agent.runtime.enabled=true", "egon.cola.component.agent-flow.enabled=true",
                        "egon.cola.component.agent-flow-registry.enabled=false")
                .withBean("fixtureModel", org.springframework.ai.chat.model.ChatModel.class,
                        () -> org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class))
                .withBean(top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization.class,
                        () -> (request, boundary) -> top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization.Decision.ALLOW)
                .withBean(top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator.class,
                        () -> org.mockito.Mockito.mock(top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator.class));
    }

    private static String testUrl(String property) {
        String url = System.getProperty(property);
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("storage tests require an explicit loopback test database");
        }
        return url;
    }
}
