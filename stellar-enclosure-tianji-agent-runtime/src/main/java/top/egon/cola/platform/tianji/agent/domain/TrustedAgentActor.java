package top.egon.cola.platform.tianji.agent.domain;

import java.util.Objects;
import java.util.List;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Internal identity created only after the caller has been authenticated and tenant-bound. */
public record TrustedAgentActor(long tenantId, SubjectType subjectType, String subjectId, String issuerFingerprint) {
    public enum SubjectType { USER, SERVICE }

    /** Compatibility namespace for pre-issuer internal callers and records. */
    public TrustedAgentActor(long tenantId, SubjectType subjectType, String subjectId) {
        this(tenantId, subjectType, subjectId, "LEGACY");
    }

    public TrustedAgentActor {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        subjectType = Objects.requireNonNull(subjectType, "subjectType");
        if (subjectId == null || subjectId.isBlank() || subjectId.length() > 256) {
            throw new IllegalArgumentException("subjectId is required and must be at most 256 characters");
        }
        if (issuerFingerprint == null || !("LEGACY".equals(issuerFingerprint)
                || issuerFingerprint.matches("[0-9a-f]{64}"))) throw new IllegalArgumentException("issuer fingerprint is invalid");
        if (!"LEGACY".equals(issuerFingerprint) && (!subjectId.equals(subjectId.trim()) || !safeText(subjectId))) {
            throw new IllegalArgumentException("verified subject is invalid");
        }
        subjectId = subjectId.trim();
    }

    public static TrustedAgentActor verified(long tenantId, SubjectType type, String subjectId, String issuer) {
        if (issuer == null || issuer.length() > 2048 || !safeText(issuer)) throw new IllegalArgumentException("issuer is invalid");
        URI uri;
        try { uri = URI.create(issuer); }
        catch (IllegalArgumentException malformed) { throw new IllegalArgumentException("issuer is invalid"); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException("issuer is invalid");
        try {
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(issuer.getBytes(StandardCharsets.UTF_8)));
            return new TrustedAgentActor(tenantId, type, subjectId, fingerprint);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    /** Stable scoped identity digest; it is not an authorization decision. */
    public String identityFingerprint() {
        try {
            var value = new StringBuilder("TIANJI_ACTOR_IDENTITY_V1:");
            for (var field : List.of(Long.toString(tenantId), subjectType.name(), issuerFingerprint, subjectId)) {
                value.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 is unavailable"); }
    }

    private static boolean safeText(String value) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 32 || character == 127 || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))) return false;
        }
        return true;
    }
}
