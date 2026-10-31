package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HumanTaskTimerModelTest {
    private final ProcessModelValidator validator = new ProcessModelValidator();

    @Test void a_single_interrupting_fixed_timer_is_bound_to_a_governed_task_and_the_native_converter_preserves_its_shape() throws Exception {
        byte[] xml = fixture().getBytes(StandardCharsets.UTF_8); var model = validator.validateBpmn(xml, "timedHumanReview");
        assertEquals(Map.of("reviewTimeout", new ValidatedProcessModel.HumanTaskTimer("review", Duration.ofMinutes(5))), model.humanTaskTimers());
        assertThrows(UnsupportedOperationException.class, () -> model.humanTaskTimers().clear());
        var nativeModel = new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(xml), true, true);
        var timer = assertInstanceOf(BoundaryEvent.class, nativeModel.getMainProcess().getFlowElement("reviewTimeout"));
        assertTrue(timer.isCancelActivity()); assertEquals("review", timer.getAttachedToRefId());
        assertEquals("PT5M", assertInstanceOf(TimerEventDefinition.class, timer.getEventDefinitions().getFirst()).getTimeDuration());
        validator.validateHumanTaskBindings(model, Map.of(), null);
    }

    @Test void raw_calendar_periods_expressions_fractional_or_unbounded_times_and_repeating_timers_are_rejected() throws Exception {
        String xml = fixture();
        for (var duration : new String[]{"P1D", "PT60S", "PT1.5S", "PT0S", "-PT1S", "PT8761H", "${timeout}", " PT5M"}) {
            assertThrows(IllegalArgumentException.class, () -> validate(xml.replace("PT5M", duration)), duration);
        }
        assertDoesNotThrow(() -> validate(xml.replace("PT5M", "PT1S"))); assertDoesNotThrow(() -> validate(xml.replace("PT5M", "PT8760H")));
        assertThrows(IllegalArgumentException.class, () -> validate(xml.replace("timeDuration", "timeCycle")));
        assertThrows(IllegalArgumentException.class, () -> validate(xml.replace("timeDuration", "timeDate")));
    }

    @Test void attachment_incoming_edges_duplicate_timers_cycles_and_noninterrupting_paths_cannot_bypass_the_task_policy() throws Exception {
        String xml = fixture();
        for (var invalid : new String[]{xml.replace("cancelActivity=\"true\"", "cancelActivity=\"false\""), xml.replace(" cancelActivity=\"true\"", ""),
                xml.replace("attachedToRef=\"review\"", "attachedToRef=\"start\""), xml.replace("attachedToRef=\"review\"", "attachedToRef=\"unknown\""),
                xml.replace("targetRef=\"review\"", "targetRef=\"reviewTimeout\""), xml.replace("targetRef=\"timedOut\"", "targetRef=\"review\""),
                xml.replace("<endEvent id=\"timedOut\"/>", "<boundaryEvent id=\"secondTimeout\" attachedToRef=\"review\" cancelActivity=\"true\"><timerEventDefinition><timeDuration>PT5M</timeDuration></timerEventDefinition></boundaryEvent><sequenceFlow id=\"secondEnd\" sourceRef=\"secondTimeout\" targetRef=\"timedOut\"/><endEvent id=\"timedOut\"/>")}) {
            assertThrows(IllegalArgumentException.class, () -> validate(invalid));
        }
        String plain = xml.replace(" task:kind=\"APPROVAL\" task:initialLane=\"finance\" task:approvalVariable=\"reviewApproved\"", "")
                .replaceAll("(?s)<extensionElements>.*?</extensionElements>", "");
        assertThrows(IllegalArgumentException.class, () -> validate(plain));
    }

    @Test void a_timeout_branch_cannot_read_an_unsubmitted_approval_result() throws Exception {
        String xml = fixture().replace("targetRef=\"timedOut\"", "targetRef=\"timeoutRoute\"")
                .replace("<endEvent id=\"timedOut\"/>", "<exclusiveGateway id=\"timeoutRoute\" default=\"noApproval\"/><endEvent id=\"timedOut\"/><endEvent id=\"forgedApproval\"/>"
                        + "<sequenceFlow id=\"yesApproval\" sourceRef=\"timeoutRoute\" targetRef=\"forgedApproval\"><conditionExpression>${reviewApproved == true}</conditionExpression></sequenceFlow>"
                        + "<sequenceFlow id=\"noApproval\" sourceRef=\"timeoutRoute\" targetRef=\"timedOut\"/>");
        assertThrows(IllegalArgumentException.class, () -> validate(xml));
    }

    @Test void a_timeout_cannot_create_a_dynamic_task_if_its_dmn_precedes_only_the_other_branch() throws Exception {
        String decision = "<serviceTask id=\"decision\" xmlns:flowable=\"http://flowable.org/bpmn\" flowable:type=\"dmn\"><extensionElements><flowable:field name=\"decisionTableReferenceKey\"><flowable:string>routing</flowable:string></flowable:field></extensionElements></serviceTask>";
        String xml = fixture().replace("targetRef=\"completed\"", "targetRef=\"decision\"").replace("targetRef=\"timedOut\"", "targetRef=\"merge\"")
                .replace("<endEvent id=\"completed\"/>", decision + "<exclusiveGateway id=\"merge\"/><userTask id=\"escalate\" task:kind=\"ACTION\" task:laneVariable=\"queue\"><extensionElements><task:taskLane key=\"finance\" roleIds=\"50\"/></extensionElements></userTask><endEvent id=\"completed\"/>"
                        + "<sequenceFlow id=\"decisionMerge\" sourceRef=\"decision\" targetRef=\"merge\"/><sequenceFlow id=\"toEscalate\" sourceRef=\"merge\" targetRef=\"escalate\"/><sequenceFlow id=\"finishEscalate\" sourceRef=\"escalate\" targetRef=\"completed\"/>")
                .replace("<endEvent id=\"timedOut\"/>", "");
        assertThrows(IllegalArgumentException.class, () -> validate(xml));
    }

    private ValidatedProcessModel validate(String xml) { return validator.validateBpmn(xml.getBytes(StandardCharsets.UTF_8), "timedHumanReview"); }
    private static String fixture() throws Exception { try (var input = HumanTaskTimerModelTest.class.getResourceAsStream("/fixtures/governed-human-task-timer.bpmn20.xml")) { assertNotNull(input); return new String(input.readAllBytes(), StandardCharsets.UTF_8); } }
}
