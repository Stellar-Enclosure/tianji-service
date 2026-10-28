package top.egon.cola.platform.tianji.process.server.release;

import java.util.Map;
import java.util.List;

/** Immutable result: deployment must use these validated bytes rather than a caller's mutable buffer. */
public final class ValidatedProcessModel {
    public enum Type { BPMN, DMN }
    public enum ValueType { STRING, INTEGER, BOOLEAN }

    private final Type type;
    private final String definitionKey;
    private final byte[] xml;
    private final Map<String, ValueType> inputs;
    private final Map<String, ValueType> outputs;
    private final List<String> ruleIds;
    private final Map<String, String> decisionTasks;
    private final List<RouteCondition> routeConditions;

    ValidatedProcessModel(Type type, String definitionKey, byte[] xml,
                          Map<String, ValueType> inputs, Map<String, ValueType> outputs, List<String> ruleIds) {
        this(type, definitionKey, xml, inputs, outputs, ruleIds, Map.of(), List.of());
    }

    ValidatedProcessModel(Type type, String definitionKey, byte[] xml,
                          Map<String, ValueType> inputs, Map<String, ValueType> outputs, List<String> ruleIds,
                          Map<String, String> decisionTasks, List<RouteCondition> routeConditions) {
        this.type = type;
        this.definitionKey = definitionKey;
        this.xml = xml.clone();
        this.inputs = Map.copyOf(inputs);
        this.outputs = Map.copyOf(outputs);
        this.ruleIds = List.copyOf(ruleIds);
        this.decisionTasks = Map.copyOf(decisionTasks);
        this.routeConditions = List.copyOf(routeConditions);
    }

    public Type type() { return type; }
    public String definitionKey() { return definitionKey; }
    public byte[] xml() { return xml.clone(); }
    public Map<String, ValueType> inputs() { return inputs; }
    public Map<String, ValueType> outputs() { return outputs; }
    public List<String> ruleIds() { return ruleIds; }
    public Map<String, String> decisionTasks() { return decisionTasks; }
    public List<RouteCondition> routeConditions() { return routeConditions; }

    public record RouteCondition(String variable, ValueType type, String literal) { }
}
