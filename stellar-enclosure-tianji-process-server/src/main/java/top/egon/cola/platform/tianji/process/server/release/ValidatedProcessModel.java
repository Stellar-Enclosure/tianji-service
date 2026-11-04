package top.egon.cola.platform.tianji.process.server.release;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.time.Duration;

/** Immutable result: deployment must use these validated bytes rather than a caller's mutable buffer. */
public final class ValidatedProcessModel {
    public enum Type { BPMN, CMMN, DMN }
    public enum ValueType { STRING, INTEGER, BOOLEAN }
    public enum WorkerEffect { READ_ONLY, TRANSACTIONAL_RECEIPT, EXTERNAL_IDEMPOTENT }
    public enum HumanTaskKind { ACTION, APPROVAL }

    private final Type type;
    private final String definitionKey;
    private final byte[] xml;
    private final Map<String, ValueType> inputs;
    private final Map<String, ValueType> outputs;
    private final List<String> ruleIds;
    private final Map<String, String> decisionTasks;
    private final List<RouteCondition> routeConditions;
    private final Map<String, WorkerTask> workerTasks;
    private final Map<String, MessageWait> messageWaits;
    private final Map<String, HumanTask> humanTasks;
    private final Map<String, HumanTaskTimer> humanTaskTimers;
    private final Map<String, CaseSource> caseSources;

    ValidatedProcessModel(Type type, String definitionKey, byte[] xml,
                          Map<String, ValueType> inputs, Map<String, ValueType> outputs, List<String> ruleIds) {
        this(type, definitionKey, xml, inputs, outputs, ruleIds, Map.of(), List.of(), Map.of(), Map.of());
    }

    ValidatedProcessModel(Type type, String definitionKey, byte[] xml,
                          Map<String, ValueType> inputs, Map<String, ValueType> outputs, List<String> ruleIds,
                          Map<String, String> decisionTasks, List<RouteCondition> routeConditions,
                          Map<String, WorkerTask> workerTasks, Map<String, MessageWait> messageWaits) {
        this(type, definitionKey, xml, inputs, outputs, ruleIds, decisionTasks, routeConditions, workerTasks, messageWaits, Map.of());
    }

    ValidatedProcessModel(Type type, String definitionKey, byte[] xml,
                          Map<String, ValueType> inputs, Map<String, ValueType> outputs, List<String> ruleIds,
                          Map<String, String> decisionTasks, List<RouteCondition> routeConditions,
                          Map<String, WorkerTask> workerTasks, Map<String, MessageWait> messageWaits, Map<String, HumanTask> humanTasks) {
        this(type, definitionKey, xml, inputs, outputs, ruleIds, decisionTasks, routeConditions, workerTasks, messageWaits, humanTasks, Map.of());
    }

    ValidatedProcessModel(Type type, String definitionKey, byte[] xml,
                          Map<String, ValueType> inputs, Map<String, ValueType> outputs, List<String> ruleIds,
                          Map<String, String> decisionTasks, List<RouteCondition> routeConditions,
                          Map<String, WorkerTask> workerTasks, Map<String, MessageWait> messageWaits, Map<String, HumanTask> humanTasks, Map<String, HumanTaskTimer> humanTaskTimers) {
        this(type, definitionKey, xml, inputs, outputs, ruleIds, decisionTasks, routeConditions, workerTasks, messageWaits, humanTasks, humanTaskTimers, Map.of());
    }

    ValidatedProcessModel(Type type, String definitionKey, byte[] xml,
                          Map<String, ValueType> inputs, Map<String, ValueType> outputs, List<String> ruleIds,
                          Map<String, String> decisionTasks, List<RouteCondition> routeConditions,
                          Map<String, WorkerTask> workerTasks, Map<String, MessageWait> messageWaits, Map<String, HumanTask> humanTasks,
                          Map<String, HumanTaskTimer> humanTaskTimers, Map<String, CaseSource> caseSources) {
        this.type = type;
        this.definitionKey = definitionKey;
        this.xml = xml.clone();
        this.inputs = Map.copyOf(inputs);
        this.outputs = Map.copyOf(outputs);
        this.ruleIds = List.copyOf(ruleIds);
        this.decisionTasks = Map.copyOf(decisionTasks);
        this.routeConditions = List.copyOf(routeConditions);
        this.workerTasks = Map.copyOf(workerTasks);
        this.messageWaits = Map.copyOf(messageWaits);
        this.humanTasks = Map.copyOf(humanTasks);
        this.humanTaskTimers = Map.copyOf(humanTaskTimers);
        this.caseSources = Map.copyOf(caseSources);
    }

    public Type type() { return type; }
    public String definitionKey() { return definitionKey; }
    public byte[] xml() { return xml.clone(); }
    public Map<String, ValueType> inputs() { return inputs; }
    public Map<String, ValueType> outputs() { return outputs; }
    public List<String> ruleIds() { return ruleIds; }
    public Map<String, String> decisionTasks() { return decisionTasks; }
    public List<RouteCondition> routeConditions() { return routeConditions; }
    public Map<String, WorkerTask> workerTasks() { return workerTasks; }
    public Map<String, MessageWait> messageWaits() { return messageWaits; }
    public Map<String, HumanTask> humanTasks() { return humanTasks; }
    public Map<String, HumanTaskTimer> humanTaskTimers() { return humanTaskTimers; }
    public Map<String, CaseSource> caseSources() { return caseSources; }

    /** Released graph-derived metadata. Constructing it grants neither source identity nor native execution authority. */
    public record CaseSource(String caseDefinitionKey, String messageElementId, String messageName, String timerElementId, Duration timeout) { }

    public record HumanTaskField(String name, String target, ValueType type) { }
    public record HumanTaskTimer(String taskElementId, Duration duration) { }
    /** Only validator-produced metadata is a release approval; constructing this record grants no task or IAM authority. */
    public record HumanTask(HumanTaskKind kind, Map<String, Set<String>> lanes, String initialLane, String laneVariable,
                            String approvalVariable, List<HumanTaskField> fields) {
        public HumanTask {
            var copy = new java.util.LinkedHashMap<String, Set<String>>(); lanes.forEach((key, roles) -> copy.put(key, Set.copyOf(roles)));
            lanes = Map.copyOf(copy); fields = List.copyOf(fields);
        }
    }

    public record RouteCondition(String variable, ValueType type, String literal) { }
    public record MessageWait(String messageName, String workerElementId, String timerElementId, Duration maximumWait,
                              String statusVariable) {
        public MessageWait(String messageName, String workerElementId, String timerElementId, Duration maximumWait) {
            this(messageName, workerElementId, timerElementId, maximumWait, null);
        }
    }
    public record WorkerParameter(String source, String target, ValueType type) { }
    public record WorkerTask(String topic, String schemaVersion, WorkerEffect effectSafety,
                             List<WorkerParameter> inputs, List<WorkerParameter> outputs) {
        public WorkerTask {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
        }
    }
}
