package top.egon.cola.platform.tianji.process.server.release;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore.ImmutableReleaseVO;

import java.time.Instant;
import java.util.Map;

import static top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionQuery.SHA256;
import static top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionQuery.matches;

/** Verifies V2 release evidence independently of the existing channel candidate format. */
public final class AutomationReleaseTrustVerifier {
    private static final int MAX_DEPENDENCY_BYTES = 256 * 1024;

    private AutomationReleaseTrustVerifier() { }

    /**
     * Supplies one bounded snapshot from the trusted release-signing and dependency registries.
     * The host adapter owns transport deadlines. The service invokes it outside any Process transaction.
     */
    @FunctionalInterface
    public interface TrustAuthority {
        TrustObservationVO observe(@NotNull @Valid AutomationAdmissionQuery query, @NotNull @Valid ImmutableReleaseVO release);
    }

    public record TrustedKeyVO(String keyId, long tenantId, long generation, String encodedPublicKey,
                               Instant notBefore, Instant notAfter) {
        public TrustedKeyVO {
            if (!matches(keyId, SHA256) || tenantId <= 0 || generation <= 0 || !matches(encodedPublicKey, "[A-Za-z0-9+/]{59}=")
                    || notBefore == null || notAfter == null || !notAfter.isAfter(notBefore)) throw invalid();
        }
    }

    public record SignedEnvelopeVO(String keyId, Instant issuedAt, Instant expiresAt, String signature) {
        public SignedEnvelopeVO {
            if (!matches(keyId, SHA256) || issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)
                    || !matches(signature, "[A-Za-z0-9+/]{86}==")) throw invalid();
        }
    }

    public record TrustObservationVO(TrustedKeyVO key, long currentGeneration, SignedEnvelopeVO envelope,
                                     Map<String, Object> observedDependencies, String payloadSha256) {
        public TrustObservationVO {
            if (key == null || currentGeneration <= 0 || envelope == null || observedDependencies == null
                    || !matches(payloadSha256, SHA256)) throw invalid();
            observedDependencies = freezeDependencies(observedDependencies);
        }

        public String envelopeSha256() {
            return ProcessTypedJson.sha256("AUTOMATION_RELEASE_SIGNATURE_ENVELOPE_V2",
                    ProcessTypedJson.canonicalBytes(Map.of("keyId", envelope.keyId(), "issuedAt", envelope.issuedAt().toString(),
                            "expiresAt", envelope.expiresAt().toString(), "signature", envelope.signature(), "payloadSha256", payloadSha256)));
        }

        public String dependencyEvidenceSha256(AutomationAdmissionQuery query) {
            return ProcessTypedJson.sha256("AUTOMATION_DEPENDENCY_OBSERVATION_V2", ProcessTypedJson.canonicalBytes(
                    Map.of("querySha256", query.sha256(), "dependencyManifestSha256",
                            AutomationModelCompiler.dependencySha256(observedDependencies), "trustGeneration", currentGeneration)));
        }
    }

    public static void verify(ImmutableReleaseVO release, AutomationAdmissionQuery query, AutomationAdmissionProofVO proof,
                       TrustObservationVO observation) {
        byte[] exactPayload = canonicalPayload(release);
        if (observation == null || observation.key().tenantId() != release.tenantId()
                || proof.trustGeneration() != observation.currentGeneration()
                || !payloadSha256(exactPayload).equals(observation.payloadSha256())
                || !proof.signedPayloadSha256().equals(observation.payloadSha256())
                || !proof.releaseSignatureEnvelopeSha256().equals(observation.envelopeSha256())
                || !release.dependencySha256().equals(AutomationModelCompiler.dependencySha256(observation.observedDependencies()))
                || !proof.dependencyEvidenceSha256().equals(observation.dependencyEvidenceSha256(query))) throw invalid();
        requireWindow(proof, observation, proof.observedAt());
        var envelope = observation.envelope();
        byte[] signed = signingPayload(release, envelope.keyId(), envelope.issuedAt(), envelope.expiresAt());
        PlatformReleaseSignatureVerifier.verifyAutomationSignature(signed, observation.key(), envelope,
                observation.currentGeneration(), proof.observedAt());
    }

    public static void requireWindow(AutomationAdmissionProofVO proof, TrustObservationVO observation, Instant databaseNow) {
        if (observation.key().notBefore().isAfter(databaseNow) || !databaseNow.isBefore(observation.key().notAfter())
                || !databaseNow.isBefore(observation.envelope().expiresAt()) || observation.envelope().issuedAt().isAfter(databaseNow)
                || proof.validUntil().isAfter(observation.envelope().expiresAt())
                || proof.validUntil().isAfter(observation.key().notAfter())) {
            throw invalid();
        }
    }

    public static byte[] canonicalPayload(ImmutableReleaseVO release) {
        return ProcessTypedJson.canonicalBytes(Map.of("domain", "AUTOMATION_RELEASE_V2", "tenantId", release.tenantId(),
                "definitionKey", release.rootDefinitionKey(), "releaseVersion", release.releaseVersion(),
                "sourceManifestSha256", release.sourceSha256(), "compiledManifestSha256", release.compiledSha256(),
                "compilerVersion", release.compilerVersion(), "dependencyManifestSha256", release.dependencySha256()));
    }

    /**
     * Ed25519 envelope claims. The hash covers the complete canonical release payload;
     * key identity and the exact validity timestamps are included in the signed bytes.
     */
    public static byte[] signingPayload(ImmutableReleaseVO release, String keyId, Instant issuedAt, Instant expiresAt) {
        if (!matches(keyId, SHA256) || issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)) throw invalid();
        return ProcessTypedJson.canonicalBytes(Map.of("domain", "AUTOMATION_RELEASE_DETACHED_SIGNATURE_V2",
                "keyId", keyId, "issuedAt", issuedAt.toString(), "expiresAt", expiresAt.toString(),
                "payloadSha256", payloadSha256(canonicalPayload(release))));
    }

    public static String payloadSha256(byte[] payload) {
        return PlatformReleaseArtifactValidator.hash(payload);
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Automation release trust evidence is unavailable or inconsistent");
    }

    private static Map<String, Object> freezeDependencies(Map<String, Object> dependencies) {
        ProcessTypedJson.canonicalBytes(dependencies, MAX_DEPENDENCY_BYTES);
        var copy = new java.util.LinkedHashMap<String, Object>();
        dependencies.forEach((key, child) -> copy.put(key, freezeValue(child)));
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static Object freezeValue(Object value) {
        if (value instanceof Map<?, ?> object) {
            var copy = new java.util.LinkedHashMap<>();
            object.forEach((key, child) -> copy.put(key, freezeValue(child)));
            return java.util.Collections.unmodifiableMap(copy);
        }
        if (value instanceof java.util.List<?> list) return list.stream().map(AutomationReleaseTrustVerifier::freezeValue).toList();
        return value;
    }
}
