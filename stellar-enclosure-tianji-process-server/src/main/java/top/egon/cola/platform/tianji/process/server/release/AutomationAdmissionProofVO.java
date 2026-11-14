package top.egon.cola.platform.tianji.process.server.release;

import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionQuery.SHA256;
import static top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionQuery.matches;

/** Short-lived host evidence. Verification audit contains only references, versions and digests. */
public record AutomationAdmissionProofVO(String querySha256, String authorizationDecisionId, String authorizationSha256,
        Instant observedAt, Instant validUntil, long trustGeneration, String releaseSignatureEnvelopeSha256,
        String signedPayloadSha256, String dependencyEvidenceSha256, Map<String, Object> verificationAudit) {

    private static final Duration MAX_WINDOW = Duration.ofSeconds(30);
    static final int MAX_ARCHIVE_BYTES = 65_536;
    private static final String REFERENCE = "[A-Za-z0-9][A-Za-z0-9_.:/@-]{0,255}";
    private static final String VERSION = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}";
    private static final Set<String> AUDIT_KEYS = Set.of("authorizationSourceRef", "authorizationVersion", "dependencySourceRef",
            "trustSourceRef", "evidence");
    private static final Set<String> EVIDENCE_KEYS = Set.of("sourceRef", "version", "sha256");

    public AutomationAdmissionProofVO {
        if (!matches(querySha256, SHA256) || !matches(authorizationDecisionId, REFERENCE) || !matches(authorizationSha256, SHA256)
                || !micros(observedAt) || !micros(validUntil) || !validUntil.isAfter(observedAt)
                || Duration.between(observedAt, validUntil).compareTo(MAX_WINDOW) > 0 || trustGeneration <= 0
                || !matches(releaseSignatureEnvelopeSha256, SHA256) || !matches(signedPayloadSha256, SHA256)
                || !matches(dependencyEvidenceSha256, SHA256)) {
            throw invalid();
        }
        verificationAudit = freezeAudit(verificationAudit);
    }

    public void requireCurrent(String expectedQuerySha256, Instant databaseNow) {
        if (!querySha256.equals(expectedQuerySha256) || databaseNow.isBefore(observedAt) || !databaseNow.isBefore(validUntil)) {
            throw new IllegalStateException("Automation admission proof is unavailable or expired");
        }
    }

    private static Map<String, Object> freezeAudit(Map<String, Object> audit) {
        if (audit == null || !AUDIT_KEYS.containsAll(audit.keySet())) throw invalid();
        var immutable = new LinkedHashMap<String, Object>();
        audit.forEach((field, value) -> {
            if ("evidence".equals(field)) {
                if (!(value instanceof List<?> references) || references.size() > 128) throw invalid();
                immutable.put(field, references.stream().map(AutomationAdmissionProofVO::freezeReference).toList());
            } else {
                immutable.put(field, cleanText(value, "authorizationVersion".equals(field) ? VERSION : REFERENCE));
            }
        });
        ProcessTypedJson.canonicalBytes(immutable, MAX_ARCHIVE_BYTES);
        return Map.copyOf(immutable);
    }

    private static Map<String, String> freezeReference(Object value) {
        if (!(value instanceof Map<?, ?> source) || !source.keySet().equals(EVIDENCE_KEYS)) throw invalid();
        return Map.of("sourceRef", cleanText(source.get("sourceRef"), REFERENCE), "version", cleanText(source.get("version"), VERSION),
                "sha256", cleanText(source.get("sha256"), SHA256));
    }

    private static String cleanText(Object value, String pattern) {
        if (!(value instanceof String text) || !matches(text, pattern)) throw invalid();
        return text;
    }

    private static boolean micros(Instant value) {
        return value != null && !value.isBefore(Instant.EPOCH) && value.getNano() % 1_000 == 0;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Automation admission proof is invalid");
    }
}
