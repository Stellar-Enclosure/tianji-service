package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.regex.Pattern;

/** Private bounded transport and exact receipt witness for the disposable recorder child. */
record ToolReceiptCutForkHandoff(String url, String setupRole, String vectorVersion) {
    private static final int FORMAT = 1;
    private static final int MAX_BYTES = 224;
    private static final Pattern URL = Pattern.compile(
            "jdbc:postgresql://127\\.0\\.0\\.1:([1-9][0-9]{0,4})/(tianji_it_tool_cut_[a-z0-9_]{1,24})");
    private static final Pattern ROLE = Pattern.compile("[a-z_][a-z0-9_]{0,62}");
    private static final Pattern VECTOR = Pattern.compile("[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}");
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");

    ToolReceiptCutForkHandoff {
        databaseName(url);
        if (setupRole == null || !ROLE.matcher(setupRole).matches()
                || vectorVersion == null || !VECTOR.matcher(vectorVersion).matches()) throw invalid();
    }

    static String databaseName(String url) {
        if (url == null) throw invalid();
        var match = URL.matcher(url);
        if (!match.matches() || Integer.parseInt(match.group(1)) > 65_535) throw invalid();
        return match.group(2);
    }

    void send(OutputStream pipe) throws IOException {
        var out = new DataOutputStream(pipe);
        out.writeByte(FORMAT);
        out.writeUTF(url);
        out.writeUTF(setupRole);
        out.writeUTF(vectorVersion);
        out.flush();
    }

    static ToolReceiptCutForkHandoff receive(InputStream pipe) throws IOException {
        byte[] frame = pipe.readNBytes(MAX_BYTES + 1);
        if (frame.length > MAX_BYTES) throw invalid();
        try (var input = new DataInputStream(new ByteArrayInputStream(frame))) {
            if (input.readUnsignedByte() != FORMAT) throw invalid();
            var request = new ToolReceiptCutForkHandoff(input.readUTF(), input.readUTF(), input.readUTF());
            if (input.read() != -1) throw invalid();
            return request;
        } catch (IOException malformed) {
            throw invalid();
        }
    }

    record Cut(String runId, UUID invocationId, UUID intentId) {
        Cut {
            if (runId == null || !UUID_TEXT.matcher(runId).matches() || invocationId == null || intentId == null) throw invalid();
        }

        String encode() {
            return runId + " " + invocationId + " " + intentId + "\n";
        }

        static Cut decode(String marker) {
            if (marker == null || !marker.endsWith("\n")) throw invalid();
            var fields = marker.substring(0, marker.length() - 1).split(" ", -1);
            if (fields.length != 3 || !UUID_TEXT.matcher(fields[1]).matches()
                    || !UUID_TEXT.matcher(fields[2]).matches()) throw invalid();
            return new Cut(fields[0], UUID.fromString(fields[1]), UUID.fromString(fields[2]));
        }

        @Override public String toString() { return "ToolReceiptCut[redacted]"; }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid disposable Tool receipt cut handoff");
    }

    @Override public String toString() { return "ToolReceiptCutForkHandoff[redacted]"; }
}
