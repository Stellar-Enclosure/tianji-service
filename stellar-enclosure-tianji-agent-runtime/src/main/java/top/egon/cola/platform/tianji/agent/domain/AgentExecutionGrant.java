package top.egon.cola.platform.tianji.agent.domain;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/** Owner-sealed evidence for one immutable execution intent; no USER bearer is stored. */
public final class AgentExecutionGrant {
    private AgentExecutionGrant() { }

    public record Ticket(String grantId, String ciphertext, Instant expiresAt) {
        public Ticket {
            if (grantId == null || !grantId.matches("9[0-9]{62}") || ciphertext == null || ciphertext.length() > 32_768
                    || !ciphertext.matches("ENC:v1:[A-Za-z0-9][A-Za-z0-9._-]{0,63}:[A-Za-z0-9_-]+:[A-Za-z0-9_-]+")
                    || expiresAt == null) throw new IllegalArgumentException("EXECUTION_GRANT_TICKET_INVALID");
        }
        @Override public String toString() { return "AgentExecutionGrant.Ticket[redacted]"; }
    }

    public record Reservation(AgentRunAuthorization.Request authorization, URI resource, long epoch, String bindingSha256) {
        public Reservation {
            Objects.requireNonNull(authorization, "authorization");
            if (epoch < 1 || bindingSha256 == null || !bindingSha256.equals(fingerprint(authorization, resource))) {
                throw new IllegalArgumentException("EXECUTION_GRANT_RESERVATION_INVALID");
            }
        }
        @Override public String toString() { return "AgentExecutionGrant.Reservation[redacted]"; }
    }

    /** Length-delimited V1 encoding avoids ambiguity between identity and execution fields. */
    public static String fingerprint(AgentRunAuthorization.Request value, URI resource) {
        Objects.requireNonNull(value, "authorization");
        if (value.actor().subjectType() != TrustedAgentActor.SubjectType.USER || "LEGACY".equals(value.actor().issuerFingerprint())
                || resource == null || !"https".equalsIgnoreCase(resource.getScheme()) || resource.getHost() == null
                || resource.getUserInfo() != null || resource.getQuery() != null || resource.getFragment() != null
                || !resource.normalize().equals(resource) || resource.toASCIIString().length() > 2048) {
            throw new IllegalArgumentException("EXECUTION_GRANT_BINDING_INVALID");
        }
        try {
            if (value.runId().length() != 36 || !java.util.UUID.fromString(value.runId()).toString().equals(value.runId())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("EXECUTION_GRANT_BINDING_INVALID"); }
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                for (String field : new String[]{"tianji-agent-execution-v1", AgentRunAuthorization.PURPOSE, resource.toASCIIString(),
                        Long.toString(value.actor().tenantId()), value.actor().subjectType().name(), value.actor().issuerFingerprint(),
                        value.actor().subjectId(), value.runId(), value.definitionKey(), Long.toString(value.definitionVersion()),
                        value.definitionSha256(), value.requestSha256(), value.snapshotSha256(), value.policyVersionRef(),
                        Boolean.toString(value.sessionRequired()), value.deadlineAt().toString()}) {
                    byte[] encoded = field.getBytes(StandardCharsets.UTF_8);
                    output.writeInt(encoded.length); output.write(encoded);
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("EXECUTION_GRANT_DIGEST_UNAVAILABLE");
        }
    }
}
