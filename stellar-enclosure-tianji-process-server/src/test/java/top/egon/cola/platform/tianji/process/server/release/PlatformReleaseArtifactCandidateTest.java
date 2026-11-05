package top.egon.cola.platform.tianji.process.server.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformReleaseArtifactCandidateTest {
    private static final String USAGE = "TIANJI_RELEASE_ARTIFACT_USAGE";
    private static final String INVALID = "TIANJI_RELEASE_ARTIFACT_INVALID";
    private static final String DIGEST = "TIANJI_RELEASE_ARTIFACT_DIGEST_MISMATCH";
    private static final String IO = "TIANJI_RELEASE_ARTIFACT_IO_UNAVAILABLE";
    private static final String OWNER_SHA = "00b1d06cf7d973a32b03f2fdba1a25612892f0afa16ab6381b945f786d92ed7c";
    private static final String PROCESS_SHA = "f65fc8ac24ff2683ab3c3645e4fb60f7003666380767014df51bea10d96b24f5";
    private static final String REVISION = "1".repeat(40);
    private static final String EXPECTED_CANDIDATE_SHA = "ab0999d1b2bc013a09ac635e7647ba398f55e8333f069d12e693cb1468a69813";
    private static final String PAC = "META-INF/permissions/manifest-v1.json";
    private static final String PAC_DUPLICATE_ALIAS = "META-INF/permissions/manifest-v1.jsox";
    private static final String BOOT = "org.springframework.boot.loader.launch.";
    private static final String LAUNCHER_PATH = "org/springframework/boot/loader/launch/";
    private static final String AGENT_PACKAGE = "top.egon.cola.platform.tianji.contract.agent.";
    private static final String INSTRUCTION = "fixture-private-instruction";
    private static final List<String> PENDING = List.of("RELEASE_SIGNING", "OWNER_PUBLICATION", "LIVE_REVOCATION", "REVIEW_AND_ACTIVATION");

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS).build();

    @TempDir
    Path temp;

    @Test
    void offline_cli_entrypoint_is_present() {
        var cli = assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactCli"));
        var run = assertDoesNotThrow(() -> cli.getDeclaredMethod("run", String[].class, OutputStream.class, PrintStream.class));
        assertTrue(Modifier.isStatic(run.getModifiers()));
        assertEquals(int.class, run.getReturnType());
    }

    @Test
    void valid_candidate_is_canonical_and_location_independent() throws Exception {
        var original = fixture("first");
        var result = verify(original);
        assertEquals(0, result.exit(), result.error());
        assertEquals("", result.error());
        var expected = original.expected();
        assertEquals(EXPECTED_CANDIDATE_SHA, expected.sha256());
        assertArrayEquals(expected.line(), result.output());
        assertTrue(result.output().length < 16_384);
        String output = text(result.output());
        assertTrue(output.endsWith("\n"));
        assertEquals(1L, output.chars().filter(c -> c == '\n').count());
        for (String privateValue : List.of(original.root.toString(), "agent/definition.json", INSTRUCTION,
                "BOOT-INF/", "<definitions", "createdAt", "approvedBy", "mtime")) {
            assertFalse(output.contains(privateValue), privateValue);
        }

        var relocated = fixture("second");
        var reordered = JSON.createObjectNode();
        var fields = new ArrayList<>(relocated.input.properties());
        Collections.reverse(fields);
        fields.forEach(field -> reordered.set(field.getKey(), field.getValue()));
        var models = (ArrayNode) reordered.path("process").path("models");
        var reversed = JSON.createArrayNode();
        for (int index = models.size() - 1; index >= 0; index--) reversed.add(models.get(index));
        ((ObjectNode) reordered.path("process")).set("models", reversed);
        Files.writeString(relocated.descriptor, " \n" + JSON.writerWithDefaultPrettyPrinter().writeValueAsString(reordered) + "\n\t");
        var second = invoke(relocated.args("20", "expense", expected.sha256()));
        assertEquals(0, second.exit(), second.error());
        assertArrayEquals(result.output(), second.output());
        assertEquals("", second.error());
        assertRefused(invoke(relocated.args("20", "expense", "0".repeat(64))), 65, DIGEST);

        var failure = new OutputStream() {
            @Override public void write(int value) throws IOException {
                throw new IOException("fixture-private-output-exception");
            }
        };
        var errors = new ByteArrayOutputStream();
        try (var stderr = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            assertEquals(66, PlatformReleaseArtifactCli.run(original.args("20", "expense", null), failure, stderr));
        }
        assertEquals(IO + "\n", text(errors.toByteArray()));
    }

    @Test
    void rejects_invalid_scope_input_and_file_binding() throws Exception {
        var valid = fixture("scope");
        assertRefused(invoke(valid.args("21", "expense", null)), 65, INVALID);
        assertRefused(invoke(valid.args("20", "different", null)), 65, INVALID);
        assertRefused(invoke(valid.args("020", "expense", null)), 64, USAGE);
        assertRefused(invoke(valid.args("9223372036854775808", "expense", null)), 64, USAGE);
        assertRefused(invoke(valid.args("20", "Expense", null)), 64, USAGE);
        assertRefused(invoke(valid.args("20", "expense", "A".repeat(64))), 64, USAGE);
        String[] duplicateArgv = valid.args("20", "expense", "0".repeat(64));
        duplicateArgv[duplicateArgv.length - 2] = "--tenant";
        assertRefused(invoke(duplicateArgv), 64, USAGE);

        var malformed = fixture("descriptor");
        String descriptor = Files.readString(malformed.descriptor);
        for (String content : List.of(
                descriptor.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                descriptor.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
                descriptor.replace("\"schemaVersion\":1", "\"schemaVersion\":1.0"),
                descriptor + "{}",
                "{\"signature\":\"fake-owner-authority\"," + descriptor.substring(1),
                " ".repeat(65_537))) {
            assertNotEquals(descriptor, content);
            Files.writeString(malformed.descriptor, content);
            assertRefused(verify(malformed), 65, INVALID);
        }
        Files.write(malformed.descriptor, new byte[] {(byte) 0xc3, 0x28});
        assertRefused(verify(malformed), 65, INVALID);
        Files.delete(malformed.descriptor);
        assertRefused(verify(malformed), 66, IO);

        var paths = fixture("paths");
        var agent = (ObjectNode) paths.input.path("agent");
        var original = agent.path("inputContract").deepCopy();
        var ref = (ObjectNode) agent.path("inputContract");
        ref.put("path", "../AgentInputDTO.java");
        assertRefused(verifyChanged(paths), 65, INVALID);
        agent.set("inputContract", agent.path("definition").deepCopy());
        assertRefused(verifyChanged(paths), 65, INVALID);
        agent.set("inputContract", original);
        Path source = paths.root.resolve("agent/AgentInputDTO.java");
        Path actual = Files.move(source, source.resolveSibling("real-AgentInputDTO.java"));
        Files.createSymbolicLink(source, actual.getFileName());
        assertRefused(verifyChanged(paths), 65, INVALID);

        Path alias = valid.root.resolveSibling("root-alias");
        Files.createSymbolicLink(alias, valid.root.getFileName());
        String[] linkedRoot = valid.args("20", "expense", null);
        linkedRoot[2] = alias.toString();
        assertRefused(invoke(linkedRoot), 65, INVALID);
    }

    @Test
    void rejects_invalid_models_and_agent_link() throws Exception {
        var model = fixture("model");
        var process = (ObjectNode) model.input.path("process");
        var models = (ArrayNode) process.path("models");
        ((ObjectNode) models.get(0)).put("definitionKey", "differentProcess");
        assertRefused(verifyChanged(model), 65, INVALID);
        ((ObjectNode) models.get(0)).put("definitionKey", "expenseReview");
        ((ObjectNode) models.get(0).path("inputs")).put("amount", "STRING");
        assertRefused(verifyChanged(model), 65, INVALID);
        ((ObjectNode) models.get(0).path("inputs")).put("amount", "INTEGER");
        var cmmn = models.remove(2);
        assertRefused(verifyChanged(model), 65, INVALID);
        models.add(cmmn);

        var link = (ObjectNode) model.input.path("agentLink");
        for (var changed : Map.of("processDefinitionKey", "expenseRouting", "workerElementId", "reserveExpense",
                "topic", "unrelatedTopic", "schemaVersion", "2", "agentDefinitionKey", "otherAgent",
                "agentDefinitionContentSha256", "2".repeat(64)).entrySet()) {
            String previous = link.path(changed.getKey()).stringValue();
            link.put(changed.getKey(), changed.getValue());
            assertRefused(verifyChanged(model), 65, INVALID);
            link.put(changed.getKey(), previous);
        }

        var bpmn = (ObjectNode) models.get(0);
        Path xml = model.root.resolve("models/expense-review.bpmn20.xml");
        byte[] bytes = Files.readAllBytes(xml);
        Files.writeString(xml, text(bytes).replace("waitForCompletion=\"true\"", "waitForCompletion=\"false\""));
        assertRefused(verifyChanged(model), 65, DIGEST);
        ((ObjectNode) bpmn.path("xml")).put("sha256", sha(Files.readAllBytes(xml)));
        assertRefused(verifyChanged(model), 65, INVALID);
        Files.write(xml, bytes);
        ((ObjectNode) bpmn.path("xml")).put("sha256", sha(bytes));
        process.put("manifestSha256", "0".repeat(64));
        assertRefused(verifyChanged(model), 65, DIGEST);
    }

    @Test
    void rejects_changed_or_unapproved_agent_artifacts() throws Exception {
        var fixture = fixture("agent");
        var approved = definition();
        Path definition = fixture.root.resolve("agent/definition.json");
        var agent = (ObjectNode) fixture.input.path("agent");
        var reference = (ObjectNode) agent.path("definition");

        for (String field : List.of("prompts", "skills", "tools")) {
            var changed = (ObjectNode) JSON.valueToTree(approved);
            var dependency = JSON.createArrayNode();
            dependency.addObject().put("key", "unapproved-fixture");
            ((ObjectNode) changed.path("dependencies")).set(field, dependency);
            rewriteDefinition(reference, definition, changed);
            assertRefused(verifyChanged(fixture), 65, INVALID);
        }
        var changed = (ObjectNode) JSON.valueToTree(approved);
        ((ObjectNode) changed.path("policy")).put("maxToolCalls", 1);
        rewriteDefinition(reference, definition, changed);
        assertRefused(verifyChanged(fixture), 65, INVALID);
        ((ObjectNode) changed.path("policy")).put("maxToolCalls", 0);
        ((ObjectNode) changed.path("configuration")).put("remoteAuthority", "fake");
        rewriteDefinition(reference, definition, changed);
        assertRefused(verifyChanged(fixture), 65, INVALID);

        rewriteDefinition(reference, definition, JSON.valueToTree(approved));
        agent.put("authorityState", "APPROVED");
        assertRefused(verifyChanged(fixture), 65, INVALID);
        agent.remove("authorityState");
        agent.put("definitionVersion", "1");
        assertRefused(verifyChanged(fixture), 65, INVALID);
        agent.put("definitionVersion", 1);
        Files.writeString(definition, "{}");
        assertRefused(verifyChanged(fixture), 65, DIGEST);
        rewriteDefinition(reference, definition, JSON.valueToTree(approved));
        var input = (ObjectNode) agent.path("inputContract");
        Path source = fixture.root.resolve("agent/AgentInputDTO.java");
        Files.writeString(source, contractSource(true).replace("record AgentInputDTO(", "record WrongDTO("));
        assertRefused(verifyChanged(fixture), 65, DIGEST);
        input.put("sha256", sha(Files.readAllBytes(source)));
        assertRefused(verifyChanged(fixture), 65, INVALID);
    }

    @Test
    void rejects_inconsistent_or_ambiguous_permission_archives() throws Exception {
        var fixture = fixture("packages");
        var agent = (ObjectNode) fixture.input.path("packages").path("agent");
        var archive = (ObjectNode) agent.path("archive");
        archive.put("sha256", "f".repeat(64));
        assertRefused(verifyChanged(fixture), 65, DIGEST);
        fixture.writeArchive(Role.AGENT, ZipChange.NONE);
        ((ObjectNode) fixture.input.path("packages").path("agent")).put("manifestEntrySha256", "e".repeat(64));
        assertRefused(verifyChanged(fixture), 65, DIGEST);

        for (var change : List.of(ZipChange.MISSING, ZipChange.DUPLICATE, ZipChange.BOOT_ALIAS,
                ZipChange.OVERSIZED, ZipChange.WRONG_PRODUCER, ZipChange.WRONG_MODULE, ZipChange.WRONG_BOOT, ZipChange.MISSING_CLASS)) {
            fixture.writeArchive(Role.AGENT, change);
            assertRefused(verifyChanged(fixture), 65, INVALID);
        }
        fixture.writeArchive(Role.AGENT, ZipChange.NONE);
        fixture.writeArchive(Role.PROCESS, ZipChange.WRONG_BOOT);
        assertRefused(verifyChanged(fixture), 65, INVALID);
    }

    private static void rewriteDefinition(ObjectNode reference, Path path, Object value) throws Exception {
        byte[] bytes = JSON.writeValueAsBytes(value);
        Files.write(path, bytes);
        reference.put("sha256", sha(bytes));
    }

    private Fixture fixture(String name) throws Exception {
        return new Fixture(temp.toRealPath().resolve(name));
    }

    private static Result verify(Fixture fixture) {
        return invoke(fixture.args("20", "expense", null));
    }

    private static Result verifyChanged(Fixture fixture) throws Exception {
        fixture.writeDescriptor();
        return verify(fixture);
    }

    private static Result invoke(String[] args) {
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        try (var stderr = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            int exit = PlatformReleaseArtifactCli.run(args, output, stderr);
            return new Result(exit, output.toByteArray(), text(errors.toByteArray()));
        }
    }

    private static void assertRefused(Result result, int exit, String error) {
        assertEquals(exit, result.exit());
        assertEquals(0, result.output().length);
        assertEquals(error + "\n", result.error());
    }

    private record Result(int exit, byte[] output, String error) { }
    private record Expected(String sha256, byte[] line) { }

    private enum Role {
        AGENT("agent", "tianji-agent-java", "stellar-enclosure-tianji-agent-server",
                "top.egon.cola.platform.tianji.agent.server.TianjiAgentServerApplication",
                List.of("tianji.api.agent.", "tianji.api.authorization.", "tianji.api.memory.", "tianji.api.session.")),
        PROCESS("process", "tianji-process-java", "stellar-enclosure-tianji-process-server",
                "top.egon.cola.platform.tianji.process.server.TianjiProcessServerApplication",
                List.of("tianji.api.process."));

        final String key;
        final String producer;
        final String module;
        final String startClass;
        final List<String> prefixes;

        Role(String key, String producer, String module, String startClass, List<String> prefixes) {
            this.key = key;
            this.producer = producer;
            this.module = module;
            this.startClass = startClass;
            this.prefixes = prefixes;
        }

        String path() { return "dist/" + key + "-exec.jar"; }
    }

    private enum ZipChange { NONE, MISSING, DUPLICATE, BOOT_ALIAS, OVERSIZED, WRONG_PRODUCER, WRONG_MODULE, WRONG_BOOT, MISSING_CLASS }

    private static final class Fixture {
        final Path root;
        final Path descriptor;
        final ObjectNode input;
        final Map<String, String> raw = new LinkedHashMap<>();
        final Map<Role, String> pac = new LinkedHashMap<>();

        Fixture(Path root) throws Exception {
            this.root = Files.createDirectory(root, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            descriptor = Files.createFile(root.resolve("candidate-input.json"),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            create("agent/definition.json", JSON.writeValueAsBytes(definition()));
            create("agent/AgentInputDTO.java", bytes(contractSource(true)));
            create("agent/AgentExecutionResult.java", bytes(contractSource(false)));
            copyModel("/processes/expense-review.bpmn20.xml", "models/expense-review.bpmn20.xml");
            copyModel("/decisions/expense-routing.dmn", "models/expense-routing.dmn");
            copyModel("/cases/expense-exception.cmmn", "models/expense-exception.cmmn");
            var agent = Map.of("definitionKey", "expenseAnalysis", "definitionVersion", 1, "definitionContentSha256", OWNER_SHA,
                    "definition", file("agent/definition.json"), "inputContract", file("agent/AgentInputDTO.java"),
                    "outputContract", file("agent/AgentExecutionResult.java"));
            var models = List.of(
                    model("BPMN", "expenseReview", "models/expense-review.bpmn20.xml", Map.of("amount", "INTEGER", "operationId", "STRING")),
                    model("DMN", "expenseRouting", "models/expense-routing.dmn", Map.of("amount", "INTEGER")),
                    model("CMMN", "expenseException", "models/expense-exception.cmmn", Map.of()));
            input = (ObjectNode) JSON.valueToTree(Map.of("schemaVersion", 1, "tenantId", "20", "channel", "expense",
                    "sourceRevision", REVISION, "agent", agent, "process",
                    Map.of("releaseVersion", "expense-v1", "manifestSha256", PROCESS_SHA, "models", models),
                    "agentLink", link(false), "packages", Map.of()));
            writeArchive(Role.AGENT, ZipChange.NONE);
            writeArchive(Role.PROCESS, ZipChange.NONE);
            writeDescriptor();
        }

        void writeDescriptor() throws IOException {
            Files.write(descriptor, JSON.writeValueAsBytes(input));
        }

        String[] args(String tenant, String channel, String expected) {
            var args = new ArrayList<>(List.of("verify", "--root", root.toString(), "--input", "candidate-input.json",
                    "--tenant", tenant, "--channel", channel));
            if (expected != null) args.addAll(List.of("--expect-sha256", expected));
            return args.toArray(String[]::new);
        }

        void writeArchive(Role role, ZipChange change) throws Exception {
            byte[] manifest = pac(role, change);
            create(role.path(), zip(role, change, manifest));
            pac.put(role, sha(manifest));
            ((ObjectNode) input.path("packages")).set(role.key, JSON.valueToTree(
                    Map.of("archive", file(role.path()), "manifestEntrySha256", pac.get(role))));
        }

        Expected expected() throws Exception {
            var agent = Map.ofEntries(
                    Map.entry("definitionKey", "expenseAnalysis"), Map.entry("definitionVersion", 1),
                    Map.entry("definitionArtifactSha256", raw.get("agent/definition.json")),
                    Map.entry("definitionContentSha256", OWNER_SHA), Map.entry("ownerDigestState", "DECLARED"),
                    Map.entry("inputContract", contract("AgentInputDTO")), Map.entry("outputContract", contract("AgentExecutionResult")),
                    Map.entry("modelRouteRef", "expense-fixture-route"), Map.entry("policyVersionRef", "expense-fixture-policy"),
                    Map.entry("authorityState", "DECLARED"), Map.entry("optionalDependencyState", "NOT_REQUIRED"));
            var models = List.of(
                    verifiedModel("BPMN", "expenseReview", "models/expense-review.bpmn20.xml", Map.of("amount", "INTEGER", "operationId", "STRING")),
                    verifiedModel("CMMN", "expenseException", "models/expense-exception.cmmn", Map.of()),
                    verifiedModel("DMN", "expenseRouting", "models/expense-routing.dmn", Map.of("amount", "INTEGER")));
            var candidate = Map.of("schemaVersion", 1, "status", "ARTIFACT_CHECKED_ONLY", "tenantId", "20", "channel", "expense",
                    "sourceRevision", REVISION, "agent", agent, "process",
                    Map.of("releaseVersion", "expense-v1", "manifestSha256", PROCESS_SHA, "models", models, "state", "ARTIFACT_CHECKED"),
                    "agentLink", link(true), "packages", Map.of("agent", checked(Role.AGENT), "process", checked(Role.PROCESS)),
                    "pendingAuthority", PENDING);
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes("TIANJI_PLATFORM_RELEASE_ARTIFACT_V1"));
            digest.update((byte) 0);
            String hash = HexFormat.of().formatHex(digest.digest(JSON.writeValueAsBytes(candidate)));
            return new Expected(hash, bytes(JSON.writeValueAsString(Map.of("candidate", candidate, "candidateSha256", hash)) + "\n"));
        }

        private Map<String, Object> contract(String name) {
            return Map.of("symbol", AGENT_PACKAGE + name, "version", "1", "artifactSha256", raw.get("agent/" + name + ".java"));
        }

        private Map<String, Object> checked(Role role) {
            return Map.of("producerId", role.producer, "archiveSha256", raw.get(role.path()), "manifestEntrySha256",
                    pac.get(role), "state", "ARTIFACT_CHECKED");
        }

        private void copyModel(String resource, String destination) throws Exception {
            create(destination, new ClassPathResource(resource).getContentAsByteArray());
        }

        private void create(String relative, byte[] bytes) throws Exception {
            Path file = root.resolve(relative);
            Files.createDirectories(file.getParent(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            if (!Files.exists(file)) Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.write(file, bytes);
            raw.put(relative, sha(bytes));
        }

        private Map<String, String> file(String path) { return Map.of("path", path, "sha256", raw.get(path)); }

        private Map<String, Object> model(String type, String key, String path, Map<String, String> inputs) {
            return Map.of("type", type, "definitionKey", key, "xml", file(path), "inputs", inputs);
        }

        private Map<String, Object> verifiedModel(String type, String key, String path, Map<String, String> inputs) {
            return Map.of("type", type, "definitionKey", key, "modelSha256", raw.get(path), "inputs", inputs);
        }
    }

    private static AgentDefinitionSpecDTO definition() {
        return new AgentDefinitionSpecDTO("expenseAnalysis", 1, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "expense-fixture-route", "expense-fixture-policy"),
                new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(20), Duration.ofSeconds(10),
                        1, 0, 16_384, 128, new BigDecimal("0.20"), "USD", 1, Set.of(), null, null),
                Map.of("chatModelBeanName", "expenseFixtureModel", "modelName", "expense-fixture-model",
                        "rootAgentName", "expenseAnalyzer", "agents", List.of(Map.of("name", "expenseAnalyzer", "instruction", INSTRUCTION))));
    }

    private static String contractSource(boolean input) {
        String prefix = "package " + AGENT_PACKAGE.substring(0, AGENT_PACKAGE.length() - 1) + ";\n"
                + "import java.util.List;\nimport java.util.Map;\n";
        if (input) return prefix + "public record AgentInputDTO(String text, List<String> artifactRefs, Map<String, Object> taskData) {}\n";
        return prefix + "public record AgentExecutionResult(String runId, AgentRunStatusEnum status, Map<String, Object> output, "
                + "String artifactRef, String outputSchemaVersion, AgentUsageDTO usage, String errorCode, String sideEffectOutcome) {}\n";
    }

    private static Map<String, Object> link(boolean checked) {
        var link = new LinkedHashMap<String, Object>();
        link.put("processDefinitionKey", "expenseReview");
        link.put("workerElementId", "agentStart");
        link.put("topic", "agentStart");
        link.put("schemaVersion", "1");
        link.put("agentDefinitionKey", "expenseAnalysis");
        link.put("agentDefinitionVersion", 1);
        link.put("agentDefinitionContentSha256", OWNER_SHA);
        if (checked) link.put("state", "ARTIFACT_CHECKED");
        return link;
    }

    private static byte[] pac(Role role, ZipChange change) throws Exception {
        if (change == ZipChange.OVERSIZED) return bytes(" ".repeat(1_048_577));
        var producer = Map.of("id", change == ZipChange.WRONG_PRODUCER ? "foreign-java" : role.producer,
                "kind", "JAVA", "expectedModules", List.of(role.module),
                "scannedModules", List.of(change == ZipChange.WRONG_MODULE ? "other-module" : role.module),
                "ownedKeyPrefixes", role.prefixes, "snapshotMode", "FULL", "clearIntent", false);
        return JSON.writeValueAsBytes(Map.of("schemaVersion", "1.0",
                "application", Map.of("applicationCode", "tianji", "businessCode", "xingyuan"), "producer", producer,
                "artifact", Map.of("buildId", "finite-a1-fixture", "commit", REVISION, "repository", "tianji-service", "version", "0.0.1-SNAPSHOT"),
                "messages", List.of(), "permissionDefinitions", List.of(), "permissionReferences", List.of(),
                "resourceReferences", List.of(), "resources", List.of()));
    }

    private static byte[] zip(Role role, ZipChange change, byte[] manifest) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            String start = change == ZipChange.WRONG_BOOT ? Role.PROCESS.startClass : role.startClass;
            if (change == ZipChange.WRONG_BOOT && role == Role.PROCESS) start = Role.AGENT.startClass;
            entry(zip, "META-INF/MANIFEST.MF", bytes("Manifest-Version: 1.0\r\nMain-Class: " + BOOT + "JarLauncher\r\n"
                    + "Start-Class: " + start + "\r\nSpring-Boot-Classes: BOOT-INF/classes/\r\nSpring-Boot-Lib: BOOT-INF/lib/\r\n\r\n"));
            if (change != ZipChange.MISSING) entry(zip, PAC, manifest);
            if (change == ZipChange.BOOT_ALIAS) entry(zip, "BOOT-INF/classes/" + PAC, manifest);
            if (change == ZipChange.DUPLICATE) entry(zip, PAC_DUPLICATE_ALIAS, manifest);
            entry(zip, LAUNCHER_PATH + "JarLauncher.class", bytes("synthetic-jar-launcher"));
            entry(zip, LAUNCHER_PATH + "PropertiesLauncher.class", bytes("synthetic-properties-launcher"));
            if (change != ZipChange.MISSING_CLASS) {
                entry(zip, "BOOT-INF/classes/" + role.startClass.replace('.', '/') + ".class", bytes("synthetic-start-class"));
            }
        }
        byte[] archive = output.toByteArray();
        if (change == ZipChange.DUPLICATE) {
            // ZipOutputStream rejects duplicate names; alter same-length local and central aliases after writing.
            byte[] alias = bytes(PAC_DUPLICATE_ALIAS);
            int replacements = 0;
            for (int index = 0; index <= archive.length - alias.length; index++) {
                if (java.util.Arrays.equals(archive, index, index + alias.length, alias, 0, alias.length)) {
                    archive[index + alias.length - 1] = (byte) 'n';
                    replacements++;
                }
            }
            assertEquals(2, replacements);
        }
        return archive;
    }

    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        var item = new ZipEntry(name);
        var crc = new CRC32();
        crc.update(bytes);
        item.setMethod(ZipEntry.STORED);
        item.setTimeLocal(LocalDateTime.of(2024, 1, 1, 0, 0));
        item.setSize(bytes.length);
        item.setCompressedSize(bytes.length);
        item.setCrc(crc.getValue());
        zip.putNextEntry(item);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static String text(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }
}
