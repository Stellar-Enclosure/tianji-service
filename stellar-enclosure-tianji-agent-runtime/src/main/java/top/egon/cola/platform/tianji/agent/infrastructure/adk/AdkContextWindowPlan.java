package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceKindEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextTrimDecisionDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One model callback owns this selection state, including retries; persistent session events are never modified. */
final class AdkContextWindowPlan {
    private final AgentModelBudgetEstimator.ContextPolicy policy;
    private final List<Content> nativeContents;
    private final List<ContextProvider.Fragment> sources;
    private final List<AgentContextTrimDecisionDTO> decisions = new ArrayList<>();

    AdkContextWindowPlan(LlmRequest original, List<ContextProvider.Fragment> sources, AgentModelBudgetEstimator.ContextPolicy policy) {
        this.policy = policy; this.nativeContents = new ArrayList<>(original.contents()); this.sources = new ArrayList<>(sources);
    }

    Selection fit(LlmRequest.Builder request, AgentExecutionSnapshot snapshot, AgentModelBudgetEstimator estimator,
                  AgentModelBudgetEstimator.ContextWindow window, AgentModelBudgetEstimator.Estimate initial, int outputLimit) {
        if (!policy.policyVersionRef().equals(snapshot.definition().dependencies().policyVersionRef())
                || !policy.equals(estimator.contextPolicy(snapshot, request.build()))) throw changed();
        var estimate = initial;
        int output = outputLimit;
        while (!fits(window, estimate, output)) {
            if (decisions.size() >= policy.maxTrimSteps()) throw exhausted();
            var removal = nextRemoval();
            if (removal == null) throw exhausted();
            cap(request, output);
            String before = AdkContextSnapshotProjector.nativeFingerprint(request.build());
            if (removal.sourceIndex() >= 0) sources.remove(removal.sourceIndex());
            else nativeContents.subList(removal.start(), removal.end()).clear();
            rebuildContents(request);
            estimate = estimator.estimate(snapshot, request.build());
            output = Math.min(output, estimate.maxOutputTokens()); cap(request, output);
            if (!window.equals(estimator.contextWindow(snapshot, request.build()))
                    || !policy.equals(estimator.contextPolicy(snapshot, request.build()))) throw changed();
            decisions.add(new AgentContextTrimDecisionDTO(removal.kind(), removal.id(), removal.version(), removal.reason(), before,
                    AdkContextSnapshotProjector.nativeFingerprint(request.build()), removal.count()));
        }
        return new Selection(estimate, output, List.copyOf(sources), List.copyOf(decisions), policy.fingerprint());
    }

    private Removal nextRemoval() {
        if (policy.trimEvidence()) {
            for (int i = sources.size() - 1; i >= 0; i--) {
                var source = sources.get(i);
                if (source.kind() == ContextProvider.Kind.RAG || source.kind() == ContextProvider.Kind.MEMORY) {
                    return new Removal(i, -1, -1, AgentContextSourceKindEnum.valueOf(source.kind().name()), source.sourceId(), source.sourceVersion(), "LOW_PRIORITY_EVIDENCE", 1);
                }
            }
        }
        if (!policy.trimHistory()) return null;
        List<Integer> turns = new ArrayList<>();
        for (int i = 0; i < nativeContents.size(); i++) if (userTurn(nativeContents.get(i))) turns.add(i);
        for (int i = 0; i < turns.size() - policy.retainRecentUserTurns(); i++) {
            int start = turns.get(i); int end = turns.get(i + 1);
            if (end - start <= 1000 && complete(nativeContents.subList(start, end))) {
                return new Removal(-1, start, end, AgentContextSourceKindEnum.ADK_CONTENTS, "adk-contents", "ADK_COMPLETE_TURN_V1", "OLD_COMPLETE_TURN", end - start);
            }
        }
        return null;
    }

    private void rebuildContents(LlmRequest.Builder request) {
        var contents = new ArrayList<>(nativeContents);
        var evidence = sources.stream().filter(source -> source.kind() == ContextProvider.Kind.RAG || source.kind() == ContextProvider.Kind.MEMORY)
                .map(source -> Part.fromText(AdkRunBudgetPlugin.sourceText(source))).toList();
        if (!evidence.isEmpty()) contents.add(Content.builder().role("user").parts(evidence).build());
        request.contents(contents);
    }

    private static boolean userTurn(Content content) {
        var parts = content.parts().orElse(List.of());
        return "user".equals(content.role().orElse(null)) && parts.stream().noneMatch(part -> part.functionCall().isPresent()
                || part.functionResponse().isPresent() || part.toolCall().isPresent() || part.toolResponse().isPresent()
                || part.executableCode().isPresent() || part.codeExecutionResult().isPresent())
                && parts.stream().anyMatch(part -> part.text().filter(text -> !text.isBlank()).isPresent() || part.inlineData().isPresent() || part.fileData().isPresent());
    }

    private static boolean complete(List<Content> turn) {
        Map<ToolKey, Integer> pending = new HashMap<>();
        for (var content : turn) {
            String role = content.role().orElse("");
            if (!"user".equals(role) && !"model".equals(role)) return false;
            for (var part : content.parts().orElse(List.of())) {
                if (part.executableCode().isPresent() || part.codeExecutionResult().isPresent()) return false;
                if (part.functionCall().isPresent()) {
                    var call = part.functionCall().orElseThrow();
                    if (!add(pending, new ToolKey("FUNCTION", call.id().orElse(null), call.name().orElse(null)))) return false;
                }
                if (part.functionResponse().isPresent()) {
                    var response = part.functionResponse().orElseThrow();
                    if (!remove(pending, new ToolKey("FUNCTION", response.id().orElse(null), response.name().orElse(null)))) return false;
                }
                if (part.toolCall().isPresent()) {
                    var call = part.toolCall().orElseThrow();
                    if (!add(pending, new ToolKey("NATIVE_TOOL", call.id().orElse(null), call.toolType().map(Object::toString).orElse(null)))) return false;
                }
                if (part.toolResponse().isPresent()) {
                    var response = part.toolResponse().orElseThrow();
                    if (!remove(pending, new ToolKey("NATIVE_TOOL", response.id().orElse(null), response.toolType().map(Object::toString).orElse(null)))) return false;
                }
            }
        }
        return pending.isEmpty();
    }
    private static boolean add(Map<ToolKey, Integer> pending, ToolKey key) {
        if (key.name() == null || key.name().isBlank() || key.id() != null && key.id().isBlank()) return false;
        if (key.id() != null && pending.containsKey(key)) return false;
        pending.merge(key, 1, Integer::sum); return true;
    }
    private static boolean remove(Map<ToolKey, Integer> pending, ToolKey key) {
        Integer count = pending.get(key); if (count == null) return false;
        if (count == 1) pending.remove(key); else pending.put(key, count - 1);
        return true;
    }
    private static boolean fits(AgentModelBudgetEstimator.ContextWindow window, AgentModelBudgetEstimator.Estimate estimate, int output) {
        long available = window.windowTokens() - window.safetyMarginTokens();
        return output >= 1 && output <= available && estimate.inputTokens() <= available - output;
    }
    private static void cap(LlmRequest.Builder request, int output) {
        var config = request.config().orElseGet(() -> GenerateContentConfig.builder().build());
        request.config(config.toBuilder().maxOutputTokens(output).build());
    }
    record Selection(AgentModelBudgetEstimator.Estimate estimate, int outputLimit, List<ContextProvider.Fragment> sources,
                     List<AgentContextTrimDecisionDTO> decisions, String policySha256) { }
    private record Removal(int sourceIndex, int start, int end, AgentContextSourceKindEnum kind, String id, String version, String reason, int count) { }
    private record ToolKey(String format, String id, String name) { }
    private static AgentLedgerException changed() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "context selection policy changed during the call"); }
    private static AgentLedgerException exhausted() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED, "required model context cannot fit the selected window"); }
}
