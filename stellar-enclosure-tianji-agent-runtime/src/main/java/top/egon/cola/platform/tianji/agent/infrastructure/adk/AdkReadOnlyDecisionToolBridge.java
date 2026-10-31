package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionDeclaration;
import io.reactivex.rxjava3.core.Single;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolRegistry;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/** Host-bound declarations whose direct native fallback never executes a transport. Initialize host telemetry before construction. */
public final class AdkReadOnlyDecisionToolBridge {
    private final ToolInvocationRecorder recorder;
    private final ReadOnlyDecisionToolTransport transport;
    private final ToolRegistry registry;
    private final Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>> hostBindings;

    public AdkReadOnlyDecisionToolBridge(ToolInvocationRecorder recorder, ReadOnlyDecisionToolTransport transport, Collection<ToolDescriptor> descriptors,
                                       Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>> hostBindings) {
        if (recorder == null || transport == null || descriptors == null || hostBindings == null) throw invalid();
        AdkToolTelemetryPrivacy.requireSafe();
        this.recorder = recorder; this.transport = transport; this.registry = new ToolRegistry(new ArrayList<>(descriptors)); this.hostBindings = hostBindings;
    }
    public boolean usesRecorder(ToolInvocationRecorder candidate) { return recorder == candidate; }
    public boolean usesTransport(ReadOnlyDecisionToolTransport candidate) { return transport == candidate; }
    RunScope bind(AgentLedgerService.ExecutionMaterial material, AgentRunLease lease, ToolEventSink sink) {
        AdkToolTelemetryPrivacy.requireSafe();
        try {
            requireMaterial(material, lease); if (sink == null) throw unavailable();
            var visible = registry.visible(material.snapshot()); var supplied = List.copyOf(hostBindings.apply(material));
            if (supplied.size() != visible.size()) throw unavailable();
            var byReference = new HashMap<DefinitionResourceRef, ToolExecutionGrant.BindingVO>();
            for (var binding : supplied) {
                if (!binding.run().equals(material.authorization()) || byReference.putIfAbsent(binding.tool(), binding) != null) throw unavailable();
            }
            var selected = new IdentityHashMap<BaseTool, TargetVO>(); var declarations = new ArrayList<BaseTool>();
            for (var descriptor : visible.values()) {
                var binding = byReference.remove(descriptor.reference());
                if (descriptor.policy().risk() != ToolPolicy.Risk.READ_ONLY || binding == null || !descriptor.policy().bindingSha256().equals(binding.backendBindingSha256())) throw unavailable();
                var declaration = new DeclarationTool(descriptor); declarations.add(declaration); selected.put(declaration, new TargetVO(descriptor, binding));
            }
            if (!byReference.isEmpty()) throw unavailable();
            return new RunScope(recorder, transport, lease, sink, declarations, selected);
        } catch (RuntimeException privateBinding) { throw dependency(); }
    }

    private static void requireMaterial(AgentLedgerService.ExecutionMaterial material, AgentRunLease lease) {
        if (material == null || material.run() == null || material.authorization() == null || material.snapshot() == null || lease == null) throw unavailable();
        var run = material.authorization(); var stored = material.run(); var snapshot = material.snapshot(); var definition = snapshot.definition();
        if ("LEGACY".equals(run.actor().issuerFingerprint()) || !"CONFIG".equals(definition.executionMode()) || !run.runId().equals(lease.runId()) || lease.epoch() < 1
                || !text(lease.owner(), 256) || !run.runId().equals(stored.runId()) || !run.definitionKey().equals(stored.definitionKey()) || run.definitionVersion() != stored.definitionVersion()
                || run.sessionRequired() != (stored.sessionId() != null) || !run.deadlineAt().equals(stored.deadlineAt())
                || !run.definitionKey().equals(definition.definitionKey()) || run.definitionVersion() != definition.version()
                || !run.definitionSha256().equals(snapshot.definitionContentSha256()) || !run.policyVersionRef().equals(definition.dependencies().policyVersionRef())) throw unavailable();
    }

    @FunctionalInterface interface ToolEventSink { void append(ToolEventVO event); }
    enum Phase { STARTED, FINISHED }
    record ToolEventVO(Phase phase, String author, DefinitionResourceRef toolRef, UUID hostInvocationId, String state, long dispatchEpoch,
                       ToolInvocationRecorder.AccountingState accountingState, String errorCode) {
        ToolEventVO {
            if (phase == null || !text(author, 128) || toolRef == null || hostInvocationId == null || state == null || dispatchEpoch < 0
                    || errorCode != null && !errorCode.matches("[A-Z][A-Z0-9_]{0,63}")) throw invalid();
            if (phase == Phase.STARTED) {
                if (!"REQUESTED".equals(state) || dispatchEpoch != 0 || accountingState != null || errorCode != null) throw invalid();
            } else if ("UNAVAILABLE".equals(state)) {
                if (dispatchEpoch != 0 || accountingState != null || errorCode == null) throw invalid();
            } else if (!Set.of("PREPARED", "DISPATCHING", "SUCCEEDED", "REJECTED", "FAILED_KNOWN", "UNKNOWN").contains(state) || accountingState == null) throw invalid();
        }
        @Override public String toString() { return "AdkReadOnlyDecisionToolBridge.ToolEventVO[redacted]"; }
    }
    static final class RunScope implements AutoCloseable {
        private final ToolInvocationRecorder recorder;
        private final ReadOnlyDecisionToolTransport transport;
        private final AgentRunLease lease;
        private final ToolEventSink sink;
        private final List<BaseTool> declarations;
        private final Map<BaseTool, TargetVO> selected;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RunScope(ToolInvocationRecorder recorder, ReadOnlyDecisionToolTransport transport, AgentRunLease lease, ToolEventSink sink,
                         List<BaseTool> declarations, IdentityHashMap<BaseTool, TargetVO> selected) {
            this.recorder = recorder; this.transport = transport; this.lease = lease; this.sink = sink;
            this.declarations = List.copyOf(declarations); this.selected = Collections.unmodifiableMap(new IdentityHashMap<>(selected));
        }
        List<BaseTool> declaredTools() { return declarations; }
        Map<String, Object> execute(BaseTool tool, Map<String, Object> arguments, UUID hostInvocationId, String author) {
            requireOpen(); AdkToolTelemetryPrivacy.requireSafe(); var target = selected.get(tool);
            if (target == null) throw unavailable();
            ToolInvocationStore.PreparationRequestVO request; ToolEventVO started;
            try {
                request = new ToolInvocationStore.PreparationRequestVO(target.binding(), lease, target.descriptor(), arguments, null, hostInvocationId, BigDecimal.ZERO, null);
                started = new ToolEventVO(Phase.STARTED, author, target.descriptor().reference(), hostInvocationId, "REQUESTED", 0, null, null);
            } catch (RuntimeException malformed) { throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND, "Governed tool input is invalid"); }
            append(started);
            ToolInvocationRecorder.RecordResult result; ToolExecutionOutcome outcome;
            try {
                requireOpen(); result = recorder.executeReadOnly(transport, request);
                if (result == null || !target.binding().run().runId().equals(result.runId()) || !hostInvocationId.equals(result.invocationId())) throw dependency();
                outcome = checkedOutcome(result, target.descriptor());
            } catch (RuntimeException privateFailure) {
                var safe = safe(privateFailure);
                append(new ToolEventVO(Phase.FINISHED, author, target.descriptor().reference(), hostInvocationId, "UNAVAILABLE", 0, null, safe.code().name())); throw safe;
            }
            append(new ToolEventVO(Phase.FINISHED, author, target.descriptor().reference(), hostInvocationId, result.state(), result.dispatchEpoch(), result.accountingState(), outcome == null ? null : outcome.errorCode()));
            requireOpen();
            if (result.accountingState() != ToolInvocationRecorder.AccountingState.SETTLED) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_USAGE_UNKNOWN, "Governed tool accounting is pending");
            if (outcome == null) throw dependency();
            if ("SUCCEEDED".equals(result.state())) return Map.of("status", "SUCCEEDED", "output", outcome.output());
            if (Set.of("REJECTED", "FAILED_KNOWN").contains(result.state())) return Map.of("status", result.state(), "errorCode", outcome.errorCode());
            throw dependency();
        }
        private static ToolExecutionOutcome checkedOutcome(ToolInvocationRecorder.RecordResult result, ToolDescriptor descriptor) {
            var outcome = result.outcome(); if (outcome == null) return null;
            if (!outcome.status().name().equals(result.state()) || outcome.businessReceiptId() != null || outcome.actualCost() == null || outcome.actualCost().signum() != 0) throw dependency();
            return new ToolExecutionOutcome(outcome.status(), outcome.output(), outcome.businessReceiptId(), outcome.errorCode(), outcome.actualCost()).validated(descriptor);
        }
        private void append(ToolEventVO event) { try { sink.append(event); } catch (RuntimeException privateFailure) { throw dependency(); } }
        private void requireOpen() { if (closed.get()) throw unavailable(); }
        String sideEffectOutcome() { return declarations.isEmpty() ? "UNKNOWN" : "NONE"; }
        @Override public void close() { closed.set(true); }
    }
    private record TargetVO(ToolDescriptor descriptor, ToolExecutionGrant.BindingVO binding) { }
    private static final class DeclarationTool extends BaseTool {
        private final FunctionDeclaration declaration;
        private DeclarationTool(ToolDescriptor descriptor) {
            super(descriptor.name(), descriptor.description());
            var properties = new TreeMap<String, Object>(); descriptor.input().fields().forEach((key, value) -> properties.put(key, Map.of("type", value.name().toLowerCase(Locale.ROOT))));
            var schema = new LinkedHashMap<String, Object>(); schema.put("type", "object"); schema.put("properties", Collections.unmodifiableMap(properties));
            schema.put("required", List.copyOf(new TreeSet<>(descriptor.input().required()))); schema.put("additionalProperties", false);
            declaration = FunctionDeclaration.builder().name(name()).description(description()).parametersJsonSchema(Collections.unmodifiableMap(schema)).build();
        }
        @Override public Optional<FunctionDeclaration> declaration() { return Optional.of(declaration); }
        @Override public Single<Map<String, Object>> runAsync(Map<String, Object> arguments, ToolContext context) { return Single.error(unavailable()); }
    }
    private static boolean text(String value, int maximum) {
        return value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= maximum && value.chars().noneMatch(Character::isISOControl) && StandardCharsets.UTF_8.newEncoder().canEncode(value);
    }
    private static AgentLedgerException safe(RuntimeException failure) { return failure instanceof AgentLedgerException scoped ? new AgentLedgerException(scoped.code(), "Governed tool execution could not complete") : dependency(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Governed read-only tool metadata is invalid"); }
    private static AgentLedgerException dependency() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Governed read-only tool execution is unavailable or changed"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Governed read-only tools are unavailable"); }
}
