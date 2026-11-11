package top.egon.cola.platform.tianji.agent.server.security;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Pattern;

/** Bounded, private stdin for the disposable queue producer. */
record QueuedGrantRecoveryHandoff(String url, String setupRole) {
    private static final int FORMAT = 1;
    private static final int MAX_BYTES = 192;
    private static final Pattern URL = Pattern.compile(
            "jdbc:postgresql://127\\.0\\.0\\.1:([1-9][0-9]{0,4})/(tianji_it_queue_recovery_[a-z0-9_]{1,24})");
    private static final Pattern ROLE = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    QueuedGrantRecoveryHandoff {
        databaseName(url);
        if (setupRole == null || !ROLE.matcher(setupRole).matches()) throw invalid();
    }

    static String databaseName(String url) {
        if (url == null) throw invalid();
        var match = URL.matcher(url);
        if (!match.matches() || Integer.parseInt(match.group(1)) > 65_535) throw invalid();
        return match.group(2);
    }

    void send(OutputStream pipe) throws IOException {
        var output = new DataOutputStream(pipe);
        output.writeByte(FORMAT);
        output.writeUTF(url);
        output.writeUTF(setupRole);
        output.flush();
    }

    static QueuedGrantRecoveryHandoff receive(InputStream pipe) throws IOException {
        byte[] bytes = pipe.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw invalid();
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readUnsignedByte() != FORMAT) throw invalid();
            var request = new QueuedGrantRecoveryHandoff(input.readUTF(), input.readUTF());
            if (input.read() != -1) throw invalid();
            return request;
        } catch (IOException malformed) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid disposable queue producer handoff");
    }

    @Override public String toString() { return "QueuedGrantRecoveryHandoff[redacted]"; }
}
