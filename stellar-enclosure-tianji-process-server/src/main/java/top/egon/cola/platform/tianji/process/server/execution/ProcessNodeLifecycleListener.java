package top.egon.cola.platform.tianji.process.server.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.flowable.engine.impl.bpmn.behavior.BoundaryTimerEventActivityBehavior;
import org.flowable.engine.impl.bpmn.parser.BpmnParse;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.parse.BpmnParseHandler;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.KindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.SideEffectOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler.OccurrenceSlotVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore.ImmutableReleaseVO;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessNodeLifecycleListener {
    private static final String MODEL_NAMESPACE = "urn:stellar-enclosure:tianji:automation:2";
    private static final String ENTER = "${tianjiAutomationScopeEnter}";
    private static final String EXIT = "${tianjiAutomationScopeExit}";
    private static final String POLICY = "${tianjiAutomationPolicy}";
    private static final String TIMER_CAPTURE = ProcessNodeLifecycleListener.class.getName() + ".boundary";

    @Qualifier("processInstanceBindingStore") private final ProcessInstanceBindingStore bindings;
    @Qualifier("processNodeExecutionStore") private final ProcessNodeExecutionStore nodes;
    @Qualifier("automationReleaseStore") private final AutomationReleaseStore releases;
    @Qualifier("automationHumanCandidateDirectory") private final ObjectProvider<HumanCandidateDirectory> candidates;

    public static void register(SpringProcessEngineConfiguration configuration, Supplier<ProcessNodeLifecycleListener> listener) {
        var existing = configuration.getPostBpmnParseHandlers();
        List<BpmnParseHandler> handlers = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        handlers.add(new LifecycleParseHandler(listener));
        configuration.setPostBpmnParseHandlers(List.copyOf(handlers));
    }

    void onScope(DelegateExecution event, boolean enter) {
        var context = context(event);
        var scope = context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(item -> event.getCurrentActivityId().equals(item.getValue().scopeId())).findFirst().orElse(null);
        if (scope == null) throw unavailable();
        var binding = binding(context.release(), scope.getKey());
        var target = exactOccurrence(event, scope.getValue());
        if (!enter) {
            var row = nodes.findByOccurrence(context.binding().getTenantId(), context.binding().getInstanceId(),
                    occurrenceId(context.binding(), scope.getKey(), target.getId())).orElse(null);
            if (row != null) finishExit(context, binding, row, target);
            return;
        }
        if (scope.getValue().attemptOrBodyId() == null || isControl(binding.kind())) {
            insert(context, binding, scope.getKey(), target, target, scope.getValue(), null);
        }
    }

    void onAttempt(DelegateExecution event) {
        var context = context(event);
        var scope = context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(item -> event.getCurrentActivityId().equals(item.getValue().attemptOrBodyId())).findFirst().orElse(null);
        if (scope == null) return;
        var binding = binding(context.release(), scope.getKey());
        if (isControl(binding.kind())) return;
        var occurrence = exactOccurrence(event.getParent(), scope.getValue());
        insert(context, binding, scope.getKey(), occurrence, event, scope.getValue(), null);
    }

    void onUserTask(DelegateTask task, boolean complete) {
        if (task.getExecutionId() == null || task.getProcessInstanceId() == null) throw unavailable();
        var execution = CommandContextUtil.getExecutionEntityManager().findById(task.getExecutionId());
        var context = context(execution);
        var scope = context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(item -> task.getTaskDefinitionKey().equals(item.getValue().activityId())).findFirst().orElseThrow(ProcessNodeLifecycleListener::unavailable);
        var logical = binding(context.release(), scope.getKey());
        if (logical.human() == null) throw unavailable();
        var attempt = ancestor(execution, scope.getValue().attemptOrBodyId());
        var occurrence = exactOccurrence(attempt.getParent(), scope.getValue());
        if (!complete) insert(context, logical, scope.getKey(), occurrence, attempt, scope.getValue(), null);
        var row = nodes.findAttempt(context.binding().getTenantId(), context.binding().getInstanceId(), attempt.getId())
                .orElseThrow(ProcessNodeLifecycleListener::unavailable);
        if (!scope.getKey().equals(row.getNodeId()) || !occurrenceId(context.binding(), scope.getKey(), occurrence.getId()).equals(row.getOccurrenceId())) {
            throw unavailable();
        }
        if (complete) {
            if (row.getState() != StateEnum.RESULT_ACCEPTED || row.getOutput() == null) throw unavailable();
            finishExit(context, logical, row, occurrence);
            return;
        }
        String expectedSha = expectedHumanPolicySha256(context.release(), scope.getKey());
        var policy = requireCandidatePolicy(context.binding().getTenantId(), logical.human().candidateBindingRef(), expectedSha);
        task.addCandidateGroups(policy.lanes().get(policy.initialLane()).stream().map(role -> "role:" + role).sorted().toList());
        Instant due = row.getStartedAt().plus(logical.human().dueAfter());
        task.setDueDate(Date.from(due.isBefore(row.getAttemptDeadlineAt()) ? due : row.getAttemptDeadlineAt()));
    }

    private boolean beforeBoundaryTimer(DelegateExecution event, BoundaryEvent boundary) {
        var context = context(event);
        var verified = context.verified().controlBindings();
        var slot = verified.policySlots().entrySet().stream()
                .filter(item -> boundary.getId().equals(item.getValue().attemptTimeoutBoundaryId())
                        || boundary.getId().equals(item.getValue().nodeTimeoutBoundaryId()))
                .findFirst().orElseThrow(ProcessNodeLifecycleListener::unavailable);
        var occurrenceSlot = verified.occurrenceSlots().get(slot.getKey());
        var occurrence = exactOccurrence(event.getParent(), occurrenceSlot);
        var root = context.binding();
        var row = nodes.findByOccurrence(root.getTenantId(), root.getInstanceId(),
                occurrenceId(root, slot.getKey(), occurrence.getId())).orElseThrow(ProcessNodeLifecycleListener::unavailable);
        var attemptDeadline = boundary.getId().equals(slot.getValue().attemptTimeoutBoundaryId());
        if (attemptDeadline && !boundary.getAttachedToRefId().equals(occurrenceSlot.attemptOrBodyId())) throw unavailable();
        if (attemptDeadline && !row.getAttemptScopeId().equals(event.getParentId())) return false;
        if (row.getState() == StateEnum.RESULT_ACCEPTED || row.getState() == StateEnum.COMPLETED
                || row.getState() == StateEnum.CANCELLED || row.getState() == StateEnum.TIMED_OUT || row.getState() == StateEnum.FAILED) {
            return false;
        }
        var deadline = attemptDeadline ? row.getAttemptDeadlineAt() : row.getNodeDeadlineAt();
        if (nodes.now().isBefore(deadline)) throw unavailable();
        var captured = new BoundaryCaptureVO(root.getTenantId(), root.getInstanceId(), row.getNodeExecutionRef(),
                attemptDeadline ? "ATTEMPT_TIMEOUT" : "NODE_TIMEOUT", occurrenceSlot.scopeId(), row.getOccurrenceId());
        var command = CommandContextUtil.getCommandContext();
        var recoveryKey = TIMER_CAPTURE + boundary.getId() + "_recover";
        if (command.getAttribute(recoveryKey) != null) throw unavailable();
        command.addAttribute(recoveryKey, captured);
        nodes.finish(row, StateEnum.TIMED_OUT);
        return true;
    }

    void onPolicy(DelegateExecution execution, String outcome, String action, String nativeOccurrence) {
        var context = context(execution);
        var slot = context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(item -> nativeOccurrence.equals(item.getValue().scopeId())).findFirst().orElseThrow(ProcessNodeLifecycleListener::unavailable);
        var root = context.binding();
        var command = CommandContextUtil.getCommandContext();
        String recovery = execution.getCurrentActivityId();
        var captureKey = TIMER_CAPTURE + recovery;
        if (command.getAttribute(captureKey) instanceof BoundaryCaptureVO captured) {
            command.removeAttribute(captureKey);
            var slots = context.verified().controlBindings().policySlots().get(slot.getKey());
            if (slots == null) throw unavailable();
            if (captured.tenantId() != root.getTenantId() || !captured.instanceId().equals(root.getInstanceId())
                    || !captured.nativeOccurrence().equals(nativeOccurrence) || !captured.outcome().equals(outcome)
                    || !"END".equals(action) || nodes.find(root.getTenantId(), root.getInstanceId(), captured.nodeExecutionRef())
                            .filter(row -> row.getState() == StateEnum.TIMED_OUT
                                    && row.getOccurrenceId().equals(captured.exactOccurrenceId())).isEmpty()) throw unavailable();
            if ("ATTEMPT_TIMEOUT".equals(outcome) && recovery.equals(slots.attemptTimeoutBoundaryId() + "_recover")) {
                if (slots.attemptEscalationBoundaryId() == null) throw unavailable();
                String escalation = TIMER_CAPTURE + slots.attemptEscalationBoundaryId() + "_recover";
                if (command.getAttribute(escalation) != null) throw unavailable();
                command.addAttribute(escalation, captured);
            }
            return;
        }
        if ("ATTEMPT_TIMEOUT".equals(outcome) || "NODE_TIMEOUT".equals(outcome)) throw unavailable();
        var occurrence = ancestor(execution, nativeOccurrence);
        var row = nodes.findByOccurrence(root.getTenantId(), root.getInstanceId(),
                        occurrenceId(root, slot.getKey(), occurrence.getId()))
                .orElseThrow(ProcessNodeLifecycleListener::unavailable);
        if (row.getState() == StateEnum.RESULT_ACCEPTED) throw unavailable();
        if (row.getState() == StateEnum.CANCELLED || row.getState() == StateEnum.COMPLETED) throw unavailable();
        if (row.getState() == StateEnum.TIMED_OUT) return;
        if (!"END".equals(action)) throw unavailable(); // Other retry/branch delegates are installed with their owning runtime steps.
        nodes.finish(row, "FAILURE".equals(outcome) ? StateEnum.FAILED : StateEnum.TIMED_OUT);
    }

    public HumanCandidatePolicyVO requireCandidatePolicy(long tenantId, String bindingRef, String expectedPolicySha256) {
        var directory = candidates.getIfAvailable();
        if (directory == null || !ProcessInstanceBindingStore.sha256(expectedPolicySha256)) throw unavailable();
        var result = directory.find(tenantId, bindingRef, expectedPolicySha256).orElseThrow(ProcessNodeLifecycleListener::unavailable);
        if (!expectedPolicySha256.equals(result.policySha256())) throw unavailable();
        return result;
    }

    public static String expectedHumanPolicySha256(ImmutableReleaseVO release, String logicalNodeId) {
        var human = object(release.dependencies().get("humanCandidates"));
        if (!(human.get(logicalNodeId) instanceof String sha256) || !ProcessInstanceBindingStore.sha256(sha256)) throw unavailable();
        return sha256;
    }

    private void insert(LifecycleContextVO context, AutomationNodeBindingVO binding, String nodeId, DelegateExecution occurrence,
                        DelegateExecution attempt, OccurrenceSlotVO slot, String nativeJobId) {
        var root = context.binding();
        var existing = nodes.findAttempt(root.getTenantId(), root.getInstanceId(), attempt.getId());
        if (existing.isPresent()) return;
        var input = formInput(context, binding, occurrence);
        String inputSchema = schemaSha256(binding.inputSchema());
        Instant now = nodes.now();
        var capability = binding.service();
        var occurrenceId = occurrenceId(root, nodeId, occurrence.getId());
        var executionRef = "node-" + hash("AUTOMATION_NODE_EXECUTION_V2", List.of(root.getTenantId(), root.getInstanceId(), occurrenceId, 1));
        var row = new ProcessNodeExecutionBO().setTenantId(root.getTenantId()).setInstanceId(root.getInstanceId())
                .setOccurrenceId(occurrenceId).setAttempt(1).setNodeExecutionRef(executionRef).setNodeId(nodeId)
                .setKind(KindEnum.valueOf(binding.kind())).setParentScopeId(occurrence.getParentId()).setAttemptScopeId(attempt.getId())
                .setNativeJobId(nativeJobId).setInput(input).setInputSchemaSha256(inputSchema)
                .setInputSha256(ProcessNodeExecutionStore.digest("AUTOMATION_NODE_INPUT_V2", inputSchema, input))
                .setOutputSchemaSha256(schemaSha256(binding.outputSchema())).setConfigurationSha256(root.getConfigurationSha256())
                .setState(waiting(binding.kind()) ? StateEnum.WAITING : StateEnum.RUNNING).setRevision(1)
                .setStartedAt(now).setNodeDeadlineAt(now.plus(binding.policy().nodeTimeout()))
                .setAttemptDeadlineAt(now.plus(binding.policy().attemptTimeout())).setSideEffectOutcome(SideEffectOutcomeEnum.NONE);
        if (capability != null) {
            row.setCapabilityKey(capability.capabilityKey()).setCapabilityVersion(capability.version()).setEffectKey("effect-"
                    + hash("AUTOMATION_NODE_EFFECT_V2", List.of(root.getTenantId(), root.getInstanceId(), occurrenceId,
                            capability.capabilityKey(), capability.version())));
        }
        nodes.insertOccurrence(row);
        occurrence.setVariableLocal("tianjiAutomationNodeOccurrence", occurrence.getId());
    }

    private void finishExit(LifecycleContextVO context, AutomationNodeBindingVO binding, ProcessNodeExecutionBO row,
                            DelegateExecution occurrence) {
        if (row.getState() == StateEnum.RESULT_ACCEPTED) {
            if (binding.human() != null && row.getOutput() != null) applyHumanOutput(context, binding, row, occurrence);
            nodes.finish(row, StateEnum.COMPLETED);
        } else if (row.getState() == StateEnum.RUNNING || row.getState() == StateEnum.WAITING && "TIMER".equals(binding.kind())) {
            nodes.finish(row, StateEnum.COMPLETED);
        } else if (row.getState() == StateEnum.WAITING && "HUMAN".equals(binding.kind())) {
            throw unavailable();
        }
    }

    private void applyHumanOutput(LifecycleContextVO context, AutomationNodeBindingVO binding, ProcessNodeExecutionBO row,
                                  DelegateExecution occurrence) {
        if (binding.outputMapping().isEmpty()) return;
        var parent = occurrence.getParent();
        if (parent == null || !context.binding().getInstanceId().equals(parent.getRootProcessInstanceId())) throw unavailable();
        var engine = context.engine();
        Object previous = parent.getVariableLocal(AutomationBootstrapBehavior.SCOPE);
        var data = previous == null ? new LinkedHashMap<String, Object>()
                : mutableObject(object(ProcessExecutionService.decodeNativeJson(engine, previous, ProcessNodeExecutionStore.MAX_DATA)));
        String outputPrefix = "nodes." + binding.nodeId() + ".output.";
        for (var mapping : binding.outputMapping()) {
            String source = mapping.source();
            Object value;
            if (source.startsWith(outputPrefix)) {
                value = lookup(row.getOutput(), source.substring(outputPrefix.length()));
            } else if (source.startsWith("output.")) {
                value = lookup(row.getOutput(), source.substring(7));
            } else if (source.startsWith("input.")) {
                value = lookup(rootInput(context), source.substring(6));
            } else if (source.startsWith("scope.")) {
                value = lookup(data, source.substring(6));
            } else {
                throw unavailable();
            }
            String target = mapping.target().startsWith("scope.") ? mapping.target().substring(6) : mapping.target();
            assign(data, target, value);
        }
        parent.setVariableLocal(AutomationBootstrapBehavior.SCOPE,
                engine.getVariableJsonMapper().readTree(ProcessTypedJson.canonicalBytes(data, ProcessNodeExecutionStore.MAX_DATA)));
    }

    private static LinkedHashMap<String, Object> mutableObject(Map<String, Object> data) {
        var result = new LinkedHashMap<String, Object>();
        data.forEach((key, value) -> result.put(key, value instanceof Map<?, ?> ? mutableObject(object(value)) : value));
        return result;
    }

    private Map<String, Object> formInput(LifecycleContextVO context, AutomationNodeBindingVO binding, DelegateExecution occurrence) {
        var engine = context.engine();
        var roots = rootInput(context);
        var parent = occurrence.getParent();
        if (parent == null) throw unavailable();
        Object scopeRef = parent.getVariable(AutomationBootstrapBehavior.SCOPE);
        Map<String, Object> scope = scopeRef == null ? Map.of()
                : object(ProcessExecutionService.decodeNativeJson(engine, scopeRef, ProcessNodeExecutionStore.MAX_DATA));
        var form = new LinkedHashMap<String, Object>();
        for (var mapping : binding.inputMapping()) {
            int separator = mapping.source().indexOf('.');
            String root = mapping.source().substring(0, separator);
            Object value = lookup(switch (root) {
                case "input" -> roots;
                case "scope" -> scope;
                default -> throw unavailable();
            }, mapping.source().substring(separator + 1));
            assign(form, mapping.target(), value);
        }
        return object(binding.inputSchema().normalizeValue(form));
    }

    private static Map<String, Object> rootInput(LifecycleContextVO context) {
        var engine = context.engine();
        Object rootRef = engine.getRuntimeService().getVariableLocal(context.binding().getInstanceId(), AutomationBootstrapBehavior.ROOT_INPUTS);
        return object(ProcessExecutionService.decodeNativeJson(engine, rootRef, ProcessNodeExecutionStore.MAX_DATA));
    }

    private static Object lookup(Map<String, Object> object, String path) {
        Object current = object;
        var segments = java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*|\\[[0-9]+\\]").matcher(path);
        while (segments.find()) {
            String component = segments.group();
            if (component.startsWith("[")) {
                if (!(current instanceof List<?> values)) throw unavailable();
                int index = Integer.parseInt(component.substring(1, component.length() - 1));
                if (index >= values.size()) throw unavailable();
                current = values.get(index);
            } else {
                var properties = object(current);
                if (!properties.containsKey(component)) throw unavailable();
                current = properties.get(component);
            }
        }
        return current;
    }

    private static void assign(Map<String, Object> target, String path, Object value) {
        var parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int index = 0; index < parts.length - 1; index++) {
            if (parts[index].contains("[")) throw unavailable();
            current = object(current.computeIfAbsent(parts[index], ignored -> new LinkedHashMap<String, Object>()));
        }
        if (parts[parts.length - 1].contains("[")) throw unavailable();
        current.put(parts[parts.length - 1], value);
    }

    private LifecycleContextVO context(DelegateExecution execution) {
        if (execution == null || !(CommandContextUtil.getProcessEngineConfiguration() instanceof SpringProcessEngineConfiguration process)
                || !(CommandContextUtil.getDmnEngineConfiguration() instanceof SpringDmnEngineConfiguration dmn)
                || execution.getRootProcessInstanceId() == null || execution.getTenantId() == null
                || !execution.getTenantId().matches("[1-9][0-9]{0,18}")) throw unavailable();
        long tenant = Long.parseLong(execution.getTenantId());
        String root = execution.getRootProcessInstanceId();
        AutomationInstanceGuardCommandInterceptor.requireHeld(tenant, root);
        var binding = bindings.find(tenant, root).orElseThrow(ProcessNodeLifecycleListener::unavailable);
        var release = releases.find(tenant, binding.getReleaseKey()).orElseThrow(ProcessNodeLifecycleListener::unavailable);
        var validation = new ProcessModelValidator();
        var deployment = new ProcessReleaseService(process, dmn, validation).getForProcessDefinition(tenant, execution.getProcessDefinitionId());
        if (!release.releaseVersion().equals(deployment.releaseKey())) throw unavailable();
        var verified = new VerifiedProcessModels(process, dmn, validation).automation(tenant, release.compilerVersion(), release.sourceSha256(),
                release.compiledSha256(), release.dependencySha256(), release.source(), release.dependencies(), deployment);
        if (!execution.getProcessDefinitionId().equals(verified.root().engineDefinitionId())) throw unavailable();
        return new LifecycleContextVO(process, binding, release, verified);
    }

    private static DelegateExecution exactOccurrence(DelegateExecution execution, OccurrenceSlotVO slot) {
        return ancestor(execution, slot.scopeId());
    }

    private static DelegateExecution ancestor(DelegateExecution execution, String nativeId) {
        for (var next = execution; next != null; next = next.getParent()) {
            if (nativeId.equals(next.getCurrentActivityId())) return next;
        }
        throw unavailable();
    }

    private static AutomationNodeBindingVO binding(ImmutableReleaseVO release, String nodeId) {
        return release.source().nodeBindings().stream().filter(item -> nodeId.equals(item.nodeId())).findFirst()
                .orElseThrow(ProcessNodeLifecycleListener::unavailable);
    }

    private static boolean isControl(String kind) {
        return Set.of("SUBPROCESS","LOOP","MULTI_INSTANCE","AND_JOIN").contains(kind);
    }

    private static boolean positiveRoleId(String role) {
        if (role == null || !role.matches("[1-9][0-9]{0,18}")) return false;
        try {
            return Long.parseLong(role) > 0;
        } catch (NumberFormatException outOfRange) {
            return false;
        }
    }

    private static boolean waiting(String kind) {
        return Set.of("HUMAN","SERVICE","AGENT","EVENT_WAIT","TIMER").contains(kind);
    }

    public static String schemaSha256(ProcessSchemaVO schema) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ProcessTypedJson.canonicalBytes(schema.toWire(), 32_768)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw unavailable();
        }
    }

    private static String occurrenceId(AutomationInstanceBindingBO root, String nodeId, String nativeScopeId) {
        return "occ-" + hash("AUTOMATION_NODE_OCCURRENCE_V2", List.of(root.getTenantId(), root.getInstanceId(), nodeId, nativeScopeId));
    }

    private static String hash(String domain, Object value) {
        return ProcessTypedJson.sha256(domain, ProcessTypedJson.canonicalBytes(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw unavailable();
        return (Map<String,Object>) map;
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Automation node lifecycle evidence is unavailable or inconsistent");
    }

    private record BoundaryCaptureVO(long tenantId, String instanceId, String nodeExecutionRef, String outcome,
                                     String nativeOccurrence, String exactOccurrenceId) { }

    private record LifecycleContextVO(SpringProcessEngineConfiguration engine, AutomationInstanceBindingBO binding,
            ImmutableReleaseVO release, VerifiedProcessModels.VerifiedAutomationModelsVO verified) { }

    @FunctionalInterface
    public interface HumanCandidateDirectory {
        /** Resolves prebound local policy only. This callback runs in the owning Flowable transaction. */
        Optional<HumanCandidatePolicyVO> find(long tenantId, String bindingRef, String expectedPolicySha256);
    }

    public record HumanCandidatePolicyVO(Map<String, Set<String>> lanes, String initialLane, String policySha256) {
        public HumanCandidatePolicyVO(Set<String> roleNames, String policySha256) {
            this(Map.of("default", roleNames), "default", policySha256);
        }

        public HumanCandidatePolicyVO {
            if (lanes == null || lanes.isEmpty() || lanes.size() > 32 || !lanes.containsKey(initialLane)
                    || !ProcessInstanceBindingStore.sha256(policySha256)) throw unavailable();
            var copy = new LinkedHashMap<String, Set<String>>();
            lanes.forEach((lane, roles) -> {
                if (lane == null || !lane.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}") || roles == null || roles.isEmpty() || roles.size() > 32
                        || roles.stream().anyMatch(role -> !positiveRoleId(role))) throw unavailable();
                copy.put(lane, Set.copyOf(roles));
            });
            if (Set.copyOf(copy.values()).size() != copy.size()) throw unavailable();
            lanes = Collections.unmodifiableMap(copy);
        }
    }

    private record LifecycleParseHandler(Supplier<ProcessNodeLifecycleListener> listener) implements BpmnParseHandler {
        @Override
        public Collection<Class<? extends BaseElement>> getHandledTypes() { return List.of(SubProcess.class, UserTask.class, ServiceTask.class, BoundaryEvent.class); }

        @Override
        public void parse(BpmnParse parse, BaseElement element) {
            if (parse.getCurrentProcess() == null
                    || !"AUTOMATION_V2".equals(parse.getCurrentProcess().getAttributeValue(MODEL_NAMESPACE, "profile"))) return;
            if (element instanceof SubProcess subprocess) {
                var listeners = new ArrayList<>(subprocess.getExecutionListeners());
                if (subprocess.getAttributeValue(MODEL_NAMESPACE, "occurrence") != null
                        && listeners.stream().noneMatch(item -> ENTER.equals(item.getImplementation()))) {
                    listeners.add(nativeListener("start", (ExecutionListener) execution -> listener.get().onAttempt(execution)));
                }
                listeners.replaceAll(item -> switch (item.getImplementation() == null ? "" : item.getImplementation()) {
                    case ENTER -> nativeListener(item.getEvent(), (ExecutionListener) execution -> listener.get().onScope(execution, true));
                    case EXIT -> nativeListener(item.getEvent(), (ExecutionListener) execution -> listener.get().onScope(execution, false));
                    default -> item;
                });
                subprocess.setExecutionListeners(listeners);
            }
            if (element instanceof UserTask task && task.getId().startsWith("ta_")) {
                var listeners = new ArrayList<>(task.getTaskListeners());
                listeners.add(nativeListener("create", (TaskListener) nativeTask -> listener.get().onUserTask(nativeTask, false)));
                listeners.add(nativeListener("complete", (TaskListener) nativeTask -> listener.get().onUserTask(nativeTask, true)));
                task.setTaskListeners(listeners);
            }
            if (element instanceof BoundaryEvent boundary && boundary.getId().startsWith("ta_") && boundary.isCancelActivity()
                    && boundary.getEventDefinitions().size() == 1) {
                var event = boundary.getEventDefinitions().getFirst();
                if (event instanceof TimerEventDefinition timer) {
                    boundary.setBehavior(new GuardedBoundaryTimer(listener, boundary, timer));
                }
            }
            if (element instanceof ServiceTask task && POLICY.equals(task.getImplementation()) && task.getId().startsWith("ta_")) {
                String outcome = task.getAttributeValue(MODEL_NAMESPACE, "outcome");
                String action = task.getAttributeValue(MODEL_NAMESPACE, "action");
                String occurrence = task.getAttributeValue(MODEL_NAMESPACE, "occurrence");
                task.setBehavior(new PolicyActivity(listener, outcome, action, occurrence));
            }
        }

        private static FlowableListener nativeListener(String event, Object implementation) {
            var result = new FlowableListener();
            result.setEvent(event);
            result.setImplementationType("instance");
            result.setInstance(implementation);
            return result;
        }
    }

    private static final class GuardedBoundaryTimer extends BoundaryTimerEventActivityBehavior {
        private final Supplier<ProcessNodeLifecycleListener> listener;
        private final BoundaryEvent boundary;

        private GuardedBoundaryTimer(Supplier<ProcessNodeLifecycleListener> listener, BoundaryEvent boundary, TimerEventDefinition timer) {
            super(timer, true);
            this.listener = listener;
            this.boundary = boundary;
        }

        @Override
        public void trigger(DelegateExecution execution, String signal, Object data) {
            if (listener.get().beforeBoundaryTimer(execution, boundary)) super.trigger(execution, signal, data);
        }
    }

    private static final class PolicyActivity extends AbstractBpmnActivityBehavior {
        private final Supplier<ProcessNodeLifecycleListener> listener;
        private final String outcome;
        private final String action;
        private final String occurrence;

        private PolicyActivity(Supplier<ProcessNodeLifecycleListener> listener, String outcome, String action, String occurrence) {
            this.listener = listener;
            this.outcome = outcome;
            this.action = action;
            this.occurrence = occurrence;
        }

        @Override
        public void execute(DelegateExecution execution) {
            listener.get().onPolicy(execution, outcome, action, occurrence);
            leave(execution);
        }
    }
}
