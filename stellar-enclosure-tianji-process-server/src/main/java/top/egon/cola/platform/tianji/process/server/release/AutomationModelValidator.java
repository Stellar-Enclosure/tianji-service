package top.egon.cola.platform.tianji.process.server.release;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO.TemplateRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.CapabilityRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.ConditionLiteralVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.ConditionOperationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.ConditionPathVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.ConditionVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.LoopControlVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.MappingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.MultiInstanceControlVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.SubprocessControlVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EventRouteVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodePolicyVO.PolicyActionVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import javax.xml.XMLConstants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Validates untrusted source and resolves references only against the host's tenant-scoped snapshot. */
@Slf4j
@Validated
@Component("automationModelValidator")
public class AutomationModelValidator {
    static final String PROFILE = "AUTOMATION_V2";
    static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String BPMN_DI = "http://www.omg.org/spec/BPMN/20100524/DI";
    private static final String DI = "http://www.omg.org/spec/DD/20100524/DI";
    private static final String DC = "http://www.omg.org/spec/DD/20100524/DC";
    private static final int MAX_NODES = 128;
    private static final int MAX_ACTIVATIONS = 10_000;
    private static final int MAX_LEAVES = 32;
    private static final int MAX_SCOPE_DEPTH = 16;
    private static final int MAX_CONDITION_BYTES = 64 * 1024;
    private static final Pattern FIELD_SEGMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*|\\[[0-9]+]");
    private static final ProcessSchemaVO BOOLEAN = ProcessSchemaVO.fromWire(Map.of("type", "boolean"));
    private static final Set<String> FLOW_NODES = Set.of("startEvent", "endEvent", "exclusiveGateway", "parallelGateway",
            "serviceTask", "businessRuleTask", "userTask", "intermediateCatchEvent", "subProcess", "callActivity");
    private static final Map<String, Set<String>> ATTRIBUTES = Map.ofEntries(
            Map.entry("definitions", Set.of("id", "name", "targetNamespace", "exporter", "exporterVersion")),
            Map.entry("process", Set.of("id", "name", "isExecutable")),
            Map.entry("startEvent", Set.of("id", "name")), Map.entry("endEvent", Set.of("id", "name")),
            Map.entry("exclusiveGateway", Set.of("id", "name", "default", "gatewayDirection")),
            Map.entry("parallelGateway", Set.of("id", "name", "gatewayDirection")),
            Map.entry("serviceTask", Set.of("id", "name")), Map.entry("businessRuleTask", Set.of("id", "name")),
            Map.entry("userTask", Set.of("id", "name")), Map.entry("intermediateCatchEvent", Set.of("id", "name")),
            Map.entry("subProcess", Set.of("id", "name")), Map.entry("callActivity", Set.of("id", "name", "calledElement")),
            Map.entry("sequenceFlow", Set.of("id", "name", "sourceRef", "targetRef")),
            Map.entry("conditionExpression", Set.of()), Map.entry("documentation", Set.of("id", "textFormat")),
            Map.entry("incoming", Set.of()), Map.entry("outgoing", Set.of()),
            Map.entry("message", Set.of("id", "name")), Map.entry("messageEventDefinition", Set.of("id", "messageRef")),
            Map.entry("timerEventDefinition", Set.of("id")), Map.entry("timeDuration", Set.of()),
            Map.entry("standardLoopCharacteristics", Set.of("id")),
            Map.entry("multiInstanceLoopCharacteristics", Set.of("id", "isSequential")));
    private static final Map<String, Set<String>> DI_CHILDREN = Map.of(
            "BPMNDiagram", Set.of("BPMNPlane"), "BPMNPlane", Set.of("BPMNShape", "BPMNEdge"),
            "BPMNShape", Set.of("Bounds", "BPMNLabel"), "BPMNEdge", Set.of("waypoint", "BPMNLabel"),
            "BPMNLabel", Set.of("Bounds"), "Bounds", Set.of(), "waypoint", Set.of());
    private static final Map<String, Set<String>> DI_ATTRIBUTES = Map.of(
            "BPMNDiagram", Set.of("id", "name"), "BPMNPlane", Set.of("id", "bpmnElement"),
            "BPMNShape", Set.of("id", "bpmnElement", "isHorizontal", "isExpanded", "isMarkerVisible"),
            "BPMNEdge", Set.of("id", "bpmnElement"), "BPMNLabel", Set.of("id"), "Bounds", Set.of("x", "y", "width", "height"),
            "waypoint", Set.of("x", "y"));
    static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(32).maxStringLength(MAX_CONDITION_BYTES).maxNumberLength(32).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public ValidatedAutomationModelVO validate(@Valid @NotNull AutomationModelSourceVO source,
                                               @Valid @NotNull DependencySnapshotVO dependencies) {
        require(source != null && dependencies != null, "/", "MODEL_REQUIRED");
        var models = new LinkedHashMap<String, SourceModel>();
        var decisions = new LinkedHashMap<String, ValidatedProcessModel>();
        int remaining = SafeModelXml.MAX_AUTOMATION_SOURCE_BYTES;
        for (var asset : source.models()) {
            String path = "models." + asset.definitionKey();
            byte[] xml = utf8(asset.xml());
            boolean bpmn = "BPMN".equals(asset.type());
            try {
                Element root = SafeModelXml.parseAutomation(xml, bpmn, remaining).getDocumentElement();
                if (bpmn) {
                    checkTree(root, new HashSet<>(), path);
                    require("definitions".equals(root.getLocalName()), path, "BPMN_DEFINITIONS_REQUIRED");
                    Element process = only(root, "process", path);
                    require(asset.definitionKey().equals(process.getAttribute("id"))
                                    && (!process.hasAttribute("isExecutable") || "true".equals(process.getAttribute("isExecutable"))),
                            path, "MODEL_ID_MISMATCH");
                    Set<String> messages = children(root, "message").stream().map(value -> value.getAttribute("id"))
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
                    models.put(asset.definitionKey(), new SourceModel(process, messages, rawSha256(xml)));
                } else {
                    decisions.put(asset.definitionKey(), ProcessModelValidator.validateAutomationDecision(xml,
                            asset.definitionKey(), root));
                }
            } catch (InvalidAutomationModelException invalid) {
                throw invalid;
            } catch (IllegalArgumentException invalid) {
                throw problem(path, "INVALID_XML_MODEL");
            }
            remaining -= xml.length;
        }
        String rootKey = source.models().getFirst().definitionKey();
        var state = new ValidationState(source, dependencies, models, decisions);
        ScopeVO root = state.build(models.get(rootKey).process(), models.get(rootKey), "", Set.of(rootKey),
                source.inputSchema(), source.outputSchema(), 0);
        require(state.visitedModels.equals(models.keySet()), "models", "UNREFERENCED_BPMN");
        require(state.usedBindings.equals(state.bindings.keySet()), "nodeBindings", "UNMATCHED_BINDING");
        state.validateRoutes(rootKey);
        return new ValidatedAutomationModelVO(source, dependencies.tenantId(), rootKey, root,
                decisions, state.workerDependencies, state.templateDependencies, state.agentConfigurationDependencies,
                state.humanDependencies, state.routeDependencies);
    }

    private static final class ValidationState {
        private final AutomationModelSourceVO source;
        private final DependencySnapshotVO dependencies;
        private final Map<String, SourceModel> models;
        private final Map<String, ValidatedProcessModel> decisions;
        private final Map<String, AutomationNodeBindingVO> bindings = new LinkedHashMap<>();
        private final Set<String> usedBindings = new HashSet<>();
        private final Set<String> visitedModels = new HashSet<>();
        private final Set<String> qualifiedIds = new HashSet<>();
        private final Map<String, WorkerRegistrationVO> workerDependencies = new LinkedHashMap<>();
        private final Map<String, TemplateRegistrationVO> templateDependencies = new LinkedHashMap<>();
        private final Map<String, TemplateRefVO> agentConfigurationDependencies = new LinkedHashMap<>();
        private final Map<String, String> humanDependencies = new LinkedHashMap<>();
        private final Map<String, String> routeDependencies = new LinkedHashMap<>();
        private final Map<String, Map<String, ProcessSchemaVO>> eventWaitLocalInputs = new HashMap<>();
        private int nodeCount;

        private ValidationState(AutomationModelSourceVO source, DependencySnapshotVO dependencies,
                                Map<String, SourceModel> models, Map<String, ValidatedProcessModel> decisions) {
            this.source = source;
            this.dependencies = dependencies;
            this.models = models;
            this.decisions = decisions;
            source.nodeBindings().forEach(binding -> bindings.put(binding.nodeId(), binding));
            visitedModels.add(source.models().getFirst().definitionKey());
        }

        private ScopeVO build(Element container, SourceModel model, String scopeId, Set<String> callStack,
                              ProcessSchemaVO scopeInput, ProcessSchemaVO scopeOutput, int depth) {
            require(depth <= MAX_SCOPE_DEPTH, scopeId, "SCOPE_DEPTH_LIMIT");
            var nodes = new LinkedHashMap<String, Element>();
            for (Element node : children(container, null)) {
                if (!FLOW_NODES.contains(node.getLocalName())) continue;
                String nodeId = qualify(scopeId, node.getAttribute("id"));
                require(isId(nodeId) && qualifiedIds.add(nodeId), scopeId, "INVALID_QUALIFIED_NODE");
                require(Set.of("startEvent", "endEvent").contains(node.getLocalName()) || ++nodeCount <= MAX_NODES,
                        nodeId, "NODE_LIMIT");
                nodes.put(nodeId, node);
            }
            var edges = new LinkedHashMap<String, EdgeVO>();
            for (Element flow : children(container, "sequenceFlow")) {
                String edgeId = qualify(scopeId, flow.getAttribute("id"));
                String from = qualify(scopeId, flow.getAttribute("sourceRef"));
                String to = qualify(scopeId, flow.getAttribute("targetRef"));
                require(nodes.containsKey(from) && nodes.containsKey(to) && qualifiedIds.add(edgeId),
                        scopeId, "FLOW_OUTSIDE_SCOPE");
                List<Element> expressions = children(flow, "conditionExpression");
                ConditionVO condition = expressions.isEmpty() ? null : condition(expressions.getFirst(), edgeId);
                edges.put(edgeId, new EdgeVO(edgeId, from, to, condition));
            }
            var flow = new ScopeFlow(nodes, edges);
            String start = soleNode(nodes, "startEvent", scopeId);
            String end = soleNode(nodes, "endEvent", scopeId);
            flow.addPolicyBranches(bindings);
            flow.order(start, end, scopeId);
            var branchRegions = flow.parallelRegions(end);
            var nativeNodes = new LinkedHashMap<String, NodeVO>();
            for (var entry : nodes.entrySet()) {
                String id = entry.getKey();
                Element element = entry.getValue();
                String tag = element.getLocalName();
                List<EdgeVO> incoming = flow.normalIncoming.get(id);
                List<EdgeVO> outgoing = flow.normalOutgoing.get(id);
                String defaultEdge = element.hasAttribute("default") ? qualify(scopeId, element.getAttribute("default")) : null;
                Function<String, ProcessSchemaVO> variables = path -> availableField(path, id, flow, scopeInput, scopeOutput);
                checkConnections(element, id, incoming, outgoing, flow.incoming.get(id).size(), defaultEdge, variables);
                var binding = bindings.get(id);
                String kind = kind(tag, binding, incoming.size(), id);
                if (binding != null) {
                    require(kind.equals(binding.kind()) && usedBindings.add(id), id, "BINDING_KIND_MISMATCH");
                    validateBinding(binding, decisions, flow, variables, scopeInput, scopeOutput);
                } else {
                    require(kind.equals(tag), id, "BINDING_REQUIRED");
                }

                ScopeVO body = null;
                Duration timer = null;
                if ("subProcess".equals(tag)) {
                    body = controlBody(element, model, id, callStack, binding, flow, variables, depth);
                } else if ("callActivity".equals(tag)) {
                    var control = (SubprocessControlVO) binding.control();
                    SourceModel child = models.get(control.definitionKey());
                    require(child != null && control.definitionKey().equals(element.getAttribute("calledElement"))
                                    && control.modelSha256().equals(child.sha256()) && !callStack.contains(control.definitionKey()),
                            id, "INVALID_PINNED_SUBPROCESS");
                    visitedModels.add(control.definitionKey());
                    var active = new HashSet<>(callStack);
                    active.add(control.definitionKey());
                    body = build(child.process(), child, id, active, binding.inputSchema(), binding.outputSchema(), depth + 1);
                } else if ("intermediateCatchEvent".equals(tag)) {
                    timer = checkWait(element, model, kind, id);
                }
                nativeNodes.put(id, new NodeVO(id, tag, kind, defaultEdge, binding, body, timer));
            }
            for (NodeVO node : nativeNodes.values()) {
                if ("AND_JOIN".equals(node.kind())) {
                    require(branchRegions.containsKey(node.id()), node.id(), "UNSTRUCTURED_PARALLEL_JOIN");
                    var regions = branchRegions.get(node.id());
                    validateMerge(node.binding(), regions, bindings, regions.size(), sourceId ->
                            flow.incoming.get(node.id()).stream()
                                    .filter(edge -> regions.stream().anyMatch(branch -> branch.contains(sourceId) && branch.contains(edge.from())))
                                    .anyMatch(edge -> flow.availableOnSuccessfulEdge(sourceId, edge)));
                    validateBranchWrites(branchRegions.get(node.id()), node.id());
                }
                if ("MULTI_INSTANCE".equals(node.kind())) {
                    var control = (MultiInstanceControlVO) node.binding().control();
                    validateMerge(node.binding(), List.of(node.body().nodes().keySet()), bindings, control.maxItems(),
                            node.body().completedAtEnd()::contains);
                }
            }
            Budget budget = flow.budget(start, end, nativeNodes, new HashMap<>());
            var endOutputs = new HashSet<String>();
            for (String id : nativeNodes.keySet()) {
                if (!id.equals(end) && flow.availableAfterSuccessfulNode(id, end)) endOutputs.add(id);
            }
            return new ScopeVO(scopeId, start, end, nativeNodes, edges, flow.branchIndices, endOutputs, budget);
        }

        private ScopeVO controlBody(Element element, SourceModel model, String id, Set<String> callStack,
                                    AutomationNodeBindingVO binding, ScopeFlow flow,
                                    Function<String, ProcessSchemaVO> variables, int depth) {
            var loop = children(element, "standardLoopCharacteristics");
            var multi = children(element, "multiInstanceLoopCharacteristics");
            if (binding.control() instanceof LoopControlVO control) {
                require(multi.isEmpty(), id, "CONFLICTING_LOOP_DECLARATION");
                requireBoolean(control.condition(), path -> field(control.carrySchema(), path), id);
                ScopeVO body = build(element, model, id, callStack, control.carrySchema(), binding.outputSchema(), depth + 1);
                for (MappingVO mapping : control.carryMapping()) {
                    validateMapping(mapping.target(), mapping.source(), control.carrySchema(),
                            reference -> controlSource(reference, body, control.carrySchema(), id), id);
                }
                return body;
            }
            var control = (MultiInstanceControlVO) binding.control();
            require(loop.isEmpty() && (multi.isEmpty() || !multi.getFirst().hasAttribute("isSequential")
                            || Boolean.toString(control.sequential()).equals(multi.getFirst().getAttribute("isSequential"))),
                    id, "CONFLICTING_LOOP_DECLARATION");
            ProcessSchemaVO items = mappedSource(control.itemsSource(), binding, flow, variables, false);
            require(items != null && "array".equals(items.type()) && items.maxItems() <= control.maxItems(), id, "UNBOUNDED_ITEMS");
            if (control.completionCondition() != null) {
                requireBoolean(control.completionCondition(), path -> field(binding.outputSchema(), path), id);
            }
            return build(element, model, id, callStack, binding.inputSchema(), binding.outputSchema(), depth + 1);
        }

        private ProcessSchemaVO controlSource(String reference, ScopeVO body, ProcessSchemaVO carry, String id) {
            if (reference.startsWith("scope.")) return field(carry, reference.substring(6));
            if (reference.startsWith("input.")) return field(source.inputSchema(), reference.substring(6));
            var ref = outputRef(reference, body.nodes().keySet());
            if (ref == null || !bindings.containsKey(ref.nodeId())) return null;
            require(body.completedAtEnd().contains(ref.nodeId()), id, "UNFINISHED_SOURCE_NODE");
            return field(bindings.get(ref.nodeId()).outputSchema(), ref.field());
        }

        private void validateBinding(AutomationNodeBindingVO binding, Map<String, ValidatedProcessModel> decisions,
                                     ScopeFlow flow, Function<String, ProcessSchemaVO> variables,
                                     ProcessSchemaVO scopeInput, ProcessSchemaVO scopeOutput) {
            String id = binding.nodeId();
            boolean eventWait = "EVENT_WAIT".equals(binding.kind());
            var localEventInputs = eventWait ? new LinkedHashMap<String, ProcessSchemaVO>() : null;
            for (MappingVO entry : binding.inputMapping()) {
                if (entry.source().startsWith("event.")) {
                    require(eventWait, id, "EVENT_INPUT_OUTSIDE_WAIT");
                    continue;
                }
                var resolved = mappedSource(entry.source(), binding, flow, variables, false);
                validateMapping(entry.target(), entry.source(), binding.inputSchema(),
                        reference -> resolved, id);
                if (eventWait) localEventInputs.put(entry.target(), resolved);
            }
            if (eventWait) {
                eventWaitLocalInputs.put(id, Map.copyOf(localEventInputs));
            } else if (!"AND_JOIN".equals(binding.kind())) {
                validateConstructedObject(binding.inputSchema(), binding.inputMapping(), id);
            }
            for (MappingVO entry : binding.outputMapping()) {
                require(!entry.source().startsWith("event."), id, "EVENT_OUTPUT_SOURCE_UNAVAILABLE");
                var existing = field(scopeInput, entry.target());
                if (existing != null) {
                    require(accepts(existing, field(scopeOutput, entry.target())), id, "CONFLICTING_SCOPE_FIELD");
                }
                validateMapping(entry.target(), entry.source(), scopeOutput,
                        reference -> mappedSource(reference, binding, flow, variables, true), id);
            }
            if (binding.service() != null) {
                CapabilityRefVO ref = binding.service();
                WorkerRegistrationVO authority = dependencies.workers().get(new VersionedRefVO(ref.capabilityKey(), ref.version()));
                require(authority != null && ref.equals(authority.capability())
                                && ref.inputSchemaSha256().equals(schemaSha256(binding.inputSchema()))
                                && ref.outputSchemaSha256().equals(schemaSha256(binding.outputSchema())),
                        id, "UNVERIFIED_WORKER_CAPABILITY");
                workerDependencies.put(id, authority);
            }
            if (binding.rule() != null) {
                var ref = binding.rule();
                ValidatedProcessModel decision = decisions.get(ref.definitionKey());
                require(decision != null && ref.modelSha256().equals(rawSha256(decision.xml())), id, "UNVERIFIED_DMN_REFERENCE");
                require(ref.inputMapping().size() == decision.inputs().size()
                        && ref.outputMapping().size() <= decision.outputs().size(), id, "RULE_MAPPING_MISMATCH");
                for (MappingVO map : ref.inputMapping()) {
                    var expected = decision.inputs().get(map.target());
                    var actual = map.source().startsWith("input.") ? field(binding.inputSchema(), map.source().substring(6)) : null;
                    require(expected != null && scalarType(expected).equals(type(actual)), id, "RULE_INPUT_TYPE_MISMATCH");
                }
                for (MappingVO map : ref.outputMapping()) {
                    var expected = map.source().startsWith("scope.") ? decision.outputs().get(map.source().substring(6)) : null;
                    var actual = field(binding.outputSchema(), map.target());
                    require(expected != null && scalarType(expected).equals(type(actual)), id, "RULE_OUTPUT_TYPE_MISMATCH");
                }
                validateConstructedObject(binding.outputSchema(), ref.outputMapping(), id);
            }
            if (binding.agent() != null) {
                var agent = binding.agent();
                String input = schemaSha256(binding.inputSchema());
                String output = schemaSha256(binding.outputSchema());
                String params = schemaSha256(agent.parameterSchema());
                for (TemplateRefVO ref : agent.allowedTemplates()) {
                    var authority = dependencies.templates().get(new VersionedRefVO(ref.key(), ref.version()));
                    require(authority != null && ref.equals(authority.template())
                            && input.equals(authority.inputSchemaSha256()) && output.equals(authority.outputSchemaSha256())
                            && params.equals(authority.parameterSchemaSha256()), id, "UNVERIFIED_AGENT_TEMPLATE");
                    templateDependencies.put(id + "/" + ref.key() + "/" + ref.version(), authority);
                }
                if (agent.configurationRef() != null) {
                    require(dependencies.agentConfigurations().contains(agent.configurationRef()), id, "UNVERIFIED_AGENT_CONFIGURATION");
                    agentConfigurationDependencies.put(id, agent.configurationRef());
                }
            }
            if (binding.human() != null) {
                String hash = dependencies.humanCandidateDigests().get(binding.human().candidateBindingRef());
                require(isSha256(hash) && binding.outputSchema().toWire().equals(binding.human().resultSchema().toWire())
                                && binding.human().dueAfter().compareTo(binding.policy().nodeTimeout()) <= 0,
                        id, "UNVERIFIED_HUMAN_BINDING");
                humanDependencies.put(id, hash);
            }
        }

        private ProcessSchemaVO mappedSource(String reference, AutomationNodeBindingVO binding, ScopeFlow flow,
                                             Function<String, ProcessSchemaVO> variables, boolean afterNode) {
            if (reference.startsWith("input.")) return field(source.inputSchema(), reference.substring(6));
            if (reference.startsWith("scope.")) return variables.apply(reference.substring(6));
            var ref = outputRef(reference, flow.nodes.keySet());
            if (ref == null || !bindings.containsKey(ref.nodeId())) return null;
            require(afterNode && binding.nodeId().equals(ref.nodeId())
                            || !binding.nodeId().equals(ref.nodeId())
                            && flow.availableAfterSuccessfulNode(ref.nodeId(), binding.nodeId()),
                    binding.nodeId(), "UNFINISHED_SOURCE_NODE");
            return field(bindings.get(ref.nodeId()).outputSchema(), ref.field());
        }

        private ProcessSchemaVO availableField(String path, String reader, ScopeFlow flow,
                                                ProcessSchemaVO scopeInput, ProcessSchemaVO scopeOutput) {
            ProcessSchemaVO input = field(scopeInput, path);
            if (input != null) return input;
            ProcessSchemaVO output = field(scopeOutput, path);
            if (output == null) return null;
            var writers = new HashSet<String>();
            for (String candidate : flow.nodes.keySet()) {
                var binding = bindings.get(candidate);
                if (binding != null && !reader.equals(candidate)
                        && binding.outputMapping().stream().anyMatch(mapping -> containsPath(mapping.target(), path))) {
                    writers.add(candidate);
                }
            }
            return !writers.isEmpty() && flow.assignedOnAllPaths(reader, writers, new HashMap<>()) ? output : null;
        }

        private void validateBranchWrites(List<Set<String>> regions, String join) {
            var previous = new ArrayList<String>();
            for (Set<String> branch : regions) {
                var writes = branch.stream().map(bindings::get).filter(Objects::nonNull)
                        .flatMap(binding -> binding.outputMapping().stream()).map(MappingVO::target).toList();
                require(writes.stream().noneMatch(write -> previous.stream()
                                .anyMatch(prior -> containsPath(write, prior) || containsPath(prior, write))),
                        join, "CONCURRENT_SCOPE_WRITE");
                previous.addAll(writes);
            }
        }

        private void validateRoutes(String rootKey) {
            var routedWaits = new HashSet<String>();
            for (EventRouteVO route : source.eventRoutes()) {
                String id = route.routeKey();
                var authority = dependencies.routes().get(new VersionedRefVO(id, route.version()));
                require(authority != null && route.equals(authority.route()), id, "UNVERIFIED_EVENT_ROUTE");
                boolean start = "START".equals(route.mode());
                require(!start || rootKey.equals(route.targetDefinitionKey()), id, "EVENT_START_WRONG_DEFINITION");
                var target = start ? null : bindings.get(route.targetNodeId());
                require(start || target != null && "EVENT_WAIT".equals(target.kind()), id, "EVENT_SIGNAL_WRONG_NODE");
                ProcessSchemaVO schema = start ? source.inputSchema() : target.inputSchema();
                for (MappingVO entry : route.mapping()) {
                    validateMapping(entry.target(), entry.source(), schema,
                            reference -> field(route.payloadSchema(), reference.substring(6)), id);
                }
                if (start) {
                    validateConstructedObject(schema, route.mapping(), id);
                } else {
                    validateSignalRoute(route, target);
                    routedWaits.add(target.nodeId());
                }
                routeDependencies.put(id + "/" + route.version(), authority.contractSha256());
            }
            eventWaitLocalInputs.keySet().stream().filter(id -> !routedWaits.contains(id)).forEach(id -> {
                var binding = bindings.get(id);
                require(binding.inputMapping().stream().noneMatch(entry -> entry.source().startsWith("event.")),
                        id, "EVENT_SIGNAL_ROUTE_REQUIRED");
                validateConstructedObject(binding.inputSchema(), binding.inputMapping(), id);
            });
        }

        private void validateSignalRoute(EventRouteVO route, AutomationNodeBindingVO target) {
            String id = route.routeKey();
            var combined = new ArrayList<>(target.inputMapping());
            combined.addAll(route.mapping());
            validateConstructedObject(target.inputSchema(), combined, id);
            var supplied = new LinkedHashMap<>(eventWaitLocalInputs.get(target.nodeId()));
            for (var mapping : combined) {
                if (!mapping.source().startsWith("event.")) continue;
                var sourceSchema = field(route.payloadSchema(), mapping.source().substring("event.".length()));
                require(accepts(field(target.inputSchema(), mapping.target()), sourceSchema), id, "MAPPING_TYPE_MISMATCH");
                supplied.put(mapping.target(), sourceSchema);
            }
            require(projectsConstructedOutput(target.outputSchema(), target.inputSchema(), "", supplied),
                    id, "EVENT_WAIT_OUTPUT_NOT_CONSTRUCTIBLE");
            String ownOutput = "nodes." + target.nodeId() + ".output.";
            for (var mapping : target.outputMapping()) {
                if (!mapping.source().startsWith(ownOutput)) continue;
                String outputPath = mapping.source().substring(ownOutput.length());
                require(guaranteedProjectionPath(outputPath, supplied), id, "EVENT_WAIT_OUTPUT_MAPPING_SOURCE_MISSING");
            }
        }
    }

    private static boolean containsPath(String prefix, String path) {
        return path.equals(prefix) || path.startsWith(prefix + ".") || path.startsWith(prefix + "[");
    }

    private static final class ScopeFlow {
        private final Map<String, Element> nodes;
        private final Map<String, EdgeVO> edges = new LinkedHashMap<>();
        private final Map<String, List<EdgeVO>> normalIncoming = new LinkedHashMap<>();
        private final Map<String, List<EdgeVO>> normalOutgoing = new LinkedHashMap<>();
        private final Map<String, List<EdgeVO>> incoming = new LinkedHashMap<>();
        private final Map<String, List<EdgeVO>> outgoing = new LinkedHashMap<>();
        private final Map<String, String> joins = new HashMap<>();
        private final Map<String, Integer> branchIndices = new LinkedHashMap<>();
        private final Set<String> failureEdges = new HashSet<>();
        private final Map<List<String>, Boolean> availability = new HashMap<>();

        private ScopeFlow(Map<String, Element> nodes, Map<String, EdgeVO> edges) {
            this.nodes = nodes;
            for (String id : nodes.keySet()) {
                normalIncoming.put(id, new ArrayList<>());
                normalOutgoing.put(id, new ArrayList<>());
                incoming.put(id, new ArrayList<>());
                outgoing.put(id, new ArrayList<>());
            }
            for (EdgeVO edge : edges.values()) {
                normalIncoming.get(edge.to()).add(edge);
                normalOutgoing.get(edge.from()).add(edge);
                add(edge);
            }
        }

        private void add(EdgeVO edge) {
            edges.put(edge.id(), edge);
            incoming.get(edge.to()).add(edge);
            outgoing.get(edge.from()).add(edge);
        }

        private void addPolicyBranches(Map<String, AutomationNodeBindingVO> bindings) {
            for (String id : nodes.keySet()) {
                var binding = bindings.get(id);
                if (binding == null) continue;
                List<PolicyActionVO> actions = List.of(binding.policy().onFailure(), binding.policy().onAttemptTimeout(),
                        binding.policy().onNodeTimeout());
                for (int index = 0; index < actions.size(); index++) {
                    String target = actions.get(index).branchTarget();
                    if (target == null) continue;
                    require(nodes.containsKey(target) && !"startEvent".equals(nodes.get(target).getLocalName())
                                    && !"parallelGateway".equals(nodes.get(target).getLocalName()),
                            id, "POLICY_BRANCH_MUST_TARGET_SAME_SCOPE");
                    String edge = "policy/" + id + "/" + index;
                    failureEdges.add(edge);
                    add(new EdgeVO(edge, id, target, null));
                }
            }
        }

        private boolean availableAfterSuccessfulNode(String writer, String reader) {
            return availability.computeIfAbsent(List.of(writer, reader), ignored ->
                    assignedOnAllPaths(reader, Set.of(writer), new HashMap<>()));
        }

        private boolean availableOnSuccessfulEdge(String writer, EdgeVO edge) {
            return edge.from().equals(writer) && !failureEdges.contains(edge.id())
                    || availableAfterSuccessfulNode(writer, edge.from());
        }

        private boolean assignedOnAllPaths(String reader, Set<String> writers, Map<String, Boolean> memo) {
            Boolean known = memo.get(reader);
            if (known != null) return known;
            List<EdgeVO> entries = incoming.get(reader);
            boolean assigned = !entries.isEmpty();
            for (EdgeVO edge : entries) {
                if (!(writers.contains(edge.from()) && !failureEdges.contains(edge.id()))
                        && !assignedOnAllPaths(edge.from(), writers, memo)) {
                    assigned = false;
                    break;
                }
            }
            memo.put(reader, assigned);
            return assigned;
        }

        private void order(String start, String end, String scopeId) {
            Set<String> order = new LinkedHashSet<>();
            orderFrom(start, order, new HashSet<>(), 0);
            require(order.size() == nodes.size() && order.contains(end), scopeId, "UNREACHABLE_FLOW_NODE");
        }

        private void orderFrom(String id, Set<String> complete, Set<String> active, int depth) {
            require(depth <= MAX_NODES + 2 && !active.contains(id), id, "UNBOUNDED_FLOW_CYCLE");
            if (complete.contains(id)) return;
            active.add(id);
            for (EdgeVO edge : outgoing.get(id)) orderFrom(edge.to(), complete, active, depth + 1);
            active.remove(id);
            complete.add(id);
        }

        private Map<String, List<Set<String>>> parallelRegions(String end) {
            var regions = new LinkedHashMap<String, List<Set<String>>>();
            for (String split : nodes.keySet()) {
                List<EdgeVO> branches = outgoing.get(split);
                if (!"parallelGateway".equals(nodes.get(split).getLocalName()) || branches.size() < 2) continue;
                String join = firstSharedJoin(branches, end);
                require(join != null && !regions.containsKey(join), split, "UNSTRUCTURED_PARALLEL_SPLIT");
                var members = new HashSet<String>();
                var allIncoming = new HashSet<String>();
                var perBranch = new ArrayList<Set<String>>();
                for (int index = 0; index < branches.size(); index++) {
                    EdgeVO entry = branches.get(index);
                    Set<String> region = reachable(entry.to(), join, outgoing);
                    require(Collections.disjoint(members, region), split, "PARALLEL_BRANCHES_INTERSECT");
                    members.addAll(region);
                    perBranch.add(region);
                    if (region.isEmpty()) {
                        require(entry.to().equals(join) && allIncoming.add(entry.id()), split, "INVALID_PARALLEL_BRANCH");
                    } else {
                        var exits = incoming.get(join).stream().filter(edge -> region.contains(edge.from())).toList();
                        require(exits.size() == 1 && allIncoming.add(exits.getFirst().id()), split, "INVALID_PARALLEL_BRANCH_EXIT");
                        for (String member : region) {
                            require(incoming.get(member).stream().allMatch(edge -> region.contains(edge.from())
                                            || edge.id().equals(entry.id())),
                                    member, "EXTERNAL_BRANCH_ENTRY");
                        }
                    }
                    branchIndices.put(entry.id(), index);
                }
                require(allIncoming.size() == incoming.get(join).size(), join, "EXTERNAL_PARALLEL_JOIN_ENTRY");
                joins.put(split, join);
                regions.put(join, perBranch);
            }
            return regions;
        }

        private String firstSharedJoin(List<EdgeVO> branches, String end) {
            var candidates = new LinkedHashSet<String>();
            candidates.addAll(reachable(branches.getFirst().to(), null, outgoing));
            candidates.removeIf(id -> !"parallelGateway".equals(nodes.get(id).getLocalName())
                    || incoming.get(id).size() < 2
                    || branches.stream().anyMatch(branch -> !dominates(branch.to(), end, id, edges.values())));
            return candidates.stream().filter(id -> candidates.stream().noneMatch(other -> !other.equals(id)
                    && reachable(other, null, outgoing).contains(id))).findFirst().orElse(null);
        }

        private Budget budget(String id, String stop, Map<String, NodeVO> typed, Map<List<String>, Budget> memo) {
            if (id.equals(stop)) return new Budget(0, 1);
            List<String> path = List.of(id, stop);
            Budget known = memo.get(path);
            if (known != null) return known;
            NodeVO node = typed.get(id);
            long activations = node.binding() == null ? 0 : 1;
            long leaves = 1;
            if (node.body() != null) {
                int iterations = 1;
                int concurrent = 1;
                if (node.binding().control() instanceof LoopControlVO loop) iterations = loop.maxIterations();
                if (node.binding().control() instanceof MultiInstanceControlVO multi) {
                    iterations = multi.maxItems();
                    if (!multi.sequential()) concurrent = iterations;
                }
                activations += (long) iterations * node.body().budget().activations();
                leaves = (long) concurrent * node.body().budget().leaves();
            }
            if (joins.containsKey(id)) {
                long parallelActivations = 0;
                long parallelLeaves = 0;
                String join = joins.get(id);
                for (EdgeVO edge : outgoing.get(id)) {
                    Budget branch = budget(edge.to(), join, typed, memo);
                    parallelActivations += branch.activations();
                    parallelLeaves += branch.leaves();
                }
                Budget continuation = budget(join, stop, typed, memo);
                activations += parallelActivations + continuation.activations();
                leaves = Math.max(leaves, Math.max(parallelLeaves, continuation.leaves()));
            } else {
                long nextActivations = 0;
                for (EdgeVO edge : outgoing.get(id)) {
                    Budget next = budget(edge.to(), stop, typed, memo);
                    nextActivations = Math.max(nextActivations, next.activations());
                    leaves = Math.max(leaves, next.leaves());
                }
                activations += nextActivations;
            }
            require(activations <= MAX_ACTIVATIONS && leaves <= MAX_LEAVES, id, "MODEL_RUNTIME_LIMIT");
            var result = new Budget((int) activations, (int) leaves);
            memo.put(path, result);
            return result;
        }
    }

    private static void checkConnections(Element element, String id, List<EdgeVO> incoming, List<EdgeVO> outgoing,
                                         int allIncoming, String defaultEdge, Function<String, ProcessSchemaVO> variables) {
        String tag = element.getLocalName();
        int in = incoming.size();
        int out = outgoing.size();
        boolean valid = switch (tag) {
            case "startEvent" -> in == 0 && out == 1;
            case "endEvent" -> allIncoming > 0 && out == 0;
            case "parallelGateway" -> in == 1 && out > 1 || in > 1 && out == 1;
            case "exclusiveGateway" -> allIncoming > 0 && out > 0 && (allIncoming == 1 || out == 1);
            default -> in <= 1 && allIncoming > 0 && out == 1;
        };
        require(valid, id, "UNSUPPORTED_GATEWAY_OR_NODE_DEGREE");
        boolean exclusiveSplit = "exclusiveGateway".equals(tag) && out > 1;
        require(exclusiveSplit ? defaultEdge != null && outgoing.stream().anyMatch(edge -> edge.id().equals(defaultEdge))
                        : defaultEdge == null, id, "INVALID_DEFAULT_FLOW");
        String scope = id.substring(0, id.length() - element.getAttribute("id").length());
        checkFlowReferences(element, "incoming", incoming, scope, id);
        checkFlowReferences(element, "outgoing", outgoing, scope, id);
        if (element.hasAttribute("gatewayDirection")) {
            String direction = element.getAttribute("gatewayDirection");
            require("Unspecified".equals(direction) || "Diverging".equals(direction) && in == 1 && out > 1
                    || "Converging".equals(direction) && in > 1 && out == 1, id, "GATEWAY_DIRECTION_MISMATCH");
        }
        for (EdgeVO edge : outgoing) {
            boolean typed = exclusiveSplit && !edge.id().equals(defaultEdge);
            require(typed == (edge.condition() != null), edge.id(), "INVALID_CONDITIONAL_FLOW");
            if (typed) requireBoolean(edge.condition(), variables, edge.id());
        }
    }

    private static void checkFlowReferences(Element element, String tag, List<EdgeVO> expected, String scope, String id) {
        List<Element> actual = children(element, tag);
        if (actual.isEmpty()) return;
        Set<String> expectedIds = new HashSet<>();
        expected.forEach(edge -> expectedIds.add(edge.id()));
        require(actual.size() == expected.size() && actual.stream().allMatch(
                reference -> expectedIds.remove(scope + reference.getTextContent().trim())), id, "FLOW_REFERENCE_MISMATCH");
    }

    private static String kind(String tag, AutomationNodeBindingVO binding, int incoming, String id) {
        return switch (tag) {
            case "startEvent", "endEvent", "exclusiveGateway" -> tag;
            case "parallelGateway" -> incoming > 1 ? "AND_JOIN" : tag;
            case "serviceTask" -> binding != null && "AGENT".equals(binding.kind()) ? "AGENT" : "SERVICE";
            case "businessRuleTask" -> "RULE";
            case "userTask" -> "HUMAN";
            case "intermediateCatchEvent" -> {
                require(binding != null && Set.of("TIMER", "EVENT_WAIT").contains(binding.kind()), id, "WAIT_BINDING_REQUIRED");
                yield binding.kind();
            }
            case "callActivity" -> "SUBPROCESS";
            case "subProcess" -> {
                require(binding != null && Set.of("LOOP", "MULTI_INSTANCE").contains(binding.kind()), id, "BOUNDED_CONTROL_REQUIRED");
                yield binding.kind();
            }
            default -> throw problem(id, "UNSUPPORTED_NODE");
        };
    }

    private static Duration checkWait(Element element, SourceModel model, String kind, String id) {
        List<Element> timers = children(element, "timerEventDefinition");
        List<Element> messages = children(element, "messageEventDefinition");
        if ("EVENT_WAIT".equals(kind)) {
            require(timers.isEmpty() && messages.size() == 1
                    && model.messages().contains(messages.getFirst().getAttribute("messageRef")), id, "INVALID_MESSAGE_WAIT");
            return null;
        }
        require(messages.isEmpty() && timers.size() == 1, id, "INVALID_TIMER_WAIT");
        try {
            var duration = Duration.parse(only(timers.getFirst(), "timeDuration", id).getTextContent());
            require(duration.isPositive() && duration.compareTo(Duration.ofDays(30)) <= 0, id, "TIMER_DURATION_LIMIT");
            return duration;
        } catch (java.time.format.DateTimeParseException | ArithmeticException invalid) {
            throw problem(id, "INVALID_TIMER_DURATION");
        }
    }

    private static Set<String> reachable(String start, String stop, Map<String, List<EdgeVO>> outgoing) {
        var pending = new ArrayDeque<String>();
        var visited = new LinkedHashSet<String>();
        pending.add(start);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (current.equals(stop) || !visited.add(current)) continue;
            for (EdgeVO edge : outgoing.get(current)) pending.addLast(edge.to());
        }
        return visited;
    }

    private static boolean dominates(String from, String to, String through, Iterable<EdgeVO> edges) {
        var outgoing = new HashMap<String, List<EdgeVO>>();
        for (EdgeVO edge : edges) outgoing.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
        var pending = new ArrayDeque<String>();
        var visited = new HashSet<String>();
        pending.add(from);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (current.equals(through) || !visited.add(current)) continue;
            if (current.equals(to)) return false;
            for (EdgeVO edge : outgoing.getOrDefault(current, List.of())) pending.addLast(edge.to());
        }
        return true;
    }

    private static void validateMerge(AutomationNodeBindingVO join, List<Set<String>> regions,
                                      Map<String, AutomationNodeBindingVO> bindings, int maximumSlots,
                                      Predicate<String> completedSource) {
        for (var merge : join.merge()) {
            ProcessSchemaVO target = field(join.outputSchema(), merge.target());
            require(target != null, join.nodeId(), "MERGE_TARGET_NOT_DECLARED");
            var slots = new HashSet<Integer>();
            for (String source : merge.sourceNodeIds()) {
                var binding = bindings.get(source);
                int slot = -1;
                for (int index = 0; index < regions.size(); index++) {
                    if (regions.get(index).contains(source)) slot = index;
                }
                boolean multi = "MULTI_INSTANCE".equals(join.kind());
                require(binding != null && slot >= 0 && (multi || slots.add(slot)), join.nodeId(), "MERGE_SOURCE_OUTSIDE_BRANCH");
                require(completedSource.test(source), join.nodeId(), "MERGE_SOURCE_NOT_COMPLETED");
                ProcessSchemaVO value = field(binding.outputSchema(), merge.target());
                boolean compatible = switch (merge.reducer()) {
                    case "EXCLUSIVE" -> accepts(target, value) && merge.sourceNodeIds().size() == 1 && (!multi || maximumSlots == 1);
                    case "COLLECT_INDEXED" -> "array".equals(target.type())
                            && target.maxItems() >= (multi ? maximumSlots : merge.sourceNodeIds().size())
                            && accepts(target.items(), value);
                    case "ALL", "ANY" -> "boolean".equals(target.type()) && "boolean".equals(type(value));
                    case "SUM" -> "integer".equals(target.type()) && "integer".equals(type(value));
                    default -> false;
                };
                require(compatible, join.nodeId(), "MERGE_REDUCER_TYPE_MISMATCH");
            }
        }
    }

    private static void validateMapping(String target, String source, ProcessSchemaVO targetSchema,
                                        Function<String, ProcessSchemaVO> sourceSchema, String id) {
        require(accepts(field(targetSchema, target), sourceSchema.apply(source)), id, "MAPPING_TYPE_MISMATCH");
    }

    private static void validateConstructedObject(ProcessSchemaVO schema, List<MappingVO> mapping, String id) {
        var targets = new ArrayList<String>();
        for (MappingVO entry : mapping) {
            String target = entry.target();
            require(targets.stream().noneMatch(previous -> containsPath(previous, target) || containsPath(target, previous)),
                    id, "OVERLAPPING_MAPPING_TARGET");
            targets.add(target);
        }
        require(requiredTargetsCovered(schema, "", targets), id, "REQUIRED_MAPPING_TARGET_MISSING");
    }

    private static boolean requiredTargetsCovered(ProcessSchemaVO schema, String path, List<String> targets) {
        if (targets.contains(path)) return true;
        if ("array".equals(schema.type())) {
            boolean gap = false;
            for (int index = 0; index < schema.maxItems(); index++) {
                String item = path + "[" + index + "]";
                if (targets.stream().noneMatch(target -> containsPath(item, target))) {
                    gap = true;
                } else if (gap || !requiredTargetsCovered(schema.items(), item, targets)) {
                    return false;
                }
            }
            return true;
        }
        if (!"object".equals(schema.type())) return false;
        for (var property : schema.properties().entrySet()) {
            String key = path.isEmpty() ? property.getKey() : path + "." + property.getKey();
            boolean supplied = targets.stream().anyMatch(target -> containsPath(key, target));
            if ((supplied || schema.required().contains(property.getKey()))
                    && (!supplied || !requiredTargetsCovered(property.getValue(), key, targets))) return false;
        }
        return true;
    }

    private static boolean projectsConstructedOutput(ProcessSchemaVO output, ProcessSchemaVO input, String path,
                                                     Map<String, ProcessSchemaVO> supplied) {
        var source = supplied.get(path);
        if (source != null) return acceptsProjection(output, source);
        if (input == null || !output.type().equals(input.type())) return false;
        if ("object".equals(output.type())) {
            for (var property : output.properties().entrySet()) {
                String name = property.getKey();
                String child = path.isEmpty() ? name : path + "." + name;
                if (supplied.keySet().stream().anyMatch(target -> containsPath(child, target))) {
                    if (!projectsConstructedOutput(property.getValue(), input.properties().get(name), child, supplied)) return false;
                } else if (output.required().contains(name)) return false;
            }
            return true;
        }
        if ("array".equals(output.type())) {
            for (int index = 0; index < input.maxItems(); index++) {
                String item = path + "[" + index + "]";
                if (supplied.keySet().stream().noneMatch(target -> containsPath(item, target))) break;
                if (index >= output.maxItems() || !projectsConstructedOutput(output.items(), input.items(), item, supplied)) return false;
            }
            return true;
        }
        return false;
    }

    /** The native projection ignores input-only fields, including those nested in array items. */
    private static boolean acceptsProjection(ProcessSchemaVO output, ProcessSchemaVO source) {
        if (!output.type().equals(source.type())) return false;
        return switch (output.type()) {
            case "object" -> source.required().containsAll(output.required()) && output.properties().entrySet().stream()
                    .allMatch(entry -> !source.properties().containsKey(entry.getKey())
                            || acceptsProjection(entry.getValue(), source.properties().get(entry.getKey())));
            case "array" -> source.maxItems() <= output.maxItems()
                    && (source.maxItems() == 0 || acceptsProjection(output.items(), source.items()));
            default -> accepts(output, source);
        };
    }

    private static boolean guaranteedProjectionPath(String reference, Map<String, ProcessSchemaVO> supplied) {
        if (supplied.containsKey(reference) || supplied.keySet().stream().anyMatch(target -> containsPath(reference, target))) return true;
        for (var mapping : supplied.entrySet()) {
            String path = mapping.getKey();
            if (!containsPath(path, reference) || reference.charAt(path.length()) != '.') continue;
            var source = mapping.getValue();
            for (String part : reference.substring(path.length() + 1).split("\\.")) {
                if (!"object".equals(source.type()) || !source.required().contains(part)) return false;
                source = source.properties().get(part);
            }
            return true;
        }
        return false;
    }

    /** Conservatively proves that all values from source are legal for the target. */
    private static boolean accepts(ProcessSchemaVO target, ProcessSchemaVO source) {
        if (target == null || source == null || !target.type().equals(source.type())) return false;
        return switch (target.type()) {
            case "object" -> target.properties().keySet().containsAll(source.properties().keySet())
                    && source.required().containsAll(target.required())
                    && source.properties().entrySet().stream()
                    .allMatch(entry -> accepts(target.properties().get(entry.getKey()), entry.getValue()));
            case "array" -> source.maxItems() <= target.maxItems() && accepts(target.items(), source.items());
            case "integer" -> (target.minimum() == null || source.minimum() != null && source.minimum() >= target.minimum())
                    && (target.maximum() == null || source.maximum() != null && source.maximum() <= target.maximum());
            case "string" -> (target.enumValues() == null || source.enumValues() != null && target.enumValues().containsAll(source.enumValues()))
                    && (target.maxLength() == null || source.maxLength() != null && source.maxLength() <= target.maxLength())
                    && (target.pattern() == null || target.pattern().equals(source.pattern()))
                    && (target.format() == null || target.format().equals(source.format()));
            default -> true;
        };
    }

    private static ProcessSchemaVO field(ProcessSchemaVO schema, String path) {
        if (schema == null || path.isEmpty()) return null;
        for (String part : path.split("\\.", -1)) {
            var segment = FIELD_SEGMENT.matcher(part);
            if (!segment.lookingAt() || segment.group().startsWith("[") || !"object".equals(schema.type())) return null;
            schema = schema.properties().get(segment.group());
            int consumed = segment.end();
            while (schema != null && consumed < part.length()) {
                segment.region(consumed, part.length());
                if (!segment.lookingAt() || !segment.group().startsWith("[") || !"array".equals(schema.type())) return null;
                String key = segment.group();
                if (key.length() > 5 || !key.matches("\\[(?:0|[1-9][0-9]{0,2})]")) return null;
                if (Integer.parseInt(key.substring(1, key.length() - 1)) >= schema.maxItems()) return null;
                schema = schema.items();
                consumed = segment.end();
            }
            if (schema == null) return null;
        }
        return schema;
    }

    private static String scalarType(ValidatedProcessModel.ValueType type) {
        return type.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String type(ProcessSchemaVO schema) {
        return schema == null ? "" : schema.type();
    }

    private static OutputRef outputRef(String reference, Set<String> nodeIds) {
        if (!reference.startsWith("nodes.")) return null;
        OutputRef found = null;
        for (String candidate : nodeIds) {
            String prefix = "nodes." + candidate + ".output.";
            if (reference.startsWith(prefix)) {
                if (found != null) return null;
                found = new OutputRef(candidate, reference.substring(prefix.length()));
            }
        }
        return found;
    }

    private static ConditionVO condition(Element element, String id) {
        String json = element.getTextContent();
        require(utf8(json).length <= MAX_CONDITION_BYTES, id, "CONDITION_SIZE_LIMIT");
        try {
            var value = JSON.readValue(json, ConditionVO.class);
            require(value != null, id, "INVALID_CONDITION");
            return value;
        } catch (tools.jackson.core.JacksonException | IllegalArgumentException invalid) {
            throw problem(id, "INVALID_CONDITION");
        }
    }

    private static void requireBoolean(ConditionVO condition, Function<String, ProcessSchemaVO> variables, String id) {
        Operand operand = operand(condition, variables, id);
        require("boolean".equals(operand.type()), id, "CONDITION_MUST_RETURN_BOOLEAN");
    }

    private static Operand operand(ConditionVO condition, Function<String, ProcessSchemaVO> variables, String id) {
        if (condition instanceof ConditionPathVO path) {
            ProcessSchemaVO schema = variables.apply(path.path());
            require(schema != null, id, "UNKNOWN_CONDITION_PATH");
            return new Operand(schema.type(), schema, null);
        }
        if (condition instanceof ConditionLiteralVO literal) {
            Object value = literal.literal();
            String type = value == null ? "null" : value instanceof Boolean ? "boolean" : value instanceof String ? "string"
                    : value instanceof Number ? "integer" : "";
            require(!type.isEmpty(), id, "CONDITION_LITERAL_MUST_BE_SCALAR");
            return new Operand(type, null, value);
        }
        var operation = (ConditionOperationVO) condition;
        var args = operation.args().stream().map(arg -> operand(arg, variables, id)).toList();
        switch (operation.op()) {
            case "AND", "OR", "NOT" -> require(args.stream().allMatch(value -> "boolean".equals(value.type())),
                    id, "CONDITION_BOOLEAN_OPERAND_REQUIRED");
            case "GT", "GE", "LT", "LE" -> require(args.stream().allMatch(value -> "integer".equals(value.type()))
                            || args.stream().allMatch(value -> "string".equals(value.type()))
                            && args.stream().anyMatch(AutomationModelValidator::decimalField),
                    id, "CONDITION_NUMERIC_OPERAND_REQUIRED");
            case "EQ", "NE" -> require(args.getFirst().type().equals(args.getLast().type())
                            && Set.of("integer", "boolean", "string", "null").contains(args.getFirst().type()),
                    id, "CONDITION_OPERAND_TYPE_MISMATCH");
            default -> throw problem(id, "UNSUPPORTED_CONDITION_OPERATION");
        }
        assertConditionFormats(args, id);
        return new Operand("boolean", BOOLEAN, null);
    }

    private static boolean decimalField(Operand operand) {
        return operand.schema() != null && operand.schema().format() != null && operand.schema().format().startsWith("decimal:");
    }

    private static void assertConditionFormats(List<Operand> args, String id) {
        if (args.size() != 2 || args.stream().anyMatch(value -> !"string".equals(value.type()))) return;
        Operand first = args.getFirst();
        Operand last = args.getLast();
        if (first.schema() != null && last.schema() != null) {
            require(Objects.equals(first.schema().format(), last.schema().format()), id, "CONDITION_FORMAT_MISMATCH");
        } else {
            Operand declared = first.schema() != null ? first : last;
            if (declared.schema() != null && declared.schema().format() != null) {
                Object literal = declared == first ? last.literal() : first.literal();
                try {
                    require(Objects.equals(literal, declared.schema().normalizeValue(literal)), id, "NON_CANONICAL_CONDITION_LITERAL");
                } catch (IllegalArgumentException invalid) {
                    throw problem(id, "CONDITION_FORMAT_MISMATCH");
                }
            }
        }
    }

    private static void checkTree(Element element, Set<String> ids, String path) {
        String tag = element.getLocalName();
        String namespace = element.getNamespaceURI();
        boolean bpmn = BPMN.equals(namespace);
        String logicalId = element.getAttribute("id");
        if (!logicalId.isEmpty()) {
            boolean xmlId = logicalId.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}");
            require(xmlId && ids.add(logicalId), path, "INVALID_OR_DUPLICATE_XML_ID");
        }
        String here = logicalId.isEmpty() ? path : path + "." + logicalId;
        Set<String> allowed = bpmn ? ATTRIBUTES.get(tag) : DI_ATTRIBUTES.get(tag);
        String diagramNamespace = switch (tag) {
            case "Bounds" -> DC;
            case "waypoint" -> DI;
            default -> BPMN_DI;
        };
        require(allowed != null && (bpmn || diagramNamespace.equals(namespace)), here, "UNSUPPORTED_XML_ELEMENT");
        var attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            String ns = attribute.getNamespaceURI();
            String name = attribute.getLocalName();
            String value = attribute.getNodeValue();
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(ns)) continue;
            boolean type = bpmn && Set.of("conditionExpression", "timeDuration").contains(tag)
                    && XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(ns) && "type".equals(name)
                    && trustedFormalExpressionType(element, value);
            require((type || ns == null && allowed.contains(name)) && value.length() <= 512
                            && !value.contains("${") && !value.contains("#{"),
                    here, "UNSUPPORTED_XML_ATTRIBUTE");
        }
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) {
                boolean permitted = bpmn ? allowedChild(tag, child) : DI_CHILDREN.get(tag).contains(child.getLocalName());
                require(permitted, here, "UNSUPPORTED_XML_CHILD");
                checkTree(child, ids, here);
            } else if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                require(Set.of("conditionExpression", "documentation", "incoming", "outgoing", "timeDuration").contains(tag)
                        || node.getNodeValue().isBlank(), here, "UNEXPECTED_XML_TEXT");
            } else {
                require(node.getNodeType() == Node.COMMENT_NODE
                        && !Set.of("conditionExpression", "timeDuration").contains(tag), here, "UNSUPPORTED_XML_NODE");
            }
        }
    }

    private static boolean trustedFormalExpressionType(Element element, String type) {
        int prefix = type.indexOf(':');
        return prefix < 0 ? "tFormalExpression".equals(type) && BPMN.equals(element.lookupNamespaceURI(null))
                : "tFormalExpression".equals(type.substring(prefix + 1))
                        && BPMN.equals(element.lookupNamespaceURI(type.substring(0, prefix)));
    }

    private static boolean allowedChild(String parent, Element child) {
        String tag = child.getLocalName();
        if ("definitions".equals(parent) && BPMN_DI.equals(child.getNamespaceURI())) return "BPMNDiagram".equals(tag);
        if (!BPMN.equals(child.getNamespaceURI())) return false;
        if ("documentation".equals(tag) && !"documentation".equals(parent)) return true;
        if (FLOW_NODES.contains(parent) && Set.of("incoming", "outgoing").contains(tag)) return true;
        return switch (parent) {
            case "definitions" -> Set.of("process", "message").contains(tag);
            case "process" -> FLOW_NODES.contains(tag) || "sequenceFlow".equals(tag);
            case "subProcess" -> FLOW_NODES.contains(tag) || "sequenceFlow".equals(tag)
                    || Set.of("standardLoopCharacteristics", "multiInstanceLoopCharacteristics").contains(tag);
            case "sequenceFlow" -> "conditionExpression".equals(tag);
            case "intermediateCatchEvent" -> Set.of("messageEventDefinition", "timerEventDefinition").contains(tag);
            case "timerEventDefinition" -> "timeDuration".equals(tag);
            default -> false;
        };
    }

    private static String soleNode(Map<String, Element> nodes, String tag, String scopeId) {
        var matches = nodes.entrySet().stream().filter(node -> tag.equals(node.getValue().getLocalName())).toList();
        require(matches.size() == 1, scopeId, "ONE_START_AND_END_REQUIRED");
        return matches.getFirst().getKey();
    }

    private static Element only(Element element, String tag, String id) {
        List<Element> children = children(element, tag);
        require(children.size() == 1, id, "EXPECTED_XML_ELEMENT_MISSING");
        return children.getFirst();
    }

    private static List<Element> children(Element element, String tag) {
        var values = new ArrayList<Element>();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element value && (tag == null || tag.equals(value.getLocalName()))) values.add(value);
        }
        return values;
    }

    private static String qualify(String scope, String local) {
        return scope.isEmpty() ? local : scope + "." + local;
    }

    static String schemaSha256(ProcessSchemaVO schema) {
        return rawSha256(ProcessTypedJson.canonicalBytes(schema.toWire(), 32_768));
    }

    static boolean isId(String value) {
        return value != null && value.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}");
    }

    static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    static String rawSha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static void require(boolean accepted, String path, String code) {
        if (!accepted) throw problem(path, code);
    }

    private static InvalidAutomationModelException problem(String path, String code) {
        String boundedPath = path.isEmpty() ? "/" : path.substring(0, Math.min(path.length(), 256));
        return new InvalidAutomationModelException(List.of(new ModelIssueVO(boundedPath, code)));
    }

    public record ModelIssueVO(String path, String code) { }

    public static final class InvalidAutomationModelException extends IllegalArgumentException {
        private final List<ModelIssueVO> issues;

        private InvalidAutomationModelException(List<ModelIssueVO> issues) {
            super("Automation model is invalid or not supported");
            this.issues = List.copyOf(issues);
        }

        public List<ModelIssueVO> issues() { return issues; }
    }

    /** Created by the host from its verified registries. Public draft data supplies no authority. */
    public record DependencySnapshotVO(long tenantId, Map<VersionedRefVO, WorkerRegistrationVO> workers,
                                       Map<VersionedRefVO, TemplateRegistrationVO> templates,
                                       Set<TemplateRefVO> agentConfigurations, Map<String, String> humanCandidateDigests,
                                       Map<VersionedRefVO, EventRegistrationVO> routes) {
        public DependencySnapshotVO {
            if (tenantId <= 0) throw new IllegalArgumentException("Invalid automation dependency tenant");
            workers = Map.copyOf(workers);
            templates = Map.copyOf(templates);
            agentConfigurations = Set.copyOf(agentConfigurations);
            humanCandidateDigests = Map.copyOf(humanCandidateDigests);
            routes = Map.copyOf(routes);
        }

        public static DependencySnapshotVO empty(long tenantId) {
            return new DependencySnapshotVO(tenantId, Map.of(), Map.of(), Set.of(), Map.of(), Map.of());
        }
    }

    public record VersionedRefVO(String key, long version) {
        public VersionedRefVO {
            if (!(isId(key) || AutomationNodeBindingVO.isCapabilityKey(key))
                    || version <= 0 || version > ProcessTypedJson.V2_MAX_WIRE_INTEGER) {
                throw new IllegalArgumentException("Invalid automation dependency reference");
            }
        }
    }

    public record WorkerRegistrationVO(CapabilityRefVO capability, String contractSha256) {
        public WorkerRegistrationVO {
            Objects.requireNonNull(capability, "capability");
            checkDigest(contractSha256);
        }
    }

    public record TemplateRegistrationVO(TemplateRefVO template, String inputSchemaSha256, String outputSchemaSha256,
                                         String parameterSchemaSha256) {
        public TemplateRegistrationVO {
            Objects.requireNonNull(template, "template");
            checkDigest(inputSchemaSha256);
            checkDigest(outputSchemaSha256);
            checkDigest(parameterSchemaSha256);
        }
    }

    public record EventRegistrationVO(EventRouteVO route, String contractSha256) {
        public EventRegistrationVO {
            Objects.requireNonNull(route, "route");
            checkDigest(contractSha256);
        }
    }

    private static void checkDigest(String value) {
        if (!isSha256(value)) throw new IllegalArgumentException("Invalid automation dependency digest");
    }

    public static final class ValidatedAutomationModelVO {
        private final AutomationModelSourceVO source;
        private final long tenantId;
        private final String rootDefinitionKey;
        private final ScopeVO graph;
        private final Map<String, ValidatedProcessModel> decisions;
        private final Map<String, WorkerRegistrationVO> workers;
        private final Map<String, TemplateRegistrationVO> templates;
        private final Map<String, TemplateRefVO> agentConfigurationDependencies;
        private final Map<String, String> humanDependencies;
        private final Map<String, String> routeDependencies;

        private ValidatedAutomationModelVO(AutomationModelSourceVO source, long tenantId, String rootDefinitionKey,
                                           ScopeVO graph, Map<String, ValidatedProcessModel> decisions,
                                           Map<String, WorkerRegistrationVO> workers, Map<String, TemplateRegistrationVO> templates,
                                           Map<String, TemplateRefVO> agentConfigurationDependencies,
                                           Map<String, String> humanDependencies, Map<String, String> routeDependencies) {
            this.source = source;
            this.tenantId = tenantId;
            this.rootDefinitionKey = rootDefinitionKey;
            this.graph = graph;
            this.decisions = Map.copyOf(decisions);
            this.workers = Map.copyOf(workers);
            this.templates = Map.copyOf(templates);
            this.agentConfigurationDependencies = Map.copyOf(agentConfigurationDependencies);
            this.humanDependencies = Map.copyOf(humanDependencies);
            this.routeDependencies = Map.copyOf(routeDependencies);
        }

        public AutomationModelSourceVO source() { return source; }
        public long tenantId() { return tenantId; }
        public String rootDefinitionKey() { return rootDefinitionKey; }
        ScopeVO graph() { return graph; }
        Map<String, ValidatedProcessModel> decisions() { return decisions; }
        Map<String, WorkerRegistrationVO> workers() { return workers; }
        Map<String, TemplateRegistrationVO> templates() { return templates; }
        Map<String, TemplateRefVO> agentConfigurationDependencies() { return agentConfigurationDependencies; }
        Map<String, String> humanDependencies() { return humanDependencies; }
        Map<String, String> routeDependencies() { return routeDependencies; }
    }

    record ScopeVO(String id, String start, String end, Map<String, NodeVO> nodes, Map<String, EdgeVO> edges,
                   Map<String, Integer> branchIndices, Set<String> completedAtEnd, Budget budget) {
        ScopeVO {
            nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
            edges = Collections.unmodifiableMap(new LinkedHashMap<>(edges));
            branchIndices = Map.copyOf(branchIndices);
            completedAtEnd = Set.copyOf(completedAtEnd);
        }
    }

    record NodeVO(String id, String tag, String kind, String defaultEdge, AutomationNodeBindingVO binding,
                  ScopeVO body, Duration timer) { }
    record EdgeVO(String id, String from, String to, ConditionVO condition) { }
    record Budget(int activations, int leaves) { }
    private record SourceModel(Element process, Set<String> messages, String sha256) { }
    private record OutputRef(String nodeId, String field) { }
    private record Operand(String type, ProcessSchemaVO schema, Object literal) { }
}
