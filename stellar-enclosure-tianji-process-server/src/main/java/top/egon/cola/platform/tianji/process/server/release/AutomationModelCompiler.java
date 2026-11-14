package top.egon.cola.platform.tianji.process.server.release;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.ConditionVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.LoopControlVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.MultiInstanceControlVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodePolicyVO.PolicyActionVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.NodeVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.ScopeVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.ValidatedAutomationModelVO;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.PROFILE;
import static top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.rawSha256;
import static top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.utf8;

/** Emits Flowable artifacts exclusively from the validated graph and server-owned BPMN templates. */
@Slf4j
@Validated
@Component("automationModelCompiler")
public class AutomationModelCompiler {
    static final String VERSION = "1";
    static final String FLOWABLE = "http://flowable.org/bpmn";
    static final String METADATA = "urn:stellar-enclosure:tianji:automation:2";
    private static final String TOPIC_PREFIX = "tianji-v2:worker:";
    static final String BOOTSTRAP = "tianji_automation_bootstrap";
    public static final String CONTROL_EXPRESSION_ROOT = "tianjiAutomationControl";
    public static final String DECISION_DELEGATE = "tianjiAutomationDecision";
    public static final String MERGE_DELEGATE = "tianjiAutomationMerge";
    public static final String MULTI_INSTANCE_ITEM = "ta_item";
    public static final String MULTI_INSTANCE_INDEX = "ta_itemIndex";
    private static final int MAX_DEPENDENCY_MANIFEST_BYTES = 256 * 1024;
    private static final String SOURCE_DOMAIN = "AUTOMATION_SOURCE_MANIFEST_V2";
    private static final String DEPENDENCY_DOMAIN = "AUTOMATION_DEPENDENCY_MANIFEST_V2";
    private static final String COMPILED_DOMAIN = "AUTOMATION_COMPILED_MANIFEST_V2";
    static final List<String> WORKER_FIELDS = List.of("nodeExecutionRef", "inputSha256", "configurationSha256");

    public CompiledAutomationModelVO compile(@Valid @NotNull ValidatedAutomationModelVO model) {
        if (model == null) throw new IllegalArgumentException("Validated automation model is required");
        for (var route : model.source().eventRoutes()) {
            if (route.schedule() != null && route.schedule().interval().getNano() % 1_000 != 0) {
                throw new IllegalArgumentException("Schedule interval must be a whole number of microseconds");
            }
        }
        var sourceManifest = sourceManifest(model.source());
        String sourceHash = sourceSha256(sourceManifest);
        var dependencies = dependencyManifest(model);
        String dependencyHash = dependencySha256(dependencies);
        var artifacts = new TreeMap<String, ModelArtifactVO>();
        var bpmn = new NativeBpmn(model);
        bpmn.emitScope(model.graph(), true);
        String key = model.rootDefinitionKey();
        String filename = key + ".bpmn20.xml";
        var bindings = new CompiledControlBindingsVO(bpmn.conditions, bpmn.branchIndices, bpmn.occurrenceSlots, bpmn.policySlots);
        byte[] xml = bpmn.complete(sourceHash, dependencyHash, bindings);
        artifacts.put(filename, new ModelArtifactVO(Type.BPMN, key, xml));
        model.decisions().forEach((decision, value) ->
                artifacts.put(decision + ".dmn", new ModelArtifactVO(Type.DMN, decision, value.xml())));
        var digests = new TreeMap<String, String>();
        artifacts.forEach((name, artifact) -> digests.put(name, artifact.sha256()));
        var evidence = new CompiledModelEvidenceVO(model.tenantId(), PROFILE, VERSION, key, sourceHash, dependencyHash,
                compiledSha256(model.tenantId(), PROFILE, VERSION, key, id("first", BOOTSTRAP), digests, bpmn.logicalIds),
                id("first", BOOTSTRAP), digests, bpmn.logicalIds);
        if (!bindings.equals(ProcessModelValidator.validateCompiledAutomationBpmn(xml, evidence))) {
            throw new IllegalStateException("Generated automation bindings did not round-trip");
        }
        return new CompiledAutomationModelVO(evidence, model.source(), sourceManifest, dependencies, artifacts, bindings);
    }

    static Map<String, Object> sourceManifest(AutomationModelSourceVO source) {
        var assets = source.models().stream().map(asset -> Map.of("type", asset.type(), "definitionKey", asset.definitionKey(),
                "modelSha256", rawSha256(utf8(asset.xml())))).toList();
        return Map.of("profile", source.profile(), "models", assets,
                "inputSchema", source.inputSchema().toWire(), "outputSchema", source.outputSchema().toWire(),
                "nodeBindings", wire(source.nodeBindings()), "eventRoutes", wire(source.eventRoutes()));
    }

    private static Map<String, Object> dependencyManifest(ValidatedAutomationModelVO model) {
        var decisions = new TreeMap<String, String>();
        model.decisions().forEach((key, value) -> decisions.put(key, rawSha256(value.xml())));
        return Map.of("tenantId", model.tenantId(), "workerCapabilities", wire(model.workers()),
                "agentTemplates", wire(model.templates()), "agentConfigurations", wire(model.agentConfigurationDependencies()),
                "humanCandidates", model.humanDependencies(),
                "eventRoutes", model.routeDependencies(), "dmn", decisions);
    }

    static String sourceSha256(Map<String, ?> descriptor) {
        return ProcessTypedJson.sha256(SOURCE_DOMAIN, ProcessTypedJson.canonicalBytes(descriptor));
    }

    static String dependencySha256(Map<String, ?> descriptor) {
        return ProcessTypedJson.sha256(DEPENDENCY_DOMAIN, ProcessTypedJson.canonicalBytes(descriptor, MAX_DEPENDENCY_MANIFEST_BYTES));
    }

    static String compiledSha256(long tenantId, String profile, String version, String root, String bootstrap,
                                 Map<String, String> artifacts, Map<String, String> nativeIds) {
        return ProcessTypedJson.sha256(COMPILED_DOMAIN, utf8(Long.toString(tenantId)), utf8(profile), utf8(version),
                utf8(root), utf8(bootstrap), ProcessTypedJson.canonicalBytes(artifacts), ProcessTypedJson.canonicalBytes(nativeIds));
    }

    /** The physical topic is pinned to trusted registration evidence, never a source XML topic or an effect key. */
    public static String physicalWorkerTopic(long tenantId, String capabilityKey, long version, String contractSha256) {
        if (tenantId <= 0 || !AutomationNodeBindingVO.isCapabilityKey(capabilityKey) || version <= 0
                || version > ProcessTypedJson.V2_MAX_WIRE_INTEGER || !AutomationModelValidator.isSha256(contractSha256)) {
            throw new IllegalArgumentException("Invalid automation worker topic authority");
        }
        return TOPIC_PREFIX + ProcessTypedJson.sha256("AUTOMATION_WORKER_TOPIC_V2", utf8(Long.toString(tenantId)),
                utf8(capabilityKey), utf8(Long.toString(version)), utf8(contractSha256));
    }

    private static Object wire(Object value) {
        return AutomationModelValidator.JSON.convertValue(value, Object.class);
    }

    private static Object freezeWire(Object value) {
        if (value instanceof List<?> list) return list.stream().map(AutomationModelCompiler::freezeWire).toList();
        if (value instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> copy.put((String) key, freezeWire(item)));
            return Collections.unmodifiableMap(copy);
        }
        return value;
    }

    private static Map<String, Object> freezeObject(Map<String, Object> value) {
        var copy = new LinkedHashMap<String, Object>();
        value.forEach((key, item) -> copy.put(key, freezeWire(item)));
        return Collections.unmodifiableMap(copy);
    }

    static String id(String purpose, String qualifiedId) {
        return "ta_" + ProcessTypedJson.sha256("AUTOMATION_NATIVE_ID_V2", utf8(purpose), utf8(qualifiedId)).substring(0, 40);
    }

    /** The loop condition in the sealed BPMN is keyed by its outgoing continuation flow. */
    public static String loopContinuationFlowId(String logicalNodeId) {
        return id("loop-next", logicalNodeId);
    }

    public static String bootstrapActivityId() {
        return id("first", BOOTSTRAP);
    }

    /** Server-owned expression entry points shared with the native runtime. */
    public enum ControlPredicate {
        XOR("condition"),
        LOOP_CONTINUATION("continueLoop"),
        MULTI_INSTANCE_ITEMS("items"),
        MULTI_INSTANCE_COMPLETION("completeMultiInstance");

        private final String methodName;

        ControlPredicate(String methodName) {
            this.methodName = methodName;
        }

        public String methodName() {
            return methodName;
        }

        public String expression(String generatedId) {
            return "${" + CONTROL_EXPRESSION_ROOT + "." + methodName + "(execution, '" + generatedId + "')}";
        }
    }

    private static final class NativeBpmn {
        private final ValidatedAutomationModelVO model;
        private final StringBuilder process = new StringBuilder();
        private final Map<String, String> messages = new LinkedHashMap<>();
        private final Map<String, String> errors = new LinkedHashMap<>();
        private final Map<String, String> logicalIds = new LinkedHashMap<>();
        private final Map<String, ConditionVO> conditions = new LinkedHashMap<>();
        private final Map<String, Integer> branchIndices = new LinkedHashMap<>();
        private final Map<String, OccurrenceSlotVO> occurrenceSlots = new LinkedHashMap<>();
        private final Map<String, PolicySlotsVO> policySlots = new LinkedHashMap<>();

        private NativeBpmn(ValidatedAutomationModelVO model) {
            this.model = model;
        }

        private byte[] complete(String sourceHash, String dependencyHash, CompiledControlBindingsVO bindings) {
            StringBuilder xml = new StringBuilder();
            xml.append("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                 xmlns:flowable="http://flowable.org/bpmn" xmlns:ta="urn:stellar-enclosure:tianji:automation:2"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" targetNamespace="urn:stellar-enclosure:tianji:automation:2">
                    """);
            messages.forEach((nativeId, name) -> tag(xml, "message", attrs("id", nativeId, "name", name)));
            errors.forEach((nativeId, code) -> tag(xml, "error", attrs("id", nativeId, "errorCode", code)));
            open(xml, "process", attrs("id", model.rootDefinitionKey(), "isExecutable", "true", "ta:profile", PROFILE,
                    "ta:compilerVersion", VERSION, "ta:sourceSha256", sourceHash, "ta:dependencySha256", dependencyHash,
                    "ta:tenantId", Long.toString(model.tenantId())));
            open(xml, "extensionElements", Map.of());
            xml.append("<ta:bindings>").append(escape(new String(controlBindingsBytes(bindings), java.nio.charset.StandardCharsets.UTF_8)))
                    .append("</ta:bindings>\n");
            close(xml, "extensionElements");
            xml.append(process);
            return utf8(xml.append("</process>\n</definitions>\n").toString());
        }

        private void emitScope(ScopeVO scope, boolean root) {
            for (NodeVO node : scope.nodes().values()) emitNode(node);
            for (var edge : scope.edges().values()) {
                String from = scope.nodes().get(edge.from()).kind().equals("AND_JOIN")
                        ? id("merge", edge.from()) : nativeNode(edge.from());
                if (root && edge.from().equals(scope.start())) from = id("first", BOOTSTRAP);
                String edgeId = id("flow", edge.id());
                String expression = null;
                if (edge.condition() != null) {
                    conditions.put(edgeId, edge.condition());
                    expression = ControlPredicate.XOR.expression(edgeId);
                }
                Integer branch = scope.branchIndices().get(edge.id());
                if (branch != null) branchIndices.put(edgeId, branch);
                sequence(edgeId, from, nativeNode(edge.to()), expression,
                        branch == null ? Map.of() : attrs("ta:branchIndex", branch.toString()));
            }
            if (root) {
                String bootstrap = id("first", BOOTSTRAP);
                task(bootstrap, "tianjiAutomationBootstrap", attrs("flowable:async", "true"));
                sequence(id("first-flow", BOOTSTRAP), nativeNode(scope.start()), bootstrap, null, Map.of());
            }
        }

        private void emitNode(NodeVO node) {
            String nativeId = nativeNode(node.id());
            logicalIds.put(node.id(), nativeId);
            var metadata = attrs("id", nativeId, "ta:logicalId", node.id());
            switch (node.kind()) {
                case "startEvent", "endEvent", "parallelGateway" -> tag(process, node.tag(), metadata);
                case "exclusiveGateway" -> {
                    if (node.defaultEdge() != null) metadata.put("default", id("flow", node.defaultEdge()));
                    tag(process, node.tag(), metadata);
                }
                case "AND_JOIN" -> {
                    tag(process, "parallelGateway", metadata);
                    String merge = id("merge", node.id());
                    task(merge, MERGE_DELEGATE, attrs("ta:occurrence", nativeId));
                    sequence(id("merge-flow", node.id()), nativeId, merge, null, Map.of());
                    occurrenceSlots.put(node.id(), new OccurrenceSlotVO(nativeId, merge, null, null, null));
                    emitBoundaries(node, merge, null);
                }
                case "LOOP" -> emitLoop(node, metadata);
                case "MULTI_INSTANCE" -> emitMultiInstance(node, metadata);
                case "SUBPROCESS" -> {
                    open(process, "subProcess", metadata);
                    listeners();
                    emitScope(node.body(), false);
                    close(process, "subProcess");
                    occurrenceSlots.put(node.id(), new OccurrenceSlotVO(nativeId, null, null, null, null));
                    emitBoundaries(node, nativeId, null);
                }
                default -> emitOccurrence(node, metadata);
            }
        }

        private void emitLoop(NodeVO node, Map<String, String> metadata) {
            String scope = nativeNode(node.id());
            String start = id("loop-start", node.id());
            String body = id("loop-body", node.id());
            String gate = id("loop-gate", node.id());
            String end = id("loop-end", node.id());
            String exit = id("loop-exit", node.id());
            String next = loopContinuationFlowId(node.id());
            var control = (LoopControlVO) node.binding().control();
            conditions.put(next, control.condition());
            open(process, "subProcess", metadata);
            listeners();
            tag(process, "startEvent", attrs("id", start));
            tag(process, "exclusiveGateway", attrs("id", gate, "default", exit));
            open(process, "subProcess", attrs("id", body, "ta:occurrence", scope, "ta:maxIterations", Integer.toString(control.maxIterations())));
            emitScope(node.body(), false);
            close(process, "subProcess");
            tag(process, "endEvent", attrs("id", end));
            sequence(id("loop-enter", node.id()), start, body, null, Map.of());
            sequence(id("loop-test", node.id()), body, gate, null, Map.of());
            sequence(next, gate, body, ControlPredicate.LOOP_CONTINUATION.expression(next), Map.of());
            sequence(exit, gate, end, null, Map.of());
            close(process, "subProcess");
            occurrenceSlots.put(node.id(), new OccurrenceSlotVO(scope, body, null, null, null));
            emitBoundaries(node, scope, null);
        }

        private void emitMultiInstance(NodeVO node, Map<String, String> metadata) {
            String scope = nativeNode(node.id());
            var control = (MultiInstanceControlVO) node.binding().control();
            String start = id("multi-start", node.id());
            String body = id("multi-body", node.id());
            String merge = id("multi-merge", node.id());
            String end = id("multi-end", node.id());
            open(process, "subProcess", metadata);
            listeners();
            tag(process, "startEvent", attrs("id", start));
            open(process, "subProcess", attrs("id", body, "ta:occurrence", scope, "ta:maxItems", Integer.toString(control.maxItems())));
            open(process, "multiInstanceLoopCharacteristics", attrs("isSequential", Boolean.toString(control.sequential()),
                    "flowable:collection", ControlPredicate.MULTI_INSTANCE_ITEMS.expression(body),
                    "flowable:elementVariable", MULTI_INSTANCE_ITEM, "flowable:elementIndexVariable", MULTI_INSTANCE_INDEX));
            if (control.completionCondition() != null) {
                conditions.put(body, control.completionCondition());
                textElement(process, "completionCondition", ControlPredicate.MULTI_INSTANCE_COMPLETION.expression(body));
            }
            close(process, "multiInstanceLoopCharacteristics");
            emitScope(node.body(), false);
            close(process, "subProcess");
            task(merge, MERGE_DELEGATE, attrs("ta:occurrence", scope));
            tag(process, "endEvent", attrs("id", end));
            sequence(id("multi-enter", node.id()), start, body, null, Map.of());
            sequence(id("multi-collect", node.id()), body, merge, null, Map.of());
            sequence(id("multi-exit", node.id()), merge, end, null, Map.of());
            close(process, "subProcess");
            occurrenceSlots.put(node.id(), new OccurrenceSlotVO(scope, body, null, null, merge));
            emitBoundaries(node, scope, null);
        }

        private void emitOccurrence(NodeVO node, Map<String, String> metadata) {
            String logical = node.id();
            String nativeId = nativeNode(logical);
            String start = id("occurrence-start", logical);
            String attempt = id("attempt", logical);
            String end = id("occurrence-end", logical);
            String attemptStart = id("attempt-start", logical);
            String activity = id("activity", logical);
            String attemptEnd = id("attempt-end", logical);
            String wait = null;
            open(process, "subProcess", metadata);
            listeners();
            tag(process, "startEvent", attrs("id", start));
            open(process, "subProcess", attrs("id", attempt, "ta:occurrence", nativeId));
            tag(process, "startEvent", attrs("id", attemptStart));
            switch (node.kind()) {
                case "SERVICE", "RULE" -> {
                    if (node.binding().service() == null) {
                        task(activity, DECISION_DELEGATE, attrs("ta:decisionKey", node.binding().rule().definitionKey()));
                    } else {
                        emitWorker(node, activity);
                    }
                }
                case "AGENT" -> {
                    task(activity, "tianjiAutomationAgentDispatch", Map.of());
                    wait = id("agent-wait", logical);
                    messageWait(wait);
                    sequence(id("agent-wait-flow", logical), activity, wait, null, Map.of());
                }
                case "HUMAN" -> tag(process, "userTask", attrs("id", activity));
                case "EVENT_WAIT" -> {
                    messageWait(activity);
                    wait = activity;
                }
                case "TIMER" -> {
                    open(process, "intermediateCatchEvent", attrs("id", activity));
                    timer(node.timer());
                    close(process, "intermediateCatchEvent");
                    wait = activity;
                }
                default -> throw new IllegalStateException("Unexpected automation node kind");
            }
            tag(process, "endEvent", attrs("id", attemptEnd));
            sequence(id("attempt-entry", logical), attemptStart, activity, null, Map.of());
            sequence(id("attempt-exit", logical), wait == null ? activity : wait, attemptEnd, null, Map.of());
            close(process, "subProcess");
            var policy = node.binding().policy();
            String attemptTimeout = id("attempt-timeout", logical);
            String escalateTimeout = errorRef(attemptTimeoutCode(policy.onAttemptTimeout()));
            boundary(node, attemptTimeout, attempt, "ATTEMPT_TIMEOUT", policy.onAttemptTimeout(), policy.attemptTimeout(),
                    null, escalateTimeout);
            tag(process, "endEvent", attrs("id", end));
            sequence(id("occurrence-entry", logical), start, attempt, null, Map.of());
            sequence(id("occurrence-exit", logical), attempt, end, null, Map.of());
            close(process, "subProcess");
            occurrenceSlots.put(logical, new OccurrenceSlotVO(nativeId, attempt, activity, wait, null));
            emitBoundaries(node, nativeId, attemptTimeout);
        }

        private void emitWorker(NodeVO node, String activity) {
            var registration = model.workers().get(node.id());
            var capability = registration.capability();
            var topic = physicalWorkerTopic(model.tenantId(), capability.capabilityKey(), capability.version(), registration.contractSha256());
            open(process, "serviceTask", attrs("id", activity, "flowable:type", "external-worker",
                    "flowable:topic", topic, "flowable:doNotIncludeVariables", "true"));
            open(process, "extensionElements", Map.of());
            for (String field : WORKER_FIELDS) {
                tag(process, "flowable:externalWorkerInParameter", attrs("source", field, "target", field));
            }
            tag(process, "flowable:externalWorkerOutParameter", attrs("source", "resultRef", "target", "resultRef"));
            close(process, "extensionElements");
            close(process, "serviceTask");
        }

        private void emitBoundaries(NodeVO node, String attached, String attemptTimeout) {
            var policy = node.binding().policy();
            String timeout = id("node-timeout", node.id());
            String error = id("node-error", node.id());
            String escalatedAttempt = null;
            boundary(node, timeout, attached, "NODE_TIMEOUT", policy.onNodeTimeout(), policy.nodeTimeout(), null, null);
            boundary(node, error, attached, "FAILURE", policy.onFailure(), null,
                    errorRef("TIANJI_AUTOMATION_NODE_FAILURE"), null);
            if (attemptTimeout != null || policy.onAttemptTimeout().branchTarget() != null) {
                escalatedAttempt = id("node-attempt-timeout", node.id());
                boundary(node, escalatedAttempt, attached, "ATTEMPT_TIMEOUT", policy.onAttemptTimeout(), null,
                        errorRef(attemptTimeoutCode(policy.onAttemptTimeout())), null);
            }
            policySlots.put(node.id(), new PolicySlotsVO(error, attemptTimeout, escalatedAttempt, timeout, policy.dispatchTimeout(),
                    policy.retry().maxAttempts(), policy.retry().backoff()));
        }

        private static String attemptTimeoutCode(PolicyActionVO action) {
            return "TIANJI_AUTOMATION_ATTEMPT_TIMEOUT_" + action.action();
        }

        private String errorRef(String code) {
            String id = id("error-code", code);
            errors.putIfAbsent(id, code);
            return id;
        }

        private void boundary(NodeVO node, String boundary, String attached, String outcome, PolicyActionVO action,
                              Duration duration, String errorRef, String throwError) {
            open(process, "boundaryEvent", attrs("id", boundary, "attachedToRef", attached));
            if (duration != null) timer(duration);
            if (errorRef != null) tag(process, "errorEventDefinition", attrs("errorRef", errorRef));
            close(process, "boundaryEvent");
            String recovery = boundary + "_recover";
            task(recovery, "tianjiAutomationPolicy", attrs("ta:occurrence", nativeNode(node.id()),
                    "ta:outcome", outcome, "ta:action", action.action()));
            sequence(boundary + "_entered", boundary, recovery, null, Map.of());
            String target;
            if (throwError != null) {
                target = boundary + "_end";
                open(process, "endEvent", attrs("id", target));
                tag(process, "errorEventDefinition", attrs("errorRef", throwError));
                close(process, "endEvent");
            } else if ("WAIT_RECOVERY".equals(action.action()) || "RETRY".equals(action.action())) {
                target = boundary + "_wait";
                messageWait(target);
            } else if ("END".equals(action.action())) {
                target = boundary + "_end";
                open(process, "endEvent", attrs("id", target));
                tag(process, "terminateEventDefinition", attrs("flowable:terminateAll", "true"));
                close(process, "endEvent");
            } else {
                target = nativeNode(action.branchTarget());
            }
            sequence(boundary + "_resolved", recovery, target, null, Map.of());
        }

        private void listeners() {
            open(process, "extensionElements", Map.of());
            tag(process, "flowable:executionListener", attrs("event", "start", "delegateExpression", "${tianjiAutomationScopeEnter}"));
            tag(process, "flowable:executionListener", attrs("event", "end", "delegateExpression", "${tianjiAutomationScopeExit}"));
            close(process, "extensionElements");
        }

        private void timer(Duration duration) {
            open(process, "timerEventDefinition", Map.of());
            textElement(process, "timeDuration", duration.toString());
            close(process, "timerEventDefinition");
        }

        private void messageWait(String activity) {
            String message = activity + "_message";
            messages.put(message, "tianji-v2:" + activity);
            open(process, "intermediateCatchEvent", attrs("id", activity));
            tag(process, "messageEventDefinition", attrs("messageRef", message));
            close(process, "intermediateCatchEvent");
        }

        private void task(String nativeId, String delegate, Map<String, String> metadata) {
            var attributes = attrs("id", nativeId, "flowable:delegateExpression", "${" + delegate + "}");
            attributes.putAll(metadata);
            tag(process, "serviceTask", attributes);
        }

        private void sequence(String nativeId, String from, String to, String condition, Map<String, String> metadata) {
            var attributes = attrs("id", nativeId, "sourceRef", from, "targetRef", to);
            attributes.putAll(metadata);
            if (condition == null) {
                tag(process, "sequenceFlow", attributes);
                return;
            }
            open(process, "sequenceFlow", attributes);
            open(process, "conditionExpression", attrs("xsi:type", "tFormalExpression"));
            process.append(escape(condition));
            close(process, "conditionExpression");
            close(process, "sequenceFlow");
        }

        private static String nativeNode(String logicalId) { return id("node", logicalId); }
    }

    private static Map<String, String> attrs(String... pairs) {
        var attributes = new LinkedHashMap<String, String>();
        for (int index = 0; index < pairs.length; index += 2) {
            attributes.put(pairs[index], pairs[index + 1]);
        }
        return attributes;
    }

    private static void textElement(StringBuilder xml, String tag, String text) {
        open(xml, tag, Map.of());
        xml.append(escape(text));
        close(xml, tag);
    }

    private static void tag(StringBuilder xml, String tag, Map<String, String> attributes) {
        appendTag(xml, tag, attributes);
        xml.append("/>\n");
    }

    private static void open(StringBuilder xml, String tag, Map<String, String> attributes) {
        appendTag(xml, tag, attributes);
        xml.append(">\n");
    }

    private static void close(StringBuilder xml, String tag) {
        xml.append("</").append(tag).append(">\n");
    }

    private static void appendTag(StringBuilder xml, String tag, Map<String, String> attributes) {
        xml.append('<').append(tag);
        attributes.forEach((key, value) -> xml.append(' ').append(key).append("=\"").append(escape(value)).append('"'));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    public record CompiledModelEvidenceVO(long tenantId, String profile, String compilerVersion, String rootDefinitionKey,
                                          String sourceSha256, String dependencySha256, String compiledSha256,
                                          String nativeFirstActivity, Map<String, String> resourceDigests, Map<String, String> nativeIds) {
        public CompiledModelEvidenceVO {
            resourceDigests = Map.copyOf(resourceDigests);
            nativeIds = Map.copyOf(nativeIds);
        }
    }

    public record CompiledAutomationModelVO(CompiledModelEvidenceVO evidence,
                                           AutomationModelSourceVO source,
                                           Map<String, Object> sourceManifest, Map<String, Object> dependencyManifest,
                                           Map<String, ModelArtifactVO> artifacts, CompiledControlBindingsVO controlBindings) {
        public CompiledAutomationModelVO {
            sourceManifest = freezeObject(sourceManifest);
            dependencyManifest = freezeObject(dependencyManifest);
            artifacts = Collections.unmodifiableMap(new TreeMap<>(artifacts));
        }

        public String rootDefinitionKey() { return evidence.rootDefinitionKey(); }
        public String sourceSha256() { return evidence.sourceSha256(); }
        public String dependencySha256() { return evidence.dependencySha256(); }
        public String compiledSha256() { return evidence.compiledSha256(); }
        public String nativeFirstActivity() { return evidence.nativeFirstActivity(); }
        public Map<String, String> nativeIds() { return evidence.nativeIds(); }
        public Map<String, ConditionVO> conditions() { return controlBindings.conditions(); }
        public Map<String, Integer> branchIndices() { return controlBindings.branchIndices(); }
        public Map<String, OccurrenceSlotVO> occurrenceSlots() { return controlBindings.occurrenceSlots(); }
        public Map<String, PolicySlotsVO> policySlots() { return controlBindings.policySlots(); }
    }

    static byte[] controlBindingsBytes(CompiledControlBindingsVO bindings) {
        return ProcessTypedJson.canonicalBytes(wire(bindings), 1024 * 1024);
    }

    public record CompiledControlBindingsVO(Map<String, ConditionVO> conditions, Map<String, Integer> branchIndices,
                                           Map<String, OccurrenceSlotVO> occurrenceSlots, Map<String, PolicySlotsVO> policySlots) {
        public CompiledControlBindingsVO {
            conditions = Map.copyOf(conditions);
            branchIndices = Map.copyOf(branchIndices);
            occurrenceSlots = Map.copyOf(occurrenceSlots);
            policySlots = Map.copyOf(policySlots);
        }
    }

    public record OccurrenceSlotVO(String scopeId, String attemptOrBodyId, String activityId, String waitId, String mergeId) { }
    public record PolicySlotsVO(String failureBoundaryId, String attemptTimeoutBoundaryId,
                                String attemptEscalationBoundaryId, String nodeTimeoutBoundaryId,
                                Duration dispatchTimeout, int maxAttempts, Duration retryBackoff) { }

    public record ModelArtifactVO(Type type, String definitionKey, byte[] xml) {
        public ModelArtifactVO { xml = xml.clone(); }
        @Override public byte[] xml() { return xml.clone(); }
        public String sha256() { return rawSha256(xml); }
    }
}
