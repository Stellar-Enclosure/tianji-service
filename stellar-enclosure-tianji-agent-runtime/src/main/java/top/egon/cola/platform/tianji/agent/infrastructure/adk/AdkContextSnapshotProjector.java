package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.JsonBaseModel;
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.contract.agent.AgentContextMetadataDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextRoleEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceKindEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextWindowDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Projects digests and controlled references only. Transport options and complete model bodies are never stored. */
final class AdkContextSnapshotProjector {
    static final String NO_TRIM_POLICY = AdkSemanticJsonDigest.fingerprint("TIANJI_CONTEXT_TRIM_NONE_V1", List.of());
    private final AgentExecutionSnapshot snapshot;
    private final AgentRunAuthorization.Request authorization;
    private final AgentRunLease lease;
    private final DefaultContextAssembler assembler;
    private final List<DefaultContextAssembler.Source> inputs;

    AdkContextSnapshotProjector(AgentExecutionSnapshot snapshot, AgentRunAuthorization.Request authorization,
                               AgentRunLease lease, DefaultContextAssembler assembler) {
        this.snapshot = snapshot; this.authorization = authorization; this.lease = lease; this.assembler = assembler;
        this.inputs = assembler.assemble(snapshot.input()).sources();
    }

    PreparedCall prepare(CallbackContext context, LlmRequest original) {
        try {
            var events = List.copyOf(context.events());
            var ids = events.stream().map(event -> event.id()).toList();
            var request = original.toBuilder().contents(List.copyOf(original.contents())).build();
            return new PreparedCall(context.eventId(), context.agentName(), context.invocationId(), context.sessionId(), ids.size(),
                    AdkSemanticJsonDigest.fingerprint("ADK_AVAILABLE_EVENT_IDS_V1", ids), ids.isEmpty() ? null : ids.getLast(), request);
        } catch (AgentLedgerException failure) { throw failure; }
        catch (RuntimeException failure) { throw invalid(); }
    }

    AgentContextMetadataDTO capture(PreparedCall call, LlmRequest request, AgentModelBudgetEstimator.ContextWindow window,
                                    AgentModelBudgetEstimator.Estimate estimate, int outputLimit,
                                    List<ContextProvider.Fragment> fragments, String adapterFingerprint) {
        try {
            var contents = AdkSemanticJsonDigest.contents(request.contents());
            var originalSystem = system(call.original());
            var system = system(request);
            List<AgentContextSourceDTO> sources = new ArrayList<>();
            sources.add(new AgentContextSourceDTO(AgentContextSourceKindEnum.DEFINITION, AgentContextRoleEnum.SYSTEM,
                    snapshot.definition().definitionKey(), Long.toString(snapshot.definition().version()), "definition:" + snapshot.definition().definitionKey(),
                    snapshot.definitionContentSha256(), originalSystem.sha256(), originalSystem.utf8Bytes(), null));
            for (var input : inputs) sources.add(new AgentContextSourceDTO(AgentContextSourceKindEnum.valueOf(input.kind()), AgentContextRoleEnum.USER,
                    input.kind().equals("USER_INPUT") ? "input:text" : "input:taskData", "1", "snapshot:" + authorization.snapshotSha256(),
                    input.contentSha256(), input.contentSha256(), input.utf8Bytes(), null));
            for (var fragment : fragments) {
                var text = AdkSemanticJsonDigest.text(AdkRunBudgetPlugin.sourceText(fragment));
                sources.add(new AgentContextSourceDTO(AgentContextSourceKindEnum.valueOf(fragment.kind().name()),
                        fragment.kind() == ContextProvider.Kind.PROMPT || fragment.kind() == ContextProvider.Kind.SKILL ? AgentContextRoleEnum.SYSTEM : AgentContextRoleEnum.USER,
                        fragment.sourceId(), fragment.sourceVersion(), fragment.provenanceId(), fragment.sourceSha256(), text.sha256(), text.utf8Bytes(), fragment.observedAt()));
            }
            String nativeReference = "adk-session:" + call.sessionId() + ":invocation:" + call.invocationId();
            sources.add(new AgentContextSourceDTO(AgentContextSourceKindEnum.ADK_CONTENTS, AgentContextRoleEnum.MIXED, "adk-contents",
                    AgentContextMetadataDTO.PROJECTION_V1, nativeReference, contents.sha256(), contents.sha256(), contents.utf8Bytes(), null));
            sources.add(new AgentContextSourceDTO(AgentContextSourceKindEnum.ADK_SYSTEM, AgentContextRoleEnum.SYSTEM, "adk-system",
                    AgentContextMetadataDTO.PROJECTION_V1, nativeReference, system.sha256(), system.sha256(), system.utf8Bytes(), null));
            String requestHash = AdkSemanticJsonDigest.fingerprint(AgentContextMetadataDTO.PROJECTION_V1, List.of(request.model().orElseThrow(),
                    contents.sha256(), request.config().map(config -> AdkSemanticJsonDigest.json(config.toJson(), true).sha256()).orElseGet(() -> AdkSemanticJsonDigest.json("null", false).sha256()),
                    request.liveConnectConfig() == null ? AdkSemanticJsonDigest.json("null", false).sha256() : AdkSemanticJsonDigest.json(request.liveConnectConfig().toJson(), true).sha256(),
                    request.cacheConfig().map(value -> AdkSemanticJsonDigest.json(JsonBaseModel.toJsonString(value), true).sha256()).orElseGet(() -> AdkSemanticJsonDigest.json("null", false).sha256()),
                    request.cacheMetadata().map(value -> AdkSemanticJsonDigest.json(value.toJson(), true).sha256()).orElseGet(() -> AdkSemanticJsonDigest.json("null", false).sha256()),
                    AdkSemanticJsonDigest.fingerprint("ADK_NATIVE_TOOL_NAMES_V1", request.tools().keySet().stream().sorted().toList()),
                    request.cacheableContentsTokenCount().map(Object::toString).orElse("NONE")));
            String policy = assembler.policyFingerprint(new ContextProvider.Request(authorization, snapshot, lease.epoch(), Duration.ofSeconds(30)));
            return new AgentContextMetadataDTO(1, AgentContextMetadataDTO.PROJECTION_V1, authorization.actor().identityFingerprint(), authorization.runId(),
                    call.reservationId(), lease.epoch(), call.agentName(), call.invocationId(), call.sessionId(), call.historyCount(), call.historySha256(), call.lastHistoryId(),
                    authorization.definitionSha256(), authorization.requestSha256(), authorization.snapshotSha256(), requestHash, policy, adapterFingerprint, NO_TRIM_POLICY,
                    new AgentContextWindowDTO(window.modelName(), window.modelRouteRef(), window.policyVersionRef(), window.windowTokens(), window.safetyMarginTokens()),
                    estimate.inputTokens(), outputLimit, sources, List.of());
        } catch (AgentLedgerException failure) { throw failure; }
        catch (RuntimeException failure) { throw invalid(); }
    }

    private static AdkSemanticJsonDigest.Value system(LlmRequest request) {
        return AdkSemanticJsonDigest.json(request.config().flatMap(GenerateContentConfig::systemInstruction).map(Content::toJson).orElse("null"), false);
    }
    record PreparedCall(String reservationId, String agentName, String invocationId, String sessionId, int historyCount,
                        String historySha256, String lastHistoryId, LlmRequest original) {
        @Override public String toString() { return "PreparedModelContext[redacted]"; }
    }
    private static AgentLedgerException invalid() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Model context metadata could not be projected"); }
}
