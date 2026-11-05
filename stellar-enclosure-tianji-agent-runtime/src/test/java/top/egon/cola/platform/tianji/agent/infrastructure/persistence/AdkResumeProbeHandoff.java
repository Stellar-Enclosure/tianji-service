package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.regex.Pattern;

/** Test-only bounded pipe format for two independent disposable-database probe children. */
final class AdkResumeProbeHandoff {
    private static final int MAX_BYTES = 1024;
    private static final int FORMAT = 1;
    private static final Pattern URL = Pattern.compile(
            "jdbc:postgresql://127\\.0\\.0\\.1:([1-9][0-9]{0,4})/(tianji_it_resume_[a-z0-9_]{1,32})");
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern INVOCATION = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final String RUN_OWNER = "original-run-owner";

    private AdkResumeProbeHandoff() { }

    static String databaseName(String url) {
        if (url == null) throw invalid();
        var candidate = URL.matcher(url);
        if (!candidate.matches() || Integer.parseInt(candidate.group(1)) > 65535) throw invalid();
        return candidate.group(2);
    }

    static void send(OutputStream pipe, Request request) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(FORMAT);
            out.writeUTF(request.url());
            out.writeLong(request.tenantId());
            out.writeBoolean(request.typedWrites());
            out.writeUTF(request.run().runId());
            out.writeLong(request.run().epoch());
            out.writeLong(request.run().expiresAt().getEpochSecond());
            out.writeInt(request.run().expiresAt().getNano());
            out.writeBoolean(request.resume());
            if (request.resume()) {
                out.writeUTF(request.invocationId());
                out.writeUTF(request.priorSessionOwnerSha256());
            }
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
            long tenant = input.readLong();
            boolean typed = exactBoolean(input);
            String runId = input.readUTF();
            long epoch = input.readLong();
            long seconds = input.readLong();
            int nanos = input.readInt();
            if (nanos < 0 || nanos >= 1_000_000_000) throw invalid();
            var run = new AgentRunLease(runId, RUN_OWNER, epoch, Instant.ofEpochSecond(seconds, nanos));
            if (!runId.equals(run.runId())) throw invalid();
            boolean resume = exactBoolean(input);
            var request = new Request(url, tenant, typed, run, resume ? input.readUTF() : null,
                    resume ? input.readUTF() : null);
            if (input.read() != -1) throw invalid();
            return request;
        } catch (DateTimeException | IOException malformed) {
            throw invalid();
        }
    }

    private static boolean exactBoolean(DataInputStream input) throws IOException {
        int value = input.readUnsignedByte();
        if (value > 1) throw invalid();
        return value == 1;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid disposable ADK resume probe handoff");
    }

    record Request(String url, long tenantId, boolean typedWrites, AgentRunLease run,
                   String invocationId, String priorSessionOwnerSha256) {
        Request {
            databaseName(url);
            if (tenantId <= 100_000 || tenantId > 101_000 || run == null || !UUID.matcher(run.runId()).matches()
                    || !RUN_OWNER.equals(run.owner()) || run.epoch() != 1 || !run.expiresAt().isAfter(Instant.EPOCH)
                    || (invocationId == null) != (priorSessionOwnerSha256 == null)
                    || invocationId != null && (!INVOCATION.matcher(invocationId).matches()
                    || !HASH.matcher(priorSessionOwnerSha256).matches())) throw invalid();
        }

        boolean resume() { return invocationId != null; }

        @Override public String toString() { return "AdkResumeProbeHandoff.Request[redacted]"; }
    }
}
