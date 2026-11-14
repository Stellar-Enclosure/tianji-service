package top.egon.cola.platform.tianji.process.server;

import org.flywaydb.core.Flyway;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.job.api.Job;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.BeforeAll;
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
import java.security.MessageDigest;
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
import static top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson.V2_MAX_WIRE_INTEGER;

@Isolated("Owns one explicitly provisioned empty Process database")
@Timeout(120)
public class AutomationControlFlowIT {

    private static final String BUSINESS = "worker_business";
    private static final String SCHEMA = "tianji_process";
    public static final String ISSUER = "https://idp.test/main";
    private static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "starter", ISSUER);
    private static final ProcessSchemaVO AMOUNT = schema("amount", "integer");
    private static final ProcessSchemaVO APPROVED = schema("approved", "boolean");
    private static final ProcessSchemaVO NESTED_REVIEW_RESULT = schema(Map.of("shadow",
            schema(Map.of("output", AMOUNT.toWire())).toWire()));
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

    private static DatabaseVO database;

    @BeforeAll
    static void provisionControlFlowDatabase() {
        database = provision(true);
    }

    @Test
    void seventeen_artifact_human_service_human_replays_actions_and_commits_one_business_effect() throws Exception {
        try (var fixture = Harness.open(database)) {
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
                    service(registration, List.of(new MappingVO("amount", "nodes.first.output.reviewedAmount")),
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
            var read = new ProcessExecutionService.AutomationObservationPermitVO(true, false, true, false, false);
            var metadataOnly = new ProcessExecutionService.AutomationObservationPermitVO(true, false, false, false, false);
            assertEquals(Map.of("approved", true, "decision", "accept", "reviewedAmount", 17L),
                    fixture.execution.observeAutomation(OWNER, root, read).getOutput());
            assertNull(fixture.execution.observeAutomation(OWNER, root, metadataOnly).getOutput());
            assertEquals(report, fixture.worker.completeAutomation(registration.actor(), request));
            var replay = fixture.execution.startAutomation(OWNER, start);
            assertEquals(root, replay.getInstanceId());
            assertEquals("reviewChain-r1", replay.getReleaseVersion());
            assertEquals(1, fixture.count("tianji_process.tianji_process_instance_binding", "WHERE release_key=?", replay.getReleaseVersion()));
            assertEquals(1, fixture.count("worker_business.effect_probe", "WHERE effect_key=?", task.getEffectKey()));
        }
    }

    @Test
    void node_output_mappings_read_scope_before_any_mapped_write() throws Exception {
        String definition = "snapshotMapping";
        String xml = model(definition, """
                <startEvent id="start"/><userTask id="review"/><endEvent id="end"/>
                <sequenceFlow id="toReview" sourceRef="start" targetRef="review"/>
                <sequenceFlow id="toEnd" sourceRef="review" targetRef="end"/>
                """);
        var output = schema(Map.of("amount", Map.of("type", "integer"), "previousAmount", Map.of("type", "integer")));
        var read = new ProcessExecutionService.AutomationObservationPermitVO(true, false, true, false, false);
        try (var fixture = Harness.open(database)) {
            fixture.publish(definition, List.of(rootModel(definition, xml)), AMOUNT, output, List.of(
                    human("review", EMPTY, AMOUNT, List.of(), List.of(
                            new MappingVO("amount", "nodes.review.output.amount"),
                            new MappingVO("previousAmount", "scope.amount")), "firstLane")));
            String root = fixture.start(definition, "snapshot-mapping", 5);
            fixture.completeHuman(fixture.humanTask(root, "review"), "snapshot-reviewer", "901", Map.of("amount", 41));
            fixture.requireFinished(root);
            assertEquals(Map.of("amount", 41L, "previousAmount", 5L),
                    fixture.execution.observeAutomation(OWNER, root, read).getOutput());
        }
    }

    @Test
    void predecessor_reference_ignores_longer_node_identifier_in_another_scope() throws Exception {
        String definition = "qualifiedPredecessor";
        String child = "qualifiedChild";
        String xml = model(definition, """
                <startEvent id="start"/><serviceTask id="review"/><userTask id="read"/>
                <callActivity id="review.output" calledElement="qualifiedChild"/><endEvent id="end"/>
                <sequenceFlow id="toReview" sourceRef="start" targetRef="review"/>
                <sequenceFlow id="toRead" sourceRef="review" targetRef="read"/>
                <sequenceFlow id="toCall" sourceRef="read" targetRef="review.output"/>
                <sequenceFlow id="toEnd" sourceRef="review.output" targetRef="end"/>
                """);
        String childXml = model(child, """
                <startEvent id="begin"/><userTask id="shadow"/><endEvent id="end"/>
                <sequenceFlow id="toShadow" sourceRef="begin" targetRef="shadow"/>
                <sequenceFlow id="toEnd" sourceRef="shadow" targetRef="end"/>
                """);
        var call = new AutomationNodeBindingVO("review.output", "SUBPROCESS", EMPTY, EMPTY,
                List.of(), List.of(), policy(false), null, null, null, null,
                new AutomationNodeBindingVO.SubprocessControlVO(child, sha256(childXml)), null);
        try (var fixture = Harness.open(database)) {
            var registration = fixture.nestedResult;
            var review = new AutomationNodeBindingVO("review", "SERVICE", AMOUNT, NESTED_REVIEW_RESULT,
                    List.of(new MappingVO("amount", "input.amount")), List.of(), policy(false),
                    registration.capability(), null, null, null, null, null);
            fixture.publish(definition, List.of(rootModel(definition, xml), rootModel(child, childXml)),
                    AMOUNT, EMPTY, List.of(call, review,
                            human("read", AMOUNT, EMPTY, List.of(new MappingVO("amount", "nodes.review.output.shadow.output.amount")),
                                    List.of(), "secondLane"),
                            human("review.output.shadow", EMPTY, AMOUNT, List.of(), List.of(), "firstLane")));
            String root = fixture.start(definition, "qualified-predecessor", 0);
            var task = fixture.acquire(registration);
            var request = complete(task, "qualified-predecessor-result", "source-receipt",
                    Map.of("shadow", Map.of("output", Map.of("amount", 41))));
            assertEquals(AutomationWorkerReportDTO.OutcomeEnum.COMPLETED,
                    fixture.worker.completeAutomation(registration.actor(), request).getOutcome());
            fixture.runJob(root);
            assertEquals("41", fixture.nodeValue(root, "read", "amount", false));
            fixture.cancel(root);
        }
    }

    @Test
    void parallel_results_are_ordered_by_published_branch_after_restart() throws Exception {
        var values = schema(Map.of("value", Map.of("type", "array", "items", Map.of("type", "integer"), "maxItems", 2)));
        String root;
        String first;
        try (var fixture = Harness.open(database)) {
            fixture.publish("parallelCollect", List.of(rootModel("parallelCollect", parallelModel("parallelCollect"))),
                    AMOUNT, values, parallelBindings(values, "COLLECT_INDEXED"));
            root = fixture.start("parallelCollect", "reverse-order", 0);
            first = fixture.humanTask(root, "left");
            fixture.completeHuman(fixture.humanTask(root, "right"), "right-reviewer", "902", Map.of("value", 22));
            assertEquals("COMPLETED", fixture.nodeState(root, "right"));
            assertEquals("22", fixture.nodeValue(root, "right", "value", true));
            assertNull(fixture.nodeValue(root, "left", "value", false));
            assertNotNull(fixture.process.getRuntimeService().createProcessInstanceQuery().processInstanceId(root).singleResult());
            assertEquals(0, fixture.count("tianji_process.act_ru_task", "WHERE id_<>? AND proc_inst_id_=?", first, root));
            assertEquals(0, fixture.count("tianji_process.tianji_process_node_execution",
                    "WHERE instance_id=? AND node_id='join' AND state='COMPLETED'", root));
        }
        try (var restarted = Harness.open(database)) {
            assertEquals(first, restarted.humanTask(root, "left"));
            restarted.completeHuman(first, "left-reviewer", "901", Map.of("value", 11));
            assertEquals(List.of(Map.of("node_id", "left", "branch_index", 0), Map.of("node_id", "right", "branch_index", 1)),
                    restarted.owner.queryForList("""
                            SELECT node_id,branch_index FROM tianji_process.tianji_process_node_execution
                            WHERE instance_id=? AND node_id IN ('left','right') AND state='COMPLETED' ORDER BY branch_index
                            """, root));
            assertEquals("[11, 22]", restarted.nodeValue(root, "join", "value", true));
            restarted.requireFinished(root);
        }
    }

    @Test
    void parallel_branch_local_writes_do_not_change_the_sibling_or_root_input_after_restart() throws Exception {
        String definition = "parallelScope";
        String xml = model(definition, """
                <startEvent id="start"/><parallelGateway id="split"/><userTask id="leftWrite"/><userTask id="rightStart"/>
                <userTask id="leftRead"/><userTask id="rightRead"/><parallelGateway id="join"/><endEvent id="end"/>
                <sequenceFlow id="toSplit" sourceRef="start" targetRef="split"/>
                <sequenceFlow id="firstBranch" sourceRef="split" targetRef="leftWrite"/>
                <sequenceFlow id="secondBranch" sourceRef="split" targetRef="rightStart"/>
                <sequenceFlow id="toLeftRead" sourceRef="leftWrite" targetRef="leftRead"/>
                <sequenceFlow id="toRightRead" sourceRef="rightStart" targetRef="rightRead"/>
                <sequenceFlow id="leftJoin" sourceRef="leftRead" targetRef="join"/>
                <sequenceFlow id="rightJoin" sourceRef="rightRead" targetRef="join"/>
                <sequenceFlow id="toEnd" sourceRef="join" targetRef="end"/>
                """);
        var observed = schema(Map.of("scoped", Map.of("type", "integer"), "original", Map.of("type", "integer")));
        var read = List.of(new MappingVO("scoped", "scope.amount"), new MappingVO("original", "input.amount"));
        var join = new AutomationNodeBindingVO("join", "AND_JOIN", EMPTY, EMPTY, List.of(), List.of(), policy(false),
                null, null, null, null, null, List.of());
        String root;
        try (var fixture = Harness.open(database)) {
            fixture.publish(definition, List.of(rootModel(definition, xml)), AMOUNT, AMOUNT, List.of(
                    human("leftWrite", EMPTY, AMOUNT, List.of(), List.of(new MappingVO("amount", "nodes.leftWrite.output.amount")), "firstLane"),
                    human("rightStart", EMPTY, EMPTY, List.of(), List.of(), "secondLane"),
                    human("leftRead", observed, EMPTY, read, List.of(), "firstLane"),
                    human("rightRead", observed, EMPTY, read, List.of(), "secondLane"), join));
            root = fixture.start(definition, "branch-scope-restart", 5);
            fixture.completeHuman(fixture.humanTask(root, "leftWrite"), "left-reviewer", "901", Map.of("amount", 41));
            assertEquals("41", fixture.nodeValue(root, "leftRead", "scoped", false));
            assertEquals("5", fixture.nodeValue(root, "leftRead", "original", false));
        }
        try (var fixture = Harness.open(database)) {
            fixture.completeHuman(fixture.humanTask(root, "rightStart"), "right-reviewer", "902", Map.of());
            assertEquals("5", fixture.nodeValue(root, "rightRead", "scoped", false));
            assertEquals("5", fixture.nodeValue(root, "rightRead", "original", false));
            assertEquals("41", fixture.nodeValue(root, "leftRead", "scoped", false));
            assertEquals(2, fixture.process.getTaskService().createTaskQuery().processInstanceId(root).count());
            fixture.cancel(root);
        }
    }

    @Test
    void parallel_sum_outside_wire_integer_bound_rolls_back_second_branch() throws Exception {
        var value = schema("value", "integer");
        try (var fixture = Harness.open(database)) {
            fixture.publish("parallelSum", List.of(rootModel("parallelSum", parallelModel("parallelSum"))), AMOUNT,
                    value, parallelBindings(value, "SUM"));
            String root = fixture.start("parallelSum", "sum-overflow", 0);
            fixture.completeHuman(fixture.humanTask(root, "right"), "right-reviewer", "902", Map.of("value", V2_MAX_WIRE_INTEGER));
            String first = fixture.humanTask(root, "left");
            assertThrows(RuntimeException.class,
                    () -> fixture.completeHuman(first, "left-reviewer", "901", Map.of("value", 1)));
            assertEquals(first, fixture.humanTask(root, "left"));
            assertEquals("WAITING", fixture.nodeState(root, "left"));
            assertEquals(0, fixture.count("tianji_process.tianji_process_node_execution",
                    "WHERE instance_id=? AND node_id='join' AND output_sha256 IS NOT NULL", root));
            fixture.cancel(root);
        }
    }

    @Test
    void two_distinct_pinned_decisions_feed_typed_downstream_mapping() throws Exception {
        var small = decisionModel("fixtureRouteOne", "review", "route-one");
        String padded = small.xml() + "<!--" + "bounded-fixture".repeat(35_000) + "-->";
        var first = new AutomationModelSourceVO.ModelSourceVO("DMN", small.definitionKey(), padded);
        assertTrue(first.xml().getBytes(StandardCharsets.UTF_8).length > 512 * 1024);
        var second = decisionModel("fixtureRouteTwo", "review", "route-two");
        String definition = "pinnedDecisions";
        String xml = model(definition, """
                <startEvent id="start"/><businessRuleTask id="ruleOne"/><businessRuleTask id="ruleTwo"/><userTask id="finalReview"/><endEvent id="end"/>
                <sequenceFlow id="toFirst" sourceRef="start" targetRef="ruleOne"/>
                <sequenceFlow id="toSecond" sourceRef="ruleOne" targetRef="ruleTwo"/>
                <sequenceFlow id="toReview" sourceRef="ruleTwo" targetRef="finalReview"/>
                <sequenceFlow id="toEnd" sourceRef="finalReview" targetRef="end"/>
                """);
        var routes = schema(Map.of("firstRoute", Map.of("type", "string"), "secondRoute", Map.of("type", "string")));
        try (var fixture = Harness.open(database)) {
            fixture.publish(definition, List.of(rootModel(definition, xml), first, second), AMOUNT, EMPTY,
                    List.of(rule("ruleOne", first), rule("ruleTwo", second), human("finalReview", routes, EMPTY,
                            List.of(new MappingVO("firstRoute", "nodes.ruleOne.output.route"),
                                    new MappingVO("secondRoute", "nodes.ruleTwo.output.route")), List.of(), "firstLane")));
            fixture.deployCompetingDecision(first.definitionKey(), decisionModel(first.definitionKey(), "review", "newer-first").xml());
            fixture.deployCompetingDecision(second.definitionKey(), decisionModel(second.definitionKey(), "review", "newer-second").xml());
            String root = fixture.start(definition, "two-decisions", 17);
            assertEquals("route-one", fixture.nodeValue(root, "ruleOne", "route", true));
            assertEquals("route-two", fixture.nodeValue(root, "ruleTwo", "route", true));
            assertEquals("route-one", fixture.nodeValue(root, "finalReview", "firstRoute", false));
            assertEquals("route-two", fixture.nodeValue(root, "finalReview", "secondRoute", false));
            fixture.completeHuman(fixture.humanTask(root, "finalReview"), "rule-reviewer", "901", Map.of());
            fixture.requireFinished(root);
        }
    }

    @Test
    void xor_uses_a_closed_typed_predicate_and_the_published_default() throws Exception {
        String definition = "fixedChoice";
        String xml = model(definition, """
                <startEvent id="start"/><exclusiveGateway id="choice" default="normal"/>
                <userTask id="escalated"/><userTask id="routine"/><exclusiveGateway id="join"/><endEvent id="end"/>
                <sequenceFlow id="toChoice" sourceRef="start" targetRef="choice"/>
                <sequenceFlow id="high" sourceRef="choice" targetRef="escalated">
                  <conditionExpression>{"op":"GT","args":[{"path":"amount"},{"literal":10}]}</conditionExpression>
                </sequenceFlow>
                <sequenceFlow id="normal" sourceRef="choice" targetRef="routine"/>
                <sequenceFlow id="escalateJoin" sourceRef="escalated" targetRef="join"/>
                <sequenceFlow id="routineJoin" sourceRef="routine" targetRef="join"/>
                <sequenceFlow id="toEnd" sourceRef="join" targetRef="end"/>
                """);
        try (var fixture = Harness.open(database)) {
            fixture.publish(definition, List.of(rootModel(definition, xml)), AMOUNT, EMPTY,
                    List.of(human("escalated", EMPTY, EMPTY, List.of(), List.of(), "firstLane"),
                            human("routine", EMPTY, EMPTY, List.of(), List.of(), "secondLane")));
            String high = fixture.start(definition, "choice-high", 11);
            String ordinary = fixture.start(definition, "choice-default", 10);
            assertEquals(1, fixture.process.getTaskService().createTaskQuery().processInstanceId(high).count());
            assertEquals(1, fixture.process.getTaskService().createTaskQuery().processInstanceId(ordinary).count());
            fixture.completeHuman(fixture.humanTask(high, "escalated"), "high-reviewer", "901", Map.of());
            fixture.completeHuman(fixture.humanTask(ordinary, "routine"), "routine-reviewer", "902", Map.of());
            fixture.requireFinished(high);
            fixture.requireFinished(ordinary);
        }
    }

    @Test
    void bounded_loop_updates_carry_in_three_distinct_native_scopes() throws Exception {
        String definition = "boundedLoop";
        String xml = model(definition, """
                <startEvent id="start"/><subProcess id="loop"><startEvent id="begin"/><userTask id="step"/><endEvent id="stepEnd"/>
                <sequenceFlow id="toStep" sourceRef="begin" targetRef="step"/><sequenceFlow id="endStep" sourceRef="step" targetRef="stepEnd"/>
                </subProcess><endEvent id="end"/><sequenceFlow id="toLoop" sourceRef="start" targetRef="loop"/>
                <sequenceFlow id="endLoop" sourceRef="loop" targetRef="end"/>
                """);
        var rounds = schema("round", "integer");
        var condition = new AutomationNodeBindingVO.ConditionOperationVO("LT", List.of(
                new AutomationNodeBindingVO.ConditionPathVO("round"), new AutomationNodeBindingVO.ConditionLiteralVO(3)));
        var loop = new AutomationNodeBindingVO("loop", "LOOP", EMPTY, rounds, List.of(), List.of(), policy(false),
                null, null, null, null, new AutomationNodeBindingVO.LoopControlVO(3, condition, rounds, Map.of("round", 0),
                List.of(new MappingVO("round", "nodes.loop.step.output.round"))), null);
        var step = human("loop.step", rounds, rounds, List.of(new MappingVO("round", "scope.round")), List.of(), "firstLane");
        try (var fixture = Harness.open(database)) {
            fixture.publish(definition, List.of(rootModel(definition, xml)), AMOUNT, EMPTY, List.of(loop, step));
            String root = fixture.start(definition, "three-iterations", 0);
            var taskRefs = new ArrayList<String>();
            for (int iteration = 0; iteration < 3; iteration++) {
                String task = fixture.humanTask(root, "loop.step");
                assertFalse(taskRefs.contains(task));
                if (iteration > 0) {
                    String stale = taskRefs.getLast();
                    assertThrows(ProcessResourceNotFoundException.class, () -> fixture.human.completeAction(
                            context("loop-reviewer", "complete.v2", "901"), stale,
                            new CompleteEngineHumanTaskCommand("stale-" + stale, "stale-revision", Map.of("round", 3))));
                }
                taskRefs.add(task);
                assertEquals(Integer.toString(iteration), fixture.owner.queryForObject("""
                        SELECT input_json->>'round' FROM tianji_process.tianji_process_node_execution
                        WHERE instance_id=? AND node_id='loop.step' AND state='WAITING'
                        """, String.class, root));
                fixture.completeHuman(task, "loop-reviewer", "901", Map.of("round", iteration + 1));
            }
            assertEquals(List.of(0, 1, 2), fixture.owner.queryForList("""
                    SELECT iteration_index FROM tianji_process.tianji_process_node_execution
                    WHERE instance_id=? AND node_id='loop.step' AND state='COMPLETED' ORDER BY iteration_index
                    """, Integer.class, root));
            assertEquals(3, fixture.owner.queryForObject("""
                    SELECT count(DISTINCT parent_scope_id) FROM tianji_process.tianji_process_node_execution
                    WHERE instance_id=? AND node_id='loop.step' AND state='COMPLETED'
                    """, Integer.class, root));
            fixture.requireFinished(root);
        }
    }

    @Test
    void loop_limit_routes_to_the_published_failure_end_without_a_fourth_attempt() throws Exception {
        String definition = "loopLimit";
        String xml = model(definition, """
                <startEvent id="start"/><subProcess id="loop"><startEvent id="begin"/><userTask id="step"/><endEvent id="stepEnd"/>
                <sequenceFlow id="toStep" sourceRef="begin" targetRef="step"/><sequenceFlow id="endStep" sourceRef="step" targetRef="stepEnd"/>
                </subProcess><endEvent id="end"/><sequenceFlow id="toLoop" sourceRef="start" targetRef="loop"/>
                <sequenceFlow id="endLoop" sourceRef="loop" targetRef="end"/>
                """);
        var rounds = schema("round", "integer");
        var continueUntilFourth = new AutomationNodeBindingVO.ConditionOperationVO("LT", List.of(
                new AutomationNodeBindingVO.ConditionPathVO("round"), new AutomationNodeBindingVO.ConditionLiteralVO(4)));
        var loop = new AutomationNodeBindingVO("loop", "LOOP", EMPTY, rounds, List.of(), List.of(), policy(false),
                null, null, null, null, new AutomationNodeBindingVO.LoopControlVO(3, continueUntilFourth, rounds,
                Map.of("round", 0), List.of(new MappingVO("round", "nodes.loop.step.output.round"))), null);
        var stepInput = schema(Map.of("round", Map.of("type", "integer"), "rootAmount", Map.of("type", "integer")));
        var step = human("loop.step", stepInput, rounds,
                List.of(new MappingVO("round", "scope.round"), new MappingVO("rootAmount", "input.amount")),
                List.of(), "firstLane");
        try (var fixture = Harness.open(database)) {
            fixture.publish(definition, List.of(rootModel(definition, xml)), AMOUNT, EMPTY, List.of(loop, step));
            String root = fixture.start(definition, "fourth-iteration-blocked", 23);
            for (int round = 1; round <= 3; round++) {
                assertEquals(List.of(Integer.toString(round - 1), "23"), fixture.owner.queryForObject("""
                        SELECT input_json->>'round',input_json->>'rootAmount'
                        FROM tianji_process.tianji_process_node_execution
                        WHERE instance_id=? AND node_id='loop.step' AND state='WAITING'
                        """, (row, index) -> List.of(row.getString(1), row.getString(2)), root));
                fixture.completeHuman(fixture.humanTask(root, "loop.step"), "loop-reviewer", "901", Map.of("round", round));
            }
            assertEquals("FAILED", fixture.nodeState(root, "loop"));
            assertEquals(3, fixture.count("tianji_process.tianji_process_node_execution",
                    "WHERE instance_id=? AND node_id='loop.step' AND state='COMPLETED'", root));
            assertEquals(0, fixture.process.getTaskService().createTaskQuery().processInstanceId(root).count());
            fixture.requireFinished(root);
        }
    }

    @Test
    void loop_limit_enters_the_published_failure_branch_after_restart() throws Exception {
        String definition = "loopLimitBranch";
        String xml = model(definition, """
                <startEvent id="start"/><subProcess id="loop"><startEvent id="begin"/><userTask id="step"/><endEvent id="stepEnd"/>
                <sequenceFlow id="toStep" sourceRef="begin" targetRef="step"/><sequenceFlow id="endStep" sourceRef="step" targetRef="stepEnd"/>
                </subProcess><userTask id="fallback"/><endEvent id="end"/>
                <sequenceFlow id="toLoop" sourceRef="start" targetRef="loop"/>
                <sequenceFlow id="endLoop" sourceRef="loop" targetRef="end"/>
                <sequenceFlow id="endFallback" sourceRef="fallback" targetRef="end"/>
                """);
        var rounds = schema("round", "integer");
        var policy = new NodePolicyVO(Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofSeconds(30),
                new NodePolicyVO.PolicyActionVO("BRANCH", "fallback"), new NodePolicyVO.PolicyActionVO("END", null),
                new NodePolicyVO.PolicyActionVO("END", null), policy(false).retry(), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
        var loop = new AutomationNodeBindingVO("loop", "LOOP", EMPTY, rounds, List.of(), List.of(), policy,
                null, null, null, null, new AutomationNodeBindingVO.LoopControlVO(2,
                new AutomationNodeBindingVO.ConditionLiteralVO(true), rounds, Map.of("round", 0),
                List.of(new MappingVO("round", "nodes.loop.step.output.round"))), null);
        var step = human("loop.step", rounds, rounds, List.of(new MappingVO("round", "scope.round")), List.of(), "firstLane");
        var fallback = human("fallback", AMOUNT, EMPTY, List.of(new MappingVO("amount", "input.amount")),
                List.of(), "secondLane");
        String root;
        String second;
        try (var fixture = Harness.open(database)) {
            fixture.publish(definition, List.of(rootModel(definition, xml)), AMOUNT, EMPTY, List.of(loop, step, fallback));
            root = fixture.start(definition, "handle-loop-limit", 23);
            fixture.completeHuman(fixture.humanTask(root, "loop.step"), "loop-reviewer", "901", Map.of("round", 1));
            second = fixture.humanTask(root, "loop.step");
            assertEquals(0, fixture.count("tianji_process.tianji_process_node_execution",
                    "WHERE instance_id=? AND node_id='fallback'", root));
        }
        try (var fixture = Harness.open(database)) {
            assertEquals(second, fixture.humanTask(root, "loop.step"));
            fixture.completeHuman(second, "loop-reviewer", "901", Map.of("round", 2));
            assertEquals("FAILED", fixture.nodeState(root, "loop"));
            assertEquals(2, fixture.count("tianji_process.tianji_process_node_execution",
                    "WHERE instance_id=? AND node_id='loop.step' AND state='COMPLETED'", root));
            assertEquals(1, fixture.process.getTaskService().createTaskQuery().processInstanceId(root).count());
            assertEquals("23", fixture.nodeValue(root, "fallback", "amount", false));
            fixture.completeHuman(fixture.humanTask(root, "fallback"), "fallback-reviewer", "902", Map.of());
            fixture.requireFinished(root);
        }
    }

    @Test
    void multi_instance_and_local_subprocess_keep_three_child_slots_in_one_root() throws Exception {
        String definition = "multiParent";
        String xml = model(definition, """
                <startEvent id="start"/><subProcess id="multi"><startEvent id="begin"/><callActivity id="call" calledElement="pinnedChild"/>
                <endEvent id="childEnd"/><sequenceFlow id="enterChild" sourceRef="begin" targetRef="call"/>
                <sequenceFlow id="returnChild" sourceRef="call" targetRef="childEnd"/></subProcess><endEvent id="end"/>
                <sequenceFlow id="toMulti" sourceRef="start" targetRef="multi"/><sequenceFlow id="endMulti" sourceRef="multi" targetRef="end"/>
                """);
        String childXml = model("pinnedChild", """
                <startEvent id="start"/><userTask id="step"/><endEvent id="end"/>
                <sequenceFlow id="toStep" sourceRef="start" targetRef="step"/><sequenceFlow id="toEnd" sourceRef="step" targetRef="end"/>
                """);
        var items = schema(Map.of("items", Map.of("type", "array", "items", Map.of("type", "integer"), "maxItems", 3)));
        var collected = schema(Map.of("value", Map.of("type", "array", "items", Map.of("type", "integer"), "maxItems", 3)));
        var value = schema("value", "integer");
        var multi = new AutomationNodeBindingVO("multi", "MULTI_INSTANCE", EMPTY, collected, List.of(), List.of(), policy(false),
                null, null, null, null, new AutomationNodeBindingVO.MultiInstanceControlVO("input.items", true, 3, null),
                List.of(new AutomationNodeBindingVO.MergeVO("value", List.of("multi.call"), "COLLECT_INDEXED")));
        var call = new AutomationNodeBindingVO("multi.call", "SUBPROCESS", EMPTY, value, List.of(), List.of(), policy(false),
                null, null, null, null, new AutomationNodeBindingVO.SubprocessControlVO("pinnedChild", sha256(childXml)), null);
        var child = human("multi.call.step", EMPTY, value, List.of(),
                List.of(new MappingVO("value", "nodes.multi.call.step.output.value")), "firstLane");
        try (var fixture = Harness.open(database)) {
            fixture.publish(definition, List.of(rootModel(definition, xml), rootModel("pinnedChild", childXml)), items, EMPTY,
                    List.of(multi, call, child));
            String root = fixture.start(startRequest(definition, "three-child-slots", Map.of("items", List.of(4, 8, 6))));
            for (int result : List.of(4, 8, 6)) {
                fixture.completeHuman(fixture.humanTask(root, "multi.call.step"), "child-reviewer", "901", Map.of("value", result));
            }
            assertEquals("[4, 8, 6]", fixture.nodeValue(root, "multi", "value", true));
            assertEquals(List.of(0, 1, 2), fixture.owner.queryForList("""
                    SELECT branch_index FROM tianji_process.tianji_process_node_execution
                    WHERE instance_id=? AND node_id='multi.call' AND state='COMPLETED' ORDER BY branch_index
                    """, Integer.class, root));
            assertEquals(3, fixture.count("tianji_process.tianji_process_node_execution",
                    "WHERE instance_id=? AND node_id='multi.call.step' AND state='COMPLETED'", root));
            assertEquals(0, fixture.count("tianji_process.act_hi_procinst", "WHERE super_process_instance_id_=?", root));
            fixture.requireFinished(root);
        }
    }

    @Test
    void parallel_multi_instance_early_completion_freezes_the_two_included_indices() throws Exception {
        String definition = "parallelEarly";
        String graph = """
                <startEvent id="start"/><subProcess id="multi"><startEvent id="begin"/>
                <callActivity id="child" calledElement="earlyChild"/><userTask id="step"/><endEvent id="stepEnd"/>
                <sequenceFlow id="toChild" sourceRef="begin" targetRef="child"/>
                <sequenceFlow id="toStep" sourceRef="child" targetRef="step"/><sequenceFlow id="endStep" sourceRef="step" targetRef="stepEnd"/>
                </subProcess><endEvent id="end"/><sequenceFlow id="toMulti" sourceRef="start" targetRef="multi"/>
                <sequenceFlow id="endMulti" sourceRef="multi" targetRef="end"/>
                """;
        String childXml = model("earlyChild", """
                <startEvent id="begin"/><userTask id="review"/><endEvent id="end"/>
                <sequenceFlow id="toReview" sourceRef="begin" targetRef="review"/>
                <sequenceFlow id="toEnd" sourceRef="review" targetRef="end"/>
                """);
        var items = schema(Map.of("items", Map.of("type", "array", "items", Map.of("type", "integer"), "maxItems", 3)));
        var collected = schema(Map.of("value", Map.of("type", "array", "items", Map.of("type", "integer"), "maxItems", 3),
                "accepted", Map.of("type", "boolean")));
        var result = schema(Map.of("value", Map.of("type", "integer"), "accepted", Map.of("type", "boolean")));
        var step = human("multi.step", EMPTY, result, List.of(), List.of(), "firstLane");
        var child = new AutomationNodeBindingVO("multi.child", "SUBPROCESS", EMPTY, EMPTY, List.of(), List.of(), policy(false),
                null, null, null, null, new AutomationNodeBindingVO.SubprocessControlVO("earlyChild", sha256(childXml)), null);
        var review = human("multi.child.review", EMPTY, EMPTY, List.of(), List.of(), "firstLane");
        var merges = List.of(new AutomationNodeBindingVO.MergeVO("value", List.of("multi.step"), "COLLECT_INDEXED"),
                new AutomationNodeBindingVO.MergeVO("accepted", List.of("multi.step"), "ANY"));
        var early = new AutomationNodeBindingVO.ConditionPathVO("accepted");
        try (var fixture = Harness.open(database)) {
            for (boolean earlyCompletion : List.of(true, false)) {
                String modelKey = earlyCompletion ? definition : "parallelAll";
                var multi = new AutomationNodeBindingVO("multi", "MULTI_INSTANCE", EMPTY, collected, List.of(), List.of(), policy(false),
                        null, null, null, null, new AutomationNodeBindingVO.MultiInstanceControlVO("input.items", false, 3,
                        earlyCompletion ? early : null), merges);
                fixture.publish(modelKey, List.of(rootModel(modelKey, model(modelKey, graph)), rootModel("earlyChild", childXml)),
                        items, EMPTY, List.of(multi, child, review, step));
                String root = fixture.start(startRequest(modelKey, modelKey + "-indexes", Map.of("items", List.of(0, 1, 2))));
                for (int index : List.of(0, 1, 2)) {
                    fixture.completeHuman(fixture.humanTask(root, "multi.child.review", index), "child-reviewer", "901", Map.of());
                }
                String spared = fixture.humanTask(root, "multi.step", 1);
                assertEquals(3, fixture.process.getTaskService().createTaskQuery().processInstanceId(root).count());
                fixture.completeHuman(fixture.humanTask(root, "multi.step", 2), "multi-reviewer", "901", Map.of("accepted", false, "value", 22));
                assertEquals(2, fixture.process.getTaskService().createTaskQuery().processInstanceId(root).count());
                fixture.completeHuman(fixture.humanTask(root, "multi.step", 0), "multi-reviewer", "901", Map.of("accepted", true, "value", 11));
                if (!earlyCompletion) {
                    assertEquals(spared, fixture.humanTask(root, "multi.step", 1));
                    fixture.completeHuman(spared, "multi-reviewer", "901", Map.of("accepted", false, "value", 17));
                }
                assertEquals(earlyCompletion ? "[11, 22]" : "[11, 17, 22]", fixture.nodeValue(root, "multi", "value", true));
                assertEquals("true", fixture.nodeValue(root, "multi", "accepted", true));
                assertEquals(earlyCompletion ? List.of(0, 2) : List.of(0, 1, 2), fixture.owner.queryForList("""
                        SELECT branch_index FROM tianji_process.tianji_process_node_execution
                        WHERE instance_id=? AND node_id='multi.step' AND state='COMPLETED' AND NOT excluded ORDER BY branch_index
                        """, Integer.class, root));
                if (earlyCompletion) {
                    assertEquals(1, fixture.count("tianji_process.tianji_process_node_execution",
                            "WHERE instance_id=? AND node_id='multi.step' AND branch_index=1 AND state='CANCELLED' AND excluded AND output_sha256 IS NULL", root));
                    assertEquals(1, fixture.count("tianji_process.tianji_process_node_execution",
                            "WHERE instance_id=? AND node_id='multi.child.review' AND branch_index=1 AND state='COMPLETED' AND excluded AND output_sha256 IS NOT NULL", root));
                    assertThrows(ProcessResourceNotFoundException.class, () -> fixture.human.completeAction(
                            context("multi-reviewer", "complete.v2", "901"), spared,
                            new CompleteEngineHumanTaskCommand("late-" + spared, "stale-revision", Map.of("accepted", true, "value", 100))));
                }
                assertEquals(earlyCompletion ? 2 : 3, fixture.count("tianji_process.tianji_process_node_execution",
                        "WHERE instance_id=? AND node_id='multi.child.review' AND state='COMPLETED' AND NOT excluded", root));
                assertEquals(0, fixture.process.getTaskService().createTaskQuery().processInstanceId(root).count());
                fixture.requireFinished(root);
            }
        }
    }

    private static List<AutomationNodeBindingVO> parallelBindings(ProcessSchemaVO output, String reducer) {
        var value = schema("value", "integer");
        var join = new AutomationNodeBindingVO("join", "AND_JOIN", EMPTY, output, List.of(),
                List.of(new MappingVO("value", "nodes.join.output.value")), policy(false), null, null, null, null, null,
                List.of(new AutomationNodeBindingVO.MergeVO("value", List.of("left", "right"), reducer)));
        return List.of(human("left", EMPTY, value, List.of(), List.of(), "firstLane"),
                human("right", EMPTY, value, List.of(), List.of(), "secondLane"), join);
    }

    private static String parallelModel(String definition) {
        return model(definition, """
                <startEvent id="start"/><parallelGateway id="split"/><userTask id="left"/><userTask id="right"/>
                <parallelGateway id="join"/><endEvent id="end"/>
                <sequenceFlow id="toSplit" sourceRef="start" targetRef="split"/>
                <sequenceFlow id="firstBranch" sourceRef="split" targetRef="left"/>
                <sequenceFlow id="secondBranch" sourceRef="split" targetRef="right"/>
                <sequenceFlow id="leftJoin" sourceRef="left" targetRef="join"/>
                <sequenceFlow id="rightJoin" sourceRef="right" targetRef="join"/>
                <sequenceFlow id="toEnd" sourceRef="join" targetRef="end"/>
                """);
    }

    private static String model(String definition, String graph) {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:step30">
                  <process id="%s" isExecutable="true">%s</process>
                </definitions>
                """.formatted(definition, graph);
    }

    private static AutomationModelSourceVO.ModelSourceVO rootModel(String definition, String xml) {
        return new AutomationModelSourceVO.ModelSourceVO("BPMN", definition, xml);
    }

    private static AutomationModelSourceVO.ModelSourceVO decisionModel(String key, String before, String after) throws java.io.IOException {
        String xml = new ClassPathResource("fixtures/baseline.dmn").getContentAsString(StandardCharsets.UTF_8);
        return new AutomationModelSourceVO.ModelSourceVO("DMN", key, xml.replace("baselineDecision", key)
                .replace("'" + before + "'", "'" + after + "'"));
    }

    private static AutomationNodeBindingVO rule(String node, AutomationModelSourceVO.ModelSourceVO model) throws Exception {
        var input = List.of(new MappingVO("amount", "input.amount"));
        var route = schema("route", "string");
        return new AutomationNodeBindingVO(node, "RULE", AMOUNT, route, input, List.of(), policy(false), null,
                new AutomationNodeBindingVO.DecisionRefVO(model.definitionKey(), sha256(model.xml()), input,
                        List.of(new MappingVO("route", "scope.route"))), null, null, null, null);
    }

    private static String sha256(String xml) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static AutomationStartCommand startRequest(String definition, String key, Map<String, Object> values) {
        return new AutomationStartCommand(definition, "business-" + key, "start-" + key, "trace-" + key, values, Map.of());
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
        return schema(Map.of(property, Map.of("type", type)));
    }

    private static ProcessSchemaVO schema(Map<String, Object> properties) {
        return ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", properties,
                "required", properties.keySet().stream().sorted().toList(), "additionalProperties", false));
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
        private final AutomationRegistrationVO nestedResult = new AutomationRegistrationVO(20, ISSUER,
                "typed-worker", "worker-source", "nestedRisk", 1, ProcessNodeLifecycleListener.schemaSha256(AMOUNT),
                ProcessNodeLifecycleListener.schemaSha256(NESTED_REVIEW_RESULT),
                WorkerEffectSafetyEnum.READ_ONLY, "handler-1", "risk-reader", 8, Duration.ofMinutes(2));
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
            var registry = new WorkerTopicRegistry(List.of(legacy), List.of(transactional, shortLease, external, readOnly, nestedResult));
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
            return open(provision(installBusiness));
        }

        public static Harness openOnProvisionedDatabase(JdbcTemplate owner, String url, String role) throws Exception {
            return open(new DatabaseVO(owner, url, role, null));
        }

        private static Harness open(DatabaseVO database) throws Exception {
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

        private void deployCompetingDecision(String key, String xml) {
            var repository = application.getBean(SpringDmnEngineConfiguration.class).getDmnRepositoryService();
            String tenant = Long.toString(OWNER.tenantId());
            var deployed = repository.createDeployment().tenantId(tenant).name("fixture-competing-" + key)
                    .addDmnBytes(key + ".dmn", xml.getBytes(StandardCharsets.UTF_8)).deploy();
            var latest = repository.createDecisionQuery().decisionKey(key).decisionTenantId(tenant).latestVersion().singleResult();
            assertNotNull(latest);
            assertEquals(deployed.getId(), latest.getDeploymentId());
            assertTrue(latest.getVersion() > 1);
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

        private String humanTask(String root, String node) {
            return humanTask(root, node, null);
        }

        private String humanTask(String root, String node, Integer branch) {
            return owner.queryForObject("""
                    SELECT t.id_ FROM tianji_process.act_ru_task t
                     JOIN tianji_process.act_ru_execution e ON e.id_=t.execution_id_
                     JOIN tianji_process.tianji_process_node_execution n ON n.attempt_scope_id=e.parent_id_
                    WHERE n.instance_id=? AND n.node_id=? AND n.state='WAITING'
                      AND (?::integer IS NULL OR n.branch_index=?)
                    """, String.class, root, node, branch, branch);
        }

        private void requireFinished(String root) {
            assertNotNull(process.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(root).singleResult().getEndTime());
            assertEquals(0, process.getRuntimeService().createProcessInstanceQuery().processInstanceId(root).count());
        }

        private void completeHuman(String task, String subject, String role, Map<String, Object> output) {
            var reader = context(subject, "get.v2", role);
            var initial = human.observeAction(reader, task);
            assertEquals(List.of("CLAIM"), initial.getAvailableActions());
            human.claim(context(subject, "claim", role), new ClaimHumanTaskCommand("claim-" + task, task, initial.getVersion()));
            String version = human.observeAction(reader, task).getVersion();
            var completer = context(subject, "complete.v2", role);
            var command = new CompleteEngineHumanTaskCommand("complete-" + task, version, output);
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
