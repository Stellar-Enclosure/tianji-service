package top.egon.cola.platform.tianji.worker.acceptance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationWorkerAcceptanceClasspathTest {
    private static final String CLASSPATH_PROPERTY = "tianji.automation.acceptance.workerClasspath";
    private static final String MAIN = "top/egon/cola/platform/tianji/worker/acceptance/AutomationWorkerAcceptanceMain.class";
    private static final int MAX_ENTRIES = 1_024;
    private static final int MAX_TREE_ENTRIES = 16_384;
    private static final long MAX_ARTIFACT_BYTES = 512L * 1024 * 1024;
    private static final List<String> FORBIDDEN_NAMESPACES = List.of("org/flowable/", "com/google/adk/", "org/springframework/ai/",
            "top/egon/cola/platform/tianji/agent/server/", "top/egon/cola/platform/tianji/process/server/");

    @Test
    @EnabledIfSystemProperty(named = "tianji.automation.acceptance.prepare", matches = "true")
    void writes_worker_classpath_without_native_engines() throws IOException {
        var loader = getClass().getClassLoader();
        for (String type : List.of("org.flowable.engine.ProcessEngine", "com.google.adk.runner.Runner",
                "org.springframework.ai.chat.model.ChatModel",
                "top.egon.cola.platform.tianji.agent.server.TianjiAgentServerApplication",
                "top.egon.cola.platform.tianji.process.server.TianjiProcessServerApplication")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(type, false, loader));
        }
        String classpath = System.getProperty("surefire.test.class.path");
        assertNotNull(classpath, "Surefire must supply the actual Worker test classpath");
        // Surefire terminates its list with one separator. Do not pass that empty item to java -cp.
        if (classpath.endsWith(File.pathSeparator)) {
            classpath = classpath.substring(0, classpath.length() - File.pathSeparator.length());
        }
        String[] entries = classpath.split(Pattern.quote(File.pathSeparator), -1);
        assertTrue(entries.length > 0 && entries.length <= MAX_ENTRIES);
        var unique = new HashSet<Path>();
        var locked = new ArrayList<Map<String, String>>(entries.length);
        int mainCount = 0;
        for (String entry : entries) {
            Path supplied = Path.of(entry);
            assertTrue(supplied.isAbsolute(), "Worker test classpath entry must be absolute");
            // The source reactor can supply absolute aliases. Lock the same existing files by their resolved paths.
            Path path = supplied.toRealPath();
            assertFalse(path.toString().contains(File.pathSeparator), "Resolved Worker test classpath entry cannot contain a separator");
            assertTrue(unique.add(path), "Duplicate Worker test classpath entry");
            byte[] sha256;
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isRegularFile(path.resolve(MAIN), LinkOption.NOFOLLOW_LINKS)) mainCount++;
                sha256 = digestTree(path);
            } else {
                assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS));
                verifyJar(path);
                sha256 = digestFile(path);
            }
            locked.add(Map.of("path", path.toString(), "sha256", HexFormat.of().formatHex(sha256)));
        }
        assertTrue(mainCount == 1, "Worker acceptance main must appear exactly once in Worker test output");
        Path output = requirePrivateOutput();
        byte[] document = ProcessTypedJson.canonicalBytes(locked, 512 * 1024);
        try (var channel = Files.newByteChannel(output, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            var bytes = ByteBuffer.wrap(document);
            while (bytes.hasRemaining()) channel.write(bytes);
        }
    }

    private static Path requirePrivateOutput() throws IOException {
        String property = System.getProperty(CLASSPATH_PROPERTY);
        assertNotNull(property, "Worker classpath requires an explicit private output file");
        Path path = Path.of(property);
        assertTrue(path.isAbsolute() && path.equals(path.normalize()) && path.getParent() != null);
        Path parent = path.getParent();
        assertTrue(parent.equals(parent.toRealPath()) && Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS));
        var owner = parent.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
        assertTrue(owner.equals(Files.getOwner(parent, LinkOption.NOFOLLOW_LINKS)));
        assertTrue(Files.getPosixFilePermissions(parent, LinkOption.NOFOLLOW_LINKS)
                .equals(PosixFilePermissions.fromString("rwx------")), "Worker classpath parent must be owner-only");
        URI testClassPath = URI.create(AutomationWorkerAcceptanceClasspathTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toExternalForm());
        Path module = Path.of(testClassPath).toRealPath().getParent().getParent();
        assertTrue("stellar-enclosure-tianji-worker-starter".equals(module.getFileName().toString()));
        assertFalse(parent.startsWith(module.getParent()) || Files.exists(path, LinkOption.NOFOLLOW_LINKS),
                "Worker classpath must be written to a fresh private file outside the checkout");
        return path;
    }

    private static void verifyJar(Path path) throws IOException {
        assertTrue(Files.size(path) <= MAX_ARTIFACT_BYTES);
        try (var jar = new JarFile(path.toFile(), false)) {
            assertFalse(jar.stream().anyMatch(entry -> forbiddenClass(entry.getName())),
                    "Worker test dependency contains a native engine or server class");
            assertTrue(jar.getEntry(MAIN) == null, "Worker acceptance main must remain in test output");
        }
    }

    private static byte[] digestTree(Path root) throws IOException {
        var files = new TreeMap<byte[], Path>(Arrays::compareUnsigned);
        long total = 0;
        try (var tree = Files.walk(root)) {
            var entries = tree.limit(MAX_TREE_ENTRIES + 1L).toList();
            assertTrue(entries.size() <= MAX_TREE_ENTRIES);
            for (Path entry : entries) {
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) continue;
                assertTrue(Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS), "Classpath tree contains a non-regular entry");
                String relative = root.relativize(entry).toString();
                assertFalse(forbiddenClass(relative), "Worker test classes contain a native engine or server class");
                assertTrue(StandardCharsets.UTF_8.newEncoder().canEncode(relative));
                byte[] name = relative.getBytes(StandardCharsets.UTF_8);
                total = Math.addExact(total, Files.size(entry));
                assertTrue(name.length <= 4_096 && total <= MAX_ARTIFACT_BYTES && files.putIfAbsent(name, entry) == null);
            }
        }
        assertFalse(files.isEmpty());
        // Keep the digest framing identical to the independent-process artifact lock reader.
        MessageDigest digest = newDigest();
        for (var entry : files.entrySet()) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(entry.getKey().length).array());
            digest.update(entry.getKey());
            digest.update(digestFile(entry.getValue()));
        }
        return digest.digest();
    }

    private static boolean forbiddenClass(String entry) {
        if (!entry.endsWith(".class")) return false;
        for (String namespace : FORBIDDEN_NAMESPACES) {
            if (entry.startsWith(namespace) || entry.contains('/' + namespace)) return true;
        }
        return false;
    }

    private static byte[] digestFile(Path path) throws IOException {
        MessageDigest digest = newDigest();
        try (var input = new DigestInputStream(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS), digest)) {
            assertTrue(input.transferTo(OutputStream.nullOutputStream()) <= MAX_ARTIFACT_BYTES);
        }
        return digest.digest();
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is required for the Worker classpath lock", missing);
        }
    }
}
