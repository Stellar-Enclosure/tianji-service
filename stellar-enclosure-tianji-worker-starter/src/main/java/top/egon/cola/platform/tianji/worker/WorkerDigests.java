package top.egon.cola.platform.tianji.worker;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Length-delimited hashes of the bounded primitive contract values. */
final class WorkerDigests {
    private WorkerDigests() { }

    static String hash(List<String> fields, Map<String, Object> values) {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("SHA-256 is unavailable"); }
        try (DataOutputStream output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            for (String field : fields) write(output, field);
            Map<String, Object> sorted = new TreeMap<>(values);
            output.writeInt(sorted.size());
            for (var entry : sorted.entrySet()) {
                write(output, entry.getKey());
                Object value = entry.getValue();
                if (value instanceof String text) { output.writeByte(1); write(output, text); }
                else if (value instanceof Long number) { output.writeByte(2); output.writeLong(number); }
                else if (value instanceof Boolean flag) { output.writeByte(3); output.writeBoolean(flag); }
                else throw new IllegalArgumentException("Worker value is invalid");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Worker digest could not be calculated");
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void write(DataOutputStream output, String field) throws IOException {
        byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
