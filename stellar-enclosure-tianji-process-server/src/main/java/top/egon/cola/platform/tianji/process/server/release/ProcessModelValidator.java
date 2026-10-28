package top.egon.cola.platform.tianji.process.server.release;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;

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

/** Validates the deliberately small, non-scriptable release subset supported by the first process API. */
public final class ProcessModelValidator {
    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String DMN = "http://www.omg.org/spec/DMN/20151101";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,127}");
    private static final Pattern VARIABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Set<String> RESERVED = Set.of("true", "false", "null", "empty", "and", "or", "not", "div", "mod",
            "eq", "ne", "lt", "gt", "le", "ge", "instanceof", "execution", "task", "authenticatedUserId");
    private static final String INTEGER = "-?(?:0|[1-9][0-9]{0,14})";
    private static final String STRING = "'[\\p{L}\\p{N} _.,:;!?/\\-]{0,128}'";
    private static final Map<String, Set<String>> BPMN_CHILDREN = Map.ofEntries(
            Map.entry("definitions", Set.of("process")),
            Map.entry("process", Set.of("startEvent", "endEvent", "userTask", "intermediateCatchEvent", "parallelGateway", "sequenceFlow")),
            Map.entry("intermediateCatchEvent", Set.of("timerEventDefinition")),
            Map.entry("timerEventDefinition", Set.of("timeDuration")));
    private static final Map<String, Set<String>> BPMN_ATTRIBUTES = Map.ofEntries(
            Map.entry("definitions", Set.of("id", "name", "targetNamespace")),
            Map.entry("process", Set.of("id", "name", "isExecutable")),
            Map.entry("startEvent", Set.of("id", "name")), Map.entry("endEvent", Set.of("id", "name")),
            Map.entry("userTask", Set.of("id", "name")), Map.entry("parallelGateway", Set.of("id", "name")),
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
        for (Element element : children(process)) {
            String kind = element.getLocalName();
            if ("sequenceFlow".equals(kind)) {
                required(element, "id");
                flows.add(element);
            } else {
                String id = required(element, "id");
                nodes.put(id, new FlowNode(kind));
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
            to.incoming++;
        }
        FlowNode start = null;
        for (FlowNode node : nodes.values()) {
            int outgoing = node.next.size();
            switch (node.kind) {
                case "startEvent" -> {
                    if (start != null || node.incoming != 0 || outgoing != 1) throw invalid();
                    start = node;
                }
                case "endEvent" -> { if (node.incoming < 1 || node.incoming > 4 || outgoing != 0) throw invalid(); }
                case "userTask", "intermediateCatchEvent" -> { if (node.incoming != 1 || outgoing != 1) throw invalid(); }
                case "parallelGateway" -> { if (node.incoming < 1 || node.incoming > 4 || outgoing < 1 || outgoing > 4) throw invalid(); }
                default -> throw invalid();
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
        return new ValidatedProcessModel(Type.BPMN, key, bytes, Map.of(), Map.of());
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
        for (Element rule : rules) {
            List<Element> conditions = children(rule, "inputEntry");
            List<Element> results = children(rule, "outputEntry");
            if (conditions.size() != inputOrder.size() || results.size() != outputOrder.size()) throw invalid();
            for (int i = 0; i < conditions.size(); i++) validateLiteral(only(conditions.get(i), "text").getTextContent(), inputOrder.get(i), true);
            for (int i = 0; i < results.size(); i++) validateLiteral(only(results.get(i), "text").getTextContent(), outputOrder.get(i), false);
        }
        return new ValidatedProcessModel(Type.DMN, key, bytes, inputTypes, outputTypes);
    }

    private static void validateLiteral(String text, ValueType type, boolean condition) {
        String literal = switch (type) { case INTEGER -> INTEGER; case STRING -> STRING; case BOOLEAN -> "(?:true|false)"; };
        String operator = condition ? (type == ValueType.INTEGER ? "(?:(?:==|!=|<=|>=|<|>)\\s*)?" : "(?:(?:==|!=)\\s*)?") : "";
        if (!text.equals(text.trim()) || !text.matches(operator + literal)
                || condition && type == ValueType.STRING && (text.contains("fn_date") || text.contains("fn_now")
                || text.contains("fn_addDate") || text.contains("fn_subtractDate"))) throw invalid();
    }

    private static void validateVariable(String variable) {
        if (!VARIABLE.matcher(variable).matches() || RESERVED.contains(variable)) throw invalid();
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
        if (!namespace.equals(element.getNamespaceURI()) || !attributes.containsKey(kind)) throw invalid();
        var attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attribute = attrs.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) continue;
            if (attribute.getNamespaceURI() != null || !attributes.get(kind).contains(attribute.getNodeName())) throw invalid();
            String value = attribute.getNodeValue();
            if (value.length() > 512 || value.contains("${") || value.contains("#{")) throw invalid();
            if ("id".equals(attribute.getNodeName()) && (!IDENTIFIER.matcher(value).matches() || !ids.add(value))) throw invalid();
        }
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) {
                if (!children.getOrDefault(kind, Set.of()).contains(child.getLocalName())) throw invalid();
                walk(child, namespace, children, attributes, ids);
            } else if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                if (!"text".equals(kind) && !"timeDuration".equals(kind) && !node.getNodeValue().isBlank()) throw invalid();
            } else if (node.getNodeType() != Node.COMMENT_NODE || "text".equals(kind) || "timeDuration".equals(kind)) throw invalid();
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
        private final List<FlowNode> next = new ArrayList<>();
        private int incoming;
        private int visitedInputs;
        private int paths;

        private FlowNode(String kind) { this.kind = kind; }
    }
}
