package top.egon.cola.platform.tianji.process.server.release;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Set;

/** Explicit local entry point; the Boot application's default Start-Class is unchanged. */
public final class PlatformReleaseArtifactCli {
    private static final String USAGE = "TIANJI_RELEASE_ARTIFACT_USAGE";
    private static final String IO_UNAVAILABLE = "TIANJI_RELEASE_ARTIFACT_IO_UNAVAILABLE";
    private static final Set<String> OPTIONS = Set.of("--root", "--input", "--tenant", "--channel", "--expect-sha256");

    private PlatformReleaseArtifactCli() { }

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) System.exit(status);
    }

    static int run(String[] args, OutputStream stdout, PrintStream stderr) {
        Options options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException invalid) {
            return fail(stderr, 64, USAGE);
        }

        byte[] candidate;
        try {
            candidate = PlatformReleaseArtifactValidator.verify(options.root(), options.input(), options.tenant(),
                    options.channel(), options.expectedSha256());
        } catch (PlatformReleaseArtifactValidator.ValidationFailure invalid) {
            return fail(stderr, invalid.exitCode(), invalid.code());
        }

        try {
            stdout.write(candidate);
            stdout.flush();
            if (stdout instanceof PrintStream stream && stream.checkError()) return fail(stderr, 66, IO_UNAVAILABLE);
            return 0;
        } catch (IOException failure) {
            return fail(stderr, 66, IO_UNAVAILABLE);
        }
    }

    private static Options parse(String[] args) {
        if (args == null || (args.length != 9 && args.length != 11) || !"verify".equals(args[0])) {
            throw new IllegalArgumentException();
        }
        var values = new HashMap<String, String>();
        for (int index = 1; index < args.length; index += 2) {
            String key = args[index], value = args[index + 1];
            if (key == null || !OPTIONS.contains(key) || value == null || value.isBlank()
                    || values.putIfAbsent(key, value) != null) throw new IllegalArgumentException();
        }
        String root = values.get("--root"), input = values.get("--input"), tenant = values.get("--tenant");
        String channel = values.get("--channel"), expected = values.get("--expect-sha256");
        if (root == null || input == null || tenant == null || !tenant.matches("[1-9][0-9]{0,18}")
                || channel == null || !channel.matches("[a-z][a-z0-9._-]{0,63}")
                || expected != null && !expected.matches("[0-9a-f]{64}")) throw new IllegalArgumentException();
        Path directory = Path.of(root);
        if (!directory.isAbsolute() || !directory.equals(directory.normalize()) || !directory.toString().equals(root)) {
            throw new IllegalArgumentException();
        }
        return new Options(directory, input, Long.parseLong(tenant), channel, expected);
    }

    private static int fail(PrintStream stderr, int status, String code) {
        stderr.print(code + "\n");
        return status;
    }

    private record Options(Path root, String input, long tenant, String channel, String expectedSha256) { }
}
