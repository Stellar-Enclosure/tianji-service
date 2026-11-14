package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.flowable.engine.impl.bpmn.parser.BpmnParse;
import org.flowable.engine.parse.BpmnParseHandler;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessAgentOutcomesQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentSubmissionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodePolicyVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.TianjiProcessServerApplication;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceConverter;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.GovernedStartPilotProperties;
import top.egon.cola.platform.tianji.process.server.execution.ProcessInstanceBindingStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionBO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationConverter;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationService;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionAuthority;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionProofVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionQuery;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftBO;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftContentCodec;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftStore;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerLeaseStore;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStoreTest.ACTOR;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStoreTest.binding;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStoreTest.seed;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentResultProducerRegistryTest.PRODUCER;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentResultProducerRegistryTest.event;

@Isolated("Uses a dedicated, empty Process Inbox PostgreSQL database")
@Timeout(90)
class AgentResultInboxTest {
    private static final String SCHEMA = "tianji_process";
    private static final String TYPED_NODE = "typed-node-execution";
    private static final String CONFIGURATION_SHA = "d".repeat(64);
    private static final String INPUT_SHA = "e".repeat(64);
    private static final String OUTPUT_SCHEMA_SHA = "f".repeat(64);
    private static final String NATIVE_URL = "tianji.process.agent-typed.postgres.test.url";

    @Test
    @EnabledIfSystemProperty(named = "tianji.process.inbox.postgres.test.url", matches = ".+")
    void keeps_v1_and_v2_receipts_durable_without_acknowledging_a_claimed_run() {
        String url = System.getProperty("tianji.process.inbox.postgres.test.url", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("Agent Inbox test requires an empty loopback tianji_it_ database");
        }
        var source = new DriverManagerDataSource(url);
        var jdbc = new JdbcTemplate(source);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, SCHEMA));
        var flyway = Flyway.configure().dataSource(source).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_process_schema_history").locations("classpath:db/tianji-process").target("6").cleanDisabled(true).load();
        assertEquals(6, flyway.migrate().migrationsExecuted);
        flyway.validate();
        jdbc.update("""
                INSERT INTO tianji_process.tianji_process_release
                    (tenant_id,release_key,state,manifest_sha256,publisher_type,publisher_id,created_at)
                VALUES (?,'release-one','ACTIVE',?,'SERVICE','legacy',clock_timestamp())
                """, ACTOR.tenantId(), "a".repeat(64));
        var runtimeSource = AgentResultInboxPostgresTest.runtimeSource(source, jdbc);
        var manager = new DataSourceTransactionManager(runtimeSource);
        var transaction = new TransactionTemplate(manager);
        var dispatches = new AgentDispatchStore(runtimeSource, manager, SCHEMA);
        var registration = new AgentResultProducerRegistry.Registration(PRODUCER, "agent-east", "process-east", ACTOR);
        var registry = new AgentResultProducerRegistry(List.of(registration));
        var inbox = new AgentResultInbox(runtimeSource, manager, SCHEMA, dispatches, registry, Duration.ofHours(1));

        var legacyIntent = transaction.execute(status -> dispatches.prepare(seed("legacy-terminal-occurrence")));
        var legacy = event(legacyIntent, "legacy-terminal-run", 3, "b".repeat(64));
        var originalLegacy = inbox.receive(PRODUCER, legacy);
        assertEquals(AgentResultReceiptStateEnum.RECEIVED, originalLegacy.state());
        assertEquals(originalLegacy, inbox.receive(PRODUCER, legacy));
        assertEquals(legacy.fingerprint(), originalLegacy.eventFingerprint());

        var typedIntent = transaction.execute(status -> dispatches.prepare(typedSeed()));
        assertNotNull(typedIntent);
        var finishedAt = jdbc.queryForObject("SELECT clock_timestamp()::timestamptz(6)", OffsetDateTime.class).toInstant();
        var typed = typedEvent(typedIntent, "typed-terminal-run", TYPED_NODE, finishedAt, true);
        var wrongAttempt = typedEvent(typedIntent, "typed-terminal-run", "other-node-execution", finishedAt, true);
        assertThrows(ProcessConflictException.class, () -> inbox.receiveV2(PRODUCER, wrongAttempt));
        var v2OnLegacyDispatch = typedEvent(legacyIntent, "mislabeled-v2-run", TYPED_NODE, finishedAt, true);
        assertThrows(ProcessConflictException.class, () -> inbox.receiveV2(PRODUCER, v2OnLegacyDispatch));
        var foreign = ProcessActor.verified(ACTOR.tenantId(), ProcessActor.SubjectType.SERVICE,
                PRODUCER.subjectId(), "https://other.example/20");
        assertThrows(ProcessResourceNotFoundException.class, () -> inbox.receiveV2(foreign, typed));
        var originalTyped = inbox.receiveV2(PRODUCER, typed);
        assertEquals(AgentResultReceiptStateEnum.RECEIVED, originalTyped.state());
        assertEquals(typed.eventId(), originalTyped.eventId());
        assertEquals(typed.fingerprint(), originalTyped.eventFingerprint());
        assertEquals(originalTyped, inbox.receiveV2(PRODUCER, typed));
        var changed = typedEvent(typedIntent, typed.runId(), TYPED_NODE, finishedAt, false);
        assertEquals(typed.eventId(), changed.eventId());
        assertThrows(ProcessConflictException.class, () -> inbox.receiveV2(PRODUCER, changed));
        var switchedProfile = event(typedIntent, typed.runId(), 3, "b".repeat(64));
        assertThrows(ProcessConflictException.class, () -> inbox.receive(PRODUCER, switchedProfile));

        assertEquals(List.of(1, 2), jdbc.queryForList("""
                SELECT (event_payload->>'schemaVersion')::integer FROM tianji_process.tianji_process_agent_result_inbox
                WHERE tenant_id=? AND state='PENDING' AND decided_at IS NULL ORDER BY 1
                """, Integer.class, ACTOR.tenantId()));
        assertEquals(typed.fingerprint(), jdbc.queryForObject("""
                SELECT event_sha256 FROM tianji_process.tianji_process_agent_result_inbox WHERE tenant_id=? AND event_id=?
                """, String.class, ACTOR.tenantId(), typed.eventId()));
        assertEquals("true", jdbc.queryForObject("""
                SELECT event_payload->'output'->>'approved' FROM tianji_process.tianji_process_agent_result_inbox
                WHERE tenant_id=? AND event_id=?
                """, String.class, ACTOR.tenantId(), typed.eventId()));
        assertNull(jdbc.queryForObject("""
                SELECT run_id FROM tianji_process.tianji_process_agent_dispatch WHERE tenant_id=? AND occurrence_id=? AND state='PENDING'
                """, String.class, ACTOR.tenantId(), typedIntent.seed().occurrenceId()));
        var reopened = new AgentResultInbox(runtimeSource, manager, SCHEMA, dispatches, registry, Duration.ofHours(1));
        assertEquals(originalLegacy, reopened.receive(PRODUCER, legacy));
        assertEquals(originalTyped, reopened.receiveV2(PRODUCER, typed));

        var legacyTuple = transaction.execute(status -> dispatches.prepare(typedSeed("legacy-input-using-reference-names")));
        var legacyWithTuple = event(legacyTuple, "legacy-other-run", 3, "b".repeat(64));
        var originalLegacyWithTuple = reopened.receive(PRODUCER, legacyWithTuple);
        assertEquals(AgentResultReceiptStateEnum.RECEIVED, originalLegacyWithTuple.state());
        assertEquals(originalLegacyWithTuple, reopened.receive(PRODUCER, legacyWithTuple));
        var v2ForOriginalLegacyRun = typedEvent(legacyTuple, legacyWithTuple.runId(), TYPED_NODE, finishedAt, true);
        assertThrows(ProcessConflictException.class, () -> reopened.receiveV2(PRODUCER, v2ForOriginalLegacyRun));
    }

    private static AgentDispatchStore.Seed typedSeed() {
        return typedSeed("typed-terminal-occurrence");
    }

    private static AgentDispatchStore.Seed typedSeed(String occurrence) {
        var context = seed(occurrence);
        return new AgentDispatchStore.Seed(context.binding(), context.instanceId(), context.occurrenceId(), context.releaseKey(),
                context.processDefinitionId(), context.processModelSha256(), context.elementId(), context.workerInputSha256(),
                context.businessKey(), context.correlationId(), Map.of("nodeExecutionRef", TYPED_NODE,
                        "configurationSha256", CONFIGURATION_SHA, "inputSha256", INPUT_SHA));
    }

    private static AgentTerminalEventV2 typedEvent(AgentDispatchStore.Intent intent, String runId, String nodeRef,
            Instant finishedAt, boolean approved) {
        var decision = AgentRunStatusEnum.COMPLETED;
        var outcome = AgentTerminalEventV2.SideEffectOutcomeEnum.NONE;
        var id = AgentTerminalEventV2.eventIdFor("agent-east", ACTOR.tenantId(), ACTOR.issuerFingerprint(), ACTOR.subjectId(), runId, 3);
        Map<String, Object> output = Map.of("approved", approved);
        var result = AgentTerminalEventV2.resultSha256(decision, output, null, null, outcome);
        return new AgentTerminalEventV2(2, "agent-east", id, ACTOR.tenantId(), ACTOR.issuerFingerprint(), ACTOR.subjectId(),
                intent.query().idempotencyKey(), intent.query().link(), intent.seed().binding().definitionKey(),
                intent.seed().binding().definitionVersion(), runId, 3, nodeRef, CONFIGURATION_SHA, INPUT_SHA, OUTPUT_SCHEMA_SHA,
                decision, finishedAt, result, output, null, null, outcome);
    }

    @Test
    @EnabledIfSystemProperty(named = NATIVE_URL, matches = ".+")
    void applies_typed_terminal_at_the_compiled_native_wait() throws GeneralSecurityException {
        var database = provisionNative();
        try (var app = nativeContext(database.url(), database.role(), false)) {
            var fixture = new NativeFixture(app, database);
            fixture.publish();
            var successful = fixture.start("success");
            var terminal = fixture.terminal(successful, AgentRunStatusEnum.COMPLETED, Map.of("approved", true), null);
            var legacyOnAutomation = event(successful.intent(), "new-v1-run", 3, "b".repeat(64));
            assertThrows(ProcessConflictException.class, () -> fixture.inbox.receive(PRODUCER, legacyOnAutomation));
            var receipt = fixture.inbox.receiveV2(PRODUCER, terminal);
            assertEquals(AgentResultReceiptStateEnum.RECEIVED, receipt.state());
            assertEquals(ProcessNodeExecutionDTO.StateEnum.WAITING, fixture.node(successful).getState());
            assertEquals(1, fixture.nativeMessages(successful));
            fixture.assertApplicationRollsBack(successful, terminal, receipt);

            var applied = fixture.correlation.apply(fixture.registration, terminal.eventId());
            assertEquals(AgentResultReceiptStateEnum.APPLIED, applied.state());
            assertEquals(receipt.receivedAt(), applied.receivedAt());
            assertEquals(applied, fixture.inbox.receiveV2(PRODUCER, terminal));
            assertEquals(applied, fixture.correlation.apply(fixture.registration, terminal.eventId()));
            assertEquals(0, fixture.nativeMessages(successful));
            assertEquals(ProcessNodeExecutionDTO.StateEnum.COMPLETED, fixture.node(successful).getState());
            assertEquals(Map.of("approved", true), fixture.node(successful).getOutput());
            assertEquals(Map.of("approved", true), fixture.execution.observeAutomation(NativeFixture.STARTER, successful.root(),
                    new ProcessExecutionService.AutomationObservationPermitVO(true, false, true, false, false)).getOutput());
            assertEquals(1, fixture.auditCount(terminal.eventId()));
            var outcome = fixture.outcomes.list(NativeFixture.STARTER, new GetProcessAgentOutcomesQuery(successful.root(), null)).items().getFirst();
            assertEquals(ProcessAgentOutcomePageDTO.InboxState.APPLIED, outcome.inboxState());
            assertEquals(AgentRunStatusEnum.COMPLETED, outcome.terminalStatus());

            var failed = fixture.start("failure");
            var failure = fixture.terminal(failed, AgentRunStatusEnum.FAILED, null, "FIXTURE_FAILURE");
            fixture.inbox.receiveV2(PRODUCER, failure);
            assertEquals(AgentResultReceiptStateEnum.APPLIED, fixture.correlation.apply(fixture.registration, failure.eventId()).state());
            assertEquals(0, fixture.nativeMessages(failed));
            assertEquals(ProcessNodeExecutionDTO.StateEnum.FAILED, fixture.node(failed).getState());
            assertEquals("FIXTURE_FAILURE", fixture.node(failed).getErrorCode());
            assertNull(fixture.node(failed).getOutput());
            assertEquals(1, fixture.auditCount(failure.eventId()));
        }
    }

    private static NativeDatabaseVO provisionNative() {
        String url = System.getProperty(NATIVE_URL, "");
        String role = System.getProperty("tianji.process.postgres.test.runtime-user", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")
                || !role.matches("tianji_it_runtime_[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Typed Agent Inbox requires a blank loopback database and a dedicated role");
        }
        var owner = new DriverManagerDataSource(url);
        var jdbc = new JdbcTemplate(owner);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, SCHEMA));
        jdbc.execute("CREATE SCHEMA " + SCHEMA);
        var flow = Flyway.configure().dataSource(owner).schemas(SCHEMA).defaultSchema(SCHEMA).createSchemas(false)
                .table("tianji_process_schema_history").locations("classpath:db/tianji-process").target("15").cleanDisabled(true).load();
        assertEquals(15, flow.migrate().migrationsExecuted);
        flow.validate();
        var outbox = Flyway.configure().dataSource(owner).schemas(SCHEMA).defaultSchema(SCHEMA).createSchemas(false)
                .locations("classpath:db/transactional-outbox/postgresql").baselineVersion("0").cleanDisabled(true).load();
        outbox.baseline();
        assertTrue(outbox.migrate().migrationsExecuted > 0);
        outbox.validate();
        AgentResultInboxPostgresTest.runtimeSource(owner, jdbc);
        String scopedUrl = url + "?currentSchema=" + SCHEMA;
        try (var installer = nativeContext(scopedUrl, System.getProperty("user.name"), true)) {
            assertNotNull(installer.getBean(RepositoryService.class));
        }
        var quoted = '"' + role + '"';
        grant(jdbc, quoted, "SELECT,INSERT,UPDATE", "egon_cola_outbox_message", "tianji_process_command_receipt", "tianji_process_draft",
                "tianji_process_release", "tianji_process_release_definition", "tianji_process_node_execution", "tianji_worker_lease_receipt");
        grant(jdbc, quoted, "SELECT,INSERT", "tianji_process_automation_release", "tianji_process_automation_activation",
                "tianji_process_instance_binding", "tianji_process_draft_command");
        jdbc.execute("GRANT UPDATE(created_at) ON tianji_process.tianji_process_instance_binding TO " + quoted);
        jdbc.execute("GRANT USAGE ON ALL SEQUENCES IN SCHEMA tianji_process TO " + quoted);
        var tables = jdbc.queryForList("""
                SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname=? AND tablename LIKE 'act#_%' ESCAPE '#'
                """, String.class, SCHEMA);
        assertTrue(tables.contains("act_ru_execution"));
        for (String table : tables) {
            assertTrue(table.matches("act_[a-z0-9_]+"));
            grant(jdbc, quoted, "SELECT,INSERT,UPDATE,DELETE", table);
        }
        return new NativeDatabaseVO(jdbc, scopedUrl, role);
    }

    private static void grant(JdbcTemplate jdbc, String role, String permissions, String... tables) {
        for (String table : tables) jdbc.execute("GRANT " + permissions + " ON " + SCHEMA + "." + table + " TO " + role);
    }

    private static ConfigurableApplicationContext nativeContext(String url, String role, boolean install) {
        if (install) {
            return new SpringApplicationBuilder(NativeInstaller.class).web(WebApplicationType.NONE).run("--spring.datasource.url=" + url,
                    "--spring.datasource.username=" + role, "--flowable.database-schema-update=true",
                    "--egon.cola.component.transactional-outbox.enabled=false", "--spring.main.banner-mode=off");
        }
        return new SpringApplicationBuilder(TianjiProcessServerApplication.class, FixtureDispatch.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + role, "--spring.main.banner-mode=off");
    }

    private record NativeDatabaseVO(JdbcTemplate owner, String url, String role) {
        private Instant now() { return owner.queryForObject("SELECT clock_timestamp()::timestamptz(6)", OffsetDateTime.class).toInstant(); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class NativeInstaller { }

    @Configuration(proxyBeanMethods = false)
    static class FixtureDispatch {
        /** Stands in only for Step 37's outbound writer and ACK. Native waits and results use production behavior. */
        private Consumer<DelegateExecution> dispatch;

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        EngineConfigurationConfigurer<SpringProcessEngineConfiguration> compiledAgentDispatchFixture() {
            return config -> {
                var handlers = new ArrayList<BpmnParseHandler>();
                if (config.getPostBpmnParseHandlers() != null) handlers.addAll(config.getPostBpmnParseHandlers());
                handlers.add(new BpmnParseHandler() {
                    @Override public Collection<Class<? extends BaseElement>> getHandledTypes() { return List.of(ServiceTask.class); }

                    @Override public void parse(BpmnParse parse, BaseElement element) {
                        if (element instanceof ServiceTask task && "${tianjiAutomationAgentDispatch}".equals(task.getImplementation())) {
                            task.setBehavior(new AbstractBpmnActivityBehavior() {
                                @Override public void execute(DelegateExecution current) {
                                    dispatch.accept(current);
                                    leave(current);
                                }
                            });
                        }
                    }
                });
                config.setPostBpmnParseHandlers(List.copyOf(handlers));
            };
        }
    }

    private static final class NativeFixture {
        private static final String DEFINITION = "typedAgentFlow";
        private static final String RELEASE = "native-agent-release";
        private static final String NODE = "review";
        private static final ProcessActor STARTER = ProcessActor.verified(ACTOR.tenantId(), ProcessActor.SubjectType.USER,
                "fixture-start-owner", "https://idp.example/20");
        private static final String XML = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:tianji:agent-result-test">
                  <process id="typedAgentFlow" isExecutable="true">
                    <startEvent id="start"/><serviceTask id="review"/><endEvent id="end"/>
                    <sequenceFlow id="first" sourceRef="start" targetRef="review"/>
                    <sequenceFlow id="second" sourceRef="review" targetRef="end"/>
                  </process>
                </definitions>
                """;

        private final NativeDatabaseVO database;
        private final SpringProcessEngineConfiguration process;
        private final JdbcTemplate jdbc;
        private final TransactionTemplate transaction;
        private final ProcessNodeExecutionStore nodes;
        private final AgentDispatchStore dispatches;
        private final WorkerLeaseStore leases;
        private final AutomationReleaseStore releases;
        private final ProcessReleaseService releaseService;
        private final AutomationActivationService activation;
        private final ProcessExecutionService execution;
        private final AgentResultProducerRegistry.Registration registration;
        private final AgentResultInbox inbox;
        private final AgentResultCorrelationService correlation;
        private final ProcessLinkedAgentOutcomeQuery outcomes;
        private AutomationReleaseStore.ImmutableReleaseVO release;
        private VerifiedProcessModels.VerifiedAutomationModelsVO verified;

        private NativeFixture(ConfigurableApplicationContext app, NativeDatabaseVO database) throws GeneralSecurityException {
            this.database = database;
            process = app.getBean(SpringProcessEngineConfiguration.class);
            var dmn = app.getBean(SpringDmnEngineConfiguration.class);
            var infrastructure = app.getBean(OutboxInfrastructure.class);
            var source = infrastructure.dataSource();
            var manager = (DataSourceTransactionManager) infrastructure.transactionManager();
            jdbc = new JdbcTemplate(source);
            transaction = new TransactionTemplate(manager);
            var guard = app.getBean(AutomationInstanceGuardCommandInterceptor.class);
            var bindings = app.getBean(ProcessInstanceBindingStore.class);
            nodes = app.getBean(ProcessNodeExecutionStore.class);
            releases = new AutomationReleaseStore(jdbc, SCHEMA);
            var validator = new ProcessModelValidator();
            releaseService = new ProcessReleaseService(process, dmn, validator);
            dispatches = new AgentDispatchStore(source, manager, SCHEMA);
            leases = new WorkerLeaseStore(source, manager, SCHEMA);
            registration = new AgentResultProducerRegistry.Registration(PRODUCER, "agent-east", "process-east", ACTOR);
            var producers = new AgentResultProducerRegistry(List.of(registration));
            inbox = new AgentResultInbox(source, manager, SCHEMA, dispatches, producers, Duration.ofHours(1));
            outcomes = new ProcessLinkedAgentOutcomeQuery(source, manager, SCHEMA, dispatches, inbox, producers, List.of(registration));
            var workers = new FlowableWorkerService(process, dmn, validator, new WorkerTopicRegistry(List.of(),
                    List.of(new WorkerTopicRegistry.AutomationRegistrationVO(ACTOR.tenantId(), "https://idp.example/20", "fixture-worker",
                            "fixture-worker-source", "unusedFixtureCapability", 1, "a".repeat(64), "b".repeat(64),
                            WorkerEffectSafetyEnum.READ_ONLY, "fixture-handler", "fixture-target", 1, Duration.ofSeconds(30)))));
            var inspector = new FlowableAgentWaitInspector(process, releaseService, new VerifiedProcessModels(process, dmn, validator));
            correlation = new AgentResultCorrelationService(process, workers, inbox, Duration.ofMillis(200), 5,
                    new AgentResultCorrelationService.TypedParticipantsVO(guard, releases, releaseService, nodes, inspector));

            var verifier = new ProcessApiSchemaVerifier(source, SCHEMA);
            var activationStore = new AutomationActivationStore(jdbc, SCHEMA);
            var authority = new TestAuthority(database, releases);
            var host = new StaticListableBeanFactory(Map.of("authorization", (AutomationAdmissionAuthority) authority::proof,
                    "signatures", (AutomationReleaseTrustVerifier.TrustAuthority) authority::signed));
            var permissions = host.getBeanProvider(AutomationAdmissionAuthority.class);
            var signatures = host.getBeanProvider(AutomationReleaseTrustVerifier.TrustAuthority.class);
            var environment = new MockEnvironment().withProperty("stellar.tianji.process.automation.enabled", "true");
            activation = new AutomationActivationService(activationStore, releases, releaseService, Mappers.getMapper(AutomationActivationConverter.class),
                    verifier, transaction, environment, permissions, signatures);
            var dependencies = new ProcessExecutionService.AutomationDependenciesVO(activationStore, releases, bindings,
                    Mappers.getMapper(AutomationInstanceConverter.class), verifier, environment, permissions, signatures);
            execution = new ProcessExecutionService(process, dmn, validator, new GovernedStartPilotProperties(List.of()), null, dependencies);
            app.getBean(FixtureDispatch.class).dispatch = this::recordDispatch;
        }

        private void publish() {
            var empty = schema(Map.of(), List.of());
            var approval = Map.<String, Object>of("approved", Map.of("type", "boolean"));
            var requiredApproval = schema(approval, List.of("approved"));
            var optionalApproval = schema(approval, List.of());
            var template = new AgentTaskBindingVO.TemplateRefVO(binding().definitionKey(), binding().definitionVersion(),
                    binding().definitionContentSha256());
            var agent = new AgentTaskBindingVO(template, List.of(template), empty, Map.of(), List.of(), null);
            var end = new NodePolicyVO.PolicyActionVO("END", null);
            var policy = new NodePolicyVO(Duration.ofMinutes(3), Duration.ofMinutes(2), Duration.ofSeconds(20), end, end, end,
                    new NodePolicyVO.RetryPolicyVO(1, Duration.ZERO, List.of()), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
            var node = new AutomationNodeBindingVO(NODE, "AGENT", empty, requiredApproval, List.of(),
                    List.of(new AutomationNodeBindingVO.MappingVO("approved", "nodes.review.output.approved")), policy,
                    null, null, agent, null, null, null);
            var model = new AutomationModelSourceVO("AUTOMATION_V2",
                    List.of(new AutomationModelSourceVO.ModelSourceVO("BPMN", DEFINITION, XML)), empty, optionalApproval, List.of(node), List.of());
            String inputSchema = ProcessNodeLifecycleListener.schemaSha256(empty);
            var registration = new AutomationModelValidator.TemplateRegistrationVO(template, inputSchema,
                    ProcessNodeLifecycleListener.schemaSha256(requiredApproval), inputSchema);
            var dependencies = new AutomationModelValidator.DependencySnapshotVO(ACTOR.tenantId(), Map.of(),
                    Map.of(new AutomationModelValidator.VersionedRefVO(template.key(), template.version()), registration), Set.of(), Map.of(), Map.of());
            var compiled = new AutomationModelCompiler().compile(new AutomationModelValidator().validate(model, dependencies));
            var prepared = releases.prepare(compiled);
            var drafts = new ProcessDraftStore(jdbc, SCHEMA);
            transaction.executeWithoutResult(status -> {
                var now = drafts.now();
                var content = AutomationReleaseStore.copyContent(model);
                var canonical = new ProcessDraftContentCodec().canonicalContent(content);
                var draft = new ProcessDraftBO().setOwner(STARTER).setDraftId("pd-typed-agent").setDefinitionKey(DEFINITION)
                        .setTitle("Native Agent result fixture").setState(DraftSummaryDTO.StateEnum.EDITING).setRevision(1).setContent(content)
                        .setContentSha256(ProcessTypedJson.sha256("AUTOMATION_DRAFT_CONTENT_V2", canonical))
                        .setCreatedAt(now).setUpdatedAt(now).setLastCommandId("create-typed-agent");
                drafts.insert(draft, new String(canonical, StandardCharsets.UTF_8));
                releaseService.publishCompiled(STARTER, RELEASE, prepared);
                releases.insert(draft, RELEASE, prepared, now);
                releases.seal(draft, RELEASE, "publish-typed-agent", now);
                release = releases.find(ACTOR.tenantId(), RELEASE).orElseThrow();
                verified = releaseService.verifiedAutomation(release);
            });
            assertEquals(1, activation.activate(STARTER, DEFINITION, new ActivationCommand(RELEASE, 0L, "activate-typed-agent")).getRevision());
        }

        private void recordDispatch(DelegateExecution current) {
            var instanceId = current.getProcessInstanceId();
            AutomationInstanceGuardCommandInterceptor.requireHeld(ACTOR.tenantId(), instanceId);
            assertEquals(verified.controlBindings().occurrenceSlots().get(NODE).activityId(), current.getCurrentActivityId());
            var occurrence = current.getParent().getParent();
            var occurrenceId = "occ-" + nodeHash("AUTOMATION_NODE_OCCURRENCE_V2", List.of(ACTOR.tenantId(), instanceId, NODE, occurrence.getId()));
            var nodeRef = "node-" + nodeHash("AUTOMATION_NODE_EXECUTION_V2", List.of(ACTOR.tenantId(), instanceId, occurrenceId, 1));
            var row = nodes.find(ACTOR.tenantId(), instanceId, nodeRef).orElseThrow();
            String business = current.getProcessInstanceBusinessKey();
            String suffix = business.substring("native-agent-".length());
            var facts = Map.<String, Object>of("nodeExecutionRef", nodeRef, "configurationSha256", row.getConfigurationSha256(),
                    "inputSha256", row.getInputSha256());
            var leaseHash = nodeHash("TEST_NATIVE_AGENT_LEASE", List.of(instanceId, current.getId()));
            var physicalInput = WorkerLeaseStore.automationPhysicalInputSha256(current.getId(), instanceId,
                    verified.root().engineDefinitionId(), current.getCurrentActivityId(), binding().topic(),
                    Long.parseLong(binding().schemaVersion()), nodeRef, row.getInputSha256(), row.getConfigurationSha256());
            var seed = new AgentDispatchStore.Seed(binding(), instanceId, current.getId(), RELEASE, verified.root().engineDefinitionId(),
                    verified.root().modelSha256(), current.getCurrentActivityId(), physicalInput, business,
                    "native-agent-correlation-" + suffix, facts);
            leases.claim(new WorkerLeaseStore.Claim(ACTOR, leaseHash, binding().topic(), binding().schemaVersion(),
                    seed.occurrenceId(), seed.occurrenceId(), WorkerScopeTypeEnum.BPMN, instanceId, RELEASE, seed.processDefinitionId(),
                    seed.elementId(), seed.idempotencyKey(), seed.processModelSha256(), physicalInput, "tianji-" + leaseHash,
                    row.getAttemptDeadlineAt(), Duration.ofSeconds(60)));
            var pending = dispatches.prepare(seed);
            String runId = "native-agent-run-" + suffix;
            var submitted = new AgentRunDTO(runId, binding().definitionKey(), binding().definitionVersion(), null,
                    AgentRunStatusEnum.RUNNING, null, false, 1, 1, 0, pending.dispatchNotAfter(), pending.createdAt(), null);
            dispatches.record(seed.key(), pending.query(), new LinkedAgentSubmissionDTO(false, submitted));
            dispatches.markAcknowledged(seed.key(), runId);
            String businessReceipt = "agent-" + runId;
            leases.finish(ACTOR, seed.occurrenceId(), leaseHash, new WorkerLeaseStore.Finish(WorkerReceiptOutcomeEnum.COMPLETED,
                    ProcessRequestDigest.hash(List.of("worker-complete-v1", seed.occurrenceId(), seed.occurrenceId(), businessReceipt),
                            Map.of("runId", runId)), businessReceipt, null, null));
            nodes.bindAgentDispatch(row, current.getId(), runId);
        }

        private NativeWaitingVO start(String suffix) {
            String root = execution.startAutomation(STARTER, new AutomationStartCommand(DEFINITION, "native-agent-" + suffix,
                    "start-native-agent-" + suffix, "native-agent-correlation-" + suffix, Map.of(), Map.of())).getInstanceId();
            var jobs = process.getManagementService();
            jobs.executeJob(jobs.createJobQuery().processInstanceId(root).singleResult().getId());
            return transaction.execute(status -> {
                var row = nodes.open(ACTOR.tenantId(), root, 2).getFirst();
                assertNotNull(row.getNativeSubscriptionId());
                var intent = dispatches.lockForResult(new AgentDispatchStore.Key(ACTOR, "process-east", root, row.getNativeJobId()));
                return new NativeWaitingVO(root, row, intent);
            });
        }

        private AgentTerminalEventV2 terminal(NativeWaitingVO waiting, AgentRunStatusEnum status, Map<String, Object> output, String code) {
            var row = waiting.node();
            var intent = waiting.intent();
            var outcome = AgentTerminalEventV2.SideEffectOutcomeEnum.NONE;
            var id = AgentTerminalEventV2.eventIdFor("agent-east", ACTOR.tenantId(), ACTOR.issuerFingerprint(), ACTOR.subjectId(), intent.runId(), 3);
            var digest = AgentTerminalEventV2.resultSha256(status, output, null, code, outcome);
            return new AgentTerminalEventV2(2, "agent-east", id, ACTOR.tenantId(), ACTOR.issuerFingerprint(), ACTOR.subjectId(),
                    intent.query().idempotencyKey(), intent.query().link(), binding().definitionKey(), binding().definitionVersion(),
                    intent.runId(), 3, row.getNodeExecutionRef(), row.getConfigurationSha256(), row.getInputSha256(), row.getOutputSchemaSha256(),
                    status, database.now(), digest, output, null, code, outcome);
        }

        private ProcessNodeExecutionBO node(NativeWaitingVO waiting) {
            return transaction.execute(status -> nodes.find(ACTOR.tenantId(), waiting.root(), waiting.node().getNodeExecutionRef()).orElseThrow());
        }

        private long nativeMessages(NativeWaitingVO waiting) {
            return process.getRuntimeService().createEventSubscriptionQuery().processInstanceId(waiting.root()).eventType("message").count();
        }

        private int auditCount(String eventId) {
            return jdbc.queryForObject("""
                    SELECT count(*) FROM tianji_process.tianji_process_command_receipt
                    WHERE tenant_id=? AND command_type='AGENT_RESULT' AND command_id=? AND state='APPLIED'
                    """, Integer.class, ACTOR.tenantId(), eventId);
        }

        private void assertApplicationRollsBack(NativeWaitingVO waiting, AgentTerminalEventV2 terminal,
                AgentResultReceipt receipt) {
            // V6's decision runs after the real Flowable message, node output, V5 close and audit writes.
            database.owner().execute("""
                    CREATE FUNCTION tianji_process.fixture_fail_agent_decision() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        IF NEW.state = 'APPLIED' AND NEW.event_id = TG_ARGV[0] THEN
                            RAISE EXCEPTION 'fixture_agent_decision_failure';
                        END IF;
                        RETURN NEW;
                    END $$
                    """);
            assertTrue(terminal.eventId().matches("[0-9a-f]{64}"));
            database.owner().execute("""
                    CREATE TRIGGER fixture_fail_agent_decision BEFORE UPDATE ON tianji_process.tianji_process_agent_result_inbox
                    FOR EACH ROW EXECUTE FUNCTION tianji_process.fixture_fail_agent_decision('%s')
                    """.formatted(terminal.eventId()));
            try {
                var failure = assertThrows(DataAccessException.class, () -> correlation.apply(registration, terminal.eventId()));
                assertTrue(failure.getMostSpecificCause().getMessage().contains("fixture_agent_decision_failure"));
            } finally {
                database.owner().execute("DROP TRIGGER fixture_fail_agent_decision ON tianji_process.tianji_process_agent_result_inbox");
                database.owner().execute("DROP FUNCTION tianji_process.fixture_fail_agent_decision()");
            }
            assertEquals(waiting.node(), node(waiting));
            assertEquals(1, nativeMessages(waiting));
            assertEquals(0, auditCount(terminal.eventId()));
            assertEquals(receipt, inbox.receiveV2(PRODUCER, terminal));
            assertEquals(waiting.intent(), transaction.execute(status -> dispatches.lockForResult(waiting.intent().seed().key())));
            assertEquals(1, jdbc.queryForObject("""
                    SELECT attempt_count FROM tianji_process.tianji_process_agent_result_inbox
                    WHERE tenant_id=? AND event_id=? AND state='PENDING' AND decided_at IS NULL
                    """, Integer.class, ACTOR.tenantId(), terminal.eventId()));
        }

        private static String nodeHash(String domain, List<?> value) {
            return ProcessTypedJson.sha256(domain, ProcessTypedJson.canonicalBytes(value));
        }

        private static ProcessSchemaVO schema(Map<String, Object> fields, List<String> required) {
            return ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", fields, "required", required, "additionalProperties", false));
        }
    }

    private record NativeWaitingVO(String root, ProcessNodeExecutionBO node, AgentDispatchStore.Intent intent) { }

    private static final class TestAuthority {
        private final NativeDatabaseVO database;
        private final AutomationReleaseStore releases;
        private final KeyPair key;
        private final Instant issued;

        private TestAuthority(NativeDatabaseVO database, AutomationReleaseStore releases) throws GeneralSecurityException {
            this.database = database;
            this.releases = releases;
            key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            issued = database.now().minusSeconds(1);
        }

        private AutomationAdmissionProofVO proof(AutomationAdmissionQuery query) {
            var material = signed(query, releases.find(ACTOR.tenantId(), query.getReleaseVersion()).orElseThrow());
            var observed = database.now();
            return new AutomationAdmissionProofVO(query.sha256(), "typed-agent-fixture", "a".repeat(64), observed, observed.plusSeconds(20),
                    material.currentGeneration(), material.envelopeSha256(), material.payloadSha256(),
                    material.dependencyEvidenceSha256(query), Map.of());
        }

        private AutomationReleaseTrustVerifier.TrustObservationVO signed(AutomationAdmissionQuery query,
                AutomationReleaseStore.ImmutableReleaseVO release) {
            assertEquals(query.getReleaseVersion(), release.releaseVersion());
            try {
                byte[] publicKey = key.getPublic().getEncoded();
                String keyId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(publicKey));
                var expires = issued.plusSeconds(180);
                var trusted = new AutomationReleaseTrustVerifier.TrustedKeyVO(keyId, ACTOR.tenantId(), 1,
                        Base64.getEncoder().encodeToString(publicKey), issued.minusSeconds(1), expires);
                var signature = Signature.getInstance("Ed25519");
                signature.initSign(key.getPrivate());
                signature.update(AutomationReleaseTrustVerifier.signingPayload(release, keyId, issued, expires));
                var envelope = new AutomationReleaseTrustVerifier.SignedEnvelopeVO(keyId, issued, expires,
                        Base64.getEncoder().encodeToString(signature.sign()));
                return new AutomationReleaseTrustVerifier.TrustObservationVO(trusted, 1, envelope, release.dependencies(),
                        AutomationReleaseTrustVerifier.payloadSha256(AutomationReleaseTrustVerifier.canonicalPayload(release)));
            } catch (GeneralSecurityException failure) {
                throw new AssertionError(failure);
            }
        }
    }
}
