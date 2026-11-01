package top.egon.cola.platform.tianji.process.server.release;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import top.egon.cola.platform.tianji.process.server.release.ValidatedCaseModel.Action;
import top.egon.cola.platform.tianji.process.server.release.ValidatedCaseModel.PlanItemPolicy;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTask;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTaskKind;

import javax.xml.XMLConstants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates the one closed, non-scriptable formal-Case shape without invoking a native converter. */
final class CmmnModelValidator {
    private static final String CMMN = "http://www.omg.org/spec/CMMN/20151109/MODEL";
    private static final String TIANJI_TASK = "urn:stellar-enclosure:tianji:task:1";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,127}");
    private static final Set<String> PLAN_ITEM_IDS = Set.of("primary", "supplemental", "milestone");
    private static final Set<String> DEFINITION_IDS = Set.of("primaryTask", "supplementalTask", "completionMilestone");
    private static final Set<String> SENTRY_IDS = Set.of("primaryCompleted", "supplementalCompleted", "supplementalTerminated");
    private static final Map<String, Set<String>> CHILDREN = Map.ofEntries(
            Map.entry("definitions", Set.of("case")),
            Map.entry("case", Set.of("casePlanModel")),
            Map.entry("casePlanModel", Set.of("planItem", "sentry", "humanTask", "milestone")),
            Map.entry("planItem", Set.of("itemControl", "entryCriterion")),
            Map.entry("itemControl", Set.of("requiredRule", "manualActivationRule")),
            Map.entry("requiredRule", Set.of()),
            Map.entry("manualActivationRule", Set.of()),
            Map.entry("entryCriterion", Set.of()),
            Map.entry("sentry", Set.of("planItemOnPart")),
            Map.entry("planItemOnPart", Set.of("standardEvent")),
            Map.entry("standardEvent", Set.of()),
            Map.entry("humanTask", Set.of("extensionElements")),
            Map.entry("extensionElements", Set.of("taskLane")),
            Map.entry("taskLane", Set.of()),
            Map.entry("milestone", Set.of()));
    private static final Map<String, Set<String>> ATTRIBUTES = Map.ofEntries(
            Map.entry("definitions", Set.of("id", "name", "targetNamespace")),
            Map.entry("case", Set.of("id", "name")),
            Map.entry("casePlanModel", Set.of("id", "name", "autoComplete")),
            Map.entry("planItem", Set.of("id", "name", "definitionRef")),
            Map.entry("itemControl", Set.of()),
            Map.entry("requiredRule", Set.of("id")),
            Map.entry("manualActivationRule", Set.of("id")),
            Map.entry("entryCriterion", Set.of("id", "sentryRef")),
            Map.entry("sentry", Set.of("id")),
            Map.entry("planItemOnPart", Set.of("id", "sourceRef")),
            Map.entry("standardEvent", Set.of()),
            Map.entry("humanTask", Set.of("id", "name")),
            Map.entry("extensionElements", Set.of()),
            Map.entry("taskLane", Set.of("key", "roleIds")),
            Map.entry("milestone", Set.of("id", "name")));

    ValidatedCaseModel validate(byte[] source, String expectedKey) {
        byte[] bytes = copy(source, expectedKey);
        Element root = SafeModelXml.parseCmmn(bytes).getDocumentElement();
        walk(root, new HashSet<>());
        if (!"definitions".equals(root.getLocalName())) throw invalid();
        required(root, "targetNamespace");

        Element caze = only(root, "case");
        if (!expectedKey.equals(required(caze, "id"))) throw invalid();
        Element plan = only(caze, "casePlanModel");
        if (!"casePlan".equals(required(plan, "id")) || !"false".equals(required(plan, "autoComplete"))) throw invalid();

        Map<String, Element> planItems = exactById(children(plan, "planItem"), PLAN_ITEM_IDS);
        Map<String, Element> definitions = new LinkedHashMap<>();
        for (Element task : children(plan, "humanTask")) putById(definitions, task);
        for (Element milestone : children(plan, "milestone")) putById(definitions, milestone);
        if (!definitions.keySet().equals(DEFINITION_IDS)) throw invalid();
        Map<String, Element> sentries = exactById(children(plan, "sentry"), SENTRY_IDS);

        if (!"milestone".equals(definitions.get("completionMilestone").getLocalName())) throw invalid();

        validatePlanItem(planItems.get("primary"), "primaryTask", "primaryRequired", "primaryManualActivation",
                Map.of());
        validatePlanItem(planItems.get("supplemental"), "supplementalTask", "supplementalRequired",
                "supplementalManualActivation", Map.of("supplementalEntry", "primaryCompleted"));
        validatePlanItem(planItems.get("milestone"), "completionMilestone", "milestoneRequired", null,
                Map.of("milestoneCompletedEntry", "supplementalCompleted",
                        "milestoneTerminatedEntry", "supplementalTerminated"));

        validateSentry(sentries.get("primaryCompleted"), "primaryCompletedPart", "primary", "complete");
        validateSentry(sentries.get("supplementalCompleted"), "supplementalCompletedPart", "supplemental", "complete");
        validateSentry(sentries.get("supplementalTerminated"), "supplementalTerminatedPart", "supplemental", "terminate");

        HumanTask primaryTask = task(definitions.get("primaryTask"));
        HumanTask supplementalTask = task(definitions.get("supplementalTask"));

        Set<String> primaryRoles = soleRoles(primaryTask);
        Set<String> supplementalRoles = soleRoles(supplementalTask);
        Map<String, HumanTask> humanTasks = Map.of("primaryTask", primaryTask, "supplementalTask", supplementalTask);
        Map<String, PlanItemPolicy> policies = Map.of(
                "primary", new PlanItemPolicy("primaryTask", Set.of(Action.DISABLE, Action.ENABLE, Action.START), primaryRoles),
                "supplemental", new PlanItemPolicy("supplementalTask", Set.of(Action.START, Action.TERMINATE), supplementalRoles),
                "milestone", new PlanItemPolicy("completionMilestone", Set.of(), Set.of()));
        return new ValidatedCaseModel(expectedKey, bytes, humanTasks, policies);
    }

    private static HumanTask task(Element element) {
        if (element == null || !"humanTask".equals(element.getLocalName())) throw invalid();
        HumanTask policy = ProcessModelValidator.humanTask(element);
        if (policy.kind() != HumanTaskKind.ACTION || policy.lanes().size() != 1 || policy.initialLane() == null
                || !policy.lanes().containsKey(policy.initialLane()) || policy.laneVariable() != null
                || policy.approvalVariable() != null || !policy.fields().isEmpty()
                || policy.lanes().get(policy.initialLane()).isEmpty()) throw invalid();
        return policy;
    }

    private static Set<String> soleRoles(HumanTask task) {
        Set<String> roles = task.lanes().get(task.initialLane());
        if (roles == null || roles.isEmpty() || roles.size() > 16) throw invalid();
        return Set.copyOf(roles);
    }

    private static void validatePlanItem(Element item, String definitionId, String requiredRuleId,
                                         String manualRuleId, Map<String, String> criteria) {
        if (item == null || !definitionId.equals(required(item, "definitionRef"))) throw invalid();
        Element control = only(item, "itemControl");
        Element requiredRule = only(control, "requiredRule");
        if (!requiredRuleId.equals(required(requiredRule, "id")) || !children(requiredRule).isEmpty()) throw invalid();
        List<Element> manualRules = children(control, "manualActivationRule");
        if (manualRuleId == null) {
            if (!manualRules.isEmpty()) throw invalid();
        } else {
            if (manualRules.size() != 1 || !manualRuleId.equals(required(manualRules.getFirst(), "id"))
                    || !children(manualRules.getFirst()).isEmpty()) throw invalid();
        }
        if (children(control).size() != (manualRuleId == null ? 1 : 2)) throw invalid();
        Map<String, Element> actualCriteria = byId(children(item, "entryCriterion"));
        if (!actualCriteria.keySet().equals(criteria.keySet())) throw invalid();
        criteria.forEach((id, sentry) -> {
            if (!sentry.equals(required(actualCriteria.get(id), "sentryRef"))) throw invalid();
        });
        if (children(item).size() != 1 + criteria.size()) throw invalid();
    }

    private static void validateSentry(Element sentry, String onPartId, String sourceId, String event) {
        if (sentry == null) throw invalid();
        Element onPart = only(sentry, "planItemOnPart");
        if (!onPartId.equals(required(onPart, "id")) || !sourceId.equals(required(onPart, "sourceRef"))) throw invalid();
        Element standardEvent = only(onPart, "standardEvent");
        if (!event.equals(eventLiteral(standardEvent)) || children(onPart).size() != 1 || children(sentry).size() != 1) throw invalid();
    }

    private static void walk(Element element, Set<String> ids) {
        String kind = element.getLocalName();
        String expectedNamespace = "taskLane".equals(kind) ? TIANJI_TASK : CMMN;
        if (!expectedNamespace.equals(element.getNamespaceURI()) || !ATTRIBUTES.containsKey(kind)) throw invalid();
        var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) continue;
            boolean taskAttribute = "humanTask".equals(kind) && TIANJI_TASK.equals(attribute.getNamespaceURI())
                    && Set.of("kind", "initialLane").contains(attribute.getLocalName());
            if (!taskAttribute && (attribute.getNamespaceURI() != null
                    || !ATTRIBUTES.get(kind).contains(attribute.getNodeName()))) throw invalid();
            String value = attribute.getNodeValue();
            if (value.length() > 512 || value.contains("${") || value.contains("#{")) throw invalid();
            if (attribute.getNamespaceURI() == null && "id".equals(attribute.getNodeName())
                    && (!IDENTIFIER.matcher(value).matches() || !ids.add(value))) throw invalid();
        }

        boolean eventText = false;
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) {
                if (!CHILDREN.getOrDefault(kind, Set.of()).contains(child.getLocalName())) throw invalid();
                walk(child, ids);
            } else if ("standardEvent".equals(kind)) {
                if (node.getNodeType() != Node.TEXT_NODE || eventText
                        || !("complete".equals(node.getNodeValue()) || "terminate".equals(node.getNodeValue()))) throw invalid();
                eventText = true;
            } else if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                if (!node.getNodeValue().isBlank()) throw invalid();
            } else if (node.getNodeType() != Node.COMMENT_NODE) throw invalid();
        }
        if ("standardEvent".equals(kind) && !eventText) throw invalid();
    }

    private static String eventLiteral(Element event) {
        Node value = event.getFirstChild();
        if (value == null || value != event.getLastChild() || value.getNodeType() != Node.TEXT_NODE) throw invalid();
        String literal = value.getNodeValue();
        if (!"complete".equals(literal) && !"terminate".equals(literal)) throw invalid();
        return literal;
    }

    private static Map<String, Element> exactById(List<Element> elements, Set<String> expected) {
        Map<String, Element> result = byId(elements);
        if (!result.keySet().equals(expected)) throw invalid();
        return result;
    }

    private static Map<String, Element> byId(List<Element> elements) {
        Map<String, Element> result = new LinkedHashMap<>();
        for (Element element : elements) putById(result, element);
        return result;
    }

    private static void putById(Map<String, Element> target, Element element) {
        if (target.putIfAbsent(required(element, "id"), element) != null) throw invalid();
    }

    private static byte[] copy(byte[] source, String expectedKey) {
        if (source == null || source.length == 0 || source.length > SafeModelXml.MAX_BYTES
                || expectedKey == null || !IDENTIFIER.matcher(expectedKey).matches()) throw invalid();
        return source.clone();
    }

    private static String required(Element element, String attribute) {
        if (element == null) throw invalid();
        String value = element.getAttribute(attribute);
        if (value.isEmpty() || !value.equals(value.trim())) throw invalid();
        return value;
    }

    private static Element only(Element parent, String name) {
        List<Element> values = children(parent, name);
        if (values.size() != 1) throw invalid();
        return values.getFirst();
    }

    private static List<Element> children(Element parent, String name) {
        return children(parent).stream().filter(element -> name.equals(element.getLocalName())).toList();
    }

    private static List<Element> children(Element parent) {
        if (parent == null) throw invalid();
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) result.add(element);
        }
        return result;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Process model is invalid or not supported");
    }
}
