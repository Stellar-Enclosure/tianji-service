package top.egon.cola.platform.tianji.process.server;

import org.flowable.cmmn.api.CmmnRepositoryService;
import org.flowable.cmmn.api.CmmnRuntimeService;
import org.flowable.cmmn.api.CmmnTaskService;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.dmn.api.DmnDecisionService;
import org.flowable.dmn.api.DmnHistoryService;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
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
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchBinding;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStore;
import top.egon.cola.platform.tianji.process.server.bridge.AgentStartWorker;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentClient;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentSubmissionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentCloseResult;
import top.egon.cola.component.outbox.api.OutboxMessage;
import top.egon.cola.component.outbox.api.TenantScopedOutboxKey;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryHandler;
import top.egon.cola.component.outbox.delivery.DeliveryResult;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.decision.FlowablePinnedDecisionExecutor;
import top.egon.cola.platform.tianji.process.server.decision.ProcessDecisionService;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerLeaseStore;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.WorkerLeaseLostException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;
import top.egon.cola.platform.tianji.process.server.api.ProcessController;
import top.egon.cola.platform.tianji.process.server.api.WorkerController;
import top.egon.cola.platform.tianji.process.server.api.ProcessApiErrors;
import top.egon.cola.platform.tianji.process.server.api.ProcessRequestBodyAdvice;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.VerifiedProcessIssuerFilter;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.EvaluateDecisionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelResource;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.PublishProcessReleaseCommand;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseDTO;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskResult;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.FailWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerAcquireStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerClient;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerContractDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.client.ProcessClientException;
import top.egon.cola.platform.tianji.worker.JdbcWorkerExecutionStore;
import top.egon.cola.platform.tianji.worker.WorkerHandlerDescriptor;
import top.egon.cola.platform.tianji.worker.WorkerHandlerRegistration;
import top.egon.cola.platform.tianji.worker.WorkerPollingProperties;
import top.egon.cola.platform.tianji.worker.RemoteWorkerPoller;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
        Flyway platform = Flyway.configure().dataSource(bootstrap).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_process_schema_history").baselineVersion("0")
                .locations("classpath:db/tianji-process").target("1").cleanDisabled(true).load();
        platform.baseline();
        platform.migrate();
        platform.validate();
        Integer oldChecksum = jdbc.queryForObject("SELECT checksum FROM tianji_process.tianji_process_schema_history WHERE version='1'", Integer.class);
        var legacyActor = new ProcessActor(991, ProcessActor.SubjectType.USER, "alice");
        var legacyCommand = new ProcessExecutionService.StartCommand("legacy", "baseline", "legacy-order", "legacy-command",
                "legacy-correlation", Map.of("amount", 10));
        String legacyInstance;
        try (ConfigurableApplicationContext oldApp = context(TianjiProcessServerApplication.class, scopedUrl, System.getProperty("user.name"))) {
            byte[] oldXml = new ClassPathResource("fixtures/baseline.bpmn20.xml").getContentAsByteArray();
            var oldRepository = oldApp.getBean(RepositoryService.class);
            var deployment = oldRepository.createDeployment().tenantId("991").addBytes("legacy.bpmn20.xml", oldXml).deploy();
            var definition = oldRepository.createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
            legacyInstance = oldApp.getBean(RuntimeService.class).createProcessInstanceBuilder().processDefinitionId(definition.getId())
                    .tenantId("991").businessKey("legacy-order").variable("amount", 10L).start().getId();
            String modelHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(oldXml));
            String requestHash = ProcessRequestDigest.hash(List.of("legacy", "baseline", "legacy-order", "legacy-correlation"), Map.of("amount", 10L));
            jdbc.update("INSERT INTO tianji_process.tianji_process_release "
                    + "(tenant_id,release_key,state,manifest_sha256,publisher_type,publisher_id,created_at) "
                    + "VALUES (991,'legacy','ACTIVE',?,'USER','alice',clock_timestamp())", "a".repeat(64));
            jdbc.update("""
                    INSERT INTO tianji_process.tianji_process_release_definition
                        (tenant_id,release_key,model_type,definition_key,engine_definition_id,engine_deployment_id,
                         engine_version,model_sha256,input_schema)
                    VALUES (991,'legacy','BPMN','baseline',?,?,?,?,'{"amount":"INTEGER"}'::jsonb)
                    """, definition.getId(), deployment.getId(), definition.getVersion(), modelHash);
            jdbc.update("""
                    INSERT INTO tianji_process.tianji_process_command_receipt
                        (tenant_id,subject_type,subject_id,command_type,command_id,request_sha256,release_key,
                         correlation_id,state,resource_id,created_at)
                    VALUES (991,'USER','alice','START','legacy-command',?,'legacy','legacy-correlation','APPLIED',?,clock_timestamp())
                    """, requestHash, legacyInstance);
        }
        Flyway version2 = Flyway.configure().dataSource(bootstrap).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_process_schema_history").baselineVersion("0")
                .locations("classpath:db/tianji-process").target("2").cleanDisabled(true).load();
        assertEquals(1, version2.migrate().migrationsExecuted);
        Integer version2Checksum = jdbc.queryForObject("SELECT checksum FROM tianji_process.tianji_process_schema_history WHERE version='2'", Integer.class);
        Flyway upgraded = Flyway.configure().dataSource(bootstrap).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_process_schema_history").baselineVersion("0")
                .locations("classpath:db/tianji-process").target("3").cleanDisabled(true).load();
        assertEquals(1, upgraded.migrate().migrationsExecuted);
        upgraded.validate();
        Integer version3Checksum = jdbc.queryForObject("SELECT checksum FROM tianji_process.tianji_process_schema_history WHERE version='3'", Integer.class);
        Flyway workerSchema = Flyway.configure().dataSource(bootstrap).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_process_schema_history").baselineVersion("0")
                .locations("classpath:db/tianji-process").target("4").cleanDisabled(true).load();
        assertEquals(1, workerSchema.migrate().migrationsExecuted);
        workerSchema.validate();
        Flyway dispatchSchema = Flyway.configure().dataSource(bootstrap).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_process_schema_history").baselineVersion("0")
                .locations("classpath:db/tianji-process").target("5").cleanDisabled(true).load();
        assertEquals(1, dispatchSchema.migrate().migrationsExecuted);
        dispatchSchema.validate();
        assertEquals(oldChecksum, jdbc.queryForObject("SELECT checksum FROM tianji_process.tianji_process_schema_history WHERE version='1'", Integer.class));
        assertEquals(version2Checksum, jdbc.queryForObject("SELECT checksum FROM tianji_process.tianji_process_schema_history WHERE version='2'", Integer.class));
        assertEquals(version3Checksum, jdbc.queryForObject("SELECT checksum FROM tianji_process.tianji_process_schema_history WHERE version='3'", Integer.class));
        assertEquals(legacyInstance, jdbc.queryForObject("SELECT resource_id FROM tianji_process.tianji_process_command_receipt "
                + "WHERE tenant_id=991 AND command_id='legacy-command'", String.class));
        assertNull(jdbc.queryForObject("SELECT decision_result::text FROM tianji_process.tianji_process_command_receipt "
                + "WHERE tenant_id=991 AND command_id='legacy-command'", String.class));
        assertEquals("LEGACY", jdbc.queryForObject("SELECT subject_issuer FROM tianji_process.tianji_process_command_receipt "
                + "WHERE tenant_id=991 AND command_id='legacy-command'", String.class));
        jdbc.execute("""
                CREATE FUNCTION tianji_process.test_release_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.release_key = 'rollback-release' THEN RAISE EXCEPTION 'forced release failure'; END IF;
                  IF NEW.release_key = 'delayed-release' THEN PERFORM pg_advisory_xact_lock(72360422027); END IF;
                  RETURN NEW;
                END $$
                """);
        jdbc.execute("CREATE TRIGGER test_release_failure BEFORE INSERT ON tianji_process.tianji_process_release_definition "
                + "FOR EACH ROW EXECUTE FUNCTION tianji_process.test_release_failure()");
        jdbc.execute("""
                CREATE FUNCTION tianji_process.test_receipt_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.command_id = 'rollback-command' THEN RAISE EXCEPTION 'forced receipt failure'; END IF;
                  RETURN NEW;
                END $$
                """);
        jdbc.execute("CREATE TRIGGER test_receipt_failure BEFORE UPDATE ON tianji_process.tianji_process_command_receipt "
                + "FOR EACH ROW EXECUTE FUNCTION tianji_process.test_receipt_failure()");
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
        jdbc.execute("REVOKE INSERT, UPDATE, DELETE ON tianji_process.tianji_process_schema_history FROM \"" + runtimeUser + "\"");
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
        RoutingCases routingCases;
        NativeWorkerProbe workerProbe;
        try (ConfigurableApplicationContext app = context(TianjiProcessServerApplication.class, scopedUrl, runtimeUser)) {
            var processConfig = app.getBean(SpringProcessEngineConfiguration.class);
            var caseConfig = app.getBean(SpringCmmnEngineConfiguration.class);
            var decisionConfig = app.getBean(org.flowable.dmn.spring.SpringDmnEngineConfiguration.class);
            verifySchemaGuard(app, jdbc);
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
            var legacyService = new ProcessExecutionService(processConfig, decisionConfig, validator);
            assertEquals(legacyInstance, legacyService.start(legacyActor, legacyCommand).instanceId());
            var verifiedLegacyName = ProcessActor.verified(991, ProcessActor.SubjectType.USER, "alice", "https://idp.example/tenants/991");
            assertThrows(IllegalArgumentException.class, () -> legacyService.get(verifiedLegacyName, legacyInstance));
            byte[] bpmn = new ClassPathResource("fixtures/baseline.bpmn20.xml").getContentAsByteArray();
            byte[] dmn = new ClassPathResource("fixtures/baseline.dmn").getContentAsByteArray();
            verifyReleaseTransactions(app, jdbc, validator, bpmn, dmn);
            verifyProcessStarts(app, jdbc, validator);
            verifyDecisionApplications(app, jdbc, validator, dmn);
            routingCases = prepareRoutingCases(app, validator, dmn);
            verifyWorkerExecutorGate(app, validator);
            workerProbe = probeNativeExternalWorker(app, jdbc);
            verifyHttpAgainstDatabase(app, validator);
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
            assertEquals(List.of(1), firstResult.matchedRuleNumbers());
            String decisionReference = java.util.UUID.randomUUID().toString();
            pinned.evaluate(firstBinding, Map.of("amount", 10), decisionReference);
            var scopedHistory = app.getBean(DmnHistoryService.class).createHistoricDecisionExecutionQuery()
                    .instanceId(decisionReference).scopeType("tianji-decision").tenantId(TENANT).singleResult();
            assertNotNull(scopedHistory);
            assertEquals(firstDefinition.getId(), scopedHistory.getDecisionDefinitionId());
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
            verifyRoutingAfterRestart(app, jdbc, routingCases);
            assertEquals("retained", runtime.getVariable(processId, "evidence"));
            assertEquals(timerId, jobs.createTimerJobQuery().processInstanceId(processId).singleResult().getId());
            assertNotNull(app.getBean(CmmnRuntimeService.class).createCaseInstanceQuery().caseInstanceId(caseId).singleResult());
            var caseTask = app.getBean(CmmnTaskService.class).createTaskQuery().caseInstanceId(caseId).singleResult();
            assertNotNull(caseTask);
            app.getBean(CmmnTaskService.class).complete(caseTask.getId());
            assertNull(app.getBean(CmmnRuntimeService.class).createCaseInstanceQuery().caseInstanceId(caseId).singleResult());
        }

        byte[] routingXml = new ClassPathResource("fixtures/approval-routing.bpmn20.xml").getContentAsByteArray();
        byte[] routingDmn = new ClassPathResource("fixtures/baseline.dmn").getContentAsByteArray();
        new WebApplicationContextRunner().withUserConfiguration(TianjiProcessServerApplication.class, TestDelivery.class)
                .withPropertyValues("stellar.tianji.process.api.enabled=true", "stellar.tianji.process.decision-tasks.enabled=true",
                        "stellar.tianji.process.worker.enabled=true",
                        "stellar.tianji.process.worker.registrations[0].tenant-id=20",
                        "stellar.tianji.process.worker.registrations[0].issuer=https://idp.example/20",
                        "stellar.tianji.process.worker.registrations[0].subject-id=worker-service",
                        "stellar.tianji.process.worker.registrations[0].topic=riskCheck",
                        "stellar.tianji.process.worker.registrations[0].schema-version=1",
                        "stellar.tianji.process.worker.registrations[0].inputs.requestedAmount=INTEGER",
                        "stellar.tianji.process.worker.registrations[0].outputs.approved=BOOLEAN",
                        "stellar.tianji.process.worker.registrations[0].input-sources.requestedAmount[0]=amount",
                        "stellar.tianji.process.worker.registrations[0].output-targets.approved[0]=approved",
                        "stellar.tianji.process.worker.registrations[0].max-concurrent=1",
                        "stellar.tianji.process.worker.registrations[0].lease-duration=PT2M",
                        "stellar.tianji.process.worker.registrations[0].retry-delay=PT0.2S",
                        "stellar.tianji.process.worker.registrations[0].effect-safety=TRANSACTIONAL_RECEIPT",
                        "spring.datasource.url=" + scopedUrl,
                        "spring.datasource.username=" + runtimeUser,
                        "spring.sql.init.mode=never", "spring.flyway.enabled=false",
                        "flowable.database-schema=tianji_process", "flowable.database-schema-update=false",
                        "flowable.async-executor-activate=true", "flowable.async-history-executor-activate=false",
                        "flowable.check-process-definitions=false", "flowable.cmmn.deploy-resources=false",
                        "flowable.dmn.deploy-resources=false", "flowable.idm.enabled=false", "flowable.eventregistry.enabled=false",
                        "egon.cola.component.transactional-outbox.enabled=true",
                        "egon.cola.component.transactional-outbox.storage.type=JDBC",
                        "egon.cola.component.transactional-outbox.storage.validate-schema=true",
                        "egon.cola.component.transactional-outbox.storage.jdbc.schema=tianji_process",
                        "egon.cola.component.transactional-outbox.polling.enabled=false",
                        "egon.cola.platform.tianquan.shoubing.register-filter=false",
                        "egon.cola.platform.tianquan.jianshen.register-filter=false",
                        "egon.cola.component.id.enabled=false",
                        "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4")
                .withBean(PermissionExecutionGuard.class, () -> mock(PermissionExecutionGuard.class))
                .withBean(IdpBearerAuthenticationFilter.class, () -> mock(IdpBearerAuthenticationFilter.class))
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> mock(Rbac3BearerAuthenticationFilter.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProcessController.class);
                    assertThat(context).hasSingleBean(WorkerController.class);
                    assertThat(context).hasSingleBean(WorkerTopicRegistry.class);
                    assertThat(context).hasSingleBean(FlowableWorkerService.class);
                    var worker = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker-service", "https://idp.example/20");
                    assertEquals(Map.of("requestedAmount", ValueType.INTEGER), context.getBean(WorkerTopicRegistry.class)
                            .require(worker, "riskCheck", "1").inputs());
                    assertThat(context).hasSingleBean(ProcessApiSchemaVerifier.class);
                    var release = context.getBean(ProcessReleaseService.class).publish(
                            new ProcessActor(20, ProcessActor.SubjectType.USER, "routing-user"), "routing-configured", List.of(
                                    new ProcessReleaseService.ModelResource(Type.BPMN, "approvalRouting", routingXml, Map.of("amount", ValueType.INTEGER)),
                                    new ProcessReleaseService.ModelResource(Type.DMN, "baselineDecision", routingDmn, Map.of())));
                    assertEquals(2, release.definitions().size());
                });

        AsyncExecutor executor;
        try (ConfigurableApplicationContext app = context(TianjiProcessServerApplication.class, scopedUrl, runtimeUser,
                "--flowable.async-executor-activate=true",
                "--flowable.process.async.executor.default-timer-job-acquire-wait-time=PT0.2S",
                "--flowable.process.async.executor.default-async-job-acquire-wait-time=PT0.2S",
                "--flowable.process.async.executor.reset-expired-jobs-interval=PT0.2S")) {
            RuntimeService runtime = app.getBean(RuntimeService.class);
            executor = app.getBean(SpringProcessEngineConfiguration.class).getAsyncExecutor();
            assertTrue(executor.isActive());
            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                    .until(() -> runtime.createProcessInstanceQuery().processInstanceId(processId).count() == 0);
            assertNotNull(app.getBean(HistoryService.class).createHistoricProcessInstanceQuery()
                    .processInstanceId(processId).finished().singleResult());
            verifyNativeWorkerReset(app, workerProbe);
            verifyWorkerAdapter(app, jdbc);
            verifyAgentDispatchAdapter(app, jdbc);
        }
        assertFalse(executor.isActive());
    }

    private static void verifyWorkerExecutorGate(ConfigurableApplicationContext app, ProcessModelValidator validator) throws Exception {
        var process = app.getBean(SpringProcessEngineConfiguration.class);
        var dmn = app.getBean(SpringDmnEngineConfiguration.class);
        var registry = new WorkerTopicRegistry(List.of(workerRegistration(20, "https://idp.example/20", "worker-service", "1")));
        var service = new FlowableWorkerService(process, dmn, validator, registry);
        var worker = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker-service", "https://idp.example/20");
        assertThrows(IllegalStateException.class, () -> service.acquire(worker, new AcquireWorkerTaskCommand("riskCheck", "1")));
        byte[] xml = new ClassPathResource("fixtures/worker-mapping.bpmn20.xml").getContentAsByteArray();
        var model = new ProcessReleaseService.ModelResource(Type.BPMN, "workerMapping", xml, Map.of("amount", ValueType.INTEGER));
        var releases = new ProcessReleaseService(process, dmn, validator, false, registry);
        assertThrows(IllegalStateException.class, () -> releases.publish(worker, "worker-disabled", List.of(model)));
    }

    private static void verifyAgentDispatchAdapter(ConfigurableApplicationContext app, JdbcTemplate bootstrap) throws Exception {
        var process = app.getBean(SpringProcessEngineConfiguration.class);
        var dmn = app.getBean(SpringDmnEngineConfiguration.class);
        var validator = new ProcessModelValidator();
        var registration = new WorkerTopicRegistry.Registration(20, "https://idp.example/20", "worker-service", "agentStart", "1",
                Map.of("requestedAmount", ValueType.INTEGER), Map.of("runId", ValueType.STRING),
                Map.of("requestedAmount", Set.of("amount")), Map.of("runId", Set.of("agentRunId")), 1,
                Duration.ofMinutes(2), Duration.ofMillis(200), WorkerEffect.EXTERNAL_IDEMPOTENT, true);
        var registry = new WorkerTopicRegistry(List.of(registration));
        var releases = new ProcessReleaseService(process, dmn, validator, false, registry, true);
        var runs = new ProcessExecutionService(process, dmn, validator);
        var workers = new FlowableWorkerService(process, dmn, validator, registry);
        var owner = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "process-owner", "https://idp.example/20");
        var actor = registration.actor();
        byte[] model = new ClassPathResource("fixtures/agent-dispatch-wait.bpmn20.xml").getContentAsByteArray();
        byte[] legacyWait = new ClassPathResource("fixtures/agent-dispatch-wait-v1.bpmn20.xml").getContentAsByteArray();
        assertThrows(IllegalArgumentException.class, () -> releases.publish(owner, "agent-status-missing", List.of(
                new ProcessReleaseService.ModelResource(Type.BPMN, "agentDispatch", legacyWait, Map.of("amount", ValueType.INTEGER)))));
        releases.publish(owner, "agent-dispatch", List.of(new ProcessReleaseService.ModelResource(Type.BPMN, "agentDispatch", model,
                Map.of("amount", ValueType.INTEGER))));
        var instance = runs.start(owner, new ProcessExecutionService.StartCommand("agent-dispatch", "agentDispatch", "business-one",
                "dispatch-start", "dispatch-correlation", Map.of("amount", 10L)));
        var binding = new AgentDispatchBinding(actor, "process-east", "agentStart", "1",
                "research", 2, "a".repeat(64), "Summarize the task", Duration.ofMinutes(1));
        var manager = (DataSourceTransactionManager) process.getTransactionManager();
        var store = new AgentDispatchStore(manager.getDataSource(), manager, SCHEMA);
        assertThrows(ProcessResourceNotFoundException.class, () -> workers.acquire(actor, new AcquireWorkerTaskCommand("agentStart", "1")));
        var publicRegistration = new WorkerTopicRegistry.Registration(20, "https://idp.example/20", "worker-service", "agentStart", "1",
                registration.inputs(), registration.outputs(), registration.inputSources(), registration.outputTargets(),
                registration.maxConcurrent(), registration.leaseDuration(), registration.retryDelay(), registration.effectSafety());
        var drifted = new FlowableWorkerService(process, dmn, validator, new WorkerTopicRegistry(List.of(publicRegistration)));
        assertThrows(ProcessResourceNotFoundException.class, () -> drifted.acquire(actor, new AcquireWorkerTaskCommand("agentStart", "1")));
        var task = workers.acquireAgentDispatch(binding).task();
        assertNotNull(task);
        var intent = workers.prepareAgentDispatch(actor, task, binding, store);
        assertEquals(Map.of("requestedAmount", 10L), intent.command().start().input().taskData());
        assertEquals(instance.instanceId(), intent.query().link().scopeInstanceId());
        var run = new AgentRunDTO("probe-run", "research", 2, null,
                AgentRunStatusEnum.QUEUED, null, false, 0, 1, 0,
                Instant.now().plusSeconds(600), Instant.now(), null);
        store.record(intent.seed().key(), intent.query(), new LinkedAgentSubmissionDTO(false, run));
        assertThrows(ProcessResourceNotFoundException.class, () -> workers.complete(actor,
                new CompleteWorkerTaskCommand(task.taskId(), task.activityOccurrenceId(), task.leaseToken(), "forged-receipt", Map.of("runId", "probe-run"))));
        bootstrap.execute("""
                CREATE FUNCTION tianji_process.test_agent_ack_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.business_receipt_id='agent-probe-run' THEN RAISE EXCEPTION 'forced Agent ack failure'; END IF;
                  RETURN NEW;
                END $$
                """);
        bootstrap.execute("CREATE TRIGGER test_agent_ack_failure BEFORE UPDATE ON tianji_process.tianji_worker_lease_receipt "
                + "FOR EACH ROW EXECUTE FUNCTION tianji_process.test_agent_ack_failure()");
        assertThrows(RuntimeException.class, () -> workers.completeAgentDispatch(actor, task, binding, store, "probe-run"));
        assertEquals(AgentDispatchStore.State.SUBMITTED,
                store.refresh(intent.seed().key(), true).state());
        assertEquals("LEASED", bootstrap.queryForObject("SELECT state FROM tianji_process.tianji_worker_lease_receipt WHERE token_sha256=?",
                String.class, WorkerLeaseStore.tokenHash(task.leaseToken())));
        assertEquals(1, process.getManagementService().createExternalWorkerJobQuery().jobId(task.taskId()).count());
        bootstrap.execute("DROP TRIGGER test_agent_ack_failure ON tianji_process.tianji_worker_lease_receipt");
        assertEquals(WorkerReceiptOutcomeEnum.COMPLETED, workers.completeAgentDispatch(actor, task, binding, store, "probe-run").outcome());
        assertEquals(AgentDispatchStore.State.ACKNOWLEDGED,
                store.refresh(intent.seed().key(), true).state());
        await().atMost(Duration.ofSeconds(10)).until(() -> process.getRuntimeService().createEventSubscriptionQuery()
                .processInstanceId(instance.instanceId()).eventType("message").eventName("TianjiAgentFinishedV1").count() == 1);
        var subscription = process.getRuntimeService().createEventSubscriptionQuery().processInstanceId(instance.instanceId())
                .eventType("message").eventName("TianjiAgentFinishedV1").singleResult();
        assertEquals("waitResult", subscription.getActivityId());
        assertEquals("20", subscription.getTenantId());
        assertEquals(1, process.getManagementService().createTimerJobQuery().processInstanceId(instance.instanceId()).count());
        assertEquals(0, process.getTaskService().createTaskQuery().processInstanceId(instance.instanceId()).count());
        var ownerTransaction = new TransactionTemplate(manager);
        assertThrows(IllegalStateException.class, () -> ownerTransaction.execute(status -> {
            process.getRuntimeService().messageEventReceived("TianjiAgentFinishedV1", subscription.getExecutionId(), Map.of("agentStatus", "COMPLETED"));
            throw new IllegalStateException("controlled rollback after message delivery");
        }));
        assertEquals(1, process.getRuntimeService().createEventSubscriptionQuery().processInstanceId(instance.instanceId()).eventType("message").count());
        assertEquals(0, process.getTaskService().createTaskQuery().processInstanceId(instance.instanceId()).count());
        process.getRuntimeService().messageEventReceived("TianjiAgentFinishedV1", subscription.getExecutionId(), Map.of("agentStatus", "COMPLETED"));
        assertEquals(1, process.getTaskService().createTaskQuery().processInstanceId(instance.instanceId()).taskDefinitionKey("review").count());
        assertEquals(0, process.getRuntimeService().createEventSubscriptionQuery().processInstanceId(instance.instanceId()).count());
        assertEquals(0, process.getManagementService().createTimerJobQuery().processInstanceId(instance.instanceId()).count());
        assertThrows(RuntimeException.class, () -> process.getRuntimeService().messageEventReceived("TianjiAgentFinishedV1", subscription.getExecutionId()));
        assertEquals("probe-run", process.getRuntimeService().getVariable(instance.instanceId(), "agentRunId"));
        process.getRuntimeService().deleteProcessInstance(instance.instanceId(), "controlled cancellation");
        var client = mock(LinkedAgentClient.class);
        when(client.close(intent.query())).thenReturn(new LinkedAgentCloseResult(intent.query(),
                new LinkedAgentSubmissionDTO(true, run)));
        var reconciler = new AgentStartWorker(binding, workers, store, client);
        assertEquals(1, reconciler.reconcileOnce(32, Duration.ofSeconds(1)).closed());
        assertEquals(AgentDispatchStore.State.CLOSED,
                store.refresh(intent.seed().key(), false).state());
        byte[] timeoutModel = new String(model, StandardCharsets.UTF_8).replace("PT5M", "PT1S").getBytes(StandardCharsets.UTF_8);
        releases.publish(owner, "agent-dispatch-timeout", List.of(new ProcessReleaseService.ModelResource(Type.BPMN, "agentDispatch",
                timeoutModel, Map.of("amount", ValueType.INTEGER))));
        var timedInstance = runs.start(owner, new ProcessExecutionService.StartCommand("agent-dispatch-timeout", "agentDispatch", "business-timeout",
                "dispatch-timeout-start", "dispatch-timeout-correlation", Map.of("amount", 10L)));
        var timedTask = workers.acquireAgentDispatch(binding).task();
        assertNotNull(timedTask);
        var timedIntent = workers.prepareAgentDispatch(actor, timedTask, binding, store);
        var timedRun = new AgentRunDTO("probe-timer-run", "research", 2, null, AgentRunStatusEnum.QUEUED, null, false,
                0, 1, 0, Instant.now().plusSeconds(600), Instant.now(), null);
        store.record(timedIntent.seed().key(), timedIntent.query(), new LinkedAgentSubmissionDTO(false, timedRun));
        workers.completeAgentDispatch(actor, timedTask, binding, store, timedRun.runId());
        await().atMost(Duration.ofSeconds(15)).until(() -> process.getTaskService().createTaskQuery()
                .processInstanceId(timedInstance.instanceId()).taskDefinitionKey("timeoutReview").count() == 1);
        assertEquals(1, process.getRuntimeService().createProcessInstanceQuery().processInstanceId(timedInstance.instanceId()).count());
        assertFalse(workers.isAgentDispatchActive(store.refresh(timedIntent.seed().key(), true)));
        when(client.close(timedIntent.query())).thenReturn(new LinkedAgentCloseResult(timedIntent.query(), new LinkedAgentSubmissionDTO(true, timedRun)));
        assertEquals(1, reconciler.reconcileOnce(32, Duration.ofSeconds(1)).closed());
        assertEquals(AgentDispatchStore.State.CLOSED, store.refresh(timedIntent.seed().key(), true).state());
        for (var terminal : List.of(AgentRunStatusEnum.FAILED, AgentRunStatusEnum.CANCELLED, AgentRunStatusEnum.TIMED_OUT)) {
            String suffix = terminal.name();
            var failedInstance = runs.start(owner, new ProcessExecutionService.StartCommand("agent-dispatch", "agentDispatch", "business-" + suffix,
                    "dispatch-" + suffix, "correlation-" + suffix, Map.of("amount", 10L)));
            var failedTask = workers.acquireAgentDispatch(binding).task();
            assertNotNull(failedTask);
            var failedIntent = workers.prepareAgentDispatch(actor, failedTask, binding, store);
            var failedRun = new AgentRunDTO("probe-" + suffix, "research", 2, null, AgentRunStatusEnum.QUEUED, null, false,
                    0, 1, 0, Instant.now().plusSeconds(600), Instant.now(), null);
            store.record(failedIntent.seed().key(), failedIntent.query(), new LinkedAgentSubmissionDTO(false, failedRun));
            workers.completeAgentDispatch(actor, failedTask, binding, store, failedRun.runId());
            await().atMost(Duration.ofSeconds(10)).until(() -> process.getRuntimeService().createEventSubscriptionQuery()
                    .processInstanceId(failedInstance.instanceId()).eventName("TianjiAgentFinishedV1").count() == 1);
            var failedSubscription = process.getRuntimeService().createEventSubscriptionQuery().processInstanceId(failedInstance.instanceId())
                    .eventName("TianjiAgentFinishedV1").singleResult();
            process.getRuntimeService().messageEventReceived("TianjiAgentFinishedV1", failedSubscription.getExecutionId(), Map.of("agentStatus", suffix));
            assertEquals(1, process.getTaskService().createTaskQuery().processInstanceId(failedInstance.instanceId()).taskDefinitionKey("failureReview").count());
            assertEquals(0, process.getTaskService().createTaskQuery().processInstanceId(failedInstance.instanceId()).taskDefinitionKey("review").count());
            process.getRuntimeService().deleteProcessInstance(failedInstance.instanceId(), "controlled terminal branch cleanup");
            when(client.close(failedIntent.query())).thenReturn(new LinkedAgentCloseResult(failedIntent.query(), new LinkedAgentSubmissionDTO(true, failedRun)));
            assertEquals(1, reconciler.reconcileOnce(32, Duration.ofSeconds(1)).closed());
        }
    }

    private static void verifyWorkerAdapter(ConfigurableApplicationContext app, JdbcTemplate bootstrap) throws Exception {
        var process = app.getBean(SpringProcessEngineConfiguration.class);
        var dmn = app.getBean(SpringDmnEngineConfiguration.class);
        var validator = new ProcessModelValidator();
        var one = workerRegistration(20, "https://idp.example/20", "worker-service", "1");
        var two = workerRegistration(20, "https://idp.example/20", "worker-service", "2");
        var competitor = workerRegistration(20, "https://idp.example/20", "other-worker-service", "2");
        var shortLease = new WorkerTopicRegistry.Registration(20, "https://idp.example/20", "worker-service", "riskCheck", "3",
                Map.of("requestedAmount", ValueType.INTEGER), Map.of("approved", ValueType.BOOLEAN),
                Map.of("requestedAmount", Set.of("amount")), Map.of("approved", Set.of("approved")), 1,
                Duration.ofSeconds(3), Duration.ofMillis(200), WorkerEffect.TRANSACTIONAL_RECEIPT);
        var external = new WorkerTopicRegistry.Registration(20, "https://idp.example/20", "worker-service", "riskCheck", "4",
                Map.of("requestedAmount", ValueType.INTEGER), Map.of("approved", ValueType.BOOLEAN),
                Map.of("requestedAmount", Set.of("amount")), Map.of("approved", Set.of("approved")), 1,
                Duration.ofMinutes(2), Duration.ofMillis(200), WorkerEffect.EXTERNAL_IDEMPOTENT);
        var foreign = workerRegistration(21, "https://idp.example/21", "worker-service", "1");
        var registry = new WorkerTopicRegistry(List.of(one, two, competitor, shortLease, external, foreign));
        var releases = new ProcessReleaseService(process, dmn, validator, false, registry);
        var runs = new ProcessExecutionService(process, dmn, validator);
        var service = new FlowableWorkerService(process, dmn, validator, registry);
        var owner20 = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "process-owner", "https://idp.example/20");
        var owner21 = ProcessActor.verified(21, ProcessActor.SubjectType.USER, "process-owner", "https://idp.example/21");
        var worker20 = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker-service", "https://idp.example/20");
        var worker20b = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "other-worker-service", "https://idp.example/20");
        var worker21 = ProcessActor.verified(21, ProcessActor.SubjectType.SERVICE, "worker-service", "https://idp.example/21");
        byte[] v1 = new ClassPathResource("fixtures/worker-mapping.bpmn20.xml").getContentAsByteArray();
        byte[] v2 = new String(v1, StandardCharsets.UTF_8).replace("riskCheck:1", "riskCheck:2")
                .replace("tianji:schemaVersion=\"1\"", "tianji:schemaVersion=\"2\"").getBytes(StandardCharsets.UTF_8);
        byte[] v3 = new String(v1, StandardCharsets.UTF_8).replace("riskCheck:1", "riskCheck:3")
                .replace("tianji:schemaVersion=\"1\"", "tianji:schemaVersion=\"3\"").getBytes(StandardCharsets.UTF_8);
        byte[] v4 = new String(v1, StandardCharsets.UTF_8).replace("riskCheck:1", "riskCheck:4")
                .replace("tianji:schemaVersion=\"1\"", "tianji:schemaVersion=\"4\"")
                .replace("tianji:effectSafety=\"TRANSACTIONAL_RECEIPT\"", "tianji:effectSafety=\"EXTERNAL_IDEMPOTENT\"")
                .getBytes(StandardCharsets.UTF_8);
        var inputs = Map.of("amount", ValueType.INTEGER, "privateFixture", ValueType.STRING);
        var model1 = new ProcessReleaseService.ModelResource(Type.BPMN, "workerMapping", v1, inputs);
        var model2 = new ProcessReleaseService.ModelResource(Type.BPMN, "workerMapping", v2, inputs);
        var model3 = new ProcessReleaseService.ModelResource(Type.BPMN, "workerMapping", v3, inputs);
        var model4 = new ProcessReleaseService.ModelResource(Type.BPMN, "workerMapping", v4, inputs);
        byte[] unauthorizedSource = new String(v1, StandardCharsets.UTF_8).replace("source=\"amount\"", "source=\"privateAmount\"")
                .getBytes(StandardCharsets.UTF_8);
        var unsafeModel = new ProcessReleaseService.ModelResource(Type.BPMN, "workerMapping", unauthorizedSource,
                Map.of("privateAmount", ValueType.INTEGER));
        assertThrows(IllegalArgumentException.class, () -> releases.publish(owner20, "worker-unauthorized-source", List.of(unsafeModel)));
        releases.publish(owner20, "worker-v1", List.of(model1));
        releases.publish(owner20, "worker-v2", List.of(model2));
        releases.publish(owner20, "worker-v3", List.of(model3));
        releases.publish(owner20, "worker-v4", List.of(model4));
        releases.publish(owner21, "worker-v1", List.of(model1));
        var first = startWorkerRun(runs, owner20, "worker-v1", "worker-first");
        var second = startWorkerRun(runs, owner20, "worker-v1", "worker-second");
        var versionTwo = startWorkerRun(runs, owner20, "worker-v2", "worker-v2");
        var otherTenant = startWorkerRun(runs, owner21, "worker-v1", "worker-tenant21");
        var v1Request = new AcquireWorkerTaskCommand("riskCheck", "1");
        assertThrows(IllegalArgumentException.class, () -> service.acquire(owner20, v1Request));
        assertThrows(IllegalArgumentException.class, () -> service.acquire(
                ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker-service", "https://other.example/20"), v1Request));

        var start = new CountDownLatch(1);
        List<AcquireWorkerTaskResult> acquired;
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<AcquireWorkerTaskResult>> futures = java.util.stream.IntStream.range(0, 8).mapToObj(index -> pool.submit(() -> {
                start.await();
                return service.acquire(worker20, v1Request);
            })).toList();
            start.countDown();
            acquired = new java.util.ArrayList<>();
            for (Future<AcquireWorkerTaskResult> future : futures) acquired.add(future.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, acquired.stream().filter(item -> item.status() == WorkerAcquireStatusEnum.ACQUIRED).count());
        WorkerTaskDTO task = acquired.stream().filter(item -> item.task() != null).findFirst().orElseThrow().task();
        assertTrue(Set.of(first.instanceId(), second.instanceId()).contains(task.processInstanceId()));
        assertEquals(Map.of("requestedAmount", 10L), task.input());
        assertEquals(Map.of("requestedAmount", ProcessValueTypeEnum.INTEGER), task.inputSchema());
        assertEquals(Map.of("approved", ProcessValueTypeEnum.BOOLEAN), task.outputSchema());
        assertEquals(WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, task.effectSafety());
        assertEquals(20, task.tenantId());
        assertEquals(WorkerAcquireStatusEnum.EMPTY, service.acquire(worker20, v1Request).status());

        var v2Task = service.acquire(worker20, new AcquireWorkerTaskCommand("riskCheck", "2")).task();
        assertNotNull(v2Task);
        assertEquals(versionTwo.instanceId(), v2Task.processInstanceId());
        var v2Command = complete(v2Task, "v2-receipt", true);
        assertEquals(WorkerReceiptOutcomeEnum.COMPLETED, service.complete(worker20, v2Command).outcome());
        assertEquals(service.complete(worker20, v2Command), service.complete(worker20, v2Command));

        var wrongOutput = new CompleteWorkerTaskCommand(task.taskId(), task.activityOccurrenceId(), task.leaseToken(),
                "receipt", Map.of("approved", true, "unauthorized", true));
        assertThrows(IllegalArgumentException.class, () -> service.complete(worker20, wrongOutput));
        var wrongIssuer = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker-service", "https://other.example/20");
        assertThrows(IllegalArgumentException.class, () -> service.complete(wrongIssuer, complete(task, "receipt", true)));
        var changedPolicy = new WorkerTopicRegistry.Registration(20, "https://idp.example/20", "worker-service", "riskCheck", "1",
                Map.of("requestedAmount", ValueType.INTEGER), Map.of("approved", ValueType.BOOLEAN),
                Map.of("requestedAmount", Set.of("amount")), Map.of("approved", Set.of("approved")), 1,
                Duration.ofMinutes(2), Duration.ofSeconds(1), WorkerEffect.TRANSACTIONAL_RECEIPT);
        var changedService = new FlowableWorkerService(process, dmn, validator, new WorkerTopicRegistry(List.of(changedPolicy)));
        assertThrows(ProcessConflictException.class, () -> changedService.complete(worker20, complete(task, "receipt", true)));
        RuntimeService runtime = app.getBean(RuntimeService.class);
        runtime.setVariable(task.processInstanceId(), "amount", 11L);
        assertThrows(RuntimeException.class, () -> service.complete(worker20, complete(task, "receipt", true)));
        runtime.setVariable(task.processInstanceId(), "amount", 10L);

        bootstrap.execute("""
                CREATE FUNCTION tianji_process.test_worker_receipt_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.business_receipt_id='force-worker-rollback' THEN RAISE EXCEPTION 'forced worker receipt failure'; END IF;
                  RETURN NEW;
                END $$
                """);
        bootstrap.execute("CREATE TRIGGER test_worker_receipt_failure BEFORE UPDATE ON tianji_process.tianji_worker_lease_receipt "
                + "FOR EACH ROW EXECUTE FUNCTION tianji_process.test_worker_receipt_failure()");
        var rollback = complete(task, "force-worker-rollback", true);
        assertThrows(RuntimeException.class, () -> service.complete(worker20, rollback));
        String tokenHash = WorkerLeaseStore.tokenHash(task.leaseToken());
        assertEquals("LEASED", bootstrap.queryForObject("SELECT state FROM tianji_process.tianji_worker_lease_receipt WHERE token_sha256=?", String.class, tokenHash));
        assertEquals(1, app.getBean(ManagementService.class).createExternalWorkerJobQuery().jobId(task.taskId()).count());
        bootstrap.execute("DROP TRIGGER test_worker_receipt_failure ON tianji_process.tianji_worker_lease_receipt");
        var accepted = service.complete(worker20, rollback);
        assertEquals(accepted, service.complete(worker20, rollback));
        assertThrows(RuntimeException.class, () -> service.complete(worker20, complete(task, "different-receipt", true)));
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() ->
                app.getBean(TaskService.class).createTaskQuery().processInstanceId(task.processInstanceId()).count() == 1);

        WorkerTaskDTO stale = service.acquire(worker20, v1Request).task();
        assertNotNull(stale);
        expireNativeWorker(bootstrap, stale.taskId(), stale.processInstanceId());
        expireWorkerLease(bootstrap, stale);
        assertThrows(WorkerLeaseLostException.class, () -> service.complete(worker20, complete(stale, "stale-receipt", true)));
        var next = awaitWorker(service, worker20, v1Request);
        assertEquals(stale.taskId(), next.taskId());
        assertEquals(stale.idempotencyKey(), next.idempotencyKey());
        assertFalse(stale.leaseToken().equals(next.leaseToken()));
        assertThrows(WorkerLeaseLostException.class, () -> service.complete(worker20, complete(stale, "stale-receipt", true)));
        assertEquals(WorkerReceiptOutcomeEnum.COMPLETED, service.complete(worker20, complete(next, "fresh-receipt", true)).outcome());

        var tenantTask = service.acquire(worker21, v1Request).task();
        assertNotNull(tenantTask);
        assertEquals(otherTenant.instanceId(), tenantTask.processInstanceId());
        assertThrows(IllegalArgumentException.class, () -> service.complete(worker20, complete(tenantTask, "wrong-tenant", true)));
        assertEquals(WorkerReceiptOutcomeEnum.COMPLETED, service.complete(worker21, complete(tenantTask, "tenant-receipt", true)).outcome());

        startWorkerRun(runs, owner20, "worker-v1", "worker-retry");
        WorkerTaskDTO retry = service.acquire(worker20, v1Request).task();
        assertNotNull(retry);
        var failed = new FailWorkerTaskCommand(retry.taskId(), retry.activityOccurrenceId(), retry.leaseToken(),
                WorkerFailureKindEnum.RETRYABLE, "TEMPORARY_FAILURE");
        var failureReceipt = service.fail(worker20, failed);
        assertEquals(WorkerReceiptOutcomeEnum.RETRY_SCHEDULED, failureReceipt.outcome());
        assertEquals(failureReceipt, service.fail(worker20, failed));
        var retried = awaitWorker(service, worker20, v1Request);
        assertEquals(retry.idempotencyKey(), retried.idempotencyKey());
        var unknown = new FailWorkerTaskCommand(retried.taskId(), retried.activityOccurrenceId(), retried.leaseToken(),
                WorkerFailureKindEnum.UNKNOWN, "OUTCOME_UNKNOWN");
        var manual = service.fail(worker20, unknown);
        assertEquals(WorkerReceiptOutcomeEnum.NEEDS_MANUAL, manual.outcome());
        assertEquals(manual, service.fail(worker20, unknown));
        assertEquals(1, app.getBean(ManagementService.class).createDeadLetterJobQuery().processInstanceId(retried.processInstanceId()).count());

        verifyWorkerConcurrency(app, bootstrap, runs, service, owner20, worker20, worker20b);
        verifyWorkerHttpAgainstDatabase(runs, service, owner20, worker20, worker21);
        if (System.getProperty("tianji.worker.business.postgres.test.url") != null) {
            verifyWorkerLostAcknowledgement(app, bootstrap, runs, service, owner20, worker20);
        }
        verifyWorkerExpiry(app, bootstrap, runs, service, owner20, worker20);
    }

    private static void verifyWorkerLostAcknowledgement(ConfigurableApplicationContext app, JdbcTemplate processDatabase,
                                                         ProcessExecutionService runs, FlowableWorkerService service,
                                                         ProcessActor owner, ProcessActor worker) throws Exception {
        String url = System.getProperty("tianji.worker.business.postgres.test.url", "");
        String user = System.getProperty("tianji.worker.business.postgres.test.runtime-user", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")
                || !user.matches("tianji_it_worker_[0-9a-f]{12}")) throw new IllegalArgumentException("fresh loopback business database and temporary role required");
        var bootstrapSource = new DriverManagerDataSource(url);
        var business = new JdbcTemplate(bootstrapSource);
        if (business.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='cross_worker'", Integer.class) != 0) {
            throw new IllegalStateException("cross-worker test schema already exists");
        }
        business.execute("CREATE SCHEMA cross_worker");
        business.execute("CREATE TABLE cross_worker.business_counter (tenant_id bigint PRIMARY KEY, value bigint NOT NULL)");
        business.update("INSERT INTO cross_worker.business_counter (tenant_id,value) VALUES (20,0)");
        Flyway flyway = Flyway.configure().dataSource(bootstrapSource).schemas("cross_worker").defaultSchema("cross_worker")
                .table("tianji_worker_schema_history").baselineVersion("0").baselineOnMigrate(true)
                .locations("classpath:db/tianji-worker").target("1").cleanDisabled(true).load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        business.execute("GRANT USAGE ON SCHEMA cross_worker TO \"" + user + "\"");
        business.execute("GRANT SELECT,INSERT,UPDATE ON cross_worker.tianji_worker_execution_receipt TO \"" + user + "\"");
        business.execute("GRANT SELECT,UPDATE ON cross_worker.business_counter TO \"" + user + "\"");
        business.execute("GRANT SELECT ON cross_worker.tianji_worker_schema_history TO \"" + user + "\"");
        assertEquals(Boolean.FALSE, business.queryForObject("SELECT has_schema_privilege(?, 'cross_worker', 'CREATE')", Boolean.class, user));
        var runtime = new DriverManagerDataSource(url + "?currentSchema=cross_worker");
        runtime.setUsername(user);
        var businessStore = new JdbcWorkerExecutionStore(runtime, new DataSourceTransactionManager(runtime), "cross_worker",
                "integration-process", 20, Duration.ofSeconds(20), Duration.ofSeconds(6));
        var contract = new WorkerContractDTO("riskCheck", "1", WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT,
                Map.of("requestedAmount", ProcessValueTypeEnum.INTEGER), Map.of("approved", ProcessValueTypeEnum.BOOLEAN));
        AtomicInteger executions = new AtomicInteger();
        var registration = new WorkerHandlerRegistration(new WorkerHandlerDescriptor(contract, "v1"), (context, jdbc) -> {
            executions.incrementAndGet();
            jdbc.update("UPDATE cross_worker.business_counter SET value=value+? WHERE tenant_id=?",
                    context.input().get("requestedAmount"), context.tenantId());
            return new WorkerBusinessResultDTO("cross-business-receipt", Map.of("approved", true));
        });
        var access = mock(TianjiProcessAccess.class);
        when(access.require(anyString())).thenReturn(worker);
        var mvc = MockMvcBuilders.standaloneSetup(new WorkerController(service, access))
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
        var client = new LostAcknowledgementClient(mvc, false);
        var scheduler = mock(TaskScheduler.class);
        var tick = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenAnswer(invocation -> {
            tick.set(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        var properties = new WorkerPollingProperties(true, 20, 1, Duration.ofSeconds(1), Duration.ofSeconds(3),
                Duration.ofSeconds(2), Duration.ZERO, 1, Duration.ZERO);
        var poller = new RemoteWorkerPoller(client, businessStore, List.of(registration), scheduler, Runnable::run, properties, Clock.systemUTC());
        var instance = startWorkerRun(runs, owner, "worker-v1", "worker-lost-ack");
        poller.start();
        try {
            tick.get().run();
            WorkerTaskDTO first = client.latestTask;
            assertNotNull(first);
            assertEquals(instance.instanceId(), first.processInstanceId());
            assertEquals(1, executions.get());
            assertEquals(10L, business.queryForObject("SELECT value FROM cross_worker.business_counter WHERE tenant_id=20", Long.class));
            assertEquals(1, app.getBean(ManagementService.class).createExternalWorkerJobQuery().jobId(first.taskId()).count());
            expireNativeWorker(processDatabase, first.taskId(), first.processInstanceId());
            expireWorkerLease(processDatabase, first);
            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() ->
                    app.getBean(ManagementService.class).createExternalWorkerJobQuery().jobId(first.taskId()).unlocked().count() == 1);
            tick.get().run();
            WorkerTaskDTO second = client.latestTask;
            assertEquals(first.taskId(), second.taskId());
            assertEquals(first.idempotencyKey(), second.idempotencyKey());
            assertFalse(first.leaseToken().equals(second.leaseToken()));
            assertEquals(1, executions.get());
            assertEquals(10L, business.queryForObject("SELECT value FROM cross_worker.business_counter WHERE tenant_id=20", Long.class));
            assertEquals(1, business.queryForObject("SELECT count(*) FROM cross_worker.tianji_worker_execution_receipt", Integer.class));
            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() ->
                    app.getBean(TaskService.class).createTaskQuery().processInstanceId(instance.instanceId()).count() == 1);
            var review = app.getBean(TaskService.class).createTaskQuery().processInstanceId(instance.instanceId()).singleResult();
            app.getBean(TaskService.class).complete(review.getId());
            assertEquals(ProcessExecutionService.State.COMPLETED, runs.get(owner, instance.instanceId()).state());
        } finally {
            poller.stop();
        }
        var responseLostClient = new LostAcknowledgementClient(mvc, true);
        var retryProperties = new WorkerPollingProperties(true, 20, 1, Duration.ofSeconds(1), Duration.ofSeconds(3),
                Duration.ofSeconds(2), Duration.ZERO, 2, Duration.ZERO);
        var retryPoller = new RemoteWorkerPoller(responseLostClient, businessStore, List.of(registration), scheduler,
                Runnable::run, retryProperties, Clock.systemUTC());
        var secondInstance = startWorkerRun(runs, owner, "worker-v1", "worker-lost-response");
        retryPoller.start();
        try {
            tick.get().run();
            assertEquals(2, responseLostClient.completionCalls);
            assertEquals(2, executions.get());
            assertEquals(20L, business.queryForObject("SELECT value FROM cross_worker.business_counter WHERE tenant_id=20", Long.class));
            assertEquals(2, business.queryForObject("SELECT count(*) FROM cross_worker.tianji_worker_execution_receipt", Integer.class));
            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() ->
                    app.getBean(TaskService.class).createTaskQuery().processInstanceId(secondInstance.instanceId()).count() == 1);
            var review = app.getBean(TaskService.class).createTaskQuery().processInstanceId(secondInstance.instanceId()).singleResult();
            app.getBean(TaskService.class).complete(review.getId());
            assertEquals(ProcessExecutionService.State.COMPLETED, runs.get(owner, secondInstance.instanceId()).state());
        } finally {
            retryPoller.stop();
        }

        Flyway externalSchema = Flyway.configure().dataSource(bootstrapSource).schemas("cross_worker").defaultSchema("cross_worker")
                .table("tianji_worker_schema_history").baselineVersion("0").baselineOnMigrate(true)
                .locations("classpath:db/tianji-worker").target("2").cleanDisabled(true).load();
        assertEquals(1, externalSchema.migrate().migrationsExecuted);
        externalSchema.validate();
        business.execute("GRANT SELECT,INSERT ON cross_worker.tianji_worker_nontransactional_intent TO \"" + user + "\"");
        var externalContract = new WorkerContractDTO("riskCheck", "4", WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT,
                Map.of("requestedAmount", ProcessValueTypeEnum.INTEGER), Map.of("approved", ProcessValueTypeEnum.BOOLEAN));
        var remote = new java.util.concurrent.ConcurrentHashMap<String, WorkerBusinessResultDTO>();
        var externalEffects = new AtomicInteger(); var externalCalls = new AtomicInteger();
        var externalRegistration = new top.egon.cola.platform.tianji.worker.NonTransactionalWorkerRegistration(new WorkerHandlerDescriptor(externalContract, "external-v1"),
                "idempotent-service-v1", (context, externalKey) -> {
                    assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                    var result = remote.computeIfAbsent(externalKey, ignored -> {
                        externalEffects.incrementAndGet();
                        return new WorkerBusinessResultDTO("external-receipt", Map.of("approved", true));
                    });
                    if (externalCalls.incrementAndGet() == 1) {
                        throw new top.egon.cola.platform.tianji.worker.WorkerHandlerFailureException(WorkerFailureKindEnum.RETRYABLE, "REMOTE_RESPONSE_LOST");
                    }
                    return result;
                });
        var externalClient = new LostAcknowledgementClient(mvc, true);
        var externalPoller = new RemoteWorkerPoller(externalClient, businessStore, List.of(), List.of(externalRegistration), scheduler,
                Runnable::run, retryProperties, Clock.systemUTC());
        var externalInstance = startWorkerRun(runs, owner, "worker-v4", "worker-external-response-lost");
        externalPoller.start();
        try {
            tick.get().run();
            assertEquals(1, externalEffects.get()); assertEquals(1, externalCalls.get());
            assertEquals(WorkerFailureKindEnum.RETRYABLE, externalClient.lastFailure.kind());
            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
                tick.get().run();
                assertEquals(1, app.getBean(TaskService.class).createTaskQuery().processInstanceId(externalInstance.instanceId()).count());
            });
            assertEquals(1, externalEffects.get()); assertEquals(2, externalCalls.get());
            assertEquals(2, externalClient.completionCalls);
            assertEquals(1, business.queryForObject("SELECT count(*) FROM cross_worker.tianji_worker_nontransactional_intent", Integer.class));
            assertEquals(3, business.queryForObject("SELECT count(*) FROM cross_worker.tianji_worker_execution_receipt", Integer.class));
            var review = app.getBean(TaskService.class).createTaskQuery().processInstanceId(externalInstance.instanceId()).singleResult();
            app.getBean(TaskService.class).complete(review.getId());
            assertEquals(ProcessExecutionService.State.COMPLETED, runs.get(owner, externalInstance.instanceId()).state());
        } finally {
            externalPoller.stop();
        }
    }

    private static final class LostAcknowledgementClient implements WorkerClient {
        private final MockMvc mvc;
        private final JsonMapper mapper = JsonMapper.builder().build();
        private final boolean dropAfterCommit;
        private boolean dropFirstCompletion = true;
        private int completionCalls;
        private WorkerTaskDTO latestTask;
        private FailWorkerTaskCommand lastFailure;

        private LostAcknowledgementClient(MockMvc mvc, boolean dropAfterCommit) { this.mvc = mvc; this.dropAfterCommit = dropAfterCommit; }

        @Override public AcquireWorkerTaskResult acquire(AcquireWorkerTaskCommand command) {
            var result = post("acquire", command, AcquireWorkerTaskResult.class);
            if (result.task() != null) latestTask = result.task();
            return result;
        }

        @Override public WorkerReceiptDTO complete(CompleteWorkerTaskCommand command) {
            completionCalls++;
            if (dropFirstCompletion && !dropAfterCommit) {
                dropFirstCompletion = false;
                throw new ProcessClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null);
            }
            WorkerReceiptDTO receipt = post("complete", command, WorkerReceiptDTO.class);
            if (dropFirstCompletion) {
                dropFirstCompletion = false;
                throw new ProcessClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null);
            }
            return receipt;
        }

        @Override public WorkerReceiptDTO fail(FailWorkerTaskCommand command) { lastFailure = command; return post("fail", command, WorkerReceiptDTO.class); }

        private <T> T post(String action, Object command, Class<T> type) {
            try {
                var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/tianji/v1/worker-tasks/" + action)
                                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command)))
                        .andReturn().getResponse();
                if (response.getStatus() != 200) throw new IllegalStateException("Worker test request failed");
                return mapper.readValue(response.getContentAsByteArray(), type);
            } catch (Exception failure) {
                throw new IllegalStateException("Worker test transport failed");
            }
        }
    }

    private static void verifyWorkerHttpAgainstDatabase(ProcessExecutionService runs, FlowableWorkerService service,
                                                        ProcessActor owner, ProcessActor worker, ProcessActor otherTenant) throws Exception {
        startWorkerRun(runs, owner, "worker-v1", "worker-http");
        var access = mock(TianjiProcessAccess.class);
        when(access.require(anyString())).thenReturn(worker);
        var mvc = MockMvcBuilders.standaloneSetup(new WorkerController(service, access))
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
        var mapper = JsonMapper.builder().build();
        var acquire = new AcquireWorkerTaskCommand("riskCheck", "1");
        byte[] body = mvc.perform(post("/api/tianji/v1/worker-tasks/acquire").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(acquire)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        var task = mapper.readValue(body, AcquireWorkerTaskResult.class).task();
        assertNotNull(task);
        var command = complete(task, "http-receipt", true);
        byte[] completion = mapper.writeValueAsBytes(command);
        when(access.require(anyString())).thenReturn(otherTenant);
        mvc.perform(post("/api/tianji/v1/worker-tasks/complete").contentType(MediaType.APPLICATION_JSON).content(completion))
                .andExpect(status().isNotFound());
        when(access.require(anyString())).thenReturn(worker);
        byte[] first = mvc.perform(post("/api/tianji/v1/worker-tasks/complete").contentType(MediaType.APPLICATION_JSON).content(completion))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        byte[] replay = mvc.perform(post("/api/tianji/v1/worker-tasks/complete").contentType(MediaType.APPLICATION_JSON).content(completion))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals(mapper.readValue(first, top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptDTO.class),
                mapper.readValue(replay, top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptDTO.class));
        when(access.require(anyString())).thenReturn(owner);
        mvc.perform(post("/api/tianji/v1/worker-tasks/acquire").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(acquire))).andExpect(status().isForbidden());
    }

    private static void verifyWorkerConcurrency(ConfigurableApplicationContext app, JdbcTemplate bootstrap,
                                                ProcessExecutionService runs, FlowableWorkerService service,
                                                ProcessActor owner20, ProcessActor worker20, ProcessActor worker20b) throws Exception {
        startWorkerRun(runs, owner20, "worker-v2", "worker-two-competitors");
        var raceStart = new CountDownLatch(1);
        var versionTwoRequest = new AcquireWorkerTaskCommand("riskCheck", "2");
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<AcquireWorkerTaskResult> left = pool.submit(() -> { raceStart.await(); return service.acquire(worker20, versionTwoRequest); });
            Future<AcquireWorkerTaskResult> right = pool.submit(() -> { raceStart.await(); return service.acquire(worker20b, versionTwoRequest); });
            raceStart.countDown();
            var leftResult = left.get(15, TimeUnit.SECONDS);
            var rightResult = right.get(15, TimeUnit.SECONDS);
            assertEquals(1, (leftResult.task() == null ? 0 : 1) + (rightResult.task() == null ? 0 : 1));
            var winner = leftResult.task() != null ? worker20 : worker20b;
            var wonTask = leftResult.task() != null ? leftResult.task() : rightResult.task();
            assertEquals(WorkerReceiptOutcomeEnum.COMPLETED, service.complete(winner, complete(wonTask, "race-receipt", true)).outcome());
        }

        startWorkerRun(runs, owner20, "worker-v2", "worker-duplicate-ack");
        var duplicateTask = service.acquire(worker20, versionTwoRequest).task();
        assertNotNull(duplicateTask);
        var duplicateCommand = complete(duplicateTask, "duplicate-receipt", true);
        var duplicateStart = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptDTO>> futures =
                    java.util.stream.IntStream.range(0, 20).mapToObj(index -> pool.submit(() -> {
                        duplicateStart.await();
                        return service.complete(worker20, duplicateCommand);
                    })).toList();
            duplicateStart.countDown();
            var original = futures.getFirst().get(15, TimeUnit.SECONDS);
            for (var future : futures) assertEquals(original, future.get(15, TimeUnit.SECONDS));
        }
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() ->
                app.getBean(TaskService.class).createTaskQuery().processInstanceId(duplicateTask.processInstanceId()).count() == 1);
        assertEquals(1, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_worker_lease_receipt "
                + "WHERE tenant_id=20 AND job_id=? AND state='COMPLETED'", Integer.class, duplicateTask.taskId()));
    }

    private static void verifyWorkerExpiry(ConfigurableApplicationContext app, JdbcTemplate bootstrap,
                                           ProcessExecutionService runs, FlowableWorkerService service,
                                           ProcessActor owner20, ProcessActor worker20) throws Exception {
        var v1Request = new AcquireWorkerTaskCommand("riskCheck", "1");
        startWorkerRun(runs, owner20, "worker-v1", "worker-expire-while-waiting");
        WorkerTaskDTO waiting = service.acquire(worker20, v1Request).task();
        assertNotNull(waiting);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var future = new java.util.concurrent.atomic.AtomicReference<Future<Boolean>>();
            new TransactionTemplate(new DataSourceTransactionManager(bootstrap.getDataSource())).executeWithoutResult(status -> {
                expireWorkerLease(bootstrap, waiting);
                future.set(pool.submit(() -> {
                    try { service.complete(worker20, complete(waiting, "waiting-receipt", true)); return false; }
                    catch (WorkerLeaseLostException expected) { return true; }
                }));
                await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50)).until(() ->
                        bootstrap.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() "
                                + "AND wait_event_type='Lock' AND query LIKE '%tianji_worker_lease_receipt%FOR UPDATE%'", Integer.class) > 0);
            });
            assertTrue(future.get().get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, app.getBean(ManagementService.class).createExternalWorkerJobQuery().jobId(waiting.taskId()).count());
        expireNativeWorker(bootstrap, waiting.taskId(), waiting.processInstanceId());
        var afterWait = awaitWorker(service, worker20, v1Request);
        assertEquals(waiting.taskId(), afterWait.taskId());
        assertEquals(WorkerReceiptOutcomeEnum.COMPLETED, service.complete(worker20, complete(afterWait, "after-wait", true)).outcome());

        startWorkerRun(runs, owner20, "worker-v3", "worker-slow-native");
        var shortTask = service.acquire(worker20, new AcquireWorkerTaskCommand("riskCheck", "3")).task();
        assertNotNull(shortTask);
        bootstrap.execute("""
                CREATE FUNCTION tianji_process.test_slow_native_worker() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF OLD.handler_cfg_='riskCheck:3' THEN PERFORM pg_sleep(3.5); END IF;
                  RETURN OLD;
                END $$
                """);
        bootstrap.execute("CREATE TRIGGER test_slow_native_worker BEFORE DELETE ON tianji_process.act_ru_external_job "
                + "FOR EACH ROW EXECUTE FUNCTION tianji_process.test_slow_native_worker()");
        long beforeSlowAck = System.nanoTime();
        assertThrows(WorkerLeaseLostException.class, () -> service.complete(worker20, complete(shortTask, "slow-receipt", true)));
        assertTrue(Duration.ofNanos(System.nanoTime() - beforeSlowAck).compareTo(Duration.ofSeconds(3)) >= 0);
        assertEquals("LEASED", bootstrap.queryForObject("SELECT state FROM tianji_process.tianji_worker_lease_receipt WHERE token_sha256=?",
                String.class, WorkerLeaseStore.tokenHash(shortTask.leaseToken())));
        assertEquals(1, app.getBean(ManagementService.class).createExternalWorkerJobQuery().jobId(shortTask.taskId()).count());
        assertEquals(0, app.getBean(TaskService.class).createTaskQuery().processInstanceId(shortTask.processInstanceId()).count());
        bootstrap.execute("DROP TRIGGER test_slow_native_worker ON tianji_process.act_ru_external_job");
    }

    private static WorkerTopicRegistry.Registration workerRegistration(long tenant, String issuer, String subject, String version) {
        return new WorkerTopicRegistry.Registration(tenant, issuer, subject, "riskCheck", version,
                Map.of("requestedAmount", ValueType.INTEGER), Map.of("approved", ValueType.BOOLEAN),
                Map.of("requestedAmount", Set.of("amount")), Map.of("approved", Set.of("approved")), 1,
                Duration.ofMinutes(2), Duration.ofMillis(200), WorkerEffect.TRANSACTIONAL_RECEIPT);
    }

    private static ProcessExecutionService.Snapshot startWorkerRun(ProcessExecutionService runs, ProcessActor actor, String release, String id) {
        return runs.start(actor, new ProcessExecutionService.StartCommand(release, "workerMapping", id, id, "trace-" + id,
                Map.of("amount", 10, "privateFixture", "not-for-worker")));
    }

    private static CompleteWorkerTaskCommand complete(WorkerTaskDTO task, String receipt, boolean approved) {
        return new CompleteWorkerTaskCommand(task.taskId(), task.activityOccurrenceId(), task.leaseToken(), receipt, Map.of("approved", approved));
    }

    private static WorkerTaskDTO awaitWorker(FlowableWorkerService service, ProcessActor actor, AcquireWorkerTaskCommand command) {
        var selected = new java.util.concurrent.atomic.AtomicReference<WorkerTaskDTO>();
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() -> {
            var result = service.acquire(actor, command);
            if (result.task() == null) return false;
            selected.set(result.task());
            return true;
        });
        return selected.get();
    }

    private static void expireWorkerLease(JdbcTemplate jdbc, WorkerTaskDTO task) {
        assertEquals(1, jdbc.update("UPDATE tianji_process.tianji_worker_lease_receipt SET leased_at=clock_timestamp()-interval '5 seconds', "
                + "lease_expires_at=clock_timestamp()-interval '1 second' WHERE token_sha256=?", WorkerLeaseStore.tokenHash(task.leaseToken())));
    }

    private static NativeWorkerProbe probeNativeExternalWorker(ConfigurableApplicationContext app, JdbcTemplate bootstrap) throws Exception {
        String xml = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn" targetNamespace="urn:test">
                  <process id="nativeWorkerProbe" isExecutable="true">
                    <startEvent id="start"/><sequenceFlow id="a" sourceRef="start" targetRef="worker"/>
                    <serviceTask id="worker" flowable:type="external-worker" flowable:topic="native-probe"/>
                    <sequenceFlow id="b" sourceRef="worker" targetRef="after"/>
                    <userTask id="after"/><sequenceFlow id="c" sourceRef="after" targetRef="end"/><endEvent id="end"/>
                  </process>
                </definitions>
                """;
        RepositoryService repository = app.getBean(RepositoryService.class);
        RuntimeService runtime = app.getBean(RuntimeService.class);
        ManagementService management = app.getBean(ManagementService.class);
        for (String tenant : List.of("20", "21")) {
            repository.createDeployment().tenantId(tenant).addString("native-probe.bpmn20.xml", xml).deploy();
        }
        String first = runtime.createProcessInstanceBuilder().processDefinitionKey("nativeWorkerProbe").tenantId("20")
                .variable("allowed", "value").variable("privateFixture", "not-for-worker").start().getId();
        String second = runtime.createProcessInstanceBuilder().processDefinitionKey("nativeWorkerProbe").tenantId("21").start().getId();
        assertTrue(management.createExternalWorkerJobAcquireBuilder().topic("native-probe", Duration.ofMinutes(1))
                .onlyBpmn().tenantId("22").acquireAndLock(1, "other-worker").isEmpty());
        var acquired = management.createExternalWorkerJobAcquireBuilder().topic("native-probe", Duration.ofMinutes(1))
                .onlyBpmn().tenantId("20").acquireAndLock(1, "first-worker");
        assertEquals(1, acquired.size());
        var job = acquired.getFirst();
        assertEquals(first, job.getProcessInstanceId());
        assertEquals("20", job.getTenantId());
        assertEquals("not-for-worker", job.getVariables().get("privateFixture"));
        assertTrue(management.createExternalWorkerJobAcquireBuilder().topic("native-probe", Duration.ofMinutes(1))
                .onlyBpmn().tenantId("20").acquireAndLock(1, "other-worker").isEmpty());
        expireNativeWorker(bootstrap, job.getId(), first);
        assertTrue(management.createExternalWorkerJobAcquireBuilder().topic("native-probe", Duration.ofMinutes(1))
                .onlyBpmn().tenantId("20").acquireAndLock(1, "other-worker").isEmpty());

        // Flowable 8 checks the owner but accepts this completion after the lock has expired.
        management.createExternalWorkerCompletionBuilder(job.getId(), "first-worker").variable("workerResult", "done").complete();
        assertNotNull(runtime.createProcessInstanceQuery().processInstanceId(first).singleResult());
        assertNull(app.getBean(TaskService.class).createTaskQuery().processInstanceId(first).singleResult());
        var completionJob = management.createJobQuery().processInstanceId(first).singleResult();
        assertNotNull(completionJob);
        management.executeJob(completionJob.getId());
        assertEquals("after", app.getBean(TaskService.class).createTaskQuery().processInstanceId(first).singleResult().getTaskDefinitionKey());
        assertEquals("done", runtime.getVariable(first, "workerResult"));

        byte[] mappedXml = new ClassPathResource("fixtures/worker-mapping.bpmn20.xml").getContentAsByteArray();
        var validator = new ProcessModelValidator();
        var mapped = validator.validateBpmn(mappedXml, "workerMapping");
        validator.validateWorkerBindings(mapped, Map.of("amount", ValueType.INTEGER, "privateFixture", ValueType.STRING), null);
        repository.createDeployment().tenantId("20").addBytes("worker-mapping.bpmn20.xml", mapped.xml()).deploy();
        String mappedInstance = runtime.createProcessInstanceBuilder().processDefinitionKey("workerMapping").tenantId("20")
                .variable("amount", 10L).variable("privateFixture", "not-for-worker").start().getId();
        var mappedJobs = management.createExternalWorkerJobAcquireBuilder().topic("riskCheck:1", Duration.ofMinutes(1))
                .onlyBpmn().tenantId("20").acquireAndLock(1, "mapped-worker");
        assertEquals(1, mappedJobs.size());
        assertEquals(Map.of("requestedAmount", 10L), mappedJobs.getFirst().getVariables());
        management.createExternalWorkerCompletionBuilder(mappedJobs.getFirst().getId(), "mapped-worker").variable("approved", true).complete();
        management.executeJob(management.createJobQuery().processInstanceId(mappedInstance).singleResult().getId());
        assertEquals(Boolean.TRUE, runtime.getVariable(mappedInstance, "approved"));

        String emptyMapping = new String(mappedXml, StandardCharsets.UTF_8).replace("workerMapping", "workerEmpty")
                .replace("riskCheck", "riskEmpty")
                .replace("<flowable:externalWorkerInParameter source=\"amount\" target=\"requestedAmount\" tianji:type=\"INTEGER\"/>", "");
        var emptyModel = validator.validateBpmn(emptyMapping.getBytes(StandardCharsets.UTF_8), "workerEmpty");
        validator.validateWorkerBindings(emptyModel, Map.of("privateFixture", ValueType.STRING), null);
        repository.createDeployment().tenantId("20").addBytes("worker-empty.bpmn20.xml", emptyModel.xml()).deploy();
        runtime.createProcessInstanceBuilder().processDefinitionKey("workerEmpty").tenantId("20")
                .variable("privateFixture", "not-for-worker").start();
        var emptyJobs = management.createExternalWorkerJobAcquireBuilder().topic("riskEmpty:1", Duration.ofMinutes(1))
                .onlyBpmn().tenantId("20").acquireAndLock(1, "empty-worker");
        assertEquals(1, emptyJobs.size());
        assertTrue(emptyJobs.getFirst().getVariables().isEmpty());

        var secondJob = management.createExternalWorkerJobAcquireBuilder().topic("native-probe", Duration.ofMinutes(1))
                .onlyBpmn().tenantId("21").acquireAndLock(1, "expired-worker");
        assertEquals(1, secondJob.size());
        assertEquals(second, secondJob.getFirst().getProcessInstanceId());
        expireNativeWorker(bootstrap, secondJob.getFirst().getId(), second);
        assertTrue(management.createExternalWorkerJobAcquireBuilder().topic("native-probe", Duration.ofMinutes(1))
                .onlyBpmn().tenantId("21").acquireAndLock(1, "replacement-worker").isEmpty());
        return new NativeWorkerProbe(second, secondJob.getFirst().getId());
    }

    private static void expireNativeWorker(JdbcTemplate bootstrap, String id, String processInstanceId) {
        assertEquals(1, bootstrap.update("UPDATE tianji_process.act_ru_external_job SET lock_exp_time_=clock_timestamp() - interval '1 second' WHERE id_=?", id));
        assertEquals(1, bootstrap.update("UPDATE tianji_process.act_ru_execution SET lock_time_=clock_timestamp() - interval '1 second' WHERE id_=?", processInstanceId));
    }

    private static void verifyNativeWorkerReset(ConfigurableApplicationContext app, NativeWorkerProbe probe) {
        ManagementService management = app.getBean(ManagementService.class);
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() ->
                management.createExternalWorkerJobQuery().jobId(probe.jobId()).unlocked().count() == 1);
        var replacement = management.createExternalWorkerJobAcquireBuilder().topic("native-probe", Duration.ofMinutes(1))
                .onlyBpmn().tenantId("21").acquireAndLock(1, "replacement-worker");
        assertEquals(1, replacement.size());
        assertEquals(probe.jobId(), replacement.getFirst().getId());
        assertThrows(RuntimeException.class, () -> management.createExternalWorkerCompletionBuilder(probe.jobId(), "expired-worker").complete());
        management.createExternalWorkerCompletionBuilder(probe.jobId(), "replacement-worker").complete();
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() ->
                app.getBean(TaskService.class).createTaskQuery().processInstanceId(probe.instanceId()).count() == 1);
    }

    private record NativeWorkerProbe(String instanceId, String jobId) { }

    private static RoutingCases prepareRoutingCases(ConfigurableApplicationContext app, ProcessModelValidator validator,
                                                    byte[] dmnBytes) throws Exception {
        var process = app.getBean(SpringProcessEngineConfiguration.class);
        var dmn = app.getBean(SpringDmnEngineConfiguration.class);
        var releases = new ProcessReleaseService(process, dmn, validator, true);
        var runs = new ProcessExecutionService(process, dmn, validator);
        var actor = new ProcessActor(20, ProcessActor.SubjectType.USER, "routing-user");
        byte[] bpmn = new ClassPathResource("fixtures/approval-routing.bpmn20.xml").getContentAsByteArray();
        var processModel = new ProcessReleaseService.ModelResource(Type.BPMN, "approvalRouting", bpmn, Map.of("amount", ValueType.INTEGER));
        String oldXml = new String(dmnBytes, StandardCharsets.UTF_8);
        var oldRules = new ProcessReleaseService.ModelResource(Type.DMN, "baselineDecision", dmnBytes, Map.of());
        var oldRelease = releases.publish(actor, "routing-old", List.of(processModel, oldRules));
        String oldReview = startRouting(runs, actor, "routing-old", "old-review", 11);
        String oldBoundary = startRouting(runs, actor, "routing-old", "old-boundary", 10);

        byte[] newer = oldXml.replace("<text>'review'</text>", "<text>'standard'</text>").getBytes(StandardCharsets.UTF_8);
        var newRules = new ProcessReleaseService.ModelResource(Type.DMN, "baselineDecision", newer, Map.of());
        releases.publish(actor, "routing-new", List.of(processModel, newRules));
        String newReview = startRouting(runs, actor, "routing-new", "new-review", 11);

        byte[] sparse = oldXml.replace("&gt; 10", "&gt; 100").getBytes(StandardCharsets.UTF_8);
        releases.publish(actor, "routing-empty", List.of(processModel,
                new ProcessReleaseService.ModelResource(Type.DMN, "baselineDecision", sparse, Map.of())));
        String empty = startRouting(runs, actor, "routing-empty", "empty", 11);

        String any = oldXml.replace("hitPolicy=\"FIRST\"", "hitPolicy=\"ANY\"").replace("&gt; 10", "&gt;= 10");
        releases.publish(actor, "routing-any", List.of(processModel, new ProcessReleaseService.ModelResource(Type.DMN,
                "baselineDecision", any.replace("<text>'review'</text>", "<text>'standard'</text>").getBytes(StandardCharsets.UTF_8), Map.of())));
        String same = startRouting(runs, actor, "routing-any", "same", 10);
        releases.publish(actor, "routing-conflict", List.of(processModel, new ProcessReleaseService.ModelResource(Type.DMN,
                "baselineDecision", any.getBytes(StandardCharsets.UTF_8), Map.of())));
        String conflict = startRouting(runs, actor, "routing-conflict", "conflict", 10);

        var foreign = new ProcessActor(21, ProcessActor.SubjectType.USER, "routing-user");
        assertThrows(IllegalArgumentException.class, () -> releases.getForProcessDefinition(foreign.tenantId(),
                definition(oldRelease, Type.BPMN).engineDefinitionId()));
        releases.publish(foreign, "routing-old", List.of(processModel, newRules));
        String foreignRun = startRouting(runs, foreign, "routing-old", "foreign", 11);
        assertThrows(IllegalArgumentException.class, () -> runs.start(actor, new ProcessExecutionService.StartCommand(
                "routing-old", "approvalRouting", "bad", "bad-routing", "trace", Map.of("amount", "11"))));
        return new RoutingCases(oldReview, oldBoundary, newReview, empty, same, conflict, foreignRun,
                definition(oldRelease, Type.DMN).engineDefinitionId(), definition(oldRelease, Type.DMN).engineDeploymentId(),
                definition(oldRelease, Type.DMN).modelSha256());
    }

    private static String startRouting(ProcessExecutionService runs, ProcessActor actor, String release, String id, int amount) {
        var command = new ProcessExecutionService.StartCommand(release, "approvalRouting", id, id, "trace-" + id, Map.of("amount", amount));
        var first = runs.start(actor, command);
        assertEquals(ProcessExecutionService.State.ACTIVE, first.state());
        assertEquals(first, runs.start(actor, command));
        return first.instanceId();
    }

    private static void verifyRoutingAfterRestart(ConfigurableApplicationContext app, JdbcTemplate bootstrap, RoutingCases cases) {
        TaskService tasks = app.getBean(TaskService.class);
        DmnHistoryService history = app.getBean(DmnHistoryService.class);
        var before = tasks.createTaskQuery().processInstanceId(cases.oldReview()).singleResult();
        assertEquals("wait", before.getTaskDefinitionKey());
        TransactionTemplate transaction = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
        assertThrows(InjectedFailure.class, () -> transaction.execute(status -> {
            tasks.complete(before.getId());
            throw new InjectedFailure();
        }));
        assertEquals("wait", tasks.createTaskQuery().processInstanceId(cases.oldReview()).singleResult().getTaskDefinitionKey());
        assertEquals(0, history.createHistoricDecisionExecutionQuery().instanceId(cases.oldReview()).count());

        bootstrap.update("UPDATE tianji_process.tianji_process_release_definition SET model_sha256=? "
                + "WHERE tenant_id=20 AND release_key='routing-old' AND model_type='DMN'", "0".repeat(64));
        assertThrows(RuntimeException.class, () -> tasks.complete(before.getId()));
        assertEquals("wait", tasks.createTaskQuery().processInstanceId(cases.oldReview()).singleResult().getTaskDefinitionKey());
        bootstrap.update("UPDATE tianji_process.tianji_process_release_definition SET model_sha256=? "
                + "WHERE tenant_id=20 AND release_key='routing-old' AND model_type='DMN'", cases.oldDecisionHash());
        bootstrap.update("UPDATE tianji_process.tianji_process_release_definition SET engine_definition_id=? "
                + "WHERE tenant_id=20 AND release_key='routing-old' AND model_type='DMN'", "missing-pinned-decision");
        assertThrows(RuntimeException.class, () -> tasks.complete(before.getId()));
        assertEquals("wait", tasks.createTaskQuery().processInstanceId(cases.oldReview()).singleResult().getTaskDefinitionKey());
        bootstrap.update("UPDATE tianji_process.tianji_process_release_definition SET engine_definition_id=? "
                + "WHERE tenant_id=20 AND release_key='routing-old' AND model_type='DMN'", cases.oldDecisionId());

        completeWait(tasks, cases.oldReview());
        assertEquals("review", tasks.createTaskQuery().processInstanceId(cases.oldReview()).singleResult().getTaskDefinitionKey());
        var decision = history.createHistoricDecisionExecutionQuery().instanceId(cases.oldReview())
                .tenantId(TENANT).decisionDefinitionId(cases.oldDecisionId()).singleResult();
        assertNotNull(decision);
        assertFalse(decision.isFailed());
        assertEquals(cases.oldDecisionDeploymentId(), decision.getDeploymentId());
        assertEquals("decide", decision.getActivityId());
        assertNotNull(decision.getExecutionId());
        completeWait(tasks, cases.oldBoundary());
        assertEquals("standard", tasks.createTaskQuery().processInstanceId(cases.oldBoundary()).singleResult().getTaskDefinitionKey());
        completeWait(tasks, cases.newReview());
        assertEquals("standard", tasks.createTaskQuery().processInstanceId(cases.newReview()).singleResult().getTaskDefinitionKey());
        completeWait(tasks, cases.empty());
        assertEquals("standard", tasks.createTaskQuery().processInstanceId(cases.empty()).singleResult().getTaskDefinitionKey());
        completeWait(tasks, cases.same());
        assertEquals("standard", tasks.createTaskQuery().processInstanceId(cases.same()).singleResult().getTaskDefinitionKey());
        completeWait(tasks, cases.foreign());
        assertEquals("standard", tasks.createTaskQuery().processInstanceId(cases.foreign()).singleResult().getTaskDefinitionKey());
        assertEquals(1, history.createHistoricDecisionExecutionQuery().instanceId(cases.foreign()).tenantId("21").count());
        var conflictTask = tasks.createTaskQuery().processInstanceId(cases.conflict()).singleResult();
        assertThrows(RuntimeException.class, () -> tasks.complete(conflictTask.getId()));
        assertEquals("wait", tasks.createTaskQuery().processInstanceId(cases.conflict()).singleResult().getTaskDefinitionKey());
        assertEquals(0, history.createHistoricDecisionExecutionQuery().instanceId(cases.conflict()).count());
    }

    private static void completeWait(TaskService tasks, String instanceId) {
        var task = tasks.createTaskQuery().processInstanceId(instanceId).singleResult();
        assertEquals("wait", task.getTaskDefinitionKey());
        tasks.complete(task.getId());
    }

    private record RoutingCases(String oldReview, String oldBoundary, String newReview, String empty, String same,
                                String conflict, String foreign, String oldDecisionId, String oldDecisionDeploymentId,
                                String oldDecisionHash) { }

    private static void verifySchemaGuard(ConfigurableApplicationContext app, JdbcTemplate bootstrap) {
        var verifier = new ProcessApiSchemaVerifier(app.getBean(DataSource.class), "tianji_process");
        verifier.verify();
        Integer checksum = bootstrap.queryForObject("SELECT checksum FROM tianji_process.tianji_process_schema_history WHERE version='2'", Integer.class);
        bootstrap.update("UPDATE tianji_process.tianji_process_schema_history SET checksum=0 WHERE version='2'");
        assertThrows(IllegalStateException.class, verifier::verify);
        bootstrap.update("UPDATE tianji_process.tianji_process_schema_history SET checksum=? WHERE version='2'", checksum);
        bootstrap.execute("ALTER TABLE tianji_process.tianji_process_command_receipt ALTER COLUMN correlation_id DROP NOT NULL");
        assertThrows(IllegalStateException.class, verifier::verify);
        bootstrap.execute("ALTER TABLE tianji_process.tianji_process_command_receipt ALTER COLUMN correlation_id SET NOT NULL");
        bootstrap.execute("DROP INDEX tianji_process.tianji_process_start_receipt_instance_idx");
        assertThrows(IllegalStateException.class, verifier::verify);
        bootstrap.execute("CREATE UNIQUE INDEX tianji_process_start_receipt_instance_idx "
                + "ON tianji_process.tianji_process_command_receipt (tenant_id, resource_id) "
                + "WHERE command_type='START' AND state='APPLIED'");
        bootstrap.execute("ALTER TABLE tianji_process.tianji_process_command_receipt DROP CONSTRAINT tianji_process_decision_receipt_result_check");
        assertThrows(IllegalStateException.class, verifier::verify);
        bootstrap.execute("""
                ALTER TABLE tianji_process.tianji_process_command_receipt
                ADD CONSTRAINT tianji_process_decision_receipt_result_check CHECK (
                    (command_type='DECISION' AND state='PENDING' AND decision_result IS NULL)
                    OR (command_type='DECISION' AND state='APPLIED' AND decision_result IS NOT NULL
                        AND jsonb_typeof(decision_result)='object')
                    OR (command_type<>'DECISION' AND decision_result IS NULL)
                )
                """);
        verifier.verify();
    }

    private static void verifyReleaseTransactions(ConfigurableApplicationContext app, JdbcTemplate bootstrap,
                                                  ProcessModelValidator validator, byte[] bpmn, byte[] dmn) throws Exception {
        var processEngine = app.getBean(SpringProcessEngineConfiguration.class);
        var decisionEngine = app.getBean(org.flowable.dmn.spring.SpringDmnEngineConfiguration.class);
        var service = new ProcessReleaseService(processEngine, decisionEngine, validator);
        var actor = new ProcessActor(20, ProcessActor.SubjectType.USER, "alice");
        var foreign = new ProcessActor(21, ProcessActor.SubjectType.USER, "alice");
        var processModel = new ProcessReleaseService.ModelResource(Type.BPMN, "baseline", bpmn, Map.of("amount", ValueType.INTEGER));
        var decisionModel = new ProcessReleaseService.ModelResource(Type.DMN, "baselineDecision", dmn, Map.of());
        List<ProcessReleaseService.ModelResource> resources = List.of(processModel, decisionModel);
        var release = service.publish(actor, "release-one", resources);
        assertEquals(2, release.definitions().size());
        assertEquals(64, release.manifestSha256().length());
        assertEquals(release, service.publish(actor, "release-one", List.of(decisionModel, processModel)));
        assertEquals(release, service.get(actor, "release-one"));
        assertEquals(Map.of("amount", ValueType.INTEGER), definition(release, Type.DMN).inputs());
        assertEquals(assertThrows(IllegalArgumentException.class, () -> service.get(foreign, "release-one")).getMessage(),
                assertThrows(IllegalArgumentException.class, () -> service.get(actor, "missing")).getMessage());
        byte[] changed = new String(bpmn, StandardCharsets.UTF_8).replace("name=\"Baseline\"", "name=\"Changed\"")
                .getBytes(StandardCharsets.UTF_8);
        var changedModel = new ProcessReleaseService.ModelResource(Type.BPMN, "baseline", changed, Map.of("amount", ValueType.INTEGER));
        assertThrows(IllegalStateException.class, () -> service.publish(actor, "release-one", List.of(changedModel, decisionModel)));
        var changedSchema = new ProcessReleaseService.ModelResource(Type.BPMN, "baseline", bpmn, Map.of("amount", ValueType.STRING));
        assertThrows(IllegalStateException.class, () -> service.publish(actor, "release-one", List.of(changedSchema, decisionModel)));
        var foreignRelease = service.publish(foreign, "release-one", resources);
        assertNotEquals(definition(release, Type.BPMN).engineDefinitionId(), definition(foreignRelease, Type.BPMN).engineDefinitionId());
        assertEquals(release, service.get(actor, "release-one"));
        var publisher = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "alice", "https://idp-a.example/tenant/20");
        service.publish(publisher, "issuer-release", resources);
        assertEquals(publisher.issuerFingerprint(), bootstrap.queryForObject("SELECT publisher_issuer "
                + "FROM tianji_process.tianji_process_release WHERE tenant_id=20 AND release_key='issuer-release'", String.class));

        var bound = definition(release, Type.DMN);
        var pinned = new FlowablePinnedDecisionExecutor(decisionEngine);
        var model = validator.validateDmn(dmn, "baselineDecision");
        var binding = new FlowablePinnedDecisionExecutor.Binding(actor.tenantId(), bound.engineDefinitionId(),
                bound.engineDeploymentId(), bound.engineVersion(), model);
        assertEquals("standard", pinned.evaluate(binding, Map.of("amount", 10)).outputs().getFirst().get("route"));

        long beforeProcess = processEngine.getRepositoryService().createDeploymentQuery().deploymentTenantId(TENANT).count();
        long beforeDmn = decisionEngine.getDmnRepositoryService().createDeploymentQuery().deploymentTenantId(TENANT).count();
        assertThrows(RuntimeException.class, () -> service.publish(actor, "rollback-release", resources));
        assertEquals(0, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_release "
                + "WHERE tenant_id=20 AND release_key='rollback-release'", Integer.class));
        assertEquals(beforeProcess, processEngine.getRepositoryService().createDeploymentQuery().deploymentTenantId(TENANT).count());
        assertEquals(beforeDmn, decisionEngine.getDmnRepositoryService().createDeploymentQuery().deploymentTenantId(TENANT).count());

        try (var pool = Executors.newFixedThreadPool(8)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<ProcessReleaseService.Release>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                start.await();
                return service.publish(actor, "concurrent-release", resources);
            }));
            start.countDown();
            var first = futures.getFirst().get(20, TimeUnit.SECONDS);
            for (Future<ProcessReleaseService.Release> future : futures) assertEquals(first, future.get(20, TimeUnit.SECONDS));
            assertEquals(2, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_release_definition "
                    + "WHERE tenant_id=20 AND release_key='concurrent-release'", Integer.class));

            CountDownLatch versionsStart = new CountDownLatch(1);
            Future<ProcessReleaseService.Release> one = pool.submit(() -> {
                versionsStart.await(); return service.publish(actor, "version-one", resources);
            });
            Future<ProcessReleaseService.Release> two = pool.submit(() -> {
                versionsStart.await(); return service.publish(actor, "version-two", resources);
            });
            versionsStart.countDown();
            var firstVersion = one.get(20, TimeUnit.SECONDS);
            var secondVersion = two.get(20, TimeUnit.SECONDS);
            assertNotEquals(definition(firstVersion, Type.BPMN).engineVersion(), definition(secondVersion, Type.BPMN).engineVersion());
            assertNotEquals(definition(firstVersion, Type.DMN).engineVersion(), definition(secondVersion, Type.DMN).engineVersion());

            try (var connection = Objects.requireNonNull(bootstrap.getDataSource()).getConnection()) {
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) { statement.execute("SELECT pg_advisory_xact_lock(72360422027)"); }
                Future<ProcessReleaseService.Release> pending = pool.submit(() -> service.publish(actor, "delayed-release", resources));
                await().atMost(Duration.ofSeconds(10)).until(() -> bootstrap.queryForObject(
                        "SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND NOT granted", Integer.class) > 0);
                assertThrows(IllegalArgumentException.class, () -> service.get(actor, "delayed-release"));
                assertEquals(0, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_release "
                        + "WHERE tenant_id=20 AND release_key='delayed-release'", Integer.class));
                connection.commit();
                assertEquals("delayed-release", pending.get(20, TimeUnit.SECONDS).releaseKey());
            }
        }
    }

    private static ProcessReleaseService.Definition definition(ProcessReleaseService.Release release, Type type) {
        return release.definitions().stream().filter(item -> item.type() == type).findFirst().orElseThrow();
    }

    private static void verifyProcessStarts(ConfigurableApplicationContext app, JdbcTemplate bootstrap,
                                            ProcessModelValidator validator) throws Exception {
        var engine = app.getBean(SpringProcessEngineConfiguration.class);
        var dmn = app.getBean(org.flowable.dmn.spring.SpringDmnEngineConfiguration.class);
        var service = new ProcessExecutionService(engine, dmn, validator);
        var actor = new ProcessActor(20, ProcessActor.SubjectType.USER, "alice");
        var other = new ProcessActor(20, ProcessActor.SubjectType.USER, "bob");
        var foreign = new ProcessActor(21, ProcessActor.SubjectType.USER, "alice");
        var request = new ProcessExecutionService.StartCommand("release-one", "baseline", "order-1", "command-1",
                "correlation-1", Map.of("amount", 10));
        var started = service.start(actor, request);
        assertEquals(ProcessExecutionService.State.ACTIVE, started.state());
        assertEquals("order-1", started.businessKey());
        assertEquals("correlation-1", started.correlationId());
        assertEquals(started, service.get(actor, started.instanceId()));
        var replay = new ProcessExecutionService.StartCommand("release-one", "baseline", "order-1", "command-1",
                "correlation-1", Map.of("amount", 10L));
        assertEquals(started, service.start(actor, replay));
        assertEquals(10L, engine.getRuntimeService().getVariable(started.instanceId(), "amount"));
        assertThrows(IllegalStateException.class, () -> service.start(actor,
                new ProcessExecutionService.StartCommand("release-one", "baseline", "order-1", "command-1",
                        "correlation-1", Map.of("amount", 11))));
        assertThrows(IllegalStateException.class, () -> service.start(actor,
                new ProcessExecutionService.StartCommand("release-one", "baseline", "order-1", "command-1",
                        "another-correlation", Map.of("amount", 10))));
        assertThrows(IllegalArgumentException.class, () -> service.start(actor,
                new ProcessExecutionService.StartCommand("release-one", "baseline", "order-1", "invalid-input",
                        "correlation-1", Map.of("amount", 10.0))));
        assertEquals(assertThrows(IllegalArgumentException.class, () -> service.get(other, started.instanceId())).getMessage(),
                assertThrows(IllegalArgumentException.class, () -> service.get(foreign, started.instanceId())).getMessage());
        var otherStarted = service.start(other, request);
        assertNotEquals(started.instanceId(), otherStarted.instanceId());
        var issuerA = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "same-subject", "https://idp-a.example/tenant/20");
        var issuerB = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "same-subject", "https://idp-b.example/tenant/20");
        var issuerRequest = new ProcessExecutionService.StartCommand("release-one", "baseline", "issuer-order", "issuer-command",
                "issuer-correlation", Map.of("amount", 10));
        var issuerInstance = service.start(issuerA, issuerRequest);
        assertThrows(IllegalArgumentException.class, () -> service.get(issuerB, issuerInstance.instanceId()));
        assertThrows(IllegalArgumentException.class, () -> service.get(new ProcessActor(20, ProcessActor.SubjectType.USER, "same-subject"),
                issuerInstance.instanceId()));
        assertNotEquals(issuerInstance.instanceId(), service.start(issuerB, issuerRequest).instanceId());
        assertEquals(issuerInstance, service.start(issuerA, issuerRequest));
        assertEquals(0, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_command_receipt "
                + "WHERE tenant_id=20 AND command_id='invalid-input'", Integer.class));

        long previous = engine.getRuntimeService().createProcessInstanceQuery().processInstanceBusinessKey("rollback-order").count();
        assertThrows(RuntimeException.class, () -> service.start(actor,
                new ProcessExecutionService.StartCommand("release-one", "baseline", "rollback-order", "rollback-command",
                        "correlation-rollback", Map.of("amount", 10))));
        assertEquals(previous, engine.getRuntimeService().createProcessInstanceQuery().processInstanceBusinessKey("rollback-order").count());
        assertEquals(0, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_command_receipt "
                + "WHERE tenant_id=20 AND command_id='rollback-command'", Integer.class));

        var releases = new ProcessReleaseService(engine, dmn, validator);
        byte[] baseline = new ClassPathResource("fixtures/baseline.bpmn20.xml").getContentAsByteArray();
        var corruptRelease = releases.publish(actor, "corrupt-release", List.of(new ProcessReleaseService.ModelResource(
                Type.BPMN, "baseline", baseline, Map.of("amount", ValueType.INTEGER))));
        var bound = definition(corruptRelease, Type.BPMN);
        bootstrap.update("UPDATE tianji_process.tianji_process_release_definition SET model_sha256=? "
                + "WHERE tenant_id=20 AND release_key='corrupt-release'", "0".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> service.start(actor,
                new ProcessExecutionService.StartCommand("corrupt-release", "baseline", "tamper-order", "tamper-hash",
                        "tamper-correlation", Map.of("amount", 10))));
        bootstrap.update("UPDATE tianji_process.tianji_process_release_definition SET model_sha256=?, engine_version=? "
                + "WHERE tenant_id=20 AND release_key='corrupt-release'", bound.modelSha256(), bound.engineVersion() + 100);
        assertThrows(IllegalArgumentException.class, () -> service.start(actor,
                new ProcessExecutionService.StartCommand("corrupt-release", "baseline", "tamper-order", "tamper-version",
                        "tamper-correlation", Map.of("amount", 10))));
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceBusinessKey("tamper-order").count());
        assertEquals(0, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_command_receipt "
                + "WHERE tenant_id=20 AND command_id IN ('tamper-hash','tamper-version')", Integer.class));

        var concurrent = new ProcessExecutionService.StartCommand("release-one", "baseline", "order-concurrent", "command-concurrent",
                "correlation-concurrent", Map.of("amount", 10));
        try (var pool = Executors.newFixedThreadPool(8)) {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<ProcessExecutionService.Snapshot>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> { gate.await(); return service.start(actor, concurrent); }));
            gate.countDown();
            var first = futures.getFirst().get(20, TimeUnit.SECONDS);
            for (Future<ProcessExecutionService.Snapshot> future : futures) assertEquals(first, future.get(20, TimeUnit.SECONDS));
            assertEquals(1, engine.getRuntimeService().createProcessInstanceQuery().processInstanceBusinessKey("order-concurrent").count());
        }

        String instant = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:test">
                  <process id="instant" isExecutable="true"><startEvent id="start"/>
                    <sequenceFlow id="flow" sourceRef="start" targetRef="end"/><endEvent id="end"/>
                  </process>
                </definitions>
                """;
        releases.publish(actor, "instant-release", List.of(new ProcessReleaseService.ModelResource(Type.BPMN, "instant",
                instant.getBytes(StandardCharsets.UTF_8), Map.of())));
        var instantRequest = new ProcessExecutionService.StartCommand("instant-release", "instant", "instant-order",
                "instant-command", "instant-correlation", Map.of());
        var completed = service.start(actor, instantRequest);
        assertEquals(ProcessExecutionService.State.COMPLETED, completed.state());
        assertNotNull(completed.finishedAt());
        assertEquals(completed, service.start(actor, instantRequest));

        engine.getRuntimeService().deleteProcessInstance(started.instanceId(), "cancelled in test");
        assertEquals(ProcessExecutionService.State.CANCELLED, service.get(actor, started.instanceId()).state());
        assertEquals(started.instanceId(), service.start(actor, request).instanceId());
        assertEquals(ProcessExecutionService.State.CANCELLED, service.start(actor, request).state());
    }

    private static void verifyDecisionApplications(ConfigurableApplicationContext app, JdbcTemplate bootstrap,
                                                   ProcessModelValidator validator, byte[] dmnXml) throws Exception {
        var process = app.getBean(SpringProcessEngineConfiguration.class);
        var engine = app.getBean(org.flowable.dmn.spring.SpringDmnEngineConfiguration.class);
        var service = new ProcessDecisionService(process, engine, validator);
        var actor = new ProcessActor(20, ProcessActor.SubjectType.USER, "alice");
        var other = new ProcessActor(20, ProcessActor.SubjectType.USER, "bob");
        var command = new ProcessDecisionService.EvaluateCommand("release-one", "baselineDecision", "decision-1",
                "decision-correlation", Map.of("amount", 10));
        long beforeProcesses = process.getRuntimeService().createProcessInstanceQuery().processInstanceTenantId(TENANT).count();
        var result = service.evaluate(actor, command);
        assertEquals("standard", result.outputs().getFirst().get("route"));
        assertEquals(List.of("row:1"), result.matchedRuleIds());
        assertEquals(beforeProcesses, process.getRuntimeService().createProcessInstanceQuery().processInstanceTenantId(TENANT).count());
        var replay = new ProcessDecisionService.EvaluateCommand("release-one", "baselineDecision", "decision-1",
                "decision-correlation", Map.of("amount", 10L));
        assertEquals(result, service.evaluate(actor, replay));
        assertEquals(1, engine.getDmnHistoryService().createHistoricDecisionExecutionQuery().tenantId(TENANT)
                .scopeType("tianji-decision").instanceId(result.executionId()).count());
        String saved = bootstrap.queryForObject("SELECT decision_result::text FROM tianji_process.tianji_process_command_receipt "
                + "WHERE tenant_id=20 AND subject_type='USER' AND subject_id='alice' AND command_type='DECISION' AND command_id='decision-1'", String.class);
        bootstrap.update("UPDATE tianji_process.tianji_process_command_receipt SET decision_result=CAST(? AS jsonb) "
                        + "WHERE tenant_id=20 AND subject_type='USER' AND subject_id='alice' AND command_type='DECISION' AND command_id='decision-1'",
                "{\"codecVersion\":1,\"outputs\":[],\"matchedRuleIds\":[\"row:1\"]}");
        assertThrows(IllegalStateException.class, () -> service.evaluate(actor, replay));
        assertEquals(1, engine.getDmnHistoryService().createHistoricDecisionExecutionQuery().tenantId(TENANT)
                .scopeType("tianji-decision").instanceId(result.executionId()).count());
        bootstrap.update("UPDATE tianji_process.tianji_process_command_receipt SET decision_result=CAST(? AS jsonb) "
                        + "WHERE tenant_id=20 AND subject_type='USER' AND subject_id='alice' AND command_type='DECISION' AND command_id='decision-1'", saved);
        assertNotEquals(result.executionId(), service.evaluate(other, command).executionId());
        var issuerA = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "same-subject", "https://idp-a.example/tenant/20");
        var issuerB = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "same-subject", "https://idp-b.example/tenant/20");
        var issuerCommand = new ProcessDecisionService.EvaluateCommand("release-one", "baselineDecision", "issuer-decision",
                "issuer-correlation", Map.of("amount", 10));
        var issuerResult = service.evaluate(issuerA, issuerCommand);
        assertEquals(issuerResult, service.evaluate(issuerA, issuerCommand));
        assertNotEquals(issuerResult.executionId(), service.evaluate(issuerB, issuerCommand).executionId());
        assertThrows(IllegalStateException.class, () -> service.evaluate(actor,
                new ProcessDecisionService.EvaluateCommand("release-one", "baselineDecision", "decision-1",
                        "decision-correlation", Map.of("amount", 11))));
        assertThrows(IllegalStateException.class, () -> service.evaluate(actor,
                new ProcessDecisionService.EvaluateCommand("release-one", "baselineDecision", "decision-1",
                        "another-correlation", Map.of("amount", 10))));
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(actor,
                new ProcessDecisionService.EvaluateCommand("release-one", "baselineDecision", "bad-input",
                        "correlation", Map.of("amount", 10.0))));
        assertEquals(0, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_command_receipt "
                + "WHERE tenant_id=20 AND command_id='bad-input'", Integer.class));

        var concurrent = new ProcessDecisionService.EvaluateCommand("release-one", "baselineDecision", "decision-concurrent",
                "decision-concurrent-correlation", Map.of("amount", 11));
        try (var pool = Executors.newFixedThreadPool(8)) {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<ProcessDecisionService.Result>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> { gate.await(); return service.evaluate(actor, concurrent); }));
            gate.countDown();
            var first = futures.getFirst().get(20, TimeUnit.SECONDS);
            for (Future<ProcessDecisionService.Result> future : futures) assertEquals(first, future.get(20, TimeUnit.SECONDS));
            assertEquals(List.of("row:2"), first.matchedRuleIds());
            assertEquals(1, engine.getDmnHistoryService().createHistoricDecisionExecutionQuery().tenantId(TENANT)
                    .scopeType("tianji-decision").instanceId(first.executionId()).count());
        }
        long beforeFailure = engine.getDmnHistoryService().createHistoricDecisionExecutionQuery()
                .tenantId(TENANT).decisionKey("baselineDecision").count();
        assertThrows(RuntimeException.class, () -> service.evaluate(actor,
                new ProcessDecisionService.EvaluateCommand("release-one", "baselineDecision", "rollback-command",
                        "rollback-correlation", Map.of("amount", 10))));
        assertEquals(beforeFailure, engine.getDmnHistoryService().createHistoricDecisionExecutionQuery()
                .tenantId(TENANT).decisionKey("baselineDecision").count());
        assertEquals(0, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_command_receipt "
                + "WHERE tenant_id=20 AND command_type='DECISION' AND command_id='rollback-command'", Integer.class));

        var releases = new ProcessReleaseService(process, engine, validator);
        String numeric = new String(dmnXml, StandardCharsets.UTF_8).replace("baselineDecision", "moneyDecision")
                .replace("name=\"route\" typeRef=\"string\"", "name=\"route\" typeRef=\"number\"")
                .replace("'standard'", "42").replace("'review'", "-1")
                .replaceFirst("<rule>", "<rule id='approved'>");
        releases.publish(actor, "numeric-release", List.of(new ProcessReleaseService.ModelResource(Type.DMN, "moneyDecision",
                numeric.getBytes(StandardCharsets.UTF_8), Map.of())));
        var numericCommand = new ProcessDecisionService.EvaluateCommand("numeric-release", "moneyDecision", "numeric-request",
                "numeric-correlation", Map.of("amount", 10));
        var numericResult = service.evaluate(actor, numericCommand);
        assertEquals(42L, numericResult.outputs().getFirst().get("route"));
        assertEquals(List.of("approved"), numericResult.matchedRuleIds());
        assertEquals(numericResult, service.evaluate(actor, numericCommand));
        String noMatch = new String(dmnXml, StandardCharsets.UTF_8).replace("baselineDecision", "emptyDecision")
                .replace("&gt; 10", "&gt; 20");
        releases.publish(actor, "empty-release", List.of(new ProcessReleaseService.ModelResource(Type.DMN, "emptyDecision",
                noMatch.getBytes(StandardCharsets.UTF_8), Map.of())));
        var empty = service.evaluate(actor, new ProcessDecisionService.EvaluateCommand("empty-release", "emptyDecision", "empty-request",
                "empty-correlation", Map.of("amount", 15)));
        assertTrue(empty.outputs().isEmpty());
        assertTrue(empty.matchedRuleIds().isEmpty());
        String overlap = new String(dmnXml, StandardCharsets.UTF_8).replace("baselineDecision", "invalidDecision")
                .replace("hitPolicy=\"FIRST\"", "hitPolicy=\"UNIQUE\"").replace("&gt; 10", "&lt;= 10");
        releases.publish(actor, "overlap-release", List.of(new ProcessReleaseService.ModelResource(Type.DMN, "invalidDecision",
                overlap.getBytes(StandardCharsets.UTF_8), Map.of())));
        assertThrows(IllegalStateException.class, () -> service.evaluate(actor,
                new ProcessDecisionService.EvaluateCommand("overlap-release", "invalidDecision", "overlap-request",
                        "overlap-correlation", Map.of("amount", 10))));
        assertEquals(0, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_command_receipt "
                + "WHERE tenant_id=20 AND command_type='DECISION' AND command_id='overlap-request'", Integer.class));
        assertThrows(RuntimeException.class, () -> bootstrap.update("""
                INSERT INTO tianji_process.tianji_process_command_receipt
                    (tenant_id, subject_type, subject_id, command_type, command_id, request_sha256, release_key,
                     correlation_id, state, resource_id, created_at)
                VALUES (20, 'USER', 'alice', 'DECISION', 'invalid-null', ?, 'release-one', 'c', 'APPLIED', 'x', clock_timestamp())
                """, "a".repeat(64)));
    }

    private static void verifyHttpAgainstDatabase(ConfigurableApplicationContext app, ProcessModelValidator validator) throws Exception {
        var process = app.getBean(SpringProcessEngineConfiguration.class);
        var dmn = app.getBean(org.flowable.dmn.spring.SpringDmnEngineConfiguration.class);
        var guard = mock(PermissionExecutionGuard.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ProcessController(new ProcessReleaseService(process, dmn, validator),
                        new ProcessExecutionService(process, dmn, validator), new ProcessDecisionService(process, dmn, validator),
                        new TianjiProcessAccess(guard, Clock.systemUTC())))
                .addFilters(new VerifiedProcessIssuerFilter())
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
        var mapper = JsonMapper.builder().build();
        Instant now = Instant.now();
        try {
            authenticateService("worker", "20", now);
            String bpmnXml = new String(new ClassPathResource("fixtures/baseline.bpmn20.xml").getContentAsByteArray(), StandardCharsets.UTF_8);
            String dmnXml = new String(new ClassPathResource("fixtures/baseline.dmn").getContentAsByteArray(), StandardCharsets.UTF_8);
            var publish = new PublishProcessReleaseCommand("http-release", List.of(
                    new ProcessModelResource(ProcessModelTypeEnum.BPMN, "baseline", bpmnXml, Map.of("amount", ProcessValueTypeEnum.INTEGER)),
                    new ProcessModelResource(ProcessModelTypeEnum.DMN, "baselineDecision", dmnXml, Map.of())));
            var release = mapper.readValue(mvc.perform(post("/api/tianji/v1/process-releases").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(publish))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), ProcessReleaseDTO.class);
            assertEquals(2, release.models().size());
            mvc.perform(get("/api/tianji/v1/process-releases/http-release")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.releaseVersion").value("http-release"));
            var start = new StartProcessCommand("baseline", "http-release", "http-order", "http-command", "http-trace", Map.of("amount", 10));
            byte[] startBody = mapper.writeValueAsBytes(start);
            var response = mvc.perform(post("/api/tianji/v1/process-instances").contentType(MediaType.APPLICATION_JSON).content(startBody))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
            var created = mapper.readValue(response.getContentAsByteArray(), ProcessInstanceDTO.class);
            var repeated = mapper.readValue(mvc.perform(post("/api/tianji/v1/process-instances").contentType(MediaType.APPLICATION_JSON)
                    .content(startBody)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), ProcessInstanceDTO.class);
            assertEquals(created, repeated);
            assertEquals(1, process.getRuntimeService().createProcessInstanceQuery().processInstanceBusinessKey("http-order").count());
            mvc.perform(get("/api/tianji/v1/process-instances/" + created.instanceId())
                    .header("X-Egon-Tianji-Tenant", "21")).andExpect(status().isConflict());
            var decision = new EvaluateDecisionCommand("baselineDecision", "http-release", "http-decision", "http-trace", Map.of("amount", 10));
            byte[] decisionBody = mapper.writeValueAsBytes(decision);
            var evaluated = mapper.readValue(mvc.perform(post("/api/tianji/v1/decisions/evaluate").contentType(MediaType.APPLICATION_JSON)
                    .content(decisionBody)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), DecisionResultDTO.class);
            assertEquals("standard", evaluated.outputs().getFirst().get("route"));
            assertEquals(evaluated, mapper.readValue(mvc.perform(post("/api/tianji/v1/decisions/evaluate").contentType(MediaType.APPLICATION_JSON)
                    .content(decisionBody)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), DecisionResultDTO.class));
            authenticateService("other-worker", "20", now);
            mvc.perform(get("/api/tianji/v1/process-instances/" + created.instanceId())).andExpect(status().isNotFound());
            authenticateService("worker", "20", now, "https://another-idp.example/issuer");
            mvc.perform(get("/api/tianji/v1/process-instances/" + created.instanceId())).andExpect(status().isNotFound());
            authenticateService("worker", "21", now);
            mvc.perform(get("/api/tianji/v1/process-instances/" + created.instanceId())).andExpect(status().isNotFound());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void authenticateService(String subject, String tenant, Instant now) {
        authenticateService(subject, tenant, now, "https://idp.example/issuer");
    }

    private static void authenticateService(String subject, String tenant, Instant now, String issuer) {
        var principal = new ServiceIdentityPrincipal(subject, tenant, subject, "token-id", URI.create("https://api.example/tianji"),
                1, Set.of("tianji:process:read"), "xingyuan", subject, "test", "credential", now.minusSeconds(30), now.plusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(), URI.create(issuer)));
    }

    private static ConfigurableApplicationContext context(Class<?> source, String url, String user, String... extra) {
        return context(new Class<?>[] {source, TestDelivery.class}, url, user, extra);
    }

    private static ConfigurableApplicationContext context(Class<?>[] sources, String url, String user, String... extra) {
        String[] args = new String[extra.length + 5];
        args[0] = "--spring.datasource.url=" + url;
        args[1] = "--spring.datasource.username=" + user;
        args[2] = "--spring.main.banner-mode=off";
        // This integration test checks atomicity, not the component's 100 ms performance budget.
        args[3] = "--egon.cola.component.transactional-outbox.state-machine.execution-timeout=PT1S";
        args[4] = "--egon.cola.component.transactional-outbox.state-machine.claim-evaluation-timeout=PT2S";
        System.arraycopy(extra, 0, args, 5, extra.length);
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
                    return DeliveryResult.retryableFailure("TEST_UNDELIVERED", "No test delivery target is configured");
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
