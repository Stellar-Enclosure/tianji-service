package top.egon.cola.platform.tianji.process.server.worker;

import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerTask;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Operator-provided grants and schemas; a worker's own capability declaration grants nothing. */
public final class WorkerTopicRegistry {
    private final Map<Key, Registration> registrations;
    private final Map<TopicKey, Schema> schemas;

    public WorkerTopicRegistry(List<Registration> registrations) {
        if (registrations == null || registrations.isEmpty() || registrations.size() > 1_000) throw invalid();
        Map<Key, Registration> entries = new HashMap<>();
        Map<TopicKey, Schema> topics = new HashMap<>();
        for (Registration registration : registrations) {
            if (registration == null) throw invalid();
            ProcessActor actor = registration.actor();
            var key = new Key(actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), registration.topic(), registration.schemaVersion());
            if (entries.putIfAbsent(key, registration) != null) throw invalid();
            var topic = new TopicKey(actor.tenantId(), registration.topic(), registration.schemaVersion());
            var schema = new Schema(registration.inputs(), registration.outputs(), registration.inputSources(),
                    registration.outputTargets(), registration.effectSafety());
            Schema previous = topics.putIfAbsent(topic, schema);
            if (previous != null && !previous.equals(schema)) throw invalid();
        }
        this.registrations = Map.copyOf(entries);
        this.schemas = Map.copyOf(topics);
    }

    public Registration require(ProcessActor actor, String topic, String schemaVersion) {
        if (actor == null || actor.subjectType() != ProcessActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())) throw unavailable();
        ProcessModelValidator.nativeWorkerTopic(topic, schemaVersion);
        Registration result = registrations.get(new Key(actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), topic, schemaVersion));
        if (result == null) throw unavailable();
        return result;
    }

    public boolean acceptsModel(long tenantId, WorkerTask worker) {
        if (tenantId <= 0 || worker == null) return false;
        Schema expected = schemas.get(new TopicKey(tenantId, worker.topic(), worker.schemaVersion()));
        if (expected == null) return false;
        Map<String, ValueType> inputs = worker.inputs().stream().collect(Collectors.toUnmodifiableMap(
                item -> item.target(), item -> item.type()));
        Map<String, ValueType> outputs = worker.outputs().stream().collect(Collectors.toUnmodifiableMap(
                item -> item.source(), item -> item.type()));
        return expected.effectSafety() == worker.effectSafety() && expected.inputs().equals(inputs) && expected.outputs().equals(outputs)
                && worker.inputs().stream().allMatch(item -> expected.inputSources().get(item.target()).contains(item.source()))
                && worker.outputs().stream().allMatch(item -> expected.outputTargets().get(item.source()).contains(item.target()));
    }

    private static Map<String, ValueType> schema(Map<String, ValueType> values) {
        if (values == null || values.size() > 16) throw invalid();
        Map<String, ValueType> copy = new HashMap<>();
        values.forEach((name, type) -> {
            ProcessModelValidator.validateVariable(name);
            if (type == null) throw invalid();
            copy.put(name, type);
        });
        return Map.copyOf(copy);
    }

    private static Map<String, Set<String>> mappings(Map<String, Set<String>> values, Map<String, ValueType> schema) {
        if (values == null || !values.keySet().equals(schema.keySet())) throw invalid();
        Map<String, Set<String>> copy = new HashMap<>();
        values.forEach((name, allowed) -> {
            if (allowed == null || allowed.isEmpty() || allowed.size() > 16) throw invalid();
            allowed.forEach(ProcessModelValidator::validateVariable);
            copy.put(name, Set.copyOf(allowed));
        });
        return Map.copyOf(copy);
    }

    private static boolean wholeMillis(Duration value) {
        return value != null && value.toMillis() * 1_000_000L == value.toNanos();
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Worker topic registration is invalid"); }
    private static ProcessResourceNotFoundException unavailable() { return new ProcessResourceNotFoundException(); }

    public record Registration(long tenantId, String issuer, String subjectId, String topic, String schemaVersion,
                               Map<String, ValueType> inputs, Map<String, ValueType> outputs,
                               Map<String, Set<String>> inputSources, Map<String, Set<String>> outputTargets, int maxConcurrent,
                               Duration leaseDuration, Duration retryDelay, WorkerEffect effectSafety) {
        public Registration {
            ProcessActor.verified(tenantId, ProcessActor.SubjectType.SERVICE, subjectId, issuer);
            ProcessModelValidator.nativeWorkerTopic(topic, schemaVersion);
            inputs = schema(inputs);
            outputs = schema(outputs);
            inputSources = mappings(inputSources, inputs);
            outputTargets = mappings(outputTargets, outputs);
            if (maxConcurrent < 1 || maxConcurrent > 32 || leaseDuration == null
                    || leaseDuration.compareTo(Duration.ofSeconds(1)) < 0 || leaseDuration.compareTo(Duration.ofMinutes(15)) > 0
                    || retryDelay == null || retryDelay.isNegative() || retryDelay.compareTo(Duration.ofHours(1)) > 0
                    || !wholeMillis(leaseDuration) || !wholeMillis(retryDelay) || effectSafety == null) throw invalid();
        }

        public ProcessActor actor() { return ProcessActor.verified(tenantId, ProcessActor.SubjectType.SERVICE, subjectId, issuer); }
    }

    private record Key(long tenantId, String issuer, String subject, String topic, String version) { }
    private record TopicKey(long tenantId, String topic, String version) { }
    private record Schema(Map<String, ValueType> inputs, Map<String, ValueType> outputs,
                          Map<String, Set<String>> inputSources, Map<String, Set<String>> outputTargets, WorkerEffect effectSafety) {
        private Schema {
            inputs = Map.copyOf(inputs);
            outputs = Map.copyOf(outputs);
            inputSources = Map.copyOf(inputSources);
            outputTargets = Map.copyOf(outputTargets);
        }
    }
}
