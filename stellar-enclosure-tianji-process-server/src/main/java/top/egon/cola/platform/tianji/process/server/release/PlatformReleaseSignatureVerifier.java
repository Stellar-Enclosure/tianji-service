package top.egon.cola.platform.tianji.process.server.release;

import tools.jackson.databind.JsonNode;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.AgentPublication;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ArtifactRef;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ContractRef;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.DeploymentManifest;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ProcessLink;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactValidator.candidateSha256;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactValidator.canonical;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactValidator.field;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactValidator.hash;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactValidator.integer;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactValidator.parse;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactValidator.shape;

final class PlatformReleaseSignatureVerifier {
    static final String IO_UNAVAILABLE = "TIANJI_RELEASE_SIGNATURE_IO_UNAVAILABLE";
    static final String SNAPSHOT_IO_UNAVAILABLE = "TIANJI_RELEASE_PROCESS_SNAPSHOT_IO_UNAVAILABLE";

    private static final String SNAPSHOT_INVALID = "TIANJI_RELEASE_PROCESS_SNAPSHOT_INVALID";
    private static final String SNAPSHOT_DIGEST_MISMATCH = "TIANJI_RELEASE_PROCESS_SNAPSHOT_DIGEST_MISMATCH";
    private static final String SNAPSHOT_MISMATCH = "TIANJI_RELEASE_PROCESS_SNAPSHOT_MISMATCH";
    private static final int SNAPSHOT_LIMIT = 65_536;
    private static final String SNAPSHOT_UTC =
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z";

    private static final String INVALID = "TIANJI_RELEASE_SIGNATURE_INVALID";
    private static final String DIGEST_MISMATCH = "TIANJI_RELEASE_SIGNATURE_DIGEST_MISMATCH";
    private static final String TRUST_REJECTED = "TIANJI_RELEASE_SIGNATURE_TRUST_REJECTED";
    private static final String SIGNATURE_MISMATCH = "TIANJI_RELEASE_SIGNATURE_MISMATCH";
    private static final String ALGORITHM = "Ed25519";
    private static final String PURPOSE = "RELEASE_CANDIDATE_ARTIFACT";
    private static final String ROLE = "RELEASE_ARTIFACT_SIGNER";
    private static final String DOMAIN = "TIANJI_PLATFORM_RELEASE_DETACHED_SIGNATURE_V1";
    private static final String ARTIFACT_CHECKED = "ARTIFACT_CHECKED";
    private static final String SHA = "[0-9a-f]{64}";
    private static final String PROCESS_KEY = "[A-Za-z_][A-Za-z0-9_.-]{0,127}";
    private static final String ARTIFACT_KEY = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}";
    private static final String UTC_SECONDS = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z";
    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final byte[] SPKI_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");
    private static final List<String> CANDIDATE_PENDING = List.of("RELEASE_SIGNING", "OWNER_PUBLICATION",
            "LIVE_REVOCATION", "REVIEW_AND_ACTIVATION");
    private static final List<String> PROOF_PENDING = List.of("CURRENT_SIGNER_AUTHORITY", "OWNER_PUBLICATION",
            "LIVE_REVOCATION", "REVIEW_AND_ACTIVATION");
    private static final List<String> SCOPE_FIELDS = List.of("purpose", "algorithm", "role", "tenantId", "channel", "keyId");
    // Both signs of each y are covered. The JDK verifies the equation even for low-order keys.
    private static final Set<BigInteger> LOW_ORDER_Y = Set.of(BigInteger.ZERO, BigInteger.ONE,
            BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(20)),
            new BigInteger("2707385501144840649318225287225658788936804267575313519463743609750303402022"),
            new BigInteger("55188659117513257062467267217118295137698188065244968500265048394206261417927"));

    private PlatformReleaseSignatureVerifier() { }

    static byte[] verify(Path root, String candidatePath, String signaturePath, String trustPath,
                         String expectedCandidateSha, String expectedTrustSha, long tenant, String channel, Clock clock,
                         ProcessSnapshotInputDTO snapshot) {
        try {
            var input = new PlatformReleaseArtifactValidator(root);
            byte[] candidateBytes = input.readSignatureInput(candidatePath, 1_048_576);
            byte[] signatureBytes = input.readSignatureInput(signaturePath, 8_192);
            byte[] trustBytes = input.readSignatureInput(trustPath, 8_192);
            var result = verifyBytes(candidateBytes, signatureBytes, trustBytes, expectedCandidateSha, expectedTrustSha,
                    tenant, channel, clock);
            if (snapshot == null) return canonicalLine(result.payload());
            return canonicalLine(compareSnapshot(input, snapshot, result));
        } catch (PlatformReleaseArtifactValidator.ValidationFailure failure) {
            throw failure.exitCode() == 66 ? unavailable() : invalid();
        } catch (IllegalArgumentException malformed) {
            throw invalid();
        } catch (IOException | UnsupportedOperationException | SecurityException | ProviderException failure) {
            throw unavailable();
        }
    }

    static VerifiedCandidate verifyBytes(byte[] candidateBytes, byte[] signatureBytes, byte[] trustBytes,
                                         String expectedCandidateSha, String expectedTrustSha, long tenant,
                                         String channel, Clock clock) {
        if (!bounded(candidateBytes, 1_048_576) || !bounded(signatureBytes, 8_192) || !bounded(trustBytes, 8_192)) {
            throw invalid();
        }
        try {
            if (!expectedTrustSha.equals(hash(trustBytes))) throw rejected();

            var envelope = parse(candidateBytes);
            shape(envelope, "candidateSha256", "candidate");
            String statedCandidateSha = digestField(envelope, "candidateSha256");
            var candidate = envelope.get("candidate");
            validateCandidate(candidate);
            var sidecar = parse(signatureBytes);
            shape(sidecar, "claims", "signature");
            var claims = claims(sidecar.get("claims"));
            byte[] signature = base64(field(sidecar, "signature"), 64);

            Object payload = canonicalValue(candidate);
            if (!Arrays.equals(candidateBytes, canonicalLine(Map.of("candidateSha256", statedCandidateSha, "candidate", payload)))) {
                throw invalid();
            }
            String candidateSha = candidateSha256(payload);
            String envelopeSha = hash(candidateBytes);
            if (!candidateSha.equals(statedCandidateSha) || !candidateSha.equals(expectedCandidateSha)
                    || !candidateSha.equals(claims.candidateSha()) || !envelopeSha.equals(claims.envelopeSha())) {
                throw new SignatureFailure(65, DIGEST_MISMATCH);
            }

            var trust = trust(parse(trustBytes));
            String tenantText = Long.toString(tenant);
            if (!tenantText.equals(field(candidate, "tenantId")) || !channel.equals(field(candidate, "channel"))
                    || !trust.scope().equals(claims.scope()) || !tenantText.equals(trust.scope().get("tenantId"))
                    || !channel.equals(trust.scope().get("channel")) || !ALGORITHM.equals(trust.scope().get("algorithm"))
                    || !PURPOSE.equals(trust.scope().get("purpose")) || !ROLE.equals(trust.scope().get("role"))) {
                throw rejected();
            }

            PublicKey key = publicKey(trust);
            Instant now = clock.instant();
            if (trust.notBefore().isAfter(claims.issuedAt()) || claims.issuedAt().isAfter(now)
                    || !now.isBefore(claims.expiresAt()) || claims.expiresAt().isAfter(trust.notAfter())) {
                throw rejected();
            }
            verifySignature(key, claims.canonicalClaims(), signature);

            var process = candidate.get("process");
            return new VerifiedCandidate(candidateSha, envelopeSha, field(candidate, "sourceRevision"), tenantText,
                    channel, trust.scope().get("keyId"), expectedTrustSha, claims.issuedAt(), claims.expiresAt(),
                    field(process, "releaseVersion"), field(process, "manifestSha256"), models(process.get("models"), "modelSha256"),
                    deployment(candidate));
        } catch (PlatformReleaseArtifactValidator.ValidationFailure | IllegalArgumentException malformed) {
            throw invalid();
        } catch (UnsupportedOperationException | SecurityException | ProviderException unavailable) {
            throw unavailable();
        }
    }

    private static DeploymentManifest deployment(JsonNode candidate) {
        var agent = candidate.get("agent");
        var link = candidate.get("agentLink");
        var packages = candidate.get("packages");
        return new DeploymentManifest(
                new AgentPublication(field(agent, "definitionKey"), integer(agent, "definitionVersion"),
                        field(agent, "definitionArtifactSha256"), field(agent, "definitionContentSha256"),
                        field(agent, "modelRouteRef"), field(agent, "policyVersionRef"),
                        contractRef(agent.get("inputContract")), contractRef(agent.get("outputContract"))),
                new ProcessLink(field(link, "processDefinitionKey"), field(link, "workerElementId"),
                        field(link, "topic"), field(link, "schemaVersion")),
                artifactRef(packages.get("agent")), artifactRef(packages.get("process")));
    }

    private static ContractRef contractRef(JsonNode contract) {
        return new ContractRef(field(contract, "version"), field(contract, "artifactSha256"));
    }

    private static ArtifactRef artifactRef(JsonNode artifact) {
        return new ArtifactRef(field(artifact, "archiveSha256"), field(artifact, "manifestEntrySha256"));
    }

    private static boolean bounded(byte[] bytes, int maximum) {
        return bytes != null && bytes.length > 0 && bytes.length <= maximum;
    }

    private static Map<String, Object> compareSnapshot(PlatformReleaseArtifactValidator input,
                                                       ProcessSnapshotInputDTO reference, VerifiedCandidate signature) {
        try {
            byte[] bytes = input.readSignatureInput(reference.path(), SNAPSHOT_LIMIT);
            String snapshotSha = hash(bytes);
            if (!snapshotSha.equals(reference.expectedSha())) throw new SignatureFailure(65, SNAPSHOT_DIGEST_MISMATCH);

            var snapshot = parse(bytes);
            shape(snapshot, "releaseVersion", "manifestSha256", "createdAt", "models");
            var published = new ProcessReleaseDTO(field(snapshot, "releaseVersion"), field(snapshot, "manifestSha256"),
                    snapshotCreatedAt(snapshot), models(snapshot.get("models"), "contentSha256"));
            if (!signature.matchesProcess(published.releaseVersion(), published.manifestSha256(), published.models())) {
                throw new SignatureFailure(65, SNAPSHOT_MISMATCH);
            }

            var proof = new HashMap<>(signature.payload());
            proof.put("status", "PS_RELEASE_SNAPSHOT_MATCHED_OFFLINE");
            proof.put("processSnapshot", Map.of("sha256", snapshotSha, "releaseVersion", published.releaseVersion(),
                    "manifestSha256", published.manifestSha256()));
            return proof;
        } catch (PlatformReleaseArtifactValidator.ValidationFailure failure) {
            throw failure.exitCode() == 66 ? new SignatureFailure(66, SNAPSHOT_IO_UNAVAILABLE)
                    : new SignatureFailure(65, SNAPSHOT_INVALID);
        } catch (DateTimeException | IllegalArgumentException malformed) {
            throw new SignatureFailure(65, SNAPSHOT_INVALID);
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            throw new SignatureFailure(66, SNAPSHOT_IO_UNAVAILABLE);
        }
    }

    private static List<ProcessModelDTO> models(JsonNode models, String digestName) {
        if (!models.isArray() || models.isEmpty() || models.size() > 16) {
            throw new SignatureFailure(65, SNAPSHOT_INVALID);
        }
        var result = new ArrayList<ProcessModelDTO>(models.size());
        for (var model : models) {
            shape(model, "type", "definitionKey", digestName, "inputs");
            var inputs = model.get("inputs");
            if (!inputs.isObject() || inputs.size() > 16) throw new SignatureFailure(65, SNAPSHOT_INVALID);
            var schema = new HashMap<String, ProcessValueTypeEnum>();
            for (String name : inputs.propertyNames()) {
                schema.put(name, ProcessValueTypeEnum.valueOf(field(inputs, name)));
            }
            result.add(new ProcessModelDTO(ProcessModelTypeEnum.valueOf(field(model, "type")),
                    field(model, "definitionKey"), field(model, digestName), schema));
        }
        return result;
    }

    private static Instant snapshotCreatedAt(JsonNode snapshot) {
        String text = field(snapshot, "createdAt");
        if (!text.matches(SNAPSHOT_UTC)) throw new SignatureFailure(65, SNAPSHOT_INVALID);
        var timestamp = LocalDateTime.parse(text.substring(0, text.length() - 1));
        if (timestamp.getYear() < 1) throw new SignatureFailure(65, SNAPSHOT_INVALID);
        return timestamp.toInstant(ZoneOffset.UTC);
    }

    private static void validateCandidate(JsonNode candidate) {
        shape(candidate, "schemaVersion", "status", "tenantId", "channel", "sourceRevision", "agent", "process",
                "agentLink", "packages", "pendingAuthority");
        schemaVersion(candidate);
        expect(candidate, "status", "ARTIFACT_CHECKED_ONLY");
        scopeFields(candidate);
        patterned(candidate, "sourceRevision", "[0-9a-f]{40}");

        var pending = candidate.get("pendingAuthority");
        if (!pending.isArray() || pending.size() != CANDIDATE_PENDING.size()) throw invalid();
        for (int index = 0; index < CANDIDATE_PENDING.size(); index++) {
            var value = pending.get(index);
            if (!value.isString() || !CANDIDATE_PENDING.get(index).equals(value.asString())) throw invalid();
        }
        var agent = candidate.get("agent");
        var link = candidate.get("agentLink");
        validateAgent(agent);
        String processKey = validateAgentLink(link, agent);
        validateProcess(candidate.get("process"), processKey);
        var packages = candidate.get("packages");
        shape(packages, "agent", "process");
        validatePackage(packages.get("agent"), "tianji-agent-java");
        validatePackage(packages.get("process"), "tianji-process-java");
    }

    private static void validateAgent(JsonNode agent) {
        shape(agent, "definitionKey", "definitionVersion", "definitionArtifactSha256", "definitionContentSha256",
                "ownerDigestState", "inputContract", "outputContract", "modelRouteRef", "policyVersionRef",
                "authorityState", "optionalDependencyState");
        patterned(agent, "definitionKey", ARTIFACT_KEY);
        if (integer(agent, "definitionVersion") <= 0) throw invalid();
        digestField(agent, "definitionArtifactSha256");
        digestField(agent, "definitionContentSha256");
        expect(agent, "ownerDigestState", "DECLARED");
        expect(agent, "authorityState", "DECLARED");
        expect(agent, "optionalDependencyState", "NOT_REQUIRED");
        if (field(agent, "modelRouteRef").length() > 256 || field(agent, "policyVersionRef").length() > 256) {
            throw invalid();
        }
        validateContract(agent.get("inputContract"), AgentInputDTO.class);
        validateContract(agent.get("outputContract"), AgentExecutionResult.class);
    }

    private static void validateContract(JsonNode contract, Class<?> symbol) {
        shape(contract, "symbol", "version", "artifactSha256");
        expect(contract, "symbol", symbol.getName());
        if (field(contract, "version").length() > 64) throw invalid();
        digestField(contract, "artifactSha256");
    }

    private static String validateAgentLink(JsonNode link, JsonNode agent) {
        shape(link, "processDefinitionKey", "workerElementId", "topic", "schemaVersion", "agentDefinitionKey",
                "agentDefinitionVersion", "agentDefinitionContentSha256", "state");
        String processKey = patterned(link, "processDefinitionKey", PROCESS_KEY);
        patterned(link, "workerElementId", PROCESS_KEY);
        ProcessModelValidator.nativeWorkerTopic(field(link, "topic"), field(link, "schemaVersion"));
        expect(link, "agentDefinitionKey", field(agent, "definitionKey"));
        if (integer(link, "agentDefinitionVersion") != integer(agent, "definitionVersion")) throw invalid();
        expect(link, "agentDefinitionContentSha256", field(agent, "definitionContentSha256"));
        expect(link, "state", ARTIFACT_CHECKED);
        return processKey;
    }

    private static void validateProcess(JsonNode process, String linkedProcessKey) {
        shape(process, "releaseVersion", "manifestSha256", "state", "models");
        patterned(process, "releaseVersion", ARTIFACT_KEY);
        digestField(process, "manifestSha256");
        expect(process, "state", ARTIFACT_CHECKED);
        var models = process.get("models");
        if (!models.isArray() || models.isEmpty() || models.size() > 16) throw invalid();
        String previous = "";
        boolean linkedProcessPresent = false;
        for (var model : models) {
            shape(model, "type", "definitionKey", "modelSha256", "inputs");
            var type = ProcessModelTypeEnum.valueOf(field(model, "type"));
            String key = patterned(model, "definitionKey", PROCESS_KEY);
            // Type and key are ASCII. A NUL separator preserves A1's separate UTF-8 sort keys.
            String order = type.name() + "\0" + key;
            if (previous.compareTo(order) >= 0) throw invalid();
            previous = order;
            linkedProcessPresent |= type == ProcessModelTypeEnum.BPMN && linkedProcessKey.equals(key);
            digestField(model, "modelSha256");

            var inputs = model.get("inputs");
            if (!inputs.isObject() || inputs.size() > 16 || type == ProcessModelTypeEnum.CMMN && !inputs.isEmpty()) throw invalid();
            for (String name : inputs.propertyNames()) {
                ProcessModelValidator.validateVariable(name);
                ProcessValueTypeEnum.valueOf(field(inputs, name));
            }
        }
        if (!linkedProcessPresent) throw invalid();
    }

    private static void validatePackage(JsonNode value, String producer) {
        shape(value, "archiveSha256", "manifestEntrySha256", "producerId", "state");
        digestField(value, "archiveSha256");
        digestField(value, "manifestEntrySha256");
        expect(value, "producerId", producer);
        expect(value, "state", ARTIFACT_CHECKED);
    }

    private static ReleaseSignatureClaimsDTO claims(JsonNode node) {
        shape(node, "schemaVersion", "purpose", "algorithm", "role", "tenantId", "channel", "keyId",
                "candidateSha256", "candidateEnvelopeSha256", "issuedAt", "expiresAt");
        return new ReleaseSignatureClaimsDTO(scope(node), digestField(node, "candidateSha256"),
                digestField(node, "candidateEnvelopeSha256"), instant(node, "issuedAt"), instant(node, "expiresAt"),
                canonical(canonicalValue(node)));
    }

    private static ReleaseSignatureTrustDTO trust(JsonNode node) {
        shape(node, "schemaVersion", "purpose", "algorithm", "role", "tenantId", "channel", "keyId",
                "publicKey", "notBefore", "notAfter");
        return new ReleaseSignatureTrustDTO(scope(node), field(node, "publicKey"), instant(node, "notBefore"),
                instant(node, "notAfter"));
    }

    private static Map<String, String> scope(JsonNode node) {
        schemaVersion(node);
        scopeFields(node);
        digestField(node, "keyId");
        var scope = new HashMap<String, String>();
        for (String name : SCOPE_FIELDS) scope.put(name, field(node, name));
        return Map.copyOf(scope);
    }

    private static void scopeFields(JsonNode node) {
        String tenant = patterned(node, "tenantId", "[1-9][0-9]{0,18}");
        try {
            Long.parseLong(tenant);
        } catch (NumberFormatException overflow) {
            throw invalid();
        }
        patterned(node, "channel", "[a-z][a-z0-9._-]{0,63}");
    }

    private static void schemaVersion(JsonNode node) {
        if (integer(node, "schemaVersion") != 1) throw invalid();
    }

    private static Instant instant(JsonNode node, String fieldName) {
        String raw = patterned(node, fieldName, UTC_SECONDS);
        try {
            var date = LocalDateTime.parse(raw, UTC_FORMAT);
            if (date.getYear() < 1) throw invalid();
            return date.toInstant(ZoneOffset.UTC);
        } catch (DateTimeException invalidTime) {
            throw invalid();
        }
    }

    private static byte[] base64(String raw, int byteCount) {
        if (raw.length() != 4 * ((byteCount + 2) / 3)) throw invalid();
        try {
            byte[] bytes = Base64.getDecoder().decode(raw);
            if (bytes.length != byteCount || !raw.equals(Base64.getEncoder().encodeToString(bytes))) throw invalid();
            return bytes;
        } catch (IllegalArgumentException malformed) {
            throw invalid();
        }
    }

    private static PublicKey publicKey(ReleaseSignatureTrustDTO trust) {
        return publicKey(trust.publicKey(), trust.scope().get("keyId"));
    }

    private static PublicKey publicKey(String publicKey, String keyId) {
        byte[] encoded = base64(publicKey, 44);
        if (!Arrays.equals(encoded, 0, SPKI_PREFIX.length, SPKI_PREFIX, 0, SPKI_PREFIX.length)
                || !hash(encoded).equals(keyId)) throw rejected();
        try {
            var key = KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(encoded));
            if (!(key instanceof EdECPublicKey edKey) || !ALGORITHM.equals(edKey.getParams().getName())
                    || !Arrays.equals(encoded, edKey.getEncoded()) || LOW_ORDER_Y.contains(edKey.getPoint().getY())) {
                throw rejected();
            }
            return edKey;
        } catch (InvalidKeySpecException malformed) {
            throw rejected();
        } catch (NoSuchAlgorithmException missing) {
            throw unavailable();
        }
    }

    static void verifyAutomationSignature(byte[] payload, AutomationReleaseTrustVerifier.TrustedKeyVO trust,
                                          AutomationReleaseTrustVerifier.SignedEnvelopeVO envelope,
                                          long currentGeneration, Instant verifiedAt) {
        if (trust.generation() != currentGeneration || !trust.keyId().equals(envelope.keyId())
                || trust.notBefore().isAfter(envelope.issuedAt()) || envelope.issuedAt().isAfter(verifiedAt)
                || !verifiedAt.isBefore(envelope.expiresAt()) || envelope.expiresAt().isAfter(trust.notAfter())) {
            throw rejected();
        }
        verifyEd25519(publicKey(trust.encodedPublicKey(), trust.keyId()), payload, base64(envelope.signature(), 64));
    }

    private static void verifySignature(PublicKey key, String claims, byte[] signature) {
        byte[] signed = (DOMAIN + "\u0000" + claims).getBytes(UTF_8);
        verifyEd25519(key, signed, signature);
    }

    private static void verifyEd25519(PublicKey key, byte[] signed, byte[] signature) {
        Signature verifier;
        try {
            verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(key);
            verifier.update(signed);
        } catch (InvalidKeyException malformed) {
            throw rejected();
        } catch (NoSuchAlgorithmException | SignatureException unavailable) {
            throw unavailable();
        }
        try {
            if (verifier.verify(signature)) return;
        } catch (SignatureException malformedSignature) {
            throw new SignatureFailure(65, SIGNATURE_MISMATCH);
        }
        throw new SignatureFailure(65, SIGNATURE_MISMATCH);
    }

    private static Object canonicalValue(JsonNode node) {
        if (node.isObject()) {
            var values = new HashMap<String, Object>();
            for (String name : node.propertyNames()) values.put(name, canonicalValue(node.get(name)));
            return Map.copyOf(values);
        }
        if (node.isArray()) {
            var values = new ArrayList<>();
            for (var item : node) values.add(canonicalValue(item));
            return List.copyOf(values);
        }
        if (node.isString()) return node.asString();
        if (node.isIntegralNumber() && node.canConvertToLong()) return node.longValue();
        throw invalid();
    }

    private static byte[] canonicalLine(Object value) {
        return (canonical(value) + "\n").getBytes(UTF_8);
    }

    private static void expect(JsonNode node, String name, String expected) {
        if (!expected.equals(field(node, name))) throw invalid();
    }

    private static String patterned(JsonNode node, String name, String pattern) {
        String value = field(node, name);
        if (!value.matches(pattern)) throw invalid();
        return value;
    }

    private static String digestField(JsonNode node, String name) {
        return patterned(node, name, SHA);
    }

    private static SignatureFailure invalid() {
        return new SignatureFailure(65, INVALID);
    }

    private static SignatureFailure rejected() {
        return new SignatureFailure(65, TRUST_REJECTED);
    }

    private static SignatureFailure unavailable() {
        return new SignatureFailure(66, IO_UNAVAILABLE);
    }

    record ProcessSnapshotInputDTO(String path, String expectedSha) { }

    private record ReleaseSignatureClaimsDTO(Map<String, String> scope, String candidateSha, String envelopeSha,
                                             Instant issuedAt, Instant expiresAt, String canonicalClaims) { }
    private record ReleaseSignatureTrustDTO(Map<String, String> scope, String publicKey, Instant notBefore,
                                            Instant notAfter) { }
    record VerifiedCandidate(String candidateSha, String envelopeSha, String revision, String tenant, String channel,
                             String keyId, String trustSha, Instant issuedAt, Instant expiresAt, String releaseVersion,
                             String manifestSha, List<ProcessModelDTO> processModels, DeploymentManifest deployment) {
        VerifiedCandidate {
            processModels = List.copyOf(processModels);
        }

        boolean matchesProcess(String version, String digest, List<ProcessModelDTO> models) {
            return releaseVersion.equals(version) && manifestSha.equals(digest)
                    && Set.copyOf(processModels).equals(Set.copyOf(models));
        }

        Map<String, Object> payload() {
            return Map.ofEntries(Map.entry("schemaVersion", 1), Map.entry("status", "ARTIFACT_SIGNATURE_VERIFIED_OFFLINE"),
                    Map.entry("candidateSha256", candidateSha), Map.entry("candidateEnvelopeSha256", envelopeSha),
                    Map.entry("sourceRevision", revision), Map.entry("tenantId", tenant), Map.entry("channel", channel),
                    Map.entry("keyId", keyId), Map.entry("role", ROLE), Map.entry("purpose", PURPOSE),
                    Map.entry("trustSha256", trustSha), Map.entry("issuedAt", issuedAt.toString()),
                    Map.entry("expiresAt", expiresAt.toString()), Map.entry("pendingAuthority", PROOF_PENDING));
        }
    }

    static final class SignatureFailure extends RuntimeException {
        private final int exitCode;
        private final String code;

        private SignatureFailure(int exitCode, String code) {
            super(code);
            this.exitCode = exitCode;
            this.code = code;
        }

        int exitCode() { return exitCode; }
        String code() { return code; }
    }
}
