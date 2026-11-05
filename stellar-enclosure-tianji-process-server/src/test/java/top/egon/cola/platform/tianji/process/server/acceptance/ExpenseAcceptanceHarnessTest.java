package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.ArtifactLockDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.ChildIdentityDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.ChildLaunchDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.ChildRole;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.EndpointsDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.ExpenseAcceptanceManifestDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.ExpensePathResultDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.OwnedChild;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Phase;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.PhaseLimitsDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.RevisionLockDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Scenario;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.SourceWitnessDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.StoreBindingDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.StoreOwner;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.StoreWitnessDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Suite;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.TenantBindingDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.TlsBindingDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.WitnessDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.WitnessKind;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.WitnessState;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpenseAcceptanceHarnessTest {
    private static final Instant NOW = Instant.parse("2026-11-05T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path directory;

    @Test
    void acceptsOnlyExplicitOwnedManifestWithoutSideEffects() throws Exception {
        var fixture = new Fixture(directory);
        assertEquals(Optional.empty(), assertDoesNotThrow(() -> ExpenseAcceptanceHarness.readManifest((String) null)));
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceHarness.readManifest(""));
        assertEquals(fixture.manifest, assertDoesNotThrow(() -> ExpenseAcceptanceHarness.readManifest(fixture.manifestPath)));
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceHarness.readManifest(fixture.writeManifest("denied.json",
                fixture.manifestJson().replace("\"runAuthorized\":true", "\"runAuthorized\":false"))));
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceHarness.readManifest(fixture.writeManifest("duplicate.json",
                fixture.manifestJson().replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"))));
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceHarness.readManifest(fixture.writeManifest("unknown.json",
                "{\"unexpected\":true," + fixture.manifestJson().substring(1))));
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceHarness.readManifest(fixture.writeManifest("coercion.json",
                fixture.manifestJson().replace("\"runAuthorized\":true", "\"runAuthorized\":\"true\""))));
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceHarness.readManifest(fixture.writeManifest("too-large.json", " ".repeat(65_537))));
        Path insecure = fixture.writeManifest("insecure.json", fixture.manifestJson());
        Files.setPosixFilePermissions(insecure, PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceHarness.readManifest(insecure));
        Path symlink = Files.createSymbolicLink(fixture.privateRoot.resolve("linked.json"), fixture.manifestPath);
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceHarness.readManifest(symlink));
        assertEquals(0, fixture.launches.size());
    }

    @Test
    void validatesExactArtifactsTwoTenantsAndIndependentStores() throws Exception {
        var fixture = new Fixture(directory);
        assertEquals(List.of(20L, 21L), assertDoesNotThrow(() -> ExpenseAcceptanceHarness.readManifest(fixture.manifestPath))
                .tenants().stream().map(TenantBindingDTO::tenantId).toList());
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceHarness.readManifest(fixture.writeManifest("bad-jar.json",
                fixture.manifestJson().replace(fixture.manifest.agent().execJarSha256(), "0".repeat(64)))));
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceHarness.readManifest(fixture.writeManifest("same-tenant.json",
                fixture.manifestJson().replace("\"tenantId\":21", "\"tenantId\":20"))));
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceHarness.readManifest(fixture.writeManifest("same-store.json",
                fixture.manifestJson().replace("tianji_it_processdb", "tianji_it_agentdb"))));
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceHarness.readManifest(fixture.writeManifest("foreign-host.json",
                fixture.manifestJson().replace("https://127.0.0.1:8411", "https://not-isolated.example:8411"))));
        var source = fixture.witness();
        var wrongRevisions = new RevisionLockDTO("f".repeat(40), "b".repeat(40), "c".repeat(40), "d".repeat(40), "e".repeat(40));
        assertThrows(IllegalStateException.class, () -> ExpenseAcceptanceHarness.open(fixture.manifest,
                ignored -> new SourceWitnessDTO(wrongRevisions, source.stores()), fixture::launch, fixture.ticks::get,
                CLOCK, duration -> fixture.ticks.addAndGet(duration.toNanos())));
        assertEquals(0, fixture.launches.size());
    }

    @Test
    void childArgumentsExposeOnlyPrivateConfigurationReferences() throws Exception {
        var fixture = new Fixture(directory);
        var child = new FakeChild(1001, NOW.minusSeconds(2), false);
        fixture.children.add(child);
        try (var harness = fixture.open()) {
            var settings = fixture.configuration(ChildRole.AGENT);
            var wrong = new java.util.HashMap<>(settings);
            wrong.put("spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/tianji_it_wrong");
            assertThrows(IllegalArgumentException.class, () -> harness.writeChildConfiguration(ChildRole.AGENT, wrong));
            Path config = assertDoesNotThrow(() -> harness.writeChildConfiguration(ChildRole.AGENT, settings));
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(config));
            var privateProperties = new java.util.Properties();
            try (var input = Files.newInputStream(config)) { privateProperties.load(input); }
            assertEquals("true", privateProperties.getProperty("tianji.expense.fixture.run-authorized"));
            assertEquals("AGENT", privateProperties.getProperty("tianji.expense.fixture.role"));
            assertEquals("20", privateProperties.getProperty("tianji.expense.fixture.tenant-1.id"));
            assertThrows(IllegalStateException.class, () -> harness.writeChildConfiguration(ChildRole.AGENT, settings));
            assertEquals(1001, assertDoesNotThrow(() -> harness.startChild(ChildRole.AGENT, config, ignored -> true)).pid());
            ChildLaunchDTO launch = fixture.launches.getFirst();
            assertTrue(launch.arguments().contains("org.springframework.boot.loader.launch.PropertiesLauncher"));
            assertTrue(launch.arguments().contains("--spring.config.additional-location=" + config.toUri().toASCIIString()));
            assertTrue(launch.arguments().stream().noneMatch(arg -> arg.contains("fixture-private-password") || arg.contains("jdbc:postgresql")));
            assertEquals(Map.of("ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS", "false"), launch.environment());
        }
        assertEquals(1, child.gracefulStops);
    }

    @Test
    void timeoutAndCleanupAffectOnlyOwnedChildren() throws Exception {
        var fixture = new Fixture(directory);
        var initialAgent = new FakeChild(1001, NOW.minusSeconds(4), true);
        var process = new FakeChild(2001, NOW.minusSeconds(3), false);
        var replacement = new FakeChild(1002, NOW.minusSeconds(2), false);
        fixture.children.addAll(List.of(initialAgent, process, replacement));
        try (var harness = fixture.open()) {
            Path agentConfig = harness.writeChildConfiguration(ChildRole.AGENT, fixture.configuration(ChildRole.AGENT));
            Path processConfig = harness.writeChildConfiguration(ChildRole.PROCESS, fixture.configuration(ChildRole.PROCESS));
            var agentIdentity = harness.startChild(ChildRole.AGENT, agentConfig, ignored -> true);
            harness.startChild(ChildRole.PROCESS, processConfig, ignored -> true);
            Path newConfig = harness.writeChildConfiguration(ChildRole.AGENT, fixture.configuration(ChildRole.AGENT));
            var unrelated = new ChildIdentityDTO(ChildRole.AGENT, 1, 9999, NOW);
            assertThrows(IllegalArgumentException.class, () -> harness.replaceChild(unrelated, newConfig, ignored -> true));
            assertEquals(0, initialAgent.gracefulStops);
            assertEquals(1002, harness.replaceChild(agentIdentity, newConfig, ignored -> true).pid());
            assertEquals(List.of(Duration.ofSeconds(10), Duration.ofSeconds(5)), initialAgent.waits);
            assertEquals(1, initialAgent.forceStops);
            assertEquals(0, process.gracefulStops);
            var deadline = harness.beginPhase(Phase.RECOVERY);
            assertEquals("first-witness", harness.await(deadline, () -> Optional.of("first-witness")));
            assertThrows(IllegalStateException.class, () -> harness.await(deadline, Optional::empty));
            assertThrows(IllegalStateException.class, () -> harness.await(deadline, () -> Optional.of("late-witness")));
            assertEquals(Duration.ofSeconds(1).toNanos(), fixture.ticks.get());
        }
        assertEquals(1, process.gracefulStops);
        assertEquals(1, replacement.gracefulStops);
        assertEquals(3, fixture.launches.size());
    }

    @Test
    void publishesCompleteRedactedEvidenceOnce() throws Exception {
        var fixture = new Fixture(directory);
        fixture.children.add(new FakeChild(1001, NOW.minusSeconds(2), false));
        fixture.children.add(new FakeChild(2001, NOW.minusSeconds(1), false));
        var harness = fixture.open();
        try (harness) {
            for (var role : ChildRole.values()) {
                var config = harness.writeChildConfiguration(role, fixture.configuration(role));
                harness.startChild(role, config, ignored -> true);
            }
            assertThrows(IllegalArgumentException.class, () -> harness.publishEvidence(harness.evidence(true, List.of())));
        }
        var rows = new ArrayList<ExpensePathResultDTO>();
        for (long tenant : List.of(20L, 21L)) {
            rows.add(path(tenant, Scenario.NORMAL));
            rows.add(path(tenant, Scenario.AGENT_FAILURE));
            rows.add(path(tenant, Scenario.MANUAL_CASE));
        }
        var evidence = harness.evidence(true, rows);
        Path path = assertDoesNotThrow(() -> harness.publishEvidence(evidence));
        byte[] content = Files.readAllBytes(path);
        var parsed = JSON.readTree(content);
        assertTrue(parsed.path("passed").booleanValue());
        assertEquals(6, parsed.path("paths").size());
        assertEquals(2, parsed.path("exits").size());
        assertFalse(new String(content, StandardCharsets.UTF_8).contains("fixture-private-password"));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(path));
        assertThrows(IllegalStateException.class, () -> harness.publishEvidence(evidence));
        assertArrayEquals(content, Files.readAllBytes(path));
    }

    private static ExpensePathResultDTO path(long tenant, Scenario scenario) {
        var required = EnumSet.range(WitnessKind.START_COMMAND, WitnessKind.ORIGINAL_RECEIPT);
        if (scenario == Scenario.NORMAL) required.add(WitnessKind.NOTICE_RECEIPT);
        else required.add(WitnessKind.UNDO_RECEIPT);
        if (scenario == Scenario.MANUAL_CASE) required.addAll(EnumSet.of(WitnessKind.HUMAN_TASK,
                WitnessKind.CASE_INSTANCE, WitnessKind.CASE_COMPLETION));
        var witnesses = new EnumMap<WitnessKind, WitnessDTO>(WitnessKind.class);
        for (var kind : WitnessKind.values()) witnesses.put(kind, required.contains(kind)
                ? new WitnessDTO(WitnessState.PROVED, "synthetic-" + kind.name())
                : new WitnessDTO(WitnessState.NOT_REACHED, null));
        return new ExpensePathResultDTO(tenant, scenario, "operation-1", "expense-1", "correlation-1", true, witnesses);
    }

    private static final class Fixture {
        final Path privateRoot;
        final Path manifestPath;
        final ExpenseAcceptanceManifestDTO manifest;
        final AtomicLong ticks = new AtomicLong();
        final List<ChildLaunchDTO> launches = new ArrayList<>();
        final List<FakeChild> children = new ArrayList<>();
        final AtomicInteger selected = new AtomicInteger();

        Fixture(Path directory) throws Exception {
            Path parent = directory.toRealPath();
            Path checkout = Files.createDirectory(parent.resolve("checkout"));
            privateRoot = Files.createDirectory(parent.resolve("private"),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            Path output = Files.createDirectory(privateRoot.resolve("evidence"),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            Path resources = Files.createDirectories(checkout.resolve("stellar-enclosure-tianji-process-server/src/test/resources"));
            var revisions = new RevisionLockDTO("a".repeat(40), "b".repeat(40), "c".repeat(40), "d".repeat(40), "e".repeat(40));
            var agent = artifact(checkout, "stellar-enclosure-tianji-agent-server", (byte) 1);
            var process = artifact(checkout, "stellar-enclosure-tianji-process-server", (byte) 2);
            var tenants = List.of(tenant(20, "501"), tenant(21, "601"));
            var stores = List.of(store(StoreOwner.AGENT, "tianji_agent", "0.8.2"), store(StoreOwner.PROCESS, "tianji_process", "NONE"),
                    store(StoreOwner.BUSINESS, "tianji_worker", "NONE"));
            var tls = new TlsBindingDTO(file("trust.p12"), file("agent.p12"), file("process.p12"), file("relay.p12"), file("tls.txt"));
            var endpoints = new EndpointsDTO("https://127.0.0.1:8410", "https://127.0.0.1:8411",
                    "https://127.0.0.1:8412", "https://127.0.0.1:8413", "https://127.0.0.1:8414", "http://127.0.0.1:8415/otlp");
            manifest = new ExpenseAcceptanceManifestDTO(1, "expense-run-1", true, Suite.BUSINESS,
                    checkout.toString(), privateRoot.toString(), output.toString(), resources.toString(), revisions,
                    agent, process, tenants, stores, tls, endpoints, new PhaseLimitsDTO(1, 1, 1, 1, 1));
            manifestPath = writeManifest("manifest.json", manifestJson());
        }

        ExpenseAcceptanceHarness open() {
            return assertDoesNotThrow(() -> ExpenseAcceptanceHarness.open(manifest, ignored -> witness(), this::launch,
                    ticks::get, CLOCK, duration -> ticks.addAndGet(duration.toNanos())));
        }

        SourceWitnessDTO witness() {
            return new SourceWitnessDTO(manifest.revisions(), manifest.stores().stream().map(store -> new StoreWitnessDTO(
                    store.owner(), store.jdbcUrl(), store.serverIdentity(), store.schema(), store.postgresVersion(), store.pgvectorVersion(),
                    store.historySha256(), store.setupRole(), store.runtimeRole(), store.observerRole(), true)).toList());
        }

        OwnedChild launch(ChildLaunchDTO launch) {
            launches.add(launch);
            return children.get(selected.getAndIncrement());
        }

        Map<String, String> configuration(ChildRole role) {
            URI endpoint = URI.create(role == ChildRole.AGENT ? manifest.endpoints().agent() : manifest.endpoints().process());
            var source = manifest.stores().stream().filter(store -> store.owner().name().equals(role.name())).findFirst().orElseThrow();
            var keyStore = Path.of(role == ChildRole.AGENT ? manifest.tls().agentKeyStore() : manifest.tls().processKeyStore());
            return Map.of("server.address", "127.0.0.1", "server.port", Integer.toString(endpoint.getPort()),
                    "server.ssl.enabled", "true", "server.ssl.key-store", keyStore.toUri().toASCIIString(),
                    "spring.flyway.enabled", "false", "spring.sql.init.mode", "never", "spring.datasource.url", source.jdbcUrl(),
                    "spring.datasource.username", source.runtimeRole(), "spring.datasource.password", "fixture-private-password");
        }

        String manifestJson() {
            return JSON.writeValueAsString(manifest);
        }

        Path writeManifest(String name, String value) throws Exception {
            Path path = Files.createFile(privateRoot.resolve(name), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            return Files.writeString(path, value);
        }

        private String file(String name) throws Exception {
            return writeManifest(name, "fixture-private-file").toString();
        }

        private TenantBindingDTO tenant(long id, String role) throws Exception {
            return new TenantBindingDTO(id, "https://test-issuer.example/tenant/" + id, "owner", List.of("reviewer-one", "reviewer-two"),
                    List.of(role, Long.toString(Long.parseLong(role) + 1), Long.toString(Long.parseLong(role) + 2)),
                    "tianji-fixture", "https://fixture-process.example", "https://fixture-agent.example", file("tenant-" + id + ".txt"));
        }

        private StoreBindingDTO store(StoreOwner owner, String schema, String vector) throws Exception {
            String name = owner.name().toLowerCase(java.util.Locale.ROOT);
            return new StoreBindingDTO(owner, "jdbc:postgresql://127.0.0.1:5432/tianji_it_" + name + "db", "fixture-local-pg",
                    schema, "18.3", vector, "f".repeat(64), "tianji_it_" + name + "_setup", "tianji_it_" + name + "_runtime",
                    "tianji_it_" + name + "_read", file(name + "-db.txt"));
        }

        private static ArtifactLockDTO artifact(Path checkout, String module, byte content) throws Exception {
            Path classes = Files.createDirectories(checkout.resolve(module).resolve("target/test-classes"));
            Path jar = classes.getParent().resolve(module + "-exec.jar");
            boolean agent = module.endsWith("agent-server");
            String base = agent ? "top/egon/cola/platform/tianji/agent/server/" : "top/egon/cola/platform/tianji/process/server/";
            String app = agent ? "TianjiAgentServerApplication.class" : "TianjiProcessServerApplication.class";
            String name = base + "acceptance/" + (agent ? "ExpenseAgentAcceptanceMain.class" : "ExpenseProcessAcceptanceMain.class");
            try (var archive = new JarOutputStream(Files.newOutputStream(jar))) {
                for (String entry : List.of("org/springframework/boot/loader/launch/PropertiesLauncher.class", "BOOT-INF/classes/" + base + app)) {
                    archive.putNextEntry(new JarEntry(entry));
                    archive.write(content);
                    archive.closeEntry();
                }
            }
            byte[] classFile = {content, 42};
            Path file = classes.resolve(name);
            Files.createDirectories(file.getParent());
            Files.write(file, classFile);
            byte[] relative = name.getBytes(StandardCharsets.UTF_8);
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(relative.length).array());
            digest.update(relative);
            digest.update(MessageDigest.getInstance("SHA-256").digest(classFile));
            return new ArtifactLockDTO(jar.toString(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))),
                    classes.toString(), HexFormat.of().formatHex(digest.digest()));
        }
    }

    private static final class FakeChild implements OwnedChild {
        private final long pid;
        private final Instant startedAt;
        private final boolean needsForce;
        private boolean alive = true;
        private int gracefulStops;
        private int forceStops;
        private final List<Duration> waits = new ArrayList<>();

        FakeChild(long pid, Instant startedAt, boolean needsForce) {
            this.pid = pid;
            this.startedAt = startedAt;
            this.needsForce = needsForce;
        }

        public long pid() { return pid; }
        public Instant startedAt() { return startedAt; }
        public boolean alive() { return alive; }
        public void requestStop() { gracefulStops++; }
        public void forceStop() { forceStops++; }
        public boolean awaitExit(Duration duration) {
            waits.add(duration);
            if (needsForce && forceStops == 0) return false;
            alive = false;
            return true;
        }
        public int exitCode() { return forceStops == 0 ? 0 : 137; }
    }

    @Test
    void relayDropsOnlyExactOwnedWorkerCompletionAfterCommit() {
        var forwards = new AtomicInteger();
        var commit = new java.util.concurrent.atomic.AtomicBoolean();
        try (var relay = new ExpenseAcceptanceFaultRelay(System::nanoTime, (request, timeout) -> {
            forwards.incrementAndGet();
            return new ExpenseAcceptanceFaultRelay.ResponseDTO(200, Map.of(), request.body());
        }, relayWitnesses(commit, new java.util.concurrent.atomic.AtomicBoolean()))) {
            var phase = ExpenseAcceptanceFaultRelay.FaultPhase.DROP_WORKER_COMPLETE_AFTER_BUSINESS_COMMIT;
            assertThrows(IllegalArgumentException.class, () -> relay.arm(new ExpenseAcceptanceFaultRelay.FaultWindowDTO(
                    phase, 42, "source", "instance", null, "job", null, null, Duration.ofSeconds(5))));
            relay.arm(new ExpenseAcceptanceFaultRelay.FaultWindowDTO(phase, 42, "source", "instance", "occurrence",
                    "job", null, null, Duration.ofSeconds(5)));
            assertThrows(IllegalStateException.class, () -> relay.arm(new ExpenseAcceptanceFaultRelay.FaultWindowDTO(
                    phase, 42, "source", "instance", "occurrence", "job", null, null, Duration.ofSeconds(5))));
            var unrelated = relayWorkerComplete("other-job", "other-occurrence");
            assertArrayEquals(unrelated.body(), relay.exchange(unrelated).body());
            assertEquals(1, forwards.get());
            var exact = relayWorkerComplete("job", "occurrence");
            assertThrows(IllegalStateException.class, () -> relay.exchange(exact));
            assertEquals(1, forwards.get());
            commit.set(true);
            assertThrows(ExpenseAcceptanceFaultRelay.ConnectionDropped.class, () -> relay.exchange(exact));
            assertEquals("occurrence", relay.awaitObserved(phase, Duration.ofMillis(100)).occurrenceId());
            assertEquals(1, forwards.get());
            assertEquals(200, relay.exchange(exact).status());
            assertEquals(2, forwards.get());
            assertThrows(IllegalArgumentException.class, () -> relay.exchange(new ExpenseAcceptanceFaultRelay.RequestDTO(
                    ExpenseAcceptanceFaultRelay.Direction.WORKER, "POST", "/api/tianji/v1/worker-tasks/complete?ignored",
                    exact.headers(), exact.body())));
        }
    }

    @Test
    void relayDefersThenDropsCommittedResultAndObservesOnlyReplacementFingerprint() {
        var event = relayTerminalEvent("c");
        var request = relayResultRequest(event);
        var json = JsonMapper.builder().build();
        var receipt = new top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt(event.eventId(),
                event.fingerprint(),
                top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum.RECEIVED,
                Instant.parse("2026-11-05T05:00:01Z"));
        var forwards = new AtomicInteger();
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        try (var relay = new ExpenseAcceptanceFaultRelay(System::nanoTime, (incoming, timeout) -> {
            forwards.incrementAndGet();
            assertArrayEquals(request.body(), incoming.body());
            return new ExpenseAcceptanceFaultRelay.ResponseDTO(202, Map.of(), json.writeValueAsBytes(receipt));
        }, relayWitnesses(new java.util.concurrent.atomic.AtomicBoolean(true), ready))) {
            var defer = ExpenseAcceptanceFaultRelay.FaultPhase.DEFER_RESULT_UNTIL_TIMER_OR_RESTART;
            var drop = ExpenseAcceptanceFaultRelay.FaultPhase.DROP_COMMITTED_RESULT_202;
            var resend = ExpenseAcceptanceFaultRelay.FaultPhase.OBSERVE_REPLACEMENT_RESEND;
            assertThrows(IllegalStateException.class, () -> relay.arm(relayResultWindow(resend, event)));
            relay.arm(relayUnresolvedWindow(defer));
            assertEquals(503, relay.exchange(request).status());
            var deferred = relay.awaitObserved(defer, Duration.ofMillis(100));
            assertEquals(event.eventId(), deferred.eventId());
            assertEquals(event.link().activityOccurrenceId(), deferred.occurrenceId());
            relay.release(defer);
            assertEquals(0, forwards.get());

            relay.arm(relayUnresolvedWindow(drop));
            assertThrows(ExpenseAcceptanceFaultRelay.ConnectionDropped.class, () -> relay.exchange(request));
            var dropped = relay.awaitObserved(drop, Duration.ofMillis(100));
            assertEquals(event.fingerprint(), dropped.fingerprint());
            assertEquals(event.link().activityOccurrenceId(), dropped.occurrenceId());
            assertThrows(IllegalArgumentException.class, () -> relay.arm(relayUnresolvedWindow(resend)));
            assertEquals(1, forwards.get());
            assertThrows(IllegalStateException.class, () -> relay.arm(relayResultWindow(resend, relayTerminalEvent("d"))));
            relay.arm(relayResultWindow(resend, event));
            assertThrows(IllegalStateException.class, () -> relay.exchange(relayResultRequest(relayTerminalEvent("d"))));
            assertThrows(IllegalStateException.class, () -> relay.exchange(request));
            assertEquals(1, forwards.get());
            ready.set(true);
            assertEquals(202, relay.exchange(request).status());
            assertEquals(event.fingerprint(), relay.awaitObserved(resend, Duration.ofMillis(100)).fingerprint());
            assertEquals(2, forwards.get());
        }
    }

    @Test
    void s12ReplayBuildsTheExactUpstreamResultHeaderWithoutOpeningATransport() {
        String wire = replayWire('1', '2');
        var result = withReplayLink(relayResultRequest(relayTerminalEvent("c")), List.of(wire));
        var origin = URI.create("https://127.0.0.1:18443");
        var timeout = Duration.ofSeconds(1);
        var header = top.egon.cola.platform.tianji.client.AgentResultTraceLinkCarrier.HEADER;
        var built = ExpenseAcceptanceFaultRelay.upstreamRequest(origin, result, timeout, true);
        assertEquals("POST", built.method());
        assertEquals("https://127.0.0.1:18443/api/tianji/v1/agent-result-inbox", built.uri().toString());
        assertEquals(List.of(wire), built.headers().allValues(header));
        assertTrue(ExpenseAcceptanceFaultRelay.upstreamRequest(origin, result, timeout, false).headers().allValues(header).isEmpty());
        var worker = withReplayLink(relayWorkerComplete("job", "occurrence"), List.of(wire));
        assertTrue(ExpenseAcceptanceFaultRelay.upstreamRequest(origin, worker, timeout, true).headers().allValues(header).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceFaultRelay.upstreamRequest(
                URI.create("https://remote.invalid:18443"), result, timeout, true));
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceFaultRelay.upstreamRequest(origin,
                withReplayLink(result, List.of(wire, wire)), timeout, true));
        assertThrows(IllegalArgumentException.class, () -> ExpenseAcceptanceFaultRelay.upstreamRequest(origin,
                withReplayLink(result, List.of("00-" + "0".repeat(32) + "-" + "2".repeat(16) + "-01")), timeout, true));
    }

    @Test
    void s12ReplayBindsOnlyTwoCommittedForwardedLinksAndClosesTheWindowGap() {
        var event = relayTerminalEvent("c");
        var request = relayResultRequest(event);
        String lostLink = replayWire('1', '2');
        String replayLink = replayWire('3', '4');
        var lost = withReplayLink(request, List.of(lostLink));
        var replay = withReplayLink(request, List.of(replayLink));
        var receipt = new top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt(event.eventId(),
                event.fingerprint(), top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum.RECEIVED,
                Instant.parse("2026-11-05T05:00:01Z"));
        var forwards = new AtomicInteger();
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        var drop = ExpenseAcceptanceFaultRelay.FaultPhase.DROP_COMMITTED_RESULT_202;
        var resend = ExpenseAcceptanceFaultRelay.FaultPhase.OBSERVE_REPLACEMENT_RESEND;
        try (var relay = new ExpenseAcceptanceFaultRelay(System::nanoTime, (incoming, timeout) -> {
            forwards.incrementAndGet();
            assertArrayEquals(request.body(), incoming.body());
            return new ExpenseAcceptanceFaultRelay.ResponseDTO(202, Map.of(),
                    JsonMapper.builder().build().writeValueAsBytes(receipt));
        }, relayWitnesses(new java.util.concurrent.atomic.AtomicBoolean(true), ready), true)) {
            assertThrows(IllegalStateException.class, () -> relay.exchange(lost));
            assertThrows(IllegalArgumentException.class, () -> relay.arm(relayUnresolvedWindow(
                    ExpenseAcceptanceFaultRelay.FaultPhase.DEFER_RESULT_UNTIL_TIMER_OR_RESTART)));
            relay.arm(relayUnresolvedWindow(drop));
            assertThrows(IllegalArgumentException.class, () -> relay.exchange(request));
            assertThrows(ExpenseAcceptanceFaultRelay.ConnectionDropped.class, () -> relay.exchange(lost));
            assertEquals(lostLink, relay.committedResultLink(drop));
            assertThrows(IllegalArgumentException.class, () -> relay.arm(relayUnresolvedWindow(drop)));
            assertThrows(IllegalStateException.class, () -> relay.exchange(replay));
            relay.arm(relayResultWindow(resend, event));
            assertThrows(IllegalStateException.class, () -> relay.exchange(replay));
            assertThrows(IllegalStateException.class, () -> relay.committedResultLink(resend));
            assertEquals(1, forwards.get());
            ready.set(true);
            assertEquals(202, relay.exchange(replay).status());
            assertEquals(replayLink, relay.committedResultLink(resend));
            assertThrows(IllegalStateException.class, () -> relay.exchange(replay));
            assertEquals(2, forwards.get());
        }
    }

    private static String replayWire(char trace, char span) {
        return "00-" + String.valueOf(trace).repeat(32) + "-" + String.valueOf(span).repeat(16) + "-01";
    }

    private static ExpenseAcceptanceFaultRelay.RequestDTO withReplayLink(ExpenseAcceptanceFaultRelay.RequestDTO request,
                                                                         List<String> values) {
        var headers = new java.util.HashMap<>(request.headers());
        headers.put(top.egon.cola.platform.tianji.client.AgentResultTraceLinkCarrier.HEADER, values);
        return new ExpenseAcceptanceFaultRelay.RequestDTO(request.direction(), request.method(), request.rawPath(), headers,
                request.body());
    }

    @Test
    void relayHoldsActualLinkedResponseWhileResultCanBeStored() throws Exception {
        var event = relayTerminalEvent("c");
        var json = JsonMapper.builder().build();
        var run = new top.egon.cola.platform.tianji.contract.agent.AgentRunDTO("run", "expense", 1, null,
                top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.QUEUED, null, false, 0, 0, 0,
                event.finishedAt().plusSeconds(30), event.finishedAt(), null);
        var lookup = new top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery(
                event.idempotencyKey(), event.link());
        byte[] linkedReply = json.writeValueAsBytes(
                new top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartResult(lookup, run));
        var receipt = new top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt(event.eventId(),
                event.fingerprint(),
                top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum.RECEIVED,
                event.finishedAt());
        var commit = new java.util.concurrent.atomic.AtomicBoolean(true);
        try (var relay = new ExpenseAcceptanceFaultRelay(System::nanoTime, (incoming, timeout) ->
                new ExpenseAcceptanceFaultRelay.ResponseDTO(202, Map.of(),
                        incoming.direction() == ExpenseAcceptanceFaultRelay.Direction.LINKED
                                ? linkedReply : json.writeValueAsBytes(receipt)),
                relayWitnesses(commit, commit, observed -> !"unowned".equals(observed.occurrenceId())))) {
            var hold = ExpenseAcceptanceFaultRelay.FaultPhase.HOLD_LINKED_SUCCESS;
            var drop = ExpenseAcceptanceFaultRelay.FaultPhase.DROP_COMMITTED_RESULT_202;
            relay.arm(relayUnresolvedWindow(hold));
            assertEquals(202, assertDoesNotThrow(() -> relay.exchange(relayLinkedRequest("foreign-source", "instance", "other"))).status());
            assertEquals(202, assertDoesNotThrow(() -> relay.exchange(relayLinkedRequest("source", "foreign-instance", "other"))).status());
            assertThrows(IllegalStateException.class, () -> relay.exchange(relayLinkedRequest("source", "instance", "unowned")));
            var held = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                    relay.exchange(relayLinkedRequest("source", "instance", "occurrence")));
            var heldWitness = assertDoesNotThrow(() -> relay.awaitObserved(hold, Duration.ofSeconds(2)));
            assertEquals("instance", heldWitness.instanceId());
            assertEquals("occurrence", heldWitness.occurrenceId());
            assertFalse(held.isDone());
            assertThrows(IllegalStateException.class, () -> relay.exchange(relayLinkedRequest("source", "instance", "different")));
            relay.arm(relayUnresolvedWindow(drop));
            assertThrows(ExpenseAcceptanceFaultRelay.ConnectionDropped.class,
                    () -> relay.exchange(relayResultRequest(event)));
            assertEquals(event.eventId(), relay.awaitObserved(drop, Duration.ofMillis(100)).eventId());
            assertFalse(held.isDone());
            relay.release(hold);
            assertArrayEquals(linkedReply, held.get(2, java.util.concurrent.TimeUnit.SECONDS).body());
        }
    }

    @Test
    void relayAtomicallyBindsTheFirstVerifiedOccurrence() throws Exception {
        var validating = new java.util.concurrent.CountDownLatch(1);
        var finishValidation = new java.util.concurrent.CountDownLatch(1);
        var commit = new java.util.concurrent.atomic.AtomicBoolean(true);
        try (var relay = new ExpenseAcceptanceFaultRelay(System::nanoTime, (request, timeout) -> {
            throw new AssertionError("The defer phase must not forward either occurrence");
        }, relayWitnesses(commit, commit, observed -> {
            if (!"slow".equals(observed.occurrenceId())) return true;
            validating.countDown();
            try {
                return finishValidation.await(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }))) {
            var phase = ExpenseAcceptanceFaultRelay.FaultPhase.DEFER_RESULT_UNTIL_TIMER_OR_RESTART;
            relay.arm(relayUnresolvedWindow(phase));
            var slow = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                    relay.exchange(relayResultRequest(relayTerminalEvent("c", "slow"))));
            try {
                assertTrue(validating.await(2, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(503, relay.exchange(relayResultRequest(relayTerminalEvent("c", "fast"))).status());
                assertEquals("fast", relay.awaitObserved(phase, Duration.ofMillis(100)).occurrenceId());
            } finally {
                finishValidation.countDown();
            }
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> slow.get(2, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            assertEquals("fast", relay.awaitObserved(phase, Duration.ofMillis(100)).occurrenceId());
            relay.release(phase);
        }
    }

    private static ExpenseAcceptanceFaultRelay.RequestDTO relayLinkedRequest(String source, String instance, String occurrence) {
        return new ExpenseAcceptanceFaultRelay.RequestDTO(ExpenseAcceptanceFaultRelay.Direction.LINKED, "POST",
                "/api/tianji/v1/agent-process-dispatches/submit",
                Map.of("Authorization", List.of("Bearer fixture"), "Content-Type", List.of("application/json")),
                JsonMapper.builder().build().writeValueAsBytes(Map.of("link", Map.of(
                        "sourceId", source, "scopeInstanceId", instance, "activityOccurrenceId", occurrence))));
    }

    private static ExpenseAcceptanceFaultRelay.FaultWindowDTO relayUnresolvedWindow(ExpenseAcceptanceFaultRelay.FaultPhase phase) {
        return new ExpenseAcceptanceFaultRelay.FaultWindowDTO(phase, 42, "source", "instance", null, null,
                null, null, Duration.ofSeconds(5));
    }

    private static ExpenseAcceptanceFaultRelay.RequestDTO relayWorkerComplete(String job, String occurrence) {
        var json = JsonMapper.builder().build();
        return new ExpenseAcceptanceFaultRelay.RequestDTO(ExpenseAcceptanceFaultRelay.Direction.WORKER, "POST",
                "/api/tianji/v1/worker-tasks/complete",
                Map.of("Authorization", List.of("Bearer fixture"), "Content-Type", List.of("application/json")),
                json.writeValueAsBytes(Map.of("taskId", job, "activityOccurrenceId", occurrence, "leaseToken", "A".repeat(43),
                        "businessReceiptId", "receipt", "output", Map.of())));
    }

    private static top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent relayTerminalEvent(String resultDigit) {
        return relayTerminalEvent(resultDigit, "occurrence");
    }

    private static top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent relayTerminalEvent(
            String resultDigit, String occurrence) {
        var occurred = Instant.parse("2026-11-05T05:00:00Z");
        var link = new top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink("source",
                top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum.BPMN,
                "instance", occurrence, "b".repeat(64), occurred.plusSeconds(30));
        String issuer = "a".repeat(64);
        String id = top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent.eventIdFor(
                "producer", 42, issuer, "owner", "run", 1);
        return new top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent(1, "producer", id,
                42, issuer, "owner", "idempotency", link, "expense", 1, "run", 1,
                top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.COMPLETED, occurred,
                resultDigit.repeat(64), null, "NONE");
    }

    private static ExpenseAcceptanceFaultRelay.RequestDTO relayResultRequest(
            top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent event) {
        return new ExpenseAcceptanceFaultRelay.RequestDTO(ExpenseAcceptanceFaultRelay.Direction.RESULT, "POST",
                "/api/tianji/v1/agent-result-inbox",
                Map.of("Authorization", List.of("Bearer fixture"), "Content-Type", List.of("application/json"),
                        "X-Egon-Tianji-Tenant", List.of("42")), JsonMapper.builder().build().writeValueAsBytes(event));
    }

    private static ExpenseAcceptanceFaultRelay.FaultWindowDTO relayResultWindow(ExpenseAcceptanceFaultRelay.FaultPhase phase,
            top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent event) {
        return new ExpenseAcceptanceFaultRelay.FaultWindowDTO(phase, 42, "source", "instance", "occurrence", null,
                event.eventId(), event.fingerprint(), Duration.ofSeconds(5));
    }

    private static ExpenseAcceptanceFaultRelay.Witnesses relayWitnesses(java.util.concurrent.atomic.AtomicBoolean commit,
                                                                         java.util.concurrent.atomic.AtomicBoolean ready) {
        return relayWitnesses(commit, ready, request -> true);
    }

    private static ExpenseAcceptanceFaultRelay.Witnesses relayWitnesses(java.util.concurrent.atomic.AtomicBoolean commit,
                     java.util.concurrent.atomic.AtomicBoolean ready,
                     java.util.function.Predicate<ExpenseAcceptanceFaultRelay.ObservedExchangeDTO> owned) {
        return new ExpenseAcceptanceFaultRelay.Witnesses() {
            @Override public boolean owned(ExpenseAcceptanceFaultRelay.FaultWindowDTO window,
                                           ExpenseAcceptanceFaultRelay.ObservedExchangeDTO request) {
                assertEquals(request.occurrenceId(), window.occurrenceId());
                return owned.test(request);
            }
            @Override public boolean businessCommitted(ExpenseAcceptanceFaultRelay.FaultWindowDTO window,
                     ExpenseAcceptanceFaultRelay.ObservedExchangeDTO request, String token, String receiptId) {
                assertEquals("A".repeat(43), token);
                assertEquals("receipt", receiptId);
                return commit.get();
            }
            @Override public boolean agentCommitted(ExpenseAcceptanceFaultRelay.FaultWindowDTO window,
                     ExpenseAcceptanceFaultRelay.ObservedExchangeDTO request, String runId) { return commit.get(); }
            @Override public boolean inboxCommitted(ExpenseAcceptanceFaultRelay.FaultWindowDTO window,
                     ExpenseAcceptanceFaultRelay.ObservedExchangeDTO request) { return commit.get(); }
            @Override public boolean replacementReady(ExpenseAcceptanceFaultRelay.FaultWindowDTO window,
                     ExpenseAcceptanceFaultRelay.ObservedExchangeDTO request) { return ready.get(); }
        };
    }
}
