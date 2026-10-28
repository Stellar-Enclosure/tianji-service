package top.egon.cola.platform.tianji.process.server.release;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.INTEGER;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.STRING;

class ProcessModelValidatorTest {
    private final ProcessModelValidator validator = new ProcessModelValidator();

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
