package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.AgentTransfer;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmResponse;
import com.google.adk.models.Model;
import com.google.adk.sessions.Session;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.ReplayProcessor;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;

final class NativeAgentGate implements AutoCloseable {
    private static final String TRANSFER_NAME = "transfer_to_agent";
    private static final String AGENT_NAME_ARGUMENT = "agent_name";
    private static final int MAX_CHILD_CHARS = 8_192;
    private static final int MAX_CHILD_BYTES = 32_768;
    private static final Method TRANSFER_METHOD = transferMethod();

    private final NativeAgentCompilerStrategy.TemplateSpec spec;
    private final LlmAgent root;
    private final Map<String, LlmAgent> agentsByName;
    private final Set<BaseAgent> agentIdentities;
    private final String topologyHash;
    private final AuthorizationAppender appender;
    private final IdentityHashMap<InvocationContext, PendingTransfer> pending = new IdentityHashMap<>();
    private final IdentityHashMap<InvocationContext, Map<String, ConfirmedTransfer>> confirmedCalls =
            new IdentityHashMap<>();
    private final Map<String, String> confirmedOutputs = new LinkedHashMap<>();
    private final Map<String, TransferTaskStateEnum> childStates = new LinkedHashMap<>();
    private final FlowableProcessor<NativeFailureToken> failureProcessor =
            ReplayProcessor.<NativeFailureToken>createWithSize(1).toSerialized();
    private BaseAgent expectedAgent;
    private String selectedConditionalChild;
    private int authorizedTransfers;
    private int completedTransitions;
    private NativeFailureToken fatal;
    private Session boundSession;
    private String boundInvocationId;
    private boolean complete;
    private boolean closed;

    @FunctionalInterface
    interface AuthorizationAppender {
        void append(String author, Map<String, Object> payload);
    }

    private NativeAgentGate(NativeAgentCompilerStrategy.TemplateSpec spec, LlmAgent root,
                            Map<String, LlmAgent> agentsByName, Set<BaseAgent> agentIdentities,
                            String topologyHash, AuthorizationAppender appender) {
        this.spec = spec;
        this.root = root;
        this.agentsByName = agentsByName;
        this.agentIdentities = agentIdentities;
        this.topologyHash = topologyHash;
        this.appender = appender;
        this.expectedAgent = root;
        spec.agents().stream().filter(agent -> agent.parentName() != null)
                .forEach(agent -> childStates.put(agent.name(), TransferTaskStateEnum.NEW));
    }

    static NativeAgentGate bind(NativeAgentCompilerStrategy.TemplateSpec spec, BaseAgent root,
                                String topologyHash, AuthorizationAppender appender) {
        if (spec == null || !(root instanceof LlmAgent rootLlm) || appender == null
                || topologyHash == null || !topologyHash.matches("[0-9a-f]{64}")) {
            throw invalid();
        }
        Map<String, NativeAgentCompilerStrategy.AgentSpec> expected = new LinkedHashMap<>();
        for (var agent : spec.agents()) {
            if (expected.putIfAbsent(agent.name(), agent) != null) throw invalid();
        }
        List<BaseAgent> actual = new ArrayList<>();
        actual.add(rootLlm);
        actual.addAll(rootLlm.subAgents());
        if (actual.size() != expected.size() || !rootLlm.name().equals(spec.rootAgentName())) throw invalid();

        BaseLlm rootModel = explicitModel(rootLlm);
        if (!rootModel.model().equals(spec.modelName())) throw invalid();
        Map<String, LlmAgent> byName = new LinkedHashMap<>();
        Set<BaseAgent> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BaseAgent candidate : actual) {
            if (!(candidate instanceof LlmAgent llm) || !identities.add(llm)
                    || byName.putIfAbsent(llm.name(), llm) != null
                    || llm != rootLlm && !llm.subAgents().isEmpty()) {
                throw invalid();
            }
            var expectedAgent = expected.get(llm.name());
            if (expectedAgent == null || !llm.outputKey().equals(Optional.of(expectedAgent.outputKey()))
                    || llm.disallowTransferToParent() != expectedAgent.disallowTransferToParent()
                    || llm.disallowTransferToPeers() != expectedAgent.disallowTransferToPeers()
                    || explicitModel(llm) != rootModel) {
                throw invalid();
            }
            if (expectedAgent.parentName() == null) {
                if (llm != rootLlm || llm.parentAgent() != null) throw invalid();
            } else if (llm.parentAgent() != rootLlm || !expectedAgent.parentName().equals(rootLlm.name())) {
                throw invalid();
            }
        }
        if (!byName.keySet().equals(expected.keySet())) throw invalid();
        return new NativeAgentGate(spec, rootLlm, Map.copyOf(byName), identities,
                topologyHash, appender);
    }

    synchronized void requireExpectedAgent(CallbackContext context) {
        requireOpen();
        InvocationContext invocation = rawContext(context);
        if (!agentIdentities.contains(invocation.agent()) || invocation.agent() != expectedAgent) {
            throw markPolicyFatal();
        }
        requireLineage(invocation);
    }

    synchronized void observeResponse(CallbackContext context, LlmResponse response) {
        requireExpectedAgent(context);
        if (response == null) throw markPolicyFatal();
        if (response.partial().orElse(false)) return;
        if (response.errorMessage().isPresent() || response.errorCode().isPresent()
                || response.interrupted().orElse(false)) {
            throw markPolicyFatal();
        }
        List<com.google.genai.types.Part> parts = response.content().flatMap(Content::parts).orElse(List.of());
        List<FunctionCall> calls = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (var part : parts) {
            part.functionCall().ifPresent(calls::add);
            if (!part.thought().orElse(false)) part.text().ifPresent(text::append);
        }
        if (calls.isEmpty()) return;
        if (calls.size() != 1 || !pending.isEmpty()) throw markPolicyFatal();
        FunctionCall call = calls.getFirst();
        if (!TRANSFER_NAME.equals(call.name().orElse(null))) throw markPolicyFatal();
        Map<String, Object> arguments = call.args().orElse(Map.of());
        if (arguments.size() != 1 || !(arguments.get(AGENT_NAME_ARGUMENT) instanceof String target)
                || !agentsByName.containsKey(target)) {
            throw markPolicyFatal();
        }
        InvocationContext invocation = rawContext(context);
        LlmAgent source = (LlmAgent) invocation.agent();
        validateTransition(source.name(), target, false);
        String childText;
        if (source == root) {
            if (!text.isEmpty()) throw markPolicyFatal();
            childText = null;
        } else {
            childText = requireChildText(text.toString());
        }
        Optional<String> providerCallId = normalizeProviderCallId(call.id());
        pending.put(invocation, new PendingTransfer(source.name(), target, Map.copyOf(arguments),
                providerCallId, null, childText, false));
    }

    synchronized void beforeTool(BaseTool tool, Map<String, Object> arguments, ToolContext context) {
        requireOpen();
        if (!isNativeTransfer(tool) || arguments == null || context == null) throw markPolicyFatal();
        InvocationContext invocation = rawContext(context);
        requireLineage(invocation);
        PendingTransfer transfer = pending.get(invocation);
        if (transfer == null || transfer.authorized() || invocation.agent() != expectedAgent
                || !transfer.source().equals(invocation.agent().name())
                || !transfer.arguments().equals(arguments)) {
            throw markPolicyFatal();
        }
        String actualCallId = requireCallId(context.functionCallId().orElse(null));
        if (transfer.providerCallId().isPresent()
                && !transfer.providerCallId().orElseThrow().equals(actualCallId)) {
            throw markPolicyFatal();
        }
        Map<String, ConfirmedTransfer> confirmed = confirmedCalls.get(invocation);
        if (confirmed != null && confirmed.containsKey(actualCallId)) throw markPolicyFatal();
        validateTransition(transfer.source(), transfer.target(), false);
        int ordinal = authorizedTransfers + 1;
        if (ordinal > spec.maxTransfers()) throw markPolicyFatal();
        Map<String, Object> payload = Map.of("to", transfer.target(), "ordinal", ordinal,
                "topologyHash", topologyHash, "state", "AUTHORIZED");
        try {
            appender.append(transfer.source(), payload);
        } catch (RuntimeException failure) {
            throw markFatal(failure);
        }
        authorizedTransfers = ordinal;
        markDispatched(transfer);
        pending.put(invocation, transfer.authorize(actualCallId));
    }

    synchronized void afterTool(BaseTool tool, Map<String, Object> arguments,
                                ToolContext context, Map<String, Object> result) {
        requireOpen();
        if (!isNativeTransfer(tool) || arguments == null || context == null || result == null) {
            throw markPolicyFatal();
        }
        InvocationContext invocation = rawContext(context);
        requireLineage(invocation);
        String callId = requireCallId(context.functionCallId().orElse(null));
        ConfirmedTransfer confirmed = confirmedCalls.getOrDefault(invocation, Map.of()).get(callId);
        String action = context.eventActions().transferToAgent().orElse(null);
        if (confirmed != null) {
            if (!confirmed.target().equals(action) || !confirmed.arguments().equals(arguments)
                    || !confirmed.result().equals(result)) {
                throw markPolicyFatal();
            }
            return;
        }
        PendingTransfer transfer = pending.get(invocation);
        if (transfer == null || !transfer.authorized() || !callId.equals(transfer.actualCallId())
                || invocation.agent() != expectedAgent || !transfer.arguments().equals(arguments)
                || !transfer.target().equals(action)) {
            throw markPolicyFatal();
        }
        validateTransition(transfer.source(), transfer.target(), true);
        completeTransition(transfer);
        confirmedCalls.computeIfAbsent(invocation, ignored -> new LinkedHashMap<>())
                .put(callId, new ConfirmedTransfer(transfer.target(), arguments, result));
        pending.remove(invocation);
    }

    synchronized RuntimeException onToolError(BaseTool tool, Map<String, Object> arguments,
                                              ToolContext context, Throwable failure) {
        if (context != null) pending.remove(context.invocationContext());
        return markFatal(failure);
    }

    synchronized void observeEvent(Event event, Optional<AdkEventProjector.Projection> projection) {
        requireOpen();
        if (event == null || projection == null) throw markPolicyFatal();
        if (!event.finalResponse()) return;
        if (boundInvocationId == null || !boundInvocationId.equals(event.invocationId())
                || !root.name().equals(event.author()) || expectedAgent != root || !pending.isEmpty()
                || !transitionComplete() || !event.functionCalls().isEmpty()) {
            throw markPolicyFatal();
        }
        var projected = projection.orElseThrow(this::markPolicyFatal);
        Object text = projected.payload().get("text");
        if (!projected.completeAgentOutput() || !"AGENT_OUTPUT".equals(projected.type())
                || !root.name().equals(projected.author()) || !(text instanceof String rootText)
                || rootText.isBlank()) {
            throw markPolicyFatal();
        }
        confirmedOutputs.put(root.name(), rootText);
        complete = true;
    }

    synchronized RuntimeException markFatal(Throwable failure) {
        if (fatal == null) {
            fatal = classify(failure);
            pending.clear();
            failureProcessor.onNext(fatal);
        }
        return safeBoundaryFailure(fatal);
    }

    synchronized RuntimeException markPolicyFatal() {
        if (fatal == null) {
            fatal = NativeFailureToken.policy();
            pending.clear();
            failureProcessor.onNext(fatal);
        }
        return safeBoundaryFailure(fatal);
    }

    synchronized boolean incomingAlreadySafe(Throwable failure) {
        if (fatal == null || failure == null) return false;
        if (fatal.kind() == NativeFailureKindEnum.AUTHORIZATION
                && failure instanceof AgentExecutionAuthorization.Failure authorization) {
            return authorization.decision() == fatal.decision();
        }
        if (failure instanceof NativeBoundaryException boundary) return boundary.token().equals(fatal);
        return failure instanceof NativeBoundaryTimeoutException timeout && timeout.token().equals(fatal);
    }

    boolean isNativeTransfer(BaseTool tool) {
        if (!(tool instanceof FunctionTool function) || !TRANSFER_METHOD.equals(function.func())
                || !TRANSFER_NAME.equals(function.name()) || function.isStreaming() || function.longRunning()) {
            return false;
        }
        var declaration = function.declaration().orElse(null);
        if (declaration == null || !TRANSFER_NAME.equals(declaration.name().orElse(null))
                || declaration.parametersJsonSchema().isPresent()) {
            return false;
        }
        var parameters = declaration.parameters().orElse(null);
        if (parameters == null || !"OBJECT".equals(parameters.type().map(Object::toString).orElse(null))
                || !parameters.required().orElse(List.of()).equals(List.of(AGENT_NAME_ARGUMENT))) {
            return false;
        }
        var properties = parameters.properties().orElse(Map.of());
        return properties.size() == 1 && properties.containsKey(AGENT_NAME_ARGUMENT)
                && "STRING".equals(properties.get(AGENT_NAME_ARGUMENT).type().map(Object::toString).orElse(null));
    }

    Flowable<Long> failureSignal() {
        return failureProcessor.take(1)
                .flatMap(token -> Flowable.<Long>error(safeBoundaryFailure(token)));
    }

    synchronized Map<String, String> requireCompletedOutputs() {
        if (fatal != null) throw safeBoundaryFailure(fatal);
        if (!complete || !transitionComplete() || !pending.isEmpty()) throw markPolicyFatal();
        return Collections.unmodifiableMap(new LinkedHashMap<>(confirmedOutputs));
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        pending.clear();
        failureProcessor.onComplete();
    }

    private synchronized void requireOpen() {
        if (fatal != null) throw safeBoundaryFailure(fatal);
        if (closed || complete) throw markPolicyFatal();
    }

    private InvocationContext rawContext(CallbackContext context) {
        if (context == null || context.invocationContext() == null) throw markPolicyFatal();
        return context.invocationContext();
    }

    private void requireLineage(InvocationContext invocation) {
        if (boundSession == null) {
            if (invocation.session() == null || invocation.invocationId() == null
                    || invocation.invocationId().isBlank()) {
                throw markPolicyFatal();
            }
            boundSession = invocation.session();
            boundInvocationId = invocation.invocationId();
        } else if (invocation.session() != boundSession
                || !boundInvocationId.equals(invocation.invocationId())) {
            throw markPolicyFatal();
        }
    }

    private void validateTransition(String source, String target, boolean afterAuthorization) {
        boolean valid = switch (spec.topology()) {
            case CONDITIONAL_RESEARCH_V1 -> switch (completedTransitions) {
                case 0 -> root.name().equals(source)
                        && ("quick_research".equals(target) || "deep_research".equals(target))
                        && childStates.get(target) == (afterAuthorization
                        ? TransferTaskStateEnum.DISPATCHED : TransferTaskStateEnum.NEW);
                case 1 -> selectedConditionalChild != null && selectedConditionalChild.equals(source)
                        && root.name().equals(target)
                        && childStates.get(source) == TransferTaskStateEnum.DISPATCHED;
                default -> false;
            };
            case SUPERVISOR_RESEARCH_V1 -> switch (completedTransitions) {
                case 0 -> root.name().equals(source) && "researcher".equals(target)
                        && childStates.get(target) == (afterAuthorization
                        ? TransferTaskStateEnum.DISPATCHED : TransferTaskStateEnum.NEW);
                case 1 -> "researcher".equals(source) && root.name().equals(target)
                        && childStates.get(source) == TransferTaskStateEnum.DISPATCHED;
                case 2 -> root.name().equals(source) && "critic".equals(target)
                        && childStates.get(target) == (afterAuthorization
                        ? TransferTaskStateEnum.DISPATCHED : TransferTaskStateEnum.NEW);
                case 3 -> "critic".equals(source) && root.name().equals(target)
                        && childStates.get(source) == TransferTaskStateEnum.DISPATCHED;
                default -> false;
            };
        };
        if (!valid || source.equals(target)) throw markPolicyFatal();
    }

    private void markDispatched(PendingTransfer transfer) {
        if (transfer.source().equals(root.name())) {
            if (childStates.replace(transfer.target(), TransferTaskStateEnum.NEW,
                    TransferTaskStateEnum.DISPATCHED) == false) {
                throw markPolicyFatal();
            }
            if (spec.topology() == NativeAgentCompilerStrategy.TransferTopologyEnum.CONDITIONAL_RESEARCH_V1) {
                selectedConditionalChild = transfer.target();
            }
        }
    }

    private void completeTransition(PendingTransfer transfer) {
        if (!transfer.source().equals(root.name())) {
            if (childStates.replace(transfer.source(), TransferTaskStateEnum.DISPATCHED,
                    TransferTaskStateEnum.RETURNED) == false || transfer.childText() == null
                    || confirmedOutputs.putIfAbsent(
                    transfer.source(), transfer.childText()) != null) {
                throw markPolicyFatal();
            }
        }
        completedTransitions++;
        expectedAgent = agentsByName.get(transfer.target());
        if (expectedAgent == null) throw markPolicyFatal();
    }

    private boolean transitionComplete() {
        Set<String> childOutputs = new HashSet<>(confirmedOutputs.keySet());
        childOutputs.remove(root.name());
        return switch (spec.topology()) {
            case CONDITIONAL_RESEARCH_V1 -> completedTransitions == 2
                    && selectedConditionalChild != null
                    && childStates.get(selectedConditionalChild) == TransferTaskStateEnum.RETURNED
                    && childStates.entrySet().stream().filter(entry -> !entry.getKey().equals(selectedConditionalChild))
                    .allMatch(entry -> entry.getValue() == TransferTaskStateEnum.NEW)
                    && childOutputs.equals(Set.of(selectedConditionalChild));
            case SUPERVISOR_RESEARCH_V1 -> completedTransitions == 4
                    && childStates.get("researcher") == TransferTaskStateEnum.RETURNED
                    && childStates.get("critic") == TransferTaskStateEnum.RETURNED
                    && childOutputs.equals(Set.of("researcher", "critic"));
        };
    }

    private static BaseLlm explicitModel(LlmAgent agent) {
        return agent.model().flatMap(Model::model).orElseThrow(NativeAgentGate::invalid);
    }

    private Optional<String> normalizeProviderCallId(Optional<String> providerId) {
        if (providerId.isEmpty() || providerId.orElseThrow().isEmpty()) return Optional.empty();
        return Optional.of(requireCallId(providerId.orElseThrow()));
    }

    private String requireCallId(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())
                || value.chars().anyMatch(Character::isISOControl)) {
            throw markPolicyFatal();
        }
        return value;
    }

    private String requireChildText(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_CHILD_CHARS) throw markPolicyFatal();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isISOControl(current) && current != '\t' && current != '\n' && current != '\r') {
                throw markPolicyFatal();
            }
            if (Character.isLowSurrogate(current)) throw markPolicyFatal();
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++index))) {
                    throw markPolicyFatal();
                }
            }
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_CHILD_BYTES) throw markPolicyFatal();
        return value;
    }

    private static NativeFailureToken classify(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 64; depth++, current = current.getCause()) {
            if (current instanceof AgentExecutionAuthorization.Failure authorization) {
                return NativeFailureToken.authorization(authorization.decision());
            }
            if (current instanceof NativeBoundaryException boundary) return boundary.token();
            if (current instanceof NativeBoundaryTimeoutException timeout) return timeout.token();
            if (current instanceof AgentLedgerException ledger) return NativeFailureToken.ledger(ledger.code());
            if (current instanceof TimeoutException) return NativeFailureToken.timeout();
        }
        return NativeFailureToken.unknown();
    }

    private static RuntimeException safeBoundaryFailure(NativeFailureToken token) {
        return switch (token.kind()) {
            case AUTHORIZATION -> new AgentExecutionAuthorization.Failure(token.decision());
            case LEDGER -> new NativeBoundaryException(token, token.ledgerCode());
            case TIMEOUT -> new NativeBoundaryTimeoutException(token);
            case POLICY, UNKNOWN -> new NativeBoundaryException(
                    token, AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE);
        };
    }

    private static Method transferMethod() {
        try {
            Method method = AgentTransfer.class.getMethod("transferToAgent", String.class, ToolContext.class);
            if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())) throw invalid();
            return method;
        } catch (NoSuchMethodException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static AgentLedgerException invalid() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                "Native Agent gate validation failed");
    }

    private record PendingTransfer(String source, String target, Map<String, Object> arguments,
                                   Optional<String> providerCallId, String actualCallId,
                                   String childText, boolean authorized) {
        private PendingTransfer {
            arguments = Map.copyOf(arguments);
            providerCallId = providerCallId == null ? Optional.empty() : providerCallId;
        }

        private PendingTransfer authorize(String callId) {
            return new PendingTransfer(source, target, arguments, providerCallId, callId, childText, true);
        }
    }

    private record ConfirmedTransfer(String target, Map<String, Object> arguments,
                                     Map<String, Object> result) {
        private ConfirmedTransfer {
            arguments = Map.copyOf(arguments);
            result = Map.copyOf(result);
        }
    }

    private enum TransferTaskStateEnum {
        NEW,
        DISPATCHED,
        RETURNED
    }

    private enum NativeFailureKindEnum {
        AUTHORIZATION,
        LEDGER,
        TIMEOUT,
        POLICY,
        UNKNOWN
    }

    private record NativeFailureToken(NativeFailureKindEnum kind,
                                      AgentRunAuthorization.Decision decision,
                                      AgentErrorCodeEnum ledgerCode) {
        private NativeFailureToken {
            if (kind == null || kind == NativeFailureKindEnum.AUTHORIZATION
                    && decision != AgentRunAuthorization.Decision.DENY
                    && decision != AgentRunAuthorization.Decision.UNAVAILABLE
                    || kind != NativeFailureKindEnum.AUTHORIZATION && decision != null
                    || kind == NativeFailureKindEnum.LEDGER && ledgerCode == null
                    || kind != NativeFailureKindEnum.LEDGER && ledgerCode != null) {
                throw new IllegalArgumentException("Native Agent failure token is invalid");
            }
        }

        private static NativeFailureToken authorization(AgentRunAuthorization.Decision decision) {
            return new NativeFailureToken(NativeFailureKindEnum.AUTHORIZATION, decision, null);
        }

        private static NativeFailureToken ledger(AgentErrorCodeEnum code) {
            return new NativeFailureToken(NativeFailureKindEnum.LEDGER, null, code);
        }

        private static NativeFailureToken timeout() {
            return new NativeFailureToken(NativeFailureKindEnum.TIMEOUT, null, null);
        }

        private static NativeFailureToken policy() {
            return new NativeFailureToken(NativeFailureKindEnum.POLICY, null, null);
        }

        private static NativeFailureToken unknown() {
            return new NativeFailureToken(NativeFailureKindEnum.UNKNOWN, null, null);
        }
    }

    private static final class NativeBoundaryException extends AgentLedgerException {
        private final NativeFailureToken token;

        private NativeBoundaryException(NativeFailureToken token, AgentErrorCodeEnum code) {
            super(code, "Native Agent boundary failed");
            this.token = token;
        }

        private NativeFailureToken token() {
            return token;
        }
    }

    private static final class NativeBoundaryTimeoutException extends RuntimeException {
        private final NativeFailureToken token;

        private NativeBoundaryTimeoutException(NativeFailureToken token) {
            super("Native Agent boundary timed out", new TimeoutException("Native Agent boundary timed out"));
            this.token = token;
        }

        private NativeFailureToken token() {
            return token;
        }
    }
}
