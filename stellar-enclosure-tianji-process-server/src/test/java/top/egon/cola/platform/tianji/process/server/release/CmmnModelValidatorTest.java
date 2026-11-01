package top.egon.cola.platform.tianji.process.server.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CmmnModelValidatorTest {
    private static final String ERROR = "Process model is invalid or not supported";
    private static final String TASK_NAMESPACE = "urn:stellar-enclosure:tianji:task:1";
    private static final Set<String> XSD_PERMISSIVE = Set.of(
            "wrong task namespace",
            "missing schema-optional definition reference",
            "event text split by a comment fragment",
            "event text carried by CDATA",
            "form key assignment",
            "explicit assignee",
            "task listener",
            "role zero",
            "primary name exceeds 512 characters",
            "nested stage",
            "process task callable",
            "case task callable");
    private final CmmnModelValidator validator = new CmmnModelValidator();

    @Test
    void validates_the_closed_governed_case_and_projects_only_immutable_policy_metadata() throws Exception {
        byte[] source = fixture("governed-case.cmmn");
        byte[] expected = source.clone();

        ValidatedCaseModel model = assertDoesNotThrow(() -> validator.validate(source, "governedCase"));

        assertEquals("governedCase", model.definitionKey());
        assertArrayEquals(expected, model.xml());
        assertNotSame(source, model.xml());
        assertEquals(Set.of("primaryTask", "supplementalTask"), model.humanTasks().keySet());
        assertEquals(Set.of("primary", "supplemental", "milestone"), model.planItems().keySet());
        assertEquals(Set.of("7", "8", "9"), model.operatorRoleIds());

        var primaryTask = model.humanTasks().get("primaryTask");
        assertEquals(ValidatedProcessModel.HumanTaskKind.ACTION, primaryTask.kind());
        assertEquals("operator", primaryTask.initialLane());
        assertNull(primaryTask.laneVariable());
        assertNull(primaryTask.approvalVariable());
        assertEquals(Map.of("operator", Set.of("7", "8")), primaryTask.lanes());
        assertTrue(primaryTask.fields().isEmpty());

        var supplementalTask = model.humanTasks().get("supplementalTask");
        assertEquals(ValidatedProcessModel.HumanTaskKind.ACTION, supplementalTask.kind());
        assertEquals("operator", supplementalTask.initialLane());
        assertEquals(Map.of("operator", Set.of("8", "9")), supplementalTask.lanes());
        assertTrue(supplementalTask.fields().isEmpty());

        var primary = model.planItems().get("primary");
        assertEquals("primaryTask", primary.definitionId());
        assertEquals(Set.of(ValidatedCaseModel.Action.DISABLE, ValidatedCaseModel.Action.ENABLE,
                ValidatedCaseModel.Action.START), primary.actions());
        assertEquals(Set.of("7", "8"), primary.roleIds());

        var supplemental = model.planItems().get("supplemental");
        assertEquals("supplementalTask", supplemental.definitionId());
        assertEquals(Set.of(ValidatedCaseModel.Action.START, ValidatedCaseModel.Action.TERMINATE), supplemental.actions());
        assertEquals(Set.of("8", "9"), supplemental.roleIds());

        var milestone = model.planItems().get("milestone");
        assertEquals("completionMilestone", milestone.definitionId());
        assertTrue(milestone.actions().isEmpty());
        assertTrue(milestone.roleIds().isEmpty());
    }

    @Test
    void accepts_a_different_valid_case_key_lane_name_and_bounded_numeric_role_set() throws Exception {
        String roles = IntStream.rangeClosed(101, 116).mapToObj(Integer::toString)
                .collect(java.util.stream.Collectors.joining(","));
        String xml = text("governed-case.cmmn")
                .replace("    <case id=", "    <!-- benign structural comment -->\n    <case id=")
                .replace("id=\"governedCase\"", "id=\"alternateCase\"")
                .replace("task:initialLane=\"operator\"", "task:initialLane=\"reviewer\"")
                .replace("key=\"operator\"", "key=\"reviewer\"")
                .replace("roleIds=\"7,8\"", "roleIds=\"" + roles + "\"")
                .replace("roleIds=\"8,9\"", "roleIds=\"116,9223372036854775807\"");

        ValidatedCaseModel model = assertDoesNotThrow(() -> validator.validate(bytes(xml), "alternateCase"));

        assertEquals("alternateCase", model.definitionKey());
        assertEquals(Set.of("primary", "supplemental", "milestone"), model.planItems().keySet());
        assertEquals("primaryTask", model.planItems().get("primary").definitionId());
        assertEquals("supplementalTask", model.planItems().get("supplemental").definitionId());
        assertEquals("completionMilestone", model.planItems().get("milestone").definitionId());
        assertEquals(Set.of("reviewer"), model.humanTasks().get("primaryTask").lanes().keySet());
        assertEquals(Set.of("reviewer"), model.humanTasks().get("supplementalTask").lanes().keySet());
        assertTrue(model.operatorRoleIds().containsAll(Set.of("101", "116", "9223372036854775807")));
        assertEquals(17, model.operatorRoleIds().size());
    }

    @Test
    void copies_source_and_returned_bytes_and_exposes_only_deeply_immutable_maps_and_sets() throws Exception {
        byte[] source = fixture("governed-case.cmmn");
        byte[] expected = source.clone();
        ValidatedCaseModel model = assertDoesNotThrow(() -> validator.validate(source, "governedCase"));

        source[0] = 'x';
        assertArrayEquals(expected, model.xml());
        byte[] returned = model.xml();
        returned[0] = 'y';
        assertArrayEquals(expected, model.xml());

        assertThrows(UnsupportedOperationException.class, () -> model.humanTasks().clear());
        assertThrows(UnsupportedOperationException.class, () -> model.planItems().clear());
        assertThrows(UnsupportedOperationException.class, () -> model.operatorRoleIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> model.planItems().get("primary").actions().clear());
        assertThrows(UnsupportedOperationException.class, () -> model.planItems().get("primary").roleIds().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> model.humanTasks().get("primaryTask").lanes().get("operator").clear());
    }

    @Test
    void rejects_the_schema_valid_bare_case_until_both_human_tasks_have_governed_policy() throws Exception {
        byte[] source = fixture("formal-case.cmmn");
        assertDoesNotThrow(() -> SafeModelXml.parseCmmn(source));
        assertInvalid(source, "formalCase");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidModels")
    void rejects_models_outside_the_one_closed_governed_case_contract(InvalidModel invalid) throws Exception {
        String original = text("governed-case.cmmn");
        String mutated = invalid.mutation().apply(original);
        if (mutated.equals(original)) throw new AssertionError("mutation did not change fixture: " + invalid.label());
        byte[] source = bytes(mutated);
        if (XSD_PERMISSIVE.contains(invalid.label())) {
            assertDoesNotThrow(() -> SafeModelXml.parseCmmn(source));
        }
        assertInvalid(source, "governedCase");
    }

    @Test
    void rejects_invalid_expected_keys_and_source_boundaries() throws Exception {
        byte[] source = fixture("governed-case.cmmn");
        assertInvalid(source, "anotherCase");
        assertInvalid(source, null);
        assertInvalid(source, "");
        assertInvalid(source, "-invalid");
        assertInvalid(source, "a".repeat(129));
        assertInvalid(null, "governedCase");
        assertInvalid(new byte[0], "governedCase");
        assertInvalid(new byte[SafeModelXml.MAX_BYTES + 1], "governedCase");
    }

    static Stream<InvalidModel> invalidModels() {
        String seventeenRoles = IntStream.rangeClosed(1, 17).mapToObj(Integer::toString)
                .collect(java.util.stream.Collectors.joining(","));
        return Stream.of(
                invalid("wrong task namespace", replace("xmlns:task=\"" + TASK_NAMESPACE + "\"", "xmlns:task=\"urn:wrong:task\"")),
                invalid("unexpected namespaced root attribute", replace("targetNamespace=", "task:unexpected=\"true\" targetNamespace=")),
                invalid("primary name exceeds 512 characters", replace("name=\"Primary Task\"", "name=\"" + "x".repeat(513) + "\"")),
                invalid("callable expression in an attribute", replace("name=\"Primary Task\"", "name=\"${execution.getVariable('secret')}\"")),
                invalid("required rule condition", replace("<requiredRule id=\"primaryRequired\"/>",
                        "<requiredRule id=\"primaryRequired\"><condition>${true}</condition></requiredRule>")),
                invalid("manual activation condition", replace("<manualActivationRule id=\"primaryManualActivation\"/>",
                        "<manualActivationRule id=\"primaryManualActivation\"><condition>#{bean.call()}</condition></manualActivationRule>")),
                invalid("CMMN import", replace("    <case id=", "    <import location=\"local\" namespace=\"urn:other\" importType=\"urn:type\"/>\n    <case id=")),
                invalid("CMMN diagram interchange", replace("</definitions>",
                        "<cmmndi:CMMNDI xmlns:cmmndi=\"http://www.omg.org/spec/CMMN/20151109/CMMNDI\"/></definitions>")),
                invalid("repetition rule", replace("<requiredRule id=\"primaryRequired\"/>",
                        "<repetitionRule id=\"primaryRepetition\"/><requiredRule id=\"primaryRequired\"/>")),
                invalid("nested stage", replace("            <humanTask id=\"primaryTask\"",
                        "            <stage id=\"nestedStage\"><humanTask id=\"nestedTask\"/></stage>\n            <humanTask id=\"primaryTask\"")),
                invalid("process task callable", replace("            <humanTask id=\"primaryTask\"",
                        "            <processTask id=\"callableProcessTask\"/>\n            <humanTask id=\"primaryTask\"")),
                invalid("case task callable", replace("            <humanTask id=\"primaryTask\"",
                        "            <caseTask id=\"callableCaseTask\"/>\n            <humanTask id=\"primaryTask\"")),
                invalid("extra plan item and definition", xml -> replace("            <milestone id=\"completionMilestone\"",
                        "            <humanTask id=\"extraTask\"/>\n            <milestone id=\"completionMilestone\"").apply(
                        replace("            <sentry id=\"primaryCompleted\">",
                                "            <planItem id=\"extra\" definitionRef=\"extraTask\"/>\n            <sentry id=\"primaryCompleted\">").apply(xml))),
                invalid("required milestone plan item missing", replace("            <planItem id=\"milestone\" name=\"Milestone\" definitionRef=\"completionMilestone\">\n"
                        + "                <itemControl>\n"
                        + "                    <requiredRule id=\"milestoneRequired\"/>\n"
                        + "                </itemControl>\n"
                        + "                <entryCriterion id=\"milestoneCompletedEntry\" sentryRef=\"supplementalCompleted\"/>\n"
                        + "                <entryCriterion id=\"milestoneTerminatedEntry\" sentryRef=\"supplementalTerminated\"/>\n"
                        + "            </planItem>\n", "")),
                invalid("wrong primary definition reference", replace("definitionRef=\"primaryTask\"", "definitionRef=\"supplementalTask\"")),
                invalid("missing schema-optional definition reference", replace(" definitionRef=\"primaryTask\"", "")),
                invalid("missing explicit autoComplete", replace(" autoComplete=\"false\"", "")),
                invalid("autoComplete true", replace("autoComplete=\"false\"", "autoComplete=\"true\"")),
                invalid("case plan id changed", replace("id=\"casePlan\"", "id=\"otherCasePlan\"")),
                invalid("primary required rule id changed", replace("id=\"primaryRequired\"", "id=\"otherPrimaryRequired\"")),
                invalid("supplemental required rule id changed", replace("id=\"supplementalRequired\"", "id=\"otherSupplementalRequired\"")),
                invalid("milestone required rule id changed", replace("id=\"milestoneRequired\"", "id=\"otherMilestoneRequired\"")),
                invalid("primary manual activation rule id changed", replace("id=\"primaryManualActivation\"", "id=\"otherPrimaryManualActivation\"")),
                invalid("supplemental manual activation rule id changed", replace("id=\"supplementalManualActivation\"", "id=\"otherSupplementalManualActivation\"")),
                invalid("supplemental criterion id changed", replace("id=\"supplementalEntry\"", "id=\"otherSupplementalEntry\"")),
                invalid("milestone completed criterion id changed", replace("id=\"milestoneCompletedEntry\"", "id=\"otherMilestoneCompletedEntry\"")),
                invalid("milestone terminated criterion id changed", replace("id=\"milestoneTerminatedEntry\"", "id=\"otherMilestoneTerminatedEntry\"")),
                invalid("primary completion on-part id changed", replace("id=\"primaryCompletedPart\"", "id=\"otherPrimaryCompletedPart\"")),
                invalid("supplemental completion on-part id changed", replace("id=\"supplementalCompletedPart\"", "id=\"otherSupplementalCompletedPart\"")),
                invalid("supplemental termination on-part id changed", replace("id=\"supplementalTerminatedPart\"", "id=\"otherSupplementalTerminatedPart\"")),
                invalid("duplicate global id", replace("id=\"supplementalRequired\"", "id=\"primaryRequired\"")),
                invalid("primary unexpectedly has entry criterion", replace("            </planItem>\n            <planItem id=\"supplemental\"",
                        "                <entryCriterion id=\"primaryEntry\" sentryRef=\"primaryCompleted\"/>\n            </planItem>\n            <planItem id=\"supplemental\"")),
                invalid("supplemental entry criterion missing", replace("                <entryCriterion id=\"supplementalEntry\" sentryRef=\"primaryCompleted\"/>\n", "")),
                invalid("supplemental entry criterion references the wrong sentry", replace("sentryRef=\"primaryCompleted\"", "sentryRef=\"supplementalCompleted\"")),
                invalid("required primary sentry missing", replace("            <sentry id=\"primaryCompleted\">\n"
                        + "                <planItemOnPart id=\"primaryCompletedPart\" sourceRef=\"primary\">\n"
                        + "                    <standardEvent>complete</standardEvent>\n"
                        + "                </planItemOnPart>\n"
                        + "            </sentry>\n", "")),
                invalid("extra sentry", replace("            <sentry id=\"primaryCompleted\">",
                        "            <sentry id=\"extraSentry\"/>\n            <sentry id=\"primaryCompleted\">")),
                invalid("milestone criteria collapse complete and terminate into one AND sentry", CmmnModelValidatorTest::andSentry),
                invalid("wrong primary sentry source", replace("sourceRef=\"primary\"", "sourceRef=\"supplemental\"")),
                invalid("wrong primary sentry state", replaceFirst("<standardEvent>complete</standardEvent>", "<standardEvent>start</standardEvent>")),
                invalid("event text split by a comment fragment", replaceFirst("<standardEvent>complete</standardEvent>",
                        "<standardEvent>com<!--fragment-->plete</standardEvent>")),
                invalid("event text carried by CDATA", replaceFirst("<standardEvent>complete</standardEvent>",
                        "<standardEvent><![CDATA[complete]]></standardEvent>")),
                invalid("sentry if-part condition", replace("            </sentry>\n            <sentry id=\"supplementalCompleted\">",
                        "                <ifPart><condition>${true}</condition></ifPart>\n            </sentry>\n            <sentry id=\"supplementalCompleted\">")),
                invalid("primary required rule missing", replace("                    <requiredRule id=\"primaryRequired\"/>\n", "")),
                invalid("supplemental manual activation rule missing", replace("                    <manualActivationRule id=\"supplementalManualActivation\"/>\n", "")),
                invalid("milestone manual activation rule", replace("                    <requiredRule id=\"milestoneRequired\"/>",
                        "                    <requiredRule id=\"milestoneRequired\"/>\n                    <manualActivationRule id=\"milestoneManual\"/>")),
                invalid("form key assignment", addFlowableAttribute("formKey", "governed-form")),
                invalid("explicit assignee", addFlowableAttribute("assignee", "user-1")),
                invalid("candidate groups", addFlowableAttribute("candidateGroups", "role:7")),
                invalid("task listener", replace("                    <task:taskLane key=\"operator\" roleIds=\"7,8\"/>",
                        "                    <task:taskLane key=\"operator\" roleIds=\"7,8\"/>\n                    <flowable:taskListener xmlns:flowable=\"http://flowable.org/cmmn\" event=\"create\" class=\"java.lang.Runtime\"/>")),
                invalid("task field", replace("                    <task:taskLane key=\"operator\" roleIds=\"7,8\"/>",
                        "                    <task:taskLane key=\"operator\" roleIds=\"7,8\"/>\n                    <task:taskField name=\"comment\" target=\"comment\" type=\"STRING\"/>")),
                invalid("multiple lanes", replace("                    <task:taskLane key=\"operator\" roleIds=\"7,8\"/>",
                        "                    <task:taskLane key=\"operator\" roleIds=\"7,8\"/>\n                    <task:taskLane key=\"manager\" roleIds=\"10\"/>")),
                invalid("initial lane does not match only lane", replaceFirst("task:initialLane=\"operator\"", "task:initialLane=\"reviewer\"")),
                invalid("approval task kind", replaceFirst("task:kind=\"ACTION\"", "task:kind=\"APPROVAL\"")),
                invalid("dynamic lane variable", replaceFirst("task:initialLane=\"operator\"", "task:laneVariable=\"selectedLane\"")),
                invalid("role zero", replace("roleIds=\"7,8\"", "roleIds=\"0,8\"")),
                invalid("empty role list", replace("roleIds=\"7,8\"", "roleIds=\"\"")),
                invalid("nonnumeric role", replace("roleIds=\"7,8\"", "roleIds=\"seven,8\"")),
                invalid("role leading zero", replace("roleIds=\"7,8\"", "roleIds=\"07,8\"")),
                invalid("role long overflow", replace("roleIds=\"7,8\"", "roleIds=\"9223372036854775808,8\"")),
                invalid("duplicate role", replace("roleIds=\"7,8\"", "roleIds=\"7,7\"")),
                invalid("seventeen roles exceed the maximum", replace("roleIds=\"7,8\"", "roleIds=\"" + seventeenRoles + "\"")),
                invalid("role whitespace", replace("roleIds=\"7,8\"", "roleIds=\"7, 8\""))
        );
    }

    private static UnaryOperator<String> addFlowableAttribute(String name, String value) {
        return xml -> xml.replace("xmlns:task=\"" + TASK_NAMESPACE + "\"",
                        "xmlns:task=\"" + TASK_NAMESPACE + "\" xmlns:flowable=\"http://flowable.org/cmmn\"")
                .replaceFirst("task:kind=\"ACTION\"", "flowable:" + name + "=\"" + value + "\" task:kind=\"ACTION\"");
    }

    private static String andSentry(String xml) {
        String criteria = "                <entryCriterion id=\"milestoneCompletedEntry\" sentryRef=\"supplementalCompleted\"/>\n"
                + "                <entryCriterion id=\"milestoneTerminatedEntry\" sentryRef=\"supplementalTerminated\"/>";
        String oneCriterion = "                <entryCriterion id=\"milestoneTerminalEntry\" sentryRef=\"supplementalTerminal\"/>";
        String sentries = "            <sentry id=\"supplementalCompleted\">\n"
                + "                <planItemOnPart id=\"supplementalCompletedPart\" sourceRef=\"supplemental\">\n"
                + "                    <standardEvent>complete</standardEvent>\n"
                + "                </planItemOnPart>\n"
                + "            </sentry>\n"
                + "            <sentry id=\"supplementalTerminated\">\n"
                + "                <planItemOnPart id=\"supplementalTerminatedPart\" sourceRef=\"supplemental\">\n"
                + "                    <standardEvent>terminate</standardEvent>\n"
                + "                </planItemOnPart>\n"
                + "            </sentry>";
        String oneSentry = "            <sentry id=\"supplementalTerminal\">\n"
                + "                <planItemOnPart id=\"supplementalCompletedPart\" sourceRef=\"supplemental\">\n"
                + "                    <standardEvent>complete</standardEvent>\n"
                + "                </planItemOnPart>\n"
                + "                <planItemOnPart id=\"supplementalTerminatedPart\" sourceRef=\"supplemental\">\n"
                + "                    <standardEvent>terminate</standardEvent>\n"
                + "                </planItemOnPart>\n"
                + "            </sentry>";
        String result = xml.replace(criteria, oneCriterion).replace(sentries, oneSentry);
        if (result.equals(xml)) throw new AssertionError("AND sentry mutation did not match fixture");
        return result;
    }

    private static InvalidModel invalid(String label, UnaryOperator<String> mutation) {
        return new InvalidModel(label, mutation);
    }

    private static UnaryOperator<String> replace(String from, String to) {
        return xml -> xml.replace(from, to);
    }

    private static UnaryOperator<String> replaceFirst(String from, String to) {
        return xml -> xml.replaceFirst(java.util.regex.Pattern.quote(from), java.util.regex.Matcher.quoteReplacement(to));
    }

    private static void assertInvalid(byte[] source, String expectedKey) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new CmmnModelValidator().validate(source, expectedKey));
        assertEquals(ERROR, failure.getMessage());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("private"));
    }

    private static byte[] fixture(String name) throws Exception {
        try (InputStream input = CmmnModelValidatorTest.class.getResourceAsStream("/fixtures/" + name)) {
            if (input == null) throw new AssertionError("missing fixture: " + name);
            return input.readAllBytes();
        }
    }

    private static String text(String name) throws Exception {
        return new String(fixture(name), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    record InvalidModel(String label, UnaryOperator<String> mutation) {
        @Override public String toString() { return label; }
    }
}
