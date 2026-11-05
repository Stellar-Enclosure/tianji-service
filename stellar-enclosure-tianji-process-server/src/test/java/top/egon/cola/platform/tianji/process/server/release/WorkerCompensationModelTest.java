package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.Association;
import org.flowable.bpmn.model.AssociationDirection;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.CompensateEventDefinition;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.ThrowEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Element;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerCompensationVO;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerParameter;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerTask;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type.BPMN;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.INTEGER;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.STRING;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect.TRANSACTIONAL_RECEIPT;

class WorkerCompensationModelTest {
    private static final String FIXTURE = "worker-compensation.bpmn20.xml";
    private static final String KEY = "workerCompensation";
    private static final String INVALID_MODEL = "Process model is invalid or not supported";
    private static final Map<String, WorkerCompensationVO> COMPENSATION = Map.of("reserve",
            new WorkerCompensationVO("reserve", "reserveCompBoundary", "reserveCompAssociation",
                    "undoReserve", "throwUndo", "compEnd"));

    private final ProcessModelValidator validator = new ProcessModelValidator();

    @Test
    void compensation_surface_present() {
        assertAll(
                () -> assertDoesNotThrow(() -> Class.forName(
                        ValidatedProcessModel.class.getName() + "$WorkerCompensationVO")),
                () -> assertDoesNotThrow(() -> ValidatedProcessModel.class.getDeclaredMethod("workerCompensations")),
                () -> assertNotNull(WorkerCompensationModelTest.class.getResource("/fixtures/worker-compensation.bpmn20.xml")));
    }

    @Test
    void converter_accepts_exact_fixture() throws Exception {
        byte[] xml = new ClassPathResource("fixtures/worker-compensation.bpmn20.xml").getContentAsByteArray();
        var model = assertDoesNotThrow(() -> new BpmnXMLConverter()
                .convertToBpmnModel(() -> new ByteArrayInputStream(xml), true, true));
        assertNotNull(model);
        var process = model.getMainProcess();
        assertNotNull(process);
        var original = assertInstanceOf(ServiceTask.class, process.getFlowElement("reserve"));
        var handler = assertInstanceOf(ServiceTask.class, process.getFlowElement("undoReserve"));
        assertFalse(original.isForCompensation());
        assertTrue(handler.isForCompensation());
        var boundary = assertInstanceOf(BoundaryEvent.class, process.getFlowElement("reserveCompBoundary"));
        assertFalse(boundary.isCancelActivity());
        assertEquals("reserve", boundary.getAttachedToRefId());
        var association = assertInstanceOf(Association.class, process.getArtifact("reserveCompAssociation"));
        assertEquals(AssociationDirection.ONE, association.getAssociationDirection());
        assertEquals("reserveCompBoundary", association.getSourceRef());
        assertEquals("undoReserve", association.getTargetRef());
        var thrown = assertInstanceOf(ThrowEvent.class, process.getFlowElement("throwUndo"));
        var definition = assertInstanceOf(CompensateEventDefinition.class, thrown.getEventDefinitions().getFirst());
        assertEquals("reserve", definition.getActivityRef());
        assertTrue(definition.isWaitForCompletion());

        var document = assertDoesNotThrow(() -> SafeModelXml.parse(xml, true));
        assertNotNull(document);
        String namespace = document.getDocumentElement().getNamespaceURI();
        var throwsInXml = document.getElementsByTagNameNS(namespace, "intermediateThrowEvent");
        assertEquals(1, throwsInXml.getLength());
        var throwInXml = assertInstanceOf(Element.class, throwsInXml.item(0));
        var definitions = throwInXml.getElementsByTagNameNS(namespace, "compensateEventDefinition");
        assertEquals(1, definitions.getLength());
        assertEquals("true", assertInstanceOf(Element.class, definitions.item(0)).getAttribute("waitForCompletion"));
    }

    @Test
    void validates_fixed_compensation_and_keeps_worker_bindings() throws IOException {
        byte[] xml = fixture(FIXTURE);
        var model = assertDoesNotThrow(() -> validator.validateBpmn(xml, KEY));
        assertNotNull(model);
        assertEquals(COMPENSATION, model.workerCompensations());
        assertThrows(UnsupportedOperationException.class, () -> model.workerCompensations().clear());

        var operationId = List.of(new WorkerParameter("operationId", "operationId", STRING));
        assertEquals(Map.of(
                "reserve", new WorkerTask("reserveBusiness", "1", TRANSACTIONAL_RECEIPT, operationId, List.of()),
                "undoReserve", new WorkerTask("undoReservation", "1", TRANSACTIONAL_RECEIPT, operationId, List.of()),
                "downstream", new WorkerTask("downstreamRejectable", "1", TRANSACTIONAL_RECEIPT, operationId, List.of())),
                model.workerTasks());
        assertEquals(Map.of("downstream", new ValidatedProcessModel.WorkerBusinessError(
                "downstreamError", "RESERVATION_REJECTED", "downstreamRejected", "throwUndo")),
                model.workerBusinessErrors());
        assertDoesNotThrow(() -> validator.validateWorkerBindings(model, Map.of("operationId", STRING), null));
        assertThrows(IllegalArgumentException.class, () -> validator.validateWorkerBindings(model, Map.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateWorkerBindings(model, Map.of("operationId", INTEGER), null));

        byte[] expectedXml = xml.clone();
        xml[0] = 0;
        byte[] exportedXml = model.xml();
        exportedXml[0] = 0;
        assertArrayEquals(expectedXml, model.xml());
    }

    @Test
    void compensation_metadata_defensively_copies_inputs_and_preserves_legacy_constructors() {
        byte[] xml = {1};
        var callerMetadata = new LinkedHashMap<>(COMPENSATION);
        var model = assertDoesNotThrow(() -> new ValidatedProcessModel(BPMN, "current", xml,
                Map.of(), Map.of(), List.of(), Map.of(), List.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), callerMetadata));
        assertNotNull(model);
        callerMetadata.clear();
        xml[0] = 0;
        assertEquals(COMPENSATION, model.workerCompensations());
        assertArrayEquals(new byte[] {1}, model.xml());

        var legacyModels = assertDoesNotThrow(() -> List.of(
                new ValidatedProcessModel(BPMN, "legacy", xml, Map.of(), Map.of(), List.of()),
                new ValidatedProcessModel(BPMN, "legacy", xml, Map.of(), Map.of(), List.of(),
                        Map.of(), List.of(), Map.of(), Map.of()),
                new ValidatedProcessModel(BPMN, "legacy", xml, Map.of(), Map.of(), List.of(),
                        Map.of(), List.of(), Map.of(), Map.of(), Map.of()),
                new ValidatedProcessModel(BPMN, "legacy", xml, Map.of(), Map.of(), List.of(),
                        Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of()),
                new ValidatedProcessModel(BPMN, "legacy", xml, Map.of(), Map.of(), List.of(),
                        Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()),
                new ValidatedProcessModel(BPMN, "legacy", xml, Map.of(), Map.of(), List.of(),
                        Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()),
                new ValidatedProcessModel(BPMN, "legacy", xml, Map.of(), Map.of(), List.of(),
                        Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of())));
        assertNotNull(legacyModels);
        for (var legacy : legacyModels) {
            assertNotNull(legacy);
            assertTrue(legacy.workerCompensations().isEmpty());
        }
    }

    @Test
    void existing_released_models_have_empty_compensation_metadata() throws IOException {
        var fixtures = Map.of(
                "baseline.bpmn20.xml", "baseline",
                "worker-mapping.bpmn20.xml", "workerMapping",
                "worker-business-error.bpmn20.xml", "workerBusinessError",
                "governed-human-task-timer.bpmn20.xml", "timedHumanReview",
                "agent-dispatch-wait-v1.bpmn20.xml", "agentDispatch",
                "formal-case-linked.bpmn20.xml", "linkedProcess");
        for (var fixture : fixtures.entrySet()) {
            byte[] xml = fixture(fixture.getKey());
            var model = assertDoesNotThrow(() -> validator.validateBpmn(xml, fixture.getValue()), fixture.getKey());
            assertNotNull(model, fixture.getKey());
            assertTrue(model.workerCompensations().isEmpty(), fixture.getKey());
        }
        var dmn = assertDoesNotThrow(() -> validator.validateDmn(fixture("baseline.dmn"), "baselineDecision"));
        assertNotNull(dmn);
        assertTrue(dmn.workerCompensations().isEmpty());

        var caseModel = assertDoesNotThrow(() -> validator.validateCmmn(fixture("governed-case.cmmn"), "governedCase"));
        assertNotNull(caseModel);
        assertEquals("governedCase", caseModel.definitionKey());
    }

    @Test
    void closed_profile_rejects_partial_extra_or_ambiguous_compensation_syntax() throws IOException {
        String xml = new String(fixture(FIXTURE), StandardCharsets.UTF_8);
        var invalidModels = new LinkedHashMap<String, String>();
        invalidModels.put("no-boundary", xml.replaceAll("(?s)<boundaryEvent id=\"reserveCompBoundary\".*?</boundaryEvent>", ""));
        invalidModels.put("no-association", xml.replaceAll("(?s)<association id=\"reserveCompAssociation\".*?/>", ""));
        invalidModels.put("no-handler", xml.replaceAll("(?s)<serviceTask id=\"undoReserve\".*?</serviceTask>", ""));
        invalidModels.put("no-throw", xml.replaceAll("(?s)<intermediateThrowEvent id=\"throwUndo\".*?</intermediateThrowEvent>",
                "<userTask id=\"throwUndo\"/>"));
        invalidModels.put("extra-boundary", insertBeforeAssociation(xml, """
                <boundaryEvent id="anotherComp" attachedToRef="downstream" cancelActivity="false">
                    <compensateEventDefinition/>
                </boundaryEvent>
                """));
        invalidModels.put("extra-association", insertBeforeAssociation(xml, """
                <association id="anotherAssociation" sourceRef="reserveCompBoundary"
                             targetRef="undoReserve" associationDirection="One"/>
                """));
        invalidModels.put("extra-handler", insertBeforeAssociation(xml, """
                <serviceTask id="anotherHandler" isForCompensation="true" flowable:type="external-worker"
                             flowable:topic="undoReservation:1" flowable:doNotIncludeVariables="true"
                             tianji:topic="undoReservation" tianji:schemaVersion="1" tianji:effectSafety="TRANSACTIONAL_RECEIPT">
                    <extensionElements>
                        <flowable:externalWorkerInParameter source="operationId" target="operationId" tianji:type="STRING"/>
                    </extensionElements>
                </serviceTask>
                """));
        invalidModels.put("extra-throw", xml.replace("<endEvent id=\"compEnd\"/>", """
                <intermediateThrowEvent id="compEnd">
                    <compensateEventDefinition activityRef="reserve" waitForCompletion="true"/>
                </intermediateThrowEvent>
                <sequenceFlow id="secondThrowEnd" sourceRef="compEnd" targetRef="finalEnd"/>
                <endEvent id="finalEnd"/>
                """));
        invalidModels.put("duplicate-global-id", xml.replace("id=\"reserveCompAssociation\"", "id=\"reserveNormal\""));
        invalidModels.put("unknown-attribute", xml.replace("associationDirection=\"One\"", "associationDirection=\"One\" name=\"unused\""));
        invalidModels.put("unknown-throw-definition", xml.replace(
                "<compensateEventDefinition activityRef=\"reserve\" waitForCompletion=\"true\"/>", "<signalEventDefinition/>"));
        invalidModels.put("extra-throw-definition", xml.replace("waitForCompletion=\"true\"/>",
                "waitForCompletion=\"true\"/><compensateEventDefinition activityRef=\"reserve\" waitForCompletion=\"true\"/>"));
        invalidModels.put("nested-compensation", xml.replace("<startEvent id=\"start\"/>",
                "<subProcess id=\"nested\"><startEvent id=\"start\"/></subProcess>"));
        assertInvalidModels(xml, invalidModels);
    }

    @Test
    void closed_profile_requires_literal_flags_detached_handler_and_dedicated_end() throws IOException {
        String xml = new String(fixture(FIXTURE), StandardCharsets.UTF_8);
        var invalidModels = new LinkedHashMap<String, String>();
        invalidModels.put("implicit-cancel-activity", xml.replace(" cancelActivity=\"false\"", ""));
        invalidModels.put("interrupting-compensation", xml.replace("cancelActivity=\"false\"", "cancelActivity=\"true\""));
        invalidModels.put("compensation-boundary-ref", xml.replace("<compensateEventDefinition/>",
                "<compensateEventDefinition activityRef=\"reserve\"/>"));
        invalidModels.put("missing-original", xml.replace("attachedToRef=\"reserve\"", "attachedToRef=\"missing\""));
        invalidModels.put("non-worker-attachment", xml.replace("attachedToRef=\"reserve\"", "attachedToRef=\"start\""));
        invalidModels.put("incoming-boundary-edge", insertBeforeAssociation(xml,
                "<sequenceFlow id=\"enterBoundary\" sourceRef=\"start\" targetRef=\"reserveCompBoundary\"/>"));
        invalidModels.put("outgoing-boundary-edge", insertBeforeAssociation(xml,
                "<sequenceFlow id=\"leaveBoundary\" sourceRef=\"reserveCompBoundary\" targetRef=\"normalEnd\"/>"));
        invalidModels.put("missing-direction", xml.replace(" associationDirection=\"One\"", ""));
        invalidModels.put("bidirectional-association", xml.replace("associationDirection=\"One\"", "associationDirection=\"Both\""));
        invalidModels.put("wrong-association-source", xml.replace("sourceRef=\"reserveCompBoundary\"", "sourceRef=\"downstreamRejected\""));
        invalidModels.put("unknown-association-target", xml.replace("targetRef=\"undoReserve\"", "targetRef=\"missing\""));
        invalidModels.put("self-association", xml.replace("targetRef=\"undoReserve\"", "targetRef=\"reserve\""));
        invalidModels.put("missing-handler-flag", xml.replace(" isForCompensation=\"true\"", ""));
        invalidModels.put("false-handler-flag", xml.replace("isForCompensation=\"true\"", "isForCompensation=\"false\""));
        invalidModels.put("marked-original", xml.replace("<serviceTask id=\"reserve\"", "<serviceTask id=\"reserve\" isForCompensation=\"true\""));
        invalidModels.put("handler-in-normal-graph", insertBeforeAssociation(xml.replace("targetRef=\"downstream\"", "targetRef=\"undoReserve\""),
                "<sequenceFlow id=\"afterHandler\" sourceRef=\"undoReserve\" targetRef=\"downstream\"/>"));
        invalidModels.put("handler-error-attachment", xml.replace("attachedToRef=\"downstream\"", "attachedToRef=\"undoReserve\""));
        invalidModels.put("broadcast-throw", xml.replace(" activityRef=\"reserve\"", ""));
        invalidModels.put("wrong-throw-ref", xml.replace("activityRef=\"reserve\"", "activityRef=\"downstream\""));
        invalidModels.put("unknown-throw-ref", xml.replace("activityRef=\"reserve\"", "activityRef=\"missing\""));
        invalidModels.put("missing-wait", xml.replace(" waitForCompletion=\"true\"", ""));
        invalidModels.put("asynchronous-throw", xml.replace("waitForCompletion=\"true\"", "waitForCompletion=\"false\""));
        invalidModels.put("second-throw-incoming", xml.replace("targetRef=\"normalEnd\"", "targetRef=\"throwUndo\"")
                .replace("<endEvent id=\"normalEnd\"/>", ""));
        invalidModels.put("second-throw-outgoing", insertBeforeAssociation(xml,
                "<sequenceFlow id=\"secondThrowOut\" sourceRef=\"throwUndo\" targetRef=\"normalEnd\"/>"));
        invalidModels.put("missing-throw-outgoing", xml.replaceAll("<sequenceFlow id=\"toCompEnd\"[^>]*/>", ""));
        invalidModels.put("shared-comp-end", xml.replace("targetRef=\"normalEnd\"", "targetRef=\"compEnd\"")
                .replace("<endEvent id=\"normalEnd\"/>", ""));
        invalidModels.put("non-end-throw-target", insertBeforeAssociation(xml.replace("<endEvent id=\"compEnd\"/>", "<userTask id=\"compEnd\"/>"),
                "<sequenceFlow id=\"afterThrowTarget\" sourceRef=\"compEnd\" targetRef=\"normalEnd\"/>"));
        assertInvalidModels(xml, invalidModels);
    }

    @Test
    void compensation_original_cannot_be_human_dmn_case_or_message_wait() throws IOException {
        String xml = new String(fixture(FIXTURE), StandardCharsets.UTF_8);
        String original = "(?s)<serviceTask id=\"reserve\".*?</serviceTask>";
        String human = xml.replaceAll(original, "<userTask id=\"reserve\"/>");
        String decision = xml.replaceAll(original, """
                <serviceTask id="reserve" flowable:type="dmn">
                    <extensionElements>
                        <flowable:field name="decisionTableReferenceKey"><flowable:string>routing</flowable:string></flowable:field>
                    </extensionElements>
                </serviceTask>
                """);
        String waiting = withMessageWait(xml);
        String linkedCase = waiting.replaceAll(original,
                        "<serviceTask id=\"reserve\" flowable:type=\"case\" flowable:caseDefinitionKey=\"governedCase\"/>")
                .replace(" tianji:workerRef=\"reserve\"", "")
                .replace("sourceRef=\"start\" targetRef=\"reserve\"", "sourceRef=\"start\" targetRef=\"beforeCase\"");
        linkedCase = insertBeforeAssociation(linkedCase, """
                <intermediateCatchEvent id="beforeCase">
                    <timerEventDefinition><timeDuration>PT1S</timeDuration></timerEventDefinition>
                </intermediateCatchEvent>
                <sequenceFlow id="enterCase" sourceRef="beforeCase" targetRef="reserve"/>
                """);
        var invalidModels = Map.of("human", human, "decision", decision, "case-source", linkedCase,
                "message-wait", waiting);
        for (var entry : invalidModels.entrySet()) {
            assertDoesNotThrow(() -> validate(withoutCompensation(entry.getValue())), entry.getKey());
        }
        assertInvalidModels(xml, invalidModels);
    }

    @Test
    void compensation_pair_accepts_only_initial_operation_id_input_and_no_output() throws IOException {
        String xml = new String(fixture(FIXTURE), StandardCharsets.UTF_8);
        var invalidModels = new LinkedHashMap<String, String>();
        String mapping = "<flowable:externalWorkerInParameter source=\"operationId\" target=\"operationId\" tianji:type=\"STRING\"/>";
        String extraInput = "<flowable:externalWorkerInParameter source=\"otherInput\" target=\"otherInput\" tianji:type=\"STRING\"/>";
        String output = "<flowable:externalWorkerOutParameter source=\"receipt\" target=\"receipt\" tianji:type=\"STRING\"/>";
        for (String worker : List.of("reserve", "undoReserve")) {
            int start = xml.indexOf("<serviceTask id=\"" + worker + "\"");
            int end = xml.indexOf("</serviceTask>", start);
            String task = xml.substring(start, end);
            invalidModels.put(worker + "-missing-input", xml.replace(task, task.replace(mapping, "")));
            invalidModels.put(worker + "-extra-input", xml.replace(task, task.replace(mapping, mapping + extraInput)));
            invalidModels.put(worker + "-wrong-source", xml.replace(task, task.replace("source=\"operationId\"", "source=\"receipt\"")));
            invalidModels.put(worker + "-wrong-target", xml.replace(task, task.replace("target=\"operationId\"", "target=\"receipt\"")));
            invalidModels.put(worker + "-wrong-type", xml.replace(task, task.replace("tianji:type=\"STRING\"", "tianji:type=\"INTEGER\"")));
            invalidModels.put(worker + "-output", xml.replace(task, task.replace(mapping, mapping + output)));
        }
        assertInvalidModels(xml, invalidModels);
    }

    @Test
    void original_error_cannot_establish_success_but_downstream_error_inherits_it() throws IOException {
        String xml = new String(fixture(FIXTURE), StandardCharsets.UTF_8);
        var downstream = assertDoesNotThrow(() -> validate(xml));
        assertNotNull(downstream);
        assertEquals(COMPENSATION, downstream.workerCompensations());

        String selfError = xml.replace("attachedToRef=\"downstream\"", "attachedToRef=\"reserve\"");
        assertNotEquals(xml, selfError);
        assertDoesNotThrow(() -> SafeModelXml.parse(bytes(selfError), true));
        var sameGraph = assertDoesNotThrow(() -> validate(withoutCompensation(selfError)));
        assertNotNull(sameGraph);
        assertEquals("throwUndo", sameGraph.workerBusinessErrors().get("reserve").targetElementId());
        assertInvalid(selfError, "original Error before reserveNormal");
    }

    @Test
    void timer_and_error_merge_requires_original_success_on_every_incoming_path() throws IOException {
        String xml = new String(fixture(FIXTURE), StandardCharsets.UTF_8);
        String afterReserve = withTimerMerge(xml);
        var control = assertDoesNotThrow(() -> validate(afterReserve));
        assertNotNull(control);
        assertEquals(COMPENSATION, control.workerCompensations());
        assertEquals(Map.of("reviewTimeout", new ValidatedProcessModel.HumanTaskTimer("review", Duration.ofMinutes(5))),
                control.humanTaskTimers());
        assertEquals("failureMerge", control.workerBusinessErrors().get("downstream").targetElementId());

        String bypass = afterReserve
                .replace("sourceRef=\"start\" targetRef=\"reserve\"", "sourceRef=\"start\" targetRef=\"review\"")
                .replace("sourceRef=\"reserve\" targetRef=\"review\"", "sourceRef=\"reserve\" targetRef=\"downstream\"")
                .replace("sourceRef=\"review\" targetRef=\"downstream\"", "sourceRef=\"review\" targetRef=\"reserve\"");
        assertNotEquals(afterReserve, bypass);
        assertDoesNotThrow(() -> SafeModelXml.parse(bytes(bypass), true));
        var sameGraph = assertDoesNotThrow(() -> validate(withoutCompensation(bypass)));
        assertNotNull(sameGraph);
        assertEquals(control.humanTaskTimers(), sameGraph.humanTaskTimers());
        assertEquals(control.workerBusinessErrors(), sameGraph.workerBusinessErrors());
        assertInvalid(bypass, "Timer path reaches the merge before reserveNormal");
    }

    @Test
    void compensation_preserves_unrelated_node_cycle_and_traversal_limits() throws IOException {
        String xml = new String(fixture(FIXTURE), StandardCharsets.UTF_8);
        assertInvalid(insertBeforeAssociation(xml, """
                <userTask id="detachedTask"/>
                <sequenceFlow id="detachedLoop" sourceRef="detachedTask" targetRef="detachedTask"/>
                """), "disconnected cycle with legal arity");

        String beforeDownstream = "sourceRef=\"reserve\" targetRef=\"downstream\"";
        assertDoesNotThrow(() -> validate(insertTasks(xml, beforeDownstream, "downstream", 119)));
        assertInvalid(insertTasks(xml, beforeDownstream, "downstream", 120), "129 parsed nodes before detaching compensation");

        String threePaths = withTimerMerge(xml).replace("targetRef=\"normalEnd\"", "targetRef=\"failureMerge\"")
                .replace("<endEvent id=\"normalEnd\"/>", "");
        String beforeThrow = "sourceRef=\"failureMerge\" targetRef=\"throwUndo\"";
        assertDoesNotThrow(() -> validate(insertTasks(threePaths, beforeThrow, "throwUndo", 80)));
        assertInvalid(insertTasks(threePaths, beforeThrow, "throwUndo", 81), "257 path traversals");
    }

    private static String withTimerMerge(String xml) {
        return insertBeforeAssociation(xml.replace("xmlns:tianji=", "xmlns:task=\"urn:stellar-enclosure:tianji:task:1\" xmlns:tianji=")
                        .replace("sourceRef=\"reserve\" targetRef=\"downstream\"", "sourceRef=\"reserve\" targetRef=\"review\"")
                        .replace("sourceRef=\"downstreamRejected\" targetRef=\"throwUndo\"",
                                "sourceRef=\"downstreamRejected\" targetRef=\"failureMerge\""),
                """
                <userTask id="review" task:kind="APPROVAL" task:initialLane="finance" task:approvalVariable="reviewApproved">
                    <extensionElements>
                        <task:taskLane key="finance" roleIds="50,51"/>
                        <task:taskField name="approved" target="reviewApproved" type="BOOLEAN"/>
                    </extensionElements>
                </userTask>
                <boundaryEvent id="reviewTimeout" attachedToRef="review" cancelActivity="true">
                    <timerEventDefinition><timeDuration>PT5M</timeDuration></timerEventDefinition>
                </boundaryEvent>
                <sequenceFlow id="afterReview" sourceRef="review" targetRef="downstream"/>
                <sequenceFlow id="reviewTimedOut" sourceRef="reviewTimeout" targetRef="failureMerge"/>
                <exclusiveGateway id="failureMerge"/>
                <sequenceFlow id="afterMerge" sourceRef="failureMerge" targetRef="throwUndo"/>
                """);
    }

    private static String withMessageWait(String xml) {
        return insertBeforeAssociation(xml.replace("<process id=\"workerCompensation\"",
                        "<message id=\"resultMessage\" name=\"TianjiResultV1\"/>\n    <process id=\"workerCompensation\"")
                        .replace("sourceRef=\"reserve\" targetRef=\"downstream\"", "sourceRef=\"reserve\" targetRef=\"resultGateway\""),
                """
                <eventBasedGateway id="resultGateway"/>
                <sequenceFlow id="toResult" sourceRef="resultGateway" targetRef="waitResult"/>
                <sequenceFlow id="toTimeout" sourceRef="resultGateway" targetRef="waitTimeout"/>
                <intermediateCatchEvent id="waitResult" tianji:workerRef="reserve">
                    <messageEventDefinition messageRef="resultMessage"/>
                </intermediateCatchEvent>
                <intermediateCatchEvent id="waitTimeout">
                    <timerEventDefinition><timeDuration>PT5M</timeDuration></timerEventDefinition>
                </intermediateCatchEvent>
                <sequenceFlow id="mergeResult" sourceRef="waitResult" targetRef="resultMerge"/>
                <sequenceFlow id="mergeTimeout" sourceRef="waitTimeout" targetRef="resultMerge"/>
                <exclusiveGateway id="resultMerge"/>
                <sequenceFlow id="afterWait" sourceRef="resultMerge" targetRef="downstream"/>
                """);
    }

    private static String withoutCompensation(String xml) {
        // Replace only the compensation syntax so the legacy validator checks the same ordinary graph.
        return xml.replaceAll("(?s)<boundaryEvent id=\"reserveCompBoundary\".*?</boundaryEvent>", "")
                .replaceAll("(?s)<serviceTask id=\"undoReserve\".*?</serviceTask>", "")
                .replaceAll("(?s)<association id=\"reserveCompAssociation\".*?/>", "")
                .replaceAll("(?s)<intermediateThrowEvent id=\"throwUndo\".*?</intermediateThrowEvent>",
                        "<userTask id=\"throwUndo\"/>");
    }

    private static String insertTasks(String xml, String edge, String target, int count) {
        var tasks = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String next = i + 1 == count ? target : "extraTask" + (i + 1);
            tasks.append("""
                    <userTask id="extraTask%d"/>
                    <sequenceFlow id="afterExtraTask%d" sourceRef="extraTask%d" targetRef="%s"/>
                    """.formatted(i, i, i, next));
        }
        return insertBeforeAssociation(xml.replace(edge, edge.replace("targetRef=\"" + target + "\"", "targetRef=\"extraTask0\"")),
                tasks.toString());
    }

    private void assertInvalidModels(String xml, Map<String, String> invalidModels) {
        for (var invalid : invalidModels.entrySet()) {
            assertNotEquals(xml, invalid.getValue(), invalid.getKey());
            assertInvalid(invalid.getValue(), invalid.getKey());
        }
    }

    private void assertInvalid(String xml, String caseName) {
        var failure = assertThrows(IllegalArgumentException.class, () -> validate(xml), caseName);
        assertEquals(INVALID_MODEL, failure.getMessage(), caseName);
    }

    private ValidatedProcessModel validate(String xml) {
        return validator.validateBpmn(bytes(xml), KEY);
    }

    private static String insertBeforeAssociation(String xml, String body) {
        String association = "<association id=\"reserveCompAssociation\"";
        return xml.replace(association, body + association);
    }

    private static byte[] bytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] fixture(String name) throws IOException {
        try (var input = WorkerCompensationModelTest.class.getResourceAsStream("/fixtures/" + name)) {
            assertNotNull(input, name);
            return input.readAllBytes();
        }
    }
}
