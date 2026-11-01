package top.egon.cola.platform.tianji.agent.application.tool;

import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Explicit fixed START capability; a future caller must commit authorized dispatch before consuming it. */
public interface FixedBpmnStartToolTransport {
    <T> T withVerifiedCall(Request request, Callback<T> callback);

    record Request(AgentRunAuthorization.Request run, AgentRunLease lease, ToolDescriptor descriptor,
                   ToolActionRequest action, UUID invocationId, Instant deadlineAt) {
        public Request {
            try {
                if (run == null || lease == null || descriptor == null || action == null || invocationId == null || deadlineAt == null
                        || "LEGACY".equals(run.actor().issuerFingerprint()) || !run.runId().equals(lease.runId())
                        || descriptor.policy().risk() == ToolPolicy.Risk.READ_ONLY || deadlineAt.isAfter(run.deadlineAt())) throw invalid();
                var arguments = descriptor.input().values(action.arguments());
                var expected = new ToolActionRequest(run.actor(), descriptor, action.businessKey(), action.businessVersion(), arguments);
                if (!run.actor().equals(action.owner()) || !descriptor.reference().equals(action.tool()) || !descriptor.policy().equals(action.policy())
                        || !expected.actionKey().equals(action.actionKey()) || !arguments.equals(action.arguments())
                        || !expected.intentId().equals(action.intentId()) || !expected.requestSha256().equals(action.requestSha256())) throw invalid();
            } catch (RuntimeException malformed) { throw invalid(); }
        }
        @Override public String toString() { return "BpmnStartToolRequest[redacted]"; }
    }

    @FunctionalInterface interface Callback<T> { T apply(ReadOnlyDecisionToolTransport.VerifiedExecutor executor, OneShotStart operation); }
    @FunctionalInterface interface OneShotStart {
        StartObservation start();
        /** Implementations must opt in to deadline enforcement; unsupported hosts never fall back to an unbounded send. */
        default StartObservation start(Instant callDeadlineAt) { throw new Failure(FailureKind.OPERATION_NOT_SENT); }
    }

    record StartObservation(String hostReceiptReference, String instanceId) {
        public StartObservation {
            try {
                if (hostReceiptReference == null || !hostReceiptReference.matches("tianji:ps-start:v1:[0-9a-f]{64}")) throw invalid();
                instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
            } catch (RuntimeException malformed) { throw invalid(); }
        }
        public Map<String, Object> output() { return Map.of("instanceId", instanceId); }
        @Override public String toString() { return "BpmnStartToolObservation[redacted]"; }
    }

    enum FailureKind {
        CREDENTIAL_UNAVAILABLE, RELEASE_UNAVAILABLE, RELEASE_CHANGED, OPERATION_NOT_SENT, OPERATION_DENIED,
        OPERATION_RESULT_UNKNOWN, OPERATION_INVALID_RESPONSE, CANCELLED, DEADLINE_EXCEEDED
    }
    final class Failure extends RuntimeException {
        private final FailureKind kind;
        public Failure(FailureKind kind) { super(kind.name(), null); this.kind = kind; }
        public FailureKind kind() { return kind; }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("BPMN start tool transport value is invalid"); }
}
