package top.egon.cola.platform.tianji.process.server;

import org.flywaydb.core.Flyway;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.job.api.Job;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.ClaimHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.MappingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.CompleteEngineHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodePolicyVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.CreateProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.PublishProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.SaveProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerCompletionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerReportDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerTaskDTO;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStore;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.AutomationBootstrapBehavior;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceConverter;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.GovernedStartPilotProperties;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCancellationReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessInstanceBindingStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
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
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftContentCodec;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftConverter;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftService;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftStore;
import top.egon.cola.platform.tianji.process.server.task.FlowableHumanTaskService;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskCompletionObservation;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskReceiptStore;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry.AutomationRegistrationVO;
import top.egon.cola.platform.tianji.worker.AutomationWorkerHandlerDescriptor;
import top.egon.cola.platform.tianji.worker.AutomationWorkerHandlerRegistration;
import top.egon.cola.platform.tianji.worker.JdbcWorkerExecutionStore;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Isolated("Owns one explicitly provisioned empty Process database")
@Timeout(120)
public class AutomationControlFlowIT {

    private static final String BUSINESS = "worker_business";
    private static final String SCHEMA = "tianji_process";
    public static final String ISSUER = "https://idp.test/main";
    private static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "starter", ISSUER);
    private static final ProcessSchemaVO AMOUNT = schema("amount", "integer");
    private static final ProcessSchemaVO APPROVED = schema("approved", "boolean");
    private static final ProcessSchemaVO EMPTY = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(),
            "required", List.of(), "additionalProperties", false));
    private static final Map<String, String> CANDIDATES = Map.of("firstLane", "a".repeat(64), "secondLane", "b".repeat(64));
    private static final Map<String, String> ROLES = Map.of("firstLane", "901", "secondLane", "902");
    private static final String CHAIN_XML = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:step28">
              <process id="reviewChain" isExecutable="true">
                <startEvent id="start"/><userTask id="first"/><serviceTask id="work"/><userTask id="second"/><endEvent id="end"/>
                <sequenceFlow id="toFirst" sourceRef="start" targetRef="first"/>
                <sequenceFlow id="toWork" sourceRef="first" targetRef="work"/>
                <sequenceFlow id="toSecond" sourceRef="work" targetRef="second"/>
                <sequenceFlow id="toEnd" sourceRef="second" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Test
    void seventeen_artifact_human_service_human_replays_actions_and_commits_one_business_effect() throws Exception {
        try (var fixture = Harness.open(true)) {
            var registration = fixture.transactional;
            var firstResult = schema("reviewedAmount", "integer");
            var secondResult = schema("decision", "string");
            var rootOutput = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties",
                    Map.of("approved", Map.of("type", "boolean"), "decision", Map.of("type", "string"),
                            "reviewedAmount", Map.of("type", "integer")),
                    "required", List.of("approved", "decision", "reviewedAmount"), "additionalProperties", false));
            var sources = archiveSources("reviewChain");
            fixture.publish("reviewChain", sources, AMOUNT, rootOutput, List.of(
                    human("first", AMOUNT, firstResult, List.of(new MappingVO("amount", "input.amount")),
                            List.of(new MappingVO("reviewedAmount", "nodes.first.output.reviewedAmount")), "firstLane"),
                    service(registration, List.of(new MappingVO("amount", "scope.reviewedAmount")),
                            List.of(new MappingVO("approved", "nodes.work.output.approved")), false),
                    human("second", APPROVED, secondResult, List.of(new MappingVO("approved", "scope.approved")),
                            List.of(new MappingVO("decision", "nodes.second.output.decision")), "secondLane")));
            fixture.requireNativeArchive("reviewChain-r1", sources);
            var start = Harness.startRequest("reviewChain", "chain", 10);
            var root = fixture.start(start);
            var first = fixture.task(root);
            assertEquals("first", fixture.human.observeAction(context("first-reviewer", "get.v2", "901"), first).getNodeId());
            assertThrows(ProcessResourceNotFoundException.class,
                    () -> fixture.human.observeAction(context("second-reviewer", "get.v2", "902"), first));
            assertEquals("10", fixture.nodeValue(root, "first", "amount", false));
            fixture.completeHuman(first, "first-reviewer", "901", Map.of("reviewedAmount", 17));
            assertEquals("17", fixture.nodeValue(root, "first", "reviewedAmount", true));

            var task = fixture.acquire(registration);
            assertEquals(Map.of("amount", 17L), task.getInput());
            var descriptor = new AutomationWorkerHandlerDescriptor(registration.capabilityKey(), registration.capabilityVersion(),
                    registration.inputSchemaSha256(), registration.outputSchemaSha256(), registration.effectMode(),
                    registration.handlerVersion(), registration.targetId());
            var handler = new AutomationWorkerHandlerRegistration(descriptor, AMOUNT, APPROVED, (context, jdbc) -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals(1, jdbc.update("INSERT INTO worker_business.effect_probe(effect_key,amount) VALUES(?,?)",
                        context.effectKey(), context.input().get("amount")));
                return new AutomationWorkerBusinessResultDTO("business-chain", Map.of("approved", true));
            }, null);
            var store = fixture.businessStore();
            var result = store.executeAutomation(task, handler);
            var duplicate = store.executeAutomation(task, handler);
            assertTrue(duplicate.replayed());
            assertEquals(result.typedResult(), duplicate.typedResult());
            assertEquals(1, fixture.count("worker_business.effect_probe", "WHERE effect_key=?", task.getEffectKey()));
            assertEquals(17, fixture.owner.queryForObject("SELECT amount FROM worker_business.effect_probe", Integer.class));

            var request = complete(task, "chain-worker", result.businessReceiptId(), result.typedOutput());
            var report = fixture.worker.completeAutomation(registration.actor(), request);
            assertEquals(AutomationWorkerReportDTO.OutcomeEnum.COMPLETED, report.getOutcome());
            assertEquals("RESULT_ACCEPTED", fixture.nodeState(root, "work"));
            assertEquals("true", fixture.nodeValue(root, "work", "approved", true));
            assertEquals(0, fixture.process.getTaskService().createTaskQuery().processInstanceId(root).count());
            fixture.runJob(root);
            assertEquals("COMPLETED", fixture.nodeState(root, "work"));
            var second = fixture.task(root);
            assertEquals("second", fixture.human.observeAction(context("second-reviewer", "get.v2", "902"), second).getNodeId());
            assertThrows(ProcessResourceNotFoundException.class,
                    () -> fixture.human.observeAction(context("first-reviewer", "get.v2", "901"), second));
            assertEquals("true", fixture.nodeValue(root, "second", "approved", false));
            fixture.completeHuman(second, "second-reviewer", "902", Map.of("decision", "accept"));
            assertEquals("accept", fixture.nodeValue(root, "second", "decision", true));
            assertNotNull(fixture.process.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(root)
                    .singleResult().getEndTime());
            assertEquals(report, fixture.worker.completeAutomation(registration.actor(), request));
            var replay = fixture.execution.startAutomation(OWNER, start);
            assertEquals(root, replay.getInstanceId());
            assertEquals("reviewChain-r1", replay.getReleaseVersion());
            assertEquals(1, fixture.count("tianji_process.tianji_process_instance_binding", "WHERE release_key=?", replay.getReleaseVersion()));
            assertEquals(1, fixture.count("worker_business.effect_probe", "WHERE effect_key=?", task.getEffectKey()));
        }
    }

    private static List<AutomationModelSourceVO.ModelSourceVO> archiveSources(String definition) throws java.io.IOException {
        String decision = new ClassPathResource("fixtures/baseline.dmn").getContentAsString(StandardCharsets.UTF_8);
        var models = new ArrayList<AutomationModelSourceVO.ModelSourceVO>();
        models.add(new AutomationModelSourceVO.ModelSourceVO("BPMN", definition, CHAIN_XML));
        for (int number = 1; number <= 16; number++) {
            String key = "archiveDecision" + number;
            models.add(new AutomationModelSourceVO.ModelSourceVO("DMN", key, decision.replace("baselineDecision", key)));
        }
        return List.copyOf(models);
    }

    public static AutomationWorkerCompletionCommand complete(AutomationWorkerTaskDTO task, String command,
            String businessReceipt, Map<String, Object> output) {
        return new AutomationWorkerCompletionCommand(task.getTaskId(), task.getActivityOccurrenceId(), task.getNodeExecutionRef(),
                task.getLeaseToken(), command, task.getEffectKey(), businessReceipt, output);
    }

    private static ProcessSchemaVO schema(String property, String type) {
        return ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(property, Map.of("type", type)),
                "required", List.of(property), "additionalProperties", false));
    }

    private static NodePolicyVO policy(boolean retry) {
        var end = new NodePolicyVO.PolicyActionVO("END", null);
        var retryAction = new NodePolicyVO.PolicyActionVO("RETRY", null);
        return new NodePolicyVO(Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofSeconds(30),
                retry ? retryAction : end, retry ? retryAction : end, end,
                new NodePolicyVO.RetryPolicyVO(retry ? 2 : 1, Duration.ZERO, retry ? List.of("TRANSIENT_WORK") : List.of()),
                "BEST_EFFORT_STOP", "WAIT_RECOVERY");
    }

    private static AutomationNodeBindingVO service(AutomationRegistrationVO registration, List<MappingVO> input,
            List<MappingVO> output, boolean retry) {
        return new AutomationNodeBindingVO("work", "SERVICE", AMOUNT, APPROVED, input, output, policy(retry),
                registration.capability(), null, null, null, null, null);
    }

    private static AutomationNodeBindingVO human(String node, ProcessSchemaVO inputSchema, ProcessSchemaVO resultSchema,
            List<MappingVO> input, List<MappingVO> output, String candidates) {
        return new AutomationNodeBindingVO(node, "HUMAN", inputSchema, resultSchema, input, output, policy(false),
                null, null, null, new AutomationNodeBindingVO.HumanBindingVO(candidates, resultSchema,
                        Duration.ofMinutes(1)), null, null);
    }

    private static HumanTaskContext context(String subject, String operation, String role) {
        var actor = ProcessActor.verified(OWNER.tenantId(), ProcessActor.SubjectType.USER, subject, ISSUER);
        return new HumanTaskContext(actor, "tianji.api.process.task." + operation, Set.of(role), Instant.now().plusSeconds(60), 1, 1);
    }

    public static final class Harness implements AutoCloseable {
        public final JdbcTemplate owner;
        public final SpringProcessEngineConfiguration process;
        public final FlowableWorkerService worker;
        public final AutomationRegistrationVO transactional = registration("riskCheck", WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, null);
        public final AutomationRegistrationVO shortLease = registration("shortRisk", WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, null,
                Duration.ofSeconds(4));
        public final AutomationRegistrationVO external = registration("externalRisk", WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, "risk-system");
        public final AutomationRegistrationVO readOnly = registration("readRisk", WorkerEffectSafetyEnum.READ_ONLY, "risk-reader");
        public final WorkerTopicRegistry.Registration legacy = new WorkerTopicRegistry.Registration(20, ISSUER, "legacy-worker",
                "riskCheck", "1", Map.of("requestedAmount", ValueType.INTEGER), Map.of("approved", ValueType.BOOLEAN),
                Map.of("requestedAmount", Set.of("amount")), Map.of("approved", Set.of("approved")), 1,
                Duration.ofMinutes(2), Duration.ofMillis(200), WorkerEffect.TRANSACTIONAL_RECEIPT);
        private final ConfigurableApplicationContext application;
        private final DriverManagerDataSource runtimeBusiness;
        private final FlowableHumanTaskService human;
        private final ProcessReleaseService releases;
        private final ProcessExecutionService execution;
        private final ProcessControlService control;
        private final ProcessDraftService drafts;
        private final AutomationActivationService activation;

        private Harness(DatabaseVO database, ConfigurableApplicationContext application) throws Exception {
            this.owner = database.owner();
            this.runtimeBusiness = database.runtimeBusiness();
            this.application = application;
            var dmn = application.getBean(SpringDmnEngineConfiguration.class);
            process = application.getBean(SpringProcessEngineConfiguration.class);
            process.setBeans(Map.of());
            dmn.setBeans(Map.of());
            var wiring = application.getBean(NativeWiring.class);
            var manager = (DataSourceTransactionManager) process.getTransactionManager();
            var source = manager.getDataSource();
            var jdbc = new JdbcTemplate(source);
            jdbc.setQueryTimeout(10);
            var transaction = new TransactionTemplate(manager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            var environment = new MockEnvironment().withProperty("stellar.tianji.process.automation.enabled", "true");
            var registry = new WorkerTopicRegistry(List.of(legacy), List.of(transactional, shortLease, external, readOnly));
            var validator = new ProcessModelValidator();
            releases = new ProcessReleaseService(process, dmn, validator, false, registry);
            var sealed = wiring.releases;
            var schemaVerifier = new ProcessApiSchemaVerifier(source, SCHEMA);
            schemaVerifier.verifyAutomationV14();
            assertTrue(process.getAsyncExecutor().isActive());
            var activations = new AutomationActivationStore(jdbc, SCHEMA);
            var fixture = new FixtureAuthority(owner, sealed);
            var provider = new StaticListableBeanFactory(Map.of("admission", (AutomationAdmissionAuthority) fixture::proof,
                    "trust", (AutomationReleaseTrustVerifier.TrustAuthority) fixture::signed));
            var permission = provider.getBeanProvider(AutomationAdmissionAuthority.class);
            var trust = provider.getBeanProvider(AutomationReleaseTrustVerifier.TrustAuthority.class);
            drafts = new ProcessDraftService(new ProcessDraftStore(jdbc, SCHEMA), new ProcessDraftCommandReceiptStore(jdbc, SCHEMA),
                    new ProcessDraftContentCodec(), Mappers.getMapper(ProcessDraftConverter.class), schemaVerifier, transaction,
                    environment, releases, sealed, new AutomationModelValidator(), new AutomationModelCompiler(),
                    new AutomationReleaseStore.DependencyResolver() {
                        @Override
                        public AutomationModelValidator.DependencySnapshotVO resolve(ProcessActor actor, AutomationModelSourceVO model) {
                            return new AutomationModelValidator.DependencySnapshotVO(actor.tenantId(),
                                    registry.automationReleaseRegistrations(actor.tenantId()), Map.of(), Set.of(), CANDIDATES, Map.of());
                        }

                        @Override
                        public boolean isCurrent(ProcessActor actor, AutomationModelSourceVO model,
                                AutomationModelValidator.DependencySnapshotVO observed) {
                            return resolve(actor, model).equals(observed);
                        }
                    });
            activation = new AutomationActivationService(activations, sealed, releases, Mappers.getMapper(AutomationActivationConverter.class),
                    schemaVerifier, transaction, environment, permission, trust);
            var dependencies = new ProcessExecutionService.AutomationDependenciesVO(activations, sealed, wiring.bindings,
                    Mappers.getMapper(AutomationInstanceConverter.class), schemaVerifier, environment, permission, trust);
            execution = new ProcessExecutionService(process, dmn, validator, new GovernedStartPilotProperties(List.of()), null, dependencies);
            var cancellation = new ProcessCancellationReceiptStore(source, SCHEMA);
            cancellation.verifyStorage();
            control = new ProcessControlService(process, dmn, validator, releases, new ProcessControlNativeAccess(source, process),
                    cancellation, new AgentDispatchStore(source, manager, SCHEMA),
                    new ProcessControlService.AutomationControlParticipants(wiring.bindings, sealed, wiring.guard, wiring.nodes));
            var receipts = new HumanTaskReceiptStore(source, SCHEMA);
            receipts.verifyStorage();
            human = new FlowableHumanTaskService(process, null, dmn, validator, receipts, null, HumanTaskCompletionObservation.disabled(),
                    new FlowableHumanTaskService.AutomationTaskParticipants(wiring.bindings, sealed, wiring.guard, wiring.nodes, wiring.lifecycle));
            worker = new FlowableWorkerService(process, dmn, validator, registry,
                    new FlowableWorkerService.AutomationWorkerParticipants(wiring.bindings, sealed, wiring.guard, wiring.nodes, wiring.lifecycle));
        }

        public static Harness open(boolean installBusiness) throws Exception {
            var database = provision(installBusiness);
            var application = context(database.url(), database.role(), false);
            try {
                return new Harness(database, application);
            } catch (Exception | Error failure) {
                application.close();
                throw failure;
            }
        }

        public void publishService(String definition, AutomationRegistrationVO registration, boolean retry) {
            String xml = """
                    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:step28">
                      <process id="%s" isExecutable="true">
                        <startEvent id="start"/><serviceTask id="work"/><endEvent id="end"/>
                        <sequenceFlow id="toWork" sourceRef="start" targetRef="work"/>
                        <sequenceFlow id="toEnd" sourceRef="work" targetRef="end"/>
                      </process>
                    </definitions>
                    """.formatted(definition);
            publish(definition, List.of(new AutomationModelSourceVO.ModelSourceVO("BPMN", definition, xml)), AMOUNT, EMPTY,
                    List.of(service(registration, List.of(new MappingVO("amount", "input.amount")), List.of(), retry)));
        }

        private void publish(String definition, List<AutomationModelSourceVO.ModelSourceVO> models, ProcessSchemaVO input,
                ProcessSchemaVO output, List<AutomationNodeBindingVO> bindings) {
            String release = definition + "-r1";
            var model = new AutomationModelSourceVO("AUTOMATION_V2", models, input, output, bindings, List.of());
            var draft = drafts.create(OWNER, definition, new CreateProcessDraftCommand(definition, null, "draft-" + definition));
            var saved = drafts.save(OWNER, draft.getDraftId(), new SaveProcessDraftCommand("save-" + definition, 1,
                    definition, AutomationReleaseStore.copyContent(model)));
            assertEquals(release, drafts.publish(OWNER, new PublishProcessDraftCommand(saved.getDraftId(), saved.getRevision(),
                    saved.getContentSha256(), release, "publish-" + definition), Instant.now().plusSeconds(60)).releaseVersion());
            activation.activate(OWNER, definition, new ActivationCommand(release, 0L, "activate-" + definition));
        }

        private void requireNativeArchive(String release, List<AutomationModelSourceVO.ModelSourceVO> models) {
            var decisionRepository = application.getBean(SpringDmnEngineConfiguration.class).getDmnRepositoryService();
            var processRepository = process.getRepositoryService();
            String tenant = Long.toString(OWNER.tenantId());
            assertEquals(17, count("tianji_process.tianji_process_release_definition", "WHERE tenant_id=? AND release_key=?",
                    OWNER.tenantId(), release));
            for (var model : models) {
                String nativeId = owner.queryForObject("""
                        SELECT engine_definition_id FROM tianji_process.tianji_process_release_definition
                        WHERE tenant_id=? AND release_key=? AND model_type=? AND definition_key=?
                        """, String.class, OWNER.tenantId(), release, model.type(), model.definitionKey());
                if ("DMN".equals(model.type())) {
                    var nativeDecision = decisionRepository.createDecisionQuery().decisionId(nativeId).decisionTenantId(tenant).singleResult();
                    assertNotNull(nativeDecision);
                    assertEquals(model.definitionKey() + ".dmn", nativeDecision.getResourceName());
                } else {
                    var nativeProcess = processRepository.createProcessDefinitionQuery().processDefinitionId(nativeId)
                            .processDefinitionTenantId(tenant).singleResult();
                    assertNotNull(nativeProcess);
                    assertEquals(model.definitionKey() + ".bpmn20.xml", nativeProcess.getResourceName());
                }
            }
        }

        public String start(String definition, String key, int amount) {
            return start(startRequest(definition, key, amount));
        }

        private static AutomationStartCommand startRequest(String definition, String key, int amount) {
            return new AutomationStartCommand(definition, "business-" + key,
                    "start-" + key, "trace-" + key, Map.of("amount", amount), Map.of());
        }

        private String start(AutomationStartCommand request) {
            var instance = execution.startAutomation(OWNER, request);
            String id = instance.getInstanceId();
            runJob(id);
            return id;
        }

        public String startLegacy() throws Exception {
            byte[] xml = new ClassPathResource("fixtures/worker-mapping.bpmn20.xml").getContentAsByteArray();
            var resource = new ProcessReleaseService.ModelResource(Type.BPMN, "workerMapping", xml,
                    Map.of("amount", ValueType.INTEGER, "privateFixture", ValueType.STRING));
            releases.publish(OWNER, "legacy-r1", List.of(resource));
            var result = execution.start(OWNER, new ProcessExecutionService.StartCommand("legacy-r1", "workerMapping",
                    "legacy-business", "start-legacy", "trace-legacy", Map.of("amount", 10, "privateFixture", "private")));
            return result.instanceId();
        }

        public String acquireLegacy() {
            return worker.acquire(legacy.actor(), new AcquireWorkerTaskCommand("riskCheck", "1")).task().taskId();
        }

        public AutomationWorkerTaskDTO acquire(AutomationRegistrationVO registration) {
            var acquired = worker.acquireAutomation(registration.actor(),
                    new AutomationWorkerAcquireCommand(registration.capabilityKey(), registration.capabilityVersion()));
            assertEquals(AutomationWorkerAcquireDTO.StatusEnum.ACQUIRED, acquired.getStatus());
            assertNotNull(acquired.getTask());
            return acquired.getTask();
        }

        public void runJob(String root) {
            var jobs = process.getManagementService();
            var job = jobs.createJobQuery().processInstanceId(root).singleResult();
            assertNotNull(job);
            jobs.executeJob(job.getId());
        }

        public Job boundaryTimer(String root, boolean hard) {
            var jobs = process.getManagementService();
            var timers = jobs.createTimerJobQuery().processInstanceId(root).list().stream()
                    .sorted(Comparator.comparing(Job::getDuedate)).toList();
            assertFalse(timers.isEmpty());
            return hard ? timers.getLast() : timers.getFirst();
        }

        public void fireTimeout(String root, boolean hard) {
            var jobs = process.getManagementService();
            var executable = jobs.moveTimerToExecutableJob(boundaryTimer(root, hard).getId());
            jobs.executeJob(executable.getId());
        }

        public void fireWorkerRetry(String root) {
            String id = owner.queryForObject("""
                    SELECT id_ FROM tianji_process.act_ru_timer_job
                    WHERE process_instance_id_=? AND handler_type_='tianji-automation-worker-retry'
                    """, String.class, root);
            var jobs = process.getManagementService();
            jobs.executeJob(jobs.moveTimerToExecutableJob(id).getId());
        }

        public void cancel(String root) {
            var snapshot = control.control(OWNER, new GetProcessInstanceQuery(root));
            assertTrue(control.cancel(OWNER, new CancelProcessCommand(root, "cancel-" + root, snapshot.version(), "fixture cancellation"))
                    .accepted());
        }

        public String nodeState(String root, String node) {
            return owner.queryForObject("""
                    SELECT state FROM tianji_process.tianji_process_node_execution
                      WHERE instance_id=? AND node_id=? ORDER BY attempt DESC LIMIT 1
                    """, String.class, root, node);
        }

        public String nodeValue(String root, String node, String field, boolean output) {
            String column = output ? "output_json" : "input_json";
            return owner.queryForObject("SELECT " + column + """
                    ->>? FROM tianji_process.tianji_process_node_execution WHERE instance_id=? AND node_id=?
                    """, String.class, field, root, node);
        }

        public int count(String table, String predicate, Object... parameters) {
            return owner.queryForObject("SELECT count(*) FROM " + table + " " + predicate, Integer.class, parameters);
        }

        private String task(String root) {
            var task = process.getTaskService().createTaskQuery().processInstanceId(root).singleResult();
            assertNotNull(task);
            return task.getId();
        }

        private void completeHuman(String task, String subject, String role, Map<String, Object> output) {
            var reader = context(subject, "get.v2", role);
            var initial = human.observeAction(reader, task);
            assertEquals(List.of("CLAIM"), initial.getAvailableActions());
            human.claim(context(subject, "claim", role), new ClaimHumanTaskCommand("claim-" + subject, task, initial.getVersion()));
            String version = human.observeAction(reader, task).getVersion();
            var completer = context(subject, "complete.v2", role);
            var command = new CompleteEngineHumanTaskCommand("complete-" + subject, version, output);
            var original = human.completeAction(completer, task, command);
            assertEquals(original, human.completeAction(completer, task, command));
        }

        private JdbcWorkerExecutionStore businessStore() {
            return new JdbcWorkerExecutionStore(runtimeBusiness, new DataSourceTransactionManager(runtimeBusiness), BUSINESS,
                    transactional.sourceId(), transactional.tenantId(), Duration.ofSeconds(10), Duration.ofSeconds(1));
        }

        @Override
        public void close() {
            application.close();
        }
    }

    private static AutomationRegistrationVO registration(String key, WorkerEffectSafetyEnum mode, String target) {
        return registration(key, mode, target, Duration.ofMinutes(2));
    }

    private static AutomationRegistrationVO registration(String key, WorkerEffectSafetyEnum mode, String target, Duration leaseDuration) {
        return new AutomationRegistrationVO(20, ISSUER, "typed-worker", "worker-source", key, 1,
                ProcessNodeLifecycleListener.schemaSha256(AMOUNT), ProcessNodeLifecycleListener.schemaSha256(APPROVED), mode,
                "handler-1", target, 8, leaseDuration);
    }

    private record DatabaseVO(JdbcTemplate owner, String url, String role, DriverManagerDataSource runtimeBusiness) { }

    private static DatabaseVO provision(boolean installBusiness) {
        String url = ProcessServerPostgresTest.explicitTestUrl();
        var ownerSource = new DriverManagerDataSource(url);
        var properties = new Properties();
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "20");
        ownerSource.setConnectionProperties(properties);
        var owner = new JdbcTemplate(ownerSource);
        owner.setQueryTimeout(10);
        assertEquals(Boolean.TRUE, owner.queryForObject("""
                SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8'
                   AND current_setting('session_replication_role')='origin'
                """, Boolean.class));
        assertEquals(0, owner.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname IN (?,?)",
                Integer.class, SCHEMA, BUSINESS));
        owner.execute("CREATE SCHEMA " + SCHEMA);
        String role = ProcessServerPostgresTest.explicitRuntimeRole(owner);
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT NOT rolreplication AND NOT rolbypassrls AND NOT has_schema_privilege(rolname,'public','CREATE')
                FROM pg_roles WHERE rolname=?
                """, Boolean.class, role)));
        migrate(ownerSource, SCHEMA, "process", "14", 14);
        owner.execute("REVOKE CREATE ON SCHEMA " + SCHEMA + " FROM PUBLIC");
        String scoped = url + "?currentSchema=" + SCHEMA;
        try (var installing = context(scoped, System.getProperty("user.name"), true)) {
            assertNotNull(installing.getBean(org.flowable.engine.RepositoryService.class));
        }
        String quoted = "\"" + role + "\"";
        owner.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + quoted);
        grant(owner, SCHEMA, quoted, "SELECT", "tianji_process_schema_history", "tianji_platform_release_activation",
                "tianji_case_start_provenance");
        grant(owner, SCHEMA, quoted, "SELECT,INSERT,UPDATE", "tianji_process_release", "tianji_process_release_definition",
                "tianji_process_command_receipt", "tianji_process_draft", "tianji_process_node_execution",
                "tianji_worker_lease_receipt", "tianji_process_agent_dispatch");
        grant(owner, SCHEMA, quoted, "SELECT,INSERT", "tianji_governed_process_start", "tianji_process_automation_release",
                "tianji_process_draft_command", "tianji_process_automation_activation", "tianji_process_instance_binding",
                "tianji_human_task_receipt", "tianji_process_cancel_receipt");
        owner.execute("GRANT UPDATE (created_at) ON " + SCHEMA + ".tianji_process_instance_binding TO " + quoted);
        var nativeTables = owner.queryForList("""
                SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname=? AND tablename LIKE 'act#_%' ESCAPE '#'
                """, String.class, SCHEMA);
        assertTrue(nativeTables.contains("act_ru_external_job"));
        for (String table : nativeTables) {
            assertTrue(table.matches("act_[a-z0-9_]+"));
            grant(owner, SCHEMA, quoted, "SELECT,INSERT,UPDATE,DELETE", table);
        }

        DriverManagerDataSource runtimeBusiness = null;
        if (installBusiness) {
            owner.execute("CREATE SCHEMA " + BUSINESS);
            migrate(ownerSource, BUSINESS, "worker", "3", 3);
            owner.execute("CREATE TABLE worker_business.effect_probe(effect_key text PRIMARY KEY, amount integer NOT NULL)");
            owner.execute("GRANT USAGE ON SCHEMA " + BUSINESS + " TO " + quoted);
            grant(owner, BUSINESS, quoted, "SELECT", "tianji_worker_schema_history");
            grant(owner, BUSINESS, quoted, "SELECT,INSERT", "tianji_worker_execution_receipt",
                    "tianji_worker_nontransactional_intent", "effect_probe");
            grant(owner, BUSINESS, quoted, "SELECT,INSERT,UPDATE", "tianji_worker_effect_receipt");
            runtimeBusiness = new DriverManagerDataSource(url + "?currentSchema=" + BUSINESS);
            runtimeBusiness.setUsername(role);
            runtimeBusiness.setConnectionProperties(properties);
        }
        return new DatabaseVO(owner, scoped, role, runtimeBusiness);
    }

    private static void migrate(DriverManagerDataSource source, String schema, String component, String target, int count) {
        var flyway = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .table("tianji_" + component + "_schema_history").createSchemas(false).baselineOnMigrate(false)
                .locations("classpath:db/tianji-" + component).target(target).cleanDisabled(true).load();
        assertEquals(count, flyway.migrate().migrationsExecuted);
        flyway.validate();
    }

    private static void grant(JdbcTemplate jdbc, String schema, String role, String privilege, String... names) {
        for (String name : names) jdbc.execute("GRANT " + privilege + " ON " + schema + "." + name + " TO " + role);
    }

    private static ConfigurableApplicationContext context(String url, String user, boolean install) {
        return new SpringApplicationBuilder(NativeTestConfiguration.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + user, "--spring.main.banner-mode=off",
                "--flowable.database-schema-update=" + install, "--egon.cola.component.transactional-outbox.enabled=false",
                "--flowable.async-executor-activate=" + !install,
                "--flowable.process.async.executor.default-timer-job-acquire-wait-time=PT5M",
                "--flowable.process.async.executor.default-async-job-acquire-wait-time=PT5M",
                "--flowable.process.async.executor.reset-expired-jobs-interval=PT5M");
    }

    static final class NativeWiring {
        ProcessInstanceBindingStore bindings;
        AutomationReleaseStore releases;
        AutomationInstanceGuardCommandInterceptor guard;
        ProcessNodeExecutionStore nodes;
        ProcessNodeLifecycleListener lifecycle;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class NativeTestConfiguration {
        @Bean
        NativeWiring nativeWiring() {
            return new NativeWiring();
        }

        @Bean
        org.flowable.spring.boot.EngineConfigurationConfigurer<SpringProcessEngineConfiguration> bootstrap(NativeWiring wiring) {
            return config -> {
                // Keep native storage and the real executor active while the fixture steps jobs through ManagementService.
                config.setEnabledJobCategories(List.of("fixture-manual-jobs"));
                var source = ((DataSourceTransactionManager) config.getTransactionManager()).getDataSource();
                var jdbc = new JdbcTemplate(source);
                wiring.bindings = new ProcessInstanceBindingStore(jdbc, SCHEMA);
                wiring.releases = new AutomationReleaseStore(jdbc, SCHEMA);
                wiring.nodes = new ProcessNodeExecutionStore(jdbc, SCHEMA);
                wiring.guard = new AutomationInstanceGuardCommandInterceptor(jdbc, SCHEMA, wiring.bindings);
                var fixtureDirectory = new StaticListableBeanFactory(Map.of("automationHumanCandidateDirectory",
                        (ProcessNodeLifecycleListener.HumanCandidateDirectory) (tenant, ref, hash) ->
                                tenant == OWNER.tenantId() && hash.equals(CANDIDATES.get(ref))
                                        ? Optional.of(new ProcessNodeLifecycleListener.HumanCandidatePolicyVO(Set.of(ROLES.get(ref)), hash))
                                        : Optional.empty()));
                wiring.lifecycle = new ProcessNodeLifecycleListener(wiring.bindings, wiring.nodes, wiring.releases,
                        fixtureDirectory.getBeanProvider(ProcessNodeLifecycleListener.HumanCandidateDirectory.class));
                AutomationBootstrapBehavior.register(config, () -> new AutomationBootstrapBehavior(wiring.bindings, wiring.releases));
                ProcessNodeLifecycleListener.register(config, () -> wiring.lifecycle);
                config.setCustomPostCommandInterceptors(List.of(wiring.guard));
                AutomationInstanceGuardCommandInterceptor.registerFailedJobHandling(config);
            };
        }
    }

    private static final class FixtureAuthority {
        private final JdbcTemplate observer;
        private final AutomationReleaseStore releases;
        private final KeyPair key;
        private final Instant keyIssued = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(30);

        private FixtureAuthority(JdbcTemplate observer, AutomationReleaseStore releases) throws Exception {
            this.observer = observer;
            this.releases = releases;
            key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        }

        private AutomationAdmissionProofVO proof(AutomationAdmissionQuery query) {
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

        private AutomationReleaseTrustVerifier.TrustObservationVO signed(
                AutomationAdmissionQuery query, AutomationReleaseStore.ImmutableReleaseVO release) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            try {
                byte[] encoded = key.getPublic().getEncoded();
                String keyId = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(encoded));
                Instant expires = keyIssued.plusSeconds(300);
                var trusted = new AutomationReleaseTrustVerifier.TrustedKeyVO(keyId, release.tenantId(), 1,
                        Base64.getEncoder().encodeToString(encoded), keyIssued.minusSeconds(1), expires);
                var signer = Signature.getInstance("Ed25519");
                signer.initSign(key.getPrivate());
                signer.update(AutomationReleaseTrustVerifier.signingPayload(release, keyId, keyIssued, expires));
                var envelope = new AutomationReleaseTrustVerifier.SignedEnvelopeVO(keyId, keyIssued, expires,
                        Base64.getEncoder().encodeToString(signer.sign()));
                return new AutomationReleaseTrustVerifier.TrustObservationVO(trusted, 1, envelope, release.dependencies(),
                        AutomationReleaseTrustVerifier.payloadSha256(AutomationReleaseTrustVerifier.canonicalPayload(release)));
            } catch (java.security.GeneralSecurityException failure) {
                throw new AssertionError(failure);
            }
        }
    }
}
