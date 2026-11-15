package top.egon.cola.platform.tianji.process.server.acceptance;

import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.client.AgentClientException;
import top.egon.cola.platform.tianji.contract.orchestration.*;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.*;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO.TemplateRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.HumanBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.MappingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationStartCommand.AgentTaskOverrideVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.*;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.bridge.*;
import top.egon.cola.platform.tianji.process.server.execution.*;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.*;
import top.egon.cola.platform.tianji.process.server.release.draft.*;
import top.egon.cola.platform.tianji.process.server.task.FlowableHumanTaskService;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskCompletionObservation;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskReceiptStore;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProcessAgentFusionIT {
    static final String PS_URL = "tianji.process.agent-fusion.postgres.test.url";
    static final String PS_SCHEMA = "tianji_process";
    static final String ISSUER = "https://idp.test/fusion";
    static final String SOURCE = "process-fusion";
    static final String PRODUCER = "agent-fusion";
    static final String DEFINITION = "fusionFlow";
    private static final String CHAIN_DEFINITION = "fusionAgentChain";
    static final String TEMPLATE = "fusionReview";
    static final String CANDIDATE = "fusion-reviewer-v1";
    static final String CANDIDATE_HASH = "a".repeat(64);
    static final ProcessActor STARTER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "starter", ISSUER);
    static final ProcessActor REVIEWER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "reviewer", ISSUER);
    static final ProcessActor BRIDGE_ACTOR = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "process-bridge", ISSUER);
    static final ProcessActor PRODUCER_ACTOR = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "agent-producer", ISSUER);
    static final AgentResultProducerRegistry.Registration RECEPTION =
            new AgentResultProducerRegistry.Registration(PRODUCER_ACTOR, PRODUCER, SOURCE, BRIDGE_ACTOR);
    static final AgentDispatchStore.TypedRoute ROUTE =
            new AgentDispatchStore.TypedRoute(BRIDGE_ACTOR, SOURCE, Duration.ofSeconds(15), Duration.ofSeconds(60));
    static final Map<String, Object> AMOUNT = Map.of("type", "integer", "minimum", 1, "maximum", 1000);
    static final Map<String, Object> CASE = object(Map.of("amount", AMOUNT)).toWire();
    static final Map<String, Object> ASSESSMENT = object(Map.of("approved", Map.of("type", "boolean"),
            "category", Map.of("type", "string"))).toWire();
    static final ProcessSchemaVO AGENT_INPUT = object(Map.of("claim", CASE));
    static final ProcessSchemaVO AGENT_OUTPUT = object(Map.of("assessment", ASSESSMENT));
    static final ProcessSchemaVO PARAMETERS = object(Map.of("lane", Map.of("type", "string")));
    static final ProcessSchemaVO EMPTY = object(Map.of());

    @Test
    void early_outbox_delivery_survives_bridge_restart_and_advances_human_agent_human_once() throws Exception {
        var database = ProcessDatabase.install(PS_URL);
        try (var process = database.open(); var agent = new AgentTaskSnapshotIT.AgentBoundary(process)) {
            var template = agent.publish(1);
            process.publish(List.of(template), template);
            var root = process.start("early", Map.of()).getInstanceId();
            process.completeHuman(root, Map.of("amount", 17L));

            agent.loseNextSubmitResponse(() -> {
                var event = agent.executeAndDeliver(Map.of("assessment", Map.of("approved", true, "category", "travel")));
                assertEquals(AgentResultReceiptStateEnum.RECEIVED, event.state());
                assertEquals(1, process.count("act_ru_external_job", "PROCESS_INSTANCE_ID_=?", root));
                assertEquals(0, process.count("act_ru_task", "PROC_INST_ID_=?", root));
                process.correlate();
                assertEquals(0, process.inboxFailures(event.eventId()));
                assertEquals("PENDING", process.inboxState(event.eventId()));
            });
            var dropped = assertThrows(AgentClientException.class, () -> process.bridge(agent.client()).runOnce());
            assertEquals(0, dropped.httpStatus(), "the Agent side committed the response before the connection closed");
            assertEquals(1, agent.runCount());
            assertEquals("PENDING", process.dispatchState(root));
            process.close();

            try (var reopened = database.open()) {
                agent.connectInbox(reopened);
                reopened.awaitNativeLeaseExpiry(root);
                assertEquals(AgentStartWorker.Outcome.ACKNOWLEDGED, reopened.bridge(agent.client()).runOnce());
                assertEquals("ACKNOWLEDGED", reopened.dispatchState(root));
                reopened.runAsync(root);
                reopened.reconcileUntilApplied(agent.eventId());
                assertEquals("APPLIED", agent.repeatDelivery().state().name());
                assertEquals(1, reopened.count("act_ru_task", "PROC_INST_ID_=?", root));
                assertEquals("COMPLETED", reopened.node(root, "assess").get("state"));
                assertEquals("true", reopened.inputText(root, "review", "assessment", "approved"));
                assertEquals("travel", reopened.inputText(root, "review", "assessment", "category"));
                reopened.completeHuman(root, Map.of());
                assertEquals(0, reopened.count("act_ru_execution", "PROC_INST_ID_=?", root));
                assertEquals(2, reopened.count("act_hi_taskinst", "PROC_INST_ID_=? AND END_TIME_ IS NOT NULL", root));
                assertEquals(1, reopened.count("tianji_process_node_execution", "instance_id=? AND node_id='review' AND state='COMPLETED'", root));
                assertEquals(1, agent.runCount());
                assertEquals(1, agent.submissions());
                assertEquals(1, reopened.bridge(agent.client()).reconcileOnce(8, Duration.ofSeconds(1)).closed());
                assertEquals("CLOSED", reopened.dispatchState(root));

                reopened.registerTemplate(template);
                var invalidRoot = reopened.start("invalid-output", Map.of()).getInstanceId();
                reopened.completeHuman(invalidRoot, Map.of("amount", 17L));
                assertEquals(AgentStartWorker.Outcome.ACKNOWLEDGED, reopened.bridge(agent.client()).runOnce());
                reopened.runAsync(invalidRoot);
                assertEquals("APPLIED", agent.repeatDelivery().state().name());
                assertEquals(0, reopened.count("act_ru_task", "PROC_INST_ID_=?", invalidRoot));
                assertEquals("WAITING", reopened.node(invalidRoot, "assess").get("state"));
                agent.executeAndDeliver(Map.of("assessment", Map.of("approved", "invalid", "category", "travel")));
                assertEquals(AgentRunStatusEnum.FAILED, agent.eventStatus());
                reopened.reconcileUntilApplied(agent.eventId());
                assertEquals("FAILED", reopened.node(invalidRoot, "assess").get("state"));
                assertNull(reopened.node(invalidRoot, "assess").get("output_json"));
                assertEquals(1, reopened.count("act_hi_taskinst", "PROC_INST_ID_=? AND END_TIME_ IS NOT NULL", invalidRoot));
                assertEquals(0, reopened.count("tianji_process_node_execution", "instance_id=? AND node_id='review'", invalidRoot));
                assertEquals(2, agent.runCount());

                reopened.publishRetryRelease(template);
                var retryRoot = reopened.start("retryable-output", Map.of()).getInstanceId();
                reopened.completeHuman(retryRoot, Map.of("amount", 17L));
                assertEquals(AgentStartWorker.Outcome.ACKNOWLEDGED, reopened.bridge(agent.client()).runOnce());
                reopened.runAsync(retryRoot);
                agent.executeAndDeliver(Map.of("assessment", Map.of("approved", "invalid", "category", "travel")));
                reopened.reconcileUntilApplied(agent.eventId());
                var first = reopened.node(retryRoot, "assess");
                assertEquals("FAILED", first.get("state"));
                assertEquals("NONE", first.get("side_effect_outcome"));
                assertEquals("INVALID_OUTPUT", first.get("error_code"));
                reopened.fireNativeRetry(retryRoot);
                var next = reopened.node(retryRoot, "assess");
                assertEquals(2, next.get("attempt"));
                for (String fixed : List.of("occurrence_id", "node_deadline_at", "input_sha256", "configuration_sha256")) {
                    assertEquals(first.get(fixed), next.get(fixed), fixed);
                }
                assertNotEquals(first.get("node_execution_ref"), next.get("node_execution_ref"));
                assertNotEquals(first.get("native_job_id"), next.get("native_job_id"));
                assertEquals("APPLIED", agent.repeatDelivery().state().name());
                assertEquals("WAITING", reopened.node(retryRoot, "assess").get("state"));
                assertEquals(0, reopened.count("act_ru_task", "PROC_INST_ID_=?", retryRoot));
                assertEquals(AgentStartWorker.Outcome.ACKNOWLEDGED, reopened.bridge(agent.client()).runOnce());
                reopened.runAsync(retryRoot);
                agent.executeAndDeliver(Map.of("assessment", Map.of("approved", true, "category", "retry")));
                reopened.reconcileUntilApplied(agent.eventId());
                assertEquals("COMPLETED", reopened.node(retryRoot, "assess").get("state"));
                assertEquals("retry", reopened.inputText(retryRoot, "review", "assessment", "category"));
                assertEquals(4, agent.runCount());
                verifyThreeSequentialAgents(reopened, agent);
            }
        }
    }

    private static void verifyThreeSequentialAgents(ProcessBoundary process, AgentTaskSnapshotIT.AgentBoundary agent) throws Exception {
        var chain = agent.publish(template(1).setTemplateKey("fusionChainAdvance").setOutputSchema(AGENT_INPUT));
        process.publishAgentChain(chain);
        String root = process.startAgentChain();
        int expectedInput = 17;
        var steps = List.of("agentA", "agentB", "agentC");
        for (String node : steps) {
            assertEquals(Integer.toString(expectedInput), process.inputText(root, node, "claim", "amount"));
            assertEquals(AgentStartWorker.Outcome.ACKNOWLEDGED, process.bridge(agent.client()).runOnce());
            var submitted = agent.linkedCommand();
            assertEquals(process.node(root, node).get("node_execution_ref"), submitted.getNodeExecutionRef());
            assertArrayEquals(ProcessTypedJson.canonicalBytes(Map.of("claim", Map.of("amount", expectedInput))),
                    ProcessTypedJson.canonicalBytes(submitted.getInput()));
            process.runAsync(root);
            agent.executeAndDeliver(Map.of("claim", Map.of("amount", ++expectedInput)));
            process.reconcileUntilApplied(agent.eventId());
            assertEquals("COMPLETED", process.node(root, node).get("state"));
        }
        assertEquals("20", process.observer.queryForObject("""
                SELECT output_json #>> '{claim,amount}' FROM tianji_process.tianji_process_node_execution
                WHERE instance_id=? AND node_id='agentC'
                """, String.class, root));
        assertEquals(3, process.observer.queryForObject("""
                SELECT count(DISTINCT run_id) FROM tianji_process.tianji_process_agent_dispatch WHERE scope_id=?
                """, Integer.class, root));
        assertEquals(0, process.count("act_ru_execution", "PROC_INST_ID_=?", root));
        assertEquals(7, agent.runCount());
    }

    static ProcessSchemaVO object(Map<String, Object> properties) {
        return ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", properties,
                "required", properties.keySet().stream().sorted().toList(), "additionalProperties", false));
    }

    static AgentRuntimeTemplateCommand template(long version) {
        var config = new AgentRuntimeTemplateCommand.TemplateConfigurationVO(
                new AgentRuntimeTemplateCommand.TemplateDependencyRefVO("fusionPrompt", version, "b".repeat(64)),
                "fusion-model", List.of(), null);
        var limits = new AgentRuntimeTemplateCommand.TechnicalLimitsVO(Duration.ofMinutes(2), Duration.ofSeconds(20),
                Duration.ofSeconds(10), 2, 2, 1, 2048, 100);
        return new AgentRuntimeTemplateCommand(TEMPLATE, version, AgentRuntimeTemplateCommand.RuntimeProfileEnum.RUNTIME_V2,
                "fusion-executor", AGENT_INPUT, AGENT_OUTPUT, PARAMETERS, config, limits);
    }

    static TemplateRefVO ref(AgentRuntimeTemplateCommand template) {
        return new TemplateRefVO(template.getTemplateKey(), template.getTemplateVersion(), template.contentSha256());
    }

    record ProcessDatabase(String url, String role, JdbcTemplate owner) {
        static ProcessDatabase install(String flag) {
            var url = isolatedUrl(flag);
            var role = System.getProperty("tianji.process.postgres.test.runtime-user", "");
            assertTrue(role.matches("tianji_it_runtime_[0-9a-f]{12}"), "process runtime role");
            var dataSource = dataSource(url, null);
            var owner = new JdbcTemplate(dataSource);
            owner.setQueryTimeout(10);
            requireRestrictedRole(owner, role);
            assertEquals(0, owner.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, PS_SCHEMA));
            owner.execute("CREATE SCHEMA " + PS_SCHEMA);
            var migration = Flyway.configure().dataSource(dataSource).schemas(PS_SCHEMA).defaultSchema(PS_SCHEMA)
                    .table("tianji_process_schema_history").createSchemas(false).baselineOnMigrate(false)
                    .locations("classpath:db/tianji-process").target("15").cleanDisabled(true).load();
            assertEquals(15, migration.migrate().migrationsExecuted);
            migration.validate();
            var scoped = url + "?currentSchema=" + PS_SCHEMA;
            try (var nativeSetup = nativeContext(scoped, System.getProperty("user.name"), true)) {
                assertNotNull(nativeSetup.getBean(org.flowable.engine.RepositoryService.class));
            }
            owner.execute("GRANT USAGE ON SCHEMA " + PS_SCHEMA + " TO " + role);
            grant(owner, PS_SCHEMA, role, "SELECT", "tianji_process_schema_history", "tianji_platform_release_activation",
                    "tianji_case_start_provenance");
            grant(owner, PS_SCHEMA, role, "SELECT,INSERT,UPDATE", "tianji_process_release", "tianji_process_release_definition",
                    "tianji_process_command_receipt", "tianji_process_draft", "tianji_process_node_execution",
                    "tianji_worker_lease_receipt", "tianji_process_agent_dispatch", "tianji_process_agent_result_inbox");
            grant(owner, PS_SCHEMA, role, "SELECT,INSERT", "tianji_governed_process_start", "tianji_process_automation_release",
                    "tianji_process_draft_command", "tianji_process_automation_activation", "tianji_process_instance_binding",
                    "tianji_human_task_receipt");
            owner.execute("GRANT UPDATE (created_at) ON " + PS_SCHEMA + ".tianji_process_instance_binding TO " + role);
            for (String table : owner.queryForList("""
                    SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname=? AND tablename LIKE 'act#_%' ESCAPE '#'
                    """, String.class, PS_SCHEMA)) {
                assertTrue(table.matches("act_[a-z0-9_]+"));
                grant(owner, PS_SCHEMA, role, "SELECT,INSERT,UPDATE,DELETE", table);
            }
            return new ProcessDatabase(scoped, role, owner);
        }

        ProcessBoundary open() throws Exception {
            var application = nativeContext(url, role, false);
            try {
                return new ProcessBoundary(owner, application);
            } catch (Exception | Error failure) {
                application.close();
                throw failure;
            }
        }
    }

    static String isolatedUrl(String flag) {
        var url = System.getProperty(flag, "");
        if (!url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException(flag + " requires a blank isolated loopback database");
        }
        return url;
    }

    static DriverManagerDataSource dataSource(String url, String user) {
        var source = new DriverManagerDataSource(url);
        if (user != null) source.setUsername(user);
        var settings = new Properties();
        settings.setProperty("connectTimeout", "5");
        settings.setProperty("socketTimeout", "20");
        source.setConnectionProperties(settings);
        return source;
    }

    static void requireRestrictedRole(JdbcTemplate db, String role) {
        assertEquals(Boolean.TRUE, db.queryForObject("""
                SELECT r.rolcanlogin AND NOT r.rolsuper AND NOT r.rolbypassrls AND NOT r.rolcreaterole
                    AND NOT r.rolcreatedb AND NOT r.rolreplication AND NOT pg_has_role(r.oid,d.datdba,'MEMBER')
                    AND NOT has_database_privilege(r.oid,d.oid,'CREATE') AND NOT has_schema_privilege(r.oid,'public','CREATE')
                    AND NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8'
                    AND current_setting('session_replication_role')='origin'
                FROM pg_roles r CROSS JOIN pg_database d WHERE r.rolname=? AND d.datname=current_database()
                """, Boolean.class, role));
    }

    static void grant(JdbcTemplate db, String schema, String role, String privilege, String... names) {
        for (String name : names) db.execute("GRANT " + privilege + " ON " + schema + "." + name + " TO \"" + role + "\"");
    }

    static final class ProcessBoundary implements AutoCloseable {
        final JdbcTemplate observer;
        final AgentResultInbox inbox;
        private final ConfigurableApplicationContext context;
        private final SpringProcessEngineConfiguration process;
        private final ProcessDraftService drafts;
        private final AutomationActivationService activation;
        private final ProcessExecutionService execution;
        private final FlowableHumanTaskService human;
        private final FlowableWorkerService worker;
        private final AgentDispatchStore dispatches;
        private final AgentResultCorrelationService results;
        final Map<AutomationModelValidator.VersionedRefVO, AutomationModelValidator.TemplateRegistrationVO> templateRegistrations =
                new LinkedHashMap<>();
        final Map<TemplateRefVO, AgentRuntimeTemplateCommand> authorityTemplates = new LinkedHashMap<>();

        ProcessBoundary(JdbcTemplate observer, ConfigurableApplicationContext context) throws Exception {
            this.observer = observer;
            this.context = context;
            process = context.getBean(SpringProcessEngineConfiguration.class);
            var dmn = context.getBean(SpringDmnEngineConfiguration.class);
            process.setBeans(Map.of());
            dmn.setBeans(Map.of());
            var wiring = context.getBean(Wiring.class);
            var manager = (DataSourceTransactionManager) process.getTransactionManager();
            var source = manager.getDataSource();
            var jdbc = new JdbcTemplate(source);
            jdbc.setQueryTimeout(10);
            var transaction = new TransactionTemplate(manager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            var environment = new MockEnvironment().withProperty("stellar.tianji.process.automation.enabled", "true");
            var validator = new ProcessModelValidator();
            var topics = new WorkerTopicRegistry(List.of(new WorkerTopicRegistry.Registration(20, ISSUER, "public-worker",
                    "fixture-legacy-topic", "1", Map.of(), Map.of(), Map.of(), Map.of(), 1,
                    Duration.ofSeconds(10), Duration.ZERO, ValidatedProcessModel.WorkerEffect.READ_ONLY)));
            var releases = new ProcessReleaseService(process, dmn, validator, false, topics);
            var verifier = new ProcessApiSchemaVerifier(source, PS_SCHEMA);
            var activationStore = new AutomationActivationStore(jdbc, PS_SCHEMA);
            var authority = new TrustFixture(observer, wiring.releases);
            var providers = new StaticListableBeanFactory(Map.of("admission", (AutomationAdmissionAuthority) authority::proof,
                    "trust", (AutomationReleaseTrustVerifier.TrustAuthority) authority::signed,
                    "templates", (ProcessExecutionService.AgentTemplateAuthority) (actor, route, ref, configurationRef) -> {
                        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                        assertEquals(STARTER, actor);
                        assertEquals(SOURCE, route);
                        assertNull(configurationRef);
                        return authorityTemplates.get(ref);
                    }, "routes", (ProcessAgentBridgeConfiguration.TypedRouteProvider) tenant -> tenant == 20 ? ROUTE : null));
            var admission = providers.getBeanProvider(AutomationAdmissionAuthority.class);
            var trust = providers.getBeanProvider(AutomationReleaseTrustVerifier.TrustAuthority.class);
            drafts = new ProcessDraftService(new ProcessDraftStore(jdbc, PS_SCHEMA), new ProcessDraftCommandReceiptStore(jdbc, PS_SCHEMA),
                    new ProcessDraftContentCodec(), Mappers.getMapper(ProcessDraftConverter.class), verifier, transaction,
                    environment, releases, wiring.releases, new AutomationModelValidator(), new AutomationModelCompiler(),
                    new AutomationReleaseStore.DependencyResolver() {
                        @Override
                        public AutomationModelValidator.DependencySnapshotVO resolve(ProcessActor actor, AutomationModelSourceVO model) {
                            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                            return dependencySnapshot(actor);
                        }

                        @Override
                        public boolean isCurrent(ProcessActor actor, AutomationModelSourceVO model,
                                                 AutomationModelValidator.DependencySnapshotVO observed) {
                            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                            return dependencySnapshot(actor).equals(observed);
                        }

                        private AutomationModelValidator.DependencySnapshotVO dependencySnapshot(ProcessActor actor) {
                            return new AutomationModelValidator.DependencySnapshotVO(actor.tenantId(), Map.of(),
                                    templateRegistrations, Set.of(), Map.of(CANDIDATE, CANDIDATE_HASH), Map.of());
                        }
                    });
            activation = new AutomationActivationService(activationStore, wiring.releases, releases,
                    Mappers.getMapper(AutomationActivationConverter.class), verifier, transaction, environment, admission, trust);
            var dependencies = new ProcessExecutionService.AutomationDependenciesVO(activationStore, wiring.releases, wiring.bindings,
                    Mappers.getMapper(AutomationInstanceConverter.class), verifier, environment, admission, trust,
                    providers.getBeanProvider(ProcessExecutionService.AgentTemplateAuthority.class),
                    providers.getBeanProvider(ProcessAgentBridgeConfiguration.TypedRouteProvider.class));
            execution = new ProcessExecutionService(process, dmn, validator, new GovernedStartPilotProperties(List.of()), null, dependencies);
            var humanReceipts = new HumanTaskReceiptStore(source, PS_SCHEMA);
            humanReceipts.verifyStorage();
            human = new FlowableHumanTaskService(process, null, dmn, validator, humanReceipts, null, HumanTaskCompletionObservation.disabled(),
                    new FlowableHumanTaskService.AutomationTaskParticipants(wiring.bindings, wiring.releases, wiring.guard, wiring.nodes, wiring.lifecycle));
            worker = new FlowableWorkerService(process, dmn, validator, topics,
                    new FlowableWorkerService.AutomationWorkerParticipants(wiring.bindings, wiring.releases, wiring.guard, wiring.nodes, wiring.lifecycle));
            dispatches = new AgentDispatchStore(source, manager, PS_SCHEMA);
            inbox = new AgentResultInbox(source, manager, PS_SCHEMA, dispatches, new AgentResultProducerRegistry(List.of(RECEPTION)),
                    Duration.ofMinutes(5));
            var inspector = new FlowableAgentWaitInspector(process, releases, new VerifiedProcessModels(process, dmn, validator), validator);
            results = new AgentResultCorrelationService(process, worker, inbox, Duration.ofMillis(100), 5,
                    new AgentResultCorrelationService.TypedParticipantsVO(wiring.guard, wiring.releases, releases, wiring.nodes, inspector));
        }

        void publish(List<AgentRuntimeTemplateCommand> allowed, AgentRuntimeTemplateCommand defaultTemplate) {
            publish(allowed, defaultTemplate, "fusion-r1", 0L, endPolicy());
        }

        void publishRetryRelease(AgentRuntimeTemplateCommand template) {
            var end = new NodePolicyVO.PolicyActionVO("END", null);
            var policy = new NodePolicyVO(Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofSeconds(45),
                    new NodePolicyVO.PolicyActionVO("RETRY", null), end, end,
                    new NodePolicyVO.RetryPolicyVO(2, Duration.ZERO, List.of("INVALID_OUTPUT")), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
            publish(List.of(template), template, "fusion-r2", 1L, policy);
        }

        private void publish(List<AgentRuntimeTemplateCommand> allowed, AgentRuntimeTemplateCommand defaultTemplate,
                             String release, long revision, NodePolicyVO policy) {
            allowed.forEach(this::registerTemplate);
            var start = object(Map.of("claim", CASE));
            var agent = new AgentTaskBindingVO(ref(defaultTemplate), allowed.stream().map(ProcessAgentFusionIT::ref).toList(),
                    PARAMETERS, Map.of("lane", "regular"), List.of("lane"), null);
            var bindings = List.of(human("submit", start, object(Map.of("amount", AMOUNT)),
                            List.of(new MappingVO("claim", "input.claim")), List.of()),
                    new AutomationNodeBindingVO("assess", "AGENT", AGENT_INPUT, AGENT_OUTPUT,
                            List.of(new MappingVO("claim.amount", "nodes.submit.output.amount")), List.of(), policy,
                            null, null, agent, null, null, null),
                    human("review", AGENT_OUTPUT, EMPTY, List.of(new MappingVO("assessment", "nodes.assess.output.assessment")), List.of()));
            var model = new AutomationModelSourceVO("AUTOMATION_V2", List.of(new AutomationModelSourceVO.ModelSourceVO("BPMN",
                    DEFINITION, """
                    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:step37">
                      <process id="fusionFlow" isExecutable="true">
                        <startEvent id="start"/><userTask id="submit" name="Submit"/>
                        <serviceTask id="assess"/><userTask id="review" name="Review"/><endEvent id="end"/>
                        <sequenceFlow id="startSubmit" sourceRef="start" targetRef="submit"/>
                        <sequenceFlow id="submitAssess" sourceRef="submit" targetRef="assess"/>
                        <sequenceFlow id="assessReview" sourceRef="assess" targetRef="review"/>
                        <sequenceFlow id="reviewEnd" sourceRef="review" targetRef="end"/>
                      </process>
                    </definitions>
                    """)), start, EMPTY, bindings, List.of());
            publishModel(DEFINITION, model, release, revision);
        }

        void publishAgentChain(AgentRuntimeTemplateCommand template) {
            registerTemplate(template);
            var agent = new AgentTaskBindingVO(ref(template), List.of(ref(template)), PARAMETERS, Map.of("lane", "regular"), List.of(), null);
            var nodes = List.of(chainNode("agentA", "input.claim", agent), chainNode("agentB", "nodes.agentA.output.claim", agent),
                    chainNode("agentC", "nodes.agentB.output.claim", agent));
            var model = new AutomationModelSourceVO("AUTOMATION_V2", List.of(new AutomationModelSourceVO.ModelSourceVO("BPMN",
                    CHAIN_DEFINITION, """
                    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:step37">
                      <process id="fusionAgentChain" isExecutable="true">
                        <startEvent id="start"/><serviceTask id="agentA"/>
                        <serviceTask id="agentB"/><serviceTask id="agentC"/><endEvent id="end"/>
                        <sequenceFlow id="startAgentA" sourceRef="start" targetRef="agentA"/>
                        <sequenceFlow id="agentAToB" sourceRef="agentA" targetRef="agentB"/>
                        <sequenceFlow id="agentBToC" sourceRef="agentB" targetRef="agentC"/>
                        <sequenceFlow id="agentCEnd" sourceRef="agentC" targetRef="end"/>
                      </process>
                    </definitions>
                    """)), AGENT_INPUT, EMPTY, nodes, List.of());
            publishModel(CHAIN_DEFINITION, model, "fusion-agent-chain-r1", 0L);
        }

        private static AutomationNodeBindingVO chainNode(String node, String source, AgentTaskBindingVO agent) {
            return new AutomationNodeBindingVO(node, "AGENT", AGENT_INPUT, AGENT_INPUT, List.of(new MappingVO("claim", source)),
                    List.of(), endPolicy(), null, null, agent, null, null, null);
        }

        private void publishModel(String definition, AutomationModelSourceVO model, String release, long revision) {
            var draft = drafts.create(STARTER, definition, new CreateProcessDraftCommand("Fusion", null, "draft-" + release));
            var saved = drafts.save(STARTER, draft.getDraftId(), new SaveProcessDraftCommand("save-" + release, 1,
                    "Fusion", AutomationReleaseStore.copyContent(model)));
            assertEquals(release, drafts.publish(STARTER, new PublishProcessDraftCommand(saved.getDraftId(), saved.getRevision(),
                    saved.getContentSha256(), release, "publish-" + release), Instant.now().plusSeconds(60)).releaseVersion());
            activation.activate(STARTER, definition, new ActivationCommand(release, revision, "activate-" + release));
        }

        void registerTemplate(AgentRuntimeTemplateCommand template) {
            var reference = ref(template);
            authorityTemplates.put(reference, template);
            templateRegistrations.put(new AutomationModelValidator.VersionedRefVO(reference.key(), reference.version()),
                    new AutomationModelValidator.TemplateRegistrationVO(reference, schemaHash(template.getInputSchema()),
                            schemaHash(template.getOutputSchema()), schemaHash(template.getParameterSchema())));
        }

        AutomationInstanceDTO start(String key, Map<String, AgentTaskOverrideVO> overrides) {
            var instance = startBeforeBootstrap(key, overrides);
            runAsync(instance.getInstanceId());
            return instance;
        }

        AutomationInstanceDTO startBeforeBootstrap(String key, Map<String, AgentTaskOverrideVO> overrides) {
            var request = new AutomationStartCommand(DEFINITION, "business-" + key,
                    "start-" + key, "trace-" + key, Map.of("claim", Map.of("amount", 17L)), overrides);
            return execution.startAutomation(STARTER, request);
        }

        String startAgentChain() {
            var request = new AutomationStartCommand(CHAIN_DEFINITION, "business-chain", "start-chain", "trace-chain",
                    Map.of("claim", Map.of("amount", 17L)), Map.of());
            String root = execution.startAutomation(STARTER, request).getInstanceId();
            runAsync(root);
            return root;
        }

        void completeHuman(String root, Map<String, Object> output) {
            var task = process.getTaskService().createTaskQuery().processInstanceId(root).singleResult();
            assertNotNull(task);
            var read = human.observeAction(taskContext("get.v2"), task.getId());
            human.claim(taskContext("claim"), new ClaimHumanTaskCommand("claim-" + task.getId(), task.getId(), read.getVersion()));
            var claimed = human.observeAction(taskContext("get.v2"), task.getId());
            human.completeAction(taskContext("complete.v2"), task.getId(),
                    new CompleteEngineHumanTaskCommand("complete-" + task.getId(), claimed.getVersion(), output));
        }

        AgentStartWorker bridge(LinkedAgentClient client) {
            return new AgentStartWorker(ROUTE, worker, dispatches, client);
        }

        void runAsync(String root) {
            var management = process.getManagementService();
            for (int tries = 0; tries < 8; tries++) {
                var jobs = management.createJobQuery().processInstanceId(root).list();
                if (jobs.isEmpty()) return;
                jobs.forEach(job -> management.executeJob(job.getId()));
            }
            fail("Native process jobs did not drain");
        }

        void fireNativeRetry(String root) {
            var retryId = observer.queryForObject("""
                    SELECT id_ FROM tianji_process.act_ru_timer_job
                    WHERE process_instance_id_=? AND handler_type_='tianji-automation-worker-retry'
                    """, String.class, root);
            var jobs = process.getManagementService();
            jobs.executeJob(jobs.moveTimerToExecutableJob(retryId).getId());
        }

        void awaitNativeLeaseExpiry(String root) throws InterruptedException {
            long stop = System.nanoTime() + ROUTE.leaseDuration().plusSeconds(3).toNanos();
            do {
                Boolean expired = observer.queryForObject("""
                        SELECT l.lease_expires_at<=clock_timestamp() AND j.lock_exp_time_<=clock_timestamp()
                        FROM tianji_process.tianji_worker_lease_receipt l
                        JOIN tianji_process.act_ru_external_job j ON j.id_=l.job_id
                        WHERE l.scope_id=? AND l.state='LEASED'
                        """, Boolean.class, root);
                if (Boolean.TRUE.equals(expired)) return;
                Thread.sleep(100);
            } while (System.nanoTime() < stop);
            fail("Original native and JDBC Worker lease did not expire");
        }

        void correlate() {
            results.reconcile(RECEPTION, 16);
        }

        void reconcileUntilApplied(String eventId) throws InterruptedException {
            var stop = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            do {
                correlate();
                if ("APPLIED".equals(inboxState(eventId))) return;
                Thread.sleep(20);
            } while (System.nanoTime() < stop);
            fail("Inbox remained " + inboxState(eventId));
        }

        int count(String table, String predicate, Object... values) {
            return observer.queryForObject("SELECT count(*) FROM " + PS_SCHEMA + "." + table + " WHERE " + predicate, Integer.class, values);
        }

        String dispatchState(String root) {
            return observer.queryForObject("SELECT state FROM tianji_process.tianji_process_agent_dispatch WHERE scope_id=?", String.class, root);
        }

        Duration dispatchDuration(String root) {
            long millis = observer.queryForObject("SELECT submission_timeout_ms FROM tianji_process.tianji_process_agent_dispatch "
                    + "WHERE scope_id=?", Long.class, root);
            return Duration.ofMillis(millis);
        }

        String inboxState(String event) {
            return observer.queryForObject("SELECT state FROM tianji_process.tianji_process_agent_result_inbox WHERE event_id=?", String.class, event);
        }

        int inboxFailures(String event) {
            return observer.queryForObject("SELECT attempt_count FROM tianji_process.tianji_process_agent_result_inbox WHERE event_id=?", Integer.class, event);
        }

        Map<String, Object> node(String root, String node) {
            return observer.queryForMap("""
                    SELECT * FROM tianji_process.tianji_process_node_execution WHERE instance_id=? AND node_id=?
                    ORDER BY attempt DESC LIMIT 1
                    """, root, node);
        }

        Instant attemptDeadline(String root, String node) {
            return observer.queryForObject("SELECT attempt_deadline_at FROM tianji_process.tianji_process_node_execution "
                    + "WHERE instance_id=? AND node_id=?", OffsetDateTime.class, root, node).toInstant();
        }

        String inputText(String root, String node, String... path) {
            return observer.queryForObject("SELECT input_json #>> ?::text[] FROM tianji_process.tianji_process_node_execution "
                    + "WHERE instance_id=? AND node_id=?", String.class, "{" + String.join(",", path) + "}", root, node);
        }

        String manifestText(String root, String... path) {
            var keys = "assess," + String.join(",", path);
            return observer.queryForObject("SELECT configuration #>> ?::text[] FROM tianji_process.tianji_process_instance_binding "
                    + "WHERE instance_id=?", String.class, "{" + keys + "}", root);
        }

        @Override public void close() {
            context.close();
        }
    }

    private static HumanTaskContext taskContext(String operation) {
        return new HumanTaskContext(REVIEWER, "tianji.api.process.task." + operation, Set.of("901"),
                Instant.now().plusSeconds(30), 1, 1);
    }

    private static AutomationNodeBindingVO human(String node, ProcessSchemaVO input, ProcessSchemaVO output,
                                                  List<MappingVO> mapping, List<MappingVO> result) {
        return new AutomationNodeBindingVO(node, "HUMAN", input, output, mapping, result, endPolicy(), null, null, null,
                new HumanBindingVO(CANDIDATE, output, Duration.ofMinutes(1)), null, null);
    }

    private static NodePolicyVO endPolicy() {
        var end = new NodePolicyVO.PolicyActionVO("END", null);
        return new NodePolicyVO(Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofSeconds(45), end, end, end,
                new NodePolicyVO.RetryPolicyVO(1, Duration.ZERO, List.of()), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
    }

    static String schemaHash(ProcessSchemaVO schema) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ProcessTypedJson.canonicalBytes(schema.toWire())));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }

    private static ConfigurableApplicationContext nativeContext(String url, String user, boolean install) {
        return new SpringApplicationBuilder(NativeConfiguration.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + user, "--spring.main.banner-mode=off",
                "--flowable.database-schema-update=" + install, "--egon.cola.component.transactional-outbox.enabled=false",
                "--flowable.async-executor-activate=" + !install,
                "--flowable.process.async.executor.default-timer-job-acquire-wait-time=PT5M",
                "--flowable.process.async.executor.default-async-job-acquire-wait-time=PT5M",
                "--flowable.process.async.executor.reset-expired-jobs-interval=PT5M");
    }

    static final class Wiring {
        ProcessInstanceBindingStore bindings;
        AutomationReleaseStore releases;
        AutomationInstanceGuardCommandInterceptor guard;
        ProcessNodeExecutionStore nodes;
        ProcessNodeLifecycleListener lifecycle;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class NativeConfiguration {
        @Bean Wiring fusionWiring() {
            return new Wiring();
        }

        @Bean EngineConfigurationConfigurer<SpringProcessEngineConfiguration> fusionConfig(Wiring wiring) {
            return configuration -> {
                configuration.setEnabledJobCategories(List.of("fixture-manual-jobs"));
                var manager = (DataSourceTransactionManager) configuration.getTransactionManager();
                var jdbc = new JdbcTemplate(manager.getDataSource());
                wiring.bindings = new ProcessInstanceBindingStore(jdbc, PS_SCHEMA);
                wiring.releases = new AutomationReleaseStore(jdbc, PS_SCHEMA);
                wiring.nodes = new ProcessNodeExecutionStore(jdbc, PS_SCHEMA);
                wiring.guard = new AutomationInstanceGuardCommandInterceptor(jdbc, PS_SCHEMA, wiring.bindings);
                var directory = new StaticListableBeanFactory(Map.of("candidate",
                        (ProcessNodeLifecycleListener.HumanCandidateDirectory) (tenant, ref, hash) ->
                                tenant == STARTER.tenantId() && CANDIDATE.equals(ref) && CANDIDATE_HASH.equals(hash)
                                        ? Optional.of(new ProcessNodeLifecycleListener.HumanCandidatePolicyVO(Set.of("901"), hash))
                                        : Optional.empty()));
                wiring.lifecycle = new ProcessNodeLifecycleListener(wiring.bindings, wiring.nodes, wiring.releases,
                        directory.getBeanProvider(ProcessNodeLifecycleListener.HumanCandidateDirectory.class));
                AutomationBootstrapBehavior.register(configuration, () -> new AutomationBootstrapBehavior(wiring.bindings, wiring.releases));
                ProcessNodeLifecycleListener.register(configuration, () -> wiring.lifecycle);
                configuration.setCustomPostCommandInterceptors(List.of(wiring.guard));
                AutomationInstanceGuardCommandInterceptor.registerFailedJobHandling(configuration);
            };
        }
    }

    private static final class TrustFixture {
        private final JdbcTemplate observer;
        private final AutomationReleaseStore releases;
        private final KeyPair key;
        private final Instant issued = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(1);
        private final Instant expires = issued.plusSeconds(300);

        private TrustFixture(JdbcTemplate observer, AutomationReleaseStore releases) throws Exception {
            this.observer = observer;
            this.releases = releases;
            key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        }

        AutomationAdmissionProofVO proof(AutomationAdmissionQuery query) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            Instant now = observer.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
            if (query.getPurpose() == AutomationAdmissionQuery.Purpose.REPLAY_START) {
                return new AutomationAdmissionProofVO(query.sha256(), "replay-permit", "a".repeat(64), now, now.plusSeconds(25),
                        1L, "b".repeat(64), "c".repeat(64), "d".repeat(64), Map.of());
            }
            var material = signed(query, releases.find(query.getActor().tenantId(), query.getReleaseVersion()).orElseThrow());
            return new AutomationAdmissionProofVO(query.sha256(), "start-permit", "a".repeat(64), now, now.plusSeconds(25),
                    material.currentGeneration(), material.envelopeSha256(), material.payloadSha256(),
                    material.dependencyEvidenceSha256(query), Map.of());
        }

        AutomationReleaseTrustVerifier.TrustObservationVO signed(AutomationAdmissionQuery query, AutomationReleaseStore.ImmutableReleaseVO release) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            try {
                byte[] publicKey = key.getPublic().getEncoded();
                String keyId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(publicKey));
                var trusted = new AutomationReleaseTrustVerifier.TrustedKeyVO(keyId, release.tenantId(), 1,
                        Base64.getEncoder().encodeToString(publicKey), issued.minusSeconds(1), expires);
                var signer = Signature.getInstance("Ed25519");
                signer.initSign(key.getPrivate());
                signer.update(AutomationReleaseTrustVerifier.signingPayload(release, keyId, issued, expires));
                var envelope = new AutomationReleaseTrustVerifier.SignedEnvelopeVO(keyId, issued, expires,
                        Base64.getEncoder().encodeToString(signer.sign()));
                return new AutomationReleaseTrustVerifier.TrustObservationVO(trusted, 1, envelope, release.dependencies(),
                        AutomationReleaseTrustVerifier.payloadSha256(AutomationReleaseTrustVerifier.canonicalPayload(release)));
            } catch (java.security.GeneralSecurityException failure) {
                throw new AssertionError(failure);
            }
        }
    }
}
