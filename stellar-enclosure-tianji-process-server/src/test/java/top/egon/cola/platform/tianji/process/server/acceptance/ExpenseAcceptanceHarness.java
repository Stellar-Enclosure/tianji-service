package top.egon.cola.platform.tianji.process.server.acceptance;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

final class ExpenseAcceptanceHarness implements AutoCloseable {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();
    private static final Set<PosixFilePermission> FILE_MODE = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> DIRECTORY_MODE = PosixFilePermissions.fromString("rwx------");
    private static final Duration POLL = Duration.ofMillis(25);
    private static final Duration GRACEFUL = Duration.ofSeconds(10);
    private static final Duration FORCED = Duration.ofSeconds(5);
    private static final long MAX_ARTIFACT_BYTES = 512L * 1024 * 1024;
    private static final int MAX_MANIFEST_BYTES = 64 * 1024;
    private static final int MAX_EVIDENCE_BYTES = 256 * 1024;
    private static final Pattern JDBC = Pattern.compile("jdbc:postgresql://(?:127\\.0\\.0\\.1|\\[::1]):([0-9]{1,5})/(tianji_it_[a-z0-9_]{1,51})");
    private static final Pattern CONFIG_KEY = Pattern.compile("[A-Za-z0-9_.\\[\\]-]{1,256}");
    private static final Set<String> CONFIG_PREFIXES = Set.of("stellar.tianji.", "egon.cola.", "flowable.", "management.otlp.",
            "management.tracing.", "server.ssl.", "spring.datasource.", "spring.security.oauth2.");
    private static final Set<String> CONFIG_KEYS = Set.of("server.address", "server.port", "spring.flyway.enabled",
            "spring.sql.init.mode", "spring.main.banner-mode");
    private static final Set<WitnessKind> BASE_WITNESSES = EnumSet.range(WitnessKind.START_COMMAND, WitnessKind.ORIGINAL_RECEIPT);
    private static final Set<WitnessKind> RECOVERY_WITNESSES = EnumSet.range(WitnessKind.WORKER_REACQUIRED, WitnessKind.PROCESS_WAIT_RETAINED);

    private final ExpenseAcceptanceManifestDTO manifest;
    private final ChildLauncher launcher;
    private final LongSupplier nanoTime;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Map<ChildRole, RunningChild> running = new EnumMap<>(ChildRole.class);
    private final Map<ChildRole, Integer> generations = new EnumMap<>(ChildRole.class);
    private final List<ChildIdentityDTO> children = new ArrayList<>();
    private final List<ChildExitDTO> exits = new ArrayList<>();
    private final Map<Path, String> writtenConfigurations = new HashMap<>();
    private final Map<Path, String> s12Libraries = new LinkedHashMap<>();
    private boolean closed;

    private ExpenseAcceptanceHarness(ExpenseAcceptanceManifestDTO manifest, ChildLauncher launcher, LongSupplier nanoTime,
                                    Clock clock, Sleeper sleeper) {
        this.manifest = manifest;
        this.launcher = Objects.requireNonNull(launcher);
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.clock = Objects.requireNonNull(clock);
        this.sleeper = Objects.requireNonNull(sleeper);
    }

    static Optional<ExpenseAcceptanceManifestDTO> readManifest(String absolutePath) {
        return absolutePath == null ? Optional.empty() : Optional.of(readManifest(absolute(absolutePath)));
    }

    static ExpenseAcceptanceManifestDTO readManifest(Path manifestFile) {
        try {
            requirePrivate(manifestFile, false);
            byte[] content;
            try (var input = Files.newInputStream(manifestFile, LinkOption.NOFOLLOW_LINKS)) {
                content = input.readNBytes(MAX_MANIFEST_BYTES + 1);
            }
            if (content.length == 0 || content.length > MAX_MANIFEST_BYTES) throw invalid();
            var result = JSON.readValue(content, ExpenseAcceptanceManifestDTO.class);
            validateManifest(result);
            requirePrivateChild(absolute(result.privateRoot()), manifestFile.toString(), false);
            return result;
        } catch (IOException | RuntimeException failure) {
            throw invalid();
        }
    }

    static ExpenseAcceptanceHarness open(ExpenseAcceptanceManifestDTO manifest, SourcePreflight preflight,
                                          ChildLauncher launcher, LongSupplier nanoTime, Clock clock, Sleeper sleeper) {
        validateManifest(manifest);
        Objects.requireNonNull(preflight);
        try {
            var witness = preflight.verify(manifest);
            if (witness == null || !manifest.revisions().equals(witness.revisions()) || witness.stores().size() != 3) {
                throw invalid();
            }
            var expected = EnumSet.allOf(StoreOwner.class);
            for (var store : witness.stores()) {
                var locked = manifest.stores().stream().filter(item -> item.owner() == store.owner()).findFirst().orElseThrow();
                if (!expected.remove(store.owner()) || !matchingStore(locked, store)) throw invalid();
            }
        } catch (RuntimeException failure) {
            throw unavailable();
        }
        var harness = new ExpenseAcceptanceHarness(manifest, Objects.requireNonNull(launcher), nanoTime, clock, sleeper);
        if (manifest.suite().observesReceiveLinks()) {
            try {
                harness.s12Libraries.putAll(S12ReceiveLinkCollectorEvidence.prepare(manifest));
            } catch (IOException failure) {
                throw unavailable();
            }
        }
        return harness;
    }

    private static boolean matchingStore(StoreBindingDTO locked, StoreWitnessDTO actual) {
        return actual.emptyFixtureScope() && locked.jdbcUrl().equals(actual.jdbcUrl())
                && locked.serverIdentity().equals(actual.serverIdentity()) && locked.schema().equals(actual.schema())
                && locked.postgresVersion().equals(actual.postgresVersion()) && locked.pgvectorVersion().equals(actual.pgvectorVersion())
                && locked.historySha256().equals(actual.historySha256()) && locked.setupRole().equals(actual.setupRole())
                && locked.runtimeRole().equals(actual.runtimeRole()) && locked.observerRole().equals(actual.observerRole());
    }

    private static void validateManifest(ExpenseAcceptanceManifestDTO manifest) {
        if (manifest == null || manifest.schemaVersion() != 1 || !manifest.runAuthorized() || manifest.suite() == null
                || !matches(manifest.runId(), "[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) throw invalid();
        Path checkout = absolute(manifest.checkoutRoot());
        Path privateRoot = absolute(manifest.privateRoot());
        requireDirectory(checkout);
        requirePrivate(privateRoot, true);
        if (privateRoot.startsWith(checkout) || checkout.startsWith(privateRoot)) throw invalid();
        requirePrivateChild(privateRoot, manifest.outputDirectory(), true);
        Path fixture = absolute(manifest.fixtureRoot());
        if (!fixture.equals(checkout.resolve("stellar-enclosure-tianji-process-server/src/test/resources"))) throw invalid();
        requireDirectory(fixture);
        var revisions = Objects.requireNonNull(manifest.revisions());
        for (String revision : List.of(revisions.tianjiService(), revisions.components(), revisions.shoubing(),
                revisions.jianshen(), revisions.tianshu())) {
            if (!matches(revision, "[0-9a-f]{40}")) throw invalid();
        }
        validateTenants(manifest.tenants(), privateRoot);
        validateStores(manifest.stores(), privateRoot);
        var tls = Objects.requireNonNull(manifest.tls());
        var tlsPaths = new HashSet<Path>();
        for (String path : List.of(tls.trustStore(), tls.agentKeyStore(), tls.processKeyStore(), tls.relayKeyStore(), tls.credentialFile())) {
            if (!tlsPaths.add(requirePrivateChild(privateRoot, path, false))) throw invalid();
        }
        validateEndpoints(Objects.requireNonNull(manifest.endpoints()));
        validateLimits(Objects.requireNonNull(manifest.limits()));
        verifyArtifact(checkout, ChildRole.AGENT, manifest.agent());
        verifyArtifact(checkout, ChildRole.PROCESS, manifest.process());
    }

    private static void validateTenants(List<TenantBindingDTO> tenants, Path privateRoot) {
        if (tenants.size() != 2 || !Objects.equals(tenants.getFirst().ownerSubject(), tenants.getLast().ownerSubject())) throw invalid();
        var ids = new HashSet<Long>();
        var issuers = new HashSet<URI>();
        var credentials = new HashSet<Path>();
        for (var tenant : tenants) {
            if (tenant.tenantId() <= 0 || !ids.add(tenant.tenantId()) || !issuers.add(https(tenant.issuer()))
                    || !safeReference(tenant.ownerSubject()) || !safeReference(tenant.applicationId())
                    || tenant.reviewerSubjects().size() != 2 || new HashSet<>(tenant.reviewerSubjects()).size() != 2
                    || tenant.reviewerSubjects().contains(tenant.ownerSubject())
                    || tenant.reviewerSubjects().stream().anyMatch(subject -> !safeReference(subject))
                    || tenant.roleIds().size() != 3 || new HashSet<>(tenant.roleIds()).size() != 3
                    || tenant.roleIds().stream().anyMatch(role -> !positiveLong(role))
                    || !credentials.add(requirePrivateChild(privateRoot, tenant.credentialFile(), false))) throw invalid();
            if (https(tenant.processResource()).equals(https(tenant.agentResource()))) throw invalid();
        }
    }

    private static void validateStores(List<StoreBindingDTO> stores, Path privateRoot) {
        if (stores.size() != 3) throw invalid();
        var owners = EnumSet.noneOf(StoreOwner.class);
        var databases = new HashSet<String>();
        var roles = new HashSet<String>();
        var credentials = new HashSet<Path>();
        for (var store : stores) {
            String schema = switch (store.owner()) {
                case AGENT -> "tianji_agent";
                case PROCESS -> "tianji_process";
                case BUSINESS -> "tianji_worker";
            };
            var jdbc = JDBC.matcher(Objects.requireNonNull(store.jdbcUrl()));
            if (!owners.add(store.owner()) || !jdbc.matches() || !validPort(Integer.parseInt(jdbc.group(1)))
                    || !databases.add(jdbc.group(2)) || !safeReference(store.serverIdentity()) || !schema.equals(store.schema())
                    || !matches(store.postgresVersion(), "[0-9]{1,2}\\.[0-9]{1,2}(?:\\.[0-9]{1,2})?")
                    || !isSha(store.historySha256()) || !credentials.add(requirePrivateChild(privateRoot, store.credentialFile(), false))) {
                throw invalid();
            }
            if (store.owner() == StoreOwner.AGENT
                    ? !matches(store.pgvectorVersion(), "[0-9]{1,2}\\.[0-9]{1,2}\\.[0-9]{1,2}")
                    : !"NONE".equals(store.pgvectorVersion())) throw invalid();
            for (String role : List.of(store.setupRole(), store.runtimeRole(), store.observerRole())) {
                if (!matches(role, "tianji_it_[a-z0-9_]{1,50}") || !roles.add(role)) throw invalid();
            }
        }
    }

    private static void validateEndpoints(EndpointsDTO endpoints) {
        var ports = new HashSet<Integer>();
        for (String endpoint : List.of(endpoints.agent(), endpoints.process(), endpoints.linkedRelay(),
                endpoints.resultRelay(), endpoints.workerRelay())) {
            if (!ports.add(requireLocalEndpoint(https(endpoint), false).getPort())) throw invalid();
        }
        URI collector = URI.create(endpoints.collector());
        if (!Set.of("http", "https").contains(collector.getScheme())
                || !ports.add(requireLocalEndpoint(collector, true).getPort())) throw invalid();
    }

    private static URI requireLocalEndpoint(URI endpoint, boolean allowPath) {
        if (endpoint.getHost() == null || !Set.of("127.0.0.1", "[::1]").contains(endpoint.getHost())
                || !validPort(endpoint.getPort()) || endpoint.getUserInfo() != null || endpoint.getQuery() != null
                || endpoint.getFragment() != null || !endpoint.equals(endpoint.normalize())
                || !allowPath && !Set.of("", "/").contains(endpoint.getPath())) throw invalid();
        return endpoint;
    }

    private static URI https(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || !uri.equals(uri.normalize())
                || !StandardCharsets.US_ASCII.newEncoder().canEncode(value) || value.length() > 512) throw invalid();
        return uri;
    }

    private static void validateLimits(PhaseLimitsDTO limits) {
        if (!between(limits.readinessSeconds(), 1, 60) || !between(limits.pathSeconds(), 1, 90)
                || !between(limits.manualSeconds(), 1, 180) || !between(limits.recoverySeconds(), 1, 240)
                || !between(limits.linkedHoldSeconds(), 1, 10)) throw invalid();
    }

    private static void verifyArtifact(Path checkout, ChildRole role, ArtifactLockDTO artifact) {
        if (artifact == null || !isSha(artifact.execJarSha256()) || !isSha(artifact.testClassesSha256())) throw invalid();
        String module = role == ChildRole.AGENT ? "stellar-enclosure-tianji-agent-server" : "stellar-enclosure-tianji-process-server";
        Path target = checkout.resolve(module).resolve("target");
        Path jar = absolute(artifact.execJar());
        Path classes = absolute(artifact.testClasses());
        if (!jar.equals(target.resolve(module + "-exec.jar")) || !classes.equals(target.resolve("test-classes"))
                || jar.toString().indexOf(':') >= 0 || classes.toString().indexOf(',') >= 0
                || !sha256(jar).equals(artifact.execJarSha256()) || !classTreeSha256(classes).equals(artifact.testClassesSha256())) {
            throw invalid();
        }
        Path main = classes.resolve(childMain(role).replace('.', '/') + ".class");
        requireNoSymlink(main);
        if (!Files.isRegularFile(main, LinkOption.NOFOLLOW_LINKS)) throw invalid();
        try (var archive = new JarFile(jar.toFile(), false)) {
            String app = role == ChildRole.AGENT
                    ? "top/egon/cola/platform/tianji/agent/server/TianjiAgentServerApplication"
                    : "top/egon/cola/platform/tianji/process/server/TianjiProcessServerApplication";
            if (archive.getJarEntry("org/springframework/boot/loader/launch/PropertiesLauncher.class") == null
                    || archive.getJarEntry("BOOT-INF/classes/" + app + ".class") == null) throw invalid();
        } catch (IOException failure) {
            throw invalid();
        }
    }

    static String sha256(Path file) {
        try {
            requireNoSymlink(file);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw invalid();
            return HexFormat.of().formatHex(fileDigest(file, MAX_ARTIFACT_BYTES));
        } catch (IOException | RuntimeException failure) {
            throw invalid();
        }
    }

    static String classTreeSha256(Path root) {
        try {
            requireDirectory(root);
            List<Path> paths;
            try (var walker = Files.walk(root)) {
                paths = walker.limit(16_385).toList();
            }
            if (paths.size() > 16_384) throw invalid();
            var files = new TreeMap<byte[], Path>(Arrays::compareUnsigned);
            long total = 0;
            for (Path path : paths) {
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw invalid();
                String relative = root.relativize(path).toString();
                if (!StandardCharsets.UTF_8.newEncoder().canEncode(relative)) throw invalid();
                byte[] name = relative.getBytes(StandardCharsets.UTF_8);
                total = Math.addExact(total, Files.size(path));
                if (name.length > 4096 || total > MAX_ARTIFACT_BYTES || files.putIfAbsent(name, path) != null) throw invalid();
            }
            if (files.isEmpty()) throw invalid();
            MessageDigest digest = digest();
            for (var entry : files.entrySet()) {
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(entry.getKey().length).array());
                digest.update(entry.getKey());
                digest.update(fileDigest(entry.getValue(), MAX_ARTIFACT_BYTES));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | RuntimeException failure) {
            throw invalid();
        }
    }

    private static byte[] fileDigest(Path path, long cap) throws IOException {
        var digest = digest();
        long count = 0;
        byte[] buffer = new byte[8192];
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            for (int size; (size = input.read(buffer)) >= 0;) {
                count += size;
                if (count > cap) throw invalid();
                digest.update(buffer, 0, size);
            }
        }
        return digest.digest();
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw unavailable();
        }
    }

    Path writeChildConfiguration(ChildRole role, Map<String, String> properties) {
        requireOpen();
        Path destination = configurationPath(role);
        try {
            requirePrivate(absolute(manifest.privateRoot()), true);
            var values = new Properties();
            if (properties == null || properties.isEmpty() || properties.size() > 512) throw invalid();
            for (var entry : properties.entrySet()) {
                if (entry.getKey() == null || !CONFIG_KEY.matcher(entry.getKey()).matches()
                        || !CONFIG_KEYS.contains(entry.getKey()) && CONFIG_PREFIXES.stream().noneMatch(entry.getKey()::startsWith)
                        || entry.getValue() == null || entry.getValue().length() > 8192
                        || !StandardCharsets.UTF_8.newEncoder().canEncode(entry.getValue())) throw invalid();
                values.setProperty(entry.getKey(), entry.getValue());
            }
            requireChildConfiguration(role, values);
            addTrustedFixtureSettings(role, values);
            var contents = new ByteArrayOutputStream();
            values.store(contents, null);
            if (contents.size() > MAX_MANIFEST_BYTES) throw invalid();
            writePrivateNew(destination, contents.toByteArray());
            writtenConfigurations.put(destination, sha256(destination));
            return destination;
        } catch (IOException failure) {
            throw unavailable();
        }
    }

    ChildIdentityDTO startChild(ChildRole role, Path privateConfig, Predicate<ChildIdentityDTO> readiness) {
        requireOpen();
        Objects.requireNonNull(readiness);
        if (running.containsKey(role) || !configurationPath(role).equals(privateConfig)) throw invalid();
        requireWrittenConfiguration(privateConfig);
        ArtifactLockDTO artifact = role == ChildRole.AGENT ? manifest.agent() : manifest.process();
        verifyArtifact(absolute(manifest.checkoutRoot()), role, artifact);
        int generation = generations.merge(role, 1, Math::addExact);
        OwnedChild handle = null;
        RunningChild child = null;
        try {
            handle = Objects.requireNonNull(launcher.launch(launchCommand(role, artifact, privateConfig)));
            var identity = new ChildIdentityDTO(role, generation, handle.pid(), handle.startedAt());
            if (identity.pid() <= 0 || identity.startedAt() == null || children.stream().anyMatch(seen ->
                    seen.pid() == identity.pid() || seen.role() == role && !seen.startedAt().isBefore(identity.startedAt()))) {
                throw unavailable();
            }
            child = new RunningChild(identity, handle);
            children.add(identity);
            running.put(role, child);
            var owned = child.handle();
            return await(Phase.READINESS, () -> {
                if (!owned.alive()) throw unavailable();
                return readiness.test(identity) && owned.alive() ? Optional.of(identity) : Optional.empty();
            });
        } catch (Exception failure) {
            if (handle != null) {
                if (child == null) stopHandle(handle);
                else stop(child);
            }
            throw unavailable();
        }
    }

    ChildIdentityDTO replaceChild(ChildIdentityDTO expected, Path privateConfig, Predicate<ChildIdentityDTO> readiness) {
        requireOpen();
        if (expected == null || !configurationPath(expected.role()).equals(privateConfig)) throw invalid();
        requireWrittenConfiguration(privateConfig);
        RunningChild prior = running.get(expected.role());
        if (prior == null || !prior.identity().equals(expected)) throw invalid();
        stop(prior);
        return startChild(expected.role(), privateConfig, readiness);
    }

    private void requireChildConfiguration(ChildRole role, Properties values) {
        URI endpoint = URI.create(role == ChildRole.AGENT ? manifest.endpoints().agent() : manifest.endpoints().process());
        var store = manifest.stores().stream().filter(item -> item.owner().name().equals(role.name())).findFirst().orElseThrow();
        String address = endpoint.getHost().equals("[::1]") ? "::1" : endpoint.getHost();
        String keyStore = role == ChildRole.AGENT ? manifest.tls().agentKeyStore() : manifest.tls().processKeyStore();
        if (!address.equals(values.getProperty("server.address"))
                || !Integer.toString(endpoint.getPort()).equals(values.getProperty("server.port"))
                || !"true".equals(values.getProperty("server.ssl.enabled"))
                || !absolute(keyStore).toUri().toASCIIString().equals(values.getProperty("server.ssl.key-store"))
                || !"false".equals(values.getProperty("spring.flyway.enabled"))
                || !"never".equals(values.getProperty("spring.sql.init.mode"))
                || !store.jdbcUrl().equals(values.getProperty("spring.datasource.url"))
                || !store.runtimeRole().equals(values.getProperty("spring.datasource.username"))) throw invalid();
    }

    private void addTrustedFixtureSettings(ChildRole role, Properties values) {
        String prefix = "tianji.expense.fixture.";
        values.setProperty(prefix + "run-authorized", "true");
        if (manifest.suite().observesReceiveLinks()) {
            values.setProperty(prefix + "s12-receive-link", "true");
        }
        values.setProperty(prefix + "role", role.name());
        values.setProperty(prefix + "trust-store", manifest.tls().trustStore());
        values.setProperty(prefix + "trust-password-file", manifest.tls().credentialFile());
        for (int index = 0; index < manifest.tenants().size(); index++) {
            var tenant = manifest.tenants().get(index);
            String tenantPrefix = prefix + "tenant-" + (index + 1) + ".";
            values.setProperty(tenantPrefix + "id", Long.toString(tenant.tenantId()));
            values.setProperty(tenantPrefix + "issuer", tenant.issuer());
            values.setProperty(tenantPrefix + "subject", tenant.ownerSubject());
        }
    }

    private void requireWrittenConfiguration(Path privateConfig) {
        requirePrivate(privateConfig, false);
        String hash = writtenConfigurations.get(privateConfig);
        if (hash == null || !hash.equals(sha256(privateConfig))) throw invalid();
    }

    private ChildLaunchDTO launchCommand(ChildRole role, ArtifactLockDTO artifact, Path configuration) throws IOException {
        String java = Path.of(System.getProperty("java.home")).toRealPath().resolve("bin/java").toString();
        String loaderPath = artifact.testClasses();
        if (manifest.suite().observesReceiveLinks()) {
            if (role == ChildRole.AGENT) {
                verifyArtifact(absolute(manifest.checkoutRoot()), ChildRole.PROCESS, manifest.process());
                loaderPath += "," + manifest.process().testClasses();
            } else {
                if (s12Libraries.size() != S12ReceiveLinkCollectorEvidence.TRACE_LIBRARIES.size()) throw invalid();
                for (var library : s12Libraries.entrySet()) {
                    if (!library.getValue().equals(sha256(library.getKey()))) throw invalid();
                    loaderPath += "," + library.getKey();
                }
            }
        }
        return new ChildLaunchDTO(role, List.of(java, "-Dloader.main=" + childMain(role), "-Dloader.path=" + loaderPath,
                "-cp", artifact.execJar(), "org.springframework.boot.loader.launch.PropertiesLauncher",
                "--spring.main.web-application-type=servlet", "--spring.config.additional-location=" + configuration.toUri().toASCIIString()),
                Map.of("ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS", "false"), absolute(manifest.privateRoot()));
    }

    private static String childMain(ChildRole role) {
        return role == ChildRole.AGENT
                ? "top.egon.cola.platform.tianji.agent.server.acceptance.ExpenseAgentAcceptanceMain"
                : "top.egon.cola.platform.tianji.process.server.acceptance.ExpenseProcessAcceptanceMain";
    }

    static ChildLauncher processBuilderLauncher() {
        return launch -> {
            var builder = new ProcessBuilder(launch.arguments()).directory(launch.workingDirectory().toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().clear();
            builder.environment().putAll(launch.environment());
            Process process = builder.start();
            return new OwnedChild() {
                public long pid() { return process.pid(); }
                public Instant startedAt() { return process.info().startInstant().orElseThrow(ExpenseAcceptanceHarness::unavailable); }
                public boolean alive() { return process.isAlive(); }
                public void requestStop() { process.destroy(); }
                public void forceStop() { process.destroyForcibly(); }
                public boolean awaitExit(Duration duration) throws InterruptedException {
                    return process.waitFor(duration.toMillis(), TimeUnit.MILLISECONDS);
                }
                public int exitCode() { return process.exitValue(); }
            };
        };
    }

    PhaseDeadlineDTO beginPhase(Phase phase) {
        requireOpen();
        return new PhaseDeadlineDTO(this, phase, nanoTime.getAsLong(), timeout(phase));
    }

    <T> T await(Phase phase, Supplier<Optional<T>> witness) {
        return await(beginPhase(phase), witness);
    }

    <T> T await(PhaseDeadlineDTO deadline, Supplier<Optional<T>> witness) {
        requireOpen();
        if (deadline == null || deadline.owner != this) throw invalid();
        Duration limit = deadline.limit;
        long start = deadline.startedNanos;
        int pollLimit = Math.toIntExact(limit.dividedBy(POLL)) + 1;
        for (int poll = 0; poll < pollLimit; poll++) {
            long elapsed = nanoTime.getAsLong() - start;
            if (elapsed < 0 || elapsed >= limit.toNanos()) break;
            var observed = Objects.requireNonNull(witness.get());
            elapsed = nanoTime.getAsLong() - start;
            if (elapsed < 0 || elapsed >= limit.toNanos()) break;
            if (observed.isPresent()) return observed.orElseThrow();
            try {
                Duration remaining = Duration.ofNanos(limit.toNanos() - elapsed);
                sleeper.sleep(remaining.compareTo(POLL) < 0 ? remaining : POLL);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw unavailable();
            }
        }
        throw new IllegalStateException("Expense acceptance phase did not reach its witness: " + deadline.phase.name());
    }

    private Duration timeout(Phase phase) {
        var limits = manifest.limits();
        return Duration.ofSeconds(switch (phase) {
            case READINESS -> limits.readinessSeconds();
            case STANDARD_PATH -> limits.pathSeconds();
            case MANUAL_CASE -> limits.manualSeconds();
            case RECOVERY -> limits.recoverySeconds();
            case LINKED_RESPONSE_HOLD -> limits.linkedHoldSeconds();
        });
    }

    @Override
    public void close() {
        if (closed && running.isEmpty()) return;
        closed = true;
        boolean failed = false;
        for (var child : List.copyOf(running.values())) {
            try {
                stop(child);
            } catch (RuntimeException unavailable) {
                failed = true;
            }
        }
        if (failed) throw unavailable();
    }

    private void stop(RunningChild child) {
        stopHandle(child.handle());
        exits.add(new ChildExitDTO(child.identity(), child.handle().exitCode()));
        running.remove(child.identity().role(), child);
    }

    private static void stopHandle(OwnedChild handle) {
        try {
            if (handle.alive()) {
                handle.requestStop();
                if (!handle.awaitExit(GRACEFUL) && handle.alive()) {
                    handle.forceStop();
                    if (!handle.awaitExit(FORCED) && handle.alive()) throw unavailable();
                }
            }
            if (handle.alive()) throw unavailable();
        } catch (InterruptedException interrupted) {
            if (handle.alive()) handle.forceStop();
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    ExpenseEvidenceDTO evidence(boolean passed, List<ExpensePathResultDTO> paths) {
        return new ExpenseEvidenceDTO(1, manifest.runId(), manifest.suite(), passed, clock.instant(), children, exits, paths);
    }

    Path publishEvidence(ExpenseEvidenceDTO evidence) {
        if (manifest.suite().observesReceiveLinks()) throw invalid();
        validateEvidence(evidence);
        Path output = requirePrivateChild(absolute(manifest.privateRoot()), manifest.outputDirectory(), true);
        Path destination = output.resolve("expense-" + manifest.runId() + ".json");
        Path temporary = null;
        boolean committed = false;
        try {
            byte[] bytes = JSON.writeValueAsBytes(evidence);
            if (bytes.length > MAX_EVIDENCE_BYTES) throw invalid();
            temporary = Files.createTempFile(output, ".expense-evidence-", ".tmp", PosixFilePermissions.asFileAttribute(FILE_MODE));
            try (var file = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                write(file, bytes);
            }
            Files.createLink(destination, temporary);
            committed = true;
            return destination;
        } catch (IOException failure) {
            throw unavailable();
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException failure) {
                    if (!committed) throw unavailable();
                }
            }
        }
    }

    private void validateEvidence(ExpenseEvidenceDTO evidence) {
        if (evidence == null || evidence.schemaVersion() != 1 || !manifest.runId().equals(evidence.runId())
                || evidence.suite() != manifest.suite() || evidence.observedAt() == null
                || evidence.observedAt().isAfter(clock.instant()) || !children.equals(evidence.children())
                || !exits.equals(evidence.exits())) throw invalid();
        var allowed = manifest.suite() == Suite.BUSINESS
                ? EnumSet.of(Scenario.NORMAL, Scenario.AGENT_FAILURE, Scenario.MANUAL_CASE) : EnumSet.of(Scenario.RECOVERY);
        var tenants = new HashSet<Long>();
        manifest.tenants().forEach(tenant -> tenants.add(tenant.tenantId()));
        var cases = new HashSet<String>();
        for (var path : evidence.paths()) {
            if (!tenants.contains(path.tenantId()) || !allowed.contains(path.scenario()) || !safeReference(path.operationId())
                    || !safeReference(path.businessKey()) || !safeReference(path.correlationId())
                    || !cases.add(path.tenantId() + ":" + path.scenario())) throw invalid();
            validateWitnesses(path);
            if (evidence.passed() && !path.passed()) throw invalid();
        }
        if (evidence.passed()) {
            long generationsNeeded = manifest.suite() == Suite.BUSINESS ? 1 : 2;
            if (!running.isEmpty() || children.size() != exits.size() || cases.size() != tenants.size() * allowed.size()
                    || children.stream().filter(child -> child.role() == ChildRole.AGENT).count() < generationsNeeded
                    || children.stream().filter(child -> child.role() == ChildRole.PROCESS).count() < generationsNeeded) throw invalid();
        }
    }

    private static void validateWitnesses(ExpensePathResultDTO path) {
        var required = EnumSet.copyOf(BASE_WITNESSES);
        switch (path.scenario()) {
            case NORMAL -> required.add(WitnessKind.NOTICE_RECEIPT);
            case AGENT_FAILURE -> required.add(WitnessKind.UNDO_RECEIPT);
            case MANUAL_CASE -> required.addAll(EnumSet.of(WitnessKind.UNDO_RECEIPT, WitnessKind.HUMAN_TASK,
                    WitnessKind.CASE_INSTANCE, WitnessKind.CASE_COMPLETION));
            case RECOVERY -> {
                required.add(WitnessKind.NOTICE_RECEIPT);
                required.addAll(RECOVERY_WITNESSES);
            }
        }
        for (var entry : path.witnesses().entrySet()) {
            WitnessDTO witness = entry.getValue();
            if (witness.state() == null || witness.state() == WitnessState.PROVED && !safeReference(witness.reference())
                    || witness.state() != WitnessState.PROVED && witness.reference() != null) throw invalid();
        }
        if (path.passed()) {
            for (WitnessKind kind : WitnessKind.values()) {
                WitnessDTO witness = path.witnesses().get(kind);
                if (witness == null || witness.state() != (required.contains(kind) ? WitnessState.PROVED : WitnessState.NOT_REACHED)) {
                    throw invalid();
                }
            }
        }
    }

    private Path configurationPath(ChildRole role) {
        return absolute(manifest.privateRoot()).resolve("expense-" + manifest.runId() + "-" + role.name().toLowerCase(Locale.ROOT) + "-"
                + (generations.getOrDefault(role, 0) + 1) + ".properties");
    }

    private void requireOpen() {
        if (closed) throw unavailable();
    }

    private static void writePrivateNew(Path destination, byte[] contents) throws IOException {
        try (var file = FileChannel.open(destination, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS), PosixFilePermissions.asFileAttribute(FILE_MODE))) {
            write(file, contents);
        }
    }

    private static void write(FileChannel file, byte[] contents) throws IOException {
        var data = ByteBuffer.wrap(contents);
        while (data.hasRemaining()) file.write(data);
        file.force(true);
    }

    private static Path requirePrivateChild(Path root, String name, boolean directory) {
        Path path = absolute(name);
        requireDescendant(root, path);
        Path current = path;
        while (!current.equals(root)) {
            requirePrivate(current, !current.equals(path) || directory);
            current = current.getParent();
        }
        requirePrivate(root, true);
        return path;
    }

    private static void requireDescendant(Path root, Path candidate) {
        if (root.equals(candidate) || !candidate.startsWith(root)) throw invalid();
    }

    private static Path absolute(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 4096 || raw.chars().anyMatch(c -> c < 32 || c == 127)
                || !StandardCharsets.UTF_8.newEncoder().canEncode(raw)) throw invalid();
        Path path = Path.of(raw);
        if (!path.isAbsolute() || !path.equals(path.normalize()) || !path.toString().equals(raw)) throw invalid();
        return path;
    }

    private static void requireNoSymlink(Path path) {
        if (!path.isAbsolute() || !path.equals(path.normalize())) throw invalid();
        for (Path current = path; current != null; current = current.getParent()) {
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(current)) throw invalid();
        }
    }

    private static void requireDirectory(Path path) {
        requireNoSymlink(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw invalid();
    }

    private static void requirePrivate(Path path, boolean directory) {
        try {
            requireNoSymlink(path);
            var attributes = Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            var owner = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
            if (!owner.equals(attributes.owner()) || !attributes.permissions().equals(directory ? DIRECTORY_MODE : FILE_MODE)
                    || (directory ? !attributes.isDirectory() : !attributes.isRegularFile())) throw invalid();
        } catch (IOException | UnsupportedOperationException failure) {
            throw invalid();
        }
    }

    private static boolean isSha(String value) { return matches(value, "[0-9a-f]{64}"); }
    private static boolean validPort(int value) { return between(value, 1024, 65535); }
    private static boolean between(int value, int minimum, int maximum) { return value >= minimum && value <= maximum; }
    private static boolean safeReference(String value) { return matches(value, "[A-Za-z0-9][A-Za-z0-9_.:@-]{0,254}"); }
    private static boolean matches(String value, String regex) { return value != null && value.matches(regex); }
    private static boolean positiveLong(String value) {
        try {
            return matches(value, "[1-9][0-9]{0,18}") && Long.parseLong(value) > 0;
        } catch (NumberFormatException outOfRange) {
            return false;
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Expense acceptance input is invalid");
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Expense acceptance harness is unavailable");
    }

    private record RunningChild(ChildIdentityDTO identity, OwnedChild handle) { }

    enum Suite {
        BUSINESS, RECOVERY, RECEIVE_LINK, RECEIVE_LINK_REPLAY;

        boolean observesReceiveLinks() {
            return this == RECEIVE_LINK || this == RECEIVE_LINK_REPLAY;
        }
    }
    enum ChildRole { AGENT, PROCESS }
    enum StoreOwner { AGENT, PROCESS, BUSINESS }
    enum Scenario { NORMAL, AGENT_FAILURE, MANUAL_CASE, RECOVERY }
    enum Phase { READINESS, STANDARD_PATH, MANUAL_CASE, RECOVERY, LINKED_RESPONSE_HOLD }
    static final class PhaseDeadlineDTO {
        private final ExpenseAcceptanceHarness owner;
        private final Phase phase;
        private final long startedNanos;
        private final Duration limit;

        private PhaseDeadlineDTO(ExpenseAcceptanceHarness owner, Phase phase, long startedNanos, Duration limit) {
            this.owner = owner;
            this.phase = phase;
            this.startedNanos = startedNanos;
            this.limit = limit;
        }
    }

    enum WitnessState { PROVED, NOT_REACHED, UNAVAILABLE, PENDING }
    enum WitnessKind {
        START_COMMAND, PROCESS_INSTANCE, DECISION_EXECUTION, DECISION_RULE, AGENT_DISPATCH, AGENT_RUN, AGENT_STATUS,
        PROCESS_END, WORKER_JOB, WORKER_OCCURRENCE, ORIGINAL_RECEIPT, NOTICE_RECEIPT, UNDO_RECEIPT,
        HUMAN_TASK, CASE_INSTANCE, CASE_COMPLETION, WORKER_REACQUIRED, EARLY_INBOX_PENDING,
        OUTBOX_RETRY_CONFIRMED, PROCESS_WAIT_RETAINED
    }

    record RevisionLockDTO(String tianjiService, String components, String shoubing, String jianshen, String tianshu) { }
    record ArtifactLockDTO(String execJar, String execJarSha256, String testClasses, String testClassesSha256) { }
    record TenantBindingDTO(long tenantId, String issuer, String ownerSubject, List<String> reviewerSubjects,
                            List<String> roleIds, String applicationId, String processResource, String agentResource,
                            String credentialFile) {
        TenantBindingDTO {
            reviewerSubjects = List.copyOf(reviewerSubjects);
            roleIds = List.copyOf(roleIds);
        }
    }
    record StoreBindingDTO(StoreOwner owner, String jdbcUrl, String serverIdentity, String schema, String postgresVersion,
                           String pgvectorVersion, String historySha256, String setupRole, String runtimeRole,
                           String observerRole, String credentialFile) { }
    record TlsBindingDTO(String trustStore, String agentKeyStore, String processKeyStore, String relayKeyStore,
                         String credentialFile) { }
    record EndpointsDTO(String agent, String process, String linkedRelay, String resultRelay, String workerRelay, String collector) { }
    record PhaseLimitsDTO(int readinessSeconds, int pathSeconds, int manualSeconds, int recoverySeconds, int linkedHoldSeconds) { }
    record ExpenseAcceptanceManifestDTO(int schemaVersion, String runId, boolean runAuthorized, Suite suite,
                                        String checkoutRoot, String privateRoot, String outputDirectory, String fixtureRoot,
                                        RevisionLockDTO revisions, ArtifactLockDTO agent, ArtifactLockDTO process,
                                        List<TenantBindingDTO> tenants, List<StoreBindingDTO> stores, TlsBindingDTO tls,
                                        EndpointsDTO endpoints, PhaseLimitsDTO limits) {
        ExpenseAcceptanceManifestDTO {
            tenants = List.copyOf(tenants);
            stores = List.copyOf(stores);
        }
        @Override public String toString() { return "ExpenseAcceptanceManifestDTO[redacted]"; }
    }

    record StoreWitnessDTO(StoreOwner owner, String jdbcUrl, String serverIdentity, String schema, String postgresVersion,
                           String pgvectorVersion, String historySha256, String setupRole, String runtimeRole,
                           String observerRole, boolean emptyFixtureScope) { }
    record SourceWitnessDTO(RevisionLockDTO revisions, List<StoreWitnessDTO> stores) {
        SourceWitnessDTO { stores = List.copyOf(stores); }
    }
    record ChildLaunchDTO(ChildRole role, List<String> arguments, Map<String, String> environment, Path workingDirectory) {
        ChildLaunchDTO {
            arguments = List.copyOf(arguments);
            environment = Map.copyOf(environment);
        }
        @Override public String toString() { return "ChildLaunchDTO[redacted]"; }
    }
    record ChildIdentityDTO(ChildRole role, int generation, long pid, Instant startedAt) { }
    record ChildExitDTO(ChildIdentityDTO child, int exitCode) { }
    record WitnessDTO(WitnessState state, String reference) { }
    record ExpensePathResultDTO(long tenantId, Scenario scenario, String operationId, String businessKey, String correlationId,
                                boolean passed, Map<WitnessKind, WitnessDTO> witnesses) {
        ExpensePathResultDTO { witnesses = Map.copyOf(witnesses); }
    }
    record ExpenseEvidenceDTO(int schemaVersion, String runId, Suite suite, boolean passed, Instant observedAt,
                              List<ChildIdentityDTO> children, List<ChildExitDTO> exits, List<ExpensePathResultDTO> paths) {
        ExpenseEvidenceDTO {
            children = List.copyOf(children);
            exits = List.copyOf(exits);
            paths = List.copyOf(paths);
        }
    }

    @FunctionalInterface
    interface SourcePreflight { SourceWitnessDTO verify(ExpenseAcceptanceManifestDTO manifest); }
    @FunctionalInterface
    interface ChildLauncher { OwnedChild launch(ChildLaunchDTO launch) throws Exception; }
    @FunctionalInterface
    interface Sleeper { void sleep(Duration duration) throws InterruptedException; }
    interface OwnedChild {
        long pid();
        Instant startedAt();
        boolean alive();
        void requestStop();
        void forceStop();
        boolean awaitExit(Duration duration) throws InterruptedException;
        int exitCode();
    }
}
