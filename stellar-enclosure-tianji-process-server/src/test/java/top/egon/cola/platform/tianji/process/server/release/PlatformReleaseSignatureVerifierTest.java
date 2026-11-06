package top.egon.cola.platform.tianji.process.server.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlatformReleaseSignatureVerifierTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final String CANDIDATE_PIN = "0b2c53ee4d2ca2489f8780627f335cfee2b8ce72f9373dff2dabe120dd93f27c";
    static final String TRUST_PIN = "b5597e82a6a47af5a043029fe9e9eca7a67eb7994934a069472c510598703972";
    private static final String WRONG_PIN = "0".repeat(64);
    private static final String USAGE = "TIANJI_RELEASE_SIGNATURE_USAGE";
    private static final String INVALID = "TIANJI_RELEASE_SIGNATURE_INVALID";
    private static final String DIGEST = "TIANJI_RELEASE_SIGNATURE_DIGEST_MISMATCH";
    private static final String TRUST_REJECTED = "TIANJI_RELEASE_SIGNATURE_TRUST_REJECTED";
    private static final String IO_UNAVAILABLE = "TIANJI_RELEASE_SIGNATURE_IO_UNAVAILABLE";
    private static final Instant ISSUED = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2050-01-01T00:00:00Z");
    private static final String MISMATCH = "TIANJI_RELEASE_SIGNATURE_MISMATCH";
    // Public fixture vectors over an actual A1 candidate, independently signed once.
    static final String CANDIDATE = fixture("""
            {
              "candidate": {
                "agent": {
                  "authorityState": "DECLARED",
                  "definitionArtifactSha256": "180ef94e820f94ab3f25f4755a62d6853c6f72110e21729d681d0522ba66c69d",
                  "definitionContentSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "definitionKey": "expenseAnalysis",
                  "definitionVersion": 1,
                  "inputContract": {
                    "artifactSha256": "96dc248fa51f930414675967a0ae710251b251cce4e2c8fdb393b155b45f95ad",
                    "symbol": "top.egon.cola.platform.tianji.contract.agent.AgentInputDTO",
                    "version": "1"
                  },
                  "modelRouteRef": "test-model-🛠",
                  "optionalDependencyState": "NOT_REQUIRED",
                  "outputContract": {
                    "artifactSha256": "43a33a0d21ebd96016bbcf35d4ea8f5b828fbed70629be32a4d086b2e5201897",
                    "symbol": "top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult",
                    "version": "1"
                  },
                  "ownerDigestState": "DECLARED",
                  "policyVersionRef": "v1"
                },
                "agentLink": {
                  "agentDefinitionContentSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "agentDefinitionKey": "expenseAnalysis",
                  "agentDefinitionVersion": 1,
                  "processDefinitionKey": "expenseReview",
                  "schemaVersion": "1",
                  "state": "ARTIFACT_CHECKED",
                  "topic": "agentStart",
                  "workerElementId": "agentStart"
                },
                "channel": "expense",
                "packages": {
                  "agent": {
                    "archiveSha256": "d3e3ef40f8d7459b783919cb2d708cf474375ea74513d62920951f17246d2ad2",
                    "manifestEntrySha256": "570e50b4f113cecd89f5ba0272da9fc1905f5e21c483521f21f9bc7cfde98867",
                    "producerId": "tianji-agent-java",
                    "state": "ARTIFACT_CHECKED"
                  },
                  "process": {
                    "archiveSha256": "91f93c0252a13454ddb0de7ed175358158c9dc99f15fd6796523c369f25e4f43",
                    "manifestEntrySha256": "808e156db1757ba73f0362d5bd564b05535ff4245dd42615010df58b8e1c56c5",
                    "producerId": "tianji-process-java",
                    "state": "ARTIFACT_CHECKED"
                  }
                },
                "pendingAuthority": [
                  "RELEASE_SIGNING",
                  "OWNER_PUBLICATION",
                  "LIVE_REVOCATION",
                  "REVIEW_AND_ACTIVATION"
                ],
                "process": {
                  "manifestSha256": "f65fc8ac24ff2683ab3c3645e4fb60f7003666380767014df51bea10d96b24f5",
                  "models": [
                    {
                      "definitionKey": "expenseReview",
                      "inputs": {
                        "amount": "INTEGER",
                        "operationId": "STRING"
                      },
                      "modelSha256": "5957151c2760507749f0b68763863dde4e42466261e63de10627e1d48ed1c1b6",
                      "type": "BPMN"
                    },
                    {
                      "definitionKey": "expenseException",
                      "inputs": {},
                      "modelSha256": "c2bc0015fcc709b1bd568b635737b4fc7feff80fe343622a9efdc1c9a02f49c1",
                      "type": "CMMN"
                    },
                    {
                      "definitionKey": "expenseRouting",
                      "inputs": {
                        "amount": "INTEGER"
                      },
                      "modelSha256": "94cbd131a086c5e2a87240e99e0c7c88b267d946c184e5c7c1f3bfd694feba74",
                      "type": "DMN"
                    }
                  ],
                  "releaseVersion": "expense-v1",
                  "state": "ARTIFACT_CHECKED"
                },
                "schemaVersion": 1,
                "sourceRevision": "987c7a8769b1f2e92623835ada174cc57c514c63",
                "status": "ARTIFACT_CHECKED_ONLY",
                "tenantId": "20"
              },
              "candidateSha256": "0b2c53ee4d2ca2489f8780627f335cfee2b8ce72f9373dff2dabe120dd93f27c"
            }
            """);
    static final String TRUST = fixture("""
            {
              "algorithm": "Ed25519",
              "channel": "expense",
              "keyId": "d5de20520695a654c99aa4c220b61590d54839c90c03f30da98b96cf5346d7ff",
              "notAfter": "2051-01-01T00:00:00Z",
              "notBefore": "2025-01-01T00:00:00Z",
              "publicKey": "MCowBQYDK2VwAyEAP8gW4S1XAzAJAgzh4uT2+Lw+4GuekQc9bH3BxFwkyz4=",
              "purpose": "RELEASE_CANDIDATE_ARTIFACT",
              "role": "RELEASE_ARTIFACT_SIGNER",
              "schemaVersion": 1,
              "tenantId": "20"
            }
            """);
    static final String SIDECAR = fixture("""
            {
              "claims": {
                "algorithm": "Ed25519",
                "candidateEnvelopeSha256": "450c339e5dca84d4c370cfd63d4a37bf87b67780d6f35b85adad18aff64f37a6",
                "candidateSha256": "0b2c53ee4d2ca2489f8780627f335cfee2b8ce72f9373dff2dabe120dd93f27c",
                "channel": "expense",
                "expiresAt": "2050-01-01T00:00:00Z",
                "issuedAt": "2025-01-01T00:00:00Z",
                "keyId": "d5de20520695a654c99aa4c220b61590d54839c90c03f30da98b96cf5346d7ff",
                "purpose": "RELEASE_CANDIDATE_ARTIFACT",
                "role": "RELEASE_ARTIFACT_SIGNER",
                "schemaVersion": 1,
                "tenantId": "20"
              },
              "signature": "QW9xg9NtQZ6C1yrB/Nlaoo7yvKd16qVcNojR13+OBzDR55obzhbMwoJP1LhtSQjBBblJz+7ydM2KMvGW6EvUCw=="
            }
            """);
    private static final String WRONG_KEY_SIDECAR = fixture("""
            {
              "claims": {
                "algorithm": "Ed25519",
                "candidateEnvelopeSha256": "450c339e5dca84d4c370cfd63d4a37bf87b67780d6f35b85adad18aff64f37a6",
                "candidateSha256": "0b2c53ee4d2ca2489f8780627f335cfee2b8ce72f9373dff2dabe120dd93f27c",
                "channel": "expense",
                "expiresAt": "2050-01-01T00:00:00Z",
                "issuedAt": "2025-01-01T00:00:00Z",
                "keyId": "d5de20520695a654c99aa4c220b61590d54839c90c03f30da98b96cf5346d7ff",
                "purpose": "RELEASE_CANDIDATE_ARTIFACT",
                "role": "RELEASE_ARTIFACT_SIGNER",
                "schemaVersion": 1,
                "tenantId": "20"
              },
              "signature": "h93U7HwyaeZbG4d77phuiPkNKFTXV8wRaQhT2WTxplDzT8RBhKEluJvxuKuXLoRlD6t3HzXfK666BSR+zuUNDg=="
            }
            """);
    private static final String EXPECTED_PROOF = fixture("""
            {
              "candidateEnvelopeSha256": "450c339e5dca84d4c370cfd63d4a37bf87b67780d6f35b85adad18aff64f37a6",
              "candidateSha256": "0b2c53ee4d2ca2489f8780627f335cfee2b8ce72f9373dff2dabe120dd93f27c",
              "channel": "expense",
              "expiresAt": "2050-01-01T00:00:00Z",
              "issuedAt": "2025-01-01T00:00:00Z",
              "keyId": "d5de20520695a654c99aa4c220b61590d54839c90c03f30da98b96cf5346d7ff",
              "pendingAuthority": [
                "CURRENT_SIGNER_AUTHORITY",
                "OWNER_PUBLICATION",
                "LIVE_REVOCATION",
                "REVIEW_AND_ACTIVATION"
              ],
              "purpose": "RELEASE_CANDIDATE_ARTIFACT",
              "role": "RELEASE_ARTIFACT_SIGNER",
              "schemaVersion": 1,
              "sourceRevision": "987c7a8769b1f2e92623835ada174cc57c514c63",
              "status": "ARTIFACT_SIGNATURE_VERIFIED_OFFLINE",
              "tenantId": "20",
              "trustSha256": "b5597e82a6a47af5a043029fe9e9eca7a67eb7994934a069472c510598703972"
            }
            """);

    @TempDir
    Path temp;

    @Test
    void verifiesPinnedDetachedSignature() throws IOException {
        var root = stage("accepted", SIDECAR);
        var result = run(args(root, CANDIDATE_PIN, TRUST_PIN));
        assertEquals(0, result.status());
        assertArrayEquals(EXPECTED_PROOF.getBytes(StandardCharsets.UTF_8), result.stdout());
        assertEquals("", result.stderr());

        var original = JSON.readTree(SIDECAR);
        var claims = (ObjectNode) original.get("claims");
        var algorithm = claims.remove("algorithm");
        claims.set("algorithm", algorithm);
        var reordered = JSON.createObjectNode();
        reordered.set("signature", original.get("signature"));
        reordered.set("claims", claims);
        Files.writeString(root.resolve("candidate.sig.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(reordered));
        var reformatted = run(args(root, CANDIDATE_PIN, TRUST_PIN));
        assertEquals(0, reformatted.status());
        assertArrayEquals(result.stdout(), reformatted.stdout());
        assertEquals("", reformatted.stderr());
    }

    @Test
    void rejectsSubstitutedCandidateTrustAndSignature() throws IOException {
        var root = stage("rejected", WRONG_KEY_SIDECAR);
        var candidate = root.resolve("candidate.json");
        var sidecar = root.resolve("candidate.sig.json");
        var trust = root.resolve("release-trust.json");
        assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, MISMATCH);
        Files.writeString(sidecar, flippedSignature());
        assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, MISMATCH);
        Files.writeString(sidecar, SIDECAR);

        assertRefused(run(args(root, WRONG_PIN, TRUST_PIN)), 65, DIGEST);
        assertRefused(run(args(root, CANDIDATE_PIN, WRONG_PIN)), 65, TRUST_REJECTED);
        String adjustedTrust = TRUST + "\n";
        Files.writeString(trust, adjustedTrust);
        assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, TRUST_REJECTED);
        assertEquals(0, run(args(root, CANDIDATE_PIN, sha(adjustedTrust))).status());
        Files.writeString(trust, TRUST);

        var changed = (ObjectNode) JSON.readTree(CANDIDATE);
        var payload = (ObjectNode) changed.get("candidate");
        payload.put("sourceRevision", "1".repeat(40));
        Files.writeString(candidate, fixture(changed));
        assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, DIGEST);
        String changedPin = sha("TIANJI_PLATFORM_RELEASE_ARTIFACT_V1\0" + JSON.writeValueAsString(payload));
        changed.put("candidateSha256", changedPin);
        Files.writeString(candidate, fixture(changed));
        assertRefused(run(args(root, changedPin, TRUST_PIN)), 65, DIGEST);
        Files.writeString(candidate, CANDIDATE);
        Files.writeString(sidecar, claim("candidateEnvelopeSha256", WRONG_PIN));
        assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, DIGEST);
        Files.writeString(sidecar, claim("candidateSha256", WRONG_PIN));
        assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, DIGEST);

        for (var entry : Map.of("tenantId", "21", "channel", "other", "keyId", WRONG_PIN,
                "role", "RELEASE_ACTIVATOR", "purpose", "OTHER_PURPOSE", "algorithm", "Ed448").entrySet()) {
            Files.writeString(sidecar, claim(entry.getKey(), entry.getValue()));
            assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, TRUST_REJECTED);
        }

        Files.writeString(sidecar, claim("role", "RELEASE_ACTIVATOR"));
        assertRefused(run(args(root, CANDIDATE_PIN, rewriteTrust(trust, "role", "RELEASE_ACTIVATOR"))), 65, TRUST_REJECTED);
        Files.writeString(sidecar, SIDECAR);
        Files.writeString(trust, TRUST);
        String[] wrongTenant = args(root, CANDIDATE_PIN, TRUST_PIN);
        wrongTenant[14] = "21";
        assertRefused(run(wrongTenant), 65, TRUST_REJECTED);
    }

    @Test
    void enforcesExactValidityWindow() throws IOException {
        var root = stage("time", SIDECAR);
        var sidecar = root.resolve("candidate.sig.json");
        var trust = root.resolve("release-trust.json");
        assertRefused(runAt(args(root, CANDIDATE_PIN, TRUST_PIN), ISSUED.minusNanos(1)), 65, TRUST_REJECTED);
        assertEquals(0, runAt(args(root, CANDIDATE_PIN, TRUST_PIN), ISSUED).status());
        assertEquals(0, runAt(args(root, CANDIDATE_PIN, TRUST_PIN), EXPIRES.minusNanos(1)).status());
        assertRefused(runAt(args(root, CANDIDATE_PIN, TRUST_PIN), EXPIRES), 65, TRUST_REJECTED);

        String endsAtExpiry = rewriteTrust(trust, "notAfter", EXPIRES.toString());
        assertEquals(0, runAt(args(root, CANDIDATE_PIN, endsAtExpiry), EXPIRES.minusNanos(1)).status());
        assertRefused(runAt(args(root, CANDIDATE_PIN, endsAtExpiry), EXPIRES), 65, TRUST_REJECTED);
        String endsTooSoon = rewriteTrust(trust, "notAfter", "2049-12-31T23:59:59Z");
        assertRefused(runAt(args(root, CANDIDATE_PIN, endsTooSoon), ISSUED), 65, TRUST_REJECTED);
        String beginsTooLate = rewriteTrust(trust, "notBefore", "2025-01-01T00:00:01Z");
        assertRefused(runAt(args(root, CANDIDATE_PIN, beginsTooLate), ISSUED.plusSeconds(1)), 65, TRUST_REJECTED);
        String reversedTrust = rewriteTrust(trust, "notAfter", "2024-01-01T00:00:00Z");
        assertRefused(runAt(args(root, CANDIDATE_PIN, reversedTrust), ISSUED), 65, TRUST_REJECTED);
        Files.writeString(trust, TRUST);
        Files.writeString(sidecar, claim("expiresAt", ISSUED.toString()));
        assertRefused(runAt(args(root, CANDIDATE_PIN, TRUST_PIN), ISSUED), 65, TRUST_REJECTED);

        for (String malformed : List.of("0000-01-01T00:00:00Z", "2025-02-29T00:00:00Z", "2025-01-01T24:00:00Z",
                "2025-01-01T00:00:60Z", "2025-01-01T00:00:00.0Z", "2025-01-01T00:00:00+00:00")) {
            Files.writeString(sidecar, claim("issuedAt", malformed));
            assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, INVALID);
        }
    }

    @Test
    void rejectsUnsafeFilesAndMalformedInputs() throws IOException {
        var root = stage("malformed", SIDECAR);
        var candidate = root.resolve("candidate.json");
        var sidecar = root.resolve("candidate.sig.json");
        var trust = root.resolve("release-trust.json");

        String[] duplicate = args(root, CANDIDATE_PIN, TRUST_PIN);
        duplicate[15] = "--tenant";
        assertRefused(run(duplicate), 64, USAGE);
        String[] unknown = args(root, CANDIDATE_PIN, TRUST_PIN);
        unknown[15] = "--now";
        assertRefused(run(unknown), 64, USAGE);
        String[] traversal = args(root, CANDIDATE_PIN, TRUST_PIN);
        traversal[4] = "../candidate.json";
        assertRefused(run(traversal), 65, INVALID);
        assertRefused(run(Arrays.copyOf(duplicate, 15)), 64, USAGE);
        assertRefused(run(args(root, "A".repeat(64), TRUST_PIN)), 64, USAGE);

        String[] same = args(root, CANDIDATE_PIN, TRUST_PIN);
        same[8] = "candidate.json";
        assertRefused(run(same), 65, INVALID);
        Files.delete(trust);
        assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 66, IO_UNAVAILABLE);
        Files.createLink(trust, candidate);
        assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, INVALID);
        Files.delete(trust);
        Files.createSymbolicLink(trust, candidate);
        assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, INVALID);
        Files.delete(trust);
        writePrivate(trust, TRUST);
        Files.setPosixFilePermissions(trust, PosixFilePermissions.fromString("rw--w----"));
        assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, INVALID);
        Files.setPosixFilePermissions(trust, PosixFilePermissions.fromString("rw-------"));
        Path alias = Files.createSymbolicLink(temp.toRealPath().resolve("alias"), root);
        assertRefused(run(args(alias, CANDIDATE_PIN, TRUST_PIN)), 65, INVALID);

        for (String badCandidate : List.of(CANDIDATE.stripTrailing(), "\ufeff" + CANDIDATE, CANDIDATE + "{}",
                CANDIDATE.replace("\"tenantId\":\"20\"", "\"tenantId\":20"),
                CANDIDATE.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                CANDIDATE.replace("\"definitionArtifactSha256\":", "\"injected\":0,\"definitionArtifactSha256\":"))) {
            Files.writeString(candidate, badCandidate);
            assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, INVALID);
        }
        Files.write(candidate, new byte[]{(byte) 0xc3, (byte) 0x28});
        assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, INVALID);
        Files.write(candidate, new byte[1_048_577]);
        assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, INVALID);
        Files.writeString(candidate, CANDIDATE);

        for (String malformedSignature : List.of("A".repeat(87), "A".repeat(88), "_".repeat(86) + "==")) {
            var parsed = (ObjectNode) JSON.readTree(SIDECAR);
            parsed.put("signature", malformedSignature);
            Files.writeString(sidecar, fixture(parsed));
            assertRefused(run(args(root, CANDIDATE_PIN, TRUST_PIN)), 65, INVALID);
        }
        Files.writeString(sidecar, SIDECAR);
        String badSpki = Base64.getEncoder().encodeToString(new byte[44]);
        assertRefused(run(args(root, CANDIDATE_PIN, rewriteTrust(trust, "publicKey", badSpki))), 65, TRUST_REJECTED);
        Files.writeString(trust, TRUST);
        assertRejectsLowOrderKeys(root);

        var broken = new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("private path"); }
        };
        var error = new ByteArrayOutputStream();
        assertEquals(66, PlatformReleaseArtifactCli.run(args(root, CANDIDATE_PIN, TRUST_PIN), broken,
                new PrintStream(error, true, StandardCharsets.UTF_8), Clock.fixed(ISSUED, ZoneOffset.UTC)));
        assertEquals(IO_UNAVAILABLE + "\n", error.toString(StandardCharsets.UTF_8));
    }

    private static void assertRejectsLowOrderKeys(Path root) throws IOException {
        String prefix = "302a300506032b6570032100";
        for (String point : List.of("00".repeat(32), "01" + "00".repeat(31),
                "ec" + "ff".repeat(30) + "7f",
                "26e8958fc2b227b045c3f489f2ef98f0d5dfac05d3c63339b13802886d53fc05",
                "c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac037a")) {
            byte[] spki = HexFormat.of().parseHex(prefix + point);
            for (int sign : new int[]{0, 0x80}) {
                spki[spki.length - 1] = (byte) ((spki[spki.length - 1] & 0x7f) | sign);
                String keyId = sha(spki);
                var trust = (ObjectNode) JSON.readTree(TRUST);
                trust.put("keyId", keyId);
                trust.put("publicKey", Base64.getEncoder().encodeToString(spki));
                String trustBytes = fixture(trust);
                Files.writeString(root.resolve("release-trust.json"), trustBytes);

                var sidecar = (ObjectNode) JSON.readTree(claim("keyId", keyId));
                byte[] forged = new byte[64];
                forged[0] = 1;  // R = identity, S = 0 verifies against an identity public key on JDK 25.
                sidecar.put("signature", Base64.getEncoder().encodeToString(forged));
                Files.writeString(root.resolve("candidate.sig.json"), fixture(sidecar));
                assertRefused(run(args(root, CANDIDATE_PIN, sha(trustBytes))), 65, TRUST_REJECTED);
            }
        }
        Files.writeString(root.resolve("release-trust.json"), TRUST);
        Files.writeString(root.resolve("candidate.sig.json"), SIDECAR);
    }

    @Test
    void comparesSignedCandidateWithPinnedPublicProcessSnapshot() throws IOException {
        var root = stage("snapshot-match", SIDECAR);
        String snapshot = fixture(processSnapshot());
        var arguments = snapshotArgs(root, "published.json", writeSnapshot(root, snapshot));
        var first = run(arguments);
        assertEquals(0, first.status());
        assertEquals("", first.stderr());
        var proof = JSON.readTree(first.stdout());
        var expected = (ObjectNode) JSON.readTree(EXPECTED_PROOF);
        expected.put("status", "PS_RELEASE_SNAPSHOT_MATCHED_OFFLINE");
        expected.set("processSnapshot", JSON.valueToTree(Map.of("sha256", sha(snapshot), "releaseVersion", "expense-v1",
                "manifestSha256", processSnapshot().get("manifestSha256").asString())));
        assertEquals(expected, proof);
        assertArrayEquals(first.stdout(), run(arguments).stdout());
        assertArrayEquals(EXPECTED_PROOF.getBytes(StandardCharsets.UTF_8), run(args(root, CANDIDATE_PIN, TRUST_PIN)).stdout());

        var equivalent = processSnapshot();
        equivalent.put("createdAt", "2026-11-05T11:00:00Z");
        ((ArrayNode) equivalent.get("models")).add(((ArrayNode) equivalent.get("models")).remove(0));
        String reformatted = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(equivalent);
        String reformattedSha = writeSnapshot(root, reformatted);
        assertRefused(run(arguments), 65, "TIANJI_RELEASE_PROCESS_SNAPSHOT_DIGEST_MISMATCH");
        var second = run(snapshotArgs(root, "published.json", reformattedSha));
        assertEquals(0, second.status());
        ((ObjectNode) expected.get("processSnapshot")).put("sha256", sha(reformatted));
        assertEquals(expected, JSON.readTree(second.stdout()));
    }

    @Test
    void refusesReleaseManifestModelAndEffectiveInputDrift() throws IOException {
        var root = stage("snapshot-drift", SIDECAR);
        var published = processSnapshot();
        published.put("releaseVersion", "other-release");
        assertSnapshotMismatch(root, published);

        published = processSnapshot();
        // Same named release with the role-rendered artifact's different manifest.
        published.put("manifestSha256", "77b76704ba1d7d63e62c8764457253637a2cab0bfcf5fa2e5f01b45069352075");
        assertSnapshotMismatch(root, published);

        published = processSnapshot();
        ((ObjectNode) published.get("models").get(1)).put("contentSha256", WRONG_PIN);
        assertSnapshotMismatch(root, published);

        published = processSnapshot();
        ((ObjectNode) published.get("models").get(1)).put("definitionKey", "differentProcess");
        assertSnapshotMismatch(root, published);

        published = processSnapshot();
        ((ObjectNode) published.get("models").get(1).get("inputs")).put("amount", "STRING");
        assertSnapshotMismatch(root, published);

        published = processSnapshot();
        ((ArrayNode) published.get("models")).remove(0);
        assertSnapshotMismatch(root, published);

        published = processSnapshot();
        var models = (ArrayNode) published.get("models");
        models.add(((ObjectNode) models.get(1)).deepCopy().put("definitionKey", "extraModel"));
        assertSnapshotMismatch(root, published);
    }

    @Test
    void refusesMalformedPublicSnapshotAndUnauthorizedFields() throws IOException {
        var root = stage("snapshot-invalid", SIDECAR);
        var published = processSnapshot();
        assertInvalidSnapshot(root, fixture(published.deepCopy().put("tenantId", "20")));
        assertInvalidSnapshot(root, fixture(published.deepCopy().put("createdAt", "2026-11-05T24:00:00Z")));
        assertInvalidSnapshot(root, fixture(published.deepCopy().put("createdAt", "2026-11-05T23:59:60Z")));
        assertInvalidSnapshot(root, fixture(published.deepCopy().put("createdAt", "2026-11-05T10:00:00+08:00")));
        assertInvalidSnapshot(root, fixture(published.deepCopy().put("createdAt", 1770000000)));
        assertInvalidSnapshot(root, fixture(published) + "{}");
        assertInvalidSnapshot(root, fixture(published).replaceFirst("\\{", "{\"releaseVersion\":\"expense-v1\","));

        var models = (ArrayNode) published.get("models");
        models.add(models.get(1).deepCopy());
        assertInvalidSnapshot(root, fixture(published));

        published = processSnapshot();
        var model = (ObjectNode) published.get("models").get(1);
        model.set("modelSha256", model.remove("contentSha256"));
        assertInvalidSnapshot(root, fixture(published));
    }

    @Test
    void verifiesSignatureBeforeReadingDistinctBoundedSnapshotFile() throws IOException {
        var root = stage("snapshot-files", WRONG_KEY_SIDECAR);
        assertRefused(run(snapshotArgs(root, "absent.json", WRONG_PIN)), 65, MISMATCH);
        Files.writeString(root.resolve("candidate.sig.json"), SIDECAR);

        assertRefused(run(snapshotArgs(root, "absent.json", WRONG_PIN)), 66, "TIANJI_RELEASE_PROCESS_SNAPSHOT_IO_UNAVAILABLE");
        assertRefused(run(snapshotArgs(root, "../published.json", WRONG_PIN)), 65, "TIANJI_RELEASE_PROCESS_SNAPSHOT_INVALID");
        assertRefused(run(snapshotArgs(root, "candidate.json", sha(CANDIDATE))), 65, "TIANJI_RELEASE_PROCESS_SNAPSHOT_INVALID");
        Files.createLink(root.resolve("candidate-link.json"), root.resolve("candidate.json"));
        assertRefused(run(snapshotArgs(root, "candidate-link.json", sha(CANDIDATE))), 65, "TIANJI_RELEASE_PROCESS_SNAPSHOT_INVALID");
        Files.createSymbolicLink(root.resolve("candidate-symlink.json"), Path.of("candidate.json"));
        assertRefused(run(snapshotArgs(root, "candidate-symlink.json", sha(CANDIDATE))), 65, "TIANJI_RELEASE_PROCESS_SNAPSHOT_INVALID");

        String oversized = "x".repeat(65_537);
        assertRefused(run(snapshotArgs(root, "published.json", writeSnapshot(root, oversized))),
                65, "TIANJI_RELEASE_PROCESS_SNAPSHOT_INVALID");
        assertRefused(run(snapshotArgs(root, "published.json", "invalid")), 64, "TIANJI_RELEASE_PROCESS_SNAPSHOT_USAGE");
        var incomplete = snapshotArgs(root, "published.json", WRONG_PIN);
        assertRefused(run(Arrays.copyOf(incomplete, incomplete.length - 2)), 64, "TIANJI_RELEASE_PROCESS_SNAPSHOT_USAGE");
        incomplete[0] = "verify-signature";
        assertRefused(run(incomplete), 64, USAGE);
    }

    private static ObjectNode processSnapshot() {
        var bpmn = new ProcessModelDTO(ProcessModelTypeEnum.BPMN, "expenseReview",
                "5957151c2760507749f0b68763863dde4e42466261e63de10627e1d48ed1c1b6",
                Map.of("amount", ProcessValueTypeEnum.INTEGER, "operationId", ProcessValueTypeEnum.STRING));
        var cmmn = new ProcessModelDTO(ProcessModelTypeEnum.CMMN, "expenseException",
                "c2bc0015fcc709b1bd568b635737b4fc7feff80fe343622a9efdc1c9a02f49c1", Map.of());
        var dmn = new ProcessModelDTO(ProcessModelTypeEnum.DMN, "expenseRouting",
                "94cbd131a086c5e2a87240e99e0c7c88b267d946c184e5c7c1f3bfd694feba74",
                Map.of("amount", ProcessValueTypeEnum.INTEGER));
        return JSON.valueToTree(new ProcessReleaseDTO("expense-v1",
                "f65fc8ac24ff2683ab3c3645e4fb60f7003666380767014df51bea10d96b24f5",
                Instant.parse("2026-11-05T10:00:00.123456Z"), List.of(dmn, bpmn, cmmn)));
    }

    private static void assertSnapshotMismatch(Path root, ObjectNode document) throws IOException {
        assertRefused(run(snapshotArgs(root, "published.json", writeSnapshot(root, fixture(document)))),
                65, "TIANJI_RELEASE_PROCESS_SNAPSHOT_MISMATCH");
    }

    private static void assertInvalidSnapshot(Path root, String document) throws IOException {
        assertRefused(run(snapshotArgs(root, "published.json", writeSnapshot(root, document))),
                65, "TIANJI_RELEASE_PROCESS_SNAPSHOT_INVALID");
    }

    private static String writeSnapshot(Path root, String document) throws IOException {
        var file = root.resolve("published.json");
        if (Files.exists(file)) Files.writeString(file, document);
        else writePrivate(file, document);
        return sha(document);
    }

    private static String[] snapshotArgs(Path root, String snapshot, String pin) {
        var signature = args(root, CANDIDATE_PIN, TRUST_PIN);
        var result = Arrays.copyOf(signature, signature.length + 4);
        result[0] = "compare-process-snapshot";
        result[signature.length] = "--process-snapshot";
        result[signature.length + 1] = snapshot;
        result[signature.length + 2] = "--expect-process-snapshot-sha256";
        result[signature.length + 3] = pin;
        return result;
    }

    private static String claim(String name, String value) {
        var sidecar = (ObjectNode) JSON.readTree(SIDECAR);
        ((ObjectNode) sidecar.get("claims")).put(name, value);
        return fixture(sidecar);
    }

    private static String rewriteTrust(Path trust, String name, String value) throws IOException {
        var document = (ObjectNode) JSON.readTree(TRUST);
        document.put(name, value);
        String modified = fixture(document);
        Files.writeString(trust, modified);
        return sha(modified);
    }

    private static String flippedSignature() {
        var sidecar = (ObjectNode) JSON.readTree(SIDECAR);
        byte[] bytes = Base64.getDecoder().decode(sidecar.get("signature").asString());
        bytes[0] ^= 1;
        sidecar.put("signature", Base64.getEncoder().encodeToString(bytes));
        return fixture(sidecar);
    }

    private static String sha(String bytes) {
        return sha(bytes.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha(byte[] bytes) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
    }

    private Path stage(String name, String signature) throws IOException {
        var root = Files.createDirectory(temp.toRealPath().resolve(name),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        writePrivate(root.resolve("candidate.json"), CANDIDATE);
        writePrivate(root.resolve("candidate.sig.json"), signature);
        writePrivate(root.resolve("release-trust.json"), TRUST);
        return root;
    }

    private static void writePrivate(Path file, String data) throws IOException {
        Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(file, data);
    }

    private static String[] args(Path root, String candidatePin, String trustPin) {
        return new String[]{"verify-signature", "--root", root.toString(), "--candidate", "candidate.json",
                "--signature", "candidate.sig.json", "--trust", "release-trust.json",
                "--expect-candidate-sha256", candidatePin, "--expect-trust-sha256", trustPin,
                "--tenant", "20", "--channel", "expense"};
    }

    private static OutcomeDTO run(String[] arguments) {
        return runAt(arguments, ISSUED);
    }

    private static OutcomeDTO runAt(String[] arguments, Instant instant) {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        int status = PlatformReleaseArtifactCli.run(arguments, stdout,
                new PrintStream(stderr, true, StandardCharsets.UTF_8), Clock.fixed(instant, ZoneOffset.UTC));
        return new OutcomeDTO(status, stdout.toByteArray(), stderr.toString(StandardCharsets.UTF_8));
    }

    private static void assertRefused(OutcomeDTO result, int status, String code) {
        assertEquals(status, result.status());
        assertArrayEquals(new byte[0], result.stdout());
        assertEquals(code + "\n", result.stderr());
    }

    private static String fixture(String prettyJson) {
        return fixture(JSON.readTree(prettyJson));
    }

    private static String fixture(JsonNode node) {
        return JSON.writeValueAsString(node) + "\n";
    }

    private record OutcomeDTO(int status, byte[] stdout, String stderr) { }
}
