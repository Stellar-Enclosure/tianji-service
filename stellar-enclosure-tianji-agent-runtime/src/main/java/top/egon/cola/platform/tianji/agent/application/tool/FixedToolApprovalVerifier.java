package top.egon.cola.platform.tianji.agent.application.tool;

import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolApprovalBinding;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Trusted fixed-source approval delivery; observations alone neither authenticate callers nor consume approvals. */
public interface FixedToolApprovalVerifier {
    <T> T withVerifiedApproval(Request request, Callback<T> callback);

    record Request(AgentRunAuthorization.Request run, AgentRunLease lease, ToolDescriptor descriptor,
                   ToolActionRequest action, String approvalReceiptId, Instant deadlineAt) {
        public Request {
            try {
                if (run == null || lease == null || descriptor == null || action == null || deadlineAt == null
                        || "LEGACY".equals(run.actor().issuerFingerprint()) || !run.runId().equals(lease.runId()) || lease.epoch() < 1
                        || descriptor.policy().risk() != ToolPolicy.Risk.APPROVAL_REQUIRED || !ProcessClient.START_RESOURCE_KEY.equals(descriptor.policy().executionResourceKey())
                        || !descriptor.output().fields().equals(Map.of("instanceId", ToolSchema.ValueType.STRING))
                        || !descriptor.output().required().equals(Set.of("instanceId")) || deadlineAt.isAfter(run.deadlineAt())) throw invalid();
                text(approvalReceiptId, 255);
                var expected = new ToolActionRequest(run.actor(), descriptor, action.businessKey(), action.businessVersion(), action.arguments());
                if (!run.actor().equals(action.owner()) || !descriptor.reference().equals(action.tool()) || !descriptor.policy().equals(action.policy())
                        || !expected.actionKey().equals(action.actionKey()) || !expected.arguments().equals(action.arguments())
                        || !expected.intentId().equals(action.intentId()) || !expected.requestSha256().equals(action.requestSha256())) throw invalid();
            } catch (RuntimeException malformed) { throw invalid(); }
        }
        public String parametersSha256() { return descriptor.parametersSha256(action.arguments()); }
        @Override public String toString() { return "ToolApprovalVerificationRequest[redacted]"; }
    }

    @FunctionalInterface interface Callback<T> { T apply(ApprovedObservation approval); }

    record ApprovedObservation(String sourceId, String sourceIssuer, String sourceResource, ToolApprovalBinding binding,
                               VerifiedApprovalDTO approval, TrustedAgentActor consumer, Instant consumerExpiresAt) {
        public ApprovedObservation {
            try {
                if (sourceId == null || !sourceId.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}") || binding == null || approval == null
                        || consumer == null || consumer.subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(consumer.issuerFingerprint())
                        || !consumer.equals(binding.consumer()) || !approval.approved() || consumerExpiresAt == null || !consumerExpiresAt.isAfter(approval.verifiedAt())) throw invalid();
                source(sourceIssuer, 2048); source(sourceResource, 512);
                if (!TrustedAgentActor.verified(consumer.tenantId(), TrustedAgentActor.SubjectType.SERVICE, consumer.subjectId(), sourceIssuer).equals(consumer)
                        || !binding.policyRef().equals(approval.policyRef()) || !binding.policyBindingSha256().equals(approval.policyBindingSha256())
                        || !binding.releaseVersion().equals(approval.releaseVersion()) || !binding.definitionKey().equals(approval.definitionKey())
                        || !binding.definitionSha256().equals(approval.definitionSha256()) || !binding.taskDefinitionKey().equals(approval.taskDefinitionKey())) throw invalid();
            } catch (RuntimeException malformed) { throw invalid(); }
        }
        public long tenantId() { return consumer.tenantId(); }
        public String sourceNamespaceSha256() { return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_APPROVAL_SOURCE_V1", sourceId, Long.toString(tenantId()), sourceIssuer, sourceResource), Map.of()); }
        public Instant expiresAt() { return consumerExpiresAt.isBefore(approval.expiresAt()) ? consumerExpiresAt : approval.expiresAt(); }
        @Override public String toString() { return "ApprovedToolObservation[redacted]"; }
    }

    enum FailureKind { CREDENTIAL_UNAVAILABLE, APPROVAL_DENIED, APPROVAL_UNAVAILABLE, APPROVAL_INVALID_RESPONSE, CANCELLED, DEADLINE_EXCEEDED }
    final class Failure extends RuntimeException {
        private final FailureKind kind;
        public Failure(FailureKind kind) { super(kind.name(), null); this.kind = kind; }
        public FailureKind kind() { return kind; }
    }

    private static void text(String value, int maximum) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > maximum
                || value.chars().anyMatch(Character::isISOControl) || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw invalid();
    }
    private static void source(String value, int maximum) {
        if (value == null || value.length() > maximum) throw invalid(); var uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !uri.toASCIIString().equals(value) || !uri.normalize().toASCIIString().equals(value)) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool approval verification value is invalid"); }
}
