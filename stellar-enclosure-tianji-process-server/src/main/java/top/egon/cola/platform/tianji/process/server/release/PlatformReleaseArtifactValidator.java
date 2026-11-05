package top.egon.cola.platform.tianji.process.server.release;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelResource;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.PublishProcessReleaseCommand;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.ModelResource;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.ZipException;

final class PlatformReleaseArtifactValidator {
    private static final int DESCRIPTOR_LIMIT = 64 * 1024;
    private static final int DEFINITION_LIMIT = 1_048_576;
    private static final int CONTRACT_LIMIT = 128 * 1024;
    private static final int ARCHIVE_LIMIT = 512 * 1_048_576;
    private static final int MANIFEST_LIMIT = 1_048_576;
    private static final int MAX_ARCHIVE_ENTRIES = 65_536;
    private static final int XML_BUNDLE_LIMIT = 4 * 1_048_576;
    private static final String PAC_ENTRY = "META-INF/permissions/manifest-v1.json";
    private static final String BOOT_ENTRY = "META-INF/MANIFEST.MF";
    private static final String BOOT_CLASS_DIRECTORY = "BOOT-INF/classes/";
    private static final String BOOT_LAUNCH = "org.springframework.boot.loader.launch.";
    private static final String DOMAIN = "TIANJI_PLATFORM_RELEASE_ARTIFACT_V1";
    private static final String CANDIDATE_STATE = "ARTIFACT_CHECKED";
    private static final String AGENT_KEY = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}";
    private static final String PROCESS_KEY = "[A-Za-z_][A-Za-z0-9_.-]{0,127}";
    private static final String CHANNEL = "[a-z][a-z0-9._-]{0,63}";
    private static final String SHA = "[0-9a-f]{64}";
    private static final Pattern JAVA_PACKAGE = Pattern.compile("package\\s+top\\.egon\\.cola\\.platform\\.tianji\\.contract\\.agent\\s*;");
    private static final Pattern JAVA_IMPORT = Pattern.compile("import\\s+(?:static\\s+)?[A-Za-z_][A-Za-z0-9_.$]*(?:\\.\\*)?\\s*;");
    private static final Comparator<String> UTF8_ORDER = (first, second) -> Arrays.compareUnsigned(
            first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxTokenCount(100_000)
                            .maxNameLength(512).maxStringLength(DEFINITION_LIMIT).maxNumberLength(128).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();
    private static final PackageIdentity AGENT_PACKAGE = new PackageIdentity("tianji-agent-java",
            "stellar-enclosure-tianji-agent-server", "top.egon.cola.platform.tianji.agent.server.TianjiAgentServerApplication");
    private static final PackageIdentity PROCESS_PACKAGE = new PackageIdentity("tianji-process-java",
            "stellar-enclosure-tianji-process-server", "top.egon.cola.platform.tianji.process.server.TianjiProcessServerApplication");

    private final Path root;
    private final UserPrincipal owner;
    private final Set<Path> usedPaths = new HashSet<>();
    private final Set<Object> usedFileKeys = new HashSet<>();

    PlatformReleaseArtifactValidator(Path root) throws IOException {
        if (root == null || !root.isAbsolute() || !root.equals(root.normalize())) throw invalid();
        var current = root.getRoot();
        for (Path component : root) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) throw invalid();
        }
        if (!root.equals(root.toRealPath())) throw invalid();
        var attributes = attributes(root);
        var currentOwner = root.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
        if (!attributes.isDirectory() || !attributes.owner().equals(currentOwner)
                || attributes.permissions().stream().anyMatch(permission -> permission.name().startsWith("GROUP_")
                || permission.name().startsWith("OTHERS_"))) throw invalid();
        if (!attributes.permissions().containsAll(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE))) {
            throw unavailable();
        }
        this.root = root;
        this.owner = attributes.owner();
    }

    static byte[] verify(Path root, String inputRelative, long tenant, String channel, String expectedCandidateSha) {
        if (tenant <= 0 || !matches(channel, CHANNEL) || expectedCandidateSha != null && !matches(expectedCandidateSha, SHA)) {
            throw invalid();
        }
        try {
            return new PlatformReleaseArtifactValidator(root).verifyCandidate(inputRelative, tenant, channel, expectedCandidateSha);
        } catch (ValidationFailure failure) {
            throw failure;
        } catch (ZipException | IllegalArgumentException failure) {
            throw invalid();
        } catch (IOException | UnsupportedOperationException | SecurityException failure) {
            throw unavailable();
        }
    }

    private byte[] verifyCandidate(String inputRelative, long tenant, String channel, String expectedCandidateSha) throws IOException {
        var input = parse(read(open(inputRelative, DESCRIPTOR_LIMIT)));
        shape(input, "schemaVersion", "tenantId", "channel", "sourceRevision", "agent", "process", "agentLink", "packages");
        if (integer(input, "schemaVersion") != 1 || !Long.toString(tenant).equals(field(input, "tenantId"))
                || !channel.equals(field(input, "channel"))) throw invalid();
        String revision = patterned(input, "sourceRevision", "[0-9a-f]{40}");
        var agent = verifyAgent(input.get("agent"));
        var process = verifyProcess(input.get("process"));
        var link = verifyLink(input.get("agentLink"), agent, process.models());
        var packages = input.get("packages");
        shape(packages, "agent", "process");
        var checkedAgentPackage = verifyPackage(packages.get("agent"), AGENT_PACKAGE);
        var checkedProcessPackage = verifyPackage(packages.get("process"), PROCESS_PACKAGE);
        var candidate = Map.of(
                "schemaVersion", 1, "status", "ARTIFACT_CHECKED_ONLY", "tenantId", Long.toString(tenant),
                "channel", channel, "sourceRevision", revision, "agent", agent.payload(), "process", process.payload(),
                "agentLink", link, "packages", Map.of("agent", checkedAgentPackage, "process", checkedProcessPackage),
                "pendingAuthority", List.of("RELEASE_SIGNING", "OWNER_PUBLICATION", "LIVE_REVOCATION", "REVIEW_AND_ACTIVATION"));
        String candidateSha = candidateSha256(candidate);
        if (expectedCandidateSha != null) compare(expectedCandidateSha, candidateSha);
        return (canonical(Map.of("candidateSha256", candidateSha, "candidate", candidate)) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private AgentEvidence verifyAgent(JsonNode agent) throws IOException {
        shape(agent, "definitionKey", "definitionVersion", "definitionContentSha256", "definition", "inputContract", "outputContract");
        String key = patterned(agent, "definitionKey", AGENT_KEY);
        long version = positive(agent, "definitionVersion");
        String ownerHash = patterned(agent, "definitionContentSha256", SHA);
        var definition = checkedFile(agent.get("definition"), DEFINITION_LIMIT);
        var raw = parse(definition.bytes());
        allowedShape(raw, Set.of("definitionKey", "version", "executionMode", "inputSchemaVersion", "outputSchemaVersion",
                "dependencies", "policy", "configuration"), Set.of("codeRef"));
        if (!key.equals(field(raw, "definitionKey")) || version != positive(raw, "version")
                || !"CONFIG".equals(field(raw, "executionMode"))
                || raw.get("codeRef") != null && !raw.get("codeRef").isNull()) throw invalid();

        var dependencies = raw.get("dependencies");
        shape(dependencies, "prompts", "skills", "tools", "modelRouteRef", "policyVersionRef");
        for (String list : List.of("prompts", "skills", "tools")) requireEmptyArray(dependencies.get(list));

        var policy = raw.get("policy");
        allowedShape(policy, Set.of("totalTimeout", "modelIdleTimeout", "toolTimeout", "maxModelCalls", "maxToolCalls",
                "maxInputTokens", "maxOutputTokens", "maxParallelism", "allowedToolVersions"),
                Set.of("maxEstimatedCost", "currency", "retryPolicyRef", "approvalPolicyRef"));
        for (String limit : List.of("maxModelCalls", "maxToolCalls", "maxInputTokens", "maxOutputTokens", "maxParallelism")) {
            integer(policy, limit);
        }
        if (integer(policy, "maxToolCalls") != 0) throw invalid();
        requireEmptyArray(policy.get("allowedToolVersions"));
        for (String timeout : List.of("totalTimeout", "modelIdleTimeout", "toolTimeout")) field(policy, timeout);
        var amount = policy.get("maxEstimatedCost");
        if (amount != null && !amount.isNull() && !amount.isNumber()) throw invalid();

        var config = raw.get("configuration");
        shape(config, "chatModelBeanName", "modelName", "rootAgentName", "agents");
        field(config, "chatModelBeanName");
        field(config, "modelName");
        String rootName = field(config, "rootAgentName");
        var agents = config.get("agents");
        if (!agents.isArray() || agents.size() != 1) throw invalid();
        var single = agents.get(0);
        shape(single, "name", "instruction");
        if (!rootName.equals(field(single, "name"))) throw invalid();
        if (!single.get("instruction").isString() || single.get("instruction").asString().isBlank()) throw invalid();

        var dto = typed(raw, AgentDefinitionSpecDTO.class);
        if (!dto.inputSchemaVersion().equals(field(raw, "inputSchemaVersion"))
                || !dto.outputSchemaVersion().equals(field(raw, "outputSchemaVersion"))
                || !dto.dependencies().modelRouteRef().equals(field(dependencies, "modelRouteRef"))
                || !dto.dependencies().policyVersionRef().equals(field(dependencies, "policyVersionRef"))) throw invalid();
        var inputContract = verifyContract(agent.get("inputContract"), AgentInputDTO.class, dto.inputSchemaVersion());
        var outputContract = verifyContract(agent.get("outputContract"), AgentExecutionResult.class, dto.outputSchemaVersion());
        var payload = Map.ofEntries(
                Map.entry("definitionKey", key), Map.entry("definitionVersion", version),
                Map.entry("definitionArtifactSha256", definition.sha256()), Map.entry("definitionContentSha256", ownerHash),
                Map.entry("ownerDigestState", "DECLARED"), Map.entry("inputContract", inputContract),
                Map.entry("outputContract", outputContract), Map.entry("modelRouteRef", dto.dependencies().modelRouteRef()),
                Map.entry("policyVersionRef", dto.dependencies().policyVersionRef()), Map.entry("authorityState", "DECLARED"),
                Map.entry("optionalDependencyState", "NOT_REQUIRED"));
        return new AgentEvidence(key, version, ownerHash, payload);
    }

    private Map<String, Object> verifyContract(JsonNode ref, Class<?> symbol, String version) throws IOException {
        var source = checkedFile(ref, CONTRACT_LIMIT);
        if (!source.path().getFileName().toString().equals(symbol.getSimpleName() + ".java")) throw invalid();
        String code = utf8(source.bytes());
        int index = tokenStart(code, 0);
        var namespace = JAVA_PACKAGE.matcher(code).region(index, code.length());
        if (!namespace.lookingAt()) throw invalid();
        index = tokenStart(code, namespace.end());
        var imported = JAVA_IMPORT.matcher(code);
        while (imported.region(index, code.length()).lookingAt()) index = tokenStart(code, imported.end());
        var declaration = Pattern.compile("public\\s+record\\s+" + Pattern.quote(symbol.getSimpleName()) + "\\s*\\(")
                .matcher(code).region(index, code.length());
        if (!declaration.lookingAt()) throw invalid();
        return Map.of("symbol", symbol.getName(), "version", version, "artifactSha256", source.sha256());
    }

    private static int tokenStart(String code, int index) {
        while (index < code.length()) {
            if (Character.isWhitespace(code.charAt(index))) {
                index++;
            } else if (code.startsWith("//", index)) {
                index += 2;
                while (index < code.length() && code.charAt(index) != '\n' && code.charAt(index) != '\r') index++;
            } else if (code.startsWith("/*", index)) {
                int end = code.indexOf("*/", index + 2);
                if (end < 0) throw invalid();
                index = end + 2;
            } else {
                return index;
            }
        }
        return index;
    }

    private ProcessEvidence verifyProcess(JsonNode process) throws IOException {
        shape(process, "releaseVersion", "manifestSha256", "models");
        String release = field(process, "releaseVersion");
        String assertedManifest = patterned(process, "manifestSha256", SHA);
        var rawModels = process.get("models");
        if (!rawModels.isArray() || rawModels.isEmpty() || rawModels.size() > 16) throw invalid();

        var validator = new ProcessModelValidator();
        var resources = new ArrayList<ProcessModelResource>();
        var models = new ArrayList<VerifiedModel>();
        var byKey = new HashMap<ModelKey, VerifiedModel>();
        int bytes = 0;
        for (var item : rawModels) {
            shape(item, "type", "definitionKey", "xml", "inputs");
            Type type = Type.valueOf(field(item, "type"));
            String key = patterned(item, "definitionKey", PROCESS_KEY);
            var xml = checkedFile(item.get("xml"), SafeModelXml.MAX_BYTES);
            bytes += xml.bytes().length;
            if (bytes > XML_BUNDLE_LIMIT) throw invalid();
            var declaredInputs = inputs(item.get("inputs"));
            resources.add(new ProcessModelResource(ProcessModelTypeEnum.valueOf(type.name()), key, utf8(xml.bytes()), declaredInputs));

            Map<String, ValueType> schema = new LinkedHashMap<>();
            declaredInputs.forEach((name, value) -> schema.put(name, ValueType.valueOf(value.name())));
            ValidatedProcessModel model = null;
            byte[] validated;
            switch (type) {
                case BPMN -> {
                    model = validator.validateBpmn(xml.bytes(), key);
                    validated = model.xml();
                }
                case DMN -> {
                    model = validator.validateDmn(xml.bytes(), key);
                    if (!schema.equals(model.inputs())) throw invalid();
                    validated = model.xml();
                }
                case CMMN -> {
                    if (!schema.isEmpty()) throw invalid();
                    validated = validator.validateCmmn(xml.bytes(), key).xml();
                }
                default -> throw invalid();
            }
            var verified = new VerifiedModel(new ModelResource(type, key, validated, schema), model, hash(validated));
            if (byKey.putIfAbsent(new ModelKey(type, key), verified) != null) throw invalid();
            models.add(verified);
        }
        new PublishProcessReleaseCommand(release, resources);
        if (models.stream().noneMatch(item -> item.resource().type() == Type.BPMN)) throw invalid();

        for (var entry : models) {
            if (entry.resource().type() != Type.BPMN) continue;
            var bpmn = entry.model();
            var inputs = entry.resource().inputs();
            ValidatedProcessModel decision = null;
            if (!bpmn.decisionTasks().isEmpty()) {
                var companion = byKey.get(new ModelKey(Type.DMN, bpmn.decisionTasks().values().iterator().next()));
                if (companion == null) throw invalid();
                decision = companion.model();
                validator.validateDecisionBinding(bpmn, inputs, decision);
            }
            validator.validateHumanTaskBindings(bpmn, inputs, decision);
            validator.validateWorkerBindings(bpmn, inputs, decision);
            for (var source : bpmn.caseSources().values()) {
                if (!byKey.containsKey(new ModelKey(Type.CMMN, source.caseDefinitionKey()))) throw invalid();
            }
        }

        models.sort(Comparator.comparing((VerifiedModel item) -> item.resource().type().name(), UTF8_ORDER)
                .thenComparing(item -> item.resource().definitionKey(), UTF8_ORDER));
        String actualManifest = ProcessReleaseService.manifestHash(models.stream().map(VerifiedModel::resource).toList());
        compare(assertedManifest, actualManifest);
        var payload = Map.of("releaseVersion", release, "manifestSha256", actualManifest, "state", CANDIDATE_STATE,
                "models", models.stream().map(VerifiedModel::payload).toList());
        return new ProcessEvidence(Map.copyOf(byKey), payload);
    }

    private static Map<String, ProcessValueTypeEnum> inputs(JsonNode json) {
        if (!json.isObject() || json.size() > 16) throw invalid();
        Map<String, ProcessValueTypeEnum> schema = new LinkedHashMap<>();
        for (String key : json.propertyNames()) schema.put(key, ProcessValueTypeEnum.valueOf(field(json, key)));
        return schema;
    }

    private static Map<String, Object> verifyLink(JsonNode link, AgentEvidence agent, Map<ModelKey, VerifiedModel> models) {
        shape(link, "processDefinitionKey", "workerElementId", "topic", "schemaVersion", "agentDefinitionKey",
                "agentDefinitionVersion", "agentDefinitionContentSha256");
        String processKey = patterned(link, "processDefinitionKey", PROCESS_KEY);
        String workerId = patterned(link, "workerElementId", PROCESS_KEY);
        String topic = field(link, "topic");
        String schemaVersion = field(link, "schemaVersion");
        ProcessModelValidator.nativeWorkerTopic(topic, schemaVersion);
        String agentKey = patterned(link, "agentDefinitionKey", AGENT_KEY);
        long agentVersion = positive(link, "agentDefinitionVersion");
        String ownerHash = patterned(link, "agentDefinitionContentSha256", SHA);
        if (!agent.key().equals(agentKey) || agent.version() != agentVersion || !agent.ownerHash().equals(ownerHash)) throw invalid();

        var selected = models.get(new ModelKey(Type.BPMN, processKey));
        long count = models.values().stream().filter(item -> item.resource().type() == Type.BPMN)
                .mapToLong(item -> item.model().messageWaits().size()).sum();
        if (selected == null || count != 1 || selected.model().messageWaits().size() != 1
                || !selected.model().messageWaits().values().iterator().next().workerElementId().equals(workerId)) throw invalid();
        var task = selected.model().workerTasks().get(workerId);
        if (task == null || !topic.equals(task.topic()) || !schemaVersion.equals(task.schemaVersion())
                || task.effectSafety() != WorkerEffect.EXTERNAL_IDEMPOTENT) throw invalid();
        return Map.of("processDefinitionKey", processKey, "workerElementId", workerId, "topic", topic,
                "schemaVersion", schemaVersion, "agentDefinitionKey", agentKey, "agentDefinitionVersion", agentVersion,
                "agentDefinitionContentSha256", ownerHash, "state", CANDIDATE_STATE);
    }

    private Map<String, Object> verifyPackage(JsonNode packaged, PackageIdentity expected) throws IOException {
        shape(packaged, "archive", "manifestEntrySha256");
        var reference = reference(packaged.get("archive"));
        String assertedManifest = patterned(packaged, "manifestEntrySha256", SHA);
        var file = open(reference.path(), ARCHIVE_LIMIT);
        String rawArchiveSha = hash(file);
        compare(reference.sha256(), rawArchiveSha);
        byte[] manifest;
        try (var jar = new JarFile(file.path().toFile(), false)) {
            if (jar.size() > MAX_ARCHIVE_ENTRIES) throw invalid();
            Set<String> required = Set.of(BOOT_ENTRY, PAC_ENTRY,
                    BOOT_CLASS_DIRECTORY + expected.startClass().replace('.', '/') + ".class",
                    BOOT_LAUNCH.replace('.', '/') + "JarLauncher.class",
                    BOOT_LAUNCH.replace('.', '/') + "PropertiesLauncher.class");
            Map<String, JarEntry> entries = new HashMap<>();
            var all = jar.entries();
            while (all.hasMoreElements()) {
                var entry = all.nextElement();
                String name = entry.getName();
                if (name.startsWith("BOOT-INF/") && name.endsWith("/" + PAC_ENTRY)) throw invalid();
                if (required.contains(name) && (entry.isDirectory() || entries.putIfAbsent(name, entry) != null)) throw invalid();
            }
            if (!entries.keySet().equals(required)) throw invalid();
            var attributes = bootAttributes(archiveEntry(jar, entries.get(BOOT_ENTRY)));
            if (!"1.0".equals(attributes.get("Manifest-Version"))
                    || !expected.startClass().equals(attributes.get("Start-Class"))
                    || !(BOOT_LAUNCH + "JarLauncher").equals(attributes.get("Main-Class"))
                    || !BOOT_CLASS_DIRECTORY.equals(attributes.get("Spring-Boot-Classes"))
                    || !"BOOT-INF/lib/".equals(attributes.get("Spring-Boot-Lib"))) throw invalid();
            manifest = archiveEntry(jar, entries.get(PAC_ENTRY));
        }
        verifyUnchanged(file);
        String manifestSha = hash(manifest);
        compare(assertedManifest, manifestSha);
        var pac = parse(manifest);
        var application = pac.get("application");
        var producer = pac.get("producer");
        if (!"1.0".equals(field(pac, "schemaVersion"))
                || !"xingyuan".equals(field(application, "businessCode"))
                || !"tianji".equals(field(application, "applicationCode"))
                || !expected.producer().equals(field(producer, "id")) || !"JAVA".equals(field(producer, "kind"))) throw invalid();
        requireModule(producer.get("expectedModules"), expected.module());
        requireModule(producer.get("scannedModules"), expected.module());
        return Map.of("producerId", expected.producer(), "archiveSha256", rawArchiveSha,
                "manifestEntrySha256", manifestSha, "state", CANDIDATE_STATE);
    }

    private static void requireModule(JsonNode node, String module) {
        if (node == null || !node.isArray() || node.size() != 1 || !node.get(0).isString()
                || !module.equals(node.get(0).asString())) throw invalid();
    }

    private static byte[] archiveEntry(JarFile jar, JarEntry entry) throws IOException {
        if (entry.getSize() < 1 || entry.getSize() > MANIFEST_LIMIT) throw invalid();
        try (var input = jar.getInputStream(entry)) {
            byte[] result = input.readNBytes(MANIFEST_LIMIT + 1);
            if (result.length != entry.getSize()) throw invalid();
            return result;
        }
    }

    /** Reads only Boot main attributes, rejecting duplicates before any JDK manifest parser could log them. */
    private static Map<String, String> bootAttributes(byte[] bytes) {
        var attributes = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        var main = utf8(bytes).lines().iterator();
        String key = null;
        StringBuilder value = new StringBuilder();
        while (main.hasNext()) {
            String line = main.next();
            if (line.isEmpty()) break;
            if (line.charAt(0) == ' ') {
                if (key == null) throw invalid();
                value.append(line, 1, line.length());
                continue;
            }
            if (key != null && attributes.putIfAbsent(key, value.toString()) != null) throw invalid();
            int separator = line.indexOf(": ");
            if (separator < 1 || !line.substring(0, separator).matches("[A-Za-z0-9_-]{1,70}")) throw invalid();
            key = line.substring(0, separator);
            value.setLength(0);
            value.append(line, separator + 2, line.length());
        }
        if (key != null && attributes.putIfAbsent(key, value.toString()) != null) throw invalid();
        return attributes;
    }

    private FileEvidence checkedFile(JsonNode raw, int limit) throws IOException {
        var reference = reference(raw);
        var file = open(reference.path(), limit);
        byte[] bytes = read(file);
        String actual = hash(bytes);
        compare(reference.sha256(), actual);
        return new FileEvidence(file.path(), actual, bytes);
    }

    private static FileReference reference(JsonNode raw) {
        shape(raw, "path", "sha256");
        return new FileReference(field(raw, "path"), patterned(raw, "sha256", SHA));
    }

    byte[] readSignatureInput(String relative, int limit) throws IOException {
        var file = open(relative, limit);
        if (file.attributes().fileKey() == null) throw unavailable();
        return read(file);
    }

    private StagedFile open(String relative, int limit) throws IOException {
        if (relative == null || relative.length() > 512) throw invalid();
        String[] parts = relative.split("/", -1);
        if (parts.length > 16) throw invalid();
        var path = root;
        PosixFileAttributes metadata = null;
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (!part.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) throw invalid();
            path = path.resolve(part);
            metadata = attributes(path);
            if (!metadata.owner().equals(owner) || metadata.permissions().contains(PosixFilePermission.GROUP_WRITE)
                    || metadata.permissions().contains(PosixFilePermission.OTHERS_WRITE)) throw invalid();
            if (index < parts.length - 1) {
                if (!metadata.isDirectory()) throw invalid();
                if (!metadata.permissions().contains(PosixFilePermission.OWNER_EXECUTE)) throw unavailable();
            }
        }
        if (metadata == null || !metadata.isRegularFile() || metadata.size() < 1 || metadata.size() > limit
                || !usedPaths.add(path) || metadata.fileKey() != null && !usedFileKeys.add(metadata.fileKey())) throw invalid();
        if (!metadata.permissions().contains(PosixFilePermission.OWNER_READ)) throw unavailable();
        return new StagedFile(path, metadata, limit);
    }

    private static PosixFileAttributes attributes(Path path) throws IOException {
        var result = Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (result.isSymbolicLink()) throw invalid();
        return result;
    }

    private static void verifyUnchanged(StagedFile file) throws IOException {
        var before = file.attributes();
        var after = attributes(file.path());
        if (!after.isRegularFile() || before.size() != after.size() || !before.owner().equals(after.owner())
                || !before.permissions().equals(after.permissions()) || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())) throw invalid();
    }

    private static byte[] read(StagedFile file) throws IOException {
        try (var input = Files.newInputStream(file.path(), LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(file.limit() + 1);
            if (bytes.length != file.attributes().size()) throw invalid();
            verifyUnchanged(file);
            return bytes;
        }
    }

    private static String hash(StagedFile file) throws IOException {
        var digest = sha256();
        try (InputStream input = Files.newInputStream(file.path(), LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[64 * 1024];
            long size = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                size += count;
                if (size > file.limit() || size > file.attributes().size()) throw invalid();
                digest.update(buffer, 0, count);
            }
            if (size != file.attributes().size()) throw invalid();
        }
        verifyUnchanged(file);
        return HexFormat.of().formatHex(digest.digest());
    }

    static String candidateSha256(Object candidate) {
        var digest = sha256();
        digest.update(DOMAIN.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        return HexFormat.of().formatHex(digest.digest(canonical(candidate).getBytes(StandardCharsets.UTF_8)));
    }

    static String hash(byte[] bytes) {
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw unavailable();
        }
    }

    private static void compare(String expected, String actual) {
        if (!expected.equals(actual)) throw new ValidationFailure(65, "TIANJI_RELEASE_ARTIFACT_DIGEST_MISMATCH");
    }

    private static String utf8(byte[] bytes) {
        try {
            String value = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            if (!value.isEmpty() && value.charAt(0) == '\uFEFF') throw invalid();
            return value;
        } catch (CharacterCodingException failure) {
            throw invalid();
        }
    }

    static JsonNode parse(byte[] bytes) {
        JsonNode value;
        try {
            value = JSON.readTree(utf8(bytes));
        } catch (RuntimeException failure) {
            throw invalid();
        }
        if (value == null || !value.isObject()) throw invalid();
        validateUnicode(value);
        return value;
    }

    private static void validateUnicode(JsonNode node) {
        if (node.isString()) {
            requireUnicode(node.asString());
        } else if (node.isObject()) {
            for (String name : node.propertyNames()) {
                requireUnicode(name);
                if (name.getBytes(StandardCharsets.UTF_8).length > 512) throw invalid();
                validateUnicode(node.get(name));
            }
        } else if (node.isArray()) {
            for (var entry : node) validateUnicode(entry);
        }
    }

    private static void requireUnicode(String value) {
        if (value.codePoints().anyMatch(character -> character >= Character.MIN_SURROGATE
                && character <= Character.MAX_SURROGATE)) throw invalid();
    }

    private static <T> T typed(JsonNode node, Class<T> type) {
        try {
            return JSON.treeToValue(node, type);
        } catch (RuntimeException failure) {
            throw invalid();
        }
    }

    static void shape(JsonNode node, String... names) {
        allowedShape(node, Set.of(names), Set.of());
    }

    private static void allowedShape(JsonNode node, Set<String> required, Set<String> optional) {
        if (node == null || !node.isObject()) throw invalid();
        for (String name : required) {
            if (node.get(name) == null || node.get(name).isNull()) throw invalid();
        }
        if (node.propertyNames().stream().anyMatch(name -> !required.contains(name) && !optional.contains(name))) throw invalid();
    }

    static String field(JsonNode node, String name) {
        if (node == null || !node.isObject()) throw invalid();
        JsonNode value = node.get(name);
        if (value == null || !value.isString()) throw invalid();
        String text = value.asString();
        if (text.isBlank() || !text.equals(text.trim())
                || text.codePoints().anyMatch(character -> character < 32 || character == 127)) throw invalid();
        return text;
    }

    static long integer(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) throw invalid();
        return value.longValue();
    }

    private static long positive(JsonNode node, String name) {
        long value = integer(node, name);
        if (value < 1) throw invalid();
        return value;
    }

    private static String patterned(JsonNode node, String name, String pattern) {
        String value = field(node, name);
        if (!matches(value, pattern)) throw invalid();
        return value;
    }

    private static boolean matches(String value, String pattern) {
        return value != null && value.matches(pattern);
    }

    private static void requireEmptyArray(JsonNode value) {
        if (value == null || !value.isArray() || value.size() != 0) throw invalid();
    }

    static String canonical(Object value) {
        return JSON.writeValueAsString(canonicalValue(value));
    }

    private static Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var ordered = new TreeMap<String, Object>(UTF8_ORDER);
            map.forEach((key, entry) -> ordered.put((String) key, canonicalValue(entry)));
            return ordered;
        }
        if (value instanceof List<?> list) return list.stream().map(PlatformReleaseArtifactValidator::canonicalValue).toList();
        if (value instanceof String || value instanceof Integer || value instanceof Long) return value;
        throw invalid();
    }

    private static ValidationFailure invalid() {
        return new ValidationFailure(65, "TIANJI_RELEASE_ARTIFACT_INVALID");
    }

    private static ValidationFailure unavailable() {
        return new ValidationFailure(66, "TIANJI_RELEASE_ARTIFACT_IO_UNAVAILABLE");
    }

    static final class ValidationFailure extends RuntimeException {
        private final int exitCode;
        private final String code;

        private ValidationFailure(int exitCode, String code) {
            super(code);
            this.exitCode = exitCode;
            this.code = code;
        }

        int exitCode() { return exitCode; }
        String code() { return code; }
    }

    private record FileReference(String path, String sha256) { }
    private record StagedFile(Path path, PosixFileAttributes attributes, int limit) { }
    private record FileEvidence(Path path, String sha256, byte[] bytes) { }
    private record AgentEvidence(String key, long version, String ownerHash, Map<String, Object> payload) { }
    private record ProcessEvidence(Map<ModelKey, VerifiedModel> models, Map<String, Object> payload) { }
    private record ModelKey(Type type, String definitionKey) { }
    private record PackageIdentity(String producer, String module, String startClass) { }

    private record VerifiedModel(ModelResource resource, ValidatedProcessModel model, String modelSha256) {
        Map<String, Object> payload() {
            var inputs = new LinkedHashMap<String, String>();
            resource.inputs().forEach((key, type) -> inputs.put(key, type.name()));
            return Map.of("type", resource.type().name(), "definitionKey", resource.definitionKey(),
                    "modelSha256", modelSha256, "inputs", inputs);
        }
    }
}
