package top.egon.cola.platform.tianji.agent.domain.tool;

import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Independent tool capability values. Constructing metadata does not verify a sealed ticket or authorize a call. */
public final class ToolExecutionGrant {
    public static final boolean SESSION_REQUIRED = false;
    private ToolExecutionGrant() { }

    public record BindingVO(AgentRunAuthorization.Request run, DefinitionResourceRef tool, String backendBindingSha256,
                            String purpose, URI resource, String authorizationResourceKey, AgentServiceExecutionGrant.Subject serviceSubject) {
        public BindingVO {
            if (run == null || tool == null || !ToolPolicy.key(tool.key()) || !ToolPolicy.key(authorizationResourceKey)
                    || backendBindingSha256 == null || !backendBindingSha256.matches("[0-9a-f]{64}")
                    || purpose == null || !purpose.matches("TIANJI_TOOL_[A-Z][A-Z0-9_]{0,83}")
                    || (run.actor().subjectType() == TrustedAgentActor.SubjectType.SERVICE) != (serviceSubject != null)) throw invalid();
            sourceFingerprint(run, resource, serviceSubject);
        }

        /** Stable issuance slot; changed metadata must conflict rather than mint another capability for this run/tool. */
        public String capabilityId() {
            String hash = OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_CAPABILITY_V1", run.actor().identityFingerprint(),
                    run.runId(), tool.key(), Long.toString(tool.version())), Map.of());
            var bytes = ByteBuffer.wrap(HexFormat.of().parseHex(hash));
            return new UUID(bytes.getLong(), bytes.getLong()).toString();
        }

        public String bindingSha256() {
            return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_BINDING_V1", sourceFingerprint(run, resource, serviceSubject),
                    tool.key(), Long.toString(tool.version()), tool.contentSha256(), backendBindingSha256, purpose, authorizationResourceKey), Map.of());
        }

        // URI.equals ignores host/scheme case; the existing source fingerprints bind the exact ASCII resource text.
        @Override public boolean equals(Object value) {
            return value instanceof BindingVO other && run.equals(other.run) && tool.equals(other.tool)
                    && backendBindingSha256.equals(other.backendBindingSha256) && purpose.equals(other.purpose)
                    && resource.toASCIIString().equals(other.resource.toASCIIString()) && authorizationResourceKey.equals(other.authorizationResourceKey)
                    && Objects.equals(serviceSubject, other.serviceSubject) && Objects.equals(sourceResource(), other.sourceResource());
        }
        @Override public int hashCode() {
            return Objects.hash(run, tool, backendBindingSha256, purpose, resource.toASCIIString(), authorizationResourceKey, serviceSubject, sourceResource());
        }
        private String sourceResource() { return serviceSubject == null ? null : serviceSubject.resourceUri().toASCIIString(); }
        @Override public String toString() { return "ToolExecutionGrant.BindingVO[redacted]"; }
    }

    public record ReservationVO(BindingVO binding, long issueEpoch) {
        public ReservationVO { if (binding == null || issueEpoch < 1) throw invalid(); }
        @Override public String toString() { return "ToolExecutionGrant.ReservationVO[redacted]"; }
    }

    /** Structurally sealed evidence only; the authority must independently match the decrypted purpose, source and binding. */
    public record EvidenceVO(ReservationVO reservation, String grantId, String ciphertext, Instant expiresAt) {
        public EvidenceVO {
            if (reservation == null) throw invalid();
            if (reservation.binding().run().actor().subjectType() == TrustedAgentActor.SubjectType.USER) {
                new AgentExecutionGrant.Ticket(grantId, ciphertext, expiresAt);
            } else {
                new AgentServiceExecutionGrant.Ticket(grantId, ciphertext, expiresAt);
            }
            if (expiresAt.isAfter(reservation.binding().run().deadlineAt())) throw invalid();
        }
        @Override public String toString() { return "ToolExecutionGrant.EvidenceVO[redacted]"; }
    }

    private static String sourceFingerprint(AgentRunAuthorization.Request run, URI resource, AgentServiceExecutionGrant.Subject subject) {
        return run.actor().subjectType() == TrustedAgentActor.SubjectType.USER
                ? AgentExecutionGrant.fingerprint(run, resource) : AgentServiceExecutionGrant.fingerprint(run, resource, subject);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool execution grant binding or evidence is invalid"); }
}
