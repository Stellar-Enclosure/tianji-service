package top.egon.cola.platform.tianji.process.server.release;

import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.dmn.xml.converter.DmnXMLConverter;
import org.flowable.validation.ProcessValidatorFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler.CompiledModelEvidenceVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler.CompiledControlBindingsVO;
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
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerBusinessError;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerCompensationVO;

import javax.xml.XMLConstants;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates bounded, non-scriptable process models; publication applies additional feature gates. */
@Slf4j
public final class ProcessModelValidator {
    public ValidatedCaseModel validateCmmn(byte[] xml, String expectedKey) { return new CmmnModelValidator().validate(xml, expectedKey); }

    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String DMN = "http://www.omg.org/spec/DMN/20151101";
    private static final String FLOWABLE = "http://flowable.org/bpmn";
    private static final String TIANJI_WORKER = "urn:stellar-enclosure:tianji:worker:1";
    private static final String TIANJI_TASK = "urn:stellar-enclosure:tianji:task:1";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,127}");
    private static final Pattern VARIABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Set<String> RESERVED = Set.of("true", "false", "null", "empty", "and", "or", "not", "div", "mod",
            "eq", "ne", "lt", "gt", "le", "ge", "instanceof", "execution", "task", "authenticatedUserId");
    private static final String INTEGER = "-?(?:0|[1-9][0-9]{0,14})";
    private static final String STRING = "'[\\p{L}\\p{N} _.,:;!?/\\-]{0,128}'";
    private static final Pattern ROUTE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]{0,63}) == (" + STRING
            + "|" + INTEGER + "|true|false)\\}");
    private static final Map<String, Set<String>> BPMN_CHILDREN = Map.ofEntries(
            Map.entry("definitions", Set.of("process", "message", "error")),
            Map.entry("process", Set.of("startEvent", "endEvent", "userTask", "serviceTask", "intermediateCatchEvent",
                    "intermediateThrowEvent", "boundaryEvent", "parallelGateway", "exclusiveGateway", "eventBasedGateway", "sequenceFlow", "association")),
            Map.entry("intermediateCatchEvent", Set.of("timerEventDefinition", "messageEventDefinition")),
            Map.entry("intermediateThrowEvent", Set.of("compensateEventDefinition")),
            Map.entry("boundaryEvent", Set.of("timerEventDefinition", "errorEventDefinition", "compensateEventDefinition")),
            Map.entry("timerEventDefinition", Set.of("timeDuration")),
            Map.entry("error", Set.of()), Map.entry("errorEventDefinition", Set.of()),
            Map.entry("serviceTask", Set.of("extensionElements")),
            Map.entry("userTask", Set.of("extensionElements")),
            Map.entry("extensionElements", Set.of("field", "externalWorkerInParameter", "externalWorkerOutParameter", "taskLane", "taskField")),
            Map.entry("field", Set.of("string")), Map.entry("sequenceFlow", Set.of("conditionExpression")));
    private static final Map<String, Set<String>> BPMN_ATTRIBUTES = Map.ofEntries(
            Map.entry("definitions", Set.of("id", "name", "targetNamespace")),
            Map.entry("message", Set.of("id", "name")), Map.entry("messageEventDefinition", Set.of("id", "messageRef")),
            Map.entry("error", Set.of("id", "errorCode")), Map.entry("errorEventDefinition", Set.of("id", "errorRef")),
            Map.entry("process", Set.of("id", "name", "isExecutable")),
            Map.entry("startEvent", Set.of("id", "name")), Map.entry("endEvent", Set.of("id", "name")),
            Map.entry("userTask", Set.of("id", "name")), Map.entry("parallelGateway", Set.of("id", "name")),
            Map.entry("boundaryEvent", Set.of("id", "name", "attachedToRef", "cancelActivity")),
            Map.entry("eventBasedGateway", Set.of("id", "name")),
            Map.entry("serviceTask", Set.of("id", "name", "isForCompensation")), Map.entry("extensionElements", Set.of()),
            Map.entry("field", Set.of("name")), Map.entry("string", Set.of()),
            Map.entry("externalWorkerInParameter", Set.of("source", "target")),
            Map.entry("externalWorkerOutParameter", Set.of("source", "target")),
            Map.entry("taskLane", Set.of("key", "roleIds")), Map.entry("taskField", Set.of("name", "target", "type")),
            Map.entry("exclusiveGateway", Set.of("id", "name", "default")), Map.entry("conditionExpression", Set.of()),
            Map.entry("intermediateCatchEvent", Set.of("id", "name")),
            Map.entry("intermediateThrowEvent", Set.of("id")),
            Map.entry("compensateEventDefinition", Set.of("activityRef", "waitForCompletion")),
            Map.entry("timerEventDefinition", Set.of("id")), Map.entry("timeDuration", Set.of("id")),
            Map.entry("sequenceFlow", Set.of("id", "name", "sourceRef", "targetRef")),
            Map.entry("association", Set.of("id", "sourceRef", "targetRef", "associationDirection")));
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

    public Optional<PlainMigrationTimerVO> plainMigrationTimer(ValidatedProcessModel model) {
        if (model == null || model.type() != Type.BPMN || model.timerWaits().size() != 1) return Optional.empty();
        Element definitions = SafeModelXml.parse(model.xml(), true).getDocumentElement();
        var definitionsChildren = children(definitions);
        if (!plainElement(definitions, "definitions") || definitionsChildren.size() != 1) return Optional.empty();
        Element process = definitionsChildren.getFirst();
        if (!plainElement(process, "process") || !"true".equals(process.getAttribute("isExecutable"))
                || !model.definitionKey().equals(process.getAttribute("id"))) return Optional.empty();
        var nodes = children(process);
        if (nodes.size() != 5) return Optional.empty();

        var starts = children(process, "startEvent");
        var ends = children(process, "endEvent");
        var catches = children(process, "intermediateCatchEvent");
        var flows = children(process, "sequenceFlow");
        if (starts.size() != 1 || ends.size() != 1 || catches.size() != 1 || flows.size() != 2) return Optional.empty();
        Element start = starts.getFirst(), end = ends.getFirst(), timer = catches.getFirst();
        if (!plainElement(start, "startEvent") || !plainElement(end, "endEvent")
                || !plainElement(timer, "intermediateCatchEvent")
                || !children(start).isEmpty() || !children(end).isEmpty()) return Optional.empty();
        String startId = start.getAttribute("id"), endId = end.getAttribute("id"), timerId = timer.getAttribute("id");
        if (!IDENTIFIER.matcher(startId).matches() || !IDENTIFIER.matcher(endId).matches()
                || !IDENTIFIER.matcher(timerId).matches() || startId.equals(timerId) || timerId.equals(endId)
                || startId.equals(endId) || !isPlainFlowPair(flows, startId, timerId, endId)) return Optional.empty();

        var timerChildren = children(timer);
        if (timerChildren.size() != 1 || !plainElement(timerChildren.getFirst(), "timerEventDefinition")) return Optional.empty();
        var durationChildren = children(timerChildren.getFirst());
        if (durationChildren.size() != 1 || !plainElement(durationChildren.getFirst(), "timeDuration")
                || !children(durationChildren.getFirst()).isEmpty()) return Optional.empty();
        Duration duration = model.timerWaits().get(timerId);
        if (duration == null || duration.getNano() != 0 || !duration.toString().equals(durationChildren.getFirst().getTextContent())) {
            return Optional.empty();
        }
        return Optional.of(new PlainMigrationTimerVO(timerId, duration));
    }

    private static boolean isPlainFlowPair(List<Element> flows, String start, String timer, String end) {
        Element first = flows.getFirst(), second = flows.getLast();
        return plainFlow(first, start, timer) && plainFlow(second, timer, end)
                || plainFlow(first, timer, end) && plainFlow(second, start, timer);
    }

    private static boolean plainFlow(Element element, String source, String target) {
        return plainElement(element, "sequenceFlow") && children(element).isEmpty()
                && source.equals(element.getAttribute("sourceRef")) && target.equals(element.getAttribute("targetRef"));
    }

    private static boolean plainElement(Element element, String localName) {
        if (!BPMN.equals(element.getNamespaceURI()) || !localName.equals(element.getLocalName())) return false;
        var attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            var attribute = attributes.item(index);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) continue;
            if (attribute.getNamespaceURI() != null || !BPMN_ATTRIBUTES.get(localName).contains(attribute.getLocalName())) return false;
        }
        return true;
    }

    public record PlainMigrationTimerVO(String activityId, Duration duration) { }

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
        Map<String, String> errorCodes = new LinkedHashMap<>();
        for (Element error : children(root, "error")) {
            String id = required(error, "id");
            String code = required(error, "errorCode");
            if (!ERROR_CODE.matcher(code).matches() || errorCodes.size() >= 16
                    || errorCodes.putIfAbsent(id, code) != null) throw invalid();
        }
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
        Map<String, String> errorBoundaryRefs = new LinkedHashMap<>();
        Map<String, String> errorBoundaryAttachments = new LinkedHashMap<>();
        Map<String, String> compensationBoundaryAttachments = new LinkedHashMap<>();
        List<Element> associations = new ArrayList<>();
        List<String> compensationWorkerIds = new ArrayList<>();
        List<String> compensationThrowIds = new ArrayList<>();
        for (Element element : children(process)) {
            String kind = element.getLocalName();
            if ("sequenceFlow".equals(kind)) {
                required(element, "id");
                flows.add(element);
            } else if ("association".equals(kind)) {
                required(element, "id");
                associations.add(element);
            } else {
                String id = required(element, "id");
                nodes.put(id, new FlowNode(kind, element));
                if ("intermediateThrowEvent".equals(kind)) compensationThrowIds.add(id);
                if ("userTask".equals(kind) && (element.hasAttributeNS(TIANJI_TASK, "kind") || element.hasAttributeNS(TIANJI_TASK, "initialLane")
                        || element.hasAttributeNS(TIANJI_TASK, "laneVariable") || element.hasAttributeNS(TIANJI_TASK, "approvalVariable") || !children(element).isEmpty())) humans.put(id, humanTask(element));
                if ("serviceTask".equals(kind)) {
                    String taskType = element.getAttributeNS(FLOWABLE, "type");
                    if (element.hasAttribute("isForCompensation")) {
                        if (!"external-worker".equals(taskType) || !"true".equals(element.getAttribute("isForCompensation"))) throw invalid();
                        compensationWorkerIds.add(id);
                    }
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
                    List<Element> definitions = children(element);
                    if (definitions.size() != 1) throw invalid();
                    Element definition = definitions.getFirst();
                    String attached = required(element, "attachedToRef");
                    if ("compensateEventDefinition".equals(definition.getLocalName())) {
                        if (!"false".equals(element.getAttribute("cancelActivity"))
                                || definition.hasAttribute("activityRef") || definition.hasAttribute("waitForCompletion")) throw invalid();
                        compensationBoundaryAttachments.put(id, attached);
                        continue;
                    }
                    if (!"true".equals(element.getAttribute("cancelActivity"))) throw invalid();
                    if (!children(element, "timerEventDefinition").isEmpty()) {
                        var literal = only(only(element, "timerEventDefinition"), "timeDuration"); var duration = timerDuration(literal);
                        if (duration.getNano() != 0 || !duration.toString().equals(literal.getTextContent())) throw invalid();
                        timers.put(id, duration);
                        humanTimers.put(id, new ValidatedProcessModel.HumanTaskTimer(attached, duration));
                    } else {
                        String ref = required(only(element, "errorEventDefinition"), "errorRef");
                        if (!IDENTIFIER.matcher(ref).matches() || !errorCodes.containsKey(ref)
                                || errorBoundaryRefs.putIfAbsent(id, ref) != null
                                || errorBoundaryAttachments.putIfAbsent(id, attached) != null) throw invalid();
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
            to.incomingEdges.add(new IncomingEdge(from, required(flow, "id")));
            to.incoming++;
        }
        if (decisions.size() > 1 || workers.size() > 16 || caseKeys.size() > 16 || humans.size() > 16 || humans.values().stream().anyMatch(task -> task.laneVariable() != null) && decisions.size() != 1
                || (!decisions.isEmpty() || !workers.isEmpty()) && nodes.values().stream().anyMatch(n -> "parallelGateway".equals(n.kind))
                || decisions.isEmpty() && messageRefs.isEmpty() && humans.isEmpty() && nodes.values().stream().anyMatch(n -> "exclusiveGateway".equals(n.kind))) throw invalid();
        CompensationProfile profile = compensationProfile(nodes, workers, associations, compensationBoundaryAttachments,
                compensationWorkerIds, compensationThrowIds, errorBoundaryAttachments, humanTimers);
        Map<String, FlowNode> normalNodes = nodes;
        if (profile != null) {
            normalNodes = new LinkedHashMap<>(nodes);
            normalNodes.remove(profile.metadata().boundaryEventId());
            normalNodes.remove(profile.metadata().compensationWorkerId());
        }
        FlowNode start = null;
        List<RouteCondition> routes = new ArrayList<>();
        int splits = 0;
        for (FlowNode node : normalNodes.values()) {
            int outgoing = node.next.size();
            switch (node.kind) {
                case "startEvent" -> {
                    if (start != null || node.incoming != 0 || outgoing != 1) throw invalid();
                    start = node;
                }
                case "endEvent" -> { if (node.incoming < 1 || node.incoming > 4 || outgoing != 0) throw invalid(); }
                case "userTask", "serviceTask", "intermediateCatchEvent", "intermediateThrowEvent" -> { if (node.incoming != 1 || outgoing != 1) throw invalid(); }
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
        if (profile != null && (waitingWorkers.contains(profile.metadata().originalWorkerId())
                || waitingWorkers.contains(profile.metadata().compensationWorkerId()))) throw invalid();
        var referencedErrors = new HashSet<String>();
        var workerErrors = new LinkedHashMap<String, WorkerBusinessError>();
        for (var entry : errorBoundaryRefs.entrySet()) {
            String boundaryId = entry.getKey(), errorId = entry.getValue();
            String workerId = errorBoundaryAttachments.get(boundaryId);
            var boundary = nodes.get(boundaryId); var worker = nodes.get(workerId);
            if (!workers.containsKey(workerId) || waitingWorkers.contains(workerId)
                    || !referencedErrors.add(errorId) || boundary == null || worker == null
                    || boundary.next.size() != 1 || workerErrors.containsKey(workerId)) throw invalid();
            String targetId = boundary.next.getFirst().element.getAttribute("id");
            workerErrors.put(workerId, new WorkerBusinessError(errorId, errorCodes.get(errorId), boundaryId, targetId));
            // The native interrupting boundary is an implicit alternative edge from its attached Worker.
            worker.next.add(boundary); boundary.previous.add(worker); boundary.incoming++;
            boundary.incomingEdges.add(new IncomingEdge(worker, null));
        }
        if (!referencedErrors.equals(errorCodes.keySet())) throw invalid();
        var timedTasks = new HashSet<String>();
        for (var entry : humanTimers.entrySet()) {
            String attached = entry.getValue().taskElementId(); if (!humans.containsKey(attached) || !timedTasks.add(attached)) throw invalid();
            // The native boundary has an implicit edge from its attached task, not an incoming sequenceFlow.
            var task = nodes.get(attached); var boundary = nodes.get(entry.getKey()); task.next.add(boundary); boundary.previous.add(task); boundary.incoming++;
            boundary.incomingEdges.add(new IncomingEdge(task, null));
        }
        if (start == null) throw invalid();
        ArrayDeque<FlowNode> ready = new ArrayDeque<>();
        start.paths = 1;
        normalNodes.values().stream().filter(node -> node.incoming == 0).forEach(ready::add);
        int visited = 0;
        int traversals = 0;
        while (!ready.isEmpty()) {
            FlowNode node = ready.remove();
            if (node.paths == 0) throw invalid();
            node.decisionBefore = decisions.containsKey(node.element.getAttribute("id")) || !node.previous.isEmpty() && node.previous.stream().allMatch(previous -> previous.decisionBefore);
            node.waitBefore = !node.previous.isEmpty() && node.previous.stream().allMatch(previous -> previous.waitBefore
                    || "userTask".equals(previous.kind) || "intermediateCatchEvent".equals(previous.kind) || workers.containsKey(previous.element.getAttribute("id")));
            if (profile != null) {
                // Only the original's explicit normal edge can establish success on a path.
                node.originalNormalBefore = !node.incomingEdges.isEmpty() && node.incomingEdges.stream().allMatch(edge ->
                        edge.previous().originalNormalBefore || profile.originalNormalFlowId().equals(edge.explicitSequenceFlowId()));
            }
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
        if (visited != normalNodes.size()) throw invalid();
        Map<String, WorkerCompensationVO> compensations = Map.of();
        if (profile != null) {
            WorkerCompensationVO compensation = profile.metadata();
            if (!nodes.get(compensation.throwEventId()).originalNormalBefore) throw invalid();
            compensations = Map.of(compensation.originalWorkerId(), compensation);
        }
        return new ValidatedProcessModel(Type.BPMN, key, bytes, Map.of(), Map.of(), List.of(), decisions, routes,
                workers, waits, humans, humanTimers, cases, Map.copyOf(timers), Map.copyOf(workerErrors), compensations);
    }

    private static CompensationProfile compensationProfile(Map<String, FlowNode> nodes, Map<String, WorkerTask> workers,
                                                            List<Element> associations, Map<String, String> boundaryAttachments,
                                                            List<String> handlerIds, List<String> throwIds,
                                                            Map<String, String> errorBoundaryAttachments,
                                                            Map<String, ValidatedProcessModel.HumanTaskTimer> humanTimers) {
        if (associations.isEmpty() && boundaryAttachments.isEmpty() && handlerIds.isEmpty() && throwIds.isEmpty()) return null;
        if (associations.size() != 1 || boundaryAttachments.size() != 1 || handlerIds.size() != 1 || throwIds.size() != 1) throw invalid();

        var attachment = boundaryAttachments.entrySet().iterator().next();
        String boundaryId = attachment.getKey();
        String originalId = attachment.getValue();
        String handlerId = handlerIds.getFirst();
        Element association = associations.getFirst();
        if (!"One".equals(association.getAttribute("associationDirection"))
                || !boundaryId.equals(association.getAttribute("sourceRef")) || !handlerId.equals(association.getAttribute("targetRef"))
                || originalId.equals(handlerId)) throw invalid();

        WorkerTask originalWorker = workers.get(originalId);
        WorkerTask handlerWorker = workers.get(handlerId);
        var operationInput = List.of(new WorkerParameter("operationId", "operationId", ValueType.STRING));
        if (originalWorker == null || handlerWorker == null
                || !operationInput.equals(originalWorker.inputs()) || !originalWorker.outputs().isEmpty()
                || !operationInput.equals(handlerWorker.inputs()) || !handlerWorker.outputs().isEmpty()
                || errorBoundaryAttachments.containsValue(handlerId)
                || humanTimers.values().stream().anyMatch(timer -> handlerId.equals(timer.taskElementId()))) throw invalid();

        FlowNode boundary = nodes.get(boundaryId);
        FlowNode handler = nodes.get(handlerId);
        FlowNode original = nodes.get(originalId);
        if (boundary.incoming != 0 || !boundary.next.isEmpty() || handler.incoming != 0 || !handler.next.isEmpty()
                || original.incoming != 1 || original.flows.size() != 1) throw invalid();
        String originalNormalFlowId = required(original.flows.getFirst(), "id");

        String throwId = throwIds.getFirst();
        FlowNode thrown = nodes.get(throwId);
        Element definition = only(thrown.element, "compensateEventDefinition");
        if (!originalId.equals(definition.getAttribute("activityRef"))
                || !"true".equals(definition.getAttribute("waitForCompletion"))
                || thrown.incoming != 1 || thrown.next.size() != 1) throw invalid();
        FlowNode end = thrown.next.getFirst();
        if (!"endEvent".equals(end.kind) || end.incoming != 1 || !end.next.isEmpty()) throw invalid();
        var metadata = new WorkerCompensationVO(originalId, boundaryId, required(association, "id"), handlerId,
                throwId, required(end.element, "id"));
        return new CompensationProfile(metadata, originalNormalFlowId);
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
        return validateDmn(bytes, expectedKey, SafeModelXml.parse(bytes, false).getDocumentElement());
    }

    static ValidatedProcessModel validateAutomationDecision(byte[] bytes, String expectedKey, Element root) {
        var validated = validateDmn(bytes, expectedKey, root);
        try {
            var converted = new DmnXMLConverter().convertToDmnModel(() -> new java.io.ByteArrayInputStream(bytes), true, true);
            if (converted.getDecisions().size() != 1 || !expectedKey.equals(converted.getDecisions().getFirst().getId())) throw invalid();
            return validated;
        } catch (RuntimeException failure) {
            throw invalid();
        }
    }

    static CompiledControlBindingsVO validateCompiledAutomationBpmn(byte[] bytes, CompiledModelEvidenceVO evidence) {
        return validateCompiledAutomationBpmn(bytes, SafeModelXml.parseCompiledAutomationBpmn(bytes), evidence);
    }

    static CompiledControlBindingsVO validateCompiledAutomationBpmn(byte[] bytes, Document document, CompiledModelEvidenceVO evidence) {
        if (evidence == null || bytes == null || document == null || evidence.tenantId() <= 0 || !AutomationModelValidator.PROFILE.equals(evidence.profile())
                || !AutomationModelCompiler.VERSION.equals(evidence.compilerVersion()) || !AutomationModelValidator.isId(evidence.rootDefinitionKey())
                || !AutomationModelValidator.isSha256(evidence.sourceSha256()) || !AutomationModelValidator.isSha256(evidence.dependencySha256())
                || evidence.nativeFirstActivity() == null || evidence.nativeIds().isEmpty()
                || evidence.resourceDigests().size() > 64 || evidence.nativeIds().size() > 512
                || evidence.resourceDigests().values().stream().anyMatch(value -> !AutomationModelValidator.isSha256(value))) throw invalid();
        String rootResource = evidence.rootDefinitionKey() + ".bpmn20.xml";
        if (!AutomationModelValidator.rawSha256(bytes).equals(evidence.resourceDigests().get(rootResource))
                || !AutomationModelCompiler.compiledSha256(evidence.tenantId(), evidence.profile(), evidence.compilerVersion(),
                evidence.rootDefinitionKey(), evidence.nativeFirstActivity(), evidence.resourceDigests(), evidence.nativeIds())
                .equals(evidence.compiledSha256())) throw invalid();
        Element process = only(document.getDocumentElement(), "process");
        String namespace = AutomationModelCompiler.METADATA;
        if (!BPMN.equals(process.getNamespaceURI()) || !evidence.rootDefinitionKey().equals(process.getAttribute("id"))
                || !"true".equals(process.getAttribute("isExecutable"))
                || !Long.toString(evidence.tenantId()).equals(process.getAttributeNS(namespace, "tenantId"))
                || !evidence.profile().equals(process.getAttributeNS(namespace, "profile"))
                || !evidence.compilerVersion().equals(process.getAttributeNS(namespace, "compilerVersion"))
                || !evidence.sourceSha256().equals(process.getAttributeNS(namespace, "sourceSha256"))
                || !evidence.dependencySha256().equals(process.getAttributeNS(namespace, "dependencySha256"))) throw invalid();
        var extension = only(process, "extensionElements");
        var metadata = only(extension, "bindings");
        if (!namespace.equals(metadata.getNamespaceURI())) throw invalid();
        String json = metadata.getTextContent();
        byte[] bindingBytes = AutomationModelValidator.utf8(json);
        if (bindingBytes.length == 0 || bindingBytes.length > 1024 * 1024) throw invalid();
        try {
            var bindings = AutomationModelValidator.JSON.readValue(json, CompiledControlBindingsVO.class);
            if (bindings == null || !Arrays.equals(bindingBytes, AutomationModelCompiler.controlBindingsBytes(bindings))) throw invalid();
            if (!compiledNativeIds(document).equals(evidence.nativeIds())) throw invalid();
            var syntax = new CompiledBpmnSyntax(evidence, bindings);
            syntax.check(document.getDocumentElement(), process);
            var nativeModel = new BpmnXMLConverter().convertToBpmnModel(() -> new java.io.ByteArrayInputStream(bytes), true, true);
            var nativeIssues = new ProcessValidatorFactory().createDefaultProcessValidator().validate(nativeModel);
            if (nativeIssues.stream().anyMatch(issue -> !issue.isWarning())) throw invalid();
            var root = nativeModel.getMainProcess();
            if (nativeModel.getProcesses().size() != 1 || root == null || !evidence.rootDefinitionKey().equals(root.getId())) throw invalid();
            var starts = root.findFlowElementsOfType(StartEvent.class, false);
            if (starts.size() != 1) throw invalid();
            var start = starts.getFirst();
            if (start.getOutgoingFlows().size() != 1
                    || !evidence.nativeFirstActivity().equals(start.getOutgoingFlows().getFirst().getTargetRef())
                    || !(root.getFlowElement(evidence.nativeFirstActivity()) instanceof ServiceTask bootstrap)
                    || !bootstrap.isAsynchronous() || !"${tianjiAutomationBootstrap}".equals(bootstrap.getImplementation())
                    || evidence.nativeIds().values().stream().anyMatch(id -> nativeModel.getFlowElement(id) == null)) throw invalid();
            return bindings;
        } catch (RuntimeException failure) {
            throw invalid();
        }
    }

    /** Extracts only deterministic logical IDs from already bounded XML before full compiled verification. */
    static Map<String, String> compiledNativeIds(Document document) {
        var process = only(document.getDocumentElement(), "process");
        var result = new LinkedHashMap<String, String>();
        var elements = process.getElementsByTagNameNS(BPMN, "*");
        for (int index = 0; index < elements.getLength(); index++) {
            var element = (Element) elements.item(index);
            if (!element.hasAttributeNS(AutomationModelCompiler.METADATA, "logicalId")) continue;
            String logicalId = element.getAttributeNS(AutomationModelCompiler.METADATA, "logicalId");
            String nativeId = element.getAttribute("id");
            if (!AutomationModelValidator.isId(logicalId) || !AutomationModelCompiler.id("node", logicalId).equals(nativeId)
                    || result.putIfAbsent(logicalId, nativeId) != null || result.size() > 512) throw invalid();
        }
        if (result.isEmpty()) throw invalid();
        return Map.copyOf(result);
    }

    private static final class CompiledBpmnSyntax {
        private static final String NS = AutomationModelCompiler.METADATA;
        private static final String XSI = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
        private static final Set<String> FLOW = Set.of("startEvent", "endEvent", "exclusiveGateway", "parallelGateway", "subProcess",
                "serviceTask", "userTask", "intermediateCatchEvent", "boundaryEvent", "sequenceFlow");
        private static final Set<String> ROOT_METADATA = Set.of("profile", "compilerVersion", "sourceSha256", "dependencySha256", "tenantId");
        private static final Pattern NATIVE_ID = Pattern.compile("ta_[0-9a-f]{40}(?:_(?:message|entered|resolved|recover|end|wait)){0,2}");
        private final CompiledModelEvidenceVO evidence;
        private final CompiledControlBindingsVO bindings;
        private final Map<String, Element> elements = new LinkedHashMap<>();
        private final Map<String, Element> primaryTasks = new LinkedHashMap<>();
        private final Map<String, String> fixedDelegates = new LinkedHashMap<>();
        private final Set<String> usedConditions = new HashSet<>();
        private final Set<String> usedBranches = new HashSet<>();

        private CompiledBpmnSyntax(CompiledModelEvidenceVO evidence, CompiledControlBindingsVO bindings) {
            this.evidence = evidence;
            this.bindings = bindings;
        }

        private void check(Element root, Element process) {
            if (!BPMN.equals(root.getNamespaceURI()) || !"definitions".equals(root.getLocalName())
                    || !NS.equals(root.getAttribute("targetNamespace"))) throw invalid();
            validateAttributes(root, Set.of("targetNamespace"), Set.of(), Set.of(), Set.of());
            if (!evidence.nativeFirstActivity().equals(nativeId("first", AutomationModelCompiler.BOOTSTRAP))) throw invalid();
            registerDelegate(evidence.nativeFirstActivity(), "tianjiAutomationBootstrap");
            if (!bindings.occurrenceSlots().keySet().equals(bindings.policySlots().keySet())) throw invalid();
            bindings.occurrenceSlots().forEach((id, slot) -> {
                if (!nativeId("node", id).equals(slot.scopeId()) || !slot.scopeId().equals(evidence.nativeIds().get(id))) throw invalid();
                if (slot.activityId() != null) {
                    if (!nativeId("activity", id).equals(slot.activityId())) throw invalid();
                    primaryTasks.put(slot.activityId(), null);
                }
                if (slot.mergeId() != null) {
                    if (!nativeId("multi-merge", id).equals(slot.mergeId())) throw invalid();
                    registerDelegate(slot.mergeId(), "tianjiAutomationMerge");
                }
                var policy = bindings.policySlots().get(id);
                registerBoundary(id, policy.failureBoundaryId(), "node-error");
                registerBoundary(id, policy.nodeTimeoutBoundaryId(), "node-timeout");
                if (policy.attemptTimeoutBoundaryId() != null) registerBoundary(id, policy.attemptTimeoutBoundaryId(), "attempt-timeout");
                if (policy.attemptEscalationBoundaryId() != null) registerBoundary(id, policy.attemptEscalationBoundaryId(), "node-attempt-timeout");
            });
            checkElement(process, null, process);
            for (Element child : children(root, "error")) checkElement(child, null, process);
            for (Element child : children(root, "message")) checkElement(child, null, process);
            if (elementChildren(root).size() != 1 + children(root, "error").size() + children(root, "message").size()
                    || !usedConditions.equals(bindings.conditions().keySet())
                    || !usedBranches.equals(bindings.branchIndices().keySet())) throw invalid();
            validateLinks(process);
            bindings.occurrenceSlots().forEach(this::checkOccurrence);
            for (String id : fixedDelegates.keySet()) {
                if (!elements.containsKey(id)) throw invalid();
            }
        }

        private void checkElement(Element element, Element enclosingScope, Element process) {
            String tag = element.getLocalName();
            String ns = element.getNamespaceURI();
            String id = element.getAttribute("id");
            if (!id.isEmpty() && element != process) {
                if (!NATIVE_ID.matcher(id).matches() || elements.putIfAbsent(id, element) != null) throw invalid();
            }
            if (BPMN.equals(ns) && FLOW.contains(tag) && id.isEmpty()) throw invalid();
            if (element.hasAttributeNS(NS, "logicalId")) {
                String name = element.getAttributeNS(NS, "logicalId");
                String scope = logicalScope(enclosingScope);
                int nested = scope == null ? 0 : scope.length() + 1;
                if (!FLOW.contains(tag) || Set.of("serviceTask", "intermediateCatchEvent", "userTask", "sequenceFlow", "boundaryEvent").contains(tag)
                        || scope != null && !name.startsWith(scope + ".") || name.length() <= nested) throw invalid();
            }
            if (BPMN.equals(ns)) {
                switch (tag) {
                    case "process" -> validateAttributes(element, Set.of("id", "isExecutable"), Set.of(), ROOT_METADATA, Set.of());
                    case "startEvent", "endEvent", "parallelGateway" ->
                            validateAttributes(element, Set.of("id"), Set.of(), Set.of("logicalId"), Set.of());
                    case "exclusiveGateway" ->
                            validateAttributes(element, Set.of("id", "default"), Set.of(), Set.of("logicalId"), Set.of());
                    case "subProcess" -> {
                        validateAttributes(element, Set.of("id"), Set.of(),
                                Set.of("logicalId", "occurrence", "maxItems", "maxIterations"), Set.of());
                        if (element.hasAttributeNS(NS, "maxItems")) checkIntegerAttribute(element, "maxItems", 256);
                        if (element.hasAttributeNS(NS, "maxIterations")) checkIntegerAttribute(element, "maxIterations", 1_000);
                        if (element.hasAttributeNS(NS, "maxItems") && element.hasAttributeNS(NS, "maxIterations")) throw invalid();
                    }
                    case "userTask", "intermediateCatchEvent" -> validateAttributes(element, Set.of("id"), Set.of(), Set.of(), Set.of());
                    case "boundaryEvent" -> validateAttributes(element, Set.of("id", "attachedToRef"), Set.of(), Set.of(), Set.of());
                    case "sequenceFlow" -> {
                        validateAttributes(element, Set.of("id", "sourceRef", "targetRef"), Set.of(), Set.of("branchIndex"), Set.of());
                        if (element.hasAttributeNS(NS, "branchIndex")) {
                            Integer index = bindings.branchIndices().get(id);
                            if (index == null || index < 0 || index >= 32 || !index.toString().equals(element.getAttributeNS(NS, "branchIndex"))
                                    || !usedBranches.add(id)) throw invalid();
                        }
                    }
                    case "conditionExpression" -> {
                        validateAttributes(element, Set.of(), Set.of(), Set.of(), Set.of("type"));
                        if (!"tFormalExpression".equals(element.getAttributeNS(XSI, "type"))) throw invalid();
                        String edge = ((Element) element.getParentNode()).getAttribute("id");
                        String key = enclosingScope == null ? null : enclosingScope.getAttributeNS(NS, "occurrence");
                        String operation = "condition";
                        if (key != null && !key.isEmpty()) {
                            String enclosing = logicalScope(enclosingScope);
                            if (enclosing != null && nativeId("loop-next", enclosing).equals(edge)) operation = "continueLoop";
                        }
                        if (!"continueLoop".equals(operation) && bindings.occurrenceSlots().keySet().stream()
                                .anyMatch(name -> nativeId("loop-next", name).equals(edge))) operation = "continueLoop";
                        checkCondition(element, edge, operation);
                    }
                    case "completionCondition" -> {
                        validateAttributes(element, Set.of(), Set.of(), Set.of(), Set.of());
                        checkCondition(element, enclosingScope.getAttribute("id"), "completeMultiInstance");
                    }
                    case "serviceTask" -> checkTask(element, id);
                    case "multiInstanceLoopCharacteristics" -> {
                        validateAttributes(element, Set.of("isSequential"), Set.of("collection", "elementVariable", "elementIndexVariable"), Set.of(), Set.of());
                        if (!Set.of("true", "false").contains(element.getAttribute("isSequential"))
                                || !predicate("items", enclosingScope.getAttribute("id")).equals(element.getAttributeNS(FLOWABLE, "collection"))
                                || !"ta_item".equals(element.getAttributeNS(FLOWABLE, "elementVariable"))
                                || !"ta_itemIndex".equals(element.getAttributeNS(FLOWABLE, "elementIndexVariable"))) throw invalid();
                    }
                    case "extensionElements", "timerEventDefinition" ->
                            validateAttributes(element, Set.of(), Set.of(), Set.of(), Set.of());
                    case "timeDuration" -> {
                        validateAttributes(element, Set.of(), Set.of(), Set.of(), Set.of());
                        String text = element.getTextContent().trim();
                        Duration duration = Duration.parse(text);
                        if (!duration.isPositive() || duration.compareTo(Duration.ofDays(30)) > 0
                                || !text.equals(duration.toString())) throw invalid();
                    }
                    case "terminateEventDefinition" -> {
                        validateAttributes(element, Set.of(), Set.of("terminateAll"), Set.of(), Set.of());
                        if (!"true".equals(element.getAttributeNS(FLOWABLE, "terminateAll"))) throw invalid();
                    }
                    case "message" -> {
                        validateAttributes(element, Set.of("id", "name"), Set.of(), Set.of(), Set.of());
                        if (!id.endsWith("_message") || !("tianji-v2:" + id.substring(0, id.length() - 8))
                                .equals(element.getAttribute("name"))) throw invalid();
                    }
                    case "error" -> {
                        validateAttributes(element, Set.of("id", "errorCode"), Set.of(), Set.of(), Set.of());
                        String code = element.getAttribute("errorCode");
                        if ((!"TIANJI_AUTOMATION_NODE_FAILURE".equals(code)
                                && !code.matches("TIANJI_AUTOMATION_ATTEMPT_TIMEOUT_(BRANCH|END|WAIT_RECOVERY|RETRY)"))
                                || !nativeId("error-code", code).equals(id)) throw invalid();
                    }
                    case "messageEventDefinition" -> validateAttributes(element, Set.of("messageRef"), Set.of(), Set.of(), Set.of());
                    case "errorEventDefinition" -> validateAttributes(element, Set.of("errorRef"), Set.of(), Set.of(), Set.of());
                    default -> throw invalid();
                }
            } else if (FLOWABLE.equals(ns)) {
                switch (tag) {
                    case "executionListener" -> {
                        validateAttributes(element, Set.of("event", "delegateExpression"), Set.of(), Set.of(), Set.of());
                        String expected = switch (element.getAttribute("event")) {
                            case "start" -> "tianjiAutomationScopeEnter";
                            case "end" -> "tianjiAutomationScopeExit";
                            default -> throw invalid();
                        };
                        if (!delegate(expected).equals(element.getAttribute("delegateExpression"))
                                || !enclosingScope.hasAttributeNS(NS, "logicalId")) throw invalid();
                    }
                    case "externalWorkerInParameter", "externalWorkerOutParameter" ->
                            validateAttributes(element, Set.of("source", "target"), Set.of(), Set.of(), Set.of());
                    default -> throw invalid();
                }
            } else if (NS.equals(ns) && "bindings".equals(tag)) {
                validateAttributes(element, Set.of(), Set.of(), Set.of(), Set.of());
            } else throw invalid();

            for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element child) {
                    Element scope = Set.of("process", "subProcess").contains(tag) ? element : enclosingScope;
                    if (!allowedChild(element, child, process)) throw invalid();
                    checkElement(child, scope, process);
                } else if (node.getNodeType() != Node.TEXT_NODE || (!Set.of("bindings", "conditionExpression",
                        "completionCondition", "timeDuration").contains(tag) && !node.getNodeValue().isBlank())) throw invalid();
            }
        }

        private static boolean allowedChild(Element parent, Element child, Element process) {
            String tag = child.getLocalName();
            String parentTag = parent.getLocalName();
            return switch (parentTag) {
                case "process" -> BPMN.equals(child.getNamespaceURI()) && (FLOW.contains(tag) || "extensionElements".equals(tag));
                case "subProcess" -> BPMN.equals(child.getNamespaceURI())
                        && (FLOW.contains(tag) || Set.of("extensionElements", "multiInstanceLoopCharacteristics").contains(tag));
                case "serviceTask" -> BPMN.equals(child.getNamespaceURI()) && "extensionElements".equals(tag)
                        && "external-worker".equals(parent.getAttributeNS(FLOWABLE, "type"));
                case "extensionElements" -> parent.getParentNode() == process ? NS.equals(child.getNamespaceURI()) && "bindings".equals(tag)
                        : FLOWABLE.equals(child.getNamespaceURI()) && ("subProcess".equals(parent.getParentNode().getLocalName())
                        ? "executionListener".equals(tag) : Set.of("externalWorkerInParameter", "externalWorkerOutParameter").contains(tag));
                case "endEvent" -> BPMN.equals(child.getNamespaceURI())
                        && Set.of("errorEventDefinition", "terminateEventDefinition").contains(tag);
                case "sequenceFlow" -> BPMN.equals(child.getNamespaceURI()) && "conditionExpression".equals(tag);
                case "multiInstanceLoopCharacteristics" -> BPMN.equals(child.getNamespaceURI()) && "completionCondition".equals(tag);
                case "boundaryEvent", "intermediateCatchEvent" -> BPMN.equals(child.getNamespaceURI())
                        && Set.of("errorEventDefinition", "timerEventDefinition", "messageEventDefinition").contains(tag);
                case "timerEventDefinition" -> BPMN.equals(child.getNamespaceURI()) && "timeDuration".equals(tag);
                default -> false;
            };
        }

        private void checkTask(Element task, String id) {
            String implementation = task.getAttributeNS(FLOWABLE, "delegateExpression");
            if ("external-worker".equals(task.getAttributeNS(FLOWABLE, "type"))) {
                validateAttributes(task, Set.of("id"), Set.of("type", "topic", "doNotIncludeVariables"), Set.of(), Set.of());
                if (!primaryTasks.containsKey(id) || !task.getAttributeNS(FLOWABLE, "topic").matches("tianji-v2:worker:[0-9a-f]{64}")
                        || !"true".equals(task.getAttributeNS(FLOWABLE, "doNotIncludeVariables"))) throw invalid();
                var extension = only(task, "extensionElements");
                var input = children(extension, "externalWorkerInParameter");
                var output = only(extension, "externalWorkerOutParameter");
                if (input.size() != AutomationModelCompiler.WORKER_FIELDS.size() || !"resultRef".equals(output.getAttribute("source"))
                        || !"resultRef".equals(output.getAttribute("target"))) throw invalid();
                for (int index = 0; index < input.size(); index++) {
                    String field = AutomationModelCompiler.WORKER_FIELDS.get(index);
                    if (!field.equals(input.get(index).getAttribute("source")) || !field.equals(input.get(index).getAttribute("target"))) throw invalid();
                }
                primaryTasks.put(id, task);
                return;
            }
            validateAttributes(task, Set.of("id"), Set.of("delegateExpression", "async"),
                    Set.of("decisionKey", "occurrence", "outcome", "action"), Set.of());
            String expected = fixedDelegates.get(id);
            if (primaryTasks.containsKey(id)) {
                if (task.hasAttributeNS(NS, "decisionKey") && AutomationModelValidator.isId(task.getAttributeNS(NS, "decisionKey"))) {
                    expected = "tianjiAutomationDecision";
                } else {
                    expected = "tianjiAutomationAgentDispatch";
                }
                primaryTasks.put(id, task);
            }
            if (expected == null && "tianjiAutomationMerge".equals(fixedMergeDelegate(task, id))) expected = "tianjiAutomationMerge";
            if (expected == null || !delegate(expected).equals(implementation)
                    || evidence.nativeFirstActivity().equals(id) != "true".equals(task.getAttributeNS(FLOWABLE, "async"))
                    || !evidence.nativeFirstActivity().equals(id) && task.hasAttributeNS(FLOWABLE, "async")) throw invalid();
        }

        private String fixedMergeDelegate(Element task, String id) {
            String scope = task.getAttributeNS(NS, "occurrence");
            for (var slot : bindings.occurrenceSlots().entrySet()) {
                if (slot.getValue().scopeId().equals(scope) && nativeId("merge", slot.getKey()).equals(id)
                        && id.equals(slot.getValue().attemptOrBodyId())) return "tianjiAutomationMerge";
            }
            return null;
        }

        private void checkCondition(Element element, String id, String operation) {
            if (!bindings.conditions().containsKey(id) || !usedConditions.add(id)
                    || !predicate(operation, id).equals(element.getTextContent().trim())) throw invalid();
        }

        private static void checkIntegerAttribute(Element element, String name, int maximum) {
            String value = element.getAttributeNS(NS, name);
            if (!value.matches("[1-9][0-9]{0,3}") || Integer.parseInt(value) > maximum) throw invalid();
        }

        private void validateLinks(Element root) {
            int rootStarts = 0;
            for (Element element : elements.values()) {
                String tag = element.getLocalName();
                Element container = (Element) element.getParentNode();
                switch (tag) {
                    case "sequenceFlow" -> {
                        Element from = scopedElement(element.getAttribute("sourceRef"), container);
                        scopedElement(element.getAttribute("targetRef"), container);
                        if (element.hasAttributeNS(NS, "branchIndex") && !"parallelGateway".equals(from.getLocalName())) throw invalid();
                        if (!children(element, "conditionExpression").isEmpty() && !"exclusiveGateway".equals(from.getLocalName())) throw invalid();
                    }
                    case "exclusiveGateway" -> {
                        if (!element.hasAttribute("default")) break;
                        Element flow = scopedElement(element.getAttribute("default"), container);
                        if (!"sequenceFlow".equals(flow.getLocalName()) || !element.getAttribute("id").equals(flow.getAttribute("sourceRef"))
                                || !children(flow, "conditionExpression").isEmpty()) throw invalid();
                    }
                    case "boundaryEvent" -> scopedElement(element.getAttribute("attachedToRef"), container);
                    case "messageEventDefinition" -> {
                        Element message = elements.get(element.getAttribute("messageRef"));
                        if (message == null || !"message".equals(message.getLocalName())) throw invalid();
                    }
                    case "errorEventDefinition" -> {
                        Element error = elements.get(element.getAttribute("errorRef"));
                        if (error == null || !"error".equals(error.getLocalName())) throw invalid();
                    }
                    case "startEvent" -> {
                        if (container == root) rootStarts++;
                    }
                    default -> { }
                }
            }
            if (rootStarts != 1) throw invalid();
        }

        private Element scopedElement(String id, Element scope) {
            Element element = elements.get(id);
            if (element == null || element.getParentNode() != scope || !FLOW.contains(element.getLocalName())) throw invalid();
            return element;
        }

        private void checkOccurrence(String id, AutomationModelCompiler.OccurrenceSlotVO slot) {
            Element scope = elements.get(slot.scopeId());
            if (scope == null) throw invalid();
            boolean join = "parallelGateway".equals(scope.getLocalName());
            if (!join && !"subProcess".equals(scope.getLocalName())) throw invalid();
            if (!join) {
                var listeners = children(only(scope, "extensionElements"), "executionListener");
                if (listeners.size() != 2 || !"start".equals(listeners.getFirst().getAttribute("event"))
                        || !"end".equals(listeners.getLast().getAttribute("event"))) throw invalid();
            }
            Element body = elements.get(slot.attemptOrBodyId());
            if (slot.attemptOrBodyId() != null) {
                if (body == null || body.getParentNode() != (join ? scope.getParentNode() : scope)
                        || !slot.scopeId().equals(body.getAttributeNS(NS, "occurrence"))) throw invalid();
                if (!Set.of(nativeId("attempt", id), nativeId("loop-body", id), nativeId("multi-body", id), nativeId("merge", id))
                        .contains(slot.attemptOrBodyId())) throw invalid();
            }
            if (slot.activityId() != null) {
                Element activity = elements.get(slot.activityId());
                if (body == null || activity == null || activity.getParentNode() != body
                        || !Set.of("serviceTask", "userTask", "intermediateCatchEvent").contains(activity.getLocalName())) throw invalid();
            }
            if (slot.waitId() != null) {
                Element wait = elements.get(slot.waitId());
                if (wait == null || wait.getParentNode() != body || !"intermediateCatchEvent".equals(wait.getLocalName())
                        || (!slot.waitId().equals(slot.activityId()) && !nativeId("agent-wait", id).equals(slot.waitId()))) throw invalid();
            }
            if (slot.mergeId() != null && elements.get(slot.mergeId()).getParentNode() != scope) throw invalid();

            var policy = bindings.policySlots().get(id);
            if (policy.dispatchTimeout() == null || !policy.dispatchTimeout().isPositive()
                    || policy.dispatchTimeout().compareTo(Duration.ofDays(30)) > 0 || policy.maxAttempts() < 1
                    || policy.maxAttempts() > 10 || policy.retryBackoff() == null || policy.retryBackoff().isNegative()
                    || policy.retryBackoff().compareTo(Duration.ofHours(1)) > 0) throw invalid();
            String attachment = join ? slot.attemptOrBodyId() : slot.scopeId();
            checkBoundary(policy.failureBoundaryId(), attachment, slot.scopeId(), "FAILURE");
            checkBoundary(policy.nodeTimeoutBoundaryId(), attachment, slot.scopeId(), "NODE_TIMEOUT");
            if (policy.attemptTimeoutBoundaryId() != null) {
                checkBoundary(policy.attemptTimeoutBoundaryId(), slot.attemptOrBodyId(), slot.scopeId(), "ATTEMPT_TIMEOUT");
            }
            if (policy.attemptEscalationBoundaryId() != null) {
                checkBoundary(policy.attemptEscalationBoundaryId(), attachment, slot.scopeId(), "ATTEMPT_TIMEOUT");
            }
        }

        private void checkBoundary(String binding, String attachment, String ownerScope, String outcome) {
            Element boundary = elements.get(binding);
            Element action = elements.get(binding + "_recover");
            Element transition = elements.get(binding + "_resolved");
            Element entered = elements.get(binding + "_entered");
            if (boundary == null || action == null || !"boundaryEvent".equals(boundary.getLocalName())
                    || !attachment.equals(boundary.getAttribute("attachedToRef"))
                    || !ownerScope.equals(action.getAttributeNS(NS, "occurrence"))
                    || !outcome.equals(action.getAttributeNS(NS, "outcome"))
                    || !Set.of("BRANCH", "END", "WAIT_RECOVERY", "RETRY").contains(action.getAttributeNS(NS, "action"))
                    || elementChildren(boundary).size() != 1 || transition == null || entered == null
                    || !binding.equals(entered.getAttribute("sourceRef")) || !(binding + "_recover").equals(entered.getAttribute("targetRef"))
                    || !(binding + "_recover").equals(transition.getAttribute("sourceRef"))) throw invalid();
            Element target = elements.get(transition.getAttribute("targetRef"));
            String policy = action.getAttributeNS(NS, "action");
            if (target == null) throw invalid();
            String definition = elementChildren(boundary).getFirst().getLocalName();
            if ("ATTEMPT_TIMEOUT".equals(outcome) && "timerEventDefinition".equals(definition)) {
                if (!"endEvent".equals(target.getLocalName()) || children(target, "errorEventDefinition").size() != 1
                        || !(binding + "_end").equals(target.getAttribute("id"))) throw invalid();
                return;
            }
            switch (policy) {
                case "WAIT_RECOVERY", "RETRY" -> {
                    if (!"intermediateCatchEvent".equals(target.getLocalName()) || !(binding + "_wait").equals(target.getAttribute("id"))
                            || children(target, "messageEventDefinition").size() != 1) throw invalid();
                }
                case "END" -> {
                    if (!"endEvent".equals(target.getLocalName()) || !(binding + "_end").equals(target.getAttribute("id"))
                            || children(target, "terminateEventDefinition").size() != 1) throw invalid();
                }
                case "BRANCH" -> {
                    if (!target.hasAttributeNS(NS, "logicalId") || "startEvent".equals(target.getLocalName())) throw invalid();
                }
                default -> throw invalid();
            }
        }

        private void registerBoundary(String logical, String id, String type) {
            if (!nativeId(type, logical).equals(id)) throw invalid();
            registerDelegate(id + "_recover", "tianjiAutomationPolicy");
        }

        private void registerDelegate(String id, String delegate) {
            if (fixedDelegates.putIfAbsent(id, delegate) != null) throw invalid();
        }

        private static String logicalScope(Element container) {
            for (Node current = container; current instanceof Element element; current = current.getParentNode()) {
                if (element.hasAttributeNS(NS, "logicalId")) return element.getAttributeNS(NS, "logicalId");
            }
            return null;
        }

        private static String nativeId(String purpose, String logicalId) {
            return AutomationModelCompiler.id(purpose, logicalId);
        }

        private static String delegate(String name) {
            return "${" + name + "}";
        }

        private static String predicate(String operation, String id) {
            return "${tianjiAutomationControl." + operation + "(execution, '" + id + "')}";
        }

        private static List<Element> elementChildren(Element parent) {
            var children = new ArrayList<Element>();
            for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element element) children.add(element);
            }
            return children;
        }

        private static void validateAttributes(Element element, Set<String> plain, Set<String> flowable,
                                               Set<String> metadata, Set<String> xsi) {
            var attributes = element.getAttributes();
            for (int index = 0; index < attributes.getLength(); index++) {
                Node attribute = attributes.item(index);
                String ns = attribute.getNamespaceURI();
                String name = attribute.getLocalName();
                if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(ns)) continue;
                if (ns == null && plain.contains(name) || FLOWABLE.equals(ns) && flowable.contains(name)
                        || NS.equals(ns) && metadata.contains(name) || XSI.equals(ns) && xsi.contains(name)) continue;
                throw invalid();
            }
        }
    }

    private static ValidatedProcessModel validateDmn(byte[] bytes, String expectedKey, Element root) {
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

    private record IncomingEdge(FlowNode previous, String explicitSequenceFlowId) { }

    private record CompensationProfile(WorkerCompensationVO metadata, String originalNormalFlowId) { }

    private static final class FlowNode {
        private final String kind;
        private final Element element;
        private final List<FlowNode> next = new ArrayList<>();
        private final List<FlowNode> previous = new ArrayList<>();
        private final List<IncomingEdge> incomingEdges = new ArrayList<>();
        private final List<Element> flows = new ArrayList<>();
        private int incoming;
        private int visitedInputs;
        private int paths;
        private boolean decisionBefore;
        private boolean waitBefore;
        private boolean originalNormalBefore;

        private FlowNode(String kind, Element element) { this.kind = kind; this.element = element; }
    }
}
