package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.EventGateway;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.MessageEventDefinition;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.INTEGER;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.STRING;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.BOOLEAN;

class ProcessModelValidatorTest {
    private final ProcessModelValidator validator = new ProcessModelValidator();

    @Test
    void public_cmmn_validation_returns_the_existing_closed_immutable_metadata() throws Exception {
        var model = validator.validateCmmn(fixture("governed-case.cmmn"), "governedCase");
        assertNotNull(model);
        assertEquals("governedCase", model.definitionKey());
        assertEquals(java.util.Set.of("primary", "supplemental", "milestone"), model.planItems().keySet());
        assertEquals(java.util.Set.of("7", "8", "9"), model.operatorRoleIds());
        assertThrows(IllegalArgumentException.class, () -> validator.validateCmmn(fixture("formal-case.cmmn"), "formalCase"));
    }

    @Test
    void previously_persisted_wait_models_without_status_projection_remain_readable() throws Exception {
        var previous = validator.validateBpmn(fixture("agent-dispatch-wait-v1.bpmn20.xml"), "agentDispatch");
        assertNull(previous.messageWaits().get("waitResult").statusVariable());
        assertTrue(previous.routeConditions().isEmpty());
        validator.validateWorkerBindings(previous, Map.of("amount", INTEGER), null);
    }

    @Test
    void fixed_flowable_converter_retains_the_literal_subscription_and_competing_timer() throws Exception {
        byte[] xml = fixture("agent-dispatch-wait.bpmn20.xml");
        var nativeModel = new BpmnXMLConverter().convertToBpmnModel(
                () -> new ByteArrayInputStream(xml), true, true);
        var process = nativeModel.getMainProcess();
        assertInstanceOf(EventGateway.class, process.getFlowElement("resultGateway"));
        var message = assertInstanceOf(IntermediateCatchEvent.class, process.getFlowElement("waitResult"));
        var definition = assertInstanceOf(MessageEventDefinition.class, message.getEventDefinitions().getFirst());
        assertEquals("TianjiAgentFinishedV1", nativeModel.getMessage(definition.getMessageRef()).getName());
        assertNull(definition.getMessageExpression());
        var timer = assertInstanceOf(IntermediateCatchEvent.class, process.getFlowElement("waitTimeout"));
        var timerDefinition = assertInstanceOf(TimerEventDefinition.class, timer.getEventDefinitions().getFirst());
        assertEquals("PT5M", timerDefinition.getTimeDuration());
    }

    @Test
    void message_waits_bind_one_worker_to_a_literal_message_and_a_competing_bounded_timer() throws Exception {
        String xml = new String(fixture("agent-dispatch-wait.bpmn20.xml"), StandardCharsets.UTF_8);
        var model = validator.validateBpmn(bytes(xml), "agentDispatch");
        assertEquals(Map.of("waitResult", new ValidatedProcessModel.MessageWait("TianjiAgentFinishedV1", "worker", "waitTimeout",
                Duration.ofMinutes(5), "agentStatus")), model.messageWaits());
        validator.validateWorkerBindings(model, Map.of("amount", INTEGER), null);
        assertThrows(IllegalArgumentException.class, () -> validator.validateWorkerBindings(model, Map.of("amount", INTEGER, "agentStatus", STRING), null));
        var overwriting = validator.validateBpmn(bytes(xml.replace("statusVariable=\"agentStatus\"", "statusVariable=\"agentRunId\"")
                .replace("agentStatus ==", "agentRunId ==")), "agentDispatch");
        assertThrows(IllegalArgumentException.class, () -> validator.validateWorkerBindings(overwriting, Map.of("amount", INTEGER), null));
        assertEquals(java.util.List.of(new ValidatedProcessModel.RouteCondition("agentStatus", STRING, "'COMPLETED'")), model.routeConditions());
        assertThrows(UnsupportedOperationException.class, () -> model.messageWaits().clear());
        for (String unsafe : new String[] {
                xml.replace("name=\"TianjiAgentFinishedV1\"", "name=\"${execution.tenantId}\""),
                xml.replace("messageRef=\"agentFinished\"", "messageRef=\"other:agentFinished\""),
                xml.replace("messageRef=\"agentFinished\"", "messageRef=\"missing\""),
                xml.replace("tianji:workerRef=\"worker\"", "tianji:workerRef=\"review\""),
                xml.replace("tianji:workerRef=\"worker\"", ""),
                xml.replace("tianji:statusVariable=\"agentStatus\"", "tianji:statusVariable=\"execution\""),
                xml.replace("tianji:statusVariable=\"agentStatus\"", ""),
                xml.replace("agentStatus == 'COMPLETED'", "agentStatus == 'QUEUED'"),
                xml.replace("agentStatus == 'COMPLETED'", "amount == 10"),
                xml.replace("agentStatus == 'COMPLETED'", "agentStatus.class == 'COMPLETED'"),
                xml.replace("<eventBasedGateway id=\"resultGateway\"/>", "<parallelGateway id=\"resultGateway\"/>"),
                xml.replace("targetRef=\"worker\"", "targetRef=\"resultGateway\""),
                xml.replace("<timerEventDefinition><timeDuration>PT5M</timeDuration></timerEventDefinition>",
                        "<messageEventDefinition messageRef=\"agentFinished\"/>"),
                xml.replace("<messageEventDefinition messageRef=\"agentFinished\"/>",
                        "<messageEventDefinition messageRef=\"agentFinished\" flowable:messageExpression=\"evil\"/>"),
                xml.replace("<message id=\"agentFinished\"", "<message id=\"unused\" name=\"Unused\"/><message id=\"agentFinished\""),
                xml.replace("PT5M", "PT0.01S"), xml.replace("PT5M", "${timeout}"),
                xml.replace("<messageEventDefinition messageRef=\"agentFinished\"/>",
                        "<messageEventDefinition messageRef=\"agentFinished\"/><timerEventDefinition><timeDuration>PT5M</timeDuration></timerEventDefinition>")
        }) assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(unsafe), "agentDispatch"));
    }

    @Test
    void accepts_packaged_bpmn_and_dmn_and_retains_only_defensive_copies() throws Exception {
        byte[] bpmn = fixture("baseline.bpmn20.xml");
        byte[] expected = bpmn.clone();
        ValidatedProcessModel model = validator.validateBpmn(bpmn, "baseline");
        assertEquals(ValidatedProcessModel.Type.BPMN, model.type());
        assertEquals("baseline", model.definitionKey());
        bpmn[0] = 'x';
        byte[] returned = model.xml();
        returned[0] = 'y';
        assertArrayEquals(expected, model.xml());

        ValidatedProcessModel decision = validator.validateDmn(fixture("baseline.dmn"), "baselineDecision");
        assertEquals(ValidatedProcessModel.Type.DMN, decision.type());
        assertEquals(Map.of("amount", INTEGER), decision.inputs());
        assertEquals(Map.of("route", STRING), decision.outputs());
        assertEquals(java.util.List.of("row:1", "row:2"), decision.ruleIds());
        String identified = new String(fixture("baseline.dmn"), StandardCharsets.UTF_8).replaceFirst("<rule>", "<rule id='approved'>");
        assertEquals(java.util.List.of("approved", "row:2"), validator.validateDmn(bytes(identified), "baselineDecision").ruleIds());
        assertThrows(UnsupportedOperationException.class, () -> decision.inputs().put("other", STRING));
    }

    @Test
    void rejects_external_entities_encodings_unknown_schema_and_unbounded_input() throws Exception {
        String xml = new String(fixture("baseline.bpmn20.xml"), StandardCharsets.UTF_8);
        String xxe = xml.replace("<definitions", "<!DOCTYPE definitions [<!ENTITY steal SYSTEM 'file:///etc/passwd'>]><definitions")
                .replace("name=\"Baseline\"", "name=\"&steal;\"");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> validator.validateBpmn(bytes(xxe), "baseline"));
        assertFalse(failure.toString().contains("passwd"));
        assertNull(failure.getCause());
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(
                xml.replace("encoding=\"UTF-8\"", "encoding=\"ISO-8859-1\"")), "baseline"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(
                xml.replace("http://www.omg.org/spec/BPMN/20100524/MODEL", "https://wrong.example/model")), "baseline"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(new byte[SafeModelXml.MAX_BYTES + 1], "baseline"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(new byte[] {(byte) 0xc3, 0x28}, "baseline"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes("<a>".repeat(25) + "</a>".repeat(25)), "baseline"));
    }

    @Test
    void rejects_bpmn_scripts_class_loading_dynamic_names_and_unsafe_graphs() throws Exception {
        String xml = new String(fixture("baseline.bpmn20.xml"), StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace(
                "<userTask id=\"review\" name=\"Review\"/>", "<scriptTask id=\"review\"><script>1</script></scriptTask>")), "baseline"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace(
                "<userTask id=\"review\" name=\"Review\"/>", "<serviceTask id=\"review\" implementation=\"java.lang.Runtime\"/>")), "baseline"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace(
                "name=\"Review\"", "name=\"&#36;{modelProbe.touch()}\"")), "baseline"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace(
                "targetRef=\"end\"", "targetRef=\"review\"")), "baseline"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace(
                "targetRef=\"end\"", "targetRef=\"missing\"")), "baseline"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace(
                "id=\"toEnd\"", "id=\"toReview\"")), "baseline"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace("PT1S", "PT0.1S")), "baseline"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml), "another-key"));
    }

    @Test
    void accepts_a_bounded_parallel_graph_but_rejects_excessive_execution_paths() {
        assertEquals("parallel", validator.validateBpmn(branching(1), "parallel").definitionKey());
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(branching(9), "parallel"));
    }

    @Test
    void validates_release_bound_decision_and_rejects_unsafe_routing_expressions() throws Exception {
        String xml = new String(fixture("approval-routing.bpmn20.xml"), StandardCharsets.UTF_8);
        var process = validator.validateBpmn(bytes(xml), "approvalRouting");
        var decision = validator.validateDmn(fixture("baseline.dmn"), "baselineDecision");
        assertEquals(Map.of("decide", "baselineDecision"), process.decisionTasks());
        validator.validateDecisionBinding(process, Map.of("amount", INTEGER), decision);
        assertThrows(IllegalArgumentException.class, () -> validator.validateDecisionBinding(process, Map.of("amount", STRING), decision));
        assertThrows(IllegalArgumentException.class, () -> validator.validateDecisionBinding(process, Map.of("amount", INTEGER, "route", STRING), decision));
        var wrongRoute = validator.validateBpmn(bytes(xml.replace("route == 'review'", "amount == 10")), "approvalRouting");
        assertThrows(IllegalArgumentException.class, () -> validator.validateDecisionBinding(wrongRoute, Map.of("amount", INTEGER), decision));

        for (String unsafe : new String[] {"route.class == 'review'", "route == 'review' || true", "execution.getVariable('route') == 'review'",
                "route == 'review' ? true : false"}) {
            assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace("route == 'review'", unsafe)), "approvalRouting"));
        }
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace("default=\"toStandard\"", "default=\"missing\"")), "approvalRouting"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace("flowable:type=\"dmn\"", "flowable:type=\"http\"")), "approvalRouting"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace("decisionTableReferenceKey", "fallbackToDefaultTenant")), "approvalRouting"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace("baselineDecision", "${tenant}")), "approvalRouting"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(xml.replace("targetRef=\"decide\"", "targetRef=\"routing\"")), "approvalRouting"));
    }

    @Test
    void validates_explicit_worker_mappings_and_rejects_implicit_or_dynamic_access() throws Exception {
        String xml = new String(fixture("worker-mapping.bpmn20.xml"), StandardCharsets.UTF_8);
        var process = validator.validateBpmn(bytes(xml), "workerMapping");
        var worker = process.workerTasks().get("worker");
        assertEquals("riskCheck", worker.topic());
        assertEquals("1", worker.schemaVersion());
        assertEquals(ValidatedProcessModel.WorkerEffect.TRANSACTIONAL_RECEIPT, worker.effectSafety());
        assertEquals(java.util.List.of(new ValidatedProcessModel.WorkerParameter("amount", "requestedAmount", INTEGER)), worker.inputs());
        assertEquals(java.util.List.of(new ValidatedProcessModel.WorkerParameter("approved", "approved", BOOLEAN)), worker.outputs());
        validator.validateWorkerBindings(process, Map.of("amount", INTEGER), null);
        assertThrows(IllegalArgumentException.class, () -> validator.validateWorkerBindings(process, Map.of("amount", STRING), null));
        assertThrows(IllegalArgumentException.class, () -> validator.validateWorkerBindings(process, Map.of("amount", INTEGER, "approved", BOOLEAN), null));
        assertThrows(UnsupportedOperationException.class, () -> worker.inputs().clear());

        for (String invalid : new String[] {
                xml.replace("flowable:doNotIncludeVariables=\"true\"", ""),
                xml.replace("flowable:topic=\"riskCheck:1\"", "flowable:topic=\"${execution.tenantId}\""),
                xml.replace("flowable:topic=\"riskCheck:1\"", "flowable:topic=\"riskCheck:2\""),
                xml.replace("tianji:schemaVersion=\"1\"", ""),
                xml.replace("tianji:effectSafety=\"TRANSACTIONAL_RECEIPT\"", ""),
                xml.replace("source=\"amount\"", "sourceExpression=\"${amount}\""),
                xml.replace("target=\"requestedAmount\"", "target=\"execution\""),
                xml.replace("tianji:type=\"INTEGER\"", "tianji:type=\"OBJECT\""),
                xml.replace("tianji:type=\"BOOLEAN\"", ""),
                xml.replace("flowable:type=\"external-worker\"", "flowable:type=\"external\""),
                xml.replace("<flowable:externalWorkerOutParameter", "<flowable:field name=\"unknown\"/><flowable:externalWorkerOutParameter")
        }) {
            assertThrows(IllegalArgumentException.class, () -> validator.validateBpmn(bytes(invalid), "workerMapping"));
        }
    }

    @Test
    void rejects_dmn_expression_injection_and_rule_shape_mismatches() throws Exception {
        String xml = new String(fixture("baseline.dmn"), StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> validator.validateDmn(bytes(xml.replace(
                "<text>amount</text>", "<text>amount.class</text>")), "baselineDecision"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateDmn(bytes(xml.replace(
                "<text>'standard'</text>", "<text>#{modelProbe.touch()}</text>")), "baselineDecision"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateDmn(bytes(xml.replace(
                "&lt;= 10", "&lt;= 10 or modelProbe.touch()")), "baselineDecision"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateDmn(bytes(xml.replace(
                "<text>amount</text>", "<text>am<!-- fragment -->ount</text>")), "baselineDecision"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateDmn(bytes(xml.replace(
                "hitPolicy=\"FIRST\"", "hitPolicy=\"COLLECT\"")), "baselineDecision"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateDmn(bytes(xml.replace(
                "<outputEntry id=\"review\"><text>'review'</text></outputEntry>", "")), "baselineDecision"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateDmn(bytes(xml.replace(
                "typeRef=\"number\"", "typeRef=\"object\"")), "baselineDecision"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateDmn(bytes(xml.replace(
                "&lt;= 10", "&lt;= 10.01")), "baselineDecision"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateDmn(bytes(xml.replace(
                "typeRef=\"number\"", "typeRef=\"string\"").replace("&lt;= 10", "'fn_now'")
                .replace("&gt; 10", "'ordinary'")), "baselineDecision"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateDmn(bytes(xml), "another-key"));
    }

    private static byte[] fixture(String name) throws Exception {
        try (var input = ProcessModelValidatorTest.class.getResourceAsStream("/fixtures/" + name)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static byte[] branching(int levels) {
        StringBuilder nodes = new StringBuilder("<startEvent id='start'/><endEvent id='end'/>");
        StringBuilder flows = new StringBuilder();
        String previous = "start";
        int sequence = 0;
        for (int i = 0; i < levels; i++) {
            String split = "split" + i, join = "join" + i, left = "left" + i, right = "right" + i;
            nodes.append("<parallelGateway id='").append(split).append("'/><parallelGateway id='").append(join)
                    .append("'/><userTask id='").append(left).append("'/><userTask id='").append(right).append("'/>");
            flows.append(flow(sequence++, previous, split)).append(flow(sequence++, split, left))
                    .append(flow(sequence++, split, right)).append(flow(sequence++, left, join)).append(flow(sequence++, right, join));
            previous = join;
        }
        flows.append(flow(sequence, previous, "end"));
        return bytes("<definitions xmlns='http://www.omg.org/spec/BPMN/20100524/MODEL' targetNamespace='urn:test'>"
                + "<process id='parallel' isExecutable='true'>" + nodes + flows + "</process></definitions>");
    }

    private static String flow(int id, String source, String target) {
        return "<sequenceFlow id='flow" + id + "' sourceRef='" + source + "' targetRef='" + target + "'/>";
    }
}
