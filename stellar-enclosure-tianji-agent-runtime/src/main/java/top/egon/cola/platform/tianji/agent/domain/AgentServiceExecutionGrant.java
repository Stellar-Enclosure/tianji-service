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
import java.util.UUID;

/** Exact SERVICE identity and sealed evidence for one process-linked execution intent. */
public final class AgentServiceExecutionGrant {
    private AgentServiceExecutionGrant() { }

    public record Subject(String clientId, String appId, String businessCode, String applicationCode,
                          String environment, URI resourceUri) {
        public Subject {
            if (!text(clientId) || !text(appId) || !text(businessCode) || !text(applicationCode) || !text(environment)) {
                throw new IllegalArgumentException("SERVICE_EXECUTION_GRANT_SUBJECT_INVALID");
            }
            resourceUri = secure(resourceUri);
        }
        @Override public String toString() { return "AgentServiceExecutionGrant.Subject[redacted]"; }
    }

    public record Ticket(String grantId, String ciphertext, Instant expiresAt) {
        public Ticket {
            if (grantId == null || !grantId.matches("8[0-9]{62}") || ciphertext == null || ciphertext.length() > 32_768
                    || !ciphertext.matches("ENC:v1:[A-Za-z0-9][A-Za-z0-9._-]{0,63}:[A-Za-z0-9_-]+:[A-Za-z0-9_-]+")
                    || expiresAt == null) throw new IllegalArgumentException("SERVICE_EXECUTION_GRANT_TICKET_INVALID");
        }
        @Override public String toString() { return "AgentServiceExecutionGrant.Ticket[redacted]"; }
    }

    public record Reservation(AgentRunAuthorization.Request authorization, URI resource, Subject subject,
                              long epoch, String bindingSha256) {
        public Reservation {
            Objects.requireNonNull(authorization, "authorization");
            if (epoch < 1 || bindingSha256 == null || !bindingSha256.equals(fingerprint(authorization, resource, subject))) {
                throw new IllegalArgumentException("SERVICE_EXECUTION_GRANT_RESERVATION_INVALID");
            }
        }
        @Override public String toString() { return "AgentServiceExecutionGrant.Reservation[redacted]"; }
    }

    /** Distinct domain and length-delimited fields bind the process snapshot and original service tuple. */
    public static String fingerprint(AgentRunAuthorization.Request value, URI resource, Subject subject) {
        Objects.requireNonNull(value, "authorization");
        Objects.requireNonNull(subject, "subject");
        resource = secure(resource);
        if (value.actor().subjectType() != TrustedAgentActor.SubjectType.SERVICE
                || "LEGACY".equals(value.actor().issuerFingerprint()) || !value.actor().subjectId().equals(subject.clientId())) {
            throw new IllegalArgumentException("SERVICE_EXECUTION_GRANT_BINDING_INVALID");
        }
        try {
            if (value.runId().length() != 36 || !UUID.fromString(value.runId()).toString().equals(value.runId())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("SERVICE_EXECUTION_GRANT_BINDING_INVALID"); }
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                for (String field : new String[]{"tianji-agent-service-execution-v1", AgentRunAuthorization.PURPOSE, resource.toASCIIString(),
                        Long.toString(value.actor().tenantId()), value.actor().subjectType().name(), value.actor().issuerFingerprint(),
                        value.actor().subjectId(), value.runId(), value.definitionKey(), Long.toString(value.definitionVersion()),
                        value.definitionSha256(), value.requestSha256(), value.snapshotSha256(), value.policyVersionRef(),
                        Boolean.toString(value.sessionRequired()), value.deadlineAt().toString(), subject.clientId(), subject.appId(),
                        subject.businessCode(), subject.applicationCode(), subject.environment(), subject.resourceUri().toASCIIString()}) {
                    byte[] encoded = field.getBytes(StandardCharsets.UTF_8);
                    output.writeInt(encoded.length); output.write(encoded);
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SERVICE_EXECUTION_GRANT_DIGEST_UNAVAILABLE");
        }
    }

    private static URI secure(URI value) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null
                || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null
                || !value.normalize().equals(value) || !value.toASCIIString().equals(value.toString())
                || value.toASCIIString().length() > 2048) throw new IllegalArgumentException("SERVICE_EXECUTION_GRANT_URI_INVALID");
        return value;
    }

    private static boolean text(String value) {
        if (value == null || value.isBlank() || value.length() > 128 || !value.equals(value.trim())) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++index)))) return false;
        }
        return true;
    }
}
