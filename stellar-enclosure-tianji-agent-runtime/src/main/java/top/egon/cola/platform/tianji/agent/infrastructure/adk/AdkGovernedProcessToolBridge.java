package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.tools.BaseTool;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyMcpToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolRegistry;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessRecorder;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Programmatic union of governed Decision, MCP and explicit idempotent or approved BPMN START tools. */
public final class AdkGovernedProcessToolBridge implements AdkGovernedToolBridge {
    private static final Duration MAX_INTENT_TTL = Duration.ofDays(7);
    private final AgentLedgerService ledger;
    private final ToolInvocationRecorder recorder;
    private final ReadOnlyDecisionToolTransport decisionTransport;
    private final ReadOnlyMcpToolTransport mcpTransport;
    private final FixedBpmnStartToolTransport startTransport;
    private final ToolResultAccessRecorder resultAccessRecorder;
    private final ToolRegistry registry;
    private final Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>> hostBindings;
    private final StartActionContextResolver actionContexts;
    private final FixedToolApprovalVerifier approvalVerifier;
    private final ApprovalReceiptResolver approvalReceipts;

    public AdkGovernedProcessToolBridge(AgentLedgerService ledger, ToolInvocationRecorder recorder,
                                        ReadOnlyDecisionToolTransport decisionTransport,
                                        FixedBpmnStartToolTransport startTransport,
                                        ToolResultAccessRecorder resultAccessRecorder,
                                        Collection<ToolDescriptor> descriptors,
                                        Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>> hostBindings,
                                        StartActionContextResolver actionContexts) {
        this(ledger, recorder, decisionTransport, null, startTransport, resultAccessRecorder, descriptors, hostBindings, actionContexts, null, null, false);
    }

    private AdkGovernedProcessToolBridge(AgentLedgerService ledger, ToolInvocationRecorder recorder,
                                         ReadOnlyDecisionToolTransport decisionTransport, ReadOnlyMcpToolTransport mcpTransport, FixedBpmnStartToolTransport startTransport,
                                         ToolResultAccessRecorder resultAccessRecorder, Collection<ToolDescriptor> descriptors,
                                         Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>> hostBindings,
                                         StartActionContextResolver actionContexts, FixedToolApprovalVerifier approvalVerifier,
                                         ApprovalReceiptResolver approvalReceipts, boolean approved) {
        if (ledger == null || recorder == null || decisionTransport == null || startTransport == null || resultAccessRecorder == null
                || descriptors == null || hostBindings == null || actionContexts == null || !recorder.usesLedger(ledger)
                || !resultAccessRecorder.usesLedger(ledger) || approved && (approvalVerifier == null || approvalReceipts == null)) throw invalid();
        AdkToolTelemetryPrivacy.requireSafe();
        this.ledger = ledger; this.recorder = recorder; this.decisionTransport = decisionTransport; this.mcpTransport = mcpTransport; this.startTransport = startTransport;
        this.resultAccessRecorder = resultAccessRecorder; this.registry = new ToolRegistry(new ArrayList<>(descriptors));
        this.hostBindings = hostBindings; this.actionContexts = actionContexts;
        this.approvalVerifier = approvalVerifier; this.approvalReceipts = approvalReceipts;
    }

    public AdkGovernedProcessToolBridge(AgentLedgerService ledger, ToolInvocationRecorder recorder,
                                        ReadOnlyDecisionToolTransport decisionTransport,
                                        FixedBpmnStartToolTransport startTransport,
                                        ToolResultAccessRecorder resultAccessRecorder,
                                        Collection<ToolDescriptor> descriptors,
                                        Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>> hostBindings,
                                        StartActionContextResolver actionContexts,
                                        FixedToolApprovalVerifier approvalVerifier,
                                        ApprovalReceiptResolver approvalReceipts) {
        this(ledger, recorder, decisionTransport, null, startTransport, resultAccessRecorder, descriptors, hostBindings, actionContexts, approvalVerifier, approvalReceipts, true);
    }

    public AdkGovernedProcessToolBridge(AgentLedgerService ledger, ToolInvocationRecorder recorder,
                                        ReadOnlyDecisionToolTransport decisionTransport, ReadOnlyMcpToolTransport mcpTransport,
                                        FixedBpmnStartToolTransport startTransport, ToolResultAccessRecorder resultAccessRecorder,
                                        Collection<ToolDescriptor> descriptors,
                                        Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>> hostBindings,
                                        StartActionContextResolver actionContexts) {
        this(ledger, recorder, decisionTransport, requireMcp(mcpTransport), startTransport, resultAccessRecorder, descriptors, hostBindings, actionContexts, null, null, false);
    }

    public AdkGovernedProcessToolBridge(AgentLedgerService ledger, ToolInvocationRecorder recorder,
                                        ReadOnlyDecisionToolTransport decisionTransport, ReadOnlyMcpToolTransport mcpTransport,
                                        FixedBpmnStartToolTransport startTransport, ToolResultAccessRecorder resultAccessRecorder,
                                        Collection<ToolDescriptor> descriptors,
                                        Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>> hostBindings,
                                        StartActionContextResolver actionContexts, FixedToolApprovalVerifier approvalVerifier,
                                        ApprovalReceiptResolver approvalReceipts) {
        this(ledger, recorder, decisionTransport, requireMcp(mcpTransport), startTransport, resultAccessRecorder, descriptors, hostBindings, actionContexts, approvalVerifier, approvalReceipts, true);
    }

    public boolean usesMcpTransport(ReadOnlyMcpToolTransport candidate) { return candidate != null && mcpTransport == candidate; }

    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate; }
    public boolean usesRecorder(ToolInvocationRecorder candidate) { return recorder == candidate; }
    public boolean usesDecisionTransport(ReadOnlyDecisionToolTransport candidate) { return decisionTransport == candidate; }
    public boolean usesStartTransport(FixedBpmnStartToolTransport candidate) { return startTransport == candidate; }
    public boolean usesResultAccessRecorder(ToolResultAccessRecorder candidate) { return resultAccessRecorder == candidate; }
    public boolean usesActionContextResolver(StartActionContextResolver candidate) { return actionContexts == candidate; }
    public boolean usesApprovalVerifier(FixedToolApprovalVerifier candidate) { return candidate != null && approvalVerifier == candidate; }
    public boolean usesApprovalReceiptResolver(ApprovalReceiptResolver candidate) { return candidate != null && approvalReceipts == candidate; }

    @Override public Scope bindScope(AgentLedgerService.ExecutionMaterial material, AgentRunLease lease, ToolEventSink sink) {
        AdkToolTelemetryPrivacy.requireSafe();
        try {
            AdkReadOnlyDecisionToolBridge.requireMaterial(material, lease);
            if (sink == null) throw unavailable();
            var visible = registry.visible(material.snapshot()); var supplied = List.copyOf(hostBindings.apply(material));
            if (visible.size() != supplied.size()) throw unavailable();
            var bindings = new HashMap<DefinitionResourceRef, ToolExecutionGrant.BindingVO>();
            for (var binding : supplied) if (binding == null || !binding.run().equals(material.authorization())
                    || bindings.putIfAbsent(binding.tool(), binding) != null) throw unavailable();
            var readDescriptors = new ArrayList<ToolDescriptor>(); var readBindings = new ArrayList<ToolExecutionGrant.BindingVO>();
            var startInputs = new ArrayList<StartInputVO>(); String businessKey = material.snapshot().businessKey();
            for (var descriptor : visible.values()) {
                var binding = bindings.remove(descriptor.reference());
                if (binding == null || !descriptor.policy().bindingSha256().equals(binding.backendBindingSha256())) throw unavailable();
                if (descriptor.policy().risk() == ToolPolicy.Risk.READ_ONLY) { readDescriptors.add(descriptor); readBindings.add(binding); continue; }
                startProfile(descriptor, approvalVerifier != null); businessKey = text(businessKey, 255);
                var context = actionContexts.resolve(new StartActionContextRequestVO(material.authorization(), material.snapshot(), descriptor));
                if (context == null) throw unavailable(); startInputs.add(new StartInputVO(descriptor, binding, context));
            }
            if (!bindings.isEmpty()) throw unavailable();
            Instant databaseNow = startInputs.isEmpty() ? null : Objects.requireNonNull(ledger.databaseNow(), "databaseNow");
            var starts = new ArrayList<StartTargetVO>();
            for (var input : startInputs) {
                Instant expiry = databaseNow.plus(input.context().intentTtl()).truncatedTo(ChronoUnit.MICROS);
                if (!expiry.isAfter(databaseNow) || expiry.isAfter(databaseNow.plus(MAX_INTENT_TTL))) throw unavailable();
                starts.add(new StartTargetVO(input.descriptor(), input.binding(), businessKey, input.context().businessVersion(), expiry));
            }
            var read = AdkReadOnlyDecisionToolBridge.bindResolved(recorder, decisionTransport, mcpTransport, material, lease,
                    event -> sink.append(AdkReadOnlyDecisionToolBridge.toGovernedEvent(event)), List.copyOf(readDescriptors), List.copyOf(readBindings));
            return new RunScope(recorder, startTransport, resultAccessRecorder, lease, sink, read, starts, List.copyOf(visible.keySet()), approvalVerifier, approvalReceipts, material.snapshot());
        } catch (AgentLedgerException scoped) { throw safe(scoped); }
        catch (RuntimeException privateBinding) { throw dependency(); }
    }

    @FunctionalInterface public interface StartActionContextResolver { StartActionContextVO resolve(StartActionContextRequestVO request); }
    /** Synchronous host metadata selection only; the returned reference is not authenticated approval or I/O authority. */
    @FunctionalInterface public interface ApprovalReceiptResolver { String resolve(ApprovalReceiptRequestVO request); }

    public record ApprovalReceiptRequestVO(AgentRunAuthorization.Request run, AgentExecutionSnapshot snapshot, ToolDescriptor descriptor, ToolActionRequest action) {
        public ApprovalReceiptRequestVO {
            try {
                if (run == null || snapshot == null || descriptor == null || action == null || descriptor.policy().risk() != ToolPolicy.Risk.APPROVAL_REQUIRED
                        || !run.definitionKey().equals(snapshot.definition().definitionKey()) || run.definitionVersion() != snapshot.definition().version()
                        || !run.definitionSha256().equals(snapshot.definitionContentSha256()) || !run.policyVersionRef().equals(snapshot.definition().dependencies().policyVersionRef())
                        || !snapshot.definition().dependencies().tools().contains(descriptor.reference()) || !snapshot.effectivePolicy().allowedToolVersions().contains(descriptor.versionKey())
                        || !Objects.equals(descriptor.policy().approvalPolicyRef(), snapshot.effectivePolicy().approvalPolicyRef())
                        || !Objects.equals(descriptor.policy().approvalPolicyRef(), snapshot.definition().policy().approvalPolicyRef())) throw invalid();
                startProfile(descriptor, true);
                var rebuilt = new ToolActionRequest(run.actor(), descriptor, action.businessKey(), action.businessVersion(), action.arguments());
                if (!run.actor().equals(action.owner()) || !descriptor.reference().equals(action.tool()) || !descriptor.policy().equals(action.policy())
                        || !rebuilt.arguments().equals(action.arguments()) || !rebuilt.intentId().equals(action.intentId()) || !rebuilt.requestSha256().equals(action.requestSha256())) throw invalid();
            } catch (RuntimeException malformed) { throw invalid(); }
        }
        @Override public String toString() { return "AdkGovernedProcessToolBridge.ApprovalReceiptRequestVO[redacted]"; }
    }

    public record StartActionContextRequestVO(AgentRunAuthorization.Request run, AgentExecutionSnapshot snapshot, ToolDescriptor descriptor) {
        public StartActionContextRequestVO {
            if (run == null || snapshot == null || descriptor == null || !run.definitionKey().equals(snapshot.definition().definitionKey())
                    || run.definitionVersion() != snapshot.definition().version() || !run.definitionSha256().equals(snapshot.definitionContentSha256())) throw invalid();
        }
        @Override public String toString() { return "AdkGovernedProcessToolBridge.StartActionContextRequestVO[redacted]"; }
    }

    public record StartActionContextVO(String businessVersion, Duration intentTtl) {
        public StartActionContextVO {
            businessVersion = text(businessVersion, 128);
            if (intentTtl == null || intentTtl.isZero() || intentTtl.isNegative() || intentTtl.compareTo(MAX_INTENT_TTL) > 0) throw invalid();
        }
        @Override public String toString() { return "AdkGovernedProcessToolBridge.StartActionContextVO[redacted]"; }
    }

    private static final class RunScope implements AdkGovernedToolBridge.Scope {
        private final ToolInvocationRecorder recorder;
        private final FixedBpmnStartToolTransport transport;
        private final ToolResultAccessRecorder resultAccess;
        private final AgentRunLease lease;
        private final ToolEventSink sink;
        private final AdkReadOnlyDecisionToolBridge.RunScope read;
        private final FixedToolApprovalVerifier approvals;
        private final ApprovalReceiptResolver approvalReceipts;
        private final AgentExecutionSnapshot snapshot;
        private final List<BaseTool> declarations;
        private final Map<BaseTool, StartTargetVO> starts;
        private final Object scopeLock = new Object();
        private final Map<UUID, String> effects = new HashMap<>();
        private boolean closed;
        private String frozenEffect;

        RunScope(ToolInvocationRecorder recorder, FixedBpmnStartToolTransport transport, ToolResultAccessRecorder resultAccess,
                 AgentRunLease lease, ToolEventSink sink, AdkReadOnlyDecisionToolBridge.RunScope read, List<StartTargetVO> targets, List<String> orderedNames,
                 FixedToolApprovalVerifier approvals, ApprovalReceiptResolver approvalReceipts, AgentExecutionSnapshot snapshot) {
            this.recorder = recorder; this.transport = transport; this.resultAccess = resultAccess; this.lease = lease; this.sink = sink; this.read = read;
            this.approvals = approvals; this.approvalReceipts = approvalReceipts; this.snapshot = snapshot;
            var byName = new HashMap<String, BaseTool>(); var selected = new IdentityHashMap<BaseTool, StartTargetVO>();
            for (var declaration : read.declaredTools()) byName.put(declaration.name(), declaration);
            for (var target : targets) { var declaration = new AdkGovernedToolDeclaration(target.descriptor()); byName.put(declaration.name(), declaration); selected.put(declaration, target); }
            var declarations = new ArrayList<BaseTool>(); for (var name : orderedNames) declarations.add(Objects.requireNonNull(byName.remove(name), "declaration"));
            if (!byName.isEmpty()) throw dependency();
            this.declarations = List.copyOf(declarations); this.starts = Collections.unmodifiableMap(new IdentityHashMap<>(selected));
        }

        @Override public List<BaseTool> declaredTools() { return declarations; }
        @Override public Map<String, Object> execute(BaseTool tool, Map<String, Object> arguments, UUID id, String author, Runnable callFence) {
            if (callFence == null) throw dependency(); requireOpen(); AdkToolTelemetryPrivacy.requireSafe();
            if (read.declaredTools().stream().anyMatch(candidate -> candidate == tool)) return read.execute(tool, arguments, id, author, callFence);
            var target = starts.get(tool); if (target == null) throw unavailable();
            ToolActionRequest action; ToolInvocationStore.PreparationRequestVO request; ToolEventVO started;
            try {
                action = new ToolActionRequest(target.binding().run().actor(), target.descriptor(), target.businessKey(), target.businessVersion(), arguments);
                request = new ToolInvocationStore.PreparationRequestVO(target.binding(), lease, target.descriptor(), action.arguments(), action, id, BigDecimal.ZERO, target.intentExpiry());
                started = new ToolEventVO(Phase.STARTED, author, target.descriptor().reference(), id, "REQUESTED", 0L, null, null);
            } catch (RuntimeException malformed) { throw invalidCommand(); }
            append(started); markEntered(id);
            var heldFence = new AtomicReference<AgentLedgerException>(); boolean[] finished = {false};
            try {
                FinishedVO value;
                if (target.descriptor().policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED) value = approved(request, id, callFence, heldFence);
                else {
                    var result = recorder.executeIdempotentStart(guardedTransport(callFence, heldFence), request, status -> resolve(id, status, false));
                    if (heldFence.get() != null) throw heldFence.get();
                    if (result == null || !action.intentId().equals(result.actionIntentId())) throw dependency();
                    if (result.ownAttempt() == null) { requireOpen(); value = access(request, id); }
                    else value = own(request, result.ownAttempt());
                }
                requireOpen(); finished[0] = true; append(value.event(author, target.descriptor().reference(), id));
                requireOpen();
                if ("AWAITING_APPROVAL".equals(value.state())) return Map.of("status", "AWAITING_APPROVAL");
                return model(value.outcome(), value.accounting(), value.fresh());
            } catch (RuntimeException privateFailure) {
                var safe = heldFence.get() == null ? safe(privateFailure) : heldFence.get();
                if (!finished[0]) {
                    try { appendUnavailableIfOpen(author, target.descriptor().reference(), id, safe); }
                    catch (RuntimeException eventFailure) { if (heldFence.get() != null) throw heldFence.get(); throw eventFailure; }
                }
                throw safe;
            }
        }

        private FinishedVO approved(ToolInvocationStore.PreparationRequestVO request, UUID id, Runnable callFence, AtomicReference<AgentLedgerException> held) {
            if (approvals == null || approvalReceipts == null) throw unavailable();
            requireOpen(); selectionFence(callFence, held);
            String receipt = approvalReceipts.resolve(new ApprovalReceiptRequestVO(request.binding().run(), snapshot, request.descriptor(), request.action()));
            if (receipt != null) receipt = text(receipt, 255);
            selectionFence(callFence, held); requireOpen();
            var result = recorder.executeApprovedStart(approvals, guardedTransport(callFence, held), request, receipt, status -> resolve(id, status, true));
            if (held.get() != null) throw held.get();
            if (result == null || !request.action().intentId().equals(result.actionIntentId())) throw dependency();
            if (result.ownAttempt() != null) return own(request, result.ownAttempt());
            return switch (result.actionStateObserved()) {
                case "AWAITING_APPROVAL" -> new FinishedVO("AWAITING_APPROVAL", 0L, ToolInvocationRecorder.AccountingState.PENDING, null, false);
                case "APPLIED", "REJECTED", "FAILED_KNOWN" -> { requireOpen(); yield access(request, id); }
                default -> throw dependency();
            };
        }

        private void selectionFence(Runnable callFence, AtomicReference<AgentLedgerException> held) {
            try { callFence.run(); }
            catch (RuntimeException privateFence) { held.compareAndSet(null, safe(privateFence)); throw held.get(); }
        }

        private FixedBpmnStartToolTransport guardedTransport(Runnable callFence, AtomicReference<AgentLedgerException> held) {
            return new FixedBpmnStartToolTransport() {
                @Override public <T> T withVerifiedCall(Request request, Callback<T> callback) {
                    return transport.withVerifiedCall(request, (executor, operation) -> callback.apply(executor, new OneShotStart() {
                        @Override public StartObservation start() { return execute(false, null); }
                        @Override public StartObservation start(Instant bound) { return execute(true, bound); }
                        private StartObservation execute(boolean bounded, Instant bound) {
                            try { callFence.run(); }
                            catch (RuntimeException privateFence) { held.compareAndSet(null, safe(privateFence)); throw new Failure(FailureKind.OPERATION_NOT_SENT); }
                            synchronized (scopeLock) { if (closed) { held.compareAndSet(null, unavailable()); throw new Failure(FailureKind.OPERATION_NOT_SENT); } }
                            return bounded ? operation.start(bound) : operation.start();
                        }
                    }));
                }
            };
        }

        private FinishedVO own(ToolInvocationStore.PreparationRequestVO request, ToolInvocationRecorder.WriteAttemptResultVO own) {
            if (!request.binding().run().runId().equals(own.runId()) || !request.invocationId().equals(own.invocationId())) throw dependency();
            if (own.outcome() == null) return new FinishedVO(own.state(), own.dispatchEpoch(), own.accountingState(), null, true);
            var outcome = own.outcome().validated(request.descriptor());
            if (!own.state().equals(outcome.status().name()) || outcome.actualCost() == null || outcome.actualCost().signum() != 0
                    || outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED && !own.operationInvokedThisCall()) throw dependency();
            startOutcome(outcome); return new FinishedVO(own.state(), own.dispatchEpoch(), own.accountingState(), outcome, true);
        }

        private FinishedVO access(ToolInvocationStore.PreparationRequestVO request, UUID id) {
            var accessRequest = new ToolResultAccessStore.StartResultAccessRequestVO(request.binding(), request.lease(), request.descriptor(), request.action(), id);
            var result = resultAccess.recordKnownStartResultAccess(accessRequest, status -> resolve(id, status, false));
            if (result == null || !request.binding().run().runId().equals(result.runId()) || !id.equals(result.accessId())
                    || !request.action().intentId().equals(result.actionIntentId()) || result.sourceActionRevision() < 1
                    || result.sourceResultSha256() == null || !result.sourceResultSha256().matches("[0-9a-f]{64}")) throw dependency();
            var outcome = result.sourceOutcome().validated(request.descriptor()); startOutcome(outcome);
            return new FinishedVO(outcome.status().name(), null, result.accessAccountingState(), outcome, false);
        }

        private Map<String, Object> model(ToolExecutionOutcome outcome, ToolInvocationRecorder.AccountingState accounting, boolean fresh) {
            if (accounting != ToolInvocationRecorder.AccountingState.SETTLED) throw usageUnknown();
            if (outcome == null) throw dependency();
            if (fresh && (outcome.actualCost() == null || outcome.actualCost().signum() != 0)) throw dependency();
            return switch (outcome.status()) {
                case SUCCEEDED -> Map.of("status", "SUCCEEDED", "output", outcome.output());
                case REJECTED, FAILED_KNOWN -> Map.of("status", outcome.status().name(), "errorCode", outcome.errorCode());
                default -> throw dependency();
            };
        }

        private void markEntered(UUID id) { synchronized (scopeLock) { if (closed || id == null || effects.putIfAbsent(id, "UNKNOWN") != null) throw unavailable(); } }
        private void resolve(UUID id, ToolExecutionOutcome.Status status, boolean allowWait) {
            if (!allowWait && status == ToolExecutionOutcome.Status.AWAITING_APPROVAL) throw dependency();
            String effect = switch (status) { case SUCCEEDED -> "CONFIRMED"; case REJECTED, FAILED_KNOWN, AWAITING_APPROVAL -> "NONE"; case UNKNOWN -> "UNKNOWN"; };
            synchronized (scopeLock) { if (effects.containsKey(id)) effects.put(id, effect); }
        }
        private String aggregate() {
            if (declarations.isEmpty()) return "UNKNOWN";
            if (effects.containsValue("UNKNOWN")) return "UNKNOWN";
            return effects.containsValue("CONFIRMED") ? "CONFIRMED" : "NONE";
        }
        @Override public String sideEffectOutcome() { synchronized (scopeLock) { return frozenEffect == null ? aggregate() : frozenEffect; } }
        @Override public void close() { synchronized (scopeLock) { if (!closed) { frozenEffect = aggregate(); closed = true; } } read.close(); }
        private void requireOpen() { synchronized (scopeLock) { if (closed) throw unavailable(); } }
        private void append(ToolEventVO event) { try { sink.append(event); } catch (RuntimeException privateEvent) { throw dependency(); } }
        private void appendUnavailableIfOpen(String author, DefinitionResourceRef tool, UUID id, AgentLedgerException safe) {
            synchronized (scopeLock) { if (closed) return; }
            append(new ToolEventVO(Phase.FINISHED, author, tool, id, "UNAVAILABLE", null, null, safe.code().name()));
        }
    }

    private record StartInputVO(ToolDescriptor descriptor, ToolExecutionGrant.BindingVO binding, StartActionContextVO context) { }
    private record StartTargetVO(ToolDescriptor descriptor, ToolExecutionGrant.BindingVO binding, String businessKey, String businessVersion, Instant intentExpiry) { }
    private record FinishedVO(String state, Long epoch, ToolInvocationRecorder.AccountingState accounting, ToolExecutionOutcome outcome, boolean fresh) {
        ToolEventVO event(String author, DefinitionResourceRef tool, UUID id) { return new ToolEventVO(Phase.FINISHED, author, tool, id, state, epoch, accounting, outcome == null ? null : outcome.errorCode()); }
    }

    private static void startProfile(ToolDescriptor descriptor, boolean approved) {
        if (descriptor.policy().risk() != ToolPolicy.Risk.IDEMPOTENT_WRITE && !(approved && descriptor.policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED) || !ProcessClient.START_RESOURCE_KEY.equals(descriptor.policy().executionResourceKey())
                || !descriptor.output().fields().equals(Map.of("instanceId", ToolSchema.ValueType.STRING)) || !descriptor.output().required().equals(Set.of("instanceId"))) throw unavailable();
    }
    private static void startOutcome(ToolExecutionOutcome outcome) {
        if (outcome == null || !Set.of(ToolExecutionOutcome.Status.SUCCEEDED, ToolExecutionOutcome.Status.REJECTED,
                ToolExecutionOutcome.Status.FAILED_KNOWN, ToolExecutionOutcome.Status.UNKNOWN).contains(outcome.status())) throw dependency();
        if (outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED) {
            if (!outcome.output().keySet().equals(Set.of("instanceId")) || !(outcome.output().get("instanceId") instanceof String instance)) throw dependency();
            new FixedBpmnStartToolTransport.StartObservation(outcome.businessReceiptId(), instance);
        }
    }
    private static String text(String value, int maximum) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > maximum
                || value.chars().anyMatch(Character::isISOControl) || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw invalid();
        return value;
    }
    private static AgentLedgerException safe(RuntimeException failure) {
        return failure instanceof AgentLedgerException scoped ? new AgentLedgerException(scoped.code(), "Governed process tool execution could not complete") : dependency();
    }
    private static ReadOnlyMcpToolTransport requireMcp(ReadOnlyMcpToolTransport transport) { if (transport == null) throw invalid(); return transport; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Governed process tool metadata is invalid"); }
    private static AgentLedgerException invalidCommand() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND, "Governed process tool input is invalid"); }
    private static AgentLedgerException usageUnknown() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_USAGE_UNKNOWN, "Governed process tool accounting is pending"); }
    private static AgentLedgerException dependency() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Governed process tool execution is unavailable or changed"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Governed process tools are unavailable"); }
}
