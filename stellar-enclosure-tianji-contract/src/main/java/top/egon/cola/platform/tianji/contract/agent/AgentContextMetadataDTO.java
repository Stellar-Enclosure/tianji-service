package top.egon.cola.platform.tianji.contract.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Describes prepared input at the native model boundary; it is neither a provider wire receipt nor an access grant. */
public record AgentContextMetadataDTO(int schemaVersion, String projectionVersion, String ownerSha256, String runId,
                                       String reservationId, long leaseEpoch, String agentName, String nativeInvocationId,
                                       String nativeSessionId, int availableHistoryEventCount, String historyEventIdsSha256,
                                       String lastHistoryEventId, String definitionContentSha256,
                                       String requestSha256, String executionSnapshotSha256, String nativeRequestSha256,
                                       String sourcePolicySha256, String modelAdapterSha256, String trimmingPolicySha256,
                                       AgentContextWindowDTO window, long estimatedInputTokens,
                                       int effectiveOutputLimit, List<AgentContextSourceDTO> sources, List<AgentContextTrimDecisionDTO> trims) {
    public static final String PROJECTION_V1 = "ADK_GENAI_MODEL_V1";
    public AgentContextMetadataDTO {
        if (schemaVersion != 1 || !PROJECTION_V1.equals(projectionVersion)) throw new IllegalArgumentException("context projection version is unsupported");
        ownerSha256 = ContractChecks.contextHash(ownerSha256);
        runId = ContractChecks.contextIdentifier(runId, "runId", 64);
        reservationId = ContractChecks.contextIdentifier(reservationId, "reservationId", 128);
        agentName = ContractChecks.contextIdentifier(agentName, "agentName", 128);
        nativeInvocationId = ContractChecks.contextIdentifier(nativeInvocationId, "nativeInvocationId", 128);
        nativeSessionId = ContractChecks.contextIdentifier(nativeSessionId, "nativeSessionId", 128);
        historyEventIdsSha256 = ContractChecks.contextHash(historyEventIdsSha256);
        if (lastHistoryEventId != null) ContractChecks.contextIdentifier(lastHistoryEventId, "lastHistoryEventId", 128);
        if (availableHistoryEventCount < 0 || (availableHistoryEventCount == 0) != (lastHistoryEventId == null)) throw new IllegalArgumentException("context history watermark is invalid");
        if (leaseEpoch < 1) throw new IllegalArgumentException("context lease epoch is invalid");
        definitionContentSha256 = ContractChecks.contextHash(definitionContentSha256);
        requestSha256 = ContractChecks.contextHash(requestSha256);
        executionSnapshotSha256 = ContractChecks.contextHash(executionSnapshotSha256);
        nativeRequestSha256 = ContractChecks.contextHash(nativeRequestSha256);
        sourcePolicySha256 = ContractChecks.contextHash(sourcePolicySha256);
        modelAdapterSha256 = ContractChecks.contextHash(modelAdapterSha256);
        trimmingPolicySha256 = ContractChecks.contextHash(trimmingPolicySha256);
        window = Objects.requireNonNull(window, "window");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        trims = trims == null ? List.of() : List.copyOf(trims);
        long available = window.windowTokens() - window.safetyMarginTokens();
        if (estimatedInputTokens < 0 || effectiveOutputLimit < 1 || effectiveOutputLimit > available
                || estimatedInputTokens > available - effectiveOutputLimit || sources.isEmpty() || sources.size() > 128 || trims.size() > 128) {
            throw new IllegalArgumentException("context metadata exceeds its limits");
        }
        Set<List<Object>> identities = new HashSet<>();
        for (var source : sources) if (!identities.add(List.of(source.kind(), source.sourceId(), source.sourceVersion()))) {
            throw new IllegalArgumentException("context source is duplicated");
        }
    }
}
