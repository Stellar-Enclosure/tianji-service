package top.egon.cola.platform.tianji.process.server.acceptance;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Explicit private input for the three child JVMs. No environment fallback. */
record AutomationAcceptanceManifestVO(int schemaVersion, boolean runAuthorized, String runId,
        Path checkoutRoot, Path privateRoot, Path outputDirectory, Map<String, String> revisions,
        List<CandidateLockVO> candidates, List<StoreLockVO> stores, List<TenantFixtureVO> tenants,
        LoopbackTlsFilesVO loopbackTls, int phaseTimeoutSeconds) {

    static final Set<PosixFilePermission> FILE_MODE = PosixFilePermissions.fromString("rw-------");
    static final Set<PosixFilePermission> DIR_MODE = PosixFilePermissions.fromString("rwx------");
    static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();
    private static final Pattern JDBC = Pattern.compile("jdbc:postgresql://127\\.0\\.0\\.1:([1-9][0-9]{0,4})/(tianji_it_automation_[a-z0-9_]{3,38})");
    private static final Set<String> REPOSITORIES = Set.of("tianji-service", "Stellar-Enclosure-Components", "tianji-web");

    AutomationAcceptanceManifestVO {
        if (schemaVersion != 1 || !runAuthorized || !matches(runId, "[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
                || phaseTimeoutSeconds < 5 || phaseTimeoutSeconds > 90) throw invalid();
        checkoutRoot = requireCanonical(checkoutRoot);
        privateRoot = requirePrivateDirectory(privateRoot);
        outputDirectory = requirePrivateDirectory(outputDirectory);
        if (privateRoot.startsWith(checkoutRoot.getParent()) || !outputDirectory.startsWith(privateRoot)) throw invalid();
        revisions = Map.copyOf(revisions);
        if (!revisions.keySet().equals(REPOSITORIES) || revisions.values().stream().anyMatch(revision -> !matches(revision, "[a-f0-9]{40}"))) {
            throw invalid();
        }
        candidates = List.copyOf(candidates);
        stores = List.copyOf(stores);
        tenants = List.copyOf(tenants);
        requireRoles(candidates, CandidateLockVO::role);
        verifyStores(stores);
        if (tenants.size() != 2 || distinct(tenants, TenantFixtureVO::tenantId) != 2
                || distinct(tenants, TenantFixtureVO::issuer) != 2) throw invalid();

        Set<Path> privateFiles = new HashSet<>();
        for (var candidate : candidates) {
            verifyCandidatePaths(checkoutRoot, privateRoot, outputDirectory, candidate, privateFiles);
        }
        for (var store : stores) addPrivateFile(privateRoot, privateFiles, store.credentialsFile());
        for (var tenant : tenants) {
            for (Path credential : tenant.credentialFiles().values()) addPrivateFile(privateRoot, privateFiles, credential);
        }
        addPrivateFile(privateRoot, privateFiles, loopbackTls.trustStore());
        addPrivateFile(privateRoot, privateFiles, loopbackTls.trustPasswordFile());
        addPrivateFile(privateRoot, privateFiles, loopbackTls.relayKeyStore());
        addPrivateFile(privateRoot, privateFiles, loopbackTls.relayPasswordFile());
    }

    enum Role {
        PROCESS("ps", "process-server", "tianji_process", "tianji_process_schema_history",
                "top.egon.cola.platform.tianji.process.server.acceptance.AutomationProcessAcceptanceMain", 15),
        AGENT("as", "agent-server", "tianji_agent", "flyway_schema_history",
                "top.egon.cola.platform.tianji.agent.server.acceptance.AutomationAgentAcceptanceMain", 18),
        WORKER("w", "worker-starter", "tianji_worker", "tianji_worker_schema_history",
                "top.egon.cola.platform.tianji.worker.acceptance.AutomationWorkerAcceptanceMain", 3);

        private final String catalogPrefix;
        private final String module;
        private final String schema;
        private final String historyTable;
        private final String main;
        private final int migrationVersion;

        Role(String suffix, String module, String schema, String historyTable, String main, int migrationVersion) {
            this.catalogPrefix = "tianji_it_automation_" + suffix + "_";
            this.module = "stellar-enclosure-tianji-" + module;
            this.schema = schema;
            this.historyTable = historyTable;
            this.main = main;
            this.migrationVersion = migrationVersion;
        }

        String catalogPrefix() { return catalogPrefix; }
        String module() { return module; }
        String schema() { return schema; }
        String historyTable() { return historyTable; }
        String main() { return main; }
        int migrationVersion() { return migrationVersion; }
    }

    enum Identity { PUBLISHER, ACTIVATOR, REQUESTER, CANDIDATE, PRODUCER, AGENT_PUBLISHER, OBSERVER }

    record CandidateLockVO(Role role, Path execJar, String execJarSha256, Path testClasses, String testClassesSha256,
                           Path workerClasspath, String workerClasspathSha256, Path roleProperties) { }

    record StoreLockVO(Role role, String jdbcUrl, String catalog, String schema, String historySha256, Path credentialsFile) { }

    record TenantFixtureVO(long tenantId, URI issuer, Map<Identity, Path> credentialFiles) {
        TenantFixtureVO {
            credentialFiles = Map.copyOf(credentialFiles);
            if (tenantId < 1 || !validIssuer(issuer) || !credentialFiles.keySet().equals(EnumSet.allOf(Identity.class))) {
                throw invalid();
            }
        }
    }

    record LoopbackTlsFilesVO(Path trustStore, Path trustPasswordFile, Path relayKeyStore, Path relayPasswordFile) { }

    static AutomationAcceptanceManifestVO read(Path path) {
        try {
            requirePrivateChild(path.getParent(), path);
            var manifest = JSON.readValue(readBytes(path, 64 * 1024), AutomationAcceptanceManifestVO.class);
            if (manifest == null || !path.startsWith(manifest.privateRoot())) throw invalid();
            requirePrivateChild(manifest.privateRoot(), path);
            return manifest;
        } catch (Exception invalid) {
            throw invalid();
        }
    }

    CandidateLockVO candidate(Role role) {
        return candidates.stream().filter(candidate -> candidate.role() == role).findFirst().orElseThrow(AutomationAcceptanceManifestVO::invalid);
    }

    StoreLockVO store(Role role) {
        return stores.stream().filter(store -> store.role() == role).findFirst().orElseThrow(AutomationAcceptanceManifestVO::invalid);
    }

    static void verifyStores(List<StoreLockVO> stores) {
        requireRoles(stores, StoreLockVO::role);
        if (distinct(stores, StoreLockVO::catalog) != stores.size() || distinct(stores, StoreLockVO::jdbcUrl) != stores.size()) {
            throw invalid();
        }
        for (var store : stores) {
            var jdbc = JDBC.matcher(Objects.requireNonNull(store.jdbcUrl()));
            if (!jdbc.matches() || Integer.parseInt(jdbc.group(1)) > 65_535 || !jdbc.group(2).equals(store.catalog())
                    || !store.catalog().startsWith(store.role().catalogPrefix()) || !store.role().schema().equals(store.schema())
                    || !isDigest(store.historySha256())) throw invalid();
        }
    }

    static Path requirePrivateChild(Path root, Path file) {
        requirePrivateDirectory(root);
        var canonical = requireCanonical(file);
        if (!canonical.startsWith(root) || !Files.isRegularFile(canonical, LinkOption.NOFOLLOW_LINKS)) throw invalid();
        try {
            if (!Files.getPosixFilePermissions(canonical, LinkOption.NOFOLLOW_LINKS).equals(FILE_MODE)
                    || !Files.getOwner(root).equals(Files.getOwner(canonical, LinkOption.NOFOLLOW_LINKS))) throw invalid();
            for (Path parent = canonical.getParent(); !parent.equals(root); parent = parent.getParent()) {
                requirePrivateDirectory(parent);
                if (!Files.getOwner(root).equals(Files.getOwner(parent, LinkOption.NOFOLLOW_LINKS))) throw invalid();
            }
        } catch (IOException unavailable) {
            throw invalid();
        }
        return canonical;
    }

    static Path requirePrivateDirectory(Path path) {
        var canonical = requireCanonical(path);
        try {
            if (!Files.isDirectory(canonical, LinkOption.NOFOLLOW_LINKS)
                    || !Files.getPosixFilePermissions(canonical, LinkOption.NOFOLLOW_LINKS).equals(DIR_MODE)) throw invalid();
        } catch (IOException unavailable) {
            throw invalid();
        }
        return canonical;
    }

    static Path requireCanonical(Path path) {
        try {
            if (path == null || !path.isAbsolute() || !path.equals(path.normalize()) || !path.equals(path.toRealPath())) throw invalid();
        } catch (IOException unavailable) {
            throw invalid();
        }
        return path;
    }

    static byte[] readBytes(Path path, int limit) throws IOException {
        try (var stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            var bytes = stream.readNBytes(limit + 1);
            if (bytes.length == 0 || bytes.length > limit) throw invalid();
            return bytes;
        }
    }

    static boolean isDigest(String value) { return matches(value, "[a-f0-9]{64}"); }

    static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid private automation acceptance configuration"); }

    private static void verifyCandidatePaths(Path checkoutRoot, Path privateRoot, Path outputDirectory,
                                             CandidateLockVO candidate, Set<Path> privateFiles) {
        var target = requireCanonical(checkoutRoot.resolve(candidate.role().module()).resolve("target"));
        if (!requireCanonical(candidate.testClasses()).equals(target.resolve("test-classes"))
                || !Files.isDirectory(candidate.testClasses(), LinkOption.NOFOLLOW_LINKS)
                || !isDigest(candidate.testClassesSha256())) throw invalid();
        addPrivateFile(privateRoot, privateFiles, candidate.roleProperties());
        if (candidate.role() == Role.WORKER) {
            if (candidate.execJar() != null || candidate.execJarSha256() != null || !isDigest(candidate.workerClasspathSha256())) throw invalid();
            addPrivateFile(privateRoot, privateFiles, candidate.workerClasspath());
        } else {
            var jar = requireCanonical(candidate.execJar());
            if (!jar.getParent().equals(target) || !jar.getFileName().toString().endsWith("-exec.jar")
                    || !Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || !isDigest(candidate.execJarSha256())
                    || candidate.workerClasspath() != null || candidate.workerClasspathSha256() != null) throw invalid();
        }
        if (privateFiles.stream().anyMatch(path -> path.startsWith(outputDirectory))) throw invalid();
    }

    private static void addPrivateFile(Path root, Set<Path> files, Path file) {
        if (!files.add(requirePrivateChild(root, file))) throw invalid();
    }

    private static <T> void requireRoles(List<T> values, Function<T, Role> role) {
        if (values.size() != Role.values().length || !Set.copyOf(values.stream().map(role).toList()).equals(EnumSet.allOf(Role.class))) {
            throw invalid();
        }
    }

    private static <T, V> long distinct(List<T> values, Function<T, V> field) {
        return values.stream().map(field).distinct().count();
    }

    private static boolean validIssuer(URI issuer) {
        return issuer != null && "https".equals(issuer.getScheme()) && "127.0.0.1".equals(issuer.getHost())
                && issuer.getPort() > 0 && issuer.getPort() <= 65_535 && issuer.getUserInfo() == null
                && issuer.getQuery() == null && issuer.getFragment() == null;
    }

    private static boolean matches(String value, String regex) { return value != null && value.matches(regex); }
}
