package top.egon.cola.platform.tianji.process.server;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ProcessStorageConfigurationTest {
    @Test
    void rejects_automatic_schema_changes_or_unvalidated_outbox_before_initializing_engines() {
        assertDoesNotThrow(() -> guard(safeEnvironment()));
        for (String[] override : new String[][] {
                {"flowable.database-schema-update", "true"},
                {"spring.sql.init.mode", "always"},
                {"spring.flyway.enabled", "true"},
                {"egon.cola.component.transactional-outbox.enabled", "false"},
                {"egon.cola.component.transactional-outbox.storage.type", "MYBATIS_PLUS"},
                {"egon.cola.component.transactional-outbox.storage.validate-schema", "false"},
                {"egon.cola.component.transactional-outbox.storage.jdbc.schema", "other_schema"},
                {"flowable.database-schema", "bad.schema"}
        }) {
            MockEnvironment environment = safeEnvironment().withProperty(override[0], override[1]);
            assertThrows(IllegalStateException.class, () -> guard(environment), override[0]);
        }
    }

    @Test
    void rejects_a_different_engine_datasource_or_transaction_manager() {
        DataSource source = mock(DataSource.class);
        DataSource other = mock(DataSource.class);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        OutboxInfrastructure outbox = new OutboxInfrastructure(source, "source", manager, "manager");
        TransactionalOutboxProperties properties = new TransactionalOutboxProperties();
        properties.getStorage().getJdbc().setSchema("tianji_process");
        var configurer = new ProcessStorageConfiguration().processStorageGuard(outbox, properties);
        SpringProcessEngineConfiguration engine = new SpringProcessEngineConfiguration();
        engine.setDatabaseSchema("tianji_process");
        engine.setDatabaseSchemaUpdate("false");
        engine.setDataSource(other);
        engine.setTransactionManager(manager);
        assertThrows(IllegalStateException.class, () -> configurer.configure(engine));
        engine.setDataSource(source);
        engine.setTransactionManager(new DataSourceTransactionManager(source));
        assertThrows(IllegalStateException.class, () -> configurer.configure(engine));
    }

    @Test
    void worker_and_inbox_enablement_require_an_enabled_servlet_process_api() {
        var config = new ProcessStorageConfiguration();
        var disabled = new MockEnvironment();
        assertDoesNotThrow(() -> config.workerWebBoundaryGuard(disabled, mock(ApplicationContext.class)).afterSingletonsInstantiated());
        var enabled = new MockEnvironment().withProperty("stellar.tianji.process.worker.enabled", "true")
                .withProperty("stellar.tianji.process.api.enabled", "true");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(enabled, mock(ApplicationContext.class)).afterSingletonsInstantiated());
        assertDoesNotThrow(() -> config.workerWebBoundaryGuard(enabled, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        enabled.setProperty("stellar.tianji.process.api.enabled", "false");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(enabled, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        var inbox = new MockEnvironment().withProperty("stellar.tianji.process.agent-results.enabled", "true")
                .withProperty("stellar.tianji.process.api.enabled", "true");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(inbox, mock(ApplicationContext.class)).afterSingletonsInstantiated());
        assertDoesNotThrow(() -> config.workerWebBoundaryGuard(inbox, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        inbox.setProperty("stellar.tianji.process.api.enabled", "false");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(inbox, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        var tasks = new MockEnvironment().withProperty("stellar.tianji.process.human-tasks.enabled", "true").withProperty("stellar.tianji.process.api.enabled", "true");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(tasks, mock(ApplicationContext.class)).afterSingletonsInstantiated());
        assertDoesNotThrow(() -> config.workerWebBoundaryGuard(tasks, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        tasks.setProperty("stellar.tianji.process.api.enabled", "false");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(tasks, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        var publication = new MockEnvironment().withProperty("stellar.tianji.process.human-tasks.publish-enabled", "true");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(publication, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        var approvals = new MockEnvironment().withProperty("stellar.tianji.process.approval-verification.enabled", "true").withProperty("stellar.tianji.process.api.enabled", "true");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(approvals, mock(ApplicationContext.class)).afterSingletonsInstantiated());
        assertDoesNotThrow(() -> config.workerWebBoundaryGuard(approvals, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        approvals.setProperty("stellar.tianji.process.api.enabled", "false");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(approvals, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
    }

    @Test
    void case_readiness_and_new_starts_have_independent_fail_closed_feature_boundaries() {
        var configuration = new ProcessStorageConfiguration();
        var servlet = mock(WebApplicationContext.class);
        for (var environment : java.util.List.of(
                new MockEnvironment().withProperty("stellar.tianji.process.cases.enabled", "true"),
                new MockEnvironment().withProperty("stellar.tianji.process.cases.new-starts-enabled", "true").withProperty("stellar.tianji.process.api.enabled", "true").withProperty("stellar.tianji.process.human-tasks.enabled", "true"),
                new MockEnvironment().withProperty("stellar.tianji.process.cases.enabled", "true").withProperty("stellar.tianji.process.cases.new-starts-enabled", "true").withProperty("stellar.tianji.process.api.enabled", "true"))) {
            assertThrows(IllegalStateException.class, () -> configuration.workerWebBoundaryGuard(environment, servlet).afterSingletonsInstantiated());
        }
        var reads = new MockEnvironment().withProperty("stellar.tianji.process.cases.enabled", "true").withProperty("stellar.tianji.process.api.enabled", "true");
        assertDoesNotThrow(() -> configuration.workerWebBoundaryGuard(reads, servlet).afterSingletonsInstantiated());
        assertThrows(IllegalStateException.class, () -> configuration.workerWebBoundaryGuard(reads, mock(ApplicationContext.class)).afterSingletonsInstantiated());
        reads.setProperty("stellar.tianji.process.cases.new-starts-enabled", "true"); reads.setProperty("stellar.tianji.process.human-tasks.enabled", "true");
        assertDoesNotThrow(() -> configuration.workerWebBoundaryGuard(reads, servlet).afterSingletonsInstantiated());
    }

    private static void guard(MockEnvironment environment) {
        ProcessStorageConfiguration.processStoragePropertiesGuard(environment).postProcessBeanFactory(null);
    }

    private static MockEnvironment safeEnvironment() {
        return new MockEnvironment().withProperty("flowable.database-schema-update", "false")
                .withProperty("flowable.database-schema", "tianji_process")
                .withProperty("spring.sql.init.mode", "never")
                .withProperty("spring.flyway.enabled", "false")
                .withProperty("egon.cola.component.transactional-outbox.storage.type", "JDBC")
                .withProperty("egon.cola.component.transactional-outbox.storage.jdbc.schema", "tianji_process");
    }
}
