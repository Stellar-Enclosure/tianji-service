package top.egon.cola.platform.tianji.process.server.identity;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Created by trusted authentication code; request bodies cannot establish this identity. */
public record ProcessActor(long tenantId, SubjectType subjectType, String subjectId, String issuerFingerprint) {
    public enum SubjectType { USER, SERVICE }

    /** Compatibility namespace for pre-issuer internal callers and records. */
    public ProcessActor(long tenantId, SubjectType subjectType, String subjectId) {
        this(tenantId, subjectType, subjectId, "LEGACY");
    }

    public ProcessActor {
        if (tenantId <= 0 || subjectType == null || subjectId == null || subjectId.isBlank()
                || subjectId.length() > 256 || !subjectId.equals(subjectId.trim()) || !safeText(subjectId)
                || issuerFingerprint == null || !("LEGACY".equals(issuerFingerprint)
                || issuerFingerprint.matches("[0-9a-f]{64}"))) throw invalid();
    }

    public static ProcessActor verified(long tenantId, SubjectType type, String subjectId, String issuer) {
        if (issuer == null || issuer.length() > 2048 || !safeText(issuer)) throw invalid();
        URI uri;
        try { uri = URI.create(issuer); }
        catch (IllegalArgumentException malformed) { throw invalid(); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) throw invalid();
        try {
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(issuer.getBytes(StandardCharsets.UTF_8)));
            return new ProcessActor(tenantId, type, subjectId, fingerprint);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
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

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Process actor is invalid");
    }
}
