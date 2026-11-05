package top.egon.cola.platform.tianji.process.server.acceptance;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.Association;
import org.flowable.bpmn.model.AssociationDirection;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.CaseServiceTask;
import org.flowable.bpmn.model.CompensateEventDefinition;
import org.flowable.bpmn.model.EventGateway;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.ThrowEvent;
import org.flowable.cmmn.converter.CmmnXmlConverter;
import org.flowable.dmn.model.DecisionTable;
import org.flowable.dmn.model.HitPolicy;
import org.flowable.dmn.xml.converter.DmnXMLConverter;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelResource;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.PublishProcessReleaseCommand;
import top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ValidatedCaseModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.CaseSource;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTaskField;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.MessageWait;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerCompensationVO;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.INTEGER;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.STRING;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect.EXTERNAL_IDEMPOTENT;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect.TRANSACTIONAL_RECEIPT;

class ExpenseFixtureModelsTest {
    private static final String BPMN = "/processes/expense-review.bpmn20.xml";
    private static final String DMN = "/decisions/expense-routing.dmn";
    private static final String CMMN = "/cases/expense-exception.cmmn";
    private static final String CONTRACT = "/fixtures/expense-four-path-contract.json";
    private static final String RELEASE = "expense-v1";
    private static final Map<String, ValidatedProcessModel.ValueType> INPUTS = Map.of("operationId", STRING, "amount", INTEGER);
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final ProcessModelValidator validator = new ProcessModelValidator();

    @Test
    void modelResourcesExist() {
        assertAll(
                () -> assertNotNull(getClass().getResource(BPMN), "expense BPMN fixture"),
                () -> assertNotNull(getClass().getResource(DMN), "expense DMN fixture"),
                () -> assertNotNull(getClass().getResource(CMMN), "expense CMMN fixture"),
                () -> assertNotNull(getClass().getResource(CONTRACT), "expense contract fixture"));
    }

    @Test
    void validatesPinnedReleaseAndNativeGraph() throws Exception {
        byte[] processXml = resource(BPMN);
        byte[] decisionXml = resource(DMN);
        byte[] caseXml = resource(CMMN);
        var process = validator.validateBpmn(processXml, "expenseReview");
        var decision = validator.validateDmn(decisionXml, "expenseRouting");
        var caze = validator.validateCmmn(caseXml, "expenseException");
        validator.validateDecisionBinding(process, INPUTS, decision);
        validator.validateHumanTaskBindings(process, INPUTS, decision);
        validator.validateWorkerBindings(process, INPUTS, decision);

        assertEquals(Map.of("classifyExpense", "expenseRouting"), process.decisionTasks());
        assertEquals(Map.of("amount", INTEGER), decision.inputs());
        assertEquals(Map.of("reviewLane", STRING), decision.outputs());
        assertEquals(List.of("smallExpense", "largeExpense"), decision.ruleIds());
        var review = process.humanTasks().get("reviewExpense");
        assertEquals("reviewLane", review.laneVariable());
        assertEquals(Map.of("standard", Set.of("501", "601"), "exception", Set.of("502", "602")), review.lanes());
        assertEquals(List.of(new HumanTaskField("finding", "reviewFinding", STRING)), review.fields());
        assertEquals(Map.of("waitAgentResult",
                new MessageWait("TianjiAgentFinishedV1", "agentStart", "waitAgentTimeout", Duration.ofSeconds(20), "agentStatus")),
                process.messageWaits());
        assertEquals(Map.of("caseSource",
                new CaseSource("expenseException", "waitCaseResult", "TianjiCaseCompletedV1", "waitCaseTimeout", Duration.ofMinutes(3))),
                process.caseSources());
        assertEquals(Map.of("reserveExpense",
                new WorkerCompensationVO("reserveExpense", "reserveCompBoundary", "reserveCompAssociation",
                        "undoReservation", "throwUndo", "compensatedEnd")), process.workerCompensations());
        assertEquals(Set.of("primary", "supplemental", "milestone"), caze.planItems().keySet());
        assertTrue(caze.planItems().get("supplemental").actions().contains(ValidatedCaseModel.Action.TERMINATE));
        assertEquals(Set.of("502", "602", "503", "603"), caze.operatorRoleIds());

        var release = new PublishProcessReleaseCommand(RELEASE, List.of(
                new ProcessModelResource(ProcessModelTypeEnum.BPMN, process.definitionKey(), text(processXml),
                        Map.of("operationId", ProcessValueTypeEnum.STRING, "amount", ProcessValueTypeEnum.INTEGER)),
                new ProcessModelResource(ProcessModelTypeEnum.DMN, decision.definitionKey(), text(decisionXml), Map.of()),
                new ProcessModelResource(ProcessModelTypeEnum.CMMN, caze.definitionKey(), text(caseXml), Map.of())));
        assertEquals(3, release.models().size());
        var registry = fixtureWorkerTopics();
        for (long tenant : List.of(20L, 21L)) {
            var caseKey = new CaseSourceRegistry.BindingKey(tenant, release.releaseVersion(), process.definitionKey(), sha256(processXml),
                    "caseSource", caze.definitionKey(), sha256(caseXml));
            assertEquals(tenant, caseKey.tenantId());
            process.workerTasks().forEach((element, task) -> {
                assertTrue(registry.acceptsModel(tenant, task), element);
                assertEquals("agentStart".equals(element), registry.isInternalAgentDispatch(tenant, task), element);
            });
        }
        assertNativeConverters(processXml, decisionXml, caseXml);
    }

    @Test
    void rejectsUnsafeModelChanges() throws Exception {
        byte[] processXml = resource(BPMN);
        byte[] decisionXml = resource(DMN);
        byte[] caseXml = resource(CMMN);
        var process = validator.validateBpmn(processXml, "expenseReview");
        var decision = validator.validateDmn(decisionXml, "expenseRouting");

        // Keep the graph connected but make original reserve reachable only on the normal branch.
        String bypass = replaceOnce(text(processXml),
                "id=\"toReserve\" sourceRef=\"classifyExpense\" targetRef=\"reserveExpense\"",
                "id=\"toReserve\" sourceRef=\"classifyExpense\" targetRef=\"agentStart\"");
        bypass = replaceOnce(bypass, "id=\"reserveNormal\" sourceRef=\"reserveExpense\" targetRef=\"agentStart\"",
                "id=\"reserveNormal\" sourceRef=\"reserveExpense\" targetRef=\"notifyExpense\"");
        byte[] bypassXml = utf8(replaceOnce(bypass,
                "id=\"onAgentSuccess\" sourceRef=\"agentStatusRouting\" targetRef=\"notifyExpense\"",
                "id=\"onAgentSuccess\" sourceRef=\"agentStatusRouting\" targetRef=\"reserveExpense\""));
        var missingLane = validator.validateDmn(utf8(replaceOnce(text(decisionXml), "'exception'", "'unconfigured'")), "expenseRouting");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bypassXml, "expenseReview")),
                () -> assertThrows(IllegalArgumentException.class, () -> validator.validateHumanTaskBindings(process, INPUTS, missingLane)),
                () -> assertThrows(IllegalArgumentException.class, () -> validator.validateWorkerBindings(process, Map.of("amount", INTEGER), decision)),
                () -> assertThrows(IllegalArgumentException.class, () -> validator.validateCmmn(caseXml, "otherCase")));
    }

    @Test
    void canonicalFixtureMatchesModelsAndNeutralDtos() throws Exception {
        byte[] processXml = resource(BPMN);
        byte[] decisionXml = resource(DMN);
        byte[] caseXml = resource(CMMN);
        var process = validator.validateBpmn(processXml, "expenseReview");
        var caze = validator.validateCmmn(caseXml, "expenseException");
        var fixture = JSON.readTree(resource(CONTRACT));

        assertFields(fixture, Set.of("schemaVersion", "releaseVersion", "models", "startInputs", "paths", "humanTask", "caseInstance"));
        assertTrue(fixture.path("schemaVersion").isIntegralNumber());
        assertEquals(1, fixture.path("schemaVersion").intValue());
        assertEquals(RELEASE, fixture.path("releaseVersion").stringValue());
        var models = fixture.path("models");
        assertFields(models, Set.of("BPMN", "DMN", "CMMN"));
        assertModel(models.path("BPMN"), "expenseReview", processXml);
        assertModel(models.path("DMN"), "expenseRouting", decisionXml);
        assertModel(models.path("CMMN"), "expenseException", caseXml);
        var inputs = fixture.path("startInputs");
        assertFields(inputs, INPUTS.keySet());
        INPUTS.forEach((name, type) -> assertEquals(type.name(), inputs.path(name).stringValue(), name));
        var paths = fixture.path("paths");
        assertFields(paths, Set.of("NORMAL", "AGENT_FAILURE", "MANUAL_CASE", "RECOVERY"));
        assertPath(paths.path("NORMAL"), 100, "smallExpense", "standard");
        assertPath(paths.path("AGENT_FAILURE"), 700, "smallExpense", "standard");
        assertPath(paths.path("MANUAL_CASE"), 1500, "largeExpense", "exception");
        assertPath(paths.path("RECOVERY"), 200, "smallExpense", "standard");

        var task = JSON.treeToValue(fixture.path("humanTask"), HumanTaskDTO.class);
        var review = process.humanTasks().get(task.taskDefinitionKey());
        assertNotNull(review);
        assertEquals(process.definitionKey(), task.definitionKey());
        assertEquals(RELEASE, task.releaseVersion());
        assertEquals(HumanTaskScopeTypeEnum.PROCESS, task.scopeType());
        assertEquals(HumanTaskKindEnum.valueOf(review.kind().name()), task.kind());
        assertEquals(paths.path("MANUAL_CASE").path("reviewLane").stringValue(), task.lane());
        assertEquals(review.lanes().keySet(), Set.copyOf(task.availableLanes()));
        assertEquals(Map.of("finding", ProcessValueTypeEnum.STRING), task.formSchema());
        assertNull(task.approvalField());
        assertFalse(task.assigned());

        var caseInstance = JSON.treeToValue(fixture.path("caseInstance"), CaseInstanceDTO.class);
        assertEquals(caze.definitionKey(), caseInstance.definitionKey());
        assertEquals(RELEASE, caseInstance.releaseVersion());
        assertEquals(CaseInstanceStatusEnum.ACTIVE, caseInstance.status());
        var planItems = caseInstance.planItems().stream().collect(Collectors.toMap(CasePlanItemDTO::planItemDefinitionKey, item -> item));
        assertEquals(caze.planItems().values().stream().map(ValidatedCaseModel.PlanItemPolicy::definitionId).collect(Collectors.toSet()),
                planItems.keySet());
        assertEquals(CasePlanItemStateEnum.COMPLETED, planItems.get("primaryTask").state());
        assertEquals(CasePlanItemStateEnum.ENABLED, planItems.get("supplementalTask").state());
        assertEquals(Set.of(CasePlanItemActionEnum.DISABLE, CasePlanItemActionEnum.START),
                Set.copyOf(planItems.get("supplementalTask").availableActions()));
        assertEquals(CasePlanItemStateEnum.AVAILABLE, planItems.get("completionMilestone").state());
        assertTrue(planItems.get("completionMilestone").availableActions().isEmpty());
    }

    private static void assertNativeConverters(byte[] processXml, byte[] decisionXml, byte[] caseXml) {
        var process = new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(processXml), true, true).getMainProcess();
        var caseSource = assertInstanceOf(CaseServiceTask.class, process.getFlowElement("caseSource"));
        assertEquals("expenseException", caseSource.getCaseDefinitionKey());
        assertFalse(caseSource.isFallbackToDefaultTenant());
        for (String id : List.of("agentWait", "caseWait")) {
            var wait = assertInstanceOf(EventGateway.class, process.getFlowElement(id));
            assertEquals(2, wait.getOutgoingFlows().size(), id);
        }
        var split = assertInstanceOf(ExclusiveGateway.class, process.getFlowElement("agentStatusRouting"));
        var merge = assertInstanceOf(ExclusiveGateway.class, process.getFlowElement("compensationJoin"));
        assertEquals("onAgentFailure", split.getDefaultFlow());
        assertEquals(2, merge.getIncomingFlows().size());
        assertEquals(1, merge.getOutgoingFlows().size());
        var boundary = assertInstanceOf(BoundaryEvent.class, process.getFlowElement("reserveCompBoundary"));
        var association = assertInstanceOf(Association.class, process.getArtifact("reserveCompAssociation"));
        var thrown = assertInstanceOf(ThrowEvent.class, process.getFlowElement("throwUndo"));
        var compensation = assertInstanceOf(CompensateEventDefinition.class, thrown.getEventDefinitions().getFirst());
        assertEquals("reserveExpense", boundary.getAttachedToRefId());
        assertFalse(boundary.isCancelActivity());
        assertEquals(AssociationDirection.ONE, association.getAssociationDirection());
        assertEquals("reserveCompBoundary", association.getSourceRef());
        assertEquals("undoReservation", association.getTargetRef());
        assertEquals("reserveExpense", compensation.getActivityRef());
        assertTrue(compensation.isWaitForCompletion());
        assertTrue(assertInstanceOf(ServiceTask.class, process.getFlowElement("undoReservation")).isForCompensation());

        var dmn = new DmnXMLConverter().convertToDmnModel(() -> new ByteArrayInputStream(decisionXml), true, true);
        var table = assertInstanceOf(DecisionTable.class, dmn.getDecisions().getFirst().getExpression());
        assertEquals(HitPolicy.FIRST, table.getHitPolicy());
        assertEquals(List.of("<= 1000", "> 1000"), table.getRules().stream()
                .map(rule -> rule.getInputEntries().getFirst().getInputEntry().getText()).toList());
        assertEquals(List.of("'standard'", "'exception'"), table.getRules().stream()
                .map(rule -> rule.getOutputEntries().getFirst().getOutputEntry().getText()).toList());

        var cmmn = new CmmnXmlConverter().convertToCmmnModel(() -> new ByteArrayInputStream(caseXml), true, true);
        var plan = cmmn.getPrimaryCase().getPlanModel();
        assertEquals("expenseException", cmmn.getPrimaryCase().getId());
        assertFalse(plan.isAutoComplete());
        assertEquals(3, plan.getPlanItems().size());
        assertEquals(1, cmmn.findPlanItem("supplemental").getEntryCriteria().size());
        assertEquals(2, cmmn.findPlanItem("milestone").getEntryCriteria().size());
    }

    private static WorkerTopicRegistry fixtureWorkerTopics() {
        var registrations = new ArrayList<WorkerTopicRegistry.Registration>();
        for (long tenant : List.of(20L, 21L)) {
            String issuer = "https://fixture.example/tenant/" + tenant;
            for (String topic : List.of("reserveBusiness", "notifyExpense", "undoReservation")) {
                registrations.add(new WorkerTopicRegistry.Registration(tenant, issuer, "business-worker", topic, "1",
                        Map.of("operationId", STRING), Map.of(), Map.of("operationId", Set.of("operationId")), Map.of(),
                        1, Duration.ofSeconds(15), Duration.ofSeconds(1), TRANSACTIONAL_RECEIPT));
            }
            registrations.add(new WorkerTopicRegistry.Registration(tenant, issuer, "bridge-worker", "agentStart", "1",
                    Map.of("requestedAmount", INTEGER), Map.of("runId", STRING), Map.of("requestedAmount", Set.of("amount")),
                    Map.of("runId", Set.of("agentRunId")), 1, Duration.ofSeconds(45), Duration.ofSeconds(1), EXTERNAL_IDEMPOTENT, true));
        }
        return new WorkerTopicRegistry(registrations);
    }

    private static void assertModel(JsonNode model, String definitionKey, byte[] xml) throws Exception {
        assertFields(model, Set.of("definitionKey", "sha256"));
        assertEquals(definitionKey, model.path("definitionKey").stringValue());
        assertEquals(sha256(xml), model.path("sha256").stringValue());
    }

    private static void assertPath(JsonNode path, int amount, String rule, String lane) {
        assertFields(path, Set.of("amount", "ruleId", "reviewLane"));
        assertTrue(path.path("amount").isIntegralNumber());
        assertEquals(amount, path.path("amount").intValue());
        assertEquals(rule, path.path("ruleId").stringValue());
        assertEquals(lane, path.path("reviewLane").stringValue());
    }

    private static void assertFields(JsonNode object, Set<String> fields) {
        assertTrue(object.isObject());
        assertEquals(fields, object.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet()));
    }

    private static String replaceOnce(String source, String old, String replacement) {
        assertTrue(source.indexOf(old) >= 0 && source.indexOf(old) == source.lastIndexOf(old), old);
        return source.replace(old, replacement);
    }

    private static byte[] resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
