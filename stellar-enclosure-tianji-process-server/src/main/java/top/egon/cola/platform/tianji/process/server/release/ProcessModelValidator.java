package top.egon.cola.platform.tianji.process.server.release;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.RouteCondition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerParameter;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerTask;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;

import javax.xml.XMLConstants;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates bounded, non-scriptable process models; publication applies additional feature gates. */
public final class ProcessModelValidator {
    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String DMN = "http://www.omg.org/spec/DMN/20151101";
    private static final String FLOWABLE = "http://flowable.org/bpmn";
    private static final String TIANJI_WORKER = "urn:stellar-enclosure:tianji:worker:1";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,127}");
    private static final Pattern VARIABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Set<String> RESERVED = Set.of("true", "false", "null", "empty", "and", "or", "not", "div", "mod",
            "eq", "ne", "lt", "gt", "le", "ge", "instanceof", "execution", "task", "authenticatedUserId");
    private static final String INTEGER = "-?(?:0|[1-9][0-9]{0,14})";
    private static final String STRING = "'[\\p{L}\\p{N} _.,:;!?/\\-]{0,128}'";
    private static final Pattern ROUTE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]{0,63}) == (" + STRING
            + "|" + INTEGER + "|true|false)\\}");
    private static final Map<String, Set<String>> BPMN_CHILDREN = Map.ofEntries(
            Map.entry("definitions", Set.of("process")),
            Map.entry("process", Set.of("startEvent", "endEvent", "userTask", "serviceTask", "intermediateCatchEvent", "parallelGateway", "exclusiveGateway", "sequenceFlow")),
            Map.entry("intermediateCatchEvent", Set.of("timerEventDefinition")),
            Map.entry("timerEventDefinition", Set.of("timeDuration")),
            Map.entry("serviceTask", Set.of("extensionElements")),
            Map.entry("extensionElements", Set.of("field", "externalWorkerInParameter", "externalWorkerOutParameter")),
            Map.entry("field", Set.of("string")), Map.entry("sequenceFlow", Set.of("conditionExpression")));
    private static final Map<String, Set<String>> BPMN_ATTRIBUTES = Map.ofEntries(
            Map.entry("definitions", Set.of("id", "name", "targetNamespace")),
            Map.entry("process", Set.of("id", "name", "isExecutable")),
            Map.entry("startEvent", Set.of("id", "name")), Map.entry("endEvent", Set.of("id", "name")),
            Map.entry("userTask", Set.of("id", "name")), Map.entry("parallelGateway", Set.of("id", "name")),
            Map.entry("serviceTask", Set.of("id", "name")), Map.entry("extensionElements", Set.of()),
            Map.entry("field", Set.of("name")), Map.entry("string", Set.of()),
            Map.entry("externalWorkerInParameter", Set.of("source", "target")),
            Map.entry("externalWorkerOutParameter", Set.of("source", "target")),
            Map.entry("exclusiveGateway", Set.of("id", "name", "default")), Map.entry("conditionExpression", Set.of()),
            Map.entry("intermediateCatchEvent", Set.of("id", "name")),
            Map.entry("timerEventDefinition", Set.of("id")), Map.entry("timeDuration", Set.of("id")),
            Map.entry("sequenceFlow", Set.of("id", "name", "sourceRef", "targetRef")));
    private static final Map<String, Set<String>> DMN_CHILDREN = Map.ofEntries(
            Map.entry("definitions", Set.of("decision")), Map.entry("decision", Set.of("decisionTable")),
            Map.entry("decisionTable", Set.of("input", "output", "rule")),
            Map.entry("input", Set.of("inputExpression")), Map.entry("inputExpression", Set.of("text")),
            Map.entry("rule", Set.of("inputEntry", "outputEntry")), Map.entry("inputEntry", Set.of("text")),
            Map.entry("outputEntry", Set.of("text")));
    private static final Map<String, Set<String>> DMN_ATTRIBUTES = Map.ofEntries(
            Map.entry("definitions", Set.of("id", "name", "namespace")), Map.entry("decision", Set.of("id", "name")),
            Map.entry("decisionTable", Set.of("id", "hitPolicy")), Map.entry("input", Set.of("id", "label")),
            Map.entry("inputExpression", Set.of("id", "typeRef")), Map.entry("output", Set.of("id", "name", "label", "typeRef")),
            Map.entry("rule", Set.of("id")), Map.entry("inputEntry", Set.of("id")),
            Map.entry("outputEntry", Set.of("id")), Map.entry("text", Set.of()));

    public ValidatedProcessModel validateBpmn(byte[] source, String expectedKey) {
        byte[] bytes = copy(source, expectedKey);
        Document document = SafeModelXml.parse(bytes, true);
        Element root = document.getDocumentElement();
        validateTree(root, BPMN, BPMN_CHILDREN, BPMN_ATTRIBUTES);
        if (!"definitions".equals(root.getLocalName())) throw invalid();
        Element process = only(root, "process");
        String key = required(process, "id");
        if (!key.equals(expectedKey) || !"true".equals(required(process, "isExecutable"))) throw invalid();

        Map<String, FlowNode> nodes = new LinkedHashMap<>();
        List<Element> flows = new ArrayList<>();
        Map<String, String> decisions = new LinkedHashMap<>();
        Map<String, WorkerTask> workers = new LinkedHashMap<>();
        for (Element element : children(process)) {
            String kind = element.getLocalName();
            if ("sequenceFlow".equals(kind)) {
                required(element, "id");
                flows.add(element);
            } else {
                String id = required(element, "id");
                nodes.put(id, new FlowNode(kind, element));
                if ("serviceTask".equals(kind)) {
                    String taskType = element.getAttributeNS(FLOWABLE, "type");
                    Element extension = only(element, "extensionElements");
                    if ("dmn".equals(taskType)) {
                        if (element.hasAttributeNS(FLOWABLE, "topic") || element.hasAttributeNS(FLOWABLE, "doNotIncludeVariables")
                                || element.hasAttributeNS(TIANJI_WORKER, "schemaVersion") || element.hasAttributeNS(TIANJI_WORKER, "topic")
                                || element.hasAttributeNS(TIANJI_WORKER, "effectSafety")
                                || children(extension).size() != 1) throw invalid();
                        Element field = only(extension, "field");
                        if (!"decisionTableReferenceKey".equals(required(field, "name"))) throw invalid();
                        String decisionKey = only(field, "string").getTextContent();
                        if (!IDENTIFIER.matcher(decisionKey).matches()) throw invalid();
                        decisions.put(id, decisionKey);
                    } else if ("external-worker".equals(taskType)) {
                        workers.put(id, workerTask(element, extension));
                    } else throw invalid();
                }
                if ("intermediateCatchEvent".equals(kind)) {
                    Element timer = only(element, "timerEventDefinition");
                    Element duration = only(timer, "timeDuration");
                    try {
                        String literal = duration.getTextContent();
                        if (!literal.equals(literal.trim())) throw invalid();
                        Duration value = Duration.parse(literal);
                        if (value.compareTo(Duration.ofSeconds(1)) < 0 || value.compareTo(Duration.ofDays(365)) > 0) throw invalid();
                    } catch (RuntimeException badDuration) {
                        throw invalid();
                    }
                }
            }
        }
        if (nodes.isEmpty() || nodes.size() > 128 || flows.size() > 256) throw invalid();
        for (Element flow : flows) {
            FlowNode from = nodes.get(required(flow, "sourceRef"));
            FlowNode to = nodes.get(required(flow, "targetRef"));
            if (from == null || to == null) throw invalid();
            from.next.add(to);
            from.flows.add(flow);
            to.previous.add(from);
            to.incoming++;
        }
        if (decisions.size() > 1 || workers.size() > 16
                || (!decisions.isEmpty() || !workers.isEmpty()) && nodes.values().stream().anyMatch(n -> "parallelGateway".equals(n.kind))
                || decisions.isEmpty() && nodes.values().stream().anyMatch(n -> "exclusiveGateway".equals(n.kind))) throw invalid();
        FlowNode start = null;
        List<RouteCondition> routes = new ArrayList<>();
        int splits = 0;
        for (FlowNode node : nodes.values()) {
            int outgoing = node.next.size();
            switch (node.kind) {
                case "startEvent" -> {
                    if (start != null || node.incoming != 0 || outgoing != 1) throw invalid();
                    start = node;
                }
                case "endEvent" -> { if (node.incoming < 1 || node.incoming > 4 || outgoing != 0) throw invalid(); }
                case "userTask", "serviceTask", "intermediateCatchEvent" -> { if (node.incoming != 1 || outgoing != 1) throw invalid(); }
                case "parallelGateway" -> { if (node.incoming < 1 || node.incoming > 4 || outgoing < 1 || outgoing > 4) throw invalid(); }
                case "exclusiveGateway" -> {
                    if (node.incoming == 1 && outgoing >= 2 && outgoing <= 4) {
                        if (++splits > 1 || !decisions.containsKey(node.previous.getFirst().element.getAttribute("id"))) throw invalid();
                        routes.addAll(validateRoutes(node));
                    } else if (node.incoming < 2 || node.incoming > 4 || outgoing != 1 || node.element.hasAttribute("default")) throw invalid();
                }
                default -> throw invalid();
            }
            if (!"exclusiveGateway".equals(node.kind) || outgoing == 1) {
                for (Element flow : node.flows) if (!children(flow, "conditionExpression").isEmpty()) throw invalid();
            }
        }
        if (start == null) throw invalid();
        ArrayDeque<FlowNode> ready = new ArrayDeque<>();
        start.paths = 1;
        nodes.values().stream().filter(node -> node.incoming == 0).forEach(ready::add);
        int visited = 0;
        int traversals = 0;
        while (!ready.isEmpty()) {
            FlowNode node = ready.remove();
            if (node.paths == 0) throw invalid();
            visited++;
            for (FlowNode next : node.next) {
                traversals += node.paths;
                if (traversals > 256) throw invalid();
                next.paths += node.paths;
                if (next.paths > 128) throw invalid();
                if (++next.visitedInputs == next.incoming) ready.add(next);
            }
        }
        if (visited != nodes.size()) throw invalid();
        return new ValidatedProcessModel(Type.BPMN, key, bytes, Map.of(), Map.of(), List.of(), decisions, routes, workers);
    }

    private static WorkerTask workerTask(Element task, Element extension) {
        String topic = task.getAttributeNS(TIANJI_WORKER, "topic");
        String version = task.getAttributeNS(TIANJI_WORKER, "schemaVersion");
        if (!nativeWorkerTopic(topic, version).equals(task.getAttributeNS(FLOWABLE, "topic"))
                || !"true".equals(task.getAttributeNS(FLOWABLE, "doNotIncludeVariables"))) throw invalid();
        WorkerEffect effect;
        try { effect = WorkerEffect.valueOf(task.getAttributeNS(TIANJI_WORKER, "effectSafety")); }
        catch (IllegalArgumentException invalidEffect) { throw invalid(); }
        List<WorkerParameter> inputs = new ArrayList<>();
        List<WorkerParameter> outputs = new ArrayList<>();
        Set<String> inputNames = new HashSet<>();
        Set<String> outputNames = new HashSet<>();
        Set<String> outputTargets = new HashSet<>();
        for (Element parameter : children(extension)) {
            String kind = parameter.getLocalName();
            if (!"externalWorkerInParameter".equals(kind) && !"externalWorkerOutParameter".equals(kind)) throw invalid();
            String source = required(parameter, "source");
            String target = required(parameter, "target");
            validateVariable(source);
            validateVariable(target);
            ValueType type;
            try { type = ValueType.valueOf(parameter.getAttributeNS(TIANJI_WORKER, "type")); }
            catch (IllegalArgumentException invalidType) { throw invalid(); }
            if ("externalWorkerInParameter".equals(kind)) {
                if (!inputNames.add(target)) throw invalid();
                inputs.add(new WorkerParameter(source, target, type));
            } else {
                if (!outputNames.add(source) || !outputTargets.add(target)) throw invalid();
                outputs.add(new WorkerParameter(source, target, type));
            }
        }
        if (inputs.size() > 16 || outputs.size() > 16) throw invalid();
        return new WorkerTask(topic, version, effect, inputs, outputs);
    }

    public static String nativeWorkerTopic(String topic, String version) {
        if (topic == null || !IDENTIFIER.matcher(topic).matches() || version == null
                || !version.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}") || topic.length() + version.length() + 1 > 255) throw invalid();
        return topic + ":" + version;
    }

    /** Validates the currently supported task mappings against the release's process and decision schemas. */
    public void validateWorkerBindings(ValidatedProcessModel bpmn, Map<String, ValueType> processInputs,
                                       ValidatedProcessModel decision) {
        if (bpmn == null || bpmn.type() != Type.BPMN || processInputs == null
                || bpmn.decisionTasks().isEmpty() && decision != null
                || !bpmn.decisionTasks().isEmpty() && (decision == null || decision.type() != Type.DMN
                || !bpmn.decisionTasks().containsValue(decision.definitionKey()))) throw invalid();
        Set<String> outputs = new HashSet<>(processInputs.keySet());
        if (decision != null) outputs.addAll(decision.outputs().keySet());
        for (WorkerTask worker : bpmn.workerTasks().values()) {
            for (WorkerParameter input : worker.inputs()) {
                if (processInputs.get(input.source()) != input.type()) throw invalid();
            }
            for (WorkerParameter output : worker.outputs()) {
                if (!outputs.add(output.target())) throw invalid();
            }
        }
    }

    private static List<RouteCondition> validateRoutes(FlowNode gateway) {
        String defaultFlow = required(gateway.element, "default");
        List<RouteCondition> routes = new ArrayList<>();
        Set<String> literals = new HashSet<>();
        String variable = null;
        boolean foundDefault = false;
        for (Element flow : gateway.flows) {
            List<Element> conditions = children(flow, "conditionExpression");
            if (defaultFlow.equals(required(flow, "id"))) {
                if (!conditions.isEmpty()) throw invalid();
                foundDefault = true;
                continue;
            }
            if (conditions.size() != 1) throw invalid();
            String expression = conditions.getFirst().getTextContent();
            var match = ROUTE.matcher(expression);
            if (!match.matches()) throw invalid();
            String name = match.group(1);
            validateVariable(name);
            String literal = match.group(2);
            ValueType type = literal.startsWith("'") ? ValueType.STRING
                    : "true".equals(literal) || "false".equals(literal) ? ValueType.BOOLEAN : ValueType.INTEGER;
            if (variable != null && !variable.equals(name) || !literals.add(literal)) throw invalid();
            variable = name;
            routes.add(new RouteCondition(name, type, literal));
        }
        if (!foundDefault || routes.isEmpty()) throw invalid();
        return routes;
    }

    /** Checks that a BPMN task and its routes use only values declared by the same release. */
    public void validateDecisionBinding(ValidatedProcessModel bpmn, Map<String, ValueType> processInputs,
                                        ValidatedProcessModel decision) {
        if (bpmn == null || bpmn.type() != Type.BPMN || decision == null || decision.type() != Type.DMN
                || processInputs == null || bpmn.decisionTasks().size() != 1
                || !bpmn.decisionTasks().containsValue(decision.definitionKey())) throw invalid();
        for (var input : decision.inputs().entrySet()) {
            if (processInputs.get(input.getKey()) != input.getValue()) throw invalid();
        }
        if (decision.outputs().keySet().stream().anyMatch(processInputs::containsKey)) throw invalid();
        for (RouteCondition route : bpmn.routeConditions()) {
            if (decision.outputs().get(route.variable()) != route.type()) throw invalid();
        }
    }

    public ValidatedProcessModel validateDmn(byte[] source, String expectedKey) {
        byte[] bytes = copy(source, expectedKey);
        Document document = SafeModelXml.parse(bytes, false);
        Element root = document.getDocumentElement();
        validateTree(root, DMN, DMN_CHILDREN, DMN_ATTRIBUTES);
        if (!"definitions".equals(root.getLocalName())) throw invalid();
        Element decision = only(root, "decision");
        String key = required(decision, "id");
        if (!key.equals(expectedKey)) throw invalid();
        Element table = only(decision, "decisionTable");
        String hitPolicy = table.hasAttribute("hitPolicy") ? table.getAttribute("hitPolicy") : "UNIQUE";
        if (!Set.of("UNIQUE", "FIRST", "ANY").contains(hitPolicy)) throw invalid();
        List<Element> inputs = children(table, "input");
        List<Element> outputs = children(table, "output");
        List<Element> rules = children(table, "rule");
        if (inputs.isEmpty() || inputs.size() > 16 || outputs.isEmpty() || outputs.size() > 16
                || rules.isEmpty() || rules.size() > 256) throw invalid();
        Map<String, ValueType> inputTypes = new LinkedHashMap<>();
        for (Element input : inputs) {
            Element expression = only(input, "inputExpression");
            String variable = only(expression, "text").getTextContent();
            validateVariable(variable);
            if (inputTypes.putIfAbsent(variable, type(required(expression, "typeRef"))) != null) throw invalid();
        }
        Map<String, ValueType> outputTypes = new LinkedHashMap<>();
        for (Element output : outputs) {
            String name = required(output, "name");
            validateVariable(name);
            if (outputTypes.putIfAbsent(name, type(required(output, "typeRef"))) != null) throw invalid();
        }
        List<ValueType> inputOrder = new ArrayList<>(inputTypes.values());
        List<ValueType> outputOrder = new ArrayList<>(outputTypes.values());
        List<String> ruleIds = new ArrayList<>();
        for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
            Element rule = rules.get(ruleIndex);
            ruleIds.add(rule.hasAttribute("id") ? required(rule, "id") : "row:" + (ruleIndex + 1));
            List<Element> conditions = children(rule, "inputEntry");
            List<Element> results = children(rule, "outputEntry");
            if (conditions.size() != inputOrder.size() || results.size() != outputOrder.size()) throw invalid();
            for (int i = 0; i < conditions.size(); i++) validateLiteral(only(conditions.get(i), "text").getTextContent(), inputOrder.get(i), true);
            for (int i = 0; i < results.size(); i++) validateLiteral(only(results.get(i), "text").getTextContent(), outputOrder.get(i), false);
        }
        return new ValidatedProcessModel(Type.DMN, key, bytes, inputTypes, outputTypes, ruleIds);
    }

    private static void validateLiteral(String text, ValueType type, boolean condition) {
        String literal = switch (type) { case INTEGER -> INTEGER; case STRING -> STRING; case BOOLEAN -> "(?:true|false)"; };
        String operator = condition ? (type == ValueType.INTEGER ? "(?:(?:==|!=|<=|>=|<|>)\\s*)?" : "(?:(?:==|!=)\\s*)?") : "";
        if (!text.equals(text.trim()) || !text.matches(operator + literal)
                || condition && type == ValueType.STRING && (text.contains("fn_date") || text.contains("fn_now")
                || text.contains("fn_addDate") || text.contains("fn_subtractDate"))) throw invalid();
    }

    public static void validateVariable(String variable) {
        if (variable == null || !VARIABLE.matcher(variable).matches() || RESERVED.contains(variable)) throw invalid();
    }

    private static ValueType type(String value) {
        return switch (value) { case "number" -> ValueType.INTEGER; case "string" -> ValueType.STRING;
            case "boolean" -> ValueType.BOOLEAN; default -> throw invalid(); };
    }

    private static byte[] copy(byte[] source, String expectedKey) {
        if (source == null || source.length == 0 || source.length > SafeModelXml.MAX_BYTES
                || expectedKey == null || !IDENTIFIER.matcher(expectedKey).matches()) throw invalid();
        return source.clone();
    }

    private static void validateTree(Element root, String namespace, Map<String, Set<String>> children,
                                     Map<String, Set<String>> attributes) {
        walk(root, namespace, children, attributes, new HashSet<>());
    }

    private static void walk(Element element, String namespace, Map<String, Set<String>> children,
                             Map<String, Set<String>> attributes, Set<String> ids) {
        String kind = element.getLocalName();
        String elementNamespace = BPMN.equals(namespace) && Set.of("field", "string", "externalWorkerInParameter",
                "externalWorkerOutParameter").contains(kind) ? FLOWABLE : namespace;
        if (!elementNamespace.equals(element.getNamespaceURI()) || !attributes.containsKey(kind)) throw invalid();
        var attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attribute = attrs.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) continue;
            boolean flowableType = BPMN.equals(namespace) && "serviceTask".equals(kind)
                    && FLOWABLE.equals(attribute.getNamespaceURI()) && "type".equals(attribute.getLocalName());
            boolean flowableWorkerAttribute = BPMN.equals(namespace) && "serviceTask".equals(kind)
                    && FLOWABLE.equals(attribute.getNamespaceURI()) && Set.of("topic", "doNotIncludeVariables").contains(attribute.getLocalName());
            boolean workerVersion = BPMN.equals(namespace) && "serviceTask".equals(kind)
                    && TIANJI_WORKER.equals(attribute.getNamespaceURI()) && Set.of("topic", "schemaVersion", "effectSafety").contains(attribute.getLocalName());
            boolean workerType = BPMN.equals(namespace) && Set.of("externalWorkerInParameter", "externalWorkerOutParameter").contains(kind)
                    && TIANJI_WORKER.equals(attribute.getNamespaceURI()) && "type".equals(attribute.getLocalName());
            boolean expressionType = BPMN.equals(namespace) && "conditionExpression".equals(kind)
                    && XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(attribute.getNamespaceURI())
                    && "type".equals(attribute.getLocalName()) && "tFormalExpression".equals(attribute.getNodeValue());
            if (!flowableType && !flowableWorkerAttribute && !workerVersion && !workerType && !expressionType
                    && (attribute.getNamespaceURI() != null || !attributes.get(kind).contains(attribute.getNodeName()))) throw invalid();
            String value = attribute.getNodeValue();
            if (value.length() > 512 || value.contains("${") || value.contains("#{")) throw invalid();
            if ("id".equals(attribute.getNodeName()) && (!IDENTIFIER.matcher(value).matches() || !ids.add(value))) throw invalid();
        }
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) {
                if (!children.getOrDefault(kind, Set.of()).contains(child.getLocalName())) throw invalid();
                walk(child, namespace, children, attributes, ids);
            } else if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                if (!Set.of("text", "timeDuration", "string", "conditionExpression").contains(kind) && !node.getNodeValue().isBlank()) throw invalid();
            } else if (node.getNodeType() != Node.COMMENT_NODE || Set.of("text", "timeDuration", "string", "conditionExpression").contains(kind)) throw invalid();
        }
    }

    private static String required(Element element, String attribute) {
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
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) result.add(element);
        }
        return result;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Process model is invalid or not supported");
    }

    private static final class FlowNode {
        private final String kind;
        private final Element element;
        private final List<FlowNode> next = new ArrayList<>();
        private final List<FlowNode> previous = new ArrayList<>();
        private final List<Element> flows = new ArrayList<>();
        private int incoming;
        private int visitedInputs;
        private int paths;

        private FlowNode(String kind, Element element) { this.kind = kind; this.element = element; }
    }
}
