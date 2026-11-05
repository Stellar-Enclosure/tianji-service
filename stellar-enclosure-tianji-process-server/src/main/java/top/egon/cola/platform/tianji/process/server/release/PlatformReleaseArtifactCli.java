package top.egon.cola.platform.tianji.process.server.release;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Set;

/** Explicit local entry point; the Boot application's default Start-Class is unchanged. */
public final class PlatformReleaseArtifactCli {
    private static final String USAGE = "TIANJI_RELEASE_ARTIFACT_USAGE";
    private static final String IO_UNAVAILABLE = "TIANJI_RELEASE_ARTIFACT_IO_UNAVAILABLE";
    private static final Set<String> OPTIONS = Set.of("--root", "--input", "--tenant", "--channel", "--expect-sha256");

    private static final String SIGNATURE_USAGE = "TIANJI_RELEASE_SIGNATURE_USAGE";
    private static final Set<String> SIGNATURE_OPTIONS = Set.of("--root", "--candidate", "--signature", "--trust",
            "--expect-candidate-sha256", "--expect-trust-sha256", "--tenant", "--channel");

    private PlatformReleaseArtifactCli() { }

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) System.exit(status);
    }

    static int run(String[] args, OutputStream stdout, PrintStream stderr) {
        return run(args, stdout, stderr, Clock.systemUTC());
    }

    static int run(String[] args, OutputStream stdout, PrintStream stderr, Clock clock) {
        if (args != null && args.length > 0 && "verify-signature".equals(args[0])) {
            return runSignature(args, stdout, stderr, clock);
        }
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

        return write(candidate, stdout, stderr, IO_UNAVAILABLE);
    }

    private static int runSignature(String[] args, OutputStream stdout, PrintStream stderr, Clock clock) {
        SignatureOptionsDTO options;
        try {
            options = parseSignature(args);
        } catch (IllegalArgumentException invalid) {
            return fail(stderr, 64, SIGNATURE_USAGE);
        }
        byte[] proof;
        try {
            proof = PlatformReleaseSignatureVerifier.verify(options.root(), options.candidate(), options.signature(),
                    options.trust(), options.expectedCandidateSha(), options.expectedTrustSha(), options.tenant(),
                    options.channel(), clock);
        } catch (PlatformReleaseSignatureVerifier.SignatureFailure invalid) {
            return fail(stderr, invalid.exitCode(), invalid.code());
        }
        return write(proof, stdout, stderr, PlatformReleaseSignatureVerifier.IO_UNAVAILABLE);
    }

    private static int write(byte[] result, OutputStream stdout, PrintStream stderr, String unavailableCode) {
        try {
            stdout.write(result);
            stdout.flush();
            if (stdout instanceof PrintStream stream && stream.checkError()) return fail(stderr, 66, unavailableCode);
            return 0;
        } catch (IOException failure) {
            return fail(stderr, 66, unavailableCode);
        }
    }

    private static Options parse(String[] args) {
        if (args == null || (args.length != 9 && args.length != 11) || !"verify".equals(args[0])) {
            throw new IllegalArgumentException();
        }
        var values = options(args, OPTIONS);
        String root = values.get("--root"), input = values.get("--input"), tenant = values.get("--tenant");
        String channel = values.get("--channel"), expected = values.get("--expect-sha256");
        if (root == null || input == null || tenant == null || !tenant.matches("[1-9][0-9]{0,18}")
                || channel == null || !channel.matches("[a-z][a-z0-9._-]{0,63}")
                || expected != null && !expected.matches("[0-9a-f]{64}")) throw new IllegalArgumentException();
        return new Options(root(root), input, Long.parseLong(tenant), channel, expected);
    }

    private static SignatureOptionsDTO parseSignature(String[] args) {
        if (args.length != 1 + 2 * SIGNATURE_OPTIONS.size()) throw new IllegalArgumentException();
        var values = options(args, SIGNATURE_OPTIONS);
        String tenant = values.get("--tenant"), channel = values.get("--channel");
        String candidateSha = values.get("--expect-candidate-sha256"), trustSha = values.get("--expect-trust-sha256");
        if (!tenant.matches("[1-9][0-9]{0,18}") || !channel.matches("[a-z][a-z0-9._-]{0,63}")
                || !candidateSha.matches("[0-9a-f]{64}") || !trustSha.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException();
        }
        return new SignatureOptionsDTO(root(values.get("--root")), values.get("--candidate"),
                values.get("--signature"), values.get("--trust"), candidateSha, trustSha, Long.parseLong(tenant), channel);
    }

    private static HashMap<String, String> options(String[] args, Set<String> allowed) {
        var values = new HashMap<String, String>();
        for (int index = 1; index < args.length; index += 2) {
            String key = args[index], value = args[index + 1];
            if (key == null || !allowed.contains(key) || value == null || value.isBlank()
                    || values.putIfAbsent(key, value) != null) throw new IllegalArgumentException();
        }
        return values;
    }

    private static Path root(String root) {
        Path directory = Path.of(root);
        if (!directory.isAbsolute() || !directory.equals(directory.normalize()) || !directory.toString().equals(root)) {
            throw new IllegalArgumentException();
        }
        return directory;
    }

    private static int fail(PrintStream stderr, int status, String code) {
        stderr.print(code + "\n");
        return status;
    }

    private record Options(Path root, String input, long tenant, String channel, String expectedSha256) { }
    private record SignatureOptionsDTO(Path root, String candidate, String signature, String trust,
                                       String expectedCandidateSha, String expectedTrustSha, long tenant, String channel) { }
}
