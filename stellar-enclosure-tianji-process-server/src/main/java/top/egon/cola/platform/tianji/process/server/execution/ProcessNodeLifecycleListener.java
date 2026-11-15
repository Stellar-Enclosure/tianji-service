package top.egon.cola.platform.tianji.process.server.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExternalWorkerServiceTask;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.MessageEventDefinition;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowElementsContainer;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.TerminateEventDefinition;
import org.flowable.bpmn.model.UserTask;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.common.engine.impl.javax.el.ELContext;
import org.flowable.common.engine.impl.javax.el.ELResolver;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.flowable.engine.impl.bpmn.behavior.BoundaryTimerEventActivityBehavior;
import org.flowable.engine.impl.bpmn.behavior.ExternalWorkerTaskActivityBehavior;
import org.flowable.engine.impl.bpmn.behavior.IntermediateCatchMessageEventActivityBehavior;
import org.flowable.engine.impl.bpmn.helper.ErrorPropagation;
import org.flowable.engine.impl.bpmn.parser.BpmnParse;
import org.flowable.engine.impl.jobexecutor.TimerEventHandler;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.CountingEntityUtil;
import org.flowable.engine.impl.util.TimerUtil;
import org.flowable.engine.interceptor.CreateExternalWorkerJobAfterContext;
import org.flowable.engine.interceptor.CreateExternalWorkerJobBeforeContext;
import org.flowable.engine.interceptor.CreateExternalWorkerJobInterceptor;
import org.flowable.engine.parse.BpmnParseHandler;
import org.flowable.eventsubscription.service.impl.persistence.entity.EventSubscriptionEntity;
import org.flowable.job.service.JobHandler;
import org.flowable.job.service.JobProcessor;
import org.flowable.job.service.JobProcessorContext;
import org.flowable.job.service.impl.persistence.entity.AbstractRuntimeJobEntity;
import org.flowable.job.service.impl.persistence.entity.ExternalWorkerJobEntity;
import org.flowable.job.service.impl.persistence.entity.ExternalWorkerJobEntityManager;
import org.flowable.job.service.impl.persistence.entity.JobEntity;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;
import org.flowable.variable.api.delegate.VariableScope;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.ConditionLiteralVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.ConditionOperationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.ConditionPathVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.ConditionVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.LoopControlVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.MappingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.MergeVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.MultiInstanceControlVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.SubprocessControlVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.KindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.SideEffectOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodePolicyVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EventRouteVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO;
import top.egon.cola.platform.tianji.process.server.decision.FlowablePinnedDecisionExecutor.ProcessReference;
import top.egon.cola.platform.tianji.process.server.decision.PinnedDecisionActivityBehaviorFactory;
import top.egon.cola.platform.tianji.process.server.decision.ProcessDecisionService;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.event.EventSubscriptionBO;
import top.egon.cola.platform.tianji.process.server.event.ProcessEventSubscriptionStore;
import top.egon.cola.platform.tianji.process.server.event.ProcessLifecycleEventPublisher;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler.ControlPredicate;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler.OccurrenceSlotVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore.ImmutableReleaseVO;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerLeaseStore;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessNodeLifecycleListener {
    private static final Pattern FIELD_SEGMENTS = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*|\\[[0-9]+\\]");
    private static final String MODEL_NAMESPACE = "urn:stellar-enclosure:tianji:automation:2";
    private static final String ENTER = "${tianjiAutomationScopeEnter}";
    private static final String EXIT = "${tianjiAutomationScopeExit}";
    private static final String POLICY = "${tianjiAutomationPolicy}";
    private static final String TIMER_CAPTURE = ProcessNodeLifecycleListener.class.getName() + ".boundary";
    private static final String WORKER_JOB_CAPTURE = ProcessNodeLifecycleListener.class.getName() + ".workerJob";
    private static final String AGENT_DISPATCH = "${tianjiAutomationAgentDispatch}";
    private static final String EXTERNAL_WORKER_COMPLETION = "external-worker-complete";
    private static final String WORKER_FAILURE_CONFIG = "error:TIANJI_AUTOMATION_NODE_FAILURE";
    private static final String NODE_REF = "nodeExecutionRef";
    private static final String INPUT_SHA = "inputSha256";
    private static final String CONFIGURATION_SHA = "configurationSha256";
    private static final String RESULT_REF = "resultRef";
    private static final String LOCAL_INPUT = "tianjiAutomationLocalInput";
    private static final String BRANCH_SCOPE = "tianjiAutomationBranchScope_";
    private static final String JOIN_ARRIVAL = "tianjiAutomationJoinArrival_";
    private static final String MULTI_ITEMS = "tianjiAutomationMultiItems";
    private static final String MULTI_FINISHED = "tianjiAutomationMultiFinished";
    private static final String MULTI_SELECTED = "tianjiAutomationMultiSelected";
    private static final String MULTI_NODE_REF = "tianjiAutomationMultiNodeRef_";
    private static final String LOOP_CARRY = "tianjiAutomationLoopCarry";
    private static final String LOOP_INDEX = "tianjiAutomationLoopIndex";
    private static final String LOOP_ITERATIONS = "tianjiAutomationLoopIterations";
    private static final String OCCURRENCE_DEADLINE = "tianjiAutomationWorkerDeadline";
    private static final String RECOVERY_MESSAGE = "tianji-v2:worker-recovery:";
    private static final String ATTEMPT_TIMEOUT = "ATTEMPT_TIMEOUT";
    private static final String NODE_TIMEOUT = "NODE_TIMEOUT";
    private static final String RETRY_JOB = "tianji-automation-worker-retry";
    public static final String WORKER_RECOVERY_PROOF_EXPIRED = "WORKER_RECOVERY_PROOF_EXPIRED";
    public static final String WORKER_RECOVERY_PROOF_MISMATCH = "WORKER_RECOVERY_PROOF_MISMATCH";

    @Qualifier("processInstanceBindingStore") private final ProcessInstanceBindingStore bindings;
    @Qualifier("processNodeExecutionStore") private final ProcessNodeExecutionStore nodes;
    @Qualifier("automationReleaseStore") private final AutomationReleaseStore releases;
    @Qualifier("automationHumanCandidateDirectory") private final ObjectProvider<HumanCandidateDirectory> candidates;
    @Qualifier("processEventSubscriptionStore") private final ProcessEventSubscriptionStore subscriptions;
    @Qualifier("processLifecycleEventPublisher") private final ProcessLifecycleEventPublisher lifecycleEvents;
    @Qualifier("automationEventReservationDirectory") private final EventReservationDirectory eventReservations;

    public ProcessNodeLifecycleListener(ProcessInstanceBindingStore bindings, ProcessNodeExecutionStore nodes,
            AutomationReleaseStore releases, ObjectProvider<HumanCandidateDirectory> candidates) {
        this(bindings, nodes, releases, candidates, null, null, null);
    }

    public static void register(SpringProcessEngineConfiguration configuration, Supplier<ProcessNodeLifecycleListener> listener) {
        var existing = configuration.getPostBpmnParseHandlers();
        List<BpmnParseHandler> handlers = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        handlers.add(new LifecycleParseHandler(listener));
        configuration.setPostBpmnParseHandlers(List.copyOf(handlers));
        configuration.setCreateExternalWorkerJobInterceptor(
                new WorkerJobCreationInterceptor(configuration.getCreateExternalWorkerJobInterceptor(), listener));
        var existingProcessors = configuration.getJobProcessors();
        List<JobProcessor> processors = existingProcessors == null ? new ArrayList<>() : new ArrayList<>(existingProcessors);
        processors.add(new WorkerCompletionJobCapture(listener));
        configuration.setJobProcessors(List.copyOf(processors));
        configuration.addCustomJobHandler(new WorkerRetryJobHandler(listener));
        configuration.addPreDefaultELResolver(new ControlExpressionResolver(listener));
    }

    private boolean onCondition(LifecycleContextVO context, DelegateExecution execution, String flowId) {
        var predicate = context.verified().controlBindings().conditions().get(flowId);
        if (!(execution.getCurrentFlowElement() instanceof ExclusiveGateway gateway)
                || predicate == null || gateway.getOutgoingFlows().stream().noneMatch(flow -> flowId.equals(flow.getId())
                        && ControlPredicate.XOR.expression(flowId).equals(flow.getConditionExpression()))) {
            throw unavailable();
        }
        var parent = requireParent(execution);
        var localInput = localInput(context, parent);
        var localScope = visibleScope(context, parent, gateway);
        return test(predicate, path -> localField(localInput, localScope, path));
    }

    private boolean continueLoop(LifecycleContextVO context, DelegateExecution execution, String flowId) {
        var parent = requireParent(execution);
        var slot = controlScope(context, parent);
        var definition = binding(context.release(), slot.getKey());
        if (!(definition.control() instanceof LoopControlVO loop)
                || !AutomationModelCompiler.loopContinuationFlowId(slot.getKey()).equals(flowId)
                || !(execution.getCurrentFlowElement() instanceof ExclusiveGateway gateway)
                || gateway.getOutgoingFlows().stream().noneMatch(flow -> flowId.equals(flow.getId())
                        && ControlPredicate.LOOP_CONTINUATION.expression(flowId).equals(flow.getConditionExpression()))) throw unavailable();
        var condition = context.verified().controlBindings().conditions().get(flowId);
        if (!loop.condition().equals(condition) || !(parent.getVariableLocal(LOOP_ITERATIONS) instanceof Integer completed)
                || completed < 1 || completed > loop.maxIterations()) throw unavailable();
        var carry = nativeObject(context, parent.getVariableLocal(LOOP_CARRY));
        boolean proceed = test(condition, path -> lookup(carry, path));
        if (proceed && completed == loop.maxIterations()) throw unavailable();
        return proceed;
    }

    private List<Object> multiItems(LifecycleContextVO context, DelegateExecution execution, String bodyId) {
        var slot = bodySlot(context, bodyId);
        var definition = binding(context.release(), slot.getKey());
        if (!(definition.control() instanceof MultiInstanceControlVO multi) || !insideBody(execution, bodyId)) {
            throw unavailable();
        }
        var occurrence = exactOccurrence(execution, slot.getValue());
        var items = frozenItems(context, occurrence, multi);
        return items.stream().map(value -> context.engine().getVariableJsonMapper()
                .readTree(ProcessTypedJson.canonicalBytes(value, ProcessNodeExecutionStore.MAX_DATA))).toList();
    }

    private boolean completeMulti(LifecycleContextVO context, DelegateExecution execution, String bodyId) {
        var slot = bodySlot(context, bodyId);
        var definition = binding(context.release(), slot.getKey());
        var predicate = context.verified().controlBindings().conditions().get(bodyId);
        if (!(definition.control() instanceof MultiInstanceControlVO multi) || !insideBody(execution, bodyId)
                || predicate == null || !predicate.equals(multi.completionCondition())) throw unavailable();
        var occurrence = exactOccurrence(execution, slot.getValue());
        var children = finishedChildren(context, occurrence);
        if (children.isEmpty()) return false;
        var output = mergeOutput(definition, merge -> multiValues(context, children, merge));
        if (!test(predicate, path -> lookup(output, path))) return false;
        Object existing = occurrence.getVariableLocal(MULTI_SELECTED);
        if (existing != null) {
            if (!selectedIndices(context, existing).equals(children.keySet())) throw unavailable();
            return true;
        }
        storeNativeObject(context, occurrence, MULTI_SELECTED, Map.of("indices", new ArrayList<>(children.keySet())));
        excludeUnselectedMultiChildren(context, execution, bodyId, children.keySet());
        return true;
    }

    private void excludeUnselectedMultiChildren(LifecycleContextVO context, DelegateExecution execution, String bodyId,
                                                Set<Integer> retained) {
        var excluded = execution.getExecutions().stream().filter(child -> bodyId.equals(child.getCurrentActivityId())
                && child.getVariableLocal(AutomationModelCompiler.MULTI_INSTANCE_INDEX) instanceof Integer index
                && !retained.contains(index)).toList();
        var references = excluded.stream()
                .flatMap(child -> child.getVariablesLocal().entrySet().stream())
                .filter(entry -> entry.getKey().startsWith(MULTI_NODE_REF))
                .map(entry -> {
                    String reference = entry.getKey().substring(MULTI_NODE_REF.length());
                    if (!reference.equals(entry.getValue())) throw unavailable();
                    return reference;
                }).toList();
        if (!references.isEmpty()) {
            var root = context.binding();
            nodes.excludeMultiChildren(root.getTenantId(), root.getInstanceId(), root.getConfigurationSha256(), references);
        }
        closeDiscardedReservations(context, excluded);
    }

    private static boolean insideBody(DelegateExecution execution, String bodyId) {
        for (var current = execution; current != null; current = current.getParent()) {
            if (bodyId.equals(current.getCurrentActivityId())) return true;
        }
        return false;
    }

    private static List<?> frozenItems(LifecycleContextVO context, DelegateExecution occurrence, MultiInstanceControlVO multi) {
        Object raw = occurrence.getVariableLocal(MULTI_ITEMS);
        if (raw == null || !(nativeObject(context, raw).get("items") instanceof List<?> items)
                || items.size() > multi.maxItems()) throw unavailable();
        return items;
    }

    private Map.Entry<String, OccurrenceSlotVO> bodySlot(LifecycleContextVO context, String bodyId) {
        return context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(entry -> bodyId.equals(entry.getValue().attemptOrBodyId()))
                .findFirst().orElseThrow(ProcessNodeLifecycleListener::unavailable);
    }

    private Map.Entry<String, OccurrenceSlotVO> controlScope(LifecycleContextVO context, DelegateExecution execution) {
        return context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(entry -> execution.getCurrentActivityId().equals(entry.getValue().scopeId()))
                .findFirst().orElseThrow(ProcessNodeLifecycleListener::unavailable);
    }

    private ProcessNodeExecutionBO exactExecutionRow(LifecycleContextVO context, DelegateExecution scope) {
        return nodes.findAttempt(context.binding().getTenantId(), context.binding().getInstanceId(), scope.getId())
                .filter(row -> !row.isExcluded() && context.binding().getConfigurationSha256().equals(row.getConfigurationSha256()))
                .orElseThrow(ProcessNodeLifecycleListener::unavailable);
    }

    private void onControlBody(DelegateExecution body, boolean enter) {
        var context = context(body);
        var slot = context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(entry -> body.getCurrentActivityId().equals(entry.getValue().attemptOrBodyId())).findFirst().orElse(null);
        if (slot == null) return;
        var definition = binding(context.release(), slot.getKey());
        if (!(definition.control() instanceof LoopControlVO loop)) return;
        var parent = exactOccurrence(requireParent(body), slot.getValue());
        Object count = parent.getVariableLocal(LOOP_ITERATIONS);
        if (!(count instanceof Integer completed) || completed < 0 || completed >= loop.maxIterations()) throw unavailable();
        var current = nativeObject(context, parent.getVariableLocal(LOOP_CARRY));
        if (enter) {
            body.setVariableLocal(LOOP_INDEX, completed);
            storeNativeObject(context, body, LOCAL_INPUT, current);
            storeNativeObject(context, body, AutomationBootstrapBehavior.SCOPE, current);
            return;
        }
        if (!Integer.valueOf(completed).equals(body.getVariableLocal(LOOP_INDEX))) throw unavailable();
        if (!(body.getCurrentFlowElement() instanceof SubProcess iteration)) throw unavailable();
        var updated = mutableObject(current);
        var result = visibleScope(context, body);
        for (var mapping : loop.carryMapping()) {
            assign(updated, mapping.target(), mappedValue(context, body, iteration, result, mapping.source()));
        }
        var carry = object(loop.carrySchema().normalizeValue(updated));
        if (completed + 1 == loop.maxIterations() && test(loop.condition(), path -> lookup(carry, path))) {
            var policy = context.verified().controlBindings().policySlots().get(slot.getKey());
            var row = exactExecutionRow(context, parent);
            if (policy == null || policy.failureBoundaryId() == null
                    || !Set.of("BRANCH", "END").contains(definition.policy().onFailure().action())) throw unavailable();
            nodes.finish(row, StateEnum.FAILED);
            captureBoundary(row, policy.failureBoundaryId(), "FAILURE", slot.getValue().scopeId());
            throw new BpmnError("TIANJI_AUTOMATION_NODE_FAILURE");
        }
        storeNativeObject(context, parent, LOOP_CARRY, carry);
        parent.setVariableLocal(LOOP_ITERATIONS, completed + 1);
    }

    private void beforeScopeExit(DelegateExecution execution) {
        var context = context(execution);
        var end = (EndEvent) execution.getCurrentFlowElement();
        if (!end.getEventDefinitions().isEmpty()) {
            if (end.getEventDefinitions().getFirst() instanceof TerminateEventDefinition terminate && terminate.isTerminateAll()) {
                closeRootReservations(context);
                publish(context, "INSTANCE_CANCELLED", nodes.now(), null, null);
            }
            return;
        }
        var parent = requireParent(execution);
        if (parent.getCurrentFlowElement() instanceof SubProcess) {
            finishNativeScope(context, parent);
        } else if (parent.getId().equals(context.binding().getInstanceId())) {
            var output = projectScope(context.release().source().outputSchema(), visibleScope(context, parent));
            storeNativeObject(context, parent, AutomationBootstrapBehavior.ROOT_OUTPUT, output);
            closeRootReservations(context);
            publish(context, "INSTANCE_COMPLETED", nodes.now(), null, null);
        }
    }

    private void closeRootReservations(LifecycleContextVO context) {
        if (subscriptions != null) subscriptions.closeOpen(context.binding().getTenantId(), context.binding().getInstanceId());
    }

    private void finishNativeScope(LifecycleContextVO context, DelegateExecution scope) {
        var slot = context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(entry -> scope.getCurrentActivityId().equals(entry.getValue().scopeId())
                        || scope.getCurrentActivityId().equals(entry.getValue().attemptOrBodyId())).findFirst().orElse(null);
        if (slot == null) return;
        var definition = binding(context.release(), slot.getKey());
        if (scope.getCurrentActivityId().equals(slot.getValue().attemptOrBodyId())) {
            if (definition.control() instanceof LoopControlVO) onControlBody(scope, false);
            if (definition.control() instanceof MultiInstanceControlVO) finishMultiBody(context, scope, slot.getValue());
            return;
        }
        if (definition.control() instanceof LoopControlVO) {
            var output = projectScope(definition.outputSchema(), nativeObject(context, scope.getVariableLocal(LOOP_CARRY)));
            completeLocal(context, definition, exactExecutionRow(context, scope), scope, output);
        } else if (definition.control() instanceof SubprocessControlVO) {
            var output = projectScope(definition.outputSchema(), visibleScope(context, scope));
            completeLocal(context, definition, exactExecutionRow(context, scope), scope, output);
        }
    }

    private static Map<String, Object> projectScope(ProcessSchemaVO schema, Map<String, Object> scope) {
        var output = new LinkedHashMap<String, Object>();
        for (var key : schema.properties().keySet()) {
            if (scope.containsKey(key)) output.put(key, scope.get(key));
        }
        return object(schema.normalizeValue(output));
    }

    private void finishMultiBody(LifecycleContextVO context, DelegateExecution body, OccurrenceSlotVO slot) {
        var occurrence = exactOccurrence(requireParent(body), slot);
        if (!(body.getVariable(AutomationModelCompiler.MULTI_INSTANCE_INDEX) instanceof Integer index) || index < 0) {
            throw unavailable();
        }
        Object previous = occurrence.getVariableLocal(MULTI_FINISHED);
        var finished = previous == null ? new LinkedHashMap<String, Object>() : mutableObject(nativeObject(context, previous));
        if (finished.putIfAbsent(index.toString(), body.getId()) != null) throw unavailable();
        storeNativeObject(context, occurrence, MULTI_FINISHED, finished);
    }

    private static Map<Integer, String> finishedChildren(LifecycleContextVO context, DelegateExecution occurrence) {
        var nativeValue = occurrence.getVariableLocal(MULTI_FINISHED);
        if (nativeValue == null) return Map.of();
        var finished = new java.util.TreeMap<Integer, String>();
        var children = nativeObject(context, nativeValue);
        children.forEach((index, child) -> {
            if (!index.matches("0|[1-9][0-9]{0,2}") || !(child instanceof String reference) || reference.isBlank()) throw unavailable();
            finished.put(Integer.parseInt(index), reference);
        });
        return finished;
    }

    private static void storeNativeObject(LifecycleContextVO context, DelegateExecution owner, String variable, Object value) {
        var bytes = ProcessTypedJson.canonicalBytes(value, ProcessNodeExecutionStore.MAX_DATA);
        owner.setVariableLocal(variable, context.engine().getVariableJsonMapper().readTree(bytes));
    }

    private static Object localField(Map<String, Object> input, Map<String, Object> scope, String path) {
        var first = fieldRoot(path);
        return lookup(scope.containsKey(first) ? scope : input, path);
    }

    private static String fieldRoot(String path) {
        int end = path.indexOf('.');
        int index = path.indexOf('[');
        if (index != -1 && (end == -1 || index < end)) end = index;
        return end == -1 ? path : path.substring(0, end);
    }

    private static boolean test(ConditionVO predicate, Function<String, Object> resolve) {
        Object value = evaluate(predicate, resolve);
        if (!(value instanceof Boolean result)) throw unavailable();
        return result;
    }

    private static Object evaluate(ConditionVO predicate, Function<String, Object> resolve) {
        return switch (predicate) {
            case ConditionPathVO path -> resolve.apply(path.path());
            case ConditionLiteralVO literal -> literal.literal();
            case ConditionOperationVO operation -> {
                var arguments = operation.args();
                yield switch (operation.op()) {
                    case "AND" -> arguments.stream().allMatch(argument -> test(argument, resolve));
                    case "OR" -> arguments.stream().anyMatch(argument -> test(argument, resolve));
                    case "NOT" -> !test(arguments.getFirst(), resolve);
                    default -> compare(operation.op(), evaluate(arguments.getFirst(), resolve),
                            evaluate(arguments.getLast(), resolve));
                };
            }
        };
    }

    private static boolean compare(String operation, Object left, Object right) {
        if ("EQ".equals(operation) || "NE".equals(operation)) {
            boolean equal = left instanceof Number && right instanceof Number
                    ? numeric(left).compareTo(numeric(right)) == 0 : Objects.equals(left, right);
            return "EQ".equals(operation) == equal;
        }
        int order = numeric(left).compareTo(numeric(right));
        return switch (operation) {
            case "GT" -> order > 0;
            case "GE" -> order >= 0;
            case "LT" -> order < 0;
            case "LE" -> order <= 0;
            default -> throw unavailable();
        };
    }

    private static BigDecimal numeric(Object value) {
        if (!(value instanceof String) && !(value instanceof Number)) throw unavailable();
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException malformed) {
            throw unavailable();
        }
    }

    private static DelegateExecution requireParent(DelegateExecution execution) {
        var parent = execution.getParent();
        if (parent == null || !execution.getRootProcessInstanceId().equals(parent.getRootProcessInstanceId())) {
            throw unavailable();
        }
        return parent;
    }

    private static Map<String, Object> localInput(LifecycleContextVO context, DelegateExecution parent) {
        Object value = parent.getVariable(LOCAL_INPUT);
        return value == null ? rootInput(context) : nativeObject(context, value);
    }

    private static Map<String, Object> visibleScope(LifecycleContextVO context, DelegateExecution execution) {
        Object value = execution.getVariable(AutomationBootstrapBehavior.SCOPE);
        return value == null ? Map.of() : nativeObject(context, value);
    }

    private static Map<String, Object> visibleScope(LifecycleContextVO context, DelegateExecution owner, FlowElement current) {
        for (var branch : branchScopes(context, current)) {
            Object value = owner.getVariableLocal(branch.variable());
            if (value != null) return nativeObject(context, value);
        }
        return visibleScope(context, owner);
    }

    private static Integer currentBranchIndex(LifecycleContextVO context, DelegateExecution occurrence) {
        var local = branchScopes(context, occurrence.getCurrentFlowElement());
        if (!local.isEmpty()) return local.getFirst().index();
        for (var parent = occurrence.getParent(); parent != null; parent = parent.getParent()) {
            if (parent.getVariableLocal(AutomationModelCompiler.MULTI_INSTANCE_INDEX) instanceof Integer index) return index;
            var enclosing = branchScopes(context, parent.getCurrentFlowElement());
            if (!enclosing.isEmpty()) return enclosing.getFirst().index();
        }
        return null;
    }

    private static List<BranchScopeVO> branchScopes(LifecycleContextVO context, FlowElement current) {
        if (current == null || current.getParentContainer() == null
                || context.verified().controlBindings().branchIndices().isEmpty()) return List.of();
        var scopes = new ArrayList<BranchScopeVO>();
        for (var join : current.getParentContainer().getFlowElements().stream()
                .filter(ParallelGateway.class::isInstance).map(ParallelGateway.class::cast)
                .filter(gateway -> gateway.getIncomingFlows().size() > 1).toList()) {
            for (var branch : parallelBranches(context, join)) {
                if (branch.members().contains(current.getId())) scopes.add(branch);
            }
        }
        scopes.sort(java.util.Comparator.comparingInt(branch -> branch.members().size()));
        return scopes;
    }

    private static List<BranchScopeVO> parallelBranches(LifecycleContextVO context, ParallelGateway join) {
        var archive = context.verified().controlBindings().branchIndices();
        for (FlowElement candidate : join.getParentContainer().getFlowElements()) {
            if (!(candidate instanceof ParallelGateway split) || split.getOutgoingFlows().size() < 2
                    || split.getOutgoingFlows().stream().anyMatch(flow -> !archive.containsKey(flow.getId()))) continue;
            var branches = new ArrayList<BranchScopeVO>();
            var covered = new LinkedHashSet<String>();
            var arrivals = new LinkedHashSet<String>();
            for (SequenceFlow entry : split.getOutgoingFlows()) {
                var members = regionBefore(entry.getTargetFlowElement(), join);
                var exits = join.getIncomingFlows().stream().filter(flow -> flow.getId().equals(entry.getId())
                        || members.contains(flow.getSourceRef())).toList();
                if (exits.size() != 1 || !arrivals.add(exits.getFirst().getId()) || !Collections.disjoint(covered, members)) {
                    branches.clear();
                    break;
                }
                covered.addAll(members);
                int index = archive.get(entry.getId());
                branches.add(new BranchScopeVO(split.getId(), index, exits.getFirst().getId(), Set.copyOf(members)));
            }
            if (branches.size() == arrivals.size() && arrivals.size() == join.getIncomingFlows().size()) {
                var ordered = branches.stream().sorted(java.util.Comparator.comparingInt(BranchScopeVO::index)).toList();
                for (int index = 0; index < ordered.size(); index++) {
                    if (index != ordered.get(index).index()) throw unavailable();
                }
                return ordered;
            }
        }
        throw unavailable();
    }

    private static Set<String> regionBefore(FlowElement start, ParallelGateway join) {
        var seen = new LinkedHashSet<String>();
        if (!(start instanceof FlowNode node)) return seen;
        var pending = new ArrayDeque<FlowNode>();
        pending.add(node);
        while (!pending.isEmpty()) {
            var next = pending.removeFirst();
            if (join.getId().equals(next.getId()) || !seen.add(next.getId())) continue;
            for (var flow : next.getOutgoingFlows()) {
                if (flow.getTargetFlowElement() instanceof FlowNode child) pending.addLast(child);
            }
        }
        return seen;
    }

    private record BranchScopeVO(String splitId, int index, String arrivalId, Set<String> members) {
        String variable() { return BRANCH_SCOPE + splitId + "_" + index; }
    }

    private static Map<String, Object> nativeObject(LifecycleContextVO context, Object value) {
        return object(ProcessExecutionService.decodeNativeJson(context.engine(), value, ProcessNodeExecutionStore.MAX_DATA));
    }

    private void onBranchArrival(DelegateExecution execution, SequenceFlow flow) {
        if (!(flow.getTargetFlowElement() instanceof ParallelGateway join) || join.getIncomingFlows().size() < 2) return;
        var context = context(execution);
        if (context.verified().controlBindings().occurrenceSlots().values().stream()
                .noneMatch(slot -> join.getId().equals(slot.scopeId()))) throw unavailable();
        var branch = parallelBranches(context, join).stream().filter(item -> flow.getId().equals(item.arrivalId()))
                .findFirst().orElseThrow(ProcessNodeLifecycleListener::unavailable);
        var parent = requireParent(execution);
        String arrival = joinArrival(join.getId(), branch.index());
        if (parent.getVariableLocal(arrival) != null) throw unavailable();
        parent.setVariableLocal(arrival, flow.getId());
    }

    private static String joinArrival(String joinId, int index) {
        return JOIN_ARRIVAL + joinId + "_" + index;
    }

    private void onMerge(DelegateExecution execution) {
        var context = context(execution);
        String mergeId = execution.getCurrentActivityId();
        var slot = context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(entry -> mergeId.equals(entry.getValue().mergeId())
                        || mergeId.equals(entry.getValue().attemptOrBodyId()) && entry.getValue().activityId() == null)
                .findFirst().orElseThrow(ProcessNodeLifecycleListener::unavailable);
        var definition = binding(context.release(), slot.getKey());
        if (definition.control() instanceof MultiInstanceControlVO multi) {
            var scope = exactOccurrence(requireParent(execution), slot.getValue());
            var finished = finishedChildren(context, scope);
            var count = frozenItems(context, scope, multi).size();
            Object selected = scope.getVariableLocal(MULTI_SELECTED);
            if (selected == null && finished.size() != count) throw unavailable();
            if (selected != null && !finished.keySet().equals(selectedIndices(context, selected))) throw unavailable();
            var result = mergeOutput(definition, merge -> multiValues(context, finished, merge));
            completeLocal(context, definition, exactExecutionRow(context, scope), scope, result);
            return;
        }
        if (!"AND_JOIN".equals(definition.kind()) || !(execution.getCurrentFlowElement() instanceof ServiceTask task)
                || !(task.getParentContainer().getFlowElement(slot.getValue().scopeId()) instanceof ParallelGateway join)) {
            throw unavailable();
        }
        var parent = requireParent(execution);
        var branches = parallelBranches(context, join);
        for (var branch : branches) {
            if (!branch.arrivalId().equals(parent.getVariableLocal(joinArrival(join.getId(), branch.index())))) {
                throw unavailable();
            }
        }
        var result = mergeOutput(definition, merge -> branches.stream().flatMap(branch -> merge.sourceNodeIds().stream()
                .filter(id -> branch.members().contains(context.verified().controlBindings().occurrenceSlots().get(id).scopeId()))
                .map(id -> {
                    var row = completedSource(context, parent, id);
                    if (!Integer.valueOf(branch.index()).equals(row.getBranchIndex())) throw unavailable();
                    return lookup(row.getOutput(), merge.target());
                })).toList());
        insert(context, definition, slot.getKey(), execution, execution, slot.getValue(), null);
        completeLocal(context, definition, exactExecutionRow(context, execution), execution, result);
        for (var branch : branches) {
            parent.removeVariableLocal(joinArrival(join.getId(), branch.index()));
            parent.removeVariableLocal(branch.variable());
        }
    }

    private static Set<Integer> selectedIndices(LifecycleContextVO context, Object selected) {
        if (!(nativeObject(context, selected).get("indices") instanceof List<?> values)) throw unavailable();
        var result = new LinkedHashSet<Integer>();
        for (Object index : values) {
            long value = integerValue(index);
            if (value < 0 || value > 255 || !result.add((int) value)) throw unavailable();
        }
        return Set.copyOf(result);
    }

    private List<Object> multiValues(LifecycleContextVO context, Map<Integer, String> finished, MergeVO merge) {
        var result = new ArrayList<Object>();
        finished.forEach((index, bodyId) -> {
            for (String id : merge.sourceNodeIds()) {
                var row = completedSource(context, bodyId, id);
                if (!index.equals(row.getBranchIndex())) throw unavailable();
                result.add(lookup(row.getOutput(), merge.target()));
            }
        });
        return Collections.unmodifiableList(result);
    }

    private void onRule(DelegateExecution execution) {
        var context = context(execution);
        var slot = context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(entry -> execution.getCurrentActivityId().equals(entry.getValue().activityId()))
                .findFirst().orElseThrow(ProcessNodeLifecycleListener::unavailable);
        var definition = binding(context.release(), slot.getKey());
        if (definition.rule() == null || !(execution.getCurrentFlowElement() instanceof ServiceTask task)
                || !definition.rule().definitionKey().equals(task.getAttributeValue(MODEL_NAMESPACE, "decisionKey"))
                || !(CommandContextUtil.getDmnEngineConfiguration() instanceof SpringDmnEngineConfiguration dmn)) throw unavailable();
        var attempt = ancestor(execution.getParent(), slot.getValue().attemptOrBodyId());
        var scope = exactOccurrence(requireParent(attempt), slot.getValue());
        var row = exactExecutionRow(context, attempt);
        var result = ProcessDecisionService.evaluateAutomation(dmn, context.verified(), definition, row,
                new ProcessReference(context.binding().getInstanceId(), execution.getId(), execution.getCurrentActivityId()));
        completeLocal(context, definition, row, scope, result);
    }

    private Map<String, Object> mergeOutput(AutomationNodeBindingVO definition, Function<MergeVO, List<Object>> sources) {
        var result = new LinkedHashMap<String, Object>();
        for (var merge : definition.merge()) {
            var inputs = sources.apply(merge);
            if (inputs.isEmpty() && !"COLLECT_INDEXED".equals(merge.reducer())) throw unavailable();
            Object value = switch (merge.reducer()) {
                case "EXCLUSIVE" -> {
                    if (inputs.size() != 1) throw unavailable();
                    yield inputs.getFirst();
                }
                case "COLLECT_INDEXED" -> inputs;
                case "ALL" -> inputs.stream().map(ProcessNodeLifecycleListener::booleanValue).allMatch(Boolean::booleanValue);
                case "ANY" -> inputs.stream().map(ProcessNodeLifecycleListener::booleanValue).anyMatch(Boolean::booleanValue);
                case "SUM" -> {
                    long sum = 0;
                    for (Object input : inputs) sum = Math.addExact(sum, integerValue(input));
                    yield sum;
                }
                default -> throw unavailable();
            };
            assign(result, merge.target(), value);
        }
        return object(definition.outputSchema().normalizeValue(result));
    }

    private static long integerValue(Object value) {
        if (!(value instanceof Number)) throw unavailable();
        try {
            return numeric(value).longValueExact();
        } catch (ArithmeticException overflow) {
            throw unavailable();
        }
    }

    private static boolean booleanValue(Object value) {
        if (!(value instanceof Boolean result)) throw unavailable();
        return result;
    }

    private void completeLocal(LifecycleContextVO context, AutomationNodeBindingVO definition, ProcessNodeExecutionBO row,
                               DelegateExecution scope, Map<String, Object> result) {
        var output = object(definition.outputSchema().normalizeValue(result));
        nodes.completeLocal(row, output);
        applyNodeOutput(context, definition, output, scope);
        publish(context, "NODE_COMPLETED", nodes.now(), row, null);
    }

    private void onWorkerJobCreated(CreateExternalWorkerJobAfterContext created) {
        var execution = created.getExecution();
        var context = context(execution);
        var attempt = exactWorkerAttempt(context, execution, created.getExternalWorkerJobEntity());
        var row = attempt.row();
        if (row.getNativeJobId() != null || row.getState() != StateEnum.WAITING) throw unavailable();
        nodes.bindWorkerJob(row, created.getExternalWorkerJobEntity().getId());
        execution.setVariableLocal(NODE_REF, row.getNodeExecutionRef());
        execution.setVariableLocal(INPUT_SHA, row.getInputSha256());
        execution.setVariableLocal(CONFIGURATION_SHA, row.getConfigurationSha256());
        execution.setVariableLocal(RESULT_REF, "");
    }

    private void onWorkerResult(DelegateExecution execution) {
        var context = context(execution);
        var attempt = exactWorkerAttempt(context, execution, null);
        var row = attempt.row();
        requireCompletionJob(row, execution);
        if (row.getState() != StateEnum.RESULT_ACCEPTED || row.getOutput() == null || row.getNativeJobId() == null
                || !row.getNodeExecutionRef().equals(execution.getVariableLocal(NODE_REF))
                || !row.getInputSha256().equals(execution.getVariableLocal(INPUT_SHA))
                || !row.getConfigurationSha256().equals(execution.getVariableLocal(CONFIGURATION_SHA))
                || !ProcessNodeExecutionStore.resultRef(row, row.getOutputSha256()).equals(execution.getVariableLocal(RESULT_REF))) {
            throw unavailable();
        }
        applyNodeOutput(context, attempt.binding(), row.getOutput(), attempt.occurrence());
        nodes.finish(row, StateEnum.COMPLETED);
        publish(context, "NODE_COMPLETED", nodes.now(), row, null);
        execution.removeVariableLocal(RESULT_REF);
    }

    private WorkerAttemptVO exactWorkerAttempt(LifecycleContextVO context, DelegateExecution execution, ExternalWorkerJobEntity job) {
        var slot = context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(entry -> execution.getCurrentActivityId().equals(entry.getValue().activityId()))
                .findFirst().orElseThrow(ProcessNodeLifecycleListener::unavailable);
        var binding = binding(context.release(), slot.getKey());
        if (binding.service() == null || !(execution.getCurrentFlowElement() instanceof ExternalWorkerServiceTask worker)
                || !context.binding().getInstanceId().equals(execution.getProcessInstanceId())) throw unavailable();
        var attempt = ancestor(execution.getParent(), slot.getValue().attemptOrBodyId());
        var occurrence = exactOccurrence(attempt.getParent(), slot.getValue());
        var root = context.binding();
        var row = nodes.findAttempt(root.getTenantId(), root.getInstanceId(), attempt.getId())
                .orElseThrow(ProcessNodeLifecycleListener::unavailable);
        if (!slot.getKey().equals(row.getNodeId()) || !occurrenceId(root, slot.getKey(), occurrence.getId()).equals(row.getOccurrenceId())
                || !occurrence.getParentId().equals(row.getParentScopeId()) || !root.getConfigurationSha256().equals(row.getConfigurationSha256())
                || !binding.service().capabilityKey().equals(row.getCapabilityKey()) || !Objects.equals(binding.service().version(), row.getCapabilityVersion())) {
            throw unavailable();
        }
        if (job != null && (!execution.getId().equals(job.getExecutionId())
                || !worker.getId().equals(job.getElementId()) || !worker.getTopic().equals(job.getJobHandlerConfiguration())
                || !EXTERNAL_WORKER_COMPLETION.equals(job.getJobHandlerType())
                || !execution.getProcessInstanceId().equals(job.getProcessInstanceId())
                || !execution.getProcessDefinitionId().equals(job.getProcessDefinitionId())
                || !execution.getTenantId().equals(job.getTenantId()))) throw unavailable();
        return new WorkerAttemptVO(binding, row, (ExecutionEntity) occurrence, (ExecutionEntity) execution, slot.getValue());
    }

    public void parkWorkerFailure(ProcessNodeExecutionBO row, String nativeJobId, String errorCode) {
        parkReportedFailure(row, nativeJobId, errorCode, SideEffectOutcomeEnum.UNKNOWN);
    }

    public void parkKnownWorkerFailure(ProcessNodeExecutionBO row, String nativeJobId, String errorCode) {
        parkReportedFailure(row, nativeJobId, errorCode, SideEffectOutcomeEnum.NONE);
    }

    private void parkReportedFailure(ProcessNodeExecutionBO row, String nativeJobId, String errorCode, SideEffectOutcomeEnum outcome) {
        var job = requireWorkerJob(row, nativeJobId);
        var execution = CommandContextUtil.getExecutionEntityManager().findById(job.getExecutionId());
        var attempt = exactWorkerAttempt(context(execution), execution, job);
        if (attempt.row().getRevision() != row.getRevision() || attempt.row().getState() != StateEnum.WAITING) throw unavailable();
        var now = nodes.now();
        if (!now.isBefore(row.getAttemptDeadlineAt()) || !now.isBefore(row.getNodeDeadlineAt())) throw unavailable();
        parkWorker(attempt, job, outcome, errorCode);
    }

    public void verifyWorkerJob(ProcessNodeExecutionBO row, String nativeJobId) {
        var job = requireWorkerJob(row, nativeJobId);
        var execution = CommandContextUtil.getExecutionEntityManager().findById(job.getExecutionId());
        var attempt = exactWorkerAttempt(context(execution), execution, job);
        if (attempt.row().getState() != StateEnum.WAITING || attempt.row().getRevision() != row.getRevision()
                || !row.getNodeExecutionRef().equals(execution.getVariableLocal(NODE_REF))
                || !row.getInputSha256().equals(execution.getVariableLocal(INPUT_SHA))
                || !row.getConfigurationSha256().equals(execution.getVariableLocal(CONFIGURATION_SHA))) throw unavailable();
    }

    public WorkerRecoveryWaitVO observeWorkerRecoveryWait(ProcessNodeExecutionBO row) {
        var wait = requireWorkerRecoveryWait(row, false);
        var execution = wait.attempt().execution();
        return new WorkerRecoveryWaitVO(wait.subscription().getId(), execution.getId(), execution.getProcessDefinitionId(),
                execution.getCurrentActivityId(), wait.attempt().occurrence().getId());
    }

    public String reenterWorkerRecovery(ProcessNodeExecutionBO prior, WorkerEffectRecoveryVO proof, String commandId, String requestSha) {
        var recovered = requireWorkerRecoveryWait(prior, true);
        var attempt = recovered.attempt();
        Instant now = nodes.now();
        if (!now.isBefore(prior.getNodeDeadlineAt()) || prior.getAttempt() >= attempt.binding().policy().retry().maxAttempts()
                || (prior.getSideEffectOutcome() == SideEffectOutcomeEnum.UNKNOWN) != (proof != null)
                || proof != null && (now.isBefore(proof.observedAt()) || !now.isBefore(proof.validUntil())
                        || proof.validUntil().isAfter(prior.getNodeDeadlineAt()) || !allowedWorkerRecovery(attempt.binding(), proof))) {
            throw recoveryBlocked();
        }
        var occurrence = attempt.occurrence();
        var nextScope = replaceWorkerAttempt(attempt);
        nextScope.setScope(true);
        int nextNumber = prior.getAttempt() + 1;
        Instant attemptDeadline = now.plus(attempt.binding().policy().attemptTimeout());
        if (attemptDeadline.isAfter(prior.getNodeDeadlineAt())) attemptDeadline = prior.getNodeDeadlineAt();
        String nextRef = executionRef(prior.getTenantId(), prior.getInstanceId(), prior.getOccurrenceId(), nextNumber);
        var next = new ProcessNodeExecutionBO()
                .setTenantId(prior.getTenantId()).setInstanceId(prior.getInstanceId()).setOccurrenceId(prior.getOccurrenceId())
                .setAttempt(nextNumber).setNodeExecutionRef(nextRef).setNodeId(prior.getNodeId()).setKind(prior.getKind())
                .setParentScopeId(occurrence.getParentId()).setAttemptScopeId(nextScope.getId())
                .setBranchIndex(prior.getBranchIndex()).setIterationIndex(prior.getIterationIndex())
                .setCapabilityKey(prior.getCapabilityKey()).setCapabilityVersion(prior.getCapabilityVersion()).setEffectKey(prior.getEffectKey())
                .setInput(prior.getInput()).setInputSha256(prior.getInputSha256()).setInputSchemaSha256(prior.getInputSchemaSha256())
                .setOutputSchemaSha256(prior.getOutputSchemaSha256()).setConfigurationSha256(prior.getConfigurationSha256())
                .setState(StateEnum.WAITING).setRevision(1).setSideEffectOutcome(SideEffectOutcomeEnum.NONE)
                .setStartedAt(now).setNodeDeadlineAt(prior.getNodeDeadlineAt()).setAttemptDeadlineAt(attemptDeadline)
                .setRetryCommandId(commandId).setRetryRequestSha256(requestSha).setWorkerEffectRecovery(proof);
        nodes.insertRecoveredAttempt(prior, next);
        registerMultiChildNode(occurrence, nextRef);
        // The normal agenda replaces a subprocess's incoming execution with a new scope ID.
        // The recovered row already names this exact scope, so enter it directly.
        CommandContextUtil.getAgenda().planContinueProcessSynchronousOperation(nextScope);
        return nextRef;
    }

    public boolean parkInvalidWorkerRecovery(ProcessNodeExecutionBO row, String nativeJobId, String reason) {
        var job = requireWorkerJob(row, nativeJobId);
        var execution = CommandContextUtil.getExecutionEntityManager().findById(job.getExecutionId());
        var attempt = exactWorkerAttempt(context(execution), execution, job);
        var proof = row.getWorkerEffectRecovery();
        var now = nodes.now();
        if (!row.equals(attempt.row()) || row.getState() != StateEnum.WAITING || proof == null
                || !now.isBefore(row.getNodeDeadlineAt())) return false;
        boolean invalid = switch (reason) {
            case WORKER_RECOVERY_PROOF_EXPIRED -> !now.isBefore(proof.validUntil());
            case WORKER_RECOVERY_PROOF_MISMATCH -> now.isBefore(proof.observedAt()) || !allowedWorkerRecovery(attempt.binding(), proof)
                    || proof.validUntil().isAfter(row.getNodeDeadlineAt()) || row.getAttempt() < 2
                    || row.getRetryCommandId() == null || !ProcessInstanceBindingStore.sha256(row.getRetryRequestSha256())
                    || !matchesWorkerEnvelope(execution, row);
            case null, default -> false;
        };
        if (!invalid) return false;
        parkWorker(attempt, job, SideEffectOutcomeEnum.UNKNOWN, reason);
        return true;
    }

    private static boolean allowedWorkerRecovery(AutomationNodeBindingVO binding, WorkerEffectRecoveryVO proof) {
        var mode = binding.service().effectMode();
        return mode != WorkerEffectSafetyEnum.READ_ONLY && (proof.mode() != WorkerEffectRecoveryVO.Mode.RETRY_NOT_APPLIED
                || mode == WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT);
    }

    private VerifiedWorkerRecoveryWaitVO requireWorkerRecoveryWait(ProcessNodeExecutionBO row, boolean guarded) {
        if (guarded) AutomationInstanceGuardCommandInterceptor.requireHeld(row.getTenantId(), row.getInstanceId());
        if (row.getState() != StateEnum.WAIT_RECOVERY || row.isExcluded() || row.getNativeSubscriptionId() == null
                || row.getNativeJobId() == null) throw recoveryBlocked();
        var subscriptions = CommandContextUtil.getEventSubscriptionService();
        var subscription = subscriptions.findById(row.getNativeSubscriptionId());
        if (subscription == null || !"message".equals(subscription.getEventType())
                || !(RECOVERY_MESSAGE + row.getNodeExecutionRef()).equals(subscription.getEventName())
                || !row.getInstanceId().equals(subscription.getProcessInstanceId())
                || !Long.toString(row.getTenantId()).equals(subscription.getTenantId())) throw recoveryBlocked();
        var execution = CommandContextUtil.getExecutionEntityManager().findById(subscription.getExecutionId());
        if (execution == null || !row.getInstanceId().equals(execution.getRootProcessInstanceId())
                || !Long.toString(row.getTenantId()).equals(execution.getTenantId())
                || !execution.getCurrentActivityId().equals(subscription.getActivityId())
                || !execution.getProcessDefinitionId().equals(subscription.getProcessDefinitionId())) throw recoveryBlocked();
        var context = context(execution, guarded);
        var slot = context.verified().controlBindings().occurrenceSlots().get(row.getNodeId());
        if (slot == null || !subscription.getActivityId().equals(slot.activityId())) throw recoveryBlocked();
        var attemptScope = execution.getParent();
        var occurrenceScope = attemptScope == null ? null : attemptScope.getParent();
        if (attemptScope == null || occurrenceScope == null || !row.getAttemptScopeId().equals(attemptScope.getId())
                || !slot.attemptOrBodyId().equals(attemptScope.getCurrentActivityId())
                || !slot.scopeId().equals(occurrenceScope.getCurrentActivityId())
                || !row.getParentScopeId().equals(occurrenceScope.getParentId())) throw recoveryBlocked();
        var root = execution.getRootProcessInstance();
        var related = execution.getEventSubscriptions();
        if (root == null || !execution.isActive() || execution.isEnded() || execution.isSuspended()
                || root.isEnded() || root.isSuspended() || !row.getInstanceId().equals(root.getId())
                || related.size() != 1 || !subscription.getId().equals(related.getFirst().getId())
                || !matchesWorkerEnvelope(execution, row)
                || !row.getNodeDeadlineAt().equals(workerNodeDeadline(occurrenceScope))) throw recoveryBlocked();
        var latest = nodes.findByOccurrence(row.getTenantId(), row.getInstanceId(), row.getOccurrenceId());
        if (latest.filter(row::equals).isEmpty()) throw recoveryBlocked();
        var attempt = exactWorkerAttempt(context, execution, null);
        if (!row.equals(attempt.row())) throw recoveryBlocked();
        var jobs = context.engine().getJobServiceConfiguration();
        if (jobs.getExternalWorkerJobEntityManager().findById(row.getNativeJobId()) != null
                || jobs.getJobService().findJobById(row.getNativeJobId()) != null
                || jobs.getTimerJobService().findTimerJobById(row.getNativeJobId()) != null
                || jobs.getSuspendedJobEntityManager().findById(row.getNativeJobId()) != null
                || jobs.getDeadLetterJobEntityManager().findById(row.getNativeJobId()) != null) throw recoveryBlocked();
        if (guarded) requireWorkerHardTimer(context, row, occurrenceScope);
        return new VerifiedWorkerRecoveryWaitVO(attempt, subscription);
    }

    private static void requireWorkerHardTimer(LifecycleContextVO context, ProcessNodeExecutionBO row, ExecutionEntity occurrence) {
        var policy = context.verified().controlBindings().policySlots().get(row.getNodeId());
        if (policy == null || policy.nodeTimeoutBoundaryId() == null) throw recoveryBlocked();
        String boundaryId = policy.nodeTimeoutBoundaryId();
        var attached = occurrence.getExecutions().stream()
                .filter(execution -> execution.getCurrentFlowElement() instanceof BoundaryEvent boundary
                        && boundaryId.equals(boundary.getId()) && occurrence.getCurrentActivityId().equals(boundary.getAttachedToRefId())
                        && occurrence.getId().equals(execution.getParentId()) && !execution.isEnded() && !execution.isSuspended())
                .limit(2).toList();
        if (attached.size() != 1) throw recoveryBlocked();
        var boundary = attached.getFirst();
        var jobs = context.engine().getJobServiceConfiguration();
        List<AbstractRuntimeJobEntity> timers = new ArrayList<>(jobs.getTimerJobService().findTimerJobsByExecutionId(boundary.getId()));
        timers.addAll(jobs.getJobService().findJobsByExecutionId(boundary.getId()));
        long due = roundedTimerMillis(row.getNodeDeadlineAt());
        if (timers.size() != 1 || !matchesHardTimer(timers.getFirst(), boundary, row, due)) throw recoveryBlocked();
    }

    private static boolean matchesHardTimer(AbstractRuntimeJobEntity timer, ExecutionEntity boundary,
                                            ProcessNodeExecutionBO row, long dueMillis) {
        String configuration = timer.getJobHandlerConfiguration();
        return "trigger-timer".equals(timer.getJobHandlerType()) && configuration != null
                && boundary.getId().equals(timer.getExecutionId()) && boundary.getCurrentActivityId().equals(timer.getElementId())
                && boundary.getCurrentActivityId().equals(TimerEventHandler.getActivityIdFromConfiguration(configuration))
                && boundary.getProcessDefinitionId().equals(timer.getProcessDefinitionId())
                && row.getInstanceId().equals(timer.getProcessInstanceId()) && Long.toString(row.getTenantId()).equals(timer.getTenantId())
                && timer.getDuedate() != null && timer.getDuedate().getTime() == dueMillis;
    }

    private static boolean matchesWorkerEnvelope(DelegateExecution execution, ProcessNodeExecutionBO row) {
        return row.getNodeExecutionRef().equals(execution.getVariableLocal(NODE_REF))
                && row.getInputSha256().equals(execution.getVariableLocal(INPUT_SHA))
                && row.getConfigurationSha256().equals(execution.getVariableLocal(CONFIGURATION_SHA));
    }

    private static ExternalWorkerJobEntity requireWorkerJob(ProcessNodeExecutionBO row, String nativeJobId) {
        if (nativeJobId == null || !nativeJobId.equals(row.getNativeJobId())) throw unavailable();
        AutomationInstanceGuardCommandInterceptor.requireHeld(row.getTenantId(), row.getInstanceId());
        var job = workerJobManager().findById(nativeJobId);
        if (job == null || !row.getInstanceId().equals(job.getProcessInstanceId())
                || !Long.toString(row.getTenantId()).equals(job.getTenantId())) throw unavailable();
        return job;
    }

    private static ExternalWorkerJobEntityManager workerJobManager() {
        return CommandContextUtil.getProcessEngineConfiguration().getJobServiceConfiguration().getExternalWorkerJobEntityManager();
    }

    private void parkWorker(WorkerAttemptVO attempt, ExternalWorkerJobEntity job, SideEffectOutcomeEnum outcome, String errorCode) {
        var execution = attempt.execution();
        if (!execution.getEventSubscriptions().isEmpty()) throw unavailable();
        retireExternalWorkerJob(attempt, job);
        String message = RECOVERY_MESSAGE + attempt.row().getNodeExecutionRef();
        var subscription = (EventSubscriptionEntity) CommandContextUtil.getEventSubscriptionService().createEventSubscriptionBuilder()
                .eventType("message").eventName(message).executionId(execution.getId())
                .processInstanceId(execution.getProcessInstanceId()).activityId(execution.getCurrentActivityId())
                .processDefinitionId(execution.getProcessDefinitionId()).tenantId(execution.getTenantId()).create();
        CountingEntityUtil.handleInsertEventSubscriptionEntityCount(subscription);
        execution.getEventSubscriptions().add(subscription);
        nodes.retireWorkerAttempt(attempt.row(), StateEnum.WAIT_RECOVERY, outcome, errorCode, subscription.getId());
    }

    public void scheduleWorkerRetry(ProcessNodeExecutionBO row, String nativeJobId, String errorCode) {
        var job = requireWorkerJob(row, nativeJobId);
        var execution = CommandContextUtil.getExecutionEntityManager().findById(job.getExecutionId());
        var attempt = exactWorkerAttempt(context(execution), execution, job);
        Instant now = nodes.now();
        if (attempt.row().getRevision() != row.getRevision() || !now.isBefore(row.getAttemptDeadlineAt())
                || !"RETRY".equals(attempt.binding().policy().onFailure().action())
                || !attempt.binding().policy().retry().retryableCodes().contains(errorCode)
                || !canRetry(attempt, now)) throw unavailable();
        scheduleRetry(attempt, job, errorCode, StateEnum.FAILED, now);
    }

    private static boolean canRetry(WorkerAttemptVO attempt, Instant now) {
        return canRetry(attempt.binding().policy(), attempt.row(), now);
    }

    private static boolean canRetry(NodePolicyVO policy,
            ProcessNodeExecutionBO row, Instant now) {
        var retry = policy.retry();
        return row.getAttempt() < retry.maxAttempts() && now.plus(retry.backoff()).isBefore(row.getNodeDeadlineAt());
    }

    private void scheduleRetry(WorkerAttemptVO attempt, ExternalWorkerJobEntity original, String errorCode,
                               StateEnum closed, Instant now) {
        var row = attempt.row();
        var execution = attempt.execution();
        retireExternalWorkerJob(attempt, original);
        scheduleAttemptRetryTimer(execution, row, attempt.binding().policy(), now);
        nodes.retireWorkerAttempt(row, closed, SideEffectOutcomeEnum.NONE, errorCode, null);
    }

    private static void scheduleAttemptRetryTimer(DelegateExecution execution, ProcessNodeExecutionBO row,
            NodePolicyVO policy, Instant now) {
        var timer = CommandContextUtil.getTimerJobService().createTimerJob();
        timer.setJobType("timer");
        timer.setJobHandlerType(RETRY_JOB);
        timer.setJobHandlerConfiguration(row.getNodeExecutionRef());
        timer.setDuedate(new Date(roundedTimerMillis(now.plus(policy.retry().backoff()))));
        timer.setExclusive(false);
        timer.setRetries(CommandContextUtil.getProcessEngineConfiguration().getJobServiceConfiguration().getAsyncExecutorNumberOfRetries());
        timer.setExecutionId(execution.getId());
        timer.setProcessInstanceId(execution.getProcessInstanceId());
        timer.setProcessDefinitionId(execution.getProcessDefinitionId());
        timer.setElementId(execution.getCurrentActivityId());
        timer.setTenantId(execution.getTenantId());
        CommandContextUtil.getTimerJobService().scheduleTimerJob(timer);
    }

    private void retireExternalWorkerJob(WorkerAttemptVO attempt, ExternalWorkerJobEntity job) {
        retireExternalWorkerJob(attempt.row(), job);
    }

    private void retireExternalWorkerJob(ProcessNodeExecutionBO row, ExternalWorkerJobEntity job) {
        if (job.isExclusive() && job.getLockOwner() != null && job.getLockExpirationTime() != null) {
            // Flowable's default unlock clears by root ID. Here only the retired job's lock may be removed.
            nodes.releaseWorkerScopeLock(row, job.getProcessDefinitionId(), job.getLockOwner(),
                    job.getLockExpirationTime().toInstant());
        }
        workerJobManager().delete(job, true);
    }

    private void runNodeRetry(JobEntity timer, String nodeRef, ExecutionEntity execution) {
        var context = context(execution);
        if (!execution.getId().equals(timer.getExecutionId()) || !execution.getTenantId().equals(timer.getTenantId())
                || !execution.getProcessInstanceId().equals(timer.getProcessInstanceId())
                || !execution.getProcessDefinitionId().equals(timer.getProcessDefinitionId())
                || !execution.getCurrentActivityId().equals(timer.getElementId())) throw unavailable();
        var agent = execution.getCurrentFlowElement() instanceof FlowNode flow && flow.getBehavior() instanceof GuardedAgentDispatch
                ? agentDispatchAttempt(execution) : exactAgentWait(execution);
        if (agent != null) {
            var slot = context.verified().controlBindings().occurrenceSlots().get(agent.row().getNodeId());
            restartNativeAttempt(agent.row(), execution, (ExecutionEntity) agent.occurrence(), slot, nodeRef);
            return;
        }
        var worker = exactWorkerAttempt(context, execution, null);
        restartNativeAttempt(worker.row(), worker.execution(), worker.occurrence(), worker.slot(), nodeRef);
    }

    private void restartNativeAttempt(ProcessNodeExecutionBO row, ExecutionEntity execution, ExecutionEntity occurrence,
            OccurrenceSlotVO slot, String nodeRef) {
        if (row.isExcluded() || !row.getNodeExecutionRef().equals(nodeRef)
                || row.getState() != StateEnum.FAILED && row.getState() != StateEnum.TIMED_OUT
                || !canRunScheduledRetryFrom(row)) throw unavailable();
        if (!nodes.now().isBefore(row.getNodeDeadlineAt())) return;
        var nextAttempt = replaceNativeAttempt(row, execution, occurrence, slot);
        CommandContextUtil.getAgenda().planContinueProcessOperation(nextAttempt);
    }

    private static ExecutionEntity replaceWorkerAttempt(WorkerAttemptVO attempt) {
        return replaceNativeAttempt(attempt.row(), attempt.execution(), attempt.occurrence(), attempt.slot());
    }

    private static ExecutionEntity replaceNativeAttempt(ProcessNodeExecutionBO row, ExecutionEntity execution,
            ExecutionEntity occurrence, OccurrenceSlotVO slot) {
        var manager = CommandContextUtil.getExecutionEntityManager();
        var oldAttempt = execution.getParent();
        if (oldAttempt == null || !row.getAttemptScopeId().equals(oldAttempt.getId())
                || !occurrence.getId().equals(oldAttempt.getParentId())) throw unavailable();
        var attemptModel = oldAttempt.getCurrentFlowElement();
        if (!(attemptModel instanceof SubProcess) || !slot.attemptOrBodyId().equals(attemptModel.getId())) throw unavailable();
        manager.deleteChildExecutions(oldAttempt, "Automation node retry", false);
        manager.deleteExecutionAndRelatedData(oldAttempt, "Automation node retry", false);
        var nextAttempt = manager.createChildExecution(occurrence);
        nextAttempt.setCurrentFlowElement(attemptModel);
        return nextAttempt;
    }

    private static boolean canRunScheduledRetryFrom(ProcessNodeExecutionBO row) {
        return row.getSideEffectOutcome() == SideEffectOutcomeEnum.NONE && row.getErrorCode() != null;
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
        if (binding.service() != null || binding.agent() != null) {
            workerNodeDeadline(target, binding);
        }
        if (scope.getValue().attemptOrBodyId() == null || isControl(binding.kind())) {
            insert(context, binding, scope.getKey(), target, target, scope.getValue(), null);
        }
        if (binding.control() instanceof LoopControlVO loop) {
            storeNativeObject(context, target, LOOP_CARRY, loop.initialCarry());
            target.setVariableLocal(LOOP_ITERATIONS, 0);
        } else if (binding.control() instanceof MultiInstanceControlVO multi) {
            var parent = requireParent(target);
            var items = mappedValue(context, parent, target.getCurrentFlowElement().getParentContainer(),
                    visibleScope(context, parent, target.getCurrentFlowElement()), multi.itemsSource());
            if (!(items instanceof List<?> values) || values.size() > multi.maxItems()) throw unavailable();
            storeNativeObject(context, target, MULTI_ITEMS, Map.of("items", values));
            initializeChildScope(context, target);
        } else if (binding.control() instanceof SubprocessControlVO) {
            initializeChildScope(context, target);
        }
    }

    private void initializeChildScope(LifecycleContextVO context, DelegateExecution target) {
        var input = exactExecutionRow(context, target).getInput();
        storeNativeObject(context, target, LOCAL_INPUT, input);
        storeNativeObject(context, target, AutomationBootstrapBehavior.SCOPE, input);
    }

    void onAttempt(DelegateExecution event) {
        var context = context(event);
        var scope = context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(item -> event.getCurrentActivityId().equals(item.getValue().attemptOrBodyId())).findFirst().orElse(null);
        if (scope == null) return;
        var binding = binding(context.release(), scope.getKey());
        if (isControl(binding.kind())) {
            if (binding.control() instanceof LoopControlVO) onControlBody(event, true);
            return;
        }
        var occurrence = exactOccurrence(event.getParent(), scope.getValue());
        insert(context, binding, scope.getKey(), occurrence, event, scope.getValue(), null);
    }

    private void bindEventWait(ExecutionEntity execution) {
        if (subscriptions == null) return;
        var wait = exactEventWait(execution);
        if (wait == null) return;
        var root = wait.context().binding();
        var row = wait.row();
        if (row.getState() != StateEnum.WAITING || row.isExcluded()) throw unavailable();
        var reservation = subscriptions.findOpen(root.getTenantId(), root.getInstanceId(), row.getParentScopeId(), row.getNodeId())
                .orElse(null);
        if (reservation == null) return;
        boolean published = wait.context().release().source().eventRoutes().stream().anyMatch(route -> "SIGNAL".equals(route.mode())
                && row.getNodeId().equals(route.targetNodeId()) && reservation.getRouteKey().equals(route.routeKey())
                && reservation.getRouteVersion() == route.version() && reservation.getSourceRef().equals(route.sourceRef()));
        if (!published) throw unavailable();
        var now = subscriptions.now();
        if (!now.isBefore(reservation.getDeadlineAt()) || !now.isBefore(row.getAttemptDeadlineAt())) {
            subscriptions.close(reservation);
            return;
        }
        var message = exactNativeMessage(execution, root);
        nodes.bindEventSubscription(row, message.getId());
        subscriptions.bindExact(reservation,
                new ProcessEventSubscriptionStore.NativeWaitVO(execution.getId(), message.getId(), row.getNodeExecutionRef()));
    }

    private static EventSubscriptionEntity exactNativeMessage(ExecutionEntity execution, AutomationInstanceBindingBO root) {
        var messages = execution.getEventSubscriptions().stream()
                .filter(item -> "message".equals(item.getEventType())
                        && ("tianji-v2:" + execution.getCurrentActivityId()).equals(item.getEventName())
                        && execution.getId().equals(item.getExecutionId()) && execution.getTenantId().equals(item.getTenantId())
                        && root.getInstanceId().equals(item.getProcessInstanceId())
                        && execution.getCurrentActivityId().equals(item.getActivityId())
                        && execution.getProcessDefinitionId().equals(item.getProcessDefinitionId()))
                .limit(2).toList();
        if (messages.size() != 1) throw unavailable();
        return messages.getFirst();
    }

    public boolean acceptEventResult(AutomationInstanceBindingBO root, ImmutableReleaseVO release, EventRouteVO route,
            EventSubscriptionBO reservation, ProcessNodeExecutionBO row, Map<String, Object> payload) {
        AutomationInstanceGuardCommandInterceptor.requireHeld(root.getTenantId(), root.getInstanceId());
        var definition = binding(release, row.getNodeId());
        if (root.getTenantId() != release.tenantId() || root.getTenantId() != row.getTenantId()
                || root.getTenantId() != reservation.getTenantId() || !root.getInstanceId().equals(row.getInstanceId())
                || !root.getInstanceId().equals(reservation.getInstanceId()) || !root.getReleaseKey().equals(release.releaseVersion())
                || !root.getConfigurationSha256().equals(row.getConfigurationSha256())
                || !row.getParentScopeId().equals(reservation.getParentScopeId())
                || !row.getNodeExecutionRef().equals(reservation.getNodeExecutionRef())
                || !Objects.equals(row.getNativeSubscriptionId(), reservation.getNativeSubscriptionId())
                || row.getKind() != KindEnum.EVENT_WAIT || row.getState() != StateEnum.WAITING
                || !row.getNodeId().equals(reservation.getTargetNodeId()) || !row.getNodeId().equals(route.targetNodeId())
                || !"EVENT_WAIT".equals(definition.kind()) || !"SIGNAL".equals(route.mode())
                || !reservation.getRouteKey().equals(route.routeKey()) || reservation.getRouteVersion() != route.version()
                || !reservation.getSourceRef().equals(route.sourceRef()) || !release.source().eventRoutes().contains(route)
                || !row.getInputSchemaSha256().equals(schemaSha256(definition.inputSchema()))
                || !row.getOutputSchemaSha256().equals(schemaSha256(definition.outputSchema()))) throw unavailable();
        var normalizedPayload = object(route.payloadSchema().normalizeValue(payload));
        var input = mutableObject(row.getInput());
        for (var mapping : route.mapping()) applyEventMapping(input, normalizedPayload, mapping);
        for (var mapping : definition.inputMapping()) {
            if (mapping.source().startsWith("event.")) applyEventMapping(input, normalizedPayload, mapping);
        }
        var completeInput = object(definition.inputSchema().normalizeValue(input));
        var output = object(definition.outputSchema().normalizeValue(projectEventOutput(definition.outputSchema(), completeInput)));
        return nodes.acceptEventResult(row, reservation.getNativeSubscriptionId(), completeInput, output);
    }

    private static void applyEventMapping(Map<String, Object> input, Map<String, Object> payload, MappingVO mapping) {
        try {
            assign(input, mapping.target(), lookup(payload, mapping.source().substring("event.".length())));
        } catch (IllegalStateException invalidPath) {
            throw new IllegalArgumentException("Process signal payload does not satisfy its mapping");
        }
    }

    private static Object projectEventOutput(ProcessSchemaVO schema, Object input) {
        if ("object".equals(schema.type()) && input instanceof Map<?, ?> properties) {
            var output = new LinkedHashMap<String, Object>();
            for (var field : schema.properties().entrySet()) {
                if (properties.containsKey(field.getKey())) {
                    output.put(field.getKey(), projectEventOutput(field.getValue(), properties.get(field.getKey())));
                }
            }
            return output;
        }
        if ("array".equals(schema.type()) && input instanceof List<?> items) {
            return items.stream().map(item -> projectEventOutput(schema.items(), item)).toList();
        }
        return input;
    }

    private EventWaitVO exactEventWait(DelegateExecution execution) {
        var context = context(execution);
        var slot = context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(entry -> execution.getCurrentActivityId().equals(entry.getValue().activityId())).findFirst().orElse(null);
        if (slot == null) return null;
        var definition = binding(context.release(), slot.getKey());
        if (!"EVENT_WAIT".equals(definition.kind())) return null;
        var attempt = requireParent(execution);
        var occurrence = requireParent(attempt);
        if (!slot.getValue().attemptOrBodyId().equals(attempt.getCurrentActivityId())
                || !slot.getValue().scopeId().equals(occurrence.getCurrentActivityId())) throw unavailable();
        var root = context.binding();
        var row = nodes.findAttempt(root.getTenantId(), root.getInstanceId(), attempt.getId())
                .orElseThrow(ProcessNodeLifecycleListener::unavailable);
        if (row.getKind() != KindEnum.EVENT_WAIT || !slot.getKey().equals(row.getNodeId())
                || !occurrence.getParentId().equals(row.getParentScopeId())
                || !occurrenceId(root, row.getNodeId(), occurrence.getId()).equals(row.getOccurrenceId())) throw unavailable();
        return new EventWaitVO(context, definition, row, occurrence);
    }

    private void onEventWaitResult(DelegateExecution execution) {
        if (subscriptions == null) return;
        var wait = exactEventWait(execution);
        if (wait == null) return;
        var row = wait.row();
        if (row.isExcluded() || row.getState() != StateEnum.RESULT_ACCEPTED || row.getOutput() == null || row.getAcceptedAt() == null) {
            throw unavailable();
        }
        applyNodeOutput(wait.context(), wait.definition(), row.getOutput(), wait.occurrence());
        nodes.finish(row, StateEnum.COMPLETED);
        publish(wait.context(), "NODE_COMPLETED", nodes.now(), row, null);
    }

    private void createAgentDispatchJob(DelegateExecution execution) {
        var attempt = agentDispatchAttempt(execution);
        var row = attempt.row();
        if (row.getState() != StateEnum.WAITING || row.getNativeJobId() != null
                || row.getAgentRunId() != null || row.getNativeSubscriptionId() != null) throw unavailable();
        var jobConfiguration = attempt.context().engine().getJobServiceConfiguration();
        var jobService = jobConfiguration.getJobService();
        if (!jobService.findExternalWorkerJobsByExecutionId(execution.getId()).isEmpty()) throw unavailable();

        // The published activity is a ServiceTask. Flowable's stock external worker behavior
        // requires an ExternalWorkerServiceTask, so create its job using the same job service.
        var job = jobService.createExternalWorkerJob();
        job.setExecutionId(execution.getId());
        job.setProcessInstanceId(execution.getProcessInstanceId());
        job.setProcessDefinitionId(execution.getProcessDefinitionId());
        job.setElementId(execution.getCurrentActivityId());
        job.setElementName(execution.getCurrentFlowElement().getName());
        job.setJobHandlerType(EXTERNAL_WORKER_COMPLETION);
        job.setJobHandlerConfiguration(FlowableWorkerService.nativeTypedAgentTopic());
        job.setJobType("externalWorker");
        job.setRetries(jobConfiguration.getAsyncExecutorNumberOfRetries());
        job.setExclusive(false);
        job.setTenantId(execution.getTenantId());
        jobService.insertExternalWorkerJob(job);
        nodes.bindAgentJob(row, job.getId());
    }

    private void completeAgentDispatchJob(DelegateExecution execution) {
        var row = agentDispatchAttempt(execution).row();
        if (row.getState() != StateEnum.WAITING || row.getNativeJobId() == null
                || row.getAgentRunId() == null || row.getNativeSubscriptionId() != null) throw unavailable();
        requireCompletionJob(row, execution);
    }

    private EventWaitVO agentDispatchAttempt(DelegateExecution execution) {
        var context = context(execution);
        var slot = context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(entry -> execution.getCurrentActivityId().equals(entry.getValue().activityId()))
                .findFirst().orElseThrow(ProcessNodeLifecycleListener::unavailable);
        var releaseBinding = binding(context.release(), slot.getKey());
        var attempt = requireParent(execution);
        var occurrence = requireParent(attempt);
        var root = context.binding();
        var row = nodes.findAttempt(root.getTenantId(), root.getInstanceId(), attempt.getId())
                .orElseThrow(ProcessNodeLifecycleListener::unavailable);
        if (releaseBinding.agent() == null || row.getKind() != KindEnum.AGENT || row.isExcluded() || !slot.getKey().equals(row.getNodeId())
                || !slot.getValue().attemptOrBodyId().equals(attempt.getCurrentActivityId())
                || !slot.getValue().scopeId().equals(occurrence.getCurrentActivityId())
                || !occurrence.getParentId().equals(row.getParentScopeId())
                || !occurrenceId(root, row.getNodeId(), occurrence.getId()).equals(row.getOccurrenceId())
                || !root.getConfigurationSha256().equals(row.getConfigurationSha256())) throw unavailable();
        return new EventWaitVO(context, releaseBinding, row, occurrence);
    }

    private void bindAgentWait(ExecutionEntity execution) {
        var wait = exactAgentWait(execution);
        if (wait == null) return;
        var subscription = exactNativeMessage(execution, wait.context().binding());
        nodes.bindAgentSubscription(wait.row(), subscription.getId());
    }

    /** Returns true when failure handling owns continuation or keeps the exact native wait parked. */
    private boolean onAgentWaitResult(ExecutionEntity execution) {
        var wait = exactAgentWait(execution);
        if (wait == null) return false;
        var row = wait.row();
        if (row.isExcluded() || row.getNativeJobId() == null || row.getAgentRunId() == null || row.getAcceptedAt() == null
                || !exactNativeMessage(execution, wait.context().binding()).getId().equals(row.getNativeSubscriptionId())) {
            throw unavailable();
        }
        if (row.getState() == StateEnum.RESULT_ACCEPTED && row.getOutput() != null && row.getErrorCode() == null) {
            applyNodeOutput(wait.context(), wait.definition(), row.getOutput(), wait.occurrence());
            nodes.finish(row, StateEnum.COMPLETED);
            publish(wait.context(), "NODE_COMPLETED", nodes.now(), row, null);
            return false;
        }
        if (row.getState() != StateEnum.FAILED || row.getOutput() != null || row.getErrorCode() == null) throw unavailable();
        var policy = wait.definition().policy();
        String action = policy.onFailure().action();
        var now = nodes.now();
        if ("RETRY".equals(action) && canRunScheduledRetryFrom(row)
                && policy.retry().retryableCodes().contains(row.getErrorCode()) && canRetry(policy, row, now)) {
            scheduleAttemptRetryTimer(execution, row, policy, now);
            return true;
        }
        if ("WAIT_RECOVERY".equals(action) || "RETRY".equals(action)) {
            nodes.parkFailedAgentResult(row);
            return true;
        }
        var slots = wait.context().verified().controlBindings().policySlots().get(row.getNodeId());
        if (slots == null || slots.failureBoundaryId() == null) throw unavailable();
        captureBoundary(row, slots.failureBoundaryId(), "FAILURE", wait.occurrence().getCurrentActivityId());
        ErrorPropagation.propagateError("TIANJI_AUTOMATION_NODE_FAILURE", execution);
        return true;
    }

    private EventWaitVO exactAgentWait(ExecutionEntity execution) {
        var context = context(execution);
        var entry = context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                .filter(item -> execution.getCurrentActivityId().equals(item.getValue().waitId())).findFirst().orElse(null);
        if (entry == null) return null;
        var definition = binding(context.release(), entry.getKey());
        if (definition.agent() == null || !"AGENT".equals(definition.kind())) return null;
        var slot = entry.getValue();
        var attempt = requireParent(execution);
        var occurrence = requireParent(attempt);
        var root = context.binding();
        var row = nodes.findAttempt(root.getTenantId(), root.getInstanceId(), attempt.getId())
                .orElseThrow(ProcessNodeLifecycleListener::unavailable);
        if (!slot.attemptOrBodyId().equals(attempt.getCurrentActivityId())
                || !slot.scopeId().equals(occurrence.getCurrentActivityId())
                || row.getKind() != KindEnum.AGENT || !entry.getKey().equals(row.getNodeId())
                || !occurrence.getParentId().equals(row.getParentScopeId())
                || !occurrenceId(root, row.getNodeId(), occurrence.getId()).equals(row.getOccurrenceId())
                || !root.getConfigurationSha256().equals(row.getConfigurationSha256())
                || !schemaSha256(definition.outputSchema()).equals(row.getOutputSchemaSha256())) throw unavailable();
        return new EventWaitVO(context, definition, row, occurrence);
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
            publish(context, "TASK_COMPLETED", nodes.now(), row, task.getId());
            return;
        }
        String expectedSha = expectedHumanPolicySha256(context.release(), scope.getKey());
        var policy = requireCandidatePolicy(context.binding().getTenantId(), logical.human().candidateBindingRef(), expectedSha);
        task.addCandidateGroups(policy.lanes().get(policy.initialLane()).stream().map(role -> "role:" + role).sorted().toList());
        Instant due = row.getStartedAt().plus(logical.human().dueAfter());
        task.setDueDate(Date.from(due.isBefore(row.getAttemptDeadlineAt()) ? due : row.getAttemptDeadlineAt()));
        publish(context, "TASK_CREATED", row.getStartedAt(), row, task.getId());
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
                || row.getState() == StateEnum.CANCELLED
                || attemptDeadline && (row.getState() == StateEnum.TIMED_OUT || row.getState() == StateEnum.FAILED
                        || row.getState() == StateEnum.WAIT_RECOVERY)
                || !attemptDeadline && row.getCapabilityKey() == null && row.getKind() != KindEnum.AGENT
                        && (row.getState() == StateEnum.TIMED_OUT || row.getState() == StateEnum.FAILED)) {
            return false;
        }
        var deadline = attemptDeadline ? row.getAttemptDeadlineAt() : row.getNodeDeadlineAt();
        var now = nodes.now();
        if (now.isBefore(deadline)) throw unavailable();
        if (row.getCapabilityKey() != null && row.getState() == StateEnum.WAITING) {
            var worker = timerWorkerAttempt(context, row, occurrenceSlot);
            var job = requireWorkerJob(worker.row(), row.getNativeJobId());
            boolean unknown = effectUnknown(context, worker, job);
            if (attemptDeadline) {
                // A due hard timer owns final routing. Leave the native occurrence intact for it.
                if (!now.isBefore(row.getNodeDeadlineAt())) return false;
                var action = worker.binding().policy().onAttemptTimeout().action();
                if (unknown || "WAIT_RECOVERY".equals(action) || "RETRY".equals(action) && !canRetry(worker, now)) {
                    parkWorker(worker, job, unknown ? SideEffectOutcomeEnum.UNKNOWN : SideEffectOutcomeEnum.NONE, ATTEMPT_TIMEOUT);
                    return false;
                }
                if ("RETRY".equals(action)) {
                    scheduleRetry(worker, job, ATTEMPT_TIMEOUT, StateEnum.TIMED_OUT, now);
                    return false;
                }
            }
            nodes.retireWorkerAttempt(row, StateEnum.TIMED_OUT,
                    unknown ? SideEffectOutcomeEnum.UNKNOWN : SideEffectOutcomeEnum.NONE,
                    attemptDeadline ? ATTEMPT_TIMEOUT : NODE_TIMEOUT, null);
        } else if (row.getKind() == KindEnum.AGENT && row.getState() == StateEnum.WAITING) {
            if (!timeOutAgent(context, row, attemptDeadline, now)) return false;
        } else if (row.getState() != StateEnum.TIMED_OUT && row.getState() != StateEnum.FAILED) {
            nodes.finish(row, StateEnum.TIMED_OUT);
        }
        if (row.getKind() == KindEnum.EVENT_WAIT) closeWaitReservation(row);
        closeDiscardedReservations(context, List.of(requireParent(event)));
        captureBoundary(row, boundary.getId(), attemptDeadline ? ATTEMPT_TIMEOUT : NODE_TIMEOUT, occurrenceSlot.scopeId());
        return true;
    }

    private boolean timeOutAgent(LifecycleContextVO context, ProcessNodeExecutionBO row, boolean attemptDeadline, Instant now) {
        if (attemptDeadline && !now.isBefore(row.getNodeDeadlineAt())) return false;
        var policy = binding(context.release(), row.getNodeId()).policy();
        var effect = SideEffectOutcomeEnum.UNKNOWN;
        ExternalWorkerJobEntity job = null;
        ExecutionEntity execution = null;
        if (row.getAgentRunId() == null && row.getNativeJobId() != null) {
            nodes.lockWorkerJob(row);
            job = requireWorkerJob(row, row.getNativeJobId());
            execution = CommandContextUtil.getExecutionEntityManager().findById(job.getExecutionId());
            var exact = agentDispatchAttempt(execution);
            if (!exact.row().equals(row) || !FlowableWorkerService.nativeTypedAgentTopic().equals(job.getJobHandlerConfiguration())
                    || !execution.getProcessDefinitionId().equals(job.getProcessDefinitionId())
                    || !execution.getCurrentActivityId().equals(job.getElementId())) throw unavailable();
            if (job.getLockOwner() == null && job.getLockExpirationTime() == null && nodes.workerHistory(row).isEmpty()) {
                effect = SideEffectOutcomeEnum.NONE;
            }
        }
        String errorCode = attemptDeadline ? ATTEMPT_TIMEOUT : NODE_TIMEOUT;
        String action = (attemptDeadline ? policy.onAttemptTimeout() : policy.onNodeTimeout()).action();
        if ("RETRY".equals(action) && effect == SideEffectOutcomeEnum.NONE && canRetry(policy, row, now)) {
            retireExternalWorkerJob(row, job);
            scheduleAttemptRetryTimer(execution, row, policy, now);
            nodes.retireAgentWait(row, false, effect, errorCode);
            return false;
        }
        boolean park = "WAIT_RECOVERY".equals(action) || "RETRY".equals(action);
        nodes.retireAgentWait(row, park, effect, errorCode);
        return !park;
    }

    private void closeDiscardedReservations(LifecycleContextVO context, List<? extends DelegateExecution> roots) {
        if (subscriptions == null || roots.isEmpty()) return;
        var scopes = new LinkedHashSet<String>();
        var pending = new ArrayDeque<DelegateExecution>(roots);
        while (!pending.isEmpty()) {
            var child = pending.removeFirst();
            if (!scopes.add(child.getId())) throw unavailable();
            pending.addAll(child.getExecutions());
        }
        var root = context.binding();
        subscriptions.closeScopes(root.getTenantId(), root.getInstanceId(), scopes);
    }

    private void closeWaitReservation(ProcessNodeExecutionBO row) {
        if (subscriptions == null) return;
        subscriptions.findOpen(row.getTenantId(), row.getInstanceId(), row.getParentScopeId(), row.getNodeId())
                .ifPresent(reservation -> {
                    if (reservation.getState() == EventSubscriptionBO.State.BOUND
                            && !row.getNodeExecutionRef().equals(reservation.getNodeExecutionRef())) throw unavailable();
                    subscriptions.close(reservation);
                });
    }

    private WorkerAttemptVO timerWorkerAttempt(LifecycleContextVO context, ProcessNodeExecutionBO row, OccurrenceSlotVO slot) {
        if (row.getNativeJobId() == null) throw unavailable();
        nodes.lockWorkerJob(row);
        var job = requireWorkerJob(row, row.getNativeJobId());
        var manager = CommandContextUtil.getExecutionEntityManager();
        var execution = manager.findById(job.getExecutionId());
        if (execution == null || !slot.activityId().equals(execution.getCurrentActivityId())) throw unavailable();
        var attempt = exactWorkerAttempt(context, execution, job);
        if (!row.getNodeExecutionRef().equals(attempt.row().getNodeExecutionRef())
                || row.getRevision() != attempt.row().getRevision()) throw unavailable();
        return attempt;
    }

    private boolean effectUnknown(LifecycleContextVO context, WorkerAttemptVO attempt, ExternalWorkerJobEntity job) {
        if (attempt.binding().service().effectMode() == WorkerEffectSafetyEnum.READ_ONLY) return false;
        var row = attempt.row();
        // An undispatched recovery attempt still carries the earlier attempt's unresolved effect.
        if (row.getWorkerEffectRecovery() != null) return true;
        var history = nodes.workerHistory(row);
        if (history.isEmpty()) return job.getLockOwner() != null || job.getLockExpirationTime() != null;
        String expectedInput = WorkerLeaseStore.automationPhysicalInputSha256(job.getId(), row.getInstanceId(),
                job.getProcessDefinitionId(), job.getElementId(), row.getCapabilityKey(), row.getCapabilityVersion(),
                row.getNodeExecutionRef(), row.getInputSha256(), row.getConfigurationSha256());
        // V4 transport history proves a dispatch permit, never proves a write was rolled back.
        // An inconsistent history also cannot establish that a lease was never issued.
        for (var lease : history) {
            if (lease.tenantId() != row.getTenantId() || !row.getNativeJobId().equals(lease.jobId())
                    || !row.getOccurrenceId().equals(lease.occurrenceId()) || !"BPMN".equals(lease.scopeType())
                    || !row.getInstanceId().equals(lease.scopeId()) || !row.getEffectKey().equals(lease.idempotencyKey())
                    || !row.getCapabilityKey().equals(lease.topic()) || !row.getCapabilityVersion().toString().equals(lease.schemaVersion())
                    || !context.release().releaseVersion().equals(lease.releaseKey())
                    || !job.getProcessDefinitionId().equals(lease.definitionId()) || !job.getElementId().equals(lease.elementId())
                    || !context.verified().root().modelSha256().equals(lease.modelSha256()) || !expectedInput.equals(lease.inputSha256())) {
                log.warn("Automation worker lease history could not establish exact native attempt affinity");
                break;
            }
        }
        return true;
    }

    private static void captureBoundary(ProcessNodeExecutionBO row, String boundaryId, String outcome, String nativeOccurrence) {
        var captured = new BoundaryCaptureVO(row.getTenantId(), row.getInstanceId(), row.getNodeExecutionRef(),
                outcome, nativeOccurrence, row.getOccurrenceId());
        var command = CommandContextUtil.getCommandContext();
        var recoveryKey = TIMER_CAPTURE + boundaryId + "_recover";
        if (command.getAttribute(recoveryKey) != null) throw unavailable();
        command.addAttribute(recoveryKey, captured);
    }

    private void captureWorkerFailure(DelegateExecution workerExecution) {
        var context = context(workerExecution);
        var attempt = exactWorkerAttempt(context, workerExecution, null);
        var row = attempt.row();
        if (row.getState() != StateEnum.FAILED || row.getNativeJobId() == null) throw unavailable();
        requireCompletionJob(row, workerExecution);
        var policy = context.verified().controlBindings().policySlots().get(row.getNodeId());
        if (policy == null || policy.failureBoundaryId() == null) throw unavailable();
        // The native error boundary removes the occurrence before the policy activity executes.
        captureBoundary(row, policy.failureBoundaryId(), "FAILURE", attempt.slot().scopeId());
    }

    private static void requireCompletionJob(ProcessNodeExecutionBO row, DelegateExecution execution) {
        var command = CommandContextUtil.getCommandContext();
        if (!(command.getAttribute(WORKER_JOB_CAPTURE) instanceof WorkerCompletionJobVO job)
                || !Objects.equals(row.getNativeJobId(), job.id()) || !execution.getId().equals(job.executionId())
                || !execution.getTenantId().equals(job.tenantId()) || !execution.getProcessInstanceId().equals(job.instanceId())
                || !execution.getProcessDefinitionId().equals(job.definitionId())
                || !execution.getCurrentActivityId().equals(job.activityId())
                || row.getState() == StateEnum.RESULT_ACCEPTED && job.configuration() != null) throw unavailable();
        command.removeAttribute(WORKER_JOB_CAPTURE);
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
                    || !Set.of("END", "BRANCH").contains(action) || nodes.find(root.getTenantId(), root.getInstanceId(), captured.nodeExecutionRef())
                            .filter(row -> ("FAILURE".equals(outcome) ? row.getState() == StateEnum.FAILED
                                    : row.getState() == StateEnum.TIMED_OUT || "NODE_TIMEOUT".equals(outcome) && row.getState() == StateEnum.FAILED)
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
        String inputSchema = schemaSha256(binding.inputSchema());
        Instant now = nodes.now();
        var capability = binding.service();
        var occurrenceId = occurrenceId(root, nodeId, occurrence.getId());
        boolean retryableOccurrence = capability != null || binding.agent() != null;
        var previous = retryableOccurrence
                ? nodes.findByOccurrence(root.getTenantId(), root.getInstanceId(), occurrenceId).orElse(null) : null;
        if (previous != null && (!canRunScheduledRetryFrom(previous)
                || previous.getState() != StateEnum.FAILED && previous.getState() != StateEnum.TIMED_OUT
                || previous.getAttempt() >= binding.policy().retry().maxAttempts()
                || !now.isBefore(previous.getNodeDeadlineAt()))) throw unavailable();
        int attemptNumber = previous == null ? 1 : previous.getAttempt() + 1;
        var nodeDeadline = retryableOccurrence ? workerNodeDeadline(occurrence, binding) : now.plus(binding.policy().nodeTimeout());
        if (previous != null && !nodeDeadline.equals(previous.getNodeDeadlineAt())) throw unavailable();
        var input = previous != null && binding.agent() != null ? previous.getInput() : formInput(context, binding, occurrence, previous);
        var attemptDeadline = now.plus(binding.policy().attemptTimeout());
        if (attemptDeadline.isAfter(nodeDeadline)) attemptDeadline = nodeDeadline;
        var executionRef = executionRef(root.getTenantId(), root.getInstanceId(), occurrenceId, attemptNumber);
        var row = new ProcessNodeExecutionBO().setTenantId(root.getTenantId()).setInstanceId(root.getInstanceId())
                .setOccurrenceId(occurrenceId).setAttempt(attemptNumber).setNodeExecutionRef(executionRef).setNodeId(nodeId)
                .setKind(KindEnum.valueOf(binding.kind())).setParentScopeId(occurrence.getParentId()).setAttemptScopeId(attempt.getId())
                .setBranchIndex(currentBranchIndex(context, occurrence))
                .setIterationIndex(occurrence.getParent().getVariable(LOOP_INDEX) instanceof Integer index ? index : null)
                .setNativeJobId(nativeJobId).setInput(input).setInputSchemaSha256(inputSchema)
                .setInputSha256(ProcessNodeExecutionStore.digest("AUTOMATION_NODE_INPUT_V2", inputSchema, input))
                .setOutputSchemaSha256(schemaSha256(binding.outputSchema())).setConfigurationSha256(root.getConfigurationSha256())
                .setState(capability != null || waiting(binding.kind()) ? StateEnum.WAITING : StateEnum.RUNNING).setRevision(1)
                .setStartedAt(now).setNodeDeadlineAt(nodeDeadline).setAttemptDeadlineAt(attemptDeadline)
                .setSideEffectOutcome(SideEffectOutcomeEnum.NONE);
        if (capability != null) {
            Object worker = object(context.release().dependencies().get("workerCapabilities")).get(nodeId);
            Object contractSha = object(worker).get("contractSha256");
            if (!(contractSha instanceof String sha) || !ProcessInstanceBindingStore.sha256(sha)) throw unavailable();
            row.setCapabilityKey(capability.capabilityKey()).setCapabilityVersion(capability.version()).setEffectKey("effect-"
                    + hash("AUTOMATION_NODE_EFFECT_V2", List.of(root.getTenantId(), root.getInstanceId(), occurrenceId,
                            capability.capabilityKey(), capability.version(), sha)));
        }
        if (previous != null) {
            if (!Objects.equals(row.getEffectKey(), previous.getEffectKey()) || !row.getInputSha256().equals(previous.getInputSha256())
                    || !row.getConfigurationSha256().equals(previous.getConfigurationSha256())) throw unavailable();
        }
        nodes.insertOccurrence(row);
        registerMultiChildNode(occurrence, executionRef);
        occurrence.setVariableLocal("tianjiAutomationNodeOccurrence", occurrence.getId());
        if (row.getState() == StateEnum.WAITING) publish(context, "NODE_WAITING", row.getStartedAt(), row, null);
    }

    private void publish(LifecycleContextVO context, String type, Instant occurredAt, ProcessNodeExecutionBO row, String taskId) {
        if (lifecycleEvents != null) {
            lifecycleEvents.publish(context.binding(), type, occurredAt, row == null ? null : row.getOccurrenceId(),
                    row == null ? null : row.getAttempt(), taskId, null);
        }
    }

    private void onBootstrapCompleted(DelegateExecution execution) {
        if (lifecycleEvents == null) return;
        var context = context(execution);
        publish(context, "INSTANCE_STARTED", context.binding().getSubmittedAt(), null, null);
    }

    private static void registerMultiChildNode(DelegateExecution occurrence, String nodeExecutionRef) {
        for (var parent = occurrence.getParent(); parent != null; parent = parent.getParent()) {
            if (parent.getVariableLocal(AutomationModelCompiler.MULTI_INSTANCE_INDEX) instanceof Integer) {
                parent.setVariableLocal(MULTI_NODE_REF + nodeExecutionRef, nodeExecutionRef);
            }
        }
    }

    private void finishExit(LifecycleContextVO context, AutomationNodeBindingVO binding, ProcessNodeExecutionBO row,
                            DelegateExecution occurrence) {
        if (row.getState() == StateEnum.RESULT_ACCEPTED) {
            if (binding.human() != null && row.getOutput() != null) applyNodeOutput(context, binding, row.getOutput(), occurrence);
            nodes.finish(row, StateEnum.COMPLETED);
            publish(context, "NODE_COMPLETED", nodes.now(), row, null);
        } else if (row.getState() == StateEnum.RUNNING || row.getState() == StateEnum.WAITING && "TIMER".equals(binding.kind())) {
            nodes.finish(row, StateEnum.COMPLETED);
            publish(context, "NODE_COMPLETED", nodes.now(), row, null);
        } else if (row.getState() == StateEnum.WAITING && (row.getKind() == KindEnum.HUMAN
                || row.getKind() == KindEnum.EVENT_WAIT && subscriptions != null)) {
            throw unavailable();
        }
    }

    private void applyNodeOutput(LifecycleContextVO context, AutomationNodeBindingVO binding, Map<String, Object> output,
                                  DelegateExecution occurrence) {
        if (binding.outputMapping().isEmpty()) return;
        var parent = occurrence.getParent();
        if (parent == null || !context.binding().getInstanceId().equals(parent.getRootProcessInstanceId())) throw unavailable();
        var current = occurrence.getCurrentFlowElement();
        var placement = branchScopes(context, current);
        String variable = placement.isEmpty() ? AutomationBootstrapBehavior.SCOPE : placement.getFirst().variable();
        var priorScope = visibleScope(context, parent, current);
        var data = mutableObject(priorScope);
        String outputPrefix = "nodes." + binding.nodeId() + ".output.";
        for (var mapping : binding.outputMapping()) {
            String source = mapping.source();
            Object value;
            if (source.startsWith(outputPrefix)) {
                value = lookup(output, source.substring(outputPrefix.length()));
            } else if (source.startsWith("output.")) {
                value = lookup(output, source.substring(7));
            } else {
                value = mappedValue(context, parent, current.getParentContainer(), priorScope, source);
            }
            String target = mapping.target().startsWith("scope.") ? mapping.target().substring(6) : mapping.target();
            assign(data, target, value);
        }
        storeNativeObject(context, parent, variable, data);
    }

    private static LinkedHashMap<String, Object> mutableObject(Map<String, Object> data) {
        var result = new LinkedHashMap<String, Object>();
        data.forEach((key, value) -> result.put(key, mutableValue(value)));
        return result;
    }

    private static Object mutableValue(Object value) {
        if (value instanceof Map<?, ?> map) return mutableObject(object(map));
        if (value instanceof List<?> list) {
            var copy = new ArrayList<Object>(list.size());
            for (Object item : list) copy.add(mutableValue(item));
            return copy;
        }
        return value;
    }

    private Map<String, Object> formInput(LifecycleContextVO context, AutomationNodeBindingVO binding, DelegateExecution occurrence,
            ProcessNodeExecutionBO previous) {
        var parent = requireParent(occurrence);
        var scope = visibleScope(context, parent, occurrence.getCurrentFlowElement());
        var container = occurrence.getCurrentFlowElement().getParentContainer();
        var form = new LinkedHashMap<String, Object>();
        for (var mapping : binding.inputMapping()) {
            if ("EVENT_WAIT".equals(binding.kind()) && mapping.source().startsWith("event.")) continue;
            assign(form, mapping.target(), mappedValue(context, parent, container, scope, mapping.source()));
        }
        if ("EVENT_WAIT".equals(binding.kind())) return form;
        if (binding.service() != null) prepareOutboundEvent(context, binding, parent, container, occurrence, form, previous);
        return object(binding.inputSchema().normalizeValue(form));
    }

    private void prepareOutboundEvent(LifecycleContextVO context, AutomationNodeBindingVO worker, DelegateExecution parent,
            FlowElementsContainer container, DelegateExecution occurrence, Map<String, Object> form, ProcessNodeExecutionBO previous) {
        if (eventReservations == null) return;
        var root = context.binding();
        var capability = worker.service();
        var configured = eventReservations.find(root.getTenantId(), context.release().compiledSha256(), worker.nodeId(),
                capability.capabilityKey(), capability.version()).orElse(null);
        if (configured == null) return;
        if (subscriptions == null || worker.inputMapping().stream()
                .anyMatch(mapping -> pathsOverlap(mapping.target(), configured.inputPath()))) throw unavailable();
        var route = context.release().source().eventRoutes().stream().filter(item -> "SIGNAL".equals(item.mode())
                && configured.routeKey().equals(item.routeKey()) && configured.routeVersion() == item.version()
                && configured.targetNodeId().equals(item.targetNodeId())).findFirst().orElseThrow(ProcessNodeLifecycleListener::unavailable);
        var target = binding(context.release(), route.targetNodeId());
        var targetSlot = context.verified().controlBindings().occurrenceSlots().get(target.nodeId());
        if (!"EVENT_WAIT".equals(target.kind()) || targetSlot == null
                || !reachableWait(container, occurrence.getCurrentFlowElement(), targetSlot.scopeId())) throw unavailable();
        if (previous != null) {
            var token = lookup(previous.getInput(), configured.inputPath());
            if (!(token instanceof String exact)) throw unavailable();
            var reserved = subscriptions.find(root.getTenantId(), ProcessEventSubscriptionStore.tokenSha256(exact))
                    .orElseThrow(ProcessNodeLifecycleListener::unavailable);
            if (!root.getInstanceId().equals(reserved.getInstanceId()) || !parent.getId().equals(reserved.getParentScopeId())
                    || !target.nodeId().equals(reserved.getTargetNodeId()) || !route.routeKey().equals(reserved.getRouteKey())
                    || route.version() != reserved.getRouteVersion() || !route.sourceRef().equals(reserved.getSourceRef())) throw unavailable();
            assign(form, configured.inputPath(), exact);
            return;
        }

        // Normalize the full input before writing its reservation. The real token is exactly 43 URL-safe characters.
        var input = mutableObject(form);
        assign(input, configured.inputPath(), "A".repeat(43));
        var normalized = object(worker.inputSchema().normalizeValue(input));
        if (!(lookup(normalized, configured.inputPath()) instanceof String)) throw unavailable();
        var now = subscriptions.now();
        var deadline = now.plus(route.ttl()).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var reserved = subscriptions.reserve(root.getTenantId(), root.getInstanceId(), parent.getId(), target.nodeId(),
                UUID.randomUUID(), route.routeKey(), route.version(), route.sourceRef(), deadline);
        assign(form, configured.inputPath(), reserved.token());
    }

    private static boolean reachableWait(FlowElementsContainer container, FlowElement source, String target) {
        if (!(source instanceof FlowNode first) || !(container.getFlowElement(target) instanceof SubProcess)) return false;
        var visited = new LinkedHashSet<String>();
        var pending = new ArrayDeque<FlowNode>();
        pending.add(first);
        while (!pending.isEmpty()) {
            var candidate = pending.removeFirst();
            if (!visited.add(candidate.getId())) continue;
            if (target.equals(candidate.getId())) return true;
            candidate.getOutgoingFlows().stream().map(SequenceFlow::getTargetFlowElement)
                    .filter(FlowNode.class::isInstance).map(FlowNode.class::cast).forEach(pending::addLast);
        }
        return false;
    }

    private static boolean pathsOverlap(String first, String second) {
        return first.equals(second) || first.startsWith(second + ".") || first.startsWith(second + "[")
                || second.startsWith(first + ".") || second.startsWith(first + "[");
    }

    private Object mappedValue(LifecycleContextVO context, DelegateExecution parent, FlowElementsContainer container,
                               Map<String, Object> scope, String path) {
        if (path.startsWith("input.")) return lookup(rootInput(context), path.substring("input.".length()));
        if (path.startsWith("scope.")) return lookup(scope, path.substring("scope.".length()));
        if (path.startsWith("nodes.")) {
            var reference = path.substring("nodes.".length());
            var sources = context.verified().controlBindings().occurrenceSlots().entrySet().stream()
                    .filter(entry -> reference.startsWith(entry.getKey() + ".output.")
                            && container.getFlowElements().stream().anyMatch(element -> entry.getValue().scopeId().equals(element.getId())))
                    .limit(2).toList();
            if (sources.size() != 1) throw unavailable();
            var source = sources.getFirst().getKey();
            return lookup(completedSource(context, parent, source).getOutput(), reference.substring(source.length() + ".output.".length()));
        }
        throw unavailable();
    }

    private ProcessNodeExecutionBO completedSource(LifecycleContextVO context, DelegateExecution parent, String nodeId) {
        return completedSource(context, parent.getId(), nodeId);
    }

    private ProcessNodeExecutionBO completedSource(LifecycleContextVO context, String parentId, String nodeId) {
        var root = context.binding();
        return nodes.requireCompletedSource(root.getTenantId(), root.getInstanceId(), root.getConfigurationSha256(), parentId, nodeId);
    }

    private static Map<String, Object> rootInput(LifecycleContextVO context) {
        var engine = context.engine();
        Object rootRef = engine.getRuntimeService().getVariableLocal(context.binding().getInstanceId(), AutomationBootstrapBehavior.ROOT_INPUTS);
        return object(ProcessExecutionService.decodeNativeJson(engine, rootRef, ProcessNodeExecutionStore.MAX_DATA));
    }

    private static Object lookup(Map<String, Object> object, String path) {
        Object current = object;
        var segments = FIELD_SEGMENTS.matcher(path);
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
        var parts = FIELD_SEGMENTS.matcher(path).results().map(java.util.regex.MatchResult::group).toList();
        if (parts.isEmpty()) throw unavailable();
        Object current = target;
        for (int index = 0; index + 1 < parts.size(); index++) {
            String key = parts.get(index);
            boolean array = parts.get(index + 1).startsWith("[");
            Object child = getOrCreate(current, key, array);
            if (array ? !(child instanceof List<?>) : !(child instanceof Map<?, ?>)) throw unavailable();
            current = child;
        }
        putValue(current, parts.getLast(), mutableValue(value));
    }

    private static Object getOrCreate(Object container, String key, boolean nextIsArray) {
        Object item;
        if (container instanceof List<?> list) {
            int index = arrayIndex(key);
            item = index < list.size() ? list.get(index) : null;
        } else {
            item = object(container).get(key);
        }
        if (item != null) return item;
        Object created = nextIsArray ? new ArrayList<>() : new LinkedHashMap<>();
        putValue(container, key, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    private static void putValue(Object container, String key, Object value) {
        if (container instanceof List<?> list) {
            int index = arrayIndex(key);
            var target = (List<Object>) list;
            while (target.size() <= index) target.add(null);
            target.set(index, value);
        } else {
            if (key.startsWith("[")) throw unavailable();
            object(container).put(key, value);
        }
    }

    private static int arrayIndex(String segment) {
        if (!segment.startsWith("[") || !segment.endsWith("]")) throw unavailable();
        return Integer.parseInt(segment.substring(1, segment.length() - 1));
    }

    private LifecycleContextVO context(DelegateExecution execution) {
        return context(execution, true);
    }

    private LifecycleContextVO context(DelegateExecution execution, boolean guarded) {
        if (execution == null || !(CommandContextUtil.getProcessEngineConfiguration() instanceof SpringProcessEngineConfiguration process)
                || !(CommandContextUtil.getDmnEngineConfiguration() instanceof SpringDmnEngineConfiguration dmn)
                || execution.getRootProcessInstanceId() == null || execution.getTenantId() == null
                || !execution.getTenantId().matches("[1-9][0-9]{0,18}")) throw unavailable();
        long tenant = Long.parseLong(execution.getTenantId());
        String root = execution.getRootProcessInstanceId();
        if (guarded) AutomationInstanceGuardCommandInterceptor.requireHeld(tenant, root);
        var binding = bindings.find(tenant, root).orElseThrow(ProcessNodeLifecycleListener::unavailable);
        var release = releases.find(tenant, binding.getReleaseKey()).orElseThrow(ProcessNodeLifecycleListener::unavailable);
        if (!binding.getDefinitionKey().equals(release.rootDefinitionKey())) throw unavailable();
        var verified = new ProcessReleaseService(process, dmn, new ProcessModelValidator()).verifiedAutomation(release);
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

    private Instant workerNodeDeadline(DelegateExecution occurrence, AutomationNodeBindingVO binding) {
        Object frozen = occurrence.getVariableLocal(OCCURRENCE_DEADLINE);
        if (frozen instanceof String value) return Instant.parse(value);
        if (frozen != null) throw unavailable();
        Instant deadline = nodes.now().plus(binding.policy().nodeTimeout());
        occurrence.setVariableLocal(OCCURRENCE_DEADLINE, deadline.toString());
        return deadline;
    }

    private static Instant workerNodeDeadline(DelegateExecution occurrence) {
        if (!(occurrence.getVariableLocal(OCCURRENCE_DEADLINE) instanceof String value)) throw unavailable();
        return Instant.parse(value);
    }

    private static long roundedTimerMillis(Instant due) {
        return due.toEpochMilli() + (due.getNano() % 1_000_000 == 0 ? 0 : 1);
    }

    private Optional<Instant> workerBoundaryDeadline(DelegateExecution event, BoundaryEvent boundary) {
        var context = context(event);
        var verified = context.verified().controlBindings();
        var slot = verified.policySlots().entrySet().stream()
                .filter(entry -> boundary.getId().equals(entry.getValue().attemptTimeoutBoundaryId())
                        || boundary.getId().equals(entry.getValue().nodeTimeoutBoundaryId()))
                .findFirst();
        if (slot.isEmpty()) return Optional.empty();
        var binding = binding(context.release(), slot.get().getKey());
        if (binding.service() == null && binding.agent() == null) return Optional.empty();
        var scope = verified.occurrenceSlots().get(slot.get().getKey());
        var parent = event.getParent();
        if (parent == null) throw unavailable();
        if (boundary.getId().equals(slot.get().getValue().nodeTimeoutBoundaryId())) {
            if (!scope.scopeId().equals(parent.getCurrentActivityId())) throw unavailable();
            return Optional.of(workerNodeDeadline(parent, binding));
        }
        if (!scope.attemptOrBodyId().equals(parent.getCurrentActivityId())) throw unavailable();
        var occurrence = exactOccurrence(parent.getParent(), scope);
        // Flowable can install the boundary timer before it calls the attempt's start listener.
        insert(context, binding, slot.get().getKey(), occurrence, parent, scope, null);
        var root = context.binding();
        return Optional.of(nodes.findAttempt(root.getTenantId(), root.getInstanceId(), event.getParentId())
                .orElseThrow(ProcessNodeLifecycleListener::unavailable).getAttemptDeadlineAt());
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

    private static String executionRef(long tenantId, String instanceId, String occurrenceId, int attempt) {
        return "node-" + hash("AUTOMATION_NODE_EXECUTION_V2", List.of(tenantId, instanceId, occurrenceId, attempt));
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

    private static AutomationOperationException recoveryBlocked() {
        return new AutomationOperationException(AutomationOperationException.Code.TIANJI_NODE_RETRY_BLOCKED);
    }

    private record BoundaryCaptureVO(long tenantId, String instanceId, String nodeExecutionRef, String outcome,
                                     String nativeOccurrence, String exactOccurrenceId) { }

    private record LifecycleContextVO(SpringProcessEngineConfiguration engine, AutomationInstanceBindingBO binding,
            ImmutableReleaseVO release, VerifiedProcessModels.VerifiedAutomationModelsVO verified) { }

    private record EventWaitVO(LifecycleContextVO context, AutomationNodeBindingVO definition, ProcessNodeExecutionBO row,
            DelegateExecution occurrence) { }

    private record WorkerAttemptVO(AutomationNodeBindingVO binding, ProcessNodeExecutionBO row, ExecutionEntity occurrence,
                                   ExecutionEntity execution, OccurrenceSlotVO slot) { }

    public record WorkerRecoveryWaitVO(String subscriptionId, String executionId, String definitionId, String activityId,
                                       String occurrenceExecutionId) { }

    private record VerifiedWorkerRecoveryWaitVO(WorkerAttemptVO attempt, EventSubscriptionEntity subscription) { }

    private record WorkerCompletionJobVO(String id, String executionId, String tenantId, String instanceId, String definitionId,
                                         String activityId, String configuration) { }

    private record WorkerCompletionJobCapture(Supplier<ProcessNodeLifecycleListener> listener) implements JobProcessor {
        @Override
        public void process(JobProcessorContext context) {
            if (!context.isInPhase(JobProcessorContext.Phase.BEFORE_EXECUTE) || !(context.getJobEntity() instanceof JobEntity job)
                    || !EXTERNAL_WORKER_COMPLETION.equals(job.getJobHandlerType()) || job.getExecutionId() == null) return;
            var command = CommandContextUtil.getCommandContext();
            var execution = CommandContextUtil.getExecutionEntityManager().findById(job.getExecutionId());
            if (execution == null || !(execution.getCurrentFlowElement() instanceof FlowNode activity)) return;
            boolean worker = activity.getBehavior() instanceof GuardedWorkerActivity;
            boolean agent = activity.getBehavior() instanceof GuardedAgentDispatch;
            if (!worker && !agent) return;
            String configuration = job.getJobHandlerConfiguration();
            if (command.getAttribute(WORKER_JOB_CAPTURE) != null
                    || configuration != null && (!worker || !WORKER_FAILURE_CONFIG.equals(configuration))) {
                throw unavailable();
            }
            command.addAttribute(WORKER_JOB_CAPTURE, new WorkerCompletionJobVO(job.getId(), job.getExecutionId(), job.getTenantId(),
                    job.getProcessInstanceId(), job.getProcessDefinitionId(), job.getElementId(), configuration));
            if (configuration != null) listener.get().captureWorkerFailure(execution);
        }
    }

    private record WorkerJobCreationInterceptor(CreateExternalWorkerJobInterceptor delegate,
            Supplier<ProcessNodeLifecycleListener> listener) implements CreateExternalWorkerJobInterceptor {
        @Override
        public void beforeCreateExternalWorkerJob(CreateExternalWorkerJobBeforeContext context) {
            if (delegate != null) delegate.beforeCreateExternalWorkerJob(context);
        }

        @Override
        public void afterCreateExternalWorkerJob(CreateExternalWorkerJobAfterContext context) {
            if (delegate != null) delegate.afterCreateExternalWorkerJob(context);
            if (context.getExternalWorkerServiceTask().getBehavior() instanceof GuardedWorkerActivity) {
                listener.get().onWorkerJobCreated(context);
            }
        }
    }

    private record WorkerRetryJobHandler(Supplier<ProcessNodeLifecycleListener> listener) implements JobHandler {
        @Override
        public String getType() { return RETRY_JOB; }

        @Override
        public void execute(JobEntity job, String nodeRef, VariableScope scope, CommandContext context) {
            if (!(scope instanceof ExecutionEntity execution)) throw unavailable();
            listener.get().runNodeRetry(job, nodeRef, execution);
        }
    }

    @FunctionalInterface
    public interface EventReservationDirectory {
        /** Looks up immutable registrations in trusted, local host configuration. */
        Optional<OutboundEventReservationVO> find(long tenantId, String compiledManifestSha256, String workerNodeId,
                String capabilityKey, long capabilityVersion);
    }

    public record OutboundEventReservationVO(String routeKey, long routeVersion, String targetNodeId, String inputPath) {
        public OutboundEventReservationVO {
            if (routeKey == null || !routeKey.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}") || routeVersion < 1
                    || routeVersion > ProcessTypedJson.V2_MAX_WIRE_INTEGER || targetNodeId == null
                    || !targetNodeId.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Invalid event reservation registration");
            new MappingVO(inputPath, "input.callback");
        }
    }

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

    private static final class ControlExpressionResolver extends ELResolver {
        private final Supplier<ProcessNodeLifecycleListener> listener;
        private final Object control = new Object();

        private ControlExpressionResolver(Supplier<ProcessNodeLifecycleListener> listener) {
            this.listener = listener;
        }

        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            if (base == null && AutomationModelCompiler.CONTROL_EXPRESSION_ROOT.equals(property)) {
                context.setPropertyResolved(true);
                return control;
            }
            if (base == control) throw unavailable();
            return null;
        }

        @Override
        public Object invoke(ELContext context, Object base, Object method, Class<?>[] types, Object[] arguments) {
            if (base != control) return null;
            context.setPropertyResolved(true);
            if (arguments == null || arguments.length != 2 || !(arguments[0] instanceof DelegateExecution execution)
                    || !(arguments[1] instanceof String id)) throw unavailable();
            var runtime = listener.get();
            var activity = runtime.context(execution);
            if (ControlPredicate.XOR.methodName().equals(method)) return runtime.onCondition(activity, execution, id);
            if (ControlPredicate.LOOP_CONTINUATION.methodName().equals(method)) return runtime.continueLoop(activity, execution, id);
            if (ControlPredicate.MULTI_INSTANCE_ITEMS.methodName().equals(method)) return runtime.multiItems(activity, execution, id);
            if (ControlPredicate.MULTI_INSTANCE_COMPLETION.methodName().equals(method)) {
                return runtime.completeMulti(activity, execution, id);
            }
            throw unavailable();
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            if (base == control) throw unavailable();
            return null;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object value) {
            if (base == control || base == null && AutomationModelCompiler.CONTROL_EXPRESSION_ROOT.equals(property)) {
                throw unavailable();
            }
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            if (base == control || base == null && AutomationModelCompiler.CONTROL_EXPRESSION_ROOT.equals(property)) {
                context.setPropertyResolved(true);
                return true;
            }
            return false;
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            return null;
        }
    }

    private record LifecycleParseHandler(Supplier<ProcessNodeLifecycleListener> listener) implements BpmnParseHandler {
        @Override
        public Collection<Class<? extends BaseElement>> getHandledTypes() {
            return List.of(SubProcess.class, UserTask.class, ServiceTask.class, ExternalWorkerServiceTask.class,
                    BoundaryEvent.class, EndEvent.class, SequenceFlow.class, IntermediateCatchEvent.class);
        }

        @Override
        public void parse(BpmnParse parse, BaseElement element) {
            if (parse.getCurrentProcess() == null
                    || !"AUTOMATION_V2".equals(parse.getCurrentProcess().getAttributeValue(MODEL_NAMESPACE, "profile"))) return;
            if (element instanceof SubProcess subprocess) {
                var listeners = new ArrayList<>(subprocess.getExecutionListeners());
                if (subprocess.getId().startsWith("ta_") && listeners.stream().noneMatch(item -> ENTER.equals(item.getImplementation()))) {
                    listeners.add(nativeListener("start", (ExecutionListener) execution -> listener.get().onAttempt(execution)));
                }
                listeners.replaceAll(item -> switch (item.getImplementation() == null ? "" : item.getImplementation()) {
                    case ENTER -> nativeListener(item.getEvent(), (ExecutionListener) execution -> listener.get().onScope(execution, true));
                    case EXIT -> nativeListener(item.getEvent(), (ExecutionListener) execution -> listener.get().onScope(execution, false));
                    default -> item;
                });
                subprocess.setExecutionListeners(listeners);
            }
            if (element instanceof SequenceFlow flow && flow.getId().startsWith("ta_")) {
                var listeners = new ArrayList<>(flow.getExecutionListeners());
                listeners.add(nativeListener("take", (ExecutionListener) execution -> listener.get().onBranchArrival(execution, flow)));
                flow.setExecutionListeners(listeners);
            }
            if (element instanceof EndEvent end && end.getId().startsWith("ta_")) {
                var listeners = new ArrayList<>(end.getExecutionListeners());
                listeners.add(nativeListener("start", (ExecutionListener) execution -> listener.get().beforeScopeExit(execution)));
                end.setExecutionListeners(listeners);
            }
            if (element instanceof ServiceTask task && AutomationModelCompiler.bootstrapActivityId().equals(task.getId())) {
                var listeners = new ArrayList<>(task.getExecutionListeners());
                listeners.add(nativeListener("end", (ExecutionListener) execution -> listener.get().onBootstrapCompleted(execution)));
                task.setExecutionListeners(listeners);
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
            if (element instanceof IntermediateCatchEvent message && message.getId().startsWith("ta_")
                    && message.getEventDefinitions().size() == 1
                    && message.getEventDefinitions().getFirst() instanceof MessageEventDefinition definition
                    && message.getBehavior() instanceof IntermediateCatchMessageEventActivityBehavior) {
                message.setBehavior(new GuardedEventWait(listener, definition));
            }
            if (element instanceof ExternalWorkerServiceTask task && task.getId().startsWith("ta_")
                    && task.getTopic() != null && task.getTopic().startsWith("tianji-v2:worker:")) {
                Expression topic = CommandContextUtil.getProcessEngineConfiguration()
                        .getExpressionManager().createExpression(task.getTopic());
                task.setBehavior(new GuardedWorkerActivity(listener, task, topic));
            }
            if (element instanceof ServiceTask task && AGENT_DISPATCH.equals(task.getImplementation())
                    && task.getId().startsWith("ta_")) {
                task.setBehavior(new GuardedAgentDispatch(listener));
            }
            if (element instanceof ServiceTask task && marker(AutomationModelCompiler.DECISION_DELEGATE).equals(task.getImplementation())
                    && task.getId().startsWith("ta_")) {
                task.setBehavior(PinnedDecisionActivityBehaviorFactory.createAutomationRuleBehavior(task,
                        execution -> listener.get().onRule(execution)));
            }
            if (element instanceof ServiceTask task && marker(AutomationModelCompiler.MERGE_DELEGATE).equals(task.getImplementation())
                    && task.getId().startsWith("ta_")) {
                task.setBehavior(new MergeActivity(listener));
            }
            if (element instanceof ServiceTask task && POLICY.equals(task.getImplementation()) && task.getId().startsWith("ta_")) {
                String outcome = task.getAttributeValue(MODEL_NAMESPACE, "outcome");
                String action = task.getAttributeValue(MODEL_NAMESPACE, "action");
                String occurrence = task.getAttributeValue(MODEL_NAMESPACE, "occurrence");
                task.setBehavior(new PolicyActivity(listener, outcome, action, occurrence));
            }
        }

        private static String marker(String id) {
            return "${" + id + "}";
        }

        private static FlowableListener nativeListener(String event, Object implementation) {
            var result = new FlowableListener();
            result.setEvent(event);
            result.setImplementationType("instance");
            result.setInstance(implementation);
            return result;
        }
    }

    private static final class GuardedEventWait extends IntermediateCatchMessageEventActivityBehavior {
        private final Supplier<ProcessNodeLifecycleListener> listener;

        private GuardedEventWait(Supplier<ProcessNodeLifecycleListener> listener, MessageEventDefinition definition) {
            super(definition);
            this.listener = listener;
        }

        @Override
        public void execute(DelegateExecution execution) {
            var nativeExecution = (ExecutionEntity) execution;
            // Flowable inserts the subscription before adding it to this lazy collection.
            // Initialize the collection first so a resumed execution cannot cache the same row twice.
            nativeExecution.getEventSubscriptions();
            super.execute(execution);
            listener.get().bindEventWait(nativeExecution);
            listener.get().bindAgentWait(nativeExecution);
        }

        @Override
        public void trigger(DelegateExecution execution, String signal, Object data) {
            var active = listener.get();
            if (active.onAgentWaitResult((ExecutionEntity) execution)) return;
            active.onEventWaitResult(execution);
            super.trigger(execution, signal, data);
        }
    }

    private static final class GuardedAgentDispatch extends AbstractBpmnActivityBehavior {
        private final Supplier<ProcessNodeLifecycleListener> listener;

        private GuardedAgentDispatch(Supplier<ProcessNodeLifecycleListener> listener) {
            this.listener = listener;
        }

        @Override
        public void execute(DelegateExecution execution) {
            listener.get().createAgentDispatchJob(execution);
        }

        @Override
        public void trigger(DelegateExecution execution, String signal, Object data) {
            listener.get().completeAgentDispatchJob(execution);
            leave(execution);
        }
    }

    private static final class GuardedWorkerActivity extends ExternalWorkerTaskActivityBehavior {
        private final Supplier<ProcessNodeLifecycleListener> listener;

        private GuardedWorkerActivity(Supplier<ProcessNodeLifecycleListener> listener, ExternalWorkerServiceTask task, Expression topic) {
            super(task, topic, null);
            this.listener = listener;
        }

        @Override
        public void trigger(DelegateExecution execution, String signal, Object data) {
            listener.get().onWorkerResult(execution);
            super.trigger(execution, signal, data);
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
        public void execute(DelegateExecution execution) {
            var deadline = listener.get().workerBoundaryDeadline(execution, boundary);
            if (deadline.isEmpty()) {
                super.execute(execution);
                return;
            }
            var timer = TimerUtil.createTimerEntityForTimerEventDefinition(timerEventDefinition, boundary, true,
                    (ExecutionEntity) execution, "trigger-timer",
                    TimerEventHandler.createConfiguration(boundary.getId(), timerEventDefinition.getEndDate(), timerEventDefinition.getCalendarName()));
            if (timer == null) throw unavailable();
            timer.setDuedate(new Date(roundedTimerMillis(deadline.orElseThrow())));
            CommandContextUtil.getTimerJobService().scheduleTimerJob(timer);
        }

        @Override
        public void trigger(DelegateExecution execution, String signal, Object data) {
            if (listener.get().beforeBoundaryTimer(execution, boundary)) super.trigger(execution, signal, data);
        }
    }

    private static final class MergeActivity extends AbstractBpmnActivityBehavior {
        private final Supplier<ProcessNodeLifecycleListener> listener;

        private MergeActivity(Supplier<ProcessNodeLifecycleListener> listener) {
            this.listener = listener;
        }

        @Override
        public void execute(DelegateExecution execution) {
            listener.get().onMerge(execution);
            leave(execution);
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
