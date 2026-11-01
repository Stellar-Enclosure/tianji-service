package top.egon.cola.platform.tianji.agent.application.tool;

import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
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
import java.util.Objects;
import java.util.UUID;

/** Fixed nonbillable read-only MCP capability; matching metadata alone grants no authority. */
public interface ReadOnlyMcpToolTransport {
    String EXECUTE_RESOURCE_KEY = "tianji.mcp.tools.call";
    <T> T withVerifiedCall(Request request, Callback<T> callback);

    record Request(AgentRunAuthorization.Request run, AgentRunLease lease, ToolDescriptor descriptor,
                   Map<String, Object> arguments, UUID invocationId, Instant deadlineAt) {
        public Request {
            try {
                if (run == null || lease == null || descriptor == null || invocationId == null || deadlineAt == null
                        || "LEGACY".equals(run.actor().issuerFingerprint()) || !run.runId().equals(lease.runId())
                        || descriptor.policy().risk() != ToolPolicy.Risk.READ_ONLY
                        || !EXECUTE_RESOURCE_KEY.equals(descriptor.policy().executionResourceKey()) || deadlineAt.isAfter(run.deadlineAt())) throw invalid();
                arguments = descriptor.input().values(arguments);
            } catch (RuntimeException malformed) { throw invalid(); }
        }
        public String parametersSha256() { return descriptor.parametersSha256(arguments); }
        public String downstreamRequestId() {
            String hash = OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_MCP_REQUEST_V1", run.actor().identityFingerprint(), run.runId(),
                    invocationId.toString(), descriptor.key(), Long.toString(descriptor.version()), descriptor.reference().contentSha256()), Map.of());
            var bytes = ByteBuffer.wrap(HexFormat.of().parseHex(hash));
            return new UUID(bytes.getLong(), bytes.getLong()).toString();
        }
        public String correlationId() { return run.runId(); }
        @Override public String toString() { return "McpToolRequest[redacted]"; }
    }

    @FunctionalInterface interface Callback<T> {
        T apply(ReadOnlyDecisionToolTransport.VerifiedExecutor executor, OneShotCall operation);
    }
    /** The consumer must commit BEGIN first; implementations narrow the original scoped window without resetting it. */
    @FunctionalInterface interface OneShotCall { CallObservation call(Instant callDeadlineAt); }
    enum Status { SUCCESS, TOOL_ERROR, UNKNOWN }
    record CallObservation(Status status, Map<String, Object> output) {
        public CallObservation {
            try {
                if (status == null || output == null || output.size() > 16) throw invalid();
                var fields = new HashMap<String, ToolSchema.ValueType>();
                for (var entry : output.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof String) fields.put(entry.getKey(), ToolSchema.ValueType.STRING);
                    else if (value instanceof Boolean) fields.put(entry.getKey(), ToolSchema.ValueType.BOOLEAN);
                    else if (value instanceof Number) fields.put(entry.getKey(), ToolSchema.ValueType.INTEGER);
                    else throw invalid();
                }
                output = new ToolSchema(fields, fields.keySet()).values(output);
                if (status != Status.SUCCESS && !output.isEmpty()) throw invalid();
            } catch (RuntimeException malformed) { throw invalid(); }
        }
        @Override public String toString() { return "McpToolObservation[redacted]"; }
    }
    enum FailureKind {
        CREDENTIAL_UNAVAILABLE, PROFILE_UNAVAILABLE, PROFILE_CHANGED, OPERATION_DENIED, OPERATION_NOT_SENT,
        OPERATION_RESULT_UNKNOWN, OPERATION_INVALID_RESPONSE, CANCELLED, DEADLINE_EXCEEDED
    }
    final class Failure extends RuntimeException {
        private final FailureKind kind;
        public Failure(FailureKind kind) { super(Objects.requireNonNull(kind, "kind").name(), null); this.kind = kind; }
        public FailureKind kind() { return kind; }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("MCP tool transport value is invalid"); }
}
