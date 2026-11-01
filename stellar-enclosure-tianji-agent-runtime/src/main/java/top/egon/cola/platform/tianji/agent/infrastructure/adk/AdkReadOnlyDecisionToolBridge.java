package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.tools.BaseTool;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyMcpToolTransport;
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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/** Host-bound declarations whose direct native fallback never executes a transport. Initialize host telemetry before construction. */
public final class AdkReadOnlyDecisionToolBridge implements AdkGovernedToolBridge {
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
    @Override public AdkGovernedToolBridge.Scope bindScope(AgentLedgerService.ExecutionMaterial material, AgentRunLease lease, AdkGovernedToolBridge.ToolEventSink sink) {
        if (sink == null) throw dependency();
        return bind(material, lease, event -> sink.append(toGovernedEvent(event)));
    }
    static RunScope bindResolved(ToolInvocationRecorder recorder, ReadOnlyDecisionToolTransport transport, AgentLedgerService.ExecutionMaterial material,
                                 AgentRunLease lease, ToolEventSink sink, List<ToolDescriptor> subset, List<ToolExecutionGrant.BindingVO> bindings) {
        return bindResolved(recorder, transport, null, material, lease, sink, subset, bindings);
    }
    static RunScope bindResolved(ToolInvocationRecorder recorder, ReadOnlyDecisionToolTransport decisionTransport, ReadOnlyMcpToolTransport mcpTransport,
                                 AgentLedgerService.ExecutionMaterial material, AgentRunLease lease, ToolEventSink sink,
                                 List<ToolDescriptor> subset, List<ToolExecutionGrant.BindingVO> bindings) {
        AdkToolTelemetryPrivacy.requireSafe();
        try {
            requireMaterial(material, lease);
            if (recorder == null || decisionTransport == null || sink == null || subset == null || bindings == null || subset.size() > 32 || bindings.size() != subset.size()) throw unavailable();
            var visible = new TreeMap<String, ToolDescriptor>(); var versions = new HashSet<String>();
            for (var descriptor : List.copyOf(subset)) {
                if (descriptor.policy().risk() != ToolPolicy.Risk.READ_ONLY || material.snapshot().effectivePolicy().maxToolCalls() < 1
                        || !material.snapshot().effectivePolicy().allowedToolVersions().contains(descriptor.versionKey())
                        || !material.snapshot().definition().dependencies().tools().contains(descriptor.reference())
                        || isMcp(descriptor) && mcpTransport == null
                        || !versions.add(descriptor.versionKey()) || visible.putIfAbsent(descriptor.name(), descriptor) != null) throw unavailable();
            }
            var byReference = new HashMap<DefinitionResourceRef, ToolExecutionGrant.BindingVO>();
            for (var binding : List.copyOf(bindings)) {
                if (!binding.run().equals(material.authorization()) || byReference.putIfAbsent(binding.tool(), binding) != null) throw unavailable();
            }
            var selected = new IdentityHashMap<BaseTool, TargetVO>(); var declarations = new ArrayList<BaseTool>();
            for (var descriptor : visible.values()) {
                var binding = byReference.remove(descriptor.reference());
                if (binding == null || !descriptor.policy().bindingSha256().equals(binding.backendBindingSha256())) throw unavailable();
                var declaration = new AdkGovernedToolDeclaration(descriptor); declarations.add(declaration); selected.put(declaration, new TargetVO(descriptor, binding));
            }
            if (!byReference.isEmpty()) throw unavailable();
            return new RunScope(recorder, decisionTransport, mcpTransport, lease, sink, declarations, selected);
        } catch (RuntimeException privateBinding) { throw dependency(); }
    }
    static AdkGovernedToolBridge.ToolEventVO toGovernedEvent(ToolEventVO event) {
        if (event == null) throw dependency();
        return new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.valueOf(event.phase().name()), event.author(), event.toolRef(), event.hostInvocationId(),
                event.state(), event.dispatchEpoch(), event.accountingState(), event.errorCode());
    }
    RunScope bind(AgentLedgerService.ExecutionMaterial material, AgentRunLease lease, ToolEventSink sink) {
        AdkToolTelemetryPrivacy.requireSafe();
        try {
            requireMaterial(material, lease); if (sink == null) throw unavailable();
            var visible = registry.visible(material.snapshot()); var supplied = List.copyOf(hostBindings.apply(material));
            return bindResolved(recorder, transport, material, lease, sink, List.copyOf(visible.values()), supplied);
        } catch (RuntimeException privateBinding) { throw dependency(); }
    }

    static void requireMaterial(AgentLedgerService.ExecutionMaterial material, AgentRunLease lease) {
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
    static final class RunScope implements AdkGovernedToolBridge.Scope {
        private final ToolInvocationRecorder recorder;
        private final ReadOnlyDecisionToolTransport transport;
        private final ReadOnlyMcpToolTransport mcpTransport;
        private final AgentRunLease lease;
        private final ToolEventSink sink;
        private final List<BaseTool> declarations;
        private final Map<BaseTool, TargetVO> selected;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RunScope(ToolInvocationRecorder recorder, ReadOnlyDecisionToolTransport transport, ReadOnlyMcpToolTransport mcpTransport, AgentRunLease lease, ToolEventSink sink,
                         List<BaseTool> declarations, IdentityHashMap<BaseTool, TargetVO> selected) {
            this.recorder = recorder; this.transport = transport; this.mcpTransport = mcpTransport; this.lease = lease; this.sink = sink;
            this.declarations = List.copyOf(declarations); this.selected = Collections.unmodifiableMap(new IdentityHashMap<>(selected));
        }
        @Override public List<BaseTool> declaredTools() { return declarations; }
        @Override public Map<String, Object> execute(BaseTool tool, Map<String, Object> arguments, UUID hostInvocationId, String author, Runnable callFence) {
            if (callFence == null) throw dependency();
            try { callFence.run(); } catch (RuntimeException privateFence) { throw safe(privateFence); }
            return executeResolved(tool, arguments, hostInvocationId, author, callFence);
        }
        Map<String, Object> execute(BaseTool tool, Map<String, Object> arguments, UUID hostInvocationId, String author) {
            return executeResolved(tool, arguments, hostInvocationId, author, null);
        }
        private Map<String, Object> executeResolved(BaseTool tool, Map<String, Object> arguments, UUID hostInvocationId, String author, Runnable callFence) {
            requireOpen(); AdkToolTelemetryPrivacy.requireSafe(); var target = selected.get(tool);
            if (target == null) throw unavailable();
            boolean mcp = isMcp(target.descriptor());
            if (mcp && callFence == null) throw unavailable();
            ToolInvocationStore.PreparationRequestVO request; ToolEventVO started;
            try {
                request = new ToolInvocationStore.PreparationRequestVO(target.binding(), lease, target.descriptor(), arguments, null, hostInvocationId, BigDecimal.ZERO, null);
                started = new ToolEventVO(Phase.STARTED, author, target.descriptor().reference(), hostInvocationId, "REQUESTED", 0, null, null);
            } catch (RuntimeException malformed) { throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND, "Governed tool input is invalid"); }
            append(started);
            ToolInvocationRecorder.RecordResult result; ToolExecutionOutcome outcome;
            var heldFence = mcp ? new AtomicReference<AgentLedgerException>() : null;
            try {
                requireOpen();
                result = mcp ? recorder.executeReadOnlyMcp(guardedMcpTransport(callFence, heldFence), request) : recorder.executeReadOnly(transport, request);
                if (mcp && heldFence.get() != null) throw heldFence.get();
                if (result == null || !target.binding().run().runId().equals(result.runId()) || !hostInvocationId.equals(result.invocationId())) throw dependency();
                outcome = checkedOutcome(result, target.descriptor());
            } catch (RuntimeException privateFailure) {
                var safe = mcp && heldFence.get() != null ? heldFence.get() : safe(privateFailure);
                if (mcp) {
                    try { appendUnavailableIfOpen(author, target.descriptor().reference(), hostInvocationId, safe); }
                    catch (RuntimeException eventFailure) { if (heldFence.get() != null) throw heldFence.get(); throw eventFailure; }
                } else append(new ToolEventVO(Phase.FINISHED, author, target.descriptor().reference(), hostInvocationId, "UNAVAILABLE", 0, null, safe.code().name()));
                throw safe;
            }
            if (mcp) requireOpen();
            append(new ToolEventVO(Phase.FINISHED, author, target.descriptor().reference(), hostInvocationId, result.state(), result.dispatchEpoch(), result.accountingState(), outcome == null ? null : outcome.errorCode()));
            requireOpen();
            if (result.accountingState() != ToolInvocationRecorder.AccountingState.SETTLED) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_USAGE_UNKNOWN, "Governed tool accounting is pending");
            if (outcome == null) throw dependency();
            if ("SUCCEEDED".equals(result.state())) return Map.of("status", "SUCCEEDED", "output", outcome.output());
            if (Set.of("REJECTED", "FAILED_KNOWN").contains(result.state())) return Map.of("status", result.state(), "errorCode", outcome.errorCode());
            throw dependency();
        }
        private ReadOnlyMcpToolTransport guardedMcpTransport(Runnable callFence, AtomicReference<AgentLedgerException> heldFence) {
            return new ReadOnlyMcpToolTransport() {
                @Override public <T> T withVerifiedCall(Request request, Callback<T> callback) {
                    return mcpTransport.withVerifiedCall(request, (executor, operation) -> callback.apply(executor, operation == null ? null : bound -> {
                        try { callFence.run(); }
                        catch (RuntimeException privateFence) {
                            heldFence.compareAndSet(null, safe(privateFence)); throw new Failure(FailureKind.OPERATION_NOT_SENT);
                        }
                        if (closed.get()) {
                            heldFence.compareAndSet(null, unavailable()); throw new Failure(FailureKind.OPERATION_NOT_SENT);
                        }
                        return operation.call(bound);
                    }));
                }
            };
        }
        private static ToolExecutionOutcome checkedOutcome(ToolInvocationRecorder.RecordResult result, ToolDescriptor descriptor) {
            var outcome = result.outcome(); if (outcome == null) return null;
            if (!outcome.status().name().equals(result.state()) || outcome.businessReceiptId() != null || outcome.actualCost() == null || outcome.actualCost().signum() != 0) throw dependency();
            return new ToolExecutionOutcome(outcome.status(), outcome.output(), outcome.businessReceiptId(), outcome.errorCode(), outcome.actualCost()).validated(descriptor);
        }
        private void append(ToolEventVO event) { try { sink.append(event); } catch (RuntimeException privateFailure) { throw dependency(); } }
        private void appendUnavailableIfOpen(String author, DefinitionResourceRef tool, UUID id, AgentLedgerException safe) {
            if (closed.get()) return;
            append(new ToolEventVO(Phase.FINISHED, author, tool, id, "UNAVAILABLE", 0, null, safe.code().name()));
        }
        private void requireOpen() { if (closed.get()) throw unavailable(); }
        @Override public String sideEffectOutcome() { return declarations.isEmpty() ? "UNKNOWN" : "NONE"; }
        @Override public void close() { closed.set(true); }
    }
    private static boolean isMcp(ToolDescriptor descriptor) { return ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY.equals(descriptor.policy().executionResourceKey()); }
    private record TargetVO(ToolDescriptor descriptor, ToolExecutionGrant.BindingVO binding) { }
    private static boolean text(String value, int maximum) {
        return value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= maximum && value.chars().noneMatch(Character::isISOControl) && StandardCharsets.UTF_8.newEncoder().canEncode(value);
    }
    private static AgentLedgerException safe(RuntimeException failure) { return failure instanceof AgentLedgerException scoped ? new AgentLedgerException(scoped.code(), "Governed tool execution could not complete") : dependency(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Governed read-only tool metadata is invalid"); }
    private static AgentLedgerException dependency() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Governed read-only tool execution is unavailable or changed"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Governed read-only tools are unavailable"); }
}
