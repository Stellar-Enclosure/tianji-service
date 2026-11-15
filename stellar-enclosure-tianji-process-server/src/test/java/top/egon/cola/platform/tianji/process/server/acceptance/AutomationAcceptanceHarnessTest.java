package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceHarness.Cut;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceHarness.CutWitnessVO;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceManifestVO.Role;

final class AutomationAcceptanceHarnessTest {
    private static final Instant NOW = Instant.parse("2026-11-15T07:00:00Z");
    @TempDir Path temporary;

    @Test
    void rejects_missing_role_duplicate_store_and_public_or_symbolic_paths() throws Exception {
        Path privateRoot = Files.createDirectory(temporary.toRealPath().resolve("private"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path credential = Files.createFile(privateRoot.resolve("credential"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(credential, "test-only");
        var stores = List.of(store(Role.PROCESS, "tianji_it_automation_ps_test", credential),
                store(Role.AGENT, "tianji_it_automation_as_test", credential),
                store(Role.WORKER, "tianji_it_automation_w_test", credential));
        assertThrows(IllegalArgumentException.class, () -> AutomationAcceptanceManifestVO.verifyStores(stores.subList(0, 2)));
        assertThrows(IllegalArgumentException.class, () -> AutomationAcceptanceManifestVO.verifyStores(List.of(
                stores.get(0), store(Role.AGENT, stores.get(0).catalog(), credential), stores.get(2))));
        assertEquals(credential, AutomationAcceptanceManifestVO.requirePrivateChild(privateRoot, credential));
        Files.setPosixFilePermissions(credential, PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(IllegalArgumentException.class, () -> AutomationAcceptanceManifestVO.requirePrivateChild(privateRoot, credential));
        Path link = Files.createSymbolicLink(privateRoot.resolve("link"), credential);
        assertThrows(IllegalArgumentException.class, () -> AutomationAcceptanceManifestVO.requirePrivateChild(privateRoot, link));
        Path extra = Files.createFile(privateRoot.resolve("extra.json"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(extra, """
                {"schemaVersion":1,"runAuthorized":true,"runId":"safe","unexpected":true}
                """);
        assertThrows(IllegalArgumentException.class, () -> AutomationAcceptanceManifestVO.read(extra));

        Path compiledClasses = temporary.toRealPath().resolve("compiled");
        Path main = Path.of(Role.PROCESS.main().replace('.', '/') + ".class");
        Path scope = Files.createDirectories(compiledClasses.resolve(main.getParent()));
        String mainName = main.getFileName().toString().replace(".class", "");
        Files.writeString(scope.resolve(main.getFileName()), "main");
        Files.writeString(scope.resolve(mainName + "$FixtureConfiguration.class"), "nested fixture");
        Files.writeString(scope.resolve("UnrelatedIT$TestConfiguration.class"), "unrelated fixture");
        var childClasses = privateRoot.resolve("isolated-classes");
        AutomationAcceptanceHarness.copyChildMain(compiledClasses, Role.PROCESS, childClasses);
        try (var children = Files.list(childClasses.resolve(main.getParent()))) {
            assertEquals(List.of(mainName + "$FixtureConfiguration.class", mainName + ".class"),
                    children.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void kills_and_replaces_only_the_observed_role() {
        List<Long> stopped = new ArrayList<>();
        try (var fleet = new AutomationAcceptanceHarness.ChildFleet((role, generation) -> {
            long pid = switch (role) {
                case PROCESS -> 11L;
                case AGENT -> 12L;
                case WORKER -> generation == 1 ? 13L : 14L;
            };
            return child(role, generation, pid, stopped);
        })) {
            fleet.launchAll();
            assertThrows(IllegalStateException.class, () -> fleet.replaceChildAtCut(Role.WORKER, () -> { }));
            fleet.observe(new CutWitnessVO(Cut.WORKER_AFTER_EXTERNAL_APPLIED_BEFORE_REPORT, "effect-1", "STARTED", NOW));
            fleet.replaceChildAtCut(Role.WORKER, () -> {
                assertEquals(List.of(11L, 12L), fleet.current().stream().map(AutomationAcceptanceHarness.ChildWitnessVO::pid).toList());
                assertEquals(List.of(13L), stopped);
            });
            assertEquals(List.of(11L, 12L, 14L), fleet.current().stream().map(AutomationAcceptanceHarness.ChildWitnessVO::pid).toList());
            assertEquals(List.of(13L), stopped);
        }
        assertEquals(List.of(13L, 14L, 12L, 11L), stopped);
    }

    @Test
    void rejects_cut_without_persisted_witness_and_redacts_failure_evidence() throws Exception {
        var cut = Cut.PS_AFTER_INBOX_COMMIT_BEFORE_APPLY;
        assertThrows(AssertionError.class, () -> AutomationAcceptanceHarness.awaitCut(cut, Duration.ofMillis(10),
                () -> true, Optional::empty));
        assertEquals(cut, AutomationAcceptanceHarness.awaitCut(cut, Duration.ofSeconds(1), () -> true,
                () -> Optional.of(new CutWitnessVO(cut, "event-1", "PENDING", NOW))).cut());
        String report = AutomationAcceptanceHarness.evidenceJson("fixture", List.of(), Map.of(), List.of(), NOW);
        assertTrue(report.contains("INCOMPLETE"));
        for (String forbidden : List.of("password", "Authorization", "Bearer", "prompt", "payload")) {
            assertFalse(report.contains(forbidden));
        }
    }

    private static AutomationAcceptanceManifestVO.StoreLockVO store(Role role, String catalog, Path credential) {
        return new AutomationAcceptanceManifestVO.StoreLockVO(role, "jdbc:postgresql://127.0.0.1:5432/" + catalog,
                catalog, role.schema(), "a".repeat(64), credential);
    }

    private static AutomationAcceptanceHarness.ChildProcess child(Role role, int generation, long pid, List<Long> stopped) {
        var evidence = new AutomationAcceptanceHarness.ChildWitnessVO(role, generation, pid,
                role == Role.WORKER ? 0 : 1000 + (int) pid, "a".repeat(64), NOW);
        return new AutomationAcceptanceHarness.ChildProcess() {
            @Override public AutomationAcceptanceHarness.ChildWitnessVO witness() { return evidence; }
            @Override public boolean alive() { return !stopped.contains(pid); }
            @Override public void stop(boolean force) { stopped.add(pid); }
        };
    }
}
