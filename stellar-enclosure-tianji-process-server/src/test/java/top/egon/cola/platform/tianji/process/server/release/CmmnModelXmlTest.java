package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.cmmn.converter.CmmnXmlConverter;
import org.flowable.cmmn.model.HumanTask;
import org.flowable.cmmn.model.Milestone;
import org.flowable.cmmn.model.Sentry;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CmmnModelXmlTest {
    private static final String CMMN = "http://www.omg.org/spec/CMMN/20151109/MODEL";

    @Test
    void packaged_cmmn_schema_accepts_the_fixed_formal_case() throws Exception {
        byte[] bytes = fixture();

        var document = assertDoesNotThrow(() -> SafeModelXml.parseCmmn(bytes));

        Element definitions = document.getDocumentElement();
        assertEquals("definitions", definitions.getLocalName());
        assertEquals(CMMN, definitions.getNamespaceURI());
        var cases = definitions.getElementsByTagNameNS(CMMN, "case");
        assertEquals(1, cases.getLength());
        assertEquals("formalCase", ((Element) cases.item(0)).getAttribute("id"));
        var plans = definitions.getElementsByTagNameNS(CMMN, "casePlanModel");
        assertEquals(1, plans.getLength());
        assertEquals("casePlan", ((Element) plans.item(0)).getAttribute("id"));
        assertEquals("false", ((Element) plans.item(0)).getAttribute("autoComplete"));
    }

    @Test
    void fixed_flowable_converter_retains_the_formal_case_structure() throws Exception {
        byte[] bytes = fixture();
        assertNotNull(assertDoesNotThrow(() -> SafeModelXml.parseCmmn(bytes)));

        var model = new CmmnXmlConverter().convertToCmmnModel(
                () -> new ByteArrayInputStream(bytes), true, true);

        assertEquals(1, model.getCases().size());
        var caze = model.getPrimaryCase();
        assertNotNull(caze);
        assertEquals("formalCase", caze.getId());
        var plan = caze.getPlanModel();
        assertEquals("casePlan", plan.getId());
        assertFalse(plan.isAutoComplete());
        assertEquals(3, plan.getPlanItems().size());
        assertEquals(3, plan.getPlanItemDefinitions().size());

        var primary = model.findPlanItem("primary");
        assertEquals("primaryTask", primary.getDefinitionRef());
        assertInstanceOf(HumanTask.class, primary.getPlanItemDefinition());
        assertTrue(primary.getEntryCriteria().isEmpty());
        assertTrue(primary.getExitCriteria().isEmpty());
        assertRuleIsPresentAndEmpty(primary.getItemControl().getRequiredRule());
        assertRuleIsPresentAndEmpty(primary.getItemControl().getManualActivationRule());
        assertNull(primary.getItemControl().getRepetitionRule());

        var supplemental = model.findPlanItem("supplemental");
        assertEquals("supplementalTask", supplemental.getDefinitionRef());
        assertInstanceOf(HumanTask.class, supplemental.getPlanItemDefinition());
        assertEquals(1, supplemental.getEntryCriteria().size());
        assertEquals("primaryCompleted", supplemental.getEntryCriteria().getFirst().getSentryRef());
        assertTrue(supplemental.getExitCriteria().isEmpty());
        assertRuleIsPresentAndEmpty(supplemental.getItemControl().getRequiredRule());
        assertRuleIsPresentAndEmpty(supplemental.getItemControl().getManualActivationRule());
        assertNull(supplemental.getItemControl().getRepetitionRule());

        var milestone = model.findPlanItem("milestone");
        assertEquals("completionMilestone", milestone.getDefinitionRef());
        assertInstanceOf(Milestone.class, milestone.getPlanItemDefinition());
        assertEquals(Set.of("supplementalCompleted", "supplementalTerminated"), milestone.getEntryCriteria().stream()
                .map(criterion -> criterion.getSentryRef()).collect(Collectors.toUnmodifiableSet()));
        assertTrue(milestone.getExitCriteria().isEmpty());
        assertRuleIsPresentAndEmpty(milestone.getItemControl().getRequiredRule());
        assertNull(milestone.getItemControl().getManualActivationRule());
        assertNull(milestone.getItemControl().getRepetitionRule());

        assertInstanceOf(HumanTask.class, model.findPlanItemDefinition("primaryTask"));
        assertInstanceOf(HumanTask.class, model.findPlanItemDefinition("supplementalTask"));
        assertInstanceOf(Milestone.class, model.findPlanItemDefinition("completionMilestone"));

        Map<String, Sentry> sentries = plan.getSentries().stream()
                .collect(Collectors.toUnmodifiableMap(Sentry::getId, Function.identity()));
        assertSentry(sentries.get("primaryCompleted"), "primary", "complete");
        assertSentry(sentries.get("supplementalCompleted"), "supplemental", "complete");
        assertSentry(sentries.get("supplementalTerminated"), "supplemental", "terminate");

        assertTrue(model.getDefinitionsAttributes().isEmpty());
        assertTrue(primary.getExtensionElements().isEmpty());
        assertTrue(supplemental.getExtensionElements().isEmpty());
        assertTrue(milestone.getExtensionElements().isEmpty());
    }

    @Test
    void cmmn_parser_rejects_xml_hazards_and_invalid_cmmn_without_leaking_details() throws Exception {
        String xml = new String(fixture(), StandardCharsets.UTF_8);
        String schemaHint = " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " xsi:schemaLocation=\"" + CMMN + " file:///private-value\"";
        var invalid = new byte[][] {
                bytes(xml.replace("version=\"1.0\"", "version=\"1.1\"")),
                bytes(xml.replace("?>", "?><?unsafe probe?>")),
                bytes(xml.replace("encoding=\"UTF-8\"", "encoding=\"ISO-8859-1\"")),
                new byte[] {(byte) 0xc3, 0x28},
                bytes("<definitions xmlns=\"" + CMMN + "\"><case>"),
                bytes(xml.replace(CMMN, "urn:wrong:cmmn")),
                fixture("baseline.bpmn20.xml"),
                fixture("baseline.dmn"),
                bytes(xml.replace("<case id=", "<xi:include xmlns:xi=\"http://www.w3.org/2001/XInclude\" href=\"file:///private-value\" parse=\"text\"/><case id=")),
                bytes(xml.replace("targetNamespace=\"https://stellar-enclosure.example/test\"", "")),
                bytes(xml.replace("<definitions", "<definitions" + schemaHint)
                        .replace("targetNamespace=\"https://stellar-enclosure.example/test\"", "")),
                bytes(xml.replace("<definitions", "<definitions" + schemaHint)
                        .replace(CMMN, "urn:wrong:cmmn")),
                bytes(xml.replace("<definitions", "<!DOCTYPE definitions [<!ENTITY unsafe SYSTEM 'file:///private-value'>]><definitions"))
        };
        assertRejected(null);
        assertRejected(new byte[0]);
        for (int i = 0; i < invalid.length; i++) assertRejected(invalid[i], "invalid XML vector " + i);
    }

    @Test
    void cmmn_parser_enforces_the_exact_byte_boundary() throws Exception {
        assertNotNull(assertDoesNotThrow(() -> SafeModelXml.parseCmmn(paddedTo(SafeModelXml.MAX_BYTES))));
        assertRejected(new byte[SafeModelXml.MAX_BYTES + 1]);
    }

    @Test
    void cmmn_parser_enforces_the_exact_depth_boundary() {
        assertNotNull(assertDoesNotThrow(() -> SafeModelXml.parseCmmn(depthModel(21))));
        assertRejected(depthModel(22));
    }

    @Test
    void cmmn_parser_enforces_the_exact_element_boundary() {
        assertNotNull(assertDoesNotThrow(() -> SafeModelXml.parseCmmn(elementCountModel(1_997))));
        assertRejected(elementCountModel(1_998));
    }

    @Test
    void cmmn_parser_enforces_the_exact_attribute_boundary() {
        assertNotNull(assertDoesNotThrow(() -> SafeModelXml.parseCmmn(attributeCountModel(31))));
        assertRejected(attributeCountModel(32));
    }

    private static void assertRuleIsPresentAndEmpty(org.flowable.cmmn.model.PlanItemRule rule) {
        assertNotNull(rule);
        assertTrue(rule.getCondition() == null || rule.getCondition().isEmpty());
    }

    private static void assertSentry(Sentry sentry, String source, String event) {
        assertNotNull(sentry);
        assertNull(sentry.getSentryIfPart());
        assertEquals(1, sentry.getOnParts().size());
        var onPart = sentry.getOnParts().getFirst();
        assertEquals(source, onPart.getSourceRef());
        assertNotNull(onPart.getSource());
        assertEquals(source, onPart.getSource().getId());
        assertEquals(event, onPart.getStandardEvent());
    }

    private static void assertRejected(byte[] bytes) {
        assertRejected(bytes, "invalid XML");
    }

    private static void assertRejected(byte[] bytes, String label) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> SafeModelXml.parseCmmn(bytes), label);
        assertEquals("Process model is invalid or not supported", failure.getMessage());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("private-value"));
    }

    private static byte[] paddedTo(int size) throws Exception {
        String xml = new String(fixture(), StandardCharsets.UTF_8);
        String closing = "</definitions>";
        int at = xml.lastIndexOf(closing);
        int padding = size - xml.getBytes(StandardCharsets.UTF_8).length - 7;
        if (at < 0 || padding < 0) throw new IllegalStateException("fixture cannot be padded");
        return bytes(xml.substring(0, at) + "<!--" + "x".repeat(padding) + "-->" + xml.substring(at));
    }

    private static byte[] depthModel(int stages) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"" + CMMN + "\" targetNamespace=\"urn:test\"><case id=\"depthCase\">"
                + "<casePlanModel id=\"depthPlan\">");
        for (int i = 0; i < stages; i++) xml.append("<stage id=\"stage").append(i).append("\">");
        for (int i = stages - 1; i >= 0; i--) xml.append("</stage>");
        return bytes(xml + "</casePlanModel></case></definitions>");
    }

    private static byte[] elementCountModel(int tasks) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"" + CMMN + "\" targetNamespace=\"urn:test\"><case id=\"elementCase\">"
                + "<casePlanModel id=\"elementPlan\">");
        for (int i = 0; i < tasks; i++) xml.append("<humanTask id=\"task").append(i).append("\"/>");
        return bytes(xml + "</casePlanModel></case></definitions>");
    }

    private static byte[] attributeCountModel(int extensionAttributes) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"" + CMMN + "\" xmlns:x=\"urn:test:attributes\" targetNamespace=\"urn:test\"");
        for (int i = 0; i < extensionAttributes; i++) xml.append(" x:a").append(i).append("=\"").append(i).append("\"");
        return bytes(xml + "><case id=\"attributeCase\"><casePlanModel id=\"attributePlan\"/></case></definitions>");
    }

    private static byte[] fixture() throws Exception {
        return fixture("formal-case.cmmn");
    }

    private static byte[] fixture(String name) throws Exception {
        try (InputStream input = CmmnModelXmlTest.class.getResourceAsStream("/fixtures/" + name)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
