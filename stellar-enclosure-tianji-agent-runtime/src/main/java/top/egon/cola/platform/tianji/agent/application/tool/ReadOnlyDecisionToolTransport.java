package top.egon.cola.platform.tianji.agent.application.tool;

import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fixed read-only transport capability. A future Recorder must commit dispatch before consuming the scoped operation. */
public interface ReadOnlyDecisionToolTransport {
    <T> T withVerifiedCall(Request request, Callback<T> callback);

    record Request(AgentRunAuthorization.Request run, AgentRunLease lease, ToolDescriptor descriptor,
                   Map<String, Object> arguments, UUID invocationId, Instant deadlineAt) {
        public Request {
            try {
                if (run == null || lease == null || descriptor == null || invocationId == null || deadlineAt == null
                        || "LEGACY".equals(run.actor().issuerFingerprint()) || !run.runId().equals(lease.runId())
                        || descriptor.policy().risk() != ToolPolicy.Risk.READ_ONLY || deadlineAt.isAfter(run.deadlineAt())) throw invalid();
                arguments = descriptor.input().values(arguments);
            } catch (RuntimeException malformed) { throw invalid(); }
        }
        public String parametersSha256() { return descriptor.parametersSha256(arguments); }
        public String downstreamRequestId() {
            String hash = OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_DMN_REQUEST_V1", run.actor().identityFingerprint(), run.runId(),
                    invocationId.toString(), descriptor.key(), Long.toString(descriptor.version()), descriptor.reference().contentSha256()), Map.of());
            var bytes = ByteBuffer.wrap(HexFormat.of().parseHex(hash));
            return new UUID(bytes.getLong(), bytes.getLong()).toString();
        }
        public String correlationId() { return run.runId(); }
        @Override public String toString() { return "DecisionToolRequest[redacted]"; }
    }

    /** Identity observation supplied only by a trusted transport callback; construction does not authenticate it. */
    record VerifiedExecutor(TrustedAgentActor actor, Instant expiresAt) {
        public VerifiedExecutor {
            if (actor == null || actor.subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint()) || expiresAt == null) throw invalid();
        }
        @Override public String toString() { return "DecisionToolExecutor[redacted]"; }
    }

    @FunctionalInterface interface Callback<T> { T apply(VerifiedExecutor executor, OneShotDecision operation); }
    @FunctionalInterface interface OneShotDecision { DecisionObservation evaluate(); }
    enum Status { MATCHED, NO_MATCH }

    record DecisionObservation(Status status, String decisionExecutionId, Instant submittedAt, Instant receivedAt,
                               Map<String, Object> output, String matchedRuleId) {
        public DecisionObservation {
            try {
                if (status == null || decisionExecutionId == null || !UUID.fromString(decisionExecutionId).toString().equals(decisionExecutionId)
                        || submittedAt == null || receivedAt == null) throw invalid();
                output = primitiveOutput(output);
                if (status == Status.NO_MATCH && (!output.isEmpty() || matchedRuleId != null)
                        || status == Status.MATCHED && (matchedRuleId == null || !(matchedRuleId.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}") || matchedRuleId.matches("row:[1-9][0-9]{0,2}")))) throw invalid();
            } catch (RuntimeException malformed) { throw invalid(); }
        }
        @Override public String toString() { return "DecisionToolObservation[redacted]"; }
    }

    enum FailureKind {
        CREDENTIAL_UNAVAILABLE, RELEASE_UNAVAILABLE, RELEASE_CHANGED, OPERATION_DENIED, OPERATION_REJECTED,
        OPERATION_RESULT_UNKNOWN, OPERATION_INVALID_RESPONSE, CANCELLED, DEADLINE_EXCEEDED
    }
    final class Failure extends RuntimeException {
        private final FailureKind kind;
        public Failure(FailureKind kind) { super(kind.name(), null); this.kind = kind; }
        public FailureKind kind() { return kind; }
    }

    private static Map<String, Object> primitiveOutput(Map<String, Object> values) {
        if (values == null || values.size() > 16) throw invalid();
        var fields = new HashMap<String, ToolSchema.ValueType>();
        for (var entry : values.entrySet()) {
            var value = entry.getValue();
            if (value instanceof String) fields.put(entry.getKey(), ToolSchema.ValueType.STRING);
            else if (value instanceof Boolean) fields.put(entry.getKey(), ToolSchema.ValueType.BOOLEAN);
            else if (value instanceof Number) fields.put(entry.getKey(), ToolSchema.ValueType.INTEGER);
            else throw invalid();
        }
        return new ToolSchema(fields, fields.keySet()).values(values);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Decision tool transport value is invalid"); }
}
