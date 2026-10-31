package top.egon.cola.platform.tianji.contract.orchestration;

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

/** Version-one length-delimited fingerprints over normalized primitive values; a digest is not authorization. */
public final class OrchestrationRequestDigests {
    private OrchestrationRequestDigests() { }
    public static String start(StartProcessCommand command) {
        return hash(List.of(command.releaseVersion(), command.definitionKey(), command.businessKey(), command.correlationId()), command.variables());
    }
    public static String hash(List<String> fields, Map<String, Object> values) {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 is unavailable"); }
        try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            for (var field : fields) text(output, field);
            var sorted = new TreeMap<>(values); output.writeInt(sorted.size());
            for (var entry : sorted.entrySet()) {
                text(output, entry.getKey()); Object value = entry.getValue();
                if (value instanceof String string) { output.writeByte(1); text(output, string); }
                else if (value instanceof Long number) { output.writeByte(2); output.writeLong(number); }
                else if (value instanceof Boolean flag) { output.writeByte(3); output.writeBoolean(flag); }
                else throw new IllegalArgumentException("Process input does not match the release schema");
            }
        } catch (IOException failure) { throw new IllegalStateException("Process request digest could not be calculated"); }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void text(DataOutputStream output, String value) throws IOException { byte[] bytes = value.getBytes(StandardCharsets.UTF_8); output.writeInt(bytes.length); output.write(bytes); }
}
