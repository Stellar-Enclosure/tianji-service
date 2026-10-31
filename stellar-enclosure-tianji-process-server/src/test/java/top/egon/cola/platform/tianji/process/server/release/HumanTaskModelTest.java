package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.BOOLEAN;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.INTEGER;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.STRING;

class HumanTaskModelTest {
    private final ProcessModelValidator validator = new ProcessModelValidator();

    @Test void pinned_role_lanes_form_targets_and_approval_route_are_bounded_and_deeply_immutable() throws Exception {
        byte[] xml = fixture(); var model = validator.validateBpmn(xml, "humanReview"); var task = model.humanTasks().get("review");
        assertEquals(ValidatedProcessModel.HumanTaskKind.APPROVAL, task.kind()); assertEquals("finance", task.initialLane()); assertNull(task.laneVariable());
        assertEquals("reviewApproved", task.approvalVariable()); assertEquals(Map.of("finance", Set.of("50", "51"), "manager", Set.of("60")), task.lanes());
        assertEquals(List.of(new ValidatedProcessModel.HumanTaskField("approved", "reviewApproved", BOOLEAN), new ValidatedProcessModel.HumanTaskField("comment", "reviewComment", STRING)), task.fields());
        validator.validateHumanTaskBindings(model, Map.of("amount", INTEGER), null);
        assertThrows(UnsupportedOperationException.class, () -> model.humanTasks().clear()); assertThrows(UnsupportedOperationException.class, () -> task.lanes().clear());
        assertThrows(UnsupportedOperationException.class, () -> task.lanes().get("finance").clear()); assertThrows(UnsupportedOperationException.class, () -> task.fields().clear());
        var nativeModel = new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(xml), true, true);
        var nativeTask = assertInstanceOf(UserTask.class, nativeModel.getMainProcess().getFlowElement("review"));
        assertEquals("APPROVAL", nativeTask.getAttributeValue("urn:stellar-enclosure:tianji:task:1", "kind"));
        assertEquals(2, nativeTask.getExtensionElements().get("taskLane").size());
    }

    @Test void incomplete_ambiguous_untrusted_or_executable_assignment_claims_are_rejected() throws Exception {
        String xml = new String(fixture(), StandardCharsets.UTF_8);
        for (var invalid : List.of(xml.replace("task:kind=\"APPROVAL\"", ""), xml.replace("task:kind=\"APPROVAL\"", "task:kind=\"ACTION\""),
                xml.replace("task:initialLane=\"finance\"", "task:initialLane=\"missing\""), xml.replace("task:initialLane=\"finance\"", "task:initialLane=\"finance\" task:laneVariable=\"queue\""),
                xml.replace("roleIds=\"50,51\"", "roleIds=\"50,50\""), xml.replace("roleIds=\"50,51\"", "roleIds=\"50, 51\""), xml.replace("roleIds=\"50,51\"", "roleIds=\"0\""),
                xml.replace("roleIds=\"60\"", "roleIds=\"51,50\""), xml.replace("roleIds=\"60\"", "roleIds=\"9223372036854775808\""),
                xml.replace("type=\"BOOLEAN\"", "type=\"STRING\""), xml.replace("target=\"reviewComment\"", "target=\"reviewApproved\""),
                xml.replace("target=\"reviewComment\"", "target=\"__tianjiAuthority\""), xml.replace("name=\"comment\"", "name=\"approved\""),
                xml.replace("task:approvalVariable=\"reviewApproved\"", "task:approvalVariable=\"reviewComment\""),
                xml.replace("task:initialLane=\"finance\"", "task:initialLane=\"${queue}\""),
                xml.replace("reviewApproved == true", "otherApproval == true"), xml.replace("reviewApproved == true", "reviewApproved == 'true'"),
                xml.replace("<task:taskLane", "<taskLane")
        )) assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(invalid), "humanReview"));
        var orphan = "<definitions xmlns='http://www.omg.org/spec/BPMN/20100524/MODEL' xmlns:task='urn:stellar-enclosure:tianji:task:1' targetNamespace='urn:test'><process id='orphan' isExecutable='true'><startEvent id='start'/><userTask id='review' task:initialLane='finance'/><endEvent id='end'/><sequenceFlow id='one' sourceRef='start' targetRef='review'/><sequenceFlow id='two' sourceRef='review' targetRef='end'/></process></definitions>";
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(orphan), "orphan"));
    }

    @Test void start_inputs_or_another_published_writer_cannot_preseed_a_form_target() throws Exception {
        var model = validator.validateBpmn(fixture(), "humanReview");
        assertThrows(IllegalArgumentException.class, () -> validator.validateHumanTaskBindings(model, Map.of("reviewApproved", BOOLEAN), null));
        assertThrows(IllegalArgumentException.class, () -> validator.validateHumanTaskBindings(model, Map.of("reviewComment", STRING), null));
        var decision = validator.validateDmn(bytes(dmn("'finance'", "reviewApproved")), "routing");
        assertThrows(IllegalArgumentException.class, () -> validator.validateHumanTaskBindings(model, Map.of("amount", INTEGER), decision));
    }

    @Test void ordinary_actions_have_no_approval_claim_and_role_lane_and_form_limits_are_finite() throws Exception {
        String xml = new String(fixture(), StandardCharsets.UTF_8);
        var action = validator.validateBpmn(bytes(xml.replace("task:kind=\"APPROVAL\"", "task:kind=\"ACTION\"").replace(" task:approvalVariable=\"reviewApproved\"", "")), "humanReview");
        assertEquals(ValidatedProcessModel.HumanTaskKind.ACTION, action.humanTasks().get("review").kind()); assertNull(action.humanTasks().get("review").approvalVariable());
        String tooManyRoles = java.util.stream.IntStream.rangeClosed(1, 17).mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining(","));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace("roleIds=\"50,51\"", "roleIds=\"" + tooManyRoles + "\"")), "humanReview"));
        String lanes = java.util.stream.IntStream.range(1, 8).mapToObj(index -> "<task:taskLane key='lane" + index + "' roleIds='" + (100 + index) + "'/>").collect(java.util.stream.Collectors.joining());
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace("</extensionElements>", lanes + "</extensionElements>")), "humanReview"));
        String fields = java.util.stream.IntStream.range(1, 16).mapToObj(index -> "<task:taskField name='field" + index + "' target='value" + index + "' type='STRING'/>").collect(java.util.stream.Collectors.joining());
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace("</extensionElements>", fields + "</extensionElements>")), "humanReview"));
    }

    @Test void dmn_selects_only_pinned_lane_keys_and_cannot_replace_an_approval_output() throws Exception {
        String xml = new String(fixture(), StandardCharsets.UTF_8).replace("task:initialLane=\"finance\"", "task:laneVariable=\"queue\"")
                .replace("<startEvent id=\"start\"/>", "<startEvent id=\"start\"/><serviceTask id=\"decision\" xmlns:flowable=\"http://flowable.org/bpmn\" flowable:type=\"dmn\"><extensionElements><flowable:field name=\"decisionTableReferenceKey\"><flowable:string>routing</flowable:string></flowable:field></extensionElements></serviceTask>")
                .replace("sourceRef=\"start\" targetRef=\"review\"", "sourceRef=\"start\" targetRef=\"decision\"")
                .replace("<sequenceFlow id=\"reviewRoute\"", "<sequenceFlow id=\"decisionReview\" sourceRef=\"decision\" targetRef=\"review\"/><sequenceFlow id=\"reviewRoute\"");
        var model = validator.validateBpmn(bytes(xml), "humanReview"); var decision = validator.validateDmn(bytes(dmn("'finance'", "queue")), "routing");
        validator.validateDecisionBinding(model, Map.of("amount", INTEGER), decision); validator.validateHumanTaskBindings(model, Map.of("amount", INTEGER), decision);
        assertThrows(IllegalArgumentException.class, () -> validator.validateHumanTaskBindings(model, Map.of("amount", INTEGER), validator.validateDmn(bytes(dmn("'outside'", "queue")), "routing")));
        assertThrows(IllegalArgumentException.class, () -> validator.validateHumanTaskBindings(model, Map.of("amount", INTEGER), null));
        assertThrows(IllegalArgumentException.class, () -> validator.validateHumanTaskBindings(model, Map.of("amount", INTEGER, "queue", STRING), decision));
        String reversed = xml.replace("sourceRef=\"start\" targetRef=\"decision\"", "sourceRef=\"start\" targetRef=\"review\"")
                .replace("sourceRef=\"decision\" targetRef=\"review\"", "sourceRef=\"decision\" targetRef=\"route\"").replace("sourceRef=\"review\" targetRef=\"route\"", "sourceRef=\"review\" targetRef=\"decision\"")
                .replace("reviewApproved == true", "queue == 'finance'");
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(reversed), "humanReview"));
    }

    private static byte[] fixture() throws Exception { try (var input = HumanTaskModelTest.class.getResourceAsStream("/fixtures/governed-human-task.bpmn20.xml")) { assertNotNull(input); return input.readAllBytes(); } }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String dmn(String value, String output) { return "<definitions xmlns='http://www.omg.org/spec/DMN/20151101' id='definitions' name='routing' namespace='urn:test'><decision id='routing' name='routing'><decisionTable hitPolicy='UNIQUE'><input><inputExpression typeRef='number'><text>amount</text></inputExpression></input><output name='" + output + "' typeRef='string'/><rule><inputEntry><text>&gt;= 0</text></inputEntry><outputEntry><text>" + value + "</text></outputEntry></rule></decisionTable></decision></definitions>"; }
}
