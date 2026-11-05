package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Pattern;

/** Exact, bounded test-only handoff to disposable checkpoint probe children. */
final class AgentCheckpointForkHandoff {
    private static final int FORMAT = 1;
    private static final int MAX_BYTES = 512;
    private static final Pattern URL = Pattern.compile(
            "jdbc:postgresql://127\\.0\\.0\\.1:([1-9][0-9]{0,4})/(tianji_it_checkpoint_[a-z0-9_]{1,32})");
    private static final Pattern SETUP_ROLE = Pattern.compile("[a-z_][a-z0-9_]{0,62}");
    private static final Pattern RUNTIME_ROLE = Pattern.compile("tianji_it_checkpoint_[a-z0-9_]{1,32}");
    private static final Pattern VECTOR = Pattern.compile("[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}");
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    private AgentCheckpointForkHandoff() { }

    static String databaseName(String url) {
        if (url == null) throw invalid();
        var match = URL.matcher(url);
        if (!match.matches() || Integer.parseInt(match.group(1)) > 65_535) throw invalid();
        return match.group(2);
    }

    static long tenant(boolean typed) {
        return typed ? 90_102 : 90_101;
    }

    static void send(OutputStream pipe, Request request) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(FORMAT);
            out.writeUTF(request.url());
            out.writeUTF(request.setupRole());
            out.writeUTF(request.runtimeRole());
            out.writeUTF(request.vectorVersion());
            out.writeBoolean(request.typed());
            out.writeUTF(request.runId());
            out.writeByte(request.phase().code);
            if (request.phase() != Phase.CAPTURE) out.writeUTF(request.cutSha256());
        }
        if (bytes.size() > MAX_BYTES) throw invalid();
        bytes.writeTo(pipe);
        pipe.flush();
    }

    static Request receive(InputStream pipe) throws IOException {
        byte[] bytes = pipe.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw invalid();
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != FORMAT) throw invalid();
            String url = input.readUTF();
            String setupRole = input.readUTF();
            String runtimeRole = input.readUTF();
            String vectorVersion = input.readUTF();
            boolean typed = exactBoolean(input);
            String runId = input.readUTF();
            var phase = switch (input.readUnsignedByte()) {
                case 0 -> Phase.CAPTURE;
                case 1 -> Phase.RECONCILE;
                case 2 -> Phase.NATIVE_RESUME;
                default -> throw invalid();
            };
            var request = new Request(url, setupRole, runtimeRole, vectorVersion, typed, runId, phase,
                    phase == Phase.CAPTURE ? null : input.readUTF());
            if (input.read() != -1) throw invalid();
            return request;
        } catch (IOException malformed) {
            throw invalid();
        }
    }

    private static boolean exactBoolean(DataInputStream input) throws IOException {
        int value = input.readUnsignedByte();
        if (value > 1) throw invalid();
        return value == 1;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid disposable checkpoint fork handoff");
    }

    enum Phase {
        CAPTURE(0, "capture"), RECONCILE(1, "reconcile"), NATIVE_RESUME(2, "resume");

        private final int code;
        private final String markerName;

        Phase(int code, String markerName) {
            this.code = code;
            this.markerName = markerName;
        }

        String markerName() { return markerName; }
    }

    record Request(String url, String setupRole, String runtimeRole, String vectorVersion, boolean typed,
                   String runId, Phase phase, String cutSha256) {
        Request {
            databaseName(url);
            if (setupRole == null || !SETUP_ROLE.matcher(setupRole).matches()
                    || runtimeRole == null || !RUNTIME_ROLE.matcher(runtimeRole).matches() || setupRole.equals(runtimeRole)
                    || vectorVersion == null || !VECTOR.matcher(vectorVersion).matches()
                    || runId == null || !UUID.matcher(runId).matches()
                    || phase == null || (phase == Phase.CAPTURE) != (cutSha256 == null)
                    || cutSha256 != null && !DIGEST.matcher(cutSha256).matches()) throw invalid();
        }

        @Override public String toString() { return "AgentCheckpointForkHandoff.Request[redacted]"; }
    }
}
