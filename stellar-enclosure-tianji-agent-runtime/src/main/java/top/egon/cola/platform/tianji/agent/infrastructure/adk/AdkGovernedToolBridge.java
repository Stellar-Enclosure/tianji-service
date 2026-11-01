package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.tools.BaseTool;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/** Native declarations and execution share one run scope; this port does not supply execution authority. */
public interface AdkGovernedToolBridge {
    Scope bindScope(AgentLedgerService.ExecutionMaterial material, AgentRunLease lease, ToolEventSink sink);

    interface Scope extends AutoCloseable {
        List<BaseTool> declaredTools();
        Map<String, Object> execute(BaseTool tool, Map<String, Object> arguments, UUID hostInvocationId, String author, Runnable callFence);
        String sideEffectOutcome();
        @Override void close();
    }

    @FunctionalInterface
    interface ToolEventSink { void append(ToolEventVO event); }

    enum Phase { STARTED, FINISHED }

    record ToolEventVO(Phase phase, String author, DefinitionResourceRef toolRef, UUID hostInvocationId, String state, Long dispatchEpoch,
                       ToolInvocationRecorder.AccountingState accountingState, String errorCode) {
        public ToolEventVO {
            if (phase == null || author == null || author.isBlank() || !author.equals(author.trim()) || author.length() > 128
                    || author.chars().anyMatch(Character::isISOControl) || !StandardCharsets.UTF_8.newEncoder().canEncode(author)
                    || toolRef == null || hostInvocationId == null || state == null || dispatchEpoch != null && dispatchEpoch < 0
                    || errorCode != null && !errorCode.matches("[A-Z][A-Z0-9_]{0,63}")) throw invalidEvent();
            if (phase == Phase.STARTED) {
                if (!"REQUESTED".equals(state) || !Long.valueOf(0).equals(dispatchEpoch) || accountingState != null || errorCode != null) throw invalidEvent();
            } else if ("UNAVAILABLE".equals(state)) {
                if (dispatchEpoch != null && dispatchEpoch != 0 || accountingState != null || errorCode == null) throw invalidEvent();
            } else if ("AWAITING_APPROVAL".equals(state)) {
                if (!Long.valueOf(0).equals(dispatchEpoch) || accountingState != ToolInvocationRecorder.AccountingState.PENDING || errorCode != null) throw invalidEvent();
            } else if (!Set.of("PREPARED", "DISPATCHING", "SUCCEEDED", "REJECTED", "FAILED_KNOWN", "UNKNOWN").contains(state)
                    || accountingState == null || dispatchEpoch == null && !Set.of("SUCCEEDED", "REJECTED", "FAILED_KNOWN").contains(state)) throw invalidEvent();
        }
        @Override public String toString() { return "AdkGovernedToolBridge.ToolEventVO[redacted]"; }
    }

    private static IllegalArgumentException invalidEvent() { return new IllegalArgumentException("Governed tool event metadata is invalid"); }
}
