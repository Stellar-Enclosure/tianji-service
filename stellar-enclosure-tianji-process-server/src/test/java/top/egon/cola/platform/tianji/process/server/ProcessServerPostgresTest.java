package top.egon.cola.platform.tianji.process.server;

import org.flowable.cmmn.api.CmmnRepositoryService;
import org.flowable.cmmn.api.CmmnRuntimeService;
import org.flowable.cmmn.api.CmmnTaskService;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.dmn.api.DmnDecisionService;
import org.flowable.dmn.api.DmnHistoryService;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.job.service.impl.asyncexecutor.AsyncExecutor;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.outbox.api.OutboxMessage;
import top.egon.cola.component.outbox.api.TenantScopedOutboxKey;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryHandler;
import top.egon.cola.component.outbox.delivery.DeliveryResult;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only in a fresh, explicitly supplied loopback database. */
@EnabledIfSystemProperty(named = "tianji.process.postgres.test.url", matches = ".+")
@Timeout(180)
class ProcessServerPostgresTest {
    private static final String SCHEMA = "tianji_process";
    private static final String TENANT = "20";

    @Test
    void engines_persist_and_flowable_and_outbox_commit_or_roll_back_together() {
        String url = System.getProperty("tianji.process.postgres.test.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("integration tests require an explicit loopback tianji_it_ database");
        }
        DriverManagerDataSource bootstrap = new DriverManagerDataSource(url);
        JdbcTemplate jdbc = new JdbcTemplate(bootstrap);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname = ?", Integer.class, SCHEMA) != 0) {
            throw new IllegalStateException("integration tests require a fresh database without the process schema");
        }
        Flyway flyway = Flyway.configure().dataSource(bootstrap).schemas(SCHEMA).defaultSchema(SCHEMA)
                .locations("classpath:db/transactional-outbox/postgresql").cleanDisabled(true).load();
        flyway.migrate();
        flyway.validate();
        String scopedUrl = url + "?currentSchema=" + SCHEMA;

        // The test alone provisions Flowable's own versioned schema in this disposable database.
        try (ConfigurableApplicationContext ignored = context(Provisioner.class, scopedUrl, System.getProperty("user.name"),
                "--flowable.database-schema-update=true", "--egon.cola.component.transactional-outbox.enabled=false")) {
            assertNotNull(ignored.getBean(RepositoryService.class));
            assertNotNull(ignored.getBean(CmmnRepositoryService.class));
            assertNotNull(ignored.getBean(DmnRepositoryService.class));
        }
        String runtimeUser = System.getProperty("tianji.process.postgres.test.runtime-user", "");
        if (!runtimeUser.matches("tianji_it_runtime_[0-9a-f]{12}")) {
            throw new IllegalArgumentException("integration tests require a dedicated temporary runtime role");
        }
        Boolean limitedRole = jdbc.queryForObject("SELECT rolcanlogin AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole "
                + "FROM pg_roles WHERE rolname = ?", Boolean.class, runtimeUser);
        assertEquals(Boolean.TRUE, limitedRole);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_auth_members WHERE member = "
                + "(SELECT oid FROM pg_roles WHERE rolname = ?)", Integer.class, runtimeUser));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT has_database_privilege(?, current_database(), 'CREATE')",
                Boolean.class, runtimeUser));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT has_schema_privilege(?, ?, 'CREATE')",
                Boolean.class, runtimeUser, SCHEMA));
        jdbc.execute("GRANT USAGE ON SCHEMA tianji_process TO \"" + runtimeUser + "\"");
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA tianji_process TO \"" + runtimeUser + "\"");
        jdbc.execute("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA tianji_process TO \"" + runtimeUser + "\"");
        jdbc.execute("REVOKE INSERT, UPDATE, DELETE ON tianji_process.flyway_schema_history FROM \"" + runtimeUser + "\"");
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT has_schema_privilege(?, ?, 'CREATE')",
                Boolean.class, runtimeUser, SCHEMA));

        RuntimeException wrongSchema = assertThrows(RuntimeException.class,
                () -> {
                    try (ConfigurableApplicationContext ignored = context(TianjiProcessServerApplication.class,
                            url + "?currentSchema=public", runtimeUser)) { }
                });
        assertTrue(hasMessage(wrongSchema, "Process DataSource search path does not select the configured schema"));
        RuntimeException unsafeMigration = assertThrows(RuntimeException.class,
                () -> {
                    try (ConfigurableApplicationContext ignored = context(TianjiProcessServerApplication.class,
                            scopedUrl, runtimeUser, "--flowable.database-schema-update=true")) { }
                });
        assertTrue(hasMessage(unsafeMigration, "Process storage requires a pre-provisioned shared Flowable and JDBC Outbox schema"));
        RuntimeException differentTransaction = assertThrows(RuntimeException.class,
                () -> {
                    try (ConfigurableApplicationContext ignored = context(
                            new Class<?>[] {TianjiProcessServerApplication.class, TestDelivery.class, MismatchedStorage.class},
                            scopedUrl, runtimeUser,
                            "--egon.cola.component.transactional-outbox.storage.data-source-bean-name=otherDataSource",
                            "--egon.cola.component.transactional-outbox.storage.transaction-manager-bean-name=otherTransactionManager")) { }
                });
        assertTrue(hasMessage(differentTransaction, "Flowable and Outbox must share one JDBC DataSource and transaction manager"));

        String processId;
        String caseId;
        String timerId;
        try (ConfigurableApplicationContext app = context(TianjiProcessServerApplication.class, scopedUrl, runtimeUser)) {
            var processConfig = app.getBean(SpringProcessEngineConfiguration.class);
            var caseConfig = app.getBean(SpringCmmnEngineConfiguration.class);
            assertFalse(processConfig.isAsyncExecutorActivate());
            assertFalse(caseConfig.isAsyncExecutorActivate());
            RepositoryService repository = app.getBean(RepositoryService.class);
            RuntimeService runtime = app.getBean(RuntimeService.class);
            TaskService tasks = app.getBean(TaskService.class);
            ManagementService jobs = app.getBean(ManagementService.class);
            repository.createDeployment().tenantId(TENANT).addClasspathResource("fixtures/baseline.bpmn20.xml").deploy();
            app.getBean(CmmnRepositoryService.class).createDeployment().tenantId(TENANT)
                    .addClasspathResource("fixtures/baseline.cmmn").deploy();
            app.getBean(DmnRepositoryService.class).createDeployment().tenantId(TENANT)
                    .addClasspathResource("fixtures/baseline.dmn").deploy();

            DmnDecisionService decisions = app.getBean(DmnDecisionService.class);
            assertEquals("standard", decisions.createExecuteDecisionBuilder().decisionKey("baselineDecision")
                    .tenantId(TENANT).variable("amount", 10).executeWithSingleResult().get("route"));
            assertEquals("review", decisions.createExecuteDecisionBuilder().decisionKey("baselineDecision")
                    .tenantId(TENANT).variable("amount", 11).executeWithSingleResult().get("route"));

            processId = runtime.createProcessInstanceBuilder().processDefinitionKey("baseline").tenantId(TENANT)
                    .businessKey("persistent").variable("evidence", "retained").start().getId();
            var review = tasks.createTaskQuery().processInstanceId(processId).singleResult();
            assertEquals("Review", review.getName());
            tasks.complete(review.getId());
            timerId = jobs.createTimerJobQuery().processInstanceId(processId).singleResult().getId();
            caseId = app.getBean(CmmnRuntimeService.class).createCaseInstanceBuilder()
                    .caseDefinitionKey("baselineCase").tenantId(TENANT).start().getId();
            assertEquals("Investigation", app.getBean(CmmnTaskService.class).createTaskQuery()
                    .caseInstanceId(caseId).singleResult().getName());

            TransactionTemplate transaction = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
            TransactionalOutbox outbox = app.getBean(TransactionalOutbox.class);
            CmmnRuntimeService cases = app.getBean(CmmnRuntimeService.class);
            DmnHistoryService decisionHistory = app.getBean(DmnHistoryService.class);
            long priorDecisions = decisionHistory.createHistoricDecisionExecutionQuery().decisionKey("baselineDecision").count();
            assertThrows(InjectedFailure.class, () -> transaction.execute(status -> {
                runtime.createProcessInstanceBuilder().processDefinitionKey("baseline").tenantId(TENANT)
                        .businessKey("rolled-back").start();
                cases.createCaseInstanceBuilder().caseDefinitionKey("baselineCase").tenantId(TENANT)
                        .businessKey("rolled-back").start();
                decisions.createExecuteDecisionBuilder().decisionKey("baselineDecision").tenantId(TENANT)
                        .variable("amount", 11).executeWithSingleResult();
                outbox.enqueue(message("rollback"));
                throw new InjectedFailure();
            }));
            assertEquals(0, runtime.createProcessInstanceQuery().processInstanceBusinessKey("rolled-back").count());
            assertEquals(0, cases.createCaseInstanceQuery().caseInstanceBusinessKey("rolled-back").count());
            assertEquals(priorDecisions, decisionHistory.createHistoricDecisionExecutionQuery().decisionKey("baselineDecision").count());
            assertEquals(0, countOutbox(jdbc, "rollback"));
            transaction.executeWithoutResult(status -> {
                runtime.createProcessInstanceBuilder().processDefinitionKey("baseline").tenantId(TENANT)
                        .businessKey("committed").start();
                cases.createCaseInstanceBuilder().caseDefinitionKey("baselineCase").tenantId(TENANT)
                        .businessKey("committed").start();
                decisions.createExecuteDecisionBuilder().decisionKey("baselineDecision").tenantId(TENANT)
                        .variable("amount", 11).executeWithSingleResult();
                outbox.enqueue(message("commit"));
            });
            assertEquals(1, runtime.createProcessInstanceQuery().processInstanceBusinessKey("committed").count());
            assertEquals(1, cases.createCaseInstanceQuery().caseInstanceBusinessKey("committed").count());
            assertEquals(priorDecisions + 1, decisionHistory.createHistoricDecisionExecutionQuery().decisionKey("baselineDecision").count());
            assertEquals(1, countOutbox(jdbc, "commit"));
        }

        try (ConfigurableApplicationContext app = context(TianjiProcessServerApplication.class, scopedUrl, runtimeUser)) {
            RuntimeService runtime = app.getBean(RuntimeService.class);
            ManagementService jobs = app.getBean(ManagementService.class);
            assertEquals("retained", runtime.getVariable(processId, "evidence"));
            assertEquals(timerId, jobs.createTimerJobQuery().processInstanceId(processId).singleResult().getId());
            assertNotNull(app.getBean(CmmnRuntimeService.class).createCaseInstanceQuery().caseInstanceId(caseId).singleResult());
            var caseTask = app.getBean(CmmnTaskService.class).createTaskQuery().caseInstanceId(caseId).singleResult();
            assertNotNull(caseTask);
            app.getBean(CmmnTaskService.class).complete(caseTask.getId());
            assertNull(app.getBean(CmmnRuntimeService.class).createCaseInstanceQuery().caseInstanceId(caseId).singleResult());
        }

        AsyncExecutor executor;
        try (ConfigurableApplicationContext app = context(TianjiProcessServerApplication.class, scopedUrl, runtimeUser,
                "--flowable.async-executor-activate=true",
                "--flowable.process.async.executor.default-timer-job-acquire-wait-time=PT0.2S",
                "--flowable.process.async.executor.default-async-job-acquire-wait-time=PT0.2S")) {
            RuntimeService runtime = app.getBean(RuntimeService.class);
            executor = app.getBean(SpringProcessEngineConfiguration.class).getAsyncExecutor();
            assertTrue(executor.isActive());
            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                    .until(() -> runtime.createProcessInstanceQuery().processInstanceId(processId).count() == 0);
            assertNotNull(app.getBean(HistoryService.class).createHistoricProcessInstanceQuery()
                    .processInstanceId(processId).finished().singleResult());
        }
        assertFalse(executor.isActive());
    }

    private static ConfigurableApplicationContext context(Class<?> source, String url, String user, String... extra) {
        return context(new Class<?>[] {source, TestDelivery.class}, url, user, extra);
    }

    private static ConfigurableApplicationContext context(Class<?>[] sources, String url, String user, String... extra) {
        String[] args = new String[extra.length + 3];
        args[0] = "--spring.datasource.url=" + url;
        args[1] = "--spring.datasource.username=" + user;
        args[2] = "--spring.main.banner-mode=off";
        System.arraycopy(extra, 0, args, 3, extra.length);
        return new SpringApplicationBuilder(sources).web(WebApplicationType.NONE).run(args);
    }

    private static boolean hasMessage(Throwable failure, String expected) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (expected.equals(current.getMessage())) return true;
        }
        return false;
    }

    private static OutboxMessage message(String command) {
        return OutboxMessage.builder().idempotencyKey(TenantScopedOutboxKey.of(20, "baseline", command))
                .channel("test").destination("baseline").schemaVersion("1")
                .header("tenantId", TENANT).payload(Map.of("tenantId", TENANT, "command", command)).build();
    }

    private static int countOutbox(JdbcTemplate jdbc, String command) {
        return jdbc.queryForObject("SELECT count(*) FROM tianji_process.egon_cola_outbox_message WHERE idempotency_key = ?",
                Integer.class, TenantScopedOutboxKey.of(20, "baseline", command));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Provisioner { }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestDelivery {
        @Bean
        DeliveryHandler testDeliveryHandler() {
            return new DeliveryHandler() {
                @Override public String channel() { return "test"; }
                @Override public void validateDestination(String destination) {
                    if (!"baseline".equals(destination)) throw new IllegalArgumentException("unexpected destination");
                }
                @Override public DeliveryResult deliver(DeliveryContext context) {
                    throw new AssertionError("the baseline test does not start Outbox polling");
                }
            };
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MismatchedStorage {
        @Bean("engineDataSource")
        @Primary
        DataSource engineDataSource(Environment environment) { return dataSource(environment); }

        @Bean("otherDataSource")
        DataSource otherDataSource(Environment environment) { return dataSource(environment); }

        @Bean("engineTransactionManager")
        @Primary
        PlatformTransactionManager engineTransactionManager(@Qualifier("engineDataSource") DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean("otherTransactionManager")
        PlatformTransactionManager otherTransactionManager(@Qualifier("otherDataSource") DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        private static DataSource dataSource(Environment environment) {
            DriverManagerDataSource source = new DriverManagerDataSource(environment.getRequiredProperty("spring.datasource.url"));
            source.setUsername(environment.getRequiredProperty("spring.datasource.username"));
            return source;
        }
    }

    private static final class InjectedFailure extends RuntimeException { }
}
