package top.egon.cola.platform.tianji.worker;

import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerTaskDTO;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
    static String automationEffectFingerprint(AutomationWorkerHandlerDescriptor descriptor, Map<String, Object> input) {
        return ProcessTypedJson.sha256("AUTOMATION_WORKER_EFFECT_V2", utf8(descriptor.capabilityKey()),
                utf8(Long.toString(descriptor.capabilityVersion())), utf8(descriptor.inputSchemaSha256()),
                utf8(descriptor.outputSchemaSha256()), utf8(descriptor.effectMode().name()),
                utf8(descriptor.handlerVersion()), ProcessTypedJson.canonicalBytes(descriptor.targetId()),
                ProcessTypedJson.canonicalBytes(input, 65_536));
    }

    static String schemaSha256(ProcessSchemaVO schema) {
        return rawSha256(ProcessTypedJson.canonicalBytes(schema.toWire(), 32_768));
    }

    static String taskInputSha256(Map<String, Object> input) {
        return rawSha256(ProcessTypedJson.canonicalBytes(input, 65_536));
    }

    static String businessInputSha256(Map<String, Object> input) {
        return ProcessTypedJson.sha256("AUTOMATION_WORKER_BUSINESS_INPUT_V2", ProcessTypedJson.canonicalBytes(input, 65_536));
    }

    static String canonicalLeaseTokenSha256(String token) {
        return AutomationWorkerTaskDTO.leaseTokenSha256(token);
    }

    static String rawSha256(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    static Map<String, Object> immutableTypedObject(Map<String, Object> input) {
        if (input == null) throw new IllegalArgumentException("Worker typed object is required");
        ProcessTypedJson.canonicalBytes(input, 65_536);
        ProcessTypedJson.validateTypedIntegerLeaves(input);
        @SuppressWarnings("unchecked")
        var frozen = (Map<String, Object>) freeze(input);
        return frozen;
    }

    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> copy.put((String) key, freeze(item)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            var copy = new ArrayList<>();
            list.forEach(item -> copy.add(freeze(item)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
