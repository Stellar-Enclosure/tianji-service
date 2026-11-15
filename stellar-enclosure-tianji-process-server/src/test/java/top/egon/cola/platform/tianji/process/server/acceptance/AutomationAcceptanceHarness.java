package top.egon.cola.platform.tianji.process.server.acceptance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceManifestVO.DIR_MODE;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceManifestVO.FILE_MODE;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceManifestVO.Identity;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceManifestVO.JSON;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceManifestVO.Role;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceManifestVO.TenantFixtureVO;

/** Drives disposable services through their ordinary authenticated network boundaries. */
@Slf4j
final class AutomationAcceptanceHarness implements AutoCloseable {
    private static final String FIXTURE = "tianji.automation.fixture.";
    private static final String LAUNCHER = "org.springframework.boot.loader.launch.PropertiesLauncher";
    private static final int HTTP_LIMIT = 6 * 1024 * 1024;
    private static final Set<String> REQUEST_HEADERS = Set.of("accept", "authorization", "content-type", "cache-control",
            "x-egon-tianji-tenant", "traceparent", "tracestate");
    private static final Set<String> RESPONSE_HEADERS = Set.of("content-type", "cache-control", "retry-after", "www-authenticate");
    private static final Pattern EXCEPTION = Pattern.compile("(?:^|Caused by: )([a-z][A-Za-z0-9_.$]+(?:Exception|Error))");
    private static final Pattern PROCESS_API_FAILURE = Pattern.compile(
            "Process (?:API dependency failure|admission API request failed): type=([a-z][A-Za-z0-9_.$]{1,180})$");
    private static final Pattern HTTP_ORIGIN = Pattern.compile("(?:https?|jdbc:postgresql)://([^/\\s,;]+)");

    enum Scenario {
        human_only, business_event_automation, mixed_agent_routing, pure_agent_control_flow,
        published_and_active_versions_are_independent, each_owner_recovers_at_persisted_cuts,
        foreign_owner_and_late_results_cannot_apply
    }

    enum Cut {
        PS_BEFORE_DISPATCH(Role.PROCESS), PS_AFTER_LINKED_ACCEPT_BEFORE_ACK(Role.PROCESS),
        AS_AFTER_RUN_PERSISTED_BEFORE_EXECUTION(Role.AGENT), AS_AFTER_TERMINAL_BEFORE_DELIVERY(Role.AGENT),
        WORKER_AFTER_EXTERNAL_APPLIED_BEFORE_REPORT(Role.WORKER), PS_AFTER_INBOX_COMMIT_BEFORE_APPLY(Role.PROCESS);
        private final Role owner;
        Cut(Role owner) { this.owner = owner; }
        Role owner() { return owner; }
    }

    record CutWitnessVO(Cut cut, String reference, String state, Instant at) {
        CutWitnessVO {
            checkedReference(reference);
            if (cut == null || at == null || state == null || !state.matches("[A-Z][A-Z_0-9]{0,47}")) throw invalid();
        }
    }

    record ChildWitnessVO(Role role, int generation, long pid, int port, String candidateSha256, Instant at) {
        ChildWitnessVO {
            if (role == null || generation < 1 || pid < 1 || port < 0 || port > 65_535
                    || (port == 0) != (role == Role.WORKER) || !AutomationAcceptanceManifestVO.isDigest(candidateSha256)
                    || at == null) throw invalid();
        }
    }

    record OperationWitnessVO(long tenantId, String instanceId, String releaseVersion, long activationRevision,
                              String receipt, int businessEffects, int businessRequests) {
        OperationWitnessVO {
            checkedReference(instanceId);
            checkedReference(releaseVersion);
            checkedReference(receipt);
            if (tenantId < 1 || activationRevision < 1 || businessEffects < 0 || businessRequests < businessEffects) throw invalid();
        }
    }

    interface ChildProcess {
        ChildWitnessVO witness();
        boolean alive();
        void stop(boolean force);
    }

    static final class ChildFleet implements AutoCloseable {
        private final BiFunction<Role, Integer, ChildProcess> launcher;
        private final EnumMap<Role, ChildProcess> children = new EnumMap<>(Role.class);
        private final EnumMap<Role, CutWitnessVO> observed = new EnumMap<>(Role.class);
        private final List<ChildWitnessVO> history = new ArrayList<>();
        private final Set<Long> seenPids = new HashSet<>();

        ChildFleet(BiFunction<Role, Integer, ChildProcess> launcher) { this.launcher = launcher; }

        void launchAll() {
            if (!children.isEmpty() || !history.isEmpty()) throw invalid();
            try {
                for (var role : Role.values()) start(role, 1);
            } catch (RuntimeException | AssertionError failure) {
                close();
                throw failure;
            }
        }

        void observe(CutWitnessVO witness) {
            if (!children.containsKey(witness.cut().owner())) throw invalid();
            if (observed.putIfAbsent(witness.cut().owner(), witness) != null) throw invalid();
        }

        void replaceChildAtCut(Role role, Runnable whileStopped) {
            if (!observed.containsKey(role)) throw new IllegalStateException("Automation child has no durable cut witness");
            var old = Objects.requireNonNull(children.get(role));
            old.stop(true);
            if (old.alive()) throw new IllegalStateException("Automation child did not exit at cut");
            children.remove(role);
            observed.remove(role);
            whileStopped.run();
            start(role, old.witness().generation() + 1);
        }

        private void start(Role role, int generation) {
            var child = launcher.apply(role, generation);
            var witness = child.witness();
            if (!child.alive() || witness.role() != role || witness.generation() != generation || !seenPids.add(witness.pid())) {
                child.stop(true);
                throw invalid();
            }
            children.put(role, child);
            history.add(witness);
        }

        List<ChildWitnessVO> current() { return children.values().stream().map(ChildProcess::witness).toList(); }
        List<ChildWitnessVO> history() { return List.copyOf(history); }

        @Override public void close() {
            RuntimeException failure = null;
            for (var role : List.of(Role.WORKER, Role.AGENT, Role.PROCESS)) {
                var child = children.remove(role);
                if (child == null) continue;
                try {
                    child.stop(false);
                    if (child.alive()) throw new IllegalStateException("Automation child remained live after shutdown");
                } catch (RuntimeException failed) {
                    if (failure == null) failure = failed;
                    else failure.addSuppressed(failed);
                }
            }
            if (failure != null) throw failure;
        }
    }

    private final AutomationAcceptanceManifestVO manifest;
    private final Duration deadline;
    private final SSLContext tls;
    private final HttpClient http;
    private final EnumMap<Role, Store> stores;
    private final Path childrenDirectory;
    private final Path gateDirectory;
    private final ExecutorService relayExecutor;
    private final EnumMap<Role, Relay> relays = new EnumMap<>(Role.class);
    private final CopyOnWriteArrayList<CutWitnessVO> cuts = new CopyOnWriteArrayList<>();
    private final EnumMap<Scenario, List<OperationWitnessVO>> scenarios = new EnumMap<>(Scenario.class);
    private final CopyOnWriteArrayList<WireGate> wireGates = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<TargetCallVO> targetCalls = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Connection> locks = new CopyOnWriteArrayList<>();
    private final ChildFleet fleet;
    private HttpsServer external;
    private volatile TargetGate targetGate;
    private boolean closed;

    static AutomationAcceptanceHarness open(AutomationAcceptanceManifestVO manifest) {
        verifyRevisions(manifest);
        for (Role role : Role.values()) verifyCandidate(manifest.candidate(role));
        var stores = new EnumMap<Role, Store>(Role.class);
        var identities = new HashSet<String>();
        for (Role role : Role.values()) {
            var store = new Store(manifest, manifest.store(role));
            if (!identities.add(store.verify())) throw invalid();
            stores.put(role, store);
        }
        try {
            return new AutomationAcceptanceHarness(manifest, stores);
        } catch (Exception failure) {
            throw orchestrationFailure("harness-open", failure);
        }
    }

    private AutomationAcceptanceHarness(AutomationAcceptanceManifestVO manifest, EnumMap<Role, Store> stores) throws Exception {
        this.manifest = manifest;
        this.stores = stores;
        deadline = Duration.ofSeconds(manifest.phaseTimeoutSeconds());
        tls = makeTls(manifest);
        http = HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        childrenDirectory = Files.createDirectory(manifest.privateRoot().resolve("children"), PosixFilePermissions.asFileAttribute(DIR_MODE));
        gateDirectory = Files.createDirectory(manifest.privateRoot().resolve("gates"), PosixFilePermissions.asFileAttribute(DIR_MODE));
        relayExecutor = Executors.newVirtualThreadPerTaskExecutor();
        fleet = new ChildFleet(this::launch);
        createBusinessTables();
    }

    void launchAll() {
        try {
            relays.put(Role.PROCESS, new Relay(Role.PROCESS));
            relays.put(Role.AGENT, new Relay(Role.AGENT));
            external = createServer();
            external.createContext("/dispatch", this::dispatchEffect);
            external.createContext("/observe", this::observeEffect);
            external.start();
            fleet.launchAll();
        } catch (IOException failure) {
            close();
            throw orchestrationFailure("launch-relays", failure);
        } catch (RuntimeException | AssertionError failure) {
            try { close(); }
            catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    AutomationAcceptanceManifestVO manifest() { return manifest; }
    List<TenantFixtureVO> tenants() { return manifest.tenants(); }
    String runId() { return manifest.runId(); }
    HttpClient http() { return http; }
    URI endpoint(Role role) { return relays.get(role).uri(); }
    Duration deadline() { return deadline; }
    JdbcTemplate observe(Role role) { return stores.get(role).observer; }
    Instant now(Role role) { return observe(role).queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant(); }

    String token(TenantFixtureVO tenant, Identity identity) {
        try {
            Path file = AutomationAcceptanceManifestVO.requirePrivateChild(manifest.privateRoot(), tenant.credentialFiles().get(identity));
            String token = new String(AutomationAcceptanceManifestVO.readBytes(file, 16 * 1024), StandardCharsets.US_ASCII).strip();
            if (!token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) throw invalid();
            return token;
        } catch (IOException failure) {
            throw invalid();
        }
    }

    Path mark(String name) {
        checkedReference(name);
        try {
            return Files.createFile(gateDirectory.resolve(name), PosixFilePermissions.asFileAttribute(FILE_MODE));
        } catch (IOException failure) {
            throw orchestrationFailure("mark", failure);
        }
    }

    void unmark(Path marker) {
        if (!gateDirectory.equals(marker.getParent())) throw invalid();
        try {
            Files.delete(marker);
        } catch (IOException failure) {
            throw orchestrationFailure("unmark", failure);
        }
    }

    int providerCalls() {
        Path file = gateDirectory.resolve("agent-model-calls");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return 0;
        try (var calls = Files.lines(AutomationAcceptanceManifestVO.requirePrivateChild(manifest.privateRoot(), file))) {
            return Math.toIntExact(calls.count());
        } catch (IOException failure) {
            throw orchestrationFailure("model-count", failure);
        }
    }

    Connection lockRoot(long tenantId, String instanceId) {
        try {
            var source = stores.get(Role.PROCESS).credentialSource("lock", false);
            Connection connection = source.getConnection();
            connection.setAutoCommit(false);
            try (var query = connection.prepareStatement("""
                    SELECT instance_id FROM tianji_process.tianji_process_instance_binding
                    WHERE tenant_id=? AND instance_id=? FOR UPDATE
                    """)) {
                query.setLong(1, tenantId);
                query.setString(2, instanceId);
                query.setQueryTimeout(5);
                try (var rows = query.executeQuery()) {
                    if (!rows.next() || !instanceId.equals(rows.getString(1)) || rows.next()) throw invalid();
                }
                locks.add(connection);
                return connection;
            } catch (SQLException | RuntimeException failure) {
                connection.close();
                throw failure;
            }
        } catch (SQLException failure) {
            throw orchestrationFailure("root-cut-lock", failure);
        }
    }

    void unlockRoot(Connection lock) {
        if (!locks.remove(lock)) throw invalid();
        try (lock) {
            lock.rollback();
        } catch (SQLException failure) {
            throw orchestrationFailure("root-cut-unlock", failure);
        }
    }

    CutWitnessVO awaitDurableCut(Cut cut, BooleanSupplier marker, Supplier<Optional<CutWitnessVO>> probe) {
        var witness = awaitCut(cut, deadline, marker, probe);
        cuts.add(witness);
        fleet.observe(witness);
        return witness;
    }

    void replaceChildAtCut(Role role, Runnable whileStopped) { fleet.replaceChildAtCut(role, whileStopped); }
    List<ChildWitnessVO> currentChildren() { return fleet.current(); }

    static CutWitnessVO awaitCut(Cut cut, Duration timeout, BooleanSupplier marker, Supplier<Optional<CutWitnessVO>> proof) {
        return await(timeout, "durable-" + cut.name(), () -> marker.getAsBoolean()
                ? proof.get().filter(witness -> witness.cut() == cut) : Optional.empty());
    }

    <T> T await(String description, Supplier<Optional<T>> proof) { return await(deadline, description, proof); }

    static <T> T await(Duration timeout, String description, Supplier<Optional<T>> proof) {
        long end = System.nanoTime() + timeout.toNanos();
        do {
            var result = proof.get();
            if (result.isPresent()) return result.orElseThrow();
            try {
                Thread.sleep(40);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while observing " + description);
            }
        } while (System.nanoTime() - end < 0);
        throw new AssertionError("Timed out waiting for " + description);
    }

    void record(Scenario scenario, List<OperationWitnessVO> proof) {
        if (proof.isEmpty() || scenarios.putIfAbsent(scenario, List.copyOf(proof)) != null) throw invalid();
        log.info("Automation acceptance witnessed scenario={}", scenario);
    }

    void writeEvidence() {
        Path target = manifest.outputDirectory().resolve("automation-" + manifest.runId() + ".json");
        try {
            writeAtomic(target, evidenceJson(manifest.runId(), fleet.history(), scenarios, cuts, now(Role.PROCESS))
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw orchestrationFailure("write-evidence", failure);
        }
    }

    static String evidenceJson(String runId, List<ChildWitnessVO> children, Map<Scenario, List<OperationWitnessVO>> proof,
                               List<CutWitnessVO> cuts, Instant at) {
        checkedReference(runId);
        boolean passed = proof.keySet().equals(Set.of(Scenario.values())) && proof.values().stream().noneMatch(List::isEmpty)
                && cuts.stream().map(CutWitnessVO::cut).distinct().count() == Cut.values().length
                && children.size() >= Role.values().length + Cut.values().length;
        return JSON.writeValueAsString(Map.of("schemaVersion", 1, "runId", runId, "status", passed ? "PASS" : "INCOMPLETE",
                "observedAt", at.toString(), "children", children, "scenarios", proof, "cuts", cuts));
    }

    WireGate gate(Role role, String path, String matchField, String value, GateMode mode) {
        checkedReference(value);
        var gate = new WireGate(role, path, matchField, value, mode);
        wireGates.add(gate);
        return gate;
    }

    enum GateMode { BEFORE_FORWARD, DROP_AFTER_RESPONSE, DROP_FIRST_RESPONSE }

    final class WireGate implements AutoCloseable {
        private final Role role;
        private final String path;
        private final String matchField;
        private final String value;
        private final GateMode mode;
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger matched = new AtomicInteger();
        private final CopyOnWriteArrayList<String> forwarded = new CopyOnWriteArrayList<>();
        private volatile int upstreamStatus;
        private volatile String bodySha256;
        private volatile boolean forwardFuture;

        WireGate(Role role, String path, String matchField, String value, GateMode mode) {
            this.role = role;
            this.path = path;
            this.matchField = matchField;
            this.value = value;
            this.mode = mode;
        }

        private boolean matches(Role route, HttpExchange exchange, byte[] body) {
            if (role != route || !path.equals(exchange.getRequestURI().getPath()) || !"POST".equals(exchange.getRequestMethod())) return false;
            try {
                return value.equals(JSON.readTree(body).at(matchField).asText());
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        private void pause(byte[] body, int status) throws InterruptedException {
            if (matched.incrementAndGet() == 1) {
                upstreamStatus = status;
                bodySha256 = sha256(body);
            }
            release.await(deadline.toMillis(), TimeUnit.MILLISECONDS);
        }

        boolean observed() { return bodySha256 != null; }
        int upstreamStatus() { return upstreamStatus; }
        String bodySha256() { return bodySha256; }
        List<String> forwardedHashes() { return List.copyOf(forwarded); }

        void allowFutureCalls() {
            forwardFuture = true;
            release.countDown();
        }

        private boolean dropFirst(byte[] body, int status) {
            if (matched.incrementAndGet() != 1) return false;
            upstreamStatus = status;
            bodySha256 = sha256(body);
            return true;
        }

        @Override public void close() {
            wireGates.remove(this);
            release.countDown();
        }
    }

    private final class Relay implements AutoCloseable {
        private final Role role;
        private final HttpsServer server;
        private final AtomicInteger target = new AtomicInteger();

        Relay(Role role) throws IOException {
            this.role = role;
            server = createServer();
            server.createContext("/", this::forward);
            server.start();
        }

        URI uri() { return serverUri(server); }

        private void forward(HttpExchange exchange) {
            try (exchange) {
                URI inbound = exchange.getRequestURI();
                String path = inbound.getRawPath();
                int port = target.get();
                if (port < 1 || !allowedPath(path) || inbound.isAbsolute()) {
                    exchange.sendResponseHeaders(503, -1);
                    return;
                }
                byte[] body = exchange.getRequestBody().readNBytes(HTTP_LIMIT + 1);
                if (body.length > HTTP_LIMIT) {
                    exchange.sendResponseHeaders(413, -1);
                    return;
                }
                var gate = wireGates.stream().filter(candidate -> candidate.matches(role, exchange, body)).findFirst().orElse(null);
                if (gate != null && gate.mode == GateMode.BEFORE_FORWARD && !gate.forwardFuture) {
                    gate.pause(body, 0);
                    return;
                }
                URI child = URI.create("https://127.0.0.1:" + port + inbound);
                var request = HttpRequest.newBuilder(child).timeout(deadline).method(exchange.getRequestMethod(),
                        body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
                exchange.getRequestHeaders().forEach((name, values) -> {
                    if (REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) values.forEach(value -> request.header(name, value));
                });
                var response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
                try (var stream = response.body()) {
                    byte[] answer = stream.readNBytes(HTTP_LIMIT + 1);
                    if (answer.length > HTTP_LIMIT) throw invalid();
                    if (gate != null) gate.forwarded.add(sha256(body));
                    if (gate != null && gate.mode == GateMode.DROP_AFTER_RESPONSE) {
                        gate.pause(body, response.statusCode());
                        return;
                    }
                    if (gate != null && gate.mode == GateMode.DROP_FIRST_RESPONSE && gate.dropFirst(body, response.statusCode())) return;
                    response.headers().map().forEach((name, values) -> {
                        if (RESPONSE_HEADERS.contains(name)) exchange.getResponseHeaders().put(name, values);
                    });
                    exchange.sendResponseHeaders(response.statusCode(), answer.length == 0 ? -1 : answer.length);
                    if (answer.length != 0) exchange.getResponseBody().write(answer);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException failed) {
                log.debug("Automation relay request ended role={} type={}", role, failed.getClass().getSimpleName());
            }
        }

        @Override public void close() { server.stop(0); }
    }

    record TargetCallVO(long tenantId, String effectKey, long generation, String inputSha256,
                        String waitToken, boolean applied, String resultRef, Instant at) { }

    List<TargetCallVO> targetCalls() { return List.copyOf(targetCalls); }

    TargetGate gateExternal(long tenantId, String inputSha256, boolean finalRejection) {
        if (tenantId < 1 || !AutomationAcceptanceManifestVO.isDigest(inputSha256) || targetGate != null) throw invalid();
        var result = new TargetGate(tenantId, inputSha256, finalRejection);
        targetGate = result;
        return result;
    }

    final class TargetGate implements AutoCloseable {
        private final long tenantId;
        private final String inputSha256;
        private final boolean finalRejection;
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile TargetCallVO observed;

        TargetGate(long tenantId, String inputSha256, boolean finalRejection) {
            this.tenantId = tenantId;
            this.inputSha256 = inputSha256;
            this.finalRejection = finalRejection;
        }

        private boolean matches(long incomingTenant, String incomingSha, long generation) {
            return tenantId == incomingTenant && inputSha256.equals(incomingSha) && generation == 1;
        }

        Optional<TargetCallVO> observed() { return Optional.ofNullable(observed); }

        @Override public void close() {
            if (targetGate == this) targetGate = null;
            release.countDown();
        }
    }

    private void dispatchEffect(HttpExchange exchange) {
        try (exchange) {
            if (!"POST".equals(exchange.getRequestMethod()) || !"/dispatch".equals(exchange.getRequestURI().toString())) {
                send(exchange, 404, null);
                return;
            }
            JsonNode body = smallJson(exchange);
            var names = body.propertyNames();
            if (!names.equals(Set.of("tenantId", "effectKey", "generation", "inputSha256"))
                    && !names.equals(Set.of("tenantId", "effectKey", "generation", "inputSha256", "waitToken"))) throw invalid();
            long tenant = number(body, "tenantId");
            long generation = number(body, "generation");
            String key = reference(body, "effectKey");
            String input = digest(body, "inputSha256");
            String token = body.has("waitToken") ? body.path("waitToken").asText() : null;
            if (token != null && !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
            requireTenant(tenant);
            TargetGate candidate = targetGate;
            TargetGate gate = candidate != null && candidate.matches(tenant, input, generation) ? candidate : null;
            boolean applied = gate == null || !gate.finalRejection;
            String receipt = applied ? "br-" + sha256((runId() + ":" + tenant + ":" + key).getBytes(StandardCharsets.UTF_8)) : null;
            try (var connection = stores.get(Role.WORKER).credentialSource("setup", false).getConnection()) {
                connection.setAutoCommit(false);
                if (applied) persistBusinessEffect(connection, tenant, key, input, receipt);
                try (var insert = connection.prepareStatement("""
                        INSERT INTO automation_fixture.target_dispatch
                            (run_id,tenant_id,effect_key,dispatch_generation,input_sha256,applied,result_ref)
                        VALUES (?,?,?,?,?,?,?)
                        """)) {
                    insert.setString(1, runId());
                    insert.setLong(2, tenant);
                    insert.setString(3, key);
                    insert.setLong(4, generation);
                    insert.setString(5, input);
                    insert.setBoolean(6, applied);
                    insert.setString(7, receipt);
                    if (insert.executeUpdate() != 1) throw invalid();
                }
                connection.commit();
            }
            var call = new TargetCallVO(tenant, key, generation, input, token, applied, receipt, now(Role.WORKER));
            targetCalls.add(call);
            if (gate != null) {
                gate.observed = call;
                gate.release.await(deadline.toMillis(), TimeUnit.MILLISECONDS);
            }
            send(exchange, applied ? 200 : 503, applied ? Map.of("businessReceiptId", receipt, "output", Map.of("accepted", true)) : null);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException | SQLException | RuntimeException failed) {
            log.debug("External effect fixture ended type={}", failed.getClass().getSimpleName());
        }
    }

    private void persistBusinessEffect(Connection connection, long tenant, String key, String input, String receipt) throws SQLException {
        try (var insert = connection.prepareStatement("""
                INSERT INTO automation_fixture.business_effect (run_id,tenant_id,effect_key,input_sha256,result_ref)
                VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING
                """)) {
            insert.setString(1, runId());
            insert.setLong(2, tenant);
            insert.setString(3, key);
            insert.setString(4, input);
            insert.setString(5, receipt);
            insert.executeUpdate();
        }
        try (var select = connection.prepareStatement("""
                SELECT input_sha256,result_ref FROM automation_fixture.business_effect
                WHERE run_id=? AND tenant_id=? AND effect_key=?
                """)) {
            select.setString(1, runId());
            select.setLong(2, tenant);
            select.setString(3, key);
            try (var rows = select.executeQuery()) {
                if (!rows.next() || !input.equals(rows.getString(1)) || !receipt.equals(rows.getString(2)) || rows.next()) {
                    throw invalid();
                }
            }
        }
    }

    private void observeEffect(HttpExchange exchange) {
        try (exchange) {
            if (!"POST".equals(exchange.getRequestMethod()) || !"/observe".equals(exchange.getRequestURI().toString())) {
                send(exchange, 404, null);
                return;
            }
            JsonNode body = smallJson(exchange);
            if (!body.propertyNames().equals(Set.of("tenantId", "effectKey", "effectFingerprint"))) throw invalid();
            long tenant = number(body, "tenantId");
            requireTenant(tenant);
            String key = reference(body, "effectKey");
            String fingerprint = digest(body, "effectFingerprint");
            var witnesses = observe(Role.WORKER).query("""
                    SELECT d.dispatch_generation,d.input_sha256,d.applied,d.result_ref,clock_timestamp() observed_at
                      FROM tianji_worker.tianji_worker_effect_receipt w
                      JOIN automation_fixture.target_dispatch d ON d.tenant_id=w.tenant_id AND d.effect_key=w.effect_key
                         AND d.dispatch_generation=w.dispatch_generation AND d.input_sha256=w.business_input_sha256
                      LEFT JOIN automation_fixture.business_effect e ON e.run_id=d.run_id AND e.tenant_id=d.tenant_id
                         AND e.effect_key=d.effect_key AND e.input_sha256=d.input_sha256
                     WHERE w.tenant_id=? AND w.effect_key=? AND w.effect_fingerprint=? AND d.run_id=?
                       AND w.source_id='fixture-worker' AND w.capability_key='fixtureExternal' AND w.capability_version=1
                       AND w.target_id='fixture-external'
                       AND ((d.applied AND d.result_ref=e.result_ref) OR (NOT d.applied AND e.result_ref IS NULL))
                    """, (rs, row) -> new ExternalObservationVO(rs.getLong(1), rs.getString(2), rs.getBoolean(3),
                    rs.getString(4), rs.getObject(5, OffsetDateTime.class).toInstant().toString()), tenant, key, fingerprint, runId());
            if (witnesses.size() != 1) send(exchange, 404, null);
            else send(exchange, 200, witnesses.getFirst());
        } catch (IOException | RuntimeException failed) {
            log.debug("External effect observation ended type={}", failed.getClass().getSimpleName());
        }
    }

    private record ExternalObservationVO(long dispatchGeneration, String inputSha256, boolean applied,
                                         String businessReceiptId, String observedAt) { }

    String persistForm(long tenant, String task, String choice) {
        requireTenant(tenant);
        checkedReference(task);
        checkedReference(choice);
        String result = "form-" + sha256((runId() + ":" + tenant + ":" + task).getBytes(StandardCharsets.UTF_8));
        var jdbc = stores.get(Role.WORKER).setup();
        jdbc.update("""
                INSERT INTO automation_fixture.business_form (run_id,tenant_id,task_id,result_ref,fixture_choice)
                VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING
                """, runId(), tenant, task, result, choice);
        var matches = jdbc.queryForObject("""
                SELECT count(*) FROM automation_fixture.business_form
                WHERE run_id=? AND tenant_id=? AND task_id=? AND result_ref=? AND fixture_choice=?
                """, Integer.class, runId(), tenant, task, result, choice);
        if (!Objects.equals(matches, 1)) throw invalid();
        return result;
    }

    private void requireTenant(long tenant) {
        if (tenants().stream().noneMatch(candidate -> candidate.tenantId() == tenant)) throw invalid();
    }

    private void createBusinessTables() {
        var store = stores.get(Role.WORKER);
        var jdbc = store.setup();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT pg_get_userbyid(nspowner)=current_user FROM pg_namespace WHERE nspname='automation_fixture'
                """, Boolean.class))) throw invalid();
        Integer contents = jdbc.queryForObject("""
                SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='automation_fixture'
                """, Integer.class);
        if (!Objects.equals(contents, 0)) throw invalid();
        jdbc.execute("""
                CREATE TABLE automation_fixture.business_effect (
                    run_id text NOT NULL, tenant_id bigint NOT NULL, effect_key text NOT NULL,
                    input_sha256 char(64) NOT NULL, result_ref text NOT NULL,
                    applied_at timestamptz NOT NULL DEFAULT clock_timestamp(),
                    PRIMARY KEY (run_id,tenant_id,effect_key))
                """);
        jdbc.execute("""
                CREATE TABLE automation_fixture.business_form (
                    run_id text NOT NULL, tenant_id bigint NOT NULL, task_id text NOT NULL,
                    result_ref text NOT NULL, fixture_choice text NOT NULL,
                    PRIMARY KEY (run_id,tenant_id,task_id), UNIQUE (run_id,tenant_id,result_ref))
                """);
        jdbc.execute("""
                CREATE TABLE automation_fixture.target_dispatch (
                    arrival_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    run_id text NOT NULL, tenant_id bigint NOT NULL, effect_key text NOT NULL,
                    dispatch_generation bigint NOT NULL, input_sha256 char(64) NOT NULL,
                    applied boolean NOT NULL, result_ref text, CHECK (applied=(result_ref IS NOT NULL)))
                """);
        String runtime = quoteRole(store.user("runtime"));
        String observer = quoteRole(store.user("observer"));
        jdbc.execute("GRANT USAGE ON SCHEMA automation_fixture TO " + runtime + "," + observer);
        jdbc.execute("GRANT SELECT ON ALL TABLES IN SCHEMA automation_fixture TO " + observer);
        jdbc.execute("GRANT SELECT ON automation_fixture.target_dispatch TO " + runtime);
        jdbc.execute("GRANT SELECT,INSERT ON automation_fixture.business_effect TO " + runtime);
    }

    private ChildProcess launch(Role role, int generation) {
        Process child = null;
        try {
            var candidate = manifest.candidate(role);
            var classpath = verifyCandidate(candidate);
            var store = stores.get(role);
            Path config = childrenDirectory.resolve(role.name().toLowerCase(Locale.ROOT) + "-" + generation + ".properties");
            Path ready = childrenDirectory.resolve(role.name().toLowerCase(Locale.ROOT) + "-" + generation + ".ready");
            String hash = role == Role.WORKER ? candidate.testClassesSha256() : candidate.execJarSha256();
            var properties = childConfiguration(role, generation, candidate, store, ready, hash);
            writeProperties(config, properties);
            String java = Path.of(System.getProperty("java.home")).toRealPath().resolve("bin/java").toString();
            var command = new ArrayList<String>();
            command.add(java);
            if (role == Role.WORKER) {
                command.addAll(List.of("-cp", classpath, role.main()));
            } else {
                Path fixtureClasses = childrenDirectory.resolve(role.name().toLowerCase(Locale.ROOT) + "-" + generation + "-classes");
                copyChildMain(candidate.testClasses(), role, fixtureClasses);
                command.addAll(List.of("-Dloader.main=" + role.main(), "-Dloader.path=" + fixtureClasses,
                        "-cp", candidate.execJar().toString(), LAUNCHER));
            }
            command.add("--spring.config.additional-location=" + config.toUri().toASCIIString());
            var builder = new ProcessBuilder(command).directory(manifest.privateRoot().toFile()).redirectErrorStream(true);
            builder.environment().clear();
            builder.environment().putAll(Map.of("ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS", "false", "OTEL_TRACES_EXPORTER", "none",
                    "OTEL_METRICS_EXPORTER", "none", "OTEL_LOGS_EXPORTER", "none"));
            child = builder.start();
            var pid = child.pid();
            var at = child.info().startInstant().orElseThrow();
            var diagnostics = new CopyOnWriteArrayList<String>();
            startLogFilter(child, diagnostics, role);
            var owned = child;
            var witness = await("ready-" + role.name(), () -> {
                if (!owned.isAlive()) throw new IllegalStateException("Automation child exited role=" + role.name() + " codes=" + diagnostics);
                if (!Files.exists(ready, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
                return Optional.of(readReady(role, generation, pid, at, hash, ready));
            });
            if (role != Role.WORKER) {
                relays.get(role).target.set(witness.port());
                URI health = endpoint(role).resolve("/actuator/health");
                await("product-health-" + role.name(), () -> {
                    if (!owned.isAlive()) throw new IllegalStateException("Automation child exited role=" + role.name() + " codes=" + diagnostics);
                    return healthy(health) ? Optional.of(Boolean.TRUE) : Optional.empty();
                });
            }
            log.info("Automation child ready role={} generation={} pid={}", role, generation, witness.pid());
            return new ChildProcess() {
                @Override public ChildWitnessVO witness() { return witness; }
                @Override public boolean alive() { return owned.isAlive(); }
                @Override public void stop(boolean force) { halt(owned, force); }
            };
        } catch (RuntimeException | AssertionError failure) {
            if (child != null) halt(child, true);
            throw failure;
        } catch (IOException failure) {
            if (child != null) halt(child, true);
            throw orchestrationFailure("launch-" + role.name(), failure);
        }
    }

    private Properties childConfiguration(Role role, int generation, AutomationAcceptanceManifestVO.CandidateLockVO candidate,
                                          Store store, Path ready, String candidateSha) throws IOException {
        var properties = loadProperties(candidate.roleProperties());
        String externalUri = serverUri(external).toASCIIString();
        for (String key : properties.stringPropertyNames()) {
            if (!allowedProperty(key)) throw invalid();
            String value = properties.getProperty(key).replace("@PROCESS_RELAY@", endpoint(Role.PROCESS).toASCIIString())
                    .replace("@AGENT_RELAY@", endpoint(Role.AGENT).toASCIIString()).replace("@EXTERNAL_TARGET@", externalUri);
            verifyEmbeddedOrigins(value);
            properties.setProperty(key, value);
        }
        properties.setProperty(FIXTURE + "run-authorized", "true");
        properties.setProperty(FIXTURE + "run-id", runId());
        properties.setProperty(FIXTURE + "role", role.name());
        properties.setProperty(FIXTURE + "generation", Integer.toString(generation));
        properties.setProperty(FIXTURE + "ready-file", ready.toString());
        properties.setProperty(FIXTURE + "gate-directory", gateDirectory.toString());
        properties.setProperty(FIXTURE + "candidate-sha256", candidateSha);
        properties.setProperty(FIXTURE + "trust-store", manifest.loopbackTls().trustStore().toString());
        properties.setProperty(FIXTURE + "trust-password-file", manifest.loopbackTls().trustPasswordFile().toString());
        if (role != Role.AGENT) properties.setProperty(FIXTURE + "external-target", externalUri);
        properties.setProperty("spring.datasource.url", store.lock.jdbcUrl());
        properties.setProperty("spring.datasource.username", store.user("runtime"));
        properties.setProperty("spring.datasource.password", store.password("runtime"));
        properties.setProperty("spring.flyway.enabled", "false");
        properties.setProperty("spring.sql.init.mode", "never");
        if (role == Role.WORKER) {
            properties.setProperty(FIXTURE + "process-relay", endpoint(Role.PROCESS).toASCIIString());
        } else {
            properties.setProperty("spring.datasource.hikari.schema", store.lock.schema());
            properties.setProperty("server.address", "127.0.0.1");
            properties.setProperty("server.port", "0");
            properties.setProperty("server.ssl.enabled", "true");
        }
        return properties;
    }

    private static boolean allowedProperty(String key) {
        return key.matches("[A-Za-z0-9_.\\[\\]-]{1,256}") && (key.startsWith("stellar.tianji.")
                || key.startsWith("tianji.automation.fixture.") || key.startsWith("egon.cola.")
                || key.startsWith("stellar.egon.") || key.startsWith("flowable.")
                || key.startsWith("management.") || key.startsWith("server.ssl.")
                || key.startsWith("spring.datasource.") || key.startsWith("spring.security.oauth2.")
                || key.startsWith("spring.autoconfigure.") || key.startsWith("spring.main.")
                || key.startsWith("logging.level."));
    }

    private static void verifyEmbeddedOrigins(String value) {
        var origin = HTTP_ORIGIN.matcher(value);
        while (origin.find()) {
            if (!origin.group(1).matches("127\\.0\\.0\\.1:[1-9][0-9]{0,4}")) throw invalid();
        }
        if (value.contains("${") || value.indexOf('\0') >= 0) throw invalid();
    }

    private ChildWitnessVO readReady(Role role, int generation, long pid, Instant at, String hash, Path path) {
        try {
            var declared = loadProperties(AutomationAcceptanceManifestVO.requirePrivateChild(manifest.privateRoot(), path));
            if (!declared.stringPropertyNames().equals(Set.of("runId", "role", "generation", "pid", "port", "candidateSha256"))
                    || !runId().equals(declared.getProperty("runId")) || !role.name().equals(declared.getProperty("role"))
                    || !Integer.toString(generation).equals(declared.getProperty("generation"))
                    || !Long.toString(pid).equals(declared.getProperty("pid")) || !hash.equals(declared.getProperty("candidateSha256"))) throw invalid();
            return new ChildWitnessVO(role, generation, pid, Integer.parseInt(declared.getProperty("port")), hash, at);
        } catch (IOException failure) {
            throw orchestrationFailure("ready-file", failure);
        }
    }

    private boolean healthy(URI health) {
        try {
            var response = http.send(HttpRequest.newBuilder(health).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                return response.statusCode() == 200 && "UP".equals(JSON.readTree(body.readNBytes(4097)).path("status").asText());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Automation health check interrupted");
        } catch (IOException | RuntimeException unavailable) {
            return false;
        }
    }

    private void startLogFilter(Process child, CopyOnWriteArrayList<String> diagnostics, Role role) {
        relayExecutor.submit(() -> {
            try (var output = child.inputReader(StandardCharsets.UTF_8)) {
                String line;
                while ((line = output.readLine()) != null) {
                    var safeLine = line.strip();
                    var apiFailure = PROCESS_API_FAILURE.matcher(safeLine);
                    if (role == Role.PROCESS && apiFailure.find()) {
                        log.warn("Automation child reported Process API failure type={}", apiFailure.group(1));
                    }
                    var match = EXCEPTION.matcher(safeLine);
                    if (match.find() && diagnostics.size() < 20 && !diagnostics.contains(match.group(1))) {
                        diagnostics.add(match.group(1));
                        log.debug("Automation child diagnostic role={} type={}", role, match.group(1));
                    }
                }
            } catch (IOException unavailable) {
                log.debug("Automation child log closed role={}", role);
            }
        });
    }

    private static void halt(Process child, boolean force) {
        if (!child.isAlive()) return;
        try {
            if (!force) {
                child.destroy();
                if (child.waitFor(4, TimeUnit.SECONDS)) return;
            }
            child.destroyForcibly();
            if (!child.waitFor(5, TimeUnit.SECONDS)) throw invalid();
        } catch (InterruptedException interrupted) {
            child.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Automation child shutdown interrupted");
        }
    }

    private static final class Store {
        private final AutomationAcceptanceManifestVO manifest;
        private final AutomationAcceptanceManifestVO.StoreLockVO lock;
        private final Properties credentials;
        private final JdbcTemplate observer;

        Store(AutomationAcceptanceManifestVO manifest, AutomationAcceptanceManifestVO.StoreLockVO lock) {
            this.manifest = manifest;
            this.lock = lock;
            try {
                credentials = loadProperties(AutomationAcceptanceManifestVO.requirePrivateChild(manifest.privateRoot(), lock.credentialsFile()));
                var names = new HashSet<>(Set.of("runtimeUser", "runtimePassword", "observerUser", "observerPassword"));
                if (lock.role() == Role.PROCESS) names.addAll(Set.of("lockUser", "lockPassword"));
                if (lock.role() == Role.WORKER) names.addAll(Set.of("setupUser", "setupPassword"));
                var roles = names.stream().filter(name -> name.endsWith("User")).map(name -> name.substring(0, name.length() - 4)).toList();
                if (!credentials.stringPropertyNames().equals(names) || roles.stream().map(this::user).distinct().count() != roles.size()) {
                    throw invalid();
                }
                observer = template(credentialSource("observer", true));
            } catch (IOException failure) {
                throw invalid();
            }
        }

        String user(String role) {
            String value = credentials.getProperty(role + "User");
            if (value == null || !value.matches("tianji_it_[a-z0-9_]{1,51}")) throw invalid();
            return value;
        }

        String password(String role) {
            String value = credentials.getProperty(role + "Password");
            if (value == null || value.isBlank() || value.length() > 512 || value.chars().anyMatch(Character::isISOControl)) throw invalid();
            return value;
        }

        PGSimpleDataSource credentialSource(String role, boolean readOnly) {
            var source = new PGSimpleDataSource();
            source.setURL(lock.jdbcUrl());
            source.setUser(user(role));
            source.setPassword(password(role));
            source.setReadOnly(readOnly);
            source.setConnectTimeout(4);
            source.setSocketTimeout(manifest.phaseTimeoutSeconds());
            source.setApplicationName("tianji-automation-" + lock.role().name().toLowerCase(Locale.ROOT) + "-" + role);
            return source;
        }

        JdbcTemplate setup() { return template(credentialSource("setup", false)); }

        String verify() {
            var identity = observer.queryForMap("""
                    SELECT current_database() catalog, current_user role, host(inet_server_addr()) host,
                           inet_server_port() port, d.oid database_oid,
                           current_setting('transaction_read_only') read_only
                      FROM pg_database d WHERE d.datname=current_database()
                    """);
            if (!lock.catalog().equals(identity.get("catalog")) || !user("observer").equals(identity.get("role"))
                    || !"127.0.0.1".equals(identity.get("host")) || !"on".equals(identity.get("read_only"))) throw invalid();
            verifyRole(observer, user("observer"));
            var runtime = template(credentialSource("runtime", false));
            if (!lock.catalog().equals(runtime.queryForObject("SELECT current_database()", String.class))
                    || !user("runtime").equals(runtime.queryForObject("SELECT current_user", String.class))) throw invalid();
            verifyRole(runtime, user("runtime"));

            var ledger = lock.schema() + "." + lock.role().historyTable();
            var rows = observer.query("SELECT version,type,script,checksum,success FROM " + ledger
                            + " WHERE version IS NOT NULL ORDER BY installed_rank",
                    (rs, row) -> new HistoryVO(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBoolean(5)));
            if (rows.size() != lock.role().migrationVersion()) throw invalid();
            var checksum = new StringBuilder();
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                var version = Integer.toString(i + 1);
                if (!version.equals(row.version()) || !"SQL".equals(row.type()) || !row.success()
                        || !row.script().matches("V" + version + "__[A-Za-z0-9_]+\\.sql")) throw invalid();
                checksum.append(row.version()).append('\t').append(row.type()).append('\t').append(row.script())
                        .append('\t').append(row.checksum()).append('\t').append(row.success()).append('\n');
            }
            if (!lock.historySha256().equals(sha256(checksum.toString().getBytes(StandardCharsets.UTF_8)))) throw invalid();

            if (lock.role() != Role.WORKER) {
                var outbox = lock.schema() + "." + lock.schema() + "_outbox_schema_history";
                Integer exactOutbox = observer.queryForObject("SELECT count(*) FROM " + outbox
                        + " WHERE version='1' AND type='SQL' AND script='V1__create_transactional_outbox_schema.sql' AND success AND checksum IS NOT NULL", Integer.class);
                if (!Objects.equals(exactOutbox, 1)) throw invalid();
                requireCatalogObject("egon_cola_outbox_message");
            }
            switch (lock.role()) {
                case PROCESS -> {
                    requireCatalogObject("act_ru_execution");
                    requireCatalogObject("act_ru_task");
                    requireCatalogObject("act_hi_procinst");
                    requireCatalogObject("act_dmn_decision");
                    requireCatalogObject("act_dmn_hi_decision_execution");
                }
                case AGENT -> {
                    var vector = observer.queryForObject("SELECT extversion FROM pg_extension WHERE extname='vector'", String.class);
                    if (vector == null || !vector.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw invalid();
                    requireCatalogObject("agent_runtime_binding");
                }
                case WORKER -> requireCatalogObject("tianji_worker_effect_receipt");
            }
            return identity.get("host") + ":" + identity.get("port") + ":" + identity.get("database_oid");
        }

        private void requireCatalogObject(String relation) {
            if (!Boolean.TRUE.equals(observer.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class,
                    lock.schema() + "." + relation))) throw invalid();
        }

        private static void verifyRole(JdbcTemplate jdbc, String role) {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT NOT rolsuper AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication AND NOT rolbypassrls
                       AND NOT has_schema_privilege(rolname, 'public', 'CREATE')
                    FROM pg_roles WHERE rolname=?
                    """, Boolean.class, role))) throw invalid();
        }
    }

    private record HistoryVO(String version, String type, String script, int checksum, boolean success) { }

    private static JdbcTemplate template(PGSimpleDataSource source) {
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(8);
        return jdbc;
    }

    private static String verifyCandidate(AutomationAcceptanceManifestVO.CandidateLockVO candidate) {
        if (!candidate.testClassesSha256().equals(ExpenseAcceptanceHarness.classTreeSha256(candidate.testClasses()))
                || !Files.isRegularFile(candidate.testClasses().resolve(candidate.role().main().replace('.', '/') + ".class"),
                        LinkOption.NOFOLLOW_LINKS)) throw invalid();
        if (candidate.role() == Role.WORKER) return lockedClasspath(candidate);
        if (!candidate.execJarSha256().equals(ExpenseAcceptanceHarness.sha256(candidate.execJar()))) throw invalid();
        try (var archive = new JarFile(candidate.execJar().toFile(), false)) {
            if (archive.getJarEntry(LAUNCHER.replace('.', '/') + ".class") == null || archive.stream().anyMatch(entry ->
                    entry.getName().matches(".*(?:Automation.*AcceptanceMain|AutomationIndependentServicesIT)\\.class"))) throw invalid();
        } catch (IOException unavailable) {
            throw invalid();
        }
        return null;
    }

    /** Supplying the whole test tree lets Boot component-scan unrelated integration fixtures. */
    static void copyChildMain(Path testClasses, Role role, Path destination) throws IOException {
        if (role == Role.WORKER || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw invalid();
        Path relative = Path.of(role.main().replace('.', '/') + ".class");
        String outerClass = relative.getFileName().toString();
        String className = outerClass.substring(0, outerClass.length() - ".class".length());
        Path source = testClasses.resolve(relative.getParent());
        AutomationAcceptanceManifestVO.requireCanonical(source);
        var matcher = Pattern.compile(Pattern.quote(className) + "(?:\\$[A-Za-z0-9_$]+)?\\.class");
        List<Path> matches;
        try (var files = Files.list(source)) {
            matches = files.filter(path -> matcher.matcher(path.getFileName().toString()).matches()).sorted().toList();
        }
        if (matches.isEmpty() || matches.size() > 128 || !matches.contains(source.resolve(outerClass))) throw invalid();
        Path packageDirectory = Files.createDirectories(destination.resolve(relative.getParent()), PosixFilePermissions.asFileAttribute(DIR_MODE));
        for (var match : matches) {
            if (!Files.isRegularFile(AutomationAcceptanceManifestVO.requireCanonical(match), LinkOption.NOFOLLOW_LINKS)) throw invalid();
            writeAtomic(packageDirectory.resolve(match.getFileName()), AutomationAcceptanceManifestVO.readBytes(match, 1024 * 1024));
        }
    }

    private static String lockedClasspath(AutomationAcceptanceManifestVO.CandidateLockVO candidate) {
        if (!candidate.workerClasspathSha256().equals(ExpenseAcceptanceHarness.sha256(candidate.workerClasspath()))) throw invalid();
        try {
            var root = JSON.readTree(AutomationAcceptanceManifestVO.readBytes(candidate.workerClasspath(), 1024 * 1024));
            if (!root.isArray() || root.isEmpty() || root.size() > 2048) throw invalid();
            var entries = new ArrayList<String>();
            var paths = new HashSet<Path>();
            for (var entry : root) {
                if (!entry.isObject() || !entry.propertyNames().equals(Set.of("path", "sha256"))) throw invalid();
                var path = AutomationAcceptanceManifestVO.requireCanonical(Path.of(entry.path("path").asText()));
                String actual = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        ? ExpenseAcceptanceHarness.classTreeSha256(path) : ExpenseAcceptanceHarness.sha256(path);
                if (!paths.add(path) || !actual.equals(entry.path("sha256").asText()) || path.toString().contains(":")) throw invalid();
                entries.add(path.toString());
            }
            if (!paths.contains(candidate.testClasses())) throw invalid();
            return String.join(java.io.File.pathSeparator, entries);
        } catch (IOException unavailable) {
            throw invalid();
        }
    }

    private static void verifyRevisions(AutomationAcceptanceManifestVO manifest) {
        if (Runtime.version().feature() != 25) throw invalid();
        manifest.revisions().forEach((repo, revision) -> {
            try {
                Path repository = AutomationAcceptanceManifestVO.requireCanonical(manifest.checkoutRoot().getParent().resolve(repo));
                var process = new ProcessBuilder("git", "-C", repository.toString(), "rev-parse", "--verify", "HEAD")
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw invalid();
                }
                if (process.exitValue() != 0 || !revision.equals(new String(process.getInputStream().readNBytes(64),
                        StandardCharsets.US_ASCII).strip())) throw invalid();
            } catch (IOException | InterruptedException failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                throw invalid();
            }
        });
    }

    private static SSLContext makeTls(AutomationAcceptanceManifestVO manifest) throws Exception {
        var files = manifest.loopbackTls();
        var trustPassword = passwordChars(files.trustPasswordFile());
        var relayPassword = passwordChars(files.relayPasswordFile());
        try {
            var trust = KeyStore.getInstance(files.trustStore().toFile(), trustPassword);
            var certs = java.util.Collections.list(trust.aliases());
            if (certs.isEmpty()) throw invalid();
            for (String alias : certs) if (!trust.isCertificateEntry(alias)) throw invalid();
            var keys = KeyStore.getInstance(files.relayKeyStore().toFile(), relayPassword);
            var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            tmf.init(trust);
            kmf.init(keys, relayPassword);
            var context = SSLContext.getInstance("TLS");
            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return context;
        } finally {
            Arrays.fill(trustPassword, '\0');
            Arrays.fill(relayPassword, '\0');
        }
    }

    private static char[] passwordChars(Path path) throws IOException {
        var bytes = AutomationAcceptanceManifestVO.readBytes(path, 1024);
        try {
            var decoded = StandardCharsets.UTF_8.decode(java.nio.ByteBuffer.wrap(bytes));
            int size = decoded.remaining();
            if (size > 0 && decoded.get(size - 1) == '\n') size--;
            if (size == 0) throw invalid();
            char[] result = new char[size];
            for (int i = 0; i < size; i++) {
                char value = decoded.get();
                if (Character.isISOControl(value)) throw invalid();
                result[i] = value;
            }
            return result;
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private HttpsServer createServer() throws IOException {
        var server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        server.setExecutor(relayExecutor);
        return server;
    }

    private static URI serverUri(HttpsServer server) { return URI.create("https://127.0.0.1:" + server.getAddress().getPort()); }

    private static boolean allowedPath(String path) {
        return "/actuator/health".equals(path) || path.startsWith("/api/tianji/") && !path.contains("..");
    }

    private static JsonNode smallJson(HttpExchange exchange) throws IOException {
        var bytes = exchange.getRequestBody().readNBytes(8193);
        if (bytes.length == 0 || bytes.length > 8192) throw invalid();
        var parsed = JSON.readTree(bytes);
        if (!parsed.isObject()) throw invalid();
        return parsed;
    }

    private static void send(HttpExchange exchange, int status, Object value) throws IOException {
        if (value == null) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            byte[] answer = JSON.writeValueAsBytes(value);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(status, answer.length);
            exchange.getResponseBody().write(answer);
        }
    }

    private static long number(JsonNode body, String name) {
        var value = body.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 1) throw invalid();
        return value.asLong();
    }

    private static String reference(JsonNode body, String name) {
        String value = body.path(name).asText();
        checkedReference(value);
        return value;
    }

    private static String digest(JsonNode body, String name) {
        String value = body.path(name).asText();
        if (!AutomationAcceptanceManifestVO.isDigest(value)) throw invalid();
        return value;
    }

    private static void checkedReference(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_][A-Za-z0-9_.:-]{0,254}")) throw invalid();
    }

    private static String quoteRole(String role) {
        if (role == null || !role.matches("tianji_it_[a-z0-9_]{1,51}")) throw invalid();
        return '"' + role + '"';
    }

    private static Properties loadProperties(Path input) throws IOException {
        var result = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw invalid();
                return super.put(key, value);
            }
        };
        try (var stream = new java.io.ByteArrayInputStream(AutomationAcceptanceManifestVO.readBytes(input, 64 * 1024))) {
            result.load(stream);
        }
        // Allow replacing a key only after the input itself has passed duplicate detection.
        var copy = new Properties();
        copy.putAll(result);
        return copy;
    }

    private static void writeProperties(Path path, Properties values) throws IOException {
        try (var output = new java.io.ByteArrayOutputStream()) {
            values.store(output, null);
            writeAtomic(path, output.toByteArray());
        }
    }

    private static void writeAtomic(Path path, byte[] value) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw invalid();
        Path temporary = Files.createTempFile(path.getParent(), ".automation-", ".tmp", PosixFilePermissions.asFileAttribute(FILE_MODE));
        try {
            Files.write(temporary, value);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Required digest algorithm unavailable");
        }
    }

    private static IllegalArgumentException invalid() { return AutomationAcceptanceManifestVO.invalid(); }

    private static IllegalStateException orchestrationFailure(String phase, Throwable failure) {
        log.warn("Automation acceptance orchestration failed phase={} type={}", phase, failure.getClass().getSimpleName());
        return new IllegalStateException("Automation fixture failed in " + phase + " (" + failure.getClass().getSimpleName() + ")");
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        try {
            fleet.close();
        } catch (RuntimeException failed) {
            failure = failed;
        } finally {
            wireGates.forEach(WireGate::close);
            var gate = targetGate;
            if (gate != null) gate.close();
            relays.values().forEach(Relay::close);
            if (external != null) external.stop(0);
            locks.forEach(this::unlockRoot);
            relayExecutor.shutdownNow();
            try (var entries = Files.walk(childrenDirectory)) {
                for (Path file : entries.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            } catch (IOException failed) {
                if (failure == null) failure = orchestrationFailure("temporary-child-config-cleanup", failed);
                else failure.addSuppressed(orchestrationFailure("temporary-child-config-cleanup", failed));
            }
        }
        if (failure != null) throw failure;
    }
}
