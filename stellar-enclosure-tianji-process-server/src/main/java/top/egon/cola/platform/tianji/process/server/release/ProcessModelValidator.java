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
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.MessageWait;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.CaseSource;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTask;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTaskField;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTaskKind;

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
    public ValidatedCaseModel validateCmmn(byte[] xml, String expectedKey) { return new CmmnModelValidator().validate(xml, expectedKey); }

    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String DMN = "http://www.omg.org/spec/DMN/20151101";
    private static final String FLOWABLE = "http://flowable.org/bpmn";
    private static final String TIANJI_WORKER = "urn:stellar-enclosure:tianji:worker:1";
    private static final String TIANJI_TASK = "urn:stellar-enclosure:tianji:task:1";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,127}");
    private static final Pattern VARIABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Set<String> RESERVED = Set.of("true", "false", "null", "empty", "and", "or", "not", "div", "mod",
            "eq", "ne", "lt", "gt", "le", "ge", "instanceof", "execution", "task", "authenticatedUserId");
    private static final String INTEGER = "-?(?:0|[1-9][0-9]{0,14})";
    private static final String STRING = "'[\\p{L}\\p{N} _.,:;!?/\\-]{0,128}'";
    private static final Pattern ROUTE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]{0,63}) == (" + STRING
            + "|" + INTEGER + "|true|false)\\}");
    private static final Map<String, Set<String>> BPMN_CHILDREN = Map.ofEntries(
            Map.entry("definitions", Set.of("process", "message")),
            Map.entry("process", Set.of("startEvent", "endEvent", "userTask", "serviceTask", "intermediateCatchEvent", "boundaryEvent", "parallelGateway", "exclusiveGateway", "eventBasedGateway", "sequenceFlow")),
            Map.entry("intermediateCatchEvent", Set.of("timerEventDefinition", "messageEventDefinition")),
            Map.entry("boundaryEvent", Set.of("timerEventDefinition")),
            Map.entry("timerEventDefinition", Set.of("timeDuration")),
            Map.entry("serviceTask", Set.of("extensionElements")),
            Map.entry("userTask", Set.of("extensionElements")),
            Map.entry("extensionElements", Set.of("field", "externalWorkerInParameter", "externalWorkerOutParameter", "taskLane", "taskField")),
            Map.entry("field", Set.of("string")), Map.entry("sequenceFlow", Set.of("conditionExpression")));
    private static final Map<String, Set<String>> BPMN_ATTRIBUTES = Map.ofEntries(
            Map.entry("definitions", Set.of("id", "name", "targetNamespace")),
            Map.entry("message", Set.of("id", "name")), Map.entry("messageEventDefinition", Set.of("id", "messageRef")),
            Map.entry("process", Set.of("id", "name", "isExecutable")),
            Map.entry("startEvent", Set.of("id", "name")), Map.entry("endEvent", Set.of("id", "name")),
            Map.entry("userTask", Set.of("id", "name")), Map.entry("parallelGateway", Set.of("id", "name")),
            Map.entry("boundaryEvent", Set.of("id", "name", "attachedToRef", "cancelActivity")),
            Map.entry("eventBasedGateway", Set.of("id", "name")),
            Map.entry("serviceTask", Set.of("id", "name")), Map.entry("extensionElements", Set.of()),
            Map.entry("field", Set.of("name")), Map.entry("string", Set.of()),
            Map.entry("externalWorkerInParameter", Set.of("source", "target")),
            Map.entry("externalWorkerOutParameter", Set.of("source", "target")),
            Map.entry("taskLane", Set.of("key", "roleIds")), Map.entry("taskField", Set.of("name", "target", "type")),
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
        Map<String, HumanTask> humans = new LinkedHashMap<>();
        Map<String, String> caseKeys = new LinkedHashMap<>();
        Map<String, String> messages = new LinkedHashMap<>();
        Set<String> messageNames = new HashSet<>();
        for (Element message : children(root, "message")) {
            String id = required(message, "id");
            String name = required(message, "name");
            if (!IDENTIFIER.matcher(name).matches() || !messageNames.add(name) || messages.size() >= 16) throw invalid();
            messages.put(id, name);
        }
        Map<String, String> messageRefs = new LinkedHashMap<>();
        Map<String, String> messageStatusVariables = new LinkedHashMap<>();
        Map<String, Duration> timers = new LinkedHashMap<>();
        Map<String, ValidatedProcessModel.HumanTaskTimer> humanTimers = new LinkedHashMap<>();
        for (Element element : children(process)) {
            String kind = element.getLocalName();
            if ("sequenceFlow".equals(kind)) {
                required(element, "id");
                flows.add(element);
            } else {
                String id = required(element, "id");
                nodes.put(id, new FlowNode(kind, element));
                if ("userTask".equals(kind) && (element.hasAttributeNS(TIANJI_TASK, "kind") || element.hasAttributeNS(TIANJI_TASK, "initialLane")
                        || element.hasAttributeNS(TIANJI_TASK, "laneVariable") || element.hasAttributeNS(TIANJI_TASK, "approvalVariable") || !children(element).isEmpty())) humans.put(id, humanTask(element));
                if ("serviceTask".equals(kind)) {
                    String taskType = element.getAttributeNS(FLOWABLE, "type");
                    if ("case".equals(taskType)) {
                        String caseKey = element.getAttributeNS(FLOWABLE, "caseDefinitionKey");
                        if (!IDENTIFIER.matcher(caseKey).matches() || !children(element).isEmpty()
                                || element.hasAttributeNS(FLOWABLE, "topic") || element.hasAttributeNS(FLOWABLE, "doNotIncludeVariables")
                                || element.hasAttributeNS(TIANJI_WORKER, "topic") || element.hasAttributeNS(TIANJI_WORKER, "schemaVersion") || element.hasAttributeNS(TIANJI_WORKER, "effectSafety")) throw invalid();
                        caseKeys.put(id, caseKey);
                    } else {
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
                }
                if ("intermediateCatchEvent".equals(kind)) {
                    if (children(element).size() != 1) throw invalid();
                    if (!children(element, "messageEventDefinition").isEmpty()) {
                        String ref = required(only(element, "messageEventDefinition"), "messageRef");
                        if (!IDENTIFIER.matcher(ref).matches() || !messages.containsKey(ref) || messageRefs.containsValue(ref)
                                || element.hasAttributeNS(TIANJI_WORKER, "workerRef") && !IDENTIFIER.matcher(element.getAttributeNS(TIANJI_WORKER, "workerRef")).matches()
                                || !element.hasAttributeNS(TIANJI_WORKER, "workerRef") && element.hasAttributeNS(TIANJI_WORKER, "statusVariable")) throw invalid();
                        messageRefs.put(id, ref);
                        if (element.hasAttributeNS(TIANJI_WORKER, "statusVariable")) {
                            String variable = element.getAttributeNS(TIANJI_WORKER, "statusVariable");
                            validateVariable(variable);
                            messageStatusVariables.put(id, variable);
                        }
                    } else {
                        if (element.hasAttributeNS(TIANJI_WORKER, "workerRef") || element.hasAttributeNS(TIANJI_WORKER, "statusVariable")) throw invalid();
                        Element duration = only(only(element, "timerEventDefinition"), "timeDuration");
                        timers.put(id, timerDuration(duration));
                    }
                }
                if ("boundaryEvent".equals(kind)) {
                    if (!"true".equals(element.getAttribute("cancelActivity")) || children(element).size() != 1) throw invalid();
                    String attached = required(element, "attachedToRef"); var literal = only(only(element, "timerEventDefinition"), "timeDuration"); var duration = timerDuration(literal);
                    if (duration.getNano() != 0 || !duration.toString().equals(literal.getTextContent())) throw invalid();
                    timers.put(id, duration);
                    humanTimers.put(id, new ValidatedProcessModel.HumanTaskTimer(attached, duration));
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
        if (decisions.size() > 1 || workers.size() > 16 || caseKeys.size() > 16 || humans.size() > 16 || humans.values().stream().anyMatch(task -> task.laneVariable() != null) && decisions.size() != 1
                || (!decisions.isEmpty() || !workers.isEmpty()) && nodes.values().stream().anyMatch(n -> "parallelGateway".equals(n.kind))
                || decisions.isEmpty() && messageRefs.isEmpty() && humans.isEmpty() && nodes.values().stream().anyMatch(n -> "exclusiveGateway".equals(n.kind))) throw invalid();
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
                case "boundaryEvent" -> { if (node.incoming != 0 || outgoing != 1) throw invalid(); }
                case "parallelGateway" -> { if (node.incoming < 1 || node.incoming > 4 || outgoing < 1 || outgoing > 4) throw invalid(); }
                case "eventBasedGateway" -> {
                    if (node.incoming != 1 || outgoing != 2 || !workers.containsKey(node.previous.getFirst().element.getAttribute("id")) && !caseKeys.containsKey(node.previous.getFirst().element.getAttribute("id"))
                            || node.next.stream().anyMatch(next -> !"intermediateCatchEvent".equals(next.kind))
                            || node.next.stream().filter(next -> messageRefs.containsKey(next.element.getAttribute("id"))).count() != 1
                            || node.next.stream().filter(next -> timers.containsKey(next.element.getAttribute("id"))).count() != 1) throw invalid();
                }
                case "exclusiveGateway" -> {
                    if (node.incoming == 1 && outgoing >= 2 && outgoing <= 4) {
                        String previous = node.previous.getFirst().element.getAttribute("id");
                        if (++splits > 1 || !decisions.containsKey(previous) && !messageStatusVariables.containsKey(previous) && !humans.containsKey(previous)) throw invalid();
                        var conditions = validateRoutes(node);
                        if (humans.containsKey(previous) && conditions.stream().anyMatch(route -> humans.get(previous).fields().stream()
                                .noneMatch(field -> field.target().equals(route.variable()) && field.type() == route.type()))) throw invalid();
                        if (decisions.containsKey(previous) && conditions.stream().anyMatch(route -> humans.values().stream().flatMap(task -> task.fields().stream())
                                .anyMatch(field -> field.target().equals(route.variable())))) throw invalid();
                        if (messageStatusVariables.containsKey(previous) && conditions.stream().anyMatch(route ->
                                !messageStatusVariables.get(previous).equals(route.variable()) || route.type() != ValueType.STRING
                                        || !Set.of("'COMPLETED'", "'FAILED'", "'CANCELLED'", "'TIMED_OUT'").contains(route.literal()))) throw invalid();
                        routes.addAll(conditions);
                    } else if (node.incoming < 2 || node.incoming > 4 || outgoing != 1 || node.element.hasAttribute("default")) throw invalid();
                }
                default -> throw invalid();
            }
            if (!"exclusiveGateway".equals(node.kind) || outgoing == 1) {
                for (Element flow : node.flows) if (!children(flow, "conditionExpression").isEmpty()) throw invalid();
            }
        }
        if (messages.size() != messageRefs.size()) throw invalid();
        Map<String, MessageWait> waits = new LinkedHashMap<>();
        Set<String> waitingWorkers = new HashSet<>();
        Map<String, CaseSource> cases = new LinkedHashMap<>();
        for (var entry : messageRefs.entrySet()) {
            FlowNode message = nodes.get(entry.getKey());
            FlowNode gateway = message.previous.getFirst();
            if (!"eventBasedGateway".equals(gateway.kind)) throw invalid();
            String sourceId = gateway.previous.getFirst().element.getAttribute("id");
            String timer = gateway.next.stream().map(next -> next.element.getAttribute("id")).filter(timers::containsKey)
                    .findFirst().orElseThrow(ProcessModelValidator::invalid);
            if (caseKeys.containsKey(sourceId)) {
                if (message.element.hasAttributeNS(TIANJI_WORKER, "workerRef") || message.element.hasAttributeNS(TIANJI_WORKER, "statusVariable")
                        || cases.put(sourceId, new CaseSource(caseKeys.get(sourceId), entry.getKey(), messages.get(entry.getValue()), timer, timers.get(timer))) != null) throw invalid();
            } else {
                String worker = message.element.getAttributeNS(TIANJI_WORKER, "workerRef");
                if (!workers.containsKey(worker) || !worker.equals(sourceId) || !waitingWorkers.add(worker)) throw invalid();
                waits.put(entry.getKey(), new MessageWait(messages.get(entry.getValue()), worker, timer, timers.get(timer), messageStatusVariables.get(entry.getKey())));
            }
        }
        if (!cases.keySet().equals(caseKeys.keySet())) throw invalid();
        var timedTasks = new HashSet<String>();
        for (var entry : humanTimers.entrySet()) {
            String attached = entry.getValue().taskElementId(); if (!humans.containsKey(attached) || !timedTasks.add(attached)) throw invalid();
            // The native boundary has an implicit edge from its attached task, not an incoming sequenceFlow.
            var task = nodes.get(attached); var boundary = nodes.get(entry.getKey()); task.next.add(boundary); boundary.previous.add(task); boundary.incoming++;
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
            node.decisionBefore = decisions.containsKey(node.element.getAttribute("id")) || !node.previous.isEmpty() && node.previous.stream().allMatch(previous -> previous.decisionBefore);
            node.waitBefore = !node.previous.isEmpty() && node.previous.stream().allMatch(previous -> previous.waitBefore
                    || "userTask".equals(previous.kind) || "intermediateCatchEvent".equals(previous.kind) || workers.containsKey(previous.element.getAttribute("id")));
            if (caseKeys.containsKey(node.element.getAttribute("id")) && !node.waitBefore) throw invalid();
            var human = humans.get(node.element.getAttribute("id"));
            if (human != null && human.laneVariable() != null && !node.decisionBefore) throw invalid();
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
        return new ValidatedProcessModel(Type.BPMN, key, bytes, Map.of(), Map.of(), List.of(), decisions, routes,
                workers, waits, humans, humanTimers, cases, Map.copyOf(timers));
    }

    private static Duration timerDuration(Element duration) {
        try { String literal = duration.getTextContent(); if (!literal.equals(literal.trim())) throw invalid(); var value = Duration.parse(literal);
            if (value.compareTo(Duration.ofSeconds(1)) < 0 || value.compareTo(Duration.ofDays(365)) > 0) throw invalid(); return value;
        } catch (RuntimeException badDuration) { throw invalid(); }
    }

    static HumanTask humanTask(Element task) {
        HumanTaskKind kind;
        try { kind = HumanTaskKind.valueOf(task.getAttributeNS(TIANJI_TASK, "kind")); }
        catch (IllegalArgumentException unknown) { throw invalid(); }
        String initial = task.hasAttributeNS(TIANJI_TASK, "initialLane") ? task.getAttributeNS(TIANJI_TASK, "initialLane") : null;
        String variable = task.hasAttributeNS(TIANJI_TASK, "laneVariable") ? task.getAttributeNS(TIANJI_TASK, "laneVariable") : null;
        String approval = task.hasAttributeNS(TIANJI_TASK, "approvalVariable") ? task.getAttributeNS(TIANJI_TASK, "approvalVariable") : null;
        if ((initial == null) == (variable == null) || kind == HumanTaskKind.APPROVAL != (approval != null)) throw invalid();
        if (initial != null && !IDENTIFIER.matcher(initial).matches()) throw invalid();
        if (variable != null) humanVariable(variable); if (approval != null) humanVariable(approval);
        var lanes = new LinkedHashMap<String, Set<String>>(); var roleSets = new HashSet<Set<String>>(); var fields = new ArrayList<HumanTaskField>();
        var names = new HashSet<String>(); var targets = new HashSet<String>();
        Element extension = only(task, "extensionElements");
        for (var element : children(extension)) {
            if ("taskLane".equals(element.getLocalName())) {
                String key = required(element, "key"); if (!IDENTIFIER.matcher(key).matches() || lanes.size() >= 8) throw invalid();
                String[] identifiers = required(element, "roleIds").split(",", -1); if (identifiers.length > 16) throw invalid();
                var roles = new HashSet<String>();
                for (var id : identifiers) {
                    try { if (!id.matches("[1-9][0-9]{0,18}") || Long.parseLong(id) < 1 || !roles.add(id)) throw invalid(); }
                    catch (NumberFormatException malformed) { throw invalid(); }
                }
                if (lanes.putIfAbsent(key, Set.copyOf(roles)) != null || !roleSets.add(Set.copyOf(roles))) throw invalid();
            } else if ("taskField".equals(element.getLocalName())) {
                String name = required(element, "name"), target = required(element, "target"); humanVariable(name); humanVariable(target);
                ValueType type;
                try { type = ValueType.valueOf(required(element, "type")); }
                catch (IllegalArgumentException unknown) { throw invalid(); }
                if (fields.size() >= 16 || !names.add(name) || !targets.add(target)) throw invalid(); fields.add(new HumanTaskField(name, target, type));
            } else throw invalid();
        }
        if (lanes.isEmpty() || initial != null && !lanes.containsKey(initial) || variable != null && targets.contains(variable)
                || approval != null && fields.stream().noneMatch(field -> field.target().equals(approval) && field.type() == ValueType.BOOLEAN)) throw invalid();
        return new HumanTask(kind, lanes, initial, variable, approval, fields);
    }

    private static void humanVariable(String variable) { validateVariable(variable); if (variable.startsWith("__tianji")) throw invalid(); }

    /** Form targets and a DMN lane selector cannot be supplied or overwritten through another release input/output path. */
    public void validateHumanTaskBindings(ValidatedProcessModel bpmn, Map<String, ValueType> processInputs, ValidatedProcessModel decision) {
        if (bpmn == null || bpmn.type() != Type.BPMN || processInputs == null || decision != null && decision.type() != Type.DMN) throw invalid();
        if (bpmn.humanTasks().isEmpty()) return;
        var occupied = new HashSet<>(processInputs.keySet());
        if (decision != null) occupied.addAll(decision.outputs().keySet());
        var otherOutputs = new HashSet<String>();
        for (var worker : bpmn.workerTasks().values()) for (var output : worker.outputs()) otherOutputs.add(output.target());
        for (var wait : bpmn.messageWaits().values()) if (wait.statusVariable() != null) otherOutputs.add(wait.statusVariable());
        occupied.addAll(otherOutputs);
        var outputs = new LinkedHashMap<String, ValueType>();
        for (var task : bpmn.humanTasks().values()) {
            for (var field : task.fields()) { if (!occupied.add(field.target()) || outputs.putIfAbsent(field.target(), field.type()) != null) throw invalid(); }
            if (task.laneVariable() != null) {
                if (decision == null || processInputs.containsKey(task.laneVariable()) || otherOutputs.contains(task.laneVariable())
                        || !bpmn.decisionTasks().containsValue(decision.definitionKey()) || decision.outputs().get(task.laneVariable()) != ValueType.STRING
                        || !task.lanes().keySet().containsAll(decisionStringValues(decision, task.laneVariable()))) throw invalid();
            }
        }
        for (var route : bpmn.routeConditions()) if (outputs.containsKey(route.variable()) && outputs.get(route.variable()) != route.type()) throw invalid();
    }

    private static Set<String> decisionStringValues(ValidatedProcessModel decision, String variable) {
        var root = SafeModelXml.parse(decision.xml(), false).getDocumentElement(); var table = only(only(root, "decision"), "decisionTable");
        var outputs = children(table, "output"); int index = -1;
        for (int number = 0; number < outputs.size(); number++) if (variable.equals(required(outputs.get(number), "name"))) index = number;
        if (index < 0) throw invalid(); var values = new HashSet<String>();
        for (var rule : children(table, "rule")) {
            String literal = only(children(rule, "outputEntry").get(index), "text").getTextContent();
            if (literal.length() < 2 || literal.charAt(0) != '\'' || literal.charAt(literal.length() - 1) != '\'') throw invalid(); values.add(literal.substring(1, literal.length() - 1));
        }
        if (values.isEmpty()) throw invalid(); return Set.copyOf(values);
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
        for (MessageWait wait : bpmn.messageWaits().values()) {
            if (wait.statusVariable() != null && !outputs.add(wait.statusVariable())) throw invalid();
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
            boolean terminalStatus = route.type() == ValueType.STRING && bpmn.messageWaits().values().stream()
                    .anyMatch(wait -> route.variable().equals(wait.statusVariable()));
            boolean humanOutput = bpmn.humanTasks().values().stream().flatMap(task -> task.fields().stream()).anyMatch(field -> field.target().equals(route.variable()) && field.type() == route.type());
            if (!terminalStatus && !humanOutput && decision.outputs().get(route.variable()) != route.type()) throw invalid();
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
        if (BPMN.equals(namespace) && Set.of("taskLane", "taskField").contains(kind)) elementNamespace = TIANJI_TASK;
        if (!elementNamespace.equals(element.getNamespaceURI()) || !attributes.containsKey(kind)) throw invalid();
        var attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attribute = attrs.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) continue;
            boolean flowableType = BPMN.equals(namespace) && "serviceTask".equals(kind)
                    && FLOWABLE.equals(attribute.getNamespaceURI()) && "type".equals(attribute.getLocalName());
            boolean caseKey = BPMN.equals(namespace) && "serviceTask".equals(kind) && "case".equals(element.getAttributeNS(FLOWABLE, "type"))
                    && FLOWABLE.equals(attribute.getNamespaceURI()) && "caseDefinitionKey".equals(attribute.getLocalName());
            boolean flowableWorkerAttribute = BPMN.equals(namespace) && "serviceTask".equals(kind)
                    && FLOWABLE.equals(attribute.getNamespaceURI()) && Set.of("topic", "doNotIncludeVariables").contains(attribute.getLocalName());
            boolean workerVersion = BPMN.equals(namespace) && "serviceTask".equals(kind)
                    && TIANJI_WORKER.equals(attribute.getNamespaceURI()) && Set.of("topic", "schemaVersion", "effectSafety").contains(attribute.getLocalName());
            boolean workerType = BPMN.equals(namespace) && Set.of("externalWorkerInParameter", "externalWorkerOutParameter").contains(kind)
                    && TIANJI_WORKER.equals(attribute.getNamespaceURI()) && "type".equals(attribute.getLocalName());
            boolean workerWait = BPMN.equals(namespace) && "intermediateCatchEvent".equals(kind)
                    && TIANJI_WORKER.equals(attribute.getNamespaceURI()) && Set.of("workerRef", "statusVariable").contains(attribute.getLocalName());
            boolean expressionType = BPMN.equals(namespace) && "conditionExpression".equals(kind)
                    && XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(attribute.getNamespaceURI())
                    && "type".equals(attribute.getLocalName()) && "tFormalExpression".equals(attribute.getNodeValue());
            boolean humanTask = BPMN.equals(namespace) && "userTask".equals(kind) && TIANJI_TASK.equals(attribute.getNamespaceURI())
                    && Set.of("kind", "initialLane", "laneVariable", "approvalVariable").contains(attribute.getLocalName());
            if (!flowableType && !caseKey && !flowableWorkerAttribute && !workerVersion && !workerType && !workerWait && !expressionType && !humanTask
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
        private boolean decisionBefore;
        private boolean waitBefore;

        private FlowNode(String kind, Element element) { this.kind = kind; this.element = element; }
    }
}
