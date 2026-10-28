package top.egon.cola.platform.tianji.process.server.release;

import java.util.Map;

/** Immutable result: deployment must use these validated bytes rather than a caller's mutable buffer. */
public final class ValidatedProcessModel {
    public enum Type { BPMN, DMN }
    public enum ValueType { STRING, INTEGER, BOOLEAN }

    private final Type type;
    private final String definitionKey;
    private final byte[] xml;
    private final Map<String, ValueType> inputs;
    private final Map<String, ValueType> outputs;

    ValidatedProcessModel(Type type, String definitionKey, byte[] xml,
                          Map<String, ValueType> inputs, Map<String, ValueType> outputs) {
        this.type = type;
        this.definitionKey = definitionKey;
        this.xml = xml.clone();
        this.inputs = Map.copyOf(inputs);
        this.outputs = Map.copyOf(outputs);
    }

    public Type type() { return type; }
    public String definitionKey() { return definitionKey; }
    public byte[] xml() { return xml.clone(); }
    public Map<String, ValueType> inputs() { return inputs; }
    public Map<String, ValueType> outputs() { return outputs; }
}
