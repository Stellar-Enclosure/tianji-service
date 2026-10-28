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
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
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
import org.springframework.core.io.ClassPathResource;
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
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.decision.FlowablePinnedDecisionExecutor;

import javax.sql.DataSource;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only in a fresh, explicitly supplied loopback database. */
@EnabledIfSystemProperty(named = "tianji.process.postgres.test.url", matches = ".+")
@Timeout(180)
class ProcessServerPostgresTest {
    private static final String SCHEMA = "tianji_process";
    private static final String TENANT = "20";

    @Test
    void engines_persist_and_flowable_and_outbox_commit_or_roll_back_together() throws Exception {
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
            var decisionConfig = app.getBean(org.flowable.dmn.spring.SpringDmnEngineConfiguration.class);
            assertFalse(processConfig.isAsyncExecutorActivate());
            assertFalse(caseConfig.isAsyncExecutorActivate());
            assertTrue(processConfig.getBeans().isEmpty());
            assertTrue(caseConfig.getBeans().isEmpty());
            assertTrue(decisionConfig.getBeans().isEmpty());
            RepositoryService repository = app.getBean(RepositoryService.class);
            RuntimeService runtime = app.getBean(RuntimeService.class);
            TaskService tasks = app.getBean(TaskService.class);
            ManagementService jobs = app.getBean(ManagementService.class);
            ProcessModelValidator validator = new ProcessModelValidator();
            byte[] bpmn = new ClassPathResource("fixtures/baseline.bpmn20.xml").getContentAsByteArray();
            byte[] dmn = new ClassPathResource("fixtures/baseline.dmn").getContentAsByteArray();
            repository.createDeployment().tenantId(TENANT)
                    .addBytes("baseline.bpmn20.xml", validator.validateBpmn(bpmn, "baseline").xml()).deploy();
            app.getBean(CmmnRepositoryService.class).createDeployment().tenantId(TENANT)
                    .addClasspathResource("fixtures/baseline.cmmn").deploy();
            app.getBean(DmnRepositoryService.class).createDeployment().tenantId(TENANT)
                    .addDmnBytes("baseline.dmn", validator.validateDmn(dmn, "baselineDecision").xml()).deploy();

            DmnDecisionService decisions = app.getBean(DmnDecisionService.class);
            assertEquals("standard", decisions.createExecuteDecisionBuilder().decisionKey("baselineDecision")
                    .tenantId(TENANT).variable("amount", 10).executeWithSingleResult().get("route"));
            assertEquals("review", decisions.createExecuteDecisionBuilder().decisionKey("baselineDecision")
                    .tenantId(TENANT).variable("amount", 11).executeWithSingleResult().get("route"));
            String multiTypeDecision = """
                    <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="moreDefinitions"
                        name="More" namespace="urn:test">
                      <decision id="moreDecision" name="More"><decisionTable id="table" hitPolicy="FIRST">
                        <input><inputExpression id="stateExpression" typeRef="string"><text>state</text></inputExpression></input>
                        <input><inputExpression id="validExpression" typeRef="boolean"><text>valid</text></inputExpression></input>
                        <output id="allowOutput" name="allow" typeRef="boolean"/>
                        <output id="routeOutput" name="route" typeRef="string"/>
                        <rule><inputEntry id="stateRule"><text>'ready'</text></inputEntry>
                          <inputEntry id="validRule"><text>true</text></inputEntry>
                          <outputEntry id="allowRule"><text>true</text></outputEntry>
                          <outputEntry id="routeRule"><text>'approved'</text></outputEntry></rule>
                      </decisionTable></decision>
                    </definitions>
                    """;
            byte[] moreDmn = multiTypeDecision.getBytes(StandardCharsets.UTF_8);
            app.getBean(DmnRepositoryService.class).createDeployment().tenantId(TENANT)
                    .addDmnBytes("more.dmn", validator.validateDmn(moreDmn, "moreDecision").xml()).deploy();
            var multiResult = decisions.createExecuteDecisionBuilder().decisionKey("moreDecision")
                    .tenantId(TENANT).variables(Map.of("state", "ready", "valid", true))
                    .executeWithSingleResult();
            assertEquals(true, multiResult.get("allow"));
            assertEquals("approved", multiResult.get("route"));
            String uniqueDmn = new String(dmn, StandardCharsets.UTF_8).replace("baselineDecision", "uniqueDecision")
                    .replace("hitPolicy=\"FIRST\"", "hitPolicy=\"UNIQUE\"").replace("&gt; 10", "&lt;= 10");
            app.getBean(DmnRepositoryService.class).createDeployment().tenantId(TENANT).addDmnBytes("unique.dmn",
                    validator.validateDmn(uniqueDmn.getBytes(StandardCharsets.UTF_8), "uniqueDecision").xml()).deploy();
            var uniqueFailure = decisions.createExecuteDecisionBuilder().decisionKey("uniqueDecision")
                    .tenantId(TENANT).variable("amount", 10).executeWithAuditTrail();
            assertEquals(Boolean.TRUE, uniqueFailure.isFailed());
            String anyDmn = uniqueDmn.replace("uniqueDecision", "anyDecision")
                    .replace("hitPolicy=\"UNIQUE\"", "hitPolicy=\"ANY\"").replace("'review'", "'standard'");
            app.getBean(DmnRepositoryService.class).createDeployment().tenantId(TENANT).addDmnBytes("any.dmn",
                    validator.validateDmn(anyDmn.getBytes(StandardCharsets.UTF_8), "anyDecision").xml()).deploy();
            var anyResult = decisions.createExecuteDecisionBuilder().decisionKey("anyDecision")
                    .tenantId(TENANT).variable("amount", 10).executeWithAuditTrail();
            assertFalse(Boolean.TRUE.equals(anyResult.isFailed()));
            assertEquals("standard", anyResult.getDecisionResult().getFirst().get("route"));
            String anyMismatch = uniqueDmn.replace("uniqueDecision", "anyMismatch")
                    .replace("hitPolicy=\"UNIQUE\"", "hitPolicy=\"ANY\"");
            app.getBean(DmnRepositoryService.class).createDeployment().tenantId(TENANT).addDmnBytes("any-mismatch.dmn",
                    validator.validateDmn(anyMismatch.getBytes(StandardCharsets.UTF_8), "anyMismatch").xml()).deploy();
            assertEquals(Boolean.TRUE, decisions.createExecuteDecisionBuilder().decisionKey("anyMismatch")
                    .tenantId(TENANT).variable("amount", 10).executeWithAuditTrail().isFailed());
            String noMatch = new String(dmn, StandardCharsets.UTF_8).replace("baselineDecision", "noMatch")
                    .replace("&gt; 10", "&gt; 20");
            app.getBean(DmnRepositoryService.class).createDeployment().tenantId(TENANT).addDmnBytes("no-match.dmn",
                    validator.validateDmn(noMatch.getBytes(StandardCharsets.UTF_8), "noMatch").xml()).deploy();
            var emptyResult = decisions.createExecuteDecisionBuilder().decisionKey("noMatch")
                    .tenantId(TENANT).variable("amount", 15).executeWithAuditTrail();
            assertFalse(Boolean.TRUE.equals(emptyResult.isFailed()));
            assertTrue(emptyResult.getDecisionResult() == null || emptyResult.getDecisionResult().isEmpty());
            String firstVersion = new String(dmn, StandardCharsets.UTF_8).replace("'standard'", "'first'");
            String secondVersion = new String(dmn, StandardCharsets.UTF_8).replace("'standard'", "'second'");
            var firstDeployment = app.getBean(DmnRepositoryService.class).createDeployment().tenantId(TENANT)
                    .parentDeploymentId("test-release-first").addDmnBytes("first.dmn",
                            validator.validateDmn(firstVersion.getBytes(StandardCharsets.UTF_8), "baselineDecision").xml()).deploy();
            var secondDeployment = app.getBean(DmnRepositoryService.class).createDeployment().tenantId(TENANT)
                    .parentDeploymentId("test-release-second").addDmnBytes("second.dmn",
                            validator.validateDmn(secondVersion.getBytes(StandardCharsets.UTF_8), "baselineDecision").xml()).deploy();
            var firstDefinition = app.getBean(DmnRepositoryService.class).createDecisionQuery()
                    .deploymentId(firstDeployment.getId()).singleResult();
            var secondDefinition = app.getBean(DmnRepositoryService.class).createDecisionQuery()
                    .deploymentId(secondDeployment.getId()).singleResult();
            var selectedVersion = decisions.createExecuteDecisionBuilder().decisionKey("baselineDecision")
                    .parentDeploymentId("test-release-first").tenantId(TENANT).variable("amount", 10).executeWithAuditTrail();
            assertNull(selectedVersion.getDmnDeploymentId());
            assertEquals(firstDefinition.getVersion(), selectedVersion.getDecisionVersion());
            assertEquals("first", selectedVersion.getDecisionResult().getFirst().get("route"));
            var missingVersion = decisions.createExecuteDecisionBuilder().decisionKey("baselineDecision")
                    .parentDeploymentId("test-release-missing").tenantId(TENANT).variable("amount", 10).executeWithAuditTrail();
            assertNull(missingVersion.getDmnDeploymentId());
            assertEquals(secondDefinition.getVersion(), missingVersion.getDecisionVersion());
            assertEquals("second", missingVersion.getDecisionResult().getFirst().get("route"));
            var pinned = new FlowablePinnedDecisionExecutor(decisionConfig);
            var firstBinding = new FlowablePinnedDecisionExecutor.Binding(20, firstDefinition.getId(), firstDeployment.getId(),
                    firstDefinition.getVersion(), validator.validateDmn(firstVersion.getBytes(StandardCharsets.UTF_8), "baselineDecision"));
            var firstResult = pinned.evaluate(firstBinding, Map.of("amount", 10));
            assertEquals("first", firstResult.outputs().getFirst().get("route"));
            assertEquals(firstDefinition.getId(), firstResult.definitionId());
            assertFalse(firstResult.matchedRuleNumbers().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> firstResult.outputs().getFirst().put("route", "changed"));
            assertThrows(IllegalArgumentException.class, () -> pinned.evaluate(firstBinding, Map.of("amount", 10.0)));
            assertThrows(IllegalArgumentException.class, () -> pinned.evaluate(firstBinding, Map.of("amount", new Object())));
            assertThrows(IllegalArgumentException.class, () -> pinned.evaluate(firstBinding, Map.of()));
            assertThrows(IllegalArgumentException.class, () -> pinned.evaluate(firstBinding, Map.of("amount", 1_000_000_000_000_000L)));
            var foreignBinding = new FlowablePinnedDecisionExecutor.Binding(21, firstDefinition.getId(), firstDeployment.getId(),
                    firstDefinition.getVersion(), firstBinding.model());
            var missingBinding = new FlowablePinnedDecisionExecutor.Binding(20, "missing", firstDeployment.getId(),
                    firstDefinition.getVersion(), firstBinding.model());
            assertEquals(assertThrows(IllegalArgumentException.class, () -> pinned.evaluate(foreignBinding, Map.of("amount", 10))).getMessage(),
                    assertThrows(IllegalArgumentException.class, () -> pinned.evaluate(missingBinding, Map.of("amount", 10))).getMessage());
            var mismatchedModel = new FlowablePinnedDecisionExecutor.Binding(20, firstDefinition.getId(), firstDeployment.getId(),
                    firstDefinition.getVersion(), validator.validateDmn(secondVersion.getBytes(StandardCharsets.UTF_8), "baselineDecision"));
            assertThrows(IllegalArgumentException.class, () -> pinned.evaluate(mismatchedModel, Map.of("amount", 10)));
            var uniqueDefinition = app.getBean(DmnRepositoryService.class).createDecisionQuery().decisionKey("uniqueDecision")
                    .decisionTenantId(TENANT).singleResult();
            var uniqueBinding = new FlowablePinnedDecisionExecutor.Binding(20, uniqueDefinition.getId(), uniqueDefinition.getDeploymentId(),
                    uniqueDefinition.getVersion(), validator.validateDmn(uniqueDmn.getBytes(StandardCharsets.UTF_8), "uniqueDecision"));
            long beforeFailure = app.getBean(DmnHistoryService.class).createHistoricDecisionExecutionQuery().decisionKey("uniqueDecision").count();
            assertThrows(IllegalStateException.class, () -> pinned.evaluate(uniqueBinding, Map.of("amount", 10)));
            assertEquals(beforeFailure, app.getBean(DmnHistoryService.class).createHistoricDecisionExecutionQuery().decisionKey("uniqueDecision").count());
            var emptyDefinition = app.getBean(DmnRepositoryService.class).createDecisionQuery().decisionKey("noMatch")
                    .decisionTenantId(TENANT).singleResult();
            var emptyBinding = new FlowablePinnedDecisionExecutor.Binding(20, emptyDefinition.getId(), emptyDefinition.getDeploymentId(),
                    emptyDefinition.getVersion(), validator.validateDmn(noMatch.getBytes(StandardCharsets.UTF_8), "noMatch"));
            assertTrue(pinned.evaluate(emptyBinding, Map.of("amount", 15)).outputs().isEmpty());
            String numericDmn = new String(dmn, StandardCharsets.UTF_8).replace("baselineDecision", "numericDecision")
                    .replace("name=\"route\" typeRef=\"string\"", "name=\"route\" typeRef=\"number\"")
                    .replace("'standard'", "42").replace("'review'", "-1");
            var numericModel = validator.validateDmn(numericDmn.getBytes(StandardCharsets.UTF_8), "numericDecision");
            var numericDeployment = app.getBean(DmnRepositoryService.class).createDeployment().tenantId(TENANT)
                    .addDmnBytes("numeric.dmn", numericModel.xml()).deploy();
            var numericDefinition = app.getBean(DmnRepositoryService.class).createDecisionQuery()
                    .deploymentId(numericDeployment.getId()).singleResult();
            var numericBinding = new FlowablePinnedDecisionExecutor.Binding(20, numericDefinition.getId(), numericDeployment.getId(),
                    numericDefinition.getVersion(), numericModel);
            assertEquals(42L, pinned.evaluate(numericBinding, Map.of("amount", 10)).outputs().getFirst().get("route"));

            // Bypass the release validator only here to verify the engine-level bean isolation independently.
            String unsafeDmn = new String(dmn, StandardCharsets.UTF_8).replace("baselineDecision", "blockedDecision")
                    .replace("'standard'", "${modelProbe.touch()}");
            app.getBean(DmnRepositoryService.class).createDeployment().tenantId(TENANT)
                    .addString("blocked.dmn", unsafeDmn).deploy();
            var failedDecision = decisions.createExecuteDecisionBuilder().decisionKey("blockedDecision")
                    .tenantId(TENANT).variable("amount", 10).executeWithAuditTrail();
            assertEquals(Boolean.TRUE, failedDecision.isFailed());
            String unsafeProcess = """
                    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                        xmlns:flowable="http://flowable.org/bpmn" targetNamespace="urn:test">
                      <process id="blockedProcess" isExecutable="true">
                        <startEvent id="start"/><sequenceFlow id="one" sourceRef="start" targetRef="call"/>
                        <serviceTask id="call" flowable:delegateExpression="${modelProbe}"/>
                        <sequenceFlow id="two" sourceRef="call" targetRef="end"/><endEvent id="end"/>
                      </process>
                    </definitions>
                    """;
            repository.createDeployment().tenantId(TENANT).addString("blocked.bpmn20.xml", unsafeProcess).deploy();
            assertThrows(RuntimeException.class, () -> runtime.createProcessInstanceBuilder()
                    .processDefinitionKey("blockedProcess").tenantId(TENANT).start());
            assertEquals(0, app.getBean(ModelProbe.class).calls.get());

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
        @Bean("modelProbe")
        ModelProbe modelProbe() { return new ModelProbe(); }

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

    static final class ModelProbe implements JavaDelegate {
        private final AtomicInteger calls = new AtomicInteger();
        public String touch() { calls.incrementAndGet(); return "unexpected"; }
        @Override public void execute(DelegateExecution execution) { calls.incrementAndGet(); }
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
